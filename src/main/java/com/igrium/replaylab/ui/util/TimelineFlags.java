package com.igrium.replaylab.ui.util;

public class TimelineFlags {
    /**
     * Don't allow editing
     */
    public static final int READONLY = 1;

    /**
     * If set, snap keyframes to other keyframes while editing
     */
    public static final int SNAP_KEYS = 2;

    /**
     * Don't draw the header
     */
    public static final int NO_HEADER = 4;

    /**
     * Don't draw the playhead
     */
    public static final int NO_PLAYHEAD = 8;

    /**
     * Do not allow the playhead to be moved manually by the user
     */
    public static final int READONLY_PLAYHEAD = 16;

    /**
     * Don't draw ticks
     */
    public static final int NO_TICKS = 32;

    /**
     * Don't attempt to merge overlapping keyframes
     */
    public static final int ALLOW_DUPLICATE_KEYS = 64;

    /**
     * Snap to ticks when the user is NOT holding ctrl
     */
    public static final int INVERT_TICK_SNAP = 128;

    /**
     * Snap to keyframes when the user is NOT holding shift
     */
    public static final int INVERT_KEY_SNAP = 256;

}
