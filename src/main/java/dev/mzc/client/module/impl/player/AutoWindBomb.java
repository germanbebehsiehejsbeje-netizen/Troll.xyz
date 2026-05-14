package dev.mzc.client.module.impl.player;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;

import java.util.Random;


public class AutoWindBomb extends Module {

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Random random = new Random();

    private int swapBackDelay = 0;
    private int originalSlot = -1;
    private boolean waitingSwapBack = false;

    public AutoWindBomb() {
        super("AutoWindBomb", Category.Player);
        this.setType(ModuleType.Safe);

        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (!isEnabled() || mc.player == null || mc.interactionManager == null) return;
            onTick();
        });
    }

    @Override
    public void onEnable() {
        swapBackDelay = 0;
        originalSlot = -1;
        waitingSwapBack = false;
    }

    @Override
    public void onDisable() {
        if (waitingSwapBack && mc.player != null && originalSlot != -1) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
        }

        swapBackDelay = 0;
        waitingSwapBack = false;
        originalSlot = -1;
    }

    private void onTick() {

        // ======== 澶勭悊寤惰繜鍒囧洖 ========
        if (swapBackDelay > 0) {
            swapBackDelay--;
            if (swapBackDelay == 0 && waitingSwapBack && originalSlot != -1) {
                mc.player.getInventory().setSelectedSlot(originalSlot);
                waitingSwapBack = false;
            }
            return;
        }

        // ======== 宸茬粡鍦ㄧ瓑寰呭垏鍥烇紝涓嶅啀瑙﹀彂 ========
        if (waitingSwapBack) return;

        // ======== 鍙抽敭瑙﹀彂涓€娆?========
        if (!mc.mouse.wasRightButtonClicked()) return;

        ItemStack main = mc.player.getMainHandStack();

        // ======== 涓绘墜宸茬粡鏄寮?/ 鑽按 / 缁忛獙鐡?/ 椋熺墿 鈫?涓嶈Е鍙戯紙瀹夊叏淇濇姢锛?========
        if (isWindCharge(main)) return;
        if (main.getItem() instanceof PotionItem
                || main.getItem() instanceof ExperienceBottleItem
                || main.getItem() instanceof EnderPearlItem
                || main.isOf(Items.GOLDEN_APPLE)
                || main.isOf(Items.ENCHANTED_GOLDEN_APPLE)
                || main.getItem() instanceof BowItem){

            return;
        }

        /* ======== 姝ｅ湪涓剧浘鏃朵笉鎵ц ======== */
        if (mc.player.isBlocking()) return;

        // ======== 鍑嗘槦妫€娴?========
        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof BlockHitResult blockHit)) return;

        // ======== 鎵鹃寮?========
        int bombSlot = findWindBombSlot();
        if (bombSlot == -1) return;

        // ======== 鎵ц锛氬垏椋庡脊 鈫?鍙抽敭鏀剧疆 ========
        originalSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(bombSlot);

        if (mc.interactionManager != null) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        }

        // ======== 璁剧疆 1~3 tick 鍚庡垏鍥?========
        swapBackDelay = 1 + random.nextInt(3);
        waitingSwapBack = true;
    }

    private int findWindBombSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isWindCharge(stack)) return i;
        }
        return -1;
    }

    private boolean isWindCharge(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return Registries.ITEM.getId(stack.getItem()).getPath().equalsIgnoreCase("wind_charge");
    }
}

