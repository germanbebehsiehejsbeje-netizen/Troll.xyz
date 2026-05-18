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
        Exalted("Exalted"),
        Spirt("Spirt"),
        Season("Season");

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
        } else if (style.is(Style.Spirt)) {
            renderSpirt(active, s, inEditor);
        } else if (style.is(Style.Season)) {
            renderSeason(active, s, inEditor);
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

    private void renderSpirt(List<Module> active, float s, boolean inEditor) {
        // Вспомогательный класс для хранения строк биндов
        class BindItem {
            String name, value;
            public BindItem(String name, String value) {
                this.name = name;
                this.value = value;
            }
        }

        // Заполняем массив активных функций
        List<BindItem> activeBinds = new ArrayList<>();
        for (Module m : active) {
            activeBinds.add(new BindItem(m.getDisplayName(), getKeyName(m.getKey())));
        }

        // Если биндов нет, панель скрывается (или оставь только шапку)
        if (activeBinds.isEmpty() && !inEditor) return;

        Color bg = new Color(22, 19, 41, 240); // Фон SpirtHack
        Color accent = new Color(110, 85, 235); // Сиреневый цвет элементов
        Color lineColors = new Color(29, 25, 54, 150); // Тонкие линии-разделители
        Color textWhite = new Color(220, 220, 225);

        float pad = 10f * s;
        float rowHeight = 22f * s;
        float titleHeight = 26f * s;
        float radius = 5f * s;

        this.width = 150f * s; // Фиксированная ширина как на скрине
        this.height = titleHeight + (activeBinds.size() * rowHeight) + 4f * s;

        NanoVGRenderer.INSTANCE.draw(vg -> {
            // Рисуем закругленный фон всей панели
            NanoVGHelper.drawRoundRect(x, y, width, height, radius, bg);

            int font = FontLoader.regular((int)(13f * s));

            // 1. Отрисовка шапки "» Hotkeys"
            NanoVGHelper.drawString("»", x + pad, y + titleHeight / 2f, font, 13f * s, 
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, accent);
            
            NanoVGHelper.drawString("Keybinds", x + pad + 14f * s, y + titleHeight / 2f, font, 13f * s, 
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, textWhite);

            // Линия под шапкой
            NanoVGHelper.drawRect(x, y + titleHeight, width, 1f * s, lineColors);

            // 2. Отрисовка строк с биндами
            float currentY = y + titleHeight;
            for (int i = 0; i < activeBinds.size(); i++) {
                BindItem item = activeBinds.get(i);

                // Название функции (слева)
                NanoVGHelper.drawString(item.name, x + pad, currentY + rowHeight / 2f, font, 12f * s, 
                        NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, textWhite);

                // Значение/Статус (справа, фиолетовое)
                NanoVGHelper.drawString(item.value, x + width - pad, currentY + rowHeight / 2f, font, 12f * s, 
                        NanoVG.NVG_ALIGN_RIGHT | NanoVG.NVG_ALIGN_MIDDLE, accent);

                // Рисуем разделяющую линию для всех строк, кроме последней
                if (i < activeBinds.size() - 1) {
                    NanoVGHelper.drawRect(x, currentY + rowHeight, width, 1f * s, lineColors);
                }

                currentY += rowHeight;
            }
        });
    }

    private void renderSeason(List<Module> active, float s, boolean inEditor) {
        // Вспомогательный класс для хранения строк биндов
        class Bind {
            String name, key;
            public Bind(String name, String key) { 
                this.name = name; 
                this.key = key; 
            }
        }

        // Заполняем массив активных функций
        List<Bind> binds = new ArrayList<>();
        for (Module m : active) {
            binds.add(new Bind(m.getDisplayName(), getKeyName(m.getKey())));
        }

        if (binds.isEmpty() && !inEditor) return;

        Color colorHeader = new Color(255, 255, 255, 255);   // Чисто белая шапка
        Color colorBody = new Color(175, 175, 175, 220);    // Серая подложка
        Color textDark = new Color(25, 25, 25);
        Color textGray = new Color(70, 70, 70);

        float radius = 10f * s; // Радиус скругления со скрина
        float headerH = 26f * s;
        float rowH = 20f * s;
        
        this.width = 145f * s;
        this.height = headerH + (binds.size() * rowH) + 6f * s;

        NanoVGRenderer.INSTANCE.draw(vg -> {
            // Тень (Мягкий черный прямоугольник сзади с большим радиусом)
            NanoVGHelper.drawRoundRect(x, y + 2f * s, width, height, radius, new Color(0, 0, 0, 35));

            // 1. Нижний серый фон тела
            NanoVGHelper.drawRoundRect(x, y, width, height, radius, colorBody);
            // 2. Верхняя белая шапка (перекрывает верх серой структуры)
            NanoVGHelper.drawRoundRect(x, y, width, headerH + 4f * s, radius, colorHeader);
            // Прямоугольная заплатка, чтобы скрыть скругления шапки снизу
            NanoVGHelper.drawRect(x, y + headerH - 1f * s, width, 5f * s, colorHeader);

            int font = FontLoader.regular((int)(13f * s));

            // Иконка клавиатуры (символ ⌨) и заголовок
            NanoVGHelper.drawString("⌨", x + 10f * s, y + headerH / 2f, font, 14f * s, 
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, textDark);
            NanoVGHelper.drawString("Keybinds", x + 28f * s, y + headerH / 2f, font, 12.5f * s, 
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, textDark);

            // 3. Отрисовка самих биндов
            float currentY = y + headerH + 3f * s;
            for (Bind b : binds) {
                // Имя модуля (Темно-серый)
                NanoVGHelper.drawString(b.name, x + 12f * s, currentY + rowH / 2f, font, 12f * s, 
                        NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, textGray);
                // Кнопка активации (Почти черная)
                NanoVGHelper.drawString(b.key, x + width - 12f * s, currentY + rowH / 2f, font, 12f * s, 
                        NanoVG.NVG_ALIGN_RIGHT | NanoVG.NVG_ALIGN_MIDDLE, textDark);
                currentY += rowH;
            }
        });
    }
}