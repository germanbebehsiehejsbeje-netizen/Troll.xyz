package dev.mzc.client.module.impl.player;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import dev.mzc.client.events.EventType;
import dev.mzc.client.events.player.MotionEvent;
import dev.mzc.client.manager.Managers;
import dev.mzc.client.manager.impl.RotationManager;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.player.FindItemResult;
import dev.mzc.client.utils.player.InvUtil;
import dev.mzc.client.utils.rotation.MovementFix;
import dev.mzc.client.utils.rotation.RotationUtil;
import dev.mzc.client.utils.vector.Rotation;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AutoFarm extends Module {

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();

    private final NumberValue<Double> rangeValue = new NumberValue<>("Range", 5.0, 1.0, 15.0, 0.5);
    private final NumberValue<Integer> boneMealDelay = new NumberValue<>("BoneMealDelay", 7, 1, 20, 1);
    private final NumberValue<Integer> actionDelay = new NumberValue<>("ActionDelay", 2, 0, 10, 1);
    private final BoolValue legitRotations = new BoolValue("LegitRotations", true);

    private long lastBoneMealTime = 0;
    private int currentActionDelay = 0;
    private BlockPos targetCropPos = null;

    private final Set<BlockPos> harvestedCrops = ConcurrentHashMap.newKeySet();

    public AutoFarm() {
        super("AutoFarm", Category.Player);
        this.setType(ModuleType.All);
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }
        baritone.getCommandManager().execute("stop");
        lastBoneMealTime = System.currentTimeMillis();
        currentActionDelay = 0;
        targetCropPos = null;
        harvestedCrops.clear();
    }

    @Override
    public void onDisable() {
        baritone.getCommandManager().execute("stop");
        if (Managers.ROTATION != null) {
            Managers.ROTATION.setActive(false);
        }
    }

    @EventHandler
    public void onMotion(MotionEvent event) {
        if (event.getType() != EventType.PRE || mc.player == null || mc.world == null) return;

        if (currentActionDelay > 0) {
            currentActionDelay--;
            return;
        }

        if (targetCropPos == null || !isValidCrop(targetCropPos)) {
            findNextCrop();
            if (targetCropPos == null) return;
        }

        if (!baritone.getPathingBehavior().isPathing()) {
            baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(targetCropPos));
        }

        if (mc.player.getBlockPos().isWithinDistance(targetCropPos, 2.5)) {
            interactWithCrop(targetCropPos);
            currentActionDelay = actionDelay.get();
            targetCropPos = null;
        }
    }

    private void findNextCrop() {
        int r = rangeValue.get().intValue();
        List<BlockPos> nearbyBlocks = BlockPos.stream(
                        mc.player.getBlockX() - r,
                        mc.player.getBlockY() - r,
                        mc.player.getBlockZ() - r,
                        mc.player.getBlockX() + r,
                        mc.player.getBlockY() + r,
                        mc.player.getBlockZ() + r)
                .map(BlockPos::toImmutable)
                .filter(this::isValidCrop)
                .sorted(Comparator.comparingDouble(pos -> mc.player.squaredDistanceTo(Vec3d.ofCenter(pos))))
                .collect(Collectors.toList());

        for (BlockPos pos : nearbyBlocks) {
            if (!harvestedCrops.contains(pos)) {
                targetCropPos = pos;
                return;
            }
        }
        harvestedCrops.clear();
        targetCropPos = null;
    }

    private boolean isValidCrop(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        Block block = state.getBlock();
        return block instanceof CropBlock;
    }

    private void interactWithCrop(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        if (!(state.getBlock() instanceof CropBlock cropBlock)) return;

        if (cropBlock.isMature(state)) {
            harvest(pos, cropBlock);
        } else if (System.currentTimeMillis() - lastBoneMealTime >= boneMealDelay.get() * 1000L) {
            useBoneMeal(pos);
            lastBoneMealTime = System.currentTimeMillis();
        }
    }

    private void harvest(BlockPos pos, CropBlock cropBlock) {
        lookAtBlock(pos);
        mc.interactionManager.attackBlock(pos, Direction.DOWN);
        mc.player.swingHand(Hand.MAIN_HAND);
        
        replantCrop(pos, cropBlock);
        harvestedCrops.add(pos);
    }

    private void useBoneMeal(BlockPos pos) {
        FindItemResult result = InvUtil.findInHotbar(Items.BONE_MEAL);
        if (result.found()) {
            int originalSlot = mc.player.getInventory().getSelectedSlot();
            InvUtil.swap(result.slot(), false);
            lookAtBlock(pos);
            
            Vec3d hitVec = Vec3d.ofCenter(pos);
            BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, pos, false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
            mc.player.swingHand(Hand.MAIN_HAND);
            
            InvUtil.swap(originalSlot, false);
        }
    }

    private void replantCrop(BlockPos pos, CropBlock cropBlock) {
        Item seedItem = getSeedItem(cropBlock);
        if (seedItem == null) return;

        FindItemResult result = InvUtil.findInHotbar(seedItem);
        if (result.found()) {
            int originalSlot = mc.player.getInventory().getSelectedSlot();
            InvUtil.swap(result.slot(), false);
            lookAtBlock(pos);
            
            BlockPos farmlandPos = pos.down();
            if (mc.world.getBlockState(farmlandPos).getBlock() instanceof FarmlandBlock) {
                Vec3d hitVec = Vec3d.ofCenter(farmlandPos);
                BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, farmlandPos, false);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
            InvUtil.swap(originalSlot, false);
        }
    }

    private Item getSeedItem(CropBlock cropBlock) {
        if (cropBlock instanceof net.minecraft.block.CropBlock) {
             // Use generic detection based on the block type if specific classes aren't found
             String blockName = cropBlock.getTranslationKey();
             if (blockName.contains("wheat")) return Items.WHEAT_SEEDS;
             if (blockName.contains("carrot")) return Items.CARROT;
             if (blockName.contains("potato")) return Items.POTATO;
             if (blockName.contains("beetroot")) return Items.BEETROOT_SEEDS;
        }
        return null;
    }

    private void lookAtBlock(BlockPos pos) {
        Vec3d targetVec = Vec3d.ofCenter(pos);
        Rotation rotation = RotationUtil.calculate(targetVec);
        if (Managers.ROTATION != null) {
            Managers.ROTATION.setRotations(rotation, 180, MovementFix.OFF, RotationManager.Priority.High);
        }
    }
}
