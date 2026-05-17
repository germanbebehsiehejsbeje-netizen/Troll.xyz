package dev.mzc.client.gui.clickgui.vape;

import dev.mzc.client.Sakura;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.module.impl.client.Friend;
import dev.mzc.client.module.impl.client.Home;
import dev.mzc.client.values.Value;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.ColorValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.ListValue;
import dev.mzc.client.values.impl.NumberValue;
import dev.mzc.client.values.impl.RangeValue;
import dev.mzc.client.values.impl.StringValue;
import dev.mzc.client.nanovg.NanoVGRenderer;
import dev.mzc.client.nanovg.font.FontLoader;
import dev.mzc.client.nanovg.util.NanoVGHelper;
import dev.mzc.client.utils.TranslationManager;
import dev.mzc.client.utils.animations.Animation;
import dev.mzc.client.utils.animations.Direction;
import dev.mzc.client.utils.animations.impl.DecelerateAnimation;
import dev.mzc.client.utils.animations.impl.EaseOutSine;
import dev.mzc.client.gui.clickgui.SelectionScreen;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.system.MemoryStack;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MZCClickGuiScreen extends Screen {

    private float x, y, width, height;
    private Category currentCategory = Category.Combat;
    private Module selectedModule;
    private float scrollY;
    private float settingsScrollY;
    
    // Dragging
    private boolean dragging;
    private float dragX, dragY;
    
    // Slider Dragging
    private boolean draggingSlider;
    private NumberValue draggingValue;
    private RangeValue<?> draggingRangeValue;
    private boolean draggingRangeUpperHandle;
    
    // Color Picker Dragging
    private boolean draggingColor;
    private ColorValue draggingColorValue;
    private int draggingColorComponent; // 0=R, 1=G, 2=B, 3=A
    private boolean pickingHue;
    private boolean pickingSB;
    
    // Binding
    private boolean binding;
    
    // Layout
    private final float sidebarWidth = 100;
    private final float moduleListWidth = 160;

    // Animation
    private final Animation openingAnimation = new EaseOutSine(250, 1);
    private final Map<Module, Animation> moduleAnimations = new HashMap<>();
    private final Map<Value<?>, Animation> valueAnimations = new HashMap<>();
    private final Animation settingsAnimation = new DecelerateAnimation(250, 1);
    private final Animation searchAnimation = new DecelerateAnimation(250, 1);
    private float categorySelectorY = 0;
    private boolean firstRender = true;
    private float scale = 1.0f;
    
    // Search
    private String searchText = "";
    private boolean searching = false;
    
    // State
    private final Set<ColorValue> expandedColors = new HashSet<>();
    private final Set<EnumValue<?>> expandedEnums = new HashSet<>();
    private boolean closing = false;
    private StringValue focusedStringValue;
    private NumberValue<?> focusedNumberValue;
    private String numberValueBuffer = "";

    private final Map<Module, Animation> searchVisibilityAnimations = new HashMap<>();
    private int iconImageId = -1;
    private final Map<TextWidthKey, Float> textWidthCache = new HashMap<>();
    private final Map<Value<?>, Float> labelWidthCache = new IdentityHashMap<>();

    public MZCClickGuiScreen() {
        super(Text.literal("MZCClickGui"));
        this.width = 600;
        this.height = 400;
    }

    @Override
    protected void init() {
        if (x == 0 && y == 0) {
            this.x = (this.client.getWindow().getScaledWidth() - this.width) / 2;
            this.y = (this.client.getWindow().getScaledHeight() - this.height) / 2;
        }
        if (iconImageId == -1) {
            iconImageId = NanoVGHelper.loadTexture("/assets/sakura/icons/i1con.png");
        }
        openingAnimation.setDirection(Direction.FORWARDS);
        openingAnimation.reset();
        settingsAnimation.setDirection(Direction.BACKWARDS);
        settingsAnimation.reset(); // Ensure settings panel starts hidden/ready
        searchText = "";
        searching = false;
        closing = false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Handle window dragging
        if (dragging) {
            x = mouseX - dragX;
            y = mouseY - dragY;
        }

        if (closing) {
            openingAnimation.setDirection(Direction.BACKWARDS);
            if (openingAnimation.getOutput().floatValue() < 0.05f) {
                Sakura.MODULES.getModule(ClickGui.class).setState(false);
                return;
            }
        }

        float animScale = openingAnimation.getOutput().floatValue();
        
        NanoVGRenderer.INSTANCE.draw(vg -> {
            float centerX = x + width / 2;
            float centerY = y + height / 2;

            NanoVGHelper.save();
            NanoVGHelper.translate(centerX, centerY);
            NanoVGHelper.scale(animScale, animScale);
            NanoVGHelper.translate(-centerX, -centerY);

            // Subtle black glow
            NanoVGHelper.drawShadow(x, y, width, height, 10, new Color(0, 0, 0, 150), 13.5f, 0, 0);

            if (ClickGui.mzcGlow.get()) {
                Color glowColor = ClickGui.color(1);
                Color transparentGlow = new Color(glowColor.getRed(), glowColor.getGreen(), glowColor.getBlue(), 100);
                
                NanoVGHelper.drawRoundRectBloom(x, y, width, height, 10, transparentGlow);
            }

            NanoVGHelper.drawRoundRectScaled(x, y, width, height, 10, new Color(20, 20, 20, 255), 1.0f); // Use 1.0f as we already scaled the context
            
            // --- Sidebar ---
            NanoVGHelper.drawRoundRect(x, y, sidebarWidth, height, 10, new Color(30, 30, 30, 255));
            
            float catYStart = y + 20;
            if (iconImageId != -1) {
                float iconW = 70;
                float iconH = 30;
                float iconX = x + (sidebarWidth - iconW) / 2;
                float iconY = y + 10;
                NanoVGHelper.drawImage(iconImageId, iconX, iconY, iconW, iconH, 1.0f);
                catYStart = iconY + iconH + 10;
            }
            
            float catY = catYStart;
            
            // Calculate Target Y for Selector
            float targetY = catYStart;
            for (Category category : Category.values()) {
                 if (category == currentCategory) break;
                 targetY += 35;
            }
            
            // Interpolate Selector Y
             if (firstRender) {
                 categorySelectorY = targetY;
                 firstRender = false;
             } else {
                 categorySelectorY = categorySelectorY + (targetY - categorySelectorY) * 0.1f;
             }
            
            // Draw Selector
            NanoVGHelper.drawRoundRect(x, categorySelectorY + 5, 3, 15, 1.5f, ClickGui.color(currentCategory.ordinal() * 10));

            for (Category category : Category.values()) {
                boolean selected = category == currentCategory;
                Color color = selected ? ClickGui.color(category.ordinal() * 10) : new Color(150, 150, 150);
                
                if (selected) {
                     NanoVGHelper.drawRoundRect(x + 5, catY, sidebarWidth - 10, 25, 5, new Color(40, 40, 40));
                }

                String icon = dev.mzc.client.gui.clickgui.skeet.CategoryIcons.forCategory(category);
                NanoVGHelper.drawString(icon, x + 15, catY + 19, FontLoader.badcache(16), 16, color);
                NanoVGHelper.drawString(category.name(), x + 35, catY + 18, FontLoader.regular(15), 15, color);
                catY += 35;
            }
            
            // --- Module List ---
            float modListX = x + sidebarWidth;
            // Background for module list
            NanoVGHelper.drawRoundRect(modListX, y, moduleListWidth, height, 0, new Color(25, 25, 25, 255));
            
            // Search Input Logic
            searchAnimation.setDirection(Direction.BACKWARDS);
            float searchAnimVal = searchAnimation.getOutput().floatValue();
            
            float searchBoxHeight = 35 * searchAnimVal;
            float listStartY = y + 10;
            
            if (searchAnimVal > 0.01) {
                float searchY = y + 10;
                int alpha = (int)(255 * searchAnimVal);
                if (alpha > 255) alpha = 255;
                if (alpha < 0) alpha = 0;
                
                // Draw Search Box Background
                NanoVGHelper.drawRoundRect(modListX + 10, searchY, moduleListWidth - 20, 25, 5, new Color(45, 45, 45, alpha));
                
                // Draw Border (Sakura Style)
                Color themeColor = ClickGui.color(1);
                Color borderColor = searching ? themeColor : new Color(60, 60, 60, alpha);
                if (searching) {
                    borderColor = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), alpha);
                }
                NanoVGHelper.drawRoundRectOutline(modListX + 10, searchY, moduleListWidth - 20, 25, 5, 1, borderColor);
                
                String displaySearch = searchText;
                boolean showPlaceholder = searchText.isEmpty() && !searching;
                
                if (showPlaceholder) {
                     displaySearch = TranslationManager.get("ui.search", "Search...");
                }
                
                Color searchColor = showPlaceholder ? new Color(150, 150, 150, alpha) : new Color(255, 255, 255, alpha);
                
                org.lwjgl.nanovg.NanoVG.nvgScissor(vg, modListX + 10, searchY, moduleListWidth - 20, 25);
                
                NanoVGHelper.drawString(displaySearch, modListX + 15, searchY + 17, FontLoader.regular(14), 14, searchColor);
                
                // Vertical Cursor (Sakura Style)
                if (searching && (System.currentTimeMillis() / 500) % 2 == 0) {
                    float textW = showPlaceholder ? 0 : textWidthCached(displaySearch, 14, false);
                    float cursorX = modListX + 15 + textW + (showPlaceholder ? 0 : 1);
                    float cursorY = searchY + 5;
                    float cursorH = 15;
                    
                    NanoVGHelper.drawRect(cursorX, cursorY, 1, cursorH, new Color(255, 255, 255, alpha));
                }
                
                org.lwjgl.nanovg.NanoVG.nvgResetScissor(vg);
                
                listStartY += searchBoxHeight;
            }
            
            List<Module> modules = new java.util.ArrayList<>();
             if (false) {
                  modules = new java.util.ArrayList<>(Sakura.MODULES.getAllModules());
             } else {
                  modules = getFilteredModules();
             }
             
             // Calculate Content Height with Animation
             float contentHeight = 10;
             if (false) {
                  for (Module module : modules) {
                      String name = module.getEnglishName().toLowerCase();
                      String cnName = "";
                      String search = searchText.toLowerCase();
                      boolean visible = name.contains(search) || cnName.contains(search);
                      
                      Animation anim = searchVisibilityAnimations.computeIfAbsent(module, m -> new DecelerateAnimation(200, 1));
                      anim.setDirection(visible ? Direction.FORWARDS : Direction.BACKWARDS);
                      float scale = anim.getOutput().floatValue();
                      if (scale > 0.01) {
                          contentHeight += 30 * scale;
                      }
                  }
             } else {
                 contentHeight += modules.size() * 30;
             }

            float maxScroll = (height - searchBoxHeight) - contentHeight;
            if (maxScroll > 0) maxScroll = 0;
            if (scrollY < maxScroll) scrollY = maxScroll;
            if (scrollY > 0) scrollY = 0;
            
            float modY = listStartY + scrollY;
            
            // Scissor for module list
            org.lwjgl.nanovg.NanoVG.nvgScissor(vg, modListX, listStartY, moduleListWidth, height - (listStartY - y));
            
            int moduleIndex = 0;
            for (Module module : modules) {
                Animation anim = moduleAnimations.computeIfAbsent(module, m -> new DecelerateAnimation(200, 1));
                anim.setDirection(module.isEnabled() ? Direction.FORWARDS : Direction.BACKWARDS);
                float progress = anim.getOutput().floatValue();
                
                float itemHeight = 30;
                float itemAlpha = 1.0f;
                
                if (false) {
                    Animation searchAnim = searchVisibilityAnimations.get(module);
                    float scale = searchAnim != null ? searchAnim.getOutput().floatValue() : 0;
                    if (scale < 0.01) continue;
                    
                    itemHeight = 30 * scale;
                    itemAlpha = scale;
                }

                if (modY + itemHeight > listStartY && modY < y + height) {
                    boolean isSelected = module == selectedModule;
                    Color bgColor = isSelected ? new Color(45, 45, 45, (int)(255 * itemAlpha)) : new Color(35, 35, 35, (int)(255 * itemAlpha));
                    
                    // Interpolate Text Color
                    Color targetColor = ClickGui.color(moduleIndex * 10);
                    int r = (int) (200 + (targetColor.getRed() - 200) * progress);
                    int g = (int) (200 + (targetColor.getGreen() - 200) * progress);
                    int b = (int) (200 + (targetColor.getBlue() - 200) * progress);
                    Color textColor = new Color(r, g, b, (int)(255 * itemAlpha));
                    
                    NanoVGHelper.drawRoundRect(modListX + 5, modY, moduleListWidth - 10, 25, 5, bgColor);
                    
                    if (itemAlpha > 0.5) {
                        NanoVGHelper.save();
                        NanoVGHelper.intersectScissor(modListX, modY, moduleListWidth, itemHeight);
                        NanoVGHelper.drawString(module.getDisplayName(), modListX + 10, modY + 18, FontLoader.regular(15), 15, textColor);
                        NanoVGHelper.restore();
                    }
                    
                    // Active indicator dot
                     if (progress > 0.05 && itemAlpha > 0.5) {
                         NanoVGHelper.drawCircle(modListX + moduleListWidth - 15, modY + 12.5f, 3 * progress, textColor);
                     }
                }
                modY += itemHeight;
                if (true) {
                     moduleIndex++;
                }
            }
            org.lwjgl.nanovg.NanoVG.nvgResetScissor(vg);
            
            // --- Settings Panel ---
            float settingsX = modListX + moduleListWidth;
            float settingsWidth = width - sidebarWidth - moduleListWidth;
            float cornerRadius = ClickGui.mzcCornerRadius.get().floatValue();

            SettingsLayout settingsLayout = computeSettingsLayout(settingsX, settingsWidth);
            if (settingsLayout != null) {
                float drawSettingsX = settingsLayout.drawSettingsX;
                int settingsAlpha = settingsLayout.alpha;

                NanoVG.nvgScissor(vg, settingsX, y, settingsWidth, height);
                NanoVGHelper.drawRoundRect(settingsX, y, settingsWidth, height, cornerRadius, new Color(25, 25, 25, settingsAlpha));

                float titleY = y + 10 + settingsScrollY;
                NanoVGHelper.drawString(selectedModule.getDisplayName() + " Settings", drawSettingsX + 10, titleY + 18, FontLoader.bold(18), 18, new Color(255, 255, 255, settingsAlpha));

                String bindText = "Bind: " + (binding ? "Listening..." : (selectedModule.getKey() == -1 ? "None" :
                        (selectedModule.getKey() < 0 ? "M" + (-100 - selectedModule.getKey()) :
                                GLFW.glfwGetKeyName(selectedModule.getKey(), 0))));
                if (bindText.contains("null")) bindText = "Bind: " + selectedModule.getKey();
                Color bindColor = binding ? ClickGui.color(1) : new Color(128, 128, 128);
                NanoVGHelper.drawString(bindText, drawSettingsX + 10, titleY + 25 + 18, FontLoader.regular(14), 14, new Color(bindColor.getRed(), bindColor.getGreen(), bindColor.getBlue(), settingsAlpha));

                for (SettingsRow row : settingsLayout.rows) {
                    float rowY = row.rowRect().y;
                    if (rowY + 100 <= y || rowY >= y + height) continue;

                    if (row instanceof BoolRow boolRow) {
                        Animation boolAnim = valueAnimations.computeIfAbsent(boolRow.value, v -> {
                            DecelerateAnimation anim = new DecelerateAnimation(200, 1);
                            if (boolRow.value.get()) {
                                anim.setDirection(Direction.FORWARDS);
                                anim.timerUtil.setTime(anim.timerUtil.getCurrentMS() - 5000);
                            } else {
                                anim.setDirection(Direction.BACKWARDS);
                                anim.timerUtil.setTime(anim.timerUtil.getCurrentMS() - 5000);
                            }
                            return anim;
                        });
                        boolAnim.setDirection(boolRow.value.get() ? Direction.FORWARDS : Direction.BACKWARDS);
                        float boolProgress = boolAnim.getOutput().floatValue();

                        NanoVGHelper.drawString(boolRow.value.getDisplayName(), drawSettingsX + 10, rowY + 18, FontLoader.regular(14), 14, new Color(255, 255, 255, settingsAlpha));

                        Color offColor = new Color(60, 60, 60, settingsAlpha);
                        Color theme = ClickGui.color(1);
                        Color onColor = new Color(theme.getRed(), theme.getGreen(), theme.getBlue(), settingsAlpha);

                        int r = (int) (offColor.getRed() + (onColor.getRed() - offColor.getRed()) * boolProgress);
                        int g = (int) (offColor.getGreen() + (onColor.getGreen() - offColor.getGreen()) * boolProgress);
                        int b = (int) (offColor.getBlue() + (onColor.getBlue() - offColor.getBlue()) * boolProgress);
                        Color bgColor = new Color(r, g, b, settingsAlpha);

                        NanoVGHelper.drawRoundRect(boolRow.switchRect.x, boolRow.switchRect.y, boolRow.switchRect.w, boolRow.switchRect.h, 6, bgColor);

                        float circleR = 4;
                        float circleXStart = boolRow.switchRect.x + 6;
                        float circleXEnd = boolRow.switchRect.x + boolRow.switchRect.w - 6;
                        float circleX = circleXStart + (circleXEnd - circleXStart) * boolProgress;
                        float circleY = boolRow.switchRect.y + boolRow.switchRect.h / 2;
                        NanoVGHelper.drawCircle(circleX, circleY, circleR, new Color(255, 255, 255, settingsAlpha));
                    } else if (row instanceof NumberRow numberRow) {
                        float rowCenterY = rowY + 12.5f;
                        NanoVGHelper.drawString(numberRow.value.getDisplayName(), drawSettingsX + 10, rowCenterY + 5, FontLoader.regular(14), 14, new Color(255, 255, 255, settingsAlpha));

                        boolean isFocused = focusedNumberValue == numberRow.value;
                        String valStr = isFocused ? numberValueBuffer + (System.currentTimeMillis() / 500 % 2 == 0 ? "_" : "") :
                                (numberRow.value.get() instanceof Float || numberRow.value.get() instanceof Double ? String.format("%.2f", numberRow.value.get()) : numberRow.value.get().toString());

                        NanoVGHelper.drawRoundRect(numberRow.valueBoxRect.x, numberRow.valueBoxRect.y, numberRow.valueBoxRect.w, numberRow.valueBoxRect.h, 3, new Color(40, 40, 40, settingsAlpha));
                        NanoVGHelper.drawRoundRectOutline(numberRow.valueBoxRect.x, numberRow.valueBoxRect.y, numberRow.valueBoxRect.w, numberRow.valueBoxRect.h, 3, 1, new Color(60, 60, 60, settingsAlpha));

                        NanoVGHelper.save();
                        NanoVGHelper.intersectScissor(numberRow.valueBoxRect.x, numberRow.valueBoxRect.y, numberRow.valueBoxRect.w, numberRow.valueBoxRect.h);
                        float textW = NanoVGHelper.getTextWidth(valStr, FontLoader.regular(12), 12);
                        float textX = numberRow.valueBoxRect.x + (numberRow.valueBoxRect.w - textW) / 2;
                        NanoVGHelper.drawString(valStr, textX, numberRow.valueBoxRect.y + 11, FontLoader.regular(12), 12, new Color(220, 220, 220, settingsAlpha));
                        NanoVGHelper.restore();

                        if (numberRow.sliderW > 20) {
                            double min = numberRow.value.getMin().doubleValue();
                            double max = numberRow.value.getMax().doubleValue();
                            double val = ((Number) numberRow.value.get()).doubleValue();
                            double percent = (val - min) / (max - min);

                            NanoVGHelper.drawRoundRect(numberRow.sliderX, numberRow.sliderY, numberRow.sliderW, 4, 2, new Color(60, 60, 60, settingsAlpha));
                            Color theme = ClickGui.color(1);
                            NanoVGHelper.drawRoundRect(numberRow.sliderX, numberRow.sliderY, (float) (numberRow.sliderW * percent), 4, 2, new Color(theme.getRed(), theme.getGreen(), theme.getBlue(), settingsAlpha));
                            float handleX = numberRow.sliderX + (float) (numberRow.sliderW * percent);
                            NanoVGHelper.drawCircle(handleX, numberRow.sliderY + 2, 5, new Color(255, 255, 255, settingsAlpha));

                            if (draggingSlider && draggingValue == numberRow.value) {
                                double mousePercent = (mouseX - numberRow.sliderX) / numberRow.sliderW;
                                mousePercent = Math.max(0, Math.min(1, mousePercent));
                                double newVal = min + (max - min) * mousePercent;

                                if (numberRow.value.get() instanceof Integer) {
                                    ((NumberValue<Integer>) numberRow.value).set((int) Math.round(newVal));
                                } else if (numberRow.value.get() instanceof Float) {
                                    float step = ((NumberValue<Float>) numberRow.value).getStep().floatValue();
                                    if (step > 0) newVal = Math.round(newVal / step) * step;
                                    ((NumberValue<Float>) numberRow.value).set((float) newVal);
                                } else if (numberRow.value.get() instanceof Double) {
                                    double step = ((NumberValue<Double>) numberRow.value).getStep().doubleValue();
                                    if (step > 0) newVal = Math.round(newVal / step) * step;
                                    ((NumberValue<Double>) numberRow.value).set(newVal);
                                } else if (numberRow.value.get() instanceof Long) {
                                    ((NumberValue<Long>) numberRow.value).set((long) Math.round(newVal));
                                }
                            }
                        }
                    } else if (row instanceof RangeRow rangeRow) {
                        float rowCenterY = rowY + 12.5f;
                        NanoVGHelper.drawString(rangeRow.value.getDisplayName(), drawSettingsX + 10, rowCenterY + 5, FontLoader.regular(14), 14, new Color(255, 255, 255, settingsAlpha));

                        float valueBoxW = 74;
                        float valueBoxX = drawSettingsX + settingsWidth - 10 - valueBoxW;
                        float valueBoxY = rowCenterY - 7.5f;

                        Number rvMin = rangeRow.value.getMinValue();
                        Number rvMax = rangeRow.value.getMaxValue();
                        boolean integerRange = rvMin instanceof Integer || rvMin instanceof Long;
                        String valStr = integerRange ? String.format("%d-%d", rvMin.intValue(), rvMax.intValue()) : String.format("%.2f-%.2f", rvMin.doubleValue(), rvMax.doubleValue());

                        NanoVGHelper.drawRoundRect(valueBoxX, valueBoxY, valueBoxW, 15, 3, new Color(40, 40, 40, settingsAlpha));
                        NanoVGHelper.drawRoundRectOutline(valueBoxX, valueBoxY, valueBoxW, 15, 3, 1, new Color(60, 60, 60, settingsAlpha));

                        NanoVGHelper.save();
                        NanoVGHelper.intersectScissor(valueBoxX, valueBoxY, valueBoxW, 15);
                        float textW = NanoVGHelper.getTextWidth(valStr, FontLoader.regular(12), 12);
                        float textX = valueBoxX + (valueBoxW - textW) / 2;
                        NanoVGHelper.drawString(valStr, textX, valueBoxY + 11, FontLoader.regular(12), 12, new Color(220, 220, 220, settingsAlpha));
                        NanoVGHelper.restore();

                        if (rangeRow.sliderW > 20) {
                            double min = rangeRow.value.getMin().doubleValue();
                            double max = rangeRow.value.getMax().doubleValue();
                            double lower = rangeRow.value.getMinValue().doubleValue();
                            double upper = rangeRow.value.getMaxValue().doubleValue();
                            double denom = Math.max(1.0E-9, (max - min));
                            double lowPercent = (lower - min) / denom;
                            double highPercent = (upper - min) / denom;

                            NanoVGHelper.drawRoundRect(rangeRow.sliderX, rangeRow.sliderY, rangeRow.sliderW, 4, 2, new Color(60, 60, 60, settingsAlpha));

                            Color theme = ClickGui.color(1);
                            float selectedX = rangeRow.sliderX + (float) (rangeRow.sliderW * lowPercent);
                            float selectedW = (float) (rangeRow.sliderW * (highPercent - lowPercent));
                            float extend = 1.5f * scale;
                            float extStart = Math.max(rangeRow.sliderX, selectedX - extend);
                            float extEnd = Math.min(rangeRow.sliderX + rangeRow.sliderW, selectedX + selectedW + extend);
                            NanoVGHelper.drawRoundRect(extStart, rangeRow.sliderY, Math.max(0.0f, extEnd - extStart), 4, 2, new Color(theme.getRed(), theme.getGreen(), theme.getBlue(), settingsAlpha));

                            float lowHandleX = rangeRow.sliderX + (float) (rangeRow.sliderW * lowPercent);
                            float highHandleX = rangeRow.sliderX + (float) (rangeRow.sliderW * highPercent);
                            drawRightAngleTriangle(lowHandleX, rangeRow.sliderY + 2, 9 * scale, true, new Color(255, 255, 255, settingsAlpha));
                            drawRightAngleTriangle(highHandleX, rangeRow.sliderY + 2, 9 * scale, false, new Color(255, 255, 255, settingsAlpha));

                            if (draggingSlider && draggingRangeValue == rangeRow.value) {
                                double mousePercent = (mouseX - rangeRow.sliderX) / rangeRow.sliderW;
                                mousePercent = Math.max(0, Math.min(1, mousePercent));
                                double newVal = min + (max - min) * mousePercent;
                                applyRangeDrag(rangeRow.value, newVal, draggingRangeUpperHandle);
                            }
                        }
                    } else if (row instanceof EnumRow enumRow) {
                        NanoVGHelper.drawString(enumRow.value.getDisplayName(), drawSettingsX + 10, rowY + 18, FontLoader.regular(14), 14, new Color(255, 255, 255, settingsAlpha));

                        Enum<?> currentEnum = (Enum<?>) enumRow.value.get();
                        String valStr = TranslationManager.get(TranslationManager.enumKey(currentEnum), currentEnum.toString());

                        NanoVGHelper.drawRoundRect(enumRow.boxRect.x, enumRow.boxRect.y, enumRow.boxRect.w, enumRow.boxRect.h, 4, new Color(40, 40, 40, settingsAlpha));
                        NanoVGHelper.drawRoundRectOutline(enumRow.boxRect.x, enumRow.boxRect.y, enumRow.boxRect.w, enumRow.boxRect.h, 4, 1, new Color(60, 60, 60, settingsAlpha));
                        NanoVGHelper.drawCenteredString(valStr, enumRow.boxRect.x + enumRow.boxRect.w / 2, enumRow.boxRect.y + 10f, FontLoader.regular(14), 14, new Color(220, 220, 220, settingsAlpha));

                        if (enumRow.progress > 0.01f) {
                            float dropdownX = enumRow.boxRect.x;
                            float dropdownW = enumRow.boxRect.w;
                            float itemHeight = enumRow.itemHeight;
                            float fullH = enumRow.constants.length * itemHeight;
                            float clipH = fullH * enumRow.progress;
                            float dropdownY = rowY + 25;

                            NanoVGHelper.save();
                            NanoVGHelper.intersectScissor(dropdownX, dropdownY, dropdownW, clipH);

                            NanoVGHelper.drawRoundRect(dropdownX, dropdownY, dropdownW, fullH, 5, new Color(30, 30, 30, settingsAlpha));
                            NanoVGHelper.drawRoundRectOutline(dropdownX, dropdownY, dropdownW, fullH, 5, 1, new Color(60, 60, 60, settingsAlpha));

                            float optY = dropdownY;
                            for (Object constant : enumRow.constants) {
                                boolean selected = constant == enumRow.value.get();
                                Color textColor = selected ? ClickGui.color(1) : new Color(200, 200, 200, settingsAlpha);
                                if (selected) {
                                    NanoVGHelper.drawRoundRect(dropdownX + 2, optY + 2, dropdownW - 4, itemHeight - 4, 3, new Color(50, 50, 50, settingsAlpha));
                                }
                                String optionStr = TranslationManager.get(TranslationManager.enumKey((Enum<?>) constant), constant.toString());
                                NanoVGHelper.drawCenteredString(optionStr, dropdownX + dropdownW / 2, optY + itemHeight / 2 + 1.5f, FontLoader.regular(14), 14, textColor);
                                optY += itemHeight;
                            }

                            NanoVGHelper.restore();
                        }
                    } else if (row instanceof ListRow listRow) {
                        NanoVGHelper.drawString(listRow.value.getDisplayName(), drawSettingsX + 10, rowY + 18, FontLoader.regular(14), 14, new Color(255, 255, 255, settingsAlpha));

                        NanoVGHelper.drawRect(listRow.buttonRect.x, listRow.buttonRect.y, listRow.buttonRect.w, listRow.buttonRect.h, new Color(20, 20, 20, settingsAlpha));
                        NanoVGHelper.drawRectOutline(listRow.buttonRect.x, listRow.buttonRect.y, listRow.buttonRect.w, listRow.buttonRect.h, 1, new Color(60, 60, 60, settingsAlpha));
                        if (mouseX >= listRow.buttonRect.x && mouseX <= listRow.buttonRect.x + listRow.buttonRect.w && mouseY >= listRow.buttonRect.y && mouseY <= listRow.buttonRect.y + listRow.buttonRect.h) {
                            NanoVGHelper.drawRectOutline(listRow.buttonRect.x, listRow.buttonRect.y, listRow.buttonRect.w, listRow.buttonRect.h, 1, new Color(100, 100, 100, settingsAlpha));
                        }
                        String btnText = TranslationManager.get("ui.select", "Select");
                        NanoVGHelper.drawCenteredString(btnText, listRow.buttonRect.x + listRow.buttonRect.w / 2, listRow.buttonRect.y + 10f, FontLoader.regular(14), 14, new Color(200, 200, 200, settingsAlpha));
                    } else if (row instanceof StringRow stringRow) {
                        boolean focused = focusedStringValue == stringRow.value;
                        NanoVGHelper.drawString(stringRow.value.getDisplayName() + ":", drawSettingsX + 10, rowY + 18, FontLoader.regular(14), 14, new Color(255, 255, 255, settingsAlpha));

                        NanoVGHelper.drawRoundRect(stringRow.inputRect.x, stringRow.inputRect.y, stringRow.inputRect.w, stringRow.inputRect.h, 3, new Color(40, 40, 40, settingsAlpha));
                        NanoVGHelper.drawRoundRectOutline(stringRow.inputRect.x, stringRow.inputRect.y, stringRow.inputRect.w, stringRow.inputRect.h, 3, 1, new Color(60, 60, 60, settingsAlpha));

                        String text = stringRow.value.getText() + (focused && (System.currentTimeMillis() / 500 % 2 == 0) ? "_" : "");
                        NanoVGHelper.save();
                        NanoVGHelper.intersectScissor(stringRow.inputRect.x, stringRow.inputRect.y, stringRow.inputRect.w, stringRow.inputRect.h);
                        NanoVGHelper.drawString(text, stringRow.inputRect.x + 5, stringRow.inputRect.y + 11.5f, FontLoader.regular(14), 14, new Color(230, 230, 230, settingsAlpha));
                        NanoVGHelper.restore();
                    } else if (row instanceof ColorRow colorRow) {
                        NanoVGHelper.drawString(colorRow.value.getDisplayName(), drawSettingsX + 10, rowY + 18, FontLoader.regular(14), 14, new Color(255, 255, 255, settingsAlpha));

                        float previewSize = 12;
                        float previewX = drawSettingsX + settingsWidth - 20;
                        NanoVGHelper.drawRoundRect(previewX, rowY + 6, previewSize, previewSize, 3, colorRow.value.get());

                        Color previewColor = colorRow.value.get();
                        String hex = String.format("#%02X%02X%02X%02X", previewColor.getRed(), previewColor.getGreen(), previewColor.getBlue(), previewColor.getAlpha());
                        float hexWidth = NanoVGHelper.getTextWidth(hex, FontLoader.regular(12), 12);
                        NanoVGHelper.drawString(hex, previewX - hexWidth - 5, rowY + 17, FontLoader.regular(12), 12, new Color(180, 180, 180, settingsAlpha));

                        if (colorRow.expanded) {
                            float totalHeight = colorRow.panelHeight + 10;
                            float currentHeight = totalHeight * colorRow.progress;
                            float contentW = settingsWidth - 40;

                            NanoVGHelper.save();
                            NanoVGHelper.intersectScissor(colorRow.panelX, colorRow.panelY, contentW, currentHeight);

                            float[] hsb = {colorRow.value.getHue(), colorRow.value.getSaturation(), colorRow.value.getBrightness()};

                            if (pickingSB && draggingColorValue == colorRow.value && colorRow.sbWidth > 0 && colorRow.sbHeight > 0) {
                                colorRow.value.setSaturation(clamp01((float) ((mouseX - colorRow.panelX) / colorRow.sbWidth)));
                                colorRow.value.setBrightness(clamp01(1 - (float) ((mouseY - colorRow.panelY) / colorRow.sbHeight)));
                            }

                            if (pickingHue && draggingColorValue == colorRow.value && colorRow.panelHeight > 0) {
                                colorRow.value.setHue(clamp01((float) ((mouseY - colorRow.panelY) / colorRow.panelHeight)));
                            }

                            if (draggingColor && draggingColorValue == colorRow.value && colorRow.sliderTrackWidth > 0) {
                                float val = clamp01((float) ((mouseX - colorRow.slidersX) / colorRow.sliderTrackWidth));
                                Color c = colorRow.value.get();
                                int rr = c.getRed();
                                int gg = c.getGreen();
                                int bb = c.getBlue();
                                int aa = c.getAlpha();

                                switch (draggingColorComponent) {
                                    case 0 -> rr = (int) (val * 255);
                                    case 1 -> gg = (int) (val * 255);
                                    case 2 -> bb = (int) (val * 255);
                                    case 3 -> aa = (int) (val * 255);
                                }
                                colorRow.value.set(new Color(rr, gg, bb, aa));
                            }

                            hsb[0] = colorRow.value.getHue();
                            hsb[1] = colorRow.value.getSaturation();
                            hsb[2] = colorRow.value.getBrightness();

                            drawRoundedGradientRect3(colorRow.panelX, colorRow.panelY, colorRow.sbWidth, colorRow.sbHeight, 3,
                                    Color.getHSBColor(0, 0, 0),
                                    Color.getHSBColor(0, 0, 1),
                                    Color.getHSBColor(0, 0, 0),
                                    Color.getHSBColor(hsb[0], 1, 1));
                            NanoVGHelper.drawRoundRectOutline(colorRow.panelX, colorRow.panelY, colorRow.sbWidth, colorRow.sbHeight, 3, 0.75f * scale, new Color(0, 0, 0, 120));

                            drawVerticalHueBar(colorRow.hueRect.x, colorRow.hueRect.y, colorRow.hueRect.w, colorRow.hueRect.h, hsb[0], 3);

                            Color c = colorRow.value.get();
                            int[] comps = {c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha()};
                            String[] labels = {"R", "G", "B", "A"};

                            for (int i = 0; i < 4; i++) {
                                Rect sliderRect = colorRow.sliderRects[i];
                                float sy = sliderRect.y;
                                float sw = colorRow.sliderTrackWidth;

                                NanoVGHelper.drawString(labels[i] + ": " + comps[i], colorRow.slidersX, sy + 8, FontLoader.regular(12), 12, new Color(200, 200, 200));

                                float trackY = sy + 14;
                                NanoVGHelper.drawRoundRect(colorRow.slidersX, trackY, sw, 4, 2, new Color(60, 60, 60));
                                NanoVGHelper.drawRoundRect(colorRow.slidersX, trackY, sw * (comps[i] / 255f), 4, 2, ClickGui.mzcThemeColor.get());
                                NanoVGHelper.drawCircle(colorRow.slidersX + sw * (comps[i] / 255f), trackY + 2, 4, Color.WHITE);
                            }

                            float pickerY = colorRow.panelY + (colorRow.sbHeight * (1 - hsb[2]));
                            float pickerX = colorRow.panelX + (colorRow.sbWidth * hsb[1] - 1);
                            pickerY = Math.max(Math.min(colorRow.panelY + colorRow.sbHeight - 2, pickerY), colorRow.panelY - 2);
                            pickerX = Math.max(Math.min(colorRow.panelX + colorRow.sbWidth - 2, pickerX), colorRow.panelX - 2);
                            NanoVGHelper.drawRect(pickerX, pickerY, 2, 2, new Color(255, 255, 255));

                            NanoVGHelper.restore();
                        }
                    }
                }

                NanoVG.nvgResetScissor(vg);
            } else {
                NanoVGHelper.drawCenteredString(TranslationManager.get("ui.select_module_settings", "Select a module to edit settings"), settingsX + settingsWidth / 2, y + height / 2, FontLoader.regular(16), 16, Color.GRAY);
            }
            
            org.lwjgl.nanovg.NanoVG.nvgRestore(vg); // Restore scale
        });
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        int keyCode = keyInput.key();
        int modifiers = keyInput.modifiers();

        // 如果正在绑定按键，优先处理绑定逻辑
        if (binding && selectedModule != null) {
            if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                selectedModule.setKey(-1);
            } else {
                selectedModule.setKey(keyCode);
            }
            binding = false;
            return true; // 已处理，不关闭 GUI
        }

        // 处理搜索模式
        if (searching) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchText.isEmpty()) {
                    searchText = searchText.substring(0, searchText.length() - 1);
                    scrollY = 0;
                }
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ENTER) {
                searching = false;
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                String clipboard = this.client.keyboard.getClipboard();
                if (clipboard != null) {
                    searchText += clipboard;
                    scrollY = 0;
                }
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searching = false;
                searchText = "";
                return true;
            }
            return true;
        }

        // 处理字符串输入
        if (focusedStringValue != null) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                String text = focusedStringValue.getText();
                if (!text.isEmpty()) {
                    focusedStringValue.setText(text.substring(0, text.length() - 1));
                }
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                focusedStringValue = null;
                return true;
            }
            return true;
        }

        // 处理数字输入
        if (focusedNumberValue != null) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!numberValueBuffer.isEmpty()) {
                    numberValueBuffer = numberValueBuffer.substring(0, numberValueBuffer.length() - 1);
                }
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (keyCode == GLFW.GLFW_KEY_ENTER) {
                    try {
                        if (!numberValueBuffer.isEmpty()) {
                            double val = Double.parseDouble(numberValueBuffer);
                            double min = focusedNumberValue.getMin().doubleValue();
                            double max = focusedNumberValue.getMax().doubleValue();
                            
                            if (val < min) val = min;
                            if (val > max) val = max;
                            
                            if (focusedNumberValue.get() instanceof Integer) {
                                ((NumberValue<Integer>) focusedNumberValue).set((int) val);
                            } else if (focusedNumberValue.get() instanceof Float) {
                                float step = ((NumberValue<Float>) focusedNumberValue).getStep().floatValue();
                                if (step > 0) {
                                    val = Math.round(val / step) * step;
                                }
                                ((NumberValue<Float>) focusedNumberValue).set((float) val);
                            } else if (focusedNumberValue.get() instanceof Double) {
                                double step = ((NumberValue<Double>) focusedNumberValue).getStep().doubleValue();
                                if (step > 0) {
                                    val = Math.round(val / step) * step;
                                }
                                ((NumberValue<Double>) focusedNumberValue).set(val);
                            } else if (focusedNumberValue.get() instanceof Long) {
                                ((NumberValue<Long>) focusedNumberValue).set((long) val);
                            }
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
                focusedNumberValue = null;
                numberValueBuffer = "";
                return true;
            }
            return true;
        }

        // 如果没有特殊处理，且是 ESC，关闭 GUI
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
    public boolean keyReleased(KeyInput keyInput) {
        return super.keyReleased(keyInput);
    }
    
    @Override
    public boolean charTyped(CharInput charInput) {
        if (!charInput.isValidChar()) {
            return super.charTyped(charInput);
        }
        char chr = (char) charInput.codepoint();
        if (searching) {
            if (isValidChar(chr)) {
                searchText += chr;
                scrollY = 0;
                return true;
            }
        }
        if (focusedStringValue != null) {
            if (isValidChar(chr)) {
                focusedStringValue.setText(focusedStringValue.getText() + chr);
                return true;
            }
        }
        if (focusedNumberValue != null) {
             if (isValidChar(chr) && (Character.isDigit(chr) || chr == '.' || chr == '-')) {
                 numberValueBuffer += chr;
                 return true;
             }
        }
        return super.charTyped(charInput);
    }

    private boolean isValidChar(char chr) {
        return chr >= 32 && chr != 127;
    }

    @Override
    public boolean mouseClicked(Click click, boolean playSound) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (binding && selectedModule != null) {
            // Handle mouse button binding
            // Button 0: Left, 1: Right, 2: Middle, 3+: Side buttons
            // Convention: key = -100 - button
            selectedModule.setKey(-100 - button);
            binding = false;
            return true;
        }

        // Dragging
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 20) {
            dragging = true;
            dragX = (float) (mouseX - x);
            dragY = (float) (mouseY - y);
            return true;
        }

        if (handleSidebarClick(mouseX, mouseY, button)) return true;
        
        if (handleModuleListClick(mouseX, mouseY, button)) return true;

        if (handleSettingsClick(mouseX, mouseY, button)) return true;
        
        return super.mouseClicked(click, playSound);
    }

    private boolean handleSidebarClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (mouseX < x || mouseX >= x + sidebarWidth) return false;

        float catY = sidebarCategoryStartY();
        for (Category category : Category.values()) {
            if (mouseY >= catY && mouseY <= catY + 25) {
                currentCategory = category;
                scrollY = 0;
                selectedModule = null;
                labelWidthCache.clear();
                settingsAnimation.setDirection(Direction.BACKWARDS);
                binding = false;
                expandedColors.clear();
                expandedEnums.clear();
                focusedStringValue = null;
                focusedNumberValue = null;
                numberValueBuffer = "";

                if (true) {
                    searchText = "";
                    searching = false;
                } else {
                    searching = true;
                }
                return true;
            }
            catY += 35;
        }
        return false;
    }

    private boolean handleModuleListClick(double mouseX, double mouseY, int button) {
        float modListX = x + sidebarWidth;
        if (mouseX < modListX || mouseX >= modListX + moduleListWidth) return false;
        if (mouseY < y || mouseY >= y + height) return false;

        if (false) {
            float searchY = y + 10;
            if (mouseY >= searchY && mouseY <= searchY + 25) {
                if (button == 0) {
                    searching = true;
                    focusedStringValue = null;
                    focusedNumberValue = null;
                    numberValueBuffer = "";
                    binding = false;
                    return true;
                }
                return false;
            }
        }

        List<Module> modules;
        if (false) {
            modules = new ArrayList<>(Sakura.MODULES.getAllModules());
        } else {
            modules = getFilteredModules();
        }

        float listStartY = moduleListStartY();
        float modY = listStartY + scrollY;

        for (Module module : modules) {
            float itemHeight = 30;
            if (false) {
                String name = module.getEnglishName().toLowerCase();
                String cnName = "";
                String search = searchText.toLowerCase();
                boolean visible = name.contains(search) || cnName.contains(search);

                Animation searchAnim = searchVisibilityAnimations.computeIfAbsent(module, m -> new DecelerateAnimation(200, 1));
                searchAnim.setDirection(visible ? Direction.FORWARDS : Direction.BACKWARDS);
                float s = searchAnim.getOutput().floatValue();
                if (s < 0.01f) continue;
                itemHeight = 30 * s;
            }

            if (modY + itemHeight > listStartY && modY < y + height) {
                if (mouseY >= modY && mouseY <= modY + itemHeight) {
                    if (button == 0) {
                        module.toggle();
                        return true;
                    }
                    if (button == 1) {
                        if (selectedModule == module) {
                            selectedModule = null;
                            labelWidthCache.clear();
                            settingsAnimation.setDirection(Direction.BACKWARDS);
                        } else {
                            selectedModule = module;
                            labelWidthCache.clear();
                            settingsAnimation.setDirection(Direction.FORWARDS);
                            settingsScrollY = 0;
                            binding = false;
                            expandedColors.clear();
                            expandedEnums.clear();
                            focusedStringValue = null;
                            focusedNumberValue = null;
                            numberValueBuffer = "";

                            if (selectedModule instanceof Friend f) {
                                f.refreshFriends();
                            } else if (selectedModule instanceof Home h) {
                                h.refreshHomes();
                            }
                        }
                        return true;
                    }
                    return false;
                }
            }
            modY += itemHeight;
        }
        return false;
    }

    private boolean handleSettingsClick(double mouseX, double mouseY, int button) {
        float modListX = x + sidebarWidth;
        float settingsX = modListX + moduleListWidth;
        float settingsWidth = width - sidebarWidth - moduleListWidth;
        if (mouseX < settingsX || mouseX >= x + width) return false;
        if (mouseY < y || mouseY >= y + height) return false;
        if (selectedModule == null) return false;

        SettingsLayout layout = computeSettingsLayout(settingsX, settingsWidth);
        if (layout == null) return false;

        if (layout.bindRect.contains(mouseX, mouseY)) {
            if (button == 0) {
                binding = !binding;
                return true;
            }
            if (button == 1) {
                selectedModule.setKey(-1);
                binding = false;
                return true;
            }
        }

        boolean clickedWidget = false;
        for (SettingsRow row : layout.rows) {
            if (!row.rowRect().contains(mouseX, mouseY) && !(row instanceof EnumRow er && er.dropdownRect != null && er.dropdownRect.contains(mouseX, mouseY)) && !(row instanceof ColorRow cr && cr.expanded && (cr.sbRect.contains(mouseX, mouseY) || cr.hueRect.contains(mouseX, mouseY) || cr.anySliderContains(mouseX, mouseY)))) {
                continue;
            }

            if (row instanceof BoolRow boolRow) {
                if (button == 0 && boolRow.hitRect.contains(mouseX, mouseY)) {
                    boolRow.value.set(!boolRow.value.get());
                    return true;
                }
            } else if (row instanceof StringRow stringRow) {
                if (button == 0 && stringRow.hitRect.contains(mouseX, mouseY)) {
                    focusedStringValue = stringRow.value;
                    focusedNumberValue = null;
                    numberValueBuffer = "";
                    clickedWidget = true;
                    return true;
                }
            } else if (row instanceof NumberRow numberRow) {
                if (button == 0 && numberRow.valueBoxRect.contains(mouseX, mouseY)) {
                    focusedNumberValue = numberRow.value;
                    numberValueBuffer = "";
                    focusedStringValue = null;
                    clickedWidget = true;
                    return true;
                }
                if (button == 0 && numberRow.sliderHitRect.contains(mouseX, mouseY)) {
                    draggingSlider = true;
                    draggingValue = numberRow.value;
                    draggingRangeValue = null;
                    draggingRangeUpperHandle = false;
                    clickedWidget = true;
                    return true;
                }
            } else if (row instanceof RangeRow rangeRow) {
                if (button == 0 && rangeRow.sliderHitRect.contains(mouseX, mouseY)) {
                    draggingSlider = true;
                    draggingValue = null;
                    draggingRangeValue = rangeRow.value;
                    draggingRangeUpperHandle = Math.abs(mouseX - rangeRow.upperHandleX) < Math.abs(mouseX - rangeRow.lowerHandleX);
                    clickedWidget = true;
                    return true;
                }
            } else if (row instanceof EnumRow enumRow) {
                if (enumRow.dropdownRect != null && enumRow.dropdownRect.contains(mouseX, mouseY)) {
                    if (button == 0) {
                        float relY = (float) (mouseY - enumRow.dropdownRect.y);
                        int index = (int) (relY / enumRow.itemHeight);
                        if (index >= 0 && index < enumRow.constants.length) {
                            ((EnumValue) enumRow.value).set(enumRow.constants[index]);
                            expandedEnums.remove(enumRow.value);
                            clickedWidget = true;
                            return true;
                        }
                    } else if (button == 1) {
                        expandedEnums.remove(enumRow.value);
                        clickedWidget = true;
                        return true;
                    }
                }

                if (enumRow.headerRect.contains(mouseX, mouseY)) {
                    if (button == 0) {
                        if (!expandedEnums.contains(enumRow.value)) {
                            enumRow.value.cycle();
                        }
                        clickedWidget = true;
                        return true;
                    }
                    if (button == 1) {
                        if (expandedEnums.contains(enumRow.value)) expandedEnums.remove(enumRow.value);
                        else expandedEnums.add(enumRow.value);
                        clickedWidget = true;
                        return true;
                    }
                }
            } else if (row instanceof ListRow listRow) {
                if (button == 0 && listRow.buttonRect.contains(mouseX, mouseY) && client != null) {
                    client.setScreen(new SelectionScreen(this, listRow.value));
                    clickedWidget = true;
                    return true;
                }
            } else if (row instanceof ColorRow colorRow) {
                if (button == 1 && colorRow.headerRect.contains(mouseX, mouseY)) {
                    if (expandedColors.contains(colorRow.value)) expandedColors.remove(colorRow.value);
                    else expandedColors.add(colorRow.value);
                    clickedWidget = true;
                    return true;
                }

                if (colorRow.expanded) {
                    if (colorRow.sbRect.contains(mouseX, mouseY)) {
                        pickingSB = true;
                        draggingColorValue = colorRow.value;
                        clickedWidget = true;
                        return true;
                    }
                    if (colorRow.hueRect.contains(mouseX, mouseY)) {
                        pickingHue = true;
                        draggingColorValue = colorRow.value;
                        clickedWidget = true;
                        return true;
                    }
                    int idx = colorRow.sliderIndexAt(mouseX, mouseY);
                    if (idx != -1) {
                        draggingColor = true;
                        draggingColorValue = colorRow.value;
                        draggingColorComponent = idx;
                        clickedWidget = true;
                        return true;
                    }
                }
            }
        }

        if (!clickedWidget) {
            focusedStringValue = null;
            focusedNumberValue = null;
        }
        return clickedWidget;
    }

    private SettingsLayout computeSettingsLayout(float settingsX, float settingsWidth) {
        settingsAnimation.setDirection(selectedModule != null ? Direction.FORWARDS : Direction.BACKWARDS);
        float settingsAnimVal = settingsAnimation.getOutput().floatValue();
        if (settingsAnimVal <= 0.01f) return null;
        if (selectedModule == null) return null;

        float drawSettingsX = settingsX + (1 - settingsAnimVal) * 30;
        int settingsAlpha = (int) (ClickGui.mzcBackgroundAlpha.get() * settingsAnimVal);
        if (settingsAlpha > 255) settingsAlpha = 255;
        if (settingsAlpha < 0) settingsAlpha = 0;

        float contentHeight = estimateSettingsContentHeight(settingsWidth);
        float maxSettingsScroll = height - contentHeight;
        if (maxSettingsScroll > 0) maxSettingsScroll = 0;
        if (settingsScrollY < maxSettingsScroll) settingsScrollY = maxSettingsScroll;
        if (settingsScrollY > 0) settingsScrollY = 0;

        float baseY = y + 10 + settingsScrollY;
        Rect bindRect = new Rect(drawSettingsX, baseY + 25, settingsWidth, 20);

        float setY = baseY + 45;
        List<SettingsRow> rows = new ArrayList<>();

        for (Value<?> value : selectedModule.getValues()) {
            if (!value.isAvailable()) continue;

            float rowY = setY;
            float rowHeight = 25;
            Rect rowRect = new Rect(drawSettingsX, rowY, settingsWidth, 25);

            if (value instanceof BoolValue boolValue) {
                Rect hitRect = new Rect(drawSettingsX, rowY, settingsWidth, 20);
                float switchW = 22;
                float switchH = 12;
                float switchX = drawSettingsX + settingsWidth - 15 - switchW;
                float switchY = rowY + 6.5f;
                Rect switchRect = new Rect(switchX, switchY, switchW, switchH);
                rows.add(new BoolRow(boolValue, rowRect, hitRect, switchRect));
            } else if (value instanceof NumberValue<?> numberValue) {
                float rowCenterY = rowY + 12.5f;
                float gap = 10;
                float valueBoxW = 40;
                float valueBoxX = drawSettingsX + settingsWidth - 10 - valueBoxW;
                float valueBoxY = rowCenterY - 7.5f;
                Rect valueBoxRect = new Rect(valueBoxX, valueBoxY, valueBoxW, 15);

                float labelW = labelWidthCached(value, 14);
                float sliderX = drawSettingsX + 10 + labelW + gap;
                float sliderW = valueBoxX - gap - sliderX;
                float sliderY = rowCenterY - 2;
                Rect sliderHitRect = new Rect(sliderX, sliderY - 5, sliderW, 14);
                rows.add(new NumberRow(numberValue, rowRect, valueBoxRect, sliderHitRect, sliderX, sliderW, sliderY));
            } else if (value instanceof RangeValue<?> rangeValue) {
                float rowCenterY = rowY + 12.5f;
                float gap = 10;
                float valueBoxW = 74;
                float valueBoxX = drawSettingsX + settingsWidth - 10 - valueBoxW;

                float labelW = labelWidthCached(value, 14);
                float sliderX = drawSettingsX + 10 + labelW + gap;
                float sliderW = valueBoxX - gap - sliderX;
                float sliderY = rowCenterY - 2;
                Rect sliderHitRect = new Rect(sliderX, sliderY - 5, sliderW, 14);

                double min = rangeValue.getMin().doubleValue();
                double max = rangeValue.getMax().doubleValue();
                double lower = rangeValue.getMinValue().doubleValue();
                double upper = rangeValue.getMaxValue().doubleValue();
                double denom = Math.max(1.0E-9, max - min);
                float lowerHandleX = sliderX + (float) (sliderW * ((lower - min) / denom));
                float upperHandleX = sliderX + (float) (sliderW * ((upper - min) / denom));
                rows.add(new RangeRow(rangeValue, rowRect, sliderHitRect, sliderX, sliderW, sliderY, lowerHandleX, upperHandleX));
            } else if (value instanceof EnumValue<?> enumValue) {
                Rect headerRect = new Rect(drawSettingsX, rowY, settingsWidth, 25);

                Enum<?> currentEnum = (Enum<?>) enumValue.get();
                String valStr = TranslationManager.get(TranslationManager.enumKey(currentEnum), currentEnum.toString());
                float valW = textWidthCached(valStr, 14, false);
                float boxW = Math.max(80, valW + 15);
                float boxH = 18;
                float boxX = drawSettingsX + settingsWidth - 10 - boxW;
                float boxY = rowY + 4;
                Rect boxRect = new Rect(boxX, boxY, boxW, boxH);

                Animation anim = valueAnimations.computeIfAbsent(enumValue, v -> new DecelerateAnimation(200, 1));
                anim.setDirection(expandedEnums.contains(enumValue) ? Direction.FORWARDS : Direction.BACKWARDS);
                float progress = anim.getOutput().floatValue();

                Rect dropdownRect = null;
                Object[] constants = enumValue.get().getClass().getEnumConstants();
                float itemHeight = 25;
                if (progress > 0.01f) {
                    float dropdownH = constants.length * itemHeight * progress;
                    dropdownRect = new Rect(boxX, rowY + 25, boxW, dropdownH);
                    rowHeight = 25 + dropdownH;
                }
                rows.add(new EnumRow(enumValue, rowRect, headerRect, boxRect, dropdownRect, constants, itemHeight, progress));
            } else if (value instanceof ListValue<?> listValue) {
                float btnW = 80;
                float btnH = 18;
                float btnX = drawSettingsX + settingsWidth - 10 - btnW;
                float btnY = rowY + 4;
                Rect buttonRect = new Rect(btnX, btnY, btnW, btnH);
                rows.add(new ListRow(listValue, rowRect, buttonRect));
            } else if (value instanceof StringValue stringValue) {
                float inputW = 100;
                float inputX = drawSettingsX + settingsWidth - inputW - 10;
                float inputY = rowY + 5;
                float inputH = 15;
                Rect hitRect = new Rect(drawSettingsX, rowY, settingsWidth, 20);
                Rect inputRect = new Rect(inputX, inputY, inputW, inputH);
                rows.add(new StringRow(stringValue, rowRect, hitRect, inputRect));
            } else if (value instanceof ColorValue colorValue) {
                Rect headerRect = new Rect(drawSettingsX, rowY, settingsWidth, 20);
                Animation anim = valueAnimations.computeIfAbsent(colorValue, v -> new DecelerateAnimation(200, 1));
                anim.setDirection(expandedColors.contains(colorValue) ? Direction.FORWARDS : Direction.BACKWARDS);
                float progress = anim.getOutput().floatValue();
                boolean expanded = progress > 0.01f;

                Rect sbRect = Rect.EMPTY;
                Rect hueRect = Rect.EMPTY;
                Rect[] sliderRects = Rect.EMPTY_ARRAY;
                float slidersX = 0;
                float sliderTrackWidth = 0;
                float panelX = 0;
                float panelY = 0;
                float sbWidth = 0;
                float sbHeight = 0;
                float panelHeight = 0;

                if (expanded) {
                    float contentX = drawSettingsX + 20;
                    float contentW = settingsWidth - 40;
                    float hueBarWidth = 10;
                    float gap = 5;
                    float leftWidth = (contentW - gap) / 2;
                    float rightWidth = contentW - leftWidth - gap;
                    sbWidth = leftWidth - hueBarWidth - gap;
                    sbHeight = sbWidth;
                    panelHeight = sbHeight;

                    panelX = contentX;
                    panelY = rowY + 25;
                    float hueX = panelX + sbWidth + gap;
                    float hueY = panelY;
                    slidersX = contentX + leftWidth + gap;
                    sliderTrackWidth = rightWidth - 6;
                    float sliderH = (panelHeight - (3 * gap)) / 4;

                    float totalHeight = panelHeight + 10;
                    float currentHeight = totalHeight * progress;
                    rowHeight = 25 + currentHeight;

                    sbRect = new Rect(panelX, panelY, sbWidth, sbHeight);
                    hueRect = new Rect(hueX, hueY, hueBarWidth, panelHeight);
                    sliderRects = new Rect[4];
                    for (int i = 0; i < 4; i++) {
                        float sy = panelY + i * (sliderH + gap);
                        sliderRects[i] = new Rect(slidersX, sy, rightWidth, sliderH);
                    }
                }

                rows.add(new ColorRow(colorValue, rowRect, headerRect, expanded, progress, sbRect, hueRect, sliderRects, slidersX, sliderTrackWidth, panelX, panelY, sbWidth, sbHeight, panelHeight));
            } else {
                rows.add(new UnknownRow(value, rowRect));
            }

            setY += rowHeight;
        }

        return new SettingsLayout(settingsX, settingsWidth, drawSettingsX, settingsAlpha, settingsAnimVal, bindRect, rows, contentHeight);
    }

    private float estimateSettingsContentHeight(float settingsWidth) {
        float h = 10 + 45;
        if (selectedModule == null) return h;
        for (Value<?> value : selectedModule.getValues()) {
            if (!value.isAvailable()) continue;
            if (value instanceof EnumValue<?> enumValue) {
                Animation anim = valueAnimations.computeIfAbsent(enumValue, v -> new DecelerateAnimation(200, 1));
                anim.setDirection(expandedEnums.contains(enumValue) ? Direction.FORWARDS : Direction.BACKWARDS);
                float progress = anim.getOutput().floatValue();
                Object[] constants = enumValue.get().getClass().getEnumConstants();
                h += 25 + (constants.length * 25f) * progress;
                continue;
            }
            if (value instanceof ColorValue colorValue) {
                Animation anim = valueAnimations.computeIfAbsent(colorValue, v -> new DecelerateAnimation(200, 1));
                anim.setDirection(expandedColors.contains(colorValue) ? Direction.FORWARDS : Direction.BACKWARDS);
                float progress = anim.getOutput().floatValue();

                float contentW = settingsWidth - 40;
                float hueBarWidth = 10;
                float gap = 5;
                float leftWidth = (contentW - gap) / 2;
                float sbWidth = leftWidth - hueBarWidth - gap;
                float panelHeight = sbWidth;
                float totalHeight = panelHeight + 10;
                float currentHeight = totalHeight * progress;
                h += 25 + currentHeight;
                continue;
            }
            h += 25;
        }
        return h;
    }

    private record SettingsLayout(float settingsX, float settingsWidth, float drawSettingsX, int alpha, float animVal, Rect bindRect, List<SettingsRow> rows, float contentHeight) {
    }

    private sealed interface SettingsRow permits BoolRow, NumberRow, RangeRow, EnumRow, ListRow, StringRow, ColorRow, UnknownRow {
        Rect rowRect();
    }

    private record BoolRow(BoolValue value, Rect rowRect, Rect hitRect, Rect switchRect) implements SettingsRow {
    }

    private record NumberRow(NumberValue<?> value, Rect rowRect, Rect valueBoxRect, Rect sliderHitRect, float sliderX, float sliderW, float sliderY) implements SettingsRow {
    }

    private record RangeRow(RangeValue<?> value, Rect rowRect, Rect sliderHitRect, float sliderX, float sliderW, float sliderY, float lowerHandleX, float upperHandleX) implements SettingsRow {
    }

    private record EnumRow(EnumValue<?> value, Rect rowRect, Rect headerRect, Rect boxRect, Rect dropdownRect, Object[] constants, float itemHeight, float progress) implements SettingsRow {
    }

    private record ListRow(ListValue<?> value, Rect rowRect, Rect buttonRect) implements SettingsRow {
    }

    private record StringRow(StringValue value, Rect rowRect, Rect hitRect, Rect inputRect) implements SettingsRow {
    }

    private record ColorRow(ColorValue value, Rect rowRect, Rect headerRect, boolean expanded, float progress, Rect sbRect, Rect hueRect, Rect[] sliderRects, float slidersX, float sliderTrackWidth, float panelX, float panelY, float sbWidth, float sbHeight, float panelHeight) implements SettingsRow {
        boolean anySliderContains(double x, double y) {
            for (Rect r : sliderRects) {
                if (r.contains(x, y)) return true;
            }
            return false;
        }

        int sliderIndexAt(double x, double y) {
            for (int i = 0; i < sliderRects.length; i++) {
                if (sliderRects[i].contains(x, y)) return i;
            }
            return -1;
        }
    }

    private record UnknownRow(Value<?> value, Rect rowRect) implements SettingsRow {
    }

    private record Rect(float x, float y, float w, float h) {
        static final Rect EMPTY = new Rect(0, 0, 0, 0);
        static final Rect[] EMPTY_ARRAY = new Rect[0];

        boolean contains(double px, double py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
    }
    
    @Override
    public boolean mouseReleased(Click click) {
        dragging = false;
        draggingSlider = false;
        draggingValue = null;
        draggingRangeValue = null;
        draggingRangeUpperHandle = false;
        draggingColor = false;
        draggingColorValue = null;
        pickingSB = false;
        pickingHue = false;
        return super.mouseReleased(click);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Determine where we are scrolling
        if (mouseX >= x + sidebarWidth && mouseX < x + sidebarWidth + moduleListWidth) {
            scrollY += verticalAmount * 10;
        } else if (mouseX >= x + sidebarWidth + moduleListWidth) {
            settingsScrollY += verticalAmount * 10;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void close() {
        closing = true;
    }
    
    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    public boolean isBinding() {
        return binding;
    }

    private float clamp01(float v) {
        if (Float.isNaN(v)) return 0.0f;
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    private void drawRightAngleTriangle(float centerX, float centerY, float size, boolean pointRight, Color color) {
        long vg = NanoVGRenderer.INSTANCE.getContext();
        float half = size * 0.5f;
        float left = centerX - half;
        float right = centerX + half;
        float top = centerY - half;
        float bottom = centerY + half;
        float midY = centerY;
        float[] p1;
        float[] p2;
        float[] p3;
        NanoVG.nvgBeginPath(vg);
        if (pointRight) {
            p1 = new float[]{left, top};
            p2 = new float[]{left, bottom};
            p3 = new float[]{right, midY};
        } else {
            p1 = new float[]{right, top};
            p2 = new float[]{right, bottom};
            p3 = new float[]{left, midY};
        }
        NanoVG.nvgMoveTo(vg, p1[0], p1[1]);
        NanoVG.nvgLineTo(vg, p2[0], p2[1]);
        NanoVG.nvgLineTo(vg, p3[0], p3[1]);
        NanoVG.nvgClosePath(vg);
        NanoVG.nvgFillColor(vg, NanoVGHelper.nvgColor(color));
        NanoVG.nvgFill(vg);

        NanoVG.nvgLineJoin(vg, NanoVG.NVG_ROUND);
        NanoVG.nvgStrokeWidth(vg, Math.max(1.2f * scale, size * 0.12f));
        NanoVG.nvgStrokeColor(vg, NanoVGHelper.nvgColor(color));
        NanoVG.nvgStroke(vg);
    }

    @SuppressWarnings("unchecked")
    private void applyRangeDrag(RangeValue<?> rangeValue, double rawValue, boolean upperHandle) {
        if (rangeValue.getMinValue() instanceof Integer) {
            int value = (int) Math.round(rawValue);
            if (upperHandle) {
                ((RangeValue<Integer>) rangeValue).setMaxValue(value);
            } else {
                ((RangeValue<Integer>) rangeValue).setMinValue(value);
            }
            return;
        }

        if (rangeValue.getMinValue() instanceof Float) {
            float value = (float) rawValue;
            float step = ((RangeValue<Float>) rangeValue).getStep().floatValue();
            if (step > 0) {
                value = Math.round(value / step) * step;
            }
            if (upperHandle) {
                ((RangeValue<Float>) rangeValue).setMaxValue(value);
            } else {
                ((RangeValue<Float>) rangeValue).setMinValue(value);
            }
            return;
        }

        if (rangeValue.getMinValue() instanceof Long) {
            long value = Math.round(rawValue);
            if (upperHandle) {
                ((RangeValue<Long>) rangeValue).setMaxValue(value);
            } else {
                ((RangeValue<Long>) rangeValue).setMinValue(value);
            }
            return;
        }

        double value = rawValue;
        double step = ((RangeValue<Double>) rangeValue).getStep().doubleValue();
        if (step > 0) {
            value = Math.round(value / step) * step;
        }
        if (upperHandle) {
            ((RangeValue<Double>) rangeValue).setMaxValue(value);
        } else {
            ((RangeValue<Double>) rangeValue).setMinValue(value);
        }
    }

    private List<Module> getFilteredModules() {
        if (true) {
             return Sakura.MODULES.getModsByCategory(currentCategory).stream()
                 .filter(m -> dev.mzc.client.auth.AuthManager.getRole().isAtLeast(m.getRequiredRole()))
                 .toList();
        }
        if (searchText.isEmpty()) {
            return List.of(); 
        }
        String lower = searchText.toLowerCase();
        return Sakura.MODULES.getAllModules().stream()
                .filter(m -> dev.mzc.client.auth.AuthManager.getRole().isAtLeast(m.getRequiredRole()))
                .filter(m -> m.getDisplayName().toLowerCase().contains(lower))
                .toList();
    }

    private void drawVerticalHueBar(float x, float y, float width, float height, float hue, float radius) {
        NanoVGHelper.drawRoundRect(x, y, width, height, radius, new Color(25, 25, 25));
        float inset = 1.0f * scale;
        float innerX = x + inset;
        float innerY = y + inset;
        float innerW = Math.max(0.0f, width - inset * 2);
        float innerH = Math.max(0.0f, height - inset * 2);

        if (innerW > 0.0f && innerH > 0.0f) {
            int segments = Math.max(64, Math.round(96.0f * scale));
            float segH = innerH / segments;
            float innerRadius = Math.max(0.0f, radius - inset);
            long vg = NanoVGRenderer.INSTANCE.getContext();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                NVGPaint paint = NVGPaint.malloc(stack);
                NVGColor c1 = NVGColor.malloc(stack);
                NVGColor c2 = NVGColor.malloc(stack);

                for (int i = 0; i < segments; i++) {
                    float h1 = i / (float) segments;
                    float h2 = (i + 1) / (float) segments;

                    Color col1 = Color.getHSBColor(h1, 1f, 1f);
                    Color col2 = Color.getHSBColor(h2, 1f, 1f);
                    NanoVG.nvgRGBA((byte) col1.getRed(), (byte) col1.getGreen(), (byte) col1.getBlue(), (byte) 255, c1);
                    NanoVG.nvgRGBA((byte) col2.getRed(), (byte) col2.getGreen(), (byte) col2.getBlue(), (byte) 255, c2);

                    float y0 = innerY + i * segH;
                    float y1 = (i == segments - 1) ? (innerY + innerH) : (y0 + segH);
                    float hSeg = (y1 - y0) + 0.75f * scale;

                    NanoVG.nvgLinearGradient(vg, innerX, y0, innerX, y0 + hSeg, c1, c2, paint);
                    NanoVG.nvgBeginPath(vg);

                    if (innerRadius > 0.0f) {
                        if (i == 0) {
                            NanoVG.nvgRoundedRectVarying(vg, innerX, y0, innerW, hSeg, innerRadius, innerRadius, 0.0f, 0.0f);
                        } else if (i == segments - 1) {
                            NanoVG.nvgRoundedRectVarying(vg, innerX, y0, innerW, hSeg, 0.0f, 0.0f, innerRadius, innerRadius);
                        } else {
                            NanoVG.nvgRect(vg, innerX, y0 - 0.5f, innerW, hSeg + 1.0f);
                        }
                    } else {
                        NanoVG.nvgRect(vg, innerX, y0 - 0.5f, innerW, hSeg + 1.0f);
                    }

                    NanoVG.nvgFillPaint(vg, paint);
                    NanoVG.nvgFill(vg);
                }
            }
        }
        NanoVGHelper.drawRoundRectOutline(x, y, width, height, radius, 0.75f * scale, new Color(0, 0, 0, 120));

        float handleY = y + hue * height;
        handleY = Math.max(y + 1, Math.min(y + height - 1, handleY));
        NanoVGHelper.drawRect(x - 1 * scale, handleY - 0.5f * scale, width + 2 * scale, 1.25f * scale, Color.WHITE);
        NanoVGHelper.drawRect(x - 1 * scale, handleY + 0.75f * scale, width + 2 * scale, 0.75f * scale, new Color(0, 0, 0, 100));
    }

    private void drawRoundedGradientRect3(float x, float y, float w, float h, float radius, Color bottomLeft, Color topLeft, Color bottomRight, Color topRight) {
        float r = Math.max(0.0f, Math.min(radius, Math.min(w, h) / 2f));
        if (w <= 0.0f || h <= 0.0f) return;

        int strips = Math.max(64, Math.round(80.0f * scale));
        float stripH = h / strips;
        long vg = NanoVGRenderer.INSTANCE.getContext();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            NVGPaint paint = NVGPaint.malloc(stack);
            NVGColor c1 = NVGColor.malloc(stack);
            NVGColor c2 = NVGColor.malloc(stack);

            for (int i = 0; i < strips; i++) {
                float y0 = y + i * stripH;
                float y1 = (i == strips - 1) ? (y + h) : (y0 + stripH);
                float yMid = (y0 + y1) * 0.5f;
                float t = (yMid - y) / h;

                float inset = roundedInsetAtY(yMid - y, h, r);
                float x0 = x + inset;
                float w0 = w - inset * 2f;
                if (w0 <= 0.0f) continue;

                Color leftColor = lerpColor(topLeft, bottomLeft, t);
                Color rightColor = lerpColor(topRight, bottomRight, t);

                NanoVG.nvgRGBA((byte) leftColor.getRed(), (byte) leftColor.getGreen(), (byte) leftColor.getBlue(), (byte) leftColor.getAlpha(), c1);
                NanoVG.nvgRGBA((byte) rightColor.getRed(), (byte) rightColor.getGreen(), (byte) rightColor.getBlue(), (byte) rightColor.getAlpha(), c2);

                float segH = (y1 - y0) + 1.0f;
                NanoVG.nvgLinearGradient(vg, x0, y0, x0 + w0, y0, c1, c2, paint);
                NanoVG.nvgBeginPath(vg);
                NanoVG.nvgRect(vg, x0, y0 - 0.5f, w0, segH + 1.0f);
                NanoVG.nvgFillPaint(vg, paint);
                NanoVG.nvgFill(vg);
            }
        }
    }

    private float roundedInsetAtY(float yFromTop, float height, float radius) {
        if (radius <= 0.0f) return 0.0f;
        float dyTop = Math.max(0.0f, Math.min(radius, yFromTop));
        float dyBottom = Math.max(0.0f, Math.min(radius, height - yFromTop));
        float dy = Math.min(dyTop, dyBottom);
        if (dy >= radius) return 0.0f;
        float v = radius - dy;
        float inside = Math.max(0.0f, radius * radius - v * v);
        return radius - (float) Math.sqrt(inside);
    }

    private Color lerpColor(Color a, Color b, float t) {
        float tt = Math.max(0.0f, Math.min(1.0f, t));
        int r = Math.round(a.getRed() + (b.getRed() - a.getRed()) * tt);
        int g = Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * tt);
        int bl = Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * tt);
        int al = Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * tt);
        return new Color(r, g, bl, al);
    }

    private float textWidthCached(String text, int size, boolean bold) {
        if (text == null || text.isEmpty()) return 0.0f;
        TextWidthKey key = new TextWidthKey(text, size, bold);
        Float cached = textWidthCache.get(key);
        if (cached != null) return cached;
        float width = NanoVGHelper.getTextWidth(text, bold ? FontLoader.bold(size) : FontLoader.regular(size), size);
        textWidthCache.put(key, width);
        return width;
    }

    private float labelWidthCached(Value<?> value, int size) {
        Float cached = labelWidthCache.get(value);
        if (cached != null) return cached;
        float width = NanoVGHelper.getTextWidth(value.getDisplayName(), FontLoader.regular(size), size);
        labelWidthCache.put(value, width);
        return width;
    }

    private float sidebarCategoryStartY() {
        if (iconImageId != -1) {
            float iconH = 30;
            float iconY = y + 10;
            return iconY + iconH + 10;
        }
        return y + 20;
    }

    private float moduleListStartY() {
        float listStartY = y + 10;
        if (false) {
            float searchAnimVal = searchAnimation.getOutput().floatValue();
            listStartY += 35 * searchAnimVal;
        }
        return listStartY;
    }

    private record TextWidthKey(String text, int size, boolean bold) {
    }
}


