package dev.mzc.client.utils.math;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class MathAngle {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static Angle calculateAngle(Vec3d target) {
        Vec3d eyesPos = mc.player.getEyePos();
        double dX = target.x - eyesPos.x;
        double dY = target.y - eyesPos.y;
        double dZ = target.z - eyesPos.z;
        double dH = Math.sqrt(dX * dX + dZ * dZ);

        float yaw = (float) (MathHelper.atan2(dZ, dX) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) (-(MathHelper.atan2(dY, dH) * 180.0D / Math.PI));

        return new Angle(yaw, pitch);
    }
}