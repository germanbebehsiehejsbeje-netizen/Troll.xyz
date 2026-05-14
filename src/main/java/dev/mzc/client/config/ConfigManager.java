package dev.mzc.client.config;

import com.google.gson.*;
import dev.mzc.client.Sakura;
import dev.mzc.client.account.type.MinecraftAccount;
import dev.mzc.client.account.type.impl.CrackedAccount;
import dev.mzc.client.account.type.impl.MicrosoftAccount;
import dev.mzc.client.gui.clickgui.panel.CategoryPanel;
import dev.mzc.client.gui.hud.HudPanel;
import dev.mzc.client.manager.Managers;
import dev.mzc.client.module.HudModule;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.Value;
import dev.mzc.client.values.impl.*;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Path CONFIG_DIR = Paths.get("sakura-config");
    private static final Path MODULES_DIR = CONFIG_DIR.resolve("modules");
    private static final Path CONFIGS_DIR = CONFIG_DIR.resolve("configs");
    private static final Path CLICKGUI_FILE = CONFIG_DIR.resolve("clickgui.json");
    private static final Path ACCOUNTS_FILE = CONFIG_DIR.resolve("accounts.json");
    private static final Path ENCRYPTED_ACCOUNTS_FILE = CONFIG_DIR.resolve("accounts_enc.json");
    private static final Path FRIENDS_FILE = CONFIG_DIR.resolve("friends.json");
    private static final Path HOMES_FILE = CONFIG_DIR.resolve("homes.json");

    private String currentPassword = null;

    public ConfigManager() {
        createConfigDir();

        loadModules();
        loadClickGui();
        loadAccounts();
        loadFriends();
        loadHomes();
    }

    private void createConfigDir() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            if (!Files.exists(MODULES_DIR)) {
                Files.createDirectories(MODULES_DIR);
            }
            if (!Files.exists(CONFIGS_DIR)) {
                Files.createDirectories(CONFIGS_DIR);
            }
        } catch (IOException e) {
            Sakura.LOGGER.error("Failed to create config directory: {}", e.getMessage());
        }
    }

    public void saveDefaultConfig() {
        saveModules();
        saveClickGui();
        saveAccounts();
        saveFriends();
        saveHomes();
    }

    public boolean saveConfig(String name) {
        try {
            Path configDir = CONFIGS_DIR.resolve(name);
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }
            saveModules(configDir);
            return true;
        } catch (IOException e) {
            Sakura.LOGGER.error("Failed to save config {}: {}", name, e.getMessage());
            return false;
        }
    }

    public boolean loadConfig(String name) {
        Path configDir = CONFIGS_DIR.resolve(name);
        if (!Files.exists(configDir)) {
            return false;
        }
        loadModules(configDir);
        return true;
    }

    public void saveFriends() {
        try {
            JsonArray array = new JsonArray();
            for (String friend : Managers.FRIEND.getFriends()) {
                array.add(friend);
            }
            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(FRIENDS_FILE.toFile()), StandardCharsets.UTF_8)) {
                GSON.toJson(array, writer);
            }
        } catch (IOException e) {
            Sakura.LOGGER.error("Failed to save friends: {}", e.getMessage());
        }
    }

    public void loadFriends() {
        if (!Files.exists(FRIENDS_FILE)) return;
        try {
            JsonArray array = JsonParser.parseString(Files.readString(FRIENDS_FILE)).getAsJsonArray();
            Managers.FRIEND.clearFriends();
            for (JsonElement element : array) {
                Managers.FRIEND.addFriend(element.getAsString());
            }
        } catch (Exception e) {
            Sakura.LOGGER.error("Failed to load friends: {}", e.getMessage());
        }
    }

    public void saveHomes() {
        try {
            JsonObject root = new JsonObject();
            for (var serverEntry : Managers.HOME.getAllHomes().entrySet()) {
                JsonObject serverObj = new JsonObject();
                for (var homeEntry : serverEntry.getValue().entrySet()) {
                    var loc = homeEntry.getValue();
                    JsonObject o = new JsonObject();
                    o.addProperty("dimension", loc.dimension());
                    o.addProperty("x", loc.x());
                    o.addProperty("y", loc.y());
                    o.addProperty("z", loc.z());
                    o.addProperty("yaw", loc.yaw());
                    o.addProperty("pitch", loc.pitch());
                    o.addProperty("enabled", loc.enabled());
                    o.addProperty("beam", loc.beam());
                    o.addProperty("beamColor", loc.beamColor());
                    serverObj.add(homeEntry.getKey(), o);
                }
                root.add(serverEntry.getKey(), serverObj);
            }
            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(HOMES_FILE.toFile()), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            Sakura.LOGGER.error("Failed to save homes: {}", e.getMessage());
        }
    }

    public void loadHomes() {
        if (!Files.exists(HOMES_FILE)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(HOMES_FILE)).getAsJsonObject();
            Managers.HOME.beginLoad();
            Managers.HOME.clearHomes();
            boolean isOldFormat = false;
            for (String k : root.keySet()) {
                JsonElement el = root.get(k);
                if (el != null && el.isJsonObject()) {
                    JsonObject o = el.getAsJsonObject();
                    if (o.has("x") || o.has("y") || o.has("z")) {
                        isOldFormat = true;
                    }
                }
                break;
            }

            if (isOldFormat) {
                for (String name : root.keySet()) {
                    JsonObject o = root.getAsJsonObject(name);
                    String dim = o.has("dimension") ? o.get("dimension").getAsString() : "";
                    int x = o.has("x") ? o.get("x").getAsInt() : 0;
                    int y = o.has("y") ? o.get("y").getAsInt() : 0;
                    int z = o.has("z") ? o.get("z").getAsInt() : 0;
                    float yaw = o.has("yaw") ? o.get("yaw").getAsFloat() : 0f;
                    float pitch = o.has("pitch") ? o.get("pitch").getAsFloat() : 0f;
                    Managers.HOME.setHome("unknown", name, new dev.mzc.client.manager.impl.HomeManager.HomeLocation(dim, x, y, z, yaw, pitch, true, true, 0x66FFFFFF));
                }
            } else {
                for (String serverId : root.keySet()) {
                    JsonObject serverObj = root.getAsJsonObject(serverId);
                    for (String name : serverObj.keySet()) {
                        JsonObject o = serverObj.getAsJsonObject(name);
                        String dim = o.has("dimension") ? o.get("dimension").getAsString() : "";
                        int x = o.has("x") ? o.get("x").getAsInt() : 0;
                        int y = o.has("y") ? o.get("y").getAsInt() : 0;
                        int z = o.has("z") ? o.get("z").getAsInt() : 0;
                        float yaw = o.has("yaw") ? o.get("yaw").getAsFloat() : 0f;
                        float pitch = o.has("pitch") ? o.get("pitch").getAsFloat() : 0f;
                        boolean enabled = !o.has("enabled") || o.get("enabled").getAsBoolean();
                        boolean beam = !o.has("beam") || o.get("beam").getAsBoolean();
                        int beamColor = o.has("beamColor") ? o.get("beamColor").getAsInt() : 0x66FFFFFF;
                        Managers.HOME.setHome(serverId, name, new dev.mzc.client.manager.impl.HomeManager.HomeLocation(dim, x, y, z, yaw, pitch, enabled, beam, beamColor));
                    }
                }
            }
            Managers.HOME.endLoad();
        } catch (Exception e) {
            Sakura.LOGGER.error("Failed to load homes: {}", e.getMessage());
        }
    }

    public void saveAccounts() {
        try {
            JsonArray array = new JsonArray();
            for (final MinecraftAccount account : Managers.ACCOUNT.getAccounts()) {
                try {
                    array.add(account.toJSON());
                } catch (RuntimeException e) {
                    Sakura.LOGGER.error(e.getMessage());
                }
            }

            String jsonString = GSON.toJson(array);

            if (currentPassword != null) {
                // 加密保存
                try {
                    String encrypted = encrypt(jsonString, currentPassword);
                    Files.writeString(ENCRYPTED_ACCOUNTS_FILE, encrypted, StandardCharsets.UTF_8);

                    // 如果存在明文文件则删除
                    if (Files.exists(ACCOUNTS_FILE)) {
                        Files.delete(ACCOUNTS_FILE);
                    }
                } catch (Exception e) {
                    Sakura.LOGGER.error("Failed to encrypt accounts: {}", e.getMessage());
                }
            } else {
                // 明文保存
                try (Writer writer = new OutputStreamWriter(
                        new FileOutputStream(ACCOUNTS_FILE.toFile()), StandardCharsets.UTF_8)) {
                    writer.write(jsonString);
                }

                // 如果存在加密文件则删除
                if (Files.exists(ENCRYPTED_ACCOUNTS_FILE)) {
                    Files.delete(ENCRYPTED_ACCOUNTS_FILE);
                }
            }
        } catch (IOException e) {
            Sakura.LOGGER.error("Failed to save accounts: {}", e.getMessage());
        }
    }

    public void loadAccounts() {
        try {
            String content = null;

            if (Files.exists(ENCRYPTED_ACCOUNTS_FILE)) {
                if (currentPassword != null) {
                    try {
                        String encrypted = Files.readString(ENCRYPTED_ACCOUNTS_FILE, StandardCharsets.UTF_8);
                        content = decrypt(encrypted, currentPassword);
                    } catch (Exception e) {
                        Sakura.LOGGER.error("Failed to decrypt accounts: {}", e.getMessage());
                        return;
                    }
                } else {
                    Sakura.LOGGER.info("Encrypted accounts file found, waiting for password.");
                    return;
                }
            } else if (Files.exists(ACCOUNTS_FILE)) {
                content = Files.readString(ACCOUNTS_FILE, StandardCharsets.UTF_8);
            }

            if (content == null) return;

            JsonArray json = JsonParser.parseString(content).getAsJsonArray();

            Managers.ACCOUNT.getAccounts().clear();
            for (JsonElement element : json) {
                if (!(element instanceof JsonObject object)) {
                    continue;
                }

                MinecraftAccount account = null;
                if (object.has("email") && object.has("password")) {
                    account = new MicrosoftAccount(object.get("email").getAsString(),
                            object.get("password").getAsString());
                    if (object.has("username")) {
                        ((MicrosoftAccount) account).setUsername(object.get("username").getAsString());
                    }
                } else if (object.has("token")) {
                    if (!object.has("username")) {
                        Sakura.LOGGER.error("Browser account does not have a username set?");
                        continue;
                    }
                    account = new MicrosoftAccount(object.get("token").getAsString());
                    ((MicrosoftAccount) account).setUsername(object.get("username").getAsString());
                } else {
                    if (object.has("username")) {
                        account = new CrackedAccount(object.get("username").getAsString());
                    }
                }

                if (account != null) {
                    Managers.ACCOUNT.register(account, false);
                } else {
                    Sakura.LOGGER.error("Could not parse account JSON.\nRaw: {}", object.toString());
                }
            }
        } catch (IOException | IllegalStateException e) {
            Sakura.LOGGER.error("Failed to load accounts: {}", e.getMessage());
        }
    }

    public void setEncryptionPassword(String password) {
        this.currentPassword = password;
        if (password != null) {
            if (Files.exists(ENCRYPTED_ACCOUNTS_FILE) && Managers.ACCOUNT.getAccounts().isEmpty()) {
                loadAccounts();
            } else {
                saveAccounts();
            }
        } else {
            saveAccounts();
        }
    }

    public boolean isEncrypted() {
        return Files.exists(ENCRYPTED_ACCOUNTS_FILE) || currentPassword != null;
    }

    private static String encrypt(String data, String password) throws Exception {
        SecretKeySpec key = generateKey(password);
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    private static String decrypt(String encryptedData, String password) throws Exception {
        SecretKeySpec key = generateKey(password);
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decoded = Base64.getDecoder().decode(encryptedData);
        byte[] decrypted = cipher.doFinal(decoded);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private static SecretKeySpec generateKey(String password) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] key = password.getBytes(StandardCharsets.UTF_8);
        key = sha.digest(key);
        key = Arrays.copyOf(key, 16);
        return new SecretKeySpec(key, "AES");
    }

    private void saveModules() {
        saveModules(MODULES_DIR);
    }

    private void saveModules(Path dir) {
        for (Module module : Sakura.MODULES.getAllModules()) {
            saveModule(module, dir);
        }
    }

    private void saveModule(Module module, Path dir) {
        try {
            Path moduleFile = dir.resolve(module.getEnglishName() + ".json");
            JsonObject moduleObject = new JsonObject();

            moduleObject.addProperty("enabled", module.isEnabled());
            moduleObject.addProperty("keybind", module.getKey());
            moduleObject.addProperty("bindMode", module.getBindMode().name());
            moduleObject.addProperty("suffix", module.getSuffix());

            if (module instanceof HudModule hudModule) {
                moduleObject.addProperty("hudX", hudModule.getX());
                moduleObject.addProperty("hudY", hudModule.getY());
            }

            JsonObject valuesObject = new JsonObject();
            for (Value<?> value : module.getValues()) {
                valuesObject.add(value.getName(), saveValue(value));
            }
            moduleObject.add("values", valuesObject);

            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(moduleFile.toFile()), StandardCharsets.UTF_8)) {
                GSON.toJson(moduleObject, writer);
            }
        } catch (IOException e) {
            Sakura.LOGGER.error("Failed to save module {}: {}", module.getEnglishName(), e.getMessage());
        }
    }

    private void loadModules() {
        loadModules(MODULES_DIR);
    }

    private void loadModules(Path dir) {
        try {
            if (!Files.exists(dir)) return;

            Files.list(dir)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        String moduleName = path.getFileName().toString();
                        moduleName = moduleName.substring(0, moduleName.length() - 5);
                        Module module = Sakura.MODULES.getModuleByString(moduleName);
                        if (module != null) {
                            loadModule(module, path);
                        }
                    });
        } catch (IOException e) {
            Sakura.LOGGER.error("Failed to load modules: {}", e.getMessage());
        }
    }

    private void loadModule(Module module, Path path) {
        try {
            JsonObject moduleObject = JsonParser.parseString(Files.readString(path)).getAsJsonObject();

            if (moduleObject.has("enabled")) {
                module.setState(moduleObject.get("enabled").getAsBoolean());
            }
            if (moduleObject.has("keybind")) {
                module.setKey(moduleObject.get("keybind").getAsInt());
            }
            if (moduleObject.has("bindMode")) {
                try {
                    module.setBindMode(Module.BindMode.valueOf(moduleObject.get("bindMode").getAsString()));
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (moduleObject.has("suffix")) {
                module.setSuffix(moduleObject.get("suffix").getAsString());
            }

            if (module instanceof HudModule hudModule) {
                if (moduleObject.has("hudX")) {
                    hudModule.setX(moduleObject.get("hudX").getAsFloat());
                }
                if (moduleObject.has("hudY")) {
                    hudModule.setY(moduleObject.get("hudY").getAsFloat());
                }
            }

            if (moduleObject.has("values")) {
                JsonObject valuesObject = moduleObject.getAsJsonObject("values");
                for (Value<?> value : module.getValues()) {
                    if (valuesObject.has(value.getName())) {
                        JsonElement valueElement = valuesObject.get(value.getName());
                        loadValue(value, valueElement);
                    }
                }
            }
        } catch (IOException e) {
            Sakura.LOGGER.error("Failed to load module {}: {}", module.getEnglishName(), e.getMessage());
        }
    }

    public void saveClickGui() {
        try {
            JsonObject clickGuiObject = new JsonObject();
            clickGuiObject.addProperty("uiHidden", Sakura.UI_HIDDEN);
            JsonArray hiddenModulesArray = new JsonArray();
            for (String moduleName : Sakura.HIDDEN_MODULES) {
                hiddenModulesArray.add(moduleName);
            }
            clickGuiObject.add("hiddenModules", hiddenModulesArray);

            JsonArray panelsArray = new JsonArray();
            if (Sakura.CLICKGUI != null) {
                for (CategoryPanel panel : Sakura.CLICKGUI.getPanels()) {
                    JsonObject panelObject = new JsonObject();
                    panelObject.addProperty("category", panel.getCategory().name());
                    panelObject.addProperty("x", panel.getX());
                    panelObject.addProperty("y", panel.getY());
                    panelObject.addProperty("opened", panel.isOpened());
                    panelsArray.add(panelObject);
                }
            }
            clickGuiObject.add("panels", panelsArray);

            if (Sakura.HUDEDITOR != null) {
                HudPanel hudPanel = Sakura.HUDEDITOR.getHudPanel();
                if (hudPanel != null) {
                    JsonObject hudPanelObject = new JsonObject();
                    hudPanelObject.addProperty("x", hudPanel.getX());
                    hudPanelObject.addProperty("y", hudPanel.getY());
                    clickGuiObject.add("hudPanel", hudPanelObject);
                }
            }

            try {
                JsonObject defaultPreset = dev.mzc.client.module.impl.client.ClickGui.exportDefaultPresetJson();
                if (defaultPreset != null) {
                    clickGuiObject.add("defaultPreset", defaultPreset);
                }
            } catch (Throwable ignored) {
            }

            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(CLICKGUI_FILE.toFile()), StandardCharsets.UTF_8)) {
                GSON.toJson(clickGuiObject, writer);
            }
            System.out.println("ClickGui saved to: " + CLICKGUI_FILE);
        } catch (IOException e) {
            System.err.println("Failed to save clickgui: " + e.getMessage());
        }
    }

    public void loadClickGui() {
        try {
            if (!Files.exists(CLICKGUI_FILE)) return;

            String content = new String(Files.readAllBytes(CLICKGUI_FILE), StandardCharsets.UTF_8);
            JsonObject clickGuiObject = JsonParser.parseString(content).getAsJsonObject();

            if (clickGuiObject.has("uiHidden")) {
                Sakura.UI_HIDDEN = clickGuiObject.get("uiHidden").getAsBoolean();
            }
            Sakura.HIDDEN_MODULES.clear();
            if (clickGuiObject.has("hiddenModules")) {
                JsonElement hiddenModulesEl = clickGuiObject.get("hiddenModules");
                if (hiddenModulesEl != null && hiddenModulesEl.isJsonArray()) {
                    JsonArray hiddenModulesArray = hiddenModulesEl.getAsJsonArray();
                    for (JsonElement element : hiddenModulesArray) {
                        if (element == null || element.isJsonNull()) continue;
                        Sakura.HIDDEN_MODULES.add(element.getAsString());
                    }
                }
            }

            if (clickGuiObject.has("panels") && Sakura.CLICKGUI != null) {
                JsonArray panelsArray = clickGuiObject.getAsJsonArray("panels");
                for (JsonElement element : panelsArray) {
                    JsonObject panelObject = element.getAsJsonObject();
                    String categoryName = panelObject.get("category").getAsString();

                    for (CategoryPanel panel : Sakura.CLICKGUI.getPanels()) {
                        if (panel.getCategory().name().equals(categoryName)) {
                            if (panelObject.has("x")) panel.setX(panelObject.get("x").getAsFloat());
                            if (panelObject.has("y")) panel.setY(panelObject.get("y").getAsFloat());
                            if (panelObject.has("opened")) panel.setOpened(panelObject.get("opened").getAsBoolean());
                            break;
                        }
                    }
                }
            }

            if (clickGuiObject.has("hudPanel") && Sakura.HUDEDITOR != null) {
                JsonObject hudPanelObject = clickGuiObject.getAsJsonObject("hudPanel");
                HudPanel hudPanel = Sakura.HUDEDITOR.getHudPanel();
                if (hudPanel != null) {
                    if (hudPanelObject.has("x")) hudPanel.setX(hudPanelObject.get("x").getAsFloat());
                    if (hudPanelObject.has("y")) hudPanel.setY(hudPanelObject.get("y").getAsFloat());
                }
            }

            if (clickGuiObject.has("defaultPreset")) {
                try {
                    JsonObject presetObj = clickGuiObject.getAsJsonObject("defaultPreset");
                    dev.mzc.client.module.impl.client.ClickGui.importDefaultPresetJson(presetObj);
                } catch (Throwable ignored) {
                }
            }
        } catch (IOException e) {
            Sakura.LOGGER.error("Failed to load clickgui: {}", e.getMessage());
        }
    }

    private JsonElement saveValue(Value<?> value) {
        Object val = value.get();

        if (value instanceof BoolValue) {
            return new JsonPrimitive((Boolean) val);
        } else if (value instanceof RangeValue<?> rangeValue) {
            JsonObject rangeObject = new JsonObject();
            Number rangeMin = rangeValue.getMinValue();
            Number rangeMax = rangeValue.getMaxValue();
            if (rangeMin instanceof Integer) {
                rangeObject.addProperty("min", rangeMin.intValue());
                rangeObject.addProperty("max", rangeMax.intValue());
            } else if (rangeMin instanceof Float) {
                rangeObject.addProperty("min", rangeMin.floatValue());
                rangeObject.addProperty("max", rangeMax.floatValue());
            } else if (rangeMin instanceof Long) {
                rangeObject.addProperty("min", rangeMin.longValue());
                rangeObject.addProperty("max", rangeMax.longValue());
            } else {
                rangeObject.addProperty("min", rangeMin.doubleValue());
                rangeObject.addProperty("max", rangeMax.doubleValue());
            }
            return rangeObject;
        } else if (value instanceof NumberValue<?> numberValue) {
            if (numberValue.get() instanceof Integer) {
                return new JsonPrimitive(numberValue.get().intValue());
            } else if (numberValue.get() instanceof Float) {
                return new JsonPrimitive(numberValue.get().floatValue());
            } else {
                return new JsonPrimitive(numberValue.get().doubleValue());
            }
        } else if (value instanceof StringValue) {
            return new JsonPrimitive(((StringValue) value).get());
        } else if (value instanceof EnumValue) {
            return new JsonPrimitive(((Enum<?>) val).name());
        } else if (value instanceof ListValue<?> listValue) {
            JsonArray array = new JsonArray();
            for (Object obj : listValue.get()) {
                if (obj instanceof net.minecraft.block.Block block) {
                     array.add(net.minecraft.registry.Registries.BLOCK.getId(block).toString());
                } else if (obj instanceof net.minecraft.entity.EntityType<?> entityType) {
                     array.add(net.minecraft.registry.Registries.ENTITY_TYPE.getId(entityType).toString());
                } else if (obj instanceof net.minecraft.item.Item item) {
                     array.add(net.minecraft.registry.Registries.ITEM.getId(item).toString());
                }
            }
            return array;
        } else if (value instanceof ColorValue colorValue) {
            JsonObject colorObject = new JsonObject();
            colorObject.addProperty("hue", colorValue.getHue());
            colorObject.addProperty("saturation", colorValue.getSaturation());
            colorObject.addProperty("brightness", colorValue.getBrightness());
            colorObject.addProperty("alpha", colorValue.getAlpha());
            colorObject.addProperty("rainbow", colorValue.isRainbow());
            colorObject.addProperty("expand", colorValue.isExpand());
            return colorObject;
        } else if (value instanceof MultiBoolValue multiBoolValue) {
            JsonObject multiObject = new JsonObject();
            for (int i = 0; i < multiBoolValue.getValues().size(); i++) {
                BoolValue boolValue = multiBoolValue.getValues().get(i);
                multiObject.addProperty(boolValue.getName(), boolValue.get());
            }
            return multiObject;
        }

        return JsonNull.INSTANCE;
    }

    @SuppressWarnings("unchecked")
    private void loadValue(Value<?> value, JsonElement valueElement) {
        try {
            if (value instanceof BoolValue && valueElement.isJsonPrimitive()) {
                ((Value<Boolean>) value).set(valueElement.getAsBoolean());
            } else if (value instanceof RangeValue<?> rangeValue) {
                if (valueElement.isJsonObject()) {
                    JsonObject rangeObject = valueElement.getAsJsonObject();
                    JsonElement minElement = rangeObject.get("min");
                    JsonElement maxElement = rangeObject.get("max");
                    if (minElement != null && maxElement != null) {
                        if (rangeValue.getMinValue() instanceof Integer) {
                            ((RangeValue<Integer>) rangeValue).set(minElement.getAsInt(), maxElement.getAsInt());
                        } else if (rangeValue.getMinValue() instanceof Float) {
                            ((RangeValue<Float>) rangeValue).set(minElement.getAsFloat(), maxElement.getAsFloat());
                        } else if (rangeValue.getMinValue() instanceof Long) {
                            ((RangeValue<Long>) rangeValue).set(minElement.getAsLong(), maxElement.getAsLong());
                        } else {
                            ((RangeValue<Double>) rangeValue).set(minElement.getAsDouble(), maxElement.getAsDouble());
                        }
                    }
                } else if (valueElement.isJsonPrimitive()) {
                    // Compatibility: if old configs contain a single number, use it for both bounds.
                    if (rangeValue.getMinValue() instanceof Integer) {
                        int single = valueElement.getAsInt();
                        ((RangeValue<Integer>) rangeValue).set(single, single);
                    } else if (rangeValue.getMinValue() instanceof Float) {
                        float single = valueElement.getAsFloat();
                        ((RangeValue<Float>) rangeValue).set(single, single);
                    } else if (rangeValue.getMinValue() instanceof Long) {
                        long single = valueElement.getAsLong();
                        ((RangeValue<Long>) rangeValue).set(single, single);
                    } else {
                        double single = valueElement.getAsDouble();
                        ((RangeValue<Double>) rangeValue).set(single, single);
                    }
                }
            } else if (value instanceof NumberValue<?> numberValue && valueElement.isJsonPrimitive()) {
                if (numberValue.get() instanceof Integer) {
                    ((NumberValue<Integer>) numberValue).set(valueElement.getAsInt());
                } else if (numberValue.get() instanceof Float) {
                    ((NumberValue<Float>) numberValue).set(valueElement.getAsFloat());
                } else {
                    ((NumberValue<Double>) numberValue).set(valueElement.getAsDouble());
                }
            } else if (value instanceof StringValue && valueElement.isJsonPrimitive()) {
                ((StringValue) value).setText(valueElement.getAsString());
            } else if (value instanceof EnumValue && valueElement.isJsonPrimitive()) {
                ((EnumValue<?>) value).setMode(valueElement.getAsString());
            } else if (value instanceof ListValue<?> listValue && valueElement.isJsonArray()) {
                JsonArray array = valueElement.getAsJsonArray();
                ListValue<Object> castedList = (ListValue<Object>) listValue;
                castedList.clear();
                for (JsonElement element : array) {
                    String id = element.getAsString();
                    
                    if (listValue.getType() == ListValue.Type.BLOCK) {
                        net.minecraft.block.Block block = net.minecraft.registry.Registries.BLOCK.get(net.minecraft.util.Identifier.of(id));
                        if (block != net.minecraft.block.Blocks.AIR || id.equals("minecraft:air")) {
                             castedList.add(block);
                        }
                    } else if (listValue.getType() == ListValue.Type.ENTITY) {
                        net.minecraft.entity.EntityType<?> entityType = net.minecraft.registry.Registries.ENTITY_TYPE.get(net.minecraft.util.Identifier.of(id));
                        if (entityType != net.minecraft.entity.EntityType.PIG || id.equals("minecraft:pig")) { // Default is PIG if not found, usually checks validation
                             castedList.add(entityType);
                        }
                    } else if (listValue.getType() == ListValue.Type.ITEM) {
                        net.minecraft.item.Item item = net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.of(id));
                        if (item != net.minecraft.item.Items.AIR || id.equals("minecraft:air")) {
                             castedList.add(item);
                        }
                    }
                }
            } else if (value instanceof ColorValue && valueElement.isJsonObject()) {
                JsonObject colorObject = valueElement.getAsJsonObject();
                ColorValue colorValue = (ColorValue) value;

                if (colorObject.has("hue")) colorValue.setHue(colorObject.get("hue").getAsFloat());
                if (colorObject.has("saturation")) colorValue.setSaturation(colorObject.get("saturation").getAsFloat());
                if (colorObject.has("brightness")) colorValue.setBrightness(colorObject.get("brightness").getAsFloat());
                if (colorObject.has("alpha")) colorValue.setAlpha(colorObject.get("alpha").getAsFloat());
                if (colorObject.has("rainbow")) colorValue.setRainbow(colorObject.get("rainbow").getAsBoolean());
                if (colorObject.has("expand")) colorValue.setExpand(colorObject.get("expand").getAsBoolean());
            } else if (value instanceof MultiBoolValue multiBoolValue && valueElement.isJsonObject()) {
                JsonObject multiObject = valueElement.getAsJsonObject();
                for (String optionName : multiObject.keySet()) {
                    multiBoolValue.set(optionName, multiObject.get(optionName).getAsBoolean());
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load value " + value.getName() + ": " + e.getMessage());
        }
    }

    public List<String> getConfigList() {
        List<String> configs = new ArrayList<>();
        try {
            if (!Files.exists(CONFIGS_DIR)) return configs;
            Files.list(CONFIGS_DIR)
                    .filter(Files::isDirectory)
                    .forEach(path -> configs.add(path.getFileName().toString()));
            configs.sort(String.CASE_INSENSITIVE_ORDER);
        } catch (IOException e) {
            System.err.println("Failed to list configs: " + e.getMessage());
        }
        return configs;
    }

    public void savePrefix(String prefix) {
        try {
            Path prefixFile = CONFIG_DIR.resolve("prefix.json");
            JsonObject prefixObject = new JsonObject();
            prefixObject.addProperty("prefix", prefix);

            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(prefixFile.toFile()), StandardCharsets.UTF_8)) {
                GSON.toJson(prefixObject, writer);
            }
        } catch (IOException e) {
            Sakura.LOGGER.error("Failed to save prefix: {}", e.getMessage());
        }
    }

    public String loadPrefix() {
        try {
            Path prefixFile = CONFIG_DIR.resolve("prefix.json");
            if (!Files.exists(prefixFile)) return ".";

            String content = new String(Files.readAllBytes(prefixFile), StandardCharsets.UTF_8);
            JsonObject prefixObject = JsonParser.parseString(content).getAsJsonObject();

            if (prefixObject.has("prefix")) {
                return prefixObject.get("prefix").getAsString();
            }
        } catch (IOException e) {
            Sakura.LOGGER.error("Failed to load prefix: {}", e.getMessage());
        }
        return ".";
    }
}
