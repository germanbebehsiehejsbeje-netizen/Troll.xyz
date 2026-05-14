package dev.mzc.client.module.impl.misc.cheatdetector.checks;

import dev.mzc.client.module.impl.misc.cheatdetector.CheatCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.CheatDetectorContext;
import dev.mzc.client.module.impl.misc.cheatdetector.TrackedPlayer;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import net.minecraft.entity.player.PlayerEntity;

public class AutoClickerCheck implements CheatCheck {
    private final BoolValue enabled;
    private final NumberValue<Integer> maxSwingsPerSecond;

    public AutoClickerCheck(BoolValue enabled, NumberValue<Integer> maxSwingsPerSecond) {
        this.enabled = enabled;
        this.maxSwingsPerSecond = maxSwingsPerSecond;
    }

    @Override
    public void onTick(PlayerEntity player, TrackedPlayer tracked, CheatDetectorContext context) {
        if (!enabled.get()) return;

        if (tracked.swingPerSecond() > maxSwingsPerSecond.get()) {
            context.notify(player, "动作频率异常");
        }
    }
}

