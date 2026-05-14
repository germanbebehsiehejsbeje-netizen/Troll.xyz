package dev.mzc.client.module.impl.player.mine;

import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.render.Render3DEvent;
import dev.mzc.client.manager.Managers;
import dev.mzc.client.manager.impl.RotationManager;
import dev.mzc.client.Sakura;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.player.InvUtil;
import dev.mzc.client.utils.render.Render3DUtil;
import dev.mzc.client.utils.rotation.MovementFix;
import dev.mzc.client.utils.rotation.RotationUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.ColorValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Nucker extends Module {
    private final NumberValue<Double> range = new NumberValue<>("Range", 5.0, 1.0, 6.0, 0.1);
    private final BoolValue rotate = new BoolValue("Rotate", true);
    private final BoolValue autoSwap = new BoolValue("Auto Swap", true);
    private final BoolValue silentSwap = new BoolValue("Silent Swap", true, autoSwap::get);
    private final BoolValue usePacketMine = new BoolValue("PacketMine", false);
    private final BoolValue swing = new BoolValue("Swing", true);
    private final BoolValue avoidSelf = new BoolValue("Avoid Self", true);
    private final BoolValue city = new BoolValue("City", true);
    private final NumberValue<Integer> targetRange = new NumberValue<>("Target Range", 10, 1, 20, 1, city::get);
    
    private final BoolValue render = new BoolValue("Render", true);
    private final BoolValue renderText = new BoolValue("Render Text", true, render::get);
    private final EnumValue<RenderMode> renderMode = new EnumValue<>("Render Mode", RenderMode.Zoom, render::get);

    private final ColorValue renderColor = new ColorValue("Render Color", new Color(255, 0, 0, 50), render::get);
    private final ColorValue readyColor = new ColorValue("Ready Color", new Color(0, 255, 0, 50), render::get);

    private BlockPos currentTarget = null;
    private long startTime = -1;
    private boolean mining = false;

    public enum RenderMode {
        Normal, Zoom
    }

    public Nucker() {
        super("Nucker", Category.Player);
        this.setType(ModuleType.Hack);
    }

    @Override
    public void onDisable() {
        resetMining();
    }

    private void resetMining() {
        if (mining && currentTarget != null) {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, currentTarget, Direction.UP));
        }
        currentTarget = null;
        startTime = -1;
        mining = false;
    }

    @EventHandler
    public void onTick(TickEvent.Post event) {
        if (nullCheck()) return;

        if (currentTarget != null) {
            // 妫€鏌ョ洰鏍囨槸鍚﹀け鏁?
            if (mc.world.isAir(currentTarget) || mc.player.squaredDistanceTo(currentTarget.toCenterPos()) > Math.pow(range.get(), 2)) {
                resetMining();
            }
        }

        if (currentTarget == null) {
            findTarget();
        }

        if (currentTarget != null) {
            handleMining();
        }
    }

    private void findTarget() {
        List<BlockPos> targets = new ArrayList<>();

        // 鑷姩鐮寸敳閫昏緫
        if (city.get()) {
            mc.world.getPlayers().stream()
                    .filter(p -> p != mc.player && p.isAlive() && mc.player.distanceTo(p) <= targetRange.get())
                    .forEach(p -> {
                        BlockPos pos = p.getBlockPos();
                        BlockPos[] surround = {
                                pos.north(), pos.south(), pos.east(), pos.west()
                        };
                        for (BlockPos neighbor : surround) {
                            if (mc.player.squaredDistanceTo(neighbor.toCenterPos()) <= Math.pow(range.get(), 2) && canMine(neighbor)) {
                                targets.add(neighbor);
                            }
                        }
                    });
        }

        // 鍩虹鏂瑰潡鎸栨帢閫昏緫
        int r = range.get().intValue() + 1; // 绋嶅井鎵╁ぇ鎼滅储鍗婂緞浠ョ‘淇濊鐩?
        BlockPos playerPos = mc.player.getBlockPos();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    
                    // 璺濈妫€鏌?
                    if (mc.player.squaredDistanceTo(pos.toCenterPos()) > Math.pow(range.get(), 2)) continue;

                    // 涓ユ牸闄愬埗锛氬彧鎸栨帢鐜╁ Y 鍧愭爣鍙婁互涓婄殑鏂瑰潡
                    if (pos.getY() < playerPos.getY()) continue;

                    // 绮剧‘鎺掗櫎鐜╁鑴氫笅鍧愭爣 (閬垮厤璇Е)
                    if (avoidSelf.get()) {
                        if (pos.getX() == playerPos.getX() && pos.getZ() == playerPos.getZ() && pos.getY() == playerPos.getY()) {
                            continue;
                        }
                    }
                    
                    if (canMine(pos)) targets.add(pos);
                }
            }
        }

        if (!targets.isEmpty()) {
            // 鎸夌収璺濈鎺掑簭锛屼紭鍏堟寲鎺樻渶杩戠殑
            targets.sort((a, b) -> Double.compare(mc.player.squaredDistanceTo(a.toCenterPos()), mc.player.squaredDistanceTo(b.toCenterPos())));
            currentTarget = targets.get(0);
            startTime = System.currentTimeMillis();
            mining = false;
        }
    }

    private boolean canMine(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return !state.isAir() && state.getHardness(mc.world, pos) != -1.0F;
    }

    private void handleMining() {
        if (currentTarget == null) return;

        // 鑱斿姩 PacketMine
        if (usePacketMine.get()) {
            PacketMine packetMine = Sakura.MODULES.getModule(PacketMine.class);
            if (packetMine != null) {
                if (!packetMine.isEnabled()) {
                    packetMine.setState(true);
                }
                
                // 妫€鏌ユ槸鍚﹀凡缁忔槸褰撳墠鎸栨帢鐩爣锛岄伩鍏嶉噸澶嶉噸缃?
                BlockData data = packetMine.getBlockData();
                if (data == null || !data.getCurrentPos().equals(currentTarget)) {
                    packetMine.hookPos(currentTarget, true);
                }
                
                resetMining(); // 绉讳氦缁?PacketMine 澶勭悊鍚庨噸缃?
                return;
            }
        }

        // 鑷姩鐬勫噯
        if (rotate.get()) {
            Managers.ROTATION.setRotations(RotationUtil.calculate(currentTarget.toCenterPos()), 10, MovementFix.OFF, RotationManager.Priority.Medium);
        }

        // 寮€濮嬫寲鎺?
        if (!mining) {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, currentTarget, Direction.UP));
            if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);
            mining = true;
        }

        // 鎸栨帢杩囩▼涓殑鎸ユ墜鍔ㄧ敾
        if (swing.get() && mc.player.age % 4 == 0) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }

        long breakTime = (long) calcBreakTime(currentTarget);
        if (System.currentTimeMillis() - startTime >= breakTime) {
            int oldSlot = mc.player.getInventory().getSelectedSlot();
            int bestSlot = -1;

            if (autoSwap.get()) {
                bestSlot = InvUtil.findFastestTool(mc.world.getBlockState(currentTarget), false).slot();
                if (bestSlot != -1 && bestSlot != oldSlot) {
                    if (silentSwap.get()) {
                        InvUtil.swap(bestSlot, true);
                    } else {
                        mc.player.getInventory().setSelectedSlot(bestSlot);
                    }
                }
            }

            // 瀹屾垚鎸栨帢
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, currentTarget, Direction.UP));
            if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);
            
            if (autoSwap.get() && silentSwap.get() && bestSlot != -1) {
                InvUtil.swapBack();
            }
            
            resetMining(); // 閲嶇疆浠ュ鎵句笅涓€涓洰鏍?
        }
    }

    private float calcBreakTime(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        float hardness = state.getHardness(mc.world, pos);
        float breakSpeed = getBreakSpeed(state);
        if (breakSpeed == -1.0f) return -1.0f;
        float relativeDamage = breakSpeed / hardness / 30.0f;
        int ticks = MathHelper.ceil(0.7f / relativeDamage);
        return ticks * 50.0f;
    }

    private float getBreakSpeed(BlockState blockState) {
        float maxSpeed = 1.0f;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            float speed = stack.getMiningSpeedMultiplier(blockState);
            if (speed > 1.0f) {
                var enchantmentRegistry = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
                RegistryEntry<Enchantment> efficiencyEntry = enchantmentRegistry.getOrThrow(Enchantments.EFFICIENCY);
                int efficiencyLevel = EnchantmentHelper.getLevel(efficiencyEntry, stack);
                if (efficiencyLevel > 0) {
                    speed += (float) (efficiencyLevel * efficiencyLevel + 1);
                }
                if (speed > maxSpeed) maxSpeed = speed;
            }
        }
        return maxSpeed;
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!render.get()) return;
        if (currentTarget != null) {
            long breakTime = (long) calcBreakTime(currentTarget);
            double progress = (double) (System.currentTimeMillis() - startTime) / breakTime;
            progress = MathHelper.clamp(progress, 0.0, 1.0);

            Box box = new Box(currentTarget);
            
            if (renderMode.get() == RenderMode.Zoom) {
                box = box.expand((progress - 1.0) / 2.0);
            }
            
            // 瀹炵幇骞虫粦棰滆壊娓愬彉锛氫粠 renderColor 鍒?readyColor
            Color c1 = renderColor.get();
            Color c2 = readyColor.get();
            int r = (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * progress);
            int g = (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * progress);
            int b = (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * progress);
            int a = (int) (c1.getAlpha() + (c2.getAlpha() - c1.getAlpha()) * progress);
            Color color = new Color(r, g, b, a);
            
            Render3DUtil.drawFilledBox(event.getMatrices(), box, color);
            Render3DUtil.drawBoxOutline(event.getMatrices(), box, color.getRGB(), 1.0f);
            
            if (renderText.get()) {
                Vec3d center = box.getCenter();
                Render3DUtil.drawText(String.format("%.0f%%", progress * 100), center, 0, 0, 0, Color.WHITE);
            }
        }
    }
}



