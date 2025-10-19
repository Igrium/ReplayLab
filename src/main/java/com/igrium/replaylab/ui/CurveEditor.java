package com.igrium.replaylab.ui;

import com.igrium.replaylab.editor.KeySelectionSet;
import com.igrium.replaylab.editor.KeySelectionSet.KeyHandleReference;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.key.Keyframe;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.ui.util.TimelineFlags;
import com.igrium.replaylab.ui.util.TimelineHeader;
import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2dc;

import java.util.*;

public class CurveEditor {

    /**
     * Get a channel's hue based on its object and index
     *
     * @param objNameHash The hash code of the object name
     * @param chNameHash  The hash code of the channel name
     * @return The hue of the channel as a float from 0-1
     */
    private static float getChannelHue(int objNameHash, int chNameHash) {
        int x = objNameHash * 32 + chNameHash;

        x ^= x << 13;
        x ^= x >>> 17;
        x ^= x << 5;

        // Map to 0-1 range by clamping to 2^24 and dividing
        int top24 = x >>> 8;
        return top24 / (float)(1 << 24);
    }

    private static final int[] curveColors = new int[16];

    static {
        for (int i = 0; i < 16; i++) {
            curveColors[i] = ImColor.hsla(i / 16f, 1f, .5f, .75f);
        }
    }

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

    private final Map<KeyHandleReference, Vector2dc> keyDragOffsets = new HashMap<>();

    private final TimelineHeader header = new TimelineHeader();

    public boolean isScrubbing() {
        return header.isScrubbing();
    }

    public boolean stoppedScrubbing() {
        return header.stoppedScrubbing();
    }

    public boolean isDragging() {
        return !keyDragOffsets.isEmpty();
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
                                KeySelectionSet selectedKeys, @Nullable ImInt playhead, int flags) {
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

        /// === CHANNEL LIST ===

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

        /// === GRAPH ===

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

            ImGui.invisibleButton("##curveGraph", gWidth, gHeight);

            /// === BACKGROUND ===
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


            /// === GRAPH CONTENTS ===
            KeyHandleReference clickedOn = null;
            float mouseX = ImGui.getMousePosX();
            float mouseY = ImGui.getMousePosY();
            boolean mouseClicked = ImGui.isMouseClicked(0);

            int chIndex = 0;
            for (String objName : objs) {
                ReplayObject obj = scene.getObject(objName);
                if (obj == null)
                    continue;

                // For each channel
                for (var chEntry : obj.getChannels().entrySet()) {
                    int chColor = curveColors[chIndex % 16];
                    boolean chSelected = selectedKeys.isChannelSelected(objName, chEntry.getKey());
                    if (!chSelected) {
                        chColor = replaceAlpha(chColor, 64);
                    }

                    // Should be pre-sorted, but we should check
                    Keyframe[] keyArray = chEntry.getValue().getKeyframes().toArray(new Keyframe[0]);

                    // Draw keyframes
                    for (int keyIdx = 0; keyIdx < keyArray.length; keyIdx++) {
                        Keyframe key = keyArray[keyIdx];
                        boolean cSelected = selectedKeys.isHandleSelected(objName, chEntry.getKey(), keyIdx, 0);
                        boolean lSelected = selectedKeys.isHandleSelected(objName, chEntry.getKey(), keyIdx, 1);
                        boolean rSelected = selectedKeys.isHandleSelected(objName, chEntry.getKey(), keyIdx, 2);

                        int color = 0xFF000000;
                        int selColor = ImGui.getColorU32(ImGuiCol.Text);

                        float keyX = msToPixelX((float) key.getCenter().x()) + graphX;
                        float keyY = valueToPixelY(key.getCenter().y()) + graphY;

                        drawList.addCircleFilled(keyX, keyY, 3f, cSelected ? selColor : color);

                        float handleAX = keyX + (float) key.getHandleA().x() * zoomFactorX;
                        float handleAY = keyY + (float) key.getHandleB().y() * zoomFactorY;

                        drawList.addCircle(handleAX, handleAY, 3f, lSelected ? selColor : color);

                        float handleBX = keyX + (float) key.getHandleB().x() * zoomFactorX;
                        float handleBY = keyY + (float) key.getHandleB().y() * zoomFactorY;

                        drawList.addCircle(handleBX, handleBY, 3f, rSelected ? selColor : color);

                        // TODO: Shouldn't this be defined in the theme somehow?
                        int lineColor = 0xFF8E79D1;
                        int lineColorSel = 0xFF93B4FF;

                        drawList.addLine(handleAX, handleAY, keyX, keyY, lSelected || cSelected ? lineColorSel : lineColor);
                        drawList.addLine(handleBX, handleBY, keyX, keyY, rSelected || cSelected ? lineColorSel : lineColor);

                        // Prioritize clicking on the selected channel unless the keyframe being clicked is already selected.
                        // In that case, don't let it be selected again so we can select overlapping keys.
                        if (mouseClicked && (selectedKeys.isChannelSelected(objName, chEntry.getKey()) || clickedOn == null)) {
                            KeyHandleReference handle0Ref = new KeyHandleReference(objName, chEntry.getKey(), keyIdx, 0);
                            KeyHandleReference handle1Ref = new KeyHandleReference(objName, chEntry.getKey(), keyIdx, 1);
                            KeyHandleReference handle2Ref = new KeyHandleReference(objName, chEntry.getKey(), keyIdx, 2);

                            if (keyHovered(keyX, keyY, mouseX, mouseY) && !selectedKeys.isHandleSelected(handle0Ref)) {
                                clickedOn = handle0Ref;
                            } else if (keyHovered(handleAX, handleAY, mouseX, mouseY) && !selectedKeys.isHandleSelected(handle1Ref)) {
                                clickedOn = handle1Ref;
                            } else if (keyHovered(handleBX, handleBY, mouseX, mouseY) && !selectedKeys.isHandleSelected(handle2Ref)) {
                                clickedOn = handle2Ref;
                            }
                        }
                    }

                    // Lines
                    if (keyArray.length <= 1)
                        continue;

                    Arrays.sort(keyArray);

                    for (int i = 0; i < keyArray.length - 1; i++) {
                        Keyframe key = keyArray[i];
                        Keyframe next = keyArray[i + 1];

                        float keyX = msToPixelX((float) key.getCenter().x()) + graphX;
                        float keyY = valueToPixelY(key.getCenter().y()) + graphY;

                        float keyHandleX = keyX + (float) key.getHandleB().x() * zoomFactorX;
                        float keyHandleY = keyY + (float) key.getHandleB().y() * zoomFactorY;

                        float nextX = msToPixelX((float) next.getCenter().x()) + graphX;
                        float nextY = valueToPixelY(next.getCenter().y()) + graphY;

                        float nextHandleX = nextX + (float) next.getHandleA().x() * zoomFactorX;
                        float nextHandleY = nextY + (float) next.getHandleB().y() * zoomFactorY;

                        drawList.addBezierCubic(keyX, keyY, keyHandleX, keyHandleY, nextHandleX, nextHandleY, nextX, nextY,
                                chColor, chSelected ? 2 : 1);
                    }
                    chIndex++;
                }
            }

            if (clickedOn != null) {
                if (!ImGui.getIO().getKeyShift()) {
                    selectedKeys.deselectAll();
                }
                selectedKeys.selectHandle(clickedOn);
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

    private static boolean keyHovered(float keyX, float keyY, float mouseX, float mouseY) {
        float RADIUS = 8f;
        return (keyX - RADIUS <= mouseX && mouseX <= keyX + RADIUS)
                && (keyY - RADIUS <= mouseY && mouseY < keyY + RADIUS);
    }
}
