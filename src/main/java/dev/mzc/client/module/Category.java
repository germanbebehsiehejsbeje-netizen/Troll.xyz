package dev.mzc.client.module;

import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.utils.TranslationManager;

public enum Category {
    Combat("A"),
    Movement("C"),
    Player("B"),
    Render("M"),
    Misc("E"),
    Client("D");

    public final String icon;

    Category(String icon) {
        this.icon = icon;
    }

    public String getName() {
        if (ClickGui.language.get() == ClickGui.Language.English) return name();
        return TranslationManager.get(TranslationManager.categoryKey(this), name());
    }
}
