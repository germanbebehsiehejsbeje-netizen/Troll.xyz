package dev.mzc.client.module.impl.misc.cheatdetector.checks.movement;

import dev.mzc.client.module.impl.misc.cheatdetector.CheatCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.CheatDetectorContext;
import dev.mzc.client.module.impl.misc.cheatdetector.TrackedPlayer;
import dev.mzc.client.values.impl.BoolValue;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

public class SpeedCCheck implements CheatCheck {
    private final BoolValue enabled;

    public SpeedCCheck(BoolValue enabled) {
        this.enabled = enabled;
    }

    @Override
    public void onTick(PlayerEntity player, TrackedPlayer tp, CheatDetectorContext ctx) {
        if (!enabled.get()) return;

        if (player.isSprinting() && !player.isSwimming() && !player.hasVehicle()) {
            double speed = Math.hypot(tp.posDelta().x, tp.posDelta().z);
            if (speed == 0.0) {
                Vec3d motion = player.getVelocity();
                if (motion.x != 0.0 || motion.z != 0.0) {
                    ctx.notify(player, String.format("SpeedC (MotionX:%.2f MotionZ:%.2f)", motion.x, motion.z));
                }
            }
        }
    }
}
