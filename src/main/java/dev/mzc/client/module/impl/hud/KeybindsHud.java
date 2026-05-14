package dev.mzc.client.module.impl.hud;

import dev.mzc.client.Sakura;
import dev.mzc.client.module.HudModule;
import dev.mzc.client.module.Module;
import dev.mzc.client.module.impl.client.HudEditor;
import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.nanovg.NanoVGRenderer;
import dev.mzc.client.nanovg.font.FontLoader;
import dev.mzc.client.nanovg.util.NanoVGHelper;
import dev.mzc.client.values.impl.NumberValue;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.nanovg.NanoVG;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class KeybindsHud extends HudModule {
    private final NumberValue<Double> hudScale = new NumberValue<>("Scale", 1.0, 0.7, 2.0, 0.1);

    public KeybindsHud() {
        super("Keybinds", 150, 100);
    }

    @Override
    public void onRender(DrawContext context) {
        float s = hudScale.get().floatValue();
        List<Module> active = collectActive();
        boolean inEditor = mc.currentScreen != null && Sakura.MODULES.getModule(HudEditor.class) != null && Sakura.MODULES.getModule(HudEditor.class).isEnabled();

        if (active.isEmpty() && !inEditor) return;

        float fontSize = 13f * s;
        int font = FontLoader.regular((int) fontSize);
        float padding = 6 * s;
        float rowH = 14 * s;

        float maxW = NanoVGHelper.getTextWidth("keybinds", font, fontSize);
        for (Module m : active) {
            maxW = Math.max(maxW, NanoVGHelper.getTextWidth(m.getDisplayName() + " [on]", font, fontSize));
        }

        width = maxW + padding * 4;
        height = padding + 15 * s + (active.isEmpty() ? rowH : active.size() * rowH) + 2 * s;

        NanoVGRenderer.INSTANCE.draw(vg -> {
            NanoVGHelper.drawRect(x, y, width, height, new Color(15, 15, 15, 255));
            NanoVGHelper.drawRectOutline(x, y, width, height, 1f * s, new Color(40, 40, 40, 255));
            NanoVGHelper.drawGradientRect(x + 1 * s, y + 1 * s, width - 2 * s, 1.5f * s, ClickGui.color(0), ClickGui.color2(0));

            NanoVGHelper.drawString("keybinds", x + width / 2f, y + padding + 6 * s, font, fontSize, NanoVG.NVG_ALIGN_CENTER | NanoVG.NVG_ALIGN_MIDDLE, Color.WHITE);

            float curY = y + padding + 15 * s;
            if (active.isEmpty()) {
                NanoVGHelper.drawString("none", x + width / 2f, curY + rowH / 2f, font, fontSize, NanoVG.NVG_ALIGN_CENTER | NanoVG.NVG_ALIGN_MIDDLE, Color.GRAY);
            }

            for (Module m : active) {
                NanoVGHelper.drawString(m.getDisplayName().toLowerCase(), x + padding, curY + rowH / 2f, font, fontSize, NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, Color.WHITE);
                NanoVGHelper.drawString("[on]", x + width - padding, curY + rowH / 2f, font, fontSize, NanoVG.NVG_ALIGN_RIGHT | NanoVG.NVG_ALIGN_MIDDLE, Color.WHITE);
                curY += rowH;
            }
        });
    }

    private List<Module> collectActive() {
        List<Module> list = new ArrayList<>();
        for (Module m : Sakura.MODULES.getAllModules()) {
            if (m.isEnabled() && m.getKey() > 0) list.add(m);
        }
        return list;
    }
}