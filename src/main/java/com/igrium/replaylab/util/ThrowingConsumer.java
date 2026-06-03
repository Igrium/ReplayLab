package com.igrium.replaylab.util;


public interface ThrowingConsumer<T, E extends Throwable> {
    void accept(T value) throws E;
}
