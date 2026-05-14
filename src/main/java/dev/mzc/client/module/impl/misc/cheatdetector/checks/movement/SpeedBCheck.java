package dev.mzc.client.module.impl.misc.cheatdetector.checks.movement;

import dev.mzc.client.module.impl.misc.cheatdetector.CheatCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.CheatDetectorContext;
import dev.mzc.client.module.impl.misc.cheatdetector.TrackedPlayer;
import dev.mzc.client.values.impl.BoolValue;
import net.minecraft.entity.player.PlayerEntity;

public class SpeedBCheck implements CheatCheck {
    private final BoolValue enabled;

    public SpeedBCheck(BoolValue enabled) {
        this.enabled = enabled;
    }

    @Override
    public void onTick(PlayerEntity player, TrackedPlayer tp, CheatDetectorContext ctx) {
        if (!enabled.get()) return;

        // Note: getHungerManager().getFoodLevel() is not synchronized for other players 
        // in vanilla multiplayer, so this may not flag correctly for remote players.
        if (player.isSprinting() && player.getHungerManager().getFoodLevel() <= 6) {
            ctx.notify(player, "SpeedB (Sprinting with low hunger)");
        }
    }
}
