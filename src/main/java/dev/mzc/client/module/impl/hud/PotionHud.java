package dev.mzc.client.module.impl.hud;

import dev.mzc.client.Sakura;
import dev.mzc.client.module.HudModule;
import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.module.impl.client.HudEditor;
import dev.mzc.client.nanovg.NanoVGRenderer;
import dev.mzc.client.nanovg.font.FontLoader;
import dev.mzc.client.nanovg.util.NanoVGHelper;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.nanovg.NanoVG;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class PotionHud extends HudModule {
    public enum Style {
        Gamesense("Gamesense"),
        Spirt("Spirt"),
        Season("Season");

        private final String name;

        Style(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private final NumberValue<Double> hudScale = new NumberValue<>("Scale", 1.0, 0.5, 2.0, 0.1);
    private final EnumValue<Style> style = new EnumValue<>("Style", Style.Gamesense);

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

        if (style.get() == Style.Spirt) {
            renderSpirt(context, effects, s, font, fontSize, inEditor);
        } else if (style.get() == Style.Season) {
            renderSeason(context, effects, s, font, fontSize, inEditor);
        } else {
            renderGamesense(context, effects, s, font, fontSize, inEditor);
        }
    }

    private void renderGamesense(DrawContext context, List<StatusEffectInstance> effects, float s, int font, float fontSize, boolean inEditor) {
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

    private void renderSpirt(DrawContext context, List<StatusEffectInstance> effects, float s, int font, float fontSize, boolean inEditor) {
        // SpirtHack цвета
        Color bg = new Color(22, 19, 41, 240);
        Color accent = new Color(110, 85, 235);
        Color lineColors = new Color(29, 25, 54, 150);
        Color textWhite = new Color(220, 220, 225);

        float pad = 10f * s;
        float rowHeight = 24f * s;
        float titleHeight = 26f * s;
        float radius = 5f * s;
        float iconSize = 18f * s;

        this.width = 160f * s;
        this.height = titleHeight + (effects.isEmpty() ? (inEditor ? rowHeight : 0) : effects.size() * rowHeight) + 4f * s;

        NanoVGRenderer.INSTANCE.draw(vg -> {
            // Рисуем закругленный фон всей панели
            NanoVGHelper.drawRoundRect(x, y, width, height, radius, bg);

            // 1. Отрисовка шапки "» Potions"
            NanoVGHelper.drawString("»", x + pad, y + titleHeight / 2f, font, fontSize,
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, accent);
            
            NanoVGHelper.drawString("Potions", x + pad + 14f * s, y + titleHeight / 2f, font, fontSize,
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, textWhite);

            // Линия под шапкой
            NanoVGHelper.drawRect(x, y + titleHeight, width, 1f * s, lineColors);

            // 2. Отрисовка строк с зельями
            float currentY = y + titleHeight;
            for (int i = 0; i < effects.size(); i++) {
                StatusEffectInstance se = effects.get(i);
                String name = getEffectName(se).toLowerCase();
                String time = formatDuration(se.getDuration());

                // Рисуем иконку зелья через Minecraft RenderSystem
                int iconX = (int)(x + pad);
                int iconY = (int)(currentY + (rowHeight - iconSize) / 2f);
                drawPotionIcon(context, se, iconX, iconY, (int)iconSize);

                // Название зелья (после иконки)
                float nameX = x + pad + iconSize + 5f * s;
                NanoVGHelper.drawString(name, nameX, currentY + rowHeight / 2f, font, 12f * s,
                        NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, textWhite);

                // Время (справа, фиолетовое)
                NanoVGHelper.drawString(time, x + width - pad, currentY + rowHeight / 2f, font, 12f * s,
                        NanoVG.NVG_ALIGN_RIGHT | NanoVG.NVG_ALIGN_MIDDLE, accent);

                // Рисуем разделяющую линию для всех строк, кроме последней
                if (i < effects.size() - 1) {
                    NanoVGHelper.drawRect(x, currentY + rowHeight, width, 1f * s, lineColors);
                }

                currentY += rowHeight;
            }
            
            if (effects.isEmpty() && inEditor) {
                NanoVGHelper.drawString("no effects", x + width / 2f, currentY + rowHeight / 2f, font, 12f * s,
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

    private void drawPotionIcon(DrawContext context, StatusEffectInstance effect, int x, int y, int size) {
        try {
            // Получаем идентификатор эффекта
            var effectType = effect.getEffectType().value();
            var effectId = Registries.STATUS_EFFECT.getId(effectType);
            
            if (effectId != null) {
                // Формируем путь к текстуре: minecraft:textures/mob_effect/<effect_name>.png
                Identifier textureId = Identifier.of("minecraft", "textures/mob_effect/" + effectId.getPath() + ".png");
                
                // Рисуем текстуру через RenderLayer
                context.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    textureId,
                    x, y,
                    0, 0,
                    size, size,
                    size, size
                );
            } else {
                drawFallbackIcon(x, y, size, effect);
            }
        } catch (Exception e) {
            drawFallbackIcon(x, y, size, effect);
        }
    }
    
    private void drawFallbackIcon(int x, int y, int size, StatusEffectInstance effect) {
        // Fallback - рисуем цветную иконку с первой буквой
        int color = effect.getEffectType().value().getColor();
        Color effectColor = new Color(color);
        
        NanoVGRenderer.INSTANCE.draw(vg -> {
            NanoVGHelper.drawRoundRect(x, y, size, size, 3f, new Color(
                effectColor.getRed(), 
                effectColor.getGreen(), 
                effectColor.getBlue(), 
                200
            ));
            
            String effectName = effect.getEffectType().value().getName().getString();
            String initial = effectName.substring(0, 1).toUpperCase();
            
            int font = FontLoader.regular((int)(size * 0.6f));
            NanoVGHelper.drawString(initial, x + size / 2f, y + size / 2f, font, size * 0.6f,
                    NanoVG.NVG_ALIGN_CENTER | NanoVG.NVG_ALIGN_MIDDLE, Color.WHITE);
        });
    }

    private void renderSeason(DrawContext context, List<StatusEffectInstance> effects, float s, int font, float fontSize, boolean inEditor) {
        class Potion {
            String name, duration;
            public Potion(String name, String duration) { 
                this.name = name; 
                this.duration = duration; 
            }
        }

        // Заполняем список эффектов
        List<Potion> potionList = new ArrayList<>();
        for (StatusEffectInstance se : effects) {
            potionList.add(new Potion(getEffectName(se).toLowerCase(), formatDuration(se.getDuration())));
        }

        if (potionList.isEmpty() && !inEditor) return;

        Color colorHeader = new Color(255, 255, 255, 255);
        Color colorBody = new Color(175, 175, 175, 220);
        Color textDark = new Color(25, 25, 25);
        Color textGray = new Color(70, 70, 70);

        float radius = 10f * s;
        float headerH = 26f * s;
        float rowH = 20f * s;

        this.width = 145f * s;
        this.height = headerH + (potionList.size() * rowH) + 6f * s;

        NanoVGRenderer.INSTANCE.draw(vg -> {
            // Тень панели
            NanoVGHelper.drawRoundRect(x, y + 2f * s, width, height, radius, new Color(0, 0, 0, 35));

            // Фон и шапка
            NanoVGHelper.drawRoundRect(x, y, width, height, radius, colorBody);
            NanoVGHelper.drawRoundRect(x, y, width, headerH + 4f * s, radius, colorHeader);
            NanoVGHelper.drawRect(x, y + headerH - 1f * s, width, 5f * s, colorHeader);

            int fontS = FontLoader.regular((int)(13f * s));

            // Иконка колбы (символ ⚗) и заголовок
            NanoVGHelper.drawString("⚗", x + 11f * s, y + headerH / 2f, fontS, 14f * s, 
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, textDark);
            NanoVGHelper.drawString("Potions", x + 28f * s, y + headerH / 2f, fontS, 12.5f * s, 
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, textDark);

            // Вывод эффектов
            float currentY = y + headerH + 3f * s;
            for (Potion p : potionList) {
                NanoVGHelper.drawString(p.name, x + 12f * s, currentY + rowH / 2f, fontS, 12f * s, 
                        NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, textGray);
                
                NanoVGHelper.drawString(p.duration, x + width - 12f * s, currentY + rowH / 2f, fontS, 12f * s, 
                        NanoVG.NVG_ALIGN_RIGHT | NanoVG.NVG_ALIGN_MIDDLE, textDark);
                currentY += rowH;
            }
            
            if (potionList.isEmpty() && inEditor) {
                NanoVGHelper.drawString("no effects", x + width / 2f, currentY + rowH / 2f, fontS, 12f * s,
                        NanoVG.NVG_ALIGN_CENTER | NanoVG.NVG_ALIGN_MIDDLE, new Color(100, 100, 100));
            }
        });
    }
}
