package dev.mzc.client.module.impl.combat;

import dev.mzc.client.Sakura;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.entity.AttackEvent;
import dev.mzc.client.events.render.Render3DEvent;
import dev.mzc.client.mixin.accessor.IMinecraftClient;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.module.impl.client.Friend;
import dev.mzc.client.utils.client.ChatUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import dev.mzc.client.values.impl.RangeValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.Random;

public class AutoCombat extends Module {

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Random random = new Random();

    /* ================= 模式 ================= */

    public enum Mode {
        HighVersion("高版本"),   // 1.9+
        LowVersion("低版本")   // 1.8
        ;

        private final String cnName;

        Mode(String cnName) {
            this.cnName = cnName;
        }
    }

    public final EnumValue<Mode> mode =
            new EnumValue<>("Mode", Mode.HighVersion);

    // High Version Settings
    private final RangeValue<Double> reactionRange = new RangeValue<>("ReactionRange", 10.0, 120.0, 0.0, 500.0, 1.0, () -> mode.get() == Mode.HighVersion);

    // Low Version Settings
    private final RangeValue<Double> cpsRange = new RangeValue<>("CPSRange", 8.0, 12.0, 1.0, 20.0, 0.5, () -> mode.get() == Mode.LowVersion);

    /* ================= 通用设置 ================= */

    private final BoolValue teamCheck = new BoolValue("TeamCheck", true);
    private final BoolValue antiBot = new BoolValue("AntiBot", true);
    private final BoolValue usingPause = new BoolValue("UsingPause", true);
    private final BoolValue pauseInGui = new BoolValue("PauseInGui", true);
    private final BoolValue requireLeftButton = new BoolValue("RequireLeftButton", true);
    private final BoolValue CriticalFix = new BoolValue("CriticalFix", true, () -> mode.get() == Mode.HighVersion);
    private final BoolValue autoShieldBreak = new BoolValue("ShieldBreaker1.9+", true,
            () -> mode.get()== Mode.HighVersion);

    private int attackCooldown = 0;
    private int originalSlot = -1;
    private boolean needSwapBack = false;

    private long nextAttackTime = 0;
    private long nextModernAttackTime = 0;

    public AutoCombat() {
        super("AutoCombat", Category.Combat);
        this.setType(ModuleType.Safe);
    }

    @Override
    public void onEnable() {
        attackCooldown = 0;
        nextAttackTime = 0;
        nextModernAttackTime = 0;
        needSwapBack = false;
    }

    @Override
    public void onDisable() {
        if (needSwapBack && mc.player != null && originalSlot != -1) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
        }
        needSwapBack = false;
    }

    /* ================= Tick & Render ================= */

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.interactionManager == null) return;
        // tick logic removed
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.interactionManager == null) return;
        if (mode.get() == Mode.LowVersion) {
            handleLegacy();
        } else if (mode.get() == Mode.HighVersion) {
            handleModern();
        }
    }

    /* ================= 1.9+ 攻击 ================= */

    private void handleModern() {
        PlayerEntity p = mc.player;

        if (pauseInGui.get() && mc.currentScreen != null) {
            nextModernAttackTime = 0;
            return;
        }

        if (attackCooldown > 0) {
            attackCooldown--;
            if (needSwapBack && attackCooldown == 0) {
                p.getInventory().setSelectedSlot(originalSlot);
                needSwapBack = false;
            }
            return;
        }

        if (requireLeftButton.get() && !mc.options.attackKey.isPressed()) {
            nextModernAttackTime = 0;
            return;
        }
        if (p.getAttackCooldownProgress(0) < 1.0f) {
            nextModernAttackTime = 0;
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (nextModernAttackTime == 0) {
            double min = reactionRange.getMinValue();
            double max = reactionRange.getMaxValue();
            if (min > max) {
                double temp = min;
                min = max;
                max = temp;
            }
            long reactionDelay = (long) (min + (max - min) * random.nextDouble());
            nextModernAttackTime = currentTime + reactionDelay;
            return;
        }

        if (currentTime < nextModernAttackTime) {
            return;
        }

        if (usingPause.get() && p.isUsingItem()) {
            nextModernAttackTime = 0;
            return;
        }

        // 仅在暴击开关启用且玩家正在下落时进行攻击
        if (CriticalFix.get() && p.getVelocity().y > 0) {
            return; // 玩家处于上升状态或地面上，不能攻击
        }

        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof EntityHitResult ehr)) {
            nextModernAttackTime = 0;
            return;
        }

        Entity target = ehr.getEntity();
        if (!isValidTarget(target, p)) {
            nextModernAttackTime = 0;
            return;
        }

        // 自动破盾
        if (autoShieldBreak.get()
                && target instanceof PlayerEntity tp
                && tp.isBlocking()) {

            int before = p.getInventory().getSelectedSlot();
            if (switchToAxe()) {
                originalSlot = before;
                needSwapBack = true;
            }
        }

        ((IMinecraftClient) mc).hookSetAttackCooldown(0);
        mc.options.attackKey.setPressed(true);
        ((IMinecraftClient) mc).hookDoAttack();
        mc.options.attackKey.setPressed(false);
        Sakura.EVENT_BUS.post(new AttackEvent(target));

        attackCooldown = 10;
        nextModernAttackTime = 0;
    }

    /* ================= 1.8 连点 ================= */

    private void handleLegacy() {
        PlayerEntity p = mc.player;

        if (pauseInGui.get() && mc.currentScreen != null) {
            nextAttackTime = 0;
            return;
        }

        if (requireLeftButton.get() && !mc.options.attackKey.isPressed()) {
            nextAttackTime = 0;
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime < nextAttackTime) {
            return;
        }

        if (usingPause.get() && p.isUsingItem()) {
            return;
        }

        // Target acquisition
        Entity target = null;

        // Smart behavior always enabled: Use default crosshair target
        if (mc.crosshairTarget instanceof EntityHitResult ehr) {
            target = ehr.getEntity();
        }

        if (target == null) {
            // No valid target in range
            return;
        }

        if (!isValidTarget(target, p)) {
            return;
        }

        // Perform attack
        ((IMinecraftClient) mc).hookSetAttackCooldown(0);
        mc.options.attackKey.setPressed(true);
        ((IMinecraftClient) mc).hookDoAttack();
        mc.options.attackKey.setPressed(false);
        Sakura.EVENT_BUS.post(new AttackEvent(target));

        // Calculate next delay in milliseconds
        double min = cpsRange.getMinValue();
        double max = cpsRange.getMaxValue();
        if (min > max) {
            double temp = min;
            min = max;
            max = temp;
        }

        double cps = min + (max - min) * random.nextDouble();
        long delayMs = (long) (1000.0 / cps);
        nextAttackTime = currentTime + delayMs;
    }

    /* ================= 工具方法 ================= */

    private boolean switchToAxe() {
        PlayerEntity p = mc.player;
        PlayerInventory inv = p.getInventory();

        if (inv.getStack(inv.getSelectedSlot()).getItem() instanceof AxeItem) return false;

        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).getItem() instanceof AxeItem) {
                inv.setSelectedSlot(i);
                return true;
            }
        }
        return false;
    }

    private boolean isValidTarget(Entity target, PlayerEntity self) {
        if (target == self) return false;
        if (!target.isAlive()) return false;
        if (Sakura.MODULES.getModule(Friend.class).isFriend(target.getName().getString())) return false;
        if (antiBot.get() && AntiBot.isBot((PlayerEntity) target)) return false;
        if (teamCheck.get() && !isEnemy(target)) return false;
        return target instanceof PlayerEntity || target instanceof Monster;
    }

    private boolean isEnemy(Entity entity) {
        if (!teamCheck.get()) return true;
        if (!(entity instanceof PlayerEntity player)) return true;
        if (mc.player == null) return false;

        int myColor = getLeatherArmorColor(mc.player);
        int theirColor = getLeatherArmorColor(player);

        if (myColor == -1 || theirColor == -1) return true;
        return myColor != theirColor;
    }

    private int getLeatherArmorColor(PlayerEntity player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getEquippedStack(slot);
            DyedColorComponent dyed = stack.get(DataComponentTypes.DYED_COLOR);
            if (dyed != null) return dyed.rgb();
        }
        return -1;
    }
}
