/*
 * Copyright (C) 2016 The Guava Authors
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

import java.util.function.Function;
import java.util.stream.Collector;

/** Collectors utilities for {@code common.collect} internals. */
final class CollectCollectors {

  private static final Collector<Object, ?, ImmutableList<Object>> TO_IMMUTABLE_LIST =
      Collector.of(
          ImmutableList::<Object>builder,
          ImmutableList.Builder::add,
          ImmutableList.Builder::combine,
          ImmutableList.Builder::build);

  static <E> Collector<E, ?, ImmutableList<E>> toImmutableList() {
    return (Collector) TO_IMMUTABLE_LIST;
  }

  static <T, K, V> Collector<T, ?, ImmutableMap<K, V>> toImmutableMap(
      Function<? super T, ? extends K> keyFunction,
      Function<? super T, ? extends V> valueFunction) {
    checkNotNull(keyFunction);
    checkNotNull(valueFunction);
    return Collector.of(
        ImmutableMap.Builder<K, V>::new,
        (builder, input) -> builder.put(keyFunction.apply(input), valueFunction.apply(input)),
        ImmutableMap.Builder::combine,
        ImmutableMap.Builder::build);
  }

  private static final Collector<Object, ?, ImmutableSet<Object>> TO_IMMUTABLE_SET =
      Collector.of(
          ImmutableSet::<Object>builder,
          ImmutableSet.Builder::add,
          ImmutableSet.Builder::combine,
          ImmutableSet.Builder::build);

  static <E> Collector<E, ?, ImmutableSet<E>> toImmutableSet() {
    return (Collector) TO_IMMUTABLE_SET;
  }
}
