package dev.mzc.client.module.impl.misc.cheatdetector;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;

public interface CheatCheck {
    void onTick(PlayerEntity player, TrackedPlayer tracked, CheatDetectorContext context);

    default void onPacket(Packet<?> packet, CheatDetectorContext context) {
    }
}
