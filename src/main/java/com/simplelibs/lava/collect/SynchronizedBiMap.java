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

import com.simplelibs.lava.checker.nullness.qual.Nullable;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The bimap returned by {@link Maps#synchronizedBiMap}: a delegating {@link BiMap} that forwards
 * every method, including the {@code Map} default methods, to its delegate while holding a shared
 * mutex, as Guava's {@code Synchronized.SynchronizedBiMap} does. The inverse and the collection
 * views are created once and cached.
 *
 * <p>Lava is single-threaded, so this class exists for API compatibility. Its collection views
 * ({@link #keySet}, {@link #values}, {@link #entrySet}) are the delegate's views and are not
 * themselves synchronized.
 */
final class SynchronizedBiMap<K, V> implements BiMap<K, V>, Serializable {
  private final BiMap<K, V> delegate;
  private final Object mutex;
  private transient @Nullable BiMap<V, K> inverse;
  private transient @Nullable Set<K> keySet;
  private transient @Nullable Set<V> valueSet;
  private transient @Nullable Set<Entry<K, V>> entrySet;

  static <K, V> BiMap<K, V> of(BiMap<K, V> bimap) {
    if (bimap instanceof SynchronizedBiMap) {
      return bimap;
    }
    return new SynchronizedBiMap<>(bimap, null, null);
  }

  private SynchronizedBiMap(
      BiMap<K, V> delegate, @Nullable Object mutex, @Nullable BiMap<V, K> inverse) {
    this.delegate = checkNotNull(delegate);
    this.mutex = (mutex == null) ? this : mutex;
    this.inverse = inverse;
  }

  @Override
  public int size() {
    synchronized (mutex) {
      return delegate.size();
    }
  }

  @Override
  public boolean isEmpty() {
    synchronized (mutex) {
      return delegate.isEmpty();
    }
  }

  @Override
  public boolean containsKey(@Nullable Object key) {
    synchronized (mutex) {
      return delegate.containsKey(key);
    }
  }

  @Override
  public boolean containsValue(@Nullable Object value) {
    synchronized (mutex) {
      return delegate.containsValue(value);
    }
  }

  @Override
  public V get(@Nullable Object key) {
    synchronized (mutex) {
      return delegate.get(key);
    }
  }

  @Override
  public V getOrDefault(@Nullable Object key, @Nullable V defaultValue) {
    synchronized (mutex) {
      return delegate.getOrDefault(key, defaultValue);
    }
  }

  @Override
  public void forEach(BiConsumer<? super K, ? super V> action) {
    synchronized (mutex) {
      delegate.forEach(action);
    }
  }

  @Override
  public V put(K key, V value) {
    synchronized (mutex) {
      return delegate.put(key, value);
    }
  }

  @Override
  public V putIfAbsent(K key, V value) {
    synchronized (mutex) {
      return delegate.putIfAbsent(key, value);
    }
  }

  @Override
  public boolean replace(K key, V oldValue, V newValue) {
    synchronized (mutex) {
      return delegate.replace(key, oldValue, newValue);
    }
  }

  @Override
  public V replace(K key, V value) {
    synchronized (mutex) {
      return delegate.replace(key, value);
    }
  }

  @Override
  public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
    synchronized (mutex) {
      return delegate.computeIfAbsent(key, mappingFunction);
    }
  }

  @Override
  public V computeIfPresent(
      K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    synchronized (mutex) {
      return delegate.computeIfPresent(key, remappingFunction);
    }
  }

  @Override
  public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    synchronized (mutex) {
      return delegate.compute(key, remappingFunction);
    }
  }

  @Override
  public V merge(
      K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
    synchronized (mutex) {
      return delegate.merge(key, value, remappingFunction);
    }
  }

  @Override
  public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
    synchronized (mutex) {
      delegate.replaceAll(function);
    }
  }

  @Override
  public V forcePut(K key, V value) {
    synchronized (mutex) {
      return delegate.forcePut(key, value);
    }
  }

  @Override
  public V remove(@Nullable Object key) {
    synchronized (mutex) {
      return delegate.remove(key);
    }
  }

  @Override
  public boolean remove(@Nullable Object key, @Nullable Object value) {
    synchronized (mutex) {
      return delegate.remove(key, value);
    }
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> map) {
    synchronized (mutex) {
      delegate.putAll(map);
    }
  }

  @Override
  public void clear() {
    synchronized (mutex) {
      delegate.clear();
    }
  }

  @Override
  public Set<K> keySet() {
    synchronized (mutex) {
      if (keySet == null) {
        keySet = delegate.keySet();
      }
      return keySet;
    }
  }

  @Override
  public Set<V> values() {
    synchronized (mutex) {
      if (valueSet == null) {
        valueSet = delegate.values();
      }
      return valueSet;
    }
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    synchronized (mutex) {
      if (entrySet == null) {
        entrySet = delegate.entrySet();
      }
      return entrySet;
    }
  }

  @Override
  public BiMap<V, K> inverse() {
    synchronized (mutex) {
      if (inverse == null) {
        inverse = new SynchronizedBiMap<>(delegate.inverse(), mutex, this);
      }
      return inverse;
    }
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == this) {
      return true;
    }
    synchronized (mutex) {
      return delegate.equals(o);
    }
  }

  @Override
  public int hashCode() {
    synchronized (mutex) {
      return delegate.hashCode();
    }
  }

  @Override
  public String toString() {
    synchronized (mutex) {
      return delegate.toString();
    }
  }

  private static final long serialVersionUID = 0;
}
