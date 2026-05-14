package dev.mzc.client.module.impl.movement;

import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.player.MovementUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;

public class Speed extends Module {

    public enum Mode {
        Strafe(),
        Vanilla(),
        OnGround(),
        Melon(),
        GrimCollide(),
        GrimTimer()
    }

    private final EnumValue<Mode> mode = new EnumValue<>("Mode", Mode.Strafe);
    private final NumberValue<Double> speed = new NumberValue<>("Speed", 0.5, 0.1, 10.0, 0.1);
    private final BoolValue autoJump = new BoolValue("AutoJump", true);
    private final BoolValue inAir = new BoolValue("InAir", true);

    private long lastTimerBoost = System.currentTimeMillis();
    private boolean boosting = false;

    public Speed() {
        super("Speed", Category.Movement);
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        if (!MovementUtil.isMoving() && !mode.is(Mode.GrimCollide)) {
            mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
            return;
        }

        switch (mode.get()) {
            case Strafe -> {
                if (autoJump.get() && mc.player.isOnGround()) {
                    mc.player.jump();
                }
                if (inAir.get() || mc.player.isOnGround()) {
                    MovementUtil.strafe(speed.get());
                }
            }

            case Vanilla -> {
                if (autoJump.get() && mc.player.isOnGround()) {
                    mc.player.jump();
                }
                MovementUtil.strafe(speed.get());
            }

            case OnGround -> {
                if (mc.player.isOnGround()) {
                    if (autoJump.get()) {
                        mc.player.jump();
                    }
                    MovementUtil.strafe(speed.get());
                }
            }

            case Melon -> {
                float melonBallSpeed = 0.36F;
                ItemStack offHandItem = mc.player.getOffHandStack();
                StatusEffectInstance speedEffect = mc.player.getStatusEffect(StatusEffects.SPEED);
                StatusEffectInstance slowEffect = mc.player.getStatusEffect(StatusEffects.SLOWNESS);

                String itemName = offHandItem.getName().getString();
                float appliedSpeed;

                if (speedEffect != null) {
                    if (speedEffect.getAmplifier() == 2) {
                        appliedSpeed = melonBallSpeed * 1.155F;
                        if (itemName.contains("Ломтик Дыни")) {
                            appliedSpeed = 0.41755F;
                        }
                    } else {
                        appliedSpeed = melonBallSpeed;
                    }
                } else {
                    appliedSpeed = melonBallSpeed * 0.68F;
                }

                if (slowEffect != null) {
                    appliedSpeed *= 0.835f;
                }

                if (!mc.player.isOnGround()) {
                    appliedSpeed *= 1.435F;
                }

                MovementUtil.strafe(appliedSpeed);
            }

            case GrimCollide -> {
                int collisions = 0;
                for (Entity entity : mc.world.getEntities()) {
                    if (entity instanceof LivingEntity living && living != mc.player && !(living instanceof ArmorStandEntity)) {
                        // Проверка пересечения хитбоксов
                        if (mc.player.getBoundingBox().expand(0.05).intersects(living.getBoundingBox())) {
                            collisions++;
                        }
                    }
                }

                if (collisions > 0 && MovementUtil.isMoving()) {
                    float yaw = (float) Math.toRadians(mc.player.getYaw());
                    double x = -Math.sin(yaw) * (0.08 * collisions);
                    double z = Math.cos(yaw) * (0.08 * collisions);
                    mc.player.addVelocity(x, 0, z);
                }
            }

            case GrimTimer -> {
                if (MovementUtil.isMoving()) {
                    long now = System.currentTimeMillis();
                    if (now - lastTimerBoost > 7000) {
                        lastTimerBoost = now;
                        boosting = false;
                    } else if (now - lastTimerBoost > 1100) {
                        boosting = true;
                    }

                    if (boosting) {
                        // Эмуляция TimerManager
                        // В Minecraft 1.21 нет прямого доступа к таймеру через renderTickCounter
                        // Обычно это реализуется через миксины в ClientWorld или MinecraftClient
                        // Если у вас нет TimerManager, это ускорение будет работать только визуально
                    }
                }
            }
        }
    }
}
