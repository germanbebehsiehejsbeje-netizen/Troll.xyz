package dev.mzc.client.module.impl.player;

import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.render.Render3DEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.utils.player.InvUtil;
import dev.mzc.client.utils.render.Render3DUtil;
import dev.mzc.client.utils.world.BlockUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.ColorValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.awt.*;

public class AirPlace extends Module {
    public enum ColorMode {
        Client(),
        Single(),
        Double(),
        Rainbow();
        ColorMode() {
        }
    }

    private final NumberValue<Double> extraRange = new NumberValue<>("ExtraRange", 0.0, 0.0, 3.0, 0.1);
    private final NumberValue<Integer> delayTicks = new NumberValue<>("Delay", 0, 0, 10, 1);
    private final BoolValue packetPlace = new BoolValue("Packet", true);
    private final BoolValue sneak = new BoolValue("Sneak", true);
    private final BoolValue swing = new BoolValue("Swing", true);

    private final BoolValue render = new BoolValue("Render", true);
    private final EnumValue<ColorMode> colorMode = new EnumValue<>("ColorMode", ColorMode.Client, () -> render.get());
    private final ColorValue singleFill = new ColorValue("Fill", new Color(255, 255, 255, 40), () -> render.get() && colorMode.is(ColorMode.Single));
    private final ColorValue singleOutline = new ColorValue("Outline", new Color(255, 255, 255, 120), () -> render.get() && colorMode.is(ColorMode.Single));
    private final ColorValue doubleA = new ColorValue("ColorA", new Color(255, 120, 255), () -> render.get() && colorMode.is(ColorMode.Double));
    private final ColorValue doubleB = new ColorValue("ColorB", new Color(120, 220, 255), () -> render.get() && colorMode.is(ColorMode.Double));
    private final NumberValue<Integer> fillAlpha = new NumberValue<>("FillAlpha", 40, 0, 255, 1, () -> render.get() && !colorMode.is(ColorMode.Single));
    private final NumberValue<Integer> outlineAlpha = new NumberValue<>("OutlineAlpha", 120, 0, 255, 1, () -> render.get() && !colorMode.is(ColorMode.Single));

    private int timer;
    private BlockPos renderTarget;

    public AirPlace() {
        super("AirPlace", Category.Player);
        this.setType(ModuleType.Hack);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (nullCheck() || mc.interactionManager == null) return;
        if (timer > 0) timer--;
        if (!mc.options.useKey.isPressed()) return;

        ItemStack stack = mc.player.getMainHandStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) return;
        if (!InvUtil.isBlockPlaceable(stack)) return;

        double reach = mc.player.getBlockInteractionRange() + extraRange.get();
        HitResult hit = mc.player.raycast(reach, 0.0f, false);
        if (hit.getType() != HitResult.Type.MISS) {
            return;
        }

        BlockPos target = BlockPos.ofFloored(hit.getPos());
        if (!mc.world.getBlockState(target).isReplaceable()) return;
        if (!mc.world.getOtherEntities(null, new Box(target)).isEmpty()) return;
        if (timer > 0) return;

        if (sneak.get()) {
            mc.player.setSneaking(true);
        }
        BlockUtil.clickBlock(target, Direction.UP, false, packetPlace.get());
        if (swing.get()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
        if (sneak.get()) {
            mc.player.setSneaking(false);
        }

        timer = delayTicks.get();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!render.get()) return;
        double reach = mc.player.getBlockInteractionRange() + extraRange.get();
        HitResult hit = mc.player.raycast(reach, 0.0f, false);
        if (hit.getType() != HitResult.Type.MISS) {
            renderTarget = null;
            return;
        }
        renderTarget = BlockPos.ofFloored(hit.getPos());
        if (mc.world.isOutOfHeightLimit(renderTarget)) return;
        if (!mc.world.getBlockState(renderTarget).isReplaceable()) return;

        Color fill;
        Color outline;
        switch (colorMode.get()) {
            case Single -> {
                fill = singleFill.get();
                outline = singleOutline.get();
            }
            case Double -> {
                double t = (Math.sin(System.currentTimeMillis() / 400.0) + 1.0) / 2.0;
                Color base = interpolateColors(doubleA.get(), doubleB.get(), t);
                fill = withAlpha(base, fillAlpha.get());
                outline = withAlpha(base, outlineAlpha.get());
            }
            case Rainbow -> {
                float hue = (System.currentTimeMillis() % 2000L) / 2000f;
                Color base = new Color(Color.HSBtoRGB(hue, 0.8f, 1f));
                fill = withAlpha(base, fillAlpha.get());
                outline = withAlpha(base, outlineAlpha.get());
            }
            case Client -> {
                Color c1 = ClickGui.color(0);
                Color c2 = ClickGui.color2(0);
                double t = (Math.sin(System.currentTimeMillis() / 400.0) + 1.0) / 2.0;
                Color base = interpolateColors(c1, c2, t);
                fill = withAlpha(base, fillAlpha.get());
                outline = withAlpha(base, outlineAlpha.get());
            }
            default -> {
                fill = withAlpha(Color.WHITE, 40);
                outline = withAlpha(Color.WHITE, 120);
            }
        }

        Box box = new Box(renderTarget);
        Render3DUtil.drawFilledBox(event.getMatrices(), box, fill);
        GlStateManager._disableDepthTest();
        Render3DUtil.drawBoxOutline(event.getMatrices(), box, outline.getRGB(), 2.5f);
        GlStateManager._enableDepthTest();
    }

    private static Color interpolateColors(Color a, Color b, double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        int r = (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        return new Color(
                Math.max(0, Math.min(255, r)),
                Math.max(0, Math.min(255, g)),
                Math.max(0, Math.min(255, bl))
        );
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
    }
}
