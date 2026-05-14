package dev.mzc.client.gui.mainmenu;

import com.mojang.blaze3d.opengl.GlStateManager;
import dev.mzc.client.auth.AuthManager;
import dev.mzc.client.gui.component.SakuraButton;
import dev.mzc.client.gui.component.SakuraTextField;
import dev.mzc.client.gui.theme.SakuraTheme;
import dev.mzc.client.nanovg.NanoVGRenderer;
import dev.mzc.client.nanovg.font.FontLoader;
import dev.mzc.client.nanovg.util.NanoVGHelper;
import dev.mzc.client.utils.render.Shader2DUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.nanovg.NanoVG;

import java.awt.*;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

public class SignUpScreen extends Screen {
    private final Screen parent;
    private SakuraTextField username;
    private SakuraTextField password;
    private SakuraTextField confirmPassword;
    private SakuraTextField cardKey;
    private String errorMessage = "";
    private boolean signingUp = false;

    public SignUpScreen(Screen parent) {
        super(Text.literal("Sign Up"));
        this.parent = parent;
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
        
        float startY = panelY + 60;
        float spacing = 40;
        float buttonHeight = 24;

        // 1. 用户名
        username = new SakuraTextField(client.textRenderer, (int) inputX, (int) startY, (int) inputWidth, 24, Text.literal(""));
        username.setPlaceholder("Username");
        addDrawableChild(username);

        // 2. 密码
        password = new SakuraTextField(client.textRenderer, (int) inputX, (int) (startY + spacing), (int) inputWidth, 24, Text.literal(""));
        password.setPlaceholder("Password");
        password.setPasswordMode(true);
        addDrawableChild(password);

        // 3. 确认密码
        confirmPassword = new SakuraTextField(client.textRenderer, (int) inputX, (int) (startY + spacing * 2), (int) inputWidth, 24, Text.literal(""));
        confirmPassword.setPlaceholder("Confirm Password");
        confirmPassword.setPasswordMode(true);
        addDrawableChild(confirmPassword);

        // 4. 卡密
        cardKey = new SakuraTextField(client.textRenderer, (int) inputX, (int) (startY + spacing * 3), (int) inputWidth, 24, Text.literal(""));
        cardKey.setPlaceholder("Card Key");
        addDrawableChild(cardKey);

        // 5. Sign Up (暂时没功能)
        float signUpY = startY + spacing * 4;
        addDrawableChild(new SakuraButton((int) inputX, (int) signUpY, (int) inputWidth, (int) buttonHeight, "Sign Up", (action) -> {
            if (signingUp) return;
            String p1 = password.getText();
            String p2 = confirmPassword.getText();
            String u = username.getText();
            String ck = cardKey.getText();

            if (u == null || u.trim().isEmpty()) {
                errorMessage = "Username cannot be empty";
                return;
            }
            if (ck == null || ck.trim().isEmpty()) {
                errorMessage = "Card Key cannot be empty";
                return;
            }
            if (p1.isEmpty()) {
                errorMessage = "Password cannot be empty";
                return;
            }
            if (!p1.equals(p2)) {
                 errorMessage = "Passwords do not match!";
                 password.setText("");
                 confirmPassword.setText("");
                 return;
            }

            signingUp = true;
            errorMessage = "Signing up...";

            new Thread(() -> {
                try {
                    var r = AuthManager.performRegister(ck, u, p1);
                    if (client != null) {
                        client.execute(() -> {
                            signingUp = false;
                            if (r.success) {
                                VerificationScreen.isVerified = true;
                                client.setScreen(new MainMenuScreen());
                            } else {
                                errorMessage = r.message;
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    if (client != null) {
                        client.execute(() -> {
                            signingUp = false;
                            errorMessage = "Error: " + e.getMessage();
                        });
                    }
                }
            }).start();
        }));

        // 6. Back to Login
        float backY = signUpY + buttonHeight + 10;
        addDrawableChild(new SakuraButton((int) inputX, (int) backY, (int) inputWidth, (int) buttonHeight, "Back to Login", (action) -> {
            client.setScreen(parent);
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
            NanoVGHelper.drawRoundRect(panelX, panelY, panelWidth, panelHeight, SakuraTheme.PANEL_ROUNDING, SakuraTheme.PANEL_BG);
            NanoVGHelper.drawRoundRectOutline(panelX, panelY, panelWidth, panelHeight, SakuraTheme.PANEL_ROUNDING, 1.5f, new Color(255, 255, 255, 30));

            NanoVGHelper.drawString("Sign Up", width / 2f, panelY + 25, FontLoader.regular(24), 24, NanoVG.NVG_ALIGN_CENTER | NanoVG.NVG_ALIGN_MIDDLE, SakuraTheme.TEXT);

            if (!errorMessage.isEmpty()) {
                NanoVGHelper.drawString(errorMessage, width / 2f, panelY + 45, FontLoader.regular(14), 14, NanoVG.NVG_ALIGN_CENTER | NanoVG.NVG_ALIGN_MIDDLE, new Color(255, 50, 50));
            }
        });

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput keyInput) {
        if (keyInput.key() == GLFW_KEY_ESCAPE) {
            client.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyInput);
    }
}
