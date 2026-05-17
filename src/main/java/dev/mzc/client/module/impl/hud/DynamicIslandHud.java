package dev.mzc.client.module.impl.hud;

import com.mojang.blaze3d.opengl.GlStateManager;
import dev.mzc.client.Sakura;
import dev.mzc.client.events.EventType;
import dev.mzc.client.events.entity.AttackEvent;
import dev.mzc.client.events.packet.PacketEvent;
import dev.mzc.client.module.HudModule;
import dev.mzc.client.module.Module;
import dev.mzc.client.mixin.accessor.IPlayerInteractEntityC2SPacket;
import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.module.impl.misc.MusicPlayer;
import dev.mzc.client.module.impl.player.AutoEat;
import dev.mzc.client.module.impl.player.AutoTotem;
import dev.mzc.client.module.impl.player.InvManager;
import dev.mzc.client.module.impl.player.inventory.ChestStealer;
import dev.mzc.client.module.impl.player.mine.AutoMine;
import dev.mzc.client.nanovg.NanoVGRenderer;
import dev.mzc.client.nanovg.font.FontLoader;
import dev.mzc.client.nanovg.util.NanoVGHelper;
import dev.mzc.client.utils.animations.Easing;
import dev.mzc.client.utils.entity.HealthUtil;
import dev.mzc.client.utils.render.Shader2DUtil;
import dev.mzc.client.utils.TranslationManager;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import org.lwjgl.nanovg.NanoVG;

import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DynamicIslandHud extends HudModule {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static volatile Text capturedTabHeader;
    private static volatile Text capturedTabFooter;
    private static volatile List<PlayerListEntry> capturedTabEntries = List.of();

    private static final class Size {
        static final float BASE_W = 65, BASE_H = 19;
        static final float EXPANDED_W = 90, EXPANDED_H = 25;
        static final float ELEMENT_SPACING = 20;
        static final float ELEMENT_WIDTH = 50;
        static final float LOGO_FONT_SIZE = 12;
        static final float INFO_FONT_SIZE = 10;
        static final float GLOW_RADIUS = 3.0f;
        static final Color INVENTORY_BG_COLOR = new Color(18, 18, 18, 70);

        static final float TAB_PLAYER_HEIGHT = 14;
        static final float TAB_PADDING = 8;
        static final float TAB_HEADER_Y = 12;
        static final float TAB_LIST_Y = 30;
        static final int TAB_COLUMNS = 1;
    }

    private static final class Timing {
        static final long EXPAND = 168L;
        static final long DISPLAY = 1200L;
        static final long COLLAPSE_1 = 168L;
        static final long COLLAPSE_2 = 224L;
        static final long TOTAL = EXPAND + DISPLAY + COLLAPSE_1 + COLLAPSE_2;
        static final long TAB_TRANSITION = 196L;
    }

    private static final long WIN_TOTAL = Timing.EXPAND + Timing.DISPLAY;

    private enum Phase {
        IDLE,
        EXPANDING,
        DISPLAY,
        COLLAPSE_1,
        COLLAPSE_2,
        TAB_EXPAND,
        TAB_DISPLAY,
        TAB_COLLAPSE
    }

    private enum Layout {
        Classic(),
        Unified(),
        Modern();
        Layout() {
        }
    }

    private enum ProgressStyle {
        Bar(),
        Background();
        ProgressStyle() {
        }

        @Override
        public String toString() {
            return TranslationManager.get(TranslationManager.enumKey(this), name());
        }
    }

    private final BoolValue enableBloom = new BoolValue("EnableBloom", true);
    private final BoolValue blur = new BoolValue("Blur", true);
    private final NumberValue<Double> blurStrength = new NumberValue<>("BlurStrength", 10.0, 1.0, 20.0, 0.5, blur::get);
    private final NumberValue<Integer> backgroundAlpha = new NumberValue<>("BackgroundAlpha", 160, 0, 255, 1);
    private final NumberValue<Double> radius = new NumberValue<>("Radius", 6.0, 0.0, 15.0, 1.0);
    private final BoolValue killNotification = new BoolValue("KillNotification", true);
    private final BoolValue winNotification = new BoolValue("WinNotification", true);
    private final BoolValue eatingNotification = new BoolValue("EatingNotification", true);
    private final BoolValue sortingNotification = new BoolValue("SortingNotification", true);
    private final BoolValue autoMineNotification = new BoolValue("AutoMineNotification", true);
    private final BoolValue musicNotification = new BoolValue("MusicNotification", true);
    private final BoolValue targetHudIntegration = new BoolValue("TargetHud", true);
    private final BoolValue chestStealerHud = new BoolValue("ChestStealerHud", true);
    private final EnumValue<Layout> layout = new EnumValue<>("Layout", Layout.Modern);
    private final EnumValue<ProgressStyle> progressStyle = new EnumValue<>("ProgressStyle", ProgressStyle.Bar, ProgressStyle.class);

    private static ToggleInfo currentToggle;
    private static ToggleInfo pendingToggle;
    private static KillInfo currentKill;
    private static KillInfo pendingKill;
    private static WinInfo currentWin;
    private static WinInfo pendingWin;

    private long toggleStartTime = -1L;
    private long tabStartTime = -1L;
    private long noTotemTriggerTime = 0L;
    private long toggleTriggerTime = 0L;
    private long sortingTriggerTime = 0L;
    private long winTriggerTime = 0L;
    private long killTriggerTime = 0L;
    private long autoMineTriggerTime = 0L;
    private long musicTriggerTime = 0L;
    private float targetExpandedWidth = Size.EXPANDED_W;

    private Entity lastAttackedEntity;
    private long lastAttackTime;
    private int lastProcessedKillId = -1;

    private InvManager invManager;
    private ChestStealer chestStealer;
    private TargetHud targetHud;
    private boolean isSorting;
    private boolean closingSorting;
    private boolean displayingSorting;
    private boolean isNoTotem;
    private boolean isAutoMineWorking;
    private boolean autoMineActive;
    private boolean autoMineAreaMode;
    private float autoMineAreaProgress;
    private boolean isAutoEatEating;
    private float autoEatProgress01;
    private long autoEatTriggerTime = 0L;
    private int maxPendingActions = 1;
    private long autoMineEnabledSince = 0L;
    private boolean isMusicPlaying;
    private String musicTitle = "";
    private float musicVolume01 = 0f;
    private long musicPlayingSince = 0L;
    private boolean isChestOverlayActive = false;
    private boolean chestOverlayDesired = false;
    private float chestOverlayAlpha = 0f;
    private long chestOverlayAlphaLastMs = 0L;
    private int chestRows = 3;
    private final Map<Integer, ChestSlotVisual> chestSlotVisuals = new HashMap<>();
    private boolean chestMorphing = false;
    private long chestMorphStartMs = 0L;
    private float chestMorphFromW = 0f;
    private float chestMorphFromH = 0f;
    private String chestTransitionOldText;
    private LivingEntity currentIntegratedTarget = null;
    private float targetHudAlpha = 0f;

    private static final class ChestSlotVisual {
        private ItemStack stack;
        private float alpha;
        private float targetAlpha;
        private long lastMs;

        private ChestSlotVisual(ItemStack stack, float alpha, float targetAlpha, long lastMs) {
            this.stack = stack;
            this.alpha = alpha;
            this.targetAlpha = targetAlpha;
            this.lastMs = lastMs;
        }
    }

    private Phase phase = Phase.IDLE;
    private float progress;
    private float blurOpacity = 1f;
    private float animX, animY, animW, animH;
    private float tabMergeProgress;
    private long lastNotificationTime = 0;

    private int unifiedPromptLines = 0;
    private long unifiedHeightAnimStart = 0L;
    private float unifiedHeightFrom = 0f;
    private float unifiedHeightTo = 0f;
    private long unifiedWidthAnimStart = 0L;
    private float unifiedWidthFrom = 0f;
    private float unifiedWidthTo = 0f;
    private long idleReturnStart = 0L;
    private float idleFromW = 0f;
    private float idleFromH = 0f;
    private long sortingAnimStart = 0L;
    private float sortingAnimFrom = 0f;
    private float sortingAnimTo = 0f;

    private final Map<PromptKind, UnifiedPillState> unifiedPills = new EnumMap<>(PromptKind.class);
    private float unifiedLastActiveW = 0f;
    private float unifiedLastActiveH = 0f;

    private static final Set<String> WIN_TRIGGERS = new HashSet<>(Arrays.asList(
            "1st Killer -", "1st Place -", "Winner -", "Winner-", " - Damage Dealt -", "Winning Team -", "1st -",
            "Winners:", "Winner:", "Winning Team:", " won the game!", "Top Seeker:", "1st Place:",
            "Last team standing!", "Winner #1 (", "Top Survivors", "Winners-", "Winners -", "Sumo Duel -",
            "Most Wool Placed -", "Your Overall Winstreak:"
    ));

    private List<PlayerListEntry> playerList;
    private float tabTargetW, tabTargetH;
    private int tabColumns = 1;
    private int tabRows = 0;
    private int tabColumnWidth = 0;

    public static void hookVanillaTab(Text header, Text footer, List<PlayerListEntry> entries) {
        capturedTabHeader = header;
        capturedTabFooter = footer;
        capturedTabEntries = entries == null ? List.of() : List.copyOf(entries);
    }

    public DynamicIslandHud() {
        super("DynamicIsland", 0, 0);
        this.width = Size.BASE_W;
        this.height = Size.BASE_H;
    }

    public static void onModuleToggle(Module module, boolean enabled) {
        pendingToggle = new ToggleInfo(module.getEnglishName(), enabled);
    }

    @Override
    public void onRender(DrawContext context) {
        update();

        renderBlur(context);
        renderSideBlurs(context, getSideBlurOpacity());

        NanoVGRenderer.INSTANCE.draw(vg -> renderContent());

        if (isTabPhase()) {
            renderCapturedTab(context);
        }
        if (isChestOverlayActive) {
            drawChestGrid(context);
        }
        if (targetHudAlpha > 0.01f && currentIntegratedTarget != null) {
            renderIntegratedTargetHud(context);
        }
    }

    private void update() {
        if (invManager == null) invManager = (InvManager) Sakura.MODULES.getModule(InvManager.class);
        if (chestStealer == null) chestStealer = Sakura.MODULES.getModule(ChestStealer.class);
        if (targetHud == null) targetHud = Sakura.MODULES.getModule(TargetHud.class);

        boolean wasNoTotem = isNoTotem;
        isNoTotem = shouldShowNoTotem();

        if (isNoTotem && !wasNoTotem) {
            displayingSorting = false;
            noTotemTriggerTime = System.currentTimeMillis();
            if (!isTabPhase()) {
                phase = Phase.EXPANDING;
                toggleStartTime = System.currentTimeMillis();
                mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, 2.0f));
            }
        } else if (wasNoTotem && !isNoTotem) {
            noTotemTriggerTime = 0L;
            if (!isTabPhase()) {
                toggleStartTime = System.currentTimeMillis() - (Timing.EXPAND + Timing.DISPLAY);
                pendingToggle = null;
                pendingKill = null;
            }
        }

        if (isUnifiedLayout()) {
            boolean wasSorting = isSorting;
            isSorting = sortingNotification.get() && invManager != null && invManager.isEnabled() && invManager.pendingActions > 0 && invManager.shouldSort();

            if (isSorting && !wasSorting) {
                maxPendingActions = Math.max(1, invManager.pendingActions);
                closingSorting = false;
                sortingTriggerTime = System.currentTimeMillis();
            } else if (wasSorting && !isSorting) {
                closingSorting = true;
                displayingSorting = false;
                sortingTriggerTime = 0L;
                if (!isTabPhase()) {
                    toggleStartTime = System.currentTimeMillis() - (Timing.EXPAND + Timing.DISPLAY);
                }
            } else if (isSorting) {
                if (invManager.pendingActions > maxPendingActions) {
                    maxPendingActions = invManager.pendingActions;
                }
            }
        } else {
            if (!isNoTotem && sortingNotification.get()) {
                boolean wasSorting = isSorting;
                isSorting = invManager != null && invManager.isEnabled() && invManager.pendingActions > 0 && invManager.shouldSort();

                if (isSorting && !wasSorting) {
                    maxPendingActions = Math.max(1, invManager.pendingActions);
                    closingSorting = false;
                    sortingTriggerTime = System.currentTimeMillis();
                } else if (wasSorting && !isSorting) {
                    closingSorting = true;
                    displayingSorting = false;
                    sortingTriggerTime = 0L;
                    if (!isTabPhase()) {
                        toggleStartTime = System.currentTimeMillis() - (Timing.EXPAND + Timing.DISPLAY);
                    }
                } else if (isSorting) {
                    if (invManager.pendingActions > maxPendingActions) {
                        maxPendingActions = invManager.pendingActions;
                    }
                }
            } else {
                boolean wasSorting = isSorting;
                isSorting = false;
                if (wasSorting) {
                    closingSorting = false;
                    displayingSorting = false;
                    sortingTriggerTime = 0L;
                    if (!isTabPhase()) {
                        toggleStartTime = System.currentTimeMillis() - (Timing.EXPAND + Timing.DISPLAY);
                    }
                }
            }
        }

        long now = System.currentTimeMillis();
         AutoMine autoMine = Sakura.MODULES.getModule(AutoMine.class);
         boolean autoMineEnabled = autoMineNotification.get() && autoMine != null && autoMine.isEnabled();
         if (autoMineEnabled && autoMineEnabledSince == 0L) {
             autoMineEnabledSince = now;
         } else if (!autoMineEnabled) {
             autoMineEnabledSince = 0L;
         }

         boolean autoMineShow = autoMineEnabled && now - autoMineEnabledSince >= 500L;
         boolean wasAutoMineWorking = isAutoMineWorking;
         isAutoMineWorking = autoMineShow;
         autoMineActive = isAutoMineWorking && autoMine.isWorking();
         autoMineAreaMode = isAutoMineWorking && autoMine.getModeValue() == AutoMine.Mode.Area;
         if (autoMineAreaMode && autoMineActive) {
             autoMineAreaProgress = clamp(autoMine.getAreaProgress(), 0f, 1f);
         } else if (!autoMineAreaMode) {
             autoMineAreaProgress = 0f;
         }
         if (isAutoMineWorking && !wasAutoMineWorking) {
             autoMineTriggerTime = now;
         } else if (!isAutoMineWorking && wasAutoMineWorking) {
             autoMineTriggerTime = 0L;
         }

        boolean wasMusicPlaying = isMusicPlaying;
        isMusicPlaying = false;
        musicTitle = "";
        musicVolume01 = 0f;
        MusicPlayer mp = Sakura.MODULES.getModule(MusicPlayer.class);
        boolean playingNow = musicNotification.get() && mp != null && mp.isEnabled() && mp.isPlayingNow();
        if (playingNow && musicPlayingSince == 0L) {
            musicPlayingSince = now;
        } else if (!playingNow) {
            musicPlayingSince = 0L;
        }

        boolean showMusic = playingNow && now - musicPlayingSince >= 500L;
        if (showMusic) {
            isMusicPlaying = true;
            musicTitle = mp.getNowPlayingTitle();
            musicVolume01 = clamp(mp.getVolume01(), 0f, 1f);
        }
        if (isMusicPlaying && !wasMusicPlaying) {
            musicTriggerTime = now;
        } else if (!isMusicPlaying && wasMusicPlaying) {
            musicTriggerTime = 0L;
        }

        AutoEat autoEat = Sakura.MODULES.getModule(AutoEat.class);
        boolean wasAutoEatEating = isAutoEatEating;
        isAutoEatEating = eatingNotification.get() && autoEat != null && autoEat.isEnabled() && autoEat.isEating();
        if (isAutoEatEating) {
            autoEatProgress01 = clamp(autoEat.getEatingProgress01(), 0f, 1f);
        } else {
            autoEatProgress01 = 0f;
        }
        if (isAutoEatEating && !wasAutoEatEating) {
            autoEatTriggerTime = now;
        } else if (!isAutoEatEating && wasAutoEatEating) {
            autoEatTriggerTime = 0L;
        }

        // Integrated Target HUD logic
        if (targetHudIntegration.get() && targetHud != null && targetHud.isEnabled()) {
            LivingEntity bestTarget = null;
            double minDist = 400; // 20 blocks squared
            if (mc.world != null && mc.player != null) {
                for (Entity e : mc.world.getEntities()) {
                    if (e instanceof LivingEntity le && le != mc.player && le.isAlive()) {
                        double d = mc.player.squaredDistanceTo(le);
                        if (d < minDist) {
                            minDist = d;
                            bestTarget = le;
                        }
                    }
                }
            }
            currentIntegratedTarget = bestTarget;
            if (currentIntegratedTarget != null && !isTabPhase()) {
                targetHudAlpha = Math.min(1f, targetHudAlpha + 0.1f);
            } else {
                targetHudAlpha = Math.max(0f, targetHudAlpha - 0.1f);
            }
        } else {
            targetHudAlpha = 0f;
            currentIntegratedTarget = null;
        }

        boolean wasDesired = chestOverlayDesired;
        boolean desired = chestStealerHud.get()
                && chestStealer != null
                && chestStealer.isEnabled()
                && mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
        chestOverlayDesired = desired;

        if (!wasDesired && desired) {
            chestTransitionOldText = getActivePromptTextSnapshot();
            chestMorphing = true;
            chestMorphStartMs = now;
            chestMorphFromW = animW > 0f ? animW : Size.BASE_W;
            chestMorphFromH = animH > 0f ? animH : Size.BASE_H;
        }

        float overlayAlpha = updateChestOverlayAlpha(now, desired);
        isChestOverlayActive = overlayAlpha > 0.01f;

        if (desired || isChestOverlayActive) {
            targetExpandedWidth = calculateChestWidth();
        }

        if (desired) {
            net.minecraft.screen.GenericContainerScreenHandler h = getChestHandler();
            if (h != null) {
                chestRows = h.getRows();
                updateChestSlotVisualsFromHandler(h, now);
                if (isChestEmpty(h) && mc.player != null) {
                    mc.player.closeHandledScreen();
                }
            }
        } else {
            chestSlotVisuals.clear();
        }

        tickChestSlotVisuals(now);
        if (chestTransitionOldText != null) {
            float a = clamp(1f - chestOverlayAlpha, 0f, 1f);
            if (a <= 0.01f) {
                chestTransitionOldText = null;
            }
        }

        if (wasDesired && !desired) {
            toggleStartTime = System.currentTimeMillis() - (Timing.EXPAND + Timing.DISPLAY);
            lastNotificationTime = now;
        }

        if (isChestOverlayActive) {
            isNoTotem = false;
            isSorting = false;
            isAutoEatEating = false;
            isAutoMineWorking = false;
            isMusicPlaying = false;
            currentToggle = null;
            pendingToggle = null;
            currentKill = null;
            pendingKill = null;
            currentWin = null;
            pendingWin = null;
            toggleTriggerTime = 0L;
            killTriggerTime = 0L;
            winTriggerTime = 0L;
            musicTriggerTime = 0L;
            autoMineTriggerTime = 0L;
            autoEatTriggerTime = 0L;
        }

        if (killNotification.get() && lastAttackedEntity instanceof LivingEntity living) {
            if (HealthUtil.getEntityHealth(living) <= 0.1f && living.getId() != lastProcessedKillId && System.currentTimeMillis() - lastAttackTime < 5000) {
                pendingKill = new KillInfo(living.getName().getString());
                lastProcessedKillId = living.getId();
            }
        }

        handleTabInput();
        processNotifications();
        calculateState();

        if (isUnifiedLayout() && (hasUnifiedPrompt() || hasUnifiedPillTransient()) && !isTabPhase()) {
            unifiedLastActiveW = animW;
            unifiedLastActiveH = animH;
        }
    }

    private void handleTabInput() {
        boolean tabPressed = mc.options.playerListKey.isPressed();

        if (tabPressed && !isTabPhase()) {
            tabStartTime = System.currentTimeMillis();
            phase = Phase.TAB_EXPAND;
            updatePlayerList();
        } else if (tabPressed && isTabPhase()) {
            updatePlayerList();
            if (phase == Phase.TAB_COLLAPSE) {
                phase = Phase.TAB_EXPAND;
                tabStartTime = System.currentTimeMillis() - (long)((1f - tabMergeProgress) * Timing.TAB_TRANSITION * 0.45f);
            }
        } else if (phase == Phase.TAB_DISPLAY || phase == Phase.TAB_EXPAND) {
            tabStartTime = System.currentTimeMillis();
            phase = Phase.TAB_COLLAPSE;
        }
    }

    private void updatePlayerList() {
        if (mc.getNetworkHandler() != null) {
            List<PlayerListEntry> source = capturedTabEntries.isEmpty() ? List.copyOf(mc.getNetworkHandler().getPlayerList()) : capturedTabEntries;
            playerList = source.stream()
                    .sorted(Comparator.comparingInt((PlayerListEntry e) -> e.getGameMode() == GameMode.SPECTATOR ? 1 : 0).thenComparing(e -> e.getProfile().name()))
                    .limit(80) 
                    .collect(Collectors.toList());

            int count = playerList.size();

            int maxNameWidth = 0;
            for (PlayerListEntry entry : playerList) {
                int w = mc.textRenderer.getWidth(mc.inGameHud.getPlayerListHud().getPlayerName(entry));
                if (w > maxNameWidth) maxNameWidth = w;
            }
            maxNameWidth = Math.min(maxNameWidth, 150);

            int pingWidth = mc.textRenderer.getWidth("999ms");
            int headSize = 10;
            int innerPadding = 4;
            int singleColWidth = headSize + innerPadding + maxNameWidth + innerPadding + pingWidth + innerPadding;
            singleColWidth = Math.max(singleColWidth, 80);

            int maxRows = 20;
            if (count == 0) {
                tabColumns = 1;
                tabRows = 0;
            } else {
                tabColumns = (int) Math.ceil((double) count / maxRows);
                if (tabColumns > 4) tabColumns = 4;
                tabRows = (int) Math.ceil((double) count / tabColumns);
            }
            tabColumnWidth = singleColWidth;

            float spacing = 5;
            tabTargetW = Size.TAB_PADDING * 2 + tabColumns * tabColumnWidth + (tabColumns - 1) * spacing;
            tabTargetW = Math.max(tabTargetW, Size.BASE_W);

            int innerW = (int) Math.max(0, tabTargetW - Size.TAB_PADDING * 2f);
            int fontH = mc.textRenderer.fontHeight;

            int headerLines;
            if (capturedTabHeader != null && !capturedTabHeader.getString().isEmpty()) {
                headerLines = mc.textRenderer.wrapLines(capturedTabHeader, innerW).size();
            } else {
                headerLines = 1;
            }

            int ftLine = 0;
            if (capturedTabFooter != null && !capturedTabFooter.getString().isEmpty()) {
                ftLine = mc.textRenderer.wrapLines(capturedTabFooter, innerW).size();
            }

            float headerH = headerLines * fontH;
            float footerH = ftLine * fontH;
            float listY = Math.max(Size.TAB_LIST_Y, Size.TAB_HEADER_Y + headerH + 8f);

            tabTargetH = listY + tabRows * Size.TAB_PLAYER_HEIGHT + Size.TAB_PADDING + (ftLine > 0 ? (footerH + 8f) : 0f);
            tabTargetH = Math.max(tabTargetH, Size.BASE_H * 2f);
        }
    }

    private void processNotifications() {
        if (isTabPhase()) return;

        long now = System.currentTimeMillis();
        boolean clearedAny = false;
        boolean winCleared = false;
        long notifDuration = isUnifiedLayout() ? WIN_TOTAL : Timing.TOTAL;
        if (currentToggle != null && toggleTriggerTime > 0L && now - toggleTriggerTime >= notifDuration) {
            currentToggle = null;
            toggleTriggerTime = 0L;
            clearedAny = true;
        }
        if (currentKill != null && killTriggerTime > 0L && now - killTriggerTime >= notifDuration) {
            currentKill = null;
            killTriggerTime = 0L;
            clearedAny = true;
        }
        if (currentWin != null && winTriggerTime > 0L && now - winTriggerTime >= WIN_TOTAL) {
            currentWin = null;
            winTriggerTime = 0L;
            clearedAny = true;
            winCleared = true;
        }

        if (clearedAny && currentToggle == null && currentKill == null && currentWin == null) {
            toggleStartTime = winCleared && !isNoTotem && !isSorting ? (now - (Timing.EXPAND + Timing.DISPLAY)) : -1L;
            lastNotificationTime = now;
            if (isSorting) {
                displayingSorting = true;
                closingSorting = false;
            }
        }

        if (pendingKill != null) {
            currentKill = pendingKill;
            pendingKill = null;
            displayingSorting = false;
            closingSorting = false;
            toggleStartTime = now;
            killTriggerTime = now;
        }

        if (pendingToggle != null) {
            currentToggle = pendingToggle;
            pendingToggle = null;
            displayingSorting = false;
            closingSorting = false;
            toggleStartTime = now;
            toggleTriggerTime = now;
        }

        if (pendingWin != null) {
            currentWin = pendingWin;
            pendingWin = null;
            displayingSorting = false;
            closingSorting = false;
            toggleStartTime = now;
            winTriggerTime = now;
        }

        if (currentKill != null || currentToggle != null || currentWin != null) {
            float killW = currentKill != null ? calculateKillWidth() : 0f;
            float toggleW = currentToggle != null ? calculateExpandedWidth() : 0f;
            float winW = currentWin != null ? calculateWinWidth() : 0f;
            targetExpandedWidth = Math.max(targetExpandedWidth, Math.max(Math.max(killW, toggleW), winW));
        }
    }

    @EventHandler
    private void onWinPacket(PacketEvent event) {
        if (!winNotification.get()) return;
        if (event.getType() != EventType.RECEIVE) return;
        if (mc.player == null) return;

        String msg = null;
        if (event.getPacket() instanceof TitleS2CPacket(Text text)) {
            if (text != null) msg = text.getString();
        } else if (event.getPacket() instanceof GameMessageS2CPacket(Text content, boolean overlay)) {
            if (content != null) msg = content.getString();
        }
        if (msg == null || msg.isEmpty()) return;

        String name = mc.player.getName().getString();
        if (matchWin(msg, name)) {
            pendingWin = new WinInfo();
        }
    }

    private boolean matchWin(String s, String playerName) {
        if (s == null || s.isEmpty() || playerName == null || playerName.isEmpty()) return false;
        String t = stripFormatting(s);
        String pn = playerName.toLowerCase();
        for (String k : WIN_TRIGGERS) {
            int idx = t.indexOf(k);
            if (idx < 0) continue;
            String tail = t.substring(Math.min(t.length(), idx + k.length()));
            if (tail.toLowerCase().contains(pn)) return true;
        }
        return false;
    }

    private String stripFormatting(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00A7') {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString().trim();
    }

    @EventHandler
    private void onPacketSend(PacketEvent event) {
        if (!killNotification.get()) return;
        if (event.getType() == EventType.SEND && event.getPacket() instanceof PlayerInteractEntityC2SPacket packet) {
            int id = ((IPlayerInteractEntityC2SPacket) packet).getEntityId();
            Entity entity = mc.world.getEntityById(id);
            if (entity != null) {
                lastAttackedEntity = entity;
                lastAttackTime = System.currentTimeMillis();
            }
        }
    }

    @EventHandler
    private void onAttack(AttackEvent event) {
        if (!killNotification.get()) return;
        lastAttackedEntity = event.getTargetEntity();
        lastAttackTime = System.currentTimeMillis();
    }

    @EventHandler
    private void onPacket(PacketEvent event) {
        if (!killNotification.get()) return;
        if (event.getType() != EventType.RECEIVE) return;

        if (event.getPacket() instanceof GameMessageS2CPacket(Text content, boolean overlay) && !overlay) {
            if (mc.player == null || content == null) return;
            String msg = content.getString();
            String myName = mc.player.getGameProfile().name();
            if ((msg.contains("was killed by") || msg.contains("was knocked into the void by"))
                    && msg.contains(myName)) {
                String victim = extractVictimName(msg);
                if (victim.equals(myName)) return;
                pendingKill = new KillInfo(victim);
            }
        }

        if (event.getPacket() instanceof EntityStatusS2CPacket packet) {
            if (packet.getStatus() == 3) {
                Entity entity = packet.getEntity(mc.world);
                if (entity == null) return;

                boolean isTarget = (entity == lastAttackedEntity || (lastAttackedEntity != null && entity.getId() == lastAttackedEntity.getId()));

                if (!isTarget && lastAttackedEntity instanceof EndCrystalEntity && entity instanceof PlayerEntity) {
                    if (entity.distanceTo(lastAttackedEntity) <= 12.0) {
                        isTarget = true;
                    }
                }

                if (isTarget && System.currentTimeMillis() - lastAttackTime < 5000) {
                    if (entity instanceof PlayerEntity || entity instanceof Monster) {
                        pendingKill = new KillInfo(entity.getName().getString());
                        lastProcessedKillId = entity.getId();
                    }
                }
            }
        }
    }

    private void calculateState() {
        long dt = ela();
        long tabDt = elaTab();

        if (isUnifiedLayout()) {
            calculateStateUnified(dt, tabDt);
        } else {
            calculateStateClassic(dt, tabDt);
        }
    }

    private void calculateStateClassic(long dt, long tabDt) {
        if (chestOverlayDesired && !isTabPhase()) {
            float targetW = calculateChestWidth();
            float targetH = calculateChestHeight();
            targetExpandedWidth = targetW;
            long now = System.currentTimeMillis();
            if (chestMorphing) {
                float t = clamp((now - chestMorphStartMs) / (float) Timing.EXPAND, 0f, 1f);
                float p = easeOut(t);
                setPhase(Phase.EXPANDING, p,
                        lerp(chestMorphFromW, targetW, p),
                        lerp(chestMorphFromH, targetH, p),
                        1f);
                if (t >= 1f) {
                    chestMorphing = false;
                    setPhase(Phase.DISPLAY, 1f, targetW, targetH, 1f);
                    toggleStartTime = now;
                }
            } else if (phase == Phase.EXPANDING) {
                if (dt < Timing.EXPAND) {
                    float p = easeOut(dt / (float) Timing.EXPAND);
                    setPhase(Phase.EXPANDING, p,
                            lerp(animW > 0f ? animW : Size.BASE_W, targetW, p),
                            lerp(animH > 0f ? animH : Size.BASE_H, targetH, p),
                            1f);
                } else {
                    setPhase(Phase.DISPLAY, 1f, targetW, targetH, 1f);
                }
            } else if (phase == Phase.DISPLAY) {
                setPhase(Phase.DISPLAY, 1f, targetW, targetH, 1f);
            } else {
                phase = Phase.EXPANDING;
                toggleStartTime = now;
            }
            animX = (mc.getWindow().getScaledWidth() - animW) / 2f;
            animY = y;
            this.width = animW;
            this.height = animH;
            this.x = animX;
            return;
        }
        if (isNoTotem && !isTabPhase()) {
            targetExpandedWidth = calculateNoTotemWidth();
            if (phase == Phase.EXPANDING) {
                if (dt < Timing.EXPAND) {
                    float p = easeOut(dt / (float) Timing.EXPAND);
                    setPhase(Phase.EXPANDING, p,
                            lerp(Size.BASE_W, targetExpandedWidth, p),
                            lerp(Size.BASE_H, Size.EXPANDED_H, p),
                            lerp(1f, 1f, p));
                } else {
                    setPhase(Phase.DISPLAY, 1f, targetExpandedWidth, Size.EXPANDED_H, 1f);
                }
            } else if (phase == Phase.DISPLAY) {
                setPhase(Phase.DISPLAY, 1f, targetExpandedWidth, Size.EXPANDED_H, 1f);
            } else {
                phase = Phase.EXPANDING;
                toggleStartTime = System.currentTimeMillis();
            }
            animX = (mc.getWindow().getScaledWidth() - animW) / 2f;
            animY = y;
            this.width = animW;
            this.height = animH;
            this.x = animX;
            return;
        }

        if (isAutoEatEating && !isTabPhase() && currentKill == null && currentToggle == null && currentWin == null && (phase == Phase.IDLE || phase == Phase.EXPANDING || phase == Phase.DISPLAY)) {
            targetExpandedWidth = calculateAutoEatWidth();
            if (phase == Phase.EXPANDING) {
                if (dt < Timing.EXPAND) {
                    float p = easeOut(dt / (float) Timing.EXPAND);
                    setPhase(Phase.EXPANDING, p,
                            lerp(Size.BASE_W, targetExpandedWidth, p),
                            lerp(Size.BASE_H, Size.EXPANDED_H, p),
                            lerp(1f, 1f, p));
                } else {
                    setPhase(Phase.DISPLAY, 1f, targetExpandedWidth, Size.EXPANDED_H, 1f);
                }
            } else if (phase == Phase.DISPLAY) {
                setPhase(Phase.DISPLAY, 1f, targetExpandedWidth, Size.EXPANDED_H, 1f);
            } else {
                phase = Phase.EXPANDING;
                toggleStartTime = System.currentTimeMillis();
            }
            animX = (mc.getWindow().getScaledWidth() - animW) / 2f;
            animY = y;
            this.width = animW;
            this.height = animH;
            this.x = animX;
            return;
        }

        if (isSorting && !isTabPhase() && currentKill == null && currentToggle == null && (displayingSorting || phase == Phase.IDLE) && System.currentTimeMillis() - lastNotificationTime > 500) {
            displayingSorting = true;
            targetExpandedWidth = calculateSortingWidth();
            if (phase == Phase.EXPANDING) {
                if (dt < Timing.EXPAND) {
                    float p = easeOut(dt / (float) Timing.EXPAND);
                    setPhase(Phase.EXPANDING, p,
                            lerp(Size.BASE_W, targetExpandedWidth, p),
                            lerp(Size.BASE_H, Size.EXPANDED_H, p),
                            lerp(1f, 1f, p));
                } else {
                    setPhase(Phase.DISPLAY, 1f, targetExpandedWidth, Size.EXPANDED_H, 1f);
                }
            } else if (phase == Phase.DISPLAY) {
                setPhase(Phase.DISPLAY, 1f, targetExpandedWidth, Size.EXPANDED_H, 1f);
            } else {
                phase = Phase.EXPANDING;
                toggleStartTime = System.currentTimeMillis();
            }
            animX = (mc.getWindow().getScaledWidth() - animW) / 2f;
            animY = y;
            this.width = animW;
            this.height = animH;
            this.x = animX;
            return;
        }

        if (isMusicPlaying && !isTabPhase() && currentKill == null && currentToggle == null && currentWin == null && (phase == Phase.IDLE || phase == Phase.EXPANDING || phase == Phase.DISPLAY)) {
            targetExpandedWidth = calculateMusicWidth();
            if (phase == Phase.EXPANDING) {
                if (dt < Timing.EXPAND) {
                    float p = easeOut(dt / (float) Timing.EXPAND);
                    setPhase(Phase.EXPANDING, p,
                            lerp(Size.BASE_W, targetExpandedWidth, p),
                            lerp(Size.BASE_H, Size.EXPANDED_H, p),
                            lerp(1f, 1f, p));
                } else {
                    setPhase(Phase.DISPLAY, 1f, targetExpandedWidth, Size.EXPANDED_H, 1f);
                }
            } else if (phase == Phase.DISPLAY) {
                setPhase(Phase.DISPLAY, 1f, targetExpandedWidth, Size.EXPANDED_H, 1f);
            } else {
                phase = Phase.EXPANDING;
                toggleStartTime = System.currentTimeMillis();
            }
            animX = (mc.getWindow().getScaledWidth() - animW) / 2f;
            animY = y;
            this.width = animW;
            this.height = animH;
            this.x = animX;
            return;
        }

        if (phase == Phase.TAB_EXPAND) {
            if (tabDt < Timing.TAB_TRANSITION) {
                float mergeT = clamp(tabDt / (Timing.TAB_TRANSITION * 0.45f), 0f, 1f);
                float expandT = clamp((tabDt - Timing.TAB_TRANSITION * 0.25f) / (Timing.TAB_TRANSITION * 0.75f), 0f, 1f);
                float mergeP = easeOut(mergeT);
                float expandP = easeOut(expandT);
                tabMergeProgress = mergeP;
                setPhase(Phase.TAB_EXPAND, expandP,
                        lerp(Size.BASE_W, tabTargetW, mergeP),
                        lerp(Size.BASE_H, tabTargetH, expandP),
                        1f);
            } else {
                tabMergeProgress = 1f;
                setPhase(Phase.TAB_DISPLAY, 1f, tabTargetW, tabTargetH, 1f);
            }
        } else if (phase == Phase.TAB_COLLAPSE) {
            if (tabDt < Timing.TAB_TRANSITION) {
                float mergeT = clamp(1f - (tabDt / (Timing.TAB_TRANSITION * 0.45f)), 0f, 1f);
                float expandT = clamp(1f - ((tabDt - Timing.TAB_TRANSITION * 0.10f) / (Timing.TAB_TRANSITION * 0.90f)), 0f, 1f);
                float mergeP = easeOut(mergeT);
                float expandP = easeOut(expandT);
                tabMergeProgress = mergeP;
                setPhase(Phase.TAB_COLLAPSE, expandP,
                        lerp(Size.BASE_W, tabTargetW, mergeP),
                        lerp(Size.BASE_H, tabTargetH, expandP),
                        1f);
            } else {
                tabMergeProgress = 0f;
                float idleW = getUnifiedIdleWidth();
                float idleH = getUnifiedIdleHeight();
                setPhase(Phase.IDLE, 0f, idleW, idleH, 1f);
                tabStartTime = -1L;
            }
        } else if (phase == Phase.TAB_DISPLAY) {
            tabMergeProgress = 1f;
            setPhase(Phase.TAB_DISPLAY, 1f, tabTargetW, tabTargetH, 1f);
        } else {
            if (currentToggle == null && currentKill == null && currentWin == null && toggleStartTime == -1L) {
                closingSorting = false;
                setPhase(Phase.IDLE, 0f, Size.BASE_W, Size.BASE_H, 1f);
            } else if (dt < Timing.EXPAND) {
                float p = easeOut(dt / (float) Timing.EXPAND);
                setPhase(Phase.EXPANDING, p,
                        lerp(Size.BASE_W, targetExpandedWidth, p),
                        lerp(Size.BASE_H, Size.EXPANDED_H, p),
                        lerp(1f, 1f, p));
            } else if (dt < Timing.EXPAND + Timing.DISPLAY) {
                float p = (dt - Timing.EXPAND) / (float) Timing.DISPLAY;
                setPhase(Phase.DISPLAY, p, targetExpandedWidth, Size.EXPANDED_H, 1f);
            } else if (dt < Timing.EXPAND + Timing.DISPLAY + Timing.COLLAPSE_1) {
                float p = easeOut((dt - Timing.EXPAND - Timing.DISPLAY) / (float) Timing.COLLAPSE_1);
                setPhase(Phase.COLLAPSE_1, p, targetExpandedWidth, Size.EXPANDED_H, 1f);
            } else {
                float timeInCollapse2 = dt - Timing.EXPAND - Timing.DISPLAY - Timing.COLLAPSE_1;
                if (timeInCollapse2 >= Timing.COLLAPSE_2) {
                    toggleStartTime = -1L;
                    closingSorting = false;
                    setPhase(Phase.IDLE, 0f, Size.BASE_W, Size.BASE_H, 1f);
                } else {
                    float p = easeOut(timeInCollapse2 / (float) Timing.COLLAPSE_2);
                    setPhase(Phase.COLLAPSE_2, p,
                            lerp(targetExpandedWidth, Size.BASE_W, p),
                            lerp(Size.EXPANDED_H, Size.BASE_H, p),
                            1f);
                }
            }
        }

        animX = (mc.getWindow().getScaledWidth() - animW) / 2f;
        animY = y;
        this.width = animW;
        this.height = animH;
        this.x = animX;
    }

    private void calculateStateUnified(long dt, long tabDt) {
        if (!hasUnifiedPrompt() && !isTabPhase()) {
            closingSorting = false;
            unifiedPills.clear();
            float idleW = getUnifiedIdleWidth();
            float idleH = getUnifiedIdleHeight();
            if (idleReturnStart == 0L) {
                idleReturnStart = System.currentTimeMillis();
                float fromW = (unifiedLastActiveW > 0f) ? unifiedLastActiveW : animW;
                float fromH = (unifiedLastActiveH > 0f) ? unifiedLastActiveH : animH;
                idleFromW = fromW <= 0 ? idleW : fromW;
                idleFromH = fromH <= 0 ? idleH : fromH;
            }
            float duration = Timing.COLLAPSE_1 + Timing.COLLAPSE_2;
            long elapsed = System.currentTimeMillis() - idleReturnStart;
            float t = clamp(elapsed / duration, 0f, 1f);
            float p = easeOut(t);
            float w = lerp(idleFromW, idleW, p);
            float h = lerp(idleFromH, idleH, p);
            setPhase(Phase.IDLE, p, w, h, 1f);
            animX = (mc.getWindow().getScaledWidth() - animW) / 2f;
            animY = y;
            this.width = animW;
            this.height = animH;
            this.x = animX;
            if (t >= 1f) {
                idleReturnStart = 0L;
                unifiedLastActiveW = 0f;
                unifiedLastActiveH = 0f;
            }
            return;
        } else {
            idleReturnStart = 0L;
        }
        if (!isTabPhase()) {
            if (chestOverlayDesired) {
                targetExpandedWidth = calculateChestWidth();
            } else {
            float headerWidth = calculateUnifiedHeaderWidth();
            float noTotemWidth = isNoTotem ? calculateNoTotemWidth() : 0f;
            float sortingWidth = isSorting ? calculateSortingWidth() : 0f;
            float autoEatWidth = isAutoEatEating ? calculateAutoEatWidth() : 0f;
            float autoMineWidth = isAutoMineWorking ? calculateAutoMineWidth() : 0f;
            float musicWidth = isMusicPlaying ? calculateMusicWidth() : 0f;
            float toggleWidth = currentToggle != null ? calculateExpandedWidth() : 0f;
            float killWidth = currentKill != null ? calculateKillWidth() : 0f;
            float winWidth = currentWin != null ? calculateWinWidth() : 0f;
            targetExpandedWidth = Math.max(headerWidth, Math.max(Math.max(noTotemWidth, Math.max(Math.max(sortingWidth, Math.max(autoEatWidth, autoMineWidth)), musicWidth)), Math.max(Math.max(toggleWidth, killWidth), winWidth)));
            }
        }

        if (chestOverlayDesired && !isTabPhase()) {
            float targetHeight = getUnifiedTargetHeight();
            targetHeight = getUnifiedAnimatedHeight(targetHeight);
            float animatedWidth = getUnifiedAnimatedWidth(targetExpandedWidth);
            if (phase == Phase.EXPANDING) {
                if (dt < Timing.EXPAND) {
                    float p = easeOut(dt / (float) Timing.EXPAND);
                    setPhase(Phase.EXPANDING, p,
                            lerp(Size.BASE_W, animatedWidth, p),
                            lerp(Size.BASE_H, targetHeight, p),
                            lerp(1f, 1f, p));
                } else {
                    setPhase(Phase.DISPLAY, 1f, animatedWidth, targetHeight, 1f);
                }
            } else if (phase == Phase.DISPLAY) {
                setPhase(Phase.DISPLAY, 1f, animatedWidth, targetHeight, 1f);
            } else {
                phase = Phase.EXPANDING;
                toggleStartTime = System.currentTimeMillis();
            }
            animX = (mc.getWindow().getScaledWidth() - animW) / 2f;
            animY = y;
            this.width = animW;
            this.height = animH;
            this.x = animX;
            return;
        }

        if (isNoTotem && !isTabPhase()) {
            float targetHeight = getUnifiedTargetHeight();
            targetHeight = getUnifiedAnimatedHeight(targetHeight);
            float animatedWidth = getUnifiedAnimatedWidth(targetExpandedWidth);
            if (phase == Phase.EXPANDING) {
                if (dt < Timing.EXPAND) {
                    float p = easeOut(dt / (float) Timing.EXPAND);
                    setPhase(Phase.EXPANDING, p,
                            lerp(Size.BASE_W, animatedWidth, p),
                            lerp(Size.BASE_H, targetHeight, p),
                            lerp(1f, 1f, p));
                } else {
                    setPhase(Phase.DISPLAY, 1f, animatedWidth, targetHeight, 1f);
                }
            } else if (phase == Phase.DISPLAY) {
                setPhase(Phase.DISPLAY, 1f, animatedWidth, targetHeight, 1f);
            } else {
                phase = Phase.EXPANDING;
                toggleStartTime = System.currentTimeMillis();
            }
            animX = (mc.getWindow().getScaledWidth() - animW) / 2f;
            animY = y;
            this.width = animW;
            this.height = animH;
            this.x = animX;
            return;
        }

        if (isSorting && !isTabPhase() && currentKill == null && currentToggle == null && (displayingSorting || phase == Phase.IDLE) && System.currentTimeMillis() - lastNotificationTime > 500) {
            displayingSorting = true;
            float targetHeight = getUnifiedTargetHeight();
            targetHeight = getUnifiedAnimatedHeight(targetHeight);
            float animatedWidth = getUnifiedAnimatedWidth(targetExpandedWidth);
            if (phase == Phase.EXPANDING) {
                if (dt < Timing.EXPAND) {
                    float p = easeOut(dt / (float) Timing.EXPAND);
                    setPhase(Phase.EXPANDING, p,
                            lerp(Size.BASE_W, animatedWidth, p),
                            lerp(Size.BASE_H, targetHeight, p),
                            lerp(1f, 1f, p));
                } else {
                    setPhase(Phase.DISPLAY, 1f, animatedWidth, targetHeight, 1f);
                }
            } else if (phase == Phase.DISPLAY) {
                setPhase(Phase.DISPLAY, 1f, animatedWidth, targetHeight, 1f);
            } else {
                phase = Phase.EXPANDING;
                toggleStartTime = System.currentTimeMillis();
            }
            animX = (mc.getWindow().getScaledWidth() - animW) / 2f;
            animY = y;
            this.width = animW;
            this.height = animH;
            this.x = animX;
            return;
        }

        if (isAutoMineWorking && !isTabPhase()) {
            float targetHeight = getUnifiedTargetHeight();
            targetHeight = getUnifiedAnimatedHeight(targetHeight);
            float animatedWidth = getUnifiedAnimatedWidth(targetExpandedWidth);
            if (phase == Phase.EXPANDING) {
                if (dt < Timing.EXPAND) {
                    float p = easeOut(dt / (float) Timing.EXPAND);
                    setPhase(Phase.EXPANDING, p,
                            lerp(Size.BASE_W, animatedWidth, p),
                            lerp(Size.BASE_H, targetHeight, p),
                            lerp(1f, 1f, p));
                } else {
                    setPhase(Phase.DISPLAY, 1f, animatedWidth, targetHeight, 1f);
                }
            } else if (phase == Phase.DISPLAY) {
                setPhase(Phase.DISPLAY, 1f, animatedWidth, targetHeight, 1f);
            } else {
                phase = Phase.EXPANDING;
                toggleStartTime = System.currentTimeMillis();
            }
            animX = (mc.getWindow().getScaledWidth() - animW) / 2f;
            animY = y;
            this.width = animW;
            this.height = animH;
            this.x = animX;
            return;
        }

        if (isAutoEatEating && !isTabPhase()) {
            float targetHeight = getUnifiedTargetHeight();
            targetHeight = getUnifiedAnimatedHeight(targetHeight);
            float animatedWidth = getUnifiedAnimatedWidth(targetExpandedWidth);
            if (phase == Phase.EXPANDING) {
                if (dt < Timing.EXPAND) {
                    float p = easeOut(dt / (float) Timing.EXPAND);
                    setPhase(Phase.EXPANDING, p,
                            lerp(Size.BASE_W, animatedWidth, p),
                            lerp(Size.BASE_H, targetHeight, p),
                            lerp(1f, 1f, p));
                } else {
                    setPhase(Phase.DISPLAY, 1f, animatedWidth, targetHeight, 1f);
                }
            } else if (phase == Phase.DISPLAY) {
                setPhase(Phase.DISPLAY, 1f, animatedWidth, targetHeight, 1f);
            } else {
                phase = Phase.EXPANDING;
                toggleStartTime = System.currentTimeMillis();
            }
            animX = (mc.getWindow().getScaledWidth() - animW) / 2f;
            animY = y;
            this.width = animW;
            this.height = animH;
            this.x = animX;
            return;
        }

        if (isMusicPlaying && !isTabPhase()) {
            float targetHeight = getUnifiedTargetHeight();
            targetHeight = getUnifiedAnimatedHeight(targetHeight);
            float animatedWidth = getUnifiedAnimatedWidth(targetExpandedWidth);
            if (phase == Phase.EXPANDING) {
                if (dt < Timing.EXPAND) {
                    float p = easeOut(dt / (float) Timing.EXPAND);
                    setPhase(Phase.EXPANDING, p,
                            lerp(Size.BASE_W, animatedWidth, p),
                            lerp(Size.BASE_H, targetHeight, p),
                            lerp(1f, 1f, p));
                } else {
                    setPhase(Phase.DISPLAY, 1f, animatedWidth, targetHeight, 1f);
                }
            } else if (phase == Phase.DISPLAY) {
                setPhase(Phase.DISPLAY, 1f, animatedWidth, targetHeight, 1f);
            } else {
                phase = Phase.EXPANDING;
                toggleStartTime = System.currentTimeMillis();
            }
            animX = (mc.getWindow().getScaledWidth() - animW) / 2f;
            animY = y;
            this.width = animW;
            this.height = animH;
            this.x = animX;
            return;
        }

        if (phase == Phase.TAB_EXPAND) {
            if (tabDt < Timing.TAB_TRANSITION) {
                float mergeT = clamp(tabDt / (Timing.TAB_TRANSITION * 0.45f), 0f, 1f);
                float expandT = clamp((tabDt - Timing.TAB_TRANSITION * 0.25f) / (Timing.TAB_TRANSITION * 0.75f), 0f, 1f);
                float mergeP = easeOut(mergeT);
                float expandP = easeOut(expandT);
                tabMergeProgress = mergeP;
                setPhase(Phase.TAB_EXPAND, expandP,
                        lerp(Size.BASE_W, tabTargetW, mergeP),
                        lerp(Size.BASE_H, tabTargetH, expandP),
                        1f);
            } else {
                tabMergeProgress = 1f;
                setPhase(Phase.TAB_DISPLAY, 1f, tabTargetW, tabTargetH, 1f);
            }
        } else if (phase == Phase.TAB_COLLAPSE) {
            if (tabDt < Timing.TAB_TRANSITION) {
                float mergeT = clamp(1f - (tabDt / (Timing.TAB_TRANSITION * 0.45f)), 0f, 1f);
                float expandT = clamp(1f - ((tabDt - Timing.TAB_TRANSITION * 0.10f) / (Timing.TAB_TRANSITION * 0.90f)), 0f, 1f);
                float mergeP = easeOut(mergeT);
                float expandP = easeOut(expandT);
                tabMergeProgress = mergeP;
                setPhase(Phase.TAB_COLLAPSE, expandP,
                        lerp(Size.BASE_W, tabTargetW, mergeP),
                        lerp(Size.BASE_H, tabTargetH, expandP),
                        1f);
            } else {
                tabMergeProgress = 0f;
                float idleW = getUnifiedIdleWidth();
                float idleH = getUnifiedIdleHeight();
                setPhase(Phase.IDLE, 0f, idleW, idleH, 1f);
                tabStartTime = -1L;
            }
        } else if (phase == Phase.TAB_DISPLAY) {
            tabMergeProgress = 1f;
            setPhase(Phase.TAB_DISPLAY, 1f, tabTargetW, tabTargetH, 1f);
        } else {
            if (currentToggle == null && currentKill == null && currentWin == null && toggleStartTime == -1L && !isNoTotem && !isSorting && !isAutoEatEating && !isAutoMineWorking && !isMusicPlaying) {
                closingSorting = false;
                float idleW = getUnifiedIdleWidth();
                float idleH = getUnifiedIdleHeight();
                setPhase(Phase.IDLE, 0f, idleW, idleH, 1f);
            } else if (dt < Timing.EXPAND) {
                float p = easeOut(dt / (float) Timing.EXPAND);
                float targetHeight = getUnifiedTargetHeight();
                targetHeight = getUnifiedAnimatedHeight(targetHeight);
                float animatedWidth = getUnifiedAnimatedWidth(targetExpandedWidth);
                setPhase(Phase.EXPANDING, p,
                        lerp(Size.BASE_W, animatedWidth, p),
                        lerp(Size.BASE_H, targetHeight, p),
                        lerp(1f, 1f, p));
            } else if (dt < Timing.EXPAND + Timing.DISPLAY) {
                float targetHeight = getUnifiedTargetHeight();
                targetHeight = getUnifiedAnimatedHeight(targetHeight);
                float p = (dt - Timing.EXPAND) / (float) Timing.DISPLAY;
                float animatedWidth = getUnifiedAnimatedWidth(targetExpandedWidth);
                setPhase(Phase.DISPLAY, p, animatedWidth, targetHeight, 1f);
            } else if (dt < Timing.EXPAND + Timing.DISPLAY + Timing.COLLAPSE_1) {
                float p = easeOut((dt - Timing.EXPAND - Timing.DISPLAY) / (float) Timing.COLLAPSE_1);
                float targetHeight = getUnifiedTargetHeight();
                targetHeight = getUnifiedAnimatedHeight(targetHeight);
                float animatedWidth = getUnifiedAnimatedWidth(targetExpandedWidth);
                setPhase(Phase.COLLAPSE_1, p, animatedWidth, targetHeight, 1f);
            } else {
                float timeInCollapse2 = dt - Timing.EXPAND - Timing.DISPLAY - Timing.COLLAPSE_1;
                float idleW = getUnifiedIdleWidth();
                float idleH = getUnifiedIdleHeight();
                if (timeInCollapse2 >= Timing.COLLAPSE_2) {
                    toggleStartTime = -1L;
                    closingSorting = false;
                    setPhase(Phase.IDLE, 0f, idleW, idleH, 1f);
                } else {
                    float p = easeOut(timeInCollapse2 / (float) Timing.COLLAPSE_2);
                    float targetHeight = getUnifiedTargetHeight();
                    targetHeight = getUnifiedAnimatedHeight(targetHeight);
                    float animatedWidth = getUnifiedAnimatedWidth(targetExpandedWidth);
                    setPhase(Phase.COLLAPSE_2, p,
                            lerp(animatedWidth, idleW, p),
                            lerp(targetHeight, idleH, p),
                            1f);
                }
            }
        }

        animX = (mc.getWindow().getScaledWidth() - animW) / 2f;
        animY = y;
        this.width = animW;
        this.height = animH;
        this.x = animX;
    }

    public float getRadius() {
        return radius.get().floatValue();
    }

    private void setPhase(Phase p, float prog, float w, float h, float blur) {
        this.phase = p;
        this.progress = prog;
        this.animW = w;
        this.animH = h;
        this.blurOpacity = interpolateBlurOpacity(blur);
    }

    private boolean isTabPhase() {
        return phase == Phase.TAB_EXPAND || phase == Phase.TAB_DISPLAY || phase == Phase.TAB_COLLAPSE;
    }

    private float getMergeProgress() {
        if (phase == Phase.TAB_EXPAND || phase == Phase.TAB_DISPLAY || phase == Phase.TAB_COLLAPSE)
            return tabMergeProgress;
        return progress;
    }

    private float interpolateBlurOpacity(float targetBlur) {
        float delta = targetBlur - this.blurOpacity;
        float interpolationFactor = 0.15f;
        return this.blurOpacity + delta * interpolationFactor;
    }

    private float getSideBlurOpacity() {
        if (isTabPhase()) {
            return (phase == Phase.TAB_EXPAND) ? (1f - tabMergeProgress) : (phase == Phase.TAB_COLLAPSE ? tabMergeProgress : 0f);
        }
        return 1f;
    }

    private void renderBlur(DrawContext context) {
        if (!blur.get()) return;

        if (isUnifiedLayout() && (hasUnifiedPrompt() || hasUnifiedPillTransient()) && !isTabPhase()) {
            renderUnifiedPillBlurs(context);
            return;
        }

        float clampedBlurOpacity = Math.max(0f, Math.min(1f, blurOpacity));
        if (clampedBlurOpacity <= 0.005f) return;

        Shader2DUtil.drawRoundedBlur(
                new MatrixStack(), animX, animY, animW, animH, getRadius(),
                new Color(0, 0, 0, 0), blurStrength.get().floatValue(), clampedBlurOpacity
        );
    }

    private void renderUnifiedPillBlurs(DrawContext context) {
        if (!blur.get()) return;

        float baseOpacity = Math.max(0f, Math.min(1f, blurOpacity));
        if (baseOpacity <= 0.005f) return;

        long now = System.currentTimeMillis();
        updateUnifiedPills(now);

        List<PromptLine> desired = getUnifiedDesiredLines();
        int desiredCount = desired.size();
        if (desiredCount <= 0 && unifiedPills.values().stream().noneMatch(s -> s.leaving)) {
            unifiedPills.clear();
            return;
        }

        float phaseAlpha = 1f;
        if (phase == Phase.EXPANDING) {
            phaseAlpha = progress;
        } else if (phase == Phase.COLLAPSE_1 || phase == Phase.COLLAPSE_2) {
            phaseAlpha = 1f - progress;
        }
        phaseAlpha = clamp(phaseAlpha, 0f, 1f);

        float strengthMul = 1f + Math.max(0, desiredCount - 1) * 0.25f;
        float strength = blurStrength.get().floatValue() * strengthMul;

        for (UnifiedPillState st : List.copyOf(unifiedPills.values())) {
            float alphaMul = 1f;
            float hMul = 1f;
            float y;

            if (st.entering) {
                float t = clamp((now - st.enterStart) / (float) UNIFIED_PILL_ENTER_MS, 0f, 1f);
                float p = easeOut(t);
                y = lerp(st.enterFromY, st.moveToY, p);
                alphaMul = p;
                hMul = p;
            } else if (st.leaving) {
                float t = clamp((now - st.leaveStart) / (float) UNIFIED_PILL_LEAVE_MS, 0f, 1f);
                float p = easeOut(t);
                y = lerp(st.leaveFromY, st.leaveToY, p);
                alphaMul = 1f - p;
                if (st.mergeIntoAbove) {
                    hMul = 1f - p;
                }
            } else {
                y = st.y;
            }

            float h = UNIFIED_PILL_H * hMul;
            if (h <= 1f) continue;

            float opacity = baseOpacity * phaseAlpha * alphaMul;
            if (opacity <= 0.005f) continue;

            Shader2DUtil.drawRoundedBlur(
                    new MatrixStack(), animX, y, animW, h, getRadius(),
                    new Color(0, 0, 0, 0), strength, opacity
            );
        }
    }

    private void renderSideBlurs(DrawContext context, float opacity) {
        if (layout.get() != Layout.Classic) return;
        if (!blur.get() || opacity <= 0.05f) return;

        float clampedBlurOpacity = Math.max(0f, Math.min(1f, blurOpacity * opacity));
        float timeBgX = animX - Size.ELEMENT_SPACING - Size.ELEMENT_WIDTH;

        if (phase == Phase.TAB_EXPAND) {
            timeBgX = lerp(timeBgX, animX, tabMergeProgress);
        } else if (phase == Phase.TAB_COLLAPSE) {
            timeBgX = lerp(timeBgX, animX, tabMergeProgress);
        }

        Shader2DUtil.drawRoundedBlur(
                new MatrixStack(), timeBgX, animY, Size.ELEMENT_WIDTH, animH, getRadius(),
                new Color(0, 0, 0, 0), blurStrength.get().floatValue(), clampedBlurOpacity
        );
        float nameBgX = animX + animW + Size.ELEMENT_SPACING;
        if (phase == Phase.TAB_EXPAND) {
            nameBgX = lerp(nameBgX, animX + animW - Size.ELEMENT_WIDTH, tabMergeProgress);
        } else if (phase == Phase.TAB_COLLAPSE) {
            nameBgX = lerp(nameBgX, animX + animW - Size.ELEMENT_WIDTH, tabMergeProgress);
        }

        Shader2DUtil.drawRoundedBlur(
                new MatrixStack(), nameBgX, animY, Size.ELEMENT_WIDTH, animH, getRadius(),
                new Color(0, 0, 0, 0), blurStrength.get().floatValue(), clampedBlurOpacity
        );
    }

    private void renderContent() {
        switch (phase) {
            case IDLE -> renderIdle();
            case EXPANDING -> renderExpanding();
            case DISPLAY -> renderDisplay();
            case COLLAPSE_1 -> renderCollapse1();
            case COLLAPSE_2 -> renderCollapse2();
            case TAB_EXPAND -> renderTabExpand();
            case TAB_DISPLAY -> renderTabDisplay();
            case TAB_COLLAPSE -> renderTabCollapse();
        }
    }

    private float calculateSortingWidth() {
        int font = FontLoader.bold(12);
        String text = isChinese() ? "整理中" : "Sorting";
        float textW = NanoVGHelper.getTextWidth(text, font, 12);
        return Math.max(Size.EXPANDED_W, textW + 10 + 60 + 20);
    }

    private float calculateAutoEatWidth() {
        int font = FontLoader.bold(12);
        String text = isChinese() ? "进食中" : "Eating";
        float textW = NanoVGHelper.getTextWidth(text, font, 12);
        return Math.max(Size.EXPANDED_W, textW + 10 + 60 + 20);
    }

    private float calculateAutoMineWidth() {
        int font = FontLoader.medium((int) Size.LOGO_FONT_SIZE);
        float textSize = Size.LOGO_FONT_SIZE - 2f;
        String label = isChinese() ? "自动挖掘" : "AutoMine";
        float labelW = NanoVGHelper.getTextWidth(label, font, textSize);
        float padding = 8f;
        if (autoMineAreaMode) {
            String status = autoMineAreaProgress >= 0.999f ? (isChinese() ? "完成!" : "Complete!") : ((int) Math.round(autoMineAreaProgress * 100f) + "%");
            float statusW = NanoVGHelper.getTextWidth(status, font, textSize);
            float needed = padding * 2 + labelW + 8f + statusW;
            return Math.max(Size.EXPANDED_W, needed);
        } else {
            float trackW = 28f;
            float needed = padding * 2 + labelW + 10f + trackW;
            return Math.max(Size.EXPANDED_W, needed);
        }
    }

    private float calculateMusicWidth() {
        int font = FontLoader.medium((int) Size.LOGO_FONT_SIZE);
        float textSize = Size.LOGO_FONT_SIZE - 2f;
        String title = musicTitle == null || musicTitle.isBlank() ? (isChinese() ? "音乐" : "Music") : musicTitle;
        float titleW = NanoVGHelper.getTextWidth(title, font, textSize);
        float maxTitleW = 140f;
        float barsW = 22f;
        float padding = 8f;
        float spacing = 10f;
        float needed = padding * 2 + Math.min(titleW, maxTitleW) + spacing + barsW;
        return Math.max(Size.EXPANDED_W, needed);
    }

    private void drawProgressFillText(String text, float x, float baselineY, int font, float size, int alpha, Color fill, float progress) {
        float w = NanoVGHelper.getTextWidth(text, font, size);
        NanoVGHelper.drawString(text, x, baselineY, font, size, withAlpha(Color.WHITE, alpha));
        float clipW = w * clamp(progress, 0f, 1f);
        if (clipW <= 0.5f) return;
        NanoVGHelper.saveScissor();
        NanoVGHelper.intersectScissor(x, baselineY - size, clipW, size * 2f);
        NanoVGHelper.drawString(text, x, baselineY, font, size, withAlpha(fill, alpha));
        NanoVGHelper.restoreScissor();
    }

    private void drawVolumeBars(float x, float centerY, float volume01, int alpha) {
        int bars = 5;
        float barW = 2.5f;
        float gap = 2.0f;
        float totalW = bars * barW + (bars - 1) * gap;

        float bottom = centerY + 6f;
        float startX = x - totalW;
        long now = System.currentTimeMillis();
        long bucket = now / 140L;
        float tBucket = (now % 140L) / 140f;
        float tSmooth = tBucket * tBucket * (3f - 2f * tBucket);
        for (int i = 0; i < bars; i++) {
            float r0 = hash01(bucket * 1315423911L + i * 2654435761L);
            float r1 = hash01((bucket + 1L) * 1315423911L + i * 2654435761L);
            float r = lerp(r0, r1, tSmooth);
            float shape = 0.55f + 0.45f * (i / (float) (bars - 1));
            float h = (6f + r * 18f) * shape;
            float volMul = 0.35f + 0.65f * clamp(volume01, 0f, 1f);
            h *= volMul;
            float bx = startX + i * (barW + gap);
            float by = bottom - h;
            float th = (i + 1) / (float) bars;
            boolean on = volume01 >= th - 0.001f;
            Color c = on ? ClickGui.color(0) : new Color(255, 255, 255, 70);
            if (!on) {
                h = Math.max(2f, h * 0.55f);
                by = bottom - h;
            }
            NanoVGHelper.drawRoundRect(bx, by, barW, h, 1.2f, withAlpha(c, alpha));
        }
    }

    private static float hash01(long x) {
        x ^= (x >>> 33);
        x *= 0xff51afd7ed558ccdL;
        x ^= (x >>> 33);
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= (x >>> 33);
        long v = x & 0xFFFFFFFFL;
        return v / (float) 0xFFFFFFFFL;
    }

    private void drawSortingInfo(int alpha) {
        if (alpha <= 5 || invManager == null) return;
        float centerY = animY + animH / 2f;
        drawSortingInfoAt(alpha, centerY);
    }

    private void drawSortingInfoAt(int alpha, float centerY) {
        if (alpha <= 5 || invManager == null) return;
        int font = FontLoader.bold(12);
        String text = isChinese() ? "整理中" : "Sorting";
        float textW = NanoVGHelper.getTextWidth(text, font, 12);

        float barW = 60f;
        float spacing = 10f;
        float totalContentW = textW + spacing + barW;

        float startX = animX + (animW - totalContentW) / 2f;

        float textY = centerY + 2f;
        NanoVGHelper.drawString(text, startX, textY, font, 12, withAlpha(Color.WHITE, alpha));

        float barH = 4;
        float barX = startX + textW + spacing;
        float barY = centerY - barH / 2f - 1f;

        float targetProg = (float) invManager.pendingActions / maxPendingActions;
        targetProg = Math.max(0f, Math.min(1f, targetProg));
        float prog = targetProg;

        NanoVGHelper.drawRoundRect(barX, barY, barW, barH, barH / 2, withAlpha(new Color(255, 255, 255, 50), alpha));
        if (prog > 0) {
            NanoVGHelper.drawRoundRect(barX, barY, barW * prog, barH, barH / 2, withAlpha(ClickGui.color(0), alpha));
        }
    }

    private void drawAutoEatInfoAt(int alpha, float centerY) {
        if (alpha <= 5 || !isAutoEatEating) return;
        int font = FontLoader.bold(12);
        String text = isChinese() ? "进食中" : "Eating";
        float textW = NanoVGHelper.getTextWidth(text, font, 12);

        float barW = 60f;
        float spacing = 10f;
        float totalContentW = textW + spacing + barW;
        float startX = animX + (animW - totalContentW) / 2f;

        float textY = centerY + 2f;
        NanoVGHelper.drawString(text, startX, textY, font, 12, withAlpha(Color.WHITE, alpha));

        float barH = 4;
        float barX = startX + textW + spacing;
        float barY = centerY - barH / 2f - 1f;

        float prog = clamp(autoEatProgress01, 0f, 1f);
        Color yellow = new Color(255, 210, 0);
        NanoVGHelper.drawRoundRect(barX, barY, barW, barH, barH / 2, withAlpha(new Color(255, 255, 255, 50), alpha));
        if (prog > 0) {
            NanoVGHelper.drawRoundRect(barX, barY, barW * prog, barH, barH / 2, withAlpha(yellow, alpha));
        }
    }

    private void drawAutoMineInfoAt(int alpha, float centerY) {
        if (alpha <= 5 || !isAutoMineWorking) return;

        int font = FontLoader.medium((int) Size.LOGO_FONT_SIZE);
        float textSize = Size.LOGO_FONT_SIZE - 2f;
        String label = isChinese() ? "自动挖掘" : "AutoMine";
        float labelW = NanoVGHelper.getTextWidth(label, font, textSize);
        float y = centerY + textSize * 0.35f;

        if (autoMineAreaMode) {
            boolean complete = autoMineAreaProgress >= 0.999f;
            String pct = complete ? (isChinese() ? "完成!" : "Complete!") : ((int) Math.round(autoMineAreaProgress * 100f) + "%");
            float pctW = NanoVGHelper.getTextWidth(pct, font, textSize);
            float spacing = 6f;
            float totalW = labelW + spacing + pctW;
            float startX = animX + (animW - totalW) / 2f;
            NanoVGHelper.drawString(label, startX, y, font, textSize, withAlpha(Color.WHITE, alpha));
            float p = complete ? 1f : clamp(autoMineAreaProgress, 0f, 1f);
            drawProgressFillText(pct, startX + labelW + spacing, y, font, textSize, alpha, ClickGui.color(0), p);
        } else {
            float trackW = 28f;
            float spacing = 10f;
            float totalW = labelW + spacing + trackW;
            float startX = animX + (animW - totalW) / 2f;
            NanoVGHelper.drawString(label, startX, y, font, textSize, withAlpha(Color.WHITE, alpha));

            float trackH = 3.5f;
            float trackX = startX + labelW + spacing;
            float cy = centerY + 1f;
            float trackY = cy - trackH / 2f - 2f;
            NanoVGHelper.drawRoundRect(trackX, trackY, trackW, trackH, trackH / 2f, withAlpha(new Color(255, 255, 255, 50), alpha));

            float p = 0.5f;
            if (autoMineActive) {
                float t = (float) (System.currentTimeMillis() % 900L) / 900f;
                p = (float) ((Math.sin(t * Math.PI * 2) + 1) / 2);
            }
            float dotX = trackX + 2f + p * (trackW - 4f);
            NanoVGHelper.drawCircle(dotX, cy, 2.2f, withAlpha(ClickGui.color(0), alpha));
        }
    }

    private void drawMusicInfoAt(int alpha, float centerY) {
        if (alpha <= 5 || !isMusicPlaying) return;
        int font = FontLoader.medium((int) Size.LOGO_FONT_SIZE);
        float textSize = Size.LOGO_FONT_SIZE - 2f;
        String title = musicTitle == null || musicTitle.isBlank() ? (isChinese() ? "音乐" : "Music") : musicTitle;
        float y = centerY + textSize * 0.35f;

        float padding = 8f;
        float barsW = 22f;
        float spacing = 10f;
        float rightX = animX + animW - padding;
        float barsRightX = rightX;
        float textX = animX + padding;
        float clipW = Math.max(0f, (barsRightX - barsW - spacing) - textX);
        NanoVGHelper.saveScissor();
        NanoVGHelper.intersectScissor(textX, y - textSize, clipW, textSize * 2f);
        NanoVGHelper.drawString(title, textX, y, font, textSize, withAlpha(Color.WHITE, alpha));
        NanoVGHelper.restoreScissor();

        drawVolumeBars(barsRightX, centerY, musicVolume01, alpha);
    }

    private net.minecraft.screen.GenericContainerScreenHandler getChestHandler() {
        if (!chestOverlayDesired) return null;
        if (!(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.GenericContainerScreen gen)) return null;
        if (!(gen.getScreenHandler() instanceof net.minecraft.screen.GenericContainerScreenHandler h)) return null;
        return h;
    }

    private float updateChestOverlayAlpha(long now, boolean desired) {
        long dt;
        if (chestOverlayAlphaLastMs == 0L) {
            dt = 16L;
        } else {
            dt = Math.max(0L, now - chestOverlayAlphaLastMs);
        }
        chestOverlayAlphaLastMs = now;
        long fadeInMs = Math.max(1L, (long) Math.round(Timing.EXPAND * 0.9));
        long fadeOutMs = Math.max(1L, (long) Math.round((Timing.COLLAPSE_1 + Timing.COLLAPSE_2) * 0.8));
        float step = desired ? (dt / (float) fadeInMs) : (dt / (float) fadeOutMs);
        chestOverlayAlpha = clamp(chestOverlayAlpha + (desired ? step : -step), 0f, 1f);
        return chestOverlayAlpha;
    }

    private void updateChestSlotVisualsFromHandler(net.minecraft.screen.GenericContainerScreenHandler h, long now) {
        int slots = h.getRows() * 9;
        for (int i = 0; i < slots; i++) {
            net.minecraft.screen.slot.Slot s = h.getSlot(i);
            ItemStack st = (s != null) ? s.getStack() : ItemStack.EMPTY;
            if (st == null) st = ItemStack.EMPTY;

            ChestSlotVisual v = chestSlotVisuals.get(i);
            if (st.isEmpty()) {
                if (v != null) {
                    v.targetAlpha = 0f;
                }
                continue;
            }

            if (v == null || !isSameChestStack(v.stack, st)) {
                chestSlotVisuals.put(i, new ChestSlotVisual(st.copy(), 0f, 1f, now));
            } else {
                v.stack = st.copy();
                v.targetAlpha = 1f;
            }
        }

        Iterator<Map.Entry<Integer, ChestSlotVisual>> it = chestSlotVisuals.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, ChestSlotVisual> e = it.next();
            if (e.getKey() >= slots) {
                e.getValue().targetAlpha = 0f;
            }
        }
    }

    private void tickChestSlotVisuals(long now) {
        long fadeInMs = Math.max(1L, (long) Math.round(Timing.EXPAND * 0.9));
        long fadeOutMs = Math.max(1L, (long) Math.round((Timing.COLLAPSE_1 + Timing.COLLAPSE_2) * 0.8));
        Iterator<Map.Entry<Integer, ChestSlotVisual>> it = chestSlotVisuals.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, ChestSlotVisual> e = it.next();
            ChestSlotVisual v = e.getValue();
            if (v.lastMs == 0L) v.lastMs = now;
            long dt = Math.max(0L, now - v.lastMs);
            v.lastMs = now;
            if (v.alpha < v.targetAlpha) {
                float step = dt / (float) fadeInMs;
                v.alpha = clamp(v.alpha + step, 0f, v.targetAlpha);
            } else if (v.alpha > v.targetAlpha) {
                float step = dt / (float) fadeOutMs;
                v.alpha = clamp(v.alpha - step, v.targetAlpha, 1f);
            }
            if (v.targetAlpha <= 0.001f && v.alpha <= 0.01f) {
                it.remove();
            }
        }
    }

    private boolean isSameChestStack(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (!ItemStack.areItemsEqual(a, b)) return false;
        String an = a.getName().getString();
        String bn = b.getName().getString();
        return an.equals(bn);
    }

    private float getChestRenderAlpha01() {
        if (isTabPhase()) return 0f;
        float p = switch (phase) {
            case EXPANDING -> progress;
            case DISPLAY -> 1f;
            case COLLAPSE_1, COLLAPSE_2 -> 1f - progress;
            default -> 1f;
        };
        return clamp(p * chestOverlayAlpha, 0f, 1f);
    }

    private String getActivePromptTextSnapshot() {
        if (currentKill != null) return currentKill.name();
        if (currentWin != null) return isChinese() ? "胜利" : "Win";
        if (isNoTotem) return isChinese() ? "缺少图腾" : "NoTotem";
        if (isAutoEatEating) return isChinese() ? "进食中" : "Eating";
        if (isSorting) return isChinese() ? "整理中" : "Sorting";
        if (isMusicPlaying) {
            if (musicTitle != null && !musicTitle.isBlank()) return musicTitle;
            return isChinese() ? "音乐" : "Music";
        }
        if (currentToggle != null) return currentToggle.name();
        if (isAutoMineWorking) return isChinese() ? "自动挖掘" : "AutoMine";
        return null;
    }

    private float getChestPadding() {
        return 10f;
    }

    private float getChestCell() {
        return 16f;
    }

    private float getChestGap() {
        return 2f;
    }

    private float getChestHeaderH() {
        return 8f;
    }

    private float calculateChestWidth() {
        float padding = getChestPadding();
        float cell = getChestCell();
        float gap = getChestGap();
        float gridW = 9 * cell + 8 * gap;
        return Math.max(Size.EXPANDED_W, gridW + padding * 2f);
    }

    private float calculateChestHeight() {
        net.minecraft.screen.GenericContainerScreenHandler h = getChestHandler();
        if (h == null) return Size.EXPANDED_H;
        float padding = getChestPadding();
        float cell = getChestCell();
        float gap = getChestGap();
        int rows = h.getRows();
        float gridH = rows * cell + Math.max(0, rows - 1) * gap;
        return Math.max(Size.EXPANDED_H, getChestHeaderH() + padding + gridH + padding);
    }

    private boolean isChestEmpty(net.minecraft.screen.GenericContainerScreenHandler h) {
        int slots = h.getRows() * 9;
        for (int i = 0; i < slots; i++) {
            net.minecraft.screen.slot.Slot s = h.getSlot(i);
            if (s != null && s.hasStack()) return false;
        }
        return true;
    }

    private void drawChestOverlay(int alpha) {
        if (chestTransitionOldText != null) {
            int a = (int) (255 * clamp(1f - chestOverlayAlpha, 0f, 1f));
            if (a > 5) {
                int font = FontLoader.bold(12);
                float textW = NanoVGHelper.getTextWidth(chestTransitionOldText, font, 12);
                float x = animX + (animW - textW) / 2f;
                float y = animY + 12f;
                NanoVGHelper.drawString(chestTransitionOldText, x, y, font, 12, withAlpha(Color.WHITE, a));
            }
        }
    }

    private void drawChestGrid(DrawContext context) {
        if (!chestOverlayDesired) return;
        float padding = getChestPadding();
        float cell = getChestCell();
        float gap = getChestGap();
        int rows = Math.max(1, chestRows);
        float gridW = 9 * cell + 8 * gap;
        float gridX = animX + (animW - gridW) / 2f;
        float gridY = animY + getChestHeaderH() + padding;
        float baseAlpha = getChestRenderAlpha01();
        if (baseAlpha <= 0.01f) return;

        GlStateManager._enableBlend();
        for (Map.Entry<Integer, ChestSlotVisual> e : chestSlotVisuals.entrySet()) {
            int i = e.getKey();
            ChestSlotVisual v = e.getValue();
            if (v == null || v.stack == null || v.stack.isEmpty()) continue;
            float a = clamp(baseAlpha * v.alpha, 0f, 1f);
            if (a <= 0.01f) continue;

            int r = i / 9;
            int c = i % 9;
            if (r >= rows) continue;
            float x = gridX + c * (cell + gap);
            float y = gridY + r * (cell + gap);

            context.drawItem(v.stack, (int) x, (int) y);
            if (v.stack.getCount() > 1) {
                String t = String.valueOf(v.stack.getCount());
                int tx = (int) (x + cell - 1 - mc.textRenderer.getWidth(t));
                int ty = (int) (y + cell - 9);
                int col = ((int) (a * 255) << 24) | 0xFFFFFF;
                context.drawTextWithShadow(mc.textRenderer, t, tx, ty, col);
            }
        }
        GlStateManager._disableBlend();
    }
    private void drawPillSorting(float x, float y, float w, float h, int alpha) {
        if (alpha <= 5 || invManager == null) return;
        int font = FontLoader.bold(12);
        String text = isChinese() ? "整理中" : "Sorting";
        float textW = NanoVGHelper.getTextWidth(text, font, 12);

        float yOff = progressStyle.is(ProgressStyle.Background) ? 1.5f : 0f;
        float centerY = y + h / 2f + yOff;
        float barW = 60f;
        float spacing = 10f;
        float totalContentW = textW + spacing + barW;
        float startX = x + (w - totalContentW) / 2f;

        float textY = centerY + 2f;
        NanoVGHelper.drawString(text, startX, textY, font, 12, withAlpha(Color.WHITE, alpha));

        float barH = 4;
        float barX = startX + textW + spacing;
        float barY = centerY - barH / 2f - 1f;

        float targetProg = (float) invManager.pendingActions / maxPendingActions;
        targetProg = Math.max(0f, Math.min(1f, targetProg));
        float prog = targetProg;

        NanoVGHelper.drawRoundRect(barX, barY, barW, barH, barH / 2, withAlpha(new Color(255, 255, 255, 50), alpha));
        if (prog > 0) {
            NanoVGHelper.drawRoundRect(barX, barY, barW * prog, barH, barH / 2, withAlpha(ClickGui.color(0), alpha));
        }
    }

    private void drawPillAutoEat(float x, float y, float w, float h, int alpha) {
        if (alpha <= 5 || !isAutoEatEating) return;
        int font = FontLoader.bold(12);
        String text = isChinese() ? "进食中" : "Eating";
        float textW = NanoVGHelper.getTextWidth(text, font, 12);

        float yOff = progressStyle.is(ProgressStyle.Background) ? 1.5f : 0f;
        float centerY = y + h / 2f + yOff;
        float barW = 60f;
        float spacing = 10f;
        float totalContentW = textW + spacing + barW;
        float startX = x + (w - totalContentW) / 2f;

        float textY = centerY + 2f;
        NanoVGHelper.drawString(text, startX, textY, font, 12, withAlpha(Color.WHITE, alpha));

        float barH = 4;
        float barX = startX + textW + spacing;
        float barY = centerY - barH / 2f - 1f;

        float prog = clamp(autoEatProgress01, 0f, 1f);
        Color yellow = new Color(255, 210, 0);
        NanoVGHelper.drawRoundRect(barX, barY, barW, barH, barH / 2, withAlpha(new Color(255, 255, 255, 50), alpha));
        if (prog > 0) {
            NanoVGHelper.drawRoundRect(barX, barY, barW * prog, barH, barH / 2, withAlpha(yellow, alpha));
        }
    }

    private void drawPillAutoMine(float x, float y, float w, float h, int alpha) {
        if (alpha <= 5 || !isAutoMineWorking) return;

        float padding = 8f;
        int font = FontLoader.medium((int) Size.LOGO_FONT_SIZE);
        float textSize = Size.LOGO_FONT_SIZE - 2f;
        String label = isChinese() ? "自动挖掘" : "AutoMine";
        float labelW = NanoVGHelper.getTextWidth(label, font, textSize);

        float yOff = progressStyle.is(ProgressStyle.Background) ? 1.5f : 0f;
        float centerY = y + (h - 3) / 2f + yOff;

        if (autoMineAreaMode) {
            boolean complete = autoMineAreaProgress >= 0.999f;
            String pct = complete ? (isChinese() ? "完成!" : "Complete!") : ((int) Math.round(autoMineAreaProgress * 100f) + "%");
            float pctW = NanoVGHelper.getTextWidth(pct, font, textSize);
            float labelX = x + padding;
            float pctX = x + w - padding - pctW;

            NanoVGHelper.drawString(label, labelX, centerY + textSize * 0.35f, font, textSize, withAlpha(Color.WHITE, alpha));
            float baselineY = centerY + textSize * 0.35f;
            float p = complete ? 1f : clamp(autoMineAreaProgress, 0f, 1f);
            drawProgressFillText(pct, pctX, baselineY, font, textSize, alpha, ClickGui.color(0), p);
        } else {
            NanoVGHelper.drawString(label, x + padding, centerY + textSize * 0.35f, font, textSize, withAlpha(Color.WHITE, alpha));

            float trackW = 28f;
            float trackH = 3.5f;
            float trackX = x + w - padding - trackW;
            float cy = y + h / 2f + yOff;
            float trackY = cy - trackH / 2f;
            NanoVGHelper.drawRoundRect(trackX, trackY, trackW, trackH, trackH / 2f, withAlpha(new Color(255, 255, 255, 50), alpha));

            float p = 0.5f;
            if (autoMineActive) {
                float t = (float) (System.currentTimeMillis() % 900L) / 900f;
                p = (float) ((Math.sin(t * Math.PI * 2) + 1) / 2);
            }
            float dotX = trackX + 2f + p * (trackW - 4f);
            NanoVGHelper.drawCircle(dotX, cy, 2.2f, withAlpha(ClickGui.color(0), alpha));
        }
    }

    private void drawPillMusic(float x, float y, float w, float h, int alpha) {
        if (alpha <= 5 || !isMusicPlaying) return;
        int font = FontLoader.medium((int) Size.LOGO_FONT_SIZE);
        float textSize = Size.LOGO_FONT_SIZE - 2f;
        String title = musicTitle == null || musicTitle.isBlank() ? (isChinese() ? "音乐" : "Music") : musicTitle;

        float padding = 8f;
        float barsW = 22f;
        float spacing = 10f;
        float yOff = progressStyle.is(ProgressStyle.Background) ? 1.5f : 0f;
        float centerY = y + (h - 3) / 2f + yOff;
        float baselineY = centerY + textSize * 0.35f;

        float rightX = x + w - padding;
        float textX = x + padding;
        float clipW = Math.max(0f, (rightX - barsW - spacing) - textX);
        NanoVGHelper.saveScissor();
        NanoVGHelper.intersectScissor(textX, baselineY - textSize, clipW, textSize * 2f);
        NanoVGHelper.drawString(title, textX, baselineY, font, textSize, withAlpha(Color.WHITE, alpha));
        NanoVGHelper.restoreScissor();

        drawVolumeBars(rightX, centerY, musicVolume01, alpha);
    }

    private void drawPillNoTotem(float x, float y, float w, float h, int alpha) {
        int font = FontLoader.bold(12);
        String text = isChinese() ? "无不死图腾" : "NoTotem";
        float textW = NanoVGHelper.getTextWidth(text, font, 12);
        float centerX = x + w / 2f;
        float yOff = progressStyle.is(ProgressStyle.Background) ? 1.5f : 0f;
        float centerY = y + h / 2f + 3 + yOff;
        NanoVGHelper.drawString(text, centerX - textW / 2f, centerY, font, 12, withAlpha(new Color(255, 80, 80), alpha));
    }

    private void drawPillToggle(float x, float y, float w, float h, int alpha, float timeProgress, boolean showProgress) {
        if (currentToggle == null) return;
        float padding = 6, iconSize = 16;
        int iconFont = FontLoader.icons(iconSize);
        String icon = currentToggle.enabled ? "U" : "T";
        Color iconColor = ClickGui.color(0);
        float iconW = NanoVGHelper.getTextWidth(icon, iconFont, iconSize);
        int textAlpha = applyTimedTextFade(alpha, timeProgress);

        float yOff = progressStyle.is(ProgressStyle.Background) ? 1.5f : 0f;
        float centerY = y + (h - 3) / 2f + yOff;
        NanoVGHelper.drawString(icon, x + padding + 6, centerY + iconSize * 0.35f - 1.5f, iconFont, iconSize, withAlpha(iconColor, textAlpha));

        int textFont = FontLoader.medium((int) Size.LOGO_FONT_SIZE);
        String status = currentToggle.name + (currentToggle.enabled ? (isChinese() ? " 已开启" : " enabled") : (isChinese() ? " 已关闭" : " disabled"));
        NanoVGHelper.drawString(status, x + padding + iconW + 14, centerY + Size.LOGO_FONT_SIZE * 0.35f, textFont, Size.LOGO_FONT_SIZE - 2f, withAlpha(Color.WHITE, textAlpha));

        if (showProgress && progressStyle.is(ProgressStyle.Bar)) {
            drawPillProgressBar(x, y, w, h, alpha, timeProgress, ClickGui.color(0));
        }
    }

    private void drawPillKill(float x, float y, float w, float h, int alpha, float timeProgress, boolean showProgress) {
        if (currentKill == null) return;
        float iconSize = 16;
        int iconFont = FontLoader.icons(iconSize);
        String icon = "D";
        Color iconColor = new Color(255, 50, 50);
        float iconW = NanoVGHelper.getTextWidth(icon, iconFont, iconSize);
        int textAlpha = applyTimedTextFade(alpha, timeProgress);

        int textFont = FontLoader.medium((int) Size.LOGO_FONT_SIZE);
        String status = (isChinese() ? "击杀 " : "Killed ") + currentKill.name;
        float textW = NanoVGHelper.getTextWidth(status, textFont, Size.LOGO_FONT_SIZE - 2f);
        float spacing = 3f;
        float totalW = iconW + spacing + textW;
        float startX = x + (w - totalW) / 2f;

        float yOff = progressStyle.is(ProgressStyle.Background) ? 1.5f : 0f;
        float centerY = y + (h - 3) / 2f + yOff;
        NanoVGHelper.drawString(icon, startX, centerY + iconSize * 0.35f, iconFont, iconSize, withAlpha(iconColor, textAlpha));
        NanoVGHelper.drawString(status, startX + iconW + spacing, centerY + Size.LOGO_FONT_SIZE * 0.35f, textFont, Size.LOGO_FONT_SIZE - 2f, withAlpha(Color.WHITE, textAlpha));

        if (showProgress && progressStyle.is(ProgressStyle.Bar)) {
            drawPillProgressBar(x, y, w, h, alpha, timeProgress, new Color(255, 120, 120));
        }
    }

    private void drawPillWin(float x, float y, float w, float h, int alpha, float timeProgress, boolean showProgress) {
        float iconSize = 16;
        int iconFont = FontLoader.icons(iconSize);
        int textFont = FontLoader.medium((int) Size.LOGO_FONT_SIZE);
        String icon = "G";
        Color iconColor = new Color(255, 210, 0);
        float iconW = NanoVGHelper.getTextWidth(icon, iconFont, iconSize);
        String status = "Win!";
        float textW = NanoVGHelper.getTextWidth(status, textFont, Size.LOGO_FONT_SIZE - 2f);
        float spacing = 4f;
        float totalW = iconW + spacing + textW;
        float startX = x + (w - totalW) / 2f;
        int textAlpha = applyTimedTextFade(alpha, timeProgress);

        float yOff = progressStyle.is(ProgressStyle.Background) ? 1.5f : 0f;
        float centerY = y + h / 2f + yOff;
        float baselineY = getCenteredBaselineY(iconFont, iconSize, icon, textFont, Size.LOGO_FONT_SIZE - 2f, status, centerY);
        NanoVGHelper.drawString(icon, startX, baselineY, iconFont, iconSize, NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_BASELINE, withAlpha(iconColor, textAlpha));
        NanoVGHelper.drawString(status, startX + iconW + spacing, baselineY - 1f, textFont, Size.LOGO_FONT_SIZE - 2f, NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_BASELINE, withAlpha(Color.WHITE, textAlpha));

        if (showProgress && progressStyle.is(ProgressStyle.Bar)) {
            drawPillProgressBar(x, y, w, h, alpha, timeProgress, new Color(255, 200, 0));
        }
    }

    private void drawPillProgressBar(float x, float y, float w, float h, int alpha, float timeProgress, Color barColor) {
        float padding = 8, barH = 1.5f;
        float barY = y + h - barH - 3;
        float maxW = Math.max(0, w - padding * 2);
        float progress = Math.max(0f, Math.min(1f, 1f - timeProgress));
        float currentW = maxW * progress;

        NanoVGHelper.drawRoundRect(x + padding, barY, maxW, barH, barH / 2, withAlpha(barColor, (int) (50 * (alpha / 255f))));
        if (currentW > 0) {
            NanoVGHelper.drawRoundRect(x + padding, barY, currentW, barH, barH / 2, withAlpha(barColor, (int) (220 * (alpha / 255f))));
        }
    }

    private void renderIdle() {
        if (layout.get() == Layout.Modern) {
            Shader2DUtil.drawRoundedBlur(new MatrixStack(), animX, animY, animW, animH, getRadius(), new Color(0, 0, 0, 0), blurStrength.get().floatValue(), 1f);
            NanoVGHelper.drawRoundRectBloom(animX, animY, animW, animH, getRadius(), withAlpha(new Color(20, 20, 20), backgroundAlpha.get()));
            NanoVGHelper.drawGradientRRect(animX + 5, animY, animW - 10, 1.5f, 1f, ClickGui.color(0), ClickGui.color2(0));
            drawCenteredTitle(1f);
            return;
        }
        drawBackground(withAlpha(Size.INVENTORY_BG_COLOR, backgroundAlpha.get()));
        if (isUnifiedLayout()) {
            drawCenteredTitle(1f);
        } else {
            drawSideInfo(0f, 1f);
            drawCenteredTitle(1f);
        }
    }

    private void renderExpanding() {
        if (layout.get() == Layout.Modern) {
            Shader2DUtil.drawRoundedBlur(new MatrixStack(), animX, animY, animW, animH, getRadius(), new Color(0, 0, 0, 0), blurStrength.get().floatValue(), progress);
            NanoVGHelper.drawRoundRectBloom(animX, animY, animW, animH, getRadius(), withAlpha(new Color(20, 20, 20), (int)(backgroundAlpha.get() * progress)));
            NanoVGHelper.drawGradientRRect(animX + 5, animY, animW - 10, 1.5f, 1f, withAlpha(ClickGui.color(0), alphaFromProgress(progress)), withAlpha(ClickGui.color2(0), alphaFromProgress(progress)));
        } else {
            drawBackground(withAlpha(Size.INVENTORY_BG_COLOR, backgroundAlpha.get()));
        }

        if (isUnifiedLayout() && (hasUnifiedPrompt() || hasUnifiedPillTransient()) && !isTabPhase()) {
            if (renderUnifiedPillStack(alphaFromProgress(progress), progress)) return;
        }
        if (isChestOverlayActive) {
            drawChestOverlay( alphaFromProgress(progress) );
            return;
        }
        if (layout.get() != Layout.Modern && !isUnifiedLayout()) {
            drawSideInfo(progress, 1f);
        }
        int lines = getUnifiedPromptLineCount();
        if (isUnifiedLayout() && lines >= 2) {
            drawUnifiedMultiPrompts(alphaFromProgress(progress), progress);
            return;
        }
        if (currentKill != null) {
            drawKillInfo(alphaFromProgress(progress), getKillTimeProgress());
        } else if (currentWin != null) {
            drawWinInfo(alphaFromProgress(progress), getWinTimeProgress());
        } else if (isNoTotem) {
            drawNoTotemInfo(alphaFromProgress(progress));
        } else if (isAutoEatEating) {
            float centerY = animY + (animH - 3) / 2f;
            drawAutoEatInfoAt(alphaFromProgress(progress), centerY);
        } else if (isSorting) {
            drawSortingInfo(alphaFromProgress(progress));
        } else if (isMusicPlaying) {
            float centerY = animY + (animH - 3) / 2f;
            drawMusicInfoAt(alphaFromProgress(progress), centerY);
        } else if (currentToggle != null) {
            drawToggleInfo(alphaFromProgress(progress), getToggleTimeProgress());
        }
        if (isUnifiedLayout() && !hasUnifiedPrompt()) {
            drawCenteredTitle(1f);
        }
    }

    private void renderDisplay() {
        if (layout.get() == Layout.Modern) {
            Shader2DUtil.drawRoundedBlur(new MatrixStack(), animX, animY, animW, animH, getRadius(), new Color(0, 0, 0, 0), blurStrength.get().floatValue(), 1f);
            NanoVGHelper.drawRoundRectBloom(animX, animY, animW, animH, getRadius(), withAlpha(new Color(20, 20, 20), backgroundAlpha.get()));
            NanoVGHelper.drawGradientRRect(animX + 5, animY, animW - 10, 1.5f, 1f, ClickGui.color(0), ClickGui.color2(0));
        } else {
            drawBackground(withAlpha(Size.INVENTORY_BG_COLOR, backgroundAlpha.get()));
        }

        if (isUnifiedLayout() && (hasUnifiedPrompt() || hasUnifiedPillTransient()) && !isTabPhase()) {
            if (renderUnifiedPillStack(255, progress)) return;
        }
        if (isChestOverlayActive) {
            drawChestOverlay(255);
            return;
        }
        if (layout.get() != Layout.Modern && !isUnifiedLayout()) {
            drawSideInfo(1f, 1f);
        }
        int lines = getUnifiedPromptLineCount();
        if (isUnifiedLayout() && lines >= 2) {
            drawUnifiedMultiPrompts(255, progress);
            return;
        }
        if (currentKill != null) {
            drawKillInfo(255, getKillTimeProgress());
        } else if (currentWin != null) {
            drawWinInfo(255, getWinTimeProgress());
        } else if (isNoTotem) {
            drawNoTotemInfo(255);
        } else if (isAutoEatEating) {
            float centerY = animY + (animH - 3) / 2f;
            drawAutoEatInfoAt(255, centerY);
        } else if (isSorting) {
            drawSortingInfo(255);
        } else if (isMusicPlaying) {
            float centerY = animY + (animH - 3) / 2f;
            drawMusicInfoAt(255, centerY);
        } else if (currentToggle != null) {
            drawToggleInfo(255, getToggleTimeProgress());
        }
        if (isUnifiedLayout() && !hasUnifiedPrompt()) {
            drawCenteredTitle(1f);
        }
    }

    private void renderCollapse1() {
        if (layout.get() == Layout.Modern) {
            Shader2DUtil.drawRoundedBlur(new MatrixStack(), animX, animY, animW, animH, getRadius(), new Color(0, 0, 0, 0), blurStrength.get().floatValue(), 1f - progress);
            NanoVGHelper.drawRoundRectBloom(animX, animY, animW, animH, getRadius(), withAlpha(new Color(20, 20, 20), (int)(backgroundAlpha.get() * (1f - progress))));
            NanoVGHelper.drawGradientRRect(animX + 5, animY, animW - 10, 1.5f, 1f, withAlpha(ClickGui.color(0), alphaFromProgress(1f - progress)), withAlpha(ClickGui.color2(0), alphaFromProgress(1f - progress)));
        } else {
            drawBackground(withAlpha(Size.INVENTORY_BG_COLOR, backgroundAlpha.get()));
        }

        if (isUnifiedLayout() && (hasUnifiedPrompt() || hasUnifiedPillTransient()) && !isTabPhase()) {
            if (renderUnifiedPillStack(alphaFromProgress(1f - progress), 1f)) return;
        }
        if (layout.get() != Layout.Modern && !isUnifiedLayout()) {
            drawSideInfo(1f, 1f);
        }
        int lines = getUnifiedPromptLineCount();
        if (isUnifiedLayout() && lines >= 2) {
            drawUnifiedMultiPrompts(alphaFromProgress(1f - progress), 1f);
            return;
        }
        if (currentKill != null) {
            drawKillInfo(alphaFromProgress(1f - progress), getKillTimeProgress());
        } else if (currentWin != null) {
            drawWinInfo(alphaFromProgress(1f - progress), getWinTimeProgress());
        } else if (currentToggle != null) {
            drawToggleInfo(alphaFromProgress(1f - progress), getToggleTimeProgress());
        } else if (closingSorting) {
            if (isUnifiedLayout()) {
                drawCenteredTitle(1f);
            } else {
                drawSortingInfo(alphaFromProgress(1f - progress));
            }
        }
        if (isUnifiedLayout() && !hasUnifiedPrompt()) {
            drawCenteredTitle(1f);
        }
    }

    private void renderCollapse2() {
        if (layout.get() == Layout.Modern) {
            Shader2DUtil.drawRoundedBlur(new MatrixStack(), animX, animY, animW, animH, getRadius(), new Color(0, 0, 0, 0), blurStrength.get().floatValue(), 0f);
        } else {
            drawBackground(withAlpha(Size.INVENTORY_BG_COLOR, backgroundAlpha.get()));
        }

        if (isUnifiedLayout() && (hasUnifiedPrompt() || hasUnifiedPillTransient()) && !isTabPhase()) {
            renderUnifiedPillStack(alphaFromProgress(1f - progress), 1f);
        }
        if (isUnifiedLayout()) {
            if (closingSorting) {
                drawCenteredTitle(1f);
            } else {
                float p = progress;
                if (p > 0.5f) {
                    float alpha = (p - 0.5f) / 0.5f;
                    drawCenteredTitle(alpha);
                }
            }
        } else {
            if (layout.get() != Layout.Modern) drawSideInfo(0f, 1f);
            drawCenteredTitle(progress);
        }
        if (isUnifiedLayout() && !hasUnifiedPrompt()) {
            drawCenteredTitle(1f);
        }
    }

    private void renderTabExpand() {
        drawBackground(withAlpha(Size.INVENTORY_BG_COLOR, backgroundAlpha.get()));
        float progress = getMergeProgress();
        float alpha = 1f - progress;
        
        if (!isUnifiedLayout()) {
            if (layout.get() != Layout.Modern) drawSideInfo(0f, alpha);
            drawCenteredTitle(alpha);
        }
    }

    private void renderTabDisplay() {
        drawBackground(withAlpha(Size.INVENTORY_BG_COLOR, backgroundAlpha.get()));
    }

    private void renderTabCollapse() {
        drawBackground(withAlpha(Size.INVENTORY_BG_COLOR, backgroundAlpha.get()));
        float progress = getMergeProgress();
        float alpha = 1f - progress;
        
        if (!isUnifiedLayout()) {
            if (alpha > 0.1f) {
                if (layout.get() != Layout.Modern) drawSideInfo(0f, alpha);
                drawCenteredTitle(alpha);
            }
        }
    }

    private boolean isUnifiedLayout() {
        return layout.is(Layout.Unified);
    }

    private void drawBackground(Color color) {
        if (enableBloom.get()) {
            NanoVGHelper.drawRoundRectBloom(animX, animY, animW, animH, getRadius(), color);
        } else {
            NanoVGHelper.drawRoundRect(animX, animY, animW, animH, getRadius(), color);
        }
    }

    private void drawPillBackground(float x, float y, float w, float h, int alpha) {
        int a = (int) (backgroundAlpha.get() * (alpha / 255f));
        Color color = withAlpha(Size.INVENTORY_BG_COLOR, a);
        if (layout.get() == Layout.Modern) {
            color = withAlpha(new Color(20, 20, 20), a);
        }
        if (enableBloom.get()) {
            NanoVGHelper.drawRoundRectBloom(x, y, w, h, getRadius(), color);
        } else {
            NanoVGHelper.drawRoundRect(x, y, w, h, getRadius(), color);
        }
    }

    private void drawPillBackgroundProgress(float x, float y, float w, float h, int alpha, float timeProgress, PromptKind kind) {
        if (!progressStyle.is(ProgressStyle.Background)) return;
        float remaining = Math.max(0f, Math.min(1f, 1f - timeProgress));
        if (remaining <= 0.001f) return;

        int a = (int) (alpha * remaining * 0.55f);
        if (a <= 1) return;

        if (kind == PromptKind.TOGGLE) {
            Color c1 = withAlpha(ClickGui.color(0), a);
            Color c2 = withAlpha(ClickGui.color(90), a);
            NanoVGHelper.drawGradientRRect2(x, y, w, h, getRadius(), c1, c2);
        } else if (kind == PromptKind.KILL) {
            Color red = withAlpha(new Color(255, 120, 120), a);
            NanoVGHelper.drawRoundRect(x, y, w, h, getRadius(), red);
        } else if (kind == PromptKind.WIN) {
            Color c1 = withAlpha(new Color(255, 215, 80), a);
            Color c2 = withAlpha(new Color(255, 180, 0), a);
            NanoVGHelper.drawGradientRRect2(x, y, w, h, getRadius(), c1, c2);
        }
    }

    private void drawCenteredTitle(float alpha) {
        if (alpha <= 0.05f) return;
        int font = FontLoader.bold((int) Size.LOGO_FONT_SIZE);
        String name = Sakura.MOD_NAME;
        float textW = NanoVGHelper.getTextWidth(name, font, Size.LOGO_FONT_SIZE);
        NanoVGHelper.drawGlowingString(name, animX + (animW - textW) / 2f, animY + animH / 2f + 4, font, Size.LOGO_FONT_SIZE, withAlpha(ClickGui.color(0), (int) (255 * alpha)), Size.GLOW_RADIUS);
    }

    private void drawUnifiedHeader(float alpha) {
        if (alpha <= 0.05f) return;

        int infoFont = FontLoader.medium(9);
        int titleFont = FontLoader.bold((int) Size.LOGO_FONT_SIZE);

        String time = LocalTime.now().format(TIME_FORMAT);
        String title = Sakura.MOD_NAME;
        String pingText = getPingText();

        float timeW = NanoVGHelper.getTextWidth(time, infoFont, Size.INFO_FONT_SIZE);
        float titleW = NanoVGHelper.getTextWidth(title, titleFont, Size.LOGO_FONT_SIZE);
        float pingW = NanoVGHelper.getTextWidth(pingText, infoFont, Size.INFO_FONT_SIZE);

        float spacing = 10f;
        float totalTextW = timeW + titleW + pingW + spacing * 2f;
        float padding = 6f;
        float totalW = totalTextW + padding * 2f;

        float startX = animX + (animW - totalW) / 2f + padding;
        float baseY = animY + 8f;

        Color infoColor = withAlpha(Color.WHITE, (int) (255 * alpha));
        Color titleColor = withAlpha(ClickGui.color(0), (int) (255 * alpha));

        float timeX = startX;
        float titleX = timeX + timeW + spacing;
        float pingX = titleX + titleW + spacing;

        float line1X = timeX + timeW + spacing * 0.5f;
        float line2X = titleX + titleW + spacing * 0.5f;
        float lineTop = animY + 4f;
        float lineBottom = animY + animH - 4f;
        int lineAlpha = (int) (160 * alpha);
        Color lineColor = new Color(255, 255, 255, Math.max(0, Math.min(255, lineAlpha)));

        NanoVGHelper.drawLine(line1X, lineTop, line1X, lineBottom, 0.6f, lineColor);
        NanoVGHelper.drawLine(line2X, lineTop, line2X, lineBottom, 0.6f, lineColor);

        NanoVGHelper.drawString(time, timeX, baseY + Size.INFO_FONT_SIZE * 0.5f, infoFont, Size.INFO_FONT_SIZE, infoColor);
        NanoVGHelper.drawString(title, titleX, baseY + Size.LOGO_FONT_SIZE * 0.5f, titleFont, Size.LOGO_FONT_SIZE - 1f, titleColor);
        NanoVGHelper.drawString(pingText, pingX, baseY + Size.INFO_FONT_SIZE * 0.5f, infoFont, Size.INFO_FONT_SIZE, infoColor);
    }

    private float calculateUnifiedHeaderWidth() {
        int infoFont = FontLoader.medium(9);
        int titleFont = FontLoader.bold((int) Size.LOGO_FONT_SIZE);

        String time = LocalTime.now().format(TIME_FORMAT);
        String title = Sakura.MOD_NAME;
        String pingText = getPingText();

        float timeW = NanoVGHelper.getTextWidth(time, infoFont, Size.INFO_FONT_SIZE);
        float titleW = NanoVGHelper.getTextWidth(title, titleFont, Size.LOGO_FONT_SIZE);
        float pingW = NanoVGHelper.getTextWidth(pingText, infoFont, Size.INFO_FONT_SIZE);

        float spacing = 10f;
        float padding = 6f;
        float totalTextW = timeW + titleW + pingW + spacing * 2f;
        return Math.max(Size.BASE_W, totalTextW + padding * 2f);
    }

    private float getUnifiedIdleWidth() {
        return Math.max(Size.EXPANDED_W, calculateUnifiedHeaderWidth());
    }

    private float getUnifiedIdleHeight() {
        return Size.EXPANDED_H;
    }

    private void drawUnifiedNoTotemAndSorting(int alpha) {
        if (alpha <= 5 || invManager == null) return;

        float headerZone = 18f;
        float contentTop = animY + headerZone;
        float contentBottom = animY + animH - 8f;
        float centerSpacing = 18f;

        float contentCenter = (contentTop + contentBottom) / 2f;
        float secondCenterY = contentCenter + centerSpacing / 2f;

        drawSortingInfoAt(alpha, secondCenterY);
    }

    private void drawUnifiedNoTotemAndToggle(int alpha, float timeProgress) {
        if (alpha <= 5) return;

        boolean noTotemFirst;
        if (noTotemTriggerTime == 0L && toggleTriggerTime == 0L) {
            noTotemFirst = true;
        } else if (noTotemTriggerTime == 0L) {
            noTotemFirst = false;
        } else if (toggleTriggerTime == 0L) {
            noTotemFirst = true;
        } else {
            noTotemFirst = noTotemTriggerTime <= toggleTriggerTime;
        }

        float headerZone = 18f;
        float contentTop = animY + headerZone;
        float contentBottom = animY + animH - 8f;
        float centerSpacing = 18f;

        float contentCenter = (contentTop + contentBottom) / 2f;
        float firstCenterY = contentCenter - centerSpacing / 2f;
        float secondCenterY = contentCenter + centerSpacing / 2f;

        if (noTotemFirst) {
            drawNoTotemInfoAt(alpha, firstCenterY);
            drawToggleInfoAt(alpha, secondCenterY, timeProgress, false);
        } else {
            drawToggleInfoAt(alpha, firstCenterY, timeProgress, false);
            drawNoTotemInfoAt(alpha, secondCenterY);
        }
    }

    private enum PromptKind {
        NOTOTEM,
        SORTING,
        EATING,
        AUTOMINE,
        MUSIC,
        TOGGLE,
        KILL,
        WIN
    }

    private static final float UNIFIED_PILL_H = 26f;
    private static final float UNIFIED_PILL_GAP = 4f;
    private static final long UNIFIED_PILL_ENTER_MS = 220L;
    private static final long UNIFIED_PILL_LEAVE_MS = 220L;
    private static final long UNIFIED_PILL_MOVE_MS = 240L;

    @FunctionalInterface
    private interface UnifiedPillRenderer {
        void render(float x, float y, float w, float h, int alpha, float timeProgress, boolean showProgress);
    }

    private record UnifiedPillDef(
            PromptKind kind,
            java.util.function.BooleanSupplier active,
            java.util.function.LongSupplier triggerTime,
            java.util.function.Supplier<Float> width,
            UnifiedPillRenderer renderer
    ) {
    }

    private static final class UnifiedPillState {
        final PromptKind kind;
        long triggerTime;
        boolean entering;
        long enterStart;
        float enterFromY;

        boolean leaving;
        boolean mergeIntoAbove;
        PromptKind mergeAboveKind;
        long leaveStart;
        float leaveFromY;
        float leaveToY;

        long moveStart;
        float moveFromY;
        float moveToY;

        float y;

        UnifiedPillState(PromptKind kind) {
            this.kind = kind;
            this.y = Float.NaN;
            this.leaveFromY = Float.NaN;
        }
    }

    private List<UnifiedPillDef> getUnifiedPillRegistry() {
        return List.of(
                new UnifiedPillDef(
                        PromptKind.NOTOTEM,
                        () -> isNoTotem,
                        () -> noTotemTriggerTime,
                        this::calculateNoTotemWidth,
                        (x, y, w, h, alpha, timeProgress, showProgress) -> drawPillNoTotem(x, y, w, h, alpha)
                ),
                new UnifiedPillDef(
                        PromptKind.SORTING,
                        () -> isSorting,
                        () -> sortingTriggerTime,
                        this::calculateSortingWidth,
                        (x, y, w, h, alpha, timeProgress, showProgress) -> drawPillSorting(x, y, w, h, alpha)
                ),
                new UnifiedPillDef(
                        PromptKind.EATING,
                        () -> isAutoEatEating,
                        () -> autoEatTriggerTime,
                        this::calculateAutoEatWidth,
                        (x, y, w, h, alpha, timeProgress, showProgress) -> drawPillAutoEat(x, y, w, h, alpha)
                ),
                new UnifiedPillDef(
                        PromptKind.AUTOMINE,
                        () -> isAutoMineWorking,
                        () -> autoMineTriggerTime,
                        this::calculateAutoMineWidth,
                        (x, y, w, h, alpha, timeProgress, showProgress) -> drawPillAutoMine(x, y, w, h, alpha)
                ),
                new UnifiedPillDef(
                        PromptKind.MUSIC,
                        () -> isMusicPlaying,
                        () -> musicTriggerTime,
                        this::calculateMusicWidth,
                        (x, y, w, h, alpha, timeProgress, showProgress) -> drawPillMusic(x, y, w, h, alpha)
                ),
                new UnifiedPillDef(
                        PromptKind.TOGGLE,
                        () -> currentToggle != null,
                        () -> toggleTriggerTime,
                        this::calculateExpandedWidth,
                        (x, y, w, h, alpha, timeProgress, showProgress) -> drawPillToggle(x, y, w, h, alpha, timeProgress, showProgress)
                ),
                new UnifiedPillDef(
                        PromptKind.KILL,
                        () -> currentKill != null,
                        () -> killTriggerTime,
                        this::calculateKillWidth,
                        (x, y, w, h, alpha, timeProgress, showProgress) -> drawPillKill(x, y, w, h, alpha, timeProgress, showProgress)
                ),
                new UnifiedPillDef(
                        PromptKind.WIN,
                        () -> currentWin != null,
                        () -> winTriggerTime,
                        this::calculateWinWidth,
                        (x, y, w, h, alpha, timeProgress, showProgress) -> drawPillWin(x, y, w, h, alpha, timeProgress, showProgress)
                )
        );
    }

    private List<PromptLine> getUnifiedDesiredLines() {
        List<PromptLine> lines = new ArrayList<>();
        if (isNoTotem) {
            lines.add(new PromptLine(PromptKind.NOTOTEM, noTotemTriggerTime));
        }
        if (isSorting) {
            lines.add(new PromptLine(PromptKind.SORTING, sortingTriggerTime));
        }
        if (isAutoEatEating) {
            lines.add(new PromptLine(PromptKind.EATING, autoEatTriggerTime));
        }
        if (isAutoMineWorking) {
            lines.add(new PromptLine(PromptKind.AUTOMINE, autoMineTriggerTime));
        }
        if (isMusicPlaying) {
            lines.add(new PromptLine(PromptKind.MUSIC, musicTriggerTime));
        }
        if (currentToggle != null) {
            lines.add(new PromptLine(PromptKind.TOGGLE, toggleTriggerTime));
        }
        if (currentKill != null) {
            lines.add(new PromptLine(PromptKind.KILL, killTriggerTime));
        }
        if (currentWin != null) {
            lines.add(new PromptLine(PromptKind.WIN, winTriggerTime));
        }

        lines.sort((a, b) -> comparePrompt(a.kind(), a.time(), b.kind(), b.time()));
        return lines;
    }

    private int comparePrompt(PromptKind aKind, long aTime, PromptKind bKind, long bTime) {
        if (aTime == bTime) {
            if (aKind == PromptKind.WIN && bKind != PromptKind.WIN) return -1;
            if (bKind == PromptKind.WIN && aKind != PromptKind.WIN) return 1;
            if (aKind == PromptKind.NOTOTEM && bKind != PromptKind.NOTOTEM) return -1;
            if (bKind == PromptKind.NOTOTEM && aKind != PromptKind.NOTOTEM) return 1;
            return 0;
        }
        return Long.compare(aTime, bTime);
    }

    private boolean hasUnifiedPillTransient() {
        return !unifiedPills.isEmpty();
    }

    private void updateUnifiedPills(long now) {
        List<PromptLine> desired = getUnifiedDesiredLines();

        for (PromptLine line : desired) {
            UnifiedPillState st = unifiedPills.get(line.kind());
            if (st == null) {
                st = new UnifiedPillState(line.kind());
                st.triggerTime = line.time();
                st.entering = true;
                st.enterStart = now;
                unifiedPills.put(line.kind(), st);
            } else {
                st.triggerTime = line.time();
            }
        }

        for (PromptKind kind : List.copyOf(unifiedPills.keySet())) {
            boolean stillDesired = desired.stream().anyMatch(l -> l.kind() == kind);
            UnifiedPillState st = unifiedPills.get(kind);
            if (st == null) continue;
            if (!stillDesired && !st.leaving) {
                st.leaving = true;
                st.leaveStart = now;
            }
        }

        float stackH = getUnifiedStackHeight(desired.size());
        float baseY = animY + (animH - stackH) / 2f;

        for (int i = 0; i < desired.size(); i++) {
            PromptKind kind = desired.get(i).kind();
            UnifiedPillState st = unifiedPills.get(kind);
            if (st == null) continue;
            float targetY = baseY + i * (UNIFIED_PILL_H + UNIFIED_PILL_GAP);

            if (st.entering) {
                float fromY = targetY;
                if (i > 0) {
                    float aboveTarget = baseY + (i - 1) * (UNIFIED_PILL_H + UNIFIED_PILL_GAP);
                    fromY = aboveTarget + UNIFIED_PILL_H;
                }
                st.enterFromY = fromY;
                st.moveFromY = fromY;
                st.moveToY = targetY;
                st.y = fromY;
            } else if (!st.leaving) {
                if (st.moveToY != targetY) {
                    st.moveStart = now;
                    st.moveFromY = st.y;
                    st.moveToY = targetY;
                }
            }
        }

        for (UnifiedPillState st : unifiedPills.values()) {
            if (!st.leaving) continue;
            st.mergeIntoAbove = false;
            st.mergeAboveKind = null;
            for (PromptLine line : desired) {
                if (line.kind() == st.kind) continue;
                if (comparePrompt(line.kind(), line.time(), st.kind, st.triggerTime) < 0) {
                    st.mergeIntoAbove = true;
                    st.mergeAboveKind = line.kind();
                }
            }
            if (Float.isNaN(st.leaveFromY)) {
                st.leaveFromY = Float.isNaN(st.y) ? baseY : st.y;
            }
        }

        for (UnifiedPillState st : unifiedPills.values()) {
            if (!st.leaving) continue;
            if (st.mergeIntoAbove && st.mergeAboveKind != null) {
                int aboveIndex = -1;
                for (int i = 0; i < desired.size(); i++) {
                    if (desired.get(i).kind() == st.mergeAboveKind) {
                        aboveIndex = i;
                        break;
                    }
                }
                if (aboveIndex >= 0) {
                    float aboveTargetY = baseY + aboveIndex * (UNIFIED_PILL_H + UNIFIED_PILL_GAP);
                    st.leaveToY = aboveTargetY + UNIFIED_PILL_H;
                } else {
                    st.leaveToY = st.leaveFromY - 4f;
                    st.mergeIntoAbove = false;
                    st.mergeAboveKind = null;
                }
            } else {
                st.leaveToY = st.leaveFromY - 4f;
            }
        }
    }

    private float getUnifiedStackHeight(int count) {
        if (count <= 0) return 0f;
        return count * UNIFIED_PILL_H + Math.max(0, count - 1) * UNIFIED_PILL_GAP;
    }

    private boolean renderUnifiedPillStack(int baseAlpha, float timeProgress) {
        long now = System.currentTimeMillis();
        updateUnifiedPills(now);

        List<PromptLine> desired = getUnifiedDesiredLines();
        int desiredCount = desired.size();
        if (desiredCount <= 0 && unifiedPills.values().stream().noneMatch(s -> s.leaving)) {
            unifiedPills.clear();
            return false;
        }

        float stackH = getUnifiedStackHeight(Math.max(1, desiredCount));
        float baseY = animY + (animH - stackH) / 2f;

        boolean drew = false;
        for (UnifiedPillState st : List.copyOf(unifiedPills.values())) {
            float alphaMul = 1f;
            float hMul = 1f;
            float y;

            if (st.entering) {
                float t = clamp((now - st.enterStart) / (float) UNIFIED_PILL_ENTER_MS, 0f, 1f);
                float p = easeOut(t);
                y = lerp(st.enterFromY, st.moveToY, p);
                alphaMul = p;
                hMul = p;
                if (t >= 1f) {
                    st.entering = false;
                    st.y = st.moveToY;
                } else {
                    st.y = y;
                }
            } else if (st.leaving) {
                float t = clamp((now - st.leaveStart) / (float) UNIFIED_PILL_LEAVE_MS, 0f, 1f);
                float p = easeOut(t);
                y = lerp(st.leaveFromY, st.leaveToY, p);
                alphaMul = 1f - p;
                if (st.mergeIntoAbove) {
                    hMul = 1f - p;
                }
                st.y = y;
                if (t >= 1f) {
                    unifiedPills.remove(st.kind);
                    continue;
                }
            } else {
                if (st.moveToY != 0f && st.moveFromY != st.moveToY) {
                    float t = st.moveStart <= 0L ? 1f : clamp((now - st.moveStart) / (float) UNIFIED_PILL_MOVE_MS, 0f, 1f);
                    float p = easeOut(t);
                    y = lerp(st.moveFromY, st.moveToY, p);
                    st.y = y;
                    if (t >= 1f) {
                        st.moveFromY = st.moveToY;
                    }
                } else {
                    st.y = Float.isNaN(st.y) ? baseY : st.y;
                }
            }

            float h = UNIFIED_PILL_H * hMul;
            if (h <= 1f) continue;
            int alpha = (int) (baseAlpha * alphaMul);
            if (alpha <= 2) continue;

            float x = animX;
            float w = animW;
            float pillY = st.y;

            drawPillBackground(x, pillY, w, h, alpha);

            UnifiedPillDef def = getUnifiedPillRegistry().stream().filter(d -> d.kind() == st.kind).findFirst().orElse(null);
            if (def != null) {
                boolean showProgress = st.kind == PromptKind.TOGGLE || st.kind == PromptKind.KILL || st.kind == PromptKind.WIN;
                float pillTimeProgress = timeProgress;
                if (st.kind == PromptKind.TOGGLE || st.kind == PromptKind.KILL) {
                    pillTimeProgress = clamp((now - st.triggerTime) / (float) WIN_TOTAL, 0f, 1f);
                } else if (st.kind == PromptKind.WIN) {
                    pillTimeProgress = clamp((now - st.triggerTime) / (float) WIN_TOTAL, 0f, 1f);
                }
                if (showProgress) {
                    drawPillBackgroundProgress(x, pillY, w, h, alpha, pillTimeProgress, st.kind);
                }
                def.renderer().render(x, pillY, w, h, alpha, pillTimeProgress, showProgress);
            }
            drew = true;
        }
        return drew;
    }

    private record PromptLine(PromptKind kind, long time) {
    }

    private void drawUnifiedMultiPrompts(int alpha, float timeProgress) {
        if (alpha <= 5) return;

        List<PromptLine> lines = new ArrayList<>();
        if (isNoTotem) {
            lines.add(new PromptLine(PromptKind.NOTOTEM, noTotemTriggerTime));
        }
        if (isSorting) {
            lines.add(new PromptLine(PromptKind.SORTING, sortingTriggerTime));
        }
        if (isAutoEatEating) {
            lines.add(new PromptLine(PromptKind.EATING, autoEatTriggerTime));
        }
        if (isAutoMineWorking) {
            lines.add(new PromptLine(PromptKind.AUTOMINE, autoMineTriggerTime));
        }
        if (isMusicPlaying) {
            lines.add(new PromptLine(PromptKind.MUSIC, musicTriggerTime));
        }
        if (currentToggle != null) {
            lines.add(new PromptLine(PromptKind.TOGGLE, toggleTriggerTime));
        }
        if (currentKill != null) {
            lines.add(new PromptLine(PromptKind.KILL, killTriggerTime));
        }
        if (currentWin != null) {
            lines.add(new PromptLine(PromptKind.WIN, winTriggerTime));
        }

        lines.sort((a, b) -> comparePrompt(a.kind(), a.time(), b.kind(), b.time()));

        float headerZone = 18f;
        float contentTop = animY + headerZone;
        float contentBottom = animY + animH - 8f;
        float centerSpacing = 18f;
        float contentCenter = (contentTop + contentBottom) / 2f;

        int n = lines.size();
        boolean hasSortingLine = lines.stream().anyMatch(l -> l.kind() == PromptKind.SORTING);
        boolean hasToggleLine = lines.stream().anyMatch(l -> l.kind() == PromptKind.TOGGLE);
        boolean splitSortingToggle = n == 2 && hasSortingLine && hasToggleLine;
        float upperHalfCenter = contentTop + (contentCenter - contentTop) * 0.28f;
        float lowerHalfCenter = contentCenter + (contentBottom - contentCenter) * 0.55f;
        for (int i = 0; i < n; i++) {
            PromptLine line = lines.get(i);
            float centerY;
            if (splitSortingToggle) {
                centerY = line.kind() == PromptKind.SORTING ? upperHalfCenter : lowerHalfCenter;
            } else {
                float offset = (i - (n - 1) / 2f) * centerSpacing;
                centerY = contentCenter + offset;
            }
            if (line.kind() == PromptKind.NOTOTEM) {
                drawNoTotemInfoAt(alpha, centerY);
            } else if (line.kind() == PromptKind.SORTING) {
                drawSortingInfoAt(alpha, centerY);
            } else if (line.kind() == PromptKind.EATING) {
                drawAutoEatInfoAt(alpha, centerY);
            } else if (line.kind() == PromptKind.AUTOMINE) {
                drawAutoMineInfoAt(alpha, centerY);
            } else if (line.kind() == PromptKind.MUSIC) {
                drawMusicInfoAt(alpha, centerY);
            } else if (line.kind() == PromptKind.TOGGLE) {
                drawToggleInfoAt(alpha, centerY, getToggleTimeProgress(), false);
            } else if (line.kind() == PromptKind.KILL) {
                drawKillInfoAt(alpha, centerY, getKillTimeProgress(), false);
            } else if (line.kind() == PromptKind.WIN) {
                drawWinInfoAt(alpha, centerY, getWinTimeProgress(), false);
            }
        }
    }

    private float getToggleTimeProgress() {
        if (toggleTriggerTime <= 0L) return 0f;
        long now = System.currentTimeMillis();
        long duration = isUnifiedLayout() ? WIN_TOTAL : Timing.TOTAL;
        return clamp((now - toggleTriggerTime) / (float) duration, 0f, 1f);
    }

    private float getKillTimeProgress() {
        if (killTriggerTime <= 0L) return 0f;
        long now = System.currentTimeMillis();
        long duration = isUnifiedLayout() ? WIN_TOTAL : Timing.TOTAL;
        return clamp((now - killTriggerTime) / (float) duration, 0f, 1f);
    }

    private float getWinTimeProgress() {
        if (winTriggerTime <= 0L) return 0f;
        long now = System.currentTimeMillis();
        return clamp((now - winTriggerTime) / (float) WIN_TOTAL, 0f, 1f);
    }

    private static int applyTimedTextFade(int alpha, float timeProgress) {
        float t = clamp(timeProgress, 0f, 1f);
        float fadeIn = 0.12f;
        float fadeOut = 0.12f;
        float inMul = t / fadeIn;
        float outMul = (1f - t) / fadeOut;
        float mul = Math.min(1f, Math.min(inMul, outMul));
        mul = clamp(mul, 0f, 1f);
        return (int) (alpha * mul);
    }

    private boolean hasUnifiedPrompt() {
        return chestOverlayDesired || isNoTotem || isSorting || isAutoEatEating || isAutoMineWorking || isMusicPlaying || currentKill != null || currentToggle != null || currentWin != null;
    }

    private int getUnifiedPromptLineCount() {
        if (!isUnifiedLayout()) return 0;
        int lines = 0;
        if (chestOverlayDesired) return 1;
        if (isNoTotem) lines++;
        if (isSorting) lines++;
        if (isAutoEatEating) lines++;
        if (isAutoMineWorking) lines++;
        if (isMusicPlaying) lines++;
        if (currentToggle != null) lines++;
        if (currentKill != null) lines++;
        if (currentWin != null) lines++;
        return lines;
    }

    public static boolean shouldSuppressChestScreenStatic() {
        dev.mzc.client.module.impl.hud.DynamicIslandHud hud = dev.mzc.client.Sakura.MODULES.getModule(dev.mzc.client.module.impl.hud.DynamicIslandHud.class);
        dev.mzc.client.module.impl.player.inventory.ChestStealer cs = dev.mzc.client.Sakura.MODULES.getModule(dev.mzc.client.module.impl.player.inventory.ChestStealer.class);
        return hud != null && hud.isEnabled() && hud.isChestOverlayActive && hud.chestStealerHud.get() && cs != null && cs.isEnabled();
    }

    private float getUnifiedAnimatedHeight(float baseTargetHeight) {
        int lines = getUnifiedPromptLineCount();
        long now = System.currentTimeMillis();

        if (unifiedPromptLines != lines) {
            unifiedPromptLines = lines;
            unifiedHeightAnimStart = now;
            unifiedHeightFrom = animH > 0f ? animH : baseTargetHeight;
            unifiedHeightTo = baseTargetHeight;
        }

        if (unifiedHeightAnimStart <= 0L) {
            return baseTargetHeight;
        }

        long dt = now - unifiedHeightAnimStart;
        long duration = 350L;
        if (dt >= duration) {
            unifiedHeightAnimStart = 0L;
            return baseTargetHeight;
        }

        float t = dt / (float) duration;
        float p = easeOut(t);
        return lerp(unifiedHeightFrom, unifiedHeightTo, p);
    }

    private float getUnifiedAnimatedWidth(float baseTargetWidth) {
        long now = System.currentTimeMillis();
        if (unifiedWidthTo != baseTargetWidth) {
            unifiedWidthAnimStart = now;
            unifiedWidthFrom = animW > 0f ? animW : baseTargetWidth;
            unifiedWidthTo = baseTargetWidth;
        }
        if (unifiedWidthAnimStart <= 0L) {
            return baseTargetWidth;
        }
        long dt = now - unifiedWidthAnimStart;
        long duration = 350L;
        if (dt >= duration) {
            unifiedWidthAnimStart = 0L;
            return baseTargetWidth;
        }
        float t = dt / (float) duration;
        float p = easeOut(t);
        return lerp(unifiedWidthFrom, unifiedWidthTo, p);
    }

    private void drawSideInfo(float expandProgress, float alpha) {
        if (alpha <= 0.05f) return;

        int font = FontLoader.medium(9);
        Color color = withAlpha(Color.WHITE, (int) (255 * alpha));
        float centerY = animY + animH / 2f + 3;
        Color bgColor = withAlpha(Size.INVENTORY_BG_COLOR, (int) (backgroundAlpha.get() * 0.44f * alpha));

        // Time
        String time = LocalTime.now().format(TIME_FORMAT);
        float timeW = NanoVGHelper.getTextWidth(time, font, Size.INFO_FONT_SIZE);
        float timeBgX = animX - Size.ELEMENT_SPACING - Size.ELEMENT_WIDTH;

        if (phase == Phase.TAB_EXPAND) {
            timeBgX = lerp(timeBgX, animX, tabMergeProgress);
        } else if (phase == Phase.TAB_COLLAPSE) {
            timeBgX = lerp(timeBgX, animX, tabMergeProgress);
        }

        if (enableBloom.get()) {
            NanoVGHelper.drawRoundRectBloom(timeBgX, animY, Size.ELEMENT_WIDTH, animH, getRadius(), bgColor);
        } else {
            NanoVGHelper.drawRoundRect(timeBgX, animY, Size.ELEMENT_WIDTH, animH, getRadius(), bgColor);
        }
        NanoVGHelper.drawString(time, timeBgX + (Size.ELEMENT_WIDTH - timeW) / 2, centerY, font, Size.INFO_FONT_SIZE, color);

        // FPS
        String username = "FPS:" + mc.getCurrentFps();
        float nameW = NanoVGHelper.getTextWidth(username, font, Size.INFO_FONT_SIZE);
        float nameBgX = animX + animW + Size.ELEMENT_SPACING;

        if (phase == Phase.TAB_EXPAND) {
            nameBgX = lerp(nameBgX, animX + animW - Size.ELEMENT_WIDTH, tabMergeProgress);
        } else if (phase == Phase.TAB_COLLAPSE) {
            nameBgX = lerp(nameBgX, animX + animW - Size.ELEMENT_WIDTH, tabMergeProgress);
        }

        if (enableBloom.get()) {
            NanoVGHelper.drawRoundRectBloom(nameBgX, animY, Size.ELEMENT_WIDTH, animH, getRadius(), bgColor);
        } else {
            NanoVGHelper.drawRoundRect(nameBgX, animY, Size.ELEMENT_WIDTH, animH, getRadius(), bgColor);
        }
        NanoVGHelper.drawString(username, nameBgX + (Size.ELEMENT_WIDTH - nameW) / 2, centerY, font, Size.INFO_FONT_SIZE, color);
    }

    private String getPingText() {
        if (mc.getNetworkHandler() == null || mc.player == null) {
            return "Ping";
        }
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        if (entry == null) {
            return "Ping";
        }
        return entry.getLatency() + "ms";
    }

    private float getUnifiedTargetHeight() {
        if (chestOverlayDesired) {
            return calculateChestHeight();
        }
        int blocks = getUnifiedPromptLineCount();
        if (blocks <= 0) {
            return Size.BASE_H;
        }
        float total = getUnifiedStackHeight(blocks);
        return Math.max(Size.BASE_H, total);
    }

    private void renderCapturedTab(DrawContext context) {
        if (playerList == null) return;

        float opacity = 1f;
        if (phase == Phase.TAB_EXPAND) {
            opacity = getMergeProgress();
        } else if (phase == Phase.TAB_COLLAPSE) {
            float p = getMergeProgress();
            opacity = Math.max(0f, (p - 0.5f) * 2f);
        }
        
        if (opacity < 0.05f) return;

        int innerX1 = (int) (animX + Size.TAB_PADDING);
        int innerY1 = (int) (animY + Size.TAB_PADDING);
        int innerX2 = (int) (animX + animW - Size.TAB_PADDING);
        int innerY2 = (int) (animY + animH - Size.TAB_PADDING);
        
        if (innerX2 <= innerX1 || innerY2 <= innerY1) return;

        context.enableScissor(innerX1, innerY1, innerX2, innerY2);

        int innerW = innerX2 - innerX1;
        int fontH = mc.textRenderer.fontHeight;

        int textAlpha = (int) (255 * opacity);
        int textColor = (textAlpha << 24) | 0xFFFFFF;
        int pingColor = (textAlpha << 24) | 0xA0A0A0;

        int y = (int) (animY + Size.TAB_HEADER_Y);

        Text headerText = capturedTabHeader;
        if (headerText == null || headerText.getString().isEmpty()) {
            headerText = Text.literal("Players: " + playerList.size());
        }
        List<OrderedText> headerLines = mc.textRenderer.wrapLines(headerText, innerW);
        for (OrderedText line : headerLines) {
            int lineW = mc.textRenderer.getWidth(line);
            int x = (int) (animX + (animW - lineW) / 2f);
            context.drawTextWithShadow(mc.textRenderer, line, x, y, textColor);
            y += fontH;
        }
        y += 8;

        int listY = Math.max((int) (animY + Size.TAB_LIST_Y), y);
        int rowH = (int) Size.TAB_PLAYER_HEIGHT;
        int headSize = 10;
        int headYOffset = Math.max(0, (rowH - headSize) / 2);

        int i = 0;
        float spacing = 5;
        for (PlayerListEntry entry : playerList) {
            int col = i / tabRows;
            int row = i % tabRows;
            if (col >= tabColumns) break;

            int colX = (int) (innerX1 + col * (tabColumnWidth + spacing));
            int rowY = listY + row * rowH;
            if (rowY + rowH > innerY2) break;

            int headX = colX;
            int headY = rowY + headYOffset;

            GlStateManager._enableBlend();
            context.drawTexture(RenderPipelines.GUI_TEXTURED,
                    entry.getSkinTextures().body().texturePath(),
                    headX, headY,
                    8, 8,
                    headSize, headSize,
                    8, 8,
                    64, 64);
            context.drawTexture(RenderPipelines.GUI_TEXTURED,
                    entry.getSkinTextures().body().texturePath(),
                    headX, headY,
                    40, 8,
                    headSize, headSize,
                    8, 8,
                    64, 64);
            GlStateManager._disableBlend();

            String ping = entry.getLatency() + "ms";
            int pingW = mc.textRenderer.getWidth(ping);
            int pingX = colX + tabColumnWidth - pingW;
            int textY = rowY + Math.max(0, (rowH - fontH) / 2);
            context.drawTextWithShadow(mc.textRenderer, ping, pingX, textY, pingColor);

            Text nameText = mc.inGameHud.getPlayerListHud().getPlayerName(entry);
            int nameX = headX + headSize + 4;
            int nameClipX2 = pingX - 6;
            int maxNameW = Math.max(0, nameClipX2 - nameX);
            
            if (maxNameW > 0) {
                net.minecraft.text.StringVisitable trimmed = mc.textRenderer.trimToWidth(nameText, maxNameW);
                OrderedText ordered = net.minecraft.util.Language.getInstance().reorder(trimmed);
                context.drawTextWithShadow(mc.textRenderer, ordered, nameX, textY, textColor);
            }

            i++;
        }

        Text footerText = capturedTabFooter;
        if (footerText != null && !footerText.getString().isEmpty()) {
            List<OrderedText> footerLines = mc.textRenderer.wrapLines(footerText, innerW);
            int footerY = innerY2 - footerLines.size() * fontH;
            
            if (footerY >= innerY1) {
                for (OrderedText line : footerLines) {
                    int lineW = mc.textRenderer.getWidth(line);
                    int x = (int) (animX + (animW - lineW) / 2f);
                    context.drawTextWithShadow(mc.textRenderer, line, x, footerY, textColor);
                    footerY += fontH;
                }
            }
        }

        context.disableScissor();
    }

    private void renderIntegratedTargetHud(DrawContext context) {
        if (currentIntegratedTarget == null || targetHud == null) return;
        float alpha = targetHudAlpha;
        float spacing = 6f;
        float thW = 150f;
        float thH = 45f;
        float thX = (mc.getWindow().getScaledWidth() - thW) / 2f;
        float thY = animY + animH + spacing;
        
        // Use NanoVG to draw background and shadow
        NanoVGRenderer.INSTANCE.draw(vg -> {
             // Shadow
             if (enableBloom.get()) {
                 NanoVGHelper.drawRoundRectBloom(thX, thY, thW, thH, 10f, withAlpha(new Color(0, 0, 0), (int)(120 * alpha)));
             }
             // BG
             NanoVGHelper.drawRoundRect(thX, thY, thW, thH, 10f, withAlpha(new Color(20, 20, 20), (int)(backgroundAlpha.get() * alpha)));
             // Accent line
             NanoVGHelper.drawGradientRRect(thX + 5, thY, thW - 10, 1.5f, 1f, withAlpha(ClickGui.color(0), (int)(255 * alpha)), withAlpha(ClickGui.color2(0), (int)(255 * alpha)));
             
             // Avatar
             float pad = 6f;
             float avatarSize = thH - pad * 2;
             if (currentIntegratedTarget instanceof PlayerEntity player) {
                 drawPlayerAvatarIntegrated(vg, player, thX + pad, thY + pad, avatarSize, 8f, alpha);
             } else {
                 NanoVGHelper.drawRoundRect(thX + pad, thY + pad, avatarSize, avatarSize, 8f, withAlpha(new Color(60, 60, 60), (int)(200 * alpha)));
             }
             
             // Name
             int font = FontLoader.bold(14);
             String name = currentIntegratedTarget.getName().getString();
             if (name.length() > 14) name = name.substring(0, 14) + "..";
             NanoVGHelper.drawString(name, thX + pad + avatarSize + 8f, thY + 14f, font, 14f, withAlpha(Color.WHITE, (int)(255 * alpha)));
             
             // Health
             float hp = HealthUtil.getEntityHealth(currentIntegratedTarget);
             float maxHp = HealthUtil.getEntityMaxHealth(currentIntegratedTarget);
             float healthPct = clamp(hp / maxHp, 0f, 1f);
             
             float barX = thX + pad + avatarSize + 8f;
             float barY = thY + thH - 16f;
             float barW = thW - (avatarSize + pad * 2 + 8f) - pad;
             float barH = 6f;
             
             NanoVGHelper.drawRoundRect(barX, barY, barW, barH, 3f, withAlpha(new Color(255, 255, 255), (int)(30 * alpha)));
             
             if (healthPct > 0) {
                 Color c1 = withAlpha(ClickGui.color(0), (int)(255 * alpha));
                 Color c2 = withAlpha(ClickGui.color2(0), (int)(255 * alpha));
                 NanoVGHelper.drawGradientRRect2(barX, barY, barW * healthPct, barH, 3f, c1, c2);
             }
             
             String hpText = String.format("%.1f HP", hp);
             NanoVGHelper.drawString(hpText, barX, barY - 4f, FontLoader.medium(10), 10f, withAlpha(new Color(200, 200, 200), (int)(255 * alpha)));
        });
    }

    private void drawPlayerAvatarIntegrated(long vg, PlayerEntity player, float x, float y, float size, float radius, float alpha) {
        if (!(player instanceof net.minecraft.client.network.AbstractClientPlayerEntity clientPlayer)) return;
        int imageId = -1;
        // Simplified lookup for brevity, ideally uses a cache
        try {
             java.lang.reflect.Method m = TargetHud.class.getDeclaredMethod("getSkinImageId", net.minecraft.util.Identifier.class);
             m.setAccessible(true);
             imageId = (int) m.invoke(targetHud, clientPlayer.getSkin().body().texturePath());
        } catch (Exception e) {}
        
        if (imageId == -1) {
            NanoVGHelper.drawRoundRect(x, y, size, size, radius, withAlpha(new Color(80, 80, 80), (int)(200 * alpha)));
            return;
        }

        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            org.lwjgl.nanovg.NVGPaint paint = org.lwjgl.nanovg.NVGPaint.malloc(stack);
            float faceScale = 8.0f;
            float faceOx = x - size;
            float faceOy = y - size;
            float faceEx = size * faceScale;
            float faceEy = size * faceScale;

            NanoVG.nvgImagePattern(vg, faceOx, faceOy, faceEx, faceEy, 0.0f, imageId, alpha, paint);
            NanoVG.nvgBeginPath(vg);
            NanoVG.nvgRoundedRect(vg, x, y, size, size, radius);
            NanoVG.nvgFillPaint(vg, paint);
            NanoVG.nvgFill(vg);
        }
    }

    private void drawToggleInfo(int alpha, float timeProgress) {
        float centerY = animY + (animH - 3) / 2f;
        drawToggleInfoAt(alpha, centerY, timeProgress, true);
    }

    private void drawToggleInfoAt(int alpha, float centerY, float timeProgress, boolean showProgress) {
        if (currentToggle == null) return;
        float padding = 6, iconSize = 16;
        int iconFont = FontLoader.icons(iconSize);
        String icon = currentToggle.enabled ? "U" : "T";
        Color iconColor = ClickGui.color(0);
        float iconW = NanoVGHelper.getTextWidth(icon, iconFont, iconSize);
        int textAlpha = applyTimedTextFade(alpha, timeProgress);
        NanoVGHelper.drawString(icon, animX + padding + 6, centerY + iconSize * 0.35f, iconFont, iconSize, withAlpha(iconColor, textAlpha));
        int textFont = FontLoader.medium((int) Size.LOGO_FONT_SIZE);
        String status = currentToggle.name + (currentToggle.enabled ? (isChinese() ? " 已开启" : " enabled") : (isChinese() ? " 已关闭" : " disabled"));
        NanoVGHelper.drawString(status, animX + padding + iconW + 14, centerY + Size.LOGO_FONT_SIZE * 0.35f, textFont, Size.LOGO_FONT_SIZE - 2f, withAlpha(Color.WHITE, textAlpha));
        if (showProgress) {
            drawProgressBar(alpha, timeProgress);
        }
    }

    private void drawProgressBar(int alpha, float timeProgress) {
        drawProgressBar(alpha, timeProgress, ClickGui.color(0));
    }

    private void drawProgressBar(int alpha, float timeProgress, Color barColor) {
        float padding = 8, barH = 1.5f;
        float barY = animY + animH - barH - 3;
        float maxW = Math.max(0, animW - padding * 2);
        float progress = Math.max(0f, Math.min(1f, 1f - timeProgress));
        float currentW = maxW * progress;

        NanoVGHelper.drawRoundRect(animX + padding, barY, maxW, barH, barH / 2, withAlpha(barColor, (int) (50 * (alpha / 255f))));
        if (currentW > 0) {
            NanoVGHelper.drawRoundRect(animX + padding, barY, currentW, barH, barH / 2, withAlpha(barColor, (int) (220 * (alpha / 255f))));
        }
    }

    private void drawWinInfo(int alpha, float timeProgress) {
        float centerY = animY + animH / 2f - 2f;
        drawWinInfoAt(alpha, centerY, timeProgress, true);
    }

    private void drawWinInfoAt(int alpha, float centerY, float timeProgress, boolean showProgress) {
        if (currentWin == null) return;
        float iconSize = 16;
        int iconFont = FontLoader.icons(iconSize);
        int textFont = FontLoader.medium((int) Size.LOGO_FONT_SIZE);
        String icon = "G";
        Color iconColor = new Color(255, 210, 0);
        float iconW = NanoVGHelper.getTextWidth(icon, iconFont, iconSize);
        int textAlpha = applyTimedTextFade(alpha, timeProgress);
        String status = "Win!";
        float textW = NanoVGHelper.getTextWidth(status, textFont, Size.LOGO_FONT_SIZE - 2f);
        float spacing = 3f;
        float totalW = iconW + spacing + textW;
        float startX = animX + (animW - totalW) / 2f;
        float baselineY = getCenteredBaselineY(iconFont, iconSize, icon, textFont, Size.LOGO_FONT_SIZE - 2f, status, centerY);
        NanoVGHelper.drawString(icon, startX, baselineY, iconFont, iconSize, NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_BASELINE, withAlpha(iconColor, textAlpha));
        NanoVGHelper.drawString(status, startX + iconW + spacing, baselineY - 1f, textFont, Size.LOGO_FONT_SIZE - 2f, NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_BASELINE, withAlpha(Color.WHITE, textAlpha));

        if (showProgress) {
            drawProgressBar(alpha, timeProgress, new Color(255, 200, 0));
        }
    }

    private float getCenteredBaselineY(int iconFont, float iconSize, String icon, int textFont, float textSize, String text, float centerY) {
        long vg = NanoVGRenderer.INSTANCE.getContext();
        float iconCenter = getTextCenterOffsetY(vg, iconFont, iconSize, icon);
        float textCenter = getTextCenterOffsetY(vg, textFont, textSize, text);
        float avgCenter = (iconCenter + textCenter) * 0.5f;
        return centerY - avgCenter;
    }

    private float getTextCenterOffsetY(long vg, int font, float size, String text) {
        NanoVG.nvgFontFaceId(vg, font);
        NanoVG.nvgFontSize(vg, size);
        NanoVG.nvgTextAlign(vg, NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_BASELINE);
        float[] bounds = new float[4];
        NanoVG.nvgTextBounds(vg, 0f, 0f, text, bounds);
        return (bounds[1] + bounds[3]) * 0.5f;
    }

    private void drawKillInfo(int alpha, float timeProgress) {
        float centerY = animY + (animH - 3) / 2f;
        drawKillInfoAt(alpha, centerY, timeProgress, true);
    }

    private void drawKillInfoAt(int alpha, float centerY, float timeProgress, boolean showProgress) {
        if (currentKill == null) return;
        float iconSize = 16;
        int iconFont = FontLoader.icons(iconSize);
        String icon = "D";
        Color iconColor = new Color(255, 50, 50);
        float iconW = NanoVGHelper.getTextWidth(icon, iconFont, iconSize);
        int textAlpha = applyTimedTextFade(alpha, timeProgress);
        int textFont = FontLoader.medium((int) Size.LOGO_FONT_SIZE);
        String status = (isChinese() ? "击杀 " : "Killed ") + currentKill.name;
        float textW = NanoVGHelper.getTextWidth(status, textFont, Size.LOGO_FONT_SIZE - 2f);
        float spacing = 8f;
        float totalW = iconW + spacing + textW;
        float startX = animX + (animW - totalW) / 2f;
        NanoVGHelper.drawString(icon, startX, centerY + iconSize * 0.35f, iconFont, iconSize, withAlpha(iconColor, textAlpha));
        NanoVGHelper.drawString(status, startX + iconW + spacing, centerY + Size.LOGO_FONT_SIZE * 0.35f, textFont, Size.LOGO_FONT_SIZE - 2f, withAlpha(Color.WHITE, textAlpha));

        if (showProgress) {
            drawProgressBar(alpha, timeProgress);
        }
    }

    private float calculateExpandedWidth() {
        if (currentToggle == null) return Size.EXPANDED_W;

        float padding = 6, iconSize = 16, textSize = Size.LOGO_FONT_SIZE;
        int iconFont = FontLoader.icons(iconSize);
        int textFont = FontLoader.medium(textSize);

        String icon = currentToggle.enabled ? "U" : "T";
        String status = currentToggle.name + (currentToggle.enabled ? (isChinese() ? " 已开启" : " enabled") : (isChinese() ? " 已关闭" : " disabled"));

        float iconW = NanoVGHelper.getTextWidth(icon, iconFont, iconSize);
        float textW = NanoVGHelper.getTextWidth(status, textFont, textSize);
        float needed = padding * 2 + iconW + 4 + textW;

        return Math.max(Size.EXPANDED_W, Math.max(needed, 41));
    }

    private float calculateKillWidth() {
        if (currentKill == null) return Size.EXPANDED_W;

        float padding = 6, iconSize = 16, textSize = Size.LOGO_FONT_SIZE;
        int iconFont = FontLoader.icons(iconSize);
        int textFont = FontLoader.medium(textSize);

        String icon = "D";
        String status = (isChinese() ? "击杀 " : "Killed ") + currentKill.name;

        float iconW = NanoVGHelper.getTextWidth(icon, iconFont, iconSize);
        float textW = NanoVGHelper.getTextWidth(status, textFont, textSize);
        float needed = padding * 2 + iconW + 4 + textW;

        return Math.max(Size.EXPANDED_W, Math.max(needed, 41));
    }

    private float calculateWinWidth() {
        if (currentWin == null) return Size.EXPANDED_W;

        float padding = 6, iconSize = 16, textSize = Size.LOGO_FONT_SIZE;
        int iconFont = FontLoader.icons(iconSize);
        int textFont = FontLoader.medium(textSize);

        String icon = "G";
        String status = "Win!";

        float iconW = NanoVGHelper.getTextWidth(icon, iconFont, iconSize);
        float textW = NanoVGHelper.getTextWidth(status, textFont, textSize);
        float needed = padding * 2 + iconW + 4 + textW;

        return Math.max(Size.EXPANDED_W, Math.max(needed, 41));
    }

    private long ela() {
        return toggleStartTime == -1L ? 0 : System.currentTimeMillis() - toggleStartTime;
    }

    private long elaTab() {
        return tabStartTime == -1L ? 0 : System.currentTimeMillis() - tabStartTime;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float easeOut(float t) {
        return (float) Easing.CUBIC_OUT.ease(t);
    }

    private static int alphaFromProgress(float p) {
        return (int) (255 * p);
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    private boolean shouldShowNoTotem() {
        AutoTotem autoTotem = (AutoTotem) Sakura.MODULES.getModule(AutoTotem.class);
        if (autoTotem == null || !autoTotem.isEnabled()) return false;
        if (mc.player == null || mc.player.getInventory() == null) return false;

        float currentHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (currentHealth > autoTotem.healthThreshold.get()) {
            return false;
        }

        boolean hasInOffhand = mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        
        boolean hasInInv = false;
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                hasInInv = true;
                break;
            }
        }

        return !hasInOffhand && hasInInv;
    }

    private float calculateNoTotemWidth() {
        int font = FontLoader.bold(12);
        String text = isChinese() ? "无不死图腾" : "NoTotem";
        float textW = NanoVGHelper.getTextWidth(text, font, 12);
        return Math.max(Size.EXPANDED_W, textW + 30);
    }

    private void drawNoTotemInfo(int alpha) {
        if (alpha <= 5) return;
        float centerY = animY + animH / 2f + 3;
        drawNoTotemInfoAt(alpha, centerY);
    }

    private void drawNoTotemInfoAt(int alpha, float centerY) {
        int font = FontLoader.bold(12);
        String text = isChinese() ? "无不死图腾" : "NoTotem";
        float textW = NanoVGHelper.getTextWidth(text, font, 12);

        float centerX = animX + animW / 2f;
        NanoVGHelper.drawString(text, centerX - textW / 2f, centerY, font, 12, withAlpha(new Color(255, 80, 80), alpha));
    }

    private boolean isChinese() {
        return false;
    }

    private record ToggleInfo(String name, boolean enabled) {
    }

    private record KillInfo(String name) {
    }

    private String extractVictimName(String msg) {
        int idx = msg.indexOf(" was killed by");
        if (idx < 0) idx = msg.indexOf(" was knocked into the void by");
        if (idx <= 0) return "Unknown";
        String before = msg.substring(0, idx).trim();
        int space = before.lastIndexOf(' ');
        return space >= 0 ? before.substring(space + 1) : before;
    }

    private record WinInfo() {
    }
}
