package dev.mzc.client.module.impl.combat;

import dev.mzc.client.Sakura;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.manager.Managers;
import dev.mzc.client.manager.impl.RotationManager.Priority;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.player.InvUtil;
import dev.mzc.client.utils.rotation.MovementFix;
import dev.mzc.client.utils.vector.Rotation;
import dev.mzc.client.utils.time.TimerUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import dev.mzc.client.values.impl.RangeValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;

public class AutoThrow extends Module {
    public AutoThrow() {
        super("AutoThrow", Category.Combat);
        this.setType(ModuleType.Safe);
    }

    private final RangeValue<Double> range = new RangeValue<>("Range", 8.5, 13.5, 0.0, 20.0, 0.25);
    private final RangeValue<Integer> delay = new RangeValue<>("Delay", 100, 300, 0, 1000, 10);
    private final NumberValue<Integer> switchDelay = new NumberValue<>("Switch Delay", 0, 0, 500, 10);
    private final NumberValue<Integer> swapDelay = new NumberValue<>("Switch Back Delay", 200, 0, 500, 10);
    private final BoolValue autoRotate = new BoolValue("Auto Rotate", true);
    private final BoolValue wallCheck = new BoolValue("Wall Check", true);
    private final BoolValue pauseWhileFishing = new BoolValue("Pause While Fishing", true);
    private final NumberValue<Integer> rotationSpeed = new NumberValue<>("Rotation Speed", 10, 1, 10, 1, autoRotate::get);
    private final NumberValue<Integer> rotationBackSpeed = new NumberValue<>("Rotation Back Speed", 10, 0, 10, 1, autoRotate::get);

    private ThrowInfo pendingPlan;
    private Rotation pendingRotation;
    private boolean pendingSwapBack = false;
    private boolean waitingForSwitch = false; // Waiting for switch delay
    
    private final TimerUtil timer = new TimerUtil();
    private final TimerUtil swapTimer = new TimerUtil();
    private final TimerUtil switchTimer = new TimerUtil();

    @Override
    public void onEnable() {
        pendingPlan = null;
        pendingRotation = null;
        pendingSwapBack = false;
        waitingForSwitch = false;

        timer.reset();
        swapTimer.reset();
        switchTimer.reset();
    }

    @Override
    public void onDisable() {
        pendingPlan = null;
        pendingRotation = null;
        pendingSwapBack = false;
        waitingForSwitch = false;
        timer.reset();
        swapTimer.reset();
        switchTimer.reset();
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        if (pauseWhileFishing.get() && mc.player.fishHook != null) {
            return;
        }

        if (pendingSwapBack && swapTimer.passedMS(swapDelay.get())) {
            InvUtil.swapBack();
            pendingSwapBack = false;
        }

        Rotation rotation;

        // If we have a pending plan (target locked)
        if (pendingPlan != null) {
            // Check if rotation is aligned (if rotation was needed)
            boolean aligned = pendingRotation == null || isFacing(pendingRotation);
            
            if (aligned) {
                // If we are waiting for switch delay
                if (waitingForSwitch) {
                    if (switchTimer.passedMS(switchDelay.get())) {
                        Rotation backRotation = new Rotation(mc.player.getYaw(), mc.player.getPitch());
                        performThrow(pendingPlan, pendingRotation);
                        
                        // Cleanup
                        pendingPlan = null;
                        pendingRotation = null;
                        waitingForSwitch = false;
                        
                        if (autoRotate.get()) {
                            Managers.ROTATION.setRotations(backRotation, rotationBackSpeed.get(), MovementFix.NORMAL, Priority.Highest);
                        }
                    }
                    // Else wait
                } else {
                    // Start throw sequence
                    // Check if we need to switch
                    if (pendingPlan.hand == Hand.MAIN_HAND && mc.player.getInventory().getSelectedSlot() != pendingPlan.hotbarSlot) {
                        InvUtil.swap(pendingPlan.hotbarSlot, true);
                        if (switchDelay.get() > 0) {
                            waitingForSwitch = true;
                            switchTimer.reset();
                            return; // Wait for next tick(s)
                        }
                    }
                    
                    // No switch needed or no delay needed
                    Rotation backRotation = new Rotation(mc.player.getYaw(), mc.player.getPitch());
                    performThrow(pendingPlan, pendingRotation);
                    
                    pendingPlan = null;
                    pendingRotation = null;
                    waitingForSwitch = false;

                    if (autoRotate.get()) {
                         Managers.ROTATION.setRotations(backRotation, rotationBackSpeed.get(), MovementFix.NORMAL, Priority.Highest);
                    }
                }
            }
            return;
        }

        ThrowInfo plan = updateThrowInfo();
        if (plan == null) {
            return;
        }

        LivingEntity target = getClosestEnemy(range.getMinValue(), range.getMaxValue());
        if (target == null) {
            return;
        }

        if (wallCheck.get() && !mc.player.canSee(target)) {
            return;
        }

        int minD = delay.getMinValue();
        int maxD = delay.getMaxValue();
        if (maxD < minD) maxD = minD;
        
        if (timer.passedMS(ThreadLocalRandom.current().nextInt(minD, maxD + 1)) && canRotate(plan.hand)) {
            rotation = getRotationToEntity(target);
            if (autoRotate.get()) {
                Managers.ROTATION.setRotations(rotation, rotationSpeed.get(), MovementFix.NORMAL, Priority.Highest);
                pendingPlan = plan;
                pendingRotation = rotation;
                waitingForSwitch = false;
            } else {
                if (isFacing(rotation)) {
                    // Set pending plan even if no rotation needed, to reuse logic
                    pendingPlan = plan;
                    pendingRotation = null; // No rotation needed
                    waitingForSwitch = false;
                }
            }
            timer.reset();
        }
    }

    private void performThrow(ThrowInfo plan, Rotation rotation) {
        float originalYaw = mc.player.getYaw();
        float originalPitch = mc.player.getPitch();
        if (rotation != null) {
            mc.player.setYaw(rotation.yaw);
            mc.player.setPitch(rotation.pitch);
        }
        
        // Slot switching is handled before calling this, or here if delay is 0
        if (plan.hand == Hand.MAIN_HAND && mc.player.getInventory().getSelectedSlot() != plan.hotbarSlot) {
            InvUtil.swap(plan.hotbarSlot, true);
        }

        mc.interactionManager.interactItem(mc.player, plan.hand);
        mc.player.swingHand(plan.hand);

        if (rotation != null) {
            mc.player.setYaw(originalYaw);
            mc.player.setPitch(originalPitch);
        }

        pendingSwapBack = true;
        swapTimer.reset();
    }

    private ThrowInfo updateThrowInfo() {
        ItemStack offhand = mc.player.getOffHandStack();
        if (isThrowable(offhand)) {
            return new ThrowInfo(Hand.OFF_HAND, -1);
        }

        int selected = mc.player.getInventory().getSelectedSlot();
        ItemStack mainhand = mc.player.getInventory().getStack(selected);
        if (isThrowable(mainhand)) {
            return new ThrowInfo(Hand.MAIN_HAND, selected);
        }

        for (int hotbar = 0; hotbar < 9; hotbar++) {
            ItemStack stack = mc.player.getInventory().getStack(hotbar);
            if (isThrowable(stack)) {
                return new ThrowInfo(Hand.MAIN_HAND, hotbar);
            }
        }
        return null;
    }

    private boolean isThrowable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        // Check for throwable snowballs, eggs
        if (item instanceof SnowballItem || item instanceof EggItem) {
            return true;
        }
        return false;
    }

    private boolean canRotate(Hand hand) {
        if (mc.player.isUsingItem()) {
            return false;
        }
        ItemStack stack = hand == Hand.MAIN_HAND ? mc.player.getMainHandStack() : mc.player.getOffHandStack();
        if (stack.isEmpty()) {
            return true;
        }
        Item item = stack.getItem();
        if (item instanceof EnderPearlItem) {
            return false;
        }
        if (item instanceof BowItem) {
            return false;
        }
        if (item instanceof PotionItem || item instanceof SplashPotionItem || item instanceof LingeringPotionItem) {
            return false;
        }
        return !stack.contains(DataComponentTypes.FOOD);
    }

    private boolean isFacing(Rotation target) {
        Rotation current = Managers.ROTATION.rotations;
        if (current == null) {
            current = new Rotation(mc.player.getYaw(), mc.player.getPitch());
        }
        float yawDiff = Math.abs(MathHelper.wrapDegrees(current.yaw - target.yaw));
        float pitchDiff = Math.abs(MathHelper.wrapDegrees(current.pitch - target.pitch));
        return yawDiff <= 10.0f && pitchDiff <= 10.0f;
    }

    private LivingEntity getClosestEnemy(double minRange, double maxRange) {
        return mc.world.getPlayers().stream()
                .filter(p -> p != mc.player)
                .filter(p -> !p.isDead())
                .filter(p -> !p.isCreative() && !p.isSpectator())
                .filter(p -> !Managers.FRIEND.isFriend(p.getName().getString()))
                .filter(p -> {
                    double dist = mc.player.distanceTo(p);
                    return dist >= minRange && dist <= maxRange;
                })
                .min(Comparator.comparingDouble(p -> mc.player.distanceTo(p)))
                .orElse(null);
    }

    private Rotation getRotationToEntity(LivingEntity target) {
        Vec3d origin = mc.player.getEyePos().add(0.0, -0.1, 0.0);
        Box targetBox = target.getBoundingBox();
        Vec3d targetCenter = new Vec3d((targetBox.minX + targetBox.maxX) * 0.5, (targetBox.minY + targetBox.maxY) * 0.5, (targetBox.minZ + targetBox.maxZ) * 0.5);
        int maxTicks = MathHelper.clamp((int) Math.ceil(mc.player.distanceTo(target) / 0.2), 8, 60);

        Rotation bestRotation = null;
        double bestError = Double.MAX_VALUE;

        for (int ticks = 1; ticks <= maxTicks; ticks++) {
            // Simplified motion prediction (linear)
            Vec3d targetMotion = target.getVelocity().multiply(ticks);
            Vec3d predictedCenter = targetCenter.add(targetMotion);
            Box predictedBox = targetBox.offset(targetMotion);

            Rotation initial = rotationToPoint(origin, predictedCenter);
            Rotation refined = refineRotation(origin, predictedCenter, predictedBox, ticks, initial);
            double error = simulateError(origin, refined.yaw, refined.pitch, predictedBox, ticks);

            if (error < bestError) {
                bestError = error;
                bestRotation = refined;
                if (bestError <= 1.0E-4) {
                    break;
                }
            }
        }

        if (bestRotation == null) {
            return rotationToPoint(origin, targetCenter);
        }
        return bestRotation;
    }

    private Rotation refineRotation(Vec3d origin, Vec3d targetCenter, Box targetBox, int ticks, Rotation initial) {
        float bestYaw = initial.yaw;
        float bestPitch = initial.pitch;
        double bestError = simulateError(origin, bestYaw, bestPitch, targetBox, ticks);
        float stepYaw = 4.0f;
        float stepPitch = 4.0f;

        for (int i = 0; i < 6; i++) {
            float baseYaw = bestYaw;
            float basePitch = bestPitch;
            for (int yawStep = -1; yawStep <= 1; yawStep++) {
                for (int pitchStep = -1; pitchStep <= 1; pitchStep++) {
                    float yaw = baseYaw + stepYaw * yawStep;
                    float pitch = MathHelper.clamp(basePitch + stepPitch * pitchStep, -89.0f, 89.0f);
                    double error = simulateError(origin, yaw, pitch, targetBox, ticks);
                    if (error < bestError) {
                        bestError = error;
                        bestYaw = yaw;
                        bestPitch = pitch;
                    }
                }
            }
            stepYaw *= 0.5f;
            stepPitch *= 0.5f;
        }

        return new Rotation(MathHelper.wrapDegrees(bestYaw), MathHelper.clamp(bestPitch, -89.0f, 89.0f));
    }

    private double simulateError(Vec3d origin, float yaw, float pitch, Box targetBox, int ticks) {
        Vec3d pos = origin;
        Vec3d velocity = getThrowVelocity(yaw, pitch);

        for (int i = 0; i < ticks; i++) {
            pos = pos.add(velocity);
            double drag = isWater(pos) ? 0.8 : 0.99;
            velocity = velocity.multiply(drag);
            velocity = velocity.add(0.0, -0.03, 0.0);
        }

        return distanceSquaredToBox(pos, targetBox);
    }

    private Vec3d getThrowVelocity(float yaw, float pitch) {
        float yawRad = yaw * ((float) Math.PI / 180.0F);
        float pitchRad = pitch * ((float) Math.PI / 180.0F);
        float x = -MathHelper.sin(yawRad) * MathHelper.cos(pitchRad);
        float y = -MathHelper.sin(pitchRad);
        float z = MathHelper.cos(yawRad) * MathHelper.cos(pitchRad);
        Vec3d velocity = new Vec3d(x, y, z).normalize().multiply(1.5f); // Potion speed is roughly 1.5? (Reference code used 1.5)
        Vec3d movement = mc.player.getVelocity(); // Use getVelocity() instead of getMovement()
        return velocity.add(movement.x, mc.player.isOnGround() ? 0.0 : movement.y, movement.z);
    }

    private Rotation rotationToPoint(Vec3d origin, Vec3d target) {
        Vec3d diff = target.subtract(origin);
        double distance = Math.hypot(diff.x, diff.z);
        float yaw = (float) (MathHelper.atan2(diff.z, diff.x) * (180 / Math.PI)) - 90.0f;
        float pitch = (float) (-(MathHelper.atan2(diff.y, distance) * (180 / Math.PI)));
        return new Rotation(yaw, pitch);
    }

    private double distanceSquaredToBox(Vec3d point, Box box) {
        double x = MathHelper.clamp(point.x, box.minX, box.maxX);
        double y = MathHelper.clamp(point.y, box.minY, box.maxY);
        double z = MathHelper.clamp(point.z, box.minZ, box.maxZ);
        double dx = point.x - x;
        double dy = point.y - y;
        double dz = point.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private boolean isWater(Vec3d pos) {
        BlockPos blockPos = new BlockPos((int)Math.floor(pos.x), (int)Math.floor(pos.y), (int)Math.floor(pos.z));
        return mc.world.getBlockState(blockPos).getBlock() == Blocks.WATER;
    }

    private record ThrowInfo(Hand hand, int hotbarSlot) {}
}


