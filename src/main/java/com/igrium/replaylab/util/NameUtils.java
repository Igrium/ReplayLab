package com.igrium.replaylab.util;

import lombok.experimental.UtilityClass;

import java.util.function.Predicate;

@UtilityClass
public final class NameUtils {

    /**
     * Make a given object name unique so it doesn't conflict with anything else in the scene.
     *
     * @param original Original object name.
     * @param inUse    A predicate to test if a given name is in use.
     * @return The unique name.
     */
    public static String makeNameUnique(String original, Predicate<? super String> inUse) {
        if (!inUse.test(original)) {
            return original;
        }

        int idx = 1;
        String updated;
        do {
            updated = original + "." + idx;
            idx++;
        } while (inUse.test(updated));

        return updated;
    }
}
