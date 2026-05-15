package dev.mzc.client.module.impl.render;

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
import dev.mzc.client.values.impl.ColorValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class GhostLines extends Module {
    private final NumberValue<Integer> count = new NumberValue<>("Count", 15, 1, 50, 1);
    private final NumberValue<Double> length = new NumberValue<>("Length", 3.0, 0.5, 10.0, 0.5);
    private final NumberValue<Double> width = new NumberValue<>("Width", 0.05, 0.01, 0.5, 0.01);
    private final NumberValue<Integer> lifeTime = new NumberValue<>("LifeTime", 40000, 5000, 120000, 5000);
    private final NumberValue<Integer> spawnDuration = new NumberValue<>("SpawnDuration", 1000, 100, 3000, 100);
    private final NumberValue<Integer> dyingDuration = new NumberValue<>("DyingDuration", 1000, 100, 3000, 100);
    private final NumberValue<Double> distance = new NumberValue<>("Distance", 25.0, 5.0, 50.0, 1.0);
    private final BoolValue glow = new BoolValue("Glow", true);
    private final ColorValue lineColor = new ColorValue("Line Color", new Color(100, 200, 255, 180));

    private static final Identifier GLOW_TEXTURE = Identifier.of("sakura", "particle/ghost-glow.png");

    private final List<GhostLine> lines = new ArrayList<>();

    public GhostLines() {
        super("GhostLines", Category.Render);
    }

    @Override
    protected void onEnable() {
        lines.clear();
    }

    @EventHandler
    public void onTick(PlayerTickEvent event) {
        if (mc.player == null) return;

        lines.removeIf(GhostLine::update);

        int diff = count.get() - lines.size();
        if (diff > 0) {
            double d = distance.get();
            int toSpawn = Math.min(diff, 3);

            for (int i = 0; i < toSpawn; i++) {
                // Random start position
                double startX = mc.player.getX() + MathUtil.getRandom(-d, d);
                double startY = mc.player.getY() + MathUtil.getRandom(-2, 5);
                double startZ = mc.player.getZ() + MathUtil.getRandom(-d, d);

                // Random direction
                double endX = startX + MathUtil.getRandom(-length.get(), length.get());
                double endY = startY + MathUtil.getRandom(-length.get() / 2, length.get() / 2);
                double endZ = startZ + MathUtil.getRandom(-length.get(), length.get());

                lines.add(new GhostLine(
                        new Vec3d(startX, startY, startZ),
                        new Vec3d(endX, endY, endZ),
                        lifeTime.get(),
                        spawnDuration.get(),
                        dyingDuration.get()
                ));
            }
        }
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        MatrixStack matrixStack = event.getMatrices();
        float tickDelta = event.getTickDelta();

        for (GhostLine line : lines) {
            line.updateAlpha();
            line.render(matrixStack, glow.get(), tickDelta);
        }
    }

    private class GhostLine {
        private Vec3d startPos, endPos;
        private final int maxLife;
        private final float spawnDuration, dyingDuration;
        private final TimerUtil timerUtil = new TimerUtil();
        private float currentAlpha = 0;
        private final long seed = (long) (Math.random() * 10000);

        public GhostLine(Vec3d startPos, Vec3d endPos, int lifetime, float spawnDuration, float dyingDuration) {
            this.startPos = startPos;
            this.endPos = endPos;
            this.maxLife = lifetime;
            this.spawnDuration = spawnDuration;
            this.dyingDuration = dyingDuration;
            timerUtil.reset();
        }

        public boolean update() {
            if (mc.player == null) return true;
            
            // Remove if timeout or too far
            boolean timeOut = timerUtil.passedMS(spawnDuration + dyingDuration + maxLife);
            boolean tooFar = mc.player.squaredDistanceTo(
                (startPos.x + endPos.x) / 2,
                (startPos.y + endPos.y) / 2,
                (startPos.z + endPos.z) / 2
            ) >= (distance.get() * 2) * (distance.get() * 2);
            
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

        public void render(MatrixStack matrixStack, boolean glow, float tickDelta) {
            Camera camera = mc.gameRenderer.getCamera();
            Vec3d cam = camera.getCameraPos();
            float alpha = currentAlpha;

            // Interpolate positions
            Vec3d renderStart = startPos;
            Vec3d renderEnd = endPos;

            Color color = ColorUtil.applyOpacity(lineColor.get(), alpha);

            // Draw the simple line
            renderSimpleLine(matrixStack, renderStart, renderEnd, cam, color);
            
            // Draw glow effect at start and end points
            if (glow) {
                renderGlowPoint(matrixStack, renderStart, cam, color, alpha);
                renderGlowPoint(matrixStack, renderEnd, cam, color, alpha);
            }
        }

        private void renderSimpleLine(MatrixStack matrixStack, Vec3d start, Vec3d end, Vec3d cam, Color color) {
            Matrix4f matrix = matrixStack.peek().getPositionMatrix();
            
            Render3DUtil.setup3D();

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buf = tessellator.begin(com.mojang.blaze3d.vertex.VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            int r = color.getRed(), g = color.getGreen(), b = color.getBlue(), a = color.getAlpha();

            buf.vertex(matrix, (float)(start.x - cam.x), (float)(start.y - cam.y), (float)(start.z - cam.z))
               .color(r, g, b, a);
            buf.vertex(matrix, (float)(end.x - cam.x), (float)(end.y - cam.y), (float)(end.z - cam.z))
               .color(r, g, b, a);

            RenderLayers.lines().draw(buf.end());
            Render3DUtil.cleanup3D();
        }

        private void renderGlowPoint(MatrixStack matrixStack, Vec3d pos, Vec3d cam, Color color, float alpha) {
            Camera camera = mc.gameRenderer.getCamera();
            
            float dx = (float)(pos.x - cam.x);
            float dy = (float)(pos.y - cam.y);
            float dz = (float)(pos.z - cam.z);
            
            float halfSize = (float)(width.get() * 5.0f);
            
            org.joml.Vector3f right = new org.joml.Vector3f();
            org.joml.Vector3f up = new org.joml.Vector3f();
            camera.getRotation().transform(new org.joml.Vector3f(1, 0, 0), right);
            camera.getRotation().transform(new org.joml.Vector3f(0, 1, 0), up);
            
            float r = color.getRed() / 255f;
            float g = color.getGreen() / 255f;
            float b = color.getBlue() / 255f;
            float a = color.getAlpha() / 255f * 0.6f;
            
            float[] bl = { dx+(-right.x-up.x)*halfSize, dy+(-right.y-up.y)*halfSize, dz+(-right.z-up.z)*halfSize };
            float[] br = { dx+( right.x-up.x)*halfSize, dy+( right.y-up.y)*halfSize, dz+( right.z-up.z)*halfSize };
            float[] tr = { dx+( right.x+up.x)*halfSize, dy+( right.y+up.y)*halfSize, dz+( right.z+up.z)*halfSize };
            float[] tl = { dx+(-right.x+up.x)*halfSize, dy+(-right.y+up.y)*halfSize, dz+(-right.z+up.z)*halfSize };
            
            Matrix4f matrix = matrixStack.peek().getPositionMatrix();
            BufferBuilder buffer = Tessellator.getInstance().begin(
                com.mojang.blaze3d.vertex.VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_TEXTURE_COLOR
            );
            
            buffer.vertex(matrix, bl[0], bl[1], bl[2]).texture(0f, 1f).color(r, g, b, a);
            buffer.vertex(matrix, br[0], br[1], br[2]).texture(1f, 1f).color(r, g, b, a);
            buffer.vertex(matrix, tr[0], tr[1], tr[2]).texture(1f, 0f).color(r, g, b, a);
            buffer.vertex(matrix, tl[0], tl[1], tl[2]).texture(0f, 0f).color(r, g, b, a);
            
            RenderLayers.beaconBeam(GLOW_TEXTURE, true).draw(buffer.end());
        }
    }
}
