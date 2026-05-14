package dev.mzc.client.module.impl.movement;

import dev.mzc.client.events.player.UpdateVelocityEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class LegitStrafe extends Module {

    private final NumberValue<Double> speed = new NumberValue<>("Speed", 1.2, 0.5, 3.0, 0.05);
    private final NumberValue<Double> airSpeed = new NumberValue<>("Air Speed", 0.8, 0.1, 2.0, 0.05);
    private final BoolValue onGroundOnly = new BoolValue("On Ground Only", false);
    private final BoolValue autoJump = new BoolValue("Auto Jump", true);

    public LegitStrafe() {
        super("LegitStrafe", Category.Movement);
        this.setType(ModuleType.Safe);
    }

    @EventHandler
    public void onUpdateVelocity(UpdateVelocityEvent event) {
        if (nullCheck()) return;
        
        if (onGroundOnly.get() && !mc.player.isOnGround()) return;
        
        // Check if player is moving
        if (!isMoving()) return;
        
        Vec3d velocity = event.getVelocity();
        double currentSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        
        // Apply speed boost
        double targetSpeed = mc.player.isOnGround() ? speed.get() * 0.28 : airSpeed.get() * 0.28;
        
        if (currentSpeed < targetSpeed) {
            double yaw = Math.toRadians(mc.player.getYaw());
            double strafe = getDirection() * targetSpeed;
            
            double motionX = -Math.sin(yaw) * strafe;
            double motionZ = Math.cos(yaw) * strafe;
            
            event.setVelocity(new Vec3d(motionX, velocity.y, motionZ));
        }
        
        // Auto jump when on ground and moving
        if (autoJump.get() && mc.player.isOnGround() && isMoving()) {
            mc.player.jump();
        }
    }
    
    private boolean isMoving() {
        return mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed() ||
               mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed();
    }
    
    private int getDirection() {
        if (mc.options.forwardKey.isPressed() && mc.options.leftKey.isPressed()) return 1;
        if (mc.options.forwardKey.isPressed() && mc.options.rightKey.isPressed()) return -1;
        if (mc.options.backKey.isPressed() && mc.options.leftKey.isPressed()) return -1;
        if (mc.options.backKey.isPressed() && mc.options.rightKey.isPressed()) return 1;
        if (mc.options.forwardKey.isPressed()) return 1;
        if (mc.options.backKey.isPressed()) return -1;
        if (mc.options.leftKey.isPressed()) return 1;
        if (mc.options.rightKey.isPressed()) return -1;
        return 0;
    }
}
