package com.igrium.replaylab.anim.modifier;

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

    /**
     * Sample the curve at regular intervals.
     *
     * @param startTime  start timestamp
     * @param endTime    end timestamp
     * @param resolution number of samples
     * @return array of sampled values
     */
    public double[] sampleCurve(double startTime, double endTime, int resolution) {
        if (resolution <= 0) {
            throw new IllegalArgumentException("resolution must be greater than zero");
        }
        double delta = (endTime - startTime) / resolution;
        double[] result = new double[resolution];

        for (int i = 0; i < resolution; i++) {
            double time = i * delta;
            result[i] = sample(time);
        }
        return result;
    }

    public double sample(double timestamp) {
        return sample(timestamp, modifiers.length);
    }

    /**
     * Sample the stack of modifiers
     *
     * @param timestamp Timestamp to sample at
     * @param level     Number of modifiers to include in the sample
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
