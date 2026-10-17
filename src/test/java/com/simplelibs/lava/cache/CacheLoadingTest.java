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

package com.simplelibs.lava.cache;

import static com.simplelibs.lava.cache.TestingCacheLoaders.bulkLoader;
import static com.simplelibs.lava.cache.TestingCacheLoaders.constantLoader;
import static com.simplelibs.lava.cache.TestingCacheLoaders.errorLoader;
import static com.simplelibs.lava.cache.TestingCacheLoaders.exceptionLoader;
import static com.simplelibs.lava.cache.TestingCacheLoaders.identityLoader;
import static com.simplelibs.lava.cache.TestingRemovalListeners.countingRemovalListener;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.simplelibs.lava.cache.CacheLoader.InvalidCacheLoadException;
import com.simplelibs.lava.cache.TestingCacheLoaders.CountingLoader;
import com.simplelibs.lava.cache.TestingCacheLoaders.IdentityLoader;
import com.simplelibs.lava.cache.TestingRemovalListeners.CountingRemovalListener;
import com.simplelibs.lava.collect.ImmutableList;
import com.simplelibs.lava.collect.ImmutableMap;
import com.simplelibs.lava.collect.Lists;
import com.simplelibs.lava.collect.Maps;
import com.simplelibs.lava.testing.FakeTicker;
import com.simplelibs.lava.testing.LavaTestCase;
import com.simplelibs.lava.testing.TestLogHandler;
import com.simplelibs.lava.util.concurrent.ExecutionError;
import com.simplelibs.lava.util.concurrent.Futures;
import com.simplelibs.lava.util.concurrent.ListenableFuture;
import com.simplelibs.lava.util.concurrent.UncheckedExecutionException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Tests relating to cache loading: exceptions during loading, etc.
 *
 * <p>Lava: only the single-threaded tests; see the note before {@code testAsMapDuringLoading}.
 *
 * @author mike nonemacher
 */
public class CacheLoadingTest extends LavaTestCase {
  TestLogHandler logHandler;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    logHandler = new TestLogHandler();
    LocalCache.logger.addHandler(logHandler);
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    // TODO(cpovirk): run tests in other thread instead of messing with main thread interrupt status
    Thread.interrupted();
    LocalCache.logger.removeHandler(logHandler);
  }

  private Throwable popLoggedThrowable() {
    List<LogRecord> logRecords = logHandler.getStoredLogRecords();
    assertEquals(1, logRecords.size());
    LogRecord logRecord = logRecords.get(0);
    logHandler.clear();
    return logRecord.getThrown();
  }

  private void checkNothingLogged() {
    assertTrue(logHandler.getStoredLogRecords().isEmpty());
  }

  private void checkLoggedCause(Throwable t) {
    assertSame(t, popLoggedThrowable().getCause());
  }

  private void checkLoggedInvalidLoad() {
    assertThat(popLoggedThrowable()).isInstanceOf(InvalidCacheLoadException.class);
  }

  @Test
  public void testLoad() throws ExecutionException {
    LoadingCache<Object, Object> cache =
        CacheBuilder.newBuilder().recordStats().build(identityLoader());
    CacheStats stats = cache.stats();
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    Object key = new Object();
    assertSame(key, cache.get(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    key = new Object();
    assertSame(key, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(2, stats.missCount());
    assertEquals(2, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    key = new Object();
    cache.refresh(key);
    checkNothingLogged();
    stats = cache.stats();
    assertEquals(2, stats.missCount());
    assertEquals(3, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertSame(key, cache.get(key));
    stats = cache.stats();
    assertEquals(2, stats.missCount());
    assertEquals(3, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(1, stats.hitCount());

    Object value = new Object();
    // callable is not called
    assertSame(key, cache.get(key, throwing(new Exception())));
    stats = cache.stats();
    assertEquals(2, stats.missCount());
    assertEquals(3, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(2, stats.hitCount());

    key = new Object();
    assertSame(value, cache.get(key, returning(value)));
    stats = cache.stats();
    assertEquals(3, stats.missCount());
    assertEquals(4, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(2, stats.hitCount());
  }

  @Test
  public void testReload() throws ExecutionException {
    final Object one = new Object();
    final Object two = new Object();
    CacheLoader<Object, Object> loader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object key) {
            return one;
          }

          @Override
          public ListenableFuture<Object> reload(Object key, Object oldValue) {
            return Futures.immediateFuture(two);
          }
        };

    LoadingCache<Object, Object> cache = CacheBuilder.newBuilder().recordStats().build(loader);
    Object key = new Object();
    CacheStats stats = cache.stats();
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    cache.refresh(key);
    checkNothingLogged();
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(2, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertSame(two, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(2, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(1, stats.hitCount());
  }

  @Test
  public void testRefresh() {
    final Object one = new Object();
    final Object two = new Object();
    FakeTicker ticker = new FakeTicker();
    CacheLoader<Object, Object> loader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object key) {
            return one;
          }

          @Override
          public ListenableFuture<Object> reload(Object key, Object oldValue) {
            return Futures.immediateFuture(two);
          }
        };

    LoadingCache<Object, Object> cache =
        CacheBuilder.newBuilder()
            .recordStats()
            .ticker(ticker)
            .refreshAfterWrite(1, MILLISECONDS)
            .build(loader);
    Object key = new Object();
    CacheStats stats = cache.stats();
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    ticker.advance(1, MILLISECONDS);
    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(1, stats.hitCount());

    ticker.advance(1, MILLISECONDS);
    assertSame(two, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(2, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(2, stats.hitCount());

    ticker.advance(1, MILLISECONDS);
    assertSame(two, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(2, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(3, stats.hitCount());
  }

  @Test
  public void testRefresh_getIfPresent() {
    final Object one = new Object();
    final Object two = new Object();
    FakeTicker ticker = new FakeTicker();
    CacheLoader<Object, Object> loader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object key) {
            return one;
          }

          @Override
          public ListenableFuture<Object> reload(Object key, Object oldValue) {
            return Futures.immediateFuture(two);
          }
        };

    LoadingCache<Object, Object> cache =
        CacheBuilder.newBuilder()
            .recordStats()
            .ticker(ticker)
            .refreshAfterWrite(1, MILLISECONDS)
            .build(loader);
    Object key = new Object();
    CacheStats stats = cache.stats();
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    ticker.advance(1, MILLISECONDS);
    assertSame(one, cache.getIfPresent(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(1, stats.hitCount());

    ticker.advance(1, MILLISECONDS);
    assertSame(two, cache.getIfPresent(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(2, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(2, stats.hitCount());

    ticker.advance(1, MILLISECONDS);
    assertSame(two, cache.getIfPresent(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(2, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(3, stats.hitCount());
  }

  @Test
  public void testBulkLoad_default() throws ExecutionException {
    LoadingCache<Integer, Integer> cache =
        CacheBuilder.newBuilder()
            .recordStats()
            .build(TestingCacheLoaders.<Integer>identityLoader());
    CacheStats stats = cache.stats();
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertEquals(ImmutableMap.of(), cache.getAll(ImmutableList.<Integer>of()));
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertEquals(ImmutableMap.of(1, 1), cache.getAll(asList(1)));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertEquals(ImmutableMap.of(1, 1, 2, 2, 3, 3, 4, 4), cache.getAll(asList(1, 2, 3, 4)));
    stats = cache.stats();
    assertEquals(4, stats.missCount());
    assertEquals(4, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(1, stats.hitCount());

    assertEquals(ImmutableMap.of(2, 2, 3, 3), cache.getAll(asList(2, 3)));
    stats = cache.stats();
    assertEquals(4, stats.missCount());
    assertEquals(4, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(3, stats.hitCount());

    // duplicate keys are ignored, and don't impact stats
    assertEquals(ImmutableMap.of(4, 4, 5, 5), cache.getAll(asList(4, 5)));
    stats = cache.stats();
    assertEquals(5, stats.missCount());
    assertEquals(5, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(4, stats.hitCount());
  }

  @Test
  public void testBulkLoad_loadAll() throws ExecutionException {
    IdentityLoader<Integer> backingLoader = identityLoader();
    CacheLoader<Integer, Integer> loader = bulkLoader(backingLoader);
    LoadingCache<Integer, Integer> cache = CacheBuilder.newBuilder().recordStats().build(loader);
    CacheStats stats = cache.stats();
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertEquals(ImmutableMap.of(), cache.getAll(ImmutableList.<Integer>of()));
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertEquals(ImmutableMap.of(1, 1), cache.getAll(asList(1)));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertEquals(ImmutableMap.of(1, 1, 2, 2, 3, 3, 4, 4), cache.getAll(asList(1, 2, 3, 4)));
    stats = cache.stats();
    assertEquals(4, stats.missCount());
    assertEquals(2, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(1, stats.hitCount());

    assertEquals(ImmutableMap.of(2, 2, 3, 3), cache.getAll(asList(2, 3)));
    stats = cache.stats();
    assertEquals(4, stats.missCount());
    assertEquals(2, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(3, stats.hitCount());

    // duplicate keys are ignored, and don't impact stats
    assertEquals(ImmutableMap.of(4, 4, 5, 5), cache.getAll(asList(4, 5)));
    stats = cache.stats();
    assertEquals(5, stats.missCount());
    assertEquals(3, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(4, stats.hitCount());
  }

  @Test
  public void testBulkLoad_extra() throws ExecutionException {
    CacheLoader<Object, Object> loader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object key) throws Exception {
            return new Object();
          }

          @Override
          public Map<Object, Object> loadAll(Iterable<?> keys) throws Exception {
            Map<Object, Object> result = Maps.newHashMap();
            for (Object key : keys) {
              Object value = new Object();
              result.put(key, value);
              // add extra entries
              result.put(value, key);
            }
            return result;
          }
        };
    LoadingCache<Object, Object> cache = CacheBuilder.newBuilder().build(loader);

    Object[] lookupKeys = new Object[] {new Object(), new Object(), new Object()};
    Map<Object, Object> result = cache.getAll(asList(lookupKeys));
    assertThat(result.keySet()).containsExactlyInAnyOrderElementsOf(asList(lookupKeys));
    for (Entry<Object, Object> entry : result.entrySet()) {
      Object key = entry.getKey();
      Object value = entry.getValue();
      assertSame(value, result.get(key));
      assertNull(result.get(value));
      assertSame(value, cache.asMap().get(key));
      assertSame(key, cache.asMap().get(value));
    }
  }

  @Test
  public void testBulkLoad_clobber() throws ExecutionException {
    final Object extraKey = new Object();
    final Object extraValue = new Object();
    CacheLoader<Object, Object> loader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object key) throws Exception {
            throw new AssertionError();
          }

          @Override
          public Map<Object, Object> loadAll(Iterable<?> keys) throws Exception {
            Map<Object, Object> result = Maps.newHashMap();
            for (Object key : keys) {
              Object value = new Object();
              result.put(key, value);
            }
            result.put(extraKey, extraValue);
            return result;
          }
        };
    LoadingCache<Object, Object> cache = CacheBuilder.newBuilder().build(loader);
    cache.asMap().put(extraKey, extraKey);
    assertSame(extraKey, cache.asMap().get(extraKey));

    Object[] lookupKeys = new Object[] {new Object(), new Object(), new Object()};
    Map<Object, Object> result = cache.getAll(asList(lookupKeys));
    assertThat(result.keySet()).containsExactlyInAnyOrderElementsOf(asList(lookupKeys));
    for (Entry<Object, Object> entry : result.entrySet()) {
      Object key = entry.getKey();
      Object value = entry.getValue();
      assertSame(value, result.get(key));
      assertSame(value, cache.asMap().get(key));
    }
    assertNull(result.get(extraKey));
    assertSame(extraValue, cache.asMap().get(extraKey));
  }

  @Test
  public void testBulkLoad_clobberNullValue() throws ExecutionException {
    final Object extraKey = new Object();
    final Object extraValue = new Object();
    CacheLoader<Object, Object> loader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object key) throws Exception {
            throw new AssertionError();
          }

          @Override
          public Map<Object, Object> loadAll(Iterable<?> keys) throws Exception {
            Map<Object, Object> result = Maps.newHashMap();
            for (Object key : keys) {
              Object value = new Object();
              result.put(key, value);
            }
            result.put(extraKey, extraValue);
            result.put(extraValue, null);
            return result;
          }
        };
    LoadingCache<Object, Object> cache = CacheBuilder.newBuilder().build(loader);
    cache.asMap().put(extraKey, extraKey);
    assertSame(extraKey, cache.asMap().get(extraKey));

    Object[] lookupKeys = new Object[] {new Object(), new Object(), new Object()};
    try {
      cache.getAll(asList(lookupKeys));
      fail();
    } catch (InvalidCacheLoadException expected) {
    }

    for (Object key : lookupKeys) {
      assertTrue(cache.asMap().containsKey(key));
    }
    assertSame(extraValue, cache.asMap().get(extraKey));
    assertFalse(cache.asMap().containsKey(extraValue));
  }

  @Test
  public void testBulkLoad_clobberNullKey() throws ExecutionException {
    final Object extraKey = new Object();
    final Object extraValue = new Object();
    CacheLoader<Object, Object> loader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object key) throws Exception {
            throw new AssertionError();
          }

          @Override
          public Map<Object, Object> loadAll(Iterable<?> keys) throws Exception {
            Map<Object, Object> result = Maps.newHashMap();
            for (Object key : keys) {
              Object value = new Object();
              result.put(key, value);
            }
            result.put(extraKey, extraValue);
            result.put(null, extraKey);
            return result;
          }
        };
    LoadingCache<Object, Object> cache = CacheBuilder.newBuilder().build(loader);
    cache.asMap().put(extraKey, extraKey);
    assertSame(extraKey, cache.asMap().get(extraKey));

    Object[] lookupKeys = new Object[] {new Object(), new Object(), new Object()};
    try {
      cache.getAll(asList(lookupKeys));
      fail();
    } catch (InvalidCacheLoadException expected) {
    }

    for (Object key : lookupKeys) {
      assertTrue(cache.asMap().containsKey(key));
    }
    assertSame(extraValue, cache.asMap().get(extraKey));
    assertFalse(cache.asMap().containsValue(extraKey));
  }

  @Test
  public void testBulkLoad_partial() throws ExecutionException {
    final Object extraKey = new Object();
    final Object extraValue = new Object();
    CacheLoader<Object, Object> loader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object key) throws Exception {
            throw new AssertionError();
          }

          @Override
          public Map<Object, Object> loadAll(Iterable<?> keys) throws Exception {
            Map<Object, Object> result = Maps.newHashMap();
            // ignore request keys
            result.put(extraKey, extraValue);
            return result;
          }
        };
    LoadingCache<Object, Object> cache = CacheBuilder.newBuilder().build(loader);

    Object[] lookupKeys = new Object[] {new Object(), new Object(), new Object()};
    try {
      cache.getAll(asList(lookupKeys));
      fail();
    } catch (InvalidCacheLoadException expected) {
    }
    assertSame(extraValue, cache.asMap().get(extraKey));
  }

  @Test
  public void testLoadNull() throws ExecutionException {
    LoadingCache<Object, Object> cache =
        CacheBuilder.newBuilder().recordStats().build(constantLoader(null));
    CacheStats stats = cache.stats();
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    try {
      cache.get(new Object());
      fail();
    } catch (InvalidCacheLoadException expected) {
    }
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    try {
      cache.getUnchecked(new Object());
      fail();
    } catch (InvalidCacheLoadException expected) {
    }
    stats = cache.stats();
    assertEquals(2, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(2, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    cache.refresh(new Object());
    checkLoggedInvalidLoad();
    stats = cache.stats();
    assertEquals(2, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(3, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    try {
      cache.get(new Object(), returning(null));
      fail();
    } catch (InvalidCacheLoadException expected) {
    }
    stats = cache.stats();
    assertEquals(3, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(4, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    try {
      cache.getAll(asList(new Object()));
      fail();
    } catch (InvalidCacheLoadException expected) {
    }
    stats = cache.stats();
    assertEquals(4, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(5, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());
  }

  @Test
  public void testReloadNull() throws ExecutionException {
    final Object one = new Object();
    CacheLoader<Object, Object> loader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object key) {
            return one;
          }

          @Override
          public ListenableFuture<Object> reload(Object key, Object oldValue) {
            return null;
          }
        };

    LoadingCache<Object, Object> cache = CacheBuilder.newBuilder().recordStats().build(loader);
    Object key = new Object();
    CacheStats stats = cache.stats();
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    cache.refresh(key);
    checkLoggedInvalidLoad();
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(1, stats.hitCount());
  }

  @Test
  public void testReloadNullFuture() throws ExecutionException {
    final Object one = new Object();
    CacheLoader<Object, Object> loader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object key) {
            return one;
          }

          @Override
          public ListenableFuture<Object> reload(Object key, Object oldValue) {
            return Futures.immediateFuture(null);
          }
        };

    LoadingCache<Object, Object> cache = CacheBuilder.newBuilder().recordStats().build(loader);
    Object key = new Object();
    CacheStats stats = cache.stats();
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    cache.refresh(key);
    checkLoggedInvalidLoad();
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(1, stats.hitCount());
  }

  @Test
  public void testRefreshNull() {
    final Object one = new Object();
    FakeTicker ticker = new FakeTicker();
    CacheLoader<Object, Object> loader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object key) {
            return one;
          }

          @Override
          public ListenableFuture<Object> reload(Object key, Object oldValue) {
            return Futures.immediateFuture(null);
          }
        };

    LoadingCache<Object, Object> cache =
        CacheBuilder.newBuilder()
            .recordStats()
            .ticker(ticker)
            .refreshAfterWrite(1, MILLISECONDS)
            .build(loader);
    Object key = new Object();
    CacheStats stats = cache.stats();
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    ticker.advance(1, MILLISECONDS);
    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(1, stats.hitCount());

    ticker.advance(1, MILLISECONDS);
    assertSame(one, cache.getUnchecked(key));
    // refreshed
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(2, stats.hitCount());

    ticker.advance(1, MILLISECONDS);
    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(2, stats.loadExceptionCount());
    assertEquals(3, stats.hitCount());
  }

  @Test
  public void testBulkLoadNull() throws ExecutionException {
    LoadingCache<Object, Object> cache =
        CacheBuilder.newBuilder().recordStats().build(bulkLoader(constantLoader(null)));
    CacheStats stats = cache.stats();
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    try {
      cache.getAll(asList(new Object()));
      fail();
    } catch (InvalidCacheLoadException expected) {
    }
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());
  }

  @Test
  public void testBulkLoadNullMap() throws ExecutionException {
    LoadingCache<Object, Object> cache =
        CacheBuilder.newBuilder()
            .recordStats()
            .build(
                new CacheLoader<Object, Object>() {
                  @Override
                  public Object load(Object key) {
                    throw new AssertionError();
                  }

                  @Override
                  public Map<Object, Object> loadAll(Iterable<?> keys) {
                    return null;
                  }
                });

    CacheStats stats = cache.stats();
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    try {
      cache.getAll(asList(new Object()));
      fail();
    } catch (InvalidCacheLoadException expected) {
    }
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());
  }

  @Test
  public void testLoadError() throws ExecutionException {
    Error e = new Error();
    CacheLoader<Object, Object> loader = errorLoader(e);
    LoadingCache<Object, Object> cache = CacheBuilder.newBuilder().recordStats().build(loader);
    CacheStats stats = cache.stats();
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    try {
      cache.get(new Object());
      fail();
    } catch (ExecutionError expected) {
      assertSame(e, expected.getCause());
    }
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    try {
      cache.getUnchecked(new Object());
      fail();
    } catch (ExecutionError expected) {
      assertSame(e, expected.getCause());
    }
    stats = cache.stats();
    assertEquals(2, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(2, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    cache.refresh(new Object());
    checkLoggedCause(e);
    stats = cache.stats();
    assertEquals(2, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(3, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    final Error callableError = new Error();
    try {
      cache.get(
          new Object(),
          new Callable<Object>() {
            @Override
            public Object call() {
              throw callableError;
            }
          });
      fail();
    } catch (ExecutionError expected) {
      assertSame(callableError, expected.getCause());
    }
    stats = cache.stats();
    assertEquals(3, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(4, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    try {
      cache.getAll(asList(new Object()));
      fail();
    } catch (ExecutionError expected) {
      assertSame(e, expected.getCause());
    }
    stats = cache.stats();
    assertEquals(4, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(5, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());
  }

  @Test
  public void testReloadError() throws ExecutionException {
    final Object one = new Object();
    final Error e = new Error();
    CacheLoader<Object, Object> loader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object key) {
            return one;
          }

          @Override
          public ListenableFuture<Object> reload(Object key, Object oldValue) {
            throw e;
          }
        };

    LoadingCache<Object, Object> cache = CacheBuilder.newBuilder().recordStats().build(loader);
    Object key = new Object();
    CacheStats stats = cache.stats();
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    cache.refresh(key);
    checkLoggedCause(e);
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(1, stats.hitCount());
  }

  @Test
  public void testReloadFutureError() throws ExecutionException {
    final Object one = new Object();
    final Error e = new Error();
    CacheLoader<Object, Object> loader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object key) {
            return one;
          }

          @Override
          public ListenableFuture<Object> reload(Object key, Object oldValue) {
            return Futures.immediateFailedFuture(e);
          }
        };

    LoadingCache<Object, Object> cache = CacheBuilder.newBuilder().recordStats().build(loader);
    Object key = new Object();
    CacheStats stats = cache.stats();
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    cache.refresh(key);
    checkLoggedCause(e);
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(1, stats.hitCount());
  }

  @Test
  public void testRefreshError() {
    final Object one = new Object();
    final Error e = new Error();
    FakeTicker ticker = new FakeTicker();
    CacheLoader<Object, Object> loader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object key) {
            return one;
          }

          @Override
          public ListenableFuture<Object> reload(Object key, Object oldValue) {
            return Futures.immediateFailedFuture(e);
          }
        };

    LoadingCache<Object, Object> cache =
        CacheBuilder.newBuilder()
            .recordStats()
            .ticker(ticker)
            .refreshAfterWrite(1, MILLISECONDS)
            .build(loader);
    Object key = new Object();
    CacheStats stats = cache.stats();
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    ticker.advance(1, MILLISECONDS);
    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(1, stats.hitCount());

    ticker.advance(1, MILLISECONDS);
    assertSame(one, cache.getUnchecked(key));
    // refreshed
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(2, stats.hitCount());

    ticker.advance(1, MILLISECONDS);
    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(2, stats.loadExceptionCount());
    assertEquals(3, stats.hitCount());
  }

  @Test
  public void testBulkLoadError() throws ExecutionException {
    Error e = new Error();
    CacheLoader<Object, Object> loader = errorLoader(e);
    LoadingCache<Object, Object> cache =
        CacheBuilder.newBuilder().recordStats().build(bulkLoader(loader));
    CacheStats stats = cache.stats();
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    try {
      cache.getAll(asList(new Object()));
      fail();
    } catch (ExecutionError expected) {
      assertSame(e, expected.getCause());
    }
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());
  }

  @Test
  public void testLoadCheckedException() {
    Exception e = new Exception();
    CacheLoader<Object, Object> loader = exceptionLoader(e);
    LoadingCache<Object, Object> cache = CacheBuilder.newBuilder().recordStats().build(loader);
    CacheStats stats = cache.stats();
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    try {
      cache.get(new Object());
      fail();
    } catch (ExecutionException expected) {
      assertSame(e, expected.getCause());
    }
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    try {
      cache.getUnchecked(new Object());
      fail();
    } catch (UncheckedExecutionException expected) {
      assertSame(e, expected.getCause());
    }
    stats = cache.stats();
    assertEquals(2, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(2, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    cache.refresh(new Object());
    checkLoggedCause(e);
    stats = cache.stats();
    assertEquals(2, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(3, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    Exception callableException = new Exception();
    try {
      cache.get(new Object(), throwing(callableException));
      fail();
    } catch (ExecutionException expected) {
      assertSame(callableException, expected.getCause());
    }
    stats = cache.stats();
    assertEquals(3, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(4, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    try {
      cache.getAll(asList(new Object()));
      fail();
    } catch (ExecutionException expected) {
      assertSame(e, expected.getCause());
    }
    stats = cache.stats();
    assertEquals(4, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(5, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());
  }

  @Test
  public void testLoadInterruptedException() {
    Exception e = new InterruptedException();
    CacheLoader<Object, Object> loader = exceptionLoader(e);
    LoadingCache<Object, Object> cache = CacheBuilder.newBuilder().recordStats().build(loader);
    CacheStats stats = cache.stats();
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    // Sanity check:
    assertFalse(Thread.interrupted());

    try {
      cache.get(new Object());
      fail();
    } catch (ExecutionException expected) {
      assertSame(e, expected.getCause());
    }
    assertTrue(Thread.interrupted());
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    try {
      cache.getUnchecked(new Object());
      fail();
    } catch (UncheckedExecutionException expected) {
      assertSame(e, expected.getCause());
    }
    assertTrue(Thread.interrupted());
    stats = cache.stats();
    assertEquals(2, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(2, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    cache.refresh(new Object());
    assertTrue(Thread.interrupted());
    checkLoggedCause(e);
    stats = cache.stats();
    assertEquals(2, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(3, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    Exception callableException = new InterruptedException();
    try {
      cache.get(new Object(), throwing(callableException));
      fail();
    } catch (ExecutionException expected) {
      assertSame(callableException, expected.getCause());
    }
    assertTrue(Thread.interrupted());
    stats = cache.stats();
    assertEquals(3, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(4, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    try {
      cache.getAll(asList(new Object()));
      fail();
    } catch (ExecutionException expected) {
      assertSame(e, expected.getCause());
    }
    assertTrue(Thread.interrupted());
    stats = cache.stats();
    assertEquals(4, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(5, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());
  }

  @Test
  public void testReloadCheckedException() {
    final Object one = new Object();
    final Exception e = new Exception();
    CacheLoader<Object, Object> loader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object key) {
            return one;
          }

          @Override
          public ListenableFuture<Object> reload(Object key, Object oldValue) throws Exception {
            throw e;
          }
        };

    LoadingCache<Object, Object> cache = CacheBuilder.newBuilder().recordStats().build(loader);
    Object key = new Object();
    CacheStats stats = cache.stats();
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    cache.refresh(key);
    checkLoggedCause(e);
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(1, stats.hitCount());
  }

  @Test
  public void testReloadFutureCheckedException() {
    final Object one = new Object();
    final Exception e = new Exception();
    CacheLoader<Object, Object> loader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object key) {
            return one;
          }

          @Override
          public ListenableFuture<Object> reload(Object key, Object oldValue) {
            return Futures.immediateFailedFuture(e);
          }
        };

    LoadingCache<Object, Object> cache = CacheBuilder.newBuilder().recordStats().build(loader);
    Object key = new Object();
    CacheStats stats = cache.stats();
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    cache.refresh(key);
    checkLoggedCause(e);
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(1, stats.hitCount());
  }

  @Test
  public void testRefreshCheckedException() {
    final Object one = new Object();
    final Exception e = new Exception();
    FakeTicker ticker = new FakeTicker();
    CacheLoader<Object, Object> loader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object key) {
            return one;
          }

          @Override
          public ListenableFuture<Object> reload(Object key, Object oldValue) {
            return Futures.immediateFailedFuture(e);
          }
        };

    LoadingCache<Object, Object> cache =
        CacheBuilder.newBuilder()
            .recordStats()
            .ticker(ticker)
            .refreshAfterWrite(1, MILLISECONDS)
            .build(loader);
    Object key = new Object();
    CacheStats stats = cache.stats();
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    ticker.advance(1, MILLISECONDS);
    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(1, stats.hitCount());

    ticker.advance(1, MILLISECONDS);
    assertSame(one, cache.getUnchecked(key));
    // refreshed
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(2, stats.hitCount());

    ticker.advance(1, MILLISECONDS);
    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(2, stats.loadExceptionCount());
    assertEquals(3, stats.hitCount());
  }

  @Test
  public void testBulkLoadCheckedException() {
    Exception e = new Exception();
    CacheLoader<Object, Object> loader = exceptionLoader(e);
    LoadingCache<Object, Object> cache =
        CacheBuilder.newBuilder().recordStats().build(bulkLoader(loader));
    CacheStats stats = cache.stats();
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    try {
      cache.getAll(asList(new Object()));
      fail();
    } catch (ExecutionException expected) {
      assertSame(e, expected.getCause());
    }
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());
  }

  @Test
  public void testBulkLoadInterruptedException() {
    Exception e = new InterruptedException();
    CacheLoader<Object, Object> loader = exceptionLoader(e);
    LoadingCache<Object, Object> cache =
        CacheBuilder.newBuilder().recordStats().build(bulkLoader(loader));
    CacheStats stats = cache.stats();
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    try {
      cache.getAll(asList(new Object()));
      fail();
    } catch (ExecutionException expected) {
      assertSame(e, expected.getCause());
    }
    assertTrue(Thread.interrupted());
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());
  }

  @Test
  public void testLoadUncheckedException() throws ExecutionException {
    Exception e = new RuntimeException();
    CacheLoader<Object, Object> loader = exceptionLoader(e);
    LoadingCache<Object, Object> cache = CacheBuilder.newBuilder().recordStats().build(loader);
    CacheStats stats = cache.stats();
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    try {
      cache.get(new Object());
      fail();
    } catch (UncheckedExecutionException expected) {
      assertSame(e, expected.getCause());
    }
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    try {
      cache.getUnchecked(new Object());
      fail();
    } catch (UncheckedExecutionException expected) {
      assertSame(e, expected.getCause());
    }
    stats = cache.stats();
    assertEquals(2, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(2, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    cache.refresh(new Object());
    checkLoggedCause(e);
    stats = cache.stats();
    assertEquals(2, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(3, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    Exception callableException = new RuntimeException();
    try {
      cache.get(new Object(), throwing(callableException));
      fail();
    } catch (UncheckedExecutionException expected) {
      assertSame(callableException, expected.getCause());
    }
    stats = cache.stats();
    assertEquals(3, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(4, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    try {
      cache.getAll(asList(new Object()));
      fail();
    } catch (UncheckedExecutionException expected) {
      assertSame(e, expected.getCause());
    }
    stats = cache.stats();
    assertEquals(4, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(5, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());
  }

  @Test
  public void testReloadUncheckedException() throws ExecutionException {
    final Object one = new Object();
    final Exception e = new RuntimeException();
    CacheLoader<Object, Object> loader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object key) {
            return one;
          }

          @Override
          public ListenableFuture<Object> reload(Object key, Object oldValue) throws Exception {
            throw e;
          }
        };

    LoadingCache<Object, Object> cache = CacheBuilder.newBuilder().recordStats().build(loader);
    Object key = new Object();
    CacheStats stats = cache.stats();
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    cache.refresh(key);
    checkLoggedCause(e);
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(1, stats.hitCount());
  }

  @Test
  public void testReloadFutureUncheckedException() throws ExecutionException {
    final Object one = new Object();
    final Exception e = new RuntimeException();
    CacheLoader<Object, Object> loader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object key) {
            return one;
          }

          @Override
          public ListenableFuture<Object> reload(Object key, Object oldValue) {
            return Futures.immediateFailedFuture(e);
          }
        };

    LoadingCache<Object, Object> cache = CacheBuilder.newBuilder().recordStats().build(loader);
    Object key = new Object();
    CacheStats stats = cache.stats();
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    cache.refresh(key);
    checkLoggedCause(e);
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(1, stats.hitCount());
  }

  @Test
  public void testRefreshUncheckedException() {
    final Object one = new Object();
    final Exception e = new RuntimeException();
    FakeTicker ticker = new FakeTicker();
    CacheLoader<Object, Object> loader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object key) {
            return one;
          }

          @Override
          public ListenableFuture<Object> reload(Object key, Object oldValue) {
            return Futures.immediateFailedFuture(e);
          }
        };

    LoadingCache<Object, Object> cache =
        CacheBuilder.newBuilder()
            .recordStats()
            .ticker(ticker)
            .refreshAfterWrite(1, MILLISECONDS)
            .build(loader);
    Object key = new Object();
    CacheStats stats = cache.stats();
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    ticker.advance(1, MILLISECONDS);
    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(1, stats.hitCount());

    ticker.advance(1, MILLISECONDS);
    assertSame(one, cache.getUnchecked(key));
    // refreshed
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(2, stats.hitCount());

    ticker.advance(1, MILLISECONDS);
    assertSame(one, cache.getUnchecked(key));
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(1, stats.loadSuccessCount());
    assertEquals(2, stats.loadExceptionCount());
    assertEquals(3, stats.hitCount());
  }

  @Test
  public void testBulkLoadUncheckedException() throws ExecutionException {
    Exception e = new RuntimeException();
    CacheLoader<Object, Object> loader = exceptionLoader(e);
    LoadingCache<Object, Object> cache =
        CacheBuilder.newBuilder().recordStats().build(bulkLoader(loader));
    CacheStats stats = cache.stats();
    assertEquals(0, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(0, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());

    try {
      cache.getAll(asList(new Object()));
      fail();
    } catch (UncheckedExecutionException expected) {
      assertSame(e, expected.getCause());
    }
    stats = cache.stats();
    assertEquals(1, stats.missCount());
    assertEquals(0, stats.loadSuccessCount());
    assertEquals(1, stats.loadExceptionCount());
    assertEquals(0, stats.hitCount());
  }

  @Test
  public void testReloadAfterFailure() throws ExecutionException {
    final AtomicInteger count = new AtomicInteger();
    final Exception e = new IllegalStateException("exception to trigger failure on first load()");
    CacheLoader<Integer, String> failOnceFunction =
        new CacheLoader<Integer, String>() {

          @Override
          public String load(Integer key) throws Exception {
            if (count.getAndIncrement() == 0) {
              throw e;
            }
            return key.toString();
          }
        };
    CountingRemovalListener<Integer, String> removalListener = countingRemovalListener();
    LoadingCache<Integer, String> cache =
        CacheBuilder.newBuilder().removalListener(removalListener).build(failOnceFunction);

    try {
      cache.getUnchecked(1);
      fail();
    } catch (UncheckedExecutionException ue) {
      assertSame(e, ue.getCause());
    }

    assertEquals("1", cache.getUnchecked(1));
    assertEquals(0, removalListener.getCount());

    count.set(0);
    cache.refresh(2);
    checkLoggedCause(e);

    assertEquals("2", cache.getUnchecked(2));
    assertEquals(0, removalListener.getCount());
  }

  @Test
  @Tag("gc")
  public void testReloadAfterValueReclamation() throws InterruptedException, ExecutionException {
    CountingLoader countingLoader = new CountingLoader();
    LoadingCache<Object, Object> cache =
        CacheBuilder.newBuilder().weakValues().build(countingLoader);
    ConcurrentMap<Object, Object> map = cache.asMap();

    int iterations = 10;
    WeakReference<Object> ref = new WeakReference<>(null);
    int expectedComputations = 0;
    for (int i = 0; i < iterations; i++) {
      // The entry should get garbage collected and recomputed.
      Object oldValue = ref.get();
      if (oldValue == null) {
        expectedComputations++;
      }
      ref = new WeakReference<>(cache.getUnchecked(1));
      oldValue = null;
      Thread.sleep(i);
      System.gc();
    }
    assertEquals(expectedComputations, countingLoader.getCount());

    for (int i = 0; i < iterations; i++) {
      // The entry should get garbage collected and recomputed.
      Object oldValue = ref.get();
      if (oldValue == null) {
        expectedComputations++;
      }
      cache.refresh(1);
      checkNothingLogged();
      ref = new WeakReference<>(map.get(1));
      oldValue = null;
      Thread.sleep(i);
      System.gc();
    }
    assertEquals(expectedComputations, countingLoader.getCount());
  }

  @Test
  public void testReloadAfterSimulatedValueReclamation() throws ExecutionException {
    CountingLoader countingLoader = new CountingLoader();
    LoadingCache<Object, Object> cache =
        CacheBuilder.newBuilder().concurrencyLevel(1).weakValues().build(countingLoader);

    Object key = new Object();
    assertNotNull(cache.getUnchecked(key));

    CacheTesting.simulateValueReclamation(cache, key);

    // this blocks if computation can't deal with partially-collected values
    assertNotNull(cache.getUnchecked(key));
    assertEquals(1, cache.size());
    assertEquals(2, countingLoader.getCount());

    CacheTesting.simulateValueReclamation(cache, key);
    cache.refresh(key);
    checkNothingLogged();
    assertEquals(1, cache.size());
    assertEquals(3, countingLoader.getCount());
  }

  @Test
  public void testReloadAfterSimulatedKeyReclamation() throws ExecutionException {
    CountingLoader countingLoader = new CountingLoader();
    LoadingCache<Object, Object> cache =
        CacheBuilder.newBuilder().concurrencyLevel(1).weakKeys().build(countingLoader);

    Object key = new Object();
    assertNotNull(cache.getUnchecked(key));
    assertEquals(1, cache.size());

    CacheTesting.simulateKeyReclamation(cache, key);

    // this blocks if computation can't deal with partially-collected values
    assertNotNull(cache.getUnchecked(key));
    assertEquals(2, countingLoader.getCount());

    CacheTesting.simulateKeyReclamation(cache, key);
    cache.refresh(key);
    checkNothingLogged();
    assertEquals(3, countingLoader.getCount());
  }

  /**
   * Make sure LoadingCache correctly wraps ExecutionExceptions and UncheckedExecutionExceptions.
   */
  @Test
  public void testLoadingExceptionWithCause() {
    final Exception cause = new Exception();
    final UncheckedExecutionException uee = new UncheckedExecutionException(cause);
    final ExecutionException ee = new ExecutionException(cause);

    LoadingCache<Object, Object> cacheUnchecked =
        CacheBuilder.newBuilder().build(exceptionLoader(uee));
    LoadingCache<Object, Object> cacheChecked =
        CacheBuilder.newBuilder().build(exceptionLoader(ee));

    try {
      cacheUnchecked.get(new Object());
      fail();
    } catch (ExecutionException e) {
      fail();
    } catch (UncheckedExecutionException caughtEe) {
      assertSame(uee, caughtEe.getCause());
    }

    try {
      cacheUnchecked.getUnchecked(new Object());
      fail();
    } catch (UncheckedExecutionException caughtUee) {
      assertSame(uee, caughtUee.getCause());
    }

    cacheUnchecked.refresh(new Object());
    checkLoggedCause(uee);

    try {
      cacheUnchecked.getAll(asList(new Object()));
      fail();
    } catch (ExecutionException e) {
      fail();
    } catch (UncheckedExecutionException caughtEe) {
      assertSame(uee, caughtEe.getCause());
    }

    try {
      cacheChecked.get(new Object());
      fail();
    } catch (ExecutionException caughtEe) {
      assertSame(ee, caughtEe.getCause());
    }

    try {
      cacheChecked.getUnchecked(new Object());
      fail();
    } catch (UncheckedExecutionException caughtUee) {
      assertSame(ee, caughtUee.getCause());
    }

    cacheChecked.refresh(new Object());
    checkLoggedCause(ee);

    try {
      cacheChecked.getAll(asList(new Object()));
      fail();
    } catch (ExecutionException caughtEe) {
      assertSame(ee, caughtEe.getCause());
    }
  }

  @Test
  public void testBulkLoadingExceptionWithCause() {
    final Exception cause = new Exception();
    final UncheckedExecutionException uee = new UncheckedExecutionException(cause);
    final ExecutionException ee = new ExecutionException(cause);

    LoadingCache<Object, Object> cacheUnchecked =
        CacheBuilder.newBuilder().build(bulkLoader(exceptionLoader(uee)));
    LoadingCache<Object, Object> cacheChecked =
        CacheBuilder.newBuilder().build(bulkLoader(exceptionLoader(ee)));

    try {
      cacheUnchecked.getAll(asList(new Object()));
      fail();
    } catch (ExecutionException e) {
      fail();
    } catch (UncheckedExecutionException caughtEe) {
      assertSame(uee, caughtEe.getCause());
    }

    try {
      cacheChecked.getAll(asList(new Object()));
      fail();
    } catch (ExecutionException caughtEe) {
      assertSame(ee, caughtEe.getCause());
    }
  }

  // Lava: Guava's multi-threaded tests (testConcurrentLoading*, testConcurrentExpirationLoading,
  // testExpandDuringLoading, testExpandDuringRefresh) are dropped: Lava is single-threaded, and
  // the expand tests poke at segment tables. The single-threaded tests below keep the intent of
  // testAsMapDuringLoading, testInvalidateDuringLoading and testInvalidateAndReloadDuringLoading
  // by making the "other thread" a nested call made from inside a loader.

  @Test
  public void testAsMapDuringLoading() throws ExecutionException {
    final String getKey = "get";
    final String refreshKey = "refresh";
    final String suffix = "Suffix";
    final List<String> observations = Lists.newArrayList();
    final AtomicReference<LoadingCache<String, String>> cacheRef = new AtomicReference<>();

    CacheLoader<String, String> computeFunction =
        new CacheLoader<String, String>() {
          @Override
          public String load(String key) {
            ConcurrentMap<String, String> map = cacheRef.get().asMap();
            if (key.equals(getKey)) {
              // start the refresh while this get is still computing
              cacheRef.get().refresh(refreshKey);
            } else {
              // both computations are in progress; asMap shouldn't have changed
              observations.add("size=" + map.size());
              observations.add("containsGet=" + map.containsKey(getKey));
              observations.add("refresh=" + map.get(refreshKey));
            }
            return key + suffix;
          }
        };

    final LoadingCache<String, String> cache = CacheBuilder.newBuilder().build(computeFunction);
    cacheRef.set(cache);
    ConcurrentMap<String, String> map = cache.asMap();
    map.put(refreshKey, refreshKey);
    assertEquals(1, map.size());
    assertFalse(map.containsKey(getKey));
    assertSame(refreshKey, map.get(refreshKey));

    assertEquals(getKey + suffix, cache.get(getKey));
    checkNothingLogged();

    // the refresh ran inside the get, while the get was still in progress
    assertEquals(asList("size=1", "containsGet=false", "refresh=" + refreshKey), observations);
    // asMap view should have been updated
    assertEquals(2, cache.size());
    assertEquals(getKey + suffix, map.get(getKey));
    assertEquals(refreshKey + suffix, map.get(refreshKey));
  }

  @Test
  public void testAsMapDuringRefresh() {
    // While a refresh is in progress, asMap shows the old value, and nothing else changes.
    final String refreshKey = "refresh";
    final List<String> observations = Lists.newArrayList();
    final AtomicReference<LoadingCache<String, String>> cacheRef = new AtomicReference<>();
    CacheLoader<String, String> computeFunction =
        new CacheLoader<String, String>() {
          @Override
          public String load(String key) {
            ConcurrentMap<String, String> map = cacheRef.get().asMap();
            observations.add("size=" + map.size());
            observations.add("containsKey=" + map.containsKey(key));
            observations.add("get=" + map.get(key));
            observations.add("getIfPresent=" + cacheRef.get().getIfPresent(key));
            return key + "Suffix";
          }
        };
    final LoadingCache<String, String> cache = CacheBuilder.newBuilder().build(computeFunction);
    cacheRef.set(cache);
    cache.asMap().put(refreshKey, refreshKey);

    cache.refresh(refreshKey);
    checkNothingLogged();

    assertEquals(
        asList("size=1", "containsKey=true", "get=" + refreshKey, "getIfPresent=" + refreshKey),
        observations);
    assertEquals(refreshKey + "Suffix", cache.asMap().get(refreshKey));
    assertEquals(1, cache.size());
  }

  @Test
  public void testInvalidateDuringLoading() throws ExecutionException {
    // computation starts; invalidate() is called on the key being computed, computation finishes
    final String getKey = "get";
    final String refreshKey = "refresh";
    final String suffix = "Suffix";
    final AtomicReference<LoadingCache<String, String>> cacheRef = new AtomicReference<>();
    final List<Boolean> containsAfterInvalidate = Lists.newArrayList();

    CacheLoader<String, String> computeFunction =
        new CacheLoader<String, String>() {
          @Override
          public String load(String key) {
            LoadingCache<String, String> cache = cacheRef.get();
            if (key.equals(getKey)) {
              // start the refresh while this get is still computing
              cache.refresh(refreshKey);
            } else {
              // both computations are in progress
              cache.invalidate(getKey);
              cache.invalidate(refreshKey);
              containsAfterInvalidate.add(cache.asMap().containsKey(getKey));
              containsAfterInvalidate.add(cache.asMap().containsKey(refreshKey));
            }
            return key + suffix;
          }
        };

    final LoadingCache<String, String> cache = CacheBuilder.newBuilder().build(computeFunction);
    cacheRef.set(cache);
    ConcurrentMap<String, String> map = cache.asMap();
    map.put(refreshKey, refreshKey);

    assertEquals(getKey + suffix, cache.get(getKey));
    checkNothingLogged();
    assertEquals(asList(false, false), containsAfterInvalidate);

    // results should be visible
    assertEquals(2, cache.size());
    assertEquals(getKey + suffix, map.get(getKey));
    assertEquals(refreshKey + suffix, map.get(refreshKey));
    assertEquals(2, cache.size());
  }

  @Test
  public void testRecursiveLoad() {
    // Lava: a load of a key that is already being loaded on this thread would block forever in
    // Guava (it waits for itself); Lava fails fast. Loads of other keys can nest.
    final AtomicReference<LoadingCache<String, String>> cacheRef = new AtomicReference<>();
    CacheLoader<String, String> loader =
        new CacheLoader<String, String>() {
          @Override
          public String load(String key) throws ExecutionException {
            if (key.equals("self")) {
              return cacheRef.get().get("self");
            } else if (key.equals("outer")) {
              return "outer+" + cacheRef.get().get("inner");
            }
            return key;
          }
        };
    LoadingCache<String, String> cache = CacheBuilder.newBuilder().build(loader);
    cacheRef.set(cache);

    try {
      cache.getUnchecked("self");
      fail();
    } catch (UncheckedExecutionException expected) {
      assertThat(expected.getCause()).isInstanceOf(IllegalStateException.class);
      assertEquals("Recursive load of: self", expected.getCause().getMessage());
    }
    assertEquals(0, cache.size());

    assertEquals("outer+inner", cache.getUnchecked("outer"));
    assertEquals(2, cache.size());
  }

  static <T> Callable<T> returning(final T value) {
    return new Callable<T>() {
      @Override
      public T call() {
        return value;
      }
    };
  }

  static <T> Callable<T> throwing(final Exception exception) {
    return new Callable<T>() {
      @Override
      public T call() throws Exception {
        throw exception;
      }
    };
  }
}
