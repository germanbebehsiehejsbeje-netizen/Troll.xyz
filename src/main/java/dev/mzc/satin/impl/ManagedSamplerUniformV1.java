package dev.mzc.satin.impl;

import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.AbstractTexture;

public final class ManagedSamplerUniformV1 extends ManagedSamplerUniformBase {
    public ManagedSamplerUniformV1(String name) {
        super(name);
    }

    @Override
    public void set(AbstractTexture texture) {
        this.set((Object) texture);
    }

    @Override
    public void set(Framebuffer textureFbo) {
        this.set((Object) textureFbo);
    }

    @Override
    public void set(int textureName) {
        this.set((Object) textureName);
    }

    @Override
    protected void set(Object value) {
        SamplerAccess[] targets = this.targets;
        if (targets.length > 0 && this.cachedValue != value) {
            // 1.21.11 migrated framebuffer attachments to GpuTexture and removed the old
            // direct sampler upload path from ShaderProgram. Keep cached value so manager
            // lifecycle remains stable until full satin sampler binding is migrated.
            this.cachedValue = value;
        }
    }
}
