package dev.mzc.client.utils.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import dev.mzc.client.utils.color.ColorUtil;
import dev.mzc.client.utils.math.MathUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;

public class RenderUtil {
    public static boolean isHovering(float x, float y, float width, float height, double mouseX, double mouseY) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    public static double deltaTime() {
        return MinecraftClient.getInstance().getCurrentFps() > 0 ? (1.0000 / MinecraftClient.getInstance().getCurrentFps()) : 1;
    }

    public static int colorSwitch(Color firstColor, Color secondColor, float time, int index, long timePerIndex, double speed, double alpha) {
        long now = (long) (speed * System.currentTimeMillis() + index * timePerIndex);

        float redDiff = (firstColor.getRed() - secondColor.getRed()) / time;
        float greenDiff = (firstColor.getGreen() - secondColor.getGreen()) / time;
        float blueDiff = (firstColor.getBlue() - secondColor.getBlue()) / time;
        int red = Math.round(secondColor.getRed() + redDiff * (now % (long) time));
        int green = Math.round(secondColor.getGreen() + greenDiff * (now % (long) time));
        int blue = Math.round(secondColor.getBlue() + blueDiff * (now % (long) time));

        float redInverseDiff = (secondColor.getRed() - firstColor.getRed()) / time;
        float greenInverseDiff = (secondColor.getGreen() - firstColor.getGreen()) / time;
        float blueInverseDiff = (secondColor.getBlue() - firstColor.getBlue()) / time;
        int inverseRed = Math.round(firstColor.getRed() + redInverseDiff * (now % (long) time));
        int inverseGreen = Math.round(firstColor.getGreen() + greenInverseDiff * (now % (long) time));
        int inverseBlue = Math.round(firstColor.getBlue() + blueInverseDiff * (now % (long) time));

        if (now % ((long) time * 2) < (long) time)
            return ColorUtil.getColor(inverseRed, inverseGreen, inverseBlue, (int) alpha);
        else return ColorUtil.getColor(red, green, blue, (int) alpha);
    }

    public static int colorSwitch(Color firstColor, Color secondColor, float time, int index, long timePerIndex, double speed) {
        return colorSwitch(firstColor, secondColor, time, index, timePerIndex, speed, 255);
    }

    public static void drawTracer(DrawContext guiGraphics, float x, float y, float size, float widthDiv, float heightDiv, int color) {
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(770, 771, 1, 0);
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);

        int topX = Math.round(x);
        int topY = Math.round(y);
        int leftX = Math.round(x - size / widthDiv);
        int rightX = Math.round(x + size / widthDiv);
        int midY = Math.round(y + size / heightDiv);
        int bottomY = Math.round(y + size);

        guiGraphics.fill(topX - 1, topY, topX + 1, midY, color);
        guiGraphics.fill(leftX, bottomY - 1, rightX, bottomY + 1, color);
        guiGraphics.fill(leftX, midY - 1, topX, bottomY, color);
        guiGraphics.fill(topX, midY - 1, rightX, bottomY, color);

        GlStateManager._disableBlend();
        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(true);
    }

    public static int getRainbow(long currentMillis, int speed, int offset, float alpha) {
        int rainbow = Color.HSBtoRGB(1.0F - ((currentMillis + (offset * 100)) % speed) / (float) speed,
                0.9F, 0.9F);
        int r = (rainbow >> 16) & 0xFF;
        int g = (rainbow >> 8) & 0xFF;
        int b = rainbow & 0xFF;
        int a = (int) (alpha * 255.0F);
        return ((a & 0xFF) << 24) |
                ((r & 0xFF) << 16) |
                ((g & 0xFF) << 8) |
                (b & 0xFF);
    }

    public static int getRainbow(long currentMillis, int speed, int offset) {
        return getRainbow(currentMillis, speed, offset, 1.0F);
    }

    public static double animate(double value, double target, double speed, boolean minedelta) {
        double c = value + (target - value) / (3 + speed * deltaTime());
        double v = value
                + ((target - value)) / (2 + speed);
        return minedelta ? v : c;
    }

    public static double animate(double value, double target) {
        return animate(value, target, 1, false);
    }

    public static float animate(float end, float start, float multiple) {
        return (1 - MathUtil.clamp((float) (deltaTime() * multiple), 0, 1)) * end + MathUtil.clamp((float) (deltaTime() * multiple), 0, 1) * start;
    }
}
