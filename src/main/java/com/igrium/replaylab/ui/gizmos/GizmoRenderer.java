package com.igrium.replaylab.ui.gizmos;

import com.igrium.craftui.api.app.CraftApp;
import com.igrium.craftui.api.gizmo.GizmoHelper;
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

    public static void drawGizmos(EditorState editorState) {
        // TODO: this should really be initialized in CraftUI
        if (Minecraft.getInstance().gui.hud.isHidden()) return;

        ImGuizmo.allowAxisFlip(false);
        boolean hidden = Minecraft.getInstance().gui.screen() != null;

        Vec3 p = GizmoHelper.getCameraPos();
        Vector3d camPos = new Vector3d(p.x(), p.y(), p.z());
        for (var obj : editorState.getScene().getObjects().values()) {
            var state = obj.drawGizmos(editorState, camPos, GizmoHelper.getViewRotationMatrix(),
                    GizmoHelper.getGizmoProjectionMatrix(), hidden);

            if (hasFlag(state, EditFlags.UPDATE_SCENE)) {
                editorState.applyToGame(hasFlag(state, EditFlags.RESAMPLE) ? o -> true : o -> o != obj);
            }
            if (hasFlag(state, EditFlags.CREATE_UNDO_STEP)) {
                editorState.applyOperator(new CommitObjectUpdateOperator(false, obj.getId()), false);
            }
        }

    }

    private static boolean hasFlag(int flags, int flag) {
        return (flags & flag) != 0;
    }
}
