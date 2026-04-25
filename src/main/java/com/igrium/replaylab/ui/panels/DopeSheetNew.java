package com.igrium.replaylab.ui.panels;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.editor.KeySelectionSet;
import com.igrium.replaylab.editor.KeySelectionSet.KeyframeReference;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.key.KeyChannel;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.ui.ReplayLabIcons;
import com.igrium.replaylab.ui.util.ChannelList;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import com.igrium.replaylab.ui.util.TimelineHeader;
import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import it.unimi.dsi.fastutil.floats.FloatList;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntPredicate;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class DopeSheetNew extends UIPanel {

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
     * The global location of the smallest key drag offset at the time of drag start
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

    private boolean mouseWasDragging;
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
    }

    public void drawDopeSheet(ReplayScene scene, @Nullable Collection<String> selectedObjects,
                              KeySelectionSet selectedKeys, @Nullable ImInt playhead) {
        droppedKeys.clear();
        updatedKeys.clear();
        Collection<String> objs = selectedObjects != null ? selectedObjects : scene.getObjects().keySet();

        if (ImGui.isMouseDragging(0)) {
            mouseStartedDragging = !mouseWasDragging;
            mouseWasDragging = true;
        }

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

        /// === MAIN ===
        ImGui.beginChild("main", ImGui.getContentRegionAvailX(), -1, false);
        {

            ///  === CHANNEL LIST ===
            ImGui.beginGroup();
            var visibleChannels = ChannelList.drawChannelList(scene, objs, 192);
            ImGui.endGroup();
            ImGui.sameLine();
            headerCursorX = ImGui.getCursorPosX() + ImGui.getStyle().getItemSpacing().x;

            /// === KEYFRAMES ===
            ImGui.beginGroup();
            {
                ImDrawList drawList = ImGui.getWindowDrawList();
                int rowIndex = 0;
                boolean wantStartDragging = false;

                String currentObjName = null;
                for (var cRef : visibleChannels) {
                    KeyChannel channel = cRef.get(scene.getObjects());
                    if (channel == null) continue; // TODO: leave empty space
                    if (currentObjName == null || !currentObjName.equals(cRef.objectName())) {
                        // Next object
                        currentObjName = cRef.objectName();

                        // Map all ms timestamps and the keyframes that land on that timestamp.
                        // Used to draw the combined keyframe row.
                        Int2ObjectAVLTreeMap<Set<KeyframeReference>> keyIndex = new Int2ObjectAVLTreeMap<>();
                        for (int i = 0; i < channel.getKeyframes().size(); i++) {
                            var key = channel.getKeyframes().get(i);
                            var set = keyIndex.computeIfAbsent(key.getTimeInt(), v -> new HashSet<>());
                            set.add(new KeyframeReference(cRef, i));
                        }

                    }
                }

//                for (String objName : objs) {
//                    ReplayObject object = scene.getObject(objName);
//                    if (object == null) continue; // TODO: leave empty space
//
//                    // A mapping of all ms timestamps and the keyframes that land on that timestamp
//                    // Used for merging keyframes in object headers
//                    Int2ObjectMap<Set<KeyframeReference>> keyIndex = new Int2ObjectAVLTreeMap<>();
//
//                    // We'll iterate over channels twice. Once to build the key index so we can draw the combined channel,
//                    // and another to draw the individual keyframes
//                }
            }
            ImGui.endGroup();
        }
        ImGui.endChild();
//
//        ImGui.sameLine();
//        float headerCursorX = ImGui.getCursorPosX();
//        ImGui.newLine();

        /// === HEADER ===
        ImGui.setCursorPos(headerCursorX, headerCursorY);
        header.drawHeader(headerHeight, zoomFactor, (float) offsetX, scene.getLength(), playhead, graphHeight, 0);
    }

    private boolean drawKeyChannel(IntList keys, int rowIndex, IntPredicate isSelected, BiConsumer<Integer, Integer> onClick, ImDrawList drawList) {
        ImGui.pushID("Dope Channel " + rowIndex);

        float lineWidth = ImGui.calcItemWidth();
        float lineHeight = ImGui.getFrameHeight();

        float cursorX = ImGui.getCursorScreenPosX();
        float cursorY = ImGui.getCursorScreenPosY();

        int color = ImGui.getColorU32(rowIndex % 2 == 0 ? ImGuiCol.TableRowBgAlt : ImGuiCol.TableRowBg);
        drawList.addRectFilled(cursorX, cursorY, cursorX + lineWidth, cursorY + lineHeight, color);

        float keySize = ImGui.getFontSize();
        float keyRadius = keySize / 2;
        float centerY = cursorX + lineHeight / 2;

        BiFunction<Float, Float, Integer> getHoveredKey = (posX, posY) -> {
            int i = 0;
            for (var key : keys) {
                float centerX = cursorX + key * zoomFactor;
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

        int i = 0;
        var iter = keys.intIterator();
        while (iter.hasNext()) {
            var key = iter.nextInt();
            boolean selected = isSelected.test(i);
            int keyColor = selected ? ImColor.rgb(1f, 1f, 1f) : ImColor.rgb(.5f, .5f, .5f);

            float centerX = msToPixelX(key);
            drawList.addNgonFilled(centerX, centerY, keyRadius, keyColor, 4);
            i++;
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
