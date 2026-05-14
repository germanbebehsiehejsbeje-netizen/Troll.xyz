package dev.mzc.client.module.impl.render;

import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.ColorValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;

import java.awt.*;

public class TotemParticles extends Module {
    public TotemParticles() {
        super("TotemParticles", Category.Render);
        this.setType(ModuleType.All);
    }

    public enum ColorMode {
        Static(),
        Gradient(),
        Rainbow(),
        Client();
        ColorMode() {
        }
    }

    private final BoolValue noRender = new BoolValue("No Render", false);
    private final EnumValue<ColorMode> colorMode = new EnumValue<>("Color Mode", ColorMode.Gradient, () -> !noRender.get());
    private final ColorValue color1 = new ColorValue("Color 1", new Color(255, 100, 100), () -> !noRender.get() && !colorMode.is(ColorMode.Client));
    private final ColorValue color2 = new ColorValue("Color 2", new Color(100, 255, 255), () -> !noRender.get() && colorMode.is(ColorMode.Gradient));
    private final NumberValue<Double> rainbowSpeed = new NumberValue<>("Rainbow Speed", 5.0, 1.0, 20.0, 0.5, () -> !noRender.get() && colorMode.is(ColorMode.Rainbow));

    private int particleIndex = 0;

    public boolean isNoRender() {
        return noRender.get();
    }

    public void resetIndex() {
        particleIndex = 0;
    }

    public Color getNextColor() {
        particleIndex++;
        return calculateColor(particleIndex);
    }

    private Color calculateColor(int index) {
        switch (colorMode.get()) {
            case Static -> {
                return color1.get();
            }
            case Gradient -> {
                float progress = (index % 100) / 100f;
                float r = color1.get().getRed() + (color2.get().getRed() - color1.get().getRed()) * progress;
                float g = color1.get().getGreen() + (color2.get().getGreen() - color1.get().getGreen()) * progress;
                float b = color1.get().getBlue() + (color2.get().getBlue() - color1.get().getBlue()) * progress;
                return new Color(
                        Math.max(0, Math.min(255, (int) r)),
                        Math.max(0, Math.min(255, (int) g)),
                        Math.max(0, Math.min(255, (int) b))
                );
            }
            case Rainbow -> {
                float hue = (float) ((System.currentTimeMillis() / 1000.0 * rainbowSpeed.get() * 0.1 + index * 0.02) % 1.0);
                return Color.getHSBColor(hue, 0.8f, 1.0f);
            }
            case Client -> {
                Color c1 = ClickGui.color(index);
                Color c2 = ClickGui.color2(index);
                float progress = (index % 100) / 100f;
                float r = c1.getRed() + (c2.getRed() - c1.getRed()) * progress;
                float g = c1.getGreen() + (c2.getGreen() - c1.getGreen()) * progress;
                float b = c1.getBlue() + (c2.getBlue() - c1.getBlue()) * progress;
                return new Color(
                        Math.max(0, Math.min(255, (int) r)),
                        Math.max(0, Math.min(255, (int) g)),
                        Math.max(0, Math.min(255, (int) b))
                );
            }
        }
        return color1.get();
    }
}
