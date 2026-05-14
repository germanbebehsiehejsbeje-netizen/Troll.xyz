package dev.mzc.client.module.impl.player;

import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;

import java.util.Random;

public class MiddleClickExtra extends Module {

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Random random = new Random();

    private final NumberValue<Integer> swapDelay = new NumberValue<>("Swap Delay", 0, 0, 10, 1);
    private final NumberValue<Integer> swapBackDelay = new NumberValue<>("Swap Back Delay", 1, 0, 10, 1);

    private int currentSwapBackDelay = 0;
    private int currentSwapDelay = 0;
    private int originalSlot = -1;
    private boolean waitingSwapBack = false;
    private boolean waitingUse = false;

    public enum Mode {
        EnderPearl(),
        FireworkRocket(),
        WindCharge(),
        ExperienceBottle();
        Mode() {
        }
    }

    public final EnumValue<Mode> mode = new EnumValue<>("Mode", Mode.EnderPearl);
    private final BoolValue onlyElytra = new BoolValue("OnlyElytra", true, () -> mode.get() == Mode.FireworkRocket);

    public MiddleClickExtra() {
        super("MiddleClickExtra", Category.Player);
        this.setType(ModuleType.Safe);

        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (!isEnabled() || mc.player == null || mc.interactionManager == null) return;
            onTick();
        });
    }

    @Override
    public void onEnable() {
        currentSwapBackDelay = 0;
        currentSwapDelay = 0;
        originalSlot = -1;
        waitingSwapBack = false;
        waitingUse = false;
    }

    @Override
    public void onDisable() {
        if ((waitingSwapBack || waitingUse) && mc.player != null && originalSlot != -1) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
        }

        currentSwapBackDelay = 0;
        currentSwapDelay = 0;
        waitingSwapBack = false;
        waitingUse = false;
        originalSlot = -1;
    }

    private void onTick() {
        // ======== 澶勭悊浣跨敤寤惰繜 ========
        if (waitingUse) {
            if (currentSwapDelay > 0) {
                currentSwapDelay--;
                return;
            }
            // 寤惰繜缁撴潫锛屾墽琛屼氦浜?
            if (mc.interactionManager != null) {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            }
            waitingUse = false;
            
            // 璁剧疆鍒囧洖寤惰繜
            currentSwapBackDelay = swapBackDelay.get();
            waitingSwapBack = true;
            return;
        }

        // ======== 澶勭悊寤惰繜鍒囧洖 ========
        if (currentSwapBackDelay > 0) {
            currentSwapBackDelay--;
            if (currentSwapBackDelay == 0 && waitingSwapBack && originalSlot != -1) {
                mc.player.getInventory().setSelectedSlot(originalSlot);
                waitingSwapBack = false;
            }
            return;
        }

        // ======== 宸茬粡鍦ㄧ瓑寰呭垏鍥烇紝涓嶅啀瑙﹀彂 ========
        if (waitingSwapBack) return;

        // ======== 涓敭瑙﹀彂涓€娆?========
        if (!mc.mouse.wasMiddleButtonClicked()) return;

        ItemStack main = mc.player.getMainHandStack();
        Item targetItem = null;

        // ======== 鍒ゆ柇鐩爣鐗╁搧 ========
        switch (mode.get()) {
            case EnderPearl:
                targetItem = Items.ENDER_PEARL;
                break;
            case FireworkRocket:
                targetItem = Items.FIREWORK_ROCKET;
                break;
            case WindCharge:
                targetItem = Items.WIND_CHARGE;
                break;
            case ExperienceBottle:
                targetItem = Items.EXPERIENCE_BOTTLE;
                break;
        }

        if (targetItem == null) return;

        // ======== OnlyElytra 閫昏緫鍒ゆ柇 ========
        if (mode.get() == Mode.FireworkRocket && onlyElytra.get()) {
            if (!mc.player.isGliding()) return; // 濡傛灉涓嶆槸姝ｅ湪椋炶鐘舵€侊紝鍒欎笉瑙﹀彂
        }

        // ======== 濡傛灉涓绘墜宸茬粡鏄洰鏍囩墿鍝侊紝鐩存帴浣跨敤锛屼笉鍒囨崲 ========
        if (main.getItem() == targetItem) {
            if (mc.interactionManager != null) {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            }
            return;
        }

        // ======== 鍑嗘槦妫€娴?(濡傛灉鏄弽鐝犮€佺伀绠€侀寮广€佺粡楠岀摱锛岄€氬父闇€瑕佹寚鍚戞柟鍧楁垨绌烘皵锛岃繖閲岀粺涓€澶勭悊) ========
        if (mc.crosshairTarget.getType() == HitResult.Type.ENTITY) return;

        // ======== 鎵剧洰鏍囩墿鍝佹墍鍦ㄧ殑妲戒綅 ========
        int targetSlot = findItemSlot(targetItem);
        if (targetSlot == -1) return;

        // ======== 鎵ц鍒囨崲骞朵氦浜?========
        originalSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(targetSlot);
        handleInteraction();
    }

    private void handleInteraction() {
        if (swapDelay.get() == 0) {
            if (mc.interactionManager != null) {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            }
            currentSwapBackDelay = swapBackDelay.get();
            waitingSwapBack = true;
        } else {
            currentSwapDelay = swapDelay.get();
            waitingUse = true;
        }
    }

    private int findItemSlot(Item item) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == item) return i;
        }
        return -1;
    }
}


