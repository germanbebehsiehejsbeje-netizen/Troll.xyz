package dev.mzc.client.module.impl.movement;

import dev.mzc.client.events.player.PlayerTickEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.player.MovementUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.BoatEntity;

public class BoatFly extends Module {

    private final NumberValue<Double> speed = new NumberValue<>("Speed", 2.0, 0.1, 10.0, 0.1);
    private final NumberValue<Double> verticalSpeed = new NumberValue<>("VerticalSpeed", 1.0, 0.1, 5.0, 0.1);
    private final BoolValue noGravity = new BoolValue("NoGravity", true);
    private final BoolValue lockYaw = new BoolValue("LockYaw", true);

    public BoatFly() {
        super("BoatFly", Category.Movement);
        this.setType(ModuleType.Hack);
    }

    @Override
    public void onDisable() {
        if (mc.player != null && mc.player.getVehicle() != null) {
            mc.player.getVehicle().setNoGravity(false);
        }
    }

    @EventHandler
    public void onTick(PlayerTickEvent event) {
        if (nullCheck()) return;
        
        if (mc.player.getVehicle() == null || !(mc.player.getVehicle() instanceof BoatEntity)) return;

        Entity vehicle = mc.player.getVehicle();
        
        if (lockYaw.get()) {
            vehicle.setYaw(mc.player.getYaw());
            ((BoatEntity) vehicle).setInputs(false, false, false, false);
        }

        vehicle.setNoGravity(noGravity.get());

        // Horizontal Movement
        double hSpeed = speed.get();
        double vX = 0;
        double vZ = 0;

        if (MovementUtil.isMoving()) {
            double yaw = MovementUtil.getDirection();
            vX = -Math.sin(yaw) * hSpeed;
            vZ = Math.cos(yaw) * hSpeed;
        }

        // Vertical Movement
        double vY;
        if (noGravity.get()) {
            vY = 0; // Hover
        } else {
            vY = vehicle.getVelocity().y; // Keep gravity if not disabled
        }

        if (mc.options.jumpKey.isPressed()) {
            vY = verticalSpeed.get();
        } else if (mc.options.sneakKey.isPressed()) {
            vY = -verticalSpeed.get();
        }

        vehicle.setVelocity(vX, vY, vZ);
    }
}
