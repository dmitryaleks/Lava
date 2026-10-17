/*
 * Copyright (C) 2011 The Guava Authors
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

/**
 * This package contains caching utilities, ported from Guava 27.0.1's {@code cache} package for
 * single-threaded use.
 *
 * <p>The core interface used to represent caches is {@link com.simplelibs.lava.cache.Cache}.
 * In-memory caches can be configured and created using {@link
 * com.simplelibs.lava.cache.CacheBuilder}, with cache entries being loaded by {@link
 * com.simplelibs.lava.cache.CacheLoader}. Statistics about cache performance are exposed using {@link
 * com.simplelibs.lava.cache.CacheStats}.
 *
 * <p>See the Guava User Guide article on <a
 * href="https://github.com/google/guava/wiki/CachesExplained">caches</a>; the API is the same.
 *
 * <p><b>Single-threaded.</b> Caches, their {@code asMap()} views and {@code
 * AbstractCache.SimpleStatsCounter} are not thread-safe and must be confined to one thread.
 * Executors passed to {@link com.simplelibs.lava.cache.CacheLoader#asyncReloading} and {@link
 * com.simplelibs.lava.cache.RemovalListeners#asynchronous} must run their tasks on that thread
 * (a direct executor, or a queue the thread drains). Immutable values ({@code CacheStats}, {@code
 * CacheBuilderSpec}, {@code RemovalNotification}) are safe to share.
 *
 * <p><b>Differences from Guava</b>, all within the documented contracts:
 *
 * <ul>
 *   <li>Eviction is exact least-recently-used over the whole cache, as with Guava's {@code
 *       concurrencyLevel(1)}; {@code concurrencyLevel} is validated but has no effect.
 *   <li>Expired and garbage-collected entries are removed at the start of every operation instead
 *       of on writes and every 64th read, so {@code size()} never counts expired entries or
 *       entries whose references the collector has enqueued. (Until it is enqueued, a reference
 *       that the collector cleared is counted, as in Guava.) Removal notifications are delivered
 *       on the calling thread at the end of each operation and before a loader runs, as in Guava,
 *       and also before an {@code asMap().compute*} function runs.
 *   <li>A {@code get} of a key whose load is running on the call stack throws {@code
 *       IllegalStateException("Recursive load of: ...")}, as in Guava. Lava throws the same
 *       exception where Guava would block forever: an {@code asMap().compute*} of a key whose
 *       load, compute or refresh is running on the call stack, or a {@code get}, from a removal
 *       listener, of a key that is about to load. A read that finds an asynchronous refresh still pending
 *       and no live value loads synchronously instead of waiting.
 *   <li>Guava 27.0.1 leaves an entry stuck in a loading state when an {@code asMap().compute*}
 *       function throws, or when {@code compute} removes an existing entry by returning null (a
 *       later {@code get} then throws {@code InvalidCacheLoadException}), or when the weigher
 *       throws for a loaded value. Lava restores or removes the entry, and keeps its counts
 *       consistent when a compute function removes or replaces its own entry. When the function
 *       writes its own key and then returns the old value, Lava keeps the written value, which
 *       Guava drops without a notification.
 *   <li>Weighers must not call back into the cache; Guava has the same hazard but does not say so
 *       (see {@link com.simplelibs.lava.cache.Weigher}).
 *   <li>Iterators of {@code asMap()} views copy a snapshot of the table when they are created,
 *       so creating one costs O(n) even when only its first element is read.
 *   <li>Caches are not serializable (Guava serializes only their configuration).
 *   <li>Logger names start with {@code com.simplelibs.lava.cache}.
 * </ul>
 *
 * @author Charles Fry
 */
package com.simplelibs.lava.cache;

