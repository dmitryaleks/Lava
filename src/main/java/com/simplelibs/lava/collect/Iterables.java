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
import java.util.Collection;

/** Package-private subset of Guava's {@code Iterables} used by Lava's collections. */
final class Iterables {
  private Iterables() {}

  /**
   * Returns a string representation of {@code iterable}, with the format {@code [e1, e2, ..., en]}
   * (that is, identical to {@link java.util.Arrays Arrays}{@code
   * .toString(Iterables.toArray(iterable))}). Note that for <i>most</i> implementations of {@link
   * Collection}, {@code collection.toString()} also gives the same result, but that behavior is not
   * generally guaranteed.
   */
  public static String toString(Iterable<?> iterable) {
    return Iterators.toString(iterable.iterator());
  }

  /**
   * Returns the single element contained in {@code iterable}.
   *
   * <p><b>Java 8 users:</b> the {@code Stream} equivalent to this method is {@code
   * stream.collect(MoreCollectors.onlyElement())}.
   *
   * @throws NoSuchElementException if the iterable is empty
   * @throws IllegalArgumentException if the iterable contains multiple elements
   */
  public static <T> T getOnlyElement(Iterable<T> iterable) {
    return Iterators.getOnlyElement(iterable.iterator());
  }

  /**
   * Returns the single element contained in {@code iterable}, or {@code defaultValue} if the
   * iterable is empty.
   *
   * <p><b>Java 8 users:</b> the {@code Stream} equivalent to this method is {@code
   * stream.collect(MoreCollectors.toOptional()).orElse(defaultValue)}.
   *
   * @throws IllegalArgumentException if the iterator contains multiple elements
   */
  public static <T> @Nullable T getOnlyElement(
      Iterable<? extends T> iterable, @Nullable T defaultValue) {
    return Iterators.getOnlyElement(iterable.iterator(), defaultValue);
  }

  /**
   * Copies an iterable's elements into an array.
   *
   * @param iterable the iterable to copy
   * @param type the type of the elements
   * @return a newly-allocated array into which all the elements of the iterable have been copied
   */
  public static <T> T[] toArray(Iterable<? extends T> iterable, Class<T> type) {
    return toArray(iterable, ObjectArrays.newArray(type, 0));
  }

  static <T> T[] toArray(Iterable<? extends T> iterable, T[] array) {
    Collection<? extends T> collection = castOrCopyToCollection(iterable);
    return collection.toArray(array);
  }

  /**
   * Copies an iterable's elements into an array.
   *
   * @param iterable the iterable to copy
   * @return a newly-allocated array into which all the elements of the iterable have been copied
   */
  static Object[] toArray(Iterable<?> iterable) {
    return castOrCopyToCollection(iterable).toArray();
  }

  /**
   * Converts an iterable into a collection. If the iterable is already a collection, it is
   * returned. Otherwise, an {@link java.util.ArrayList} is created with the contents of the
   * iterable in the same iteration order.
   */
  private static <E> Collection<E> castOrCopyToCollection(Iterable<E> iterable) {
    return (iterable instanceof Collection)
        ? (Collection<E>) iterable
        : Lists.newArrayList(iterable.iterator());
  }

  /**
   * Adds all elements in {@code iterable} to {@code collection}.
   *
   * @return {@code true} if {@code collection} was modified as a result of this operation.
   */
  public static <T> boolean addAll(Collection<T> addTo, Iterable<? extends T> elementsToAdd) {
    if (elementsToAdd instanceof Collection) {
      Collection<? extends T> c = Collections2.cast(elementsToAdd);
      return addTo.addAll(c);
    }
    return Iterators.addAll(addTo, checkNotNull(elementsToAdd).iterator());
  }
}

