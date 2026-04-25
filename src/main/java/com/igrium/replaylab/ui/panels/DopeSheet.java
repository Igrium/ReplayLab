package com.igrium.replaylab.ui.panels;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.editor.KeySelectionSet;
import com.igrium.replaylab.operator.CommitObjectUpdateOperator;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.editor.KeySelectionSet.KeyframeReference;
import com.igrium.replaylab.scene.key.Keyframe;
import com.igrium.replaylab.scene.key.KeyChannel;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.ui.util.TimelineFlags;
import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.*;
import imgui.type.ImInt;
import it.unimi.dsi.fastutil.ints.Int2IntAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import lombok.Getter;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.*;

/**
 * A dope-sheet panel for the replay editor.
 *
 * <p>A dope sheet is a timeline view that shows keyframes as diamond shapes
 * arranged on horizontal rows — one row per object/channel. Users can scrub
 * the playhead, select keyframes, and drag them to new positions.
 *
 * <p>Call {@link #drawDopeSheet} each ImGui frame to render and process input.
 */
@Deprecated
public class DopeSheet extends UIPanel {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    /** Render flag: draw the in-point marker on the timeline. */
    public static final int DRAW_IN_POINT = 256;

    /** Render flag: draw the out-point marker on the timeline. */
    public static final int DRAW_OUT_POINT = 512;

    /** Number of timeline ticks per wall-clock second (i.e. millisecond resolution). */
    private static final int TICKS_PER_SECOND = 1000;

    // -----------------------------------------------------------------------
    // Zoom
    // -----------------------------------------------------------------------

    /**
     * Current zoom level in <em>pixels per tick</em>.
     * Larger values zoom in; smaller values zoom out.
     */
    @Getter
    private float zoomFactor = 0.1f;

    /**
     * Pending zoom value that will be applied at the start of the next frame.
     * We delay by one frame to prevent jitter when zooming with the scroll wheel,
     * because the scroll position must be updated in the same frame the zoom changes.
     */
    private float nextZoomFactor = Float.NaN;

    // -----------------------------------------------------------------------
    // Key dragging state
    // -----------------------------------------------------------------------

    /**
     * Snapshot of each selected keyframe's time (in ticks) taken at the moment
     * the user began dragging. Used as the drag origin so we can compute an
     * absolute offset rather than accumulating floating-point deltas.
     *
     * <p>Non-empty while a drag is in progress; cleared when the mouse is released.
     */
    @Getter
    private final Map<KeyframeReference, Integer> keyDragOffsets = new HashMap<>();

    /**
     * The keyframe with the smallest original time among those being dragged,
     * together with its offset. Cached to support potential clamping logic.
     */
    private @Nullable KeyOffsetPair lowestKeyDragOffset;

    // -----------------------------------------------------------------------
    // Per-frame mouse state
    // -----------------------------------------------------------------------

    /** Whether the mouse was being dragged on the previous frame. */
    private boolean mouseWasDragging;

    /** {@code true} only on the first frame a drag gesture is detected. */
    private boolean mouseStartedDragging;

    // -----------------------------------------------------------------------
    // Playhead drag state
    // -----------------------------------------------------------------------

    /** {@code true} while the user is actively scrubbing the playhead. */
    @Getter
    private boolean draggingPlayhead;

    /**
     * {@code true} for exactly one frame after the user releases the playhead.
     * Callers can use this to trigger commit logic.
     */
    @Getter
    private boolean finishedDraggingPlayhead;

    // -----------------------------------------------------------------------
    // Miscellaneous per-frame output flags
    // -----------------------------------------------------------------------

    /**
     * {@code true} for exactly one frame when the user first starts dragging
     * keyframes. Cleared the following frame.
     */
    @Getter
    private boolean startedDraggingKeys;

    /** IDs of the object categories whose channel rows are currently expanded. */
    @Getter
    private final Set<String> openCategories = new HashSet<>();

    /**
     * IDs of objects whose keyframes were mutated and fully committed this frame
     * (i.e. the drag was released). Does <em>not</em> include objects with
     * keyframes still being dragged.
     */
    @Getter
    private final Set<String> updatedObjects = new HashSet<>();

    // -----------------------------------------------------------------------
    // Per-frame scratch collections (re-used to avoid allocations)
    // -----------------------------------------------------------------------

    /**
     * Maps a tick position to the index of that position in {@link #categoryKeys}.
     * Populated while building the category-level keyframe list and cleared each
     * object iteration.
     */
    private final Int2IntMap keyIndexCache = new Int2IntAVLTreeMap();

    /**
     * For each index in {@link #categoryKeys}, the set of per-channel keyframes
     * that share that tick position. Lets a click on a category key select all
     * the channel keys at the same time.
     */
    private final List<Set<ChannelKeyRef>> categoryKeyRefs = new ArrayList<>();

    /**
     * Synthetic {@link Keyframe} objects used to render the category header row.
     * Each entry corresponds to a unique tick position across all channels in the
     * category.
     */
    private final List<Keyframe> categoryKeys = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public DopeSheet(Identifier id) {
        super(id);
    }

    // -----------------------------------------------------------------------
    // Public API — zoom
    // -----------------------------------------------------------------------

    /**
     * Sets the zoom level.
     *
     * @param zoomFactor Pixels per tick. Must be strictly positive.
     * @throws IllegalArgumentException if {@code zoomFactor <= 0}.
     */
    public void setZoomFactor(float zoomFactor) {
        if (zoomFactor <= 0) {
            throw new IllegalArgumentException("Zoom factor must be greater than 0");
        }
        this.zoomFactor = zoomFactor;
    }

    // -----------------------------------------------------------------------
    // UIPanel override
    // -----------------------------------------------------------------------

    @Override
    protected void drawContents(EditorState editorState) {
        drawDopeSheet(
                editorState.getScene(),
                editorState.getKeySelection(),
                /* length = */ 20 * 1000,
                editorState.getPlayheadRef(),
                TimelineFlags.SNAP_KEYS
        );

        // If any objects were fully updated this frame, push an undo-able operator
        // and persist the scene to disk.
        if (!getUpdatedObjects().isEmpty()) {
            editorState.applyOperator(new CommitObjectUpdateOperator(getUpdatedObjects()));
            editorState.saveSceneAsync();
        }

        // While keys are being dragged (but not yet committed), we still need to
        // push the live positions to the game engine each frame.
        if (!getKeyDragOffsets().isEmpty()) {
            editorState.queueApplyToGame();
        }

        if (ImGui.shortcut(ImGuiKey.Delete)) {
            // TODO: re-enable once RemoveKeyframesOperator is implemented
            // editorState.applyOperator(new RemoveKeyframesOperator(editorState.getKeySelection()));
        }
    }

    // -----------------------------------------------------------------------
    // Main draw entry point
    // -----------------------------------------------------------------------

    /**
     * Render the full dope sheet and process all user input for one ImGui frame.
     *
     * <p>Keyframe times stored in {@code scene} are modified in place as the user
     * drags; a commit is issued to {@link #updatedObjects} when the drag ends.
     *
     * @param scene    The scene whose objects and keyframes are displayed.
     *                 Keyframe times are relative to the timeline's in-point.
     * @param selected Tracks which keyframes are currently selected.
     *                 Modified as the user clicks to select/deselect.
     * @param length   Timeline length in ticks ({@code outPoint - inPoint}).
     * @param playhead Current playhead position in ticks. Modified while scrubbing.
     *                 Pass {@code null} to hide the playhead entirely.
     * @param flags    Bitfield of {@link TimelineFlags} render/behaviour flags.
     */
    public void drawDopeSheet(ReplayScene scene, KeySelectionSet selected,
                              int length, @Nullable ImInt playhead, int flags) {

        // --- Reset single-frame output state ---
        finishedDraggingPlayhead = false;
        updatedObjects.clear();

        // --- Update mouse drag tracking ---
        updateMouseDragState();

        // --- Resolve any pending zoom change from the previous frame ---
        if (!Float.isNaN(nextZoomFactor)) {
            setZoomFactor(nextZoomFactor);
            nextZoomFactor = Float.NaN;
        }

        // --- Compute tick-interval grid ---
        float em = ImGui.getFontSize();
        float zoomFactor = getZoomFactor();                          // pixels per tick
        float emPerSecond = (zoomFactor * TICKS_PER_SECOND) / em;   // em-units per second

        float majorInterval = computeMajorTimeSpacing(emPerSecond, 8, 10); // seconds
        float minorInterval = majorInterval / 2;
        float tinyInterval  = majorInterval / 4;

        // Choose the snap resolution: use tiny ticks if they are large enough to be
        // useful, otherwise fall back to minor ticks.
        int snapTargetMs = resolvSnapTarget(tinyInterval, minorInterval, zoomFactor, em);

        // --- Layout: record the header region origin so we can draw it last ---
        // (The header is drawn after the scroll child so we know the scroll offset.)
        float headerCursorX = 0;
        float headerCursorY = 0;

        ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 0);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, ImGui.getStyle().getItemSpacingX(), 0);

        float headerHeight = ImGui.getTextLineHeight() * 2f;

        if (!hasFlag(TimelineFlags.NO_HEADER, flags)) {
            headerCursorX = ImGui.getCursorPosX();
            headerCursorY = ImGui.getCursorPosY();
            // Reserve vertical space; will be filled in after we know scrollAmount.
            ImGui.dummy(0, headerHeight);
        }

        // --- Outer child: contains the channel label list + the scrollable key area ---
        ImGui.beginChild("Dope Sheet Data",
                ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY(),
                false, ImGuiWindowFlags.NoScrollWithMouse);

        drawChannelList(scene, openCategories);

        // Measure the label column so we can align the header above the key area.
        float catGroupWidth  = ImGui.getItemRectSizeX() + ImGui.getStyle().getItemSpacingX();
        float catGroupHeight = ImGui.getItemRectSizeY();

        ImGui.sameLine();

        // --- Inner child: horizontally scrollable area containing the keyframe rows ---
        ImGui.beginChild("KeyList",
                ImGui.getContentRegionAvailX(),
                catGroupHeight + ImGui.getStyle().getScrollbarSize(),
                false, ImGuiWindowFlags.HorizontalScrollbar);

        ImGui.pushItemWidth(ImGui.getContentRegionAvailX());

        boolean wantStartDragging = drawKeyframes(scene, openCategories, selected, length, flags);

        // --- Process active key drag or initiate a new one ---
        float mWheel = processKeyDragging(scene, selected, wantStartDragging, snapTargetMs, zoomFactor, flags);

        // --- Zoom via scroll wheel (applied next frame to avoid jitter) ---
        mWheel = processScrollWheelZoom(mWheel, zoomFactor, playhead);

        float timelineScrollAmount = ImGui.getScrollX();

        ImGui.endChild(); // KeyList

        // --- Vertical scroll for the outer child (manual, so KeyList can suppress it) ---
        if (ImGui.isWindowHovered() && mWheel != 0) {
            float curScrollY = ImGui.getScrollY();
            float newScrollY = Math.clamp(curScrollY - mWheel * 24, 0, ImGui.getScrollMaxY());
            ImGui.setScrollY(newScrollY);
            ImGui.getIO().setMouseWheel(0);
        }

        ImGui.endChild(); // Dope Sheet Data

        ImGui.popStyleVar();
        ImGui.popStyleVar();

        // --- Draw header on top of everything else (needs scroll offset) ---
        if (!hasFlag(TimelineFlags.NO_HEADER, flags)) {
            ImGui.setCursorPosX(headerCursorX + catGroupWidth);
            ImGui.setCursorPosY(headerCursorY);
            drawHeader(headerHeight, timelineScrollAmount, length, majorInterval,
                    playhead, catGroupHeight, flags);
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers extracted from drawDopeSheet
    // -----------------------------------------------------------------------

    /**
     * Updates {@link #mouseWasDragging} and {@link #mouseStartedDragging} based on
     * the current ImGui mouse state, and clears playhead drag state when the mouse
     * is released.
     */
    private void updateMouseDragState() {
        if (ImGui.isMouseDragging(0)) {
            mouseStartedDragging = !mouseWasDragging;
            mouseWasDragging = true;
        } else {
            mouseStartedDragging = false;
            mouseWasDragging = false;
            if (draggingPlayhead) {
                finishedDraggingPlayhead = true;
            }
            draggingPlayhead = false;
        }
    }

    /**
     * Chooses the snap grid resolution in milliseconds.
     *
     * <p>We prefer tiny ticks (quarter-major) but only use them when they are
     * physically large enough on screen (> 1.2 em) to be worth snapping to.
     *
     * @return Snap resolution in milliseconds (ticks).
     */
    private int resolvSnapTarget(float tinyInterval, float minorInterval, float zoomFactor, float em) {
        if (tinyInterval * TICKS_PER_SECOND * zoomFactor > em * 1.2) {
            return (int) (tinyInterval * TICKS_PER_SECOND);
        } else {
            return (int) (minorInterval * TICKS_PER_SECOND);
        }
    }

    /**
     * Handles the full key-drag lifecycle for one frame:
     * <ul>
     *   <li>If a drag is already in progress, translates all dragged keyframes by
     *       the mouse delta (with optional snapping).</li>
     *   <li>When the mouse is released, removes duplicate keys (unless
     *       {@link TimelineFlags#ALLOW_DUPLICATE_KEYS} is set) and adds all
     *       affected objects to {@link #updatedObjects}.</li>
     *   <li>If no drag is in progress but {@code wantStartDragging} is true,
     *       snapshots the current times of all selected keyframes to start a
     *       new drag.</li>
     * </ul>
     *
     * @param wantStartDragging {@code true} when the user began a drag gesture
     *                          over a keyframe this frame.
     * @return The current mouse-wheel value (unchanged; returned so the caller can
     *         pass it along for vertical-scroll handling after consuming zoom).
     */
    private float processKeyDragging(ReplayScene scene, KeySelectionSet selected,
                                     boolean wantStartDragging, int snapTargetMs,
                                     float zoomFactor, int flags) {
        if (!keyDragOffsets.isEmpty()) {
            if (ImGui.isMouseDragging(0, 0)) {
                // Compute how far the mouse has moved in ticks since the drag started.
                int dx = (int) (ImGui.getMouseDragDeltaX() / zoomFactor);

                for (var entry : keyDragOffsets.entrySet()) {
                    Keyframe key = entry.getKey().get(scene.getObjects());
                    if (key == null) continue;

                    int tPrime = entry.getValue() + dx;

                    // Snap to the nearest grid line if requested.
                    if (hasFlag(TimelineFlags.SNAP_KEYS, flags)) {
                        int dragSnapTarget = snapTargetMs / 2;
                        tPrime = dragSnapTarget * Math.round((float) tPrime / dragSnapTarget);
                    }

                    key.setTime(tPrime);
                }
            } else {
                // Mouse released — commit the drag.

                if (!hasFlag(TimelineFlags.ALLOW_DUPLICATE_KEYS, flags)) {
                    // If two keys ended up at the same time in the same channel, keep only one.
                    keyDragOffsets.keySet().stream()
                            .map(ref -> ref.channelRef().get(scene.getObjects()))
                            .filter(Objects::nonNull)
                            .distinct()
                            .forEach(KeyChannel::removeDuplicates);
                }

                // Collect the names of all objects that had keyframes moved.
                updatedObjects.addAll(
                        keyDragOffsets.keySet().stream()
                                .map(ref -> ref.channelRef().objectName())
                                .toList()
                );

                keyDragOffsets.clear();
            }
        } else if (wantStartDragging && !hasFlag(TimelineFlags.READONLY, flags)) {
            // Begin a new drag: snapshot the current time of every selected keyframe.
            lowestKeyDragOffset = null;
            selected.forSelectedKeyframes(ref -> {
                Keyframe keyframe = ref.get(scene.getObjects());
                if (keyframe != null) {
                    keyDragOffsets.put(ref, keyframe.getTimeInt());
                }
            });
            startedDraggingKeys = true;
        }

        return ImGui.getIO().getMouseWheel();
    }

    /**
     * Handles scroll-wheel zoom.
     *
     * <p>The new zoom factor is stored in {@link #nextZoomFactor} and applied on
     * the <em>next</em> frame to avoid jitter. The scroll position is adjusted in
     * the same call so the point under the playhead stays fixed.
     *
     * @param mWheel     Current mouse-wheel delta from {@link imgui.ImGuiIO}.
     *                   Non-zero only when the key list child is hovered.
     * @param zoomFactor The zoom factor active this frame (before any change).
     * @param playhead   Current playhead position, or {@code null}.
     * @return The remaining mouse-wheel value after zoom consumed it
     *         (0 if zoom was applied, unchanged otherwise).
     */
    private float processScrollWheelZoom(float mWheel, float zoomFactor, @Nullable ImInt playhead) {
        if (mWheel == 0 || !ImGui.isWindowHovered()) {
            return mWheel;
        }

        float oldScroll = ImGui.getScrollX();

        // Each scroll step scales by 2^(step * 0.125), giving smooth exponential zoom.
        float factor  = (float) Math.pow(2, mWheel * 0.125f);
        float newZoom = zoomFactor * factor;
        nextZoomFactor = newZoom;

        // Keep the playhead pixel-stable during zoom; if there is no playhead,
        // scale the scroll offset proportionally.
        if (playhead != null) {
            float playMs    = (float) playhead.get();
            float playPxOld = playMs * zoomFactor;
            float playPxNew = playMs * newZoom;
            ImGui.setScrollX(oldScroll + (playPxNew - playPxOld));
        } else {
            ImGui.setScrollX(oldScroll * newZoom / zoomFactor);
        }

        // TODO: SetItemKeyOwner once it's added to the ImGui Java bindings.

        return 0; // Zoom consumed the wheel event.
    }

    // -----------------------------------------------------------------------
    // Grid / tick spacing
    // -----------------------------------------------------------------------

    /**
     * Computes how many <em>seconds</em> should elapse between major tick marks
     * so that they appear roughly {@code targetEmInterval} em-units apart.
     *
     * <p>The result is always a power of {@code multiple} (e.g. 0.1, 1, 10, 100 s
     * when {@code multiple = 10}), giving a clean human-readable scale.
     *
     * @param emPerSecond      How many em-units represent one second at the current zoom.
     * @param targetEmInterval Desired pixel gap between major ticks (in em units).
     * @param multiple         Rounding base for the interval (typically 10).
     * @return Seconds between adjacent major ticks.
     */
    private static float computeMajorTimeSpacing(float emPerSecond, int targetEmInterval, int multiple) {
        double idealSeconds  = targetEmInterval / emPerSecond;
        double log           = Math.log(idealSeconds) / Math.log(multiple);
        double roundedPower  = Math.round(log);
        return (float) Math.pow(multiple, roundedPower);
    }

    // -----------------------------------------------------------------------
    // Header (tick ruler + playhead)
    // -----------------------------------------------------------------------

    /**
     * Draws the time ruler at the top of the timeline and the draggable playhead.
     *
     * <p>This is called <em>after</em> the scrollable key area so that we know the
     * final horizontal scroll offset and can align the tick marks correctly.
     *
     * @param headerHeight   Height of the ruler bar in pixels.
     * @param scrollAmount   Horizontal scroll offset of the key-list child in pixels.
     * @param length         Timeline length in ticks.
     * @param majorInterval  Seconds between major tick marks (from
     *                       {@link #computeMajorTimeSpacing}).
     * @param playhead       Current playhead position in ticks; {@code null} hides it.
     * @param channelsHeight Total pixel height of the channel rows, used to draw the
     *                       vertical playhead line through the key area.
     * @param flags          Render/behaviour flags.
     */
    private void drawHeader(float headerHeight, float scrollAmount, int length, float majorInterval,
                            @Nullable ImInt playhead, float channelsHeight, int flags) {

        float width   = ImGui.getContentRegionAvailX();
        float cursorX = ImGui.getCursorScreenPosX();
        float cursorY = ImGui.getCursorScreenPosY();

        ImDrawList drawList = ImGui.getWindowDrawList();

        // Ruler background
        drawList.addRectFilled(cursorX, cursorY, cursorX + width, cursorY + headerHeight,
                ImGuiCol.TableHeaderBg);
        drawList.pushClipRect(cursorX, cursorY, cursorX + width, cursorY + headerHeight);

        // The invisible button captures clicks for playhead dragging.
        ImGui.invisibleButton("#header", width, headerHeight);

        float em          = ImGui.getFontSize();
        float zoomFactor  = getZoomFactor();
        float emPerSecond = (zoomFactor * TICKS_PER_SECOND) / em;

        int   outSecond    = Math.ceilDiv(length, TICKS_PER_SECOND);
        float minorInterval = majorInterval / 2;
        float tinyInterval  = majorInterval / 4;

        int snapTargetMs = resolvSnapTarget(tinyInterval, minorInterval, zoomFactor, em);

        if (!hasFlag(TimelineFlags.NO_TICKS, flags)) {
            drawTickMarks(drawList, cursorX, cursorY, headerHeight,
                    scrollAmount, outSecond, majorInterval, minorInterval, tinyInterval, em, zoomFactor);
        }

        drawList.popClipRect();

        // --- Playhead ---
        if (playhead != null && !hasFlag(TimelineFlags.NO_PLAYHEAD, flags)) {
            drawPlayhead(drawList, playhead, cursorX, cursorY, headerHeight,
                    scrollAmount, length, channelsHeight, snapTargetMs, zoomFactor, flags);
        }
    }

    /**
     * Draws major, minor, and tiny tick marks (and their labels) across the ruler.
     */
    private void drawTickMarks(ImDrawList drawList,
                               float cursorX, float cursorY, float headerHeight,
                               float scrollAmount, int outSecond,
                               float majorInterval, float minorInterval, float tinyInterval,
                               float em, float zoomFactor) {

        // Major ticks — include a time label above each one.
        for (float sec = 0; sec <= outSecond; sec += majorInterval) {
            float pos = sec * TICKS_PER_SECOND * zoomFactor - scrollAmount;

            String label = (majorInterval < 1)
                    ? String.format("%.2f", sec)
                    : Integer.toString(Math.round(sec));

            ImVec2 strLen = ImGui.calcTextSize(label);
            drawList.addText(cursorX + (pos - strLen.x / 2f), cursorY, 0xFFFFFFFF, label);

            // Tick line starts at 55 % of header height.
            drawList.addLine(cursorX + pos, cursorY + headerHeight / 1.8f,
                    cursorX + pos, cursorY + headerHeight, 0xAAAAAAAA);
        }

        // Minor ticks — starts at ~71 % of header height.
        for (float sec = 0; sec <= outSecond; sec += minorInterval) {
            float pos = sec * TICKS_PER_SECOND * zoomFactor - scrollAmount;
            drawList.addLine(cursorX + pos, cursorY + headerHeight / 1.4f,
                    cursorX + pos, cursorY + headerHeight, 0xAAAAAAAA);
        }

        // Tiny ticks — only drawn when they are physically large enough to be readable.
        if (tinyInterval * TICKS_PER_SECOND * zoomFactor > em * 1.2) {
            for (float sec = 0; sec <= outSecond; sec += tinyInterval) {
                float pos = sec * TICKS_PER_SECOND * zoomFactor - scrollAmount;
                drawList.addLine(cursorX + pos, cursorY + headerHeight / 1.2f,
                        cursorX + pos, cursorY + headerHeight, 0xAAAAAAAA);
            }
        }
    }

    /**
     * Draws the playhead indicator (vertical line + handle rectangle) and processes
     * dragging input.
     *
     * <p>The playhead is only draggable when {@link TimelineFlags#READONLY_PLAYHEAD}
     * is not set and the cursor is hovering the header area.
     */
    private void drawPlayhead(ImDrawList drawList, ImInt playhead,
                              float cursorX, float cursorY, float headerHeight,
                              float scrollAmount, int length, float channelsHeight,
                              int snapTargetMs, float zoomFactor, int flags) {

        int color = ImGui.getColorU32(ImGuiCol.CheckMark) | 0xFF000000;

        // Begin drag when the header is clicked (and dragging is allowed).
        if (!hasFlag(TimelineFlags.READONLY_PLAYHEAD, flags)
                && ImGui.isItemHovered()
                && ImGui.isMouseDown(0)) {
            draggingPlayhead = true;
        }

        if (draggingPlayhead) {
            // Convert mouse X back to ticks, clamp to [0, length].
            int newPlayhead = (int) ((ImGui.getMousePosX() + scrollAmount - cursorX) / zoomFactor);
            newPlayhead = Math.clamp(newPlayhead, 0, length);

            if (hasFlag(TimelineFlags.SNAP_PLAYHEAD, flags)) {
                newPlayhead = snapTargetMs * Math.round((float) newPlayhead / snapTargetMs);
            }

            playhead.set(newPlayhead);
        }

        // The playhead line and handle are drawn in a no-input overlay child so they
        // appear on top of the channel rows without intercepting mouse events.
        ImGui.setCursorPos(0, 0);
        ImGui.beginChild("overlay", ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY(),
                false, ImGuiWindowFlags.NoBackground | ImGuiWindowFlags.NoInputs);

        float playheadX = cursorX + playhead.get() * zoomFactor - scrollAmount;
        float radius    = ImGui.getFontSize() / 2f;

        var overlayDrawList = ImGui.getWindowDrawList();
        if (playheadX >= cursorX) {
            // Vertical line through the channel rows.
            overlayDrawList.addLine(playheadX, cursorY + headerHeight,
                    playheadX, cursorY + headerHeight + channelsHeight, color);
            // Diamond/rectangular handle in the ruler.
            overlayDrawList.addRectFilled(playheadX - radius, cursorY + headerHeight / 2,
                    playheadX + radius, cursorY + headerHeight, color);
        }

        ImGui.endChild();
    }

    // -----------------------------------------------------------------------
    // Channel label list (left column)
    // -----------------------------------------------------------------------

    /**
     * Draws the collapsible tree of object/channel names on the left side of the
     * dope sheet.
     *
     * <p>Each top-level entry is an object ({@link ReplayObject}); its children
     * are individual {@link KeyChannel}s. Toggling a tree node adds or removes the
     * object ID from {@code openCategories}.
     *
     * @param scene          The scene to read objects from.
     * @param openCategories Mutable set of object IDs whose rows are expanded.
     */
    private void drawChannelList(ReplayScene scene, Set<String> openCategories) {
        ImGui.pushID("channels");
        ImGui.beginGroup();

        for (var entry : scene.getObjects().entrySet()) {
            String catName = entry.getKey();
            ReplayObject cat = entry.getValue();

            ImGui.setNextItemOpen(openCategories.contains(catName));
            ImGui.alignTextToFramePadding();

            boolean catOpen = ImGui.treeNodeEx(catName);

            // Sync open/closed state back to our set when the user toggles the node.
            if (ImGui.isItemToggledOpen() && !openCategories.remove(catName)) {
                openCategories.add(catName);
            }

            if (catOpen) {
                for (var chName : cat.getChannels().keySet()) {
                    ImGui.alignTextToFramePadding();
                    if (ImGui.treeNodeEx(chName, ImGuiTreeNodeFlags.Leaf)) {
                        ImGui.treePop();
                    }
                }
                ImGui.treePop();
            }
        }

        ImGui.dummy(128, 0); // Enforce a minimum width for the label column.
        ImGui.endGroup();
        ImGui.popID();
    }

    // -----------------------------------------------------------------------
    // Keyframe rows (right / scrollable column)
    // -----------------------------------------------------------------------

    /**
     * Draws all keyframe rows (one per object, plus one per open channel) and
     * returns whether the user started a drag gesture this frame.
     *
     * <p>For each object, a synthetic "category row" is built by merging all
     * channel keyframes that share the same tick position. Clicking a diamond in
     * the category row selects the corresponding keyframes in every channel.
     *
     * @return {@code true} if the user began dragging over a selected keyframe.
     */
    private boolean drawKeyframes(ReplayScene scene, Set<String> openCategories,
                                  KeySelectionSet selected, float length, int flags) {

        ImDrawList drawList = ImGui.getWindowDrawList();
        int rowIndex = 0;
        boolean wantStartDragging = false;

        for (var entry : scene.getObjects().entrySet()) {
            String categoryId = entry.getKey();
            ReplayObject category = entry.getValue();

            // --- Build the merged category-level keyframe list ---
            categoryKeyRefs.clear();
            keyIndexCache.clear();
            categoryKeys.clear();

            for (var chEntry : category.getChannels().entrySet()) {
                String chName = chEntry.getKey();
                KeyChannel channel = chEntry.getValue();

                for (int keyIndex = 0; keyIndex < channel.getKeyframes().size(); keyIndex++) {
                    int keyTime = channel.getKeyframes().get(keyIndex).getTimeInt();

                    // Reuse an existing category entry if another channel already has a
                    // keyframe at this tick, otherwise create a new one.
                    int categoryIndex;
                    Set<ChannelKeyRef> keyRefs;
                    if (keyIndexCache.containsKey(keyTime)) {
                        categoryIndex = keyIndexCache.get(keyTime);
                        keyRefs = categoryKeyRefs.get(categoryIndex);
                    } else {
                        keyRefs = new HashSet<>();
                        categoryIndex = categoryKeyRefs.size();
                        categoryKeyRefs.add(keyRefs);
                        categoryKeys.add(new Keyframe(keyTime, 0));
                        keyIndexCache.put(keyTime, categoryIndex);
                    }

                    keyRefs.add(new ChannelKeyRef(chName, keyIndex));
                }
            }

            // --- Draw the merged category row ---
            ImGui.setNextItemWidth(length * zoomFactor);
            if (drawKeyChannel(
                    categoryKeys,
                    rowIndex,
                    // A category key is selected iff any of its constituent channel keys is selected.
                    keyIndex -> {
                        for (var ref : categoryKeyRefs.get(keyIndex)) {
                            if (selected.isKeyframeSelected(
                                    new KeyframeReference(categoryId, ref.channelId(), ref.keyIndex()))) {
                                return true;
                            }
                        }
                        return false;
                    },
                    // Clicking a category key selects all constituent channel keys.
                    keyIndex -> {
                        if (!ImGui.getIO().getKeyCtrl()) {
                            selected.deselectAll();
                        }
                        if (keyIndex != null) {
                            for (var ref : categoryKeyRefs.get(keyIndex)) {
                                selected.selectKeyframe(
                                        new KeyframeReference(categoryId, ref.channelId(), ref.keyIndex()));
                            }
                        }
                    },
                    drawList, flags)) {
                wantStartDragging = true;
            }
            rowIndex++;

            // --- Draw individual channel rows (only when the category is expanded) ---
            if (openCategories.contains(categoryId)) {
                for (var chEntry : category.getChannels().entrySet()) {
                    KeyChannel channel = chEntry.getValue();

                    ImGui.setNextItemWidth(length * zoomFactor);
                    if (drawKeyChannel(
                            channel.getKeyframes(),
                            rowIndex,
                            keyIdx -> selected.isKeyframeSelected(categoryId, chEntry.getKey(), keyIdx),
                            keyIndex -> {
                                if (!ImGui.getIO().getKeyCtrl()) {
                                    selected.deselectAll();
                                }
                                if (keyIndex != null) {
                                    selected.selectKeyframe(categoryId, chEntry.getKey(), keyIndex);
                                }
                            },
                            drawList, flags)) {
                        wantStartDragging = true;
                    }
                    rowIndex++;
                }
            }
        }

        return wantStartDragging;
    }

    // -----------------------------------------------------------------------
    // Single-channel row renderer
    // -----------------------------------------------------------------------

    /**
     * Draws a single horizontal keyframe row.
     *
     * <p>Each keyframe is rendered as a rotated square (diamond) via
     * {@link ImDrawList#addNgonFilled} with 4 sides. The row background alternates
     * colour for readability.
     *
     * @param keys       The keyframes to render.  Their {@code time} fields (in ticks)
     *                   are read each frame, so live drag updates are reflected
     *                   immediately.
     * @param rowIndex   Vertical row index, used for alternating row colours and
     *                   ImGui push IDs.
     * @param isSelected Predicate: returns {@code true} if the key at the given list
     *                   index is currently selected (controls diamond colour).
     * @param onClick    Called on left-click with the index of the key under the
     *                   cursor, or {@code null} if the click was on empty space.
     * @param drawList   The ImGui draw list to submit geometry to.
     * @param flags      Render/behaviour flags.
     * @return {@code true} if the user began a drag gesture over this row while a
     *         key was hovered.
     */
    private boolean drawKeyChannel(List<Keyframe> keys, int rowIndex,
                                   IntPredicate isSelected, Consumer<Integer> onClick,
                                   ImDrawList drawList, int flags) {
        ImGui.pushID("Dope Channel " + rowIndex);

        float lineWidth  = ImGui.calcItemWidth();
        float lineHeight = ImGui.getFrameHeight();
        float cursorX    = ImGui.getCursorScreenPosX();
        float cursorY    = ImGui.getCursorScreenPosY();

        // Alternating row background — even rows use the "alt" colour.
        int bgColor = (rowIndex % 2 == 0)
                ? ImGui.getColorU32(ImGuiCol.TableRowBgAlt)
                : ImGui.getColorU32(ImGuiCol.TableRowBg);
        drawList.addRectFilled(cursorX, cursorY, cursorX + lineWidth, cursorY + lineHeight, bgColor);

        float keySize    = ImGui.getFontSize();
        float keyRadius  = keySize / 2;
        float centerY    = cursorY + lineHeight / 2;

        // --- Hit-test helper: finds the key (by index) whose diamond contains (posX, posY) ---
        BiFloatFunction<Integer> getHoveredKey = (posX, posY) -> {
            int i = 0;
            for (var key : keys) {
                float centerX = cursorX + key.getTimeInt() * zoomFactor;
                // 2-pixel padding on each side for easier clicking.
                if (centerX - keyRadius - 2 < posX && posX < centerX + keyRadius + 2
                        && centerY - keyRadius - 2 <= posY && posY <= centerY + keyRadius + 2) {
                    return i;
                }
                i++;
            }
            return null;
        };

        // Invisible button to capture hover / click events for the whole row.
        ImGui.invisibleButton("##canvas", lineWidth, lineHeight);

        boolean wantsStartDragging = false;
        float mx = ImGui.getMousePosX();
        float my = ImGui.getMousePosY();
        Integer hovered = getHoveredKey.apply(mx, my);

        if (ImGui.isItemHovered()) {
            if (mouseStartedDragging && hovered != null) {
                // The user dragged from a key — signal that a drag should begin.
                wantsStartDragging = true;
            } else if (ImGui.isMouseClicked(0)) {
                onClick.accept(hovered);
            }
        }

        // --- Render diamond shapes for each keyframe ---
        int i = 0;
        for (Keyframe key : keys) {
            boolean selected = isSelected.test(i);
            // White = selected; grey = unselected.
            int keyColor = selected ? ImColor.rgb(1f, 1f, 1f) : ImColor.rgb(.5f, .5f, .5f);

            float centerX = cursorX + key.getTimeInt() * zoomFactor;
            // addNgonFilled with 4 sides and 45 ° rotation from the default gives a diamond.
            drawList.addNgonFilled(centerX, centerY, keyRadius, keyColor, 4);
            i++;
        }

        ImGui.popID();
        return wantsStartDragging;
    }

    // -----------------------------------------------------------------------
    // Internal records and interfaces
    // -----------------------------------------------------------------------

    /**
     * A keyframe reference paired with the time offset used as the drag origin.
     * Stored transiently while a drag is being initiated.
     */
    private record KeyOffsetPair(KeyframeReference ref, Integer offset) {}

    /**
     * Identifies a keyframe within a channel by the channel's string ID and the
     * keyframe's position index within that channel's list.
     */
    private record ChannelKeyRef(String channelId, int keyIndex) {}

    /**
     * Deprecated intermediate representation.  Use {@link ChannelKeyRef} instead.
     */
    @Deprecated
    private record IntPair(int a, int b) {}

    /**
     * A {@link java.util.function.BiFunction} variant whose arguments are primitive
     * {@code float}s, avoiding boxing overhead in the hot draw path.
     */
    private interface BiFloatFunction<T> {
        T apply(float a, float b);
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    /** Returns {@code true} if {@code flag} is set in the {@code flags} bitfield. */
    private static boolean hasFlag(int flag, int flags) {
        return (flags & flag) == flag;
    }
}