package dev.mzc.client.module.impl.player;

import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.player.InvUtil;
import dev.mzc.client.utils.player.MovementUtil;
import dev.mzc.client.utils.time.TimerUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

public class AutoSoup extends Module {

    public enum RefillMode {
        InvOpen(),
        NoMove(),
        Always();
        RefillMode() {
        }
    }

    private final NumberValue<Double> health = new NumberValue<>("Health", 14.0, 1.0, 20.0, 0.5);
    private final BoolValue dropBowl = new BoolValue("DropBowl", true);
    private final BoolValue autoRefill = new BoolValue("AutoRefill", true);
    private final EnumValue<RefillMode> refillMode = new EnumValue<>("RefillMode", RefillMode.InvOpen, autoRefill::get);
    private final NumberValue<Integer> refillDelay = new NumberValue<>("RefillDelay", 100, 0, 1000, 10, autoRefill::get);

    private final TimerUtil timer = new TimerUtil();

    public AutoSoup() {
        super("AutoSoup", Category.Player);
        this.setType(ModuleType.Safe);
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        // Eating logic
        if (mc.player.getHealth() <= health.get()) {
            int soupSlot = InvUtil.findInHotbar(Items.MUSHROOM_STEW).slot();

            if (soupSlot != -1) {
                int oldSlot = mc.player.getInventory().getSelectedSlot();

                if (soupSlot != oldSlot) {
                    mc.player.getInventory().setSelectedSlot(soupSlot);
                    mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(soupSlot));
                }

                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(oldSlot));
                mc.player.getInventory().setSelectedSlot(oldSlot);
                return; 
            }
        }
        
        // Drop bowl logic
        if (dropBowl.get()) {
             int bowlSlot = InvUtil.findInHotbar(Items.BOWL).slot();
             if (bowlSlot != -1) {
                 mc.player.getInventory().setSelectedSlot(bowlSlot);
                 mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(bowlSlot));
                 mc.player.dropSelectedItem(true);
             }
        }

        // Refill logic
        if (autoRefill.get() && timer.passedMS(refillDelay.get())) {
            boolean shouldRefill = switch (refillMode.get()) {
                case InvOpen -> mc.currentScreen instanceof InventoryScreen;
                case NoMove -> !MovementUtil.isMoving();
                case Always -> true;
            };

            if (shouldRefill) {
                refillSoup();
            }
        }
    }

    private void refillSoup() {
        // Find empty slot in hotbar
        int emptyHotbarSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty() || mc.player.getInventory().getStack(i).getItem() == Items.BOWL) {
                emptyHotbarSlot = i;
                break;
            }
        }

        if (emptyHotbarSlot != -1) {
            // Find soup in inventory (9-35)
            for (int i = 9; i < 36; i++) {
                if (mc.player.getInventory().getStack(i).getItem() == Items.MUSHROOM_STEW) {
                    // Move soup to empty hotbar slot
                    // Slot ID mapping:
                    // Hotbar: 36-44
                    // Inventory: 9-35
                    // But in InventoryScreen/PlayerScreenHandler:
                    // 9-35 are main inventory (IDs 9-35)
                    // 0-8 are crafting/armor/offhand? No.
                    // PlayerScreenHandler slots:
                    // 0-4: Crafting
                    // 5-8: Armor
                    // 9-35: Inventory (3 rows)
                    // 36-44: Hotbar
                    // 45: Offhand
                    
                    // InvUtil usually handles slot mapping or we use windowClick with correct IDs.
                    // If we are in InventoryScreen, we use quick move (Shift-Click) if hotbar has space?
                    // Or drag and drop?
                    
                    // Simplest: Shift-Click from inventory moves to hotbar.
                    // Slot ID i (9-35) is correct for PlayerScreenHandler.
                    
                    mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                    timer.reset();
                    return;
                }
            }
        }
    }
}



