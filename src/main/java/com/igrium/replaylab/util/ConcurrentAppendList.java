package com.igrium.replaylab.util;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.IntFunction;

/**
 * An append-only, random-access list supporting fully lock-free concurrent reads and CAS-coordinated appends.
 * <p>
 * Elements are stored in a series of geometrically-growing blocks (16, 32, 64, 128, ...), so an append never
 * copies existing elements; growing simply allocates a new block. Element slots are written exactly once and never
 * moved, which is what makes unsynchronized reads safe.
 * <p>
 * Appends are conditional: they only succeed if the list is currently exactly the expected size. This makes the
 * list usable as a memoization table for a sequence computed in order: many threads may race to compute element
 * {@code n}, but only one result is ever committed.
 * <p>
 * {@code null} elements are not permitted.
 *
 * @param <T> element type
 * @implNote This is AI slop, but it's Fable, so maybe it's okay? Bite me.
 */
public class ConcurrentAppendList<T> {

    /** log2 of the size of the first two blocks. */
    private static final int FIRST_BLOCK_BITS = 4;
    private static final int FIRST_BLOCK_SIZE = 1 << FIRST_BLOCK_BITS;

    /**
     * Block {@code b} holds {@code FIRST_BLOCK_SIZE << b} elements; enough blocks for indices
     * up to {@code Integer.MAX_VALUE}.
     */
    private static final int NUM_BLOCKS = Integer.SIZE - FIRST_BLOCK_BITS;

    private final AtomicReferenceArray<AtomicReferenceArray<T>> blocks = new AtomicReferenceArray<>(NUM_BLOCKS);

    /**
     * The number of committed elements. Only ever advances by 1, and only after the slot at the previous size has
     * been written, so every index below <code>size</code> is safe to read.
     */
    private final AtomicInteger size = new AtomicInteger();

    public ConcurrentAppendList() {
        blocks.set(0, new AtomicReferenceArray<>(FIRST_BLOCK_SIZE));
    }

    /** Which block the given element index lives in. */
    private static int blockIndex(int index) {
        // Offsetting by FIRST_BLOCK_SIZE makes the block boundaries land on powers of two.
        return (Integer.SIZE - 1 - Integer.numberOfLeadingZeros(index + FIRST_BLOCK_SIZE)) - FIRST_BLOCK_BITS;
    }

    /** Offset of the given element index within its block. */
    private static int blockOffset(int index, int blockIndex) {
        return (index + FIRST_BLOCK_SIZE) - (FIRST_BLOCK_SIZE << blockIndex);
    }

    /**
     * The current number of elements.
     */
    public int size() {
        return size.get();
    }

    /**
     * Get the element at the given index. Never blocks, regardless of concurrent reads or writes.
     *
     * @throws IndexOutOfBoundsException if {@code index < 0 || index >= size()}
     */
    public @NotNull T get(int index) {
        if (index < 0 || index >= size.get()) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for length " + size.get());
        }
        int block = blockIndex(index);
        // Non-null is guaranteed: size only advances past an index after its slot is committed.
        return blocks.get(block).get(blockOffset(index, block));
    }

    /**
     * Replace the element at an already-committed index. Lock-free; concurrent readers see either the old or the
     * new value, never a partial state. Only committed indices may be set, so this never interferes with an
     * in-flight append.
     *
     * @return the previous value at that index
     * @throws IndexOutOfBoundsException if {@code index < 0 || index >= size()}
     */
    public @NotNull T set(int index, @NotNull T value) {
        if (index < 0 || index >= size.get()) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for length " + size.get());
        }
        int block = blockIndex(index);
        return blocks.get(block).getAndSet(blockOffset(index, block), value);
    }

    /**
     * Unconditionally append a value, retrying until it lands. Lock-free: a failed attempt means another thread's
     * append succeeded, so the system as a whole always makes progress.
     *
     * @return the index the value was inserted at
     */
    public int add(@NotNull T value) {
        while (true) {
            int s = size.get();
            if (compareAndAdd(s, value)) {
                return s;
            }
        }
    }

    /**
     * Atomically append a value, but only if the list currently has exactly {@code expectedSize} elements
     * (i.e. the value would land at index {@code expectedSize}).
     *
     * @return {@code true} if this call's value was committed; {@code false} if the size didn't match or another
     *         thread committed index {@code expectedSize} first.
     */
    public boolean compareAndAdd(int expectedSize, @NotNull T value) {
        if (expectedSize < 0 || size.get() != expectedSize) {
            return false;
        }

        int block = blockIndex(expectedSize);
        AtomicReferenceArray<T> blockArr = getOrCreateBlock(block);
        int offset = blockOffset(expectedSize, block);

        if (blockArr.compareAndSet(offset, null, value)) {
            // We won the slot; publish it. This CAS can only fail if a losing racer already helped below.
            size.compareAndSet(expectedSize, expectedSize + 1);
            return true;
        } else {
            // Another thread committed this index first. Help it publish in case it hasn't yet, so a stalled
            // winner can't leave the committed element invisible.
            size.compareAndSet(expectedSize, expectedSize + 1);
            return false;
        }
    }

    /**
     * Like {@link #compareAndAdd}, but computes the value from the target index. The function is only invoked if
     * the size matches at the time of the call, and is invoked without holding any lock. If multiple threads race
     * on the same index, each may invoke its own function, but exactly one result is committed; the others are
     * discarded and those calls return {@code false}.
     *
     * @return {@code true} if this call's computed value was committed.
     */
    public boolean compareAndCompute(int expectedSize, IntFunction<@NotNull T> computeFn) {
        // Cheap pre-check so we don't compute when we obviously can't commit.
        if (expectedSize < 0 || size.get() != expectedSize) {
            return false;
        }
        int block = blockIndex(expectedSize);
        // Also skip computing if the slot is already claimed but not yet published.
        AtomicReferenceArray<T> blockArr = getOrCreateBlock(block);
        if (blockArr.get(blockOffset(expectedSize, block)) != null) {
            size.compareAndSet(expectedSize, expectedSize + 1);
            return false;
        }

        return compareAndAdd(expectedSize, computeFn.apply(expectedSize));
    }

    /**
     * Ensure the list is filled up to and including {@code index}, computing any missing elements in order, then
     * return the element at {@code index}. If the index is already committed this is just a read. Like
     * {@link #compareAndCompute}, the function is invoked without holding any lock, and racing threads may each
     * compute a given index, but exactly one result per index is ever committed — so once this returns, the value
     * at each index is stable (barring explicit {@link #set} calls). Threads racing on overlapping ranges share the
     * work rather than duplicating the whole range: each committed element advances every racer.
     *
     * @return the (possibly freshly computed) element at {@code index}
     * @throws IndexOutOfBoundsException if {@code index < 0}
     */
    public @NotNull T computeUpTo(int index, IntFunction<@NotNull T> computeFn) {
        if (index < 0) {
            throw new IndexOutOfBoundsException("Index " + index + " is negative");
        }
        int s;
        while ((s = size.get()) <= index) {
            // Either we commit index s, or another thread already did and the size check reflects it next loop.
            compareAndCompute(s, computeFn);
        }
        return get(index);
    }

    /**
     * Copy the elements into a new array. The length is snapshotted at the start of the call: elements appended
     * concurrently are not included, and every included slot is guaranteed non-null. Concurrent {@link #set} calls
     * may or may not be reflected.
     */
    public Object[] toArray() {
        int n = size.get();
        Object[] out = new Object[n];
        copyRange(out, n);
        return out;
    }

    /**
     * Typed variant of {@link #toArray()}, e.g. {@code list.toArray(String[]::new)}.
     */
    public T[] toArray(@NotNull IntFunction<T[]> generator) {
        int n = size.get();
        T[] out = generator.apply(n);
        copyRange(out, n);
        return out;
    }

    /** Walks whole blocks rather than going through {@link #get}, skipping per-element index math. */
    private void copyRange(Object[] out, int n) {
        int index = 0;
        for (int b = 0; index < n; b++) {
            AtomicReferenceArray<T> block = blocks.get(b);
            int count = Math.min(FIRST_BLOCK_SIZE << b, n - index);
            for (int i = 0; i < count; i++) {
                out[index++] = block.get(i);
            }
        }
    }

    private AtomicReferenceArray<T> getOrCreateBlock(int blockIndex) {
        AtomicReferenceArray<T> block = blocks.get(blockIndex);
        if (block == null) {
            block = new AtomicReferenceArray<>(FIRST_BLOCK_SIZE << blockIndex);
            if (!blocks.compareAndSet(blockIndex, null, block)) {
                block = blocks.get(blockIndex);
            }
        }
        return block;
    }
}
