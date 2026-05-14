package dev.mzc.client.mixin.render;

import dev.mzc.client.Sakura;
import dev.mzc.client.module.impl.render.Crystal;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EndCrystalEntityRenderer;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.entity.state.EndCrystalEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndCrystalEntityRenderer.class)
public abstract class MixinEndCrystalEntityRenderer {
    @Unique
    private Crystal crystal;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo info) {
        crystal = Sakura.MODULES.getModule(Crystal.class);
    }

    @Shadow
    @Final
    @Mutable
    private static RenderLayer END_CRYSTAL;

    @Shadow
    @Final
    private static Identifier TEXTURE;

    @Inject(method = "render", at = @At("HEAD"))
    private void render$renderLayer(EndCrystalEntityRenderState endCrystalEntityRenderState, MatrixStack matrixStack, OrderedRenderCommandQueue queue, CameraRenderState cameraRenderState, CallbackInfo ci) {
        // 1.21.11 RenderLayer factory changed; keep vanilla layer for now.
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void render$scale(EndCrystalEntityRenderState endCrystalEntityRenderState, MatrixStack matrixStack, OrderedRenderCommandQueue queue, CameraRenderState cameraRenderState, CallbackInfo info) {
        if (!crystal.isEnabled() || !crystal.modifyScale.get()) return;

        float v = ((Number) crystal.scale.get()).floatValue();

        if (crystal.enableBreathing.get()) {
            long time = System.currentTimeMillis();
            float breathingEffect = (float) (Math.sin(time * 0.001 * ((Number) crystal.breathingSpeed.get()).floatValue()) * ((Number) crystal.breathingAmount.get()).floatValue());
            v += breathingEffect;
        }
        if (crystal.enableRotation.get()) {
            long time = System.currentTimeMillis();
            float rotation = (time * 0.01f * ((Number) crystal.rotationSpeed.get()).floatValue()) % 360;
            matrixStack.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(rotation));
        }

        matrixStack.scale(v, v, v);
    }

}
