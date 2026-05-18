package dev.mzc.client.module.impl.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.entity.AttackEvent;
import dev.mzc.client.events.render.Render3DEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

public class Particles extends Module {

    public enum ParticleType {
        Star, Heart, Snowflake, Lightning, Arrow, Crown, Dollar, Cube, Rhombus, Triangle, Cross, PearlMark
    }

    private final EnumValue<ParticleType> particleType = new EnumValue<>("Type", ParticleType.Star);
    private final NumberValue<Integer> particleCount = new NumberValue<>("Count", 50, 10, 200, 5);
    private final NumberValue<Double> spawnRadius = new NumberValue<>("SpawnRadius", 10.0, 2.0, 30.0, 1.0);
    private final NumberValue<Double> fallSpeed = new NumberValue<>("FallSpeed", 0.05, 0.01, 0.2, 0.01);
    private final NumberValue<Double> particleSize = new NumberValue<>("Size", 0.3, 0.1, 1.0, 0.05);
    private final BoolValue spawnOnMove = new BoolValue("SpawnOnMove", true);
    private final BoolValue spawnOnAttack = new BoolValue("SpawnOnAttack", true);
    private final NumberValue<Integer> attackParticleCount = new NumberValue<>("AttackCount", 20, 5, 50, 1, spawnOnAttack::get);
    private final NumberValue<Double> attackSpawnRadius = new NumberValue<>("AttackRadius", 3.0, 1.0, 10.0, 0.5, spawnOnAttack::get);

    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();

    private static final Map<ParticleType, Identifier> TEXTURES = new java.util.HashMap<>();

    static {
        TEXTURES.put(ParticleType.Star, Identifier.of("sakura", "world/star.png"));
        TEXTURES.put(ParticleType.Heart, Identifier.of("sakura", "world/heart.png"));
        TEXTURES.put(ParticleType.Snowflake, Identifier.of("sakura", "world/snowflake.png"));
        TEXTURES.put(ParticleType.Lightning, Identifier.of("sakura", "world/lightning.png"));
        TEXTURES.put(ParticleType.Arrow, Identifier.of("sakura", "world/arrow.png"));
        TEXTURES.put(ParticleType.Crown, Identifier.of("sakura", "world/crown.png"));
        TEXTURES.put(ParticleType.Dollar, Identifier.of("sakura", "world/dollar.png"));
        TEXTURES.put(ParticleType.Cube, Identifier.of("sakura", "world/cubeblast1.png"));
        TEXTURES.put(ParticleType.Rhombus, Identifier.of("sakura", "world/rhombus.png"));
        TEXTURES.put(ParticleType.Triangle, Identifier.of("sakura", "world/triangle.png"));
        TEXTURES.put(ParticleType.Cross, Identifier.of("sakura", "world/cross.png"));
        TEXTURES.put(ParticleType.PearlMark, Identifier.of("sakura", "world/pearl-mark.png"));
    }

    private final RenderPipeline PARTICLE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation("pipeline/sakura_particle")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build()
    );

    private final Function<Identifier, RenderLayer> PARTICLE_LAYER = Util.memoize(texture -> RenderLayer.of(
            "sakura_particle",
            RenderSetup.builder(PARTICLE_PIPELINE)
                    .texture("Sampler0", texture)
                    .translucent()
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .outputTarget(OutputTarget.MAIN_TARGET)
                    .build()
    ));

    public Particles() {
        super("Particles", Category.Render);
        this.setType(ModuleType.All);
    }

    @Override
    protected void onEnable() {
        particles.clear();
    }

    @Override
    protected void onDisable() {
        particles.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (nullCheck()) return;

        // Spawn ambient particles
        if (particles.size() < particleCount.get()) {
            spawnAmbientParticles();
        }

        // Update particles
        updateParticles();
    }

    @EventHandler
    private void onAttack(AttackEvent event) {
        if (nullCheck() || !spawnOnAttack.get()) return;
        
        // Spawn particles at target position
        Vec3d targetPos = event.getTargetEntity().getEyePos();
        spawnAttackParticles(targetPos);
    }

    private void spawnAmbientParticles() {
        int toSpawn = Math.min(5, particleCount.get() - particles.size());
        Vec3d playerPos = mc.player.getEyePos();
        double radius = spawnRadius.get();

        for (int i = 0; i < toSpawn; i++) {
            double x = playerPos.x + (random.nextDouble() - 0.5) * radius * 2;
            double y = playerPos.y + random.nextDouble() * radius;
            double z = playerPos.z + (random.nextDouble() - 0.5) * radius * 2;

            particles.add(new Particle(x, y, z, random.nextDouble() * 0.02 + 0.01));
        }
    }

    private void spawnAttackParticles(Vec3d targetPos) {
        int count = attackParticleCount.get();
        double radius = attackSpawnRadius.get();

        for (int i = 0; i < count; i++) {
            double x = targetPos.x + (random.nextDouble() - 0.5) * radius * 2;
            double y = targetPos.y + random.nextDouble() * radius;
            double z = targetPos.z + (random.nextDouble() - 0.5) * radius * 2;

            particles.add(new Particle(x, y, z, random.nextDouble() * 0.05 + 0.02));
        }
    }

    private void updateParticles() {
        Vec3d playerPos = mc.player.getEyePos();
        double maxDistance = spawnRadius.get() * 2;

        particles.removeIf(particle -> {
            // Update position
            particle.y -= fallSpeed.get();
            particle.rotation += particle.rotationSpeed;
            particle.alpha -= 0.002;

            // Remove if out of bounds or faded
            if (particle.alpha <= 0 || particle.y < playerPos.y - 10) {
                return true;
            }

            // Remove if too far from player
            double distance = Math.sqrt(
                Math.pow(particle.x - playerPos.x, 2) + 
                Math.pow(particle.z - playerPos.z, 2)
            );
            return distance > maxDistance;
        });

        // Spawn particles when moving
        if (spawnOnMove.get() && mc.player.getVelocity().horizontalLength() > 0.1) {
            if (random.nextInt(3) == 0 && particles.size() < particleCount.get()) {
                Vec3d pos = mc.player.getEyePos();
                particles.add(new Particle(
                    pos.x + (random.nextDouble() - 0.5) * 2,
                    pos.y + random.nextDouble() * 2,
                    pos.z + (random.nextDouble() - 0.5) * 2,
                    random.nextDouble() * 0.03 + 0.01
                ));
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (nullCheck() || particles.isEmpty()) return;

        MatrixStack matrices = event.getMatrices();
        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
        Identifier texture = TEXTURES.getOrDefault(particleType.get(), TEXTURES.get(ParticleType.Star));

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(770, 1, 0, 1);
        GlStateManager._disableCull();
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        for (Particle particle : particles) {
            float x = (float) (particle.x - camPos.x);
            float y = (float) (particle.y - camPos.y);
            float z = (float) (particle.z - camPos.z);

            float size = (float) particleSize.get().floatValue();
            int alpha = (int) (particle.alpha * 255);

            matrices.push();
            matrices.translate(x, y, z);
            matrices.multiply(mc.gameRenderer.getCamera().getRotation());
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotation(particle.rotation));

            Matrix4f localMatrix = matrices.peek().getPositionMatrix();

            buffer.vertex(localMatrix, -size, size, 0).texture(0f, 1f).color(255, 255, 255, alpha);
            buffer.vertex(localMatrix, size, size, 0).texture(1f, 1f).color(255, 255, 255, alpha);
            buffer.vertex(localMatrix, size, -size, 0).texture(1f, 0f).color(255, 255, 255, alpha);
            buffer.vertex(localMatrix, -size, -size, 0).texture(0f, 0f).color(255, 255, 255, alpha);

            matrices.pop();
        }

        PARTICLE_LAYER.apply(texture).draw(buffer.end());

        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(true);
        GlStateManager._disableBlend();
        GlStateManager._enableCull();
    }

    private static class Particle {
        double x, y, z;
        double velocityY;
        float rotation;
        float rotationSpeed;
        float alpha;

        Particle(double x, double y, double z, double velocityY) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.velocityY = velocityY;
            this.rotation = (float) (Math.random() * Math.PI * 2);
            this.rotationSpeed = (float) ((Math.random() - 0.5) * 0.1);
            this.alpha = 0.8f + (float) Math.random() * 0.2f;
        }
    }
}
