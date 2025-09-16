package com.igrium.replaylab.ui;

import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.ReplayScene.KeyReference;
import com.igrium.replaylab.scene.key.Keyframe;
import com.igrium.replaylab.scene.key.KeyChannel;
import com.igrium.replaylab.scene.obj.objs.ReplayObject;
import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;
import it.unimi.dsi.fastutil.ints.Int2IntAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
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

    private static final int TICKS_PER_SECOND = 1000;

    /**
     * The number of pixels per tick
     */
    @Getter
    private float zoomFactor = 1;

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
    @Getter
    private final Map<KeyReference, Integer> keyDragOffsets = new HashMap<>();

    /**
     * <code>true</code> if any keyframes are currently being dragged.
     */
    public boolean isDraggingKeys() {
        return !keyDragOffsets.isEmpty();
    }

    private boolean mouseWasDragging;
    /** true if the mouse started dragging this frame */
    private boolean mouseStartedDragging;

    /**
     * <code>true</code> if the playhead is currently being dragged.
     */
    @Getter
    private boolean draggingPlayhead;

    /**
     * <code>true</code> if the playhead was being dragged but stopped this frame.
     */
    @Getter
    private boolean finishedDraggingPlayhead;

    /**
     * <code>true</code> if the user started dragging keyframes on this frame.
     */
    @Getter
    private boolean startedDraggingKeys;

    @Getter
    private final Set<String> openCategories = new HashSet<>();

    /**
     * All the replay objects that have had an update <em>committed</em> this frame.
     * Does not include keyframes being dragged.
     */
    @Getter
    private final Set<String> updatedObjects = new HashSet<>();

    private void startDragging(Set<KeyReference> selected, ReplayScene scene) {
        for (var ref : selected) {
            Keyframe keyframe = scene.getKeyframe(ref);
            if (keyframe != null) {
                keyDragOffsets.put(ref, keyframe.getTime());
            }
        }
        startedDraggingKeys = true;
    }

    /**
     * Draw the dope sheet.
     *
     * @param scene    The scene to edit. Keyframes <em>relative to the timeline's in point</em>
     *                 and will be updated as the user changes them
     * @param selected All keyframes which are currently selected. Updated as the player selects/deselects keyframes.
     * @param length   Length of the timeline (outPoint - inPoint)
     * @param playhead The current playhead position. Updated as the player scrubs.
     * @param flags    Render flags.
     */
    public void drawDopeSheet(ReplayScene scene, Set<KeyReference> selected,
                              int length, @Nullable ImInt playhead, int flags) {

        finishedDraggingPlayhead = false;
        updatedObjects.clear();

        if (ImGui.isMouseDragging(0)) {
            mouseStartedDragging = !mouseWasDragging;
            mouseWasDragging = true;
        } else {
            mouseStartedDragging = false;
            mouseWasDragging = false;
            if (draggingPlayhead) {
                finishedDraggingPlayhead = true;
            }
            draggingPlayhead = false;
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

        drawChannelList(scene, openCategories);
        float catGroupWidth = ImGui.getItemRectSizeX() + ImGui.getStyle().getItemSpacingX();
        float catGroupHeight = ImGui.getItemRectSizeY();
        ImGui.sameLine();

        ImGui.beginChild("KeyList", ImGui.getContentRegionAvailX(), catGroupHeight + ImGui.getStyle().getScrollbarSize(),
                false, ImGuiWindowFlags.HorizontalScrollbar);
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX());
        boolean wantStartDragging = drawKeyframes(scene, openCategories, selected, length, flags);

        // Handle dragging
        if (!keyDragOffsets.isEmpty()) {
            if (ImGui.isMouseDragging(0, 0)) {
                float dx = ImGui.getMouseDragDeltaX() / zoomFactor;
                for (var entry : keyDragOffsets.entrySet()) {
                    var key = scene.getKeyframe(entry.getKey());
                    if (key != null) {
                        float tPrime = entry.getValue() + dx;
                        if (hasFlag(SNAP_KEYS, flags)) {
                            tPrime = Math.round(tPrime);
                        }
                        key.setTime((int) tPrime);
                    }
                }
            } else {
                updatedObjects.addAll(keyDragOffsets.keySet().stream().map(KeyReference::object).toList());
                keyDragOffsets.clear();
            }
        } else if (wantStartDragging && !hasFlag(READONLY, flags)) {
            startDragging(selected, scene);
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

        ImGui.popStyleVar();
        ImGui.popStyleVar();

        if (!hasFlag(NO_HEADER, flags)) {
            ImGui.setCursorPosX(headerCursorX + catGroupWidth);
            ImGui.setCursorPosY(headerCursorY);
            drawHeader(headerHeight, timelineScrollAmount, length, playhead, catGroupHeight, flags);
        }

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
     * @param playhead       The current playhead position. Updated as the player scrubs.
     * @param channelsHeight Total height of the channel list. Used for drawing the playhead line.
     * @param flags          Render flags
     */
    private void drawHeader(float headerHeight, float scrollAmount, int length,
                            @Nullable ImInt playhead, float channelsHeight, int flags) {

        float width = ImGui.getContentRegionAvailX();

        float cursorX = ImGui.getCursorScreenPosX();
        float cursorY = ImGui.getCursorScreenPosY();

        ImDrawList drawList = ImGui.getWindowDrawList();

        drawList.addRectFilled(cursorX, cursorY, cursorX + width, cursorY + headerHeight, ImGuiCol.TableHeaderBg);
        drawList.pushClipRect(cursorX, cursorY, cursorX + width, cursorY + headerHeight);

        ImGui.invisibleButton("#header", width, headerHeight);

        float em = ImGui.getFontSize();
        float zoomFactor = getZoomFactor(); // pixels per tick
        float emPerSecond = (zoomFactor * TICKS_PER_SECOND) / em; // em per second

        float majorInterval = computeMajorTimeSpacing(emPerSecond, 8, 10);

        // TODO: only render intervals that are present in frame

        int outSecond = Math.ceilDiv(length, TICKS_PER_SECOND);

        if (!hasFlag(NO_TICKS, flags)) {
            // Major Intervals
            for (float sec = 0; sec <= outSecond; sec += majorInterval) {
                float pos = sec * TICKS_PER_SECOND * zoomFactor - scrollAmount;

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
                float pos = sec * TICKS_PER_SECOND * zoomFactor - scrollAmount;
                drawList.addLine(cursorX + pos, cursorY + headerHeight / 1.4f, cursorX + pos, cursorY + headerHeight, 0xAAAAAAAA);
            }

            // Don't bother drawing tiny ticks if they're too small
            float tinyInterval = majorInterval / 4;
            if (tinyInterval * TICKS_PER_SECOND * zoomFactor > em * 1.2) {
                for (float sec = 0; sec <= outSecond; sec += tinyInterval) {
                    float pos = sec * TICKS_PER_SECOND * zoomFactor - scrollAmount;
                    drawList.addLine(cursorX + pos, cursorY + headerHeight / 1.2f, cursorX + pos, cursorY + headerHeight, 0xAAAAAAAA);
                }
            }
        }
        drawList.popClipRect();

        // Playhead

        if (playhead != null && !hasFlag(NO_PLAYHEAD, flags)) {
            int color = ImGui.getColorU32(ImGuiCol.CheckMark) | 0xFF000000;

            if (!hasFlag(READONLY_PLAYHEAD, flags) && ImGui.isItemHovered() && ImGui.isMouseDown(0)) {
                draggingPlayhead = true;
            }

            if (draggingPlayhead) {
                int newPlayhead = (int) ((ImGui.getMousePosX() + scrollAmount - cursorX) / zoomFactor);

                if (newPlayhead < 0)
                    newPlayhead = 0;
                else if (newPlayhead > length)
                    newPlayhead = length;

                playhead.set(newPlayhead);
            }

            ImGui.setCursorPos(0, 0);
            ImGui.beginChild("overlay", ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY(), false,
                    ImGuiWindowFlags.NoBackground | ImGuiWindowFlags.NoInputs);

            float playheadX = cursorX + playhead.get() * zoomFactor - scrollAmount;
            float radius = ImGui.getFontSize() / 2f;

            var drawList2 = ImGui.getWindowDrawList();
            if (playheadX >= cursorX) {
                drawList2.addLine(playheadX, cursorY + headerHeight, playheadX, cursorY + headerHeight + channelsHeight, color);
                drawList2.addRectFilled(playheadX - radius, cursorY + headerHeight / 2, playheadX + radius, cursorY + headerHeight, color);
            }
            ImGui.endChild();

        }

    }


    private void drawChannelList(ReplayScene scene, Set<String> openCategories) {
        ImGui.pushID("channels");

        ImGui.beginGroup();

        int catIndex = 0;
        for (var entry : scene.getObjects().entrySet()) {
            String catName = entry.getKey();
            ReplayObject cat = entry.getValue();
            ImGui.setNextItemOpen(openCategories.contains(catName));

            ImGui.alignTextToFramePadding();
            boolean catOpen = ImGui.treeNodeEx(catName);

            // If the tree item was toggled, add or remove it to openCategories depending on it's state.
            if (ImGui.isItemToggledOpen() && !openCategories.remove(catName)) {
                openCategories.add(catName);
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


        ImGui.dummy(128, 0); //force width
        ImGui.endGroup();
        ImGui.popID();
    }

    private record IntPair(int a, int b) {};

    // A mapping between key indices in the category row and which keyframes they represent in the channel rows
    // Store here so we don't need to re-allocate each frame
    private final List<Set<IntPair>> categoryKeyRefs = new ArrayList<>();
    private final Int2IntMap keyIndexCache = new Int2IntAVLTreeMap();

    // Dummy keyframes used to display category header
    private final List<Keyframe> categoryKeys = new ArrayList<>();

    private boolean drawKeyframes(ReplayScene scene, Set<String> openCategories, Set<KeyReference> selected,
                                  float length, int flags) {

        ImDrawList drawList = ImGui.getWindowDrawList();
        int rowIndex = 0;
        boolean wantStartDragging = false;

        // Since categories are now a Map<String, KeyChannelCategory>
        for (var entry : scene.getObjects().entrySet()) {
            // Build ref cache
            String categoryId = entry.getKey();
            ReplayObject category = entry.getValue();
            categoryKeyRefs.clear();
            keyIndexCache.clear();
            categoryKeys.clear();

            // Index all channel keyframes
            for (int chIndex = 0; chIndex < category.getChannels().size(); chIndex++) {
                KeyChannel channel = category.getChannels().get(chIndex);

                for (int keyIndex = 0; keyIndex < channel.getKeys().size(); keyIndex++) {
                    int keyTime = channel.getKeys().get(keyIndex).getTime();
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
            if (drawKeyChannel(categoryKeys, rowIndex, keyIndex -> {
                for (var ref : categoryKeyRefs.get(keyIndex)) {
                    if (selected.contains(new KeyReference(categoryId, ref.a(), ref.b()))) {
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
                        selected.add(new KeyReference(categoryId, ref.a(), ref.b()));
                    }
                }
            }, drawList, flags)) {
                wantStartDragging = true;
            }
            rowIndex++;

            // Draw individual channels
            if (openCategories.contains(categoryId)) {
                for (int chIndex = 0; chIndex < category.getChannels().size(); chIndex++) {
                    KeyChannel channel = category.getChannels().get(chIndex);

                    ImGui.setNextItemWidth(length * zoomFactor);
                    int chIndexCopy = chIndex;
                    if (drawKeyChannel(channel.getKeys(), rowIndex,
                            keyIndex -> selected.contains(new KeyReference(categoryId, chIndexCopy, keyIndex)),
                            keyIndex -> {
                                if (!ImGui.getIO().getKeyCtrl()) {
                                    selected.clear();
                                }
                                if (keyIndex != null) {
                                    selected.add(new KeyReference(categoryId, chIndexCopy, keyIndex));
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
        int color = rowIndex % 2 == 0 ? ImGui.getColorU32(ImGuiCol.TableRowBgAlt) : ImGui.getColorU32(ImGuiCol.TableRowBg);

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
