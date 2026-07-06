package com.igrium.replaylab.scene.key;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import com.igrium.replaylab.ReplayLab;
import com.igrium.replaylab.config.ReplayLabConfig;
import com.igrium.replaylab.editor.KeySelectionSet;
import com.igrium.replaylab.math.Bezier2d;
import com.igrium.replaylab.math.Beziers;
import it.unimi.dsi.fastutil.ints.*;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Type;
import java.util.*;
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
     * @return The index of the new keyframe (likely at the end)
     */
    public int addKeyframe(int timestamp, double value) {
        int i = 0;
        for (var key : keyframes) {
            if (key.getTimeInt() == timestamp) {
                key.setValue(value);
                return i;
            }
            i++;
        }

        Keyframe keyframe = new Keyframe(timestamp, value);
        keyframe.setHandleType(ReplayLabConfig.getInstance().getDefaultHandleType());
        this.keyframes.add(keyframe);
        
        ChannelUtils.computeHandles(this, null);
        return keyframes.size() - 1;
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
     * Sort all the keyframes in this channel. Although not required,
     * Improves the performance of calls to <code>sample</code>.
     *
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
            keyframes.sort(Comparator.comparing(Keyframe::getTime));
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
     *
     * @param timestamp Timestamp to sample at.
     * @return The scalar value of the curve at that time.
     */
    public double sample(int timestamp) {
        // Because of how selections are handled, we need to ensure the keyframe list is sorted each frame (boo)
        // Optimizations in the dope sheet should ensure that they're usually pre-sorted, so we just need to check.
        Keyframe[] keys = this.keyframes.toArray(Keyframe[]::new);
        Arrays.sort(keys);
        return sample(keys, timestamp);

    }

    /**
     * Sample a channel curve at a given point
     *
     * @param keys      Sorted array of keyframes
     * @param timestamp Timestamp to sample at
     * @return The scalar value of the curve at that time
     */
    public static double sample(Keyframe[] keys, int timestamp) {
        if (keys.length == 0)
            return 0;
        else if (keys.length == 1)
            return keys[0].getValue();

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

        return key.getInterpolationMode().sample(key, next, timestamp);
    }

    public boolean isEmpty() {
        return keyframes.isEmpty();
    }

    /**
     * Compute the maximum handle value.
     * @return The maximum handle value. <code>0</code> if there are no keyframes.
     */
    public double getMaxHandle() {
        boolean found = false;
        double val = 0;

        for (var key : keyframes) {
            if (!found || key.getValue() > val) {
                found = true;
                val = key.getValue();
            }
            // Found is always true at this point
            val = Math.max(val, key.getGlobalAY());
            val = Math.max(val, key.getGlobalBY());
        }
        return val;
    }

    /**
     * Compute the minimum handle value.
     * @return The minimum handle value. <code>0</code> if there are no keyframes.
     */
    public double getMinHandle() {
        boolean found = false;
        double val = 0;

        for (var key : keyframes) {
            if (!found || key.getValue() < val) {
                found = true;
                val = key.getValue();
            }
            // Found is always true at this point
            val = Math.min(val, key.getGlobalAY());
            val = Math.min(val, key.getGlobalBY());
        }
        return val;
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
     * Copy a selection of keyframes to the clipboard
     * @param playhead The current playhead position
     * @param indices Indices of the keyframes to copy. <code>null</code> to copy all of them.
     * @return A json array ready to be serialized to the clipboard
     */
    public JsonArray copyToClipboard(int playhead, @Nullable IntCollection indices) {
        JsonArray array = new JsonArray();
        IntIterator iter;
        if (indices != null && !indices.isEmpty()) {
            iter = indices.iterator();
        } else {
            iter = IntIterators.fromTo(0, keyframes.size());
        }

        Gson gson = new Gson();

        while (iter.hasNext()) {
            int index = iter.nextInt();
            var key = new Keyframe(keyframes.get(index));
            key.getCenter().x -= playhead;
            array.add(gson.toJsonTree(key));
        }

        return array;
    }

    /**
     * Paste keyframes from the clipboard
     * @param playhead The current playhead position
     * @param array Serialized list of keyframes
     * @return The indices of the newly-pasted keyframes in the keyframe list
     */
    public IntList pasteFromClipboard(int playhead, JsonArray array) {
        if (array.isEmpty()) return IntList.of();

        Gson gson = new Gson();
        Int2IntMap existing = new Int2IntOpenHashMap();
        IntList added = new IntArrayList();

        for (int i = 0; i < keyframes.size(); i++) {
            existing.put(keyframes.get(i).getTimeInt(), i);
        }

        for (var element : array) {
            Keyframe key = gson.fromJson(element, Keyframe.class);
            key.getCenter().x += playhead;

            int idx;
            if (existing.containsKey(key.getTimeInt())) {
                idx = existing.get(key.getTimeInt());
                keyframes.get(idx).copyFrom(key);
            } else {
                idx = keyframes.size();
                keyframes.add(key);
                existing.put(key.getTimeInt(), idx);
            }
            added.add(idx);
        }

        ChannelUtils.computeHandles(this, null);

        return added;
    }

    /**
     * Compute the integral of the sampled curve from time 0 to a certain time.
     * Matches the semantics of <code>sample</code>: the curve extrapolates as a
     * constant outside the keyed range.
     *
     * @param maxTime Timestamp to integrate to
     * @return The integral of the sampled curve on <code>[0, maxTime]</code>
     * @apiNote Does not take modifiers into account
     * @implNote Like the other sampling methods, this works best if the channel has been sorted.
     */
    public double integrate(double maxTime) {
        Keyframe[] keys = keyframes.toArray(Keyframe[]::new);
        Arrays.sort(keys);
        return integrate(keys, maxTime);
    }


    /**
     * Compute the integral of the sampled curve from time 0 to a certain time.
     * Matches the semantics of <code>sample</code>: the curve extrapolates as a
     * constant outside the keyed range.
     *
     * @param keys    Sorted array of keyframes
     * @param maxTime Timestamp to integrate to
     * @return The integral of the sampled curve on <code>[0, maxTime]</code>
     */
    public static double integrate(Keyframe[] keys, double maxTime) {
        if (keys.length == 0) return 0;
        if (keys.length == 1) return keys[0].getValue() * maxTime;

        double integral = 0;

        // sample() clamps to the first key's value before its time,
        // so that region integrates as a constant from t=0
        double firstTime = keys[0].getTime();
        if (firstTime > 0) {
            integral += keys[0].getValue() * Math.min(maxTime, firstTime);
        }

        for (int i = 0; i < keys.length - 1; i++) {
            Keyframe key = keys[i];
            Keyframe next = keys[i + 1];
            if (key.getTime() >= maxTime) break;

            integral += key.getInterpolationMode().integrate(key, next, maxTime);
        }

        // Same clamping past the last key
        Keyframe last = keys[keys.length - 1];
        if (maxTime > last.getTime()) {
            integral += last.getValue() * (maxTime - last.getTime());
        }

        return integral;
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
        keys.sort(Comparator.comparing(Keyframe::getTime));

        return new KeyChannel(keys);
    }

    @Override
    public JsonElement serialize(KeyChannel src, Type typeOfSrc, JsonSerializationContext context) {
        return context.serialize(src.getKeyframes());
    }
}

