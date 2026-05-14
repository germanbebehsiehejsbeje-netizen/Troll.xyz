package dev.mzc.client.module.impl.render;

import dev.mzc.client.events.render.Render3DEvent;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.mixin.accessor.IClientPlayerInteractionManager;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.utils.render.Render3DUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.ColorValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.render.state.BreakingBlockRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.awt.*;
import java.util.Locale;

public class MiningAnimation extends Module {
    private final EnumValue<Mode> mode = new EnumValue<>("Mode", Mode.Expand, Mode.class);
    private final EnumValue<ColorMode> colorMode = new EnumValue<>("ColorMode", ColorMode.Double, ColorMode.class);

    private final ColorValue fillSingle = new ColorValue("Fill", new Color(255, 255, 255, 50), () -> colorMode.is(ColorMode.Single));
    private final ColorValue outlineSingle = new ColorValue("Outline", new Color(255, 255, 255, 200), () -> colorMode.is(ColorMode.Single));
    private final ColorValue fillStart = new ColorValue("FillStart", new Color(255, 0, 0, 50), () -> colorMode.is(ColorMode.Double));
    private final ColorValue fillEnd = new ColorValue("FillEnd", new Color(0, 255, 0, 50), () -> colorMode.is(ColorMode.Double));
    private final ColorValue outlineStart = new ColorValue("OutlineStart", new Color(255, 0, 0, 200), () -> colorMode.is(ColorMode.Double));
    private final ColorValue outlineEnd = new ColorValue("OutlineEnd", new Color(0, 255, 0, 200), () -> colorMode.is(ColorMode.Double));

    private final NumberValue<Float> lineWidth = new NumberValue<>("LineWidth", 1.5f, 0.1f, 5.0f, 0.1f);
    private final BoolValue disableVanillaTexture = new BoolValue("NoVanilla", false);
    private final BoolValue debugHud = new BoolValue("DebugHud", false);
    private static final double MIN_SCALE = 0.05;
    private static final float RENDER_SMOOTH_SPEED = 14.0f;

    private BlockPos currentPos;
    private float targetProgress;
    private float renderedProgress;
    private long lastRenderNanos;

    public MiningAnimation() {
        super("MiningAnimation", Category.Render);
        this.setType(ModuleType.All);
    }

    public boolean disableVanillaTexture() {
        return isEnabled() && disableVanillaTexture.get();
    }

    @Override
    protected void onEnable() {
        resetProgressState();
    }

    @Override
    protected void onDisable() {
        resetProgressState();
    }

    public void captureBreakingState(WorldRenderState worldRenderState) {
        if (!isEnabled() || worldRenderState == null || mc.player == null) return;
        if (worldRenderState.breakingBlockRenderStates == null || worldRenderState.breakingBlockRenderStates.isEmpty()) return;

        BreakingBlockRenderState selected = null;
        double nearest = Double.MAX_VALUE;
        for (BreakingBlockRenderState state : worldRenderState.breakingBlockRenderStates) {
            if (state == null || state.entityBlockPos == null) continue;
            double dist = state.entityBlockPos.getSquaredDistance(mc.player.getBlockPos());
            if (dist < nearest) {
                nearest = dist;
                selected = state;
            }
        }

        if (selected == null) return;

        BlockPos pos = selected.entityBlockPos.toImmutable();
        float raw = MathHelper.clamp((selected.breakProgress + 1) / 10.0f, 0.0f, 1.0f);
        applyProgressSample(pos, raw);
    }

    public void renderFromDamagePass(MatrixStack matrices, WorldRenderState worldRenderState) {
        if (!isEnabled() || worldRenderState == null) return;
        captureBreakingState(worldRenderState);
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck() || mc.interactionManager == null) return;

        IClientPlayerInteractionManager im = (IClientPlayerInteractionManager) mc.interactionManager;
        BlockPos pos = im.getCurrentBreakingPos();

        if (pos == null || mc.world.getBlockState(pos).isAir()) {
            resetProgressState();
            return;
        }

        float raw = MathHelper.clamp(im.getCurrentBreakingProgress(), 0.0f, 1.0f);
        if (raw <= 0.0f) {
            resetProgressState();
            return;
        }

        applyProgressSample(pos, raw);
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;
        if (mc.interactionManager == null) return;

        if (currentPos == null || mc.world == null) return;
        if (mc.world.getBlockState(currentPos).isAir()) {
            resetProgressState();
            return;
        }

        IClientPlayerInteractionManager im = (IClientPlayerInteractionManager) mc.interactionManager;
        BlockPos livePos = im.getCurrentBreakingPos();
        if (livePos == null || !livePos.equals(currentPos)) {
            resetProgressState();
            return;
        }
        if (MathHelper.clamp(im.getCurrentBreakingProgress(), 0.0f, 1.0f) <= 0.0f) {
            resetProgressState();
            return;
        }

        long now = System.nanoTime();
        if (lastRenderNanos == 0L) lastRenderNanos = now;
        float dt = MathHelper.clamp((now - lastRenderNanos) / 1_000_000_000.0f, 0.0f, 0.05f);
        lastRenderNanos = now;

        float alpha = 1.0f - (float) Math.exp(-RENDER_SMOOTH_SPEED * dt);
        renderedProgress += (targetProgress - renderedProgress) * alpha;
        if (Math.abs(renderedProgress - targetProgress) < 0.0005f) renderedProgress = targetProgress;

        float progress = renderedProgress;
        if (progress <= 0.001f) return;

        renderAt(event.getMatrices(), currentPos, progress);
    }

    private Box buildAnimatedBox(BlockPos pos, double progress) {
        Box base = new Box(pos);
        if (mode.is(Mode.Full)) return base;

        double scale = 1.0;
        if (mode.is(Mode.Expand)) {
            scale = Math.max(MIN_SCALE, progress);
        } else if (mode.is(Mode.Shrink)) {
            scale = Math.max(MIN_SCALE, 1.0 - progress);
        }

        double contract = (1.0 - scale) * 0.5;
        return base.expand(-contract);
    }

    private void renderAt(MatrixStack matrices, BlockPos pos, double progress) {
        if (matrices == null || pos == null) return;
        if (mc.world == null || mc.world.getBlockState(pos).isAir()) return;
        if (progress <= 0.0) return;

        Box box = buildAnimatedBox(pos, progress);

        Color fill = getFillColor(progress);
        Color outline = getOutlineColor(progress);

        Render3DUtil.drawFilledBoxThroughWalls(matrices, box, fill);
        Render3DUtil.drawBoxOutlineThroughWalls(matrices, box, outline.getRGB(), lineWidth.get());

        if (debugHud.get()) {
            Vec3d center = new Vec3d(
                    box.minX + (box.maxX - box.minX) * 0.5,
                    box.maxY + 0.2,
                    box.minZ + (box.maxZ - box.minZ) * 0.5
            );
            String debugText = String.format(Locale.ROOT, "Mining %.0f%% %s", progress * 100.0, pos.toShortString());
            Render3DUtil.drawText(debugText, center, 0, 0, 0, Color.WHITE);
        }
    }

    private void applyProgressSample(BlockPos pos, float rawProgress) {
        float clamped = MathHelper.clamp(rawProgress, 0.0f, 1.0f);
        if (currentPos == null || !currentPos.equals(pos)) {
            currentPos = pos.toImmutable();
            targetProgress = clamped;
            renderedProgress = clamped;
            return;
        }

        // Keep tiny backward jitter from packets from causing visible "steps".
        if (clamped > 0.0f && clamped + 0.01f < targetProgress) {
            targetProgress = Math.max(targetProgress - 0.03f, clamped);
        } else {
            targetProgress = clamped;
        }
    }

    private void resetProgressState() {
        currentPos = null;
        targetProgress = 0.0f;
        renderedProgress = 0.0f;
        lastRenderNanos = 0L;
    }

    private Color getFillColor(double progress) {
        return switch (colorMode.get()) {
            case Client -> {
                Color c1 = ClickGui.color(0);
                Color c2 = ClickGui.color2(0);
                Color base = lerpColor(c1, c2, progress);
                yield new Color(base.getRed(), base.getGreen(), base.getBlue(), 50);
            }
            case Single -> fillSingle.get();
            case Double -> lerpColor(fillStart.get(), fillEnd.get(), progress);
        };
    }

    private Color getOutlineColor(double progress) {
        return switch (colorMode.get()) {
            case Client -> {
                Color c1 = ClickGui.color(0);
                Color c2 = ClickGui.color2(0);
                Color base = lerpColor(c1, c2, progress);
                yield new Color(base.getRed(), base.getGreen(), base.getBlue(), 200);
            }
            case Single -> outlineSingle.get();
            case Double -> lerpColor(outlineStart.get(), outlineEnd.get(), progress);
        };
    }

    private static Color lerpColor(Color a, Color b, double t) {
        t = MathHelper.clamp(t, 0.0, 1.0);
        int r = (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        int al = (int) Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t);
        return new Color(r, g, bl, al);
    }

    public enum Mode {
        Expand(),
        Shrink(),
        Full();
        Mode() {
        }
    }

    public enum ColorMode {
        Client(),
        Single(),
        Double();
        ColorMode() {
        }
    }
}
