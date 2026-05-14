package dev.mzc.client.module.impl.movement;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.BlockItem;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.BoolValue;

public class SafeWalk extends Module {

    private final MinecraftClient mc = MinecraftClient.getInstance();

    // 前方检测距离（靠边提前量）
    private final double forwardCheckDistance = 0.005;

    // 离开边缘计时（避免抖动）
    private int safeTicks = 0;

    // 是否是模块模拟的下蹲
    private boolean simSneaking = false;

    private final BoolValue holdingBlockCheck = new BoolValue("Holding block check", false);
    private final BoolValue directionCheck = new BoolValue("Direction check", false);

    public SafeWalk() {
        super("SafeWalk", Category.Movement);
        this.setType(ModuleType.Safe);

        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (!isEnabled() || mc.player == null || mc.world == null) return;
            handleSafeWalk();
        });
    }

    @Override
    public void onEnable() {
        safeTicks = 0;
    }

    @Override
    public void onDisable() {
        safeTicks = 0;
        releaseSneak();
    }

    private void handleSafeWalk() {
        if (!mc.player.isOnGround()) {
            safeTicks = 0;
            releaseSneak();
            return;
        }

        // Holding block check
        if (holdingBlockCheck.get() && !(mc.player.getMainHandStack().getItem() instanceof BlockItem)) {
            safeTicks = 0;
            releaseSneak();
            return;
        }

        // Direction check
        if (directionCheck.get() && !mc.options.backKey.isPressed()) {
            safeTicks = 0;
            releaseSneak();
            return;
        }

        Vec3d vel = mc.player.getVelocity();
        double vx = vel.x;
        double vz = vel.z;

        double speed = Math.sqrt(vx * vx + vz * vz);
        if (speed == 0) return;

        double nx = vx / speed;
        double nz = vz / speed;

        Vec3d pos = mc.player.getEntityPos();
        double checkX = pos.x + nx * forwardCheckDistance;
        double checkY = pos.y - 0.1;
        double checkZ = pos.z + nz * forwardCheckDistance;

        BlockPos below = BlockPos.ofFloored(checkX, checkY - 0.5, checkZ);
        boolean groundAhead = !mc.world.getBlockState(below).isAir();

        if (!groundAhead) {
            safeTicks = 0;
            pressSneak();
            return;
        }

        safeTicks++;
        if (safeTicks >= 1) {
            releaseSneak();
        }
    }

    private boolean isPlayerPhysicallySneaking() {
        KeyBinding key = mc.options.sneakKey;
        if (key == null) return false;
        return InputUtil.isKeyPressed(mc.getWindow(), key.getDefaultKey().getCode());
    }

    private void pressSneak() {
        KeyBinding key = mc.options.sneakKey;
        if (key == null) return;

        if (isPlayerPhysicallySneaking()) {
            simSneaking = false;
            return;
        }

        if (!simSneaking) {
            key.setPressed(true);
            simSneaking = true;
        }
    }

    private void releaseSneak() {
        if (!simSneaking) return;
        KeyBinding key = mc.options.sneakKey;
        if (key != null) key.setPressed(false);
        simSneaking = false;
    }
}
