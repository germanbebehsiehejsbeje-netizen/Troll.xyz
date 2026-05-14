package dev.mzc.client.module.impl.client;

import dev.mzc.client.Sakura;
import dev.mzc.client.gui.clickgui.ClickGuiScreen;
import dev.mzc.client.gui.hud.HudEditorScreen;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;

public class HudEditor extends Module {

    public final NumberValue<Double> globalCornerRadius = new NumberValue<>("GlobalCornerRadius", 6.0, 0.0, 20.0, 1.0);
    public final BoolValue enableChatBloom = new BoolValue("EnableChatBloom", true);


    public HudEditor() {
        super("HudEditor", Category.Client);
        this.setType(ModuleType.All);
    }

    @Override
    protected void onEnable() {
        if (mc.currentScreen instanceof ClickGuiScreen) {
            mc.currentScreen.close();
        }

        if (mc.player != null && !(mc.currentScreen instanceof HudEditorScreen)) {
            mc.setScreen(Sakura.HUDEDITOR);
        }
    }

    @Override
    protected void onDisable() {
        if (mc.currentScreen instanceof HudEditorScreen) {
            mc.setScreen(null);
        }

        Sakura.CONFIG.saveDefaultConfig();
    }
}
