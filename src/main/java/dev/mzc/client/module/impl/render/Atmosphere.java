package dev.mzc.client.module.impl.render;

import dev.mzc.client.events.render.Render3DEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.render.Shader2DUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.ColorValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;

import java.awt.*;

public class Atmosphere extends Module {
    public Atmosphere() {
        super("Atmosphere", Category.Render);
        this.setType(ModuleType.All);
    }

    public final BoolValue modifyTime = new BoolValue("Modify Time", false);
    public final BoolValue modifyFog = new BoolValue("Modify Fog", false);
    public final BoolValue fogBlur = new BoolValue("Fog Blur", false);
    public final NumberValue<Integer> time = new NumberValue<>("Time", 12000, 0, 24000, 1000);
    public final ColorValue fogColor = new ColorValue("Fog Color", new Color(255, 255, 255));
    public final NumberValue<Float> fogBlurRadius = new NumberValue<>("Fog Blur Radius", 5.0f, 1.0f, 20.0f, 0.5f);
    
    // Fog Distance Settings
    public final NumberValue<Float> fogStart = new NumberValue<>("Fog Start", 0.0f, 0.0f, 256.0f, 1.0f, () -> modifyFog.get());
    public final NumberValue<Float> fogEnd = new NumberValue<>("Fog End", 64.0f, 16.0f, 512.0f, 1.0f, () -> modifyFog.get());

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!fogBlur.get()) return;
        
        // Apply blur effect to create fog blur
        if (Shader2DUtil.BLUR_PROGRAM != null) {
            Shader2DUtil.BLUR_PROGRAM.setParameters(0, 0, 
                mc.getWindow().getScaledWidth(),
                mc.getWindow().getScaledHeight(),
                fogBlurRadius.get(), 
                fogColor.get(), 
                1.0f, 
                0.5f);
            Shader2DUtil.BLUR_PROGRAM.use();
        }
    }


}