package dev.mzc.client.gui.clickgui.component;

import dev.mzc.client.gui.Component;
import dev.mzc.client.gui.IComponent;
import dev.mzc.client.gui.clickgui.component.values.*;
import dev.mzc.client.gui.theme.SakuraTheme;
import dev.mzc.client.module.Module;
import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.nanovg.NanoVGRenderer;
import dev.mzc.client.nanovg.font.FontLoader;
import dev.mzc.client.nanovg.util.NanoVGHelper;
import dev.mzc.client.utils.animations.Direction;
import dev.mzc.client.utils.animations.impl.EaseInOutQuad;
import dev.mzc.client.utils.animations.impl.EaseOutSine;
import dev.mzc.client.utils.color.ColorUtil;
import dev.mzc.client.utils.render.RenderUtil;
import dev.mzc.client.values.Value;
import dev.mzc.client.values.impl.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.nanovg.NanoVG;

import java.awt.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class ModuleComponent implements IComponent {
    private static final int MODULE_HEIGHT = 20;

    private float x, y, width, height = MODULE_HEIGHT;
    private float scale = 1.0f;
    private final Module module;
    private boolean opened;
    private boolean listening = false;
    private boolean previewEnabled = false;
    private final EaseInOutQuad openAnimation = new EaseInOutQuad(250, 1);
    private final EaseOutSine toggleAnimation = new EaseOutSine(300, 1);
    private final EaseOutSine hoverAnimation = new EaseOutSine(200, 1);
    private final EaseInOutQuad visibilityAnimation = new EaseInOutQuad(250, 1);
    private final CopyOnWriteArrayList<Component> settings = new CopyOnWriteArrayList<>();

    public ModuleComponent(Module module) {
        this.module = module;
        openAnimation.setDirection(Direction.BACKWARDS);
        toggleAnimation.setDirection(Direction.BACKWARDS);
        hoverAnimation.setDirection(Direction.BACKWARDS);
        visibilityAnimation.setDirection(Direction.FORWARDS);
        visibilityAnimation.timerUtil.setTime(0);
        refreshSettings();
    }

    public void refreshSettings() {
        settings.clear();
        for (Value<?> value : module.getValues()) {
            if (value instanceof BoolValue boolValue) {
                settings.add(new BoolValueComponent(boolValue));
            } else if (value instanceof RangeValue<?> rangeValue) {
                settings.add(new RangeValueComponent(rangeValue));
            } else if (value instanceof NumberValue<?> numberValue) {
                settings.add(new NumberValueComponent(numberValue));
            } else if (value instanceof EnumValue<?> modeComponent) {
                settings.add(new EnumValueComponent(modeComponent));
            } else if (value instanceof ColorValue colorSetting) {
                settings.add(new ColorValueComponent(colorSetting));
            } else if (value instanceof MultiBoolValue multiBoolValue) {
                settings.add(new MultiBoolValueComponent(multiBoolValue));
            } else if (value instanceof StringValue stringValue) {
                settings.add(new StringValueComponent(stringValue));
            } else if (value instanceof ListValue<?> listValue) {
                settings.add(new ListValueComponent(listValue));
            }
        }
    }

    @Override
    public void render(DrawContext guiGraphics, int mouseX, int mouseY, float partialTicks) {
        float baseFontSize = (float) ClickGui.getFontSize();
        float scaledHeight = MODULE_HEIGHT * scale;
        float yOffset = scaledHeight;
        openAnimation.setDirection(opened ? Direction.FORWARDS : Direction.BACKWARDS);
        toggleAnimation.setDirection(module.isEnabled() || previewEnabled ? Direction.FORWARDS : Direction.BACKWARDS);
        hoverAnimation.setDirection(isHovered(mouseX, mouseY) ? Direction.FORWARDS : Direction.BACKWARDS);

        boolean hasVisibleSettings = false;
        for (Component component : settings) {
            if (!component.isVisible()) continue;
            hasVisibleSettings = true;
            component.setScale(scale);
            yOffset += (float) (component.getHeight() * openAnimation.getOutput().floatValue());
        }

        if (hasVisibleSettings && openAnimation.getOutput().floatValue() > 0) {
            yOffset += (float) (4 * scale * openAnimation.getOutput().floatValue());
        }

        this.height = yOffset;

        final boolean finalHasVisibleSettings = hasVisibleSettings;
        final float finalYOffset = yOffset;

        NanoVGRenderer.INSTANCE.draw(vg -> {
            boolean isSakura = ClickGui.style.is(ClickGui.GuiStyle.Sakura);
            float visibility = (float) visibilityAnimation.getOutput().floatValue();
            Color mainColor = isSakura ? SakuraTheme.PRIMARY : ClickGui.color(0);
            Color secondColor = isSakura ? SakuraTheme.PRIMARY_HOVER : ClickGui.color2(0);
            
            if (visibility < 1.0f) {
                NanoVG.nvgSave(vg);
                NanoVG.nvgScissor(vg, x, y, width, scaledHeight * visibility);
                NanoVG.nvgGlobalAlpha(vg, visibility);
            }

            // Main Background with improved rounding
            if (!isSakura) {
                Color bgColor = new Color(40, 45, 60, 180);
                Color outlineColor = new Color(255, 255, 255, 30);
                NanoVGHelper.drawRoundRect(x, y, width, scaledHeight, 6 * scale, bgColor);
                NanoVGHelper.drawRoundRectOutline(x, y, width, scaledHeight, 6 * scale, 1f, outlineColor);
            }

            // Hover Overlay
            if (hoverAnimation.getOutput().floatValue() > 0) {
                Color hoverColor = isSakura ? new Color(255, 255, 255, 15) : new Color(255, 255, 255, 20);
                NanoVGHelper.drawRect(x, y, width, scaledHeight, ColorUtil.applyOpacity(hoverColor, (float) hoverAnimation.getOutput().floatValue()));
            }

            // Enabled State with Gradient extending to the right
            if (toggleAnimation.getOutput().floatValue() > 0) {
                float anim = (float) toggleAnimation.getOutput().floatValue();
                if (isSakura) {
                    NanoVGHelper.drawRect(x, y, 2 * scale, scaledHeight, ColorUtil.applyOpacity(mainColor, anim));
                    NanoVGHelper.drawGradientRect(x, y, width, scaledHeight, 
                        ColorUtil.applyOpacity(mainColor, anim * 0.15f), 
                        new Color(mainColor.getRed(), mainColor.getGreen(), mainColor.getBlue(), 0));
                } else {
                    // Full width gradient ending transparently on the right
                    NanoVGHelper.drawGradientRect(x, y, width, scaledHeight, 
                        ColorUtil.applyOpacity(mainColor, anim * 0.45f), 
                        new Color(secondColor.getRed(), secondColor.getGreen(), secondColor.getBlue(), 0));
                    // Vertical accent bar
                    NanoVGHelper.drawRect(x, y + 1.5f * scale, 1.8f * scale, (scaledHeight - 3f * scale) * anim, mainColor);
                }
            }

            // Expanded Area Background
            if (finalHasVisibleSettings && openAnimation.getOutput().floatValue() > 0) {
                float expandedHeight = (float) ((finalYOffset - scaledHeight) * openAnimation.getOutput().floatValue());
                if (isSakura) {
                   NanoVGHelper.drawRect(x, y + scaledHeight, width, expandedHeight, new Color(0, 0, 0, 40));
                } else {
                    Color settingsBg = new Color(40, 45, 60, (int) (180 * openAnimation.getOutput().floatValue()));
                    NanoVGHelper.drawRoundRect(x, y + scaledHeight, width, expandedHeight, 6 * scale, settingsBg);
                    NanoVGHelper.drawRoundRectOutline(x, y + scaledHeight, width, expandedHeight, 6 * scale, 1f, new Color(255, 255, 255, 30));
                }
            }

            // Module Name (Lowered position for better symmetry)
            Color textColor = isSakura ? (module.isEnabled() ? SakuraTheme.PRIMARY : SakuraTheme.TEXT) : (module.isEnabled() ? Color.WHITE : new Color(200, 200, 200));
            float textPadding = 7 * scale;
            NanoVGHelper.drawString(module.getDisplayName(), x + textPadding, y + 13 * scale, FontLoader.regular(baseFontSize * 0.85f), baseFontSize * 0.85f, textColor);

            // Bind Box - Only render if a key is bound or listening
            int keyCode = module.getKey();
            boolean hasKey = keyCode != 0 && keyCode != GLFW.GLFW_KEY_UNKNOWN;

            if (hasKey || listening) {
                float boxWidth = 20 * scale;
                float boxHeight = 10 * scale;
                float boxX = x + width - boxWidth - 6 * scale;
                float boxY = y + (scaledHeight - boxHeight) / 2;
                
                Color bindBgColor;
                Color bindBorderColor;

                if (listening) {
                    bindBgColor = new Color(255, 100, 100, 150);
                    bindBorderColor = new Color(255, 150, 150, 200);
                } else {
                    bindBgColor = isSakura ? new Color(0, 0, 0, 30) : new Color(0, 0, 0, 60);
                    bindBorderColor = ColorUtil.applyOpacity(mainColor, 0.5f);
                }

                if (isSakura) {
                    NanoVGHelper.drawRect(boxX, boxY, boxWidth, boxHeight, bindBgColor);
                } else {
                    NanoVGHelper.drawRoundRect(boxX, boxY, boxWidth, boxHeight, 2 * scale, bindBgColor);
                    NanoVGHelper.drawRoundRectOutline(boxX, boxY, boxWidth, boxHeight, 2 * scale, 1f, bindBorderColor);
                }

                float fontSize = 7 * scale;
                String displayText = listening ? "..." : getKeyName(keyCode);
                NanoVGHelper.drawCenteredString(displayText, boxX + boxWidth / 2, boxY + boxHeight / 2 + 2, FontLoader.regular(fontSize), fontSize, isSakura ? SakuraTheme.TEXT_SECONDARY : Color.WHITE);
            }

            if (visibility < 1.0f) {
                NanoVG.nvgRestore(vg);
            }
        });

        if (visibilityAnimation.getOutput().floatValue() <= 0.01f) return;

        float componentYOffset = scaledHeight;
        for (Component component : settings) {
            if (!component.isVisible()) continue;
            component.setX(x + 4 * scale);
            component.setY(y + 12 * scale + componentYOffset);
            component.setWidth(width - 8 * scale);
            if (openAnimation.getOutput().floatValue() > .1f) {
                component.render(guiGraphics, mouseX, mouseY, partialTicks);
            }
            componentYOffset += component.getHeight();
        }

        IComponent.super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (visibilityAnimation.getOutput().floatValue() <= 0.01f) return false;

        if (isBindBoxHovered((int) mouseX, (int) mouseY)) {
            if (mouseButton == 0) {
                listening = !listening;
                return true;
            } else if (mouseButton == 2) {
                module.setBindMode(module.getBindMode() == Module.BindMode.Toggle ? Module.BindMode.Hold : Module.BindMode.Toggle);
                return true;
            } else if (listening) {
                module.setKey(-100 - mouseButton);
                listening = false;
                return true;
            }
        } else if (listening) {
            module.setKey(-100 - mouseButton);
            listening = false;
            return true;
        }

        if (isHovered((int) mouseX, (int) mouseY)) {
            switch (mouseButton) {
                case 0 -> module.toggle();
                case 1 -> opened = !opened;
            }
            return true;
        }
        if (opened) {
            for (Component setting : settings) {
                if (setting.mouseClicked(mouseX, mouseY, mouseButton)) return true;
            }
        }
        return IComponent.super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int state) {
        if (opened) {
            settings.forEach(setting -> setting.mouseReleased(mouseX, mouseY, state));
        }
        return IComponent.super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listening) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                module.setKey(-1);
            } else if (keyCode != GLFW.GLFW_KEY_UNKNOWN) {
                module.setKey(keyCode);
            }
            listening = false;
            return true;
        }
        if (opened) {
            for (Component component : settings) {
                if (component.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
            }
        }
        return IComponent.super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (opened) {
            for (Component setting : settings) {
                if (setting.charTyped(chr, modifiers)) {
                    return true;
                }
            }
        }
        return IComponent.super.charTyped(chr, modifiers);
    }

    public boolean isHovered(int mouseX, int mouseY) {
        return RenderUtil.isHovering(x, y, width, MODULE_HEIGHT * scale, mouseX, mouseY);
    }

    public boolean isBindBoxHovered(int mouseX, int mouseY) {
        // Only consider hovering if the bind box is actually being rendered
        int keyCode = module.getKey();
        boolean hasKey = keyCode != 0 && keyCode != GLFW.GLFW_KEY_UNKNOWN;
        if (!hasKey && !listening) return false;

        float boxWidth = 20 * scale;
        float boxHeight = 10 * scale;
        float boxX = x + width - boxWidth - 6 * scale;
        float boxY = y + (MODULE_HEIGHT * scale - boxHeight) / 2;
        return RenderUtil.isHovering(boxX, boxY, boxWidth, boxHeight, mouseX, mouseY);
    }

    public boolean isListening() {
        return listening;
    }

    public void setListening(boolean listening) {
        this.listening = listening;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public Module getModule() {
        return module;
    }

    public boolean isOpened() {
        return opened;
    }

    public void setOpened(boolean opened) {
        this.opened = opened;
        openAnimation.setDirection(opened ? Direction.FORWARDS : Direction.BACKWARDS);
        if (!opened) {
            openAnimation.timerUtil.setTime(0);
        }
    }

    public EaseInOutQuad getOpenAnimation() {
        return openAnimation;
    }

    public EaseOutSine getToggleAnimation() {
        return toggleAnimation;
    }

    public EaseOutSine getHoverAnimation() {
        return hoverAnimation;
    }

    public CopyOnWriteArrayList<Component> getSettings() {
        return settings;
    }

    public void setX(float x) {
        this.x = x;
    }

    public void setY(float y) {
        this.y = y;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public void setVisible(boolean visible) {
        visibilityAnimation.setDirection(visible ? Direction.FORWARDS : Direction.BACKWARDS);
    }

    public boolean shouldRemove() {
        return !visibilityAnimation.getDirection().forwards() && visibilityAnimation.isDone();
    }

    public double getVisibilityOutput() {
        return visibilityAnimation.getOutput().doubleValue();
    }

    public void resetVisibilityAnimation() {
        visibilityAnimation.reset();
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    public void setPreviewEnabled(boolean previewEnabled) {
        this.previewEnabled = previewEnabled;
    }

    private String getKeyName(int keyCode) {
        if (keyCode < 0) {
            return "M" + (-100 - keyCode);
        }
        try {
            InputUtil.Key key = InputUtil.Type.KEYSYM.createFromCode(keyCode);
            String name = key.getLocalizedText().getString();
            if (name.length() > 6) {
                name = name.substring(0, 5) + ".";
            }
            return name.toUpperCase();
        } catch (Exception e) {
            return "?";
        }
    }
}
