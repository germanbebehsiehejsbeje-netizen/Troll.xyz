package dev.mzc.client.values.impl;

import dev.mzc.client.values.Value;

public class RangeValue<T extends Number> extends Value<RangeValue.Range<T>> {
    private final T min;
    private final T max;
    private final T step;

    public RangeValue(String name, T defaultMin, T defaultMax, T min, T max, T step, Dependency dependency) {
        super(name, dependency);
        this.min = min;
        this.max = max;
        this.step = step;

        T clampedMin = clampAndCast(defaultMin);
        T clampedMax = clampAndCast(defaultMax);
        if (clampedMin.doubleValue() > clampedMax.doubleValue()) {
            T temp = clampedMin;
            clampedMin = clampedMax;
            clampedMax = temp;
        }

        this.value = new Range<>(clampedMin, clampedMax);
        this.defaultValue = new Range<>(clampedMin, clampedMax);
    }

    public RangeValue(String name, T defaultMin, T defaultMax, T min, T max, T step) {
        this(name, defaultMin, defaultMax, min, max, step, () -> true);
    }

    @Override
    public void reset() {
        this.value = new Range<>(defaultValue.getMin(), defaultValue.getMax());
    }

    @Override
    public void set(Range<T> value) {
        if (value == null) return;
        set(value.getMin(), value.getMax());
    }

    public void set(T rangeMin, T rangeMax) {
        T clampedMin = clampAndCast(rangeMin);
        T clampedMax = clampAndCast(rangeMax);
        if (clampedMin.doubleValue() > clampedMax.doubleValue()) {
            T temp = clampedMin;
            clampedMin = clampedMax;
            clampedMax = temp;
        }
        this.value = new Range<>(clampedMin, clampedMax);
    }

    public void setMinValue(T rangeMin) {
        set(rangeMin, getMaxValue());
    }

    public void setMaxValue(T rangeMax) {
        set(getMinValue(), rangeMax);
    }

    public T getMinValue() {
        return value.getMin();
    }

    public T getMaxValue() {
        return value.getMax();
    }

    public T getMin() {
        return min;
    }

    public T getMax() {
        return max;
    }

    public T getStep() {
        return step;
    }

    private T clampAndCast(T val) {
        if (val == null) {
            return min;
        }

        Number number = val;
        if (min instanceof Integer) {
            number = number.intValue();
        } else if (min instanceof Float) {
            number = number.floatValue();
        } else if (min instanceof Double) {
            number = number.doubleValue();
        } else if (min instanceof Long) {
            number = number.longValue();
        }

        if (number.doubleValue() < min.doubleValue()) {
            return min;
        }
        if (number.doubleValue() > max.doubleValue()) {
            return max;
        }
        @SuppressWarnings("unchecked")
        T casted = (T) number;
        return casted;
    }

    public static class Range<N extends Number> {
        private N min;
        private N max;

        public Range(N min, N max) {
            this.min = min;
            this.max = max;
        }

        public N getMin() {
            return min;
        }

        public void setMin(N min) {
            this.min = min;
        }

        public N getMax() {
            return max;
        }

        public void setMax(N max) {
            this.max = max;
        }
    }
}
