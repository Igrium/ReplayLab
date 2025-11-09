package com.igrium.replaylab.scene.objs;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.obj.CameraProvider;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.scene.obj.ReplayObjectType;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import com.igrium.replaylab.util.SimpleMutable;
import imgui.ImGui;
import imgui.type.ImInt;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
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
    private int resolutionX = 1920;

    @Getter
    private int resolutionY = 1080;

    @Getter
    private float frameRate = 24f;

    @Getter @Setter
    private boolean motionBlur;

    /**
     * The camera's shutter speed as represented by a fraction of the frame rate.
     */
    @Getter
    private float shutterSpeed = 0.5f;

    public ScenePropsObject(ReplayObjectType<?> type, ReplayScene scene) {
        super(type, scene);
    }

    public void setStartTime(int startTime) {
        if (startTime < 0) {
            throw new IllegalArgumentException("startTime may not be negative.");
        }
        this.startTime = startTime;
    }

    public void setLength(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length may not be negative.");
        }
        this.length = length;
    }

    public void setResolutionX(int resolutionX) {
        if (resolutionX < 2) {
            throw new IllegalArgumentException("Resolution must be at least two.");
        }
        this.resolutionX = resolutionX;
    }

    public void setResolutionY(int resolutionY) {
        if (resolutionY < 2) {
            throw new IllegalArgumentException("Resolution must be at least two.");
        }
        this.resolutionY = resolutionY;
    }

    public void setResolution(int resolutionX, int resolutionY) {
        setResolutionX(resolutionX);
        setResolutionY(resolutionY);
    }

    public void setFrameRate(float frameRate) {
        if (frameRate <= 0) {
            throw new IllegalArgumentException("Frame rate must be greater than zero");
        }
        this.frameRate = frameRate;
    }

    public void setShutterSpeed(float shutterSpeed) {
        if (shutterSpeed <= 0 || shutterSpeed >= 1) {
            throw new IllegalArgumentException("Shutter speed fall in the range (0-1)");
        }
        this.shutterSpeed = shutterSpeed;
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
        if (json.has("resolutionX")) {
            setResolutionX(json.getAsJsonPrimitive("resolutionX").getAsInt());
        }
        if (json.has("resolutionY")) {
            setResolutionY(json.getAsJsonPrimitive("resolutionY").getAsInt());
        }
        if (json.has("frameRate")) {
            setFrameRate(json.getAsJsonPrimitive("frameRate").getAsFloat());
        }
        if (json.has("motionBlur")) {
            setMotionBlur(json.getAsJsonPrimitive("motionBlur").getAsBoolean());
        }
        if (json.has("shutterSpeed")) {
            setShutterSpeed(json.getAsJsonPrimitive("shutterSpeed").getAsFloat());
        }
    }

    @Override
    protected void writeJson(JsonObject json, JsonSerializationContext context) {
        json.addProperty("cameraObject", getCameraObject());
        json.addProperty("startTime", getStartTime());
        json.addProperty("length", getLength());
        json.addProperty("resolutionX", getResolutionX());
        json.addProperty("resolutionY", getResolutionY());
        json.addProperty("frameRate", getFrameRate());
        json.addProperty("motionBlur", isMotionBlur());
        json.addProperty("shutterSpeed", getShutterSpeed());

    }

    private final Mutable<String> cameraObjectInput = new SimpleMutable<>();
    private final ImInt startTimeInput = new ImInt();
    private final ImInt lengthInput = new ImInt();

    boolean editingRes = false;
    final int[] resInput = new int[2];
    boolean editingStartTime = false;
    boolean editingLength = false;

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
