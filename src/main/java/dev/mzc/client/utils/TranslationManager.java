package dev.mzc.client.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.impl.client.ClickGui;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class TranslationManager {
    private static final Map<ClickGui.Language, Map<String, String>> TRANSLATIONS = new EnumMap<>(ClickGui.Language.class);

    static {
        reload();
    }

    private TranslationManager() {
    }

    public static void reload() {
        TRANSLATIONS.clear();
        TRANSLATIONS.put(ClickGui.Language.English, new java.util.HashMap<>());
        TRANSLATIONS.put(ClickGui.Language.German, new java.util.HashMap<>());
        TRANSLATIONS.put(ClickGui.Language.Russian, new java.util.HashMap<>());

        loadLanguageFile(ClickGui.Language.English, "assets/sakura/lang/en_us.json");
        loadLanguageFile(ClickGui.Language.German, "assets/sakura/lang/de_DE.json");
        loadLanguageFile(ClickGui.Language.German, "assets/sakura/lang/de_de.json");
        loadLanguageFile(ClickGui.Language.Russian, "assets/sakura/lang/ru_RU.json");
        loadLanguageFile(ClickGui.Language.Russian, "assets/sakura/lang/ru_ru.json");
    }

    public static String get(String key) {
        return get(key, key);
    }

    public static String get(String key, String fallbackEnglish) {
        return get(key, ClickGui.language.get(), fallbackEnglish);
    }

    public static String get(String key, ClickGui.Language language, String fallbackEnglish) {
        String translated = TRANSLATIONS.getOrDefault(language, Collections.emptyMap()).get(key);
        if (translated != null && !translated.isEmpty()) {
            return translated;
        }

        String english = TRANSLATIONS.getOrDefault(ClickGui.Language.English, Collections.emptyMap()).get(key);
        if (english != null && !english.isEmpty()) {
            return english;
        }

        if (fallbackEnglish != null && !fallbackEnglish.isEmpty()) {
            return fallbackEnglish;
        }

        return key;
    }



    public static String categoryKey(Category category) {
        return "category." + normalizeKey(category.name());
    }

    public static String moduleKey(String englishName) {
        return "module." + normalizeKey(englishName);
    }

    public static String valueKey(String name) {
        return "value." + normalizeKey(name);
    }

    public static String enumKey(Enum<?> value) {
        Class<?> enumClass = value.getDeclaringClass();
        Class<?> owner = enumClass.getEnclosingClass();
        String ownerKey = owner == null ? "" : normalizeKey(owner.getSimpleName()) + ".";
        return "enum." + ownerKey + normalizeKey(enumClass.getSimpleName()) + "." + normalizeKey(value.name());
    }

    public static String normalizeKey(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }

        String normalized = raw
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "")
                .toLowerCase(Locale.ROOT);

        return normalized;
    }

    private static void loadLanguageFile(ClickGui.Language language, String path) {
        try (InputStream stream = TranslationManager.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                return;
            }

            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> target = TRANSLATIONS.get(language);
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                target.put(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (Exception ignored) {
        }
    }
}
