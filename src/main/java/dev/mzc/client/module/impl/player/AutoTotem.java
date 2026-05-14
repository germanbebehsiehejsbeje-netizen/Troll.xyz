package dev.mzc.client.module.impl.player;

import dev.mzc.client.utils.player.SlotUtil;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import dev.mzc.client.values.impl.RangeValue;
import dev.mzc.client.utils.math.MathUtil;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.math.BlockPos;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.util.math.Direction;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

public class AutoTotem extends Module {

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private int swapCooldown = 0;
    private long nextActionTime = 0;
    private int step = 0; // 0: Idle, 1: Picked Up, 2: Placing, 3: Refill Picked Up, 4: Refill Placing, 5: Hotbar Swapping, 6: Waiting For Swap
    private int targetSlotId = -1;
    private int waitTicks = 0;

    private final RangeValue<Integer> delayRange = new RangeValue<>("DelayRange", 0, 2, 0, 20, 1);
    private final RangeValue<Integer> hotbarSwapDelay = new RangeValue<>("HotbarSwapDelay", 50, 150, 0, 1000, 10);
    private final NumberValue<Integer> clickDelayValue = new NumberValue<>("ClickDelay", 1, 0, 10, 1);
    public final NumberValue<Double> healthThreshold = new NumberValue<>("HealthThreshold", 20.0, 1.0, 30.0, 0.5);
    private final BoolValue checkContainer = new BoolValue("CheckContainer", true);
    private final BoolValue strictMode = new BoolValue("StrictMode", false);
    private final BoolValue antiCheat = new BoolValue("AntiCheat", true);
    private final BoolValue autoRefill = new BoolValue("HotbarTotemRefill", true);
    private final BoolValue hotbarSwap = new BoolValue("HotbarTotem", true);

    private final boolean[] hotbarTotems = new boolean[9];
    private int refillSlot = -1; // 姝ｅ湪琛ヤ綅鐨勭洰鏍囨Ы浣?
    private int preSlot = -1; // 鍒囨墜鍓嶇殑鍘熸Ы浣?
    private int hotbarTotemSlot = -1; // 璁″垝鐢ㄤ簬浜ゆ崲鍒板壇鎵嬬殑蹇嵎鏍忓浘鑵炬Ы

    public AutoTotem() {
        super("AutoTotem", Category.Player);
        this.setType(ModuleType.Safe);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!isEnabled() || mc.player == null) return;
            handleAutoTotem();
        });
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    private void resetState() {
        swapCooldown = 0;
        nextActionTime = 0;
        step = 0;
        targetSlotId = -1;
        refillSlot = -1;
        preSlot = -1;
        for (int i = 0; i < 9; i++) hotbarTotems[i] = false;
    }

    private void handleAutoTotem() {
        if (mc.player == null) return;

        // 鏇存柊蹇嵎鏍忓浘鑵捐蹇?
        if (autoRefill.get()) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.isOf(Items.TOTEM_OF_UNDYING)) {
                    hotbarTotems[i] = true;
                } else if (!stack.isEmpty() && mc.currentScreen instanceof InventoryScreen) {
                    // 濡傛灉鐜╁鍦ㄨ儗鍖呯晫闈㈡墜鍔ㄥ線蹇嵎鏍忔斁浜嗛潪鍥捐吘鐗╁搧锛屽仠姝㈣褰曡浣嶇疆涓哄浘鑵句綅
                    hotbarTotems[i] = false;
                }
            }
        }

        if (swapCooldown > 0) {
            swapCooldown--;
            return;
        }

        if (System.currentTimeMillis() < nextActionTime) {
            return;
        }

        // 鐘舵€佹満澶勭悊
        switch (step) {
            case 0: // Idle - 鏌ユ壘骞跺紑濮?
                startProcess();
                break;
            case 1: // Picked Up - 鍑嗗鏀惧叆鍓墜
                placeInOffhand();
                break;
            case 2: // Placing - 鏀惧叆鍓墜鍚庯紝濡傛灉鍏夋爣杩樻湁涓滆タ鍒欐斁鍥?
                cleanupCursor();
                break;
            case 3: // Refill Picked Up
                placeInHotbar();
                break;
            case 4: // Refill Placing
                cleanupRefill();
                break;
            case 5: // Hotbar Swapping
                performHotbarSwap();
                break;
            case 6: // Waiting For Swap
                checkSwapSuccess();
                break;
        }
    }

    private void startProcess() {
        // 瀹瑰櫒妫€鏌?
        if (checkContainer.get() && mc.currentScreen instanceof HandledScreen && !(mc.currentScreen instanceof InventoryScreen)) {
            return;
        }

        // 浼樺厛妫€鏌ュ壇鎵嬫槸鍚﹂渶瑕佽ˉ鍥捐吘
        float currentHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        boolean offhandNeedsTotem = !mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING) && 
                                  (currentHealth <= healthThreshold.get() || true); // 濮嬬粓灏濊瘯淇濇寔鍓墜鍥捐吘

        if (offhandNeedsTotem) {
            // 1. 浼樺厛灏濊瘯蹇嵎鏍忓垏鎵?
            if (hotbarSwap.get()) {
                int hotbarTotem = -1;
                for (int i = 0; i < 9; i++) {
                    if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                        hotbarTotem = i;
                        break;
                    }
                }

                if (hotbarTotem != -1) {
                    preSlot = mc.player.getInventory().getSelectedSlot();
                    mc.player.getInventory().setSelectedSlot(hotbarTotem);
                    if (mc.player.networkHandler != null) {
                        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(hotbarTotem));
                    }
                    hotbarTotemSlot = hotbarTotem;
                    step = 5;
                    
                    // 使用自定义延迟范围设置换手动作时间
                    int delay = ThreadLocalRandom.current().nextInt(hotbarSwapDelay.getMinValue(), hotbarSwapDelay.getMaxValue() + 1);
                    nextActionTime = System.currentTimeMillis() + delay;
                    return;
                }
            }

            // 2. 蹇嵎鏍忔病鍥捐吘锛岃蛋鑳屽寘鐐瑰嚮閫昏緫
            // 涓ユ牸妯″紡锛氬繀椤绘墦寮€鑳屽寘
            if (strictMode.get() && !(mc.currentScreen instanceof InventoryScreen)) return;

            int totemSlot = findTotem(9); // 浠庣9鏍煎紑濮嬫壘锛岄伩鍏嶆嬁蹇嵎鏍忕殑
            if (totemSlot == -1) totemSlot = findTotem(0); // 鎵句笉鍒板氨浠庡ご鎵?
            
            if (totemSlot != -1) {
                int slotId = SlotUtil.indexToId(totemSlot);
                if (slotId != -1) {
                    targetSlotId = slotId;
                    click(targetSlotId);
                    step = 1;
                    setClickDelay();
                    return;
                }
            }
        }

        // 濡傛灉鍓墜涓嶉渶瑕侊紝涓旀墦寮€浜嗚儗鍖咃紝妫€鏌ュ揩鎹锋爮琛ヤ綅
        if (autoRefill.get() && mc.currentScreen instanceof InventoryScreen) {
            for (int i = 0; i < 9; i++) {
                if (hotbarTotems[i] && !mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                    int totemInMain = findTotem(9); // 蹇呴』浠庝富鑳屽寘鎵?
                    if (totemInMain != -1) {
                        int sourceId = SlotUtil.indexToId(totemInMain);
                        refillSlot = SlotUtil.indexToId(i);
                        
                        targetSlotId = sourceId;
                        click(targetSlotId);
                        step = 3;
                        setClickDelay();
                        return;
                    }
                }
            }
        }
    }

    private void performHotbarSwap() {
        if (mc.player == null || mc.player.networkHandler == null) {
            resetState();
            return;
        }

        // 濡傛灉鍓墜宸茬粡鏄浘鑵惧垯涓嶅啀浜ゆ崲
        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            step = 0;
            preSlot = -1;
            hotbarTotemSlot = -1;
            swapCooldown = MathUtil.getRandom(delayRange.getMinValue(), delayRange.getMaxValue());
            return;
        }

        // 闃插憜锛氱‘淇濅富鎵嬮€変腑鐨勬槸鍥捐吘
        if (!mc.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            // 濡傛灉褰撳墠閫変腑涓嶆槸鍥捐吘锛屽皾璇曞垏鍥炶褰曠殑鍥捐吘妲?
            if (hotbarTotemSlot >= 0 && hotbarTotemSlot < 9
                    && mc.player.getInventory().getStack(hotbarTotemSlot).isOf(Items.TOTEM_OF_UNDYING)) {
                mc.player.getInventory().setSelectedSlot(hotbarTotemSlot);
                if (mc.player.networkHandler != null) {
                    mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(hotbarTotemSlot));
                }
            }
        }

        // 鍐嶆纭涓绘墜鏄浘鑵撅紝鍚﹀垯鏀惧純鏈浜ゆ崲锛岄伩鍏嶆妸濂囨€墿鍝佹斁鍒板壇鎵?
        if (!mc.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            step = 0;
            preSlot = -1;
            hotbarTotemSlot = -1;
            swapCooldown = MathUtil.getRandom(delayRange.getMinValue(), delayRange.getMaxValue());
            return;
        }

        // 鍙戦€佹崲鎵嬪寘
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
        
        // 进入等待检测阶段
        step = 6;
        waitTicks = 0;
        setClickDelay();
    }

    private void checkSwapSuccess() {
        if (mc.player == null || mc.player.networkHandler == null) {
            resetState();
            return;
        }

        // 检测副手是否已经切换成功
        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            // 切换成功，切回原槽位
            if (preSlot != -1) {
                mc.player.getInventory().setSelectedSlot(preSlot);
                if (mc.player.networkHandler != null) {
                    mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(preSlot));
                }
            }

            step = 0;
            preSlot = -1;
            hotbarTotemSlot = -1;
            swapCooldown = MathUtil.getRandom(delayRange.getMinValue(), delayRange.getMaxValue());
        } else {
            // 如果还没成功，继续等待，设置一个超时（比如等待 10 次检测，即约 10 tick）
            waitTicks++;
            if (waitTicks > 10) {
                // 超时，可能换手失败，尝试切回原槽位并重置
                if (preSlot != -1) {
                    mc.player.getInventory().setSelectedSlot(preSlot);
                    if (mc.player.networkHandler != null) {
                        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(preSlot));
                    }
                }
                step = 0;
                preSlot = -1;
                hotbarTotemSlot = -1;
                swapCooldown = MathUtil.getRandom(delayRange.getMinValue(), delayRange.getMaxValue());
            } else {
                // 继续等待下一次检测
                setClickDelay();
            }
        }
    }

    private void placeInOffhand() {
        int offhandSlotId = 45;
        click(offhandSlotId);
        
        step = 2;
        setClickDelay();
    }

    private void placeInHotbar() {
        if (refillSlot != -1) {
            click(refillSlot);
        }
        step = 4;
        setClickDelay();
    }

    private void cleanupRefill() {
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            click(targetSlotId);
        }
        step = 0;
        refillSlot = -1;
        targetSlotId = -1;
            swapCooldown = MathUtil.getRandom(delayRange.getMinValue(), delayRange.getMaxValue());
    }

    private void cleanupCursor() {
        // 濡傛灉鍏夋爣涓婅繕鏈変笢瑗匡紙姣斿涔嬪墠鍓墜涓婄殑涓滆タ锛夛紝鏀惧洖鍒板垰鎵嶆嬁鍥捐吘鐨勪綅缃?
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            click(targetSlotId);
        }
        
        step = 0;
        targetSlotId = -1;
        swapCooldown = MathUtil.getRandom(delayRange.getMinValue(), delayRange.getMaxValue());
    }

    private void setClickDelay() {
        // 1 tick = 50ms.
        int baseDelay = clickDelayValue.get() * 50;
        
        // 随机增加一些毫秒 (1-50ms)，使得时间不再是整 50/100/150ms
        int randomMs = ThreadLocalRandom.current().nextInt(1, 51);
        
        // 反作弊开启时，额外增加随机延迟 (0-100ms)
        if (antiCheat.get()) {
            randomMs += ThreadLocalRandom.current().nextInt(0, 101);
        }
        
        nextActionTime = System.currentTimeMillis() + baseDelay + randomMs;
    }

    private void click(int id) {
        if (mc.interactionManager == null || mc.player == null) return;
        
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, id, 0, SlotActionType.PICKUP, mc.player);
    }

    private int findTotem(int startSlot) {
        PlayerInventory inv = mc.player.getInventory();
        for (int i = startSlot; i < 36; i++) {
            if (inv.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return i;
        }
        if (startSlot > 0) { // 濡傛灉浠庡悗闈㈡病鎵惧埌锛屽啀浠庡墠闈㈡壘
            for (int i = 0; i < startSlot; i++) {
                if (inv.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return i;
            }
        }
        return -1;
    }
}



