package dev.mzc.client.module.impl.render.totempopchams;

import dev.mzc.client.module.impl.render.TotemPopChams;
import net.minecraft.util.math.MathHelper;

public class ChamConfigState {
    public boolean renderFillModel;
    public boolean renderWireframe;
    public boolean disperse;
    public boolean fadeOut;
    public double disperseSpeed;
    public double disperseMaxDistance;
    public double lifeTime;
    public double wireframeThickness;
    public int filledColor;
    public int wireframeColor;
    public float displacementAmount;
    public float alpha;

    public void updateConfigRenderState(final CapturedPlayer player, final float delta, TotemPopChams module) {
        this.renderFillModel = module.filledModelEnabled.get();
        this.renderWireframe = module.wireframeEnabled.get();
        this.disperse = module.disperse.get();
        this.disperseSpeed = module.disperseSpeed.get();
        this.disperseMaxDistance = module.disperseMaxDistance.get();
        this.filledColor = module.filledColor.get().getRGB();
        this.wireframeColor = module.wireframeColor.get().getRGB();
        this.fadeOut = module.fadeOut.get();
        this.lifeTime = module.lifeTime.get();
        this.wireframeThickness = module.wireframeThickness.get();
        this.displacementAmount = this.displacement(player, alpha);
        this.alpha = this.alpha(player, delta);
    }

    private float displacement(final CapturedPlayer player, final float delta) {
        final float s = Math.max(0, player.age - 1 + delta);
        final float ss = (float) MathHelper.clamp(this.disperseSpeed, 1f, 10f);
        final float sm = (float) Math.pow(ss / 10f, 2) * 2f;
        return this.disperse ? (float) Math.min(s * sm, this.disperseMaxDistance) : 0f;
    }

    private float alpha(final CapturedPlayer player, final float delta) {
        if (!this.fadeOut) {
            return 1f;
        }

        final float s = Math.max(0, player.age - 1 + delta);
        final float f = (float) (this.lifeTime * 0.8f);
        final float l = s >= f ? (float) (1f - ((s - f) / (this.lifeTime - f))) : 1f;
        final float d = this.disperse ? (float) (1f - (this.displacementAmount / this.disperseMaxDistance)) : 1f;
        return MathHelper.clamp(l * d, 0f, 1f);
    }
}
