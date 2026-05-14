package dev.mzc.client.module.impl.render;

import dev.mzc.client.events.render.item.HeldItemRendererEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;

public class ViewModel extends Module {

    public ViewModel() {
        super("ViewModel", Category.Render);
        this.setType(ModuleType.All);
    }

    public final NumberValue<Double> scaleMainX = new NumberValue<>("Scale Main X", 1.0, 0.1, 5.0, 0.01);
    public final NumberValue<Double> scaleMainY = new NumberValue<>("Scale Main Y", 1.0, 0.1, 5.0, 0.01);
    public final NumberValue<Double> scaleMainZ = new NumberValue<>("Scale Main Z", 1.0, 0.1, 5.0, 0.01);
    public final NumberValue<Double> mainX = new NumberValue<>("Main X", 0.0, -3.0, 3.0, 0.01);
    public final NumberValue<Double> mainY = new NumberValue<>("Main Y", 0.0, -3.0, 3.0, 0.01);
    public final NumberValue<Double> mainZ = new NumberValue<>("Main Z", 0.0, -3.0, 3.0, 0.01);

    public final NumberValue<Double> scaleOffX = new NumberValue<>("Scale Off X", 1.0, 0.1, 5.0, 0.01);
    public final NumberValue<Double> scaleOffY = new NumberValue<>("Scale Off Y", 1.0, 0.1, 5.0, 0.01);
    public final NumberValue<Double> scaleOffZ = new NumberValue<>("Scale Off Z", 1.0, 0.1, 5.0, 0.01);
    public final NumberValue<Double> offX = new NumberValue<>("Off X", 0.0, -3.0, 3.0, 0.01);
    public final NumberValue<Double> offY = new NumberValue<>("Off Y", 0.0, -3.0, 3.0, 0.01);
    public final NumberValue<Double> offZ = new NumberValue<>("Off Z", 0.0, -3.0, 3.0, 0.01);

    @EventHandler
    private void onHeldItemRender(HeldItemRendererEvent event) {
        MatrixStack stack = event.getMatrices();

        if (event.getHand() == Hand.MAIN_HAND) {
            stack.scale(scaleMainX.get().floatValue(), scaleMainY.get().floatValue(), scaleMainZ.get().floatValue());
            stack.translate(mainX.get(), mainY.get(), mainZ.get());
        } else {
            stack.scale(scaleOffX.get().floatValue(), scaleOffY.get().floatValue(), scaleOffZ.get().floatValue());
            stack.translate(offX.get(), offY.get(), offZ.get());
        }
    }
}