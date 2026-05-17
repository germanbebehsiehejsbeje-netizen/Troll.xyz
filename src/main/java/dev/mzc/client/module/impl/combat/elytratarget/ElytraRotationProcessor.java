package dev.mzc.client.module.impl.combat.elytratarget;

import dev.mzc.client.manager.Managers;
import dev.mzc.client.manager.impl.RotationManager;
import dev.mzc.client.utils.rotation.MovementFix;
import dev.mzc.client.utils.rotation.RotationUtil;
import dev.mzc.client.utils.vector.Rotation;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import static dev.mzc.client.Sakura.mc;

/**
 * Computes the target rotation for ElytraTarget and submits it to {@link Managers#ROTATION}.
 *
 * <p>The legacy implementation tried to do its own smoothing on top of {@link RotationManager},
 * which both fought our manager and produced erratic angular motion (sin/cos boosts, random
 * micro-adjustments, infinite rotation speed). Anti-cheats flag this immediately. Now we just
 * compute a clean target and let RotationManager smooth + emit motion packets.
 */
public class ElytraRotationProcessor {
    private final ElytraTargetModule elytraTargetModule;

    private static final int IDEAL_DISTANCE = 10;

    private final TargetMovementPrediction predict = new TargetMovementPrediction();

    public final BoolValue customRotations = new BoolValue("CustomRotations", true);
    private final BoolValue sharpRotations = new BoolValue("SharpRotations", false);
    private final BoolValue autoDistance = new BoolValue("AutoDistance", true);
    private final EnumValue<TargetPosition> rotateAt = new EnumValue<>("RotateAt", TargetPosition.ABOVE);
    private final NumberValue<Double> rotationSpeed =
            new NumberValue<>("RotationSpeed", 1.0, 0.1, 2.0, 0.05);

    public ElytraRotationProcessor(ElytraTargetModule elytraTargetModule) {
        this.elytraTargetModule = elytraTargetModule;
    }

    public boolean using() {
        return elytraTargetModule.isEnabled() &&
               mc.player != null &&
               mc.player.isGliding();
    }

    public void processRotation() {
        if (!using()) return;
        if (!customRotations.get()) return;

        LivingEntity target = elytraTargetModule.getTarget();
        if (target == null) return;

        Rotation targetRotation = calculateRotation(target);

        // Single source of truth for rotation smoothing — let RotationManager do its job.
        // High priority keeps us above generic modules but not so high that we override user intent.
        // MovementFix.NORMAL keeps the body yaw aligned with the head — server sees a coherent player.
        double speed = sharpRotations.get() ? rotationSpeed.get() * 1.5 : rotationSpeed.get();
        Managers.ROTATION.setRotations(
                targetRotation,
                speed,
                MovementFix.NORMAL,
                RotationManager.Priority.High
        );
    }

    public Rotation calculateRotation(LivingEntity target) {
        Vec3d targetPos = getPredictedPos(target);

        if (autoDistance.get() && mc.player != null) {
            Vec3d playerPos = mc.player.getEyePos();
            Vec3d delta = targetPos.subtract(playerPos);
            double distance = delta.length();

            if (distance > 1.0E-4 && distance < IDEAL_DISTANCE) {
                Vec3d direction = delta.normalize();
                targetPos = targetPos.subtract(direction.multiply(IDEAL_DISTANCE - distance));
            }
        }

        return RotationUtil.calculate(targetPos);
    }

    public Vec3d getPredictedPos(LivingEntity target) {
        Vec3d basePos = rotateAt.get().getPosition(target);
        return predict.predictPosition(target, basePos);
    }

    public TargetMovementPrediction getPredict() {
        return predict;
    }

    public BoolValue getCustomRotations() {
        return customRotations;
    }

    public BoolValue getSharpRotations() {
        return sharpRotations;
    }

    public BoolValue getAutoDistance() {
        return autoDistance;
    }

    public EnumValue<TargetPosition> getRotateAt() {
        return rotateAt;
    }

    public NumberValue<Double> getRotationSpeed() {
        return rotationSpeed;
    }
}
