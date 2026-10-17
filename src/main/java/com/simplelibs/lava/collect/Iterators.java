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

import java.util.Objects;
import com.simplelibs.lava.base.Preconditions;
import com.simplelibs.lava.checker.nullness.qual.Nullable;
import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;

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

  /**
   * Simply returns its argument.
   *
   * @deprecated no need to use this
   * @since 10.0
   */
  @Deprecated
  public static <T> UnmodifiableIterator<T> unmodifiableIterator(UnmodifiableIterator<T> iterator) {
    return checkNotNull(iterator);
  }

  /**
   * Returns the number of elements remaining in {@code iterator}. The iterator will be left
   * exhausted: its {@code hasNext()} method will return {@code false}.
   */
  public static int size(Iterator<?> iterator) {
    long count = 0L;
    while (iterator.hasNext()) {
      iterator.next();
      count++;
    }
    return CollectMath.saturatedCast(count);
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
   * Returns the single element contained in {@code iterator}, or {@code defaultValue} if the
   * iterator is empty.
   *
   * @throws IllegalArgumentException if the iterator contains multiple elements. The state of the
   *     iterator is unspecified.
   */
  public static <T> @Nullable T getOnlyElement(
      Iterator<? extends T> iterator, @Nullable T defaultValue) {
    return iterator.hasNext() ? getOnlyElement(iterator) : defaultValue;
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
   * Returns an iterator containing the elements of {@code array} in order. The returned iterator is
   * a view of the array; subsequent changes to the array will be reflected in the iterator.
   *
   * <p><b>Note:</b> It is often preferable to represent your data using a collection type, for
   * example using {@link Arrays#asList(Object[])}, making this method unnecessary.
   *
   * <p>The {@code Iterable} equivalent of this method is either {@link Arrays#asList(Object[])},
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
   * <p>The {@link Iterable} equivalent of this method is {@link Collections#singleton}.
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

