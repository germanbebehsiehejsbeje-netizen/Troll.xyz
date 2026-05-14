package dev.mzc.client.module.impl.combat;

import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.manager.Managers;
import dev.mzc.client.manager.impl.RotationManager;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.player.FindItemResult;
import dev.mzc.client.utils.player.InvUtil;
import dev.mzc.client.utils.rotation.MovementFix;
import dev.mzc.client.utils.rotation.RotationUtil;
import dev.mzc.client.utils.vector.Rotation;
import dev.mzc.client.utils.world.BlockUtil;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;

import java.util.Comparator;

public class CrystalBlocker extends Module {
    private final NumberValue<Double> range = new NumberValue<>("Range", 4.0, 1.0, 6.0, 0.1);
    private final EnumValue<RotateMode> rotate = new EnumValue<>("Rotate", RotateMode.Silent);
    private final EnumValue<SwitchMode> switchMode = new EnumValue<>("Switch", SwitchMode.Visible);
    private final NumberValue<Integer> delay = new NumberValue<>("Delay", 2, 0, 20, 1);
    private final NumberValue<Integer> visibleSwapBackDelay = new NumberValue<>("SwapBackDelay", 0, 0, 20, 1, () -> switchMode.is(SwitchMode.Visible));
    private final NumberValue<Double> silentSpeed = new NumberValue<>("SilentSpeed", 10.0, 0.5, 20.0, 0.1, () -> rotate.is(RotateMode.Silent));

    private int timer = 0;
    private boolean waitingSwapBack = false;
    private int swapBackTicks = 0;
    private int savedOldSlot = -1;

    public enum RotateMode {
        None, Normal, Silent
    }
    public enum SwitchMode {
        Visible, Silent
    }

    public CrystalBlocker() {
        super("CrystalBlocker", Category.Combat);
        this.setType(ModuleType.Safe);
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        if (!mc.player.isOnGround()) return;

        if (waitingSwapBack) {
            if (swapBackTicks > 0) {
                swapBackTicks--;
                if (swapBackTicks > 0) return;
            }
            if (savedOldSlot >= 0) {
                mc.player.getInventory().setSelectedSlot(savedOldSlot);
                if (mc.interactionManager != null) mc.interactionManager.syncSelectedSlot();
            }
            waitingSwapBack = false;
            savedOldSlot = -1;
            return;
        }

        if (timer > 0) {
            timer--;
        }

        // 1. 瀵绘壘鍛ㄥ洿 4x4 鑼冨洿鍐呯殑姘存櫠
        EndCrystalEntity targetCrystal = mc.world.getEntitiesByClass(EndCrystalEntity.class, 
                mc.player.getBoundingBox().expand(range.get()), 
                crystal -> {
                    // 妫€鏌ユ槸鍚﹀湪鍚屼竴楂樺害 (Y 鍧愭爣宸€煎皬浜?1)
                    return Math.abs(crystal.getY() - mc.player.getY()) < 1.0;
                })
                .stream()
                .min(Comparator.comparingDouble(c -> mc.player.distanceTo(c)))
                .orElse(null);

        if (targetCrystal == null) return;

        // 2.1 濡傛灉鐜╁涓庢按鏅朵箣闂村凡鏈夐粦鏇滅煶闃绘尅锛屽垯涓嶅啀鏀剧疆
        Vec3d eyes = mc.player.getEyePos();
        Vec3d towards = targetCrystal.getEntityPos().add(0, 0.5, 0);
        HitResult hit = mc.world.raycast(new RaycastContext(
                eyes,
                towards,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = ((BlockHitResult) hit).getBlockPos();
            if (mc.world.getBlockState(hitPos).isOf(Blocks.OBSIDIAN)) return;
        }

        // 2. 璁＄畻鐜╁鍜屾按鏅朵腑闂寸殑鏂瑰潡浣嶇疆
        Vec3d crystalPos = targetCrystal.getEntityPos();
        Vec3d playerPos = mc.player.getEntityPos();
        
        // 鍙栦腑闂寸偣
        Vec3d midPoint = playerPos.lerp(crystalPos, 0.5);
        BlockPos placePos = BlockPos.ofFloored(midPoint).withY(mc.player.getBlockY());

        // 濡傛灉涓棿鐐瑰氨鏄帺瀹惰剼涓嬫垨姘存櫠鑴氫笅锛屽皾璇曞鎵炬洿鍚堥€傜殑闃绘尅鐐?
        if (placePos.equals(mc.player.getBlockPos()) || placePos.equals(targetCrystal.getBlockPos())) {
             // 绠€鍗曠殑鏂瑰悜鍋忕Щ瀵绘壘
             Vec3d dir = crystalPos.subtract(playerPos).normalize();
             placePos = BlockPos.ofFloored(playerPos.add(dir)).withY(mc.player.getBlockY());
        }

        // 3. 妫€鏌ユ槸鍚﹀彲浠ユ斁缃?
        if (!BlockUtil.canPlaceAt(placePos)) return;
        if (!BlockUtil.solid(placePos.down())) return;

        // 4. 瀵绘壘榛戞洔鐭?
        FindItemResult obsidian = InvUtil.findInHotbar(Items.OBSIDIAN);
        if (!obsidian.found()) return;

        // 5. 鏃嬭浆涓庢斁缃€昏緫
        Direction side = BlockUtil.getPlaceSide(placePos);
        if (side == null) return;

        // 璁＄畻鏃嬭浆
        Rotation rot = RotationUtil.calculate(placePos);
        boolean readyToPlace = true;

        if (rotate.get() == RotateMode.Normal) {
            Rotation patched = RotationUtil.applySensitivityPatch(rot);
            float clampedPitch = MathHelper.clamp(patched.pitch, -90.0f, 90.0f);
            mc.player.setYaw(patched.yaw);
            mc.player.setHeadYaw(patched.yaw);
            mc.player.setPitch(clampedPitch);
        } else if (rotate.get() == RotateMode.Silent) {
            // 姣?tick 閮芥洿鏂版棆杞紝淇濇寔骞虫粦
            // MovementFix.GRIM 浼氬湪闈欓粯鏃嬭浆鏃朵慨姝ｇЩ鍔ㄦ柟鍚戯紝閬垮厤琚湇鍔″櫒鎷夊洖
            Managers.ROTATION.setRotations(rot, silentSpeed.get(), MovementFix.GRIM, RotationManager.Priority.Highest);
            
            // 妫€鏌ュ綋鍓嶆棆杞槸鍚﹀凡缁忚冻澶熸帴杩戠洰鏍?
            if (Managers.ROTATION.rotations != null) {
                double yawDiff = Math.abs(MathHelper.wrapDegrees(Managers.ROTATION.rotations.yaw - rot.yaw));
                double pitchDiff = Math.abs(Managers.ROTATION.rotations.pitch - rot.pitch);
                // 濡傛灉瑙掑害宸紓杩囧ぇ锛屾殏鍋滄斁缃紝绛夊緟鏃嬭浆瀵归綈
                if (yawDiff > 15 || pitchDiff > 15) {
                    readyToPlace = false;
                }
            }
        }

        // 鎵ц鏀剧疆
        if (readyToPlace && timer <= 0) {
            placeBlock(placePos, obsidian, side);
            timer = delay.get();
        }
    }

    private void placeBlock(BlockPos pos, FindItemResult item, Direction side) {
        // 鍒囨崲鐗╁搧
        int oldSlot = mc.player.getInventory().getSelectedSlot();
        if (switchMode.get() == SwitchMode.Visible) {
            int target = item.slot();
            boolean needSwitch = oldSlot != target;
            if (needSwitch) {
                mc.player.getInventory().setSelectedSlot(target);
                mc.interactionManager.syncSelectedSlot();
            }
            BlockUtil.clickBlock(pos.offset(side), side.getOpposite(), false, false);
            mc.player.swingHand(Hand.MAIN_HAND);
            if (needSwitch) {
                int backDelay = visibleSwapBackDelay.get();
                if (backDelay > 0) {
                    waitingSwapBack = true;
                    swapBackTicks = backDelay;
                    savedOldSlot = oldSlot;
                } else {
                    mc.player.getInventory().setSelectedSlot(oldSlot);
                    mc.interactionManager.syncSelectedSlot();
                }
            }
        } else {
            boolean swapped = InvUtil.invSwap(item.slot());
            if (!swapped) return;
            BlockUtil.clickBlock(pos.offset(side), side.getOpposite(), false, true);
            mc.player.swingHand(Hand.MAIN_HAND);
            InvUtil.invSwapBack();
        }
    }
}


