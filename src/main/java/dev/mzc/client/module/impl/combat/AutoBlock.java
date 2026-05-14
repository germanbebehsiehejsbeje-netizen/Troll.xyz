package dev.mzc.client.module.impl.combat;

import dev.mzc.client.Sakura;
import dev.mzc.client.events.entity.AttackEvent;
import dev.mzc.client.events.input.HandleInputEvent;
import dev.mzc.client.events.packet.PacketEvent;
import dev.mzc.client.mixin.accessor.IPlayerInteractEntityC2SPacket;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.packet.PacketUtil;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import dev.mzc.client.values.impl.RangeValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Hand;
import net.minecraft.registry.tag.ItemTags;

import java.util.ArrayList;
import java.util.List;

public class AutoBlock extends Module {
    public enum Mode {
        Vanilla(),
        HypixelFull();
        Mode() {
        }
    }

    private final EnumValue<Mode> mode = new EnumValue<>("Mode", Mode.Vanilla, Mode.class);
    private final NumberValue<Double> extraRange = new NumberValue<>("ExtraRange", 0.5, 0.0, 10.0, 0.1);
    private final RangeValue<Double> blockChance = new RangeValue<>("BlockChance", 80.0, 100.0, 0.0, 100.0, 1.0);

    private Entity target;
    private boolean wasBlinking;
    private int abTick;
    private boolean wasPacketBlocking;

    public AutoBlock() {
        super("AutoBlock", Category.Combat);
        this.setType(ModuleType.Hack);
    }

    @Override
    protected void onEnable() {
        resetInternal();
    }

    @Override
    protected void onDisable() {
        if (mc.player != null) {
            resetPacketUnblocking();
        }
        wasPacketBlocking = false;
        resetInternal();
    }

    @EventHandler
    public void onHandleInput(HandleInputEvent event) {
        if (nullCheck()) return;
        if (target == null) return;

        if (mc.player.distanceTo(target) > getRealAttackReach()) {
            if (mode.is(Mode.Vanilla)) {
                resetPacketUnblocking();
            } else if (mode.is(Mode.HypixelFull)) {
                if (wasBlinking) {
                    BlinkUtil.sync(true, true);
                    BlinkUtil.stopBlink();
                    wasBlinking = false;
                }
            }
        }
    }

    @EventHandler
    private void onAttackEntity(AttackEvent event) {
        if (nullCheck()) return;

        target = event.getTargetEntity();
        handleAttack();
    }

    @EventHandler
    private void onPacket(PacketEvent event) {
        if (!isEnabled()) return;
        if (nullCheck()) return;

        Packet<?> packet = event.getPacket();
        if (event.getType() == dev.mzc.client.events.EventType.SENT) {
            if (packet instanceof PlayerInteractEntityC2SPacket) {
                int id = ((IPlayerInteractEntityC2SPacket) packet).getEntityId();
                Entity e = mc.world.getEntityById(id);
                if (e != null) {
                    target = e;
                }
                handleAttack();
            }
            return;
        }

        if (event.getType() == dev.mzc.client.events.EventType.SEND) {
            if (BlinkUtil.isBlinking() && !BlinkUtil.isLimiter() && packet instanceof PlayerMoveC2SPacket) {
                event.setCancelled(true);
                BlinkUtil.addPacket(packet);
            }
        }
    }

    private void handleAttack() {
        ItemStack mainHand = mc.player.getMainHandStack();
        if (!mainHand.isIn(ItemTags.SWORDS)) return;

        double chance = blockChance.getMinValue() + (blockChance.getMaxValue() - blockChance.getMinValue()) * Math.random();
        if (Math.random() * 100 > chance) return;

        abTick++;

        if (mode.is(Mode.Vanilla)) {
            if (mc.player.getOffHandStack().getItem() instanceof ShieldItem) {
                sendUseItem(Hand.OFF_HAND);
                return;
            }

            sendUseItem(Hand.MAIN_HAND);
            wasPacketBlocking = true;
            return;
        }

        if (mode.is(Mode.HypixelFull)) {
            if (abTick >= 1) {
                BlinkUtil.doBlink();
                sendUseItemNoBlink(Hand.MAIN_HAND);
                wasBlinking = true;
                abTick = 0;
            }
        }
    }

    private double getRealAttackReach() {
        return mc.player.getEntityInteractionRange() + extraRange.get();
    }

    private void sendUseItem(Hand hand) {
        PacketUtil.sendSequencedPacket(seq -> new PlayerInteractItemC2SPacket(hand, seq, mc.player.getYaw(), mc.player.getPitch()));
    }

    private void sendUseItemNoBlink(Hand hand) {
        boolean prev = BlinkUtil.isLimiter();
        BlinkUtil.setLimiter(true);
        try {
            sendUseItem(hand);
        } finally {
            BlinkUtil.setLimiter(prev);
        }
    }

    private void resetPacketUnblocking() {
        if (!wasPacketBlocking) return;
        if (mc.getNetworkHandler() == null) return;

        int slot = mc.player.getInventory().getSelectedSlot();
        int alt = slot == 0 ? 1 : slot - 1;

        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(alt));
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        wasPacketBlocking = false;
    }

    private void resetInternal() {
        if (mode.is(Mode.HypixelFull) && wasBlinking) {
            BlinkUtil.sync(true, true);
            BlinkUtil.stopBlink();
        }
        abTick = 0;
        target = null;
        wasBlinking = false;
    }

    private static class BlinkUtil {
        private static boolean blinking;
        private static boolean started;
        private static boolean limiter;
        private static final List<Packet<?>> packets = new ArrayList<>();
        private static final List<Vec3d> positions = new ArrayList<>();
        private static Double prevYMotion;

        private static boolean isBlinking() {
            return blinking;
        }

        private static boolean isLimiter() {
            return limiter;
        }

        private static void setLimiter(boolean limiter) {
            BlinkUtil.limiter = limiter;
        }

        private static void doBlink() {
            if (Sakura.mc.player == null) return;

            if (!started) {
                started = true;
                positions.clear();

                double x = Sakura.mc.player.getX();
                double z = Sakura.mc.player.getZ();
                double minY = Sakura.mc.player.getBoundingBox().minY;
                positions.add(new Vec3d(x, minY + Sakura.mc.player.getHeight() / 2.0, z));
                positions.add(new Vec3d(x, minY, z));

                prevYMotion = Sakura.mc.player.getVelocity().y;
            }

            blinking = true;
            positions.add(new Vec3d(Sakura.mc.player.getX(), Sakura.mc.player.getY(), Sakura.mc.player.getZ()));
        }

        private static void stopBlink() {
            blinking = false;
            started = false;
            positions.clear();
            packets.clear();
            prevYMotion = null;
        }

        private static void addPacket(Packet<?> packet) {
            packets.add(packet);
        }

        private static void sync(boolean sendPackets, boolean teleportBack) {
            if (Sakura.mc.player == null || Sakura.mc.getNetworkHandler() == null) return;

            limiter = true;
            try {
                if (sendPackets) {
                    for (int i = 0; i < packets.size(); i++) {
                        Sakura.mc.getNetworkHandler().sendPacket(packets.get(i));
                    }
                }
                packets.clear();

                if (teleportBack && positions.size() > 1) {
                    Vec3d back = positions.get(1);
                    Sakura.mc.player.setPosition(back.x, back.y, back.z);
                    if (prevYMotion != null) {
                        Vec3d v = Sakura.mc.player.getVelocity();
                        Sakura.mc.player.setVelocity(v.x, prevYMotion, v.z);
                    }
                }
                positions.clear();
            } finally {
                limiter = false;
            }
        }
    }
}


