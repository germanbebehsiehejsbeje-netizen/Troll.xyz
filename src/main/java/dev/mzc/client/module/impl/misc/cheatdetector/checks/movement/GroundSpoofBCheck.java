package dev.mzc.client.module.impl.misc.cheatdetector.checks.movement;

import dev.mzc.client.module.impl.misc.cheatdetector.CheatCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.CheatDetectorContext;
import dev.mzc.client.module.impl.misc.cheatdetector.TrackedPlayer;
import dev.mzc.client.values.impl.BoolValue;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class GroundSpoofBCheck implements CheatCheck {
    private final BoolValue enabled;

    public GroundSpoofBCheck(BoolValue enabled) {
        this.enabled = enabled;
    }

    @Override
    public void onTick(PlayerEntity player, TrackedPlayer tp, CheatDetectorContext ctx) {
        if (!enabled.get()) return;
        if (ctx.mc().world == null) return;

        if (!isOnGroundHeuristic(ctx.mc().world, player) && Math.floor(player.getY()) == player.getY() && Math.abs(tp.posDelta().y) < 0.02) {
            BlockPos pos = BlockPos.ofFloored(player.getX(), player.getY() - 0.01, player.getZ());
            if (isCompletelySolid(ctx.mc().world, pos)) {
                ctx.notify(player, "GroundSpoofB");
            }
        }
    }

    private boolean isCompletelySolid(net.minecraft.world.World world, BlockPos pos) {
        if (world == null) return false;
        
        BlockPos[] blocks = {
                pos,
                pos.east(),
                pos.east().north(),
                pos.west(),
                pos.west().south(),
                pos.north(),
                pos.north().west(),
                pos.south(),
                pos.south().east()
        };

        int count = 0;
        for (BlockPos p : blocks) {
            if (world.getBlockState(p).isFullCube(world, p)) {
                count++;
            }
        }
        return count >= 8;
    }

    private static boolean isOnGroundHeuristic(World world, PlayerEntity player) {
        if (player.isOnGround()) return true;
        BlockPos under = BlockPos.ofFloored(player.getX(), player.getY() - 0.01, player.getZ());
        return !world.getBlockState(under).getCollisionShape(world, under).isEmpty();
    }
}
