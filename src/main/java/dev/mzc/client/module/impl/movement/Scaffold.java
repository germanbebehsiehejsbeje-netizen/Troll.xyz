package dev.mzc.client.module.impl.movement;

import dev.mzc.client.events.EventType;
import dev.mzc.client.events.player.MotionEvent;
import dev.mzc.client.manager.Managers;
import dev.mzc.client.manager.impl.RotationManager;
import dev.mzc.client.mixin.accessor.IMinecraftClient;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.rotation.MovementFix;
import dev.mzc.client.utils.rotation.RotationUtil;
import dev.mzc.client.utils.vector.Rotation;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class Scaffold extends Module {

    public final NumberValue<Integer> extend = new NumberValue<>("Extend", 1, 0, 5, 1);
    public final BoolValue tower = new BoolValue("Tower", true);
    public final BoolValue autoSwap = new BoolValue("AutoSwap", true);
    public final BoolValue safeWalk = new BoolValue("SafeWalk", true);
    public final BoolValue downward = new BoolValue("Downward (Sneak)", false);

    private BlockPlacement currentPlacement = null;
    private int previousSlot = -1;
    private boolean rotationInitialized = false;
    private float accumulatedYaw = 0f;
    private float accumulatedPitch = 0f;
    private boolean wasSneak = false;
    private boolean safeWalkActive = false;
    private final Random random = new Random();

    private float lastPlaceYaw = 0f;
    private float lastDeltaYaw = 0f;
    private long startTime = 0;

    public Scaffold() {
        super("Scaffold", Category.Movement);
    }

    @Override
    protected void onEnable() {
        currentPlacement = null;
        previousSlot = -1;
        rotationInitialized = false;
        wasSneak = false;
        safeWalkActive = false;

        lastPlaceYaw = 0f;
        lastDeltaYaw = 0f;
        startTime = System.currentTimeMillis();

        if (mc.player != null) {
            accumulatedYaw = mc.player.getYaw();
            accumulatedPitch = mc.player.getPitch();
        }
    }

    @Override
    protected void onDisable() {
        if (previousSlot != -1 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
            previousSlot = -1;
        }

        if (wasSneak) {
            mc.options.sneakKey.setPressed(false);
            wasSneak = false;
        }

        safeWalkActive = false;
        currentPlacement = null;
        rotationInitialized = false;
    }

    @EventHandler
    public void onMotion(MotionEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (event.getType() == EventType.PRE) {
            updateSafeWalk();
            handleTower();

            BlockPos targetPos = findPlacePosition();
            if (targetPos == null) {
                currentPlacement = null;
                return;
            }

            BlockPlacement placement = findPlacement(targetPos);
            if (placement == null) {
                currentPlacement = null;
                return;
            }

            currentPlacement = placement;
            rotateToPlacement(placement);

            if (safeWalk.get() && safeWalkActive) {
                mc.options.sneakKey.setPressed(true);
                wasSneak = true;
            } else if (wasSneak && !safeWalkActive) {
                mc.options.sneakKey.setPressed(false);
                wasSneak = false;
            }
        } else {
            if (currentPlacement != null && canPlaceBlock()) {
                tryPlaceBlock(currentPlacement);
            }
        }
    }

    private boolean canPlaceBlock() {
        return ((IMinecraftClient) mc).hookGetItemUseCooldown() <= 0 && !mc.player.isUsingItem();
    }

    private void updateSafeWalk() {
        if (!safeWalk.get()) {
            safeWalkActive = false;
            return;
        }

        if (mc.player.isOnGround()) {
            if (!hasFullSupportBelow()) {
                safeWalkActive = true;
                return;
            }

            Vec3d velocity = mc.player.getVelocity();
            double futureX = mc.player.getX() + velocity.x * 1.5;
            double futureZ = mc.player.getZ() + velocity.z * 1.5;

            BlockPos futureBelow = BlockPos.ofFloored(futureX, mc.player.getY() - 1, futureZ);
            BlockState belowState = mc.world.getBlockState(futureBelow);

            if (belowState.isReplaceable() || belowState.isAir()) {
                BlockState placed = currentPlacement != null
                        ? mc.world.getBlockState(currentPlacement.placePos)
                        : null;

                if (placed == null || placed.isReplaceable() || placed.isAir()) {
                    safeWalkActive = true;
                    return;
                }
            }
        }

        safeWalkActive = false;
    }

    private boolean hasFullSupportBelow() {
        Box box = mc.player.getBoundingBox();
        double y = mc.player.getY() - 0.01;

        double[][] points = {
                {box.minX, box.minZ},
                {box.minX, box.maxZ},
                {box.maxX, box.minZ},
                {box.maxX, box.maxZ},
                {(box.minX + box.maxX) / 2, (box.minZ + box.maxZ) / 2}
        };

        for (double[] point : points) {
            BlockPos pos = BlockPos.ofFloored(point[0], y - 0.5, point[1]);
            BlockState state = mc.world.getBlockState(pos);
            if (!state.isReplaceable() && !state.isAir()) {
                return true;
            }
        }

        return false;
    }

    private BlockPos findPlacePosition() {
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        BlockPos playerBlock = mc.player.getBlockPos();
        BlockPos below = playerBlock.down();

        if (downward.get() && mc.options.sneakKey.isPressed()) {
            BlockPos downPos = below.down();
            if (mc.world.getBlockState(downPos).isReplaceable()) {
                return downPos;
            }
        }

        if (mc.world.getBlockState(below).isReplaceable()) {
            return below;
        }

        Box box = mc.player.getBoundingBox();
        double y = (double) below.getY();
        double[][] corners = {
                {box.minX, box.minZ},
                {box.minX, box.maxZ},
                {box.maxX, box.minZ},
                {box.maxX, box.maxZ}
        };

        for (double[] corner : corners) {
            BlockPos cornerPos = BlockPos.ofFloored(corner[0], y, corner[1]);
            if (mc.world.getBlockState(cornerPos).isReplaceable()) {
                return cornerPos;
            }
        }

        int extendValue = extend.get();
        if (extendValue > 0) {
            Vec3d moveDir = getMoveDirection();
            if (moveDir.lengthSquared() > 0.001) {
                for (int i = 1; i <= extendValue; i++) {
                    BlockPos extendPos = BlockPos.ofFloored(
                            playerPos.x + moveDir.x * i * 0.6,
                            below.getY(),
                            playerPos.z + moveDir.z * i * 0.6
                    );
                    if (mc.world.getBlockState(extendPos).isReplaceable()) {
                        return extendPos;
                    }
                }
            }
        }

        Vec3d velocity = mc.player.getVelocity();
        if (Math.abs(velocity.x) > 0.05 || Math.abs(velocity.z) > 0.05) {
            for (double mult = 0.3; mult <= 2.0; mult += 0.3) {
                BlockPos predictedPos = BlockPos.ofFloored(
                        playerPos.x + velocity.x * mult,
                        below.getY(),
                        playerPos.z + velocity.z * mult
                );
                if (mc.world.getBlockState(predictedPos).isReplaceable()) {
                    if (hasValidNeighbor(predictedPos)) {
                        return predictedPos;
                    }
                }
            }
        }

        return null;
    }

    private boolean hasValidNeighbor(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockState state = mc.world.getBlockState(pos.offset(dir));
            if (!state.isReplaceable() && !state.isAir()) {
                return true;
            }
        }
        return false;
    }

    private Vec3d getMoveDirection() {
        float yaw = mc.player.getYaw();

        double forward = 0, strafe = 0;
        if (mc.options.forwardKey.isPressed()) forward += 1;
        if (mc.options.backKey.isPressed()) forward -= 1;
        if (mc.options.leftKey.isPressed()) strafe += 1;
        if (mc.options.rightKey.isPressed()) strafe -= 1;

        if (forward == 0 && strafe == 0) {
            Vec3d vel = mc.player.getVelocity();
            if (Math.abs(vel.x) > 0.05 || Math.abs(vel.z) > 0.05) {
                return new Vec3d(vel.x, 0, vel.z).normalize();
            }
            return Vec3d.ZERO;
        }

        double rad = Math.toRadians(yaw);
        double moveAngle = Math.atan2(forward, strafe) - Math.PI / 2;
        double finalAngle = rad + moveAngle;

        return new Vec3d(-Math.sin(finalAngle), 0, Math.cos(finalAngle)).normalize();
    }

    private static class BlockPlacement {
        BlockPos placePos;
        BlockPos neighborPos;
        Direction direction;
        Vec3d hitVec;

        BlockPlacement(BlockPos placePos, BlockPos neighborPos, Direction direction, Vec3d hitVec) {
            this.placePos = placePos;
            this.neighborPos = neighborPos;
            this.direction = direction;
            this.hitVec = hitVec;
        }
    }

    private BlockPlacement findPlacement(BlockPos targetPos) {
        Direction[] directions = {
                Direction.DOWN, Direction.UP,
                Direction.NORTH, Direction.SOUTH,
                Direction.WEST, Direction.EAST
        };

        List<BlockPlacement> candidates = new ArrayList<>();
        Vec3d eyePos = mc.player.getEyePos();

        for (Direction dir : directions) {
            BlockPos neighborPos = targetPos.offset(dir);
            BlockState neighborState = mc.world.getBlockState(neighborPos);

            if (neighborState.isReplaceable() || neighborState.isAir()) continue;
            if (isBlacklistedNeighbor(neighborState.getBlock())) continue;

            Direction placeFace = dir.getOpposite();

            if (!isFaceVisible(neighborPos, placeFace, eyePos)) continue;

            Vec3d hitVec = calculateHitVec(neighborPos, placeFace);

            double distance = eyePos.distanceTo(hitVec);
            if (distance > 4.5) continue;

            candidates.add(new BlockPlacement(targetPos, neighborPos, placeFace, hitVec));
        }

        if (candidates.isEmpty()) return null;

        Rotation currentAngle = Managers.ROTATION.getRotation();

        BlockPlacement best = null;
        double bestScore = Double.MAX_VALUE;

        for (BlockPlacement candidate : candidates) {
            double distance = eyePos.distanceTo(candidate.hitVec);
            Rotation requiredAngle = RotationUtil.calculate(candidate.hitVec);
            
            double angleDiff = Math.sqrt(Math.pow(MathHelper.wrapDegrees(currentAngle.yaw - requiredAngle.yaw), 2) + 
                                         Math.pow(currentAngle.pitch - requiredAngle.pitch, 2));

            double score = distance * 1.0 + angleDiff * 0.3;

            if (candidate.direction == Direction.UP) score -= 5.0;

            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        return best;
    }

    private boolean isFaceVisible(BlockPos blockPos, Direction face, Vec3d eyePos) {
        Box box = new Box(blockPos);

        return switch (face) {
            case UP -> eyePos.y > box.maxY;
            case DOWN -> eyePos.y < box.minY;
            case NORTH -> eyePos.z < box.minZ;
            case SOUTH -> eyePos.z > box.maxZ;
            case WEST -> eyePos.x < box.minX;
            case EAST -> eyePos.x > box.maxX;
        };
    }

    private Vec3d calculateHitVec(BlockPos blockPos, Direction face) {
        double x = (double) blockPos.getX();
        double y = (double) blockPos.getY();
        double z = (double) blockPos.getZ();

        double rx = 0.25 + random.nextDouble() * 0.5;
        double ry = 0.25 + random.nextDouble() * 0.5;
        double rz = 0.25 + random.nextDouble() * 0.5;

        return switch (face) {
            case UP -> new Vec3d(x + rx, y + 1.0, z + rz);
            case DOWN -> new Vec3d(x + rx, y, z + rz);
            case NORTH -> new Vec3d(x + rx, y + ry, z);
            case SOUTH -> new Vec3d(x + rx, y + ry, z + 1.0);
            case WEST -> new Vec3d(x, y + ry, z + rz);
            case EAST -> new Vec3d(x + 1.0, y + ry, z + rz);
        };
    }

    private boolean isBlacklistedNeighbor(Block block) {
        return block == Blocks.CHEST || block == Blocks.ENDER_CHEST ||
                block == Blocks.TRAPPED_CHEST || block == Blocks.CRAFTING_TABLE ||
                block == Blocks.FURNACE || block == Blocks.ENCHANTING_TABLE ||
                block == Blocks.ANVIL;
    }

    private void rotateToPlacement(BlockPlacement placement) {
        Rotation targetAngle = RotationUtil.calculate(placement.hitVec);

        float clampedPitch = MathHelper.clamp(targetAngle.pitch, 30f, 89f);
        targetAngle = new Rotation(targetAngle.yaw, clampedPitch);

        Rotation safeAngle = createSafeAngle(targetAngle);
        
        Managers.ROTATION.setRotations(
                safeAngle,
                15.0,
                MovementFix.NORMAL,
                RotationManager.Priority.High
        );
    }

    private Rotation createSafeAngle(Rotation rawAngle) {
        if (!rotationInitialized) {
            accumulatedYaw = Managers.ROTATION.getRotation().yaw;
            accumulatedPitch = Managers.ROTATION.getRotation().pitch;
            rotationInitialized = true;
        }

        float yawNoise = (random.nextFloat() - 0.5f) * 0.8f;
        float pitchNoise = (random.nextFloat() - 0.5f) * 0.6f;

        long elapsed = System.currentTimeMillis() - startTime;
        float timeSeconds = elapsed / 1000f;

        float idleYawNoise = (float) (
                Math.sin(timeSeconds * 1.7) * 2.5f +
                Math.sin(timeSeconds * 3.3) * 1.2f +
                Math.sin(timeSeconds * 0.9) * 1.8f
        );

        float idlePitchNoise = (float) (
                Math.sin(timeSeconds * 1.4) * 1.0f +
                Math.cos(timeSeconds * 2.1) * 0.7f
        );

        idleYawNoise += (random.nextFloat() - 0.5f) * 0.5f;
        idlePitchNoise += (random.nextFloat() - 0.5f) * 0.3f;

        float targetYaw = rawAngle.yaw + yawNoise + idleYawNoise;
        float targetPitch = rawAngle.pitch + pitchNoise + idlePitchNoise;

        targetPitch = MathHelper.clamp(targetPitch, 30f, 89f);

        float currentServerYaw = Managers.ROTATION.getRotation().yaw;

        float yawDelta = MathHelper.wrapDegrees(targetYaw - currentServerYaw);
        float pitchDelta = targetPitch - accumulatedPitch;

        float yawSpeed;
        float absYaw = Math.abs(yawDelta);

        if (absYaw < 5) {
            yawSpeed = 0.85f + random.nextFloat() * 0.15f;
        } else if (absYaw < 30) {
            yawSpeed = 0.50f + random.nextFloat() * 0.10f;
        } else if (absYaw < 90) {
            yawSpeed = 0.35f + random.nextFloat() * 0.10f;
        } else {
            yawSpeed = 0.25f + random.nextFloat() * 0.08f;
        }

        float pitchSpeed = 0.60f + random.nextFloat() * 0.10f;

        float yawStep = yawDelta * yawSpeed;
        float pitchStep = pitchDelta * pitchSpeed;

        yawStep = MathHelper.clamp(yawStep, -35f, 35f);
        pitchStep = MathHelper.clamp(pitchStep, -30f, 30f);

        float newYaw = currentServerYaw + yawStep;
        accumulatedPitch += pitchStep;
        accumulatedPitch = MathHelper.clamp(accumulatedPitch, -90f, 90f);

        newYaw = MathHelper.wrapDegrees(newYaw);
        accumulatedYaw = newYaw;

        accumulatedYaw = applyGCD(accumulatedYaw, currentServerYaw);
        accumulatedPitch = applyGCD(accumulatedPitch, Managers.ROTATION.getRotation().pitch);

        return new Rotation(accumulatedYaw, MathHelper.clamp(accumulatedPitch, -90f, 90f));
    }

    private float applyGCD(float target, float current) {
        float sensitivity = mc.options.getMouseSensitivity().getValue().floatValue();
        float f = sensitivity * 0.6F + 0.2F;
        float gcd = f * f * f * 1.2F;
        float delta = target - current;
        delta = Math.round(delta / gcd) * gcd;
        return current + delta;
    }

    private boolean tryPlaceBlock(BlockPlacement placement) {
        int blockSlot = findBlockSlot();
        if (blockSlot == -1) return false;

        if (!isLookingAtHitVec(placement)) {
            return false;
        }

        Rotation currentRot = Managers.ROTATION.getRotation();
        float currentYaw = currentRot.yaw;

        float currentDeltaX = Math.abs(MathHelper.wrapDegrees(currentYaw - lastPlaceYaw));
        float diffFromLastDelta = Math.abs(currentDeltaX - lastDeltaYaw);

        if (lastPlaceYaw != 0 && (diffFromLastDelta < 0.5f || currentDeltaX < 2.5f)) {
            float forcedShift = 3.0f + random.nextFloat() * 4.0f;
            if (random.nextBoolean()) forcedShift = -forcedShift;

            accumulatedYaw = MathHelper.wrapDegrees(currentYaw + forcedShift);
            accumulatedYaw = applyGCD(accumulatedYaw, currentYaw);

            Rotation forcedAngle = new Rotation(accumulatedYaw, accumulatedPitch);
            Managers.ROTATION.setRotations(forcedAngle, 10.0, MovementFix.NORMAL, RotationManager.Priority.High);

            return false;
        }

        if (autoSwap.get()) {
            if (previousSlot == -1) {
                previousSlot = mc.player.getInventory().getSelectedSlot();
            }
            if (mc.player.getInventory().getSelectedSlot() != blockSlot) {
                mc.player.getInventory().setSelectedSlot(blockSlot);
            }
        } else {
            ItemStack held = mc.player.getMainHandStack();
            if (!(held.getItem() instanceof BlockItem)) return false;
        }

        BlockHitResult hitResult = new BlockHitResult(
                placement.hitVec,
                placement.direction,
                placement.neighborPos,
                false
        );

        if (mc.interactionManager == null) return false;
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);

        if (result.isAccepted()) {
            mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            mc.player.swingHand(Hand.MAIN_HAND);

            ((IMinecraftClient) mc).hookSetItemUseCooldown(4);

            float newYaw = Managers.ROTATION.getRotation().yaw;
            lastDeltaYaw = Math.abs(MathHelper.wrapDegrees(newYaw - lastPlaceYaw));
            lastPlaceYaw = newYaw;

            if (autoSwap.get() && previousSlot != -1) {
                mc.player.getInventory().setSelectedSlot(previousSlot);
                previousSlot = -1;
            }
            return true;
        }

        return false;
    }

    private boolean isLookingAtHitVec(BlockPlacement placement) {
        Rotation currentAngle = Managers.ROTATION.getRotation();

        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = Vec3d.fromPolar(currentAngle.pitch, currentAngle.yaw);

        Vec3d endPos = eyePos.add(lookVec.multiply(5.0));

        Box targetBox = new Box(placement.neighborPos);

        Optional<Vec3d> hit = targetBox.raycast(eyePos, endPos);
        return hit.isPresent();
    }

    private int findBlockSlot() {
        int bestSlot = -1;
        int bestCount = 0;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof BlockItem blockItem)) continue;
            if (isUnusableBlock(blockItem.getBlock())) continue;

            if (stack.getCount() > bestCount) {
                bestCount = stack.getCount();
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    private boolean isUnusableBlock(Block block) {
        return block == Blocks.TNT || block == Blocks.SAND || block == Blocks.RED_SAND ||
                block == Blocks.GRAVEL || block == Blocks.ANVIL || block == Blocks.CHEST ||
                block == Blocks.ENDER_CHEST || block == Blocks.TRAPPED_CHEST ||
                block == Blocks.ENCHANTING_TABLE || block == Blocks.CRAFTING_TABLE ||
                block == Blocks.FURNACE || block == Blocks.TORCH || block == Blocks.WALL_TORCH ||
                block == Blocks.REDSTONE_TORCH || block == Blocks.LADDER || block == Blocks.VINE ||
                block == Blocks.LILY_PAD || block == Blocks.CACTUS;
    }

    private void handleTower() {
        if (!tower.get()) return;
        if (!mc.options.jumpKey.isPressed()) return;
        if (mc.player.isOnGround() && currentPlacement != null) {
            mc.player.jump();
        }
    }
}
