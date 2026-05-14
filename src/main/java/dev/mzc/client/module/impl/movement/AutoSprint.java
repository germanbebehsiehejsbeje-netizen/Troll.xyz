package dev.mzc.client.module.impl.movement;

import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.player.JumpRotationEvent;
import dev.mzc.client.manager.Managers;
import dev.mzc.client.manager.impl.RotationManager;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.player.MovementUtil;
import dev.mzc.client.utils.rotation.MovementFix;
import dev.mzc.client.utils.vector.Rotation;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.MultiBoolValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.BlockItem;
import net.minecraft.util.math.MathHelper;

import java.util.List;

public class AutoSprint extends Module {
    public enum Mode {
        Legit(),
        Omnidirectional(),
        Omnirotational();
        Mode() {
        }
    }

    private final EnumValue<Mode> mode = new EnumValue<>("Mode", Mode.Legit);

    private final MultiBoolValue ignore = new MultiBoolValue("Ignore", List.of(
            new BoolValue("Blindness", false),
            new BoolValue("Hunger", false),
            new BoolValue("Collision", false)
    ));

    private final MultiBoolValue stopOn = new MultiBoolValue("StopOn", List.of(
            new BoolValue("Ground", true),
            new BoolValue("Air", true),
            new BoolValue("UsingItem", true)
    ));

    private final NumberValue<Double> rotationSpeed = new NumberValue<>("RotationSpeed", 0.5, 0.0, 1.0, 0.01, () -> mode.is(Mode.Omnirotational));
    private final BoolValue noSlowAttack = new BoolValue("NoSlowAttack", true);

    public AutoSprint() {
        super("AutoSprint", Category.Movement);
        this.setType(ModuleType.Safe);
    }

    @Override
    public String getSuffix() {
        return mode.get().name();
    }

    public boolean isNoSlowAttack() {
        return noSlowAttack.get();
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        if (mode.is(Mode.Omnirotational) && MovementUtil.isMoving()) {
            float targetYaw = getMovementDirectionYaw(mc.player.getYaw(), mc.player.forwardSpeed, mc.player.sidewaysSpeed);
            Managers.ROTATION.setRotations(new Rotation(targetYaw, mc.player.getPitch()), rotationSpeed.get(), MovementFix.OFF, RotationManager.Priority.Lowest);
        }

        if (!MovementUtil.isMoving()) {
            return;
        }

        if (shouldPreventSprint()) {
            if (mc.player.isSprinting()) {
                mc.player.setSprinting(false);
            }
            return;
        }

        if (!shouldSprint()) {
            return;
        }

        if (!mc.player.isSprinting()) {
            mc.player.setSprinting(true);
        }
    }

    @EventHandler
    public void onJumpRotation(JumpRotationEvent event) {
        if (nullCheck()) return;
        if (!mode.is(Mode.Omnidirectional)) return;
        if (!MovementUtil.isMoving()) return;

        float targetYaw = getMovementDirectionYaw(event.getYaw(), mc.player.forwardSpeed, mc.player.sidewaysSpeed);
        event.setYaw(targetYaw);
    }

    private boolean shouldSprint() {
        if (!ignore.isEnabled("Blindness")) {
            StatusEffectInstance effect = mc.player.getStatusEffect(StatusEffects.BLINDNESS);
            if (effect != null && effect.getDuration() > 0) {
                return false;
            }
        }

        if (!ignore.isEnabled("Hunger")) {
            if (mc.player.getHungerManager().getFoodLevel() <= 6) {
                return false;
            }
        }

        if (!ignore.isEnabled("Collision")) {
            if (mc.player.horizontalCollision) {
                return false;
            }
        }

        return mode.is(Mode.Omnidirectional) || mode.is(Mode.Omnirotational) || mc.player.forwardSpeed > 0;
    }

    private boolean shouldPreventSprint() {
        if (!mode.is(Mode.Legit)) return false;

        if (stopOn.isEnabled("UsingItem") && isSlowDueToUsingItem()) {
            return true;
        }

        boolean check = mc.player.isOnGround() ? stopOn.isEnabled("Ground") : stopOn.isEnabled("Air");
        if (!check) return false;

        if (mode.is(Mode.Omnidirectional)) return false;

        Rotation currentRotation = Managers.ROTATION.getRotation();
        float deltaYawRad = (mc.player.getYaw() - currentRotation.yaw) * MathHelper.RADIANS_PER_DEGREE;

        float forward = mc.player.forwardSpeed;
        float sideways = mc.player.sidewaysSpeed;

        boolean hasForwardMovement = forward * MathHelper.cos(deltaYawRad) + sideways * MathHelper.sin(deltaYawRad) > 1.0E-5F;
        return !hasForwardMovement;
    }

    private boolean isSlowDueToUsingItem() {
        if (!mc.player.isUsingItem()) return false;
        if (mc.player.getActiveItem() == null || mc.player.getActiveItem().isEmpty()) return false;
        return !(mc.player.getActiveItem().getItem() instanceof BlockItem);
    }

    private float getMovementDirectionYaw(float baseYaw, float forward, float strafe) {
        if (forward == 0.0F && strafe == 0.0F) return baseYaw;
        double dir = MovementUtil.getDirection(baseYaw, forward, strafe);
        return MathHelper.wrapDegrees((float) Math.toDegrees(dir));
    }
}
