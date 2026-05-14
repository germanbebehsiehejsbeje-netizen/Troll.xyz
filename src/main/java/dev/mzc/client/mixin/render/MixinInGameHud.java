package dev.mzc.client.mixin.render;

import dev.mzc.client.Sakura;
import dev.mzc.client.events.render.Render2DEvent;
import dev.mzc.client.module.impl.hud.HotbarHud;
import dev.mzc.client.module.impl.hud.PotionHud;
import dev.mzc.client.module.impl.hud.ScoreboardHud;
import dev.mzc.client.module.impl.render.NoRender;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import dev.mzc.client.module.impl.misc.NameProtect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.Text;
import net.minecraft.entity.Entity;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(InGameHud.class)
public abstract class MixinInGameHud {
    @Shadow @Final private MinecraftClient client;
    @Shadow @org.jetbrains.annotations.Nullable public net.minecraft.text.Text title;
    @Shadow @org.jetbrains.annotations.Nullable public net.minecraft.text.Text subtitle;
    @Shadow public int titleFadeInTicks;
    @Shadow public int titleStayTicks;
    @Shadow public int titleFadeOutTicks;
    @Shadow public int titleRemainTicks;
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        Sakura.EVENT_BUS.post(new Render2DEvent(context));
    }

    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void onRenderHotbar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        HotbarHud hotbarHud = Sakura.MODULES.getModule(HotbarHud.class);
        if (hotbarHud != null && hotbarHud.isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderStatusEffectOverlay(CallbackInfo ci) {
        NoRender noRender = Sakura.MODULES.getModule(NoRender.class);
        if (noRender.noPotionIcons()) ci.cancel();
        PotionHud potionHud = Sakura.MODULES.getModule(PotionHud.class);
        if (potionHud != null && potionHud.isEnabled()) ci.cancel();
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderPortalOverlay(DrawContext context, float nauseaStrength, CallbackInfo ci) {
        NoRender noRender = Sakura.MODULES.getModule(NoRender.class);
        if (noRender.noPortalOverlay()) ci.cancel();
    }

    @ModifyArgs(method = "renderMiscOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderOverlay(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/util/Identifier;F)V", ordinal = 0))
    private void onRenderPumpkinOverlay(Args args) {
        NoRender noRender = Sakura.MODULES.getModule(NoRender.class);
        if (noRender.noPumpkinOverlay()) args.set(2, 0f);
    }

    @ModifyArgs(method = "renderMiscOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderOverlay(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/util/Identifier;F)V", ordinal = 1))
    private void onRenderPowderedSnowOverlay(Args args) {
        NoRender noRender = Sakura.MODULES.getModule(NoRender.class);
        if (noRender.noPowderedSnowOverlay()) args.set(2, 0f);
    }

    @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderVignetteOverlay(DrawContext context, Entity entity, CallbackInfo ci) {
        NoRender noRender = Sakura.MODULES.getModule(NoRender.class);
        if (noRender.noVignette()) ci.cancel();
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("HEAD"), cancellable = true)
    private void onRenderScoreboardSidebar(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
        NoRender noRender = Sakura.MODULES.getModule(NoRender.class);
        if (noRender.noScoreboard()) {
            ci.cancel();
            return;
        }
        ScoreboardHud scoreboardHud = Sakura.MODULES.getModule(ScoreboardHud.class);
        if (scoreboardHud != null && scoreboardHud.isEnabled()) {
            ci.cancel();
            return;
        }

        // NameProtect 处理
        NameProtect nameProtect = Sakura.MODULES.getModule(NameProtect.class);
        if (nameProtect != null && nameProtect.isEnabled()) {
            Sakura.LOGGER.info("Scoreboard Mixin Called: " + objective.getDisplayName().getString());
            ci.cancel();
            renderProtectedScoreboard(context, objective);
        }
    }

    @Unique
    private void renderProtectedScoreboard(DrawContext context, ScoreboardObjective objective) {
        net.minecraft.scoreboard.Scoreboard scoreboard = objective.getScoreboard();
        java.util.Collection<ScoreboardEntry> collection = scoreboard.getScoreboardEntries(objective);
        java.util.List<ScoreboardEntry> list = collection.stream()
                .filter(entry -> !entry.hidden())
                .sorted(java.util.Comparator.comparingInt(ScoreboardEntry::value).reversed())
                .limit(15)
                .collect(java.util.stream.Collectors.toList());
        
        if (list.isEmpty()) return;

        net.minecraft.client.font.TextRenderer textRenderer = this.client.textRenderer;
        int i = 0;
        
        // 处理标题
        Text titleText = NameProtect.getGradientReplacement(objective.getDisplayName());
        int titleWidth = textRenderer.getWidth(titleText);

        for (ScoreboardEntry entry : list) {
            Text name = NameProtect.getGradientReplacement(entry.name());
            i = Math.max(i, textRenderer.getWidth(name) + 20);
        }

        int j = list.size();
        int k = j * 9;
        int l = context.getScaledWindowHeight() / 2 + k / 3;
        int n = context.getScaledWindowWidth() - i - 3;
        int o = context.getScaledWindowWidth() - 3;
        int p = 0;

        NumberFormat numberFormat = objective.getNumberFormatOr(StyledNumberFormat.RED);

        for (ScoreboardEntry entry : list) {
            ++p;
            Text text = NameProtect.getGradientReplacement(entry.name());
            Text text2 = entry.formatted(numberFormat);
            int q = l - p * 9;
            context.fill(n - 2, q, o, q + 9, this.client.options.getTextBackgroundColor(0.3F));
            context.drawText(textRenderer, text, n, q, -1, false);
            context.drawText(textRenderer, text2, o - textRenderer.getWidth(text2), q, -1, false);
            if (p == j) {
                context.fill(n - 2, q - 9 - 1, o, q - 1, this.client.options.getTextBackgroundColor(0.4F));
                context.fill(n - 2, q - 1, o, q, this.client.options.getTextBackgroundColor(0.3F));
                context.drawText(textRenderer, titleText, n + i / 2 - titleWidth / 2, q - 9, -1, false);
            }
        }
    }

    @Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderSpyglassOverlay(DrawContext context, float scale, CallbackInfo ci) {
        NoRender noRender = Sakura.MODULES.getModule(NoRender.class);
        if (noRender.noSpyglassOverlay()) ci.cancel();
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void onRenderCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        NoRender noRender = Sakura.MODULES.getModule(NoRender.class);
        if (noRender.noCrosshair()) ci.cancel();
    }

    @Inject(method = "renderTitleAndSubtitle", at = @At("HEAD"), cancellable = true)
    private void onRenderTitle(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        NoRender noRender = Sakura.MODULES.getModule(NoRender.class);
        if (noRender.noTitle()) {
            ci.cancel();
            return;
        }

        if (dev.mzc.client.module.impl.client.ClickGui.globalFontReplacement.get()) {
            ci.cancel();

            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            int width = context.getScaledWindowWidth();
            int height = context.getScaledWindowHeight();
            int alpha = 255;

            if (this.titleFadeInTicks + this.titleStayTicks + this.titleFadeOutTicks > 0) {
                if (this.titleRemainTicks > this.titleFadeOutTicks + this.titleStayTicks) {
                    float f = (float) (this.titleFadeInTicks + this.titleStayTicks + this.titleFadeOutTicks - this.titleRemainTicks) / (float) this.titleFadeInTicks;
                    alpha = (int) (f * 255.0F);
                } else if (this.titleRemainTicks <= this.titleFadeOutTicks) {
                    float f = (float) this.titleRemainTicks / (float) this.titleFadeOutTicks;
                    alpha = (int) (f * 255.0F);
                }
            }

            alpha = net.minecraft.util.math.MathHelper.clamp(alpha, 0, 255);
            if (alpha <= 8) return;

            if (this.title != null) {
                float titleScale = 3.0f;
                context.getMatrices().pushMatrix();
                context.getMatrices().translate(width / 2.0f, height / 2.0f - 40.0f);
                context.getMatrices().scale(titleScale, titleScale);
                int finalColor = 0xFFFFFF | (alpha << 24);
                context.drawCenteredTextWithShadow(mc.textRenderer, this.title, 0, 0, finalColor);
                context.getMatrices().popMatrix();
            }

            if (this.subtitle != null) {
                float subtitleScale = 1.5f;
                context.getMatrices().pushMatrix();
                context.getMatrices().translate(width / 2.0f, height / 2.0f + 10.0f);
                context.getMatrices().scale(subtitleScale, subtitleScale);
                int finalColor = 0xFFFFFF | (alpha << 24);
                context.drawCenteredTextWithShadow(mc.textRenderer, this.subtitle, 0, 0, finalColor);
                context.getMatrices().popMatrix();
            }
        }
    }

    @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"), cancellable = true)
    private void onRenderHeldItemTooltip(DrawContext context, CallbackInfo ci) {
        NoRender noRender = Sakura.MODULES.getModule(NoRender.class);
        if (noRender.noHeldItemName()) ci.cancel();
    }

    @Inject(method = "renderStatusBars", at = @At("HEAD"), cancellable = true)
    private void onRenderStatusBars(DrawContext context, CallbackInfo ci) {
        HotbarHud hotbarHud = Sakura.MODULES.getModule(HotbarHud.class);
        if (hotbarHud != null && hotbarHud.isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderNauseaOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderNausea(DrawContext context, float distortionStrength, CallbackInfo ci) {
        NoRender noRender = Sakura.MODULES.getModule(NoRender.class);
        if (noRender.noNausea()) ci.cancel();
    }
}
