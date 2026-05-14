package dev.mzc.client.module.impl.movement;

import dev.mzc.client.events.EventType;
import dev.mzc.client.events.packet.PacketEvent;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.render.Render3DEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.render.Render3DUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.ColorValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.MultiBoolValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

public class FakeLag extends Module {

    private final NumberValue<Integer> delayMs = new NumberValue<>("Delay", 400, 0, 2000, 10);
    private final BoolValue fastMode = new BoolValue("FastMode", false);
    private final BoolValue fastFlushAll = new BoolValue("FastFlushAll", true, fastMode::get);
    private final NumberValue<Integer> nextLagDelayMs = new NumberValue<>("NextLagDelay", 0, 0, 3000, 50);
    private final NumberValue<Integer> maxPacketsPerTick = new NumberValue<>("MaxPackets/Tick", 1, 1, 100, 1);
    private final BoolValue flushOnDisable = new BoolValue("FlushOnDisable", true);
    private final BoolValue autoBlinkOnStop = new BoolValue("Auto Blink On Stop", true);
    private final BoolValue pulseMode = new BoolValue("Pulse Mode", false);
    private final NumberValue<Integer> pulseDurationMs = new NumberValue<>("Pulse Duration", 500, 50, 3000, 50);
    private final NumberValue<Integer> pulseCooldownMs = new NumberValue<>("Pulse Cooldown", 1500, 0, 5000, 50);
    private final NumberValue<Integer> maxPulsePackets = new NumberValue<>("Pulse MaxPackets", 80, 10, 300, 5);

    private final BoolValue playerActivateRange = new BoolValue("PlayerActivateRange", false);
    private final NumberValue<Double> playerActivateRadius = new NumberValue<>("PlayerRange", 20.0, 0.0, 30.0, 0.5, playerActivateRange::get);
    private final NumberValue<Double> blinkPlayerRadius = new NumberValue<>("BlinkPlayerRadius", 0.0, 0.0, 10.0, 0.5);

    private final MultiBoolValue blinkActions = new MultiBoolValue("BlinkActions", Arrays.asList(
        new BoolValue("Vehicle", true),
        new BoolValue("Eat", true),
        new BoolValue("PvpAction", true),
        new BoolValue("Velocity", true),
        new BoolValue("Elytra", true),
        new BoolValue("Inventory", true),
        new BoolValue("Chat", true),
        new BoolValue("Sneak", true),
        new BoolValue("Water", true),
        new BoolValue("StopMotion", true),
        new BoolValue("Potion", true),
        new BoolValue("AnyAction", true)
    ));

    public enum RenderMode { Off, Box }
    private final EnumValue<RenderMode> renderEsp = new EnumValue<>("RenderEsp", RenderMode.Box);
    private final ColorValue chamsColor = new ColorValue("Color", new Color(128, 255, 255, 128), () -> !renderEsp.is(RenderMode.Off));

    private final Deque<QueuedPacket> queuedPackets = new ArrayDeque<>();
    private final Deque<PositionSample> positionHistory = new ArrayDeque<>();

    private boolean pulsing = false;
    private long pulseStartTime = 0L;
    private long lastPulseEndTime = 0L;
    private long lagResumeAtMs = 0L;

    private Vec3d renderPos = null;
    private boolean hasRenderState = false;

    public FakeLag() {
        super("FakeLag", Category.Movement);
        addValues(delayMs, fastMode, fastFlushAll, nextLagDelayMs, maxPacketsPerTick, flushOnDisable, autoBlinkOnStop, pulseMode, pulseDurationMs, pulseCooldownMs, maxPulsePackets, playerActivateRange, playerActivateRadius, blinkPlayerRadius, blinkActions, renderEsp, chamsColor);
    }

    private void addValues(dev.mzc.client.values.Value<?>... values) {
        this.values.addAll(Arrays.asList(values));
    }

    private boolean isDelayActive() {
        return delayMs.get() > 0;
    }

    private boolean isBlinkingInternal() {
        return System.currentTimeMillis() < lagResumeAtMs;
    }

    private void startBlink() {
        long now = System.currentTimeMillis();
        if (flushOnDisable.get()) {
            flushAll();
        } else {
            queuedPackets.clear();
        }
        pulsing = false;
        lagResumeAtMs = now + (long) nextLagDelayMs.get();

        positionHistory.clear();
        if (mc.player != null) {
            positionHistory.addLast(new PositionSample(
                    now,
                    new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()),
                    mc.player.getYaw(),
                    mc.player.getPitch()
            ));
        }

        renderPos = null;
        hasRenderState = false;
    }

    @Override
    public void onEnable() {
        queuedPackets.clear();
        positionHistory.clear();
        pulsing = false;
        pulseStartTime = 0L;
        lastPulseEndTime = 0L;
        lagResumeAtMs = 0L;
        renderPos = null;
        hasRenderState = false;

        if (mc.player != null) {
            long now = System.currentTimeMillis();
            positionHistory.addLast(new PositionSample(now,
                    new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()),
                    mc.player.getYaw(),
                    mc.player.getPitch()
            ));
        }
    }

    @Override
    public void onDisable() {
        if (flushOnDisable.get()) {
            flushAll();
        } else {
            queuedPackets.clear();
        }
        positionHistory.clear();
        pulsing = false;
        lagResumeAtMs = 0L;
        renderPos = null;
        hasRenderState = false;
    }

    private void flushAll() {
        if (queuedPackets.isEmpty()) return;
        while (!queuedPackets.isEmpty()) {
            QueuedPacket qp = queuedPackets.pollFirst();
            if (qp != null && qp.packet() != null && mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(qp.packet());
            }
        }
    }

    private void flushExpiredNormal() {
        if (!isDelayActive()) return;
        if (queuedPackets.isEmpty()) return;

        long now = System.currentTimeMillis();
        long delay = delayMs.get();

        int limit = (fastMode.get() && fastFlushAll.get()) ? Integer.MAX_VALUE : maxPacketsPerTick.get();

        int sent = 0;
        while (!queuedPackets.isEmpty() && sent < limit) {
            QueuedPacket first = queuedPackets.peekFirst();
            if (first == null) break;
            if (now - first.timestamp() >= delay) {
                queuedPackets.pollFirst();
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendPacket(first.packet());
                }
                sent++;
            } else {
                break;
            }
        }
    }

    private void updateHistory() {
        if (mc.player == null) return;

        long now = System.currentTimeMillis();
        positionHistory.addLast(new PositionSample(
                now,
                new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()),
                mc.player.getYaw(),
                mc.player.getPitch()
        ));

        long keepTime = Math.max(delayMs.get(), pulseDurationMs.get()) + 2000L;

        while (!positionHistory.isEmpty()) {
            PositionSample first = positionHistory.peekFirst();
            if (first == null) break;
            if (now - first.timestamp() > keepTime) {
                positionHistory.pollFirst();
            } else {
                break;
            }
        }
    }

    private PositionSample getServerSample() {
        if (positionHistory.isEmpty()) return null;

        long now = System.currentTimeMillis();
        long targetTime = now - delayMs.get();
        PositionSample closest = null;
        long bestDiff = Long.MAX_VALUE;

        for (PositionSample sample : positionHistory) {
            long diff = Math.abs(sample.timestamp() - targetTime);
            if (diff < bestDiff) {
                bestDiff = diff;
                closest = sample;
            }
        }

        return closest;
    }

    private boolean isPlayerStopped() {
        if (mc.player == null) return false;
        Vec3d vel = mc.player.getVelocity();
        double speedSq = vel.x * vel.x + vel.z * vel.z;
        return speedSq < 1e-4;
    }

    private boolean canStartPulse(long now) {
        long cd = pulseCooldownMs.get();
        return !pulsing && (now - lastPulseEndTime >= cd);
    }

    private boolean isPulseExpired(long now) {
        long duration = pulseDurationMs.get();
        return pulsing && (now - pulseStartTime >= duration);
    }

    private boolean hasNearbyPlayer(double radius) {
        if (mc.world == null || mc.player == null) return false;
        if (radius <= 0) return true;
        double r2 = radius * radius;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            if (p.isDead() || p.isRemoved()) continue;
            double d2 = p.squaredDistanceTo(mc.player);
            if (d2 <= r2) return true;
        }
        return false;
    }

    private double nearestPlayerDistance() {
        if (mc.world == null || mc.player == null) return Double.MAX_VALUE;
        double best = Double.MAX_VALUE;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            if (p.isDead() || p.isRemoved()) continue;
            double d = p.distanceTo(mc.player);
            if (d < best) best = d;
        }
        return best;
    }

    @EventHandler
    public void onTick(TickEvent.Pre e) {
        if (nullCheck()) return;

        handleBlinkConditionsTick();

        if (!isLaggingAllowed()) {
            flushAll();
            updateHistory();
            return;
        }

        updateHistory();

        long now = System.currentTimeMillis();

        if (pulseMode.get()) {
            if (pulsing) {
                boolean tooLong = isPulseExpired(now);
                boolean tooManyPackets = queuedPackets.size() >= maxPulsePackets.get();

                if (tooLong || tooManyPackets) {
                    flushAll();
                    pulsing = false;
                    lastPulseEndTime = now;
                }
            }
        } else {
            flushExpiredNormal();

            if (isDelayActive() && autoBlinkOnStop.get() && isPlayerStopped()) {
                startBlink();
            }
        }
    }

    private void handleBlinkConditionsTick() {
        if (mc.player == null || mc.world == null) return;

        double radius = blinkPlayerRadius.get();
        if (radius > 0) {
            double dist = nearestPlayerDistance();
            if (dist <= radius && dist != Double.MAX_VALUE) {
                startBlink();
            }
        }

        if (blinkActions.isEnabled("Vehicle") && mc.player.hasVehicle()) startBlink();
        if (blinkActions.isEnabled("Elytra") && mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) startBlink();
        if (blinkActions.isEnabled("Inventory") && mc.currentScreen instanceof HandledScreen<?>) startBlink();
        if (blinkActions.isEnabled("Chat") && mc.currentScreen instanceof ChatScreen) startBlink();
        if (blinkActions.isEnabled("Eat") && mc.player.isUsingItem() && (mc.player.getActiveItem().getUseAction() == UseAction.EAT || mc.player.getActiveItem().getUseAction() == UseAction.DRINK)) startBlink();
        if (blinkActions.isEnabled("Sneak") && mc.player.isSneaking()) startBlink();
        if (blinkActions.isEnabled("Water") && mc.player.isTouchingWater()) startBlink();
        if (blinkActions.isEnabled("StopMotion") && isPlayerStopped()) startBlink();
        if (blinkActions.isEnabled("Potion") && mc.player.isUsingItem() && mc.player.getActiveItem().getItem().getTranslationKey().contains("potion")) startBlink();
    }

    private boolean isLaggingAllowed() {
        if (!isDelayActive() && !pulseMode.get()) return false;
        if (isBlinkingInternal()) return false;
        return !playerActivateRange.get() || hasNearbyPlayer(playerActivateRadius.get());
    }

    @EventHandler
    public void onPacket(PacketEvent e) {
        if (nullCheck()) return;

        Packet<?> packet = e.getPacket();

        if (e.getType() == EventType.RECEIVE) {
            if (packet instanceof PlayerRespawnS2CPacket || packet instanceof GameJoinS2CPacket) {
                setState(false);
            }
            if (packet instanceof EntityVelocityUpdateS2CPacket vel && blinkActions.isEnabled("Velocity") && mc.player != null && vel.getEntityId() == mc.player.getId()) {
                startBlink();
            }
            if (packet instanceof EntityStatusS2CPacket s2c && blinkActions.isEnabled("Velocity")) {
                if (s2c.getEntity(mc.world) == mc.player) {
                    byte st = s2c.getStatus();
                    if (st == 2 || st == 33) startBlink();
                }
            }
            return;
        }

        if (packet instanceof ClientStatusC2SPacket status && status.getMode().equals(ClientStatusC2SPacket.Mode.PERFORM_RESPAWN)) return;

        if (packet instanceof PlayerInteractEntityC2SPacket && blinkActions.isEnabled("PvpAction")) {
             startBlink();
        }

        if (!isLaggingAllowed()) return;

        long now = System.currentTimeMillis();

        if (pulseMode.get()) {
            if (!pulsing) {
                if (!canStartPulse(now)) return;
                pulsing = true;
                pulseStartTime = now;
            }
            queuedPackets.addLast(new QueuedPacket(now, packet));
            e.cancel();
        } else if (isDelayActive()) {
            queuedPackets.addLast(new QueuedPacket(now, packet));
            e.cancel();
        }
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (nullCheck() || renderEsp.is(RenderMode.Off)) return;

        if (queuedPackets.isEmpty() || !isLaggingAllowed()) return;

        PositionSample sample = getServerSample();
        if (sample == null) return;

        Vec3d targetPos = sample.pos();
        Vec3d localPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        if (targetPos.squaredDistanceTo(localPos) < 0.04) {
            renderPos = null;
            hasRenderState = false;
            return;
        }

        if (!hasRenderState || renderPos == null) {
            renderPos = targetPos;
            hasRenderState = true;
        } else {
            renderPos = renderPos.add(targetPos.subtract(renderPos).multiply(0.2));
        }

        if (renderEsp.is(RenderMode.Box)) {
            Box bb = mc.player.getBoundingBox().offset(renderPos.subtract(localPos));
            Render3DUtil.drawBoxOutline(event.getMatrices(), bb, chamsColor.get().getRGB(), 1.5f);
            Render3DUtil.drawFilledBox(event.getMatrices(), bb, chamsColor.get().getRGB());
        }
    }

    private record QueuedPacket(long timestamp, Packet<?> packet) {}
    private record PositionSample(long timestamp, Vec3d pos, float yaw, float pitch) {}
}
