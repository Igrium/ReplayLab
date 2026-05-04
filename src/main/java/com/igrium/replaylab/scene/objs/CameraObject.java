package com.igrium.replaylab.scene.objs;

import com.igrium.replaylab.ReplayLabEntities;
import com.igrium.replaylab.camera.AnimatedCameraEntity;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.math.Transform3;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.obj.EntityObject;
import com.igrium.replaylab.scene.obj.ReplayObjectType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.SpawnReason;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3dc;

import java.util.Objects;

public class CameraObject extends EntityObject<AnimatedCameraEntity> {

    public CameraObject(ReplayObjectType<?> type, ReplayScene scene) {
        super(type, scene);
    }
//
//    private final Vector3d posCache = new Vector3d();
//    private final Vector3d rotCache = new Vector3d();

    @Override
    protected AnimatedCameraEntity createEntity(ClientWorld world) {
        var ent = ReplayLabEntities.CAMERA.create(world, SpawnReason.COMMAND);
        assert ent != null;

        setCameraTransform(ent, getTransform(new Transform3()));

        world.addEntity(ent);
        return ent;
    }

    @Override
    protected void applyToEntity(AnimatedCameraEntity entity, int timestamp) {
        setCameraTransform(entity, getTransform(new Transform3()));
    }

    private static void setCameraTransform(AnimatedCameraEntity camera, Transform3 transform) {
        camera.setCameraPosition(transform.pos());
        camera.setCameraRotation(transform.getRot(new Quaternionf()));
        // No scale
    }

    @Override
    public PropertiesPanelState drawGizmos(EditorState editor, Vector3dc cameraPos, Matrix4fc viewMatrix, Matrix4fc projectionMatrix, boolean hideUI) {
        AnimatedCameraEntity entity = getInstantiatedEntity();
        if (entity != null) {
            String selected = editor.getSelectedObject();
            boolean s = selected != null && selected.equals(getId());
            entity.setSelected(s);
            entity.setActive(s);
        }
        return super.drawGizmos(editor, cameraPos, viewMatrix, projectionMatrix, hideUI);
    }
}
