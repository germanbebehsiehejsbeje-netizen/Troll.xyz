package dev.mzc.client.module.impl.movement;

import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.player.UpdateVelocityEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;

public class MovementHelper extends Module {

    private final BoolValue autoJump = new BoolValue("Auto Jump", true);
    private final BoolValue sprint = new BoolValue("Auto Sprint", true);
    private final BoolValue noSlowdown = new BoolValue("No Slowdown", true);
    private final NumberValue<Double> jumpHeight = new NumberValue<>("Jump Height", 1.0, 0.5, 2.0, 0.1, autoJump::get);
    private final BoolValue edgeAssist = new BoolValue("Edge Assist", true);
    private final NumberValue<Double> edgeThreshold = new NumberValue<>("Edge Threshold", 0.2, 0.1, 0.5, 0.05, edgeAssist::get);
    
    private boolean shouldJump = false;

    public MovementHelper() {
        super("MovementHelper", Category.Movement);
        this.setType(ModuleType.Safe);
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;
        
        // Auto Sprint
        if (sprint.get() && isMoving() && mc.player.isOnGround()) {
            mc.player.setSprinting(true);
        }
        
        // Auto Jump
        if (autoJump.get() && isMoving() && mc.player.isOnGround()) {
            // Check if there's a block in front
            BlockPos forward = getBlockInFront();
            if (forward != null && !mc.world.getBlockState(forward).isAir()) {
                if (shouldJump) {
                    mc.player.jump();
                    shouldJump = false;
                }
            } else {
                shouldJump = true;
            }
        }
        
        // Edge Assist - help player not fall off edges
        if (edgeAssist.get() && isMoving()) {
            BlockPos playerPos = mc.player.getBlockPos();
            Vec3d pos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            
            double relX = pos.x - playerPos.getX();
            double relZ = pos.z - playerPos.getZ();
            
            // If near edge, slow down slightly
            if (relX < edgeThreshold.get() || relX > (1.0 - edgeThreshold.get()) ||
                relZ < edgeThreshold.get() || relZ > (1.0 - edgeThreshold.get())) {
                
                BlockPos below = playerPos.down();
                if (mc.world.getBlockState(below).isAir()) {
                    // Near edge with air below, reduce speed
                    Vec3d velocity = mc.player.getVelocity();
                    mc.player.setVelocity(velocity.multiply(0.95, 1.0, 0.95));
                }
            }
        }
    }

    @EventHandler
    public void onUpdateVelocity(UpdateVelocityEvent event) {
        if (nullCheck()) return;
        
        // No Slowdown
        if (noSlowdown.get()) {
            Vec3d velocity = event.getVelocity();
            
            // Remove slowdown effects
            if (mc.player.isUsingItem()) {
                event.setVelocity(new Vec3d(
                    velocity.x * 1.3,
                    velocity.y,
                    velocity.z * 1.3
                ));
            }
        }
        
        // Jump height modifier
        if (autoJump.get() && mc.player.isOnGround() && shouldJump) {
            Vec3d velocity = event.getVelocity();
            double jumpMultiplier = jumpHeight.get();
            
            event.setVelocity(new Vec3d(
                velocity.x,
                0.42 * jumpMultiplier,
                velocity.z
            ));
        }
    }

    private boolean isMoving() {
        return mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed() ||
               mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed();
    }

    private BlockPos getBlockInFront() {
        if (mc.player == null) return null;
        
        double yaw = Math.toRadians(mc.player.getYaw());
        int dx = (int) Math.round(-Math.sin(yaw));
        int dz = (int) Math.round(Math.cos(yaw));
        
        return mc.player.getBlockPos().add(dx, 0, dz);
    }
}
