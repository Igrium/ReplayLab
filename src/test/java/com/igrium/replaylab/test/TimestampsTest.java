package com.igrium.replaylab.test;

import com.igrium.replaylab.util.Timestamps;
import com.igrium.replaylab.util.Timestamps.Display;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Vibe-coded unit tests. Bite me.
 */
public class TimestampsTest {

    // 1h 2m 3s 40ms
    private static final int SAMPLE_MS = 3_600_000 + 120_000 + 3_000 + 40;

    @Test
    public void testSplitTimestamp_EmptyArray() {
        int[] dest = new int[0];
        Timestamps.splitTimestamp(SAMPLE_MS, dest);
        assertEquals(0, dest.length);
    }

    @Test
    public void testSplitTimestamp_Length1_IsRawMs() {
        int[] dest = new int[1];
        Timestamps.splitTimestamp(SAMPLE_MS, dest);
        assertEquals(SAMPLE_MS, dest[0]);
    }

    @Test
    public void testSplitTimestamp_Length2_MsAndSeconds() {
        int[] dest = new int[2];
        Timestamps.splitTimestamp(SAMPLE_MS, dest);
        assertEquals(40, dest[0]);
        assertEquals(3723, dest[1]); // total seconds
    }

    @Test
    public void testSplitTimestamp_Length3_MsSecondsMinutes() {
        int[] dest = new int[3];
        Timestamps.splitTimestamp(SAMPLE_MS, dest);
        assertEquals(40, dest[0]);
        assertEquals(3, dest[1]);
        assertEquals(62, dest[2]); // total minutes
    }

    @Test
    public void testSplitTimestamp_Length4_MsSecondsMinutesHours() {
        int[] dest = new int[4];
        Timestamps.splitTimestamp(SAMPLE_MS, dest);
        assertEquals(40, dest[0]);
        assertEquals(3, dest[1]);
        assertEquals(2, dest[2]);
        assertEquals(1, dest[3]);
    }

    @Test
    public void testToTimestamp_Milliseconds() {
        assertEquals(String.valueOf(SAMPLE_MS), Timestamps.toTimestamp(SAMPLE_MS, Display.MILLISECONDS));
    }

    @Test
    public void testToTimestamp_Seconds() {
        // SECONDS mode shows total elapsed seconds (unwrapped), not seconds-within-minute.
        // Sub-second precision is truncated to two digits (centiseconds).
        assertEquals("3723.04", Timestamps.toTimestamp(SAMPLE_MS, Display.SECONDS));
    }

    @Test
    public void testToTimestamp_Minutes() {
        // MINUTES mode shows total elapsed minutes (unwrapped), not minutes-within-hour.
        assertEquals("62:03.04", Timestamps.toTimestamp(SAMPLE_MS, Display.MINUTES));
    }

    @Test
    public void testToTimestamp_Hours() {
        assertEquals("01:02:03.04", Timestamps.toTimestamp(SAMPLE_MS, Display.HOURS));
    }

    @Test
    public void testFromTimestamp_HoursMinutesSeconds() {
        assertEquals(SAMPLE_MS, Timestamps.fromTimestamp("01:02:03.040"));
    }

    @Test
    public void testFromTimestamp_MinutesSeconds() {
        assertEquals(123_040, Timestamps.fromTimestamp("02:03.040"));
    }

    @Test
    public void testFromTimestamp_SecondsOnly() {
        assertEquals(3_040, Timestamps.fromTimestamp("03.040"));
    }

    @Test
    public void testToTimestamp_MillisAreTruncatedToTwoDigits() {
        // Sub-second display is truncated (not rounded) to centisecond precision,
        // so anything below 10ms is truncated away entirely.
        assertEquals("00.00", Timestamps.toTimestamp(4, Display.SECONDS));
        assertEquals("00.04", Timestamps.toTimestamp(49, Display.SECONDS));
    }

    @Test
    public void testToTimestamp_CentisecondAlignedMillisRoundTrip() {
        // Only values that are an exact multiple of 10ms survive the truncation
        // to centiseconds and round-trip back to the original value.
        assertEquals("00.99", Timestamps.toTimestamp(990, Display.SECONDS));
        assertEquals(990, Timestamps.fromTimestamp(Timestamps.toTimestamp(990, Display.SECONDS)));
    }

    @Test
    public void testFromTimestamp_WholeNumbersNoFraction() {
        assertEquals(3_723_000, Timestamps.fromTimestamp("01:02:03"));
    }

    @Test
    public void testFromTimestamp_TooManyColons() {
        assertThrows(NumberFormatException.class, () -> Timestamps.fromTimestamp("01:02:03:04"));
    }

    @Test
    public void testFromTimestamp_HoursSuffix() {
        assertEquals(1.5 * 3_600_000, Timestamps.fromTimestamp("1.5h"));
    }

    @Test
    public void testFromTimestamp_MinutesSuffix() {
        assertEquals(90_000, Timestamps.fromTimestamp("1.5m"));
    }

    @Test
    public void testFromTimestamp_SecondsSuffix() {
        assertEquals(1_500, Timestamps.fromTimestamp("1.5s"));
    }

    @Test
    public void testFromTimestamp_MillisecondsSuffix() {
        assertEquals(500, Timestamps.fromTimestamp("500ms"));
    }

    @Test
    public void testFromTimestamp_SuffixIsCaseInsensitiveAndTrimmed() {
        assertEquals(500, Timestamps.fromTimestamp("  500MS  "));
        assertEquals(90_000, Timestamps.fromTimestamp(" 1.5M "));
    }

    @Test
    public void testFromTimestamp_BareIntegerIsSeconds() {
        assertEquals(5_000, Timestamps.fromTimestamp("5"));
    }

    @Test
    public void testRoundTrip_AllDisplayModes() {
        // Every display mode shows the total elapsed value at its own granularity
        // (unwrapped for all but HOURS), so all of them round trip exactly. The
        // MILLISECONDS mode needs an explicit "ms" suffix on the way back in,
        // since a bare number is otherwise interpreted as seconds.
        for (Display display : Display.values()) {
            String str = Timestamps.toTimestamp(SAMPLE_MS, display);
            if (display == Display.MILLISECONDS) {
                str += "ms";
            }
            int parsed = Timestamps.fromTimestamp(str);
            assertEquals(SAMPLE_MS, parsed, "Round trip failed for " + display);
        }
    }
}
