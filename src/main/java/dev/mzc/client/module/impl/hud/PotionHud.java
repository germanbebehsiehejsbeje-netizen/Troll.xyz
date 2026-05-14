package dev.mzc.client.module.impl.hud;

import dev.mzc.client.Sakura;
import dev.mzc.client.module.HudModule;
import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.module.impl.client.HudEditor;
import dev.mzc.client.nanovg.NanoVGRenderer;
import dev.mzc.client.nanovg.font.FontLoader;
import dev.mzc.client.nanovg.util.NanoVGHelper;
import dev.mzc.client.values.impl.NumberValue;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.nanovg.NanoVG;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class PotionHud extends HudModule {
    private final NumberValue<Double> hudScale = new NumberValue<>("Scale", 1.0, 0.5, 2.0, 0.1);

    public PotionHud() {
        super("PotionHud", 10, 150);
    }

    @Override
    public void onRender(DrawContext context) {
        if (mc.player == null) return;

        float s = hudScale.get().floatValue();
        int font = FontLoader.regular((int) (13 * s));
        float fontSize = 13 * s;

        List<StatusEffectInstance> effects = new ArrayList<>(mc.player.getStatusEffects());
        boolean inEditor = mc.currentScreen != null && Sakura.MODULES.getModule(HudEditor.class) != null && Sakura.MODULES.getModule(HudEditor.class).isEnabled();

        if (effects.isEmpty() && !inEditor) return;

        float padding = 8 * s;
        float rowHeight = 16 * s;
        float titleHeight = 18 * s;

        float maxW = NanoVGHelper.getTextWidth("potions", font, fontSize);
        for (StatusEffectInstance se : effects) {
            String name = getEffectName(se).toLowerCase();
            String time = "  " + formatDuration(se.getDuration()); // Space for icon
            maxW = Math.max(maxW, NanoVGHelper.getTextWidth(name, font, fontSize) + NanoVGHelper.getTextWidth(time, font, fontSize) + 30 * s);
        }

        this.width = Math.max(100 * s, maxW + padding * 2);
        this.height = titleHeight + (effects.isEmpty() ? (inEditor ? rowHeight : 0) : effects.size() * rowHeight) + padding / 2f;

        NanoVGRenderer.INSTANCE.draw(vg -> {
            // Фон Gamesense
            NanoVGHelper.drawRect(x, y, width, height, new Color(15, 15, 15, 255));
            NanoVGHelper.drawRectOutline(x, y, width, height, 1f * s, new Color(45, 45, 45, 255));
            NanoVGHelper.drawGradientRect(x + 1*s, y + 1*s, width - 2*s, 1.5f * s, ClickGui.color(0), ClickGui.color2(0));

            // Заголовок
            NanoVGHelper.drawString("potions", x + width / 2f, y + titleHeight / 2f + 1*s, font, fontSize,
                    NanoVG.NVG_ALIGN_CENTER | NanoVG.NVG_ALIGN_MIDDLE, Color.WHITE);

            float currentY = y + titleHeight;
            for (StatusEffectInstance se : effects) {
                String name = getEffectName(se).toLowerCase();
                String time = formatDuration(se.getDuration());
                String clock = "⌛";

                // Название слева
                NanoVGHelper.drawString(name, x + padding, currentY + rowHeight / 2f, font, fontSize,
                        NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, new Color(180, 180, 180));

                // Время справа
                float timeW = NanoVGHelper.getTextWidth(time, font, fontSize);
                NanoVGHelper.drawString(time, x + width - padding, currentY + rowHeight / 2f, font, fontSize,
                        NanoVG.NVG_ALIGN_RIGHT | NanoVG.NVG_ALIGN_MIDDLE, Color.WHITE);

                // Иконка часов перед временем (цветная)
                NanoVGHelper.drawString(clock, x + width - padding - timeW - 4*s, currentY + rowHeight / 2f, font, fontSize - 2*s,
                        NanoVG.NVG_ALIGN_RIGHT | NanoVG.NVG_ALIGN_MIDDLE, ClickGui.color(0));

                currentY += rowHeight;
            }
            
            if (effects.isEmpty() && inEditor) {
                NanoVGHelper.drawString("no effects", x + width / 2f, currentY + rowHeight / 2f, font, fontSize - 2*s,
                        NanoVG.NVG_ALIGN_CENTER | NanoVG.NVG_ALIGN_MIDDLE, new Color(100, 100, 100));
            }
        });
    }

    private String getEffectName(StatusEffectInstance se) {
        String name = se.getEffectType().value().getName().getString();
        int amp = se.getAmplifier();
        return amp > 0 ? name + " " + (amp + 1) : name;
    }

    private String formatDuration(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
