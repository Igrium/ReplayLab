package com.igrium.replaylab.operator.scene;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.math.DynamicRotation;
import com.igrium.replaylab.operator.ReplayOperator;
import com.igrium.replaylab.object.ReplayObject;
import com.igrium.replaylab.object.ReplayObject3D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public class CameraToViewOperator implements ReplayOperator {

    private @Nullable ReplayObject3D cameraObject;

    private Vector3d prevPos;
    private Vector3d newPos;

    private DynamicRotation prevRot;
    private DynamicRotation newRot;

    @Override
    public boolean execute(EditorState editor) throws Exception {
        if (editor.isCameraView()) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        Entity camEnt = mc.cameraEntity != null ? mc.cameraEntity : mc.player;
        if (camEnt == null) return false;

        ReplayObject cObj = editor.getScene().getSceneCameraObject();

        if (cObj instanceof ReplayObject3D c3d){
            cameraObject = c3d;
        } else {
            return false;
        }

        prevPos = new Vector3d(cameraObject.position());
        prevRot = new DynamicRotation().set(cameraObject.rotation());

        cameraObject.position().set(camEnt.getX(), camEnt.getEyeY(), camEnt.getZ());
        cameraObject.rotation().setEulerYXZ(org.joml.Math.toRadians(-camEnt.getYaw()), org.joml.Math.toRadians(camEnt.getPitch()), 0);

        newPos = new Vector3d(cameraObject.position());
        newRot = new DynamicRotation().set(cameraObject.rotation());

        editor.setCameraView(true);

        return true;
    }

    @Override
    public void undo(EditorState editor) throws Exception {
        if (cameraObject == null) return;
        editor.setCameraView(false);
        cameraObject.position().set(prevPos);
        cameraObject.rotation().set(prevRot);
    }

    @Override
    public void redo(EditorState editor) throws Exception {
        if  (cameraObject == null) return;
        cameraObject.position().set(newPos);
        cameraObject.rotation().set(newRot);
    }
}
