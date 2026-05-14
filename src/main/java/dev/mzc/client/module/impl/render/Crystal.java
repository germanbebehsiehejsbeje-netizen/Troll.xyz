package dev.mzc.client.module.impl.render;

import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.ColorValue;
import dev.mzc.client.values.impl.NumberValue;
import net.minecraft.util.Identifier;

import java.awt.*;

public class Crystal extends Module {


    public Crystal() {
        super("Crystal", Category.Render);
        this.setType(ModuleType.All);
    }

    public final BoolValue Texture = new BoolValue("Texture", false);
    public final BoolValue modifyScale = new BoolValue("Modify Scale", false);
    public final NumberValue<Float> scale = new NumberValue<>("Scale", 1.0f, 0.1f, 3.0f, 0.1f);
    public final ColorValue crystalColor = new ColorValue("Crystal Color", new Color(255, 255, 255, 255));

    public final BoolValue enableBreathing = new BoolValue("Breathing Effect", true);
    public final NumberValue<Float> breathingSpeed = new NumberValue<>("Breathing Speed", 1.0f, 0.1f, 5.0f, 0.1f);
    public final NumberValue<Float> breathingAmount = new NumberValue<>("Breathing Amount", 0.2f, 0.0f, 1.0f, 0.05f);

    public final BoolValue enableRotation = new BoolValue("Rotation Effect", true);
    public final NumberValue<Float> rotationSpeed = new NumberValue<>("Rotation Speed", 1.0f, 0.1f, 10.0f, 0.1f);


    public static final Identifier BLANK = Identifier.of("textures/blank.png");
}