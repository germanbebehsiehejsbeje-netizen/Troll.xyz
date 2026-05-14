package dev.mzc.client.module.impl.render;

import dev.mzc.client.auth.UserRole;
import dev.mzc.client.events.packet.PacketEvent;
import dev.mzc.client.events.EventType;
import dev.mzc.client.events.render.Render3DEvent;
import dev.mzc.client.mixin.accessor.IPlayerInteractEntityC2SPacket;
import dev.mzc.client.utils.render.Render3DUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.World;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.awt.Color;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import meteordevelopment.orbit.EventHandler;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;

public class KillEffect extends Module {
    public enum Mode {
        Lightning,
        Fire,
        Totem,
        Heart,
        Cloud,
        Experience,
        Ghost
    }

    private final EnumValue<Mode> mode = new EnumValue<>("Mode", Mode.Lightning);
    private final NumberValue<Integer> orbCount = new NumberValue<>("OrbCount", 5, 1, 30, 1, () -> mode.get() == Mode.Experience);
    private final BoolValue totemPop = new BoolValue("TotemPop", true);
    private final BoolValue sound = new BoolValue("Sound", true);
    private final BoolValue players = new BoolValue("Players", true);
    private final BoolValue mobs = new BoolValue("Mobs", true);
    private final BoolValue animals = new BoolValue("Animals", false);

    private Entity lastAttackedEntity;
    private long lastAttackTime;
    private final List<ActiveGhost> activeGhosts = new ArrayList<>();

    public KillEffect() {
        super("KillEffect", Category.Render);
        this.setRequiredRole(UserRole.USER);
        this.setType(ModuleType.Safe);
    }

    @Override
    protected void onDisable() {
        activeGhosts.clear();
    }

    @EventHandler
    public void onPacket(PacketEvent event) {
        if (mc.world == null || mc.player == null) return;

        if (event.getType() == EventType.SEND && event.getPacket() instanceof PlayerInteractEntityC2SPacket packet) {
            int id = ((IPlayerInteractEntityC2SPacket) packet).getEntityId();
            Entity entity = mc.world.getEntityById(id);
            if (entity != null) {
                lastAttackedEntity = entity;
                lastAttackTime = System.currentTimeMillis();
            }
        }

        if (event.getType() == EventType.RECEIVE && event.getPacket() instanceof EntityStatusS2CPacket packet) {
            if (packet.getStatus() == 3 || (totemPop.get() && packet.getStatus() == 35)) {
                Entity entity = packet.getEntity(mc.world);
                if (entity == null) return;

                if (shouldTrigger(entity)) {
                    triggerEffect(entity);
                }
            }
        }
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (activeGhosts.isEmpty()) return;

        long now = System.currentTimeMillis();
        activeGhosts.removeIf(ghost -> now - ghost.startTime > 2000);

        for (ActiveGhost ghost : activeGhosts) {
            float progress = (now - ghost.startTime) / 2000f;
            float alpha = Math.max(0, 1.0f - progress);

            // Поднимается выше и плавнее
            double rise = Math.pow(progress, 0.7) * 4.0;
            Vec3d currentPos = ghost.pos.add(0, rise, 0);

            // Вместо модели рисуем эффект "души" партиклами, так как модель в 1.21.4 рендерить сложно
            if (now % 2 == 0) {
                 mc.world.addParticleClient(ParticleTypes.SOUL_FIRE_FLAME, currentPos.x, currentPos.y + 1, currentPos.z, 0, 0.05, 0);
            }

            drawGhostLines(event.getMatrices(), currentPos, alpha, progress);
        }
    }

    private void drawGhostLines(MatrixStack matrices, Vec3d currentPos, float alpha, float progress) {
        // Спиралевидные линии
        int lines = 4;
        Color color = new Color(100, 200, 255, (int) (alpha * 255));
        double radius = 0.5 * (1.0 - progress);
        double rotation = progress * Math.PI * 4;

        for (int i = 0; i < lines; i++) {
            double angle = rotation + (i * (Math.PI * 2 / lines));
            double nextAngle = angle + 0.2;
            
            Vec3d start = currentPos.add(
                Math.cos(angle) * radius, 
                -0.5, 
                Math.sin(angle) * radius
            );
            
            Vec3d end = currentPos.add(
                Math.cos(nextAngle) * radius * 0.8, 
                -1.5, 
                Math.sin(nextAngle) * radius * 0.8
            );
            
            Render3DUtil.drawLine(matrices, start, end, color, 2.0f);
            
            // Дополнительная вертикальная линия к земле
            Vec3d ground = new Vec3d(start.x, start.y - 1.0, start.z);
            Render3DUtil.drawLine(matrices, start, ground, new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(color.getAlpha() * 0.5)), 1.0f);
        }
    }

    private boolean shouldTrigger(Entity entity) {
        if (entity == mc.player) return false;

        boolean isTarget = (entity == lastAttackedEntity || (lastAttackedEntity != null && entity.getId() == lastAttackedEntity.getId()));

        if (!isTarget && lastAttackedEntity instanceof EndCrystalEntity && entity instanceof LivingEntity) {
            if (entity.distanceTo(lastAttackedEntity) <= 12.0) {
                isTarget = true;
            }
        }

        if (isTarget && System.currentTimeMillis() - lastAttackTime < 5000) {
            if (entity instanceof PlayerEntity) {
                return players.get();
            } else if (entity instanceof Monster) {
                return mobs.get();
            } else {
                return animals.get();
            }
        }
        
        return false;
    }

    private void triggerEffect(Entity entity) {
        if (mode.is(Mode.Lightning)) {
            LightningEntity lightningEntity = new LightningEntity(EntityType.LIGHTNING_BOLT, mc.world);
            lightningEntity.setPosition(entity.getX(), entity.getY(), entity.getZ());
            mc.world.addEntity(lightningEntity);
        } else if (mode.is(Mode.Fire)) {
            for (int i = 0; i < 20; i++) {
                mc.world.addParticleClient(ParticleTypes.FLAME, entity.getX(), entity.getY() + entity.getHeight() / 2, entity.getZ(), (Math.random() - 0.5) * 0.5, (Math.random() - 0.5) * 0.5, (Math.random() - 0.5) * 0.5);
            }
        } else if (mode.is(Mode.Totem)) {
            mc.particleManager.addEmitter(entity, ParticleTypes.TOTEM_OF_UNDYING, 30);
        } else if (mode.is(Mode.Heart)) {
            float width = entity.getWidth();
            float height = entity.getHeight();
            for (int i = 0; i < 10; i++) {
                mc.world.addParticleClient(ParticleTypes.HEART,
                    entity.getX() + (Math.random() - 0.5) * width, 
                    entity.getY() + Math.random() * height, 
                    entity.getZ() + (Math.random() - 0.5) * width, 
                    (Math.random() - 0.5) * 0.5, Math.random() * 0.5, (Math.random() - 0.5) * 0.5);
            }
        } else if (mode.is(Mode.Cloud)) {
            for (int i = 0; i < 20; i++) {
                mc.world.addParticleClient(ParticleTypes.CLOUD, entity.getX(), entity.getY() + entity.getHeight() / 2, entity.getZ(), (Math.random() - 0.5) * 0.2, (Math.random() - 0.5) * 0.2, (Math.random() - 0.5) * 0.2);
            }
        } else if (mode.is(Mode.Experience)) {
            int count = orbCount.get();
            for (int i = 0; i < count; i++) {
                double offsetX = (Math.random() - 0.5) * 0.5;
                double offsetZ = (Math.random() - 0.5) * 0.5;
                ExperienceOrbEntity orb = new ClientExperienceOrbEntity(mc.world, entity.getX() + offsetX, entity.getY(), entity.getZ() + offsetZ, 20);
                orb.setVelocity((Math.random() - 0.5) * 0.5, Math.random() * 0.5, (Math.random() - 0.5) * 0.5);
                mc.world.addEntity(orb);
            }
            for (int i = 0; i < 20; i++) {
                mc.world.addParticleClient(ParticleTypes.HAPPY_VILLAGER,
                    entity.getX() + (Math.random() - 0.5) * 1.0, 
                    entity.getY() + Math.random() * 1.5, 
                    entity.getZ() + (Math.random() - 0.5) * 1.0, 
                    (Math.random() - 0.5) * 0.1, 0.1, (Math.random() - 0.5) * 0.1);
            }
        } else if (mode.is(Mode.Ghost)) {
            activeGhosts.add(new ActiveGhost(entity, new Vec3d(entity.getX(), entity.getY(), entity.getZ()), entity.getYaw(), entity.getPitch()));
        }

        if (sound.get()) {
            if (mode.is(Mode.Lightning)) {
                mc.world.playSound(mc.player, entity.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 1.0f, 1.0f);
            } else if (mode.is(Mode.Fire)) {
                mc.world.playSound(mc.player, entity.getBlockPos(), SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.WEATHER, 1.0f, 1.0f);
            } else if (mode.is(Mode.Totem)) {
                mc.world.playSound(mc.player, entity.getBlockPos(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.WEATHER, 1.0f, 1.0f);
            }
        }
    }

    private static class ActiveGhost {
        Entity entity;
        Vec3d pos;
        long startTime;
        float yaw, pitch;

        ActiveGhost(Entity entity, Vec3d pos, float yaw, float pitch) {
            this.entity = entity;
            this.pos = pos;
            this.startTime = System.currentTimeMillis();
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static class ClientExperienceOrbEntity extends ExperienceOrbEntity {
        public ClientExperienceOrbEntity(World world, double x, double y, double z, int amount) {
            super(world, x, y, z, amount);
            try {
                Field pickupDelayField;
                try {
                    pickupDelayField = ExperienceOrbEntity.class.getDeclaredField("pickupDelay");
                } catch (NoSuchFieldException e) {
                    pickupDelayField = ExperienceOrbEntity.class.getDeclaredField("field_7222");
                }
                pickupDelayField.setAccessible(true);
                pickupDelayField.setInt(this, 0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void tick() {
            super.tick();
            if (this.getEntityWorld().isClient()) {
                PlayerEntity player = MinecraftClient.getInstance().player;
                if (player != null && player.getBoundingBox().intersects(this.getBoundingBox())) {
                    this.onPlayerCollision(player);
                }
            }
        }

        @Override
        public void onPlayerCollision(PlayerEntity player) {
            if (this.getEntityWorld().isClient()) {
                this.getEntityWorld().playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.1F, (this.random.nextFloat() - this.random.nextFloat()) * 0.35F + 0.9F);
                this.discard();
            } else {
                super.onPlayerCollision(player);
            }
        }
    }
}
