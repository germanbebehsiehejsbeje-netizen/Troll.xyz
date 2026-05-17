package dev.mzc.client.module.impl.player;

import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.manager.Managers;
import dev.mzc.client.manager.impl.RotationManager;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.module.impl.player.autobrew.BrewRecipe;
import dev.mzc.client.utils.rotation.MovementFix;
import dev.mzc.client.utils.rotation.RotationUtil;
import dev.mzc.client.utils.vector.Rotation;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.BrewingStandScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.BrewingStandScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AutoBrew — automatically brews potions in a loop using nearby chests + brewing stands.
 *
 * Flow:
 *   1. Scan all brewing stands within range. Prioritize stands that have FINISHED brewing
 *      (have brewed potions inside but ingredient slot empty + brewTime=0).
 *   2. For finished stands → retrieve potions.
 *   3. For empty stands → check inventory; if we have what we need, fill it and start brewing.
 *   4. If we need ingredients we don't have → scan chests for them. If we have everything,
 *      we don't bother chests.
 *   5. Loop until disabled.
 *
 * Always tops up blaze powder in stands' fuel slots.
 */
public class AutoBrew extends Module {

    public enum Goal { Strength_II, Speed_II, Invisibility_8m, FireResistance_8m }

    public final EnumValue<Goal> goal = new EnumValue<>("Goal", Goal.Strength_II);
    public final NumberValue<Double> reach = new NumberValue<>("Reach", 4.5, 2.0, 6.0, 0.1);
    public final NumberValue<Double> scanRadius = new NumberValue<>("ScanRadius", 5.0, 2.0, 16.0, 0.5);
    public final NumberValue<Double> rotationSpeed = new NumberValue<>("RotationSpeed", 1.0, 0.1, 1.0, 0.05);
    public final NumberValue<Integer> tickDelay = new NumberValue<>("TickDelay", 4, 1, 20, 1);
    public final NumberValue<Integer> minFuel = new NumberValue<>("MinFuelLevel", 5, 0, 19, 1);
    public final BoolValue silent = new BoolValue("Silent", true);
    public final BoolValue stackablePotions = new BoolValue("StackablePotions", false);

    /* Brewing stand slot indices */
    private static final int STAND_BOTTLE_0 = 0;
    private static final int STAND_BOTTLE_1 = 1;
    private static final int STAND_BOTTLE_2 = 2;
    private static final int STAND_INGREDIENT = 3;
    private static final int STAND_FUEL = 4;

    private enum State {
        IDLE,
        OPEN_CHEST, LOOT_CHEST, CLOSE_CHEST,
        OPEN_STAND, RETRIEVE, INSERT_FUEL, INSERT_BOTTLES, WAIT_BOTTLES, INSERT_INGREDIENT, CLOSE_STAND
    }

    private State state = State.IDLE;
    private int delayTicks = 0;
    /** How many ticks we've already spent in WAIT_BOTTLES, to give up if server is slow/unresponsive. */
    private int waitBottlesTicks = 0;
    /** Inventory fingerprint snapshot when we opened the current chest, to detect "took nothing". */
    private int chestOpenFingerprint = 0;

    private BlockPos targetChest;
    private BlockPos targetStand;

    /** Items the recipe needs that we don't currently hold — used for chest scans. */
    private final Set<Item> neededItems = new HashSet<>();

    /**
     * Brewing stands we recently filled with ingredients; key = pos, value = epoch ms when
     * the brew should be done (~ +20s after we close). Until that time we treat the stand as
     * busy and skip it. Vanilla brew time is 400 ticks (20s) but we add a small margin.
     */
    private final java.util.Map<BlockPos, Long> brewingUntil = new java.util.HashMap<>();
    private static final long BREW_DURATION_MS = 21_000L;

    /**
     * Brewing stands that we opened, found EMPTY (no potions, no useful state) and couldn't
     * load (no inventory items). We skip them until our inventory fingerprint changes, so we
     * don't loop on the same useless stand every tick.
     */
    private final java.util.Set<BlockPos> emptyStandBlacklist = new java.util.HashSet<>();
    /**
     * Chests we opened and found nothing useful in. Same fingerprint reset rules as the stand
     * blacklist.
     */
    private final java.util.Set<BlockPos> emptyChestBlacklist = new java.util.HashSet<>();
    private int blacklistFingerprint = 0;

    public AutoBrew() {
        super("AutoBrew", Category.Player);
        this.setType(ModuleType.Hack);
    }

    @Override
    public void onEnable() {
        state = State.IDLE;
        delayTicks = 0;
        targetChest = null;
        targetStand = null;
        neededItems.clear();
        brewingUntil.clear();
        emptyStandBlacklist.clear();
        emptyChestBlacklist.clear();
        blacklistFingerprint = 0;
    }

    @Override
    public void onDisable() {
        if (mc.player != null && mc.currentScreen instanceof HandledScreen<?>) {
            mc.player.closeHandledScreen();
        }
        brewingUntil.clear();
        emptyStandBlacklist.clear();
        emptyChestBlacklist.clear();
    }

    @Override
    public String getSuffix() { return goal.get().name(); }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        if (delayTicks > 0) { delayTicks--; return; }

        switch (state) {
            case IDLE -> doIdle();
            case OPEN_CHEST -> doOpenChest();
            case LOOT_CHEST -> doLootChest();
            case CLOSE_CHEST -> doCloseChest();
            case OPEN_STAND -> doOpenStand();
            case RETRIEVE -> doRetrieve();
            case INSERT_FUEL -> doInsertFuel();
            case INSERT_BOTTLES -> doInsertBottles();
            case WAIT_BOTTLES -> doWaitBottles();
            case INSERT_INGREDIENT -> doInsertIngredient();
            case CLOSE_STAND -> doCloseStand();
        }
    }

    /* =================================================================== IDLE */

    private void doIdle() {
        BrewRecipe.Recipe recipe = currentRecipe();

        long now = System.currentTimeMillis();
        // Drop expired entries (those whose brew should be done by now).
        brewingUntil.entrySet().removeIf(e -> now >= e.getValue());

        // Drop empty-stand blacklist entries when inventory contents change (so we re-check
        // the stand once we actually have something to put in it).
        int fp = computeInventoryFingerprint();
        if (fp != blacklistFingerprint) {
            emptyStandBlacklist.clear();
            emptyChestBlacklist.clear();
            blacklistFingerprint = fp;
        }

        boolean canDoAnythingAtStand = canDoAnythingAtStand(recipe);

        // 1) If we have something useful for a stand (water+ingredient, or even just fuel), or
        //    if there is a stand whose brew timer JUST expired (likely has potions), open the
        //    closest unblacklisted stand. Otherwise skip stands and go fetch from chest.
        BlockPos finished = findFinishedStandCandidate(canDoAnythingAtStand);
        if (finished != null) {
            targetStand = finished;
            state = State.OPEN_STAND;
            delayTicks = tickDelay.get();
            return;
        }

        // 2) Pick the next recipe step from inventory.
        BrewRecipe.Step nextStep = pickNextStep(recipe);
        if (nextStep != null) {
            BlockPos stand = findFreeBrewingStand();
            if (stand == null) {
                // All stands either busy, blacklisted, or absent — wait.
                delayTicks = tickDelay.get();
                return;
            }
            targetStand = stand;
            state = State.OPEN_STAND;
            delayTicks = tickDelay.get();
            return;
        }

        // 3) Need ingredients we don't have.
        updateNeededItems(recipe);
        boolean missingWater = countInInventory(Items.POTION, Potions.WATER) < 3;
        boolean missingBlaze = countInInventory(Items.BLAZE_POWDER) < 1;
        if (!neededItems.isEmpty() || missingWater || missingBlaze) {
            BlockPos chest = findNearestChest();
            if (chest != null) {
                targetChest = chest;
                state = State.OPEN_CHEST;
                delayTicks = tickDelay.get();
                return;
            }
        }

        delayTicks = tickDelay.get();
    }

    /**
     * True if the inventory has anything that is useful to put into a brewing stand right now —
     * either we have a complete next-step (water+ingredient), or we at least have blaze powder
     * which could be used to top up a stand's fuel, or we have any recipe ingredient that
     * could finish a half-brewed potion already sitting in some stand. Used to gate "should we
     * walk to a stand at all".
     *
     * Also returns true if any stand has a brew timer that has just expired (likely has potions
     * to retrieve).
     */
    private boolean canDoAnythingAtStand(BrewRecipe.Recipe recipe) {
        if (pickNextStep(recipe) != null) return true;
        if (countInInventory(Items.BLAZE_POWDER) > 0
                && countInInventory(Items.POTION, Potions.WATER) >= 3) return true;
        // Any recipe ingredient (other than water) we have could complete an in-progress
        // potion already in a stand.
        for (BrewRecipe.Step step : recipe.steps()) {
            if (countInInventory(step.ingredient()) > 0) return true;
        }
        // Any expired timer means the stand may have finished potions to retrieve.
        long now = System.currentTimeMillis();
        for (java.util.Map.Entry<BlockPos, Long> e : brewingUntil.entrySet()) {
            if (now >= e.getValue()) return true;
        }
        return false;
    }

    /** Cheap fingerprint of inventory: sum of (slot index * count * itemHash). */
    private int computeInventoryFingerprint() {
        int h = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) continue;
            h = h * 31 + i * 31 + s.getCount() * 17 + System.identityHashCode(s.getItem());
        }
        return h;
    }

    /* =================================================================== CHEST */

    private void doOpenChest() {
        if (targetChest == null || !isValidContainerBlock(targetChest)) {
            state = State.IDLE;
            return;
        }
        if (mc.currentScreen instanceof GenericContainerScreen) {
            chestOpenFingerprint = computeInventoryFingerprint();
            state = State.LOOT_CHEST;
            delayTicks = tickDelay.get();
            return;
        }
        if (!aimAndInteract(targetChest)) return;
        delayTicks = tickDelay.get();
    }

    private void doLootChest() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            state = State.OPEN_CHEST;
            return;
        }
        GenericContainerScreenHandler handler = screen.getScreenHandler();
        int chestSize = handler.getRows() * 9;

        BrewRecipe.Recipe recipe = currentRecipe();
        updateNeededItems(recipe);

        int waterNeeded = Math.max(0, 3 - countInInventory(Items.POTION, Potions.WATER));
        int blazeNeeded = Math.max(0, 1 - countInInventory(Items.BLAZE_POWDER));

        // 1) ingredients
        for (int i = 0; i < chestSize; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            if (!neededItems.contains(stack.getItem())) continue;
            mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
            delayTicks = tickDelay.get();
            return;
        }

        // 2) blaze powder
        if (blazeNeeded > 0) {
            for (int i = 0; i < chestSize; i++) {
                ItemStack stack = handler.getSlot(i).getStack();
                if (stack.getItem() != Items.BLAZE_POWDER) continue;
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                delayTicks = tickDelay.get();
                return;
            }
        }

        // 3) Bottles — any potion that's a recipe source (water OR a pre-brewed intermediate
        //    like Awkward) is useful. We grab them only if our current bottle stockpile is
        //    insufficient to fuel any step.
        if (waterNeeded > 0 || !canStartAnyStepWithBottlesOnHand(recipe)) {
            for (int i = 0; i < chestSize; i++) {
                ItemStack stack = handler.getSlot(i).getStack();
                if (stack.isEmpty() || stack.getItem() != Items.POTION) continue;
                RegistryEntry<Potion> p = getPotion(stack);
                if (p == null) continue;
                if (!isRecipeSource(recipe, p)) continue;
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                delayTicks = tickDelay.get();
                return;
            }
        }

        state = State.CLOSE_CHEST;
        delayTicks = tickDelay.get();
    }

    /** True if we already hold ≥3 of any potion that can serve as the source of a recipe step. */
    private boolean canStartAnyStepWithBottlesOnHand(BrewRecipe.Recipe recipe) {
        for (BrewRecipe.Step step : recipe.steps()) {
            if (countInInventory(Items.POTION, step.from()) >= 3) return true;
        }
        return false;
    }

    private boolean isRecipeSource(BrewRecipe.Recipe recipe, RegistryEntry<Potion> potion) {
        for (BrewRecipe.Step step : recipe.steps()) {
            if (step.from().equals(potion)) return true;
        }
        return false;
    }

    private void doCloseChest() {
        // If our inventory didn't change while this chest was open, mark it as useless so we
        // don't keep visiting it on every loop. The blacklist is cleared whenever the inventory
        // fingerprint changes (e.g. after retrieving potions from a stand).
        if (targetChest != null && computeInventoryFingerprint() == chestOpenFingerprint) {
            emptyChestBlacklist.add(targetChest);
        }
        if (mc.currentScreen instanceof HandledScreen<?>) mc.player.closeHandledScreen();
        targetChest = null;
        state = State.IDLE;
        delayTicks = tickDelay.get();
    }

    /* =================================================================== STAND */

    private void doOpenStand() {
        if (targetStand == null || !isBrewingStand(targetStand)) {
            state = State.IDLE;
            return;
        }
        if (mc.currentScreen instanceof BrewingStandScreen) {
            // Decide what to do based on the stand's current state.
            BrewingStandScreenHandler h = ((BrewingStandScreen) mc.currentScreen).getScreenHandler();
            if (hasFinishedPotions(h)) {
                state = State.RETRIEVE;
                delayTicks = tickDelay.get();
                return;
            }
            // No finished potions. If we don't have anything to load (no recipe step possible
            // and no fuel to top up), blacklist this stand and bail out so we don't loop on it.
            if (!hasUsefulInventoryForStand(h)) {
                emptyStandBlacklist.add(targetStand);
                blacklistFingerprint = computeInventoryFingerprint();
                state = State.CLOSE_STAND;
                delayTicks = tickDelay.get();
                return;
            }
            state = State.INSERT_FUEL;
            delayTicks = tickDelay.get();
            return;
        }
        if (!aimAndInteract(targetStand)) return;
        delayTicks = tickDelay.get();
    }

    /**
     * Do we currently hold ANYTHING that would let us advance brewing in the open stand?
     * (a recipe step ready, or fuel that the stand needs, or an ingredient for an
     * intermediate potion already sitting in the stand)
     */
    private boolean hasUsefulInventoryForStand(BrewingStandScreenHandler handler) {
        BrewRecipe.Recipe recipe = currentRecipe();
        if (pickNextStep(recipe) != null) return true;

        // The stand may already have intermediate potions (e.g. Awkward) waiting to be brewed
        // further. If so, and we have the ingredient for the next step, this stand is useful.
        BrewRecipe.Step standStep = pickNextStepForStand(handler, recipe);
        if (standStep != null && countInInventory(standStep.ingredient()) >= 1) return true;

        // Fuel top-up only counts when the stand is fully out of fuel AND we have water bottles
        // to brew with — otherwise refilling fuel alone is pointless and we'd loop on the
        // stand forever.
        if (handler.getFuel() == 0
                && handler.getSlot(STAND_FUEL).getStack().isEmpty()
                && countInInventory(Items.BLAZE_POWDER) > 0
                && countInInventory(Items.POTION, Potions.WATER) >= 3) return true;
        return false;
    }

    private void doRetrieve() {
        if (!(mc.currentScreen instanceof BrewingStandScreen screen)) {
            state = State.OPEN_STAND;
            return;
        }
        BrewingStandScreenHandler handler = screen.getScreenHandler();

        for (int slot = STAND_BOTTLE_0; slot <= STAND_BOTTLE_2; slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (stack.isEmpty()) continue;
            mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.QUICK_MOVE, mc.player);
            delayTicks = tickDelay.get();
            return;
        }

        // Bottles cleared. The brew is fully retrieved — drop the brew timer.
        if (targetStand != null) brewingUntil.remove(targetStand);
        // Move to fueling/loading the same stand if we have a step ready.
        state = State.INSERT_FUEL;
        delayTicks = tickDelay.get();
    }

    private void doInsertFuel() {
        if (!(mc.currentScreen instanceof BrewingStandScreen screen)) {
            state = State.OPEN_STAND;
            return;
        }
        BrewingStandScreenHandler handler = screen.getScreenHandler();
        int fuelLevel = handler.getFuel();

        // Don't refuel while the stand still has any fuel left — only top it up when it
        // runs out completely.
        if (fuelLevel > 0) {
            state = State.INSERT_BOTTLES;
            delayTicks = tickDelay.get();
            return;
        }

        // Fuel is 0. If the fuel slot already has powder, the stand will pick it up itself —
        // don't add more.
        ItemStack inFuel = handler.getSlot(STAND_FUEL).getStack();
        if (!inFuel.isEmpty() && inFuel.getItem() == Items.BLAZE_POWDER) {
            state = State.INSERT_BOTTLES;
            delayTicks = tickDelay.get();
            return;
        }

        int playerSlot = findInPlayerInventory(handler, s -> s.getItem() == Items.BLAZE_POWDER);
        if (playerSlot == -1) {
            // No blaze powder; blacklist this stand until inv changes (so we don't loop on it
            // while we walk to the chest).
            if (targetStand != null) {
                emptyStandBlacklist.add(targetStand);
                blacklistFingerprint = computeInventoryFingerprint();
            }
            state = State.CLOSE_STAND;
            delayTicks = tickDelay.get();
            return;
        }
        // Place exactly ONE blaze powder: pickup the stack, right-click the fuel slot to drop
        // a single item, then return the rest to the player slot.
        mc.interactionManager.clickSlot(handler.syncId, playerSlot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(handler.syncId, STAND_FUEL, 1, SlotActionType.PICKUP, mc.player);
        if (!handler.getCursorStack().isEmpty()) {
            mc.interactionManager.clickSlot(handler.syncId, playerSlot, 0, SlotActionType.PICKUP, mc.player);
        }
        delayTicks = tickDelay.get();
        state = State.INSERT_BOTTLES;
    }

    private void doInsertBottles() {
        if (!(mc.currentScreen instanceof BrewingStandScreen screen)) {
            state = State.OPEN_STAND;
            return;
        }
        BrewingStandScreenHandler handler = screen.getScreenHandler();

        // Decide what kind of bottles need to go into the stand. If the stand already has any
        // potion in the bottle slots, that potion type dictates the rest. Otherwise pick the
        // best step from inventory.
        RegistryEntry<Potion> bottleType = currentBottleTypeForStand(handler);
        if (bottleType == null) {
            // No useful bottles available right now — close the stand instead of looping.
            waitBottlesTicks = 0;
            state = State.WAIT_BOTTLES;
            delayTicks = tickDelay.get();
            return;
        }
        java.util.function.Predicate<ItemStack> matcher = s -> isPotionOfType(s, bottleType);

        if (stackablePotions.get()) {
            // Stackable mode: pick up the entire water stack once, then drop ONE bottle into
            // each of the three bottle slots via right-click (button=1).
            doInsertBottlesStackable(handler, matcher);
            return;
        }

        // Non-stackable (vanilla): each bottle is its own ItemStack of size 1.
        for (int slot = STAND_BOTTLE_0; slot <= STAND_BOTTLE_2; slot++) {
            ItemStack inSlot = handler.getSlot(slot).getStack();
            if (!inSlot.isEmpty()) continue;
            int playerSlot = findInPlayerInventory(handler, matcher);
            if (playerSlot == -1) break;
            mc.interactionManager.clickSlot(handler.syncId, playerSlot, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
            delayTicks = tickDelay.get();
            return;
        }

        waitBottlesTicks = 0;
        state = State.WAIT_BOTTLES;
        delayTicks = tickDelay.get();
    }

    /**
     * Determine which potion type to load into a stand's bottle slots:
     *   1. If a bottle slot already has a potion, use that type (must keep brewing consistent).
     *   2. Otherwise pick the best step our inventory can support and use its source potion.
     *   3. If neither works, returns null.
     */
    private RegistryEntry<Potion> currentBottleTypeForStand(BrewingStandScreenHandler handler) {
        for (int slot = STAND_BOTTLE_0; slot <= STAND_BOTTLE_2; slot++) {
            ItemStack s = handler.getSlot(slot).getStack();
            if (s.isEmpty() || s.getItem() != Items.POTION) continue;
            RegistryEntry<Potion> p = getPotion(s);
            if (p != null) return p;
        }
        BrewRecipe.Step step = pickNextStep(currentRecipe());
        return step != null ? step.from() : null;
    }

    private boolean isPotionOfType(ItemStack stack, RegistryEntry<Potion> type) {
        if (stack.getItem() != Items.POTION) return false;
        RegistryEntry<Potion> p = getPotion(stack);
        return p != null && p.equals(type);
    }

    /**
     * Insert bottles when bottles are stackable (server custom). One PICKUP grabs the
     * whole stack, three RIGHT-CLICKs (button=1) drop one bottle into each empty bottle slot,
     * then return the leftover stack to the player inventory.
     */
    private void doInsertBottlesStackable(BrewingStandScreenHandler handler,
                                          java.util.function.Predicate<ItemStack> matcher) {
        // Skip if all bottle slots already have something.
        boolean anyEmpty = false;
        for (int slot = STAND_BOTTLE_0; slot <= STAND_BOTTLE_2; slot++) {
            if (handler.getSlot(slot).getStack().isEmpty()) { anyEmpty = true; break; }
        }
        if (!anyEmpty) {
            waitBottlesTicks = 0;
            state = State.WAIT_BOTTLES;
            delayTicks = tickDelay.get();
            return;
        }

        // We do all 4 clicks (pickup + 3 right-clicks) sequentially in one tick — server
        // handles them in order. Then put leftover back in one final pickup if cursor is not empty.
        int playerSlot = findInPlayerInventory(handler, matcher);
        if (playerSlot == -1) {
            // No bottles to place — bail to next state, brew might just have fewer bottles.
            waitBottlesTicks = 0;
            state = State.WAIT_BOTTLES;
            delayTicks = tickDelay.get();
            return;
        }

        // Grab the whole bottle stack
        mc.interactionManager.clickSlot(handler.syncId, playerSlot, 0, SlotActionType.PICKUP, mc.player);

        // Right-click each empty bottle slot to deposit ONE bottle there.
        for (int slot = STAND_BOTTLE_0; slot <= STAND_BOTTLE_2; slot++) {
            if (!handler.getSlot(slot).getStack().isEmpty()) continue;
            mc.interactionManager.clickSlot(handler.syncId, slot, 1, SlotActionType.PICKUP, mc.player);
        }

        // Return any remaining stack to the source slot.
        if (!handler.getCursorStack().isEmpty()) {
            mc.interactionManager.clickSlot(handler.syncId, playerSlot, 0, SlotActionType.PICKUP, mc.player);
        }

        delayTicks = tickDelay.get();
        waitBottlesTicks = 0;
        state = State.WAIT_BOTTLES;
    }

    /**
     * After we've inserted bottles, wait a few ticks for the server to confirm them in the
     * stand's bottle slots. Without this delay, doInsertIngredient would see the slots as
     * empty and bail out (blacklisting a perfectly good stand).
     */
    private void doWaitBottles() {
        if (!(mc.currentScreen instanceof BrewingStandScreen screen)) {
            state = State.OPEN_STAND;
            return;
        }
        BrewingStandScreenHandler handler = screen.getScreenHandler();

        // If any bottle slot has a real potion (not empty), proceed to ingredient.
        boolean anyPotion = false;
        for (int slot = STAND_BOTTLE_0; slot <= STAND_BOTTLE_2; slot++) {
            ItemStack s = handler.getSlot(slot).getStack();
            if (!s.isEmpty() && s.getItem() == Items.POTION) { anyPotion = true; break; }
        }
        if (anyPotion) {
            state = State.INSERT_INGREDIENT;
            delayTicks = tickDelay.get();
            return;
        }

        // Bail out after ~2 seconds (40 ticks) if the server hasn't confirmed bottles. We just
        // close instead of blacklisting — the stand might be fine, server is just slow.
        waitBottlesTicks++;
        if (waitBottlesTicks > 40) {
            state = State.CLOSE_STAND;
            delayTicks = tickDelay.get();
            return;
        }
        delayTicks = 1;
    }

    private void doInsertIngredient() {
        if (!(mc.currentScreen instanceof BrewingStandScreen screen)) {
            state = State.OPEN_STAND;
            return;
        }
        BrewingStandScreenHandler handler = screen.getScreenHandler();

        // Wait until brewing is empty (it might be brewing leftover from a previous batch).
        if (handler.getBrewTime() > 0) {
            // Already brewing — mark the timer so IDLE won't reopen this stand.
            if (targetStand != null) {
                brewingUntil.put(targetStand, System.currentTimeMillis() + BREW_DURATION_MS);
            }
            state = State.CLOSE_STAND;
            delayTicks = tickDelay.get();
            return;
        }

        // Decide step from bottle contents.
        BrewRecipe.Recipe recipe = currentRecipe();
        BrewRecipe.Step step = pickNextStepForStand(handler, recipe);
        if (step == null) {
            // Bottles fully done or unsupported state → blacklist + close (we have no useful
            // recipe step for what's currently in this stand).
            if (targetStand != null) {
                emptyStandBlacklist.add(targetStand);
                blacklistFingerprint = computeInventoryFingerprint();
            }
            state = State.CLOSE_STAND;
            delayTicks = tickDelay.get();
            return;
        }

        ItemStack ingSlot = handler.getSlot(STAND_INGREDIENT).getStack();
        if (!ingSlot.isEmpty() && ingSlot.getItem() == step.ingredient()) {
            // Ingredient already in place — brewing must be starting/already happening. Mark
            // timer and close.
            if (targetStand != null) {
                brewingUntil.put(targetStand, System.currentTimeMillis() + BREW_DURATION_MS);
            }
            state = State.CLOSE_STAND;
            delayTicks = tickDelay.get();
            return;
        }

        int playerSlot = findInPlayerInventory(handler, s -> s.getItem() == step.ingredient());
        if (playerSlot == -1) {
            // We *thought* we had the ingredient (pickNextStep said so) but the player inv
            // search failed. Don't loop on this stand: blacklist it until inventory changes.
            if (targetStand != null) {
                emptyStandBlacklist.add(targetStand);
                blacklistFingerprint = computeInventoryFingerprint();
            }
            state = State.CLOSE_STAND;
            delayTicks = tickDelay.get();
            return;
        }
        mc.interactionManager.clickSlot(handler.syncId, playerSlot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(handler.syncId, STAND_INGREDIENT, 1, SlotActionType.PICKUP, mc.player);
        if (!handler.getCursorStack().isEmpty()) {
            mc.interactionManager.clickSlot(handler.syncId, playerSlot, 0, SlotActionType.PICKUP, mc.player);
        }
        // Mark this stand as brewing for ~20s. Until that timer expires we won't open it.
        if (targetStand != null) {
            brewingUntil.put(targetStand, System.currentTimeMillis() + BREW_DURATION_MS);
        }
        delayTicks = tickDelay.get();
        state = State.CLOSE_STAND;
    }

    private void doCloseStand() {
        if (mc.currentScreen instanceof HandledScreen<?>) mc.player.closeHandledScreen();
        targetStand = null;
        state = State.IDLE;
        delayTicks = tickDelay.get();
    }

    /* =================================================================== HELPERS */

    private BrewRecipe.Recipe currentRecipe() {
        return switch (goal.get()) {
            case Strength_II -> BrewRecipe.STRENGTH_II;
            case Speed_II -> BrewRecipe.SPEED_II;
            case Invisibility_8m -> BrewRecipe.INVISIBILITY_8M;
            case FireResistance_8m -> BrewRecipe.FIRE_RES_8M;
        };
    }

    private void updateNeededItems(BrewRecipe.Recipe recipe) {
        neededItems.clear();
        for (BrewRecipe.Step step : recipe.steps()) {
            if (countInInventory(step.ingredient()) < 1) neededItems.add(step.ingredient());
        }
    }

    private BrewRecipe.Step pickNextStep(BrewRecipe.Recipe recipe) {
        for (int i = recipe.steps().size() - 1; i >= 0; i--) {
            BrewRecipe.Step step = recipe.steps().get(i);
            if (countInInventory(Items.POTION, step.from()) >= 3
                    && countInInventory(step.ingredient()) >= 1) {
                return step;
            }
        }
        return null;
    }

    private BrewRecipe.Step pickNextStepForStand(BrewingStandScreenHandler handler, BrewRecipe.Recipe recipe) {
        RegistryEntry<Potion> currentPotion = null;
        for (int slot = STAND_BOTTLE_0; slot <= STAND_BOTTLE_2; slot++) {
            ItemStack s = handler.getSlot(slot).getStack();
            if (s.isEmpty()) continue;
            currentPotion = getPotion(s);
            if (currentPotion != null) break;
        }
        if (currentPotion == null) return null;
        for (BrewRecipe.Step step : recipe.steps()) {
            if (step.from().equals(currentPotion)) return step;
        }
        return null;
    }

    private RegistryEntry<Potion> getPotion(ItemStack stack) {
        PotionContentsComponent c = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (c == null) return null;
        return c.potion().orElse(null);
    }

    private boolean isWaterBottle(ItemStack stack) {
        if (stack.getItem() != Items.POTION) return false;
        RegistryEntry<Potion> p = getPotion(stack);
        return p != null && p.equals(Potions.WATER);
    }

    private int countInInventory(Item item) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == item) count += s.getCount();
        }
        return count;
    }

    private int countInInventory(Item potionItem, RegistryEntry<Potion> potion) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() != potionItem) continue;
            RegistryEntry<Potion> p = getPotion(s);
            if (p != null && p.equals(potion)) count += s.getCount();
        }
        return count;
    }

    private int findInPlayerInventory(ScreenHandler handler, java.util.function.Predicate<ItemStack> pred) {
        int total = handler.slots.size();
        int playerInvStart = total - 36;
        for (int i = playerInvStart; i < total; i++) {
            ItemStack s = handler.getSlot(i).getStack();
            if (s.isEmpty()) continue;
            if (pred.test(s)) return i;
        }
        return -1;
    }

    /* ----------------- block scanning ----------------- */

    private static final class BrewingStandSnapshot {
        BlockPos pos;
        boolean hasPotions;
        int brewTime;
        boolean ingredientPresent;

        boolean isFinished() {
            return hasPotions && brewTime == 0 && !ingredientPresent;
        }
    }

    /**
     * The closest brewing stand that we believe could have finished potions, while skipping
     * stands we recently blacklisted as empty/useless. Also requires that we have something
     * useful to do at a stand (otherwise opening any stand without potions inside is wasted).
     */
    private BlockPos findFinishedStandCandidate(boolean canDoAnythingAtStand) {
        long now = System.currentTimeMillis();
        for (BlockPos pos : findAllBrewingStandsByDistance()) {
            if (emptyStandBlacklist.contains(pos)) continue;
            Long until = brewingUntil.get(pos);
            // A stand whose timer has expired might have finished potions — open it regardless.
            // A stand without a timer is "unknown"; only open it if we can usefully act on it.
            if (until == null) {
                if (canDoAnythingAtStand) return pos;
            } else if (now >= until) {
                return pos;
            }
        }
        return null;
    }

    private BlockPos findFreeBrewingStand() {
        long now = System.currentTimeMillis();
        for (BlockPos pos : findAllBrewingStandsByDistance()) {
            if (emptyStandBlacklist.contains(pos)) continue;
            Long until = brewingUntil.get(pos);
            if (until == null || now >= until) return pos;
        }
        return null;
    }

    private BlockPos findNearestBrewingStand() {
        return findNearestBlock(this::isBrewingStand);
    }

    private BlockPos findNearestChest() {
        return findNearestBlock(pos -> isValidContainerBlock(pos) && !emptyChestBlacklist.contains(pos));
    }

    private BlockPos findNearestBlock(java.util.function.Predicate<BlockPos> matcher) {
        if (mc.world == null) return null;
        Vec3d eye = mc.player.getEyePos();
        double r = scanRadius.get();
        double r2 = r * r;
        int range = (int) Math.ceil(r);
        BlockPos origin = mc.player.getBlockPos();
        BlockPos best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos pos = origin.add(x, y, z);
                    double d = Vec3d.ofCenter(pos).squaredDistanceTo(eye);
                    if (d > r2) continue;
                    if (!matcher.test(pos)) continue;
                    if (d < bestDist) { bestDist = d; best = pos.toImmutable(); }
                }
            }
        }
        return best;
    }

    /**
     * All brewing stands in scan range, sorted by distance ascending (closest first).
     */
    private List<BlockPos> findAllBrewingStandsByDistance() {
        if (mc.world == null) return List.of();
        Vec3d eye = mc.player.getEyePos();
        double r = scanRadius.get();
        double r2 = r * r;
        int range = (int) Math.ceil(r);
        BlockPos origin = mc.player.getBlockPos();
        List<BlockPos> all = new ArrayList<>();
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos pos = origin.add(x, y, z);
                    double d = Vec3d.ofCenter(pos).squaredDistanceTo(eye);
                    if (d > r2) continue;
                    if (!isBrewingStand(pos)) continue;
                    all.add(pos.toImmutable());
                }
            }
        }
        all.sort(Comparator.comparingDouble(p -> Vec3d.ofCenter(p).squaredDistanceTo(eye)));
        return all;
    }

    private boolean isValidContainerBlock(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return state.isOf(Blocks.CHEST) || state.isOf(Blocks.TRAPPED_CHEST) || state.isOf(Blocks.BARREL);
    }

    private boolean isBrewingStand(BlockPos pos) {
        return mc.world.getBlockState(pos).isOf(Blocks.BREWING_STAND);
    }

    private boolean hasFinishedPotions(BrewingStandScreenHandler handler) {
        if (handler.getBrewTime() > 0) return false;
        if (!handler.getSlot(STAND_INGREDIENT).getStack().isEmpty()) return false;
        BrewRecipe.Recipe recipe = currentRecipe();
        RegistryEntry<Potion> finalPotion = recipe.finalPotion();
        for (int slot = STAND_BOTTLE_0; slot <= STAND_BOTTLE_2; slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (stack.isEmpty() || stack.getItem() != Items.POTION) continue;
            RegistryEntry<Potion> p = getPotion(stack);
            if (p == null) continue;
            // Final goal — retrieve.
            if (p.equals(finalPotion)) return true;
            // Potion that's not part of our recipe path at all — retrieve to free the stand.
            if (!isPartOfRecipe(recipe, p)) return true;
        }
        return false;
    }

    private boolean isPartOfRecipe(BrewRecipe.Recipe recipe, RegistryEntry<Potion> potion) {
        for (BrewRecipe.Step s : recipe.steps()) {
            if (s.from().equals(potion) || s.to().equals(potion)) return true;
        }
        return false;
    }

    /* ----------------- rotation + interaction ----------------- */

    private boolean aimAndInteract(BlockPos pos) {
        Vec3d hit = Vec3d.ofCenter(pos);
        Vec3d eye = mc.player.getEyePos();
        if (eye.distanceTo(hit) > reach.get()) return false;

        Rotation target = RotationUtil.calculate(hit);
        Managers.ROTATION.setRotations(
                target,
                rotationSpeed.get(),
                silent.get() ? MovementFix.NORMAL : MovementFix.OFF,
                RotationManager.Priority.High
        );

        Rotation cur = Managers.ROTATION.getRotation();
        float yawDiff = MathHelper.wrapDegrees(cur.yaw - target.yaw);
        float pitchDiff = cur.pitch - target.pitch;
        if (Math.hypot(yawDiff, pitchDiff) > 4.0) return false;

        Direction dir = closestFace(eye, pos);
        Vec3d hitVec = Vec3d.ofCenter(pos).add(dir.getOffsetX() * 0.5, dir.getOffsetY() * 0.5, dir.getOffsetZ() * 0.5);
        BlockHitResult bhr = new BlockHitResult(hitVec, dir, pos, false);
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
        if (result.isAccepted()) {
            mc.player.swingHand(Hand.MAIN_HAND);
            return true;
        }
        return false;
    }

    private Direction closestFace(Vec3d eye, BlockPos pos) {
        Vec3d c = Vec3d.ofCenter(pos);
        Vec3d d = eye.subtract(c);
        double ax = Math.abs(d.x), ay = Math.abs(d.y), az = Math.abs(d.z);
        if (ay >= ax && ay >= az) return d.y > 0 ? Direction.UP : Direction.DOWN;
        if (ax >= az) return d.x > 0 ? Direction.EAST : Direction.WEST;
        return d.z > 0 ? Direction.SOUTH : Direction.NORTH;
    }
}
