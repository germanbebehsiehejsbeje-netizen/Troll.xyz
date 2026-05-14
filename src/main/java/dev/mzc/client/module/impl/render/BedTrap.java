package dev.mzc.client.module.impl.render;

import dev.mzc.client.events.EventType;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.packet.PacketEvent;
import dev.mzc.client.mixin.accessor.IPlayerInteractEntityC2SPacket;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BedTrap extends Module {
    public final NumberValue<Double> squashFactor = new NumberValue<>("Squash Factor", 0.5, 0.1, 1.0, 0.1);
    public final NumberValue<Integer> duration = new NumberValue<>("Duration", 10, 2, 40, 1);
    
    private final Map<UUID, Long> animatedEntities = new HashMap<>();

    public BedTrap() {
        super("BedTrap", Category.Render);
    }

    @EventHandler
    public void onPacket(PacketEvent event) {
        if (mc.world == null || mc.player == null) return;
        
        if (event.getType() == EventType.SEND && event.getPacket() instanceof PlayerInteractEntityC2SPacket packet) {
            packet.handle(new PlayerInteractEntityC2SPacket.Handler() {
                @Override
                public void interact(net.minecraft.util.Hand hand) {}
                @Override
                public void interactAt(net.minecraft.util.Hand hand, net.minecraft.util.math.Vec3d pos) {}
                
                @Override
                public void attack() {
                    int id = ((IPlayerInteractEntityC2SPacket) packet).getEntityId();
                    Entity entity = mc.world.getEntityById(id);
                    if (entity instanceof LivingEntity) {
                        animatedEntities.put(entity.getUuid(), System.currentTimeMillis());
                    }
                }
            });
        }
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (animatedEntities.isEmpty()) return;
        long now = System.currentTimeMillis();
        long maxDuration = duration.get() * 50L;
        animatedEntities.entrySet().removeIf(entry -> now - entry.getValue() > maxDuration);
    }

    public float getScaleY(Entity entity) {
        if (!isEnabled() || entity == null) return 1.0f;
        Long startTime = animatedEntities.get(entity.getUuid());
        if (startTime == null) return 1.0f;

        long elapsed = System.currentTimeMillis() - startTime;
        float maxDuration = duration.get() * 50f;
        float progress = elapsed / maxDuration;

        if (progress >= 1.0f) return 1.0f;

        // Плавное сплющивание и возврат: sin(pi * progress)
        // 0 -> 1 -> 0
        float squash = (float) Math.sin(progress * Math.PI);
        return 1.0f - (squash * (1.0f - squashFactor.get().floatValue()));
    }
}
