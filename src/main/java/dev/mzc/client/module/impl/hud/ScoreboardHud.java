package dev.mzc.client.module.impl.hud;

import dev.mzc.client.module.HudModule;
import dev.mzc.client.nanovg.NanoVGRenderer;
import dev.mzc.client.nanovg.util.NanoVGHelper;
import dev.mzc.client.utils.render.Shader2DUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.ColorValue;
import dev.mzc.client.values.impl.NumberValue;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ScoreboardHud extends HudModule {
    private final BoolValue blur = new BoolValue("Blur", true);
    private final NumberValue<Double> blurStrength = new NumberValue<>("BlurStrength", 8.0, 1.0, 20.0, 0.5, blur::get);
    private final BoolValue bloom = new BoolValue("Bloom", false);
    private final NumberValue<Double> radius = new NumberValue<>("Radius", 4.0, 0.0, 12.0, 0.5);
    private final NumberValue<Double> scale = new NumberValue<>("Scale", 1.0, 0.5, 2.0, 0.05);
    private final NumberValue<Double> padding = new NumberValue<>("Padding", 4.0, 2.0, 10.0, 0.5);
    private final NumberValue<Double> rowHeight = new NumberValue<>("RowHeight", 9.0, 7.0, 14.0, 0.5);
    private final NumberValue<Integer> maxEntries = new NumberValue<>("MaxEntries", 15, 3, 20, 1);
    private final BoolValue showTitle = new BoolValue("ShowTitle", true);
    private final BoolValue showScore = new BoolValue("ShowScore", true);
    private final BoolValue textShadow = new BoolValue("TextShadow", false);
    private final ColorValue backgroundColor = new ColorValue("Background", new Color(0, 0, 0, 102));

    public ScoreboardHud() {
        super("ScoreboardHud", 8, 120);
        this.width = 120;
        this.height = 70;
    }

    @Override
    public void onRender(DrawContext context) {
        if (mc.world == null) {
            return;
        }

        ScoreboardObjective objective = getSidebarObjective(mc.world.getScoreboard());
        if (objective == null) {
            return;
        }

        List<SidebarLine> lines = collectLines(objective);
        if (lines.isEmpty() && !showTitle.get()) {
            return;
        }

        float uiScale = scale.get().floatValue();
        float pad = padding.get().floatValue();
        float rowH = rowHeight.get().floatValue();

        int titleWidth = showTitle.get() ? mc.textRenderer.getWidth(objective.getDisplayName()) : 0;
        int contentWidth = titleWidth;

        for (SidebarLine line : lines) {
            int lineWidth = mc.textRenderer.getWidth(line.name);
            if (showScore.get() && line.scoreWidth > 0) {
                lineWidth += 2 + line.scoreWidth;
            }
            contentWidth = Math.max(contentWidth, lineWidth);
        }

        float baseWidth = pad * 2.0f + contentWidth;
        int rowCount = lines.size() + (showTitle.get() ? 1 : 0);
        float baseHeight = pad * 2.0f + rowCount * rowH;

        this.width = baseWidth * uiScale;
        this.height = baseHeight * uiScale;

        if (blur.get()) {
            Shader2DUtil.drawRoundedBlur(new MatrixStack(), x, y, width, height,
                    radius.get().floatValue(), new Color(0, 0, 0, 0), blurStrength.get().floatValue(), 1.0f);
        }

        Color bg = backgroundColor.get();
        NanoVGRenderer.INSTANCE.draw(vg -> {
            if (bloom.get()) {
                NanoVGHelper.drawRoundRectBloom(x, y, width, height, radius.get().floatValue(), bg);
            } else {
                NanoVGHelper.drawRoundRect(x, y, width, height, radius.get().floatValue(), bg);
            }
        });

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(uiScale, uiScale);

        float currentY = pad;

        if (showTitle.get()) {
            int centeredX = (int) (pad + (contentWidth - titleWidth) * 0.5f);
            context.drawText(mc.textRenderer, objective.getDisplayName(), centeredX, (int) currentY,
                    Colors.WHITE, textShadow.get());
            currentY += rowH;
        }

        for (SidebarLine line : lines) {
            context.drawText(mc.textRenderer, line.name, (int) pad, (int) currentY, Colors.WHITE, textShadow.get());

            if (showScore.get() && line.scoreWidth > 0) {
                int scoreX = (int) (pad + contentWidth - line.scoreWidth);
                context.drawText(mc.textRenderer, line.score, scoreX, (int) currentY, Colors.WHITE, textShadow.get());
            }

            currentY += rowH;
        }

        context.getMatrices().popMatrix();
    }

    private ScoreboardObjective getSidebarObjective(Scoreboard scoreboard) {
        return scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
    }

    private List<SidebarLine> collectLines(ScoreboardObjective objective) {
        Scoreboard scoreboard = objective.getScoreboard();
        NumberFormat numberFormat = objective.getNumberFormatOr(StyledNumberFormat.RED);

        return scoreboard.getScoreboardEntries(objective)
                .stream()
                .filter(entry -> !entry.hidden())
                .sorted(Comparator.comparingInt(ScoreboardEntry::value).reversed())
                .limit(maxEntries.get())
                .map(entry -> {
                    Team team = scoreboard.getScoreHolderTeam(entry.owner());
                    Text decoratedName = Team.decorateName(team, entry.name());
                    Text scoreText = showScore.get() ? entry.formatted(numberFormat) : Text.empty();
                    int scoreW = showScore.get() ? mc.textRenderer.getWidth(scoreText) : 0;
                    return new SidebarLine(decoratedName, scoreText, scoreW);
                })
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    private static class SidebarLine {
        final Text name;
        final Text score;
        final int scoreWidth;

        SidebarLine(Text name, Text score, int scoreWidth) {
            this.name = name;
            this.score = score;
            this.scoreWidth = scoreWidth;
        }
    }
}
