package dev.mzc.client.mixin.render;

import dev.mzc.client.gui.account.AccountSelectorScreen;
import dev.mzc.client.gui.mainmenu.MainMenuScreen;
import dev.mzc.client.shaders.MainMenuShader;
import dev.mzc.client.nanovg.NanoVGRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class MixinScreen {
    @Unique
    private boolean sakura$shaderRenderedThisFrame;

    @Inject(method = "renderWithTooltip", at = @At("HEAD"))
    private void onBeginNanoVgScreenBatch(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        sakura$shaderRenderedThisFrame = false;
        Screen self = (Screen) (Object) this;
        if (sakura$shouldRenderShader(self)) {
            sakura$shaderRenderedThisFrame = renderMzcMainMenuShader(context, mouseX, mouseY);
        }
        NanoVGRenderer.INSTANCE.beginBatch();
    }

    @Inject(method = "renderWithTooltip", at = @At("RETURN"))
    private void onEndNanoVgScreenBatch(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        NanoVGRenderer.INSTANCE.endBatch();
    }

    @Unique
    private boolean sakura$shouldRenderShader(Screen screen) {
        String simple = screen.getClass().getSimpleName();
        boolean isLoadingLike = simple.contains("Loading")
                || simple.contains("Downloading")
                || simple.contains("Connect")
                || simple.contains("Progress")
                || simple.contains("Reconfiguring")
                || simple.contains("Saving")
                || simple.contains("Receiving")
                || simple.contains("Disconnected");

        return screen instanceof SelectWorldScreen
                || screen instanceof MultiplayerScreen
                || screen instanceof MainMenuScreen
                || screen instanceof AccountSelectorScreen
                || screen instanceof LevelLoadingScreen
                || screen instanceof ConnectScreen
                || screen instanceof MessageScreen
                || screen instanceof ProgressScreen
                || screen instanceof ReconfiguringScreen
                || screen instanceof DisconnectedScreen
                || isLoadingLike;
    }

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void onRenderBackground(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (sakura$shouldRenderShader(self)) {
            
            // 非主界面恢复标准速度
            if (!(self instanceof MainMenuScreen)) {
                MainMenuShader.getSharedInstance().setSpeed(1.0f);
            }

            if (sakura$shaderRenderedThisFrame || renderMzcMainMenuShader(context, mouseX, mouseY)) {
                ci.cancel();
            }
        }
    }

    @Unique
    private boolean renderMzcMainMenuShader(DrawContext context, int mouseX, int mouseY) {
        try {
            MainMenuShader shader = MainMenuShader.getSharedInstance();
            int width = context.getScaledWindowWidth();
            int height = context.getScaledWindowHeight();
            shader.setMouseOffset((mouseX / (float) Math.max(1, width) - 0.5f) * width * 0.25f);
            shader.render(width, height, 1.0f);
            return true;
        } catch (Throwable t) {
            try {
                MainMenuShader.cleanupSharedInstance();
                MainMenuShader shader = MainMenuShader.getSharedInstance();
                int width = context.getScaledWindowWidth();
                int height = context.getScaledWindowHeight();
                shader.setMouseOffset((mouseX / (float) Math.max(1, width) - 0.5f) * width * 0.25f);
                shader.render(width, height, 1.0f);
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    @Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true)
    public void renderPanoramaBackgroundHook(DrawContext context, float delta, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (sakura$shouldRenderShader(self)) {
            ci.cancel();
            return;
        }
        
        // Use vanilla background rendering path for stability.
        MainMenuShader.cleanupSharedInstance();
    }
}
