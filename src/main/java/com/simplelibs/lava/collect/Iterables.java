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
import java.util.Iterator;
import java.util.function.Predicate;

/** Package-private subset of Guava's {@code Iterables} used by Lava's collections. */
final class Iterables {
  private Iterables() {}

  /**
   * Removes, from an iterable, every element that satisfies the provided predicate.
   *
   * <p>Removals may or may not happen immediately as each element is tested against the predicate.
   * The behavior of this method is not specified if {@code predicate} is dependent on {@code
   * removeFrom}.
   *
   * <p><b>Java 8 users:</b> if {@code removeFrom} is a {@link Collection}, use {@code
   * removeFrom.removeIf(predicate)} instead.
   *
   * @param removeFrom the iterable to (potentially) remove elements from
   * @param predicate a predicate that determines whether an element should be removed
   * @return {@code true} if any elements were removed from the iterable
   * @throws UnsupportedOperationException if the iterable does not support {@code remove()}.
   * @since 2.0
   */
  public static <T> boolean removeIf(Iterable<T> removeFrom, Predicate<? super T> predicate) {
    if (removeFrom instanceof Collection) {
      return ((Collection<T>) removeFrom).removeIf(predicate);
    }
    return Iterators.removeIf(removeFrom.iterator(), predicate);
  }

  /** Removes and returns the first matching element, or returns {@code null} if there is none. */
  static <T> @Nullable T removeFirstMatching(
      Iterable<T> removeFrom, Predicate<? super T> predicate) {
    checkNotNull(predicate);
    Iterator<T> iterator = removeFrom.iterator();
    while (iterator.hasNext()) {
      T next = iterator.next();
      if (predicate.test(next)) {
        iterator.remove();
        return next;
      }
    }
    return null;
  }

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
   * @throws java.util.NoSuchElementException if the iterable is empty
   * @throws IllegalArgumentException if the iterable contains multiple elements
   */
  public static <T> T getOnlyElement(Iterable<T> iterable) {
    return Iterators.getOnlyElement(iterable.iterator());
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

  /**
   * Returns {@code true} if any element in {@code iterable} satisfies the predicate.
   *
   * <p><b>{@code Stream} equivalent:</b> {@code Stream#anyMatch}.
   */
  public static <T> boolean any(Iterable<T> iterable, Predicate<? super T> predicate) {
    return Iterators.any(iterable.iterator(), predicate);
  }

  /**
   * Returns the first element in {@code iterable} that satisfies the given predicate, or {@code
   * defaultValue} if none found. Note that this can usually be handled more naturally using {@code
   * tryFind(iterable, predicate).or(defaultValue)}.
   *
   * <p><b>{@code Stream} equivalent:</b> {@code
   * stream.filter(predicate).findFirst().orElse(defaultValue)}
   *
   * @since 7.0
   */
  public static <T> @Nullable T find(
      Iterable<? extends T> iterable, Predicate<? super T> predicate, @Nullable T defaultValue) {
    return Iterators.find(iterable.iterator(), predicate, defaultValue);
  }

  // Methods only in Iterables, not in Iterators

  /**
   * Determines if the given iterable contains no elements.
   *
   * <p>There is no precise {@link Iterator} equivalent to this method, since one can only ask an
   * iterator whether it has any elements <i>remaining</i> (which one does using {@link
   * Iterator#hasNext}).
   *
   * <p><b>{@code Stream} equivalent:</b> {@code !stream.findAny().isPresent()}
   *
   * @return {@code true} if the iterable contains no elements
   */
  public static boolean isEmpty(Iterable<?> iterable) {
    if (iterable instanceof Collection) {
      return ((Collection<?>) iterable).isEmpty();
    }
    return !iterable.iterator().hasNext();
  }
}

