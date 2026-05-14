package dev.mzc.client.module.impl.combat;

import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.mixin.accessor.IMinecraftClient;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.RangeValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.HitResult;

import java.util.Random;

public class AutoClicker extends Module {

    private final Random random = new Random();

    private final BoolValue leftClick = new BoolValue("LeftClick", true);
    private final RangeValue<Double> leftCPS = new RangeValue<>("LeftCPS", 8.0, 12.0, 1.0, 20.0, 0.5, leftClick::get);

    private final BoolValue rightClick = new BoolValue("RightClick", false);
    private final RangeValue<Double> rightCPS = new RangeValue<>("RightCPS", 8.0, 12.0, 1.0, 20.0, 0.5, rightClick::get);
    private final BoolValue requirePressed = new BoolValue("RequirePressed", true);

    private final BoolValue pauseInGui = new BoolValue("PauseInGui", true);
    private final BoolValue usingPause = new BoolValue("UsingPause", true);

    private long nextLeftClickTime = 0;
    private long nextRightClickTime = 0;

    public AutoClicker() {
        super("AutoClicker", Category.Combat);
        this.setType(ModuleType.Safe);
    }

    @Override
    public void onEnable() {
        nextLeftClickTime = 0;
        nextRightClickTime = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        if (pauseInGui.get() && mc.currentScreen != null) {
            nextLeftClickTime = 0;
            nextRightClickTime = 0;
            return;
        }

        if (usingPause.get() && mc.player.isUsingItem()) {
            return;
        }

        ItemStack mainHand = mc.player.getMainHandStack();

        long currentTime = System.currentTimeMillis();

        // Left Click Logic
        boolean allowLeft = mc.crosshairTarget != null && mc.crosshairTarget.getType() != HitResult.Type.BLOCK;
        if (leftClick.get() && allowLeft && (!requirePressed.get() || mc.options.attackKey.isPressed())) {
            if (currentTime >= nextLeftClickTime) {
                ((IMinecraftClient) mc).hookSetAttackCooldown(0);
                mc.options.attackKey.setPressed(true);
                ((IMinecraftClient) mc).hookDoAttack();
                mc.options.attackKey.setPressed(false);

                double min = leftCPS.getMinValue();
                double max = leftCPS.getMaxValue();
                double cps = min + (max - min) * random.nextDouble();
                nextLeftClickTime = currentTime + (long) (1000.0 / cps);
            }
        } else {
            nextLeftClickTime = 0;
        }

        // Right Click Logic
        if (rightClick.get() && (!requirePressed.get() || mc.options.useKey.isPressed())) {
            boolean restrictedUseItem = mainHand.get(DataComponentTypes.FOOD) != null
                    || mainHand.isOf(Items.BOW)
                    || mainHand.isOf(Items.TRIDENT)
                    || mainHand.isOf(Items.CROSSBOW);
            if (restrictedUseItem) {
                nextRightClickTime = 0;
                return;
            }
            if (currentTime >= nextRightClickTime) {
                ((IMinecraftClient) mc).hookSetItemUseCooldown(0);
                mc.options.useKey.setPressed(true);
                ((IMinecraftClient) mc).hookDoItemUse();
                mc.options.useKey.setPressed(false);

                double min = rightCPS.getMinValue();
                double max = rightCPS.getMaxValue();
                double cps = min + (max - min) * random.nextDouble();
                nextRightClickTime = currentTime + (long) (1000.0 / cps);
            }
        } else {
            nextRightClickTime = 0;
        }
    }
}
