package com.igrium.replaylab.scene.key;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * A collection of all keyframe channels in the scene.
 * All values in the manifest are mutable - keyframes <em>and</em> collection values.
 */
public class KeyframeManifest {

    /**
     * Contains a "reference" to a particular keyframe within the manifest.
     * If the manifest gets re-initialized for any reason, this reference should remain.
     *
     * @param category Category index.
     * @param channel  Channel index within the category.
     * @param keyframe Keyframe index within the channel.
     */
    public record KeyReference(String category, int channel, int keyframe) {
    }

    @Getter
    private final BiMap<String, KeyChannelCategory> categories;

    protected KeyframeManifest(BiMap<String, KeyChannelCategory> categories) {
        this.categories = categories;
    }

    public KeyframeManifest() {
        this(HashBiMap.create());
    }

    public KeyframeManifest copy() {
        BiMap<String, KeyChannelCategory> copied = HashBiMap.create(categories.size());
        for (var entry : categories.entrySet()) {
            copied.put(entry.getKey(), entry.getValue().copy());
        }
        return new KeyframeManifest(copied);
    }


    /**
     * Locate and return a keyframe from the manifest.
     * @param ref Keyframe reference.
     * @return A mutable object reference to the keyframe. <code>null</code> if the indices were out of bounds.
     */
    public final @Nullable Keyframe getKeyframe(KeyReference ref) {
        return getKeyframe(ref.category, ref.channel, ref.keyframe);
    }

    /**
     * Locate and return a keyframe from the manifest.
     *
     * @param category Category index.
     * @param channel  Channel index within the category.
     * @param keyframe Keyframe index within the channel.
     * @return A mutable object reference to the keyframe. <code>null</code> if the indices were out of bounds.
     */
    public @Nullable Keyframe getKeyframe(String category, int channel, int keyframe) {
        if (channel < 0 || keyframe < 0)
            return null;

        var cat = categories.get(category);
        if (cat == null) {
            return null;
        }
        if (channel >= cat.getChannels().size()) {
            return null;
        }

        var ch = cat.getChannels().get(channel);

        if (keyframe >= ch.getKeys().size()) {
            return null;
        }

        return ch.getKeys().get(keyframe);
    }
}