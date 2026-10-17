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

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The few combinators from Guava's {@code Predicates} that Lava's collection views use internally,
 * expressed over {@link java.util.function.Predicate}. Lava does not port {@code Predicates} as
 * API.
 */
final class CollectPredicates {
  private CollectPredicates() {}

  /** Returns a predicate that is true if both components are; evaluates {@code first} first. */
  static <T> Predicate<T> and(Predicate<? super T> first, Predicate<? super T> second) {
    checkNotNull(first);
    checkNotNull(second);
    return t -> first.test(t) && second.test(t);
  }

  /** Returns {@code predicate} applied to the result of {@code function}. */
  static <A, B> Predicate<A> compose(
      Predicate<B> predicate, Function<A, ? extends B> function) {
    checkNotNull(predicate);
    checkNotNull(function);
    return a -> predicate.test(function.apply(a));
  }

}
