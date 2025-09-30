package com.igrium.replaylab.util;

import org.apache.commons.lang3.mutable.Mutable;

/**
 * I honestly don't know why Commons doesn't come with this.
 */
public class SimpleMutable<T> implements Mutable<T> {
    private T value;

    public SimpleMutable() {}

    public SimpleMutable(T initial) {
        this.value = initial;
    }

    @Override
    public T getValue() {
        return value;
    }

    @Override
    public void setValue(T value) {
        this.value = value;
    }
}
