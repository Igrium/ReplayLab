package com.igrium.replaylab.ui.panels;

import com.igrium.replaylab.config.Keybinds;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.editor.KeySelectionSet;
import com.igrium.replaylab.editor.KeySelectionSet.KeyHandleReference;
import com.igrium.replaylab.editor.KeySelectionSet.KeyframeReference;
import com.igrium.replaylab.operator.object.CommitObjectUpdateOperator;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.anim.ChannelUtils;
import com.igrium.replaylab.anim.KeyChannel;
import com.igrium.replaylab.anim.Keyframe;
import com.igrium.replaylab.object.ReplayObject;
import com.igrium.replaylab.ui.ReplayLabIcons;
import com.igrium.replaylab.ui.subpanels.TimelineHeader;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import com.igrium.replaylab.ui.util.TimelineFlags;
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
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector2f;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class DopeSheet extends KeyframePanel {

    private static final float SNAP_THRESHOLD_PX = 4f;
    private static final float INTERVAL_DIVISOR = 4f;

    private enum KeyframeShape {
        DIAMOND,
        SQUARE,
        CIRCLE,
        CIRCLE_FILLED
    }

    private record KeyDrawData(int ms, boolean selected, KeyframeShape shape) {
    }

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

    private record KeyOffsetPair(KeyframeReference ref, double offset) {
    }

    /**
     * The key drag offset that's closest to the mouse (ms)
     */
    private @Nullable KeyOffsetPair smallestKeyDragOffset;

    // Not null if currently panning
    private @Nullable Double panStartPos;

    private final ImBoolean snapKeyframes = new ImBoolean();

    /**
     * The global pixel position of a selection box start position
     */
    private @Nullable ImVec2 boxSelectStart;

    private boolean mouseDragging;
    private boolean mouseStartedDragging;

    public DopeSheet(Identifier id) {
        super(id);
    }

    public boolean isDragging() {
        return !keyDragOffsets.isEmpty();
    }

    public boolean isBoxSelecting() {
        return boxSelectStart != null;
    }

    @Override
    protected void drawInternal(EditorState editorState, Map<String, ReplayObject> objects) {
        drawDopeSheet(editorState, objects, editorState.getKeySelection(),
                editorState.getPlayheadRef());

        // Jump forward if playing and off screen
        double endMs = getOffsetX() + getHeader().getWidthMs();
        if (editorState.isPlaying() && (editorState.getPlayhead() > endMs || editorState.getPlayhead() < getOffsetX())) {
            setOffsetX(editorState.getPlayhead());
        }

        if (isScrubbing() || stoppedScrubbing()) {
            editorState.scrub(stoppedScrubbing());
        }

        var droppedObjects = getDroppedKeys().stream()
                .map(KeyframeReference::objectName)
                .distinct()
                .toList();

        // If any objects were fully updated this frame, push an undo operator
        if (!droppedObjects.isEmpty()) {
            editorState.applyOperator(new CommitObjectUpdateOperator(droppedObjects));
        }

        // Recompute handles
        keyDragOffsets.keySet().stream()
                .map(KeyframeReference::channelRef)
                .distinct().forEach(chRef -> {
                    KeyChannel ch = chRef.get(editorState.getScene().getObjects());
                    if (ch == null) return;

                    var dragging = keyDragOffsets.keySet().stream()
                            .map(hRef -> new ChannelUtils.LocalHandleRef(hRef.keyIndex(), 0))
                            .collect(Collectors.toSet());

                    ChannelUtils.computeHandles(ch, dragging);
                });

        if (!keyDragOffsets.isEmpty()) {
            editorState.queueApplyToGame();
        }
    }

    @Override
    protected int getHeaderFlags() {
        return TimelineFlags.INVERT_KEY_SNAP;
    }

    private boolean wantsFit;

    @Override
    protected void drawControlButtons(EditorState editorState) {
        ReplayLabControls.toggleButton(ReplayLabIcons.ICON_ARROW_POINTER, "selectedOnly", getSelectedOnlyRef(),
                "gui.replaylab.selected_only");
        ImGui.sameLine();

        ReplayLabControls.toggleButton(ReplayLabIcons.ICON_MAGNET, "snapKeyframes", snapKeyframes,
                "gui.replaylab.tooltip_snap");
        ImGui.sameLine();
        wantsFit = ReplayLabControls.iconButton(ReplayLabIcons.ICON_RESIZE_FULL_ALT, "fitKeys",
                "gui.replaylab.tooltip_fit");
    }

    public void drawDopeSheet(EditorState editor, Map<String, ReplayObject> objs,
                              KeySelectionSet selectedKeys, @Nullable ImInt playhead) {
        ReplayScene scene = editor.getScene();
        droppedKeys.clear();
        updatedKeys.clear();
        keyTimes.clear();

        boolean draggingNow = ImGui.isMouseDragging(0);
        mouseStartedDragging = draggingNow && !mouseDragging;
        mouseDragging = draggingNow;

        wantsFit |= ImGui.shortcut(Keybinds.frameSelected());

        float graphHeight = ImGui.getContentRegionAvailY();

        boolean wantStartDragging = false;

        var expandedObjects = getExpandedChannels();

        /// === MAIN ===
        // We'll manually implement scrolling so keyframe graph can "consume" it.
//        ImGui.beginChild("main", ImGui.getContentRegionAvailX(), -1, false, ImGuiWindowFlags.NoScrollWithMouse);
        {
            /// === KEYFRAMES ===
            float graphX = ImGui.getCursorScreenPosX();
            float graphY = ImGui.getCursorScreenPosY();

            // Every position that a given keyframe renders at. Used for box select.
            Map<KeyframeReference, Set<Vector2f>> keyPositions = new HashMap<>();
            boolean hoveringAnyKey = false;
            ImGui.beginGroup();
            {
                float graphWidth = ImGui.getContentRegionAvailX();
                ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, ImGui.getStyle().getItemSpacingX(), 0);

                ImDrawList drawList = ImGui.getWindowDrawList();
                // Intersect with the table's current clip rect (rather than replacing it) so this doesn't
                // override the scroll-aware clipping the table already applies to this column/row.
                drawList.pushClipRect(graphX, graphY, graphX + graphWidth, graphY + graphHeight, true);
                int rowIndex = 0;

                for (var objEntry : objs.entrySet()) {
                    String objName = objEntry.getKey();
                    ReplayObject obj = objEntry.getValue();
                    if (obj == null) continue;

                    // COMBINED ROW
                    {
                        // Map all ms timestamps and the keyframes that land on said timestamp.
                        Int2ObjectMap<Set<Pair<KeyframeReference, Keyframe>>> combinedKeys =
                                new Int2ObjectOpenHashMap<>();
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

                            // Add to map of all frames with a keyframe on them (used for header)
                            keyTimes.add(msEntry.getIntKey());
                        }


                        if (drawKeyChannel(drawData, rowIndex, (selIdx, button) -> {
                            boolean isCtrl = ImGui.getIO().getKeyCtrl();
                            if (selIdx != null) {
                                boolean itemSelected = drawData[selIdx].selected;
                                var pairs = combinedKeys.get(drawData[selIdx].ms);

                                if (button == 0) { // Left Click
                                    if (isCtrl) {
                                        if (!itemSelected) {
                                            for (var pair : pairs) selectedKeys.selectKeyframe(pair.key());
                                        } else {
                                            for (var pair : pairs) selectedKeys.deselectKeyframe(pair.key());
                                        }
                                    } else {
                                        if (!itemSelected) {
                                            selectedKeys.deselectAll();
                                            for (var pair : pairs) selectedKeys.selectKeyframe(pair.key());
                                        }
                                    }
                                } else if (button == 1) { // Right Click
                                    if (!itemSelected) {
                                        if (!isCtrl) {
                                            selectedKeys.deselectAll();
                                        }
                                        for (var pair : pairs) selectedKeys.selectKeyframe(pair.key());
                                    }
                                }
                            } else {
                                if (button == 0 && !isCtrl) {
                                    selectedKeys.deselectAll();
                                }
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
                            boolean isCtrl = ImGui.getIO().getKeyCtrl();
                            String chName = chEntry.getKey();

                            if (selIdx != null) {
                                boolean itemSelected = drawData[selIdx].selected;

                                if (button == 0) { // Left Click
                                    if (isCtrl) {
                                        if (!itemSelected) {
                                            selectedKeys.selectKeyframe(objName, chName, selIdx);
                                        } else {
                                            selectedKeys.deselectKeyframe(objName, chName, selIdx);
                                        }
                                    } else {
                                        if (!itemSelected) {
                                            selectedKeys.deselectAll();
                                            selectedKeys.selectKeyframe(objName, chName, selIdx);
                                        }
                                    }
                                } else if (button == 1) { // Right Click
                                    if (!itemSelected) {
                                        if (!isCtrl) {
                                            selectedKeys.deselectAll();
                                        }
                                        selectedKeys.selectKeyframe(objName, chName, selIdx);
                                    }
                                }
                            } else {
                                if (button == 0 && !isCtrl) {
                                    selectedKeys.deselectAll();
                                }
                            }
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
                        drawList.addLine(pixelInGlobal, graphY, pixelInGlobal, graphY + graphHeight,
                                ImGui.getColorU32(ImGuiCol.Separator));
                        drawList.addRectFilled(graphX, graphY, pixelInGlobal, graphY + graphHeight,
                                ImGui.getColorU32(ImGuiCol.ModalWindowDimBg));
                    }

                    if (pixelOut < graphWidth) {
                        float pixelOutGlobal = pixelOut + graphX;
                        drawList.addLine(pixelOutGlobal, graphY, pixelOutGlobal, graphY + graphHeight,
                                ImGui.getColorU32(ImGuiCol.Separator));
                        drawList.addRectFilled(pixelOutGlobal, graphY, graphX + graphWidth,
                                graphY + graphHeight, ImGui.getColorU32(ImGuiCol.ModalWindowDimBg));
                    }

                }
                drawList.popClipRect();

            }
            ImGui.endGroup();
            float renderedGraphWidth = ImGui.getItemRectSizeX();
            boolean graphHovered = ImGui.isItemHovered();

            /// === FITTING ===
            if (wantsFit) {
                Vector2d bounds = new Vector2d();

                // Don't need over-optimization cause it's only called when fitting
                List<Keyframe> keys;
                if (selectedKeys.isEmpty()) {
                    keys = StreamSupport.stream(KeySelectionSet.iterateAllHandles(objs).spliterator(), false)
                            .map(KeyHandleReference::keyRef)
                            .distinct()
                            .map(k -> k.get(objs))
                            .filter(Objects::nonNull)
                            .toList();
                } else {
                    keys = selectedKeys.getSelectedKeyframes().stream()
                            .filter(k -> objs.containsKey(k.objectName()))
                            .map(k -> k.get(objs))
                            .filter(Objects::nonNull)
                            .toList();
                }

                if (computeTimeBounds(keys, bounds)) {
                    setOffsetX(bounds.x);
                    setZoomFactorX((float) (renderedGraphWidth / (bounds.y - getOffsetX())));
                }
            }

            /// === RIGHT CLICK ===
            boolean rightClicked = ImGui.isItemClicked(ImGuiMouseButton.Right);
            if (rightClicked && hoveringAnyKey) {
                ImGui.openPopup("contextMenu");
            }

            if (ImGui.beginPopup("contextMenu")) {
                KeyframePanel.keyContextMenu(editor, editor.getKeySelection().effectiveSelectedHandles());
                ImGui.endPopup();
            }

            /// === ZOOM ===
            if (graphHovered) {
                float mWheel = ImGui.getIO().getMouseWheel();
                if (mWheel != 0) {
                    float mouseGlobalX = ImGui.getMousePosX();
                    double mouseXMs = pixelXToMs(mouseGlobalX - graphX);
                    float factor = (float) Math.pow(2, mWheel * .125);
                    setZoomFactorX(getZoomFactorX() * factor, mouseXMs);
                }
                // zoom logic
            } else {
                float scrollSpeed = ImGui.getTextLineHeightWithSpacing() * 3f;
                ImGui.setScrollY(ImGui.getScrollY() - ImGui.getIO().getMouseWheel() * scrollSpeed);
            }

            /// === PANNING ===
            if (ImGui.isMouseDragging(2)) {
                if (panStartPos == null && ImGui.isWindowHovered(ImGuiHoveredFlags.ChildWindows)) {
                    panStartPos = getOffsetX();
                }

                if (panStartPos != null) {
                    double dx = ImGui.getMouseDragDeltaX(2) / getZoomFactorX();
                    setOffsetX(panStartPos - dx);
                }
            } else {
                panStartPos = null;
            }

            /// === BOX SELECT ===
            // Start/stop
            {
                if (!isBoxSelecting() && !isDragging() && !isScrubbing() && !hoveringAnyKey
                        && graphHovered
                        && ImGui.isMouseClicked(0)) {

                    boxSelectStart = ImGui.getMousePos();

                    if (!ImGui.getIO().getKeyCtrl()) {
                        selectedKeys.deselectAll();
                    }
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
//        ImGui.endChild();

        /// === DRAGGING ===
        if (isDragging()) {
            if (mouseDragging) {
                float dx = ImGui.getMouseDragDeltaX();
                double rawDeltaMs = dx / getZoomFactorX();
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
                                .collect(Collectors.toSet());

                        double bestDistPx = Double.MAX_VALUE;
                        double bestSnapMs = 0;

                        for (KeyChannel channel : obj.getChannels().values()) {
                            for (Keyframe candidate : channel.getKeyframes()) {
                                if (draggingFrames.contains(candidate)) continue;

                                double distPx = Math.abs((candidate.getTime() - anchorMs) * getZoomFactorX());
                                if (distPx < SNAP_THRESHOLD_PX && distPx < bestDistPx) {
                                    bestDistPx = distPx;
                                    bestSnapMs = candidate.getTime();
                                }
                            }
                        }

                        if (bestDistPx < Double.MAX_VALUE) {
                            snapAdjustMs = bestSnapMs - anchorMs;
                        } else if (snapKeyframes.get()) {
                            double snapInterval = TimelineHeader.computeMajorInterval(getZoomFactorX()) / INTERVAL_DIVISOR;
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
            /**
             * The global location of the smallest key drag offset at the time of drag start in ms
             */
            smallestKeyDragOffset = null;

            selectedKeys.forSelectedKeyframes(ref -> {
                KeyChannel chan = ref.channelRef().get(scene.getObjects());
                if (chan == null || chan.isLocked()) return;
                Keyframe keyframe = chan.getKeyframes().get(ref.keyIndex());

                // Store each key's original time as its drag base.
                double offset = keyframe.getTime();
                keyDragOffsets.put(ref, offset);

                // Track whichever selected key sits closest to the mouse cursor
                double distToMouse = Math.abs(keyframe.getTime() - mouseMs);
                if (smallestKeyDragOffset == null
                        || distToMouse < Math.abs(smallestKeyDragOffset.offset() - mouseMs)) {
                    smallestKeyDragOffset = new KeyOffsetPair(ref, offset);
                }
            });
        }

        wantsFit = false;
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

        float lineWidth = ImGui.getContentRegionAvailX();
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
                    onClick.accept(hovered, 0);
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

        return switch (handleTypes[0]) {
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
            case CIRCLE -> drawList.addCircle(x, y, radius, color, 0, radius / 1.5f);
            case CIRCLE_FILLED -> drawList.addCircleFilled(x, y, radius, color);
        }
    }

    private float msToPixelX(double ms) {
        return (float) ((ms - getOffsetX()) * getZoomFactorX());
    }

    private double pixelXToMs(float pixel) {
        return pixel / getZoomFactorX() + getOffsetX();
    }

    private static int replaceAlpha(int colorArgb, int newAlpha) {
        newAlpha &= 0xFF;
        return (colorArgb & 0x00FFFFFF) | (newAlpha << 24);
    }

    private static boolean computeTimeBounds(Collection<? extends Keyframe> selected, Vector2d dest) {
        if (selected.size() < 2) return false;
        boolean foundAny = false;
        for (var key : selected) {

            if (!foundAny) {
                dest.set(key.getCenter());
                foundAny = true;
            } else {
                dest.x = Math.min(key.getTime(), dest.x);
                dest.y = Math.max(key.getTime(), dest.y);
            }
        }

        return foundAny;
    }

    private static String t(String key) {
        return Language.getInstance().get(key) + "###" + key;
    }
}