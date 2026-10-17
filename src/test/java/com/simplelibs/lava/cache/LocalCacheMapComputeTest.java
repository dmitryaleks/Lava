/*
 * Copyright (C) 2017 The Guava Authors
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

import static org.assertj.core.api.Assertions.assertThat;

import com.simplelibs.lava.testing.LavaTestCase;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Test Java8 map.compute in a cache context.
 *
 * <p>Lava: Guava runs these operations from parallel streams; Lava is single-threaded, so the same
 * operations run sequentially (the expected results are the sequential outcomes).
 */
public class LocalCacheMapComputeTest extends LavaTestCase {
  final int count = 10000;
  final String delimiter = "-";
  final String key = "key";
  Cache<String, String> cache;

  // helper
  private static void doParallelCacheOp(int count, IntConsumer consumer) {
    // Lava: sequential, not parallel
    IntStream.range(0, count).forEach(consumer);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    this.cache =
        CacheBuilder.newBuilder()
            .expireAfterAccess(500000, TimeUnit.MILLISECONDS)
            .maximumSize(count)
            .build();
  }

  @Test
  public void testComputeIfAbsent() {
    // repeated insertion for same key, expect 1 winner (the first)
    doParallelCacheOp(
        count,
        n -> {
          cache.asMap().computeIfAbsent(key, k -> "value" + n);
        });
    assertEquals(1, cache.size());
    assertEquals("value0", cache.getIfPresent(key));
  }

  @Test
  public void testComputeIfAbsentEviction() {
    // b/80241237

    Cache<String, String> c = CacheBuilder.newBuilder().maximumSize(1).build();

    assertThat(c.asMap().computeIfAbsent("hash-1", k -> "")).isEqualTo("");
    assertThat(c.asMap().computeIfAbsent("hash-1", k -> "")).isEqualTo("");
    assertThat(c.asMap().computeIfAbsent("hash-1", k -> "")).isEqualTo("");
    assertThat(c.size()).isEqualTo(1);
    assertThat(c.asMap().computeIfAbsent("hash-2", k -> "")).isEqualTo("");
  }

  @Test
  public void testComputeEviction() {
    // b/80241237

    Cache<String, String> c = CacheBuilder.newBuilder().maximumSize(1).build();

    assertThat(c.asMap().compute("hash-1", (k, v) -> "a")).isEqualTo("a");
    assertThat(c.asMap().compute("hash-1", (k, v) -> "b")).isEqualTo("b");
    assertThat(c.asMap().compute("hash-1", (k, v) -> "c")).isEqualTo("c");
    assertThat(c.size()).isEqualTo(1);
    assertThat(c.asMap().computeIfAbsent("hash-2", k -> "")).isEqualTo("");
  }

  @Test
  public void testComputeIfPresent() {
    cache.put(key, "1");
    // repeated update for same key, expect count successful updates
    doParallelCacheOp(
        count,
        n -> {
          cache.asMap().computeIfPresent(key, (k, v) -> v + delimiter + n);
        });
    assertEquals(1, cache.size());
    assertThat(cache.getIfPresent(key).split(delimiter)).hasSize(count + 1);
  }

  @Test
  public void testUpdates() {
    cache.put(key, "1");
    // repeated update for same key, some null, some non-null
    doParallelCacheOp(
        count,
        n -> {
          cache.asMap().compute(key, (k, v) -> n % 2 == 0 ? v + delimiter + n : null);
        });
    assertTrue(1 >= cache.size());
  }

  @Test
  public void testCompute() {
    cache.put(key, "1");
    // repeated deletion
    doParallelCacheOp(
        count,
        n -> {
          cache.asMap().compute(key, (k, v) -> null);
        });
    assertEquals(0, cache.size());
  }

  @Test
  public void testComputeExceptionally() {
    try {
      doParallelCacheOp(count, n -> {
        cache.asMap().compute(key, (k, v) -> { throw new RuntimeException(); });
      });
      fail("Should not get here");
    } catch (RuntimeException ex) {
    }
    // Lava: the entry is not left stuck in a loading state (Guava 27.0.1 does leave it stuck, see
    // the package documentation), so the cache stays usable.
    assertEquals(0, cache.size());
    assertNull(cache.getIfPresent(key));
    assertEquals("value", cache.asMap().computeIfAbsent(key, k -> "value"));
    assertEquals("value", cache.getIfPresent(key));
  }

  @Test
  public void testComputeExceptionally_existingEntryRestored() {
    // Lava: when the function throws for an existing entry, the old value stays.
    cache.put(key, "1");
    try {
      cache.asMap().compute(key, (k, v) -> { throw new IllegalStateException("boom"); });
      fail();
    } catch (IllegalStateException expected) {
      assertEquals("boom", expected.getMessage());
    }
    assertEquals("1", cache.getIfPresent(key));
    assertEquals(1, cache.size());
    assertEquals("12", cache.asMap().compute(key, (k, v) -> v + "2"));
  }
}
