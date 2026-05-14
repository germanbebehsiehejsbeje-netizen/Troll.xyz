package dev.mzc.client.module.impl.player;

import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.time.TimerUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;

public class AutoEat extends Module {
    private static final int EAT_TICKS_TOTAL = 33;

    public enum Mode {
        Hunger(),
        Health(),
        Both(),
        Either();
        Mode() {
        }
    }

    private final EnumValue<Mode> mode = new EnumValue<>("Mode", Mode.Hunger, Mode.class);
    private final BoolValue swapBack = new BoolValue("SwapBack", true);
    private final BoolValue avoidPlayers = new BoolValue("AvoidPlayers", false);
    private final NumberValue<Double> avoidRange = new NumberValue<>("AvoidRange",
            8.0,
            0.0,
            20.0,
            0.5,
            () -> avoidPlayers.get()
    );


    private final NumberValue<Double> healthThreshold = new NumberValue<>("HealthThreshold",
            12.0,
            1.0,
            20.0,
            0.5,
            () -> mode.is(Mode.Health) || mode.is(Mode.Both) || mode.is(Mode.Either)
    );

    

    private boolean eating;
    private int originalSlot = -1;
    private boolean startedUsing = false;
    private int eatTicksLeft = 0;

    private final TimerUtil timer = new TimerUtil();

    public AutoEat() {
        super("AutoEat", Category.Player);
        this.setType(ModuleType.Safe);
    }

    public boolean isEating() {
        return eating;
    }

    public float getEatingProgress01() {
        if (!eating) return 0f;
        if (EAT_TICKS_TOTAL <= 0) return 0f;
        float p = 1f - (eatTicksLeft / (float) EAT_TICKS_TOTAL);
        if (p < 0f) return 0f;
        if (p > 1f) return 1f;
        return p;
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        if (eating) {
            mc.options.useKey.setPressed(true);
            if (eatTicksLeft > 0) {
                eatTicksLeft--;
            }
            if (eatTicksLeft <= 0) {
                mc.options.useKey.setPressed(false);
                eating = false;
                startedUsing = false;
                if (swapBack.get() && originalSlot != -1) {
                    mc.player.getInventory().setSelectedSlot(originalSlot);
                    mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(originalSlot));
                }
                originalSlot = -1;
            }
            return;
        }

        if (mc.player.isUsingItem()) return;

        if (avoidPlayers.get() && mc.world != null) {
            double r = avoidRange.get();
            for (var p : mc.world.getPlayers()) {
                if (p == mc.player) continue;
                if (p.isRemoved()) continue;
                if (p.distanceTo(mc.player) <= r) {
                    return;
                }
            }
        }

        int bestFoodSlot = findBestFoodInHotbar();
        if (bestFoodSlot == -1) return;

        ItemStack bestFood = mc.player.getInventory().getStack(bestFoodSlot);
        FoodComponent comp = bestFood.get(DataComponentTypes.FOOD);

        int nutrition = comp != null ? comp.nutrition() : 0;
        boolean alwaysEdible = comp != null && comp.canAlwaysEat();

        int hunger = mc.player.getHungerManager().getFoodLevel();
        int deficit = Math.max(0, 20 - hunger);

        boolean hungerOk = nutrition > 0 && deficit >= nutrition;

        double health = mc.player.getHealth();
        boolean healthOk = health < healthThreshold.get() && (alwaysEdible || deficit > 0);

        boolean shouldEat;

        if (mode.is(Mode.Hunger)) {
            shouldEat = hungerOk;
        } else if (mode.is(Mode.Health)) {
            shouldEat = healthOk;
        } else if (mode.is(Mode.Both)) {
            shouldEat = hungerOk && healthOk;
        } else {
            shouldEat = hungerOk || healthOk;
        }

        if (!shouldEat) return;

        int oldSlot = mc.player.getInventory().getSelectedSlot();

        if (bestFoodSlot != oldSlot) {
            mc.player.getInventory().setSelectedSlot(bestFoodSlot);
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(bestFoodSlot));
        }

        originalSlot = oldSlot;

        mc.options.useKey.setPressed(true);
        eating = true;
        startedUsing = false;
        eatTicksLeft = EAT_TICKS_TOTAL;
        timer.reset();
    }

    private int findBestFoodInHotbar() {

        int bestSlot = -1;
        int bestNutrition = -1;
        int bestCount = -1;

        for (int i = 0; i < 9; i++) {

            ItemStack stack = mc.player.getInventory().getStack(i);

            FoodComponent comp = stack.get(DataComponentTypes.FOOD);
            if (comp == null) continue;

            int nutrition = comp.nutrition();

            if (nutrition > bestNutrition || (nutrition == bestNutrition && stack.getCount() > bestCount)) {

                bestNutrition = nutrition;
                bestCount = stack.getCount();
                bestSlot = i;
            }
        }

        return bestSlot;
    }
}



