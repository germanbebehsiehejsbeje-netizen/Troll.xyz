package dev.mzc.client.module.impl.misc.cheatdetector.checks.movement;

import dev.mzc.client.module.impl.misc.cheatdetector.CheatCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.CheatDetectorContext;
import dev.mzc.client.module.impl.misc.cheatdetector.TrackedPlayer;
import dev.mzc.client.values.impl.BoolValue;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HighJumpACheck implements CheatCheck {
    private final BoolValue enabled;
    private final Map<UUID, State> stateByPlayer = new HashMap<>();

    public HighJumpACheck(BoolValue enabled) {
        this.enabled = enabled;
    }

    @Override
    public void onTick(PlayerEntity player, TrackedPlayer tp, CheatDetectorContext ctx) {
        if (!enabled.get()) return;
        if (ctx.mc().world == null) return;

        State st = stateByPlayer.computeIfAbsent(player.getUuid(), id -> new State());

        boolean onGround = isOnGroundHeuristic(ctx.mc().world, player);
        if (onGround) {
            st.inAir = false;
            st.baseY = 0.0;
            st.highestY = 0.0;
            st.flagged = false;
            return;
        }

        if (player.isTouchingWater()
                || player.isInLava()
                || player.isGliding()
                || player.isClimbing()
                || player.hasVehicle()
                || player.hasStatusEffect(StatusEffects.SLOW_FALLING)
                || player.hasStatusEffect(StatusEffects.LEVITATION)) {
            st.inAir = false;
            st.flagged = false;
            return;
        }

        if (!st.inAir) {
            st.inAir = true;
            st.baseY = player.getY();
            st.highestY = st.baseY;
        }

        if (player.getY() > st.highestY) st.highestY = player.getY();

        double height = st.highestY - st.baseY;
        double possible = baseJumpHeight(player) + 0.25;
        if (!st.flagged && height > possible) {
            st.flagged = true;
            ctx.notify(player, String.format("HighJumpA (Current: %.2f Max: %.2f)", height, possible));
        }
    }

    private static double baseJumpHeight(PlayerEntity player) {
        double base = 1.252203340253729;
        StatusEffectInstance jump = player.getStatusEffect(StatusEffects.JUMP_BOOST);
        if (jump == null) return base;
        int level = jump.getAmplifier() + 1;
        return base + 0.58397 * level;
    }

    private static boolean isOnGroundHeuristic(World world, PlayerEntity player) {
        if (player.isOnGround()) return true;

        BlockPos under1 = BlockPos.ofFloored(player.getX(), player.getY() - 0.01, player.getZ());
        if (!world.getBlockState(under1).getCollisionShape(world, under1).isEmpty()) return true;

        BlockPos under2 = BlockPos.ofFloored(player.getX(), player.getY() - 0.51, player.getZ());
        return !world.getBlockState(under2).getCollisionShape(world, under2).isEmpty();
    }

    private static class State {
        private boolean inAir;
        private double baseY;
        private double highestY;
        private boolean flagged;
    }
}
