package com.igrium.replaylab.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ArrayUtils {
    /**
     * Inverts a permutation represented as an int array.
     *
     * <p>Treats {@code array} as a mapping from index to value, and returns the
     * inverse mapping: for each {@code i}, {@code result[array[i]] == i}.
     *
     * @param array Array to invert. Must contain each value in the range
     *              {@code [0, array.length)} exactly once (i.e. a permutation
     *              of its own indices).
     * @return A new array of the same length representing the inverse permutation.
     * @apiNote No validation is performed. Values outside {@code [0, array.length)}
     * will throw {@link ArrayIndexOutOfBoundsException}, and duplicate
     * values will silently overwrite each other, producing incorrect results.
     */
    public static int[] invert(int[] array) {
        int[] dest = new int[array.length];
        for (int i = 0; i < array.length; i++) {
            dest[array[i]] = i;
        }
        return dest;
    }


}
