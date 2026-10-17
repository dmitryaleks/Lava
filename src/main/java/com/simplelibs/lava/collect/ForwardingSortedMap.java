/*
 * Copyright (C) 2007 The Guava Authors
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

import java.util.Comparator;
import java.util.SortedMap;

/**
 * A sorted map which forwards all its method calls to another sorted map, as {@code delegate()}
 * returns. Package-private subset of Guava's {@code ForwardingSortedMap} used by Lava's views: it
 * overrides the interface methods only, and Guava's {@code standard*} helper methods are not
 * ported.
 */
abstract class ForwardingSortedMap<K, V> extends ForwardingMap<K, V>
    implements SortedMap<K, V> {

  /** Constructor for use by subclasses. */
  protected ForwardingSortedMap() {}

  @Override
  protected abstract SortedMap<K, V> delegate();

  @Override
  public Comparator<? super K> comparator() {
    return delegate().comparator();
  }

  @Override
  public K firstKey() {
    return delegate().firstKey();
  }

  @Override
  public SortedMap<K, V> headMap(K toKey) {
    return delegate().headMap(toKey);
  }

  @Override
  public K lastKey() {
    return delegate().lastKey();
  }

  @Override
  public SortedMap<K, V> subMap(K fromKey, K toKey) {
    return delegate().subMap(fromKey, toKey);
  }

  @Override
  public SortedMap<K, V> tailMap(K fromKey) {
    return delegate().tailMap(fromKey);
  }

  /**
   * A sensible implementation of {@link SortedMap#keySet} in terms of the methods of {@code
   * ForwardingSortedMap}. In many cases, you may wish to override {@link
   * ForwardingSortedMap#keySet} to forward to this implementation or a subclass thereof.
   *
   * @since 15.0
   */
  protected class StandardKeySet extends Maps.SortedKeySet<K, V> {
    /** Constructor for use by subclasses. */
    public StandardKeySet() {
      super(ForwardingSortedMap.this);
    }
  }
}
