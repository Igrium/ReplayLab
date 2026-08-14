package com.igrium.replaylab.object.types;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.entity.EmptyObjectEntity;
import com.igrium.replaylab.entity.ReplayLabEntities;
import com.igrium.replaylab.object.EntityObject;
import com.igrium.replaylab.object.EntityProvider;
import com.igrium.replaylab.object.ReplayObject3D;
import com.igrium.replaylab.object.ReplayObjectType;
import com.igrium.replaylab.scene.ReplayScene;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import org.joml.Matrix4fc;
import org.joml.Vector3dc;

import java.util.Objects;

/**
 * An empty object used for misc applications like parenting
 */
public class ObjectEmpty extends EntityObject<EmptyObjectEntity> {
    public ObjectEmpty(ReplayObjectType<?> type, ReplayScene scene) {
        super(type, scene);
        setHasPos(true);
        setHasRot(true);
        setHasScale(true);
    }

    @Override
    public void apply(int timestamp) {
        // NOOP
    }

    @Override
    protected EmptyObjectEntity createEntity(ClientLevel world) {
        var ent = ReplayLabEntities.EMPTY.create(world, EntitySpawnReason.COMMAND);
        addToWorld(world, ent);
        return ent;
    }

    @Override
    protected void applyToEntity(EmptyObjectEntity entity, int timestamp) {
        super.applyToEntity(entity, timestamp);
        entity.setPos(position().x, position().y, position().z);

    }

    @Override
    public int drawGizmos(EditorState editor, Vector3dc cameraPos, Matrix4fc viewMatrix, Matrix4fc projectionMatrix, boolean hideUI) {
        var ent = getInstantiatedEntity();
        if (ent != null) {
            ent.setPos(position().x, position().y, position().z);
            String id = getId();
            ent.setSelected(editor.isObjectSelected(id));
            ent.setActive(editor.isObjectActive(id));

            rotation().getQuaternion(ent.getRotation());
            scale().get(ent.getScale());
        }
        return super.drawGizmos(editor, cameraPos, viewMatrix, projectionMatrix, hideUI);
    }
}
