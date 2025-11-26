package com.igrium.replaylab.scene.key;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import com.igrium.replaylab.ReplayLab;
import com.igrium.replaylab.debug.TimelineDebugScreen;
import com.igrium.replaylab.editor.KeySelectionSet;
import com.igrium.replaylab.math.Bezier2d;
import com.igrium.replaylab.math.Bezier2dc;
import com.igrium.replaylab.math.Beziers;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.slf4j.Logger;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * A single "channel" of keyframes. A given channel always contain a single curve of scalar values.
 */
@JsonAdapter(KeyChannelSerializer.class)
public class KeyChannel {

    private static final Logger LOGGER = ReplayLab.getLogger("KeyChannel");

    @Getter
    private final List<Keyframe> keyframes;

    /**
     * Prevent this channel from being modified. Not serialized or included in the undo/redo stack
     */
    @Getter @Setter
    private transient boolean locked;

    /**
     * Hide this channel in the dope sheet and curve editor. Not serialized or included in the undo/redo stack.
     */
    @Getter @Setter
    private transient boolean hidden;

    public KeyChannel() {
        this(new ArrayList<>());
    }

    protected KeyChannel(List<Keyframe> keyframes) {
        this.keyframes = keyframes;
    }

    /**
     * Add a keyframe to this channel.
     * If there is already a keyframe at that timestamp, replace its value with the new value.
     * @param timestamp Timestamp to add at.
     * @param value Value to give the new keyframe. <code>NaN</code> to automatically generate.
     * @return A reference to the added keyframe. Use to adjust interpolation, etc.
     */
    public Keyframe addKeyframe(int timestamp, double value) {
        for (var key : keyframes) {
            if (key.getTimeInt() == timestamp) {
                key.setValue(value);
                return key;
            }
        }

        Keyframe keyframe = new Keyframe(timestamp, value);
        this.keyframes.add(keyframe);
        ChannelUtils.computeAutoHandles(this, null);
        return keyframe;
    }

    /**
     * Remove all keyframes with duplicate timestamps.
     * All keyframe references should be considered invalid if this method returns true.
     * @return If any duplicates were found.
     */
    public boolean removeDuplicates() {
        IntSet occupied = new IntAVLTreeSet();
        boolean success = false;

        var iter = keyframes.iterator();
        while (iter.hasNext()) {
            Keyframe key = iter.next();

            if (occupied.contains(key.getTimeInt())) {
                iter.remove();
                success = true;
            } else {
                occupied.add(key.getTimeInt());
            }
        }
        return success;
    }

    /**
     * Sort all the keyframes in this channel. Although not required, Improves the performance of calls to <code>sample</code>.
     * @return an array such that <code>result[newIndex] = oldIndex</code> for every keyframe in the channel.
     */
    public int[] sortKeys() {
        int[] sortedIndices = IntStream.range(0, keyframes.size()).boxed()
                .sorted(Comparator.comparing(keyframes::get))
                .mapToInt(Integer::intValue).toArray();

        Keyframe[] prev = keyframes.toArray(Keyframe[]::new);
        for (int i = 0; i < prev.length; i++) {
            keyframes.set(i, prev[sortedIndices[i]]);
        }

        return sortedIndices;
    }

    /**
     * Sort all the keyframes in this channel by their time. Causes subsequent samples to run in linear time.
     *
     * @param selection Modify the selection to reference new mappings
     * @param objName   The name of the object this channel is in. Should be non-null of <code>selection</code> is non-null.
     * @param chName    The name of this channel. Should be non-null if <code>selection</code> is non-null.
     */
    public void sortKeys(@Nullable KeySelectionSet selection, @Nullable String objName, @Nullable String chName) {
        // Shortcut if we don't need to keep track of selection
        if (selection == null) {
            keyframes.sort(Comparator.comparing(k -> k));
            return;
        }

        int[] sorted = sortKeys(); // sorted[newIndex] = oldIndex

        int[] newIndexMapping = new int[sorted.length];
        for (int i = 0; i < sorted.length; i++) {
            newIndexMapping[sorted[i]] = i;
        }

        selection.remapSelection(objName, chName, newIndexMapping);
    }

    /**
     * Sample the curve at a given timestamp.
     * @param timestamp Timestamp to sample at.
     * @return The scalar value of the curve at that time.
     */
    public double sample(int timestamp) {
        // Because of how selections are handled, we need to ensure the keyframe list is sorted each frame (boo)
        // Optimizations in the dope sheet should ensure that they're usually pre-sorted, so we just need to check.
        Keyframe[] keys = this.keyframes.toArray(Keyframe[]::new);
        if (keys.length == 0)
            return 0;
        else if (keys.length == 1)
            return keys[0].getValue();

        Arrays.sort(keys);

        // If out of bounds
        if (timestamp <= keys[0].getTime()) {
            return keys[0].getValue();
        } else if (timestamp >= keys[keys.length-1].getTime()) {
            return keys[keys.length-1].getValue();
        }

        int keyIndex = findKeyIndex(keys, timestamp);
        if (keyIndex < 0) {
            // Should have been taken care of by out-of-bounds check
            assert false : "findKeyIndex returned -1 despite timestamp being in range";
            return 0;
        }

        int nextIndex = keyIndex + 1;
        if (nextIndex >= keys.length) {
            return keys[keyIndex].getValue();
        }

        Keyframe key = keys[keyIndex];
        Keyframe next = keys[nextIndex];

        Bezier2d bezier = Beziers.fromKeyframes(key, next, new Bezier2d());
        double t = Beziers.intersectX(bezier, timestamp);
        return bezier.sampleY(t);
    }
    /**
     * Find the index of the keyframe directly to the left of the given timestamp.
     * Specifically, return the index of the greatest key less than or equal to the timestamp.
     *
     * @param keys      Sorted keyframe list.
     * @param timestamp Timestamp to check
     * @return The index, or <code>-1</code> if no keyframe was found.
     */
    private static int findKeyIndex(Keyframe[] keys, int timestamp) {
        if (keys.length == 0) return -1;

        int left = 0;
        int right = keys.length - 1;

        if (keys[left].getTimeInt() > timestamp) {
            return -1;
        }
        if (keys[right].getTimeInt() <= timestamp) {
            return right;
        }
        // Modified binary search
        while (right - left > 1) {
            int mid = left + (right - left) / 2;
            if (keys[mid].getTimeInt() <= timestamp) { // too low
                left = mid;
            } else if (keys[mid].getTimeInt() > timestamp) { // too high
                right = mid;
            }
        }
        return left;
    }


    /**
     * Make a deep copy of this channel.
     */
    public KeyChannel copy() {
        List<Keyframe> copied = new ArrayList<>(keyframes.size());
        for (Keyframe key : keyframes) {
            copied.add(new Keyframe(key));
        }
        var ch = new KeyChannel(copied);
        ch.locked = locked;
        return ch;
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
        return context.serialize(src.getKeyframes());
    }
}

