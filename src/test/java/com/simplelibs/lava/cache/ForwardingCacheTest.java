/*
 * Copyright (C) 2009 The Guava Authors
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

package com.simplelibs.lava.cache;

import com.simplelibs.lava.cache.ForwardingCache.SimpleForwardingCache;
import com.simplelibs.lava.collect.ImmutableList;
import com.simplelibs.lava.collect.ImmutableMap;
import com.simplelibs.lava.testing.LavaTestCase;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link ForwardingCache}.
 *
 * <p>Lava: Guava's test uses Mockito mocks of {@code Cache}; this version uses a hand-written
 * {@link RecordingCache} that records each call (method and arguments) and returns canned results,
 * and also covers the methods Guava's test left out ({@code get(K, Callable)}, {@code put},
 * {@code putAll}).
 *
 * @author Charles Fry
 */
public class ForwardingCacheTest extends LavaTestCase {

  /** A cache that records the calls made to it and returns the canned values set on it. */
  static class RecordingCache<K, V> implements Cache<K, V> {
    final List<String> calls = new ArrayList<>();

    V getIfPresentResult;
    V getResult;
    ExecutionException getFailure;
    ImmutableMap<K, V> getAllPresentResult = ImmutableMap.of();
    long sizeResult;
    CacheStats statsResult;
    ConcurrentMap<K, V> asMapResult;
    Object lastCallable;

    @Override
    public V getIfPresent(Object key) {
      calls.add("getIfPresent(" + key + ")");
      return getIfPresentResult;
    }

    @Override
    public V get(K key, Callable<? extends V> valueLoader) throws ExecutionException {
      calls.add("get(" + key + ", callable)");
      lastCallable = valueLoader;
      if (getFailure != null) {
        throw getFailure;
      }
      return getResult;
    }

    @Override
    public ImmutableMap<K, V> getAllPresent(Iterable<?> keys) {
      calls.add("getAllPresent(" + keys + ")");
      return getAllPresentResult;
    }

    @Override
    public void put(K key, V value) {
      calls.add("put(" + key + ", " + value + ")");
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
      calls.add("putAll(" + m + ")");
    }

    @Override
    public void invalidate(Object key) {
      calls.add("invalidate(" + key + ")");
    }

    @Override
    public void invalidateAll(Iterable<?> keys) {
      calls.add("invalidateAll(" + keys + ")");
    }

    @Override
    public void invalidateAll() {
      calls.add("invalidateAll()");
    }

    @Override
    public long size() {
      calls.add("size()");
      return sizeResult;
    }

    @Override
    public CacheStats stats() {
      calls.add("stats()");
      return statsResult;
    }

    @Override
    public ConcurrentMap<K, V> asMap() {
      calls.add("asMap()");
      return asMapResult;
    }

    @Override
    public void cleanUp() {
      calls.add("cleanUp()");
    }

    @Override
    public String toString() {
      return "RecordingCache";
    }
  }

  private Cache<String, Boolean> forward;
  private RecordingCache<String, Boolean> recorder;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    recorder = new RecordingCache<>();
    forward =
        new ForwardingCache<String, Boolean>() {
          @Override
          protected Cache<String, Boolean> delegate() {
            return recorder;
          }
        };
  }

  private void assertCalls(String... expected) {
    assertEquals(Arrays.asList(expected), recorder.calls);
  }

  @Test
  public void testGetIfPresent() throws ExecutionException {
    recorder.getIfPresentResult = Boolean.TRUE;
    assertSame(Boolean.TRUE, forward.getIfPresent("key"));
    assertCalls("getIfPresent(key)");
  }

  @Test
  public void testGetIfPresent_absent() {
    assertNull(forward.getIfPresent("key"));
    assertCalls("getIfPresent(key)");
  }

  @Test
  public void testGet_callable() throws ExecutionException {
    Callable<Boolean> loader = () -> Boolean.FALSE;
    recorder.getResult = Boolean.TRUE;
    assertSame(Boolean.TRUE, forward.get("key", loader));
    assertSame(loader, recorder.lastCallable);
    assertCalls("get(key, callable)");
  }

  @Test
  public void testGet_callableFailure() {
    ExecutionException failure = new ExecutionException(new Exception("failed"));
    recorder.getFailure = failure;
    try {
      forward.get("key", () -> Boolean.TRUE);
      fail();
    } catch (ExecutionException expected) {
      assertSame(failure, expected);
    }
    assertCalls("get(key, callable)");
  }

  @Test
  public void testGetAllPresent() throws ExecutionException {
    recorder.getAllPresentResult = ImmutableMap.of("key", Boolean.TRUE);
    assertEquals(
        ImmutableMap.of("key", Boolean.TRUE), forward.getAllPresent(ImmutableList.of("key")));
    assertCalls("getAllPresent([key])");
  }

  @Test
  public void testPut() {
    forward.put("key", Boolean.TRUE);
    assertCalls("put(key, true)");
  }

  @Test
  public void testPutAll() {
    forward.putAll(ImmutableMap.of("key", Boolean.TRUE));
    assertCalls("putAll({key=true})");
  }

  @Test
  public void testInvalidate() {
    forward.invalidate("key");
    assertCalls("invalidate(key)");
  }

  @Test
  public void testInvalidateAllIterable() {
    forward.invalidateAll(ImmutableList.of("key"));
    assertCalls("invalidateAll([key])");
  }

  @Test
  public void testInvalidateAll() {
    forward.invalidateAll();
    assertCalls("invalidateAll()");
  }

  @Test
  public void testSize() {
    recorder.sizeResult = 42L;
    assertEquals(42L, forward.size());
    assertCalls("size()");
  }

  @Test
  public void testStats() {
    assertNull(forward.stats());
    CacheStats stats = new CacheStats(1, 2, 3, 4, 5, 6);
    recorder.statsResult = stats;
    assertSame(stats, forward.stats());
    assertCalls("stats()", "stats()");
  }

  @Test
  public void testAsMap() {
    assertNull(forward.asMap());
    ConcurrentMap<String, Boolean> map = new ConcurrentHashMap<>();
    recorder.asMapResult = map;
    assertSame(map, forward.asMap());
    assertCalls("asMap()", "asMap()");
  }

  @Test
  public void testCleanUp() {
    forward.cleanUp();
    assertCalls("cleanUp()");
  }

  @Test
  public void testToString() {
    // ForwardingObject.toString() is the delegate's
    assertEquals("RecordingCache", forward.toString());
    assertCalls();
  }

  @Test
  public void testSimpleForwardingCache() {
    Cache<String, Boolean> simple = new SimpleForwardingCache<String, Boolean>(recorder) {};
    simple.put("a", Boolean.FALSE);
    simple.invalidate("a");
    assertCalls("put(a, false)", "invalidate(a)");
    try {
      new SimpleForwardingCache<String, Boolean>(null) {};
      fail();
    } catch (NullPointerException expected) {
    }
  }

  /** Make sure that all methods are forwarded. */
  @Test
  public void testAllMethodsAreForwarded() throws Exception {
    for (Method method : Cache.class.getMethods()) {
      Method forwarding = ForwardingCache.class.getMethod(method.getName(), method.getParameterTypes());
      assertSame(
          method + " is not overridden by ForwardingCache",
          ForwardingCache.class,
          forwarding.getDeclaringClass());
    }
  }
}
