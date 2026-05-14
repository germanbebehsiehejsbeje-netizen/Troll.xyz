package dev.mzc.client.gui.clickgui.component.values;

import dev.mzc.client.gui.Component;
import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.nanovg.NanoVGRenderer;
import dev.mzc.client.nanovg.font.FontLoader;
import dev.mzc.client.nanovg.util.NanoVGHelper;
import dev.mzc.client.utils.render.RenderUtil;
import dev.mzc.client.values.impl.StringValue;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

import static dev.mzc.client.Sakura.mc;

public class StringValueComponent extends Component {
    private final StringValue setting;
    private boolean editing = false;
    private String tempText = "";
    private int cursorPos = 0;
    private int selectionStart = -1; // -1 表示没有选中
    private long lastBlinkTime = 0;
    private boolean cursorVisible = true;

    public StringValueComponent(StringValue setting) {
        this.setting = setting;
    }

    @Override
    public void render(DrawContext guiGraphics, int mouseX, int mouseY, float partialTicks) {
        float baseFontSize = (float) ClickGui.getFontSize();
        float titleFontSize = baseFontSize * 0.70f;
        setHeight(26 * scale);

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBlinkTime > 530) {
            cursorVisible = !cursorVisible;
            lastBlinkTime = currentTime;
        }

        NanoVGRenderer.INSTANCE.draw(vg -> {
            NanoVGHelper.drawString(setting.getDisplayName(), getX(), getY(), FontLoader.regular(titleFontSize), titleFontSize, new Color(255, 255, 255, 255));

            float inputWidth = getWidth();
            float inputX = getX();
            float inputY = getY() + 5 * scale;
            float inputHeight = 12 * scale;

            NanoVGHelper.drawRoundRect(inputX, inputY, inputWidth, inputHeight, 2 * scale,
                    editing ? new Color(60, 60, 80) : new Color(40, 40, 40));

            NanoVGHelper.drawRoundRectOutline(inputX, inputY, inputWidth, inputHeight, 2 * scale, 0.5f * scale,
                    editing ? new Color(100, 100, 150) : new Color(80, 80, 80));

            String displayText = editing ? tempText : setting.get();
            if (displayText == null) displayText = "";

            float textFontSize = baseFontSize * 0.65f;
            float textWidth = NanoVGHelper.getTextWidth(displayText, FontLoader.regular(textFontSize), textFontSize);
            String trimmedText = displayText;

            if (textWidth > inputWidth - 6 * scale) {
                while (textWidth > inputWidth - 6 * scale && !trimmedText.isEmpty()) {
                    if (editing && cursorPos == displayText.length()) {
                        trimmedText = trimmedText.substring(1);
                    } else {
                        trimmedText = trimmedText.substring(0, trimmedText.length() - 1);
                    }
                    textWidth = NanoVGHelper.getTextWidth(trimmedText + (editing && cursorPos == displayText.length() ? "" : "..."),
                            FontLoader.regular(textFontSize), textFontSize);
                }
                if (!editing || cursorPos < displayText.length()) {
                    trimmedText = trimmedText + "...";
                }
            }

            // 绘制选中区域
            if (editing && hasSelection()) {
                int selStart = Math.min(selectionStart, cursorPos);
                int selEnd = Math.max(selectionStart, cursorPos);
                String beforeSelection = tempText.substring(0, selStart);
                String selectedText = tempText.substring(selStart, selEnd);
                
                float selectionX = inputX + 2 * scale + NanoVGHelper.getTextWidth(beforeSelection, FontLoader.regular(textFontSize), textFontSize);
                float selectionWidth = NanoVGHelper.getTextWidth(selectedText, FontLoader.regular(textFontSize), textFontSize);
                
                NanoVGHelper.drawRect(selectionX, inputY + 2 * scale, selectionWidth, inputHeight - 4 * scale, new Color(100, 150, 255, 100));
            }

            NanoVGHelper.drawString(trimmedText, inputX + 2 * scale, inputY + 9 * scale,
                    FontLoader.regular(textFontSize), textFontSize,
                    editing ? new Color(255, 255, 255) : new Color(200, 200, 200));

            if (editing && cursorVisible && !hasSelection()) {
                String beforeCursor = tempText.substring(0, Math.min(cursorPos, tempText.length()));
                float cursorX = inputX + 2 * scale + NanoVGHelper.getTextWidth(beforeCursor, FontLoader.regular(textFontSize), textFontSize);

                if (cursorX < inputX + inputWidth - 2 * scale) {
                    NanoVGHelper.drawRect(cursorX, inputY + 2 * scale, 0.5f * scale, inputHeight - 4 * scale, new Color(255, 255, 255));
                }
            }
        });

        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        float inputWidth = getWidth() - 8 * scale;
        float inputX = getX() + 1 * scale;
        float inputY = getY() + 5 * scale;
        float inputHeight = 12 * scale;

        if (RenderUtil.isHovering(inputX, inputY, inputWidth, inputHeight, (float) mouseX, (float) mouseY) && mouseButton == 0) {
            if (!editing) {
                editing = true;
                tempText = setting.get();
                cursorPos = tempText.length();
                selectionStart = -1;
                lastBlinkTime = System.currentTimeMillis();
                cursorVisible = true;
            }
            return true;
        } else if (editing && mouseButton == 0) {
            finishEditing();
        }

        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!editing) return false;

        boolean ctrlPressed = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shiftPressed = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

        // Ctrl+A 全选
        if (ctrlPressed && keyCode == GLFW.GLFW_KEY_A) {
            selectionStart = 0;
            cursorPos = tempText.length();
            resetCursor();
            return true;
        }

        // Ctrl+C 复制
        if (ctrlPressed && keyCode == GLFW.GLFW_KEY_C) {
            if (hasSelection()) {
                int selStart = Math.min(selectionStart, cursorPos);
                int selEnd = Math.max(selectionStart, cursorPos);
                String selectedText = tempText.substring(selStart, selEnd);
                mc.keyboard.setClipboard(selectedText);
            }
            return true;
        }

        // Ctrl+X 剪切
        if (ctrlPressed && keyCode == GLFW.GLFW_KEY_X) {
            if (hasSelection()) {
                int selStart = Math.min(selectionStart, cursorPos);
                int selEnd = Math.max(selectionStart, cursorPos);
                String selectedText = tempText.substring(selStart, selEnd);
                mc.keyboard.setClipboard(selectedText);
                deleteSelection();
            }
            return true;
        }

        // Ctrl+V 粘贴
        if (ctrlPressed && keyCode == GLFW.GLFW_KEY_V) {
            String clipboard = mc.keyboard.getClipboard();
            if (clipboard != null && !clipboard.isEmpty()) {
                if (hasSelection()) {
                    deleteSelection();
                }
                tempText = tempText.substring(0, cursorPos) + clipboard + tempText.substring(cursorPos);
                cursorPos += clipboard.length();
                resetCursor();
            }
            return true;
        }

        switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                finishEditing();
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                editing = false;
                selectionStart = -1;
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSelection()) {
                    deleteSelection();
                } else if (cursorPos > 0) {
                    tempText = tempText.substring(0, cursorPos - 1) + tempText.substring(cursorPos);
                    cursorPos--;
                }
                resetCursor();
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (hasSelection()) {
                    deleteSelection();
                } else if (cursorPos < tempText.length()) {
                    tempText = tempText.substring(0, cursorPos) + tempText.substring(cursorPos + 1);
                }
                resetCursor();
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (shiftPressed) {
                    // Shift+左箭头：扩展选择
                    if (selectionStart == -1) {
                        selectionStart = cursorPos;
                    }
                    if (cursorPos > 0) {
                        cursorPos--;
                    }
                } else {
                    // 左箭头：移动光标
                    if (hasSelection()) {
                        cursorPos = Math.min(selectionStart, cursorPos);
                        selectionStart = -1;
                    } else if (cursorPos > 0) {
                        cursorPos--;
                    }
                }
                resetCursor();
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (shiftPressed) {
                    // Shift+右箭头：扩展选择
                    if (selectionStart == -1) {
                        selectionStart = cursorPos;
                    }
                    if (cursorPos < tempText.length()) {
                        cursorPos++;
                    }
                } else {
                    // 右箭头：移动光标
                    if (hasSelection()) {
                        cursorPos = Math.max(selectionStart, cursorPos);
                        selectionStart = -1;
                    } else if (cursorPos < tempText.length()) {
                        cursorPos++;
                    }
                }
                resetCursor();
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                if (shiftPressed) {
                    if (selectionStart == -1) {
                        selectionStart = cursorPos;
                    }
                    cursorPos = 0;
                } else {
                    cursorPos = 0;
                    selectionStart = -1;
                }
                resetCursor();
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                if (shiftPressed) {
                    if (selectionStart == -1) {
                        selectionStart = cursorPos;
                    }
                    cursorPos = tempText.length();
                } else {
                    cursorPos = tempText.length();
                    selectionStart = -1;
                }
                resetCursor();
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!editing) return false;

        if (setting.isOnlyNumber() && !Character.isDigit(chr) && chr != '.' && chr != '-') {
            return false;
        }

        if (hasSelection()) {
            deleteSelection();
        }

        tempText = tempText.substring(0, cursorPos) + chr + tempText.substring(cursorPos);
        cursorPos++;
        resetCursor();

        return true;
    }

    private boolean hasSelection() {
        return selectionStart != -1 && selectionStart != cursorPos;
    }

    private void deleteSelection() {
        if (!hasSelection()) return;
        
        int selStart = Math.min(selectionStart, cursorPos);
        int selEnd = Math.max(selectionStart, cursorPos);
        
        tempText = tempText.substring(0, selStart) + tempText.substring(selEnd);
        cursorPos = selStart;
        selectionStart = -1;
    }

    private void finishEditing() {
        editing = false;
        selectionStart = -1;
        setting.setText(tempText);
    }

    private void resetCursor() {
        lastBlinkTime = System.currentTimeMillis();
        cursorVisible = true;
    }

    @Override
    public boolean isVisible() {
        return setting.isAvailable();
    }
}
