package dev.mzc.client.module.impl.misc.cheatdetector.checks;

import dev.mzc.client.module.impl.misc.cheatdetector.CheatCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.CheatDetectorContext;
import dev.mzc.client.module.impl.misc.cheatdetector.TrackedPlayer;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AimGcdCheck implements CheatCheck {
    private static final double MINIMUM_DIVISOR = 0.02;
    private static final int GCD_BUCKET_SCALE = 10000;

    private final BoolValue enabled;
    private final NumberValue<Integer> gcdSignificant;
    private final Map<UUID, State> stateByPlayer = new HashMap<>();

    public AimGcdCheck(BoolValue enabled, NumberValue<Integer> gcdSignificant) {
        this.enabled = enabled;
        this.gcdSignificant = gcdSignificant;
    }

    @Override
    public void onTick(PlayerEntity player, TrackedPlayer tracked, CheatDetectorContext context) {
        if (!enabled.get()) return;

        double last = tracked.lastAbsYawDelta();
        double prev = tracked.prevAbsYawDelta();
        if (last < 0.35 || prev < 0.35 || last >= 12.0 || prev >= 12.0) {
            stateByPlayer.remove(player.getUuid());
            return;
        }

        double div = gcd(last, prev);
        if (div <= MINIMUM_DIVISOR) return;

        int bucket = (int) Math.floor(div * GCD_BUCKET_SCALE);
        State st = stateByPlayer.computeIfAbsent(player.getUuid(), id -> new State());
        if (st.lastBucket == bucket) {
            st.streak++;
        } else {
            st.lastBucket = bucket;
            st.streak = 1;
        }

        if (st.streak >= gcdSignificant.get()) {
            context.notify(player, "旋转GCD异常");
        }
    }

    private double gcd(double a, double b) {
        if (a == 0) return 0;
        if (a < b) {
            double t = a;
            a = b;
            b = t;
        }
        while (b > MINIMUM_DIVISOR) {
            double t = a - (Math.floor(a / b) * b);
            a = b;
            b = t;
        }
        return a;
    }

    private static class State {
        private int lastBucket = Integer.MIN_VALUE;
        private int streak;
    }
}
