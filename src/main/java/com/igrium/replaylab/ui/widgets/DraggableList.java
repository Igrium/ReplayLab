package com.igrium.replaylab.ui.widgets;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiMouseButton;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.experimental.UtilityClass;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A list of UI controls that can be re-ordered via drag/drop.
 * <p>
 * Wrap the list in {@link #begin(String)} / {@link #end()}, and each item in
 * {@link #beginItem(String)} / {@link #endItem()}. {@code endItem} returns -1 normally, or, on the
 * frame a drag is released, the index the dragged item should move to. The caller mutates its own
 * backing list.
 * <p>
 * While dragging, an insertion line is drawn in the gap nearest the cursor.
 * <p>
 * State lives in a static map keyed by the list's string ID, so the whole API is static.
 */
@UtilityClass
public class DraggableList {

    // Per-list state, kept between frames.
    private static class State {
        // Item the mouse pressed down on, -1 if none.
        int pressedIndex = -1;
        // Item being dragged, -1 if none.
        int draggingIndex = -1;
        // Label of the dragged item, drawn next to the cursor.
        String draggingLabel;

        // Item bounds from last frame.
        final List<Float> prevMinYs = new ArrayList<>();
        final List<Float> prevMaxYs = new ArrayList<>();

        // Scratch for the current frame.
        int currentIndex;
        float itemMinY;
        float listMinX;
        float listMaxX;
        final List<Float> minYs = new ArrayList<>();
        final List<Float> maxYs = new ArrayList<>();
    }

    private static final Int2ObjectMap<State> STATES = new Int2ObjectOpenHashMap<>();

    // Stack of active lists, so lists can nest.
    private static final Deque<State> STACK = new ArrayDeque<>();

    /**
     * Begin a re-orderable list. Pair with {@link #end()}.
     *
     * @param id Unique ID for this list. Like ImGui labels, text after {@code ##} is used for the
     *           ID but not shown.
     */
    public static void begin(String id) {
        int idHash = imHashStr(id);
        State state = STATES.computeIfAbsent(imHashStr(id), k -> new State());
        STACK.push(state);

        state.currentIndex = 0;
        state.minYs.clear();
        state.maxYs.clear();
        state.listMinX = ImGui.getCursorScreenPosX();
        state.listMaxX = state.listMinX + ImGui.getContentRegionAvailX();

        ImGui.pushID(id);
    }

    /**
     * Begin an item. Draw its contents between this and {@link #endItem()}.
     *
     * @param label Label for this item, drawn by the cursor while it's dragged. Text after
     *              {@code ##} is stripped, like ImGui labels.
     */
    public static void beginItem(String label) {
        State state = STACK.peek();
        if (state == null) {
            throw new IllegalStateException("Mismatched start/end");
        }
        state.itemMinY = ImGui.getCursorScreenPosY();

        // Remember the dragged item's label so end() can draw it by the cursor.
        if (state.currentIndex == state.draggingIndex) {
            state.draggingLabel = label != null ? label.substring(0, findRenderedTextEnd(label)) : null;
        }

        ImGui.pushID(state.currentIndex);
        ImGui.beginGroup();
    }

    /**
     * End an item.
     *
     * @return The index the dragged item should move to, but only on the frame its drag is released.
     * {@code -1} otherwise, including when the item is dropped back where it started.
     */
    public static int endItem() {
        State state = STACK.peek();

        if (state == null) {
            throw new IllegalStateException("Mismatched start/end");
        }

        ImGui.endGroup();
        ImGui.popID();

        int index = state.currentIndex;
        float min = state.itemMinY;
        float max = ImGui.getCursorScreenPosY();
        state.minYs.add(min);
        state.maxYs.add(max);

        boolean hovering = ImGui.isItemHovered();

        // Remember which item the press landed on.
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left) && hovering) {
            state.pressedIndex = index;
        }

        // Turn a press into a drag once the mouse moves.
        if (state.pressedIndex == index && state.draggingIndex == -1
                && ImGui.isMouseDragging(ImGuiMouseButton.Left)) {
            state.draggingIndex = index;
        }

        int result = -1;

        // Report the target index when the drag is released.
        if (state.draggingIndex == index && ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            int slot = computeInsertionSlot(state.prevMinYs, state.prevMaxYs);
            result = resolveTargetIndex(slot, index);
            state.draggingIndex = -1;
            state.pressedIndex = -1;
        }

        state.currentIndex++;
        return result;
    }

    /**
     * End the re-orderable list.
     */
    public static void end() {
        State state = STACK.pop();
        ImGui.popID();

        if (state == null) {
            throw new IllegalStateException("Mismatched start/end");
        }

        // Clear stale state if the mouse was released outside any item.
        if (ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            state.pressedIndex = -1;
            state.draggingIndex = -1;
        }

        if (state.draggingIndex >= 0 && !state.minYs.isEmpty()) {
            ImDrawList drawList = ImGui.getWindowDrawList();

            // Highlight the dragged row in place.
            if (state.draggingIndex < state.minYs.size()) {
                float dragMinY = state.minYs.get(state.draggingIndex);
                float dragMaxY = state.maxYs.get(state.draggingIndex);

                drawList.addRect(state.listMinX, dragMinY, state.listMaxX, dragMaxY,
                        ImGui.getColorU32(ImGuiCol.DragDropTarget));
            }

            // Insertion line at the gap nearest the cursor.
            int slot = computeInsertionSlot(state.minYs, state.maxYs);
            float lineY;
            if (slot <= 0) {
                lineY = state.minYs.getFirst();
            } else if (slot >= state.minYs.size()) {
                lineY = state.maxYs.getLast();
            } else {
                lineY = (state.maxYs.get(slot - 1) + state.minYs.get(slot)) * 0.5f;
            }
            drawList.addLine(state.listMinX, lineY, state.listMaxX, lineY,
                    ImGui.getColorU32(ImGuiCol.DragDropTarget), 2f);

            // Label following the cursor.
            if (state.draggingLabel != null && !state.draggingLabel.isEmpty()) {
                drawFloatingLabel(state.draggingLabel);
            }
        }

        // Save this frame's bounds for next frame.
        state.prevMinYs.clear();
        state.prevMinYs.addAll(state.minYs);
        state.prevMaxYs.clear();
        state.prevMaxYs.addAll(state.maxYs);
    }

    // Returns the insertion slot [0, n] nearest the cursor, given the item bounds.
    private static int computeInsertionSlot(List<Float> minY, List<Float> maxY) {
        float mouseY = ImGui.getMousePosY();
        int slot = 0;
        for (int i = 0; i < minY.size(); i++) {
            float center = (minY.get(i) + maxY.get(i)) * 0.5f;
            if (mouseY > center) {
                slot = i + 1;
            }
        }
        return slot;
    }

    // Turns an insertion slot into the dragged item's new index, or -1 if it didn't move.
    private static int resolveTargetIndex(int slot, int draggedIndex) {
        // Removing the dragged item shifts everything after it down by one.
        int target = (slot > draggedIndex) ? slot - 1 : slot;
        return (target == draggedIndex) ? -1 : target;
    }

    // Draws a small label by the cursor, styled like an ImGui tooltip.
    private static void drawFloatingLabel(String label) {
        ImDrawList drawList = ImGui.getForegroundDrawList();

        ImVec2 textSize = ImGui.calcTextSize(label);
        float padX = ImGui.getStyle().getFramePaddingX();
        float padY = ImGui.getStyle().getFramePaddingY();

        // Offset down-right of the cursor so the label doesn't sit under the pointer.
        float x = ImGui.getMousePosX() + 16f;
        float y = ImGui.getMousePosY() + 8f;

        drawList.addRectFilled(x, y, x + textSize.x + padX * 2, y + textSize.y + padY * 2,
                ImGui.getColorU32(ImGuiCol.PopupBg));
        drawList.addRect(x, y, x + textSize.x + padX * 2, y + textSize.y + padY * 2,
                ImGui.getColorU32(ImGuiCol.Border));
        drawList.addText(x + padX, y + padY, ImGui.getColorU32(ImGuiCol.Text), label);
    }

    private static int findRenderedTextEnd(String text) {
        int textDisplayEnd = 0;
        int limit = text.length();

        while (textDisplayEnd < limit
                && text.charAt(textDisplayEnd) != '\0'
                && (text.charAt(textDisplayEnd) != '#'
                || textDisplayEnd + 1 >= text.length()
                || text.charAt(textDisplayEnd + 1) != '#')) {
            textDisplayEnd++;
        }

        return textDisplayEnd;
    }


    private static int imHashStr(String data) {
        // Handle the "##" reset behavior: everything before a "##" marker
        // is discarded from the hash, mirroring the original ImHashStr logic.
        String toHash = data;
        int idx;
        while ((idx = toHash.indexOf("##")) != -1) {
            toHash = toHash.substring(idx + 2);
        }

        return toHash.hashCode();
    }
}