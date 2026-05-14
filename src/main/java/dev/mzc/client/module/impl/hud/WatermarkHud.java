package dev.mzc.client.module.impl.hud;

import dev.mzc.client.Sakura;
import dev.mzc.client.module.HudModule;
import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.nanovg.NanoVGRenderer;
import dev.mzc.client.nanovg.font.FontLoader;
import dev.mzc.client.nanovg.util.NanoVGHelper;
import dev.mzc.client.utils.math.FrameRateCounter;
import dev.mzc.client.values.Value;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.nanovg.NanoVG;

import java.awt.*;

public class WatermarkHud extends HudModule {
    private enum Style { GAMESENSE }

    private final Value<Style> style = new EnumValue<>("Style", Style.GAMESENSE);
    private final Value<Double> hudScale = new NumberValue<>("Scale", 1.0, 0.5, 2.0, 0.1);

    public WatermarkHud() {
        super("Watermark", 10, 10);
    }

    @Override
    public void onRender(DrawContext context) {
        float s = hudScale.get().floatValue();

        // Формируем строку как на скрине
        String fps = FrameRateCounter.INSTANCE.getFps() + " fps";
        String ping = getCurrentPing() + "ms";
        String text = Sakura.MOD_NAME.toLowerCase() + " | " + "github.com/mzc" + " | " + fps + " | " + ping;

        float fontSize = 13f * s;
        int font = FontLoader.regular((int) fontSize);
        float textW = NanoVGHelper.getTextWidth(text, font, fontSize);
        float textH = NanoVGHelper.getFontHeight(font, fontSize);

        float paddingX = 8f * s;
        float paddingY = 6f * s;

        this.width = textW + paddingX * 2;
        this.height = textH + paddingY * 2 + 2 * s; // +2 для полоски

        NanoVGRenderer.INSTANCE.draw(vg -> {
            // Фон (Черный прямоугольник с обводкой)
            NanoVGHelper.drawRect(x, y, width, height, new Color(15, 15, 15, 255));
            NanoVGHelper.drawRectOutline(x, y, width, height, 1f * s, new Color(40, 40, 40, 255));

            // Радужная полоска сверху
            NanoVGHelper.drawGradientRect(x + 1 * s, y + 1 * s, width - 2 * s, 1.5f * s, ClickGui.color(0), ClickGui.color2(0));

            // Текст
            NanoVGHelper.drawString(text, x + paddingX, y + paddingY + textH / 2f + 1 * s, font, fontSize,
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, Color.WHITE);
        });
    }

    private int getCurrentPing() {
        if (mc.player == null || mc.getNetworkHandler() == null) return 0;
        var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return entry != null ? Math.max(entry.getLatency(), 0) : 0;
    }
}