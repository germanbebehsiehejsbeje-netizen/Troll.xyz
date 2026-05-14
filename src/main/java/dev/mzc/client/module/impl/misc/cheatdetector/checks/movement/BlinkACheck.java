package dev.mzc.client.module.impl.misc.cheatdetector.checks.movement;

import dev.mzc.client.module.impl.misc.cheatdetector.CheatCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.CheatDetectorContext;
import dev.mzc.client.module.impl.misc.cheatdetector.TrackedPlayer;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;

public class BlinkACheck implements CheatCheck {
    private final BoolValue enabled;
    private final NumberValue<Double> maxDistance;

    public BlinkACheck(BoolValue enabled, NumberValue<Double> maxDistance) {
        this.enabled = enabled;
        this.maxDistance = maxDistance;
    }

    @Override
    public void onTick(PlayerEntity player, TrackedPlayer tp, CheatDetectorContext ctx) {
        if (!enabled.get()) return;
        if (!tp.hasPosDelta()) return;

        double dist = tp.posDelta().length();
        double speedMul = speedMul(player);
        double limit = maxDistance.get() * speedMul + player.fallDistance + 0.1;
        if (dist > limit) {
            ctx.notify(player, String.format("BlinkA (Current: %.2f Max: %.2f)", dist, limit));
        }
    }

    private static double speedMul(PlayerEntity player) {
        double mul = 1.0;
        StatusEffectInstance speed = player.getStatusEffect(StatusEffects.SPEED);
        if (speed != null) {
            mul *= speed.getAmplifier() * 0.2 + 1.0;
        }
        mul *= player.getMovementSpeed() * 10.0;
        return mul;
    }
}
