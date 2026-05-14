package dev.mzc.client.module.impl.movement;

import dev.mzc.client.Sakura;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.render.Render3DEvent;
import dev.mzc.client.manager.Managers;
import dev.mzc.client.manager.impl.RotationManager;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.module.impl.client.Friend;
import dev.mzc.client.utils.rotation.MovementFix;
import dev.mzc.client.utils.rotation.RotationUtil;
import dev.mzc.client.utils.vector.Rotation;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class HvHAimbot extends Module {

    public enum TargetMode {
        Distance, Health, FOV
    }

    public enum AimMode {
        Silent, Visual, Packet
    }

    private final EnumValue<TargetMode> targetMode = new EnumValue<>("Target Mode", TargetMode.Distance);
    private final EnumValue<AimMode> aimMode = new EnumValue<>("Aim Mode", AimMode.Silent);
    private final NumberValue<Double> range = new NumberValue<>("Range", 100.0, 10.0, 200.0, 5.0);
    private final NumberValue<Double> fov = new NumberValue<>("FOV", 90.0, 10.0, 180.0, 5.0);
    private final NumberValue<Double> speed = new NumberValue<>("Speed", 5.0, 0.5, 20.0, 0.5);
    private final BoolValue weaponOnly = new BoolValue("Weapon Only", true);
    private final BoolValue ignoreTeam = new BoolValue("Ignore Team", true);
    private final BoolValue autoShoot = new BoolValue("Auto Shoot", true);
    private final NumberValue<Double> prediction = new NumberValue<>("Prediction", 0.1, 0.0, 1.0, 0.05);
    
    private Entity target = null;
    private int ticksHeld = 0;

    public HvHAimbot() {
        super("HvHAimbot", Category.Movement);
        this.setType(ModuleType.Hack);
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;
        
        // Check if holding bow/crossbow/trident
        if (weaponOnly.get() && !isHoldingWeapon()) {
            target = null;
            return;
        }
        
        target = findTarget();
        
        if (target != null && autoShoot.get()) {
            Item item = mc.player.getMainHandStack().getItem();
            
            if (item instanceof BowItem) {
                if (mc.options.useKey.isPressed()) {
                    ticksHeld++;
                    if (BowItem.getPullProgress(ticksHeld) >= 1.0f) {
                        mc.options.useKey.setPressed(false);
                        mc.interactionManager.stopUsingItem(mc.player);
                    }
                } else {
                    mc.options.useKey.setPressed(true);
                    ticksHeld = 0;
                }
            } else if (item instanceof CrossbowItem) {
                if (CrossbowItem.isCharged(mc.player.getMainHandStack())) {
                    mc.options.useKey.setPressed(true);
                }
            }
        }
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (nullCheck() || target == null) return;
        
        // Get target position with prediction
        Vec3d targetPos = getPredictedPosition(target);
        
        // Calculate rotation to target
        Rotation targetRotation = RotationUtil.calculate(targetPos);
        
        if (aimMode.get() == AimMode.Silent) {
            // Silent aim using rotation manager
            Managers.ROTATION.setRotations(targetRotation, 100, MovementFix.GRIM, RotationManager.Priority.Highest);
        } else if (aimMode.get() == AimMode.Visual) {
            // Visual aim - smoothly rotate
            smoothAim(targetRotation, event.getTickDelta());
        }
        
        // Draw target ESP
        drawTargetESP(target);
    }

    private Entity findTarget() {
        List<Entity> candidates = StreamSupport.stream(mc.world.getEntities().spliterator(), false)
                .filter(e -> e instanceof LivingEntity)
                .filter(e -> e != mc.player)
                .filter(Entity::isAlive)
                .filter(e -> mc.player.distanceTo(e) <= range.get())
                .filter(this::isValid)
                .filter(e -> getAngleToEntity(e) <= fov.get() / 2.0)
                .collect(Collectors.toList());

        if (candidates.isEmpty()) return null;

        switch (targetMode.get()) {
            case Distance:
                candidates.sort(Comparator.comparingDouble(e -> mc.player.distanceTo(e)));
                break;
            case Health:
                candidates.sort(Comparator.comparingDouble(e -> ((LivingEntity) e).getHealth()));
                break;
            case FOV:
                candidates.sort(Comparator.comparingDouble(this::getAngleToEntity));
                break;
        }

        return candidates.get(0);
    }

    private boolean isValid(Entity entity) {
        if (ignoreTeam.get() && isTeammate(entity)) return false;
        if (Sakura.MODULES.getModule(Friend.class).isFriend(entity.getName().getString())) return false;
        return entity instanceof PlayerEntity;
    }

    private boolean isTeammate(Entity entity) {
        if (!(entity instanceof PlayerEntity player)) return false;
        if (mc.player == null) return false;
        
        // Check scoreboard team
        if (mc.player.getScoreboardTeam() != null && player.getScoreboardTeam() != null) {
            return mc.player.getScoreboardTeam().equals(player.getScoreboardTeam());
        }
        
        return false;
    }

    private boolean isHoldingWeapon() {
        Item item = mc.player.getMainHandStack().getItem();
        return item instanceof BowItem || 
               item instanceof CrossbowItem || 
               item == Items.TRIDENT;
    }

    private Vec3d getPredictedPosition(Entity entity) {
        Vec3d pos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
        Vec3d velocity = entity.getVelocity();
        
        // Simple linear prediction
        double predictX = pos.x + velocity.x * prediction.get();
        double predictY = pos.y + velocity.y * prediction.get() + 1.6; // Head height
        double predictZ = pos.z + velocity.z * prediction.get();
        
        return new Vec3d(predictX, predictY, predictZ);
    }

    private void smoothAim(Rotation targetRotation, float tickDelta) {
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();
        
        float yawDiff = MathHelper.wrapDegrees(targetRotation.yaw - currentYaw);
        float pitchDiff = targetRotation.pitch - currentPitch;
        
        float yawStep = (float) (yawDiff * speed.get() / 20.0);
        float pitchStep = (float) (pitchDiff * speed.get() / 20.0);
        
        mc.player.setYaw(currentYaw + yawStep);
        mc.player.setPitch(MathHelper.clamp(currentPitch + pitchStep, -90.0f, 90.0f));
    }

    private void drawTargetESP(Entity entity) {
        if (entity instanceof LivingEntity livingEntity) {
            // Draw bounding box - simplified version
            // Note: drawBoundingBox doesn't exist in current RenderUtil
            // Can be implemented later if needed
        }
    }

    private double getAngleToEntity(Entity entity) {
        Rotation rot = RotationUtil.calculate(entity);
        float yawDiff = Math.abs(MathHelper.wrapDegrees(rot.yaw - mc.player.getYaw()));
        float pitchDiff = Math.abs(MathHelper.wrapDegrees(rot.pitch - mc.player.getPitch()));
        return yawDiff + pitchDiff;
    }
}
