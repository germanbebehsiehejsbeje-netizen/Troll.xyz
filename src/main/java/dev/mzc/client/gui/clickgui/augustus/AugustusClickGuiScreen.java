package dev.mzc.client.gui.clickgui.augustus;

import dev.mzc.client.Sakura;
import dev.mzc.client.gui.clickgui.ClickGuiScreen;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.nanovg.NanoVGRenderer;
import dev.mzc.client.nanovg.util.NanoVGHelper;
import dev.mzc.client.nanovg.font.FontLoader;
import dev.mzc.client.utils.render.Shader2DUtil;
import dev.mzc.client.values.Value;
import dev.mzc.client.values.impl.*;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static dev.mzc.client.Sakura.mc;

public class AugustusClickGuiScreen extends ClickGuiScreen {
    private float x, y;
    private final float width, height;
    private Category currentCategory = Category.Combat;
    private final List<ModuleRect> moduleRects = new ArrayList<>();
    
    private final float sidebarWidth = 140;
    private float scrollY = 0;
    private float maxScroll = 0;

    public AugustusClickGuiScreen() {
        super();
        this.width = 600;
        this.height = 420;
    }

    @Override
    public void init() {
        this.x = (mc.getWindow().getScaledWidth() - width) / 2f;
        this.y = (mc.getWindow().getScaledHeight() - height) / 2f;
        refreshModules();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float partialTicks) {
        if (ClickGui.backgroundBlur.get()) {
            Shader2DUtil.drawQuadBlur(new MatrixStack(), 0, 0, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(), ClickGui.blurStrength.get().floatValue(), 1);
        }

        NanoVGRenderer.INSTANCE.draw(vg -> {
            // Main Background (Dark Blue-ish/Black as seen in typical Augustus GUI)
            Color mainBg = new Color(20, 20, 25, 230);
            NanoVGHelper.drawRoundRect(x, y, width, height, 5, mainBg);
            
            // Header / Logo area
            NanoVGHelper.drawRoundRectVarying(vg, x, y, sidebarWidth, 50, 5, 0, 0, 0, new Color(25, 25, 30, 255));
            NanoVGHelper.drawString("AUGUSTUS", x + 20, y + 32, FontLoader.bold(18), 18, Color.WHITE);
            
            // Sidebar Category background
            NanoVGHelper.drawRoundRectVarying(vg, x, y + 50, sidebarWidth, height - 50, 0, 0, 0, 5, new Color(25, 25, 30, 255));

            float catY = y + 70;
            for (Category category : Category.values()) {
                if (category == Category.Search) continue;
                
                boolean selected = category == currentCategory;
                Color color = selected ? ClickGui.color(0) : new Color(150, 150, 160);
                
                if (selected) {
                    // Selection indicator
                    NanoVGHelper.drawRect(x, catY - 10, 3, 30, color);
                }
                
                // Icon + Name (badcache icon font)
                String icon = dev.mzc.client.gui.clickgui.skeet.CategoryIcons.forCategory(category);
                NanoVGHelper.drawString(icon, x + 15, catY + 12, FontLoader.badcache(20), 20, color);
                NanoVGHelper.drawString(category.name().toUpperCase(), x + 45, catY + 10, FontLoader.regular(13), 13, color);
                catY += 35;
            }

            // Right side Header
            NanoVGHelper.drawString(currentCategory.name().toUpperCase(), x + sidebarWidth + 20, y + 32, FontLoader.bold(18), 18, Color.WHITE);
            NanoVGHelper.drawRect(x + sidebarWidth + 20, y + 45, width - sidebarWidth - 40, 1, new Color(255, 255, 255, 20));

            // Scissor for modules
            NanoVGHelper.save();
            NanoVGHelper.scissor(x + sidebarWidth, y + 55, width - sidebarWidth, height - 65);
            renderModules(mouseX, mouseY);
            NanoVGHelper.restore();
        });
    }

    private void renderModules(int mouseX, int mouseY) {
        float startX = x + sidebarWidth + 20;
        float startY = y + 60 + scrollY;
        float currY = startY;

        for (ModuleRect rect : moduleRects) {
            rect.render(startX, currY, width - sidebarWidth - 40, mouseX, mouseY);
            currY += rect.getFullHeight() + 10;
        }
        maxScroll = Math.max(0, (currY - startY) - (height - 80));
    }

    @Override
    public boolean mouseClicked(Click click, boolean playSound) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        
        if (mouseX >= x && mouseX <= x + sidebarWidth && mouseY >= y + 70 && mouseY <= y + height) {
            float catY = y + 70;
            for (Category category : Category.values()) {
                if (category == Category.Search) continue;
                if (mouseY >= catY - 10 && mouseY <= catY + 20) {
                    currentCategory = category;
                    refreshModules();
                    return true;
                }
                catY += 35;
            }
        }

        for (ModuleRect rect : moduleRects) {
            if (rect.mouseClicked(mouseX, mouseY, button)) return true;
        }
        
        return super.mouseClicked(click, playSound);
    }

    @Override
    public boolean mouseReleased(Click click) {
        for (ModuleRect rect : moduleRects) {
            rect.mouseReleased(click.x(), click.y(), click.button());
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scrollY += (float) (scrollY * 30);
        this.scrollY = MathHelper.clamp(this.scrollY, -maxScroll, 0);
        return true;
    }

    private void refreshModules() {
        moduleRects.clear();
        for (Module module : Sakura.MODULES.getModsByCategory(currentCategory)) {
            moduleRects.add(new ModuleRect(module));
        }
    }

    private static class ModuleRect {
        private final Module module;
        private boolean expanded = false;
        private final List<ValueComponent> valueComponents = new ArrayList<>();
        private float lastX, lastY, lastW;

        public ModuleRect(Module module) {
            this.module = module;
            for (Value<?> val : module.getValues()) {
                if (val instanceof BoolValue) valueComponents.add(new BoolComponent((BoolValue) val));
                else if (val instanceof NumberValue) valueComponents.add(new SliderComponent((NumberValue<?>) val));
                else if (val instanceof EnumValue) valueComponents.add(new EnumComponent((EnumValue<?>) val));
            }
        }

        public void render(float x, float y, float w, int mouseX, int mouseY) {
            this.lastX = x; this.lastY = y; this.lastW = w;
            
            // Module Container
            Color moduleBg = new Color(30, 30, 35, 255);
            NanoVGHelper.drawRoundRect(x, y, w, 40, 4, moduleBg);

            Color textColor = module.isEnabled() ? ClickGui.color(0) : Color.WHITE;
            NanoVGHelper.drawString(module.getEnglishName(), x + 12, y + 25, FontLoader.regular(15), 15, textColor);

            // Checkbox for toggle (typical for Augustus)
            float checkSize = 14;
            float checkX = x + w - 25;
            float checkY = y + 13;
            NanoVGHelper.drawRoundRect(checkX, checkY, checkSize, checkSize, 2, new Color(40, 40, 45));
            if (module.isEnabled()) {
                NanoVGHelper.drawRect(checkX + 3, checkY + 3, checkSize - 6, checkSize - 6, ClickGui.color(0));
            }

            if (expanded) {
                float valY = y + 40;
                // Draw a separator line
                NanoVGHelper.drawRect(x + 5, valY, w - 10, 1, new Color(255, 255, 255, 10));
                valY += 5;
                for (ValueComponent valComp : valueComponents) {
                    valComp.render(x, valY, w, mouseX, mouseY);
                    valY += valComp.getHeight();
                }
            }
        }

        public float getFullHeight() {
            float h = 40;
            if (expanded) {
                h += 6; // separator + gap
                for (ValueComponent valComp : valueComponents) h += valComp.getHeight();
            }
            return h;
        }

        public boolean mouseClicked(double mx, double my, int btn) {
            if (mx >= lastX && mx <= lastX + lastW && my >= lastY && my <= lastY + 40) {
                if (btn == 0) {
                    module.toggle();
                    return true;
                } else if (btn == 1) {
                    expanded = !expanded;
                    return true;
                }
            }
            if (expanded) {
                for (ValueComponent valComp : valueComponents) {
                    if (valComp.mouseClicked(mx, my, btn)) return true;
                }
            }
            return false;
        }

        public void mouseReleased(double mx, double my, int btn) {
            if (expanded) {
                for (ValueComponent valComp : valueComponents) valComp.mouseReleased(mx, my, btn);
            }
        }
    }

    private abstract static class ValueComponent {
        public abstract void render(float x, float y, float w, int mx, int my);
        public abstract boolean mouseClicked(double mx, double my, int btn);
        public void mouseReleased(double mx, double my, int btn) {}
        public abstract float getHeight();
    }

    private static class BoolComponent extends ValueComponent {
        private final BoolValue val;
        private float lx, ly, lw;
        public BoolComponent(BoolValue val) { this.val = val; }
        @Override
        public void render(float x, float y, float w, int mx, int my) {
            this.lx = x; this.ly = y; this.lw = w;
            NanoVGHelper.drawString(val.getName(), x + 15, y + 16, FontLoader.regular(13), 13, new Color(200, 200, 200));
            
            float checkSize = 12;
            float checkX = x + w - 22;
            float checkY = y + 4;
            NanoVGHelper.drawRoundRect(checkX, checkY, checkSize, checkSize, 2, new Color(45, 45, 50));
            if (val.get()) {
                NanoVGHelper.drawRect(checkX + 2, checkY + 2, checkSize - 4, checkSize - 4, ClickGui.color(0));
            }
        }
        @Override
        public float getHeight() { return 24; }
        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            if (mx >= lx && mx <= lx + lw && my >= ly && my <= ly + getHeight()) {
                val.set(!val.get());
                return true;
            }
            return false;
        }
    }

    private static class SliderComponent extends ValueComponent {
        private final NumberValue<?> val;
        private float lx, ly, lw;
        private boolean dragging = false;
        public SliderComponent(NumberValue<?> val) { this.val = val; }
        @Override
        public void render(float x, float y, float w, int mx, int my) {
            this.lx = x; this.ly = y; this.lw = w;
            double min = val.getMin().doubleValue();
            double max = val.getMax().doubleValue();
            double curr = val.get().doubleValue();
            float renderW = (float) ((curr - min) / (max - min) * (w - 30));
            
            if (dragging) {
                double newVal = min + (mx - (x + 15)) / (w - 30) * (max - min);
                newVal = MathHelper.clamp(newVal, min, max);
                setVal(newVal);
            }

            NanoVGHelper.drawString(val.getName() + ": " + val.get(), x + 15, y + 12, FontLoader.regular(12), 12, new Color(180, 180, 180));
            NanoVGHelper.drawRect(x + 15, y + 18, w - 30, 2, new Color(45, 45, 50));
            NanoVGHelper.drawRect(x + 15, y + 18, renderW, 2, ClickGui.color(0));
            NanoVGHelper.drawCircle(x + 15 + renderW, y + 19, 4, Color.WHITE);
        }
        @Override
        public float getHeight() { return 30; }
        @SuppressWarnings("unchecked")
        private void setVal(double v) { ((NumberValue<Number>) val).set(v); }
        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            if (mx >= lx + 15 && mx <= lx + lw - 15 && my >= ly + 10 && my <= ly + 25) {
                dragging = true;
                return true;
            }
            return false;
        }
        @Override
        public void mouseReleased(double mx, double my, int btn) { dragging = false; }
    }

    private static class EnumComponent extends ValueComponent {
        private final EnumValue<?> val;
        private float lx, ly, lw;
        public EnumComponent(EnumValue<?> val) { this.val = val; }
        @Override
        public void render(float x, float y, float w, int mx, int my) {
            this.lx = x; this.ly = y; this.lw = w;
            String modeName = val.get().name();
            NanoVGHelper.drawString(val.getName(), x + 15, y + 15, FontLoader.regular(13), 13, new Color(200, 200, 200));
            NanoVGHelper.drawString(modeName, x + w - 15 - NanoVGHelper.getTextWidth(modeName, FontLoader.regular(13), 13), y + 15, FontLoader.regular(13), 13, ClickGui.color(0));
        }
        @Override
        public float getHeight() { return 24; }
        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            if (mx >= lx && mx <= lx + lw && my >= ly && my <= ly + getHeight()) {
                val.cycle();
                return true;
            }
            return false;
        }
    }
}