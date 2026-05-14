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
        GAMESENSE, MODERN_BLUR, RISE, TENACITY
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
            renderGamesense(context, target, x, y + offset, 1.0f);
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
}