/*
 * Copyright (C) 2006 The Guava Authors
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

package com.simplelibs.lava.util.concurrent;

import static com.simplelibs.lava.base.Preconditions.checkNotNull;
import static com.simplelibs.lava.base.Preconditions.checkState;

import com.simplelibs.lava.checker.nullness.qual.Nullable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Static utility methods pertaining to the {@link Future} interface.
 *
 * <p>This is the subset of Guava 27.0.1's {@code Futures} needed to implement and consume {@code
 * CacheLoader.reload}: factories for already-completed futures and {@link #getDone}. The futures
 * returned by the {@code immediate*} factories are complete on construction, so they never block
 * and can be used freely on a single thread.
 *
 * @author Kevin Bourrillion
 * @author Nishant Thakkar
 * @author Sven Mawson
 * @since 1.0
 */
public final class Futures {
  private Futures() {}

  /**
   * Creates a {@code ListenableFuture} which has its value set immediately upon construction. The
   * getters just return the value. This {@code Future} can't be canceled or timed out and its
   * {@code isDone()} method always returns {@code true}.
   */
  public static <V> ListenableFuture<V> immediateFuture(@Nullable V value) {
    if (value == null) {
      // This cast is safe because null is assignable to V for all V (i.e. it is bivariant)
      @SuppressWarnings("unchecked")
      ListenableFuture<V> typedNull =
          (ListenableFuture<V>) ImmediateFuture.ImmediateSuccessfulFuture.NULL;
      return typedNull;
    }
    return new ImmediateFuture.ImmediateSuccessfulFuture<V>(value);
  }

  /**
   * Returns a {@code ListenableFuture} which has an exception set immediately upon construction.
   *
   * <p>The returned {@code Future} can't be cancelled, and its {@code isDone()} method always
   * returns {@code true}. Calling {@code get()} will immediately throw the provided {@code
   * Throwable} wrapped in an {@code ExecutionException}.
   */
  public static <V> ListenableFuture<V> immediateFailedFuture(Throwable throwable) {
    checkNotNull(throwable);
    return new ImmediateFuture.ImmediateFailedFuture<V>(throwable);
  }

  /**
   * Creates a {@code ListenableFuture} which is cancelled immediately upon construction, so that
   * {@code isCancelled()} always returns {@code true}.
   *
   * @since 14.0
   */
  public static <V> ListenableFuture<V> immediateCancelledFuture() {
    return new ImmediateFuture.ImmediateCancelledFuture<V>();
  }

  /**
   * Returns the result of the input {@code Future}, which must have already completed.
   *
   * <p>The benefits of this method are twofold. First, the name "getDone" suggests to readers that
   * the {@code Future} is already done. Second, if buggy code calls {@code getDone} on a {@code
   * Future} that is still pending, the program will throw instead of block.
   *
   * <p>If you are looking for a method to determine whether a given {@code Future} is done, use the
   * instance method {@link Future#isDone()}.
   *
   * @throws ExecutionException if the {@code Future} failed with an exception
   * @throws CancellationException if the {@code Future} was cancelled
   * @throws IllegalStateException if the {@code Future} is not done
   * @since 20.0
   */
  public static <V> V getDone(Future<V> future) throws ExecutionException {
    /*
     * We throw IllegalStateException, since the call could succeed later. Perhaps we "should" throw
     * IllegalArgumentException, since the call could succeed with a different argument. Those
     * exceptions' docs suggest that either is acceptable.
     */
    checkState(future.isDone(), "Future was expected to be done: %s", future);
    return getUninterruptibly(future);
  }

  /**
   * Invokes {@code future.get()} uninterruptibly, restoring the thread's interrupt status if an
   * interrupt was swallowed. This is Guava's {@code Uninterruptibles.getUninterruptibly}.
   */
  static <V> V getUninterruptibly(Future<V> future) throws ExecutionException {
    boolean interrupted = false;
    try {
      while (true) {
        try {
          return future.get();
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
