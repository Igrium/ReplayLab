package com.igrium.replaylab.ui;

import com.igrium.replaylab.scene.key.KeyChannel;
import it.unimi.dsi.fastutil.ints.*;
import org.apache.commons.lang3.function.TriConsumer;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Provides easy access to a set of selected object, keyframes, and then keyframe handles
 */
public class KeySelectionSet {
    public record ChannelReference(String objectName, String channelName) {
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
    }

    ;

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
    }

    public interface KeyRefConsumer {
        void accept(String objectName, String channelName, int keyIndex);
    }

    public interface HandleRefConsumer {
        void accept(String objectName, String channelName, int keyIndex, int handleIndex);
    }

    private final Map<String, Map<String, Int2ObjectMap<IntSet>>> selected = new HashMap<>();

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
        forSelectedKeyframes((obj, ch, key) -> c.accept(new KeyframeReference(obj, ch, key)));
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
        for (var objEntry : selected.entrySet()) {
            for (var chEntry : objEntry.getValue().entrySet()) {
                for (var keyEntry : chEntry.getValue().int2ObjectEntrySet()) {
                    IntIterator iter = keyEntry.getValue().intIterator();
                    while (iter.hasNext()) {
                        c.accept(objEntry.getKey(), chEntry.getKey(), keyEntry.getIntKey(), iter.nextInt());
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
}
