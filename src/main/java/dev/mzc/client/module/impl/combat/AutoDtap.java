package dev.mzc.client.module.impl.combat;

import dev.mzc.client.auth.UserRole;
import dev.mzc.client.events.client.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.EndCrystalItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.util.math.Box;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.BoolValue;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

public class AutoDtap extends Module {

    private final Random random = new Random();

    private int swapBackDelay = 0;
    private int stepDelay = 0;
    private int step = 0;
    private int originalSlot = -1;
    private boolean triggered = false;
    private final BoolValue swapBack = new BoolValue("SwapBack", true);

    public AutoDtap() {
        super("AutoDtap", Category.Combat);
        this.setType(ModuleType.Safe);
        this.setRequiredRole(UserRole.VIP);
    }

    @Override
    public void onEnable() {
        swapBackDelay = 0;
        stepDelay = 0;
        step = 0;
        originalSlot = -1;
        triggered = false;
    }

    @Override
    public void onDisable() {
        resetState();
        swapBackDelay = 0;
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        // 濡傛灉姝ｅ湪鎵ц杩囩▼涓紙step 1, 2锛夛紝褰诲簳娑堣€楁帀鍙抽敭鐐瑰嚮浜嬩欢锛屼笉璁?case 0 鎺ユ敹鍒?
        if (step != 0) {
            // 娑堣€楁帀 Minecraft 璁板綍鐨勬墍鏈夊彸閿偣鍑荤姸鎬?
            while (mc.options.useKey.wasPressed()) {}
            if (mc.mouse != null) mc.mouse.wasRightButtonClicked();
            mc.options.useKey.setPressed(false); // 寮哄埗鍙栨秷鎸変綇鐘舵€侊紝闃叉 client-side 鑷姩閲嶅鏀剧疆
            // 纭繚鏈?tick 涓嶄細鍐嶅悜涓嬫墽琛岃Е鍙戦€昏緫
        }

        // ======== 澶勭悊寤惰繜 ========
        if (stepDelay > 0) {
            stepDelay--;
            return;
        }

        // ======== 鐘舵€佹満澶勭悊 ========
        switch (step) {
            case 0: // 鏈Е鍙戯紝鍑嗗鏀剧疆榛戞洔鐭?
                // 鍙湁鍦?step 涓?0 鏃舵墠妫€鏌ヨЕ鍙?
                if (mc.mouse != null && mc.mouse.wasRightButtonClicked()) {
                    ItemStack main = mc.player.getMainHandStack();
                    // 蹇呴』鎵嬫寔鍓?                    ItemStack main = mc.player.getMainHandStack();
                    if (!main.isIn(ItemTags.SWORDS)) return;

                    // 纭繚鐜╁姝ｅ湪鐪嬪悜涓€涓柟鍧楋紙闃叉 air place锛?
                    HitResult hit = mc.crosshairTarget;
                    if (!(hit instanceof BlockHitResult blockHit)) return;
                    if (blockHit.getType() != HitResult.Type.BLOCK) return;
                    if (mc.world.getBlockState(blockHit.getBlockPos()).isAir()) return;

                    Block targetBlock = mc.world.getBlockState(blockHit.getBlockPos()).getBlock();
                    boolean isObsidian = targetBlock == Blocks.OBSIDIAN || targetBlock == Blocks.BEDROCK;

                    // 棰勫厛妫€鏌ユ槸鍚︽湁姘存櫠锛屾病鏈夊氨涓嶅紑濮嬫祦绋?
                    int endCrystalSlot = findEndCrystalSlot();
                    if (endCrystalSlot == -1) return;

                    originalSlot = mc.player.getInventory().getSelectedSlot();

                    if (isObsidian) {
                        // 濡傛灉鍘熸湰灏辨槸榛戞洔鐭虫垨鍩哄博锛岀洿鎺ュ垏鎹㈠埌姘存櫠骞惰繘鍏ヤ笅涓€姝?
                        mc.player.getInventory().setSelectedSlot(endCrystalSlot);
                        mc.interactionManager.syncSelectedSlot();
                        
                        // 鏋勯€犫€滅偣鍑绘柟鍧楅《闈⑩€濈殑鍛戒腑缁撴灉鐢ㄤ簬鏀剧疆姘存櫠
                        BlockHitResult topHit = new BlockHitResult(
                                blockHit.getPos(),
                                Direction.UP,
                                blockHit.getBlockPos(),
                                false
                        );
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, topHit);
                        mc.player.swingHand(Hand.MAIN_HAND);

                        triggered = true;
                        stepDelay = 1 + random.nextInt(2);
                        step = 2; // 鐩存帴璺冲埌鎭㈠鍘熸墜鎸侀樁娈碉紝鍥犱负宸茬粡鏀惧畬姘存櫠浜?
                    } else {
                        // 鎵惧埌榛戞洔鐭虫Ы浣?
                        int obsidianSlot = findObsidianSlot();
                        if (obsidianSlot == -1) return;

                        mc.player.getInventory().setSelectedSlot(obsidianSlot);
                        mc.interactionManager.syncSelectedSlot();

                        // 鐩存帴瀵瑰綋鍓嶆柟鍧楁墽琛屽彸閿斁缃粦鏇滅煶
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockHit);
                        mc.player.swingHand(Hand.MAIN_HAND);

                        // 銆愬叧閿慨澶嶃€戠珛鍗冲垏鎹㈠埌姘存櫠妲戒綅锛岄槻姝?client 鑷姩閲嶅鏀剧疆榛戞洔鐭?
                        mc.player.getInventory().setSelectedSlot(endCrystalSlot);
                        mc.interactionManager.syncSelectedSlot();

                        triggered = true;
                        // 璁剧疆鍒囨崲寤惰繜 1~2 tick
                        stepDelay = 1 + random.nextInt(2);
                        step = 1;
                    }
                }
                break;

            case 1: // 绛夊緟榛戞洔鐭虫斁缃畬鎴愬苟鏀剧疆姘存櫠
                if (stepDelay > 0) {
                    stepDelay--;
                    return;
                }

                // 姝ゆ椂鎵嬫寔宸茬粡鏄按鏅讹紙鍦?case 0 涓凡鍒囨崲锛?
                HitResult hit = mc.crosshairTarget;
                if (!(hit instanceof BlockHitResult baseHit)) {
                    resetState();
                    return;
                }

                if (baseHit.getType() != HitResult.Type.BLOCK) {
                    resetState();
                    return;
                }
                BlockPos base = baseHit.getBlockPos();

                // 妫€鏌ユ柟鍧楁槸鍚﹀凡缁忓彉鎴愰粦鏇滅煶锛堟垨鑰呭師鏈氨鏄熀宀╋級
                // 娉ㄦ剰锛氱敱浜庢槸 client-side 棰勬祴锛岃繖閲屽彲鑳借繕鏄┖姘旓紝浣?interactBlock 閫氬父鑳藉鐞?
                if (mc.world.getBlockState(base).isAir()) {
                    // 濡傛灉杩樻槸绌烘皵锛岃鏄庨粦鏇滅煶杩樻病鏀惧ソ锛屽彲浠ュ啀绛変竴 tick 鎴栬€呯洿鎺ュ皾璇曟斁缃按鏅讹紙棰勬祴锛?
                }

                // 鏋勯€犫€滅偣鍑绘柟鍧楅《闈⑩€濈殑鍛戒腑缁撴灉鐢ㄤ簬鏀剧疆姘存櫠
                BlockHitResult topHit = new BlockHitResult(
                        baseHit.getPos(),
                        Direction.UP,
                        base,
                        false
                );
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, topHit);
                mc.player.swingHand(Hand.MAIN_HAND);

                // 璁剧疆鎭㈠鍘熸墜鎸佸欢杩?1~2 tick
                stepDelay = 1 + random.nextInt(2);
                step = 2;
                break;

            case 2: // 鎭㈠鍘熸墜鎸?
                if (stepDelay > 0) {
                    stepDelay--;
                    return;
                }

                if (swapBack.get() && originalSlot != -1) {
                    mc.player.getInventory().setSelectedSlot(originalSlot);
                    mc.interactionManager.syncSelectedSlot();
                }

                // 瀹屾垚鍒囨崲锛岄噸缃姸鎬?
                resetState();
                break;
        }
    }

    private boolean handleObsidianPlace() {
        // 纭繚鐜╁姝ｅ湪鐪嬪悜涓€涓柟鍧?
        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof BlockHitResult)) return false;

        BlockHitResult blockHit = (BlockHitResult) hit;
        BlockPos base = blockHit.getBlockPos();

        // 绠€鍖栵細鐩存帴鏀剧疆榛戞洔鐭筹紝涓嶅啀妫€鏌ユ槸鍚﹀悎娉曠殑鏂瑰潡
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockHit);
        mc.player.swingHand(Hand.MAIN_HAND);

        return true;
    }

    private int findObsidianSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.OBSIDIAN)) return i;
        }
        return -1;
    }

    private int findEndCrystalSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.END_CRYSTAL)) return i;
        }
        return -1;
    }

    private void resetState() {
        if (swapBack.get() && originalSlot != -1) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
            mc.interactionManager.syncSelectedSlot();
        }
        step = 0;
        stepDelay = 0;
        originalSlot = -1;
        triggered = false;
    }
}


