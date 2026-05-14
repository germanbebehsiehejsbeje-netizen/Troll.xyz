package dev.mzc.client.gui.clickgui.component.values;

import dev.mzc.client.Sakura;
import dev.mzc.client.gui.Component;
import dev.mzc.client.gui.clickgui.SelectionScreen;
import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.nanovg.NanoVGRenderer;
import dev.mzc.client.nanovg.font.FontLoader;
import dev.mzc.client.nanovg.util.NanoVGHelper;
import dev.mzc.client.utils.render.RenderUtil;
import dev.mzc.client.utils.render.Shader2DUtil;
import dev.mzc.client.utils.TranslationManager;
import dev.mzc.client.values.impl.ListValue;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.nanovg.NanoVG; // Import NanoVG

import java.awt.*;

public class ListValueComponent extends Component {
    private final ListValue<?> setting;

    public ListValueComponent(ListValue<?> setting) {
        this.setting = setting;
    }

    @Override
    public void render(DrawContext guiGraphics, int mouseX, int mouseY, float partialTicks) {
        float baseFontSize = (float) ClickGui.getFontSize();
        float scaledHeight = 18 * scale;
        setHeight(scaledHeight);
        
        NanoVGRenderer.INSTANCE.draw(vg -> {
            float fontSize = baseFontSize * 0.75f;
            int font = FontLoader.regular(fontSize);
            String text = setting.getDisplayName();
            
            NanoVGHelper.drawString(text, getX(), getY() + 1 * scale, font, fontSize, Color.WHITE);
            
            float btnWidth = 30 * scale;
            float btnHeight = 10 * scale;
            float btnX = getX() + getWidth() - btnWidth;
            float btnY = getY() - 6 * scale;
            
            Color bgColor = new Color(70, 70, 70);
            if (RenderUtil.isHovering(btnX, btnY, btnWidth, btnHeight, mouseX, mouseY)) {
                bgColor = new Color(100, 100, 100);
            }
            
            NanoVGHelper.drawRoundRect(btnX, btnY, btnWidth, btnHeight, 2 * scale, bgColor);
            String btnText = TranslationManager.get("ui.select", "Select");
            NanoVGHelper.drawCenteredString(btnText, btnX + btnWidth / 2, btnY + 2 * scale + fontSize / 2 - 1, font, fontSize, Color.WHITE);
        });
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        float btnWidth = 30 * scale;
        float btnHeight = 10 * scale;
        float btnX = getX() + getWidth() - btnWidth;
        // Use the same Y coordinate logic as in render()
        float btnY = getY() - 6 * scale;
        
        if (RenderUtil.isHovering(btnX, btnY, btnWidth, btnHeight, (float) mouseX, (float) mouseY) && mouseButton == 0) {
            Sakura.mc.setScreen(new dev.mzc.client.gui.clickgui.SakuraSelectionScreen(Sakura.mc.currentScreen, setting));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean isVisible() {
        return this.setting.isAvailable();
    }
}


