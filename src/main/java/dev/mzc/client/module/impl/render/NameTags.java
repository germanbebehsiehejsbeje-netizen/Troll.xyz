package dev.mzc.client.module.impl.render;

import dev.mzc.client.utils.combat.DamageUtil;
import dev.mzc.client.utils.entity.HealthUtil;
import com.mojang.blaze3d.opengl.GlStateManager;
import dev.mzc.client.module.impl.client.ClickGui;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.BlockPos;
import dev.mzc.client.events.render.Render2DEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.nanovg.NanoVGRenderer;
import dev.mzc.client.nanovg.font.FontLoader;
import dev.mzc.client.nanovg.util.NanoVGHelper;
import dev.mzc.client.utils.render.Render3DUtil;
import dev.mzc.client.utils.render.Shader2DUtil;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3x2fStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.lwjgl.nanovg.NanoVG.*;

public class NameTags extends Module {
    public static NameTags INSTANCE;
    private static final Map<Character, Color> COLOR_CODES = new HashMap<>();
    private static final Map<Integer, Character> RGB_TO_CODE = new HashMap<>();

    static {
        COLOR_CODES.put('0', new Color(0, 0, 0));
        COLOR_CODES.put('1', new Color(0, 0, 170));
        COLOR_CODES.put('2', new Color(0, 170, 0));
        COLOR_CODES.put('3', new Color(0, 170, 170));
        COLOR_CODES.put('4', new Color(170, 0, 0));
        COLOR_CODES.put('5', new Color(170, 0, 170));
        COLOR_CODES.put('6', new Color(255, 170, 0));
        COLOR_CODES.put('7', new Color(170, 170, 170));
        COLOR_CODES.put('8', new Color(85, 85, 85));
        COLOR_CODES.put('9', new Color(85, 85, 255));
        COLOR_CODES.put('a', new Color(85, 255, 85));
        COLOR_CODES.put('b', new Color(85, 255, 255));
        COLOR_CODES.put('c', new Color(255, 85, 85));
        COLOR_CODES.put('d', new Color(255, 85, 255));
        COLOR_CODES.put('e', new Color(255, 255, 85));
        COLOR_CODES.put('f', new Color(255, 255, 255));
        COLOR_CODES.put('r', new Color(255, 255, 255));

        for (var entry : COLOR_CODES.entrySet()) {
            if (entry.getKey() == 'r') continue;
            RGB_TO_CODE.put(entry.getValue().getRGB() & 0xFFFFFF, entry.getKey());
        }
    }

    public enum Mode {
        Normal(),
        Simple(),
        Modern(),
        Minimal(),
        Minecraft(),
        Utility2B2T();
        Mode() {
        }
    }

    private final EnumValue<Mode> mode = new EnumValue<>("Mode", Mode.Normal);
    private final NumberValue<Double> scaling = new NumberValue<>("Size", 1.5, 0.1, 5.0, 0.1);
    private final BoolValue self = new BoolValue("Self", false);
    private final BoolValue armor = new BoolValue("Armor", true, () -> mode.get() == Mode.Normal || mode.get() == Mode.Modern || mode.get() == Mode.Minecraft || mode.get() == Mode.Utility2B2T);
    private final BoolValue enchants = new BoolValue("Enchants", true, () -> (mode.get() == Mode.Normal || mode.get() == Mode.Modern || mode.get() == Mode.Minecraft || mode.get() == Mode.Utility2B2T) && armor.get());
    private final BoolValue itemName = new BoolValue("ItemName", true, () -> mode.get() == Mode.Normal || mode.get() == Mode.Modern || mode.get() == Mode.Minecraft);
    private final BoolValue health = new BoolValue("Health", true);
    private final BoolValue blur = new BoolValue("Blur", true, () -> mode.get() != Mode.Minecraft && mode.get() != Mode.Utility2B2T);
    private final NumberValue<Double> blurStrength = new NumberValue<>("BlurStrength", 10.0, 1.0, 25.0, 0.5, blur::get);
    private final BoolValue winRate = new BoolValue("WinRate", true, () -> mode.get() != Mode.Utility2B2T && mode.get() != Mode.Minecraft);
    private final NumberValue<Integer> winRateRange = new NumberValue<>("PredictRange", 50, 5, 200, 1, winRate::get);

    public NameTags() {
        super("NameTags", Category.Render);
        this.setType(ModuleType.All);
        INSTANCE = this;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRender2D(Render2DEvent event) {
        if (nullCheck()) return;
        float tickProgress = mc.getRenderTickCounter().getTickProgress(true);

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player && !self.get()) continue;
            if (!player.isAlive()) continue;

            double x = player.lastX + (player.getX() - player.lastX) * tickProgress;
            double y = player.lastY + (player.getY() - player.lastY) * tickProgress;
            double z = player.lastZ + (player.getZ() - player.lastZ) * tickProgress;

            Vec3d pos = new Vec3d(x, y + player.getBoundingBox().getLengthY() + 0.3, z);
            Vec3d screenPos = Render3DUtil.worldToScreen(pos);

            if (screenPos != null) {
                render(event.getContext(), player, (float) screenPos.x, (float) screenPos.y, (float) screenPos.z);
            }
        }
    }

    private void drawNvg(float posX, float posY, float scale, Consumer<Long> drawer) {
        NanoVGRenderer.INSTANCE.draw(vg -> {
            nvgSave(vg);
            nvgTranslate(vg, posX, posY);
            nvgScale(vg, scale, scale);
            nvgTranslate(vg, -posX, -posY);
            drawer.accept(vg);
            nvgRestore(vg);
        });
    }

    private void render(DrawContext context, PlayerEntity player, float posX, float posY, float posZ) {
        final String name = toLegacyText(player.getDisplayName());
        final float hp = HealthUtil.getEntityHealth(player);

        List<ItemStack> stacks = getPlayerEquipment(player);
        final String mainHandName = getPlayerMainHandName(player);

        final float headSize = 28;
        final float itemSize = 16;
        final float itemSpacing = 2;
        final float padding = 8;
        final float headerHeight = 18;
        final float radius = 6;
        final float enchantFontSize = 7;
        final float infoFontSize = 11;

        // Calculate scale
        float tickProgress = mc.getRenderTickCounter().getTickProgress(true);
        double dist = mc.player.getCameraPosVec(tickProgress).distanceTo(player.getLerpedPos(tickProgress));
        final float finalScale = (float) (scaling.get().floatValue() / Math.max(dist, 1.0) * 4.0);

        if (mode.get() == Mode.Minecraft) {
            String healthStr = health.get() ? String.format(" [%.1f]", hp) : "";
            String text = name + healthStr;
            int textWidth = mc.textRenderer.getWidth(text);
            
            float boxWidth = textWidth + 4;
            float boxHeight = 10;
            
            Matrix3x2fStack matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.translate(posX, posY);
            matrices.scale(finalScale, finalScale);
            matrices.translate(-posX, -posY);
            
            context.fill((int)(posX - boxWidth / 2f), (int)(posY - boxHeight), (int)(posX + boxWidth / 2f), (int)posY, 0x80000000);
            context.drawTextWithShadow(mc.textRenderer, text, (int)(posX - textWidth / 2f), (int)(posY - 9), -1);
            
            if (armor.get()) {
                float itemAreaWidth = stacks.size() * (itemSize + itemSpacing);
                float itemStartX = posX - itemAreaWidth / 2f;
                float itemY = posY - boxHeight - itemSize - 2;
                
                float currentX = itemStartX;
                for (ItemStack stack : stacks) {
                    if (!stack.isEmpty()) {
                        context.drawItem(stack, (int)currentX, (int)itemY);
                        if (enchants.get()) {
                            drawMinecraftEnchants(context, stack, currentX, itemY);
                        }
                    }
                    currentX += itemSize + itemSpacing;
                }
            }
            
            matrices.popMatrix();
            return;
        }

        if (mode.get() == Mode.Utility2B2T) {
            String healthStr = health.get() ? String.format(" %s", (int)hp) : "";
            String text = name + healthStr;
            int textWidth = mc.textRenderer.getWidth(text);
            
            Matrix3x2fStack matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.translate(posX, posY);
            matrices.scale(finalScale, finalScale);
            matrices.translate(-posX, -posY);
            
            context.drawTextWithShadow(mc.textRenderer, text, (int)(posX - textWidth / 2f), (int)(posY - 9), getHealthColor(hp).getRGB());
            
            if (armor.get()) {
                float itemAreaWidth = stacks.size() * (itemSize + 4);
                float itemStartX = posX - itemAreaWidth / 2f;
                float itemY = posY - 30;
                
                float currentX = itemStartX;
                for (ItemStack stack : stacks) {
                    if (!stack.isEmpty()) {
                        context.drawItem(stack, (int)currentX, (int)itemY);
                        if (enchants.get()) {
                            drawMinecraftEnchants(context, stack, currentX, itemY);
                        }
                    }
                    currentX += itemSize + 4;
                }
            }
            
            matrices.popMatrix();
            return;
        }

        if (mode.get() == Mode.Simple || mode.get() == Mode.Minimal) {
            boolean isMinimal = mode.get() == Mode.Minimal;
            String healthStr = health.get() ? (isMinimal ? String.format("%.0f", hp) : String.format("%.1f", hp)) : "";
            float nameW = getColoredTextWidth(name, FontLoader.bold(11), infoFontSize);
            float hpW = health.get() ? NanoVGHelper.getTextWidth(healthStr, FontLoader.bold(11), infoFontSize) : 0;
            float gap = health.get() ? 4 : 0;
            float totalTextW = nameW + gap + hpW;

            float contentWidth = totalTextW + padding * 2;
            float contentHeight = isMinimal ? 14 : headerHeight;
            float cornerRadius = isMinimal ? 4 : radius;

            // Calculate Win Rate Dimensions
            float rateBoxW = 0;
            String rateText = "";
            Color rateColor = Color.WHITE;
            float rate = 0;
            
            if (winRate.get() && !isMinimal) {
                rate = calculateWinRate(player);
                rateText = String.format("%.0f%%", rate * 100);
                rateColor = new Color(Color.HSBtoRGB(rate * 0.33f, 1f, 1f));
                float rateW = NanoVGHelper.getTextWidth(rateText, FontLoader.bold(11), infoFontSize);
                rateBoxW = rateW + padding * 2;
            }

            float totalWidth = contentWidth + (winRate.get() && !isMinimal ? 4 + rateBoxW : 0);
            
            float boxX = posX - totalWidth / 2;
            float boxY = posY - contentHeight;

            // Draw Blur
            if (blur.get()) {
                float scaledWidth = contentWidth * finalScale;
                float scaledHeight = contentHeight * finalScale;
                float scaledRadius = cornerRadius * finalScale;
                
                float scaledTotalWidth = totalWidth * finalScale;
                float scaledStartX = posX - scaledTotalWidth / 2;

                Shader2DUtil.drawRoundedBlur(new MatrixStack(),
                        scaledStartX, posY - (contentHeight * finalScale), scaledWidth, scaledHeight, scaledRadius,
                        new Color(0, 0, 0, 0),
                        blurStrength.get().floatValue(), 1.0f
                );
            }

            // Draw Background and Info
            drawNvg(posX, posY, finalScale, vg -> {
                Color bgColor = isMinimal ? new Color(0, 0, 0, 160) : new Color(158, 158, 158, 48);
                NanoVGHelper.drawRoundRect(boxX, boxY, contentWidth, contentHeight, cornerRadius, bgColor);
                if (!isMinimal) NanoVGHelper.drawRoundRect(boxX + 2, boxY + 2, contentWidth - 4, contentHeight - 4, cornerRadius - 2, new Color(171, 171, 172, 48));
                
                float textY = boxY + contentHeight / 2f + (isMinimal ? 0 : 1);
                
                float textStartX = boxX + padding;
                int font = isMinimal ? FontLoader.regular(10) : FontLoader.bold(11);
                float fSize = isMinimal ? 10 : infoFontSize;

                drawColoredString(name, textStartX, textY, font, fSize, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE, Color.WHITE);
                if (health.get()) {
                    Color healthColor = getHealthColor(hp);
                    NanoVGHelper.drawString(healthStr, textStartX + nameW + gap, textY, font, fSize, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE, healthColor);
                }
            });

            // Draw Win Rate (Simple)
            if (winRate.get() && !isMinimal) {
                float rateBoxX = boxX + contentWidth + 4;
                float rateBoxH = contentHeight;

                if (blur.get()) {
                    float scaledRateW = rateBoxW * finalScale;
                    float scaledRateH = rateBoxH * finalScale;
                    float scaledRadius = cornerRadius * finalScale;
                    
                    float scaledTotalWidth = totalWidth * finalScale;
                    float scaledStartX = posX - scaledTotalWidth / 2;
                    float scaledRateBoxX = scaledStartX + (contentWidth + 4) * finalScale;

                    Shader2DUtil.drawRoundedBlur(new MatrixStack(),
                            scaledRateBoxX, posY - (contentHeight * finalScale), scaledRateW, scaledRateH, scaledRadius,
                            new Color(0, 0, 0, 0),
                            blurStrength.get().floatValue(), 1.0f
                    );
                }

                final float finalRateBoxX = rateBoxX;
                final float finalRateBoxW = rateBoxW;
                final float finalRateBoxH = rateBoxH;
                final String finalRateText = rateText;
                final Color finalRateColor = rateColor;

                drawNvg(posX, posY, finalScale, vg -> {
                    NanoVGHelper.drawRoundRect(finalRateBoxX, boxY, finalRateBoxW, finalRateBoxH, cornerRadius, new Color(158, 158, 158, 48));
                    NanoVGHelper.drawRoundRect(finalRateBoxX + 2, boxY + 2, finalRateBoxW - 4, finalRateBoxH - 4, radius - 2, new Color(171, 171, 172, 48));

                    float textY = boxY + finalRateBoxH / 2f + 1;
                    NanoVGHelper.drawString(finalRateText, finalRateBoxX + padding, textY, FontLoader.bold(11), infoFontSize, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE, finalRateColor);
                });
            }

        } else if (mode.get() == Mode.Normal || mode.get() == Mode.Modern) {
            boolean isModern = mode.get() == Mode.Modern;
            int maxEnchants = calculateMaxEnchants(stacks);
            float enchantHeight = maxEnchants * (enchantFontSize + 1);
            float nameHeight = (itemName.get() && !mainHandName.isEmpty()) ? 14 : 0;
            float itemsWidth = 6 * itemSize + 5 * itemSpacing;

            float contentHeight = Math.max(headSize, enchantHeight + itemSize + nameHeight) + padding;
            float contentWidth = headSize + padding + itemsWidth + padding;

            float mainPanelWidth = contentWidth + padding * 2;
            float totalHeight = headerHeight + contentHeight + padding;

            // Calculate Win Rate Dimensions
            float rateBoxW = 0;
            String rateText = "";
            Color rateColor = Color.WHITE;
            float rate = 0;
            
            if (winRate.get()) {
                rate = calculateWinRate(player);
                rateText = String.format("%.0f%%", rate * 100);
                rateColor = new Color(Color.HSBtoRGB(rate * 0.33f, 1f, 1f));
                float rateW = NanoVGHelper.getTextWidth(rateText, FontLoader.bold(11), infoFontSize);
                rateBoxW = rateW + padding * 2;
            }

            float fullWidth = mainPanelWidth + (winRate.get() ? 4 + rateBoxW : 0);
            
            float startX = posX - fullWidth / 2;
            float boxX = startX;
            float boxY = posY - totalHeight;

            // Draw Blur
            if (blur.get()) {
                float scaledWidth = mainPanelWidth * finalScale;
                float scaledHeight = totalHeight * finalScale;
                float scaledRadius = radius * finalScale;
                
                float scaledFullWidth = fullWidth * finalScale;
                float scaledStartX = posX - scaledFullWidth / 2;

                Shader2DUtil.drawRoundedBlur(new MatrixStack(),
                        scaledStartX, posY - (totalHeight * finalScale), scaledWidth, scaledHeight, scaledRadius,
                        new Color(0, 0, 0, 0),
                        blurStrength.get().floatValue(), 1.0f
                );
            }

            float headX = boxX + padding;
            float headY = boxY + headerHeight + padding;
            PlayerListEntry entry = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(player.getUuid());

            // Batch 1: Backgrounds and Info (NanoVG)
            drawNvg(posX, posY, finalScale, vg -> {
                if (isModern) {
                    NanoVGHelper.drawRoundRect(boxX, boxY, mainPanelWidth, totalHeight, radius, new Color(20, 20, 20, 180));
                    NanoVGHelper.drawGradientRRect(boxX + 5, boxY, mainPanelWidth - 10, 1.5f, 1f, ClickGui.color(0), ClickGui.color2(0));
                } else {
                    drawPanelBackground(boxX, boxY, mainPanelWidth, totalHeight, headerHeight, radius);
                }
                drawPanelInfo(boxX, boxY, mainPanelWidth, headerHeight, padding, infoFontSize, name, hp);

                // Head Background
                if (!isModern) {
                    if (entry != null && entry.getSkinTextures() != null) {
                        NanoVGHelper.drawRoundRect(headX - 1, headY - 1, headSize + 2, headSize + 2, 4, new Color(172, 172, 174, 47));
                    } else {
                        NanoVGHelper.drawRoundRect(headX, headY, headSize, headSize, 4, new Color(168, 168, 170, 52));
                    }
                }
            });

            // Batch 2: Vanilla Rendering (Head Texture + Items)
            drawPlayerHeadTexture(context, entry, posX, posY, finalScale, headX, headY, headSize);
            if (armor.get()) {
                drawEquipmentItems(context, stacks, posX, posY, finalScale, headX, headY, headSize, padding, enchantHeight, itemSize, itemSpacing);
            }

            // Batch 3: Equipment Overlays (NanoVG)
            if (armor.get() || (itemName.get() && !mainHandName.isEmpty())) {
                drawNvg(posX, posY, finalScale, vg -> {
                    if (armor.get()) {
                        drawEquipmentText(vg, stacks, headX, headY, headSize, padding, enchantHeight, itemSize, itemSpacing, enchantFontSize);
                    }
                    if (itemName.get() && !mainHandName.isEmpty()) {
                        float itemAreaX = headX + headSize + padding;
                        float itemY = headY + enchantHeight;
                        float nameY = itemY + itemSize + 6;
                        float nameWidth = NanoVGHelper.getTextWidth(mainHandName, FontLoader.regular(10), 10);
                        float nameX = itemAreaX + itemsWidth / 2 - nameWidth / 2;
                        NanoVGHelper.drawString(mainHandName, nameX, nameY + 8, FontLoader.regular(10), 10, new Color(184, 184, 186, 184));
                    }
                });
            }

            // Draw Win Rate (Normal)
            if (winRate.get()) {
                float rateBoxX = boxX + mainPanelWidth + 4;
                float rateBoxH = headerHeight;

                if (blur.get()) {
                    float scaledRateW = rateBoxW * finalScale;
                    float scaledRateH = rateBoxH * finalScale;
                    float scaledRadius = radius * finalScale;
                    
                    float scaledFullWidth = fullWidth * finalScale;
                    float scaledStartX = posX - scaledFullWidth / 2;
                    float scaledRateBoxX = scaledStartX + (mainPanelWidth + 4) * finalScale;

                    Shader2DUtil.drawRoundedBlur(new MatrixStack(),
                            scaledRateBoxX, posY - (totalHeight * finalScale), scaledRateW, scaledRateH, scaledRadius,
                            new Color(0, 0, 0, 0),
                            blurStrength.get().floatValue(), 1.0f
                    );
                }

                final float finalRateBoxX = rateBoxX;
                final float finalRateBoxW = rateBoxW;
                final float finalRateBoxH = rateBoxH;
                final String finalRateText = rateText;
                final Color finalRateColor = rateColor;

                drawNvg(posX, posY, finalScale, vg -> {
                    Color rbColor = isModern ? new Color(20, 20, 20, 180) : new Color(158, 158, 158, 48);
                    NanoVGHelper.drawRoundRect(finalRateBoxX, boxY, finalRateBoxW, finalRateBoxH, radius, rbColor);
                    if (isModern) NanoVGHelper.drawGradientRRect(finalRateBoxX + 2, boxY, finalRateBoxW - 4, 1.5f, 1f, ClickGui.color(0), ClickGui.color2(0));
                    else NanoVGHelper.drawRoundRect(finalRateBoxX + 2, boxY + 2, finalRateBoxW - 4, finalRateBoxH - 4, radius - 2, new Color(171, 171, 172, 48));

                    float textY = boxY + finalRateBoxH / 2f + 1;
                    NanoVGHelper.drawString(finalRateText, finalRateBoxX + padding, textY, FontLoader.bold(11), infoFontSize, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE, finalRateColor);
                });
            }
        }
    }

    private void drawMinecraftEnchants(DrawContext context, ItemStack stack, float x, float y) {
        var enchantments = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (enchantments != null && !enchantments.isEmpty()) {
            float enchantY = y - 8;
            for (var enchEntry : enchantments.getEnchantments()) {
                String shortName = getEnchantShortName(enchEntry);
                int level = enchantments.getLevel(enchEntry);
                if (shortName.isEmpty()) continue;

                String text = shortName + (level > 1 ? level : "");
                Matrix3x2fStack matrices = context.getMatrices();
                matrices.pushMatrix();
                matrices.translate(x, enchantY);
                matrices.scale(0.5f, 0.5f);
                matrices.translate(-x, -enchantY);
                context.drawTextWithShadow(mc.textRenderer, text, 0, 0, -1);
                matrices.popMatrix();
                enchantY -= 5;
            }
        }
    }

    private List<ItemStack> getPlayerEquipment(PlayerEntity player) {
        List<ItemStack> stacks = new ArrayList<>();
        stacks.add(player.getMainHandStack());
        stacks.add(player.getEquippedStack(EquipmentSlot.HEAD));
        stacks.add(player.getEquippedStack(EquipmentSlot.CHEST));
        stacks.add(player.getEquippedStack(EquipmentSlot.LEGS));
        stacks.add(player.getEquippedStack(EquipmentSlot.FEET));
        stacks.add(player.getOffHandStack());
        return stacks;
    }

    private String getPlayerMainHandName(PlayerEntity player) {
        return (itemName.get() && !player.getMainHandStack().isEmpty())
                ? getEnglishItemName(player.getMainHandStack()) : "";
    }

    private int calculateMaxEnchants(List<ItemStack> stacks) {
        if (!enchants.get()) return 0;
        int maxEnchants = 0;
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                var ench = stack.get(DataComponentTypes.ENCHANTMENTS);
                if (ench != null) {
                    int count = 0;
                    for (var e : ench.getEnchantments()) {
                        String s = getEnchantShortName(e);
                        if (!s.isEmpty()) count++;
                    }
                    maxEnchants = Math.max(maxEnchants, count);
                }
            }
        }
        return maxEnchants;
    }

    private void drawPanelBackground(float x, float y, float width, float height, float headerHeight, float radius) {
        NanoVGHelper.drawRoundRect(x, y, width, height, radius, new Color(158, 158, 158, 48));
        NanoVGHelper.drawRoundRect(x + 2, y + 2, width - 4, headerHeight - 2, radius - 2, new Color(171, 171, 172, 48));
    }

    private void drawPanelInfo(float x, float y, float width, float headerHeight, float padding, float fontSize, String name, float hp) {
        float headerY = y + headerHeight - 5;

        String healthStr = health.get() ? String.format("%.1f", hp) : "";
        float nameW = getColoredTextWidth(name, FontLoader.bold(11), fontSize);
        float hpW = health.get() ? NanoVGHelper.getTextWidth(healthStr, FontLoader.bold(11), fontSize) : 0;
        float gap = health.get() ? 4 : 0;
        float totalTextW = nameW + gap + hpW;

        float startX = x + (width - totalTextW) / 2;

        // Name
        drawColoredString(name, startX, headerY, FontLoader.bold(11), fontSize, NVG_ALIGN_LEFT | NVG_ALIGN_BASELINE, Color.WHITE);

        // Health
        if (health.get()) {
            Color healthColor = getHealthColor(hp);
            NanoVGHelper.drawString(healthStr, startX + nameW + gap, headerY, FontLoader.bold(11), fontSize, healthColor);
        }
    }

    private void drawPlayerHeadTexture(DrawContext context, PlayerListEntry entry, float posX, float posY, float scale, float headX, float headY, float headSize) {
        if (entry != null && entry.getSkinTextures() != null) {
            Matrix3x2fStack matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.translate(posX, posY);
            matrices.scale(scale, scale);
            matrices.translate(-posX, -posY);

            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(770, 771, 1, 0);

            context.drawTexture(RenderPipelines.GUI_TEXTURED,
                    entry.getSkinTextures().body().texturePath(),
                    (int) headX, (int) headY,
                    8, 8,
                    (int) headSize, (int) headSize,
                    8, 8,
                    64, 64);
            context.drawTexture(RenderPipelines.GUI_TEXTURED,
                    entry.getSkinTextures().body().texturePath(),
                    (int) headX, (int) headY,
                    40, 8,
                    (int) headSize, (int) headSize,
                    8, 8,
                    64, 64);

            GlStateManager._disableBlend();
            matrices.popMatrix();
        }
    }

    private void drawEquipmentItems(DrawContext context, List<ItemStack> stacks, float posX, float posY, float scale, float headX, float headY, float headSize, float padding, float enchantHeight, float itemSize, float itemSpacing) {
        float itemAreaX = headX + headSize + padding;
        float itemY = headY + enchantHeight;
        float currentItemX = itemAreaX;

        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(posX, posY);
        matrices.scale(scale, scale);
        matrices.translate(-posX, -posY);

        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                context.drawItem(stack, (int) currentItemX, (int) itemY);
            }
            currentItemX += itemSize + itemSpacing;
        }

        matrices.popMatrix();
    }

    private void drawEquipmentText(long vg, List<ItemStack> stacks, float headX, float headY, float headSize, float padding, float enchantHeight, float itemSize, float itemSpacing, float enchantFontSize) {
        float itemAreaX = headX + headSize + padding;
        float itemY = headY + enchantHeight;
        float currentItemX = itemAreaX;

        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                // Draw Count
                if (stack.getCount() > 1) {
                    String countStr = String.valueOf(stack.getCount());
                    NanoVGHelper.drawString(countStr, currentItemX + 9, itemY + 12, FontLoader.bold(7), 7, Color.WHITE);
                }

                // Draw Enchants
                if (enchants.get()) {
                    drawItemEnchants(vg, stack, currentItemX, itemY, enchantFontSize);
                }
            }
            currentItemX += itemSize + itemSpacing;
        }
    }

    private String toLegacyText(Text text) {
        if (text == null) return "";
        StringBuilder builder = new StringBuilder();
        Style[] lastStyle = {Style.EMPTY};

        text.asOrderedText().accept((i, style, codePoint) -> {
            if (!style.equals(lastStyle[0])) {
                builder.append('§').append('r');
                appendStyleCodes(builder, style);
                lastStyle[0] = style;
            }
            builder.appendCodePoint(codePoint);
            return true;
        });

        return builder.toString();
    }

    private void appendStyleCodes(StringBuilder builder, Style style) {
        TextColor color = style.getColor();
        if (color != null) {
            int rgb = color.getRgb() & 0xFFFFFF;
            Character code = RGB_TO_CODE.get(rgb);
            if (code != null) {
                builder.append('§').append(code);
            } else {
                builder.append("§#").append(String.format("%06X", rgb));
            }
        }
        if (style.isObfuscated()) builder.append("§k");
        if (style.isBold()) builder.append("§l");
        if (style.isStrikethrough()) builder.append("§m");
        if (style.isUnderlined()) builder.append("§n");
        if (style.isItalic()) builder.append("§o");
    }

    private float getColoredTextWidth(String text, int font, float fontSize) {
        float width = 0;
        StringBuilder segment = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                if (!segment.isEmpty()) {
                    width += NanoVGHelper.getTextWidth(segment.toString(), font, fontSize);
                    segment.setLength(0);
                }
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (code == '#' && i + 7 < text.length()) {
                    String hex = text.substring(i + 2, i + 8);
                    if (isHexColor(hex)) {
                        i += 7;
                        continue;
                    }
                }
                i++;
            } else {
                segment.append(c);
            }
        }

        if (!segment.isEmpty()) {
            width += NanoVGHelper.getTextWidth(segment.toString(), font, fontSize);
        }

        return width;
    }

    private boolean isHexColor(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean isHex = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!isHex) return false;
        }
        return value.length() == 6;
    }

    private void drawColoredString(String text, float x, float y, int font, float fontSize, int align, Color defaultColor) {
        float currentX = x;
        Color currentColor = defaultColor;
        StringBuilder segment = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                if (!segment.isEmpty()) {
                    Color drawColor = new Color(currentColor.getRed(), currentColor.getGreen(), currentColor.getBlue(), defaultColor.getAlpha());
                    NanoVGHelper.drawString(segment.toString(), currentX, y, font, fontSize, align, drawColor);
                    currentX += NanoVGHelper.getTextWidth(segment.toString(), font, fontSize);
                    segment.setLength(0);
                }
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (code == '#' && i + 7 < text.length()) {
                    String hex = text.substring(i + 2, i + 8);
                    if (isHexColor(hex)) {
                        currentColor = new Color(Integer.parseInt(hex, 16));
                        i += 7;
                        continue;
                    }
                }
                Color newColor = COLOR_CODES.get(code);
                if (newColor != null) {
                    currentColor = newColor;
                } else if (code == 'r') {
                    currentColor = new Color(255, 255, 255);
                }
                i++;
            } else {
                segment.append(c);
            }
        }

        if (!segment.isEmpty()) {
            Color drawColor = new Color(currentColor.getRed(), currentColor.getGreen(), currentColor.getBlue(), defaultColor.getAlpha());
            NanoVGHelper.drawString(segment.toString(), currentX, y, font, fontSize, align, drawColor);
        }
    }

    private void drawItemEnchants(long vg, ItemStack stack, float itemX, float itemY, float enchantFontSize) {
        var enchantments = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (enchantments != null && !enchantments.isEmpty()) {
            float enchantY = itemY - 2;
            for (var enchEntry : enchantments.getEnchantments()) {
                String shortName = getEnchantShortName(enchEntry);
                int level = enchantments.getLevel(enchEntry);
                if (shortName.isEmpty()) continue;

                String levelStr = level > 1 ? String.valueOf(level) : "";
                float shortWidth = NanoVGHelper.getTextWidth(shortName, FontLoader.regular(7), enchantFontSize);
                NanoVGHelper.drawString(shortName, itemX + 8 - shortWidth / 2, enchantY, FontLoader.regular(7), enchantFontSize, new Color(187, 187, 191, 52));
                if (!levelStr.isEmpty()) {
                    NanoVGHelper.drawString(levelStr, itemX + 8 + shortWidth / 2, enchantY, FontLoader.regular(7), enchantFontSize, new Color(255, 100, 100));
                }
                enchantY -= (enchantFontSize + 1);
            }
        }
    }

    private String getEnglishItemName(ItemStack stack) {
        String key = stack.getItem().getTranslationKey();
        String name = key.substring(key.lastIndexOf('.') + 1);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_') {
                result.append(' ');
            } else if (i == 0 || name.charAt(i - 1) == '_') {
                result.append(Character.toUpperCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private String getEnchantShortName(RegistryEntry<Enchantment> enchant) {
        String id = enchant.getIdAsString();
        if (id.contains("blast_protection")) return "Bla";
        if (id.contains("fire_protection")) return "Fir";
        if (id.contains("projectile_protection")) return "Pro";
        if (id.contains("protection")) return "Pro";
        if (id.contains("thorns")) return "Tho";
        if (id.contains("sharpness")) return "Sha";
        if (id.contains("efficiency")) return "Eff";
        if (id.contains("unbreaking")) return "Unb";
        if (id.contains("power")) return "Pow";
        if (id.contains("mending")) return "Men";
        if (id.contains("feather_falling")) return "Fea";
        if (id.contains("depth_strider")) return "Dep";
        if (id.contains("frost_walker")) return "Fro";
        if (id.contains("soul_speed")) return "Sou";
        if (id.contains("swift_sneak")) return "Swi";
        if (id.contains("respiration")) return "Res";
        if (id.contains("aqua_affinity")) return "Aqu";
        if (id.contains("fire_aspect")) return "Fir";
        if (id.contains("looting")) return "Loo";
        if (id.contains("knockback")) return "Kno";
        if (id.contains("smite")) return "Smi";
        if (id.contains("bane")) return "Ban";
        if (id.contains("sweeping")) return "Swe";
        if (id.contains("fortune")) return "For";
        if (id.contains("silk_touch")) return "Sil";
        if (id.contains("vanishing")) return "Van";
        if (id.contains("binding")) return "Bin";
        if (id.contains("loyalty")) return "Loy";
        if (id.contains("riptide")) return "Rip";
        if (id.contains("channeling")) return "Cha";
        if (id.contains("impaling")) return "Imp";
        if (id.contains("multishot")) return "Mul";
        if (id.contains("quick_charge")) return "Qui";
        if (id.contains("piercing")) return "Pie";
        return "";
    }

    private Color getHealthColor(float health) {
        if (health >= 15) return new Color(100, 255, 100);
        if (health >= 10) return new Color(255, 255, 100);
        if (health >= 5) return new Color(255, 165, 0);
        return new Color(255, 100, 100);
    }

    private float calculateWinRate(PlayerEntity enemy) {
        if (mc.player == null) return 0.5f;
        // Range Check
        if (mc.player.distanceTo(enemy) > winRateRange.get()) {
            return 0.5f;
        }

        float myHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        float enemyHealth = HealthUtil.getEntityHealth(enemy);

        double myDamage = getAttackDamage(mc.player.getMainHandStack(), mc.player);
        double enemyDamage = getAttackDamage(enemy.getMainHandStack(), enemy);

        double damageToEnemy = applyArmor(enemy, myDamage);
        damageToEnemy = DamageUtil.applyResistance(enemy, damageToEnemy);
        damageToEnemy = DamageUtil.applyProtection(enemy, damageToEnemy, false);

        double damageToMe = applyArmor(mc.player, enemyDamage);
        damageToMe = DamageUtil.applyResistance(mc.player, damageToMe);
        damageToMe = DamageUtil.applyProtection(mc.player, damageToMe, false);

        if (damageToEnemy <= 0) damageToEnemy = 0.1;
        if (damageToMe <= 0) damageToMe = 0.1;

        int hitsToKillEnemy = (int) Math.ceil(enemyHealth / damageToEnemy);
        int hitsToKillMe = (int) Math.ceil(myHealth / damageToMe);

        float baseWinRate = (float) hitsToKillMe / (hitsToKillMe + hitsToKillEnemy);
        
        // Weapon dominance bonus (~3%)
        if (myDamage > enemyDamage) {
            baseWinRate += 0.03f;
        } else if (myDamage < enemyDamage) {
            baseWinRate -= 0.03f;
        }

        // Speed Effect Bonus (1% per level difference)
        int mySpeed = 0;
        if (mc.player.hasStatusEffect(StatusEffects.SPEED)) {
            var effect = mc.player.getStatusEffect(StatusEffects.SPEED);
            if (effect != null) mySpeed = effect.getAmplifier() + 1;
        }
        int enemySpeed = 0;
        if (enemy.hasStatusEffect(StatusEffects.SPEED)) {
            var effect = enemy.getStatusEffect(StatusEffects.SPEED);
            if (effect != null) enemySpeed = effect.getAmplifier() + 1;
        }
        
        if (mySpeed != enemySpeed) {
            baseWinRate += (mySpeed - enemySpeed) * 0.01f;
        }
        
        // Void Detection Logic
        int myDist = getDistanceToVoid(mc.player);
        int enemyDist = getDistanceToVoid(enemy);
        
        float clampedMyDist = Math.min(myDist, 5.0f);
        float clampedEnemyDist = Math.min(enemyDist, 5.0f);
        
        if (clampedMyDist < 5.0f || clampedEnemyDist < 5.0f) {
            float mySafety = clampedMyDist / 5.0f;
            float enemySafety = clampedEnemyDist / 5.0f;
            float diff = mySafety - enemySafety;
            float modifier = diff * 0.03f;
            baseWinRate += modifier;
        }

        return Math.min(Math.max(baseWinRate, 0.0f), 1.0f);
    }

    private int getDistanceToVoid(PlayerEntity entity) {
        BlockPos entityPos = entity.getBlockPos();
        int minDist = 999;
        
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                if (x*x + z*z > 25) continue;
                
                BlockPos targetPos = entityPos.add(x, 0, z);
                if (isVoidColumn(targetPos)) {
                    int dist = (int) Math.sqrt(x*x + z*z);
                    if (dist < minDist) {
                        minDist = dist;
                    }
                }
            }
        }
        
        return minDist;
    }
    
    private boolean isVoidColumn(BlockPos pos) {
        if (mc.world == null) return false;
        
        int startY = pos.getY() - 1;
        int bottomY = mc.world.getBottomY();
        
        for (int y = startY; y >= bottomY; y--) {
            if (!mc.world.isAir(new BlockPos(pos.getX(), y, pos.getZ()))) {
                return false;
            }
        }
        
        return true;
    }

    private double applyArmor(PlayerEntity entity, double damage) {
        double armor = 0;
        double toughness = 0;

        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = entity.getEquippedStack(slot);
            if (stack.isEmpty()) continue;

            AttributeModifiersComponent modifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
            if (modifiers == null) {
                modifiers = stack.getItem().getDefaultStack().get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
            }

            if (modifiers != null) {
                for (var entry : modifiers.modifiers()) {
                    String attrId = entry.attribute().getIdAsString();
                    if (attrId.equals(EntityAttributes.ARMOR.getIdAsString())) {
                        armor += entry.modifier().value();
                    } else if (attrId.equals(EntityAttributes.ARMOR_TOUGHNESS.getIdAsString())) {
                        toughness += entry.modifier().value();
                    }
                }
            }
        }

        double f = 2 + toughness / 4;
        double factor = Math.min(Math.max(armor - damage / f, armor * 0.2), 20);
        return damage * (1 - factor / 25);
    }

    private double getAttackDamage(ItemStack stack, PlayerEntity player) {
        double damage = 1.0; 

        AttributeModifiersComponent modifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) {
            modifiers = stack.getItem().getDefaultStack().get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        }

        if (modifiers != null) {
            for (var entry : modifiers.modifiers()) {
                if (entry.attribute().getIdAsString().equals(EntityAttributes.ATTACK_DAMAGE.getIdAsString()) &&
                    (entry.slot().equals(AttributeModifierSlot.MAINHAND) || entry.slot().equals(AttributeModifierSlot.ANY))) {
                    damage += entry.modifier().value();
                }
            }
        }
        
        var enchantments = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (enchantments != null) {
             for (var entry : enchantments.getEnchantments()) {
                 if (entry.getIdAsString().contains("sharpness")) {
                     int level = enchantments.getLevel(entry);
                     damage += 0.5 * level + 0.5;
                 }
             }
        }

        if (player.hasStatusEffect(StatusEffects.STRENGTH)) {
            var effect = player.getStatusEffect(StatusEffects.STRENGTH);
            if (effect != null) {
                int level = effect.getAmplifier() + 1;
                damage += level * 3;
            }
        }
        if (player.hasStatusEffect(StatusEffects.WEAKNESS)) {
            var effect = player.getStatusEffect(StatusEffects.WEAKNESS);
            if (effect != null) {
                int level = effect.getAmplifier() + 1;
                damage -= level * 4;
            }
        }
        
        return Math.max(damage, 0);
    }
}
