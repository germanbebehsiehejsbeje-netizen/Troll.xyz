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
        WINDOWS("Windows"),
        HUD("Hud");

        final String name;
        Tab(String name) { this.name = name; }
    }
    private Tab currentTab = Tab.UI;

    // Search State (integrated in top bar)
    private String searchInput = "";
    private boolean searchFocused = false;

    // Windows (Terminal) State
    private String windowInput = "";
    private final List<String> windowLogs = new ArrayList<>();
    private boolean windowTyping = false;

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
        
        windowLogs.add("MZC System Console v2.1");
        windowLogs.add("Type 'help' for available commands.");
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

        // Global Blur Background
        if (ClickGui.backgroundBlur.get()) {
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
            float tabsW = 260;
            float searchW = 120;
            float barW = tabsW + searchW + 20;
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

            // SEARCH FIELD (Integrated)
            float searchFieldX = barX + tabsW + 10;
            float searchFieldY = barY + 5;
            float searchFieldW = searchW;
            float searchFieldH = barH - 10;
            
            NanoVGHelper.drawRoundRect(searchFieldX, searchFieldY, searchFieldW, searchFieldH, 8, isSakura ? new Color(0, 0, 0, 40) : new Color(0, 0, 0, 60));
            NanoVGHelper.drawRoundRectOutline(searchFieldX, searchFieldY, searchFieldW, searchFieldH, 8, 1f, searchFocused ? (isSakura ? SakuraTheme.PRIMARY : ClickGui.color(0)) : new Color(255, 255, 255, 20));
            
            // Search Icon
            NanoVGHelper.drawString("F", searchFieldX + 8, searchFieldY + searchFieldH / 2 + 5, FontLoader.icons(12), 12, new Color(255, 255, 255, 150));
            
            // Search Text
            String displaySearch = searchInput;
            if (displaySearch.isEmpty() && !searchFocused) displaySearch = "Search...";
            else if (searchFocused && (System.currentTimeMillis() / 500 % 2 == 0)) displaySearch += "|";
            
            NanoVGHelper.drawString(displaySearch, searchFieldX + 24, searchFieldY + searchFieldH / 2 + 4.5f, FontLoader.regular(12), 12, searchInput.isEmpty() ? new Color(255, 255, 255, 80) : Color.WHITE);

            // WINDOWS TAB: Terminal View
            if (currentTab == Tab.WINDOWS) {
                renderTerminal(vg, "Console", windowLogs, windowInput, windowTyping, sw, sh);
            }
        });

        if (currentTab == Tab.UI) {
            panels.forEach(panel -> {
                // Synchronize search text to Search Category panel if it exists
                if (panel.getCategory() == Category.Search) {
                    try {
                        java.lang.reflect.Field field = panel.getClass().getDeclaredField("searchText");
                        field.setAccessible(true);
                        field.set(panel, searchInput);
                    } catch (Exception ignored) {}
                }
                panel.render(guiGraphics, mouseX, mouseY, partialTicks);
            });
        } else if (currentTab == Tab.HUD) {
            for (Module module : Sakura.MODULES.getAllModules()) {
                if (module instanceof HudModule hud && hud.isEnabled()) {
                    hud.renderInEditor(guiGraphics, mouseX, mouseY);
                }
            }
        }
    }

    private void renderTerminal(long vg, String title, List<String> logs, String input, boolean typing, float sw, float sh) {
        float tw = 500, th = 350;
        float tx = (sw - tw) / 2;
        float ty = (sh - th) / 2 + 20;

        NanoVGHelper.drawRoundRectBloom(tx, ty, tw, th, 15, new Color(0, 0, 0, 100));
        NanoVGHelper.drawRoundRect(tx, ty, tw, th, 12, new Color(40, 45, 60, 210));
        NanoVGHelper.drawRoundRectOutline(tx, ty, tw, th, 12, 1, new Color(255, 255, 255, 45));

        float logY = ty + 25;
        int maxLogs = 16;
        int start = Math.max(0, logs.size() - maxLogs);
        for (int i = start; i < logs.size(); i++) {
            NanoVGHelper.drawString(logs.get(i), tx + 15, logY, FontLoader.regular(13), 13, new Color(200, 220, 255));
            logY += 18;
        }

        float inputY = ty + th - 25;
        String displayInput = "> " + input + (typing && (System.currentTimeMillis() / 500 % 2 == 0) ? "_" : "");
        NanoVGHelper.drawString(displayInput, tx + 15, inputY + 10, FontLoader.regular(14), 14, Color.WHITE);
    }

    @Override
    public boolean mouseClicked(Click click, boolean playSound) {
        double mouseX = click.x();
        double mouseY = click.y();
        int mouseButton = click.button();
        
        float sw = mc.getWindow().getScaledWidth();
        float tabsW = 260;
        float searchW = 120;
        float barW = tabsW + searchW + 20;
        float barH = 32;
        float barX = (sw - barW) / 2;
        float barY = 25;

        // Tab Clicking
        if (RenderUtil.isHovering(barX, barY, tabsW, barH, (int)mouseX, (int)mouseY)) {
            float tabWidth = tabsW / Tab.values().length;
            int index = (int)((mouseX - barX) / tabWidth);
            if (index >= 0 && index < Tab.values().length) {
                currentTab = Tab.values()[index];
                searchFocused = false;
                return true;
            }
        }
        
        // Search Clicking
        float searchFieldX = barX + tabsW + 10;
        if (RenderUtil.isHovering(searchFieldX, barY + 5, searchW, barH - 10, (int)mouseX, (int)mouseY)) {
            searchFocused = true;
            return true;
        } else {
            searchFocused = false;
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
        } else if (currentTab == Tab.WINDOWS) {
            windowTyping = true;
            return true;
        }
        return super.mouseClicked(click, playSound);
    }

    private void handleCommand(String input) {
        windowLogs.add("> " + input);
        String[] args = input.trim().split(" ");
        String cmd = args[0].toLowerCase();

        switch (cmd) {
            case "help" -> {
                windowLogs.add("Available commands:");
                windowLogs.add(" - friend add/remove <name>");
                windowLogs.add(" - config save/load <name>");
                windowLogs.add(" - clear");
            }
            case "clear" -> windowLogs.clear();
            case "friend" -> {
                if (args.length >= 3) {
                    if (args[1].equalsIgnoreCase("add")) {
                        dev.mzc.client.manager.Managers.FRIEND.addFriend(args[2]);
                        windowLogs.add("Added " + args[2] + " to friends.");
                    } else if (args[1].equalsIgnoreCase("remove")) {
                        dev.mzc.client.manager.Managers.FRIEND.removeFriend(args[2]);
                        windowLogs.add("Removed " + args[2] + " to friends.");
                    }
                } else windowLogs.add("Usage: friend add/remove <name>");
            }
            case "config" -> {
                if (args.length >= 3) {
                    if (args[1].equalsIgnoreCase("save")) {
                        if (Sakura.CONFIG.saveConfig(args[2])) windowLogs.add("Saved config: " + args[2]);
                    } else if (args[1].equalsIgnoreCase("load")) {
                        if (Sakura.CONFIG.loadConfig(args[2])) windowLogs.add("Loaded config: " + args[2]);
                    }
                } else windowLogs.add("Usage: config save/load <name>");
            }
            default -> windowLogs.add("Unknown command. Type 'help'.");
        }
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
        
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchInput.isEmpty()) {
                searchInput = searchInput.substring(0, searchInput.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                searchFocused = false;
                return true;
            }
        }

        if (currentTab == Tab.WINDOWS && windowTyping) {
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                if (!windowInput.isEmpty()) {
                    handleCommand(windowInput);
                    windowInput = "";
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !windowInput.isEmpty()) {
                windowInput = windowInput.substring(0, windowInput.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                windowTyping = false;
                return true;
            }
        }

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
        
        if (searchFocused) {
            searchInput += chr;
            return true;
        }

        if (currentTab == Tab.WINDOWS && windowTyping) {
            windowInput += chr;
            return true;
        }

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
