package dev.mzc.client.module.impl.misc.cheatdetector.checks.aim;

import dev.mzc.client.module.impl.misc.cheatdetector.CheatCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.CheatDetectorContext;
import dev.mzc.client.module.impl.misc.cheatdetector.TrackedPlayer;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Set;

public class AimStepCheck implements CheatCheck {
    private static final Set<Integer> YAW_STEP = Set.of(90, 135, 180);
    private static final Set<Integer> PITCH_STEP = Set.of(90, 135);

    private final BoolValue enabled;
    private final NumberValue<Double> minDiffYaw;
    private final NumberValue<Double> minDiffPitch;

    public AimStepCheck(BoolValue enabled, NumberValue<Double> minDiffYaw, NumberValue<Double> minDiffPitch) {
        this.enabled = enabled;
        this.minDiffYaw = minDiffYaw;
        this.minDiffPitch = minDiffPitch;
    }

    @Override
    public void onTick(PlayerEntity player, TrackedPlayer tracked, CheatDetectorContext context) {
        if (!enabled.get()) return;

        boolean flagPitch = false;
        boolean flagYaw = false;
        double stepPitch = 0;
        double stepYaw = 0;

        double deltaPitch = tracked.lastAbsPitchDelta();
        double deltaYaw = tracked.lastAbsYawDelta();

        for (int step : PITCH_STEP) {
            if (Math.abs(deltaPitch - step) < minDiffPitch.get()) {
                flagPitch = true;
                stepPitch = deltaPitch;
                break;
            }
        }
        for (int step : YAW_STEP) {
            if (Math.abs(deltaYaw - step) < minDiffYaw.get()) {
                flagYaw = true;
                stepYaw = deltaYaw;
                break;
            }
        }

        if (flagPitch && flagYaw) {
            context.notify(player, "Perfect step aim");
        } else if (flagPitch) {
            context.notify(player, "Perfect pitch step aim");
        } else if (flagYaw) {
            context.notify(player, "Perfect yaw step aim");
        }
    }
}

