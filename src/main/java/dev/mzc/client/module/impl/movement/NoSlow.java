package dev.mzc.client.module.impl.movement;

import dev.mzc.client.events.player.SlowdownEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.BoolValue;
import meteordevelopment.orbit.EventHandler;

public class NoSlow extends Module {
    private final BoolValue soulSand = new BoolValue("SoulSand", true);
    private final BoolValue berryBush = new BoolValue("BerryBush", true);
    private final BoolValue web = new BoolValue("Web", true);
    private final BoolValue item = new BoolValue("Item", true);
    private final BoolValue sneak = new BoolValue("Sneak", false);

    public NoSlow() {
        super("NoSlow", Category.Movement);
        this.setType(ModuleType.Hack);
    }

    public boolean isItem() {
        return item.get();
    }

    public boolean isSneak() {
        return sneak.get();
    }

    public boolean isSoulSand() {
        return soulSand.get();
    }

    @EventHandler
    public void onSlowdown(SlowdownEvent event) {
        if (event.getType() == SlowdownEvent.Type.Item && item.get()) {
            event.setSlowdown(false);
        } else if (event.getType() == SlowdownEvent.Type.Web && web.get()) {
            event.setSlowdown(false);
        } else if (event.getType() == SlowdownEvent.Type.SoulSand && soulSand.get()) {
            event.setSlowdown(false);
        } else if (event.getType() == SlowdownEvent.Type.BerryBush && berryBush.get()) {
            event.setSlowdown(false);
        } else if (event.getType() == SlowdownEvent.Type.Sneak && sneak.get()) {
            event.setSlowdown(false);
        }
    }
}
