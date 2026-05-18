package dev.mzc.client.utils.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.mzc.client.shaders.BlurProgram;
import dev.mzc.client.shaders.DistortionShaderProgram;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

import java.awt.*;

public class Shader2DUtil {
    public static BlurProgram BLUR_PROGRAM;
    public static DistortionShaderProgram DISTORTION_PROGRAM;

    public static void init() {
        BLUR_PROGRAM = new BlurProgram();
        DISTORTION_PROGRAM = new DistortionShaderProgram();
    }

    public static void drawQuadBlur(MatrixStack matrices, float x, float y, float width, float height, float blurStrength, float blurOpacity) {
        BufferBuilder bb = preShaderDraw(matrices, x - 10, y - 10, width + 20, height + 20);

        BLUR_PROGRAM.setParameters(x, y, width, height, 0f, new Color(0, 0, 0, 0), blurStrength, blurOpacity);
        BLUR_PROGRAM.use();

        RenderLayers.debugQuads().draw(bb.end());
        endRender();
    }

    public static void drawDistortionBackground(MatrixStack matrices, float x, float y, float width, float height, float delta) {
        // Update shader time
        DISTORTION_PROGRAM.updateTime(delta);
        
        BufferBuilder bb = preShaderDraw(matrices, x, y, width, height);
        DISTORTION_PROGRAM.setParameters(width, height);
        DISTORTION_PROGRAM.use();

        RenderLayers.debugQuads().draw(bb.end());
        endRender();
    }

    public static void drawRoundedBlur(MatrixStack matrices, float x, float y, float width, float height, float radius, Color c1, float blurStrenth, float blurOpacity) {
        blurOpacity = Math.max(0f, Math.min(1f, blurOpacity));

        BufferBuilder bb = preShaderDraw(matrices, x - 10, y - 10, width + 20, height + 20);
        BLUR_PROGRAM.setParameters(x, y, width, height, radius, c1, blurStrenth, blurOpacity);
        BLUR_PROGRAM.use();

        RenderLayers.debugQuads().draw(bb.end());
        endRender();
    }

    public static void setRectanglePoints(BufferBuilder buffer, Matrix4f matrix, float x, float y, float x1, float y1) {
        buffer.vertex(matrix, x, y, 0).color(1f, 1f, 1f, 1f);
        buffer.vertex(matrix, x, y1, 0).color(1f, 1f, 1f, 1f);
        buffer.vertex(matrix, x1, y1, 0).color(1f, 1f, 1f, 1f);
        buffer.vertex(matrix, x1, y, 0).color(1f, 1f, 1f, 1f);
    }

    public static BufferBuilder preShaderDraw(MatrixStack matrices, float x, float y, float width, float height) {
        beginRender();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        setRectanglePoints(buffer, matrix, x, y, x + width, y + height);
        return buffer;
    }

    public static void beginRender() {
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(770, 771, 1, 0);
    }

    public static void endRender() {
        GlStateManager._blendFuncSeparate(770, 771, 1, 0);
        GlStateManager._disableBlend();
    }

    public static void setRefraction(float amount, float band) {
        if (BLUR_PROGRAM != null) {
            BLUR_PROGRAM.setRefractionParams(amount, band);
            BLUR_PROGRAM.setRefractionStrength(0.75f);
        }
    }

    public static void setRefractionStrength(float strength) {
        if (BLUR_PROGRAM != null) {
            BLUR_PROGRAM.setRefractionStrength(strength);
        }
    }

    public static void setLensCurvature(float curvature) {
        if (BLUR_PROGRAM != null) {
            BLUR_PROGRAM.setLensCurvature(curvature);
        }
    }
}
