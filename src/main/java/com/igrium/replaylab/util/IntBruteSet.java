package com.igrium.replaylab.util;

import it.unimi.dsi.fastutil.ints.AbstractIntSet;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntIterator;
import org.jetbrains.annotations.NotNull;

import java.util.NoSuchElementException;

/**
 * An int set that simply contains an array of true or false depending on the values in the set.
 * If you put a really high integer in, it will start to eat a lot of memory.
 * @apiNote Does not support negative values.
 */
public class IntBruteSet extends AbstractIntSet {

    private boolean[] array;
    private int size = 0;

    public IntBruteSet() {
        array = new boolean[16];
    }

    public IntBruteSet(int initialMaxValue) {
        array = new boolean[initialMaxValue + 1];
    }

    private void grow(int target) {
        if (target < array.length)
            return;

        // Allocate an array that accepts the target as the last index.
        boolean[] newArray = new boolean[target + 1];
        System.arraycopy(array, 0, newArray, 0, array.length);
        this.array = newArray;
    }

    @Override
    public @NotNull IntIterator iterator() {
        return new BruteIterator();
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean contains(int k) {
        return k >= 0 && k < array.length && array[k];
    }

    @Override
    public boolean add(int k) {
        if (k < 0) {
            throw new IndexOutOfBoundsException("Cannot add values less than zero.");
        }
        grow(k);
        if (!array[k]) {
            array[k] = true;
            size++;
            return true;
        }
        return false;
    }

    @Override
    public boolean addAll(IntCollection c) {
        // Identify number of non-present values in c.
        int maxMissing = -1;
        IntIterator iter = c.intIterator();
        while (iter.hasNext()) {
            int i = iter.nextInt();
            if (i >= 0 && !contains(i) && i > maxMissing) {
                maxMissing = i;
            }
        }

        if (maxMissing < 0)
            return false;

        grow(maxMissing);
        iter = c.intIterator();
        boolean updated = false;
        while (iter.hasNext()) {
            int i = iter.nextInt();
            if (i >= 0 && add(i)) {
                updated = true;
            }
        }

        return updated;
    }


    @Override
    public boolean remove(int k) {
        if (k < 0 || k >= array.length)
            return false;

        if (array[k]) {
            array[k] = false;
            size--;
            return true;
        }
        return false;
    }

    // No need to implement custom removeAll because we don't grow

    private class BruteIterator implements IntIterator {

        private enum State {
            /** We have computed the next element and haven't returned it yet. */
            READY,

            /** We haven't yet computed or have already returned the element. */
            NOT_READY,

            /** We have reached the end of the data and are finished. */
            DONE
        }

        private State state = State.NOT_READY;
        private int next = 0;
        private int nextScanIndex = 0;
        private int prevReturnedValue = -1;

        private void computeNext() {
            while (nextScanIndex < array.length) {
                if (array[nextScanIndex]) {
                    next = nextScanIndex;
                    state = State.READY;
                    nextScanIndex++;
                    return;
                }
                nextScanIndex++;
            }
            state = State.DONE;
        }

        @Override
        public int nextInt() {
            if (state == State.NOT_READY) {
                computeNext();
            }
            if (state == State.READY) {
                prevReturnedValue = next;
                state = State.NOT_READY;
                return prevReturnedValue;
            } else {
                throw new NoSuchElementException();
            }
        }

        @Override
        public boolean hasNext() {
            if (state == State.NOT_READY) {
                computeNext();
            }
            return state == State.READY;
        }

        @Override
        public void remove() {
            array[prevReturnedValue] = false;
            size--;
        }
    }
}
