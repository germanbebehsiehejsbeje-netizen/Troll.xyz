package dev.mzc.client.module.impl.misc;

import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;

public class BetterChat extends Module {
    public BetterChat() {
        super("BetterChat", Category.Misc);
        this.setType(ModuleType.Safe);
    }

    public final BoolValue enableInputAnim = new BoolValue("InputAnimation", true);
    public final NumberValue<Integer> inputAnimTime = new NumberValue<>("InputTime", 300, 50, 1000, 50);

    public final BoolValue enableMessageAnim = new BoolValue("MessageAnimation", true);
    public final NumberValue<Integer> messageAnimTime = new NumberValue<>("MessageTime", 300, 50, 1000, 50);

    public final BoolValue stackDuplicates = new BoolValue("StackDuplicates", true);
    public final BoolValue removeMessageIndicator = new BoolValue("NoIndicator", false);
}
