package dev.mzc.client.module.impl.hud;

import dev.mzc.client.Sakura;
import dev.mzc.client.module.HudModule;
import dev.mzc.client.module.Module;
import dev.mzc.client.module.impl.client.HudEditor;
import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.nanovg.NanoVGRenderer;
import dev.mzc.client.nanovg.font.FontLoader;
import dev.mzc.client.nanovg.util.NanoVGHelper;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.nanovg.NanoVG;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class KeybindsHud extends HudModule {
    public enum Style {
        Simple("Simple"),
        Exalted("Exalted");

        private final String name;

        Style(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private final NumberValue<Double> hudScale = new NumberValue<>("Scale", 1.0, 0.7, 2.0, 0.1);
    private final EnumValue<Style> style = new EnumValue<>("Style", Style.Exalted);

    public KeybindsHud() {
        super("Keybinds", 150, 100);
    }

    @Override
    public void onRender(DrawContext context) {
        float s = hudScale.get().floatValue();
        List<Module> active = collectActive();
        boolean inEditor = mc.currentScreen != null && Sakura.MODULES.getModule(HudEditor.class) != null && Sakura.MODULES.getModule(HudEditor.class).isEnabled();

        if (active.isEmpty() && !inEditor) return;

        if (style.is(Style.Exalted)) {
            renderExalted(active, s, inEditor);
        } else {
            renderSimple(active, s, inEditor);
        }
    }

    private void renderSimple(List<Module> active, float s, boolean inEditor) {
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

    private void renderExalted(List<Module> active, float s, boolean inEditor) {
        float fontSize = 15f * s;
        int fontIcon = FontLoader.regular((int) (16f * s));
        int fontModule = FontLoader.regular((int) fontSize);
        
        final float rowHeight = 14f * s;
        final float verticalSpacing = 2.0F * s;
        final float textSpacing = 4.0F * s;
        final float rightPadding = 4.0F * s;
        final float leftPadding = 4.0F * s;
        final float bindOffset = 20.0F * s;
        final float textVerticalOffset = 2.5F * s;

        // Calculate max name width
        float maxNameWidth = 0;
        float maxBindWidth = 0;
        List<String> bindNames = new ArrayList<>();
        
        for (Module module : active) {
            String name = module.getDisplayName();
            String bind = getKeyName(module.getKey());
            bindNames.add(bind);
            
            float nameWidth = NanoVGHelper.getTextWidth(name, fontModule, fontSize);
            float bindWidth = NanoVGHelper.getTextWidth(bind, fontModule, fontSize);
            
            if (nameWidth > maxNameWidth) maxNameWidth = nameWidth;
            if (bindWidth > maxBindWidth) maxBindWidth = bindWidth;
        }

        // Calculate dimensions
        final float finalMaxNameWidth = maxNameWidth;
        final float finalMaxBindWidth = maxBindWidth;
        float headerWidth = NanoVGHelper.getTextWidth("Binds", fontModule, fontSize);
        float totalWidth = leftPadding + maxNameWidth + textSpacing + 2 + bindOffset + leftPadding + maxBindWidth + rightPadding;
        float headerTotal = Math.max(totalWidth, NanoVGHelper.getTextWidth("B", fontIcon, 16f * s) + headerWidth + 10 * s);
        
        width = headerTotal;
        height = 16 * s + (active.isEmpty() ? rowHeight : active.size() * (rowHeight + verticalSpacing)) + 4 * s;

        NanoVGRenderer.INSTANCE.draw(vg -> {
            // Header background
            NanoVGHelper.drawRoundRect(x, y - 1, width, 16 * s, 5 * s, new Color(0, 0, 0, 150));
            
            // Header text
            NanoVGHelper.drawString("B", x + width - 4 * s - NanoVGHelper.getTextWidth("B", fontIcon, 16f * s), 
                    y + 4.9f * s, fontIcon, 16f * s, NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_TOP, 
                    ClickGui.color(0));
            NanoVGHelper.drawString("Binds", x + 5 * s, y + 5 * s, fontModule, fontSize, 
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_TOP, Color.WHITE);

            // Render each module
            float offset = 15 * s;
            int index = 0;
            
            for (Module module : active) {
                float animation = module.getAnimations().getOutput().floatValue();
                float rowTop = y + 2 * s + offset;
                String bind = bindNames.get(index);
                
                float nameRectWidth = leftPadding + finalMaxNameWidth + textSpacing;
                float bindRectWidth = leftPadding + NanoVGHelper.getTextWidth(bind, fontModule, fontSize) + rightPadding;
                
                // Name background
                float nameX = x + nameRectWidth + 2 + bindOffset - 2 - nameRectWidth;
                NanoVGHelper.drawRoundRect(nameX, rowTop, nameRectWidth, rowHeight, 5 * s, new Color(0, 0, 0, 150));
                
                // Bind background
                float bindX = x + nameRectWidth + 2 + bindOffset;
                NanoVGHelper.drawRoundRect(bindX, rowTop, bindRectWidth, rowHeight, 5 * s, new Color(0, 0, 0, 150));
                
                // Module name text
                float nameTextX = bindX + leftPadding - 10 * s - NanoVGHelper.getTextWidth(module.getDisplayName(), fontModule, fontSize);
                NanoVGHelper.drawString(module.getDisplayName(), nameTextX, rowTop + textVerticalOffset, 
                        fontModule, fontSize, NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_TOP, Color.WHITE);
                
                // Bind text
                NanoVGHelper.drawString(bind, bindX + leftPadding, rowTop + textVerticalOffset, 
                        fontModule, fontSize, NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_TOP, Color.WHITE);
                
                offset += animation * (rowHeight + verticalSpacing);
                index++;
            }
        });
    }

    private String getKeyName(int key) {
        if (key <= 0) return "None";
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name == null || name.isEmpty()) {
            // Fallback for special keys
            return switch (key) {
                case GLFW.GLFW_KEY_SPACE -> "SPACE";
                case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
                case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
                case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
                case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
                case GLFW.GLFW_KEY_LEFT_ALT -> "LALT";
                case GLFW.GLFW_KEY_RIGHT_ALT -> "RALT";
                case GLFW.GLFW_KEY_UP -> "UP";
                case GLFW.GLFW_KEY_DOWN -> "DOWN";
                case GLFW.GLFW_KEY_LEFT -> "LEFT";
                case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
                default -> "K" + key;
            };
        }
        return name.toUpperCase();
    }

    private List<Module> collectActive() {
        List<Module> list = new ArrayList<>();
        for (Module m : Sakura.MODULES.getAllModules()) {
            if (m.isEnabled() && m.getKey() > 0) list.add(m);
        }
        return list;
    }
}