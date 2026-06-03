package com.igrium.replaylab.scene.objs;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.ReplayLabEntities;
import com.igrium.replaylab.camera.AnimatedCameraEntity;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.math.Transform3;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.obj.EntityObject;
import com.igrium.replaylab.scene.obj.ReplayObjectType;
import com.igrium.replaylab.ui.util.KeyWidgets;
import com.igrium.replaylab.ui.util.PropertyWidgets;
import imgui.ImGui;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.SpawnReason;
import net.minecraft.util.Language;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3dc;

import java.util.Objects;

public class CameraObject extends EntityObject<AnimatedCameraEntity> {

    private static final String FOV = "fov";

    @Getter
    private double fov = 60;

    public void setFov(double fov) {
        this.fov =  Math.max(1, fov);
    }

    public CameraObject(ReplayObjectType<?> type, ReplayScene scene) {
        super(type, scene);

        addProperty(FOV, this::getFov, this::setFov);
    }

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
        entity.setFov((float) fov);
    }

    private static void setCameraTransform(AnimatedCameraEntity camera, Transform3 transform) {
        camera.setCameraPosition(transform.pos());
        camera.setCameraRotation(transform.getRot(new Quaternionf()));
    }

    @Override
    public PropertiesPanelState drawGizmos(EditorState editor, Vector3dc cameraPos, Matrix4fc viewMatrix, Matrix4fc projectionMatrix, boolean hideUI) {
        AnimatedCameraEntity entity = getInstantiatedEntity();
        ScenePropsObject scene = editor.getScene().getSceneProps();
        if (entity != null) {
            String id = getId();
            entity.setSelected(editor.isObjectSelected(id));
            entity.setActive(editor.isObjectActive(id));
            entity.setSceneCamera(isSceneCamera());
            entity.setAspectRatio((float) scene.getResolutionX() / scene.getResolutionY());
        }
        return super.drawGizmos(editor, cameraPos, viewMatrix, projectionMatrix, hideUI);
    }

    private boolean startedDragging = false;

    @Override
    public PropertiesPanelState drawPropertiesPanel(EditorState editor) {
        PropertiesPanelState pState = super.drawPropertiesPanel(editor);

        ImGui.separatorText(t("gui.replaylab.camera"));
        var wState = PropertyWidgets.dragFloatN(this, t("options.fov"), 1, editor.getPlayhead(), FOV);
        boolean modified = wState.hasNewKey() || wState.isUpdated();

        PropertiesPanelState newState;

        if (modified || (startedDragging && ImGui.isMouseDown(0))) {
            startedDragging = true;
            newState = PropertiesPanelState.DRAGGING;
        } else if (startedDragging) {
            startedDragging = false;
            newState = PropertiesPanelState.COMMIT;
        } else {
            newState = PropertiesPanelState.NONE;
        }

        return PropertiesPanelState.max(newState, pState);
    }

    @Override
    protected void writeJson(JsonObject json, JsonSerializationContext context) {
        super.writeJson(json, context);
        json.addProperty(FOV, getFov());
    }

    @Override
    protected void readJson(JsonObject json, JsonDeserializationContext context) {
        super.readJson(json, context);
        if (json.has(FOV)) {
            setFov(json.get(FOV).getAsDouble());
        }
    }

    private static String t(String key) {
        return Language.getInstance().get(key) + "###" + key;
    }
}
