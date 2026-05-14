package dev.mzc.client.manager.impl;

import dev.mzc.client.Sakura;
import dev.mzc.client.events.client.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.text.Text;

import static dev.mzc.client.Sakura.mc;

public class AntiSniperManager {
    private static final String TARGET_PLAYER = "MZC8865";
    private static final double SAFE_DISTANCE = 5.0;

    public AntiSniperManager() {
        Sakura.EVENT_BUS.subscribe(this);
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;

        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;

            if (TARGET_PLAYER.equalsIgnoreCase(player.getName().getString())) {
                double distance = mc.player.distanceTo(player);
                if (distance <= SAFE_DISTANCE) {
                    disconnect("disconnected");
                    return;
                }
            }
        }
    }

    private void disconnect(String reason) {
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().getConnection().disconnect(Text.of(reason));
        }
    }
}
