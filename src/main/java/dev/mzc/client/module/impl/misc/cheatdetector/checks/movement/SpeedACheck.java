package dev.mzc.client.module.impl.misc.cheatdetector.checks.movement;

import dev.mzc.client.module.impl.misc.cheatdetector.CheatCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.CheatDetectorContext;
import dev.mzc.client.module.impl.misc.cheatdetector.TrackedPlayer;
import dev.mzc.client.values.impl.BoolValue;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;

public class SpeedACheck implements CheatCheck {
    private final BoolValue enabled;

    // Converted speeds from AdvancedConfig per-second to per-tick (divided by 20)
    private static final double SPEED_AFTER_JUMP = 7.4 / 20.0;
    private static final double SPEED_SPRINT = 5.612 / 20.0;
    private static final double SPEED_SNEAK = 1.295 / 20.0;
    private static final double SPEED_WALK = 4.317 / 20.0;
    private static final double THRESHOLD = 0.08;

    public SpeedACheck(BoolValue enabled) {
        this.enabled = enabled;
    }

    @Override
    public void onTick(PlayerEntity player, TrackedPlayer tp, CheatDetectorContext ctx) {
        if (!enabled.get()) return;

        // Check if player is on ground and not in water
        if (!tp.hasPosDelta() || !tp.currentOnGround() || !tp.lastOnGround() || player.isTouchingWater()) return;

        double maxTickSpeed;
        if (tp.ticksSinceOffGround() < 10) {
            maxTickSpeed = SPEED_AFTER_JUMP;
        } else if (player.isSprinting()) {
            maxTickSpeed = SPEED_SPRINT;
        } else if (player.isSneaking()) {
            maxTickSpeed = SPEED_SNEAK;
        } else {
            maxTickSpeed = SPEED_WALK;
        }

        // Calculate speed multiplier from effects (similar to TRPlayer logic)
        double speedMul = 1.0;
        StatusEffectInstance speedEffect = player.getStatusEffect(StatusEffects.SPEED);
        if (speedEffect != null) {
            speedMul = speedEffect.getAmplifier() * 0.2 + 1.0;
        }
        
        // Use player.getMovementSpeed() * 10.0 to match the original multiplier base
        speedMul *= player.getMovementSpeed() * 10.0;

        double speed = Math.hypot(tp.posDelta().x, tp.posDelta().z);
        double possibleSpeed = maxTickSpeed * speedMul + THRESHOLD;

        if (speed > possibleSpeed) {
            ctx.notify(player, String.format("SpeedA (Current: %.2f Max: %.2f)", speed, possibleSpeed));
        }
    }
}
