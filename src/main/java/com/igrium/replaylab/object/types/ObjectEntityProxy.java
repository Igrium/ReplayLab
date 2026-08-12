package com.igrium.replaylab.object.types;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.math.Transform3;
import com.igrium.replaylab.object.*;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.ui.widgets.EntitySelector;
import com.replaymod.replay.camera.CameraEntity;
import imgui.type.ImInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Math;

/**
 * An object which proxies an entity in the world, allowing it to be used as a camera, be parented to, etc.
 */
public class ObjectEntityProxy extends ReplayObject implements EntityProvider<Entity>, TransformProvider {
    public ObjectEntityProxy(ReplayObjectType<?> type, ReplayScene scene) {
        super(type, scene);
    }


    private final ImInt entId = new ImInt();

    public int getEntId() {
        return entId.get();
    }

    public void setEntId(int entId) {
        this.entId.set(entId);
    }

    @Override
    public @Nullable Entity getEntity(ClientLevel world) {
        return world.getEntity(getEntId());
    }

    @Override
    protected void writeJson(JsonObject json, JsonSerializationContext context) {
        json.addProperty("entId", getEntId());
    }

    @Override
    protected void readJson(JsonObject json, JsonDeserializationContext context) {
        if (json.has("entId")) {
            setEntId(json.get("entId").getAsInt());
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
        float tickDelta = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);

        Vec3 entPos = ent.getPosition(tickDelta);
        dest.pos().set(entPos.x, entPos.y, entPos.z);

        dest.rot().setEulerYXZ(
                -Math.toRadians(ent.getViewYRot(tickDelta)),
                Math.toRadians(ent.getViewXRot(tickDelta)),
                0
        );


        dest.scale().set(1);

        return dest;
    }

    @Override
    public int drawPropertiesPanel(EditorState editor) {
        int flags = EditFlags.NONE;

        if (EntitySelector.selector("Entity", entId, e -> !(e instanceof CameraEntity))) {
            flags |= EditFlags.COMMIT;
        }

        return flags;
    }

}
