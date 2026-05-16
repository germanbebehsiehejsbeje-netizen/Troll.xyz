package dev.mzc.client.mixin.input;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.mzc.client.Sakura;
import dev.mzc.client.events.input.MoveInputEvent;
import dev.mzc.client.module.impl.movement.InventoryMove;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.PlayerInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyboardInput.class)
public class MixinKeyboardInput {

    /**
     * If our InventoryMove module wants this key to act as pressed (because a screen is open and the underlying
     * GLFW key is physically held), pretend it is pressed. Mirrors LiquidBounce's hookInventoryMove.
     */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;isPressed()Z"))
    private boolean troll$inventoryMovePressed(KeyBinding key, Operation<Boolean> original) {
        boolean realPressed = original.call(key);
        if (realPressed) return true;

        InventoryMove im = InventoryMove.INSTANCE;
        if (im == null || !im.shouldHandleInputs(key)) return false;

        // Only treat as pressed if the actual GLFW key is currently held.
        try {
            int code = key.getDefaultKey().getCode();
            return GLFW.glfwGetKey(Sakura.mc.getWindow().getHandle(), code) == GLFW.GLFW_PRESS;
        } catch (Throwable t) {
            return false;
        }
    }

    @ModifyExpressionValue(method = "tick", at = @At(value = "NEW", target = "(ZZZZZZZ)Lnet/minecraft/util/PlayerInput;"))
    private PlayerInput modifyInput(PlayerInput original) {
        MoveInputEvent event = new MoveInputEvent(
                original.forward(),
                original.backward(),
                original.left(),
                original.right(),
                original.jump(),
                original.sneak(),
                original.sprint()
        );
        Sakura.EVENT_BUS.post(event);
        return event.toPlayerInput();
    }
}
