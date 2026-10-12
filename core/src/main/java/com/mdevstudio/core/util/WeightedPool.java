package com.mdevstudio.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class WeightedPool<T> {

    private final List<T> values = new ArrayList<>();
    private final List<Double> cumulative = new ArrayList<>();
    private double total;

    public void add(T value, double weight) {
        if (weight <= 0) {
            return;
        }
        total += weight;
        values.add(value);
        cumulative.add(total);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public T pick() {
        double roll = ThreadLocalRandom.current().nextDouble(total);
        for (int i = 0; i < cumulative.size(); i++) {
            if (roll < cumulative.get(i)) {
                return values.get(i);
            }
        }
        return values.getLast();
    }
}
