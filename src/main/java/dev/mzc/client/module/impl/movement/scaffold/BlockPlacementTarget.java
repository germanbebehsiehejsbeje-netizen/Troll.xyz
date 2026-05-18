package dev.mzc.client.module.impl.movement.scaffold;

import dev.mzc.client.utils.vector.Rotation;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Port of LB's BlockPlacementTarget data class.
 *
 * @param interactedBlockPos block pos that is right-clicked
 * @param placedBlock        block pos at which the new block is placed
 * @param direction          face direction
 * @param minPlacementY      minimum Y of the click point on the face (relevant for slabs/stairs)
 * @param hitVec             exact hit vector on the face
 * @param rotation           rotation needed to look at the hitVec
 */
public record BlockPlacementTarget(
        BlockPos interactedBlockPos,
        BlockPos placedBlock,
        Direction direction,
        double minPlacementY,
        Vec3d hitVec,
        Rotation rotation
) {

    public BlockHitResult blockHitResult() {
        Vec3d center = Vec3d.ofCenter(interactedBlockPos);
        return new BlockHitResult(center, direction, interactedBlockPos, false);
    }

    /**
     * Check whether a vanilla raycast result corresponds to this placement.
     */
    public boolean doesCrosshairTargetMatchRequirements(BlockHitResult crosshairTarget) {
        if (crosshairTarget == null) return false;
        if (crosshairTarget.getType() != HitResult.Type.BLOCK) return false;
        if (!crosshairTarget.getBlockPos().equals(this.interactedBlockPos)) return false;
        if (crosshairTarget.getSide() != this.direction) return false;
        if (crosshairTarget.getPos().y < this.minPlacementY) return false;
        return true;
    }
}
