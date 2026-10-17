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

import static com.simplelibs.lava.cache.CacheBuilder.NULL_TICKER;
import static com.simplelibs.lava.cache.TestingCacheLoaders.identityLoader;
import static com.simplelibs.lava.cache.TestingRemovalListeners.countingRemovalListener;
import static com.simplelibs.lava.cache.TestingRemovalListeners.queuingRemovalListener;
import static com.simplelibs.lava.cache.TestingWeighers.constantWeigher;
import static com.simplelibs.lava.collect.Maps.immutableEntry;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.simplelibs.lava.base.Equivalence;
import com.simplelibs.lava.base.Ticker;
import com.simplelibs.lava.cache.LocalCache.Strength;
import com.simplelibs.lava.cache.TestingCacheLoaders.CountingLoader;
import com.simplelibs.lava.cache.TestingRemovalListeners.CountingRemovalListener;
import com.simplelibs.lava.cache.TestingRemovalListeners.QueuingRemovalListener;
import com.simplelibs.lava.collect.ImmutableList;
import com.simplelibs.lava.collect.ImmutableMap;
import com.simplelibs.lava.collect.ImmutableSet;
import com.simplelibs.lava.collect.Maps;
import com.simplelibs.lava.testing.FakeTicker;
import com.simplelibs.lava.testing.LavaTestCase;
import com.simplelibs.lava.testing.NullPointerTester;
import com.simplelibs.lava.testing.TestLogHandler;
import com.simplelibs.lava.util.concurrent.UncheckedExecutionException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;

/**
 * Tests of the {@code LocalCache} behind {@code CacheBuilder}.
 *
 * <p>Lava: Guava's test is mostly white-box, driving the segments, reference entries and queues
 * that Lava does not have. Kept here are the tests whose subject is observable behaviour, rewritten
 * on {@code LocalCache}'s map interface, {@code CacheBuilder} and the package-private
 * reclamation hooks {@code simulateKeyReclamation}/{@code simulateValueReclamation}:
 *
 * <ul>
 *   <li>the configuration that {@code CacheBuilder} hands to the cache;
 *   <li>loading, removal causes and notifications, listener exceptions, collected entries;
 *   <li>the map views ({@code values()}, {@code removeIf}, {@code getOrDefault});
 *   <li>recency, expiration and eviction order, checked against a simple model.
 * </ul>
 *
 * <p>Rewritten rather than ported: {@code testSegment*} (as {@code testCollectedValue_*} and {@code
 * testStoreLoadedValue_*}), {@code testRecordRead*}/{@code testRecordWrite} (as model-based checks
 * of the eviction order), {@code testDrain*ReferenceQueue*}, {@code testReclaim*}, {@code
 * testRemoveComputingValue} (as {@code testRemoveLoadingValue}), {@code testSetMaximumSize} and
 * {@code testRemovalListener_replaced_computing} (the other thread became a call from the loader).
 *
 * <p>Dropped, with the reason: the tests that poke at Guava's segments, reference entries and
 * queues ({@code testNewEntry}, {@code testCopyEntry}, {@code testExpand*}, {@code
 * testGetCausesExpansion}, {@code testPutCausesExpansion}, {@code testRemoveEntryFromChain}, {@code
 * testRemoveEntry}, {@code testClear}, {@code testDrainRecencyQueue*}, {@code testEntryFactory},
 * {@code testSetConcurrencyLevel}, {@code testSetInitialCapacity}), because Lava has none of them;
 * {@code testSerializationProxy*}, because Lava caches are not serializable; the {@code
 * MapTestSuiteBuilder} suites, replaced by {@code CacheAsMapContractTest}; and {@code
 * testCopyEntry_computing}, which copies an entry that another thread is loading.
 *
 * @author Charles Fry
 */
public class LocalCacheTest extends LavaTestCase {

  static final int SMALL_MAX_SIZE = 64 * 5; // Guava: DRAIN_THRESHOLD * 5

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
    LocalCache.logger.removeHandler(logHandler);
  }

  private Throwable popLoggedThrowable() {
    List<LogRecord> logRecords = logHandler.getStoredLogRecords();
    assertSame(1, logRecords.size());
    LogRecord logRecord = logRecords.get(0);
    logHandler.clear();
    return logRecord.getThrown();
  }

  private void checkNothingLogged() {
    assertTrue(logHandler.getStoredLogRecords().isEmpty());
  }

  private void checkLogged(Throwable t) {
    assertSame(t, popLoggedThrowable());
  }

  private static <K, V> LocalCache<K, V> makeLocalCache(
      CacheBuilder<? super K, ? super V> builder) {
    return new LocalCache<>(builder, null);
  }

  private static CacheBuilder<Object, Object> createCacheBuilder() {
    return CacheBuilder.newBuilder();
  }

  // constructor tests

  @Test
  public void testDefaults() {
    LocalCache<Object, Object> map = makeLocalCache(createCacheBuilder());

    assertSame(Strength.STRONG, map.keyStrength);
    assertSame(Strength.STRONG, map.valueStrength);
    assertSame(map.keyStrength.defaultEquivalence(), map.keyEquivalence);
    assertSame(map.valueStrength.defaultEquivalence(), map.valueEquivalence);

    assertEquals(0, map.expireAfterAccessNanos);
    assertEquals(0, map.expireAfterWriteNanos);
    assertEquals(0, map.refreshNanos);
    assertEquals(CacheBuilder.UNSET_INT, map.maxWeight);

    assertSame(CacheBuilder.NullListener.INSTANCE, map.removalListener);
    assertSame(NULL_TICKER, map.ticker);

    assertFalse(map.evictsBySize());
    assertFalse(map.customWeigher());
    assertFalse(map.expires());
    assertFalse(map.expiresAfterWrite());
    assertFalse(map.expiresAfterAccess());
    assertFalse(map.refreshes());
  }

  @Test
  public void testSetKeyEquivalence() {
    Equivalence<Object> testEquivalence =
        new Equivalence<Object>() {
          @Override
          protected boolean doEquivalent(Object a, Object b) {
            return false;
          }

          @Override
          protected int doHash(Object t) {
            return 0;
          }
        };

    LocalCache<Object, Object> map =
        makeLocalCache(createCacheBuilder().keyEquivalence(testEquivalence));
    assertSame(testEquivalence, map.keyEquivalence);
    assertSame(map.valueStrength.defaultEquivalence(), map.valueEquivalence);
  }

  @Test
  public void testSetValueEquivalence() {
    Equivalence<Object> testEquivalence =
        new Equivalence<Object>() {
          @Override
          protected boolean doEquivalent(Object a, Object b) {
            return false;
          }

          @Override
          protected int doHash(Object t) {
            return 0;
          }
        };

    LocalCache<Object, Object> map =
        makeLocalCache(createCacheBuilder().valueEquivalence(testEquivalence));
    assertSame(testEquivalence, map.valueEquivalence);
    assertSame(map.keyStrength.defaultEquivalence(), map.keyEquivalence);
  }

  @Test
  public void testSetMaximumSize() {
    // Lava: no segments, so the maximum size is the cache-wide maximum weight (each entry weighs 1)
    for (long maxSize : new long[] {0, 1, 2, 10, 99, Long.MAX_VALUE}) {
      LocalCache<Object, Object> map = makeLocalCache(createCacheBuilder().maximumSize(maxSize));
      assertTrue(map.evictsBySize());
      assertFalse(map.customWeigher());
      assertEquals(maxSize, map.maxWeight);

      map =
          makeLocalCache(
              createCacheBuilder().maximumWeight(maxSize).weigher(constantWeigher(1)));
      assertTrue(map.evictsBySize());
      assertTrue(map.customWeigher());
      assertEquals(maxSize, map.maxWeight);
    }
  }

  @Test
  public void testConcurrencyLevelHasNoEffect() {
    // Lava: concurrencyLevel is validated but, with one table and one eviction order, has no
    // effect: the eviction order is the exact LRU order of a concurrencyLevel(1) Guava cache.
    for (int concurrencyLevel : new int[] {1, 2, 3, 4, 16, 64, Integer.MAX_VALUE}) {
      LocalCache<Object, Object> map =
          makeLocalCache(createCacheBuilder().concurrencyLevel(concurrencyLevel).maximumSize(10));
      List<Object> keys = new ArrayList<>();
      for (int i = 0; i < 100; i++) {
        Object key = new Object();
        keys.add(key);
        map.put(key, i);
        assertEquals(Math.min(i + 1, 10), map.size());
      }
      assertEquals(ImmutableSet.copyOf(keys.subList(90, 100)), map.keySet());
    }
  }

  @Test
  public void testSetWeigher() {
    Weigher<Object, Object> testWeigher =
        new Weigher<Object, Object>() {
          @Override
          public int weigh(Object key, Object value) {
            return 42;
          }
        };
    LocalCache<Object, Object> map =
        makeLocalCache(createCacheBuilder().maximumWeight(1).weigher(testWeigher));
    assertSame(testWeigher, map.weigher);
  }

  @Test
  public void testSetWeakKeys() {
    LocalCache<Object, Object> map = makeLocalCache(createCacheBuilder().weakKeys());
    checkStrength(map, Strength.WEAK, Strength.STRONG);
  }

  @Test
  public void testSetWeakValues() {
    LocalCache<Object, Object> map = makeLocalCache(createCacheBuilder().weakValues());
    checkStrength(map, Strength.STRONG, Strength.WEAK);
  }

  @Test
  public void testSetSoftValues() {
    LocalCache<Object, Object> map = makeLocalCache(createCacheBuilder().softValues());
    checkStrength(map, Strength.STRONG, Strength.SOFT);
  }

  private static void checkStrength(
      LocalCache<Object, Object> map, Strength keyStrength, Strength valueStrength) {
    assertSame(keyStrength, map.keyStrength);
    assertSame(valueStrength, map.valueStrength);
    assertSame(keyStrength.defaultEquivalence(), map.keyEquivalence);
    assertSame(valueStrength.defaultEquivalence(), map.valueEquivalence);
  }

  @Test
  public void testSetExpireAfterWrite() {
    long duration = 42;
    TimeUnit unit = TimeUnit.SECONDS;
    LocalCache<Object, Object> map =
        makeLocalCache(createCacheBuilder().expireAfterWrite(duration, unit));
    assertEquals(unit.toNanos(duration), map.expireAfterWriteNanos);
    assertTrue(map.expires());
    assertTrue(map.expiresAfterWrite());
    assertFalse(map.expiresAfterAccess());
  }

  @Test
  public void testSetExpireAfterAccess() {
    long duration = 42;
    TimeUnit unit = TimeUnit.SECONDS;
    LocalCache<Object, Object> map =
        makeLocalCache(createCacheBuilder().expireAfterAccess(duration, unit));
    assertEquals(unit.toNanos(duration), map.expireAfterAccessNanos);
    assertTrue(map.expires());
    assertTrue(map.expiresAfterAccess());
    assertFalse(map.expiresAfterWrite());
  }

  @Test
  public void testSetRefresh() {
    long duration = 42;
    TimeUnit unit = TimeUnit.SECONDS;
    LocalCache<Object, Object> map =
        makeLocalCache(createCacheBuilder().refreshAfterWrite(duration, unit));
    assertEquals(unit.toNanos(duration), map.refreshNanos);
    assertTrue(map.refreshes());
  }

  @Test
  public void testSetRemovalListener() {
    RemovalListener<Object, Object> testListener = TestingRemovalListeners.nullRemovalListener();
    LocalCache<Object, Object> map =
        makeLocalCache(createCacheBuilder().removalListener(testListener));
    assertSame(testListener, map.removalListener);
  }

  @Test
  public void testSetTicker() {
    Ticker testTicker =
        new Ticker() {
          @Override
          public long read() {
            return 0;
          }
        };
    LocalCache<Object, Object> map = makeLocalCache(createCacheBuilder().ticker(testTicker));
    assertSame(testTicker, map.ticker);
  }

  // computation tests

  @Test
  public void testCompute() throws ExecutionException {
    CountingLoader loader = new CountingLoader();
    LocalCache<Object, Object> map = makeLocalCache(createCacheBuilder());
    assertEquals(0, loader.getCount());

    Object key = new Object();
    Object value = map.get(key, loader);
    assertEquals(1, loader.getCount());
    assertEquals(value, map.get(key, loader));
    assertEquals(1, loader.getCount());
  }

  /** Loads and hits through {@code get(key, loader)} move entries to the most recent position. */
  @Test
  public void testRecordReadOnCompute() throws ExecutionException {
    final CountingLoader loader = new CountingLoader();
    for (CacheBuilder<Object, Object> builder : allEvictingMakers()) {
      checkEvictionFollowsReads(
          builder,
          new ReadOp() {
            @Override
            public void read(LocalCache<Object, Object> map, Object key) throws ExecutionException {
              assertNotNull(map.get(key, loader));
            }
          },
          1);
    }
    assertEquals(0, loader.getCount()); // every read was a hit
  }

  @Test
  public void testRecordReadOnGet() throws ExecutionException {
    for (CacheBuilder<Object, Object> builder : allEvictingMakers()) {
      checkEvictionFollowsReads(
          builder,
          new ReadOp() {
            @Override
            public void read(LocalCache<Object, Object> map, Object key) {
              assertNotNull(map.get(key));
            }
          },
          2);
    }
  }

  @Test
  public void testRecordWrite() throws ExecutionException {
    // overwriting an entry (put, replace, replace(k, old, new) and a hit of putIfAbsent) makes it
    // the most recently used
    final List<Object> values = new ArrayList<>();
    for (CacheBuilder<Object, Object> builder : allEvictingMakers()) {
      final int[] choice = {0};
      checkEvictionFollowsReads(
          builder,
          new ReadOp() {
            @Override
            public void read(LocalCache<Object, Object> map, Object key) {
              Object value = new Object();
              values.add(value);
              switch (choice[0]++ % 4) {
                case 0:
                  assertNotNull(map.put(key, value));
                  break;
                case 1:
                  assertNotNull(map.replace(key, value));
                  break;
                case 2:
                  assertTrue(map.replace(key, map.get(key), value));
                  break;
                default:
                  assertNotNull(map.putIfAbsent(key, value));
                  break;
              }
            }
          },
          3);
    }
  }

  private interface ReadOp {
    void read(LocalCache<Object, Object> map, Object key) throws ExecutionException;
  }

  /**
   * Fills a cache with {@link #SMALL_MAX_SIZE} entries, then repeatedly applies {@code read} to a
   * random selection of keys and adds a new entry, which must evict exactly the least recently used
   * key of a model of the access order.
   */
  private void checkEvictionFollowsReads(
      CacheBuilder<Object, Object> builder, ReadOp read, long seed) throws ExecutionException {
    LocalCache<Object, Object> map = makeLocalCache(builder);
    Random random = new Random(seed);
    List<Object> accessOrder = new ArrayList<>(); // least recently used first
    List<Object> values = new ArrayList<>(); // keep the values (and keys) strongly reachable
    for (int i = 0; i < SMALL_MAX_SIZE; i++) {
      Object key = new Object();
      Object value = new Object();
      map.put(key, value);
      accessOrder.add(key);
      values.add(value);
    }
    assertEquals(SMALL_MAX_SIZE, map.size());

    for (int round = 0; round < 20; round++) {
      List<Object> shuffled = new ArrayList<>(accessOrder);
      Collections.shuffle(shuffled, random);
      for (Object key : shuffled.subList(0, random.nextInt(shuffled.size()))) {
        read.read(map, key);
        accessOrder.remove(key);
        accessOrder.add(key);
      }

      Object key = new Object();
      Object value = new Object();
      values.add(value);
      assertNull(map.put(key, value));
      accessOrder.add(key);
      Object evicted = accessOrder.remove(0);

      assertEquals(SMALL_MAX_SIZE, map.size());
      assertFalse(map.containsKey(evicted));
      assertEquals(ImmutableSet.copyOf(accessOrder), ImmutableSet.copyOf(map.keySet()));
    }
  }

  @Test
  public void testComputeExistingEntry() throws ExecutionException {
    CountingLoader loader = new CountingLoader();
    LocalCache<Object, Object> map = makeLocalCache(createCacheBuilder());
    assertEquals(0, loader.getCount());

    Object key = new Object();
    Object value = new Object();
    map.put(key, value);

    assertEquals(value, map.get(key, loader));
    assertEquals(0, loader.getCount());
  }

  @Test
  public void testComputePartiallyCollectedKey() throws ExecutionException {
    // Lava: the partially collected entry is simulated with the reclamation hook; it is reclaimed
    // at the start of the next operation, and the loader runs for the key.
    CacheBuilder<Object, Object> builder = createCacheBuilder().weakKeys();
    CountingLoader loader = new CountingLoader();
    LocalCache<Object, Object> map = makeLocalCache(builder);
    assertEquals(0, loader.getCount());

    Object key = new Object();
    Object value = new Object();
    map.put(key, value);

    assertSame(value, map.get(key, loader));
    assertEquals(0, loader.getCount());
    assertEquals(1, map.size());

    map.simulateKeyReclamation(key);
    assertNotSame(value, map.get(key, loader));
    assertEquals(1, loader.getCount());
    assertEquals(1, map.size());
  }

  @Test
  public void testComputePartiallyCollectedValue() throws ExecutionException {
    for (boolean enqueue : new boolean[] {true, false}) {
      for (CacheBuilder<Object, Object> builder :
          ImmutableList.of(createCacheBuilder().weakValues(), createCacheBuilder().softValues())) {
        CountingLoader loader = new CountingLoader();
        QueuingRemovalListener<Object, Object> listener = queuingRemovalListener();
        LocalCache<Object, Object> map = makeLocalCache(builder.removalListener(listener));
        assertEquals(0, loader.getCount());

        Object key = new Object();
        Object value = new Object();
        map.put(key, value);

        assertSame(value, map.get(key, loader));
        assertEquals(0, loader.getCount());
        assertEquals(1, map.size());

        CacheTesting.reclaimValue(map, key, enqueue);
        assertNotSame(value, map.get(key, loader));
        assertEquals(1, loader.getCount());
        assertEquals(1, map.size());
        assertNotified(listener, key, null, RemovalCause.COLLECTED);
        assertTrue(listener.isEmpty());
      }
    }
  }

  @Test
  public void testComputeExpiredEntry() throws ExecutionException {
    CacheBuilder<Object, Object> builder =
        createCacheBuilder().expireAfterWrite(1, TimeUnit.NANOSECONDS);
    CountingLoader loader = new CountingLoader();
    LocalCache<Object, Object> map = makeLocalCache(builder);
    assertEquals(0, loader.getCount());

    Object key = new Object();
    Object one = map.get(key, loader);
    assertEquals(1, loader.getCount());

    Object two = map.get(key, loader);
    assertNotSame(one, two);
    assertEquals(2, loader.getCount());
  }

  @Test
  public void testValues() {
    LocalCache<Object, Object> map = makeLocalCache(createCacheBuilder());
    map.put("foo", "bar");
    map.put("baz", "bar");
    map.put("quux", "quux");
    assertFalse(map.values() instanceof Set);
    assertTrue(map.values().removeAll(ImmutableSet.of("bar")));
    assertEquals(1, map.size());
  }

  @Test
  public void testComputeIfAbsent_RemovalListener() {
    List<RemovalNotification<Object, Object>> notifications = new ArrayList<>();
    RemovalListener<Object, Object> removalListener =
        new RemovalListener<Object, Object>() {
          @Override
          public void onRemoval(RemovalNotification<Object, Object> notification) {
            notifications.add(notification);
          }
        };
    Cache<Object, Object> cache =
        CacheBuilder.newBuilder().removalListener(removalListener).build();
    cache.put("a", "b");
    cache.asMap().computeIfAbsent("a", k -> "c");
    assertTrue(notifications.toString(), notifications.isEmpty());
  }

  @Test
  public void testCompute_removalNotifications() {
    // compute* notify like the other writes: REPLACED for a new value, and nothing when the
    // function returns the value that is already there
    // Lava: when the function removes the entry by returning null, the notification is EXPLICIT
    // (Guava 27.0.1 leaves the entry stuck in a loading state instead; see the package docs)
    QueuingRemovalListener<Object, Object> listener = queuingRemovalListener();
    LocalCache<Object, Object> map = makeLocalCache(createCacheBuilder().removalListener(listener));
    Object key = new Object();
    Object one = new Object();
    Object two = new Object();

    assertSame(one, map.compute(key, (k, v) -> one));
    assertTrue(listener.isEmpty());
    assertSame(one, map.compute(key, (k, v) -> v));
    assertTrue(listener.isEmpty());
    assertSame(two, map.compute(key, (k, v) -> two));
    assertNotified(listener, key, one, RemovalCause.REPLACED);
    assertSame(two, map.computeIfPresent(key, (k, v) -> v));
    assertTrue(listener.isEmpty());
    assertNull(map.computeIfPresent(key, (k, v) -> null));
    assertNotified(listener, key, two, RemovalCause.EXPLICIT);
    assertEquals(0, map.size());
    assertTrue(listener.isEmpty());
  }

  @Test
  public void testGetOrDefault() {
    LocalCache<Object, Object> map = makeLocalCache(createCacheBuilder().initialCapacity(1));
    map.put(1, 1);
    assertEquals(1, map.getOrDefault(1, 2));
    assertEquals(2, map.getOrDefault(2, 2));
  }

  @Test
  public void testRemoveIfWithConcurrentModification() {
    LocalCache<Integer, Integer> map = makeLocalCache(createCacheBuilder().initialCapacity(1));
    map.put(1, 1);
    map.put(2, 1);
    map.put(3, 1);
    map.entrySet()
        .removeIf(
            entry -> {
              if (entry.getValue().equals(1)) {
                map.put(entry.getKey(), 2);
                return true;
              } else {
                return false;
              }
            });
    assertEquals(3, map.size());
    assertFalse(map.containsValue(1));
  }

  @Test
  public void testRemoveIfWithConcurrentRemoval() {
    LocalCache<Integer, Integer> map = makeLocalCache(createCacheBuilder().initialCapacity(1));
    map.put(0, 1);
    map.put(1, 1);
    map.put(2, 1);
    map.entrySet()
        .removeIf(
            entry -> {
              assertThat(entry.getValue()).isNotNull();
              map.remove((entry.getKey() + 1) % 3);
              return false;
            });
    assertEquals(1, map.size());
  }

  @Test
  public void testRemoveIf_valuesAndKeySet() {
    LocalCache<Integer, Integer> map = makeLocalCache(createCacheBuilder());
    for (int i = 0; i < 10; i++) {
      map.put(i, i * i);
    }
    assertTrue(map.values().removeIf(v -> v % 2 == 0)); // keeps the odd squares: 1, 9, 25, 49, 81
    assertEquals(ImmutableSet.of(1, 3, 5, 7, 9), ImmutableSet.copyOf(map.keySet()));
    assertFalse(map.values().removeIf(v -> v > 1000));
    assertTrue(map.keySet().removeIf(k -> k > 5));
    assertEquals(ImmutableMap.of(1, 1, 3, 9, 5, 25), ImmutableMap.copyOf(map));
    assertTrue(map.entrySet().removeIf(e -> e.getKey() == 3));
    assertEquals(ImmutableMap.of(1, 1, 5, 25), ImmutableMap.copyOf(map));
    try {
      map.values().removeIf(null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  // Removal listener tests

  @Test
  public void testRemovalListenerCheckedException() {
    final RuntimeException e = new RuntimeException();
    RemovalListener<Object, Object> listener =
        new RemovalListener<Object, Object>() {
          @Override
          public void onRemoval(RemovalNotification<Object, Object> notification) {
            throw e;
          }
        };

    CacheBuilder<Object, Object> builder = createCacheBuilder().removalListener(listener);
    final LocalCache<Object, Object> cache = makeLocalCache(builder);
    Object key = new Object();
    cache.put(key, new Object());
    checkNothingLogged();

    cache.remove(key);
    List<LogRecord> records = logHandler.getStoredLogRecords();
    assertEquals(1, records.size());
    assertEquals(Level.WARNING, records.get(0).getLevel());
    assertEquals("Exception thrown by removal listener", records.get(0).getMessage());
    assertEquals("com.simplelibs.lava.cache.LocalCache", records.get(0).getLoggerName());
    checkLogged(e);
  }

  @Test
  public void testRemovalListenerError() {
    // an Error thrown by a listener is logged too, and later notifications are still delivered
    final Error error = new AssertionError("listener");
    final List<Object> delivered = new ArrayList<>();
    RemovalListener<Object, Object> listener =
        new RemovalListener<Object, Object>() {
          @Override
          public void onRemoval(RemovalNotification<Object, Object> notification) {
            delivered.add(notification.getKey());
            if (delivered.size() == 1) {
              throw error;
            }
          }
        };
    LocalCache<Object, Object> cache = makeLocalCache(createCacheBuilder().removalListener(listener));
    cache.put("a", 1);
    cache.put("b", 2);
    cache.clear();
    assertEquals(2, delivered.size());
    checkLogged(error);
  }

  @Test
  public void testRemovalListener_reentrant() {
    // a listener may call back into the cache
    // Lava: notifications stay in FIFO order and a notification caused by a re-entrant call is
    // delivered after the running listener returns. (Guava would deliver "b" from within the
    // listener's remove call, so its log is [a:EXPLICIT, b:EXPLICIT, size=1].)
    final List<String> log = new ArrayList<>();
    final LocalCache<Object, Object>[] cacheRef = newCacheArray();
    RemovalListener<Object, Object> listener =
        new RemovalListener<Object, Object>() {
          @Override
          public void onRemoval(RemovalNotification<Object, Object> notification) {
            log.add(notification.getKey() + ":" + notification.getCause());
            if (notification.getKey().equals("a")) {
              cacheRef[0].put("c", 3);
              cacheRef[0].remove("b");
              log.add("size=" + cacheRef[0].size());
            }
          }
        };
    cacheRef[0] = makeLocalCache(createCacheBuilder().removalListener(listener));
    cacheRef[0].put("a", 1);
    cacheRef[0].put("b", 2);
    cacheRef[0].remove("a");
    assertEquals(ImmutableList.of("a:EXPLICIT", "size=1", "b:EXPLICIT"), log);

    assertEquals(ImmutableMap.of("c", 3), ImmutableMap.copyOf(cacheRef[0]));
  }

  @SuppressWarnings("unchecked")
  private static LocalCache<Object, Object>[] newCacheArray() {
    return (LocalCache<Object, Object>[]) new LocalCache<?, ?>[1];
  }

  @Test
  public void testRemovalListener_replaced_computing() throws ExecutionException {
    // Lava: single-threaded, so the put() happens inside the loader. As in Guava, the put wins and
    // the loaded value is discarded with REPLACED (while still being returned to the loader's
    // caller).
    final Object computedObject = new Object();
    final Object one = new Object();
    final Object two = new Object();
    final LocalCache<Object, Object>[] mapRef = newCacheArray();

    final CacheLoader<Object, Object> loader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object key) {
            mapRef[0].put(one, two);
            return computedObject;
          }
        };

    QueuingRemovalListener<Object, Object> listener = queuingRemovalListener();
    CacheBuilder<Object, Object> builder = createCacheBuilder().removalListener(listener);
    final LocalCache<Object, Object> map = makeLocalCache(builder);
    mapRef[0] = map;
    assertTrue(listener.isEmpty());

    assertSame(computedObject, map.get(one, loader));
    assertSame(two, map.get(one));
    assertEquals(1, map.size());

    map.cleanUp(); // force notifications
    assertNotified(listener, one, computedObject, RemovalCause.REPLACED);
    assertTrue(listener.isEmpty());
  }

  @Test
  public void testStoreLoadedValue_inactive() throws ExecutionException {
    // the entry is removed while it loads: the loaded value is stored, without any notification
    QueuingRemovalListener<Object, Object> listener = queuingRemovalListener();
    final LocalCache<Object, Object>[] mapRef = newCacheArray();
    final Object key = new Object();
    final Object value = new Object();
    CacheLoader<Object, Object> loader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object k) {
            mapRef[0].clear();
            mapRef[0].remove(k);
            return value;
          }
        };
    LocalCache<Object, Object> map = makeLocalCache(createCacheBuilder().removalListener(listener));
    mapRef[0] = map;

    assertSame(value, map.get(key, loader));
    assertSame(value, map.get(key));
    assertEquals(1, map.size());
    assertTrue(listener.isEmpty());
  }

  @Test
  public void testStoreLoadedValue_refreshReplacesOldValue() throws ExecutionException {
    QueuingRemovalListener<Object, Object> listener = queuingRemovalListener();
    final Object key = new Object();
    final Object value3 = new Object();
    final Object value4 = new Object();
    CacheLoader<Object, Object> loader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object k) {
            return value4;
          }
        };
    LocalCache<Object, Object> map =
        new LocalCache<>(createCacheBuilder().removalListener(listener), loader);

    map.put(key, value3);
    map.refresh(key);
    assertSame(value4, map.get(key));
    assertEquals(1, map.size());
    assertNotified(listener, key, value3, RemovalCause.REPLACED);
    assertTrue(listener.isEmpty());
  }

  @Test
  public void testRefresh_duplicate() {
    // a refresh of a key that is already loading is a no-op, even when asked from the loader
    final LocalCache<Object, Object>[] mapRef = newCacheArray();
    final CountingLoader[] loaderRef = new CountingLoader[1];
    final Object key = new Object();
    loaderRef[0] =
        new CountingLoader() {
          @Override
          public Object load(Object from) {
            mapRef[0].refresh(from);
            return super.load(from);
          }
        };
    LocalCache<Object, Object> map = new LocalCache<>(createCacheBuilder(), loaderRef[0]);
    mapRef[0] = map;

    map.refresh(key);
    assertEquals(1, loaderRef[0].getCount());
    assertEquals(1, map.size());
    checkNothingLogged();
  }

  @Test
  public void testRemovalListener_explicit() {
    QueuingRemovalListener<Object, Object> listener = queuingRemovalListener();
    LocalCache<Object, Object> map = makeLocalCache(createCacheBuilder().removalListener(listener));
    assertTrue(listener.isEmpty());

    Object one = new Object();
    Object two = new Object();
    Object three = new Object();
    Object four = new Object();
    Object five = new Object();
    Object six = new Object();

    map.put(one, two);
    map.remove(one);
    assertNotified(listener, one, two, RemovalCause.EXPLICIT);

    map.put(two, three);
    map.remove(two, three);
    assertNotified(listener, two, three, RemovalCause.EXPLICIT);

    map.put(three, four);
    Iterator<?> i = map.entrySet().iterator();
    i.next();
    i.remove();
    assertNotified(listener, three, four, RemovalCause.EXPLICIT);

    map.put(four, five);
    i = map.keySet().iterator();
    i.next();
    i.remove();
    assertNotified(listener, four, five, RemovalCause.EXPLICIT);

    map.put(five, six);
    i = map.values().iterator();
    i.next();
    i.remove();
    assertNotified(listener, five, six, RemovalCause.EXPLICIT);

    assertTrue(listener.isEmpty());
  }

  @Test
  public void testRemovalListener_replaced() {
    QueuingRemovalListener<Object, Object> listener = queuingRemovalListener();
    LocalCache<Object, Object> map = makeLocalCache(createCacheBuilder().removalListener(listener));
    assertTrue(listener.isEmpty());

    Object one = new Object();
    Object two = new Object();
    Object three = new Object();
    Object four = new Object();
    Object five = new Object();
    Object six = new Object();

    map.put(one, two);
    map.put(one, three);
    assertNotified(listener, one, two, RemovalCause.REPLACED);

    Map<Object, Object> newMap = ImmutableMap.of(one, four);
    map.putAll(newMap);
    assertNotified(listener, one, three, RemovalCause.REPLACED);

    map.replace(one, five);
    assertNotified(listener, one, four, RemovalCause.REPLACED);

    map.replace(one, five, six);
    assertNotified(listener, one, five, RemovalCause.REPLACED);
  }

  @Test
  public void testRemovalListener_collected() {
    QueuingRemovalListener<Object, Object> listener = queuingRemovalListener();
    LocalCache<Object, Object> map =
        makeLocalCache(createCacheBuilder().softValues().removalListener(listener));
    assertTrue(listener.isEmpty());

    Object one = new Object();
    Object two = new Object();
    Object three = new Object();

    map.put(one, two);
    map.put(two, three);
    assertTrue(listener.isEmpty());

    // Lava: the collected reference is cleared (Guava's test left the value in place), so the
    // notification carries a null value
    map.simulateValueReclamation(one);
    map.cleanUp();
    assertNotified(listener, one, null, RemovalCause.COLLECTED);

    assertTrue(listener.isEmpty());
    assertEquals(1, map.size());
    assertSame(three, map.get(two));
  }

  @Test
  public void testRemovalListener_expired() {
    FakeTicker ticker = new FakeTicker();
    QueuingRemovalListener<Object, Object> listener = queuingRemovalListener();
    LocalCache<Object, Object> map =
        makeLocalCache(
            createCacheBuilder()
                .expireAfterWrite(3, TimeUnit.NANOSECONDS)
                .ticker(ticker)
                .removalListener(listener));
    assertTrue(listener.isEmpty());

    Object one = new Object();
    Object two = new Object();
    Object three = new Object();
    Object four = new Object();
    Object five = new Object();

    map.put(one, two);
    ticker.advance(1);
    map.put(two, three);
    ticker.advance(1);
    map.put(three, four);
    assertTrue(listener.isEmpty());
    ticker.advance(1);
    map.put(four, five);
    assertNotified(listener, one, two, RemovalCause.EXPIRED);

    assertTrue(listener.isEmpty());
  }

  @Test
  public void testRemovalListener_size() {
    QueuingRemovalListener<Object, Object> listener = queuingRemovalListener();
    LocalCache<Object, Object> map =
        makeLocalCache(createCacheBuilder().maximumSize(2).removalListener(listener));
    assertTrue(listener.isEmpty());

    Object one = new Object();
    Object two = new Object();
    Object three = new Object();
    Object four = new Object();

    map.put(one, two);
    map.put(two, three);
    assertTrue(listener.isEmpty());
    map.put(three, four);
    assertNotified(listener, one, two, RemovalCause.SIZE);

    assertTrue(listener.isEmpty());
  }

  static <K, V> void assertNotified(
      QueuingRemovalListener<K, V> listener, K key, V value, RemovalCause cause) {
    RemovalNotification<K, V> notification = listener.remove();
    assertSame(key, notification.getKey());
    assertSame(value, notification.getValue());
    assertSame(cause, notification.getCause());
  }

  @Test
  public void testClear_notification() {
    QueuingRemovalListener<Object, Object> listener = queuingRemovalListener();
    LocalCache<Object, Object> map =
        makeLocalCache(
            createCacheBuilder()
                .maximumSize(SMALL_MAX_SIZE)
                .expireAfterWrite(99999, SECONDS)
                .removalListener(listener));

    Object key = new Object();
    Object value = new Object();
    map.put(key, value);
    assertEquals(1, map.size());

    map.clear();
    assertEquals(0, map.size());
    assertTrue(map.isEmpty());
    assertNull(map.get(key));
    assertNotified(listener, key, value, RemovalCause.EXPLICIT);
    assertTrue(listener.isEmpty());

    // the cleared entry no longer counts toward the maximum size
    for (int i = 0; i < SMALL_MAX_SIZE; i++) {
      map.put(new Object(), new Object());
    }
    assertEquals(SMALL_MAX_SIZE, map.size());
    assertTrue(listener.isEmpty());
  }

  // Operations on entries whose value was collected. Lava: simulated by clearing the reference,
  // either with the reclamation hook, which also enqueues it (so that the next operation reclaims
  // the entry before it does anything else), or as the garbage collector does just before the
  // reference is enqueued, leaving the entry in the cache with a collected value (which Guava's
  // CacheTesting.simulateValueReclamation did).

  /** The builders that give a cache weak or soft values, with the expiration Guava's tests set. */
  private static Iterable<CacheBuilder<Object, Object>> valueReferenceMakers() {
    return ImmutableList.of(
        createCacheBuilder().weakValues().expireAfterAccess(99999, SECONDS),
        createCacheBuilder().softValues().expireAfterAccess(99999, SECONDS),
        createCacheBuilder().weakValues().weakKeys(),
        createCacheBuilder().softValues().maximumSize(SMALL_MAX_SIZE));
  }

  @Test
  public void testCollectedValue_replaceValue() {
    checkCollectedValue_replaceValue(true);
    checkCollectedValue_replaceValue(false);
  }

  private void checkCollectedValue_replaceValue(boolean enqueue) {
    for (CacheBuilder<Object, Object> builder : valueReferenceMakers()) {
      QueuingRemovalListener<Object, Object> listener = queuingRemovalListener();
      LocalCache<Object, Object> map = makeLocalCache(builder.removalListener(listener));

      Object key = new Object();
      Object oldValue = new Object();
      Object newValue = new Object();

      // no entry
      assertFalse(map.replace(key, oldValue, newValue));
      assertEquals(0, map.size());

      // same value
      map.put(key, oldValue);
      assertEquals(1, map.size());
      assertSame(oldValue, map.get(key));
      assertTrue(map.replace(key, oldValue, newValue));
      assertEquals(1, map.size());
      assertSame(newValue, map.get(key));
      assertNotified(listener, key, oldValue, RemovalCause.REPLACED);

      // different value
      assertFalse(map.replace(key, oldValue, newValue));
      assertEquals(1, map.size());
      assertSame(newValue, map.get(key));
      assertTrue(listener.isEmpty());

      // cleared
      map.put(key, oldValue);
      assertNotified(listener, key, newValue, RemovalCause.REPLACED);
      assertSame(oldValue, map.get(key));
      CacheTesting.reclaimValue(map, key, enqueue);
      assertFalse(map.replace(key, oldValue, newValue));
      assertEquals(0, map.size());
      assertNull(map.get(key));
      assertNotified(listener, key, null, RemovalCause.COLLECTED);
      assertTrue(listener.isEmpty());
    }
  }

  @Test
  public void testCollectedValue_replace() {
    checkCollectedValue_replace(true);
    checkCollectedValue_replace(false);
  }

  private void checkCollectedValue_replace(boolean enqueue) {
    for (CacheBuilder<Object, Object> builder : valueReferenceMakers()) {
      QueuingRemovalListener<Object, Object> listener = queuingRemovalListener();
      LocalCache<Object, Object> map = makeLocalCache(builder.removalListener(listener));

      Object key = new Object();
      Object oldValue = new Object();
      Object newValue = new Object();

      // no entry
      assertNull(map.replace(key, newValue));
      assertEquals(0, map.size());

      // same key
      map.put(key, oldValue);
      assertEquals(1, map.size());
      assertSame(oldValue, map.get(key));
      assertSame(oldValue, map.replace(key, newValue));
      assertEquals(1, map.size());
      assertSame(newValue, map.get(key));
      assertNotified(listener, key, oldValue, RemovalCause.REPLACED);

      // cleared
      map.put(key, oldValue);
      assertNotified(listener, key, newValue, RemovalCause.REPLACED);
      assertSame(oldValue, map.get(key));
      CacheTesting.reclaimValue(map, key, enqueue);
      assertNull(map.replace(key, newValue));
      assertEquals(0, map.size());
      assertNull(map.get(key));
      assertNotified(listener, key, null, RemovalCause.COLLECTED);
      assertTrue(listener.isEmpty());
    }
  }

  @Test
  public void testCollectedValue_put() {
    checkCollectedValue_put(true);
    checkCollectedValue_put(false);
  }

  private void checkCollectedValue_put(boolean enqueue) {
    for (CacheBuilder<Object, Object> builder : valueReferenceMakers()) {
      QueuingRemovalListener<Object, Object> listener = queuingRemovalListener();
      LocalCache<Object, Object> map = makeLocalCache(builder.removalListener(listener));

      Object key = new Object();
      Object oldValue = new Object();
      Object newValue = new Object();

      // no entry
      assertEquals(0, map.size());
      assertNull(map.put(key, oldValue));
      assertEquals(1, map.size());

      // same key
      assertSame(oldValue, map.put(key, newValue));
      assertEquals(1, map.size());
      assertSame(newValue, map.get(key));
      assertNotified(listener, key, oldValue, RemovalCause.REPLACED);

      // cleared
      map.put(key, oldValue);
      assertNotified(listener, key, newValue, RemovalCause.REPLACED);
      assertSame(oldValue, map.get(key));
      CacheTesting.reclaimValue(map, key, enqueue);
      assertNull(map.put(key, newValue));
      assertEquals(1, map.size());
      assertSame(newValue, map.get(key));
      assertNotified(listener, key, null, RemovalCause.COLLECTED);
      assertTrue(listener.isEmpty());
    }
  }

  @Test
  public void testCollectedValue_putIfAbsent() {
    checkCollectedValue_putIfAbsent(true);
    checkCollectedValue_putIfAbsent(false);
  }

  private void checkCollectedValue_putIfAbsent(boolean enqueue) {
    for (CacheBuilder<Object, Object> builder : valueReferenceMakers()) {
      QueuingRemovalListener<Object, Object> listener = queuingRemovalListener();
      LocalCache<Object, Object> map = makeLocalCache(builder.removalListener(listener));

      Object key = new Object();
      Object oldValue = new Object();
      Object newValue = new Object();

      // no entry
      assertEquals(0, map.size());
      assertNull(map.putIfAbsent(key, oldValue));
      assertEquals(1, map.size());

      // same key
      assertSame(oldValue, map.putIfAbsent(key, newValue));
      assertEquals(1, map.size());
      assertSame(oldValue, map.get(key));
      assertTrue(listener.isEmpty());

      // cleared
      CacheTesting.reclaimValue(map, key, enqueue);
      assertNull(map.putIfAbsent(key, newValue));
      assertEquals(1, map.size());
      assertSame(newValue, map.get(key));
      assertNotified(listener, key, null, RemovalCause.COLLECTED);
      assertTrue(listener.isEmpty());
    }
  }

  @Test
  public void testCollectedValue_remove() {
    checkCollectedValue_remove(true);
    checkCollectedValue_remove(false);
  }

  private void checkCollectedValue_remove(boolean enqueue) {
    for (CacheBuilder<Object, Object> builder : valueReferenceMakers()) {
      QueuingRemovalListener<Object, Object> listener = queuingRemovalListener();
      LocalCache<Object, Object> map = makeLocalCache(builder.removalListener(listener));

      Object key = new Object();
      Object oldValue = new Object();

      // no entry
      assertEquals(0, map.size());
      assertNull(map.remove(key));
      assertEquals(0, map.size());

      // same key
      map.put(key, oldValue);
      assertEquals(1, map.size());
      assertSame(oldValue, map.get(key));
      assertSame(oldValue, map.remove(key));
      assertEquals(0, map.size());
      assertNull(map.get(key));
      assertNotified(listener, key, oldValue, RemovalCause.EXPLICIT);

      // cleared
      map.put(key, oldValue);
      assertEquals(1, map.size());
      assertSame(oldValue, map.get(key));
      CacheTesting.reclaimValue(map, key, enqueue);
      assertNull(map.remove(key));
      assertEquals(0, map.size());
      assertNull(map.get(key));
      assertNotified(listener, key, null, RemovalCause.COLLECTED);
      assertTrue(listener.isEmpty());
    }
  }

  @Test
  public void testCollectedValue_removeValue() {
    checkCollectedValue_removeValue(true);
    checkCollectedValue_removeValue(false);
  }

  private void checkCollectedValue_removeValue(boolean enqueue) {
    for (CacheBuilder<Object, Object> builder : valueReferenceMakers()) {
      QueuingRemovalListener<Object, Object> listener = queuingRemovalListener();
      LocalCache<Object, Object> map = makeLocalCache(builder.removalListener(listener));

      Object key = new Object();
      Object oldValue = new Object();
      Object newValue = new Object();

      // no entry
      assertEquals(0, map.size());
      assertFalse(map.remove(key, oldValue));
      assertEquals(0, map.size());

      // same value
      map.put(key, oldValue);
      assertEquals(1, map.size());
      assertSame(oldValue, map.get(key));
      assertTrue(map.remove(key, oldValue));
      assertEquals(0, map.size());
      assertNull(map.get(key));
      assertNotified(listener, key, oldValue, RemovalCause.EXPLICIT);

      // different value
      map.put(key, oldValue);
      assertEquals(1, map.size());
      assertSame(oldValue, map.get(key));
      assertFalse(map.remove(key, newValue));
      assertEquals(1, map.size());
      assertSame(oldValue, map.get(key));
      assertTrue(listener.isEmpty());

      // cleared
      CacheTesting.reclaimValue(map, key, enqueue);
      assertFalse(map.remove(key, oldValue));
      assertEquals(0, map.size());
      assertNull(map.get(key));
      assertNotified(listener, key, null, RemovalCause.COLLECTED);
      assertTrue(listener.isEmpty());
    }
  }

  @Test
  public void testCollectedValue_getAndContains() {
    checkCollectedValue_getAndContains(true);
    checkCollectedValue_getAndContains(false);
  }

  private void checkCollectedValue_getAndContains(boolean enqueue) {
    for (CacheBuilder<Object, Object> builder : valueReferenceMakers()) {
      LocalCache<Object, Object> map = makeLocalCache(builder);
      Object key = new Object();
      Object value = new Object();
      Object otherKey = new Object();
      Object otherValue = new Object();
      map.put(key, value);
      map.put(otherKey, otherValue);
      assertSame(value, map.get(key));
      assertTrue(map.containsKey(key));
      assertTrue(map.containsValue(value));
      // don't see absent values
      assertNull(map.get(new Object()));
      assertFalse(map.containsKey(new Object()));
      assertFalse(map.containsValue(new Object()));

      CacheTesting.reclaimValue(map, key, enqueue);
      assertFalse(map.containsKey(key));
      assertFalse(map.containsValue(value));
      assertNull(map.get(key));
      // an entry that was only cleared is still counted until an operation reclaims it, as in Guava
      assertEquals(enqueue ? 1 : 2, map.size());
      assertTrue(map.containsKey(otherKey));
      assertTrue(map.containsValue(otherValue));
    }
  }

  @Test
  public void testExpiredEntry_getAndContains() {
    FakeTicker ticker = new FakeTicker();
    LocalCache<Object, Object> map =
        makeLocalCache(
            createCacheBuilder().ticker(ticker).expireAfterAccess(1, TimeUnit.NANOSECONDS));
    Object key = new Object();
    Object value = new Object();
    map.put(key, value);
    assertSame(value, map.get(key));
    assertTrue(map.containsKey(key));
    assertTrue(map.containsValue(value));
    assertEquals(1, map.size());

    ticker.advance(2);
    assertFalse(map.containsKey(key));
    assertFalse(map.containsValue(value));
    assertNull(map.get(key));
    assertEquals(0, map.size());
  }

  // reclamation of keys and values

  @Test
  public void testReclaimKey() {
    CountingRemovalListener<Object, Object> listener = countingRemovalListener();
    LocalCache<Object, Object> map =
        makeLocalCache(
            createCacheBuilder()
                .weakKeys()
                .maximumSize(SMALL_MAX_SIZE)
                .expireAfterWrite(99999, SECONDS)
                .removalListener(listener));

    Object keyOne = new Object();
    Object valueOne = new Object();
    Object keyTwo = new Object();
    Object valueTwo = new Object();
    map.put(keyOne, valueOne);
    map.put(keyTwo, valueTwo);
    assertEquals(2, map.size());
    assertEquals(0, listener.getCount());

    map.simulateKeyReclamation(keyOne);
    assertEquals(1, map.size());
    assertEquals(1, listener.getCount());
    // Lava: the key reference was cleared
    assertNull(listener.getLastEvictedKey());
    assertSame(valueOne, listener.getLastEvictedValue());
    assertSame(RemovalCause.COLLECTED, listener.getLastNotification().getCause());
    assertFalse(map.containsValue(valueOne));
    assertSame(valueTwo, map.get(keyTwo));
  }

  @Test
  public void testReclaimValue() {
    for (CacheBuilder<Object, Object> builder : valueReferenceMakers()) {
      CountingRemovalListener<Object, Object> listener = countingRemovalListener();
      LocalCache<Object, Object> map = makeLocalCache(builder.removalListener(listener));

      Object key = new Object();
      Object value = new Object();
      map.put(key, value);
      assertEquals(1, map.size());

      map.simulateValueReclamation(key);
      map.cleanUp();
      assertEquals(0, map.size());
      assertEquals(1, listener.getCount());
      assertSame(key, listener.getLastEvictedKey());
      assertNull(listener.getLastEvictedValue());
      assertSame(RemovalCause.COLLECTED, listener.getLastNotification().getCause());

      // reclaiming again does nothing: the entry is gone, and a new one is not affected
      map.simulateValueReclamation(key);
      map.cleanUp();
      Object newValue = new Object();
      map.put(key, newValue);
      assertSame(newValue, map.get(key));
      assertEquals(1, listener.getCount());
    }
  }

  @Test
  public void testRemoveLoadingValue() throws ExecutionException {
    // a load that fails removes the loading entry without any notification, and keeps a previous
    // value if there was one
    CountingRemovalListener<Object, Object> listener = countingRemovalListener();
    final boolean[] fail = {true};
    final Object value = new Object();
    CacheLoader<Object, Object> loader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object key) {
            if (fail[0]) {
              throw new IllegalStateException();
            }
            return value;
          }
        };
    LocalCache<Object, Object> map =
        new LocalCache<>(
            createCacheBuilder()
                .maximumSize(SMALL_MAX_SIZE)
                .expireAfterWrite(99999, SECONDS)
                .removalListener(listener),
            loader);

    Object key = new Object();
    try {
      map.get(key, loader);
      fail();
    } catch (UncheckedExecutionException expected) {
    }
    assertEquals(0, map.size());
    assertEquals(0, listener.getCount());

    fail[0] = false;
    assertSame(value, map.get(key, loader));
    assertEquals(1, map.size());

    // a failed refresh keeps the old value
    fail[0] = true;
    map.refresh(key);
    assertSame(value, map.get(key));
    assertEquals(1, map.size());
    assertEquals(0, listener.getCount());
    assertThat(logHandler.getStoredLogRecords()).hasSize(1);
  }

  // reference queues

  /** Returns an iterable containing all combinations weakKeys and weak/softValues. */
  private static Iterable<CacheBuilder<Object, Object>> allKeyValueStrengthMakers() {
    return ImmutableList.of(
        createCacheBuilder(),
        createCacheBuilder().weakValues(),
        createCacheBuilder().softValues(),
        createCacheBuilder().weakKeys(),
        createCacheBuilder().weakKeys().weakValues(),
        createCacheBuilder().weakKeys().softValues());
  }

  /** Returns an iterable containing all combinations of maximumSize and expireAfterAccess/Write. */
  static Iterable<CacheBuilder<Object, Object>> allEvictingMakers() {
    return ImmutableList.of(
        createCacheBuilder().maximumSize(SMALL_MAX_SIZE),
        createCacheBuilder().expireAfterAccess(99999, SECONDS).maximumSize(SMALL_MAX_SIZE),
        createCacheBuilder().expireAfterWrite(99999, SECONDS).maximumSize(SMALL_MAX_SIZE),
        createCacheBuilder()
            .maximumSize(SMALL_MAX_SIZE)
            .expireAfterAccess(SMALL_MAX_SIZE, TimeUnit.SECONDS),
        createCacheBuilder()
            .maximumSize(SMALL_MAX_SIZE)
            .expireAfterWrite(SMALL_MAX_SIZE, TimeUnit.SECONDS));
  }

  @Test
  public void testDrainKeyReferenceQueueOnWrite() {
    for (CacheBuilder<Object, Object> builder : allKeyValueStrengthMakers()) {
      LocalCache<Object, Object> map = makeLocalCache(builder);
      if (map.keyStrength != Strength.STRONG) {
        Object keyOne = new Object();
        Object valueOne = new Object();
        Object keyTwo = new Object();
        Object valueTwo = new Object();

        map.put(keyOne, valueOne);
        map.simulateKeyReclamation(keyOne);

        map.put(keyTwo, valueTwo);
        assertFalse(map.containsKey(keyOne));
        assertFalse(map.containsValue(valueOne));
        assertNull(map.get(keyOne));
        assertEquals(1, map.size());
      }
    }
  }

  @Test
  public void testDrainValueReferenceQueueOnWrite() {
    for (CacheBuilder<Object, Object> builder : allKeyValueStrengthMakers()) {
      LocalCache<Object, Object> map = makeLocalCache(builder);
      if (map.valueStrength != Strength.STRONG) {
        Object keyOne = new Object();
        Object valueOne = new Object();
        Object keyTwo = new Object();
        Object valueTwo = new Object();

        map.put(keyOne, valueOne);
        map.simulateValueReclamation(keyOne);

        map.put(keyTwo, valueTwo);
        assertFalse(map.containsKey(keyOne));
        assertFalse(map.containsValue(valueOne));
        assertNull(map.get(keyOne));
        assertEquals(1, map.size());
      }
    }
  }

  @Test
  public void testDrainKeyReferenceQueueOnRead() {
    for (CacheBuilder<Object, Object> builder : allKeyValueStrengthMakers()) {
      LocalCache<Object, Object> map = makeLocalCache(builder);
      if (map.keyStrength != Strength.STRONG) {
        Object keyOne = new Object();
        Object valueOne = new Object();
        Object keyTwo = new Object();

        map.put(keyOne, valueOne);
        map.simulateKeyReclamation(keyOne);

        // Lava: one read is enough (Guava drains its queues on every 64th read)
        assertNull(map.get(keyTwo));
        assertFalse(map.containsKey(keyOne));
        assertFalse(map.containsValue(valueOne));
        assertNull(map.get(keyOne));
        assertEquals(0, map.size());
      }
    }
  }

  @Test
  public void testDrainValueReferenceQueueOnRead() {
    for (CacheBuilder<Object, Object> builder : allKeyValueStrengthMakers()) {
      LocalCache<Object, Object> map = makeLocalCache(builder);
      if (map.valueStrength != Strength.STRONG) {
        Object keyOne = new Object();
        Object valueOne = new Object();
        Object keyTwo = new Object();

        map.put(keyOne, valueOne);
        map.simulateValueReclamation(keyOne);

        // Lava: one read is enough (Guava drains its queues on every 64th read)
        assertNull(map.get(keyTwo));
        assertFalse(map.containsKey(keyOne));
        assertFalse(map.containsValue(valueOne));
        assertNull(map.get(keyOne));
        assertEquals(0, map.size());
      }
    }
  }

  // expiration and eviction

  @Test
  public void testExpireAfterWrite() {
    FakeTicker ticker = new FakeTicker();
    LocalCache<Object, Object> map =
        makeLocalCache(
            createCacheBuilder().ticker(ticker).expireAfterWrite(2, TimeUnit.NANOSECONDS));

    Object key = new Object();
    Object value = new Object();
    map.put(key, value);
    assertSame(value, map.get(key));

    ticker.advance(1);
    assertSame(value, map.get(key)); // reads do not extend the life of the entry
    assertTrue(map.containsKey(key));
    assertEquals(1, map.size());

    ticker.advance(1);
    assertNull(map.get(key));
    assertFalse(map.containsKey(key));
    assertEquals(0, map.size());
  }

  @Test
  public void testExpireAfterAccess() {
    FakeTicker ticker = new FakeTicker();
    LocalCache<Object, Object> map =
        makeLocalCache(
            createCacheBuilder().ticker(ticker).expireAfterAccess(2, TimeUnit.NANOSECONDS));

    Object key = new Object();
    Object value = new Object();
    map.put(key, value);
    assertSame(value, map.get(key));

    ticker.advance(1);
    assertSame(value, map.get(key)); // reads extend the life of the entry
    ticker.advance(1);
    assertSame(value, map.get(key));
    ticker.advance(1);
    assertTrue(map.containsKey(key)); // containsKey does not
    assertEquals(1, map.size());

    ticker.advance(1);
    assertFalse(map.containsKey(key));
    assertNull(map.get(key));
    assertEquals(0, map.size());
  }

  @Test
  public void testEvictEntries() {
    int maxSize = 10;
    LocalCache<Object, Object> map = makeLocalCache(createCacheBuilder().maximumSize(maxSize));

    int originalCount = 1024;
    LinkedHashMap<Object, Object> originalMap = Maps.newLinkedHashMap();
    for (int i = 0; i < originalCount; i++) {
      Object key = new Object();
      Object value = new Object();
      map.put(key, value);
      originalMap.put(key, value);
      if (i >= maxSize) {
        Iterator<Object> it = originalMap.keySet().iterator();
        it.next();
        it.remove();
      }
      assertEquals(originalMap, map);
    }
  }

  // entries that are being loaded or computed (Lava: reached on one thread, by making the calls
  // from inside the loader or function that is in flight)

  @Test
  public void testOperationsDuringLoad() throws ExecutionException {
    // while a key loads, and has no old value, the other operations see it as absent
    final QueuingRemovalListener<Object, Object> listener = queuingRemovalListener();
    final LocalCache<Object, Object>[] mapRef = newCacheArray();
    final List<Object> results = new ArrayList<>();
    final Object other = new Object();
    CacheLoader<Object, Object> loader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object k) {
            LocalCache<Object, Object> map = mapRef[0];
            results.add(map.get(k));
            results.add(map.containsKey(k));
            results.add(map.containsValue(other));
            results.add(map.size());
            results.add(map.replace(k, other));
            results.add(map.replace(k, other, other));
            results.add(map.remove(k));
            results.add(map.remove(k, other));
            results.add(map.entrySet().size());
            results.add(map.keySet().contains(k));
            return "loaded";
          }
        };
    LocalCache<Object, Object> map = makeLocalCache(createCacheBuilder().removalListener(listener));
    mapRef[0] = map;

    Object key = new Object();
    assertEquals("loaded", map.get(key, loader));
    assertEquals(
        Arrays.asList(null, false, false, 0, null, false, null, false, 0, false), results);
    assertEquals("loaded", map.get(key));
    assertEquals(1, map.size());
    assertTrue(listener.isEmpty());
  }

  @Test
  public void testLoadFailure_entryReplacedDuringLoad() throws ExecutionException {
    final LocalCache<Object, Object>[] mapRef = newCacheArray();
    final Object replacement = new Object();
    CacheLoader<Object, Object> loader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object k) {
            mapRef[0].put(k, replacement);
            throw new IllegalStateException("failed");
          }
        };
    LocalCache<Object, Object> map = makeLocalCache(createCacheBuilder());
    mapRef[0] = map;

    Object key = new Object();
    try {
      map.get(key, loader);
      fail();
    } catch (UncheckedExecutionException expected) {
      assertEquals("failed", expected.getCause().getMessage());
    }
    // the failed load does not remove the value that was put in the meantime
    assertSame(replacement, map.get(key));
    assertEquals(1, map.size());
  }

  @Test
  public void testLoadFailure_entryClearedDuringLoad() throws ExecutionException {
    final LocalCache<Object, Object>[] mapRef = newCacheArray();
    CacheLoader<Object, Object> loader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object k) {
            mapRef[0].clear();
            throw new IllegalStateException("failed");
          }
        };
    LocalCache<Object, Object> map = makeLocalCache(createCacheBuilder());
    mapRef[0] = map;

    Object key = new Object();
    map.put("other", "value");
    try {
      map.get(key, loader);
      fail();
    } catch (UncheckedExecutionException expected) {
      assertEquals("failed", expected.getCause().getMessage());
    }
    assertEquals(0, map.size());
    assertEquals("loaded", map.get(key, identityLoaderOf("loaded")));
    assertEquals(1, map.size());
  }

  private static CacheLoader<Object, Object> identityLoaderOf(final Object value) {
    return new CacheLoader<Object, Object>() {
      @Override
      public Object load(Object k) {
        return value;
      }
    };
  }

  @Test
  public void testRecursiveCompute() throws ExecutionException {
    // Lava: where Guava would wait forever for the load or computation that is running on this
    // very thread, a compute of the same key fails fast. Other keys can nest.
    final LocalCache<Object, Object>[] mapRef = newCacheArray();
    LocalCache<Object, Object> map = makeLocalCache(createCacheBuilder());
    mapRef[0] = map;

    try {
      map.compute("k", (k, v) -> mapRef[0].compute("k", (k2, v2) -> "inner"));
      fail();
    } catch (IllegalStateException expected) {
      assertEquals("Recursive load of: k", expected.getMessage());
    }
    assertEquals(0, map.size()); // the entry was not left behind
    assertEquals("b", map.compute("a", (k, v) -> mapRef[0].compute("b", (k2, v2) -> "b")));
    assertEquals(2, map.size());

    // a compute from the loader of the same key
    CacheLoader<Object, Object> loader =
        new CacheLoader<Object, Object>() {
          @Override
          public Object load(Object k) {
            return mapRef[0].compute(k, (k2, v2) -> "inner");
          }
        };
    map = new LocalCache<>(createCacheBuilder(), loader);
    mapRef[0] = map;
    try {
      map.getOrLoad("loaded");
      fail();
    } catch (UncheckedExecutionException expected) {
      assertThat(expected.getCause()).isInstanceOf(IllegalStateException.class);
      assertEquals("Recursive load of: loaded", expected.getCause().getMessage());
    }
    assertEquals(0, map.size());
  }

  @Test
  public void testComputeFunctionSeesOldValue() {
    final LocalCache<Object, Object>[] mapRef = newCacheArray();
    LocalCache<Object, Object> map = makeLocalCache(createCacheBuilder().maximumSize(10));
    mapRef[0] = map;
    map.put("k", 1);
    final List<Object> seen = new ArrayList<>();
    assertEquals(
        2,
        map.compute(
            "k",
            (k, v) -> {
              seen.add(v);
              seen.add(mapRef[0].get(k));
              seen.add(mapRef[0].containsKey(k));
              seen.add(mapRef[0].size());
              return (Integer) v + 1;
            }));
    assertEquals(Arrays.asList(1, 1, true, 1), seen);
    assertEquals(2, map.get("k"));
  }

  @Test
  public void testGetWithPendingRefreshAndCollectedValue() throws ExecutionException {
    // Lava: a read that finds a pending asynchronous refresh and no live value loads synchronously
    // (Guava would wait for the refresh, which a single thread cannot do when it is the one that
    // has to run it); the result of the refresh is discarded when it completes.
    FakeTicker ticker = new FakeTicker();
    final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    final AtomicInteger loads = new AtomicInteger();
    CacheLoader<Object, Object> loader =
        CacheLoader.asyncReloading(
            new CacheLoader<Object, Object>() {
              @Override
              public Object load(Object key) {
                loads.incrementAndGet();
                return new Object();
              }
            },
            tasks::add);
    QueuingRemovalListener<Object, Object> listener = queuingRemovalListener();
    LocalCache<Object, Object> map =
        new LocalCache<>(
            createCacheBuilder()
                .weakValues()
                .refreshAfterWrite(1, TimeUnit.MILLISECONDS)
                .ticker(ticker)
                .removalListener(listener),
            loader);

    Object key = new Object();
    Object value = map.get(key, loader);
    assertEquals(1, loads.get());
    ticker.advance(2, TimeUnit.MILLISECONDS);
    assertSame(value, map.get(key, loader)); // the old value, while the reload is pending
    assertEquals(1, tasks.size());
    assertEquals(1, loads.get());

    CacheTesting.reclaimValue(map, key, false); // the old value is collected
    Object loaded = map.get(key, loader);
    assertEquals(2, loads.get());
    assertNotSame(value, loaded);
    assertNotified(listener, key, null, RemovalCause.COLLECTED);
    assertTrue(listener.isEmpty());
    assertSame(loaded, map.get(key));

    tasks.remove().run(); // the reload completes, and is discarded
    assertEquals(3, loads.get());
    assertSame(loaded, map.get(key));
    assertEquals(1, map.size());
    RemovalNotification<Object, Object> discarded = listener.remove();
    assertSame(key, discarded.getKey());
    assertNotSame(loaded, discarded.getValue());
    assertSame(RemovalCause.REPLACED, discarded.getCause());
    assertTrue(listener.isEmpty());
  }

  // entries that were collected but not yet reclaimed

  @Test
  public void testClear_collectedEntries() {
    for (boolean enqueue : new boolean[] {true, false}) {
      QueuingRemovalListener<Object, Object> listener = queuingRemovalListener();
      LocalCache<Object, Object> map =
          makeLocalCache(createCacheBuilder().weakValues().removalListener(listener));
      Object collectedKey = new Object();
      Object collectedValue = new Object();
      Object key = new Object();
      Object value = new Object();
      map.put(collectedKey, collectedValue);
      map.put(key, value);

      CacheTesting.reclaimValue(map, collectedKey, enqueue);
      map.clear();
      assertEquals(0, map.size());

      Map<Object, RemovalNotification<Object, Object>> notifications = new LinkedHashMap<>();
      for (RemovalNotification<Object, Object> n : listener) {
        notifications.put(n.getKey(), n);
      }
      assertEquals(2, notifications.size());
      assertNull(notifications.get(collectedKey).getValue());
      assertSame(RemovalCause.COLLECTED, notifications.get(collectedKey).getCause());
      assertSame(value, notifications.get(key).getValue());
      assertSame(RemovalCause.EXPLICIT, notifications.get(key).getCause());
    }
  }

  @Test
  public void testCollectedKey_notYetEnqueued() {
    QueuingRemovalListener<Object, Object> listener = queuingRemovalListener();
    LocalCache<Object, Object> map =
        makeLocalCache(createCacheBuilder().weakKeys().removalListener(listener));
    Object keyOne = new Object();
    Object valueOne = new Object();
    Object keyTwo = new Object();
    Object valueTwo = new Object();
    map.put(keyOne, valueOne);
    map.put(keyTwo, valueTwo);

    CacheTesting.clearKeyReference(map, keyOne);
    assertFalse(map.containsKey(keyOne));
    assertFalse(map.containsValue(valueOne));
    assertNull(map.get(keyOne));
    assertEquals(ImmutableMap.of(keyTwo, valueTwo), ImmutableMap.copyOf(map));
    assertEquals(ImmutableList.of(valueTwo), ImmutableList.copyOf(map.values()));
    assertEquals(ImmutableList.of(keyTwo), ImmutableList.copyOf(map.keySet()));
    assertTrue(listener.isEmpty());

    map.clear();
    // the order in which clear() visits the entries is unspecified
    RemovalNotification<Object, Object> collected = listener.remove();
    RemovalNotification<Object, Object> explicit = listener.remove();
    if (collected.getCause() == RemovalCause.EXPLICIT) {
      RemovalNotification<Object, Object> swap = collected;
      collected = explicit;
      explicit = swap;
    }
    assertEquals(RemovalCause.COLLECTED, collected.getCause());
    assertNull(collected.getKey());
    assertSame(valueOne, collected.getValue());
    assertEquals(RemovalCause.EXPLICIT, explicit.getCause());
    assertSame(keyTwo, explicit.getKey());
    assertSame(valueTwo, explicit.getValue());
    assertTrue(listener.isEmpty());
  }

  // iterators and entries

  @Test
  public void testIterators() {
    LocalCache<Object, Object> map = makeLocalCache(createCacheBuilder());
    for (int i = 0; i < 5; i++) {
      map.put(i, i * 10);
    }
    List<Object> order = new ArrayList<>(map.keySet()); // the order of every iteration
    assertEquals(5, order.size());
    Iterator<Object> keys = map.keySet().iterator();

    try {
      keys.remove();
      fail();
    } catch (IllegalStateException expected) {
    }

    // the iterators see a snapshot of the entries: those removed since are skipped, those added
    // since are not seen, and there is no ConcurrentModificationException. (The iterator has
    // already looked ahead to the entry after the one it returns.)
    assertEquals(order.get(0), keys.next());
    map.remove(order.get(2));
    map.remove(order.get(3));
    map.put("added", "added");
    List<Object> rest = new ArrayList<>();
    while (keys.hasNext()) {
      rest.add(keys.next());
    }
    assertEquals(Arrays.asList(order.get(1), order.get(4)), rest);
    try {
      keys.next();
      fail();
    } catch (NoSuchElementException expected) {
    }

    // the same for entries and values, which write through
    Iterator<Entry<Object, Object>> entries = map.entrySet().iterator();
    Entry<Object, Object> entry = entries.next();
    map.clear();
    assertNotNull(entry);
    assertFalse(entries.hasNext() && map.containsKey(entries.next().getKey()));
    assertEquals(0, map.size());

    map.put(1, 10);
    map.put(2, 20);
    keys = map.keySet().iterator();
    Object key = keys.next();
    keys.remove();
    assertFalse(map.containsKey(key));
    try {
      keys.remove();
      fail();
    } catch (IllegalStateException expected) {
    }
    assertEquals(1, map.size());
  }

  @Test
  public void testWriteThroughEntry() {
    LocalCache<Object, Object> map = makeLocalCache(createCacheBuilder());
    map.put("a", 1);
    Entry<Object, Object> entry = map.entrySet().iterator().next();
    assertEquals("a=1", entry.toString());
    assertEquals("a".hashCode() ^ 1, entry.hashCode());
    assertTrue(entry.equals(immutableEntry("a", 1)));
    assertFalse(entry.equals(immutableEntry("a", 2)));
    assertFalse(entry.equals(immutableEntry("b", 1)));
    assertFalse(entry.equals("a=1"));
    assertFalse(entry.equals(null));

    assertEquals(1, entry.setValue(5));
    assertEquals(5, entry.getValue());
    assertEquals(5, map.get("a"));
    assertEquals("a=5", entry.toString());
  }

  // null parameters

  @Test
  public void testNullParameters() throws Exception {
    NullPointerTester tester = new NullPointerTester();
    tester.testAllPublicInstanceMethods(makeLocalCache(createCacheBuilder()));
    CacheLoader<Object, Object> loader = identityLoader();
    tester.testAllPublicInstanceMethods(new LocalCache<Object, Object>(createCacheBuilder(), loader));

    LocalCache<Object, Object> map = makeLocalCache(createCacheBuilder());
    map.put("a", 1);
    assertEquals(5, map.getOrDefault(null, 5));
    assertNull(map.getOrDefault("b", null));
    assertEquals(1, map.getOrDefault("a", null));
  }

  @Test
  public void testNullKeysAndValues() {
    // follows Guava: lookups and removals of null answer "absent", writes throw
    LocalCache<Object, Object> map = makeLocalCache(createCacheBuilder());
    assertNull(map.get(null));
    assertFalse(map.containsKey(null));
    assertFalse(map.containsValue(null));
    assertNull(map.remove(null));
    assertFalse(map.remove(null, 1));
    assertFalse(map.remove("a", null));
    assertFalse(map.replace("a", null, 1));
    try {
      map.put(null, 1);
      fail();
    } catch (NullPointerException expected) {
    }
    try {
      map.put("a", null);
      fail();
    } catch (NullPointerException expected) {
    }
    try {
      map.putIfAbsent("a", null);
      fail();
    } catch (NullPointerException expected) {
    }
    try {
      map.replace("a", null);
      fail();
    } catch (NullPointerException expected) {
    }
    try {
      map.replace("a", 1, null);
      fail();
    } catch (NullPointerException expected) {
    }
    assertTrue(map.isEmpty());
    map.put("a", 1);
    assertEquals(immutableEntry("a", 1), map.entrySet().iterator().next());
  }
}
