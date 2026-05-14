package dev.mzc.client.module.impl.render;

import dev.mzc.client.events.render.Render3DEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.values.impl.ColorValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.ListValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;

import java.awt.*;

public class EntityESP extends Module {

    public enum ColorMode {
        Single,
        Double,
        Rainbow,
        Client
    }

    private final ListValue<EntityType<?>> entities = new ListValue<>("Entities", ListValue.Type.ENTITY);
    private final EnumValue<ColorMode> colorMode = new EnumValue<>("ColorMode", ColorMode.Client);
    
    // Color Settings
    private final ColorValue color = new ColorValue("Color", new Color(255, 0, 0), () -> colorMode.is(ColorMode.Single));
    private final ColorValue color1 = new ColorValue("Color1", new Color(255, 0, 0), () -> colorMode.is(ColorMode.Double));
    private final ColorValue color2 = new ColorValue("Color2", new Color(0, 0, 255), () -> colorMode.is(ColorMode.Double));
    private final NumberValue<Double> speed = new NumberValue<>("Speed", 1.0, 0.1, 10.0, 0.1, () -> colorMode.is(ColorMode.Single) || colorMode.is(ColorMode.Client));
    private final NumberValue<Double> saturation = new NumberValue<>("Saturation", 1.0, 0.0, 1.0, 0.1, () -> colorMode.is(ColorMode.Rainbow));
    private final NumberValue<Double> brightness = new NumberValue<>("Brightness", 1.0, 0.0, 1.0, 0.1, () -> colorMode.is(ColorMode.Rainbow));

    public EntityESP() {
        super("EntityESP", Category.Render);
    }

    public static boolean shouldGlow(Entity entity) {
        if (dev.mzc.client.Sakura.mc.player == null) return false;
        EntityESP esp = dev.mzc.client.Sakura.MODULES.getModule(EntityESP.class);
        return esp != null && esp.isEnabled() && esp.entities.contains(entity.getType());
    }

    public static int getGlowColor() {
        EntityESP esp = dev.mzc.client.Sakura.MODULES.getModule(EntityESP.class);
        if (esp != null) {
            Color c = Color.WHITE;
            switch (esp.colorMode.get()) {
                case Single -> c = esp.color.get();
                case Double -> {
                    // Simple color interpolation logic
                    double time = (System.currentTimeMillis() * esp.speed.get()) % 2000.0 / 1000.0;
                    if (time > 1) time = 2 - time;
                    int r = (int) (esp.color1.get().getRed() + (esp.color2.get().getRed() - esp.color1.get().getRed()) * time);
                    int g = (int) (esp.color1.get().getGreen() + (esp.color2.get().getGreen() - esp.color1.get().getGreen()) * time);
                    int b = (int) (esp.color1.get().getBlue() + (esp.color2.get().getBlue() - esp.color1.get().getBlue()) * time);
                    c = new Color(r, g, b);
                }
                case Rainbow -> {
                    // Rainbow logic
                    float hue = (System.currentTimeMillis() % (long)(1000 / esp.speed.get())) / (float)(1000 / esp.speed.get());
                    c = Color.getHSBColor(hue, esp.saturation.get().floatValue(), esp.brightness.get().floatValue());
                }
                case Client -> c = new Color(ClickGui.color());
            }
            
            return c.getRGB();
        }
        return -1;
    }
    
    @Override
    public void onDisable() {
        if (mc.world != null) {
            for (Entity entity : mc.world.getEntities()) {
                if (entities.contains(entity.getType())) {
                    entity.setGlowing(false);
                }
            }
        }
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player && mc.options.getPerspective().isFirstPerson()) continue;
            
            if (entities.contains(entity.getType())) {
                entity.setGlowing(true);
            }
        }
    }
}
