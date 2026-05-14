package dev.mzc.client.module.impl.client;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import dev.mzc.client.Sakura;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.manager.Managers;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.module.impl.combat.KillAura;
import dev.mzc.client.module.impl.combat.MutiAura;
import dev.mzc.client.module.impl.player.AutoEat;
import dev.mzc.client.module.impl.player.mine.AutoMine;
import dev.mzc.client.utils.client.ChatUtil;
import dev.mzc.client.utils.player.InvUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import dev.mzc.client.values.impl.StringValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.ItemTags;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BaritoneControl extends Module {
    private final BoolValue fixViewJitter = new BoolValue("FixViewJitter", true);
    private final BoolValue enableFreeLook = new BoolValue("FreeLook", true, fixViewJitter::get);
    private final BoolValue enableSmoothLook = new BoolValue("SmoothLook", true, fixViewJitter::get);
    private final NumberValue<Integer> smoothLookTicks = new NumberValue<>("SmoothLookTicks", 8, 1, 40, 1, () -> fixViewJitter.get() && enableSmoothLook.get());

    private final BoolValue autoPause = new BoolValue("AutoPause", true);
    private final BoolValue pauseOnLowDurability = new BoolValue("PauseOnLowDurability", true, autoPause::get);
    private final NumberValue<Integer> minPickaxeDurability = new NumberValue<>("MinDurability", 20, 1, 200, 1, () -> autoPause.get() && pauseOnLowDurability.get());
    private final BoolValue replacePickaxeOnLowDurability = new BoolValue("ReplacePickaxe", true, () -> autoPause.get() && pauseOnLowDurability.get());
    private final BoolValue pauseOnLowFood = new BoolValue("PauseOnLowFood", true, autoPause::get);
    private final NumberValue<Integer> minFoodLevel = new NumberValue<>("MinFood", 8, 0, 20, 1, () -> autoPause.get() && pauseOnLowFood.get());
    private final BoolValue pauseOnLowHealth = new BoolValue("PauseOnLowHealth", true, autoPause::get);
    private final NumberValue<Double> minHealth = new NumberValue<>("MinHealth", 10.0, 1.0, 20.0, 0.5, () -> autoPause.get() && pauseOnLowHealth.get());
    private final BoolValue pauseOnAutoEat = new BoolValue("PauseOnAutoEat", true, autoPause::get);
    private final BoolValue pauseOnAura = new BoolValue("PauseOnAura", true, autoPause::get);

    private final BoolValue antiPlayer = new BoolValue("AntiPlayer", false);
    private final NumberValue<Double> antiPlayerRange = new NumberValue<>("AntiPlayerRange", 16.0, 1.0, 128.0, 1.0, antiPlayer::get);
    private final BoolValue antiPlayerAutoLog = new BoolValue("AntiPlayerAutoLog", true, antiPlayer::get);
    private final BoolValue antiPlayerAutoMessage = new BoolValue("AntiPlayerAutoMessage", false, antiPlayer::get);
    private final StringValue antiPlayerMessage = new StringValue("AntiPlayerMessage", "别靠近我", () -> antiPlayer.get() && antiPlayerAutoMessage.get());
    private final NumberValue<Integer> antiPlayerMsgCooldownSec = new NumberValue<>("AntiPlayerMsgCooldown", 60, 1, 600, 1, () -> antiPlayer.get() && antiPlayerAutoMessage.get());
    private final BoolValue antiPlayerIgnoreFriends = new BoolValue("AntiPlayerIgnoreFriends", true, antiPlayer::get);

    private boolean paused;
    private String pauseReason;
    private boolean auraPaused;
    private int auraPrevSlot = -1;
    private int auraSwordSlot = -1;
    private long lastPickaxeReplaceMs;

    private final Map<UUID, Long> lastWhisper = new HashMap<>();
    private final Map<UUID, Boolean> wasInRange = new HashMap<>();
    private boolean pendingAntiPlayerLog;
    private int pendingAntiPlayerLogTicks;

    private boolean savedBaritoneView;
    private Boolean savedFreeLook;
    private Boolean savedBlockFreeLook;
    private Boolean savedSmoothLook;
    private Integer savedSmoothLookTicks;
    private Double savedRandomLooking;
    private Double savedRandomLooking113;

    public BaritoneControl() {
        super("BaritoneControl", Category.Client);
        this.setType(ModuleType.All);
    }

    @Override
    protected void onEnable() {
        paused = false;
        pauseReason = null;
        auraPaused = false;
        auraPrevSlot = -1;
        auraSwordSlot = -1;
        lastPickaxeReplaceMs = 0L;
        lastWhisper.clear();
        wasInRange.clear();
        pendingAntiPlayerLog = false;
        pendingAntiPlayerLogTicks = 0;
        savedBaritoneView = false;
        setSuffix("");
    }

    @Override
    protected void onDisable() {
        paused = false;
        pauseReason = null;
        if (auraPaused) {
            restoreAuraSlot();
        }
        auraPaused = false;
        auraPrevSlot = -1;
        auraSwordSlot = -1;
        lastWhisper.clear();
        wasInRange.clear();
        pendingAntiPlayerLog = false;
        pendingAntiPlayerLogTicks = 0;
        restoreBaritoneView();
        setSuffix("");
    }

    public boolean isPaused() {
        return isEnabled() && paused;
    }

    public String getPauseReason() {
        return pauseReason;
    }

    @EventHandler
    public void onTick(TickEvent.Post event) {
        if (nullCheck()) return;

        if (!isAnyBaritoneFeatureEnabled()) {
            if (paused) {
                paused = false;
                pauseReason = null;
                if (auraPaused) {
                    restoreAuraSlot();
                    auraPaused = false;
                }
                setSuffix("");
            }
            pendingAntiPlayerLog = false;
            pendingAntiPlayerLogTicks = 0;
            restoreBaritoneView();
            return;
        }

        applyBaritoneView();

        if (pendingAntiPlayerLog) {
            pendingAntiPlayerLogTicks--;
            if (pendingAntiPlayerLogTicks <= 0) {
                cancelBaritone();
                if (mc.world != null) {
                    mc.world.disconnect(net.minecraft.text.Text.of("Disconnected by BaritoneControl"));
                    mc.setScreen(new TitleScreen());
                }
                pendingAntiPlayerLog = false;
                return;
            }
        }

        if (antiPlayer.get()) {
            if (handleAntiPlayer()) return;
        }

        if (!autoPause.get()) {
            if (paused) {
                paused = false;
                pauseReason = null;
                if (auraPaused) {
                    restoreAuraSlot();
                    auraPaused = false;
                }
                ChatUtil.addChatMessage("§aBaritone设置: 继续运行");
                setSuffix("");
            }
            return;
        }

        String reason = computePauseReason();
        if (reason != null) {
            if (!paused || pauseReason == null || !pauseReason.equals(reason)) {
                paused = true;
                pauseReason = reason;
                cancelBaritone();
                if (reason.startsWith("Aura:")) {
                    auraPaused = true;
                    ensureSwordSelected();
                }
                ChatUtil.addChatMessage("§eBaritone设置: 暂停: " + reason);
                setSuffix("PAUSE");
            }
            return;
        }

        if (paused) {
            paused = false;
            pauseReason = null;
            if (auraPaused) {
                restoreAuraSlot();
                auraPaused = false;
            }
            ChatUtil.addChatMessage("§aBaritone设置: 继续运行");
            setSuffix("");
        }
    }

    private boolean isAnyBaritoneFeatureEnabled() {
        AutoMine am = Sakura.MODULES.getModule(AutoMine.class);
        return am != null && am.isEnabled();
    }

    private void applyBaritoneView() {
        if (!fixViewJitter.get()) {
            restoreBaritoneView();
            return;
        }

        try {
            var s = BaritoneAPI.getSettings();

            if (!savedBaritoneView) {
                savedBaritoneView = true;
                savedFreeLook = s.freeLook.value;
                savedBlockFreeLook = s.blockFreeLook.value;
                savedSmoothLook = s.smoothLook.value;
                savedSmoothLookTicks = s.smoothLookTicks.value;
                savedRandomLooking = s.randomLooking.value;
                savedRandomLooking113 = s.randomLooking113.value;
            }

            if (enableFreeLook.get()) {
                s.freeLook.value = true;
                s.blockFreeLook.value = true;
            }
            if (enableSmoothLook.get()) {
                s.smoothLook.value = true;
                s.smoothLookTicks.value = smoothLookTicks.get();
            }
            s.randomLooking.value = 0.0;
            s.randomLooking113.value = 0.0;
        } catch (Throwable ignored) {
        }
    }

    private void restoreBaritoneView() {
        if (!savedBaritoneView) return;
        savedBaritoneView = false;

        try {
            var s = BaritoneAPI.getSettings();
            if (savedFreeLook != null) s.freeLook.value = savedFreeLook;
            if (savedBlockFreeLook != null) s.blockFreeLook.value = savedBlockFreeLook;
            if (savedSmoothLook != null) s.smoothLook.value = savedSmoothLook;
            if (savedSmoothLookTicks != null) s.smoothLookTicks.value = savedSmoothLookTicks;
            if (savedRandomLooking != null) s.randomLooking.value = savedRandomLooking;
            if (savedRandomLooking113 != null) s.randomLooking113.value = savedRandomLooking113;
        } catch (Throwable ignored) {
        }

        savedFreeLook = null;
        savedBlockFreeLook = null;
        savedSmoothLook = null;
        savedSmoothLookTicks = null;
        savedRandomLooking = null;
        savedRandomLooking113 = null;
    }

    private void cancelBaritone() {
        try {
            IBaritone b = BaritoneAPI.getProvider().getPrimaryBaritone();
            b.getMineProcess().cancel();
            b.getPathingBehavior().cancelEverything();
        } catch (Throwable ignored) {
        }
    }

    private String computePauseReason() {
        if (pauseOnAura.get() && auraHasTarget()) return "Aura:检测到目标";

        AutoEat ae = Sakura.MODULES.getModule(AutoEat.class);
        if (pauseOnAutoEat.get() && ae != null && ae.isEnabled()) {
            if (mc.player.isUsingItem()) {
                ItemStack active = mc.player.getActiveItem();
                if (active != null && active.get(DataComponentTypes.FOOD) != null) {
                    return "AutoEat进食中";
                }
            }
        }

        if (pauseOnLowHealth.get() && mc.player.getHealth() < minHealth.get()) return "血量过低";

        int food = mc.player.getHungerManager().getFoodLevel();
        if (pauseOnLowFood.get() && food < minFoodLevel.get()) return "饱食度过低";

        ItemStack main = mc.player.getMainHandStack();
        if (pauseOnLowDurability.get() && isPickaxe(main) && main.isDamageable()) {
            int remain = main.getMaxDamage() - main.getDamage();
            int threshold = minPickaxeDurability.get();
            if (remain <= threshold) {
                if (replacePickaxeOnLowDurability.get() && mc.currentScreen == null) {
                    if (tryReplacePickaxe(threshold)) return null;
                }
                return "稿子耐久过低";
            }
        }

        return null;
    }

    private boolean tryReplacePickaxe(int threshold) {
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) return false;

        long now = System.currentTimeMillis();
        if (now - lastPickaxeReplaceMs < 500L) return false;

        int selected = mc.player.getInventory().getSelectedSlot();

        int bestSlot = -1;
        int bestRemain = threshold;

        int size = Math.min(36, mc.player.getInventory().size());
        for (int i = 0; i < size; i++) {
            if (i == selected) continue;
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!isPickaxe(s)) continue;
            if (!s.isDamageable()) continue;
            int remain = s.getMaxDamage() - s.getDamage();
            if (remain <= threshold) continue;
            if (remain > bestRemain) {
                bestRemain = remain;
                bestSlot = i;
            }
        }

        if (bestSlot == -1) return false;
        if (!InvUtil.invSwap(bestSlot)) return false;

        lastPickaxeReplaceMs = now;
        ChatUtil.addChatMessage("§aBaritone设置: 已更换稿子(" + bestRemain + ")");
        return true;
    }

    private boolean isPickaxe(ItemStack s) {
        return s != null && !s.isEmpty() && s.getItem().getTranslationKey().contains("pickaxe");
    }

    private boolean auraHasTarget() {
        KillAura ka = Sakura.MODULES.getModule(KillAura.class);
        if (ka != null && ka.isEnabled()) {
            var t = ka.getCurrentTarget();
            if (t != null && t.isAlive()) return true;
        }

        MutiAura ma = Sakura.MODULES.getModule(MutiAura.class);
        if (ma != null && ma.isEnabled()) {
            return ma.hasTarget();
        }

        return false;
    }

    private void ensureSwordSelected() {
        int swordSlot = findSwordInHotbar();
        if (swordSlot == -1) return;

        int current = mc.player.getInventory().getSelectedSlot();
        auraPrevSlot = current;
        auraSwordSlot = swordSlot;

        if (current == swordSlot) return;
        mc.player.getInventory().setSelectedSlot(swordSlot);
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(swordSlot));
    }

    private void restoreAuraSlot() {
        if (auraPrevSlot < 0 || auraPrevSlot > 8) return;
        if (auraSwordSlot != -1 && mc.player.getInventory().getSelectedSlot() != auraSwordSlot) return;

        mc.player.getInventory().setSelectedSlot(auraPrevSlot);
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(auraPrevSlot));
        auraPrevSlot = -1;
        auraSwordSlot = -1;
    }

    private int findSwordInHotbar() {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s == null || s.isEmpty()) continue;
            if (s.isIn(ItemTags.SWORDS)) return i;
        }
        return -1;
    }

    private boolean handleAntiPlayer() {
        double r = antiPlayerRange.get();
        double r2 = r * r;

        PlayerEntity self = mc.player;
        boolean hasAnyInRange = false;
        boolean whisperedThisTick = false;

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == self) continue;
            if (!p.isAlive()) continue;
            if (antiPlayerIgnoreFriends.get() && Managers.FRIEND.isFriend(p.getName().getString())) continue;

            double d2 = self.squaredDistanceTo(p);
            boolean in = d2 <= r2;
            Boolean prev = wasInRange.get(p.getUuid());
            if (!in) {
                if (prev != null && prev) {
                    lastWhisper.remove(p.getUuid());
                }
                wasInRange.put(p.getUuid(), false);
                continue;
            }

            hasAnyInRange = true;
            wasInRange.put(p.getUuid(), true);

            if (antiPlayerAutoMessage.get() && (prev == null || !prev)) {
                if (tryWhisper(p)) {
                    whisperedThisTick = true;
                }
            }
        }

        if (hasAnyInRange && antiPlayerAutoLog.get()) {
            if (antiPlayerAutoMessage.get() && whisperedThisTick) {
                pendingAntiPlayerLog = true;
                pendingAntiPlayerLogTicks = 1;
                return false;
            }

            cancelBaritone();
            if (mc.world != null) {
                mc.world.disconnect(net.minecraft.text.Text.of("Disconnected by BaritoneControl"));
                mc.setScreen(new TitleScreen());
            }
            return true;
        }

        return false;
    }

    private boolean tryWhisper(PlayerEntity p) {
        if (mc.player == null || mc.player.networkHandler == null) return false;
        long now = System.currentTimeMillis();
        long last = lastWhisper.getOrDefault(p.getUuid(), 0L);
        if (now - last < (long) antiPlayerMsgCooldownSec.get() * 1000L) return false;
        lastWhisper.put(p.getUuid(), now);

        String name = p.getName().getString();
        String msg = antiPlayerMessage.get();
        if (msg == null) msg = "";
        msg = msg.trim();
        if (msg.isEmpty()) return false;

        mc.player.networkHandler.sendChatCommand("w " + name + " " + msg);
        ChatUtil.addChatMessage("§eBaritone设置: 已私信 " + name);
        return true;
    }
}
