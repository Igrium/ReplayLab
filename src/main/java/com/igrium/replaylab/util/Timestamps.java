package com.igrium.replaylab.util;

import lombok.Getter;
import lombok.experimental.UtilityClass;

/**
 * Utility functions for converting between timestamp strings and milliseconds
 */
@UtilityClass
public class Timestamps {
    public enum Display {
        MILLISECONDS, SECONDS, MINUTES, HOURS;

        @Getter
        private final String translationKey;

        Display() {
            translationKey = "timestamp_display." + name().toLowerCase();
        }
    }

    private static final int HOURS_2_MS = 3_600_000;
    private static final int MINUTES_2_MS = 60_000;
    private static final int SECONDS_2_MS = 1_000;

    /**
     * Split the supplied timestamp into milliseconds, seconds, minutes, and hours
     * depending on the length of the supplied array
     *
     * @param ms   Millisecond value
     * @param dest Destination array
     */
    public static void splitTimestamp(int ms, int[] dest) {
        if (dest.length == 0) return;

        if (dest.length == 1) {
            dest[0] = ms;
            return;
        }

        dest[0] = ms % 1000;
        int totalSeconds = ms / 1000;

        if (dest.length == 2) {
            dest[1] = totalSeconds;
            return;
        }

        dest[1] = totalSeconds % 60;
        int totalMinutes = totalSeconds / 60;

        if (dest.length == 3) {
            dest[2] = totalMinutes;
            return;
        }

        dest[2] = totalMinutes % 60;
        dest[3] = totalMinutes / 60;
    }

    /**
     * Convert the supplied timestamp into a timestamp string
     *
     * @param ms       Timestamp in milliseconds
     * @param decimals The amount of decimal places to use for the seconds value
     * @param display  The display mode to use
     * @return <code>[hours]:[minuites]:[seconds].[milliseconds]</code>
     */
    public static String toTimestamp(int ms, int decimals, Display display) {
        if (display == Display.MILLISECONDS) {
            return String.valueOf(ms);
        }

        int length = display.ordinal() + 1;
        int[] split = new int[length];
        splitTimestamp(ms, split);
        // We only want to show two digits
        int rounded;
        if (decimals > 0) {
            int divisor = (int) Math.pow(10, 3 - decimals);
            rounded = split[0] / divisor;
        } else rounded = 0;

        return switch (display) {
            case HOURS -> String.format("%02d:%02d:%02d.%0" + decimals + "d", split[3], split[2], split[1], rounded);
            case MINUTES -> String.format("%02d:%02d.%0" + decimals + "d", split[2], split[1], rounded);
            case SECONDS -> String.format("%02d.%0" + decimals + "d", split[1], rounded);
            default -> "";
        };

    }

    public static String toTimestamp(int ms, Display display) {
        return toTimestamp(ms, 2, display);
    }

    /**
     * Convert a timestamp string to milliseconds.
     *
     * @param str <code>[hours]:[minutes]:[seconds].[milliseconds]</code>
     * @return milliseconds
     * @throws NumberFormatException If one of the numbers is improperly formated
     * @apiNote Inverse of {@code #toTimestamp}, but it also takes suffix-formatted strings.
     * A bare number with no colon and no suffix (e.g. <code>"5"</code>) is treated as seconds.
     */
    public static int fromTimestamp(String str) throws NumberFormatException {
        str = str.trim().toLowerCase();

        if (str.endsWith("ms")) {
            return Integer.parseInt(str.substring(0, str.length() - 2));
        } else if (str.endsWith("h")) {
            // Must parse inside if statement, or we get number format exception if there's no suffix
            float floatVal = Float.parseFloat(str.substring(0, str.length() - 1));
            return (int) (floatVal * HOURS_2_MS);
        } else if (str.endsWith("m")) {
            float floatVal = Float.parseFloat(str.substring(0, str.length() - 1));
            return (int) (floatVal * MINUTES_2_MS);
        } else if (str.endsWith("s")) {
            float floatVal = Float.parseFloat(str.substring(0, str.length() - 1));
            return (int) (floatVal * SECONDS_2_MS);
        }

        String[] split = str.split(":");

        if (split.length > 3) {
            throw new NumberFormatException("Too many colons (:) in timestamp string (max 2)");
        }

        int idx = split.length - 1;
        int ms = (int) (Float.parseFloat(split[idx]) * SECONDS_2_MS);
        idx--;
        if (idx >= 0) {
            ms += Integer.parseInt(split[idx]) * MINUTES_2_MS;
            idx--;
        }
        if (idx >= 0) {
            ms += Integer.parseInt(split[idx]) * HOURS_2_MS;
        }

        return ms;
    }
}
