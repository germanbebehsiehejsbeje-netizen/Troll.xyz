package dev.mzc.client.utils.player;

import dev.mzc.client.events.input.MoveInputEvent;
import dev.mzc.client.module.impl.movement.MoveFix;
import dev.mzc.client.Sakura;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.MathHelper;

import static dev.mzc.client.Sakura.mc;

public class MovementUtil {
    public static boolean isAttacking = false;

    public static boolean isMoving() {
        return mc.player.forwardSpeed != 0 || mc.player.sidewaysSpeed != 0;
    }

    public static double getSpeed() {
        return Math.sqrt(mc.player.getVelocity().x * mc.player.getVelocity().x + mc.player.getVelocity().z * mc.player.getVelocity().z);
    }

    public static double getBaseSpeed(boolean slow, double customSpeed) {
        double baseSpeed = customSpeed;

        if (mc.player.hasStatusEffect(StatusEffects.SPEED)) {
            int amplifier = mc.player.getStatusEffect(StatusEffects.SPEED).getAmplifier();
            baseSpeed *= 1.0 + 0.2 * (amplifier + 1);
        }

        if (slow && mc.player.hasStatusEffect(StatusEffects.SLOWNESS)) {
            int amplifier = mc.player.getStatusEffect(StatusEffects.SLOWNESS).getAmplifier();
            baseSpeed /= 1.0 + 0.2 * (amplifier + 1);
        }

        return baseSpeed;
    }

    public static double getBaseSpeed(boolean slow) {
        return getBaseSpeed(slow, 0.2873);
    }

    public static double getDistance2D() {
        if (mc.player == null) return 0;
        double dx = mc.player.getX() - mc.player.lastX;
        double dz = mc.player.getZ() - mc.player.lastZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static double getJumpBoost() {
        if (mc.player == null) return 0;
        if (mc.player.hasStatusEffect(StatusEffects.JUMP_BOOST)) {
            return (mc.player.getStatusEffect(StatusEffects.JUMP_BOOST).getAmplifier() + 1) * 0.1;
        }
        return 0;
    }

    public static double getMotionY() {
        if (mc.player == null) return 0;
        return mc.player.getVelocity().y;
    }

    public static void setMotionY(double y) {
        if (mc.player == null) return;
        mc.player.setVelocity(mc.player.getVelocity().x, y, mc.player.getVelocity().z);
    }

    public static void setMotionX(double x) {
        if (mc.player == null) return;
        mc.player.setVelocity(x, mc.player.getVelocity().y, mc.player.getVelocity().z);
    }

    public static void setMotionZ(double z) {
        if (mc.player == null) return;
        mc.player.setVelocity(mc.player.getVelocity().x, mc.player.getVelocity().y, z);
    }

    public static void setMotion(double x, double y, double z) {
        if (mc.player == null) return;
        mc.player.setVelocity(x, y, z);
    }

    public static void strafe(double speed) {
        if (mc.player == null || !isMoving()) return;

        double yaw = getDirection();
        mc.player.setVelocity(-Math.sin(yaw) * speed, mc.player.getVelocity().y, Math.cos(yaw) * speed);
    }

    public static double getDirection() {
        if (mc.player == null) return 0;

        float yaw = mc.player.getYaw();
        float forward = mc.player.forwardSpeed;
        float strafe = mc.player.sidewaysSpeed;

        if (forward < 0) {
            yaw += 180;
        }

        float modifier = 1;
        if (forward != 0) {
            modifier = forward < 0 ? -0.5f : 0.5f;
        }

        if (strafe > 0) {
            yaw -= 90 * modifier;
        }
        if (strafe < 0) {
            yaw += 90 * modifier;
        }

        return Math.toRadians(yaw);
    }

    public static double getDirection(float rotationYaw, final double moveForward, final double moveStrafing) {
        if (moveForward < 0F) rotationYaw += 180F;

        float forward = 1F;

        if (moveForward < 0F) forward = -0.5F;
        else if (moveForward > 0F) forward = 0.5F;

        if (moveStrafing > 0F) rotationYaw -= 90F * forward;
        if (moveStrafing < 0F) rotationYaw += 90F * forward;

        return Math.toRadians(rotationYaw);
    }

    public static float getMoveForward() {
        if (mc.player == null) return 0;
        return mc.player.forwardSpeed;
    }

    public static float getMoveStrafe() {
        if (mc.player == null) return 0;
        return mc.player.sidewaysSpeed;
    }

    public static double[] getMotion(double speed) {
        if (mc.player == null) return new double[]{0, 0};

        float forward = mc.player.forwardSpeed;
        float strafe = mc.player.sidewaysSpeed;
        float yaw = mc.player.getYaw();

        if (forward == 0 && strafe == 0) {
            return new double[]{0, 0};
        }

        if (forward != 0) {
            if (strafe > 0) {
                yaw -= (forward > 0 ? 45 : -45);
            } else if (strafe < 0) {
                yaw += (forward > 0 ? 45 : -45);
            }
            strafe = 0;
            forward = forward > 0 ? 1 : -1;
        }

        double sin = Math.sin(Math.toRadians(yaw));
        double cos = Math.cos(Math.toRadians(yaw));

        double x = forward * speed * -sin + strafe * speed * cos;
        double z = forward * speed * cos - strafe * speed * -sin;

        return new double[]{x, z};
    }

    /**
     * Исправляет движение на пакетном уровне.
     */
    public static void fixMovement(final MoveInputEvent event, final float auraYaw, boolean stinc) {
        float forward = event.getForward();
        float strafe = event.getStrafe();
        
        if (forward == 0 && strafe == 0) return;

        // Check if enhanced MoveFix module is enabled
        MoveFix moveFix = Sakura.MODULES.getModule(MoveFix.class);
        if (moveFix != null && moveFix.isEnabled()) {
            DirectionalInput input = new DirectionalInput(forward, strafe);
            float clientYaw = mc.player.getYaw();
            
            DirectionalInput corrected = moveFix.correctInput(input, clientYaw, auraYaw);
            
            event.setForward(corrected.isForwards() ? 1.0f : (corrected.isBackwards() ? -1.0f : 0.0f));
            event.setStrafe(corrected.isLeft() ? 1.0f : (corrected.isRight() ? -1.0f : 0.0f));
            return;
        }

        // "Stinc" режим (GRIM): при любом вводе мы движемся строго на цель (Server Yaw)
        if (stinc) {
            // Сохраняем оригинальный вектор движения
            float magnitude = (float) Math.sqrt(forward * forward + strafe * strafe);
            if (magnitude > 0.001f) {
                // Нормализуем и устанавливаем направление строго по yaw киллауры
                event.setForward(1.0f);
                event.setStrafe(0.0f);
            }
            return;
        }

        // Обычная коррекция (Silent Move Fix) по твоей формуле
        float clientYaw = mc.player.getYaw();
        float diff = auraYaw - clientYaw;
        double angle = Math.toRadians(diff);
        
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);

        float newForward = (float) (forward * cos - strafe * sin);
        float newStrafe = (float) (forward * sin + strafe * cos);

        event.setForward(newForward);
        event.setStrafe(newStrafe);
    }

    public static float getTickDelta() {
        return mc.getRenderTickCounter().getTickProgress(true);
    }

    public static double[] directionSpeedKey(double speed) {
        if (mc.player == null) return new double[]{0, 0};

        float forward = (mc.options.forwardKey.isPressed() ? 1 : 0) + (mc.options.backKey.isPressed() ? -1 : 0);
        float side = (mc.options.leftKey.isPressed() ? 1 : 0) + (mc.options.rightKey.isPressed() ? -1 : 0);
        float yaw = mc.player.lastYaw + (mc.player.getYaw() - mc.player.lastYaw) * getTickDelta();

        if (forward != 0.0f) {
            if (side > 0.0f) {
                yaw += ((forward > 0.0f) ? -45 : 45);
            } else if (side < 0.0f) {
                yaw += ((forward > 0.0f) ? 45 : -45);
            }
            side = 0.0f;
            if (forward > 0.0f) {
                forward = 1.0f;
            } else if (forward < 0.0f) {
                forward = -1.0f;
            }
        }

        final double sin = Math.sin(Math.toRadians(yaw + 90.0f));
        final double cos = Math.cos(Math.toRadians(yaw + 90.0f));
        final double posX = forward * speed * cos + side * speed * sin;
        final double posZ = forward * speed * sin - side * speed * cos;

        return new double[]{posX, posZ};
    }

    public static double[] directionSpeed(double speed) {
        if (mc.player == null) return new double[]{0, 0};

        float forward = mc.player.forwardSpeed;
        float side = mc.player.sidewaysSpeed;
        float yaw = mc.player.lastYaw + (mc.player.getYaw() - mc.player.lastYaw) * getTickDelta();

        if (forward != 0.0f) {
            if (side > 0.0f) {
                yaw += ((forward > 0.0f) ? -45 : 45);
            } else if (side < 0.0f) {
                yaw += ((forward > 0.0f) ? 45 : -45);
            }
            side = 0.0f;
            if (forward > 0.0f) {
                forward = 1.0f;
            } else if (forward < 0.0f) {
                forward = -1.0f;
            }
        }

        final double sin = Math.sin(Math.toRadians(yaw + 90.0f));
        final double cos = Math.cos(Math.toRadians(yaw + 90.0f));
        final double posX = forward * speed * cos + side * speed * sin;
        final double posZ = forward * speed * sin - side * speed * cos;

        return new double[]{posX, posZ};
    }
}
