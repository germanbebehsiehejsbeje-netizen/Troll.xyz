package dev.mzc.client.module.impl.combat;

import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.player.InvUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;

public class AutoPot extends Module {
    private final BoolValue fireRes = new BoolValue("FireRes", true);
    private final BoolValue speed = new BoolValue("Speed", true);
    private final BoolValue strength = new BoolValue("Strength", true);
    private final BoolValue heal = new BoolValue("Heal", true);
    private final NumberValue<Double> healThreshold = new NumberValue<>("HealThreshold", 14.0, 2.0, 20.0, 0.5, heal::get);
    private final NumberValue<Double> silentSpeed = new NumberValue<>("SilentSpeed", 0.2, 0.05, 5.0, 0.05);
    private final NumberValue<Integer> delay = new NumberValue<>("Delay", 0, 0, 60, 1);
    private final NumberValue<Integer> applyWait = new NumberValue<>("ApplyWait", 10, 0, 40, 1);
    private final BoolValue debug = new BoolValue("Debug", false);
    private final BoolValue requireLookDown = new BoolValue("RequireLookDown", true);
    private final NumberValue<Double> minPitchDown = new NumberValue<>("MinPitchDown", 80.0, 0.0, 90.0, 1.0, requireLookDown::get);
    private final BoolValue invSwap = new BoolValue("InvSwap", true);
    private final NumberValue<Integer> swapSlot = new NumberValue<>("SwapSlot", 9, 8, 9, 1, invSwap::get);
    private int timer;

    public AutoPot() {
        super("AutoPot", Category.Combat);
        this.setType(ModuleType.Safe);
    }

    @EventHandler
    private void onTick(TickEvent.Pre e) {
        if (nullCheck()) return;
        if (!mc.player.isOnGround()) return;
        if (timer > 0) {
            timer--;
            return;
        }
        if (mc.interactionManager == null) return;

        RegistryEntry<Potion> need = choosePotion();
        if (need == null) return;

        int slot = findMatchingSplashPotionSlot(need);
        if (slot == -1) return;

        if (requireLookDown.get() && mc.player.getPitch() < minPitchDown.get()) {
            if (debug.get()) {
                mc.player.sendMessage(net.minecraft.text.Text.of(String.format("[AutoPot] pitch %.1f < %.1f", mc.player.getPitch(), minPitchDown.get())), false);
            }
            return;
        }

        if (invSwap.get()) {
            int targetHotbar = Math.max(8, Math.min(9, swapSlot.get())) - 1;
            int before = mc.player.getInventory().getSelectedSlot();
            boolean changedSelect = before != targetHotbar;

            if (changedSelect) InvUtil.swap(targetHotbar, false);

            boolean swapped = InvUtil.invSwap(slot);
            if (swapped) {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                InvUtil.invSwapBack();
                if (changedSelect) InvUtil.swap(before, false);
                timer = Math.max(delay.get(), applyWait.get());
                if (debug.get()) {
                    mc.player.sendMessage(net.minecraft.text.Text.of(String.format("[AutoPot] invswap slot %d", targetHotbar + 1)), false);
                }
            }
        } else {
            boolean swapped = InvUtil.invSwap(slot);
            if (swapped) {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                InvUtil.invSwapBack();
                timer = Math.max(delay.get(), applyWait.get());
                if (debug.get()) {
                    mc.player.sendMessage(net.minecraft.text.Text.of("[AutoPot] used potion with invswap."), false);
                }
            }
        }
    }

    private RegistryEntry<Potion> choosePotion() {
        if (heal.get()) {
            float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
            if (hp < healThreshold.get()) return Potions.HEALING;
        }
        if (fireRes.get() && !mc.player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE)) return Potions.FIRE_RESISTANCE;
        if (speed.get() && !mc.player.hasStatusEffect(StatusEffects.SPEED)) return Potions.SWIFTNESS;
        if (strength.get() && !mc.player.hasStatusEffect(StatusEffects.STRENGTH)) return Potions.STRENGTH;
        return null;
    }

    private int findMatchingSplashPotionSlot(RegistryEntry<Potion> desired) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!isSplashPotionStack(stack)) continue;
            RegistryEntry<Potion> p = getPotion(stack);
            if (p != null && matchesPotionForDesired(p, desired)) return i;
        }
        return -1;
    }

    private boolean matchesPotionForDesired(RegistryEntry<Potion> candidate, RegistryEntry<Potion> desired) {
        if (desired.equals(Potions.SWIFTNESS)) {
            return candidate.equals(Potions.SWIFTNESS) || candidate.equals(Potions.STRONG_SWIFTNESS) || candidate.equals(Potions.LONG_SWIFTNESS);
        }
        if (desired.equals(Potions.STRENGTH)) {
            return candidate.equals(Potions.STRENGTH) || candidate.equals(Potions.STRONG_STRENGTH) || candidate.equals(Potions.LONG_STRENGTH);
        }
        if (desired.equals(Potions.FIRE_RESISTANCE)) {
            return candidate.equals(Potions.FIRE_RESISTANCE) || candidate.equals(Potions.LONG_FIRE_RESISTANCE);
        }
        if (desired.equals(Potions.HEALING)) {
            return candidate.equals(Potions.HEALING) || candidate.equals(Potions.STRONG_HEALING);
        }
        return candidate.equals(desired);
    }

    private boolean isSplashPotionStack(ItemStack stack) {
        return stack.getItem() == Items.SPLASH_POTION;
    }

    private RegistryEntry<Potion> getPotion(ItemStack stack) {
        PotionContentsComponent comp = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (comp == null) return null;
        return comp.potion().orElse(null);
    }
}
