package dev.mzc.client.module.impl.player.inventory;

import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.math.MathUtil;
import dev.mzc.client.utils.player.InvUtil;
import dev.mzc.client.utils.time.TimerUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import dev.mzc.client.values.impl.RangeValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.*;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ChestStealer extends Module {
    public ChestStealer() {
        super("ChestStealer", Category.Player);
        this.setType(ModuleType.Safe);
    }

    public enum Mode {
        QuickMove(),
        Human();
        Mode() {
        }
    }

    private final RangeValue<Integer> delay = new RangeValue<>("Delay", 90, 140, 0, 800, 1);
    private final RangeValue<Integer> delay2 = new RangeValue<>("Delay2", 90, 140, 0, 800, 1);
    private final NumberValue<Integer> openDelayTicks = new NumberValue<>("Open Delay", 1, 0, 20, 1);
    private final NumberValue<Integer> missClickChance = new NumberValue<>("Miss Click Chance", 10, 0, 100, 1);
    private final NumberValue<Integer> missClickChance2 = new NumberValue<>("Miss Click Chance 2", 10, 0, 100, 1);
    public final BoolValue onlyImportant = new BoolValue("ImportantOnly", true);
    public final BoolValue pickEnderChest = new BoolValue("EnderChest", false);
    private final BoolValue autoClose = new BoolValue("AutoCloseGUI", true);
    private final EnumValue<Mode> mode = new EnumValue<>("Mode", Mode.QuickMove);

    private final TimerUtil timer = new TimerUtil();
    private Screen lastTickScreen;
    private int openDelayLeft;
    private boolean missClicked = false;
    private boolean usingSecondConfig = false;
    private long lastConfigSwitchMs = 0L;
    private int totalPlaceableBlockCount = 0;

    @Override
    public void onEnable() {
        lastTickScreen = null;
        openDelayLeft = 0;
        missClicked = false;
        usingSecondConfig = false;
        lastConfigSwitchMs = System.currentTimeMillis();
        timer.reset();
    }

    @Override
    public void onDisable() {
        lastTickScreen = null;
        openDelayLeft = 0;
        missClicked = false;
        usingSecondConfig = false;
        lastConfigSwitchMs = 0L;
        timer.reset();
    }

    @EventHandler
    public void onTick(TickEvent.Pre e) {
        if (nullCheck()) return;
        long now = System.currentTimeMillis();
        if (lastConfigSwitchMs == 0L) lastConfigSwitchMs = now;
        long switchInterval = usingSecondConfig ? 25000L : 40000L;
        if (now - lastConfigSwitchMs >= switchInterval) {
            usingSecondConfig = !usingSecondConfig;
            lastConfigSwitchMs = now;
            timer.reset();
            missClicked = false;
        }
        Screen current = mc.currentScreen;
        if (!(current instanceof GenericContainerScreen container)) return;
        totalPlaceableBlockCount = countPlaceableBlocksInInventory();
        GenericContainerScreenHandler menu = container.getScreenHandler();

        if (current != lastTickScreen) {
            timer.reset();
            openDelayLeft = Math.max(0, openDelayTicks.get());
            missClicked = false;
        } else {
            if (openDelayLeft > 0) {
                openDelayLeft--;
                lastTickScreen = current;
                return;
            }
            String title = container.getTitle().getString();
            String chest = Text.translatable("container.chest").getString();
            String largeChest = Text.translatable("container.chestDouble").getString();
            String enderChest = Text.translatable("container.enderchest").getString();

            boolean isNormalChest = title.equals(chest) || title.equals(largeChest) || title.equals("Chest");
            boolean isEnderChest = title.equals(enderChest);
            if (!(isNormalChest || (pickEnderChest.get() && isEnderChest))) return;

            if (autoClose.get() && isChestEmpty(menu) && timer.passedMS(getRandomDelayMs())) {
                mc.player.closeHandledScreen();
            } else {
                boolean inventoryFull = InvUtil.isInventoryFull();
                Integer mergeSlot = findMergeableChestSlot(menu);
                if (mergeSlot != null) {
                    if (!timer.passedMS(getRandomDelayMs())) {
                        lastTickScreen = current;
                        return;
                    }
                    
                    // Miss click logic
                    if (!missClicked && MathUtil.getRandom(0, 100) < getMissClickChance()) {
                        int emptySlot = findEmptyPlayerSlot(menu);
                        if (emptySlot != -1) {
                            mc.interactionManager.clickSlot(menu.syncId, emptySlot, 0, SlotActionType.PICKUP, mc.player);
                            mc.interactionManager.clickSlot(menu.syncId, emptySlot, 0, SlotActionType.PICKUP, mc.player);
                        }
                        missClicked = true;
                        timer.reset();
                        lastTickScreen = current;
                        return;
                    }
                    missClicked = false;

                    if (stealSlot(menu, mergeSlot)) {
                        timer.reset();
                        lastTickScreen = current;
                        return;
                    }
                }

                if (inventoryFull) {
                    lastTickScreen = current;
                    return;
                }

                List<Integer> slots = IntStream.range(0, menu.getRows() * 9).boxed().collect(Collectors.toList());
                Collections.shuffle(slots);
                for (Integer pSlotId : slots) {
                    ItemStack stack = menu.getSlot(pSlotId).getStack();
                    if (stack.isEmpty()) continue;
                    if (!timer.passedMS(getRandomDelayMs())) break;
                    
                    // Miss click logic inside loop
                    if (!missClicked && MathUtil.getRandom(0, 100) < getMissClickChance()) {
                        int emptySlot = findEmptyPlayerSlot(menu);
                        if (emptySlot != -1) {
                            mc.interactionManager.clickSlot(menu.syncId, emptySlot, 0, SlotActionType.PICKUP, mc.player);
                            mc.interactionManager.clickSlot(menu.syncId, emptySlot, 0, SlotActionType.PICKUP, mc.player);
                        }
                        missClicked = true;
                        timer.reset();
                        break;
                    }
                    missClicked = false;

                    if (isItemUseful(stack) && isBestItemInChest(menu, stack)) {
                        if (stealSlot(menu, pSlotId)) {
                            timer.reset();
                            break;
                        }
                    }
                }
            }
        }
        lastTickScreen = current;
    }

    private int getMinDelayMs() {
        return usingSecondConfig ? delay2.getMinValue() : delay.getMinValue();
    }

    private int getMaxDelayMs() {
        return usingSecondConfig ? delay2.getMaxValue() : delay.getMaxValue();
    }

    private int getRandomDelayMs() {
        return MathUtil.getRandom(getMinDelayMs(), getMaxDelayMs());
    }

    private int getMissClickChance() {
        return usingSecondConfig ? missClickChance2.get() : missClickChance.get();
    }

    private boolean stealSlot(GenericContainerScreenHandler menu, int chestSlotId) {
        ItemStack chestStack = menu.getSlot(chestSlotId).getStack();
        if (mode.is(Mode.QuickMove)) {
            if (canMergeWithInventory(menu, chestStack)) {
                return mergeIntoInventory(menu, chestSlotId);
            }
            mc.interactionManager.clickSlot(menu.syncId, chestSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);
            return true;
        }

        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            ItemStack cursor = mc.player.currentScreenHandler.getCursorStack();
            int clearSlotId = findMergeOrEmptyPlayerSlot(menu, cursor, -1);
            if (clearSlotId == -1) return false;
            mc.interactionManager.clickSlot(menu.syncId, clearSlotId, 0, SlotActionType.PICKUP, mc.player);
            if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) return false;
        }

        int targetSlotId = findMergeOrEmptyPlayerSlot(menu, chestStack, -1);
        if (targetSlotId == -1) return false;

        mc.interactionManager.clickSlot(menu.syncId, chestSlotId, 0, SlotActionType.PICKUP, mc.player);
        if (mc.player.currentScreenHandler.getCursorStack().isEmpty()) return false;

        mc.interactionManager.clickSlot(menu.syncId, targetSlotId, 0, SlotActionType.PICKUP, mc.player);
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            ItemStack cursor = mc.player.currentScreenHandler.getCursorStack();
            int newTarget = findMergeOrEmptyPlayerSlot(menu, cursor, targetSlotId);
            if (newTarget != -1) {
                mc.interactionManager.clickSlot(menu.syncId, newTarget, 0, SlotActionType.PICKUP, mc.player);
            }
            if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                mc.interactionManager.clickSlot(menu.syncId, chestSlotId, 0, SlotActionType.PICKUP, mc.player);
                return false;
            }
        }

        return true;
    }

    private boolean canMergeWithInventory(GenericContainerScreenHandler menu, ItemStack stack) {
        if (stack.isEmpty() || !stack.isStackable()) return false;
        return findMergePlayerSlot(menu, stack, -1) != -1;
    }

    private Integer findMergeableChestSlot(GenericContainerScreenHandler menu) {
        int chestSlots = menu.getRows() * 9;
        for (int i = 0; i < chestSlots; i++) {
            ItemStack stack = menu.getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            if (canMergeWithInventory(menu, stack)) {
                return i;
            }
        }
        return null;
    }

    private boolean mergeIntoInventory(GenericContainerScreenHandler menu, int chestSlotId) {
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) return false;

        ItemStack chestStack = menu.getSlot(chestSlotId).getStack();
        int targetSlotId = findMergePlayerSlot(menu, chestStack, -1);
        if (targetSlotId == -1) return false;

        mc.interactionManager.clickSlot(menu.syncId, chestSlotId, 0, SlotActionType.PICKUP, mc.player);
        if (mc.player.currentScreenHandler.getCursorStack().isEmpty()) return false;

        int lastMergeSlot = -1;
        while (true) {
            ItemStack cursor = mc.player.currentScreenHandler.getCursorStack();
            int mergeSlot = findMergePlayerSlot(menu, cursor, lastMergeSlot);
            if (mergeSlot == -1) break;
            lastMergeSlot = mergeSlot;
            mc.interactionManager.clickSlot(menu.syncId, mergeSlot, 0, SlotActionType.PICKUP, mc.player);
            if (mc.player.currentScreenHandler.getCursorStack().isEmpty()) return true;
        }

        mc.interactionManager.clickSlot(menu.syncId, chestSlotId, 0, SlotActionType.PICKUP, mc.player);
        return false;
    }

    private int findEmptyPlayerSlot(GenericContainerScreenHandler menu) {
        int playerStart = menu.getRows() * 9;
        int playerEnd = Math.min(playerStart + 36, menu.slots.size());
        for (int i = playerStart; i < playerEnd; i++) {
            if (menu.getSlot(i).getStack().isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private int findMergeOrEmptyPlayerSlot(GenericContainerScreenHandler menu, ItemStack stack, int excludeSlotId) {
        int mergeSlot = findMergePlayerSlot(menu, stack, excludeSlotId);
        if (mergeSlot != -1) return mergeSlot;
        return findEmptyPlayerSlot(menu);
    }

    private int findMergePlayerSlot(GenericContainerScreenHandler menu, ItemStack stack, int excludeSlotId) {
        int playerStart = menu.getRows() * 9;
        int playerEnd = Math.min(playerStart + 36, menu.slots.size());
        int bestMergeSlot = -1;
        int bestCount = -1;
        if (!stack.isEmpty() && stack.isStackable()) {
            for (int i = playerStart; i < playerEnd; i++) {
                if (i == excludeSlotId) continue;
                ItemStack invStack = menu.getSlot(i).getStack();
                if (invStack.isEmpty()) continue;
                if (ItemStack.areItemsAndComponentsEqual(invStack, stack) && invStack.getCount() < invStack.getMaxCount()) {
                    int count = invStack.getCount();
                    if (count > bestCount) {
                        bestCount = count;
                        bestMergeSlot = i;
                    }
                }
            }
        }
        return bestMergeSlot;
    }

    private boolean isItemUseful(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (isMineral(stack.getItem())) return false;
        if (stack.getItem() instanceof BlockItem && InvUtil.isBlockPlaceable(stack)) {
            if (isLowValueFillBlock(stack) && totalPlaceableBlockCount >= 96) {
                return false;
            }
        }
        if (!onlyImportant.get()) return true;

        Item item = stack.getItem();
        if (InvHelper.isGodItem(stack) || InvHelper.isSharpnessAxe(stack)) return true;
        if (InvHelper.isArmor(stack)) {
            var equippable = stack.get(DataComponentTypes.EQUIPPABLE);
            if (equippable == null) return false;
            float protection = InvHelper.getProtection(stack);
            float bestArmor = InvHelper.getBestArmorScore(equippable.slot());
            return protection > bestArmor;
        }
        if (stack.isIn(ItemTags.SWORDS)) {
            return InvHelper.getSwordDamage(stack) > InvHelper.getBestSwordDamage();
        }
        if (stack.isIn(ItemTags.PICKAXES)) {
            return InvHelper.getToolScore(stack) > InvHelper.getBestPickaxeScore();
        }
        if (item instanceof AxeItem) {
            return InvHelper.getToolScore(stack) > InvHelper.getBestAxeScore();
        }
        if (item instanceof ShovelItem) {
            return InvHelper.getToolScore(stack) > InvHelper.getBestShovelScore();
        }
        if (item instanceof CrossbowItem) {
            return InvHelper.getCrossbowScore(stack) > InvHelper.getBestCrossbowScore();
        }
        if (item instanceof BowItem && InvHelper.isPunchBow(stack)) {
            return InvHelper.getPunchBowScore(stack) > InvHelper.getBestPunchBowScore();
        }
        if (item instanceof BowItem && InvHelper.isPowerBow(stack)) {
            return InvHelper.getPowerBowScore(stack) > InvHelper.getBestPowerBowScore();
        }
        if (stack.isIn(ItemTags.SWORDS) ||
                InvHelper.isArmor(stack) ||
                item instanceof BowItem ||
                item instanceof CrossbowItem ||
                item instanceof FishingRodItem ||
                item instanceof EnderPearlItem ||
                item instanceof AxeItem ||
                stack.isIn(ItemTags.PICKAXES) ||
                item instanceof ShovelItem ||
                item == Items.TNT ||
                item instanceof PotionItem ||
                item.getComponents().contains(DataComponentTypes.FOOD) ||
                item == Items.WATER_BUCKET ||
                item == Items.COBWEB ||
                item == Items.TOTEM_OF_UNDYING ||
                item == Items.END_CRYSTAL ||
                (item instanceof BlockItem && InvUtil.isBlockPlaceable(stack))) {
            return true;
        }
        return !stack.contains(DataComponentTypes.CUSTOM_NAME) && InvHelper.isCommonItemUseful(stack);
    }

    private int countPlaceableBlocksInInventory() {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack st = mc.player.getInventory().getStack(i);
            if (st.isEmpty()) continue;
            if (!(st.getItem() instanceof BlockItem)) continue;
            if (!InvUtil.isBlockPlaceable(st)) continue;
            int add = st.getCount();
            if (st.isIn(ItemTags.LOGS)) add *= 4;
            count += add;
        }
        return count;
    }

    private boolean isLowValueFillBlock(ItemStack st) {
        Item item = st.getItem();
        return item == Items.DIRT
                || item == Items.GRASS_BLOCK
                || item == Items.COARSE_DIRT
                || item == Items.ROOTED_DIRT
                || item == Items.PODZOL
                || item == Items.MYCELIUM
                || item == Items.MUD
                || item == Items.PACKED_MUD;
    }

    private static boolean isMineral(Item item) {
        return item == Items.DIAMOND
                || item == Items.DIAMOND_BLOCK
                || item == Items.DIAMOND_ORE
                || item == Items.DEEPSLATE_DIAMOND_ORE
                || item == Items.IRON_INGOT
                || item == Items.IRON_NUGGET
                || item == Items.IRON_BLOCK
                || item == Items.RAW_IRON
                || item == Items.RAW_IRON_BLOCK
                || item == Items.IRON_ORE
                || item == Items.DEEPSLATE_IRON_ORE
                || item == Items.GOLD_INGOT
                || item == Items.GOLD_NUGGET
                || item == Items.GOLD_BLOCK
                || item == Items.RAW_GOLD
                || item == Items.RAW_GOLD_BLOCK
                || item == Items.GOLD_ORE
                || item == Items.DEEPSLATE_GOLD_ORE
                || item == Items.NETHER_GOLD_ORE
                || item == Items.COPPER_INGOT
                || item == Items.COPPER_BLOCK
                || item == Items.RAW_COPPER
                || item == Items.RAW_COPPER_BLOCK
                || item == Items.COPPER_ORE
                || item == Items.DEEPSLATE_COPPER_ORE
                || item == Items.EMERALD
                || item == Items.EMERALD_BLOCK
                || item == Items.EMERALD_ORE
                || item == Items.DEEPSLATE_EMERALD_ORE
                || item == Items.COAL
                || item == Items.COAL_BLOCK
                || item == Items.COAL_ORE
                || item == Items.DEEPSLATE_COAL_ORE
                || item == Items.REDSTONE
                || item == Items.REDSTONE_BLOCK
                || item == Items.REDSTONE_ORE
                || item == Items.DEEPSLATE_REDSTONE_ORE
                || item == Items.LAPIS_LAZULI
                || item == Items.LAPIS_BLOCK
                || item == Items.LAPIS_ORE
                || item == Items.DEEPSLATE_LAPIS_ORE
                || item == Items.QUARTZ
                || item == Items.QUARTZ_BLOCK
                || item == Items.NETHER_QUARTZ_ORE
                || item == Items.AMETHYST_SHARD
                || item == Items.NETHERITE_INGOT
                || item == Items.NETHERITE_SCRAP
                || item == Items.NETHERITE_BLOCK
                || item == Items.ANCIENT_DEBRIS;
    }

    private boolean isBestItemInChest(GenericContainerScreenHandler menu, ItemStack stack) {
        if (!InvHelper.isGodItem(stack) && !InvHelper.isSharpnessAxe(stack)) {
            for (int i = 0; i < menu.getRows() * 9; i++) {
                ItemStack checkStack = menu.getSlot(i).getStack();
                if (InvHelper.isArmor(stack) && InvHelper.isArmor(checkStack)) {
                    var stackEquippable = stack.get(DataComponentTypes.EQUIPPABLE);
                    var checkEquippable = checkStack.get(DataComponentTypes.EQUIPPABLE);
                    if (stackEquippable != null && checkEquippable != null
                            && stackEquippable.slot() == checkEquippable.slot()
                            && InvHelper.getProtection(checkStack) > InvHelper.getProtection(stack)) {
                        return false;
                    }
                } else if (stack.isIn(ItemTags.SWORDS) && checkStack.isIn(ItemTags.SWORDS)) {
                    if (InvHelper.getSwordDamage(checkStack) > InvHelper.getSwordDamage(stack)) {
                        return false;
                    }
                } else if (stack.isIn(ItemTags.PICKAXES) && checkStack.isIn(ItemTags.PICKAXES)) {
                    if (InvHelper.getToolScore(checkStack) > InvHelper.getToolScore(stack)) {
                        return false;
                    }
                } else if (stack.getItem() instanceof AxeItem && checkStack.getItem() instanceof AxeItem) {
                    if (InvHelper.getToolScore(checkStack) > InvHelper.getToolScore(stack)) {
                        return false;
                    }
                } else if (stack.getItem() instanceof ShovelItem
                        && checkStack.getItem() instanceof ShovelItem
                        && InvHelper.getToolScore(checkStack) > InvHelper.getToolScore(stack)) {
                    return false;
                }
            }
            return true;
        } else {
            return true;
        }
    }

    private boolean isChestEmpty(GenericContainerScreenHandler menu) {
        for (int i = 0; i < menu.getRows() * 9; i++) {
            ItemStack item = menu.getSlot(i).getStack();
            if (!item.isEmpty() && (canMergeWithInventory(menu, item) || (isItemUseful(item) && isBestItemInChest(menu, item)))) {
                return false;
            }
        }
        return true;
    }
}
