package dev.mzc.client.mixin.shader;

import dev.mzc.satin.impl.SamplerAccess;
import net.minecraft.client.gl.ShaderProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.lwjgl.opengl.GL32C;

@Mixin(ShaderProgram.class)
public abstract class MixinShaderProgram implements SamplerAccess {
    @Override
    public boolean sakura$hasSampler(String name) {
        return sakura$getSamplerLocation(name) >= 0;
    }

    @Override
    public int sakura$getSamplerLocation(String name) {
        return GL32C.glGetUniformLocation(((ShaderProgram) (Object) this).getGlRef(), name);
    }
}
