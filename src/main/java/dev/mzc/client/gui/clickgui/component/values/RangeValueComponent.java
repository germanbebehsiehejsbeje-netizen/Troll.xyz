package dev.mzc.client.gui.clickgui.component.values;

import dev.mzc.client.gui.Component;
import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.nanovg.NanoVGRenderer;
import dev.mzc.client.nanovg.font.FontLoader;
import dev.mzc.client.nanovg.util.NanoVGHelper;
import dev.mzc.client.utils.math.MathUtil;
import dev.mzc.client.utils.render.RenderUtil;
import dev.mzc.client.values.impl.RangeValue;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.nanovg.NanoVG;

import java.awt.*;
import java.text.DecimalFormat;

public class RangeValueComponent extends Component {
    private enum DragHandle {
        NONE,
        MIN,
        MAX
    }

    private final RangeValue<? extends Number> setting;
    private final boolean isWholeNumber;
    private DragHandle draggingHandle = DragHandle.NONE;
    private float minAnim;
    private float maxAnim;

    public RangeValueComponent(RangeValue<? extends Number> setting) {
        this.setting = setting;
        Number minValue = setting.getMinValue();
        this.isWholeNumber = minValue instanceof Integer || minValue instanceof Long;
    }

    @Override
    public void render(DrawContext guiGraphics, int mouseX, int mouseY, float partialTicks) {
        float baseFontSize = (float) ClickGui.getFontSize();
        float titleFontSize = baseFontSize * 0.75f;
        float w = getWidth();
        setHeight(30 * scale);

        double minBound = setting.getMin().doubleValue();
        double maxBound = setting.getMax().doubleValue();
        double currentMin = setting.getMinValue().doubleValue();
        double currentMax = setting.getMaxValue().doubleValue();

        double range = Math.max(1.0E-9, maxBound - minBound);
        float minTarget = (float) (w * (currentMin - minBound) / range);
        float maxTarget = (float) (w * (currentMax - minBound) / range);
        minAnim = RenderUtil.animate(minAnim, minTarget, 50);
        maxAnim = RenderUtil.animate(maxAnim, maxTarget, 50);

        NanoVGRenderer.INSTANCE.draw(vg -> {
            NanoVGHelper.drawString(setting.getDisplayName(), getX(), getY(), FontLoader.regular(titleFontSize), titleFontSize, Color.WHITE);

            float labelFontSize = baseFontSize * 0.5f;
            String boundMin = formatValue(setting.getMin().doubleValue());
            String boundMax = formatValue(setting.getMax().doubleValue());
            String current = formatValue(currentMin) + " - " + formatValue(currentMax);

            NanoVGHelper.drawString(boundMin, getX(), getY() + 18 * scale, FontLoader.regular(labelFontSize), labelFontSize, new Color(255, 255, 255, 255));
            float currentW = NanoVGHelper.getTextWidth(current, FontLoader.regular(labelFontSize), labelFontSize);
            NanoVGHelper.drawString(current, getX() + (w - currentW) / 2f, getY() + 18 * scale, FontLoader.regular(labelFontSize), labelFontSize, new Color(255, 255, 255, 255));
            float boundMaxW = NanoVGHelper.getTextWidth(boundMax, FontLoader.regular(labelFontSize), labelFontSize);
            NanoVGHelper.drawString(boundMax, getX() + w - boundMaxW, getY() + 18 * scale, FontLoader.regular(labelFontSize), labelFontSize, new Color(255, 255, 255, 255));

            float sliderY = getY() + 7 * scale;
            NanoVGHelper.drawRoundRect(getX(), sliderY, w, 4 * scale, 2 * scale, new Color(200, 200, 200, 255));

            float selectedX = getX() + Math.min(minAnim, maxAnim);
            float selectedW = Math.abs(maxAnim - minAnim);
            float extend = 1.5f * scale;
            float trackStart = getX();
            float trackEnd = getX() + w;
            float extStart = Math.max(trackStart, selectedX - extend);
            float extEnd = Math.min(trackEnd, selectedX + selectedW + extend);
            NanoVGHelper.drawGradientRRect2(extStart, sliderY, Math.max(0.0f, extEnd - extStart), 4 * scale, 2 * scale, ClickGui.color(0), ClickGui.color2(0));

            float handleMinX = getX() + minAnim;
            float handleMaxX = getX() + maxAnim;
            float handleY = getY() + 9 * scale;
            float handleSize = 6.3f * scale;
            drawRightAngleTriangle(vg, handleMinX, handleY, handleSize, true, new Color(255, 255, 255));
            drawRightAngleTriangle(vg, handleMaxX, handleY, handleSize, false, new Color(255, 255, 255));
        });

        if (draggingHandle != DragHandle.NONE) {
            double value = minBound + MathUtil.clamp((mouseX - getX()) / w, 0, 1) * (maxBound - minBound);
            value = MathUtil.incValue(value, setting.getStep().doubleValue());

            if (draggingHandle == DragHandle.MIN) {
                setMinFromDouble(value);
            } else {
                setMaxFromDouble(value);
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }

    @SuppressWarnings("unchecked")
    private void setMinFromDouble(double value) {
        if (setting.getMinValue() instanceof Integer) {
            ((RangeValue<Integer>) setting).setMinValue((int) value);
        } else if (setting.getMinValue() instanceof Long) {
            ((RangeValue<Long>) setting).setMinValue((long) value);
        } else if (setting.getMinValue() instanceof Float) {
            ((RangeValue<Float>) setting).setMinValue((float) value);
        } else {
            ((RangeValue<Double>) setting).setMinValue(value);
        }
    }

    @SuppressWarnings("unchecked")
    private void setMaxFromDouble(double value) {
        if (setting.getMinValue() instanceof Integer) {
            ((RangeValue<Integer>) setting).setMaxValue((int) value);
        } else if (setting.getMinValue() instanceof Long) {
            ((RangeValue<Long>) setting).setMaxValue((long) value);
        } else if (setting.getMinValue() instanceof Float) {
            ((RangeValue<Float>) setting).setMaxValue((float) value);
        } else {
            ((RangeValue<Double>) setting).setMaxValue(value);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        float w = getWidth();
        float sliderY = getY() + 7 * scale;
        if (mouseButton == 0 && RenderUtil.isHovering(getX(), sliderY, w, 4 * scale, (float) mouseX, (float) mouseY)) {
            float handleMinX = getX() + minAnim;
            float handleMaxX = getX() + maxAnim;
            double distToMin = Math.abs(mouseX - handleMinX);
            double distToMax = Math.abs(mouseX - handleMaxX);
            draggingHandle = distToMin <= distToMax ? DragHandle.MIN : DragHandle.MAX;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int state) {
        if (state == 0) {
            draggingHandle = DragHandle.NONE;
        }
        return super.mouseReleased(mouseX, mouseY, state);
    }

    private String formatValue(double value) {
        if (isWholeNumber) {
            return String.valueOf(Math.round(value));
        }
        return new DecimalFormat("#0.00").format(value);
    }

    private void drawRightAngleTriangle(long vg, float centerX, float centerY, float size, boolean pointRight, Color color) {
        float half = size * 0.5f;
        float left = centerX - half;
        float right = centerX + half;
        float top = centerY - half;
        float bottom = centerY + half;
        float midY = centerY;
        float[] p1;
        float[] p2;
        float[] p3;
        NanoVG.nvgBeginPath(vg);
        if (pointRight) {
            p1 = new float[]{left, top};
            p2 = new float[]{left, bottom};
            p3 = new float[]{right, midY};
        } else {
            p1 = new float[]{right, top};
            p2 = new float[]{right, bottom};
            p3 = new float[]{left, midY};
        }
        NanoVG.nvgMoveTo(vg, p1[0], p1[1]);
        NanoVG.nvgLineTo(vg, p2[0], p2[1]);
        NanoVG.nvgLineTo(vg, p3[0], p3[1]);
        NanoVG.nvgClosePath(vg);
        NanoVG.nvgFillColor(vg, NanoVGHelper.nvgColor(color));
        NanoVG.nvgFill(vg);

        NanoVG.nvgLineJoin(vg, NanoVG.NVG_ROUND);
        NanoVG.nvgStrokeWidth(vg, Math.max(1.2f * scale, size * 0.12f));
        NanoVG.nvgStrokeColor(vg, NanoVGHelper.nvgColor(color));
        NanoVG.nvgStroke(vg);
    }

    @Override
    public boolean isVisible() {
        return setting.isAvailable();
    }
}
