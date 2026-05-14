package dev.mzc.client.module.impl.misc;

import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.NumberValue;

import java.util.concurrent.ThreadLocalRandom;

public class FakeCoords extends Module {
    private final NumberValue<Integer> minOffsetX = new NumberValue<>("MinOffsetX", 100000, 100000, 10000000, 10000);
    private final NumberValue<Integer> minOffsetZ = new NumberValue<>("MinOffsetZ", 100000, 100000, 10000000, 10000);
    private long xOffset;
    private long zOffset;

    public FakeCoords() {
        super("FakeCoords", Category.Misc);
        this.setType(ModuleType.Safe);
    }

    @Override
    protected void onEnable() {
        xOffset = randomAxisOffset(minOffsetX.get());
        zOffset = randomAxisOffset(minOffsetZ.get());
    }

    @Override
    protected void onDisable() {
        xOffset = 0L;
        zOffset = 0L;
    }

    public long getXOffset() {
        return xOffset;
    }

    public long getZOffset() {
        return zOffset;
    }

    private long randomAxisOffset(int minOffset) {
        long min = Math.max(100000L, minOffset);
        long magnitude = min + ThreadLocalRandom.current().nextLong(min);
        magnitude = ((magnitude + 15L) / 16L) * 16L;
        return ThreadLocalRandom.current().nextBoolean() ? magnitude : -magnitude;
    }
}
