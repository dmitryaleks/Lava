/**
 * The small subset of Guava's concurrency utilities that the rest of Lava's API exposes: {@link
 * com.simplelibs.lava.util.concurrent.ListenableFuture} and helpers for implementing {@code
 * CacheLoader.reload}, plus the {@link com.simplelibs.lava.util.concurrent.UncheckedExecutionException}
 * and {@link com.simplelibs.lava.util.concurrent.ExecutionError} wrappers thrown by caches.
 *
 * <p>Lava is single-threaded: none of these classes synchronize. Futures must be completed, and
 * their listeners run, on the thread that owns the objects using them.
 */
package com.simplelibs.lava.util.concurrent;
