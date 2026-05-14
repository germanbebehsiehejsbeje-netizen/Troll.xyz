package dev.mzc.client.module.impl.render;

import com.mojang.blaze3d.vertex.VertexFormat;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.misc.WorldLoadEvent;
import dev.mzc.client.events.render.Render3DEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.utils.color.ColorUtil;
import dev.mzc.client.utils.render.Render3DUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.ColorValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static dev.mzc.client.Sakura.mc;

public class Trail extends Module {

    private final EnumValue<Mode> mode = new EnumValue<>("Mode", Mode.Point);
    private final EnumValue<ColorMode> colorMode = new EnumValue<>("Color Mode", ColorMode.Single);
    private final NumberValue<Integer> maxPoints = new NumberValue<>("Max Points", 100, 10, 1000, 10);
    private final NumberValue<Integer> lifeTime = new NumberValue<>("Life Time", 2000, 500, 10000, 100);
    private final NumberValue<Double> thickness = new NumberValue<>("Thickness", 2.0, 0.1, 5.0, 0.1);
    private final NumberValue<Double> minDistance = new NumberValue<>("Min Distance", 0.05, 0.01, 0.5, 0.01);
    private final NumberValue<Double> height = new NumberValue<>("Height", 1.8, 0.1, 3.0, 0.1, () -> mode.get() == Mode.Line);
    private final ColorValue trailColor = new ColorValue("Color", new Color(255, 255, 255, 200), () -> colorMode.get() == ColorMode.Single || colorMode.get() == ColorMode.Double);
    private final ColorValue secondColor = new ColorValue("Second Color", new Color(255, 0, 0, 200), () -> colorMode.get() == ColorMode.Double);
    private final NumberValue<Integer> alpha = new NumberValue<>("Alpha", 200, 0, 255, 1);
    private final NumberValue<Double> colorSpeed = new NumberValue<>("Color Speed", 5.0, 1.0, 20.0, 0.5, () -> colorMode.get() != ColorMode.Single);
    private final BoolValue fade = new BoolValue("Fade", true);
    private final BoolValue onlyThirdPerson = new BoolValue("Only Third Person", false);
    private final BoolValue antialias = new BoolValue("Antialias", true);

    private final List<Point> points = new ArrayList<>();

    public Trail() {
        super("Trail", Category.Render);
        this.setType(ModuleType.All);
    }

    public enum Mode {
        Point, Line
    }

    public enum ColorMode {
        Single, Double, Client, Rainbow
    }

    @Override
    protected void onEnable() {
        points.clear();
    }

    @Override
    protected void onDisable() {
        points.clear();
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        Vec3d pos = mc.player.getEntityPos();

        if (!points.isEmpty()) {
            Vec3d last = points.get(points.size() - 1).bottom;
            if (pos.distanceTo(last) < minDistance.get()) {
                return;
            }
        }

        points.add(new Point(pos, pos.add(0, height.get(), 0)));

        long now = System.currentTimeMillis();
        points.removeIf(p -> now - p.time > lifeTime.get());
        while (points.size() > maxPoints.get()) {
            points.remove(0);
        }
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (points.size() < 2) return;
        if (onlyThirdPerson.get() && mc.options.getPerspective().isFirstPerson()) return;

        long now = System.currentTimeMillis();

        if (antialias.get()) {
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
            GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
            GL11.glHint(GL11.GL_POLYGON_SMOOTH_HINT, GL11.GL_NICEST);
        }

        Render3DUtil.setup3D();
        Matrix4f matrix = event.getMatrices().peek().getPositionMatrix();
        Vec3d camPos = mc.getEntityRenderDispatcher().camera.getCameraPos();
        float tickDelta = event.getTickDelta();

        Vec3d headBottom = new Vec3d(
                MathHelper.lerp(tickDelta, mc.player.lastX, mc.player.getX()),
                MathHelper.lerp(tickDelta, mc.player.lastY, mc.player.getY()),
                MathHelper.lerp(tickDelta, mc.player.lastZ, mc.player.getZ())
        );
        Vec3d headTop = headBottom.add(0, height.get(), 0);

        if (mode.get() == Mode.Point) {
            Point lastPoint = points.get(points.size() - 1);
            Color headColor = getPointColor(points.size(), points.size() + 1, now, now);
            Render3DUtil.drawLine(event.getMatrices(), lastPoint.bottom, headBottom, headColor, thickness.get().floatValue());

            for (int i = 0; i < points.size() - 1; i++) {
                Point p1 = points.get(i);
                Point p2 = points.get(i + 1);
                Color color = getPointColor(i, points.size(), p1.time, now);
                Render3DUtil.drawLine(event.getMatrices(), p1.bottom, p2.bottom, color, thickness.get().floatValue());
            }
        } else {
            BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

            for (int i = 0; i < points.size(); i++) {
                Vec3d b1;
                Vec3d t1;
                long time1;
                Vec3d b2;
                Vec3d t2;
                long time2;

                if (i == points.size() - 1) {
                    Point p = points.get(i);
                    b1 = p.bottom;
                    t1 = p.top;
                    time1 = p.time;
                    b2 = headBottom;
                    t2 = headTop;
                    time2 = now;
                } else {
                    Point p1 = points.get(i);
                    Point p2 = points.get(i + 1);
                    b1 = p1.bottom;
                    t1 = p1.top;
                    time1 = p1.time;
                    b2 = p2.bottom;
                    t2 = p2.top;
                    time2 = p2.time;
                }

                int c1 = getPointColor(i, points.size() + 1, time1, now).getRGB();
                int c2 = getPointColor(i + 1, points.size() + 1, time2, now).getRGB();

                buffer.vertex(matrix, (float) (b1.x - camPos.x), (float) (b1.y - camPos.y), (float) (b1.z - camPos.z)).color(c1);
                buffer.vertex(matrix, (float) (t1.x - camPos.x), (float) (t1.y - camPos.y), (float) (t1.z - camPos.z)).color(c1);
                buffer.vertex(matrix, (float) (t2.x - camPos.x), (float) (t2.y - camPos.y), (float) (t2.z - camPos.z)).color(c2);
                buffer.vertex(matrix, (float) (b2.x - camPos.x), (float) (b2.y - camPos.y), (float) (b2.z - camPos.z)).color(c2);
            }

            RenderLayers.debugQuads().draw(buffer.end());
        }

        Render3DUtil.cleanup3D();

        if (antialias.get()) {
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
            GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        }
    }

    private Color getPointColor(int index, int total, long time, long now) {
        Color baseColor;
        float speed = colorSpeed.get().floatValue();

        switch (colorMode.get()) {
            case Single -> baseColor = trailColor.get();
            case Double -> {
                float ratio = (float) Math.sin((index * 0.1) + (System.currentTimeMillis() / 1000.0 * speed));
                ratio = (ratio + 1f) / 2f;
                baseColor = ColorUtil.interpolateColor(trailColor.get(), secondColor.get(), ratio);
            }
            case Client -> baseColor = ClickGui.color(index * 10);
            case Rainbow -> {
                float hue = (float) ((System.currentTimeMillis() / 10000.0 * speed + index * 0.01) % 1.0);
                baseColor = Color.getHSBColor(hue, 0.7f, 1.0f);
            }
            default -> baseColor = trailColor.get();
        }

        int finalAlpha = alpha.get();
        if (fade.get()) {
            float progress = 1.0f - (float) (now - time) / lifeTime.get();
            progress = MathHelper.clamp(progress, 0, 1);
            finalAlpha = (int) (finalAlpha * progress);
        }

        return new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), finalAlpha);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        points.clear();
    }

    private static class Point {
        private final Vec3d bottom;
        private final Vec3d top;
        private final long time;

        public Point(Vec3d bottom, Vec3d top) {
            this.bottom = bottom;
            this.top = top;
            this.time = System.currentTimeMillis();
        }
    }
}
