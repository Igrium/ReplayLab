package com.igrium.replaylab.ui.panels;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.editor.KeySelectionSet.KeyframeReference;
import com.igrium.replaylab.editor.KeySelectionSet;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.key.KeyChannel;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.ui.ReplayLabIcons;
import com.igrium.replaylab.ui.util.ChannelList;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import com.igrium.replaylab.ui.util.TimelineHeader;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2dc;

import java.util.*;

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
            ImGui.beginGroup();
            ImGui.text("Replay Controls");
            ImGui.text("Lione");
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
}
