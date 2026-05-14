package dev.mzc.client.module.impl.render;

import dev.mzc.client.mixin.accessor.IMinecraftClient;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.client.ChatUtil;
import dev.mzc.client.values.impl.BoolValue;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Map;
import java.util.Set;

public class XRay extends Module {
    public XRay() {
        super("XRay", Category.Render);
        this.setType(ModuleType.All);
        initOreMap();
    }

    private final BoolValue coal = new BoolValue("Coal", false);
    private final BoolValue iron = new BoolValue("Iron", true);
    private final BoolValue gold = new BoolValue("Gold", true);
    private final BoolValue redstone = new BoolValue("Redstone", true);
    private final BoolValue lapis = new BoolValue("Lapis", true);
    private final BoolValue diamond = new BoolValue("Diamond", true);
    private final BoolValue emerald = new BoolValue("Emerald", true);
    private final BoolValue copper = new BoolValue("Copper", true);
    private final BoolValue netherite = new BoolValue("Netherite", true);
    private final BoolValue netherGold = new BoolValue("NetherGold", true);
    private final BoolValue netherQuartz = new BoolValue("NetherQuartz", false);
    private final BoolValue sodiumCompat = new BoolValue("SodiumCompat", true);

    private final Map<Block, BoolValue> oreMap = new HashMap<>();
    private final Map<String, BoolField> sodiumFields = new LinkedHashMap<>();
    private Object sodiumOptionsRef;

    private void initOreMap() {
        Set.of(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, Blocks.COAL_BLOCK).forEach(b -> oreMap.put(b, coal));
        Set.of(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.RAW_IRON_BLOCK, Blocks.IRON_BLOCK).forEach(b -> oreMap.put(b, iron));
        Set.of(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.RAW_GOLD_BLOCK, Blocks.GOLD_BLOCK).forEach(b -> oreMap.put(b, gold));
        Set.of(Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.REDSTONE_BLOCK).forEach(b -> oreMap.put(b, redstone));
        Set.of(Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE, Blocks.LAPIS_BLOCK).forEach(b -> oreMap.put(b, lapis));
        Set.of(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.DIAMOND_BLOCK).forEach(b -> oreMap.put(b, diamond));
        Set.of(Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE, Blocks.EMERALD_BLOCK).forEach(b -> oreMap.put(b, emerald));
        Set.of(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE, Blocks.RAW_COPPER_BLOCK, Blocks.COPPER_BLOCK).forEach(b -> oreMap.put(b, copper));
        Set.of(Blocks.ANCIENT_DEBRIS, Blocks.NETHERITE_BLOCK).forEach(b -> oreMap.put(b, netherite));
        Set.of(Blocks.NETHER_GOLD_ORE, Blocks.GILDED_BLACKSTONE).forEach(b -> oreMap.put(b, netherGold));
        Set.of(Blocks.NETHER_QUARTZ_ORE, Blocks.QUARTZ_BLOCK).forEach(b -> oreMap.put(b, netherQuartz));
    }

    @Override
    protected void onEnable() {
        if (sodiumCompat.get()) {
            applySodiumCompat();
        }
        ((IMinecraftClient) mc).setChunkCullingEnabled(false);
        if (mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    @Override
    protected void onDisable() {
        restoreSodiumCompat();
        ((IMinecraftClient) mc).setChunkCullingEnabled(true);
        if (mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    public boolean shouldRender(Block block) {
        if (!isEnabled()) return true;
        BoolValue setting = oreMap.get(block);
        if (block == Blocks.DIRT || block == Blocks.DIRT_PATH || block == Blocks.FARMLAND || block == Blocks.SHORT_GRASS || block == Blocks.TALL_GRASS || block == Blocks.SEAGRASS || block == Blocks.TALL_SEAGRASS || block == Blocks.KELP || block == Blocks.KELP_PLANT) return false;
        return setting != null && setting.get();
    }

    public boolean isOre(Block block) {
        return oreMap.containsKey(block);
    }

    private void applySodiumCompat() {
        if (!FabricLoader.getInstance().isModLoaded("sodium")) return;
        if (sodiumOptionsRef != null || !sodiumFields.isEmpty()) return;

        Object options = getSodiumOptions();
        if (options == null) {
            ChatUtil.addChatMessage("§eXRay: 检测到Sodium，但无法自动兼容（请手动禁用Sodium再试）");
            return;
        }

        sodiumOptionsRef = options;
        boolean changed = false;

        Map<Object, Boolean> visited = new IdentityHashMap<>();
        for (Object container : new Object[]{
            options,
            getFieldValue(options, "performance"),
            getFieldValue(options, "performanceOptions"),
            getFieldValue(options, "advanced"),
            getFieldValue(options, "advancedOptions"),
            getFieldValue(options, "quality"),
            getFieldValue(options, "qualityOptions")
        }) {
            if (container == null) continue;
            changed |= setBooleanIfPresent(container, visited, "useBlockFaceCulling", false);
            changed |= setBooleanIfPresent(container, visited, "useOcclusionCulling", false);
            changed |= setBooleanIfPresent(container, visited, "useFogOcclusion", false);
            changed |= setBooleanIfPresent(container, visited, "useEntityCulling", false);
        }

        if (!changed) {
            sodiumOptionsRef = null;
            sodiumFields.clear();
            ChatUtil.addChatMessage("§eXRay: Sodium兼容未找到可修改项（可能版本不兼容）");
            return;
        }

        ChatUtil.addChatMessage("§aXRay: 已临时调整Sodium设置以兼容透视");
    }

    private void restoreSodiumCompat() {
        if (sodiumOptionsRef == null && sodiumFields.isEmpty()) return;
        for (Entry<String, BoolField> e : sodiumFields.entrySet()) {
            BoolField bf = e.getValue();
            try {
                bf.field.setBoolean(bf.target, bf.oldValue);
            } catch (Throwable ignored) {
            }
        }
        sodiumFields.clear();
        sodiumOptionsRef = null;
    }

    private Object getSodiumOptions() {
        for (String cls : new String[]{
            "me.jellysquid.mods.sodium.client.SodiumClientMod",
            "net.caffeinemc.mods.sodium.client.SodiumClientMod"
        }) {
            try {
                Class<?> c = Class.forName(cls);
                try {
                    Method m = c.getDeclaredMethod("options");
                    m.setAccessible(true);
                    Object o = m.invoke(null);
                    if (o != null) return o;
                } catch (Throwable ignored) {
                }

                try {
                    Field f = c.getDeclaredField("options");
                    f.setAccessible(true);
                    Object o = f.get(null);
                    if (o != null) return o;
                } catch (Throwable ignored) {
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private Object getFieldValue(Object target, String name) {
        if (target == null) return null;
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean setBooleanIfPresent(Object target, Map<Object, Boolean> visited, String fieldName, boolean value) {
        if (target == null) return false;
        if (visited.putIfAbsent(target, true) != null) return false;

        Field f = findField(target.getClass(), fieldName);
        if (f == null || f.getType() != boolean.class) return false;

        try {
            f.setAccessible(true);
            boolean old = f.getBoolean(target);
            if (old == value) return true;
            String key = target.getClass().getName() + "#" + fieldName;
            sodiumFields.putIfAbsent(key, new BoolField(target, f, old));
            f.setBoolean(target, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Field findField(Class<?> c, String name) {
        Class<?> cur = c;
        while (cur != null && cur != Object.class) {
            try {
                return cur.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable ignored) {
                return null;
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    private static final class BoolField {
        private final Object target;
        private final Field field;
        private final boolean oldValue;

        private BoolField(Object target, Field field, boolean oldValue) {
            this.target = target;
            this.field = field;
            this.oldValue = oldValue;
        }
    }
}
