package com.igrium.replaylab.ui;

import com.igrium.craftui.app.CraftApp;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.scene.obj.ReplayObject3D;
import imgui.extension.imguizmo.ImGuizmo;
import imgui.extension.imguizmo.flag.Mode;
import imgui.extension.imguizmo.flag.Operation;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4d;
import org.joml.Matrix4f;

@UtilityClass
public class SceneGizmos {
    @Getter
    private static final Matrix4f viewMatrix = new Matrix4f();
    @Getter
    private static final Matrix4f projectionMatrix = new Matrix4f();

    private static final float[] viewArray = new float[16];
    private static final float[] projectionArray = new float[16];
    private static final float[] modelArray = new float[16];

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

        viewMatrix.get(viewArray);
        projectionMatrix.get(projectionArray);

        Matrix4d tmpMatrix = new Matrix4d();
        Matrix4f modelMatrix = new Matrix4f(tmpMatrix);

//        for (var obj : editorState.getScene().getObjects().values()) {
//            if (obj instanceof ReplayObject3D threeD) {
//                tmpMatrix.identity();
//                threeD.getCombinedTransform(tmpMatrix);
//                modelMatrix.set(tmpMatrix);
//                modelMatrix.get(modelArray);
//                ImGuizmo.manipulate(viewArray, projectionArray, Operation.TRANSLATE, Mode.LOCAL, modelArray);
//            }
//        }
    }
}
