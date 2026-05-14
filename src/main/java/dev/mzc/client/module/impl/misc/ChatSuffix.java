package dev.mzc.client.module.impl.misc;

import dev.mzc.client.Sakura;
import dev.mzc.client.events.client.ChatMessageEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import dev.mzc.client.values.impl.StringValue;
import meteordevelopment.orbit.EventHandler;

import java.util.concurrent.ThreadLocalRandom;

public class ChatSuffix extends Module {
    private enum AppendMode {
        Fixed(),
        Random();
        AppendMode() {
        }
    }

    private enum SuffixMode {
        Letters(),
        Numbers(),
        Traditional(),
        Random();
        SuffixMode() {
        }
    }

    private final EnumValue<AppendMode> mode = new EnumValue<>("Mode", AppendMode.Fixed, AppendMode.class);
    private final StringValue suffix = new StringValue("Suffix", "", () -> mode.get() == AppendMode.Fixed);
    private final EnumValue<SuffixMode> suffixMode = new EnumValue<>("SuffixMode", SuffixMode.Letters, SuffixMode.class, () -> mode.get() == AppendMode.Random);
    private final NumberValue<Integer> suffixLength = new NumberValue<>("SuffixLength", 6, 1, 20, 1, () -> mode.get() == AppendMode.Random);

    public ChatSuffix() {
        super("ChatSuffix", Category.Misc);
        this.setType(ModuleType.Safe);
    }

    @EventHandler
    private void onChat(ChatMessageEvent.Client event) {
        if (nullCheck() || mc.getNetworkHandler() == null) return;

        String original = event.getMessage();
        if (original == null || original.isBlank()) return;

        // Keep commands untouched.
        String cmdPrefix = Sakura.COMMAND != null ? Sakura.COMMAND.getPrefix() : ".";
        if (original.startsWith("/") || (!cmdPrefix.isEmpty() && original.startsWith(cmdPrefix))) return;

        String modified = buildMessage(original);
        if (modified.equals(original)) return;

        event.cancel();
        mc.getNetworkHandler().sendChatMessage(modified);
    }

    private String buildMessage(String original) {
        StringBuilder out = new StringBuilder(original.length() + 32);
        out.append(original);

        if (mode.get() == AppendMode.Fixed) {
            String base = suffix.get();
            if (base == null) base = "";
            base = base.trim();
            if (!base.isEmpty()) {
                out.append(' ').append(base);
            }
        } else {
            int len = Math.max(1, suffixLength.get());
            out.append(' ');
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < len; i++) {
                out.append(randomSuffixChar(random));
            }
        }

        return out.toString();
    }

    private char randomSuffixChar(ThreadLocalRandom random) {
        SuffixMode mode = suffixMode.get();
        if (mode == SuffixMode.Random) {
            int pick = random.nextInt(3);
            mode = pick == 0 ? SuffixMode.Letters : (pick == 1 ? SuffixMode.Numbers : SuffixMode.Traditional);
        }
        return switch (mode) {
            case Letters -> (char) ('a' + random.nextInt(26));
            case Numbers -> (char) ('0' + random.nextInt(10));
            case Traditional -> (char) random.nextInt(0x4E00, 0x9FFF + 1);
            case Random -> (char) ('a' + random.nextInt(26));
        };
    }
}
