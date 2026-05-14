package dev.mzc.client.gui.mainmenu;

import com.mojang.blaze3d.opengl.GlStateManager;
import dev.mzc.client.auth.AccountStorage;
import dev.mzc.client.auth.AuthManager;
import dev.mzc.client.gui.component.SakuraButton;
import dev.mzc.client.gui.component.SakuraTextField;
import dev.mzc.client.gui.theme.SakuraTheme;
import dev.mzc.client.nanovg.NanoVGRenderer;
import dev.mzc.client.nanovg.font.FontLoader;
import dev.mzc.client.nanovg.util.NanoVGHelper;
import dev.mzc.client.utils.render.Shader2DUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.lwjgl.nanovg.NanoVG;

import java.awt.*;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

public class VerificationScreen extends Screen {
    public static boolean isVerified = false;
    private SakuraTextField username;
    private SakuraTextField password;
    private String errorMessage = "";
    private int failedAttempts = 0;
    private boolean rememberPassword = false;

    public VerificationScreen() {
        super(Text.literal("Verification"));
    }

    @Override
    protected void init() {
        clearChildren();

        float panelWidth = 300;
        float panelHeight = 320;
        float panelX = (width - panelWidth) / 2;
        float panelY = (height - panelHeight) / 2;

        float inputWidth = 200;
        float inputX = panelX + (panelWidth - inputWidth) / 2;
        
        float usernameY = panelY + 50;
        float passwordY = panelY + 90;
        float rememberY = panelY + 130;
        float buttonsY = panelY + 160;
        float buttonHeight = 24;
        float buttonGap = 10;

        AccountStorage.Credentials stored = AccountStorage.load();
        if (stored != null) {
            // Check for 72h expiration
            long now = System.currentTimeMillis();
            long diff = now - stored.timestamp;
            if (diff > 72 * 60 * 60 * 1000L) { // 72 hours
                AccountStorage.delete();
                stored = null;
                rememberPassword = false;
            } else {
                rememberPassword = true;
            }
        }

        // 用户名输入框
        username = new SakuraTextField(client.textRenderer, (int) inputX, (int) usernameY, (int) inputWidth, 24, Text.literal(""));
        username.setPlaceholder("Username");
        addDrawableChild(username);

        // 密码输入框
        password = new SakuraTextField(client.textRenderer, (int) inputX, (int) passwordY, (int) inputWidth, 24, Text.literal(""));
        password.setPlaceholder("Password");
        password.setPasswordMode(true);
        addDrawableChild(password);

        if (rememberPassword && stored != null) {
            username.setText(stored.username);
            password.setText(stored.password);
        }

        // Remember Password Toggle
        addDrawableChild(new SakuraButton((int) inputX, (int) rememberY, (int) inputWidth, 20, "Remember Password: " + (rememberPassword ? "ON" : "OFF"), (button) -> {
            rememberPassword = !rememberPassword;
            button.setMessage(Text.literal("Remember Password: " + (rememberPassword ? "ON" : "OFF")));
        }));

        addDrawableChild(new SakuraButton((int) inputX, (int) buttonsY, (int) inputWidth, (int) buttonHeight, "Login", (action) -> {
            String u = username.getText();
            String p = password.getText();
            if (p.isEmpty()) {
                errorMessage = "Password cannot be empty";
                return;
            }

            var r = AuthManager.performLogin(u, p, rememberPassword);
            if (r.success) {
                isVerified = true;
                client.setScreen(new MainMenuScreen());
                return;
            }

            if (r.isConnectionError) {
                errorMessage = r.message;
                return;
            }

            failedAttempts++;
            if (failedAttempts >= 3) {
                client.scheduleStop();
            } else {
                errorMessage = r.message + " (" + failedAttempts + "/3)";
                password.setText("");
            }
        }));

        // Sign Up 按钮
        addDrawableChild(new SakuraButton((int) inputX, (int) (buttonsY + buttonHeight + buttonGap), (int) inputWidth, (int) buttonHeight, "Sign Up", (action) -> {
            client.setScreen(new SignUpScreen(this));
        }));

        // 退出游戏按钮
        addDrawableChild(new SakuraButton((int) inputX, (int) (buttonsY + (buttonHeight + buttonGap) * 2), (int) inputWidth, (int) buttonHeight, "Exit Game", (action) -> {
            client.scheduleStop();
        }));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Shader2DUtil.drawQuadBlur(new MatrixStack(), 0, 0, width, height, 10, 0.5f);

        float panelWidth = 300;
        float panelHeight = 320;
        float panelX = (width - panelWidth) / 2;
        float panelY = (height - panelHeight) / 2;

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(770, 771, 1, 0);
        GlStateManager._disableDepthTest();
        GlStateManager._disableCull();

        // Background Blur
        Shader2DUtil.drawRoundedBlur(new MatrixStack(), panelX, panelY, panelWidth, panelHeight, SakuraTheme.PANEL_ROUNDING, new Color(0, 0, 0, 0), 10.0f, 1.0f);

        NanoVGRenderer.INSTANCE.draw(vg -> {
            // Draw Panel Background
            NanoVGHelper.drawRoundRect(panelX, panelY, panelWidth, panelHeight, SakuraTheme.PANEL_ROUNDING, SakuraTheme.PANEL_BG);
            NanoVGHelper.drawRoundRectOutline(panelX, panelY, panelWidth, panelHeight, SakuraTheme.PANEL_ROUNDING, 1.5f, new Color(255, 255, 255, 30));

            // Welcome Back Text - Using regular font as fallback if bold fails
            try {
                NanoVGHelper.drawString("Welcome Back", width / 2f, panelY - 40, FontLoader.bold(40), 40, NanoVG.NVG_ALIGN_CENTER | NanoVG.NVG_ALIGN_BOTTOM, new Color(255, 255, 255));
            } catch (Exception e) {
                 // Fallback to regular font if bold not loaded
                NanoVGHelper.drawString("Welcome Back", width / 2f, panelY - 40, FontLoader.regular(40), 40, NanoVG.NVG_ALIGN_CENTER | NanoVG.NVG_ALIGN_BOTTOM, new Color(255, 255, 255));
            }
            
            // 标题
            NanoVGHelper.drawString("Client Verification", width / 2f, panelY + 25, FontLoader.regular(24), 24, NanoVG.NVG_ALIGN_CENTER | NanoVG.NVG_ALIGN_MIDDLE, SakuraTheme.TEXT);
        });

        super.render(context, mouseX, mouseY, delta);

        // 错误信息 - 绘制在最上层，显示在用户名输入框上方
        if (!errorMessage.isEmpty()) {
            NanoVGRenderer.INSTANCE.draw(vg -> {
                NanoVGHelper.drawString(errorMessage, width / 2f, username.getY() - 10, FontLoader.regular(14), 14, NanoVG.NVG_ALIGN_CENTER | NanoVG.NVG_ALIGN_BOTTOM, new Color(255, 50, 50));
            });
        }
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput keyInput) {
        if (keyInput.key() == GLFW_KEY_ESCAPE) {
            return true; // 阻止 ESC 关闭
        }
        return super.keyPressed(keyInput);
    }
}
