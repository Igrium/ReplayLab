package com.igrium.replaylab.scene.objs;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.obj.*;
import com.igrium.replaylab.scene.obj.PropertyHolder.Property;
import com.igrium.replaylab.ui.widgets.KeyWidgets.WidgetState;
import com.igrium.replaylab.ui.widgets.PropertyWidgets;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import com.igrium.replaylab.util.SimpleMutable;
import imgui.ImGui;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.util.Language;
import org.apache.commons.lang3.mutable.Mutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global scene properties. Exactly one of these should exist per-scene.
 */
public final class ScenePropsObject extends ReplayObject {

    private static final Logger LOGGER = LoggerFactory.getLogger("ReplayLab/ScenePropsObject");

    private static final double MAX_SPEED = 200;

    public static String PROP_SPEED = "speed";

    @Getter @Setter @NonNull
    private String cameraObject = "";

    @Getter
    private int startTime;

    @Getter
    private int length = 10_000; // 10 seconds default

    @Getter
    private float fps = 24f;

    @Getter
    private int resolutionX = 1920;

    @Getter
    private int resolutionY = 1080;

    @Getter
    private double speed = 1;

    public void setSpeed(double speed) {
        this.speed = Math.clamp(speed, 0, MAX_SPEED);
    }

    public ScenePropsObject(ReplayObjectType<?> type, ReplayScene scene) {
        super(type, scene);
        addProperty(PROP_SPEED, new Property(this::getSpeed, this::setSpeed, 0, MAX_SPEED, true));
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
        var cameraElem = json.get("cameraObject");
        if (cameraElem != null && cameraElem.isJsonPrimitive()) {
            setCameraObject(cameraElem.getAsString());
        } else {
            setCameraObject("");
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
        if (json.has("speed")) {
            setSpeed(json.getAsJsonPrimitive("speed").getAsDouble());
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
        json.addProperty("speed", getSpeed());
    }

    private final Mutable<String> cameraObjectInput = new SimpleMutable<>();
    private final ImInt startTimeInput = new ImInt();
    private final ImInt lengthInput = new ImInt();
    private final ImFloat fpsInput = new ImFloat();

    boolean editingRes = false;
    final int[] resInput = new int[2];

    @Override
    public int drawPropertiesPanel(EditorState editor) {

        int rFlags = 0;

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
            rFlags = ObjectEditState.CREATE_UNDO_STEP;
            editingRes = false;
        }

        fpsInput.set(fps);
        if (ImGui.inputFloat("FPS", fpsInput)) {
            setFps(fpsInput.get());
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            rFlags |= ObjectEditState.CREATE_UNDO_STEP;
        }

        cameraObjectInput.setValue(cameraObject);
        if (ReplayLabControls.objectSelector("Camera Object", cameraObjectInput,
                obj -> obj instanceof EntityProvider<?>, getScene().getObjects())) {
            rFlags |= ObjectEditState.COMMIT;
            setCameraObject(cameraObjectInput.getValue());
        }

        startTimeInput.set(startTime);
        if (ReplayLabControls.inputTimestamp("Start Time", startTimeInput)) {
            startTime = Math.max(0, startTimeInput.get());
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            rFlags |= ObjectEditState.COMMIT | ObjectEditState.RESAMPLE;
        }

        lengthInput.set(length);
        if (ReplayLabControls.inputTimestamp("Length", lengthInput)) {
            length = Math.max(0, lengthInput.get());
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            rFlags |= ObjectEditState.CREATE_UNDO_STEP;
        }

        WidgetState sState = PropertyWidgets.dragFloatN(this, "Speed", .125f, editor.getPlayhead(), PROP_SPEED);
        if (sState.isDropped() || sState.hasNewKey()) {
            rFlags |= ObjectEditState.COMMIT;
        }

        return rFlags;
    }

    @Override
    public String getDisplayName() {
        return Language.getInstance().get("replayobject.sceneProps");
    }
}
