package dev.mzc.client.module.impl.misc;

import dev.mzc.client.manager.impl.NotificationManager;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.TranslationManager;
import dev.mzc.client.values.impl.EnumValue;

public class Auto32k extends Module {

    private final EnumValue<Mode> mode = new EnumValue<>("Mode", Mode.Sword);

    private enum Mode {
        Sword(),
        Axe(),
        PickaxeFortune(),
        PickaxeSilkTouch(),
        Shovel(),
        Mace(),
        Armor(),
        Bedrock(),
        Barrier(),
        CommandBlock();

        Mode() {
        }
    }

    public Auto32k() {
        super("Auto32k", Category.Misc);
        this.setType(ModuleType.Hack);
    }

    @Override
    public void onEnable() {
        if (mc.player == null) {
            this.toggle();
            return;
        }

        switch (mode.get()) {
            case Sword -> sendCommand("/give @p netherite_sword[custom_name='{\"text\":\"MZC-Client\",\"italic\":false}',unbreakable={},enchantments={levels:{\"minecraft:sharpness\":255,\"minecraft:smite\":255,\"minecraft:bane_of_arthropods\":255}}] 1");
            case Axe -> sendCommand("/give @p netherite_axe[custom_name='{\"text\":\"MZC-Client\",\"italic\":false}',unbreakable={},enchantments={levels:{\"minecraft:sharpness\":255,\"minecraft:efficiency\":255,\"minecraft:smite\":255,\"minecraft:bane_of_arthropods\":255}}] 1");
            case PickaxeFortune -> sendCommand("/give @p netherite_pickaxe[custom_name='{\"text\":\"MZC-Client\",\"italic\":false}',unbreakable={},enchantments={levels:{\"minecraft:efficiency\":255,\"minecraft:fortune\":255}}] 1");
            case PickaxeSilkTouch -> sendCommand("/give @p netherite_pickaxe[custom_name='{\"text\":\"MZC-Client\",\"italic\":false}',unbreakable={},enchantments={levels:{\"minecraft:efficiency\":255,\"minecraft:silk_touch\":1}}] 1");
            case Shovel -> sendCommand("/give @p netherite_shovel[custom_name='{\"text\":\"MZC-Client\",\"italic\":false}',unbreakable={},enchantments={levels:{\"minecraft:efficiency\":255}}] 1");
            case Mace -> sendCommand("/give @p mace[custom_name='{\"text\":\"MZC-Client\",\"italic\":false}',unbreakable={},enchantments={levels:{\"minecraft:density\":255,\"minecraft:breach\":255,\"minecraft:wind_burst\":2}}] 1");
            case Armor -> {
                sendCommand("/give @p netherite_helmet[custom_name='{\"text\":\"MZC-Client\",\"italic\":false}',unbreakable={},enchantments={levels:{\"minecraft:protection\":255}}] 1");
                sendCommand("/give @p netherite_chestplate[custom_name='{\"text\":\"MZC-Client\",\"italic\":false}',unbreakable={},enchantments={levels:{\"minecraft:protection\":255}}] 1");
                sendCommand("/give @p netherite_leggings[custom_name='{\"text\":\"MZC-Client\",\"italic\":false}',unbreakable={},enchantments={levels:{\"minecraft:protection\":255}}] 1");
                sendCommand("/give @p netherite_boots[custom_name='{\"text\":\"MZC-Client\",\"italic\":false}',unbreakable={},enchantments={levels:{\"minecraft:protection\":255}}] 1");
            }
            case Bedrock -> sendCommand("/give @p bedrock[custom_name='{\"text\":\"MZC-Client\",\"italic\":false}'] 64");
            case Barrier -> sendCommand("/give @p barrier[custom_name='{\"text\":\"MZC-Client\",\"italic\":false}'] 64");
            case CommandBlock -> sendCommand("/give @p command_block[custom_name='{\"text\":\"MZC-Client\",\"italic\":false}'] 64");
        }

        NotificationManager.send("Obtained " + TranslationManager.get(TranslationManager.enumKey(mode.get()), mode.get().name()));
        this.toggle();
    }

    private void sendCommand(String command) {
        if (!command.isEmpty()) {
            mc.player.networkHandler.sendChatCommand(command.substring(1));
        }
    }
}
