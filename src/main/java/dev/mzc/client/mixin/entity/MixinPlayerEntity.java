package dev.mzc.client.mixin.entity;

import dev.mzc.client.Sakura;
import dev.mzc.client.events.player.SprintEvent;
import dev.mzc.client.module.impl.movement.AutoSprint;
import dev.mzc.client.module.impl.movement.MoveFix;
import dev.mzc.client.module.impl.movement.NoSlow;
import dev.mzc.client.utils.player.MovementUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.mzc.client.events.EventType;
import dev.mzc.client.events.player.TravelEvent;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static dev.mzc.client.Sakura.mc;

@Mixin(PlayerEntity.class)
public abstract class MixinPlayerEntity extends LivingEntity {

    protected MixinPlayerEntity(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void onTravelPre(Vec3d movementInput, CallbackInfo ci) {
        if ((Object) this == mc.player) {
            TravelEvent event = new TravelEvent(EventType.PRE, movementInput);
            Sakura.EVENT_BUS.post(event);
            if (event.isCancelled()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "travel", at = @At("RETURN"))
    private void onTravelPost(Vec3d movementInput, CallbackInfo ci) {
        if ((Object) this == mc.player) {
            TravelEvent event = new TravelEvent(EventType.POST, movementInput);
            Sakura.EVENT_BUS.post(event);
        }
    }

    @Redirect(method = "knockbackTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;setSprinting(Z)V"))
    private void preventSprintReset(PlayerEntity instance, boolean sprinting) {
        if (Sakura.MODULES.getModule(AutoSprint.class).isEnabled() && Sakura.MODULES.getModule(AutoSprint.class).isNoSlowAttack()) {
            return;
        }
        instance.setSprinting(sprinting);
    }

    @Redirect(method = "knockbackTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V"))
    private void preventAttackSlowdown(PlayerEntity instance, Vec3d velocity) {
        if (Sakura.MODULES.getModule(AutoSprint.class).isEnabled() && Sakura.MODULES.getModule(AutoSprint.class).isNoSlowAttack()) {
            return;
        }
        instance.setVelocity(velocity);
    }
}
