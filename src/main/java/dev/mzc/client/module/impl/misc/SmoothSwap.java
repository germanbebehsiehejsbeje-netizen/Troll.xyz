package dev.mzc.client.module.impl.misc;

import dev.mzc.client.auth.UserRole;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.render.smoothswap.SmoothSwapManager;
import dev.mzc.client.values.impl.NumberValue;

public class SmoothSwap extends Module {

    public static SmoothSwap INSTANCE;
    public final NumberValue<Double> animationSpeed = new NumberValue<>("AnimationSpeed", 1.0, 0.1, 5.0, 0.1);

    public SmoothSwap() {
        super("SmoothSwap", Category.Misc);
        this.setType(ModuleType.Safe);
        this.setRequiredRole(UserRole.VIP);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        SmoothSwapManager.init();
    }
}
