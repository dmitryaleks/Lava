/*
 * Copyright (C) 2015 The Guava Authors
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

import static com.simplelibs.lava.base.Preconditions.checkArgument;
import static com.simplelibs.lava.base.Preconditions.checkNotNull;

import com.simplelibs.lava.checker.nullness.qual.Nullable;
import java.util.Comparator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/** Spliterator utilities for {@code common.collect} internals. */
final class CollectSpliterators {
  private CollectSpliterators() {}

  static <T> Spliterator<T> indexed(int size, int extraCharacteristics, IntFunction<T> function) {
    return indexed(size, extraCharacteristics, function, null);
  }

  static <T> Spliterator<T> indexed(
      int size,
      int extraCharacteristics,
      IntFunction<T> function,
      Comparator<? super T> comparator) {
    if (comparator != null) {
      checkArgument((extraCharacteristics & Spliterator.SORTED) != 0);
    }
    class WithCharacteristics implements Spliterator<T> {
      private final Spliterator.OfInt delegate;

      WithCharacteristics(Spliterator.OfInt delegate) {
        this.delegate = delegate;
      }

      @Override
      public boolean tryAdvance(Consumer<? super T> action) {
        return delegate.tryAdvance((IntConsumer) i -> action.accept(function.apply(i)));
      }

      @Override
      public void forEachRemaining(Consumer<? super T> action) {
        delegate.forEachRemaining((IntConsumer) i -> action.accept(function.apply(i)));
      }

      @Override
      public @Nullable Spliterator<T> trySplit() {
        Spliterator.OfInt split = delegate.trySplit();
        return (split == null) ? null : new WithCharacteristics(split);
      }

      @Override
      public long estimateSize() {
        return delegate.estimateSize();
      }

      @Override
      public int characteristics() {
        return Spliterator.ORDERED
            | Spliterator.SIZED
            | Spliterator.SUBSIZED
            | extraCharacteristics;
      }

      @Override
      public Comparator<? super T> getComparator() {
        if (hasCharacteristics(Spliterator.SORTED)) {
          return comparator;
        } else {
          throw new IllegalStateException();
        }
      }
    }
    return new WithCharacteristics(IntStream.range(0, size).spliterator());
  }

  /**
   * Returns a {@code Spliterator} over the elements of {@code fromSpliterator} mapped by {@code
   * function}.
   */
  static <F, T> Spliterator<T> map(
      Spliterator<F> fromSpliterator, Function<? super F, ? extends T> function) {
    checkNotNull(fromSpliterator);
    checkNotNull(function);
    return new Spliterator<T>() {

      @Override
      public boolean tryAdvance(Consumer<? super T> action) {
        return fromSpliterator.tryAdvance(
            fromElement -> action.accept(function.apply(fromElement)));
      }

      @Override
      public void forEachRemaining(Consumer<? super T> action) {
        fromSpliterator.forEachRemaining(fromElement -> action.accept(function.apply(fromElement)));
      }

      @Override
      public Spliterator<T> trySplit() {
        Spliterator<F> fromSplit = fromSpliterator.trySplit();
        return (fromSplit != null) ? map(fromSplit, function) : null;
      }

      @Override
      public long estimateSize() {
        return fromSpliterator.estimateSize();
      }

      @Override
      public int characteristics() {
        return fromSpliterator.characteristics()
            & ~(Spliterator.DISTINCT | Spliterator.NONNULL | Spliterator.SORTED);
      }
    };
  }

  /** Returns a {@code Spliterator} filtered by the specified predicate. */
  static <T> Spliterator<T> filter(Spliterator<T> fromSpliterator, Predicate<? super T> predicate) {
    checkNotNull(fromSpliterator);
    checkNotNull(predicate);
    class Splitr implements Spliterator<T>, Consumer<T> {
      T holder = null;

      @Override
      public void accept(T t) {
        this.holder = t;
      }

      @Override
      public boolean tryAdvance(Consumer<? super T> action) {
        while (fromSpliterator.tryAdvance(this)) {
          try {
            if (predicate.test(holder)) {
              action.accept(holder);
              return true;
            }
          } finally {
            holder = null;
          }
        }
        return false;
      }

      @Override
      public Spliterator<T> trySplit() {
        Spliterator<T> fromSplit = fromSpliterator.trySplit();
        return (fromSplit == null) ? null : filter(fromSplit, predicate);
      }

      @Override
      public long estimateSize() {
        return fromSpliterator.estimateSize() / 2;
      }

      @Override
      public Comparator<? super T> getComparator() {
        return fromSpliterator.getComparator();
      }

      @Override
      public int characteristics() {
        return fromSpliterator.characteristics()
            & (Spliterator.DISTINCT
                | Spliterator.NONNULL
                | Spliterator.ORDERED
                | Spliterator.SORTED);
      }
    }
    return new Splitr();
  }
}
