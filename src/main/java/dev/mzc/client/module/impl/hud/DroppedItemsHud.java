package dev.mzc.client.module.impl.hud;

import dev.mzc.client.Sakura;
import dev.mzc.client.gui.hud.HudEditorScreen;
import dev.mzc.client.module.HudModule;
import dev.mzc.client.module.impl.client.HudEditor;
import dev.mzc.client.nanovg.font.FontLoader;
import dev.mzc.client.nanovg.util.NanoVGHelper;
import dev.mzc.client.nanovg.NanoVGRenderer;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.ColorValue;
import dev.mzc.client.values.impl.ListValue;
import dev.mzc.client.values.impl.NumberValue;
import dev.mzc.client.values.impl.StringValue;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.ItemEntity;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DroppedItemsHud extends HudModule {
    private final NumberValue<Double> hudScale = new NumberValue<>("Scale", 1.0, 0.5, 2.0, 0.1);
    private final BoolValue showBackground = new BoolValue("Background", true);
    private final NumberValue<Double> padding = new NumberValue<>("Padding", 6.0, 0.0, 20.0, 0.5);
    private final NumberValue<Double> radius = new NumberValue<>("Radius", 6.0, 0.0, 20.0, 1.0);

    private final ColorValue level1Color = new ColorValue("Level1Color", new Color(0, 200, 255));
    private final ColorValue level2Color = new ColorValue("Level2Color", new Color(120, 255, 120));
    private final ColorValue level3Color = new ColorValue("Level3Color", new Color(255, 180, 60));

    private final ListValue<Item> level1Items = new ListValue<>("Level1Items", ListValue.Type.ITEM);
    private final ListValue<Item> level2Items = new ListValue<>("Level2Items", ListValue.Type.ITEM);
    private final ListValue<Item> level3Items = new ListValue<>("Level3Items", ListValue.Type.ITEM);

    public DroppedItemsHud() {
        super("DroppedItemsHud", 14, 220);
        this.width = 160;
        this.height = 80;
    }

    @Override
    public void renderInGame(DrawContext context) {
        HudEditor editor = Sakura.MODULES.getModule(HudEditor.class);
        if (editor != null && editor.isEnabled()) return;
        if (mc.world == null) return;

        float s = hudScale.get().floatValue();
        float pad = padding.get().floatValue() * s;
        float r = radius.get().floatValue() * s;

        List<Entry> entries = collectEntries();
        entries.sort((a, b) -> Integer.compare(b.text().length(), a.text().length()));
        int font = FontLoader.medium(12);
        float lineH = NanoVGHelper.getFontHeight(font, 12 * s) + 4 * s;

        float textMaxW = 0;
        for (Entry e : entries) {
            String line = e.text();
            float w = NanoVGHelper.getTextWidth(line, font, 12 * s);
            if (w > textMaxW) textMaxW = w;
        }

        this.width = (int) (pad * 2 + textMaxW);
        this.height = (int) (pad * 2 + lineH * entries.size());

        NanoVGRenderer.INSTANCE.draw(vg -> {
            if (showBackground.get()) {
                NanoVGHelper.drawRoundRect(x, y, width, height, r, new Color(0, 0, 0, 100));
            }
            float curY = y + pad;
            for (Entry e : entries) {
                Color c = switch (e.level) {
                    case 1 -> level1Color.get();
                    case 2 -> level2Color.get();
                    default -> level3Color.get();
                };
                NanoVGHelper.drawString(e.text(), x + pad, curY + NanoVGHelper.getFontHeight(font, 12 * s), font, 12 * s, c);
                curY += lineH;
            }
        });
    }

    private List<Entry> collectEntries() {
        Set<Item> l1Items = new HashSet<>(level1Items.get());
        Set<Item> l2Items = new HashSet<>(level2Items.get());
        Set<Item> l3Items = new HashSet<>(level3Items.get());

        java.util.Map<Item, Integer> itemCounts = new java.util.HashMap<>();
        for (var entity : mc.world.getEntities()) {
            if (entity instanceof ItemEntity itemEntity) {
                ItemStack stack = itemEntity.getStack();
                if (stack.isEmpty()) continue;
                Item item = stack.getItem();
                itemCounts.merge(item, stack.getCount(), Integer::sum);
            }
        }
        List<Entry> entries = new ArrayList<>();
        for (var e : itemCounts.entrySet()) {
            Item item = e.getKey();
            int count = e.getValue();
            int level = classify(item, l1Items, l2Items, l3Items);
            String name = new ItemStack(item).getName().getString();
            entries.add(new Entry(level, name + " x" + count));
        }
        return entries;
    }

    private int classify(Item item, Set<Item> l1Items, Set<Item> l2Items, Set<Item> l3Items) {
        if (l1Items.contains(item)) return 1;
        if (l2Items.contains(item)) return 2;
        if (l3Items.contains(item)) return 3;
        return 1;
    }

    private List<String> parseIds(String csv) { return List.of(); }

    private record Entry(int level, String text) {}
}
