package dev.mzc.client.module.impl.misc;

import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;

public class ItemPhysics extends Module {
    public static ItemPhysics INSTANCE;

    public final NumberValue<Double> rotateSpeed = new NumberValue<>("RotateSpeed", 1.0, 0.1, 5.0, 0.1);
    public final BoolValue oldRotation = new BoolValue("OldRotation", false);
    public final BoolValue fastRender = new BoolValue("FastRender", false);

    public ItemPhysics() {
        super("ItemPhysics", Category.Misc);
        this.setType(ModuleType.Safe);
        INSTANCE = this;
    }
}
