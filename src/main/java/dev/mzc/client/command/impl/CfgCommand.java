package dev.mzc.client.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.mzc.client.Sakura;
import dev.mzc.client.command.Command;
import dev.mzc.client.utils.client.ChatUtil;
import net.minecraft.command.CommandSource;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CfgCommand extends Command {
    public CfgCommand() {
        super("Cfg", "Config management", literal("cfg"));
    }

    @Override
    public void buildCommand(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("dir")
                .executes(c -> {
                    try {
                        Path configDir = dev.mzc.client.config.ConfigManager.CONFIG_DIR;
                        if (!Files.exists(configDir)) {
                            Files.createDirectories(configDir);
                        }
                        Desktop.getDesktop().open(configDir.toFile());
                        ChatUtil.addChatMessage("Opened config folder: trollhack");
                    } catch (IOException e) {
                        ChatUtil.addChatMessage("Failed to open config folder: " + e.getMessage());
                    }
                    return 1;
                }))
                .then(literal("save")
                        .then(argument("name", StringArgumentType.string())
                                .executes(c -> {
                                    String name = StringArgumentType.getString(c, "name");
                                    boolean success = Sakura.CONFIG.saveConfig(name);
                                    if (success) {
                                        ChatUtil.addChatMessage("Config \"" + name + "\" saved successfully!");
                                    } else {
                                        ChatUtil.addChatMessage("Failed to save config \"" + name + "\".");
                                    }
                                    return success ? 1 : 0;
                                }))
                        .executes(c -> {
                            ChatUtil.addChatMessage("Usage: .cfg save <name>");
                            return 1;
                        }))
                .then(literal("load")
                        .then(argument("name", StringArgumentType.string())
                                .executes(c -> {
                                    String name = StringArgumentType.getString(c, "name");
                                    boolean success = Sakura.CONFIG.loadConfig(name);
                                    if (success) {
                                        ChatUtil.addChatMessage("Config \"" + name + "\" loaded successfully!");
                                        Sakura.CONFIG.saveDefaultConfig();
                                    } else {
                                        ChatUtil.addChatMessage("Config \"" + name + "\" not found.");
                                    }
                                    return success ? 1 : 0;
                                }))
                        .executes(c -> {
                            ChatUtil.addChatMessage("Usage: .cfg load <name>");
                            return 1;
                        }))
                .then(literal("list")
                        .executes(c -> {
                            java.util.List<String> configs = Sakura.CONFIG.getConfigList();
                            if (configs.isEmpty()) {
                                ChatUtil.addChatMessage("No saved configs found.");
                            } else {
                                ChatUtil.addChatMessage("Saved configs:");
                                for (String configName : configs) {
                                    ChatUtil.addChatMessage(" - " + configName);
                                }
                            }
                            return 1;
                        }))
                .then(literal("delete")
                        .then(argument("name", StringArgumentType.string())
                                .executes(c -> {
                                    String name = StringArgumentType.getString(c, "name");
                                    Path configDir = dev.mzc.client.config.ConfigManager.CONFIGS_DIR.resolve(name);
                                    try {
                                        if (Files.exists(configDir)) {
                                            Files.walk(configDir)
                                                    .sorted(java.util.Comparator.reverseOrder())
                                                    .forEach(path -> {
                                                        try {
                                                            Files.delete(path);
                                                        } catch (IOException ignored) {
                                                        }
                                                    });
                                            ChatUtil.addChatMessage("Config \"" + name + "\" deleted successfully!");
                                            return 1;
                                        } else {
                                            ChatUtil.addChatMessage("Config \"" + name + "\" not found.");
                                            return 0;
                                        }
                                    } catch (IOException e) {
                                        ChatUtil.addChatMessage("Failed to delete config: " + e.getMessage());
                                        return 0;
                                    }
                                }))
                        .executes(c -> {
                            ChatUtil.addChatMessage("Usage: .cfg delete <name>");
                            return 1;
                        }))
                .executes(c -> {
                    ChatUtil.addChatMessage("CFG Commands:");
                    ChatUtil.addChatMessage(" .cfg dir - Open config folder");
                    ChatUtil.addChatMessage(" .cfg save <name> - Save current config");
                    ChatUtil.addChatMessage(" .cfg load <name> - Load a config");
                    ChatUtil.addChatMessage(" .cfg list - List all configs");
                    ChatUtil.addChatMessage(" .cfg delete <name> - Delete a config");
                    return 1;
                });
    }
}
