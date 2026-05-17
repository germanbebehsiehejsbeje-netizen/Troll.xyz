package dev.mzc.client.mixin.render;

import dev.mzc.client.Sakura;
import dev.mzc.client.events.input.MouseDraggedEvent;
import dev.mzc.client.module.impl.movement.InventoryMove;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

@Mixin(HandledScreen.class)
public abstract class MixinHandledScreen extends Screen {

    protected MixinHandledScreen(net.minecraft.text.Text title) {
        super(title);
    }

    @Shadow
    @Nullable
    protected abstract Slot getSlotAt(double x, double y);

    @Shadow
    protected abstract void onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType);

    @Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V", at = @At("HEAD"), cancellable = true)
    private void troll$cancelOnMouseClick(Slot slot, int slotId, int button, SlotActionType actionType, CallbackInfo ci) {
        InventoryMove im = InventoryMove.INSTANCE;
        if (im != null && (Object) this instanceof InventoryScreen && im.doNotAllowClicking()) {
            ci.cancel();
        }
    }

    @Shadow
    private boolean doubleClicking;

    private Slot lastClickedSlot;

    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void onMouseReleased(Click click, CallbackInfoReturnable<Boolean> cir) {
        lastClickedSlot = null;
    }

    @Inject(method = "mouseDragged", at = @At("TAIL"))
    private void onMouseDragged(Click click, double offsetX, double offsetY, CallbackInfoReturnable<Boolean> cir) {
        MouseDraggedEvent event = new MouseDraggedEvent();
        Sakura.EVENT_BUS.post(event);

        if (click.button() != GLFW_MOUSE_BUTTON_LEFT || doubleClicking || !event.isCancelled()) {
            return;
        }

        Slot slot = getSlotAt(click.x(), click.y());
        boolean shiftDown = this.client != null && (
                InputUtil.isKeyPressed(this.client.getWindow(), GLFW_KEY_LEFT_SHIFT)
                        || InputUtil.isKeyPressed(this.client.getWindow(), GLFW_KEY_RIGHT_SHIFT)
        );
        if (slot != null && slot.hasStack() && shiftDown && slot != lastClickedSlot) {
            lastClickedSlot = slot;
            onMouseClick(slot, slot.id, click.button(), SlotActionType.QUICK_MOVE);
        }
    }
}
