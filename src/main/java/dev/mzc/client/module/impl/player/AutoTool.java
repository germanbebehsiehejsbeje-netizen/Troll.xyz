package dev.mzc.client.module.impl.player;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.NumberValue;

public class AutoTool extends Module {

    private final MinecraftClient mc = MinecraftClient.getInstance();

    // Sakura 椋庢牸寤惰繜閰嶇疆
    private final NumberValue<Double> switchDelay = new NumberValue<>("SwitchDelay", 2.0, 0.0, 10.0, 0.1);
    private final NumberValue<Double> switchBackDelay = new NumberValue<>("SwitchBackDelay", 2.0, 0.0, 10.0, 0.1);

    private int lastSlot = -1;
    private boolean swapped = false;

    // 鐙珛璁℃暟鍣?
    private int switchCounter = 0;
    private int switchBackCounter = 0;

    public AutoTool() {
        super("AutoTool", Category.Player);
        this.setType(ModuleType.Safe);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!isEnabled() || mc.player == null || mc.world == null) return;
            handleAutoTool();
        });
    }

    @Override
    public void onEnable() {
        swapped = false;
        lastSlot = -1;
        switchCounter = 0;
        switchBackCounter = 0;
    }

    @Override
    public void onDisable() {
        switchCounter = 0;
        switchBackCounter = 0;
    }

    private void handleAutoTool() {
        PlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null) return;

        // 濡傛灉鍦ㄥ垏鎹㈠欢杩熶腑锛屽厛绛夊緟
        if (switchCounter > 0) {
            switchCounter--;
            return;
        }

        // 濡傛灉鍦ㄥ垏鍥炲欢杩熶腑锛屽厛绛夊緟
        if (switchBackCounter > 0) {
            switchBackCounter--;
            return;
        }

        if (mc.crosshairTarget instanceof BlockHitResult hit && mc.options.attackKey.isPressed()) {
            BlockState state = mc.world.getBlockState(hit.getBlockPos());
            int bestSlot = getBestToolSlot(state);

            if (bestSlot != -1 && p.getInventory().getSelectedSlot() != bestSlot) {
                if (!swapped) {
                    lastSlot = p.getInventory().getSelectedSlot();
                    swapped = true;
                }

                // 鍒囨崲宸ュ叿
                p.getInventory().setSelectedSlot(bestSlot);
                switchCounter = switchDelay.get().intValue(); // 鍒囨崲寤惰繜
            }
        } else {
            // 鍒囧洖鍘熸潵宸ュ叿
            if (swapped && lastSlot != -1 && p.getInventory().getSelectedSlot() != lastSlot) {
                p.getInventory().setSelectedSlot(lastSlot);
                swapped = false;
                switchBackCounter = switchBackDelay.get().intValue(); // 鍒囧洖寤惰繜
            }
        }
    }

    // 鍙€夋嫨鎸栨帢閫熷害鏈€楂樼殑宸ュ叿
    private int getBestToolSlot(BlockState state) {
        PlayerEntity p = mc.player;
        if (p == null) return -1;

        int bestSlot = -1;
        float bestSpeed = 1; // 灏忎簬绛変簬1鐨勯€熷害 Minecraft 璁や负涓嶉€傜敤

        for (int i = 0; i < 9; i++) {
            ItemStack stack = p.getInventory().getStack(i);
            float speed = stack.getMiningSpeedMultiplier(state);

            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }

        return bestSlot;
    }
}



