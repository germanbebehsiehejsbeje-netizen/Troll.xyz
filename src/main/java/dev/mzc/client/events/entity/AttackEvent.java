package dev.mzc.client.events.entity;

import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

public class AttackEvent {
    private final Entity targetEntity;
    private final Vec3d hitPos;

    public AttackEvent(Entity targetEntity) {
        this.targetEntity = targetEntity;
        this.hitPos = null;
    }

    public AttackEvent(Entity targetEntity, Vec3d hitPos) {
        this.targetEntity = targetEntity;
        this.hitPos = hitPos;
    }

    public Entity getTargetEntity() {
        return targetEntity;
    }

    public Vec3d getHitPos() {
        return hitPos;
    }
}
