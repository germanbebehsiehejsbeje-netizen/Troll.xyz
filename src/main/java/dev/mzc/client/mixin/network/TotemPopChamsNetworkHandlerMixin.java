package dev.mzc.client.mixin.network;

import dev.mzc.client.Sakura;
import dev.mzc.client.events.packet.PacketEvent;
import dev.mzc.client.events.EventType;
import dev.mzc.client.module.impl.render.TotemPopChams;
import dev.mzc.client.module.impl.render.totempopchams.TotemPopChamsHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class TotemPopChamsNetworkHandlerMixin {

    @Inject(method = "onEntityStatus", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/s2c/play/EntityStatusS2CPacket;getEntity(Lnet/minecraft/world/World;)Lnet/minecraft/entity/Entity;"))
    public void handleStatus(EntityStatusS2CPacket packet, CallbackInfo ci) {
        TotemPopChams module = Sakura.MODULES.getModule(TotemPopChams.class);
        // Always capture totem pops, rendering is controlled by module state
        if (module == null) {
            Sakura.LOGGER.warn("[TotemPopChams] Module is null!");
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }
        Entity entity = packet.getEntity(client.world);
        if (entity instanceof PlayerEntity player) {
            if (!module.showOwnPops.get()) {
                if (player == client.player) {
                    return;
                }
            }
            if (packet.getStatus() == EntityStatuses.USE_TOTEM_OF_UNDYING) {
                Sakura.LOGGER.info("[TotemPopChams] Totem pop detected for: {}", player.getName().getString());
                TotemPopChamsHandler.handleTotem(player);
                Sakura.LOGGER.info("[TotemPopChams] Captured players count: {}", TotemPopChamsHandler.getPositions().size());
            }
        }
    }
}
