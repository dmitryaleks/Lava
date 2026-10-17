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

import com.simplelibs.lava.checker.nullness.qual.Nullable;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** Package-private subset of Guava's {@code Sets} used by Lava's collections. */
final class Sets {
  private Sets() {}

  /**
   * Returns a new hash set using the smallest initial table size that can hold {@code expectedSize}
   * elements without resizing. Note that this is not what {@link HashSet#HashSet(int)} does, but it
   * is what most users want and expect it to do.
   *
   * <p>This behavior can't be broadly guaranteed, but has been tested with OpenJDK 1.7 and 1.8.
   *
   * @param expectedSize the number of elements you expect to add to the returned set
   * @return a new, empty hash set with enough capacity to hold {@code expectedSize} elements
   *     without resizing
   * @throws IllegalArgumentException if {@code expectedSize} is negative
   */
  public static <E> HashSet<E> newHashSetWithExpectedSize(int expectedSize) {
    return new HashSet<E>(Maps.capacity(expectedSize));
  }

  /**
   * Creates a {@code LinkedHashSet} instance, with a high enough "initial capacity" that it
   * <i>should</i> hold {@code expectedSize} elements without growth. This behavior cannot be
   * broadly guaranteed, but it is observed to be true for OpenJDK 1.7. It also can't be guaranteed
   * that the method isn't inadvertently <i>oversizing</i> the returned set.
   *
   * @param expectedSize the number of elements you expect to add to the returned set
   * @return a new, empty {@code LinkedHashSet} with enough capacity to hold {@code expectedSize}
   *     elements without resizing
   * @throws IllegalArgumentException if {@code expectedSize} is negative
   * @since 11.0
   */
  public static <E> LinkedHashSet<E> newLinkedHashSetWithExpectedSize(int expectedSize) {
    return new LinkedHashSet<E>(Maps.capacity(expectedSize));
  }

  /** An implementation for {@link Set#hashCode()}. */
  static int hashCodeImpl(Set<?> s) {
    int hashCode = 0;
    for (Object o : s) {
      hashCode += o != null ? o.hashCode() : 0;

      hashCode = ~~hashCode;
      // Needed to deal with unusual integer overflow in GWT.
    }
    return hashCode;
  }

  /** An implementation for {@link Set#equals(Object)}. */
  static boolean equalsImpl(Set<?> s, @Nullable Object object) {
    if (s == object) {
      return true;
    }
    if (object instanceof Set) {
      Set<?> o = (Set<?>) object;

      try {
        return s.size() == o.size() && s.containsAll(o);
      } catch (NullPointerException | ClassCastException ignored) {
        return false;
      }
    }
    return false;
  }
}

