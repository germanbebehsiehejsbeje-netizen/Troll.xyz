package dev.mzc.client.mixin.client;

import dev.mzc.client.Sakura;
import dev.mzc.client.events.EventType;
import dev.mzc.client.events.render.ScreenshotEvent;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.function.Consumer;

@Mixin(ScreenshotRecorder.class)
public class MixinScreenshotRecorder {
    
    @Inject(method = "saveScreenshot(Ljava/io/File;Lnet/minecraft/client/gl/Framebuffer;Ljava/util/function/Consumer;)V", at = @At("RETURN"))
    private static void onScreenshotPost(File gameDirectory, Framebuffer framebuffer, Consumer<Text> messageReceiver, CallbackInfo ci) {
        System.out.println("[MixinScreenshotRecorder] Screenshot POST event posting...");
        Sakura.EVENT_BUS.post(new ScreenshotEvent(EventType.POST));
    }
}
