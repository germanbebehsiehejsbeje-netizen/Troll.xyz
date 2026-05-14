package dev.mzc.client.gui.account.list;

import dev.mzc.client.account.type.MinecraftAccount;
import dev.mzc.client.account.type.impl.CrackedAccount;
import dev.mzc.client.account.type.impl.MicrosoftAccount;
import dev.mzc.client.account.util.TextureDownloader;
import dev.mzc.client.manager.Managers;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.session.Session;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_1;

public class AccountEntry extends AlwaysSelectedEntryListWidget.Entry<AccountEntry> {
    private static final TextureDownloader FACE_DOWNLOADER = new TextureDownloader();

    private final MinecraftAccount account;
    private long lastClickTime = -1;

    public AccountEntry(MinecraftAccount account) {
        this.account = account;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
        // 使用原生渲染头像
        if (account instanceof CrackedAccount || (account instanceof MicrosoftAccount msa && msa.getUsernameOrNull() != null)) {
            final String id = "face_" + account.username().toLowerCase();
            if (!FACE_DOWNLOADER.exists(id)) {
                if (!FACE_DOWNLOADER.isDownloading(id)) {
                    FACE_DOWNLOADER.downloadTexture(id,
                            "https://minotar.net/helm/" + account.username() + "/15", false);
                }
            } else {
                final Identifier texture = FACE_DOWNLOADER.get(id);
                if (texture != null) {
                    int avatarY = getY() + (getHeight() - 16) / 2;
                    context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, getX() + 4, avatarY, 0, 0, 16, 16, 16, 16);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean playSound) {
        if (click.button() == GLFW_MOUSE_BUTTON_1) {
            final long time = System.currentTimeMillis() - lastClickTime;
            if (time > 0L && time < 500L) {
                final Session session = account.login();
                if (session != null) {
                    Managers.ACCOUNT.setSession(session);
                }
            }
            lastClickTime = System.currentTimeMillis();
            return false;
        }
        return super.mouseClicked(click, playSound);
    }

    @Override
    public Text getNarration() {
        if (account instanceof MicrosoftAccount msa && msa.username() == null) {
            return null;
        }
        return Text.literal(account.username());
    }

    public MinecraftAccount getAccount() {
        return account;
    }
}
