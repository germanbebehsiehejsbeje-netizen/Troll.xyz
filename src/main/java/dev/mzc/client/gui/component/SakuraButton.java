package dev.mzc.client.gui.component;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;

public class SakuraButton extends ButtonWidget {
    public SakuraButton(int x, int y, int width, int height, net.minecraft.text.Text message, PressAction onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
    }

    public SakuraButton(int x, int y, int width, int height, String message, PressAction onPress) {
        super(x, y, width, height, net.minecraft.text.Text.literal(message), onPress, DEFAULT_NARRATION_SUPPLIER);
    }

    @Override
    protected void drawIcon(DrawContext context, int mouseX, int mouseY, float delta) {
        // No icon for this button; rely on default label rendering.
    }
}
