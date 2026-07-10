package com.igrium.replaylab.anim.modifier;

import it.unimi.dsi.fastutil.doubles.Double2DoubleFunction;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.util.function.DoubleUnaryOperator;

public class CurveModifierSampler {
    // I get the feeling this is one of those pieces of code where I'll have no idea what it does in a week

    private final Map<Double, Map<Integer, ComputedValue>> cache = new ConcurrentHashMap<>();

    private final CurveModifier[] modifiers;
    private final DoubleUnaryOperator baseSampleFunction;

    public CurveModifierSampler(List<? extends CurveModifier> modifiers, DoubleUnaryOperator baseSampleFunction) {
        // Modifiers are supposed to be frozen, but we don't want someone fucking with the list
        this.modifiers = modifiers.toArray(CurveModifier[]::new);
        this.baseSampleFunction = baseSampleFunction;
    }

    public double sample(double timestamp) {
        return sample(timestamp, modifiers.length);
    }

    /**
     * Sample the stack of modifiers
     * @param timestamp Timestamp to sample at
     * @param level Number of modifiers to include in the sample
     * @return Sampled value
     */
    public double sample(double timestamp, int level) {
        if (level > modifiers.length) {
            throw new IndexOutOfBoundsException("Level " + level + " out of bounds for modifier count " + modifiers.length);
        }

        Map<Integer, ComputedValue> tsCache = cache.computeIfAbsent(timestamp, k -> new ConcurrentHashMap<>());
        ComputedValue val = tsCache.computeIfAbsent(level, v -> new ComputedValue());

        // No need to fight over lock.
        if (val.completed) {
            return val.value;
        }

        synchronized (val) {
            // Might have completed while waiting on the lock
            if (val.completed) {
                return val.value;
            }
            if (level == 0) {
                val.value = baseSampleFunction.applyAsDouble(timestamp);
            } else {
                val.value = modifiers[level - 1].compute(timestamp, ts -> sample(ts, level - 1));
            }
            val.completed = true;
        }
        return val.value;
    }


    private static class ComputedValue {
        volatile boolean completed;
        volatile double value;
    }

}
