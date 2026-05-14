package dev.mzc.client.mixin.client;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import dev.mzc.client.Sakura;
import dev.mzc.client.events.client.SuggestChatEvent;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.command.CommandSource;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.concurrent.CompletableFuture;

@Mixin(ChatInputSuggestor.class)
public abstract class MixinChatInputSuggestor {

    @Shadow
    private ParseResults<CommandSource> parse;

    @Shadow
    @Final
    TextFieldWidget textField;

    @Shadow
    boolean completingSuggestions;

    @Shadow
    @Nullable
    private ChatInputSuggestor.SuggestionWindow window;

    @Shadow
    private @Nullable CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow
    protected abstract void showCommandSuggestions();

    @Inject(method = "refresh", at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/StringReader;canRead()Z", remap = false), cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD)
    private void hookRefresh(CallbackInfo ci, String string, StringReader stringReader) {
        int cursor = textField.getCursor();

        String full = stringReader.getString();
        int start = stringReader.getCursor();
        if (full.startsWith("/", start)) {
            int nameStart = start + 1;
            int nameEnd = nameStart + 3;
            if (full.length() >= nameEnd && full.regionMatches(true, nameStart, "mzc", 0, 3)) {
                if (full.length() == nameEnd || full.charAt(nameEnd) == ' ') {
                    stringReader.setCursor(nameStart);
                    parse = Sakura.COMMAND.getDispatcher().parse(stringReader, Sakura.COMMAND.getSource());

                    if (cursor >= 1 && (window == null || !completingSuggestions)) {
                        pendingSuggestions = Sakura.COMMAND.getDispatcher().getCompletionSuggestions(parse, cursor);
                        pendingSuggestions.thenRun(() -> {
                            if (pendingSuggestions.isDone()) {
                                showCommandSuggestions();
                            }
                        });
                    }

                    ci.cancel();
                    return;
                }
            }
        }

        SuggestChatEvent suggestChatEvent = new SuggestChatEvent();
        Sakura.EVENT_BUS.post(suggestChatEvent);

        if (suggestChatEvent.getPrefix() == null || suggestChatEvent.getDispatcher() == null) {
            return;
        }

        if (stringReader.getString().startsWith(suggestChatEvent.getPrefix(), stringReader.getCursor())) {
            stringReader.setCursor(stringReader.getCursor() + suggestChatEvent.getPrefix().length());
            if (parse == null) {
                parse = suggestChatEvent.getDispatcher().parse(stringReader, suggestChatEvent.getSource());
            }
            if (cursor >= 1 && (window == null || !completingSuggestions)) {
                pendingSuggestions = suggestChatEvent.getDispatcher().getCompletionSuggestions(parse, cursor);
                pendingSuggestions.thenRun(() -> {
                    if (pendingSuggestions.isDone()) {
                        showCommandSuggestions();
                    }
                });
            }
            ci.cancel();
        }
    }
}
