package dev.mzc.client.module.impl.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.misc.WorldLoadEvent;
import dev.mzc.client.events.render.Render3DEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.render.Render3DUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static dev.mzc.client.Sakura.mc;

public class DashTrail extends Module {

    private final NumberValue<Integer> maxPoints = new NumberValue<>("Max Points", 100, 10, 500, 10);
    private final NumberValue<Integer> lifeTime = new NumberValue<>("Life Time", 2000, 500, 10000, 100);
    private final NumberValue<Double> minDistance = new NumberValue<>("Min Distance", 0.05, 0.01, 0.5, 0.01);
    private final NumberValue<Double> size = new NumberValue<>("Size", 0.5, 0.1, 2.0, 0.1);
    private final NumberValue<Integer> alpha = new NumberValue<>("Alpha", 200, 0, 255, 1);
    private final BoolValue fade = new BoolValue("Fade", true);
    private final BoolValue onlyThirdPerson = new BoolValue("Only Third Person", false);
    private final BoolValue randomRotation = new BoolValue("Random Rotation", true);
    private final BoolValue removeBlackBg = new BoolValue("Remove Black Background", true);

    private final List<DashPoint> points = new ArrayList<>();
    private final List<TrailTexture> textures = new ArrayList<>();
    private int currentTextureIndex = 0;
    private boolean texturesLoaded = false;

    public DashTrail() {
        super("DashTrail", Category.Render);
        this.setType(ModuleType.All);
    }

    @Override
    protected void onEnable() {
        points.clear();
        loadTextures();
    }

    @Override
    protected void onDisable() {
        points.clear();
        unloadTextures();
    }

    private void loadTextures() {
        if (texturesLoaded) return;
        
        try {
            // Load all PNG files from dashtrails folder
            for (int i = 1; i <= 21; i++) {
                final int textureIndex = i; // Make final for lambda
                String texturePath = "textures/dashtrails/dashcubic" + i + ".png";
                Identifier identifier = Identifier.of("sakura", texturePath);
                
                try {
                    // Try to load the texture using Minecraft's resource system
                    var resource = mc.getResourceManager().getResource(identifier);
                    
                    if (resource.isPresent()) {
                        InputStream stream = resource.get().getInputStream();
                        NativeImage image = NativeImage.read(stream);
                        stream.close();
                        
                        // Remove black background if enabled
                        if (removeBlackBg.get()) {
                            removeBlackBackground(image);
                        }
                        
                        NativeImageBackedTexture texture = new NativeImageBackedTexture(
                                () -> "sakura_dashtrail_" + textureIndex, image);
                        Identifier texId = Identifier.of("sakura", "dashtrail_" + textureIndex);
                        mc.getTextureManager().registerTexture(texId, texture);
                        
                        textures.add(new TrailTexture(texId, image.getWidth(), image.getHeight()));
                    }
                } catch (Exception e) {
                    // Skip if texture doesn't exist or fails to load
                }
            }
            
            texturesLoaded = !textures.isEmpty();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void removeBlackBackground(NativeImage image) {
        try {
            // Use reflection to access private getColor method
            Method getColorMethod = NativeImage.class.getDeclaredMethod("getColor", int.class, int.class);
            getColorMethod.setAccessible(true);
            
            Method setColorMethod = NativeImage.class.getDeclaredMethod("setColor", int.class, int.class, int.class);
            setColorMethod.setAccessible(true);
            
            int width = image.getWidth();
            int height = image.getHeight();
            
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int color = (int) getColorMethod.invoke(image, x, y);
                    
                    // Extract RGB components
                    int red = (color >> 16) & 0xFF;
                    int green = (color >> 8) & 0xFF;
                    int blue = color & 0xFF;
                    
                    // Check if pixel is black or very dark
                    if (red < 30 && green < 30 && blue < 30) {
                        // Make it fully transparent by setting alpha to 0
                        setColorMethod.invoke(image, x, y, 0);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void unloadTextures() {
        for (TrailTexture tex : textures) {
            mc.getTextureManager().destroyTexture(tex.identifier);
        }
        textures.clear();
        texturesLoaded = false;
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        Vec3d pos = mc.player.getEntityPos();

        if (!points.isEmpty()) {
            Vec3d last = points.get(points.size() - 1).pos;
            if (pos.distanceTo(last) < minDistance.get()) {
                return;
            }
        }

        // Add new point with random texture
        if (!textures.isEmpty()) {
            TrailTexture tex = textures.get(currentTextureIndex % textures.size());
            currentTextureIndex++;
            
            float rotation = randomRotation.get() ? ThreadLocalRandom.current().nextFloat(0, 360) : 0;
            
            points.add(new DashPoint(pos, tex, rotation));
        }

        long now = System.currentTimeMillis();
        points.removeIf(p -> now - p.time > lifeTime.get());
        while (points.size() > maxPoints.get()) {
            points.remove(0);
        }
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (points.isEmpty() || !texturesLoaded) return;
        if (onlyThirdPerson.get() && mc.options.getPerspective().isFirstPerson()) return;

        long now = System.currentTimeMillis();
        Matrix4f matrix = event.getMatrices().peek().getPositionMatrix();
        Vec3d camPos = mc.getEntityRenderDispatcher().camera.getCameraPos();

        Render3DUtil.setup3D();
        GlStateManager._enableDepthTest();

        for (DashPoint point : points) {
            float progress = 1.0f - (float) (now - point.time) / lifeTime.get();
            progress = MathHelper.clamp(progress, 0, 1);
            
            int pointAlpha = fade.get() ? (int) (alpha.get() * progress) : alpha.get();
            if (pointAlpha <= 0) continue;

            renderTexture(event.getMatrices(), point, camPos, pointAlpha);
        }

        Render3DUtil.cleanup3D();
    }

    private void renderTexture(MatrixStack matrices, DashPoint point, Vec3d camPos, int alpha) {
        Vec3d pos = point.pos;
        double x = pos.x - camPos.x;
        double y = pos.y - camPos.y;
        double z = pos.z - camPos.z;
        
        float size = this.size.get().floatValue();
        float halfSize = size / 2.0f;
        
        // Apply rotation
        float cos = MathHelper.cos(point.rotation * 0.017453292F);
        float sin = MathHelper.sin(point.rotation * 0.017453292F);
        
        // Calculate vertices with rotation
        float[] dx = {-halfSize, halfSize, halfSize, -halfSize};
        float[] dz = {-halfSize, -halfSize, halfSize, halfSize};
        
        float[] rotatedX = new float[4];
        float[] rotatedZ = new float[4];
        
        for (int i = 0; i < 4; i++) {
            rotatedX[i] = dx[i] * cos - dz[i] * sin;
            rotatedZ[i] = dx[i] * sin + dz[i] * cos;
        }

        // Use POSITION_COLOR format without textures for now
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        // Create a color with alpha
        int color = (alpha << 24) | 0xFFFFFF;
        
        buffer.vertex(matrices.peek().getPositionMatrix(), 
                (float) x + rotatedX[0], (float) y, (float) z + rotatedZ[0])
                .color(color);
        buffer.vertex(matrices.peek().getPositionMatrix(), 
                (float) x + rotatedX[1], (float) y, (float) z + rotatedZ[1])
                .color(color);
        buffer.vertex(matrices.peek().getPositionMatrix(), 
                (float) x + rotatedX[2], (float) y, (float) z + rotatedZ[2])
                .color(color);
        buffer.vertex(matrices.peek().getPositionMatrix(), 
                (float) x + rotatedX[3], (float) y, (float) z + rotatedZ[3])
                .color(color);

        RenderLayers.debugQuads().draw(buffer.end());
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        points.clear();
    }

    private static class DashPoint {
        private final Vec3d pos;
        private final TrailTexture texture;
        private final float rotation;
        private final long time;

        public DashPoint(Vec3d pos, TrailTexture texture, float rotation) {
            this.pos = pos;
            this.texture = texture;
            this.rotation = rotation;
            this.time = System.currentTimeMillis();
        }
    }

    private static class TrailTexture {
        private final Identifier identifier;
        private final int width;
        private final int height;

        public TrailTexture(Identifier identifier, int width, int height) {
            this.identifier = identifier;
            this.width = width;
            this.height = height;
        }
    }
}
