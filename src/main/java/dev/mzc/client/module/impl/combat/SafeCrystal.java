package dev.mzc.client.module.impl.combat;

import dev.mzc.client.Sakura;
import dev.mzc.client.manager.Managers;
import dev.mzc.client.module.impl.combat.KillAura;
import dev.mzc.client.utils.vector.Rotation;
import dev.mzc.client.values.impl.BoolValue;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.NumberValue;


/**
 * AutoCrystal = CrystalClicker + CrystalPlacer
 * 逻辑保持完全一致，仅增加开关
 */
public class SafeCrystal extends Module {

    private final MinecraftClient mc = MinecraftClient.getInstance();

    /* ================= 设置开关 ================= */

    private final BoolValue autoBreak = new BoolValue("AutoBreak", true);
    private final BoolValue autoPlace = new BoolValue("AutoPlace", true);
    private final NumberValue<Double> AntiSuicide = new NumberValue<>("AntiSuicide", 2.0, 1.0, 20.0, 0.5);
    private final NumberValue<Double> placeRange = new NumberValue<>("Place Range", 3.6, 1.0, 6.0, 0.1);
    private final NumberValue<Double> fov = new NumberValue<>("FOV", 180.0, 30.0, 360.0, 1.0);
    private final BoolValue onlyWhenFacing = new BoolValue("Only When Facing", true);
    private final BoolValue checkDistance = new BoolValue("Check Distance", true);


    /* ================= 冷却 ================= */

    private int attackCooldown = 0;
    private int placeCooldown = 0;

    private static final double MAX_ATTACK_DISTANCE = 3.6D;

    public SafeCrystal() {
        super("SafeCrystal",Category.Combat);
        this.setType(ModuleType.Safe);

        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (!isEnabled() || mc.player == null || mc.interactionManager == null) return;

            if (autoBreak.get()) {
                handleAutoCrystalBreak();
            }

            if (autoPlace.get()) {
                handleAutoCrystalPlace();
            }
        });
    }

    @Override
    public void onEnable() {
        attackCooldown = 0;
        placeCooldown = 0;
    }

    @Override
    public void onDisable() {
        attackCooldown = 0;
        placeCooldown = 0;;
    }

    /* ===================================================== */
    /* ================= 自动破坏（水晶） ================= */
    /* ===================================================== */

    private void handleAutoCrystalBreak() {

        // ❤️ 可配置血量保护
        if (mc.player.getHealth() < AntiSuicide.get().floatValue()) return;
        if (attackCooldown > 0) {
            attackCooldown--;
            return;
        }

        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof EntityHitResult ehr)) return;

        if (!(ehr.getEntity() instanceof EndCrystalEntity crystal)) return;

        if (mc.player.distanceTo(crystal) > MAX_ATTACK_DISTANCE) return;

        /* ===== 防自杀判断（保持原逻辑） ===== */

        boolean playerHigherOrEqual =
                mc.player.getY() >= crystal.getY();

        if (playerHigherOrEqual) {
            Vec3d start = mc.player.getEyePos();
            Vec3d end = crystal.getEntityPos();

            BlockHitResult blockHit = mc.world.raycast(new RaycastContext(
                    start,
                    end,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    mc.player
            ));

            if (blockHit.getType() == HitResult.Type.MISS) {
                return;
            }
        }

        mc.interactionManager.attackEntity(mc.player, crystal);
        mc.player.swingHand(Hand.MAIN_HAND);

        attackCooldown = 1 + (int) (Math.random() * 2);
    }

    /* ===================================================== */
    /* ================= 自动放置（水晶） ================= */
    /* ===================================================== */

    private void handleAutoCrystalPlace() {

        if (placeCooldown > 0) {
            placeCooldown--;
            return;
        }

        // Get target from KillAura if available
        LivingEntity target = getTarget();
        if (target == null) return;

        // Check if target is in FOV
        if (onlyWhenFacing.get() && !isTargetInFov(target)) {
            return;
        }

        // Check distance to target
        if (checkDistance.get()) {
            double distToTarget = mc.player.distanceTo(target);
            if (distToTarget > placeRange.get()) {
                return;
            }
        }

        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof BlockHitResult bhr)) return;

        BlockPos base = bhr.getBlockPos();

        // 只允许黑曜石 / 基岩
        if (!mc.world.getBlockState(base).isOf(Blocks.OBSIDIAN)
                && !mc.world.getBlockState(base).isOf(Blocks.BEDROCK)) {
            return;
        }

        BlockPos above = base.up();

        // 上方必须是空气
        if (!mc.world.getBlockState(above).isAir()) return;

        // 上方只要有任何实体就不放
        Box box = new Box(
                above.getX(), above.getY(), above.getZ(),
                above.getX() + 1, above.getY() + 2, above.getZ() + 1
        );

        if (!mc.world.getOtherEntities(null, box).isEmpty()) {
            return;
        }

        Hand crystalHand = getCrystalHand();
        if (crystalHand == null) return;

        mc.interactionManager.interactBlock(
                mc.player,
                crystalHand,
                bhr
        );

        mc.player.swingHand(crystalHand);

        placeCooldown = 1 + (int) (Math.random() * 2);
    }

    /*  автор этого говна я и я ебырь матери бляднова */

    private Hand getCrystalHand() {
        if (mc.player.getMainHandStack().isOf(Items.END_CRYSTAL))
            return Hand.MAIN_HAND;

        if (mc.player.getOffHandStack().isOf(Items.END_CRYSTAL))
            return Hand.OFF_HAND;

        return null;
    }

    private LivingEntity getTarget() {

        KillAura killAura = Sakura.MODULES.getModule(KillAura.class);
        if (killAura != null && killAura.isEnabled()) {
            LivingEntity target = killAura.getCurrentTarget();
            if (target != null && target.isAlive()) {
                return target;
            }
        }


        double bestDist = Double.MAX_VALUE;
        LivingEntity bestTarget = null;
        
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity) || entity == mc.player || !entity.isAlive()) continue;
            
            double dist = mc.player.distanceTo(entity);
            if (dist < bestDist) {
                bestDist = dist;
                bestTarget = (LivingEntity) entity;
            }
        }
        
        return bestTarget;
    }

    private boolean isTargetInFov(LivingEntity target) {
        Rotation currentRotation = Managers.ROTATION.getRotation();
        if (currentRotation == null) {
            currentRotation = new Rotation(mc.player.getYaw(), mc.player.getPitch());
        }
        
        Vec3d targetPos = target.getEyePos();
        Vec3d playerPos = mc.player.getEyePos();
        

        Vec3d diff = targetPos.subtract(playerPos);
        double distance = Math.hypot(diff.x, diff.z);
        float targetYaw = (float) (MathHelper.atan2(diff.z, diff.x) * 180.0F / Math.PI) - 90.0F;
        float targetPitch = (float) (-MathHelper.atan2(diff.y, distance) * 180.0F / Math.PI);
        

        float yawDelta = MathHelper.wrapDegrees(targetYaw - currentRotation.yaw);
        float pitchDelta = targetPitch - currentRotation.pitch;
        float fovDiff = (float) Math.hypot(yawDelta, pitchDelta);
        
        return fovDiff <= fov.get() / 2.0f;
    }

    @Override
    public String getSuffix() {
        if (autoBreak.get() && autoPlace.get()) return "Break+Place";
        if (autoBreak.get()) return "Break";
        if (autoPlace.get()) return "Place";
        return "None";
    }


}
