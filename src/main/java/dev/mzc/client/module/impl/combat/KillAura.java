package dev.mzc.client.module.impl.combat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.mzc.client.Sakura;
import dev.mzc.client.manager.Managers;
import dev.mzc.client.manager.impl.RotationManager;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.utils.color.ColorUtil;
import dev.mzc.client.utils.render.Render3DUtil;
import dev.mzc.client.utils.rotation.MovementFix;
import dev.mzc.client.utils.rotation.RotationUtil;
import dev.mzc.client.utils.vector.Rotation;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.packet.PacketEvent;
import dev.mzc.client.events.render.Render3DEvent;
import dev.mzc.client.events.EventType;
import dev.mzc.client.events.entity.AttackEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.io.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class KillAura extends Module {

    public enum RotationMode { 
        Slide, Resolver, Snap, Neuro, Packet, Polar, Grim, Smooth, HolyWorld, NoRot,
        Legit, LonyGrief, Universal
    }
    public enum SprintResetMode { Normal, Legit, HvH, Packet }
    public enum AttackMode { V1_8, V1_9 }
    public enum MaceKillMode { Off, Vanilla, Aggressive }

    private static KillAura INSTANCE;

    private final EnumValue<AttackMode> attackMode = new EnumValue<>("Attack Mode", AttackMode.V1_9);
    private final NumberValue<Double> attackRange = new NumberValue<>("Attack Range", 3.2, 2.6, 6.0, 0.1);
    private final NumberValue<Double> fov = new NumberValue<>("FOV", 360.0, 30.0, 360.0, 1.0);
    private final EnumValue<RotationMode> rotationMode = new EnumValue<>("Rotation Mode", RotationMode.Resolver);

    private final BoolValue elytraPrediction = new BoolValue("Elytra Prediction", true);
    private final NumberValue<Integer> forwardValue = new NumberValue<>("Forward Value", 3, 1, 6, 1);

    private final EnumValue<SprintResetMode> sprintReset = new EnumValue<>("Sprint Reset", SprintResetMode.Packet);

    private final BoolValue targetPlayers = new BoolValue("Players", true);
    private final BoolValue targetMobs = new BoolValue("Mobs", true);
    private final BoolValue targetAnimals = new BoolValue("Animals", false);
    private final BoolValue targetFriends = new BoolValue("Friends", false);

    private final BoolValue onlyCritical = new BoolValue("Only Critical", true);
    private final BoolValue smartCrits = new BoolValue("Smart Crits", true);
    private final BoolValue maceSwap = new BoolValue("Mace Swap", false);
    private final BoolValue dynamicCooldown = new BoolValue("Dynamic Cooldown", true);
    private final BoolValue breakShield = new BoolValue("Break Shield", true);
    private final BoolValue noAttackWhenEat = new BoolValue("No Attack When Eat", true);
    private final BoolValue ignoreWalls = new BoolValue("Ignore Walls", false);
    private final BoolValue renderTarget = new BoolValue("Target ESP", true);
    private final EnumValue<MaceKillMode> maceKillMode = new EnumValue<>("MaceKill Mode", MaceKillMode.Off);
    private final NumberValue<Integer> maceKillHeight = new NumberValue<>("MaceKill Height", 15, 5, 50, 1, () -> maceKillMode.get() != MaceKillMode.Off);
    private final NumberValue<Integer> maceKillPackets = new NumberValue<>("MaceKill Packets", 10, 3, 30, 1, () -> maceKillMode.get() != MaceKillMode.Off);

    // New rotation mode settings
    private final NumberValue<Double> universalYawSpeed = new NumberValue<>("Universal Yaw Speed", 30.0, 5.0, 60.0, 1.0, () -> rotationMode.is(RotationMode.Universal));
    private final NumberValue<Double> universalPitchSpeed = new NumberValue<>("Universal Pitch Speed", 20.0, 5.0, 40.0, 1.0, () -> rotationMode.is(RotationMode.Universal));
    private final BoolValue spookySkeletons = new BoolValue("Spooky Skeletons", false, () -> rotationMode.is(RotationMode.Universal));
    private final BoolValue hulyMode = new BoolValue("Huly Mode", false, () -> rotationMode.is(RotationMode.Universal) && spookySkeletons.get());

    private LivingEntity target;
    private LivingEntity lastTarget;
    private Vec3d currentAimPoint;
    private int slideIndex;
    private float visualYaw, visualPitch;
    private boolean visualInitialized;
    private long lastClickTime = System.currentTimeMillis();
    private int clickCounter;
    private final NeuroEngine neuro = new NeuroEngine();
    private Rotation lastOut, lastDesired;
    private boolean attackedThisTick;
    private boolean maceKillActive = false;
    private int maceKillState = 0;

    // New rotation mode fields
    private float legitLastYawStep;
    private float legitLastPitchStep;
    private float legitDriftYaw;
    private float legitDriftPitch;
    private long legitNextDriftUpdate;

    private float holyWorldDriftYaw;
    private float holyWorldDriftPitch;
    private long holyWorldNextOffsetRefresh;

    private float lonyGriefAcceleration;
    private boolean lonyGriefBackwardsRotating;
    private float lonyGriefLastYaw;
    private float lonyGriefLastPitch;

    private float universalLastYawDelta;
    private float universalLastPitchDelta;
    private int universalLastPitchChangeDirection;
    private int universalTicksSinceSwitchedDirection;
    private int universalJopa;

    public KillAura() {
        super("KillAura", Category.Combat);
        INSTANCE = this;
    }

    public static KillAura getInstance() { return INSTANCE; }
    
    public LivingEntity getTarget() { return target; }

    @Override
    protected void onDisable() {
        target = null;
        lastTarget = null;
        currentAimPoint = null;
        visualInitialized = false;
        lastOut = null;
        lastDesired = null;
        maceKillState = 0;
        maceKillActive = false;
        
        // Reset new rotation mode fields
        legitLastYawStep = 0;
        legitLastPitchStep = 0;
        legitDriftYaw = 0;
        legitDriftPitch = 0;
        legitNextDriftUpdate = 0;
        
        holyWorldDriftYaw = 0;
        holyWorldDriftPitch = 0;
        holyWorldNextOffsetRefresh = 0;
        
        lonyGriefAcceleration = 0;
        lonyGriefBackwardsRotating = false;
        lonyGriefLastYaw = 0;
        lonyGriefLastPitch = 0;
        
        universalLastYawDelta = 0;
        universalLastPitchDelta = 0;
        universalLastPitchChangeDirection = 0;
        universalTicksSinceSwitchedDirection = 0;
        universalJopa = 0;
    }

    @EventHandler
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.RECEIVE) return;
        Packet<?> packet = event.getPacket();
        if (packet instanceof HandSwingC2SPacket || packet instanceof UpdateSelectedSlotC2SPacket) {
            recalculateClickWindow();
        }
    }

    @EventHandler
    public void onPreTick(TickEvent.Pre event) {
        if (nullCheck()) return;
        target = selectTarget();
        if (target == null) {
            visualInitialized = false;
            lastOut = null;
            lastDesired = null;
            return;
        }

        AimResult aim = selectAimPoint(target);
        Vec3d aimVec = aim.point;

        if (elytraPrediction.get() && (isFlying() || target.isGliding())) {
            Vec3d velocity = target.getVelocity();
            aimVec = aimVec.add(velocity.x * forwardValue.get(), velocity.y * (forwardValue.get() * 0.5), velocity.z * forwardValue.get());
        }

        currentAimPoint = aimVec;
        Rotation desired = angleTo(aimVec);

        Rotation out = switch (rotationMode.get()) {
            case Slide -> applySlideRotation(desired);
            case Resolver -> applyResolverRotation(desired);
            case Snap -> applySnapRotation(desired);
            case Neuro -> neuro.apply(desired);
            case Packet, Grim -> desired;
            case HolyWorld -> applyHolyWorldRotationActual(desired, target);
            case Polar -> applyPolarRotation(desired, target);
            case Smooth -> applySmoothRotation(desired);
            case NoRot -> Managers.ROTATION.getRotation();
            case Legit -> applyLegitRotation(desired, target);
            case LonyGrief -> applyLonyGriefRotation(desired, target);
            case Universal -> applyUniversalRotation(desired, target);
        };

        out = applyGcdPatch(out);
        
        lastOut = out;
        lastDesired = desired;

        RotationManager.Priority priority = (rotationMode.is(RotationMode.Packet) || rotationMode.is(RotationMode.Polar) || rotationMode.is(RotationMode.Grim) || rotationMode.is(RotationMode.HolyWorld) || rotationMode.is(RotationMode.Legit) || rotationMode.is(RotationMode.LonyGrief) || rotationMode.is(RotationMode.Universal)) ? RotationManager.Priority.Highest : RotationManager.Priority.High;
        
        MovementFix fix = isFlying() ? MovementFix.OFF : MovementFix.GRIM;
        Managers.ROTATION.setRotations(out, 100, fix, priority);

        if (!visualInitialized) { visualYaw = out.yaw; visualPitch = out.pitch; visualInitialized = true; }
        else { visualYaw = MathHelper.lerp(0.55f, visualYaw, out.yaw); visualPitch = MathHelper.lerp(0.55f, visualPitch, out.pitch); }
    }

    @EventHandler
    public void onPostTick(TickEvent.Post event) {
        if (nullCheck()) return;
        
        attackedThisTick = false;
        if (target != null) {
            if (maceKillMode.get() != MaceKillMode.Off && isFlying()) {
                executeMaceKill(target);
            } else {
                tryAttack(target);
            }
        }
        
        if (lastOut != null && lastDesired != null) {
            neuro.collectLiveSample(lastOut, lastDesired, attackedThisTick);
        }
        neuro.tick();
        
        lastTarget = target;
    }

    private Rotation applyPolarRotation(Rotation targetAngle, Entity entity) {
        Rotation currentAngle = Managers.ROTATION.getRotation();
        
        float yawDelta = MathHelper.wrapDegrees(targetAngle.yaw - currentAngle.yaw);
        float pitchDelta = targetAngle.pitch - currentAngle.pitch;
        
        float rotationDiff = (float) Math.hypot(yawDelta, pitchDelta);
        if (rotationDiff < 0.05f) rotationDiff = 1.0f;

        long time = System.currentTimeMillis();
        float parallelYaw = (float) Math.sin(time / 90.0) * 1.5f;
        float parallelPitch = (float) Math.cos(time / 110.0) * 1.2f;

        if (clickCounter % 2 == 0) {
            parallelYaw += (ThreadLocalRandom.current().nextFloat() - 0.5f) * 1.2f;
            parallelPitch += (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.9f;
        }

        float speed = 0.45f;
        float nextYaw = currentAngle.yaw + MathHelper.clamp(yawDelta, -rotationDiff * speed, rotationDiff * speed) + parallelYaw;
        float nextPitch = MathHelper.clamp(currentAngle.pitch + MathHelper.clamp(pitchDelta, -rotationDiff * speed, rotationDiff * speed) + parallelPitch, -90, 90);

        return new Rotation(nextYaw, nextPitch);
    }

    private Rotation applySmoothRotation(Rotation targetAngle) {
        Rotation currentAngle = Managers.ROTATION.getRotation();
        float yawDelta = MathHelper.wrapDegrees(targetAngle.yaw - currentAngle.yaw);
        float pitchDelta = targetAngle.pitch - currentAngle.pitch;
        float speed = 0.75f;
        float newYaw = currentAngle.yaw + MathHelper.clamp(yawDelta, -speed * 180f, speed * 180f);
        float newPitch = MathHelper.clamp(currentAngle.pitch + MathHelper.clamp(pitchDelta, -speed * 90f, speed * 90f), -90f, 90f);
        return new Rotation(newYaw, newPitch);
    }

    // Legit Rotation - очень плавная и легитная ротация
    private Rotation applyLegitRotation(Rotation targetRotation, Entity entity) {
        Rotation currentRotation = Managers.ROTATION.getRotation();
        
        float yawDelta = MathHelper.wrapDegrees(targetRotation.yaw - currentRotation.yaw);
        float pitchDelta = targetRotation.pitch - currentRotation.pitch;

        updateLegitDrift();

        int age = mc.player != null ? mc.player.age : 0;
        float yawSpeed = MathHelper.clamp(2.8f + Math.abs(yawDelta) * 0.22f, 1.8f, 15.0f);
        float pitchSpeed = MathHelper.clamp(1.8f + Math.abs(pitchDelta) * 0.16f, 1.0f, 9.5f);

        float yawNoise = (float) Math.sin(age * 0.33f + (entity != null ? entity.getId() * 0.11f : 0.0f)) * 0.16f + legitDriftYaw;
        float pitchNoise = (float) Math.cos(age * 0.27f + (entity != null ? entity.getId() * 0.07f : 0.0f)) * 0.09f + legitDriftPitch;

        float yawStep = calculateLegitStep(yawDelta + yawNoise, yawSpeed, 0.56f);
        float pitchStep = calculateLegitStep(pitchDelta + pitchNoise, pitchSpeed, 0.48f);

        yawStep = MathHelper.lerp(0.38f, legitLastYawStep, yawStep);
        pitchStep = MathHelper.lerp(0.32f, legitLastPitchStep, pitchStep);

        if (Math.abs(yawStep) > Math.abs(yawDelta)) {
            yawStep = yawDelta;
        }

        if (Math.abs(pitchStep) > Math.abs(pitchDelta)) {
            pitchStep = pitchDelta;
        }

        legitLastYawStep = yawStep;
        legitLastPitchStep = pitchStep;

        return new Rotation(
                currentRotation.yaw + yawStep,
                MathHelper.clamp(currentRotation.pitch + pitchStep, -90.0f, 90.0f)
        );
    }

    private float calculateLegitStep(float delta, float maxStep, float smoothing) {
        if (Math.abs(delta) < 0.001f) {
            return 0.0f;
        }

        float step = MathHelper.clamp(delta * smoothing, -maxStep, maxStep);
        if (Math.abs(delta) < maxStep * 0.55f) {
            step = delta * 0.8f;
        }

        return step;
    }

    private void updateLegitDrift() {
        long time = System.currentTimeMillis();
        if (time < legitNextDriftUpdate) {
            legitDriftYaw *= 0.92f;
            legitDriftPitch *= 0.92f;
            return;
        }

        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        legitDriftYaw = random.nextFloat() * 0.28f - 0.14f;
        legitDriftPitch = random.nextFloat() * 0.18f - 0.09f;
        legitNextDriftUpdate = time + random.nextLong(85L, 165L);
    }

    // Holy World Rotation - ротация с орбитальным дрейфом
    private Rotation applyHolyWorldRotationActual(Rotation targetRotation, Entity entity) {
        Rotation currentRotation = Managers.ROTATION.getRotation();
        
        refreshHolyWorldOffsets();

        float yawDelta = MathHelper.wrapDegrees(targetRotation.yaw - currentRotation.yaw);
        float pitchDelta = targetRotation.pitch - currentRotation.pitch;

        float yawSpeed = MathHelper.clamp(10.0f + Math.abs(yawDelta) * 0.38f, 7.0f, 34.0f);
        float pitchSpeed = MathHelper.clamp(4.5f + Math.abs(pitchDelta) * 0.24f, 3.0f, 18.0f);

        int age = mc.player != null ? mc.player.age : 0;
        float orbitYaw = (float) Math.sin(age * 0.21f) * 1.35f + holyWorldDriftYaw;
        float orbitPitch = (float) Math.cos(age * 0.17f) * 0.55f + holyWorldDriftPitch;

        return new Rotation(
                currentRotation.yaw + MathHelper.clamp(yawDelta + orbitYaw, -yawSpeed, yawSpeed),
                MathHelper.clamp(currentRotation.pitch + MathHelper.clamp(pitchDelta + orbitPitch, -pitchSpeed, pitchSpeed), -90.0f, 90.0f)
        );
    }

    private void refreshHolyWorldOffsets() {
        long now = System.currentTimeMillis();
        if (now < holyWorldNextOffsetRefresh) {
            holyWorldDriftYaw *= 0.94f;
            holyWorldDriftPitch *= 0.94f;
            return;
        }

        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        holyWorldDriftYaw = random.nextFloat() * 1.35f - 0.675f;
        holyWorldDriftPitch = random.nextFloat() * 0.4f - 0.2f;
        holyWorldNextOffsetRefresh = now + random.nextLong(120L, 240L);
    }

    // Lony Grief Rotation - ротация с ускорением и jitter
    private Rotation applyLonyGriefRotation(Rotation targetRotation, Entity entity) {
        Rotation currentRotation = Managers.ROTATION.getRotation();

        if (lonyGriefLastYaw == 0.0f && lonyGriefLastPitch == 0.0f) {
            lonyGriefLastYaw = currentRotation.yaw;
            lonyGriefLastPitch = currentRotation.pitch;
        }

        boolean hasTarget = entity instanceof LivingEntity;
        boolean hasTrace = hasTarget && rayTrace((LivingEntity) entity, attackRange.get());

        float deltaYaw = MathHelper.wrapDegrees(targetRotation.yaw - lonyGriefLastYaw);
        float deltaPitch = targetRotation.pitch - lonyGriefLastPitch;

        // Gliding logic
        if (mc.player.isGliding()) {
            if (!lonyGriefBackwardsRotating) {
                lonyGriefAcceleration += 0.005F;
                if (lonyGriefAcceleration >= 0.13F) {
                    lonyGriefBackwardsRotating = true;
                }
            } else {
                if (lonyGriefAcceleration >= -0.02F) {
                    lonyGriefAcceleration -= 0.005F;
                }
                if (lonyGriefAcceleration <= -0.02F) {
                    lonyGriefBackwardsRotating = false;
                }
            }
        }
        // Logic when no trace
        else if (!hasTrace) {
            lonyGriefAcceleration += 0.0015F;
        }
        // Logic when trace exists
        else if (lonyGriefAcceleration > 0.0F) {
            lonyGriefAcceleration -= 0.01F;
        }

        float smooth = Math.max(lonyGriefAcceleration, 0.0F);

        // Smooth aiming
        float newYaw = lonyGriefLastYaw + deltaYaw * Math.min(Math.max(smooth, 0.0F), 1.0F);
        float newPitch = lonyGriefLastPitch + deltaPitch * Math.min(Math.max(smooth / 2.0F, 0.0F), 1.0F);

        // Add jitter
        newYaw += getLonyGriefJitter(mc.player.isGliding(), true);
        newPitch = MathHelper.clamp(newPitch + getLonyGriefJitter(mc.player.isGliding(), false), -89.0F, 89.0F);

        // Apply GCD
        Rotation smoothRotation = snapToGcdLony(lonyGriefLastYaw, lonyGriefLastPitch, new Rotation(newYaw, newPitch));

        // Update last values
        lonyGriefLastYaw = smoothRotation.yaw;
        lonyGriefLastPitch = smoothRotation.pitch;

        return smoothRotation;
    }

    private float getLonyGriefJitter(boolean isGliding, boolean isYaw) {
        float time = (float) (System.currentTimeMillis() % 10000L) / 1000.0F;
        float amplitude = isGliding ? 2.5F : 1.5F;

        if (isYaw) {
            return (float) Math.sin(time * 2F * Math.PI * 3F) * amplitude;
        } else {
            return (float) Math.cos(time * 2F * Math.PI * 2.5F) * amplitude;
        }
    }

    private Rotation snapToGcdLony(float lastYaw, float lastPitch, Rotation target) {
        float gcd = getGcd();

        float deltaYaw = target.yaw - lastYaw;
        float deltaPitch = target.pitch - lastPitch;

        float snappedDeltaYaw = (float) (Math.round(deltaYaw / gcd) * gcd);
        float snappedDeltaPitch = (float) (Math.round(deltaPitch / gcd) * gcd);

        return new Rotation(lastYaw + snappedDeltaYaw, lastPitch + snappedDeltaPitch);
    }

    // Universal Rotation - сложная адаптивная ротация
    private Rotation applyUniversalRotation(Rotation targetRotation, Entity entity) {
        Rotation currentRotation = Managers.ROTATION.getRotation();
        
        float yawDelta = MathHelper.wrapDegrees(targetRotation.yaw - currentRotation.yaw);
        float pitchDelta = targetRotation.pitch - currentRotation.pitch;

        float maxYawSpeed = universalYawSpeed.get().floatValue() / 3f;
        float maxPitchSpeed = universalPitchSpeed.get().floatValue() / 3f;

        if ((pitchDelta < 0.0f && this.universalLastPitchDelta > 0.0f) || (pitchDelta > 0.0f && this.universalLastPitchDelta < 0.0f)) {
            universalTicksSinceSwitchedDirection = 0;
        } else {
            ++universalTicksSinceSwitchedDirection;
        }

        boolean invalid = universalTicksSinceSwitchedDirection == 0 && Math.abs(pitchDelta) > 5.0f;
        if (invalid) {
            pitchDelta -= 1f;
            pitchDelta *= 0.3f;
            maxPitchSpeed *= 0.4f;
        }

        if (Math.abs(pitchDelta) < 0.05f) {
            pitchDelta -= (float) (Math.random() * 0.05f - 0.225f);
        }

        if (Math.abs(yawDelta - universalLastYawDelta) < 0.08f) {
            yawDelta -= (float) (Math.random() * 0.15f - 0.125f);
        }

        if (Math.abs(pitchDelta) < 0.01f) {
            pitchDelta -= (float) (Math.random() * 0.01f - 0.005f);
        }

        if (Math.abs(yawDelta) > 180.25f) {
            maxYawSpeed *= 0.8f;
        }

        if (Math.abs(yawDelta) > 15.0f && Math.abs(pitchDelta) < 0.1f) {
            maxYawSpeed *= 0.7f;
        }

        if (Math.abs(yawDelta) < 0.05f && Math.abs(pitchDelta) < 0.05f) {
            maxYawSpeed *= 1.1f;
            maxPitchSpeed *= 1.1f;
        }

        if (yawDelta > 1.25f && universalLastYawDelta > 1.25f) {
            yawDelta -= universalLastYawDelta;
            maxYawSpeed *= 3;
        }

        if (Math.abs(yawDelta) > 2.75f && Math.abs(pitchDelta) == 0.0f) {
            maxYawSpeed *= 0.8f;
            maxPitchSpeed *= 1.1f;
        }

        if (Math.abs(yawDelta) > 0.5f && Math.abs(pitchDelta) < 0.05f) {
            maxYawSpeed *= 0.7f;
            maxPitchSpeed *= 1.05f;
        }

        if (Math.abs(yawDelta) > 1.825f && Math.abs(pitchDelta) == 0.0f) {
            maxYawSpeed *= 0.6f;
            maxPitchSpeed *= 0.9f;
        }

        if (Math.abs(yawDelta) > 20.0f && Math.abs(pitchDelta) < 0.1f) {
            maxYawSpeed *= 0.5f;
            maxPitchSpeed *= 1.1f;
        }

        if (Math.abs(yawDelta) > 0.25f && Math.abs(pitchDelta) > 0.25f && Math.abs(pitchDelta) < 20.0f && Math.abs(yawDelta) < 20.0f) {
            maxYawSpeed *= 0.95f;
            maxPitchSpeed *= 0.85f;
        }

        if (Math.abs(yawDelta) > 0.1f && Math.abs(pitchDelta) > 0.1f && Math.abs(yawDelta) < 20.0f && Math.abs(pitchDelta) < 20.0f) {
            maxYawSpeed *= 0.9f;
            maxPitchSpeed *= 0.8f;
        }

        if (Math.abs(yawDelta) > 0.05f && Math.abs(pitchDelta) == 0.0f) {
            maxYawSpeed *= 0.8f;
            maxPitchSpeed *= 0.95f;
        }

        if (Math.abs(yawDelta) > 0.05f && Math.abs(pitchDelta) < 0.05f) {
            maxYawSpeed *= 0.85f;
            maxPitchSpeed *= 1.1f;
        }

        if (Math.abs(yawDelta) > 0.75f && Math.abs(pitchDelta) > 0.75f) {
            maxYawSpeed *= 0.8f;
            maxPitchSpeed *= 0.75f;
        }

        if (Math.abs(yawDelta) > 0.03f && Math.abs(pitchDelta) > 0.03f) {
            maxYawSpeed *= 0.9f;
            maxPitchSpeed *= 0.8f;
        }

        int currentPitchChangeDirection = pitchDelta > 0 ? 1 : -1;
        if (universalLastPitchChangeDirection != 0 && currentPitchChangeDirection != universalLastPitchChangeDirection) {
            maxPitchSpeed *= 0.2f;
        }
        universalLastPitchChangeDirection = currentPitchChangeDirection;

        boolean hasCollision = entity != null && hasCollisionWith(entity, 1f);
        boolean check = entity instanceof LivingEntity && rayTrace((LivingEntity) entity, attackRange.get());

        if (spookySkeletons.get()) {
            int maxJopa = 20;
            float deldeldel = !hulyMode.get() ? 30f : 13f;
            if (hasCollision) {
                pitchDelta /= deldeldel;
                yawDelta /= deldeldel;
                universalJopa = maxJopa;
            }

            if (!hulyMode.get() && !hasCollision && check){
                maxPitchSpeed *= 1.3f;
                maxYawSpeed *= 1.1f;
            } else if (!hulyMode.get() && !hasCollision){
                maxPitchSpeed *= 1.1f;
                maxYawSpeed *= 1.25f;
            }

            if (universalJopa-- > 0) {
                float superJopa = Math.max(1f, (universalJopa / (float) maxJopa) * 15f);
                yawDelta /= superJopa;
                pitchDelta /= superJopa;
            }
        }

        universalLastYawDelta = yawDelta;
        universalLastPitchDelta = pitchDelta;

        return new Rotation(
                currentRotation.yaw + MathHelper.clamp(yawDelta, -maxYawSpeed, maxYawSpeed),
                currentRotation.pitch + MathHelper.clamp(pitchDelta, -maxPitchSpeed, maxPitchSpeed)
        );
    }

    private boolean hasCollisionWith(Entity entity, float expand) {
        return mc.world.getBlockCollisions(mc.player, entity.getBoundingBox().expand(expand)).iterator().hasNext();
    }

    private boolean isFlying() {
        return mc.player.isGliding();
    }

    public LivingEntity getCurrentTarget() {
        return target;
    }

    public void startRecord() { neuro.startRecord(); }
    public void stopRecord() { neuro.stopRecord(); }
    public boolean saveRecord(String name) { return neuro.save(name); }
    public boolean loadRecord(String name) { return neuro.load(name); }
    public String recordDir() { return neuro.getDir(); }

    private LivingEntity selectTarget() {
        List<LivingEntity> candidates = new ArrayList<>();
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || !isValid(living)) continue;
            double dist = mc.player.getEyePos().distanceTo(closestPoint(mc.player.getEyePos(), living.getBoundingBox()));
            if (dist > attackRange.get()) continue;
            if (rotationDeltaTo(living) > fov.get()) continue;
            candidates.add(living);
        }
        candidates.sort(Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e)));
        return candidates.isEmpty() ? null : candidates.getFirst();
    }

    private boolean isValid(LivingEntity e) {
        if (e == mc.player || !e.isAlive()) return false;
        if (e instanceof PlayerEntity p) {
            if (!targetPlayers.get()) return false;
            if (Teams.getInstance() != null && Teams.getInstance().isTeammate(p)) return false;
            return targetFriends.get() || Managers.FRIEND == null || !Managers.FRIEND.isFriend(p.getName().getString());
        }
        return targetMobs.get();
    }

    private AimResult selectAimPoint(LivingEntity entity) {
        Box box = entity.getBoundingBox();
        if (rotationMode.is(RotationMode.Slide)) {
            List<Vec3d> points = sampleHitboxPoints(box);
            if (points.isEmpty()) return new AimResult(entity.getEyePos(), 0.5f);
            slideIndex = (slideIndex + 1) % points.size();
            return new AimResult(points.get(slideIndex), hitPartRatio(box, points.get(slideIndex)));
        }
        
        double heightFactor = 0.45 + (Math.sin(System.currentTimeMillis() / 400.0) * 0.3);
        Vec3d interpPoint = new Vec3d(
            MathHelper.lerp(0.5, box.minX, box.maxX),
            box.minY + (entity.getHeight() * heightFactor),
            MathHelper.lerp(0.5, box.minZ, box.maxZ)
        );
        return new AimResult(interpPoint, (float)heightFactor);
    }

    private List<Vec3d> sampleHitboxPoints(Box box) {
        List<Vec3d> out = new ArrayList<>();
        for (int xi : new int[]{0, 6}) {
            double x = MathHelper.lerp(xi / 6.0, box.minX + 0.01, box.maxX - 0.01);
            for (int zi : new int[]{0, 6}) {
                double z = MathHelper.lerp(zi / 6.0, box.minZ + 0.01, box.maxZ - 0.01);
                for (int yi = 0; yi < 9; yi++) {
                    double y = MathHelper.lerp(yi / 8.0, box.minY + 0.01, box.maxY - 0.01);
                    Vec3d p = new Vec3d(x, y, z);
                    if (isVisiblePoint(p) || ignoreWalls.get()) out.add(p);
                }
            }
        }
        return out;
    }

    private float hitPartRatio(Box box, Vec3d p) { return (float) MathHelper.clamp((p.y - box.minY) / Math.max(1.0E-4, box.getLengthY()), 0.0, 1.0); }

    private double rotationDeltaTo(LivingEntity entity) { return rotationDeltaTo(entity.getEyePos()); }
    private double rotationDeltaTo(Vec3d pos) {
        Rotation cur = Managers.ROTATION.getRotation();
        Rotation to = angleTo(pos);
        return Math.hypot(MathHelper.wrapDegrees(to.yaw - cur.yaw), to.pitch - cur.pitch);
    }

    private Rotation angleTo(Vec3d worldPoint) {
        Vec3d d = worldPoint.subtract(mc.player.getEyePos());
        float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
        return new Rotation(yaw, MathHelper.clamp(pitch, -90f, 90f));
    }

    private Rotation applySlideRotation(Rotation desired) {
        Rotation cur = Managers.ROTATION.getRotation();
        return new Rotation(cur.yaw + MathHelper.clamp(MathHelper.wrapDegrees(desired.yaw - cur.yaw), -70f, 70f), MathHelper.clamp(cur.pitch + MathHelper.clamp(desired.pitch - cur.pitch, -45f, 45f), -90, 90));
    }

    private Rotation applyResolverRotation(Rotation desired) {
        Rotation cur = Managers.ROTATION.getRotation();
        float yawStep = MathHelper.clamp(MathHelper.wrapDegrees(desired.yaw - cur.yaw), -90f, 90f);
        float pitchStep = MathHelper.clamp(desired.pitch - cur.pitch, -60f, 60f);
        return new Rotation(cur.yaw + yawStep + (float)(Math.sin(System.currentTimeMillis() / 30.0) * 2.5), MathHelper.clamp(cur.pitch + pitchStep + (float)(Math.cos(System.currentTimeMillis() / 40.0) * 1.5), -90, 90));
    }

    private Rotation applySnapRotation(Rotation desired) {
        Rotation cur = Managers.ROTATION.getRotation();
        return new Rotation(MathHelper.lerp(0.98f, cur.yaw, cur.yaw + MathHelper.wrapDegrees(desired.yaw - cur.yaw)), MathHelper.clamp(MathHelper.lerp(0.98f, cur.pitch, desired.pitch), -90, 90));
    }

    private Rotation applyGcdPatch(Rotation desired) {
        Rotation cur = Managers.ROTATION.getRotation();
        float gcd = getGcd(), dy = MathHelper.wrapDegrees(desired.yaw - cur.yaw), dp = desired.pitch - cur.pitch;
        return new Rotation(cur.yaw + Math.round(dy / gcd) * gcd, MathHelper.clamp(cur.pitch + Math.round(dp / gcd) * gcd, -90f, 90f));
    }

    private float getGcd() { float sens = sens(); float f = sens * 0.6f + 0.2f; return Math.max(1.0E-4f, f * f * f * 1.2f); }
    private float sens() { return mc.options.getMouseSensitivity().getValue().floatValue(); }

    private void tryAttack(LivingEntity tgt) {
        if (!canAttackNow()) return;
        if (!rayTrace(tgt, attackRange.get())) return;

        int maceSlot = -1;
        int oldSlot = mc.player.getInventory().getSelectedSlot();

        if (maceSwap.get() && mc.player.fallDistance > 1.3f) {
            maceSlot = getMaceSlot();
            if (maceSlot != -1) {
                mc.player.getInventory().setSelectedSlot(maceSlot);
                mc.interactionManager.syncSelectedSlot();
            }
        }

        Sakura.EVENT_BUS.post(new AttackEvent(tgt));

        boolean wasSprinting = mc.player.isSprinting();
        boolean shouldReset = wasSprinting && (mc.player.isOnGround() || sprintReset.is(SprintResetMode.HvH));

        if (shouldReset) {
            if (sprintReset.is(SprintResetMode.Packet)) {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            } else {
                mc.player.setSprinting(false);
            }
        }

        if (breakShield.get()) tryBreakShield(tgt);

        mc.interactionManager.attackEntity(mc.player, tgt);
        mc.player.swingHand(Hand.MAIN_HAND);
        attackedThisTick = true;

        if (shouldReset) {
            if (sprintReset.is(SprintResetMode.Packet)) {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
            } else {
                mc.player.setSprinting(true);
            }
        }

        if (maceSlot != -1) {
            mc.player.getInventory().setSelectedSlot(oldSlot);
            mc.interactionManager.syncSelectedSlot();
        }

        recalculateClickWindow();
    }

    private int getMaceSlot() {
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).getItem().getTranslationKey().toLowerCase().contains("mace")) return i;
        return -1;
    }

    private boolean isHoldingMace() {
        return mc.player.getMainHandStack().getItem().getTranslationKey().toLowerCase().contains("mace");
    }

    private boolean canAttackNow() {
        if (noAttackWhenEat.get() && mc.player.isUsingItem() && (mc.player.getActiveItem().getUseAction() == UseAction.EAT || mc.player.getActiveItem().getUseAction() == UseAction.DRINK)) return false;
        
        if (rotationMode.is(RotationMode.Neuro) && !neuro.shouldHit()) return false;

        if (!cooldownReady()) return false;
        
        if (!onlyCritical.get()) return true;

        if (isInWater() || hasLowCeiling() || mc.player.isClimbing() || mc.player.getAbilities().flying) return true;

        Criticals criticalsModule = Sakura.MODULES.getModule(Criticals.class);
        if (criticalsModule != null && criticalsModule.isEnabled()) {
            return mc.player.isOnGround();
        } else {
            boolean isFalling = mc.player.getVelocity().y < -0.05 && mc.player.fallDistance > 0.12;
            return isFalling && !mc.player.isOnGround();
        }
    }

    private boolean isInWater() { return mc.player.isTouchingWater() || mc.player.isSubmergedInWater(); }
    
    private boolean hasLowCeiling() {
        BlockPos pos = mc.player.getBlockPos().up(2);
        if (mc.world == null) return false;
        BlockState state = mc.world.getBlockState(pos);
        return !state.isAir() && !state.getCollisionShape(mc.world, pos).isEmpty();
    }

    private boolean cooldownReady() {
        if (isHoldingMace() && mc.player.fallDistance > 0) return System.currentTimeMillis() - lastClickTime >= 50L;
        if (attackMode.is(AttackMode.V1_8)) {
            // Enhanced 1.8 mode with adaptive CPS and micro-randomization
            // Base timing: 45-65ms (15-22 CPS) with dynamic adjustment
            long baseDelay = 45L + ThreadLocalRandom.current().nextLong(20L);
                
            // Adaptive slowdown if attacking same target for too long (anticheats watch for constant CPS)
            if (clickCounter > 20 && clickCounter % 10 == 0) {
                baseDelay += ThreadLocalRandom.current().nextLong(15, 30L); // occasional slowdown
            }
                
            // Micro-pause after every 5th click to mimic human patterns
            if (clickCounter % 5 == 0) {
                baseDelay += ThreadLocalRandom.current().nextLong(10, 25L);
            }
                
            return System.currentTimeMillis() - lastClickTime >= baseDelay;
        }
        int ticks = dynamicCooldown.get() ? dynamicTickCount() : 10;
        float progress = mc.player.getAttackCooldownProgress(0.5f);
        return System.currentTimeMillis() - lastClickTime >= ticks * 40L && progress >= 0.92f;
    }

    private int dynamicTickCount() {
        String server = (mc.getNetworkHandler() != null && mc.getNetworkHandler().getServerInfo() != null) ? mc.getNetworkHandler().getServerInfo().address.toLowerCase(Locale.ROOT) : "";
        if (server.contains("funtime")) return new int[]{10, 11, 10, 12}[clickCounter % 4];
        return 10;
    }

    private void recalculateClickWindow() { lastClickTime = System.currentTimeMillis(); clickCounter++; }

    private void tryBreakShield(LivingEntity tgt) {
        if (!(tgt instanceof PlayerEntity p) || !p.isUsingItem()) return;
        int axe = -1;
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isIn(ItemTags.AXES)) { axe = i; break; }
        if (axe != -1) {
            int old = mc.player.getInventory().getSelectedSlot();
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(axe));
            mc.interactionManager.attackEntity(mc.player, tgt);
            mc.player.swingHand(Hand.MAIN_HAND);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(old));
        }
    }

    private boolean rayTrace(LivingEntity entity, double range) {
        Vec3d eye = mc.player.getEyePos();
        Box box = entity.getBoundingBox();
        
        // Fix 1: if eye is inside the hitbox, allow the hit
        if (box.expand(0.05).contains(eye)) return true;
        
        Rotation r = Managers.ROTATION.getRotation();
        Vec3d look = toVector(r.yaw, r.pitch); 
        Vec3d end = eye.add(look.multiply(range));
        
        // Precise raycast against entity
        Optional<Vec3d> hit = box.expand(0.1).raycast(eye, end);
        if (hit.isPresent()) {
            if (!ignoreWalls.get()) {
                HitResult blockHit = mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
                if (blockHit.getType() != HitResult.Type.MISS) {
                    return eye.squaredDistanceTo(hit.get()) < eye.squaredDistanceTo(blockHit.getPos());
                }
            }
            return true;
        }
        
        return ignoreWalls.get();
    }

    private boolean isVisiblePoint(Vec3d p) { return mc.world.raycast(new RaycastContext(mc.player.getEyePos(), p, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player)).getType() == HitResult.Type.MISS; }
    private Vec3d toVector(float yaw, float pitch) { float f = pitch * 0.017453292F, g = -yaw * 0.017453292F; return new Vec3d(MathHelper.sin(g) * MathHelper.cos(f), -MathHelper.sin(f), MathHelper.cos(g) * MathHelper.cos(f)); }
    private Vec3d closestPoint(Vec3d eye, Box box) { return new Vec3d(MathHelper.clamp(eye.x, box.minX, box.maxX), MathHelper.clamp(eye.y, box.minY, box.maxY), MathHelper.clamp(eye.z, box.minZ, box.maxZ)); }

    private void executeMaceKill(LivingEntity target) {
        if (maceKillState == 0) {
            // State 0: Move to high position above target
            Vec3d targetPos = new Vec3d(target.getX(), target.getY(), target.getZ());
            Vec3d highPos = new Vec3d(targetPos.x, targetPos.y + maceKillHeight.get(), targetPos.z);
            
            // Send multiple position packets to spoof being high up
            int packets = maceKillPackets.get();
            for (int i = 0; i < packets; i++) {
                mc.player.networkHandler.sendPacket(
                    new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround(
                        highPos.x, highPos.y, highPos.z, false, mc.player.horizontalCollision
                    )
                );
            }
            
            // Set player position to high position
            mc.player.setPosition(highPos);
            maceKillState = 1;
            return;
        }
        
        if (maceKillState == 1) {
            // State 1: Dive down to target and attack
            Vec3d attackPos = new Vec3d(target.getX(), target.getY() + 0.5, target.getZ());
            
            // Send dive packets
            int packets = maceKillPackets.get();
            for (int i = 0; i < packets; i++) {
                mc.player.networkHandler.sendPacket(
                    new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround(
                        attackPos.x, attackPos.y, attackPos.z, false, mc.player.horizontalCollision
                    )
                );
            }
            
            // Set position to attack
            mc.player.setPosition(attackPos);
            
            // Execute attack
            if (maceKillMode.get() == MaceKillMode.Aggressive) {
                // Aggressive: multiple attacks
                for (int i = 0; i < 3; i++) {
                    tryAttack(target);
                }
            } else {
                // Vanilla: single attack
                tryAttack(target);
            }
            
            maceKillState = 0;
        }
    }

    @EventHandler
    public void onRender(Render3DEvent event) {
        if (renderTarget.get() && lastTarget != null && lastTarget.isAlive()) {
            int rgb = ColorUtil.swapAlpha(ClickGui.color(0).getRGB(), 80);
            Render3DUtil.drawFilledBox(event.getMatrices(), lastTarget.getBoundingBox(), rgb);
            Render3DUtil.drawBoxOutline(event.getMatrices(), lastTarget.getBoundingBox(), rgb, 1.8f);
        }
    }

    private record AimResult(Vec3d point, float hitBoxYRatio) {}

    private final class NeuroEngine {
        private List<RecordSample> samples = new ArrayList<>();
        private List<RecordSample> activeDataset = new ArrayList<>();
        private boolean recording;
        private int playbackIndex = 0;
        private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

        void startRecord() {
            samples.clear();
            recording = true;
        }

        void stopRecord() {
            recording = false;
        }

        Rotation apply(Rotation desired) {
            if (activeDataset.isEmpty()) {
                Rotation cur = Managers.ROTATION.getRotation();
                float yawStep = MathHelper.clamp(MathHelper.wrapDegrees(desired.yaw - cur.yaw), -70f, 70f);
                float pitchStep = MathHelper.clamp(desired.pitch - cur.pitch, -45f, 45f);
                return new Rotation(cur.yaw + yawStep + (float)(Math.sin(System.currentTimeMillis() / 38.0) * 2.0), MathHelper.clamp(cur.pitch + pitchStep + (float)(Math.cos(System.currentTimeMillis() / 55.0) * 1.5f), -90, 90));
            }
            
            if (playbackIndex >= activeDataset.size()) {
                playbackIndex = 0;
            }
            
            RecordSample sample = activeDataset.get(playbackIndex);
            
            float yaw = desired.yaw + sample.yawOffset;
            float pitch = MathHelper.clamp(desired.pitch + sample.pitchOffset, -90, 90);
            
            return new Rotation(yaw, pitch);
        }
        
        void tick() {
            if (recording) return;
            if (!activeDataset.isEmpty() && rotationMode.is(RotationMode.Neuro) && target != null) {
                playbackIndex++;
                if (playbackIndex >= activeDataset.size()) playbackIndex = 0;
            }
        }

        boolean shouldHit() {
            if (rotationMode.is(RotationMode.Neuro) && !activeDataset.isEmpty() && target != null) {
                if (playbackIndex < activeDataset.size()) {
                    return activeDataset.get(playbackIndex).hit;
                }
            }
            return true;
        }

        void collectLiveSample(Rotation current, Rotation desired, boolean hit) {
            if (!recording) return;
            float yawOffset = MathHelper.wrapDegrees(current.yaw - desired.yaw);
            float pitchOffset = current.pitch - desired.pitch;
            samples.add(new RecordSample(yawOffset, pitchOffset, hit));
            if (samples.size() > 20000) samples.removeFirst();
        }

        boolean save(String name) {
            try {
                File dir = new File(getDir());
                if (!dir.exists() && !dir.mkdirs()) return false;
                File file = new File(dir, name + ".json");
                try (Writer writer = new FileWriter(file)) {
                    gson.toJson(samples, writer);
                    return true;
                }
            } catch (IOException e) {
                return false;
            }
        }

        boolean load(String name) {
            try {
                File file = new File(getDir(), name + ".json");
                if (!file.exists()) return false;
                try (Reader reader = new FileReader(file)) {
                    List<RecordSample> loaded = gson.fromJson(reader, new TypeToken<List<RecordSample>>(){}.getType());
                    if (loaded != null) {
                        activeDataset = loaded;
                        playbackIndex = 0;
                        return true;
                    }
                    return false;
                }
            } catch (IOException e) {
                return false;
            }
        }

        String getDir() {
            return mc.runDirectory.toPath().resolve("sakura").resolve("neuro").toString();
        }
    }
    private record RecordSample(float yawOffset, float pitchOffset, boolean hit) {}
}
