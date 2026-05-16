package dev.mzc.client.module.impl.movement;

import dev.mzc.client.Sakura;
import dev.mzc.client.events.EventType;
import dev.mzc.client.events.input.MoveInputEvent;
import dev.mzc.client.events.player.JumpEvent;
import dev.mzc.client.events.player.TravelEvent;
import dev.mzc.client.events.player.UpdateVelocityEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.module.impl.combat.KillAura;
import dev.mzc.client.utils.player.DirectionalInput;
import dev.mzc.client.utils.player.MovementFixHelper;
import dev.mzc.client.utils.player.MovementUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class MoveFix extends Module {
    public enum Mode {
        Free,
        Focused,
        Targeted
    }

    private final EnumValue<Mode> mode = new EnumValue<>("Mode", Mode.Focused);
    private final EnumValue<UpdateMode> updateMode = new EnumValue<>("Update Mode", UpdateMode.UpdateMouse);
    private final BoolValue targeting = new BoolValue("Targeting", true);
    private final BoolValue grim = new BoolValue("Grim", false);
    private final BoolValue travel = new BoolValue("Travel", false, grim::get);

    public MoveFix() {
        super("MoveFix", Category.Movement);
        this.setType(ModuleType.Safe);
    }

    public enum UpdateMode {
        MovementPacket(),
        UpdateMouse(),
        All();
        UpdateMode() {
        }


    }

    public static float fixRotation;
    public static float fixPitch;
    private float prevYaw;
    private float prevPitch;
    private float smoothedFixYaw;
    private boolean smoothYawInitialized;

    @EventHandler
    public void onTravel(TravelEvent e) {
        if (!grim.get() || !travel.get()) return;

        if (mc.player.isRiding())
            return;

        if (e.isPre()) {
            prevYaw = mc.player.getYaw();
            prevPitch = mc.player.getPitch();
            mc.player.setYaw(getAppliedFixYaw());
            mc.player.setPitch(fixPitch);
        } else {
            mc.player.setYaw(prevYaw);
            mc.player.setPitch(prevPitch);
        }
    }

    @EventHandler
    public void onJump(JumpEvent event) {
        if (!grim.get()) return;
        if (mc.player.isRiding())
            return;

        if (event.getType().equals(EventType.PRE)) {
            prevYaw = mc.player.getYaw();
            prevPitch = mc.player.getPitch();
            mc.player.setYaw(getAppliedFixYaw());
            mc.player.setPitch(fixPitch);
        } else {
            mc.player.setYaw(prevYaw);
            mc.player.setPitch(prevPitch);
        }
    }

    @EventHandler
    public void onPlayerMove(UpdateVelocityEvent event) {
        if (!grim.get() || travel.get() || mc.player.isRiding()) return;

        event.cancel();
        event.setVelocity(movementInputToVelocity(event.getMovementInput(), event.getSpeed(), getAppliedFixYaw()));
    }

    @EventHandler(priority = -999)
    public void onMoveInput(MoveInputEvent event) {
        if (!grim.get()) return;
        if (mc.player.isRiding())
            return;

        updateSmoothedFixYaw();

        float mF = event.getForward();
        float mS = event.getStrafe();
        float delta = (mc.player.getYaw() - smoothedFixYaw) * MathHelper.RADIANS_PER_DEGREE;
        float cos = MathHelper.cos(delta);
        float sin = MathHelper.sin(delta);
        float outStrafe = mS * cos - mF * sin;
        float outForward = mF * cos + mS * sin;

        if (Math.abs(outForward) < 1.0E-3f) outForward = 0.0f;
        if (Math.abs(outStrafe) < 1.0E-3f) outStrafe = 0.0f;

        event.setStrafe(outStrafe);
        event.setForward(outForward);

        // Force sprint update if moving and sprint key is held
        // This fixes the issue where rotated inputs result in low forward speed, disabling vanilla sprint
        if (mc.options.sprintKey.isPressed() && (mF != 0 || mS != 0)) {
            mc.player.setSprinting(true);
        }
    }

    @Override
    protected void onEnable() {
        smoothYawInitialized = false;
        smoothedFixYaw = fixRotation;
    }

    @Override
    protected void onDisable() {
        smoothYawInitialized = false;
    }

    private void updateSmoothedFixYaw() {
        if (!smoothYawInitialized) {
            smoothedFixYaw = fixRotation;
            smoothYawInitialized = true;
            return;
        }

        float toTarget = MathHelper.wrapDegrees(fixRotation - smoothedFixYaw);
        float toPlayer = Math.abs(MathHelper.wrapDegrees(fixRotation - mc.player.getYaw()));
        float inputStrength = Math.min(1.0f, Math.abs(mc.player.forwardSpeed) + Math.abs(mc.player.sidewaysSpeed));

        // Human-like correction envelope: no hard snaps, capped per-tick turn.
        float maxStep = 3.0f + Math.min(10.0f, toPlayer * 0.16f);
        maxStep += inputStrength * 2.0f;
        maxStep = MathHelper.clamp(maxStep, 2.0f, 13.0f);

        smoothedFixYaw = MathHelper.wrapDegrees(smoothedFixYaw + MathHelper.clamp(toTarget, -maxStep, maxStep));
    }

    private float getAppliedFixYaw() {
        updateSmoothedFixYaw();
        return smoothedFixYaw;
    }

    private static Vec3d movementInputToVelocity(Vec3d movementInput, float speed, float yaw) {
        double d = movementInput.lengthSquared();
        if (d < 1.0E-7) {
            return Vec3d.ZERO;
        } else {
            Vec3d vec3d = (d > 1.0 ? movementInput.normalize() : movementInput).multiply(speed);
            float f = MathHelper.sin(yaw * 0.017453292F);
            float g = MathHelper.cos(yaw * 0.017453292F);
            return new Vec3d(vec3d.x * (double) g - vec3d.z * (double) f, vec3d.y, vec3d.z * (double) g + vec3d.x * (double) f);
        }
    }

    public static boolean isActive() {
        return Sakura.MODULES.getModule(MoveFix.class).isEnabled();
    }

    public DirectionalInput correctInput(DirectionalInput input, float playerYaw, float targetYaw) {
        if (!isEnabled() || input == null || !input.isMoving()) {
            return input;
        }

        float forward = input.isForwards() ? 1.0f : (input.isBackwards() ? -1.0f : 0.0f);
        float sideways = input.isLeft() ? 1.0f : (input.isRight() ? -1.0f : 0.0f);

        if (forward == 0.0f && sideways == 0.0f) {
            return input;
        }

        float deltaYaw = MathHelper.wrapDegrees(playerYaw - targetYaw) * 0.017453292f;
        float correctedForward = forward * MathHelper.cos(deltaYaw) - sideways * MathHelper.sin(deltaYaw);
        float correctedSideways = sideways * MathHelper.cos(deltaYaw) + forward * MathHelper.sin(deltaYaw);

        if (isFree()) {
            return new DirectionalInput(Math.round(correctedForward), Math.round(correctedSideways));
        }

        double angleToTarget = computeAngleToTarget();
        if (isTargeted() && !Double.isNaN(angleToTarget)) {
            return MovementFixHelper.findBestInput(
                    correctedForward,
                    correctedSideways,
                    (forwardValue, strafeValue) -> getTargetedPenalty(forwardValue, strafeValue, targetYaw, angleToTarget, correctedForward, correctedSideways)
            );
        }

        return MovementFixHelper.findBestInput(
                correctedForward,
                correctedSideways,
                (forwardValue, strafeValue) -> getTargetingPenalty(forwardValue, strafeValue, targetYaw)
        );
    }

    private double getTargetingPenalty(float forward, float strafe, float targetYaw) {
        if (!targeting.get() || KillAura.getInstance() == null || !KillAura.getInstance().isEnabled() || 
            KillAura.getInstance().getTarget() == null || mc.player == null) {
            return 0.0;
        }

        Vec3d targetPos = KillAura.getInstance().getTarget().getEyePos();
        double deltaX = targetPos.x - mc.player.getX();
        double deltaZ = targetPos.z - mc.player.getZ();
        double angleToTarget = MathHelper.wrapDegrees((float) (Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0));
        double moveAngle = MathHelper.wrapDegrees((float) Math.toDegrees(MovementUtil.getDirection(targetYaw, forward, strafe)));
        return Math.abs(MathHelper.wrapDegrees((float) (angleToTarget - moveAngle))) * 0.0015;
    }

    private double getTargetedPenalty(float forward, float strafe, float targetYaw, double angleToTarget, float desiredForward, float desiredStrafe) {
        if (KillAura.getInstance() == null || !KillAura.getInstance().isEnabled() || 
            KillAura.getInstance().getTarget() == null || mc.player == null) {
            return 0.0;
        }

        float intentForward = Math.abs(desiredForward) < 0.01f ? 0.0f : Math.signum(desiredForward);
        float intentStrafe = Math.abs(desiredStrafe) < 0.01f ? 0.0f : Math.signum(desiredStrafe);
        if (intentForward == 0.0f && intentStrafe == 0.0f) {
            intentForward = 1.0f;
        }

        double desiredAngle = MathHelper.wrapDegrees((float) Math.toDegrees(MovementUtil.getDirection((float) angleToTarget, intentForward, intentStrafe)));
        double moveAngle = MathHelper.wrapDegrees((float) Math.toDegrees(MovementUtil.getDirection(targetYaw, forward, strafe)));
        double orbitPenalty = Math.abs(MathHelper.wrapDegrees((float) (desiredAngle - moveAngle))) * 0.0095;
        double targetPenalty = Math.abs(MathHelper.wrapDegrees((float) (angleToTarget - moveAngle))) * 0.00125;
        return orbitPenalty + targetPenalty;
    }

    private double computeAngleToTarget() {
        if (KillAura.getInstance() == null || KillAura.getInstance().getTarget() == null || mc.player == null) {
            return Double.NaN;
        }

        Vec3d targetPos = KillAura.getInstance().getTarget().getEyePos();
        double deltaX = targetPos.x - mc.player.getX();
        double deltaZ = targetPos.z - mc.player.getZ();
        return MathHelper.wrapDegrees((float) (Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0));
    }

    public static boolean isTargeting() {
        MoveFix instance = Sakura.MODULES.getModule(MoveFix.class);
        return instance.isEnabled() && !instance.isFree() && instance.targeting.get() && 
               KillAura.getInstance() != null && KillAura.getInstance().isEnabled();
    }

    public static boolean isFree() {
        MoveFix instance = Sakura.MODULES.getModule(MoveFix.class);
        return instance.isEnabled() && instance.mode.get() == Mode.Free;
    }

    public static boolean isFocused() {
        MoveFix instance = Sakura.MODULES.getModule(MoveFix.class);
        return instance.isEnabled() && instance.mode.get() == Mode.Focused;
    }

    public static boolean isTargeted() {
        MoveFix instance = Sakura.MODULES.getModule(MoveFix.class);
        return instance.isEnabled() && instance.mode.get() == Mode.Targeted;
    }

    public boolean isGrimEnabled() {
        return grim.get();
    }
}
