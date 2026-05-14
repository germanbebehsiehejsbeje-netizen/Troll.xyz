package dev.mzc.client.module.impl.misc.cheatdetector.checks;

import dev.mzc.client.module.impl.misc.cheatdetector.CheatCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.CheatDetectorContext;
import dev.mzc.client.module.impl.misc.cheatdetector.TrackedPlayer;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import net.minecraft.entity.player.PlayerEntity;

public class AimSmoothCheck implements CheatCheck {
    private final BoolValue enabled;
    private final NumberValue<Integer> smoothWindow;
    private final NumberValue<Double> smoothVarEps;
    private final NumberValue<Double> minYawSpeed;
    private final NumberValue<Double> maxYawSpeed;

    public AimSmoothCheck(BoolValue enabled, NumberValue<Integer> smoothWindow, NumberValue<Double> smoothVarEps, NumberValue<Double> minYawSpeed, NumberValue<Double> maxYawSpeed) {
        this.enabled = enabled;
        this.smoothWindow = smoothWindow;
        this.smoothVarEps = smoothVarEps;
        this.minYawSpeed = minYawSpeed;
        this.maxYawSpeed = maxYawSpeed;
    }

    @Override
    public void onTick(PlayerEntity player, TrackedPlayer tracked, CheatDetectorContext context) {
        if (!enabled.get()) return;
        if (tracked.yawDeltaHist().size() < smoothWindow.get()) return;

        double avg = 0;
        for (float v : tracked.yawDeltaHist()) avg += v;
        avg /= tracked.yawDeltaHist().size();
        double var = 0;
        for (float v : tracked.yawDeltaHist()) {
            double d = v - avg;
            var += d * d;
        }
        var /= tracked.yawDeltaHist().size();
        double spd = Math.abs(avg);
        if (var <= smoothVarEps.get() && spd >= minYawSpeed.get() && spd <= maxYawSpeed.get()) {
            context.notify(player, "转头平滑度异常");
        }
    }
}

