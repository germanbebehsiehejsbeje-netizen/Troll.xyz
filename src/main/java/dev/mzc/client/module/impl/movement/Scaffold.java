package dev.mzc.client.module.impl.movement;

import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.client.TimerEvent;
import dev.mzc.client.events.input.MoveInputEvent;
import dev.mzc.client.events.player.MotionEvent;
import dev.mzc.client.events.player.SprintEvent;
import dev.mzc.client.manager.Managers;
import dev.mzc.client.manager.impl.RotationManager;
import dev.mzc.client.mixin.accessor.IMinecraftClient;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.module.impl.movement.scaffold.*;
import dev.mzc.client.utils.player.MovementUtil;
import dev.mzc.client.utils.rotation.MovementFix;
import dev.mzc.client.utils.vector.Rotation;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Full Scaffold module — port of LiquidBounce's ModuleScaffold using our RotationManager.
 *
 * Architecture:
 *   - {@link ScaffoldMovementPlanner}        chooses the optimal movement line
 *   - {@link ScaffoldTechniques}             selects the placement technique (Normal/Expand/GodBridge/Breezily)
 *   - {@link ScaffoldTargetFinder}           finds a face/hit-vec for placement
 *   - {@link ScaffoldTowers}                 tower behaviour (None/Motion/Pulldown/Karhu/Vulcan/Hypixel)
 *   - {@link ScaffoldFeatures}               sub-features (AutoBlock, Eagle, Down, Telly, HeadHitter,
 *                                            Ledge, JumpStrafe, SpeedLimiter, SprintControl, Strafe,
 *                                            Acceleration, Blink, Ceiling, MovementPrediction,
 *                                            StabilizeMovement)
 */
public class Scaffold extends Module {
    public static Scaffold INSTANCE;

    public enum SameYMode { Off, On, Falling, Hypixel }
    public enum RotationTimingMode { Normal, OnTick, OnTickSnap }
    public enum SwingMode { DoNotHide, HideClient, HideServer, HideBoth }

    /* ----------------- Settings ----------------- */
    public final EnumValue<ScaffoldTechniques.Technique> technique =
            new EnumValue<>("Technique", ScaffoldTechniques.Technique.Normal);
    public final EnumValue<ScaffoldTargetFinder.AimMode> aimMode =
            new EnumValue<>("AimMode", ScaffoldTargetFinder.AimMode.Stabilized);
    public final EnumValue<RotationTimingMode> rotationTiming =
            new EnumValue<>("RotationTiming", RotationTimingMode.Normal);
    public final EnumValue<ScaffoldTowers.TowerMode> towerMode =
            new EnumValue<>("Tower", ScaffoldTowers.TowerMode.None);
    public final EnumValue<SameYMode> sameY = new EnumValue<>("SameY", SameYMode.Off);
    public final EnumValue<SwingMode> swingMode = new EnumValue<>("Swing", SwingMode.DoNotHide);

    public final NumberValue<Integer> delayMin = new NumberValue<>("DelayMin", 0, 0, 40, 1);
    public final NumberValue<Integer> delayMax = new NumberValue<>("DelayMax", 0, 0, 40, 1);
    public final NumberValue<Float> minDist = new NumberValue<>("MinDist", 0.0f, 0.0f, 0.25f, 0.01f);
    public final NumberValue<Float> timer = new NumberValue<>("Timer", 1.0f, 0.1f, 10.0f, 0.05f);
    public final NumberValue<Double> rotationSpeed = new NumberValue<>("RotationSpeed", 1.0, 0.1, 1.0, 0.05);

    public final BoolValue requiresSight = new BoolValue("RequiresSight", false);
    public final BoolValue considerInventory = new BoolValue("ConsiderInventory", false);
    public final BoolValue safeWalk = new BoolValue("SafeWalk", true);

    // AutoBlock
    public final BoolValue autoBlock = new BoolValue("AutoBlock", true);
    public final BoolValue autoBlockAlwaysHold = new BoolValue("AlwaysHoldBlock", false, autoBlock::get);
    public final NumberValue<Integer> autoBlockDoNotUseBelow =
            new NumberValue<>("DoNotUseBelowCount", 0, 0, 64, 1, autoBlock::get);

    // Eagle
    public final BoolValue eagle = new BoolValue("Eagle", false);
    public final NumberValue<Integer> eagleEvery = new NumberValue<>("EagleEvery", 1, 1, 8, 1, eagle::get);

    // Down (sneak to go down)
    public final BoolValue downward = new BoolValue("Down", false);

    // Telly
    public final BoolValue telly = new BoolValue("Telly", false);
    public final EnumValue<ScaffoldFeatures.Telly.Mode> tellyResetMode =
            new EnumValue<>("TellyReset", ScaffoldFeatures.Telly.Mode.Reset, telly::get);
    public final NumberValue<Integer> tellyEvery = new NumberValue<>("TellyEvery", 4, 1, 20, 1, telly::get);

    // HeadHitter
    public final BoolValue headHitter = new BoolValue("HeadHitter", false);

    // Ledge
    public final BoolValue ledge = new BoolValue("Ledge", true);
    public final EnumValue<ScaffoldFeatures.Ledge.Mode> ledgeMode =
            new EnumValue<>("LedgeMode", ScaffoldFeatures.Ledge.Mode.Jump, ledge::get);

    // GodBridge sub-modes (visible only for GodBridge technique). Multi-pick like LB's `multiEnumChoice`.
    public final dev.mzc.client.values.impl.MultiBoolValue godBridgeModes =
            new dev.mzc.client.values.impl.MultiBoolValue(
                    "GodBridgeModes",
                    () -> technique.get() == ScaffoldTechniques.Technique.GodBridge,
                    java.util.List.of(
                            new BoolValue("Jump", true),
                            new BoolValue("Sneak", false),
                            new BoolValue("StopInput", false),
                            new BoolValue("Backwards", false)
                    )
            );
    public final NumberValue<Integer> godBridgeForceSneakBelow =
            new NumberValue<>("ForceSneakBelowCount", 3, 0, 10, 1,
                    () -> technique.get() == ScaffoldTechniques.Technique.GodBridge);

    // JumpStrafe
    public final BoolValue jumpStrafe = new BoolValue("JumpStrafe", false);

    // SpeedLimiter
    public final BoolValue speedLimiter = new BoolValue("SpeedLimiter", false);
    public final NumberValue<Double> speedLimit = new NumberValue<>("SpeedLimit", 0.281, 0.05, 0.5, 0.001, speedLimiter::get);

    // SprintControl
    public final BoolValue sprintControl = new BoolValue("SprintControl", false);
    public final EnumValue<ScaffoldFeatures.SprintControl.Mode> sprintClient =
            new EnumValue<>("SprintClient", ScaffoldFeatures.SprintControl.Mode.ForceNoSprint, sprintControl::get);
    public final EnumValue<ScaffoldFeatures.SprintControl.Mode> sprintServer =
            new EnumValue<>("SprintServer", ScaffoldFeatures.SprintControl.Mode.ForceNoSprint, sprintControl::get);

    // Strafe
    public final BoolValue strafe = new BoolValue("Strafe", false);
    public final NumberValue<Double> strafeAccel = new NumberValue<>("StrafeAccel", 0.07, 0.0, 0.5, 0.01, strafe::get);

    // Acceleration
    public final BoolValue acceleration = new BoolValue("Acceleration", false);
    public final NumberValue<Double> accelBoost = new NumberValue<>("AccelBoost", 1.05, 1.0, 1.5, 0.01, acceleration::get);

    // Blink
    public final BoolValue blink = new BoolValue("Blink", false);
    public final NumberValue<Integer> blinkDelay = new NumberValue<>("BlinkDelay", 5, 0, 40, 1, blink::get);

    // Ceiling
    public final BoolValue ceiling = new BoolValue("Ceiling", false);

    // MovementPrediction
    public final BoolValue movementPrediction = new BoolValue("MovementPrediction", true);

    // StabilizeMovement
    public final BoolValue stabilizeMovement = new BoolValue("StabilizeMovement", false);

    // 8-direction snap movement (auto-correct strafe to 8 cardinal/diagonal directions)
    public final BoolValue snap8 = new BoolValue("Snap8Directions", false);
    public final BoolValue snap8OnlyMoving = new BoolValue("Snap8OnlyMoving", true, snap8::get);

    /* ----------------- State ----------------- */
    private final ScaffoldFeatures.Eagle eagleFeat = new ScaffoldFeatures.Eagle();
    private final ScaffoldFeatures.Down downFeat = new ScaffoldFeatures.Down();
    private final ScaffoldFeatures.Telly tellyFeat = new ScaffoldFeatures.Telly();
    private final ScaffoldFeatures.HeadHitter headHitterFeat = new ScaffoldFeatures.HeadHitter();
    private final ScaffoldFeatures.Ledge ledgeFeat = new ScaffoldFeatures.Ledge();
    private final ScaffoldFeatures.JumpStrafe jumpStrafeFeat = new ScaffoldFeatures.JumpStrafe();
    private final ScaffoldFeatures.SpeedLimiter speedLimiterFeat = new ScaffoldFeatures.SpeedLimiter();
    private final ScaffoldFeatures.SprintControl sprintCtrlFeat = new ScaffoldFeatures.SprintControl();
    private final ScaffoldFeatures.Strafe strafeFeat = new ScaffoldFeatures.Strafe();
    private final ScaffoldFeatures.Acceleration accelFeat = new ScaffoldFeatures.Acceleration();
    private final ScaffoldFeatures.Blink blinkFeat = new ScaffoldFeatures.Blink();
    private final ScaffoldFeatures.Ceiling ceilingFeat = new ScaffoldFeatures.Ceiling();
    private final ScaffoldFeatures.MovementPrediction predictionFeat = new ScaffoldFeatures.MovementPrediction();
    private final ScaffoldFeatures.StabilizeMovement stabilizeFeat = new ScaffoldFeatures.StabilizeMovement();
    private final ScaffoldFeatures.AutoBlock autoBlockFeat = new ScaffoldFeatures.AutoBlock();

    private ScaffoldTowers.Tower tower = new ScaffoldTowers.None();
    private ScaffoldTowers.TowerMode currentTowerMode = ScaffoldTowers.TowerMode.None;
    private boolean wasTowering = false;

    private BlockPlacementTarget currentTarget;
    private ScaffoldMovementPlanner.Line currentOptimalLine;
    private int placementDelay = 0;
    private int placementY = 0;
    private int startY = 0;
    private int jumps = 2;
    private int forceSneak = 0;

    public Scaffold() {
        super("Scaffold", Category.Movement);
        this.setType(ModuleType.Hack);
        INSTANCE = this;
    }

    @Override
    public String getSuffix() {
        return technique.get().name();
    }

    @Override
    public void onEnable() {
        if (mc.player != null) {
            placementY = mc.player.getBlockY() - 1;
            startY = mc.player.getBlockY();
            jumps = 2;
        }
        ScaffoldMovementPlanner.reset();
        ScaffoldTechniques.resetGodBridge();
        eagleFeat.reset();
        tellyFeat.reset();
        blinkFeat.reset();
        currentTarget = null;
        currentOptimalLine = null;
        placementDelay = 0;
        forceSneak = 0;
        wasTowering = false;
        currentTowerMode = towerMode.get();
        tower = ScaffoldTowers.create(currentTowerMode);
        syncFeatureFlags();
    }

    @Override
    public void onDisable() {
        if (mc.player != null) autoBlockFeat.reset(mc.player);
        ScaffoldMovementPlanner.reset();
        ScaffoldTechniques.resetGodBridge();
        currentTarget = null;
        currentOptimalLine = null;
        if (tower != null) tower.reset();
        forceSneak = 0;
    }

    private void syncFeatureFlags() {
        // Wire option values to feature flags.
        eagleFeat.enabled = eagle.get();
        eagleFeat.eagleEvery = eagleEvery.get();
        downFeat.enabled = downward.get();
        tellyFeat.enabled = telly.get();
        tellyFeat.resetMode = tellyResetMode.get();
        tellyFeat.placeEvery = tellyEvery.get();
        headHitterFeat.enabled = headHitter.get();
        ledgeFeat.enabled = ledge.get();
        ledgeFeat.mode = ledgeMode.get();
        // GodBridge: pick a random enabled mode from the multi-toggle settings.
        if (technique.get() == ScaffoldTechniques.Technique.GodBridge) {
            ledgeFeat.forcedMode = pickGodBridgeMode();
        } else {
            ledgeFeat.forcedMode = null;
        }
        jumpStrafeFeat.enabled = jumpStrafe.get();
        speedLimiterFeat.enabled = speedLimiter.get();
        speedLimiterFeat.maxHorizontalSpeed = speedLimit.get();
        sprintCtrlFeat.enabled = sprintControl.get();
        sprintCtrlFeat.clientMode = sprintClient.get();
        sprintCtrlFeat.serverMode = sprintServer.get();
        strafeFeat.enabled = strafe.get();
        strafeFeat.strafeAccel = strafeAccel.get();
        accelFeat.enabled = acceleration.get();
        accelFeat.boost = accelBoost.get();
        blinkFeat.enabled = blink.get();
        blinkFeat.delay = blinkDelay.get();
        ceilingFeat.enabled = ceiling.get();
        predictionFeat.enabled = movementPrediction.get();
        stabilizeFeat.enabled = stabilizeMovement.get();
        autoBlockFeat.enabled = autoBlock.get();
        autoBlockFeat.alwaysHoldBlock = autoBlockAlwaysHold.get();
        autoBlockFeat.doNotUseBelowCount = autoBlockDoNotUseBelow.get();

        // Re-create tower if mode changed
        if (currentTowerMode != towerMode.get()) {
            currentTowerMode = towerMode.get();
            tower = ScaffoldTowers.create(currentTowerMode);
        }
    }

    /* ----------------- Event handlers ----------------- */

    @EventHandler
    public void onTimer(TimerEvent event) {
        if (nullCheck()) return;
        if (event.isCancelled() || event.isModified()) return;
        if (timer.get() != 1.0f) event.set(timer.get());
    }

    @EventHandler
    public void onMoveInput(MoveInputEvent event) {
        if (nullCheck()) return;
        syncFeatureFlags();

        // Compute optimal movement line each input tick.
        if (event.getForward() != 0 || event.getStrafe() != 0) {
            currentOptimalLine = ScaffoldMovementPlanner.getOptimalMovementLine(event.getForward(), event.getStrafe());
        } else {
            currentOptimalLine = null;
        }

        // Force sneak (eagle / safewalk / ledge sneakTime)
        if (forceSneak > 0) {
            event.setSneak(true);
            forceSneak--;
        }

        // Eagle
        if (eagleFeat.shouldEagle(event.getForward() != 0 || event.getStrafe() != 0)) {
            event.setSneak(true);
        }

        // Ledge
        var ledgeAction = ledgeFeat.compute(mc.player, currentTarget);
        if (ledgeAction.jump()) event.setJump(true);
        if (ledgeAction.stopInput()) {
            event.setForward(0);
            event.setStrafe(0);
        } else if (ledgeAction.stepBack()) {
            event.setForward(-1);
            event.setStrafe(0);
        }
        if (ledgeAction.sneakTime() > forceSneak) {
            event.setSneak(true);
            forceSneak = ledgeAction.sneakTime();
        }

        // 8-directional movement snap — re-direct (forward, strafe) so the world-space movement
        // direction lines up with the nearest of the 8 cardinal/diagonal axes. Keeps placements
        // on a consistent line and prevents drift off the side of the bridge.
        if (snap8.get()) {
            float fwd = event.getForward();
            float str = event.getStrafe();
            boolean hasInput = fwd != 0 || str != 0;
            if (hasInput || !snap8OnlyMoving.get()) {
                applySnap8Correction(event, fwd, str);
            }
        }
    }

    /**
     * Convert (forward, strafe) -> world-space direction -> snap to one of 8 axes -> back to (forward, strafe).
     */
    private void applySnap8Correction(MoveInputEvent event, float fwd, float str) {
        if (mc.player == null) return;
        if (fwd == 0 && str == 0) return;

        // Player input -> world direction
        float yaw = mc.player.getYaw();
        double rad = Math.toRadians(yaw);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        double worldX = -sin * fwd - cos * str;
        double worldZ = cos * fwd - sin * str;
        double len = Math.hypot(worldX, worldZ);
        if (len < 1e-4) return;
        worldX /= len; worldZ /= len;

        Vec3d snapped = ScaffoldMovementPlanner.snapDirectionTo8(new Vec3d(worldX, 0, worldZ));
        if (snapped.lengthSquared() < 1e-6) return;

        // Convert back to player-relative (forward, strafe) maintaining ±1 magnitudes.
        // Rotation matrix inverse: yaw rotates world by -yaw to player-frame.
        double pf = -sin * snapped.x + cos * snapped.z;
        double ps = -cos * snapped.x - sin * snapped.z;

        // Quantize to {-1, 0, 1}; threshold 0.5 separates pure axis from diagonal.
        float newFwd = pf > 0.5 ? 1f : pf < -0.5 ? -1f : 0f;
        float newStr = ps > 0.5 ? 1f : ps < -0.5 ? -1f : 0f;
        if (newFwd == 0 && newStr == 0) return;
        event.setForward(newFwd);
        event.setStrafe(newStr);
    }

    @EventHandler
    public void onSprint(SprintEvent event) {
        if (nullCheck()) return;
        Boolean override = sprintCtrlFeat.overrideClientSprint();
        if (override != null) event.setSprint(override);
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;
        syncFeatureFlags();

        eagleFeat.onTick();
        autoBlockFeat.onTick(mc.player);
        blinkFeat.tick();

        if (mc.player.isOnGround()) {
            placementY = mc.player.getBlockY() - 1;
            jumps++;
            wasTowering = false;
        }
        if (mc.options.jumpKey.isPressed()) {
            startY = mc.player.getBlockY();
            jumps = 2;
        }

        // Movement-affecting features
        accelFeat.apply(mc.player);
        speedLimiterFeat.apply(mc.player);
        jumpStrafeFeat.apply(mc.player);
        stabilizeFeat.apply(mc.player, currentOptimalLine);
        if (currentOptimalLine != null) {
            // Strafe approximation: use forward=1 as we are bridging
            strafeFeat.apply(mc.player, 1.0f, 0.0f);
        }
    }

    @EventHandler
    public void onMotion(MotionEvent event) {
        if (nullCheck()) return;

        if (event.getType() == dev.mzc.client.events.EventType.PRE) {
            // Tower motion adjustments before sending packets
            if (currentTowerMode != ScaffoldTowers.TowerMode.None
                    && mc.options.jumpKey.isPressed()) {
                wasTowering = true;
                tower.onMotion(mc.player);
            }

            updateTargetAndRotation();
            return;
        }

        // POST: actually try to place block
        if (placementDelay > 0) {
            placementDelay--;
            return;
        }
        if (currentTarget == null) return;
        if (((IMinecraftClient) mc).hookGetItemUseCooldown() > 0) return;
        if (mc.player.isUsingItem()) return;

        // Telly cadence
        if (telly.get() && !tellyFeat.canPlace()) return;

        // Blink: hold packets from being sent for a few ticks
        if (blinkFeat.shouldHoldPackets()) return;

        // Make sure we have a block
        ItemStack stack = mc.player.getMainHandStack();
        boolean hasBlockMain = ScaffoldBlockSelection.isValidBlock(stack);
        boolean hasBlockOff = ScaffoldBlockSelection.isValidBlock(mc.player.getOffHandStack());

        if (!hasBlockMain && autoBlockFeat.enabled) {
            if (autoBlockFeat.swap(mc.player)) {
                stack = mc.player.getMainHandStack();
                hasBlockMain = ScaffoldBlockSelection.isValidBlock(stack);
            }
        }
        if (!hasBlockMain && !hasBlockOff) return;

        Hand hand = hasBlockMain ? Hand.MAIN_HAND : Hand.OFF_HAND;

        // Place using the finder-computed BlockHitResult (interactedBlockPos + direction + hitVec).
        // The finder already knows which block to click and where on the face — we trust it.
        // This matches LB's `doPlacement(currentCrosshairTarget, ...)` flow.
        BlockHitResult bhr = new BlockHitResult(
                currentTarget.hitVec(),
                currentTarget.direction(),
                currentTarget.interactedBlockPos(),
                false
        );

        // Apply minDist
        if (!checkMinDistFor(bhr)) return;

        // Send a rotation packet matching the placement rotation BEFORE the interact packet,
        // so the server sees the correct yaw/pitch for this click. Required for techniques
        // like GodBridge where the placement direction differs sharply from the actual camera.
        Rotation placeRot = currentTarget.rotation();
        try {
            mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full(
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    placeRot.yaw, placeRot.pitch,
                    mc.player.isOnGround(), mc.player.horizontalCollision
            ));
        } catch (Throwable ignored) {
            // Best-effort; if Yarn signature changed we just skip the rotation packet.
        }

        // Place
        ActionResult result = mc.interactionManager.interactBlock(mc.player, hand, bhr);

        if (result.isAccepted()) {
            handleSwing(hand);
            BlockPos placed = currentTarget.placedBlock();
            ScaffoldMovementPlanner.trackPlacedBlock(placed);
            eagleFeat.onBlockPlacement();
            sprintCtrlFeat.onBlockPlacement();
            blinkFeat.onBlockPlacement();
            predictionFeat.onPlace();

            placementDelay = randomDelay();

            currentTarget = null;
        }
    }

    private boolean checkMinDistFor(BlockHitResult bhr) {
        if (minDist.get() <= 0.001f) return true;
        Vec3d diff = bhr.getPos().subtract(mc.player.getEyePos());
        var side = bhr.getSide();
        if (side.getAxis() == net.minecraft.util.math.Direction.Axis.Y) return true;
        double dist = (side == net.minecraft.util.math.Direction.NORTH || side == net.minecraft.util.math.Direction.SOUTH) ? diff.z : diff.x;
        return Math.abs(dist) >= minDist.get();
    }

    private void handleSwing(Hand hand) {
        switch (swingMode.get()) {
            case DoNotHide -> mc.player.swingHand(hand);
            case HideClient -> mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(hand));
            case HideServer -> mc.player.swingHand(hand);
            case HideBoth -> {}
        }
    }

    private int randomDelay() {
        int min = Math.min(delayMin.get(), delayMax.get());
        int max = Math.max(delayMin.get(), delayMax.get());
        if (min == max) return min;
        return min + (int) (Math.random() * (max - min + 1));
    }

    /* ----------------- Target selection ----------------- */

    private void updateTargetAndRotation() {
        if (mc.player == null) return;

        ItemStack bestStack = mc.player.getMainHandStack();
        if (!ScaffoldBlockSelection.isValidBlock(bestStack)) {
            int slot = autoBlockFeat.findBestHotbarSlot(mc.player);
            if (slot != -1) bestStack = mc.player.getInventory().getStack(slot);
        }
        if (!ScaffoldBlockSelection.isValidBlock(bestStack)) return;

        Vec3d predictedPos = predictionFeat.predict(mc.player, currentOptimalLine);
        Vec3d eyeAtPredicted = predictedPos.add(0, mc.player.getStandingEyeHeight(), 0);

        BlockPos basePos = computeBasePos(predictedPos);
        if (currentTowerMode != ScaffoldTowers.TowerMode.None
                && (mc.options.jumpKey.isPressed() || wasTowering)) {
            basePos = tower.getTargetedPosition(basePos);
        }

        boolean isGoingDown = downFeat.shouldGoDown();
        boolean isHeadHittering = headHitterFeat.isHittingHead(mc.player);
        boolean isTowering = currentTowerMode != ScaffoldTowers.TowerMode.None
                && (mc.options.jumpKey.isPressed() || wasTowering);

        BlockPlacementTarget target = ScaffoldTechniques.find(
                technique.get(), aimMode.get(), basePos,
                predictedPos, eyeAtPredicted,
                currentOptimalLine, bestStack,
                isGoingDown, isHeadHittering,
                !isTowering // prefer horizontal placements when not towering
        );

        if (target == null) {
            currentTarget = null;
            return;
        }

        // requiresSight
        if (requiresSight.get()) {
            // raycast from player using target.rotation
            BlockHitResult bhr = raycastFromPlayer(target.rotation());
            if (bhr == null || !bhr.getBlockPos().equals(target.interactedBlockPos())) {
                currentTarget = null;
                return;
            }
        }

        currentTarget = target;

        // Telly says don't aim this tick
        if (telly.get() && tellyFeat.doNotAim) return;

        if (rotationTiming.get() == RotationTimingMode.Normal) {
            applyRotation(target.rotation());
        }
    }

    private void applyRotation(Rotation rot) {
        Managers.ROTATION.setRotations(
                rot,
                rotationSpeed.get(),
                MovementFix.NORMAL,
                RotationManager.Priority.Medium
        );
    }

    private BlockPos computeBasePos(Vec3d predictedPos) {
        BlockPos pBlock = BlockPos.ofFloored(predictedPos.x, predictedPos.y, predictedPos.z);
        // Use the cached placementY (last known ground level) so that we keep targeting blocks
        // at the correct height while jumping/airborne. Without this, target Y drifts up with
        // the player and the finder cannot place anything → player falls.
        switch (sameY.get()) {
            case Off -> { return new BlockPos(pBlock.getX(), placementY, pBlock.getZ()); }
            case On -> { return new BlockPos(pBlock.getX(), placementY, pBlock.getZ()); }
            case Falling -> {
                if (mc.player.getVelocity().y < 0.2) {
                    return new BlockPos(pBlock.getX(), placementY, pBlock.getZ());
                }
                return pBlock.down();
            }
            case Hypixel -> {
                double vy = mc.player.getVelocity().y;
                if (Math.abs(vy + 0.15233518685055708) < 1e-6 && jumps >= 2) {
                    jumps = 0;
                    return new BlockPos(pBlock.getX(), startY, pBlock.getZ());
                }
                return new BlockPos(pBlock.getX(), startY - 1, pBlock.getZ());
            }
        }
        return new BlockPos(pBlock.getX(), placementY, pBlock.getZ());
    }

    private BlockHitResult raycastFromPlayer(Rotation rot) {
        if (mc.player == null || mc.world == null) return null;
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = Vec3d.fromPolar(rot.pitch, rot.yaw);
        Vec3d end = eye.add(look.multiply(5.0));
        var raycast = mc.world.raycast(new net.minecraft.world.RaycastContext(
                eye, end,
                net.minecraft.world.RaycastContext.ShapeType.OUTLINE,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,
                mc.player
        ));
        return raycast;
    }

    /* ----------------- Public API ----------------- */
    public BlockPlacementTarget getCurrentTarget() { return currentTarget; }

    /**
     * Pick a GodBridge ledge mode using the {@link #godBridgeModes} multi-toggle.
     * If the player's block count is low ({@link #godBridgeForceSneakBelow}), force Sneak (matches LB).
     */
    private ScaffoldFeatures.Ledge.Mode pickGodBridgeMode() {
        // Force sneak if running out of blocks
        int below = godBridgeForceSneakBelow.get();
        if (mc.player != null && countScaffoldBlocks(mc.player) < below) {
            return ScaffoldFeatures.Ledge.Mode.Sneak;
        }

        var enabled = new java.util.ArrayList<ScaffoldFeatures.Ledge.Mode>();
        if (godBridgeModes.isEnabled("Jump")) enabled.add(ScaffoldFeatures.Ledge.Mode.Jump);
        if (godBridgeModes.isEnabled("Sneak")) enabled.add(ScaffoldFeatures.Ledge.Mode.Sneak);
        if (godBridgeModes.isEnabled("StopInput")) enabled.add(ScaffoldFeatures.Ledge.Mode.StopInput);
        if (godBridgeModes.isEnabled("Backwards")) enabled.add(ScaffoldFeatures.Ledge.Mode.Backwards);
        if (enabled.isEmpty()) return ScaffoldFeatures.Ledge.Mode.Jump;
        return enabled.get((int) (Math.random() * enabled.size()));
    }

    private int countScaffoldBlocks(net.minecraft.entity.player.PlayerEntity p) {
        int count = 0;
        for (int i = 0; i < 9; i++) {
            var stack = p.getInventory().getStack(i);
            if (ScaffoldBlockSelection.isValidBlock(stack)) count += stack.getCount();
        }
        var off = p.getOffHandStack();
        if (ScaffoldBlockSelection.isValidBlock(off)) count += off.getCount();
        return count;
    }
}
