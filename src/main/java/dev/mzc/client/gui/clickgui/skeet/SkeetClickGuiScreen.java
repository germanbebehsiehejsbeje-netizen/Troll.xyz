package dev.mzc.client.gui.clickgui.skeet;

import dev.mzc.client.Sakura;
import dev.mzc.client.gui.clickgui.ClickGuiScreen;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.nanovg.NanoVGRenderer;
import dev.mzc.client.nanovg.font.FontLoader;
import dev.mzc.client.nanovg.util.NanoVGHelper;
import dev.mzc.client.utils.render.Shader2DUtil;
import dev.mzc.client.values.Value;
import dev.mzc.client.values.impl.*;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static dev.mzc.client.Sakura.mc;
import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Skeet.cc-style ClickGUI with ProggyTiny pixel font.
 */
public class SkeetClickGuiScreen extends ClickGuiScreen {

    // ─── Font sizes (ProggyTiny is a pixel font — needs specific sizes) ──────
    private static final float FONT_TITLE = 16f;
    private static final float FONT_HEADER = 12f;
    private static final float FONT_LABEL = 12f;
    private static final float FONT_SMALL = 11f;

    // ─── Colors (Skeet palette) ────────────────────────────────────────────────
    private static final Color BG_OUTER = new Color(10, 10, 12, 252);
    private static final Color BG_INNER = new Color(18, 18, 20, 255);
    private static final Color BORDER = new Color(40, 40, 42, 255);
    private static final Color BORDER_LIGHT = new Color(50, 50, 53, 255);
    private static final Color ACCENT = new Color(70, 130, 220, 255);
    private static final Color TEXT_PRIMARY = new Color(210, 210, 215, 255);
    private static final Color TEXT_SECONDARY = new Color(130, 130, 135, 255);
    private static final Color TEXT_DIM = new Color(80, 80, 85, 255);
    private static final Color CHECKBOX_BG = new Color(30, 30, 33, 255);
    private static final Color SLIDER_BG = new Color(30, 30, 33, 255);
    private static final Color SLIDER_FILL = ACCENT;
    private static final Color DROPDOWN_BG = new Color(24, 24, 27, 255);
    private static final Color TAB_HOVER = new Color(28, 28, 30, 255);
    private static final Color TAB_SELECTED = new Color(32, 32, 35, 255);

    // ─── Layout ────────────────────────────────────────────────────────────────
    private static final float SIDEBAR_W = 60;
    private static final float WINDOW_W = 630;
    private static final float WINDOW_H = 430;
    private static final float GROUP_HEADER_H = 22;
    private static final float ITEM_H = 18;
    private static final float ITEM_PAD = 3;
    private static final float GROUP_PAD = 8;
    private static final float COL_GAP = 10;

    private float x, y;
    private Category currentCategory = Category.Combat;
    private float scrollY = 0;
    private float maxScroll = 0;

    private NumberValue<?> draggingSlider = null;
    private float draggingSliderX, draggingSliderW;
    private EnumValue<?> openDropdown = null;

    /**
     * Maps category to badcache icon glyph (delegates to CategoryIcons).
     */
    private static String iconForCategory(Category cat) {
        return CategoryIcons.forCategory(cat);
    }

    public SkeetClickGuiScreen() {
        super();
    }

    @Override
    public void init() {
        this.x = (mc.getWindow().getScaledWidth() - WINDOW_W) / 2f;
        this.y = (mc.getWindow().getScaledHeight() - WINDOW_H) / 2f;
        scrollY = 0;
        openDropdown = null;
    }

    // ─── Helpers for ProggyTiny font ───────────────────────────────────────────

    private int font(float size) {
        return FontLoader.proggyTiny(size);
    }

    private void drawText(String text, float tx, float ty, float size, Color color) {
        NanoVGHelper.drawString(text, tx, ty, font(size), size, color);
    }

    private float textWidth(String text, float size) {
        return NanoVGHelper.getTextWidth(text, font(size), size);
    }

    // ─── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Shader2DUtil.drawQuadBlur(new MatrixStack(), 0, 0,
                mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(), 6, 0.7f);

        NanoVGRenderer.INSTANCE.draw(vg -> {
            // Outer border
            NanoVGHelper.drawRect(x - 1, y - 1, WINDOW_W + 2, WINDOW_H + 2, BORDER);
            // Main background
            NanoVGHelper.drawRect(x, y, WINDOW_W, WINDOW_H, BG_OUTER);

            // Top RGB bar
            renderRgbBar(x, y, WINDOW_W, 1.5f);

            // ─── Sidebar ───────────────────────────────────────────────────
            NanoVGHelper.drawRect(x, y + 2, SIDEBAR_W, WINDOW_H - 2, BG_INNER);
            NanoVGHelper.drawLine(x + SIDEBAR_W, y + 2, x + SIDEBAR_W, y + WINDOW_H, 1, BORDER);

            float tabY = y + 10;
            for (Category cat : Category.values()) {




                boolean selected = cat == currentCategory;
                boolean hovered = mouseX >= x && mouseX <= x + SIDEBAR_W &&
                        mouseY >= tabY && mouseY <= tabY + 44;

                if (selected) {
                    NanoVGHelper.drawRect(x, tabY, SIDEBAR_W, 44, TAB_SELECTED);
                    NanoVGHelper.drawRect(x, tabY, 2, 44, ACCENT);
                } else if (hovered) {
                    NanoVGHelper.drawRect(x, tabY, SIDEBAR_W, 44, TAB_HOVER);
                }

                Color textCol = selected ? ACCENT : (hovered ? TEXT_PRIMARY : TEXT_SECONDARY);
                String icon = iconForCategory(cat);
                float iconSize = 30f;
                int iconFont = FontLoader.badcache(iconSize);
                float iconW = NanoVGHelper.getTextWidth(icon, iconFont, iconSize);
                NanoVGHelper.drawString(icon, x + (SIDEBAR_W - iconW) / 2, tabY + 32, iconFont, iconSize, textCol);

                tabY += 44;
            }

            // ─── Content Area ──────────────────────────────────────────────
            float contentX = x + SIDEBAR_W + 8;
            float contentY = y + 8;
            float contentW = WINDOW_W - SIDEBAR_W - 16;
            float contentH = WINDOW_H - 16;

            drawText(currentCategory.name(), contentX + 4, contentY + 16, FONT_TITLE, TEXT_PRIMARY);
            NanoVGHelper.drawLine(contentX, contentY + 22, contentX + contentW, contentY + 22, 1, BORDER);

            float listY = contentY + 26;
            float listH = contentH - 26;
            NanoVGHelper.save();
            NanoVGHelper.scissor(contentX, listY, contentW, listH);

            float colW = (contentW - COL_GAP) / 2;
            float leftX = contentX;
            float rightX = contentX + colW + COL_GAP;

            List<Module> modules = getModulesForCategory();
            int half = (modules.size() + 1) / 2;


            // Calculate content height WITHOUT rendering
            float leftColumnHeight = calculateColumnHeight(modules.subList(0, Math.min(half, modules.size())));
            float rightColumnHeight = calculateColumnHeight(modules.subList(Math.min(half, modules.size()), modules.size()));
            
            float contentHeight = Math.max(leftColumnHeight, rightColumnHeight);
            maxScroll = Math.max(0, contentHeight - listH);

            // Now render with actual scrollY
            float leftEnd = renderColumn(modules.subList(0, Math.min(half, modules.size())), leftX, listY + scrollY, colW, mouseX, mouseY);
            float rightEnd = renderColumn(modules.subList(Math.min(half, modules.size()), modules.size()), rightX, listY + scrollY, colW, mouseX, mouseY);

            maxScroll = Math.max(0, Math.max(leftEnd, rightEnd) - (listY + listH));


            NanoVGHelper.restore();
            NanoVGHelper.resetScissor();
        });
    }

    private float renderColumn(List<Module> modules, float colX, float startY, float colW, int mouseX, int mouseY) {
        float curY = startY;
        for (Module module : modules) {
            curY = renderModuleGroup(module, colX, curY, colW, mouseX, mouseY);
            curY += GROUP_PAD;
        }
        return curY;
    }


    private float calculateColumnHeight(List<Module> modules) {
        float height = 0;
        for (Module module : modules) {
            height += calculateGroupHeight(module) + GROUP_PAD;
        }
        return height;
    }


    private float renderModuleGroup(Module module, float gx, float gy, float gw, int mouseX, int mouseY) {
        float groupContentH = 0;
        List<Value<?>> visibleValues = new ArrayList<>();
        for (Value<?> val : module.getValues()) {
            if (!val.isAvailable()) continue;
            visibleValues.add(val);
            groupContentH += ITEM_H + ITEM_PAD;
        }
        float totalH = GROUP_HEADER_H + groupContentH + 6;

        // Skeet-style: bordered box, title cuts through top border
        NanoVGHelper.drawRectOutline(gx, gy + 6, gw, totalH - 6, 1, BORDER);

        String title = module.getDisplayName();
        Color headerText = module.isEnabled() ? TEXT_PRIMARY : TEXT_SECONDARY;
        float titleW = textWidth(title, FONT_HEADER);

        NanoVGHelper.drawRect(gx + 10, gy + 4, titleW + 6, 6, BG_OUTER);
        drawText(title, gx + 13, gy + 11, FONT_HEADER, headerText);

        float cbSize = 9;
        float cbX = gx + gw - cbSize - 8;
        float cbY = gy + 6 - cbSize / 2f + 0.5f;
        NanoVGHelper.drawRect(cbX - 2, gy + 4, cbSize + 4, 6, BG_OUTER);
        NanoVGHelper.drawRect(cbX, cbY, cbSize, cbSize, CHECKBOX_BG);
        NanoVGHelper.drawRectOutline(cbX, cbY, cbSize, cbSize, 1, BORDER_LIGHT);
        if (module.isEnabled()) {
            NanoVGHelper.drawRect(cbX + 2, cbY + 2, cbSize - 4, cbSize - 4, ACCENT);
        }

        float itemY = gy + GROUP_HEADER_H;
        for (Value<?> val : visibleValues) {
            renderValue(val, gx + 8, itemY, gw - 16, mouseX, mouseY);
            itemY += ITEM_H + ITEM_PAD;
        }

        return gy + totalH;
    }

    private void renderValue(Value<?> val, float vx, float vy, float vw, int mouseX, int mouseY) {
        if (val instanceof BoolValue boolVal) {
            float cbX = vx;
            float cbY = vy + 4;
            float cbSize = 8;
            NanoVGHelper.drawRect(cbX, cbY, cbSize, cbSize, CHECKBOX_BG);
            NanoVGHelper.drawRectOutline(cbX, cbY, cbSize, cbSize, 1, BORDER_LIGHT);
            if (boolVal.get()) {
                NanoVGHelper.drawRect(cbX + 2, cbY + 2, cbSize - 4, cbSize - 4, ACCENT);
            }
            drawText(val.getDisplayName(), vx + 13, vy + 12, FONT_LABEL, TEXT_PRIMARY);

        } else if (val instanceof NumberValue<?> numVal) {
            String label = val.getDisplayName();
            String valStr = formatNumber(numVal);
            drawText(label, vx, vy + 8, FONT_LABEL, TEXT_SECONDARY);
            float valTextW = textWidth(valStr, FONT_LABEL);
            drawText(valStr, vx + vw - valTextW, vy + 8, FONT_LABEL, TEXT_DIM);

            float sliderY = vy + 14;
            float sliderH = 3;
            double min = numVal.getMin().doubleValue();
            double max = numVal.getMax().doubleValue();
            double cur = numVal.get().doubleValue();
            float pct = (float) ((cur - min) / Math.max(1e-9, max - min));

            NanoVGHelper.drawRect(vx, sliderY, vw, sliderH, SLIDER_BG);
            NanoVGHelper.drawRect(vx, sliderY, vw * pct, sliderH, SLIDER_FILL);

            if (draggingSlider == numVal) {
                double mousePct = MathHelper.clamp((mouseX - draggingSliderX) / draggingSliderW, 0, 1);
                double newVal = min + (max - min) * mousePct;
                applyNumberValue(numVal, newVal);
            }

        } else if (val instanceof EnumValue<?> enumVal) {
            drawText(val.getDisplayName(), vx, vy + 11, FONT_LABEL, TEXT_SECONDARY);

            float boxW = Math.min(80, vw * 0.45f);
            float boxX = vx + vw - boxW;
            float boxY = vy + 2;
            float boxH = 14;

            NanoVGHelper.drawRect(boxX, boxY, boxW, boxH, DROPDOWN_BG);
            NanoVGHelper.drawRectOutline(boxX, boxY, boxW, boxH, 1, BORDER_LIGHT);

            String current = enumVal.get().name();
            drawText(current, boxX + 4, boxY + 11, FONT_SMALL, TEXT_PRIMARY);
            drawText("v", boxX + boxW - 9, boxY + 11, FONT_SMALL, TEXT_DIM);

        } else if (val instanceof ColorValue colorVal) {
            drawText(val.getDisplayName(), vx, vy + 11, FONT_LABEL, TEXT_SECONDARY);
            float previewX = vx + vw - 18;
            float previewY = vy + 4;
            NanoVGHelper.drawRect(previewX, previewY, 14, 9, colorVal.get());
            NanoVGHelper.drawRectOutline(previewX, previewY, 14, 9, 1, BORDER_LIGHT);

        } else {
            drawText(val.getDisplayName(), vx, vy + 11, FONT_LABEL, TEXT_DIM);
        }
    }

    private void renderRgbBar(float bx, float by, float bw, float bh) {
        long time = System.currentTimeMillis();
        float phase = (time % 6000L) / 6000f;

        int stops = 24;
        Color[] colors = new Color[stops + 1];
        for (int i = 0; i <= stops; i++) {
            float hue = ((float) i / stops - phase) % 1f;
            if (hue < 0) hue += 1f;
            colors[i] = Color.getHSBColor(hue, 0.85f, 1.0f);
        }

        float segW = bw / stops;
        for (int i = 0; i < stops; i++) {
            float sx = bx + i * segW;
            NanoVGHelper.drawGradientRect(sx, by, segW + 0.6f, bh, colors[i], colors[i + 1]);
        }
    }

    // ─── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(Click click, boolean playSound) {
        double mx = click.x();
        double my = click.y();
        int btn = click.button();

        // Sidebar
        if (mx >= x && mx <= x + SIDEBAR_W) {
            float tabY = y + 10;
            for (Category cat : Category.values()) {
                if (my >= tabY && my <= tabY + 44) {
                    currentCategory = cat;
                    scrollY = 0;
                    return true;
                }
                tabY += 44;
            }
        }

        float contentX = x + SIDEBAR_W + 8;
        float contentY = y + 8 + 26;
        float contentW = WINDOW_W - SIDEBAR_W - 16;
        float colW = (contentW - COL_GAP) / 2;
        float leftX = contentX;
        float rightX = contentX + colW + COL_GAP;

        List<Module> modules = getModulesForCategory();
        int half = (modules.size() + 1) / 2;

        if (handleColumnClick(modules.subList(0, Math.min(half, modules.size())), leftX, contentY + scrollY, colW, mx, my, btn)) return true;
        if (handleColumnClick(modules.subList(Math.min(half, modules.size()), modules.size()), rightX, contentY + scrollY, colW, mx, my, btn)) return true;

        return super.mouseClicked(click, playSound);
    }

    private boolean handleColumnClick(List<Module> modules, float colX, float startY, float colW, double mx, double my, int btn) {
        float curY = startY;
        for (Module module : modules) {
            float groupH = calculateGroupHeight(module);

            float cbSize = 9;
            float cbX = colX + colW - cbSize - 8;
            float cbY = curY + 6 - cbSize / 2f + 0.5f;
            if (mx >= cbX && mx <= cbX + cbSize && my >= cbY && my <= cbY + cbSize) {
                module.toggle();
                return true;
            }

            float itemY = curY + GROUP_HEADER_H;
            for (Value<?> val : module.getValues()) {
                if (!val.isAvailable()) continue;
                float vx = colX + 8;
                float vw = colW - 16;

                if (my >= itemY && my <= itemY + ITEM_H) {
                    if (val instanceof BoolValue boolVal) {
                        if (mx >= vx && mx <= vx + vw) {
                            boolVal.set(!boolVal.get());
                            return true;
                        }
                    } else if (val instanceof NumberValue<?> numVal) {
                        if (mx >= vx && mx <= vx + vw && my >= itemY + 12) {
                            draggingSlider = numVal;
                            draggingSliderX = vx;
                            draggingSliderW = vw;
                            return true;
                        }
                    } else if (val instanceof EnumValue<?> enumVal) {
                        float boxW = Math.min(80, vw * 0.45f);
                        float boxX = vx + vw - boxW;
                        if (mx >= boxX && mx <= boxX + boxW) {
                            enumVal.cycle();
                            return true;
                        }
                    }
                }
                itemY += ITEM_H + ITEM_PAD;
            }

            curY += groupH + GROUP_PAD;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingSlider = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scrollY += (float) (scrollY * 20);
        this.scrollY = MathHelper.clamp(this.scrollY, -maxScroll, 0);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        if (keyInput.key() == GLFW.GLFW_KEY_ESCAPE) {
            ClickGui clickGui = Sakura.MODULES.getModule(ClickGui.class);
            if (clickGui != null && clickGui.isEnabled()) {
                ClickGui.requestEscapeSuppression(220L);
                clickGui.setState(false);
            }
            return true;
        }
        return super.keyPressed(keyInput);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private List<Module> getModulesForCategory() {
        List<Module> result = new ArrayList<>();
        for (Module module : Sakura.MODULES.getModsByCategory(currentCategory)) {
            if (ClickGui.moduleFilter.get() == ClickGui.ModuleFilter.Safe && module.getType() == Module.ModuleType.Hack) continue;
            if (ClickGui.moduleFilter.get() == ClickGui.ModuleFilter.Hack && module.getType() != Module.ModuleType.Hack) continue;
            result.add(module);
        }
        return result;
    }

    private float calculateGroupHeight(Module module) {
        float h = GROUP_HEADER_H + 6;
        for (Value<?> val : module.getValues()) {
            if (!val.isAvailable()) continue;
            h += ITEM_H + ITEM_PAD;
        }
        return h;
    }

    private String formatNumber(NumberValue<?> val) {
        Number n = val.get();
        if (n instanceof Integer || n instanceof Long) return n.toString();
        return String.format("%.2f", n.doubleValue());
    }

    @SuppressWarnings("unchecked")
    private void applyNumberValue(NumberValue<?> val, double newVal) {
        double min = val.getMin().doubleValue();
        double max = val.getMax().doubleValue();
        newVal = MathHelper.clamp(newVal, min, max);
        double step = val.getStep().doubleValue();
        if (step > 0) newVal = Math.round(newVal / step) * step;

        if (val.get() instanceof Integer) ((NumberValue<Integer>) val).set((int) Math.round(newVal));
        else if (val.get() instanceof Float) ((NumberValue<Float>) val).set((float) newVal);
        else if (val.get() instanceof Double) ((NumberValue<Double>) val).set(newVal);
        else if (val.get() instanceof Long) ((NumberValue<Long>) val).set((long) Math.round(newVal));
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}
