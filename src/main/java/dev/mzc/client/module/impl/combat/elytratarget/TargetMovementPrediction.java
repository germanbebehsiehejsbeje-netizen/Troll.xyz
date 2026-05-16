package dev.mzc.client.module.impl.combat.elytratarget;

import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

public class TargetMovementPrediction {
    private PredictMode predictMode = PredictMode.SIMPLE;

    private final BoolValue prediction = new BoolValue("Prediction", true);
    private final EnumValue<PredictMode> mode = new EnumValue<>("PredictMode", PredictMode.SIMPLE);
    private final BoolValue glidingOnly = new BoolValue("GlidingOnly", true);
    private final NumberValue<Double> multiplier = new NumberValue<>("Multiplier", 1.8, 0.5, 6.0, 0.1);

    public TargetMovementPrediction() {
    }

    public Vec3d predictPosition(LivingEntity target, Vec3d targetPosition) {
        if (!prediction.get() || getEntityBPS(target) < 13 || (glidingOnly.get() && !target.isGliding())) {
            return targetPosition;
        }

        double mult = multiplier.get();
        return predictMode.predict(target, targetPosition, mult);
    }

    private double getEntityBPS(LivingEntity entity) {
        Vec3d velocity = entity.getVelocity();
        return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) * 20.0;
    }

    public BoolValue getPrediction() {
        return prediction;
    }

    public EnumValue<PredictMode> getMode() {
        return mode;
    }

    public BoolValue getGlidingOnly() {
        return glidingOnly;
    }

    public NumberValue<Double> getMultiplier() {
        return multiplier;
    }

    private enum PredictMode {
        SIMPLE("Simple", (target, targetPosition, multiplier) ->
                targetPosition.add(target.getVelocity().multiply(multiplier))
        ),

        VELOCITY("Velocity", (target, targetPosition, multiplier) -> {
            Vec3d simple = SIMPLE.predict(target, targetPosition, multiplier);
            return simple.subtract(0.0, 0.5 * 0.05 * multiplier * multiplier, 0.0);
        });

        private final String name;
        private final PredictFunction predict;

        PredictMode(String name, PredictFunction predict) {
            this.name = name;
            this.predict = predict;
        }

        public String getName() {
            return name;
        }

        public Vec3d predict(LivingEntity target, Vec3d targetPosition, double multiplier) {
            return predict.apply(target, targetPosition, multiplier);
        }

        @FunctionalInterface
        interface PredictFunction {
            Vec3d apply(LivingEntity target, Vec3d targetPosition, double multiplier);
        }
    }
}
