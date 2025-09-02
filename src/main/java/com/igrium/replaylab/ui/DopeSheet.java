package com.igrium.replaylab.ui;

import com.igrium.replaylab.util.IntBruteSet;
import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImFloat;
import it.unimi.dsi.fastutil.floats.Float2IntAVLTreeMap;
import it.unimi.dsi.fastutil.floats.Float2IntMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import lombok.Data;
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
    public record ChannelCategory(String name, List<KeyChannel> channels) {}

    public record KeyReference(int category, int channel, int key) {
        public @Nullable ImFloat getValue(List<ChannelCategory> categories) {
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


    /**
     * The number of pixels per tick
     */
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

    private final IntSet openCategories = new IntBruteSet();

    private static int computeMajorTickSpacing(float unitsPerTick, int targetInterval) {
        double idealTicks = targetInterval / unitsPerTick;

        // Round to power of 2
        double log2 = Math.log(idealTicks) / Math.log(2.0);
        double roundedPower = Math.round(log2);
        return Math.max((int) Math.pow(2, roundedPower), 1);
    }

    private void startDragging(Set<KeyReference> selected, List<ChannelCategory> categories) {
        for (var ref : selected) {
            ImFloat val = ref.getValue(categories);
            if (val != null) {
                keyDragOffsets.put(ref, val.floatValue());
            }
        }
    }

    /**
     * Draw the dope sheet.
     *
     * @param categories All the keyframes to render. Keyframes <em>relative to the timeline's in point</em>
     *                   and will be updated as the user changes them
     * @param selected   All keyframes which are currently selected. Updated as the player selects/deselects keyframes.
     * @param inPoint    The in point of the timeline. Cosmetic only.
     * @param length     Length of the timeline (outPoint - inPoint)
     * @param playhead   The current playhead position. Updated as the player scrubs.
     * @param flags      Render flags.
     */
    public void drawDopeSheet(List<ChannelCategory> categories, Set<KeyReference> selected,
                              float inPoint, float length, @Nullable ImFloat playhead, int flags) {
        if (ImGui.isMouseDragging(0)) {
            mouseStartedDragging = !mouseWasDragging;
            mouseWasDragging = true;
        } else {
            mouseStartedDragging = false;
            mouseWasDragging = false;
        }

        float headerCursorX = 0;
        float headerCursorY = 0;

        ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 0);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, ImGui.getStyle().getItemSpacingX(), 0);

        float headerHeight = ImGui.getTextLineHeight() * 1.5f;

        if (!hasFlag(NO_HEADER, flags)) {
            headerCursorX = ImGui.getCursorPosX();
            headerCursorY = ImGui.getCursorPosY();
            ImGui.dummy(0, headerHeight);
        }


        ImGui.beginChild("Dope Sheet Data", ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY(), false);

        drawChannelList(categories, openCategories);
        float catGroupWidth = ImGui.getItemRectSizeX();
        float catGroupHeight = ImGui.getItemRectSizeY();
        ImGui.sameLine();

        ImGui.beginChild("KeyList", ImGui.getContentRegionAvailX(), catGroupHeight + ImGui.getStyle().getScrollbarSize(),
                false, ImGuiWindowFlags.HorizontalScrollbar);
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX());
        boolean wantStartDragging = drawKeyframes(categories, openCategories, selected, inPoint, length, playhead, flags);

        // Handle dragging
        if (!keyDragOffsets.isEmpty()) {
            if (ImGui.isMouseDragging(0, 0)) {
                float dx = ImGui.getMouseDragDeltaX() / zoomFactor;
                for (var entry : keyDragOffsets.entrySet()) {
                    var time = entry.getKey().getValue(categories);
                    if (time != null) {
                        float tPrime = entry.getValue() + dx;
                        if (hasFlag(SNAP_KEYS, flags)) {
                            tPrime = Math.round(tPrime);
                        }
                        time.set(tPrime);
                    }
                }
            } else {
                keyDragOffsets.clear();
            }
        } else if (wantStartDragging && !hasFlag(READONLY, flags)) {
            startDragging(selected, categories);
        }

        // Handle scroll wheel zoom
        float mWheel = ImGui.getIO().getMouseWheel();
        if (mWheel != 0 && ImGui.isWindowHovered()) {
            float factor = (float) Math.pow(2, mWheel * .125f);
            zoomFactor *= factor;
            // TODO: SetItemKeyOwner once it's added to the bindings
        }

        float timelineScrollAmount = ImGui.getScrollX();

        ImGui.endChild();
        ImGui.endChild();

        if (!hasFlag(NO_HEADER, flags)) {
            ImGui.setCursorPosX(headerCursorX);
            ImGui.setCursorPosY(headerCursorY);
            drawHeader(headerHeight, catGroupWidth, timelineScrollAmount, inPoint, length, playhead, flags);
        }

        ImGui.popStyleVar();
        ImGui.popStyleVar();
    }

    private void drawHeader(float headerHeight, float timelineOffset, float scrollAmount, float inPoint, float length,
                            @Nullable ImFloat playhead, int flags) {
        float width = ImGui.getContentRegionAvailX();

        float cursorScreenX = ImGui.getCursorScreenPosX();
        float cursorScreenY = ImGui.getCursorScreenPosY();

        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(cursorScreenX, cursorScreenY, cursorScreenX + width, cursorScreenY + headerHeight,
                ImColor.rgba(0, 0, 0, .25f));

        ImGui.invisibleButton("#header", width, headerHeight);

        float em = ImGui.getFontSize();

        int majorInterval = computeMajorTickSpacing(getZoomFactor() / em, 6);

        int inTick = Math.floorDiv((int) inPoint, majorInterval) * majorInterval;
        int outTick = Math.ceilDiv((int) (inPoint + length), majorInterval) * majorInterval;

        for (int tick = inTick; tick <= outTick; tick += majorInterval) {
            float pos = tick * zoomFactor + timelineOffset - scrollAmount;
            String str = Integer.toString(tick);
            ImVec2 strLen = ImGui.calcTextSize(str);

            drawList.addText(cursorScreenX + (pos - strLen.x / 2f), cursorScreenY, 0xFFFFFFFF, str);
        }
    }

    private void drawChannelList(List<ChannelCategory> categories, IntSet openCategories) {
        ImGui.pushID("channels");
        ImGui.beginGroup();


        int catIndex = 0;
        for (var cat : categories) {
            ImGui.setNextItemOpen(openCategories.contains(catIndex));

            ImGui.alignTextToFramePadding();
            boolean catOpen = ImGui.treeNodeEx(cat.name);

            // If the tree item was toggled, add or remove it to openCategories depending on it's state.
            if (ImGui.isItemToggledOpen() && !openCategories.remove(catIndex)) {
                openCategories.add(catIndex);
            }

            if (catOpen) {
                for (var ch : cat.channels()) {
                    ImGui.alignTextToFramePadding();
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
    private final List<Set<IntPair>> categoryKeyRefs = new ArrayList<>();
    private final Float2IntMap keyIndexCache = new Float2IntAVLTreeMap();
    private final List<ImFloat> categoryKeys = new ArrayList<>();

    private boolean drawKeyframes(List<ChannelCategory> categories, IntSet openCategories, Set<KeyReference> selected,
                                  float inPoint, float length, @Nullable ImFloat playhead, int flags) {

        ImDrawList drawList = ImGui.getWindowDrawList();
        int rowIndex = 0;
        boolean wantStartDragging = false;
        for (int catIndex = 0; catIndex < categories.size(); catIndex++) {
            // Build ref cache
            ChannelCategory category = categories.get(catIndex);
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
            ImGui.setNextItemWidth(length * zoomFactor);
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
                wantStartDragging = true;
            }
            rowIndex++;

            // Draw individual channels
            if (openCategories.contains(catIndex)) {
                for (int chIndex = 0; chIndex < category.channels().size(); chIndex++) {
                    KeyChannel channel = category.channels.get(chIndex);

                    ImGui.setNextItemWidth(length * zoomFactor);
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
                        wantStartDragging = true;
                    }

                    rowIndex++;
                }
            }
        }
        return wantStartDragging;
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
        int color = rowIndex % 2 == 0 ? ImColor.rgba(1, 1, 1, .1f) : ImColor.rgba(.5f, .5f, .5f, .1f);

        drawList.addRectFilled(cursorX, cursorY, cursorX + lineWidth, cursorY + lineHeight, color);

        float keySize = ImGui.getFontSize();
        float keyRadius = keySize / 2;
        float centerY = cursorY + lineHeight / 2;

        BiFloatFunction<Integer> getHoveredKey = (posX, posY) -> {
            int i = 0;
            for (var keyTime : keys) {
                float centerX = cursorX + (keyTime.get()) * zoomFactor;
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

            float centerX = cursorX + (keyTime.get()) * zoomFactor;
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
