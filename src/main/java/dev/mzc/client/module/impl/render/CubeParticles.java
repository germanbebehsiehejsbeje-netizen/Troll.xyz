package dev.mzc.client.module.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.mzc.client.events.player.PlayerTickEvent;
import dev.mzc.client.events.render.Render3DEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.animations.Easing;
import dev.mzc.client.utils.color.ColorUtil;
import dev.mzc.client.utils.math.MathUtil;
import dev.mzc.client.utils.render.Render3DUtil;
import dev.mzc.client.utils.time.TimerUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CubeParticles extends Module {
    private final NumberValue<Integer> count = new NumberValue<>("Count", 20, 1, 100, 1);
    private final NumberValue<Double> size = new NumberValue<>("Size", 0.1, 0.01, 1.0, 0.01);
    private final NumberValue<Integer> lifeTime = new NumberValue<>("LifeTime", 60000, 5000, 300000, 5000);
    private final NumberValue<Integer> spawnDuration = new NumberValue<>("SpawnDuration", 500, 100, 2000, 100);
    private final NumberValue<Integer> dyingDuration = new NumberValue<>("DyingDuration", 500, 100, 2000, 100);
    private final NumberValue<Double> distance = new NumberValue<>("Distance", 20.0, 5.0, 50.0, 1.0);
    private final NumberValue<Double> riseSpeed = new NumberValue<>("Rise Speed", 0.03, 0.01, 0.1, 0.01);
    private final NumberValue<Double> rotateSpeed = new NumberValue<>("Rotate Speed", 2.0, 0.5, 5.0, 0.5);
    private final NumberValue<Double> maxHeight = new NumberValue<>("Max Height", 6.0, 1.0, 20.0, 0.5);
    private final BoolValue glow = new BoolValue("Glow", true);
    private final NumberValue<Double> glowSize = new NumberValue<>("Glow Size", 2.0, 1.0, 4.0, 0.5);

    private final List<CubeParticle> particles = new ArrayList<>();

    public CubeParticles() {
        super("CubeParticles", Category.Render);
    }

    @Override
    protected void onEnable() {
        particles.clear();
    }

    @EventHandler
    public void onTick(PlayerTickEvent event) {
        if (mc.player == null) return;

        particles.removeIf(CubeParticle::update);

        int diff = count.get() - particles.size();
        if (diff > 0) {
            double d = distance.get();
            int toSpawn = Math.min(diff, 5);

            for (int i = 0; i < toSpawn; i++) {
                double spawnX = mc.player.getX() + MathUtil.getRandom(-d, d);
                double spawnZ = mc.player.getZ() + MathUtil.getRandom(-d, d);
                double spawnY = findGroundY(spawnX, spawnZ);

                particles.add(new CubeParticle(
                        (float) spawnX,
                        (float) spawnY,
                        (float) spawnZ,
                        MathUtil.getRandom(-0.01f, 0.01f),
                        riseSpeed.get().floatValue(),
                        MathUtil.getRandom(-0.01f, 0.01f),
                        size.get().floatValue(),
                        lifeTime.get(),
                        spawnDuration.get(),
                        dyingDuration.get(),
                        rotateSpeed.get().floatValue(),
                        maxHeight.get().floatValue()
                ));
            }
        }
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        MatrixStack matrixStack = event.getMatrices();
        float tickDelta = event.getTickDelta();

        for (CubeParticle particle : particles) {
            particle.updateAlpha();
            particle.render(matrixStack, glow.get(), glowSize.get().floatValue(), tickDelta);
        }
    }

    private double findGroundY(double x, double z) {
        if (mc.world == null || mc.player == null) return 0;

        int playerY = (int) mc.player.getY();

        for (int y = playerY + 2; y > playerY - 15; y--) {
            BlockPos pos = BlockPos.ofFloored(x, y, z);
            BlockPos above = pos.up();

            boolean blockSolid = !mc.world.getBlockState(pos).isAir();
            boolean aboveAir = mc.world.getBlockState(above).isAir();

            if (blockSolid && aboveAir) {
                return y + 1.0;
            }
        }

        return mc.player.getY();
    }

    private class CubeParticle {
        private float prevX, prevY, prevZ;
        private float x, y, z;
        private final float startY;
        private final float motionX, motionY, motionZ;
        private final int maxLife;
        private final float size;
        private final float limitHeight;

        private float rotX, rotY, rotZ;
        private float prevRotX, prevRotY, prevRotZ;
        private final float rotSpeedX, rotSpeedY, rotSpeedZ;

        private final float spawnDuration, dyingDuration;
        private final TimerUtil timerUtil = new TimerUtil();
        private float currentAlpha = 0;
        private final long seed = (long) (Math.random() * 10000);

        public CubeParticle(float x, float y, float z,
                            float motionX, float motionY, float motionZ,
                            float size, int lifetime,
                            float spawnDuration, float dyingDuration, float rotateSpeed, float limitHeight) {
            this.prevX = x;
            this.prevY = y;
            this.prevZ = z;
            this.x = x;
            this.y = y;
            this.z = z;
            this.startY = y;
            this.motionX = motionX;
            this.motionY = motionY;
            this.motionZ = motionZ;
            this.size = size;
            this.maxLife = lifetime;
            this.spawnDuration = spawnDuration;
            this.dyingDuration = dyingDuration;
            this.limitHeight = limitHeight;

            this.rotX = MathUtil.getRandom(-180f, 180f);
            this.rotY = MathUtil.getRandom(-180f, 180f);
            this.rotZ = MathUtil.getRandom(-180f, 180f);
            this.prevRotX = rotX;
            this.prevRotY = rotY;
            this.prevRotZ = rotZ;

            this.rotSpeedX = MathUtil.getRandom(-rotateSpeed, rotateSpeed);
            this.rotSpeedY = MathUtil.getRandom(-rotateSpeed, rotateSpeed);
            this.rotSpeedZ = MathUtil.getRandom(-rotateSpeed, rotateSpeed);
            timerUtil.reset();
        }

        public boolean update() {
            prevX = x;
            prevY = y;
            prevZ = z;
            prevRotX = rotX;
            prevRotY = rotY;
            prevRotZ = rotZ;

            if (y < startY + limitHeight) {
                y += motionY;
            } else {
                // Floating/Gliding effect using sine wave
                long time = System.currentTimeMillis() + seed;
                y = startY + limitHeight + (float) Math.sin(time / 1000.0) * 0.2f;
                x += (float) Math.sin(time / 1500.0) * 0.005f;
                z += (float) Math.cos(time / 1500.0) * 0.005f;
            }

            x += motionX;
            z += motionZ;

            rotX += rotSpeedX;
            rotY += rotSpeedY;
            rotZ += rotSpeedZ;

            if (mc.player == null) return true;
            
            // Disappear after a minute (lifeTime) or if player moves too far (distance check)
            boolean timeOut = timerUtil.passedMS(spawnDuration + dyingDuration + maxLife);
            boolean tooFar = mc.player.squaredDistanceTo(x, y, z) >= (distance.get() * 2) * (distance.get() * 2);
            
            return timeOut || tooFar || (currentAlpha <= 0.0 && timerUtil.passedMS(spawnDuration + dyingDuration));
        }

        public void updateAlpha() {
            long time = timerUtil.getTime();
            if (time < spawnDuration) {
                currentAlpha = (float) Easing.QUART_OUT.ease(time / (double) spawnDuration);
            } else if (time < spawnDuration + maxLife) {
                currentAlpha = 1.0f;
            } else if (time < spawnDuration + maxLife + dyingDuration) {
                currentAlpha = 1.0f - (float) Easing.QUART_IN.ease((time - spawnDuration - maxLife) / (double) dyingDuration);
            } else {
                currentAlpha = 0;
            }
        }

        public void render(MatrixStack matrixStack, boolean glow, float glowSize, float tickDelta) {
            Camera camera = mc.gameRenderer.getCamera();
            Vec3d cam = camera.getCameraPos();
            float alpha = currentAlpha;

            double interpX = MathUtil.interpolate(x, prevX, tickDelta) - cam.x;
            double interpY = MathUtil.interpolate(y, prevY, tickDelta) - cam.y;
            double interpZ = MathUtil.interpolate(z, prevZ, tickDelta) - cam.z;

            float interpRotX = MathUtil.interpolateFloat(rotX, prevRotX, tickDelta);
            float interpRotY = MathUtil.interpolateFloat(rotY, prevRotY, tickDelta);
            float interpRotZ = MathUtil.interpolateFloat(rotZ, prevRotZ, tickDelta);

            float halfSize = size * alpha;

            Color color = ColorUtil.applyOpacity(new Color(100, 200, 255), alpha);

            matrixStack.push();
            matrixStack.translate(interpX, interpY, interpZ);
            matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(interpRotX));
            matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(interpRotY));
            matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(interpRotZ));

            if (glow) {
                renderGlow(matrixStack, halfSize * glowSize, color, alpha, tickDelta);
            }

            renderCube(matrixStack, halfSize, color);

            matrixStack.pop();
        }

        private void renderGlow(MatrixStack matrixStack, float size, Color color, float alpha, float tickDelta) {
            Camera camera = mc.gameRenderer.getCamera();

            matrixStack.push();
            matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-MathUtil.interpolateFloat(rotZ, prevRotZ, tickDelta)));
            matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-MathUtil.interpolateFloat(rotY, prevRotY, tickDelta)));
            matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-MathUtil.interpolateFloat(rotX, prevRotX, tickDelta)));
            matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
            matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

            Matrix4f glowMatrix = matrixStack.peek().getPositionMatrix();

            Render3DUtil.setup3D();

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buf = tessellator.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
            int r = color.getRed(), g = color.getGreen(), b = color.getBlue();
            int centerAlpha = (int) (80 * alpha);
            int edgeAlpha = 0;

            buf.vertex(glowMatrix, 0, 0, 0).color(r, g, b, centerAlpha);

            int segments = 16;
            for (int i = 0; i <= segments; i++) {
                float angle = (float) (i * 2 * Math.PI / segments);
                float px = (float) Math.cos(angle) * size;
                float py = (float) Math.sin(angle) * size;
                buf.vertex(glowMatrix, px, py, 0).color(r, g, b, edgeAlpha);
            }

            RenderLayers.debugTriangleFan().draw(buf.end());
            Render3DUtil.cleanup3D();
            matrixStack.pop();
        }

        private void renderCube(MatrixStack matrixStack, float size, Color color) {
            Matrix4f matrix = matrixStack.peek().getPositionMatrix();
            MatrixStack.Entry entry = matrixStack.peek();
            float s = size;

            Render3DUtil.setup3D();

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buf = tessellator.begin(VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH);
            int r = color.getRed(), g = color.getGreen(), b = color.getBlue(), a = color.getAlpha();

            // Bottom
            vertexLine(buf, matrix, entry, -s, -s, -s, s, -s, -s, r, g, b, a);
            vertexLine(buf, matrix, entry, s, -s, -s, s, -s, s, r, g, b, a);
            vertexLine(buf, matrix, entry, s, -s, s, -s, -s, s, r, g, b, a);
            vertexLine(buf, matrix, entry, -s, -s, s, -s, -s, -s, r, g, b, a);

            // Top
            vertexLine(buf, matrix, entry, -s, s, -s, s, s, -s, r, g, b, a);
            vertexLine(buf, matrix, entry, s, s, -s, s, s, s, r, g, b, a);
            vertexLine(buf, matrix, entry, s, s, s, -s, s, s, r, g, b, a);
            vertexLine(buf, matrix, entry, -s, s, s, -s, s, -s, r, g, b, a);

            // Sides
            vertexLine(buf, matrix, entry, -s, -s, -s, -s, s, -s, r, g, b, a);
            vertexLine(buf, matrix, entry, s, -s, -s, s, s, -s, r, g, b, a);
            vertexLine(buf, matrix, entry, s, -s, s, s, s, s, r, g, b, a);
            vertexLine(buf, matrix, entry, -s, -s, s, -s, s, s, r, g, b, a);

            RenderLayers.lines().draw(buf.end());
            Render3DUtil.cleanup3D();
        }

        private void vertexLine(BufferBuilder buffer, Matrix4f matrix, MatrixStack.Entry entry, float x1, float y1, float z1, float x2, float y2, float z2, int r, int g, int b, int a) {
            float xNormal = x2 - x1;
            float yNormal = y2 - y1;
            float zNormal = z2 - z1;
            float normalSqrt = MathHelper.sqrt(xNormal * xNormal + yNormal * yNormal + zNormal * zNormal);
            if (normalSqrt > 0) {
                xNormal /= normalSqrt;
                yNormal /= normalSqrt;
                zNormal /= normalSqrt;
            } else {
                xNormal = 0.0f;
                yNormal = 0.0f;
                zNormal = 0.0f;
            }

            buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).normal(entry, xNormal, yNormal, zNormal).lineWidth(2.0f);
            buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a).normal(entry, xNormal, yNormal, zNormal).lineWidth(2.0f);
        }
    }
}
