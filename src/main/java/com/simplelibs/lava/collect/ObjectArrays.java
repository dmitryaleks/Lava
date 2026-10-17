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

  /**
   * Returns an array containing all of the elements in the specified collection; the runtime type
   * of the returned array is that of the specified array. If the collection fits in the specified
   * array, it is returned therein. Otherwise, a new array is allocated with the runtime type of the
   * specified array and the size of the specified collection.
   *
   * <p>If the collection fits in the specified array with room to spare (i.e., the array has more
   * elements than the collection), the element in the array immediately following the end of the
   * collection is set to {@code null}. This is useful in determining the length of the collection
   * <i>only</i> if the caller knows that the collection does not contain any null elements.
   *
   * <p>This method returns the elements in the order they are returned by the collection's
   * iterator.
   *
   * <p>TODO(kevinb): support concurrently modified collections?
   *
   * @param c the collection for which to return an array of elements
   * @param array the array in which to place the collection elements
   * @throws ArrayStoreException if the runtime type of the specified array is not a supertype of
   *     the runtime type of every element in the specified collection
   */
  static <T> T[] toArrayImpl(Collection<?> c, T[] array) {
    int size = c.size();
    if (array.length < size) {
      array = newArray(array, size);
    }
    fillArray(c, array);
    if (array.length > size) {
      array[size] = null;
    }
    return array;
  }

  /**
   * Implementation of {@link Collection#toArray(Object[])} for collections backed by an object
   * array. the runtime type of the returned array is that of the specified array. If the collection
   * fits in the specified array, it is returned therein. Otherwise, a new array is allocated with
   * the runtime type of the specified array and the size of the specified collection.
   *
   * <p>If the collection fits in the specified array with room to spare (i.e., the array has more
   * elements than the collection), the element in the array immediately following the end of the
   * collection is set to {@code null}. This is useful in determining the length of the collection
   * <i>only</i> if the caller knows that the collection does not contain any null elements.
   */
  static <T> T[] toArrayImpl(Object[] src, int offset, int len, T[] dst) {
    checkPositionIndexes(offset, offset + len, src.length);
    if (dst.length < len) {
      dst = newArray(dst, len);
    } else if (dst.length > len) {
      dst[len] = null;
    }
    System.arraycopy(src, offset, dst, 0, len);
    return dst;
  }

  /**
   * Returns an array containing all of the elements in the specified collection. This method
   * returns the elements in the order they are returned by the collection's iterator. The returned
   * array is "safe" in that no references to it are maintained by the collection. The caller is
   * thus free to modify the returned array.
   *
   * <p>This method assumes that the collection size doesn't change while the method is running.
   *
   * <p>TODO(kevinb): support concurrently modified collections?
   *
   * @param c the collection for which to return an array of elements
   */
  static Object[] toArrayImpl(Collection<?> c) {
    return fillArray(c, new Object[c.size()]);
  }

  private static Object[] fillArray(Iterable<?> elements, Object[] array) {
    int i = 0;
    for (Object element : elements) {
      array[i++] = element;
    }
    return array;
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

