package dev.mzc.client.module.impl.render.totempopchams;

import dev.mzc.client.mixin.accessor.ModelPartAccessor;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

public class WireframePartExtractor implements ModelExtractor {

    @Override
    public void extractModelPart(final ModelPart part, final MatrixStack matrices, final VertexConsumer buffer, final int color, final double lineWidth) {
        matrices.push();
        part.applyTransform(matrices);
        final Matrix4f matrix = matrices.peek().getPositionMatrix();
        for (ModelPart.Cuboid cuboid : this.getCuboids(part)) {
            final float minX = (cuboid.minX / 16.0f);
            final float minY = (cuboid.minY / 16.0f);
            final float minZ = (cuboid.minZ / 16.0f);
            final float maxX = (cuboid.maxX / 16.0f);
            final float maxY = (cuboid.maxY / 16.0f);
            final float maxZ = (cuboid.maxZ / 16.0f);
            this.boxLines(buffer, matrix, (float) lineWidth, minX, minY, minZ, maxX, maxY, maxZ, color);
        }
        matrices.pop();
    }

    private void boxLines(final VertexConsumer buffer, final Matrix4f matrix, final float lineWidth, final float x1, final float y1, final float z1, final float x2, final float y2, final float z2, final int color) {
        final Vector3f[] vertices = new Vector3f[] {
            new Vector3f(x1, y1, z1), new Vector3f(x2, y1, z1), new Vector3f(x2, y2, z1), new Vector3f(x1, y2, z1),
            new Vector3f(x1, y1, z2), new Vector3f(x2, y1, z2), new Vector3f(x2, y2, z2), new Vector3f(x1, y2, z2)
        };

        final int[][] edges = new int[][] {
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };

        final int a = (color >> 24) & 0xFF;
        final int r = (color >> 16) & 0xFF;
        final int g = (color >> 8) & 0xFF;
        final int b = color & 0xFF;

        for (final int[] edge : edges) {
            final Vector3f from = vertices[edge[0]];
            final Vector3f to = vertices[edge[1]];
            final Vector3f normal = new Vector3f(to).sub(from).normalize();

            buffer.vertex(matrix, from.x, from.y, from.z).color(r, g, b, a).lineWidth(lineWidth).normal(normal.x, normal.y, normal.z);
            buffer.vertex(matrix, to.x, to.y, to.z).color(r, g, b, a).lineWidth(lineWidth).normal(normal.x, normal.y, normal.z);
        }
    }

    private List<ModelPart.Cuboid> getCuboids(final ModelPart part) {
        return ((ModelPartAccessor)(Object) part).getCuboids();
    }
}
