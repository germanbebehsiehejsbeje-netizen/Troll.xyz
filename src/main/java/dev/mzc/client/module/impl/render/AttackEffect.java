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
import net.minecraft.registry.entry.RegistryEntry; // Import this
import dev.mzc.client.events.packet.PacketEvent;
import dev.mzc.client.events.EventType;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import dev.mzc.client.mixin.accessor.IPlayerInteractEntityC2SPacket;

public class AttackEffect extends Module {
    public enum ParticleMode {
        // ... (keep as is)
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
        DAMAGE(ParticleTypes.DAMAGE_INDICATOR);
        private final SimpleParticleType effect;

        ParticleMode(SimpleParticleType effect) {
            this.effect = effect;
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

    public AttackEffect() {
        super("AttackEffect", Category.Render);
        this.setType(ModuleType.All);
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
