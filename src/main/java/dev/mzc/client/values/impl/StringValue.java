package dev.mzc.client.values.impl;

import dev.mzc.client.values.Value;

public class StringValue extends Value<String> {
    private boolean onlyNumber;

    public StringValue(String name) {
        this(name, "", () -> true);
    }

    public StringValue(String name, String defaultValue, Dependency dependency) {
        super(name, dependency);
        this.value = defaultValue;
        this.defaultValue = defaultValue;
        this.onlyNumber = false;
    }

    public StringValue(String name, String defaultValue) {
        this(name, defaultValue, () -> true);
    }

    public StringValue(String name, String defaultValue, boolean onlyNumber, Dependency dependency) {
        super(name, dependency);
        this.value = defaultValue;
        this.defaultValue = defaultValue;
        this.onlyNumber = onlyNumber;
    }

    public StringValue(String name, String defaultValue, boolean onlyNumber) {
        this(name, defaultValue, onlyNumber, () -> true);
    }

    public String getText() {
        return get();
    }

    public boolean isOnlyNumber() {
        return onlyNumber;
    }

    public void setText(String text) {
        set(text);
    }

    public void setOnlyNumber(boolean onlyNumber) {
        this.onlyNumber = onlyNumber;
    }
}
