package dev.mzc.client.module.impl.misc.cheatdetector.checks.movement;

import dev.mzc.client.module.impl.misc.cheatdetector.CheatCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.CheatDetectorContext;
import dev.mzc.client.module.impl.misc.cheatdetector.TrackedPlayer;
import dev.mzc.client.values.impl.BoolValue;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NoSlowACheck implements CheatCheck {
    private static final double[] SLOW_SPEED_TICK = {
            2.56 / 20.0,
            1.92 / 20.0,
            1.6 / 20.0,
            1.4 / 20.0,
            1.36 / 20.0,
            1.26 / 20.0,
            1.18 / 20.0,
            1.16 / 20.0
    };

    private static final int IN_JUMP_DISABLE_TICKS = 4;
    private static final double THRESHOLD = 0.08;

    private final BoolValue enabled;
    private final Map<UUID, State> stateByPlayer = new HashMap<>();

    public NoSlowACheck(BoolValue enabled) {
        this.enabled = enabled;
    }

    @Override
    public void onTick(PlayerEntity player, TrackedPlayer tp, CheatDetectorContext ctx) {
        if (!enabled.get()) return;

        State st = stateByPlayer.computeIfAbsent(player.getUuid(), id -> new State());

        if (!tp.currentUsingItem() || !tp.lastUsingItem()) {
            st.itemUseTick = 0;
            st.disableTick = 0;
            return;
        }

        if (!tp.currentOnGround()) {
            st.disableTick = IN_JUMP_DISABLE_TICKS;
            return;
        }

        if (st.disableTick > 0) {
            st.disableTick--;
            return;
        }

        int idx = Math.min(st.itemUseTick, SLOW_SPEED_TICK.length - 1);
        double maxTickSpeed = SLOW_SPEED_TICK[idx];
        double speedMul = speedMul(player);

        double speed = Math.hypot(tp.posDelta().x, tp.posDelta().z);
        double possibleSpeed = maxTickSpeed * speedMul + THRESHOLD;
        if (speed > possibleSpeed) {
            ctx.notify(player, String.format("NoSlowA (Current: %.2f Max: %.2f)", speed, possibleSpeed));
        }

        if (st.itemUseTick < SLOW_SPEED_TICK.length - 1) st.itemUseTick++;
    }

    private static double speedMul(PlayerEntity player) {
        double mul = 1.0;
        StatusEffectInstance speedEffect = player.getStatusEffect(StatusEffects.SPEED);
        if (speedEffect != null) {
            mul *= speedEffect.getAmplifier() * 0.2 + 1.0;
        }
        mul *= player.getMovementSpeed() * 10.0;
        return mul;
    }

    private static class State {
        private int itemUseTick;
        private int disableTick;
    }
}
