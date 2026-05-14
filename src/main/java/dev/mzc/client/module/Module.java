package dev.mzc.client.module;

import dev.mzc.client.Sakura;
import dev.mzc.client.auth.AuthManager;
import dev.mzc.client.auth.UserRole;
import dev.mzc.client.module.impl.hud.DynamicIslandHud;
import dev.mzc.client.module.impl.hud.ModuleListHud;
import dev.mzc.client.utils.TranslationManager;
import dev.mzc.client.utils.animations.Animation;
import dev.mzc.client.utils.animations.Direction;
import dev.mzc.client.utils.animations.impl.DecelerateAnimation;
import dev.mzc.client.values.Value;
import dev.mzc.client.values.impl.BoolValue;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

public class Module {
    public enum BindMode {
        Toggle, Hold
    }

    public enum ModuleType {
        Safe, Hack, All
    }

    private final String englishName;
    private final String translationKey;
    private boolean state;
    private final Category category;
    private ModuleType type = ModuleType.Safe;
    private UserRole requiredRole = UserRole.USER;
    private String suffix = "";
    private int key = -1;
    private BindMode bindMode = BindMode.Toggle;
    private final BoolValue hidden;
    public final List<Value<?>> values = new ArrayList<>();
    private final Animation animations = new DecelerateAnimation(250, 1).setDirection(Direction.BACKWARDS);

    protected final MinecraftClient mc;

    public Module(String englishName, Category category) {
        this.englishName = englishName;
        this.translationKey = TranslationManager.moduleKey(englishName);
        this.category = category;
        this.mc = MinecraftClient.getInstance();
        this.hidden = new BoolValue("Hidden", false);
        this.values.add(this.hidden);
    }

    public void setRequiredRole(UserRole role) {
        this.requiredRole = role;
    }

    public UserRole getRequiredRole() {
        return requiredRole;
    }

    protected boolean nullCheck() {
        return mc.player == null || mc.world == null;
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    public boolean isEnabled() {
        return state;
    }

    public boolean isDisabled() {
        return !state;
    }

    public <M extends Module> boolean isEnabled(Class<M> module) {
        Module mod = Sakura.MODULES.getModule(module);
        return mod != null && mod.isEnabled();
    }

    public void setSuffix(String tag) {
        if (tag != null && !tag.isEmpty()) {
            this.suffix = "" + tag;
        } else {
            this.suffix = "";
        }
    }

    public void toggle() {
        setState(!state);
    }

    public void setState(boolean state) {
        if (state && !AuthManager.getRole().isAtLeast(requiredRole)) {
            Sakura.LOGGER.info("权限不足: " + getEnglishName() + " 需要 " + requiredRole.getDisplayName());
            return;
        }

        if (this.state != state) {
            this.state = state;
            DynamicIslandHud.onModuleToggle(this, state);
            ModuleListHud.onModuleToggle(this, state);
            if (state) {
                Sakura.EVENT_BUS.subscribe(this);
                onEnable();
            } else {
                Sakura.EVENT_BUS.unsubscribe(this);
                onDisable();
            }
        }
    }

    public void reset() {
        setState(false);
        if (!englishName.equalsIgnoreCase("ClickGui")) {
            setKey(-1);
        }
        setBindMode(BindMode.Toggle);
        for (Value<?> value : values) {
            value.reset();
        }
    }

    public String getDisplayName() {
        return TranslationManager.get(translationKey, englishName);
    }

    public String getEnglishName() {
        return englishName;
    }

    public String getChineseName() {
        return TranslationManager.getChinese(translationKey, englishName);
    }

    public boolean isState() {
        return state;
    }

    public Category getCategory() {
        return category;
    }

    public String getSuffix() {
        return suffix;
    }

    public int getKey() {
        return key;
    }

    public List<Value<?>> getValues() {
        return values;
    }

    public boolean isHidden() {
        return hidden.get();
    }

    public Animation getAnimations() {
        return animations;
    }

    public void setKey(int key) {
        this.key = key;
    }

    public BindMode getBindMode() {
        return bindMode;
    }

    public void setBindMode(BindMode bindMode) {
        this.bindMode = bindMode;
    }

    public ModuleType getType() {
        return type;
    }

    public void setType(ModuleType type) {
        this.type = type;
    }
}
