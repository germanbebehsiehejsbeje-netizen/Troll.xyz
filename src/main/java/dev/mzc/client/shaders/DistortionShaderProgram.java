package dev.mzc.client.shaders;

import dev.mzc.satin.api.ManagedCoreShader;
import dev.mzc.satin.api.ShaderEffectManager;
import dev.mzc.satin.api.uniform.Uniform1f;
import dev.mzc.satin.api.uniform.Uniform2f;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

import static dev.mzc.client.Sakura.mc;

public class DistortionShaderProgram {
    private final Uniform2f inputResolution;
    private final Uniform1f time;
    
    private float currentTime = 0.0f;
    
    public static final ManagedCoreShader DISTORTION = ShaderEffectManager.getInstance()
            .manageCoreShader(Identifier.of("sakura", "core/clickgui_distortion"), VertexFormats.POSITION);
    
    public DistortionShaderProgram() {
        this.inputResolution = DISTORTION.findUniform2f("InputResolution");
        this.time = DISTORTION.findUniform1f("Time");
    }
    
    public void setParameters(float width, float height) {
        float factor = (float) mc.getWindow().getScaleFactor();
        inputResolution.set(width * factor, height * factor);
        time.set(currentTime);
    }
    
    public void use() {
        var buffer = mc.getFramebuffer();
        inputResolution.set((float) buffer.textureWidth, (float) buffer.textureHeight);
    }
    
    public void updateTime(float delta) {
        currentTime += delta;
    }
    
    public float getTime() {
        return currentTime;
    }
}
