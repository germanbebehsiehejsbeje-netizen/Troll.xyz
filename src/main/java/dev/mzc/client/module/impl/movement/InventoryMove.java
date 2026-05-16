package dev.mzc.client.module.impl.movement;

import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.client.TimerEvent;
import dev.mzc.client.events.input.MoveInputEvent;
import dev.mzc.client.events.packet.PacketEvent;
import dev.mzc.client.events.player.SprintEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ButtonClickC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.CraftRequestC2SPacket;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.network.packet.c2s.play.SlotChangedStateC2SPacket;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of LiquidBounce's InventoryMove module — replaces the old GUIMove.
 *
 * Allows the player to walk while an inventory / handled screen is open.
 *
 * Behaviors:
 *   Normal       — full movement in any container.
 *   Safe         — full movement only inside the player's own inventory; clicking is suppressed while moving.
 *   Undetectable — movement is allowed only while no container screen is open (effectively disables itself in inventory).
 *   StopOnAction — movement is allowed, but if the module clicks/sends a container packet, that packet is delayed
 *                  one tick and inputs are zeroed for that tick (mirrors LB).
 */
public class InventoryMove extends Module {
    public static InventoryMove INSTANCE;

    public enum Behavior {
        Normal,
        Safe,
        Undetectable,
        StopOnAction
    }

    public enum SprintMode {
        DoNotChange,
        ForceSprint,
        ForceNoSprint
    }

    public enum SneakMode {
        DoNotChange,
        ForceSneak,
        ForceNoSneak
    }

    private final EnumValue<Behavior> behavior = new EnumValue<>("Behavior", Behavior.Normal);
    private final BoolValue passthroughSneak = new BoolValue("PassthroughSneak", false);

    // Sprint sub-feature
    private final BoolValue sprintControl = new BoolValue("SprintControl", false);
    private final EnumValue<SprintMode> sprintClient = new EnumValue<>("SprintClient", SprintMode.DoNotChange, sprintControl::get);
    private final EnumValue<SprintMode> sprintServer = new EnumValue<>("SprintServer", SprintMode.DoNotChange, sprintControl::get);

    // Sneak sub-feature
    private final BoolValue sneakControl = new BoolValue("SneakControl", false);
    private final EnumValue<SneakMode> sneakClient = new EnumValue<>("SneakClient", SneakMode.DoNotChange, sneakControl::get);

    // Timer sub-feature
    private final BoolValue timerEnabled = new BoolValue("Timer", false);
    private final NumberValue<Float> timerSpeed = new NumberValue<>("TimerSpeed", 1.0f, 0.1f, 2.0f, 0.05f, timerEnabled::get);

    // Packets that we treat as "container actions" for StopOnAction.
    private final List<Packet<?>> delayedContainerPackets = new ArrayList<>();
    private boolean flushingDelayedPackets = false;

    public InventoryMove() {
        super("InventoryMove", Category.Movement);
        this.setType(ModuleType.Safe);
        INSTANCE = this;
    }

    @Override
    public String getSuffix() {
        return behavior.get().name();
    }

    @Override
    public void onDisable() {
        delayedContainerPackets.clear();
    }

    /**
     * Called from MixinKeyboardInput / mixin hooks to decide whether this module wants to forward a key press
     * even though a screen is open.
     */
    public boolean shouldHandleInputs(KeyBinding key) {
        if (!isEnabled()) return false;
        if (mc.player == null) return false;

        Screen screen = mc.currentScreen;
        if (screen == null) return true;

        // never override chat / creative search
        if (screen instanceof ChatScreen) return false;
        if (isInCreativeSearchField(screen)) return false;

        // sneak passthrough toggle
        if (key == mc.options.sneakKey && !passthroughSneak.get()) {
            return false;
        }

        Behavior b = behavior.get();
        // not a container — only chat/etc. blocks above; allow.
        if (!(screen instanceof HandledScreen<?>)) {
            return b == Behavior.Normal || b == Behavior.Safe || b == Behavior.StopOnAction;
        }

        // inside a handled (container) screen
        return switch (b) {
            case Normal, StopOnAction -> true;
            case Safe -> screen instanceof InventoryScreen; // only own inventory
            case Undetectable -> false;
        };
    }

    /**
     * If true, slot clicks performed manually by the user inside the inventory screen should be cancelled
     * to avoid revealing movement to the server.
     */
    public boolean doNotAllowClicking() {
        if (!isEnabled()) return false;
        if (behavior.get() != Behavior.Safe) return false;
        if (mc.player == null) return false;
        return isAnyMovementKeyPressed();
    }

    private boolean isAnyMovementKeyPressed() {
        long w = mc.getWindow().getHandle();
        if (GLFW.glfwGetKey(w, mc.options.forwardKey.getDefaultKey().getCode()) == GLFW.GLFW_PRESS) return true;
        if (GLFW.glfwGetKey(w, mc.options.backKey.getDefaultKey().getCode()) == GLFW.GLFW_PRESS) return true;
        if (GLFW.glfwGetKey(w, mc.options.leftKey.getDefaultKey().getCode()) == GLFW.GLFW_PRESS) return true;
        if (GLFW.glfwGetKey(w, mc.options.rightKey.getDefaultKey().getCode()) == GLFW.GLFW_PRESS) return true;
        if (GLFW.glfwGetKey(w, mc.options.jumpKey.getDefaultKey().getCode()) == GLFW.GLFW_PRESS) return true;
        return false;
    }

    private static boolean isInCreativeSearchField(Screen screen) {
        if (!(screen instanceof CreativeInventoryScreen creative)) return false;
        try {
            return CreativeInventoryScreen.selectedTab.getType() == net.minecraft.item.ItemGroup.Type.SEARCH;
        } catch (Throwable t) {
            return false;
        }
    }

    /* ----------------------------- behaviour: StopOnAction ----------------------------- */

    @EventHandler
    public void onPacket(PacketEvent event) {
        if (!isEnabled()) return;
        if (flushingDelayedPackets) return;
        if (event.isCancelled()) return; // respect prior handlers (e.g. XCarry)
        if (behavior.get() != Behavior.StopOnAction) return;
        if (event.getType() != dev.mzc.client.events.EventType.SEND) return;

        Packet<?> packet = event.getPacket();
        if (!isC2SContainerPacket(packet)) return;

        // Snapshot any movement; if any key is held, delay this packet by one tick and zero inputs.
        if (isAnyMovementKeyPressed()) {
            event.cancel();
            delayedContainerPackets.add(packet);
        }
    }

    @EventHandler
    public void onMoveInput(MoveInputEvent event) {
        if (!isEnabled()) return;

        // Sneak override
        if (sneakControl.get()) {
            switch (sneakClient.get()) {
                case ForceSneak -> event.setSneak(true);
                case ForceNoSneak -> event.setSneak(false);
                default -> {}
            }
        }

        // Sprint client override (network-side handled in SprintEvent below)
        if (sprintControl.get() && isHandledScreenOpen()) {
            switch (sprintClient.get()) {
                case ForceSprint -> {
                    if (isMoving(event)) event.setSprint(true);
                }
                case ForceNoSprint -> event.setSprint(false);
                default -> {}
            }
        }

        // StopOnAction: if we have queued container packets, force input to zero this tick and flush.
        if (behavior.get() == Behavior.StopOnAction && !delayedContainerPackets.isEmpty()) {
            event.setForward(0f);
            event.setStrafe(0f);
            event.setJump(false);
            event.setSneak(false);
            flushDelayedPackets();
        }
    }

    @EventHandler
    public void onSprint(SprintEvent event) {
        if (!isEnabled()) return;
        if (!sprintControl.get()) return;
        if (!isHandledScreenOpen()) return;

        switch (sprintServer.get()) {
            case ForceSprint -> event.setSprint(true);
            case ForceNoSprint -> event.setSprint(false);
            default -> {}
        }
    }

    @EventHandler
    public void onTimer(TimerEvent event) {
        if (!isEnabled()) return;
        if (!timerEnabled.get()) return;
        if (event.isCancelled() || event.isModified()) return;
        if (!(mc.currentScreen instanceof HandledScreen<?>)) return;
        event.set(timerSpeed.get());
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        // Nothing required here for normal behavior — KeyBinding.isPressed() is overridden via mixin.
        // But we keep this hook so that if mixin priority leaves something stuck, we clear it on disable.
    }

    /* ----------------------------- helpers ----------------------------- */

    private void flushDelayedPackets() {
        if (delayedContainerPackets.isEmpty()) return;
        Packet<?>[] snapshot = delayedContainerPackets.toArray(new Packet<?>[0]);
        delayedContainerPackets.clear();
        // Schedule for next tick on the render thread.
        mc.execute(() -> {
            if (mc.getNetworkHandler() == null) return;
            flushingDelayedPackets = true;
            try {
                for (Packet<?> p : snapshot) {
                    mc.getNetworkHandler().getConnection().send(p);
                }
            } finally {
                flushingDelayedPackets = false;
            }
        });
    }

    private boolean isMoving(MoveInputEvent e) {
        return e.getForward() != 0f || e.getStrafe() != 0f;
    }

    private boolean isHandledScreenOpen() {
        return mc.currentScreen instanceof HandledScreen<?>;
    }

    private static boolean isC2SContainerPacket(Packet<?> packet) {
        return packet instanceof ClickSlotC2SPacket
                || packet instanceof ButtonClickC2SPacket
                || packet instanceof CloseHandledScreenC2SPacket
                || packet instanceof CraftRequestC2SPacket
                || packet instanceof SelectMerchantTradeC2SPacket
                || packet instanceof SlotChangedStateC2SPacket;
    }
}
