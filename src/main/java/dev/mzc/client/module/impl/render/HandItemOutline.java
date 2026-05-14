package dev.mzc.client.module.impl.render;

import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.values.impl.ColorValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;

import java.awt.*;

public class HandItemOutline extends Module {

    public enum ColorMode {
        Single(),
        Double(),
        Rainbow(),
        Client();
        ColorMode() {
        }
    }

    private final EnumValue<ColorMode> colorMode = new EnumValue<>("ColorMode", ColorMode.Client, ColorMode.class);

    private final ColorValue color1 = new ColorValue("Color1", new Color(255, 255, 255, 255),
            () -> colorMode.is(ColorMode.Single) || colorMode.is(ColorMode.Double));
    private final ColorValue color2 = new ColorValue("Color2", new Color(0, 150, 255, 255), () -> colorMode.is(ColorMode.Double));
    private final NumberValue<Double> speed = new NumberValue<>("Speed", 5.0, 1.0, 20.0, 0.5,
            () -> colorMode.is(ColorMode.Rainbow));
    private final NumberValue<Float> thickness = new NumberValue<>("Thickness", 1.03f, 1.0f, 1.1f, 0.005f);
    private final NumberValue<Float> offset = new NumberValue<>("Offset", 0.02f, 0.0f, 0.08f, 0.002f);

    public HandItemOutline() {
        super("HandItemOutline", Category.Render);
        this.setType(ModuleType.All);
    }

    public ColorMode getColorMode() {
        return colorMode.get();
    }

    public Color getOutlineColor() {
        return switch (colorMode.get()) {
            case Client -> ClickGui.color(0);
            case Single -> color1.get();
            case Double -> color2.get();
            case Rainbow -> getRainbow();
        };
    }

    public Color getOutlineColor2() {
        return switch (colorMode.get()) {
            case Client -> ClickGui.color2(0);
            case Single -> color1.get();
            case Double -> color1.get();
            case Rainbow -> getRainbow2();
        };
    }

    private Color getRainbow() {
        double duration = 5.0 / speed.get();
        float hue = (float) ((System.currentTimeMillis() % (int) (duration * 1000)) / (duration * 1000));
        return Color.getHSBColor(hue, 0.8f, 1f);
    }

    private Color getRainbow2() {
        double duration = 5.0 / speed.get();
        float hue = (float) (((System.currentTimeMillis() + 800L) % (int) (duration * 1000)) / (duration * 1000));
        return Color.getHSBColor(hue, 0.8f, 1f);
    }

    public float getThickness() {
        return thickness.get();
    }

    public float getOffset() {
        return offset.get();
    }
}
