package dev.mzc.client.module.impl.misc.cheatdetector.checks.movement;

import dev.mzc.client.module.impl.misc.cheatdetector.CheatCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.CheatDetectorContext;
import dev.mzc.client.module.impl.misc.cheatdetector.TrackedPlayer;
import dev.mzc.client.values.impl.BoolValue;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class StrafeACheck implements CheatCheck {
    private static final int[] MOVEMENT = {1, 0, -1};
    private static final double MAX_DIFF_TO_FLAG = 0.005;

    private final BoolValue enabled;
    private final Map<UUID, Set<Vec3d>> futureMotionByPlayer = new HashMap<>();

    public StrafeACheck(BoolValue enabled) {
        this.enabled = enabled;
    }

    @Override
    public void onTick(PlayerEntity player, TrackedPlayer tp, CheatDetectorContext ctx) {
        if (!enabled.get()) return;

        UUID id = player.getUuid();
        Vec3d motion = tp.currentTickMotion();

        if (tp.currentOnGround()
                || isNoMove(motion)
                || tp.lastAbsYawDelta() < 5.0
                || player.isGliding()
                || player.hasVehicle()) {
            futureMotionByPlayer.remove(id);
            return;
        }

        if (isInvalidMotion(motion)) return;
        if (!isSprintingStable(tp, 10)) return;

        Set<Vec3d> futureMotion = futureMotionByPlayer.get(id);
        if (futureMotion != null) {
            for (Vec3d predicted : futureMotion) {
                double diff = getMaxXZDiff(motion, predicted);
                if (diff <= MAX_DIFF_TO_FLAG) {
                    ctx.notify(player, String.format("StrafeA (diff:%.4f)", diff));
                    break;
                }
            }
        }

        Set<Vec3d> next = new HashSet<>();
        double speed = Math.hypot(motion.x, motion.z);
        float yaw = player.getYaw();
        for (int forward : MOVEMENT) {
            for (int strafe : MOVEMENT) {
                double dir = direction(forward, strafe, yaw);
                next.add(getStrafeMotion(speed, dir, motion.y));
            }
        }

        futureMotionByPlayer.put(id, next);
    }

    private static boolean isNoMove(Vec3d motion) {
        return motion.x == 0.0 && motion.z == 0.0;
    }

    private static boolean isInvalidMotion(Vec3d motion) {
        return Math.abs(motion.x) >= 3.9 || Math.abs(motion.y) >= 3.9 || Math.abs(motion.z) >= 3.9;
    }

    private static double getMaxXZDiff(Vec3d motion1, Vec3d motion2) {
        return Math.max(Math.abs(motion1.x - motion2.x), Math.abs(motion1.z - motion2.z));
    }

    private static Vec3d getStrafeMotion(double speed, double yawRad, double motionY) {
        return new Vec3d(-MathHelper.sin((float) yawRad) * speed, motionY, MathHelper.cos((float) yawRad) * speed);
    }

    private static double direction(int moveForward, int moveStrafe, float rotationYaw) {
        float yaw = rotationYaw;
        if (moveForward < 0) {
            yaw += 180.0f;
        }

        float forward = 1.0f;
        if (moveForward < 0) {
            forward = -0.5f;
        } else if (moveForward > 0) {
            forward = 0.5f;
        }

        if (moveStrafe > 0) {
            yaw -= 70.0f * forward;
        }

        if (moveStrafe < 0) {
            yaw += 70.0f * forward;
        }

        return Math.toRadians(yaw);
    }

    private static boolean isSprintingStable(TrackedPlayer tp, int ticks) {
        Boolean[] hist = tp.sprintHist().toArray(new Boolean[0]);
        if (hist.length < ticks) return false;
        boolean current = tp.currentSprinting();
        for (int i = hist.length - 1; i >= hist.length - ticks; i--) {
            if (hist[i] == null || hist[i] != current) return false;
        }
        return true;
    }
}
