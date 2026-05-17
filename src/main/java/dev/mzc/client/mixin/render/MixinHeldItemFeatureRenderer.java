package dev.mzc.client.mixin.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Disabled: Chams module removed
//@Mixin(HeldItemFeatureRenderer.class)
public class MixinHeldItemFeatureRenderer {
    /*
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/ArmedEntityRenderState;FF)V", at = @At("HEAD"))
    private void onRenderHead(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, ArmedEntityRenderState state, float limbAngle, float limbDistance, CallbackInfo ci) {
        GlStateManager._enableDepthTest();
        GlStateManager._depthFunc(GL11.GL_ALWAYS);
        GlStateManager._depthMask(false);
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/ArmedEntityRenderState;FF)V", at = @At("RETURN"))
    private void onRenderReturn(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, ArmedEntityRenderState state, float limbAngle, float limbDistance, CallbackInfo ci) {
        GlStateManager._depthMask(true);
        GlStateManager._depthFunc(GL11.GL_LEQUAL);
    }
    */
}
