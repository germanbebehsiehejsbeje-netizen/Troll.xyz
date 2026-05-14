package dev.mzc.client.mixin.render;

import dev.mzc.client.render.smoothswap.ISmoothSwapItemStack;
import dev.mzc.client.render.smoothswap.*;
import dev.mzc.client.module.impl.misc.SmoothSwap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(DrawContext.class)
public abstract class MixinDrawContextSmoothSwap {

    @Final
    @Shadow
    private MinecraftClient client;

    @Unique
    private static boolean smooth_Swapping$isRendering = false;

    @Shadow
    public abstract void drawItem(ItemStack item, int x, int y);

    @Shadow
    public abstract void drawStackOverlay(net.minecraft.client.font.TextRenderer textRenderer, ItemStack stack, int x, int y, @org.jetbrains.annotations.Nullable String countLabel);

    @Inject(method = "drawStackOverlay(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    public void onDrawStackOverlay(net.minecraft.client.font.TextRenderer textRenderer, ItemStack stack, int x, int y, String countLabel, CallbackInfo ci) {
        if (SmoothSwap.INSTANCE == null || !SmoothSwap.INSTANCE.isEnabled()) return;
        int index = SwapUtil.getSlotIndex(stack);
        if (SmoothSwapManager.swaps.containsKey(index)) {
            List<InventorySwap> swapList = SmoothSwapManager.swaps.get(index);
            for (InventorySwap swap : swapList) {
                if (!swap.renderDestinationSlot()) {
                    ci.cancel();
                    return;
                }
            }
        }
    }

    @Inject(method = "drawItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;III)V", at = @At("HEAD"), cancellable = true)
    public void onItemDraw(LivingEntity entity, World world, ItemStack stack, int x, int y, int seed, CallbackInfo cbi) {
        if (smooth_Swapping$isRendering) return;
        if (SmoothSwap.INSTANCE == null || !SmoothSwap.INSTANCE.isEnabled()) return;

        try {
            smooth_Swapping$isRendering = true;

            if ((Object) stack instanceof ISmoothSwapItemStack && ((ISmoothSwapItemStack) (Object) stack).smooth_Swapping$isSwapStack()) return;

            smooth_Swapping$doSwap(stack, x, y, cbi);
        } catch (Exception e) {
            SwapUtil.reset();
        } finally {
            smooth_Swapping$isRendering = false;
        }
    }

    @Unique
    private void smooth_Swapping$doSwap(ItemStack stack, int x, int y, CallbackInfo cbi) throws Error {
        int index = SwapUtil.getSlotIndex(stack);

        if (SmoothSwapManager.swaps.containsKey(index)) {
            List<InventorySwap> swapList = SmoothSwapManager.swaps.get(index);
            boolean renderDestinationSlot = true;

            for (int i = 0; i < swapList.size(); i++) {
                InventorySwap swap = swapList.get(i);
                swap.setRenderDestinationSlot(swap.isChecked());

                if (!swap.renderDestinationSlot()) {
                    renderDestinationSlot = false;
                }

                smooth_Swapping$renderSwap(swap, x, y, swap.getSwapItem());

                if (SwapUtil.hasArrived(swap)) {
                    SwapUtil.setRenderToTrue(swapList);
                    swapList.remove(swap);
                }
            }

            if (!renderDestinationSlot) {
                cbi.cancel();
            }
        }
    }
    
    @Unique
    private void smooth_Swapping$renderSwap(InventorySwap swap, int x, int y, ItemStack swapItem) {
        float lastFrameDuration = client.getRenderTickCounter().getTickProgress(true);

        double swapX = swap.getX();
        double swapY = swap.getY();
        double angle = swap.getAngle();

        double progress = 1D - SwapUtil.map(Math.hypot(swapX, swapY), 0, swap.getDistance(), 1D, 0D);

        List<CatmullRomSpline> splines = CatmullRomUtil.getDefaultSplines();

        double ease = CatmullRomUtil.getProgress(progress, splines);

        double renderX = -swap.getStartX() - Math.cos(angle) * swap.getDistance() * ease;
        double renderY = swap.getStartY() + Math.sin(angle) * swap.getDistance() * ease;

        Matrix3x2fStack matrices = ((DrawContext) (Object) this).getMatrices();
        matrices.pushMatrix();
        matrices.translate((float) renderX, (float) -renderY);

        drawItem(swapItem, x, y);
        drawStackOverlay(client.textRenderer, swapItem, x, y, null);

        double speed = swap.getDistance() / 10 * SmoothSwap.INSTANCE.animationSpeed.get();

        swap.setX(swapX + lastFrameDuration * speed * Math.cos(angle));
        swap.setY(swapY + lastFrameDuration * speed * Math.sin(angle));
        matrices.popMatrix();
    }
}
