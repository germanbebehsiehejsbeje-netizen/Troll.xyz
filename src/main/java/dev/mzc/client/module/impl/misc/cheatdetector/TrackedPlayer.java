package dev.mzc.client.module.impl.misc.cheatdetector;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class TrackedPlayer {
    private Vec3d lastPos = Vec3d.ZERO;
    private Vec3d posDelta = Vec3d.ZERO;
    private Vec3d lastTickMotion = Vec3d.ZERO;
    private Vec3d currentTickMotion = Vec3d.ZERO;
    private final Deque<Vec3d> posHist = new ArrayDeque<>();
    private float lastYaw;
    private boolean hasYaw;
    private final Deque<Float> yawDeltaHist = new ArrayDeque<>();
    private double prevAbsYawDelta;
    private double lastAbsYawDelta;
    private final Map<Integer, Integer> yawGcdBuckets = new HashMap<>();
    private float lastPitch;
    private boolean hasPitch;
    private double prevAbsPitchDelta;
    private double lastAbsPitchDelta;
    private boolean lastOnGround2;
    private boolean lastOnGround;
    private boolean currentOnGround;
    private boolean lastSprinting;
    private boolean currentSprinting;
    private final Deque<Boolean> sprintHist = new ArrayDeque<>();
    private boolean lastUsingItem;
    private boolean currentUsingItem;

    private int swingThisTick;
    private long swingTick = -1;
    private final int[] swingHistory = new int[20];
    private int swingPtr;
    private int swingSum;
    private int swingPerSecond;

    private short offGroundTicks;
    private short ticksSinceOffGround = 100;

    private Vec3d lastVelocity = Vec3d.ZERO;
    private long lastVelocityTick = -1;
    private double maxObservedHSpeedSinceVel;

    public void update(PlayerEntity player, long tick) {
        Vec3d pos = player.getEntityPos();
        if (lastPos != Vec3d.ZERO) {
            posDelta = pos.subtract(lastPos);
        }
        lastTickMotion = currentTickMotion;
        currentTickMotion = posDelta;
        lastPos = pos;
        posHist.addLast(pos);
        if (posHist.size() > 20) posHist.removeFirst();

        lastOnGround2 = lastOnGround;
        lastOnGround = currentOnGround;
        currentOnGround = player.isOnGround();

        lastSprinting = currentSprinting;
        currentSprinting = player.isSprinting();
        sprintHist.addLast(currentSprinting);
        if (sprintHist.size() > 20) sprintHist.removeFirst();

        lastUsingItem = currentUsingItem;
        currentUsingItem = player.isUsingItem();

        if (player.isOnGround()) {
            if (offGroundTicks > 0) {
                ticksSinceOffGround = 0;
            } else if (ticksSinceOffGround < Short.MAX_VALUE) {
                ticksSinceOffGround++;
            }
            offGroundTicks = 0;
        } else {
            if (offGroundTicks < Short.MAX_VALUE) {
                offGroundTicks++;
            }
            ticksSinceOffGround = 0;
        }

        float yaw = player.getYaw();
        if (hasYaw) {
            float dyaw = MathHelper.wrapDegrees(yaw - lastYaw);
            yawDeltaHist.addLast(dyaw);
            if (yawDeltaHist.size() > 20) yawDeltaHist.removeFirst();
            prevAbsYawDelta = lastAbsYawDelta;
            lastAbsYawDelta = Math.abs(dyaw);
        } else {
            hasYaw = true;
        }
        lastYaw = yaw;

        float pitch = player.getPitch();
        if (hasPitch) {
            float dpitch = pitch - lastPitch;
            prevAbsPitchDelta = lastAbsPitchDelta;
            lastAbsPitchDelta = Math.abs(dpitch);
        } else {
            hasPitch = true;
        }
        lastPitch = pitch;

        advanceSwingTick(tick);

        if (lastVelocityTick >= 0) {
            double h = Math.hypot(posDelta.x, posDelta.z);
            if (h > maxObservedHSpeedSinceVel) {
                maxObservedHSpeedSinceVel = h;
            }
        }
    }

    public void onSwing(long tick) {
        advanceSwingTick(tick);
        swingThisTick++;
        swingHistory[swingPtr]++;
        swingSum++;
        swingPerSecond = swingSum;
    }

    public void onVelocity(Vec3d vel, long tick) {
        lastVelocity = vel;
        lastVelocityTick = tick;
        maxObservedHSpeedSinceVel = 0.0;
    }

    public Vec3d posDelta() {
        return posDelta;
    }

    public Deque<Vec3d> posHist() {
        return posHist;
    }

    public Vec3d lastTickMotion() {
        return lastTickMotion;
    }

    public Vec3d currentTickMotion() {
        return currentTickMotion;
    }

    public boolean hasPosDelta() {
        return posDelta != Vec3d.ZERO;
    }

    public Deque<Float> yawDeltaHist() {
        return yawDeltaHist;
    }

    public double prevAbsYawDelta() {
        return prevAbsYawDelta;
    }

    public double lastAbsYawDelta() {
        return lastAbsYawDelta;
    }

    public Map<Integer, Integer> yawGcdBuckets() {
        return yawGcdBuckets;
    }

    public double prevAbsPitchDelta() {
        return prevAbsPitchDelta;
    }

    public double lastAbsPitchDelta() {
        return lastAbsPitchDelta;
    }

    public int swingThisTick() {
        return swingThisTick;
    }

    public int swingPerSecond() {
        return swingPerSecond;
    }

    public short offGroundTicks() {
        return offGroundTicks;
    }

    public short ticksSinceOffGround() {
        return ticksSinceOffGround;
    }

    public boolean lastOnGround2() {
        return lastOnGround2;
    }

    public boolean lastOnGround() {
        return lastOnGround;
    }

    public boolean currentOnGround() {
        return currentOnGround;
    }

    public boolean lastSprinting() {
        return lastSprinting;
    }

    public boolean currentSprinting() {
        return currentSprinting;
    }

    public Deque<Boolean> sprintHist() {
        return sprintHist;
    }

    public boolean lastUsingItem() {
        return lastUsingItem;
    }

    public boolean currentUsingItem() {
        return currentUsingItem;
    }

    public Vec3d lastVelocity() {
        return lastVelocity;
    }

    public long lastVelocityTick() {
        return lastVelocityTick;
    }

    public void clearVelocityWindow() {
        lastVelocityTick = -1;
    }

    public double maxObservedHSpeedSinceVel() {
        return maxObservedHSpeedSinceVel;
    }

    private void advanceSwingTick(long tick) {
        if (swingTick == tick) {
            swingPerSecond = swingSum;
            return;
        }
        if (swingTick == -1) {
            swingTick = tick;
            swingPtr = 0;
            swingSum = 0;
            swingThisTick = 0;
            swingPerSecond = 0;
            return;
        }

        long dt = tick - swingTick;
        swingTick = tick;
        swingThisTick = 0;
        if (dt <= 0) {
            swingPerSecond = swingSum;
            return;
        }

        int steps = (int) Math.min(20, dt);
        for (int i = 0; i < steps; i++) {
            swingPtr = (swingPtr + 1) % 20;
            swingSum -= swingHistory[swingPtr];
            swingHistory[swingPtr] = 0;
        }
        swingPerSecond = swingSum;
    }
}
