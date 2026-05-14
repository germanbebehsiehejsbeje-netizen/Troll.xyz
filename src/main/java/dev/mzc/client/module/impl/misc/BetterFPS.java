package dev.mzc.client.module.impl.misc;

import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;

import java.util.HashMap;
import java.util.Map;

public class BetterFPS extends Module {
    private static final double DROPPED_ITEM_GROUP_CELL = 1.5;

    private final NumberValue<Integer> threshold = new NumberValue<>("Threshold", 25, 1, 1000, 1);

    private final NumberValue<Double> boatCullDistance = new NumberValue<>("BoatCullDistance", 18.0, 4.0, 64.0, 1.0);
    private final NumberValue<Double> minecartCullDistance = new NumberValue<>("MinecartCullDistance", 18.0, 4.0, 64.0, 1.0);
    private final NumberValue<Double> droppedItemCullDistance = new NumberValue<>("DroppedItemCullDistance", 14.0, 4.0, 64.0, 1.0);
    private final NumberValue<Double> expOrbCullDistance = new NumberValue<>("ExpOrbCullDistance", 14.0, 4.0, 64.0, 1.0);
    private final NumberValue<Double> overlapCellSize = new NumberValue<>("OverlapCellSize", 1.25, 0.25, 4.0, 0.25);
    private final NumberValue<Double> minKeepDistance = new NumberValue<>("MinKeepDistance", 0.0, 0.0, 16.0, 0.5);

    private final NumberValue<Integer> particlePerTick = new NumberValue<>("ParticlePerTick", 300, 0, 3000, 10);
    private final NumberValue<Integer> expParticlePerTick = new NumberValue<>("ExpParticlePerTick", 80, 0, 1000, 5);
    private final NumberValue<Integer> scanInterval = new NumberValue<>("ScanInterval", 5, 1, 20, 1);

    private final Map<Long, Integer> boatOverlapCounts = new HashMap<>();
    private final Map<Long, Integer> minecartOverlapCounts = new HashMap<>();
    private final Map<Long, Integer> expOrbOverlapCounts = new HashMap<>();
    private final Map<Item, Integer> droppedItemTypeCounts = new HashMap<>();
    private final Map<Long, Integer> droppedItemBlockTypeCounts = new HashMap<>();
    private int particleCounterThisTick;
    private int expParticleCounterThisTick;
    private int lastScanTick = -1;

    public BetterFPS() {
        super("BetterFPS", Category.Misc);
        this.setType(ModuleType.Safe);
    }

    @Override
    protected void onDisable() {
        boatOverlapCounts.clear();
        minecartOverlapCounts.clear();
        expOrbOverlapCounts.clear();
        droppedItemTypeCounts.clear();
        droppedItemBlockTypeCounts.clear();
        particleCounterThisTick = 0;
        expParticleCounterThisTick = 0;
        lastScanTick = -1;
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        particleCounterThisTick = 0;
        expParticleCounterThisTick = 0;
        int age = mc.player.age;
        if (lastScanTick != -1 && age - lastScanTick < scanInterval.get()) return;

        lastScanTick = age;
        recalculateNearbyCounts();
    }

    public boolean shouldSkipEntity(Entity entity) {
        if (!isEnabled() || mc.player == null || entity == null || entity == mc.player) return false;

        if (entity instanceof AbstractBoatEntity) {
            int overlapCount = getOverlapCount(entity, boatOverlapCounts);
            return shouldSkipByLoad(overlapCount, threshold.get(), boatCullDistance.get(), entity);
        }
        if (entity instanceof AbstractMinecartEntity) {
            int overlapCount = getOverlapCount(entity, minecartOverlapCounts);
            return shouldSkipByLoad(overlapCount, threshold.get(), minecartCullDistance.get(), entity);
        }
        if (entity instanceof ExperienceOrbEntity) {
            int overlapCount = getOverlapCount(entity, expOrbOverlapCounts);
            return shouldSkipByLoad(overlapCount, threshold.get(), expOrbCullDistance.get(), entity);
        }
        if (entity instanceof ItemEntity) {
            if (shouldSkipDenseItemGroup((ItemEntity) entity)) return true;
            int typeCount = getDroppedItemTypeCount((ItemEntity) entity);
            return shouldSkipByLoad(typeCount, threshold.get(), droppedItemCullDistance.get(), entity);
        }

        return false;
    }

    public boolean allowParticle() {
        if (!isEnabled()) return true;
        if (particlePerTick.get() <= 0) return false;

        if (particleCounterThisTick >= particlePerTick.get()) return false;
        particleCounterThisTick++;
        return true;
    }

    public boolean allowExpParticle(ParticleEffect effect) {
        if (!isEnabled()) return true;
        if (!isExpParticle(effect)) return true;
        if (expParticlePerTick.get() <= 0) return false;

        if (expParticleCounterThisTick >= expParticlePerTick.get()) return false;
        expParticleCounterThisTick++;
        return true;
    }

    public int getRenderedAmountForItem(ItemEntity itemEntity, int originalRenderedAmount) {
        if (!isEnabled() || itemEntity == null) return originalRenderedAmount;
        int groupedCount = droppedItemBlockTypeCounts.getOrDefault(toBlockItemKey(itemEntity), 0);
        if (groupedCount > 3) {
            return Math.min(originalRenderedAmount, 2);
        }
        return originalRenderedAmount;
    }

    private boolean shouldSkipByLoad(int nearbyCount, int threshold, double keepDistance, Entity entity) {
        if (nearbyCount <= threshold) return false;

        double distanceSq = mc.player.squaredDistanceTo(entity);
        double keepDistanceSq = keepDistance * keepDistance;
        if (distanceSq > keepDistanceSq) return true;

        double minKeepDistanceSq = minKeepDistance.get() * minKeepDistance.get();
        if (distanceSq <= minKeepDistanceSq) return false;

        // When overloaded, keep only a stable subset of entities in the medium range.
        double keepRatio = Math.max(0.05, Math.min(1.0, (double) threshold / (double) nearbyCount));
        double sample = stableSample01(entity.getId());
        return sample > keepRatio;
    }

    private void recalculateNearbyCounts() {
        boatOverlapCounts.clear();
        minecartOverlapCounts.clear();
        expOrbOverlapCounts.clear();
        droppedItemTypeCounts.clear();
        droppedItemBlockTypeCounts.clear();

        if (mc.world == null) return;

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof AbstractBoatEntity) {
                incrementCount(boatOverlapCounts, toOverlapKey(entity));
                continue;
            }
            if (entity instanceof AbstractMinecartEntity) {
                incrementCount(minecartOverlapCounts, toOverlapKey(entity));
                continue;
            }
            if (entity instanceof ExperienceOrbEntity) {
                incrementCount(expOrbOverlapCounts, toOverlapKey(entity));
                continue;
            }
            if (entity instanceof ItemEntity) {
                ItemEntity itemEntity = (ItemEntity) entity;
                Item item = itemEntity.getStack().getItem();
                droppedItemTypeCounts.put(item, droppedItemTypeCounts.getOrDefault(item, 0) + 1);
                incrementCount(droppedItemBlockTypeCounts, toBlockItemKey(itemEntity));
            }
        }
    }

    private int getOverlapCount(Entity entity, Map<Long, Integer> map) {
        return map.getOrDefault(toOverlapKey(entity), 0);
    }

    private int getDroppedItemTypeCount(ItemEntity itemEntity) {
        Item item = itemEntity.getStack().getItem();
        return droppedItemTypeCounts.getOrDefault(item, 0);
    }

    private void incrementCount(Map<Long, Integer> map, long key) {
        map.put(key, map.getOrDefault(key, 0) + 1);
    }

    private long toOverlapKey(Entity entity) {
        double cell = Math.max(0.25, overlapCellSize.get());
        int cx = (int) Math.floor(entity.getX() / cell);
        int cy = (int) Math.floor(entity.getY() / cell);
        int cz = (int) Math.floor(entity.getZ() / cell);
        return hash3(cx, cy, cz);
    }

    private long hash3(int x, int y, int z) {
        long h = 1469598103934665603L;
        h ^= x;
        h *= 1099511628211L;
        h ^= y;
        h *= 1099511628211L;
        h ^= z;
        h *= 1099511628211L;
        return h;
    }

    private long toBlockItemKey(ItemEntity entity) {
        int cx = (int) Math.floor(entity.getX() / DROPPED_ITEM_GROUP_CELL);
        int cy = (int) Math.floor(entity.getY() / DROPPED_ITEM_GROUP_CELL);
        int cz = (int) Math.floor(entity.getZ() / DROPPED_ITEM_GROUP_CELL);
        long blockKey = hash3(cx, cy, cz);
        int itemId = Item.getRawId(entity.getStack().getItem());
        return blockKey * 31L + itemId;
    }

    private boolean isExpParticle(ParticleEffect effect) {
        return effect.getType() == ParticleTypes.ENCHANT
                || effect.getType() == ParticleTypes.ENTITY_EFFECT
                || effect.getType() == ParticleTypes.EFFECT;
    }

    private boolean shouldSkipDenseItemGroup(ItemEntity itemEntity) {
        int groupedCount = droppedItemBlockTypeCounts.getOrDefault(toBlockItemKey(itemEntity), 0);
        if (groupedCount <= 3) return false;

        double distanceSq = mc.player.squaredDistanceTo(itemEntity);
        double minKeepDistanceSq = minKeepDistance.get() * minKeepDistance.get();
        if (distanceSq <= minKeepDistanceSq) return false;

        double keepRatio = Math.max(0.05, 2.0 / groupedCount);
        return stableSample01(itemEntity.getId()) > keepRatio;
    }

    private double stableSample01(int value) {
        int h = value;
        h ^= (h >>> 16);
        h *= 0x7feb352d;
        h ^= (h >>> 15);
        h *= 0x846ca68b;
        h ^= (h >>> 16);
        return (h & 0x7fffffff) / (double) Integer.MAX_VALUE;
    }
}
