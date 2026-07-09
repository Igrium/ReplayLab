package com.igrium.replaylab.ui.util;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiMouseButton;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A list of UI controls that can be re-ordered via drag/drop.
 * <p>
 * Begin a re-orderable list with {@link #begin(String)} and end it with {@link #end()}.
 * To draw an item, use {@link #beginItem(String)} and {@link #endItem()}. The latter returns an integer.
 * If it's greater than or equal to zero, this indicates the new index in the list this item should
 * be moved to (the caller is responsible for actually mutating its backing list).
 * <p>
 * When the user starts dragging an item, a little black line will appear between the two items it
 * will be placed between. This black line always appears in the closest space to the mouse cursor.
 * <p>
 * The result is reported via an immediate callback (ImGui convention): {@code endItem} returns the
 * target index only on the frame the drag is released, and only for the item that was dragged.
 * All other calls return {@code -1}.
 * <p>
 * Persistent drag state is keyed by the string ID passed to {@link #begin(String)}, mirroring
 * ImGui's own ID-keyed storage, so this class exposes a purely static API.
 */
public class DraggableList {

    /**
     * Persistent, per-list state. Keyed by the hashed list ID so the same visual list keeps its
     * drag state across frames.
     */
    private static class State {
        /** The item the user pressed down on but may not yet be dragging. -1 if none. */
        int pressedIndex = -1;
        /** The item currently being dragged. -1 if none. */
        int draggingIndex = -1;
        /** Display label of the item currently being dragged, shown floating by the cursor. */
        String draggingLabel;

        /** Item geometry captured on the previous complete frame. */
        final List<Float> prevMinYs = new ArrayList<>();
        final List<Float> prevMaxYs = new ArrayList<>();

        // --- Per-frame scratch ---
        int currentIndex;
        float itemMinY;
        float listMinX;
        float listMaxX;
        final List<Float> minYs = new ArrayList<>();
        final List<Float> maxYs = new ArrayList<>();
    }

    private static final Int2ObjectMap<State> STATES = new Int2ObjectOpenHashMap<>();

    /** Stack of active lists, to support nesting. */
    private static final Deque<State> STACK = new ArrayDeque<>();

    /**
     * Begin a re-orderable list. Must be paired with {@link #end()}.
     *
     * @param id A unique ID for this list. As with ImGui labels, text after a {@code ##} marker is
     *           used for the ID but not displayed.
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
     * Begin an item. Draw the item's contents between this and {@link #endItem()}.
     *
     * @param label Display label for this item. Shown floating by the cursor while the item is
     *              dragged. Text after a {@code ##} marker is stripped, as with ImGui labels.
     */
    public static void beginItem(String label) {
        State state = STACK.peek();
        if (state == null) {
            throw new IllegalStateException("Mismatched start/end");
        }
        state.itemMinY = ImGui.getCursorScreenPosY();

        // Capture the label of the item being dragged so end() can render it near the cursor.
        if (state.currentIndex == state.draggingIndex) {
            state.draggingLabel = label != null ? label.substring(0, findRenderedTextEnd(label)) : null;
        }

        ImGui.pushID(state.currentIndex);
        ImGui.beginGroup();
    }

    /**
     * End an item.
     *
     * @return On the frame the drag is released, and only for the item that was being dragged,
     * the new index that item should be moved to. {@code -1} in all other cases (including when the
     * item is dropped back into its original position).
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

        boolean hovering = ImGui.isMouseHoveringRect(state.listMinX, min, state.listMaxX, max);

        // Track which item the press started on.
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left) && hovering) {
            state.pressedIndex = index;
        }

        // Promote a press into a drag once the mouse actually moves.
        if (state.pressedIndex == index && state.draggingIndex == -1
                && ImGui.isMouseDragging(ImGuiMouseButton.Left)) {
            state.draggingIndex = index;
        }

        int result = -1;

        // On release, report the target index for the dragged item.
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

        // Clear stale press/drag state if the mouse was released outside any item.
        if (ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            state.pressedIndex = -1;
            state.draggingIndex = -1;
        }

        // Draw drag feedback while dragging.
        if (state.draggingIndex >= 0 && !state.minYs.isEmpty()) {
            ImDrawList drawList = ImGui.getWindowDrawList();

            // (1) Highlight the dragged item's row in place, so it's clear what's moving.
            if (state.draggingIndex < state.minYs.size()) {
                float dragMinY = state.minYs.get(state.draggingIndex);
                float dragMaxY = state.maxYs.get(state.draggingIndex);

                drawList.addRect(state.listMinX, dragMinY, state.listMaxX, dragMaxY,
                        ImGui.getColorU32(ImGuiCol.DragDropTarget));
            }

            // Insertion line at the gap closest to the cursor.
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

            // (3) Floating label following the cursor.
            if (state.draggingLabel != null && !state.draggingLabel.isEmpty()) {
                drawFloatingLabel(state.draggingLabel);
            }
        }

        // Swap geometry into the previous-frame buffers.
        state.prevMinYs.clear();
        state.prevMinYs.addAll(state.minYs);
        state.prevMaxYs.clear();
        state.prevMaxYs.addAll(state.maxYs);
    }

    /**
     * Compute the insertion slot [0, n] closest to the mouse cursor, given item bounds.
     */
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

    /**
     * Convert an insertion slot into the resulting index of the dragged item, or -1 if it didn't move.
     */
    private static int resolveTargetIndex(int slot, int draggedIndex) {
        // Removing the dragged item shifts everything after it down by one.
        int target = (slot > draggedIndex) ? slot - 1 : slot;
        return (target == draggedIndex) ? -1 : target;
    }

    /**
     * Draw a small floating label near the cursor, mirroring an ImGui tooltip's look.
     */
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

        // Combine with seed. We don't use crc32 here — Java's String.hashCode
        // is used instead, per instructions. This produces different values
        // than the C++ version, but that's fine for our purposes.
        int hash = toHash.hashCode();
        return hash;
    }
}