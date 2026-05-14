package dev.mzc.client.module.impl.misc.cheatdetector.checks;

import dev.mzc.client.module.impl.misc.cheatdetector.CheatCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.CheatDetectorContext;
import dev.mzc.client.module.impl.misc.cheatdetector.TrackedPlayer;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import net.minecraft.entity.player.PlayerEntity;

public class VelocityResponseCheck implements CheatCheck {
    private final BoolValue enabled;
    private final NumberValue<Double> minVelocityApply;
    private final NumberValue<Integer> velWindowTicks;

    public VelocityResponseCheck(BoolValue enabled, NumberValue<Double> minVelocityApply, NumberValue<Integer> velWindowTicks) {
        this.enabled = enabled;
        this.minVelocityApply = minVelocityApply;
        this.velWindowTicks = velWindowTicks;
    }

    @Override
    public void onTick(PlayerEntity player, TrackedPlayer tracked, CheatDetectorContext context) {
        if (!enabled.get()) return;
        if (tracked.lastVelocityTick() < 0) return;

        long dt = context.worldTick() - tracked.lastVelocityTick();
        if (dt < 0) {
            tracked.clearVelocityWindow();
            return;
        }
        if (dt > velWindowTicks.get()) {
            tracked.clearVelocityWindow();
            return;
        }

        double expected = Math.hypot(tracked.lastVelocity().x, tracked.lastVelocity().z);
        if (expected > 0.01 && tracked.maxObservedHSpeedSinceVel() < expected * minVelocityApply.get()) {
            context.notify(player, "击退响应异常");
            tracked.clearVelocityWindow();
        }
    }
}

