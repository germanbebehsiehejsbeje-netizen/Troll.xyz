package dev.mzc.client.shaders;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.mzc.client.utils.animations.AnimationUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.MappableRingBuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class MainMenuShader {
    private static MainMenuShader sharedInstance;
    private static final int UNIFORMS_SIZE = new Std140SizeCalculator().putVec4().putVec4().get();

    private final EnumMap<MainMenuShaderType, RenderPipeline> pipelines = new EnumMap<>(MainMenuShaderType.class);
    private MainMenuShaderType currentShaderType;
    private MappableRingBuffer uniforms;
    private float timeSeconds;
    private float transitionValue = 1.0f;
    private float mouseOffsetX;
    private float speed = 1.0f;
    private long lastTimeMillis = -1;

    public static MainMenuShader getSharedInstance() {
        if (sharedInstance == null) {
            sharedInstance = new MainMenuShader(MainMenuShaderType.BLACK);
        }
        return sharedInstance;
    }

    public static void cleanupSharedInstance() {
        if (sharedInstance != null) {
            sharedInstance.cleanup();
            sharedInstance = null;
        }
    }

    public MainMenuShader(MainMenuShaderType shaderType) {
        this.currentShaderType = shaderType;
    }

    public void render(int width, int height) {
        render(width, height, this.transitionValue);
    }

    public void render(int width, int height, float transition) {
        RenderPipeline pipeline = this.getPipeline(this.currentShaderType);
        if (pipeline == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer framebuffer = client.getFramebuffer();
        float scaleFactor = (float) client.getWindow().getScaleFactor();
        float pxWidth = width * scaleFactor;
        float pxHeight = height * scaleFactor;

        long now = System.currentTimeMillis();
        if (this.lastTimeMillis != -1) {
            float delta = (now - this.lastTimeMillis) / 1000.0f;
            this.timeSeconds += delta * this.speed;
        }
        this.lastTimeMillis = now;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView view = encoder.mapBuffer(this.uniforms.getBlocking(), false, true)) {
            Std140Builder builder = Std140Builder.intoBuffer(view.data());
            builder.putVec2(pxWidth, pxHeight);
            builder.putFloat(this.timeSeconds);
            builder.putFloat(transition);
            builder.putVec2(this.mouseOffsetX, 0.5f);
            builder.putVec2(pxWidth, pxHeight);
        }

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "MZC MainMenu",
                framebuffer.getColorAttachmentView(),
                OptionalInt.empty(),
                framebuffer.useDepthAttachment ? framebuffer.getDepthAttachmentView() : null,
                OptionalDouble.empty()
        )) {
            renderPass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("MenuUniforms", this.uniforms.getBlocking());
            renderPass.draw(0, 3);
        }
        this.uniforms.rotate();
    }

    public void setTransition(float transition) {
        this.transitionValue = transition;
    }

    public float getTransition() {
        return this.transitionValue;
    }

    public void setMouseOffset(float x) {
        this.mouseOffsetX = x;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public float getSpeed() {
        return this.speed;
    }

    public void switchShaderType(MainMenuShaderType newType) {
        if (newType == null || this.currentShaderType == newType) {
            return;
        }
        this.currentShaderType = newType;
        // 不重置 timeSeconds，保持连续时间避免动画在 t=0 附近静止
    }

    public MainMenuShaderType getCurrentShaderType() {
        return currentShaderType;
    }

    public void nextShader() {
        switchShaderType(currentShaderType.next());
    }

    public void previousShader() {
        switchShaderType(currentShaderType.previous());
    }

    public void cleanup() {
        if (this.uniforms != null) {
            this.uniforms.close();
            this.uniforms = null;
        }
    }

    private RenderPipeline getPipeline(MainMenuShaderType type) {
        if (this.uniforms == null) {
            this.uniforms = new MappableRingBuffer(() -> "MZC MenuUniforms", GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM, UNIFORMS_SIZE);
        }

        return this.pipelines.computeIfAbsent(type, t -> RenderPipeline.builder(RenderPipelines.POST_EFFECT_PROCESSOR_SNIPPET)
                .withLocation(Identifier.of("sakura", "pipeline/menu/" + t.name().toLowerCase()))
                .withVertexShader(Identifier.of("sakura", "core/screen_triangle"))
                .withFragmentShader(t.fragmentShader)
                .withUniform("MenuUniforms", UniformType.UNIFORM_BUFFER)
                .withCull(false)
                .build());
    }

    public enum MainMenuShaderType {
        SAKURA(Identifier.of("sakura", "core/menu_sakura"), "樱花效果"),
        RAINBOW(Identifier.of("sakura", "core/menu_rainbow"), "彩虹"),
        GALAXY(Identifier.of("sakura", "core/menu_galaxy"), "星空"),
        BLACK_HOLE(Identifier.of("sakura", "core/menu_black_hole"), "黑洞"),
        BLACK_HOLE_2(Identifier.of("sakura", "core/menu_black_hole_2"), "黑洞2"),
        WINDOWS(Identifier.of("sakura", "core/menu_windows"), "Windows"),
        HUD_RINGS(Identifier.of("sakura", "core/menu_hud_rings"), "HUD环"),
        BLACK(Identifier.of("sakura", "core/menu_black"), "黑色波纹");

        private final Identifier fragmentShader;
        private final String displayName;

        MainMenuShaderType(Identifier fragmentShader, String displayName) {
            this.fragmentShader = fragmentShader;
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public MainMenuShaderType next() {
            MainMenuShaderType[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }

        public MainMenuShaderType previous() {
            MainMenuShaderType[] values = values();
            return values[(this.ordinal() - 1 + values.length) % values.length];
        }

        public static MainMenuShaderType fromName(String name) {
            for (MainMenuShaderType type : values()) {
                if (type.name().equalsIgnoreCase(name)) {
                    return type;
                }
            }
            return null;
        }
    }
}
