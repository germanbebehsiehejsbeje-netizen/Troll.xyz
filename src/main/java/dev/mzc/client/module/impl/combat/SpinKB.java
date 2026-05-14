package dev.mzc.client.module.impl.combat;

import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.entity.AttackEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;

public class SpinKB extends Module {
    private final NumberValue<Double> yawOffset = new NumberValue<>("YawOffset", 70.0, 5.0, 180.0, 1.0);
    private final NumberValue<Integer> packets = new NumberValue<>("Packets", 3, 1, 10, 1);
    private final NumberValue<Integer> restoreDelayTicks = new NumberValue<>("RestoreDelay", 0, 0, 5, 1);
    private final BoolValue sendRestorePacket = new BoolValue("SendRestorePacket", true);
    private final BoolValue onlySprint = new BoolValue("OnlySprint", false);

    private boolean restoring;
    private int restoreTicksLeft;
    private float restoreYaw;
    private float restorePitch;

    public SpinKB() {
        super("SpinKB", Category.Combat);
        this.setType(ModuleType.Hack);
    }

    @EventHandler
    private void onAttack(AttackEvent event) {
        if (nullCheck()) return;
        if (onlySprint.get() && !mc.player.isSprinting()) return;

        float baseYaw = mc.player.getYaw();
        float basePitch = mc.player.getPitch();

        float a = (float) yawOffset.get().doubleValue();
        float yawA = MathHelper.wrapDegrees(baseYaw + a);
        float yawB = MathHelper.wrapDegrees(baseYaw - a);

        int n = packets.get();
        float lastYaw = baseYaw;
        for (int i = 0; i < n; i++) {
            float yaw = (i % 2 == 0) ? yawA : yawB;
            lastYaw = yaw;
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, basePitch, mc.player.isOnGround(), mc.player.horizontalCollision));
        }

        mc.player.setYaw(lastYaw);
        mc.player.setPitch(basePitch);

        restoring = true;
        restoreTicksLeft = restoreDelayTicks.get();
        restoreYaw = baseYaw;
        restorePitch = basePitch;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!restoring) return;
        if (nullCheck()) {
            restoring = false;
            return;
        }

        if (restoreTicksLeft > 0) {
            restoreTicksLeft--;
            return;
        }

        mc.player.setYaw(restoreYaw);
        mc.player.setPitch(restorePitch);
        if (sendRestorePacket.get()) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(restoreYaw, restorePitch, mc.player.isOnGround(), mc.player.horizontalCollision));
        }

        restoring = false;
    }
}
