package dev.mzc.client.module.impl.misc;

import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import dev.mzc.client.values.impl.RangeValue;
import dev.mzc.client.values.impl.StringValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Spam extends Module {
    private enum SuffixMode {
        Letters(),
        Numbers(),
        Traditional(),
        Random();
        SuffixMode() {
        }
    }

    private final RangeValue<Integer> delayTicks = new RangeValue<>("Delay", 20, 20, 1, 100, 1);
    private final StringValue message = new StringValue("Message");
    private final BoolValue randomSuffix = new BoolValue("RandomSuffix", false);
    private final EnumValue<SuffixMode> suffixMode = new EnumValue<>("SuffixMode", SuffixMode.Letters, SuffixMode.class, randomSuffix::get);
    private final NumberValue<Integer> suffixLength = new NumberValue<>("SuffixLength", 6, 1, 20, 1, randomSuffix::get);
    private final BoolValue whisperMode = new BoolValue("Whisper", false);
    private final BoolValue whisperRandom = new BoolValue("WhisperRandom", false, whisperMode::get);
    private final StringValue whisperTarget = new StringValue("WhisperTarget", "", () -> whisperMode.get() && !whisperRandom.get());
    private int tickCounter;
    private int nextDelayTicks;

    public Spam() {
        super("Spam", Category.Misc);
        this.setType(ModuleType.Safe);
    }

    @Override
    protected void onEnable() {
        tickCounter = 0;
        nextDelayTicks = randomDelayTicks();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;
        String msg = buildMessage();
        if (msg == null || msg.isEmpty()) return;
        if (tickCounter++ < nextDelayTicks) return;
        tickCounter = 0;
        nextDelayTicks = randomDelayTicks();
        if (mc.getNetworkHandler() == null) return;
        if (whisperMode.get()) {
            String target = resolveWhisperTarget();
            if (target == null || target.isEmpty()) return;
            String clean = msg.startsWith("/") ? msg.substring(1) : msg;
            mc.getNetworkHandler().sendChatCommand("w " + target + " " + clean);
            return;
        }
        if (msg.startsWith("/")) {
            mc.getNetworkHandler().sendChatCommand(msg.substring(1));
        } else {
            mc.getNetworkHandler().sendChatMessage(msg);
        }
    }

    private String buildMessage() {
        String msg = message.get();
        if (msg == null) return null;
        if (!randomSuffix.get()) return msg;
        int len = Math.max(1, suffixLength.get());
        StringBuilder sb = new StringBuilder(msg.length() + 1 + len);
        sb.append(msg);
        sb.append(' ');
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < len; i++) {
            sb.append(randomSuffixChar(random));
        }
        return sb.toString();
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

    private String resolveWhisperTarget() {
        if (!whisperRandom.get()) {
            return whisperTarget.get().trim();
        }

        if (mc.getNetworkHandler() == null || mc.player == null) return null;
        String selfName = mc.player.getName().getString();
        List<String> candidates = new ArrayList<>();
        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            if (entry == null || entry.getProfile() == null) continue;
            String name = entry.getProfile().name();
            if (name == null || name.isEmpty()) continue;
            if (selfName != null && name.equalsIgnoreCase(selfName)) continue;
            candidates.add(name);
        }

        if (candidates.isEmpty()) return null;
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private int randomDelayTicks() {
        int min = Math.max(1, delayTicks.getMinValue());
        int max = Math.max(1, delayTicks.getMaxValue());
        if (max < min) {
            int tmp = min;
            min = max;
            max = tmp;
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}
