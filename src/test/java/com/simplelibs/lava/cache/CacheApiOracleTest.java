package com.simplelibs.lava.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.simplelibs.lava.collect.ImmutableList;
import com.simplelibs.lava.util.concurrent.UncheckedExecutionException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Compares the parts of the cache API that Guava's cache tests do not exercise ({@code toString}
 * forms, {@code CacheStats} arithmetic, {@code CacheBuilderSpec} errors and refresh keys, the
 * default methods of {@code AbstractCache}/{@code AbstractLoadingCache}, {@code
 * RemovalListeners.asynchronous}) with the real Guava 27.0.1 on the test classpath.
 *
 * <p>Not a port of a Guava test: an addition for Lava, in the style of {@code
 * CacheDifferentialTest}.
 */
class CacheApiOracleTest {

  // CacheBuilder.toString

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Test
  void cacheBuilderToStringMatchesGuava() {
    for (int mask = 0; mask < (1 << 11); mask++) {
      boolean size = (mask & 4) != 0;
      boolean weight = (mask & 8) != 0 && !size;
      boolean weakValues = (mask & 256) != 0;
      boolean softValues = (mask & 512) != 0 && !weakValues;

      CacheBuilder lava = CacheBuilder.newBuilder();
      com.google.common.cache.CacheBuilder guava = com.google.common.cache.CacheBuilder.newBuilder();
      if ((mask & 1) != 0) {
        lava.initialCapacity(10);
        guava.initialCapacity(10);
      }
      if ((mask & 2) != 0) {
        lava.concurrencyLevel(3);
        guava.concurrencyLevel(3);
      }
      if (size) {
        lava.maximumSize(100);
        guava.maximumSize(100);
      }
      if (weight) {
        lava.maximumWeight(200).weigher((k, v) -> 1);
        guava.maximumWeight(200).weigher((k, v) -> 1);
      }
      if ((mask & 16) != 0) {
        lava.expireAfterWrite(5, TimeUnit.SECONDS);
        guava.expireAfterWrite(5, TimeUnit.SECONDS);
      }
      if ((mask & 32) != 0) {
        lava.expireAfterAccess(7, TimeUnit.MILLISECONDS);
        guava.expireAfterAccess(7, TimeUnit.MILLISECONDS);
      }
      if ((mask & 64) != 0) {
        lava.refreshAfterWrite(1, TimeUnit.MINUTES);
        guava.refreshAfterWrite(1, TimeUnit.MINUTES);
      }
      if ((mask & 128) != 0) {
        lava.weakKeys();
        guava.weakKeys();
      }
      if (weakValues) {
        lava.weakValues();
        guava.weakValues();
      }
      if (softValues) {
        lava.softValues();
        guava.softValues();
      }
      if ((mask & 1024) != 0) {
        lava.removalListener(TestingRemovalListeners.nullRemovalListener());
        guava.removalListener(n -> {});
      }
      assertThat(lava.toString()).as("mask %s", mask).isEqualTo(guava.toString());
    }
  }

  // CacheStats

  private static com.google.common.cache.CacheStats guava(long[] v) {
    return new com.google.common.cache.CacheStats(v[0], v[1], v[2], v[3], v[4], v[5]);
  }

  private static CacheStats lava(long[] v) {
    return new CacheStats(v[0], v[1], v[2], v[3], v[4], v[5]);
  }

  private static void assertSameStats(CacheStats lava, com.google.common.cache.CacheStats guava) {
    assertThat(lava.requestCount()).isEqualTo(guava.requestCount());
    assertThat(lava.hitCount()).isEqualTo(guava.hitCount());
    assertThat(lava.hitRate()).isEqualTo(guava.hitRate());
    assertThat(lava.missCount()).isEqualTo(guava.missCount());
    assertThat(lava.missRate()).isEqualTo(guava.missRate());
    assertThat(lava.loadCount()).isEqualTo(guava.loadCount());
    assertThat(lava.loadSuccessCount()).isEqualTo(guava.loadSuccessCount());
    assertThat(lava.loadExceptionCount()).isEqualTo(guava.loadExceptionCount());
    assertThat(lava.loadExceptionRate()).isEqualTo(guava.loadExceptionRate());
    assertThat(lava.totalLoadTime()).isEqualTo(guava.totalLoadTime());
    assertThat(lava.averageLoadPenalty()).isEqualTo(guava.averageLoadPenalty());
    assertThat(lava.evictionCount()).isEqualTo(guava.evictionCount());
    assertThat(lava.toString()).isEqualTo(guava.toString());
    assertThat(lava.hashCode()).isEqualTo(guava.hashCode());
  }

  @Test
  void cacheStatsMatchesGuava() {
    Random random = new Random(27);
    for (int i = 0; i < 2000; i++) {
      long[] a = new long[6];
      long[] b = new long[6];
      for (int j = 0; j < 6; j++) {
        a[j] = (random.nextInt(4) == 0) ? 0 : random.nextInt(1000);
        b[j] = (random.nextInt(4) == 0) ? 0 : random.nextInt(1000);
      }
      assertSameStats(lava(a), guava(a));
      assertSameStats(lava(a).plus(lava(b)), guava(a).plus(guava(b)));
      assertSameStats(lava(a).minus(lava(b)), guava(a).minus(guava(b)));
      assertThat(lava(a).equals(lava(b))).isEqualTo(guava(a).equals(guava(b)));
      assertThat(lava(a).equals(lava(a.clone()))).isTrue();
    }
    assertThat(lava(new long[6]).equals(null)).isFalse();
    assertThat(lava(new long[6]).equals("x")).isFalse();
    for (int j = 0; j < 6; j++) {
      long[] negative = new long[6];
      negative[j] = -1;
      Throwable expected = null;
      try {
        guava(negative);
      } catch (IllegalArgumentException e) {
        expected = e;
      }
      try {
        lava(negative);
        throw new AssertionError("expected IllegalArgumentException");
      } catch (IllegalArgumentException e) {
        assertThat(expected).isNotNull();
        assertThat(e.getMessage()).isEqualTo(expected.getMessage());
      }
    }
    // plus does not saturate in Guava 27.0.1: an overflow is rejected by the constructor
    long[] big = {Long.MAX_VALUE, 1, 1, 1, 1, 1};
    long[] one = {1, 1, 1, 1, 1, 1};
    Throwable expected = null;
    try {
      guava(big).plus(guava(one));
    } catch (IllegalArgumentException e) {
      expected = e;
    }
    assertThat(expected).isNotNull();
    try {
      lava(big).plus(lava(one));
      throw new AssertionError("expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage()).isEqualTo(expected.getMessage());
    }
    assertSameStats(lava(one).minus(lava(big)), guava(one).minus(guava(big)));
  }

  // CacheBuilderSpec

  private static final List<String> VALID_SPECS =
      ImmutableList.of(
          "",
          "initialCapacity=10",
          "maximumSize=100",
          "maximumWeight=7",
          "concurrencyLevel=3",
          "weakKeys",
          "softValues",
          "weakValues",
          "recordStats",
          "expireAfterAccess=1d",
          "expireAfterAccess=24h",
          "expireAfterWrite=5h",
          "refreshAfterWrite=30m",
          "refreshAfterWrite=1800s",
          "refreshInterval=10s",
          " initialCapacity = 10 ,  weakKeys ",
          "maximumSize=10,weakKeys,softValues,expireAfterWrite=1s,refreshAfterWrite=1s,"
              + "recordStats,concurrencyLevel=1,initialCapacity=2");

  private static final List<String> INVALID_SPECS =
      ImmutableList.of(
          "foo=17",
          "weakKeys=true",
          "weakKeys,weakKeys",
          "softValues,weakValues",
          "initialCapacity",
          "initialCapacity=x",
          "initialCapacity=1,initialCapacity=2",
          "maximumSize=",
          "maximumSize=1,maximumSize=2",
          "maximumSize=1,maximumWeight=2",
          "maximumWeight=1,maximumSize=2",
          "concurrencyLevel=1,concurrencyLevel=2",
          "expireAfterWrite=10",
          "expireAfterWrite=10x",
          "expireAfterWrite=",
          "expireAfterWrite=1s,expireAfterWrite=2s",
          "expireAfterAccess=1s,expireAfterAccess=2s",
          "refreshAfterWrite=5",
          "refreshAfterWrite=1s,refreshInterval=2s",
          "a=b=c",
          ",weakKeys",
          "weakKeys,",
          "weakKeys,,softValues",
          "recordStats=true",
          "recordStats,recordStats",
          "initialCapacity=99999999999",
          "initialCapacity=1.5",
          "maximumSize=x",
          "maximumSize=1.5",
          "maximumWeight=x",
          "concurrencyLevel=x",
          "expireAfterWrite=1.5s",
          "expireAfterAccess=xs",
          "refreshAfterWrite=xm",
          "=1");

  @Test
  void cacheBuilderSpecMatchesGuava() {
    List<CacheBuilderSpec> lava = new ArrayList<>();
    List<com.google.common.cache.CacheBuilderSpec> guava = new ArrayList<>();
    for (String text : VALID_SPECS) {
      CacheBuilderSpec l = CacheBuilderSpec.parse(text);
      com.google.common.cache.CacheBuilderSpec g = com.google.common.cache.CacheBuilderSpec.parse(text);
      assertThat(l.toString()).as(text).isEqualTo(g.toString());
      assertThat(l.toParsableString()).as(text).isEqualTo(g.toParsableString());
      assertThat(CacheBuilderSpec.parse(l.toParsableString())).isEqualTo(l);

      assertThat(CacheBuilder.from(l).toString())
          .as(text)
          .isEqualTo(com.google.common.cache.CacheBuilder.from(g).toString());
      lava.add(l);
      guava.add(g);
    }
    for (int i = 0; i < lava.size(); i++) {
      for (int j = 0; j < lava.size(); j++) {
        boolean equal = guava.get(i).equals(guava.get(j));
        assertThat(lava.get(i).equals(lava.get(j)))
            .as("%s equals %s", VALID_SPECS.get(i), VALID_SPECS.get(j))
            .isEqualTo(equal);
        if (equal) {
          assertThat(lava.get(i).hashCode()).isEqualTo(lava.get(j).hashCode());
        }
      }
    }
    assertThat(CacheBuilderSpec.disableCaching().toParsableString())
        .isEqualTo(com.google.common.cache.CacheBuilderSpec.disableCaching().toParsableString());
    assertThat(CacheBuilderSpec.disableCaching()).isEqualTo(CacheBuilderSpec.parse("maximumSize=0"));
    assertThat(CacheBuilderSpec.parse("")).isNotEqualTo("");

    for (String text : INVALID_SPECS) {
      String expected = null;
      try {
        com.google.common.cache.CacheBuilderSpec.parse(text);
      } catch (IllegalArgumentException e) {
        expected = e.getMessage();
      }
      try {
        CacheBuilderSpec.parse(text);
        throw new AssertionError("Lava accepted " + text + " (Guava: " + expected + ")");
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage()).as(text).isEqualTo(expected);
      }
    }
  }

  // AbstractCache and AbstractLoadingCache defaults

  private static final class LavaCache extends AbstractLoadingCache<String, Integer> {
    final List<String> calls = new ArrayList<>();
    final Map<String, Integer> contents = new LinkedHashMap<>();

    @Override
    public Integer getIfPresent(Object key) {
      calls.add("getIfPresent(" + key + ")");
      return contents.get(key);
    }

    @Override
    public void put(String key, Integer value) {
      calls.add("put(" + key + ", " + value + ")");
      contents.put(key, value);
    }

    @Override
    public void invalidate(Object key) {
      calls.add("invalidate(" + key + ")");
      contents.remove(key);
    }

    @Override
    public Integer get(String key) throws ExecutionException {
      calls.add("get(" + key + ")");
      if (key.startsWith("bad")) {
        throw new ExecutionException(new IllegalStateException(key));
      }
      return key.length();
    }
  }

  private static final class GuavaCache
      extends com.google.common.cache.AbstractLoadingCache<String, Integer> {
    final List<String> calls = new ArrayList<>();
    final Map<String, Integer> contents = new LinkedHashMap<>();

    @Override
    public Integer getIfPresent(Object key) {
      calls.add("getIfPresent(" + key + ")");
      return contents.get(key);
    }

    @Override
    public void put(String key, Integer value) {
      calls.add("put(" + key + ", " + value + ")");
      contents.put(key, value);
    }

    @Override
    public void invalidate(Object key) {
      calls.add("invalidate(" + key + ")");
      contents.remove(key);
    }

    @Override
    public Integer get(String key) throws ExecutionException {
      calls.add("get(" + key + ")");
      if (key.startsWith("bad")) {
        throw new ExecutionException(new IllegalStateException(key));
      }
      return key.length();
    }
  }

  @Test
  void abstractCacheDefaultsMatchGuava() throws Exception {
    LavaCache lava = new LavaCache();
    GuavaCache guava = new GuavaCache();

    lava.putAll(mapOf("a", 1, "b", 2));
    guava.putAll(com.google.common.collect.ImmutableMap.of("a", 1, "b", 2));
    assertThat(lava.getAllPresent(Arrays.asList("b", "x", "a", "b")))
        .isEqualTo(guava.getAllPresent(Arrays.asList("b", "x", "a", "b")));
    assertThat(new ArrayList<>(lava.getAllPresent(Arrays.asList("b", "x", "a", "b")).keySet()))
        .isEqualTo(
            new ArrayList<>(guava.getAllPresent(Arrays.asList("b", "x", "a", "b")).keySet()));
    lava.invalidateAll(Arrays.asList("b", "zz", "a"));
    guava.invalidateAll(Arrays.asList("b", "zz", "a"));
    assertThat(lava.getAll(Arrays.asList("ccc", "d", "ccc", "ee")))
        .isEqualTo(guava.getAll(Arrays.asList("ccc", "d", "ccc", "ee")));
    assertThat(new ArrayList<>(lava.getAll(Arrays.asList("ccc", "d", "ccc", "ee")).keySet()))
        .isEqualTo(new ArrayList<>(guava.getAll(Arrays.asList("ccc", "d", "ccc", "ee")).keySet()));
    assertThat(lava.getUnchecked("four")).isEqualTo(guava.getUnchecked("four"));
    assertThat(lava.apply("four")).isEqualTo(guava.apply("four"));
    lava.cleanUp();
    guava.cleanUp();
    assertThat(lava.calls).isEqualTo(guava.calls);

    try {
      lava.getAll(Arrays.asList("ok", "bad1", "bad2"));
      throw new AssertionError();
    } catch (ExecutionException e) {
      assertThat(e.getCause()).hasMessage("bad1");
    }
    try {
      guava.getAll(Arrays.asList("ok", "bad1", "bad2"));
      throw new AssertionError();
    } catch (ExecutionException e) {
      assertThat(e.getCause()).hasMessage("bad1");
    }
    try {
      lava.getUnchecked("bad3");
      throw new AssertionError();
    } catch (UncheckedExecutionException e) {
      assertThat(e.getCause()).hasMessage("bad3");
    }
    try {
      guava.getUnchecked("bad3");
      throw new AssertionError();
    } catch (com.google.common.util.concurrent.UncheckedExecutionException e) {
      assertThat(e.getCause()).hasMessage("bad3");
    }
    assertThat(lava.calls).isEqualTo(guava.calls);

    // the methods an AbstractCache does not implement
    List<Class<?>> lavaFailures = new ArrayList<>();
    List<Class<?>> guavaFailures = new ArrayList<>();
    String[] names = {"get(k, callable)", "size", "invalidateAll", "stats", "asMap", "refresh"};
    for (String name : names) {
      lavaFailures.add(failureOf(name, true, lava, guava));
      guavaFailures.add(failureOf(name, false, lava, guava));
    }
    assertThat(lavaFailures).isEqualTo(guavaFailures);
    assertThat(lavaFailures)
        .containsOnly(UnsupportedOperationException.class);
  }

  private static Class<?> failureOf(String name, boolean useLava, LavaCache lava, GuavaCache guava) {
    try {
      switch (name) {
        case "get(k, callable)":
          if (useLava) {
            lava.get("k", () -> 1);
          } else {
            guava.get("k", () -> 1);
          }
          break;
        case "size":
          if (useLava) {
            lava.size();
          } else {
            guava.size();
          }
          break;
        case "invalidateAll":
          if (useLava) {
            lava.invalidateAll();
          } else {
            guava.invalidateAll();
          }
          break;
        case "stats":
          if (useLava) {
            lava.stats();
          } else {
            guava.stats();
          }
          break;
        case "asMap":
          if (useLava) {
            lava.asMap();
          } else {
            guava.asMap();
          }
          break;
        case "refresh":
          if (useLava) {
            lava.refresh("k");
          } else {
            guava.refresh("k");
          }
          break;
        default:
          throw new AssertionError(name);
      }
      return null;
    } catch (Exception e) {
      return e.getClass();
    }
  }

  private static Class<?> invoke(Runnable call) {
    try {
      call.run();
      return null;
    } catch (RuntimeException e) {
      return e.getClass();
    }
  }

  private static Map<String, Integer> mapOf(String k1, int v1, String k2, int v2) {
    Map<String, Integer> map = new LinkedHashMap<>();
    map.put(k1, v1);
    map.put(k2, v2);
    return map;
  }

  // RemovalListeners.asynchronous

  @Test
  void asynchronousRemovalListenerMatchesGuava() {
    List<String> lavaLog = new ArrayList<>();
    List<String> guavaLog = new ArrayList<>();
    ArrayDeque<Runnable> lavaTasks = new ArrayDeque<>();
    ArrayDeque<Runnable> guavaTasks = new ArrayDeque<>();
    RemovalListener<String, Integer> lava =
        RemovalListeners.asynchronous(
            n -> lavaLog.add(n.getKey() + n.getValue() + n.getCause()), lavaTasks::add);
    com.google.common.cache.RemovalListener<String, Integer> guava =
        com.google.common.cache.RemovalListeners.asynchronous(
            n -> guavaLog.add(n.getKey() + n.getValue() + n.getCause()), guavaTasks::add);

    lava.onRemoval(RemovalNotification.create("a", 1, RemovalCause.SIZE));
    guava.onRemoval(com.google.common.cache.RemovalNotification.create("a", 1, com.google.common.cache.RemovalCause.SIZE));
    assertThat(lavaLog).isEmpty();
    assertThat(guavaLog).isEmpty();
    assertThat(lavaTasks).hasSize(1);
    assertThat(guavaTasks).hasSize(1);
    lavaTasks.remove().run();
    guavaTasks.remove().run();
    assertThat(lavaLog).isEqualTo(guavaLog).containsExactly("a1SIZE");

    for (Runnable r : new Runnable[] {
      () -> RemovalListeners.asynchronous(null, Runnable::run),
      () -> RemovalListeners.asynchronous(n -> {}, null)}) {
      assertThat(invoke(r)).isEqualTo(NullPointerException.class);
    }
  }
}
