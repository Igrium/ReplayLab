package com.igrium.replaylab.scene.objs;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.render.VideoRenderSettings;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.obj.EntityObject;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.scene.obj.ReplayObjectType;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import com.igrium.replaylab.util.SimpleMutable;
import imgui.ImGui;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.Language;
import org.apache.commons.lang3.mutable.Mutable;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global scene properties. Exactly one of these should exist per-scene.
 */
public final class ScenePropsObject extends ReplayObject {

    private static final Logger LOGGER = LoggerFactory.getLogger("ReplayLab/ScenePropsObject");

    @Getter @Setter @Nullable
    private String cameraObject;


    @Getter
    private int startTime;

    @Getter
    private int endTime = 10000; // 10 seconds

//    @Getter
//    private int length = 10000; // 10 seconds default

    @Getter
    private float fps = 24f;

    @Getter
    private int resolutionX = 1920;

    @Getter
    private int resolutionY = 1080;


    /**
     * The last render settings used. Serialized with the object, but generally not used in undo/redo.
     */
    // TODO: determine if lack of undo step causes accidental reset
    @Getter
    private VideoRenderSettings renderSettings = new VideoRenderSettings();

    public ScenePropsObject(ReplayObjectType<?> type, ReplayScene scene) {
        super(type, scene);
    }

    public void setStartTime(int startTime) {
        this.startTime = Math.max(startTime, 0);
        if (endTime < this.startTime) endTime = this.startTime;
    }

    public void setEndTime(int endTime) {
        this.endTime = Math.max(endTime, 0);
        if (this.endTime < startTime) startTime = this.endTime;
    }

    public int getLength() {
        return endTime - startTime;
    }

    public void setFps(float fps) {
        this.fps = Math.max(fps, 1);
    }

    public void setResolutionX(int resolutionX) {
        this.resolutionX = Math.max(resolutionX, 2);
    }

    public void setResolutionY(int resolutionY) {
        this.resolutionY = Math.max(resolutionY, 2);
    }

    public void setResolution(int resolutionX, int resolutionY) {
        setResolutionX(resolutionX);
        setResolutionY(resolutionY);
    }


    @Override
    public void apply(int timestamp) {
    }

    @Override
    public void remapReferences(String oldName, String newName) {
        super.remapReferences(oldName, newName);
        if (oldName.equals(getCameraObject())) {
            setCameraObject(newName);
        }
    }

    @Override
    protected void readJson(JsonObject json, JsonDeserializationContext context) {
        var cameraElem = json.get("cameraObject");
        if (cameraElem != null && cameraElem.isJsonPrimitive()) {
            setCameraObject(cameraElem.getAsString());
        } else {
            setCameraObject(null);
        }
        if (json.has("startTime")) {
            setStartTime(json.getAsJsonPrimitive("startTime").getAsInt());
        }
        if (json.has("endTime")) {
            setEndTime(json.getAsJsonPrimitive("endTime").getAsInt());
        }
        if (json.has("fps")) {
            setFps(json.getAsJsonPrimitive("fps").getAsInt());
        }
        if (json.has("resolutionX")) {
            setResolutionX(json.getAsJsonPrimitive("resolutionX").getAsInt());
        }
        if (json.has("resolutionY")) {
            setResolutionY(json.getAsJsonPrimitive("resolutionY").getAsInt());
        }

        if (json.has("renderSettings")) {
            this.renderSettings = context.deserialize(json.get("renderSettings"), VideoRenderSettings.class);
        }
    }

    @Override
    protected void writeJson(JsonObject json, JsonSerializationContext context) {
        json.addProperty("cameraObject", getCameraObject());
        json.addProperty("startTime", getStartTime());
        json.addProperty("endTime", getEndTime());
        json.addProperty("fps", getFps());
        json.addProperty("resolutionX", getResolutionX());
        json.addProperty("resolutionY", getResolutionY());

        json.add("renderSettings", context.serialize(renderSettings));
    }

    private final Mutable<String> cameraObjectInput = new SimpleMutable<>();
    private final ImInt startTimeInput = new ImInt();
    private final ImInt endTimeInput = new ImInt();
    private final ImFloat fpsInput = new ImFloat();

    boolean editingRes = false;
    final int[] resInput = new int[2];
    boolean editingStartTime = false;
    boolean editingEndTime = false;
    boolean editingFps = false;

    @Override
    public PropertiesPanelState drawPropertiesPanel(EditorState editor) {

        boolean modified = false;

        // RESOLUTION
        if (!editingRes) {
            resInput[0] = getResolutionX();
            resInput[1] = getResolutionY();
        }

        if (ImGui.inputInt2(t("gui.replaylab.res"), resInput)) {
            editingRes = true;
            if (resInput[0] < 2)
                resInput[0] = 2;

            if (resInput[1] < 2)
                resInput[1] = 2;
        } else if (editingRes && !ImGui.isItemActive()) {
            setResolution(resInput[0], resInput[1]);
            modified = true;
            editingRes = false;
        }

        // FPS
        fpsInput.set(fps);
        if (ImGui.inputFloat(t("gui.replaylab.fps"), fpsInput)) {
            editingFps = true;
        }
        setFps(fpsInput.get());

        if (editingFps && !ImGui.isItemActive()) {
            editingFps = false;
            modified = true;
        }

        // CAMERA OBJECT
        cameraObjectInput.setValue(cameraObject);
        if (ReplayLabControls.objectSelector(t("gui.replaylab.camobj"), cameraObjectInput,
                obj -> obj instanceof EntityObject<?>, getScene().getObjects())) {
            modified = true;
            setCameraObject(cameraObjectInput.getValue());
        }

        // START TIME
        if (!editingStartTime) {
            startTimeInput.set(startTime);
        }

        if (ImGui.inputInt(t("gui.replaylab.starttime"), startTimeInput)) {
            editingStartTime = true;
        }

        if (editingStartTime && !ImGui.isItemActive()) {
            setStartTime(startTimeInput.get());
            editingStartTime = false;
            modified = true;
        }

        // END TIME
        if (!editingEndTime) {
            endTimeInput.set(endTime);
        }

        if (ImGui.inputInt(t("gui.replaylab.endtime"), endTimeInput)) {
            editingEndTime = true;
        }

        if (editingEndTime && !ImGui.isItemActive()) {
            setEndTime(endTimeInput.get());
            editingEndTime = false;
            modified = true;
        }

        return modified ? PropertiesPanelState.COMMIT : PropertiesPanelState.NONE;
    }

    private static String t(String key) {
        return Language.getInstance().get(key);
    }

}
