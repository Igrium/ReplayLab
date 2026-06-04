package com.igrium.replaylab.ui.subpanels;

import com.igrium.replaylab.ui.util.TimelineFlags;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;
import it.unimi.dsi.fastutil.floats.FloatUnaryOperator;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Nullable;

public class TimelineHeader {

    private static final int TICK_MULTIPLE = 10;
    private static final float SNAP_THRESHOLD_PX = 8f;

    /**
     * Compute the distance between each major interval in a timeline header.
     *
     * @param emPerTimeUnit    The number of text units per time unit (based on the zoom factor)
     * @param targetEmInterval Target number of text units between each major interval.
     * @param multiple         Only use multiples of this number.
     * @return The number of time units between each major interval.
     */
    public static float computeMajorInterval(float emPerTimeUnit, int targetEmInterval, int multiple) {
        double idealSeconds = targetEmInterval / emPerTimeUnit;
        double log2 = Math.log(idealSeconds) / Math.log(multiple);
        double roundedPower = Math.round(log2);
        return (float) Math.pow(multiple, roundedPower);
    }

    /**
     * Compute the distance between each major interval using reasonable values.
     *
     * @param zoomFactor The current horizontal zoom factor.
     * @return The number of milliseconds between each major interval
     */
    public static float computeMajorInterval(float zoomFactor) {
        float em = ImGui.getFontSize();
        float emPerTick = zoomFactor / em;
        return computeMajorInterval(emPerTick, 8, TICK_MULTIPLE);
    }

    /**
     * True if the user is actively scrubbing.
     */
    @Getter
    private boolean scrubbing;

    /**
     * True if the user stopped scrubbing the playhead on this frame.
     */
    @Getter @Accessors(fluent = true)
    private boolean stoppedScrubbing;

    /**
     * Draw the header of the dope sheet or curve editor.
     *
     * @param headerHeight   Vertical height of the header (pixels)
     * @param zoomFactor     The number of pixels per millisecond
     * @param offsetX        Distance the timeline has been scrolled from the scene start (ms)
     * @param length         Total length of the scene (ms)
     * @param playhead       Current playhead position (ms). Updated as the user scrubs.
     * @param windowHeight   Vertical height of the editor (pixels). Used for drawing the playhead line.
     * @param keys           Every frame that has a keyframe on it
     * @param flags          Render flags.
     */
    public void drawHeader(float headerHeight, float zoomFactor, float offsetX, int length,
                           @Nullable ImInt playhead, float windowHeight, int @Nullable [] keys, int flags) {
        stoppedScrubbing = false;

        FloatUnaryOperator msToPixel = ms -> (ms - offsetX) * zoomFactor;
        FloatUnaryOperator pixelToMs = pixel -> pixel / zoomFactor + offsetX;

        float width = ImGui.getContentRegionAvailX();

        float cursorX = ImGui.getCursorScreenPosX();
        float cursorY = ImGui.getCursorScreenPosY();

        ImDrawList drawList = ImGui.getWindowDrawList();
        ImGui.invisibleButton("#header", width, headerHeight);

        int majorInterval = (int) computeMajorInterval(zoomFactor);
        int minorInterval = majorInterval / 2;
        int tinyInterval = majorInterval / 4;

        if (majorInterval == 0 || minorInterval == 0 || tinyInterval == 0) {
            return;
        }

        float em = ImGui.getFontSize();
        int snapTargetMs;
        if (tinyInterval * zoomFactor > em * 1.2) {
            snapTargetMs = tinyInterval;
        } else {
            snapTargetMs = minorInterval;
        }

        // === TICKS ===
        if (!hasFlag(TimelineFlags.NO_TICKS, flags)) {
            drawList.pushClipRect(cursorX, cursorY, cursorX + width, cursorY + headerHeight);

            // The amount of milliseconds the window is wide
            float widthMs = width / zoomFactor;

            // Round the first tick to draw down

            int headerStartTick = Math.floorDiv((int) offsetX, TICK_MULTIPLE) * TICK_MULTIPLE;

            // Round the last tick to draw up
            int headerEndTick = Math.floorDiv((int) (offsetX + widthMs), TICK_MULTIPLE) * TICK_MULTIPLE;

            int startTick = Math.floorDiv((int) offsetX, majorInterval) * majorInterval;
            int endTick = Math.ceilDiv((int) (offsetX + widthMs), majorInterval) * majorInterval;

            ImVec2 strLength = new ImVec2();
            for (int ms = startTick; ms <= endTick; ms += tinyInterval) {
                float pos = msToPixel.apply(ms) + cursorX;

                boolean isMajor = ms % majorInterval == 0;
                boolean isMinor = ms % minorInterval == 0;

                if (isMajor) {
                    // Number
                    float sec = ms / 1000f;
                    String str = majorInterval < 1000 ? String.format("%.2f", sec) : Integer.toString(Math.round(sec));
                    ImGui.calcTextSize(strLength, str);
                    drawList.addText(pos - strLength.x / 2f, cursorY, ImGui.getColorU32(ImGuiCol.Text), str);

                    // Tick
                    drawList.addLine(pos, cursorY + headerHeight / 1.8f, pos, cursorY + headerHeight, 0xAAAAAAAA);

                } else if (isMinor) {
                    drawList.addLine(pos, cursorY + headerHeight / 1.4f, pos, cursorY + headerHeight, 0xAAAAAAAA);

                } else if (tinyInterval * zoomFactor > em * 1.2f) {
                    // Don't bother drawing tiny ticks if they're too small
                    drawList.addLine(pos, cursorY + headerHeight / 1.2f, pos, cursorY + headerHeight, 0xAAAAAAAA);
                }
            }

            drawList.popClipRect();
        }

        // === PLAYHEAD ===
        if (playhead != null && !hasFlag(TimelineFlags.NO_PLAYHEAD, flags)) {

            if (!hasFlag(TimelineFlags.READONLY_PLAYHEAD, flags) && ImGui.isItemActive() && ImGui.isMouseDown(0)) {
                scrubbing = true;
            } else if (scrubbing) {
                scrubbing = false;
                stoppedScrubbing = true;
            }

            if (scrubbing) {
                int newPlayhead = (int) pixelToMs.apply(ImGui.getMousePosX() - cursorX);

                if (newPlayhead < 0)
                    newPlayhead = 0;

                if (newPlayhead > length)
                    newPlayhead = length;


                boolean ctrlPressed = ImGui.getIO().getKeyCtrl();
                boolean shiftPressed = ImGui.getIO().getKeyShift();

                // If we're inverting or ctrl is pressed, but not both
                if (hasFlag(TimelineFlags.INVERT_TICK_SNAP, flags) != ctrlPressed) {
                    newPlayhead = Math.round((float) newPlayhead / snapTargetMs) * snapTargetMs;
                }

                if (keys != null && keys.length > 0 && hasFlag(TimelineFlags.INVERT_KEY_SNAP, flags) != shiftPressed) {
                    float snapThresholdMs = SNAP_THRESHOLD_PX / zoomFactor;
                    int closestKeyIDx = getClosestInt(keys, newPlayhead);

                    if (closestKeyIDx >= 0 && Math.abs(keys[closestKeyIDx] - newPlayhead) < snapThresholdMs) {
                        newPlayhead = keys[closestKeyIDx];
                    }
                }

                playhead.set(newPlayhead);
            }

            int color = ImGui.getColorU32(ImGuiCol.CheckMark) | 0xFF000000;

            // Dumb hack to make playhead draw over window properly
            ImGui.setCursorPos(0, 0);
            ImGui.beginChild("overlay", ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY(), false,
                    ImGuiWindowFlags.NoBackground | ImGuiWindowFlags.NoInputs);

            float playheadX = msToPixel.apply(playhead.get()) + cursorX;
            float radius = ImGui.getFontSize() / 2f;

            var drawList2 = ImGui.getWindowDrawList();
            if (playheadX > cursorX) {
                drawList2.addRectFilled(playheadX - radius, cursorY + headerHeight / 2, playheadX + radius, cursorY + headerHeight, color);
                if (windowHeight > 0) {
                    drawList2.addLine(playheadX, cursorY + headerHeight, playheadX, cursorY + headerHeight + windowHeight, color);
                }
            }

            ImGui.endChild();
        }
    }

    /**
     * Identify the index of the value item in the int array closest to the specified value
     *
     * @param ints The array of integers to test
     * @param val  The value to test against
     * @return The index in the array of the closest integer. <code>-1</code> if the array was empty.
     */
    private static int getClosestInt(int[] ints, int val) {
        int idx = -1;
        int closestAbs = 0;
        for (int i = 0; i < ints.length; i++) {
            int abs = Math.abs(ints[i] - val);
            if (idx < 0 || abs < closestAbs) {
                closestAbs = abs;
                idx = i;
            }
        }
        return idx;
    }

    private static boolean hasFlag(int flag, int flags) {
        return (flags & flag) == flag;
    }
}
