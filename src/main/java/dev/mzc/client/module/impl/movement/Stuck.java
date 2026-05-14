package dev.mzc.client.module.impl.movement;

import dev.mzc.client.events.EventType;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.input.MoveInputEvent;
import dev.mzc.client.events.packet.PacketEvent;
import dev.mzc.client.events.player.MotionEvent;
import dev.mzc.client.manager.Managers;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Stuck extends Module {
    private final BoolValue autoStuck = new BoolValue("AutoStuck", false);
    private final NumberValue<Double> autoY = new NumberValue<>("AutoY", 0.0, -64.0, 320.0, 1.0, autoStuck::get);
    private final NumberValue<Integer> autoCooldownMs = new NumberValue<>("AutoCooldown", 3000, 0, 60000, 100, autoStuck::get);

    private final BoolValue autoPause = new BoolValue("AutoPause", false);
    private final NumberValue<Integer> activeMs = new NumberValue<>("ActiveMs", 3000, 100, 60000, 100, autoPause::get);
    private final NumberValue<Integer> pauseMs = new NumberValue<>("PauseMs", 1000, 0, 60000, 100, autoPause::get);

    private int stage = 0;
    private Packet<?> actionPacket;
    private float lastYaw;
    private float lastPitch;
    private boolean tryDisable = false;
    private boolean sending = false;
    private boolean autoEngaged = true;
    private long lastAutoEndTime = 0L;
    private boolean pausedByAutoPause = false;
    private long autoPausePhaseStart = 0L;
    private final Queue<CommonPongC2SPacket> pongPackets = new ConcurrentLinkedQueue<>();

    public Stuck() {
        super("Stuck", Category.Movement);
        this.setType(ModuleType.Hack);
    }

    @Override
    protected void onEnable() {
        autoEngaged = !autoStuck.get();
        lastAutoEndTime = 0L;
        pausedByAutoPause = false;
        autoPausePhaseStart = System.currentTimeMillis();
        resetSession();
    }

    @Override
    protected void onDisable() {
        autoEngaged = true;
        lastAutoEndTime = 0L;
        pausedByAutoPause = false;
        autoPausePhaseStart = 0L;
        resetSession();
    }

    @Override
    public void setState(boolean state) {
        if (mc.player == null) return;
        if (state) {
            super.setState(true);
            return;
        }

        if (autoStuck.get() && !autoEngaged) {
            super.setState(false);
            return;
        }

        if (autoPause.get() && pausedByAutoPause) {
            super.setState(false);
            return;
        }

        if (this.stage == 3) {
            super.setState(false);
        } else {
            this.tryDisable = true;
        }
    }

    @EventHandler
    public void onTick(TickEvent.Pre e) {
        if (nullCheck()) return;

        long now = System.currentTimeMillis();

        if (autoPause.get()) {
            if (pausedByAutoPause) {
                if (now - autoPausePhaseStart >= pauseMs.get()) {
                    pausedByAutoPause = false;
                    autoPausePhaseStart = now;
                    resetSession();
                }
            } else {
                if (now - autoPausePhaseStart >= activeMs.get()) {
                    pausedByAutoPause = true;
                    autoPausePhaseStart = now;
                    stage = 0;
                    actionPacket = null;
                    tryDisable = false;
                    pongPackets.clear();
                }
            }
        } else {
            pausedByAutoPause = false;
        }

        if (autoStuck.get()) {
            double y = mc.player.getY();

            if (!autoEngaged) {
                if (y <= autoY.get() && now - lastAutoEndTime >= autoCooldownMs.get()) {
                    autoEngaged = true;
                    resetSession();
                }
            } else {
                if (y > autoY.get() + 1.0) {
                    autoEngaged = false;
                    lastAutoEndTime = now;
                    stage = 0;
                    actionPacket = null;
                    tryDisable = false;
                    pongPackets.clear();
                }
            }
        } else {
            autoEngaged = true;
        }
    }

    @EventHandler
    public void onMotion(MotionEvent e) {
        if (nullCheck()) return;

        if (e.getType() == EventType.PRE && mc.player.age <= 1) {
            stage = 3;
            actionPacket = null;
            toggle();
            return;
        }

        if (!isEffectActive()) return;

        if (e.getType() != EventType.PRE) return;

        mc.player.setVelocity(0.0, 0.0, 0.0);

        if (stage == 1) {
            stage = 2;

            float rotationYaw = mc.player.getYaw();
            float rotationPitch = mc.player.getPitch();

            if (shouldRotate() && (lastYaw != rotationYaw || lastPitch != rotationPitch)) {
                sendNoEvent(new PlayerMoveC2SPacket.LookAndOnGround(rotationYaw, rotationPitch, mc.player.isOnGround(), mc.player.horizontalCollision));
                flushPongs();
                lastYaw = rotationYaw;
                lastPitch = rotationPitch;
            }

            if (actionPacket != null) {
                sendNoEvent(actionPacket);
            }
        }

        if (tryDisable) {
            sendNoEvent(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX() + 1337.0,
                    mc.player.getY(),
                    mc.player.getZ() + 1337.0,
                    mc.player.isOnGround(),
                    mc.player.horizontalCollision
            ));
            flushPongs();
            tryDisable = false;
            stage = 3;
            super.setState(false);
        }
    }

    private boolean shouldRotate() {
        if (actionPacket instanceof PlayerInteractItemC2SPacket useItem) {
            ItemStack item = mc.player.getStackInHand(useItem.getHand());
            boolean isBowlFood =
                    item.contains(DataComponentTypes.FOOD)
                            && (item.isOf(Items.MUSHROOM_STEW)
                            || item.isOf(Items.RABBIT_STEW)
                            || item.isOf(Items.BEETROOT_SOUP)
                            || item.isOf(Items.SUSPICIOUS_STEW));
            return !isBowlFood && !(item.getItem() instanceof BowItem);
        }

        if (actionPacket instanceof PlayerActionC2SPacket digging) {
            return digging.getAction() == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM
                    && mc.player.getActiveItem().getItem() instanceof BowItem;
        }

        return true;
    }

    @EventHandler
    public void onMoveInput(MoveInputEvent event) {
        if (nullCheck()) return;
        if (!isEffectActive()) return;
        event.setForward(0.0F);
        event.setStrafe(0.0F);
        event.setJump(false);
        event.setSneak(false);
    }

    @EventHandler
    public void onPacket(PacketEvent event) {
        if (nullCheck()) return;
        if (!isEffectActive()) return;
        if (event.getType() != EventType.SEND && event.getType() != EventType.RECEIVE) return;
        if (sending) return;

        Packet<?> p = event.getPacket();

        if (event.getType() == EventType.SEND) {
            if (p instanceof PlayerMoveC2SPacket) {
                event.setCancelled(true);
            } else if (p instanceof CommonPongC2SPacket pong) {
                pongPackets.offer(pong);
                event.setCancelled(true);
            } else if (p instanceof PlayerInteractItemC2SPacket || p instanceof PlayerActionC2SPacket) {
                actionPacket = p;
                stage = 1;
                event.setCancelled(true);
            }
        } else {
            if (p instanceof PlayerPositionLookS2CPacket) {
                flushPongs();
                stage = 3;
                toggle();
            }
        }
    }

    private void flushPongs() {
        while (!pongPackets.isEmpty()) {
            sendNoEvent(pongPackets.poll());
        }
    }

    private void sendNoEvent(Packet<?> packet) {
        if (mc.getNetworkHandler() == null) return;
        sending = true;
        try {
            mc.getNetworkHandler().sendPacket(packet);
        } finally {
            sending = false;
        }
    }

    private void resetSession() {
        stage = 0;
        actionPacket = null;
        lastYaw = Managers.ROTATION.getYaw();
        lastPitch = Managers.ROTATION.getPitch();
        tryDisable = false;
        sending = false;
        pongPackets.clear();
    }

    private boolean isEffectActive() {
        if (autoPause.get() && pausedByAutoPause) return false;
        if (autoStuck.get() && !autoEngaged) return false;
        return true;
    }
}
