package dev.mzc.client.module.impl.combat;

import dev.mzc.client.events.EventType;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.entity.AttackEvent;
import dev.mzc.client.events.packet.PacketEvent;
import dev.mzc.client.events.render.Render3DEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.render.Render3DUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.awt.*;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Backtrack extends Module {
    private final NumberValue<Double> range = new NumberValue<>("Range", 3.2, 0.0, 8.0, 0.1);
    private final NumberValue<Integer> delay = new NumberValue<>("Delay", 200, 0, 1000, 10);
    private final NumberValue<Integer> trackingBuffer = new NumberValue<>("TrackingBuffer", 500, 0, 2000, 10);
    private final NumberValue<Integer> attackWindow = new NumberValue<>("AttackWindow", 1000, 0, 5000, 50);
    private final BoolValue pauseOnHurt = new BoolValue("PauseOnHurt", false);
    private final NumberValue<Integer> hurtTime = new NumberValue<>("HurtTime", 3, 0, 10, 1, pauseOnHurt::get);
    private final BoolValue render = new BoolValue("Render", true);

    private final Queue<QueuedPacket> packetQueue = new ConcurrentLinkedQueue<>();

    private PlayerEntity target;
    private Vec3d targetRealPos;
    private long lastAttackTime;
    private long lastInRangeTime;

    public Backtrack() {
        super("Backtrack", Category.Combat);
        this.setType(ModuleType.Hack);
    }

    @Override
    protected void onEnable() {
        clear(false);
    }

    @Override
    protected void onDisable() {
        clear(true);
    }

    @Override
    public String getSuffix() {
        return delay.get() + "ms";
    }

    @EventHandler
    private void onAttack(AttackEvent event) {
        if (nullCheck()) return;
        if (!(event.getTargetEntity() instanceof PlayerEntity player)) return;
        if (AntiBot.isBot(player)) return;

        if (target != player) {
            // Target switched: discard delayed packets to avoid stale burst replay.
            clear(false);
        }

        target = player;
        targetRealPos = player.getEntityPos();
        long now = System.currentTimeMillis();
        lastAttackTime = now;
    }

    @EventHandler
    private void onPacket(PacketEvent event) {
        if (nullCheck()) return;
        if (event.getType() != EventType.RECEIVE) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof PlayerPositionLookS2CPacket
                || packet instanceof DisconnectS2CPacket
                || packet instanceof PlayerRespawnS2CPacket
                || packet instanceof KeepAliveS2CPacket) {
            // Never flush from network receive path to avoid off-thread packet.apply timing issues.
            clear(false);
            return;
        }

        long now = System.currentTimeMillis();
        boolean forTarget = isPacketForTarget(packet);
        if (forTarget) {
            updateTargetRealPosition(packet);
        }

        if (!shouldBacktrack(now)) return;

        if (forTarget) {
            event.cancel();
            packetQueue.add(new QueuedPacket((Packet<? super ClientPlayPacketListener>) packet, now));
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (nullCheck()) {
            clear(false);
            return;
        }

        long now = System.currentTimeMillis();
        flushExpiredPackets(now);

        PlayerEntity currentTarget = target;
        if (currentTarget == null) return;

        if (!currentTarget.isAlive() || currentTarget.isRemoved() || AntiBot.isBot(currentTarget)) {
            clear(true);
            return;
        }

        if (targetRealPos == null) {
            targetRealPos = currentTarget.getEntityPos();
        }

        if (mc.player.distanceTo(currentTarget) <= range.get()) {
            lastInRangeTime = now;
        }

        if (!shouldBacktrack(now) && !packetQueue.isEmpty()) {
            flushAllPackets();
        }

        // When not delaying target packets, entity position is already real.
        if (!shouldBacktrack(now) || packetQueue.isEmpty()) {
            PlayerEntity latestTarget = target;
            if (latestTarget != null) {
                targetRealPos = latestTarget.getEntityPos();
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;
        if (!render.get()) return;
        PlayerEntity currentTarget = target;
        if (currentTarget == null || targetRealPos == null) return;
        if (!shouldBacktrack(System.currentTimeMillis())) return;

        Box backtrackBox = toBox(targetRealPos, currentTarget.getWidth(), currentTarget.getHeight());
        Render3DUtil.drawFullBox(event.getMatrices(), backtrackBox, new Color(220, 40, 40, 70), new Color(255, 70, 70, 180), 2.0f);

        Box currentBox = currentTarget.getBoundingBox();
        Render3DUtil.drawBoxOutline(event.getMatrices(), currentBox, new Color(70, 220, 70, 180).getRGB(), 1.2f);
    }

    private boolean shouldBacktrack(long now) {
        if (target == null || mc.player == null) return false;
        if (!target.isAlive() || target.isRemoved()) return false;
        if (pauseOnHurt.get() && target.hurtTime >= hurtTime.get()) return false;

        boolean inRange = mc.player.distanceTo(target) <= range.get();
        if (inRange) {
            lastInRangeTime = now;
        }

        boolean inTrackingBuffer = now - lastInRangeTime <= trackingBuffer.get();
        boolean attackFresh = attackWindow.get() <= 0 || now - lastAttackTime <= attackWindow.get();
        return attackFresh && (inRange || inTrackingBuffer);
    }

    private boolean isPacketForTarget(Packet<?> packet) {
        if (target == null || mc.world == null) return false;
        int targetId = target.getId();

        if (packet instanceof EntityS2CPacket entityPacket) {
            Entity entity = entityPacket.getEntity(mc.world);
            return entity != null && entity.getId() == targetId;
        }

        if (packet instanceof EntityPositionS2CPacket entityPositionPacket) {
            return entityPositionPacket.entityId() == targetId;
        }

        if (packet instanceof EntityVelocityUpdateS2CPacket velocityPacket) {
            return velocityPacket.getEntityId() == targetId;
        }

        return false;
    }

    private void updateTargetRealPosition(Packet<?> packet) {
        if (target == null || mc.world == null) return;

        if (packet instanceof EntityS2CPacket entityPacket) {
            Entity entity = entityPacket.getEntity(mc.world);
            if (entity == null || entity.getId() != target.getId()) return;

            Vec3d current = targetRealPos != null ? targetRealPos : entity.getEntityPos();
            if (entityPacket.isPositionChanged()) {
                double dx = entityPacket.getDeltaX() / 4096.0;
                double dy = entityPacket.getDeltaY() / 4096.0;
                double dz = entityPacket.getDeltaZ() / 4096.0;
                targetRealPos = current.add(dx, dy, dz);
            } else {
                targetRealPos = current;
            }
            return;
        }

        if (packet instanceof EntityPositionS2CPacket entityPositionPacket) {
            if (entityPositionPacket.entityId() != target.getId()) return;
            targetRealPos = entityPositionPacket.change().position();
        }
    }

    private Box toBox(Vec3d pos, double width, double height) {
        double half = width / 2.0;
        return new Box(
                pos.x - half, pos.y, pos.z - half,
                pos.x + half, pos.y + height, pos.z + half
        );
    }

    private void flushExpiredPackets(long now) {
        int maxDelay = delay.get();
        while (!packetQueue.isEmpty()) {
            QueuedPacket queued = packetQueue.peek();
            if (queued == null) break;
            if (now - queued.time < maxDelay) break;
            packetQueue.poll();
            applyPacket(queued.packet);
        }
    }

    private void flushAllPackets() {
        while (!packetQueue.isEmpty()) {
            QueuedPacket queued = packetQueue.poll();
            if (queued != null) {
                applyPacket(queued.packet);
            }
        }
    }

    private void applyPacket(Packet<? super ClientPlayPacketListener> packet) {
        if (packet == null || mc.getNetworkHandler() == null) return;
        packet.apply(mc.getNetworkHandler());
    }

    private void clear(boolean flushPackets) {
        if (flushPackets) {
            flushAllPackets();
        } else {
            packetQueue.clear();
        }
        targetRealPos = null;
        target = null;
        lastAttackTime = 0L;
        lastInRangeTime = 0L;
    }

    private record QueuedPacket(Packet<? super ClientPlayPacketListener> packet, long time) {
    }
}
