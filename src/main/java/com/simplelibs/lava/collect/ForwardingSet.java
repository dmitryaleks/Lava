/*
 * Copyright (C) 2007 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified for Lava: single-threaded, Guava-independent port of Guava 27.0.1.
 */

package com.simplelibs.lava.collect;

import com.simplelibs.lava.checker.nullness.qual.Nullable;
import java.util.Set;

/**
 * A set which forwards all its method calls to another set, as {@code delegate()} returns.
 * Package-private subset of Guava's {@code ForwardingSet} used by Lava's views: it overrides the
 * interface methods only, and Guava's {@code standard*} helper methods are not ported.
 */
abstract class ForwardingSet<E> extends ForwardingCollection<E> implements Set<E> {

  /** Constructor for use by subclasses. */
  protected ForwardingSet() {}

  @Override
  protected abstract Set<E> delegate();

  @Override
  public boolean equals(@Nullable Object object) {
    return object == this || delegate().equals(object);
  }

  @Override
  public int hashCode() {
    return delegate().hashCode();
  }
}
