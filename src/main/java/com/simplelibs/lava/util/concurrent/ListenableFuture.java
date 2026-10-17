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

package com.simplelibs.lava.util.concurrent;

import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

/**
 * A {@link Future} that accepts completion listeners. Each listener has an associated executor, and
 * it is invoked using this executor once the future's computation is {@linkplain Future#isDone()
 * complete}. If the computation has already completed when the listener is added, the listener will
 * execute immediately.
 *
 * <p>In Lava this interface exists so that code written against Guava's {@code CacheLoader.reload}
 * keeps compiling. Instances are usually obtained from {@link Futures#immediateFuture
 * Futures.immediate*} (for synchronous reloads) or {@link ListenableFutureTask} (for reloads that
 * are completed later by an executor that the owning thread drains).
 *
 * <p>Lava is single-threaded: futures must be completed, and listeners run, on the thread that owns
 * the objects listening to them.
 *
 * <p><b>Test doubles</b>: If you need a {@code ListenableFuture} for your test, try one of the
 * methods in the {@link Futures#immediateFuture Futures.immediate*} family or a {@link
 * ListenableFutureTask}. <b>Avoid</b> creating a mock or stub {@code Future}. Mock and stub
 * implementations are fragile because they assume that only certain methods will be called and
 * because they often implement subtleties of the API improperly.
 *
 * @author Sven Mawson
 * @author Nishant Thakkar
 * @since 1.0
 */
public interface ListenableFuture<V> extends Future<V> {
  /**
   * Registers a listener to be {@linkplain Executor#execute(Runnable) run} on the given executor.
   * The listener will run when the {@code Future}'s computation is {@linkplain Future#isDone()
   * complete} or, if the computation is already complete, immediately.
   *
   * <p>There is no guaranteed ordering of execution of listeners, but any listener added through
   * this method is guaranteed to be called once the computation is complete.
   *
   * <p>Exceptions thrown by a listener will be propagated up to the executor. Any exception thrown
   * during {@code Executor.execute} (e.g., a {@code RejectedExecutionException} or an exception
   * thrown by {@linkplain MoreExecutors#directExecutor direct execution}) will be caught and
   * logged.
   *
   * <p>Note: For fast, lightweight listeners, consider {@link MoreExecutors#directExecutor}.
   * Heavyweight {@code directExecutor} listeners delay the caller that completes the future and any
   * other listeners.
   *
   * @param listener the listener to run when the computation is complete
   * @param executor the executor to run the listener in
   * @throws RejectedExecutionException if we tried to execute the listener immediately but the
   *     executor rejected it.
   */
  void addListener(Runnable listener, Executor executor);
}
