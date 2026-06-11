package com.igrium.replaylab.ui.panels;

import com.igrium.replaylab.config.Keybinds;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.editor.KeySelectionSet;
import com.igrium.replaylab.editor.KeySelectionSet.ChannelReference;
import com.igrium.replaylab.editor.KeySelectionSet.KeyframeReference;
import com.igrium.replaylab.editor.KeySelectionSet.KeyHandleReference;
import com.igrium.replaylab.operator.CommitObjectUpdateOperator;
import com.igrium.replaylab.operator.RemoveKeyframesOperator;
import com.igrium.replaylab.operator.ReplayOperator;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.key.ChannelUtils;
import com.igrium.replaylab.scene.key.KeyChannel;
import com.igrium.replaylab.scene.key.Keyframe;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.ui.ReplayLabIcons;
import com.igrium.replaylab.ui.subpanels.ChannelList;
import com.igrium.replaylab.ui.subpanels.ChannelListFlags;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import com.igrium.replaylab.ui.util.TimelineFlags;
import com.igrium.replaylab.ui.subpanels.TimelineHeader;
import com.replaymod.replaystudio.rar.state.Replay;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.*;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.*;

import java.lang.Math;
import java.util.*;
import java.util.stream.Collectors;

public class CurveEditor extends UIPanel {

    private record ChannelExtents(double min, double max) {};

    private final Map<ChannelReference, ChannelExtents> normalizationCache = new HashMap<>();
    private final ImBoolean normalized = new ImBoolean(false);

    public boolean isNormalized() {
        return normalized.get();
    }

    public void setNormalized(boolean normalized) {
        this.normalized.set(normalized);
    }

    /**
     * The X pan amount in milliseconds
     */
    @Getter
    @Setter
    private double offsetX;

    /**
     * The Y pan amount in curve units
     */
    @Getter
    @Setter
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

    public CurveEditor(Identifier id) {
        super(id);
    }


    private record KeyOffsetPair(KeyHandleReference ref, Vector2dc offset) { }
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

    /**
     * The keyframe that is currently being edited in the context menu
     */
    private @Nullable KeyHandleReference contextKey = null;

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
     *
     * @param targetZoom New zoom factor.
     * @param center     Point to center around (ms)
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

    @Override
    public void onAppliedOperator(ReplayOperator op, EditorState editorState) {
        updateNormalizationCache(editorState.getScene());
    }

    @Override
    protected void drawContents(EditorState editorState) {
        drawAndManageHandles(editorState, 0);
        long replayTime = editorState.getScene().sceneToReplayTime(editorState.getPlayhead());

        if (stoppedScrubbing() ||
                (isScrubbing() && replayTime > EditorState.getReplayHandlerOrThrow().getReplaySender().currentTimeStamp())) {
            editorState.queueTimeJump();
        } else if (isScrubbing()) {
            editorState.queueApplyToGame();
        }

        if (ImGui.shortcut(Keybinds.deleteSelected())) {
            // we need to clear selected keyframes
            var selected = editorState.getKeySelection().getSelectedKeyframes();
            editorState.getKeySelection().deselectAll();

            editorState.applyOperator(new RemoveKeyframesOperator(selected));


            updateNormalizationCache(editorState.getScene());
        }

        if (ImGui.shortcut(Keybinds.selectAll())) {
            editorState.getKeySelection().selectAll(editorState.getScene().getObjects());
        }

        if (ImGui.shortcut(Keybinds.selectNone())) {
            editorState.getKeySelection().deselectAll();
        }
    }

    public void drawAndManageHandles(EditorState editorState, int flags) {
        drawCurveEditor(editorState.getScene(), null, editorState.getKeySelection(), editorState.getPlayheadRef(), flags);


        // All handles being directly manipulated should have their type set to aligned.
        for (var hRef : keyDragOffsets.keySet()) {
            if (keyDragOffsets.containsKey(new KeyHandleReference(hRef.keyRef(), 0)))
                continue; // Don't mess with the handles if center is being dragged
            Keyframe key = hRef.keyRef().get(editorState.getScene().getObjects());
            if (key != null) {
                if (key.getHandleAType() != Keyframe.HandleType.FREE) {
                    key.setHandleAType(Keyframe.HandleType.ALIGNED);
                }
                if (key.getHandleBType() != Keyframe.HandleType.FREE) {
                    key.setHandleBType(Keyframe.HandleType.ALIGNED);
                }
            }
        }

        // Recompute handles
        getUpdatedHandles().stream()
                .map(ref -> ref.keyRef().channelRef())
                .distinct().forEach(chRef -> {
                    KeyChannel ch = chRef.get(editorState.getScene().getObjects());
                    if (ch == null) return;

                    var dragging = keyDragOffsets.keySet().stream()
                            .map(hRef -> new ChannelUtils.LocalHandleRef(hRef.keyIndex(), hRef.handleIndex()))
                            .collect(Collectors.toSet());

                    ChannelUtils.computeAutoHandles(ch, dragging);
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
     * @param scene           The scene to edit. Keyframes will be updated as the user changes them.
     * @param selectedObjects The objects to display the keyframes of. <code>null</code> to display all objects
     * @param selectedKeys    All keyframe handles which are currently selected.
     *                        Updated as the user selects/deselects keyframes.
     * @param playhead        Current playhead position. Updated as the player scrubs.
     * @param flags           Render flags.
     */
    public void drawCurveEditor(ReplayScene scene, @Nullable Collection<String> selectedObjects,
                                KeySelectionSet selectedKeys, @Nullable ImInt playhead, int flags) {

        droppedHandles.clear();
        updatedHandles.clear();
//        Collection<String> objs = selectedObjects != null ? selectedObjects : scene.getObjects().keySet();
        Map<String, ReplayObject> objs = selectedObjects != null ? scene.getObjects()
                .entrySet().stream()
                .filter(entry -> selectedObjects.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)) : scene.getObjects();

        int majorIntervalX = (int) TimelineHeader.computeMajorInterval(zoomFactorX);
        int minorIntervalX = majorIntervalX / 2;

        float majorIntervalY = TimelineHeader.computeMajorInterval(zoomFactorY);
        float minorIntervalY = majorIntervalY / 2;

        float headerCursorY = ImGui.getCursorPosY();
        float headerHeight = ImGui.getTextLineHeight() * 2f;

        ImGui.dummy(0, headerHeight);
        ImGui.sameLine();

        /// === BUTTONS ===
        ReplayLabControls.toggleButton(ReplayLabIcons.ICON_MAGNET, "snapKeyframes", snapKeyframes, "gui.replaylab.tooltip_snap");

        ImGui.sameLine();
        boolean wantsFit = ReplayLabControls.iconButton(ReplayLabIcons.ICON_RESIZE_FULL_ALT, "wantsFit", "gui.replaylab.tooltip_fit");

        boolean wasNormalized = isNormalized();

        ImGui.sameLine();
        ReplayLabControls.toggleButton(ReplayLabIcons.ICON_ARROWS_V, "normalize", normalized, "gui.replaylab.tooltip_normalize");


        /// === CHANNEL LIST ===
        ImGui.beginChild("channels", 192, -1, false, ImGuiWindowFlags.NoScrollbar);
        ChannelList.drawChannelList(selectedKeys, objs, 192,
                ChannelListFlags.SHOW_HIDE | ChannelListFlags.ALLOW_SELECTION
                        | ChannelListFlags.HIGHLIGHT_SELECTION | ChannelListFlags.SHOW_COLORS);
        ImGui.endChild();

        ImGui.sameLine();
        float headerCursorX = ImGui.getCursorPosX();
        float graphHeight = ImGui.getContentRegionAvailY();

        float mouseGlobalX = ImGui.getMousePosX();
        float mouseGlobalY = ImGui.getMousePosY();

        // Vertical auto-fit
        if (!wasNormalized && isNormalized()) {
            setOffsetY(-1.2);
            setZoomFactorY(graphHeight / 2.4f);
            updateNormalizationCache(scene);
        } else if (wasNormalized && !isNormalized()) {
            wantsFit = true;
        }

        /// === GRAPH ===

        IntSet keyTimes = new IntOpenHashSet();

        if (ImGui.beginChild("keygraph", ImGui.getContentRegionAvailX(), graphHeight, false)) {
            float graphX = ImGui.getCursorScreenPosX();
            float graphY = ImGui.getCursorScreenPosY();

            float gWidth = ImGui.getContentRegionAvailX();
            float gHeight = ImGui.getContentRegionAvailY();

            /// === FITTING ===
            if ((wantsFit || !doneInitialFit) && !objs.isEmpty()) {
                Vector2d boundsMin = new Vector2d();
                Vector2d boundsMax = new Vector2d();

                Iterable<KeyHandleReference> iter = selectedKeys.isEmpty()
                        ? KeySelectionSet.iterateAllHandles(scene.getObjects())
                        : selectedKeys.getSelectedHandles();

                computeDisplayBoundingBox(scene.getObjects(), iter, boundsMin, boundsMax);

                if (boundsMin.x != boundsMax.x)
                    setZoomFactorX((float) (gWidth / (boundsMax.x - boundsMin.x)));

                if (boundsMin.y != boundsMax.y)
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
            for (int ms = startTick; ms <= endTick; ms += minorIntervalX) {
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
            KeyHandleReference rightClickedOn = null;

            boolean mouseClicked = ImGui.isItemClicked();
            boolean rightClicked = ImGui.isItemClicked(ImGuiMouseButton.Right);

            // If we're overing any key, with some leeway for drag threshold
            boolean hoveringAnyKey = false;
            float keyHoverRadius = 12f + ImGui.getIO().getMouseDragThreshold() * 2;

            int chIndex = 0;
            for (var objEntry : objs.entrySet()) {
                String objName = objEntry.getKey();
                ReplayObject obj = objEntry.getValue();
                if (obj == null)
                    continue;

                // For each channel
                for (var chEntry : obj.getChannels().entrySet()) {
                    if (chEntry.getValue().isHidden())
                        continue;

                    // Don't allocate new ChannelReference unless we need to
                    ChannelExtents extents = isNormalized() ?
                            normalizationCache.get(new ChannelReference(objName, chEntry.getKey())) : null;

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

                            drawList.addCircle(handleAX, handleAY, 3f, lSelected ? selColor : handleEndColor);

                            float handleBX = msToPixelX(key.getGlobalBX()) + graphX;
                            double displayB = rawToDisplay(key.getGlobalBY(), extents);
                            float handleBY = valueToPixelY(displayB) + graphY;

                            drawList.addCircle(handleBX, handleBY, 3f, rSelected ? selColor : handleEndColor);

                            // TODO: Shouldn't this be defined in the theme somehow?
                            int lineColor = 0x808E79D1;
                            int lineColorSel = 0xFF93B4FF;

                            int lColor = getHandleColor(key.getHandleAType());
                            if (!(lSelected || cSelected)) {
                                lColor = replaceAlpha(lColor, 63);
                            }

                            int rColor = getHandleColor(key.getHandleBType());
                            if (!(rSelected || cSelected)) {
                                rColor = replaceAlpha(rColor, 63);
                            }

                            drawList.addLine(handleAX, handleAY, keyX, keyY, lColor);
                            drawList.addLine(handleBX, handleBY, keyX, keyY, rColor);

                            boolean channelSelected = selectedKeys.isChannelSelected(objName, chEntry.getKey());
                            // Calculate the visual distance (in pixels) between the handles and the center keyframe
                            float handleSnapThreshold = 12f; // Adjust this threshold to your liking
                            boolean isHandleAClose = Math.hypot(handleAX - keyX, handleAY - keyY) <= handleSnapThreshold;
                            boolean isHandleBClose = Math.hypot(handleBX - keyX, handleBY - keyY) <= handleSnapThreshold;

                            // Prioritize clicking on the selected channel unless the keyframe being clicked is already selected.
                            if (mouseClicked && (channelSelected || clickedOn == null)) {
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
                            if (rightClicked && (channelSelected || rightClickedOn == null)) {
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

                        drawList.addBezierCubic(keyX, keyY, keyHandleX, keyHandleY, nextHandleX, nextHandleY, nextX, nextY,
                                chColor, chSelected ? 2 : 1);
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

                    chIndex++;
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

                    // Gray out the area ABOVE -1 (from graphY down to the boundary line)
                    if (pixelNeg1 > graphY) {
                        drawList.addLine(pixelIn, pixelNeg1, pixelOut, pixelNeg1, separatorColor);
                        drawList.addRectFilled(pixelIn, graphY, pixelOut, pixelNeg1, outOfNormalColor);
                    }

                    // Gray out the area BELOW 1 (from the boundary line down to graphY + graphHeight)
                    if (pixelPos1 < graphY + graphHeight) {
                        drawList.addLine(pixelIn, pixelPos1, pixelOut, pixelPos1, separatorColor);
                        drawList.addRectFilled(pixelIn, pixelPos1, pixelOut, graphY + graphHeight, outOfNormalColor);
                    }
                }
            }

            ///  === SELECTION ===
            if (clickedOn != null || rightClickedOn != null) {
                if (!ImGui.getIO().getKeyCtrl()) {
                    selectedKeys.deselectAll();
                }
                selectedKeys.selectHandle(clickedOn != null ? clickedOn : rightClickedOn);
            } else if (mouseClicked && !ImGui.getIO().getKeyCtrl() && !hoveringAnyKey) {
                // Don't deselect on right click
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
                        ChannelExtents extents = isNormalized() ?
                                normalizationCache.get(new ChannelReference(objName, chEntry.getKey())) : null;
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
                contextKey = rightClickedOn;
                ImGui.openPopup("contextMenu");
            }

            if (ImGui.beginPopup("contextMenu")) {

                ImGui.menuItem("Test menu item");

                // Handle selection
                if (ImGui.beginMenu("Handle Type")) {
                    // If every selected handle has the same handle type, find it.
                    Keyframe.HandleType handleType = null;
                    for (KeyHandleReference handle : selectedKeys.effectiveSelectedHandles()) {
                        Keyframe key = handle.keyRef().get(scene.getObjects());
                        if (key != null) {
                            Keyframe.HandleType type = switch (handle.handleIndex()) {
                                case 1 -> key.getHandleAType();
                                case 2 -> key.getHandleBType();
                                default -> null;
                            };

                            if (type == null) continue; // Shouldn't happen

                            if (handleType == null) {
                                handleType = type;
                            } else if (handleType != type) {
                                handleType = null;
                                break;
                            }
                        }
                    }


                    Keyframe.HandleType newHandleType = null;

                    if (ImGui.menuItem("Free", "", handleType == Keyframe.HandleType.FREE)) {
                        newHandleType = Keyframe.HandleType.FREE;
                    }
                    if (ImGui.menuItem("Aligned", "", handleType == Keyframe.HandleType.ALIGNED)) {
                        newHandleType = Keyframe.HandleType.ALIGNED;
                    }
                    if (ImGui.menuItem("Vector", "", handleType == Keyframe.HandleType.VECTOR)) {
                        newHandleType = Keyframe.HandleType.VECTOR;
                    }
                    if (ImGui.menuItem("Automatic", "", handleType == Keyframe.HandleType.AUTO)) {
                        newHandleType = Keyframe.HandleType.AUTO;
                    }
                    if (ImGui.menuItem("Auto Clamped", "", handleType == Keyframe.HandleType.AUTO_CLAMPED)) {
                        newHandleType = Keyframe.HandleType.AUTO_CLAMPED;
                    }

                    if (newHandleType != null) {
                        for (KeyHandleReference ref : selectedKeys.effectiveSelectedHandles()) {
                            Keyframe keyframe = ref.keyRef().get(scene.getObjects());
                            if (keyframe == null) continue;
                            switch (ref.handleIndex()) {
                                case 1 -> keyframe.setHandleAType(newHandleType);
                                case 2 -> keyframe.setHandleBType(newHandleType);
                            }
                            updatedHandles.add(ref);
                            droppedHandles.add(ref);
                        }

                    }

                    ImGui.endMenu();
                }
                ImGui.endPopup();
            }

            /// ==== DRAGGING ===
            if (isDragging() && !ImGui.isMouseDragging(0)) {
                /// Stop dragging
                droppedHandles.addAll(keyDragOffsets.keySet());
                keyDragOffsets.clear();
                smallestKeyDragOffset = null;

            } else if (!isDragging() && !isBoxSelecting() && ImGui.isMouseDragging(0)
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

                    // MODIFICATION: Convert raw Y positions to Display Space before calculating offsets
//                    ChannelExtents extents = normalizationCache.get(new ChannelId(hRef.objectName(), hRef.channelName()));
                    ChannelExtents extents = isNormalized() ?
                            normalizationCache.get(new ChannelReference(hRef.objectName(), hRef.channelName())) : null;
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
                    float thresholdX = 8f / zoomFactorX;
                    float thresholdY = 8f / zoomFactorY;

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

                            // MODIFICATION: Fetch extents for the target curve we are testing snap points against
//                            ChannelExtents snapExtents = normalizationCache.get(new ChannelId(objName, chEntry.getKey()));
                            ChannelExtents snapExtents = isNormalized() ?
                                    normalizationCache.get(new ChannelReference(objName, chEntry.getKey())) : null;

                            for (Keyframe key : chEntry.getValue().getKeyframes()) {
                                for (int i = 0; i < 3; i++) {

                                    // Don't attempt to lock to something we're also dragging
                                    if (keyDragOffsets.containsKey(new KeyHandleReference(objName, chEntry.getKey(), keyIdx, i)))
                                        continue;

                                    double handleX = key.getHandleX(i);

                                    // MODIFICATION: Normalize the static snap target value to match our Display Space
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

                    ChannelExtents extents = isNormalized() ?
                            normalizationCache.get(new ChannelReference(hRef.objectName(), hRef.channelName())) : null;

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
            header.drawHeader(headerHeight, zoomFactorX, (float) offsetX, scene.getLength(), playhead, graphHeight, keyTimes.toIntArray(), flags);
        }

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
            var extents = isNormalized() ? normalizationCache.get(handle.keyRef().channelRef()) : null;
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

    /**
     * Compute a bounding box of all the curves in a set of replay objects.
     *
     * @param objects Objects to compute curves of.
     * @param dest1   Min corner of the bbox
     * @param dest2   Max corner of the bbox
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
