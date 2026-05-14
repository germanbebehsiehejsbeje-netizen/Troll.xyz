package dev.mzc.client.module.impl.misc.cheatdetector.checks.movement;

import dev.mzc.client.module.impl.misc.cheatdetector.CheatCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.CheatDetectorContext;
import dev.mzc.client.module.impl.misc.cheatdetector.TrackedPlayer;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Deque;
import java.util.List;

public class FlyCCheck implements CheatCheck {
    private static final List<Block> IGNORED_BLOCKS = List.of(
            Blocks.COBWEB,
            Blocks.WATER,
            Blocks.LAVA,
            Blocks.POWDER_SNOW,
            Blocks.SLIME_BLOCK,
            Blocks.SOUL_SAND,
            Blocks.SCAFFOLDING
    );

    private final BoolValue enabled;
    private final NumberValue<Integer> minRepeatTicks;

    public FlyCCheck(BoolValue enabled, NumberValue<Integer> minRepeatTicks) {
        this.enabled = enabled;
        this.minRepeatTicks = minRepeatTicks;
    }

    @Override
    public void onTick(PlayerEntity player, TrackedPlayer tp, CheatDetectorContext ctx) {
        if (!enabled.get()) return;
        if (ctx.mc().world == null) return;
        if (player.hasVehicle() || tp.currentOnGround() || player.isGliding()) return;

        BlockPos feet = BlockPos.ofFloored(player.getX(), player.getY() - 0.01, player.getZ());
        if (IGNORED_BLOCKS.stream().anyMatch(b -> ctx.mc().world.getBlockState(feet).isOf(b))) return;

        Deque<Vec3d> hist = tp.posHist();
        if (hist.size() < 6) return;

        Vec3d[] arr = hist.toArray(new Vec3d[0]);
        Vec3d firstDiff = arr[arr.length - 1].subtract(arr[arr.length - 2]);
        double y = firstDiff.y;
        int repeat = 0;
        for (int i = arr.length - 2; i >= 1; i--) {
            Vec3d diff = arr[i].subtract(arr[i - 1]);
            if (Math.abs(diff.y - y) < 1.0e-9) repeat++;
        }

        if (repeat >= minRepeatTicks.get()) {
            ctx.notify(player, String.format("FlyC (Repeat Y-diff from %d ticks: %.2f)", repeat, y));
        }
    }
}
