package dev.mzc.client.module.impl.combat.elytratarget;

import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.manager.Managers;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.module.impl.combat.Teams;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ElytraTarget — autonomous port of LB's ModuleElytraTarget.
 *
 * <p>What it does:
 * <ul>
 *   <li>Tracks the nearest player target on its own (no dependency on KillAura).</li>
 *   <li>Rotates the player toward the target while falling/gliding on an elytra.</li>
 *   <li>Optionally fires fireworks (AutoFirework) when chasing.</li>
 * </ul>
 */
public class ElytraTargetModule extends Module {
    public static ElytraTargetModule INSTANCE;

    public final ElytraRotationProcessor elytraRotationProcessor = new ElytraRotationProcessor(this);

    /* --- Targeting --- */
    public final NumberValue<Double> range = new NumberValue<>("Range", 80.0, 5.0, 256.0, 1.0);
    public final BoolValue targetPlayers = new BoolValue("TargetPlayers", true);
    public final BoolValue targetMobs = new BoolValue("TargetMobs", false);
    public final BoolValue ignoreFriends = new BoolValue("IgnoreFriends", true);
    public final BoolValue ignoreTeammates = new BoolValue("IgnoreTeammates", true);
    public final BoolValue requireLineOfSight = new BoolValue("RequireLineOfSight", false);

    /* --- AutoFirework --- */
    public final BoolValue autoFirework = new BoolValue("AutoFirework", true);
    public final NumberValue<Integer> fireworkDelay =
            new NumberValue<>("FireworkDelay", 30, 5, 200, 5, autoFirework::get);
    public final NumberValue<Double> fireworkMinDistance =
            new NumberValue<>("FireworkMinDistance", 8.0, 1.0, 64.0, 1.0, autoFirework::get);

    /* --- KillAura integration --- */
    /** When true and we are gliding, KillAura should NOT push its own rotation — ours is good enough. */
    public final BoolValue ignoreKillAuraRotations = new BoolValue("IgnoreKillAuraRotations", true);

    private LivingEntity target;
    private long lastFireworkTime = 0L;

    public ElytraTargetModule() {
        super("ElytraTarget", Category.Combat);
        this.setType(ModuleType.Hack);
        INSTANCE = this;

        // Add all settings from the rotation processor
        values.add(elytraRotationProcessor.getCustomRotations());
        values.add(elytraRotationProcessor.getSharpRotations());
        values.add(elytraRotationProcessor.getAutoDistance());
        values.add(elytraRotationProcessor.getRotateAt());
        values.add(elytraRotationProcessor.getRotationSpeed());

        // Prediction settings
        values.add(elytraRotationProcessor.getPredict().getPrediction());
        values.add(elytraRotationProcessor.getPredict().getMode());
        values.add(elytraRotationProcessor.getPredict().getGlidingOnly());
        values.add(elytraRotationProcessor.getPredict().getMultiplier());
    }

    public LivingEntity getTarget() { return target; }

    /**
     * True when KillAura should suppress its own rotation override and reuse our active rotation
     * (we are flying and pointing at this exact target already).
     */
    public boolean canIgnoreKillAuraRotations() {
        return isEnabled()
                && ignoreKillAuraRotations.get()
                && mc.player != null
                && mc.player.isGliding()
                && target != null;
    }

    @Override
    public void onDisable() {
        target = null;
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        // 1. Lock onto a target. Keep the same target across ticks until it becomes invalid
        // (dead, out of range, friend/teammate toggled, line-of-sight lost). Only acquire a new
        // target when our current one is null/invalid.
        if (target == null || !isStillValidLockedTarget(target)) {
            target = selectTarget();
        }

        // 2. Fire a firework if appropriate (we are gliding + have target + cooldown elapsed + distance ok).
        tryAutoFirework();

        // 3. Rotate toward target while gliding (handled by the processor itself)
        elytraRotationProcessor.processRotation();
    }

    /** Stricter than {@link #isValidTarget} — additionally checks range and line-of-sight settings. */
    private boolean isStillValidLockedTarget(LivingEntity e) {
        if (!isValidTarget(e)) return false;
        double r = range.get();
        if (mc.player.squaredDistanceTo(e) > r * r) return false;
        if (requireLineOfSight.get() && !mc.player.canSee(e)) return false;
        return true;
    }

    /* ----------------- Target selection ----------------- */

    private LivingEntity selectTarget() {
        if (mc.world == null || mc.player == null) return null;
        double r = range.get();
        double r2 = r * r;
        List<LivingEntity> candidates = new ArrayList<>();
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (!isValidTarget(living)) continue;
            if (mc.player.squaredDistanceTo(entity) > r2) continue;
            if (requireLineOfSight.get() && !mc.player.canSee(living)) continue;
            candidates.add(living);
        }
        candidates.sort(Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e)));
        return candidates.isEmpty() ? null : candidates.getFirst();
    }

    private boolean isValidTarget(LivingEntity e) {
        if (e == mc.player || !e.isAlive()) return false;
        if (e instanceof PlayerEntity p) {
            if (!targetPlayers.get()) return false;
            if (ignoreTeammates.get() && Teams.getInstance() != null && Teams.getInstance().isTeammate(p)) return false;
            if (ignoreFriends.get() && Managers.FRIEND != null && Managers.FRIEND.isFriend(p.getName().getString())) return false;
            return true;
        }
        return targetMobs.get();
    }

    /* ----------------- AutoFirework ----------------- */

    private void tryAutoFirework() {
        if (!autoFirework.get()) return;
        if (target == null) return;
        if (mc.player == null) return;
        if (!mc.player.isGliding()) return;

        long now = System.currentTimeMillis();
        if (now - lastFireworkTime < fireworkDelay.get() * 50L) return;

        double dist = mc.player.distanceTo(target);
        if (dist < fireworkMinDistance.get()) return;

        // Find a firework rocket in main/off hand.
        Hand hand = findFireworkHand();
        if (hand == null) return;

        // Use the rocket. interactItem fires the rocket while gliding.
        if (mc.interactionManager != null) {
            mc.interactionManager.interactItem(mc.player, hand);
            lastFireworkTime = now;
        }
    }

    private Hand findFireworkHand() {
        if (isFirework(mc.player.getMainHandStack())) return Hand.MAIN_HAND;
        if (isFirework(mc.player.getOffHandStack())) return Hand.OFF_HAND;
        return null;
    }

    private static boolean isFirework(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isOf(Items.FIREWORK_ROCKET);
    }
}
