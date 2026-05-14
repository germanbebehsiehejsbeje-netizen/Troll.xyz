package dev.mzc.client.module.impl.misc;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;

public class AutoRespawn extends Module {

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private int savedSlot = 0;
    private float savedYaw = 0;
    private float savedPitch = 0;

    private boolean waitingRestore = false;
    private int restoreTicks = 0;

    public AutoRespawn() {
        super("AutoRespawn", Category.Misc);
        this.setType(ModuleType.Safe);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!isEnabled() || mc.player == null) return;
            tick();
        });
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }

    private void tick() {
        ClientPlayerEntity p = mc.player;
        if (p == null) return;

        // 妫€娴嬫浜＄晫闈?鈫?鍏堜繚瀛?
        if (mc.currentScreen instanceof DeathScreen) {

            // 淇濆瓨姝讳骸鍓嶇姸鎬?
            savedSlot = p.getInventory().getSelectedSlot();
            savedYaw = p.getYaw();
            savedPitch = p.getPitch();

            // 杩涜閲嶇敓
            p.requestRespawn();
            mc.setScreen(null);

            // 鏍囪杩涘叆鎭㈠绛夊緟
            waitingRestore = true;
            restoreTicks = 2; // 寤惰繜 2 tick 淇濊瘉瀹炰綋鍒濆鍖栧畬姣?
            return;
        }

        // 閲嶇敓瀹屾垚 鈫?寤惰繜鎭㈠鐘舵€?
        if (waitingRestore) {
            if (restoreTicks > 0) {
                restoreTicks--;
                return;
            }

            // 鎭㈠瑙嗚鍜岀墿鍝佹爮
            p.getInventory().setSelectedSlot(savedSlot);
            p.setYaw(savedYaw);
            p.setPitch(savedPitch);

            waitingRestore = false;
        }
    }
}

