package dev.mzc.client.mixin.accessor;

import net.minecraft.client.particle.BillboardParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BillboardParticle.class)
public interface IBillboardParticle {
    @Accessor("red")
    void setRed(float red);

    @Accessor("green")
    void setGreen(float green);

    @Accessor("blue")
    void setBlue(float blue);
}
