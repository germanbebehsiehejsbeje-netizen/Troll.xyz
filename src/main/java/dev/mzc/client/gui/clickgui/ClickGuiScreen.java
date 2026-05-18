package dev.mzc.client.gui.clickgui;

import dev.mzc.client.Sakura;
import dev.mzc.client.gui.clickgui.panel.CategoryPanel;
import dev.mzc.client.gui.theme.SakuraTheme;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.HudModule;
import dev.mzc.client.module.Module;
import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.nanovg.NanoVGRenderer;
import dev.mzc.client.nanovg.font.FontLoader;
import dev.mzc.client.nanovg.util.NanoVGHelper;
import dev.mzc.client.utils.animations.Animation;
import dev.mzc.client.utils.animations.Direction;
import dev.mzc.client.utils.animations.impl.EaseOutSine;
import dev.mzc.client.utils.render.RenderUtil;
import dev.mzc.client.utils.render.Shader2DUtil;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

import dev.mzc.client.gui.clickgui.component.ModuleComponent;
import dev.mzc.client.module.impl.client.Friend;
import dev.mzc.client.module.impl.client.Home;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static dev.mzc.client.Sakura.mc;
import org.lwjgl.glfw.GLFW;

public class ClickGuiScreen extends Screen {
    public static Animation openingAnimation = new EaseOutSine(400, 1);
    private final List<CategoryPanel> panels = new ArrayList<>();
    public int scroll;
    private DrawContext currentContext;

    // Tabs
    private enum Tab {
        UI("UI"),
        HUD("Hud");

        final String name;
        Tab(String name) { this.name = name; }
    }
    private Tab currentTab = Tab.UI;

    public ClickGuiScreen() {
        super(Text.literal("ClickGui"));
        openingAnimation.setDirection(Direction.BACKWARDS);
        float widthOffset = 0;
        for (Category category : Category.values()) {
            CategoryPanel panel = new CategoryPanel(category);
            panel.setX(50 + widthOffset);
            panel.setY(80); 
            panels.add(panel);
            widthOffset += panel.getWidth() * (float)ClickGui.getGuiScale() + 10;
        }
        

    }

    @Override
    public void init() {
        openingAnimation.setDirection(Direction.FORWARDS);
        openingAnimation.reset();
        
        Friend friendModule = Sakura.MODULES.getModule(Friend.class);
        if (friendModule != null) {
            friendModule.refreshFriends();
            for (CategoryPanel panel : panels) {
                for (ModuleComponent comp : panel.getModuleComponents()) {
                    if (comp.getModule() == friendModule) {
                        comp.refreshSettings();
                        break;
                    }
                }
            }
        }

        Home homeModule = Sakura.MODULES.getModule(Home.class);
        if (homeModule != null) {
            homeModule.refreshHomes();
            for (CategoryPanel panel : panels) {
                for (ModuleComponent comp : panel.getModuleComponents()) {
                    if (comp.getModule() == homeModule) {
                        comp.refreshSettings();
                        break;
                    }
                }
            }
        }

        for (CategoryPanel panel : panels) {
            panel.setOpened(true);
            panel.getOpenAnimation().setDirection(Direction.BACKWARDS);
            panel.getOpenAnimation().timerUtil.setTime(0);
        }
    }

    @Override
    public void render(DrawContext guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.currentContext = guiGraphics;
        boolean isSakura = ClickGui.style.is(ClickGui.GuiStyle.Sakura);
        boolean skajiStyle = ClickGui.style.is(ClickGui.GuiStyle.Skaji);
        
        if (currentTab == Tab.UI) {
            final float wheel = getDWheel();
            if (wheel != 0) {
                scroll += wheel > 0 ? 15 : -15;
                for (CategoryPanel panel : panels) {
                    if (!panel.isDragging()) {
                        panel.setY(panel.getY() + (wheel > 0 ? 15 : -15));
                    }
                }
            }
        }

        // Shader Background
        if (ClickGui.shaderBackground.get()) {
            Shader2DUtil.drawDistortionBackground(
                    new MatrixStack(),
                    0, 0,
                    mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(),
                    partialTicks
            );
        }
        // Global Blur Background
        else if (ClickGui.backgroundBlur.get()) {
            float blurStrength = ClickGui.blurStrength.get().floatValue();
            if (skajiStyle) blurStrength *= 1.35f;
            Shader2DUtil.drawQuadBlur(
                    new MatrixStack(),
                    0, 0,
                    mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(),
                    blurStrength,
                    1.0f
            );
        }

        NanoVGRenderer.INSTANCE.draw(vg -> {
            float sw = mc.getWindow().getScaledWidth();
            float sh = mc.getWindow().getScaledHeight();
            
            // Full Screen Dark Overlay
            NanoVGHelper.drawRect(0, 0, sw, sh, new Color(0, 0, 0, 40));

            // Top Bar Design
            float tabsW = 140;
            float barW = tabsW + 20;
            float barH = 32;
            float barX = (sw - barW) / 2;
            float barY = 25;
            float barRadius = isSakura ? SakuraTheme.PANEL_ROUNDING : 12;

            if (isSakura) {
                NanoVGHelper.drawShadow(barX, barY, barW, barH, barRadius, new Color(0, 0, 0, 128), 20, 0, 2);
                NanoVGHelper.drawRoundRect(barX, barY, barW, barH, barRadius, SakuraTheme.PANEL_BG);
                NanoVGHelper.drawRoundRectOutline(barX, barY, barW, barH, barRadius, 1.5f, new Color(255, 255, 255, 30));
            } else {
                NanoVGHelper.drawRoundRectBloom(barX, barY, barW, barH, barRadius, new Color(0, 0, 0, 80));
                NanoVGHelper.drawRoundRect(barX, barY, barW, barH, barRadius, new Color(40, 45, 60, 200));
                NanoVGHelper.drawRoundRectOutline(barX, barY, barW, barH, barRadius, 1f, new Color(255, 255, 255, 50));
            }

            // TABS
            float tabWidth = tabsW / Tab.values().length;
            float currentTabX = barX;

            for (Tab tab : Tab.values()) {
                boolean hovered = RenderUtil.isHovering(currentTabX, barY, tabWidth, barH, mouseX, mouseY);
                boolean selected = currentTab == tab;

                if (selected) {
                    float padding = 4;
                    Color selectColor = isSakura ? new Color(SakuraTheme.PRIMARY.getRed(), SakuraTheme.PRIMARY.getGreen(), SakuraTheme.PRIMARY.getBlue(), 60) : new Color(255, 255, 255, 35);
                    NanoVGHelper.drawRoundRect(currentTabX + padding, barY + padding, tabWidth - padding * 2, barH - padding * 2, isSakura ? 8 : barRadius - 2, selectColor);
                    if (isSakura) {
                        NanoVGHelper.drawRect(currentTabX + padding + 10, barY + barH - 6, tabWidth - padding * 2 - 20, 1.5f, SakuraTheme.PRIMARY);
                    }
                } else if (hovered) {
                    float padding = 6;
                    NanoVGHelper.drawRoundRect(currentTabX + padding, barY + padding, tabWidth - padding * 2, barH - padding * 2, isSakura ? 9 : barRadius - 3, new Color(255, 255, 255, 18));
                }

                Color textColor = selected ? (isSakura ? SakuraTheme.PRIMARY : Color.WHITE) : (hovered ? new Color(230, 230, 230) : (isSakura ? SakuraTheme.TEXT_SECONDARY : new Color(190, 195, 210)));
                float fontSize = 13;
                float textW = NanoVGHelper.getTextWidth(tab.name, FontLoader.regular(fontSize), fontSize);
                NanoVGHelper.drawString(tab.name, currentTabX + (tabWidth - textW) / 2, barY + barH / 2 + 4.5f, FontLoader.regular(fontSize), fontSize, textColor);
                currentTabX += tabWidth;
            }




        });

        if (currentTab == Tab.UI) {
            panels.forEach(panel -> panel.render(guiGraphics, mouseX, mouseY, partialTicks));
        } else if (currentTab == Tab.HUD) {
            for (Module module : Sakura.MODULES.getAllModules()) {
                if (module instanceof HudModule hud && hud.isEnabled()) {
                    hud.renderInEditor(guiGraphics, mouseX, mouseY);
                }
            }
        }
    }



    @Override
    public boolean mouseClicked(Click click, boolean playSound) {
        double mouseX = click.x();
        double mouseY = click.y();
        int mouseButton = click.button();
        
        float sw = mc.getWindow().getScaledWidth();
        float tabsW = 140;
        float barW = tabsW + 20;
        float barH = 32;
        float barX = (sw - barW) / 2;
        float barY = 25;

        // Tab Clicking
        if (RenderUtil.isHovering(barX, barY, tabsW, barH, (int)mouseX, (int)mouseY)) {
            float tabWidth = tabsW / Tab.values().length;
            int index = (int)((mouseX - barX) / tabWidth);
            if (index >= 0 && index < Tab.values().length) {
                currentTab = Tab.values()[index];
                return true;
            }
        }

        if (currentTab == Tab.UI && currentContext != null) {
            int finalMouseY = (int) mouseY;
            boolean handled = false;
            for (CategoryPanel panel : panels) {
                if (panel.mouseClicked(mouseX, finalMouseY, mouseButton)) {
                    handled = true;
                }
            }
            return handled || super.mouseClicked(click, playSound);
        } else if (currentTab == Tab.HUD) {
            for (Module module : Sakura.MODULES.getAllModules()) {
                if (module instanceof HudModule hud && hud.isEnabled()) {
                    if (hud.mouseClicked((float) mouseX, (float) mouseY, mouseButton)) {
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(click, playSound);
    }



    @Override
    public boolean mouseReleased(Click click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int state = click.button();
        if (currentTab == Tab.UI && currentContext != null) {
            int finalMouseY = (int) mouseY;
            boolean handled = false;
            for (CategoryPanel panel : panels) {
                if (panel.mouseReleased(mouseX, finalMouseY, state)) {
                    handled = true;
                }
            }
            return handled || super.mouseReleased(click);
        } else if (currentTab == Tab.HUD) {
            for (Module module : Sakura.MODULES.getAllModules()) {
                if (module instanceof HudModule hud && hud.isEnabled()) {
                    hud.mouseReleased(state);
                }
            }
        }
        return super.mouseReleased(click);
    }

    @Override
    public void close() {
        Sakura.MODULES.getModule(ClickGui.class).setState(false);
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        int keyCode = keyInput.key();
        int scanCode = keyInput.scancode();
        int modifiers = keyInput.modifiers();

        boolean handled = false;
        if (currentTab == Tab.UI) {
            for (CategoryPanel panel : panels) {
                if (panel.keyPressed(keyCode, scanCode, modifiers)) {
                    handled = true;
                }
            }
        }
        
        if (handled) return true;
        
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            ClickGui clickGui = Sakura.MODULES.getModule(ClickGui.class);
            if (clickGui != null && clickGui.isEnabled()) {
                ClickGui.requestEscapeSuppression(220L);
                clickGui.setState(false);
            }
            return true;
        }
        
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean charTyped(CharInput charInput) {
        if (!charInput.isValidChar()) return super.charTyped(charInput);
        char chr = (char) charInput.codepoint();

        boolean handled = false;
        if (currentTab == Tab.UI) {
            for (CategoryPanel panel : panels) {
                if (panel.charTyped(chr, charInput.modifiers())) {
                    handled = true;
                }
            }
        }
        return handled || super.charTyped(charInput);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    public boolean isBinding() {
        for (CategoryPanel panel : panels) {
            if (panel.isAnyComponentListening()) return true;
        }
        return false;
    }

    private float accumulatedScroll = 0;
    private float getDWheel() {
        float scroll = accumulatedScroll;
        accumulatedScroll = 0;
        return scroll;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        accumulatedScroll += (float) scrollY;
        return true;
    }

    public List<CategoryPanel> getPanels() { return panels; }
}
