package dev.mzc.client.module.impl.client;

import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.TranslationManager;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.lemonchat.client.ClientSession;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;

import java.util.Objects;

public class Capes extends Module {
    public Capes() {
        super("Capes", Category.Client);
        this.setType(ModuleType.All);
    }

    public enum CapeMode {
        Default(),
        Light(),
        Nichijou(),
        EnderMan(),
        IronGolem(),
        Pickaxe(),
        RedCreeper(),
        Cobalt(),
        Ketamine(),
        Prismarine(),
        Mojang(),
        Opal(),
        Edge(),
        FireFox();
        CapeMode() {
        }
    }

    public final EnumValue<CapeMode> capeMode = new EnumValue<>("Cape Mode", CapeMode.Default);

    public String getName() {
        return
                switch (capeMode.get()) {
                    case Default -> "cape_default";
                    case Light -> "cape_light";
                    case Nichijou -> "cape_nichijou";
                    case EnderMan -> "enderman";
                    case IronGolem -> "irongolem";
                    case Pickaxe -> "pickaxe";
                    case RedCreeper -> "redcreeper";
                    case Cobalt -> "cobalt";
                    case Ketamine -> "ketamine";
                    case Mojang -> "mojang";
                    case Opal -> "opal";
                    case Prismarine -> "prismarine";
                    case Edge -> "edge";
                    case FireFox -> "firefox";
                };
    }

    private AssetInfo.TextureAsset getTexture(String capeName) {
        return new AssetInfo.TextureAssetInfo(
                Identifier.of("sakura", "capes/" + capeName),
                Identifier.of("sakura", "textures/capes/" + capeName + ".png")
        );
    }

    public AssetInfo.TextureAsset getCape(AbstractClientPlayerEntity player, boolean elytra) {
        try {
            if (isEnabled() && player.equals(mc.player)) {
                return getTexture(getName());
            }

            if (ClientSession.get() != null && ClientSession.get().hasCape(player)) {
                return getTexture(ClientSession.get().getCapeName(player));
            }
            SkinTextures skin = Objects.requireNonNull(mc.getNetworkHandler().getPlayerListEntry(player.getUuid())).getSkinTextures();
            return elytra ? skin.elytra() : skin.cape();
        } catch (Exception e) {
            return null;
        }
    }
}
