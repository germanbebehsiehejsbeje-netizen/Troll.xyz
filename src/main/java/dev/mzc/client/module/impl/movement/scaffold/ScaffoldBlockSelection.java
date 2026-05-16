package dev.mzc.client.module.impl.movement.scaffold;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Set;

/**
 * Port of LB's ScaffoldBlockItemSelection.
 *
 * Decides what items can be used as scaffold blocks and which are unfavourable.
 */
public final class ScaffoldBlockSelection {
    private ScaffoldBlockSelection() {}

    private static final Set<Block> DISALLOWED = Set.of(
            Blocks.TNT,
            Blocks.COBWEB,
            Blocks.NETHER_PORTAL,
            Blocks.SLIME_BLOCK,
            Blocks.HONEY_BLOCK,
            Blocks.BEDROCK
    );

    private static final Set<Block> UNFAVOURABLE = Set.of(
            Blocks.CRAFTING_TABLE,
            Blocks.JIGSAW,
            Blocks.SMITHING_TABLE,
            Blocks.FLETCHING_TABLE,
            Blocks.ENCHANTING_TABLE,
            Blocks.CAULDRON,
            Blocks.MAGMA_BLOCK,
            Blocks.SCAFFOLDING,
            Blocks.LADDER,
            Blocks.VINE
    );

    public static Block getBlockFromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (!(stack.getItem() instanceof BlockItem bi)) return null;
        return bi.getBlock();
    }

    public static boolean isValidBlock(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Block block = getBlockFromStack(stack);
        if (block == null) return false;
        if (DISALLOWED.contains(block)) return false;
        if (block instanceof FallingBlock) return false;
        // Block must support standing on top — approximate via solid full block check.
        var defaultState = block.getDefaultState();
        try {
            // entityCanStandOnFace replacement: shape full-cube on UP
            return defaultState.isSideSolidFullSquare(MinecraftClient.getInstance().world, BlockPos.ORIGIN, Direction.UP);
        } catch (Throwable t) {
            return defaultState.isSolid();
        }
    }

    /**
     * "Unfavourable" — used only as a last resort.
     */
    public static boolean isBlockUnfavourable(ItemStack stack) {
        Block block = getBlockFromStack(stack);
        if (block == null) return true;
        if (UNFAVOURABLE.contains(block)) return true;
        if (block instanceof BlockEntityProvider) return true;
        try {
            var ds = block.getDefaultState();
            // Slippery (ice etc)
            if (block.getSlipperiness() > 0.6f) return true;
            // Slow/sticky (slime/soul sand)
            if (block.getVelocityMultiplier() < 1.0f) return true;
            if (block.getJumpVelocityMultiplier() < 1.0f) return true;
            // Not a full collision cube
            var shape = ds.getCollisionShape(MinecraftClient.getInstance().world, BlockPos.ORIGIN);
            if (shape == null || shape.isEmpty()) return true;
            var box = shape.getBoundingBox();
            if (box.minX > 0.001 || box.minY > 0.001 || box.minZ > 0.001 ||
                box.maxX < 0.999 || box.maxY < 0.999 || box.maxZ < 0.999) return true;
        } catch (Throwable ignored) {}
        return false;
    }
}
