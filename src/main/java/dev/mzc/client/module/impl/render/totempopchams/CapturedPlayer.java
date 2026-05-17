package dev.mzc.client.module.impl.render.totempopchams;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public class CapturedPlayer extends OtherClientPlayerEntity {

    public float statePitch;
    public float glidingProcess;
    public float flyingRotation;
    public float animPos;
    public PlayerEntityModel model;
    public PlayerEntityRenderState state;

    public CapturedPlayer(final ClientWorld world, final PlayerEntity original) {
        super(world, new GameProfile(UUID.randomUUID(), "Captured Player"));
        this.copyPositionAndRotation(original);
        this.lastX = original.lastX;
        this.lastY = original.lastY;
        this.lastZ = original.lastZ;
        this.lastYaw = original.lastYaw;
        this.lastPitch = original.lastPitch;
        this.limbAnimator.setSpeed(original.limbAnimator.getSpeed());
        this.animPos = original.limbAnimator.getAnimationProgress();
        MinecraftClient client = MinecraftClient.getInstance();
        this.state = (PlayerEntityRenderState) client.getEntityRenderDispatcher().getRenderer(this).getAndUpdateRenderState(this, 0F);

        this.state.handSwingProgress = original.handSwingProgress;
        this.state.sneaking = original.isSneaking();
        this.state.glidingTicks = original.getGlidingTicks();
        this.flyingRotation = state.flyingRotation;
        this.statePitch = state.pitch;
        this.glidingProcess = state.getGlidingProgress();

        PlayerEntityModel originalModel = (PlayerEntityModel)
                ((LivingEntityRenderer<?, ?, ?>) client.getEntityRenderDispatcher().getRenderer(original)).getModel();
        this.model = new PlayerEntityModel(client.getLoadedEntityModels().getModelPart(EntityModelLayers.PLAYER), false);
        this.copyModelPartTransform(this.model.head, originalModel.head);
        this.copyModelPartTransform(this.model.hat, originalModel.hat);
        this.copyModelPartTransform(this.model.body, originalModel.body);
        this.copyModelPartTransform(this.model.leftArm, originalModel.leftArm);
        this.copyModelPartTransform(this.model.rightArm, originalModel.rightArm);
        this.copyModelPartTransform(this.model.leftLeg, originalModel.leftLeg);
        this.copyModelPartTransform(this.model.rightLeg, originalModel.rightLeg);

        this.bodyYaw = original.bodyYaw;
        this.lastBodyYaw = original.lastBodyYaw;
        this.headYaw = original.headYaw;
        this.lastHeadYaw = original.lastHeadYaw;
        this.handSwingProgress = original.handSwingProgress;
        this.handSwingTicks = original.handSwingTicks;

        this.setSneaking(original.isSneaking());
        this.setPose(original.getPose());
    }

    private void copyModelPartTransform(final ModelPart target, final ModelPart source) {
        target.pitch = source.pitch;
        target.yaw = source.yaw;
        target.roll = source.roll;
        target.originX = source.originX;
        target.originY = source.originY;
        target.originZ = source.originZ;
        target.xScale = source.xScale;
        target.yScale = source.yScale;
        target.zScale = source.zScale;
    }

    public void tickAge(final int lifeTime) {
        this.age++;
        if (this.age >= lifeTime) {
            this.remove(RemovalReason.DISCARDED);
            TotemPopChamsHandler.getPositions().remove(this);
        }
    }

    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean isCreative() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isCollidable(@Nullable Entity entity) {
        return false;
    }

    @Override
    public boolean collidesWith(Entity other) {
        return false;
    }

    @Override
    protected void pushAway(Entity entity) {
    }
}
