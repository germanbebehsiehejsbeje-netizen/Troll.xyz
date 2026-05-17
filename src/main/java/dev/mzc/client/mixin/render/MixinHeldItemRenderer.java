package dev.mzc.client.mixin.render;

import dev.mzc.client.Sakura;
import dev.mzc.client.events.render.item.EatTransformationEvent;
import dev.mzc.client.events.render.item.HeldItemRendererEvent;
import dev.mzc.client.events.render.item.RenderSwingAnimationEvent;
import dev.mzc.client.events.render.item.UpdateHeldItemsEvent;
import dev.mzc.client.module.impl.render.Animations;
import dev.mzc.client.module.impl.render.SwingAnimation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static dev.mzc.client.Sakura.mc;

@Mixin(HeldItemRenderer.class)
public class MixinHeldItemRenderer {

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    private ItemStack mainHand;

    @Shadow
    private ItemStack offHand;

    @Shadow
    private float equipProgressMainHand;

    @Shadow
    private float equipProgressOffHand;

    @Shadow
    private float lastEquipProgressMainHand;

    @Shadow
    private float lastEquipProgressOffHand;

    @Inject(method = "applyEatOrDrinkTransformation", at = @At(value = "HEAD"), cancellable = true)
    private void hookApplyEatOrDrinkTransformation(MatrixStack matrices, float tickDelta, Arm arm, ItemStack stack, PlayerEntity player, CallbackInfo ci) {
        ci.cancel();
        float h;
        float f = (float) this.client.player.getItemUseTimeLeft() - tickDelta + 1.0f;
        float g = f / (float) stack.getMaxUseTime(mc.player);
        if (g < 0.8f) {
            h = MathHelper.abs(MathHelper.cos(f / 4.0f * (float) Math.PI) * 0.1f);
            EatTransformationEvent eatTransformationEvent = new EatTransformationEvent();
            Sakura.EVENT_BUS.post(eatTransformationEvent);
            matrices.translate(0.0f, eatTransformationEvent.isCancelled() ? h * eatTransformationEvent.getFactor() : h, 0.0f);
        }
        h = 1.0f - (float) Math.pow(g, 27.0);
        int i = arm == Arm.RIGHT ? 1 : -1;
        matrices.translate(h * 0.6f * (float) i, h * -0.5f, h * 0.0f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * h * 90.0f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(h * 10.0f));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) i * h * 30.0f));
    }

    @ModifyArg(method = "updateHeldItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;clamp(FFF)F", ordinal = 2), index = 0)
    private float hookEquipProgressMainhand(float value) {
        RenderSwingAnimationEvent renderSwingAnimation = new RenderSwingAnimationEvent();
        Sakura.EVENT_BUS.post(renderSwingAnimation);
        float f = mc.player.getAttackCooldownProgress(1.0f);
        float modified = renderSwingAnimation.isCancelled() ? 1.0f : f * f * f;
        return (ItemStack.areEqual(mainHand, mc.player.getMainHandStack()) ? modified : 0.0f) - equipProgressMainHand;
    }

    @Inject(method = "updateHeldItems", at = @At(value = "HEAD"), cancellable = true)
    private void hookUpdateHeldItems(CallbackInfo ci) {
        ItemStack itemStack = mc.player.getMainHandStack();
        ItemStack itemStack2 = mc.player.getOffHandStack();
        UpdateHeldItemsEvent updateHeldItemsEvent = new UpdateHeldItemsEvent();
        Sakura.EVENT_BUS.post(updateHeldItemsEvent);
        if (updateHeldItemsEvent.isCancelled()) {
            ci.cancel();
            equipProgressMainHand = 1.0f;
            equipProgressOffHand = 1.0f;
            lastEquipProgressMainHand = 1.0f;
            lastEquipProgressOffHand = 1.0f;
            mainHand = itemStack;
            offHand = itemStack2;
        }
    }

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void hookRenderFirstPersonItemHead(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int light, CallbackInfo ci) {
        Animations animations = Sakura.MODULES.getModule(Animations.class);
        // Previously this cancelled the default rendering when Animations was enabled,
        // which prevented the held item from being drawn because the custom renderer
        // did not perform the full item render. Avoid cancelling here so the default
        // pipeline still draws the item while Animations can adjust transforms later.
        if (animations != null && animations.isEnabled() && !item.isEmpty()) {
            // Intentionally do not cancel default rendering to keep items visible.
        }
        
        // Apply SwingAnimation custom rendering
        SwingAnimation swingAnim = Sakura.MODULES.getModule(SwingAnimation.class);
        if (swingAnim != null && swingAnim.isEnabled() && !swingAnim.mode.is(SwingAnimation.Mode.Normal)) {
            ci.cancel();
            matrices.push();
            VertexConsumerProvider.Immediate vertexConsumers = mc.getBufferBuilders().getEntityVertexConsumers();
            swingAnim.handleRenderItem(player, tickDelta, pitch, hand, swingProgress, item, equipProgress, matrices, vertexConsumers, light);
            matrices.pop();
        }
    }

    @Inject(method = "applySwingOffset", at = @At("HEAD"), cancellable = true)
    private void hookApplySwingOffset(MatrixStack matrices, Arm arm, float swingProgress, CallbackInfo ci) {
        if (mc.player == null) return;

        Animations animations = Sakura.MODULES.getModule(Animations.class);
        if (animations == null || !animations.isEnabled()) return;

        Animations.SwingMode mode = animations.swingMode.get();
        if (mode == Animations.SwingMode.Default || mode == Animations.SwingMode.Normal) return;

        float equipProgress = arm == mc.player.getMainArm() ? equipProgressMainHand : equipProgressOffHand;
        int i = arm == Arm.RIGHT ? 1 : -1;
        float f = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        float g = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);

        float desiredEquip = equipProgress;
        switch (mode) {
            case One, Three -> desiredEquip = -0.4F * MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
            case Two -> desiredEquip = 0.2F * MathHelper.sin(MathHelper.sqrt(swingProgress) * ((float) Math.PI * 2F));
            case Four, Five, Block1, Block2, Block3, Twelve, Thirteen -> desiredEquip = 0.0F;
        }

        if (desiredEquip != equipProgress) {
            matrices.translate(0.0F, (desiredEquip - equipProgress) * -0.6F, 0.0F);
        }

        switch (mode) {
            case One -> {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * (45.0F + f * -20.0F)));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) i * g * -20.0F));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g * 0.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * -45.0F));
            }
            case Two -> {
            }
            case Three -> {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * (45.0F + f * -20.0F)));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) i * g * -70.0F));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-70.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * -45.0F));
            }
            case Four -> matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(swingProgress > 0 ? -MathHelper.sin(swingProgress * 13f) * 37f : 0));
            case Five -> matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) i * g * -20.0F));
            case Seven -> {
                float a = -MathHelper.sin(swingProgress * 3f) / 2f + 1f;
                matrices.scale(a, a, a);
            }
            case Round1 -> matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(swingProgress * (animations.flip ? 360.0F : -360.0F)));
            case Round2 -> matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(swingProgress * -360.0F));
            case Block1 -> {
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(50.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-30.0F * (1.0F - g) - 30.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(110.0F));
            }
            case Block2 -> {
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(50.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-60.0F * g - 50.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(110.0F));
            }
            case Block3 -> {
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(50.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-60.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(110.0F + 20.0F * g));
            }
            case Twelve -> {
                matrices.translate(0.0F, 0.0F, -g / 4.0F);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-120.0F));
            }
            case Thirteen -> {
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-MathHelper.sin(swingProgress * 3f) * 60f));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-60.0F * g));
            }
            case Fourteen -> {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g * -85.0F));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-85.0F));
            }
            case Default, Normal -> {
            }
        }

        ci.cancel();
    }

    @Inject(method = "renderFirstPersonItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", shift = At.Shift.AFTER))
    private void hookRenderFirstPersonItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int light, CallbackInfo ci) {
        HeldItemRendererEvent event = new HeldItemRendererEvent(matrices, hand);
        Sakura.EVENT_BUS.post(event);
    }
}
