package dev.mzc.client.module.impl.client;

import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.ColorValue;
import dev.mzc.client.values.impl.StringValue;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.awt.*;

public class Chat extends Module {
    public Chat() {
        super("Chat", Category.Client);
        this.setType(ModuleType.All);
    }

    public final StringValue prefix = new StringValue("Chat Prefix");
    public final BoolValue enable = new BoolValue("Enable", true);
    public final BoolValue enableTab = new BoolValue("Enable Tab", false);
    public final ColorValue color = new ColorValue("Color", Color.RED);

    public Text getPlayerName(PlayerListEntry playerListEntry) {
        Text name;
        name = playerListEntry.getDisplayName();
        if (name == null) name = Text.literal(playerListEntry.getProfile().name());
        return name;
    }
    
    public static void print(String message) {
        if (dev.mzc.client.Sakura.mc.player != null) {
            dev.mzc.client.Sakura.mc.player.sendMessage(Text.of("§7[MZC] §r" + message), false);
        }
    }
}
