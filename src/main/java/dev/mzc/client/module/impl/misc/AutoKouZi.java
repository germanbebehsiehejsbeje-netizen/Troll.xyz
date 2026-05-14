package dev.mzc.client.module.impl.misc;

import dev.mzc.client.auth.UserRole;
import dev.mzc.client.config.ConfigManager;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.client.ChatUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import dev.mzc.client.values.impl.RangeValue;
import dev.mzc.client.values.impl.StringValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class AutoKouZi extends Module {
    private enum LanguageMode {
        Chinese(),
        English();
        LanguageMode() {
        }
    }

    private enum FileMode {
        FuckYou(),
        AntiFuckYou(),
        AntiHYW(),
        Antijijiji();
        FileMode() {
        }
    }

    private enum SuffixMode {
        Letters(),
        Numbers(),
        Traditional(),
        Smart();
        SuffixMode() {
        }
    }

    private static final String KOUZI_FILE_NAME = "KouZi";
    private static final String KOUZI_FILE_NAME_TXT = "AntiKouZi";
    private static final String HYW_FILE_NAME = "AntiHYW.txt";
    private static final String JIJIJI_FILE_NAME = "Antijijiji.txt";

    private static final String KOUZI_FILE_NAME_EN = "KouZi-eng.txt";
    private static final String KOUZI_FILE_NAME_TXT_EN = "AntiKouZI-eng.txt";
    private static final String HYW_FILE_NAME_EN = "AntiHYW-eng.txt";
    private static final String JIJIJI_FILE_NAME_EN = "Antijijiji-eng.txt";

    private final RangeValue<Integer> delayTicks = new RangeValue<>("Delay", 20, 20, 1, 100, 1);
    private final EnumValue<FileMode> fileMode = new EnumValue<>("FileMode", FileMode.FuckYou, FileMode.class);
    private final EnumValue<LanguageMode> language = new EnumValue<>("Language", LanguageMode.Chinese, LanguageMode.class);
    private final BoolValue randomSuffix = new BoolValue("RandomSuffix", false);
    private final EnumValue<SuffixMode> suffixMode = new EnumValue<>("SuffixMode", SuffixMode.Letters, SuffixMode.class, randomSuffix::get);
    private final NumberValue<Integer> suffixLength = new NumberValue<>("SuffixLength", 6, 1, 20, 1, randomSuffix::get);
    private final BoolValue whisperMode = new BoolValue("Whisper", false);
    private final BoolValue whisperRandom = new BoolValue("WhisperRandom", false, whisperMode::get);
    private final StringValue whisperTarget = new StringValue("WhisperTarget", "", () -> whisperMode.get() && !whisperRandom.get());

    private int tickCounter;
    private int lineIndex;
    private List<String> lines = List.of();
    private int reloadCounter;
    private int nextDelayTicks;
    private Path activeFile;
    private long lastModified = -1L;

    public AutoKouZi() {
        super("AutoKouZi", Category.Misc);
        this.setType(ModuleType.Safe);
        this.setRequiredRole(UserRole.VIP);
    }

    @Override
    protected void onEnable() {
        tickCounter = 0;
        nextDelayTicks = randomDelayTicks();
        lineIndex = 0;
        reloadCounter = 0;
        loadLines();
        if (lines.isEmpty()) {
            ChatUtil.addChatMessage("错误：KouZi 文件为空");
            toggle();
        } else if (activeFile != null) {
            ChatUtil.addChatMessage("KouZi 已载入：" + lines.size() + " 行 | " + activeFile.toString());
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;
        if (++reloadCounter >= 20) {
            reloadCounter = 0;
            if (shouldReload()) {
                loadLines();
                if (lines.isEmpty()) return;
            }
        }
        if (lines.isEmpty()) return;
        if (tickCounter++ < nextDelayTicks) return;
        tickCounter = 0;
        nextDelayTicks = randomDelayTicks();
        String line = nextLine();
        if (line == null || line.isEmpty()) return;
        if (mc.getNetworkHandler() == null) return;
        String message = buildMessage(line);
        if (message.isEmpty()) return;
        if (whisperMode.get()) {
            String target = resolveWhisperTarget();
            if (target == null || target.isEmpty()) return;
            String clean = message.startsWith("/") ? message.substring(1) : message;
            mc.getNetworkHandler().sendChatCommand("w " + target + " " + clean);
            return;
        }
        if (message.startsWith("/")) {
            mc.getNetworkHandler().sendChatCommand(message.substring(1));
        } else {
            mc.getNetworkHandler().sendChatMessage(message);
        }
    }

    private void loadLines() {
        lineIndex = 0;
        lines = new ArrayList<>();
        try {
            Files.createDirectories(ConfigManager.CONFIG_DIR);
            Path source = resolveSourceFile();
            File file = source.toFile();
            if (!file.exists()) {
                Path parent = source.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                file.createNewFile();

                String resourcePath = "/assets/sakura/texts/" + getFileName(fileMode.get(), language.get());

                String content = "";
                try {
                    InputStream in = AutoKouZi.class.getResourceAsStream(resourcePath);
                    if (in == null) {
                        // Try with classloader without leading slash
                        String loaderPath = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
                        in = AutoKouZi.class.getClassLoader().getResourceAsStream(loaderPath);
                    }

                    // Compatibility fallback: accept alternate AntiKouZi english casing.
                    if (in == null && fileMode.get() == FileMode.AntiFuckYou && language.get() == LanguageMode.English) {
                        String fallback = "/assets/sakura/texts/AntiKouZi-eng.txt";
                        in = AutoKouZi.class.getResourceAsStream(fallback);
                        if (in == null) {
                            in = AutoKouZi.class.getClassLoader().getResourceAsStream(fallback.substring(1));
                        }
                    }
                    
                    if (in != null) {
                        content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                        in.close();
                    } else {
                        ChatUtil.addChatMessage("调试：未找到资源 " + resourcePath);
                    }
                } catch (Exception e) {
                    ChatUtil.addChatMessage("调试：读取资源出错 " + e.getMessage());
                    e.printStackTrace();
                }

                if (!content.isEmpty()) {
                    Files.writeString(source, content, StandardCharsets.UTF_8);
                    ChatUtil.addChatMessage("AutoKouZi: 已从资源恢复配置 " + source.getFileName());
                } else {
                    ChatUtil.addChatMessage("AutoKouZi: 警告 - 默认配置为空或未找到");
                }
                
                activeFile = source;
                lastModified = Files.getLastModifiedTime(source).toMillis();
            }
            try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        lines.add(line);
                    }
                }
            }
            activeFile = source;
            lastModified = Files.getLastModifiedTime(source).toMillis();
        } catch (Exception e) {
            ChatUtil.addChatMessage("错误：读取 KouZi 文件失败");
            lines = List.of();
        }
    }

    private Path resolveSourceFile() {
        Path primary = ConfigManager.CONFIG_DIR.resolve(getFileName(fileMode.get(), language.get()));
        Path alt = Paths.get("..").resolve(primary);
        if (Files.exists(primary)) return primary;
        if (Files.exists(alt)) return alt;
        return primary;
    }

    private boolean shouldReload() {
        try {
            Path source = resolveSourceFile();
            if (activeFile == null || !activeFile.equals(source)) return true;
            if (!Files.exists(source)) return true;
            long modified = Files.getLastModifiedTime(source).toMillis();
            return modified != lastModified;
        } catch (Exception e) {
            return true;
        }
    }

    private String nextLine() {
        if (lines.isEmpty()) return null;
        if (lineIndex >= lines.size()) {
            lineIndex = 0;
        }
        return lines.get(lineIndex++);
    }

    private String buildMessage(String base) {
        if (!randomSuffix.get()) return base;
        int len = Math.max(1, suffixLength.get());
        StringBuilder sb = new StringBuilder(base.length() + 1 + len);
        sb.append(base);
        sb.append(' ');
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < len; i++) {
            sb.append(randomSuffixChar(random));
        }
        return sb.toString();
    }

    private char randomSuffixChar(ThreadLocalRandom random) {
        SuffixMode mode = suffixMode.get();
        if (mode == SuffixMode.Smart) {
            int pick = random.nextInt(3);
            mode = pick == 0 ? SuffixMode.Letters : (pick == 1 ? SuffixMode.Numbers : SuffixMode.Traditional);
        }
        return switch (mode) {
            case Letters -> (char) ('a' + random.nextInt(26));
            case Numbers -> (char) ('0' + random.nextInt(10));
            case Traditional -> (char) random.nextInt(0x4E00, 0x9FFF + 1);
            case Smart -> (char) ('a' + random.nextInt(26));
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

    private String getFileName(FileMode mode, LanguageMode lang) {
        boolean en = lang == LanguageMode.English;
        return switch (mode) {
            case FuckYou -> en ? KOUZI_FILE_NAME_EN : KOUZI_FILE_NAME;
            case AntiFuckYou -> en ? KOUZI_FILE_NAME_TXT_EN : KOUZI_FILE_NAME_TXT;
            case AntiHYW -> en ? HYW_FILE_NAME_EN : HYW_FILE_NAME;
            case Antijijiji -> en ? JIJIJI_FILE_NAME_EN : JIJIJI_FILE_NAME;
        };
    }
}
