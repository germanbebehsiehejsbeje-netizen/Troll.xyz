package dev.mzc.client.mixin.render;

import dev.mzc.client.Sakura;
import dev.mzc.client.module.impl.misc.BetterFPS;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {
    @Inject(method = "shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z", at = @At("HEAD"), cancellable = true)
    private void mzc$betterFpsSkipEntity(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        BetterFPS betterFPS = Sakura.MODULES.getModule(BetterFPS.class);
        if (betterFPS != null && betterFPS.shouldSkipEntity(entity)) {
            cir.setReturnValue(false);
        }
    }
}

