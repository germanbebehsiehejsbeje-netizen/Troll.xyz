package dev.mzc.client.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.mzc.client.Sakura;
import dev.mzc.client.module.impl.client.Capes;
import dev.mzc.client.module.impl.client.Skin;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.util.AssetInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class MixinAbstractClientPlayerEntity {
    @ModifyReturnValue(method = "getSkin", at = @At("RETURN"))
    private SkinTextures modifySkinTextures(SkinTextures original) {
        AbstractClientPlayerEntity player = (AbstractClientPlayerEntity) (Object) this;

        // 披风替换
        Capes capes = Sakura.MODULES.getModule(Capes.class);
        AssetInfo.TextureAsset newCape = original.cape();
        AssetInfo.TextureAsset newElytra = original.elytra();

        if (capes != null) {
            AssetInfo.TextureAsset modCape = capes.getCape(player, false);
            AssetInfo.TextureAsset modElytra = capes.getCape(player, true);
            if (modCape != null) newCape = modCape;
            if (modElytra != null) newElytra = modElytra;
        }

        // 皮肤替换
        Skin skinModule = Sakura.MODULES.getModule(Skin.class);
        if (skinModule != null && skinModule.isEnabled()) {
            boolean isSelf = player.equals(Sakura.mc.player);
            boolean shouldApply = isSelf || skinModule.applyToOthers.get();
            if (shouldApply) {
                AssetInfo.TextureAsset customSkin = skinModule.getSkin();
                if (customSkin != null) {
                    PlayerSkinType model = original.model();
                    Skin.ArmModel pref = skinModule.armModel.get();
                    if (pref == Skin.ArmModel.Slim) model = PlayerSkinType.SLIM;
                    else if (pref == Skin.ArmModel.Wide) model = PlayerSkinType.WIDE;
                    return new SkinTextures(
                            customSkin,
                            newCape,
                            newElytra,
                            model,
                            original.secure()
                    );
                }
            }
        }

        if (newCape == original.cape() && newElytra == original.elytra()) {
            return original;
        }

        return new SkinTextures(
                original.body(),
                newCape,
                newElytra,
                original.model(),
                original.secure()
        );
    }
}
