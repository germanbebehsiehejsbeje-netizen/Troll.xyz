package dev.mzc.client.module.impl.movement;

import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.manager.Managers;
import dev.mzc.client.manager.impl.RotationManager;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.rotation.MovementFix;
import dev.mzc.client.utils.vector.Rotation;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;

public class AntiAim extends Module {

    public enum Mode {
        Spin, Jitter, Fake, Custom
    }

    private final EnumValue<Mode> mode = new EnumValue<>("Mode", Mode.Spin);
    private final NumberValue<Double> spinSpeed = new NumberValue<>("Spin Speed", 10.0, 1.0, 50.0, 0.5, () -> mode.get() == Mode.Spin || mode.get() == Mode.Fake);
    private final NumberValue<Double> jitterRange = new NumberValue<>("Jitter Range", 30.0, 5.0, 90.0, 1.0, () -> mode.get() == Mode.Jitter);
    private final NumberValue<Double> customYaw = new NumberValue<>("Custom Yaw", 0.0, -180.0, 180.0, 1.0, () -> mode.get() == Mode.Custom);
    private final NumberValue<Double> customPitch = new NumberValue<>("Custom Pitch", 90.0, -90.0, 90.0, 1.0, () -> mode.get() == Mode.Custom);
    
    private float currentSpinAngle = 0;
    private boolean jitterDirection = true;

    public AntiAim() {
        super("AntiAim", Category.Movement);
        this.setType(ModuleType.Hack);
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;
        
        Rotation targetRotation = null;
        
        switch (mode.get()) {
            case Spin:
            case Fake:
                currentSpinAngle += spinSpeed.get().floatValue();
                if (currentSpinAngle >= 360) {
                    currentSpinAngle -= 360;
                }
                
                // Create fake rotation with spin yaw but keep current pitch
                targetRotation = new Rotation(currentSpinAngle, mc.player.getPitch());
                break;
                
            case Jitter:
                float jitterYaw;
                if (jitterDirection) {
                    jitterYaw = mc.player.getYaw() + jitterRange.get().floatValue();
                } else {
                    jitterYaw = mc.player.getYaw() - jitterRange.get().floatValue();
                }
                jitterDirection = !jitterDirection;
                
                targetRotation = new Rotation(jitterYaw, mc.player.getPitch());
                break;
                
            case Custom:
                targetRotation = new Rotation(customYaw.get().floatValue(), customPitch.get().floatValue());
                break;
        }
        
        // Apply silent rotation using RotationManager (server sees this, camera doesn't move)
        if (targetRotation != null) {
            Managers.ROTATION.setRotations(targetRotation, 100.0, MovementFix.GRIM, RotationManager.Priority.Highest);
        }
    }

    @Override
    protected void onEnable() {
        currentSpinAngle = mc.player.getYaw();
    }
}
