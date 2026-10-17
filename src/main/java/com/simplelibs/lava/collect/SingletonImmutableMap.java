/*
 * Copyright (C) 2008 The Guava Authors
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
import static com.simplelibs.lava.collect.CollectPreconditions.checkEntryNotNull;

import com.simplelibs.lava.checker.nullness.qual.Nullable;
import java.util.function.BiConsumer;

/**
 * Implementation of {@link ImmutableMap} with exactly one entry.
 *
 * <p>Lava's counterpart of Guava's {@code SingletonImmutableBiMap}, which is what Guava returns
 * for every one-entry {@code ImmutableMap}. Lava does not port {@code ImmutableBiMap}, but keeps
 * its observable behavior: as for any {@code BiMap}, {@link #values()} is an {@link ImmutableSet}.
 *
 * @author Jesse Wilson
 * @author Kevin Bourrillion
 */
@SuppressWarnings("serial") // uses writeReplace(), not default serialization
final class SingletonImmutableMap<K, V> extends ImmutableMap<K, V> {
  final transient K singleKey;
  final transient V singleValue;

  SingletonImmutableMap(K singleKey, V singleValue) {
    checkEntryNotNull(singleKey, singleValue);
    this.singleKey = singleKey;
    this.singleValue = singleValue;
  }

  @Override
  public V get(@Nullable Object key) {
    return singleKey.equals(key) ? singleValue : null;
  }

  @Override
  public int size() {
    return 1;
  }

  @Override
  public void forEach(BiConsumer<? super K, ? super V> action) {
    checkNotNull(action).accept(singleKey, singleValue);
  }

  @Override
  public boolean containsKey(@Nullable Object key) {
    return singleKey.equals(key);
  }

  @Override
  public boolean containsValue(@Nullable Object value) {
    return singleValue.equals(value);
  }

  @Override
  boolean isPartialView() {
    return false;
  }

  @Override
  ImmutableSet<Entry<K, V>> createEntrySet() {
    return ImmutableSet.of(Maps.immutableEntry(singleKey, singleValue));
  }

  @Override
  ImmutableSet<K> createKeySet() {
    return ImmutableSet.of(singleKey);
  }

  @Override
  ImmutableCollection<V> createValues() {
    // Like Guava's ImmutableBiMap.values(): the inverse map's key set.
    return ImmutableSet.of(singleValue);
  }
}
