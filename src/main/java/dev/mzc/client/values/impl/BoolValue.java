package dev.mzc.client.values.impl;

import dev.mzc.client.values.Value;

public class BoolValue extends Value<Boolean> {
    public BoolValue(String name, boolean defaultValue, Dependency dependency) {
        super(name, dependency);
        this.value = defaultValue;
        this.defaultValue = defaultValue;
    }

    public BoolValue(String name, boolean defaultValue) {
        this(name, defaultValue, () -> true);
    }
}
