package com.igrium.replaylab.object.types;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.anim.KeyChannel;
import com.igrium.replaylab.anim.Keyframe;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.object.EditFlags;
import com.igrium.replaylab.object.EntityProvider;
import com.igrium.replaylab.object.ReplayObject;
import com.igrium.replaylab.object.ReplayObjectType;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.ui.widgets.KeyWidgets.WidgetState;
import com.igrium.replaylab.ui.widgets.PropertyWidgets;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import com.igrium.replaylab.util.SimpleMutable;
import imgui.ImGui;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import it.unimi.dsi.fastutil.ints.*;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.locale.Language;
import org.apache.commons.lang3.mutable.Mutable;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Global scene properties. Exactly one of these should exist per-scene.
 */
public final class ObjectSceneProps extends ReplayObject {

    private static final Logger LOGGER = LoggerFactory.getLogger("ReplayLab/ScenePropsObject");

    private static final double MAX_SPEED = 200;

    public static String PROP_SPEED = "speed";
    public static String PROP_CAMERA = "camera";

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

    /**
     * The index in the camera objects array of the active camera object
     */
    @Getter @Setter
    private int cameraIdx;

    private final Int2ObjectMap<String> cameras = new Int2ObjectArrayMap<>();

    public ObjectSceneProps(ReplayObjectType<?> type, ReplayScene scene) {
        super(type, scene);
        addProperty(PROP_SPEED, new Property(this::getSpeed, this::setSpeed, 0, MAX_SPEED, false, true));
        addProperty(PROP_CAMERA, this::getCameraIdx, this::setCameraIdx);
    }

    /**
     * All the cameras that are used during the scene with an index to identify them in keyframes
     */
    public Int2ObjectMap<String> getCameras() {
        return Int2ObjectMaps.unmodifiable(cameras);
    }

    public void setSpeed(double speed) {
        this.speed = Math.clamp(speed, 0, MAX_SPEED);
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

    public @NonNull String getCamera(int idx) {
        String cam = cameras.get(idx);
        return cam != null ? cam : "";
    }

    public @NonNull String getCamera() {
        return getCamera(getCameraIdx());
    }

    public void setCamera(@NonNull String camera) {
        if (camera.isBlank()) {
            setCameraIdx(-1);
            cleanCamList();
            return;
        }

        int idx = -1;
        // Not the best in terms of time complexity, but the set is quite small.
        for (var entry : cameras.int2ObjectEntrySet()) {
            if (entry.getValue().equals(camera)) {
                idx = entry.getIntKey();
                break;
            }
        }

        if (idx < 0) {
            idx = findEmptyKey(cameras.keySet());
            cameras.put(idx, camera);
        }

        setCameraIdx(idx);
        cleanCamList();
    }

    /**
     * Collect the camera indices of all the cameras that are keyframed
     * @return Set of indices in cameras array
     */
    public IntSet getKeyedCamIndices() {
        KeyChannel chan = getChannel(PROP_CAMERA);
        if (chan == null) return IntSets.emptySet();

        IntSet set = new IntArraySet();
        for (Keyframe key : chan.getKeyframes()) {
            set.add((int) Math.round(key.getValue()));
        }

        return set;
    }

    private void cleanCamList() {
        IntSet keyed = getKeyedCamIndices();
        IntSet toRemove = new IntArraySet();
        var iter = cameras.keySet().iterator();
        while (iter.hasNext()) {
            int idx = iter.nextInt();
            if (idx != getCameraIdx() && !keyed.contains(idx)) {
                iter.remove();
                toRemove.add(idx);
            }
        }
    }

    @Override
    public void apply(int timestamp) {
    }

    @Override
    public void remapReferences(String oldName, String newName) {
        super.remapReferences(oldName, newName);
        for (var entry : this.cameras.int2ObjectEntrySet()) {
            if (entry.getValue().equals(oldName)) {
                entry.setValue(newName);
            }
        }
    }

    @Override
    protected void readJson(JsonObject json, JsonDeserializationContext context) {
        if (json.has("cameras") && json.has("cameraIdx")) {
            JsonObject cameras = json.getAsJsonObject("cameras");
            this.cameras.clear();
            for (var entry : cameras.entrySet()) {
                this.cameras.put(Integer.parseInt(entry.getKey()), entry.getValue().getAsString());
            }

            setCameraIdx(json.get("cameraIdx").getAsInt());
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
        JsonObject cameras = new JsonObject();
        for (var entry :  this.cameras.int2ObjectEntrySet()) {
            cameras.addProperty(String.valueOf(entry.getIntKey()), entry.getValue());
        }

        json.add("cameras", cameras);
        json.addProperty("cameraIdx", getCameraIdx());

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
            rFlags = EditFlags.CREATE_UNDO_STEP;
            editingRes = false;
        }

        fpsInput.set(fps);
        if (ImGui.inputFloat("FPS", fpsInput)) {
            setFps(fpsInput.get());
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            rFlags |= EditFlags.CREATE_UNDO_STEP;
        }

        cameraObjectInput.setValue(getCamera());
        if (ReplayLabControls.objectSelector("Camera Object", cameraObjectInput,
                obj -> obj instanceof EntityProvider<?>, getScene().getObjects())) {
            rFlags |= EditFlags.COMMIT;
            setCamera(cameraObjectInput.get());
        }

        startTimeInput.set(startTime);
        if (ReplayLabControls.inputTimestamp("Start Time", startTimeInput.getData())) {
            startTime = Math.max(0, startTimeInput.get());
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            rFlags |= EditFlags.COMMIT | EditFlags.RESAMPLE;
        }

        lengthInput.set(length);
        if (ReplayLabControls.inputTimestamp("Length", lengthInput.getData())) {
            length = Math.max(0, lengthInput.get());
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            rFlags |= EditFlags.CREATE_UNDO_STEP;
        }

        WidgetState sState = PropertyWidgets.dragFloatN(this, "Speed", .125f, editor.getPlayhead(), PROP_SPEED);
        if (sState.isDropped() || sState.hasNewKey()) {
            rFlags |= EditFlags.COMMIT;
        }

        return rFlags;
    }

    @Override
    public String getDisplayName() {
        return Language.getInstance().getOrDefault("replayobject.sceneProps");
    }

    private static int findEmptyKey(IntSet set) {
        int i = 0;
        while (set.contains(i)) {
            i++;
        }
        return i;
    }
}
