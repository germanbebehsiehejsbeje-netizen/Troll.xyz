package dev.mzc.client.module.impl.hud;

import dev.mzc.client.Sakura;
import dev.mzc.client.module.HudModule;
import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.nanovg.NanoVGRenderer;
import dev.mzc.client.nanovg.font.FontLoader;
import dev.mzc.client.nanovg.util.NanoVGHelper;
import dev.mzc.client.utils.math.FrameRateCounter;
import dev.mzc.client.values.Value;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.nanovg.NanoVG;

import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class WatermarkHud extends HudModule {
    public enum Style { 
        Gamesense("Gamesense"),
<<<<<<< HEAD
        Exalted("Exalted"),
        Spirt("Spirt"),
        Season("Season"),
        TROLLHACK("Trollhack"),
        Compact("Compact");
=======
        Exalted("Exalted");
>>>>>>> parent of 584bcf3 (update fixed movecorection and elytra rezolver)

        private final String name;

        Style(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private final EnumValue<Style> style = new EnumValue<>("Style", Style.Exalted);
    private final NumberValue<Double> hudScale = new NumberValue<>("Scale", 1.0, 0.5, 2.0, 0.1);
    private final BoolValue showCoords = new BoolValue("Coords", true);
    private final BoolValue showPing = new BoolValue("Ping", true);
    private final BoolValue showTime = new BoolValue("Time", true);

    // Icon font for Compact style
    private int iconFontId = -1;

    private int getCompactIconFont(float size) {
        return FontLoader.badcache((int) size);
    }

    public WatermarkHud() {
        super("Watermark", 10, 10);
    }

    @Override
    public void onRender(DrawContext context) {
        float s = hudScale.get().floatValue();

        if (style.is(Style.Exalted)) {
            renderExalted(s);
<<<<<<< HEAD
        } else if (style.is(Style.Spirt)) {
            renderSpirt(s);
        } else if (style.is(Style.Season)) {
            renderSeason(s);
        } else if (style.is(Style.TROLLHACK)) {
            renderTrollhack(s);
        } else if (style.is(Style.Compact)) {
            renderCompact(s);
=======
>>>>>>> parent of 584bcf3 (update fixed movecorection and elytra rezolver)
        } else {
            renderGamesense(s);
        }
    }

    private void renderGamesense(float s) {
        // Формируем строку как на скрине
        String fps = FrameRateCounter.INSTANCE.getFps() + " fps";
        String ping = getCurrentPing() + "ms";
        String text = Sakura.MOD_NAME.toLowerCase() + " | " + "github.com/mzc" + " | " + fps + " | " + ping;

        float fontSize = 13f * s;
        int font = FontLoader.regular((int) fontSize);
        float textW = NanoVGHelper.getTextWidth(text, font, fontSize);
        float textH = NanoVGHelper.getFontHeight(font, fontSize);

        float paddingX = 8f * s;
        float paddingY = 6f * s;

        this.width = textW + paddingX * 2;
        this.height = textH + paddingY * 2 + 2 * s; // +2 для полоски

        NanoVGRenderer.INSTANCE.draw(vg -> {
            // Фон (Черный прямоугольник с обводкой)
            NanoVGHelper.drawRect(x, y, width, height, new Color(15, 15, 15, 255));
            NanoVGHelper.drawRectOutline(x, y, width, height, 1f * s, new Color(40, 40, 40, 255));

            // Радужная полоска сверху
            NanoVGHelper.drawGradientRect(x + 1 * s, y + 1 * s, width - 2 * s, 1.5f * s, ClickGui.color(0), ClickGui.color2(0));

            // Текст
            NanoVGHelper.drawString(text, x + paddingX, y + paddingY + textH / 2f + 1 * s, font, fontSize,
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, Color.WHITE);
        });
    }

    private void renderExalted(float s) {
        float fontSize = 15f * s;
        int fontIcon = FontLoader.regular((int) (16f * s));
        int fontText = FontLoader.regular((int) fontSize);

        // Gather data
        String clientName = Sakura.MOD_NAME;
        String playerName = mc.player != null ? mc.player.getName().getString() : "Unknown";
        String fps = mc.getCurrentFps() + "fps";
        
        String serverText;
        if (mc.getCurrentServerEntry() != null) {
            serverText = mc.getCurrentServerEntry().address;
        } else {
            serverText = "Single";
        }
        
        String coords = "Coords: " + (mc.player != null ? mc.player.getBlockX() + ", " + mc.player.getBlockY() + ", " + mc.player.getBlockZ() : "0, 0, 0");
        int ping = getCurrentPing();
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        // Calculate widths for top row
        float clientWidth = 5 * s + NanoVGHelper.getTextWidth("A", fontIcon, 16f * s) + 5 + NanoVGHelper.getTextWidth(clientName, fontText, fontSize) + 5;
        float playerWidth = 5 * s + NanoVGHelper.getTextWidth("G", fontIcon, 16f * s) + 5 + NanoVGHelper.getTextWidth(playerName, fontText, fontSize) + 3;
        float fpsWidth = 5 * s + NanoVGHelper.getTextWidth("F", fontIcon, 16f * s) + 5 + NanoVGHelper.getTextWidth(fps, fontText, fontSize) + 5;
        float serverWidth = 5 * s + NanoVGHelper.getTextWidth("L", fontIcon, 16f * s) + 5 + NanoVGHelper.getTextWidth(serverText, fontText, fontSize) + 5;
        float timeWidth = 5 * s + NanoVGHelper.getTextWidth("M", fontIcon, 16f * s) + 5 + NanoVGHelper.getTextWidth(time, fontText, fontSize) + 5;

        float topRowWidth = clientWidth + 2 + playerWidth + 2 + fpsWidth + 2 + serverWidth + 2 + timeWidth;
        
        // Calculate widths for bottom row
        float coordsWidth = showCoords.get() ? (5 * s + NanoVGHelper.getTextWidth("C", fontIcon, 16f * s) + 5 + NanoVGHelper.getTextWidth(coords, fontText, fontSize) + 5) : 0;
        float pingWidth = showPing.get() ? (5 * s + NanoVGHelper.getTextWidth("E", fontIcon, 16f * s) + 5 + NanoVGHelper.getTextWidth("Ping: " + ping, fontText, fontSize) + 5) : 0;
        float ticksWidth = 5 * s + NanoVGHelper.getTextWidth("N", fontIcon, 16f * s) + 5 + NanoVGHelper.getTextWidth("Ticks: 20", fontText, fontSize) + 5;
        float bpsWidth = 5 * s + NanoVGHelper.getTextWidth("D", fontIcon, 16f * s) + 5 + NanoVGHelper.getTextWidth("Bps: 0", fontText, fontSize) + 5;
        
        float bottomRowWidth = coordsWidth + (showCoords.get() && showPing.get() ? 2 : 0) + pingWidth + (showPing.get() ? 2 : 0) + ticksWidth + 2 + bpsWidth;

        width = (int) Math.max(topRowWidth, bottomRowWidth);
        height = (int) (32 * s);

        NanoVGRenderer.INSTANCE.draw(vg -> {
            // TOP ROW
            float currentX = x;
            float rowY = y;
            float rectHeight = 15 * s;

            // Client name box
            NanoVGHelper.drawRoundRect(currentX, rowY, clientWidth, rectHeight, 5 * s, new Color(0, 0, 0, 150));
            NanoVGHelper.drawString("A", currentX + 5.5f * s, rowY + 6.5f * s, fontIcon, 16f * s, 
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_TOP, ClickGui.color(0));
            NanoVGHelper.drawString(clientName, currentX + 5 * s + NanoVGHelper.getTextWidth("A", fontIcon, 16f * s) + 5, 
                    rowY + 5.1f * s, fontText, fontSize, NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_TOP, ClickGui.color(0));
            currentX += clientWidth + 2;

            // Player name box
            NanoVGHelper.drawRoundRect(currentX, rowY, playerWidth, rectHeight, 5 * s, new Color(0, 0, 0, 150));
            NanoVGHelper.drawString("G", currentX + 5 * s, rowY + 6.5f * s, fontIcon, 15f * s, 
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_TOP, ClickGui.color(0));
            NanoVGHelper.drawString(playerName, currentX + 5 * s + NanoVGHelper.getTextWidth("G", fontIcon, 15f * s) + 3, 
                    rowY + 5.1f * s, fontText, fontSize, NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_TOP, new Color(255, 255, 255, 255));
            currentX += playerWidth + 2;

            // FPS box
            NanoVGHelper.drawRoundRect(currentX, rowY, fpsWidth, rectHeight, 5 * s, new Color(0, 0, 0, 150));
            NanoVGHelper.drawString("F", currentX + 5 * s, rowY + 6.5f * s, fontIcon, 15f * s, 
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_TOP, ClickGui.color(0));
            NanoVGHelper.drawString(fps, currentX + 5 * s + NanoVGHelper.getTextWidth("F", fontIcon, 15f * s) + 4, 
                    rowY + 5.1f * s, fontText, fontSize, NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_TOP, new Color(255, 255, 255, 255));
            currentX += fpsWidth + 2;

            // Server box
            NanoVGHelper.drawRoundRect(currentX, rowY, serverWidth, rectHeight, 5 * s, new Color(0, 0, 0, 150));
            NanoVGHelper.drawString("L", currentX + 5 * s, rowY + 6.5f * s, fontIcon, 15f * s, 
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_TOP, ClickGui.color(0));
            NanoVGHelper.drawString(serverText, currentX + 5 * s + NanoVGHelper.getTextWidth("L", fontIcon, 15f * s) + 4, 
                    rowY + 5.1f * s, fontText, fontSize, NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_TOP, new Color(255, 255, 255, 255));
            currentX += serverWidth + 2;

            // Time box
            NanoVGHelper.drawRoundRect(currentX, rowY, timeWidth, rectHeight, 5 * s, new Color(0, 0, 0, 150));
            NanoVGHelper.drawString("M", currentX + 5 * s, rowY + 6.5f * s, fontIcon, 15f * s, 
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_TOP, ClickGui.color(0));
            NanoVGHelper.drawString(time, currentX + 5 * s + NanoVGHelper.getTextWidth("M", fontIcon, 15f * s) + 4, 
                    rowY + 5.1f * s, fontText, fontSize, NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_TOP, new Color(255, 255, 255, 255));

            // BOTTOM ROW
            currentX = x;
            rowY = y + 17 * s;

            // Coords box
            if (showCoords.get()) {
                NanoVGHelper.drawRoundRect(currentX, rowY, coordsWidth, rectHeight, 5 * s, new Color(0, 0, 0, 150));
                NanoVGHelper.drawString("C", currentX + 5 * s, rowY + 6.5f * s, fontIcon, 15f * s, 
                        NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_TOP, ClickGui.color(0));
                NanoVGHelper.drawString(coords, currentX + 5 * s + NanoVGHelper.getTextWidth("C", fontIcon, 15f * s) + 4, 
                        rowY + 5.1f * s, fontText, fontSize, NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_TOP, new Color(255, 255, 255, 255));
                currentX += coordsWidth + 2;
            }

            // Ping box
            if (showPing.get()) {
                NanoVGHelper.drawRoundRect(currentX, rowY, pingWidth, rectHeight, 5 * s, new Color(0, 0, 0, 150));
                NanoVGHelper.drawString("E", currentX + 5 * s, rowY + 6.5f * s, fontIcon, 15f * s, 
                        NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_TOP, ClickGui.color(0));
                NanoVGHelper.drawString("Ping: " + ping, currentX + 5 * s + NanoVGHelper.getTextWidth("E", fontIcon, 15f * s) + 4, 
                        rowY + 5.1f * s, fontText, fontSize, NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_TOP, new Color(255, 255, 255, 255));
                currentX += pingWidth + 2;
            }

            // Ticks box
            NanoVGHelper.drawRoundRect(currentX, rowY, ticksWidth, rectHeight, 5 * s, new Color(0, 0, 0, 150));
            NanoVGHelper.drawString("N", currentX + 5 * s, rowY + 6.5f * s, fontIcon, 15f * s, 
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_TOP, ClickGui.color(0));
            NanoVGHelper.drawString("Ticks: 20", currentX + 5 * s + NanoVGHelper.getTextWidth("N", fontIcon, 15f * s) + 4, 
                    rowY + 5.1f * s, fontText, fontSize, NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_TOP, new Color(255, 255, 255, 255));
            currentX += ticksWidth + 2;

            // Bps box
            NanoVGHelper.drawRoundRect(currentX, rowY, bpsWidth, rectHeight, 5 * s, new Color(0, 0, 0, 150));
            NanoVGHelper.drawString("D", currentX + 5 * s, rowY + 6.5f * s, fontIcon, 15f * s, 
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_TOP, ClickGui.color(0));
            NanoVGHelper.drawString("Bps: 0", currentX + 5 * s + NanoVGHelper.getTextWidth("D", fontIcon, 15f * s) + 4, 
                    rowY + 5.1f * s, fontText, fontSize, NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_TOP, new Color(255, 255, 255, 255));
        });
    }

    private int getCurrentPing() {
        if (mc.player == null || mc.getNetworkHandler() == null) return 0;
        var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return entry != null ? Math.max(entry.getLatency(), 0) : 0;
    }
<<<<<<< HEAD

    private void renderSpirt(float s) {
        if (mc.player == null) return;

        // Цвета из SpirtHack
        Color bgMain = new Color(22, 19, 41, 240); // Темно-фиолетовый фон
        Color bgRight = new Color(29, 25, 54, 255); // Чуть более светлый правый блок
        Color accentColor = new Color(110, 85, 235); // Сиреневый ромб
        Color textColor = new Color(220, 220, 225); // Бело-серый текст

        String clientName = Sakura.MOD_NAME; // или "trollhack"
        String userName = mc.player.getName().getString();
        String timeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        String rightText = userName + "  /  " + timeStr;

        int font = FontLoader.regular((int)(13f * s));
        float pad = 8f * s;
        float radius = 5f * s; // Скругление углов

        float leftW = NanoVGHelper.getTextWidth(clientName, font, 13f * s) + 25f * s;
        float rightW = NanoVGHelper.getTextWidth(rightText, font, 13f * s) + 16f * s;
        
        this.width = leftW + rightW;
        this.height = 26f * s;

        NanoVGRenderer.INSTANCE.draw(vg -> {
            // 1. Рисуем общую подложку (левая часть)
            NanoVGHelper.drawRoundRect(x, y, leftW + 5f * s, height, radius, bgMain);
            
            // 2. Рисуем правую выделенную плашку
            NanoVGHelper.drawRoundRect(x + leftW, y, rightW, height, radius, bgRight);
            
            // Заплатка, чтобы убрать скругление на стыке двух панелей
            NanoVGHelper.drawRect(x + leftW, y, 5f * s, height, bgRight);

            // 3. Иконка ромба (используем символ ❖)
            NanoVGHelper.drawString("❖", x + 8f * s, y + height / 2f, font, 12f * s, 
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, accentColor);

            // 4. Текст названия клиента
            NanoVGHelper.drawString(clientName, x + 22f * s, y + height / 2f, font, 13f * s, 
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, textColor);

            // 5. Правый текст (Ник / Время)
            NanoVGHelper.drawString(rightText, x + leftW + 8f * s, y + height / 2f, font, 13f * s, 
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, textColor);
        });
    }

    private void renderSeason(float s) {
        // Белый цвет с альфой для мягких градиентов фона
        Color whiteBG = new Color(255, 255, 255, 240);
        Color seasonBlue = new Color(114, 170, 246); // Синий цвет для буквы S и текста BETA
        Color textBlack = new Color(20, 20, 20);

        int fontBold = FontLoader.regular(14); // Замени на жирный шрифт, если есть в FontLoader

        float circleRadius = 20f * s;
        float textPad = 12f * s;
        
        String title = "SEASON ";
        String suffix = "BETA";
        
        float textW = NanoVGHelper.getTextWidth(title + suffix, fontBold, 14f * s);
        this.width = (circleRadius * 2) + textW + (textPad * 2) - 5f * s;
        this.height = circleRadius * 2;

        NanoVGRenderer.INSTANCE.draw(vg -> {
            // 1. Заднее прямоугольное крыло для текста
            NanoVGHelper.drawRoundRect(x + circleRadius, y + 4f * s, width - circleRadius, height - 8f * s, 6f * s, whiteBG);
            
            // 2. Левый большой круг с мягким свечением/градиентом
            NanoVGHelper.drawCircle(x + circleRadius, y + circleRadius, circleRadius, whiteBG);
            
            // 3. Рисуем букву "S" по центру круга
            NanoVGHelper.drawCenteredString("S", x + circleRadius, y + circleRadius + 1f * s, 
                    fontBold, 22f * s, seasonBlue);

            // 4. Текст внутри крыла
            float textStartX = x + (circleRadius * 2) + 5f * s;
            float textY = y + circleRadius;

            // Слово "SEASON" (Черное)
            NanoVGHelper.drawString(title, textStartX, textY, fontBold, 13f * s, 
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, textBlack);

            // Слово "BETA" (Синее, идет сразу после SEASON)
            float seasonWidth = NanoVGHelper.getTextWidth(title, fontBold, 13f * s);
            NanoVGHelper.drawString(suffix, textStartX + seasonWidth, textY, fontBold, 13f * s, 
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, seasonBlue);
        });
    }

    private void renderTrollhack(float s) {
        // Проверяем базовые сущности майнкрафта для вывода FPS и пинга
        if (mc.world == null || mc.player == null) return;

        // Сбор информации для разделителей (как на скриншотах Gamesense/Demise)
        final String name = "trollhack";
        final String branch = "beta";
        final String fps = mc.getCurrentFps() + " fps";
        
        // Получаем пинг текущего игрока
        final String ping;
        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()) != null) {
            ping = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()).getLatency() + "ms";
        } else {
            ping = "0ms";
        }
        
        // Время для финального элемента в строке
        final String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        // Формируем общую строчку с разделителями '|'
        final String splitter = " | ";
        final String fullText = name + " " + branch + splitter + fps + splitter + ping + splitter + time;

        // Палитра со скриншота
        Color bgMain = new Color(20, 20, 22, 240);       // Темная матовая подложка
        Color borderDark = new Color(40, 40, 45, 255);   // Внешний контур рамки
        Color textWhite = new Color(245, 245, 245);      // Основной текст
        Color textCyan = new Color(0, 205, 205);         // Бирюзовый акцент для названия/версии

        int font = FontLoader.regular((int)(11f * s));
        float textWidth = NanoVGHelper.getTextWidth(fullText, font, 11f * s);
        
        // Рассчитываем динамические размеры под длину текста
        float paddingX = 8f * s;
        float paddingY = 6f * s;
        this.width = textWidth + (paddingX * 2);
        this.height = 11f * s + (paddingY * 2);

        NanoVGRenderer.INSTANCE.draw(vg -> {
            // 1. Отрисовка основной темной панели
            NanoVGHelper.drawRect(x, y, width, height, bgMain);
            
            // 2. Тонкая обводка (Outline) панели
            NanoVG.nvgBeginPath(vg);
            NanoVG.nvgRect(vg, x, y, width, height);
            NanoVG.nvgStrokeColor(vg, NanoVGHelper.nvgColor(borderDark));
            NanoVG.nvgStrokeWidth(vg, 1.0f);
            NanoVG.nvgStroke(vg);

            // 3. Верхняя градиентная или сплошная неоновая линия (фирменный стиль)
            // На скриншоте используется бирюзовый градиентный переход, переходящий в фиолетовый
            NanoVGHelper.drawGradientRect(x + 1f, y + 1f, width - 2f, 1.5f, 
                    textCyan, new Color(140, 90, 215));

            // 4. Посегментный вывод текста для правильной покраски префикса "trollhack beta"
            float currentX = x + paddingX;
            float textY = y + height / 2f;

            // Рендерим "trollhack " (Бирюзовый/Циан)
            String prefix = name + " ";
            NanoVGHelper.drawString(prefix, currentX, textY, font, 11f * s, 
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, textCyan);
            currentX += NanoVGHelper.getTextWidth(prefix, font, 11f * s);

            // Рендерим "beta" (Белый)
            NanoVGHelper.drawString(branch, currentX, textY, font, 11f * s, 
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, textWhite);
            currentX += NanoVGHelper.getTextWidth(branch, font, 11f * s);

            // Рендерим оставшуюся системную часть " | X fps | X ms | HH:mm:ss"
            String technicalStats = splitter + fps + splitter + ping + splitter + time;
            NanoVGHelper.drawString(technicalStats, currentX, textY, font, 11f * s, 
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, textWhite);
        });
    }

    private void renderCompact(float s) {
        if (mc.world == null || mc.player == null) return;

        // Collect data as in sample: branch | nick | server | fps | fps
        String branch = "alpha";
        String userName = mc.getSession().getUsername().toLowerCase();
        
        String serverIp = "local";
        if (mc.getCurrentServerEntry() != null) {
            serverIp = mc.getCurrentServerEntry().address.toLowerCase();
        }

        String fpsText = mc.getCurrentFps() + " fps";
        String splitter = "  |  ";

        // Form the resulting line for accurate calculation of capsule width
        String fullText = branch + splitter + userName + splitter + serverIp + splitter + fpsText + splitter + fpsText;

        int fontText = FontLoader.regular((int)(11f * s));
        int fontSep = FontLoader.regular((int)(10f * s));
        float textWidth = NanoVGHelper.getTextWidth(fullText, fontText, 11f * s);

        // Icon size
        float iconSize = 14f * s;
        
        // Long capsule geometry
        float paddingX = 10f * s;
        this.width = iconSize + paddingX + textWidth + paddingX;
        this.height = 20f * s; // Narrow elongated strip

        NanoVGRenderer.INSTANCE.draw(vg -> {
            Color bgCap = new Color(25, 29, 36, 240);       // Dark capsule background
            Color logoBlue = new Color(115, 160, 250);     // Light blue neon for glow
            Color textWhite = new Color(235, 240, 245);     // Main parameter text
            Color splitColor = new Color(55, 62, 74, 255);  // Dark gray thin '|' separators

            // 1. Draw capsule with strong corner radius (height / 2 gives perfect oval on sides)
            NanoVGHelper.drawRoundRect(x, y, width, height, height / 2f, bgCap);

            // 2. Draw icon from badcache.ttf on the left
            float iconFontSize = 14f * s;
            int iconFont = getCompactIconFont(iconFontSize);
            String iconChar = "F"; // Using Player icon for watermark
            
            // Draw blue glow behind icon
            float iconW = NanoVGHelper.getTextWidth(iconChar, iconFont, iconFontSize);
            float iconH = NanoVGHelper.getFontHeight(iconFont, iconFontSize);
            NanoVGHelper.drawCircle(x + iconSize / 2f + 2f * s, y + height / 2f, iconSize / 2f + 2f * s, 
                    new Color(115, 160, 250, 60));
            
            // Draw icon
            NanoVGHelper.drawString(iconChar, x + iconSize / 2f, y + height / 2f + iconH * 0.3f, 
                    iconFont, iconFontSize, NanoVG.NVG_ALIGN_CENTER | NanoVG.NVG_ALIGN_MIDDLE, logoBlue);

            // Starting coordinate for text after icon
            float currentX = x + iconSize + paddingX;

            // Split the line into elements to color separators gray and text white
            String[] tokens = fullText.split("\\|");
            
            for (int i = 0; i < tokens.length; i++) {
                String token = tokens[i].trim();
                
                // Output information word
                NanoVGHelper.drawString(token, currentX, y + height / 2f, fontText, 11f * s, 
                        NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, textWhite);
                currentX += NanoVGHelper.getTextWidth(token, fontText, 11f * s);

                // If this is not the last element, draw gray separator '|'
                if (i < tokens.length - 1) {
                    float spaceW = NanoVGHelper.getTextWidth("  ", fontText, 11f * s);
                    currentX += spaceW;
                    
                    NanoVGHelper.drawString("|", currentX, y + height / 2f - 0.5f * s, fontSep, 10f * s, 
                            NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, splitColor);
                    currentX += NanoVGHelper.getTextWidth("|", fontSep, 10f * s) + spaceW;
                }
            }
        });
    }
=======
>>>>>>> parent of 584bcf3 (update fixed movecorection and elytra rezolver)
}