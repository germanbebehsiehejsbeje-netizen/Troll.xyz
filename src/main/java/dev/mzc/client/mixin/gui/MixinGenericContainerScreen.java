package dev.mzc.client.mixin.gui;

import dev.mzc.client.module.impl.hud.DynamicIslandHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GenericContainerScreen.class)
public class MixinGenericContainerScreen {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void mzc$suppressVanillaChest(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (DynamicIslandHud.shouldSuppressChestScreenStatic()) {
            ci.cancel();
        }
    }
}

