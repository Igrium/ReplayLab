package com.igrium.replaylab.scene;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import com.igrium.replaylab.scene.obj.SerializedReplayObject;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A simple map wrapper to store the serialized form of replay objects in a thread-safe way.
 */
public class SerializedObjectHolder {
    private final BiMap<String, SerializedReplayObject> objs = HashBiMap.create();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public @Nullable SerializedReplayObject get(String id) {
        try {
            lock.readLock().lock();
            return objs.get(id);
        } finally {
             lock.readLock().unlock();
        }
    }

    public @Nullable String getId(SerializedReplayObject obj) {
        try {
            lock.readLock().lock();
            return objs.inverse().get(obj);
        } finally {
            lock.readLock().unlock();
        }
    }

    public @Nullable SerializedReplayObject put(String id, SerializedReplayObject obj) {
        try {
            lock.writeLock().lock();
            return objs.put(id, obj);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public @Nullable SerializedReplayObject remove(String id) {
        try {
            lock.writeLock().lock();
            return objs.remove(id);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get a thread-safe copy of all the serialized objects in this holder.
     * @return Immutable shallow copy.
     */
    public BiMap<String, SerializedReplayObject> getAll() {
        try {
            lock.readLock().lock();
            return ImmutableBiMap.copyOf(objs);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Clear the contents of this holder and replace it with the contents of the supplied map.
     * @param objs Map to replace contents with.
     */
    public void replaceContents(Map<? extends String, ? extends SerializedReplayObject> objs) {
        try {
            lock.writeLock().lock();
            this.objs.clear();
            this.objs.putAll(objs);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
