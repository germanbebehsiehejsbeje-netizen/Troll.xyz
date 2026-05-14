package dev.mzc.client.values.impl;

import dev.mzc.client.values.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * ListValue 用于存储一个列表的选择项，主要用于BlockESP等需要选择多个方块/实体的场景
 */
public class ListValue<T> extends Value<List<T>> {

    public enum Type {
        BLOCK,
        ENTITY,
        ITEM
    }

    private final List<T> selected;
    private final Type type;

    public ListValue(String name, Value.Dependency visibility, Type type) {
        super(name, visibility);
        this.selected = new ArrayList<>();
        this.type = type;
    }

    public ListValue(String name, Supplier<Boolean> visibility, Type type) {
        this(name, (Value.Dependency) visibility::get, type);
    }

    public ListValue(String name, Type type) {
        this(name, () -> true, type);
    }

    public ListValue(String name) {
        this(name, () -> true, Type.BLOCK);
    }

    public Type getType() {
        return type;
    }

    @Override
    public List<T> get() {
        return selected;
    }

    @Override
    public void set(List<T> value) {
        this.selected.clear();
        if (value != null) {
            this.selected.addAll(value);
        }
    }
    
    public void add(T item) {
        if (!selected.contains(item)) {
            selected.add(item);
        }
    }
    
    public void remove(T item) {
        selected.remove(item);
    }
    
    public boolean contains(T item) {
        return selected.contains(item);
    }
    
    public void clear() {
        selected.clear();
    }
}
