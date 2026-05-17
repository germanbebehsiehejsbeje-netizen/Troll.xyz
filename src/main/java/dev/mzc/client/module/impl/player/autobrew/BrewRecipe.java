package dev.mzc.client.module.impl.player.autobrew;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.List;

/**
 * Multi-step brewing recipe definition. Each entry maps "current potion contents" to
 * "ingredient required to step toward the target".
 *
 * Example for Strength II:
 *   WATER     + NETHER_WART     -> AWKWARD
 *   AWKWARD   + BLAZE_POWDER    -> STRENGTH
 *   STRENGTH  + GLOWSTONE_DUST  -> STRONG_STRENGTH (Strength II)
 */
public final class BrewRecipe {

    public record Step(RegistryEntry<Potion> from, Item ingredient, RegistryEntry<Potion> to) {}

    public record Recipe(String name, List<Step> steps) {
        public RegistryEntry<Potion> finalPotion() { return steps.get(steps.size() - 1).to(); }
    }

    public static final Recipe STRENGTH_II = new Recipe(
            "Strength II",
            List.of(
                    new Step(Potions.WATER, Items.NETHER_WART, Potions.AWKWARD),
                    new Step(Potions.AWKWARD, Items.BLAZE_POWDER, Potions.STRENGTH),
                    new Step(Potions.STRENGTH, Items.GLOWSTONE_DUST, Potions.STRONG_STRENGTH)
            )
    );

    public static final Recipe SPEED_II = new Recipe(
            "Speed II",
            List.of(
                    new Step(Potions.WATER, Items.NETHER_WART, Potions.AWKWARD),
                    new Step(Potions.AWKWARD, Items.SUGAR, Potions.SWIFTNESS),
                    new Step(Potions.SWIFTNESS, Items.GLOWSTONE_DUST, Potions.STRONG_SWIFTNESS)
            )
    );

    public static final Recipe INVISIBILITY_8M = new Recipe(
            "Invisibility 8m",
            List.of(
                    new Step(Potions.WATER, Items.NETHER_WART, Potions.AWKWARD),
                    new Step(Potions.AWKWARD, Items.GOLDEN_CARROT, Potions.NIGHT_VISION),
                    new Step(Potions.NIGHT_VISION, Items.FERMENTED_SPIDER_EYE, Potions.INVISIBILITY),
                    new Step(Potions.INVISIBILITY, Items.REDSTONE, Potions.LONG_INVISIBILITY)
            )
    );

    public static final Recipe FIRE_RES_8M = new Recipe(
            "Fire Resistance 8m",
            List.of(
                    new Step(Potions.WATER, Items.NETHER_WART, Potions.AWKWARD),
                    new Step(Potions.AWKWARD, Items.MAGMA_CREAM, Potions.FIRE_RESISTANCE),
                    new Step(Potions.FIRE_RESISTANCE, Items.REDSTONE, Potions.LONG_FIRE_RESISTANCE)
            )
    );
}
