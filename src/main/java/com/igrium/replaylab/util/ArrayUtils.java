package com.igrium.replaylab.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ArrayUtils {
    /**
     * Invert an int array.
     *
     * @param array Array to invert. Every value must be unique.
     * @return The inverted array.
     * @apiNote Very specific requirements for supplied array. Will completely blow up of violated.
     */
    public static int[] invert(int[] array) {
        int[] dest = new int[array.length];
        for (int i = 0; i < array.length; i++) {
            dest[array[i]] = i;
        }
        return dest;
    }
}
