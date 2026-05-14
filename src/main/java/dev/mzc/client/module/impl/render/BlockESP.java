package dev.mzc.client.module.impl.render;

import dev.mzc.client.events.render.Render3DEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.render.Render3DUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.ColorValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.*;
import java.util.Map;

public class BlockESP extends Module {

    private final EnumValue<Page> page = new EnumValue<>("Page", Page.General);

    // General Settings
    private final EnumValue<Mode> mode = new EnumValue<>("Mode", Mode.Both, () -> page.is(Page.General));
    private final NumberValue<Float> lineWidth = new NumberValue<>("LineWidth", 1.5f, 0.1f, 5.0f, 0.1f, () -> page.is(Page.General));
    private final BoolValue throughWalls = new BoolValue("ThroughWalls", true, () -> page.is(Page.General));

    // Chest Settings
    private final BoolValue chest = new BoolValue("Chest", true, () -> page.is(Page.Chest));
    private final ColorValue chestColor = new ColorValue("ChestColor", new Color(255, 170, 0, 100), () -> page.is(Page.Chest));
    private final ColorValue chestLineColor = new ColorValue("ChestLineColor", new Color(255, 170, 0, 255), () -> page.is(Page.Chest));

    // Barrel Settings
    private final BoolValue barrel = new BoolValue("Barrel", true, () -> page.is(Page.Barrel));
    private final ColorValue barrelColor = new ColorValue("BarrelColor", new Color(255, 170, 0, 100), () -> page.is(Page.Barrel));
    private final ColorValue barrelLineColor = new ColorValue("BarrelLineColor", new Color(255, 170, 0, 255), () -> page.is(Page.Barrel));

    // Ender Chest Settings
    private final BoolValue enderChest = new BoolValue("EnderChest", true, () -> page.is(Page.EnderChest));
    private final ColorValue enderChestColor = new ColorValue("EnderChestColor", new Color(170, 0, 255, 100), () -> page.is(Page.EnderChest));
    private final ColorValue enderChestLineColor = new ColorValue("EnderChestLineColor", new Color(170, 0, 255, 255), () -> page.is(Page.EnderChest));

    // Shulker Settings
    private final BoolValue shulker = new BoolValue("Shulker", true, () -> page.is(Page.Shulker));
    private final ColorValue shulkerColor = new ColorValue("ShulkerColor", new Color(255, 0, 170, 100), () -> page.is(Page.Shulker));
    private final ColorValue shulkerLineColor = new ColorValue("ShulkerLineColor", new Color(255, 0, 170, 255), () -> page.is(Page.Shulker));

    // Furnace Settings
    private final BoolValue furnace = new BoolValue("Furnace", true, () -> page.is(Page.Furnace));
    private final ColorValue furnaceColor = new ColorValue("FurnaceColor", new Color(128, 128, 128, 100), () -> page.is(Page.Furnace));
    private final ColorValue furnaceLineColor = new ColorValue("FurnaceLineColor", new Color(128, 128, 128, 255), () -> page.is(Page.Furnace));

    // Bed Settings
    private final BoolValue bed = new BoolValue("Bed", true, () -> page.is(Page.Bed));
    private final ColorValue bedColor = new ColorValue("BedColor", new Color(255, 255, 255, 100), () -> page.is(Page.Bed));
    private final ColorValue bedLineColor = new ColorValue("BedLineColor", new Color(255, 255, 255, 255), () -> page.is(Page.Bed));


    public BlockESP() {
        super("BlockESP", Category.Render);
        this.setType(ModuleType.All);
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (mc.world == null) return;

        setSuffix(page.get().name());

        int radius = mc.options.getViewDistance().getValue();
        ChunkPos playerChunk = mc.player.getChunkPos();

        for (int x = playerChunk.x - radius; x <= playerChunk.x + radius; x++) {
            for (int z = playerChunk.z - radius; z <= playerChunk.z + radius; z++) {
                WorldChunk chunk = mc.world.getChunk(x, z);
                if (chunk != null) {
                    for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                        BlockPos pos = blockEntity.getPos();
                        Box box = new Box(pos);

                        Color sideColor = null;
                        Color lineColor = null;

                        if (blockEntity instanceof ChestBlockEntity && chest.get()) {
                            sideColor = chestColor.get();
                            lineColor = chestLineColor.get();
                        } else if (blockEntity instanceof BarrelBlockEntity && barrel.get()) {
                            sideColor = barrelColor.get();
                            lineColor = barrelLineColor.get();
                        } else if (blockEntity instanceof EnderChestBlockEntity && enderChest.get()) {
                            sideColor = enderChestColor.get();
                            lineColor = enderChestLineColor.get();
                        } else if (blockEntity instanceof ShulkerBoxBlockEntity && shulker.get()) {
                            sideColor = shulkerColor.get();
                            lineColor = shulkerLineColor.get();
                        } else if (blockEntity instanceof FurnaceBlockEntity && furnace.get()) {
                            sideColor = furnaceColor.get();
                            lineColor = furnaceLineColor.get();
                        } else if (blockEntity instanceof BedBlockEntity && bed.get()) {
                            sideColor = bedColor.get();
                            lineColor = bedLineColor.get();
                        }

                        if (sideColor != null) {
                            renderBox(event.getMatrices(), box, sideColor, lineColor);
                        }
                    }
                }
            }
        }
    }

    private void renderBox(net.minecraft.client.util.math.MatrixStack stack, Box box, Color sideColor, Color lineColor) {
        if (throughWalls.get()) {
            if (mode.is(Mode.Fill)) {
                Render3DUtil.drawFilledBoxThroughWalls(stack, box, sideColor);
            } else if (mode.is(Mode.Outline)) {
                Render3DUtil.drawBoxOutlineThroughWalls(stack, box, lineColor.getRGB(), lineWidth.get());
            } else if (mode.is(Mode.Both)) {
                Render3DUtil.drawFullBoxThroughWalls(stack, box, sideColor.getRGB(), lineColor.getRGB(), lineWidth.get());
            }
        } else {
            if (mode.is(Mode.Fill)) {
                Render3DUtil.drawFilledBox(stack, box, sideColor);
            } else if (mode.is(Mode.Outline)) {
                Render3DUtil.drawBoxOutline(stack, box, lineColor.getRGB(), lineWidth.get());
            } else if (mode.is(Mode.Both)) {
                Render3DUtil.drawFullBox(stack, box, sideColor, lineColor, lineWidth.get());
            }
        }
    }

    public enum Page {
        General(),
        Chest(),
        Barrel(),
        EnderChest(),
        Shulker(),
        Furnace(),
        Bed();
        Page() {
        }
    }

    public enum Mode {
        Fill(),
        Outline(),
        Both();
        Mode() {
        }
    }
}
