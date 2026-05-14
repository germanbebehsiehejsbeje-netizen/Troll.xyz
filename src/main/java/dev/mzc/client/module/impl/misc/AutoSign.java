package dev.mzc.client.module.impl.misc;

import dev.mzc.client.events.packet.PacketEvent;
import dev.mzc.client.events.EventType;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import meteordevelopment.orbit.EventHandler;
import dev.mzc.client.values.impl.StringValue;
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket;

public class AutoSign extends Module {
    private final StringValue line1 = new StringValue("Line 1");
    private final StringValue line2 = new StringValue("Line 2");
    private final StringValue line3 = new StringValue("Line 3");
    private final StringValue line4 = new StringValue("Line 4");

    public AutoSign() {
        super("AutoSign", Category.Misc);
        this.setType(ModuleType.Safe);
    }

    private boolean sending = false;

    @EventHandler
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.SEND && event.getPacket() instanceof UpdateSignC2SPacket packet) {
            if (sending) return;

            String[] lines = new String[]{line1.get(), line2.get(), line3.get(), line4.get()};
            event.cancel();
            
            sending = true;
            try {
                mc.getNetworkHandler().sendPacket(new UpdateSignC2SPacket(
                    packet.getPos(),
                    packet.isFront(),
                    lines[0],
                    lines[1],
                    lines[2],
                    lines[3]
                ));
            } finally {
                sending = false;
            }
        }
    }
}
