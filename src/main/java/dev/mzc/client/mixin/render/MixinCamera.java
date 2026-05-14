package dev.mzc.client.mixin.render;

import dev.mzc.client.Sakura;
import dev.mzc.client.module.impl.movement.ClickTP;
import dev.mzc.client.module.impl.movement.BetterSneak;
import dev.mzc.client.module.impl.render.CameraClip;
import dev.mzc.client.module.impl.render.Freelook;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public abstract class MixinCamera {
    @Unique
    private Entity focusedEntity;
    @Unique
    private boolean thirdPerson;
    @Unique
    private float tickDelta;

    @Shadow
    protected abstract float clipToSpace(float desiredCameraDistance);

    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;moveBy(FFF)V", ordinal = 0))
    private void modifyCameraDistance(Args args) {
        if (Sakura.MODULES.getModule(CameraClip.class).isNormal()) {
            args.set(0, -clipToSpace(Sakura.MODULES.getModule(CameraClip.class).getDistance()));
        }
    }

    @Inject(method = "clipToSpace", at = @At("HEAD"), cancellable = true)
    private void onClipToSpace(float f, CallbackInfoReturnable<Float> cir) {
        CameraClip clip = Sakura.MODULES.getModule(CameraClip.class);
        if (clip.isNormal()) {
            cir.setReturnValue(clip.getDistance());
        } else if (clip.isAction()) {
            cir.setReturnValue(clip.getActionDistance());
        }
    }

    @Inject(method = "update", at = @At("HEAD"))
    private void onUpdateHead(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
        this.focusedEntity = focusedEntity;
        this.thirdPerson = thirdPerson;
        this.tickDelta = tickProgress;
    }

    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"))
    private void modifyCameraRotation(Args args) {
        if (Sakura.MODULES.getModule(Freelook.class).isEnabled()) {
            args.set(0, Freelook.cameraYaw);
            args.set(1, Freelook.cameraPitch);
        }
        dev.mzc.client.module.impl.render.Freecam freecam = Sakura.MODULES.getModule(dev.mzc.client.module.impl.render.Freecam.class);
        if (freecam != null && freecam.isEnabled()) {
            args.set(0, (float) freecam.getYaw(tickDelta));
            args.set(1, (float) freecam.getPitch(tickDelta));
        }
    }

    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setPos(DDD)V"))
    private void onSetCameraPosition(Args args) {
        dev.mzc.client.module.impl.render.Freecam freecam = Sakura.MODULES.getModule(dev.mzc.client.module.impl.render.Freecam.class);
        if (freecam != null && freecam.isEnabled()) {
            args.set(0, freecam.getX(tickDelta));
            args.set(1, freecam.getY(tickDelta));
            args.set(2, freecam.getZ(tickDelta));
            return;
        }

        CameraClip actionCamera = Sakura.MODULES.getModule(CameraClip.class);

        if (actionCamera != null && actionCamera.shouldModifyCamera() && focusedEntity != null) {
            Vec3d playerPos = focusedEntity.getLerpedPos(tickDelta);
            actionCamera.update(playerPos);
            Vec3d cameraPos = actionCamera.getCameraPos();
            if (cameraPos != null) {
                args.set(0, cameraPos.x);
                args.set(1, cameraPos.y);
                args.set(2, cameraPos.z);
            }
        }

        BetterSneak betterSneak = Sakura.MODULES.getModule(BetterSneak.class);
        if (betterSneak != null && betterSneak.isEnabled() && focusedEntity != null && !thirdPerson) {
            if (focusedEntity == Sakura.mc.player) {
                // Prevent camera jitter while airborne/falling: only smooth crouch eye offset on stable ground.
                if (!focusedEntity.isOnGround() || focusedEntity.getVelocity().y < -0.02 || focusedEntity.fallDistance > 0.0f) {
                    return;
                }
                double baseY = (double) args.get(1);
                double standY = focusedEntity.getLerpedPos(tickDelta).y + focusedEntity.getEyeHeight(EntityPose.STANDING);
                double vanillaDelta = standY - baseY;
                if (vanillaDelta <= 0.0) return;

                double maxDelta = focusedEntity.getEyeHeight(EntityPose.STANDING) - focusedEntity.getEyeHeight(EntityPose.CROUCHING);
                maxDelta = Math.max(0.0, maxDelta) + 0.05;
                if (vanillaDelta > maxDelta) return;

                double mult = Math.max(0.0, Math.min(1.0, betterSneak.getOffsetMultiplier()));
                args.set(1, standY - vanillaDelta * mult);
            }
        }
    }
}
