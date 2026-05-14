package dev.mzc.client.module.impl.combat;

import dev.mzc.client.Sakura;
import dev.mzc.client.auth.UserRole;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.manager.Managers;
import dev.mzc.client.manager.impl.RotationManager.Priority;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.player.InvUtil;
import dev.mzc.client.utils.TranslationManager;
import dev.mzc.client.utils.rotation.MovementFix;
import dev.mzc.client.utils.vector.Rotation;
import dev.mzc.client.utils.time.TimerUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import dev.mzc.client.values.impl.RangeValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.*;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;

public class AutoRod extends Module {
    public enum Mode {
        Normal(),
        WeaponTrigger();
        Mode() {
        }
        
        @Override
        public String toString() {
            return TranslationManager.get(TranslationManager.enumKey(this), name());
        }
    }

    public AutoRod() {
        super("AutoRod", Category.Combat);
        this.setType(ModuleType.Safe);
        this.setRequiredRole(UserRole.SUPER_VIP);
    }

    private final EnumValue<Mode> mode = new EnumValue<>("Mode", Mode.Normal);
    private final RangeValue<Double> range = new RangeValue<>("Range", 5.5, 9.0, 0.0, 20.0, 0.25, () -> mode.is(Mode.Normal));
    private final RangeValue<Integer> delay = new RangeValue<>("Delay", 100, 300, 0, 1000, 10, () -> mode.is(Mode.Normal));
    private final NumberValue<Integer> switchDelay = new NumberValue<>("Switch Delay", 0, 0, 500, 10);
    private final NumberValue<Integer> retractDelay = new NumberValue<>("Retract Delay", 100, 0, 500, 10);
    private final BoolValue autoRotate = new BoolValue("Auto Rotate", true, () -> mode.is(Mode.Normal));
    private final BoolValue wallCheck = new BoolValue("Wall Check", true, () -> mode.is(Mode.Normal));
    private final NumberValue<Integer> rotationSpeed = new NumberValue<>("Rotation Speed", 10, 1, 10, 1, () -> autoRotate.get() && mode.is(Mode.Normal));
    private final NumberValue<Integer> rotationBackSpeed = new NumberValue<>("Rotation Back Speed", 10, 0, 10, 1, () -> autoRotate.get() && mode.is(Mode.Normal));

    private ThrowInfo pendingPlan;
    private Rotation pendingRotation;
    
    private boolean isRetracting = false;
    private boolean wasSwapped = false; // Tracks if we swapped slot for the current throw
    private boolean waitingForSwitch = false;

    private boolean weaponTriggerActive = false;
    private int originalSlot = -1;

    private final TimerUtil timer = new TimerUtil();
    private final TimerUtil retractTimer = new TimerUtil();
    private final TimerUtil switchTimer = new TimerUtil();

    @Override
    public void onEnable() {
        pendingPlan = null;
        pendingRotation = null;
        isRetracting = false;
        wasSwapped = false;
        waitingForSwitch = false;
        weaponTriggerActive = false;
        originalSlot = -1;

        timer.reset();
        retractTimer.reset();
        switchTimer.reset();
    }

    @Override
    public void onDisable() {
        pendingPlan = null;
        pendingRotation = null;
        isRetracting = false;
        wasSwapped = false;
        waitingForSwitch = false;
        weaponTriggerActive = false;
        originalSlot = -1;
        timer.reset();
        retractTimer.reset();
        switchTimer.reset();
        
        // Auto retract if hook is out? 
        // Optional: if (mc.player != null && mc.player.fishHook != null) ...
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        if (mode.is(Mode.WeaponTrigger)) {
            handleWeaponTriggerMode();
            return;
        }

        // Handle Retraction Phase
        if (isRetracting) {
            if (retractTimer.passedMS(retractDelay.get())) {
                if (wasSwapped) {
                    InvUtil.swapBack();
                    wasSwapped = false;
                } else {
                    // If we didn't swap, we need to right click again to retract
                    // Ensure we are holding the rod
                    if (isHoldingRod()) {
                         mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND); // Assuming main hand for simplicity, or track hand
                         mc.player.swingHand(Hand.MAIN_HAND);
                    }
                }
                isRetracting = false;
                timer.reset(); // Start cooldown for next throw
            }
            return; // Don't look for new targets while retracting
        }

        // If we have a hook out but we are not in isRetracting state, 
        // it means it's a lingering hook from manual usage or interrupted state.
        // We should retract it before throwing a new one.
        if (mc.player.fishHook != null) {
            if (timer.passedMS(200)) {
                 // Try to retract
                 if (isHoldingRod()) {
                     mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                     mc.player.swingHand(Hand.MAIN_HAND);
                     timer.reset();
                 }
            }
            return;
        }

        Rotation rotation;

        if (pendingPlan != null) {
            boolean aligned = pendingRotation == null || isFacing(pendingRotation);

            if (aligned) {
                if (waitingForSwitch) {
                    if (switchTimer.passedMS(switchDelay.get())) {
                        Rotation backRotation = new Rotation(mc.player.getYaw(), mc.player.getPitch());
                        performThrow(pendingPlan, pendingRotation);
                        
                        pendingPlan = null;
                        pendingRotation = null;
                        waitingForSwitch = false;
                        
                        if (autoRotate.get()) {
                            Managers.ROTATION.setRotations(backRotation, rotationBackSpeed.get(), MovementFix.NORMAL, Priority.Highest);
                        }
                    }
                } else {
                    if (pendingPlan.hand == Hand.MAIN_HAND && mc.player.getInventory().getSelectedSlot() != pendingPlan.hotbarSlot) {
                        InvUtil.swap(pendingPlan.hotbarSlot, true);
                        wasSwapped = true;
                        if (switchDelay.get() > 0) {
                            waitingForSwitch = true;
                            switchTimer.reset();
                            return;
                        }
                    } else {
                        wasSwapped = false; // Reset if no swap needed
                    }

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
                    pendingPlan = plan;
                    pendingRotation = null;
                    waitingForSwitch = false;
                }
            }
            // timer.reset();
        }
    }

    private void performThrow(ThrowInfo plan, Rotation rotation) {
        float originalYaw = mc.player.getYaw();
        float originalPitch = mc.player.getPitch();
        if (rotation != null) {
            mc.player.setYaw(rotation.yaw);
            mc.player.setPitch(rotation.pitch);
        }
        
        // Slot swap handled in logic above

        mc.interactionManager.interactItem(mc.player, plan.hand);
        mc.player.swingHand(plan.hand);

        if (rotation != null) {
            mc.player.setYaw(originalYaw);
            mc.player.setPitch(originalPitch);
        }

        isRetracting = true;
        retractTimer.reset();
    }

    private boolean isHoldingRod() {
        return mc.player.getMainHandStack().getItem() instanceof FishingRodItem || 
               mc.player.getOffHandStack().getItem() instanceof FishingRodItem;
    }

    private ThrowInfo updateThrowInfo() {
        ItemStack offhand = mc.player.getOffHandStack();
        if (isRod(offhand)) {
            return new ThrowInfo(Hand.OFF_HAND, -1);
        }

        int selected = mc.player.getInventory().getSelectedSlot();
        ItemStack mainhand = mc.player.getInventory().getStack(selected);
        if (isRod(mainhand)) {
            return new ThrowInfo(Hand.MAIN_HAND, selected);
        }

        for (int hotbar = 0; hotbar < 9; hotbar++) {
            ItemStack stack = mc.player.getInventory().getStack(hotbar);
            if (isRod(stack)) {
                return new ThrowInfo(Hand.MAIN_HAND, hotbar);
            }
        }
        return null;
    }

    private boolean isRod(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof FishingRodItem;
    }

    private boolean canRotate(Hand hand) {
        if (mc.player.isUsingItem()) {
            return false;
        }
        // AutoThrow had some checks here, mostly relevant for other items.
        // For rod, we just check if we are not eating/using something else.
        return true; 
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
        Vec3d velocity = new Vec3d(x, y, z).normalize().multiply(1.5f); // Rod velocity roughly 1.5? (Snowball is 1.5)
        // Fishing bobber velocity is slightly different but 1.5 is a good approximation for prediction
        Vec3d movement = mc.player.getVelocity(); 
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

    // Flag to ensure we don't start retracting until we've confirmed the throw (or at least waited a tick)
    private boolean waitingForThrow = false;

    private void handleWeaponTriggerMode() {
        if (weaponTriggerActive) {
            // Step 2: Throw (after 1 tick of swap if needed)
            if (waitingForThrow) {
                 mc.interactionManager.interactItem(mc.player, pendingPlan.hand);
                 mc.player.swingHand(pendingPlan.hand);
                 waitingForThrow = false;
                 isRetracting = true;
                 retractTimer.reset();
                 return;
            }

            // Step 3: Retract
            if (isRetracting) {
                // Wait for the delay
                if (!retractTimer.passedMS(retractDelay.get())) {
                    return; // Wait
                }
                
                // Delay passed, execute retraction
                if (mc.player.fishHook != null) {
                    if (mc.player.getInventory().getSelectedSlot() != pendingPlan.hotbarSlot) {
                        InvUtil.swap(pendingPlan.hotbarSlot, true);
                    }
                    
                    mc.player.swingHand(Hand.MAIN_HAND);
                    mc.interactionManager.interactItem(mc.player, pendingPlan.hand);
                }
                
                isRetracting = false;
                
                if (originalSlot != -1) {
                    InvUtil.swap(originalSlot, true);
                }
                
                weaponTriggerActive = false;
                pendingPlan = null;
                originalSlot = -1;
            }
            return;
        }
        
        if (mc.options.useKey.isPressed()) {
            ItemStack mainHand = mc.player.getMainHandStack();
            if (isWeapon(mainHand)) {
                ThrowInfo rodInfo = findRod();
                if (rodInfo != null) {
                    originalSlot = mc.player.getInventory().getSelectedSlot();
                    pendingPlan = rodInfo;
                    weaponTriggerActive = true;
                    
                    // Step 1: Switch
                    if (mc.player.getInventory().getSelectedSlot() != rodInfo.hotbarSlot) {
                        InvUtil.swap(rodInfo.hotbarSlot, true);
                    }
                    
                    // Delay throw by 1 tick to ensure item switch is processed
                    waitingForThrow = true;
                    
                    mc.options.useKey.setPressed(false);
                }
            }
        }
    }

    private boolean isWeapon(ItemStack stack) {
        return stack.isIn(ItemTags.SWORDS) || stack.getItem() instanceof AxeItem;
    }
    
    private ThrowInfo findRod() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof FishingRodItem) {
                return new ThrowInfo(Hand.MAIN_HAND, i);
            }
        }
        // Offhand check
        if (mc.player.getOffHandStack().getItem() instanceof FishingRodItem) {
            return new ThrowInfo(Hand.OFF_HAND, -1);
        }
        return null;
    }

    private record ThrowInfo(Hand hand, int hotbarSlot) {}
}


