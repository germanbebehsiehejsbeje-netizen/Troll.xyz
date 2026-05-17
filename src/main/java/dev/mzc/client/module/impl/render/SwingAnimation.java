package dev.mzc.client.module.impl.render;

import dev.mzc.client.Sakura;
import dev.mzc.client.events.EventType;
import dev.mzc.client.events.entity.LimbAnimationEvent;
import dev.mzc.client.events.entity.SwingSpeedEvent;
import dev.mzc.client.events.entity.UpdateServerPositionEvent;
import dev.mzc.client.events.packet.PacketEvent;
import dev.mzc.client.events.render.item.EatTransformationEvent;
import dev.mzc.client.events.render.item.RenderSwingAnimationEvent;
import dev.mzc.client.events.render.item.UpdateHeldItemsEvent;
import dev.mzc.client.mixin.accessor.IAccessorBundlePacket;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.module.impl.combat.KillAura;
import dev.mzc.client.values.Value;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.List;

public class SwingAnimation extends Module {
    public enum Mode {
        Normal("Normal"),
        Mode1("Mode 1"),
        Mode2("Mode 2"),
        Mode3("Mode 3"),
        Mode4("Mode 4"),
        Mode5("Mode 5");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // New animation settings from MiaWare
    public final EnumValue<Mode> mode = new EnumValue<>("Mode", Mode.Normal);
    private final BoolValue auraOnly = new BoolValue("Aura Only", false);
    private final NumberValue<Double> strength = new NumberValue<>("Strength", 20.0, 20.0, 75.0, 0.1, () -> !mode.is(Mode.Normal) && !mode.is(Mode.Mode5));

    private final BoolValue slowSwing = new BoolValue("Slow", false);
    private final NumberValue<Double> speed = new NumberValue<>("Speed", 12.0, 1.0, 50.0, 1.0, slowSwing::get);

    // Legacy settings
    private final Value<Boolean> noSwitchConfig = new BoolValue("NoSwitchAnimation", false);
    private final Value<Boolean> oldSwingConfig = new BoolValue("OldSwingAnimation", false);
    private final Value<Boolean> swingSpeedConfig = new BoolValue("SwingSpeed", false);
    private final Value<Integer> swingFactorConfig = new NumberValue<>("SwingFactor", 6, 1, 20, 1, () -> swingSpeedConfig.get());
    private final Value<Boolean> selfOnlyConfig = new BoolValue("SelfOnly", true, () -> false);
    private final Value<Boolean> eatTransformConfig = new BoolValue("EatTransform", false);
    private final Value<Double> eatTransformFactorConfig = new NumberValue<>("EatTransform-Factor", 1.0, 0.0, 1.0, 0.1, () -> eatTransformConfig.get());
    private final Value<Boolean> limbSwing = new BoolValue("NoLimbSwing", false);
    private final Value<Boolean> interpolationConfig = new BoolValue("NoInterpolation", false, () -> limbSwing.get());

    public SwingAnimation() {
        super("SwingAnimation", Category.Render);
        this.setType(ModuleType.All);
    }

    @Override
    protected void onEnable() {
        Sakura.EVENT_BUS.subscribe(this);
    }

    @Override
    protected void onDisable() {
        Sakura.EVENT_BUS.unsubscribe(this);
    }

    @EventHandler
    public void onUpdateHeldItems(UpdateHeldItemsEvent event) {
        if (noSwitchConfig.get()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwingSpeed(SwingSpeedEvent event) {
        if (swingSpeedConfig.get()) {
            event.setCancelled(true);
            event.setSwingSpeed(swingFactorConfig.get());
            event.setSelfOnly(selfOnlyConfig.get());
        }
    }

    @EventHandler
    public void onEatTransformation(EatTransformationEvent event) {
        if (eatTransformConfig.get()) {
            event.setCancelled(true);
            event.setFactor(eatTransformFactorConfig.get().floatValue());
        }
    }

    @EventHandler
    public void onLimbAnimation(LimbAnimationEvent event) {
        if (limbSwing.get()) {
            event.setCancelled(true);
            event.setSpeed(0.0f);
        }
    }

    @EventHandler
    public void onUpdateServerPosition(UpdateServerPositionEvent event) {
        if (limbSwing.get() && interpolationConfig.get()) {
            event.getLivingEntity().setPos(event.getX(), event.getY(), event.getZ());
            event.getLivingEntity().setYaw(event.getYaw());
            event.getLivingEntity().setPitch(event.getPitch());
        }
    }

    @EventHandler
    public void onRenderSwing(RenderSwingAnimationEvent event) {
        if (oldSwingConfig.get()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPacketInbound(PacketEvent event) {
        if (mc.player == null || event.getType() != EventType.RECEIVE) return;

        if (event.getPacket() instanceof BundleS2CPacket packet) {
            List<Packet<?>> packets = new ArrayList<>();
            for (Packet<?> packet1 : packet.getPackets()) {
                if (packet1 instanceof EntityAnimationS2CPacket packet2 && oldSwingConfig.get()
                        && packet2.getEntityId() == mc.player.getId()
                        && (packet2.getAnimationId() == EntityAnimationS2CPacket.SWING_MAIN_HAND || packet2.getAnimationId() == EntityAnimationS2CPacket.SWING_OFF_HAND)) {
                    continue;
                }
                packets.add(packet1);
            }
            ((IAccessorBundlePacket) packet).setIterable(packets);
        } else if (event.getPacket() instanceof EntityAnimationS2CPacket packet && oldSwingConfig.get()
                && packet.getEntityId() == mc.player.getId()
                && (packet.getAnimationId() == EntityAnimationS2CPacket.SWING_MAIN_HAND || packet.getAnimationId() == EntityAnimationS2CPacket.SWING_OFF_HAND)) {
            event.setCancelled(true);
        }
    }

    // New animation handlers from MiaWare
    private void handleSwordAnim(MatrixStack matrices, float swingProgress, Arm arm) {
        float g = MathHelper.sin(MathHelper.sqrt(swingProgress) * 3.1415927F);
        float anim = (float) Math.sin(swingProgress * (Math.PI / 2) * 2);
        float isLeft = arm == Arm.LEFT ? -1f : 1f;

        switch (mode.get()) {
            case Mode1 -> {
                applyEquipOffset(matrices, arm, 0);
                applySwingOffset(matrices, arm, swingProgress);
            }
            case Mode2 -> {
                applyEquipOffset(matrices, arm, 0);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(50f));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(isLeft * -60f));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(isLeft * (110f + strength.get().floatValue() * g)));
            }
            case Mode3 -> {
                applyEquipOffset(matrices, arm, 0);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(50f));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(isLeft * (-30f * (1f - g) - 30f + (strength.get().floatValue() - 20f) * g)));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(isLeft * 110f));
            }
            case Mode4 -> {
                applyEquipOffset(matrices, arm, 0);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(isLeft * 90f));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(isLeft * -30f));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f - strength.get().floatValue() * anim + 10f));
            }
            case Mode5 -> {
                float rotation = swingProgress * -360f;
                applyEquipOffset(matrices, arm, 0);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotation));
            }
            default -> {
                // Normal vanilla animation
            }
        }
    }

    public void handleRenderItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        if (!player.isUsingSpyglass()) {
            boolean bl = hand == Hand.MAIN_HAND;
            Arm arm = bl ? player.getMainArm() : player.getMainArm().getOpposite();
            matrices.push();
            
            if (item.isOf(Items.CROSSBOW)) {
                boolean bl2 = CrossbowItem.isCharged(item);
                boolean bl3 = arm == Arm.RIGHT;
                int i = bl3 ? 1 : -1;
                if (player.isUsingItem() && player.getItemUseTimeLeft() > 0 && player.getActiveHand() == hand) {
                    this.applyEquipOffset(matrices, arm, equipProgress);
                    matrices.translate((float) i * -0.4785682F, -0.094387F, 0.05731531F);
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-11.935F));
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * 65.3F));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) i * -9.785F));
                    float f = (float) item.getMaxUseTime(mc.player) - ((float) mc.player.getItemUseTimeLeft() - tickDelta + 1.0F);
                    float g = f / (float) CrossbowItem.getPullTime(item, mc.player);
                    if (g > 1.0F) {
                        g = 1.0F;
                    }

                    if (g > 0.1F) {
                        float h = MathHelper.sin((f - 0.1F) * 1.3F);
                        float j = g - 0.1F;
                        float k = h * j;
                        matrices.translate(k * 0.0F, k * 0.004F, k * 0.0F);
                    }

                    matrices.translate(g * 0.0F, g * 0.0F, g * 0.04F);
                    matrices.scale(1.0F, 1.0F, 1.0F + g * 0.2F);
                    matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees((float) i * 45.0F));
                } else {
                    float fx = -0.4F * MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
                    float gx = 0.2F * MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) (Math.PI * 2));
                    float h = -0.2F * MathHelper.sin(swingProgress * (float) Math.PI);
                    matrices.translate((float) i * fx, gx, h);
                    this.applyEquipOffset(matrices, arm, equipProgress);
                    this.applySwingOffset(matrices, arm, swingProgress);
                    if (bl2 && swingProgress < 0.001F && bl) {
                        matrices.translate((float) i * -0.641864F, 0.0F, 0.0F);
                        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * 10.0F));
                    }
                }
                this.renderItem(player, item, bl3 ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, !bl3, matrices, vertexConsumers, light);
            } else {
                boolean bl2 = arm == Arm.RIGHT;

                // ViewModel integration
                ViewModel viewModel = Sakura.MODULES.getModule(ViewModel.class);
                if (viewModel != null && viewModel.isEnabled()) {
                    if (bl2) {
                        matrices.translate(viewModel.mainX.get().doubleValue(), viewModel.mainY.get().doubleValue(), viewModel.mainZ.get().doubleValue());
                    } else {
                        matrices.translate(viewModel.offX.get().doubleValue(), viewModel.offY.get().doubleValue(), viewModel.offZ.get().doubleValue());
                    }
                }

                if (player.isUsingItem() && player.getItemUseTimeLeft() > 0 && player.getActiveHand() == hand) {
                    int l = bl2 ? 1 : -1;
                    switch (item.getUseAction()) {
                        case NONE, BLOCK:
                            this.applyEquipOffset(matrices, arm, equipProgress);
                            break;
                        case EAT:
                        case DRINK:
                            this.applyEatOrDrinkTransformation(matrices, tickDelta, arm, item);
                            this.applyEquipOffset(matrices, arm, equipProgress);
                            break;
                        case BOW:
                            this.applyEquipOffset(matrices, arm, equipProgress);
                            matrices.translate((float) l * -0.2785682F, 0.18344387F, 0.15731531F);
                            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-13.935F));
                            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) l * 35.3F));
                            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) l * -9.785F));
                            float mx = (float) item.getMaxUseTime(mc.player) - ((float) mc.player.getItemUseTimeLeft() - tickDelta + 1.0F);
                            float fxx = mx / 20.0F;
                            fxx = (fxx * fxx + fxx * 2.0F) / 3.0F;
                            if (fxx > 1.0F) {
                                fxx = 1.0F;
                            }

                            if (fxx > 0.1F) {
                                float gx = MathHelper.sin((mx - 0.1F) * 1.3F);
                                float h = fxx - 0.1F;
                                float j = gx * h;
                                matrices.translate(j * 0.0F, j * 0.004F, j * 0.0F);
                            }

                            matrices.translate(fxx * 0.0F, fxx * 0.0F, fxx * 0.04F);
                            matrices.scale(1.0F, 1.0F, 1.0F + fxx * 0.2F);
                            matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees((float) l * 45.0F));
                            break;
                        case SPEAR:
                            this.applyEquipOffset(matrices, arm, equipProgress);
                            matrices.translate((float) l * -0.5F, 0.7F, 0.1F);
                            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-55.0F));
                            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) l * 35.3F));
                            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) l * -9.785F));
                            float m = (float) item.getMaxUseTime(mc.player) - ((float) mc.player.getItemUseTimeLeft() - tickDelta + 1.0F);
                            float fx = m / 10.0F;
                            if (fx > 1.0F) {
                                fx = 1.0F;
                            }

                            if (fx > 0.1F) {
                                float gx = MathHelper.sin((m - 0.1F) * 1.3F);
                                float h = fx - 0.1F;
                                float j = gx * h;
                                matrices.translate(j * 0.0F, j * 0.004F, j * 0.0F);
                            }

                            matrices.translate(0.0F, 0.0F, fx * 0.2F);
                            matrices.scale(1.0F, 1.0F, 1.0F + fx * 0.2F);
                            matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees((float) l * 45.0F));
                            break;
                        case BRUSH:
                            this.applyBrushTransformation(matrices, tickDelta, arm, item, equipProgress);
                    }
                } else if (player.isUsingRiptide()) {
                    this.applyEquipOffset(matrices, arm, equipProgress);
                    int l = bl2 ? 1 : -1;
                    matrices.translate((float) l * -0.4F, 0.8F, 0.3F);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) l * 65.0F));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) l * -85.0F));
                } else {
                    // Apply custom swing animation if enabled and conditions met
                    if (arm == mc.options.getMainArm().getValue() && isEnabled() && auraCheck() && !mode.is(Mode.Normal)) {
                        handleSwordAnim(matrices, swingProgress, arm);
                    } else {
                        float n = -0.4F * MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
                        float mxx = 0.2F * MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) (Math.PI * 2));
                        float fxxx = -0.2F * MathHelper.sin(swingProgress * (float) Math.PI);
                        int o = bl2 ? 1 : -1;
                        matrices.translate((float) o * n, mxx, fxxx);
                        this.applyEquipOffset(matrices, arm, equipProgress);
                        this.applySwingOffset(matrices, arm, swingProgress);
                    }
                }
                this.renderItem(player, item, bl2 ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, !bl2, matrices, vertexConsumers, light);
            }

            matrices.pop();
        }
    }

    private void applyBrushTransformation(MatrixStack matrices, float tickDelta, Arm arm, ItemStack stack, float equipProgress) {
        this.applyEquipOffset(matrices, arm, equipProgress);
        float f = (float) (mc.player.getItemUseTimeLeft() % 10);
        float g = f - tickDelta + 1.0F;
        float h = 1.0F - g / 10.0F;
        float n = -15.0F + 75.0F * MathHelper.cos(h * 2.0F * (float) Math.PI);
        if (arm != Arm.RIGHT) {
            matrices.translate(0.1, 0.83, 0.35);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-80.0F));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0F));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(n));
            matrices.translate(-0.3, 0.22, 0.35);
        } else {
            matrices.translate(-0.25, 0.22, 0.35);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-80.0F));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(0.0F));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(n));
        }
    }

    private void applyEatOrDrinkTransformation(MatrixStack matrices, float tickDelta, Arm arm, ItemStack stack) {
        float f = (float) mc.player.getItemUseTimeLeft() - tickDelta + 1.0F;
        float g = f / (float) stack.getMaxUseTime(mc.player);
        if (g < 0.8F) {
            float h = MathHelper.abs(MathHelper.cos(f / 4.0F * (float) Math.PI) * 0.1F);
            matrices.translate(0.0F, h, 0.0F);
        }

        float h = 1.0F - (float) Math.pow(g, 27.0);
        int i = arm == Arm.RIGHT ? 1 : -1;
        matrices.translate(h * 0.6F * (float) i, h * -0.5F, h * 0.0F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * h * 90.0F));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(h * 10.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) i * h * 30.0F));
    }

    private void applyEquipOffset(MatrixStack matrices, Arm arm, float equipProgress) {
        int i = arm == Arm.RIGHT ? 1 : -1;
        matrices.translate((float) i * 0.56F, -0.52F + equipProgress * -0.6F, -0.72F);
    }

    private void applySwingOffset(MatrixStack matrices, Arm arm, float swingProgress) {
        int i = arm == Arm.RIGHT ? 1 : -1;
        float f = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        float g = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * (45.0F + f * -20.0F)));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) i * g * -20.0F));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g * -80.0F));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * -45.0F));
    }

    public void renderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext renderMode, boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        if (!stack.isEmpty()) {
            // 1.21.11 item rendering pipeline changed to state + command queue.
            // Keep a safe no-op here for now to avoid hard crash/compile break during migration.
        }
    }

    public boolean auraCheck() {
        KillAura aura = Sakura.MODULES.getModule(KillAura.class);
        return !auraOnly.get() || (aura != null && aura.getTarget() != null);
    }
}
