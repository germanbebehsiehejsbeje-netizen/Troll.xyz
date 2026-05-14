package dev.mzc.client.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.mzc.client.command.Command;
import dev.mzc.client.utils.client.ChatUtil;
import net.minecraft.command.CommandSource;

import java.util.List;

public class BaritoneBridgeCommand extends Command {
    public BaritoneBridgeCommand() {
        super("Baritone", "Baritone bridge for /mzc baritone", literal("MZC", "mzc"));
    }

    @Override
    public void buildCommand(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("baritone")
                .then(argument("cmd", StringArgumentType.greedyString())
                        .suggests(this::suggestBaritone)
                        .executes(c -> {
                            String cmd = StringArgumentType.getString(c, "cmd").trim();
                            if (cmd.isEmpty()) {
                                ChatUtil.addChatMessage("Usage: /MZC baritone <command>");
                                return 0;
                            }

                            if (cmd.length() >= 2 && cmd.startsWith("\"") && cmd.endsWith("\"")) {
                                cmd = cmd.substring(1, cmd.length() - 1).trim();
                            }

                            try {
                                boolean ok = baritone.api.BaritoneAPI.getProvider()
                                        .getPrimaryBaritone()
                                        .getCommandManager()
                                        .execute(cmd);

                                if (!ok) {
                                    ChatUtil.addChatMessage("§cBaritone command failed: " + cmd);
                                }
                            } catch (Throwable t) {
                                ChatUtil.addChatMessage("§cBaritone not available.");
                                return 0;
                            }

                            return 1;
                        }))
                .executes(c -> {
                    ChatUtil.addChatMessage("Usage: /MZC baritone <command>");
                    return 1;
                }));
    }

    private <S> java.util.concurrent.CompletableFuture<Suggestions> suggestBaritone(com.mojang.brigadier.context.CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();

        if (remaining.length() >= 2 && remaining.startsWith("\"") && remaining.endsWith("\"")) {
            remaining = remaining.substring(1, remaining.length() - 1);
        }

        remaining = remaining.trim();

        try {
            var commandManager = baritone.api.BaritoneAPI.getProvider()
                    .getPrimaryBaritone()
                    .getCommandManager();

            List<String> list = commandManager.tabComplete(remaining).toList();
            if (!list.isEmpty()) {
                return CommandSource.suggestMatching(list, builder);
            }

            if (remaining.isEmpty()) {
                List<String> names = commandManager.getRegistry()
                        .stream()
                        .flatMap(cmd -> cmd.getNames().stream())
                        .distinct()
                        .filter(n -> n != null && !n.isBlank())
                        .toList();

                return CommandSource.suggestMatching(names, builder);
            }

            if (remaining.indexOf(' ') < 0) {
                String prefix = remaining.toLowerCase(java.util.Locale.ROOT);
                List<String> names = commandManager.getRegistry()
                        .stream()
                        .flatMap(cmd -> cmd.getNames().stream())
                        .distinct()
                        .filter(n -> n != null && n.toLowerCase(java.util.Locale.ROOT).startsWith(prefix))
                        .toList();

                return CommandSource.suggestMatching(names, builder);
            }

            return builder.buildFuture();
        } catch (Throwable ignored) {
            return builder.buildFuture();
        }
    }
}
