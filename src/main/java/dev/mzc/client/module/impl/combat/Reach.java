package dev.mzc.client.module.impl.combat;

import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

import java.util.concurrent.ThreadLocalRandom;

public class Reach extends Module {
    private final NumberValue<Double> entityReach = new NumberValue<>("EntityReach", 3.1, 3.0, 6.0, 0.05);
    private final NumberValue<Double> blockReach = new NumberValue<>("BlockReach", 4.0, 4.0, 6.0, 0.05);
    private final NumberValue<Integer> chance = new NumberValue<>("Chance", 100, 1, 100, 1);

    private static final Identifier ENTITY_REACH_ID = Identifier.of("sakura", "entity_reach_modifier");
    private static final Identifier BLOCK_REACH_ID = Identifier.of("sakura", "block_reach_modifier");

    public Reach() {
        super("Reach", Category.Combat);
        this.setType(ModuleType.Hack);
    }

    @Override
    public void onEnable() {
        updateReach();
    }

    @Override
    public void onDisable() {
        resetReach();
    }

    @EventHandler
    public void onTick(TickEvent.Post event) {
        if (mc.player == null) return;
        updateReach();
    }

    private void updateReach() {
        if (mc.player == null) return;

        if (shouldApplyThisTick()) {
            applyModifier(EntityAttributes.ENTITY_INTERACTION_RANGE, ENTITY_REACH_ID, entityReach.get());
            applyModifier(EntityAttributes.BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID, blockReach.get());
        } else {
            removeModifier(EntityAttributes.ENTITY_INTERACTION_RANGE, ENTITY_REACH_ID);
            removeModifier(EntityAttributes.BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID);
        }
    }

    private void resetReach() {
        if (mc.player == null) return;
        removeModifier(EntityAttributes.ENTITY_INTERACTION_RANGE, ENTITY_REACH_ID);
        removeModifier(EntityAttributes.BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID);
    }

    private void applyModifier(RegistryEntry<EntityAttribute> attribute, Identifier id, double targetValue) {
        EntityAttributeInstance instance = mc.player.getAttributeInstance(attribute);
        if (instance != null) {
            instance.removeModifier(id);
            // Calculate what we need to add to reach targetValue
            // instance.getValue() now returns the value without our modifier (since we just removed it)
            double current = instance.getValue();
            double bonus = targetValue - current;
            
            // Only apply if significant difference (avoid floating point noise)
            if (Math.abs(bonus) > 0.001) {
                 instance.addTemporaryModifier(new EntityAttributeModifier(id, bonus, EntityAttributeModifier.Operation.ADD_VALUE));
            }
        }
    }
    
    private void removeModifier(RegistryEntry<EntityAttribute> attribute, Identifier id) {
        EntityAttributeInstance instance = mc.player.getAttributeInstance(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }

    private boolean shouldApplyThisTick() {
        int value = chance.get();
        if (value >= 100) return true;
        return ThreadLocalRandom.current().nextInt(100) < value;
    }
}
