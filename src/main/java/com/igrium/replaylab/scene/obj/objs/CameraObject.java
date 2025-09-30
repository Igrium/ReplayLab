package com.igrium.replaylab.scene.obj.objs;

import com.igrium.replaylab.ReplayLabEntities;
import com.igrium.replaylab.camera.AnimatedCameraEntity;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.obj.EntityObject;
import com.igrium.replaylab.scene.obj.ReplayObjectType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.SpawnReason;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public class CameraObject extends EntityObject<AnimatedCameraEntity> {

    public CameraObject(ReplayObjectType<?> type, ReplayScene scene) {
        super(type, scene);
    }

    private final Vector3d posCache = new Vector3d();
    private final Vector3d rotCache = new Vector3d();

    @Override
    protected AnimatedCameraEntity createEntity(ClientWorld world) {
        var ent = ReplayLabEntities.CAMERA.create(world, SpawnReason.COMMAND);
        assert ent != null;

        getCombinedTransform(posCache, rotCache, null);
        setCameraTransform(ent, posCache, rotCache);

        world.spawnEntity(ent);
        return ent;
    }

    @Override
    protected void applyEntityTransform(AnimatedCameraEntity entity, int timestamp) {
        getCombinedTransform(posCache, rotCache, null);
        setCameraTransform(entity, posCache, rotCache);
    }

    private static void setCameraTransform(AnimatedCameraEntity camera, Vector3dc pos, Vector3dc rot) {
        camera.setCameraPosition(pos.x(), pos.y(), pos.z());
        camera.setCameraRotation((float) rot.x(), (float) rot.y(), (float) rot.z());
    }
}
