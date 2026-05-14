package dev.mzc.client.module.impl.misc;

import dev.mzc.client.events.EventType;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.packet.PacketEvent;
import dev.mzc.client.manager.impl.NotificationManager;
import dev.mzc.client.module.impl.misc.cheatdetector.CheatCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.CheatDetectorContext;
import dev.mzc.client.module.impl.misc.cheatdetector.TrackedPlayer;
import dev.mzc.client.module.impl.misc.cheatdetector.checks.AimGcdCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.checks.AimSmoothCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.checks.AutoClickerCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.checks.FlyCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.checks.InvalidRotationCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.checks.VelocityResponseCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.checks.aim.AimStepCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.checks.movement.BlinkACheck;
import dev.mzc.client.module.impl.misc.cheatdetector.checks.movement.GroundSpoofACheck;
import dev.mzc.client.module.impl.misc.cheatdetector.checks.movement.GroundSpoofBCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.checks.movement.HighJumpACheck;
import dev.mzc.client.module.impl.misc.cheatdetector.checks.movement.MotionACheck;
import dev.mzc.client.module.impl.misc.cheatdetector.checks.movement.MotionBCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.checks.movement.NoSlowACheck;
import dev.mzc.client.module.impl.misc.cheatdetector.checks.movement.SpeedACheck;
import dev.mzc.client.module.impl.misc.cheatdetector.checks.movement.SpeedBCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.checks.movement.SpeedCCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.checks.movement.StrafeACheck;
import dev.mzc.client.module.impl.misc.cheatdetector.checks.movement.BoatFlyACheck;
import dev.mzc.client.module.impl.misc.cheatdetector.checks.movement.FlyACheck;
import dev.mzc.client.module.impl.misc.cheatdetector.checks.movement.FlyBCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.checks.movement.FlyCCheck;
import dev.mzc.client.module.impl.misc.cheatdetector.checks.exploits.GameModeACheck;
import dev.mzc.client.module.impl.misc.cheatdetector.checks.exploits.TimerACheck;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CheatDetector extends Module {
    private final BoolValue notifyChat = new BoolValue("Chat", true);
    private final BoolValue notifyHud = new BoolValue("Notification", true);
    private final NumberValue<Integer> notifyCooldownMs = new NumberValue<>("NotifyCD", 2500, 250, 20000, 50);

    private final BoolValue checkAimSmooth = new BoolValue("AimSmooth", true);
    private final NumberValue<Integer> smoothWindow = new NumberValue<>("SmoothWindow", 8, 3, 20, 1, checkAimSmooth::get);
    private final NumberValue<Double> smoothVarEps = new NumberValue<>("SmoothVar", 0.02, 0.0, 5.0, 0.01, checkAimSmooth::get);
    private final NumberValue<Double> minYawSpeed = new NumberValue<>("MinYawSpeed", 0.3, 0.0, 30.0, 0.1, checkAimSmooth::get);
    private final NumberValue<Double> maxYawSpeed = new NumberValue<>("MaxYawSpeed", 20.0, 1.0, 360.0, 1.0, checkAimSmooth::get);

    private final BoolValue checkAimGcd = new BoolValue("AimGCD", true);
    private final NumberValue<Integer> gcdSignificant = new NumberValue<>("GcdHits", 20, 3, 60, 1, checkAimGcd::get);

    private final BoolValue checkAimStep = new BoolValue("AimStep", false);
    private final NumberValue<Double> aimStepMinDiffYaw = new NumberValue<>("AimStepYawEps", 2.0, 0.1, 20.0, 0.1, checkAimStep::get);
    private final NumberValue<Double> aimStepMinDiffPitch = new NumberValue<>("AimStepPitchEps", 2.0, 0.1, 20.0, 0.1, checkAimStep::get);

    private final BoolValue checkInvalidRot = new BoolValue("InvalidRot", true);
    private final BoolValue checkAutoClicker = new BoolValue("AutoClicker", true);
    private final NumberValue<Integer> maxSwingsPerSecond = new NumberValue<>("MaxSwingsSecond", 18, 6, 40, 1, checkAutoClicker::get);

    private final BoolValue checkFly = new BoolValue("Fly", true);
    private final NumberValue<Integer> maxOffGroundTicks = new NumberValue<>("MaxOffGround", 18, 4, 120, 1, checkFly::get);

    private final BoolValue checkGroundSpoofA = new BoolValue("GroundSpoofA", false);
    private final BoolValue checkGroundSpoofB = new BoolValue("GroundSpoofB", false);

    private final BoolValue checkVelocity = new BoolValue("Velocity", true);
    private final NumberValue<Double> minVelocityApply = new NumberValue<>("MinVelApply", 0.18, 0.0, 1.0, 0.01, checkVelocity::get);
    private final NumberValue<Integer> velWindowTicks = new NumberValue<>("VelWindow", 6, 2, 20, 1, checkVelocity::get);

    private final BoolValue checkSpeedA = new BoolValue("SpeedA", true);
    private final BoolValue checkSpeedB = new BoolValue("SpeedB", true);
    private final BoolValue checkSpeedC = new BoolValue("SpeedC", true);

    private final BoolValue checkNoSlowA = new BoolValue("NoSlowA", false);
    private final BoolValue checkMotionA = new BoolValue("MotionA", false);
    private final BoolValue checkMotionB = new BoolValue("MotionB", false);
    private final BoolValue checkStrafeA = new BoolValue("StrafeA", false);
    private final BoolValue checkBlinkA = new BoolValue("BlinkA", false);
    private final NumberValue<Double> blinkMaxDistance = new NumberValue<>("BlinkDist", 8.0, 2.0, 40.0, 0.25, checkBlinkA::get);
    private final BoolValue checkHighJumpA = new BoolValue("HighJumpA", false);
    private final BoolValue checkFlyA = new BoolValue("FlyA", false);
    private final BoolValue checkFlyB = new BoolValue("FlyB", false);
    private final BoolValue checkFlyC = new BoolValue("FlyC", false);
    private final NumberValue<Integer> flyCMinRepeatTicks = new NumberValue<>("FlyCRepeat", 10, 3, 30, 1, checkFlyC::get);
    private final BoolValue checkBoatFlyA = new BoolValue("BoatFlyA", false);

    private final BoolValue checkGameModeA = new BoolValue("GameModeA", false);
    private final BoolValue checkTimerA = new BoolValue("TimerA", false);
    private final NumberValue<Integer> timerAFlagBalance = new NumberValue<>("TimerBalance", 100, 50, 400, 10, checkTimerA::get);

    private final Map<UUID, TrackedPlayer> players = new HashMap<>();
    private final Map<String, Long> lastNotify = new HashMap<>();

    private final List<CheatCheck> checks = List.of(
            new InvalidRotationCheck(checkInvalidRot),
            new AutoClickerCheck(checkAutoClicker, maxSwingsPerSecond),
            new AimSmoothCheck(checkAimSmooth, smoothWindow, smoothVarEps, minYawSpeed, maxYawSpeed),
            new AimGcdCheck(checkAimGcd, gcdSignificant),
            new AimStepCheck(checkAimStep, aimStepMinDiffYaw, aimStepMinDiffPitch),
            new SpeedACheck(checkSpeedA),
            new SpeedBCheck(checkSpeedB),
            new SpeedCCheck(checkSpeedC),
            new NoSlowACheck(checkNoSlowA),
            new MotionACheck(checkMotionA),
            new MotionBCheck(checkMotionB),
            new StrafeACheck(checkStrafeA),
            new BlinkACheck(checkBlinkA, blinkMaxDistance),
            new HighJumpACheck(checkHighJumpA),
            new FlyACheck(checkFlyA),
            new FlyBCheck(checkFlyB),
            new FlyCCheck(checkFlyC, flyCMinRepeatTicks),
            new BoatFlyACheck(checkBoatFlyA),
            new FlyCheck(checkFly, maxOffGroundTicks),
            new GroundSpoofACheck(checkGroundSpoofA),
            new GroundSpoofBCheck(checkGroundSpoofB),
            new VelocityResponseCheck(checkVelocity, minVelocityApply, velWindowTicks),
            new GameModeACheck(checkGameModeA),
            new TimerACheck(checkTimerA, timerAFlagBalance)
    );

    public CheatDetector() {
        super("CheatDetector", Category.Misc);
        this.setType(ModuleType.Hack);
    }

    @Override
    protected void onDisable() {
        players.clear();
        lastNotify.clear();
    }

    @EventHandler
    public void onTick(TickEvent.Pre e) {
        if (nullCheck() || mc.world == null || mc.player == null) return;

        CheatDetectorContext context = new CheatDetectorContext(mc, mc.world.getTime(), this::notifyOnce);
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            TrackedPlayer tp = players.computeIfAbsent(p.getUuid(), id -> new TrackedPlayer());
            tp.update(p, mc.world.getTime());
            for (CheatCheck check : checks) {
                check.onTick(p, tp, context);
            }
        }

        players.entrySet().removeIf(en -> {
            PlayerEntity p = mc.world.getPlayerByUuid(en.getKey());
            return p == null || p == mc.player;
        });
    }

    @EventHandler
    public void onPacket(PacketEvent event) {
        if (nullCheck() || mc.world == null || mc.player == null) return;
        if (event.getType() != EventType.RECEIVE) return;

        CheatDetectorContext context = new CheatDetectorContext(mc, mc.world.getTime(), this::notifyOnce);
        for (CheatCheck check : checks) {
            check.onPacket(event.getPacket(), context);
        }

        if (event.getPacket() instanceof EntityAnimationS2CPacket pkt) {
            Entity entity = mc.world.getEntityById(pkt.getEntityId());
            if (entity instanceof PlayerEntity p && p != mc.player) {
                TrackedPlayer tp = players.computeIfAbsent(p.getUuid(), id -> new TrackedPlayer());
                tp.onSwing(mc.world.getTime());
            }
        } else if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket pkt) {
            Entity entity = mc.world.getEntityById(pkt.getEntityId());
            if (entity instanceof PlayerEntity p && p != mc.player) {
                TrackedPlayer tp = players.computeIfAbsent(p.getUuid(), id -> new TrackedPlayer());
                tp.onVelocity(pkt.getVelocity(), mc.world.getTime());
            }
        }
    }

    private void notifyOnce(PlayerEntity p, String reason) {
        String normalizedReason = reason;
        int i = normalizedReason.indexOf('(');
        if (i > 0) normalizedReason = normalizedReason.substring(0, i).trim();
        String key = p.getUuidAsString() + "|" + normalizedReason;
        long now = System.currentTimeMillis();
        long last = lastNotify.getOrDefault(key, 0L);
        if (now - last < notifyCooldownMs.get()) return;
        lastNotify.put(key, now);

        String msg = "§c[CheatDetector] §f" + p.getName().getString() + " §7" + reason;
        if (notifyHud.get()) {
            NotificationManager.send(key, msg, 3000L);
        }
        if (notifyChat.get()) {
            mc.player.sendMessage(net.minecraft.text.Text.of(msg), false);
        }
    }
}
