package dev.mzc.client.gui.clickgui.vape;

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

public class VulkanClickGuiScreen extends ClickGuiScreen {
    private float x, y, width, height;
    private Category currentCategory = Category.Combat;
    private final List<ModuleRect> moduleRects = new ArrayList<>();
    
    private float sidebarWidth = 100;
    private float scrollY = 0;
    private float maxScroll = 0;

    public VulkanClickGuiScreen() {
        super();
        this.width = 500;
        this.height = 350;
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
            Shader2DUtil.drawQuadBlur(new MatrixStack(), 0, 0, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(), 8, 1);
        }

        NanoVGRenderer.INSTANCE.draw(vg -> {
            NanoVGHelper.drawRoundRect(x, y, width, height, 10, new Color(15, 15, 15, 240));
            NanoVGHelper.drawRoundRectOutline(x, y, width, height, 10, 1.5f, new Color(40, 40, 40, 255));

            NanoVGHelper.drawRoundRect(x, y, sidebarWidth, height, 10, new Color(20, 20, 20, 255));
            
            float catY = y + 40;
            for (Category category : Category.values()) {
                boolean selected = category == currentCategory;
                Color color = selected ? ClickGui.color(0) : Color.GRAY;
                
                if (selected) {
                    Color selectedBg = new Color(color.getRed(), color.getGreen(), color.getBlue(), 40);
                    NanoVGHelper.drawRoundRect(x + 5, catY, sidebarWidth - 10, 28, 6, selectedBg);
                }
                
                NanoVGHelper.drawString(category.name(), x + 18, catY + 18, FontLoader.regular(15), 15, color);
                catY += 38;
            }

            NanoVGHelper.drawString(currentCategory.name(), x + sidebarWidth + 20, y + 35, FontLoader.bold(22), 22, Color.WHITE);
            NanoVGHelper.drawRect(x + sidebarWidth + 20, y + 50, width - sidebarWidth - 40, 1.5f, new Color(40, 40, 40));

            renderModules(mouseX, mouseY);
        });
    }

    private void renderModules(int mouseX, int mouseY) {
        float startX = x + sidebarWidth + 20;
        float startY = y + 70 + scrollY;
        float currY = startY;

        for (ModuleRect rect : moduleRects) {
            if (currY + 40 > y + 60 && currY < y + height - 10) {
                rect.render(startX, currY, width - sidebarWidth - 40, mouseX, mouseY);
            }
            currY += 45;
            
            if (rect.expanded) {
                for (ValueComponent valComp : rect.valueComponents) {
                    if (currY + 30 > y + 60 && currY < y + height - 10) {
                        valComp.render(startX + 10, currY, width - sidebarWidth - 60, mouseX, mouseY);
                    }
                    currY += 35;
                }
            }
        }
        maxScroll = Math.max(0, (currY - startY) - (height - 80));
    }

    @Override
    public boolean mouseClicked(Click click, boolean playSound) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        
        if (mouseX >= x && mouseX <= x + sidebarWidth && mouseY >= y + 40 && mouseY <= y + height) {
            float catY = y + 40;
            for (Category category : Category.values()) {
                if (mouseY >= catY && mouseY <= catY + 38) {
                    currentCategory = category;
                    refreshModules();
                    return true;
                }
                catY += 38;
            }
        }

        for (ModuleRect rect : moduleRects) {
            if (rect.isHovered(mouseX, mouseY)) {
                if (button == 0) {
                    rect.module.toggle();
                    return true;
                } else if (button == 1) {
                    rect.expanded = !rect.expanded;
                    return true;
                }
            }
            
            if (rect.expanded) {
                for (ValueComponent valComp : rect.valueComponents) {
                    if (valComp.mouseClicked(mouseX, mouseY, button)) return true;
                }
            }
        }
        return super.mouseClicked(click, playSound);
    }

    @Override
    public boolean mouseReleased(Click click) {
        for (ModuleRect rect : moduleRects) {
            if (rect.expanded) {
                for (ValueComponent valComp : rect.valueComponents) {
                    valComp.mouseReleased(click.x(), click.y(), click.button());
                }
            }
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scrollY += (float) (scrollY * 20);
        this.scrollY = MathHelper.clamp(this.scrollY, -maxScroll, 0);
        return true;
    }

    private void refreshModules() {
        moduleRects.clear();
        for (Module module : Sakura.MODULES.getModsByCategory(currentCategory)) {
            if (ClickGui.moduleFilter.get() == ClickGui.ModuleFilter.Safe && module.getType() == Module.ModuleType.Hack) {
                continue;
            }
            if (ClickGui.moduleFilter.get() == ClickGui.ModuleFilter.Hack && module.getType() != Module.ModuleType.Hack) {
                continue;
            }
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
            Color bg = module.isEnabled() ? ClickGui.color(0) : new Color(30, 30, 30);
            NanoVGHelper.drawRoundRect(x, y, w, 40, 6, bg);
            NanoVGHelper.drawString(module.getEnglishName(), x + 15, y + 25, FontLoader.regular(16), 16, Color.WHITE);
            NanoVGHelper.drawString(expanded ? "-" : "+", x + w - 25, y + 25, FontLoader.regular(20), 20, Color.LIGHT_GRAY);
        }

        public boolean isHovered(double mx, double my) {
            return mx >= lastX && mx <= lastX + lastW && my >= lastY && my <= lastY + 40;
        }
    }

    private abstract static class ValueComponent {
        public abstract void render(float x, float y, float w, int mx, int my);
        public abstract boolean mouseClicked(double mx, double my, int btn);
        public void mouseReleased(double mx, double my, int btn) {}
    }

    private static class BoolComponent extends ValueComponent {
        private final BoolValue val;
        private float lx, ly, lw;
        public BoolComponent(BoolValue val) { this.val = val; }
        @Override
        public void render(float x, float y, float w, int mx, int my) {
            this.lx = x; this.ly = y; this.lw = w;
            NanoVGHelper.drawString(val.getName(), x, y + 20, FontLoader.regular(14), 14, Color.WHITE);
            Color toggleColor = val.get() ? ClickGui.color(0) : Color.DARK_GRAY;
            NanoVGHelper.drawRoundRect(x + w - 30, y + 5, 20, 12, 10, toggleColor);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            if (mx >= lx && mx <= lx + lw && my >= ly && my <= ly + 30) {
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
            double curr = ((Number) val.get()).doubleValue();
            float renderW = (float) ((curr - min) / (max - min) * w);
            
            if (dragging) {
                double newVal = min + (mx - x) / w * (max - min);
                setVal(newVal);
            }

            NanoVGHelper.drawString(val.getName() + ": " + val.get(), x, y + 15, FontLoader.regular(13), 13, Color.LIGHT_GRAY);
            NanoVGHelper.drawRect(x, y + 22, w, 4, new Color(40, 40, 40));
            NanoVGHelper.drawRect(x, y + 22, renderW, 4, ClickGui.color(0));
        }
        
        @SuppressWarnings("unchecked")
        private void setVal(double v) {
            ((NumberValue) val).set(v);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            if (mx >= lx && mx <= lx + lw && my >= ly + 18 && my <= ly + 32) {
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
            String modeName = ((Enum<?>) val.get()).name();
            NanoVGHelper.drawString(val.getName() + ": " + modeName, x, y + 20, FontLoader.regular(14), 14, Color.WHITE);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            if (mx >= lx && mx <= lx + lw && my >= ly && my <= ly + 30) {
                val.cycle();
                return true;
            }
            return false;
        }
    }
}
