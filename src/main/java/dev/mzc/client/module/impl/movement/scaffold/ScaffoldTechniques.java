package dev.mzc.client.module.impl.movement.scaffold;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Port of LB scaffold techniques: Normal / Expand / GodBridge / Breezily.
 *
 * Each technique decides which target position to consider, which offsets to try, and
 * which AimMode to use. Returns a {@link BlockPlacementTarget} or null.
 */
public final class ScaffoldTechniques {
    private ScaffoldTechniques() {}

    public enum Technique { Normal, Expand, GodBridge, Breezily }

    /** Single per-Scaffold-instance state for GodBridge yaw locking. */
    private static final GodBridgeState GOD_BRIDGE_STATE = new GodBridgeState();

    public static void resetGodBridge() { GOD_BRIDGE_STATE.reset(); }

    public static BlockPlacementTarget find(
            Technique technique,
            ScaffoldTargetFinder.AimMode aimMode,
            BlockPos targetPos,
            Vec3d predictedPos,
            Vec3d predictedEyePos,
            ScaffoldMovementPlanner.Line optimalLine,
            ItemStack stackToPlaceWith,
            boolean isGoingDown,
            boolean isHeadHittering,
            boolean preferHorizontal
    ) {
        switch (technique) {
            case Normal -> {
                return normal(aimMode, targetPos, predictedPos, predictedEyePos, optimalLine, stackToPlaceWith, isGoingDown, preferHorizontal);
            }
            case Expand -> {
                return expand(aimMode, predictedPos, predictedEyePos, optimalLine, stackToPlaceWith);
            }
            case GodBridge -> {
                return GOD_BRIDGE_STATE.compute(targetPos, predictedPos, predictedEyePos, optimalLine, stackToPlaceWith);
            }
            case Breezily -> {
                return breezily(aimMode, targetPos, predictedPos, predictedEyePos, optimalLine, stackToPlaceWith, isHeadHittering, preferHorizontal);
            }
        }
        return null;
    }

    private static BlockPlacementTarget normal(
            ScaffoldTargetFinder.AimMode aimMode,
            BlockPos targetPos, Vec3d predictedPos, Vec3d predictedEyePos,
            ScaffoldMovementPlanner.Line optimalLine, ItemStack stackToPlaceWith,
            boolean isGoingDown, boolean preferHorizontal
    ) {
        var offsets = isGoingDown
                ? ScaffoldTargetFinder.Offsets.DOWN
                : ScaffoldTargetFinder.Offsets.NORMAL;
        var priority = optimalLine != null
                ? ScaffoldTargetFinder.leastDistToLine(optimalLine)
                : ScaffoldTargetFinder.leastDistToPos(predictedPos);
        ScaffoldTargetFinder.Options opts = new ScaffoldTargetFinder.Options(
                offsets, priority, aimMode,
                isGoingDown, // consider facing-away when going down
                stackToPlaceWith, predictedPos, predictedEyePos, optimalLine,
                System.nanoTime(), preferHorizontal
        );
        return ScaffoldTargetFinder.findBest(targetPos, opts);
    }

    /**
     * Expand: place blocks horizontally outward in a wide ring at the player's Y.
     * Uses NEAREST_ROTATION-style aim and considers facing-away faces.
     */
    private static BlockPlacementTarget expand(
            ScaffoldTargetFinder.AimMode aimMode,
            Vec3d predictedPos, Vec3d predictedEyePos,
            ScaffoldMovementPlanner.Line optimalLine, ItemStack stackToPlaceWith
    ) {
        BlockPos pPos = BlockPos.ofFloored(predictedPos.x, predictedPos.y - 1, predictedPos.z);
        var priority = ScaffoldTargetFinder.leastDistToPos(predictedPos);
        ScaffoldTargetFinder.Options opts = new ScaffoldTargetFinder.Options(
                ScaffoldTargetFinder.Offsets.EXPAND, priority,
                aimMode == ScaffoldTargetFinder.AimMode.Stabilized
                        ? ScaffoldTargetFinder.AimMode.NearestRotation : aimMode,
                true, // expand needs facing-away faces
                stackToPlaceWith, predictedPos, predictedEyePos, optimalLine,
                System.nanoTime()
        );
        return ScaffoldTargetFinder.findBest(pPos, opts);
    }

    /**
     * Breezily: keyboard-cycling normal scaffold. Same finder as Normal but a smaller offset set
     * to keep placements directly under the player; switches to Random aim for variation.
     */
    private static BlockPlacementTarget breezily(
            ScaffoldTargetFinder.AimMode aimMode,
            BlockPos targetPos, Vec3d predictedPos, Vec3d predictedEyePos,
            ScaffoldMovementPlanner.Line optimalLine, ItemStack stackToPlaceWith,
            boolean isHeadHittering, boolean preferHorizontal
    ) {
        var offsets = isHeadHittering
                ? ScaffoldTargetFinder.Offsets.NORMAL
                : ScaffoldTargetFinder.Offsets.NO_OFFSET;
        var priority = optimalLine != null
                ? ScaffoldTargetFinder.leastDistToLine(optimalLine)
                : ScaffoldTargetFinder.leastDistToPos(predictedPos);
        ScaffoldTargetFinder.Options opts = new ScaffoldTargetFinder.Options(
                offsets, priority, aimMode,
                false, stackToPlaceWith, predictedPos, predictedEyePos, optimalLine,
                System.nanoTime(), preferHorizontal
        );
        return ScaffoldTargetFinder.findBest(targetPos, opts);
    }
}
