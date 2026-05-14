package dev.mzc.client.module.impl.misc.cheatdetector.checks.movement;

import dev.mzc.client.module.impl.misc.cheatdetector.CheatCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.CheatDetectorContext;
import dev.mzc.client.module.impl.misc.cheatdetector.TrackedPlayer;
import dev.mzc.client.values.impl.BoolValue;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class GroundSpoofACheck implements CheatCheck {
    private final BoolValue enabled;

    public GroundSpoofACheck(BoolValue enabled) {
        this.enabled = enabled;
    }

    @Override
    public void onTick(PlayerEntity player, TrackedPlayer tracked, CheatDetectorContext context) {
        if (!enabled.get()) return;
        if (!tracked.lastOnGround2() || !tracked.lastOnGround() || !tracked.currentOnGround()) return;
        if (context.mc().world == null) return;

        BlockPos groundPos = player.getBlockPos().down();
        if (check(context.mc().world.getBlockState(groundPos), context.mc().world.getBlockState(groundPos.up()))) {
            List<BlockPos> blocks = List.of(
                    groundPos.east(),
                    groundPos.east().north(),
                    groundPos.west(),
                    groundPos.west().south(),
                    groundPos.north(),
                    groundPos.north().west(),
                    groundPos.south(),
                    groundPos.south().east()
            );
            List<BlockPos> scaffolding = List.of(
                    groundPos.up().east(),
                    groundPos.up().east().north(),
                    groundPos.up().west(),
                    groundPos.up().west().south(),
                    groundPos.up().north(),
                    groundPos.up().north().west(),
                    groundPos.up().south(),
                    groundPos.up().south().east()
            );

            short airCount = 0;
            for (BlockPos pos : blocks) {
                if (context.mc().world.getBlockState(pos).isAir()) airCount++;
            }
            for (BlockPos pos : scaffolding) {
                if (context.mc().world.getBlockState(pos).isOf(Blocks.SCAFFOLDING)) return;
            }
            if (airCount >= 8) {
                context.notify(player, "GroundSpoof");
            }
        }
    }

    private static boolean check(BlockState below, BlockState above) {
        if (!below.isAir() || !above.isAir()) return false;
        return true;
    }
}

