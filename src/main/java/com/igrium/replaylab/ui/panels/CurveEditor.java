package com.igrium.replaylab.ui.panels;

import com.igrium.replaylab.config.Keybinds;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.editor.KeySelectionSet;
import com.igrium.replaylab.editor.KeySelectionSet.ChannelReference;
import com.igrium.replaylab.editor.KeySelectionSet.KeyframeReference;
import com.igrium.replaylab.editor.KeySelectionSet.KeyHandleReference;
import com.igrium.replaylab.operator.object.CommitObjectUpdateOperator;
import com.igrium.replaylab.operator.ReplayOperator;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.anim.ChannelUtils;
import com.igrium.replaylab.anim.KeyChannel;
import com.igrium.replaylab.anim.Keyframe;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.ui.ReplayLabIcons;
import com.igrium.replaylab.ui.subpanels.ChannelListFlags;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import com.igrium.replaylab.ui.subpanels.TimelineHeader;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.*;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import lombok.Getter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.*;

import java.lang.Math;
import java.util.*;
import java.util.stream.Collectors;

public class CurveEditor extends KeyframePanel {

    private record ChannelExtents(double min, double max) {};

    /** Pixel radius within which a bezier handle is considered "collapsed" onto its keyframe. */
    private static final float HANDLE_SNAP_THRESHOLD = 12f;

    public CurveEditor(Identifier id) {
        super(id);
        channelListFlags |= ChannelListFlags.SHOW_COLORS | ChannelListFlags.HIGHLIGHT_SELECTION;
        setSeparateChannelScrolling(true);
    }

    private final Map<ChannelReference, ChannelExtents> normalizationCache = new HashMap<>();
    private final ImBoolean normalized = new ImBoolean(false);

    private boolean wasNormalized;

    public boolean isNormalized() {
        return normalized.get();
    }

    public void setNormalized(boolean normalized) {
        this.normalized.set(normalized);
    }

    /**
     * All the handles that have had an updated <em>committed</em> this frame.
     * Does not include keyframes being dragged.
     */
    @Getter
    private final Set<KeyHandleReference> droppedHandles = new HashSet<>();

    /**
     * All the handles that have been updated this frame. Could be in the middle of dragging or an option has been changed.
     */
    @Getter
    private final Set<KeyHandleReference> updatedHandles = new HashSet<>();

    /**
     * The amount of ms / value units offset from the mouse that each key being dragged has.
     */
    private final Map<KeyHandleReference, Vector2dc> keyDragOffsets = new HashMap<>();



    private record KeyOffsetPair(KeyHandleReference ref, Vector2dc offset) { }
    /**
     * The key drag offset that's closest to the mouse
     */
    private @Nullable KeyOffsetPair smallestKeyDragOffset;

    /**
     * The global location of the smallest key drag offset at the time of drag start
     */
    private final Vector2d dragStartPos = new Vector2d();

    // Not null if currently panning
    private @Nullable Vector2d panStartPos;

    private final ImBoolean snapKeyframes = new ImBoolean();

    private boolean doneInitialFit = false;

    private boolean wantsFit;

    // Tracks whether the mouse was already dragging last frame, so we only
    // start a new drag on the exact frame dragging begins (matching DopeSheetNew).
    // Otherwise a drag started outside this panel and moved in while still held
    // would be picked up here and start moving the selected keyframe.
    private boolean mouseWasDragging;


    /**
     * The global pixel position of a selection box start position
     */
    private @Nullable ImVec2 boxSelectStart;

    public boolean isDragging() {
        return !keyDragOffsets.isEmpty();
    }

    public boolean isBoxSelecting() {
        return boxSelectStart != null;
    }

    @Override
    public void onAppliedOperator(ReplayOperator op, EditorState editorState) {
        updateNormalizationCache(editorState.getScene());
    }

    @Override
    protected void drawControlButtons(EditorState editorState) {
        ReplayLabControls.toggleButton(ReplayLabIcons.ICON_ARROW_POINTER, "selectedOnly", getSelectedOnlyRef(),
                "gui.replaylab.selected_only");
        ImGui.sameLine();

        ReplayLabControls.toggleButton(ReplayLabIcons.ICON_MAGNET, "snapKeyframes", snapKeyframes,
                "gui.replaylab.tooltip_snap");
        ImGui.sameLine();
        wantsFit = ReplayLabControls.iconButton(ReplayLabIcons.ICON_RESIZE_FULL_ALT, "wantsFit",
                "gui.replaylab.tooltip_fit");

        wasNormalized = isNormalized();

        ImGui.sameLine();
        ReplayLabControls.toggleButton(ReplayLabIcons.ICON_ARROWS_V, "normalize", normalized,
                "gui.replaylab.tooltip_normalize");
    }

    @Override
    protected void drawInternal(EditorState editorState, Map<String, ReplayObject> objects) {
        drawAndManageHandles(editorState, objects,0);

        if (isScrubbing() || stoppedScrubbing()) {
            editorState.scrub(stoppedScrubbing());
        }

    }

    public void drawAndManageHandles(EditorState editorState, Map<String, ReplayObject> objs, int flags) {
        drawCurveEditor(editorState, objs, editorState.getKeySelection(),
                editorState.getPlayheadRef(), flags);

        // Jump forward if playing and off screen
        double endMs = getOffsetX() + getHeader().getWidthMs();
        if (editorState.isPlaying() && (editorState.getPlayhead() > endMs || editorState.getPlayhead() < getOffsetX())) {
            setOffsetX(editorState.getPlayhead());
        }

        // Recompute handles
        getUpdatedHandles().stream()
                .map(ref -> ref.keyRef().channelRef())
                .distinct().forEach(chRef -> {
                    KeyChannel ch = chRef.get(editorState.getScene().getObjects());
                    if (ch == null) return;

                    var dragging = keyDragOffsets.keySet().stream()
                            .filter(hRef -> hRef.keyRef().channelRef().equals(chRef))
                            .map(hRef -> new ChannelUtils.LocalHandleRef(hRef.keyIndex(), hRef.handleIndex()))
                            .collect(Collectors.toSet());

                    ChannelUtils.computeHandles(ch, dragging);
                });

        if (!getDroppedHandles().isEmpty()) {
            var updatedObjects = getDroppedHandles().stream()
                    .map(KeyHandleReference::objectName)
                    .distinct()
                    .toList();

            editorState.applyOperator(new CommitObjectUpdateOperator(updatedObjects));
            editorState.applyToGame();

            updateNormalizationCache(editorState.getScene());
        } else if (isDragging()) {
            // Always apply to game if we're dragging
            editorState.applyToGame();
        }

    }


    /**
     * Draw the curve editor.
     *
     * @param editor          The editor state
     * @param selectedKeys    All keyframe handles which are currently selected.
     *                        Updated as the user selects/deselects keyframes.
     * @param playhead        Current playhead position. Updated as the player scrubs.
     * @param flags           Render flags.
     */
    public void drawCurveEditor(EditorState editor, Map<String, ReplayObject> objs,
                                KeySelectionSet selectedKeys, @Nullable ImInt playhead, int flags) {

        ReplayScene scene = editor.getScene();
        droppedHandles.clear();
        updatedHandles.clear();
        keyTimes.clear();

        float majorIntervalX = TimelineHeader.computeMajorInterval(getZoomFactorX());
        float minorIntervalX = majorIntervalX / 2;

        float majorIntervalY = TimelineHeader.computeMajorInterval(getZoomFactorY());
        float minorIntervalY = majorIntervalY / 2;

        /// === BUTTONS ===

        wantsFit |= ImGui.shortcut(Keybinds.frameSelected());

        float graphHeight = ImGui.getContentRegionAvailY();

        float mouseGlobalX = ImGui.getMousePosX();
        float mouseGlobalY = ImGui.getMousePosY();

        // Vertical auto-fit
        if (!wasNormalized && isNormalized()) {
            setOffsetY(1.2);
            setZoomFactorY(graphHeight / 2.4f);
            updateNormalizationCache(scene);
        } else if (wasNormalized && !isNormalized()) {
            wantsFit = true;
        }

        /// === GRAPH ===

        if (ImGui.beginChild("keygraph", ImGui.getContentRegionAvailX(), graphHeight, false)) {
            float graphX = ImGui.getCursorScreenPosX();
            float graphY = ImGui.getCursorScreenPosY();

            float gWidth = ImGui.getContentRegionAvailX();
            float gHeight = ImGui.getContentRegionAvailY();



            /// === FITTING ===
            // Appearing window breaks fit
            if ((wantsFit || !doneInitialFit) && !objs.isEmpty() && !ImGui.isWindowAppearing()) {
                Vector2d boundsMin = new Vector2d();
                Vector2d boundsMax = new Vector2d();

                Iterable<KeyHandleReference> iter = selectedKeys.isEmpty()
                        ? KeySelectionSet.iterateAllHandles(objs)
                        : selectedKeys.getSelectedHandles();

                computeDisplayBoundingBox(objs, iter, boundsMin, boundsMax);

                if (boundsMin.x != boundsMax.x)
                    setZoomFactorX((float) (gWidth / (boundsMax.x - boundsMin.x)));

                if (boundsMin.y != boundsMax.y)
                    setZoomFactorY((float) (gHeight / (boundsMax.y - boundsMin.y)));

                setOffsetX(boundsMin.x);
                setOffsetY(boundsMax.y);

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
                            setZoomFactorX(getZoomFactorX() * factor, mouseXMs);
                        }
                        if (!ImGui.getIO().getKeyCtrl()) {
                            setZoomFactorY(getZoomFactorY() * factor, mouseYValue);
                        }

                    }
                }
            }

            ImDrawList drawList = ImGui.getWindowDrawList();

            /// === BACKGROUND ===
            drawList.addRectFilled(graphX, graphY, graphX + gWidth, graphY + gHeight,
                    ImGui.getColorU32(ImGuiCol.FrameBg));

            // Amount of milliseconds the graph is wide
            float widthMs = gWidth / getZoomFactorX();
            float startTick = (float) Math.floor(getOffsetX() / majorIntervalX) * majorIntervalX;
            float endTick = (float) Math.ceil((getOffsetX() + widthMs) / majorIntervalX) * majorIntervalX;
            int colMajor = replaceAlpha(ImGui.getColorU32(ImGuiCol.Text), 48);
            int colMinor = replaceAlpha(colMajor, 16);

            // X intervals
            // Tolerance for floating-point comparisons when checking major-interval alignment
            float epsilon = minorIntervalX * 0.01f;
            for (float ms = startTick; ms <= endTick + epsilon; ms += minorIntervalX) {
                float xPos = msToPixelX(ms) + graphX;
                float remainder = ms % majorIntervalX;
                if (remainder < 0) {
                    remainder += majorIntervalX;
                }
                boolean isMajorLine = remainder < epsilon || (majorIntervalX - remainder) < epsilon;
                int color = isMajorLine ? colMajor : colMinor;
                drawList.addLine(xPos, graphY, xPos, graphY + gHeight, color);
            }

            // Amount of units the graph is tall
            float heightUnits = gHeight / getZoomFactorY();

            double startValue = Math.floor((getOffsetY() - heightUnits) / majorIntervalY) * majorIntervalY;
            double endValue = Math.ceil(getOffsetY() / majorIntervalY) * majorIntervalY;

            // Y intervals
            for (double value = startValue; value <= endValue; value += minorIntervalY) {
                float yPos = valueToPixelY(value) + graphY;
                int color = value % majorIntervalY == 0 ? colMajor : colMinor;
                drawList.addLine(graphX, yPos, graphX + gWidth, yPos, color);
            }

            /// === Keyframes ===
            KeyHandleReference clickedOn = null;
            KeyHandleReference rightClickedOn = null;

            boolean mouseClicked = ImGui.isItemClicked();
            boolean rightClicked = ImGui.isItemClicked(ImGuiMouseButton.Right);

            // If we're overing any key, with some leeway for drag threshold
            boolean hoveringAnyKey = false;
            float keyHoverRadius = 12f + ImGui.getIO().getMouseDragThreshold() * 2;

            for (var objEntry : objs.entrySet()) {
                String objName = objEntry.getKey();
                ReplayObject obj = objEntry.getValue();
                if (obj == null)
                    continue;

                // For each channel
                for (var chEntry : obj.getChannels().entrySet()) {
                    if (chEntry.getValue().isHidden())
                        continue;

                    ChannelExtents extents = getExtents(objName, chEntry.getKey());

                    int chColor = obj.getChannelColor(chEntry.getKey());
                    boolean chSelected = selectedKeys.isChannelSelected(objName, chEntry.getKey());
                    if (!chSelected) {
                        chColor = replaceAlpha(chColor, 128);
                    }

                    // Should be pre-sorted, but we should check
                    Keyframe[] keyArray = chEntry.getValue().getKeyframes().toArray(new Keyframe[0]);

                    // Draw keyframes
                    if (!chEntry.getValue().isLocked()) {
                        for (int keyIdx = 0; keyIdx < keyArray.length; keyIdx++) {
                            Keyframe key = keyArray[keyIdx];
                            keyTimes.add(key.getTimeInt());

                            boolean cSelected = selectedKeys.isHandleSelected(objName, chEntry.getKey(), keyIdx, 0);
                            boolean lSelected = selectedKeys.isHandleSelected(objName, chEntry.getKey(), keyIdx, 1);
                            boolean rSelected = selectedKeys.isHandleSelected(objName, chEntry.getKey(), keyIdx, 2);

                            int color = 0xFF000000;
                            int handleEndColor = 0x88000000;
                            int selColor = ImGui.getColorU32(ImGuiCol.Text);

                            float keyX = msToPixelX((float) key.getCenter().x()) + graphX;
                            double display = rawToDisplay(key.getCenter().y, extents);
                            float keyY = valueToPixelY(display) + graphY;

                            drawList.addCircleFilled(keyX, keyY, 3f, cSelected ? selColor : color);

                            float handleAX = msToPixelX(key.getGlobalAX()) + graphX;
                            double displayA = rawToDisplay(key.getGlobalAY(), extents);
                            float handleAY = valueToPixelY(displayA) + graphY;

                            float handleBX = msToPixelX(key.getGlobalBX()) + graphX;
                            double displayB = rawToDisplay(key.getGlobalBY(), extents);
                            float handleBY = valueToPixelY(displayB) + graphY;

                            if (chSelected) {
                                int lColor = getHandleColor(key.getHandleAType());
                                if (!(lSelected || cSelected)) {
                                    lColor = replaceAlpha(lColor, 63);
                                }

                                int rColor = getHandleColor(key.getHandleBType());
                                if (!(rSelected || cSelected)) {
                                    rColor = replaceAlpha(rColor, 63);
                                }


                                drawList.addCircle(handleAX, handleAY, 3f, lSelected ? selColor : handleEndColor);
                                drawList.addCircle(handleBX, handleBY, 3f, rSelected ? selColor : handleEndColor);

                                drawList.addLine(handleAX, handleAY, keyX, keyY, lColor);
                                drawList.addLine(handleBX, handleBY, keyX, keyY, rColor);
                            }

                            boolean isHandleAClose = Math.hypot(handleAX - keyX, handleAY - keyY) <= HANDLE_SNAP_THRESHOLD;
                            boolean isHandleBClose = Math.hypot(handleBX - keyX, handleBY - keyY) <= HANDLE_SNAP_THRESHOLD;

                            // Prioritize clicking on the selected channel unless the keyframe being clicked is already selected.
                            if (mouseClicked && (chSelected || clickedOn == null)) {
                                KeyframeReference keyRef = new KeyframeReference(objName, chEntry.getKey(), keyIdx);

                                KeyHandleReference handle0Ref = new KeyHandleReference(keyRef, 0);
                                KeyHandleReference handle1Ref = new KeyHandleReference(keyRef, 1);
                                KeyHandleReference handle2Ref = new KeyHandleReference(keyRef, 2);

                                if (keyHovered(keyX, keyY, mouseGlobalX, mouseGlobalY)) {
                                    clickedOn = handle0Ref;
                                } else if (keyHovered(handleAX, handleAY, mouseGlobalX, mouseGlobalY)) {
                                    clickedOn = isHandleAClose ? handle0Ref : handle1Ref;
                                } else if (keyHovered(handleBX, handleBY, mouseGlobalX, mouseGlobalY)) {
                                    clickedOn = isHandleBClose ? handle0Ref : handle2Ref;
                                }
                            }

                            // On the other hand, right-clicking should always prioritize the selected channel
                            if (rightClicked && (chSelected || rightClickedOn == null)) {
                                KeyframeReference keyRef = new KeyframeReference(objName, chEntry.getKey(), keyIdx);

                                if (keyHovered(keyX, keyY, mouseGlobalX, mouseGlobalY)) {
                                    rightClickedOn = new KeyHandleReference(keyRef, 0);
                                } else if (keyHovered(handleAX, handleAY, mouseGlobalX, mouseGlobalY)) {
                                    rightClickedOn = new KeyHandleReference(keyRef, isHandleAClose ? 0 : 1);
                                } else if (keyHovered(handleBX, handleBY, mouseGlobalX, mouseGlobalY)) {
                                    rightClickedOn = new KeyHandleReference(keyRef, isHandleBClose ? 0 : 2);
                                }
                            }

                            if (!hoveringAnyKey && (keyHovered(keyX, keyY, mouseGlobalX, mouseGlobalY, keyHoverRadius)
                                    || keyHovered(handleAX, handleAY, mouseGlobalX, mouseGlobalY, keyHoverRadius)
                                    || keyHovered(handleBX, handleBY, mouseGlobalX, mouseGlobalY, keyHoverRadius))) {
                                hoveringAnyKey = true;
                            }
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
                        double displayKey = rawToDisplay(key.getCenter().y(), extents);
                        float keyY = valueToPixelY(displayKey) + graphY;

                        float keyHandleX = msToPixelX(key.getGlobalBX()) + graphX;
                        double displayKeyHandle = rawToDisplay(key.getGlobalBY(), extents);
                        float keyHandleY = valueToPixelY(displayKeyHandle) + graphY;

                        float nextX = msToPixelX(next.getCenter().x()) + graphX;
                        double displayNext = rawToDisplay(next.getCenter().y(), extents);
                        float nextY =  valueToPixelY(displayNext) + graphY;

                        float nextHandleX = msToPixelX(next.getGlobalAX()) + graphX;
                        double displayNextHandle = rawToDisplay(next.getGlobalAY(), extents);
                        float nextHandleY = valueToPixelY(displayNextHandle) + graphY;

                        float thickness = chSelected ? 2 : 1;
                        switch(key.getInterpolationMode()) {
                            case BEZIER -> drawList.addBezierCubic(keyX, keyY, keyHandleX, keyHandleY,
                                    nextHandleX, nextHandleY, nextX, nextY, chColor, thickness);
                            case LINEAR -> drawList.addLine(keyX, keyY, nextX, nextY, chColor, thickness);
                            case CONSTANT -> {
                                drawList.addLine(keyX, keyY, nextX, keyY, chColor, thickness);
                                drawList.addLine(nextX, keyY, nextX, nextY, chColor, thickness);
                            }
                        }

                    }

                    // Continue lines to edge of screen
                    float startX = msToPixelX(keyArray[0].getCenter().x()) + graphX;
                    double displayStart = rawToDisplay(keyArray[0].getCenter().y(), extents);
                    float startY = valueToPixelY(displayStart) + graphY;
                    drawList.addLine(graphX, startY, startX, startY, chColor, chSelected ? 2 : 1);

                    Keyframe endKey = keyArray[keyArray.length - 1];
                    float endX = msToPixelX(endKey.getCenter().x()) + graphX;
                    double displayEnd = rawToDisplay(endKey.getCenter().y(), extents);
                    float endY = valueToPixelY(displayEnd) + graphY;
                    drawList.addLine(endX, endY, graphX + gWidth, endY, chColor, chSelected ? 2 : 1);
                }
            }

            /// === OUT-OF-BOUNDS GRAYOUT
            {
                float pixelIn = msToPixelX(0) + graphX;
                float pixelOut = msToPixelX(scene.getLength()) + graphX;

                if (pixelIn > graphX) {
                    drawList.addLine(pixelIn, graphY, pixelIn, graphY + graphHeight, ImGui.getColorU32(ImGuiCol.Separator));
                    drawList.addRectFilled(graphX, graphY, pixelIn, graphY + graphHeight, ImGui.getColorU32(ImGuiCol.ModalWindowDimBg));
                }

                if (pixelOut < graphX + gWidth) {
                    drawList.addLine(pixelOut, graphY, pixelOut, graphY + graphHeight, ImGui.getColorU32(ImGuiCol.Separator));
                    drawList.addRectFilled(pixelOut, graphY, graphX + gWidth, graphY + graphHeight, ImGui.getColorU32(ImGuiCol.ModalWindowDimBg));
                }

                if (isNormalized()) {
                    // Resolve the actual U32 color integer from the theme style first
                    int modalDimColor = ImGui.getColorU32(ImGuiCol.ModalWindowDimBg);
                    int separatorColor = ImGui.getColorU32(ImGuiCol.Separator);

                    int outOfNormalColor = ColorHelper.withAlpha(
                            (int) (ColorHelper.getAlpha(modalDimColor) *.75f), modalDimColor);

                    // Normalization bounds are -1 and 1
                    float pixelNeg1 = valueToPixelY(-1) + graphY;
                    float pixelPos1 = valueToPixelY(1) + graphY;

                    // Gray out the area ABOVE +1 (from graphY down to the boundary line)
                    if (pixelPos1 > graphY) {
                        drawList.addLine(pixelIn, pixelPos1, pixelOut, pixelPos1, separatorColor);
                        drawList.addRectFilled(pixelIn, graphY, pixelOut, pixelPos1, outOfNormalColor);
                    }

                    // Gray out the area BELOW -1 (from the boundary line down to graphY + graphHeight)
                    if (pixelNeg1 < graphY + graphHeight) {
                        drawList.addLine(pixelIn, pixelNeg1, pixelOut, pixelNeg1, separatorColor);
                        drawList.addRectFilled(pixelIn, pixelNeg1, pixelOut, graphY + graphHeight, outOfNormalColor);
                    }
                }
            }

            /// === SELECTION ===
            boolean leftOrRightClicked = clickedOn != null || rightClickedOn != null;
            boolean ctrlHeld = ImGui.getIO().getKeyCtrl();

            if (leftOrRightClicked) {
                var clickTarget = clickedOn != null ? clickedOn : rightClickedOn;
                boolean alreadySelected = selectedKeys.isHandleSelected(clickTarget);

                if (!alreadySelected) {
                    if (!ctrlHeld) selectedKeys.deselectAll();
                    selectedKeys.selectHandle(clickTarget);
                } else if (clickedOn != null && ctrlHeld) {
                    // Left-clicking on selected item w/ control deselects it
                    selectedKeys.deselectHandle(clickTarget);
                }
            } else if (mouseClicked && !ctrlHeld && !hoveringAnyKey) {
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

                for (var objEntry : objs.entrySet()) {
                    String objName = objEntry.getKey();
                    ReplayObject obj = objEntry.getValue();
                    if (obj == null)
                        continue;

                    for (var chEntry : obj.getChannels().entrySet()) {
                        if (chEntry.getValue().isHidden() || chEntry.getValue().isLocked())
                            continue;
                        ChannelExtents extents = getExtents(objName, chEntry.getKey());
                        int keyIdx = 0;
                        for (Keyframe key : chEntry.getValue().getKeyframes()) {


                            float centerX = msToPixelX(key.getCenter().x);
                            double display = rawToDisplay(key.getCenter().y, extents);
                            float centerY = valueToPixelY(display);

                            if (boxMinX < centerX && centerX < boxMaxX
                                    && boxMinY < centerY && centerY < boxMaxY) {
                                selectedKeys.selectHandle(objName, chEntry.getKey(), keyIdx, 0);
                            }

                            float handleAX = msToPixelX(key.getGlobalAX());
                            double displayA = rawToDisplay(key.getGlobalAY(), extents);
                            float handleAY = valueToPixelY(displayA);

                            if (boxMinX < handleAX && handleAX < boxMaxX
                                    && boxMinY < handleAY && handleAY < boxMaxY) {
                                selectedKeys.selectHandle(objName, chEntry.getKey(), keyIdx, 1);
                            }

                            float handleBX = msToPixelX(key.getGlobalBX());
                            double displayB = rawToDisplay(key.getGlobalBY(), extents);
                            float handleBY = valueToPixelY(displayB);

                            if (boxMinX < handleBX && handleBX < boxMaxX
                                    && boxMinY < handleBY && handleBY < boxMaxY) {
                                selectedKeys.selectHandle(objName, chEntry.getKey(), keyIdx, 2);
                            }

                            keyIdx++;
                        }
                    }
                }
            }

            /// === RIGHT CLICK ===
            if (rightClickedOn != null) {
                ImGui.openPopup("contextMenu");
            }

            if (ImGui.beginPopup("contextMenu")) {
                KeyframePanel.keyContextMenu(editor, selectedKeys.effectiveSelectedHandles());
                ImGui.endPopup();
            }

            /// ==== DRAGGING ===
            if (isDragging() && !ImGui.isMouseDragging(0)) {
                /// Stop dragging
                droppedHandles.addAll(keyDragOffsets.keySet());
                keyDragOffsets.clear();
                smallestKeyDragOffset = null;

            } else if (!isDragging() && !isBoxSelecting() && ImGui.isMouseDragging(0) && !mouseWasDragging
                    && hoveringAnyKey && !isScrubbing()) {

                /// Start Dragging
                float mouseXMs = pixelXToMs(mouseGlobalX - graphX);
                // mouseYValue acts as the mouse position in Display Space
                double mouseYValue = pixelYToValue(mouseGlobalY - graphY);

                selectedKeys.forSelectedHandles(hRef -> {
                    KeyChannel ch = hRef.keyRef().channelRef().get(scene.getObjects());
                    if (ch != null && (ch.isLocked() || ch.isHidden()))
                        return;

                    Vector2d pos = hRef.get(scene.getObjects());
                    if (pos == null) return;

                    ChannelExtents extents = getExtents(hRef.objectName(), hRef.channelName());
                    double displayY = rawToDisplay(pos.y, extents);

                    Vector2d offset = new Vector2d(pos.x - mouseXMs, displayY - mouseYValue);
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
                // mouseYValue acts as the mouse position in Display Space
                double mouseYValue = pixelYToValue(mouseGlobalY - graphY);

                // Snapping
                if (snap && smallestKeyDragOffset != null) {
                    float thresholdX = 8f / getZoomFactorX();
                    float thresholdY = 8f / getZoomFactorY();

                    // The position of the closest dragged keyframe to the mouse.
                    double smallestOffsetX = smallestKeyDragOffset.offset().x();
                    double closestKeyX = mouseXMs + smallestOffsetX;

                    double smallestOffsetY = smallestKeyDragOffset.offset().y();
                    double closestKeyY = mouseYValue + smallestOffsetY;

                    double snapTargetX = Double.NaN;
                    double snapTargetXDist = Double.NaN;

                    double snapTargetY = Double.NaN;
                    double snapTargetYDist = Double.NaN;

                    for (var objEntry : objs.entrySet()) {
                        String objName =  objEntry.getKey();
                        ReplayObject obj = objEntry.getValue();
                        if (obj == null) continue;

                        for (var chEntry : obj.getChannels().entrySet()) {
                            int keyIdx = 0;
                            ChannelExtents snapExtents = getExtents(objName, chEntry.getKey());

                            for (Keyframe key : chEntry.getValue().getKeyframes()) {
                                for (int i = 0; i < 3; i++) {

                                    // Don't attempt to lock to something we're also dragging
                                    if (keyDragOffsets.containsKey(new KeyHandleReference(objName, chEntry.getKey(), keyIdx, i)))
                                        continue;

                                    double handleX = key.getHandleX(i);

                                    double handleY = rawToDisplay(key.getHandleY(i), snapExtents);

                                    double distX = Math.abs(closestKeyX - handleX);
                                    double distY = Math.abs(closestKeyY - handleY);

                                    if (!Double.isFinite(snapTargetXDist) || distX < snapTargetXDist) {
                                        snapTargetXDist = distX;
                                        snapTargetX = handleX;
                                    }

                                    if (!Double.isFinite(snapTargetYDist) || distY < snapTargetYDist) {
                                        snapTargetYDist = distY;
                                        snapTargetY = handleY; // Stores target in Display Space
                                    }
                                }
                                keyIdx++;
                            }
                        }
                    }

                    if (Double.isFinite(snapTargetXDist) && snapTargetXDist < thresholdX) {
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

                for (var entry : keyDragOffsets.entrySet()) {
                    KeyHandleReference hRef = entry.getKey();
                    Vector2dc offset = entry.getValue();
                    Keyframe key = hRef.keyRef().get(scene.getObjects());
                    if (key == null) continue;

                    ChannelExtents extents = getExtents(hRef.objectName(), hRef.channelName());

                    double newGlobalTime = mouseXMs + offset.x();
                    double newGlobalDisplayY = mouseYValue + offset.y();

                    double newGlobalValue = displayToRaw(newGlobalDisplayY, extents);

                    switch (hRef.handleIndex()) {
                        case 0 -> {
                            key.setTime((int) newGlobalTime);
                            key.setValue(newGlobalValue);
                        }
                        case 1 -> {
                            key.getHandleA().x = newGlobalTime - key.getCenter().x;
                            key.getHandleA().y = newGlobalValue - key.getCenter().y;
                        }
                        case 2 -> {
                            key.getHandleB().x = newGlobalTime - key.getCenter().x;
                            key.getHandleB().y = newGlobalValue - key.getCenter().y;
                        }
                    }
                }

                updatedHandles.addAll(keyDragOffsets.keySet());
            }

            mouseWasDragging = ImGui.isMouseDragging(0);

            /// === PANNING ===
            if (ImGui.isMouseDragging(2)) {
                if (panStartPos == null && ImGui.isWindowHovered(ImGuiHoveredFlags.ChildWindows)) {
                    panStartPos = new Vector2d(getOffsetX(), getOffsetY());
                }

                if (panStartPos != null) {
                    double dx = ImGui.getMouseDragDeltaX(2) / getZoomFactorX();
                    double dy = ImGui.getMouseDragDeltaY(2) / getZoomFactorY();

                    setOffsetX(panStartPos.x() - dx);
                    setOffsetY(panStartPos.y() + dy);
                }
            } else {
                panStartPos = null;
            }


        }
        ImGui.endChild();
        wantsFit = false;
    }

    /**
     * Re-calculate the bounds for each channel to use when normalization is enabled.
     * @param scene The active replay scene
     */
    public void updateNormalizationCache(ReplayScene scene) {
        normalizationCache.clear();

        for (var objEntry : scene.getObjects().entrySet()) {
            for (var chEntry : objEntry.getValue().getChannels().entrySet()) {
                if (chEntry.getValue().isEmpty()) continue;
                normalizationCache.put(new ChannelReference(objEntry.getKey(), chEntry.getKey()),
                        new ChannelExtents(chEntry.getValue().getMinHandle(), chEntry.getValue().getMaxHandle()));
            }
        }
    }

    private @Nullable ChannelExtents getExtents(String objectName, String channelName) {
        return getExtents(new ChannelReference(objectName, channelName));
    }

    private @Nullable ChannelExtents getExtents(ChannelReference channelRef) {
        return isNormalized() ? normalizationCache.get(channelRef) : null;
    }

    private float msToPixelX(double ms) {
        return (float) ((ms - getOffsetX()) * getZoomFactorX());
    }

    private float pixelXToMs(float pixel) {
        return (float) (pixel / getZoomFactorX() + getOffsetX());
    }

    private float valueToPixelY(double value) {
        return (float) ((getOffsetY() - value) * getZoomFactorY());
    }

    private double pixelYToValue(double pixel) {
        return getOffsetY() - pixel / getZoomFactorY();
    }

    /**
     * Convert a raw curve value to a "display" value, mapped from zero to one.
     * @param raw The original value in the curve
     * @param extents The curves computed extents. Null if it does not have any.
     * @return The display-space value
     */
    private double rawToDisplay(double raw, @Nullable ChannelExtents extents) {
        if (extents == null) return raw;
        if (Math.abs(extents.max - extents.min) < .001) return 0;
        return ((raw - extents.min) / (extents.max - extents.min)) * 2 - 1;
    }

    private double displayToRaw(double display, @Nullable ChannelExtents extents) {
        if (extents == null) return display;
        return ((display + 1) / 2) * (extents.max - extents.min) + extents.min;
    }

    private void computeDisplayBoundingBox(Map<String, ReplayObject> objs, Iterable<? extends KeyHandleReference> selected,
                                           Vector2d dest1, Vector2d dest2) {
        boolean foundAny = false;

        Vector2d vec = new Vector2d();

        for (var handle : selected) {
            var extents = getExtents(handle.keyRef().channelRef());
            if (!handle.get(objs, vec)) continue;

            vec.y = rawToDisplay(vec.y, extents);

            if (foundAny) {
                dest1.min(vec);
                dest2.max(vec);
            } else {
                dest1.set(vec);
                dest2.set(vec);
                foundAny = true;
            }
        }
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

    private static int getHandleColor(Keyframe.HandleType type) {
        // Colors stolen from Blender
        return switch (type) {
            case FREE -> 0xFFEBB400;
            case ALIGNED -> 0xFF449434;
            case VECTOR -> 0xFF353596;
            case AUTO -> 0xFFba1c0b;
            case AUTO_CLAMPED -> 0xFFD27A8F;
        };
    }

}