package dev.mzc.client.utils.math;

public record Angle(float yaw, float pitch) {
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
}