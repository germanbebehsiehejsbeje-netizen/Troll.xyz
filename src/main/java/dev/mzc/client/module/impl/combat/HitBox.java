package dev.mzc.client.module.impl.combat;

import dev.mzc.client.events.render.Render3DEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.utils.color.ColorUtil;
import dev.mzc.client.utils.render.Render3DUtil;
import dev.mzc.client.utils.render.RenderUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.ColorValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;

import net.minecraft.util.math.MathHelper;

import java.awt.*;

public class HitBox extends Module {

    private final NumberValue<Double> expand = new NumberValue<>("Expand", 0.5, 0.0, 5.0, 0.1);
    private final BoolValue render = new BoolValue("Render", false);
    private final EnumValue<RenderMode> renderMode = new EnumValue<>("Render Mode", RenderMode.Both, () -> render.get());
    private final EnumValue<ColorMode> colorMode = new EnumValue<>("Color Mode", ColorMode.Client, () -> render.get());
    private final ColorValue color = new ColorValue("Color", new Color(255, 255, 255), () -> render.get() && (colorMode.get() == ColorMode.Single || colorMode.get() == ColorMode.Double));
    private final ColorValue color2 = new ColorValue("Color 2", new Color(255, 0, 0), () -> render.get() && colorMode.get() == ColorMode.Double);

    public HitBox() {
        super("HitBox", Category.Combat);
        this.setType(ModuleType.Hack);
    }
    
    public double getExpand() {
        return (double) expand.get();
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!render.get()) return;

        Color c = getColor();
        // Fill color with lower alpha
        Color fillColor = new Color(c.getRed(), c.getGreen(), c.getBlue(), 80);
        // Outline color with full alpha
        Color outlineColor = new Color(c.getRed(), c.getGreen(), c.getBlue(), 255);

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof LivingEntity && entity != mc.player) {
                double tickDelta = event.getTickDelta();
                double x = MathHelper.lerp(tickDelta, entity.lastX, entity.getX());
                double y = MathHelper.lerp(tickDelta, entity.lastY, entity.getY());
                double z = MathHelper.lerp(tickDelta, entity.lastZ, entity.getZ());
                
                Box box = entity.getBoundingBox();
                // Offset box to interpolated position
                box = box.offset(x - entity.getX(), y - entity.getY(), z - entity.getZ());
                
                double expansion = expand.get();
                box = box.expand(expansion);
                
                switch (renderMode.get()) {
                    case Fill -> Render3DUtil.drawFilledBox(event.getMatrices(), box, fillColor.getRGB());
                    case Outline -> Render3DUtil.drawBoxOutline(event.getMatrices(), box, outlineColor.getRGB(), 2.0f);
                    case Both -> {
                        Render3DUtil.drawFilledBox(event.getMatrices(), box, fillColor.getRGB());
                        Render3DUtil.drawBoxOutline(event.getMatrices(), box, outlineColor.getRGB(), 2.0f);
                    }
                }
            }
        }
    }

    private Color getColor() {
        return switch (colorMode.get()) {
            case Client -> ClickGui.color(1);
            case Single -> color.get();
            case Double -> ColorUtil.interpolateColorsBackAndForth(15, 0, color.get(), color2.get(), false);
            case Rainbow -> new Color(RenderUtil.getRainbow(System.currentTimeMillis(), 0, 100)); // Default rainbow
        };
    }

    public enum RenderMode {
        Outline,
        Fill,
        Both
    }

    public enum ColorMode {
        Client,
        Single,
        Double,
        Rainbow
    }
}
