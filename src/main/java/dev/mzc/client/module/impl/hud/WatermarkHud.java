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
        Exalted("Exalted");

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

    public WatermarkHud() {
        super("Watermark", 10, 10);
    }

    @Override
    public void onRender(DrawContext context) {
        float s = hudScale.get().floatValue();

        if (style.is(Style.Exalted)) {
            renderExalted(s);
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
}