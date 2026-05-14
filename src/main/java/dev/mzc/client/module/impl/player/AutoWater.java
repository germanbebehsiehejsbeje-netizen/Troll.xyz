package dev.mzc.client.module.impl.player;

import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.input.MoveInputEvent;
import dev.mzc.client.events.player.MotionEvent;
import dev.mzc.client.events.EventType;
import dev.mzc.client.manager.Managers;
import dev.mzc.client.manager.impl.RotationManager;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.packet.PacketUtil;
import dev.mzc.client.utils.rotation.MovementFix;
import dev.mzc.client.utils.rotation.RotationUtil;
import dev.mzc.client.utils.vector.Rotation;
import dev.mzc.client.utils.world.BlockUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class AutoWater extends Module {

    private enum Flow {
        Web,
        Fire,
        Fall
    }

    private enum Phase {
        Wait,
        SwapBack
    }

    private final BoolValue rotate = new BoolValue("Rotate", true);
    private final BoolValue antiWeb = new BoolValue("AntiWeb", false);
    private final BoolValue autoMlg = new BoolValue("AutoMLG", false);
    private final NumberValue<Double> minFirePitch = new NumberValue<>("MinFirePitch", 60.0, 0.0, 90.0, 1.0);
    private final NumberValue<Integer> maxWaitTicks = new NumberValue<>("MaxWait", 40, 5, 100, 1);
    private final NumberValue<Integer> minWaitTicks = new NumberValue<>("MinWait", 5, 0, 40, 1);
    private final NumberValue<Integer> mlgCheckDown = new NumberValue<>("MLGCheckDown", 1, 0, 3, 1, autoMlg::get);
    private final NumberValue<Double> mlgOffset = new NumberValue<>("MLGOffset", 0.3, 0.0, 1.0, 0.05, autoMlg::get);

    private int originalSlot = -1;
    private int bucketSlot = -1;
    private int phaseTicks = 0;
    private int totalTicks = 0;

    private boolean inProgress = false;
    private boolean firePlacedConfirmed = false;
    private int pickupAlignedTicks = 0;
    private int pickupTries = 0;
    private boolean pendingPickup = false;
    private boolean fallPlacedConfirmed = false;
    private BlockPos fallAimPos = null;
    private Flow flow = null;
    private Phase phase = Phase.Wait;
    private BlockPos webPos = null;
    private BlockPos waterPos = null;
    private BlockPos aimPos = null;

    public AutoWater() {
        super("AutoWater", Category.Player);
        this.setType(ModuleType.Safe);
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @EventHandler
    public void onTick(TickEvent.Pre e) {
        if (nullCheck()) return;

        if (inProgress) {
            mc.player.setSprinting(false);

            ensureBucketSelected();

            if (rotate.get() && phase == Phase.Wait && aimPos != null) {
                applySilentRotation(aimPos);
            }

            phaseTicks++;
            totalTicks++;

            if (phase == Phase.Wait) {
                if (flow == Flow.Fire) {
                    tickFire();
                } else if (flow == Flow.Web) {
                    tickWeb();
                } else if (flow == Flow.Fall) {
                    tickFall();
                }
            } else if (phase == Phase.SwapBack) {
                if (phaseTicks >= 2) {
                    if (originalSlot != -1) {
                        mc.player.getInventory().setSelectedSlot(originalSlot);
                        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(originalSlot));
                    }
                    resetState();
                }
            }
            return;
        }

        if (!mc.options.useKey.isPressed()) return;

        ItemStack held = mc.player.getMainHandStack();
        boolean isMelee = held.isIn(ItemTags.SWORDS) || held.getItem() instanceof AxeItem;
        if (!isMelee) return;

        int wb = findWaterBucketInHotbar();
        if (wb == -1) return;

        if (mc.player.isOnFire()) {
            if (mc.player.getPitch() < minFirePitch.get()) return;
            if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return;
            BlockHitResult hit = (BlockHitResult) mc.crosshairTarget;
            if (!isNearPlayerFeet(hit.getBlockPos())) return;
            BlockPos water = hit.getBlockPos().offset(hit.getSide());
            startFlowFire(water, wb);
            return;
        }

        BlockPos webPos = getCrosshairWebPos();
        if (antiWeb.get() && webPos != null) {
            startFlowWeb(webPos, wb);
        }
    }

    @EventHandler
    public void onPostTick(TickEvent.Post e) {
        if (nullCheck()) return;
        if (inProgress) return;
        if (!autoMlg.get()) return;
        tryStartFallFlow();
    }

    private void tickFire() {
        boolean timeout = totalTicks >= maxWaitTicks.get();

        ItemStack main = mc.player.getMainHandStack();
        boolean holdingEmptyBucket = main.isOf(Items.BUCKET);
        boolean holdingWaterBucket = main.isOf(Items.WATER_BUCKET);

        if (!firePlacedConfirmed) {
            if (holdingEmptyBucket) {
                firePlacedConfirmed = true;
                pickupAlignedTicks = 0;
                pickupTries = 0;
                pendingPickup = false;
                waterPos = resolveWaterPos(waterPos);
                aimPos = waterPos;
                phaseTicks = 0;
                return;
            }

            if (phaseTicks >= 1) {
                phaseTicks = 0;
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            }

            if (timeout) {
                phase = Phase.SwapBack;
                phaseTicks = 0;
            }
            return;
        }

        if (holdingWaterBucket) {
            phase = Phase.SwapBack;
            aimPos = null;
            phaseTicks = 0;
            pickupAlignedTicks = 0;
            pickupTries = 0;
            pendingPickup = false;
            return;
        }

        if (!rotate.get()) {
            if (phaseTicks >= 1 || timeout) {
                pickupTries++;
                pendingPickup = true;
                pickupAlignedTicks = 0;
                phaseTicks = 0;
            }
            if (timeout) {
                phase = Phase.SwapBack;
                aimPos = null;
                pendingPickup = false;
            }
            return;
        }

        waterPos = resolveWaterPos(waterPos);
        aimPos = waterPos;
        if (waterPos == null) {
            if (timeout) {
                phase = Phase.SwapBack;
                aimPos = null;
                pickupAlignedTicks = 0;
                pickupTries = 0;
                pendingPickup = false;
                phaseTicks = 0;
            }
            return;
        }

        if (phaseTicks >= 1 || timeout) {
            pickupTries++;

            boolean aligned = applySilentRotation(waterPos);
            if (aligned) {
                pickupAlignedTicks++;
                if (pickupAlignedTicks >= 2) {
                    pendingPickup = true;
                    pickupAlignedTicks = 0;
                }
            } else {
                pickupAlignedTicks = 0;
                return;
            }

            phaseTicks = 0;
            if (timeout) {
                phase = Phase.SwapBack;
                aimPos = null;
                pendingPickup = false;
            }
        }
    }

    private void tickWeb() {
        boolean timeout = totalTicks >= maxWaitTicks.get();
        boolean minMet = totalTicks >= minWaitTicks.get();
        boolean webGone = webPos != null && !isCobweb(webPos);

        if ((minMet && webGone) || timeout) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            phase = Phase.SwapBack;
            aimPos = null;
            phaseTicks = 0;
        }
    }

    private void tickFall() {
        boolean timeout = totalTicks >= maxWaitTicks.get();

        ItemStack main = mc.player.getMainHandStack();
        boolean holdingWaterBucket = main.isOf(Items.WATER_BUCKET);
        boolean holdingEmptyBucket = main.isOf(Items.BUCKET);

        if (!fallPlacedConfirmed) {
            if (!holdingWaterBucket) {
                phase = Phase.SwapBack;
                aimPos = null;
                phaseTicks = 0;
                return;
            }

            fallAimPos = findMlgAimPos();
            aimPos = fallAimPos;

            if (fallAimPos == null) {
                if (timeout) {
                    phase = Phase.SwapBack;
                    aimPos = null;
                    phaseTicks = 0;
                }
                return;
            }

            if (phaseTicks >= 1) {
                if (rotate.get()) applySilentRotation(fallAimPos);
                sendUseItemAt(fallAimPos);
                fallPlacedConfirmed = true;
                phaseTicks = 0;
            }
            return;
        }

        if (holdingWaterBucket) {
            phase = Phase.SwapBack;
            aimPos = null;
            phaseTicks = 0;
            return;
        }

        if (holdingEmptyBucket && fallAimPos != null) {
            aimPos = fallAimPos;
            if (phaseTicks >= 1) {
                if (rotate.get()) applySilentRotation(fallAimPos);
                sendUseItemAt(fallAimPos);
                phaseTicks = 0;
            }
        }

        if (timeout) {
            phase = Phase.SwapBack;
            aimPos = null;
            phaseTicks = 0;
        }
    }

    private void startFlowWeb(BlockPos webPos, int waterBucketSlot) {
        this.flow = Flow.Web;
        this.phase = Phase.Wait;
        this.phaseTicks = 0;
        this.totalTicks = 0;
        this.webPos = webPos;
        this.waterPos = null;
        this.aimPos = webPos;
        this.firePlacedConfirmed = false;
        this.inProgress = true;

        mc.player.setSprinting(false);

        if (rotate.get() && aimPos != null) {
            applySilentRotation(aimPos);
        }

        originalSlot = mc.player.getInventory().getSelectedSlot();
        bucketSlot = waterBucketSlot;

        mc.player.getInventory().setSelectedSlot(bucketSlot);
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(bucketSlot));
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
    }

    private void startFlowFall(int waterBucketSlot) {
        this.flow = Flow.Fall;
        this.phase = Phase.Wait;
        this.phaseTicks = 0;
        this.totalTicks = 0;
        this.webPos = null;
        this.waterPos = null;
        this.firePlacedConfirmed = false;
        this.pendingPickup = false;
        this.pickupAlignedTicks = 0;
        this.pickupTries = 0;
        this.fallPlacedConfirmed = false;
        this.fallAimPos = null;
        this.aimPos = null;
        this.inProgress = true;

        mc.player.setSprinting(false);

        originalSlot = mc.player.getInventory().getSelectedSlot();
        bucketSlot = waterBucketSlot;

        mc.player.getInventory().setSelectedSlot(bucketSlot);
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(bucketSlot));
    }

    private void startFlowFire(BlockPos waterPos, int waterBucketSlot) {
        this.flow = Flow.Fire;
        this.phase = Phase.Wait;
        this.phaseTicks = 0;
        this.totalTicks = 0;
        this.webPos = null;
        this.waterPos = waterPos;
        this.aimPos = waterPos;
        this.firePlacedConfirmed = false;
        this.inProgress = true;

        mc.player.setSprinting(false);

        if (rotate.get() && aimPos != null) {
            applySilentRotation(aimPos);
        }

        originalSlot = mc.player.getInventory().getSelectedSlot();
        bucketSlot = waterBucketSlot;

        mc.player.getInventory().setSelectedSlot(bucketSlot);
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(bucketSlot));
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
    }

    private void ensureBucketSelected() {
        if (bucketSlot < 0 || bucketSlot > 8) return;
        if (mc.player.getInventory().getSelectedSlot() == bucketSlot) return;
        mc.player.getInventory().setSelectedSlot(bucketSlot);
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(bucketSlot));
    }

    private void resetState() {
        originalSlot = -1;
        bucketSlot = -1;
        phaseTicks = 0;
        totalTicks = 0;
        inProgress = false;
        firePlacedConfirmed = false;
        pickupAlignedTicks = 0;
        pickupTries = 0;
        pendingPickup = false;
        fallPlacedConfirmed = false;
        fallAimPos = null;
        flow = null;
        phase = Phase.Wait;
        webPos = null;
        waterPos = null;
        aimPos = null;
    }

    @EventHandler
    public void onMoveInput(MoveInputEvent event) {
        if (nullCheck()) return;
        if (!inProgress) return;
        if (flow != Flow.Fire) return;
        if (phase != Phase.Wait) return;
        if (!firePlacedConfirmed) return;
        event.setForward(0.0F);
        event.setStrafe(0.0F);
        event.setSprint(false);
    }

    @EventHandler
    public void onMotion(MotionEvent event) {
        if (nullCheck()) return;
        if (event.getType() != EventType.POST) return;
        if (!pendingPickup) return;
        if (!inProgress) return;
        if (phase != Phase.Wait) return;

        ensureBucketSelected();

        if (flow == Flow.Fire) {
            if (!pendingPickup) return;
            if (!firePlacedConfirmed) return;

            if (waterPos != null && rotate.get()) {
                applySilentRotation(waterPos);
            }

            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            if (pickupTries >= 3 && waterPos != null) {
                interactBlock(waterPos, Direction.UP);
            }
            pendingPickup = false;
            return;
        }
    }

    private BlockPos getCrosshairWebPos() {
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return null;
        BlockHitResult blockHit = (BlockHitResult) mc.crosshairTarget;
        BlockPos pos = blockHit.getBlockPos();
        if (isCobweb(pos)) return pos;
        return null;
    }

    private BlockHitResult getCrosshairFeetHit() {
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return null;
        BlockHitResult blockHit = (BlockHitResult) mc.crosshairTarget;
        BlockPos pos = blockHit.getBlockPos();

        BlockPos playerPos = mc.player.getBlockPos();
        if (pos.equals(playerPos) || pos.equals(playerPos.down())) return blockHit;
        return null;
    }

    private boolean isCobweb(BlockPos pos) {
        return mc.world.getBlockState(pos).isOf(Blocks.COBWEB);
    }

    private boolean tryStartFallFlow() {
        if (mc.world == null || mc.player == null) return false;
        if (mc.world.getRegistryKey() == World.NETHER) return false;
        if (!checkFalling()) return false;

        int wb = findWaterBucketInHotbar();
        if (wb == -1) return false;

        BlockPos pos = findMlgAimPos();
        if (pos == null) return false;

        startFlowFall(wb);
        fallAimPos = pos;
        aimPos = pos;

        if (rotate.get()) {
            applySilentRotation(pos);
        }

        return true;
    }

    private boolean checkFalling() {
        if (mc.player.isOnGround()) return false;
        if (mc.player.isGliding()) return false;
        if (mc.player.isTouchingWater()) return false;

        double fd = mc.player.fallDistance;
        double safe = mc.player.getSafeFallDistance();
        if (fd > safe) return true;

        return mc.player.getVelocity().y < -0.6 && fd > 1.0;
    }

    private BlockPos findMlgAimPos() {
        BlockPos base = mc.player.getBlockPos().down(mlgCheckDown.get());
        double off = mlgOffset.get();
        double[] xz = new double[]{off, -off};
        for (double xo : xz) {
            for (double zo : xz) {
                BlockPos under = new BlockPos(
                    (int) Math.floor(base.getX() + xo),
                    base.getY(),
                    (int) Math.floor(base.getZ() + zo)
                );

                if (mc.world.isAir(under)) continue;
                if (mc.world.getBlockState(under).isReplaceable()) continue;

                BlockPos aim = under.up();
                if (BlockUtil.getPlaceSide(aim) == null) continue;
                return aim;
            }
        }
        return null;
    }

    private void sendUseItemAt(BlockPos pos) {
        Rotation rot = RotationUtil.calculate(pos);
        PacketUtil.sendSequencedPacket(seq -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, seq, rot.yaw, rot.pitch));
    }

    private boolean applySilentRotation(BlockPos pos) {
        Rotation rot = RotationUtil.calculate(pos);
        Managers.ROTATION.setRotations(rot, 10.0, MovementFix.GRIM, RotationManager.Priority.Highest);

        if (Managers.ROTATION.rotations != null) {
            double yawDiff = Math.abs(MathHelper.wrapDegrees(Managers.ROTATION.rotations.yaw - rot.yaw));
            double pitchDiff = Math.abs(Managers.ROTATION.rotations.pitch - rot.pitch);
            return yawDiff <= 15 && pitchDiff <= 15;
        }

        return false;
    }

    private void interactBlock(BlockPos pos, Direction side) {
        Vec3d hitVec = new Vec3d(
                pos.getX() + 0.5 + side.getOffsetX() * 0.45,
                pos.getY() + 0.5 + side.getOffsetY() * 0.45,
                pos.getZ() + 0.5 + side.getOffsetZ() * 0.45
        );
        BlockHitResult bhr = new BlockHitResult(hitVec, side, pos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
    }

    private int findWaterBucketInHotbar() {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isOf(Items.WATER_BUCKET)) return i;
        }
        return -1;
    }

    private boolean isNearPlayerFeet(BlockPos pos) {
        BlockPos p = mc.player.getBlockPos();
        return Math.abs(pos.getX() - p.getX()) <= 1 && Math.abs(pos.getY() - p.getY()) <= 1 && Math.abs(pos.getZ() - p.getZ()) <= 1;
    }

    private BlockPos resolveWaterPos(BlockPos preferred) {
        if (preferred != null && mc.world.getBlockState(preferred).isOf(Blocks.WATER)) return preferred;

        BlockPos player = mc.player.getBlockPos();
        BlockPos best = null;
        int bestDist = Integer.MAX_VALUE;
        int r = 4;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = player.add(dx, dy, dz);
                    if (!mc.world.getBlockState(p).isOf(Blocks.WATER)) continue;
                    int dist = dx * dx + dy * dy + dz * dz;
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = p;
                    }
                }
            }
        }
        return best;
    }
}



