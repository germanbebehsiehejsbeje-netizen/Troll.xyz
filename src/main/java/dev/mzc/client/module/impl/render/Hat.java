package dev.mzc.client.module.impl.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.mzc.client.events.render.Render3DEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.utils.color.ColorUtil;
import dev.mzc.client.utils.math.MathUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.ColorValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.*;

public class Hat extends Module {

    public enum Mode {
        Astolfo(),
        Sexy(),
        Fade(),
        Dynamic(),
        Client();
        Mode() {
        }
    }

    public enum Style {
        Cone(),
        Halo(),
        Ring(),
        TopHat(),
        Crown();
        Style() {
        }
    }

    private final EnumValue<Mode> mode = new EnumValue<>("Mode", Mode.Sexy);
    private final EnumValue<Style> style = new EnumValue<>("Style", Style.Cone);
    private final NumberValue<Integer> points = new NumberValue<>("Points", 30, 3, 180, 1);
    private final NumberValue<Double> size = new NumberValue<>("Size", 0.5, 0.1, 3.0, 0.1);
    private final NumberValue<Double> offsetValue = new NumberValue<>("Offset", 2000.0, 0.0, 5000.0, 100.0);
    private final NumberValue<Double> height = new NumberValue<>("Height", 0.25, 0.05, 1.0, 0.05, () -> style.is(Style.Cone) || style.is(Style.TopHat) || style.is(Style.Crown));
    private final NumberValue<Double> thickness = new NumberValue<>("Thickness", 0.06, 0.01, 0.5, 0.01, () -> style.is(Style.Halo) || style.is(Style.Ring) || style.is(Style.TopHat) || style.is(Style.Crown));
    private final NumberValue<Double> yOffset = new NumberValue<>("YOffset", 0.12, 0.0, 0.5, 0.01, () -> style.is(Style.Ring) || style.is(Style.TopHat));
    private final NumberValue<Double> elytraYOffset = new NumberValue<>("Elytra YOffset", 0.10, 0.0, 0.5, 0.01);
    private final ColorValue colorValue = new ColorValue("Color", new Color(255, 255, 255), () -> mode.is(Mode.Fade) || mode.is(Mode.Dynamic));
    private final ColorValue secondColorValue = new ColorValue("Second Color", new Color(0, 0, 0), () -> mode.is(Mode.Fade));
    private final BoolValue onlyThirdPerson = new BoolValue("Only Third Person", true);

    private final double[][] positions = new double[181][2];
    private int lastPoints;
    private double lastSize;

    public Hat() {
        super("Hat", Category.Render);
        this.setType(ModuleType.All);
    }

    private void computeChineseHatPoints(int points, double radius) {
        for (int i = 0; i <= points; i++) {
            double circleX = radius * StrictMath.cos(i * Math.PI * 2 / points);
            double circleZ = radius * StrictMath.sin(i * Math.PI * 2 / points);
            this.positions[i][0] = circleX;
            this.positions[i][1] = circleZ;
        }
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;

        if (this.lastSize != this.size.get() || this.lastPoints != this.points.get()) {
            this.lastSize = this.size.get();
            this.lastPoints = this.points.get();
            this.computeChineseHatPoints(this.lastPoints, this.lastSize);
        }

        drawHat(event.getMatrices(), event.getTickDelta(), mc.player);
    }

    public void drawHat(MatrixStack matrices, float tickDelta, PlayerEntity player) {
        if (player == mc.player && mc.options.getPerspective().isFirstPerson() && onlyThirdPerson.get()) {
            return;
        }

        int pointCount = this.points.get();
        double radius = this.size.get();

        Color[] colors = new Color[181];
        Color[] colorMode = getColorMode();

        for (int i = 0; i < colors.length; ++i) {
            colors[i] = this.fadeBetween(colorMode, this.offsetValue.get(), (double) i * ((double) this.offsetValue.get() / this.points.get()));
        }

        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        double x = MathHelper.lerp(tickDelta, player.lastRenderX, player.getX()) - camera.x;
        double y = MathHelper.lerp(tickDelta, player.lastRenderY, player.getY()) - camera.y;
        double z = MathHelper.lerp(tickDelta, player.lastRenderZ, player.getZ()) - camera.z;

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(770, 771, 1, 0);
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        GlStateManager._disableCull();

        matrices.push();

        matrices.translate(x, y + 1.9, z);

        if (player.isSneaking()) {
            matrices.translate(0, -0.2, 0);
        }

        float yaw = MathUtil.interpolateFloat(player.lastHeadYaw, player.headYaw, tickDelta);
        matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(yaw));

        float pitch = MathUtil.interpolateFloat(player.lastPitch, player.getPitch(), tickDelta);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch / 3.0f));
        matrices.translate(0, 0, pitch / 270.0);

        double dy = 0.0;
        if (style.get() == Style.Ring || style.get() == Style.TopHat) dy += yOffset.get();
        if (player.isGliding()) dy += elytraYOffset.get();
        if (dy != 0.0) matrices.translate(0, dy, 0);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();

        if (style.get() == Style.Cone) {
            float lineWidth = 2.0f;
            BufferBuilder outlineBuffer = tessellator.begin(VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH);
            for (int i = 0; i < pointCount; i++) {
                int next = (i + 1) % pointCount;
                double[] p1 = this.positions[i];
                double[] p2 = this.positions[next];

                float dx = (float) (p2[0] - p1[0]);
                float dz = (float) (p2[1] - p1[1]);
                float len = MathHelper.sqrt(dx * dx + dz * dz);
                float nx = len == 0.0f ? 1.0f : dx / len;
                float nz = len == 0.0f ? 0.0f : dz / len;

                Color c1 = colors[i];
                Color c2 = colors[next];
                outlineBuffer.vertex(matrix, (float) p1[0], 0.0f, (float) p1[1]).color(c1.getRed(), c1.getGreen(), c1.getBlue(), 255).normal(nx, 0.0f, nz).lineWidth(lineWidth);
                outlineBuffer.vertex(matrix, (float) p2[0], 0.0f, (float) p2[1]).color(c2.getRed(), c2.getGreen(), c2.getBlue(), 255).normal(nx, 0.0f, nz).lineWidth(lineWidth);
            }
            RenderLayers.lines().draw(outlineBuffer.end());

            BufferBuilder coneBuffer = tessellator.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
            coneBuffer.vertex(matrix, 0, height.get().floatValue(), 0).color(255, 255, 255, 128);
            for (int i = 0; i <= pointCount; i++) {
                double[] pos = this.positions[i % pointCount];
                Color clr = colors[i % colors.length];
                coneBuffer.vertex(matrix, (float) pos[0], 0, (float) pos[1]).color(clr.getRed(), clr.getGreen(), clr.getBlue(), 128);
            }
            RenderLayers.debugTriangleFan().draw(coneBuffer.end());
        } else if (style.get() == Style.Halo || style.get() == Style.Ring) {
            double inner = Math.max(0.0, radius - thickness.get());
            BufferBuilder ring = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (int i = 0; i < pointCount; i++) {
                int next = (i + 1) % pointCount;
                double ox1 = this.positions[i][0];
                double oz1 = this.positions[i][1];
                double ox2 = this.positions[next][0];
                double oz2 = this.positions[next][1];
                double ix1 = radius == 0.0 ? 0.0 : inner / radius * ox1;
                double iz1 = radius == 0.0 ? 0.0 : inner / radius * oz1;
                double ix2 = radius == 0.0 ? 0.0 : inner / radius * ox2;
                double iz2 = radius == 0.0 ? 0.0 : inner / radius * oz2;
                Color c1 = colors[i];
                Color c2 = colors[next];
                ring.vertex(matrix, (float) ox1, 0, (float) oz1).color(c1.getRed(), c1.getGreen(), c1.getBlue(), 160);
                ring.vertex(matrix, (float) ix1, 0, (float) iz1).color(c1.getRed(), c1.getGreen(), c1.getBlue(), 160);
                ring.vertex(matrix, (float) ix2, 0, (float) iz2).color(c2.getRed(), c2.getGreen(), c2.getBlue(), 160);
                ring.vertex(matrix, (float) ox2, 0, (float) oz2).color(c2.getRed(), c2.getGreen(), c2.getBlue(), 160);
            }
            RenderLayers.debugQuads().draw(ring.end());
        } else if (style.get() == Style.TopHat) {
            double brimInner = Math.max(0.0, radius - thickness.get());
            BufferBuilder brim = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (int i = 0; i < pointCount; i++) {
                int next = (i + 1) % pointCount;
                double ox1 = this.positions[i][0];
                double oz1 = this.positions[i][1];
                double ox2 = this.positions[next][0];
                double oz2 = this.positions[next][1];
                double ix1 = radius == 0.0 ? 0.0 : brimInner / radius * ox1;
                double iz1 = radius == 0.0 ? 0.0 : brimInner / radius * oz1;
                double ix2 = radius == 0.0 ? 0.0 : brimInner / radius * ox2;
                double iz2 = radius == 0.0 ? 0.0 : brimInner / radius * oz2;
                Color c1 = colors[i];
                Color c2 = colors[next];
                brim.vertex(matrix, (float) ox1, 0, (float) oz1).color(c1.getRed(), c1.getGreen(), c1.getBlue(), 180);
                brim.vertex(matrix, (float) ix1, 0, (float) iz1).color(c1.getRed(), c1.getGreen(), c1.getBlue(), 180);
                brim.vertex(matrix, (float) ix2, 0, (float) iz2).color(c2.getRed(), c2.getGreen(), c2.getBlue(), 180);
                brim.vertex(matrix, (float) ox2, 0, (float) oz2).color(c2.getRed(), c2.getGreen(), c2.getBlue(), 180);
            }
            RenderLayers.debugQuads().draw(brim.end());

            double bodyRadius = brimInner * 0.6;
            BufferBuilder sides = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            float topY = (float) height.get().doubleValue();
            for (int i = 0; i < pointCount; i++) {
                int next = (i + 1) % pointCount;
                double bx1 = radius == 0.0 ? 0.0 : bodyRadius / radius * this.positions[i][0];
                double bz1 = radius == 0.0 ? 0.0 : bodyRadius / radius * this.positions[i][1];
                double bx2 = radius == 0.0 ? 0.0 : bodyRadius / radius * this.positions[next][0];
                double bz2 = radius == 0.0 ? 0.0 : bodyRadius / radius * this.positions[next][1];
                Color c1 = colors[i];
                Color c2 = colors[next];
                sides.vertex(matrix, (float) bx1, 0, (float) bz1).color(c1.getRed(), c1.getGreen(), c1.getBlue(), 160);
                sides.vertex(matrix, (float) bx1, topY, (float) bz1).color(c1.getRed(), c1.getGreen(), c1.getBlue(), 160);
                sides.vertex(matrix, (float) bx2, topY, (float) bz2).color(c2.getRed(), c2.getGreen(), c2.getBlue(), 160);
                sides.vertex(matrix, (float) bx2, 0, (float) bz2).color(c2.getRed(), c2.getGreen(), c2.getBlue(), 160);
            }
            RenderLayers.debugQuads().draw(sides.end());

            BufferBuilder top = tessellator.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
            top.vertex(matrix, 0, (float) height.get().doubleValue(), 0).color(255, 255, 255, 160);
            for (int i = 0; i <= pointCount; i++) {
                int idx = i % pointCount;
                double tx = radius == 0.0 ? 0.0 : bodyRadius / radius * this.positions[idx][0];
                double tz = radius == 0.0 ? 0.0 : bodyRadius / radius * this.positions[idx][1];
                Color clr = colors[idx];
                top.vertex(matrix, (float) tx, (float) height.get().doubleValue(), (float) tz).color(clr.getRed(), clr.getGreen(), clr.getBlue(), 160);
            }
            RenderLayers.debugTriangleFan().draw(top.end());
        } else if (style.get() == Style.Crown) {
            double baseInner = Math.max(0.0, radius - thickness.get());
            BufferBuilder base = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (int i = 0; i < pointCount; i++) {
                int next = (i + 1) % pointCount;
                double ox1 = this.positions[i][0];
                double oz1 = this.positions[i][1];
                double ox2 = this.positions[next][0];
                double oz2 = this.positions[next][1];
                double ix1 = radius == 0.0 ? 0.0 : baseInner / radius * ox1;
                double iz1 = radius == 0.0 ? 0.0 : baseInner / radius * oz1;
                double ix2 = radius == 0.0 ? 0.0 : baseInner / radius * ox2;
                double iz2 = radius == 0.0 ? 0.0 : baseInner / radius * oz2;
                Color c1 = colors[i];
                Color c2 = colors[next];
                base.vertex(matrix, (float) ox1, 0, (float) oz1).color(c1.getRed(), c1.getGreen(), c1.getBlue(), 200);
                base.vertex(matrix, (float) ix1, 0, (float) iz1).color(c1.getRed(), c1.getGreen(), c1.getBlue(), 200);
                base.vertex(matrix, (float) ix2, 0, (float) iz2).color(c2.getRed(), c2.getGreen(), c2.getBlue(), 200);
                base.vertex(matrix, (float) ox2, 0, (float) oz2).color(c2.getRed(), c2.getGreen(), c2.getBlue(), 200);
            }
            RenderLayers.debugQuads().draw(base.end());

            int step = Math.max(1, pointCount / 12);
            BufferBuilder spikes = tessellator.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
            for (int i = 0; i < pointCount; i += step) {
                int j = (i + step) % pointCount;
                double x1 = this.positions[i][0];
                double z1 = this.positions[i][1];
                double x2 = this.positions[j][0];
                double z2 = this.positions[j][1];
                Color c1 = colors[i];
                Color c2 = colors[j];
                spikes.vertex(matrix, (float) x1, 0, (float) z1).color(c1.getRed(), c1.getGreen(), c1.getBlue(), 180);
                spikes.vertex(matrix, (float) x2, 0, (float) z2).color(c2.getRed(), c2.getGreen(), c2.getBlue(), 180);
                spikes.vertex(matrix, 0, (float) height.get().doubleValue(), 0).color(255, 255, 255, 200);
            }
            RenderLayers.debugTriangleFan().draw(spikes.end());
        }

        matrices.pop();

        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(true);
        GlStateManager._enableCull();
        GlStateManager._disableBlend();
    }

    private Color[] getColorMode() {
        return switch (this.mode.get()) {
            case Astolfo -> new Color[]{
                    new Color(252, 106, 140), new Color(252, 106, 213),
                    new Color(218, 106, 252), new Color(145, 106, 252),
                    new Color(106, 140, 252), new Color(106, 213, 252),
                    new Color(106, 213, 252), new Color(106, 140, 252),
                    new Color(145, 106, 252), new Color(218, 106, 252),
                    new Color(252, 106, 213), new Color(252, 106, 140)
            };
            case Sexy -> new Color[]{
                    new Color(255, 150, 255), new Color(255, 132, 199),
                    new Color(211, 101, 187), new Color(160, 80, 158),
                    new Color(120, 63, 160), new Color(123, 65, 168),
                    new Color(104, 52, 152), new Color(142, 74, 175),
                    new Color(160, 83, 179), new Color(255, 110, 189),
                    new Color(255, 150, 255)
            };
            case Fade -> new Color[]{
                    this.colorValue.get(),
                    this.secondColorValue.get(),
                    this.colorValue.get()
            };
            case Dynamic -> new Color[]{
                    this.colorValue.get(),
                    ColorUtil.darker(this.colorValue.get(), 0.75f),
                    this.colorValue.get()
            };
            case Client -> new Color[]{
                    ClickGui.color(0),
                    ClickGui.color2(0),
                    ClickGui.color(0)
            };
        };
    }

    public Color fadeBetween(Color[] table, double speed, double offset) {
        return this.fadeBetween(table, (System.currentTimeMillis() + offset) % speed / speed);
    }

    public Color fadeBetween(Color[] table, double progress) {
        int i = table.length;
        if (progress == 1.0) {
            return table[0];
        }
        if (progress == 0.0) {
            return table[i - 1];
        }
        double max = Math.max(0.0, (1.0 - progress) * (i - 1));
        int min = (int) max;
        return this.fadeBetween(table[min], table[min + 1], max - min);
    }

    public Color fadeBetween(Color start, Color end, double progress) {
        if (progress > 1.0) {
            progress = 1.0 - progress % 1.0;
        }
        return this.gradient(start, end, progress);
    }

    public Color gradient(Color start, Color end, double progress) {
        double invert = 1.0 - progress;
        return new Color(
                (int) (start.getRed() * invert + end.getRed() * progress),
                (int) (start.getGreen() * invert + end.getGreen() * progress),
                (int) (start.getBlue() * invert + end.getBlue() * progress),
                (int) (start.getAlpha() * invert + end.getAlpha() * progress)
        );
    }
}
