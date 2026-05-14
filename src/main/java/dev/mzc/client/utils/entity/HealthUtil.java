package dev.mzc.client.utils.entity;

import dev.mzc.client.Sakura;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;


public final class HealthUtil {
    public enum HealthBypassMode {
        Auto,
        Vanilla,
        Hoplite,
        Scoreboard
    }

    private static HealthBypassMode mode = HealthBypassMode.Auto;

    private HealthUtil() {
    }

    public static void setMode(HealthBypassMode mode) {
        if (mode != null) {
            HealthUtil.mode = mode;
        }
    }

    public static HealthBypassMode getMode() {
        return mode;
    }

    public static Integer getScore(PlayerEntity player) {
        if (player == null || Sakura.mc.world == null) return 0;

        Scoreboard scoreboard = Sakura.mc.world.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.LIST);
        if (objective == null) return 0;

        ReadableScoreboardScore score = scoreboard.getScore(
                ScoreHolder.fromName(player.getGameProfile().name()),
                objective
        );
        if (score == null) return 0;
        return score.getScore();
    }

    public static String getCurrentServerIP() {
        if (Sakura.mc.isInSingleplayer()) return "Singleplayer";
        ServerInfo server = Sakura.mc.getCurrentServerEntry();
        if (server != null) return server.address == null ? "Unknown" : server.address.toLowerCase();
        return "Unknown";
    }

    public static float getEntityHealth(LivingEntity ent) {
        if (ent == null) return 0.0f;
        if (!(ent instanceof PlayerEntity p)) {
            return ent.getHealth() + ent.getAbsorptionAmount();
        }

        // 手动指定 Hoplite 或 Auto 检测到 hoplite 服务器：用 LIST 分数
        if (shouldUseHopliteMode()) {
            return (float) getScore(p);
        }

        // 手动指定 Scoreboard 或 Auto 检测到 funtime 服务器：用 BELOW_NAME 分数
        if (shouldUseScoreboardMode()) {
            return getBelowNameScore(p);
        }

        // 其余情况（Auto 回退 / Vanilla）：比较 entityHealth 和计分板分数
        float entityHealth = ent.getHealth() + ent.getAbsorptionAmount();
        float belowNameScore = getBelowNameScore(p);

        // 计分板全为 0，说明服务器没有挂载计分板血量，直接用 entityHealth
        if (belowNameScore == 0.0f) {
            return entityHealth;
        }

        // 两者相同直接返回
        if (Math.abs(entityHealth - belowNameScore) < 0.01f) {
            return entityHealth;
        }

        // 不同时优先用计分板分数
        return belowNameScore;
    }

    private static float getBelowNameScore(PlayerEntity p) {
        if (p == null || Sakura.mc.world == null) return 0.0f;

        Scoreboard sb = p.getEntityWorld().getScoreboard();
        ScoreboardObjective obj = sb.getObjectiveForSlot(ScoreboardDisplaySlot.BELOW_NAME);
        if (obj == null) return 0.0f;

        ReadableScoreboardScore score = sb.getScore(
                ScoreHolder.fromName(p.getGameProfile().name()),
                obj
        );
        if (score == null) return 0.0f;

        return (float) score.getScore();
    }

    public static float getEntityMaxHealth(LivingEntity ent) {
        if (ent == null) return 1.0f;
        if (!(ent instanceof PlayerEntity p)) return ent.getMaxHealth() + ent.getAbsorptionAmount();

        float hp = getEntityHealth(p);
        if (shouldUseHopliteMode() || shouldUseScoreboardMode()) {
            return Math.max(1.0f, hp);
        }
        return p.getMaxHealth() + p.getAbsorptionAmount();
    }

    private static boolean shouldUseHopliteMode() {
        if (mode == HealthBypassMode.Hoplite) return true;
        if (mode != HealthBypassMode.Auto) return false;

        return isServer("hoplite");
    }

    private static boolean shouldUseScoreboardMode() {
        if (mode == HealthBypassMode.Scoreboard) return true;
        if (mode != HealthBypassMode.Auto) return false;

        return isServer("funtime");
    }

    private static boolean isServer(String keyword) {
        if (Sakura.mc.getNetworkHandler() == null || Sakura.mc.getNetworkHandler().getServerInfo() == null) return false;
        return getCurrentServerIP().contains(keyword);
    }
}
