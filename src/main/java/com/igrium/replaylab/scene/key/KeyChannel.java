package com.igrium.replaylab.scene.key;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import lombok.Getter;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * A single "channel" of keyframes. A given channel always contain a single curve of scalar values.
 */
@JsonAdapter(KeyChannelSerializer.class)
public class KeyChannel {

    @Getter
    private final List<Keyframe> keys;

    public KeyChannel() {
        this(new ArrayList<>());
    }

    protected KeyChannel(List<Keyframe> keyframes) {
        this.keys = keyframes;
    }

    /**
     * Sample the curve at a given timestamp.
     * @param timestamp Timestamp to sample at.
     * @return The scalar value of the curve at that time.
     */
    public double sample(int timestamp) {
        return 0; // TODO: implement
    }

    /**
     * Make a deep copy of this channel.
     */
    public KeyChannel copy() {
        List<Keyframe> copied = new ArrayList<>(keys.size());
        for (Keyframe key : keys) {
            copied.add(new Keyframe(key));
        }
        return new KeyChannel(copied);
    }
}

class KeyChannelSerializer implements JsonSerializer<KeyChannel>, JsonDeserializer<KeyChannel> {

    @Override
    public KeyChannel deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonArray arr = json.getAsJsonArray();
        List<Keyframe> keys = new ArrayList<>(arr.size());

        for (var el : arr) {
            keys.add(context.deserialize(el, Keyframe.class));
        }

        return new KeyChannel(keys);
    }

    @Override
    public JsonElement serialize(KeyChannel src, Type typeOfSrc, JsonSerializationContext context) {
        return context.serialize(src.getKeys());
    }
}

