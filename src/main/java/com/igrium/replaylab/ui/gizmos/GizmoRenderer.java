package com.igrium.replaylab.ui.gizmos;

import com.igrium.craftui.app.CraftApp;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.object.EditFlags;
import com.igrium.replaylab.operator.object.CommitObjectUpdateOperator;
import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.extension.imguizmo.ImGuizmo;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.experimental.UtilityClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;

@UtilityClass @Accessors(fluent = true)
public class GizmoRenderer {

    @Getter
    private static final Vector3d cameraPos = new Vector3d();
    @Getter
    private static final Matrix4f viewMatrix = new Matrix4f();
    @Getter
    private static final Matrix4f projectionMatrix = new Matrix4f();


    public static void setupCameraProjection(Matrix4fc positionMatrix, CameraRenderState cameraState) {
        Vec3 camPos = cameraState.pos;
        GizmoRenderer.viewMatrix().set(positionMatrix);
        GizmoRenderer.cameraPos().set(camPos.x, camPos.y, camPos.z);

        Matrix4f projection = GizmoRenderer.projectionMatrix().set(cameraState.projectionMatrix);

        // Flip depth because MC has matrix backwards apparently
        float near = 0.05f;
        float far = cameraState.depthFar;
        projection.m22((far + near) / (near - far))
                .m32(2 * far * near / (near - far));
    }

    public static void drawGizmos(EditorState editorState, CraftApp.ViewportBounds viewportBounds) {
        // TODO: this should really be initialized in CraftUI
        if (Minecraft.getInstance().gui.hud.isHidden()) return;

        ImGuizmo.setOrthographic(false);
        ImGuizmo.beginFrame();

        ImGuizmo.setDrawList();
        int screenHeight = Minecraft.getInstance().getWindow().getScreenHeight();

        float rectX = viewportBounds.x();
        float rectY = screenHeight - viewportBounds.y() - viewportBounds.height();

        ImGuiViewport vp = ImGui.getMainViewport();
            rectX += vp.getPosX();
            rectY += vp.getPosY();


        ImGuizmo.setRect(rectX, rectY, viewportBounds.width(), viewportBounds.height());
        ImGuizmo.allowAxisFlip(false);

        boolean hidden = Minecraft.getInstance().gui.screen() != null;

        for (var obj : editorState.getScene().getObjects().values()) {
            var state = obj.drawGizmos(editorState, cameraPos, viewMatrix, projectionMatrix, hidden);

            if (hasFlag(state, EditFlags.UPDATE_SCENE)) {
                editorState.applyToGame(hasFlag(state, EditFlags.RESAMPLE) ? o -> true : o -> o != obj);
            }
            if (hasFlag(state, EditFlags.CREATE_UNDO_STEP)) {
                editorState.applyOperator(new CommitObjectUpdateOperator(false, obj.getId()), false);
            }
        }

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
