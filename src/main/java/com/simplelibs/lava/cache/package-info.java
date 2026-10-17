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
 *       of on writes and every 64th read, so {@code size()} never counts them. Removal
 *       notifications are delivered on the calling thread before a loader or compute function
 *       runs and at the end of each operation.
 *   <li>Where Guava's implementation would block forever on the thread's own in-flight load (a
 *       {@code get} or {@code asMap().compute} of a key whose load or compute is running on the
 *       call stack), Lava throws {@code IllegalStateException("Recursive load of: ...")}. A read
 *       that finds an asynchronous refresh still pending and no live value loads synchronously
 *       instead of waiting.
 *   <li>Guava 27.0.1 leaves an entry stuck in a loading state when an {@code asMap().compute*}
 *       function throws, or when {@code compute} removes an existing entry by returning null (a
 *       later {@code get} then throws {@code InvalidCacheLoadException}). Lava restores or removes
 *       the entry.
 *   <li>Caches are not serializable (Guava serializes only their configuration).
 *   <li>Logger names start with {@code com.simplelibs.lava.cache}.
 * </ul>
 *
 * @author Charles Fry
 */
package com.simplelibs.lava.cache;

