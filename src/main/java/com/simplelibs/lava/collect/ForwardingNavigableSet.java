/*
 * Copyright (C) 2012 The Guava Authors
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

import java.util.Iterator;
import java.util.NavigableSet;

/**
 * A navigable set which forwards all its method calls to another navigable set, as {@code
 * delegate()} returns. Package-private subset of Guava's {@code ForwardingNavigableSet} used by
 * Lava's views: it overrides the interface methods only, and Guava's {@code standard*} helper
 * methods are not ported.
 */
abstract class ForwardingNavigableSet<E> extends ForwardingSortedSet<E>
    implements NavigableSet<E> {

  /** Constructor for use by subclasses. */
  protected ForwardingNavigableSet() {}

  @Override
  protected abstract NavigableSet<E> delegate();

  @Override
  public E lower(E e) {
    return delegate().lower(e);
  }

  @Override
  public E floor(E e) {
    return delegate().floor(e);
  }

  @Override
  public E ceiling(E e) {
    return delegate().ceiling(e);
  }

  @Override
  public E higher(E e) {
    return delegate().higher(e);
  }

  @Override
  public E pollFirst() {
    return delegate().pollFirst();
  }

  @Override
  public E pollLast() {
    return delegate().pollLast();
  }

  @Override
  public NavigableSet<E> descendingSet() {
    return delegate().descendingSet();
  }

  @Override
  public Iterator<E> descendingIterator() {
    return delegate().descendingIterator();
  }

  @Override
  public NavigableSet<E> subSet(
      E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
    return delegate().subSet(fromElement, fromInclusive, toElement, toInclusive);
  }

  @Override
  public NavigableSet<E> headSet(E toElement, boolean inclusive) {
    return delegate().headSet(toElement, inclusive);
  }

  @Override
  public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
    return delegate().tailSet(fromElement, inclusive);
  }
}
