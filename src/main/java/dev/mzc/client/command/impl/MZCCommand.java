package dev.mzc.client.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.mzc.client.Sakura;
import dev.mzc.client.command.Command;
import dev.mzc.client.command.ModuleArgumentType;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.client.ChatUtil;
import dev.mzc.client.utils.client.KeyUtil;
import net.minecraft.client.util.InputUtil;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.lwjgl.glfw.GLFW;

import net.minecraft.world.GameMode;

import dev.mzc.client.manager.Managers;

import dev.mzc.client.module.HudModule;
import dev.mzc.client.module.impl.render.AntiVanish;
import dev.mzc.client.module.impl.render.AttackEffect;
import dev.mzc.client.module.impl.render.BlockESP;
import dev.mzc.client.module.impl.render.BlockOutline;
import dev.mzc.client.module.impl.render.Hat;
import dev.mzc.client.module.impl.render.JumpCircles;
import dev.mzc.client.module.impl.render.KillEffect;
import dev.mzc.client.module.impl.render.MiningAnimation;
import dev.mzc.client.module.impl.misc.NameProtect;
import dev.mzc.client.module.impl.render.NameTags;
import dev.mzc.client.module.impl.render.Trail;
import dev.mzc.client.module.impl.render.ViewModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MZCCommand extends Command {
    private static GameMode originalGameMode = null;
    
    // We store the class names because referencing the classes directly in the static list 
    // before ModuleManager is initialized might cause issues if they haven't been loaded,
    // though here it's likely fine. But to be safe and cleaner:
    private static final List<Class<? extends Module>> RENDER_MODULES_TO_HIDE = new ArrayList<>();

    static {
        RENDER_MODULES_TO_HIDE.add(Hat.class);
        RENDER_MODULES_TO_HIDE.add(JumpCircles.class);
        RENDER_MODULES_TO_HIDE.add(Trail.class);
        RENDER_MODULES_TO_HIDE.add(KillEffect.class);
        RENDER_MODULES_TO_HIDE.add(NameTags.class);
        RENDER_MODULES_TO_HIDE.add(ViewModel.class);
        RENDER_MODULES_TO_HIDE.add(AttackEffect.class);
        RENDER_MODULES_TO_HIDE.add(BlockESP.class);
        RENDER_MODULES_TO_HIDE.add(BlockOutline.class);
        RENDER_MODULES_TO_HIDE.add(AntiVanish.class);
        RENDER_MODULES_TO_HIDE.add(MiningAnimation.class);
        RENDER_MODULES_TO_HIDE.add(NameProtect.class);
    }

    public MZCCommand() {
        super("MZC", "MZC main command", literal("MZC", "mzc"));
    }

    public static void hideHudModules() {
        Sakura.UI_HIDDEN = true;
        Sakura.HIDDEN_MODULES.clear();
        for (Module module : Sakura.MODULES.getAllModules()) {
            if (module.isEnabled() && isModuleToHide(module)) {
                Sakura.HIDDEN_MODULES.add(module.getEnglishName());
                module.setState(false);
            }
        }
    }

    private static boolean isModuleToHide(Module module) {
        if (module instanceof HudModule) return true;
        for (Class<? extends Module> clazz : RENDER_MODULES_TO_HIDE) {
            if (clazz.isInstance(module)) return true;
        }
        return false;
    }

    public static void showHudModules() {
        Sakura.UI_HIDDEN = false;
        for (String moduleName : Sakura.HIDDEN_MODULES) {
            Module module = Sakura.MODULES.getModule(moduleName);
            if (module != null && !module.isEnabled()) {
                module.setState(true);
            }
        }
        Sakura.HIDDEN_MODULES.clear();
    }

    private void updateOriginalGameMode() {
        if (originalGameMode == null) {
            originalGameMode = mc.interactionManager.getCurrentGameMode();
        }
    }

    @Override
    public void buildCommand(LiteralArgumentBuilder<CommandSource> builder) {
        builder
            .then(literal("gamemode")
                .then(literal("creative")
                    .executes(c -> {
                        updateOriginalGameMode();
                        mc.interactionManager.setGameMode(GameMode.CREATIVE);
                        ChatUtil.addChatMessage("Set client-side gamemode to §aCreative§f.");
                        return 1;
                    }))
                .then(literal("survival")
                    .executes(c -> {
                        updateOriginalGameMode();
                        mc.interactionManager.setGameMode(GameMode.SURVIVAL);
                        ChatUtil.addChatMessage("Set client-side gamemode to §aSurvival§f.");
                        return 1;
                    }))
                .then(literal("adventure")
                    .executes(c -> {
                        updateOriginalGameMode();
                        mc.interactionManager.setGameMode(GameMode.ADVENTURE);
                        ChatUtil.addChatMessage("Set client-side gamemode to §aAdventure§f.");
                        return 1;
                    }))
                .then(literal("spectator")
                    .executes(c -> {
                        updateOriginalGameMode();
                        mc.interactionManager.setGameMode(GameMode.SPECTATOR);
                        ChatUtil.addChatMessage("Set client-side gamemode to §aSpectator§f.");
                        return 1;
                    }))
                .then(literal("default")
                    .executes(c -> {
                        if (originalGameMode != null) {
                            mc.interactionManager.setGameMode(originalGameMode);
                            ChatUtil.addChatMessage("Reverted client-side gamemode to §a" + originalGameMode.getId() + "§f.");
                            originalGameMode = null;
                        } else {
                            ChatUtil.addChatMessage("§cNo previous gamemode stored. Defaulting to Survival.");
                            mc.interactionManager.setGameMode(GameMode.SURVIVAL);
                        }
                        return 1;
                    })))
            .then(literal("toggle")
                .then(argument("module", ModuleArgumentType.module())
                    .executes(c -> {
                        Module module = ModuleArgumentType.getModule(c, "module");
                        module.toggle();
                        ChatUtil.addChatMessage(module.getEnglishName() + " is now " +
                                (module.isEnabled() ? "§aenabled" : "§cdisabled") + "§f.");
                        return 1;
                    })
                    .then(literal("on")
                        .executes(c -> {
                            Module module = ModuleArgumentType.getModule(c, "module");
                            if (!module.isEnabled()) {
                                module.toggle();
                                ChatUtil.addChatMessage(module.getEnglishName() + " is now §aenabled§f.");
                            } else {
                                ChatUtil.addChatMessage(module.getEnglishName() + " is already §aenabled§f.");
                            }
                            return 1;
                        }))
                    .then(literal("off")
                        .executes(c -> {
                            Module module = ModuleArgumentType.getModule(c, "module");
                            if (module.isEnabled()) {
                                module.toggle();
                                ChatUtil.addChatMessage(module.getEnglishName() + " is now §cdisabled§f.");
                            } else {
                                ChatUtil.addChatMessage(module.getEnglishName() + " is already §cdisabled§f.");
                            }
                            return 1;
                        }))))
            .then(literal("config")
                .then(literal("save")
                    .then(argument("name", StringArgumentType.string())
                        .executes(c -> {
                            String name = StringArgumentType.getString(c, "name");
                            if (Sakura.CONFIG.saveConfig(name)) {
                                ChatUtil.addChatMessage("Saved config §a" + name + "§f.");
                            } else {
                                ChatUtil.addChatMessage("§cFailed to save config " + name + ".");
                            }
                            return 1;
                        })))
                .then(literal("load")
                    .then(argument("name", StringArgumentType.string())
                        .suggests((context, suggestionsBuilder) -> CommandSource.suggestMatching(Sakura.CONFIG.getConfigList(), suggestionsBuilder))
                        .executes(c -> {
                            String name = StringArgumentType.getString(c, "name");
                            if (Sakura.CONFIG.loadConfig(name)) {
                                ChatUtil.addChatMessage("Loaded config §a" + name + "§f.");
                            } else {
                                ChatUtil.addChatMessage("§cFailed to load config " + name + " (not found?).");
                            }
                            return 1;
                        })))
                .then(literal("list")
                    .executes(c -> {
                        List<String> configs = Sakura.CONFIG.getConfigList();
                        if (configs.isEmpty()) {
                            ChatUtil.addChatMessage("No configs found.");
                            return 1;
                        }
                        ChatUtil.addChatMessage("Configs (§a" + configs.size() + "§f):");
                        int perLine = 8;
                        for (int i = 0; i < configs.size(); i += perLine) {
                            int end = Math.min(configs.size(), i + perLine);
                            String line = configs.subList(i, end).stream()
                                    .map(n -> "§a" + n + "§f")
                                    .collect(java.util.stream.Collectors.joining("§7, §f"));
                            ChatUtil.addChatMessage(line);
                        }
                        return 1;
                    })))
            .then(literal("friend")
                .then(literal("add")
                    .then(argument("name", StringArgumentType.string())
                        .suggests((context, suggestionsBuilder) -> {
                            if (mc.getNetworkHandler() != null) {
                                return CommandSource.suggestMatching(
                                        mc.getNetworkHandler().getPlayerList().stream()
                                                .map(entry -> entry.getProfile().name())
                                                .filter(name -> !Managers.FRIEND.isFriend(name)),
                                        suggestionsBuilder);
                            }
                            return suggestionsBuilder.buildFuture();
                        })
                        .executes(c -> {
                            String name = StringArgumentType.getString(c, "name");
                            if (Managers.FRIEND.isFriend(name)) {
                                ChatUtil.addChatMessage("§c" + name + " is already a friend.");
                            } else {
                                Managers.FRIEND.addFriend(name);
                                ChatUtil.addChatMessage("Added §a" + name + "§f as a friend.");
                            }
                            return 1;
                        })))
                .then(literal("remove")
                    .then(argument("name", StringArgumentType.string())
                        .suggests((context, suggestionsBuilder) -> CommandSource.suggestMatching(Managers.FRIEND.getFriends(), suggestionsBuilder))
                        .executes(c -> {
                            String name = StringArgumentType.getString(c, "name");
                            if (!Managers.FRIEND.isFriend(name)) {
                                ChatUtil.addChatMessage("§c" + name + " is not a friend.");
                            } else {
                                Managers.FRIEND.removeFriend(name);
                                ChatUtil.addChatMessage("Removed §a" + name + "§f from friends.");
                            }
                            return 1;
                        })))
                .then(literal("list")
                    .executes(c -> {
                        if (Managers.FRIEND.getFriends().isEmpty()) {
                            ChatUtil.addChatMessage("You have no friends.");
                        } else {
                            ChatUtil.addChatMessage("Friends: §a" + String.join(", ", Managers.FRIEND.getFriends()));
                        }
                        return 1;
                    }))
                .then(literal("clear")
                    .executes(c -> {
                        Managers.FRIEND.clearFriends();
                        ChatUtil.addChatMessage("Cleared all friends.");
                        return 1;
                    })))
            .then(literal("home")
                .then(literal("add")
                    .then(argument("name", StringArgumentType.string())
                        .executes(c -> {
                            if (mc.player == null || mc.world == null) return 0;
                            String name = StringArgumentType.getString(c, "name");
                            var pos = mc.player.getBlockPos();
                            String dim = mc.world.getRegistryKey().getValue().toString();
                            float yaw = mc.player.getYaw();
                            float pitch = mc.player.getPitch();
                            String serverId = dev.mzc.client.module.impl.client.Home.getCurrentServerId();
                            Managers.HOME.setHome(serverId, name, new dev.mzc.client.manager.impl.HomeManager.HomeLocation(dim, pos.getX(), pos.getY(), pos.getZ(), yaw, pitch, true, true, 0x66FFFFFF));
                            ChatUtil.addChatMessage("§aHome已设置: §f" + name + " §7(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")");
                            return 1;
                        })))
                .then(literal("del")
                    .then(argument("name", StringArgumentType.string())
                        .suggests((context, suggestionsBuilder) -> {
                            String serverId = dev.mzc.client.module.impl.client.Home.getCurrentServerId();
                            return CommandSource.suggestMatching(Managers.HOME.getHomes(serverId).keySet(), suggestionsBuilder);
                        })
                        .executes(c -> {
                            String name = StringArgumentType.getString(c, "name");
                            String serverId = dev.mzc.client.module.impl.client.Home.getCurrentServerId();
                            if (!Managers.HOME.removeHome(serverId, name)) {
                                ChatUtil.addChatMessage("§cHome不存在: " + name);
                                return 0;
                            }
                            ChatUtil.addChatMessage("§aHome已删除: §f" + name);
                            return 1;
                        })))
                .then(literal("list")
                    .executes(c -> {
                        String serverId = dev.mzc.client.module.impl.client.Home.getCurrentServerId();
                        if (Managers.HOME.getHomes(serverId).isEmpty()) {
                            ChatUtil.addChatMessage("你还没有Home。");
                        } else {
                            ChatUtil.addChatMessage("Homes(" + serverId + "): §a" + String.join(", ", Managers.HOME.getHomes(serverId).keySet()));
                        }
                        return 1;
                    })
                    .then(literal("all")
                        .executes(c -> {
                            if (Managers.HOME.getAllHomes().isEmpty()) {
                                ChatUtil.addChatMessage("你还没有Home。");
                                return 1;
                            }

                            for (var serverEntry : Managers.HOME.getAllHomes().entrySet()) {
                                if (serverEntry.getValue().isEmpty()) continue;
                                ChatUtil.addChatMessage("Homes(" + serverEntry.getKey() + "): §a" + String.join(", ", serverEntry.getValue().keySet()));
                            }
                            return 1;
                        })))
                .then(literal("clear")
                    .executes(c -> {
                        Managers.HOME.clearHomes();
                        ChatUtil.addChatMessage("Cleared all homes.");
                        return 1;
                    })))
            .then(literal("hide")
                .executes(c -> {
                    if (Sakura.UI_HIDDEN) {
                        showHudModules();
                        ChatUtil.addChatMessage("UI is now §ashown§f.");
                        Sakura.CONFIG.saveDefaultConfig();
                    } else {
                        hideHudModules();
                        ChatUtil.addChatMessage("UI is now §chidden§f.");
                        Sakura.CONFIG.saveDefaultConfig();
                    }
                    return 1;
                })
                .then(literal("on")
                    .executes(c -> {
                        if (!Sakura.UI_HIDDEN) {
                            hideHudModules();
                            ChatUtil.addChatMessage("UI is now §chidden§f.");
                            Sakura.CONFIG.saveDefaultConfig();
                        } else {
                            ChatUtil.addChatMessage("UI is already §chidden§f.");
                        }
                        return 1;
                    }))
                .then(literal("off")
                    .executes(c -> {
                        if (Sakura.UI_HIDDEN) {
                            showHudModules();
                            ChatUtil.addChatMessage("UI is now §ashown§f.");
                            Sakura.CONFIG.saveDefaultConfig();
                        } else {
                            ChatUtil.addChatMessage("UI is already §ashown§f.");
                        }
                        return 1;
                    })))
            .then(literal("bind")
                .then(argument("module", ModuleArgumentType.module())
                    .executes(c -> {
                        Module module = ModuleArgumentType.getModule(c, "module");
                        module.setKey(InputUtil.UNKNOWN_KEY.getCode());
                        ChatUtil.addChatMessage("Unbound " + module.getEnglishName() + ".");
                        Sakura.CONFIG.saveDefaultConfig();
                        return 1;
                    })
                    .then(argument("key", StringArgumentType.string())
                        .executes(c -> {
                            Module module = ModuleArgumentType.getModule(c, "module");
                            String keyName = StringArgumentType.getString(c, "key");

                            if (keyName.equalsIgnoreCase("none")) {
                                module.setKey(InputUtil.UNKNOWN_KEY.getCode());
                                ChatUtil.addChatMessage("Unbound " + module.getEnglishName() + ".");
                                Sakura.CONFIG.saveDefaultConfig();
                                return 1;
                            }

                            InputUtil.Key key = KeyUtil.getKeyFromName(keyName);
                            if (key == InputUtil.UNKNOWN_KEY || key.getCode() == GLFW.GLFW_KEY_UNKNOWN) {
                                ChatUtil.addChatMessage("Invalid key: " + keyName);
                                return 0;
                            }

                            module.setKey(key.getCode());
                            // Default to toggle mode for quick bind
                            module.setBindMode(Module.BindMode.Toggle); 
                            ChatUtil.addChatMessage("Bound " + module.getEnglishName() + " to " + keyName.toUpperCase() + ".");
                            Sakura.CONFIG.saveDefaultConfig();
                            return 1;
                        }))))
            .then(literal("tp")
                .then(argument("x", DoubleArgumentType.doubleArg())
                    .then(argument("y", DoubleArgumentType.doubleArg())
                        .then(argument("z", DoubleArgumentType.doubleArg())
                            .executes(c -> {
                                double x = DoubleArgumentType.getDouble(c, "x");
                                double y = DoubleArgumentType.getDouble(c, "y");
                                double z = DoubleArgumentType.getDouble(c, "z");

                                mc.player.updatePosition(x, y, z);
                                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, true, false));

                                ChatUtil.addChatMessage("Teleported to " + x + ", " + y + ", " + z);
                                return 1;
                            }))))
                .then(argument("player", StringArgumentType.string())
                    .suggests((context, suggestionsBuilder) -> {
                        if (mc.getNetworkHandler() != null) {
                            return CommandSource.suggestMatching(
                                    mc.getNetworkHandler().getPlayerList().stream()
                                            .map(entry -> entry.getProfile().name()),
                                    suggestionsBuilder);
                        }
                        return suggestionsBuilder.buildFuture();
                    })
                    .executes(c -> {
                        String targetName = StringArgumentType.getString(c, "player");
                        PlayerEntity target = null;
                        for (PlayerEntity p : mc.world.getPlayers()) {
                            if (p.getName().getString().equalsIgnoreCase(targetName)) {
                                target = p;
                                break;
                            }
                        }

                        if (target != null) {
                            double x = target.getX();
                            double y = target.getY();
                            double z = target.getZ();

                            mc.player.updatePosition(x, y, z);
                            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, true, false));

                            ChatUtil.addChatMessage("Teleported to " + targetName + " (" + (int)x + ", " + (int)y + ", " + (int)z + ")");
                        } else {
                            ChatUtil.addChatMessage("§cPlayer " + targetName + " not found.");
                        }
                        return 1;
                    })))
            .executes(c -> {
                ChatUtil.addChatMessage("Usage: /MZC <command>");
                ChatUtil.addChatMessage("Commands: toggle, bind, gamemode, config, friend, hide, tp");
                return 1;
            });
    }
}
