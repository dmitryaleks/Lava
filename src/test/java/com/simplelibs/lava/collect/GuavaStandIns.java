package com.simplelibs.lava.collect;

import static com.simplelibs.lava.base.Preconditions.checkArgument;

import com.simplelibs.lava.checker.nullness.qual.Nullable;
import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Test-only stand-ins for the Guava factories that the ported Guava tests use to build inputs and
 * expected values but that Lava does not port: {@code ImmutableSortedMap}, {@code ImmutableBiMap}
 * and {@code Functions}. Each has the semantics the tests rely on.
 */
final class GuavaStandIns {
  private GuavaStandIns() {}

  /** Stand-in for {@code ImmutableSortedMap.of(...)}: an unmodifiable naturally ordered map. */
  static <K, V> NavigableMap<K, V> sortedMapOf(Object... keysAndValues) {
    return sortedMapOrderedBy(null, keysAndValues);
  }

  /** Stand-in for {@code ImmutableSortedMap.orderedBy(comparator)...build()}. */
  @SuppressWarnings("unchecked")
  static <K, V> NavigableMap<K, V> sortedMapOrderedBy(
      @Nullable Comparator<? super K> comparator, Object... keysAndValues) {
    TreeMap<K, V> map = new TreeMap<>(comparator);
    for (int i = 0; i < keysAndValues.length; i += 2) {
      K key = (K) keysAndValues[i];
      checkArgument(!map.containsKey(key), "duplicate key: %s", key);
      map.put(key, (V) keysAndValues[i + 1]);
    }
    return Collections.unmodifiableNavigableMap(map);
  }

  /** Stand-in for {@code ImmutableBiMap.of(...)}: an unmodifiable insertion-ordered bimap. */
  @SuppressWarnings("unchecked")
  static <K, V> BiMap<K, V> biMapOf(Object... keysAndValues) {
    HashBiMap<K, V> map = HashBiMap.create();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      K key = (K) keysAndValues[i];
      checkArgument(!map.containsKey(key), "duplicate key: %s", key);
      map.put(key, (V) keysAndValues[i + 1]);
    }
    return Maps.unmodifiableBiMap(map);
  }

  /** Stand-in for {@code Functions.toStringFunction()} (serializable). */
  @SuppressWarnings("unchecked")
  static <T> Function<T, String> toStringFunction() {
    return (Function<T, String>) ToStringFunction.INSTANCE;
  }

  private enum ToStringFunction implements Function<Object, String> {
    INSTANCE;

    @Override
    public String apply(Object o) {
      return o.toString();
    }

    @Override
    public String toString() {
      return "Functions.toStringFunction()";
    }
  }

  /** Stand-in for {@code Functions.identity()} (serializable). */
  @SuppressWarnings("unchecked")
  static <T> Function<T, T> identity() {
    return (Function<T, T>) IdentityFunction.INSTANCE;
  }

  private enum IdentityFunction implements Function<Object, Object> {
    INSTANCE;

    @Override
    public Object apply(Object o) {
      return o;
    }

    @Override
    public String toString() {
      return "Functions.identity()";
    }
  }

  /** Stand-in for {@code Functions.constant(value)}. */
  static <E> Function<Object, E> constant(@Nullable E value) {
    return (Function<Object, E> & Serializable) o -> value;
  }

  /**
   * Stand-in for {@code Functions.forMap(map)}: throws {@link IllegalArgumentException} for keys
   * that are not present, like Guava's.
   */
  static <K, V> Function<K, V> forMap(Map<K, V> map) {
    return key -> {
      V result = map.get(key);
      checkArgument(result != null || map.containsKey(key), "Key '%s' not present in map", key);
      return result;
    };
  }

  /** Stand-in for {@code ImmutableSortedMap.<K, V>reverseOrder()}. */
  static <K extends Comparable<? super K>, V> SortedMapBuilder<K, V> reverseOrderSortedMap() {
    return new SortedMapBuilder<>(Comparator.<K>reverseOrder());
  }

  /** Builder half of the {@code ImmutableSortedMap} stand-in. */
  static final class SortedMapBuilder<K, V> {
    private final TreeMap<K, V> map;

    SortedMapBuilder(Comparator<? super K> comparator) {
      this.map = new TreeMap<>(comparator);
    }

    SortedMapBuilder<K, V> put(K key, V value) {
      checkArgument(!map.containsKey(key), "duplicate key: %s", key);
      map.put(key, value);
      return this;
    }

    NavigableMap<K, V> build() {
      return Collections.unmodifiableNavigableMap(new TreeMap<>(map));
    }
  }

  /** Stand-in for {@code new ImmutableBiMap.Builder<K, V>()}. */
  static final class BiMapBuilder<K, V> {
    private final HashBiMap<K, V> map = HashBiMap.create();

    BiMapBuilder<K, V> put(K key, V value) {
      checkArgument(!map.containsKey(key), "duplicate key: %s", key);
      map.put(key, value);
      return this;
    }

    BiMap<K, V> build() {
      return Maps.unmodifiableBiMap(HashBiMap.create(map));
    }
  }
}
