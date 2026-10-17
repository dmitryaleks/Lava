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

import static com.simplelibs.lava.base.Preconditions.checkNotNull;

import com.simplelibs.lava.checker.nullness.qual.Nullable;
import com.simplelibs.lava.collect.Collections2.FilteredCollection;
import java.io.Serializable;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Package-private subset of Guava's {@code Sets} used by Lava's collections. */
final class Sets {
  private Sets() {}

  /**
   * {@link AbstractSet} substitute without the potentially-quadratic {@code removeAll}
   * implementation.
   */
  abstract static class ImprovedAbstractSet<E> extends AbstractSet<E> {
    @Override
    public boolean removeAll(Collection<?> c) {
      return removeAllImpl(this, c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
      return super.retainAll(checkNotNull(c));
    }
  }

  // HashSet

  /**
   * Creates a <i>mutable</i>, initially empty {@code HashSet} instance.
   *
   * <p><b>Note:</b> if mutability is not required, use {@link ImmutableSet#of()} instead. If {@code
   * E} is an {@link Enum} type, use {@code EnumSet#noneOf} instead. Otherwise, strongly consider
   * using a {@code LinkedHashSet} instead, at the cost of increased memory footprint, to get
   * deterministic iteration behavior.
   *
   * <p><b>Note for Java 7 and later:</b> this method is now unnecessary and should be treated as
   * deprecated. Instead, use the {@code HashSet} constructor directly, taking advantage of the new
   * <a href="http://goo.gl/iz2Wi">"diamond" syntax</a>.
   */
  public static <E> HashSet<E> newHashSet() {
    return new HashSet<E>();
  }

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
   * Returns the elements of {@code unfiltered} that satisfy a predicate. The returned set is a live
   * view of {@code unfiltered}; changes to one affect the other.
   *
   * <p>The resulting set's iterator does not support {@code remove()}, but all other set methods
   * are supported. When given an element that doesn't satisfy the predicate, the set's {@code
   * add()} and {@code addAll()} methods throw an {@link IllegalArgumentException}. When methods
   * such as {@code removeAll()} and {@code clear()} are called on the filtered set, only elements
   * that satisfy the filter will be removed from the underlying set.
   *
   * <p>The returned set isn't threadsafe or serializable, even if {@code unfiltered} is.
   *
   * <p>Many of the filtered set's methods, such as {@code size()}, iterate across every element in
   * the underlying set and determine which elements satisfy the filter. When a live view is
   * <i>not</i> needed, it may be faster to copy {@code Iterables.filter(unfiltered, predicate)} and
   * use the copy.
   *
   * <p><b>Warning:</b> {@code predicate} must be <i>consistent with equals</i>, as documented at
   * {@code Predicate#apply}. Do not provide a predicate such as {@code
   * CollectPredicates.instanceOf(ArrayList.class)}, which is inconsistent with equals. (See {@code
   * Iterables#filter(Iterable, Class)} for related functionality.)
   *
   * <p><b>Java 8 users:</b> many use cases for this method are better addressed by {@link
   * java.util.stream.Stream#filter}. This method is not being deprecated, but we gently encourage
   * you to migrate to streams.
   */
  public static <E> Set<E> filter(Set<E> unfiltered, Predicate<? super E> predicate) {
    if (unfiltered instanceof SortedSet) {
      return filter((SortedSet<E>) unfiltered, predicate);
    }
    if (unfiltered instanceof FilteredSet) {
      // Support clear(), removeAll(), and retainAll() when filtering a filtered
      // collection.
      FilteredSet<E> filtered = (FilteredSet<E>) unfiltered;
      Predicate<E> combinedPredicate = CollectPredicates.<E>and(filtered.predicate, predicate);
      return new FilteredSet<E>((Set<E>) filtered.unfiltered, combinedPredicate);
    }

    return new FilteredSet<E>(checkNotNull(unfiltered), checkNotNull(predicate));
  }

  /**
   * Returns the elements of a {@code SortedSet}, {@code unfiltered}, that satisfy a predicate. The
   * returned set is a live view of {@code unfiltered}; changes to one affect the other.
   *
   * <p>The resulting set's iterator does not support {@code remove()}, but all other set methods
   * are supported. When given an element that doesn't satisfy the predicate, the set's {@code
   * add()} and {@code addAll()} methods throw an {@link IllegalArgumentException}. When methods
   * such as {@code removeAll()} and {@code clear()} are called on the filtered set, only elements
   * that satisfy the filter will be removed from the underlying set.
   *
   * <p>The returned set isn't threadsafe or serializable, even if {@code unfiltered} is.
   *
   * <p>Many of the filtered set's methods, such as {@code size()}, iterate across every element in
   * the underlying set and determine which elements satisfy the filter. When a live view is
   * <i>not</i> needed, it may be faster to copy {@code Iterables.filter(unfiltered, predicate)} and
   * use the copy.
   *
   * <p><b>Warning:</b> {@code predicate} must be <i>consistent with equals</i>, as documented at
   * {@code Predicate#apply}. Do not provide a predicate such as {@code
   * CollectPredicates.instanceOf(ArrayList.class)}, which is inconsistent with equals. (See {@code
   * Iterables#filter(Iterable, Class)} for related functionality.)
   *
   * @since 11.0
   */
  public static <E> SortedSet<E> filter(SortedSet<E> unfiltered, Predicate<? super E> predicate) {
    if (unfiltered instanceof FilteredSet) {
      // Support clear(), removeAll(), and retainAll() when filtering a filtered
      // collection.
      FilteredSet<E> filtered = (FilteredSet<E>) unfiltered;
      Predicate<E> combinedPredicate = CollectPredicates.<E>and(filtered.predicate, predicate);
      return new FilteredSortedSet<E>((SortedSet<E>) filtered.unfiltered, combinedPredicate);
    }

    return new FilteredSortedSet<E>(checkNotNull(unfiltered), checkNotNull(predicate));
  }

  /**
   * Returns the elements of a {@code NavigableSet}, {@code unfiltered}, that satisfy a predicate.
   * The returned set is a live view of {@code unfiltered}; changes to one affect the other.
   *
   * <p>The resulting set's iterator does not support {@code remove()}, but all other set methods
   * are supported. When given an element that doesn't satisfy the predicate, the set's {@code
   * add()} and {@code addAll()} methods throw an {@link IllegalArgumentException}. When methods
   * such as {@code removeAll()} and {@code clear()} are called on the filtered set, only elements
   * that satisfy the filter will be removed from the underlying set.
   *
   * <p>The returned set isn't threadsafe or serializable, even if {@code unfiltered} is.
   *
   * <p>Many of the filtered set's methods, such as {@code size()}, iterate across every element in
   * the underlying set and determine which elements satisfy the filter. When a live view is
   * <i>not</i> needed, it may be faster to copy {@code Iterables.filter(unfiltered, predicate)} and
   * use the copy.
   *
   * <p><b>Warning:</b> {@code predicate} must be <i>consistent with equals</i>, as documented at
   * {@code Predicate#apply}. Do not provide a predicate such as {@code
   * CollectPredicates.instanceOf(ArrayList.class)}, which is inconsistent with equals. (See {@code
   * Iterables#filter(Iterable, Class)} for related functionality.)
   *
   * @since 14.0
   */
  @SuppressWarnings("unchecked")
  public static <E> NavigableSet<E> filter(
      NavigableSet<E> unfiltered, Predicate<? super E> predicate) {
    if (unfiltered instanceof FilteredSet) {
      // Support clear(), removeAll(), and retainAll() when filtering a filtered
      // collection.
      FilteredSet<E> filtered = (FilteredSet<E>) unfiltered;
      Predicate<E> combinedPredicate = CollectPredicates.<E>and(filtered.predicate, predicate);
      return new FilteredNavigableSet<E>((NavigableSet<E>) filtered.unfiltered, combinedPredicate);
    }

    return new FilteredNavigableSet<E>(checkNotNull(unfiltered), checkNotNull(predicate));
  }

  private static class FilteredSet<E> extends FilteredCollection<E> implements Set<E> {
    FilteredSet(Set<E> unfiltered, Predicate<? super E> predicate) {
      super(unfiltered, predicate);
    }

    @Override
    public boolean equals(@Nullable Object object) {
      return equalsImpl(this, object);
    }

    @Override
    public int hashCode() {
      return hashCodeImpl(this);
    }
  }

  private static class FilteredSortedSet<E> extends FilteredSet<E> implements SortedSet<E> {

    FilteredSortedSet(SortedSet<E> unfiltered, Predicate<? super E> predicate) {
      super(unfiltered, predicate);
    }

    @Override
    public Comparator<? super E> comparator() {
      return ((SortedSet<E>) unfiltered).comparator();
    }

    @Override
    public SortedSet<E> subSet(E fromElement, E toElement) {
      return new FilteredSortedSet<E>(
          ((SortedSet<E>) unfiltered).subSet(fromElement, toElement), predicate);
    }

    @Override
    public SortedSet<E> headSet(E toElement) {
      return new FilteredSortedSet<E>(((SortedSet<E>) unfiltered).headSet(toElement), predicate);
    }

    @Override
    public SortedSet<E> tailSet(E fromElement) {
      return new FilteredSortedSet<E>(((SortedSet<E>) unfiltered).tailSet(fromElement), predicate);
    }

    @Override
    public E first() {
      return Iterators.find(unfiltered.iterator(), predicate);
    }

    @Override
    public E last() {
      SortedSet<E> sortedUnfiltered = (SortedSet<E>) unfiltered;
      while (true) {
        E element = sortedUnfiltered.last();
        if (predicate.test(element)) {
          return element;
        }
        sortedUnfiltered = sortedUnfiltered.headSet(element);
      }
    }
  }

  private static class FilteredNavigableSet<E> extends FilteredSortedSet<E>
      implements NavigableSet<E> {
    FilteredNavigableSet(NavigableSet<E> unfiltered, Predicate<? super E> predicate) {
      super(unfiltered, predicate);
    }

    NavigableSet<E> unfiltered() {
      return (NavigableSet<E>) unfiltered;
    }

    @Override
    public @Nullable E lower(E e) {
      return Iterators.find(unfiltered().headSet(e, false).descendingIterator(), predicate, null);
    }

    @Override
    public @Nullable E floor(E e) {
      return Iterators.find(unfiltered().headSet(e, true).descendingIterator(), predicate, null);
    }

    @Override
    public E ceiling(E e) {
      return Iterables.find(unfiltered().tailSet(e, true), predicate, null);
    }

    @Override
    public E higher(E e) {
      return Iterables.find(unfiltered().tailSet(e, false), predicate, null);
    }

    @Override
    public E pollFirst() {
      return Iterables.removeFirstMatching(unfiltered(), predicate);
    }

    @Override
    public E pollLast() {
      return Iterables.removeFirstMatching(unfiltered().descendingSet(), predicate);
    }

    @Override
    public NavigableSet<E> descendingSet() {
      return Sets.filter(unfiltered().descendingSet(), predicate);
    }

    @Override
    public Iterator<E> descendingIterator() {
      return Iterators.filter(unfiltered().descendingIterator(), predicate);
    }

    @Override
    public E last() {
      return Iterators.find(unfiltered().descendingIterator(), predicate);
    }

    @Override
    public NavigableSet<E> subSet(
        E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
      return filter(
          unfiltered().subSet(fromElement, fromInclusive, toElement, toInclusive), predicate);
    }

    @Override
    public NavigableSet<E> headSet(E toElement, boolean inclusive) {
      return filter(unfiltered().headSet(toElement, inclusive), predicate);
    }

    @Override
    public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
      return filter(unfiltered().tailSet(fromElement, inclusive), predicate);
    }
  }

  /** An implementation for {@link Set#hashCode()}. */
  static int hashCodeImpl(Set<?> s) {
    int hashCode = 0;
    for (Object o : s) {
      hashCode += o != null ? o.hashCode() : 0;

      hashCode = ~~hashCode;
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

  /**
   * Returns an unmodifiable view of the specified navigable set. This method allows modules to
   * provide users with "read-only" access to internal navigable sets. Query operations on the
   * returned set "read through" to the specified set, and attempts to modify the returned set,
   * whether direct or via its collection views, result in an {@code UnsupportedOperationException}.
   *
   * <p>The returned navigable set will be serializable if the specified navigable set is
   * serializable.
   *
   * @param set the navigable set for which an unmodifiable view is to be returned
   * @return an unmodifiable view of the specified navigable set
   * @since 12.0
   */
  public static <E> NavigableSet<E> unmodifiableNavigableSet(NavigableSet<E> set) {
    if (set instanceof ImmutableCollection || set instanceof UnmodifiableNavigableSet) {
      return set;
    }
    return new UnmodifiableNavigableSet<E>(set);
  }

  static final class UnmodifiableNavigableSet<E> extends ForwardingSortedSet<E>
      implements NavigableSet<E>, Serializable {
    private final NavigableSet<E> delegate;
    private final SortedSet<E> unmodifiableDelegate;

    UnmodifiableNavigableSet(NavigableSet<E> delegate) {
      this.delegate = checkNotNull(delegate);
      this.unmodifiableDelegate = Collections.unmodifiableSortedSet(delegate);
    }

    @Override
    protected SortedSet<E> delegate() {
      return unmodifiableDelegate;
    }

    // default methods not forwarded by ForwardingSortedSet

    @Override
    public boolean removeIf(java.util.function.Predicate<? super E> filter) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Stream<E> stream() {
      return delegate.stream();
    }

    @Override
    public Stream<E> parallelStream() {
      return delegate.parallelStream();
    }

    @Override
    public void forEach(Consumer<? super E> action) {
      delegate.forEach(action);
    }

    @Override
    public E lower(E e) {
      return delegate.lower(e);
    }

    @Override
    public E floor(E e) {
      return delegate.floor(e);
    }

    @Override
    public E ceiling(E e) {
      return delegate.ceiling(e);
    }

    @Override
    public E higher(E e) {
      return delegate.higher(e);
    }

    @Override
    public E pollFirst() {
      throw new UnsupportedOperationException();
    }

    @Override
    public E pollLast() {
      throw new UnsupportedOperationException();
    }

    private transient UnmodifiableNavigableSet<E> descendingSet;

    @Override
    public NavigableSet<E> descendingSet() {
      UnmodifiableNavigableSet<E> result = descendingSet;
      if (result == null) {
        result = descendingSet = new UnmodifiableNavigableSet<E>(delegate.descendingSet());
        result.descendingSet = this;
      }
      return result;
    }

    @Override
    public Iterator<E> descendingIterator() {
      return Iterators.unmodifiableIterator(delegate.descendingIterator());
    }

    @Override
    public NavigableSet<E> subSet(
        E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
      return unmodifiableNavigableSet(
          delegate.subSet(fromElement, fromInclusive, toElement, toInclusive));
    }

    @Override
    public NavigableSet<E> headSet(E toElement, boolean inclusive) {
      return unmodifiableNavigableSet(delegate.headSet(toElement, inclusive));
    }

    @Override
    public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
      return unmodifiableNavigableSet(delegate.tailSet(fromElement, inclusive));
    }

    private static final long serialVersionUID = 0;
  }

  /** Remove each element in an iterable from a set. */
  static boolean removeAllImpl(Set<?> set, Iterator<?> iterator) {
    boolean changed = false;
    while (iterator.hasNext()) {
      changed |= set.remove(iterator.next());
    }
    return changed;
  }

  static boolean removeAllImpl(Set<?> set, Collection<?> collection) {
    checkNotNull(collection);
    /*
     * AbstractSet.removeAll(List) has quadratic behavior if the list size
     * is just more than the set's size.  We augment the test by
     * assuming that sets have fast contains() performance, and other
     * collections don't.  See
     * http://code.google.com/p/guava-libraries/issues/detail?id=1013
     */
    if (collection instanceof Set && collection.size() > set.size()) {
      return Iterators.removeAll(set.iterator(), collection);
    } else {
      return removeAllImpl(set, collection.iterator());
    }
  }
}

