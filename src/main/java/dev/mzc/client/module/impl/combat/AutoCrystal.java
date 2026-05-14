package dev.mzc.client.module.impl.combat;

import dev.mzc.client.manager.Managers;
import dev.mzc.client.manager.impl.RotationManager;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.rotation.MovementFix;
import dev.mzc.client.utils.rotation.RotationUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Comparator;
import java.util.List;

public class AutoCrystal extends Module {

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final NumberValue<Double> minDamage = new NumberValue<>("MinDamage", 6.0, 0.0, 36.0, 0.5);
    private final NumberValue<Double> maxSelfDamage = new NumberValue<>("MaxSelfDamage", 10.0, 0.0, 36.0, 0.5);
    private final NumberValue<Double> range = new NumberValue<>("Range", 5.5, 0.0, 6.0, 0.1);
    private final NumberValue<Double> wallsRange = new NumberValue<>("WallsRange", 3.5, 0.0, 6.0, 0.1);
    private final NumberValue<Double> placeRange = new NumberValue<>("PlaceRange", 5.0, 0.0, 6.0, 0.1);
    private final NumberValue<Double> targetRange = new NumberValue<>("TargetRange", 10.0, 1.0, 20.0, 0.5);

    private final NumberValue<Integer> breakDelaySetting = new NumberValue<>("BreakDelay", 2, 0, 20, 1);
    private final NumberValue<Integer> placeDelaySetting = new NumberValue<>("PlaceDelay", 5, 0, 20, 1);

    private final BoolValue autoBreak = new BoolValue("AutoBreak", true);
    private final BoolValue autoPlace = new BoolValue("AutoPlace", true);
    private final BoolValue antiSuicide = new BoolValue("AntiSuicide", true);
    private final BoolValue rotate = new BoolValue("Rotate", true);

    private final EnumValue<SwingMode> swingMode = new EnumValue<>("Swing", SwingMode.Packet);
    private final EnumValue<TargetMode> targetMode = new EnumValue<>("TargetMode", TargetMode.Closest);

    private int breakTimer = 0;
    private int placeTimer = 0;
    private PlayerEntity currentTarget = null;

    public AutoCrystal() {
        super("AutoCrystal", Category.Combat);
        this.setType(ModuleType.Safe);

        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (!isEnabled() || mc.player == null || mc.world == null) return;

            currentTarget = findTarget();

            if (autoBreak.get()) tickBreak();
            if (autoPlace.get() && currentTarget != null) tickPlace();
        });
    }

    private void tickBreak() {
        if (breakTimer > 0) { breakTimer--; return; }

        EndCrystalEntity best = findBestCrystalToBreak();
        if (best == null || mc.player == null || mc.interactionManager == null) return;

        Vec3d crystalPos = new Vec3d(best.getX(), best.getY(), best.getZ());
        float self = estimateDamage(crystalPos, mc.player);

        if (antiSuicide.get() && (self >= mc.player.getHealth() + mc.player.getAbsorptionAmount() || self > maxSelfDamage.get())) return;

        if (rotate.get()) rotateTo(crystalPos);

        mc.interactionManager.attackEntity(mc.player, best);
        swingHand();
        breakTimer = breakDelaySetting.get();
    }

    private EndCrystalEntity findBestCrystalToBreak() {
        if (mc.world == null || mc.player == null) return null;
        EndCrystalEntity best = null;
        float bestDmg = minDamage.get().floatValue();

        List<EndCrystalEntity> crystals = mc.world.getEntitiesByClass(EndCrystalEntity.class,
                mc.player.getBoundingBox().expand(range.get()), e -> !e.isRemoved());

        for (EndCrystalEntity crystal : crystals) {
            Vec3d pos = new Vec3d(crystal.getX(), crystal.getY(), crystal.getZ());
            double dist = mc.player.getEyePos().distanceTo(pos);
            boolean los = canSee(pos);

            if (!los && dist > wallsRange.get()) continue;

            if (currentTarget != null) {
                float dmg = estimateDamage(pos, currentTarget);
                if (dmg > bestDmg) {
                    bestDmg = dmg;
                    best = crystal;
                }
            }
        }
        return best;
    }

    private void tickPlace() {
        if (placeTimer > 0) { placeTimer--; return; }

        BlockPos bestPos = findBestPlacePos();
        if (bestPos == null || mc.player == null || mc.interactionManager == null) return;

        if (!isHoldingCrystal()) return;

        Hand hand = getCrystalHand();
        Vec3d hitVec = new Vec3d(bestPos.getX() + 0.5, bestPos.getY() + 0.5, bestPos.getZ() + 0.5);

        if (rotate.get()) rotateTo(hitVec);

        BlockHitResult bhr = new BlockHitResult(hitVec, net.minecraft.util.math.Direction.UP, bestPos, false);
        mc.interactionManager.interactBlock(mc.player, hand, bhr);
        swingHand();
        placeTimer = placeDelaySetting.get();
    }

    private BlockPos findBestPlacePos() {
        if (currentTarget == null || mc.player == null || mc.world == null) return null;

        BlockPos playerPos = mc.player.getBlockPos();
        float bestDmg = minDamage.get().floatValue();
        BlockPos best = null;

        int r = (int) Math.ceil(placeRange.get());
        for (BlockPos pos : BlockPos.iterate(playerPos.add(-r, -3, -r), playerPos.add(r, 3, r))) {
            if (!isValidPlacePos(pos)) continue;

            Vec3d crystalVec = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
            if (mc.player.getEyePos().distanceTo(crystalVec) > placeRange.get()) continue;

            float dmgTarget = estimateDamage(crystalVec, currentTarget);
            float dmgSelf = estimateDamage(crystalVec, mc.player);

            if (dmgTarget > bestDmg && dmgSelf <= maxSelfDamage.get()) {
                bestDmg = dmgTarget;
                best = pos.toImmutable();
            }
        }
        return best;
    }

    private boolean isValidPlacePos(BlockPos pos) {
        if (mc.world == null) return false;
        if (!mc.world.getBlockState(pos).isOf(Blocks.OBSIDIAN) && !mc.world.getBlockState(pos).isOf(Blocks.BEDROCK)) return false;
        if (!mc.world.isAir(pos.up()) || !mc.world.isAir(pos.up(2))) return false;
        return mc.world.getOtherEntities(null, new Box(pos.up()).stretch(0, 1, 0)).isEmpty();
    }

    private float estimateDamage(Vec3d explosionPos, PlayerEntity player) {
        Vec3d playerCenter = new Vec3d(player.getX(), player.getY() + (player.getHeight() / 2.0), player.getZ());
        double dist = playerCenter.distanceTo(explosionPos);
        if (dist > 12.0) return 0;
        float baseDmg = (float) ((12.0 - dist) / 12.0 * 12.0);
        return baseDmg * (1.0f - Math.min(player.getArmor() / 25.0f, 0.8f));
    }

    private void rotateTo(Vec3d pos) {
        dev.mzc.client.utils.vector.Rotation rot = RotationUtil.calculate(pos);
        Managers.ROTATION.setRotations(rot, 100, MovementFix.NORMAL, RotationManager.Priority.High);
    }

    private boolean canSee(Vec3d target) {
        if (mc.world == null || mc.player == null) return false;
        return mc.world.raycast(new RaycastContext(mc.player.getEyePos(), target, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player)).getType() == HitResult.Type.MISS;
    }

    private Hand getCrystalHand() {
        if (mc.player != null && mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)) return Hand.MAIN_HAND;
        return Hand.OFF_HAND;
    }

    private boolean isHoldingCrystal() {
        if (mc.player == null) return false;
        return mc.player.getMainHandStack().isOf(Items.END_CRYSTAL) || mc.player.getOffHandStack().isOf(Items.END_CRYSTAL);
    }

    private void swingHand() {
        if (mc.player == null) return;
        if (swingMode.is(SwingMode.Packet)) {
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.HandSwingC2SPacket(Hand.MAIN_HAND));
            }
        } else {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private PlayerEntity findTarget() {
        if (mc.world == null || mc.player == null) return null;
        return mc.world.getPlayers().stream()
                .filter(p -> p != mc.player && p.isAlive() && mc.player.distanceTo(p) <= targetRange.get())
                .min(Comparator.comparingDouble(p -> mc.player.distanceTo(p)))
                .orElse(null);
    }

    @Override
    public String getSuffix() {
        return currentTarget != null ? " » " + currentTarget.getName().getString() : null;
    }

    public enum SwingMode { Default, Packet }
    public enum TargetMode { Closest, LowestHP }
}