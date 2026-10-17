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

import com.simplelibs.lava.cache.ForwardingLoadingCache.SimpleForwardingLoadingCache;
import com.simplelibs.lava.collect.ImmutableList;
import com.simplelibs.lava.collect.ImmutableMap;
import com.simplelibs.lava.testing.LavaTestCase;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link ForwardingLoadingCache}.
 *
 * <p>Lava: Guava's test uses Mockito mocks of {@code LoadingCache}; this version uses a
 * hand-written {@link RecordingLoadingCache} that records each call (method and arguments) and
 * returns canned results.
 *
 * @author Charles Fry
 */
public class ForwardingLoadingCacheTest extends LavaTestCase {

  /** A loading cache that records the calls made to it and returns canned values. */
  static class RecordingLoadingCache<K, V> extends ForwardingCacheTest.RecordingCache<K, V>
      implements LoadingCache<K, V> {
    V loadResult;
    ExecutionException loadFailure;
    ImmutableMap<K, V> getAllResult = ImmutableMap.of();

    @Override
    public V get(K key) throws ExecutionException {
      calls.add("get(" + key + ")");
      if (loadFailure != null) {
        throw loadFailure;
      }
      return loadResult;
    }

    @Override
    public V getUnchecked(K key) {
      calls.add("getUnchecked(" + key + ")");
      return loadResult;
    }

    @Override
    public ImmutableMap<K, V> getAll(Iterable<? extends K> keys) throws ExecutionException {
      calls.add("getAll(" + keys + ")");
      return getAllResult;
    }

    @SuppressWarnings("deprecation") // implements the deprecated LoadingCache.apply
    @Override
    public V apply(K key) {
      calls.add("apply(" + key + ")");
      return loadResult;
    }

    @Override
    public void refresh(K key) {
      calls.add("refresh(" + key + ")");
    }
  }

  private LoadingCache<String, Boolean> forward;
  private RecordingLoadingCache<String, Boolean> recorder;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    recorder = new RecordingLoadingCache<>();
    forward =
        new ForwardingLoadingCache<String, Boolean>() {
          @Override
          protected LoadingCache<String, Boolean> delegate() {
            return recorder;
          }
        };
  }

  private void assertCalls(String... expected) {
    assertEquals(Arrays.asList(expected), recorder.calls);
  }

  @Test
  public void testGet() throws ExecutionException {
    recorder.loadResult = Boolean.TRUE;
    assertSame(Boolean.TRUE, forward.get("key"));
    assertCalls("get(key)");
  }

  @Test
  public void testGet_failure() {
    ExecutionException failure = new ExecutionException(new Exception("failed"));
    recorder.loadFailure = failure;
    try {
      forward.get("key");
      fail();
    } catch (ExecutionException expected) {
      assertSame(failure, expected);
    }
    assertCalls("get(key)");
  }

  @Test
  public void testGetUnchecked() {
    recorder.loadResult = Boolean.TRUE;
    assertSame(Boolean.TRUE, forward.getUnchecked("key"));
    assertCalls("getUnchecked(key)");
  }

  @Test
  public void testGetAll() throws ExecutionException {
    recorder.getAllResult = ImmutableMap.of("key", Boolean.TRUE);
    assertEquals(ImmutableMap.of("key", Boolean.TRUE), forward.getAll(ImmutableList.of("key")));
    assertCalls("getAll([key])");
  }

  @SuppressWarnings("deprecation") // tests the deprecated LoadingCache.apply
  @Test
  public void testApply() {
    recorder.loadResult = Boolean.TRUE;
    assertSame(Boolean.TRUE, forward.apply("key"));
    assertCalls("apply(key)");
  }

  @Test
  public void testInvalidate() {
    forward.invalidate("key");
    assertCalls("invalidate(key)");
  }

  @Test
  public void testRefresh() {
    forward.refresh("key");
    assertCalls("refresh(key)");
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
    assertCalls("asMap()");
  }

  @Test
  public void testCleanUp() {
    forward.cleanUp();
    assertCalls("cleanUp()");
  }

  @Test
  public void testForwardsTheCacheMethodsToo() {
    forward.put("a", Boolean.TRUE);
    forward.getIfPresent("a");
    assertCalls("put(a, true)", "getIfPresent(a)");
  }

  @Test
  public void testSimpleForwardingLoadingCache() throws ExecutionException {
    recorder.loadResult = Boolean.FALSE;
    LoadingCache<String, Boolean> simple =
        new SimpleForwardingLoadingCache<String, Boolean>(recorder) {};
    assertSame(Boolean.FALSE, simple.get("a"));
    simple.refresh("a");
    assertCalls("get(a)", "refresh(a)");
    try {
      new SimpleForwardingLoadingCache<String, Boolean>(null) {};
      fail();
    } catch (NullPointerException expected) {
    }
  }

  /** Make sure that all methods are forwarded. */
  @Test
  public void testAllMethodsAreForwarded() throws Exception {
    for (Method method : LoadingCache.class.getMethods()) {
      if (!Modifier.isAbstract(method.getModifiers())) {
        continue; // the default methods of java.util.function.Function
      }
      Method forwarding =
          ForwardingLoadingCache.class.getMethod(method.getName(), method.getParameterTypes());
      assertTrue(
          method + " is not overridden by ForwardingLoadingCache or ForwardingCache",
          forwarding.getDeclaringClass() == ForwardingLoadingCache.class
              || forwarding.getDeclaringClass() == ForwardingCache.class);
    }
  }
}
