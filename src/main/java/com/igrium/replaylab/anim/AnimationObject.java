package com.igrium.replaylab.anim;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.igrium.replaylab.scene.KeyChannelCategory;
import com.igrium.replaylab.scene.ReplayScene;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Represents a single "object" that may be animated, such as a camera or a display entity
 */
public abstract class AnimationObject {

    @Getter
    private final AnimationObjectType<?> type;

    @Getter
    private final ReplayScene scene;

    protected AnimationObject(AnimationObjectType<?> type, ReplayScene scene) {
        this.type = type;
        this.scene = scene;
    }

    /**
     * Obtain the string ID of this object.
     * @return The ID, or <code>null</code> if this object isn't part of its scene (removed, etc)
     */
    public final @Nullable String getId() {
        return scene.getObjects().inverse().get(this);
    }

    public void readJson(JsonObject json) {};

    public void writeJson(JsonObject json) {};

    /**
     * Compute the result of the animation curve(s) and apply it to the game.
     * @param keyframes The keyframes belonging to this object.
     * @param timestamp Timestamp to sample.
     */
    public abstract void apply(KeyChannelCategory keyframes, int timestamp);

    /**
     * Declare the desired amount of channels and their names for the UI.
     * @return A list with all channel names in order.
     * The length of this list determines the number of channels.
     */
    public abstract List<String> listChannelNames();

    public static JsonObject toJson(AnimationObject obj, JsonObject json) {
        obj.writeJson(json);
        json.addProperty("type", obj.getType().getId());
        return json;
    }

    public static AnimationObject fromJson(JsonObject json, ReplayScene scene) throws IllegalArgumentException {
        JsonPrimitive typeE = json.getAsJsonPrimitive("type");
        if (typeE == null || !typeE.isString()) {
            throw new IllegalArgumentException("Animation object must include a 'type' string tag.");
        }

        var type = AnimationObjectType.REGISTRY.get(typeE.getAsString());
        if (type == null) {
            throw new IllegalArgumentException("Unknown animation object type: " + typeE.getAsString());
        }

        AnimationObject obj = type.create(scene);
        obj.readJson(json);
        return obj;
    }
}
