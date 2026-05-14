package dev.mzc.client.manager.impl;

import dev.mzc.client.nanovg.NanoVGRenderer;
import dev.mzc.client.nanovg.font.FontLoader;
import dev.mzc.client.nanovg.util.NanoVGHelper;
import dev.mzc.client.utils.animations.Easing;
import dev.mzc.client.utils.render.Shader2DUtil;
import net.minecraft.client.util.math.MatrixStack;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE;

public class NotificationManager {
    private static final long DEFAULT_TIMEOUT = 3000L;
    private static final float NOTIFICATION_HEIGHT = 48.0f;
    private static final float STACK_SPACING = 5.0f;
    private static final float CARD_RADIUS = 7.0f;
    private static final float IN_OUT_DURATION = 320.0f;
    private static final float STACK_ANIM_SPEED = 14.0f;

    private static final List<Notification> notifications = new ArrayList<>();
    private static final Map<Long, Notification> notificationMap = new HashMap<>();
    private static long lastRenderNanos = 0L;

    public static void send(String message) {
        send(message.hashCode(), message, DEFAULT_TIMEOUT);
    }

    public static void send(String message, long length) {
        send(message.hashCode(), message, length);
    }

    public static void send(Object identifier, String message, long length) {
        send(identifier.hashCode(), message, length);
    }

    public static void send(long id, String message, long length) {
        synchronized (notificationMap) {
            Notification existing = notificationMap.get(id);
            if (existing != null && !existing.shouldRemove) {
                existing.update(message, length);
            } else {
                Notification notification = new Notification(message, length, id);
                notificationMap.put(id, notification);
                notifications.add(notification);
            }
        }
    }

    public static float[] renderPreview(MatrixStack matrices, float x, float y, boolean leftAligned, Color primaryColor,
                                        Color backgroundColor, float maxWidth, boolean blur, float blurStrength,
                                        float radius, boolean shadow, boolean progressBar) {
        NotificationVisual visual = resolveVisual("Notification Preview");
        float width = computeNotificationWidth(visual, maxWidth);
        float height = NOTIFICATION_HEIGHT;

        if (blur) {
            Shader2DUtil.drawRoundedBlur(matrices, x, y, width, height, CARD_RADIUS,
                    new Color(0, 0, 0, 0), blurStrength, 1.0f);
        }

        NanoVGRenderer.INSTANCE.draw(vg -> drawNotificationContent(
                x, y, width, height, visual, 1.0f, 0.66f,
                primaryColor, backgroundColor, shadow, progressBar
        ));

        return new float[]{width, height};
    }

    public static void render(MatrixStack matrices, float x, float y, boolean leftAligned, Color primaryColor,
                              Color backgroundColor, float maxWidth, boolean blur, float blurStrength,
                              float radius, boolean shadow, boolean progressBar) {
        float stackY = y;
        long now = System.currentTimeMillis();
        long nowNanos = System.nanoTime();
        if (lastRenderNanos == 0L) lastRenderNanos = nowNanos;
        float dt = clamp01((nowNanos - lastRenderNanos) / 1_000_000_000.0);
        lastRenderNanos = nowNanos;
        float stackLerp = 1.0f - (float) Math.exp(-STACK_ANIM_SPEED * dt);

        List<RenderData> renderList = new ArrayList<>();
        List<Notification> snapshot = new ArrayList<>(notifications);
        int stackIndex = 0;

        for (int i = 0; i < snapshot.size(); i++) {
            Notification notification = snapshot.get(i);
            long alive = now - notification.startTime;

            float alpha;
            float offsetProgress;

            if (alive < IN_OUT_DURATION) {
                float inFactor = (float) Easing.CUBIC_OUT.ease(clamp01((double) alive / IN_OUT_DURATION));
                alpha = inFactor;
                offsetProgress = inFactor;
            } else if (alive > notification.length) {
                long endTime = alive - notification.length;
                if (endTime > IN_OUT_DURATION) {
                    notification.shouldRemove = true;
                    continue;
                }
                float outFactor = 1.0f - (float) Easing.CUBIC_IN.ease(clamp01((double) endTime / IN_OUT_DURATION));
                alpha = outFactor;
                offsetProgress = outFactor;
            } else {
                alpha = 1.0f;
                offsetProgress = 1.0f;
            }

            NotificationVisual visual = resolveVisual(notification.message);
            float width = computeNotificationWidth(visual, maxWidth);
            float height = NOTIFICATION_HEIGHT;

            float targetY = y - stackIndex * (height + STACK_SPACING);
            if (Float.isNaN(notification.animatedY)) {
                notification.animatedY = targetY;
            } else {
                notification.animatedY += (targetY - notification.animatedY) * stackLerp;
            }
            float drawY = notification.animatedY;

            float targetX;
            if (leftAligned) {
                targetX = x;
            } else {
                float previewWidth = computeNotificationWidth(resolveVisual("Notification Preview"), maxWidth);
                targetX = x + previewWidth - width;
            }

            float direction = leftAligned ? -1.0f : 1.0f;
            float drawX = targetX + direction * (1.0f - offsetProgress) * (width + 12.0f);
            float lifeProgress = 1.0f - clamp01((float) alive / notification.length);

            renderList.add(new RenderData(notification, visual, drawX, drawY, width, height, alpha, lifeProgress));
            stackIndex++;

            if (blur && alpha > 0.1f) {
                Shader2DUtil.drawRoundedBlur(matrices, drawX, drawY, width, height, CARD_RADIUS,
                        new Color(0, 0, 0, 0), blurStrength * alpha, alpha);
            }
        }

        NanoVGRenderer.INSTANCE.draw(vg -> {
            for (RenderData data : renderList) {
                drawNotificationContent(
                        data.x, data.y, data.width, data.height,
                        data.visual, data.alpha, data.progress,
                        primaryColor, backgroundColor, shadow, progressBar
                );
            }
        });

        notifications.removeIf(n -> n.shouldRemove);
        synchronized (notificationMap) {
            notificationMap.values().removeIf(n -> n.shouldRemove);
        }
    }

    private static void drawNotificationContent(float x, float y, float width, float height, NotificationVisual visual,
                                                float alpha, float progress, Color primary, Color background,
                                                boolean shadow, boolean progressBar) {
        int alphaInt = (int) (255 * clamp01(alpha));

        Color mixedBackground = blend(background, new Color(9, 9, 14, 220), 0.58f);
        Color bg = withAlpha(mixedBackground, (int) (mixedBackground.getAlpha() * clamp01(alpha)));
        Color haloColor = withAlpha(visual.accentColor, (int) (132 * clamp01(alpha)));

        if (shadow) {
            NanoVGHelper.drawShadow(x, y, width, height, CARD_RADIUS,
                    new Color(0, 0, 0, Math.max(0, alphaInt / 2)), 14.0f, 0.0f, 3.0f);
        }

        NanoVGHelper.drawRoundRectHaloFlow(x, y, width, height, CARD_RADIUS, haloColor, 4.5f, 10.0f);
        NanoVGHelper.drawRoundRect(x, y, width, height, CARD_RADIUS, bg);

        float pad = 6.0f;
        float iconSize = height - pad * 2.0f;
        float iconX = x + pad;
        float iconY = y + pad;

        Color iconBg = withAlpha(blend(visual.accentColor, new Color(5, 7, 15, 255), 0.66f),
                (int) (160 * clamp01(alpha)));
        Color iconColor = withAlpha(Color.WHITE, alphaInt);

        NanoVGHelper.drawRoundRect(iconX, iconY, iconSize, iconSize, 5.0f, iconBg);

        int iconFont = FontLoader.solid(16);
        NanoVGHelper.drawString(visual.icon,
                iconX + iconSize / 2.0f,
                iconY + iconSize / 2.0f + 1.0f,
                iconFont,
                16.0f,
                NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE,
                iconColor
        );

        float textX = iconX + iconSize + 7.0f;
        float textMaxWidth = Math.max(40.0f, x + width - textX - 8.0f);

        String title = ellipsize(stripFormattingCodes(visual.title), FontLoader.bold(11), 11.0f, textMaxWidth);
        String description = ellipsize(stripFormattingCodes(visual.description), FontLoader.regular(10), 10.0f, textMaxWidth);

        NanoVGHelper.drawString(title, textX, y + 17.0f, FontLoader.bold(11), 11.0f,
                NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE, withAlpha(Color.WHITE, alphaInt));
        NanoVGHelper.drawString(description, textX, y + 30.0f, FontLoader.regular(10), 10.0f,
                NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE, withAlpha(new Color(172, 176, 193), alphaInt));

        if (progressBar && progress > 0.0f) {
            float progressY = y + height - 4.0f;
            float barWidth = (width - 1.0f) * clamp01(progress);
            Color start = withAlpha(visual.accentColor, (int) (220 * clamp01(alpha)));
            Color end = withAlpha(primary, (int) (220 * clamp01(alpha)));

            NanoVGHelper.drawRoundRect(x + 0.5f, progressY, width - 1.0f, 2.5f, 1.25f,
                    withAlpha(new Color(255, 255, 255), (int) (24 * clamp01(alpha))));
            NanoVGHelper.drawGradientRRect2(x + 0.5f, progressY, barWidth, 2.5f, 1.25f, start, end);
        }
    }

    private static float computeNotificationWidth(NotificationVisual visual, float maxWidth) {
        String title = stripFormattingCodes(visual.title);
        String desc = stripFormattingCodes(visual.description);

        float iconBlock = NOTIFICATION_HEIGHT - 12.0f;
        float textWidth = Math.max(
                NanoVGHelper.getTextWidth(title, FontLoader.bold(11), 11.0f),
                NanoVGHelper.getTextWidth(desc, FontLoader.regular(10), 10.0f)
        );
        float width = 6.0f + iconBlock + 7.0f + textWidth + 8.0f;
        return Math.min(Math.max(156.0f, width), maxWidth);
    }

    private static NotificationVisual resolveVisual(String rawMessage) {
        String plain = stripFormattingCodes(rawMessage == null ? "" : rawMessage).trim();
        if (plain.isEmpty()) {
            plain = "Notification";
        }

        String title = "Notification";
        String description = plain;

        int split = plain.indexOf(": ");
        if (split > 1 && split < plain.length() - 2) {
            title = plain.substring(0, split).trim();
            description = plain.substring(split + 2).trim();
        } else if (plain.length() <= 18) {
            title = plain;
            description = "System message";
        }

        String lower = plain.toLowerCase();
        if (isDisabledState(lower)) {
            return new NotificationVisual(title, description, "\uf00d", new Color(255, 92, 92));
        }
        if (isEnabledState(lower)) {
            return new NotificationVisual(title, description, "\uf00c", new Color(96, 220, 142));
        }

        if (lower.contains("warning") || lower.contains("low hp") || lower.contains("breaking")) {
            return new NotificationVisual(title, description, "\uf071", new Color(255, 178, 74));
        }
        if (lower.contains("killed") || lower.contains("death") || lower.contains("die")) {
            return new NotificationVisual(title, description, "\uf714", new Color(255, 95, 115));
        }
        if (lower.contains("pop") || lower.contains("totem")) {
            return new NotificationVisual(title, description, "\uf1b2", new Color(164, 124, 255));
        }
        if (lower.contains("win") || lower.contains("victory") || lower.contains("赢") || lower.contains("胜利")) {
            return new NotificationVisual(title, description, "\uf091", new Color(255, 215, 0));
        }
        if (lower.contains("success")) {
            return new NotificationVisual(title, description, "\uf00c", new Color(96, 220, 142));
        }
        return new NotificationVisual(title, description, "\uf05a", new Color(104, 162, 255));
    }

    private static boolean isEnabledState(String text) {
        return containsAny(text,
                " enabled", "enable", "success",
                "已启用", "已开启", "启用", "开启",
                " aktiviert", "aktiviert", "eingeschaltet",
                " включен", "включено", "включить", "включ");
    }

    private static boolean isDisabledState(String text) {
        return containsAny(text,
                " disabled", "disable",
                "已禁用", "已关闭", "禁用", "关闭",
                " deaktiviert", "deaktiviert", "ausgeschaltet",
                " выключен", "выключено", "выключить", "выключ");
    }

    private static boolean containsAny(String text, String... needles) {
        if (text == null || text.isEmpty()) return false;
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String stripFormattingCodes(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '\u00A7' || c == '\u6402') && i + 1 < text.length()) {
                char code = text.charAt(i + 1);
                if (code == '#' && i + 7 < text.length()) {
                    i += 7;
                    continue;
                }
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String ellipsize(String text, int font, float size, float maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (NanoVGHelper.getTextWidth(text, font, size) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        float ellipsisWidth = NanoVGHelper.getTextWidth(ellipsis, font, size);
        StringBuilder out = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String candidate = out.toString() + c;
            float width = NanoVGHelper.getTextWidth(candidate, font, size);
            if (width + ellipsisWidth > maxWidth) {
                break;
            }
            out.append(c);
        }

        return out + ellipsis;
    }

    private static float clamp01(double value) {
        return (float) Math.max(0.0, Math.min(1.0, value));
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    private static Color blend(Color a, Color b, float factor) {
        float t = clamp01(factor);
        int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        int al = (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t);
        return new Color(r, g, bl, al);
    }

    private static class RenderData {
        Notification notification;
        NotificationVisual visual;
        float x;
        float y;
        float width;
        float height;
        float alpha;
        float progress;

        RenderData(Notification notification, NotificationVisual visual, float x, float y, float width,
                   float height, float alpha, float progress) {
            this.notification = notification;
            this.visual = visual;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.alpha = alpha;
            this.progress = progress;
        }
    }

    private static class NotificationVisual {
        final String title;
        final String description;
        final String icon;
        final Color accentColor;

        NotificationVisual(String title, String description, String icon, Color accentColor) {
            this.title = title;
            this.description = description;
            this.icon = icon;
            this.accentColor = accentColor;
        }
    }

    public static class Notification {
        private String message;
        private long length;
        public final long id;
        public long startTime = -1L;
        public boolean shouldRemove = false;
        public float animatedY = Float.NaN;

        public Notification(String message, long length, long id) {
            this.message = message;
            this.length = length;
            this.id = id;
            this.startTime = System.currentTimeMillis();
        }

        public void update(String message, long length) {
            this.message = message;
            this.length = length + (System.currentTimeMillis() - startTime);
        }
    }
}
