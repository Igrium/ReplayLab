package com.igrium.replaylab.scene.objs;

import com.igrium.replaylab.ReplayLabEntities;
import com.igrium.replaylab.camera.AnimatedCameraEntity;
import com.igrium.replaylab.math.Transform3;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.obj.EntityObject;
import com.igrium.replaylab.scene.obj.ReplayObjectType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.SpawnReason;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;

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
//        Transform3 transform = new Transform3();
//        super.getTransform(transform);
//        super.getCombinedTransform(posCache, rotCache, null);
//        setCameraTransform(entity, posCache, rotCache);

//        entity.setSelected(getScene().is);
    }

    private static void setCameraTransform(AnimatedCameraEntity camera, Transform3 transform) {
        camera.setCameraPosition(transform.pos());
        camera.setCameraRotation(transform.getRot(new Quaternionf()));
        // No scale
    }
}
