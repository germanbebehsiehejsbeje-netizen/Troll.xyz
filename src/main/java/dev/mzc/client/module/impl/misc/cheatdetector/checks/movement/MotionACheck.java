package dev.mzc.client.module.impl.misc.cheatdetector.checks.movement;

import dev.mzc.client.module.impl.misc.cheatdetector.CheatCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.CheatDetectorContext;
import dev.mzc.client.module.impl.misc.cheatdetector.TrackedPlayer;
import dev.mzc.client.values.impl.BoolValue;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HoneyBlock;
import net.minecraft.block.PowderSnowBlock;
import net.minecraft.block.SlimeBlock;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MotionACheck implements CheatCheck {
    private static final double[] JUMP_MOTIONS = {
            0.41159999516010254, -0.08506399504327788, -0.08336271676487925, -0.0816954640195993,
            -0.08006155629742463, -0.07846032669852913, -0.07689112166107052, -0.07535330069443089,
            -0.07384623611779237, -0.07236931280394177, -0.07092192792819801
    };

    private static final double[] JUMP_MOTIONS_1 = {
            0.5095999912261959, -0.08702399309539816, -0.08528351489334113, -0.08357784622212827,
            -0.0819062908918066, -0.08026816663620898, -0.07866280483447859, -0.07708955023816295,
            -0.07554776070376615, -0.07403680693065001, -0.07255607220417704, -0.07110495214399076,
            -0.0696828544573303
    };

    private static final double[] JUMP_MOTIONS_2 = {
            0.6076000164985658, -0.0889839917316434, -0.08720431359424546, -0.08546022898565087,
            -0.08375102603596235, -0.08207600711266715, -0.0804344885358894, -0.07882580029933772,
            -0.07724928579683382, -0.07570430155431032, -0.0741902169671691, -0.0727064140428918,
            -0.07125228714879878, -0.06982724276485225, -0.06843069924140427
    };

    private final BoolValue enabled;
    private final Map<UUID, State> stateByPlayer = new HashMap<>();

    public MotionACheck(BoolValue enabled) {
        this.enabled = enabled;
    }

    @Override
    public void onTick(PlayerEntity player, TrackedPlayer tp, CheatDetectorContext ctx) {
        if (!enabled.get()) return;
        if (ctx.mc().world == null) return;

        State st = stateByPlayer.computeIfAbsent(player.getUuid(), id -> new State());
        boolean onGround = isOnGroundHeuristic(ctx.mc().world, player);
        if (onGround) {
            st.airTicks = 0;
        } else if (st.airTicks < 200) {
            st.airTicks++;
        }

        if (st.disableTicks > 0) {
            st.disableTicks--;
            st.readyToJump = false;
            return;
        }

        if (!check(player, tp, ctx, st)) {
            st.readyToJump = false;
            return;
        }

        if (onGround && !st.readyToJump) {
            st.readyToJump = true;
        }

        if (!st.readyToJump) return;

        int airTick = st.airTicks - 1;
        if (airTick < 0) return;
        if (airTick == 0 && tp.currentTickMotion().y < 0.20) return;

        double[] possible = getPossibleMotions(player);
        if (possible == null || airTick >= possible.length) {
            st.readyToJump = false;
            return;
        }

        double should = possible[airTick];
        double current = tp.currentTickMotion().y;
        if (Math.abs(current - should) > 0.08) {
            ctx.notify(player, String.format("MotionA (tick %d should: %.2f current: %.2f)", airTick, should, current));
        }
    }

    private static boolean check(PlayerEntity player, TrackedPlayer tp, CheatDetectorContext ctx, State st) {
        Vec3d motion = tp.currentTickMotion();
        if (isInvalidMotion(motion)) return false;

        if (player.isGliding()) {
            st.disableTicks = 3;
            return false;
        }

        if (player.hasStatusEffect(StatusEffects.SLOW_FALLING)) return false;

        if (player.isInsideWall()) return false;
        if (player.isTouchingWater() || player.isInLava()) return false;
        if (player.hasVehicle()) return false;
        if (player.isUsingRiptide()) return false;
        if (player.isSwimming()) return false;
        if (player.isSleeping()) return false;
        if (player.isClimbing()) return false;
        if (player.getAbilities().flying) return false;

        if (ctx.mc().world == null) return false;
        BlockPos groundPos = BlockPos.ofFloored(player.getX(), player.getY() - 0.01, player.getZ());
        BlockState state = ctx.mc().world.getBlockState(groundPos);
        if (!state.isFullCube(ctx.mc().world, groundPos)) return false;

        Block block = state.getBlock();
        if (block instanceof BedBlock || block instanceof SlimeBlock || block instanceof HoneyBlock || block instanceof PowderSnowBlock) return false;

        return true;
    }

    private static boolean isOnGroundHeuristic(World world, PlayerEntity player) {
        if (player.isOnGround()) return true;

        BlockPos under1 = BlockPos.ofFloored(player.getX(), player.getY() - 0.01, player.getZ());
        if (!world.getBlockState(under1).getCollisionShape(world, under1).isEmpty()) return true;

        BlockPos under2 = BlockPos.ofFloored(player.getX(), player.getY() - 0.51, player.getZ());
        return !world.getBlockState(under2).getCollisionShape(world, under2).isEmpty();
    }

    private static boolean isInvalidMotion(Vec3d motion) {
        return Math.abs(motion.x) >= 3.9 || Math.abs(motion.y) >= 3.9 || Math.abs(motion.z) >= 3.9;
    }

    private static double[] getPossibleMotions(PlayerEntity player) {
        StatusEffectInstance jump = player.getStatusEffect(StatusEffects.JUMP_BOOST);
        if (jump == null) return JUMP_MOTIONS;

        return switch (jump.getAmplifier()) {
            case 0 -> JUMP_MOTIONS_1;
            case 1 -> JUMP_MOTIONS_2;
            default -> null;
        };
    }

    private static class State {
        private boolean readyToJump;
        private int disableTicks;
        private int airTicks;
    }
}
