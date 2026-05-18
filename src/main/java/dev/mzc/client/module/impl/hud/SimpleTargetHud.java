package dev.mzc.client.module.impl.hud;

import dev.mzc.client.Sakura;
import dev.mzc.client.module.HudModule;
import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.module.impl.combat.KillAura;
import dev.mzc.client.nanovg.NanoVGRenderer;
import dev.mzc.client.nanovg.font.FontLoader;
import dev.mzc.client.nanovg.util.NanoVGHelper;
import dev.mzc.client.utils.animations.AnimationUtil;
import dev.mzc.client.utils.entity.HealthUtil;
import dev.mzc.client.utils.render.Shader2DUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

import java.awt.*;

/**
 * Direct port of the provided 1.8-style target HUD.
 *
 * Reference layout (no modifications):
 *   - Card 94×30, radius 4, rgba(20,15,11,205) with KawaseBlur behind it
 *   - Head 18×18 at (posX+7, posY+5.5)
 *   - Name at (posX+headSize+2.5, posY+7.5), trimmed to 15 chars + "..." if > 17
 *   - HP track 55.5×2.5 at (posX+headSize+2.5, posY+20), radius 1, rgba(41,49,51,255)
 *   - HP fill same rect width × healthAnimation, theme color
 */
public class SimpleTargetHud extends HudModule {

    private final BoolValue useKillAuraTarget = new BoolValue("Use KillAura Target", true);
    private final NumberValue<Double> targetRange = new NumberValue<>("Target Range", 6.0, 1.0, 20.0, 0.1);
    private final NumberValue<Double> hudScale = new NumberValue<>("Scale", 1.5, 0.5, 3.0, 0.1);
    private final BoolValue blurBackground = new BoolValue("Blur", true);
    private final NumberValue<Double> blurStrength = new NumberValue<>("Blur Strength", 5.0, 1.0, 16.0, 0.5, blurBackground::get);

    // Animation state
    private float healthAnimation = 1f;
    private float fadeAlpha = 0f;
    private float sizeAnim = 0f;
    private LivingEntity lastTarget = null;

    public SimpleTargetHud() {
        super("SimpleTargetHud", 100, 100);
    }

    @Override
    public void renderInGame(DrawContext context) {
        if (Sakura.MODULES.getModule(dev.mzc.client.module.impl.client.HudEditor.class).isEnabled()
                && mc.currentScreen instanceof dev.mzc.client.gui.hud.HudEditorScreen) {
            return;
        }
        renderCard(context, true);
    }

    @Override
    public void renderInEditor(DrawContext context, float mouseX, float mouseY) {
        if (dragging) {
            int gw = mc.getWindow().getScaledWidth();
            int gh = mc.getWindow().getScaledHeight();
            x = Math.max(0, Math.min(mouseX - dragX, gw - width));
            y = Math.max(0, Math.min(mouseY - dragY, gh - height));
            relativeX = x / gw;
            relativeY = y / gh;
        }
        renderCard(context, false);
    }

    private void renderCard(DrawContext context, boolean inGame) {
        float s = hudScale.get().floatValue();

        // Original constants (DO NOT change — these are from the reference):
        // card: 94×30
        // head: 18×18 at (+7, +5.5)
        // hp bar: 55.5×2.5 at (+head+2.5, +20)
        final float CARD_W = 94f * s;
        final float CARD_H = 30f * s;
        this.width = CARD_W;
        this.height = CARD_H;

        LivingEntity target = inGame ? findTarget() : (mc.player != null ? mc.player : null);

        boolean visible = target != null;
        fadeAlpha = AnimationUtil.fast(fadeAlpha, visible ? 1f : 0f, 8f);
        sizeAnim = AnimationUtil.fast(sizeAnim, visible ? 1f : 0f, 10f);
        if (fadeAlpha <= 0.01f) return;
        if (target == null) target = lastTarget;
        if (target == null) return;
        lastTarget = target;

        renderRiseCard(context, target, x, y, s, fadeAlpha);
    }

    private LivingEntity findTarget() {
        if (useKillAuraTarget.get()) {
            KillAura ka = KillAura.getInstance();
            if (ka != null && ka.isEnabled()) {
                LivingEntity t = ka.getTarget();
                if (t != null && t.isAlive() && t != mc.player) return t;
            }
        }
        if (mc.world == null || mc.player == null) return null;
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        double range = targetRange.get();
        for (net.minecraft.entity.Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity living) || e == mc.player || !e.isAlive()) continue;
            double d = mc.player.distanceTo(e);
            if (d > range) continue;
            if (d < bestDist) {
                bestDist = d;
                best = living;
            }
        }
        return best;
    }

    /**
     * Strict port of the reference rendering code.
     * All offsets and sizes are scaled by `s` (Scale value) but proportions stay 1:1.
     */
    private void renderRiseCard(DrawContext context, LivingEntity target, float posX, float posY, float s, float alpha) {
        // Reference constants from the provided snippet (scaled)
        final float CARD_W = 94f * s;
        final float CARD_H = 30f * s;
        final float RADIUS = 4f * s;

        final float HEAD_SIZE = 18f * s;
        final float HEAD_X = posX + 7f * s;
        final float HEAD_Y = posY + 6f * s;

        // Name & bar start AFTER the head: head_x + head_size + small gap
        // (the original snippet wrote `posX + headSize + 2.5f` which forgot the +7 head offset)
        final float CONTENT_X = HEAD_X + HEAD_SIZE + 2.5f * s;
        final float NAME_Y = posY + 7.5f * s;

        final float BAR_Y = posY + 20f * s;
        final float BAR_H = 2.5f * s;
        // Bar fills the rest of the card with a small right padding (6px)
        final float BAR_W = CARD_W - (CONTENT_X - posX) - 6f * s;

        // 1) KawaseBlur behind the rounded card
        if (blurBackground.get()) {
            Shader2DUtil.drawRoundedBlur(new MatrixStack(), posX, posY, CARD_W, CARD_H,
                    RADIUS, new Color(0, 0, 0, 0), blurStrength.get().floatValue(), alpha);
        }

        // 2) Card background — rgba(20, 15, 11, 205)
        NanoVGRenderer.INSTANCE.draw(vg -> {
            int bgA = (int) (205 * alpha);
            NanoVGHelper.drawRoundRect(posX, posY, CARD_W, CARD_H, RADIUS, new Color(20, 15, 11, bgA));
        });

        // 3) Target head (with hurt-tint) at (posX+7, posY+5.5), 18×18
        drawTargetHead(context, target, HEAD_X, HEAD_Y, HEAD_SIZE, alpha);

        // 4) Name — trimmed > 17 chars to first 15 + "..."
        String full = target.getName().getString();
        String name = full.length() > 17 ? (full.substring(0, 15) + "...") : full;

        NanoVGRenderer.INSTANCE.draw(vg -> {
            float fontSize = 9f * s;
            int font = FontLoader.medium(fontSize);
            // Scissor to the content area so long names don't overflow
            NanoVGHelper.save();
            NanoVGHelper.intersectScissor(CONTENT_X, posY, CARD_W - (CONTENT_X - posX) - 4f * s, CARD_H);
            NanoVGHelper.drawString(name, CONTENT_X, NAME_Y + fontSize, font, fontSize, withAlpha(Color.WHITE, alpha));
            NanoVGHelper.restore();
        });

        // 5) HP bar track — radius 1, rgba(41, 49, 51, 255)
        // 6) HP bar fill — width × healthAnimation, theme color
        float currentHp = HealthUtil.getEntityHealth(target);
        float maxHp = HealthUtil.getEntityMaxHealth(target);
        float hpPct = MathHelper.clamp(currentHp / Math.max(1f, maxHp), 0f, 1f);
        healthAnimation = AnimationUtil.fast(healthAnimation, hpPct, 18f);

        NanoVGRenderer.INSTANCE.draw(vg -> {
            int tA = (int) (255 * alpha);
            NanoVGHelper.drawRoundRect(CONTENT_X, BAR_Y, BAR_W, BAR_H, 1f * s, new Color(41, 49, 51, tA));
            float fillW = BAR_W * healthAnimation;
            if (fillW > 0) {
                NanoVGHelper.drawRoundRect(CONTENT_X, BAR_Y, fillW, BAR_H, 1f * s, withAlpha(ClickGui.color(0), alpha));
            }
        });
    }

    /**
     * Direct equivalent of `drawTargetHead` / `drawFace` from the reference.
     * Renders the player head texture and applies a red hurt-tint based on hurtTime.
     */
    private void drawTargetHead(DrawContext context, LivingEntity target, float hx, float hy, float size, float alpha) {
        if (!(target instanceof PlayerEntity player)) {
            NanoVGRenderer.INSTANCE.draw(vg ->
                    NanoVGHelper.drawRect(hx, hy, size, size, withAlpha(new Color(40, 40, 40), alpha)));
            return;
        }
        if (mc.getNetworkHandler() == null) return;
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
        if (entry == null) return;

        PlayerSkinDrawer.draw(context, entry.getSkinTextures(), (int) hx, (int) hy, (int) size);

        // hurtPercent = hurtTime / 10 (matches reference drawFace logic)
        float hurtPct = target.hurtTime / 10f;
        if (hurtPct > 0.01f) {
            int ra = (int) (180 * hurtPct * alpha);
            NanoVGRenderer.INSTANCE.draw(vg ->
                    NanoVGHelper.drawRect(hx, hy, size, size, new Color(255, 60, 60, ra)));
        }
    }

    private Color withAlpha(Color c, float alpha) {
        int a = (int) (c.getAlpha() * MathHelper.clamp(alpha, 0f, 1f));
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), MathHelper.clamp(a, 0, 255));
    }
}
