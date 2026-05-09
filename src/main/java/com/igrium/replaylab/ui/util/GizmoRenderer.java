package com.igrium.replaylab.ui.util;

import com.igrium.craftui.app.CraftApp;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.CommitObjectUpdateOperator;
import com.igrium.replaylab.scene.obj.ReplayObject;
import imgui.extension.imguizmo.ImGuizmo;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.experimental.UtilityClass;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@UtilityClass @Accessors(fluent = true)
public class GizmoRenderer {

    @Getter
    private static final Vector3d cameraPos = new Vector3d();
    @Getter
    private static final Matrix4f viewMatrix = new Matrix4f();
    @Getter
    private static final Matrix4f projectionMatrix = new Matrix4f();

    @Getter @Accessors(fluent = true)
    public enum TransformMode {

        NONE(false, false, false),
        FREE(true, true, true),
        LOC(true, false, false),
        ROT(false, true, false),
        SCALE(false, false, true);

        final boolean showLocation;
        final boolean showRotation;
        final boolean showScale;

        TransformMode(boolean showLocation, boolean showRotation, boolean showScale) {
            this.showLocation = showLocation;
            this.showRotation = showRotation;
            this.showScale = showScale;
        }
    }

    public static void setupCameraProjection(Matrix4fc positionMatrix, Matrix4fc projectionMatrix, Camera camera) {
        Vec3d camPos = camera.getPos();
        GizmoRenderer.viewMatrix().set(positionMatrix);
        GizmoRenderer.projectionMatrix().set(projectionMatrix);
        GizmoRenderer.cameraPos().set(camPos.x, camPos.y, camPos.z);
    }

    public static void drawGizmos(EditorState editorState, CraftApp.ViewportBounds viewportBounds) {
        // TODO: this should really be initialized in CraftUI
        ImGuizmo.setOrthographic(false);
        ImGuizmo.beginFrame();

        ImGuizmo.setDrawList();
        int screenHeight = MinecraftClient.getInstance().getWindow().getHeight();
        ImGuizmo.setRect(
                viewportBounds.x(),
                screenHeight - viewportBounds.y() - viewportBounds.height(),
                viewportBounds.width(),
                viewportBounds.height()
        );
        ImGuizmo.allowAxisFlip(false);

        boolean hidden = MinecraftClient.getInstance().currentScreen != null;
        int numObjects = editorState.getScene().getObjects().size();

        List<String> wantUndoStep = new ArrayList<>(numObjects);
        List<ReplayObject> wantUpdateScene = new ArrayList<>(numObjects);

        for (var obj : editorState.getScene().getObjects().values()) {
            var state = obj.drawGizmos(editorState, cameraPos, viewMatrix, projectionMatrix, hidden);
            if (state.wantsInsertKeyframe()) {
                obj.insertKey(editorState.getPlayhead());
            }
            if (state.wantsUndoStep()) {
                wantUndoStep.add(obj.getId());
            }
            if (state.wantsUpdateScene()) {
                wantUpdateScene.add(obj);
            }
        }

        if (!wantUpdateScene.isEmpty()) {
            editorState.applyToGame(o -> !wantUpdateScene.contains(o));
        }

        if (!wantUndoStep.isEmpty()) {
            editorState.applyOperator(new CommitObjectUpdateOperator(wantUndoStep));
        }

    }
}
