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

    public enum RotationMode { Slide, Resolver, Snap, Neuro, Packet, Polar, Grim, Smooth, HolyWorld, NoRot }
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
            case Packet, Grim, HolyWorld -> desired;
            case Polar -> applyPolarRotation(desired, target);
            case Smooth -> applySmoothRotation(desired);
            case NoRot -> Managers.ROTATION.getRotation();
        };

        out = applyGcdPatch(out);
        
        lastOut = out;
        lastDesired = desired;

        RotationManager.Priority priority = (rotationMode.is(RotationMode.Packet) || rotationMode.is(RotationMode.Polar) || rotationMode.is(RotationMode.Grim) || rotationMode.is(RotationMode.HolyWorld)) ? RotationManager.Priority.Highest : RotationManager.Priority.High;
        
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
