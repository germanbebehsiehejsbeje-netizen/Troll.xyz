package dev.mzc.client.mixin.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.mzc.client.Sakura;
import dev.mzc.client.manager.Managers;
import dev.mzc.client.manager.impl.RotationManager;
import dev.mzc.client.module.impl.render.BedTrap;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static dev.mzc.client.Sakura.mc;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer<T extends LivingEntity, S extends LivingEntityRenderState> {

    @Unique
    private Entity capturedEntity;

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD")
    )
    private void onRenderHead(S state, MatrixStack matrices, OrderedRenderCommandQueue renderQueue, CameraRenderState cameraState, CallbackInfo ci) {
        BedTrap bedTrap = Sakura.MODULES.getModule(BedTrap.class);
        if (bedTrap != null && bedTrap.isEnabled()) {
            // entity access via capturedEntity (set in onUpdateRenderState)
        }
    }

    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At("HEAD")
    )
    private void onUpdateRenderState(T entity, S state, float tickDelta, CallbackInfo ci) {
        capturedEntity = entity;
    }

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", shift = At.Shift.AFTER)
    )
    private void onRenderBeforeModel(S state, MatrixStack matrices, OrderedRenderCommandQueue renderQueue, CameraRenderState cameraState, CallbackInfo ci) {
        BedTrap bedTrap = Sakura.MODULES.getModule(BedTrap.class);
        if (bedTrap != null && bedTrap.isEnabled() && capturedEntity != null) {
            float scaleY = bedTrap.getScaleY(capturedEntity);
            if (scaleY != 1.0f) {
                matrices.scale(1.0f, scaleY, 1.0f);
            }
        }
    }

    @ModifyExpressionValue(
            method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;clampBodyYaw(Lnet/minecraft/entity/LivingEntity;FF)F")
    )
    private float hookBodyYaw(float original, LivingEntity entity, S state, float tickDelta) {
        if (entity != mc.player) return original;
        if (!Managers.ROTATION.isActive()) return original;
        return MathHelper.lerp(tickDelta, RotationManager.getPrevRenderYawOffset(), RotationManager.getRenderYawOffset());
    }

    @ModifyExpressionValue(
            method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;lerpAngleDegrees(FFF)F")
    )
    private float hookHeadYaw(float original, LivingEntity entity, S state, float tickDelta) {
        if (entity != mc.player) return original;
        if (!Managers.ROTATION.isActive()) return original;
        return MathHelper.lerpAngleDegrees(tickDelta, RotationManager.getPrevRotationYawHead(), RotationManager.getRotationYawHead());
    }

    @ModifyExpressionValue(
            method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getLerpedPitch(F)F")
    )
    private float hookPitch(float original, LivingEntity entity, S state, float tickDelta) {
        if (entity != mc.player) return original;
        if (!Managers.ROTATION.isActive()) return original;
        return MathHelper.lerp(tickDelta, RotationManager.getPrevRenderPitch(), RotationManager.getRenderPitch());
    }
}