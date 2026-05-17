package dev.mzc.client.module.impl.combat.elytratarget;

import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import meteordevelopment.orbit.EventHandler;

public class ElytraTargetModule extends Module {
    public final ElytraRotationProcessor elytraRotationProcessor = new ElytraRotationProcessor(this);

    public ElytraTargetModule() {
        super("ElytraTarget", Category.Combat);
        this.setType(ModuleType.Safe);
        
        // Add all settings from the rotation processor
        values.add(elytraRotationProcessor.getCustomRotations());
        values.add(elytraRotationProcessor.getSharpRotations());
        values.add(elytraRotationProcessor.getAutoDistance());
        values.add(elytraRotationProcessor.getRotateAt());
        
        // Add prediction settings
        values.add(elytraRotationProcessor.getPredict().getPrediction());
        values.add(elytraRotationProcessor.getPredict().getMode());
        values.add(elytraRotationProcessor.getPredict().getGlidingOnly());
        values.add(elytraRotationProcessor.getPredict().getMultiplier());
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;
        elytraRotationProcessor.processRotation();
    }
}
