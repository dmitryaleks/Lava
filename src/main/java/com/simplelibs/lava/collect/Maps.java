/*
 * Copyright (C) 2007 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * Modified for Lava: single-threaded, Guava-independent port of Guava 27.0.1.
 */

package com.simplelibs.lava.collect;

import static com.simplelibs.lava.base.Preconditions.checkNotNull;
import static com.simplelibs.lava.collect.CollectPreconditions.checkNonnegative;

import com.simplelibs.lava.checker.nullness.qual.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

/** Maps (public API completed in a later milestone). */
final class Maps {
  private Maps() {}

  private enum EntryFunction implements Function<Entry<?, ?>, Object> {
    KEY {
      @Override
      public @Nullable Object apply(Entry<?, ?> entry) {
        return entry.getKey();
      }
    },
    VALUE {
      @Override
      public @Nullable Object apply(Entry<?, ?> entry) {
        return entry.getValue();
      }
    };
  }

  @SuppressWarnings("unchecked")
  static <K> Function<Entry<K, ?>, K> keyFunction() {
    return (Function) EntryFunction.KEY;
  }

  @SuppressWarnings("unchecked")
  static <V> Function<Entry<?, V>, V> valueFunction() {
    return (Function) EntryFunction.VALUE;
  }

  static <K, V> Iterator<K> keyIterator(Iterator<Entry<K, V>> entryIterator) {
    return new TransformedIterator<Entry<K, V>, K>(entryIterator) {
      @Override
      K transform(Entry<K, V> entry) {
        return entry.getKey();
      }
    };
  }

  static <K, V> Iterator<V> valueIterator(Iterator<Entry<K, V>> entryIterator) {
    return new TransformedIterator<Entry<K, V>, V>(entryIterator) {
      @Override
      V transform(Entry<K, V> entry) {
        return entry.getValue();
      }
    };
  }

  /**
   * Creates a {@code HashMap} instance, with a high enough "initial capacity" that it <i>should</i>
   * hold {@code expectedSize} elements without growth. This behavior cannot be broadly guaranteed,
   * but it is observed to be true for OpenJDK 1.7. It also can't be guaranteed that the method
   * isn't inadvertently <i>oversizing</i> the returned map.
   *
   * @param expectedSize the number of entries you expect to add to the returned map
   * @return a new, empty {@code HashMap} with enough capacity to hold {@code expectedSize} entries
   *     without resizing
   * @throws IllegalArgumentException if {@code expectedSize} is negative
   */
  public static <K, V> HashMap<K, V> newHashMapWithExpectedSize(int expectedSize) {
    return new HashMap<>(capacity(expectedSize));
  }

  /**
   * Returns a capacity that is sufficient to keep the map from being resized as long as it grows no
   * larger than expectedSize and the load factor is ≥ its default (0.75).
   */
  static int capacity(int expectedSize) {
    if (expectedSize < 3) {
      checkNonnegative(expectedSize, "expectedSize");
      return expectedSize + 1;
    }
    if (expectedSize < CollectMath.MAX_POWER_OF_TWO) {
      // This is the calculation used in JDK8 to resize when a putAll
      // happens; it seems to be the most conservative calculation we
      // can make.  0.75 is the default load factor.
      return (int) ((float) expectedSize / 0.75F + 1.0F);
    }
    return Integer.MAX_VALUE; // any large value
  }

  /**
   * Creates a {@code LinkedHashMap} instance, with a high enough "initial capacity" that it
   * <i>should</i> hold {@code expectedSize} elements without growth. This behavior cannot be
   * broadly guaranteed, but it is observed to be true for OpenJDK 1.7. It also can't be guaranteed
   * that the method isn't inadvertently <i>oversizing</i> the returned map.
   *
   * @param expectedSize the number of entries you expect to add to the returned map
   * @return a new, empty {@code LinkedHashMap} with enough capacity to hold {@code expectedSize}
   *     entries without resizing
   * @throws IllegalArgumentException if {@code expectedSize} is negative
   * @since 19.0
   */
  public static <K, V> LinkedHashMap<K, V> newLinkedHashMapWithExpectedSize(int expectedSize) {
    return new LinkedHashMap<>(capacity(expectedSize));
  }

  /**
   * Returns an immutable map entry with the specified key and value. The {@link Entry#setValue}
   * operation throws an {@link UnsupportedOperationException}.
   *
   * <p>The returned entry is serializable.
   *
   * @param key the key to be associated with the returned entry
   * @param value the value to be associated with the returned entry
   */
  public static <K, V> Entry<K, V> immutableEntry(@Nullable K key, @Nullable V value) {
    return new ImmutableEntry<>(key, value);
  }

  /**
   * Returns an unmodifiable view of the specified map entry. The {@link Entry#setValue} operation
   * throws an {@link UnsupportedOperationException}. This also has the side-effect of redefining
   * {@code equals} to comply with the Entry contract, to avoid a possible nefarious implementation
   * of equals.
   *
   * @param entry the entry for which to return an unmodifiable view
   * @return an unmodifiable view of the entry
   */
  static <K, V> Entry<K, V> unmodifiableEntry(final Entry<? extends K, ? extends V> entry) {
    checkNotNull(entry);
    return new AbstractMapEntry<K, V>() {
      @Override
      public K getKey() {
        return entry.getKey();
      }

      @Override
      public V getValue() {
        return entry.getValue();
      }
    };
  }

  static <K, V> UnmodifiableIterator<Entry<K, V>> unmodifiableEntryIterator(
      final Iterator<Entry<K, V>> entryIterator) {
    return new UnmodifiableIterator<Entry<K, V>>() {
      @Override
      public boolean hasNext() {
        return entryIterator.hasNext();
      }

      @Override
      public Entry<K, V> next() {
        return unmodifiableEntry(entryIterator.next());
      }
    };
  }

  /** An implementation of {@link Map#equals}. */
  static boolean equalsImpl(Map<?, ?> map, Object object) {
    if (map == object) {
      return true;
    } else if (object instanceof Map) {
      Map<?, ?> o = (Map<?, ?>) object;
      return map.entrySet().equals(o.entrySet());
    }
    return false;
  }

  /** An implementation of {@link Map#toString}. */
  static String toStringImpl(Map<?, ?> map) {
    StringBuilder sb = Collections2.newStringBuilderForCollection(map.size()).append('{');
    boolean first = true;
    for (Entry<?, ?> entry : map.entrySet()) {
      if (!first) {
        sb.append(", ");
      }
      first = false;
      sb.append(entry.getKey()).append('=').append(entry.getValue());
    }
    return sb.append('}').toString();
  }
}

