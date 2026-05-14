package dev.mzc.client.gui.mainmenu;

import dev.mzc.client.shaders.MainMenuShader;

public class ShaderButton extends MenuButton {
    public ShaderButton(int x, int y, int width, int height) {
        super(x, y, width, height, "", null, true);
        updateText();
    }

    public void updateText() {
        this.text = "Shader: " + MainMenuShader.getSharedInstance().getCurrentShaderType().getDisplayName();
    }

    public void nextShader() {
        MainMenuShader.getSharedInstance().nextShader();
        updateText();
    }

    public void previousShader() {
        MainMenuShader.getSharedInstance().previousShader();
        updateText();
    }

    @Override
    public void onClick() {
        nextShader();
    }
}
