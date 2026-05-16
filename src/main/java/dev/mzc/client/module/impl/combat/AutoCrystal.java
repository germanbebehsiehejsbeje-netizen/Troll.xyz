package dev.mzc.client.module.impl.combat;

import dev.mzc.client.Sakura;
import dev.mzc.client.events.EventType;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.packet.PacketEvent;
import dev.mzc.client.manager.Managers;
import dev.mzc.client.manager.impl.RotationManager;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.combat.CombatUtil;
import dev.mzc.client.utils.combat.DamageUtil;
import dev.mzc.client.utils.player.FindItemResult;
import dev.mzc.client.utils.player.InvUtil;
import dev.mzc.client.utils.rotation.MovementFix;
import dev.mzc.client.utils.rotation.RotationUtil;
import dev.mzc.client.utils.vector.Rotation;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.EndCrystalItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * AutoCrystal ported and adapted from ThunderHack's CrystalAura/AutoCrystal logic.
 * Performs crystal placement and breaking with damage-based target selection.
 */
public class AutoCrystal extends Module {

    /* ===== Targeting ===== */
    private final NumberValue<Double> targetRange = new NumberValue<>("TargetRange", 10.0, 1.0, 20.0, 0.5);
    private final EnumValue<TargetMode> targetMode = new EnumValue<>("TargetMode", TargetMode.Distance);

    /* ===== Place ===== */
    private final BoolValue autoPlace = new BoolValue("AutoPlace", true);
    private final NumberValue<Double> placeRange = new NumberValue<>("PlaceRange", 5.0, 0.0, 6.0, 0.1);
    private final NumberValue<Double> placeWallRange = new NumberValue<>("PlaceWallRange", 3.5, 0.0, 6.0, 0.1);
    private final NumberValue<Integer> placeDelay = new NumberValue<>("PlaceDelay", 2, 0, 20, 1);
    private final EnumValue<Interact> interact = new EnumValue<>("Interact", Interact.Strict);

    /* ===== Break ===== */
    private final BoolValue autoBreak = new BoolValue("AutoBreak", true);
    private final NumberValue<Double> breakRange = new NumberValue<>("BreakRange", 5.0, 0.0, 6.0, 0.1);
    private final NumberValue<Double> breakWallRange = new NumberValue<>("BreakWallRange", 3.5, 0.0, 6.0, 0.1);
    private final NumberValue<Integer> breakDelay = new NumberValue<>("BreakDelay", 1, 0, 20, 1);
    private final BoolValue inhibit = new BoolValue("Inhibit", true);

    /* ===== Damage ===== */
    private final NumberValue<Double> minDamage = new NumberValue<>("MinDamage", 6.0, 0.0, 36.0, 0.5);
    private final NumberValue<Double> maxSelfDamage = new NumberValue<>("MaxSelfDamage", 8.0, 0.0, 36.0, 0.5);
    private final BoolValue antiSuicide = new BoolValue("AntiSuicide", true);
    private final BoolValue facePlace = new BoolValue("FacePlace", true);
    private final NumberValue<Double> facePlaceHp = new NumberValue<>("FacePlaceHP", 5.0, 0.0, 20.0, 0.5);

    /* ===== Switch ===== */
    private final EnumValue<SwitchMode> autoSwitch = new EnumValue<>("Switch", SwitchMode.Inventory);

    /* ===== Auto Obsidian (AutoCrystalBase port) ===== */
    private final BoolValue autoObby = new BoolValue("AutoObby", false);
    private final NumberValue<Integer> obbyRange = new NumberValue<>("ObbyRange", 5, 1, 7, 1);
    private final NumberValue<Double> obbyMinDamageDelta = new NumberValue<>("ObbyDamageDelta", 5.0, 1.0, 20.0, 0.5);
    private final NumberValue<Integer> obbyPlaceDelay = new NumberValue<>("ObbyPlaceDelay", 6, 0, 40, 1);

    /* ===== Misc ===== */
    private final EnumValue<RotateMode> rotateMode = new EnumValue<>("Rotate", RotateMode.Smooth);
    private final NumberValue<Double> rotationSpeed = new NumberValue<>("RotateSpeed", 0.55, 0.1, 1.0, 0.05);
    private final NumberValue<Double> yawStep = new NumberValue<>("YawStep", 180.0, 30.0, 180.0, 5.0);
    private final NumberValue<Integer> preRotateTicks = new NumberValue<>("PreRotateTicks", 1, 0, 5, 1);
    private final NumberValue<Double> rotationThreshold = new NumberValue<>("RotateThreshold", 6.0, 0.0, 30.0, 0.5);
    private final BoolValue gcdFix = new BoolValue("GcdFix", true);
    private final BoolValue jitter = new BoolValue("DelayJitter", true);
    private final BoolValue pauseOnUse = new BoolValue("PauseOnUse", true);
    private final EnumValue<SwingMode> swingMode = new EnumValue<>("Swing", SwingMode.Packet);
    private final BoolValue protectFriends = new BoolValue("ProtectFriends", true);

    /* ===== State ===== */
    private PlayerEntity target;
    private EndCrystalEntity bestCrystal;
    private PlaceData bestPlace;
    private ObbyData bestObby;
    private float renderDamage, renderSelfDamage;

    private int breakTimer;
    private int placeTimer;
    private int obbyTimer;

    private Rotation currentRotation;
    private int rotationProgress;

    private final Set<Integer> deadCrystals = new HashSet<>();

    public AutoCrystal() {
        super("AutoCrystal", Category.Combat);
        this.setType(ModuleType.Hack);
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    private void resetState() {
        target = null;
        bestCrystal = null;
        bestPlace = null;
        bestObby = null;
        renderDamage = 0;
        renderSelfDamage = 0;
        breakTimer = 0;
        placeTimer = 0;
        obbyTimer = 0;
        currentRotation = null;
        rotationProgress = 0;
        deadCrystals.clear();
    }

    @EventHandler
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE) return;
        if (mc.world == null) return;

        // Track explosions to mark crystals as dead (avoid re-attacking them).
        if (event.getPacket() instanceof ExplosionS2CPacket explosion) {
            Vec3d pos = explosion.center();
            for (Entity ent : mc.world.getEntities()) {
                if (ent instanceof EndCrystalEntity cr && cr.squaredDistanceTo(pos.x, pos.y, pos.z) <= 144) {
                    deadCrystals.add(cr.getId());
                }
            }
        }
    }

    @EventHandler
    public void onPreTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        if (breakTimer > 0) breakTimer--;
        if (placeTimer > 0) placeTimer--;
        if (obbyTimer > 0) obbyTimer--;

        // Pause if the player is doing something else (eating, drinking, mining).
        if (pauseOnUse.get() && isBusy()) {
            target = null;
            bestCrystal = null;
            bestPlace = null;
            bestObby = null;
            currentRotation = null;
            rotationProgress = 0;
            return;
        }

        // Clean up entries for crystals that no longer exist.
        deadCrystals.removeIf(id -> mc.world.getEntityById(id) == null);

        target = findTarget();
        if (target == null) {
            bestCrystal = null;
            bestPlace = null;
            bestObby = null;
            currentRotation = null;
            rotationProgress = 0;
            return;
        }

        bestCrystal = autoBreak.get() ? findBestCrystal() : null;
        bestPlace = autoPlace.get() ? findBestPlacePos() : null;
        bestObby = autoObby.get() ? findBestObbyPos() : null;

        // Pick the action we'll rotate towards this tick (priority: break > place > obby).
        Vec3d rotTarget = null;
        if (bestCrystal != null) {
            rotTarget = bestCrystal.getBoundingBox().getCenter();
        } else if (bestPlace != null) {
            rotTarget = bestPlace.bhr.getPos();
        } else if (bestObby != null) {
            rotTarget = bestObby.bhr.getPos();
        }

        if (rotTarget != null && rotateMode.get() != RotateMode.Off) {
            Rotation desired = RotationUtil.calculate(rotTarget);
            Rotation out = applyRotationMode(desired);
            if (gcdFix.get()) out = applyGcdPatch(out);

            currentRotation = out;
            rotationProgress++;

            Managers.ROTATION.setRotations(out, 100, MovementFix.GRIM, RotationManager.Priority.High);
        } else {
            currentRotation = null;
            rotationProgress = 0;
        }
    }

    @EventHandler
    public void onPostTick(TickEvent.Post event) {
        if (nullCheck()) return;
        if (target == null) return;

        // Wait for rotation to settle before acting (anti-snap detection).
        boolean needRotate = rotateMode.get() != RotateMode.Off && rotate();
        if (needRotate && rotationProgress < preRotateTicks.get()) return;

        // Break first to free up the position, then place.
        if (bestCrystal != null && breakTimer <= 0) {
            if (!needRotate || isFacingNear(bestCrystal.getBoundingBox().getCenter())) {
                attackCrystal(bestCrystal);
                breakTimer = breakDelay.get() + jitterTicks();
                return; // don't place in the same tick we attacked
            }
        }

        if (bestPlace != null && placeTimer <= 0) {
            if (!needRotate || isFacingNear(bestPlace.bhr.getPos())) {
                placeCrystal(bestPlace);
                placeTimer = placeDelay.get() + jitterTicks();
                return;
            }
        }

        if (bestObby != null && obbyTimer <= 0) {
            if (!needRotate || isFacingNear(bestObby.bhr.getPos())) {
                placeObby(bestObby);
                obbyTimer = obbyPlaceDelay.get() + jitterTicks();
            }
        }
    }

    /* ===================================================== */
    /* =================== Rotation =========================*/
    /* ===================================================== */

    private boolean rotate() {
        return rotateMode.get() != RotateMode.Off;
    }

    private Rotation applyRotationMode(Rotation desired) {
        Rotation cur = Managers.ROTATION.getRotation();
        return switch (rotateMode.get()) {
            case Off -> cur;
            case Snap -> desired;
            case Slide -> applySlide(cur, desired);
            case Smooth -> applySmooth(cur, desired);
        };
    }

    private Rotation applySlide(Rotation cur, Rotation desired) {
        float yawClamp = (float) (double) yawStep.get();
        float dy = net.minecraft.util.math.MathHelper.wrapDegrees(desired.yaw - cur.yaw);
        float dp = desired.pitch - cur.pitch;
        float clampedYaw = net.minecraft.util.math.MathHelper.clamp(dy, -yawClamp, yawClamp);
        float clampedPitch = net.minecraft.util.math.MathHelper.clamp(dp, -45f, 45f);
        return new Rotation(cur.yaw + clampedYaw,
                net.minecraft.util.math.MathHelper.clamp(cur.pitch + clampedPitch, -90f, 90f));
    }

    private Rotation applySmooth(Rotation cur, Rotation desired) {
        float speed = (float) (double) rotationSpeed.get();
        float yawClamp = (float) (double) yawStep.get();
        float dy = net.minecraft.util.math.MathHelper.wrapDegrees(desired.yaw - cur.yaw);
        float dp = desired.pitch - cur.pitch;

        // Slight humanizing jitter.
        dy += (float) ((Math.random() - 0.5) * 0.6);
        dp += (float) ((Math.random() - 0.5) * 0.4);

        float maxYaw = Math.min(speed * 180f, yawClamp);
        float maxPitch = speed * 90f;
        float clampedYaw = net.minecraft.util.math.MathHelper.clamp(dy, -maxYaw, maxYaw);
        float clampedPitch = net.minecraft.util.math.MathHelper.clamp(dp, -maxPitch, maxPitch);

        return new Rotation(cur.yaw + clampedYaw,
                net.minecraft.util.math.MathHelper.clamp(cur.pitch + clampedPitch, -90f, 90f));
    }

    private Rotation applyGcdPatch(Rotation desired) {
        Rotation cur = Managers.ROTATION.getRotation();
        float gcd = getGcd();
        float dy = net.minecraft.util.math.MathHelper.wrapDegrees(desired.yaw - cur.yaw);
        float dp = desired.pitch - cur.pitch;
        return new Rotation(cur.yaw + Math.round(dy / gcd) * gcd,
                net.minecraft.util.math.MathHelper.clamp(cur.pitch + Math.round(dp / gcd) * gcd, -90f, 90f));
    }

    private float getGcd() {
        float sens = mc.options.getMouseSensitivity().getValue().floatValue();
        float f = sens * 0.6f + 0.2f;
        return Math.max(1.0E-4f, f * f * f * 1.2f);
    }

    private boolean isFacingNear(Vec3d target) {
        if (currentRotation == null) return true;
        Rotation desired = RotationUtil.calculate(target);
        float dy = net.minecraft.util.math.MathHelper.wrapDegrees(desired.yaw - currentRotation.yaw);
        float dp = desired.pitch - currentRotation.pitch;
        double diff = Math.hypot(dy, dp);
        return diff <= rotationThreshold.get();
    }

    private boolean isBusy() {
        if (mc.player == null) return false;
        if (mc.player.isUsingItem()) return true;
        if (mc.interactionManager != null && mc.interactionManager.isBreakingBlock()) return true;
        return false;
    }

    private int jitterTicks() {
        return jitter.get() ? (int) (Math.random() * 2) : 0;
    }

    /* ===================================================== */
    /* =================== Target finding =================== */
    /* ===================================================== */

    private PlayerEntity findTarget() {
        if (mc.world == null || mc.player == null) return null;

        PlayerEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || !p.isAlive()) continue;
            if (Managers.FRIEND != null && Managers.FRIEND.isFriend(p.getName().getString())) continue;
            double dist = mc.player.distanceTo(p);
            if (dist > targetRange.get()) continue;

            double score = switch (targetMode.get()) {
                case Distance -> dist;
                case Health -> p.getHealth() + p.getAbsorptionAmount();
                case FOV -> Math.abs(angleDelta(p));
            };

            if (score < bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }

    private double angleDelta(PlayerEntity p) {
        Vec3d diff = p.getEyePos().subtract(mc.player.getEyePos());
        double targetYaw = Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0;
        double delta = ((targetYaw - mc.player.getYaw()) % 360 + 540) % 360 - 180;
        return delta;
    }

    /* ===================================================== */
    /* =================== Break logic ====================== */
    /* ===================================================== */

    private @Nullable EndCrystalEntity findBestCrystal() {
        if (mc.world == null || mc.player == null || target == null) return null;

        EndCrystalEntity best = null;
        float bestDamage = 0f;
        float bestSelf = 0f;

        for (Entity ent : mc.world.getEntities()) {
            if (!(ent instanceof EndCrystalEntity cr)) continue;
            if (!cr.isAlive()) continue;
            if (inhibit.get() && deadCrystals.contains(cr.getId())) continue;

            Vec3d crystalPos = cr.getBoundingBox().getCenter();
            double dist = mc.player.getEyePos().distanceTo(crystalPos);
            boolean los = canSee(crystalPos);
            double maxDist = los ? breakRange.get() : breakWallRange.get();
            if (dist > maxDist) continue;

            float damage = DamageUtil.calculateCrystalDamage(target, cr.getEntityPos());
            float selfDamage = DamageUtil.calculateCrystalDamage(mc.player, cr.getEntityPos());

            if (protectFriends.get()) {
                for (PlayerEntity friend : mc.world.getPlayers()) {
                    if (friend == mc.player) continue;
                    if (Managers.FRIEND == null || !Managers.FRIEND.isFriend(friend.getName().getString())) continue;
                    float friendDmg = DamageUtil.calculateCrystalDamage(friend, cr.getEntityPos());
                    if (friendDmg > selfDamage) selfDamage = friendDmg;
                }
            }

            if (damage < 1.5f) continue;
            if (!isSafe(damage, selfDamage)) continue;
            if (damage < minDamage.get() && !shouldOverrideMinDamage(damage)) continue;

            if (damage > bestDamage) {
                bestDamage = damage;
                bestSelf = selfDamage;
                best = cr;
            }
        }

        if (best != null) {
            renderDamage = bestDamage;
            renderSelfDamage = bestSelf;
        }
        return best;
    }

    private void attackCrystal(EndCrystalEntity crystal) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, mc.player.isSneaking()));
        swingHand(Hand.MAIN_HAND, true);
        deadCrystals.add(crystal.getId());
    }

    /* ===================================================== */
    /* =================== Place logic ====================== */
    /* ===================================================== */

    private @Nullable PlaceData findBestPlacePos() {
        if (mc.world == null || mc.player == null || target == null) return null;
        if (!hasCrystal()) return null;

        BlockPos playerPos = mc.player.getBlockPos();
        int r = (int) Math.ceil(placeRange.get());

        PlaceData best = null;
        float bestDmg = 0f;

        for (int x = playerPos.getX() - r; x <= playerPos.getX() + r; x++) {
            for (int y = playerPos.getY() - r; y <= playerPos.getY() + r; y++) {
                for (int z = playerPos.getZ() - r; z <= playerPos.getZ() + r; z++) {
                    BlockPos bp = new BlockPos(x, y, z);
                    PlaceData data = getPlaceData(bp);
                    if (data == null) continue;
                    if (data.damage < minDamage.get() && !shouldOverrideMinDamage(data.damage)) continue;
                    if (data.damage > bestDmg) {
                        bestDmg = data.damage;
                        best = data;
                    }
                }
            }
        }

        if (best != null) {
            renderDamage = best.damage;
            renderSelfDamage = best.selfDamage;
        }
        return best;
    }

    private @Nullable PlaceData getPlaceData(BlockPos bp) {
        if (mc.world == null || mc.player == null) return null;

        Block base = mc.world.getBlockState(bp).getBlock();
        if (base != Blocks.OBSIDIAN && base != Blocks.BEDROCK) return null;
        if (!mc.world.isAir(bp.up())) return null;
        if (isPositionBlockedByEntity(bp)) return null;

        // 12 block radius hard limit (vanilla crystal damage range).
        if (target.getEntityPos().squaredDistanceTo(bp.toCenterPos().add(0, 0.5, 0)) > 144) return null;

        Vec3d crystalVec = new Vec3d(bp.getX() + 0.5, bp.getY() + 1.0, bp.getZ() + 0.5);

        float damage = DamageUtil.calculateCrystalDamage(target, crystalVec);
        if (damage < 1.5f) return null;
        float selfDamage = DamageUtil.calculateCrystalDamage(mc.player, crystalVec);

        if (protectFriends.get()) {
            for (PlayerEntity friend : mc.world.getPlayers()) {
                if (friend == mc.player) continue;
                if (Managers.FRIEND == null || !Managers.FRIEND.isFriend(friend.getName().getString())) continue;
                float fd = DamageUtil.calculateCrystalDamage(friend, crystalVec);
                if (fd > selfDamage) selfDamage = fd;
            }
        }

        if (!isSafe(damage, selfDamage)) return null;

        BlockHitResult bhr = computeInteract(bp, crystalVec);
        if (bhr == null) return null;

        return new PlaceData(bp, bhr, damage, selfDamage);
    }

    private @Nullable BlockHitResult computeInteract(BlockPos bp, Vec3d crystalVec) {
        Vec3d eyes = mc.player.getEyePos();
        double distSq = eyes.squaredDistanceTo(crystalVec);
        double maxRange = placeRange.get();
        double maxWallRange = placeWallRange.get();

        if (distSq > maxRange * maxRange) return null;

        if (interact.get() == Interact.Default) {
            BlockHitResult wallCheck = mc.world.raycast(new RaycastContext(eyes, crystalVec,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            if (wallCheck != null && wallCheck.getType() == HitResult.Type.BLOCK
                    && !wallCheck.getBlockPos().equals(bp)) {
                if (distSq > maxWallRange * maxWallRange) return null;
            }
            return new BlockHitResult(crystalVec, Direction.UP, bp, false);
        } else {
            // Strict: pick the side closest to the player eye that is replaceable.
            Direction bestSide = null;
            double bestDist = Double.MAX_VALUE;

            if (mc.player.getEyePos().y > bp.getY() + 0.5) {
                bestSide = Direction.UP;
            } else {
                for (Direction dir : Direction.values()) {
                    if (dir == Direction.UP || dir == Direction.DOWN) continue;
                    BlockPos neighbor = bp.offset(dir);
                    if (!mc.world.getBlockState(neighbor).isReplaceable()) continue;
                    Vec3d sideVec = new Vec3d(bp.getX() + 0.5 + dir.getOffsetX() * 0.5,
                            bp.getY() + 0.99,
                            bp.getZ() + 0.5 + dir.getOffsetZ() * 0.5);
                    double d = eyes.squaredDistanceTo(sideVec);
                    if (d < bestDist) {
                        bestDist = d;
                        bestSide = dir;
                    }
                }
                if (bestSide == null) bestSide = Direction.UP;
            }

            Vec3d hitVec = new Vec3d(
                    bp.getX() + 0.5 + bestSide.getOffsetX() * 0.5,
                    bp.getY() + 0.5 + bestSide.getOffsetY() * 0.5,
                    bp.getZ() + 0.5 + bestSide.getOffsetZ() * 0.5);
            if (eyes.squaredDistanceTo(hitVec) > maxRange * maxRange) return null;
            return new BlockHitResult(hitVec, bestSide, bp, false);
        }
    }

    private void placeCrystal(PlaceData data) {
        if (mc.player == null || mc.interactionManager == null || mc.getNetworkHandler() == null) return;

        boolean offhandCrystal = mc.player.getOffHandStack().getItem() instanceof EndCrystalItem;
        boolean mainCrystal = mc.player.getMainHandStack().getItem() instanceof EndCrystalItem;

        // Pick hand: prefer mainhand if it has a crystal so we don't disturb offhand totem.
        Hand hand;
        int prevSlot = -1;
        boolean invSwapped = false;

        if (mainCrystal) {
            hand = Hand.MAIN_HAND;
        } else if (offhandCrystal) {
            hand = Hand.OFF_HAND;
        } else {
            // No crystal in either hand — try to switch.
            if (autoSwitch.get() == SwitchMode.None) return;

            FindItemResult hotbarRes = InvUtil.findInHotbar(Items.END_CRYSTAL);
            if (hotbarRes.found() && hotbarRes.slot() <= 8) {
                prevSlot = mc.player.getInventory().getSelectedSlot();
                switch (autoSwitch.get()) {
                    case Normal -> InvUtil.swap(hotbarRes.slot(), false);
                    case Silent, Inventory -> {
                        sendPacket(new UpdateSelectedSlotC2SPacket(hotbarRes.slot()));
                        mc.player.getInventory().setSelectedSlot(hotbarRes.slot());
                    }
                    default -> {}
                }
            } else if (autoSwitch.get() == SwitchMode.Inventory) {
                FindItemResult invRes = InvUtil.find(Items.END_CRYSTAL);
                if (!invRes.found()) return;
                InvUtil.invSwap(mc.player.getInventory().getSelectedSlot());
                invSwapped = true;
                // After swap, the crystal is in the selected hotbar slot.
            } else {
                return;
            }
            hand = Hand.MAIN_HAND;
        }

        // Rotation is handled by RotationManager (sent via MotionEvent).
        sendPacket(new PlayerInteractBlockC2SPacket(hand, data.bhr, 0));
        swingHand(hand, false);

        // Restore original slot if we silent-switched.
        if (prevSlot != -1 && (autoSwitch.get() == SwitchMode.Silent || autoSwitch.get() == SwitchMode.Inventory)) {
            sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
            mc.player.getInventory().setSelectedSlot(prevSlot);
        }

        if (invSwapped) {
            InvUtil.invSwapBack();
        }
    }

    /* ===================================================== */
    /* =================== Auto Obsidian ==================== */
    /* ===================================================== */

    /**
     * Find the best position to place an obsidian block such that putting a crystal on top
     * of it would deal significantly more damage to the target than the current best place spot.
     */
    private @Nullable ObbyData findBestObbyPos() {
        if (mc.world == null || mc.player == null || target == null) return null;
        if (!hasObsidian()) return null;

        // Baseline: damage we'd already get from the current best place position (without auto-obby).
        float baseline = bestPlace != null ? bestPlace.damage : (float) (double) minDamage.get();
        float bestGain = (float) (double) obbyMinDamageDelta.get();
        ObbyData best = null;

        BlockPos targetPos = target.getBlockPos();
        int r = obbyRange.get();

        for (int x = targetPos.getX() - r; x <= targetPos.getX() + r; x++) {
            for (int y = targetPos.getY() - r; y <= targetPos.getY() + r; y++) {
                for (int z = targetPos.getZ() - r; z <= targetPos.getZ() + r; z++) {
                    BlockPos bp = new BlockPos(x, y, z);
                    if (!mc.world.isAir(bp)) continue;
                    if (!mc.world.isAir(bp.up())) continue; // need space for the future crystal
                    if (isPositionBlockedByEntity(bp)) continue;

                    // The crystal would sit at bp.up() (since obsidian goes at bp).
                    Vec3d crystalVec = new Vec3d(bp.getX() + 0.5, bp.getY() + 1.0, bp.getZ() + 0.5);

                    // Distance / range checks for both the obby placement and the eventual crystal.
                    Vec3d eyes = mc.player.getEyePos();
                    double obbyHit = eyes.squaredDistanceTo(Vec3d.ofCenter(bp));
                    if (obbyHit > placeRange.get() * placeRange.get()) continue;
                    if (eyes.squaredDistanceTo(crystalVec) > placeRange.get() * placeRange.get()) continue;

                    // Need a solid neighbor to click against.
                    Direction side = pickObbySide(bp);
                    if (side == null) continue;

                    // Crystal damage with obby ghost-block in place.
                    float damage = DamageUtil.calculateGhostBlockDamage(target, crystalVec, bp);
                    if (damage < 1.5f) continue;
                    float selfDamage = DamageUtil.calculateGhostBlockDamage(mc.player, crystalVec, bp);

                    if (protectFriends.get()) {
                        for (PlayerEntity friend : mc.world.getPlayers()) {
                            if (friend == mc.player) continue;
                            if (Managers.FRIEND == null || !Managers.FRIEND.isFriend(friend.getName().getString())) continue;
                            float fd = DamageUtil.calculateGhostBlockDamage(friend, crystalVec, bp);
                            if (fd > selfDamage) selfDamage = fd;
                        }
                    }

                    if (!isSafe(damage, selfDamage)) continue;

                    float gain = damage - baseline;
                    if (gain < obbyMinDamageDelta.get()) continue;
                    if (gain <= bestGain && best != null) continue;

                    Vec3d hitVec = new Vec3d(
                            bp.getX() + 0.5 + side.getOffsetX() * 0.5,
                            bp.getY() + 0.5 + side.getOffsetY() * 0.5,
                            bp.getZ() + 0.5 + side.getOffsetZ() * 0.5);
                    BlockHitResult bhr = new BlockHitResult(hitVec, side, bp, false);

                    bestGain = gain;
                    best = new ObbyData(bp, bhr, damage, selfDamage);
                }
            }
        }

        return best;
    }

    private @Nullable Direction pickObbySide(BlockPos bp) {
        if (mc.world == null) return null;
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = bp.offset(dir);
            net.minecraft.block.BlockState state = mc.world.getBlockState(neighbor);
            if (state.isAir() || state.isReplaceable()) continue;
            if (!state.isFullCube(mc.world, neighbor)) continue;
            return dir.getOpposite();
        }
        return null;
    }

    private void placeObby(ObbyData data) {
        if (mc.player == null || mc.interactionManager == null || mc.getNetworkHandler() == null) return;

        boolean inMain = mc.player.getMainHandStack().isOf(Items.OBSIDIAN);
        int prevSlot = mc.player.getInventory().getSelectedSlot();
        boolean invSwapped = false;

        if (!inMain) {
            if (autoSwitch.get() == SwitchMode.None) return;
            FindItemResult hot = InvUtil.findInHotbar(Items.OBSIDIAN);
            if (hot.found() && hot.slot() <= 8) {
                switch (autoSwitch.get()) {
                    case Normal -> InvUtil.swap(hot.slot(), false);
                    case Silent, Inventory -> {
                        sendPacket(new UpdateSelectedSlotC2SPacket(hot.slot()));
                        mc.player.getInventory().setSelectedSlot(hot.slot());
                    }
                    default -> {}
                }
            } else if (autoSwitch.get() == SwitchMode.Inventory) {
                FindItemResult inv = InvUtil.find(Items.OBSIDIAN);
                if (!inv.found()) return;
                InvUtil.invSwap(prevSlot);
                invSwapped = true;
            } else {
                return;
            }
        }

        // Rotation is handled by RotationManager (sent via MotionEvent).
        sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, data.bhr, 0));
        swingHand(Hand.MAIN_HAND, false);

        if (!inMain && (autoSwitch.get() == SwitchMode.Silent || autoSwitch.get() == SwitchMode.Inventory)) {
            sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
            mc.player.getInventory().setSelectedSlot(prevSlot);
        }

        if (invSwapped) {
            InvUtil.invSwapBack();
        }
    }

    private boolean hasObsidian() {
        if (mc.player == null) return false;
        if (mc.player.getMainHandStack().isOf(Items.OBSIDIAN)) return true;
        if (autoSwitch.get() == SwitchMode.None) return false;
        if (InvUtil.findInHotbar(Items.OBSIDIAN).found()) return true;
        if (autoSwitch.get() == SwitchMode.Inventory) {
            return InvUtil.find(Items.OBSIDIAN).found();
        }
        return false;
    }

    /* ===================================================== */
    /* =================== Helpers ========================== */
    /* ===================================================== */

    private boolean isSafe(float damage, float selfDamage) {
        if (mc.player == null) return false;
        if (!antiSuicide.get()) return selfDamage <= maxSelfDamage.get();
        if (selfDamage + 0.5f > mc.player.getHealth() + mc.player.getAbsorptionAmount()) return false;
        return selfDamage <= maxSelfDamage.get();
    }

    private boolean shouldOverrideMinDamage(float damage) {
        if (target == null || !facePlace.get()) return false;
        if (target.getHealth() + target.getAbsorptionAmount() <= facePlaceHp.get()) return true;
        return (target.getHealth() + target.getAbsorptionAmount()) - damage <= 0;
    }

    private boolean isPositionBlockedByEntity(BlockPos bp) {
        if (mc.world == null) return false;
        Box above = new Box(bp.up()).expand(0, 1.0, 0);
        for (Entity ent : mc.world.getEntities()) {
            if (ent == null) continue;
            if (ent instanceof ExperienceOrbEntity) continue;
            if (ent instanceof EndCrystalEntity cr && deadCrystals.contains(cr.getId())) continue;
            if (ent.getBoundingBox().intersects(above)) return true;
        }
        return false;
    }

    private boolean hasCrystal() {
        if (mc.player == null) return false;
        if (mc.player.getMainHandStack().getItem() instanceof EndCrystalItem) return true;
        if (mc.player.getOffHandStack().getItem() instanceof EndCrystalItem) return true;
        if (autoSwitch.get() == SwitchMode.None) return false;
        if (InvUtil.findInHotbar(Items.END_CRYSTAL).found()) return true;
        if (autoSwitch.get() == SwitchMode.Inventory) {
            return InvUtil.find(Items.END_CRYSTAL).found();
        }
        return false;
    }

    private boolean canSee(Vec3d target) {
        if (mc.world == null || mc.player == null) return false;
        return mc.world.raycast(new RaycastContext(mc.player.getEyePos(), target,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player)).getType() == HitResult.Type.MISS;
    }

    private void swingHand(Hand hand, boolean attack) {
        if (mc.player == null) return;
        switch (swingMode.get()) {
            case Visual -> mc.player.swingHand(hand);
            case Packet -> sendPacket(new HandSwingC2SPacket(hand));
            case Both -> {
                mc.player.swingHand(hand);
                sendPacket(new HandSwingC2SPacket(hand));
            }
        }
    }

    private float[] computeAngle(Vec3d vec) {
        Vec3d eyes = mc.player.getEyePos();
        double dx = vec.x - eyes.x;
        double dy = vec.y - eyes.y;
        double dz = vec.z - eyes.z;
        double dh = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dh));
        return new float[]{yaw, pitch};
    }

    private void sendPacket(net.minecraft.network.packet.Packet<?> packet) {
        if (mc.getNetworkHandler() != null) mc.getNetworkHandler().sendPacket(packet);
    }

    @Override
    public String getSuffix() {
        if (target == null) return null;
        return " » " + target.getName().getString();
    }

    /* ===================================================== */
    /* =================== Records & enums ================== */
    /* ===================================================== */

    private record PlaceData(BlockPos pos, BlockHitResult bhr, float damage, float selfDamage) {
    }

    private record ObbyData(BlockPos pos, BlockHitResult bhr, float damage, float selfDamage) {
    }

    public enum TargetMode {Distance, Health, FOV}

    public enum SwingMode {Visual, Packet, Both}

    public enum SwitchMode {None, Normal, Silent, Inventory}

    public enum Interact {Default, Strict}

    public enum RotateMode {Off, Snap, Slide, Smooth}
}
