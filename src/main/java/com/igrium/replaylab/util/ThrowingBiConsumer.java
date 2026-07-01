package com.igrium.replaylab.util;

public interface ThrowingBiConsumer<T, U, E extends Throwable> {
    void accept(T l, U r) throws E;
}
