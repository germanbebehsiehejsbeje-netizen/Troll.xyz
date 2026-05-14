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

public class EdgeBug extends Module {

    private final NumberValue<Double> speed = new NumberValue<>("Speed", 1.5, 0.5, 3.0, 0.1);
    private final BoolValue autoJump = new BoolValue("Auto Jump", true);
    private final NumberValue<Double> edgeDistance = new NumberValue<>("Edge Distance", 0.3, 0.1, 0.5, 0.05);
    private final BoolValue onlyOnEdge = new BoolValue("Only On Edge", true);
    
    private boolean wasOnEdge = false;

    public EdgeBug() {
        super("EdgeBug", Category.Movement);
        this.setType(ModuleType.Hack);
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;
        
        boolean onEdge = isOnEdge();
        
        if (onlyOnEdge.get() && !onEdge) {
            wasOnEdge = false;
            return;
        }
        
        if (onEdge) {
            wasOnEdge = true;
            
            Vec3d velocity = mc.player.getVelocity();
            double currentSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
            double targetSpeed = speed.get() * 0.28;
            
            // Speed boost on edge
            if (currentSpeed < targetSpeed) {
                double yaw = Math.toRadians(mc.player.getYaw());
                double motionX = -Math.sin(yaw) * targetSpeed;
                double motionZ = Math.cos(yaw) * targetSpeed;
                
                mc.player.setVelocity(motionX, velocity.y, motionZ);
            }
            
            // Auto jump when hitting edge
            if (autoJump.get() && mc.player.isOnGround()) {
                mc.player.jump();
            }
        }
    }

    @EventHandler
    public void onUpdateVelocity(UpdateVelocityEvent event) {
        if (nullCheck() || !wasOnEdge) return;
        
        // Maintain momentum when falling off edge
        if (!mc.player.isOnGround() && mc.player.fallDistance > 0.5) {
            Vec3d velocity = event.getVelocity();
            double boost = 1.05; // 5% speed boost
            
            event.setVelocity(new Vec3d(
                velocity.x * boost,
                velocity.y,
                velocity.z * boost
            ));
        }
    }

    private boolean isOnEdge() {
        BlockPos playerPos = mc.player.getBlockPos();
        Vec3d pos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        
        // Get player position relative to block
        double relX = pos.x - playerPos.getX();
        double relZ = pos.z - playerPos.getZ();
        
        // Check if near block edge
        boolean nearXEdge = relX < edgeDistance.get() || relX > (1.0 - edgeDistance.get());
        boolean nearZEdge = relZ < edgeDistance.get() || relZ > (1.0 - edgeDistance.get());
        
        // Check if there's air on one side
        BlockPos below = playerPos.down();
        
        if (nearXEdge || nearZEdge) {
            // Check adjacent blocks for air
            for (int dx = -1; dx <= 1; dx += 2) {
                BlockPos checkX = below.add(dx, 0, 0);
                if (mc.world.getBlockState(checkX).isAir()) {
                    return true;
                }
            }
            
            for (int dz = -1; dz <= 1; dz += 2) {
                BlockPos checkZ = below.add(0, 0, dz);
                if (mc.world.getBlockState(checkZ).isAir()) {
                    return true;
                }
            }
        }
        
        return false;
    }
}
