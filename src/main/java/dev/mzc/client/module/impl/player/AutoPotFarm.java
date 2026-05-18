package dev.mzc.client.module.impl.player;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.render.Render3DEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.player.InvUtil;
import dev.mzc.client.utils.render.Render3DUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.ColorValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.BrewingStandScreenHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class AutoPotFarm extends Module {

    private final IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();

    private final NumberValue<Double> range = new NumberValue<>("Range", 10.0, 1.0, 30.0, 1.0);
    private final NumberValue<Integer> brewTime = new NumberValue<>("BrewTime", 20, 10, 40, 1);
    private final NumberValue<Integer> actionDelay = new NumberValue<>("ActionDelay", 5, 0, 20, 1);
    private final BoolValue autoIngredients = new BoolValue("AutoIngredients", true);
    private final ColorValue outlineColor = new ColorValue("OutlineColor", new Color(0, 200, 255, 200));
    private final NumberValue<Float> outlineWidth = new NumberValue<>("OutlineWidth", 3.0f, 0.5f, 10.0f, 0.5f);
    private final BoolValue debug = new BoolValue("Debug", false);

    private BlockPos targetBrewingStand = null;
    private int brewProgress = 0;
    private int actionDelayCounter = 0;
    private boolean isBrewing = false;
    private long brewStartTime = 0;

    public AutoPotFarm() {
        super("AutoPotFarm", Category.Player);
        this.setType(ModuleType.Safe);
    }

    @Override
    protected void onEnable() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }
        baritone.getCommandManager().execute("stop");
        targetBrewingStand = null;
        brewProgress = 0;
        actionDelayCounter = 0;
        isBrewing = false;
        brewStartTime = 0;
    }

    @Override
    protected void onDisable() {
        baritone.getCommandManager().execute("stop");
        targetBrewingStand = null;
        isBrewing = false;
        brewProgress = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        if (actionDelayCounter > 0) {
            actionDelayCounter--;
            return;
        }

        // Find brewing stand if we don't have a target
        if (targetBrewingStand == null || !isValidBrewingStand(targetBrewingStand)) {
            findBrewingStand();
            if (targetBrewingStand == null) return;
        }

        // Pathfind to brewing stand
        if (!mc.player.getBlockPos().isWithinDistance(targetBrewingStand, 2.0)) {
            if (!baritone.getPathingBehavior().isPathing()) {
                baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(targetBrewingStand));
            }
            return;
        }

        // Stop baritone when we're close enough
        if (baritone.getPathingBehavior().isPathing()) {
            baritone.getCommandManager().execute("stop");
        }

        // Check if brewing stand screen is open
        if (mc.player.currentScreenHandler instanceof BrewingStandScreenHandler) {
            handleBrewing();
        } else {
            // Open brewing stand
            openBrewingStand();
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (targetBrewingStand == null || !isValidBrewingStand(targetBrewingStand)) return;

        MatrixStack matrices = event.getMatrices();
        Box box = new Box(targetBrewingStand);

        // Draw brewing progress outline
        if (isBrewing) {
            float progress = (float) brewProgress / (float) brewTime.get();
            drawProgressiveOutline(matrices, box, progress);
        } else {
            // Draw static outline when not brewing
            Color color = outlineColor.get();
            Render3DUtil.drawBoxOutline(matrices, box, color.getRGB(), outlineWidth.get());
        }
    }

    private void findBrewingStand() {
        int r = range.get().intValue();
        BlockPos playerPos = mc.player.getBlockPos();

        List<BlockPos> brewingStands = StreamSupport.stream(
                BlockPos.iterateOutwards(playerPos, r, r, r).spliterator(), false
        )
                .filter(pos -> mc.world.getBlockState(pos).getBlock() == Blocks.BREWING_STAND)
                .sorted(Comparator.comparingDouble(pos -> mc.player.squaredDistanceTo(Vec3d.ofCenter(pos))))
                .collect(Collectors.toList());

        if (!brewingStands.isEmpty()) {
            targetBrewingStand = brewingStands.get(0);
            if (debug.get()) {
                mc.player.sendMessage(net.minecraft.text.Text.of("§a[AutoPotFarm] Found brewing stand at " + targetBrewingStand), false);
            }
        }
    }

    private boolean isValidBrewingStand(BlockPos pos) {
        if (pos == null) return false;
        return mc.world.getBlockState(pos).getBlock() == Blocks.BREWING_STAND;
    }

    private void openBrewingStand() {
        Vec3d hitVec = Vec3d.ofCenter(targetBrewingStand);
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.DOWN, targetBrewingStand, false);
        
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        mc.player.swingHand(Hand.MAIN_HAND);
        actionDelayCounter = actionDelay.get();
    }

    private void handleBrewing() {
        BrewingStandScreenHandler handler = (BrewingStandScreenHandler) mc.player.currentScreenHandler;
        
        // Auto-fill ingredients if enabled
        if (autoIngredients.get()) {
            fillIngredients(handler);
        }
        // Track brewing progress using timer
        // Brewing takes 400 ticks (20 seconds) in vanilla Minecraft
        if (isBrewing) {
            long elapsed = System.currentTimeMillis() - brewStartTime;
            int brewTimeMs = brewTime.get() * 1000; // Convert seconds to milliseconds
            brewProgress = Math.min(brewTime.get(), (int) ((float) elapsed / (float) brewTimeMs * brewTime.get()));
            
            // Check if brewing is complete
            if (elapsed >= brewTimeMs) {
                isBrewing = false;
                brewProgress = brewTime.get();
                
                if (debug.get()) {
                    mc.player.sendMessage(net.minecraft.text.Text.of("§a[AutoPotFarm] Brewing complete!"), false);
                }
                
                // Close screen and add delay before next action
                mc.player.closeHandledScreen();
                actionDelayCounter = actionDelay.get();
            } else {
                if (debug.get()) {
                    mc.player.sendMessage(net.minecraft.text.Text.of("§e[AutoPotFarm] Brewing progress: " + brewProgress + "/" + brewTime.get()), false);
                }
            }
        } else {
            // Check if we should start brewing (ingredients are present)
            boolean hasPotions = false;
            for (int i = 0; i < 3; i++) {
                if (!handler.getSlot(i).getStack().isEmpty()) {
                    hasPotions = true;
                    break;
                }
            }
            
            boolean hasIngredient = !handler.getSlot(3).getStack().isEmpty();
            
            if (hasPotions && hasIngredient) {
                // Start brewing
                isBrewing = true;
                brewStartTime = System.currentTimeMillis();
                brewProgress = 0;
            }
        }
    }

    private void fillIngredients(BrewingStandScreenHandler handler) {
        // Fill blaze powder as fuel if needed (slot 4 is fuel slot)
        ItemStack fuelSlot = handler.getSlot(4).getStack();
        if (fuelSlot.isEmpty() || fuelSlot.getItem() != Items.BLAZE_POWDER) {
            int blazePowderSlot = findItemInInventory(Items.BLAZE_POWDER);
            if (blazePowderSlot != -1) {
                // Click on fuel slot to add blaze powder
                mc.interactionManager.clickSlot(handler.syncId, 4, 0, 
                        net.minecraft.screen.slot.SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(handler.syncId, blazePowderSlot, 0,
                        net.minecraft.screen.slot.SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(handler.syncId, 4, 0,
                        net.minecraft.screen.slot.SlotActionType.PICKUP, mc.player);
                actionDelayCounter = actionDelay.get();
            }
        }

        // Fill potion bottles if slots are empty (slots 0, 1, 2)
        for (int i = 0; i < 3; i++) {
            ItemStack slot = handler.getSlot(i).getStack();
            if (slot.isEmpty()) {
                int potionSlot = findItemInInventory(Items.POTION);
                if (potionSlot != -1) {
                    mc.interactionManager.clickSlot(handler.syncId, i, 0,
                            net.minecraft.screen.slot.SlotActionType.PICKUP, mc.player);
                    mc.interactionManager.clickSlot(handler.syncId, potionSlot, 0,
                            net.minecraft.screen.slot.SlotActionType.PICKUP, mc.player);
                    mc.interactionManager.clickSlot(handler.syncId, i, 0,
                            net.minecraft.screen.slot.SlotActionType.PICKUP, mc.player);
                    actionDelayCounter = actionDelay.get();
                }
            }
        }

        // Fill brewing ingredient if top slot is empty (slot 3)
        ItemStack ingredientSlot = handler.getSlot(3).getStack();
        if (ingredientSlot.isEmpty()) {
            // Find common brewing ingredients
            Item[] ingredients = {
                Items.NETHER_WART,
                Items.GLOWSTONE_DUST,
                Items.REDSTONE,
                Items.GUNPOWDER,
                Items.SUGAR,
                Items.RABBIT_FOOT,
                Items.SPIDER_EYE,
                Items.GHAST_TEAR,
                Items.MAGMA_CREAM,
                Items.GOLDEN_CARROT,
                Items.PUFFERFISH,
                Items.PHANTOM_MEMBRANE,
                Items.DRAGON_BREATH
            };

            for (Item ingredient : ingredients) {
                int slot = findItemInInventory(ingredient);
                if (slot != -1) {
                    mc.interactionManager.clickSlot(handler.syncId, 3, 0,
                            net.minecraft.screen.slot.SlotActionType.PICKUP, mc.player);
                    mc.interactionManager.clickSlot(handler.syncId, slot, 0,
                            net.minecraft.screen.slot.SlotActionType.PICKUP, mc.player);
                    mc.interactionManager.clickSlot(handler.syncId, 3, 0,
                            net.minecraft.screen.slot.SlotActionType.PICKUP, mc.player);
                    actionDelayCounter = actionDelay.get();
                    break;
                }
            }
        }
    }

    private int findItemInInventory(Item item) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    private void drawProgressiveOutline(MatrixStack matrices, Box box, float progress) {
        Color color = outlineColor.get();
        
        // Create a box that fills based on progress (from bottom to top)
        double heightFill = progress;
        Box filledBox = new Box(
            box.minX, box.minY, box.minZ,
            box.maxX, box.minY + (box.maxY - box.minY) * heightFill, box.maxZ
        );

        // Draw the outline with partial fill effect
        Render3DUtil.drawBoxOutline(matrices, filledBox, color.getRGB(), outlineWidth.get());
        
        // Draw the full outline faintly
        Color faintColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(color.getAlpha() * 0.3f));
        Render3DUtil.drawBoxOutline(matrices, box, faintColor.getRGB(), outlineWidth.get() * 0.5f);
    }
}
