package dev.mzc.client.module.impl.render;

import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.registry.entry.RegistryEntry;
import dev.mzc.client.events.packet.PacketEvent;
import dev.mzc.client.events.EventType;
import dev.mzc.client.events.entity.AttackEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import dev.mzc.client.mixin.accessor.IPlayerInteractEntityC2SPacket;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import dev.mzc.client.particle.CustomHitParticle;

import java.util.ArrayList;
import java.util.List;
import java.awt.*;

public class AttackEffect extends Module {
    public enum ParticleMode {
        HEART(ParticleTypes.HEART),
        FLAME(ParticleTypes.FLAME),
        VILLAGER_HAPPY(ParticleTypes.HAPPY_VILLAGER),
        NOTE(ParticleTypes.NOTE),
        CLOUD(ParticleTypes.CLOUD),
        SMOKE(ParticleTypes.SMOKE),
        SOUL_FLAME(ParticleTypes.SOUL_FIRE_FLAME),
        LAVA(ParticleTypes.LAVA),
        ENCHANT(ParticleTypes.ENCHANT),
        WITCH(ParticleTypes.WITCH),
        DAMAGE(ParticleTypes.DAMAGE_INDICATOR),
        CUSTOM(); // Custom particle with physics
        
        private final SimpleParticleType effect;

        ParticleMode(SimpleParticleType effect) {
            this.effect = effect;
        }
        
        ParticleMode() {
            this.effect = null;
        }

        public ParticleEffect getEffect() {
            return effect;
        }
    }
    
    public enum SoundMode {
        NONE(null),
        HIT(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG),
        EXPLOSION(SoundEvents.ENTITY_GENERIC_EXPLODE),
        ORB(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP),
        ANVIL(SoundEvents.BLOCK_ANVIL_LAND),
        THUNDER(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER),
        BELL(SoundEvents.BLOCK_BELL_USE),
        BLAZE(SoundEvents.ENTITY_BLAZE_HURT),
        GLASS(SoundEvents.BLOCK_GLASS_BREAK),
        TOTEM(SoundEvents.ITEM_TOTEM_USE),
        LEVEL_UP(SoundEvents.ENTITY_PLAYER_LEVELUP);
        private final SoundEvent sound;

        SoundMode(Object soundObj) {
            if (soundObj instanceof RegistryEntry) {
                this.sound = (SoundEvent) ((RegistryEntry<?>) soundObj).value();
            } else if (soundObj instanceof SoundEvent) {
                this.sound = (SoundEvent) soundObj;
            } else {
                this.sound = null;
            }
        }

        public SoundEvent getSound() {
            return sound;
        }
    }

    public final BoolValue always = new BoolValue("Always", false);
    public final EnumValue<ParticleMode> particle = new EnumValue<>("Particle", ParticleMode.HEART);
    public final EnumValue<SoundMode> sound = new EnumValue<>("Sound", SoundMode.NONE);
    public final NumberValue<Double> velocityMultiplier = new NumberValue<>("Velocity Multiplier", 0.6, 0.1, 2.0, 0.1);
    public final NumberValue<Double> lifeMultiplier = new NumberValue<>("Life Multiplier", 0.7, 0.1, 2.0, 0.1);
    public final NumberValue<Double> volume = new NumberValue<>("Volume", 1.0, 0.1, 2.0, 0.1, () -> !sound.is(SoundMode.NONE));
    public final NumberValue<Double> pitch = new NumberValue<>("Pitch", 1.0, 0.5, 2.0, 0.1, () -> !sound.is(SoundMode.NONE));
    public final BoolValue checkCooldown = new BoolValue("Check Cooldown", true);
    public final NumberValue<Integer> particleCount = new NumberValue<>("Particle Count", 8, 1, 20, 1);
    public final BoolValue enablePhysics = new BoolValue("Enable Physics", true);
    
    private final List<CustomHitParticle> customParticles = new ArrayList<>();

    public AttackEffect() {
        super("AttackEffect", Category.Render);
        this.setType(ModuleType.All);
    }

    @EventHandler
    public void onAttack(AttackEvent event) {
        if (!isEnabled()) return;
        if (mc.player == null || mc.world == null) return;
        
        if (checkCooldown.get() && mc.player.getAttackCooldownProgress(0.5f) < 0.9f) {
            return;
        }

        Entity target = event.getTargetEntity();
        if (!(target instanceof LivingEntity)) return;

        // Get hit position from event or calculate from entity
        Vec3d hitPos = event.getHitPos();
        if (hitPos == null) {
            hitPos = target.getBoundingBox().getCenter();
        }

        // Spawn particles at exact hit location
        int count = particleCount.get();
        for (int i = 0; i < count; i++) {
            spawnParticleAtLocation(hitPos);
        }
    }

    private void spawnParticleAtLocation(Vec3d hitPos) {
        if (particle.is(ParticleMode.CUSTOM)) {
            // Spawn custom particle with physics
            if (enablePhysics.get()) {
                spawnCustomParticle(hitPos);
            } else {
                spawnVanillaParticle(hitPos);
            }
        } else {
            // Spawn vanilla particle
            spawnVanillaParticle(hitPos);
        }
    }

    private void spawnCustomParticle(Vec3d hitPos) {
        // Generate random velocity for burst effect
        double vx = (Math.random() * 2.0 - 1.0) * 0.4 * velocityMultiplier.get();
        double vy = Math.random() * 0.4 * velocityMultiplier.get();
        double vz = (Math.random() * 2.0 - 1.0) * 0.4 * velocityMultiplier.get();

        // Create custom particle with physics
        CustomHitParticle customParticle = new CustomHitParticle(
            (ClientWorld) mc.world,
            hitPos.x, hitPos.y, hitPos.z,
            vx, vy, vz
        );

        // Apply life multiplier
        int newMaxAge = (int) (30 * lifeMultiplier.get());
        customParticle.maxAge = Math.max(1, newMaxAge);

        // Add to particle list
        customParticles.add(customParticle);
    }

    private void spawnVanillaParticle(Vec3d hitPos) {
        // Generate random velocity for burst effect
        double vx = (Math.random() * 2.0 - 1.0) * 0.4 * velocityMultiplier.get();
        double vy = Math.random() * 0.4 * velocityMultiplier.get();
        double vz = (Math.random() * 2.0 - 1.0) * 0.4 * velocityMultiplier.get();

        // Spawn vanilla particle
        mc.particleManager.addParticle(
            particle.get().getEffect(),
            hitPos.x, hitPos.y, hitPos.z,
            vx, vy, vz
        );
    }

    @EventHandler
    public void onTick(dev.mzc.client.events.client.TickEvent event) {
        if (!isEnabled()) return;
        
        // Update custom particles
        customParticles.removeIf(p -> p.dead);
        customParticles.forEach(CustomHitParticle::tick);
    }

    @EventHandler
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.SEND && event.getPacket() instanceof PlayerInteractEntityC2SPacket packet) {
            packet.handle(new PlayerInteractEntityC2SPacket.Handler() {
                @Override
                public void interact(net.minecraft.util.Hand hand) {}

                @Override
                public void interactAt(net.minecraft.util.Hand hand, net.minecraft.util.math.Vec3d pos) {}

                @Override
                public void attack() {
                    if (sound.is(SoundMode.NONE)) return;
                    
                    if (checkCooldown.get() && mc.player.getAttackCooldownProgress(0.5f) < 0.9f) {
                        return;
                    }

                    IPlayerInteractEntityC2SPacket accessor = (IPlayerInteractEntityC2SPacket) packet;
                    Entity entity = mc.world.getEntityById(accessor.getEntityId());
                    
                    if (entity instanceof LivingEntity) {
                        // Use mc.player.playSound to ensure local player hears it
                        mc.player.playSound(sound.get().getSound(), 
                            volume.get().floatValue(), 
                            pitch.get().floatValue());
                    }
                }
            });
        }
    }
}
