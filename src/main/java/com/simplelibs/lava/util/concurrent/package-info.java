/**
 * The small subset of Guava's concurrency utilities that the rest of Lava's API exposes: {@link
 * com.simplelibs.lava.util.concurrent.ListenableFuture} and helpers for implementing {@code
 * CacheLoader.reload}, plus the {@link com.simplelibs.lava.util.concurrent.UncheckedExecutionException}
 * and {@link com.simplelibs.lava.util.concurrent.ExecutionError} wrappers thrown by caches.
 *
 * <p>Lava is single-threaded: listener bookkeeping ({@link
 * com.simplelibs.lava.util.concurrent.ExecutionList}) is not synchronized, so futures must be
 * completed, and their listeners run, on the thread that owns the objects using them. In particular,
 * a {@link com.simplelibs.lava.util.concurrent.ListenableFutureTask} must be run by an executor that
 * executes on the owning thread (for example {@link
 * com.simplelibs.lava.util.concurrent.MoreExecutors#directExecutor()} or a queue the owning thread
 * drains); thread-pool executors, including {@code Executors.newSingleThreadExecutor()}, are not
 * supported.
 */
package com.simplelibs.lava.util.concurrent;
