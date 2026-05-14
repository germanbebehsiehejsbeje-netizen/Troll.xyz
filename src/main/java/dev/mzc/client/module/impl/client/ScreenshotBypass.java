package dev.mzc.client.module.impl.client;

import dev.mzc.client.Sakura;
import dev.mzc.client.command.impl.MZCCommand;
import dev.mzc.client.events.render.ScreenshotEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import meteordevelopment.orbit.EventHandler;

public class ScreenshotBypass extends Module {

    public ScreenshotBypass() {
        super("ScreenshotBypass", Category.Client);
    }

    @Override
    protected void onEnable() {
        Sakura.EVENT_BUS.subscribe(this);
    }

    @Override
    protected void onDisable() {
        Sakura.EVENT_BUS.unsubscribe(this);
    }

    @EventHandler
    private void onScreenshot(ScreenshotEvent event) {
        if (event.isPre()) {
            // скриншотик 2000
            MZCCommand.hideHudModules();
        } else if (event.isPost()) {
            // прячет вас от блуднова чтобы онт ваш хуй не отсосал
            MZCCommand.showHudModules();
        }
    }
}
