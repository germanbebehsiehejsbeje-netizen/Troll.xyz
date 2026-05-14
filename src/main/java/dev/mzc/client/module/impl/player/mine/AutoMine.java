package dev.mzc.client.module.impl.player.mine;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.input.MouseButtonEvent;
import dev.mzc.client.events.misc.KeyAction;
import dev.mzc.client.module.impl.client.BaritoneControl;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.Sakura;
import dev.mzc.client.utils.client.ChatUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.ListValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class AutoMine extends Module {
    public enum Mode {
        Ores,
        Area
    }

    private final EnumValue<Mode> mode = new EnumValue<>("Mode", Mode.Ores, Mode.class);

    private final BoolValue diamond = new BoolValue("Diamond", true, () -> mode.is(Mode.Ores));
    private final BoolValue iron = new BoolValue("Iron", false, () -> mode.is(Mode.Ores));
    private final BoolValue gold = new BoolValue("Gold", false, () -> mode.is(Mode.Ores));
    private final BoolValue emerald = new BoolValue("Emerald", false, () -> mode.is(Mode.Ores));
    private final BoolValue redstone = new BoolValue("Redstone", false, () -> mode.is(Mode.Ores));
    private final BoolValue lapis = new BoolValue("Lapis", false, () -> mode.is(Mode.Ores));
    private final BoolValue coal = new BoolValue("Coal", false, () -> mode.is(Mode.Ores));
    private final BoolValue copper = new BoolValue("Copper", false, () -> mode.is(Mode.Ores));
    private final BoolValue quartz = new BoolValue("Quartz", false, () -> mode.is(Mode.Ores));
    private final BoolValue ancientDebris = new BoolValue("AncientDebris", false, () -> mode.is(Mode.Ores));

    private final BoolValue includeDeepslate = new BoolValue("Deepslate", true, () -> mode.is(Mode.Ores));
    private final BoolValue includeBlocks = new BoolValue("IncludeBlocks", false, () -> mode.is(Mode.Ores));
    private final BoolValue autoRestart = new BoolValue("AutoRestart", true);
    private final NumberValue<Integer> restartDelayTicks = new NumberValue<>("RestartDelay", 40, 1, 400, 1, autoRestart::get);

    private final BoolValue dropOnFull = new BoolValue("DropOnFull", true, () -> mode.is(Mode.Ores));
    private final ListValue<Item> dropItems = new ListValue<>("DropItems", () -> mode.is(Mode.Ores) && dropOnFull.get(), ListValue.Type.ITEM);
    private final NumberValue<Integer> stuckSeconds = new NumberValue<>("StuckSeconds", 3, 1, 30, 1, () -> mode.is(Mode.Ores) && dropOnFull.get());
    private final NumberValue<Integer> stuckRepeatSeconds = new NumberValue<>("StuckRepeatSeconds", 3, 1, 30, 1, () -> mode.is(Mode.Ores) && dropOnFull.get());
    private final NumberValue<Integer> stuckMaxRepeats = new NumberValue<>("StuckMaxRepeats", 5, 0, 50, 1, () -> mode.is(Mode.Ores) && dropOnFull.get());

    private int ticksSinceStart;
    private Vec3d lastMovePos;
    private int stillTicks;
    private int lastDropStillTicks;
    private int stuckDropCount;
    private BlockPos areaPos1;
    private BlockPos areaPos2;
    private boolean areaHasRun;
    private BlockPos areaCacheMin;
    private BlockPos areaCacheMax;
    private int areaScanTotal;
    private int areaScanCursor;
    private int areaScanAir;
    private int areaScanAirTmp;
    private float areaProgress;

    private BaritoneControl baritone() {
        if (Sakura.MODULES == null) return null;
        return Sakura.MODULES.getModule(BaritoneControl.class);
    }

    public AutoMine() {
        super("AutoMine", Category.Player);
        this.setType(ModuleType.Hack);
    }

    @Override
    protected void onEnable() {
        ticksSinceStart = 0;
        lastMovePos = null;
        stillTicks = 0;
        lastDropStillTicks = -9999;
        stuckDropCount = 0;
        areaHasRun = false;
        resetAreaScan();
        updateSuffix();
        if (mode.is(Mode.Ores)) {
            if (!startOreMining()) {
                setState(false);
            }
        } else {
            if (areaPos1 == null || areaPos2 == null) {
                ChatUtil.addChatMessage("§eAutoMine(区域): 右键标记两个方块开始挖空");
            } else {
                startAreaClear();
            }
        }
    }

    @Override
    protected void onDisable() {
        ticksSinceStart = 0;
        lastMovePos = null;
        stillTicks = 0;
        lastDropStillTicks = -9999;
        stuckDropCount = 0;
        areaHasRun = false;
        resetAreaScan();
        try {
            IBaritone b = BaritoneAPI.getProvider().getPrimaryBaritone();
            b.getMineProcess().cancel();
            b.getPathingBehavior().cancelEverything();
        } catch (Throwable ignored) {
        }
    }

    @EventHandler
    public void onTick(TickEvent.Post event) {
        if (nullCheck()) return;
        ticksSinceStart++;
        BaritoneControl baritone = baritone();
        boolean pausedByControl = baritone != null && baritone.isPaused();

        if (mode.is(Mode.Ores)) updateStillTicks();
        updateAreaProgress(pausedByControl);
        if (mode.is(Mode.Ores) && dropOnFull.get() && mc.currentScreen == null) {
            if (shouldDropBecauseStuck(pausedByControl)) {
                int maxRepeats = stuckMaxRepeats.get();
                if (maxRepeats != 0 && stuckDropCount >= maxRepeats) {
                    return;
                }

                int intervalTicks = Math.max(1, stuckRepeatSeconds.get() * 20);
                if (stillTicks - lastDropStillTicks >= intervalTicks) {
                    String dropped = dropOneTrashStack();
                    lastDropStillTicks = stillTicks;
                    stuckDropCount++;
                    if (dropped != null) {
                        ChatUtil.addChatMessage("§eAutoMine: 卡住，已丢弃一组 " + dropped);
                    } else {
                        ChatUtil.addChatMessage("§eAutoMine: 卡住，但未找到可丢弃物品");
                    }
                }
            } else if (stillTicks == 0) {
                lastDropStillTicks = -9999;
                stuckDropCount = 0;
            }
        }

        updateSuffix();

        if (!autoRestart.get()) return;
        if (pausedByControl) return;

        if (ticksSinceStart < restartDelayTicks.get()) return;
        ticksSinceStart = 0;

        try {
            IBaritone b = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (mode.is(Mode.Ores)) {
                if (b.getMineProcess().isActive()) return;
            } else {
                if (b.getBuilderProcess().isActive()) return;
                if (areaPos1 == null || areaPos2 == null) return;
                if (areaHasRun) return;
            }
        } catch (Throwable ignored) {
            return;
        }

        if (mode.is(Mode.Ores)) startOreMining();
        else startAreaClear();
    }

    @EventHandler
    public void onMouse(MouseButtonEvent e) {
        if (nullCheck()) return;
        if (!mode.is(Mode.Area)) return;
        if (mc.currentScreen != null) return;
        if (e.getAction() != KeyAction.Press) return;
        if (e.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;

        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = ((BlockHitResult) mc.crosshairTarget).getBlockPos();

        if (areaPos1 == null || (areaPos1 != null && areaPos2 != null)) {
            areaPos1 = pos;
            areaPos2 = null;
            areaHasRun = false;
            ChatUtil.addChatMessage("§aAutoMine(区域): 已设置点1 " + areaPos1.toShortString());
        } else {
            areaPos2 = pos;
            areaHasRun = false;
            ChatUtil.addChatMessage("§aAutoMine(区域): 已设置点2 " + areaPos2.toShortString());
            startAreaClear();
        }

        e.setCancelled(true);
    }

    private boolean startOreMining() {
        try {
            IBaritone b = BaritoneAPI.getProvider().getPrimaryBaritone();
            Block[] blocks = getTargetBlocks();
            if (blocks.length == 0) return false;
            b.getMineProcess().mine(blocks);
            return true;
        } catch (Throwable t) {
            ChatUtil.addChatMessage("§cBaritone not available.");
            return false;
        }
    }

    private boolean startAreaClear() {
        if (areaPos1 == null || areaPos2 == null) return false;
        try {
            IBaritone b = BaritoneAPI.getProvider().getPrimaryBaritone();
            BlockPos min = new BlockPos(
                    Math.min(areaPos1.getX(), areaPos2.getX()),
                    Math.min(areaPos1.getY(), areaPos2.getY()),
                    Math.min(areaPos1.getZ(), areaPos2.getZ())
            );
            BlockPos max = new BlockPos(
                    Math.max(areaPos1.getX(), areaPos2.getX()),
                    Math.max(areaPos1.getY(), areaPos2.getY()),
                    Math.max(areaPos1.getZ(), areaPos2.getZ())
            );
            b.getBuilderProcess().clearArea(min, max);
            areaHasRun = true;
            resetAreaScan();
            ChatUtil.addChatMessage("§eAutoMine(区域): 开始挖空 " + min.toShortString() + " -> " + max.toShortString());
            return true;
        } catch (Throwable t) {
            ChatUtil.addChatMessage("§cBaritone not available.");
            return false;
        }
    }

    private void stopMiningNow() {
        try {
            IBaritone b = BaritoneAPI.getProvider().getPrimaryBaritone();
            b.getMineProcess().cancel();
            b.getPathingBehavior().cancelEverything();
        } catch (Throwable ignored) {
        }
    }

    private void updateStillTicks() {
        if (mc.player == null) return;
        Vec3d now = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        if (lastMovePos == null) {
            lastMovePos = now;
            stillTicks = 0;
            return;
        }

        double d2 = now.squaredDistanceTo(lastMovePos);
        if (d2 > 0.0004) {
            lastMovePos = now;
            stillTicks = 0;
            lastDropStillTicks = -9999;
            stuckDropCount = 0;
        } else {
            stillTicks++;
        }
    }

    private boolean shouldDropBecauseStuck(boolean pausedByControl) {
        if (mc.player == null) return false;
        int thresholdTicks = Math.max(1, stuckSeconds.get() * 20);
        if (stillTicks < thresholdTicks) return false;
        return !pausedByControl;
    }

    private String dropOneTrashStack() {
        if (mc.player == null || mc.interactionManager == null) return null;

        int syncId = mc.player.currentScreenHandler.syncId;

        for (int invSlot = 9; invSlot < 36; invSlot++) {
            ItemStack s = mc.player.getInventory().getStack(invSlot);
            if (isTrash(s)) {
                mc.interactionManager.clickSlot(syncId, invSlot, 1, SlotActionType.THROW, mc.player);
                return s.getName().getString();
            }
        }

        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            ItemStack s = mc.player.getInventory().getStack(hotbarSlot);
            if (isTrash(s)) {
                int screenSlot = 36 + hotbarSlot;
                mc.interactionManager.clickSlot(syncId, screenSlot, 1, SlotActionType.THROW, mc.player);
                return s.getName().getString();
            }
        }

        return null;
    }

    private boolean isTrash(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!dropItems.get().isEmpty()) {
            return dropItems.contains(stack.getItem());
        }
        return stack.isOf(Blocks.GRANITE.asItem())
                || stack.isOf(Blocks.DIORITE.asItem())
                || stack.isOf(Blocks.TUFF.asItem());
    }

    private Block[] getTargetBlocks() {
        boolean deep = includeDeepslate.get();
        boolean blocks = includeBlocks.get();
        List<Block> out = new ArrayList<>();

        if (diamond.get()) {
            out.add(Blocks.DIAMOND_ORE);
            if (deep) out.add(Blocks.DEEPSLATE_DIAMOND_ORE);
            if (blocks) out.add(Blocks.DIAMOND_BLOCK);
        }
        if (iron.get()) {
            out.add(Blocks.IRON_ORE);
            if (deep) out.add(Blocks.DEEPSLATE_IRON_ORE);
            if (blocks) out.add(Blocks.IRON_BLOCK);
        }
        if (gold.get()) {
            out.add(Blocks.GOLD_ORE);
            if (deep) out.add(Blocks.DEEPSLATE_GOLD_ORE);
            out.add(Blocks.NETHER_GOLD_ORE);
            if (blocks) out.add(Blocks.GOLD_BLOCK);
        }
        if (emerald.get()) {
            out.add(Blocks.EMERALD_ORE);
            if (deep) out.add(Blocks.DEEPSLATE_EMERALD_ORE);
            if (blocks) out.add(Blocks.EMERALD_BLOCK);
        }
        if (redstone.get()) {
            out.add(Blocks.REDSTONE_ORE);
            if (deep) out.add(Blocks.DEEPSLATE_REDSTONE_ORE);
            if (blocks) out.add(Blocks.REDSTONE_BLOCK);
        }
        if (lapis.get()) {
            out.add(Blocks.LAPIS_ORE);
            if (deep) out.add(Blocks.DEEPSLATE_LAPIS_ORE);
            if (blocks) out.add(Blocks.LAPIS_BLOCK);
        }
        if (coal.get()) {
            out.add(Blocks.COAL_ORE);
            if (deep) out.add(Blocks.DEEPSLATE_COAL_ORE);
            if (blocks) out.add(Blocks.COAL_BLOCK);
        }
        if (copper.get()) {
            out.add(Blocks.COPPER_ORE);
            if (deep) out.add(Blocks.DEEPSLATE_COPPER_ORE);
            if (blocks) out.add(Blocks.COPPER_BLOCK);
        }
        if (quartz.get()) {
            out.add(Blocks.NETHER_QUARTZ_ORE);
            if (blocks) out.add(Blocks.QUARTZ_BLOCK);
        }
        if (ancientDebris.get()) {
            out.add(Blocks.ANCIENT_DEBRIS);
        }

        return out.toArray(new Block[0]);
    }

    private void updateSuffix() {
        BaritoneControl baritone = baritone();
        boolean pausedByControl = baritone != null && baritone.isPaused();

        int enabled = 0;
        if (diamond.get()) enabled++;
        if (iron.get()) enabled++;
        if (gold.get()) enabled++;
        if (emerald.get()) enabled++;
        if (redstone.get()) enabled++;
        if (lapis.get()) enabled++;
        if (coal.get()) enabled++;
        if (copper.get()) enabled++;
        if (quartz.get()) enabled++;
        if (ancientDebris.get()) enabled++;
        String base = mode.is(Mode.Ores) ? (enabled + "") : "AREA";
        setSuffix(pausedByControl ? (base + "|PAUSE") : base);
    }

    public Mode getModeValue() {
        return mode.get();
    }

    public boolean isWorking() {
        if (!isEnabled()) return false;
        BaritoneControl baritone = baritone();
        if (baritone != null && baritone.isPaused()) return false;
        try {
            IBaritone b = BaritoneAPI.getProvider().getPrimaryBaritone();
            return mode.is(Mode.Ores) ? b.getMineProcess().isActive() : b.getBuilderProcess().isActive();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public float getAreaProgress() {
        if (!mode.is(Mode.Area)) return 0f;
        return Math.max(0f, Math.min(1f, areaProgress));
    }

    private void resetAreaScan() {
        areaCacheMin = null;
        areaCacheMax = null;
        areaScanTotal = 0;
        areaScanCursor = 0;
        areaScanAir = 0;
        areaScanAirTmp = 0;
        areaProgress = 0f;
    }

    private void updateAreaProgress(boolean pausedByControl) {
        if (pausedByControl) return;
        if (!mode.is(Mode.Area)) {
            resetAreaScan();
            return;
        }
        if (areaPos1 == null || areaPos2 == null || mc.world == null) {
            resetAreaScan();
            return;
        }

        boolean active;
        try {
            IBaritone b = BaritoneAPI.getProvider().getPrimaryBaritone();
            active = b.getBuilderProcess().isActive();
        } catch (Throwable ignored) {
            resetAreaScan();
            return;
        }
        if (!active) {
            resetAreaScan();
            return;
        }

        BlockPos min = new BlockPos(
                Math.min(areaPos1.getX(), areaPos2.getX()),
                Math.min(areaPos1.getY(), areaPos2.getY()),
                Math.min(areaPos1.getZ(), areaPos2.getZ())
        );
        BlockPos max = new BlockPos(
                Math.max(areaPos1.getX(), areaPos2.getX()),
                Math.max(areaPos1.getY(), areaPos2.getY()),
                Math.max(areaPos1.getZ(), areaPos2.getZ())
        );

        if (areaCacheMin == null || areaCacheMax == null || !areaCacheMin.equals(min) || !areaCacheMax.equals(max)) {
            areaCacheMin = min;
            areaCacheMax = max;
            long lx = (long) (max.getX() - min.getX() + 1);
            long ly = (long) (max.getY() - min.getY() + 1);
            long lz = (long) (max.getZ() - min.getZ() + 1);
            long total = lx * ly * lz;
            if (total <= 0 || total > Integer.MAX_VALUE) {
                resetAreaScan();
                return;
            }
            areaScanTotal = (int) total;
            areaScanCursor = 0;
            areaScanAir = 0;
            areaScanAirTmp = 0;
            areaProgress = 0f;
        }

        if (areaScanTotal <= 0) return;
        int xLen = areaCacheMax.getX() - areaCacheMin.getX() + 1;
        int yLen = areaCacheMax.getY() - areaCacheMin.getY() + 1;
        int zLen = areaCacheMax.getZ() - areaCacheMin.getZ() + 1;
        int layer = xLen * yLen;

        int batch = 2000;
        for (int i = 0; i < batch; i++) {
            if (areaScanCursor >= areaScanTotal) {
                areaScanAir = areaScanAirTmp;
                areaScanAirTmp = 0;
                areaScanCursor = 0;
                break;
            }
            int idx = areaScanCursor++;
            int z = idx / layer;
            int rem = idx - z * layer;
            int y = rem / xLen;
            int x = rem - y * xLen;
            BlockPos p = areaCacheMin.add(x, y, z);
            if (mc.world.getBlockState(p).isAir()) areaScanAirTmp++;
        }

        int denom = Math.max(1, areaScanCursor);
        areaProgress = areaScanAirTmp / (float) denom;
        if (areaScanCursor == 0 && areaScanTotal > 0) {
            areaProgress = areaScanAir / (float) areaScanTotal;
        }
    }
}
