package dev.mzc.client.module.impl.misc;

import dev.mzc.client.events.EventType;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.input.MoveInputEvent;
import dev.mzc.client.events.player.MotionEvent;
import dev.mzc.client.manager.Managers;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.rotation.MovementFix;
import dev.mzc.client.utils.rotation.RotationUtil;
import dev.mzc.client.utils.vector.Rotation;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

public class SpearTarget extends Module {
    private final NumberValue<Double> range = new NumberValue<>("Target Range", 50.0, 10.0, 100.0, 1.0);
    private final NumberValue<Double> prediction = new NumberValue<>("Prediction", 2.0, 0.0, 5.0, 0.1);
    private final NumberValue<Integer> fireworkDelay = new NumberValue<>("Firework Delay", 5, 1, 20, 1);
    private final NumberValue<Double> rotSpeed = new NumberValue<>("Rotation Speed", 1.5, 0.1, 5.0, 0.1);

    private final BoolValue forceForward = new BoolValue("Force Forward", true);
    private final BoolValue requireSpear = new BoolValue("Require Spear", true);
    private final BoolValue autoTarget = new BoolValue("Auto Target", true);
    private final BoolValue onlyPlayers = new BoolValue("Only Players", false);
    private final BoolValue useFireworks = new BoolValue("Use Fireworks", true);
    private final BoolValue silentRotation = new BoolValue("Silent Rotation", true);
    private final BoolValue autoLaunch = new BoolValue("Auto Launch", true);
    private final BoolValue capioCharge = new BoolValue("Capio Charge", true);

    private LivingEntity target;
    private int fireworkTimer;
    private boolean elytraFlying;
    private int flyingTicks;

    public SpearTarget() {
        super("SpearTarget", Category.Misc);
    }

    @EventHandler
    public void onMotion(MotionEvent event) {
        if (nullCheck()) return;

        if (event.getType() == EventType.PRE) {
            if (this.autoTarget.get()) {
                this.findTarget();
            }
            this.updateElytraState();

            if (this.target != null && this.isValidTarget(this.target) && this.elytraFlying) {
                if (this.silentRotation.get()) {
                    Rotation targetAngle = getPredictedRotation(this.target);
                    // Очень низкая скорость для плавности (как AimAssist)
                    Managers.ROTATION.setRotations(targetAngle, rotSpeed.get(), MovementFix.GRIM, dev.mzc.client.manager.impl.RotationManager.Priority.Highest);
                }
            }
        } else if (event.getType() == EventType.POST) {
            if (this.elytraFlying && this.target != null && this.isValidTarget(this.target)) {
                if (this.capioCharge.get() && this.isCapioInMainHand()) {
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                }

                if (this.useFireworks.get() && this.fireworkTimer <= 0) {
                    if (!this.requireSpear.get() || isHoldingSpear()) {
                        this.useFirework();
                    }
                }
            }
        }
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;
        if (this.fireworkTimer > 0) this.fireworkTimer--;

        if (this.autoLaunch.get() && isHoldingSpear() && hasElytraEquipped() && !mc.player.isOnGround() && !this.elytraFlying && this.fireworkTimer <= 0) {
            if (this.tryLaunchElytra()) {
                this.elytraFlying = true;
                this.flyingTicks = 1;
            }
        }
    }

    @EventHandler
    public void onMoveInput(MoveInputEvent event) {
        if (this.isEnabled() && this.forceForward.get() && this.elytraFlying && this.target != null && this.isValidTarget(this.target)) {
            event.setForward(1.0f);
        }
    }

    private void updateElytraState() {
        boolean hasElytra = this.hasElytraEquipped();
        boolean onGround = mc.player.isOnGround();
        Vec3d vel = mc.player.getVelocity();
        double horizSpeedSq = vel.x * vel.x + vel.z * vel.z;

        if (hasElytra && !onGround) {
            this.flyingTicks++;
            this.elytraFlying = mc.player.isGliding() || (this.elytraFlying && this.flyingTicks > 0) || horizSpeedSq > 0.01;
        } else {
            this.elytraFlying = false;
            this.flyingTicks = 0;
        }
    }

    private Rotation getPredictedRotation(LivingEntity target) {
        Vec3d targetPos = target.getEyePos();
        double dist = mc.player.distanceTo(target);
        Vec3d vel = target.getVelocity();
        double tickPredict = prediction.get() * (dist / 20.0);
        Vec3d predicted = targetPos.add(vel.x * tickPredict, vel.y * tickPredict, vel.z * tickPredict);

        return RotationUtil.calculate(predicted);
    }

    private void useFirework() {
        if (mc.player.getOffHandStack().getItem() instanceof FireworkRocketItem) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            this.fireworkTimer = this.fireworkDelay.get();
            return;
        }

        int weaponSlot = mc.player.getInventory().getSelectedSlot();
        int fwSlot = this.findFireworksSlotExcluding(weaponSlot);

        if (fwSlot != -1) {
            int oldSlot = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(fwSlot);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.getInventory().setSelectedSlot(oldSlot);
            this.fireworkTimer = this.fireworkDelay.get();
        }
    }

    private boolean tryLaunchElytra() {
        if (mc.player.getOffHandStack().getItem() instanceof FireworkRocketItem) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            this.fireworkTimer = this.fireworkDelay.get();
            return true;
        }
        int weaponSlot = mc.player.getInventory().getSelectedSlot();
        int fwSlot = this.findFireworksSlotExcluding(weaponSlot);
        if (fwSlot == -1) return false;

        int oldSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(fwSlot);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.getInventory().setSelectedSlot(oldSlot);
        this.fireworkTimer = this.fireworkDelay.get();
        return true;
    }

    private boolean hasElytraEquipped() {
        return mc.player != null && mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
    }

    private void findTarget() {
        if (this.target != null && this.isValidTarget(this.target)) return;

        LivingEntity best = null;
        double bestDist = this.range.get();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity == mc.player || !this.isValidTarget(living)) continue;

            double distance = mc.player.distanceTo(entity);
            if (distance < bestDist) {
                bestDist = distance;
                best = living;
            }
        }
        this.target = best;
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (mc.player == null || entity == null || entity == mc.player) return false;
        if (!entity.isAlive()) return false;
        if (mc.player.distanceTo(entity) > this.range.get()) return false;
        if (this.onlyPlayers.get() && !(entity instanceof PlayerEntity)) return false;

        return mc.player.canSee(entity);
    }

    private boolean isHoldingSpear() {
        if (mc.player == null) return false;
        return this.isSpearItem(mc.player.getMainHandStack()) || this.isSpearItem(mc.player.getOffHandStack());
    }

    private boolean isCapioInMainHand() {
        return mc.player != null && this.isCapioItem(mc.player.getMainHandStack());
    }

    private boolean isSpearItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.isOf(Items.TRIDENT)) return true;
        return this.isCapioItem(stack);
    }

    private boolean isCapioItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String id = Registries.ITEM.getId(stack.getItem()).getPath().toLowerCase();
        return id.contains("netherite_spear") || id.contains("capio");
    }

    private int findFireworksSlotExcluding(int excludeSlot) {
        for (int i = 0; i < 9; ++i) {
            if (i == excludeSlot) continue;
            if (mc.player.getInventory().getStack(i).getItem() instanceof FireworkRocketItem) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void onEnable() {
        this.target = null;
        this.fireworkTimer = 0;
        this.elytraFlying = false;
        this.flyingTicks = 0;
    }

    @Override
    protected void onDisable() {
        this.target = null;
        this.fireworkTimer = 0;
        this.elytraFlying = false;
        this.flyingTicks = 0;
    }
}
