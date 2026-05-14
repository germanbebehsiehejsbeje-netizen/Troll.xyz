package dev.mzc.client.module.impl.misc.cheatdetector.checks.movement;

import dev.mzc.client.module.impl.misc.cheatdetector.CheatCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.CheatDetectorContext;
import dev.mzc.client.module.impl.misc.cheatdetector.TrackedPlayer;
import dev.mzc.client.values.impl.BoolValue;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FlyACheck implements CheatCheck {
    private final BoolValue enabled;
    private final Map<UUID, Integer> yZeroStreakByPlayer = new HashMap<>();

    public FlyACheck(BoolValue enabled) {
        this.enabled = enabled;
    }

    @Override
    public void onTick(PlayerEntity player, TrackedPlayer tp, CheatDetectorContext ctx) {
        if (!enabled.get()) return;
        if (!tp.hasPosDelta()) return;

        if (player.hasVehicle() || player.isGliding() || player.isTouchingWater() || player.isInLava()) {
            yZeroStreakByPlayer.remove(player.getUuid());
            return;
        }

        if (tp.currentOnGround()) {
            yZeroStreakByPlayer.remove(player.getUuid());
            return;
        }

        double lastY = tp.lastTickMotion().y;
        double curY = tp.currentTickMotion().y;
        boolean yZero = Math.abs(lastY) < 1.0e-6 && Math.abs(curY) < 1.0e-6;
        if (!yZero) {
            yZeroStreakByPlayer.remove(player.getUuid());
            return;
        }

        int streak = yZeroStreakByPlayer.getOrDefault(player.getUuid(), 0) + 1;
        yZeroStreakByPlayer.put(player.getUuid(), streak);
        if (streak >= 6) {
            ctx.notify(player, String.format("FlyA (Invalid Y-motion: %.2f onGround=%s)", curY, tp.currentOnGround()));
            yZeroStreakByPlayer.put(player.getUuid(), 0);
        }
    }
}
