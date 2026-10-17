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

import static com.simplelibs.lava.base.Preconditions.checkPositionIndexes;

import java.lang.reflect.Array;
import java.util.Collection;

/** Package-private subset of Guava's {@code ObjectArrays} used by Lava's collections. */
final class ObjectArrays {
  private ObjectArrays() {}

  /**
   * Returns a new array of the given length with the specified component type.
   *
   * @param type the component type
   * @param length the length of the new array
   */
  @SuppressWarnings("unchecked")
  public static <T> T[] newArray(Class<T> type, int length) {
    return (T[]) Array.newInstance(type, length);
  }

  /**
   * Returns a new array of the given length with the same type as a reference array.
   *
   * @param reference any array of the desired type
   * @param length the length of the new array
   */
  public static <T> T[] newArray(T[] reference, int length) {
    return Platform.newArray(reference, length);
  }

  static Object[] checkElementsNotNull(Object... array) {
    return checkElementsNotNull(array, array.length);
  }

  static Object[] checkElementsNotNull(Object[] array, int length) {
    for (int i = 0; i < length; i++) {
      checkElementNotNull(array[i], i);
    }
    return array;
  }

  // We do this instead of Preconditions.checkNotNull to save boxing and array
  // creation cost.
  static Object checkElementNotNull(Object element, int index) {
    if (element == null) {
      throw new NullPointerException("at index " + index);
    }
    return element;
  }
}

