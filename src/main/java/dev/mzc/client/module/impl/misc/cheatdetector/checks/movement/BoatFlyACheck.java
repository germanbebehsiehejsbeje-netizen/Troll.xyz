package dev.mzc.client.module.impl.misc.cheatdetector.checks.movement;

import dev.mzc.client.module.impl.misc.cheatdetector.CheatCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.CheatDetectorContext;
import dev.mzc.client.module.impl.misc.cheatdetector.TrackedPlayer;
import dev.mzc.client.values.impl.BoolValue;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.BubbleColumnBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.function.Predicate;

public class BoatFlyACheck implements CheatCheck {
    private static final List<Block> IGNORE_BLOCKS = List.of(
            Blocks.WATER,
            Blocks.PISTON,
            Blocks.PISTON_HEAD,
            Blocks.MOVING_PISTON,
            Blocks.STICKY_PISTON
    );

    private final BoolValue enabled;

    public BoatFlyACheck(BoolValue enabled) {
        this.enabled = enabled;
    }

    @Override
    public void onTick(PlayerEntity player, TrackedPlayer tp, CheatDetectorContext ctx) {
        if (!enabled.get()) return;
        if (ctx.mc().world == null) return;

        Entity v = player.getVehicle();
        if (!(v instanceof BoatEntity boat)) return;

        if (boat.isOnGround()) return;

        if (boat.isTouchingWater()) {
            if (blockCheck(ctx, boat, st -> st.isOf(Blocks.BUBBLE_COLUMN) && !st.get(BubbleColumnBlock.DRAG))) return;
        } else {
            if (IGNORE_BLOCKS.stream().anyMatch(b -> blockCheck(ctx, boat, st -> st.isOf(b)))) return;
        }

        if (boat.getVelocity().y >= 0.01) {
            ctx.notify(player, String.format("BoatFlyA (Invalid boat Y-motion: %.2f inWater=%s onGround=%s)", boat.getVelocity().y, boat.isTouchingWater(), boat.isOnGround()));
        }
    }

    private static boolean blockCheck(CheatDetectorContext ctx, BoatEntity boat, Predicate<BlockState> predicate) {
        BlockPos feet = BlockPos.ofFloored(boat.getX(), boat.getY() - 0.01, boat.getZ());
        BlockPos under = feet.down();
        return predicate.test(ctx.mc().world.getBlockState(feet)) || predicate.test(ctx.mc().world.getBlockState(under));
    }
}
