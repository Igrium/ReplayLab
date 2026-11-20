package com.igrium.replaylab.ui.subpanels;

import com.igrium.craftui.api.style.CraftUIFonts;
import com.igrium.replaylab.config.ReplayLabConfig;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.scene.RenameReplayMarkerOperator;
import com.igrium.replaylab.operator.scene.SetSceneLengthOperator;
import com.igrium.replaylab.operator.scene.SetSceneStartOperator;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import com.igrium.replaylab.ui.util.TimelineFlags;
import com.igrium.replaylab.util.Timestamps;
import com.replaymod.replaystudio.data.Marker;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiMouseButton;
import imgui.type.ImInt;
import imgui.type.ImString;
import it.unimi.dsi.fastutil.floats.FloatUnaryOperator;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.util.CommonColors;
import net.minecraft.locale.Language;
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
    @Getter
    @Accessors(fluent = true)
    private boolean stoppedScrubbing;

    /**
     * The amount of milliseconds the window is wide
     * Only updated after drawHeader.
     */
    @Getter
    private float widthMs;

    /**
     * The time that was right-clicked on
     */
    private int contextTime;
    private String contextTimeStr = "";

    private @Nullable Marker contextReplayMarker;

    @Getter @Setter
    private boolean drawReplayMarkers = true;

    /**
     * Markers being rendered and their scene times. Recomputed once per frame.
     */
    private final Object2IntMap<Marker> markerCache = new Object2IntOpenHashMap<>();

    private final ImString markerNameCache = new ImString();

    /**
     * Draw the header of the dope sheet or curve editor.
     *
     * @param headerHeight Vertical height of the header (pixels)
     * @param zoomFactor   The number of pixels per millisecond
     * @param offsetX      Distance the timeline has been scrolled from the scene start (ms)
     * @param length       Total length of the scene (ms)
     * @param playhead     Current playhead position (ms). Updated as the user scrubs.
     * @param windowHeight Vertical height of the editor (pixels). Used for drawing the playhead line.
     * @param keys         Every frame that has a keyframe on it
     * @param flags        Render flags.
     */
    public void drawHeader(EditorState editorState, float headerHeight, float zoomFactor, float offsetX, int length,
                           @Nullable ImInt playhead, float windowHeight, int @Nullable [] keys, int flags) {
        stoppedScrubbing = false;

        FloatUnaryOperator msToPixel = ms -> (ms - offsetX) * zoomFactor;
        FloatUnaryOperator pixelToMs = pixel -> pixel / zoomFactor + offsetX;

        float width = ImGui.getContentRegionAvailX();

        float cursorX = ImGui.getCursorScreenPosX();
        float cursorY = ImGui.getCursorScreenPosY();

        ImDrawList drawList = ImGui.getWindowDrawList();
        ImGui.invisibleButton("#header", width, headerHeight);

        // The amount of milliseconds the window is wide
        widthMs = width / zoomFactor;

        // === MARKER POSITIONS ===
        boolean markersEnabled = drawReplayMarkers && !hasFlag(TimelineFlags.NO_TICKS, flags);
        markerCache.clear();
        if (markersEnabled) {
            for (Marker marker : editorState.getReplayMarkers()) {
                int sceneTime = editorState.getScene().replayToSceneTime(marker.getTime());
                if (offsetX <= sceneTime && sceneTime <= offsetX + widthMs) {
                    markerCache.put(marker, sceneTime);
                }
            }
        }
        float markerSnapThresholdMs = SNAP_THRESHOLD_PX / zoomFactor;

        // === HOVER AND RIGHT-CLICK ===
        boolean hovered = ImGui.isItemHovered();

        if (ImGui.isItemClicked(ImGuiMouseButton.Right)) {
            ImGui.openPopup("headerPopup");
            int clickedTime = (int) pixelToMs.apply(ImGui.getMousePosX() - cursorX);
            contextReplayMarker = getSnapMarker(clickedTime, markerSnapThresholdMs);
            contextTime = contextReplayMarker != null ? markerCache.getInt(contextReplayMarker) : clickedTime;
            contextTimeStr = Timestamps.toTimestamp(contextTime, 3,
                    ReplayLabConfig.getInstance().getTimestampMode());
        }

        if (ImGui.beginPopup("headerPopup")) {

            ImGui.pushFont(CraftUIFonts.getFont(ReplayLabControls.ROBOTO_MONO), 0);
            ImGui.beginDisabled();
            ImGui.text(contextTimeStr);
            ImGui.endDisabled();
            ImGui.popFont();

            ImGui.separator();

            if (contextReplayMarker != null) {
                String name = contextReplayMarker.getName();
                markerNameCache.set(name != null ? name : "");
                ImGui.inputText("##markerName", markerNameCache);
                if (ImGui.isItemDeactivatedAfterEdit()) {
                    editorState.applyOperator(new RenameReplayMarkerOperator(contextReplayMarker, markerNameCache.get()));
                }
            }

            if (ImGui.menuItem(t("gui.replaylab.copy_ts"))) {
                ImGui.setClipboardText(contextTimeStr);
            }

            ImGui.beginDisabled(contextTime >= length);
            if (ImGui.menuItem(t("gui.replaylab.set_scene_start"))) {
                editorState.applyOperator(new SetSceneStartOperator(editorState.getScene().sceneToReplayTime(contextTime), true));
            }
            ImGui.endDisabled();

            ImGui.beginDisabled(contextTime < 0);
            if (ImGui.menuItem(t("gui.replaylab.set_scene_end"))) {
                editorState.applyOperator(new SetSceneLengthOperator(contextTime));
            }
            ImGui.endDisabled();

            ImGui.endPopup();
        } else {
            contextTime = -1;
            contextReplayMarker = null;
        }

        // === TICKS ===
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

        if (!hasFlag(TimelineFlags.NO_TICKS, flags)) {
            drawList.pushClipRect(cursorX, cursorY, cursorX + width, cursorY + headerHeight);


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

            // === MARKERS ===

            drawList.popClipRect();
            if (markersEnabled) {
                float radius = ImGui.getFontSize() / 3f;
                float mouseX = ImGui.getMousePosX();
                float mouseY = ImGui.getMousePosY();
                for (var entry : markerCache.object2IntEntrySet()) {
                    Marker marker = entry.getKey();
                    float pos = msToPixel.apply(entry.getIntValue()) + cursorX;

                    boolean markerHovered = false;
                    if (ImGui.isWindowHovered()
                            && pos - radius < mouseX && mouseX < pos + radius
                            && cursorY < mouseY && mouseY < cursorY + radius * 2) {
                        markerHovered = true;
                        String name = marker.getName();
                        if (name != null) {
                            ImGui.setTooltip(name);
                        }
                    }

                    int color = markerHovered ? 0xFF0000AA : 0xAA0000AA;
                    drawList.addTriangleFilled(
                            pos - radius, cursorY,
                            pos + radius, cursorY,
                            pos, cursorY + radius * 2,
                            color);
                }
            }
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
                int newPlayhead = (int) Math.clamp(pixelToMs.apply(ImGui.getMousePosX() - cursorX), 0, length);

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

            // Draw directly to the foreground draw list in screen space. This avoids
            // the old beginChild/setCursorPos(0,0) hack, which reset the cursor relative
            // to the enclosing window/table cell rather than this header, and corrupted
            // the layout of any table this header was drawn inside of.
            ImDrawList fgDrawList = ImGui.getForegroundDrawList();

            float playheadX = msToPixel.apply(playhead.get()) + cursorX;
            float radius = ImGui.getFontSize() / 2f;

            if (playheadX > cursorX) {
                fgDrawList.addRectFilled(playheadX - radius, cursorY + headerHeight / 2, playheadX + radius,
                        cursorY + headerHeight, color);
                if (windowHeight > 0) {
                    fgDrawList.addLine(playheadX, cursorY + headerHeight, playheadX,
                            cursorY + headerHeight + windowHeight, color);
                }
            }

            if ((hovered || contextTime >= 0) && windowHeight > 0) {
                float x;
                if (contextTime >= 0) {
                    x = msToPixel.apply(contextTime) + cursorX;
                } else {
                    x = ImGui.getMousePosX();
                    Marker snapMarker = getSnapMarker(pixelToMs.apply(x - cursorX), markerSnapThresholdMs);
                    if (snapMarker != null) {
                        x = msToPixel.apply(markerCache.getInt(snapMarker)) + cursorX;
                    }
                }

                if (Math.abs(x - playheadX) > 10)
                    fgDrawList.addLine(x, cursorY + headerHeight, x, cursorY + headerHeight + windowHeight,
                            CommonColors.BLACK);
            }
        }
    }

    /**
     * Find the cached marker closest to the given scene time, if it's within the snap threshold.
     *
     * @param sceneTime   Scene time to snap (ms)
     * @param thresholdMs Maximum distance to snap from (ms)
     * @return The closest marker, or <code>null</code> if no marker is close enough.
     */
    private @Nullable Marker getSnapMarker(float sceneTime, float thresholdMs) {
        Marker closest = null;
        float closestTime = thresholdMs;
        for (var entry : markerCache.object2IntEntrySet()) {
            float dist = Math.abs(entry.getIntValue() - sceneTime);
            if (dist < closestTime) {
                closest = entry.getKey();
                closestTime = dist;
            }
        }
        return closest;
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

    private static String t(String key) {
        return Language.getInstance().getOrDefault(key) + "###" + key;
    }
}