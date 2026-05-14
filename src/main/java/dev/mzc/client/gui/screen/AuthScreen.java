package dev.mzc.client.gui.screen;

import dev.mzc.client.Sakura;
import dev.mzc.client.auth.AccountStorage;
import dev.mzc.client.auth.AuthManager;
import dev.mzc.client.auth.network.AuthClient;
import dev.mzc.client.nanovg.NanoVGRenderer;
import dev.mzc.client.nanovg.font.FontLoader;
import dev.mzc.client.nanovg.util.NanoVGHelper;
import dev.mzc.client.shaders.MainMenuShader;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.nanovg.NanoVG;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static dev.mzc.client.Sakura.mc;

public class AuthScreen extends Screen {
    private static final float FIELD_LABEL_FONT_SIZE = 10.4f; // Username/Password label: +30%
    private static final float FIELD_BLOCK_HEIGHT = 42f;
    private static final float FIELD_BLOCK_STEP = 64f; // input-to-input gap +20%

    private enum Mode {
        LOGIN,
        REGISTER
    }

    private enum Field {
        USERNAME,
        PASSWORD,
        CONFIRM_PASSWORD,
        CARD_KEY
    }

    private static final class UiRect {
        private final float x;
        private final float y;
        private final float w;
        private final float h;

        private UiRect(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        private boolean contains(double px, double py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }

        private float centerX() {
            return x + w / 2f;
        }

        private float centerY() {
            return y + h / 2f;
        }

        private UiRect offset(float dx, float dy) {
            return new UiRect(this.x + dx, this.y + dy, this.w, this.h);
        }
    }

    private static final class Layout {
        private UiRect loginTab;
        private UiRect registerTab;
        private UiRect primaryButton;
        private UiRect switchModeButton;
        private UiRect rememberToggle;
        private final Map<Field, UiRect> fields = new LinkedHashMap<>();
        private float cardX;
        private float cardY;
        private float cardW;
        private float cardH;
    }

    private Mode mode = Mode.LOGIN;
    private Field focusedField = Field.USERNAME;

    private String username = "";
    private String password = "";
    private String confirmPassword = "";
    private String cardKey = "";

    private boolean rememberPassword = false;
    private int failedAttempts = 0;

    private String statusMessage = "";
    private boolean authenticating = false;
    private boolean authSuccess = false;
    private boolean authError = false;

    private float cursorBlink = 0f;
    private Timer blinkTimer;
    private float time = 0f;
    private float entranceProgress = 0f;
    private float errorProgress = 0f;
    private float exitProgress = 0f;
    private float flashProgress = 0f;
    private boolean isExiting = false;

    private static final int PARTICLE_COUNT = 15;
    private final float[] particles = new float[PARTICLE_COUNT * 4];

    private static final Color BG_DEEP = new Color(8, 8, 20);
    private static final Color CARD_BG = new Color(18, 18, 35);
    private static final Color CARD_BORDER = new Color(70, 70, 110);

    private static final Color ACCENT = new Color(88, 166, 255);
    private static final Color ACCENT_ALT = new Color(168, 85, 247);
    private static final Color SUCCESS_COLOR = new Color(34, 197, 94);
    private static final Color ERROR_COLOR = new Color(239, 68, 68);

    private static final Color TEXT_MAIN = new Color(255, 255, 255);
    private static final Color TEXT_DIM = new Color(150, 150, 170);

    private static final Color INPUT_BG = new Color(25, 25, 45);
    private static final Color INPUT_BORDER = new Color(60, 60, 90);

    public AuthScreen() {
        super(Text.literal("AuthScreen"));

        this.blinkTimer = new Timer();
        this.blinkTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                cursorBlink = 1f - cursorBlink;
            }
        }, 0, 530);

        Random rand = new Random();
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particles[i * 4] = rand.nextFloat();
            particles[i * 4 + 1] = rand.nextFloat();
            particles[i * 4 + 2] = rand.nextFloat() * 2f + 1f;
            particles[i * 4 + 3] = rand.nextFloat() * 0.4f + 0.2f;
        }
    }

    @Override
    public void init() {
        entranceProgress = 0f;
        isExiting = false;
        exitProgress = 0f;
        flashProgress = 0f;

        AccountStorage.Credentials stored = AccountStorage.load();
        if (stored != null) {
            long now = System.currentTimeMillis();
            long diff = now - stored.timestamp;
            if (diff <= 72L * 60L * 60L * 1000L) {
                username = stored.username == null ? "" : stored.username;
                password = stored.password == null ? "" : stored.password;
                rememberPassword = true;
            } else {
                AccountStorage.delete();
                rememberPassword = false;
            }
        }
    }

    @Override
    public void removed() {
        if (blinkTimer != null) {
            blinkTimer.cancel();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float dt = delta * 0.05f;
        time += dt;

        if (isExiting) {
            exitProgress += dt * 2.5f;

            if (exitProgress >= 1f) {
                mc.setScreen(new TitleScreen());
                return;
            }
        } else {
            entranceProgress += (1.0f - entranceProgress) * dt * 3f;
            entranceProgress = Math.min(entranceProgress, 1.0f);
        }

        if (authError) {
            errorProgress += dt * 3f;
            if (errorProgress > 1f) {
                errorProgress = 0f;
                authError = false;
            }
        }

        try {
            MainMenuShader shader = MainMenuShader.getSharedInstance();
            shader.setMouseOffset((mouseX / (float) Math.max(1, this.width) - 0.5f) * this.width * 0.25f);
            shader.render(this.width, this.height, 1.0f);
        } catch (Throwable ignored) {
        }

        NanoVGRenderer.INSTANCE.draw(vg -> {
            float alpha = isExiting ? (1f - easeInOutCubic(exitProgress)) : easeOutCubic(entranceProgress);
            drawBackground(alpha);
            drawParticles(alpha);
            drawCard(mouseX, mouseY, alpha);
        });
    }

    private void drawBackground(float alpha) {
        NanoVGHelper.drawRect(0, 0, width, height, BG_DEEP);

        int glowAlpha = clamp((int) (alpha * 38));
        Color glow1 = new Color(88, 166, 255, glowAlpha);
        Color glow2 = new Color(168, 85, 247, glowAlpha);

        float x1 = width * 0.3f + (float) Math.sin(time * 0.3f) * 30f;
        float y1 = height * 0.4f + (float) Math.cos(time * 0.2f) * 50f;
        NanoVGHelper.drawCircle(x1, y1, 200f, glow1);

        float x2 = width * 0.7f + (float) Math.cos(time * 0.25f) * 40f;
        float y2 = height * 0.6f + (float) Math.sin(time * 0.35f) * 40f;
        NanoVGHelper.drawCircle(x2, y2, 180f, glow2);
    }

    private void drawParticles(float alpha) {
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            float x = particles[i * 4];
            float y = particles[i * 4 + 1];
            float size = particles[i * 4 + 2];
            float a = particles[i * 4 + 3];

            y -= 0.001f;
            if (y < 0) y = 1;
            particles[i * 4 + 1] = y;

            float px = x * width;
            float py = y * height;
            int particleAlpha = clamp((int) (a * alpha * 180));
            if (particleAlpha > 5) {
                NanoVGHelper.drawCircle(px, py, size, new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), particleAlpha));
            }
        }
    }

    private void drawCard(double mouseX, double mouseY, float alpha) {
        Layout layout = buildLayout();

        float offsetY = isExiting ? (1f - easeInOutCubic(exitProgress)) * -50f : (1f - easeOutBack(entranceProgress)) * 60f;
        float cardY = layout.cardY + offsetY;

        NanoVGHelper.drawRoundRect(layout.cardX, cardY, layout.cardW, layout.cardH, 20f,
                new Color(CARD_BG.getRed(), CARD_BG.getGreen(), CARD_BG.getBlue(), clamp((int) (240 * alpha))));
        NanoVGHelper.drawRoundRectOutline(layout.cardX, cardY, layout.cardW, layout.cardH, 20f, 1.5f,
                new Color(CARD_BORDER.getRed(), CARD_BORDER.getGreen(), CARD_BORDER.getBlue(), clamp((int) (180 * alpha))));

        float centerX = layout.cardX + layout.cardW / 2f;
        drawHeader(centerX, cardY + 38f, alpha);

        UiRect loginTab = layout.loginTab.offset(0f, offsetY);
        UiRect registerTab = layout.registerTab.offset(0f, offsetY);
        drawTab(loginTab, "Login", mode == Mode.LOGIN, mouseX, mouseY, alpha);
        drawTab(registerTab, "Register", mode == Mode.REGISTER, mouseX, mouseY, alpha);

        for (Map.Entry<Field, UiRect> entry : layout.fields.entrySet()) {
            drawField(entry.getKey(), entry.getValue().offset(0f, offsetY), mouseX, mouseY, alpha);
        }

        if (mode == Mode.LOGIN && layout.rememberToggle != null) {
            drawRememberToggle(layout.rememberToggle.offset(0f, offsetY), alpha);
        }

        drawActionButton(layout.primaryButton.offset(0f, offsetY), mode == Mode.LOGIN ? "Login" : "Sign Up", !authenticating, alpha);
        drawSwitchButton(layout.switchModeButton.offset(0f, offsetY),
                mode == Mode.LOGIN ? "Need an account? Register" : "Have an account? Login",
                alpha);

        drawStatus(centerX, cardY - 18f, alpha);
        drawFooter(centerX, cardY + layout.cardH - 22f, alpha);
    }

    private void drawHeader(float centerX, float y, float alpha) {
        String title = "MZC Authentication";
        String subtitle = mode == Mode.LOGIN ? "Login to continue" : "Create a new account";

        NanoVGHelper.drawString(title, centerX, y, FontLoader.bold(26), 26,
                NanoVG.NVG_ALIGN_CENTER | NanoVG.NVG_ALIGN_MIDDLE,
                new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), clamp((int) (255 * alpha))));

        NanoVGHelper.drawString(subtitle, centerX, y + 22f, FontLoader.regular(12), 12,
                NanoVG.NVG_ALIGN_CENTER | NanoVG.NVG_ALIGN_MIDDLE,
                new Color(TEXT_DIM.getRed(), TEXT_DIM.getGreen(), TEXT_DIM.getBlue(), clamp((int) (180 * alpha))));
    }

    private void drawTab(UiRect rect, String text, boolean active, double mouseX, double mouseY, float alpha) {
        boolean hovered = rect.contains(mouseX, mouseY);
        Color bg = active
                ? new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), clamp((int) (150 * alpha)))
                : new Color(35, 35, 58, clamp((int) ((hovered ? 180 : 130) * alpha)));
        NanoVGHelper.drawRoundRect(rect.x, rect.y, rect.w, rect.h, 8f, bg);

        Color textColor = active ? new Color(255, 255, 255, clamp((int) (255 * alpha)))
                : new Color(TEXT_DIM.getRed(), TEXT_DIM.getGreen(), TEXT_DIM.getBlue(), clamp((int) (230 * alpha)));

        NanoVGHelper.drawString(text, rect.centerX(), rect.centerY(), FontLoader.bold(12), 12,
                NanoVG.NVG_ALIGN_CENTER | NanoVG.NVG_ALIGN_MIDDLE, textColor);
    }

    private void drawField(Field field, UiRect rect, double mouseX, double mouseY, float alpha) {
        boolean hovered = rect.contains(mouseX, mouseY);
        boolean focused = focusedField == field;
        boolean hasText = !getFieldValue(field).isEmpty();

        float shake = authError ? (float) Math.sin(errorProgress * 30f) * 4f * (1f - errorProgress) : 0f;

        Color bg = new Color(INPUT_BG.getRed(), INPUT_BG.getGreen(), INPUT_BG.getBlue(), clamp((int) (220 * alpha)));
        NanoVGHelper.drawRoundRect(rect.x + shake, rect.y, rect.w, rect.h, 10f, bg);

        int br = focused ? 120 : (hovered ? 90 : INPUT_BORDER.getRed());
        int bgc = focused ? 150 : (hovered ? 110 : INPUT_BORDER.getGreen());
        int bb = focused ? 220 : (hovered ? 160 : INPUT_BORDER.getBlue());
        NanoVGHelper.drawRoundRectOutline(rect.x + shake, rect.y, rect.w, rect.h, 10f, 1.5f,
                new Color(br, bgc, bb, clamp((int) (200 * alpha))));

        String label = switch (field) {
            case USERNAME -> "USERNAME";
            case PASSWORD -> "PASSWORD";
            case CONFIRM_PASSWORD -> "CONFIRM PASSWORD";
            case CARD_KEY -> "CARD KEY";
        };

        String placeholder = switch (field) {
            case USERNAME -> "Enter username";
            case PASSWORD -> "Enter password";
            case CONFIRM_PASSWORD -> "Confirm password";
            case CARD_KEY -> "Enter card key";
        };

        String value = getFieldValue(field);
        String shown = value;
        if (isPasswordField(field) && !value.isEmpty()) {
            shown = "*".repeat(Math.max(0, value.length()));
        }

        NanoVGHelper.drawString(label, rect.x + 10f, rect.y - 10f, FontLoader.bold(FIELD_LABEL_FONT_SIZE), FIELD_LABEL_FONT_SIZE,
                NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE,
                new Color(TEXT_DIM.getRed(), TEXT_DIM.getGreen(), TEXT_DIM.getBlue(), clamp((int) (170 * alpha))));

        if (shown.isEmpty()) {
            NanoVGHelper.drawString(placeholder, rect.x + 12f, rect.centerY(), FontLoader.regular(13), 13,
                    NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE,
                    new Color(TEXT_DIM.getRed(), TEXT_DIM.getGreen(), TEXT_DIM.getBlue(), clamp((int) (130 * alpha))));
            return;
        }

        String clipped = shown;
        float maxWidth = rect.w - 24f;
        while (!clipped.isEmpty() && NanoVGHelper.getTextWidth(clipped, FontLoader.regular(13), 13) > maxWidth) {
            clipped = clipped.substring(1);
        }
        if (!clipped.equals(shown)) {
            clipped = "..." + clipped;
        }

        NanoVGHelper.drawString(clipped, rect.x + 12f, rect.centerY(), FontLoader.regular(13), 13,
                NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE,
                new Color(TEXT_MAIN.getRed(), TEXT_MAIN.getGreen(), TEXT_MAIN.getBlue(), clamp((int) (255 * alpha))));

        if (focused && cursorBlink > 0.5f) {
            float textWidth = NanoVGHelper.getTextWidth(clipped, FontLoader.regular(13), 13);
            float cx = Math.min(rect.x + rect.w - 8f, rect.x + 12f + textWidth + 2f);
            NanoVGHelper.drawLine(cx, rect.y + 10f, cx, rect.y + rect.h - 10f, 1.5f,
                    new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), clamp((int) (255 * alpha))));
        }
    }

    private void drawRememberToggle(UiRect rect, float alpha) {
        Color textColor = new Color(TEXT_MAIN.getRed(), TEXT_MAIN.getGreen(), TEXT_MAIN.getBlue(), clamp((int) (210 * alpha)));

        NanoVGHelper.drawRoundRect(rect.x, rect.y, rect.h, rect.h, 6f,
                new Color(35, 35, 55, clamp((int) (170 * alpha))));

        if (rememberPassword) {
            NanoVGHelper.drawRoundRect(rect.x + 4f, rect.y + 4f, rect.h - 8f, rect.h - 8f, 4f,
                    new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), clamp((int) (255 * alpha))));
        }

        NanoVGHelper.drawString("Remember Password", rect.x + rect.h + 8f, rect.centerY(), FontLoader.regular(12), 12,
                NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, textColor);
    }

    private void drawActionButton(UiRect rect, String text, boolean enabled, float alpha) {
        Color bg = enabled
                ? new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), clamp((int) (230 * alpha)))
                : new Color(60, 60, 80, clamp((int) (170 * alpha)));

        NanoVGHelper.drawRoundRect(rect.x, rect.y, rect.w, rect.h, 12f, bg);

        String shown = authenticating ? "Please wait..." : text;
        Color tc = enabled ? new Color(255, 255, 255, clamp((int) (255 * alpha)))
                : new Color(TEXT_DIM.getRed(), TEXT_DIM.getGreen(), TEXT_DIM.getBlue(), clamp((int) (200 * alpha)));

        NanoVGHelper.drawString(shown, rect.centerX(), rect.centerY(), FontLoader.bold(13), 13,
                NanoVG.NVG_ALIGN_CENTER | NanoVG.NVG_ALIGN_MIDDLE, tc);
    }

    private void drawSwitchButton(UiRect rect, String text, float alpha) {
        NanoVGHelper.drawRoundRect(rect.x, rect.y, rect.w, rect.h, 10f,
                new Color(35, 35, 55, clamp((int) (150 * alpha))));

        NanoVGHelper.drawString(text, rect.centerX(), rect.centerY(), FontLoader.regular(12), 12,
                NanoVG.NVG_ALIGN_CENTER | NanoVG.NVG_ALIGN_MIDDLE,
                new Color(TEXT_DIM.getRed(), TEXT_DIM.getGreen(), TEXT_DIM.getBlue(), clamp((int) (230 * alpha))));
    }

    private void drawStatus(float centerX, float y, float alpha) {
        if (statusMessage == null || statusMessage.isEmpty()) return;

        Color color = authSuccess ? SUCCESS_COLOR : (authError ? ERROR_COLOR : ACCENT_ALT);

        NanoVGHelper.drawString(statusMessage, centerX, y, FontLoader.regular(12), 12,
                NanoVG.NVG_ALIGN_CENTER | NanoVG.NVG_ALIGN_MIDDLE,
                new Color(color.getRed(), color.getGreen(), color.getBlue(), clamp((int) (255 * alpha))));
    }

    private void drawFooter(float centerX, float y, float alpha) {
        String version = "v" + Sakura.MOD_VER;
        NanoVGHelper.drawString(version, centerX, y, FontLoader.regular(9), 9,
                NanoVG.NVG_ALIGN_CENTER | NanoVG.NVG_ALIGN_MIDDLE,
                new Color(TEXT_DIM.getRed(), TEXT_DIM.getGreen(), TEXT_DIM.getBlue(), clamp((int) (100 * alpha))));
    }

    private Layout buildLayout() {
        Layout layout = new Layout();

        float cardW = Math.min(430f, width * 0.9f);
        float cardH = mode == Mode.LOGIN ? 420f : 510f;
        float cardX = (width - cardW) / 2f;
        float cardY = (height - cardH) / 2f;

        layout.cardX = cardX;
        layout.cardY = cardY;
        layout.cardW = cardW;
        layout.cardH = cardH;

        float tabW = (cardW - 56f) / 2f;
        float tabY = cardY + 80f;
        layout.loginTab = new UiRect(cardX + 20f, tabY, tabW, 28f);
        layout.registerTab = new UiRect(cardX + 36f + tabW, tabY, tabW, 28f);

        float fieldX = cardX + 20f;
        float fieldW = cardW - 40f;
        float y = tabY + 52f;

        layout.fields.put(Field.USERNAME, new UiRect(fieldX, y, fieldW, FIELD_BLOCK_HEIGHT));
        y += FIELD_BLOCK_STEP;

        layout.fields.put(Field.PASSWORD, new UiRect(fieldX, y, fieldW, FIELD_BLOCK_HEIGHT));
        y += FIELD_BLOCK_STEP;

        if (mode == Mode.REGISTER) {
            layout.fields.put(Field.CONFIRM_PASSWORD, new UiRect(fieldX, y, fieldW, FIELD_BLOCK_HEIGHT));
            y += FIELD_BLOCK_STEP;
            layout.fields.put(Field.CARD_KEY, new UiRect(fieldX, y, fieldW, FIELD_BLOCK_HEIGHT));
            y += FIELD_BLOCK_STEP + 2f;
        } else {
            // Keep remember row spacing balanced: top/bottom gaps both reduced by 50%.
            layout.rememberToggle = new UiRect(fieldX, y - 10f, fieldW, 18f);
            y += 20f;
        }

        layout.primaryButton = new UiRect(fieldX, y, fieldW, 38f);
        y += 46f;
        layout.switchModeButton = new UiRect(fieldX, y, fieldW, 30f);

        return layout;
    }

    private boolean isPasswordField(Field field) {
        return field == Field.PASSWORD || field == Field.CONFIRM_PASSWORD;
    }

    private String getFieldValue(Field field) {
        return switch (field) {
            case USERNAME -> username;
            case PASSWORD -> password;
            case CONFIRM_PASSWORD -> confirmPassword;
            case CARD_KEY -> cardKey;
        };
    }

    private void setFieldValue(Field field, String value) {
        switch (field) {
            case USERNAME -> username = value;
            case PASSWORD -> password = value;
            case CONFIRM_PASSWORD -> confirmPassword = value;
            case CARD_KEY -> cardKey = value;
        }
    }

    private int maxFieldLen(Field field) {
        return field == Field.CARD_KEY ? 64 : 32;
    }

    private List<Field> visibleFields() {
        List<Field> fields = new ArrayList<>();
        fields.add(Field.USERNAME);
        fields.add(Field.PASSWORD);
        if (mode == Mode.REGISTER) {
            fields.add(Field.CONFIRM_PASSWORD);
            fields.add(Field.CARD_KEY);
        }
        return fields;
    }

    private void cycleFocus(boolean backwards) {
        List<Field> fields = visibleFields();
        if (fields.isEmpty()) return;

        int idx = fields.indexOf(focusedField);
        if (idx < 0) {
            focusedField = fields.get(0);
            return;
        }

        int next = backwards ? (idx - 1 + fields.size()) % fields.size() : (idx + 1) % fields.size();
        focusedField = fields.get(next);
    }

    private void setMode(Mode newMode) {
        if (this.mode == newMode) return;
        this.mode = newMode;
        this.focusedField = Field.USERNAME;
        this.statusMessage = "";
        this.authError = false;
        this.errorProgress = 0f;
        this.authSuccess = false;

        if (newMode == Mode.LOGIN) {
            confirmPassword = "";
            cardKey = "";
        }
    }

    private void submit() {
        if (authenticating || isExiting) return;

        authError = false;
        errorProgress = 0f;
        authSuccess = false;

        if (mode == Mode.LOGIN) {
            if (username.isBlank()) {
                failLocal("Username cannot be empty");
                return;
            }
            if (password.isBlank()) {
                failLocal("Password cannot be empty");
                return;
            }

            authenticating = true;
            statusMessage = "Logging in...";

            new Thread(() -> {
                AuthClient.Result result;
                try {
                    result = AuthManager.performLogin(username, password, rememberPassword);
                } catch (Exception e) {
                    result = AuthClient.Result.connectionError("Connection failed");
                }
                final AuthClient.Result finalResult = result;

                mc.execute(() -> {
                    authenticating = false;
                    if (finalResult.success) {
                        onSuccess("Login success");
                        return;
                    }

                    failedAttempts++;
                    if (!finalResult.isConnectionError && failedAttempts >= 3 && mc != null) {
                        statusMessage = "Too many failures";
                        authError = true;
                        mc.scheduleStop();
                        return;
                    }

                    failLocal(finalResult.message == null || finalResult.message.isBlank() ? "Login failed" : finalResult.message);
                });
            }, "AuthScreen-Login").start();
            return;
        }

        if (username.isBlank()) {
            failLocal("Username cannot be empty");
            return;
        }
        if (password.isBlank()) {
            failLocal("Password cannot be empty");
            return;
        }
        if (!password.equals(confirmPassword)) {
            password = "";
            confirmPassword = "";
            failLocal("Passwords do not match");
            return;
        }
        if (cardKey.isBlank()) {
            failLocal("Card key cannot be empty");
            return;
        }

        authenticating = true;
        statusMessage = "Registering...";

        new Thread(() -> {
            AuthClient.Result result;
            try {
                result = AuthManager.performRegister(cardKey, username, password);
            } catch (Exception e) {
                result = AuthClient.Result.connectionError("Connection failed");
            }
            final AuthClient.Result finalResult = result;

            mc.execute(() -> {
                authenticating = false;
                if (finalResult.success) {
                    onSuccess("Register success");
                } else {
                    failLocal(finalResult.message == null || finalResult.message.isBlank() ? "Register failed" : finalResult.message);
                }
            });
        }, "AuthScreen-Register").start();
    }

    private void onSuccess(String message) {
        authSuccess = true;
        authError = false;
        statusMessage = message;

        CompletableFuture.delayedExecutor(650, TimeUnit.MILLISECONDS).execute(() -> {
            if (mc != null) {
                mc.execute(() -> {
                    isExiting = true;
                    exitProgress = 0f;
                    flashProgress = 0f;
                });
            }
        });
    }

    private void failLocal(String message) {
        authError = true;
        authSuccess = false;
        statusMessage = message;
        errorProgress = 0f;
    }

    @Override
    public boolean charTyped(CharInput charInput) {
        if (authenticating || isExiting || focusedField == null) return false;
        if (!charInput.isValidChar()) return false;

        char chr = (char) charInput.codepoint();
        if (chr < 32 || chr > 126) return false;

        String current = getFieldValue(focusedField);
        if (current.length() >= maxFieldLen(focusedField)) return true;

        setFieldValue(focusedField, current + chr);
        authError = false;
        errorProgress = 0f;
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        int key = keyInput.key();
        int modifiers = keyInput.modifiers();

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            return true;
        }

        if (authenticating || isExiting) {
            return true;
        }

        if (key == GLFW.GLFW_KEY_TAB) {
            boolean backwards = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            cycleFocus(backwards);
            return true;
        }

        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }

        if (focusedField != null && key == GLFW.GLFW_KEY_BACKSPACE) {
            String current = getFieldValue(focusedField);
            if (!current.isEmpty()) {
                setFieldValue(focusedField, current.substring(0, current.length() - 1));
                authError = false;
                errorProgress = 0f;
            }
            return true;
        }

        if (focusedField != null && key == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            String clip = mc.keyboard.getClipboard();
            if (clip != null && !clip.isEmpty()) {
                String sanitized = clip.chars()
                        .mapToObj(c -> String.valueOf((char) c))
                        .filter(s -> s.charAt(0) >= 32 && s.charAt(0) <= 126)
                        .reduce("", String::concat);

                String cur = getFieldValue(focusedField);
                int remain = maxFieldLen(focusedField) - cur.length();
                if (remain > 0) {
                    if (sanitized.length() > remain) {
                        sanitized = sanitized.substring(0, remain);
                    }
                    setFieldValue(focusedField, cur + sanitized);
                    authError = false;
                    errorProgress = 0f;
                }
            }
            return true;
        }

        return super.keyPressed(keyInput);
    }

    @Override
    public boolean mouseClicked(Click click, boolean playSound) {
        if (authenticating || isExiting) {
            return super.mouseClicked(click, playSound);
        }

        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (button != 0) {
            return super.mouseClicked(click, playSound);
        }

        Layout layout = buildLayout();

        if (layout.loginTab.contains(mouseX, mouseY)) {
            setMode(Mode.LOGIN);
            return true;
        }
        if (layout.registerTab.contains(mouseX, mouseY)) {
            setMode(Mode.REGISTER);
            return true;
        }

        for (Map.Entry<Field, UiRect> entry : layout.fields.entrySet()) {
            if (entry.getValue().contains(mouseX, mouseY)) {
                focusedField = entry.getKey();
                return true;
            }
        }

        if (layout.rememberToggle != null && layout.rememberToggle.contains(mouseX, mouseY)) {
            rememberPassword = !rememberPassword;
            return true;
        }

        if (layout.primaryButton.contains(mouseX, mouseY)) {
            submit();
            return true;
        }

        if (layout.switchModeButton.contains(mouseX, mouseY)) {
            setMode(mode == Mode.LOGIN ? Mode.REGISTER : Mode.LOGIN);
            return true;
        }

        return super.mouseClicked(click, playSound);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private float easeOutCubic(float t) {
        return 1f - (float) Math.pow(1f - t, 3f);
    }

    private float easeInOutCubic(float t) {
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3f) / 2f;
    }

    private float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(t - 1f, 3f) + c1 * (float) Math.pow(t - 1f, 2f);
    }
}
