package com.igrium.replaylab.ui.subpanels;

import com.igrium.craftui.api.style.CraftUIFonts;
import com.igrium.replaylab.config.ReplayLabConfig;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.replay.AddMarkerOperator;
import com.igrium.replaylab.operator.replay.DeleteMarkerOperator;
import com.igrium.replaylab.operator.replay.MoveMarkerOperator;
import com.igrium.replaylab.operator.replay.RenameMarkerOperator;
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
import imgui.flag.ImGuiPopupFlags;
import imgui.flag.ImGuiSelectableFlags;
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

    @Getter
    @Setter
    private boolean drawReplayMarkers = true;

    /**
     * Markers being rendered and their scene times. Recomputed once per frame.
     */
    private final Object2IntMap<Marker> markerCache = new Object2IntOpenHashMap<>();

    private final ImString markerNameCache = new ImString();

    /**
     * The replay marker the mouse was pressed on, if the button is still held.
     * Only becomes {@link #draggedMarker} once ImGui's drag threshold is passed.
     */
    private @Nullable Marker pressedMarker;

    /**
     * The replay marker currently being dragged, if any.
     */
    @Getter
    private @Nullable Marker draggedMarker;

    /**
     * The scene time of the pressed/dragged marker before the drag started.
     */
    private int markerDragStartTime;

    /**
     * The scene time the dragged marker is currently being previewed at.
     */
    private int markerDragTime;

    /**
     * True if the user is currently dragging a replay marker.
     */
    public boolean isDraggingMarker() {
        return draggedMarker != null;
    }

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

        // === HOVER AND RIGHT-CLICK ===
        boolean hovered = ImGui.isItemHovered();
        boolean active = ImGui.isItemActive();
        boolean activated = ImGui.isItemActivated();

        // The marker the cursor is directly over
        float markerRadius = ImGui.getFontSize() / 3f;
        Marker hoveredMarker = null;
        if (markersEnabled && (hovered || active)) {
            float mouseX = ImGui.getMousePosX();
            float mouseY = ImGui.getMousePosY();
            if (cursorY < mouseY && mouseY < cursorY + markerRadius * 2) {
                float closestDist = Float.MAX_VALUE;
                for (var entry : markerCache.object2IntEntrySet()) {
                    float dist = Math.abs(msToPixel.apply(entry.getIntValue()) + cursorX - mouseX);
                    if (dist < markerRadius && dist < closestDist) {
                        hoveredMarker = entry.getKey();
                        closestDist = dist;
                    }
                }
            }
        }

        // === MARKER PRESS ===
        if (markersEnabled && pressedMarker == null && draggedMarker == null && activated && hoveredMarker != null
                && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            pressedMarker = hoveredMarker;
            markerDragStartTime = markerCache.getInt(hoveredMarker);
            markerDragTime = markerDragStartTime;
        }

        // While pressed/dragging, that marker is always the "hovered" one.
        if (draggedMarker != null) {
            hoveredMarker = draggedMarker;
        } else if (pressedMarker != null) {
            hoveredMarker = pressedMarker;
        }

        if (ImGui.isItemClicked(ImGuiMouseButton.Right)) {
            ImGui.openPopup("headerPopup");
            int clickedTime = (int) pixelToMs.apply(ImGui.getMousePosX() - cursorX);
            contextReplayMarker = hoveredMarker;
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
                ImGui.inputTextWithHint("##markerName", tt("gui.replaylab.marker_name"), markerNameCache);
                if (ImGui.isItemDeactivatedAfterEdit()) {
                    String newName = markerNameCache.get();
                    editorState.applyOperator(new RenameMarkerOperator(contextReplayMarker,
                            newName.isBlank() ? null : newName));
                }

                if (ImGui.menuItem(t("gui.replaylab.delete_marker"))) {
                    editorState.applyOperator(new DeleteMarkerOperator(contextReplayMarker));
                }

                ImGui.separator();
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

            if (contextReplayMarker == null) {
                ImGui.separator();
                if (ImGui.menuItem(t("gui.replaylab.add_marker"))) {
                    editorState.applyOperator(new AddMarkerOperator(editorState.getScene().sceneToReplayTime(contextTime)));
                }
            }

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

        // === MARKER DRAG UPDATE ===
        boolean mouseHeld = active && ImGui.isMouseDown(ImGuiMouseButton.Left);

        // A pressed marker only becomes a drag once ImGui's drag threshold is passed.
        if (pressedMarker != null && draggedMarker == null && mouseHeld
                && ImGui.isMouseDragging(ImGuiMouseButton.Left)) {
            draggedMarker = pressedMarker;
        }

        if (draggedMarker != null) {
            if (!markersEnabled) {
                // Cancel
                draggedMarker = null;
                pressedMarker = null;
            } else if (mouseHeld) {
                int delta = Math.round(ImGui.getMouseDragDeltaX(ImGuiMouseButton.Left) / zoomFactor);
                int newTime = markerDragStartTime + delta;

                // Snap to the playhead if close enough
                if (playhead != null && !hasFlag(TimelineFlags.NO_PLAYHEAD, flags)
                        && Math.abs(newTime - playhead.get()) * zoomFactor < SNAP_THRESHOLD_PX) {
                    newTime = playhead.get();
                } else {
                    newTime = snapTime(newTime, snapTargetMs, zoomFactor, keys, flags);
                }
                markerDragTime = newTime;
            } else {
                // Released
                Marker marker = draggedMarker;
                draggedMarker = null;
                pressedMarker = null;
                if (markerDragTime != markerDragStartTime) {
                    var scene = editorState.getScene();
                    commitMarkerDrag(editorState, marker,
                            scene.sceneToReplayTime(markerDragStartTime), scene.sceneToReplayTime(markerDragTime));
                }
            }
        } else if (pressedMarker != null && !mouseHeld) {
            // Released without dragging: treat as a click and jump the playhead to the marker
            if (markersEnabled && playhead != null && !hasFlag(TimelineFlags.NO_PLAYHEAD, flags)
                    && !hasFlag(TimelineFlags.READONLY_PLAYHEAD, flags)) {
                playhead.set(Math.clamp(markerDragStartTime, 0, length));
                stoppedScrubbing = true;
            }
            pressedMarker = null;
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
                float radius = markerRadius;
                if (draggedMarker != null && !markerCache.containsKey(draggedMarker)) {
                    // Dragged marker's original position scrolled out of view; make sure it still renders.
                    markerCache.put(draggedMarker, markerDragTime);
                }
                for (var entry : markerCache.object2IntEntrySet()) {
                    Marker marker = entry.getKey();
                    int markerTime = marker == draggedMarker ? markerDragTime : entry.getIntValue();
                    float pos = msToPixel.apply(markerTime) + cursorX;

                    boolean markerHovered = marker == hoveredMarker;
                    if (markerHovered && ImGui.isWindowHovered()) {
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

        // === PLAYHEAD ===inputText
        if (playhead != null && !hasFlag(TimelineFlags.NO_PLAYHEAD, flags)) {

            if (!hasFlag(TimelineFlags.READONLY_PLAYHEAD, flags) && draggedMarker == null && pressedMarker == null && active
                    && ImGui.isMouseDown(0)) {
                scrubbing = true;
            } else if (scrubbing) {
                scrubbing = false;
                stoppedScrubbing = true;
            }

            if (scrubbing) {
                int newPlayhead = (int) Math.clamp(pixelToMs.apply(ImGui.getMousePosX() - cursorX), 0, length);

                newPlayhead = snapTime(newPlayhead, snapTargetMs, zoomFactor, keys, flags);

                // Snap to the marker being hovered (takes priority over other snapping)
                if (hoveredMarker != null) {
                    newPlayhead = Math.clamp(markerCache.getInt(hoveredMarker), 0, length);
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

            if ((hovered || contextTime >= 0 || draggedMarker != null) && windowHeight > 0) {
                float x;
                if (draggedMarker != null) {
                    x = msToPixel.apply(markerDragTime) + cursorX;
                } else if (contextTime >= 0) {
                    x = msToPixel.apply(contextTime) + cursorX;
                } else {
                    x = ImGui.getMousePosX();
                    if (hoveredMarker != null) {
                        x = msToPixel.apply(markerCache.getInt(hoveredMarker)) + cursorX;
                    }
                }

                if (Math.abs(x - playheadX) > 10)
                    fgDrawList.addLine(x, cursorY + headerHeight, x, cursorY + headerHeight + windowHeight,
                            CommonColors.BLACK);
            }
        }
    }

    /**
     * Called when the user finishes dragging a replay marker to a new position.
     * Not called if the marker ended up where it started.
     *
     * @param editorState   The editor state.
     * @param marker        The marker that was dragged.
     * @param oldReplayTime The marker's time before the drag, in replay time (ms).
     * @param newReplayTime The marker's new time, in replay time (ms).
     */
    protected void commitMarkerDrag(EditorState editorState, Marker marker, int oldReplayTime, int newReplayTime) {
        editorState.applyOperator(new MoveMarkerOperator(marker, oldReplayTime, newReplayTime));
    }

    /**
     * Apply tick and keyframe snapping to a timestamp based on modifier keys and flags.
     */
    private static int snapTime(int time, int snapTargetMs, float zoomFactor, int @Nullable [] keys, int flags) {
        boolean ctrlPressed = ImGui.getIO().getKeyCtrl();
        boolean shiftPressed = ImGui.getIO().getKeyShift();

        // If we're inverting or ctrl is pressed, but not both
        if (hasFlag(TimelineFlags.INVERT_TICK_SNAP, flags) != ctrlPressed) {
            time = Math.round((float) time / snapTargetMs) * snapTargetMs;
        }

        if (keys != null && keys.length > 0 && hasFlag(TimelineFlags.INVERT_KEY_SNAP, flags) != shiftPressed) {
            float snapThresholdMs = SNAP_THRESHOLD_PX / zoomFactor;
            int closestKeyIDx = getClosestInt(keys, time);

            if (closestKeyIDx >= 0 && Math.abs(keys[closestKeyIDx] - time) < snapThresholdMs) {
                time = keys[closestKeyIDx];
            }
        }
        return time;
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

    private static String tt(String key) {
        return Language.getInstance().getOrDefault(key);
    }

    private static String t(String key) {
        return Language.getInstance().getOrDefault(key) + "###" + key;
    }
}