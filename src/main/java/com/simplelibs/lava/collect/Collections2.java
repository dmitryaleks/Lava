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

import static com.simplelibs.lava.base.Preconditions.checkArgument;
import static com.simplelibs.lava.base.Preconditions.checkNotNull;
import static com.simplelibs.lava.collect.CollectPreconditions.checkNonnegative;

import com.simplelibs.lava.checker.nullness.qual.Nullable;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Package-private subset of Guava's {@code Collections2} used by Lava's collections. */
final class Collections2 {
  private Collections2() {}

  /**
   * Returns the elements of {@code unfiltered} that satisfy a predicate. The returned collection is
   * a live view of {@code unfiltered}; changes to one affect the other.
   *
   * <p>The resulting collection's iterator does not support {@code remove()}, but all other
   * collection methods are supported. When given an element that doesn't satisfy the predicate, the
   * collection's {@code add()} and {@code addAll()} methods throw an {@link
   * IllegalArgumentException}. When methods such as {@code removeAll()} and {@code clear()} are
   * called on the filtered collection, only elements that satisfy the filter will be removed from
   * the underlying collection.
   *
   * <p>The returned collection isn't threadsafe or serializable, even if {@code unfiltered} is.
   *
   * <p>Many of the filtered collection's methods, such as {@code size()}, iterate across every
   * element in the underlying collection and determine which elements satisfy the filter. When a
   * live view is <i>not</i> needed, it may be faster to copy {@code Iterables.filter(unfiltered,
   * predicate)} and use the copy.
   *
   * <p><b>Warning:</b> {@code predicate} must be <i>consistent with equals</i>, as documented at
   * {@link Predicate#apply}. Do not provide a predicate such as {@code
   * CollectPredicates.instanceOf(ArrayList.class)}, which is inconsistent with equals. (See {@link
   * Iterables#filter(Iterable, Class)} for related functionality.)
   *
   * <p><b>{@code Stream} equivalent:</b> {@link java.util.stream.Stream#filter Stream.filter}.
   */
  // TODO(kevinb): how can we omit that Iterables link when building gwt
  // javadoc?
  public static <E> Collection<E> filter(Collection<E> unfiltered, Predicate<? super E> predicate) {
    if (unfiltered instanceof FilteredCollection) {
      // Support clear(), removeAll(), and retainAll() when filtering a filtered
      // collection.
      return ((FilteredCollection<E>) unfiltered).createCombined(predicate);
    }

    return new FilteredCollection<E>(checkNotNull(unfiltered), checkNotNull(predicate));
  }

  /**
   * Delegates to {@link Collection#contains}. Returns {@code false} if the {@code contains} method
   * throws a {@code ClassCastException} or {@code NullPointerException}.
   */
  static boolean safeContains(Collection<?> collection, @Nullable Object object) {
    checkNotNull(collection);
    try {
      return collection.contains(object);
    } catch (ClassCastException | NullPointerException e) {
      return false;
    }
  }

  /**
   * Delegates to {@link Collection#remove}. Returns {@code false} if the {@code remove} method
   * throws a {@code ClassCastException} or {@code NullPointerException}.
   */
  static boolean safeRemove(Collection<?> collection, @Nullable Object object) {
    checkNotNull(collection);
    try {
      return collection.remove(object);
    } catch (ClassCastException | NullPointerException e) {
      return false;
    }
  }

  static class FilteredCollection<E> extends AbstractCollection<E> {
    final Collection<E> unfiltered;
    final Predicate<? super E> predicate;

    FilteredCollection(Collection<E> unfiltered, Predicate<? super E> predicate) {
      this.unfiltered = unfiltered;
      this.predicate = predicate;
    }

    FilteredCollection<E> createCombined(Predicate<? super E> newPredicate) {
      return new FilteredCollection<E>(unfiltered, CollectPredicates.<E>and(predicate, newPredicate));
      // .<E> above needed to compile in JDK 5
    }

    @Override
    public boolean add(E element) {
      checkArgument(predicate.test(element));
      return unfiltered.add(element);
    }

    @Override
    public boolean addAll(Collection<? extends E> collection) {
      for (E element : collection) {
        checkArgument(predicate.test(element));
      }
      return unfiltered.addAll(collection);
    }

    @Override
    public void clear() {
      Iterables.removeIf(unfiltered, predicate);
    }

    @Override
    public boolean contains(@Nullable Object element) {
      if (safeContains(unfiltered, element)) {
        @SuppressWarnings("unchecked") // element is in unfiltered, so it must be an E
        E e = (E) element;
        return predicate.test(e);
      }
      return false;
    }

    @Override
    public boolean containsAll(Collection<?> collection) {
      return containsAllImpl(this, collection);
    }

    @Override
    public boolean isEmpty() {
      return !Iterables.any(unfiltered, predicate);
    }

    @Override
    public Iterator<E> iterator() {
      return Iterators.filter(unfiltered.iterator(), predicate);
    }

    @Override
    public Spliterator<E> spliterator() {
      return CollectSpliterators.filter(unfiltered.spliterator(), predicate);
    }

    @Override
    public void forEach(Consumer<? super E> action) {
      checkNotNull(action);
      unfiltered.forEach(
          (E e) -> {
            if (predicate.test(e)) {
              action.accept(e);
            }
          });
    }

    @Override
    public boolean remove(Object element) {
      return contains(element) && unfiltered.remove(element);
    }

    @Override
    public boolean removeAll(final Collection<?> collection) {
      return removeIf(collection::contains);
    }

    @Override
    public boolean retainAll(final Collection<?> collection) {
      return removeIf(element -> !collection.contains(element));
    }

    @Override
    public boolean removeIf(java.util.function.Predicate<? super E> filter) {
      checkNotNull(filter);
      return unfiltered.removeIf(element -> predicate.test(element) && filter.test(element));
    }

    @Override
    public int size() {
      int size = 0;
      for (E e : unfiltered) {
        if (predicate.test(e)) {
          size++;
        }
      }
      return size;
    }

    @Override
    public Object[] toArray() {
      // creating an ArrayList so filtering happens once
      return Lists.newArrayList(iterator()).toArray();
    }

    @Override
    public <T> T[] toArray(T[] array) {
      return Lists.newArrayList(iterator()).toArray(array);
    }
  }

  /**
   * Returns a collection that applies {@code function} to each element of {@code fromCollection}.
   * The returned collection is a live view of {@code fromCollection}; changes to one affect the
   * other.
   *
   * <p>The returned collection's {@code add()} and {@code addAll()} methods throw an {@link
   * UnsupportedOperationException}. All other collection methods are supported, as long as {@code
   * fromCollection} supports them.
   *
   * <p>The returned collection isn't threadsafe or serializable, even if {@code fromCollection} is.
   *
   * <p>When a live view is <i>not</i> needed, it may be faster to copy the transformed collection
   * and use the copy.
   *
   * <p>If the input {@code Collection} is known to be a {@code List}, consider {@link
   * Lists#transform}. If only an {@code Iterable} is available, use {@link Iterables#transform}.
   *
   * <p><b>{@code Stream} equivalent:</b> {@link java.util.stream.Stream#map Stream.map}.
   */
  public static <F, T> Collection<T> transform(
      Collection<F> fromCollection, Function<? super F, T> function) {
    return new TransformedCollection<>(fromCollection, function);
  }

  static class TransformedCollection<F, T> extends AbstractCollection<T> {
    final Collection<F> fromCollection;
    final Function<? super F, ? extends T> function;

    TransformedCollection(Collection<F> fromCollection, Function<? super F, ? extends T> function) {
      this.fromCollection = checkNotNull(fromCollection);
      this.function = checkNotNull(function);
    }

    @Override
    public void clear() {
      fromCollection.clear();
    }

    @Override
    public boolean isEmpty() {
      return fromCollection.isEmpty();
    }

    @Override
    public Iterator<T> iterator() {
      return Iterators.transform(fromCollection.iterator(), function);
    }

    @Override
    public Spliterator<T> spliterator() {
      return CollectSpliterators.map(fromCollection.spliterator(), function);
    }

    @Override
    public void forEach(Consumer<? super T> action) {
      checkNotNull(action);
      fromCollection.forEach((F f) -> action.accept(function.apply(f)));
    }

    @Override
    public boolean removeIf(java.util.function.Predicate<? super T> filter) {
      checkNotNull(filter);
      return fromCollection.removeIf(element -> filter.test(function.apply(element)));
    }

    @Override
    public int size() {
      return fromCollection.size();
    }
  }

  /**
   * Returns {@code true} if the collection {@code self} contains all of the elements in the
   * collection {@code c}.
   *
   * <p>This method iterates over the specified collection {@code c}, checking each element returned
   * by the iterator in turn to see if it is contained in the specified collection {@code self}. If
   * all elements are so contained, {@code true} is returned, otherwise {@code false}.
   *
   * @param self a collection which might contain all elements in {@code c}
   * @param c a collection whose elements might be contained by {@code self}
   */
  static boolean containsAllImpl(Collection<?> self, Collection<?> c) {
    for (Object o : c) {
      if (!self.contains(o)) {
        return false;
      }
    }
    return true;
  }

  /** An implementation of {@link Collection#toString()}. */
  static String toStringImpl(final Collection<?> collection) {
    StringBuilder sb = newStringBuilderForCollection(collection.size()).append('[');
    boolean first = true;
    for (Object o : collection) {
      if (!first) {
        sb.append(", ");
      }
      first = false;
      if (o == collection) {
        sb.append("(this Collection)");
      } else {
        sb.append(o);
      }
    }
    return sb.append(']').toString();
  }

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

