package dev.mzc.client.module.impl.movement;

import dev.mzc.client.Sakura;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.player.UpdateVelocityEvent;
import dev.mzc.client.manager.Managers;
import dev.mzc.client.manager.impl.RotationManager;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.module.impl.combat.KillAura;
import dev.mzc.client.utils.rotation.MovementFix;
import dev.mzc.client.utils.vector.Rotation;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class TargetStrafe extends Module {

    public enum Mode { Matrix, Grim }
    public enum GrimPoint { Cube, Center, Circle }
    public enum MatrixPoint { Circle, Cube }
    public enum Direction { Clockwise, Counterclockwise, Random }

    private final EnumValue<Mode> mode = new EnumValue<>("Mode", Mode.Grim);
    private final EnumValue<GrimPoint> grimType = new EnumValue<>("Grim Point", GrimPoint.Cube, () -> mode.get() == Mode.Grim);
    private final EnumValue<Direction> grimDirection = new EnumValue<>("Grim Direction", Direction.Clockwise, () -> mode.get() == Mode.Grim);
    private final NumberValue<Double> grimRadius = new NumberValue<>("Grim Radius", 0.87, 0.1, 1.5, 0.01, () -> mode.get() == Mode.Grim);

    private final EnumValue<MatrixPoint> matrixType = new EnumValue<>("Matrix Point", MatrixPoint.Circle, () -> mode.get() == Mode.Matrix);
    private final EnumValue<Direction> matrixDirection = new EnumValue<>("Matrix Direction", Direction.Clockwise, () -> mode.get() == Mode.Matrix);
    private final NumberValue<Double> matrixRadius = new NumberValue<>("Matrix Radius", 2.5, 0.1, 7.0, 0.01, () -> mode.get() == Mode.Matrix);
    private final NumberValue<Double> matrixSpeed = new NumberValue<>("Matrix Speed", 0.3, 0.1, 1.0, 0.01, () -> mode.get() == Mode.Matrix);

    private final BoolValue autoJump = new BoolValue("Auto Jump", true);
    private final BoolValue onlyKeyPressed = new BoolValue("Only Key Pressed", false);
    private final BoolValue inFrontOfTarget = new BoolValue("In Front Of Target", false);

    private int grimPointIndex = 0;

    public TargetStrafe() {
        super("TargetStrafe", Category.Movement);
        this.setType(ModuleType.Hack);
    }

    @EventHandler
    public void onUpdateVelocity(UpdateVelocityEvent event) {
        if (nullCheck() || mode.get() != Mode.Grim) return;

        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) return;

        if (onlyKeyPressed.get() && !isMoving()) return;

        // Было: Vec3d playerPos = mc.player.getPos(); (или getPosition)
// Исправлено:
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Vec3d targetPos = new Vec3d(target.getX(), target.getY(), target.getZ());
        double r = grimRadius.get();
        int dirMultiplier = getDirectionMultiplier(grimDirection.get());

        Vec3d nextPoint = calculateNextPoint(target, playerPos, targetPos, r, dirMultiplier);
        if (nextPoint == null) return;

        // Вычисляем вектор к точке
        Vec3d diff = nextPoint.subtract(playerPos);
        float desiredYaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90f;

        // Устанавливаем ротации (Важно: приоритет Highest для Grim)
        Rotation rot = new Rotation(desiredYaw, mc.player.getPitch());
        Managers.ROTATION.setRotations(rot, 100, MovementFix.GRIM, RotationManager.Priority.Highest);

        // Grim Speed Logic
        double speed = getGrimSpeed();

        // Математически корректный расчет вектора скорости
        float radians = (float) Math.toRadians(desiredYaw);
        double motionX = -Math.sin(radians) * speed;
        double motionZ = Math.cos(radians) * speed;

        // Устанавливаем скорость, не ломая Y (вертикальное движение)
        event.setVelocity(new Vec3d(motionX, event.getVelocity().y, motionZ));
        event.cancel();

        if (autoJump.get() && mc.player.isOnGround() && isMoving()) {
            mc.player.jump();
        }
    }

    private Vec3d calculateNextPoint(LivingEntity target, Vec3d pPos, Vec3d tPos, double r, int dir) {
        if (inFrontOfTarget.get()) {
            float tYaw = target.getYaw();
            return tPos.add(-Math.sin(Math.toRadians(tYaw)) * r, 0, Math.cos(Math.toRadians(tYaw)) * r);
        }

        switch (grimType.get()) {
            case Center -> {
                return tPos;
            }
            case Circle -> {
                double currentAngle = Math.atan2(pPos.z - tPos.z, pPos.x - tPos.x);
                double nextAngle = currentAngle + (dir * 0.4); // Угловой шаг
                return new Vec3d(tPos.x + Math.cos(nextAngle) * r, pPos.y, tPos.z + Math.sin(nextAngle) * r);
            }
            case Cube -> {
                Vec3d[] points = getCubePoints(tPos, pPos, r);
                // Ищем ближайшую точку по направлению движения, чтобы не было флагов за Step
                double bestDist = Double.MAX_VALUE;
                int bestIdx = grimPointIndex;
                for (int i = 0; i < points.length; i++) {
                    double d = pPos.squaredDistanceTo(points[i]);
                    if (d < bestDist) {
                        bestDist = d;
                        bestIdx = i;
                    }
                }
                if (bestDist < 0.2) {
                    grimPointIndex = (bestIdx + dir + points.length) % points.length;
                }
                return points[grimPointIndex];
            }
        }
        return tPos;
    }

    private double getGrimSpeed() {
        double base = 0.2806; // Базовая скорость ходьбы
        if (mc.player.isOnGround()) return base;

        // Рассчитываем скорость в воздухе (Grim разрешает небольшое ускорение при прыжке)
        return mc.player.getAbilities().getWalkSpeed() * 2.15;
    }

    private boolean isMoving() {
        return mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed() ||
                mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed();
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck() || mode.get() != Mode.Matrix) return;

        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) return;
        if (onlyKeyPressed.get() && !isMoving()) return;

        if (autoJump.get() && mc.player.isOnGround()) mc.player.jump();

        // Было: Vec3d pPos = mc.player.getPosition();
// Исправлено:
        Vec3d pPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Vec3d tPos = new Vec3d(target.getX(), target.getY(), target.getZ());
        double r = matrixRadius.get();
        double speed = matrixSpeed.get();
        int dir = getDirectionMultiplier(matrixDirection.get());

        // Matrix Logic (Tick based)
        double angle = Math.atan2(pPos.z - tPos.z, pPos.x - tPos.x);
        angle += dir * speed / Math.max(pPos.distanceTo(tPos), 0.1);

        double x = tPos.x + r * Math.cos(angle);
        double z = tPos.z + r * Math.sin(angle);

        float yaw = (float) Math.toDegrees(Math.atan2(z - pPos.z, x - pPos.x)) - 90f;

        mc.player.setVelocity(
                -Math.sin(Math.toRadians(yaw)) * speed,
                mc.player.getVelocity().y,
                Math.cos(Math.toRadians(yaw)) * speed
        );
    }

    private LivingEntity getTarget() {
        KillAura ka = Sakura.MODULES.getModule(KillAura.class);
        return (ka != null && ka.isEnabled()) ? ka.getCurrentTarget() : null;
    }

    private int getDirectionMultiplier(Direction direction) {
        if (direction == Direction.Counterclockwise) return -1;
        if (direction == Direction.Random) return (System.currentTimeMillis() / 2000) % 2 == 0 ? 1 : -1;
        return 1;
    }

    private Vec3d[] getCubePoints(Vec3d tPos, Vec3d pPos, double r) {
        return new Vec3d[]{
                new Vec3d(tPos.x - r, pPos.y, tPos.z - r),
                new Vec3d(tPos.x - r, pPos.y, tPos.z + r),
                new Vec3d(tPos.x + r, pPos.y, tPos.z + r),
                new Vec3d(tPos.x + r, pPos.y, tPos.z - r)
        };
    }

    @Override
    protected void onEnable() {
        grimPointIndex = 0;
    }
}