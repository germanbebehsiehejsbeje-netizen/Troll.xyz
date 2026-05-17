package dev.mzc.client.module.impl.movement.scaffold;

import dev.mzc.client.utils.rotation.RotationUtil;
import dev.mzc.client.utils.vector.Rotation;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Compact port of LB's BlockPlacementTarget finder.
 *
 * Given a "target" block position (where to place a block) and a set of offsets to investigate,
 * tries each offset as a candidate placement, picks a neighbor block to right-click, and chooses
 * a hit point on the neighbor's face according to {@link AimMode}.
 */
public final class ScaffoldTargetFinder {
    private ScaffoldTargetFinder() {}

    public enum AimMode {
        Center,
        Random,
        Stabilized,
        NearestRotation,
        ReverseYaw,
        DiagonalYaw,
        AngleYaw,
        EdgePoint
    }

    /**
     * Predefined offset sets used by techniques.
     */
    public static final class Offsets {
        public static final List<Vec3i> NO_OFFSET = List.of(new Vec3i(0, 0, 0));
        public static final List<Vec3i> NORMAL;
        public static final List<Vec3i> FULL;
        public static final List<Vec3i> DOWN;
        public static final List<Vec3i> EXPAND;

        static {
            // NORMAL: target & 4 horizontal neighbors (1-block away)
            NORMAL = new ArrayList<>();
            NORMAL.add(new Vec3i(0, 0, 0));
            NORMAL.add(new Vec3i(1, 0, 0));
            NORMAL.add(new Vec3i(-1, 0, 0));
            NORMAL.add(new Vec3i(0, 0, 1));
            NORMAL.add(new Vec3i(0, 0, -1));

            // FULL: 5x3x5 cube
            List<Vec3i> full = new ArrayList<>();
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    for (int y = -1; y <= 1; y++) {
                        full.add(new Vec3i(x, y, z));
                    }
                }
            }
            FULL = full;

            // DOWN: same column, downward
            List<Vec3i> down = new ArrayList<>();
            for (int y = 0; y >= -3; y--) down.add(new Vec3i(0, y, 0));
            DOWN = down;

            // EXPAND: large flat ring around player at same Y for expand technique
            List<Vec3i> expand = new ArrayList<>();
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    expand.add(new Vec3i(x, 0, z));
                }
            }
            EXPAND = expand;
        }
    }

    /**
     * Configuration for a placement search.
     */
    public static final class Options {
        public final List<Vec3i> offsets;
        public final Comparator<BlockPos> priority;
        public final AimMode aimMode;
        public final boolean considerFacingAwayFaces;
        public final ItemStack stackToPlaceWith;
        public final Vec3d predictedPos;        // player position used for placement decision
        public final Vec3d predictedEyePos;     // player eye position (for raycast / prefs)
        public final ScaffoldMovementPlanner.Line optimalLine; // may be null
        public final long randomizationSeed;
        /** When true, placements that interact with the bottom face of a block above (Direction.UP) are preferred less. */
        public final boolean preferHorizontal;

        public Options(List<Vec3i> offsets, Comparator<BlockPos> priority, AimMode aimMode,
                       boolean considerFacingAwayFaces, ItemStack stackToPlaceWith,
                       Vec3d predictedPos, Vec3d predictedEyePos,
                       ScaffoldMovementPlanner.Line optimalLine,
                       long randomizationSeed, boolean preferHorizontal) {
            this.offsets = offsets;
            this.priority = priority;
            this.aimMode = aimMode;
            this.considerFacingAwayFaces = considerFacingAwayFaces;
            this.stackToPlaceWith = stackToPlaceWith;
            this.predictedPos = predictedPos;
            this.predictedEyePos = predictedEyePos;
            this.optimalLine = optimalLine;
            this.randomizationSeed = randomizationSeed;
            this.preferHorizontal = preferHorizontal;
        }

        // Backwards-compatible 10-arg constructor (preferHorizontal=true by default)
        public Options(List<Vec3i> offsets, Comparator<BlockPos> priority, AimMode aimMode,
                       boolean considerFacingAwayFaces, ItemStack stackToPlaceWith,
                       Vec3d predictedPos, Vec3d predictedEyePos,
                       ScaffoldMovementPlanner.Line optimalLine,
                       long randomizationSeed) {
            this(offsets, priority, aimMode, considerFacingAwayFaces, stackToPlaceWith,
                    predictedPos, predictedEyePos, optimalLine, randomizationSeed, true);
        }
    }

    /** "least block distance to player" priority — used when no optimal line. */
    public static Comparator<BlockPos> leastDistToPos(Vec3d pos) {
        return Comparator.comparingDouble(p -> -Vec3d.ofCenter(p).squaredDistanceTo(pos));
    }

    /** "least distance to optimal movement line" priority. */
    public static Comparator<BlockPos> leastDistToLine(ScaffoldMovementPlanner.Line line) {
        return Comparator.comparingDouble(p -> -line.distSqTo(Vec3d.ofCenter(p)));
    }

    /**
     * Main API: find best block placement target near {@code targetPos} using {@code options}.
     */
    public static BlockPlacementTarget findBest(BlockPos targetPos, Options options) {
        var mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return null;

        BlockState targetState = mc.world.getBlockState(targetPos);
        if (isBlockSolid(targetState, targetPos)) return null;

        List<Vec3i> sorted = new ArrayList<>(options.offsets);
        sorted.sort((a, b) -> options.priority.compare(targetPos.add(b), targetPos.add(a)));

        for (Vec3i offset : sorted) {
            BlockPos candidate = targetPos.add(offset);
            BlockState candidateState = mc.world.getBlockState(candidate);
            if (isBlockSolid(candidateState, candidate)) continue;

            // Find a valid neighbor face to click on
            BlockPlacementTarget target = findFaceForCandidate(candidate, options);
            if (target != null) return target;
        }

        return null;
    }

    private static BlockPlacementTarget findFaceForCandidate(BlockPos candidate, Options options) {
        var mc = MinecraftClient.getInstance();
        PlayerEntity player = mc.player;
        if (player == null || mc.world == null) return null;

        Direction[] dirs = Direction.values();
        Direction bestDir = null;
        BlockPos bestNeighborPos = null;
        Vec3d bestHit = null;
        double bestScore = Double.POSITIVE_INFINITY;
        Rotation bestRot = null;

        Rotation currentServerRot = new Rotation(player.getYaw(), player.getPitch());

        for (Direction dir : dirs) {
            BlockPos neighborPos = candidate.offset(dir.getOpposite());
            BlockState neighborState = mc.world.getBlockState(neighborPos);
            if (neighborState.isAir() || neighborState.isReplaceable()) continue;

            // Direction the face points (towards candidate from neighbor)
            Direction face = dir;

            // Check facing-away (face must be visible to player eye unless allowed)
            if (!options.considerFacingAwayFaces) {
                Vec3d faceCenter = Vec3d.ofCenter(neighborPos).add(
                        face.getOffsetX() * 0.5,
                        face.getOffsetY() * 0.5,
                        face.getOffsetZ() * 0.5
                );
                Vec3d toEye = options.predictedEyePos.subtract(faceCenter);
                Vec3d normal = new Vec3d(face.getOffsetX(), face.getOffsetY(), face.getOffsetZ());
                if (toEye.dotProduct(normal) < 0) continue;
            }

            Vec3d hit = produceHitOnFace(neighborPos, face, options);
            if (hit == null) continue;

            // distance check (stay within reach 4.5)
            if (hit.distanceTo(options.predictedEyePos) > 5.0) continue;

            Rotation rot = RotationUtil.calculate(hit);
            float yawDiff = MathHelper.wrapDegrees(rot.yaw - currentServerRot.yaw);
            float pitchDiff = rot.pitch - currentServerRot.pitch;
            double score = yawDiff * yawDiff + pitchDiff * pitchDiff;

            // Strongly de-prioritize placing on top of a block above us (Direction.UP face of the
            // upper block) when we're bridging horizontally. Without this the finder may pick a
            // ceiling-style placement instead of the side face we actually want.
            if (options.preferHorizontal && face == Direction.UP) {
                score += 5000.0;
            }
            // Slightly de-prioritize DOWN faces too (bottom of a block above) — same reason.
            if (options.preferHorizontal && face == Direction.DOWN && neighborPos.getY() > options.predictedEyePos.y) {
                score += 2500.0;
            }

            if (score < bestScore) {
                bestScore = score;
                bestDir = face;
                bestNeighborPos = neighborPos;
                bestHit = hit;
                bestRot = rot;
            }
        }

        if (bestDir == null) return null;
        return new BlockPlacementTarget(bestNeighborPos, candidate, bestDir, bestHit.y - bestNeighborPos.getY(), bestHit, bestRot);
    }

    /**
     * Pick a hit point on a block's specific face according to AimMode.
     */
    private static Vec3d produceHitOnFace(BlockPos blockPos, Direction face, Options options) {
        Random rnd = new Random(options.randomizationSeed ^ blockPos.hashCode() ^ face.hashCode());

        // Face center on the surface in [0,1]^3 local coordinates
        double cx = 0.5, cy = 0.5, cz = 0.5;
        switch (face) {
            case UP -> cy = 1.0;
            case DOWN -> cy = 0.0;
            case NORTH -> cz = 0.0;
            case SOUTH -> cz = 1.0;
            case WEST -> cx = 0.0;
            case EAST -> cx = 1.0;
        }

        double offsetA = 0, offsetB = 0;
        switch (options.aimMode) {
            case Center -> { offsetA = 0; offsetB = 0; }
            case Random -> {
                offsetA = (rnd.nextDouble() - 0.5) * 0.8;
                offsetB = (rnd.nextDouble() - 0.5) * 0.8;
            }
            case Stabilized -> {
                // Try to aim at the side of the block closest to the optimal movement line.
                if (options.optimalLine != null) {
                    Vec3d nearest = options.optimalLine.nearestPointTo(Vec3d.ofCenter(blockPos));
                    Vec3d local = nearest.subtract(Vec3d.of(blockPos));
                    if (face.getAxis() == Direction.Axis.Y) {
                        offsetA = MathHelper.clamp(local.x - 0.5, -0.4, 0.4);
                        offsetB = MathHelper.clamp(local.z - 0.5, -0.4, 0.4);
                    } else if (face.getAxis() == Direction.Axis.X) {
                        offsetA = MathHelper.clamp(local.z - 0.5, -0.4, 0.4);
                        offsetB = MathHelper.clamp(local.y - 0.5, -0.4, 0.4);
                    } else {
                        offsetA = MathHelper.clamp(local.x - 0.5, -0.4, 0.4);
                        offsetB = MathHelper.clamp(local.y - 0.5, -0.4, 0.4);
                    }
                } else {
                    offsetA = (rnd.nextDouble() - 0.5) * 0.2;
                    offsetB = (rnd.nextDouble() - 0.5) * 0.2;
                }
            }
            case NearestRotation -> {
                offsetA = 0;
                offsetB = -0.1;
            }
            case ReverseYaw -> {
                // Aim at the opposite side of the face — used by some bypasses
                offsetA = 0;
                offsetB = 0.45;
            }
            case DiagonalYaw -> {
                offsetA = 0.4;
                offsetB = 0.4;
            }
            case AngleYaw -> {
                offsetA = 0.3 * Math.signum(rnd.nextDouble() - 0.5);
                offsetB = 0.3 * Math.signum(rnd.nextDouble() - 0.5);
            }
            case EdgePoint -> {
                offsetA = (rnd.nextBoolean() ? 1 : -1) * 0.45;
                offsetB = (rnd.nextBoolean() ? 1 : -1) * 0.45;
            }
        }

        switch (face) {
            case UP, DOWN -> {
                cx = MathHelper.clamp(cx + offsetA, 0.05, 0.95);
                cz = MathHelper.clamp(cz + offsetB, 0.05, 0.95);
            }
            case NORTH, SOUTH -> {
                cx = MathHelper.clamp(cx + offsetA, 0.05, 0.95);
                cy = MathHelper.clamp(cy + offsetB, 0.05, 0.95);
            }
            case EAST, WEST -> {
                cz = MathHelper.clamp(cz + offsetA, 0.05, 0.95);
                cy = MathHelper.clamp(cy + offsetB, 0.05, 0.95);
            }
        }

        return new Vec3d(blockPos.getX() + cx, blockPos.getY() + cy, blockPos.getZ() + cz);
    }

    private static boolean isBlockSolid(BlockState state, BlockPos pos) {
        var mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;
        try {
            return state.isSideSolidFullSquare(mc.world, pos, Direction.UP);
        } catch (Throwable t) {
            return state.isSolid();
        }
    }
}
