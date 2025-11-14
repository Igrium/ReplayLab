package com.igrium.replaylab.scene.objs;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.render.VideoRenderSettings;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.obj.CameraProvider;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.scene.obj.ReplayObjectType;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import com.igrium.replaylab.util.SimpleMutable;
import imgui.ImGui;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import lombok.Getter;
import lombok.Setter;
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
    private int length = 10000; // 10 seconds default

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
    }

    public void setLength(int length) {
        this.length = Math.max(length, 0);
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
        if (json.has("cameraObject")) {
            setCameraObject(json.getAsJsonPrimitive("cameraObject").getAsString());
        }
        if (json.has("startTime")) {
            setStartTime(json.getAsJsonPrimitive("startTime").getAsInt());
        }
        if (json.has("length")) {
            setLength(json.getAsJsonPrimitive("length").getAsInt());
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
        json.addProperty("length", getLength());
        json.addProperty("fps", getFps());
        json.addProperty("resolutionX", getResolutionX());
        json.addProperty("resolutionY", getResolutionY());

        json.add("renderSettings", context.serialize(renderSettings));
    }

    private final Mutable<String> cameraObjectInput = new SimpleMutable<>();
    private final ImInt startTimeInput = new ImInt();
    private final ImInt lengthInput = new ImInt();
    private final ImFloat fpsInput = new ImFloat();

    boolean editingRes = false;
    final int[] resInput = new int[2];
    boolean editingStartTime = false;
    boolean editingLength = false;
    boolean editingFps = false;

    @Override
    public PropertiesPanelState drawPropertiesPanel() {

        boolean modified = false;

        if (!editingRes) {
            resInput[0] = getResolutionX();
            resInput[1] = getResolutionY();
        }

        if (ImGui.inputInt2("Resolution", resInput)) {
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

        fpsInput.set(fps);
        if (ImGui.inputFloat("FPS", fpsInput)) {
            editingFps = true;
        }
        setFps(fpsInput.get());

        if (editingFps && !ImGui.isItemActive()) {
            editingFps = false;
            modified = true;
        }

        cameraObjectInput.setValue(cameraObject);
        if (ReplayLabControls.objectSelector("Camera Object", cameraObjectInput,
                obj -> obj instanceof CameraProvider, getScene().getObjects())) {
            modified = true;
            setCameraObject(cameraObjectInput.getValue());
        }

        startTimeInput.set(startTime);
        if (ImGui.inputInt("Start Time", startTimeInput)) {
            editingStartTime = true;
        }
        startTime = startTimeInput.get();
        if (startTime < 0)
            startTime = 0;

        if (editingStartTime && !ImGui.isItemActive()) {
            editingStartTime = false;
            modified = true;
        }

        lengthInput.set(length);
        if (ImGui.inputInt("Length (ms)", lengthInput)) {
            editingLength = true;
        }
        length = lengthInput.get();
        if (length < 0) {
            length = 0;
        }

        if (editingLength && !ImGui.isItemActive()) {
            editingLength = false;
            modified = true;
        }


        return modified ? PropertiesPanelState.COMMIT : PropertiesPanelState.NONE;
    }
}
