package com.igrium.replaylab.util;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A {@link BiMap} that additionally remembers the order in which entries were inserted and exposes that ordering as a
 * {@link List} of {@link Map.Entry entries} via {@link #entryList()}.
 * <p>
 * The bijection (and both-directions lookups) is backed by a Guava {@link HashBiMap}; the insertion order is backed
 * by a
 * plain {@link ArrayList} of keys. Neither structure duplicates the other's data: values live only in the bimap, order
 * lives only in the list.
 * <p>
 * <b>Why this isn't {@code implements List<Map.Entry<K, V>>}:</b> a single class cannot implement both {@link Map} and
 * {@code List<Map.Entry<K, V>>}, because {@link Map#remove(Object)} returns {@code V} while {@link List#remove(Object)}
 * returns {@code boolean} — same erased signature, incompatible return types, which the compiler rejects. So this class
 * <i>is</i> the {@link BiMap}, and the ordered {@code List<Map.Entry<K, V>>} is a live view returned by
 * {@link #entryList()}.
 * <p>
 * The list view is a live, unindexed view (as the name suggests, it is not optimized for random access — {@code get(i)}
 * is O(1) but structural removals are O(n)). Mutations through the list, the map, or any of the collection views all
 * write through to the same shared state.
 *
 * @param <K> key type
 * @param <V> value type
 * @implNote HEEEELPPPP I'm getting addicted to vibe-coding! To be fair, this is a small, self-contained class that is
 * only a utility for "real" code, and I was lazy.
 */
@SuppressWarnings("SuspiciousMethodCalls")
public class BiListMap<K, V> extends AbstractMap<K, V> implements BiMap<K, V> {

    /**
     * The bijection and the source of truth for values.
     */
    private final BiMap<K, V> map;

    /**
     * Keys in insertion order. The i-th inserted (surviving) entry has key {@code order.get(i)}.
     */
    private final List<K> order;

    // Lazily-created live views.
    private transient Inverse inverse;
    private transient EntryList entryList;

    public BiListMap() {
        this.map = HashBiMap.create();
        this.order = new ArrayList<>();
    }

    /**
     * Creates an empty {@link BiListMap}.
     */
    public static <K, V> BiListMap<K, V> create() {
        return new BiListMap<>();
    }

    /**
     * Creates a {@link BiListMap} containing the entries of {@code source}, in the iteration order of {@code source}.
     *
     * @throws IllegalArgumentException if {@code source} contains any duplicate values.
     */
    public static <K, V> BiListMap<K, V> create(Map<? extends K, ? extends V> source) {
        BiListMap<K, V> result = new BiListMap<>();
        result.putAll(source);
        return result;
    }

    // region core mutators — every path that changes state goes through these three.

    @Override
    public V put(K key, V value) {
        boolean existed = map.containsKey(key);
        V old = map.put(key, value); // throws IllegalArgumentException if value collides with a different key
        if (!existed) {
            order.add(key);
        }
        return old;
    }

    @Override
    public V forcePut(@Nullable K key, @Nullable V value) {
        boolean keyExisted = map.containsKey(key);
        // The (possibly different) key that currently owns this value; it gets evicted by forcePut.
        K displaced = map.inverse().get(value);
        V old = map.forcePut(key, value);
        if (displaced != null && !displaced.equals(key)) {
            order.remove(displaced);
        }
        if (!keyExisted) {
            order.add(key);
        }
        return old;
    }

    @Override
    public V remove(Object key) {
        if (!map.containsKey(key)) {
            return null;
        }
        V old = map.remove(key);
        order.remove(key);
        return old;
    }

    @Override
    public void putAll(@NotNull Map<? extends K, ? extends V> m) {
        // AbstractMap.putAll iterates and calls put(), so bijection checks and ordering are handled per-entry.
        super.putAll(m);
    }

    @Override
    public void clear() {
        map.clear();
        order.clear();
    }

    // endregion

    // region read-through map methods (delegated to the bimap for O(1) behaviour)

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    @Override
    public V get(Object key) {
        return map.get(key);
    }

    // endregion

    /**
     * A live, insertion-ordered {@link List} view of this map's entries. {@code get(i)} returns a live entry whose
     * {@link Map.Entry#setValue(Object) setValue} writes through to this map. Structural changes to the list (removal,
     * insertion, replacement) write through as well, and changes to the map are reflected in the list.
     * <p>
     * This is the {@code List<Map.Entry<K, V>>} promised by the class contract. It is not optimized for random access.
     */
    public List<Map.Entry<K, V>> entryList() {
        EntryList result = entryList;
        return result != null ? result : (entryList = new EntryList());
    }

    @Override
    public @NotNull BiMap<V, K> inverse() {
        Inverse result = inverse;
        return result != null ? result : (inverse = new Inverse());
    }

    @Override
    public @NotNull Set<K> keySet() {
        return new KeySet();
    }

    @Override
    public @NotNull Set<V> values() {
        return new ValueSet();
    }

    @Override
    public @NotNull Set<Map.Entry<K, V>> entrySet() {
        return new EntrySet();
    }

    /**
     * A live {@link Map.Entry} keyed by a stable key; the value is always read from (and written back to) the map.
     */
    private final class BiEntry implements Map.Entry<K, V> {
        private final K key;

        BiEntry(K key) {
            this.key = key;
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return map.get(key);
        }

        @Override
        public V setValue(V value) {
            return put(key, value);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) {
                return false;
            }
            return Objects.equals(key, e.getKey()) && Objects.equals(getValue(), e.getValue());
        }

        @Override
        public int hashCode() {
            V value = getValue();
            return (key == null ? 0 : key.hashCode()) ^ (value == null ? 0 : value.hashCode());
        }

        @Override
        public String toString() {
            return key + "=" + getValue();
        }
    }

    /**
     * Shared iterator over {@link #order} that keeps the bimap in sync on {@code remove()}.
     */
    private abstract class OrderIterator<E> implements Iterator<E> {
        private final Iterator<K> it = order.iterator();
        private K current;

        @Override
        public boolean hasNext() {
            return it.hasNext();
        }

        protected K nextKey() {
            current = it.next();
            return current;
        }

        @Override
        public void remove() {
            it.remove();
            map.remove(current);
        }
    }

    private final class KeySet extends AbstractSet<K> {
        @Override
        public @NotNull Iterator<K> iterator() {
            return new OrderIterator<K>() {
                @Override
                public K next() {
                    return nextKey();
                }
            };
        }

        @Override
        public int size() {
            return order.size();
        }

        @Override
        public boolean contains(Object o) {
            return map.containsKey(o);
        }

        @Override
        public boolean remove(Object o) {
            if (!map.containsKey(o)) {
                return false;
            }
            BiListMap.this.remove(o);
            return true;
        }

        @Override
        public void clear() {
            BiListMap.this.clear();
        }
    }

    private final class ValueSet extends AbstractSet<V> {
        @Override
        public @NotNull Iterator<V> iterator() {
            return new OrderIterator<V>() {
                @Override
                public V next() {
                    return map.get(nextKey());
                }
            };
        }

        @Override
        public int size() {
            return order.size();
        }

        @Override
        public boolean contains(Object o) {
            return map.containsValue(o);
        }

        @Override
        public boolean remove(Object o) {
            if (!map.containsValue(o)) {
                return false;
            }
            BiListMap.this.remove(map.inverse().get(o));
            return true;
        }

        @Override
        public void clear() {
            BiListMap.this.clear();
        }
    }

    private final class EntrySet extends AbstractSet<Map.Entry<K, V>> {
        @Override
        public @NotNull Iterator<Map.Entry<K, V>> iterator() {
            return new OrderIterator<Map.Entry<K, V>>() {
                @Override
                public Map.Entry<K, V> next() {
                    return new BiEntry(nextKey());
                }
            };
        }

        @Override
        public int size() {
            return order.size();
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) {
                return false;
            }
            return map.containsKey(e.getKey()) && Objects.equals(map.get(e.getKey()), e.getValue());
        }

        @Override
        public boolean remove(Object o) {
            if (!contains(o)) {
                return false;
            }
            BiListMap.this.remove(((Map.Entry<?, ?>) o).getKey());
            return true;
        }

        @Override
        public void clear() {
            BiListMap.this.clear();
        }
    }

    /**
     * The {@link List} view returned by {@link #entryList()}.
     */
    private final class EntryList extends AbstractList<Map.Entry<K, V>> {
        @Override
        public Map.Entry<K, V> get(int index) {
            return new BiEntry(order.get(index));
        }

        @Override
        public int size() {
            return order.size();
        }

        @Override
        public Map.Entry<K, V> set(int index, Map.Entry<K, V> element) {
            K oldKey = order.get(index);
            V oldValue = map.get(oldKey);
            validateReplacement(element.getKey(), element.getValue(), oldKey, oldValue);

            map.remove(oldKey);
            order.remove(index);
            map.put(element.getKey(), element.getValue());
            order.add(index, element.getKey());
            modCount++;
            return new SimpleImmutableEntry<>(oldKey, oldValue);
        }

        @Override
        public void add(int index, Map.Entry<K, V> element) {
            validateReplacement(element.getKey(), element.getValue(), null, null);
            map.put(element.getKey(), element.getValue());
            order.add(index, element.getKey());
            modCount++;
        }

        @Override
        public Map.Entry<K, V> remove(int index) {
            K key = order.get(index);
            V value = map.get(key);
            map.remove(key);
            order.remove(index);
            modCount++;
            return new SimpleImmutableEntry<>(key, value);
        }

        @Override
        public void clear() {
            BiListMap.this.clear();
            modCount++;
        }
    }

    /**
     * Fails fast (before any mutation) if inserting {@code (newKey, newValue)} would break the bijection. The
     * {@code exemptKey}/{@code exemptValue} pair, if given, is the slot being replaced and so is not treated as a
     * collision.
     */
    private void validateReplacement(K newKey, V newValue, @Nullable K exemptKey, @Nullable V exemptValue) {
        if (map.containsKey(newKey) && !Objects.equals(newKey, exemptKey)) {
            throw new IllegalArgumentException("key already present: " + newKey);
        }
        if (map.containsValue(newValue) && !Objects.equals(newValue, exemptValue)) {
            throw new IllegalArgumentException("value already present: " + newValue);
        }
    }

    /**
     * Live inverse view. Mutations route through the underlying {@link HashBiMap#inverse()} for correct bijection
     * semantics, then patch {@link #order} so insertion order stays consistent across both directions.
     */
    private final class Inverse extends AbstractMap<V, K> implements BiMap<V, K> {
        /**
         * Live inverse of the underlying bimap: keys are our values, values are our keys.
         */
        private final BiMap<V, K> inv = map.inverse();

        @Override
        public int size() {
            return map.size();
        }

        @Override
        public boolean isEmpty() {
            return map.isEmpty();
        }

        @Override
        public boolean containsKey(Object value) {
            return inv.containsKey(value);
        }

        @Override
        public boolean containsValue(Object key) {
            return map.containsKey(key);
        }

        @Override
        public K get(Object value) {
            return inv.get(value);
        }

        @Override
        public K put(V value, K key) {
            K previousKeyForValue = inv.get(value);
            boolean valueExisted = inv.containsKey(value);
            // Throws IllegalArgumentException if key already maps to a different value in the forward map.
            K old = inv.put(value, key);
            if (!valueExisted) {
                // Brand-new value; the winning key must be new (else the put above would have thrown).
                order.add(key);
            } else if (!Objects.equals(previousKeyForValue, key)) {
                // Existing value re-keyed: replace its label in the order list, keeping its position.
                order.set(order.indexOf(previousKeyForValue), key);
            }
            return old;
        }

        @Override
        public K forcePut(@Nullable V value, @Nullable K key) {
            K previousKeyForValue = inv.get(value);
            boolean keyExisted = map.containsKey(key);
            K old = inv.forcePut(value, key);
            if (previousKeyForValue != null && !previousKeyForValue.equals(key)) {
                order.remove(previousKeyForValue);
            }
            if (!keyExisted) {
                order.add(key);
            }
            return old;
        }

        @Override
        public K remove(Object value) {
            if (!inv.containsKey(value)) {
                return null;
            }
            K key = inv.get(value);
            inv.remove(value);
            order.remove(key);
            return key;
        }

        @Override
        public void clear() {
            BiListMap.this.clear();
        }

        @Override
        public @NotNull Set<V> keySet() {
            return BiListMap.this.values();
        }

        @Override
        public @NotNull Set<K> values() {
            return BiListMap.this.keySet();
        }

        @Override
        public @NotNull BiMap<K, V> inverse() {
            return BiListMap.this;
        }

        @Override
        public @NotNull Set<Map.Entry<V, K>> entrySet() {
            return new AbstractSet<>() {
                @Override
                public @NotNull Iterator<Map.Entry<V, K>> iterator() {
                    return new OrderIterator<Map.Entry<V, K>>() {
                        @Override
                        public Map.Entry<V, K> next() {
                            K key = nextKey();
                            return new SimpleImmutableEntry<>(map.get(key), key);
                        }
                    };
                }

                @Override
                public int size() {
                    return order.size();
                }

                @Override
                public boolean contains(Object o) {
                    if (!(o instanceof Map.Entry<?, ?> e)) {
                        return false;
                    }
                    return inv.containsKey(e.getKey()) && Objects.equals(inv.get(e.getKey()), e.getValue());
                }

                @Override
                public boolean remove(Object o) {
                    if (!contains(o)) {
                        return false;
                    }
                    Inverse.this.remove(((Map.Entry<?, ?>) o).getKey());
                    return true;
                }

                @Override
                public void clear() {
                    BiListMap.this.clear();
                }
            };
        }
    }
}