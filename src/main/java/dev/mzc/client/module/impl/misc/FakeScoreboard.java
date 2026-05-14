package dev.mzc.client.module.impl.misc;

import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.NumberValue;
import dev.mzc.client.values.impl.StringValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class FakeScoreboard extends Module {
    private static final String SCOREBOARD_NAME = "mzc_fake";

    private final StringValue title = new StringValue("Title");
    private final StringValue money = new StringValue("Money");
    private final StringValue shards = new StringValue("Shards");
    private final StringValue kills = new StringValue("Kills");
    private final StringValue deaths = new StringValue("Deaths");
    private final StringValue playtime = new StringValue("Playtime");
    private final StringValue team = new StringValue("Team");
    private final StringValue footer = new StringValue("Footer");
    private final NumberValue<Integer> keyallSeconds = new NumberValue<>("KeyallSeconds", 3599, 0, 7200, 1);

    private final List<ScoreHolder> holders = new ArrayList<>();
    private ScoreboardObjective customObjective;
    private ScoreboardObjective originalObjective;
    private long keyallStartTime;
    private long lastMsUpdate;
    private int displayMs;
    private int msChangeDirection;
    private long lastScoreboardUpdate;

    public FakeScoreboard() {
        super("FakeScoreboard", Category.Misc);
        this.setType(ModuleType.Safe);
    }

    @Override
    protected void onEnable() {
        if (mc.world == null) return;

        Scoreboard scoreboard = mc.world.getScoreboard();
        originalObjective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);

        keyallStartTime = System.currentTimeMillis();
        lastMsUpdate = System.currentTimeMillis();
        lastScoreboardUpdate = 0L;
        displayMs = 50 + (int) (Math.random() * 50.0);
        msChangeDirection = Math.random() < 0.5 ? 1 : -1;

        updateScoreboard();
    }

    @Override
    protected void onDisable() {
        if (mc.world == null) return;
        Scoreboard scoreboard = mc.world.getScoreboard();
        cleanupHolders(scoreboard);

        if (customObjective != null) {
            try {
                scoreboard.removeObjective(customObjective);
            } catch (Exception ignored) {
            }
            customObjective = null;
        }

        try {
            scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, originalObjective);
        } catch (Exception ignored) {
            scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, null);
        }
        originalObjective = null;
    }

    @EventHandler
    public void onTick(TickEvent.Post event) {
        if (!isEnabled()) return;
        if (mc.world == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        if (now - lastScoreboardUpdate >= 1000L) {
            updateScoreboard();
            lastScoreboardUpdate = now;
        }
    }

    public void updateScoreboard() {
        if (mc.world == null || mc.player == null) return;

        Scoreboard scoreboard = mc.world.getScoreboard();
        cleanupHolders(scoreboard);

        if (customObjective != null) {
            try {
                scoreboard.removeObjective(customObjective);
            } catch (Exception ignored) {
            }
        }

        customObjective = scoreboard.addObjective(
            SCOREBOARD_NAME,
            ScoreboardCriterion.DUMMY,
            gradientTitle(title.get()),
            ScoreboardCriterion.RenderType.INTEGER,
            false,
            null
        );

        scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, customObjective);

        List<Text> entries = generateEntriesText();
        for (int i = 0; i < entries.size(); i++) {
            ScoreHolder holder = ScoreHolder.fromName("mzc_fake_entry_" + i);
            holders.add(holder);

            var score = scoreboard.getOrCreateScore(holder, customObjective);
            score.setDisplayText(entries.get(i));
            score.setScore(entries.size() - i);
        }
    }

    private void cleanupHolders(Scoreboard scoreboard) {
        for (int i = 0; i < holders.size(); i++) {
            try {
                scoreboard.removeScores(holders.get(i));
            } catch (Exception ignored) {
            }
        }
        holders.clear();
    }

    private String getKeyallTimer() {
        long elapsed = (System.currentTimeMillis() - keyallStartTime) / 1000L;
        long remaining = Math.max(0L, (long) keyallSeconds.get() - elapsed);
        long minutes = remaining / 60L;
        long seconds = remaining % 60L;
        return String.format("%dm %ds", minutes, seconds);
    }

    private String getFooterWithMs() {
        long now = System.currentTimeMillis();
        if (now - lastMsUpdate > 2000L + (long) (Math.random() * 2000.0)) {
            int delta = 1 + (int) (Math.random() * 5.0);
            displayMs += msChangeDirection * delta;

            if (displayMs < 20) {
                displayMs = 20;
                msChangeDirection = 1;
            } else if (displayMs > 150) {
                displayMs = 150;
                msChangeDirection = -1;
            }

            if (Math.random() < 0.1) {
                msChangeDirection *= -1;
            }

            lastMsUpdate = now;
        }

        String s = footer.get();
        int l = s.indexOf('(');
        int r = s.indexOf(')');
        if (l == -1 || r == -1 || r <= l) return s;

        String head = s.substring(0, l).trim();
        return head + displayMs + "ms";
    }

    private List<Text> generateEntriesText() {
        MutableText blank = Text.literal("");

        MutableText moneyLine = colored("$", 0x00FF00)
            .append(colored(" Money:", 0xFFFFFF))
            .append(colored(money.get(), 0x00FF00));

        MutableText shardsLine = colored("★", 0xA50ABC)
            .append(colored(" Shards:", 0xFFFFFF))
            .append(colored(shards.get(), 0xA50ABC));

        MutableText killsLine = colored("?", 0xFF0000)
            .append(colored(" Kills:", 0xFFFFFF))
            .append(colored(kills.get(), 0xFF0000));

        MutableText deathsLine = colored("?", 0xFC7E73)
            .append(colored(" Deaths:", 0xFFFFFF))
            .append(colored(deaths.get(), 0xFC7E73));

        MutableText keyallLine = colored("?", 0x00A2FF)
            .append(colored(" Keyall:", 0xFFFFFF))
            .append(colored(getKeyallTimer(), 0x00A2FF));

        MutableText playtimeLine = colored("?", 0xFFF000)
            .append(colored(" Playtime:", 0xFFFFFF))
            .append(colored(playtime.get(), 0xFFF000));

        MutableText teamLine = colored("?", 0x00A2FF)
            .append(colored(" Team:", 0xFFFFFF))
            .append(colored(team.get(), 0x00A2FF));

        Text footerText = footerText();

        return List.of(blank, moneyLine, shardsLine, killsLine, deathsLine, keyallLine, playtimeLine, teamLine, blank, footerText);
    }

    private Text footerText() {
        String s = getFooterWithMs();
        int l = s.indexOf('(');
        int r = s.indexOf(')');
        if (l == -1 || r == -1 || r <= l) {
            return colored(s, 0xA0A0A0);
        }

        String head = s.substring(0, l).trim();
        String mid = s.substring(l + 1, r).trim();
        return colored(head, 0xA0A0A0)
            .append(colored("(", 0xA0A0A0))
            .append(colored(mid, 0x00A2FF))
            .append(colored(")", 0xA0A0A0));
    }

    private MutableText colored(String s, int rgb) {
        return Text.literal(s).setStyle(Style.EMPTY.withColor(rgb));
    }

    private Text gradientTitle(String s) {
        return gradient(s, 0x007CF9, 0x00C6F9);
    }

    private MutableText gradient(String s, int startRgb, int endRgb) {
        int sr = (startRgb >> 16) & 255;
        int sg = (startRgb >> 8) & 255;
        int sb = startRgb & 255;

        int er = (endRgb >> 16) & 255;
        int eg = (endRgb >> 8) & 255;
        int eb = endRgb & 255;

        MutableText out = Text.empty();
        int n = Math.max(1, s.length());
        for (int i = 0; i < s.length(); i++) {
            float t = (float) i / (float) n;
            int r = (int) (sr + (er - sr) * t);
            int g = (int) (sg + (eg - sg) * t);
            int b = (int) (sb + (eb - sb) * t);
            int rgb = (r << 16) | (g << 8) | b;
            out.append(Text.literal(String.valueOf(s.charAt(i))).setStyle(Style.EMPTY.withColor(rgb).withFormatting(Formatting.BOLD)));
        }
        return out;
    }
}
