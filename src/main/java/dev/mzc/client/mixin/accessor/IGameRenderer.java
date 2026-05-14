package dev.mzc.client.mixin.accessor;

import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface IGameRenderer {
    @Invoker("renderHand")
    void hookRenderHand(float tickProgress, boolean sleeping, Matrix4f positionMatrix);

    @Accessor("firstPersonRenderer")
    net.minecraft.client.render.item.HeldItemRenderer getHeldItemRenderer();
}
