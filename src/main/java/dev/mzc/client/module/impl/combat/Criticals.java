package dev.mzc.client.module.impl.combat;

import dev.mzc.client.events.entity.AttackEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.EnumValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.entity.LivingEntity;

public class Criticals extends Module {

    private final EnumValue<Mode> mode = new EnumValue<>("Mode", Mode.Packet);

    public Criticals() {
        super("Criticals", Category.Combat);
        this.setType(ModuleType.Hack);
    }

    @EventHandler
    public void onAttack(AttackEvent event) {
        if (event.getTargetEntity() instanceof LivingEntity && mc.player.isOnGround() && !mc.player.isTouchingWater() && !mc.player.isInLava()) {
            switch (mode.get()) {
                case Packet -> {
                    double x = mc.player.getX();
                    double y = mc.player.getY();
                    double z = mc.player.getZ();
                    
                    mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0625, z, false, false));
                    mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, false));
                    
                    mc.player.addCritParticles(event.getTargetEntity());
                }
                case Jump -> {
                    if (mc.player.isOnGround())
                        mc.player.jump();
                }
            }
        }
    }

    public enum Mode {
        Packet,
        Jump
    }
}
