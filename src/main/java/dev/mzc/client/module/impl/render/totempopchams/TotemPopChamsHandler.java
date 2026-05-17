package dev.mzc.client.module.impl.render.totempopchams;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TotemPopChamsHandler {

    private static final Set<CapturedPlayer> positions = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static void handleTotem(final PlayerEntity player) {
        positions.add(new CapturedPlayer(MinecraftClient.getInstance().world, player));
    }

    public static Set<CapturedPlayer> getPositions() {
        return positions;
    }
}
