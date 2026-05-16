package dev.mzc.client.gui.clickgui.skeet;

import dev.mzc.client.module.Category;

/**
 * Maps client categories to badcache.ttf icon glyphs.
 * Glyph map (badcache.ttf):
 *   A = Headshot       B = Knife / Karambit    C = Sun / Visuals
 *   D = Moon           E = Sword / Sabre       F = Player silhouette
 *   G = Lightning      H = Brush / Colors      I = Crosshair / Aim
 *   J = Soldier
 */
public final class CategoryIcons {
    private CategoryIcons() {}

    public static String forCategory(Category cat) {
        return switch (cat) {
            case Combat -> "A";    // Headshot
            case Movement -> "G";  // Lightning
            case Player -> "F";    // Player
            case Render -> "C";    // Sun
            case Misc -> "H";      // Brush
            case Client -> "D";    // Moon
            case HVH -> "J";       // Soldier
            case Search -> "I";    // Crosshair
        };
    }
}
