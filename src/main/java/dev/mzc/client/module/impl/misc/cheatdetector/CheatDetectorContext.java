package dev.mzc.client.module.impl.misc.cheatdetector;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.function.BiConsumer;

public class CheatDetectorContext {
    private final MinecraftClient mc;
    private final long worldTick;
    private final BiConsumer<PlayerEntity, String> notifier;

    public CheatDetectorContext(MinecraftClient mc, long worldTick, BiConsumer<PlayerEntity, String> notifier) {
        this.mc = mc;
        this.worldTick = worldTick;
        this.notifier = notifier;
    }

    public MinecraftClient mc() {
        return mc;
    }

    public long worldTick() {
        return worldTick;
    }

    public void notify(PlayerEntity player, String reason) {
        notifier.accept(player, reason);
    }
}

