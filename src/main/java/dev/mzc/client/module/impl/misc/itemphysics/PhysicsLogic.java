package dev.mzc.client.module.impl.misc.itemphysics;

import dev.mzc.client.mixin.accessor.IEntity;
import dev.mzc.client.module.impl.misc.ItemPhysics;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

public class PhysicsLogic {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final float BASE_MULTIPLIER = 0.25F;
    private static final double RANDOM_Y_OFFSET_SCALE = 0.05 / (Math.PI * 2);

    public static void calculateRotation(ItemEntity entity, ItemEntityRenderState state) {
        if (ItemPhysics.INSTANCE == null || !ItemPhysics.INSTANCE.isEnabled()) return;

        float rotateBy = mc.getRenderTickCounter().getTickProgress(false) * BASE_MULTIPLIER * ItemPhysics.INSTANCE.rotateSpeed.get().floatValue();
        
        if (mc.isPaused()) rotateBy = 0;

        Vec3d motionMultiplier = ((IEntity) entity).getMovementMultiplier();

        IItemEntityRenderStateExtender extender = (IItemEntityRenderStateExtender) state;
        boolean isBlock = extender.isBlock();

        if (isBlock) {
            if (!entity.isOnGround()) {
                rotateBy *= 2;
                Fluid fluid = calculateFluid(entity, false);
                if (fluid == null) fluid = calculateFluid(entity, true);
                
                if (fluid != null) {
                    rotateBy /= 2.0f; // Simple drag
                }

                entity.setPitch(entity.getPitch() + rotateBy);
            } else if (ItemPhysics.INSTANCE.oldRotation.get()) {
                for (int side = 0; side < 4; side++) {
                    double rotation = side * 90;
                    double range = 5;
                    if (entity.getPitch() > rotation - range && entity.getPitch() < rotation + range)
                        entity.setPitch((float) rotation);
                }
                
                float pitch = entity.getPitch();
                if (pitch != 0 && pitch != 90 && pitch != 180 && pitch != 270) {
                    double dist0 = Math.abs(pitch);
                    double dist90 = Math.abs(pitch - 90);
                    double dist180 = Math.abs(pitch - 180);
                    double dist270 = Math.abs(pitch - 270);
                    
                    if (dist0 <= dist90 && dist0 <= dist180 && dist0 <= dist270)
                        entity.setPitch(pitch < 0 ? pitch + rotateBy : pitch - rotateBy);
                    else if (dist90 < dist0 && dist90 <= dist180 && dist90 <= dist270)
                        entity.setPitch(pitch - 90 < 0 ? pitch + rotateBy : pitch - rotateBy);
                    else if (dist180 < dist90 && dist180 < dist0 && dist180 <= dist270)
                        entity.setPitch(pitch - 180 < 0 ? pitch + rotateBy : pitch - rotateBy);
                    else if (dist270 < dist90 && dist270 < dist180 && dist270 < dist0)
                        entity.setPitch(pitch - 270 < 0 ? pitch + rotateBy : pitch - rotateBy);
                }
            }
        } else if (!Double.isNaN(entity.getX()) && !Double.isNaN(entity.getY()) && !Double.isNaN(entity.getZ()) && entity.getEntityWorld() != null) {
            if (entity.isOnGround()) {
                if (!isBlock) entity.setPitch(0);
            } else {
                rotateBy *= 2;
                Fluid fluid = calculateFluid(entity, false);
                if (fluid != null) rotateBy /= 2.0f;
                entity.setPitch(entity.getPitch() + rotateBy);
            }
        }
    }

    public static boolean render(ItemEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, Random random) {
        if (ItemPhysics.INSTANCE == null || !ItemPhysics.INSTANCE.isEnabled()) return false;

        IItemEntityRenderStateExtender extender = (IItemEntityRenderStateExtender) state;
        if (extender.getStack().isEmpty() || state.itemRenderState.isEmpty()) return false;

        random.setSeed(state.seed);

        int count = ItemPhysics.INSTANCE.fastRender.get() ? 1 : Math.max(1, state.renderedAmount);
        boolean isBlock = extender.isBlock();

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(extender.getYRot()));

        if (state.age != 0.0f && (isBlock || mc.options != null)) {
            double bob = state.uniqueOffset * RANDOM_Y_OFFSET_SCALE;
            if (isBlock) {
                matrices.translate(0.0, -0.2, -0.08);
            } else if (extender.hasAdditionalOffset()) {
                matrices.translate(0.0, 0.0, -0.14 - bob);
            } else {
                matrices.translate(0.0, 0.0, -0.04 - bob);
            }

            if (isBlock) matrices.translate(0.0, 0.25, 0.0);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(extender.getXRot()));
            if (isBlock) matrices.translate(0.0, -0.25, 0.0);
        }

        if (!isBlock) {
            float zOffset = -0.09375F * (count - 1) * 0.5F;
            matrices.translate(0.0, 0.0, zOffset);
        }

        for (int i = 0; i < count; i++) {
            matrices.push();
            if (i > 0 && isBlock) {
                float jitter = 0.15f;
                float x = (random.nextFloat() * 2.0F - 1.0F) * jitter;
                float y = (random.nextFloat() * 2.0F - 1.0F) * jitter;
                float z = (random.nextFloat() * 2.0F - 1.0F) * jitter;
                matrices.translate(x, y, z);
            }

            state.itemRenderState.render(matrices, queue, light, 0, state.outlineColor);
            matrices.pop();

            if (!isBlock) {
                matrices.translate(0.0, 0.0, 0.09375F);
            }
        }

        matrices.pop();
        return true;
    }

    public static int getModelCount(int count) {
        if (count > 48) return 5;
        if (count > 32) return 4;
        if (count > 16) return 3;
        if (count > 1) return 2;
        return 1;
    }

    private static Fluid calculateFluid(ItemEntity item, boolean below) {
        if (item.getEntityWorld() == null) return null;
        
        double y = item.getEntityPos().y;
        BlockPos pos = item.getBlockPos();
        if (below) pos = pos.down();
        
        FluidState state = item.getEntityWorld().getFluidState(pos);
        Fluid fluid = state.getFluid();
        if (fluid == null || state.isEmpty()) return null;
        
        if (below) return fluid;
        
        float filled = state.getHeight(item.getEntityWorld(), pos);
        if (y - pos.getY() - 0.2 <= filled) return fluid;
        
        return null;
    }
}
