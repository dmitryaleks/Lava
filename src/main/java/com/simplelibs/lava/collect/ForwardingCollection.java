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

import java.util.Collection;
import java.util.Iterator;

/**
 * A collection which forwards all its method calls to another collection, as {@code delegate()}
 * returns. Package-private subset of Guava's {@code ForwardingCollection} used by Lava's views: it
 * overrides the interface methods only, and Guava's {@code standard*} helper methods are not
 * ported.
 */
abstract class ForwardingCollection<E> extends ForwardingObject implements Collection<E> {

  /** Constructor for use by subclasses. */
  protected ForwardingCollection() {}

  @Override
  protected abstract Collection<E> delegate();

  @Override
  public Iterator<E> iterator() {
    return delegate().iterator();
  }

  @Override
  public int size() {
    return delegate().size();
  }

  @Override
  public boolean removeAll(Collection<?> collection) {
    return delegate().removeAll(collection);
  }

  @Override
  public boolean isEmpty() {
    return delegate().isEmpty();
  }

  @Override
  public boolean contains(Object object) {
    return delegate().contains(object);
  }

  @Override
  public boolean add(E element) {
    return delegate().add(element);
  }

  @Override
  public boolean remove(Object object) {
    return delegate().remove(object);
  }

  @Override
  public boolean containsAll(Collection<?> collection) {
    return delegate().containsAll(collection);
  }

  @Override
  public boolean addAll(Collection<? extends E> collection) {
    return delegate().addAll(collection);
  }

  @Override
  public boolean retainAll(Collection<?> collection) {
    return delegate().retainAll(collection);
  }

  @Override
  public void clear() {
    delegate().clear();
  }

  @Override
  public Object[] toArray() {
    return delegate().toArray();
  }

  @Override
  public <T> T[] toArray(T[] array) {
    return delegate().toArray(array);
  }
}
