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

import static java.util.concurrent.TimeUnit.SECONDS;

import com.simplelibs.lava.collect.testing.MapInterfaceTest;
import com.simplelibs.lava.testing.FakeTicker;
import java.util.Map;
import org.junit.jupiter.api.Nested;

/**
 * Lava replacement for the {@code MapTestSuiteBuilder} suites of Guava's {@code LocalCacheTest}:
 * runs the ported {@link MapInterfaceTest} over {@code cache.asMap()} for the same configurations
 * (defaults, {@code concurrencyLevel}, {@code maximumSize}, {@code maximumWeight}, weak keys, weak
 * and soft values, expiration after access and after write with a ticker that never advances, a
 * removal listener, {@code recordStats}), plus a loading cache and a combination of everything.
 *
 * <p>The keys are string literals and the values are small (cached) {@code Integer}s, so entries
 * of the weak and soft configurations are never collected during a test.
 *
 * @author Charles Fry
 */
class CacheAsMapContractTest {

  /** Builds the cache under test; subclasses choose the configuration. */
  abstract static class CacheMapTest extends MapInterfaceTest<String, Integer> {
    CacheMapTest() {
      // no null keys or values; put, remove, clear and iterator remove are all supported
      super(false, false, true, true, true, true);
    }

    /** Returns the cache to test, empty. */
    abstract Cache<String, Integer> makeCache();

    @Override
    protected Map<String, Integer> makeEmptyMap() {
      return makeCache().asMap();
    }

    @Override
    protected Map<String, Integer> makePopulatedMap() {
      Cache<String, Integer> cache = makeCache();
      cache.put("one", 1);
      cache.put("two", 2);
      cache.put("three", 3);
      cache.put("four", 4);
      return cache.asMap();
    }

    @Override
    protected String getKeyNotInPopulatedMap() {
      return "six";
    }

    @Override
    protected Integer getValueNotInPopulatedMap() {
      return 6;
    }
  }

  private static CacheBuilder<Object, Object> builder() {
    return CacheBuilder.newBuilder();
  }

  @Nested
  class Defaults extends CacheMapTest {
    @Override
    Cache<String, Integer> makeCache() {
      return builder().build();
    }
  }

  @Nested
  class ConcurrencyLevel1 extends CacheMapTest {
    @Override
    Cache<String, Integer> makeCache() {
      return builder().concurrencyLevel(1).build();
    }
  }

  @Nested
  class MaximumSize extends CacheMapTest {
    @Override
    Cache<String, Integer> makeCache() {
      return builder().maximumSize(Integer.MAX_VALUE).build();
    }
  }

  @Nested
  class MaximumWeight extends CacheMapTest {
    @Override
    Cache<String, Integer> makeCache() {
      return builder()
          .maximumWeight(Integer.MAX_VALUE)
          .weigher(TestingWeighers.constantWeigher(1))
          .build();
    }
  }

  @Nested
  class WeakKeys extends CacheMapTest {
    @Override
    Cache<String, Integer> makeCache() {
      return builder().weakKeys().build();
    }
  }

  @Nested
  class WeakValues extends CacheMapTest {
    @Override
    Cache<String, Integer> makeCache() {
      return builder().weakValues().build();
    }
  }

  @Nested
  class SoftValues extends CacheMapTest {
    @Override
    Cache<String, Integer> makeCache() {
      return builder().softValues().build();
    }
  }

  @Nested
  class ExpireAfterAccess extends CacheMapTest {
    @Override
    Cache<String, Integer> makeCache() {
      return builder().expireAfterAccess(1, SECONDS).ticker(new FakeTicker()).build();
    }
  }

  @Nested
  class ExpireAfterWrite extends CacheMapTest {
    @Override
    Cache<String, Integer> makeCache() {
      return builder().expireAfterWrite(1, SECONDS).ticker(new FakeTicker()).build();
    }
  }

  @Nested
  class RemovalListenerConfigured extends CacheMapTest {
    @Override
    Cache<String, Integer> makeCache() {
      return builder().removalListener(TestingRemovalListeners.nullRemovalListener()).build();
    }
  }

  @Nested
  class RecordStats extends CacheMapTest {
    @Override
    Cache<String, Integer> makeCache() {
      return builder().recordStats().build();
    }
  }

  @Nested
  class LoadingCacheAsMap extends CacheMapTest {
    @Override
    Cache<String, Integer> makeCache() {
      return builder().build(TestingCacheLoaders.<String, Integer>constantLoader(0));
    }
  }

  @Nested
  class EverythingAtOnce extends CacheMapTest {
    @Override
    Cache<String, Integer> makeCache() {
      return builder()
          .weakKeys()
          .softValues()
          .maximumSize(1000)
          .expireAfterAccess(1, SECONDS)
          .expireAfterWrite(1, SECONDS)
          .refreshAfterWrite(1, SECONDS)
          .ticker(new FakeTicker())
          .recordStats()
          .removalListener(TestingRemovalListeners.nullRemovalListener())
          .build(TestingCacheLoaders.<String, Integer>constantLoader(0));
    }
  }
}
