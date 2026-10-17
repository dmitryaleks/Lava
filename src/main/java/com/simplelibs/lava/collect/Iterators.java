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

import static com.simplelibs.lava.base.Preconditions.checkArgument;
import static com.simplelibs.lava.base.Preconditions.checkNotNull;
import static com.simplelibs.lava.collect.CollectPreconditions.checkRemove;

import com.simplelibs.lava.base.Preconditions;
import com.simplelibs.lava.checker.nullness.qual.Nullable;
import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/** Package-private subset of Guava's {@code Iterators} used by Lava's collections. */
final class Iterators {
  private Iterators() {}

  /**
   * Returns the empty iterator.
   *
   * <p>The {@link Iterable} equivalent of this method is {@link ImmutableSet#of()}.
   */
  static <T> UnmodifiableIterator<T> emptyIterator() {
    return emptyListIterator();
  }

  /**
   * Returns the empty iterator.
   *
   * <p>The {@link Iterable} equivalent of this method is {@link ImmutableSet#of()}.
   */
  // Casting to any type is safe since there are no actual elements.
  @SuppressWarnings("unchecked")
  static <T> UnmodifiableListIterator<T> emptyListIterator() {
    return (UnmodifiableListIterator<T>) ArrayItr.EMPTY;
  }

  /** Returns an unmodifiable view of {@code iterator}. */
  public static <T> UnmodifiableIterator<T> unmodifiableIterator(
      final Iterator<? extends T> iterator) {
    checkNotNull(iterator);
    if (iterator instanceof UnmodifiableIterator) {
      @SuppressWarnings("unchecked") // Since it's unmodifiable, the covariant cast is safe
      UnmodifiableIterator<T> result = (UnmodifiableIterator<T>) iterator;
      return result;
    }
    return new UnmodifiableIterator<T>() {
      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public T next() {
        return iterator.next();
      }
    };
  }

  /** Returns {@code true} if {@code iterator} contains {@code element}. */
  public static boolean contains(Iterator<?> iterator, @Nullable Object element) {
    if (element == null) {
      while (iterator.hasNext()) {
        if (iterator.next() == null) {
          return true;
        }
      }
    } else {
      while (iterator.hasNext()) {
        if (element.equals(iterator.next())) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Traverses an iterator and removes every element that belongs to the provided collection. The
   * iterator will be left exhausted: its {@code hasNext()} method will return {@code false}.
   *
   * @param removeFrom the iterator to (potentially) remove elements from
   * @param elementsToRemove the elements to remove
   * @return {@code true} if any element was removed from {@code iterator}
   */
  public static boolean removeAll(Iterator<?> removeFrom, Collection<?> elementsToRemove) {
    checkNotNull(elementsToRemove);
    boolean result = false;
    while (removeFrom.hasNext()) {
      if (elementsToRemove.contains(removeFrom.next())) {
        removeFrom.remove();
        result = true;
      }
    }
    return result;
  }

  /**
   * Removes every element that satisfies the provided predicate from the iterator. The iterator
   * will be left exhausted: its {@code hasNext()} method will return {@code false}.
   *
   * @param removeFrom the iterator to (potentially) remove elements from
   * @param predicate a predicate that determines whether an element should be removed
   * @return {@code true} if any elements were removed from the iterator
   * @since 2.0
   */
  public static <T> boolean removeIf(Iterator<T> removeFrom, Predicate<? super T> predicate) {
    checkNotNull(predicate);
    boolean modified = false;
    while (removeFrom.hasNext()) {
      if (predicate.test(removeFrom.next())) {
        removeFrom.remove();
        modified = true;
      }
    }
    return modified;
  }

  /**
   * Determines whether two iterators contain equal elements in the same order. More specifically,
   * this method returns {@code true} if {@code iterator1} and {@code iterator2} contain the same
   * number of elements and every element of {@code iterator1} is equal to the corresponding element
   * of {@code iterator2}.
   *
   * <p>Note that this will modify the supplied iterators, since they will have been advanced some
   * number of elements forward.
   */
  public static boolean elementsEqual(Iterator<?> iterator1, Iterator<?> iterator2) {
    while (iterator1.hasNext()) {
      if (!iterator2.hasNext()) {
        return false;
      }
      Object o1 = iterator1.next();
      Object o2 = iterator2.next();
      if (!Objects.equals(o1, o2)) {
        return false;
      }
    }
    return !iterator2.hasNext();
  }

  /**
   * Returns a string representation of {@code iterator}, with the format {@code [e1, e2, ..., en]}.
   * The iterator will be left exhausted: its {@code hasNext()} method will return {@code false}.
   */
  public static String toString(Iterator<?> iterator) {
    StringBuilder sb = new StringBuilder().append('[');
    boolean first = true;
    while (iterator.hasNext()) {
      if (!first) {
        sb.append(", ");
      }
      first = false;
      sb.append(iterator.next());
    }
    return sb.append(']').toString();
  }

  /**
   * Returns the single element contained in {@code iterator}.
   *
   * @throws NoSuchElementException if the iterator is empty
   * @throws IllegalArgumentException if the iterator contains multiple elements. The state of the
   *     iterator is unspecified.
   */
  public static <T> T getOnlyElement(Iterator<T> iterator) {
    T first = iterator.next();
    if (!iterator.hasNext()) {
      return first;
    }

    StringBuilder sb = new StringBuilder().append("expected one element but was: <").append(first);
    for (int i = 0; i < 4 && iterator.hasNext(); i++) {
      sb.append(", ").append(iterator.next());
    }
    if (iterator.hasNext()) {
      sb.append(", ...");
    }
    sb.append('>');

    throw new IllegalArgumentException(sb.toString());
  }

  /**
   * Adds all elements in {@code iterator} to {@code collection}. The iterator will be left
   * exhausted: its {@code hasNext()} method will return {@code false}.
   *
   * @return {@code true} if {@code collection} was modified as a result of this operation
   */
  public static <T> boolean addAll(Collection<T> addTo, Iterator<? extends T> iterator) {
    checkNotNull(addTo);
    checkNotNull(iterator);
    boolean wasModified = false;
    while (iterator.hasNext()) {
      wasModified |= addTo.add(iterator.next());
    }
    return wasModified;
  }

  /**
   * Returns a view of {@code unfiltered} containing all elements that satisfy the input predicate
   * {@code retainIfTrue}.
   */
  public static <T> UnmodifiableIterator<T> filter(
      final Iterator<T> unfiltered, final Predicate<? super T> retainIfTrue) {
    checkNotNull(unfiltered);
    checkNotNull(retainIfTrue);
    return new AbstractIterator<T>() {
      @Override
      protected T computeNext() {
        while (unfiltered.hasNext()) {
          T element = unfiltered.next();
          if (retainIfTrue.test(element)) {
            return element;
          }
        }
        return endOfData();
      }
    };
  }

  /**
   * Returns {@code true} if one or more elements returned by {@code iterator} satisfy the given
   * predicate.
   */
  public static <T> boolean any(Iterator<T> iterator, Predicate<? super T> predicate) {
    return indexOf(iterator, predicate) != -1;
  }

  /**
   * Returns the first element in {@code iterator} that satisfies the given predicate; use this
   * method only when such an element is known to exist. If no such element is found, the iterator
   * will be left exhausted: its {@code hasNext()} method will return {@code false}. If it is
   * possible that <i>no</i> element will match, use {@code #tryFind} or {@link #find(Iterator,
   * Predicate, Object)} instead.
   *
   * @throws NoSuchElementException if no element in {@code iterator} matches the given predicate
   */
  public static <T> T find(Iterator<T> iterator, Predicate<? super T> predicate) {
    checkNotNull(iterator);
    checkNotNull(predicate);
    while (iterator.hasNext()) {
      T t = iterator.next();
      if (predicate.test(t)) {
        return t;
      }
    }
    throw new NoSuchElementException();
  }

  /**
   * Returns the first element in {@code iterator} that satisfies the given predicate. If no such
   * element is found, {@code defaultValue} will be returned from this method and the iterator will
   * be left exhausted: its {@code hasNext()} method will return {@code false}. Note that this can
   * usually be handled more naturally using {@code tryFind(iterator, predicate).or(defaultValue)}.
   *
   * @since 7.0
   */
  public static <T> @Nullable T find(
      Iterator<? extends T> iterator, Predicate<? super T> predicate, @Nullable T defaultValue) {
    checkNotNull(iterator);
    checkNotNull(predicate);
    while (iterator.hasNext()) {
      T t = iterator.next();
      if (predicate.test(t)) {
        return t;
      }
    }
    return defaultValue;
  }

  /**
   * Returns the index in {@code iterator} of the first element that satisfies the provided {@code
   * predicate}, or {@code -1} if the Iterator has no such elements.
   *
   * <p>More formally, returns the lowest index {@code i} such that {@code
   * predicate.test(Iterators.get(iterator, i))} returns {@code true}, or {@code -1} if there is no
   * such index.
   *
   * <p>If -1 is returned, the iterator will be left exhausted: its {@code hasNext()} method will
   * return {@code false}. Otherwise, the iterator will be set to the element which satisfies the
   * {@code predicate}.
   *
   * @since 2.0
   */
  public static <T> int indexOf(Iterator<T> iterator, Predicate<? super T> predicate) {
    checkNotNull(predicate, "predicate");
    for (int i = 0; iterator.hasNext(); i++) {
      T current = iterator.next();
      if (predicate.test(current)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Returns a view containing the result of applying {@code function} to each element of {@code
   * fromIterator}.
   *
   * <p>The returned iterator supports {@code remove()} if {@code fromIterator} does. After a
   * successful {@code remove()} call, {@code fromIterator} no longer contains the corresponding
   * element.
   */
  public static <F, T> Iterator<T> transform(
      final Iterator<F> fromIterator, final Function<? super F, ? extends T> function) {
    checkNotNull(function);
    return new TransformedIterator<F, T>(fromIterator) {
      @Override
      T transform(F from) {
        return function.apply(from);
      }
    };
  }

  /**
   * Returns the next element in {@code iterator} or {@code defaultValue} if the iterator is empty.
   * The {@link Iterables} analog to this method is {@code Iterables#getFirst}.
   *
   * @param defaultValue the default value to return if the iterator is empty
   * @return the next element of {@code iterator} or the default value
   * @since 7.0
   */
  public static <T> @Nullable T getNext(Iterator<? extends T> iterator, @Nullable T defaultValue) {
    return iterator.hasNext() ? iterator.next() : defaultValue;
  }

  /**
   * Deletes and returns the next value from the iterator, or returns {@code null} if there is no
   * such value.
   */
  static <T> @Nullable T pollNext(Iterator<T> iterator) {
    if (iterator.hasNext()) {
      T result = iterator.next();
      iterator.remove();
      return result;
    } else {
      return null;
    }
  }

  // Methods only in Iterators, not in Iterables

  /** Clears the iterator using its remove method. */
  static void clear(Iterator<?> iterator) {
    checkNotNull(iterator);
    while (iterator.hasNext()) {
      iterator.next();
      iterator.remove();
    }
  }

  /**
   * Returns an iterator containing the elements of {@code array} in order. The returned iterator is
   * a view of the array; subsequent changes to the array will be reflected in the iterator.
   *
   * <p><b>Note:</b> It is often preferable to represent your data using a collection type, for
   * example using {@code Arrays#asList(Object[])}, making this method unnecessary.
   *
   * <p>The {@code Iterable} equivalent of this method is either {@code Arrays#asList(Object[])},
   * {@link ImmutableList#copyOf(Object[])}}, or {@link ImmutableList#of}.
   */
  @SafeVarargs
  public static <T> UnmodifiableIterator<T> forArray(final T... array) {
    return forArray(array, 0, array.length, 0);
  }

  /**
   * Returns a list iterator containing the elements in the specified range of {@code array} in
   * order, starting at the specified index.
   *
   * <p>The {@code Iterable} equivalent of this method is {@code
   * Arrays.asList(array).subList(offset, offset + length).listIterator(index)}.
   */
  static <T> UnmodifiableListIterator<T> forArray(
      final T[] array, final int offset, int length, int index) {
    checkArgument(length >= 0);
    int end = offset + length;

    // Technically we should give a slightly more descriptive error on overflow
    Preconditions.checkPositionIndexes(offset, end, array.length);
    Preconditions.checkPositionIndex(index, length);
    if (length == 0) {
      return emptyListIterator();
    }
    return new ArrayItr<T>(array, offset, length, index);
  }

  private static final class ArrayItr<T> extends AbstractIndexedListIterator<T> {
    static final UnmodifiableListIterator<Object> EMPTY = new ArrayItr<>(new Object[0], 0, 0, 0);

    private final T[] array;
    private final int offset;

    ArrayItr(T[] array, int offset, int length, int index) {
      super(length, index);
      this.array = array;
      this.offset = offset;
    }

    @Override
    protected T get(int index) {
      return array[offset + index];
    }
  }

  /**
   * Returns an iterator containing only {@code value}.
   *
   * <p>The {@link Iterable} equivalent of this method is {@code Collections#singleton}.
   */
  public static <T> UnmodifiableIterator<T> singletonIterator(final @Nullable T value) {
    return new UnmodifiableIterator<T>() {
      boolean done;

      @Override
      public boolean hasNext() {
        return !done;
      }

      @Override
      public T next() {
        if (done) {
          throw new NoSuchElementException();
        }
        done = true;
        return value;
      }
    };
  }

  /** Used to avoid http://bugs.sun.com/view_bug.do?bug_id=6558557 */
  static <T> ListIterator<T> cast(Iterator<T> iterator) {
    return (ListIterator<T>) iterator;
  }
}

