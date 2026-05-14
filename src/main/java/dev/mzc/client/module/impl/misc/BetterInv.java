package dev.mzc.client.module.impl.misc;

import dev.mzc.client.events.input.MouseDraggedEvent;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class BetterInv extends Module {

    private final BoolValue dragItemMove = new BoolValue("DragItemMove", true);
    private final BoolValue fastDrop = new BoolValue("FastDrop", false);
    private final NumberValue<Integer> delay = new NumberValue<>("Delay", 0, 0, 4, 1);

    private int dropTicks;

    public BetterInv() {
        super("BetterInv", Category.Misc);
        this.setType(ModuleType.Safe);
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (!fastDrop.get()) {
            return;
        }

        if (mc.options.dropKey.isPressed() && dropTicks > delay.get()) {
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.DROP_ITEM,
                    BlockPos.ORIGIN,
                    Direction.DOWN
                ));
            }
            dropTicks = 0;
        }
        ++dropTicks;
    }

    @EventHandler
    public void onMouseDragged(MouseDraggedEvent event) {
        if (dragItemMove.get()) {
            event.setCancelled(true);
        }
    }
}
