package dev.mzc.client.module.impl.combat;

import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.manager.Managers;
import dev.mzc.client.manager.impl.RotationManager.Priority;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.math.MathUtil;
import dev.mzc.client.utils.rotation.MovementFix;
import dev.mzc.client.utils.rotation.RotationUtil;
import dev.mzc.client.utils.vector.Rotation;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MutiAura extends Module {
    public enum AttackMode {
        LowVersion, highVersion
    }

    private final EnumValue<AttackMode> mode = new EnumValue<>("Mode", AttackMode.LowVersion);
    private final NumberValue<Double> range = new NumberValue<>("Range", 6.0, 1.0, 12.0, 0.1);
    private final NumberValue<Integer> attacksPerTick = new NumberValue<>("AttacksPerTick", 3, 1, 20, 1);
    private final NumberValue<Double> minCps = new NumberValue<>("Min CPS", 10.0, 1.0, 30.0, 1.0, () -> mode.is(AttackMode.LowVersion));
    private final NumberValue<Double> maxCps = new NumberValue<>("Max CPS", 12.0, 1.0, 30.0, 1.0, () -> mode.is(AttackMode.LowVersion));

    private final BoolValue rotate = new BoolValue("Rotate", false);
    private final NumberValue<Integer> rotateSpeed = new NumberValue<>("Rotation Speed", 10, 1, 10, 1, rotate::get);

    private final BoolValue targetPlayers = new BoolValue("Players", true);
    private final BoolValue targetMobs = new BoolValue("Mobs", true);
    private final BoolValue targetAnimals = new BoolValue("Animals", false);
    private final BoolValue teamCheck = new BoolValue("Team Check", true);
    private final BoolValue pauseInGui = new BoolValue("Pause In Gui", true);

    private final List<LivingEntity> targets = new ArrayList<>();
    private long lastAttackTime;

    public MutiAura() {
        super("MutiAura", Category.Combat);
        this.setType(ModuleType.Hack);
    }

    @EventHandler
    public void onPreTick(TickEvent.Pre event) {
        if (nullCheck()) return;
        if (pauseInGui.get() && mc.currentScreen != null) return;

        findTargets();
        if (targets.isEmpty()) return;

        if (rotate.get()) {
            Rotation rot = RotationUtil.calculate(targets.get(0));
            Managers.ROTATION.setRotations(rot, rotateSpeed.get(), MovementFix.NORMAL, Priority.Medium);
        }

        if (!canAttackNow()) return;

        int count = Math.min(attacksPerTick.get(), targets.size());
        for (int i = 0; i < count; i++) {
            LivingEntity t = targets.get(i);
            if (!t.isAlive()) continue;
            if (mc.player.squaredDistanceTo(t) > range.get() * range.get()) continue;

            mc.interactionManager.attackEntity(mc.player, t);
            mc.player.swingHand(Hand.MAIN_HAND);
        }

        lastAttackTime = System.currentTimeMillis();
    }

    private boolean canAttackNow() {
        if (mode.is(AttackMode.highVersion)) {
            return mc.player.getAttackCooldownProgress(0.5f) >= 1.0f;
        }

        long now = System.currentTimeMillis();
        double baseDelay = 1000.0 / MathUtil.getRandom(minCps.get(), maxCps.get());
        long delay = (long) (baseDelay + (Math.random() - 0.5) * baseDelay * 0.4);
        return now - lastAttackTime >= delay;
    }

    private void findTargets() {
        targets.clear();

        double r = range.get();
        Box box = mc.player.getBoundingBox().expand(r);
        List<Entity> candidates = mc.world.getOtherEntities(mc.player, box, e ->
            e instanceof LivingEntity
                && e.isAlive()
                && !e.isSpectator()
                && e != mc.player
                && isValidType(e)
                && isEnemy(e)
        );

        candidates.sort(Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e)));
        for (Entity e : candidates) {
            targets.add((LivingEntity) e);
        }
    }

    public boolean hasTarget() {
        if (nullCheck()) return false;
        findTargets();
        return !targets.isEmpty();
    }

    private boolean isValidType(Entity e) {
        if (e instanceof PlayerEntity) return targetPlayers.get();
        if (e instanceof Monster) return targetMobs.get();
        if (e instanceof AnimalEntity) return targetAnimals.get();
        return false;
    }

    private boolean isEnemy(Entity entity) {
        if (entity instanceof PlayerEntity player) {
            if (Managers.FRIEND.isFriend(player.getName().getString())) return false;
        }

        if (!teamCheck.get()) return true;
        if (!(entity instanceof PlayerEntity player)) return true;
        if (mc.player == null) return false;

        int myColor = getLeatherArmorColor(mc.player);
        int theirColor = getLeatherArmorColor(player);

        if (myColor == -1 || theirColor == -1) return true;
        return myColor != theirColor;
    }

    private int getLeatherArmorColor(PlayerEntity player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getEquippedStack(slot);
            if (stack.isEmpty()) continue;
            DyedColorComponent dyed = stack.get(DataComponentTypes.DYED_COLOR);
            if (dyed != null) {
                return dyed.rgb();
            }
        }
        return -1;
    }
}
