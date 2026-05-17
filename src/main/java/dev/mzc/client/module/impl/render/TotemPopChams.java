package dev.mzc.client.module.impl.render;

import dev.mzc.client.Sakura;
import dev.mzc.client.events.render.Render3DEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.module.impl.render.totempopchams.ChamExtractor;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.ColorValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;

import java.awt.*;

public class TotemPopChams extends Module {
    public TotemPopChams() {
        super("TotemPopChams", Category.Render);
        this.setType(ModuleType.All);
    }

    public final BoolValue filledModelEnabled = new BoolValue("Filled Model", true);
    public final ColorValue filledColor = new ColorValue("Filled Color", new Color(0x9317DE5D, true), filledModelEnabled::get);

    public final BoolValue wireframeEnabled = new BoolValue("Wireframe", true);
    public final ColorValue wireframeColor = new ColorValue("Wireframe Color", new Color(0x932DD8E8, true), wireframeEnabled::get);
    public final NumberValue<Double> wireframeThickness = new NumberValue<>("Wireframe Thickness", 2.0, 0.5, 5.0, 0.1, wireframeEnabled::get);

    public final BoolValue disperse = new BoolValue("Disperse", true);
    public final NumberValue<Double> disperseSpeed = new NumberValue<>("Disperse Speed", 5.0, 1.0, 10.0, 0.1, disperse::get);
    public final NumberValue<Double> disperseMaxDistance = new NumberValue<>("Disperse Max Distance", 3.0, 0.5, 10.0, 0.1, disperse::get);

    public final BoolValue fadeOut = new BoolValue("Fade Out", true);
    public final NumberValue<Double> lifeTime = new NumberValue<>("Life Time", 10.0, 1.0, 30.0, 0.5);

    public final BoolValue showOwnPops = new BoolValue("Show Own Pops", false);

    private final ChamExtractor extractor = new ChamExtractor();

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;
        int capturedCount = dev.mzc.client.module.impl.render.totempopchams.TotemPopChamsHandler.getPositions().size();
        if (capturedCount > 0) {
            Sakura.LOGGER.debug("[TotemPopChams] Rendering {} captured players", capturedCount);
        }
        extractor.extract(event.getMatrices(), event.getTickDelta(), this);
    }
}
