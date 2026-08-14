package com.igrium.replaylab.object.types;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.entity.EmptyObjectEntity;
import com.igrium.replaylab.entity.ReplayLabEntities;
import com.igrium.replaylab.math.Transform3;
import com.igrium.replaylab.object.EntityObject;
import com.igrium.replaylab.object.ReplayObjectType;
import com.igrium.replaylab.scene.ReplayScene;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import org.joml.Matrix4fc;
import org.joml.Vector3dc;

/**
 * An empty object used for misc applications like parenting
 */
public class ObjectEmpty extends EntityObject<EmptyObjectEntity> {
    public ObjectEmpty(ReplayObjectType<?> type, ReplayScene scene) {
        super(type, scene);
    }

    @Override
    protected EmptyObjectEntity createEntity(ClientLevel world) {
        var ent = ReplayLabEntities.EMPTY.create(world, EntitySpawnReason.COMMAND);
        assert ent != null;

        applyTransform(ent, getTransform(new Transform3()));

        addToWorld(world, ent);
        return ent;
    }

    @Override
    protected void applyToEntity(EmptyObjectEntity entity, int timestamp) {
        super.applyToEntity(entity, timestamp);
        applyTransform(entity, getTransform(new Transform3()));
    }

    private static void applyTransform(EmptyObjectEntity entity, Transform3 transform) {
        var pos = transform.pos();
        entity.setPos(pos.x, pos.y, pos.z);
        entity.xOld = pos.x;
        entity.yOld = pos.y;
        entity.zOld = pos.z;

        transform.getRot(entity.getRotation());
        transform.getScale(entity.getScale());
    }

    @Override
    public int drawGizmos(EditorState editor, Vector3dc cameraPos, Matrix4fc viewMatrix, Matrix4fc projectionMatrix, boolean hideUI) {
        var ent = getInstantiatedEntity();
        if (ent != null) {
            String id = getId();
            ent.setSelected(editor.isObjectSelected(id));
            ent.setActive(editor.isObjectActive(id));
        }
        return super.drawGizmos(editor, cameraPos, viewMatrix, projectionMatrix, hideUI);
    }
}
