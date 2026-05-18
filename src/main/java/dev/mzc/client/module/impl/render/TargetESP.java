package dev.mzc.client.module.impl.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.mzc.client.Sakura;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.render.Render3DEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.module.impl.combat.KillAura;
import dev.mzc.client.utils.animations.Direction;
import dev.mzc.client.utils.animations.impl.DecelerateAnimation;
import dev.mzc.client.utils.color.ColorUtil;
import dev.mzc.client.values.impl.ColorValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.concurrent.ThreadLocalRandom;

public class TargetESP extends Module {

    public enum EspMode {
        Target, Jello, Spirits, Spirits1, Spirits2, Circle, GhostOrbits, Crystals, Square, Pulse, Ring, Lightning, Helix, Shockwave, BlackHole
    }

    public enum ColorMode {
        Rainbow, Wave, Single, Client
    }

    private final EnumValue<EspMode> espMode = new EnumValue<>("ESPMode", EspMode.Target);
    private final EnumValue<ColorMode> espColourMode = new EnumValue<>("ESPColourMode", ColorMode.Rainbow);
    private final ColorValue espColor1 = new ColorValue("ESPColor1", new Color(255, 0, 0, 255), () -> espColourMode.is(ColorMode.Single) || espColourMode.is(ColorMode.Wave));
    private final ColorValue espColor2 = new ColorValue("ESPColor2", new Color(0, 255, 255, 255), () -> espColourMode.is(ColorMode.Single) || espColourMode.is(ColorMode.Wave));
    private final NumberValue<Double> espSize = new NumberValue<>("ESPSize", 1.2, 0.5, 3.0, 0.1);
    private final NumberValue<Double> rotationSpeed = new NumberValue<>("RotSpeed", 2.0, 0.5, 10.0, 0.1);
    private final NumberValue<Double> waveSpeed = new NumberValue<>("WaveSpeed", 3.0, 0.5, 10.0, 0.1, () -> espColourMode.is(ColorMode.Wave));
    private final NumberValue<Double> jelloSpeed = new NumberValue<>("JelloSpeed", 0.15, 0.01, 2.0, 0.01, () -> espMode.is(EspMode.Jello));

    private float rotation = 0f;
    private float prevCircleStep = 0f;
    private float circleStep = 0f;

    private static final Identifier TARGET_TEX = Identifier.of("sakura", "particle/ghost-glow.png");
    private static final Identifier TARGET1_TEX = Identifier.of("sakura", "particle/ghost-triangle.png");
    private static final Identifier GLOW_TEXTURE = Identifier.of("sakura", "particle/glow.png");
    private static final Identifier SQUARE_TARGET_TEX = Identifier.of("sakura", "textures/square_target_esp.png");

    private final DecelerateAnimation animation = new DecelerateAnimation(400, 1.0);
    private final DecelerateAnimation animation2 = new DecelerateAnimation(250, 1.0);
    private Entity lastTarget;
    private float animationNurik;
    private long currentTime;
    private long timestamp4;
    private long timestamp5;
    private float value23;

    private static final int ORBIT_PARTICLE_COUNT = 3;
    private static final float ORBIT_BASE_RADIUS = 0.4f;
    private static final float ORBIT_BASE_MUL = 0.1f;
    private static final float ORBIT_SPEED = 15.0f;
    private static final int ORBIT_TRAIL_LENGTH = 40;

    private static final float[] SCALE_CACHE = new float[101];

    static {
        for (int k = 0; k <= 100; k++) {
            SCALE_CACHE[k] = Math.max(0.28f * (k / 100f), 0.15f);
        }
    }

    private final Vec3d[] orbitPositions = new Vec3d[ORBIT_PARTICLE_COUNT];
    private final Vec3d[] orbitMotions = new Vec3d[ORBIT_PARTICLE_COUNT];
    private final List<Vec3d>[] orbitTrails = new List[ORBIT_PARTICLE_COUNT];
    private float movingAngle = 0;
    private long lastOrbitTime = 0;
    private final DecelerateAnimation orbitShrinkAnim = new DecelerateAnimation(300, 1.0);
    private float crystalMoving = 0;

    private final RenderPipeline TARGET_ICON_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation("pipeline/sakura_target_icon")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build()
    );

    private final Function<Identifier, RenderLayer> TARGET_ICON_LAYER = Util.memoize(texture -> RenderLayer.of(
            "sakura_target_icon",
            RenderSetup.builder(TARGET_ICON_PIPELINE)
                    .texture("Sampler0", texture)
                    .translucent()
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .outputTarget(OutputTarget.MAIN_TARGET)
                    .build()
    ));

    public TargetESP() {
        super("TargetESP", Category.Render);
        for (int i = 0; i < ORBIT_PARTICLE_COUNT; i++) {
            this.orbitTrails[i] = new ArrayList<>();
            this.orbitMotions[i] = Vec3d.ZERO;
        }
    }

    @Override
    protected void onEnable() {
        rotation = 0f;
        prevCircleStep = 0f;
        circleStep = 0f;
        this.timestamp4 = System.currentTimeMillis();
        this.timestamp5 = System.nanoTime();
        this.currentTime = System.currentTimeMillis();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (espMode.is(EspMode.Jello)) {
            prevCircleStep = circleStep;
            circleStep += jelloSpeed.get().floatValue();
        }
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled()) return;
        KillAura killAura = Sakura.MODULES.getModule(KillAura.class);
        if (killAura == null || !killAura.isEnabled()) {
            animation.setDirection(Direction.BACKWARDS);
            animation2.setDirection(Direction.BACKWARDS);
        } else {
            LivingEntity target = killAura.getCurrentTarget();
            if (target != null && target.isAlive()) {
                if (this.lastTarget != target) {
                    for (int i = 0; i < ORBIT_PARTICLE_COUNT; i++) {
                        orbitPositions[i] = null;
                        orbitMotions[i] = Vec3d.ZERO;
                        orbitTrails[i].clear();
                    }
                }
                this.lastTarget = target;
                animation.setDirection(Direction.FORWARDS);
                animation2.setDirection(Direction.FORWARDS);
            } else {
                animation.setDirection(Direction.BACKWARDS);
                animation2.setDirection(Direction.BACKWARDS);
            }
        }

        if (this.lastTarget == null || animation.getOutput() <= 0.01) return;

        EspMode mode = espMode.get();
        switch (mode) {
            case Target -> renderTarget(event);
            case Jello -> renderJello(event);
            case Spirits -> drawSpiritsTrack(event);
            case Spirits1 -> drawSpirits(event);
            case Spirits2 -> renderSpirits2(event);
            case Circle -> drawCircle(event);
            case GhostOrbits -> drawGhostOrbits(event);
            case Crystals -> renderCrystals(event);
            case Square -> renderSquare(event);
            case Pulse -> renderPulse(event);
            case Ring -> renderRing(event);
            case Lightning -> renderLightning(event);
            case Helix -> renderHelix(event);
            case Shockwave -> renderShockwave(event);
            case BlackHole -> renderBlackHole(event);
        }
    }

    private void renderTarget(Render3DEvent event) {
        LivingEntity target = (LivingEntity) lastTarget;
        MatrixStack matrices = event.getMatrices();
        Vec3d cam = mc.getEntityRenderDispatcher().camera.getCameraPos();

        double ex = MathHelper.lerp(event.getTickDelta(), target.lastX, target.getX()) - cam.x;
        double ey = MathHelper.lerp(event.getTickDelta(), target.lastY, target.getY()) - cam.y;
        double ez = MathHelper.lerp(event.getTickDelta(), target.lastZ, target.getZ()) - cam.z;

        float entityHeight = target.getHeight();

        rotation -= rotationSpeed.get().floatValue();
        if (rotation <= -360f) rotation += 360f;

        float size = espSize.get().floatValue() * 0.5f * animation.getOutput().floatValue();

        matrices.push();
        matrices.translate(ex, ey + entityHeight * 0.5, ez);

        Camera camera = mc.gameRenderer.getCamera();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(770, 771, 1, 0);
        GlStateManager._disableCull();
        GlStateManager._disableDepthTest();

        Identifier texture = espColourMode.is(ColorMode.Rainbow) ? TARGET1_TEX : TARGET_TEX;
        drawTextureQuad(matrices, size, texture, animation.getOutput().floatValue());

        GlStateManager._enableCull();
        GlStateManager._enableDepthTest();
        GlStateManager._disableBlend();

        matrices.pop();
    }

    private void renderJello(Render3DEvent event) {
        LivingEntity target = (LivingEntity) lastTarget;
        MatrixStack matrices = event.getMatrices();
        Vec3d cam = mc.getEntityRenderDispatcher().camera.getCameraPos();

        double ex = MathHelper.lerp(event.getTickDelta(), target.lastX, target.getX()) - cam.x;
        double ey = MathHelper.lerp(event.getTickDelta(), target.lastY, target.getY()) - cam.y;
        double ez = MathHelper.lerp(event.getTickDelta(), target.lastZ, target.getZ()) - cam.z;

        float entityHeight = target.getHeight();
        float radius = (target.getWidth() * 0.75f) * espSize.get().floatValue();
        float cs = prevCircleStep + (circleStep - prevCircleStep) * event.getTickDelta();
        double prevSinAnim = absSinAnimation(cs - 0.45f);
        double sinAnim = absSinAnimation(cs);
        float topY = (float) (sinAnim * entityHeight);
        float bottomY = (float) (prevSinAnim * entityHeight);

        matrices.push();
        matrices.translate(ex, ey, ez);

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(770, 771, 1, 0);
        GlStateManager._disableCull();
        GlStateManager._disableDepthTest();

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        int segments = 60;
        float alphaMult = animation.getOutput().floatValue();
        for (int i = 0; i < segments; i++) {
            float p1 = i / (float) segments;
            float p2 = (i + 1) / (float) segments;
            double a1 = (Math.PI * 2.0) * p1;
            double a2 = (Math.PI * 2.0) * p2;
            float x1 = (float) (Math.cos(a1) * radius);
            float z1 = (float) (Math.sin(a1) * radius);
            float x2 = (float) (Math.cos(a2) * radius);
            float z2 = (float) (Math.sin(a2) * radius);

            Color c1 = getJelloColorForProgress(p1);
            Color c2 = getJelloColorForProgress(p2);

            int a1Int = (int) (c1.getAlpha() * alphaMult);
            int a2Int = (int) (c2.getAlpha() * alphaMult);

            buffer.vertex(matrix, x1, topY, z1).color(c1.getRed(), c1.getGreen(), c1.getBlue(), a1Int);
            buffer.vertex(matrix, x1, bottomY, z1).color(c1.getRed(), c1.getGreen(), c1.getBlue(), 0);
            buffer.vertex(matrix, x2, bottomY, z2).color(c2.getRed(), c2.getGreen(), c2.getBlue(), 0);
            buffer.vertex(matrix, x2, topY, z2).color(c2.getRed(), c2.getGreen(), c2.getBlue(), a2Int);
        }
        RenderLayers.debugQuads().draw(buffer.end());

        GlStateManager._enableCull();
        GlStateManager._enableDepthTest();
        GlStateManager._disableBlend();

        matrices.pop();
    }

    private void drawSpirits(Render3DEvent event) {
        LivingEntity target = (LivingEntity) lastTarget;
        MatrixStack matrices = event.getMatrices();
        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();

        double x = MathHelper.lerp(event.getTickDelta(), target.lastX, target.getX()) - camPos.x;
        double y = MathHelper.lerp(event.getTickDelta(), target.lastY, target.getY()) - camPos.y + (double) target.getHeight() / 2.0;
        double z = MathHelper.lerp(event.getTickDelta(), target.lastZ, target.getZ()) - camPos.z;

        float hurtTime = ((float) target.hurtTime - (target.hurtTime != 0 ? event.getTickDelta() : 0.0F)) / 10.0F;

        float animValue = -0.15F * animation2.getOutput().floatValue() + 0.65F;
        long time = (long) ((float) (System.currentTimeMillis() - this.timestamp4) / 2.0F);
        long nanoTime = System.nanoTime();
        float deltaTime = (float) (nanoTime - this.timestamp5) / 2000000.0F;
        this.timestamp5 = nanoTime;
        this.value23 += hurtTime * deltaTime;

        matrices.push();
        matrices.translate(x, y, z);
        matrices.scale(1.5F, 1.5F, 1.5F);

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(770, 1, 0, 1);
        GlStateManager._disableCull();
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        float alphaMult = animation2.getOutput().floatValue();
        for (int layer = 0; layer < 3; layer++) {
            for (int i = 0; i < 14; i++) {
                matrices.push();
                float progress = (float) i / 13.0F;
                float size = (0.55F * (1.0F - progress) + 0.2F * progress) * alphaMult;
                double angle = (double) (0.2F * ((float) time + this.value23 - (float) i * 7.0F) / 15.0F);

                boolean firstHalf = progress < 0.5F;
                float wave = firstHalf ? progress * 2.0F : (1.0F - progress) * 2.0F;
                double amplitude = Math.sin((double) wave * Math.PI) * 2.0;

                Random random = new Random((long) i * 12345L);
                double offsetX = (random.nextDouble() - 0.5) * amplitude;
                double offsetY = (random.nextDouble() - 0.5) * amplitude;
                double offsetZ = (random.nextDouble() - 0.5) * amplitude;

                double animOffsetX = offsetX * (double) alphaMult - offsetX;
                double animOffsetY = offsetY * (double) alphaMult - offsetY;
                double animOffsetZ = offsetZ * (double) alphaMult - offsetZ;

                double posX = -Math.sin(angle) * (double) animValue;
                double posZ = -Math.cos(angle) * (double) animValue;

                switch (layer) {
                    case 0:
                        animOffsetY += (double) i * 0.02;
                        matrices.translate(posX + animOffsetX, posZ + animOffsetY, -posZ + animOffsetZ);
                        break;
                    case 1:
                        animOffsetY -= (double) i * 0.02;
                        matrices.translate(-posX + animOffsetX, posX + animOffsetY, -posZ + animOffsetZ);
                        break;
                    case 2:
                        matrices.translate(-posX + animOffsetX, -posX + animOffsetY, posZ + animOffsetZ);
                }

                float particleSize = size * 0.5F;
                Color color = getColorForProgress(progress);
                int rgba = ColorUtil.getColor(color.getRed(), color.getGreen(), color.getBlue(), (int) (600.0F * alphaMult));

                matrices.multiply(mc.gameRenderer.getCamera().getRotation());

                Matrix4f matrix = matrices.peek().getPositionMatrix();
                buffer.vertex(matrix, -particleSize, -particleSize, 0.0F).texture(1.0F, 1.0F).color(rgba);
                buffer.vertex(matrix, particleSize, -particleSize, 0.0F).texture(0.0F, 1.0F).color(rgba);
                buffer.vertex(matrix, particleSize, particleSize, 0.0F).texture(0.0F, 0.0F).color(rgba);
                buffer.vertex(matrix, -particleSize, particleSize, 0.0F).texture(1.0F, 0.0F).color(rgba);

                matrices.pop();
            }
        }

        TARGET_ICON_LAYER.apply(GLOW_TEXTURE).draw(buffer.end());
        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(true);
        GlStateManager._disableBlend();
        GlStateManager._enableCull();
        matrices.pop();
    }

    private void renderSpirits2(Render3DEvent event) {
        LivingEntity target = (LivingEntity) lastTarget;
        MatrixStack matrices = event.getMatrices();
        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();

        double x = MathHelper.lerp(event.getTickDelta(), target.lastX, target.getX()) - camPos.x;
        double y = MathHelper.lerp(event.getTickDelta(), target.lastY, target.getY()) - camPos.y + (double) target.getHeight() / 2.0;
        double z = MathHelper.lerp(event.getTickDelta(), target.lastZ, target.getZ()) - camPos.z;

        float time = (float) (System.currentTimeMillis() - this.timestamp4) / 1100.0F;
        float rotSpd = 360.0F;
        float rotation = time * rotSpd;
        float radius = 0.5F;

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(770, 1, 0, 1);
        GlStateManager._disableCull();
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        float alphaMult = animation2.getOutput().floatValue();
        for (int layer = 0; layer < 4; layer++) {
            float layerOffset = (float) (layer - 1) * 0.4F;
            float prevSize = -1.0F;
            for (float i = 0.0F; i < 130.0F; i++) {
                float angle = rotation + i + layerOffset * 360.0F;
                double radians = Math.toRadians(-angle);
                double yOffset = Math.sin(radians + 2.0) * (double) layerOffset;
                float size = radius * (i / 140.0F);
                float finalSize = prevSize >= 0.0F ? (prevSize + size) / 2.0F : size;
                prevSize = size;
                finalSize *= alphaMult;

                float alpha = MathHelper.clamp(finalSize, 0.0F, 1.0F);
                Color color = getColorForProgress(i / 130.0f);
                int rgba = ColorUtil.getColor(color.getRed(), color.getGreen(), color.getBlue(), (int) (600.0 * alphaMult * alpha));

                matrices.push();
                matrices.translate(x, y + yOffset, z);
                matrices.multiply(mc.gameRenderer.getCamera().getRotation());

                float halfSize = finalSize / 2.0F;
                double cosAngle = Math.cos(radians) * (double) radius - (double) halfSize;
                double sinAngle = Math.sin(radians) * (double) radius - (double) halfSize;

                Matrix4f matrix = matrices.peek().getPositionMatrix();
                buffer.vertex(matrix, (float) cosAngle, -halfSize, (float) sinAngle).texture(0.0F, 1.0F).color(rgba);
                buffer.vertex(matrix, (float) (cosAngle + finalSize), -halfSize, (float) sinAngle).texture(1.0F, 1.0F).color(rgba);
                buffer.vertex(matrix, (float) (cosAngle + finalSize), halfSize, (float) sinAngle).texture(1.0F, 0.0F).color(rgba);
                buffer.vertex(matrix, (float) cosAngle, halfSize, (float) sinAngle).texture(0.0F, 0.0F).color(rgba);

                matrices.pop();
            }
        }

        TARGET_ICON_LAYER.apply(GLOW_TEXTURE).draw(buffer.end());
        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(true);
        GlStateManager._disableBlend();
        GlStateManager._enableCull();
    }

    private void drawCircle(Render3DEvent event) {
        LivingEntity target = (LivingEntity) lastTarget;
        MatrixStack matrices = event.getMatrices();
        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();

        double x = MathHelper.lerp(event.getTickDelta(), target.lastX, target.getX()) - camPos.x;
        double y = MathHelper.lerp(event.getTickDelta(), target.lastY, target.getY()) - camPos.y;
        double z = MathHelper.lerp(event.getTickDelta(), target.lastZ, target.getZ()) - camPos.z;

        float height = target.getHeight();
        short period = 1500;
        double time = (double) (System.currentTimeMillis() % (long) period);
        boolean ascending = time > (double) (period / 2);
        float progress = (float) (time / (double) ((float) period / 2.0F));

        if (ascending) progress -= 1.0F;
        else progress = 1.0F - progress;

        progress = (double) progress < 0.5 ? 2.0F * progress * progress :
                (float) (1.0 - Math.pow((double) (-2.0F * progress + 2.0F), 2.0) / 2.0);

        double yOffset = (double) (height / 2.0F * ((double) progress > 0.5 ? 1.0F - progress : progress) * (float) (ascending ? -1 : 1));

        matrices.push();
        matrices.translate(x, y + (double) (height * progress) + yOffset, z);

        float hurtTime = ((float) target.hurtTime - (target.hurtTime != 0 ? event.getTickDelta() : 0.0F)) / 10.0F;

        long timeMs = (long) ((float) (System.currentTimeMillis() - this.timestamp4) / 2.5F);
        long nanoTime = System.nanoTime();
        float deltaTime = (float) (nanoTime - this.timestamp5) / 2000000.0F;
        this.timestamp5 = nanoTime;
        this.value23 += hurtTime * deltaTime;

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(770, 1, 0, 1);
        GlStateManager._disableCull();
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        float alphaMult = animation2.getOutput().floatValue();
        for (int layer = 0; layer < 4; layer++) {
            for (int i = 0; i < 15; i++) {
                matrices.push();
                float particleProgress = (float) i / 14.0F;
                float size = (0.5F * (1.0F - particleProgress) + 0.5F * particleProgress) * alphaMult;
                float angle = 0.2F * ((float) timeMs + this.value23 - (float) i * 3.5F) / 15.0F;

                boolean firstHalf = particleProgress < 0.5F;
                float wave = firstHalf ? particleProgress * 2.0F : (1.0F - progress) * 2.0F;
                double amplitude = Math.sin((double) wave * Math.PI) * 2.0;

                Random random = new Random((long) i * 12345L);
                double offsetX = (random.nextDouble() - 0.5) * amplitude;
                double offsetY = (random.nextDouble() - 0.5) * amplitude;
                double offsetZ = (random.nextDouble() - 0.5) * amplitude;

                double animOffsetX = offsetX * (double) alphaMult - offsetX;
                double animOffsetY = offsetY * (double) alphaMult - offsetY;
                double animOffsetZ = offsetZ * (double) alphaMult - offsetZ;

                double radius = 0.7;
                switch (layer) {
                    case 0:
                        matrices.translate(Math.cos((double) angle) * radius + animOffsetX, animOffsetY, Math.sin((double) angle) * radius + animOffsetZ);
                        break;
                    case 1:
                        matrices.translate(-Math.sin((double) angle) * radius + animOffsetX, animOffsetY, Math.cos((double) angle) * radius + animOffsetZ);
                        break;
                    case 2:
                        matrices.translate(-Math.cos((double) angle) * radius + animOffsetX, animOffsetY, -Math.sin((double) angle) * radius + animOffsetZ);
                        break;
                    case 3:
                        matrices.translate(Math.sin((double) angle) * radius + animOffsetX, animOffsetY, -Math.cos((double) angle) * radius + animOffsetZ);
                }

                float particleSize = size * 0.5F;
                Color color = getColorForProgress(particleProgress);
                int rgba = ColorUtil.getColor(color.getRed(), color.getGreen(), color.getBlue(), (int) (400.0F * alphaMult));

                matrices.multiply(mc.gameRenderer.getCamera().getRotation());

                Matrix4f matrix = matrices.peek().getPositionMatrix();
                buffer.vertex(matrix, -particleSize, -particleSize, 0.0F).texture(1.0F, 1.0F).color(rgba);
                buffer.vertex(matrix, particleSize, -particleSize, 0.0F).texture(0.0F, 1.0F).color(rgba);
                buffer.vertex(matrix, particleSize, particleSize, 0.0F).texture(0.0F, 0.0F).color(rgba);
                buffer.vertex(matrix, -particleSize, particleSize, 0.0F).texture(1.0F, 0.0F).color(rgba);

                matrices.pop();
            }
        }

        TARGET_ICON_LAYER.apply(GLOW_TEXTURE).draw(buffer.end());
        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(true);
        GlStateManager._disableBlend();
        GlStateManager._enableCull();
        matrices.pop();
    }

    private void drawSpiritsTrack(Render3DEvent event) {
        LivingEntity target = (LivingEntity) lastTarget;
        long currentTimeMs = System.currentTimeMillis();
        this.animationNurik += (float) (currentTimeMs - this.currentTime) / 120.0F;
        this.currentTime = currentTimeMs;

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(770, 1, 0, 1);
        GlStateManager._disableCull();
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);

        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
        double x = MathHelper.lerp(event.getTickDelta(), target.lastX, target.getX()) - camPos.x;
        double y = MathHelper.lerp(event.getTickDelta(), target.lastY, target.getY()) - camPos.y;
        double z = MathHelper.lerp(event.getTickDelta(), target.lastZ, target.getZ()) - camPos.z;

        int n2 = 3, n3 = 12, n4 = 3 * n2;
        MatrixStack matrices = event.getMatrices();
        matrices.push();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        float alphaMult = animation2.getOutput().floatValue();
        for (int i = 0; i < n4; i += n2) {
            for (int j = 0; j < n3; ++j) {
                float f2 = this.animationNurik + (float) j * 0.1F;
                int n5 = (int) Math.pow((double) i, 2.0D);
                matrices.push();
                matrices.translate(x + (double) (0.8F * MathHelper.sin(f2 + (float) n5)), y + 0.5 + (double) (0.3F * MathHelper.sin(this.animationNurik + (float) j * 0.2F)) + (double) (0.2F * (float) i), z + (double) (0.8F * MathHelper.cos(f2 - (float) n5)));
                matrices.scale(alphaMult * (0.005F + (float) j / 2000.0F), alphaMult * (0.005F + (float) j / 2000.0F), alphaMult * (0.005F + (float) j / 2000.0F));
                matrices.multiply(mc.gameRenderer.getCamera().getRotation());
                int n7 = -25, n8 = 50;
                Color color = getColorForProgress((float) j / n3);
                int rgba = ColorUtil.getColor(color.getRed(), color.getGreen(), color.getBlue(), (int) (alphaMult * 600.0F));
                Matrix4f matrix = matrices.peek().getPositionMatrix();
                buffer.vertex(matrix, (float) n7, (float) (n7 + n8), 0.0F).texture(0.0F, 1.0F).color(rgba);
                buffer.vertex(matrix, (float) (n7 + n8), (float) (n7 + n8), 0.0F).texture(1.0F, 1.0F).color(rgba);
                buffer.vertex(matrix, (float) (n7 + n8), (float) n7, 0.0F).texture(1.0F, 0.0F).color(rgba);
                buffer.vertex(matrix, (float) n7, (float) n7, 0.0F).texture(0.0F, 0.0F).color(rgba);
                matrices.pop();
            }
        }

        TARGET_ICON_LAYER.apply(GLOW_TEXTURE).draw(buffer.end());
        matrices.pop();
        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(true);
        GlStateManager._disableBlend();
        GlStateManager._enableCull();
    }

    private void drawGhostOrbits(Render3DEvent event) {
        LivingEntity target = (LivingEntity) lastTarget;
        MatrixStack matrices = event.getMatrices();
        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
        float delta = event.getTickDelta();
        Camera camera = mc.gameRenderer.getCamera();

        double tx = MathHelper.lerp(delta, target.lastX, target.getX());
        double ty = MathHelper.lerp(delta, target.lastY, target.getY());
        double tz = MathHelper.lerp(delta, target.lastZ, target.getZ());
        Vec3d targetCenter = new Vec3d(tx, ty + target.getHeight() / 2.0, tz);

        long now = System.currentTimeMillis();
        if (lastOrbitTime == 0) lastOrbitTime = now;
        float dtMs = now - lastOrbitTime;
        lastOrbitTime = now;

        float fpsFactor = 500.0f / Math.max(mc.getCurrentFps(), 10);
        movingAngle += (20.0f * dtMs / 16.667f) * (ORBIT_SPEED / 55.0f);

        boolean isHurt = target.hurtTime > 7;
        orbitShrinkAnim.setDirection(isHurt ? Direction.FORWARDS : Direction.BACKWARDS);
        float shrinkValue = orbitShrinkAnim.getOutput().floatValue();

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(770, 1, 0, 1);
        GlStateManager._disableCull();
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        float alphaMult = animation2.getOutput().floatValue();
        for (int i = 0; i < ORBIT_PARTICLE_COUNT; i++) {
            float angleOffset = i * 360f / ORBIT_PARTICLE_COUNT;
            float currentAngle = movingAngle + angleOffset;
            double radian = Math.toRadians(currentAngle);

            float orbitRadius = ORBIT_BASE_RADIUS - shrinkValue * ORBIT_BASE_RADIUS;
            float ox = (float) Math.sin(radian) * orbitRadius;
            float oz = (float) Math.cos(radian) * orbitRadius;
            double oy = 0.3 * Math.sin(Math.toRadians(movingAngle / (i + 1.0f)));

            Vec3d targetGhostPos = targetCenter.add(ox, oy, oz);

            if (orbitPositions[i] == null || orbitPositions[i].distanceTo(targetGhostPos) > 10) {
                orbitPositions[i] = targetGhostPos;
                orbitMotions[i] = Vec3d.ZERO;
            }

            float mul = ORBIT_BASE_MUL * fpsFactor;
            Vec3d diff = targetGhostPos.subtract(orbitPositions[i]);
            orbitMotions[i] = diff.multiply(mul, mul, mul);
            orbitPositions[i] = orbitPositions[i].add(orbitMotions[i]);

            if (orbitTrails[i].isEmpty() || orbitTrails[i].get(0).distanceTo(orbitPositions[i]) > 0.01) {
                orbitTrails[i].add(0, orbitPositions[i]);
                while (orbitTrails[i].size() > ORBIT_TRAIL_LENGTH) orbitTrails[i].remove(orbitTrails[i].size() - 1);
            }

            for (int j = 0; j < orbitTrails[i].size(); j++) {
                Vec3d p = orbitTrails[i].get(j);
                float offset = 1.0f - (float) j / ORBIT_TRAIL_LENGTH;

                matrices.push();
                matrices.translate(p.x - camPos.x, p.y - camPos.y, p.z - camPos.z);
                matrices.multiply(camera.getRotation());
                Matrix4f matrix = matrices.peek().getPositionMatrix();

                float opacity = (float) Math.pow(offset, 1.8) * alphaMult * 0.7f;
                Color baseColor = getColorForProgress(offset);
                int color = ColorUtil.getColor(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), (int) (opacity * 255));
                float scale = SCALE_CACHE[Math.min((int) (offset * 100), 100)] * 0.8f;

                buffer.vertex(matrix, -scale, scale, 0).texture(0f, 1f).color(color);
                buffer.vertex(matrix, scale, scale, 0).texture(1f, 1f).color(color);
                buffer.vertex(matrix, scale, -scale, 0).texture(1f, 0f).color(color);
                buffer.vertex(matrix, -scale, -scale, 0).texture(0f, 0f).color(color);
                matrices.pop();
            }

            if (!orbitTrails[i].isEmpty()) {
                Vec3d head = orbitTrails[i].get(0);
                matrices.push();
                matrices.translate(head.x - camPos.x, head.y - camPos.y, head.z - camPos.z);
                matrices.multiply(camera.getRotation());
                Matrix4f matrix = matrices.peek().getPositionMatrix();

                float headScale = 0.35f * alphaMult;
                Color baseColor = getColorForProgress(0);
                int headColor = ColorUtil.getColor(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), (int) (120 * alphaMult));

                buffer.vertex(matrix, -headScale, headScale, 0).texture(0f, 1f).color(headColor);
                buffer.vertex(matrix, headScale, headScale, 0).texture(1f, 1f).color(headColor);
                buffer.vertex(matrix, headScale, -headScale, 0).texture(1f, 0f).color(headColor);
                buffer.vertex(matrix, -headScale, -headScale, 0).texture(0f, 0f).color(headColor);
                matrices.pop();
            }
        }

        TARGET_ICON_LAYER.apply(GLOW_TEXTURE).draw(buffer.end());
        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(true);
        GlStateManager._disableBlend();
        GlStateManager._enableCull();
    }

    private void renderCrystals(Render3DEvent event) {
        float alpha = animation2.getOutput().floatValue();
        if (alpha <= 0.0F) return;
        LivingEntity target = (LivingEntity) lastTarget;
        MatrixStack matrices = event.getMatrices();
        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
        float tickDelta = event.getTickDelta();

        double tx = MathHelper.lerp(tickDelta, target.lastX, target.getX());
        double ty = MathHelper.lerp(tickDelta, target.lastY, target.getY());
        double tz = MathHelper.lerp(tickDelta, target.lastZ, target.getZ());

        crystalMoving += 1.0f;

        float entityHeight = target.getHeight();
        float entityWidth = target.getWidth();
        float width = entityWidth * 1.5f;

        Color themeColor = getColorForProgress(0);
        int cr = themeColor.getRed();
        int cg = themeColor.getGreen();
        int cb = themeColor.getBlue();

        matrices.push();
        matrices.translate(tx - camPos.x, ty - camPos.y, tz - camPos.z);

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(770, 771, 1, 0);
        GlStateManager._disableCull();
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);

        BufferBuilder crystalBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        int crystalAlpha = Math.min(255, (int) (alpha * 255));
        int cTop = ColorUtil.getColor(Math.min(255, cr + 60), Math.min(255, cg + 60), Math.min(255, cb + 60), crystalAlpha);
        int cSide1 = ColorUtil.getColor(Math.min(255, cr + 30), Math.min(255, cg + 30), Math.min(255, cb + 30), crystalAlpha);
        int cSide2 = ColorUtil.getColor(cr, cg, cb, crystalAlpha);
        int cBot = ColorUtil.getColor(Math.max(0, cr - 30), Math.max(0, cg - 30), Math.max(0, cb - 30), crystalAlpha);

        float cw = 0.075f;
        float ch = 0.20f;

        for (int i = 0; i < 360; i += 19) {
            float val = 1.2f - 0.5f * alpha;
            float angleDeg = i + crystalMoving * 0.3f;
            float angleRad = (float) Math.toRadians(angleDeg);
            float sin = (float) (Math.sin(angleRad) * width * val);
            float cos = (float) (Math.cos(angleRad) * width * val);

            float heightPrc = ((i / 20.0f) * 0.6180339f) % 1.0f;
            float crystalY = entityHeight * heightPrc;

            matrices.push();
            matrices.translate(sin, crystalY, cos);

            Vector3f dir = new Vector3f(-sin, 0, -cos).normalize();
            Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), dir);
            matrices.multiply(rotation);

            Matrix4f matrix = matrices.peek().getPositionMatrix();

            float[] ex = {cw, 0, -cw, 0};
            float[] ez = {0, cw, 0, -cw};

            for (int j = 0; j < 4; j++) {
                int next = (j + 1) % 4;
                int fc = (j % 2 == 0) ? cTop : cSide1;
                crystalBuffer.vertex(matrix, 0, ch, 0).color(fc);
                crystalBuffer.vertex(matrix, ex[j], 0, ez[j]).color(fc);
                crystalBuffer.vertex(matrix, ex[next], 0, ez[next]).color(fc);
            }

            for (int j = 0; j < 4; j++) {
                int next = (j + 1) % 4;
                int fc = (j % 2 == 0) ? cBot : cSide2;
                crystalBuffer.vertex(matrix, 0, -ch, 0).color(fc);
                crystalBuffer.vertex(matrix, ex[next], 0, ez[next]).color(fc);
                crystalBuffer.vertex(matrix, ex[j], 0, ez[j]).color(fc);
            }

            matrices.pop();
        }

        RenderLayers.debugQuads().draw(crystalBuffer.end());

        BufferBuilder glowBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        Camera camera = mc.gameRenderer.getCamera();

        for (int i = 0; i < 360; i += 19) {
            float val = 1.2f - 0.5f * alpha;
            float angleDeg = i + crystalMoving * 0.3f;
            float angleRad = (float) Math.toRadians(angleDeg);
            float sin = (float) (Math.sin(angleRad) * width * val);
            float cos = (float) (Math.cos(angleRad) * width * val);

            float heightPrc = ((i / 20.0f) * 0.6180339f) % 1.0f;
            float crystalY = entityHeight * heightPrc;

            matrices.push();
            matrices.translate(sin, crystalY, cos);
            matrices.multiply(camera.getRotation());

            Matrix4f matrix = matrices.peek().getPositionMatrix();
            float glowSize = 0.15f * alpha;
            int glowAlpha = (int) (alpha * 100);
            int glowColor = ColorUtil.getColor(cr, cg, cb, glowAlpha);

            glowBuffer.vertex(matrix, -glowSize, glowSize, 0).texture(0f, 1f).color(glowColor);
            glowBuffer.vertex(matrix, glowSize, glowSize, 0).texture(1f, 1f).color(glowColor);
            glowBuffer.vertex(matrix, glowSize, -glowSize, 0).texture(1f, 0f).color(glowColor);
            glowBuffer.vertex(matrix, -glowSize, -glowSize, 0).texture(0f, 0f).color(glowColor);

            matrices.pop();
        }

        TARGET_ICON_LAYER.apply(GLOW_TEXTURE).draw(glowBuffer.end());
        matrices.pop();

        GlStateManager._enableCull();
        GlStateManager._depthMask(true);
        GlStateManager._enableDepthTest();
        GlStateManager._disableBlend();
    }

    private void renderSquare(Render3DEvent event) {
        LivingEntity target = (LivingEntity) lastTarget;
        MatrixStack matrices = event.getMatrices();
        Vec3d cam = mc.getEntityRenderDispatcher().camera.getCameraPos();

        double ex = MathHelper.lerp(event.getTickDelta(), target.lastX, target.getX()) - cam.x;
        double ey = MathHelper.lerp(event.getTickDelta(), target.lastY, target.getY()) - cam.y;
        double ez = MathHelper.lerp(event.getTickDelta(), target.lastZ, target.getZ()) - cam.z;

        float entityHeight = target.getHeight();

        rotation -= rotationSpeed.get().floatValue();
        if (rotation <= -360f) rotation += 360f;

        float size = espSize.get().floatValue() * 0.6f * animation.getOutput().floatValue();

        matrices.push();
        matrices.translate(ex, ey + entityHeight * 0.5, ez);

        Camera camera = mc.gameRenderer.getCamera();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(770, 771, 1, 0);
        GlStateManager._disableCull();
        GlStateManager._disableDepthTest();

        drawTextureQuad(matrices, size, SQUARE_TARGET_TEX, animation.getOutput().floatValue());

        GlStateManager._enableCull();
        GlStateManager._enableDepthTest();
        GlStateManager._disableBlend();

        matrices.pop();
    }

    private void renderPulse(Render3DEvent event) {
        LivingEntity target = (LivingEntity) lastTarget;
        MatrixStack matrices = event.getMatrices();
        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();

        double x = MathHelper.lerp(event.getTickDelta(), target.lastX, target.getX()) - camPos.x;
        double y = MathHelper.lerp(event.getTickDelta(), target.lastY, target.getY()) - camPos.y;
        double z = MathHelper.lerp(event.getTickDelta(), target.lastZ, target.getZ()) - camPos.z;

        float entityHeight = target.getHeight();
        float entityWidth = target.getWidth();

        long time = System.currentTimeMillis();
        float pulseSpeed = 0.003f;
        float pulse = (float) Math.sin(time * pulseSpeed) * 0.5f + 0.5f;
        float baseRadius = entityWidth * espSize.get().floatValue();
        float radius = baseRadius * (0.8f + pulse * 0.4f);

        matrices.push();
        matrices.translate(x, y + entityHeight * 0.5, z);

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(770, 1, 0, 1);
        GlStateManager._disableCull();
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        int segments = 60;
        float alphaMult = animation.getOutput().floatValue();
        Color color = getColorForProgress(pulse);
        int rgba = ColorUtil.getColor(color.getRed(), color.getGreen(), color.getBlue(), (int) (150 * alphaMult));

        for (int i = 0; i < segments; i++) {
            float p1 = i / (float) segments;
            float p2 = (i + 1) / (float) segments;
            double a1 = (Math.PI * 2.0) * p1;
            double a2 = (Math.PI * 2.0) * p2;
            float x1 = (float) (Math.cos(a1) * radius);
            float z1 = (float) (Math.sin(a1) * radius);
            float x2 = (float) (Math.cos(a2) * radius);
            float z2 = (float) (Math.sin(a2) * radius);

            buffer.vertex(matrix, x1, 0, z1).color(rgba);
            buffer.vertex(matrix, 0, 0, 0).color(rgba);
            buffer.vertex(matrix, x2, 0, z2).color(rgba);
        }

        RenderLayers.debugQuads().draw(buffer.end());

        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(true);
        GlStateManager._disableBlend();
        GlStateManager._enableCull();

        matrices.pop();
    }

    private void renderRing(Render3DEvent event) {
        LivingEntity target = (LivingEntity) lastTarget;
        MatrixStack matrices = event.getMatrices();
        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();

        double x = MathHelper.lerp(event.getTickDelta(), target.lastX, target.getX()) - camPos.x;
        double y = MathHelper.lerp(event.getTickDelta(), target.lastY, target.getY()) - camPos.y;
        double z = MathHelper.lerp(event.getTickDelta(), target.lastZ, target.getZ()) - camPos.z;

        float entityHeight = target.getHeight();
        float entityWidth = target.getWidth();

        rotation -= rotationSpeed.get().floatValue() * 0.5f;
        if (rotation <= -360f) rotation += 360f;

        float baseRadius = entityWidth * espSize.get().floatValue();
        float ringThickness = 0.08f;

        matrices.push();
        matrices.translate(x, y + entityHeight * 0.1, z);

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(770, 1, 0, 1);
        GlStateManager._disableCull();
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        int segments = 80;
        float alphaMult = animation.getOutput().floatValue();
        Color color = getColorForProgress(0);
        int rgba = ColorUtil.getColor(color.getRed(), color.getGreen(), color.getBlue(), (int) (200 * alphaMult));

        for (int i = 0; i < segments; i++) {
            float p1 = i / (float) segments;
            float p2 = (i + 1) / (float) segments;
            double a1 = (Math.PI * 2.0) * p1;
            double a2 = (Math.PI * 2.0) * p2;

            float outerR = baseRadius;
            float innerR = baseRadius - ringThickness;

            float x1Outer = (float) (Math.cos(a1) * outerR);
            float z1Outer = (float) (Math.sin(a1) * outerR);
            float x2Outer = (float) (Math.cos(a2) * outerR);
            float z2Outer = (float) (Math.sin(a2) * outerR);

            float x1Inner = (float) (Math.cos(a1) * innerR);
            float z1Inner = (float) (Math.sin(a1) * innerR);
            float x2Inner = (float) (Math.cos(a2) * innerR);
            float z2Inner = (float) (Math.sin(a2) * innerR);

            buffer.vertex(matrix, x1Outer, 0, z1Outer).color(rgba);
            buffer.vertex(matrix, x1Inner, 0, z1Inner).color(rgba);
            buffer.vertex(matrix, x2Inner, 0, z2Inner).color(rgba);
            buffer.vertex(matrix, x2Outer, 0, z2Outer).color(rgba);
        }

        RenderLayers.debugQuads().draw(buffer.end());

        // Second ring at different height
        matrices.push();
        matrices.translate(0, entityHeight * 0.4, 0);
        matrix = matrices.peek().getPositionMatrix();
        
        BufferBuilder buffer2 = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        Color color2 = getColorForProgress(0.5f);
        int rgba2 = ColorUtil.getColor(color2.getRed(), color2.getGreen(), color2.getBlue(), (int) (150 * alphaMult));

        for (int i = 0; i < segments; i++) {
            float p1 = i / (float) segments;
            float p2 = (i + 1) / (float) segments;
            double a1 = (Math.PI * 2.0) * p1;
            double a2 = (Math.PI * 2.0) * p2;

            float outerR = baseRadius * 0.9f;
            float innerR = outerR - ringThickness;

            float x1Outer = (float) (Math.cos(a1) * outerR);
            float z1Outer = (float) (Math.sin(a1) * outerR);
            float x2Outer = (float) (Math.cos(a2) * outerR);
            float z2Outer = (float) (Math.sin(a2) * outerR);

            float x1Inner = (float) (Math.cos(a1) * innerR);
            float z1Inner = (float) (Math.sin(a1) * innerR);
            float x2Inner = (float) (Math.cos(a2) * innerR);
            float z2Inner = (float) (Math.sin(a2) * innerR);

            buffer2.vertex(matrix, x1Outer, 0, z1Outer).color(rgba2);
            buffer2.vertex(matrix, x1Inner, 0, z1Inner).color(rgba2);
            buffer2.vertex(matrix, x2Inner, 0, z2Inner).color(rgba2);
            buffer2.vertex(matrix, x2Outer, 0, z2Outer).color(rgba2);
        }

        RenderLayers.debugQuads().draw(buffer2.end());
        matrices.pop();

        // Third ring
        matrices.push();
        matrices.translate(0, entityHeight * 0.8, 0);
        matrix = matrices.peek().getPositionMatrix();
        
        BufferBuilder buffer3 = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        Color color3 = getColorForProgress(0.75f);
        int rgba3 = ColorUtil.getColor(color3.getRed(), color3.getGreen(), color3.getBlue(), (int) (100 * alphaMult));

        for (int i = 0; i < segments; i++) {
            float p1 = i / (float) segments;
            float p2 = (i + 1) / (float) segments;
            double a1 = (Math.PI * 2.0) * p1;
            double a2 = (Math.PI * 2.0) * p2;

            float outerR = baseRadius * 0.75f;
            float innerR = outerR - ringThickness;

            float x1Outer = (float) (Math.cos(a1) * outerR);
            float z1Outer = (float) (Math.sin(a1) * outerR);
            float x2Outer = (float) (Math.cos(a2) * outerR);
            float z2Outer = (float) (Math.sin(a2) * outerR);

            float x1Inner = (float) (Math.cos(a1) * innerR);
            float z1Inner = (float) (Math.sin(a1) * innerR);
            float x2Inner = (float) (Math.cos(a2) * innerR);
            float z2Inner = (float) (Math.sin(a2) * innerR);

            buffer3.vertex(matrix, x1Outer, 0, z1Outer).color(rgba3);
            buffer3.vertex(matrix, x1Inner, 0, z1Inner).color(rgba3);
            buffer3.vertex(matrix, x2Inner, 0, z2Inner).color(rgba3);
            buffer3.vertex(matrix, x2Outer, 0, z2Outer).color(rgba3);
        }

        RenderLayers.debugQuads().draw(buffer3.end());
        matrices.pop();

        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(true);
        GlStateManager._disableBlend();
        GlStateManager._enableCull();

        matrices.pop();
    }

    private void renderLightning(Render3DEvent event) {
        LivingEntity target = (LivingEntity) lastTarget;
        MatrixStack matrices = event.getMatrices();
        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();

        double tx = MathHelper.lerp(event.getTickDelta(), target.lastX, target.getX()) - camPos.x;
        double ty = MathHelper.lerp(event.getTickDelta(), target.lastY, target.getY()) - camPos.y;
        double tz = MathHelper.lerp(event.getTickDelta(), target.lastZ, target.getZ()) - camPos.z;

        float entityHeight = target.getHeight();
        float entityWidth = target.getWidth();

        long time = System.currentTimeMillis();
        float alphaMult = animation.getOutput().floatValue();

        matrices.push();
        matrices.translate(tx, ty, tz);

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(770, 1, 0, 1);
        GlStateManager._disableCull();
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR);

        Random random = new Random(time);
        int numBolts = 8;
        float baseRadius = entityWidth * espSize.get().floatValue() * 1.5f;

        for (int i = 0; i < numBolts; i++) {
            Color color = getColorForProgress(i / (float) numBolts);
            int rgba = ColorUtil.getColor(color.getRed(), color.getGreen(), color.getBlue(), (int) (200 * alphaMult));

            // Generate lightning bolt from top to bottom
            float startY = entityHeight;
            float endY = 0;
            int segments = 12;
            float segmentHeight = (startY - endY) / segments;

            float prevX = (float) ((random.nextDouble() - 0.5) * baseRadius * 0.5);
            float prevZ = (float) ((random.nextDouble() - 0.5) * baseRadius * 0.5);
            float prevY = startY;

            for (int j = 0; j < segments; j++) {
                float currY = startY - (j + 1) * segmentHeight;
                float currX = prevX + (float) ((random.nextDouble() - 0.5) * baseRadius * 0.3);
                float currZ = prevZ + (float) ((random.nextDouble() - 0.5) * baseRadius * 0.3);

                // Clamp to reasonable bounds
                currX = MathHelper.clamp(currX, -baseRadius, baseRadius);
                currZ = MathHelper.clamp(currZ, -baseRadius, baseRadius);

                buffer.vertex(matrix, prevX, prevY, prevZ).color(rgba);
                buffer.vertex(matrix, currX, currY, currZ).color(rgba);

                prevX = currX;
                prevZ = currZ;
                prevY = currY;
            }
        }

        RenderLayers.lines().draw(buffer.end());

        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(true);
        GlStateManager._disableBlend();
        GlStateManager._enableCull();

        matrices.pop();
    }

    private void renderHelix(Render3DEvent event) {
        LivingEntity target = (LivingEntity) lastTarget;
        MatrixStack matrices = event.getMatrices();
        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();

        double tx = MathHelper.lerp(event.getTickDelta(), target.lastX, target.getX()) - camPos.x;
        double ty = MathHelper.lerp(event.getTickDelta(), target.lastY, target.getY()) - camPos.y;
        double tz = MathHelper.lerp(event.getTickDelta(), target.lastZ, target.getZ()) - camPos.z;

        float entityHeight = target.getHeight();
        float entityWidth = target.getWidth();

        rotation -= rotationSpeed.get().floatValue();
        if (rotation <= -360f) rotation += 360f;

        float baseRadius = entityWidth * espSize.get().floatValue() * 0.8f;
        float alphaMult = animation.getOutput().floatValue();

        matrices.push();
        matrices.translate(tx, ty, tz);

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(770, 1, 0, 1);
        GlStateManager._disableCull();
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        int particles = 40;
        float helixHeight = entityHeight * 1.2f;
        float helixTurns = 2.5f;

        for (int i = 0; i < particles; i++) {
            float progress = i / (float) particles;
            float angle = (float) ((double) progress * Math.PI * 2.0 * helixTurns) + (float) Math.toRadians(rotation);

            float x = (float) Math.cos(angle) * baseRadius;
            float z = (float) Math.sin(angle) * baseRadius;
            float y = progress * helixHeight;

            Color color = getColorForProgress(progress);
            int rgba = ColorUtil.getColor(color.getRed(), color.getGreen(), color.getBlue(), (int) (180 * alphaMult));

            matrices.push();
            matrices.translate(x, y, z);
            matrices.multiply(mc.gameRenderer.getCamera().getRotation());

            Matrix4f localMatrix = matrices.peek().getPositionMatrix();
            float size = 0.06f * (0.5f + progress * 0.5f);

            buffer.vertex(localMatrix, -size, size, 0).texture(0f, 1f).color(rgba);
            buffer.vertex(localMatrix, size, size, 0).texture(1f, 1f).color(rgba);
            buffer.vertex(localMatrix, size, -size, 0).texture(1f, 0f).color(rgba);
            buffer.vertex(localMatrix, -size, -size, 0).texture(0f, 0f).color(rgba);

            matrices.pop();
        }

        TARGET_ICON_LAYER.apply(GLOW_TEXTURE).draw(buffer.end());

        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(true);
        GlStateManager._disableBlend();
        GlStateManager._enableCull();

        matrices.pop();
    }

    private void renderShockwave(Render3DEvent event) {
        LivingEntity target = (LivingEntity) lastTarget;
        MatrixStack matrices = event.getMatrices();
        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();

        double tx = MathHelper.lerp(event.getTickDelta(), target.lastX, target.getX()) - camPos.x;
        double ty = MathHelper.lerp(event.getTickDelta(), target.lastY, target.getY()) - camPos.y;
        double tz = MathHelper.lerp(event.getTickDelta(), target.lastZ, target.getZ()) - camPos.z;

        float entityHeight = target.getHeight();
        long time = System.currentTimeMillis();
        float cycleTime = 2000f; // 2 second cycle
        float progress = (time % (long) cycleTime) / cycleTime;

        float baseRadius = target.getWidth() * espSize.get().floatValue();
        float maxRadius = baseRadius * 3.0f;
        float currentRadius = maxRadius * progress;
        float alphaMult = animation.getOutput().floatValue() * (1.0f - progress);

        matrices.push();
        matrices.translate(tx, ty + entityHeight * 0.5, tz);

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(770, 1, 0, 1);
        GlStateManager._disableCull();
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        int segments = 60;
        Color color = getColorForProgress(progress);
        int rgba = ColorUtil.getColor(color.getRed(), color.getGreen(), color.getBlue(), (int) (150 * alphaMult));

        // Draw expanding ring
        for (int i = 0; i < segments; i++) {
            float p1 = i / (float) segments;
            float p2 = (i + 1) / (float) segments;
            double a1 = (Math.PI * 2.0) * p1;
            double a2 = (Math.PI * 2.0) * p2;

            float outerR = currentRadius + 0.1f;
            float innerR = currentRadius - 0.1f;

            float x1Outer = (float) (Math.cos(a1) * outerR);
            float z1Outer = (float) (Math.sin(a1) * outerR);
            float x2Outer = (float) (Math.cos(a2) * outerR);
            float z2Outer = (float) (Math.sin(a2) * outerR);

            float x1Inner = (float) (Math.cos(a1) * innerR);
            float z1Inner = (float) (Math.sin(a1) * innerR);
            float x2Inner = (float) (Math.cos(a2) * innerR);
            float z2Inner = (float) (Math.sin(a2) * innerR);

            buffer.vertex(matrix, x1Outer, 0, z1Outer).color(rgba);
            buffer.vertex(matrix, x1Inner, 0, z1Inner).color(rgba);
            buffer.vertex(matrix, x2Inner, 0, z2Inner).color(rgba);
            buffer.vertex(matrix, x2Outer, 0, z2Outer).color(rgba);
        }

        RenderLayers.debugQuads().draw(buffer.end());

        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(true);
        GlStateManager._disableBlend();
        GlStateManager._enableCull();

        matrices.pop();
    }

    private void renderBlackHole(Render3DEvent event) {
        LivingEntity target = (LivingEntity) lastTarget;
        MatrixStack matrices = event.getMatrices();
        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();

        double tx = MathHelper.lerp(event.getTickDelta(), target.lastX, target.getX()) - camPos.x;
        double ty = MathHelper.lerp(event.getTickDelta(), target.lastY, target.getY()) - camPos.y;
        double tz = MathHelper.lerp(event.getTickDelta(), target.lastZ, target.getZ()) - camPos.z;

        float entityHeight = target.getHeight();
        float entityWidth = target.getWidth();

        rotation -= rotationSpeed.get().floatValue() * 1.5f;
        if (rotation <= -360f) rotation += 360f;

        float baseRadius = entityWidth * espSize.get().floatValue() * 1.2f;
        float alphaMult = animation.getOutput().floatValue();

        matrices.push();
        matrices.translate(tx, ty + entityHeight * 0.5, tz);

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(770, 1, 0, 1);
        GlStateManager._disableCull();
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        int particles = 80;
        long time = System.currentTimeMillis();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Draw swirling particles around center
        for (int i = 0; i < particles; i++) {
            float progress = i / (float) particles;
            float angle = (float) ((double) progress * Math.PI * 4.0) + (float) Math.toRadians(rotation + progress * 360);
            
            // Spiral effect - particles get closer to center
            float spiralRadius = baseRadius * (1.0f - progress * 0.7f);
            float wobble = (float) Math.sin(time * 0.005 + i * 0.5) * 0.1f;
            
            float x = (float) Math.cos(angle) * (spiralRadius + wobble);
            float z = (float) Math.sin(angle) * (spiralRadius + wobble);
            float y = (float) Math.sin(time * 0.003 + i * 0.3) * entityHeight * 0.3f;

            Color color = getColorForProgress(progress);
            int rgba = ColorUtil.getColor(color.getRed(), color.getGreen(), color.getBlue(), (int) (180 * alphaMult));

            matrices.push();
            matrices.translate(x, y, z);
            matrices.multiply(mc.gameRenderer.getCamera().getRotation());

            Matrix4f localMatrix = matrices.peek().getPositionMatrix();
            float size = 0.04f + progress * 0.04f;

            buffer.vertex(localMatrix, -size, size, 0).texture(0f, 1f).color(rgba);
            buffer.vertex(localMatrix, size, size, 0).texture(1f, 1f).color(rgba);
            buffer.vertex(localMatrix, size, -size, 0).texture(1f, 0f).color(rgba);
            buffer.vertex(localMatrix, -size, -size, 0).texture(0f, 0f).color(rgba);

            matrices.pop();
        }

        TARGET_ICON_LAYER.apply(GLOW_TEXTURE).draw(buffer.end());

        // Draw dark center
        matrices.push();
        matrices.multiply(mc.gameRenderer.getCamera().getRotation());
        Matrix4f centerMatrix = matrices.peek().getPositionMatrix();
        
        BufferBuilder centerBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        float centerSize = baseRadius * 0.3f;
        int centerColor = ColorUtil.getColor(20, 0, 40, (int) (200 * alphaMult));
        
        centerBuffer.vertex(centerMatrix, -centerSize, centerSize, 0).color(centerColor);
        centerBuffer.vertex(centerMatrix, centerSize, centerSize, 0).color(centerColor);
        centerBuffer.vertex(centerMatrix, centerSize, -centerSize, 0).color(centerColor);
        centerBuffer.vertex(centerMatrix, -centerSize, -centerSize, 0).color(centerColor);
        
        RenderLayers.debugQuads().draw(centerBuffer.end());
        matrices.pop();

        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(true);
        GlStateManager._disableBlend();
        GlStateManager._enableCull();

        matrices.pop();
    }

    private void drawTextureQuad(MatrixStack matrices, float size, Identifier texture, float alpha) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        float halfSize = size;
        Color c1 = getColorForProgress(0);
        Color c2 = getColorForProgress(0.25f);
        Color c3 = getColorForProgress(0.5f);
        Color c4 = getColorForProgress(0.75f);

        if (espColourMode.is(ColorMode.Rainbow)) {
            c1 = c2 = c3 = c4 = Color.WHITE;
        }

        int a = (int) (alpha * 255);
        buffer.vertex(matrix, -halfSize, -halfSize, 0).texture(0, 0).color(c1.getRed(), c1.getGreen(), c1.getBlue(), a);
        buffer.vertex(matrix, -halfSize, halfSize, 0).texture(0, 1).color(c2.getRed(), c2.getGreen(), c2.getBlue(), a);
        buffer.vertex(matrix, halfSize, halfSize, 0).texture(1, 1).color(c3.getRed(), c3.getGreen(), c3.getBlue(), a);
        buffer.vertex(matrix, halfSize, -halfSize, 0).texture(1, 0).color(c4.getRed(), c4.getGreen(), c4.getBlue(), a);

        TARGET_ICON_LAYER.apply(texture).draw(buffer.end());
    }

    private Color getColorForProgress(float progress) {
        switch (espColourMode.get()) {
            case Rainbow -> {
                float hue = (progress + System.currentTimeMillis() / 5000f) % 1f;
                return Color.getHSBColor(hue, 0.8f, 1f);
            }
            case Wave -> {
                float wave = (float) Math.sin((progress * Math.PI * 2) + (System.currentTimeMillis() / 1000f * waveSpeed.get()));
                wave = (wave + 1f) / 2f;
                return ColorUtil.interpolateColor(espColor1.get(), espColor2.get(), wave);
            }
            case Single -> {
                return ColorUtil.interpolateColor(espColor1.get(), espColor2.get(), progress);
            }
            case Client -> {
                Color c1 = ClickGui.color(0);
                Color c2 = ClickGui.color2(0);
                return ColorUtil.interpolateColor(c1, c2, progress);
            }
        }
        return Color.WHITE;
    }

    private Color getJelloColorForProgress(float progress) {
        return switch (espColourMode.get()) {
            case Rainbow -> getColorForProgress(progress);
            case Wave -> getColorForProgress(progress);
            case Single -> {
                float t = progress <= 0.5f ? progress * 2f : (1f - progress) * 2f;
                yield ColorUtil.interpolateColor(espColor1.get(), espColor2.get(), t);
            }
            case Client -> {
                float t = progress <= 0.5f ? progress * 2f : (1f - progress) * 2f;
                Color c1 = ClickGui.color(0);
                Color c2 = ClickGui.color2(0);
                yield ColorUtil.interpolateColor(c1, c2, t);
            }
        };
    }

    private static double absSinAnimation(double input) {
        return Math.abs(1.0 + Math.sin(input)) / 2.0;
    }
}
