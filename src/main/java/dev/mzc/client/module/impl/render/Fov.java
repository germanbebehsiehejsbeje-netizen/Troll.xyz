package dev.mzc.client.module.impl.render;

import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;

public class Fov extends Module {
    private final NumberValue<Double> targetFov = new NumberValue<>("FOV", 70.0, 10.0, 140.0, 1.0);

    private float currentFov = 70f;

    public Fov() {
        super("Fov", Category.Render);
        this.setType(ModuleType.All);
    }

    @Override
    protected void onEnable() {
        if (mc == null || mc.options == null) return;
        currentFov = (float) mc.options.getFov().getValue();
        // initialize target to current option value if user hasn't changed it
        if (targetFov.get() == null) {
            targetFov.set((double) currentFov);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        float desired = targetFov.get().floatValue();
        // smooth fixed to 1 (instant)
        currentFov = desired;
    }

    // Called from mixin to override the fov used for projection
    public float getCurrentFov(float original) {
        return currentFov;
    }
}
