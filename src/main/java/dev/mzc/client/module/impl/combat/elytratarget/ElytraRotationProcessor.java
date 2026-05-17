package dev.mzc.client.module.impl.combat.elytratarget;

import dev.mzc.client.manager.Managers;
import dev.mzc.client.manager.impl.RotationManager;
import dev.mzc.client.module.impl.combat.KillAura;
import dev.mzc.client.utils.rotation.MovementFix;
import dev.mzc.client.utils.rotation.RotationUtil;
import dev.mzc.client.utils.vector.Rotation;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import static dev.mzc.client.Sakura.mc;

public class ElytraRotationProcessor {
    private final ElytraTargetModule elytraTargetModule;
    private TargetPosition targetPositionMode = TargetPosition.CENTER;

    private static final float BASE_YAW_SPEED = 45.0f;
    private static final float BASE_PITCH_SPEED = 35.0f;
    private static final int IDEAL_DISTANCE = 10;

    private final TargetMovementPrediction predict = new TargetMovementPrediction();

    public final BoolValue customRotations = new BoolValue("CustomRotations", true);
    private final BoolValue sharpRotations = new BoolValue("SharpRotations", false);
    private final BoolValue autoDistance = new BoolValue("AutoDistance", true);
    private final EnumValue<TargetPosition> rotateAt = new EnumValue<>("RotateAt", TargetPosition.CENTER);

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
        
        LivingEntity target = KillAura.getInstance() != null ? KillAura.getInstance().getTarget() : null;
        if (target == null) return;
        
        Rotation targetRotation = calculateRotation(target);
        Rotation currentRotation = RotationManager.lastServerRotations;
        
        if (currentRotation == null) {
            currentRotation = new Rotation(mc.player.getYaw(), mc.player.getPitch());
        }

        Rotation processedRotation = process(currentRotation, targetRotation);

        Managers.ROTATION.setRotations(
                processedRotation, 
                100,
                MovementFix.OFF,
                RotationManager.Priority.Highest
        );
    }

    private Rotation process(Rotation currentRotation, Rotation targetRotation) {
        float deltaYaw = MathHelper.wrapDegrees(targetRotation.yaw - currentRotation.yaw);
        float deltaPitch = MathHelper.wrapDegrees(targetRotation.pitch - currentRotation.pitch);
        float difference = (float) Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);

        long currentTime = System.currentTimeMillis();
        boolean shouldBoost = Math.sin(currentTime / 300.0) > 0.8;
        boolean isTargetBehind = Math.abs(deltaYaw) > 90.0f;

        float speedMultiplier = shouldBoost ? 2.0f : 1.2f;
        float smoothBoost = shouldBoost
                ? (float) (Math.sin((currentTime % 360) / 300.0f * Math.PI) * 0.8f + 1.2f)
                : 1.2f;

        float backTargetMultiplier = isTargetBehind
                ? (float) (2.2f * Math.sin(currentTime / 150.0) * 0.2 + 1.0)
                : 1.2f;

        float speed = speedMultiplier * smoothBoost;

        float yawSpeed = getBaseYawSpeed() * speed * backTargetMultiplier;
        float pitchSpeed = getBasePitchSpeed() * speed;

        float microAdjustment = (float) (Math.sin(currentTime / 80.0) * 0.08 + Math.cos(currentTime / 120.0) * 0.05);

        float moveYaw = MathHelper.clamp(deltaYaw, -yawSpeed, yawSpeed);
        float movePitch = MathHelper.clamp(deltaPitch, -pitchSpeed, pitchSpeed);

        if (difference < 5.0f) {
            moveYaw += microAdjustment * 0.2f;
            movePitch += microAdjustment * 0.8f;
        }

        return new Rotation(
                currentRotation.yaw + moveYaw,
                MathHelper.clamp(currentRotation.pitch + movePitch, -90.0f, 90.0f)
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
        targetPositionMode = rotateAt.get();
        Vec3d basePos = targetPositionMode.getPosition(target);
        Vec3d predictedPos = predict.predictPosition(target, basePos);
        return predictedPos.add(getRandomDirectionVector().multiply(0.4));
    }

    private float getBaseYawSpeed() {
        return (sharpRotations.get() ? BASE_YAW_SPEED * 1.5f : BASE_YAW_SPEED) / 3f;
    }

    private float getBasePitchSpeed() {
        return (sharpRotations.get() ? BASE_PITCH_SPEED * 1.5f : BASE_PITCH_SPEED) / 3f;
    }

    private Vec3d getRandomDirectionVector() {
        double t = System.currentTimeMillis() / 1000.0;
        return new Vec3d(
                Math.sin(t * 1.8) * 0.04 + (Math.random() - 0.5) * 0.02,
                Math.sin(t * 2.2) * 0.03 + (Math.random() - 0.5) * 0.015,
                Math.cos(t * 1.8) * 0.04 + (Math.random() - 0.5) * 0.02
        );
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
}
