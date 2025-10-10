package com.igrium.replaylab.ui.util;

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

    private static final int TICK_MULTIPLE = 10000;

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
     * @param flags          Render flags.
     */
    public void drawHeader(float headerHeight, float zoomFactor, float offsetX, int length,
                                  @Nullable ImInt playhead, float windowHeight, int flags) {
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

            // Major intervals
            for (int ms = headerStartTick; ms <= headerEndTick; ms += majorInterval) {
                float pos = msToPixel.apply(ms);
                float sec = ms / 1000f;

                // Number
                String str = majorInterval < 1000 ? String.format("%.2f", sec) : Integer.toString(Math.round(sec));
                ImVec2 strLen = ImGui.calcTextSize(str);
                drawList.addText(cursorX + (pos - strLen.x / 2f), cursorY, ImGui.getColorU32(ImGuiCol.Text), str);

                // Tick
                drawList.addLine(cursorX + pos, cursorY + headerHeight / 1.8f,
                        cursorX + pos, cursorY + headerHeight, 0xAAAAAAAA);
            }

            // Minor ticks
            for (int ms = headerStartTick; ms <= headerEndTick; ms += minorInterval) {
                float pos = msToPixel.apply(ms);
                drawList.addLine(cursorX + pos, cursorY + headerHeight / 1.4f,
                        cursorX + pos, cursorY + headerHeight, 0xAAAAAAAA);
            }

            // Don't bother drawing tiny ticks if they're too small
            if (tinyInterval * zoomFactor > em * 1.2) {
                for (float ms = 0; ms <= headerEndTick; ms += tinyInterval) {
                    float pos = msToPixel.apply(ms);
                    drawList.addLine(cursorX + pos, cursorY + headerHeight / 1.2f,
                            cursorX + pos, cursorY + headerHeight, 0xAAAAAAAA);
                }
            }

            drawList.popClipRect();
        }

        // === PLAYHEAD ===
        if (playhead != null && !hasFlag(TimelineFlags.NO_PLAYHEAD, flags)) {

            if (!hasFlag(TimelineFlags.READONLY_PLAYHEAD, flags) && ImGui.isItemHovered() && ImGui.isMouseDown(0)) {
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

                if (hasFlag(TimelineFlags.SNAP_PLAYHEAD, flags)) {
                    newPlayhead = Math.round((float) newPlayhead / snapTargetMs) * snapTargetMs;
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
                    drawList2.addRectFilled(playheadX - radius, cursorY + headerHeight / 2, playheadX + radius, cursorY + headerHeight, color);
                }
            }

            ImGui.endChild();
        }
    }

    private static boolean hasFlag(int flag, int flags) {
        return (flags & flag) == flag;
    }
}
