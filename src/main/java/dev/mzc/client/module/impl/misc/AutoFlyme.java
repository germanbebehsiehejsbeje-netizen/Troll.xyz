package dev.mzc.client.module.impl.misc;

import dev.mzc.client.events.EventType;
import dev.mzc.client.events.packet.PacketEvent;
import dev.mzc.client.events.player.MotionEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.player.MovementUtil;
import dev.mzc.client.utils.time.TimerUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;

public class AutoFlyme extends Module {
    public AutoFlyme() {
        super("AutoFlyme", Category.Misc);
    }

    public final BoolValue instantSpeed = new BoolValue("InstantSpeed", true);
    public final BoolValue hover = new BoolValue("Hover", false);
    public final BoolValue useTimer = new BoolValue("UseTimer", false);

    public final NumberValue<Float> hoverY = new NumberValue<>("HoverY", 0.228f, 0.0f, 1.0f, 0.01f, () -> hover.get());
    public final NumberValue<Float> speed = new NumberValue<>("Speed", 1.05f, 0.0f, 8f, 0.01f, () -> instantSpeed.get());

    private final TimerUtil timer = new TimerUtil();

    @Override
    public void onEnable() {
        if (mc.player == null) return;
        if (!mc.player.getAbilities().flying) {
            mc.player.networkHandler.sendChatCommand("flyme");
        }
    }

    @Override
    public void onDisable() {
        // MZC Client doesn't seem to have a global TICK_TIMER field in Sakura class, 
        // usually it's handled via TimerModule or specific mixins.
    }

    @EventHandler
    public void onPacketReceive(PacketEvent e) {
        if (e.getType() == EventType.RECEIVE && e.getPacket() instanceof GameMessageS2CPacket packet) {
            String text = packet.content().getString();
            if ((text.contains("Вы атаковали игрока") || text.contains("Возможность летать была удалена")) && timer.passedMS(1000)) {
                mc.player.networkHandler.sendChatCommand("flyme");
                mc.player.networkHandler.sendChatCommand("flyme");
                timer.reset();
            }
        }
    }

    @EventHandler
    public void onMotion(MotionEvent event) {
        if (event.getType() != EventType.PRE) return;

        // Note: useTimer logic is omitted as it requires a specific timer system 
        // which might be different in MZC (TICK_TIMER is not present in Sakura).
        
        if (!mc.player.getAbilities().flying && timer.passedMS(1000) && !mc.player.isOnGround() && mc.options.jumpKey.isPressed()) {
            mc.player.networkHandler.sendChatCommand("flyme");
            timer.reset();
        }

        if (!mc.options.jumpKey.isPressed() && hover.get() && mc.player.getAbilities().flying && !mc.player.isOnGround() && !mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().offset(0.0, -hoverY.get(), 0.0)).iterator().hasNext()) {
            mc.player.setVelocity(mc.player.getVelocity().x, -0.05, mc.player.getVelocity().z);
        }

        if (instantSpeed.get() && mc.player.getAbilities().flying) {
            if (MovementUtil.isMoving()) {
                double[] dir = MovementUtil.directionSpeed(speed.get());
                mc.player.setVelocity(dir[0], mc.player.getVelocity().y, dir[1]);
            } else {
                mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
            }
        }
    }
}
