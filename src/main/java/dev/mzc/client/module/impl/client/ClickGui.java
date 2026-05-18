package dev.mzc.client.module.impl.client;

import dev.mzc.client.Sakura;
import dev.mzc.client.gui.clickgui.vape.MZCClickGuiScreen;
import dev.mzc.client.gui.clickgui.vape.VulkanClickGuiScreen;
import dev.mzc.client.gui.clickgui.augustus.AugustusClickGuiScreen;
import dev.mzc.client.gui.clickgui.skeet.SkeetClickGuiScreen;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.color.ColorUtil;
import dev.mzc.client.utils.render.RenderUtil;
import dev.mzc.client.values.Value;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.ColorValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;

import net.minecraft.text.MutableText;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import java.awt.*;

public class ClickGui extends Module {
    private static volatile long suppressEscapeUntilMs = 0L;
    public enum ColorMode {
        Fade(),
        Rainbow(),
        Astolfo(),
        Dynamic(),
        Tenacity(),
        Static(),
        Double();
        ColorMode() {
        }
    }

    public enum Language {
        English(),
        German(),
        Russian();
        Language() {
        }
    }

    public enum GuiStyle {
        Sakura(),
        Skaji(),
        MZC(),
        Vulkan(),
        Augustus(),
        Default(),
        Skeet();
        GuiStyle() {
        }
    }

    public enum ModuleFilter {
        All(),
        Safe(),
        Hack();
        ModuleFilter() {
        }
    }

    public enum ThemePreset {
        Default(),
        DeepBlue(),
        SakuraPink(),
        EmeraldMint(),
        GreenGold(),
        Sunset(),
        PurpleNeon(),
        IceCyan(),
        AmberGold(),
        CrimsonRose(),
        LimeTech(),
        MonoGray(),
        RoyalGold(),
        OceanAqua(),
        CherrySoda(),
        GalaxyIndigo(),
        CoffeeMocha();
        ThemePreset() {
        }
    }

    public static EnumValue<GuiStyle> style = new EnumValue<>("Style", GuiStyle.Sakura);
    public static EnumValue<Language> language = new EnumValue<>("Language", Language.English);
    public static EnumValue<ModuleFilter> moduleFilter = new EnumValue<>("Module Filter", ModuleFilter.Safe);
    public static Value<Boolean> extraSetting = new BoolValue("ExtraSetting", false);
    public static EnumValue<ThemePreset> themePreset = new EnumValue<>("Theme Preset", ThemePreset.DeepBlue, ThemePreset.class, extra(() -> true));
    
    // MZC Settings
    public static Value<Double> mzcScale = new NumberValue<>("MZC Scale", 1.0, 0.5, 2.0, 0.05, () -> style.is(GuiStyle.MZC) || style.is(GuiStyle.Vulkan));
    public static Value<Boolean> mzcBlur = new BoolValue("Blur", true, () -> style.is(GuiStyle.MZC));
    public static Value<Boolean> mzcGlow = new BoolValue("Glow", false, () -> style.is(GuiStyle.MZC));
    public static Value<Double> mzcCornerRadius = new NumberValue<>("Corner Radius", 10.0, 0.0, 20.0, 1.0, extra(() -> style.is(GuiStyle.MZC)));
    public static Value<Double> mzcBackgroundAlpha = new NumberValue<>("Background Alpha", 255.0, 0.0, 255.0, 1.0, extra(() -> style.is(GuiStyle.MZC)));
    public static Value<Color> mzcThemeColor = new ColorValue("Theme Color", new Color(20, 220, 140), extra(() -> style.is(GuiStyle.MZC) || style.is(GuiStyle.Vulkan)));

    // Sakura Settings
    public static Value<Double> guiScale = new NumberValue<>("Gui Scale", 1.0, 0.5, 2.0, 0.05, () -> style.is(GuiStyle.Sakura) || style.is(GuiStyle.Skaji));
    public static Value<Double> fontSize = new NumberValue<>("Font Size", 11.0, 6.0, 20.0, 0.5, extra(() -> style.is(GuiStyle.Sakura) || style.is(GuiStyle.Skaji)));
    public static Value<Boolean> globalFontReplacement = new BoolValue("Global Font", false, extra(() -> true));

    public static Value<Color> backgroundColor = new ColorValue("Background Color", new Color(28, 28, 28), extra(() -> style.is(GuiStyle.Sakura) || style.is(GuiStyle.Skaji)));
    public static Value<Color> expandedBackgroundColor = new ColorValue("Expanded Background", new Color(20, 20, 20), extra(() -> style.is(GuiStyle.Sakura) || style.is(GuiStyle.Skaji)));
    public static EnumValue<ColorMode> colorMode = new EnumValue<>("Color Mode", ColorMode.Tenacity, () -> style.is(GuiStyle.Sakura) || style.is(GuiStyle.Skaji) || style.is(GuiStyle.MZC) || style.is(GuiStyle.Vulkan) || style.is(GuiStyle.Augustus));
    public static ColorValue mainColor = new ColorValue("Main Color", new Color(255, 183, 197), extra(() -> (style.is(GuiStyle.Sakura) || style.is(GuiStyle.Skaji) || style.is(GuiStyle.Augustus)) && !colorMode.is(ColorMode.Rainbow)));
    public static ColorValue secondColor = new ColorValue("Second Color", new Color(255, 133, 161), extra(() -> (style.is(GuiStyle.Sakura) || style.is(GuiStyle.Skaji) || style.is(GuiStyle.MZC) || style.is(GuiStyle.Vulkan) || style.is(GuiStyle.Augustus)) && (colorMode.is(ColorMode.Tenacity) || colorMode.is(ColorMode.Double))));
    public static final Value<Double> colorSpeed = new NumberValue<>("Color Speed", 4.0, 1.0, 10.0, 0.5, extra(() -> (style.is(GuiStyle.Sakura) || style.is(GuiStyle.Skaji) || style.is(GuiStyle.MZC) || style.is(GuiStyle.Vulkan) || style.is(GuiStyle.Augustus)) && (colorMode.is(ColorMode.Tenacity) || colorMode.is(ColorMode.Dynamic))));
    public static final Value<Double> colorIndex = new NumberValue<>("Color Separation", 20.0, 1.0, 100.0, 1.0, extra(() -> (style.is(GuiStyle.Sakura) || style.is(GuiStyle.Skaji) || style.is(GuiStyle.MZC) || style.is(GuiStyle.Vulkan) || style.is(GuiStyle.Augustus)) && colorMode.is(ColorMode.Tenacity)));
    public static final Value<Double> rainbowSpeed = new NumberValue<>("Rainbow Speed", 2000.0, 500.0, 5000.0, 100.0, extra(() -> (style.is(GuiStyle.Sakura) || style.is(GuiStyle.Skaji) || style.is(GuiStyle.MZC) || style.is(GuiStyle.Vulkan) || style.is(GuiStyle.Augustus)) && colorMode.is(ColorMode.Rainbow)));
    public static final Value<Double> fadeSpeed = new NumberValue<>("Fade Speed", 5.0, 1.0, 10.0, 0.5, extra(() -> (style.is(GuiStyle.Sakura) || style.is(GuiStyle.Skaji) || style.is(GuiStyle.MZC) || style.is(GuiStyle.Vulkan) || style.is(GuiStyle.Augustus)) && colorMode.is(ColorMode.Fade)));
    public static final Value<Double> astolfoSaturation = new NumberValue<>("Saturation", 0.8, 0.0, 1.0, 0.05, extra(() -> (style.is(GuiStyle.Sakura) || style.is(GuiStyle.Skaji) || style.is(GuiStyle.MZC) || style.is(GuiStyle.Vulkan) || style.is(GuiStyle.Augustus)) && colorMode.is(ColorMode.Astolfo)));
    public static final Value<Double> astolfoBrightness = new NumberValue<>("Brightness", 1.0, 0.0, 1.0, 0.05, extra(() -> (style.is(GuiStyle.Sakura) || style.is(GuiStyle.Skaji) || style.is(GuiStyle.MZC) || style.is(GuiStyle.Vulkan) || style.is(GuiStyle.Augustus)) && colorMode.is(ColorMode.Astolfo)));

    public static Value<Boolean> backgroundBlur = new BoolValue("Background Blur", true, () -> style.is(GuiStyle.Sakura) || style.is(GuiStyle.Skaji) || style.is(GuiStyle.Vulkan) || style.is(GuiStyle.Augustus));
    public static Value<Double> blurStrength = new NumberValue<>("Blur Strength", 8.0, 1.0, 20.0, 0.5, extra(() -> (style.is(GuiStyle.Sakura) || style.is(GuiStyle.Skaji) || style.is(GuiStyle.Vulkan) || style.is(GuiStyle.Augustus)) && backgroundBlur.get()));
    public static Value<Boolean> shaderBackground = new BoolValue("Shader Background", false, () -> style.is(GuiStyle.Sakura) || style.is(GuiStyle.Skaji));
    public static final Value<Boolean> bjdOnly = new BoolValue("BJD Only", false, () -> false);

    public static Value.Dependency extra(Value.Dependency dependency) {
        return () -> extraSetting.get() && (dependency == null || dependency.check());
    }

    private MZCClickGuiScreen mzcClickGui;
    private VulkanClickGuiScreen vulkanClickGui;
    private AugustusClickGuiScreen augustusClickGui;
    private SkeetClickGuiScreen skeetClickGui;

    public ClickGui() {
        super("ClickGui", Category.Client);
        this.setType(ModuleType.All);
    }

    @Override
    protected void onEnable() {
        if (mc.mouse == null) {
            this.setState(false);
            return;
        }

        if (style.get() == GuiStyle.MZC) {
            if (mzcClickGui == null) {
                mzcClickGui = new MZCClickGuiScreen();
            }
            mc.setScreen(mzcClickGui);
        } else if (style.get() == GuiStyle.Vulkan) {
            if (vulkanClickGui == null) {
                vulkanClickGui = new VulkanClickGuiScreen();
            }
            mc.setScreen(vulkanClickGui);
        } else if (style.get() == GuiStyle.Augustus) {
            if (augustusClickGui == null) {
                augustusClickGui = new AugustusClickGuiScreen();
            }
            mc.setScreen(augustusClickGui);
        } else if (style.get() == GuiStyle.Skeet) {
            if (skeetClickGui == null) {
                skeetClickGui = new SkeetClickGuiScreen();
            }
            mc.setScreen(skeetClickGui);
        } else {
            // Sakura, Skaji, Default — all use Sakura GUI
            mc.setScreen(Sakura.CLICKGUI);
        }
    }

    @Override
    protected void onDisable() {
        if (mc.mouse != null && (mc.currentScreen instanceof MZCClickGuiScreen || mc.currentScreen instanceof VulkanClickGuiScreen || mc.currentScreen instanceof AugustusClickGuiScreen || mc.currentScreen instanceof SkeetClickGuiScreen || mc.currentScreen == Sakura.CLICKGUI)) {
            mc.setScreen(null);
        }
    }

    public static int colors(int tick) {
        return color(tick).getRGB();
    }

    public static void requestEscapeSuppression(long durationMs) {
        suppressEscapeUntilMs = Math.max(suppressEscapeUntilMs, System.currentTimeMillis() + Math.max(0L, durationMs));
    }

    public static boolean shouldSuppressEscapeNow() {
        return System.currentTimeMillis() < suppressEscapeUntilMs;
    }

    public static int color() {
        return color(1).getRGB();
    }

    public static Color color(int tick) {
        applyThemePresetIfNeeded();
        Color main = (style.is(GuiStyle.MZC) || style.is(GuiStyle.Vulkan)) ? mzcThemeColor.get() : mainColor.get();
        return switch (colorMode.get()) {
            case Fade -> ColorUtil.fade(fadeSpeed.get().intValue(), tick * 20, new Color(main.getRGB()), 1);
            case Static -> main;
            case Astolfo ->
                    new Color(ColorUtil.swapAlpha(astolfoRainbow(tick, astolfoSaturation.get().floatValue(), astolfoBrightness.get().floatValue()), 255));
            case Rainbow ->
                    new Color(RenderUtil.getRainbow(System.currentTimeMillis(), rainbowSpeed.get().intValue(), tick));
            case Tenacity ->
                    ColorUtil.interpolateColorsBackAndForth(colorSpeed.get().intValue(), colorIndex.get().intValue() * tick, main, secondColor.get(), false);
            case Dynamic ->
                    new Color(ColorUtil.swapAlpha(ColorUtil.colorSwitch(main, new Color(ColorUtil.darker(main.getRGB(), 0.25F)), 2000.0F, 0, 10, colorSpeed.get()).getRGB(), 255));
            case Double -> {
                tick *= 200;
                yield new Color(ColorUtil.colorSwitch2(main, secondColor.get(), 2000, -tick / 40, 75, 2));
            }
        };
    }

    public static Color color2(int tick) {
        return switch (colorMode.get()) {
            case Tenacity, Double -> color(tick + 50);
            default -> color(tick);
        };
    }

    public static int astolfoRainbow(final int offset, final float saturation, final float brightness) {
        double currentColor = Math.ceil((double) (System.currentTimeMillis() + offset * 20L)) / 6.0;
        return Color.getHSBColor(((float) ((currentColor %= 360.0) / 360.0) < 0.5) ? (-(float) (currentColor / 360.0)) : ((float) (currentColor / 360.0)), saturation, brightness).getRGB();
    }

    public static double getGuiScale() {
        if (style.get() == GuiStyle.MZC || style.get() == GuiStyle.Vulkan) {
            return mzcScale.get();
        }
        return guiScale.get();
    }

    public static double getFontSize() {
        return fontSize.get();
    }

    public static Text getMZCGradientText(Text original) {
        if (original == null) return null;
        return replaceMZCInText(original);
    }

    private static MutableText replaceMZCInText(Text text) {
        MutableText result;

        if (text.getContent() instanceof PlainTextContent) {
            String content = ((PlainTextContent) text.getContent()).string();
            if (content.toLowerCase().contains("mzc")) {
                result = replaceMZCStringWithStyle(content, text.getStyle());
            } else {
                result = Text.literal(content).setStyle(text.getStyle());
            }
        } else {
            result = text.copyContentOnly().setStyle(text.getStyle());
        }

        for (Text sibling : text.getSiblings()) {
            result.append(replaceMZCInText(sibling));
        }

        return result;
    }

    private static MutableText replaceMZCStringWithStyle(String original, Style style) {
        MutableText result = Text.empty();
        String lower = original.toLowerCase();
        int lastIndex = 0;
        int index = lower.indexOf("mzc");
        long time = System.currentTimeMillis();

        while (index != -1) {
            // Previous part
            String prefix = original.substring(lastIndex, index);
            if (!prefix.isEmpty()) {
                result.append(Text.literal(prefix).setStyle(style));
            }

            // MZC part
            String content = original.substring(index, index + 3);
            for (int i = 0; i < content.length(); i++) {
                int color = getMZCColor(i, time);
                result.append(Text.literal(String.valueOf(content.charAt(i)))
                        .setStyle(style.withColor(color)));
            }

            lastIndex = index + 3;
            index = lower.indexOf("mzc", lastIndex);
        }

        String suffix = original.substring(lastIndex);
        if (!suffix.isEmpty()) {
            result.append(Text.literal(suffix).setStyle(style));
        }

        return result;
    }

    private static int getMZCColor(int offset, long time) {
        if (style.get() == GuiStyle.MZC || style.get() == GuiStyle.Vulkan) {
            // Use MZC theme color for MZC/Vulkan style
            Color start = mzcThemeColor.get();
            Color end = Color.WHITE;
            
            double speed = 2.0;
            double progress = (Math.sin((time * 0.003 * speed + offset * 0.5)) + 1.0) / 2.0;
            
            int r = (int) (start.getRed() + (end.getRed() - start.getRed()) * progress);
            int g = (int) (start.getGreen() + (end.getGreen() - start.getGreen()) * progress);
            int b = (int) (start.getBlue() + (end.getBlue() - start.getBlue()) * progress);
            return (r << 16) | (g << 8) | b;
        } else {
            return colors(offset);
        }
    }

    private static ThemePreset lastAppliedPreset;
    private static PresetSnapshot defaultSnapshot;

    public static com.google.gson.JsonObject exportDefaultPresetJson() {
        if (defaultSnapshot == null) return null;
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("main", defaultSnapshot.main.getRGB());
        o.addProperty("mainRainbow", defaultSnapshot.mainRainbow);
        o.addProperty("second", defaultSnapshot.second.getRGB());
        o.addProperty("secondRainbow", defaultSnapshot.secondRainbow);
        o.addProperty("mzc", defaultSnapshot.mzc.getRGB());
        o.addProperty("mzcRainbow", defaultSnapshot.mzcRainbow);
        o.addProperty("bg", defaultSnapshot.bg.getRGB());
        o.addProperty("bgRainbow", defaultSnapshot.bgRainbow);
        o.addProperty("expanded", defaultSnapshot.expanded.getRGB());
        o.addProperty("expandedRainbow", defaultSnapshot.expandedRainbow);
        return o;
    }

    public static void importDefaultPresetJson(com.google.gson.JsonObject o) {
        if (o == null) return;
        Color main = new Color(o.get("main").getAsInt(), true);
        boolean mainRb = o.get("mainRainbow").getAsBoolean();
        Color second = new Color(o.get("second").getAsInt(), true);
        boolean secondRb = o.get("secondRainbow").getAsBoolean();
        Color mzcC = new Color(o.get("mzc").getAsInt(), true);
        boolean mzcRb = o.get("mzcRainbow").getAsBoolean();
        Color bgC = new Color(o.get("bg").getAsInt(), true);
        boolean bgRb = o.get("bgRainbow").getAsBoolean();
        Color expandedC = new Color(o.get("expanded").getAsInt(), true);
        boolean expandedRb = o.get("expandedRainbow").getAsBoolean();
        defaultSnapshot = new PresetSnapshot(main, mainRb, second, secondRb, mzcC, mzcRb, bgC, bgRb, expandedC, expandedRb);
    }

    private static final class PresetSnapshot {
        private final Color main;
        private final boolean mainRainbow;
        private final Color second;
        private final boolean secondRainbow;
        private final Color mzc;
        private final boolean mzcRainbow;
        private final Color bg;
        private final boolean bgRainbow;
        private final Color expanded;
        private final boolean expandedRainbow;

        private PresetSnapshot(Color main, boolean mainRainbow, Color second, boolean secondRainbow, Color mzc, boolean mzcRainbow, Color bg, boolean bgRainbow, Color expanded, boolean expandedRainbow) {
            this.main = main;
            this.mainRainbow = mainRainbow;
            this.second = second;
            this.secondRainbow = secondRainbow;
            this.mzc = mzc;
            this.mzcRainbow = mzcRainbow;
            this.bg = bg;
            this.bgRainbow = bgRainbow;
            this.expanded = expanded;
            this.expandedRainbow = expandedRainbow;
        }
    }

    private static PresetSnapshot capturePresetSnapshot() {
        ColorValue bg = (ColorValue) backgroundColor;
        ColorValue expanded = (ColorValue) expandedBackgroundColor;
        ColorValue mzc = (ColorValue) mzcThemeColor;
        return new PresetSnapshot(
                mainColor.get(), mainColor.isRainbow(),
                secondColor.get(), secondColor.isRainbow(),
                mzc.get(), mzc.isRainbow(),
                bg.get(), bg.isRainbow(),
                expanded.get(), expanded.isRainbow()
        );
    }

    private static void restorePresetSnapshot(PresetSnapshot s) {
        if (s == null) return;
        ColorValue bg = (ColorValue) backgroundColor;
        ColorValue expanded = (ColorValue) expandedBackgroundColor;
        ColorValue mzc = (ColorValue) mzcThemeColor;

        mainColor.set(s.main);
        mainColor.setRainbow(s.mainRainbow);
        secondColor.set(s.second);
        secondColor.setRainbow(s.secondRainbow);
        mzc.set(s.mzc);
        mzc.setRainbow(s.mzcRainbow);
        bg.set(s.bg);
        bg.setRainbow(s.bgRainbow);
        expanded.set(s.expanded);
        expanded.setRainbow(s.expandedRainbow);
    }

    private static void applyThemePresetIfNeeded() {
        ThemePreset preset = themePreset.get();
        if (preset == null) return;
        ThemePreset prev = lastAppliedPreset;
        if (preset == prev) return;

        if (defaultSnapshot == null) {
            defaultSnapshot = capturePresetSnapshot();
        }
        if (prev == ThemePreset.Default) {
            defaultSnapshot = capturePresetSnapshot();
        }
        if (preset == ThemePreset.Default) {
            restorePresetSnapshot(defaultSnapshot);
            lastAppliedPreset = preset;
            return;
        }

        Color primary;
        Color secondary;
        Color bgBase;
        Color expandedBase;
        switch (preset) {
            case DeepBlue -> {
                primary = new Color(18, 110, 220);
                secondary = new Color(120, 210, 255);
                bgBase = new Color(14, 18, 28);
                expandedBase = new Color(10, 14, 22);
            }
            case SakuraPink -> {
                primary = new Color(255, 183, 197);
                secondary = new Color(255, 133, 161);
                bgBase = new Color(28, 28, 28);
                expandedBase = new Color(20, 20, 20);
            }
            case EmeraldMint -> {
                primary = new Color(20, 220, 140);
                secondary = new Color(120, 255, 220);
                bgBase = new Color(14, 22, 18);
                expandedBase = new Color(10, 18, 14);
            }
            case GreenGold -> {
                primary = new Color(40, 220, 120);
                secondary = new Color(255, 215, 90);
                bgBase = new Color(12, 20, 14);
                expandedBase = new Color(10, 16, 12);
            }
            case Sunset -> {
                primary = new Color(255, 120, 60);
                secondary = new Color(255, 80, 170);
                bgBase = new Color(24, 18, 16);
                expandedBase = new Color(18, 12, 12);
            }
            case PurpleNeon -> {
                primary = new Color(155, 90, 255);
                secondary = new Color(255, 80, 210);
                bgBase = new Color(18, 14, 24);
                expandedBase = new Color(14, 10, 20);
            }
            case IceCyan -> {
                primary = new Color(60, 200, 255);
                secondary = new Color(170, 255, 240);
                bgBase = new Color(10, 18, 22);
                expandedBase = new Color(8, 14, 18);
            }
            case AmberGold -> {
                primary = new Color(255, 190, 70);
                secondary = new Color(255, 120, 40);
                bgBase = new Color(22, 18, 12);
                expandedBase = new Color(18, 14, 10);
            }
            case CrimsonRose -> {
                primary = new Color(255, 70, 90);
                secondary = new Color(255, 120, 200);
                bgBase = new Color(22, 12, 16);
                expandedBase = new Color(18, 10, 14);
            }
            case LimeTech -> {
                primary = new Color(120, 255, 120);
                secondary = new Color(0, 220, 255);
                bgBase = new Color(10, 18, 14);
                expandedBase = new Color(8, 14, 12);
            }
            case MonoGray -> {
                primary = new Color(235, 235, 235);
                secondary = new Color(120, 120, 120);
                bgBase = new Color(18, 18, 18);
                expandedBase = new Color(12, 12, 12);
            }
            case RoyalGold -> {
                primary = new Color(255, 215, 90);
                secondary = new Color(180, 120, 30);
                bgBase = new Color(18, 16, 12);
                expandedBase = new Color(14, 12, 9);
            }
            case OceanAqua -> {
                primary = new Color(0, 200, 200);
                secondary = new Color(0, 120, 255);
                bgBase = new Color(10, 16, 20);
                expandedBase = new Color(8, 12, 16);
            }
            case CherrySoda -> {
                primary = new Color(255, 80, 120);
                secondary = new Color(120, 220, 255);
                bgBase = new Color(18, 12, 16);
                expandedBase = new Color(14, 10, 14);
            }
            case GalaxyIndigo -> {
                primary = new Color(110, 80, 255);
                secondary = new Color(60, 220, 255);
                bgBase = new Color(12, 10, 20);
                expandedBase = new Color(10, 8, 16);
            }
            case CoffeeMocha -> {
                primary = new Color(210, 160, 120);
                secondary = new Color(120, 80, 60);
                bgBase = new Color(18, 14, 12);
                expandedBase = new Color(14, 10, 9);
            }
            default -> {
                return;
            }
        }

        mainColor.set(primary);
        mainColor.setRainbow(false);
        secondColor.set(secondary);
        secondColor.setRainbow(false);
        mzcThemeColor.set(primary);
        ((ColorValue) mzcThemeColor).setRainbow(false);

        float bgAlpha = ((ColorValue) backgroundColor).getAlpha();
        float expandedAlpha = ((ColorValue) expandedBackgroundColor).getAlpha();
        ((ColorValue) backgroundColor).set(new Color(bgBase.getRed(), bgBase.getGreen(), bgBase.getBlue(), Math.round(bgAlpha * 255f)));
        ((ColorValue) backgroundColor).setRainbow(false);
        ((ColorValue) expandedBackgroundColor).set(new Color(expandedBase.getRed(), expandedBase.getGreen(), expandedBase.getBlue(), Math.round(expandedAlpha * 255f)));
        ((ColorValue) expandedBackgroundColor).setRainbow(false);
        lastAppliedPreset = preset;
    }
}
