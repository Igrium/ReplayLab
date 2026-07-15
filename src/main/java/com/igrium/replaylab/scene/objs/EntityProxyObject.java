package com.igrium.replaylab.scene.objs;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.math.Transform3;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.obj.*;
import com.igrium.replaylab.ui.widgets.EntityPicker;
import com.igrium.replaylab.ui.widgets.EntitySelector;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;
import imgui.type.ImInt;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.MinecraftClient;
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


    private final ImInt entId = new ImInt();

    public int getEntId() {
        return entId.get();
    }

    public void setEntId(int entId) {
        this.entId.set(entId);
    }

    @Override
    public @Nullable Entity getEntity(ClientWorld world) {
        return world.getEntityById(getEntId());
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

        Vec3d entPos = ent.getPos();
        dest.pos().set(entPos.x, entPos.y, entPos.z);

        dest.rot().setEulerYXZ(
                -Math.toRadians(ent.getYaw()),
                Math.toRadians(ent.getPitch()),
                0
        );

        dest.scale().set(1);

        return dest;
    }

    @Override
    public int drawPropertiesPanel(EditorState editor) {
        Entity ent = getEntity();
        String msg = ent != null ? ent.getName().getString() : "[None]";

        if (ImGui.button(msg + "###select")) {
            EntityPicker.open("entity");
        }

        if (ImGui.isItemClicked(ImGuiMouseButton.Right)) {
            ImGui.openPopup("entityContext");
        }

        int flags = ObjectEditState.NONE;
        EntityPicker picker = EntityPicker.get("entity");
        if (picker != null) {
            Entity picked = picker.getPickedEntity();
            if (picked != null) {
                setEntId(picked.getId());
                picker.close();
                flags |= ObjectEditState.COMMIT;
            }
        }

        ClientWorld world = MinecraftClient.getInstance().world;

        if (ImGui.beginPopup("entityContext")) {
            EntitySelector.selectEntity("ent", entId, world.getEntities());
            ImGui.endPopup();
        }


        return flags;
    }

}
