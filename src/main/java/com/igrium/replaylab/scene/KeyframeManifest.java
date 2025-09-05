package com.igrium.replaylab.scene;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
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

    /**
     * A category of keyframe channels, each making up a single object (3D vector, etc)
     */
    public static class KeyframeCategory {
        @NonNull
        @Getter @Setter
        private String name = "";

        @Getter
        private final List<KeyframeChannel> channels;

        protected KeyframeCategory(List<KeyframeChannel> channels) {
            this.channels = channels;
        }

        public KeyframeCategory() {
            this.channels = new ArrayList<>();
        }

        public KeyframeCategory copy() {
            List<KeyframeChannel> copied = new ArrayList<>(channels.size());
            for (KeyframeChannel channel : channels) {
                copied.add(channel.copy());
            }
            return new KeyframeCategory(copied);
        }
    }

    /**
     * A single "channel" of keyframes. A given channel always contain a single curve of scalar values.
     * If you need to tie multiple values together (like a vector), use a {@link KeyframeCategory}
     */
    public static class KeyframeChannel {

        @NonNull
        @Getter @Setter
        private String name = "";

        @Getter
        private final List<Keyframe> keys;

        protected KeyframeChannel(List<Keyframe> keyframes) {
            this.keys = keyframes;
        }

        public KeyframeChannel() {
            this.keys = new ArrayList<>();
        }

        public KeyframeChannel copy() {
            List<Keyframe> copied = new ArrayList<>(keys.size());
            for (Keyframe key : keys) {
                copied.add(new Keyframe(key));
            }
            return new KeyframeChannel(copied);
        }
    }

    /**
     * A single keyframe in the timeline. Contains a time, scalar value, and any curve attributes.
     */
    @Getter @Setter
    @AllArgsConstructor
    public static class Keyframe {
        private double time;
        private double value;

        public Keyframe(Keyframe other) {
            this(other.time, other.value);
        }

        public void copyFrom(Keyframe other) {
            this.time = other.time;
            this.value = other.value;
        }
    }

    @Getter
    private final List<KeyframeCategory> categories;


    protected KeyframeManifest(List<KeyframeCategory> categories) {
        this.categories = categories;
    }

    public KeyframeManifest() {
        this(new ArrayList<>());
    }

    public KeyframeManifest copy() {
        List<KeyframeCategory> copied = new ArrayList<>(categories.size());
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
        if (channel >= cat.channels.size()) {
            return null;
        }

        var ch = cat.channels.get(channel);

        if (keyframe >= ch.keys.size()) {
            return null;
        }

        return ch.keys.get(keyframe);
    }

}
