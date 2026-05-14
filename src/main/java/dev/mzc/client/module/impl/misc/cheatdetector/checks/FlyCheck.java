package dev.mzc.client.module.impl.misc.cheatdetector.checks;

import dev.mzc.client.module.impl.misc.cheatdetector.CheatCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.CheatDetectorContext;
import dev.mzc.client.module.impl.misc.cheatdetector.TrackedPlayer;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FlyCheck implements CheatCheck {
    private final BoolValue enabled;
    private final NumberValue<Integer> maxOffGroundTicks;
    private final Map<UUID, Integer> offGroundTicksByPlayer = new HashMap<>();
    private final Map<UUID, Integer> hoverTicksByPlayer = new HashMap<>();

    public FlyCheck(BoolValue enabled, NumberValue<Integer> maxOffGroundTicks) {
        this.enabled = enabled;
        this.maxOffGroundTicks = maxOffGroundTicks;
    }

    @Override
    public void onTick(PlayerEntity player, TrackedPlayer tracked, CheatDetectorContext context) {
        if (!enabled.get()) return;
        if (context.mc().world == null) return;

        UUID id = player.getUuid();
        if (isOnGroundHeuristic(context.mc().world, player)) {
            offGroundTicksByPlayer.remove(id);
            hoverTicksByPlayer.remove(id);
            return;
        }

        if (player.isTouchingWater()
                || player.isInLava()
                || player.hasVehicle()
                || player.isClimbing()
                || player.isSwimming()
                || player.isGliding()
                || player.hasStatusEffect(StatusEffects.LEVITATION)
                || player.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
            offGroundTicksByPlayer.remove(id);
            hoverTicksByPlayer.remove(id);
            return;
        }

        int offGroundTicks = offGroundTicksByPlayer.getOrDefault(id, 0) + 1;
        offGroundTicksByPlayer.put(id, offGroundTicks);
        if (offGroundTicks < maxOffGroundTicks.get()) {
            hoverTicksByPlayer.remove(id);
            return;
        }

        double dy = tracked.posDelta().y;
        boolean hovering = Math.abs(dy) < 0.02;
        if (!hovering) {
            hoverTicksByPlayer.remove(id);
            return;
        }

        int hoverTicks = hoverTicksByPlayer.getOrDefault(id, 0) + 1;
        hoverTicksByPlayer.put(id, hoverTicks);
        if (hoverTicks >= 8) {
            context.notify(player, "滞空异常");
        }
    }

    private static boolean isOnGroundHeuristic(World world, PlayerEntity player) {
        if (player.isOnGround()) return true;

        BlockPos under1 = BlockPos.ofFloored(player.getX(), player.getY() - 0.01, player.getZ());
        if (!world.getBlockState(under1).getCollisionShape(world, under1).isEmpty()) return true;

        BlockPos under2 = BlockPos.ofFloored(player.getX(), player.getY() - 0.51, player.getZ());
        return !world.getBlockState(under2).getCollisionShape(world, under2).isEmpty();
    }
}
