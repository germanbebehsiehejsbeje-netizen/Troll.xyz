package dev.mzc.client.module.impl.render.totempopchams;

import dev.mzc.client.module.impl.render.TotemPopChams;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

public class ChamExtractor {
    private static final int LIGHT = 15728880;
    private final WireframePartExtractor wireframeExtractor;
    private final ChamConfigState state;

    public ChamExtractor() {
        this.wireframeExtractor = new WireframePartExtractor();
        this.state = new ChamConfigState();
    }

    public void extract(final MatrixStack matrices, final float delta, TotemPopChams module) {
        final VertexConsumerProvider.Immediate immediate = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        for (final CapturedPlayer player : TotemPopChamsHandler.getPositions()) {
            this.state.updateConfigRenderState(player, delta, module);
            if (this.state.renderFillModel) {
                this.renderPlayerModel(matrices, player, immediate);
            }
            if (this.state.renderWireframe) {
                this.renderWireframe(matrices, player, immediate);
            }
        }
        // Don't call immediate.draw() here - let the render pipeline handle it
    }

    private void renderPlayerModel(final MatrixStack matrices, final CapturedPlayer player, final VertexConsumerProvider.Immediate immediate) {
        this.setupAndPush(matrices, player);
        final PlayerEntityModel model = player.model;
        this.setupRenderState(player.state, model);
        this.applyFlyingTransforms(player, matrices);

        final VertexConsumer buffer = immediate.getBuffer(TotemPopChamsLayers.TRANSLUCENT_ENTITY_HIGHLIGHT);
        final int color = this.alphaColor(this.state.filledColor, this.state.alpha);
        if (!this.state.disperse) {
            model.render(matrices, buffer, LIGHT, OverlayTexture.DEFAULT_UV, color);
        } else {
            for (final BodyPart part : BodyPart.values()) {
                Vec3d pos = this.getPartDisplacement(player, part);
                matrices.push();
                matrices.translate(pos.getX(), pos.getY(), pos.getZ());
                this.renderModelPart(model, part, matrices, buffer, color);
                matrices.pop();
            }
        }

        matrices.pop();
    }

    private void renderWireframe(final MatrixStack matrices, final CapturedPlayer player, final VertexConsumerProvider.Immediate immediate) {
        this.setupAndPush(matrices, player);
        final PlayerEntityModel model = player.model;
        this.setupRenderState(player.state, model);
        this.applyFlyingTransforms(player, matrices);

        final VertexConsumer buffer = immediate.getBuffer(TotemPopChamsLayers.WIREFRAME);
        final int color = this.alphaColor(this.state.wireframeColor, this.state.alpha);
        for (final BodyPart part : BodyPart.values()) {
            final Vec3d disp = this.getPartDisplacement(player, part);
            matrices.push();
            matrices.translate(disp.getX(), disp.getY(), disp.getZ());
            this.renderWireframePart(model, part, matrices, buffer, color);
            matrices.pop();
        }

        matrices.pop();
    }

    private int alphaColor(final int baseColor, final float alphaMultiplier) {
        final int newAlpha = MathHelper.clamp((int) (((baseColor >> 24) & 0xFF) * alphaMultiplier), 0, 255);
        return (newAlpha << 24) | (baseColor & 0x00FFFFFF);
    }

    private void renderModelPart(final PlayerEntityModel model, final BodyPart part, final MatrixStack matrices, final VertexConsumer buffer, final int color) {
        switch (part) {
            case HEAD -> model.head.render(matrices, buffer, LIGHT, OverlayTexture.DEFAULT_UV, color);
            case BODY -> model.body.render(matrices, buffer, LIGHT, OverlayTexture.DEFAULT_UV, color);
            case LEFT_ARM -> model.leftArm.render(matrices, buffer, LIGHT, OverlayTexture.DEFAULT_UV, color);
            case RIGHT_ARM -> model.rightArm.render(matrices, buffer, LIGHT, OverlayTexture.DEFAULT_UV, color);
            case LEFT_LEG -> model.leftLeg.render(matrices, buffer, LIGHT, OverlayTexture.DEFAULT_UV, color);
            case RIGHT_LEG -> model.rightLeg.render(matrices, buffer, LIGHT, OverlayTexture.DEFAULT_UV, color);
        }
    }

    private void renderWireframePart(final PlayerEntityModel model, final BodyPart part, final MatrixStack matrices, final VertexConsumer buffer, final int color) {
        switch (part) {
            case HEAD -> this.wireframeExtractor.extractModelPart(model.head, matrices, buffer, color, this.state.wireframeThickness);
            case BODY -> this.wireframeExtractor.extractModelPart(model.body, matrices, buffer, color, this.state.wireframeThickness);
            case LEFT_ARM -> this.wireframeExtractor.extractModelPart(model.leftArm, matrices, buffer, color, this.state.wireframeThickness);
            case RIGHT_ARM -> this.wireframeExtractor.extractModelPart(model.rightArm, matrices, buffer, color, this.state.wireframeThickness);
            case LEFT_LEG -> this.wireframeExtractor.extractModelPart(model.leftLeg, matrices, buffer, color, this.state.wireframeThickness);
            case RIGHT_LEG -> this.wireframeExtractor.extractModelPart(model.rightLeg, matrices, buffer, color, this.state.wireframeThickness);
        }
    }

    private Vec3d getPartDisplacement(final CapturedPlayer player, final BodyPart part) {
        if (!this.state.disperse) {
            return Vec3d.ZERO;
        }

        final long seed = player.getUuid().getMostSignificantBits() ^ player.getUuid().getLeastSignificantBits() ^ part.ordinal();
        final Random rand = Random.create(seed);

        final Vec3d base = switch (part) {
            case HEAD -> new Vec3d(0, -1, 0);
            case BODY -> Vec3d.ZERO;
            case LEFT_ARM -> new Vec3d(1, 0, 0);
            case RIGHT_ARM -> new Vec3d(-1, 0, 0);
            case LEFT_LEG -> new Vec3d(0.3, 1, 0);
            case RIGHT_LEG -> new Vec3d(-0.3, 1, 0);
        };
        final Vec3d noise = new Vec3d((rand.nextDouble() - 0.5) * 0.5, (rand.nextDouble() - 0.5) * 0.5, (rand.nextDouble() - 0.5) * 0.5);
        return base.add(noise).normalize().multiply(this.state.displacementAmount);
    }

    private void setupRenderState(final PlayerEntityRenderState state, final PlayerEntityModel model) {
        state.leftPantsLegVisible = false;
        state.rightPantsLegVisible = false;
        state.leftSleeveVisible = false;
        state.rightSleeveVisible = false;
        state.jacketVisible = false;
        state.hatVisible = false;

        model.leftPants.visible = false;
        model.rightPants.visible = false;
        model.leftSleeve.visible = false;
        model.rightSleeve.visible = false;
        model.jacket.visible = false;
        model.hat.visible = false;
    }

    private void setupAndPush(final MatrixStack stack, final CapturedPlayer player) {
        stack.push();
        // Need to account for camera position since Render3DEvent matrix doesn't include camera translation
        final Vec3d camera = MinecraftClient.getInstance().getEntityRenderDispatcher().camera.getCameraPos();
        stack.translate(player.getX() - camera.x, player.getY() - camera.y, player.getZ() - camera.z);
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - player.bodyYaw));
        stack.scale(-1.0F, -1.0F, 1.0F);
        stack.scale(1.6f / (float) 2, 1.8f / (float) 2, 1.6f / (float) 2);
        stack.translate(0.0F, -1.5F, 0.0F);
    }

    private void applyFlyingTransforms(final CapturedPlayer player, final MatrixStack stack) {
        if (!player.state.usingRiptide) {
            stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(player.glidingProcess * (90.0F - player.statePitch)));
        }
        if (player.state.applyFlyingRotation) {
            stack.multiply(RotationAxis.POSITIVE_Y.rotation(player.flyingRotation));
        }
    }

    private enum BodyPart {
        HEAD, BODY, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG
    }
}
