package dev.mzc.client.module.impl.combat;

import dev.mzc.client.events.EventType;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.packet.PacketEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;

import java.util.Random;

public class TimerRange extends Module {

    private final EnumValue<Mode> mode = new EnumValue<>("Mode", Mode.Normal);
    private final NumberValue<Integer> ticks = new NumberValue<>("Ticks", 10, 1, 20, 1);
    private final NumberValue<Double> timerBoost = new NumberValue<>("TimerBoost", 1.5, 0.1, 5.0, 0.1);
    private final NumberValue<Double> range = new NumberValue<>("Range", 3.5, 1.0, 6.0, 0.1);
    private final BoolValue resetOnLagback = new BoolValue("ResetOnLagback", true);
    private final BoolValue resetOnKnockback = new BoolValue("ResetOnKnockback", true);

    private int playerTicks = 0;
    private final Random random = new Random();

    public enum Mode {
        Normal, Smart
    }

    public TimerRange() {
        super("TimerRange", Category.Combat);
        addValues(mode, ticks, timerBoost, range, resetOnLagback, resetOnKnockback);
    }

    private void addValues(dev.mzc.client.values.Value<?>... values) {
        for (dev.mzc.client.values.Value<?> v : values) {
            this.values.add(v);
        }
    }

    @Override
    public void onDisable() {
        resetTimer();
        playerTicks = 0;
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        LivingEntity target = getNearestTarget();
        if (target != null && mc.player.distanceTo(target) <= range.get()) {
            if (playerTicks <= 0) {
                playerTicks = ticks.get();
            }
        }

        if (playerTicks > 0) {
            // В Minecraft 1.21 нет прямого доступа к mc.timer.timerSpeed
            // Это обычно реализуется через миксины. 
            // Мы можем попробовать имитировать это через изменение скорости движения или ждать реализации TimerManager
            playerTicks--;
        } else {
            resetTimer();
        }
    }

    @EventHandler
    public void onPacket(PacketEvent event) {
        if (nullCheck()) return;
        Packet<?> packet = event.getPacket();

        if (resetOnLagback.get() && packet instanceof PlayerPositionLookS2CPacket) {
            resetTimer();
            playerTicks = 0;
        }

        if (resetOnKnockback.get() && packet instanceof EntityVelocityUpdateS2CPacket p) {
            if (p.getEntityId() == mc.player.getId()) {
                resetTimer();
                playerTicks = 0;
            }
        }
    }

    private void resetTimer() {
        // mc.timer.timerSpeed = 1.0f; 
    }

    private LivingEntity getNearestTarget() {
        LivingEntity nearest = null;
        double distance = Double.MAX_VALUE;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.isDead() || player.isRemoved()) continue;
            double d = mc.player.distanceTo(player);
            if (d < distance) {
                distance = d;
                nearest = player;
            }
        }
        return nearest;
    }
}
