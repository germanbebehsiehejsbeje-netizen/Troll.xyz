package dev.mzc.client.gui.hud;

import dev.mzc.client.Sakura;
import dev.mzc.client.module.HudModule;
import dev.mzc.client.module.Module;
import dev.mzc.client.module.impl.client.HudEditor;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

public class HudEditorScreen extends Screen {
    private final HudPanel hudPanel;

    public HudEditorScreen() {
        super(Text.literal("HUD Editor"));
        this.hudPanel = new HudPanel();
        hudPanel.setX(50);
        hudPanel.setY(20);
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        hudPanel.render(context, mouseX, mouseY, delta);
        for (Module module : Sakura.MODULES.getAllModules()) {
            if (module instanceof HudModule hud && hud.isEnabled()) {
                hud.renderInEditor(context, mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean playSound) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (hudPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        for (Module module : Sakura.MODULES.getAllModules()) {
            if (module instanceof HudModule hud && hud.isEnabled()) {
                if (hud.mouseClicked((float) mouseX, (float) mouseY, button)) {
                    return true;
                }
            }
        }
        return super.mouseClicked(click, playSound);
    }

    @Override
    public boolean mouseReleased(Click click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        hudPanel.mouseReleased(mouseX, mouseY, button);

        for (Module module : Sakura.MODULES.getAllModules()) {
            if (module instanceof HudModule hud && hud.isEnabled()) {
                hud.mouseReleased(button);
            }
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        int keyCode = keyInput.key();
        int scanCode = keyInput.scancode();
        int modifiers = keyInput.modifiers();
        if (hudPanel.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean charTyped(CharInput charInput) {
        if (!charInput.isValidChar()) {
            return super.charTyped(charInput);
        }
        char chr = (char) charInput.codepoint();
        int modifiers = charInput.modifiers();
        if (hudPanel.charTyped(chr, modifiers)) {
            return true;
        }
        return super.charTyped(charInput);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!hudPanel.isDragging()) {
            hudPanel.setY(hudPanel.getY() + (scrollY > 0 ? 15 : -15));
        }
        return true;
    }

    @Override
    public void close() {
        super.close();
        HudEditor hudEditor = Sakura.MODULES.getModule(HudEditor.class);
        if (hudEditor != null) {
            hudEditor.setState(false);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    public HudPanel getHudPanel() {
        return hudPanel;
    }
}
