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

import com.simplelibs.lava.checker.nullness.qual.Nullable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementations of {@code Futures.immediate*}. Every instance is complete on construction, so
 * none of the {@code get} methods ever block.
 */
abstract class ImmediateFuture<V> implements ListenableFuture<V> {
  private static final Logger log = Logger.getLogger(ImmediateFuture.class.getName());

  @Override
  public void addListener(Runnable listener, Executor executor) {
    checkNotNull(listener, "Runnable was null.");
    checkNotNull(executor, "Executor was null.");
    try {
      executor.execute(listener);
    } catch (RuntimeException e) {
      // ListenableFuture's contract is that it will not throw unchecked exceptions, so log the bad
      // runnable and/or executor and swallow it.
      log.log(
          Level.SEVERE,
          "RuntimeException while executing runnable " + listener + " with executor " + executor,
          e);
    }
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return false;
  }

  @Override
  public abstract V get() throws InterruptedException, ExecutionException;

  @Override
  public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException {
    checkNotNull(unit);
    return get();
  }

  @Override
  public boolean isCancelled() {
    return false;
  }

  @Override
  public boolean isDone() {
    return true;
  }

  static final class ImmediateSuccessfulFuture<V> extends ImmediateFuture<V> {
    static final ImmediateSuccessfulFuture<Object> NULL = new ImmediateSuccessfulFuture<>(null);

    private final @Nullable V value;

    ImmediateSuccessfulFuture(@Nullable V value) {
      this.value = value;
    }

    @Override
    public V get() {
      return value;
    }

    @Override
    public String toString() {
      // Behaviour analogous to Guava's AbstractFuture#toString().
      return super.toString() + "[status=SUCCESS, result=[" + value + "]]";
    }
  }

  static final class ImmediateFailedFuture<V> extends ImmediateFuture<V> {
    private final Throwable thrown;

    ImmediateFailedFuture(Throwable thrown) {
      this.thrown = thrown;
    }

    /** Like Guava's {@code AbstractFuture}, honours a pending interrupt before reporting. */
    @Override
    public V get() throws InterruptedException, ExecutionException {
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      throw new ExecutionException(thrown);
    }

    @Override
    public String toString() {
      return super.toString() + "[status=FAILURE, cause=[" + thrown + "]]";
    }
  }

  static final class ImmediateCancelledFuture<V> extends ImmediateFuture<V> {
    /** Like Guava's {@code AbstractFuture}, honours a pending interrupt before reporting. */
    @Override
    public V get() throws InterruptedException {
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      throw new CancellationException("Task was cancelled.");
    }

    @Override
    public boolean isCancelled() {
      return true;
    }

    @Override
    public String toString() {
      return super.toString() + "[status=CANCELLED]";
    }
  }
}
