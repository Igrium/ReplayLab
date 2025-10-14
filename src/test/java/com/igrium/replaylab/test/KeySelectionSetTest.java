package com.igrium.replaylab.test;

import com.igrium.replaylab.editor.KeySelectionSet;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.Map;

class KeySelectionSetTest {
    private KeySelectionSet set;

    @BeforeEach
    void setUp() {
        set = new KeySelectionSet();
    }

    @Test
    void testSelectAndDeselectHandle() {
        KeySelectionSet.KeyHandleReference ref = new KeySelectionSet.KeyHandleReference("obj", "chan", 1, 2);

        // Select handle via wrapper
        assertTrue(set.selectHandle(ref));
        // Selecting again should return false since it's already selected.
        assertFalse(set.selectHandle(ref));
        // Handle should be selected
        assertTrue(set.isHandleSelected(ref));
        // Deselect handle via wrapper
        assertTrue(set.deselectHandle(ref));
        // Deselecting again should return false since it's already deselected.
        assertFalse(set.deselectHandle(ref));
        // Should no longer be selected
        assertFalse(set.isHandleSelected(ref));
    }

    @Test
    void testSelectAndDeselectKeyframe() {
        KeySelectionSet.KeyframeReference ref = new KeySelectionSet.KeyframeReference("obj", "chan", 5);

        // Select keyframe (selects handles 0,1,2)
        assertTrue(set.selectKeyframe(ref));
        // Selecting again should return false (no change)
        assertFalse(set.selectKeyframe(ref));
        // Keyframe should be selected
        assertTrue(set.isKeyframeSelected(ref));
        // Deselect keyframe via wrapper
        assertTrue(set.deselectKeyframe(ref));
        // Deselecting again should return false
        assertFalse(set.deselectKeyframe(ref));
        // Should not be selected anymore
        assertFalse(set.isKeyframeSelected(ref));
    }

    @Test
    void testSelectAndDeselectChannel() {
        KeySelectionSet.ChannelReference cref = new KeySelectionSet.ChannelReference("obj", "chan");
        int numKeyframes = 3;

        // Select channel (selects all handles for all keyframes)
        assertTrue(set.selectChannel(cref, numKeyframes));
        // Selecting again should return false (no change)
        assertFalse(set.selectChannel(cref, numKeyframes));
        // Channel should be selected
        assertTrue(set.isChannelSelected(cref));
        // Deselect channel via wrapper
        assertTrue(set.deselectChannel(cref));
        // Deselecting again should return false
        assertFalse(set.deselectChannel(cref));
        // Should not be selected anymore
        assertFalse(set.isChannelSelected(cref));
    }

    @Test
    void testDeselectObjectWrapper() {
        KeySelectionSet.KeyHandleReference ref = new KeySelectionSet.KeyHandleReference("obj", "chan", 2, 1);

        set.selectHandle(ref);
        // Deselect object via wrapper
        assertTrue(set.deselectObject("obj"));
        // Deselecting again should return false
        assertFalse(set.deselectObject("obj"));
        // All keys/channels/handles for this object should be gone
        assertFalse(set.isObjectSelected("obj"));
    }

    @Test
    void testGetSelectedObjectsAndChannels() {
        set.selectHandle("a", "c1", 1, 2);
        set.selectHandle("a", "c2", 2, 3);
        set.selectHandle("b", "c1", 1, 2);

        Set<String> objects = set.getSelectedObjects();
        assertEquals(Set.of("a", "b"), objects);

        Set<String> channelsA = set.getSelectedChannels("a");
        assertEquals(Set.of("c1", "c2"), channelsA);

        Set<String> channelsB = set.getSelectedChannels("b");
        assertEquals(Set.of("c1"), channelsB);

        Map<String, Set<String>> allChannels = set.getSelectedChannels();
        assertEquals(Set.of("c1", "c2"), allChannels.get("a"));
        assertEquals(Set.of("c1"), allChannels.get("b"));
    }

    @Test
    void testGetSelectedKeyframesAndHandles() {
        set.selectKeyframe("obj", "chan", 0);
        set.selectKeyframe("obj", "chan", 1);
        set.selectHandle("obj", "chan", 2, 2);

        Set<KeySelectionSet.KeyframeReference> keyframes = set.getSelectedKeyframes();
        assertTrue(keyframes.contains(new KeySelectionSet.KeyframeReference("obj", "chan", 0)));
        assertTrue(keyframes.contains(new KeySelectionSet.KeyframeReference("obj", "chan", 1)));
        assertTrue(keyframes.contains(new KeySelectionSet.KeyframeReference("obj", "chan", 2)));

        Set<KeySelectionSet.KeyHandleReference> handles = set.getSelectedHandles();
        assertTrue(handles.contains(new KeySelectionSet.KeyHandleReference("obj", "chan", 0, 0)));
        assertTrue(handles.contains(new KeySelectionSet.KeyHandleReference("obj", "chan", 0, 1)));
        assertTrue(handles.contains(new KeySelectionSet.KeyHandleReference("obj", "chan", 0, 2)));
        assertTrue(handles.contains(new KeySelectionSet.KeyHandleReference("obj", "chan", 2, 2)));
    }

    @Test
    void testForSelectedObjectsAndChannels() {
        set.selectHandle("obj1", "chanA", 1, 2);
        set.selectHandle("obj2", "chanB", 2, 3);

        // Objects
        Set<String> seenObjects = new java.util.HashSet<>();
        set.forSelectedObjects(seenObjects::add);
        assertEquals(Set.of("obj1", "obj2"), seenObjects);

        // Channels in obj1
        Set<String> seenChannels = new java.util.HashSet<>();
        set.forSelectedChannels("obj1", seenChannels::add);
        assertEquals(Set.of("chanA"), seenChannels);

        // Channels in obj2
        seenChannels.clear();
        set.forSelectedChannels("obj2", seenChannels::add);
        assertEquals(Set.of("chanB"), seenChannels);

        // All channels
        java.util.Map<String, Set<String>> channelMap = new java.util.HashMap<>();
        set.forSelectedChannels(channelMap::put);
        assertEquals(Set.of("chanA"), channelMap.get("obj1"));
        assertEquals(Set.of("chanB"), channelMap.get("obj2"));
    }

    @Test
    void testForSelectedKeyframesAndHandles() {
        set.selectKeyframe("obj", "chan", 4);
        set.selectHandle("obj", "chan", 5, 2);

        Set<KeySelectionSet.KeyframeReference> seenKeyframes = new java.util.HashSet<>();
        set.forSelectedKeyframes(seenKeyframes::add);
        assertTrue(seenKeyframes.contains(new KeySelectionSet.KeyframeReference("obj", "chan", 4)));
        assertTrue(seenKeyframes.contains(new KeySelectionSet.KeyframeReference("obj", "chan", 5)));

        Set<KeySelectionSet.KeyHandleReference> seenHandles = new java.util.HashSet<>();
        set.forSelectedHandles(seenHandles::add);
        assertTrue(seenHandles.contains(new KeySelectionSet.KeyHandleReference("obj", "chan", 4, 0)));
        assertTrue(seenHandles.contains(new KeySelectionSet.KeyHandleReference("obj", "chan", 4, 1)));
        assertTrue(seenHandles.contains(new KeySelectionSet.KeyHandleReference("obj", "chan", 4, 2)));
        assertTrue(seenHandles.contains(new KeySelectionSet.KeyHandleReference("obj", "chan", 5, 2)));
    }
}