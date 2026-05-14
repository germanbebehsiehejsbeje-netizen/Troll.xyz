package dev.mzc.client.mixin.render;

import dev.mzc.client.Sakura;
import dev.mzc.client.module.impl.render.AspectRatio;
import dev.mzc.client.module.impl.render.Fov;
import dev.mzc.client.module.impl.render.NoRender;
import dev.mzc.client.nanovg.NanoVGRenderer;
import dev.mzc.client.utils.math.FrameRateCounter;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {
    @Shadow
    public abstract float getFarPlaneDistance();

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;applyCursorTo(Lnet/minecraft/client/util/Window;)V"))
    private void sakura$renderScreenNanoVgOnTop(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        NanoVGRenderer.INSTANCE.flushScreenQueue();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void postHudRenderHook(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        FrameRateCounter.INSTANCE.recordFrame();
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void tiltViewWhenHurtHook(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (Sakura.MODULES.getModule(NoRender.class).getNoHurtCam()) {
            ci.cancel();
        }
    }

    @Inject(method = "getBasicProjectionMatrix", at = @At("TAIL"), cancellable = true)
    public void getBasicProjectionMatrix(float fovDegrees, CallbackInfoReturnable<Matrix4f> info) {
        if (Sakura.MODULES.getModule(AspectRatio.class).isEnabled()) {
            float ratio = Sakura.MODULES.getModule(AspectRatio.class).ratio.get().floatValue();
            info.setReturnValue(new Matrix4f().setPerspective(
                    (float) (fovDegrees * 0.01745329238474369),
                    ratio,
                    0.05f,
                    getFarPlaneDistance()
            ));
        }

        // Fov lock / smoothing
        try {
            Fov fovLock = Sakura.MODULES.getModule(Fov.class);
            if (fovLock != null && fovLock.isEnabled()) {
                float desiredFov = fovLock.getCurrentFov(fovDegrees);
                // compute window aspect ratio
                float ratio = 1.0f;
                try {
                    net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
                    if (mc != null && mc.getWindow() != null) {
                        ratio = (float) mc.getWindow().getFramebufferWidth() / (float) mc.getWindow().getFramebufferHeight();
                    }
                } catch (Throwable ignored) {
                }

                info.setReturnValue(new Matrix4f().setPerspective(
                        (float) (desiredFov * 0.01745329238474369),
                        ratio,
                        0.05f,
                        getFarPlaneDistance()
                ));
            }
        } catch (Throwable ignored) {
        }
    }
}
