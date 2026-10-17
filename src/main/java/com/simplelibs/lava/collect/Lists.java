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
import static com.simplelibs.lava.collect.CollectPreconditions.checkNonnegative;

import com.simplelibs.lava.checker.nullness.qual.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.RandomAccess;

/** Lists (public API completed in a later milestone). */
final class Lists {
  private Lists() {}

  // ArrayList

  /**
   * Creates a <i>mutable</i>, empty {@code ArrayList} instance (for Java 6 and earlier).
   *
   * <p><b>Note:</b> if mutability is not required, use {@link ImmutableList#of()} instead.
   *
   * <p><b>Note for Java 7 and later:</b> this method is now unnecessary and should be treated as
   * deprecated. Instead, use the {@code ArrayList} {@linkplain ArrayList#ArrayList() constructor}
   * directly, taking advantage of the new <a href="http://goo.gl/iz2Wi">"diamond" syntax</a>.
   */
  public static <E> ArrayList<E> newArrayList() {
    return new ArrayList<>();
  }

  /**
   * Creates a <i>mutable</i> {@code ArrayList} instance containing the given elements.
   *
   * <p><b>Note:</b> essentially the only reason to use this method is when you will need to add or
   * remove elements later. Otherwise, for non-null elements use {@link ImmutableList#of()} (for
   * varargs) or {@link ImmutableList#copyOf(Object[])} (for an array) instead. If any elements
   * might be null, or you need support for {@link List#set(int, Object)}, use {@link
   * Arrays#asList}.
   *
   * <p>Note that even when you do need the ability to add or remove, this method provides only a
   * tiny bit of syntactic sugar for {@code newArrayList(}{@link Arrays#asList asList}{@code
   * (...))}, or for creating an empty list then calling {@link Collections#addAll}. This method is
   * not actually very useful and will likely be deprecated in the future.
   */
  @SafeVarargs
  public static <E> ArrayList<E> newArrayList(E... elements) {
    checkNotNull(elements); // for GWT
    // Avoid integer overflow when a large array is passed in
    int capacity = computeArrayListCapacity(elements.length);
    ArrayList<E> list = new ArrayList<>(capacity);
    Collections.addAll(list, elements);
    return list;
  }

  /**
   * Creates a <i>mutable</i> {@code ArrayList} instance containing the given elements; a very thin
   * shortcut for creating an empty list then calling {@link Iterables#addAll}.
   *
   * <p><b>Note:</b> if mutability is not required and the elements are non-null, use {@link
   * ImmutableList#copyOf(Iterable)} instead. (Or, change {@code elements} to be a {@link
   * FluentIterable} and call {@code elements.toList()}.)
   *
   * <p><b>Note for Java 7 and later:</b> if {@code elements} is a {@link Collection}, you don't
   * need this method. Use the {@code ArrayList} {@linkplain ArrayList#ArrayList(Collection)
   * constructor} directly, taking advantage of the new <a href="http://goo.gl/iz2Wi">"diamond"
   * syntax</a>.
   */
  public static <E> ArrayList<E> newArrayList(Iterable<? extends E> elements) {
    checkNotNull(elements); // for GWT
    // Let ArrayList's sizing logic work, if possible
    return (elements instanceof Collection)
        ? new ArrayList<>(Collections2.cast(elements))
        : newArrayList(elements.iterator());
  }

  /**
   * Creates a <i>mutable</i> {@code ArrayList} instance containing the given elements; a very thin
   * shortcut for creating an empty list and then calling {@link Iterators#addAll}.
   *
   * <p><b>Note:</b> if mutability is not required and the elements are non-null, use {@link
   * ImmutableList#copyOf(Iterator)} instead.
   */
  public static <E> ArrayList<E> newArrayList(Iterator<? extends E> elements) {
    ArrayList<E> list = newArrayList();
    Iterators.addAll(list, elements);
    return list;
  }

  static int computeArrayListCapacity(int arraySize) {
    checkNonnegative(arraySize, "arraySize");

    // TODO(kevinb): Figure out the right behavior, and document it
    return CollectMath.saturatedCast(5L + arraySize + (arraySize / 10));
  }

  /** An implementation of {@link List#hashCode()}. */
  static int hashCodeImpl(List<?> list) {
    // TODO(lowasser): worth optimizing for RandomAccess?
    int hashCode = 1;
    for (Object o : list) {
      hashCode = 31 * hashCode + (o == null ? 0 : o.hashCode());

      hashCode = ~~hashCode;
      // needed to deal with GWT integer overflow
    }
    return hashCode;
  }

  /** An implementation of {@link List#equals(Object)}. */
  static boolean equalsImpl(List<?> thisList, @Nullable Object other) {
    if (other == checkNotNull(thisList)) {
      return true;
    }
    if (!(other instanceof List)) {
      return false;
    }
    List<?> otherList = (List<?>) other;
    int size = thisList.size();
    if (size != otherList.size()) {
      return false;
    }
    if (thisList instanceof RandomAccess && otherList instanceof RandomAccess) {
      // avoid allocation and use the faster loop
      for (int i = 0; i < size; i++) {
        if (!Objects.equals(thisList.get(i), otherList.get(i))) {
          return false;
        }
      }
      return true;
    } else {
      return Iterators.elementsEqual(thisList.iterator(), otherList.iterator());
    }
  }

  /** An implementation of {@link List#indexOf(Object)}. */
  static int indexOfImpl(List<?> list, @Nullable Object element) {
    if (list instanceof RandomAccess) {
      return indexOfRandomAccess(list, element);
    } else {
      ListIterator<?> listIterator = list.listIterator();
      while (listIterator.hasNext()) {
        if (Objects.equals(element, listIterator.next())) {
          return listIterator.previousIndex();
        }
      }
      return -1;
    }
  }

  private static int indexOfRandomAccess(List<?> list, @Nullable Object element) {
    int size = list.size();
    if (element == null) {
      for (int i = 0; i < size; i++) {
        if (list.get(i) == null) {
          return i;
        }
      }
    } else {
      for (int i = 0; i < size; i++) {
        if (element.equals(list.get(i))) {
          return i;
        }
      }
    }
    return -1;
  }

  /** An implementation of {@link List#lastIndexOf(Object)}. */
  static int lastIndexOfImpl(List<?> list, @Nullable Object element) {
    if (list instanceof RandomAccess) {
      return lastIndexOfRandomAccess(list, element);
    } else {
      ListIterator<?> listIterator = list.listIterator(list.size());
      while (listIterator.hasPrevious()) {
        if (Objects.equals(element, listIterator.previous())) {
          return listIterator.nextIndex();
        }
      }
      return -1;
    }
  }

  private static int lastIndexOfRandomAccess(List<?> list, @Nullable Object element) {
    if (element == null) {
      for (int i = list.size() - 1; i >= 0; i--) {
        if (list.get(i) == null) {
          return i;
        }
      }
    } else {
      for (int i = list.size() - 1; i >= 0; i--) {
        if (element.equals(list.get(i))) {
          return i;
        }
      }
    }
    return -1;
  }
}

