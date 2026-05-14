package dev.mzc.client.module.impl.misc.cheatdetector.checks.movement;

import dev.mzc.client.module.impl.misc.cheatdetector.CheatCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.CheatDetectorContext;
import dev.mzc.client.module.impl.misc.cheatdetector.TrackedPlayer;
import dev.mzc.client.values.impl.BoolValue;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;

public class MotionBCheck implements CheatCheck {
    private final BoolValue enabled;

    public MotionBCheck(BoolValue enabled) {
        this.enabled = enabled;
    }

    @Override
    public void onTick(PlayerEntity player, TrackedPlayer tp, CheatDetectorContext ctx) {
        if (!enabled.get()) return;

        if (tp.currentOnGround()
                || player.hasStatusEffect(StatusEffects.SLOW_FALLING)
                || player.hasVehicle()
                || player.isTouchingWater() || player.isInLava()
                || player.isGliding()
                || player.isClimbing()) {
            return;
        }

        double should = predictedMotion(tp.lastTickMotion().y, 1);
        double current = tp.currentTickMotion().y;
        if (Math.abs(current - should) > 0.01) {
            ctx.notify(player, String.format("MotionB (should: %.2f current: %.2f)", should, current));
        }
    }

    private static double predictedMotion(double motion, int ticks) {
        if (ticks <= 0) return motion;
        double predicted = motion;
        for (int i = 0; i < ticks; i++) {
            predicted = (predicted - 0.08) * 0.98;
        }
        return predicted;
    }
}
