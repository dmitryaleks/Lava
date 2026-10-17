/*
 * Copyright (C) 2011 The Guava Authors
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

import static com.simplelibs.lava.cache.CacheBuilder.EMPTY_STATS;
import static com.simplelibs.lava.cache.TestingCacheLoaders.identityLoader;
import static org.assertj.core.api.Assertions.assertThat;

import com.simplelibs.lava.cache.LocalCache.LocalLoadingCache;
import com.simplelibs.lava.collect.ImmutableMap;
import com.simplelibs.lava.collect.ImmutableSet;
import com.simplelibs.lava.testing.LavaTestCase;
import com.simplelibs.lava.testing.NullPointerTester;
import com.simplelibs.lava.util.concurrent.UncheckedExecutionException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Tests of the caches built by {@code CacheBuilder.build(CacheLoader)}.
 *
 * @author Charles Fry
 */
public class LocalLoadingCacheTest extends LavaTestCase {

  private static <K, V> LocalLoadingCache<K, V> makeCache(
      CacheBuilder<K, V> builder, CacheLoader<? super K, V> loader) {
    return new LocalLoadingCache<>(builder, loader);
  }

  private CacheBuilder<Object, Object> createCacheBuilder() {
    return CacheBuilder.newBuilder().recordStats();
  }

  // constructor tests

  @Test
  public void testComputingFunction() {
    CacheLoader<Object, Object> loader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object from) {
            return new Object();
          }
        };
    LocalLoadingCache<Object, Object> cache = makeCache(createCacheBuilder(), loader);
    assertSame(loader, cache.localCache.defaultLoader);
  }

  // null parameters test

  @Test
  public void testNullParameters() throws Exception {
    NullPointerTester tester = new NullPointerTester();
    CacheLoader<Object, Object> loader = identityLoader();
    tester.testAllPublicInstanceMethods(makeCache(createCacheBuilder(), loader));
  }

  // stats tests

  @Test
  public void testStats() {
    CacheBuilder<Object, Object> builder = createCacheBuilder().concurrencyLevel(1).maximumSize(2);
    LocalLoadingCache<Object, Object> cache = makeCache(builder, identityLoader());
    assertEquals(EMPTY_STATS, cache.stats());

    Object one = new Object();
    cache.getUnchecked(one);
    CacheStats stats = cache.stats();
    assertEquals(1, stats.requestCount());
    assertEquals(0, stats.hitCount());
    assertEquals(0.0, stats.hitRate());
    assertEquals(1, stats.missCount());
    assertEquals(1.0, stats.missRate());
    assertEquals(1, stats.loadCount());
    long totalLoadTime = stats.totalLoadTime();
    assertTrue(totalLoadTime >= 0);
    assertTrue(stats.averageLoadPenalty() >= 0.0);
    assertEquals(0, stats.evictionCount());

    cache.getUnchecked(one);
    stats = cache.stats();
    assertEquals(2, stats.requestCount());
    assertEquals(1, stats.hitCount());
    assertEquals(1.0 / 2, stats.hitRate());
    assertEquals(1, stats.missCount());
    assertEquals(1.0 / 2, stats.missRate());
    assertEquals(1, stats.loadCount());
    assertEquals(0, stats.evictionCount());

    Object two = new Object();
    cache.getUnchecked(two);
    stats = cache.stats();
    assertEquals(3, stats.requestCount());
    assertEquals(1, stats.hitCount());
    assertEquals(1.0 / 3, stats.hitRate());
    assertEquals(2, stats.missCount());
    assertEquals(2.0 / 3, stats.missRate());
    assertEquals(2, stats.loadCount());
    assertTrue(stats.totalLoadTime() >= totalLoadTime);
    totalLoadTime = stats.totalLoadTime();
    assertTrue(stats.averageLoadPenalty() >= 0.0);
    assertEquals(0, stats.evictionCount());

    Object three = new Object();
    cache.getUnchecked(three);
    stats = cache.stats();
    assertEquals(4, stats.requestCount());
    assertEquals(1, stats.hitCount());
    assertEquals(1.0 / 4, stats.hitRate());
    assertEquals(3, stats.missCount());
    assertEquals(3.0 / 4, stats.missRate());
    assertEquals(3, stats.loadCount());
    assertTrue(stats.totalLoadTime() >= totalLoadTime);
    totalLoadTime = stats.totalLoadTime();
    assertTrue(stats.averageLoadPenalty() >= 0.0);
    assertEquals(1, stats.evictionCount());
  }

  @Test
  public void testStatsNoops() {
    CacheBuilder<Object, Object> builder = createCacheBuilder().concurrencyLevel(1);
    LocalLoadingCache<Object, Object> cache = makeCache(builder, identityLoader());
    ConcurrentMap<Object, Object> map = cache.localCache; // modifiable map view
    assertEquals(EMPTY_STATS, cache.stats());

    Object one = new Object();
    assertNull(map.put(one, one));
    assertSame(one, map.get(one));
    assertTrue(map.containsKey(one));
    assertTrue(map.containsValue(one));
    Object two = new Object();
    assertSame(one, map.replace(one, two));
    assertTrue(map.containsKey(one));
    assertFalse(map.containsValue(one));
    Object three = new Object();
    assertTrue(map.replace(one, two, three));
    assertTrue(map.remove(one, three));
    assertFalse(map.containsKey(one));
    assertFalse(map.containsValue(one));
    assertNull(map.putIfAbsent(two, three));
    assertSame(three, map.remove(two));
    assertNull(map.put(three, one));
    assertNull(map.put(one, two));

    assertThat(map).containsEntry(three, one);
    assertThat(map).containsEntry(one, two);

    // TODO(cgruber): Confirm with fry@ that this is a reasonable substitute.
    // Set<Entry<Object, Object>> entries = map.entrySet();
    // assertThat(entries).containsExactlyInAnyOrder(
    //    Maps.immutableEntry(three, one), Maps.immutableEntry(one, two));
    // Set<Object> keys = map.keySet();
    // assertThat(keys).containsExactlyInAnyOrder(one, three);
    // Collection<Object> values = map.values();
    // assertThat(values).containsExactlyInAnyOrder(one, two);

    map.clear();

    assertEquals(EMPTY_STATS, cache.stats());
  }

  @Test
  public void testNoStats() {
    CacheBuilder<Object, Object> builder =
        CacheBuilder.newBuilder().concurrencyLevel(1).maximumSize(2);
    LocalLoadingCache<Object, Object> cache = makeCache(builder, identityLoader());
    assertEquals(EMPTY_STATS, cache.stats());

    Object one = new Object();
    cache.getUnchecked(one);
    assertEquals(EMPTY_STATS, cache.stats());

    cache.getUnchecked(one);
    assertEquals(EMPTY_STATS, cache.stats());

    Object two = new Object();
    cache.getUnchecked(two);
    assertEquals(EMPTY_STATS, cache.stats());

    Object three = new Object();
    cache.getUnchecked(three);
    assertEquals(EMPTY_STATS, cache.stats());
  }

  @Test
  public void testRecordStats() {
    CacheBuilder<Object, Object> builder =
        createCacheBuilder().recordStats().concurrencyLevel(1).maximumSize(2);
    LocalLoadingCache<Object, Object> cache = makeCache(builder, identityLoader());
    assertEquals(0, cache.stats().hitCount());
    assertEquals(0, cache.stats().missCount());

    Object one = new Object();
    cache.getUnchecked(one);
    assertEquals(0, cache.stats().hitCount());
    assertEquals(1, cache.stats().missCount());

    cache.getUnchecked(one);
    assertEquals(1, cache.stats().hitCount());
    assertEquals(1, cache.stats().missCount());

    Object two = new Object();
    cache.getUnchecked(two);
    assertEquals(1, cache.stats().hitCount());
    assertEquals(2, cache.stats().missCount());

    Object three = new Object();
    cache.getUnchecked(three);
    assertEquals(1, cache.stats().hitCount());
    assertEquals(3, cache.stats().missCount());
  }

  // asMap tests

  @Test
  public void testAsMap() {
    CacheBuilder<Object, Object> builder = createCacheBuilder();
    LocalLoadingCache<Object, Object> cache = makeCache(builder, identityLoader());
    assertEquals(EMPTY_STATS, cache.stats());

    Object one = new Object();
    Object two = new Object();
    Object three = new Object();

    ConcurrentMap<Object, Object> map = cache.asMap();
    assertNull(map.put(one, two));
    assertSame(two, map.get(one));
    map.putAll(ImmutableMap.of(two, three));
    assertSame(three, map.get(two));
    assertSame(two, map.putIfAbsent(one, three));
    assertSame(two, map.get(one));
    assertNull(map.putIfAbsent(three, one));
    assertSame(one, map.get(three));
    assertSame(two, map.replace(one, three));
    assertSame(three, map.get(one));
    assertFalse(map.replace(one, two, three));
    assertSame(three, map.get(one));
    assertTrue(map.replace(one, three, two));
    assertSame(two, map.get(one));
    assertEquals(3, map.size());

    map.clear();
    assertTrue(map.isEmpty());
    assertEquals(0, map.size());

    cache.getUnchecked(one);
    assertEquals(1, map.size());
    assertSame(one, map.get(one));
    assertTrue(map.containsKey(one));
    assertTrue(map.containsValue(one));
    assertSame(one, map.remove(one));
    assertEquals(0, map.size());

    cache.getUnchecked(one);
    assertEquals(1, map.size());
    assertFalse(map.remove(one, two));
    assertTrue(map.remove(one, one));
    assertEquals(0, map.size());

    cache.getUnchecked(one);
    Map<Object, Object> newMap = ImmutableMap.of(one, one);
    assertEquals(newMap, map);
    assertEquals(newMap.entrySet(), map.entrySet());
    assertEquals(newMap.keySet(), map.keySet());
    Set<Object> expectedValues = ImmutableSet.of(one);
    Set<Object> actualValues = ImmutableSet.copyOf(map.values());
    assertEquals(expectedValues, actualValues);
  }

  /**
   * Lookups on the map view move the entry to the most recent position, but {@code containsKey}
   * and iteration don't.
   *
   * <p>Lava: a black-box version of Guava's test, which looked at the segment's recency queue.
   */
  @Test
  public void testAsMapRecency() {
    CacheBuilder<Object, Object> builder = createCacheBuilder().maximumSize(3);
    LocalLoadingCache<Object, Object> cache = makeCache(builder, identityLoader());
    ConcurrentMap<Object, Object> map = cache.asMap();

    for (int i = 1; i <= 3; i++) {
      assertEquals(i, cache.getUnchecked(i));
    }
    // eviction order: 1, 2, 3
    assertEquals(1, map.get(1)); // 2, 3, 1
    assertTrue(map.containsKey(2)); // no change
    assertTrue(map.containsValue(2)); // no change
    assertEquals(3, ImmutableSet.copyOf(map.keySet()).size()); // iteration: no change
    assertEquals(3, ImmutableSet.copyOf(map.entrySet()).size());
    assertEquals(3, ImmutableSet.copyOf(map.values()).size());

    assertEquals(4, cache.getUnchecked(4)); // evicts 2
    assertFalse(map.containsKey(2));
    assertEquals(ImmutableSet.of(1, 3, 4), ImmutableSet.copyOf(map.keySet()));

    // eviction order: 3, 1, 4
    assertEquals(3, cache.getUnchecked(3)); // 1, 4, 3
    assertEquals(5, cache.getUnchecked(5)); // evicts 1
    assertFalse(map.containsKey(1));
    assertEquals(ImmutableSet.of(3, 4, 5), ImmutableSet.copyOf(map.keySet()));
  }

  @Test
  public void testRecursiveComputation() {
    final AtomicReference<LoadingCache<Integer, String>> cacheRef = new AtomicReference<>();
    CacheLoader<Integer, String> recursiveLoader =
        new CacheLoader<Integer, String>() {
          @Override
          public String load(Integer key) {
            if (key > 0) {
              return key + ", " + cacheRef.get().getUnchecked(key - 1);
            } else {
              return "0";
            }
          }
        };

    LoadingCache<Integer, String> recursiveCache =
        CacheBuilder.newBuilder().weakKeys().weakValues().build(recursiveLoader);
    cacheRef.set(recursiveCache);
    assertEquals("3, 2, 1, 0", recursiveCache.getUnchecked(3));

    recursiveLoader =
        new CacheLoader<Integer, String>() {
          @Override
          public String load(Integer key) {
            return cacheRef.get().getUnchecked(key);
          }
        };

    recursiveCache = CacheBuilder.newBuilder().weakKeys().weakValues().build(recursiveLoader);
    cacheRef.set(recursiveCache);

    // Lava: Guava runs this on another thread and only checks that the computation does not hang
    // (it fails with an IllegalStateException that the thread's exception handler swallows). Here
    // the exception is checked.
    try {
      cacheRef.get().getUnchecked(3);
      fail();
    } catch (UncheckedExecutionException expected) {
      assertThat(expected.getCause()).isInstanceOf(IllegalStateException.class);
      assertEquals("Recursive load of: 3", expected.getCause().getMessage());
    }
    assertEquals(0, cacheRef.get().size());
  }
}
