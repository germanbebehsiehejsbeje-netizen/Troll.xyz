package dev.mzc.client.mixin.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//@Mixin(ArmorFeatureRenderer.class)
public class MixinArmorFeatureRenderer {
    /*
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/state/LivingEntityRenderState;FF)V", at = @At("HEAD"))
    private void onRenderHead(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, LivingEntityRenderState state, float limbAngle, float limbDistance, CallbackInfo ci) {
        Chams chams = Sakura.MODULES.getModule(Chams.class);
        if (!(state instanceof BipedEntityRenderState bipedState) || !chams.shouldApplyArmor(bipedState)) return;

        GlStateManager._enableDepthTest();
        GlStateManager._depthFunc(GL11.GL_ALWAYS);
        GlStateManager._depthMask(false);
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/state/LivingEntityRenderState;FF)V", at = @At("RETURN"))
    private void onRenderReturn(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, LivingEntityRenderState state, float limbAngle, float limbDistance, CallbackInfo ci) {
        Chams chams = Sakura.MODULES.getModule(Chams.class);
        if (!(state instanceof BipedEntityRenderState bipedState) || !chams.shouldApplyArmor(bipedState)) return;

        GlStateManager._depthMask(true);
        GlStateManager._depthFunc(GL11.GL_LEQUAL);
    }
    */
}
