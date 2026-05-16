package dev.mzc.client.module.impl.movement.scaffold;

import dev.mzc.client.utils.vector.Rotation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Stateful GodBridge technique.
 *
 * Mirrors LB's ScaffoldGodBridgeTechnique:
 *   - yaw snapping happens on the ground tick (when player.onGround).
 *   - the chosen yaw + pitch is locked until either:
 *       * the player lands again (next ground tick), or
 *       * the snapped movement direction (movingYaw) changes.
 *   - getRotations:
 *       * straight movement (yaw is multiple of 90°) -> movingYaw ± 45, pitch 75.7
 *       * diagonal movement                          -> movingYaw, pitch 75.6
 *       * no input                                   -> floor(targetYaw/90)*90 + 45, pitch 75
 */
public final class GodBridgeState {
    private boolean isOnRightSide = false;

    // Last computed lock — kept across ticks so the rotation stays still until movement changes.
    private Float lockedYaw = null;
    private Float lockedPitch = null;
    private Float lockedMovingYaw = null;     // value of movingYaw used to compute the lock
    private boolean lastInputWasNone = false; // flag: last computation was for "no input"

    public void reset() {
        isOnRightSide = false;
        lockedYaw = null;
        lockedPitch = null;
        lockedMovingYaw = null;
        lastInputWasNone = false;
    }

    /**
     * Compute / re-use the locked GodBridge target.
     */
    public BlockPlacementTarget compute(
            BlockPos basePos, Vec3d predictedPos, Vec3d predictedEyePos,
            ScaffoldMovementPlanner.Line optimalLine, ItemStack stackToPlaceWith
    ) {
        var mc = MinecraftClient.getInstance();
        PlayerEntity p = mc.player;
        if (p == null) return null;

        // Use the cheapest possible finder: we just need a candidate block to fill blockHitResult/etc.
        var priority = ScaffoldTargetFinder.leastDistToPos(predictedPos);
        ScaffoldTargetFinder.Options opts = new ScaffoldTargetFinder.Options(
                ScaffoldTargetFinder.Offsets.NORMAL, priority,
                ScaffoldTargetFinder.AimMode.Center,
                false, stackToPlaceWith, predictedPos, predictedEyePos, optimalLine,
                0L
        );
        BlockPlacementTarget t = ScaffoldTargetFinder.findBest(basePos, opts);
        if (t == null) return null;

        Rotation lockedRot = pickRotation(p, t);
        return new BlockPlacementTarget(
                t.interactedBlockPos(), t.placedBlock(), t.direction(),
                t.minPlacementY(), t.hitVec(), lockedRot
        );
    }

    private Rotation pickRotation(PlayerEntity p, BlockPlacementTarget t) {
        float playerYaw = p.getYaw();
        float forward = p.forwardSpeed;
        float strafe = p.sidewaysSpeed;
        boolean noInput = forward == 0 && strafe == 0;

        // Compute current movingYaw if there is input
        Float currentMovingYaw = null;
        if (!noInput) {
            float moveDir = ScaffoldMovementPlanner.getMovementDirectionOfInput(playerYaw, forward, strafe) + 180f;
            currentMovingYaw = (float) (Math.round(moveDir / 45f) * 45f);
        }

        // We re-lock ONLY when:
        //   - we have no lock yet, or
        //   - movingYaw value (snapped to 45°) actually changed, or
        //   - input mode flipped (no-input <-> moving)
        // We do NOT re-lock just because isOnGround toggled — that breaks Eagle (sneak resets
        // ground state every block placement, causing the yaw to flip between ±45 sides).
        boolean shouldRecompute = lockedYaw == null
                || noInput != lastInputWasNone
                || (currentMovingYaw != null && (lockedMovingYaw == null
                        || !lockedMovingYaw.equals(currentMovingYaw)));

        if (!shouldRecompute && lockedYaw != null && lockedPitch != null) {
            return new Rotation(lockedYaw, lockedPitch);
        }

        // === recompute ===
        if (noInput) {
            float axis = (float) (Math.floor(t.rotation().yaw / 90f) * 90f);
            lockedYaw = axis + 45f;
            lockedPitch = 75f;
            lockedMovingYaw = null;
            lastInputWasNone = true;
            return new Rotation(lockedYaw, lockedPitch);
        }

        float movingYaw = currentMovingYaw;
        boolean isStraight = movingYaw % 90f == 0f;

        if (isStraight) {
            // Decide which side of the block we're on. We compute this only when we actually
            // re-lock (i.e. movingYaw changed) — not every ground tick — so that Eagle/sneak
            // doesn't make the side flip mid-bridge.
            double rad = Math.toRadians(movingYaw);
            double cosY = Math.cos(rad);
            double sinY = Math.sin(rad);
            isOnRightSide = Math.floor(p.getX() + cosY * 0.5) != Math.floor(p.getX())
                    || Math.floor(p.getZ() + sinY * 0.5) != Math.floor(p.getZ());

            // LB also flips when leaning off block
            BlockPos belowPos = BlockPos.ofFloored(p.getX(), p.getY() - 1, p.getZ());
            BlockPos aheadPos = BlockPos.ofFloored(
                    p.getX() + cosY * 0.6,
                    p.getY() - 1,
                    p.getZ() + sinY * 0.6
            );
            if (mc().world != null) {
                boolean leaningOff = mc().world.getBlockState(belowPos).isAir();
                boolean nextAir = mc().world.getBlockState(aheadPos).isAir();
                if (leaningOff && nextAir) isOnRightSide = !isOnRightSide;
            }

            lockedYaw = movingYaw + (isOnRightSide ? 45f : -45f);
            lockedPitch = 75.7f;
        } else {
            lockedYaw = movingYaw;
            lockedPitch = 75.6f;
        }
        lockedMovingYaw = movingYaw;
        lastInputWasNone = false;

        // Clamp pitch
        lockedPitch = MathHelper.clamp(lockedPitch, -90f, 90f);
        return new Rotation(lockedYaw, lockedPitch);
    }

    private static MinecraftClient mc() { return MinecraftClient.getInstance(); }
}
