package dev.mzc.client.module.impl.movement;

import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.NumberValue;

public class BetterSneak extends Module {
    private final NumberValue<Double> offsetMultiplier = new NumberValue<>("OffsetMultiplier", 0.5, 0.0, 1.0, 0.05);

    public BetterSneak() {
        super("BetterSneak", Category.Movement);
        this.setType(ModuleType.Safe);
    }

    public double getOffsetMultiplier() {
        return offsetMultiplier.get();
    }
}

