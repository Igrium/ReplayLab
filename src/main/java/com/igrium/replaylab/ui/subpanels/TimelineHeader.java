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

    private static final int TICK_COLOR = 0xAAAAAAAA;
    private static final int MARKER_COLOR = 0xAA0000AA;
    private static final int MARKER_HOVERED_COLOR = 0xFF0000AA;


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

    private @Nullable Marker ctxMarker;

    @Getter
    @Setter
    private boolean drawReplayMarkers = true;

    /**
     * Markers being rendered and their scene times. Recomputed once per frame.
     */
    private final Object2IntMap<Marker> markerCache = new Object2IntOpenHashMap<>();

    private final ImString markerNameCache = new ImString();

    /**
     * A replay marker the mouse was pressed on, while the button is still held.
     */
    private static final class MarkerDrag {
        final Marker marker;
        /** Scene time of the marker before the drag started. */
        final int startTime;
        /** Scene time the marker is currently being previewed at. */
        int time;
        /** False until ImGui's drag threshold is passed; a release before then counts as a click. */
        boolean dragging;

        MarkerDrag(Marker marker, int startTime) {
            this.marker = marker;
            this.startTime = startTime;
            this.time = startTime;
        }
    }

    private @Nullable MarkerDrag markerDrag;

    /**
     * The replay marker currently being dragged, if any.
     */
    public @Nullable Marker getDraggedMarker() {
        return markerDrag != null && markerDrag.dragging ? markerDrag.marker : null;
    }

    /**
     * True if the user is currently dragging a replay marker.
     */
    public boolean isDraggingMarker() {
        return markerDrag != null && markerDrag.dragging;
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

        var scene = editorState.getScene();
        boolean showTicks = !hasFlag(TimelineFlags.NO_TICKS, flags);
        boolean markersEnabled = drawReplayMarkers && showTicks;
        boolean showPlayhead = playhead != null && !hasFlag(TimelineFlags.NO_PLAYHEAD, flags);
        boolean canMovePlayhead = showPlayhead && !hasFlag(TimelineFlags.READONLY_PLAYHEAD, flags);

        float width = ImGui.getContentRegionAvailX();
        float cursorX = ImGui.getCursorScreenPosX();
        float cursorY = ImGui.getCursorScreenPosY();
        float em = ImGui.getFontSize();
        float markerRadius = em / 3f;

        // Scene time (ms) <-> screen-space X
        FloatUnaryOperator toScreenX = ms -> (ms - offsetX) * zoomFactor + cursorX;
        FloatUnaryOperator toTime = x -> (x - cursorX) / zoomFactor + offsetX;

        ImDrawList drawList = ImGui.getWindowDrawList();
        ImGui.invisibleButton("#header", width, headerHeight);
        boolean hovered = ImGui.isItemHovered();
        boolean active = ImGui.isItemActive();
        boolean activated = ImGui.isItemActivated();

        widthMs = width / zoomFactor;

        // === MARKER POSITIONS ===
        markerCache.clear();
        if (markersEnabled) {
            for (Marker marker : editorState.getReplayMarkers()) {
                int sceneTime = scene.replayToSceneTime(marker.getTime());
                if (offsetX <= sceneTime && sceneTime <= offsetX + widthMs) {
                    markerCache.put(marker, sceneTime);
                }
            }
            if (markerDrag != null) {
                markerCache.put(markerDrag.marker, markerDrag.time);
            }
        } else {
            markerDrag = null; // Cancel any drag
        }

        // === MARKER HOVER AND PRESS ===
        Marker hoveredMarker = null;
        if (markerDrag != null) {
            // While pressed/dragging, that marker is always the "hovered" one.
            hoveredMarker = markerDrag.marker;
        } else if (markersEnabled && (hovered || active)) {
            float mouseX = ImGui.getMousePosX();
            float mouseY = ImGui.getMousePosY();
            if (cursorY < mouseY && mouseY < cursorY + markerRadius * 2) {
                float closestDist = markerRadius;
                for (var entry : markerCache.object2IntEntrySet()) {
                    float dist = Math.abs(toScreenX.apply(entry.getIntValue()) - mouseX);
                    if (dist < closestDist) {
                        hoveredMarker = entry.getKey();
                        closestDist = dist;
                    }
                }
            }

            if (hoveredMarker != null && activated && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
                markerDrag = new MarkerDrag(hoveredMarker, markerCache.getInt(hoveredMarker));
            }
        }

        // === CONTEXT MENU ===
        if (ImGui.isItemClicked(ImGuiMouseButton.Right)) {
            ImGui.openPopup("headerPopup");
            ctxMarker = hoveredMarker;
            contextTime = hoveredMarker != null
                    ? markerCache.getInt(hoveredMarker)
                    : (int) toTime.apply(ImGui.getMousePosX());
            contextTimeStr = Timestamps.toTimestamp(contextTime, 3,
                    ReplayLabConfig.getInstance().getTimestampMode());
        }

        if (ImGui.beginPopup("headerPopup")) {
            ImGui.pushFont(CraftUIFonts.getFont(ReplayLabControls.ROBOTO_MONO), 0);
            ImGui.textDisabled(contextTimeStr);
            ImGui.popFont();

            ImGui.separator();

            if (ctxMarker != null) {
                String name = ctxMarker.getName();
                markerNameCache.set(name != null ? name : "");
                ImGui.inputTextWithHint("##markerName", tt("gui.replaylab.marker.name"), markerNameCache);
                if (ImGui.isItemDeactivatedAfterEdit()) {
                    String newName = markerNameCache.get();
                    editorState.applyOperator(new RenameMarkerOperator(ctxMarker,
                            newName.isBlank() ? null : newName));
                }

                if (ImGui.menuItem(t("gui.replaylab.marker.goto"))) {
                    editorState.snapViewportTo(ctxMarker.getX(), ctxMarker.getY(), ctxMarker.getZ(),
                            ctxMarker.getYaw(), ctxMarker.getPitch());
                }

                if (ImGui.menuItem(t("gui.replaylab.marker.delete"))) {
                    editorState.applyOperator(new DeleteMarkerOperator(ctxMarker));
                }

                ImGui.separator();
            }

            if (ImGui.menuItem(t("gui.replaylab.copy_ts"))) {
                ImGui.setClipboardText(contextTimeStr);
            }

            if (ImGui.menuItem(t("gui.replaylab.set_scene_start"), false, contextTime < length)) {
                editorState.applyOperator(new SetSceneStartOperator(scene.sceneToReplayTime(contextTime), true));
            }

            if (ImGui.menuItem(t("gui.replaylab.set_scene_end"), false, contextTime >= 0)) {
                editorState.applyOperator(new SetSceneLengthOperator(contextTime));
            }

            if (ctxMarker == null) {
                ImGui.separator();
                if (ImGui.menuItem(t("gui.replaylab.add_marker"))) {
                    editorState.applyOperator(new AddMarkerOperator(scene.sceneToReplayTime(contextTime)));
                }
            }

            ImGui.endPopup();
        } else {
            contextTime = -1;
            ctxMarker = null;
        }

        // === INTERVALS ===
        int majorInterval = (int) computeMajorInterval(zoomFactor);
        int minorInterval = majorInterval / 2;
        int tinyInterval = majorInterval / 4;
        boolean showTinyTicks = tinyInterval * zoomFactor > em * 1.2f;
        // At extreme zoom the intervals can round down to 0. Skip the ticks, but keep everything else working.
        boolean intervalsValid = tinyInterval > 0;
        int snapTargetMs = Math.max(1, showTinyTicks ? tinyInterval : minorInterval);

        // === MARKER DRAG UPDATE ===
        if (markerDrag != null) {
            if (active && ImGui.isMouseDown(ImGuiMouseButton.Left)) {
                // A press only becomes a drag once ImGui's drag threshold is passed.
                if (ImGui.isMouseDragging(ImGuiMouseButton.Left)) {
                    markerDrag.dragging = true;
                }

                if (markerDrag.dragging) {
                    int newTime = markerDrag.startTime
                            + Math.round(ImGui.getMouseDragDeltaX(ImGuiMouseButton.Left) / zoomFactor);

                    // Snap to the playhead if close enough
                    if (showPlayhead && Math.abs(newTime - playhead.get()) * zoomFactor < SNAP_THRESHOLD_PX) {
                        newTime = playhead.get();
                    } else {
                        newTime = snapTime(newTime, snapTargetMs, zoomFactor, keys, flags);
                    }
                    markerDrag.time = newTime;
                    markerCache.put(markerDrag.marker, newTime);
                }
            } else {
                // Released
                if (markerDrag.dragging) {
                    if (markerDrag.time != markerDrag.startTime) {
                        commitMarkerDrag(editorState, markerDrag.marker,
                                scene.sceneToReplayTime(markerDrag.startTime), scene.sceneToReplayTime(markerDrag.time));
                    }
                } else if (canMovePlayhead) {
                    // Released without dragging: treat as a click and jump the playhead to the marker
                    playhead.set(Math.clamp(markerDrag.startTime, 0, length));
                    stoppedScrubbing = true;
                }
                markerDrag = null;
            }
        }

        // === TICKS ===
        if (showTicks && intervalsValid) {
            drawList.pushClipRect(cursorX, cursorY, cursorX + width, cursorY + headerHeight);

            int startTick = Math.floorDiv((int) offsetX, majorInterval) * majorInterval;
            int endTick = Math.ceilDiv((int) (offsetX + widthMs), majorInterval) * majorInterval;
            int step = showTinyTicks ? tinyInterval : minorInterval;

            ImVec2 textSize = new ImVec2();
            for (int ms = startTick; ms <= endTick; ms += step) {
                float x = toScreenX.apply(ms);
                float heightDivisor;

                if (ms % majorInterval == 0) {
                    float sec = ms / 1000f;
                    String str = majorInterval < 1000 ? String.format("%.2f", sec) : Integer.toString(Math.round(sec));
                    ImGui.calcTextSize(textSize, str);
                    drawList.addText(x - textSize.x / 2f, cursorY, ImGui.getColorU32(ImGuiCol.Text), str);
                    heightDivisor = 1.8f;
                } else if (ms % minorInterval == 0) {
                    heightDivisor = 1.4f;
                } else {
                    heightDivisor = 1.2f;
                }

                drawList.addLine(x, cursorY + headerHeight / heightDivisor, x, cursorY + headerHeight, TICK_COLOR);
            }

            drawList.popClipRect();
        }

        // === MARKERS ===
        if (markersEnabled) {
            for (var entry : markerCache.object2IntEntrySet()) {
                float x = toScreenX.apply(entry.getIntValue());
                int color = entry.getKey() == hoveredMarker ? MARKER_HOVERED_COLOR : MARKER_COLOR;
                drawList.addTriangleFilled(
                        x - markerRadius, cursorY,
                        x + markerRadius, cursorY,
                        x, cursorY + markerRadius * 2,
                        color);
            }

            if (hoveredMarker != null && hoveredMarker.getName() != null && ImGui.isWindowHovered()) {
                ImGui.setTooltip(hoveredMarker.getName());
            }
        }

        if (!showPlayhead) {
            return;
        }

        // === SCRUBBING ===
        if (canMovePlayhead && markerDrag == null && active && ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            scrubbing = true;
        } else if (scrubbing) {
            scrubbing = false;
            stoppedScrubbing = true;
        }

        if (scrubbing) {
            // Snapping to a hovered marker takes priority over other snapping
            int newPlayhead = hoveredMarker != null
                    ? markerCache.getInt(hoveredMarker)
                    : snapTime((int) toTime.apply(ImGui.getMousePosX()), snapTargetMs, zoomFactor, keys, flags);
            playhead.set(Math.clamp(newPlayhead, 0, length));
        }

        // === PLAYHEAD ===
        ImDrawList fgDrawList = ImGui.getForegroundDrawList();
        int playheadColor = ImGui.getColorU32(ImGuiCol.CheckMark) | 0xFF000000;
        float playheadX = toScreenX.apply(playhead.get());
        float lineTop = cursorY + headerHeight;
        float lineBottom = lineTop + windowHeight;

        if (playheadX > cursorX) {
            float radius = em / 2f;
            fgDrawList.addRectFilled(playheadX - radius, cursorY + headerHeight / 2, playheadX + radius, lineTop,
                    playheadColor);
            if (windowHeight > 0) {
                fgDrawList.addLine(playheadX, lineTop, playheadX, lineBottom, playheadColor);
            }
        }

        // === TIME INDICATOR ===
        Marker draggedMarker = getDraggedMarker();
        if (windowHeight > 0 && (hovered || contextTime >= 0 || draggedMarker != null)) {
            float x = draggedMarker != null ? toScreenX.apply(markerCache.getInt(draggedMarker))
                    : contextTime >= 0 ? toScreenX.apply(contextTime)
                    : hoveredMarker != null ? toScreenX.apply(markerCache.getInt(hoveredMarker))
                    : ImGui.getMousePosX();

            if (Math.abs(x - playheadX) > 10) {
                fgDrawList.addLine(x, lineTop, x, lineBottom, CommonColors.BLACK);
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