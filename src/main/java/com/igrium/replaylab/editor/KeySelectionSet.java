package com.igrium.replaylab.editor;

import com.google.common.collect.AbstractIterator;
import com.igrium.replaylab.scene.key.KeyChannel;
import com.igrium.replaylab.scene.key.Keyframe;
import com.igrium.replaylab.scene.obj.ReplayObject;
import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector2dc;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Provides easy access to a set of selected object, keyframes, and then keyframe handles
 */
public class KeySelectionSet {
    public record ChannelReference(String objectName, String channelName) {
        public @Nullable KeyChannel get(Map<? extends String, ? extends ReplayObject> objects) {
            var obj = objects.get(objectName);
            return obj != null ? obj.getChannels().get(channelName) : null;
        }

        public boolean equals(String objectName, String channelName) {
            return Objects.equals(this.objectName, objectName) && Objects.equals(this.channelName, channelName);
        }
    }

    public record KeyframeReference(ChannelReference channelRef, int keyIndex) {
        public KeyframeReference(String objName, String channelName, int keyIndex) {
            this(new ChannelReference(objName, channelName), keyIndex);
        }

        public String objectName() {
            return channelRef.objectName();
        }

        public String channelName() {
            return channelRef.channelName();
        }

        public @Nullable Keyframe get(Map<? extends String, ? extends ReplayObject> objects) {
            var ch = channelRef.get(objects);
            if (ch == null) return null;
            List<Keyframe> keys = ch.getKeyframes();
            return (0 <= keyIndex && keyIndex < keys.size()) ? keys.get(keyIndex) : null;
        }

        public boolean equals(String objName, String channelName, int keyIndex) {
            return channelRef.equals(objName, channelName) && this.keyIndex == keyIndex;
        }
    }

    public record KeyHandleReference(KeyframeReference keyRef, int handleIndex) {
        public KeyHandleReference(String objName, String channelName, int keyIndex, int handleIndex) {
            this(new KeyframeReference(objName, channelName, keyIndex), handleIndex);
        }

        public String objectName() {
            return keyRef.objectName();
        }

        public String channelName() {
            return keyRef.channelName();
        }

        public int keyIndex() {
            return keyRef.keyIndex();
        }

        public @Nullable Vector2dc getLocal(Map<? extends String, ? extends ReplayObject> objects) {
            var key = keyRef.get(objects);
            if (key == null) return null;
            
            return switch (handleIndex) {
                case 0 -> key.getCenter();
                case 1 -> key.getHandleA();
                case 2 -> key.getHandleB();
                default -> null;
            };
        }

        public @Nullable Vector2d get(Map<? extends String, ? extends ReplayObject> objects) {
            Vector2d dest = new Vector2d();
            return get(objects, dest) ? dest : null;
        }

        public boolean get(Map<? extends String, ? extends ReplayObject> objects, Vector2d dest) {
            var key = keyRef.get(objects);
            if (key == null) return false;

            switch(handleIndex) {
                case 0 -> dest.set(key.getCenter());
                case 1 -> key.getGlobalA(dest);
                case 2 -> key.getGlobalB(dest);
                default -> {
                    return false;
                }
            }
            return true;
        }

        public boolean equals(String objName, String channelName, int keyIndex, int handleIndex) {
            return keyRef.equals(objName, channelName, keyIndex) && this.handleIndex == handleIndex;
        }

        public boolean equals(KeyframeReference keyRef, int handleIndex) {
            return Objects.equals(this.keyRef, keyRef) && this.handleIndex == handleIndex;
        }
    }

    public interface KeyRefConsumer {
        void accept(String objectName, String channelName, int keyIndex);
    }

    public interface HandleRefConsumer {
        void accept(String objectName, String channelName, int keyIndex, int handleIndex);
    }

    private final Map<String, Map<String, Int2ObjectMap<IntSet>>> selected;

    public KeySelectionSet() {
        this.selected = new HashMap<>();
    }

    private KeySelectionSet(KeySelectionSet contents) {
        this.selected = deepCopy(contents.selected);
    }

    /**
     * Iterate over every object with a selected element.
     *
     * @param c Consumer
     */
    public void forSelectedObjects(Consumer<? super String> c) {
        selected.keySet().forEach(c);
    }

    /**
     * Get a set of every object with a selected element.
     *
     * @return Immutable set of selected objects.
     */
    public Set<String> getSelectedObjects() {
        return Set.copyOf(selected.keySet());
    }

    /**
     * Iterate over every channel in an object with a selected element.
     *
     * @param object Object to iterate through.
     * @param c      Consumer
     */
    public void forSelectedChannels(String object, Consumer<? super String> c) {
        var map = selected.get(object);
        if (map != null) {
            map.keySet().forEach(c);
        }
    }

    /**
     * Get a set of every channel in an object with a selected element.
     *
     * @param object Object to iterate through.
     * @return Immutable set of channels.
     */
    public Set<String> getSelectedChannels(String object) {
        var map = selected.get(object);
        return map != null ? Set.copyOf(map.keySet()) : Set.of();
    }

    /**
     * Iterate over every channel in the scene with a selected element.
     *
     * @param c A consumer that accepts an object and its selected channels.
     */
    public void forSelectedChannels(BiConsumer<? super String, ? super Set<String>> c) {
        for (var entry : selected.entrySet()) {
            c.accept(entry.getKey(), Collections.unmodifiableSet(entry.getValue().keySet()));
        }
    }

    /**
     * Get a set of every channel in the scene with a selected element.
     *
     * @return Immutable map of objects and their selected channels.
     */
    public Map<String, Set<String>> getSelectedChannels() {
        Map<String, Set<String>> dest = new HashMap<>();
        forSelectedChannels(dest::put);
        return dest;
    }

    /**
     * Iterate over all selected keyframes in the scene.
     *
     * @param c Consumer
     */
    public void forSelectedKeyframes(KeyRefConsumer c) {
        for (var objEntry : selected.entrySet()) {
            for (var chEntry : objEntry.getValue().entrySet()) {
                IntIterator iter = chEntry.getValue().keySet().intIterator();
                while (iter.hasNext()) {
                    c.accept(objEntry.getKey(), chEntry.getKey(), iter.nextInt());
                }
            }
        }
    }

    /**
     * Iterate over all selected keyframes in the scene.
     *
     * @param c Consumer
     */
    public void forSelectedKeyframes(Consumer<? super KeyframeReference> c) {
        for (var objEntry : selected.entrySet()) {
            for (var chEntry : objEntry.getValue().entrySet()) {
                ChannelReference chRef = new ChannelReference(objEntry.getKey(), chEntry.getKey());
                IntIterator iter = chEntry.getValue().keySet().intIterator();
                while (iter.hasNext()) {
                    c.accept(new KeyframeReference(chRef, iter.nextInt()));
                }
            }
        }
    }

    /**
     * Get a set of every selected keyframe in the scene.
     *
     * @return Immutable set of selected keyframe references.
     */
    public Set<KeyframeReference> getSelectedKeyframes() {
        Set<KeyframeReference> dest = new HashSet<>();
        forSelectedKeyframes(dest::add);
        return dest;
    }


    /**
     * Iterate over all selected handles in the scene.
     *
     * @param c Consumer
     */
    public void forSelectedHandles(HandleRefConsumer c) {
        forSelectedHandles(c, false);
    }

    public void forSelectedHandles(HandleRefConsumer c, boolean includeChildren) {
        for (var objEntry : selected.entrySet()) {
            for (var chEntry : objEntry.getValue().entrySet()) {
                for (var keyEntry : chEntry.getValue().int2ObjectEntrySet()) {
                    // If this is the center and we're including children, return everything.
                    if (includeChildren && keyEntry.getValue().contains(0)) {
                        c.accept(objEntry.getKey(), chEntry.getKey(), keyEntry.getIntKey(), 0);
                        c.accept(objEntry.getKey(), chEntry.getKey(), keyEntry.getIntKey(), 1);
                        c.accept(objEntry.getKey(), chEntry.getKey(), keyEntry.getIntKey(), 2);
                    } else {
                        IntIterator iter = keyEntry.getValue().intIterator();
                        while (iter.hasNext()) {
                            c.accept(objEntry.getKey(), chEntry.getKey(), keyEntry.getIntKey(), iter.nextInt());
                        }
                    }
                }
            }
        }
    }

    /**
     * Iterate over all selected handles in the scene
     *
     * @param c Consumer
     */
    public void forSelectedHandles(Consumer<KeyHandleReference> c) {
        forSelectedHandles((obj, ch, key, handle) -> c.accept(new KeyHandleReference(obj, ch, key, handle)));
    }

    public void forSelectedHandles(Consumer<KeyHandleReference> c, boolean includeChildren) {
        forSelectedHandles((obj, ch, key, handle) -> c.accept(new KeyHandleReference(obj, ch, key, handle)), true);
    }

    /**
     * Get a set of all selected handles in the scene
     *
     * @return Immutable set of all selected handles
     */
    public Set<KeyHandleReference> getSelectedHandles() {
        Set<KeyHandleReference> dest = new HashSet<>();
        forSelectedHandles(dest::add);
        return dest;
    }

    /**
     * Get a set of all "effectively" selected handles,
     * meaning that a center handle will treat its two children as selected rather than selecting itself.
     * @return Immutable set of all selected handles
     */
    public Set<KeyHandleReference> effectiveSelectedHandles() {
        Set<KeyHandleReference> dest = new HashSet<>();
        forSelectedHandles(ref -> {
            if (ref.handleIndex == 0) {
                dest.add(new KeyHandleReference(ref.keyRef, 1));
                dest.add(new KeyHandleReference(ref.keyRef, 2));
            } else {
                dest.add(ref);
            }
        });
        return dest;
    }

    public boolean isEmpty() {
        return selected.isEmpty();
    }

    /**
     * Check if a given object has any selected elements.
     *
     * @param objName The object name.
     * @return <code>true</code> if the object has at least one selected element.
     */
    public boolean isObjectSelected(String objName) {
        return selected.containsKey(objName);
    }

    /**
     * Check if a given channel has any selected keyframes.
     *
     * @param objName The object name
     * @param chName  The channel name
     * @return <code>true</code> if the channel has at least one selected keyframe.
     */
    public boolean isChannelSelected(String objName, String chName) {
        var object = selected.get(objName);
        return object != null && object.containsKey(chName);
    }

    /**
     * Check if a given channel has any selected keyframes.
     *
     * @param ref The channel reference.
     * @return <code>true</code> if the channel has at least one selected keyframe.
     */
    public boolean isChannelSelected(ChannelReference ref) {
        return isChannelSelected(ref.objectName(), ref.channelName());
    }

    /**
     * Check if a given keyframe has any selected handles.
     *
     * @param objName The object name
     * @param chName  The channel name
     * @param keyIdx  The keyframe's index within the channel
     * @return <code>true</code> if the channel has at least one selected keyframe.
     */
    public boolean isKeyframeSelected(String objName, String chName, int keyIdx) {
        var object = selected.get(objName);
        if (object == null) return false;

        var channel = object.get(chName);
        return channel != null && channel.containsKey(keyIdx);
    }

    /**
     * Check if a given keyframe has any selected handles.
     *
     * @param ref The handle reference
     * @return <code>true</code> if the channel has at least one selected keyframe.
     */
    public boolean isKeyframeSelected(KeyframeReference ref) {
        return isKeyframeSelected(ref.objectName(), ref.channelName(), ref.keyIndex());
    }

    /**
     * Check if a given handle is selected.
     *
     * @param objName   The object name
     * @param chName    The channel name
     * @param keyIdx    The keyframe's index within the channel
     * @param handleIdx The handle's index
     * @return <code>true</code> if that handle is selected.
     */
    public boolean isHandleSelected(String objName, String chName, int keyIdx, int handleIdx) {
        var object = selected.get(objName);
        if (object == null) return false;

        var channel = object.get(chName);
        if (channel == null) return false;

        var key = channel.get(keyIdx);
        return key != null && key.contains(handleIdx);
    }

    /**
     * Check if a given handle is selected.
     *
     * @param ref The handle reference
     * @return <code>true</code> if that handle is selected.
     */
    public boolean isHandleSelected(KeyHandleReference ref) {
        return isHandleSelected(ref.objectName(), ref.channelName(), ref.keyIndex(), ref.handleIndex());
    }

    /**
     * Select a handle.
     *
     * @param objName   The object name
     * @param chName    The channel name
     * @param keyIdx    The keyframe's index within the channel
     * @param handleIdx The handle's index
     * @return <code>true</code> if the handle was not already selected.
     */
    public boolean selectHandle(String objName, String chName, int keyIdx, int handleIdx) {
        var object = selected.computeIfAbsent(objName, v -> new HashMap<>());
        var channel = object.computeIfAbsent(chName, v -> new Int2ObjectAVLTreeMap<>());
        var key = channel.computeIfAbsent(keyIdx, v -> new IntArraySet());

        return key.add(handleIdx);
    }

    /**
     * Select a handle
     *
     * @param ref       The keyframe reference
     * @param handleIdx The handle's index
     * @return <code>true</code> if the handle was not already selected.
     */
    public boolean selectHandle(KeyframeReference ref, int handleIdx) {
        return selectHandle(ref.objectName(), ref.channelName(), ref.keyIndex(), handleIdx);
    }

    /**
     * Select a handle.
     *
     * @param ref The handle reference
     * @return <code>true</code> if the handle was not already selected.
     */
    public boolean selectHandle(KeyHandleReference ref) {
        return selectHandle(ref.objectName(), ref.channelName(), ref.keyIndex(), ref.handleIndex());
    }

    /**
     * Deselect a handle
     *
     * @param objName   The object name
     * @param chName    The channel name
     * @param keyIdx    The keyframe's index within the channel
     * @param handleIdx The handle's index
     * @return <code>true</code> if the handle was previously selected.
     */
    public boolean deselectHandle(String objName, String chName, int keyIdx, int handleIdx) {
        var object = selected.get(objName);
        if (object == null) return false;

        var channel = object.get(chName);
        if (channel == null) return false;

        var key = channel.get(keyIdx);
        if (key == null) return false;

        if (!key.remove(handleIdx)) return false;

        if (key.isEmpty()) {
            channel.remove(keyIdx);
            if (channel.isEmpty()) {
                object.remove(chName);
                if (object.isEmpty()) {
                    selected.remove(objName);
                }
            }
        }

        return true;
    }

    /**
     * Deselect a handle
     *
     * @param ref The handle reference
     * @return <code>true</code> if the handle was previously selected.
     */
    public boolean deselectHandle(KeyHandleReference ref) {
        return deselectHandle(ref.objectName(), ref.channelName(), ref.keyIndex(), ref.handleIndex());
    }

    /**
     * Select all the handles in a keyframe.
     *
     * @param objName The object name
     * @param chName  The channel name
     * @param keyIdx  The keyframe's index within the channel.
     * @return <code>true</code> if the selection was modified as a result of this call.
     */
    public boolean selectKeyframe(String objName, String chName, int keyIdx) {
        var object = selected.computeIfAbsent(objName, v -> new HashMap<>());
        var channel = object.computeIfAbsent(chName, v -> new Int2ObjectAVLTreeMap<>());
        var key = channel.computeIfAbsent(keyIdx, v -> new IntArraySet());

        boolean success = key.add(0);
        if (key.add(1)) success = true;
        if (key.add(2)) success = true;

        return success;
    }

    /**
     * Select all handles in a keyframe
     *
     * @param ref The keyframe reference
     * @return @return <code>true</code> if the selection was modified as a result of this call.
     */
    public boolean selectKeyframe(KeyframeReference ref) {
        return selectKeyframe(ref.objectName(), ref.channelName(), ref.keyIndex());
    }

    /**
     * Remap the selection references of a channel after it has been sorted.
     *
     * @param objName The object name
     * @param chName  The channel name
     * @param mapping An array where <code>mapping[oldIndex] = newIndex</code>
     */
    public void remapSelection(String objName, String chName, int[] mapping) {
        Map<String, Int2ObjectMap<IntSet>> obj = selected.get(objName);
        if (obj == null) return;

        Int2ObjectMap<IntSet> ch = obj.get(chName);
        if (ch == null) return;

        Int2ObjectMap<IntSet> newCh = new Int2ObjectAVLTreeMap<>();
        for (var entry : ch.int2ObjectEntrySet()) {
            int oldIndex = entry.getIntKey();
            if (0 <= oldIndex && oldIndex < mapping.length) {
                newCh.put(mapping[oldIndex], entry.getValue());
            }
        }

        obj.put(chName, newCh);
    }

    /**
     * Deselect all the handles in a keyframe.
     *
     * @param objName The object name
     * @param chName  The channel name
     * @param keyIdx  The keyframe's index within the channel
     * @return <code>true</code> if the selection was modified as a result of this call.
     */
    public boolean deselectKeyframe(String objName, String chName, int keyIdx) {
        var object = selected.get(objName);
        if (object == null) return false;

        var channel = object.get(chName);
        if (channel == null) return false;

        if (channel.remove(keyIdx) == null) return false;

        if (channel.isEmpty()) {
            object.remove(chName);
            if (object.isEmpty()) {
                selected.remove(objName);
            }
        }
        return true;
    }

    /**
     * Deselect all the handles in a keyframe.
     *
     * @param ref The keyframe reference
     * @return <code>true</code> if the selection was modified as a result of this call.
     */
    public boolean deselectKeyframe(KeyframeReference ref) {
        return deselectKeyframe(ref.objectName(), ref.channelName(), ref.keyIndex());
    }

    /**
     * Select all keyframes within a channel
     *
     * @param objName      The object name
     * @param chName       THe channel name
     * @param numKeyframes The total number of keyframes in this channel
     * @return <code>true</code> if the selection was modified as a result of this call.
     */
    public boolean selectChannel(String objName, String chName, int numKeyframes) {
        var object = selected.computeIfAbsent(objName, v -> new HashMap<>());
        var channel = object.computeIfAbsent(chName, v -> new Int2ObjectAVLTreeMap<>());

        boolean success = false;
        for (int i = 0; i < numKeyframes; i++) {
            var key = channel.computeIfAbsent(i, v -> new IntArraySet());
            if (key.add(0)) success = true;
            if (key.add(1)) success = true;
            if (key.add(2)) success = true;
        }

        return success;
    }

    /**
     * Select all keyframes within a channel
     *
     * @param ref          The channel reference
     * @param numKeyframes The total number of keyframes in this channel
     * @return <code>true</code> if the selection was modified as a result of this call.
     */
    public boolean selectChannel(ChannelReference ref, int numKeyframes) {
        return selectChannel(ref.objectName(), ref.channelName(), numKeyframes);
    }

    /**
     * Deselect all keyframes within a channel
     *
     * @param objName The object name
     * @param chName  The channel name
     * @return <code>true</code> if the selection was modified as a result of this call.
     */
    public boolean deselectChannel(String objName, String chName) {
        var object = selected.get(objName);
        if (object == null) return false;

        if (object.remove(chName) == null) return false;

        if (object.isEmpty()) {
            selected.remove(objName);
        }
        return true;
    }

    /**
     * Deselect all keyframes within a channel
     *
     * @param ref The channel reference
     * @return <code>true</code> if the selection was modified as a result of this call.
     */
    public boolean deselectChannel(ChannelReference ref) {
        return deselectChannel(ref.objectName(), ref.channelName());
    }

    /**
     * Deselect all keyframes belonging to a given object
     *
     * @param objName The object name
     * @return <code>true</code> if the selection was modified as a result of this call.
     * @apiNote No <code>selectObject</code> method is provided because channels can vary in terms of length
     */
    public boolean deselectObject(String objName) {
        return selected.remove(objName) != null;
    }

    /**
     * Deselect the entire selection.
     * @return If there was
     */
    public boolean deselectAll() {
        boolean success = !selected.isEmpty();
        selected.clear();
        return success;
    }

    private static Map<String, Map<String, Int2ObjectMap<IntSet>>> deepCopy(Map<String, Map<String, Int2ObjectMap<IntSet>>> src) {
        Map<String, Map<String, Int2ObjectMap<IntSet>>> dest = new HashMap<>(src.size());
        for (var objEntry : src.entrySet()) {
            Map<String, Int2ObjectMap<IntSet>> object = new HashMap<>(objEntry.getValue().size());
            for (var chEntry : objEntry.getValue().entrySet()) {
                Int2ObjectMap<IntSet> ch = new Int2ObjectAVLTreeMap<>();
                for (var keyEntry : chEntry.getValue().int2ObjectEntrySet()) {
                    ch.put(keyEntry.getIntKey(), new IntArraySet(keyEntry.getValue()));
                }
                object.put(chEntry.getKey(), ch);
            }
            dest.put(objEntry.getKey(), object);
        }
        return dest;
    }
}
