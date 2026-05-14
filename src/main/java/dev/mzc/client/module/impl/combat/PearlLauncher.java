package dev.mzc.client.module.impl.combat;

import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.mixin.accessor.IMinecraftClient;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.TranslationManager;
import dev.mzc.client.utils.client.ChatUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.Optional;

public class PearlLauncher extends Module {

    public enum Mode {
        Vertical(),
        LongDistance(),
        Custom()
        ;
        Mode() { }
    }

    private final EnumValue<Mode> mode = new EnumValue<>("Mode", Mode.Vertical);
    private final NumberValue<Double> customPitch = new NumberValue<>("CustomPitch", -90.0, -90.0, 90.0, 1.0, () -> mode.get() == Mode.Custom);
    private final NumberValue<Integer> boostDelay = new NumberValue<>("BoostDelay", 2, 1, 20, 1);
    private final NumberValue<Integer> boostCount = new NumberValue<>("BoostCount", 1, 1, 5, 1);
    private final BoolValue debug = new BoolValue("Debug", false);

    private int timer = 0;
    private int originalSlot = -1;
    private int state = 0; // 0: Idle, 1: Throwing Pearl, 2: Waiting, 3: Boosting

    // 记录投掷时的变量
    private Vec3d startPos;
    private Vec3d pearlVel;
    private Vec3d playerVelAtThrow;
    private float startYaw;
    private int pearlEntityId = -1;

    public PearlLauncher() {
        super("PearlLauncher", Category.Combat);
        this.setType(ModuleType.Safe);
    }

    @Override
    public void onEnable() {
        if (nullCheck()) {
            this.toggle();
            return;
        }

        int pearlSlot = findItem(Items.ENDER_PEARL);
        int windChargeSlot = findItem(Items.WIND_CHARGE);

        if (pearlSlot == -1 || windChargeSlot == -1) {
            ChatUtil.addChatMessage("§c缺少末影珍珠或风弹！");
            this.toggle();
            return;
        }

        originalSlot = mc.player.getInventory().getSelectedSlot();
        timer = 0;
        state = 1;
        pearlEntityId = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (nullCheck() || state == 0) return;

        // 尝试寻找珍珠实体
        if (state >= 2 && pearlEntityId == -1) {
            Optional<EnderPearlEntity> pearl = mc.world.getEntitiesByClass(EnderPearlEntity.class, mc.player.getBoundingBox().expand(10), e -> e.getOwner() == mc.player)
                    .stream().min(Comparator.comparingDouble(e -> e.squaredDistanceTo(mc.player)));
            if (pearl.isPresent()) {
                pearlEntityId = pearl.get().getId();
                if (debug.get()) ChatUtil.addChatMessage("§a[Debug] 找到珍珠实体 ID: " + pearlEntityId);
            }
        }

        switch (state) {
            case 1: // Throw Pearl
                int pearlSlot = findItem(Items.ENDER_PEARL);
                if (pearlSlot != -1) {
                    mc.player.getInventory().setSelectedSlot(pearlSlot);
                    
                    startYaw = mc.player.getYaw();
                    float pitch = -90.0f;
                    if (mode.get() == Mode.LongDistance) pitch = -45.0f;
                    else if (mode.get() == Mode.Custom) pitch = customPitch.get().floatValue();
                    
                    mc.player.setYaw(startYaw);
                    mc.player.setPitch(pitch);

                    startPos = mc.player.getEyePos().subtract(0, 0.1, 0);
                    playerVelAtThrow = mc.player.getVelocity();
                    
                    float f = -MathHelper.sin(startYaw * 0.017453292F) * MathHelper.cos(pitch * 0.017453292F);
                    float g = -MathHelper.sin(pitch * 0.017453292F);
                    float h = MathHelper.cos(startYaw * 0.017453292F) * MathHelper.cos(pitch * 0.017453292F);
                    Vec3d dir = new Vec3d(f, g, h).normalize();
                    
                    pearlVel = dir.multiply(1.5).add(playerVelAtThrow);
                    
                    if (debug.get()) {
                        ChatUtil.addChatMessage(String.format("§a[Debug] 投掷珍珠: §f玩家速度[%.3f, %.3f, %.3f]", 
                            playerVelAtThrow.x, playerVelAtThrow.y, playerVelAtThrow.z));
                    }

                    useItem();
                    state = 2;
                    timer = 0;
                } else {
                    stop();
                }
                break;

            case 2: // Waiting
                timer++;
                if (timer >= boostDelay.get()) {
                    state = 3;
                    timer = 0;
                }
                break;

            case 3: // Boosting
                int windChargeSlot = findItem(Items.WIND_CHARGE);
                if (windChargeSlot != -1) {
                    mc.player.getInventory().setSelectedSlot(windChargeSlot);
                    
                    // 获取当前珍珠状态
                    Vec3d currentPearlPos = startPos;
                    Vec3d currentPearlVel = pearlVel;
                    int remainingStartTick = boostDelay.get(); // 如果没找到实体，按初始投掷点预测

                    if (pearlEntityId != -1) {
                        Entity pearl = mc.world.getEntityById(pearlEntityId);
                        if (pearl != null) {
                            currentPearlPos = new Vec3d(pearl.getX(), pearl.getY(), pearl.getZ());
                            currentPearlVel = pearl.getVelocity();
                            remainingStartTick = 0; // 从当前位置开始预测
                        }
                    }

                    // 重新计算交汇点 (基于最新数据)
                    IntersectionResult result = findIntersection(currentPearlPos, currentPearlVel, remainingStartTick);
                    Vec3d intersection = result.pos;
                    
                    // 动量补偿瞄准：抵消玩家移动对风弹的影响
                    // 目标方向 = (交汇点 - 玩家位置) / 预计飞行时间 - 玩家当前速度
                    Vec3d eyePos = mc.player.getEyePos();
                    Vec3d playerVel = mc.player.getVelocity();
                    
                    // 计算需要抵消动量的理想发射向量
                    // 公式：V_wind = (Target - Start) / T - V_player
                    double t = Math.max(1, result.travelTicks);
                    Vec3d neededDir = intersection.subtract(eyePos).multiply(1.0 / t).subtract(playerVel);
                    
                    float[] rots = getRotationFromVector(neededDir);
                    mc.player.setYaw(rots[0]);
                    mc.player.setPitch(rots[1]);
                    
                    if (debug.get()) {
                        double movedDist = eyePos.distanceTo(startPos);
                        String entityInfo = "未追踪到实体";
                        if (pearlEntityId != -1) {
                            Entity pearl = mc.world.getEntityById(pearlEntityId);
                            if (pearl != null) {
                                Vec3d pPos = new Vec3d(pearl.getX(), pearl.getY(), pearl.getZ());
                                double diff = pPos.distanceTo(intersection);
                                entityInfo = String.format("偏差: %.3f", diff);
                            }
                        }
                        ChatUtil.addChatMessage(String.format("§b[Debug] 玩家位移: %.2f, 预计飞行: %d Tick, %s", 
                            movedDist, result.travelTicks, entityInfo));
                    }

                    useItem();
                    
                    timer++;
                    if (timer >= boostCount.get()) {
                        stop();
                    }
                } else {
                    stop();
                }
                break;
        }
    }

    private static class IntersectionResult {
        Vec3d pos;
        int travelTicks;
        IntersectionResult(Vec3d pos, int travelTicks) { this.pos = pos; this.travelTicks = travelTicks; }
    }

    private IntersectionResult findIntersection(Vec3d pearlStart, Vec3d pearlVel, int startTick) {
        Vec3d eyePos = mc.player.getEyePos();
        double windChargeSpeed = 1.5; 
        
        int travelTicks = 1;
        Vec3d targetPos = predictPearlPos(pearlStart, pearlVel, startTick + travelTicks);

        for (int i = 0; i < 5; i++) {
            double distance = eyePos.distanceTo(targetPos);
            travelTicks = (int) Math.ceil(distance / windChargeSpeed);
            targetPos = predictPearlPos(pearlStart, pearlVel, startTick + travelTicks);
        }
        
        return new IntersectionResult(targetPos, travelTicks);
    }

    /**
     * 预测珍珠在 n 个 tick 后的位置
     */
    private Vec3d predictPearlPos(Vec3d start, Vec3d velocity, int ticks) {
        double x = start.x;
        double y = start.y;
        double z = start.z;
        double vx = velocity.x;
        double vy = velocity.y;
        double vz = velocity.z;

        for (int i = 0; i < ticks; i++) {
            x += vx;
            y += vy;
            z += vz;
            
            vx *= 0.99;
            vy *= 0.99;
            vz *= 0.99;
            vy -= 0.03;
        }
        
        return new Vec3d(x, y, z);
    }

    private float[] getRotationFromVector(Vec3d vec) {
        double diffX = vec.x;
        double diffY = vec.y;
        double diffZ = vec.z;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(diffY, diffXZ)));
        return new float[]{MathHelper.wrapDegrees(yaw), MathHelper.wrapDegrees(pitch)};
    }

    private void useItem() {
        ((IMinecraftClient) mc).hookSetItemUseCooldown(0);
        mc.options.useKey.setPressed(true);
        ((IMinecraftClient) mc).hookDoItemUse();
        mc.options.useKey.setPressed(false);
    }

    private void stop() {
        if (originalSlot != -1 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
        }
        state = 0;
        this.toggle();
    }

    private int findItem(net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == item) {
                return i;
            }
        }
        return -1;
    }
}
