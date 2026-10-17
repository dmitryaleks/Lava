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

import com.simplelibs.lava.checker.nullness.qual.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A map which forwards all its method calls to another map, as {@code delegate()} returns.
 * Package-private subset of Guava's {@code ForwardingMap} used by Lava's views: it overrides the
 * interface methods only, and Guava's {@code standard*} helper methods are not ported.
 */
abstract class ForwardingMap<K, V> extends ForwardingObject implements Map<K, V> {

  /** Constructor for use by subclasses. */
  protected ForwardingMap() {}

  @Override
  protected abstract Map<K, V> delegate();

  @Override
  public int size() {
    return delegate().size();
  }

  @Override
  public boolean isEmpty() {
    return delegate().isEmpty();
  }

  @Override
  public V remove(Object object) {
    return delegate().remove(object);
  }

  @Override
  public void clear() {
    delegate().clear();
  }

  @Override
  public boolean containsKey(@Nullable Object key) {
    return delegate().containsKey(key);
  }

  @Override
  public boolean containsValue(@Nullable Object value) {
    return delegate().containsValue(value);
  }

  @Override
  public V get(@Nullable Object key) {
    return delegate().get(key);
  }

  @Override
  public V put(K key, V value) {
    return delegate().put(key, value);
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> map) {
    delegate().putAll(map);
  }

  @Override
  public Set<K> keySet() {
    return delegate().keySet();
  }

  @Override
  public Collection<V> values() {
    return delegate().values();
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    return delegate().entrySet();
  }

  @Override
  public boolean equals(@Nullable Object object) {
    return object == this || delegate().equals(object);
  }

  @Override
  public int hashCode() {
    return delegate().hashCode();
  }

  /**
   * A sensible implementation of {@link Map#keySet} in terms of the following methods: {@link
   * ForwardingMap#clear}, {@link ForwardingMap#containsKey}, {@link ForwardingMap#isEmpty}, {@link
   * ForwardingMap#remove}, {@link ForwardingMap#size}, and the {@link Set#iterator} method of
   * {@link ForwardingMap#entrySet}. In many cases, you may wish to override {@link
   * ForwardingMap#keySet} to forward to this implementation or a subclass thereof.
   *
   * @since 10.0
   */
  protected class StandardKeySet extends Maps.KeySet<K, V> {
    /** Constructor for use by subclasses. */
    public StandardKeySet() {
      super(ForwardingMap.this);
    }
  }
}
