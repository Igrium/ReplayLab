package com.igrium.replaylab.ui.gizmos;

import com.igrium.craftui.app.CraftApp;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.CommitObjectUpdateOperator;
import com.igrium.replaylab.scene.obj.ObjectEditState;
import com.igrium.replaylab.scene.obj.ReplayObject;
import imgui.extension.imguizmo.ImGuizmo;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.experimental.UtilityClass;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

@UtilityClass @Accessors(fluent = true)
public class GizmoRenderer {

    @Getter
    private static final Vector3d cameraPos = new Vector3d();
    @Getter
    private static final Matrix4f viewMatrix = new Matrix4f();
    @Getter
    private static final Matrix4f projectionMatrix = new Matrix4f();

    // TODO: Fix gizmo projection matrix issue. I've been working on this for three days, and I'm tired.
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

            if (hasFlag(state, ObjectEditState.UPDATE_SCENE)) {
                editorState.applyToGame(hasFlag(state, ObjectEditState.RESAMPLE) ? o -> true : o -> o != obj);
            }
            if (hasFlag(state, ObjectEditState.CREATE_UNDO_STEP)) {
                editorState.applyOperator(new CommitObjectUpdateOperator(obj.getId()), false);
            }
        }

//        if (!wantUpdateScene.isEmpty()) {
//            editorState.applyToGame(o -> !wantUpdateScene.contains(o));
//        }
//
//        if (!wantUndoStep.isEmpty()) {
//            editorState.applyOperator(new CommitObjectUpdateOperator(wantUndoStep));
//        }
    }

    private final float[] viewMatrixRender = new float[16];
    private final float[] projectionMatrixRender = new float[16];
    private final float[] modelMatrixRender = new float[16];
    private final float[] deltaMatrixRender = new float[16];

    /**
     * Wrapper over {@link ImGuizmo#manipulate} which uses joml matrices
     *
     * @param viewMatrix       target camera view
     * @param projectionMatrix target camera projection
     * @param operation        target operation
     * @param mode             target mode
     * @param modelMatrix      model matrix
     * @param deltaMatrix      delta matrix
     */
    public static void manipulate(Matrix4fc viewMatrix, Matrix4fc projectionMatrix,
                                  int operation, int mode,
                                  Matrix4f modelMatrix, @Nullable Matrix4f deltaMatrix) {
        viewMatrix.get(viewMatrixRender);
        projectionMatrix.get(projectionMatrixRender);
        modelMatrix.get(modelMatrixRender);
        if (deltaMatrix != null) deltaMatrix.get(deltaMatrixRender);

        ImGuizmo.manipulate(viewMatrixRender, projectionMatrixRender,
                operation, mode,
                modelMatrixRender, deltaMatrixRender);

        modelMatrix.set(modelMatrixRender);
        if (deltaMatrix != null) deltaMatrix.set(deltaMatrixRender);
    }

    /**
     * Wrapper over {@link ImGuizmo#manipulate} which uses joml matrices
     *
     * @param viewMatrix       target camera view
     * @param projectionMatrix target camera projection
     * @param operation        target operation
     * @param mode             target mode
     * @param modelMatrix      model matrix
     */
    public static void manipulate(Matrix4fc viewMatrix, Matrix4fc projectionMatrix,
                                  int operation, int mode, Matrix4f modelMatrix) {
        manipulate(viewMatrix, projectionMatrix, operation, mode, modelMatrix, null);
    }

    private static boolean hasFlag(int flags, int flag) {
        return (flags & flag) != 0;
    }
}
