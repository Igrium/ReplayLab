package com.igrium.replaylab.ui;

import com.igrium.replaylab.editor.KeySelectionSet;
import com.igrium.replaylab.editor.KeySelectionSet.KeyHandleReference;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.key.KeyChannel;
import com.igrium.replaylab.scene.key.Keyframe;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import com.igrium.replaylab.ui.util.TimelineFlags;
import com.igrium.replaylab.ui.util.TimelineHeader;
import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;
import org.joml.*;

import java.lang.Math;
import java.util.*;
import java.util.function.*;

public class CurveEditor {

    /**
     * Get a channel's hue based on its object and index
     *
     * @param objNameHash The hash code of the object name
     * @param chNameHash  The hash code of the channel name
     * @return The hue of the channel as a float from 0-1
     */
    private static float getChannelHue(int objNameHash, int chNameHash) {
        int x = objNameHash * 32 + chNameHash;

        x ^= x << 13;
        x ^= x >>> 17;
        x ^= x << 5;

        // Map to 0-1 range by clamping to 2^24 and dividing
        int top24 = x >>> 8;
        return top24 / (float)(1 << 24);
    }

    private static final int[] curveColors = new int[16];

    static {
        for (int i = 0; i < 16; i++) {
            curveColors[i] = ImColor.hsla(i / 16f, 1f, .5f, .75f);
        }
    }

    /**
     * The X pan amount in milliseconds
     */
    @Getter @Setter
    private double offsetX;

    /**
     * The Y pan amount in curve units
     */
    @Getter @Setter
    private double offsetY;

    /**
     * Amount of pixels per millisecond
     */
    @Getter
    private float zoomFactorX = 0.1f;

    /**
     * Amount of pixels per curve unit
     */
    @Getter
    private float zoomFactorY = 0.1f;

    /**
     * All the replay objects that have had an update <em>committed</em> this frame.
     * Does not include keyframes being dragged.
     */
    @Getter
    private final Set<String> updatedObjects = new HashSet<>();

    /**
     * The amount of ms / value units offset from the mouse that each key being dragged has.
     */
    private final Map<KeyHandleReference, Vector2dc> keyDragOffsets = new HashMap<>();

    private record KeyOffsetPair(KeyHandleReference ref, Vector2dc offset) {};

    /**
     * The key drag offset that's closest to the mouse
     */
    private @Nullable KeyOffsetPair smallestKeyDragOffset;

    /**
     * The global location of the smallest key drag offset at the time of drag start
     */
    private final Vector2d dragStartPos = new Vector2d();

    private final TimelineHeader header = new TimelineHeader();

    // Not null if currently panning
    private @Nullable Vector2d panStartPos;

    private final ImBoolean snapKeyframes = new ImBoolean();

    private boolean doneInitialFit = false;

    /**
     * The global pixel position of a selection box start position
     */
    private @Nullable ImVec2 boxSelectStart;

    public boolean isScrubbing() {
        return header.isScrubbing();
    }

    public boolean stoppedScrubbing() {
        return header.stoppedScrubbing();
    }

    public boolean isDragging() {
        return !keyDragOffsets.isEmpty();
    }

    public boolean isBoxSelecting() {
        return boxSelectStart != null;
    }

    public void setZoomFactorX(float zoomFactorX) {
        if (zoomFactorX <= 0) {
            throw new IllegalArgumentException("Zoom factor must be greater than 0");
        }
        this.zoomFactorX = zoomFactorX;
    }

    public void setZoomFactorY(float zoomFactorY) {
        if (zoomFactorY <= 0) {
            throw new IllegalArgumentException("Zoom factor must be greater than 0");
        }
        this.zoomFactorY = zoomFactorY;
    }

    /**
     * Modify the zoom of the editor on the X axis, centering it around a supplied point.
     * @param targetZoom New zoom factor.
     * @param center Point to center around (ms)
     */
    public void setZoomFactorX(float targetZoom, double center) {
        if (targetZoom == this.zoomFactorX) return;

        double newOffsetX = center - (center - offsetX) * (this.zoomFactorX / targetZoom);
        this.zoomFactorX = targetZoom;
        this.offsetX = newOffsetX;
    }

    public void setZoomFactorY(float targetZoom, double center) {
        if (targetZoom == this.zoomFactorY) return;

        double newOffsetY = center - (center - offsetY) * (this.zoomFactorY / targetZoom);
        this.zoomFactorY = targetZoom;
        this.offsetY = newOffsetY;
    }

    public void fitBounds(Iterable<? extends ReplayObject> objs, float graphWidthPx, float graphHeightPx) {
        Vector2d boundsMin = new Vector2d();
        Vector2d boundsMax = new Vector2d();
        computeBoundingBox(objs, boundsMin, boundsMax);

        setOffsetX(boundsMin.x);
        setOffsetY(boundsMin.y);

        if (boundsMin.x != boundsMax.x)
            setZoomFactorX((float) (graphWidthPx / (boundsMax.x - boundsMin.x)));

        if (boundsMin.y != boundsMax.x)
            setZoomFactorY((float) (graphHeightPx / (boundsMax.y - boundsMin.y)));
    }

    /**
     * Draw the curve editor.
     *
     * @param scene           The scene to edit. Keyframes will be updated as the user changes them.
     * @param selectedObjects The objects to display the keyframes of. <code>null</code> to display all objects
     * @param selectedKeys    All keyframe handles which are currently selected.
     *                        Updated as the user selects/deselects keyframes.
     * @param playhead        Current playhead position. Updated as the player scrubs.
     * @param flags           Render flags.
     */
    public void drawCurveEditor(ReplayScene scene, @Nullable Collection<String> selectedObjects,
                                KeySelectionSet selectedKeys, @Nullable ImInt playhead, int flags) {

        updatedObjects.clear();
        Collection<String> objs = selectedObjects != null ? selectedObjects : scene.getObjects().keySet();

        int majorIntervalX = (int) TimelineHeader.computeMajorInterval(zoomFactorX);
        int minorIntervalX = majorIntervalX / 2;

        float majorIntervalY = TimelineHeader.computeMajorInterval(zoomFactorY);
        float minorIntervalY = majorIntervalY / 2;

        float headerCursorY = ImGui.getCursorPosY();
        float headerHeight = ImGui.getTextLineHeight() * 2f;

        ImGui.dummy(0, headerHeight);
        ImGui.sameLine();

        /// === BUTTONS ===
        ReplayLabControls.toggleButton(ReplayLabIcons.ICON_MAGNET, "Snap Keyframes", snapKeyframes);
        ImGui.sameLine();
        boolean wantsFit = ReplayLabControls.iconButton(ReplayLabIcons.ICON_RESIZE_FULL_ALT, "Fit to Selected");

        /// === CHANNEL LIST ===

        ImGui.pushID("channels");
        ImGui.beginGroup();
        ImGui.separator();
        ImBoolean locked = new ImBoolean();
        for (var name : objs) {
            ReplayObject obj = scene.getObject(name);
            if (obj == null)
                continue;

            if (ImGui.treeNodeEx(name)) {
                for (var entry : obj.getChannels().entrySet()) {
                    ImGui.text(entry.getKey());
                    ImGui.sameLine();
                    boolean wasLocked = entry.getValue().isLocked();
                    locked.set(wasLocked);

                    ImGui.checkbox("##" + entry.getKey() + "_lock", locked);
                    if (locked.get() != wasLocked) {
                        entry.getValue().setLocked(locked.get());
                    }
                }
                ImGui.treePop();
            }
        }

        ImGui.dummy(128, 0); // force width
        ImGui.endGroup();
        ImGui.popID();

        ImGui.sameLine();
        float headerCursorX = ImGui.getCursorPosX();
        float graphHeight = ImGui.getContentRegionAvailY();

        float mouseGlobalX = ImGui.getMousePosX();
        float mouseGlobalY = ImGui.getMousePosY();

        /// === GRAPH ===

        if (ImGui.beginChild("keygraph", ImGui.getContentRegionAvailX(), graphHeight, false)) {
            float graphX = ImGui.getCursorScreenPosX();
            float graphY = ImGui.getCursorScreenPosY();

            float gWidth = ImGui.getContentRegionAvailX();
            float gHeight = ImGui.getContentRegionAvailY();

            /// === FITTING ===
            if ((wantsFit || !doneInitialFit) && !objs.isEmpty()) {
                Vector2d boundsMin = new Vector2d();
                Vector2d boundsMax = new Vector2d();

                if (!doneInitialFit || selectedKeys.isEmpty()) {
                    computeBoundingBox(scene.getObjects().values(), boundsMin, boundsMax);
                } else {
                    computeBoundingBox(selectedKeys, scene, boundsMin, boundsMax);
                }

                if (boundsMin.x != boundsMax.x)
                    setZoomFactorX((float) (gWidth / (boundsMax.x - boundsMin.x)));

                if (boundsMin.y != boundsMax.x)
                    setZoomFactorY((float) (gHeight / (boundsMax.y - boundsMin.y)));

                setOffsetX(boundsMin.x);
                setOffsetY(boundsMin.y);

                doneInitialFit = true;
            }

            ImGui.invisibleButton("##curveGraph", gWidth, gHeight);
            boolean graphHovered = ImGui.isItemHovered();

            /// === SCROLL ZOOM ===
            {
                if (graphHovered) {
                    double mouseXMs = pixelXToMs(mouseGlobalX - graphX);
                    double mouseYValue = pixelYToValue(mouseGlobalY - graphY);

                    float mWheel = ImGui.getIO().getMouseWheel();
                    if (mWheel != 0) {
                        float factor = (float) Math.pow(2, mWheel * .125);

                        if (!ImGui.getIO().getKeyShift()) {
                            setZoomFactorX(zoomFactorX * factor, mouseXMs);
                        }
                        if (!ImGui.getIO().getKeyCtrl()) {
                            setZoomFactorY(zoomFactorY * factor, mouseYValue);
                        }

                    }
                }
            }


            ImDrawList drawList = ImGui.getWindowDrawList();

            /// === BACKGROUND ===
            drawList.addRectFilled(graphX, graphY, graphX + gWidth, graphY + gHeight, ImGui.getColorU32(ImGuiCol.FrameBg));

            // Amount of milliseconds the graph is wide
            float widthMs = gWidth / zoomFactorX;
            int startTick = Math.floorDiv((int) offsetX, majorIntervalX) * majorIntervalX;
            int endTick = Math.ceilDiv((int) (offsetX + widthMs), majorIntervalX) * majorIntervalX;

            int colMajor = replaceAlpha(ImGui.getColorU32(ImGuiCol.Text), 48);
            int colMinor = replaceAlpha(colMajor, 16);

            // X intervals
            for (int ms = startTick; ms <= endTick; ms+= minorIntervalX) {
                float xPos = msToPixelX(ms) + graphX;
                int color = ms % majorIntervalX == 0 ? colMajor : colMinor;
                drawList.addLine(xPos, graphY, xPos, graphY + gHeight, color);
            }

            // Amount of units the graph is tall
            float heightUnits = gHeight / zoomFactorY;

            double startValue = Math.floor(offsetY / majorIntervalY) * majorIntervalY;
            double endValue = Math.ceil((offsetY + heightUnits) / majorIntervalY) * majorIntervalY;

            // Y intervals
            for (double value = startValue; value <= endValue; value += minorIntervalY) {
                float yPos = valueToPixelY(value) + graphY;
                int color = value % majorIntervalY == 0 ? colMajor : colMinor;
                drawList.addLine(graphX, yPos, graphX + gWidth, yPos, color);
            }

            /// === Keyframes ===
            KeyHandleReference clickedOn = null;

            boolean mouseClicked = ImGui.isItemClicked();

            // If we're overing any key, with some leeway for drag threshold
            boolean hoveringAnyKey = false;
            float keyHoverRadius = 12f + ImGui.getIO().getMouseDragThreshold() * 2;

            int chIndex = 0;
            for (String objName : objs) {
                ReplayObject obj = scene.getObject(objName);
                if (obj == null)
                    continue;

                // For each channel
                for (var chEntry : obj.getChannels().entrySet()) {
                    int chColor = curveColors[chIndex % 16];
                    boolean chSelected = selectedKeys.isChannelSelected(objName, chEntry.getKey());
                    if (!chSelected) {
                        chColor = replaceAlpha(chColor, 128);
                    }

                    // Should be pre-sorted, but we should check
                    Keyframe[] keyArray = chEntry.getValue().getKeyframes().toArray(new Keyframe[0]);

                    // Draw keyframes
                    for (int keyIdx = 0; keyIdx < keyArray.length; keyIdx++) {
                        Keyframe key = keyArray[keyIdx];
                        boolean cSelected = selectedKeys.isHandleSelected(objName, chEntry.getKey(), keyIdx, 0);
                        boolean lSelected = selectedKeys.isHandleSelected(objName, chEntry.getKey(), keyIdx, 1);
                        boolean rSelected = selectedKeys.isHandleSelected(objName, chEntry.getKey(), keyIdx, 2);

                        int color = 0xFF000000;
                        int selColor = ImGui.getColorU32(ImGuiCol.Text);

                        float keyX = msToPixelX((float) key.getCenter().x()) + graphX;
                        float keyY = valueToPixelY(key.getCenter().y()) + graphY;

                        drawList.addCircleFilled(keyX, keyY, 3f, cSelected ? selColor : color);

                        float handleAX = keyX + (float) key.getHandleA().x() * zoomFactorX;
                        float handleAY = keyY + (float) key.getHandleA().y() * zoomFactorY;

                        drawList.addCircle(handleAX, handleAY, 3f, lSelected ? selColor : color);

                        float handleBX = keyX + (float) key.getHandleB().x() * zoomFactorX;
                        float handleBY = keyY + (float) key.getHandleB().y() * zoomFactorY;

                        drawList.addCircle(handleBX, handleBY, 3f, rSelected ? selColor : color);

                        // TODO: Shouldn't this be defined in the theme somehow?
                        int lineColor = 0x808E79D1;
                        int lineColorSel = 0xFF93B4FF;

                        drawList.addLine(handleAX, handleAY, keyX, keyY, lSelected || cSelected ? lineColorSel : lineColor);
                        drawList.addLine(handleBX, handleBY, keyX, keyY, rSelected || cSelected ? lineColorSel : lineColor);

                        // Prioritize clicking on the selected channel unless the keyframe being clicked is already selected.
                        // In that case, don't let it be selected again so we can select overlapping keys.
                        if (mouseClicked && (selectedKeys.isChannelSelected(objName, chEntry.getKey()) || clickedOn == null)) {
                            KeyHandleReference handle0Ref = new KeyHandleReference(objName, chEntry.getKey(), keyIdx, 0);
                            KeyHandleReference handle1Ref = new KeyHandleReference(objName, chEntry.getKey(), keyIdx, 1);
                            KeyHandleReference handle2Ref = new KeyHandleReference(objName, chEntry.getKey(), keyIdx, 2);

                            if (keyHovered(keyX, keyY, mouseGlobalX, mouseGlobalY) && !selectedKeys.isHandleSelected(handle0Ref)) {
                                clickedOn = handle0Ref;
                            } else if (keyHovered(handleAX, handleAY, mouseGlobalX, mouseGlobalY) && !selectedKeys.isHandleSelected(handle1Ref)) {
                                clickedOn = handle1Ref;
                            } else if (keyHovered(handleBX, handleBY, mouseGlobalX, mouseGlobalY) && !selectedKeys.isHandleSelected(handle2Ref)) {
                                clickedOn = handle2Ref;
                            }
                        }

                        if (!hoveringAnyKey && (keyHovered(keyX, keyY, mouseGlobalX, mouseGlobalY, keyHoverRadius)
                                || keyHovered(handleAX, handleAY, mouseGlobalX, mouseGlobalY, keyHoverRadius)
                                || keyHovered(handleBX, handleBY, mouseGlobalX, mouseGlobalY, keyHoverRadius))) {
                            hoveringAnyKey = true;
                        }
                    }

                    // Lines
                    if (keyArray.length <= 1)
                        continue;

                    Arrays.sort(keyArray);

                    for (int i = 0; i < keyArray.length - 1; i++) {
                        Keyframe key = keyArray[i];
                        Keyframe next = keyArray[i + 1];

                        float keyX = msToPixelX((float) key.getCenter().x()) + graphX;
                        float keyY = valueToPixelY(key.getCenter().y()) + graphY;

                        float keyHandleX = keyX + (float) key.getHandleB().x() * zoomFactorX;
                        float keyHandleY = keyY + (float) key.getHandleB().y() * zoomFactorY;

                        float nextX = msToPixelX((float) next.getCenter().x()) + graphX;
                        float nextY = valueToPixelY(next.getCenter().y()) + graphY;

                        float nextHandleX = nextX + (float) next.getHandleA().x() * zoomFactorX;
                        float nextHandleY = nextY + (float) next.getHandleA().y() * zoomFactorY;

                        drawList.addBezierCubic(keyX, keyY, keyHandleX, keyHandleY, nextHandleX, nextHandleY, nextX, nextY,
                                chColor, chSelected ? 2 : 1);
                    }
                    chIndex++;
                }
            }

            ///  === SELECTION ===
            if (clickedOn != null) {
                if (!ImGui.getIO().getKeyCtrl()) {
                    selectedKeys.deselectAll();
                }
                selectedKeys.selectHandle(clickedOn);
            } else if (mouseClicked && !ImGui.getIO().getKeyCtrl() && !hoveringAnyKey) {
                selectedKeys.deselectAll();
            }

            /// Box selection start/stop
            if (!isBoxSelecting() && !isDragging() && !isScrubbing() && !hoveringAnyKey
                    && graphHovered && ImGui.isMouseDown(0)) {
                // Start box selecting
                boxSelectStart = ImGui.getMousePos();
                if (!ImGui.getIO().getKeyCtrl()) {
                    selectedKeys.deselectAll();
                }
            } else if (isBoxSelecting() && !ImGui.isMouseDown(0)) {
                // Stop box selecting
                boxSelectStart = null;
            }

            /// Currently box selecting
            if (boxSelectStart != null) {
                float boxMinX = Math.min(boxSelectStart.x, mouseGlobalX) - graphX;
                float boxMinY = Math.min(boxSelectStart.y, mouseGlobalY) - graphY;

                float boxMaxX = Math.max(boxSelectStart.x, mouseGlobalX) - graphX;
                float boxMaxY = Math.max(boxSelectStart.y, mouseGlobalY) - graphY;

                ImDrawList dl = ImGui.getForegroundDrawList();

                int rectColor = replaceAlpha(ImGui.getColorU32(ImGuiCol.HeaderActive), 25);
                dl.addRectFilled(boxSelectStart.x, boxSelectStart.y, mouseGlobalX, mouseGlobalY, rectColor);
                dl.addRect(boxSelectStart.x, boxSelectStart.y, mouseGlobalX, mouseGlobalY, ImGui.getColorU32(ImGuiCol.HeaderActive));

                for (String objName : objs) {
                    ReplayObject obj = scene.getObject(objName);
                    if (obj == null)
                        continue;

                    for (var chEntry : obj.getChannels().entrySet()) {
                        int keyIdx = 0;
                        for (Keyframe key : chEntry.getValue().getKeyframes()) {

                            float centerX = msToPixelX(key.getCenter().x);
                            float centerY = valueToPixelY(key.getCenter().y);

                            if (boxMinX < centerX && centerX < boxMaxX
                                    && boxMinY < centerY && centerY < boxMaxY) {
                                selectedKeys.selectHandle(objName, chEntry.getKey(), keyIdx, 0);
                            }

                            float handleAX = msToPixelX(key.getGlobalAX());
                            float handleAY = valueToPixelY(key.getGlobalAY());

                            if (boxMinX < handleAX && handleAX < boxMaxX
                                    && boxMinY < handleAY && handleAY < boxMaxY) {
                                selectedKeys.selectHandle(objName, chEntry.getKey(), keyIdx, 1);
                            }

                            float handleBX = msToPixelX(key.getGlobalBX());
                            float handleBY = valueToPixelY(key.getGlobalBY());

                            if (boxMinX < handleBX && handleBX < boxMaxX
                                    && boxMinY < handleBY && handleBY < boxMaxY) {
                                selectedKeys.selectHandle(objName, chEntry.getKey(), keyIdx, 2);
                            }

                            keyIdx++;
                        }
                    }
                }
            }


            /// ==== DRAGGING ===
            if (isDragging() && !ImGui.isMouseDragging(0)) {
                /// Stop dragging
                for (var ref : keyDragOffsets.keySet()) {
                    updatedObjects.add(ref.objectName());
                }
                keyDragOffsets.clear();
                smallestKeyDragOffset = null;

            } else if (!isDragging() && !isBoxSelecting() && ImGui.isMouseDragging(0)
                    && hoveringAnyKey && !isScrubbing()) {

                /// Start Dragging
                float mouseXMs = pixelXToMs(mouseGlobalX - graphX);
                double mouseYValue = pixelYToValue(mouseGlobalY - graphY);

                selectedKeys.forSelectedHandles(hRef -> {
                    Vector2d pos = hRef.get(scene.getObjects());
                    if (pos == null) return;
                    Vector2d offset = pos.sub(mouseXMs, mouseYValue, new Vector2d());
                    keyDragOffsets.put(hRef, offset);

                    if (isSmaller(offset, smallestKeyDragOffset)) {
                        smallestKeyDragOffset = new KeyOffsetPair(hRef, offset);
                        dragStartPos.set(pos);
                    }
                }, true);

            } else {
                /// Currently dragging
                boolean snap;
                if (snapKeyframes.get()) {
                    snap = !ImGui.getIO().getKeyCtrl();
                } else {
                    snap = ImGui.getIO().getKeyCtrl();
                }

                double mouseXMs = pixelXToMs(mouseGlobalX - graphX);
                double mouseYValue = pixelYToValue(mouseGlobalY - graphY);

                // Snapping
                if (snap && smallestKeyDragOffset != null) {
                    float thresholdX = 8f / zoomFactorX;
                    float thresholdY = 8f / zoomFactorY;

                    // The position of the closest dragged keyframe to the mouse.
                    double smallestOffsetX = smallestKeyDragOffset.offset().x();
                    double closestKeyX = mouseXMs + smallestOffsetX;

                    double smallestOffsetY = smallestKeyDragOffset.offset().y();
                    double closestKeyY = mouseYValue + smallestKeyDragOffset.offset().y();

                    double snapTargetX = Double.NaN;
                    double snapTargetXDist = Double.NaN;

                    double snapTargetY = Double.NaN;
                    double snapTargetYDist = Double.NaN;

                    for (String objName : objs) {
                        ReplayObject obj = scene.getObject(objName);
                        if (obj == null) continue;

                        for (var chEntry : obj.getChannels().entrySet()) {
                            int keyIdx = 0;
                            for (Keyframe key : chEntry.getValue().getKeyframes()) {
                                for (int i = 0; i < 3; i++) {

                                    // Don't attempt to lock to something we're also dragging
                                    // Don't like that we're allocating, but whatever
                                    if (keyDragOffsets.containsKey(new KeyHandleReference(objName, chEntry.getKey(), keyIdx, i)))
                                        continue;

                                    double handleX = key.getHandleX(i);
                                    double handleY = key.getHandleY(i);

                                    double distX = Math.abs(closestKeyX - handleX);
                                    double distY = Math.abs(closestKeyY - handleY);

                                    if (!Double.isFinite(snapTargetXDist) || distX < snapTargetXDist) {
                                        snapTargetXDist = distX;
                                        snapTargetX = handleX;
                                    }

                                    if (!Double.isFinite(snapTargetYDist) || distY < snapTargetYDist) {
                                        snapTargetYDist = distY;
                                        snapTargetY = handleY;
                                    }
                                }
                                keyIdx++;
                            }
                        }
                    }

                    if (Double.isFinite(snapTargetXDist) && snapTargetXDist < thresholdX) {
//                        System.out.println(snapTargetXDist);
                        mouseXMs = snapTargetX - smallestOffsetX;

                        float snapPixelX = msToPixelX(snapTargetX) + graphX;
                        drawList.addLine(snapPixelX, graphY, snapPixelX, graphY + graphHeight, 0xFF000000);
                    }

                    if (Double.isFinite(snapTargetYDist) && snapTargetYDist < thresholdY) {
                        mouseYValue = snapTargetY - smallestOffsetY;

                        float snapPixelY = valueToPixelY(snapTargetY) + graphY;
                        drawList.addLine(graphX, snapPixelY, graphX + gWidth, snapPixelY, 0xFF000000);
                    }
                }

                // Snapping
//                if (snap && smallestKeyDragOffset != null) {
//                    float thresholdX = 8f / zoomFactorX;
//                    float thresholdY = 8f / zoomFactorY;
//
//                    double smallestOffsetGlobalX = mouseXMs + smallestKeyDragOffset.offset().x();
//                    double smallestOffsetGlobalY = mouseYValue + smallestKeyDragOffset.offset().y();
//
//                    // Yeah we're allocating a lot here, but I don't give a shit
//                    final Vector2d snapTarget = new Vector2d(Double.NaN);
//                    double snapTargetX = Double.NaN;
//                    double snapTargetY = Double.NaN;
//
//                    Iterable<Map.Entry<String, ReplayObject>> selObjs = selectedObjects != null ? () -> scene.getObjects().entrySet()
//                            .stream()
//                            .filter(e -> selectedObjects.contains(e.getKey()))
//                            .iterator() : scene.getObjects().entrySet();
//
//
//                    DoublePredicate canSnapX = x -> Math.abs(x - smallestOffsetGlobalX) < thresholdX;
//                    DoublePredicate canSnapY = y -> Math.abs(y - smallestOffsetGlobalY) < thresholdY;
//
//                    KeyHandleReference smRef = smallestKeyDragOffset.ref(); // Just to shorten code
//                    Vector2d tmpVec = new Vector2d();
//                    for (var objName : objs) {
//                        ReplayObject obj = scene.getObject(objName);
//                        if (obj == null) continue;
//                        for (var chEntry : obj.getChannels().entrySet()) {
//                            int keyIdx = 0;
//                            for (var key : chEntry.getValue().getKeyframes()) {
//                                if (!smRef.keyRef().equals(objName, chEntry.getKey(), keyIdx)) {
//                                    if (canSnapX.test(key.getCenter().x())) {
//                                        snapTargetX = key.getCenter().x();
//                                    }
//                                    if (canSnapY.test(key.getCenter().y())) {
//                                        snapTargetY = key.getCenter().y();
//                                    }
//
//                                    key.getGlobalA(tmpVec);
//                                    if (canSnapX.test(tmpVec.x)) {
//                                        snapTargetX = tmpVec.x;
//                                    }
//                                    if (canSnapY.test(tmpVec.y)) {
//                                        snapTargetY = tmpVec.y;
//                                    }
//
//                                    key.getGlobalB(tmpVec);
//                                    if (canSnapX.test(tmpVec.x)) {
//                                        snapTargetX = tmpVec.x;
//                                    }
//                                    if (canSnapY.test(tmpVec.y)) {
//                                        snapTargetY = tmpVec.y;
//                                    }
//                                }
//                                keyIdx++;
//                            }
//                        }
//
//                        // Also test the original location
//                        if (canSnapX.test(dragStartPos.x)) {
//                            snapTargetX = dragStartPos.x;
//                        }
//
//                        if (canSnapY.test(dragStartPos.y)) {
//                            snapTargetY = dragStartPos.y;
//                        }
//                    }
//
//                    if (Double.isFinite(snapTargetX)) {
//                        mouseXMs = snapTargetX - smallestKeyDragOffset.offset().x();
//                    }
//                    if (Double.isFinite(snapTargetY)) {
//                        mouseYValue = snapTargetY - smallestKeyDragOffset.offset().y();
//                    }
//                }

                for (var entry : keyDragOffsets.entrySet()) {
                    KeyHandleReference hRef = entry.getKey();
                    Vector2dc offset = entry.getValue();
                    Keyframe key = hRef.keyRef().get(scene.getObjects());
                    if (key == null) continue;

                    double newGlobalTime = mouseXMs + offset.x();
                    double newGlobalValue = mouseYValue + offset.y();


                    switch (hRef.handleIndex()) {
                        case 0 -> {
                            key.setTime((int) newGlobalTime);
                            key.setValue(newGlobalValue);
                        } case 1 -> {
                            key.getHandleA().x = newGlobalTime - key.getCenter().x;
                            key.getHandleA().y = newGlobalValue - key.getCenter().y;
                        } case 2 -> {
                            key.getHandleB().x = newGlobalTime - key.getCenter().x;
                            key.getHandleB().y = newGlobalValue - key.getCenter().y;
                        }
                    }
                }
            }

            /// === PANNING ===
            if (ImGui.isMouseDragging(2)) {
                if (panStartPos == null && ImGui.isWindowHovered(ImGuiHoveredFlags.ChildWindows)) {
                    panStartPos = new Vector2d(offsetX, offsetY);
                }

                if (panStartPos != null) {
                    double dx = ImGui.getMouseDragDeltaX(2) / zoomFactorX;
                    double dy = ImGui.getMouseDragDeltaY(2) / zoomFactorY;

                    offsetX = panStartPos.x() - dx;
                    offsetY = panStartPos.y() - dy;
                }
            } else {
                panStartPos = null;
            }


        }
        ImGui.endChild();

        /// === HEADER ===
        if (!hasFlag(TimelineFlags.NO_HEADER, flags)) {
            ImGui.setCursorPosX(headerCursorX);
            ImGui.setCursorPosY(headerCursorY);
            header.drawHeader(headerHeight, zoomFactorX, (float) offsetX, scene.getLength(), playhead, graphHeight, flags);
        }

    }

    private float msToPixelX(double ms) {
        return (float) ((ms - offsetX) * zoomFactorX);
    }

    private float pixelXToMs(float pixel) {
        return (float) (pixel / zoomFactorX + offsetX);
    }

    private float valueToPixelY(double value) {
        return (float) ((value - offsetY) * zoomFactorY);
    }

    private double pixelYToValue(double pixel) {
        return pixel / zoomFactorY + offsetY;
    }

    private static boolean hasFlag(int flag, int flags) {
        return (flags & flag) == flag;
    }

    private static int replaceAlpha(int colorArgb, int newAlpha) {
        newAlpha &= 0xFF;
        return (colorArgb & 0x00FFFFFF) | (newAlpha << 24);
    }

    private static boolean keyHovered(float keyX, float keyY, float mouseX, float mouseY, float radius) {
        return (keyX - radius <= mouseX && mouseX <= keyX + radius)
                && (keyY - radius <= mouseY && mouseY < keyY + radius);
    }

    private static boolean keyHovered(float keyX, float keyY, float mouseX, float mouseY) {
        return keyHovered(keyX, keyY, mouseX, mouseY, 8f);
    }

    // Move externally to reduce code overhead
    private static boolean isSmaller(Vector2dc vec, @Nullable KeyOffsetPair smallest) {
        return smallest == null || (vec.lengthSquared() < smallest.offset().lengthSquared());
    }

    /**
     * Compute a bounding box of all the curves in a set of replay objects.
     * @param objects Objects to compute curves of.
     * @param dest1 Min corner of the bbox
     * @param dest2 Max corner of the bbox
     * @implNote Doesn't compute the <em>tightest</em> bounding box according to the beziers; only the bounds of all the handles.
     */
    private static void computeBoundingBox(Iterable<? extends ReplayObject> objects, Vector2d dest1, Vector2d dest2) {
        boolean hasInit = false;

        Vector2d tmpVec = new Vector2d();

        for (var obj : objects) {
            for (var ch : obj.getChannels().values()) {
                for (var key : ch.getKeyframes()) {
                    Vector2dc center = key.getCenter();

                    if (!hasInit) {
                        dest1.set(center);
                        dest2.set(center);
                        hasInit = true;
                    } else {
                        dest1.min(center);
                        dest2.max(center);
                    }

                    key.getGlobalA(tmpVec);
                    dest1.min(tmpVec);

                    key.getGlobalB(tmpVec);
                    dest2.max(tmpVec);
                }
            }
        }
    }

    private static void computeBoundingBox(KeySelectionSet selected, ReplayScene scene, Vector2d dest1, Vector2d dest2) {
        Vector2d tmpVec = new Vector2d(Double.NaN);
        selected.forSelectedHandles(ref -> {
            boolean hasInit = tmpVec.isFinite();
            boolean found = ref.get(scene.getObjects(), tmpVec);

            if (!found) return;

            if (hasInit) {
                dest1.min(tmpVec);
                dest2.max(tmpVec);
            } else {
                dest1.set(tmpVec);
                dest2.set(tmpVec);
            }
        });
    }
}
