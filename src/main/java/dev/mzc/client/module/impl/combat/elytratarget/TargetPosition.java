package dev.mzc.client.module.impl.combat.elytratarget;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import java.util.function.Function;

public enum TargetPosition {
    EYES("Eyes", target -> target.getEyePos()),
    CENTER("Center", target -> target.getEyePos().add(0.0, -target.getEyeHeight(target.getPose()) / 2.0, 0.0));

    private final String name;
    private final Function<LivingEntity, Vec3d> position;

    TargetPosition(String name, Function<LivingEntity, Vec3d> position) {
        this.name = name;
        this.position = position;
    }

    public Vec3d getPosition(LivingEntity target) {
        return position.apply(target);
    }

    public String getName() {
        return name;
    }
}
