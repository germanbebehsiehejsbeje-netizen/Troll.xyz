package dev.mzc.client.mixin.client;

import dev.mzc.client.module.impl.render.TotemPopChams;
import dev.mzc.client.Sakura;
import dev.mzc.client.module.impl.render.totempopchams.TotemPopChamsHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class TotemPopChamsPlayerMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    public void tick(CallbackInfo ci) {
        TotemPopChams module = Sakura.MODULES.getModule(TotemPopChams.class);
        // Always tick captured players to update their age, rendering is controlled by module state
        if (module == null) return;
        
        TotemPopChamsHandler.getPositions().forEach(p -> {
            p.tickAge(module.lifeTime.get().intValue());
        });
    }
}
