package dev.mzc.satin.impl;

import com.mojang.logging.LogUtils;
import dev.mzc.satin.api.uniform.SamplerUniform;
import net.minecraft.client.gl.ShaderProgram;

public abstract class ManagedSamplerUniformBase extends ManagedUniformBase implements SamplerUniform {
    protected SamplerAccess[] targets = new SamplerAccess[0];
    protected int[] locations = new int[0];
    protected Object cachedValue;

    public ManagedSamplerUniformBase(String name) {
        super(name);
    }

    @Override
    public boolean findUniformTarget(ShaderProgram shader) {
        LogUtils.getLogger().debug("Finding sampler {} in shader", this.name);
        return findUniformTarget1(((SamplerAccess) shader));
    }

    private boolean findUniformTarget1(SamplerAccess access) {
        if (access.sakura$hasSampler(this.name)) {
            this.targets = new SamplerAccess[]{access};
            this.locations = new int[]{access.sakura$getSamplerLocation(this.name)};
            this.syncCurrentValues();
            return true;
        }
        return false;
    }

    private void syncCurrentValues() {
        Object value = this.cachedValue;
        if (value != null) { // after the first upload
            this.cachedValue = null;
            this.set(value);
        }
    }

    protected abstract void set(Object value);

}
