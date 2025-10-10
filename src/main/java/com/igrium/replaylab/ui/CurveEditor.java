package com.igrium.replaylab.ui;

import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.ReplayScene.KeyHandleReference;

import imgui.type.ImInt;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class CurveEditor {

    /**
     * The X pan amount in timeline space
     */
    @Getter @Setter
    private double offsetX;

    /**
     * The Y pan amount in timeline space
     */
    @Getter @Setter
    private double offsetY;


    @Getter
    private float zoomFactorX = 0.1f;
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

    /**
     * Draw the curve editor.
     *
     * @param scene    The scene to edit. Keyframes will be updated as the user changes them.
     * @param selected All keyframe handles which are currently selected.
     *                 Updated as the user selects/deselects keyframes.
     * @param playhead Current playhead position. Updated as the player scrubs.
     * @param flags    Render flags.
     */
    public void drawCurveEditor(ReplayScene scene, Set<KeyHandleReference> selected, @Nullable ImInt playhead, int flags) {
        updatedObjects.clear();

        // Compute tick intervals

    }
}
