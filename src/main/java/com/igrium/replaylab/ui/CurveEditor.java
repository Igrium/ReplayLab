package com.igrium.replaylab.ui;

import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.ReplayScene.KeyHandleReference;

import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.ui.util.TimelineFlags;
import com.igrium.replaylab.ui.util.TimelineHeader;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
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


        int majorIntervalX = (int) TimelineHeader.computeMajorInterval(zoomFactorX);
        int minorIntervalX = majorIntervalX / 2;

        float majorIntervalY = TimelineHeader.computeMajorInterval(zoomFactorY);
        float minorIntervalY = majorIntervalY / 2;

        float headerCursorY = ImGui.getCursorPosY();
        float headerHeight = ImGui.getTextLineHeight() * 2f;

        if (!hasFlag(TimelineFlags.NO_HEADER, flags)) {
            ImGui.dummy(0, headerHeight);
        }

        Collection<String> objs = selectedObjects != null ? selectedObjects : scene.getObjects().keySet();

        // === CHANNEL LIST ===

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

        ImGui.sameLine();
        float headerCursorX = ImGui.getCursorPosX();
        float graphHeight = ImGui.getContentRegionAvailY();

        // === GRAPH ===

        if (ImGui.beginChild("keygraph", ImGui.getContentRegionAvailX(), graphHeight, false)) {
            // === SCROLL ===
            if (ImGui.isWindowHovered()) {
                float mWheel = ImGui.getIO().getMouseWheel();
                if (mWheel != 0) {
                    float factor = (float) Math.pow(2, mWheel * .125);

                    if (ImGui.getIO().getKeyCtrl()) {
                        zoomFactorY *= factor;
                    } else {
                        zoomFactorX *= factor;
                    }
                }
            }

            float graphX = ImGui.getCursorScreenPosX();
            float graphY = ImGui.getCursorScreenPosY();

            float gWidth = ImGui.getContentRegionAvailX();
            float gHeight = ImGui.getContentRegionAvailY();

            ImDrawList drawList = ImGui.getWindowDrawList();

            // === BACKGROUND ===
            drawList.addRectFilled(graphX, graphY, graphX + gWidth, graphY + gHeight, ImGui.getColorU32(ImGuiCol.FrameBg));

            // Amount of milliseconds the graph is wide
            float widthMs = gWidth / zoomFactorX;
            int startTick = Math.floorDiv((int) offsetX, majorIntervalX) * majorIntervalX;
            int endTick = Math.ceilDiv((int) (offsetX + widthMs), majorIntervalX) * majorIntervalX;

            int colMajor = replaceAlpha(ImGui.getColorU32(ImGuiCol.Text), 48);
            int colMinor = replaceAlpha(colMajor, 16);

            // X intervals
            for (int ms = startTick; ms <= endTick; ms+= minorIntervalX) {
                float xPos = msToPixelX(ms) + graphX;
                int color = ms % majorIntervalX == 0 ? colMajor : colMinor;
                drawList.addLine(xPos, graphY, xPos, graphY + gHeight, color);
            }

            // Amount of units the graph is tall
            float heightUnits = gHeight / zoomFactorY;

            double startValue = Math.floor(offsetY / majorIntervalY) * majorIntervalY;
            double endValue = Math.ceil((offsetY + heightUnits) / majorIntervalY) * majorIntervalY;

            // Y intervals
            for (double value = startValue; value <= endValue; value += minorIntervalY) {
                float yPos = valueToPixelY(value) + graphY;
                int color = value % majorIntervalY == 0 ? colMajor : colMinor;
                drawList.addLine(graphX, yPos, graphX + gWidth, yPos, color);
            }
        }
        ImGui.endChild();

        // === HEADER ===
        if (!hasFlag(TimelineFlags.NO_HEADER, flags)) {
            ImGui.setCursorPosX(headerCursorX);
            ImGui.setCursorPosY(headerCursorY);
            header.drawHeader(headerHeight, zoomFactorX, (float) offsetX, scene.getLength(), playhead, graphHeight, flags);
        }

    }

    private float msToPixelX(float ms) {
        return (float) ((ms - offsetX) * zoomFactorX);
    }

    private float pixelXToMs(float pixel) {
        return (float) (pixel / zoomFactorX + offsetX);
    }

    private float valueToPixelY(double value) {
        return (float) ((value - offsetY) * zoomFactorY);
    }

    private double pixelYToValue(double pixel) {
        return pixel / zoomFactorY + offsetY;
    }

    private static boolean hasFlag(int flag, int flags) {
        return (flags & flag) == flag;
    }

    private static int replaceAlpha(int colorArgb, int newAlpha) {
        newAlpha &= 0xFF;
        return (colorArgb & 0x00FFFFFF) | (newAlpha << 24);
    }
}
