package com.igrium.replaylab.ui;

import com.igrium.replaylab.editor.KeySelectionSet;
import com.igrium.replaylab.editor.KeySelectionSet.KeyframeReference;

import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.key.Keyframe;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.ui.util.TimelineHeader;
import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImInt;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.IntPredicate;

public class DopeSheetNew {
    /**
     * The pan amount in milliseconds
     */
    @Getter @Setter
    private double offset;

    /**
     * The amount of pixels per millisecond
     */
    @Getter
    public float zoomFactor = 0.1f;

    /**
     * All the replay objects that have had an update <em>committed</em> this frame.
     * Does not include keyframes being dragged.
     */
    @Getter
    private final Set<String> updatedObjects = new HashSet<>();

    /**
     * The amount of units offset from the mouse that each key being dragged has
     */
    private final Map<KeyframeReference, Double> keyDragOffsets = new HashMap<>();

    private final TimelineHeader header = new TimelineHeader();

    /**
     * All the objects which are expanded this frame
     */
    private final Set<String> openObjects = new HashSet<>();

    // Not null if we're currently panning
    private @Nullable Double panStartPos;

    public void setZoomFactor(float zoomFactor) {
        if (zoomFactor <= 0) {
            throw new IllegalArgumentException("Zoom factor must be greater than zero");
        }
        this.zoomFactor = zoomFactor;
    }

    private interface DrawKeyChLambda {
        void call(List<Keyframe> keys, int rowIdx, IntPredicate isSelected);
    }

    private record AggregateKeyRef(String objName, int keyIdx) {};

    /**
     * Draw the dope sheet.
     *
     * @param scene           The scene to edit. Keyframes will be updated as the user changes them.
     * @param selectedObjects The objects to display the keyframes of. <code>null</code> to display all objects
     * @param selectedKeys    All keyframe handles which are currently selected.
     *                        Updated as the user selects/deselects keyframes.
     * @param playhead        Current playhead position. Updated as the player scrubs.
     * @param flags           Render flags.
     */
    public void drawDopeSheet(ReplayScene scene, @Nullable Collection<String> selectedObjects,
                                KeySelectionSet selectedKeys, @Nullable ImInt playhead, int flags) {
        updatedObjects.clear();
        Collection<String> objs = selectedObjects != null ? selectedObjects : scene.getObjects().keySet();

        int majorIntervalX = (int) TimelineHeader.computeMajorInterval(zoomFactor);
        int minorIntervalX = majorIntervalX / 2;

        ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 0);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, ImGui.getStyle().getItemSpacingX(), 0);

        // Allocate space for header
        float headerCursorY = ImGui.getCursorPosY();
        float headerHeight = ImGui.getTextLineHeight() * 2f;

        ImGui.dummy(0, headerHeight);

        ImGui.beginChild("content", -1, -1, false);

        /// === CHANNEL LIST ===
        ImGui.pushID("channels");
        ImGui.beginGroup();
        {
            openObjects.clear();

            for (String objName : objs) {
                ReplayObject obj = scene.getObject(objName);
                if (obj == null) continue;

                ImGui.alignTextToFramePadding();
                if (ImGui.treeNodeEx(objName)) {
                    openObjects.add(objName);

                    for (String chName : obj.getChannels().keySet()) {
                        ImGui.alignTextToFramePadding();
                        ImGui.text(chName);
                    }
                    ImGui.treePop();
                }
            }
            ImGui.dummy(128, 0); // Force width
        }
        ImGui.endGroup();
        ImGui.popID(); // channels

        KeyframeReference hoveredKeyframe = null;
        AggregateKeyRef hoveredAggregate = null;

        /// === AGGREGATE KEYFRAMES ===
        // Aggregate all keyframes into a single timeline for relevant objects.

        /// === Key List ===
        ImGui.sameLine();
        ImGui.beginChild("KeyList", -1, -1, false);
        {
            float graphX = ImGui.getCursorScreenPosX();

            ImDrawList drawList = ImGui.getWindowDrawList();

            float graphWidth = ImGui.getContentRegionAvailX();

            /// === DRAW KEYFRAMES ===
            DrawKeyChLambda drawKeyCh = (keys, rowIdx, isSelected) -> {
                ImGui.pushID("Dope Channel" + rowIdx);

                float lineWidth = ImGui.calcItemWidth();
                float lineHeight = ImGui.getFrameHeight();

                float cursorY = ImGui.getCursorScreenPosY();

                int bgColor = ImGui.getColorU32(rowIdx % 2 == 0 ? ImGuiCol.TableRowBgAlt : ImGuiCol.TableRowBg);

                drawList.addRectFilled(graphX, cursorY, graphX + lineWidth, cursorY + lineHeight, bgColor);

                float keySize = ImGui.getFontSize();
                float keyRadius = keySize / 2;
                float centerY = cursorY + lineHeight / 2;

                ImGui.invisibleButton("##canvas", lineWidth, lineHeight);

                int i = 0;
                for (Keyframe key : keys) {
                    boolean selected = isSelected.test(i);
                    int keyColor = selected ? ImColor.rgb(1f, 1f, 1f) : ImColor.rgb(.5f, .5f, .5f);

                    float centerX = msToPixelX(key.getTimeInt()) + graphX;
                    drawList.addNgonFilled(centerX, centerY, keyRadius, keyColor, 4);
                    i++;
                }

                ImGui.popID(); // Dope Channel
            };

            float keySize = ImGui.getFontSize();
            float keyRadius = keySize / 2;

            BiPredicate<Double, Float> isKeyHovered = (ms, mouseX) -> {
                float centerX = msToPixelX(ms) + graphX;
                return (centerX - keyRadius - 2 < mouseX && mouseX < centerX + keyRadius + 2);
            };

            float mouseX = ImGui.getMousePosX();

            /// === CHANNELS ===
            int rowIdx = 0;
            for (String objName : objs) {
                ReplayObject obj = scene.getObject(objName);
                if (obj == null) continue;

                // TODO: Draw the aggregate keyframes rather than a placeholder
                ImGui.setNextItemWidth(graphWidth);
                drawKeyCh.call(List.of(), rowIdx, v -> false);
                rowIdx++;

                // Draw individual channels
                if (openObjects.contains(objName)) {
                    for (var chEntry : obj.getChannels().entrySet()) {
                        ImGui.setNextItemWidth(graphWidth);
                        drawKeyCh.call(chEntry.getValue().getKeyframes(), rowIdx,
                                idx -> selectedKeys.isKeyframeSelected(objName, chEntry.getKey(), idx));
                        rowIdx++;

                        // Check hover
                        if (ImGui.isItemHovered()) {
                            int keyIdx = 0;
                            for (Keyframe key : chEntry.getValue().getKeyframes()) {
                                if (isKeyHovered.test(key.getCenter().x, mouseX)) {
                                    hoveredKeyframe = new KeyframeReference(objName, chEntry.getKey(), keyIdx);
                                    break;
                                }
                                keyIdx++;
                            }
                        }
                    }
                }
            }
        }
        ImGui.popStyleVar(2);

        /// === SELECTION ===
        {
            if (ImGui.isMouseClicked(0) && ImGui.isWindowHovered(ImGuiHoveredFlags.ChildWindows | ImGuiHoveredFlags.AllowWhenBlockedByActiveItem)) {
                if (ImGui.getIO().getKeyCtrl()) {
                    if (hoveredKeyframe != null) {
                        if (selectedKeys.isKeyframeSelected(hoveredKeyframe)) {
                            selectedKeys.deselectKeyframe(hoveredKeyframe);
                        } else {
                            selectedKeys.selectKeyframe(hoveredKeyframe);
                        }
                    }
                } else {
                    selectedKeys.deselectAll();
                    if (hoveredKeyframe != null) {
                        selectedKeys.selectKeyframe(hoveredKeyframe);
                    }
                }
            }
        }

        ImGui.endChild(); // KeyList
        ImGui.endChild(); // content
    }

    private float msToPixelX(double ms) {
        return (float) ((ms - offset) * zoomFactor);
    }

    private float pixelXToMs(float pixel) {
        return (float) (pixel / zoomFactor + offset);
    }
}
