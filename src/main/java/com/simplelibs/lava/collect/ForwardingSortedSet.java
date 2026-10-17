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
import java.util.Comparator;
import java.util.SortedSet;

/**
 * A sorted set which forwards all its method calls to another sorted set, as {@code delegate()}
 * returns. Package-private subset of Guava's {@code ForwardingSortedSet} used by Lava's views: it
 * overrides the interface methods only, and Guava's {@code standard*} helper methods are not
 * ported.
 */
abstract class ForwardingSortedSet<E> extends ForwardingSet<E> implements SortedSet<E> {

  /** Constructor for use by subclasses. */
  protected ForwardingSortedSet() {}

  @Override
  protected abstract SortedSet<E> delegate();

  @Override
  public Comparator<? super E> comparator() {
    return delegate().comparator();
  }

  @Override
  public E first() {
    return delegate().first();
  }

  @Override
  public SortedSet<E> headSet(E toElement) {
    return delegate().headSet(toElement);
  }

  @Override
  public E last() {
    return delegate().last();
  }

  @Override
  public SortedSet<E> subSet(E fromElement, E toElement) {
    return delegate().subSet(fromElement, toElement);
  }

  @Override
  public SortedSet<E> tailSet(E fromElement) {
    return delegate().tailSet(fromElement);
  }
}
