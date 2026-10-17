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

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Methods factored out so that Guava could emulate them differently in GWT. */
final class Platform {
  static <K, V> Map<K, V> newHashMapWithExpectedSize(int expectedSize) {
    return Maps.newHashMapWithExpectedSize(expectedSize);
  }

  static <K, V> Map<K, V> newLinkedHashMapWithExpectedSize(int expectedSize) {
    return Maps.newLinkedHashMapWithExpectedSize(expectedSize);
  }

  static <E> Set<E> newHashSetWithExpectedSize(int expectedSize) {
    return Sets.newHashSetWithExpectedSize(expectedSize);
  }

  static <E> Set<E> newLinkedHashSetWithExpectedSize(int expectedSize) {
    return Sets.newLinkedHashSetWithExpectedSize(expectedSize);
  }

  static <K, V> Map<K, V> preservesInsertionOrderOnPutsMap() {
    return new LinkedHashMap<>();
  }

  static <E> Set<E> preservesInsertionOrderOnAddsSet() {
    return new LinkedHashSet<>();
  }

  /**
   * Returns a new array of the given length with the same type as a reference array.
   *
   * @param reference any array of the desired type
   * @param length the length of the new array
   */
  static <T> T[] newArray(T[] reference, int length) {
    Class<?> type = reference.getClass().getComponentType();

    // the cast is safe because
    // result.getClass() == reference.getClass().getComponentType()
    @SuppressWarnings("unchecked")
    T[] result = (T[]) Array.newInstance(type, length);
    return result;
  }

  /** Equivalent to Arrays.copyOfRange(source, from, to, arrayOfType.getClass()). */
  @SuppressWarnings("unchecked")
  static <T> T[] copy(Object[] source, int from, int to, T[] arrayOfType) {
    return Arrays.copyOfRange(source, from, to, (Class<? extends T[]>) arrayOfType.getClass());
  }

  private Platform() {}
}
