package com.igrium.replaylab.util;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.LinkedBlockingQueue;

public class SimpleBlockingQueue<T> extends LinkedBlockingQueue<T> {
    public SimpleBlockingQueue(int capacity) {
        super(capacity);
    }

    @Override
    public boolean offer(@NotNull T t) {
        try {
            super.put(t);
            return true;
        } catch (InterruptedException e) {
             return false;
        }
    }
}
