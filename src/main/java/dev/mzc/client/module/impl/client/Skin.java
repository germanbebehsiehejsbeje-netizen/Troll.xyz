package dev.mzc.client.module.impl.client;

import dev.mzc.client.Sakura;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.utils.TranslationManager;
import dev.mzc.client.values.impl.EnumValue;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.StringValue;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;

import java.io.InputStream;

public class Skin extends Module {

    public final StringValue skinFile = new StringValue("SkinFile", "skin.png");
    public final BoolValue applyToOthers = new BoolValue("ApplyToOthers", false);
    public enum ArmModel {
        Auto, Wide, Slim;
    }
    public final EnumValue<ArmModel> armModel = new EnumValue<>("ArmModel", ArmModel.Auto, ArmModel.class);

    // 当前加载的皮肤纹理，null 表示未加载或加载失败
    private AssetInfo.TextureAsset cachedSkin = null;
    private String cachedFileName = null;

    public Skin() {
        super("Skin", Category.Client);
        this.setType(ModuleType.All);
    }

    @Override
    protected void onEnable() {
        reloadSkin();
    }

    @Override
    protected void onDisable() {
        cachedSkin = null;
        cachedFileName = null;
    }

    public void reloadSkin() {
        cachedSkin = null;
        cachedFileName = null;
        loadSkin(skinFile.get());
    }

    private void loadSkin(String fileName) {
        try {
            String resourcePath = "/assets/sakura/skin/" + fileName;
            InputStream is = Skin.class.getResourceAsStream(resourcePath);
            if (is == null) {
                Sakura.LOGGER.warn("[Skin] 皮肤资源不存在: {}", resourcePath);
                return;
            }
            try (is) {
                NativeImage image = NativeImage.read(is);
                String idPath = "skin/" + fileName.replace(" ", "_").toLowerCase();
                Identifier id = Identifier.of("sakura", idPath);
                mc.getTextureManager().registerTexture(id,
                        new NativeImageBackedTexture(() -> "sakura_skin_" + fileName, image));
                cachedSkin = new AssetInfo.TextureAssetInfo(id, id);
                cachedFileName = fileName;
                Sakura.LOGGER.info("[Skin] 皮肤加载成功: {}", fileName);
            }
        } catch (Exception e) {
            Sakura.LOGGER.error("[Skin] 皮肤加载失败: {}", e.getMessage());
        }
    }

    /**
     * 获取当前皮肤，如果文件名变了则重新加载
     */
    public AssetInfo.TextureAsset getSkin() {
        if (!isEnabled()) return null;
        String current = skinFile.get();
        if (cachedSkin == null || !current.equals(cachedFileName)) {
            loadSkin(current);
        }
        return cachedSkin;
    }
}
