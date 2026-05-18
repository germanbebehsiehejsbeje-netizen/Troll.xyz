package dev.mzc.client.module.impl.movement.scaffold;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Port of LB's ScaffoldMovementPlanner.
 *
 * Tries to compute the optimal "movement line" the player should walk along while bridging.
 * The line is a Vec3 ray (origin + direction). Used by Stabilized rotation factory to
 * keep yaw consistent.
 */
public final class ScaffoldMovementPlanner {
    public static final int MAX_LAST_PLACED = 4;

    public record Line(Vec3d position, Vec3d direction) {
        /** Nearest point to a 3D position (ignoring Y of point if lineDirY=0). */
        public Vec3d nearestPointTo(Vec3d p) {
            Vec3d d = direction;
            Vec3d toP = p.subtract(position);
            double t = toP.dotProduct(d) / d.lengthSquared();
            return position.add(d.multiply(t));
        }

        /** Squared distance from line to a point. */
        public double distSqTo(Vec3d p) {
            Vec3d nearest = nearestPointTo(p);
            return nearest.squaredDistanceTo(p);
        }
    }

    private static final Deque<BlockPos> lastPlacedBlocks = new ArrayDeque<>(MAX_LAST_PLACED);
    private static BlockPos lastPosition = null;

    private static final double[] OFFSETS_TO_TRY = {0.301, 0.0, -0.301};

    /**
     * Snap an arbitrary world-direction vector to one of 8 cardinal/diagonal directions (y=0).
     * Public so the main module can use it for input correction.
     */
    public static Vec3d snapDirectionTo8(Vec3d worldDir) {
        if (worldDir.lengthSquared() < 1e-6) return Vec3d.ZERO;
        double yawDeg = Math.toDegrees(Math.atan2(-worldDir.x, worldDir.z));
        return chooseDirection((float) MathHelper.wrapDegrees(yawDeg));
    }

    public static Line getOptimalMovementLine(float forward, float strafe) {
        if (forward == 0 && strafe == 0) return null;

        var mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return null;

        Vec3d direction = chooseDirection(getMovementDirectionOfInput(mc.player.getYaw(), forward, strafe));

        BlockPos blockUnder = findBlockPlayerStandsOn();
        if (blockUnder == null) return null;

        Line lastLine = fitLineThroughLastPlaced();

        Vec3d base;
        if (lastLine != null && !divergesTooMuchFromDirection(lastLine, direction)) {
            base = lastLine.position;
        } else {
            base = Vec3d.of(blockUnder);
        }

        return new Line(
                new Vec3d(base.x + 0.5, mc.player.getY(), base.z + 0.5),
                direction
        );
    }

    private static boolean divergesTooMuchFromDirection(Line line, Vec3d direction) {
        return line.direction.dotProduct(direction) < 0.5;
    }

    private static Line fitLineThroughLastPlaced() {
        if (lastPlacedBlocks.size() < 2) return null;
        BlockPos[] arr = lastPlacedBlocks.toArray(new BlockPos[0]);
        BlockPos last = arr[arr.length - 1];
        BlockPos secondToLast = arr[arr.length - 2];
        Vec3d a = Vec3d.of(secondToLast);
        Vec3d b = Vec3d.of(last);
        Vec3d avg = a.add(b).multiply(0.5);
        Vec3d dir = b.subtract(a).normalize();
        if (Double.isNaN(dir.x) || Double.isNaN(dir.z)) return null;
        return new Line(avg, dir);
    }

    private static BlockPos findBlockPlayerStandsOn() {
        var mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return null;

        Set<BlockPos> candidates = new HashSet<>();
        for (double xo : OFFSETS_TO_TRY) {
            for (double zo : OFFSETS_TO_TRY) {
                BlockPos pos = BlockPos.ofFloored(
                        mc.player.getX() + xo,
                        mc.player.getY() - 1.0,
                        mc.player.getZ() + zo
                );
                var state = mc.world.getBlockState(pos);
                if (!state.getCollisionShape(mc.world, pos).isEmpty()) {
                    candidates.add(pos);
                }
            }
        }

        if (!lastPlacedBlocks.isEmpty()) {
            BlockPos lastPlaced = lastPlacedBlocks.peekLast();
            if (candidates.contains(lastPlaced)) return lastPlaced;
        }
        if (lastPosition != null && candidates.contains(lastPosition)) return lastPosition;

        BlockPos any = candidates.stream().findFirst().orElse(null);
        lastPosition = any;
        return any;
    }

    /**
     * Snap an arbitrary yaw to one of 8 cardinal/diagonal directions and return its unit vector (y=0).
     */
    private static Vec3d chooseDirection(float currentAngle) {
        float currentDirection = currentAngle / 180f * 4 + 4;
        float roundedNum = Math.round(currentDirection);
        float newAngle = MathHelper.wrapDegrees((roundedNum - 4) / 4f * 180f);
        double rad = Math.toRadians(newAngle);
        return new Vec3d(-Math.sin(rad), 0.0, Math.cos(rad)).normalize();
    }

    /**
     * Compute the world-space direction yaw the player would actually move toward, given input.
     */
    public static float getMovementDirectionOfInput(float playerYaw, float forward, float strafe) {
        if (forward == 0 && strafe == 0) return playerYaw;
        float angle = (float) Math.toDegrees(Math.atan2(-strafe, forward));
        return MathHelper.wrapDegrees(playerYaw + angle);
    }

    public static void trackPlacedBlock(BlockPos pos) {
        if (!lastPlacedBlocks.isEmpty() && pos.equals(lastPlacedBlocks.peekLast())) return;
        while (lastPlacedBlocks.size() >= MAX_LAST_PLACED) {
            lastPlacedBlocks.pollFirst();
        }
        lastPlacedBlocks.addLast(pos);
    }

    public static void reset() {
        lastPlacedBlocks.clear();
        lastPosition = null;
    }
}
