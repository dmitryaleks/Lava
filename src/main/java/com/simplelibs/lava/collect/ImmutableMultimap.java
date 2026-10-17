/*
 * Copyright (C) 2008 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified for Lava: single-threaded, Guava-independent port of Guava 27.0.1.
 */

package com.simplelibs.lava.collect;

import static com.simplelibs.lava.base.Preconditions.checkNotNull;
import static com.simplelibs.lava.collect.CollectPreconditions.checkEntryNotNull;

import com.simplelibs.lava.checker.nullness.qual.Nullable;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * A {@link Multimap} whose contents will never change, with many other important properties
 * detailed at {@link ImmutableCollection}.
 *
 * <p><b>Warning:</b> avoid <i>direct</i> usage of {@link ImmutableMultimap} as a type (as with
 * {@link Multimap} itself). Prefer the subtype {@link ImmutableSetMultimap}, which has well-defined
 * {@link #equals} semantics, thus avoiding a common source of bugs and confusion.
 *
 * <p><b>Lava:</b> this is a minimal port that exists to support {@link ImmutableMap#asMultimap}
 * and {@link ImmutableSetMultimap}. Guava's static factories and public {@code Builder} on this
 * class are omitted (they create an {@code ImmutableListMultimap}, which Lava does not port), as is
 * {@code keys()}, which would require {@code Multiset}.
 *
 * <p><b>Note:</b> every {@link ImmutableMultimap} offers an {@link #inverse} view, so there is no
 * need for a distinct {@code ImmutableBiMultimap} type.
 *
 * <p><a id="iteration"></a>
 *
 * <p><b>Key-grouped iteration.</b> All view collections follow the same iteration order. In all
 * current implementations, the iteration order always keeps multiple entries with the same key
 * together. Any creation method that would customarily respect insertion order (such as {@code
 * copyOf(Multimap)}) instead preserves key-grouped order by inserting entries for an existing key
 * immediately after the last entry having that key.
 *
 * <p>See the Guava User Guide article on <a href=
 * "https://github.com/google/guava/wiki/ImmutableCollectionsExplained"> immutable collections</a>.
 *
 * @author Jared Levy
 * @since 2.0
 */
public abstract class ImmutableMultimap<K, V> extends BaseImmutableMultimap<K, V>
    implements Serializable {

  /**
   * Shared implementation of the immutable multimap builders. Package-private in Lava: Guava's
   * public {@code ImmutableMultimap.Builder} builds an {@code ImmutableListMultimap}, which Lava
   * does not port; use {@link ImmutableSetMultimap.Builder}.
   */
  abstract static class Builder<K, V> {
    Map<K, Collection<V>> builderMap;
    Comparator<? super K> keyComparator;
    Comparator<? super V> valueComparator;

    Builder() {
      this.builderMap = Platform.preservesInsertionOrderOnPutsMap();
    }

    Collection<V> newMutableValueCollection() {
      return new ArrayList<>();
    }

    /** Adds a key-value mapping to the built multimap. */
    public Builder<K, V> put(K key, V value) {
      checkEntryNotNull(key, value);
      Collection<V> valueCollection = builderMap.get(key);
      if (valueCollection == null) {
        builderMap.put(key, valueCollection = newMutableValueCollection());
      }
      valueCollection.add(value);
      return this;
    }

    /**
     * Adds an entry to the built multimap.
     *
     * @since 11.0
     */
    public Builder<K, V> put(Entry<? extends K, ? extends V> entry) {
      return put(entry.getKey(), entry.getValue());
    }

    /**
     * Adds entries to the built multimap.
     *
     * @since 19.0
     */
    public Builder<K, V> putAll(Iterable<? extends Entry<? extends K, ? extends V>> entries) {
      for (Entry<? extends K, ? extends V> entry : entries) {
        put(entry);
      }
      return this;
    }

    /**
     * Stores a collection of values with the same key in the built multimap.
     *
     * @throws NullPointerException if {@code key}, {@code values}, or any element in {@code values}
     *     is null. The builder is left in an invalid state.
     */
    public Builder<K, V> putAll(K key, Iterable<? extends V> values) {
      if (key == null) {
        throw new NullPointerException("null key in entry: null=" + Iterables.toString(values));
      }
      Collection<V> valueCollection = builderMap.get(key);
      if (valueCollection != null) {
        for (V value : values) {
          checkEntryNotNull(key, value);
          valueCollection.add(value);
        }
        return this;
      }
      Iterator<? extends V> valuesItr = values.iterator();
      if (!valuesItr.hasNext()) {
        return this;
      }
      valueCollection = newMutableValueCollection();
      while (valuesItr.hasNext()) {
        V value = valuesItr.next();
        checkEntryNotNull(key, value);
        valueCollection.add(value);
      }
      builderMap.put(key, valueCollection);
      return this;
    }

    /**
     * Stores an array of values with the same key in the built multimap.
     *
     * @throws NullPointerException if the key or any value is null. The builder is left in an
     *     invalid state.
     */
    public Builder<K, V> putAll(K key, V... values) {
      return putAll(key, Arrays.asList(values));
    }

    /**
     * Stores another multimap's entries in the built multimap. The generated multimap's key and
     * value orderings correspond to the iteration ordering of the {@code multimap.asMap()} view,
     * with new keys and values following any existing keys and values.
     *
     * @throws NullPointerException if any key or value in {@code multimap} is null. The builder is
     *     left in an invalid state.
     */
    public Builder<K, V> putAll(Multimap<? extends K, ? extends V> multimap) {
      for (Entry<? extends K, ? extends Collection<? extends V>> entry :
          multimap.asMap().entrySet()) {
        putAll(entry.getKey(), entry.getValue());
      }
      return this;
    }

    /**
     * Specifies the ordering of the generated multimap's keys.
     *
     * @since 8.0
     */
    public Builder<K, V> orderKeysBy(Comparator<? super K> keyComparator) {
      this.keyComparator = checkNotNull(keyComparator);
      return this;
    }

    /**
     * Specifies the ordering of the generated multimap's values for each key.
     *
     * @since 8.0
     */
    public Builder<K, V> orderValuesBy(Comparator<? super V> valueComparator) {
      this.valueComparator = checkNotNull(valueComparator);
      return this;
    }

    Builder<K, V> combine(Builder<K, V> other) {
      for (Map.Entry<K, Collection<V>> entry : other.builderMap.entrySet()) {
        putAll(entry.getKey(), entry.getValue());
      }
      return this;
    }

    /** Returns a newly-created immutable multimap. */
    public abstract ImmutableMultimap<K, V> build();
  }

  final transient ImmutableMap<K, ? extends ImmutableCollection<V>> map;
  final transient int size;

  // These constants allow the deserialization code to set final fields. This
  // holder class makes sure they are not initialized unless an instance is
  // deserialized.
  static class FieldSettersHolder {
    static final Serialization.FieldSetter<ImmutableMultimap> MAP_FIELD_SETTER =
        Serialization.getFieldSetter(ImmutableMultimap.class, "map");
    static final Serialization.FieldSetter<ImmutableMultimap> SIZE_FIELD_SETTER =
        Serialization.getFieldSetter(ImmutableMultimap.class, "size");
  }

  ImmutableMultimap(ImmutableMap<K, ? extends ImmutableCollection<V>> map, int size) {
    this.map = map;
    this.size = size;
  }

  // mutators (not supported)

  /**
   * Guaranteed to throw an exception and leave the multimap unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  public ImmutableCollection<V> removeAll(Object key) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the multimap unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  public ImmutableCollection<V> replaceValues(K key, Iterable<? extends V> values) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the multimap unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  public void clear() {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns an immutable collection of the values for the given key. If no mappings in the multimap
   * have the provided key, an empty immutable collection is returned. The values are in the same
   * order as the parameters used to build this multimap.
   */
  @Override
  public abstract ImmutableCollection<V> get(K key);

  /**
   * Returns an immutable multimap which is the inverse of this one. For every key-value mapping in
   * the original, the result will have a mapping with key and value reversed.
   *
   * @since 11.0
   */
  public abstract ImmutableMultimap<V, K> inverse();

  /**
   * Guaranteed to throw an exception and leave the multimap unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  public boolean put(K key, V value) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the multimap unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  public boolean putAll(K key, Iterable<? extends V> values) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the multimap unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  public boolean putAll(Multimap<? extends K, ? extends V> multimap) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the multimap unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  public boolean remove(Object key, Object value) {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns {@code true} if this immutable multimap's implementation contains references to
   * user-created objects that aren't accessible via this multimap's methods. This is generally used
   * to determine whether {@code copyOf} implementations should make an explicit copy to avoid
   * memory leaks.
   */
  boolean isPartialView() {
    return map.isPartialView();
  }

  // accessors

  @Override
  public boolean containsKey(@Nullable Object key) {
    return map.containsKey(key);
  }

  @Override
  public boolean containsValue(@Nullable Object value) {
    return value != null && super.containsValue(value);
  }

  @Override
  public int size() {
    return size;
  }

  // views

  /**
   * Returns an immutable set of the distinct keys in this multimap, in the same order as they
   * appear in this multimap.
   */
  @Override
  public ImmutableSet<K> keySet() {
    return map.keySet();
  }

  @Override
  Set<K> createKeySet() {
    throw new AssertionError("unreachable");
  }

  /**
   * Returns an immutable map that associates each key with its corresponding values in the
   * multimap. Keys and values appear in the same order as in this multimap.
   */
  @Override
  @SuppressWarnings("unchecked") // a widening cast
  public ImmutableMap<K, Collection<V>> asMap() {
    return (ImmutableMap) map;
  }

  @Override
  Map<K, Collection<V>> createAsMap() {
    throw new AssertionError("should never be called");
  }

  /** Returns an immutable collection of all key-value pairs in the multimap. */
  @Override
  public ImmutableCollection<Entry<K, V>> entries() {
    return (ImmutableCollection<Entry<K, V>>) super.entries();
  }

  @Override
  ImmutableCollection<Entry<K, V>> createEntries() {
    // Lava: unreachable. ImmutableSetMultimap, the only subclass, overrides entries(); Guava's
    // EntryCollection (and its flat-mapping spliterator) served ImmutableListMultimap, which Lava
    // does not port.
    throw new AssertionError("unreachable");
  }

  @Override
  UnmodifiableIterator<Entry<K, V>> entryIterator() {
    return new UnmodifiableIterator<Entry<K, V>>() {
      final Iterator<? extends Entry<K, ? extends ImmutableCollection<V>>> asMapItr =
          map.entrySet().iterator();
      K currentKey = null;
      Iterator<V> valueItr = Iterators.emptyIterator();

      @Override
      public boolean hasNext() {
        return valueItr.hasNext() || asMapItr.hasNext();
      }

      @Override
      public Entry<K, V> next() {
        if (!valueItr.hasNext()) {
          Entry<K, ? extends ImmutableCollection<V>> entry = asMapItr.next();
          currentKey = entry.getKey();
          valueItr = entry.getValue().iterator();
        }
        return Maps.immutableEntry(currentKey, valueItr.next());
      }
    };
  }

  @Override
  public void forEach(BiConsumer<? super K, ? super V> action) {
    checkNotNull(action);
    asMap()
        .forEach(
            (key, valueCollection) -> valueCollection.forEach(value -> action.accept(key, value)));
  }

  /**
   * Returns an immutable collection of the values in this multimap. Its iterator traverses the
   * values for the first key, the values for the second key, and so on.
   */
  @Override
  public ImmutableCollection<V> values() {
    return (ImmutableCollection<V>) super.values();
  }

  @Override
  ImmutableCollection<V> createValues() {
    return new Values<>(this);
  }

  @Override
  UnmodifiableIterator<V> valueIterator() {
    return new UnmodifiableIterator<V>() {
      Iterator<? extends ImmutableCollection<V>> valueCollectionItr = map.values().iterator();
      Iterator<V> valueItr = Iterators.emptyIterator();

      @Override
      public boolean hasNext() {
        return valueItr.hasNext() || valueCollectionItr.hasNext();
      }

      @Override
      public V next() {
        if (!valueItr.hasNext()) {
          valueItr = valueCollectionItr.next().iterator();
        }
        return valueItr.next();
      }
    };
  }

  private static final class Values<K, V> extends ImmutableCollection<V> {
    private final transient ImmutableMultimap<K, V> multimap;

    Values(ImmutableMultimap<K, V> multimap) {
      this.multimap = multimap;
    }

    @Override
    public boolean contains(@Nullable Object object) {
      return multimap.containsValue(object);
    }

    @Override
    public UnmodifiableIterator<V> iterator() {
      return multimap.valueIterator();
    }

    @Override
    int copyIntoArray(Object[] dst, int offset) {
      for (ImmutableCollection<V> valueCollection : multimap.map.values()) {
        offset = valueCollection.copyIntoArray(dst, offset);
      }
      return offset;
    }

    @Override
    public int size() {
      return multimap.size();
    }

    @Override
    boolean isPartialView() {
      return true;
    }

    private static final long serialVersionUID = 0;
  }

  private static final long serialVersionUID = 0;
}
