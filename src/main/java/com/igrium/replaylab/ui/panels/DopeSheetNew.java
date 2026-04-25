package com.igrium.replaylab.ui.panels;

import com.igrium.replaylab.ReplayLab;
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
import com.igrium.replaylab.ui.ReplayLabUI;
import com.igrium.replaylab.ui.util.ChannelList;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import com.igrium.replaylab.ui.util.TimelineHeader;
import imgui.*;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import it.unimi.dsi.fastutil.ints.*;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class DopeSheetNew extends UIPanel {

    private static final float SNAP_THRESHOLD_PX = 4f;
    private static final float INTERVAL_DIVISOR = 4f;

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
        this.zoomFactor = zoomFactor;
    }

    /**
     * Modify the zoom of the editor on the X axis, centering it around a supplied point.
     *
     * @param targetZoom New zoom factor.
     * @param center     Point to center around (ms)
     */
    public void setZoomFactorX(float targetZoom, double center) {
        if (targetZoom == this.zoomFactor) return;

        double newOffsetX = center - (center - offsetX) * (this.zoomFactor / targetZoom);
        this.zoomFactor = targetZoom;
        this.offsetX = newOffsetX;
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
            editorState.applyOperator(new RemoveKeyframesOperator(editorState.getKeySelection().getSelectedKeyframes()));
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
        int minorIntervalX = majorIntervalX / 2;

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
        ImGui.beginChild("main", ImGui.getContentRegionAvailX(), -1, false);
        {
            ///  === CHANNEL LIST ===
            ImGui.beginGroup();
            var expandedObjects = ChannelList.drawChannelList(scene, objs, 192);
            ImGui.endGroup();
            float channelListHeight = ImGui.getItemRectSizeY();
            ImGui.sameLine();
            headerCursorX = ImGui.getCursorPosX() + ImGui.getStyle().getItemSpacing().x;

            /// === KEYFRAMES ===
            ImGui.beginGroup();
            {
                float graphX = ImGui.getCursorScreenPosX();
                float graphY = ImGui.getCursorScreenPosY();
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
                        Int2ObjectMap<Set<KeyframeReference>> combinedKeys = new Int2ObjectOpenHashMap<>();
                        for (var chEntry : obj.getChannels().entrySet()) {
                            var chan = chEntry.getValue();
                            for (int i = 0; i < chan.getKeyframes().size(); i++) {
                                var key = chan.getKeyframes().get(i);
                                var set = combinedKeys.computeIfAbsent(key.getTimeInt(), v -> new HashSet<>());
                                set.add(new KeyframeReference(objName, chEntry.getKey(), i));
                            }
                        }
                        int[] keyMsList = combinedKeys.keySet().toIntArray();
                        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                        IntPredicate isSelected = keyIdx -> {
                            for (var ref : combinedKeys.get(keyMsList[keyIdx])) {
                                if (selectedKeys.isKeyframeSelected(ref))
                                    return true;
                            }
                            return false;
                        };
                        if (drawKeyChannel(keyMsList, rowIndex, isSelected, (selIdx, button) -> {
                            if (button != 0) return; // TODO: right-click

                            if (selIdx == null) {
                                if (!ImGui.getIO().getKeyCtrl()) selectedKeys.deselectAll();
                                return;
                            }

                            boolean isCtrl = ImGui.getIO().getKeyCtrl();
                            boolean wasSelected = isCtrl && isSelected.test((int) selIdx);
                            var refs = combinedKeys.get(keyMsList[selIdx]);

                            if (!isCtrl) {
                                selectedKeys.deselectAll();
                            }
                            for (var ref : refs) {
                                selectedKeys.selectKeyframe(ref);
                            }

                        }, drawList)) {
                            wantStartDragging = true;
                        }
                    }

                    rowIndex++;

                    // INDIVIDUAL CHANNELS
                    if (!expandedObjects.contains(objName)) continue;

                    for (var chEntry : obj.getChannels().entrySet()) {
                        int[] keyMsList = chEntry.getValue().getKeyframes()
                                .stream()
                                .mapToInt(Keyframe::getTimeInt)
                                .toArray();

                        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                        IntPredicate isSelected = keyIdx ->
                                selectedKeys.isKeyframeSelected(objName, chEntry.getKey(), keyIdx);

                        if (drawKeyChannel(keyMsList, rowIndex, isSelected, (selIdx, button) -> {
                            if (button != 0) return; // TODO: right-click

                            if (selIdx == null) {
                                if (!ImGui.getIO().getKeyCtrl()) selectedKeys.deselectAll();
                                return;
                            }

                            boolean isCtrl = ImGui.getIO().getKeyCtrl();
                            String chKey = chEntry.getKey();

                            // TODO: Ctrl deselect (broken because of dragging)
                            if (!isCtrl) {
                                selectedKeys.deselectAll();
                            }
                            selectedKeys.selectKeyframe(objName, chKey, selIdx);
                        }, drawList)) {
                            wantStartDragging = true;
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
                        drawList.addLine(pixelInGlobal, graphY, pixelInGlobal, graphY + graphHeight, ImGui.getColorU32(ImGuiCol.Separator));
                        drawList.addRectFilled(graphX, graphY, pixelInGlobal, graphY + graphHeight, ImGui.getColorU32(ImGuiCol.ModalWindowDimBg));
                    }

                    if (pixelOut < graphWidth) {
                        float pixelOutGlobal = pixelOut + graphX;
                        drawList.addLine(pixelOutGlobal, graphY, pixelOutGlobal, graphY + graphHeight, ImGui.getColorU32(ImGuiCol.Separator));
                        drawList.addRectFilled(pixelOutGlobal, graphY, graphX + graphWidth, graphY + graphHeight, ImGui.getColorU32(ImGuiCol.ModalWindowDimBg));
                    }
                }

            }
            ImGui.endGroup();


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
        }
        ImGui.endChild();
        // TODO: I don't wanna do the math for box selection right now...

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

    private boolean drawKeyChannel(int[] keys, int rowIndex, IntPredicate isSelected, BiConsumer<Integer, Integer> onClick, ImDrawList drawList) {
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
                float centerX = msToPixelX(key) + cursorX;
//                float centerX = cursorX + key * zoomFactor;
                if (centerX - keyRadius - 2 < posX && posX < centerX + keyRadius + 2
                        && centerY - keyRadius - 2 <= posY && posY <= centerY + keyRadius + 2) {
                    return i;
                }
                i++;
            }
            return null;
        };

        ImGui.invisibleButton("##canvas", lineWidth, lineHeight);
        boolean wantsStartDragging = false;

        float mx = ImGui.getMousePosX();
        float my = ImGui.getMousePosY();

        Integer hovered = getHoveredKey.apply(mx, my);

        if (ImGui.isItemHovered()) {
            if (mouseStartedDragging && hovered != null) {
                wantsStartDragging = true;
            } else if (ImGui.isMouseClicked(0)) {
                onClick.accept(hovered, 0);
            } else if (ImGui.isMouseClicked(1)) {
                onClick.accept(hovered, 1);
            }
        }

        for (int i = 0; i < keys.length; i++) {
            int keyMs = keys[i];
            boolean selected = isSelected.test(i);
            int keyColor = selected ? ImColor.rgb(1f, 1f, 1f) : ImColor.rgb(.5f, .5f, .5f);
            drawList.addNgonFilled(cursorX + msToPixelX(keyMs), centerY, keyRadius, keyColor, 4);
        }

        ImGui.popID();
        return wantsStartDragging;
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
