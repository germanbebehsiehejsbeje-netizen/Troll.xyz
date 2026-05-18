package dev.mzc.client.module.impl.hud;

import dev.mzc.client.Sakura;
import dev.mzc.client.module.HudModule;
import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.nanovg.NanoVGRenderer;
import dev.mzc.client.nanovg.font.FontLoader;
import dev.mzc.client.nanovg.util.NanoVGHelper;
import dev.mzc.client.utils.animations.AnimationUtil;
import dev.mzc.client.utils.entity.HealthUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.nanovg.NanoVG;
import java.awt.*;
import java.util.*;
import java.util.List;

public class TargetHud extends HudModule {

    public enum HudStyle {
        GAMESENSE, MODERN_BLUR, RISE, TENACITY, SPIRT, SEASON, RISE_NEW
    }

    public enum AvatarPos {
        LEFT, RIGHT
    }

    private final BoolValue hudEnabled = new BoolValue("HUD", true);
    private final EnumValue<HudStyle> hudStyle = new EnumValue<>("HudStyle", HudStyle.GAMESENSE, hudEnabled::get);
    private final EnumValue<AvatarPos> avatarPos = new EnumValue<>("AvatarPos", AvatarPos.RIGHT, hudEnabled::get);
    private final NumberValue<Double> hudScale = new NumberValue<>("Scale", 1.0, 0.5, 2.0, 0.1);
    private final NumberValue<Double> targetRange = new NumberValue<>("TargetRange", 6.0, 1.0, 20.0, 0.1);
    private final NumberValue<Double> maxTargets = new NumberValue<>("MaxTargets", 1.0, 1.0, 3.0, 1.0);

    private final Map<Integer, Float> animatedHealthMap = new HashMap<>();

    public TargetHud() {
        super("TargetHud", 100, 100);
    }

    @Override
    public void renderInGame(DrawContext context) {
        if (!hudEnabled.get()) return;

        float s = hudScale.get().floatValue();
        this.width = 155f * s;
        this.height = 42f * s;

        List<LivingEntity> targets = getTargets();
        if (targets.isEmpty()) {
            animatedHealthMap.clear();
            return;
        }

        float offset = 0;
        for (LivingEntity target : targets) {
            if (hudStyle.get() == HudStyle.SPIRT) {
                renderSpirt(context, target, x, y + offset, 1.0f);
            } else if (hudStyle.get() == HudStyle.SEASON) {
                renderSeason(context, target, x, y + offset, 1.0f);
            } else if (hudStyle.get() == HudStyle.RISE_NEW) {
                renderRiseNew(context, target, x, y + offset, 1.0f);
            } else {
                renderGamesense(context, target, x, y + offset, 1.0f);
            }
            offset += (height + 5);
        }

        // Cleanup health map for entities that are no longer targets
        animatedHealthMap.entrySet().removeIf(entry -> targets.stream().noneMatch(t -> t.getId() == entry.getKey()));
    }

    private void renderGamesense(DrawContext context, LivingEntity target, float baseX, float baseY, float alpha) {
        float s = hudScale.get().floatValue();

        NanoVGRenderer.INSTANCE.draw(vg -> {
            // 1. Фон и обводка
            NanoVGHelper.drawRect(baseX, baseY, width, height, withAlpha(new Color(15, 15, 15), alpha));
            NanoVGHelper.drawRectOutline(baseX, baseY, width, height, 1f * s, withAlpha(new Color(45, 45, 45), alpha));

            // 2. Радужная полоска сверху
            NanoVGHelper.drawGradientRect(baseX + 1 * s, baseY + 1 * s, width - 2 * s, 1.5f * s,
                    withAlpha(ClickGui.color(0), alpha), withAlpha(ClickGui.color2(0), alpha));
        });

        float pad = 6f * s;
        float avatarSize = height - (pad * 2) - (2 * s);
        
        boolean isRight = avatarPos.get() == AvatarPos.RIGHT;
        
        float avatarX = isRight ? (baseX + width - avatarSize - pad) : (baseX + pad);
        float avatarY = baseY + pad + 1.5f * s;
        
        float contentX = isRight ? (baseX + pad) : (baseX + avatarSize + pad * 2);

        // 5. Аватар
        if (target instanceof PlayerEntity player) {
            drawPlayerAvatar(context, player, avatarX, avatarY, avatarSize, alpha);
        } else {
            NanoVGRenderer.INSTANCE.draw(vg -> {
                NanoVGHelper.drawRect(avatarX, avatarY, avatarSize, avatarSize, withAlpha(new Color(30, 30, 30), alpha));
            });
        }

        NanoVGRenderer.INSTANCE.draw(vg -> {
            // 3. Текст имени (Слева или справа от аватара, нижний регистр)
            float fontSize = 13f * s;
            int font = FontLoader.regular((int) fontSize);
            String name = target.getName().getString().toLowerCase();
            NanoVGHelper.drawString(name, contentX, baseY + 12f * s, font, fontSize,
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, withAlpha(Color.WHITE, alpha));

            // 4. Полоска здоровья
            float barW = width - avatarSize - (pad * 3);
            float barH = 10f * s;
            float barX = contentX;
            float barY = baseY + height - barH - pad;

            NanoVGHelper.drawRect(barX, barY, barW, barH, withAlpha(new Color(25, 25, 25), alpha));

            float currentHp = HealthUtil.getEntityHealth(target);
            float maxHp = HealthUtil.getEntityMaxHealth(target);
            
            float animatedHp = animatedHealthMap.getOrDefault(target.getId(), currentHp);
            animatedHp = AnimationUtil.fast(animatedHp, currentHp, 15f);
            animatedHealthMap.put(target.getId(), animatedHp);

            float hpPct = Math.min(1f, Math.max(0f, animatedHp / maxHp));

            if (hpPct > 0) {
                NanoVGHelper.drawGradientRect(barX, barY, barW * hpPct, barH,
                        withAlpha(ClickGui.color(0), alpha), withAlpha(ClickGui.color2(0), alpha));
            }

            String hpText = String.format("%.1f hp", currentHp);
            NanoVGHelper.drawCenteredString(hpText, barX + barW / 2f, barY + barH / 2f + 1,
                    FontLoader.regular((int)(10 * s)), 10f * s, withAlpha(Color.WHITE, alpha));
        });
    }

    private List<LivingEntity> getTargets() {
        List<LivingEntity> list = new ArrayList<>();
        if (mc.world == null) return list;
        for (net.minecraft.entity.Entity e : mc.world.getEntities()) {
            if (e instanceof LivingEntity living && e != mc.player && e.isAlive()) {
                if (mc.player.distanceTo(e) <= targetRange.get()) list.add(living);
            }
        }
        list.sort(Comparator.comparingDouble(e -> mc.player.distanceTo(e)));
        int limit = maxTargets.get().intValue();
        return list.size() > limit ? list.subList(0, limit) : list;
    }

    private Color withAlpha(Color c, float alpha) {
        int a = (int)(c.getAlpha() * alpha);
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), MathHelper.clamp(a, 0, 255));
    }

    private void drawPlayerAvatar(DrawContext context, PlayerEntity player, float x, float y, float size, float alpha) {
        PlayerListEntry playerListEntry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
        if (playerListEntry != null) {
            PlayerSkinDrawer.draw(context, playerListEntry.getSkinTextures(), (int) x, (int) y, (int) size);
        }
    }

    private void renderSpirt(DrawContext context, LivingEntity target, float baseX, float baseY, float alpha) {
        float s = hudScale.get().floatValue();

        // SpirtHack цвета
        Color bgMain = new Color(22, 19, 41, 240);
        Color bgAccent = new Color(29, 25, 54, 255);
        Color accentColor = new Color(110, 85, 235);
        Color textColor = new Color(220, 220, 225);
        Color healthBarBg = new Color(15, 13, 28, 255);

        float pad = 6f * s;
        float avatarSize = height - (pad * 2);
        
        boolean isRight = avatarPos.get() == AvatarPos.RIGHT;
        
        float avatarX = isRight ? (baseX + width - avatarSize - pad) : (baseX + pad);
        float avatarY = baseY + pad;
        
        float contentX = isRight ? (baseX + pad) : (baseX + avatarSize + pad * 2);
        float contentWidth = width - avatarSize - (pad * 3);

        // Аватар
        if (target instanceof PlayerEntity player) {
            drawPlayerAvatar(context, player, avatarX, avatarY, avatarSize, alpha);
        } else {
            NanoVGRenderer.INSTANCE.draw(vg -> {
                NanoVGHelper.drawRoundRect(avatarX, avatarY, avatarSize, avatarSize, 4f * s, withAlpha(new Color(30, 27, 50), alpha));
            });
        }

        NanoVGRenderer.INSTANCE.draw(vg -> {
            // Фон с закругленными углами
            NanoVGHelper.drawRoundRect(baseX, baseY, width, height, 5f * s, bgMain);
            
            // Акцентная полоска сверху (правильная позиция)
            float accentBarY = baseY + 1.5f * s;
            NanoVGHelper.drawGradientRect(baseX + 3f * s, accentBarY, width - 6f * s, 2.5f * s,
                    withAlpha(accentColor, alpha), withAlpha(new Color(140, 115, 255, 255), alpha));
            
            // Имя цели и дистанция
            float fontSize = 13f * s;
            int font = FontLoader.regular((int) fontSize);
            String name = target.getName().getString().toLowerCase();
            
            float nameY = baseY + pad + 2f * s;
            
            // Иконка цели (❖)
            NanoVGHelper.drawString("❖", contentX, nameY, font, fontSize,
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, withAlpha(accentColor, alpha));
            
            float nameX = contentX + 15f * s;
            NanoVGHelper.drawString(name, nameX, nameY, font, fontSize,
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, withAlpha(textColor, alpha));
            
            // Дистанция до цели (справа вверху)
            float distance = mc.player.distanceTo(target);
            String distText = String.format("%.1fm", distance);
            float distX = baseX + width - pad;
            NanoVGHelper.drawString(distText, distX, nameY, font, 11f * s,
                    NanoVG.NVG_ALIGN_RIGHT | NanoVG.NVG_ALIGN_MIDDLE, withAlpha(accentColor, alpha));

            // Полоска здоровья
            float barW = contentWidth;
            float barH = 9f * s;
            float barX = contentX;
            float barY = baseY + height - barH - pad + 1f * s;

            // Фон полоски здоровья
            NanoVGHelper.drawRoundRect(barX, barY, barW, barH, 3f * s, withAlpha(healthBarBg, alpha));

            float currentHp = HealthUtil.getEntityHealth(target);
            float maxHp = HealthUtil.getEntityMaxHealth(target);
            
            float animatedHp = animatedHealthMap.getOrDefault(target.getId(), currentHp);
            animatedHp = AnimationUtil.fast(animatedHp, currentHp, 15f);
            animatedHealthMap.put(target.getId(), animatedHp);

            float hpPct = Math.min(1f, Math.max(0f, animatedHp / maxHp));

            if (hpPct > 0) {
                // Градиент здоровья в стиле Spirt
                NanoVGHelper.drawRoundRect(barX, barY, barW * hpPct, barH, 3f * s,
                        withAlpha(accentColor, alpha));
            }

            // Текст здоровья
            String hpText = String.format("%.1f", currentHp);
            NanoVGHelper.drawCenteredString(hpText, barX + barW / 2f, barY + barH / 2f,
                    FontLoader.regular((int)(9 * s)), 9f * s, withAlpha(Color.WHITE, alpha));
        });
    }

    private void renderSeason(DrawContext context, LivingEntity target, float baseX, float baseY, float alpha) {
        float s = hudScale.get().floatValue();
        this.width = 130f * s;
        this.height = 36f * s;

        // Палитра со скриншота
        Color bgMain = new Color(40, 42, 45, 230);       // Матовый темно-серый фон
        Color healthBarColor = new Color(85, 95, 180);   // Фиолетово-синяя полоска HP
        Color healthBarBg = new Color(25, 25, 25, 180);  // Темный фон для полоски
        Color textColor = new Color(240, 240, 245);      // Светлый текст

        NanoVGRenderer.INSTANCE.draw(vg -> {
            // Отрисовка фона панели
            NanoVGHelper.drawRect(baseX, baseY, width, height, bgMain);

            float pad = 3f * s;
            float avatarSize = height - (pad * 2);

            // 1. Отрисовка головы (Слева)
            float avatarX = baseX + pad;
            float avatarY = baseY + pad;
            if (target instanceof PlayerEntity player) {
                drawPlayerAvatar(context, player, avatarX, avatarY, avatarSize, alpha);
            } else {
                // Для мобов рисуем просто серый квадрат, если нет текстуры головы
                NanoVGHelper.drawRect(avatarX, avatarY, avatarSize, avatarSize, new Color(60, 62, 65));
            }

            // Позиционирование элементов справа
            float contentX = avatarX + avatarSize + 5f * s;
            int font = FontLoader.regular((int) (11 * s));

            // 2. Никнейм игрока (Справа вверху)
            String name = target.getName().getString();
            NanoVGHelper.drawString(name, contentX, baseY + 5f * s, font, 10f * s,
                    NanoVG.NVG_ALIGN_LEFT, textColor);

            // Расчет пропорций здоровья
            float currentHp = HealthUtil.getEntityHealth(target);
            float maxHp = HealthUtil.getEntityMaxHealth(target);
            float hpPct = Math.min(1f, Math.max(0f, currentHp / maxHp));

            // 3. Полоска здоровья (Под никнеймом)
            float barX = contentX;
            float barY = baseY + 16f * s;
            float barW = width - (contentX - baseX) - 6f * s;
            float barH = 4f * s;

            // Фон ХП бара
            NanoVGHelper.drawRect(barX, barY, barW, barH, healthBarBg);
            // Активная фиолетовая часть
            if (hpPct > 0) {
                NanoVGHelper.drawRect(barX, barY, barW * hpPct, barH, healthBarColor);
            }

            // 4. Текстовое значение здоровья (Справа внизу, рядом с баром)
            String hpText = String.format("%.1f", currentHp);
            float hpTextX = barX + barW + 3f * s;
            float hpTextY = barY + barH / 2f;

            // Рисуем число ХП
            NanoVGHelper.drawString(hpText, hpTextX, hpTextY, font, 9f * s,
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, textColor);

            // Рисуем фиолетовое сердечко "💜" сразу после цифр
            float hpTextWidth = NanoVGHelper.getTextWidth(hpText, font, 9f * s);
            NanoVGHelper.drawString("💜", hpTextX + hpTextWidth + 1f * s, hpTextY, font, 8f * s,
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, healthBarColor);
        });
    }

    private void renderRiseNew(DrawContext context, LivingEntity target, float baseX, float baseY, float alpha) {
        float s = hudScale.get().floatValue();
        this.width = 175f * s;
        this.height = 55f * s;

        NanoVGRenderer.INSTANCE.draw(vg -> {
            renderRiseHud(vg, context, target, baseX, baseY, s, alpha);
        });
    }

    private void renderRiseHud(long vg, DrawContext context, LivingEntity target, float baseX, float baseY, float s, float alpha) {
        // Основная палитра (Почти черный полупрозрачный фон)
        Color bgMain = withAlpha(new Color(10, 10, 12), alpha);
        Color textColor = withAlpha(new Color(240, 240, 245), alpha);
        Color grayText = withAlpha(new Color(150, 150, 155), alpha);

        // 1. Задний фон модуля
        NanoVGHelper.drawRect(baseX, baseY, width, height, bgMain);

        float pad = 5f * s;
        
        // 2. Место под скин/модель игрока (Слева) - показываем голову игрока
        float modelW = 32f * s;
        float modelH = height - pad * 2 - (3f * s);
        float modelX = baseX + pad;
        float modelY = baseY + pad;
        
        if (target instanceof PlayerEntity player) {
            // Рисуем реальную голову игрока
            drawPlayerAvatar(context, player, modelX, modelY, modelW, alpha);
        } else {
            // Для мобов - просто серый квадрат
            NanoVGHelper.drawRect(modelX, modelY, modelW, modelH, withAlpha(new Color(30, 30, 35), alpha));
        }

        float contentX = baseX + pad + modelW + 8f * s;
        int fontMain = FontLoader.regular((int) (13 * s));
        int fontSmall = FontLoader.regular((int) (10 * s));

        // 3. Никнейм цели
        String name = target.getName().getString();
        NanoVGHelper.drawString(name, contentX, baseY + 12f * s, fontMain, 13f * s,
                NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, textColor);

        // 4. Пинг/Задержка в верхнем правом углу (На скрине "1ms")
        String infoRight = "1ms"; 
        NanoVGHelper.drawString(infoRight, baseX + width - pad - 2f * s, baseY + 12f * s, fontSmall, 9f * s,
                NanoVG.NVG_ALIGN_RIGHT | NanoVG.NVG_ALIGN_MIDDLE, grayText);

        // Расчет физики для кругов и полосок
        float currentHp = HealthUtil.getEntityHealth(target);
        float maxHp = HealthUtil.getEntityMaxHealth(target);
        float hpPct = Math.min(1f, Math.max(0f, currentHp / maxHp));
        float distance = mc.player != null ? mc.player.distanceTo(target) : 0f;
        float yaw = target.getYaw() < 0 ? target.getYaw() + 360f : target.getYaw();

        // 5. Отрисовка трех круговых индикаторов (HP, Dist, Yaw)
        float circleY = baseY + 26f * s;
        float circleSpacing = 20f * s;

        // Первый круг: HP (Зеленый/Желтый)
        drawIndicatorCircle(vg, contentX, circleY, 7f * s, hpPct, new Color(140, 205, 70), String.format("%.0f", currentHp), fontSmall, s);
        
        // Второй круг: Дистанция (Красный)
        float distPct = Math.min(1f, distance / 20f);
        drawIndicatorCircle(vg, contentX + circleSpacing, circleY, 7f * s, distPct, new Color(215, 55, 65), String.format("%.1f", distance), fontSmall, s);
        
        // Третий круг: Угол/Направление (Серый)
        float yawPct = yaw / 360f;
        drawIndicatorCircle(vg, contentX + (circleSpacing * 2), circleY, 7f * s, yawPct, new Color(160, 160, 170), String.format("%.0f", yaw), fontSmall, s);

        // 6. Текст статуса под кругами ("Winning" / "Losing")
        String statusText = currentHp < (maxHp * 0.4f) ? "Winning" : "Fighting";
        if (mc.player != null && mc.player.getHealth() < target.getHealth()) statusText = "Losing";
        
        NanoVGHelper.drawString(statusText, contentX, baseY + 42f * s, fontMain, 13f * s,
                NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, textColor);

        // 7. Нижний длинный градиентный бар здоровья (на всю ширину)
        float barH = 2f * s;
        float barY = baseY + height - barH;
        
        // Темная подложка бара
        NanoVGHelper.drawRect(baseX, barY, width, barH, withAlpha(new Color(20, 20, 22), alpha));
        // Градиентная линия (от красного к зеленому)
        if (hpPct > 0) {
            NanoVGHelper.drawGradientRect(baseX, barY, width * hpPct, barH, 
                    withAlpha(new Color(230, 60, 60), alpha), withAlpha(new Color(120, 210, 80), alpha));
        }
    }

    // Метод отрисовки круглого индикатора через дуги NanoVG
    private void drawIndicatorCircle(long vg, float cx, float cy, float radius, float pct, Color color, String text, int font, float s) {
        // Задний серый круг
        NanoVG.nvgBeginPath(vg);
        NanoVG.nvgCircle(vg, cx, cy, radius);
        NanoVG.nvgStrokeColor(vg, NanoVGHelper.nvgColor(new Color(35, 35, 40)));
        NanoVG.nvgStrokeWidth(vg, 1.5f * s);
        NanoVG.nvgStroke(vg);

        // Активная цветная дуга
        if (pct > 0) {
            NanoVG.nvgBeginPath(vg);
            // Минус PI/2 чтобы отсчет шел строго сверху (12 часов)
            float startAngle = (float) (-Math.PI / 2);
            float endAngle = startAngle + (float) (pct * Math.PI * 2);
            NanoVG.nvgArc(vg, cx, cy, radius, startAngle, endAngle, 1);
            NanoVG.nvgStrokeColor(vg, NanoVGHelper.nvgColor(color));
            NanoVG.nvgStrokeWidth(vg, 1.5f * s);
            NanoVG.nvgStroke(vg);
        }

        // Текст внутри круга
        NanoVGHelper.drawCenteredString(text, cx, cy + 0.5f * s, font, 6.5f * s, Color.WHITE);
    }
}