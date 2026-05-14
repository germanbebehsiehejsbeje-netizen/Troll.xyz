package dev.mzc.client.module.impl.movement;

import dev.mzc.client.events.player.UpdateVelocityEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;

public class PixelSurf extends Module {

    private final NumberValue<Double> speed = new NumberValue<>("Speed", 1.5, 0.5, 3.0, 0.1);
    private final BoolValue autoJump = new BoolValue("Auto Jump", true);
    private final NumberValue<Double> climbHeight = new NumberValue<>("Climb Height", 0.5, 0.1, 1.0, 0.1);
    
    private boolean isSurfing = false;

    public PixelSurf() {
        super("PixelSurf", Category.Movement);
        this.setType(ModuleType.Hack);
    }

    @EventHandler
    public void onUpdateVelocity(UpdateVelocityEvent event) {
        if (nullCheck()) return;
        
        Vec3d velocity = event.getVelocity();
        
        // Check if player is near a wall edge and moving forward
        if (isNearWallEdge() && mc.options.forwardKey.isPressed()) {
            isSurfing = true;
            
            // Calculate surf direction based on player yaw
            double yaw = Math.toRadians(mc.player.getYaw());
            double targetSpeed = speed.get() * 0.28;
            
            // Apply horizontal surf velocity
            double motionX = -Math.sin(yaw) * targetSpeed;
            double motionZ = Math.cos(yaw) * targetSpeed;
            
            // Add upward climb velocity to stick to wall
            double motionY = climbHeight.get() * 0.1;
            
            event.setVelocity(new Vec3d(motionX, motionY, motionZ));
            event.cancel();
            
            // Auto jump if enabled
            if (autoJump.get() && mc.player.isOnGround()) {
                mc.player.jump();
            }
        } else {
            isSurfing = false;
        }
    }
    
    private boolean isNearWallEdge() {
        BlockPos playerPos = mc.player.getBlockPos();
        Vec3d pos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        
        // Get player position relative to block (0.0 to 1.0)
        double relX = pos.x - playerPos.getX();
        double relZ = pos.z - playerPos.getZ();
        
        // Check if near block edges (within 0.2 blocks)
        double edgeThreshold = 0.2;
        boolean nearXEdge = relX < edgeThreshold || relX > (1.0 - edgeThreshold);
        boolean nearZEdge = relZ < edgeThreshold || relZ > (1.0 - edgeThreshold);
        
        // Must be in air (not on ground)
        if (mc.player.isOnGround()) return false;
        
        // Check if there's a wall beside us at head/feet level
        BlockPos checkPos = playerPos;
        
        // Check X direction walls
        if (nearXEdge) {
            int wallX = relX < 0.5 ? -1 : 1;
            BlockPos wallPos = checkPos.add(wallX, 0, 0);
            if (!mc.world.getBlockState(wallPos).isAir()) {
                // Check if there's ground below the wall
                BlockPos belowWall = wallPos.down();
                return !mc.world.getBlockState(belowWall).isAir();
            }
        }
        
        // Check Z direction walls
        if (nearZEdge) {
            int wallZ = relZ < 0.5 ? -1 : 1;
            BlockPos wallPos = checkPos.add(0, 0, wallZ);
            if (!mc.world.getBlockState(wallPos).isAir()) {
                // Check if there's ground below the wall
                BlockPos belowWall = wallPos.down();
                return !mc.world.getBlockState(belowWall).isAir();
            }
        }
        
        return false;
    }
    
    @Override
    protected void onDisable() {
        isSurfing = false;
    }
}
