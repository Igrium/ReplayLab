package com.igrium.replaylab.scene.objs;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.math.Transform3;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.obj.EntityProvider;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.scene.obj.ReplayObjectType;
import com.igrium.replaylab.scene.obj.TransformProvider;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Math;

/**
 * An object which proxies an entity in the world, allowing it to be used as a camera, be parented to, etc.
 */
public class EntityProxyObject extends ReplayObject implements EntityProvider<Entity>, TransformProvider {
    public EntityProxyObject(ReplayObjectType<?> type, ReplayScene scene) {
        super(type, scene);
    }

    /**
     * The UUID of the entity to target
     */
    @Getter @Setter
    private int entId;

    @Override
    public @Nullable Entity getEntity(ClientWorld world) {
        return world.getEntityById(entId);
    }

    @Override
    protected void writeJson(JsonObject json, JsonSerializationContext context) {
        json.addProperty("entId", entId);
    }

    @Override
    protected void readJson(JsonObject json, JsonDeserializationContext context) {
        if (json.has("entId")) {
            entId = json.get("entId").getAsInt();
        }
    }

    @Override
    public void apply(int timestamp) {

    }

    @Override
    public Transform3 getTransform(Transform3 dest) {
        Entity ent = getEntity();
        if (ent == null) {
            return dest.identity();
        }

        Vec3d entPos = ent.getPos();
        dest.pos().set(entPos.x, entPos.y, entPos.z);

        dest.rot().setEulerYXZ(
                -Math.toRadians(ent.getYaw()),
                Math.toRadians(ent.getPitch()),
                0
        );

        dest.scale().set(1);

        return null;
    }
}
