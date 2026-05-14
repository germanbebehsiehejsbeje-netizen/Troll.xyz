package dev.mzc.client.module.impl.misc;

import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.manager.Managers;
import dev.mzc.client.manager.impl.RotationManager;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.rotation.MovementFix;
import dev.mzc.client.utils.vector.Rotation;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import dev.mzc.client.utils.time.TimerUtil;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class AntiAFK extends Module {
    private final BoolValue rotate = new BoolValue("Rotate Head", true);
    private final BoolValue onlyIdle = new BoolValue("Only Idle", false);
    private final NumberValue<Integer> speed = new NumberValue<>("Rotation Speed", 5, 1, 20, 1, rotate::get);
    private final NumberValue<Double> angle = new NumberValue<>("Angle", 10.0, 1.0, 90.0, 1.0, rotate::get);
    private final BoolValue autoJump = new BoolValue("Auto Jump", true);
    private final NumberValue<Integer> jumpInterval = new NumberValue<>("Jump Interval", 1500, 200, 10000, 100, autoJump::get);
    private final BoolValue autoOpen = new BoolValue("Auto Open", false);
    private final NumberValue<Integer> idleDelayMs = new NumberValue<>("Idle Delay", 30000, 1000, 600000, 500, autoOpen::get);

    private float baseYaw;
    private double phaseDeg;
    private final TimerUtil jumpTimer = new TimerUtil();
    private long lastActiveMs;
    private boolean engaged;
    private boolean prevAutoOpen;
    private Vec3d lastPos;

    public AntiAFK() {
        super("AntiAFK", Category.Misc);
        this.setType(ModuleType.Safe);
    }

    @Override
    public void onEnable() {
        if (mc.player != null) {
            baseYaw = mc.player.getYaw();
            lastPos = mc.player.getEntityPos();
        }
        phaseDeg = 0.0;
        jumpTimer.reset();
        lastActiveMs = System.currentTimeMillis();
        prevAutoOpen = autoOpen.get();
        engaged = !prevAutoOpen;
    }

    @Override
    public void onDisable() {
        phaseDeg = 0.0;
        engaged = false;
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;
        long now = System.currentTimeMillis();
        Vec3d pos = mc.player.getEntityPos();

        boolean auto = autoOpen.get();
        if (auto != prevAutoOpen) {
            prevAutoOpen = auto;
            lastActiveMs = now;
            engaged = !auto;
            phaseDeg = 0.0;
            baseYaw = mc.player.getYaw();
            jumpTimer.reset();
        }

        if (auto) {
            boolean inputActive = isInputActive();
            boolean moved = !engaged && lastPos != null && pos.squaredDistanceTo(lastPos) > 1.0E-4;

            if (inputActive || moved) {
                lastActiveMs = now;
                engaged = false;
                phaseDeg = 0.0;
                baseYaw = mc.player.getYaw();
                jumpTimer.reset();
                lastPos = pos;
                return;
            }

            if (!engaged) {
                if (now - lastActiveMs >= idleDelayMs.get()) {
                    engaged = true;
                    baseYaw = mc.player.getYaw();
                    phaseDeg = 0.0;
                    jumpTimer.reset();
                } else {
                    lastPos = pos;
                    return;
                }
            }
        }

        lastPos = pos;
        if (!rotate.get()) return;
        if (onlyIdle.get() && !isIdleMovement()) return;

        phaseDeg += angle.get();
        if (phaseDeg >= 360.0) phaseDeg -= 360.0;
        if (phaseDeg < 0.0) phaseDeg += 360.0;
        float yaw = MathHelper.wrapDegrees(baseYaw + (float) phaseDeg);

        Rotation r = new Rotation(yaw, mc.player.getPitch());
        Managers.ROTATION.setRotations(r, speed.get(), MovementFix.OFF, RotationManager.Priority.Lowest);
        
        if (autoJump.get() && jumpTimer.passedMS(jumpInterval.get())) {
            if (mc.player.isOnGround()) {
                mc.player.jump();
            }
            jumpTimer.reset();
        }
    }

    private boolean isInputActive() {
        MinecraftClient c = mc;
        if (c == null) return false;
        return c.options.forwardKey.isPressed()
                || c.options.backKey.isPressed()
                || c.options.leftKey.isPressed()
                || c.options.rightKey.isPressed()
                || c.options.jumpKey.isPressed()
                || c.options.sneakKey.isPressed()
                || c.options.attackKey.isPressed()
                || c.options.useKey.isPressed();
    }

    private boolean isIdleMovement() {
        MinecraftClient c = mc;
        if (c == null || c.player == null) return false;
        boolean directionPressed = c.options.forwardKey.isPressed()
                || c.options.backKey.isPressed()
                || c.options.leftKey.isPressed()
                || c.options.rightKey.isPressed();
        return !directionPressed;
    }
}
