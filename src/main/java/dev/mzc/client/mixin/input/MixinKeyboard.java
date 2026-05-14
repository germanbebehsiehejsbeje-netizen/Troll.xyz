package dev.mzc.client.mixin.input;

import dev.mzc.client.Sakura;
import dev.mzc.client.events.misc.KeyAction;
import dev.mzc.client.events.misc.KeyEvent;
import dev.mzc.client.gui.clickgui.ClickGuiScreen;
import dev.mzc.client.gui.clickgui.vape.MZCClickGuiScreen;
import dev.mzc.client.module.impl.client.ClickGui;
import net.minecraft.client.Keyboard;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class MixinKeyboard {
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    public void onKey(long window, int action, KeyInput input, CallbackInfo ci) {
        // 处理 F2 截图按键
        if (input.key() == GLFW.GLFW_KEY_F2 && action == GLFW.GLFW_PRESS) {
            // 发送截图前事件，让 ScreenshotBypass 模块隐藏 HUD
            Sakura.EVENT_BUS.post(new dev.mzc.client.events.render.ScreenshotEvent(dev.mzc.client.events.EventType.PRE));
        }
        
        // 对于 ClickGui，完全不拦截 ESC，让 GUI 自己处理
        if (input.key() == GLFW.GLFW_KEY_ESCAPE && action != GLFW.GLFW_RELEASE && Sakura.mc.currentScreen != null) {
            if (Sakura.mc.currentScreen instanceof ClickGuiScreen || Sakura.mc.currentScreen instanceof MZCClickGuiScreen) {
                // 不做任何处理，让事件正常传递
                return;
            }
        }

        if (Sakura.mc.currentScreen == null
                && input.key() == GLFW.GLFW_KEY_ESCAPE
                && action != GLFW.GLFW_RELEASE
                && ClickGui.shouldSuppressEscapeNow()) {
            ci.cancel();
            return;
        }

        if (Sakura.EVENT_BUS.post(new KeyEvent(input.key(), input.modifiers(), KeyAction.from(action))).isCancelled()) {
            ci.cancel();
        }
    }
}
