package com.igrium.replaylab.test;

import com.igrium.replaylab.util.BiListMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class BiListMapTest {

    /** Collects the keys of the map's entryList, in order. */
    private static <K, V> List<K> keyOrder(BiListMap<K, V> map) {
        List<K> keys = new ArrayList<>();
        for (Map.Entry<K, V> e : map.entryList()) {
            keys.add(e.getKey());
        }
        return keys;
    }

    @Test
    public void preservesInsertionOrder() {
        BiListMap<String, Integer> map = BiListMap.create();
        map.put("c", 3);
        map.put("a", 1);
        map.put("b", 2);

        assertEquals(Arrays.asList("c", "a", "b"), keyOrder(map));
        assertEquals(Arrays.asList("c", "a", "b"), new ArrayList<>(map.keySet()));
        assertEquals(Arrays.asList(3, 1, 2), new ArrayList<>(map.values()));
    }

    @Test
    public void updatingValueKeepsPosition() {
        BiListMap<String, Integer> map = BiListMap.create();
        map.put("a", 1);
        map.put("b", 2);
        map.put("c", 3);

        // Re-putting an existing key updates the value but must not move it.
        assertEquals(2, map.put("b", 20));
        assertEquals(20, map.get("b"));
        assertEquals(Arrays.asList("a", "b", "c"), keyOrder(map));
    }

    @Test
    public void entryListIndexedAccess() {
        BiListMap<String, Integer> map = BiListMap.create();
        map.put("x", 10);
        map.put("y", 20);

        List<Map.Entry<String, Integer>> list = map.entryList();
        assertEquals(2, list.size());
        assertEquals("x", list.get(0).getKey());
        assertEquals(10, list.get(0).getValue());
        assertEquals("y", list.get(1).getKey());
        assertEquals(20, list.get(1).getValue());
    }

    @Test
    public void entryListSetValueWritesThrough() {
        BiListMap<String, Integer> map = BiListMap.create();
        map.put("a", 1);

        Map.Entry<String, Integer> entry = map.entryList().get(0);
        assertEquals(1, entry.setValue(99));
        assertEquals(99, map.get("a"));
        assertEquals(99, entry.getValue());
    }

    @Test
    public void duplicateValueThrows() {
        BiListMap<String, Integer> map = BiListMap.create();
        map.put("a", 1);
        // 1 already belongs to "a"; assigning it to "b" would break the bijection.
        assertThrows(IllegalArgumentException.class, () -> map.put("b", 1));
        // Failed put must not have mutated anything.
        assertFalse(map.containsKey("b"));
        assertEquals(1, map.size());
    }

    @Test
    public void forcePutEvictsDisplacedKey() {
        BiListMap<String, Integer> map = BiListMap.create();
        map.put("a", 1);
        map.put("b", 2);
        map.put("c", 3);

        // Force value 2 onto "a"; "b" (which owned 2) must be evicted entirely.
        map.forcePut("a", 2);
        assertEquals(2, map.get("a"));
        assertFalse(map.containsKey("b"));
        assertEquals(Arrays.asList("a", "c"), keyOrder(map));
        assertEquals("a", map.inverse().get(2));
    }

    @Test
    public void removeUpdatesOrderAndInverse() {
        BiListMap<String, Integer> map = BiListMap.create();
        map.put("a", 1);
        map.put("b", 2);
        map.put("c", 3);

        assertEquals(2, map.remove("b"));
        assertNull(map.remove("nope"));
        assertEquals(Arrays.asList("a", "c"), keyOrder(map));
        assertFalse(map.inverse().containsKey(2));
        assertEquals(2, map.size());
    }

    @Test
    public void inverseLookupAndPut() {
        BiListMap<String, Integer> map = BiListMap.create();
        map.put("a", 1);
        map.put("b", 2);

        assertEquals("a", map.inverse().get(1));
        assertEquals("b", map.inverse().get(2));

        // Insert through the inverse: value -> new key.
        map.inverse().put(3, "c");
        assertEquals(3, map.get("c"));
        assertEquals(Arrays.asList("a", "b", "c"), keyOrder(map));

        // inverse().inverse() is the original map.
        assertSame(map, map.inverse().inverse());
    }

    @Test
    public void inverseRekeyKeepsPosition() {
        BiListMap<String, Integer> map = BiListMap.create();
        map.put("a", 1);
        map.put("b", 2);
        map.put("c", 3);

        // Re-key value 2 from "b" to "z"; it must stay in the middle slot.
        map.inverse().put(2, "z");
        assertFalse(map.containsKey("b"));
        assertEquals(2, map.get("z"));
        assertEquals(Arrays.asList("a", "z", "c"), keyOrder(map));
    }

    @Test
    public void iteratorRemoveSyncsMap() {
        BiListMap<String, Integer> map = BiListMap.create();
        map.put("a", 1);
        map.put("b", 2);
        map.put("c", 3);

        Iterator<String> it = map.keySet().iterator();
        it.next(); // a
        it.next(); // b
        it.remove();

        assertFalse(map.containsKey("b"));
        assertFalse(map.containsValue(2));
        assertEquals(Arrays.asList("a", "c"), keyOrder(map));
        assertEquals(2, map.size());
    }

    @Test
    public void entryListStructuralMutations() {
        BiListMap<String, Integer> map = BiListMap.create();
        map.put("a", 1);
        map.put("b", 2);
        List<Map.Entry<String, Integer>> list = map.entryList();

        // insert
        list.add(Map.entry("c", 3));
        assertEquals(Arrays.asList("a", "b", "c"), keyOrder(map));

        // replace at index
        Map.Entry<String, Integer> old = list.set(1, Map.entry("B", 20));
        assertEquals("b", old.getKey());
        assertEquals(2, old.getValue());
        assertEquals(Arrays.asList("a", "B", "c"), keyOrder(map));
        assertEquals(20, map.get("B"));
        assertFalse(map.containsKey("b"));

        // remove by index
        Map.Entry<String, Integer> removed = list.remove(0);
        assertEquals("a", removed.getKey());
        assertEquals(Arrays.asList("B", "c"), keyOrder(map));
    }

    @Test
    public void clearEmptiesEverything() {
        BiListMap<String, Integer> map = BiListMap.create();
        map.put("a", 1);
        map.put("b", 2);

        map.clear();
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
        assertTrue(map.entryList().isEmpty());
        assertTrue(map.inverse().isEmpty());
    }

    @Test
    public void createFromSourcePreservesSourceOrder() {
        Map<String, Integer> source = new LinkedHashMap<>();
        source.put("first", 1);
        source.put("second", 2);
        source.put("third", 3);

        BiListMap<String, Integer> map = BiListMap.create(source);
        assertEquals(Arrays.asList("first", "second", "third"), keyOrder(map));
        assertEquals("second", map.inverse().get(2));
    }

    @Test
    public void createFromSourceWithDuplicateValuesThrows() {
        Map<String, Integer> source = new LinkedHashMap<>();
        source.put("a", 1);
        source.put("b", 1);
        assertThrows(IllegalArgumentException.class, () -> BiListMap.create(source));
    }

    @Test
    public void equalsMatchesPlainMap() {
        BiListMap<String, Integer> map = BiListMap.create();
        map.put("a", 1);
        map.put("b", 2);

        Map<String, Integer> expected = new HashMap<>();
        expected.put("a", 1);
        expected.put("b", 2);

        // Map equality ignores order.
        assertEquals(expected, map);
        assertEquals(map, expected);
        assertEquals(expected.hashCode(), map.hashCode());
    }
}
