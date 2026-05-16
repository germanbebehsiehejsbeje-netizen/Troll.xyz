package dev.mzc.client.nanovg.font;

public class FontLoader {
    public static int regular(float size) {
        return FontManager.fontWithCJK("regular.otf", size);
    }

    public static int bold(float size) {
        return FontManager.fontWithCJK("regular_bold.otf", size);
    }

    public static int medium(float size) {
        return FontManager.fontWithCJK("regular_medium.otf", size);
    }

    public static int greycliffSemi(float size) {
        return FontManager.fontWithCJK("regular_semi.otf", size);
    }

    public static int solid(float size) {
        return FontManager.font("solid.ttf", size);
    }

    public static int icons(float size) {
        return FontManager.font("icon.ttf", size);
    }

    public static int proggyTiny(float size) {
        return FontManager.font("ProggyTiny.ttf", size);
    }

    /**
     * Skeet-style icon font. Each letter A-J renders an icon:
     * A=Headshot, B=Knife, C=Sun (visuals), D=Moon, E=Sword (combat),
     * F=Player, G=Lightning, H=Brush (colors), I=Crosshair, J=Soldier
     */
    public static int badcache(float size) {
        return FontManager.font("badcache.ttf", size);
    }

    public static int cjk(float size) {
        return FontManager.font("kuriyama.ttf", size);
    }
}
