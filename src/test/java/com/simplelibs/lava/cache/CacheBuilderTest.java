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

import static com.simplelibs.lava.cache.TestingCacheLoaders.constantLoader;
import static com.simplelibs.lava.cache.TestingCacheLoaders.identityLoader;
import static com.simplelibs.lava.cache.TestingRemovalListeners.countingRemovalListener;
import static com.simplelibs.lava.cache.TestingRemovalListeners.nullRemovalListener;
import static com.simplelibs.lava.cache.TestingRemovalListeners.queuingRemovalListener;
import static com.simplelibs.lava.cache.TestingWeighers.constantWeigher;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.simplelibs.lava.base.Equivalence;
import com.simplelibs.lava.base.Ticker;
import com.simplelibs.lava.cache.TestingRemovalListeners.CountingRemovalListener;
import com.simplelibs.lava.cache.TestingRemovalListeners.QueuingRemovalListener;
import com.simplelibs.lava.testing.FakeTicker;
import com.simplelibs.lava.testing.LavaTestCase;
import com.simplelibs.lava.testing.NullPointerTester;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for CacheBuilder.
 *
 * <p>Lava: the tests of Guava's internal sizing (segment count and table lengths for {@code
 * initialCapacity}, {@code concurrencyLevel}, {@code maximumSize} and the defaults) are dropped,
 * because Lava has no segments ({@code concurrencyLevel} is validated but has no effect), and so are
 * the multi-threaded "clear basher" test; the other threaded tests are rewritten single-threaded.
 */
public class CacheBuilderTest extends LavaTestCase {

  @Test
  public void testNewBuilder() {
    CacheLoader<Object, Integer> loader = constantLoader(1);

    LoadingCache<String, Integer> cache =
        CacheBuilder.newBuilder().removalListener(countingRemovalListener()).build(loader);

    assertEquals(Integer.valueOf(1), cache.getUnchecked("one"));
    assertEquals(1, cache.size());
  }

  @Test
  public void testInitialCapacity_negative() {
    CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder();
    try {
      builder.initialCapacity(-1);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testInitialCapacity_setTwice() {
    CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder().initialCapacity(16);
    try {
      // even to the same value is not allowed
      builder.initialCapacity(16);
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testInitialCapacity_large() {
    CacheBuilder.newBuilder().initialCapacity(Integer.MAX_VALUE);
    // that the builder didn't blow up is enough;
    // don't actually create this monster!
  }

  @Test
  public void testConcurrencyLevel_zero() {
    CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder();
    try {
      builder.concurrencyLevel(0);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testConcurrencyLevel_setTwice() {
    CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder().concurrencyLevel(16);
    try {
      // even to the same value is not allowed
      builder.concurrencyLevel(16);
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testConcurrencyLevel_large() {
    CacheBuilder.newBuilder().concurrencyLevel(Integer.MAX_VALUE);
    // don't actually build this beast
  }

  @Test
  public void testMaximumSize_negative() {
    CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder();
    try {
      builder.maximumSize(-1);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testMaximumSize_setTwice() {
    CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder().maximumSize(16);
    try {
      // even to the same value is not allowed
      builder.maximumSize(16);
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testMaximumSize_andWeight() {
    CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder().maximumSize(16);
    try {
      builder.maximumWeight(16);
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testMaximumWeight_negative() {
    CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder();
    try {
      builder.maximumWeight(-1);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testMaximumWeight_setTwice() {
    CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder().maximumWeight(16);
    try {
      // even to the same value is not allowed
      builder.maximumWeight(16);
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      builder.maximumSize(16);
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testMaximumWeight_withoutWeigher() {
    CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder().maximumWeight(1);
    try {
      builder.build(identityLoader());
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testWeigher_withoutMaximumWeight() {
    CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder().weigher(constantWeigher(42));
    try {
      builder.build(identityLoader());
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testWeigher_withMaximumSize() {
    try {
      CacheBuilder.newBuilder().weigher(constantWeigher(42)).maximumSize(1);
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      CacheBuilder.newBuilder().maximumSize(1).weigher(constantWeigher(42));
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testKeyStrengthSetTwice() {
    CacheBuilder<Object, Object> builder1 = CacheBuilder.newBuilder().weakKeys();
    try {
      builder1.weakKeys();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testValueStrengthSetTwice() {
    CacheBuilder<Object, Object> builder1 = CacheBuilder.newBuilder().weakValues();
    try {
      builder1.weakValues();
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      builder1.softValues();
      fail();
    } catch (IllegalStateException expected) {
    }

    CacheBuilder<Object, Object> builder2 = CacheBuilder.newBuilder().softValues();
    try {
      builder2.softValues();
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      builder2.weakValues();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testLargeDurations() {
    java.time.Duration threeHundredYears = java.time.Duration.ofDays(365 * 300);
    CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder();
    try {
      builder.expireAfterWrite(threeHundredYears);
      fail();
    } catch (ArithmeticException expected) {
    }
    try {
      builder.expireAfterAccess(threeHundredYears);
      fail();
    } catch (ArithmeticException expected) {
    }
    try {
      builder.refreshAfterWrite(threeHundredYears);
      fail();
    } catch (ArithmeticException expected) {
    }
  }

  @Test
  public void testTimeToLive_negative() {
    CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder();
    try {
      builder.expireAfterWrite(-1, SECONDS);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testTimeToLive_negative_duration() {
    CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder();
    try {
      builder.expireAfterWrite(java.time.Duration.ofSeconds(-1));
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testTimeToLive_small() {
    CacheBuilder.newBuilder().expireAfterWrite(1, NANOSECONDS).build(identityLoader());
    // well, it didn't blow up.
  }

  @Test
  public void testTimeToLive_setTwice() {
    CacheBuilder<Object, Object> builder =
        CacheBuilder.newBuilder().expireAfterWrite(3600, SECONDS);
    try {
      // even to the same value is not allowed
      builder.expireAfterWrite(3600, SECONDS);
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testTimeToLive_setTwice_duration() {
    CacheBuilder<Object, Object> builder =
        CacheBuilder.newBuilder().expireAfterWrite(java.time.Duration.ofSeconds(3600));
    try {
      // even to the same value is not allowed
      builder.expireAfterWrite(java.time.Duration.ofSeconds(3600));
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testTimeToIdle_negative() {
    CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder();
    try {
      builder.expireAfterAccess(-1, SECONDS);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testTimeToIdle_negative_duration() {
    CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder();
    try {
      builder.expireAfterAccess(java.time.Duration.ofSeconds(-1));
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testTimeToIdle_small() {
    CacheBuilder.newBuilder().expireAfterAccess(1, NANOSECONDS).build(identityLoader());
    // well, it didn't blow up.
  }

  @Test
  public void testTimeToIdle_setTwice() {
    CacheBuilder<Object, Object> builder =
        CacheBuilder.newBuilder().expireAfterAccess(3600, SECONDS);
    try {
      // even to the same value is not allowed
      builder.expireAfterAccess(3600, SECONDS);
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testTimeToIdle_setTwice_duration() {
    CacheBuilder<Object, Object> builder =
        CacheBuilder.newBuilder().expireAfterAccess(java.time.Duration.ofSeconds(3600));
    try {
      // even to the same value is not allowed
      builder.expireAfterAccess(java.time.Duration.ofSeconds(3600));
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testTimeToIdleAndToLive() {
    CacheBuilder.newBuilder()
        .expireAfterWrite(1, NANOSECONDS)
        .expireAfterAccess(1, NANOSECONDS)
        .build(identityLoader());
    // well, it didn't blow up.
  }

  @Test
  public void testRefresh_zero() {
    CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder();
    try {
      builder.refreshAfterWrite(0, SECONDS);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testRefresh_zero_duration() {
    CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder();
    try {
      builder.refreshAfterWrite(java.time.Duration.ZERO);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testRefresh_setTwice() {
    CacheBuilder<Object, Object> builder =
        CacheBuilder.newBuilder().refreshAfterWrite(3600, SECONDS);
    try {
      // even to the same value is not allowed
      builder.refreshAfterWrite(3600, SECONDS);
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testRefresh_setTwice_duration() {
    CacheBuilder<Object, Object> builder =
        CacheBuilder.newBuilder().refreshAfterWrite(java.time.Duration.ofSeconds(3600));
    try {
      // even to the same value is not allowed
      builder.refreshAfterWrite(java.time.Duration.ofSeconds(3600));
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testTicker_setTwice() {
    Ticker testTicker = Ticker.systemTicker();
    CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder().ticker(testTicker);
    try {
      // even to the same instance is not allowed
      builder.ticker(testTicker);
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testRemovalListener_setTwice() {
    RemovalListener<Object, Object> testListener = nullRemovalListener();
    CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder().removalListener(testListener);
    try {
      // even to the same instance is not allowed
      builder = builder.removalListener(testListener);
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testKeyEquivalence_setTwice() {
    // Lava: keyEquivalence and valueEquivalence are package-private, as in Guava
    Equivalence<Object> equivalence = Equivalence.identity();
    CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder().keyEquivalence(equivalence);
    try {
      builder.keyEquivalence(equivalence);
      fail();
    } catch (IllegalStateException expected) {
    }
    assertEquals("CacheBuilder{keyEquivalence}", builder.toString());
  }

  @Test
  public void testValueEquivalence_setTwice() {
    Equivalence<Object> equivalence = Equivalence.equals();
    CacheBuilder<Object, Object> builder =
        CacheBuilder.newBuilder().valueEquivalence(equivalence);
    try {
      builder.valueEquivalence(equivalence);
      fail();
    } catch (IllegalStateException expected) {
    }
    assertEquals("CacheBuilder{valueEquivalence}", builder.toString());
  }

  @Test
  public void testValuesIsNotASet() {
    assertFalse(CacheBuilder.newBuilder().build().asMap().values() instanceof Set);
  }

  @Test
  public void testNullCache() {
    CountingRemovalListener<Object, Object> listener = countingRemovalListener();
    LoadingCache<Object, Object> nullCache =
        CacheBuilder.newBuilder().maximumSize(0).removalListener(listener).build(identityLoader());
    assertEquals(0, nullCache.size());
    Object key = new Object();
    assertSame(key, nullCache.getUnchecked(key));
    assertEquals(1, listener.getCount());
    assertEquals(0, nullCache.size());
    CacheTesting.checkEmpty(nullCache.asMap());
  }

  @Test
  public void testRemovalNotification_clear() {
    // If a clear() happens while a computation is pending, we should not get a removal
    // notification for it.
    // Lava: single-threaded, so the clear() is made by the loader itself while it computes "b"
    // (Guava's test cleared from another thread while the loader was blocked on a latch).

    final AtomicBoolean shouldClear = new AtomicBoolean(false);
    final AtomicReference<LoadingCache<String, String>> cacheRef = new AtomicReference<>();
    CacheLoader<String, String> computingFunction =
        new CacheLoader<String, String>() {
          @Override
          public String load(String key) {
            if (shouldClear.get()) {
              cacheRef.get().invalidateAll();
            }
            return key;
          }
        };
    QueuingRemovalListener<String, String> listener = queuingRemovalListener();

    final LoadingCache<String, String> cache =
        CacheBuilder.newBuilder()
            .concurrencyLevel(1)
            .removalListener(listener)
            .build(computingFunction);
    cacheRef.set(cache);

    // seed the map, so it is not empty when cleared
    cache.getUnchecked("a");
    shouldClear.set(true);

    assertEquals("b", cache.getUnchecked("b"));

    // At this point, the listener should be holding the seed value (a -> a), and the map should
    // contain the computed value (b -> b), since the clear() happened before the computation
    // completed.
    assertEquals(1, listener.size());
    RemovalNotification<String, String> notification = listener.remove();
    assertEquals("a", notification.getKey());
    assertEquals("a", notification.getValue());
    assertEquals(RemovalCause.EXPLICIT, notification.getCause());
    assertEquals(1, cache.size());
    shouldClear.set(false);
    assertEquals("b", cache.getUnchecked("b"));
    assertTrue(listener.isEmpty());
  }

  /**
   * Calls get() repeatedly with a loader that fails, returns null or loads, and tests that all of
   * the removed entries (removed because of size limits or expiration) trigger appropriate removal
   * notifications.
   *
   * <p>Lava: a single-threaded version of Guava's multi-threaded "basher" test, with the same
   * checks. A fake ticker advances with every get, so that entries both expire and are evicted.
   * (Guava's multi-threaded {@code testRemovalNotification_clear_basher} is dropped.)
   */
  @Test
  public void testRemovalNotification_get_basher() {
    int nGets = 200000;
    final int nUniqueKeys = 10000;
    final Random random = new Random(2018);
    FakeTicker ticker = new FakeTicker();

    QueuingRemovalListener<String, String> removalListener = queuingRemovalListener();
    final AtomicInteger computeCount = new AtomicInteger();
    final AtomicInteger exceptionCount = new AtomicInteger();
    final AtomicInteger computeNullCount = new AtomicInteger();
    CacheLoader<String, String> countingIdentityLoader =
        new CacheLoader<String, String>() {
          @Override
          public String load(String key) {
            int behavior = random.nextInt(4);
            if (behavior == 0) { // throw an exception
              exceptionCount.incrementAndGet();
              throw new RuntimeException("fake exception for test");
            } else if (behavior == 1) { // return null
              computeNullCount.incrementAndGet();
              return null;
            } else {
              computeCount.incrementAndGet();
              return key;
            }
          }
        };
    final LoadingCache<String, String> cache =
        CacheBuilder.newBuilder()
            .recordStats()
            .concurrencyLevel(2)
            .expireAfterWrite(100, MILLISECONDS)
            .ticker(ticker)
            .removalListener(removalListener)
            .maximumSize(5000)
            .build(countingIdentityLoader);

    for (int i = 0; i < nGets; i++) {
      ticker.advance(20, MICROSECONDS);
      try {
        cache.getUnchecked("key" + random.nextInt(nUniqueKeys));
      } catch (RuntimeException e) {
      }
    }
    cache.cleanUp();

    // Verify that each received removal notification was valid
    for (RemovalNotification<String, String> notification : removalListener) {
      assertEquals("Invalid removal notification", notification.getKey(), notification.getValue());
      assertTrue(
          notification.getCause() == RemovalCause.SIZE
              || notification.getCause() == RemovalCause.EXPIRED);
    }

    CacheStats stats = cache.stats();
    assertEquals(removalListener.size(), stats.evictionCount());
    assertEquals(computeCount.get(), stats.loadSuccessCount());
    assertEquals(exceptionCount.get() + computeNullCount.get(), stats.loadExceptionCount());
    // each computed value is still in the cache, or was passed to the removal listener
    assertEquals(computeCount.get(), cache.size() + removalListener.size());
    CacheTesting.checkValidState(cache);
  }

  @Test
  public void testNullParameters() throws Exception {
    NullPointerTester tester = new NullPointerTester();
    CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder();
    tester.testAllPublicInstanceMethods(builder);
  }
}
