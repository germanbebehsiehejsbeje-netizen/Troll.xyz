package dev.mzc.client.module.impl.movement;

import dev.mzc.client.events.entity.BlockPushEvent;
import dev.mzc.client.events.player.MoveEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Box;

import static dev.mzc.client.Sakura.mc;

public class AntiBlockPush extends Module {

    private final EnumValue<Mode> mode = new EnumValue<>("Mode", Mode.Normal);
    private final NumberValue<Double> speed = new NumberValue<>("Speed", 10.0, 0.0, 20.0, 1.0, () -> mode.get() == Mode.Strafe);

    public AntiBlockPush() {
        super("AntiBlockPush", Category.Movement);
    }

    @EventHandler
    private void onBlockPush(BlockPushEvent event) {
        event.cancel();
    }

    @EventHandler
    public void onMove(MoveEvent event) {
        if (mode.get() != Mode.Strafe) return;
        
        if (!isInsideBlock()) {
            return;
        }
        
        double s = this.speed.get();
        double moveSpeed = 0.002873 * s;
        double forward = mc.player.forwardSpeed;
        double sideways = mc.player.sidewaysSpeed;
        double yaw = mc.player.getYaw();

        if (forward == 0.0 && sideways == 0.0) {
            event.setX(0.0);
            event.setZ(0.0);
            return;
        }

        if (forward != 0.0 && sideways != 0.0) {
            forward *= Math.sin(0.7853981633974483);
            sideways *= Math.cos(0.7853981633974483);
        }

        double sin = Math.sin(Math.toRadians(yaw));
        double cos = Math.cos(Math.toRadians(yaw));

        event.setX(forward * moveSpeed * -sin + sideways * moveSpeed * cos);
        event.setZ(forward * moveSpeed * cos - sideways * moveSpeed * -sin);
    }

    private boolean isInsideBlock() {
        if (nullCheck()) return false;
        Box box = mc.player.getBoundingBox().contract(0.001);
        return mc.world.getBlockCollisions(mc.player, box).iterator().hasNext();
    }

    public enum Mode {
        Normal,
        Strafe
    }
}
