package dev.mzc.client.gui.mainmenu;

import dev.mzc.client.Sakura;
import dev.mzc.client.account.type.impl.MicrosoftAccount;
import dev.mzc.client.gui.account.AccountSelectorScreen;
import dev.mzc.client.manager.Managers;
import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.nanovg.NanoVGRenderer;
import dev.mzc.client.nanovg.font.FontLoader;
import dev.mzc.client.nanovg.util.NanoVGHelper;
import dev.mzc.client.shaders.MainMenuShader;
import dev.mzc.client.utils.MusicPlayer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.session.Session;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.lwjgl.nanovg.NanoVG;

import java.awt.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.mzc.client.Sakura.mc;
import static org.lwjgl.nanovg.NanoVG.nvgCreateImageMem;

public class MainMenuScreen extends Screen {
    private final List<MenuButton> buttons = new ArrayList<>();
    private ShaderButton shaderButton;
    private int iconImage = -1;
    private int iconWidth, iconHeight;

    // 账号头像
    private static final AtomicInteger pendingAvatarImage = new AtomicInteger(-1);
    private int avatarNvgImage = -1;
    private String lastAvatarUsername = null;
    private boolean avatarLoading = false;

    // 随机名字按钮区域（在 render 里计算，mouseClicked 里用）
    private float randBtnX, randBtnY, randBtnW, randBtnH;
    
    // Music Player Area
    private float musicX, musicY, musicW, musicH;
    private float playBtnX, playBtnY, playBtnSize;
    private float nextBtnX, nextBtnY, nextBtnSize;
    private float prevBtnX, prevBtnY, prevBtnSize;
    private float listBtnX, listBtnY, listBtnSize;
    private boolean listOpened = false;
    private float listScrollY = 0;
    private float maxListScroll = 0;

    private static final Random RANDOM = new Random();
    private static final String[] PREFIXES = {
        "Dark", "Shadow", "Swift", "Iron", "Storm", "Frost", "Blaze", "Void",
        "Neon", "Pixel", "Hyper", "Ultra", "Mega", "Alpha", "Omega", "Cyber",
        "Ghost", "Stealth", "Rapid", "Silent", "Lunar", "Solar", "Astro", "Nova"
    };
    private static final String[] SUFFIXES = {
        "Wolf", "Fox", "Eagle", "Hawk", "Bear", "Tiger", "Lion", "Viper",
        "Blade", "Strike", "Force", "Edge", "Pulse", "Wave", "Shift", "Drift",
        "Hunter", "Sniper", "Raider", "Knight", "Ranger", "Slayer", "Reaper", "Phantom"
    };

    private static String generateRandomName() {
        String prefix = PREFIXES[RANDOM.nextInt(PREFIXES.length)];
        String suffix = SUFFIXES[RANDOM.nextInt(SUFFIXES.length)];
        // 30% 概率加数字后缀
        String num = RANDOM.nextInt(10) < 3 ? String.valueOf(RANDOM.nextInt(999) + 1) : "";
        return prefix + suffix + num;
    }

    public MainMenuScreen() {
        super(Text.literal("MainMenuScreen"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected void init() {
        ClickGui clickGui = Sakura.MODULES.getModule(ClickGui.class);
        if (clickGui != null && clickGui.getKey() == -1) {
            mc.setScreen(new WelcomeScreen());
            return;
        }

        updateLayout();
        loadIcon();
    }

    private void updateLayout() {
        buttons.clear();
        int centerX = this.width / 2;
        int startY = this.height / 4 + 48;
        int buttonWidth = 200;
        int buttonHeight = 20;
        int spacing = 24;

        buttons.add(new MenuButton(
                centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight,
                I18n.translate("menu.singleplayer"), () -> mc.setScreen(new SelectWorldScreen(this))
        ));
        buttons.add(new MenuButton(
                centerX - buttonWidth / 2, startY + spacing, buttonWidth, buttonHeight,
                I18n.translate("menu.multiplayer"), () -> mc.setScreen(new MultiplayerScreen(this)), !isMultiplayerDisabled()
        ));
        buttons.add(new MenuButton(
                centerX - buttonWidth / 2, startY + spacing * 2, buttonWidth, buttonHeight,
                "Alt Manager", () -> mc.setScreen(new AccountSelectorScreen(this))
        ));
        buttons.add(new MenuButton(
                centerX - buttonWidth / 2, startY + spacing * 3, 98, buttonHeight,
                I18n.translate("menu.options"), () -> mc.setScreen(new OptionsScreen(this, mc.options))
        ));
        buttons.add(new MenuButton(
                centerX + 2, startY + spacing * 3, 98, buttonHeight,
                I18n.translate("menu.quit"), mc::scheduleStop
        ));

        shaderButton = new ShaderButton(centerX - 110, this.height - 30, 220, 20);
        buttons.add(shaderButton);
    }

    private void loadIcon() {
        if (iconImage != -1) return;
        iconImage = NanoVGHelper.loadTexture("/assets/sakura/icons/icon.png");
        if (iconImage != -1) {
            iconWidth = 2334;
            iconHeight = 860;
        }
    }

    private boolean isMultiplayerDisabled() {
        if (mc.isMultiplayerEnabled()) return false;
        if (mc.isUsernameBanned()) return true;
        return mc.getMultiplayerBanDetails() != null;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 主界面 Shader 速度降低
        MainMenuShader.getSharedInstance().setSpeed(0.6f);
        this.renderBackground(context, mouseX, mouseY, delta);
        MusicPlayer.INSTANCE.update();

        try {
            NanoVGRenderer.INSTANCE.draw(vg -> {
                if (iconImage != -1) {
                    float displayScale = 0.14f;
                    float displayWidth = iconWidth * displayScale;
                    float displayHeight = iconHeight * displayScale;
                    float x = (width - displayWidth) / 2f;
                    float y = height / 5f - displayHeight / 2f;
                    NanoVGHelper.drawTexture(iconImage, x, y, displayWidth, displayHeight, 1.0f, 1.0f, 1.0f, 0);
                }
                if (mc.getSession() != null) {
                    renderAccountCard(mc.getSession().getUsername());
                }
                renderMusicPlayer(mouseX, mouseY);
            });
        } catch (Throwable ignored) {
        }

        renderCustomButtons(context, mouseX, mouseY);
    }

    private void renderMusicPlayer(int mouseX, int mouseY) {
        musicW = 230f;
        musicH = 70f;
        musicX = 15f;
        musicY = this.height - musicH - 15f;
        float radius = 10f;

        // Visual Improvement: Background with a bit of glow and better colors
        NanoVGHelper.drawShadow(musicX, musicY, musicW, musicH, radius, new Color(0, 0, 0, 100), 10, 0, 0);
        NanoVGHelper.drawRoundRect(musicX, musicY, musicW, musicH, radius, new Color(25, 25, 35, 220));
        NanoVGHelper.drawRoundRectOutline(musicX, musicY, musicW, musicH, radius, 1.5f, new Color(255, 255, 255, 40));

        // Song Name
        String songName = MusicPlayer.INSTANCE.getCurrentSongName().toUpperCase();
        if (songName.length() > 20) songName = songName.substring(0, 17) + "...";
        NanoVGHelper.drawString(songName, musicX + 15, musicY + 22, FontLoader.bold(15), 15, Color.WHITE);

        // Progress Bar (Visual improvement)
        float progressX = musicX + 15;
        float progressY = musicY + 35;
        float progressW = musicW - 30;
        float progressH = 4;
        NanoVGHelper.drawRoundRect(progressX, progressY, progressW, progressH, 2, new Color(50, 50, 60, 200));
        float prog = MusicPlayer.INSTANCE.getProgress();
        if (prog > 0) {
            NanoVGHelper.drawRoundRect(progressX, progressY, progressW * prog, progressH, 2, ClickGui.color(0));
            // Tiny glow at the end of progress
            NanoVGHelper.drawCircle(progressX + progressW * prog, progressY + progressH/2, 3, Color.WHITE);
        }

        // Controls Positions
        playBtnSize = 24;
        playBtnX = musicX + musicW / 2 - playBtnSize / 2;
        playBtnY = musicY + 42;
        
        prevBtnSize = 20;
        prevBtnX = playBtnX - 40;
        prevBtnY = playBtnY + 2;
        
        nextBtnSize = 20;
        nextBtnX = playBtnX + playBtnSize + 20;
        nextBtnY = playBtnY + 2;

        listBtnSize = 22;
        listBtnX = musicX + musicW - 35;
        listBtnY = musicY + 12;

        // Draw Controls
        String playIcon = MusicPlayer.INSTANCE.isPlaying() ? "Pause" : "Play";
        renderControlIcon(playIcon, playBtnX, playBtnY, playBtnSize, mouseX, mouseY);
        renderControlIcon("Prev", prevBtnX, prevBtnY, prevBtnSize, mouseX, mouseY);
        renderControlIcon("Next", nextBtnX, nextBtnY, nextBtnSize, mouseX, mouseY);
        
        // List Button (Visual improvement)
        boolean listHovered = mouseX >= listBtnX && mouseX <= listBtnX + listBtnSize && mouseY >= listBtnY && mouseY <= listBtnY + listBtnSize;
        NanoVGHelper.drawString("L", listBtnX + 6, listBtnY + 16, FontLoader.icons(18), 18, listHovered ? ClickGui.color(0) : Color.LIGHT_GRAY);

        if (listOpened) {
            renderSongList(mouseX, mouseY);
        }
    }

    private void renderSongList(int mouseX, int mouseY) {
        List<MusicPlayer.MusicTrack> tracks = MusicPlayer.INSTANCE.getTracks();
        float listW = 230f;
        float listMaxH = 200f;
        float listH = Math.min(tracks.size() * 25f + 10f, listMaxH);
        float listX = musicX;
        float listY = musicY - listH - 8f;
        
        maxListScroll = Math.max(0, (tracks.size() * 25f + 10f) - listMaxH);

        NanoVGHelper.drawShadow(listX, listY, listW, listH, 10, new Color(0, 0, 0, 100), 15, 0, 0);
        NanoVGHelper.drawRoundRect(listX, listY, listW, listH, 10, new Color(20, 20, 30, 240));
        NanoVGHelper.drawRoundRectOutline(listX, listY, listW, listH, 10, 1.5f, new Color(255, 255, 255, 50));

        NanoVGHelper.save();
        NanoVGHelper.scissor(listX, listY + 5, listW, listH - 10);
        
        float itemY = listY + 5 + listScrollY;
        for (int i = 0; i < tracks.size(); i++) {
            MusicPlayer.MusicTrack track = tracks.get(i);
            boolean hovered = mouseX >= listX && mouseX <= listX + listW && mouseY >= itemY && mouseY <= itemY + 25;
            boolean current = i == MusicPlayer.INSTANCE.getCurrentTrackIndex();
            
            if (hovered) {
                NanoVGHelper.drawRect(listX + 5, itemY, listW - 10, 25, new Color(255, 255, 255, 15));
            }
            
            if (current) {
                NanoVGHelper.drawRect(listX + 2, itemY + 5, 2, 15, ClickGui.color(0));
            }
            
            Color textColor = current ? ClickGui.color(0) : (hovered ? Color.WHITE : new Color(180, 180, 180));
            NanoVGHelper.drawString(track.name, listX + 15, itemY + 17, FontLoader.regular(14), 14, textColor);
            
            itemY += 25;
        }
        NanoVGHelper.restore();
        
        // Scroll bar
        if (maxListScroll > 0) {
            float barH = (listH / (tracks.size() * 25f + 10f)) * listH;
            float barY = listY + (-listScrollY / maxListScroll) * (listH - barH);
            NanoVGHelper.drawRect(listX + listW - 4, barY, 2, barH, new Color(255, 255, 255, 100));
        }
    }

    private void renderControlIcon(String type, float x, float y, float size, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + size && mouseY >= y && mouseY <= y + size;
        Color color = hovered ? ClickGui.color(0) : Color.WHITE;
        long vg = NanoVGRenderer.INSTANCE.getContext();
        
        if (type.equals("Play")) {
            NanoVG.nvgBeginPath(vg);
            NanoVG.nvgMoveTo(vg, x + size * 0.35f, y + size * 0.25f);
            NanoVG.nvgLineTo(vg, x + size * 0.85f, y + size * 0.5f);
            NanoVG.nvgLineTo(vg, x + size * 0.35f, y + size * 0.75f);
            NanoVG.nvgClosePath(vg);
            NanoVG.nvgFillColor(vg, NanoVGHelper.nvgColor(color));
            NanoVG.nvgFill(vg);
        } else if (type.equals("Pause")) {
            float w = size * 0.15f;
            NanoVGHelper.drawRoundRect(x + size * 0.25f, y + size * 0.25f, w, size * 0.5f, 1, color);
            NanoVGHelper.drawRoundRect(x + size * 0.6f, y + size * 0.25f, w, size * 0.5f, 1, color);
        } else if (type.equals("Next")) {
            NanoVGHelper.drawRect(x + size * 0.75f, y + size * 0.3f, 2, size * 0.4f, color);
            NanoVG.nvgBeginPath(vg);
            NanoVG.nvgMoveTo(vg, x + size * 0.25f, y + size * 0.3f);
            NanoVG.nvgLineTo(vg, x + size * 0.65f, y + size * 0.5f);
            NanoVG.nvgLineTo(vg, x + size * 0.25f, y + size * 0.7f);
            NanoVG.nvgClosePath(vg);
            NanoVG.nvgFillColor(vg, NanoVGHelper.nvgColor(color));
            NanoVG.nvgFill(vg);
        } else if (type.equals("Prev")) {
            NanoVGHelper.drawRect(x + size * 0.2f, y + size * 0.3f, 2, size * 0.4f, color);
            NanoVG.nvgBeginPath(vg);
            NanoVG.nvgMoveTo(vg, x + size * 0.75f, y + size * 0.3f);
            NanoVG.nvgLineTo(vg, x + size * 0.35f, y + size * 0.5f);
            NanoVG.nvgLineTo(vg, x + size * 0.75f, y + size * 0.7f);
            NanoVG.nvgClosePath(vg);
            NanoVG.nvgFillColor(vg, NanoVGHelper.nvgColor(color));
            NanoVG.nvgFill(vg);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int mouseX = (int) click.x();
        int mouseY = (int) click.y();
        int btn = click.button();

        // Music Controls
        if (btn == 0) {
            if (mouseX >= playBtnX && mouseX <= playBtnX + playBtnSize && mouseY >= playBtnY && mouseY <= playBtnY + playBtnSize) {
                if (MusicPlayer.INSTANCE.isPlaying()) MusicPlayer.INSTANCE.pause();
                else MusicPlayer.INSTANCE.play();
                return true;
            }
            if (mouseX >= nextBtnX && mouseX <= nextBtnX + nextBtnSize && mouseY >= nextBtnY && mouseY <= nextBtnY + nextBtnSize) {
                MusicPlayer.INSTANCE.next();
                return true;
            }
            if (mouseX >= prevBtnX && mouseX <= prevBtnX + prevBtnSize && mouseY >= prevBtnY && mouseY <= prevBtnY + prevBtnSize) {
                MusicPlayer.INSTANCE.previous();
                return true;
            }
            if (mouseX >= listBtnX && mouseX <= listBtnX + listBtnSize && mouseY >= listBtnY && mouseY <= listBtnY + listBtnSize) {
                listOpened = !listOpened;
                listScrollY = 0;
                return true;
            }

            if (listOpened) {
                List<MusicPlayer.MusicTrack> tracks = MusicPlayer.INSTANCE.getTracks();
                float listW = 230f;
                float listMaxH = 200f;
                float listH = Math.min(tracks.size() * 25f + 10f, listMaxH);
                float listX = musicX;
                float listY = musicY - listH - 8f;
                if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
                    float itemY = listY + 5 + listScrollY;
                    for (int i = 0; i < tracks.size(); i++) {
                        if (mouseY >= itemY && mouseY <= itemY + 25) {
                            MusicPlayer.INSTANCE.play(i);
                            return true;
                        }
                        itemY += 25;
                    }
                    return true;
                } else {
                    listOpened = false;
                }
            }
        }

        // 随机名字按钮
        if (btn == 0 && mouseX >= randBtnX && mouseX <= randBtnX + randBtnW
                && mouseY >= randBtnY && mouseY <= randBtnY + randBtnH) {
            String newName = generateRandomName();
            Session session = new Session(newName, UUID.randomUUID(), "", Optional.empty(), Optional.empty());
            Managers.ACCOUNT.setSession(session);
            // 重置头像
            if (avatarNvgImage != -1) {
                NanoVGHelper.deleteTexture(avatarNvgImage);
                avatarNvgImage = -1;
            }
            lastAvatarUsername = null;
            avatarLoading = false;
            pendingAvatarImage.set(-1);
            return true;
        }

        // 遍历按钮列表处理点击
        if (btn == 0 || btn == 1) {
            for (MenuButton button : buttons) {
                if (button.enabled && button.isHovered(mouseX, mouseY)) {
                    if (button instanceof ShaderButton sb) {
                        if (btn == 0) sb.nextShader();
                        else sb.previousShader();
                    } else if (btn == 0) {
                        button.onClick();
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (listOpened) {
            listScrollY += (float) (scrollY * 20);
            listScrollY = MathHelper.clamp(listScrollY, -maxListScroll, 0);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void renderCustomButtons(DrawContext context, int mouseX, int mouseY) {
        NanoVGRenderer.INSTANCE.draw(vg -> {
            for (MenuButton button : buttons) {
                boolean hovered = button.isHovered(mouseX, mouseY);
                boolean enabled = button.enabled;
                float radius = 7f;

                Color bg = hovered
                        ? new Color(58, 76, 122, enabled ? 205 : 130)
                        : new Color(24, 34, 56, enabled ? 170 : 120);

                if (button instanceof ShaderButton) {
                    // 让 Shader 切换按钮更加通透一些
                    bg = hovered 
                            ? new Color(255, 255, 255, 45) 
                            : new Color(255, 255, 255, 25);
                }

                Color border = hovered
                        ? new Color(255, 255, 255, enabled ? 230 : 150)
                        : new Color(208, 208, 208, enabled ? 190 : 130);
                Color textColor = enabled ? new Color(255, 255, 255, 255) : new Color(136, 136, 136, 255);

                NanoVGHelper.drawRoundRect(button.x, button.y, button.width, button.height, radius, bg);
                NanoVGHelper.drawRoundRectOutline(button.x, button.y, button.width, button.height, radius, 1.0f, border);
                NanoVGHelper.drawCenteredString(
                        button.text,
                        button.x + button.width / 2f,
                        button.y + button.height / 2f + 0.5f,
                        FontLoader.regular(14),
                        14f,
                        textColor
                );
            }
        });
    }

    @Override
    public void removed() {
        super.removed();
        if (iconImage != -1) {
            NanoVGHelper.deleteTexture(iconImage);
            iconImage = -1;
        }
        if (avatarNvgImage != -1) {
            NanoVGHelper.deleteTexture(avatarNvgImage);
            avatarNvgImage = -1;
            lastAvatarUsername = null;
        }
    }

    private void renderAccountCard(String username) {
        // 用户名变了，重置头像
        if (!username.equals(lastAvatarUsername)) {
            if (avatarNvgImage != -1) {
                NanoVGHelper.deleteTexture(avatarNvgImage);
                avatarNvgImage = -1;
            }
            lastAvatarUsername = username;
            avatarLoading = false;
            pendingAvatarImage.set(-1);
        }

        // 把后台线程下载好的 image 提交到 NanoVG（必须在渲染线程）
        int pending = pendingAvatarImage.getAndSet(-1);
        if (pending != -1) {
            avatarNvgImage = pending;
        }

        // 异步下载头像
        if (avatarNvgImage == -1 && !avatarLoading) {
            avatarLoading = true;
            String url = "https://minotar.net/helm/" + username + "/64";
            Sakura.EXECUTOR.execute(() -> {
                try (CloseableHttpClient client = HttpClients.createDefault()) {
                    HttpGet req = new HttpGet(url);
                    try (CloseableHttpResponse resp = client.execute(req)) {
                        byte[] bytes = resp.getEntity().getContent().readAllBytes();
                        ByteBuffer buf = ByteBuffer.allocateDirect(bytes.length);
                        buf.put(bytes).flip();
                        // nvgCreateImageMem 必须在渲染线程调用
                        mc.execute(() -> {
                            int img = nvgCreateImageMem(NanoVGRenderer.INSTANCE.getContext(), 0, buf);
                            pendingAvatarImage.set(img);
                        });
                    }
                } catch (Exception ignored) {}
            });
        }

        // 判断账号类型
        String accountType = "Offline";
        if (Managers.ACCOUNT != null) {
            for (var acc : Managers.ACCOUNT.getAccounts()) {
                if (acc.username() != null && acc.username().equalsIgnoreCase(username)) {
                    if (acc instanceof MicrosoftAccount) accountType = "Microsoft";
                    break;
                }
            }
        }

        // 卡片尺寸 and 位置
        float cardW = 200f;
        float cardH = 50f;
        float margin = 10f;
        float cardX = this.width - cardW - margin;
        float cardY = margin;
        float avatarSize = 34f;
        float avatarX = cardX + 8f;
        float avatarY = cardY + (cardH - avatarSize) / 2f;
        float radius = 8f;

        // 随机按钮（在卡片内部右侧）
        float btnSize = 28f;
        float btnPadding = 8f;
        randBtnW = btnSize;
        randBtnH = btnSize;
        randBtnX = cardX + cardW - btnSize - btnPadding;
        randBtnY = cardY + (cardH - btnSize) / 2f;

        // 背景卡片
        NanoVGHelper.drawRoundRect(cardX, cardY, cardW, cardH, radius, new Color(18, 18, 28, 200));
        NanoVGHelper.drawRoundRectOutline(cardX, cardY, cardW, cardH, radius, 1f, new Color(255, 255, 255, 30));

        // 随机按钮渲染
        boolean btnHovered = mc.mouse != null
                && mc.mouse.getX() / mc.getWindow().getScaleFactor() >= randBtnX
                && mc.mouse.getX() / mc.getWindow().getScaleFactor() <= randBtnX + randBtnW
                && mc.mouse.getY() / mc.getWindow().getScaleFactor() >= randBtnY
                && mc.mouse.getY() / mc.getWindow().getScaleFactor() <= randBtnY + randBtnH;
        Color btnBg = btnHovered ? new Color(58, 76, 122, 210) : new Color(35, 35, 45, 180);
        NanoVGHelper.drawRoundRect(randBtnX, randBtnY, btnSize, btnSize, radius, btnBg);
        NanoVGHelper.drawRoundRectOutline(randBtnX, randBtnY, btnSize, btnSize, radius, 1f, new Color(255, 255, 255, 30));
        // 随机按钮字母 R（常规加粗字体）
        NanoVGHelper.drawCenteredString("R", randBtnX + btnSize / 2f, randBtnY + btnSize / 2f + 1f,
                FontLoader.bold(14), 14f, new Color(255, 255, 255, btnHovered ? 255 : 180));

        // 头像（圆角矩形裁剪，和卡片弧度一致）
        if (avatarNvgImage != -1) {
            long vg = NanoVGRenderer.INSTANCE.getContext();
            org.lwjgl.nanovg.NVGPaint imgPaint = org.lwjgl.nanovg.NVGPaint.create();
            org.lwjgl.nanovg.NanoVG.nvgImagePattern(vg, avatarX, avatarY, avatarSize, avatarSize, 0, avatarNvgImage, 1f, imgPaint);
            org.lwjgl.nanovg.NanoVG.nvgBeginPath(vg);
            org.lwjgl.nanovg.NanoVG.nvgRoundedRect(vg, avatarX, avatarY, avatarSize, avatarSize, radius);
            org.lwjgl.nanovg.NanoVG.nvgFillPaint(vg, imgPaint);
            org.lwjgl.nanovg.NanoVG.nvgFill(vg);
        } else {
            NanoVGHelper.drawRoundRect(avatarX, avatarY, avatarSize, avatarSize, radius, new Color(60, 80, 120, 200));
        }

        // 用户名
        float textX = avatarX + avatarSize + 10f;
        float nameY = cardY + cardH / 2f - 6f;
        NanoVGHelper.drawString(username, textX, nameY, FontLoader.bold(15), 15f, NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, new Color(255, 255, 255, 255));

        // 账号类型
        Color typeColor = accountType.equals("Microsoft") ? new Color(80, 200, 100, 255) : new Color(160, 160, 160, 255);
        NanoVGHelper.drawString(accountType, textX, nameY + 16f, FontLoader.regular(12), 12f, NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE, typeColor);
    }
}
