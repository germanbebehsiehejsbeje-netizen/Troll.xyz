package dev.mzc.client.particle;

import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

/**
 * Custom particle system with physics and occlusion checking.
 * This is a standalone particle that doesn't extend Minecraft's particle system.
 */
public class CustomHitParticle {
    private final MinecraftClient mc;
    public double x, y, z;
    public double velocityX, velocityY, velocityZ;
    public int age;
    public int maxAge;
    public float scale;
    public boolean dead = false;
    
    private double gravity = 0.04;
    private double bounceFactor = 0.3;
    private double friction = 0.98;
    private boolean onGround = false;
    private ClientWorld world;
    
    public CustomHitParticle(ClientWorld world, double x, double y, double z, 
                             double velocityX, double velocityY, double velocityZ) {
        this.mc = MinecraftClient.getInstance();
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
        this.maxAge = 30;
        this.scale = 0.25f;
    }

    public void tick() {
        if (this.dead) return;
        
        // Don't apply gravity if on ground
        if (!this.onGround) {
            this.velocityY -= gravity;
        }
        
        // Apply friction
        this.velocityX *= friction;
        this.velocityZ *= friction;
        
        // Move particle
        this.x += this.velocityX;
        this.y += this.velocityY;
        this.z += this.velocityZ;
        
        // Check for collision with blocks
        checkBlockCollision();
        
        // Age the particle
        this.age++;
        if (this.age >= this.maxAge) {
            this.dead = true;
        }
    }
    
    private void checkBlockCollision() {
        BlockPos pos = new BlockPos((int) Math.floor(this.x), 
                                   (int) Math.floor(this.y), 
                                   (int) Math.floor(this.z));
        
        // Check if particle is inside a block
        var blockState = this.world.getBlockState(pos);
        var voxelShape = blockState.getCollisionShape(this.world, pos);
        if (!voxelShape.isEmpty()) {
            var box = voxelShape.getBoundingBox().offset(pos);
            if (box.contains(new Vec3d(this.x, this.y, this.z))) {
                // Particle is inside a block, push it out
                handleBlockCollision(pos, box);
            }
        }
        
        // Check if on ground (block below)
        BlockPos groundPos = new BlockPos((int) Math.floor(this.x), 
                                         (int) Math.floor(this.y) - 1, 
                                         (int) Math.floor(this.z));
        var groundState = this.world.getBlockState(groundPos);
        var groundShape = groundState.getCollisionShape(this.world, groundPos);
        if (!groundShape.isEmpty()) {
            var groundBox = groundShape.getBoundingBox().offset(groundPos);
            if (this.y <= groundBox.maxY + 0.01 && this.velocityY <= 0) {
                this.onGround = true;
                this.velocityY = 0;
                this.y = groundBox.maxY + 0.01;
            }
        }
    }
    
    private void handleBlockCollision(BlockPos pos, net.minecraft.util.math.Box blockBox) {
        // Calculate which side we collided with
        double dx = this.x - (blockBox.minX + blockBox.maxX) / 2;
        double dy = this.y - (blockBox.minY + blockBox.maxY) / 2;
        double dz = this.z - (blockBox.minZ + blockBox.maxZ) / 2;
        
        double halfWidthX = (blockBox.maxX - blockBox.minX) / 2;
        double halfWidthY = (blockBox.maxY - blockBox.minY) / 2;
        double halfWidthZ = (blockBox.maxZ - blockBox.minZ) / 2;
        
        // Normalize distances
        double normDx = Math.abs(dx) - halfWidthX;
        double normDy = Math.abs(dy) - halfWidthY;
        double normDz = Math.abs(dz) - halfWidthZ;
        
        // Find the minimum penetration depth
        double minPenetration = Math.min(normDx, Math.min(normDy, normDz));
        
        if (minPenetration == normDy) {
            // Top or bottom collision
            if (dy > 0) {
                this.y = blockBox.maxY + 0.01;
            } else {
                this.y = blockBox.minY - 0.01;
            }
            this.velocityY = -this.velocityY * bounceFactor;
            
            // If hitting from above, mark as on ground
            if (dy > 0 && this.velocityY < 0.1) {
                this.onGround = true;
                this.velocityY = 0;
            }
        } else if (minPenetration == normDx) {
            // Left or right collision
            if (dx > 0) {
                this.x = blockBox.maxX + 0.01;
            } else {
                this.x = blockBox.minX - 0.01;
            }
            this.velocityX = -this.velocityX * bounceFactor;
        } else {
            // Front or back collision
            if (dz > 0) {
                this.z = blockBox.maxZ + 0.01;
            } else {
                this.z = blockBox.minZ - 0.01;
            }
            this.velocityZ = -this.velocityZ * bounceFactor;
        }
        
        // Reduce velocity after collision
        this.velocityX *= 0.8;
        this.velocityY *= 0.8;
        this.velocityZ *= 0.8;
    }

    public boolean shouldRender() {
        // Check if particle is visible (not behind walls)
        if (mc.world == null || mc.player == null) return false;
        
        Vec3d particlePos = new Vec3d(this.x, this.y, this.z);
        Vec3d cameraPos = mc.player.getEyePos();
        
        // Perform raycast to check if particle is behind blocks
        HitResult hitResult = mc.world.raycast(new RaycastContext(
            cameraPos,
            particlePos,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            mc.player
        ));
        
        // If the raycast hits a block before reaching the particle, don't render
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            double distToBlock = cameraPos.distanceTo(hitResult.getPos());
            double distToParticle = cameraPos.distanceTo(particlePos);
            return distToBlock >= distToParticle - 0.3; // Small tolerance
        }
        
        return true;
    }
}

