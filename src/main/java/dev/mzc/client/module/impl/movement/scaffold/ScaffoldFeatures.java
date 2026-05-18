package dev.mzc.client.module.impl.movement.scaffold;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Bundle of scaffold sub-features adapted from LB.
 *
 * Each feature carries its own state. They expose simple toggles & methods invoked from {@link
 * dev.mzc.client.module.impl.movement.Scaffold}.
 */
public final class ScaffoldFeatures {

    /* ============== Eagle (sneak before placing) ============== */
    public static final class Eagle {
        public boolean enabled = false;
        public int blocksPlacedSinceLast = 0;
        public int eagleEvery = 1; // sneak every N blocks
        public boolean shouldEagle(boolean isMoving) {
            if (!enabled) return false;
            return blocksPlacedSinceLast >= eagleEvery && isMoving;
        }
        public void onBlockPlacement() {
            blocksPlacedSinceLast = 0;
        }
        public void onTick() {
            blocksPlacedSinceLast++;
        }
        public void reset() { blocksPlacedSinceLast = 0; }
    }

    /* ============== Down (sneak to go down) ============== */
    public static final class Down {
        public boolean enabled = false;
        /** Activated when the player holds sneak. Decided externally and re-checked here. */
        public boolean shouldGoDown() {
            if (!enabled) return false;
            return MinecraftClient.getInstance().options.sneakKey.isPressed();
        }
    }

    /* ============== Telly (move-then-place pattern) ============== */
    public static final class Telly {
        public enum Mode { Reset, Reverse }
        public boolean enabled = false;
        public Mode resetMode = Mode.Reset;
        public int placeEvery = 4;
        public int tickCounter = 0;
        public boolean doNotAim = false;

        public boolean canPlace() {
            if (!enabled) return true;
            tickCounter++;
            if (tickCounter < placeEvery) {
                doNotAim = true;
                return false;
            }
            doNotAim = false;
            tickCounter = 0;
            return true;
        }
        public void reset() { tickCounter = 0; doNotAim = false; }
    }

    /* ============== HeadHitter ============== */
    public static final class HeadHitter {
        public boolean enabled = false;
        public boolean isHittingHead(PlayerEntity p) {
            if (!enabled || p == null) return false;
            return p.horizontalCollision && p.getVelocity().y > 0;
        }
    }

    /* ============== Ledge (auto-jump / auto-sneak at edges) ============== */
    public static final class Ledge {
        public enum Mode { Jump, Sneak, StopInput, Backwards }

        public boolean enabled = true;
        /** Currently active mode (used by Normal technique and as a fallback). */
        public Mode mode = Mode.Jump;
        /** Force this technique-specific mode if non-null. Used by GodBridge. */
        public Mode forcedMode = null;
        public int sneakTimeMin = 1;
        public int sneakTimeMax = 1;

        public record LedgeAction(boolean jump, boolean stopInput, boolean stepBack, int sneakTime) {
            public static final LedgeAction NONE = new LedgeAction(false, false, false, 0);
        }

        public LedgeAction compute(PlayerEntity p, BlockPlacementTarget target) {
            if (!enabled || p == null) return LedgeAction.NONE;

            // Detect "about to fall off ledge" — we are on ground or just stepped off,
            // and there is no support block under our forward step.
            if (!isAboutToLedge(p)) return LedgeAction.NONE;

            // If we already have a placement target that can be reached this tick — no ledge action.
            if (target != null && isTargetReachableSoon(p, target)) return LedgeAction.NONE;

            Mode m = forcedMode != null ? forcedMode : mode;
            return switch (m) {
                case Jump -> new LedgeAction(true, false, false, 0);
                case Sneak -> {
                    int t = sneakTimeMin == sneakTimeMax
                            ? sneakTimeMin
                            : sneakTimeMin + (int) (Math.random() * (sneakTimeMax - sneakTimeMin + 1));
                    yield new LedgeAction(false, false, false, t);
                }
                case StopInput -> new LedgeAction(false, true, false, 0);
                case Backwards -> new LedgeAction(false, false, true, 0);
            };
        }

        /** True when the player is at the edge of a block and about to walk off. */
        private boolean isAboutToLedge(PlayerEntity p) {
            var mc = MinecraftClient.getInstance();
            if (mc.world == null) return false;

            // Predict position 1-2 ticks ahead using velocity + input direction.
            // We are about to ledge if the predicted footing block is air/empty.
            Vec3d v = p.getVelocity();
            double speedH = Math.hypot(v.x, v.z);

            // Direction: prefer velocity if moving, otherwise yaw
            double fx, fz;
            if (speedH > 0.05) {
                fx = v.x / speedH;
                fz = v.z / speedH;
            } else {
                double rad = Math.toRadians(p.getYaw());
                fx = -Math.sin(rad);
                fz = Math.cos(rad);
            }

            // Step ahead 0.6 (≈ a player-radius) and check support directly under that point
            double stepDist = Math.max(0.45, speedH * 2.0); // longer look-ahead when fast
            stepDist = Math.min(stepDist, 1.0);
            double px = p.getX() + fx * stepDist;
            double pz = p.getZ() + fz * stepDist;

            // Check 3 height samples to catch slabs/stairs as well
            for (double dy = 0.0; dy >= -1.0; dy -= 0.5) {
                BlockPos bp = BlockPos.ofFloored(px, p.getY() + dy - 0.05, pz);
                var state = mc.world.getBlockState(bp);
                if (!state.isAir() && !state.getCollisionShape(mc.world, bp).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        /** Check if the placement target is close enough to be placed on this/next tick. */
        private boolean isTargetReachableSoon(PlayerEntity p, BlockPlacementTarget target) {
            Vec3d eye = new Vec3d(p.getX(), p.getY() + p.getStandingEyeHeight(), p.getZ());
            return target.hitVec().distanceTo(eye) < 5.0;
        }
    }

    /* ============== JumpStrafe ============== */
    public static final class JumpStrafe {
        public boolean enabled = false;
        public float strafeSpeed = 0.20f;

        public void apply(PlayerEntity p) {
            if (!enabled || p == null) return;
            if (p.isOnGround()) return;
            // Slightly amplify horizontal velocity to mimic strafe-jumps
            Vec3d v = p.getVelocity();
            double mag = Math.hypot(v.x, v.z);
            if (mag < 0.05) return;
            double newMag = Math.min(mag * (1.0 + strafeSpeed * 0.05), 0.30);
            double scale = newMag / mag;
            p.setVelocity(v.x * scale, v.y, v.z * scale);
        }
    }

    /* ============== SpeedLimiter ============== */
    public static final class SpeedLimiter {
        public boolean enabled = false;
        public double maxHorizontalSpeed = 0.281;

        public void apply(PlayerEntity p) {
            if (!enabled || p == null) return;
            Vec3d v = p.getVelocity();
            double mag = Math.hypot(v.x, v.z);
            if (mag <= maxHorizontalSpeed) return;
            double scale = maxHorizontalSpeed / mag;
            p.setVelocity(v.x * scale, v.y, v.z * scale);
        }
    }

    /* ============== SprintControl ============== */
    public static final class SprintControl {
        public enum Mode { DoNotChange, ForceSprint, ForceNoSprint }
        public boolean enabled = false;
        public Mode clientMode = Mode.ForceNoSprint;
        public Mode serverMode = Mode.ForceNoSprint;

        public Boolean overrideClientSprint() {
            if (!enabled) return null;
            return switch (clientMode) {
                case ForceSprint -> true;
                case ForceNoSprint -> false;
                case DoNotChange -> null;
            };
        }
        public Boolean overrideServerSprint() {
            if (!enabled) return null;
            return switch (serverMode) {
                case ForceSprint -> true;
                case ForceNoSprint -> false;
                case DoNotChange -> null;
            };
        }
        public void onBlockPlacement() {}
    }

    /* ============== Strafe ============== */
    public static final class Strafe {
        public boolean enabled = false;
        public double strafeAccel = 0.07;

        public void apply(PlayerEntity p, float forward, float strafe) {
            if (!enabled || p == null) return;
            if (forward == 0 && strafe == 0) return;
            float yaw = p.getYaw();
            double rad = Math.toRadians(yaw);
            double mx = -Math.sin(rad) * forward + Math.cos(rad) * -strafe;
            double mz = Math.cos(rad) * forward + Math.sin(rad) * strafe;
            double len = Math.hypot(mx, mz);
            if (len < 0.001) return;
            mx /= len; mz /= len;
            Vec3d v = p.getVelocity();
            p.setVelocity(v.x + mx * strafeAccel, v.y, v.z + mz * strafeAccel);
        }
    }

    /* ============== Acceleration ============== */
    public static final class Acceleration {
        public boolean enabled = false;
        public double boost = 1.05;

        public void apply(PlayerEntity p) {
            if (!enabled || p == null) return;
            Vec3d v = p.getVelocity();
            p.setVelocity(v.x * boost, v.y, v.z * boost);
        }
    }

    /* ============== Blink (queue placement packets briefly) ============== */
    public static final class Blink {
        public boolean enabled = false;
        public int delay = 0;
        public int waitTicks = 0;

        public boolean shouldHoldPackets() {
            return enabled && waitTicks > 0;
        }
        public void onBlockPlacement() {
            if (enabled) waitTicks = delay;
        }
        public void tick() {
            if (waitTicks > 0) waitTicks--;
        }
        public void reset() { waitTicks = 0; }
    }

    /* ============== Ceiling (build above player) ============== */
    public static final class Ceiling {
        public boolean enabled = false;
        public boolean canConstructCeiling(PlayerEntity p) {
            if (!enabled || p == null) return false;
            // Up-key + jump pressed
            return MinecraftClient.getInstance().options.jumpKey.isPressed()
                    && MinecraftClient.getInstance().options.forwardKey.isPressed();
        }
    }

    /* ============== Movement Prediction (peek next position) ============== */
    public static final class MovementPrediction {
        public boolean enabled = true;
        public double lookAhead = 0.2;
        public Vec3d predict(PlayerEntity p, ScaffoldMovementPlanner.Line optimalLine) {
            if (p == null) return Vec3d.ZERO;
            Vec3d cur = new Vec3d(p.getX(), p.getY(), p.getZ());
            if (!enabled) return cur;
            // Step ahead by lookAhead blocks along velocity (or line direction).
            Vec3d v = p.getVelocity();
            double speedH = Math.hypot(v.x, v.z);
            if (speedH < 0.05) return cur;
            Vec3d dir = new Vec3d(v.x / speedH, 0, v.z / speedH);
            return cur.add(dir.multiply(lookAhead));
        }
        public void onPlace() {}
        public void reset() {}
    }

    /* ============== StabilizeMovement (ease to grid lines) ============== */
    public static final class StabilizeMovement {
        public boolean enabled = false;
        // Pulls the player slightly toward the optimal line every tick
        public void apply(PlayerEntity p, ScaffoldMovementPlanner.Line line) {
            if (!enabled || p == null || line == null) return;
            Vec3d cur = new Vec3d(p.getX(), p.getY(), p.getZ());
            Vec3d nearest = line.nearestPointTo(cur);
            double dx = nearest.x - cur.x;
            double dz = nearest.z - cur.z;
            double m = Math.hypot(dx, dz);
            if (m < 0.001 || m > 1.0) return;
            double pull = 0.02;
            Vec3d v = p.getVelocity();
            p.setVelocity(v.x + (dx / m) * pull, v.y, v.z + (dz / m) * pull);
        }
    }

    /* ============== AutoBlock (silent slot selection) ============== */
    public static final class AutoBlock {
        public boolean enabled = true;
        public boolean alwaysHoldBlock = false;
        public int doNotUseBelowCount = 0;
        public int slotResetDelay = 1;

        private int previousSlot = -1;
        private int holdTicks = 0;

        public int findBestHotbarSlot(PlayerEntity p) {
            if (p == null) return -1;
            int best = -1;
            int bestCount = -1;
            for (int i = 0; i < 9; i++) {
                var stack = p.getInventory().getStack(i);
                if (!ScaffoldBlockSelection.isValidBlock(stack)) continue;
                if (stack.getCount() <= doNotUseBelowCount) continue;
                if (stack.getCount() > bestCount) { bestCount = stack.getCount(); best = i; }
            }
            // Fallback ignoring count threshold
            if (best == -1) {
                for (int i = 0; i < 9; i++) {
                    var stack = p.getInventory().getStack(i);
                    if (!ScaffoldBlockSelection.isValidBlock(stack)) continue;
                    if (stack.getCount() > bestCount) { bestCount = stack.getCount(); best = i; }
                }
            }
            return best;
        }

        public boolean swap(PlayerEntity p) {
            if (!enabled) return false;
            int target = findBestHotbarSlot(p);
            if (target == -1) return false;
            int current = p.getInventory().getSelectedSlot();
            if (current == target) return true;
            if (previousSlot == -1) previousSlot = current;
            p.getInventory().setSelectedSlot(target);
            holdTicks = slotResetDelay;
            return true;
        }

        public void onTick(PlayerEntity p) {
            if (!enabled || p == null) return;
            if (holdTicks > 0) {
                holdTicks--;
                if (holdTicks == 0 && !alwaysHoldBlock && previousSlot != -1) {
                    p.getInventory().setSelectedSlot(previousSlot);
                    previousSlot = -1;
                }
            }
        }
        public void reset(PlayerEntity p) {
            if (p != null && previousSlot != -1) {
                p.getInventory().setSelectedSlot(previousSlot);
            }
            previousSlot = -1;
            holdTicks = 0;
        }
    }
}
