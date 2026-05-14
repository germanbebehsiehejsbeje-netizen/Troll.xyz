package dev.mzc.client.module.impl.misc;

import dev.mzc.client.Sakura;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.utils.color.ColorUtil;
import dev.mzc.client.values.impl.ColorValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.StringValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.TranslatableTextContent;
import java.util.Optional;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.awt.*;

public class NameProtect extends Module {
    public final StringValue customName = new StringValue("CustomName", "桜");
    public final EnumValue<Mode> mode = new EnumValue<>("Mode", Mode.Rainbow);
    
    public final ColorValue singleColor = new ColorValue("SingleColor", Color.PINK, () -> mode.is(Mode.Single));
    public final ColorValue gradientStart = new ColorValue("GradientStart", new Color(255, 180, 225), () -> mode.is(Mode.Double));
    public final ColorValue gradientEnd = new ColorValue("GradientEnd", Color.WHITE, () -> mode.is(Mode.Double));

    public enum Mode {
        Rainbow, Single, Double, Client
    }

    public NameProtect() {
        super("NameProtect", Category.Misc);
        this.setType(ModuleType.Safe);
    }

    public static String getReplacement(String original) {
        if (original == null) return null;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return original;

        String playerName = mc.getSession().getUsername();
        String displayName = mc.player.getName().getString();
        
        NameProtect mod = (NameProtect) Sakura.MODULES.getModule(NameProtect.class);
        String fakeName = (mod != null) ? mod.customName.get() : "桜";

        String result = original;
        if (playerName != null && playerName.length() >= 2 && result.contains(playerName)) {
            result = result.replace(playerName, fakeName);
        }
        if (displayName != null && displayName.length() >= 2 && result.contains(displayName)) {
            result = result.replace(displayName, fakeName);
        }
        
        return result;
    }

    public static Text getGradientReplacement(String original) {
        if (original == null) return null;

        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return Text.of(original);

            String playerName = mc.getSession().getUsername();
            String displayName = mc.player.getName().getString();

            NameProtect mod = (NameProtect) Sakura.MODULES.getModule(NameProtect.class);
            String fakeName = (mod != null) ? mod.customName.get() : "桜";

            String resultStr = original;
            boolean hasPlayerName = playerName != null && playerName.length() >= 2 && resultStr.contains(playerName);
            boolean hasDisplayName = displayName != null && displayName.length() >= 2 && resultStr.contains(displayName);

            if (hasPlayerName || hasDisplayName) {
                String target = hasPlayerName ? playerName : displayName;
                MutableText result = Text.empty();
                int lastIndex = 0;
                int index = resultStr.indexOf(target);

                while (index != -1) {
                    result.append(Text.of(resultStr.substring(lastIndex, index)));
                    
                    if (mod != null) {
                        result.append(mod.getDynamicText(fakeName));
                    } else {
                        result.append(Text.of(fakeName));
                    }

                    lastIndex = index + target.length();
                    index = resultStr.indexOf(target, lastIndex);
                }

                result.append(Text.of(resultStr.substring(lastIndex)));
                return result;
            }
        } catch (Exception e) {
            return Text.of(original);
        }
        return Text.of(original);
    }

    public static Text getGradientReplacement(Text original) {
        if (original == null) return null;
        return replaceInText(original);
    }

    private static MutableText replaceInText(Text text) {
        MutableText result;

        if (text.getContent() instanceof PlainTextContent plain) {
            String content = plain.string();
            if (shouldReplace(content)) {
                result = replaceStringWithStyle(content, text.getStyle());
            } else {
                result = Text.literal(content).setStyle(text.getStyle());
            }
        } else if (text.getContent() instanceof TranslatableTextContent trans) {
            Object[] args = trans.getArgs();
            Object[] newArgs = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Text t) {
                    newArgs[i] = replaceInText(t);
                } else if (args[i] instanceof String s) {
                    if (shouldReplace(s)) {
                        newArgs[i] = getGradientReplacement(s);
                    } else {
                        newArgs[i] = s;
                    }
                } else {
                    newArgs[i] = args[i];
                }
            }
            result = Text.translatable(trans.getKey(), newArgs).setStyle(text.getStyle());
        } else {
            result = text.copyContentOnly().setStyle(text.getStyle());
        }

        for (Text sibling : text.getSiblings()) {
            result.append(replaceInText(sibling));
        }

        return result;
    }

    private static MutableText replaceStringWithStyle(String original, Style style) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return Text.literal(original).setStyle(style);
        
        String playerName = mc.getSession().getUsername();
        String displayName = mc.player.getName().getString();

        NameProtect mod = (NameProtect) Sakura.MODULES.getModule(NameProtect.class);
        String fakeName = (mod != null) ? mod.customName.get() : "桜";

        String resultStr = original;
        boolean hasPlayerName = playerName != null && playerName.length() >= 2 && resultStr.contains(playerName);
        boolean hasDisplayName = displayName != null && displayName.length() >= 2 && resultStr.contains(displayName);

        if (!hasPlayerName && !hasDisplayName) {
            return Text.literal(original).setStyle(style);
        }

        String target = hasPlayerName ? playerName : displayName;
        MutableText result = Text.empty();
        int lastIndex = 0;
        int index = resultStr.indexOf(target);

        while (index != -1) {
            String prefix = resultStr.substring(lastIndex, index);
            if (!prefix.isEmpty()) {
                result.append(Text.literal(prefix).setStyle(style));
            }

            if (mod != null) {
                result.append(mod.getDynamicText(fakeName));
            } else {
                result.append(Text.literal(fakeName).setStyle(style));
            }

            lastIndex = index + target.length();
            index = resultStr.indexOf(target, lastIndex);
        }

        String suffix = resultStr.substring(lastIndex);
        if (!suffix.isEmpty()) {
            result.append(Text.literal(suffix).setStyle(style));
        }

        return result;
    }


    public static boolean shouldReplace(String text) {
        if (text == null) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;
        
        String playerName = mc.getSession().getUsername();
        String displayName = mc.player.getName().getString();
        
        boolean hasPlayerName = playerName != null && playerName.length() >= 2 && text.contains(playerName);
        boolean hasDisplayName = displayName != null && displayName.length() >= 2 && text.contains(displayName);
        
        if (hasPlayerName || hasDisplayName) {
            Sakura.LOGGER.info("NameProtect Matching: '" + text + "' | PlayerName: " + playerName + " | DisplayName: " + displayName);
        }
        
        return hasPlayerName || hasDisplayName;
    }

    public Text getDynamicText(String content) {
        MutableText text = Text.empty();
        long time = System.currentTimeMillis();

        for (int i = 0; i < content.length(); i++) {
            final int offset = i;
            text.append(Text.literal(String.valueOf(content.charAt(i)))
                    .setStyle(Style.EMPTY.withColor(getGradientColor(offset, time))));
        }
        return text;
    }

    private int getGradientColor(int offset, long time) {
        switch (mode.get()) {
            case Rainbow:
                double speed = 2.0;
                float hue = (float) (((time * 0.001 * speed + offset * 0.05) % 1.0));
                return Color.HSBtoRGB(hue, 0.6f, 1.0f);
            case Single:
                return ColorUtil.fade(10, offset * 10, singleColor.get(), 1.0f).getRGB();
            case Double:
                return ColorUtil.interpolateColorsBackAndForth(10, offset * 10, gradientStart.get(), gradientEnd.get(), true).getRGB();
            case Client:
                return ClickGui.color(offset).getRGB();
            default:
                return Color.WHITE.getRGB();
        }
    }
}
