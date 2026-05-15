package dev.mzc.client.module.impl.movement;

import dev.mzc.client.events.EventType;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.packet.PacketEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.mixin.accessor.IExplosionS2CPacket;
import dev.mzc.client.utils.player.MovementUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;

public class LongJump extends Module {

    public enum Mode {
        Blink(),
        Packet(),
        Legit()
    }

    private final EnumValue<Mode> mode = new EnumValue<>("Mode", Mode.Blink);
    private final NumberValue<Double> speedMultiplier = new NumberValue<>("SpeedMultiplier", 1.5, 0.5, 5.0, 0.1);
    private final NumberValue<Double> friction = new NumberValue<>("Friction", 0.95, 0.80, 0.99, 0.01);
    private final NumberValue<Integer> blinkDelay = new NumberValue<>("BlinkDelay", 350, 100, 1000, 10, () -> mode.is(Mode.Blink));
    private final BoolValue autoEnable = new BoolValue("AutoEnable", true);
    private final NumberValue<Double> heightBoost = new NumberValue<>("HeightBoost", 1.0, 0.0, 5.0, 0.1);

    private final Deque<Packet<?>> queuedPackets = new ArrayDeque<>();
    private boolean blinking = false;
    private long blinkStartTime = 0;
    private boolean hasExplosionImpulse = false;
    private double currentSpeedMultiplier = 1.0;
    private Vec3d explosionVelocity = Vec3d.ZERO;

    public LongJump() {
        super("LongJump", Category.Movement);
        addValues(mode, speedMultiplier, friction, blinkDelay, autoEnable, heightBoost);
    }

    private void addValues(dev.mzc.client.values.Value<?>... values) {
        this.values.addAll(java.util.Arrays.asList(values));
    }

    @Override
    protected void onEnable() {
        queuedPackets.clear();
        blinking = false;
        hasExplosionImpulse = false;
        currentSpeedMultiplier = 1.0;
        explosionVelocity = Vec3d.ZERO;
        blinkStartTime = 0;
    }

    @Override
    protected void onDisable() {
        flushPackets();
        queuedPackets.clear();
        blinking = false;
        hasExplosionImpulse = false;
        currentSpeedMultiplier = 1.0;
    }

    private void flushPackets() {
        if (queuedPackets.isEmpty()) return;
        
        while (!queuedPackets.isEmpty()) {
            Packet<?> packet = queuedPackets.pollFirst();
            if (packet != null && mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(packet);
            }
        }
    }

    @EventHandler
    public void onPacket(PacketEvent event) {
        if (nullCheck()) return;

        // Intercept explosion packet
        if (event.getType() == EventType.RECEIVE && event.getPacket() instanceof ExplosionS2CPacket) {
            IExplosionS2CPacket accessor = (IExplosionS2CPacket) event.getPacket();
            
            // Check if explosion affects player and validate it's from damage
            if (accessor.getPlayerKnockback().isPresent() && mc.player.hurtTime > 0) {
                Vec3d knockback = accessor.getPlayerKnockback().get();
                
                // Verify there's actual impulse from explosion
                if (knockback.lengthSquared() > 0.001) {
                    hasExplosionImpulse = true;
                    explosionVelocity = knockback;
                    
                    // Add height boost to Y component
                    Vec3d boostedVelocity = new Vec3d(
                        knockback.x,
                        knockback.y + heightBoost.get(),
                        knockback.z
                    );
                    explosionVelocity = boostedVelocity;
                    
                    // Start blink mode if enabled
                    if (mode.is(Mode.Blink)) {
                        startBlink();
                    }
                }
            }
            return;
        }

        // Queue movement packets during blink
        if (blinking && event.getType() == EventType.SEND) {
            Packet<?> packet = event.getPacket();
            
            if (packet instanceof PlayerMoveC2SPacket) {
                queuedPackets.addLast(packet);
                event.cancel();
            }
        }
    }

    private void startBlink() {
        blinking = true;
        blinkStartTime = System.currentTimeMillis();
        queuedPackets.clear();
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        // Check if we should auto-enable on explosion impulse
        if (autoEnable.get() && mc.player.hurtTime > 0 && !isEnabled()) {
            // This would require external trigger, handled by packet event
        }

        // Handle blink timing
        if (blinking && mode.is(Mode.Blink)) {
            long elapsed = System.currentTimeMillis() - blinkStartTime;
            
            if (elapsed >= blinkDelay.get()) {
                // Flush all queued packets
                flushPackets();
                blinking = false;
                
                // Apply speed multiplier after release
                currentSpeedMultiplier = speedMultiplier.get();
            }
        }

        // Apply velocity modification after explosion
        if (hasExplosionImpulse && mc.player.hurtTime > 0) {
            // Allow player to fly up on Y axis from explosion
            Vec3d currentVel = mc.player.getVelocity();
            
            // Maintain vertical velocity from explosion
            if (explosionVelocity.y > 0) {
                mc.player.setVelocity(
                    currentVel.x,
                    explosionVelocity.y,
                    currentVel.z
                );
            }
        }

        // Apply speed multiplier and friction after packet release
        if (currentSpeedMultiplier > 1.0 && MovementUtil.isMoving()) {
            Vec3d velocity = mc.player.getVelocity();
            
            // Apply horizontal speed multiplier
            double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
            if (horizontalSpeed > 0.001) {
                double newSpeed = horizontalSpeed * currentSpeedMultiplier;
                
                // Normalize and apply new speed
                double ratio = newSpeed / horizontalSpeed;
                mc.player.setVelocity(
                    velocity.x * ratio,
                    velocity.y,
                    velocity.z * ratio
                );
            }
            
            // Apply friction to gradually reduce speed
            currentSpeedMultiplier *= friction.get();
            
            // Reset multiplier when close to 1.0
            if (currentSpeedMultiplier <= 1.01) {
                currentSpeedMultiplier = 1.0;
            }
        }

        // Reset explosion impulse state when hurtTime expires
        if (hasExplosionImpulse && mc.player.hurtTime == 0) {
            hasExplosionImpulse = false;
            explosionVelocity = Vec3d.ZERO;
        }
    }

    @Override
    public String getSuffix() {
        if (mode.is(Mode.Blink) && blinking) {
            long elapsed = System.currentTimeMillis() - blinkStartTime;
            return "Blinking " + elapsed + "ms";
        }
        if (currentSpeedMultiplier > 1.0) {
            return String.format("Speed x%.2f", currentSpeedMultiplier);
        }
        return mode.get().name();
    }
}
