package dev.mzc.client.gui.clickgui;

import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.nanovg.NanoVGRenderer;
import dev.mzc.client.nanovg.font.FontLoader;
import dev.mzc.client.nanovg.util.NanoVGHelper;
import dev.mzc.client.utils.TranslationManager;
import dev.mzc.client.utils.render.RenderUtil;
import dev.mzc.client.utils.render.Shader2DUtil;
import dev.mzc.client.values.impl.ListValue;
import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.nanovg.NanoVG;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.entity.EntityType;
import net.minecraft.item.SpawnEggItem;

public class SakuraSelectionScreen extends Screen {

    private final Screen parent;
    private final ListValue<?> listValue;
    
    private final List<Object> allItems = new ArrayList<>();
    private List<Object> filteredItems = new ArrayList<>();
    
    private String searchText = "";
    private float scrollY = 0;
    
    private float x, y, width, height;
    
    public SakuraSelectionScreen(Screen parent, ListValue<?> listValue) {
        super(Text.literal("Sakura Selection"));
        this.parent = parent;
        this.listValue = listValue;
        
        if (listValue.getType() == ListValue.Type.ENTITY) {
            Registries.ENTITY_TYPE.forEach(entityType -> {
                if (entityType != EntityType.MARKER && entityType != EntityType.ITEM && entityType != EntityType.AREA_EFFECT_CLOUD) {
                     allItems.add(entityType);
                }
            });
            allItems.sort(Comparator.comparing(o -> ((EntityType<?>)o).getName().getString()));
        } else if (listValue.getType() == ListValue.Type.BLOCK) {
            Registries.BLOCK.forEach(block -> {
                 if (block.asItem() != Items.AIR) {
                     allItems.add(block);
                 }
            });
            allItems.sort(Comparator.comparing(o -> ((Block)o).getName().getString()));
        } else {
            Registries.ITEM.forEach(item -> {
                if (item != Items.AIR) {
                    allItems.add(item);
                }
            });
            allItems.sort(Comparator.comparing(o -> ((net.minecraft.item.Item)o).getName().getString()));
        }
        
        updateFilter();
    }
    
    private String getTitleText() {
        if (listValue.getType() == ListValue.Type.ENTITY) {
            return TranslationManager.get("ui.select_entities", "Select Entities");
        }
        if (listValue.getType() == ListValue.Type.BLOCK) {
            return TranslationManager.get("ui.select_blocks", "Select Blocks");
        }
        return TranslationManager.get("ui.select_items", "Select Items");
    }
    
    private String getSearchText() {
        return TranslationManager.get("ui.search", "Search...");
    }

    @Override
    protected void init() {
        this.width = 400;
        this.height = 300;
        this.x = (this.client.getWindow().getScaledWidth() - this.width) / 2;
        this.y = (this.client.getWindow().getScaledHeight() - this.height) / 2;
    }

    private void updateFilter() {
        if (searchText.isEmpty()) {
            filteredItems = new ArrayList<>(allItems);
        } else {
            String lower = searchText.toLowerCase();
            filteredItems = allItems.stream()
                .filter(o -> {
                    String localizedName = "";
                    String internalName = "";
                    if (o instanceof Block block) {
                        localizedName = block.getName().getString().toLowerCase();
                        internalName = Registries.BLOCK.getId(block).getPath().toLowerCase();
                    } else if (o instanceof EntityType<?> entityType) {
                        localizedName = entityType.getName().getString().toLowerCase();
                        internalName = Registries.ENTITY_TYPE.getId(entityType).getPath().toLowerCase();
                    } else if (o instanceof net.minecraft.item.Item item) {
                        localizedName = item.getName().getString().toLowerCase();
                        internalName = Registries.ITEM.getId(item).getPath().toLowerCase();
                    }
                    return localizedName.contains(lower) || internalName.contains(lower);
                })
                .collect(Collectors.toList());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Full screen blur background
        Shader2DUtil.drawQuadBlur(new MatrixStack(), 0, 0, this.client.getWindow().getScaledWidth(), this.client.getWindow().getScaledHeight(), 10f, 1f);

        // Panel Blur
        Shader2DUtil.drawRoundedBlur(new MatrixStack(), x, y, width, height, 15, new Color(0, 0, 0, 0), 15f, 1f);

        NanoVGRenderer.INSTANCE.draw(vg -> {
            // Panel Background (Semi-transparent gradient)
            NanoVGHelper.drawRoundRect(x, y, width, height, 15f, new Color(255, 255, 255, 30));
            // NanoVGHelper.drawGradientRoundedRect(x, y, width, height, 15f, new Color(255, 255, 255, 30), new Color(255, 255, 255, 10));
            NanoVGHelper.drawRoundRectOutline(x, y, width, height, 15, 1, new Color(255, 255, 255, 50));
            
            // Title
            NanoVGHelper.drawCenteredString(getTitleText(), x + width / 2, y + 25, FontLoader.bold(20), 20, Color.WHITE);
            
            // Search Box
            float searchX = x + 20;
            float searchY = y + 50;
            float searchW = width - 40;
            float searchH = 30;
            
            NanoVGHelper.drawRoundRect(searchX, searchY, searchW, searchH, 8, new Color(0, 0, 0, 50));
            NanoVGHelper.drawRoundRectOutline(searchX, searchY, searchW, searchH, 8, 1, new Color(255, 255, 255, 30));
            
            String displaySearch = searchText.isEmpty() ? getSearchText() : searchText;
            Color searchColor = searchText.isEmpty() ? new Color(200, 200, 200, 150) : Color.WHITE;
            
            NanoVGHelper.save();
            NanoVGHelper.intersectScissor(searchX, searchY, searchW, searchH);
            NanoVGHelper.drawString(displaySearch, searchX + 10, searchY + 20, FontLoader.regular(16), 16, searchColor);
            
            if (System.currentTimeMillis() / 500 % 2 == 0) {
                float textW = NanoVGHelper.getTextWidth(searchText, FontLoader.regular(16), 16);
                NanoVGHelper.drawRect(searchX + 10 + textW + 1, searchY + 8, 1, 14, searchColor);
            }
            NanoVGHelper.restore();
            
            // List Area
            float listX = x + 20;
            float listY = y + 90;
            float listW = width - 40;
            float listH = height - 110;
            
            NanoVGHelper.save();
            NanoVGHelper.intersectScissor(listX, listY, listW, listH);
            
            float itemY = listY + scrollY;
            float itemH = 35;
            
            for (Object obj : filteredItems) {
                String name = "";
                boolean selected = isSelected(obj);
                
                if (obj instanceof Block block) {
                    name = block.getName().getString();
                } else if (obj instanceof EntityType<?> entityType) {
                    name = entityType.getName().getString();
                } else if (obj instanceof net.minecraft.item.Item item) {
                    name = item.getName().getString();
                }

                if (itemY + itemH > listY && itemY < listY + listH) {
                    // Item Background
                    Color mainColor = ClickGui.color(0);
                    Color itemBgColor = selected ? new Color(mainColor.getRed(), mainColor.getGreen(), mainColor.getBlue(), 100) : new Color(255, 255, 255, 20);
                    if (RenderUtil.isHovering(listX, itemY, listW, 30, mouseX, mouseY)) {
                         itemBgColor = selected ? new Color(mainColor.getRed(), mainColor.getGreen(), mainColor.getBlue(), 150) : new Color(255, 255, 255, 40);
                    }
                    
                    NanoVGHelper.drawRoundRect(listX, itemY, listW, 30, 8, itemBgColor);
                    if (selected) {
                         NanoVGHelper.drawRoundRectOutline(listX, itemY, listW, 30, 8, 1, new Color(mainColor.getRed(), mainColor.getGreen(), mainColor.getBlue(), 200));
                    }
                    
                    // Text
                    NanoVGHelper.drawString(name, listX + 40, itemY + 20, FontLoader.regular(15), 15, Color.WHITE);
                    
                    // Selected Indicator
                    if (selected) {
                        NanoVGHelper.drawCircle(listX + listW - 20, itemY + 15, 4, mainColor);
                        NanoVGHelper.drawCircle(listX + listW - 20, itemY + 15, 6, new Color(mainColor.getRed(), mainColor.getGreen(), mainColor.getBlue(), 100));
                    }
                }
                itemY += itemH;
            }
            
            NanoVGHelper.restore();
        });
        
        // Render Item Icons (Vanilla Pass)
        float listX = x + 20;
        float listY = y + 90;
        float listW = width - 40;
        float listH = height - 110;
        float itemY = listY + scrollY;
        float itemH = 35;
        
        context.enableScissor((int)listX, (int)listY, (int)(listX + listW), (int)(listY + listH));
        
        for (Object obj : filteredItems) {
             if (itemY + itemH > listY && itemY < listY + listH) {
                  if (obj instanceof Block block) {
                       context.drawItem(new ItemStack(block), (int)listX + 8, (int)itemY + 7);
                  } else if (obj instanceof EntityType<?> entityType) {
                       SpawnEggItem spawnEgg = SpawnEggItem.forEntity(entityType);
                       if (spawnEgg != null) {
                           context.drawItem(new ItemStack(spawnEgg), (int)listX + 8, (int)itemY + 7);
                       }
                   } else if (obj instanceof net.minecraft.item.Item item) {
                        context.drawItem(new ItemStack(item), (int)listX + 8, (int)itemY + 7);
                  }
             }
             itemY += itemH;
        }
        context.disableScissor();
    }
    
    @Override
    public boolean mouseClicked(Click click, boolean playSound) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        float searchX = x + 20;
        float searchY = y + 50;
        float searchW = width - 40;
        float searchH = 30;
        
        if (mouseX >= searchX && mouseX <= searchX + searchW && mouseY >= searchY && mouseY <= searchY + searchH) {
            return true;
        }
        
        float listX = x + 20;
        float listY = y + 90;
        float listW = width - 40;
        float listH = height - 110;
        
        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            float relativeY = (float)mouseY - listY - scrollY;
            int index = (int)(relativeY / 35);
            
            if (index >= 0 && index < filteredItems.size()) {
                Object obj = filteredItems.get(index);
                toggleSelection(obj);
                return true;
            }
        }
        
        return super.mouseClicked(click, playSound);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollY += verticalAmount * 20;
        float maxScroll = (height - 110) - (filteredItems.size() * 35);
        if (maxScroll > 0) maxScroll = 0;
        if (scrollY < maxScroll) scrollY = maxScroll;
        if (scrollY > 0) scrollY = 0;
        return true;
    }
    
    @Override
    public boolean keyPressed(KeyInput keyInput) {
        int keyCode = keyInput.key();
        int scanCode = keyInput.scancode();
        int modifiers = keyInput.modifiers();

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.client.setScreen(parent);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!searchText.isEmpty()) {
                searchText = searchText.substring(0, searchText.length() - 1);
                updateFilter();
                scrollY = 0;
            }
            return true;
        }
        return super.keyPressed(keyInput);
    }
    
    @Override
    public boolean charTyped(CharInput charInput) {
        if (!charInput.isValidChar()) {
            return super.charTyped(charInput);
        }

        char chr = (char) charInput.codepoint();
        int modifiers = charInput.modifiers();

        if (chr >= 32 && chr != 127) {
            searchText += chr;
            updateFilter();
            scrollY = 0;
            return true;
        }
        return super.charTyped(charInput);
    }
    
    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @SuppressWarnings("unchecked")
    private boolean isSelected(Object obj) {
        return ((ListValue<Object>)listValue).contains(obj);
    }

    @SuppressWarnings("unchecked")
    private void toggleSelection(Object obj) {
        if (((ListValue<Object>)listValue).contains(obj)) {
            ((ListValue<Object>)listValue).remove(obj);
        } else {
            ((ListValue<Object>)listValue).add(obj);
        }
    }
}


