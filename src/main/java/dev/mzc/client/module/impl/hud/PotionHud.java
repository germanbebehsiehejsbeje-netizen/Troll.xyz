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
<<<<<<< HEAD
    public enum Style {
        Gamesense("Gamesense"),
        Spirt("Spirt"),
        Season("Season"),
        Compact("Compact");

        private final String name;

        Style(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

=======
>>>>>>> parent of 584bcf3 (update fixed movecorection and elytra rezolver)
    private final NumberValue<Double> hudScale = new NumberValue<>("Scale", 1.0, 0.5, 2.0, 0.1);

    // Icon font for Compact style
    private int iconFontId = -1;

    private int getCompactIconFont(float size) {
        return FontLoader.badcache((int) size);
    }

    // Map potion effects to icon characters from badcache.ttf
    private String getPotionIconChar(net.minecraft.entity.effect.StatusEffect effect) {
        // badcache.ttf icon mapping:
        // A = Headshot, B = Knife, C = Sun/Visuals, D = Moon, E = Sword/Combat
        // F = Player, G = Lightning, H = Brush/Colors, I = Crosshair, J = Soldier
        
        if (effect == net.minecraft.entity.effect.StatusEffects.STRENGTH) return "E"; // Sword - strength
        if (effect == net.minecraft.entity.effect.StatusEffects.HASTE) return "E"; // Sword - haste/combat
        if (effect == net.minecraft.entity.effect.StatusEffects.SPEED) return "G"; // Lightning - speed
        if (effect == net.minecraft.entity.effect.StatusEffects.JUMP_BOOST) return "G"; // Lightning - jump
        if (effect == net.minecraft.entity.effect.StatusEffects.INSTANT_HEALTH) return "F"; // Player - health
        if (effect == net.minecraft.entity.effect.StatusEffects.REGENERATION) return "F"; // Player - regen
        if (effect == net.minecraft.entity.effect.StatusEffects.ABSORPTION) return "F"; // Player - absorption
        if (effect == net.minecraft.entity.effect.StatusEffects.SATURATION) return "F"; // Player - food
        if (effect == net.minecraft.entity.effect.StatusEffects.RESISTANCE) return "A"; // Headshot/Defense
        if (effect == net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE) return "C"; // Sun/Fire
        if (effect == net.minecraft.entity.effect.StatusEffects.WATER_BREATHING) return "H"; // Brush/Water
        if (effect == net.minecraft.entity.effect.StatusEffects.INVISIBILITY) return "D"; // Moon/Invisible
        if (effect == net.minecraft.entity.effect.StatusEffects.NIGHT_VISION) return "C"; // Sun/Vision
        if (effect == net.minecraft.entity.effect.StatusEffects.SLOWNESS) return "B"; // Knife/Slow
        if (effect == net.minecraft.entity.effect.StatusEffects.WEAKNESS) return "B"; // Knife/Weak
        if (effect == net.minecraft.entity.effect.StatusEffects.POISON) return "B"; // Knife/Poison
        if (effect == net.minecraft.entity.effect.StatusEffects.WITHER) return "B"; // Knife/Wither
        
        // Default icon
        return "F"; // Player icon as default
    }

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

<<<<<<< HEAD
        if (style.get() == Style.Spirt) {
            renderSpirt(context, effects, s, font, fontSize, inEditor);
        } else if (style.get() == Style.Season) {
            renderSeason(context, effects, s, font, fontSize, inEditor);
        } else if (style.get() == Style.Compact) {
            renderCompact(context, effects, s, font, fontSize, inEditor);
        } else {
            renderGamesense(context, effects, s, font, fontSize, inEditor);
        }
    }

    private void renderGamesense(DrawContext context, List<StatusEffectInstance> effects, float s, int font, float fontSize, boolean inEditor) {
=======
>>>>>>> parent of 584bcf3 (update fixed movecorection and elytra rezolver)
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
        if (ticks == -1 || ticks > 1000000) {
            return "**:**";
        }
        int totalSeconds = ticks / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
<<<<<<< HEAD

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
        if (effects.isEmpty() && !inEditor) return;

        int fontRegular = FontLoader.regular(12);
        int fontBold = FontLoader.regular(13);

        float cardW = 120f * s;
        float cardH = 32f * s;
        float spacing = 5f * s;
        float radius = 8f * s;

        this.width = cardW;
        this.height = (cardH + spacing) * effects.size() - spacing;

        // Copy to avoid ConcurrentModificationException
        List<StatusEffectInstance> effectList = new ArrayList<>(effects);

        // Clean up animations for removed effects
        java.util.Set<String> currentKeys = new java.util.HashSet<>();
        for (StatusEffectInstance effect : effectList) {
            currentKeys.add(effect.getEffectType().value().getName().getString());
        }
        effectAnimations.keySet().removeIf(key -> !currentKeys.contains(key));

        NanoVGRenderer.INSTANCE.draw(vg -> {
            float currentY = y;

            for (int i = 0; i < effectList.size(); i++) {
                StatusEffectInstance effect = effectList.get(i);
                var effectType = effect.getEffectType();
                String name = effectType.value().getName().getString();
                String effectKey = name;
                
                // Get animation progress
                float rawProgress = getAnimationProgress(effectKey);
                float animProgress = easeOutCubic(rawProgress);
                
                // Format duration to MM:SS
                int durationTicks = effect.getDuration();
                String timeText = formatDuration(durationTicks);

                // Extract effect color for left indicator and heart
                int colorRGB = effectType.value().getColor();
                Color effectColor = new Color(colorRGB);
                
                // Brighten color if too dark
                effectColor = brightenColor(effectColor, 1.3f);

                // Calculate animated position (slide from right)
                float slideOffset = (1.0f - animProgress) * 30f * s;
                float cardX = x + slideOffset;
                
                // Calculate animated alpha and scale
                float cardAlpha = animProgress;
                float cardScale = 0.95f + (0.05f * animProgress);
                float scaledCardW = cardW * cardScale;
                float scaledCardH = cardH * cardScale;
                float yOffset = (cardH - scaledCardH) / 2f;

                // Card colors with glass effect
                Color bgCard = new Color(15, 15, 17, (int)(220 * cardAlpha));
                Color glassHighlight = new Color(255, 255, 255, (int)(30 * cardAlpha));
                Color textMain = new Color(240, 240, 245, (int)(255 * cardAlpha));
                
                // Timer turns red if less than 15 seconds (300 ticks)
                Color timeColor = (durationTicks <= 300) 
                        ? new Color(230, 70, 70, (int)(255 * cardAlpha)) 
                        : new Color(180, 180, 185, (int)(255 * cardAlpha));

                // Bloom glow color
                Color bloomColor = new Color(effectColor.getRed(), effectColor.getGreen(), effectColor.getBlue(), (int)(80 * cardAlpha * animProgress));

                // Apply vertical offset for scaling
                float adjustedY = currentY + yOffset;

                // === BLOOM EFFECT ===
                // Draw bloom glow behind card
                for (int bloomLayer = 3; bloomLayer > 0; bloomLayer--) {
                    float bloomSize = 4f * s * bloomLayer;
                    float bloomAlpha = (0.15f / bloomLayer) * cardAlpha * animProgress;
                    Color bloomGlow = new Color(
                            effectColor.getRed(), 
                            effectColor.getGreen(), 
                            effectColor.getBlue(), 
                            (int)(255 * bloomAlpha)
                    );
                    NanoVGHelper.drawRoundRect(
                            cardX - bloomSize / 2f, 
                            adjustedY - bloomSize / 2f,
                            scaledCardW + bloomSize, 
                            scaledCardH + bloomSize, 
                            radius + bloomSize, 
                            bloomGlow
                    );
                }

                // === GLASS CARD BACKGROUND ===
                // Main dark card
                NanoVGHelper.drawRoundRect(cardX, adjustedY, scaledCardW, scaledCardH, radius, bgCard);
                
                // Glass highlight (top gradient)
                NanoVGHelper.drawGradientRect(
                        cardX + 1f * s, 
                        adjustedY + 1f * s, 
                        scaledCardW - 2f * s, 
                        scaledCardH * 0.4f, 
                        glassHighlight, 
                        new Color(255, 255, 255, 0)
                );
                
                // Glass border (subtle white edge)
                NanoVGHelper.drawRoundRectOutline(cardX, adjustedY, scaledCardW, scaledCardH, radius, 1.5f * s, 
                        new Color(255, 255, 255, (int)(40 * cardAlpha)));

                // === LEFT VERTICAL COLORED INDICATOR ===
                NanoVGHelper.drawRoundRect(cardX, adjustedY, 5f * s * animProgress, scaledCardH, radius, effectColor);
                NanoVGHelper.drawRect(cardX + 2.5f * s, adjustedY, 2.5f * s * animProgress, scaledCardH, effectColor);

                // === HEART ICON '❤' with bloom ===
                float heartX = cardX + 18f * s;
                float heartY = adjustedY + scaledCardH / 2f;
                
                // Heart bloom
                NanoVGHelper.drawCenteredString("❤", heartX, heartY + 1f * s, fontBold, 22f * s, 
                        new Color(effectColor.getRed(), effectColor.getGreen(), effectColor.getBlue(), (int)(100 * cardAlpha * animProgress)));
                // Main heart
                NanoVGHelper.drawCenteredString("❤", heartX, heartY + 1f * s, fontBold, 18f * s, effectColor);

                // === TEXT ===
                float textX = cardX + 34f * s;
                
                // Effect name with fade-in
                NanoVGHelper.drawString(name, textX, adjustedY + 10f * s, fontBold, 12.5f * s, 
                        NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, textMain);
                
                // Timer with fade-in
                NanoVGHelper.drawString(timeText, textX, adjustedY + 22f * s, fontRegular, 11f * s, 
                        NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, timeColor);

                // Move Y coordinate for next effect card
                currentY += cardH + spacing;
            }
        });
    }

    private Color brightenColor(Color color, float factor) {
        int r = Math.min(255, (int)(color.getRed() * factor));
        int g = Math.min(255, (int)(color.getGreen() * factor));
        int b = Math.min(255, (int)(color.getBlue() * factor));
        return new Color(r, g, b, color.getAlpha());
    }

    // Animation tracking for Season style
    private java.util.Map<String, Float> effectAnimations = new java.util.HashMap<>();
    private long lastFrameTime = 0;

    private float getAnimationProgress(String effectKey) {
        long currentTime = System.nanoTime();
        if (lastFrameTime == 0) {
            lastFrameTime = currentTime;
            effectAnimations.clear();
        }
        
        float delta = (currentTime - lastFrameTime) / 1_000_000_000f;
        lastFrameTime = currentTime;
        
        // Initialize new effects at 0
        if (!effectAnimations.containsKey(effectKey)) {
            effectAnimations.put(effectKey, 0f);
        }
        
        // Smoothly animate to 1.0
        float progress = effectAnimations.get(effectKey);
        progress = Math.min(1.0f, progress + delta * 5.0f); // 5 = faster animation speed
        effectAnimations.put(effectKey, progress);
        
        return progress;
    }

    private float easeOutCubic(float t) {
        return 1.0f - (float) Math.pow(1.0f - t, 3);
    }

    private void renderCompact(DrawContext context, List<StatusEffectInstance> effects, float s, int font, float fontSize, boolean inEditor) {
        if (effects.isEmpty() && !inEditor) return;

        float baseWidth = 135f * s;
        float headerHeight = 22f * s;
        float rowHeight = 18f * s;
        float paddingBottom = 6f * s;

        this.width = baseWidth;
        this.height = headerHeight + (effects.size() * rowHeight) + paddingBottom;

        List<StatusEffectInstance> effectList = new ArrayList<>(effects);

        NanoVGRenderer.INSTANCE.draw(vg -> {
            Color bgCard = new Color(24, 28, 34, 235);
            Color textWhite = new Color(240, 243, 248);
            Color textGray = new Color(160, 165, 175);
            Color iconBlue = new Color(110, 155, 245);

            // 1. Render main card body
            NanoVGHelper.drawRoundRect(x, y, width, height, 8f * s, bgCard);

            // Fonts for different elements
            int fontHeader = FontLoader.regular((int)(13f * s));
            int fontName = FontLoader.regular((int)(11.5f * s));
            int fontTimer = FontLoader.regular((int)(11f * s));

            // Icon size
            float iconSize = 12f * s;

            // 2. Draw header icon from badcache.ttf
            float iconFontSize = 12f * s;
            int iconFont = getCompactIconFont(iconFontSize);
            String headerIconChar = "F"; // Using Player icon for potions
            
            // Draw blue glow behind icon
            float iconW = NanoVGHelper.getTextWidth(headerIconChar, iconFont, iconFontSize);
            float iconH = NanoVGHelper.getFontHeight(iconFont, iconFontSize);
            NanoVGHelper.drawCircle(x + iconSize / 2f + 8f * s, y + headerHeight / 2f, iconSize / 2f + 1.5f * s, 
                    new Color(110, 155, 245, 50));
            
            // Draw icon
            NanoVGHelper.drawString(headerIconChar, x + 8f * s + iconSize / 2f, y + headerHeight / 2f + iconH * 0.3f, 
                    iconFont, iconFontSize, NanoVG.NVG_ALIGN_CENTER | NanoVG.NVG_ALIGN_MIDDLE, iconBlue);
            
            // Header text
            NanoVGHelper.drawString("Potions", x + 8f * s + iconSize + 4f * s, y + headerHeight / 2f, fontHeader, 12f * s, 
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, textWhite);

            // 3. Render potion effects
            float currentY = y + headerHeight + 2f * s;

            for (StatusEffectInstance effect : effectList) {
                String name = effect.getEffectType().value().getName().getString();
                
                // Get icon character and color for this effect
                String effectIconChar = getPotionIconChar(effect.getEffectType().value());
                int effectColorRGB = effect.getEffectType().value().getColor();
                Color iconColor = new Color(effectColorRGB);

                // Draw effect icon using icon.ttf
                float effectIconX = x + 9f * s + iconSize / 2f;
                float effectIconY = currentY + (rowHeight - iconSize) / 2f + iconSize * 0.3f;
                
                // Draw colored glow behind icon
                NanoVGHelper.drawCircle(effectIconX, effectIconY - iconSize * 0.3f + iconSize / 2f, 
                        iconSize / 2f + 1f * s, new Color(iconColor.getRed(), iconColor.getGreen(), iconColor.getBlue(), 40));
                
                // Draw icon
                NanoVGHelper.drawString(effectIconChar, effectIconX, effectIconY, 
                        fontName, iconSize, NanoVG.NVG_ALIGN_CENTER | NanoVG.NVG_ALIGN_MIDDLE, iconColor);

                // Effect name
                NanoVGHelper.drawString(name, x + 9f * s + iconSize + 5f * s, currentY + rowHeight / 2f, fontName, 11.5f * s, 
                        NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, textWhite);

                // 4. Custom timer with superscript seconds
                int totalSecs = effect.getDuration() / 20;
                int mins = totalSecs / 60;
                int secs = totalSecs % 60;

                String minStr = mins + ":";
                String secStr = String.format("%02d", secs);

                // Draw minutes and colon
                float minWidth = NanoVGHelper.getTextWidth(minStr, fontTimer, 11f * s);
                float secWidth = NanoVGHelper.getTextWidth(secStr, fontTimer, 9f * s);
                
                float startTimerX = x + width - 10f * s - minWidth - secWidth;

                // Minutes (Normal size)
                NanoVGHelper.drawString(minStr, startTimerX, currentY + rowHeight / 2f, fontTimer, 11f * s, 
                        NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, textWhite);

                // Seconds (Slightly smaller)
                NanoVGHelper.drawString(secStr, startTimerX + minWidth, currentY + rowHeight / 2f, fontTimer, 9f * s, 
                        NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, textGray);

                currentY += rowHeight;
            }

            if (effects.isEmpty() && inEditor) {
                NanoVGHelper.drawString("no effects", x + width / 2f, currentY + rowHeight / 2f, fontName, 11.5f * s,
                        NanoVG.NVG_ALIGN_CENTER | NanoVG.NVG_ALIGN_MIDDLE, textGray);
            }
        });
    }
=======
>>>>>>> parent of 584bcf3 (update fixed movecorection and elytra rezolver)
}
