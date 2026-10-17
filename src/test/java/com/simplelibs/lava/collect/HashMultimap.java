package com.simplelibs.lava.collect;

import com.simplelibs.lava.checker.nullness.qual.Nullable;
import java.util.Map.Entry;
import java.util.Set;

/** Test stand-in for Guava's {@code HashMultimap} (set values, insertion ordered). */
final class HashMultimap<K, V> extends TestMultimap<K, V> implements SetMultimap<K, V> {
  private HashMultimap() {
    super(true);
  }

  static <K, V> HashMultimap<K, V> create() {
    return new HashMultimap<>();
  }

  @Override
  public Set<V> get(@Nullable K key) {
    return (Set<V>) super.get(key);
  }

  @Override
  public Set<V> removeAll(@Nullable Object key) {
    return (Set<V>) super.removeAll(key);
  }

  @Override
  public Set<V> replaceValues(K key, Iterable<? extends V> values) {
    return (Set<V>) super.replaceValues(key, values);
  }

  @Override
  public Set<Entry<K, V>> entries() {
    return (Set<Entry<K, V>>) super.entries();
  }
}
