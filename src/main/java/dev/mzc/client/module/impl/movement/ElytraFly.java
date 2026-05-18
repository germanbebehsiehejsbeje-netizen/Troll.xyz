package dev.mzc.client.module.impl.movement;

import dev.mzc.client.events.EventType;
import dev.mzc.client.events.packet.PacketEvent;
import dev.mzc.client.events.player.PlayerTickEvent;
import dev.mzc.client.events.player.TravelEvent;
import dev.mzc.client.mixin.accessor.IPlayerMoveC2SPacket;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import dev.mzc.client.utils.player.MovementUtil;
import dev.mzc.client.values.impl.BoolValue;

public class ElytraFly extends Module {

    private final EnumValue<Mode> mode = new EnumValue<>("Mode", Mode.Control);
    private final NumberValue<Double> speed = new NumberValue<>("Speed", 1.0, 0.1, 5.0, 0.1);
    private final NumberValue<Double> downSpeed = new NumberValue<>("DownSpeed", 0.0, 0.0, 5.0, 0.1);
    private final BoolValue pitchSpoof = new BoolValue("PitchSpoof", true);
    private final BoolValue autoStop = new BoolValue("AutoStop", true);

    public ElytraFly() {
        super("ElytraFly", Category.Movement);
        this.setType(ModuleType.Hack);
    }

    @Override
    public void onEnable() {
        if (mc.player != null && !mc.player.isGliding() && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
            if (mc.world != null && !mc.player.isOnGround()) {
                mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
        }
    }

    @EventHandler
    public void onTick(PlayerTickEvent event) {
        if (nullCheck()) return;

        if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) return;

        if (mode.get() == Mode.Boost) {
            if (mc.player.isOnGround()) {
                mc.player.jump();
            } else {
                if (!mc.player.isGliding() && mc.player.fallDistance > 0) {
                    mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                }
                
                if (mc.player.isGliding()) {
                    MovementUtil.setMotionY(-downSpeed.get() / 20.0);
                    MovementUtil.strafe(speed.get());
                }
            }
        } else if (mode.get() == Mode.Control) {
            if (!mc.player.isGliding() && !mc.player.isOnGround() && mc.player.fallDistance > 0) {
                mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
        } else if (mode.get() == Mode.HwElytraFly) {
            handleHwElytraFly();
        }
    }

    @EventHandler
    public void onTravel(TravelEvent event) {
        if (nullCheck() || mode.get() != Mode.Control) return;
        if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) return;
        if (!mc.player.isGliding()) return;
        if (event.getType() != EventType.PRE) return;

        Vec3d lookVec = getRotationVec(1.0f);
        double lookDist = Math.sqrt(lookVec.x * lookVec.x + lookVec.z * lookVec.z);
        double motionDist = Math.sqrt(mc.player.getVelocity().x * mc.player.getVelocity().x + mc.player.getVelocity().z * mc.player.getVelocity().z);

        if (mc.options.sneakKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, -downSpeed.get(), mc.player.getVelocity().z);
        } else if (!mc.options.jumpKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, -0.00000000003D * 0, mc.player.getVelocity().z);
        }

        if (mc.options.jumpKey.isPressed()) {
            if (motionDist > 0.1) {
                double rawUpSpeed = motionDist * 0.01325D;
                mc.player.setVelocity(mc.player.getVelocity().x, mc.player.getVelocity().y + rawUpSpeed * 3.2D, mc.player.getVelocity().z);
                mc.player.setVelocity(mc.player.getVelocity().x - lookVec.x * rawUpSpeed / lookDist, mc.player.getVelocity().y, mc.player.getVelocity().z - lookVec.z * rawUpSpeed / lookDist);
            } else {
                double[] dir = directionSpeedKey(speed.get());
                mc.player.setVelocity(dir[0], mc.player.getVelocity().y, dir[1]);
            }
        }

        if (lookDist > 0.0D) {
            mc.player.setVelocity(mc.player.getVelocity().x + (lookVec.x / lookDist * motionDist - mc.player.getVelocity().x) * 0.1D, mc.player.getVelocity().y, mc.player.getVelocity().z + (lookVec.z / lookDist * motionDist - mc.player.getVelocity().z) * 0.1D);
        }

        if (!mc.options.jumpKey.isPressed()) {
            double[] dir = directionSpeedKey(speed.get());
            mc.player.setVelocity(dir[0], mc.player.getVelocity().y, dir[1]);
        }

        mc.player.setVelocity(mc.player.getVelocity().x * 0.9800000190734863D, mc.player.getVelocity().y * 0.9900000095367432D, mc.player.getVelocity().z * 0.9900000095367432D);

        event.cancel();
        mc.player.move(MovementType.SELF, mc.player.getVelocity());
    }

    private Vec3d getRotationVector(float pitch, float yaw) {
        float f = pitch * 0.017453292F;
        float g = -yaw * 0.017453292F;
        float h = MathHelper.cos(g);
        float i = MathHelper.sin(g);
        float j = MathHelper.cos(f);
        float k = MathHelper.sin(f);
        return new Vec3d(i * j, -k, h * j);
    }

    private Vec3d getRotationVec(float tickDelta) {
        return this.getRotationVector(0, mc.player.getYaw(tickDelta));
    }

    private double[] directionSpeedKey(double speed) {
        float forward = (mc.options.forwardKey.isPressed() ? 1 : 0) + (mc.options.backKey.isPressed() ? -1 : 0);
        float side = (mc.options.leftKey.isPressed() ? 1 : 0) + (mc.options.rightKey.isPressed() ? -1 : 0);
        float yaw = mc.player.lastYaw + (mc.player.getYaw() - mc.player.lastYaw) * 1.0f;
        if (forward != 0.0f) {
            if (side > 0.0f) {
                yaw += ((forward > 0.0f) ? -45 : 45);
            } else if (side < 0.0f) {
                yaw += ((forward > 0.0f) ? 45 : -45);
            }
            side = 0.0f;
            if (forward > 0.0f) {
                forward = 1.0f;
            } else if (forward < 0.0f) {
                forward = -1.0f;
            }
        }
        final double sin = Math.sin(Math.toRadians(yaw + 90.0f));
        final double cos = Math.cos(Math.toRadians(yaw + 90.0f));
        final double posX = forward * speed * cos + side * speed * sin;
        final double posZ = forward * speed * sin - side * speed * cos;
        return new double[]{posX, posZ};
    }

    private void handleHwElytraFly() {
        // HwElytraFly mode for HolyWorld server
        // Automatically takes off and flies upward using elytra
        
        if (mc.player.isOnGround()) {
            // On ground - jump and prepare for takeoff
            mc.player.jump();
            mc.player.setVelocity(mc.player.getVelocity().x, 0.42, mc.player.getVelocity().z);
            mc.player.setPitch(-90.0f);
        } else if (!mc.player.isGliding()) {
            // In air but not gliding - start elytra flight
            if (mc.player.fallDistance > 0 || mc.player.getVelocity().y > 0) {
                mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                mc.player.setVelocity(mc.player.getVelocity().x, 0.5, mc.player.getVelocity().z);
                mc.player.setPitch(-90.0f);
            }
        }
        
        // Always reset pitch to 0 for stable flight
        mc.player.setPitch(0.0f);
        
        // Continuous upward motion when airborne
        if (!mc.player.isOnGround() && mc.player.isGliding()) {
            mc.player.setVelocity(mc.player.getVelocity().x, 0.36, mc.player.getVelocity().z);
            
            // Apply forward strafe if speed is set
            if (speed.get() > 0) {
                MovementUtil.strafe(speed.get());
            }
        }
    }

    public enum Mode {
        Control,
        Boost,
        HwElytraFly
    }

    @EventHandler
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.SEND) {
            if (event.getPacket() instanceof PlayerMoveC2SPacket) {
                PlayerMoveC2SPacket packet = (PlayerMoveC2SPacket) event.getPacket();
                if (mode.get() == Mode.Control && mc.player != null && mc.player.isGliding() && mc.options.jumpKey.isPressed() && pitchSpoof.get()) {
                    ((IPlayerMoveC2SPacket) packet).setPitch(-40.0f);
                }
            }
        }
    }
}
