package com.igrium.replaylab.ui;

import com.igrium.replaylab.scene.KeyChannelCategory;
import com.igrium.replaylab.scene.KeyframeManifest;
import com.igrium.replaylab.scene.KeyframeManifest.KeyReference;
import com.igrium.replaylab.scene.Keyframe;
import com.igrium.replaylab.scene.KeyChannel;
import com.igrium.replaylab.util.IntBruteSet;
import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImFloat;
import it.unimi.dsi.fastutil.floats.Float2IntAVLTreeMap;
import it.unimi.dsi.fastutil.floats.Float2IntMap;
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

    private boolean mouseWasDragging;
    /** true if the mouse started dragging this frame */
    private boolean mouseStartedDragging;

    private boolean isPlayheadDragging;

    private final IntSet openCategories = new IntBruteSet();

    private void startDragging(Set<KeyReference> selected, KeyframeManifest manifest) {
        for (var ref : selected) {
            Keyframe keyframe = manifest.getKeyframe(ref);
            if (keyframe != null) {
                keyDragOffsets.put(ref, keyframe.getTime());
            }
        }
    }

    /**
     * Draw the dope sheet.
     *
     * @param manifest The keyframe manifest to edit. Keyframes <em>relative to the timeline's in point</em>
     *                   and will be updated as the user changes them
     * @param selected   All keyframes which are currently selected. Updated as the player selects/deselects keyframes.
     * @param length     Length of the timeline (outPoint - inPoint)
     * @param playhead   The current playhead position. Updated as the player scrubs.
     * @param flags      Render flags.
     */
    public void drawDopeSheet(KeyframeManifest manifest, Set<KeyReference> selected,
                              float length, @Nullable ImFloat playhead, int flags) {
        if (ImGui.isMouseDragging(0)) {
            mouseStartedDragging = !mouseWasDragging;
            mouseWasDragging = true;
        } else {
            mouseStartedDragging = false;
            mouseWasDragging = false;
            isPlayheadDragging = false;
        }

        float headerCursorX = 0;
        float headerCursorY = 0;

        ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 0);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, ImGui.getStyle().getItemSpacingX(), 0);

        float headerHeight = ImGui.getTextLineHeight() * 2f;

        if (!hasFlag(NO_HEADER, flags)) {
            headerCursorX = ImGui.getCursorPosX();
            headerCursorY = ImGui.getCursorPosY();
            ImGui.dummy(0, headerHeight);
        }


        ImGui.beginChild("Dope Sheet Data", ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY(), false);

        drawChannelList(manifest, openCategories);
        float catGroupWidth = ImGui.getItemRectSizeX() + ImGui.getStyle().getItemSpacingX();
        float catGroupHeight = ImGui.getItemRectSizeY();
        ImGui.sameLine();

        ImGui.beginChild("KeyList", ImGui.getContentRegionAvailX(), catGroupHeight + ImGui.getStyle().getScrollbarSize(),
                false, ImGuiWindowFlags.HorizontalScrollbar);
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX());
        boolean wantStartDragging = drawKeyframes(manifest, openCategories, selected, length, playhead, flags);

        // Handle dragging
        if (!keyDragOffsets.isEmpty()) {
            if (ImGui.isMouseDragging(0, 0)) {
                float dx = ImGui.getMouseDragDeltaX() / zoomFactor;
                for (var entry : keyDragOffsets.entrySet()) {
                    var key = manifest.getKeyframe(entry.getKey());
                    if (key != null) {
                        float tPrime = entry.getValue() + dx;
                        if (hasFlag(SNAP_KEYS, flags)) {
                            tPrime = Math.round(tPrime);
                        }
                        key.setTime(tPrime);
                    }
                }
            } else {
                keyDragOffsets.clear();
            }
        } else if (wantStartDragging && !hasFlag(READONLY, flags)) {
            startDragging(selected, manifest);
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
            ImGui.setCursorPosX(headerCursorX + catGroupWidth);
            ImGui.setCursorPosY(headerCursorY);
            drawHeader(headerHeight, timelineScrollAmount, length, 20, playhead, catGroupHeight, flags);
        }

        ImGui.popStyleVar();
        ImGui.popStyleVar();
    }

    private static float computeMajorTimeSpacing(float emPerSecond, int targetEmInterval, int multiple) {
        double idealSeconds = targetEmInterval / emPerSecond;
        double log2 = Math.log(idealSeconds) / Math.log(multiple);
        double roundedPower = Math.round(log2);
        return (float) Math.pow(multiple, roundedPower);
    }

    /**
     * Draw the header
     *
     * @param headerHeight   Vertical height of the header (pixels).
     * @param scrollAmount   Distance the timeline has been scrolled in pixels.
     * @param length         Total length of the timeline.
     * @param tps            Number of timeline ticks in a second.
     * @param playhead       The current playhead position. Updated as the player scrubs.
     * @param channelsHeight Total height of the channel list. Used for drawing the playhead line.
     * @param flags          Render flags
     */
    private void drawHeader(float headerHeight, float scrollAmount, float length,
                            int tps, @Nullable ImFloat playhead, float channelsHeight, int flags) {

        float width = ImGui.getContentRegionAvailX();

        float cursorX = ImGui.getCursorScreenPosX();
        float cursorY = ImGui.getCursorScreenPosY();

        ImDrawList drawList = ImGui.getWindowDrawList();

        drawList.addRectFilled(cursorX, cursorY, cursorX + width, cursorY + headerHeight, 0x40000000);
        drawList.pushClipRect(cursorX, cursorY, cursorX + width, cursorY + headerHeight);

        ImGui.invisibleButton("#header", width, headerHeight);

        float em = ImGui.getFontSize();
        float zoomFactor = getZoomFactor(); // pixels per tick
        float emPerSecond = (zoomFactor * tps) / em; // em per second

        float majorInterval = computeMajorTimeSpacing(emPerSecond, 8, 10);

        // TODO: only render intervals that are present in frame

        int outSecond = (int) Math.ceil(length / (float) tps);

        if (!hasFlag(NO_TICKS, flags)) {
            // Major Intervals
            for (float sec = 0; sec <= outSecond; sec += majorInterval) {
                float pos = sec * tps * zoomFactor - scrollAmount;

                String str;
                if (majorInterval < 1) {
                    str = String.format("%.2f", sec);
                } else {
                    str = Integer.toString(Math.round(sec));
                }

                ImVec2 strLen = ImGui.calcTextSize(str);
                drawList.addText(cursorX + (pos - strLen.x / 2f), cursorY, 0xFFFFFFFF, str);

                // Major tick
                drawList.addLine(cursorX + pos, cursorY + headerHeight / 1.8f, cursorX + pos, cursorY + headerHeight, 0xAAAAAAAA);
            }

            // Minor ticks
            float minorInterval = majorInterval / 2;
            for (float sec = 0; sec <= outSecond; sec += minorInterval) {
                float pos = sec * tps * zoomFactor - scrollAmount;
                drawList.addLine(cursorX + pos, cursorY + headerHeight / 1.4f, cursorX + pos, cursorY + headerHeight, 0xAAAAAAAA);
            }

            // Don't bother drawing tiny ticks if they're too small
            float tinyInterval = majorInterval / 4;
            if (tinyInterval * tps * zoomFactor > em * 1.2) {
                for (float sec = 0; sec <= outSecond; sec += tinyInterval) {
                    float pos = sec * tps * zoomFactor - scrollAmount;
                    drawList.addLine(cursorX + pos, cursorY + headerHeight / 1.2f, cursorX + pos, cursorY + headerHeight, 0xAAAAAAAA);
                }
            }
        }
        drawList.popClipRect();

        // Playhead
        if (playhead != null && !hasFlag(NO_PLAYHEAD, flags)) {
            int color = ImGui.getColorU32(ImGuiCol.Tab) | 0xFF000000;

            if (!hasFlag(READONLY_PLAYHEAD, flags) && ImGui.isItemHovered() && ImGui.isMouseDown(0)) {
                isPlayheadDragging = true;
            }

            if (isPlayheadDragging) {
                float newPlayhead = (ImGui.getMousePosX() + scrollAmount - cursorX) / zoomFactor;

                if (newPlayhead < 0)
                    newPlayhead = 0;
                else if (newPlayhead > length)
                    newPlayhead = length;

                playhead.set(newPlayhead);
                color = ImGui.getColorU32(ImGuiCol.TabHovered) | 0xFF000000;
            }

            float playheadX = cursorX + playhead.get() * zoomFactor - scrollAmount;
            float radius = ImGui.getFontSize() / 2f;

            if (playheadX >= cursorX) {
//                var fgDrawList = ImGui.getForegroundDrawList();

                drawList.addRectFilled(playheadX - radius, cursorY + headerHeight / 2, playheadX + radius, cursorY + headerHeight, color);
                drawList.addLine(playheadX, cursorY + headerHeight, playheadX, cursorY + headerHeight + channelsHeight, color);
            }
        }
    }


    private void drawChannelList(KeyframeManifest manifest, IntSet openCategories) {
        ImGui.pushID("channels");
        ImGui.beginGroup();

        int catIndex = 0;
        for (var cat : manifest.getCategories()) {
            ImGui.setNextItemOpen(openCategories.contains(catIndex));

            ImGui.alignTextToFramePadding();
            boolean catOpen = ImGui.treeNodeEx(cat.getName());

            // If the tree item was toggled, add or remove it to openCategories depending on it's state.
            if (ImGui.isItemToggledOpen() && !openCategories.remove(catIndex)) {
                openCategories.add(catIndex);
            }

            if (catOpen) {
                for (var ch : cat.getChannels()) {
                    ImGui.alignTextToFramePadding();
                    if (ImGui.treeNodeEx(ch.getName(), ImGuiTreeNodeFlags.Leaf)) {
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

    // Dummy keyframes used to display category header
    private final List<Keyframe> categoryKeys = new ArrayList<>();

    private boolean drawKeyframes(KeyframeManifest manifest, IntSet openCategories, Set<KeyReference> selected,
                                  float length, @Nullable ImFloat playhead, int flags) {

        ImDrawList drawList = ImGui.getWindowDrawList();
        int rowIndex = 0;
        boolean wantStartDragging = false;
        for (int catIndex = 0; catIndex < manifest.getCategories().size(); catIndex++) {
            // Build ref cache
            KeyChannelCategory category = manifest.getCategories().get(catIndex);
            categoryKeyRefs.clear();
            keyIndexCache.clear();
            categoryKeys.clear();

            // Index all channel keyframes
            for (int chIndex = 0; chIndex < category.getChannels().size(); chIndex++) {
                KeyChannel channel = category.getChannels().get(chIndex);

                for (int keyIndex = 0; keyIndex < channel.getKeys().size(); keyIndex++) {
                    float keyTime = channel.getKeys().get(keyIndex).getTime();
                    int categoryIndex;
                    Set<IntPair> keyRefs;
                    if (keyIndexCache.containsKey(keyTime)) {
                        categoryIndex = keyIndexCache.get(keyTime);
                        keyRefs = categoryKeyRefs.get(categoryIndex);
                    } else {
                        keyRefs = new HashSet<>();
                        categoryIndex = categoryKeyRefs.size();
                        categoryKeyRefs.add(keyRefs);
                        categoryKeys.add(new Keyframe(keyTime, 0));
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
                for (int chIndex = 0; chIndex < category.getChannels().size(); chIndex++) {
                    KeyChannel channel = category.getChannels().get(chIndex);

                    ImGui.setNextItemWidth(length * zoomFactor);
                    int chIndexCopy = chIndex;
                    if (drawKeyChannel(channel.getKeys(), rowIndex,
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
    private boolean drawKeyChannel(List<Keyframe> keys, int rowIndex, IntPredicate isSelected, Consumer<Integer> onClick, ImDrawList drawList, int flags) {
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
            for (var key : keys) {
                float centerX = cursorX + (key.getTime()) * zoomFactor;
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
        for (Keyframe key : keys) {
            boolean selected = isSelected.test(i);
            int keyColor = selected ? ImColor.rgb(1f, 1f, 1f) : ImColor.rgb(.5f, .5f, .5f);

            float centerX = cursorX + (key.getTime()) * zoomFactor;
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
