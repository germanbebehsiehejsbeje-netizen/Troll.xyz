package dev.mzc.client.utils.player;

import net.minecraft.text.Text;

import java.util.regex.Pattern;

public class PlayerUtil {
    // Pattern to detect if a string contains any letters
    private static final Pattern LETTER_PATTERN = Pattern.compile(".*[a-zA-Z].*");
    
    // Pattern for valid Minecraft usernames (3-16 characters, alphanumeric and underscore)
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");

    /**
     * Sanitizes display text by removing formatting codes and special characters
     */
    public static String sanitizeDisplayText(String text) {
        if (text == null) return "";
        // Remove Minecraft formatting codes (§ and &)
        return text.replaceAll("[§&][0-9a-fk-orK-OR]", "").trim();
    }

    /**
     * Sanitizes display text from Text object
     */
    public static String sanitizeDisplayText(Text text) {
        if (text == null) return "";
        return sanitizeDisplayText(text.getString());
    }

    /**
     * Checks if a string contains any letters
     */
    public static boolean hasLetters(String text) {
        if (text == null || text.isEmpty()) return false;
        return LETTER_PATTERN.matcher(text).matches();
    }

    /**
     * Validates if a name is a valid Minecraft username
     */
    public static boolean isValidName(String name) {
        if (name == null || name.isEmpty()) return false;
        return VALID_NAME_PATTERN.matcher(name).matches();
    }
}
