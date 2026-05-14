package dev.mzc.client.module.impl.misc;

import dev.mzc.client.events.EventType;
import dev.mzc.client.events.packet.PacketEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EntityPosition;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;

public class NoRotate extends Module {
    public NoRotate() {
        super("NoRotate", Category.Misc);
        this.setType(ModuleType.Safe);
    }

    @EventHandler
    private void onReceivePacket(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE || nullCheck()) return;

        if (event.getPacket() instanceof PlayerPositionLookS2CPacket packet) {
            EntityPosition oldPosition = packet.change();
            EntityPosition newPosition = oldPosition.withRotation(mc.player.getYaw(), mc.player.getPitch());
            event.setPacket(PlayerPositionLookS2CPacket.of(packet.teleportId(), newPosition, packet.relatives()));
        }
    }
}
