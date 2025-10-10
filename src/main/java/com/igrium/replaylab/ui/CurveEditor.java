package com.igrium.replaylab.ui;

import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.ReplayScene.KeyHandleReference;

import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.ui.util.TimelineFlags;
import com.igrium.replaylab.ui.util.TimelineHeader;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class CurveEditor {

    /**
     * The X pan amount in milliseconds
     */
    @Getter @Setter
    private double offsetX;

    /**
     * The Y pan amount in curve units
     */
    @Getter @Setter
    private double offsetY;

    /**
     * Amount of pixels per millisecond
     */
    @Getter
    private float zoomFactorX = 0.1f;

    /**
     * Amount of pixels per curve unit
     */
    @Getter
    private float zoomFactorY = 0.1f;

    public void setZoomFactorX(float zoomFactorX) {
        if (zoomFactorX < 0) {
            throw new IllegalArgumentException("Zoom factor must be greater than 0");
        }
        this.zoomFactorX = zoomFactorX;
    }

    public void setZoomFactorY(float zoomFactorY) {
        if (zoomFactorY < 0) {
            throw new IllegalArgumentException("Zoom factor must be greater than 0");
        }
        this.zoomFactorY = zoomFactorY;
    }

    /**
     * All the replay objects that have had an update <em>committed</em> this frame.
     * Does not include keyframes being dragged.
     */
    @Getter
    private final Set<String> updatedObjects = new HashSet<>();

    private final TimelineHeader header = new TimelineHeader();

    public boolean isScrubbing() {
        return header.isScrubbing();
    }

    public boolean stoppedScrubbing() {
        return header.stoppedScrubbing();
    }

    /**
     * Draw the curve editor.
     *
     * @param scene           The scene to edit. Keyframes will be updated as the user changes them.
     * @param selectedObjects The objects to display the keyframes of. <code>null</code> to display all objects
     * @param selectedKeys    All keyframe handles which are currently selected.
     *                        Updated as the user selects/deselects keyframes.
     * @param playhead        Current playhead position. Updated as the player scrubs.
     * @param flags           Render flags.
     */
    public void drawCurveEditor(ReplayScene scene, @Nullable Collection<String> selectedObjects,
                                Set<KeyHandleReference> selectedKeys, @Nullable ImInt playhead, int flags) {
        updatedObjects.clear();

        int majorInterval = (int) TimelineHeader.computeMajorInterval(zoomFactorX);
        int minorInterval = majorInterval / 2;
        int tinyInterval = majorInterval / 4;

        float headerCursorX = ImGui.getCursorPosX();
        float headerCursorY = ImGui.getCursorPosY();
        float headerHeight = ImGui.getTextLineHeight() * 2f;

        if (!hasFlag(TimelineFlags.NO_HEADER, flags)) {
            ImGui.dummy(0, headerHeight);
        }

        Collection<String> objs = selectedObjects != null ? selectedObjects : scene.getObjects().keySet();

        // === CHANNEL LIST ==

        ImGui.pushID("channels");
        ImGui.beginGroup();

        ImBoolean locked = new ImBoolean();
        for (var name : objs) {
            ReplayObject obj = scene.getObject(name);
            if (obj == null)
                continue;

            if (ImGui.treeNodeEx(name)) {
                for (var entry : obj.getChannels().entrySet()) {
                    ImGui.text(entry.getKey());
                    ImGui.sameLine();
                    boolean wasLocked = entry.getValue().isLocked();
                    locked.set(wasLocked);

                    ImGui.checkbox("##" + entry.getKey() + "_lock", locked);
                    if (locked.get() != wasLocked) {
                        entry.getValue().setLocked(locked.get());
                    }
                }
                ImGui.treePop();
            }
        }

        ImGui.dummy(128, 0); // force width
        ImGui.endGroup();
        ImGui.popID();

        headerCursorX += ImGui.getItemRectSizeX();

        // === HEADER ===
        if (!hasFlag(TimelineFlags.NO_HEADER, flags)) {
            ImGui.setCursorPosX(headerCursorX);
            ImGui.setCursorPosY(headerCursorY);
            header.drawHeader(headerHeight, zoomFactorX, (float) offsetX, scene.getLength(), playhead, 0, flags);
        }

    }

    private static boolean hasFlag(int flag, int flags) {
        return (flags & flag) == flag;
    }
}
