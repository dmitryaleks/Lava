package com.simplelibs.lava.collect;

import com.simplelibs.lava.checker.nullness.qual.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * A simple mutable, insertion-ordered {@link Multimap} for tests. Lava does not port Guava's
 * mutable multimaps; the test-only {@link HashMultimap}, {@link ArrayListMultimap} and {@link
 * LinkedListMultimap} subclasses stand in for them as inputs to {@code ImmutableSetMultimap}.
 * Keys keep first-insertion order and entries are grouped by key.
 */
class TestMultimap<K, V> extends AbstractMultimap<K, V> {
  private final Map<K, Collection<V>> map = new LinkedHashMap<>();
  private final boolean setValues;
  private int size;

  TestMultimap(boolean setValues) {
    this.setValues = setValues;
  }

  private Collection<V> newCollection() {
    return setValues ? new LinkedHashSet<>() : new ArrayList<>();
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public boolean containsKey(@Nullable Object key) {
    return map.containsKey(key);
  }

  @Override
  public boolean put(@Nullable K key, @Nullable V value) {
    boolean added = map.computeIfAbsent(key, k -> newCollection()).add(value);
    if (added) {
      size++;
    }
    return added;
  }

  @Override
  public boolean putAll(@Nullable K key, Iterable<? extends V> values) {
    boolean changed = false;
    for (V value : values) {
      changed |= put(key, value);
    }
    return changed;
  }

  @Override
  public boolean remove(@Nullable Object key, @Nullable Object value) {
    Collection<V> values = map.get(key);
    if (values == null || !values.remove(value)) {
      return false;
    }
    size--;
    if (values.isEmpty()) {
      map.remove(key);
    }
    return true;
  }

  @Override
  public Collection<V> get(@Nullable K key) {
    Collection<V> values = map.get(key);
    if (values == null) {
      return setValues ? Collections.<V>emptySet() : Collections.<V>emptyList();
    }
    return setValues
        ? Collections.unmodifiableSet((Set<V>) values)
        : Collections.unmodifiableCollection(values);
  }

  @Override
  public Collection<V> removeAll(@Nullable Object key) {
    Collection<V> removed = map.remove(key);
    if (removed == null) {
      return newCollection();
    }
    size -= removed.size();
    return removed;
  }

  @Override
  public void clear() {
    map.clear();
    size = 0;
  }

  @Override
  Collection<Entry<K, V>> createEntries() {
    return setValues ? new EntrySet() : new Entries();
  }

  @Override
  Iterator<Entry<K, V>> entryIterator() {
    java.util.List<Entry<K, V>> entries = new ArrayList<>();
    for (Entry<K, Collection<V>> e : map.entrySet()) {
      for (V v : e.getValue()) {
        entries.add(Maps.immutableEntry(e.getKey(), v));
      }
    }
    return Collections.unmodifiableList(entries).iterator();
  }

  @Override
  Set<K> createKeySet() {
    return Collections.unmodifiableSet(map.keySet());
  }

  @Override
  Collection<V> createValues() {
    return new Values();
  }

  @Override
  Map<K, Collection<V>> createAsMap() {
    return Collections.unmodifiableMap(map);
  }
}
