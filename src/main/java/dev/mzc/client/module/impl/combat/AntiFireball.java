package dev.mzc.client.module.impl.combat;

import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.manager.Managers;
import dev.mzc.client.manager.impl.RotationManager;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.rotation.MovementFix;
import dev.mzc.client.utils.rotation.RaytraceUtil;
import dev.mzc.client.utils.rotation.RotationUtil;
import dev.mzc.client.utils.vector.Rotation;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Set;

public class AntiFireball extends Module {
    private static final Set<String> TARGET_TYPES = Set.of(
            "fireball",
            "small_fireball",
            "dragon_fireball",
            "wither_skull"
    );

    private final NumberValue<Double> range = new NumberValue<>("Range", 6.0, 1.0, 12.0, 0.1);
    private final NumberValue<Integer> rotateSpeed = new NumberValue<>("RotateSpeed", 10, 1, 10, 1);
    private final NumberValue<Integer> attacks = new NumberValue<>("Attacks", 2, 1, 6, 1);
    private final NumberValue<Integer> cooldownTicks = new NumberValue<>("Cooldown", 6, 0, 20, 1);
    private final BoolValue pauseInGui = new BoolValue("PauseInGui", true);

    private int cooldown;
    private Entity target;

    public AntiFireball() {
        super("AntiFireball", Category.Combat);
        this.setType(ModuleType.Safe);
    }

    @Override
    public void onDisable() {
        target = null;
        cooldown = 0;
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (nullCheck()) return;
        if (pauseInGui.get() && mc.currentScreen != null) return;
        if (cooldown > 0) cooldown--;

        target = findIncomingFireball();
        if (target == null) return;

        Rotation rot = RotationUtil.calculate(target);
        Managers.ROTATION.setRotations(rot, rotateSpeed.get(), MovementFix.GRIM, RotationManager.Priority.Highest);

        if (cooldown > 0) return;
        if (!isRotationClose(rot, 10f, 10f)) return;
        if (!canHitTarget(target, rot)) return;

        int count = Math.max(1, attacks.get());
        for (int i = 0; i < count; i++) {
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        }
        cooldown = cooldownTicks.get();
    }

    private Entity findIncomingFireball() {
        double r = range.get();
        double rSq = r * r;
        Vec3d eye = mc.player.getEyePos();
        Box box = mc.player.getBoundingBox().expand(r);

        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : mc.world.getOtherEntities(mc.player, box)) {
            if (!isFireballType(e)) continue;
            if (!e.isAlive()) continue;
            Vec3d toPlayer = eye.subtract(e.getEntityPos());
            double distSq = toPlayer.lengthSquared();
            if (distSq > rSq) continue;

            Vec3d vel = e.getVelocity();
            if (vel.lengthSquared() < 0.01) continue;
            double toward = vel.normalize().dotProduct(toPlayer.normalize());
            if (toward < 0.35) continue;

            double score = distSq / Math.max(0.1, toward);
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    private boolean isFireballType(Entity e) {
        String path = Registries.ENTITY_TYPE.getId(e.getType()).getPath();
        return TARGET_TYPES.contains(path);
    }

    private boolean canHitTarget(Entity e, Rotation rot) {
        EntityHitResult hit = RaytraceUtil.rayTraceEntity(range.get(), rot, ent -> ent == e);
        return hit != null;
    }

    private boolean isRotationClose(Rotation targetRot, float yawMax, float pitchMax) {
        if (Managers.ROTATION.rotations == null) return false;
        double yawDiff = Math.abs(MathHelper.wrapDegrees(Managers.ROTATION.rotations.yaw - targetRot.yaw));
        double pitchDiff = Math.abs(Managers.ROTATION.rotations.pitch - targetRot.pitch);
        return yawDiff <= yawMax && pitchDiff <= pitchMax;
    }
}
