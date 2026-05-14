package dev.mzc.client.module.impl.render;

import dev.mzc.client.events.render.item.HeldItemRendererEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.ColorValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;

import java.awt.*;

public class Chams extends Module {
    public Chams() {
        super("Chams", Category.Render);
        this.setType(ModuleType.All);
    }

    public final BoolValue handItems = new BoolValue("Hand Items", false);
    private final ColorValue handItemsColor = new ColorValue("Hand Items Color", new Color(0x9317DE5D, true), handItems::get);

    public final BoolValue players = new BoolValue("Players", false);
    public final ColorValue playerColor = new ColorValue("Player Color", new Color(0x932DD8E8, true), players::get);
    public final BoolValue playerTexture = new BoolValue("Player Texture", true, players::get);
    public final BoolValue playerHeldItems = new BoolValue("Player Held Items", true, players::get);
    public final BoolValue playerArmor = new BoolValue("Player Armor", true, players::get);

    public final BoolValue alternativeBlending = new BoolValue("Alternative Blending", true);

    @EventHandler
    public void onRenderHands(HeldItemRendererEvent event) {
        // 1.21.11 removed RenderSystem#setShaderColor; hand tint is handled in render pipeline/mixins.
    }

    public boolean shouldApplyHand(ArmedEntityRenderState state) {
        return isEnabled() && players.get() && playerHeldItems.get() && alternativeBlending.get() && state instanceof PlayerEntityRenderState;
    }

    public boolean shouldApplyArmor(LivingEntityRenderState state) {
        return isEnabled() && players.get() && playerArmor.get() && alternativeBlending.get() && state instanceof PlayerEntityRenderState;
    }
}
