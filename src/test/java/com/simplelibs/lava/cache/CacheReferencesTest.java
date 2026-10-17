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

import static com.simplelibs.lava.cache.CacheBuilderFactory.Strength.STRONG;
import static com.simplelibs.lava.cache.TestingRemovalListeners.countingRemovalListener;
import static com.simplelibs.lava.collect.Maps.immutableEntry;
import static org.assertj.core.api.Assertions.assertThat;

import com.simplelibs.lava.cache.CacheBuilderFactory.Strength;
import com.simplelibs.lava.cache.TestingRemovalListeners.CountingRemovalListener;
import com.simplelibs.lava.collect.ImmutableSet;
import com.simplelibs.lava.testing.GcFinalization;
import com.simplelibs.lava.testing.LavaTestCase;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Tests of basic {@link LoadingCache} operations with all possible combinations of key & value
 * strengths.
 *
 * @author mike nonemacher
 */
public class CacheReferencesTest extends LavaTestCase {

  private static final CacheLoader<Key, String> KEY_TO_STRING_LOADER =
      new CacheLoader<Key, String>() {
        @Override
        public String load(Key key) {
          return key.toString();
        }
      };

  private CacheBuilderFactory factoryWithAllKeyStrengths() {
    return new CacheBuilderFactory()
        .withKeyStrengths(ImmutableSet.of(STRONG, Strength.WEAK))
        .withValueStrengths(ImmutableSet.of(STRONG, Strength.WEAK, Strength.SOFT));
  }

  private Iterable<LoadingCache<Key, String>> caches() {
    CacheBuilderFactory factory = factoryWithAllKeyStrengths();
    List<LoadingCache<Key, String>> caches = new ArrayList<>();
    for (CacheBuilder<Object, Object> builder : factory.buildAllPermutations()) {
      caches.add(builder.build(KEY_TO_STRING_LOADER));
    }
    return caches;
  }

  @Test
  public void testContainsKeyAndValue() {
    for (LoadingCache<Key, String> cache : caches()) {
      // maintain strong refs so these won't be collected, regardless of cache's key/value strength
      Key key = new Key(1);
      String value = key.toString();
      assertSame(value, cache.getUnchecked(key));
      assertTrue(cache.asMap().containsKey(key));
      assertTrue(cache.asMap().containsValue(value));
      assertEquals(1, cache.size());
    }
  }

  @Test
  public void testClear() {
    for (LoadingCache<Key, String> cache : caches()) {
      Key key = new Key(1);
      String value = key.toString();
      assertSame(value, cache.getUnchecked(key));
      assertFalse(cache.asMap().isEmpty());
      cache.invalidateAll();
      assertEquals(0, cache.size());
      assertTrue(cache.asMap().isEmpty());
      assertFalse(cache.asMap().containsKey(key));
      assertFalse(cache.asMap().containsValue(value));
    }
  }

  @Test
  public void testKeySetEntrySetValues() {
    for (LoadingCache<Key, String> cache : caches()) {
      Key key1 = new Key(1);
      String value1 = key1.toString();
      Key key2 = new Key(2);
      String value2 = key2.toString();
      assertSame(value1, cache.getUnchecked(key1));
      assertSame(value2, cache.getUnchecked(key2));
      assertEquals(ImmutableSet.of(key1, key2), cache.asMap().keySet());
      assertThat(cache.asMap().values()).containsExactlyInAnyOrder(value1, value2);
      assertEquals(
          ImmutableSet.of(immutableEntry(key1, value1), immutableEntry(key2, value2)),
          cache.asMap().entrySet());
    }
  }

  @Test
  public void testInvalidate() {
    for (LoadingCache<Key, String> cache : caches()) {
      Key key1 = new Key(1);
      String value1 = key1.toString();
      Key key2 = new Key(2);
      String value2 = key2.toString();
      assertSame(value1, cache.getUnchecked(key1));
      assertSame(value2, cache.getUnchecked(key2));
      cache.invalidate(key1);
      assertFalse(cache.asMap().containsKey(key1));
      assertTrue(cache.asMap().containsKey(key2));
      assertEquals(1, cache.size());
      assertEquals(ImmutableSet.of(key2), cache.asMap().keySet());
      assertThat(cache.asMap().values()).contains(value2);
      assertEquals(ImmutableSet.of(immutableEntry(key2, value2)), cache.asMap().entrySet());
    }
  }

  // Lava: Guava's helper for real-GC tests ("fails in Maven with 64-bit JDK") was unused. These
  // smoke tests do wait for the collector, so they are tagged "gc" (excluded by -PskipGcTests);
  // the reclamation logic is tested without a collector through CacheTesting.simulate*Reclamation
  // in CacheLoadingTest and LocalCacheTest.

  @Test
  @Tag("gc")
  public void testWeakKeys_collected() {
    CountingRemovalListener<Object, String> removalListener = countingRemovalListener();
    LoadingCache<Object, String> cache =
        CacheBuilder.newBuilder()
            .weakKeys()
            .removalListener(removalListener)
            .build(CacheLoader.from(key -> "value"));
    Object strongKey = new Object();
    cache.getUnchecked(strongKey);
    WeakReference<Object> collected = loadGarbageKey(cache);

    GcFinalization.awaitClear(collected);
    // the cache's own reference is enqueued asynchronously after it is cleared
    GcFinalization.awaitDone(() -> cache.size() == 1, "the collected key was not reclaimed");

    assertEquals(1, cache.size());
    assertTrue(cache.asMap().containsKey(strongKey));
    assertEquals(1, removalListener.getCount());
    assertNull(removalListener.getLastEvictedKey());
    assertEquals("value", removalListener.getLastEvictedValue());
    assertEquals(RemovalCause.COLLECTED, removalListener.getLastNotification().getCause());
  }

  @Test
  @Tag("gc")
  public void testWeakValues_collected() {
    CountingRemovalListener<Integer, Object> removalListener = countingRemovalListener();
    LoadingCache<Integer, Object> cache =
        CacheBuilder.newBuilder()
            .weakValues()
            .removalListener(removalListener)
            .build(CacheLoader.from(key -> new Object()));
    Object strongValue = cache.getUnchecked(1);
    WeakReference<Object> collected = loadGarbageValue(cache, 2);

    GcFinalization.awaitClear(collected);
    // the cache's own reference is enqueued asynchronously after it is cleared
    GcFinalization.awaitDone(() -> cache.size() == 1, "the collected value was not reclaimed");

    assertEquals(1, cache.size());
    assertSame(strongValue, cache.getIfPresent(1));
    assertEquals(1, removalListener.getCount());
    assertEquals(Integer.valueOf(2), removalListener.getLastEvictedKey());
    assertNull(removalListener.getLastEvictedValue());
    assertEquals(RemovalCause.COLLECTED, removalListener.getLastNotification().getCause());
  }

  private static WeakReference<Object> loadGarbageKey(LoadingCache<Object, String> cache) {
    Object garbage = new Object();
    cache.getUnchecked(garbage);
    return new WeakReference<>(garbage);
  }

  private static WeakReference<Object> loadGarbageValue(
      LoadingCache<Integer, Object> cache, Integer key) {
    return new WeakReference<>(cache.getUnchecked(key));
  }

  // A simple type whose .toString() will return the same value each time, but without maintaining
  // a strong reference to that value.
  static class Key {
    private final int value;
    private WeakReference<String> toString;

    Key(int value) {
      this.value = value;
    }

    @Override
    public synchronized String toString() {
      String s;
      if (toString != null) {
        s = toString.get();
        if (s != null) {
          return s;
        }
      }
      s = Integer.toString(value);
      toString = new WeakReference<>(s);
      return s;
    }
  }
}
