package dev.mzc.client.gui.clickgui;

import dev.mzc.client.module.impl.client.ClickGui;
import dev.mzc.client.values.impl.ListValue;
import dev.mzc.client.nanovg.NanoVGRenderer;
import dev.mzc.client.nanovg.font.FontLoader;
import dev.mzc.client.nanovg.util.NanoVGHelper;
import dev.mzc.client.utils.TranslationManager;
import net.minecraft.block.Block;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.item.SpawnEggItem;

public class SelectionScreen extends Screen {

    private final Screen parent;
    private final ListValue<?> listValue;
    
    private final List<Object> allItems = new ArrayList<>();
    private List<Object> filteredItems = new ArrayList<>();
    
    private String searchText = "";
    private float scrollY = 0;
    
    private float x, y, width, height;
    
    public SelectionScreen(Screen parent, ListValue<?> listValue) {
        super(Text.literal("Selection"));
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
        this.renderBackground(context, mouseX, mouseY, delta);
        
        NanoVGRenderer.INSTANCE.draw(vg -> {
            // Background
            NanoVGHelper.drawRoundRect(x, y, width, height, 10, new Color(30, 30, 30, 255));
            NanoVGHelper.drawRoundRectOutline(x, y, width, height, 10, 1, new Color(60, 60, 60, 255));
            
            // Title
            NanoVGHelper.drawCenteredString(getTitleText(), x + width / 2, y + 20, FontLoader.bold(18), 18, Color.WHITE);
            
            // Search Box
            float searchX = x + 20;
            float searchY = y + 40;
            float searchW = width - 40;
            float searchH = 25;
            
            NanoVGHelper.drawRoundRect(searchX, searchY, searchW, searchH, 5, new Color(45, 45, 45));
            NanoVGHelper.drawRoundRectOutline(searchX, searchY, searchW, searchH, 5, 1, new Color(70, 70, 70));
            
            String displaySearch = searchText.isEmpty() ? getSearchText() : searchText;
            Color searchColor = searchText.isEmpty() ? Color.GRAY : Color.WHITE;
            
            NanoVGHelper.save();
            NanoVGHelper.intersectScissor(searchX, searchY, searchW, searchH);
            NanoVGHelper.drawString(displaySearch, searchX + 10, searchY + 17, FontLoader.regular(14), 14, searchColor);
            
            if (!searchText.isEmpty() && (System.currentTimeMillis() / 500 % 2 == 0)) {
                float textW = NanoVGHelper.getTextWidth(searchText, FontLoader.regular(14), 14);
                NanoVGHelper.drawRect(searchX + 10 + textW + 1, searchY + 5, 1, 15, Color.WHITE);
            }
            NanoVGHelper.restore();
            
            // List Area
            float listX = x + 20;
            float listY = y + 75;
            float listW = width - 40;
            float listH = height - 95;
            
            NanoVGHelper.save();
            NanoVGHelper.intersectScissor(listX, listY, listW, listH);
            
            float itemY = listY + scrollY;
            float itemH = 30;
            
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
                    Color bgColor = selected ? new Color(60, 100, 60) : new Color(45, 45, 45);
                    
                    NanoVGHelper.drawRoundRect(listX, itemY, listW, 25, 5, bgColor);
                    
                    // Draw Item Icon (using DrawContext if possible, but we are inside NanoVG block)
                    // Mixing NanoVG and Vanilla rendering is tricky.
                    // We will draw text first, then use vanilla render for items after NanoVG pass.
                    
                    NanoVGHelper.drawString(name, listX + 35, itemY + 17, FontLoader.regular(14), 14, Color.WHITE);
                    
                    if (selected) {
                        NanoVGHelper.drawCircle(listX + listW - 15, itemY + 12.5f, 4, Color.GREEN);
                    }
                }
                itemY += itemH;
            }
            
            NanoVGHelper.restore();
        });
        
        // Render Item Icons (Vanilla Pass)
        float listX = x + 20;
        float listY = y + 75;
        float listW = width - 40;
        float listH = height - 95;
        float itemY = listY + scrollY;
        float itemH = 30;
        
        // Apply Scissor for Vanilla Render
        context.enableScissor((int)listX, (int)listY, (int)(listX + listW), (int)(listY + listH));
        
        for (Object obj : filteredItems) {
             if (itemY + itemH > listY && itemY < listY + listH) {
                  if (obj instanceof Block block) {
                      context.drawItem(new ItemStack(block), (int)listX + 5, (int)itemY + 4);
                  } else if (obj instanceof EntityType<?> entityType) {
                      SpawnEggItem spawnEgg = SpawnEggItem.forEntity(entityType);
                      if (spawnEgg != null) {
                          context.drawItem(new ItemStack(spawnEgg), (int)listX + 5, (int)itemY + 4);
                      }
                  } else if (obj instanceof net.minecraft.item.Item item) {
                      context.drawItem(new ItemStack(item), (int)listX + 5, (int)itemY + 4);
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
        float searchY = y + 40;
        float searchW = width - 40;
        float searchH = 25;
        
        if (mouseX >= searchX && mouseX <= searchX + searchW && mouseY >= searchY && mouseY <= searchY + searchH) {
            return true;
        }
        
        float listX = x + 20;
        float listY = y + 75;
        float listW = width - 40;
        float listH = height - 95;
        
        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            float relativeY = (float)mouseY - listY - scrollY;
            int index = (int)(relativeY / 30);
            
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
        float maxScroll = (height - 95) - (filteredItems.size() * 30);
        if (maxScroll > 0) maxScroll = 0;
        if (scrollY < maxScroll) scrollY = maxScroll;
        if (scrollY > 0) scrollY = 0;
        return true;
    }
    
    @Override
    public boolean keyPressed(KeyInput keyInput) {
        int keyCode = keyInput.key();
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


