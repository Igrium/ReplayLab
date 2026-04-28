package com.igrium.replaylab.ui.panels;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.editor.KeySelectionSet;
import com.igrium.replaylab.editor.KeySelectionSet.KeyframeReference;
import com.igrium.replaylab.operator.CommitObjectUpdateOperator;
import com.igrium.replaylab.operator.RemoveKeyframesOperator;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.key.KeyChannel;
import com.igrium.replaylab.scene.key.Keyframe;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.ui.ReplayLabIcons;
import com.igrium.replaylab.ui.util.ChannelList;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import com.igrium.replaylab.ui.util.TimelineHeader;
import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.*;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class DopeSheetNew extends UIPanel {

    private static final float SNAP_THRESHOLD_PX = 4f;
    private static final float INTERVAL_DIVISOR = 4f;
    private static final float MAX_ZOOM_FACTOR = 8f;

    private enum KeyframeShape {
        DIAMOND,
        SQUARE,
        CIRCLE,
        CIRCLE_FILLED
    }

    private record KeyDrawData(int ms, boolean selected, KeyframeShape shape) {};

    /**
     * The X pan amount in milliseconds
     */
    @Getter @Setter
    private double offsetX;

    /**
     * The amount of pixels per millisecond
     */
    @Getter
    private float zoomFactor = 0.1f;

    /**
     * All the keyframes that have had an update <em>comitted</em> this frame.
     * Does not include keyframes being dragged.
     */
    @Getter
    private final Set<KeyframeReference> droppedKeys = new HashSet<>();

    /**
     * All the handles that have been updated this frame.
     */
    @Getter
    private final Set<KeyframeReference> updatedKeys = new HashSet<>();

    /**
     * The amount of ms offset from the mouse that each key being dragged has.
     */
    private final Map<KeyframeReference, Double> keyDragOffsets = new HashMap<>();

    private record KeyOffsetPair(KeyframeReference ref, double offset) {};

    /**
     * The key drag offset that's closest to the mouse (ms)
     */
    private @Nullable KeyOffsetPair smallestKeyDragOffset;

    /**
     * The global location of the smallest key drag offset at the time of drag start in ms
     */
    private double dragStartPos;

    private final TimelineHeader header = new TimelineHeader();

    // Not null if currently panning
    private @Nullable Double panStartPos;

    private final ImBoolean snapKeyframes = new ImBoolean();

    /**
     * The global pixel position of a selection box start position
     */
    private @Nullable ImVec2 boxSelectStart;

    /**
     * The keyframe that is currently being edited in the context menu
     */
    private @Nullable KeyframeReference contextKey;

    private boolean mouseDragging;
    private boolean mouseStartedDragging;


    public DopeSheetNew(Identifier id) {
        super(id);
    }

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

    public void setZoomFactor(float zoomFactor) {
        if (zoomFactor <= 0) {
            throw new IllegalArgumentException("zoomFactor must be greater than 0.");
        }
        this.zoomFactor = Math.min(zoomFactor, MAX_ZOOM_FACTOR);
    }

    /**
     * Modify the zoom of the editor, centering it around a supplied point
     *
     * @param targetZoom New zoom factor.
     * @param center     Point to center around (ms)
     */
    public void setZoomFactor(float targetZoom, double center) {
        targetZoom = Math.min(targetZoom, MAX_ZOOM_FACTOR);
        if (targetZoom == this.zoomFactor) return;

        double newOffset = center - (center - offsetX) * (this.zoomFactor / targetZoom);
        this.zoomFactor = targetZoom;;
        this.offsetX = newOffset;
    }

    @Override
    protected void drawContents(EditorState editorState) {
        drawDopeSheet(editorState.getScene(), null, editorState.getKeySelection(), editorState.getPlayheadRef());

        long replayTime = editorState.getScene().sceneToReplayTime(editorState.getPlayhead());
        if (stoppedScrubbing() ||
                (isScrubbing() && replayTime > EditorState.getReplayHandlerOrThrow().getReplaySender().currentTimeStamp())) {
            editorState.queueTimeJump();
        } else if (isScrubbing()) {
            editorState.queueApplyToGame();
        }

        var droppedObjects = getDroppedKeys().stream()
                .map(KeyframeReference::objectName)
                .distinct()
                .toList();

        // If any objects were fully updated this frame, push an undo operator
        if (!droppedObjects.isEmpty()) {
            editorState.applyOperator(new CommitObjectUpdateOperator(droppedObjects));
        }

        if (!keyDragOffsets.isEmpty()) {
            editorState.queueApplyToGame();
        }

        if (ImGui.shortcut(ImGuiKey.Delete)) {
            var selected = editorState.getKeySelection().getSelectedKeyframes();
            editorState.getKeySelection().deselectAll();
            editorState.applyOperator(new RemoveKeyframesOperator(selected));
        }

        if (ImGui.shortcut(ImGuiKey.ModCtrl | ImGuiKey.A)) {
            editorState.getKeySelection().selectAll(editorState.getScene().getObjects());
        }
    }

    public void drawDopeSheet(ReplayScene scene, @Nullable Collection<String> selectedObjects,
                              KeySelectionSet selectedKeys, @Nullable ImInt playhead) {
        droppedKeys.clear();
        updatedKeys.clear();
        Collection<String> objs = selectedObjects != null ? selectedObjects : scene.getObjects().keySet();

        boolean draggingNow = ImGui.isMouseDragging(0);
        mouseStartedDragging = draggingNow && !mouseDragging;
        mouseDragging = draggingNow;

        int majorIntervalX = (int) TimelineHeader.computeMajorInterval(zoomFactor);

        float headerCursorY = ImGui.getCursorPosY();
        float headerHeight = ImGui.getTextLineHeight() * 2f;

        ImGui.dummy(0, headerHeight);
        ImGui.sameLine();

        /// === BUTTONS ===
        ReplayLabControls.toggleButton(ReplayLabIcons.ICON_MAGNET, "gui.replaylab.tooltip_snap", snapKeyframes);
        ImGui.sameLine();
        boolean wantsFit = ReplayLabControls.iconButton(ReplayLabIcons.ICON_RESIZE_FULL_ALT, "", "gui.replaylab.tooltip_fit");


        float graphHeight = ImGui.getContentRegionAvailY();
        float headerCursorX;

        boolean wantStartDragging = false;

        /// === MAIN ===
        // We'll manually implement scrolling so keyframe graph can "consume" it.
        ImGui.beginChild("main", ImGui.getContentRegionAvailX(), -1, false, ImGuiWindowFlags.NoScrollWithMouse);
        {
            ///  === CHANNEL LIST ===
            ImGui.beginGroup();
            var expandedObjects = ChannelList.drawChannelList(scene, objs, 192);
            ImGui.endGroup();
            float channelListHeight = ImGui.getItemRectSizeY();
            ImGui.sameLine();
            headerCursorX = ImGui.getCursorPosX() + ImGui.getStyle().getItemSpacing().x;

            float graphStart = ImGui.getCursorPosX();

            /// === KEYFRAMES ===
            ImGui.beginGroup();
            float graphX = ImGui.getCursorScreenPosX();
            float graphY = ImGui.getCursorScreenPosY();

            // Every position that a given keyframe renders at. Used for box select.
            Map<KeyframeReference, Set<Vector2f>> keyPositions = new HashMap<>();
            boolean hoveringAnyKey = false;
            {
                float graphWidth = ImGui.getContentRegionAvailX();
                ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, ImGui.getStyle().getItemSpacingX(), 0);

                ImDrawList drawList = ImGui.getWindowDrawList();
                int rowIndex = 0;

                for (String objName : objs) {
                    ReplayObject obj = scene.getObject(objName);
                    if (obj == null) continue;

                    // COMBINED ROW
                    {
                        // Map all ms timestamps and the keyframes that land on said timestamp.
                        Int2ObjectMap<Set<Pair<KeyframeReference, Keyframe>>> combinedKeys = new Int2ObjectOpenHashMap<>();
                        for (var chEntry : obj.getChannels().entrySet()) {
                            var chan = chEntry.getValue();
                            int i = 0;
                            for (var key : chan.getKeyframes()) {
                                var set = combinedKeys.computeIfAbsent(key.getTimeInt(), v -> new HashSet<>());
                                set.add(new ObjectObjectImmutablePair<>(
                                        new KeyframeReference(objName, chEntry.getKey(), i), key
                                ));
                                i++;
                            }
                        }

                        KeyDrawData[] drawData = new KeyDrawData[combinedKeys.size()];
                        int i = 0;
                        for (var msEntry : combinedKeys.int2ObjectEntrySet()) {
                            List<Keyframe.HandleType> handleTypes = new ArrayList<>(msEntry.getValue().size() * 2);
                            boolean selected = false;
                            for (var pair : msEntry.getValue()) {
                                handleTypes.add(pair.value().getHandleAType());
                                handleTypes.add(pair.value().getHandleBType());
                                // Don't do the check if we're already selected
                                if (!selected && selectedKeys.isKeyframeSelected(pair.key()))
                                    selected = true;
                            }

                            drawData[i] = new KeyDrawData(
                                    msEntry.getIntKey(),
                                    selected,
                                    getKeyShape(handleTypes.toArray(Keyframe.HandleType[]::new)));
                            i++;
                        }

                        if (drawKeyChannel(drawData, rowIndex, (selIdx, button) -> {
                            if (button != 0) return; // TODO: right-click
                            if (!ImGui.getIO().getKeyCtrl()) {
                                selectedKeys.deselectAll();
                            }
                            if (selIdx == null) return;
                            for (var pair : combinedKeys.get(drawData[selIdx].ms)) {
                                selectedKeys.selectKeyframe(pair.key());
                            }
                        }, (keyIdx, pos) -> {
                            for (var pair : combinedKeys.get(drawData[keyIdx].ms)) {
                                Set<Vector2f> set = keyPositions.computeIfAbsent(pair.key(), v -> new HashSet<>());
                                set.add(pos);
                            }
                        }, drawList)) {
                            hoveringAnyKey = true;
                            if (mouseStartedDragging) wantStartDragging = true;
                        }
                    }

                    rowIndex++;

                    // INDIVIDUAL CHANNELS
                    if (!expandedObjects.contains(objName)) continue;

                    for (var chEntry : obj.getChannels().entrySet()) {
                        KeyDrawData[] drawData = new KeyDrawData[chEntry.getValue().getKeyframes().size()];
                        {
                            int i = 0;
                            for (var key : chEntry.getValue().getKeyframes()) {
                                drawData[i] = new KeyDrawData(key.getTimeInt(),
                                        selectedKeys.isKeyframeSelected(objName, chEntry.getKey(), i),
                                        getKeyShape(key.getHandleAType(), key.getHandleBType()));
                                i++;
                            }
                        }
                        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());

                        if (drawKeyChannel(drawData, rowIndex, (selIdx, button) -> {
                            if (button != 0) return;

                            boolean isCtrl = ImGui.getIO().getKeyCtrl();
                            String chName = chEntry.getKey();

                            if (!isCtrl) {
                                selectedKeys.deselectAll();
                            }

                            // TODO: Ctrl deselect (broken because of dragging)
                            if (selIdx != null)
                                selectedKeys.selectKeyframe(objName, chName, selIdx);

                        }, (keyIdx, pos) -> {
                            var ref = new KeyframeReference(objName, chEntry.getKey(), keyIdx);
                            Set<Vector2f> set = keyPositions.computeIfAbsent(ref, v -> new HashSet<>());
                            set.add(pos);
                        }, drawList)) {
                            hoveringAnyKey = true;
                            if (mouseStartedDragging) wantStartDragging = true;
                        }

                        rowIndex++;
                    }
                }
                ImGui.popStyleVar();

                /// === OUT-OF-BOUNDS GRAYOUT ===
                {
                    // TODO: respect scene in point
                    float pixelIn = msToPixelX(0);
                    float pixelOut = msToPixelX(scene.getLength());

                    if (pixelIn > 0) {
                        float pixelInGlobal = pixelIn + graphX;
                        drawList.addLine(pixelInGlobal, graphY, pixelInGlobal, graphY + channelListHeight, ImGui.getColorU32(ImGuiCol.Separator));
                        drawList.addRectFilled(graphX, graphY, pixelInGlobal, graphY + channelListHeight, ImGui.getColorU32(ImGuiCol.ModalWindowDimBg));
                    }

                    if (pixelOut < graphWidth) {
                        float pixelOutGlobal = pixelOut + graphX;
                        drawList.addLine(pixelOutGlobal, graphY, pixelOutGlobal, graphY + channelListHeight, ImGui.getColorU32(ImGuiCol.Separator));
                        drawList.addRectFilled(pixelOutGlobal, graphY, graphX + graphWidth, graphY + channelListHeight, ImGui.getColorU32(ImGuiCol.ModalWindowDimBg));
                    }
                }

            }
            ImGui.endGroup();

            /// === ZOOM ===
            if (ImGui.isItemHovered()) {
                float mWheel = ImGui.getIO().getMouseWheel();
                if (mWheel != 0) {
                    float mouseGlobalX = ImGui.getMousePosX();
                    double mouseXMs = pixelXToMs(mouseGlobalX - graphX);
                    float factor = (float) Math.pow(2, mWheel * .125);
                    setZoomFactor(zoomFactor * factor, mouseXMs);
                }
                // zoom logic
            } else {
                float scrollSpeed = ImGui.getTextLineHeightWithSpacing() * 3f;
                ImGui.setScrollY(ImGui.getScrollY() - ImGui.getIO().getMouseWheel() * scrollSpeed);
            }

            /// === PANNING ===
            if (ImGui.isMouseDragging(2)) {
                if (panStartPos == null && ImGui.isWindowHovered(ImGuiHoveredFlags.ChildWindows)) {
                    panStartPos = offsetX;
                }

                if (panStartPos != null) {
                    double dx = ImGui.getMouseDragDeltaX(2) / zoomFactor;
                    offsetX = panStartPos - dx;
                }
            } else {
                panStartPos = null;
            }

            /// === BOX SELECT ===
            // Start/stop
            {
                if (!isBoxSelecting() && !isDragging() && !isScrubbing() && !hoveringAnyKey
                        && ImGui.isWindowHovered(ImGuiHoveredFlags.ChildWindows | ImGuiHoveredFlags.AllowWhenBlockedByActiveItem) && ImGui.isMouseDown(0)) {
                    boxSelectStart = ImGui.getMousePos();
                    if (!ImGui.getIO().getKeyCtrl()) {
                        selectedKeys.deselectAll();
                    }
                    boxSelectStart = ImGui.getMousePos();
                } else if (!ImGui.isMouseDown(0)) {
                    boxSelectStart = null;
                }

                // Currently box selecting
                float mouseGlobalX = ImGui.getMousePosX();
                float mouseGlobalY = ImGui.getMousePosY();
                if (boxSelectStart != null) {
                    float boxMinX = Math.min(boxSelectStart.x, mouseGlobalX);
                    float boxMinY = Math.min(boxSelectStart.y, mouseGlobalY);

                    float boxMaxX = Math.max(boxSelectStart.x, mouseGlobalX);
                    float boxMaxY = Math.max(boxSelectStart.y, mouseGlobalY);

                    ImDrawList dl = ImGui.getForegroundDrawList();

                    int rectColor = replaceAlpha(ImGui.getColorU32(ImGuiCol.HeaderActive), 25);
                    dl.addRectFilled(boxMinX, boxMinY, boxMaxX, boxMaxY, rectColor);
                    dl.addRect(boxMinX, boxMinY, boxMaxX, boxMaxY, ImGui.getColorU32(ImGuiCol.HeaderActive));

                    for (var entry : keyPositions.entrySet()) {
                        boolean inBox = false;
                        for (var center : entry.getValue()) {
                            if (boxMinX < center.x && center.x < boxMaxX
                                    && boxMinY < center.y && center.y < boxMaxY) {
                                inBox = true;
                                break;
                            }
                        }
                        if (inBox) {
                            selectedKeys.selectKeyframe(entry.getKey());
                        } else if (!ImGui.getIO().getKeyCtrl()) {
                            selectedKeys.deselectKeyframe(entry.getKey());
                        }
                    }
                }
            }
        }
        ImGui.endChild();

        /// === DRAGGING ===
        if (isDragging()) {
            if (mouseDragging) {
                float dx = ImGui.getMouseDragDeltaX();
                double rawDeltaMs = dx / zoomFactor;
                double snapAdjustMs = 0;

                // Snap to other keyframes
                if (!ImGui.getIO().getKeyCtrl() && smallestKeyDragOffset != null) {
                    double anchorMs = smallestKeyDragOffset.offset() + rawDeltaMs;
                    String anchorObj = smallestKeyDragOffset.ref().objectName();
                    ReplayObject obj = scene.getObject(anchorObj);

                    if (obj != null) {
                        Set<Keyframe> draggingFrames = keyDragOffsets.keySet().stream()
                                .map(r -> r.get(scene.getObjects()))
                                .filter(Objects::nonNull)
                                .collect(java.util.stream.Collectors.toSet());

                        double bestDistPx = Double.MAX_VALUE;
                        double bestSnapMs = 0;

                        for (KeyChannel channel : obj.getChannels().values()) {
                            for (Keyframe candidate : channel.getKeyframes()) {
                                if (draggingFrames.contains(candidate)) continue;

                                double distPx = Math.abs((candidate.getTime() - anchorMs) * zoomFactor);
                                if (distPx < SNAP_THRESHOLD_PX && distPx < bestDistPx) {
                                    bestDistPx = distPx;
                                    bestSnapMs = candidate.getTime();
                                }
                            }
                        }

                        if (bestDistPx < Double.MAX_VALUE) {
                            snapAdjustMs = bestSnapMs - anchorMs;
                        } else if (snapKeyframes.get()) {
                            double snapInterval = TimelineHeader.computeMajorInterval(zoomFactor) / INTERVAL_DIVISOR;
                            double nearestIntervalMs = snapInterval * Math.round(anchorMs / snapInterval);

                            snapAdjustMs = nearestIntervalMs - anchorMs;
                        }
                    }
                }

                for (var entry : keyDragOffsets.entrySet()) {
                    Keyframe key = entry.getKey().get(scene.getObjects());
                    if (key == null) continue;

                    key.setTime(entry.getValue() + rawDeltaMs + snapAdjustMs);
                }
            } else {
                // Commit drag
                keyDragOffsets.keySet().stream()
                        .map(ref -> ref.channelRef().get(scene.getObjects()))
                        .filter(Objects::nonNull)
                        .distinct()
                        .forEach(KeyChannel::removeDuplicates);

                droppedKeys.addAll(keyDragOffsets.keySet());
                keyDragOffsets.clear();
                smallestKeyDragOffset = null;
            }
        } else if (wantStartDragging) {
            // Begin a new drag
            double mouseMs = pixelXToMs(ImGui.getMousePosX());
            dragStartPos = mouseMs;
            smallestKeyDragOffset = null;

            selectedKeys.forSelectedKeyframes(ref -> {
                Keyframe keyframe = ref.get(scene.getObjects());
                if (keyframe == null) return;

                // Store each key's original time as its drag base.
                // The update loop adds the raw pixel drag delta to this value.
                double offset = keyframe.getTime();
                keyDragOffsets.put(ref, offset);

                // Track whichever selected key sits closest to the mouse cursor,
                // so snapping (if enabled) can anchor to the nearest key.
                double distToMouse = Math.abs(keyframe.getTime() - mouseMs);
                if (smallestKeyDragOffset == null
                        || distToMouse < Math.abs(smallestKeyDragOffset.offset() - mouseMs)) {
                    smallestKeyDragOffset = new KeyOffsetPair(ref, offset);
                }
            });
        }

        /// === HEADER ===
        ImGui.setCursorPos(headerCursorX, headerCursorY);
        header.drawHeader(headerHeight, zoomFactor, (float) offsetX, scene.getLength(), playhead, graphHeight, 0);
    }

    /**
     * Draw a singular row of keyframes.
     *
     * @param keys          All keyframes to render.
     * @param rowIndex      Index of the row.
     * @param onClick       Called when a key has been clicked on (keyIndex, mouseButton -> void)
     * @param screenPosSink Called with the position of each keyframe on the screen. (keyIndex, positionGlobal -> void)
     * @param drawList      The draw list to use.
     * @return <code>true</code> if any key is hovered.
     */
    private boolean drawKeyChannel(KeyDrawData[] keys, int rowIndex,
                                   BiConsumer<Integer, Integer> onClick,
                                   BiConsumer<Integer, Vector2f> screenPosSink,
                                   ImDrawList drawList) {
        ImGui.pushID("Dope Channel " + rowIndex);

        float lineWidth = ImGui.calcItemWidth();
        float lineHeight = ImGui.getFrameHeight();

        float cursorX = ImGui.getCursorScreenPosX();
        float cursorY = ImGui.getCursorScreenPosY();

        int color = ImGui.getColorU32(rowIndex % 2 == 0 ? ImGuiCol.TableRowBgAlt : ImGuiCol.TableRowBg);
        drawList.addRectFilled(cursorX, cursorY, cursorX + lineWidth, cursorY + lineHeight, color);

        float keySize = ImGui.getFontSize();
        float keyRadius = keySize / 2;
        float centerY = cursorY + lineHeight / 2;

        BiFunction<Float, Float, Integer> getHoveredKey = (posX, posY) -> {
            int i = 0;
            for (var key : keys) {
                float centerX = msToPixelX(key.ms()) + cursorX;
                if (centerX - keyRadius - 2 < posX && posX < centerX + keyRadius + 2
                        && centerY - keyRadius - 2 <= posY && posY <= centerY + keyRadius + 2) {
                    return i;
                }
                i++;
            }
            return null;
        };

        ImGui.invisibleButton("##canvas", lineWidth, lineHeight);

        float mx = ImGui.getMousePosX();
        float my = ImGui.getMousePosY();

        Integer hovered = getHoveredKey.apply(mx, my);

        if (ImGui.isItemHovered()) {
            if (!mouseStartedDragging || hovered == null) {
                if (ImGui.isMouseClicked(0)) {
                    if (hovered == null || !keys[hovered].selected) {
                        onClick.accept(hovered, 0);
                    }
                } else if (ImGui.isMouseClicked(1)) {
                    onClick.accept(hovered, 1);
                }
            }
        }

        for (int i = 0; i < keys.length; i++) {
            int keyMs = keys[i].ms;
            boolean selected = keys[i].selected;
            int keyColor = selected ? ImColor.rgb(1f, 1f, 1f) : ImColor.rgb(.5f, .5f, .5f);
            Vector2f pos = new Vector2f(cursorX + msToPixelX(keyMs), centerY);
//            drawList.addNgonFilled(pos.x, pos.y, keyRadius, keyColor, 4);
            drawKeyframe(keys[i].shape, pos.x, pos.y, keyRadius, keyColor, drawList);
            screenPosSink.accept(i, pos);
        }

        ImGui.popID();
        return hovered != null;
    }

    private KeyframeShape getKeyShape(Keyframe.HandleType... handleTypes) {
        if (handleTypes.length == 0)
            return KeyframeShape.DIAMOND;

        for (var type : handleTypes) {
            if (type != handleTypes[0])
                return KeyframeShape.DIAMOND;
        }

        return switch(handleTypes[0]) {
            case AUTO -> KeyframeShape.CIRCLE;
            case AUTO_CLAMPED -> KeyframeShape.CIRCLE_FILLED;
            case VECTOR -> KeyframeShape.SQUARE;
            default -> KeyframeShape.DIAMOND;
        };
    }

    private void drawKeyframe(KeyframeShape shape, float x, float y, float radius, int color, ImDrawList drawList) {
        if (shape != KeyframeShape.DIAMOND) {
            radius *= .75f;
        }
        switch (shape) {
            case DIAMOND -> drawList.addNgonFilled(x, y, radius, color, 4);
            case SQUARE -> drawList.addRectFilled(x - radius, y - radius, x + radius, y + radius, color);
            case CIRCLE -> drawList.addCircle(x, y, radius, color, 0, radius / 1.4f);
            case CIRCLE_FILLED -> drawList.addCircleFilled(x, y, radius, color);
        }
    }

    private float msToPixelX(double ms) {
        return (float) ((ms - offsetX) * zoomFactor);
    }

    private double pixelXToMs(float pixel) {
        return pixel / zoomFactor + offsetX;
    }

    private static int replaceAlpha(int colorArgb, int newAlpha) {
        newAlpha &= 0xFF;
        return (colorArgb & 0x00FFFFFF) | (newAlpha << 24);
    }
}
