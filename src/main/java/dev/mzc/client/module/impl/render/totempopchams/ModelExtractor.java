package dev.mzc.client.module.impl.render.totempopchams;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;

public interface ModelExtractor {
    void extractModelPart(final ModelPart part, final MatrixStack matrices, final VertexConsumer buffer, final int color, final double lineWidth);
}
