package com.igrium.replaylab.scene;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
    public record KeyReference(int category, int channel, int keyframe) {
    }

    @Getter
    private final List<KeyChannelCategory> categories;


    protected KeyframeManifest(List<KeyChannelCategory> categories) {
        this.categories = categories;
    }

    public KeyframeManifest() {
        this(new ArrayList<>());
    }

    public KeyframeManifest copy() {
        List<KeyChannelCategory> copied = new ArrayList<>(categories.size());
        for (var cat : categories) {
            copied.add(cat.copy());
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
    public @Nullable Keyframe getKeyframe(int category, int channel, int keyframe) {
        if (category < 0 || channel < 0 || keyframe < 0 || category >= categories.size()) {
            return null;
        }
        var cat = categories.get(category);
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
