package dev.mzc.client.module.impl.render;

import dev.mzc.client.events.render.Render3DEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.render.Render3DUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.ColorValue;
import dev.mzc.client.values.impl.NumberValue;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import static dev.mzc.client.Sakura.mc;

public class Fireflies extends Module {
    private final NumberValue<Integer> amount     = new NumberValue<>("Amount",      20,   1,   100, 1);
    private final NumberValue<Double>  speed      = new NumberValue<>("Speed",       0.02, 0.005, 0.1,  0.005);
    private final NumberValue<Double>  zigzagFreq = new NumberValue<>("Zigzag Freq", 0.1,  0.01,  0.5,  0.01);
    private final NumberValue<Double>  zigzagAmp  = new NumberValue<>("Zigzag Amp",  0.3,  0.0,   1.0,  0.05);
    private final NumberValue<Integer> trailLength= new NumberValue<>("Trail Length",15,   2,    50,   1);
    private final NumberValue<Double>  size       = new NumberValue<>("Size",        0.06, 0.01,  0.2,  0.01);
    private final NumberValue<Double>  flightRange= new NumberValue<>("Flight Range",25.0, 5.0,  50.0, 1.0);
    private final BoolValue  glow      = new BoolValue("Glow",      true);
    private final BoolValue  particles = new BoolValue("Particles", true);
    private final ColorValue color     = new ColorValue("Color", new Color(180, 255, 100, 180));

    private static final Identifier GLOW_TEXTURE =
            Identifier.of("sakura", "textures/fireflies/glow.png");

    private final List<Firefly>  fireflies    = new ArrayList<>();
    private final List<Particle> particleList = new ArrayList<>();
    private final Random random = new Random();

    public Fireflies() {
        super("Fireflies", Category.Render);
    }

    @Override
    protected void onEnable() {
        fireflies.clear();
        particleList.clear();
        if (mc.player == null) return;
        Vec3d pPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        for (int i = 0; i < amount.get(); i++) {
            fireflies.add(new Firefly(pPos.add(
                    (random.nextDouble() - 0.5) * 10, 1.5,
                    (random.nextDouble() - 0.5) * 10)));
        }
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null) return;
        if (fireflies.size() != amount.get()) onEnable();

        // Частицы-искры
        if (particles.get()) {
            Iterator<Particle> it = particleList.iterator();
            while (it.hasNext()) {
                Particle p = it.next();
                p.age++;
                if (p.age > 20) { it.remove(); continue; }
                float pAlpha = 1.0f - (p.age / 20f);
                Color pCol = new Color(
                        color.get().getRed(), color.get().getGreen(), color.get().getBlue(),
                        (int)(color.get().getAlpha() * pAlpha * 0.5));
                double pS = size.get() * 0.3 * pAlpha;
                net.minecraft.util.math.Box pBox = new net.minecraft.util.math.Box(
                        p.pos.x - pS, p.pos.y - pS, p.pos.z - pS,
                        p.pos.x + pS, p.pos.y + pS, p.pos.z + pS);
                Render3DUtil.drawFilledBox(event.getMatrices(), pBox, pCol.getRGB());
            }
        }

        for (Firefly fly : fireflies) {
            fly.update();

            // Хвост
            if (fly.trail.size() > 1) {
                for (int i = 0; i < fly.trail.size() - 1; i++) {
                    Vec3d p1 = fly.trail.get(i);
                    Vec3d p2 = fly.trail.get(i + 1);
                    float alphaFactor = (float) i / fly.trail.size();
                    Color lineCol = new Color(
                            color.get().getRed(), color.get().getGreen(), color.get().getBlue(),
                            (int)(color.get().getAlpha() * alphaFactor * 0.4));
                    Render3DUtil.drawLine(event.getMatrices(), p1, p2, lineCol, 0.8f);
                }
            }

            // Glow billboard
            if (glow.get()) {
                drawGlowBillboard(event.getMatrices(), fly.pos);
            }

            // Головка
            double s = size.get();
            net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(
                    fly.pos.x - s/2, fly.pos.y - s/2, fly.pos.z - s/2,
                    fly.pos.x + s/2, fly.pos.y + s/2, fly.pos.z + s/2);
            Render3DUtil.drawFilledBox(event.getMatrices(), box, color.get().getRGB());
        }
    }

    private void drawGlowBillboard(MatrixStack matrices, Vec3d pos) {
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();

        float dx = (float)(pos.x - camPos.x);
        float dy = (float)(pos.y - camPos.y);
        float dz = (float)(pos.z - camPos.z);

        float half = (float)(size.get() * 2.5);

        Color c = color.get();
        float r = c.getRed()   / 255f;
        float g = c.getGreen() / 255f;
        float b = c.getBlue()  / 255f;
        float a = c.getAlpha() / 255f * 0.85f;

        org.joml.Vector3f right = new org.joml.Vector3f();
        org.joml.Vector3f up    = new org.joml.Vector3f();
        camera.getRotation().transform(new org.joml.Vector3f(1, 0, 0), right);
        camera.getRotation().transform(new org.joml.Vector3f(0, 1, 0), up);

        float[] bl = { dx+(-right.x-up.x)*half, dy+(-right.y-up.y)*half, dz+(-right.z-up.z)*half };
        float[] br = { dx+( right.x-up.x)*half, dy+( right.y-up.y)*half, dz+( right.z-up.z)*half };
        float[] tr = { dx+( right.x+up.x)*half, dy+( right.y+up.y)*half, dz+( right.z+up.z)*half };
        float[] tl = { dx+(-right.x+up.x)*half, dy+(-right.y+up.y)*half, dz+(-right.z+up.z)*half };

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(
                com.mojang.blaze3d.vertex.VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_TEXTURE_COLOR
        );

        buffer.vertex(matrix, bl[0], bl[1], bl[2]).texture(0f, 1f).color(r, g, b, a);
        buffer.vertex(matrix, br[0], br[1], br[2]).texture(1f, 1f).color(r, g, b, a);
        buffer.vertex(matrix, tr[0], tr[1], tr[2]).texture(1f, 0f).color(r, g, b, a);
        buffer.vertex(matrix, tl[0], tl[1], tl[2]).texture(0f, 0f).color(r, g, b, a);

        // beaconBeam(texture, true) — true = additive blend
        // UV не скроллится т.к. мы передаём статичные 0-1 координаты
        RenderLayers.beaconBeam(GLOW_TEXTURE, true).draw(buffer.end());
    }

    private class Firefly {
        Vec3d pos;
        List<Vec3d> trail = new ArrayList<>();
        float phase, yaw, pitch, targetYaw, targetPitch;

        Firefly(Vec3d startPos) {
            this.pos = startPos;
            this.phase = random.nextFloat() * 1000;
            this.yaw = random.nextFloat() * 360;
            this.pitch = (random.nextFloat() - 0.5f) * 120;
            this.targetYaw = yaw;
            this.targetPitch = pitch;
        }

        void update() {
            trail.add(pos);
            if (trail.size() > trailLength.get()) trail.remove(0);

            if (particles.get() && random.nextInt(3) == 0)
                particleList.add(new Particle(pos));

            phase += zigzagFreq.get().floatValue();

            if (random.nextInt(40) == 0) {
                targetYaw   += (random.nextFloat() - 0.5f) * 70;
                targetPitch += (random.nextFloat() - 0.5f) * 50;
            }

            yaw   = MathHelper.lerp(0.04f, yaw,   targetYaw);
            pitch = MathHelper.lerp(0.04f, pitch, targetPitch);

            double radYaw   = Math.toRadians(yaw);
            double radPitch = Math.toRadians(pitch);

            double vx = -Math.sin(radYaw) * Math.cos(radPitch) * speed.get();
            double vy = -Math.sin(radPitch) * speed.get();
            double vz =  Math.cos(radYaw)  * Math.cos(radPitch) * speed.get();

            double zAmp = zigzagAmp.get();
            double zx = Math.cos(radYaw * 2) * Math.sin(phase)  * zAmp * 0.06;
            double zy = Math.cos(phase * 0.7)                    * zAmp * 0.06;
            double zz = Math.sin(radYaw * 2) * Math.cos(phase)   * zAmp * 0.06;

            pos = pos.add(vx + zx, vy + zy, vz + zz);

            double minH = mc.player.getY() + 0.5;
            double maxH = mc.player.getY() + 4.0;
            if (pos.y < minH) {
                pos = new Vec3d(pos.x, minH + 0.1, pos.z);
                targetPitch = -30;
            }
            if (pos.y > maxH) {
                pos = new Vec3d(pos.x, maxH - 0.1, pos.z);
                targetPitch = 30;
            }

            Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY() + 1.5, mc.player.getZ());
            double dist = pos.distanceTo(playerPos);

            if (dist > flightRange.get()) {
                double diffX = playerPos.x - pos.x;
                double diffY = playerPos.y - pos.y;
                double diffZ = playerPos.z - pos.z;
                targetYaw   = (float)Math.toDegrees(Math.atan2(diffZ, diffX)) - 90;
                targetPitch = (float)-Math.toDegrees(Math.atan2(diffY, Math.sqrt(diffX*diffX + diffZ*diffZ)));

                if (dist > flightRange.get() * 1.8) {
                    pos = playerPos.add((random.nextDouble()-0.5)*5, 0.5, (random.nextDouble()-0.5)*5);
                    trail.clear();
                }
            }
        }
    }

    private static class Particle {
        Vec3d pos;
        int age = 0;
        Particle(Vec3d pos) { this.pos = pos; }
    }
}