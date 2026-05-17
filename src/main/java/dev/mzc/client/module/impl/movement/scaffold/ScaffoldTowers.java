package dev.mzc.client.module.impl.movement.scaffold;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Port of LB tower modes:
 *   None       — no tower behavior; jump+place handled by the regular flow.
 *   Motion     — sets player Y velocity to a fixed jump speed every tick the jump key is held.
 *   Pulldown   — alternates Y motion between up/down for blatant tower used on most servers.
 *   Karhu      — Karhu-bypass tower (smaller delta, motion clamping).
 *   Vulcan     — Vulcan-bypass tower (force ground = false on motion ticks).
 *   Hypixel    — Hypixel-tuned tower (delayed jump cycle).
 *
 * Each tower is implemented as a stateful object since they need motion phase tracking.
 */
public final class ScaffoldTowers {
    public enum TowerMode { None, Motion, Pulldown, Karhu, Vulcan, Hypixel }

    public interface Tower {
        BlockPos getTargetedPosition(BlockPos basePos);
        /** Apply velocity / position adjustments. Called from MotionEvent.Pre. */
        void onMotion(PlayerEntity p);
        void reset();
    }

    public static Tower create(TowerMode mode) {
        return switch (mode) {
            case None -> new None();
            case Motion -> new Motion();
            case Pulldown -> new Pulldown();
            case Karhu -> new Karhu();
            case Vulcan -> new Vulcan();
            case Hypixel -> new Hypixel();
        };
    }

    /* ----------------- implementations ----------------- */

    public static class None implements Tower {
        @Override public BlockPos getTargetedPosition(BlockPos basePos) { return basePos; }
        @Override public void onMotion(PlayerEntity p) {}
        @Override public void reset() {}
    }

    public static class Motion implements Tower {
        @Override public BlockPos getTargetedPosition(BlockPos basePos) {
            return basePos.down();
        }
        @Override public void onMotion(PlayerEntity p) {
            if (!MinecraftClient.getInstance().options.jumpKey.isPressed()) return;
            if (!isJumpReady(p)) return;
            // Tower motion: fixed upward velocity each tick (matches LB's 0.42)
            p.setVelocity(p.getVelocity().x, 0.42, p.getVelocity().z);
            p.setOnGround(false);
        }
        @Override public void reset() {}
    }

    public static class Pulldown implements Tower {
        private boolean phase = false;
        @Override public BlockPos getTargetedPosition(BlockPos basePos) {
            return basePos.down();
        }
        @Override public void onMotion(PlayerEntity p) {
            if (!MinecraftClient.getInstance().options.jumpKey.isPressed()) return;
            phase = !phase;
            Vec3d v = p.getVelocity();
            if (phase) {
                p.setVelocity(v.x, 0.42, v.z);
            } else {
                p.setVelocity(v.x, -0.28, v.z);
            }
        }
        @Override public void reset() { phase = false; }
    }

    public static class Karhu implements Tower {
        private int tickCounter = 0;
        @Override public BlockPos getTargetedPosition(BlockPos basePos) {
            return basePos.down();
        }
        @Override public void onMotion(PlayerEntity p) {
            if (!MinecraftClient.getInstance().options.jumpKey.isPressed()) return;
            tickCounter++;
            Vec3d v = p.getVelocity();
            // Karhu pattern: 0.42 -> -0.0784 -> 0.33319999363
            switch (tickCounter % 3) {
                case 0 -> p.setVelocity(v.x, 0.42, v.z);
                case 1 -> p.setVelocity(v.x, -0.0784, v.z);
                case 2 -> p.setVelocity(v.x, 0.33319999363, v.z);
            }
        }
        @Override public void reset() { tickCounter = 0; }
    }

    public static class Vulcan implements Tower {
        private int tickCounter = 0;
        @Override public BlockPos getTargetedPosition(BlockPos basePos) {
            return basePos.down();
        }
        @Override public void onMotion(PlayerEntity p) {
            if (!MinecraftClient.getInstance().options.jumpKey.isPressed()) return;
            tickCounter++;
            Vec3d v = p.getVelocity();
            // Vulcan uses 0.42 -> 0.0 -> 0.42 alternation with onGround spoof
            if (tickCounter % 2 == 0) {
                p.setVelocity(v.x, 0.42, v.z);
                p.setOnGround(false);
            } else {
                p.setVelocity(v.x, 0.0, v.z);
            }
        }
        @Override public void reset() { tickCounter = 0; }
    }

    public static class Hypixel implements Tower {
        private int delay = 0;
        @Override public BlockPos getTargetedPosition(BlockPos basePos) {
            return basePos.down();
        }
        @Override public void onMotion(PlayerEntity p) {
            if (!MinecraftClient.getInstance().options.jumpKey.isPressed()) return;
            // Hypixel needs a measured cadence: jump every ~2 ticks
            if (delay <= 0) {
                Vec3d v = p.getVelocity();
                if (isJumpReady(p)) {
                    p.setVelocity(v.x, 0.42, v.z);
                    delay = 2;
                }
            } else {
                delay--;
            }
        }
        @Override public void reset() { delay = 0; }
    }

    private static boolean isJumpReady(PlayerEntity p) {
        return p.isOnGround() || (p.getVelocity().y < 0.01 && p.getVelocity().y > -0.01);
    }
}
