package dev.mzc.client.module.impl.misc.cheatdetector.checks;

import dev.mzc.client.module.impl.misc.cheatdetector.CheatCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.CheatDetectorContext;
import dev.mzc.client.module.impl.misc.cheatdetector.TrackedPlayer;
import dev.mzc.client.values.impl.BoolValue;
import net.minecraft.entity.player.PlayerEntity;

public class InvalidRotationCheck implements CheatCheck {
    private final BoolValue enabled;

    public InvalidRotationCheck(BoolValue enabled) {
        this.enabled = enabled;
    }

    @Override
    public void onTick(PlayerEntity player, TrackedPlayer tracked, CheatDetectorContext context) {
        if (!enabled.get()) return;
        if (Float.isNaN(player.getYaw()) || Float.isNaN(player.getPitch()) || Math.abs(player.getPitch()) > 90.1f) {
            context.notify(player, "无效旋转数据");
        }
    }
}

