package dev.mzc.client.module.impl.render;

import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.ColorValue;
import dev.mzc.client.values.impl.NumberValue;

import java.awt.*;

public class Atmosphere extends Module {
    public Atmosphere() {
        super("Atmosphere", Category.Render);
        this.setType(ModuleType.All);
    }

    public final BoolValue modifyTime = new BoolValue("Modify Time", false);
    public final BoolValue modifyFog = new BoolValue("Modify Fog", false);
    public final NumberValue<Integer> time = new NumberValue<>("Time", 12000, 0, 24000, 1000);
    public final ColorValue fogColor = new ColorValue("Fog Color", new Color(255, 255, 255));


}