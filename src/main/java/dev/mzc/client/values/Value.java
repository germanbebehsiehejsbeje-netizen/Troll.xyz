package dev.mzc.client.values;

import dev.mzc.client.utils.TranslationManager;

public abstract class Value<V> {
    protected final Dependency dependency;
    protected V value;
    protected V defaultValue;
    protected final String name;
    protected final String translationKey;

    public Value(String name, Dependency dependency) {
        this.name = name;
        this.translationKey = TranslationManager.valueKey(name);
        this.dependency = dependency;
    }

    public void reset() {
        this.value = this.defaultValue;
    }

    public V getDefaultValue() {
        return defaultValue;
    }

    public Value(String name) {
        this(name, () -> true);
    }

    public V get() {
        return value;
    }

    public void set(V value) {
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return TranslationManager.get(translationKey, name);
    }

    public String getChineseName() {
        return TranslationManager.getChinese(translationKey, name);
    }

    public boolean isAvailable() {
        return dependency != null && this.dependency.check();
    }

    @FunctionalInterface
    public interface Dependency {
        boolean check();
    }

    public Dependency getDependency() {
        return dependency;
    }
}
