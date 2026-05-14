package dev.mzc.client.module.impl.player;

import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.mixin.accessor.IMinecraftClient;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.BoatItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;

public class FastUse extends Module {

    private final NumberValue<Double> cooldown = new NumberValue<>("Cooldown", 0.0, 0.0, 4.0, 0.1);
    private final BoolValue experienceBottle = new BoolValue("ExperienceBottle", true);
    private final BoolValue egg = new BoolValue("Egg", true);
    private final BoolValue snowball = new BoolValue("Snowball", true);
    private final BoolValue minecart = new BoolValue("Minecart", true);
    private final BoolValue boat = new BoolValue("Boat", true);
    private final BoolValue spawnEgg = new BoolValue("SpawnEgg", true);

    private long lastUseTime = 0;

    public FastUse() {
        super("FastUse", Category.Player);
        this.setType(ModuleType.Safe);
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        boolean holdingTargetItem = isTargetItem(mc.player.getMainHandStack()) || isTargetItem(mc.player.getOffHandStack());

        if (holdingTargetItem && mc.options.useKey.isPressed()) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastUseTime >= cooldown.get() * 50) { // 转换 tick 到毫秒，支持 0.1 tick 级别
                ((IMinecraftClient) mc).hookSetItemUseCooldown(0);
                ((IMinecraftClient) mc).hookDoItemUse();
                lastUseTime = currentTime;
            }
        }
    }

    private boolean isTargetItem(ItemStack stack) {
        return isTargetItem(stack.getItem());
    }

    private boolean isTargetItem(Item item) {
        if (experienceBottle.get() && item == Items.EXPERIENCE_BOTTLE) return true;
        if (egg.get() && item == Items.EGG) return true;
        if (snowball.get() && item == Items.SNOWBALL) return true;
        if (minecart.get() && isMinecart(item)) return true;
        if (boat.get() && isBoat(item)) return true;
        return spawnEgg.get() && item instanceof SpawnEggItem;
    }

    private boolean isMinecart(Item item) {
        return item == Items.MINECART
                || item == Items.CHEST_MINECART
                || item == Items.FURNACE_MINECART
                || item == Items.HOPPER_MINECART
                || item == Items.TNT_MINECART
                || item == Items.COMMAND_BLOCK_MINECART;
    }

    private boolean isBoat(Item item) {
        return item instanceof BoatItem;
    }
}
