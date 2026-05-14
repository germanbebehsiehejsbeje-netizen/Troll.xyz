package dev.mzc.client.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.mzc.client.command.Command;
import dev.mzc.client.module.impl.client.Chat;
import dev.mzc.client.module.impl.combat.KillAura;
import net.minecraft.command.CommandSource;

public class RecordCommand extends Command {
    public RecordCommand() {
        super("Record", "KillAura neuro record control", literal("record"));
    }

    @Override
    public void buildCommand(LiteralArgumentBuilder<CommandSource> builder) {
        builder
                .then(literal("start").executes(c -> {
                    KillAura aura = KillAura.getInstance();
                    if (aura == null) {
                        Chat.print("§cKillAura not initialized.");
                        return 0;
                    }
                    aura.startRecord();
                    Chat.print("§aNeuro record started.");
                    return 1;
                }))
                .then(literal("stop").executes(c -> {
                    KillAura aura = KillAura.getInstance();
                    if (aura == null) {
                        Chat.print("§cKillAura not initialized.");
                        return 0;
                    }
                    aura.stopRecord();
                    Chat.print("§eNeuro record stopped and profile updated.");
                    return 1;
                }))
                .then(literal("save")
                        .then(argument("name", StringArgumentType.word())
                                .executes(c -> {
                                    KillAura aura = KillAura.getInstance();
                                    if (aura == null) {
                                        Chat.print("§cKillAura not initialized.");
                                        return 0;
                                    }
                                    String name = StringArgumentType.getString(c, "name");
                                    boolean ok = aura.saveRecord(name);
                                    Chat.print(ok ? "§aSaved profile: §f" + name : "§cFailed to save profile.");
                                    return ok ? 1 : 0;
                                })))
                .then(literal("load")
                        .then(argument("name", StringArgumentType.word())
                                .executes(c -> {
                                    KillAura aura = KillAura.getInstance();
                                    if (aura == null) {
                                        Chat.print("§cKillAura not initialized.");
                                        return 0;
                                    }
                                    String name = StringArgumentType.getString(c, "name");
                                    boolean ok = aura.loadRecord(name);
                                    Chat.print(ok ? "§aLoaded profile: §f" + name : "§cProfile not found or invalid.");
                                    return ok ? 1 : 0;
                                })))
                .then(literal("dir").executes(c -> {
                    KillAura aura = KillAura.getInstance();
                    if (aura == null) {
                        Chat.print("§cKillAura not initialized.");
                        return 0;
                    }
                    Chat.print("§7Neuro dir: §f" + aura.recordDir());
                    return 1;
                }))
                .executes(c -> {
                    Chat.print("§7Usage: .record start|stop|save <name>|load <name>|dir");
                    return 1;
                });
    }
}

