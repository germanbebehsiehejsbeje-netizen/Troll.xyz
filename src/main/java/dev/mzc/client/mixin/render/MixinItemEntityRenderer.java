package dev.mzc.client.mixin.render;

import dev.mzc.client.Sakura;
import dev.mzc.client.module.impl.misc.BetterFPS;
import dev.mzc.client.module.impl.misc.itemphysics.IItemEntityRenderStateExtender;
import dev.mzc.client.module.impl.misc.itemphysics.PhysicsLogic;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.entity.state.ItemStackEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class MixinItemEntityRenderer extends EntityRenderer<ItemEntity, ItemEntityRenderState> {
    protected MixinItemEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Inject(method = "renderStack(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/ItemStackEntityRenderState;Lnet/minecraft/util/math/random/Random;)V",
            at = @At("HEAD"), cancellable = true)
    private static void onRenderStack(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, ItemStackEntityRenderState state, Random random, CallbackInfo ci) {
        if (state instanceof ItemEntityRenderState itemState) {
            BetterFPS betterFPS = Sakura.MODULES.getModule(BetterFPS.class);
            if (betterFPS != null) {
                ItemEntity itemEntity = ((IItemEntityRenderStateExtender) itemState).getEntity();
                itemState.renderedAmount = betterFPS.getRenderedAmountForItem(itemEntity, itemState.renderedAmount);
            }

            if (PhysicsLogic.render(itemState, matrices, queue, light, random)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V", 
            at = @At("TAIL"))
    private void onUpdateRenderState(ItemEntity itemEntity, ItemEntityRenderState itemEntityRenderState, float f, CallbackInfo ci) {
        ((IItemEntityRenderStateExtender) itemEntityRenderState).extractPhysic(itemEntity);
    }
}
