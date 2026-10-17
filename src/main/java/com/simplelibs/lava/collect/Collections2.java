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

import static com.simplelibs.lava.collect.CollectPreconditions.checkNonnegative;

import java.util.Collection;

/** Package-private subset of Guava's {@code Collections2} used by Lava's collections. */
final class Collections2 {
  private Collections2() {}

  /** Returns best-effort-sized StringBuilder based on the given collection size. */
  static StringBuilder newStringBuilderForCollection(int size) {
    checkNonnegative(size, "size");
    return new StringBuilder((int) Math.min(size * 8L, CollectMath.MAX_POWER_OF_TWO));
  }

  /** Used to avoid http://bugs.sun.com/view_bug.do?bug_id=6558557 */
  static <T> Collection<T> cast(Iterable<T> iterable) {
    return (Collection<T>) iterable;
  }
}

