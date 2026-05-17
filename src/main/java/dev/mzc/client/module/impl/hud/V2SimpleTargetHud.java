package dev.mzc.client.module.impl.hud;

import dev.mzc.client.Sakura;
import dev.mzc.client.manager.Managers;
import dev.mzc.client.module.HudModule;
import dev.mzc.client.module.impl.client.HudEditor;
import dev.mzc.client.module.impl.combat.KillAura;
import dev.mzc.client.nanovg.NanoVGRenderer;
import dev.mzc.client.nanovg.font.FontLoader;
import dev.mzc.client.nanovg.util.NanoVGHelper;
import dev.mzc.client.utils.animations.AnimationUtil;
import dev.mzc.client.utils.entity.HealthUtil;
import dev.mzc.client.utils.render.Shader2DUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.ColorValue;
import dev.mzc.client.values.impl.NumberValue;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.nanovg.NanoVG;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class V2SimpleTargetHud extends HudModule {
    private final NumberValue<Double> scale = new NumberValue<>("Scale", 1.0, 0.6, 2.0, 0.05);
    private final BoolValue useKillAuraTarget = new BoolValue("Use KillAura Target", true);
    private final BoolValue playersOnly = new BoolValue("Players Only", true);
    private final NumberValue<Double> targetRange = new NumberValue<>("Target Range", 8.0, 2.0, 24.0, 0.5);
    private final BoolValue blur = new BoolValue("Blur", true);
    private final NumberValue<Double> blurStrength = new NumberValue<>("Blur Strength", 7.0, 1.0, 18.0, 0.5, blur::get);
    private final NumberValue<Double> radius = new NumberValue<>("Radius", 8.0, 2.0, 14.0, 0.5);
    private final BoolValue showAvatar = new BoolValue("Avatar", true);
    private final BoolValue showHealthText = new BoolValue("Health Text", true);
    private final BoolValue damageTrail = new BoolValue("Damage Trail", true);
    private final ColorValue backgroundColor = new ColorValue("Background Color", new Color(10, 10, 11, 222));
    private final ColorValue healthColor = new ColorValue("Health Color", new Color(232, 232, 232, 255));
    private final ColorValue damageColor = new ColorValue("Damage Color", new Color(110, 46, 46, 210));

    private LivingEntity lastTarget;
    private float fade;
    private float healthPctAnim = 1f;
    private float damagePctAnim = 1f;
    private float previousHealth = -1f;
    private float previousHealthPct = 1f;
    private final Map<Integer, Integer> skinImages = new HashMap<>();

    public V2SimpleTargetHud() {
        super("V2SimpleTargetHud", 100, 135);
    }

    @Override
    public void renderInGame(DrawContext context) {
        HudEditor hudEditor = Sakura.MODULES.getModule(HudEditor.class);
        if (hudEditor != null && hudEditor.isEnabled() && mc.currentScreen instanceof dev.mzc.client.gui.hud.HudEditorScreen) {
            return;
        }
        renderTargetHud(context, findTarget());
    }

    @Override
    public void renderInEditor(DrawContext context, float mouseX, float mouseY) {
        if (dragging) {
            int gameWidth = mc.getWindow().getScaledWidth();
            int gameHeight = mc.getWindow().getScaledHeight();
            x = Math.max(0, Math.min(mouseX - dragX, gameWidth - width));
            y = Math.max(0, Math.min(mouseY - dragY, gameHeight - height));
            relativeX = x / gameWidth;
            relativeY = y / gameHeight;
        }
        renderTargetHud(context, mc.player);
        NanoVGRenderer.INSTANCE.draw(vg -> NanoVGHelper.drawRoundRectOutline(x, y, width, height, radius.get().floatValue() * scale.get().floatValue(), 0.8f, new Color(255, 255, 255, 42)));
    }

    private void renderTargetHud(DrawContext context, LivingEntity target) {
        float s = scale.get().floatValue();
        float baseW = showAvatar.get() ? 124f : 104f;
        float baseH = 40f;
        width = baseW * s;
        height = baseH * s;

        boolean visible = target != null;
        fade = AnimationUtil.fast(fade, visible ? 1f : 0f, 10f);
        if (fade <= 0.01f) return;

        if (target == null) target = lastTarget;
        if (target == null) return;
        if (target != lastTarget) {
            previousHealth = -1f;
        }
        lastTarget = target;

        float drawAlpha = MathHelper.clamp(fade, 0f, 1f);
        float cardRadius = radius.get().floatValue() * s;

        if (blur.get()) {
            Shader2DUtil.drawRoundedBlur(new MatrixStack(), x, y, width, height, cardRadius, new Color(0, 0, 0, 0), blurStrength.get().floatValue(), drawAlpha);
        }

        float avatarSize = 26f * s;
        float pad = 7f * s;
        float avatarX = x + pad;
        float avatarY = y + (height - avatarSize) / 2f;
        float contentX = showAvatar.get() ? avatarX + avatarSize + 7f * s : x + pad;
        float contentW = x + width - pad - contentX;
        float nameY = y + 12f * s;
        float infoY = y + 23f * s;
        float barX = contentX;
        float barY = y + height - 9f * s;
        float barW = contentW;
        float barH = 4f * s;

        NanoVGRenderer.INSTANCE.draw(vg -> {
            Color bg = backgroundColor.get();
            NanoVGHelper.drawRoundRect(x, y, width, height, cardRadius, withAlpha(bg, drawAlpha));
            NanoVGHelper.drawGradientRRect(x, y, width, height, cardRadius, withAlpha(lift(bg, 16, 64), drawAlpha), withAlpha(darken(bg, 7), drawAlpha));
            NanoVGHelper.drawRoundRectOutline(x + 0.5f, y + 0.5f, width - 1f, height - 1f, Math.max(0, cardRadius - 0.5f), 0.8f, withAlpha(new Color(58, 58, 62, 115), drawAlpha));
        });

        if (showAvatar.get()) {
            drawAvatar(context, target, avatarX, avatarY, avatarSize, drawAlpha);
        }

        float health = Math.max(0f, HealthUtil.getEntityHealth(target));
        float maxHealth = getMaxHealth(target, health);
        float healthPct = MathHelper.clamp(health / maxHealth, 0f, 1f);
        updateHealthAnimation(health, healthPct);

        String name = target.getName().getString();
        String info = target instanceof PlayerEntity ? distanceText(target) : target.getType().getName().getString();
        String hpText = String.format("%.1f", health);

        NanoVGRenderer.INSTANCE.draw(vg -> {
            float nameSize = 10.5f * s;
            float infoSize = 7.5f * s;
            float hpSize = 8.5f * s;
            int nameFont = FontLoader.medium(nameSize);
            int infoFont = FontLoader.regular(infoSize);
            int hpFont = FontLoader.medium(hpSize);
            float hpTextWidth = showHealthText.get() ? NanoVGHelper.getTextWidth(hpText, hpFont, hpSize) : 0f;
            float nameMaxWidth = Math.max(8f * s, contentW - hpTextWidth - (showHealthText.get() ? 7f * s : 0f));
            String fittedName = fitText(name, nameFont, nameSize, nameMaxWidth);

            NanoVGHelper.save();
            NanoVGHelper.intersectScissor(contentX, y, nameMaxWidth, height);
            NanoVGHelper.drawString(fittedName, contentX, nameY, nameFont, nameSize, NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, withAlpha(new Color(244, 244, 244, 255), drawAlpha));
            NanoVGHelper.restore();

            NanoVGHelper.save();
            NanoVGHelper.intersectScissor(contentX, y, contentW, height);
            NanoVGHelper.drawString(info, contentX, infoY, infoFont, infoSize, NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, withAlpha(new Color(150, 150, 154, 255), drawAlpha));
            NanoVGHelper.restore();

            if (showHealthText.get()) {
                NanoVGHelper.drawString(hpText, x + width - pad, nameY, hpFont, hpSize, NanoVG.NVG_ALIGN_RIGHT | NanoVG.NVG_ALIGN_MIDDLE, withAlpha(new Color(214, 214, 216, 255), drawAlpha));
            }

            NanoVGHelper.drawRoundRect(barX, barY, barW, barH, barH / 2f, withAlpha(new Color(30, 30, 32, 235), drawAlpha));
            if (damageTrail.get() && damagePctAnim > healthPctAnim) {
                NanoVGHelper.drawRoundRect(barX, barY, barW * damagePctAnim, barH, barH / 2f, withAlpha(damageColor.get(), drawAlpha));
            }
            if (healthPctAnim > 0.002f) {
                NanoVGHelper.drawRoundRect(barX, barY, barW * healthPctAnim, barH, barH / 2f, withAlpha(healthColor.get(), drawAlpha));
            }
        });
    }

    private void updateHealthAnimation(float health, float healthPct) {
        if (previousHealth < 0f) {
            previousHealth = health;
            previousHealthPct = healthPct;
            healthPctAnim = healthPct;
            damagePctAnim = healthPct;
            return;
        }

        if (health < previousHealth) {
            damagePctAnim = Math.max(damagePctAnim, previousHealthPct);
        } else if (health > previousHealth) {
            damagePctAnim = healthPct;
        }

        healthPctAnim = AnimationUtil.fast(healthPctAnim, healthPct, 18f);
        damagePctAnim = AnimationUtil.fast(damagePctAnim, healthPct, health < previousHealth ? 5f : 12f);
        previousHealth = health;
        previousHealthPct = healthPct;
    }

    private LivingEntity findTarget() {
        if (useKillAuraTarget.get()) {
            KillAura killAura = KillAura.getInstance();
            if (killAura != null && killAura.isEnabled()) {
                LivingEntity target = killAura.getTarget();
                if (isValidKillAuraTarget(target)) return target;
            }
        }

        if (mc.world == null || mc.player == null) return null;
        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || !isValidTarget(living)) continue;
            double distance = mc.player.distanceTo(living);
            if (distance < closestDistance) {
                closest = living;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity == null || entity == mc.player || !entity.isAlive()) return false;
        if (mc.player == null) return false;
        if (mc.player.distanceTo(entity) > targetRange.get()) return false;
        if (playersOnly.get() && !(entity instanceof PlayerEntity)) return false;
        if (entity instanceof PlayerEntity player && Managers.FRIEND != null && Managers.FRIEND.isFriend(player.getName().getString())) return false;
        return true;
    }

    private boolean isValidKillAuraTarget(LivingEntity entity) {
        if (entity == null || entity == mc.player || !entity.isAlive()) return false;
        if (mc.player == null) return false;
        if (mc.player.distanceTo(entity) > targetRange.get()) return false;
        if (entity instanceof PlayerEntity player && Managers.FRIEND != null && Managers.FRIEND.isFriend(player.getName().getString())) return false;
        return true;
    }

    private float getMaxHealth(LivingEntity target, float health) {
        if (target instanceof PlayerEntity player) {
            return Math.max(Math.max(20f, player.getMaxHealth() + player.getAbsorptionAmount()), health);
        }
        return Math.max(1f, HealthUtil.getEntityMaxHealth(target));
    }

    private void drawAvatar(DrawContext context, LivingEntity target, float avatarX, float avatarY, float avatarSize, float alpha) {
        NanoVGRenderer.INSTANCE.draw(vg -> {
            NanoVGHelper.drawRoundRect(avatarX - 1f, avatarY - 1f, avatarSize + 2f, avatarSize + 2f, 5f * scale.get().floatValue(), withAlpha(new Color(25, 25, 27, 240), alpha));
            NanoVGHelper.drawRoundRectOutline(avatarX - 1f, avatarY - 1f, avatarSize + 2f, avatarSize + 2f, 5f * scale.get().floatValue(), 0.7f, withAlpha(new Color(65, 65, 68, 140), alpha));
        });

        if (target instanceof PlayerEntity player && mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
            if (entry != null && drawRoundedPlayerHead(entry, avatarX, avatarY, avatarSize, 4f * scale.get().floatValue(), alpha)) {
                if (target.hurtTime > 0) {
                    float hurt = MathHelper.clamp(target.hurtTime / 10f, 0f, 1f);
                    NanoVGRenderer.INSTANCE.draw(vg -> NanoVGHelper.drawRoundRect(avatarX, avatarY, avatarSize, avatarSize, 4f * scale.get().floatValue(), new Color(150, 45, 45, (int) (75 * hurt * alpha))));
                }
                return;
            }
        }

        NanoVGRenderer.INSTANCE.draw(vg -> {
            NanoVGHelper.drawRoundRect(avatarX, avatarY, avatarSize, avatarSize, 4f * scale.get().floatValue(), withAlpha(new Color(35, 35, 37, 255), alpha));
            String letter = target.getName().getString().isEmpty() ? "?" : target.getName().getString().substring(0, 1).toUpperCase();
            NanoVGHelper.drawCenteredString(letter, avatarX + avatarSize / 2f, avatarY + avatarSize / 2f + 0.5f * scale.get().floatValue(), FontLoader.medium(13f * scale.get().floatValue()), 13f * scale.get().floatValue(), withAlpha(new Color(218, 218, 220, 255), alpha));
        });

        if (target.hurtTime > 0) {
            float hurt = MathHelper.clamp(target.hurtTime / 10f, 0f, 1f);
            NanoVGRenderer.INSTANCE.draw(vg -> NanoVGHelper.drawRoundRect(avatarX, avatarY, avatarSize, avatarSize, 4f * scale.get().floatValue(), new Color(150, 45, 45, (int) (75 * hurt * alpha))));
        }
    }

    private boolean drawRoundedPlayerHead(PlayerListEntry entry, float avatarX, float avatarY, float avatarSize, float radius, float alpha) {
        if (entry.getSkinTextures() == null || entry.getSkinTextures().body() == null) return false;
        Identifier textureId = entry.getSkinTextures().body().texturePath();
        AbstractTexture texture = mc.getTextureManager().getTexture(textureId);
        if (!(texture.getGlTexture() instanceof GlTexture glTexture)) return false;

        int glId = glTexture.getGlId();
        if (glId <= 0) return false;
        int imageId = skinImages.computeIfAbsent(glId, id -> NanoVGHelper.createImageFromHandle(id, 64, 64));
        if (imageId == -1) return false;

        NanoVGRenderer.INSTANCE.draw(vg -> {
            drawSkinRegion(vg, imageId, 8f, 8f, 8f, 8f, avatarX, avatarY, avatarSize, radius, alpha);
            drawSkinRegion(vg, imageId, 40f, 8f, 8f, 8f, avatarX, avatarY, avatarSize, radius, alpha);
        });
        return true;
    }

    private void drawSkinRegion(long vg, int imageId, float srcX, float srcY, float srcW, float srcH, float x, float y, float size, float radius, float alpha) {
        float patternScale = size / srcW;
        NVGPaint paint = NVGPaint.create();
        NanoVG.nvgImagePattern(vg, x - srcX * patternScale, y - srcY * patternScale, 64f * patternScale, 64f * patternScale, 0f, imageId, MathHelper.clamp(alpha, 0f, 1f), paint);
        NanoVG.nvgBeginPath(vg);
        NanoVG.nvgRoundedRect(vg, x, y, size, size, radius);
        NanoVG.nvgFillPaint(vg, paint);
        NanoVG.nvgFill(vg);
    }

    private String trim(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, Math.max(0, max - 2)) + ".." : value;
    }

    private String fitText(String value, int font, float size, float maxWidth) {
        if (value == null || value.isEmpty()) return "";
        if (NanoVGHelper.getTextWidth(value, font, size) <= maxWidth) return value;
        String suffix = "..";
        float suffixWidth = NanoVGHelper.getTextWidth(suffix, font, size);
        int end = value.length();
        while (end > 0 && NanoVGHelper.getTextWidth(value.substring(0, end), font, size) + suffixWidth > maxWidth) {
            end--;
        }
        return end <= 0 ? suffix : value.substring(0, end) + suffix;
    }

    private String distanceText(LivingEntity target) {
        if (mc.player == null) return "player";
        return String.format("%.1fm", mc.player.distanceTo(target));
    }

    private Color withAlpha(Color color, float alpha) {
        int a = MathHelper.clamp((int) (color.getAlpha() * MathHelper.clamp(alpha, 0f, 1f)), 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), a);
    }

    private Color lift(Color color, int amount, int alpha) {
        return new Color(
                MathHelper.clamp(color.getRed() + amount, 0, 255),
                MathHelper.clamp(color.getGreen() + amount, 0, 255),
                MathHelper.clamp(color.getBlue() + amount, 0, 255),
                MathHelper.clamp(Math.min(color.getAlpha(), alpha), 0, 255)
        );
    }

    private Color darken(Color color, int amount) {
        return new Color(
                MathHelper.clamp(color.getRed() - amount, 0, 255),
                MathHelper.clamp(color.getGreen() - amount, 0, 255),
                MathHelper.clamp(color.getBlue() - amount, 0, 255),
                color.getAlpha()
        );
    }
}
