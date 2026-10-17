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

import static com.simplelibs.lava.base.Preconditions.checkNotNull;
import static com.simplelibs.lava.base.Preconditions.checkState;
import static com.simplelibs.lava.testing.LavaTestCase.assertEquals;
import static com.simplelibs.lava.testing.LavaTestCase.assertFalse;
import static com.simplelibs.lava.testing.LavaTestCase.assertNotNull;
import static com.simplelibs.lava.testing.LavaTestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

import com.simplelibs.lava.cache.LocalCache.LocalManualCache;
import com.simplelibs.lava.cache.LocalCache.Strength;
import com.simplelibs.lava.collect.ImmutableList;
import com.simplelibs.lava.collect.ImmutableMap;
import com.simplelibs.lava.collect.ImmutableSet;
import com.simplelibs.lava.testing.EqualsTester;
import com.simplelibs.lava.testing.FakeTicker;
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * A collection of utilities for {@link Cache} testing.
 *
 * <p>Lava: only the black-box parts of Guava's {@code CacheTesting}. Lava's {@code LocalCache} has
 * no segments, recency queues, expiration queues or deferred cleanup, so the helpers that poked
 * into those are gone, and the consistency checks go through the public views of the cache.
 *
 * @author mike nonemacher
 */
class CacheTesting {

  /**
   * Simulates garbage collection of the value associated with the given key: the reference is
   * cleared and enqueued, and the next cache operation reclaims the entry. Throws an
   * IllegalStateException if the cache does not use weak or soft values.
   */
  static <K, V> void simulateValueReclamation(Cache<K, V> cache, K key) {
    LocalCache<K, V> map = toLocalCache(cache);
    checkState(map.valueStrength != Strength.STRONG);
    map.simulateValueReclamation(checkNotNull(key));
  }

  /**
   * Simulates garbage collection of the given key: the reference is cleared and enqueued, and the
   * next cache operation reclaims the entry. Throws an IllegalStateException if the cache does not
   * use weak keys.
   */
  static <K, V> void simulateKeyReclamation(Cache<K, V> cache, K key) {
    LocalCache<K, V> map = toLocalCache(cache);
    checkState(map.keyStrength != Strength.STRONG);
    map.simulateKeyReclamation(checkNotNull(key));
  }

  /**
   * Reclaims the value of the entry for {@code key}, which must be weakly or softly referenced.
   * With {@code enqueue}, this is {@link LocalCache#simulateValueReclamation}: the reference is
   * cleared and enqueued, so the next cache operation reclaims the entry first. Without it, the
   * reference is only cleared, as the collector does just before the reference handler enqueues
   * it: the entry stays in the cache with a collected value until an operation runs into it (as
   * Guava's {@code CacheTesting.simulateValueReclamation} did).
   */
  static <K, V> void reclaimValue(LocalCache<K, V> map, K key, boolean enqueue) {
    if (enqueue) {
      map.simulateValueReclamation(key);
      return;
    }
    checkState(map.valueStrength != Strength.STRONG);
    for (LocalCache.Node<K, V> node : nodes(map)) {
      if (node.getKey() == key) {
        LocalCache.ValueRef<V> ref = node.valueRef;
        if (ref instanceof LocalCache.LoadingValue) {
          // a refresh or reload is in flight: the collected value is the one it replaces
          ref = ((LocalCache.LoadingValue<V>) ref).oldValue;
        }
        ((Reference<?>) ref).clear();
        return;
      }
    }
    throw new IllegalStateException("no entry for " + key);
  }

  /**
   * Clears the key reference of the entry for {@code key} (which must be weakly referenced) without
   * enqueueing it, as the collector does just before the reference handler enqueues it. The entry
   * stays in the cache until the reference is enqueued, which {@link
   * LocalCache#simulateKeyReclamation} does.
   */
  static <K, V> void clearKeyReference(LocalCache<K, V> map, K key) {
    checkState(map.keyStrength != Strength.STRONG);
    for (LocalCache.Node<K, V> node : nodes(map)) {
      if (node.getKey() == key) {
        ((Reference<?>) node.mapKey).clear();
        return;
      }
    }
    throw new IllegalStateException("no entry for " + key);
  }

  @SuppressWarnings("unchecked")
  private static <K, V> Collection<LocalCache.Node<K, V>> nodes(LocalCache<K, V> map) {
    try {
      Field table = LocalCache.class.getDeclaredField("table");
      table.setAccessible(true);
      return ((Map<Object, LocalCache.Node<K, V>>) table.get(map)).values();
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  /**
   * Gets the {@link LocalCache} used by the given {@link Cache}, if any, or throws an
   * IllegalArgumentException if this is a Cache type that doesn't have a LocalCache.
   */
  static <K, V> LocalCache<K, V> toLocalCache(Cache<K, V> cache) {
    if (cache instanceof LocalManualCache) {
      return ((LocalManualCache<K, V>) cache).localCache;
    }
    throw new IllegalArgumentException(
        "Cache of type " + cache.getClass() + " doesn't have a LocalCache.");
  }

  /**
   * Determines whether the given cache can be converted to a LocalCache by {@link #toLocalCache}
   * without throwing an exception.
   */
  static boolean hasLocalCache(Cache<?, ?> cache) {
    return (checkNotNull(cache) instanceof LocalManualCache);
  }

  /**
   * Lava: reclaims the entries whose references were collected (Lava does this at the start of
   * every operation, so {@code cleanUp()} is all it takes) and delivers their notifications.
   */
  static void drainReferenceQueues(Cache<?, ?> cache) {
    if (hasLocalCache(cache)) {
      cache.cleanUp();
    }
  }

  /**
   * Peeks into the cache's views to check its internal consistency: after a clean-up, the size
   * agrees with the entry, key and value views, and every entry has a non-null key and value that
   * the cache reports as present.
   */
  static void checkValidState(Cache<?, ?> cache) {
    if (hasLocalCache(cache)) {
      checkValidState(toLocalCache(cache));
    }
  }

  static void checkValidState(LocalCache<?, ?> cchm) {
    cchm.cleanUp();
    int entries = 0;
    for (Entry<?, ?> entry : cchm.entrySet()) {
      assertNotNull(entry.getKey());
      assertNotNull(entry.getValue());
      assertTrue(cchm.containsKey(entry.getKey()));
      entries++;
    }
    assertEquals(cchm.size(), entries);
    assertEquals(cchm.size(), cchm.keySet().size());
    assertEquals(cchm.size(), cchm.values().size());
    assertEquals(cchm.longSize(), cchm.size());
    assertEquals(cchm.size() == 0, cchm.isEmpty());
  }

  /** Delivers the removal notifications that are still pending (Lava: a clean-up). */
  static void processPendingNotifications(Cache<?, ?> cache) {
    if (hasLocalCache(cache)) {
      cache.cleanUp();
    }
  }

  interface Receiver<T> {
    void accept(T object);
  }

  /**
   * Assuming the given cache has maximum size {@code maxSize}, this method populates the cache (by
   * getting the keys {@code [0, 2 * maxSize)} in order), so that the keys {@code [maxSize, 2 *
   * maxSize)} remain in recency order, then invokes the given {@code operation} on the least
   * recently used key, {@code maxSize}. It then adds one more entry and verifies that the
   * operation took that key out of the eviction order: either it was invalidated (and nothing else
   * is evicted), or it became the most recently used key (and the next oldest, {@code maxSize +
   * 1}, is evicted instead).
   *
   * <p>Lava: the black-box version of Guava's check, which compared the heads of the access queue.
   */
  static void checkRecency(
      LoadingCache<Integer, Integer> cache, int maxSize, Receiver<Integer> operation) {
    checkNotNull(operation);
    warmUp(cache, 0, 2 * maxSize);
    assertEquals(maxSize, cache.size());
    Integer head = maxSize;
    assertTrue(cache.asMap().containsKey(head));
    assertFalse(cache.asMap().containsKey(maxSize - 1));

    operation.accept(head);
    boolean headRemoved = !cache.asMap().containsKey(head);
    assertEquals(headRemoved ? maxSize - 1 : maxSize, cache.size());

    cache.getUnchecked(2 * maxSize);
    assertEquals(maxSize, cache.size());
    assertTrue(cache.asMap().containsKey(2 * maxSize));
    if (headRemoved) {
      assertTrue(cache.asMap().containsKey(maxSize + 1));
    } else {
      assertTrue(cache.asMap().containsKey(head));
      assertFalse(cache.asMap().containsKey(maxSize + 1));
    }
    checkValidState(cache);
  }

  /** Returns the number of elements remaining in {@code iterator}, consuming it. */
  static int size(Iterator<?> iterator) {
    int size = 0;
    while (iterator.hasNext()) {
      iterator.next();
      size++;
    }
    return size;
  }

  /** Returns the number of elements in {@code iterable}. */
  static int size(Iterable<?> iterable) {
    return size(iterable.iterator());
  }

  /** Returns the single element of {@code iterable}, failing if it has any other size. */
  static <T> T getOnlyElement(Iterable<T> iterable) {
    Iterator<T> iterator = iterable.iterator();
    T only = iterator.next();
    assertFalse(iterator.hasNext());
    return only;
  }

  /** Warms the given cache by getting all values in {@code [start, end)}, in order. */
  static void warmUp(LoadingCache<Integer, Integer> map, int start, int end) {
    checkNotNull(map);
    for (int i = start; i < end; i++) {
      map.getUnchecked(i);
    }
  }

  /**
   * Advances the ticker past {@code expiringTime} (in milliseconds) and verifies that all entries
   * have expired and been removed, with their notifications delivered.
   */
  static void expireEntries(Cache<?, ?> cache, long expiringTime, FakeTicker ticker) {
    checkNotNull(ticker);
    LocalCache<?, ?> cchm = toLocalCache(cache);
    ticker.advance(2 * expiringTime, TimeUnit.MILLISECONDS);
    cchm.cleanUp();
    assertEquals("Cache must be empty by now", 0, cchm.size());
    assertEquals("Cache must be empty by now", 0, cache.size());
  }

  static void checkEmpty(Cache<?, ?> cache) {
    assertEquals(0, cache.size());
    assertFalse(cache.asMap().containsKey(null));
    assertFalse(cache.asMap().containsKey(6));
    assertFalse(cache.asMap().containsValue(null));
    assertFalse(cache.asMap().containsValue(6));
    checkEmpty(cache.asMap());
  }

  static void checkEmpty(ConcurrentMap<?, ?> map) {
    checkEmpty(map.keySet());
    checkEmpty(map.values());
    checkEmpty(map.entrySet());
    assertEquals(ImmutableMap.of(), map);
    assertEquals(ImmutableMap.of().hashCode(), map.hashCode());
    assertEquals(ImmutableMap.of().toString(), map.toString());

    if (map instanceof LocalCache) {
      LocalCache<?, ?> cchm = (LocalCache<?, ?>) map;

      checkValidState(cchm);
      assertTrue(cchm.isEmpty());
      assertEquals(0, cchm.size());
    }
  }

  static void checkEmpty(Collection<?> collection) {
    assertTrue(collection.isEmpty());
    assertEquals(0, collection.size());
    assertFalse(collection.iterator().hasNext());
    assertThat(collection.toArray()).isEmpty();
    assertThat(collection.toArray(new Object[0])).isEmpty();
    if (collection instanceof Set) {
      new EqualsTester()
          .addEqualityGroup(ImmutableSet.of(), collection)
          .addEqualityGroup(ImmutableSet.of(""))
          .testEquals();
    } else if (collection instanceof List) {
      new EqualsTester()
          .addEqualityGroup(ImmutableList.of(), collection)
          .addEqualityGroup(ImmutableList.of(""))
          .testEquals();
    }
  }
}
