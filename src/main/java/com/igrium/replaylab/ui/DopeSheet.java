package com.igrium.replaylab.ui;

import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImFloat;
import it.unimi.dsi.fastutil.floats.Float2IntAVLTreeMap;
import it.unimi.dsi.fastutil.floats.Float2IntMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.*;

public class DopeSheet {
    /**
     * Don't allow editing
     */
    public static final int READONLY = 1;

    /**
     * If set, snap keyframes to increments of 1 while editing.
     */
    public static final int SNAP_KEYS = 2;

    /**
     * Don't draw the header
     */
    public static final int NO_HEADER = 4;

    /**
     * Don't draw the playhead
     */
    public static final int NO_PLAYHEAD = 8;

    /**
     * Do not allow the playhead to be moved manually by the user
     */
    public static final int READONLY_PLAYHEAD = 16;

    /**
     * Snap the playhead to increments of 1 while scrubbing
     */
    public static final int SNAP_PLAYHEAD = 32;

    /**
     * Don't draw ticks
     */
    public static final int NO_TICKS = 64;

    public static final int DRAW_IN_POINT = 128;
    public static final int DRAW_OUT_POINT = 256;

    public record KeyChannel(String name, List<ImFloat> keys) {};
    public record KeyChannelCategory(String name, List<KeyChannel> channels) {}

    public record KeyReference(int category, int channel, int key) {
        public @Nullable ImFloat getValue(List<KeyChannelCategory> categories) {
            if (category < 0 || channel < 0 || key < 0 || category >= categories.size()) {
                return null;
            }
            var cat = categories.get(category);
            if (channel >= cat.channels.size()) {
                return null;
            }

            var ch = cat.channels.get(channel);

            if (key >= ch.keys.size()) {
                return null;
            }

            return ch.keys().get(key);
        }
    }


    @Getter
    private float zoomFactor = 32;

    public void setZoomFactor(float zoomFactor) {
        if (zoomFactor <= 0) {
            throw new IllegalArgumentException("Zoom factor must be greater than 0");
        }
        this.zoomFactor = zoomFactor;
    }

    @Getter @Setter
    private float startFrame;

    @Getter
    private float size = 512;

    public void setSize(float size) {
        if (size < 0) {
            throw new IllegalArgumentException("Size may not be negative");
        }
        this.size = size;
    }

    /**
     * A map of keys being dragged with their time before the drag.
     */
    private final Map<KeyReference, Float> keyDragOffsets = new HashMap<>();

    /** When the playhead is being dragged */
    @Nullable
    private Float playheadDragOffset = null;

    private boolean mouseWasDragging;
    /** true if the mouse started dragging this frame */
    private boolean mouseStartedDragging;

    private final IntSet openCategories = new IntArraySet();

    public void drawDopeSheet(List<KeyChannelCategory> categories, Set<KeyReference> selected,
                                  float inPoint, float outPoint, @Nullable ImFloat playhead, int flags) {
        if (ImGui.isMouseDragging(0)) {
            mouseStartedDragging = !mouseWasDragging;
            mouseWasDragging = true;
        } else {
            mouseStartedDragging = false;
            mouseWasDragging = false;
        }

        ImDrawList drawList = ImGui.getWindowDrawList();

        if (!hasFlag(NO_HEADER, flags)) {
            ImGui.text("TODO: Implement header");
        }

        ImGui.beginChild("Dope Sheet Data", ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY(), false);

        drawChannelList(categories, openCategories);
        ImGui.sameLine();

        ImGui.beginGroup();
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX());
        drawKeyframes(categories, openCategories, selected, inPoint, outPoint, playhead, flags);
        ImGui.popItemWidth();
        ImGui.endGroup();

        ImGui.endChild();

    }

    private void drawChannelList(List<KeyChannelCategory> categories, IntSet openCategories) {
        ImGui.pushID("channels");
        ImGui.beginGroup();

        int catIndex = 0;
        for (var cat : categories) {
            ImGui.setNextItemOpen(openCategories.contains(catIndex));

            boolean catOpen = ImGui.treeNodeEx(cat.name);

            // If the tree item was toggled, add or remove it to openCategories depending on it's state.
            if (ImGui.isItemToggledOpen() && !openCategories.remove(catIndex)) {
                openCategories.add(catIndex);
            }

            if (catOpen) {
                for (var ch : cat.channels()) {
                    if (ImGui.treeNodeEx(ch.name(), ImGuiTreeNodeFlags.Leaf)) {
                        ImGui.treePop();
                    }
                }
                ImGui.treePop();
            }

            catIndex++;
        }

        ImGui.endGroup();
        ImGui.popID();
    }

    private record IntPair(int a, int b) {};

    // A mapping between key indices in the category row and which keyframes they represent in the channel rows
    // Store here so we don't need to re-allocate each frame
//    private Map<Integer, Set<IntPair>> channelKeyMapping = new HashMap<>();
    private final List<Set<IntPair>> categoryKeyRefs = new ArrayList<>();
    private final Float2IntMap keyIndexCache = new Float2IntAVLTreeMap();
    private final List<ImFloat> categoryKeys = new ArrayList<>();

    private void drawKeyframes(List<KeyChannelCategory> categories, IntSet openCategories, Set<KeyReference> selected,
                               float inPoint, float outPoint, @Nullable ImFloat playhead, int flags) {

        ImDrawList drawList = ImGui.getWindowDrawList();

        int rowIndex = 0;
        boolean wantsStartDragging = false;
        for (int catIndex = 0; catIndex < categories.size(); catIndex++) {
            // Build ref cache
            KeyChannelCategory category = categories.get(catIndex);
            categoryKeyRefs.clear();
            keyIndexCache.clear();
            categoryKeys.clear();

            // Index all channel keyframes
            for (int chIndex = 0; chIndex < category.channels().size(); chIndex++) {
                KeyChannel channel = category.channels().get(chIndex);

                for (int keyIndex = 0; keyIndex < channel.keys().size(); keyIndex++) {
                    float keyTime = channel.keys().get(keyIndex).get();
                    int categoryIndex;
                    Set<IntPair> keyRefs;
                    if (keyIndexCache.containsKey(keyTime)) {
                        categoryIndex = keyIndexCache.get(keyTime);
                        keyRefs = categoryKeyRefs.get(categoryIndex);
                    } else {
                        keyRefs = new HashSet<>();
                        categoryIndex = categoryKeyRefs.size();
                        categoryKeyRefs.add(keyRefs);
                        categoryKeys.add(new ImFloat(keyTime));
                        keyIndexCache.put(keyTime, categoryIndex);
                    }

                    keyRefs.add(new IntPair(chIndex, keyIndex));
                }
            }

            // Draw category
            int catIndexCopy = catIndex; // I hate lambdas
            if (drawKeyChannel(categoryKeys, rowIndex, keyIndex -> {
                for (var ref : categoryKeyRefs.get(keyIndex)) {
                    if (selected.contains(new KeyReference(catIndexCopy, ref.a(), ref.b()))) {
                        return true;
                    }
                }
                return false;
            }, keyIndex -> {
                if (!ImGui.getIO().getKeyCtrl()) {
                    selected.clear();
                }
                if (keyIndex != null) {
                    for (var ref : categoryKeyRefs.get(keyIndex)) {
                        selected.add(new KeyReference(catIndexCopy, ref.a(), ref.b()));
                    }
                }
            }, drawList, flags)) {
                wantsStartDragging = true;
            }
            rowIndex++;

            // Draw individual channels
            if (openCategories.contains(catIndex)) {
                for (int chIndex = 0; chIndex < category.channels().size(); chIndex++) {
                    KeyChannel channel = category.channels.get(chIndex);

                    int chIndexCopy = chIndex;
                    if (drawKeyChannel(channel.keys(), rowIndex,
                            keyIndex -> selected.contains(new KeyReference(catIndexCopy, chIndexCopy, keyIndex)),
                            keyIndex -> {
                                if (!ImGui.getIO().getKeyCtrl()) {
                                    selected.clear();
                                }
                                if (keyIndex != null) {
                                    selected.add(new KeyReference(catIndexCopy, chIndexCopy, keyIndex));
                                }
                            }, drawList, flags)) {
                        wantsStartDragging = true;
                    };

                    rowIndex++;
                }
            }
        }
    }

    private static <K, T> T clearOrCreate(Map<K, T> map, K key, Supplier<T> factory, Consumer<T> clearFunction) {
        return map.compute(key, (k, v) -> {
            if (v != null) {
                clearFunction.accept(v);
                return v;
            } else {
                v = factory.get();
                return v;
            }
        });
    }

    private interface BiFloatFunction<T> {
        T apply(float a, float b);
    }

    /**
     * Draw a single channel in the dope sheet.
     *
     * @param keys       List of key positions.
     * @param rowIndex   Vertical index of the channel in the list.
     * @param isSelected A predicate to check if the key at a given index in keys is selected.
     * @param onClick    Called when the user clicks on the channel, optionally with the key that was clicked on.
     * @param drawList   Draw list to use.
     * @param flags      Render flags.
     * @return true if the user started dragging the selected keys.
     */
    private boolean drawKeyChannel(List<ImFloat> keys, int rowIndex, IntPredicate isSelected, Consumer<Integer> onClick, ImDrawList drawList, int flags) {
        ImGui.pushID("Dope Channel " + rowIndex);

        float lineWidth = ImGui.calcItemWidth();
        float lineHeight = ImGui.getFrameHeight();

        float cursorX = ImGui.getCursorScreenPosX();
        float cursorY = ImGui.getCursorScreenPosY();

        // Background
        int color = rowIndex % 2 == 0 ? ImColor.rgba(1, 1, 1, .05f) : ImColor.rgba(.5f, .5f, .5f, .05f);

        drawList.addRectFilled(cursorX, cursorY, cursorX + lineWidth, cursorY + lineHeight, color);

        float keySize = ImGui.getFontSize();
        float keyRadius = keySize / 2;
        float centerY = cursorY + lineHeight / 2;

        BiFloatFunction<Integer> getHoveredKey = (posX, posY) -> {
            int i = 0;
            for (var keyTime : keys) {
                float centerX = cursorX + keyTime.get() * zoomFactor;
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
                onClick.accept(hovered);
            }
        }

        int i = 0;
        for (ImFloat keyTime : keys) {
            boolean selected = isSelected.test(i);
            int keyColor = selected ? ImColor.rgb(1f, 1f, 1f) : ImColor.rgb(.5f, .5f, .5f);

            float centerX = cursorX + keyTime.get() * zoomFactor;
            drawList.addNgonFilled(centerX, centerY, keyRadius, keyColor, 4);
            i++;
        }

        ImGui.popID();
        return wantsStartDragging;
    }

    private static boolean hasFlag(int flag, int flags) {
        return (flags & flag) == flag;
    }
}
