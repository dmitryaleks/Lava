package com.simplelibs.lava.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Differential tests against Guava 27.0.1: seeded random operation sequences (the {@code Cache}
 * and {@code LoadingCache} methods, every {@code asMap()} method including the compute family and
 * view {@code removeIf}s, iteration, write-through entries, ticker advances and {@code cleanUp})
 * run against a Lava cache and a Guava cache built from the same random configuration (size or
 * weight bounds, expiration after write and access, refresh, synchronous or same-thread
 * asynchronous reloading, bulk loading, failing loaders). Guava is built with {@code
 * concurrencyLevel(1)}, so its eviction is the same exact LRU as Lava's, and both read one shared
 * fake clock.
 *
 * <p>Every result, including exception classes, messages and causes, must match. Because Lava runs
 * maintenance eagerly where Guava defers it (see {@code LocalCache}), sizes, eviction counts and
 * removal notifications are compared after {@code cleanUp()}, as sorted multisets.
 *
 * <p>Deliberate differences are not exercised here: compute functions never throw and never
 * remove an existing entry by returning null, loaders do not re-enter the cache, and asynchronous
 * reloads are completed after every operation (Guava would block a single thread on a pending
 * refresh in some of these cases). Those paths have dedicated tests.
 */
class CacheDifferentialTest {
  private static final int ROUNDS = 250;
  private static final int OPS = 60;
  private static final int KEYS = 10;

  /** The shared fake clock, in nanoseconds. */
  private static final class Clock {
    long nanos;
  }

  /** A FIFO executor drained explicitly, used for same-thread asynchronous reloading. */
  private static final class QueueExecutor implements Executor {
    final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

    @Override
    public void execute(Runnable command) {
      tasks.add(command);
    }

    void runAll() {
      Runnable task;
      while ((task = tasks.poll()) != null) {
        task.run();
      }
    }
  }

  /** A random cache configuration, applied identically to both libraries. */
  private static final class Config {
    final boolean loading;
    final long maximumSize;
    final long maximumWeight;
    final long expireAfterWrite;
    final long expireAfterAccess;
    final long refreshAfterWrite;
    final boolean bulk;
    final boolean async;

    Config(Random random) {
      loading = random.nextInt(5) != 0;
      int bound = random.nextInt(10);
      maximumSize = bound < 3 ? new long[] {0, 1, 2, 3, 5}[random.nextInt(5)] : -1;
      maximumWeight = bound >= 3 && bound < 5 ? new long[] {0, 2, 4, 7}[random.nextInt(4)] : -1;
      expireAfterWrite = random.nextInt(2) == 0 ? new long[] {0, 1, 3, 5}[random.nextInt(4)] : -1;
      expireAfterAccess = random.nextInt(3) == 0 ? new long[] {2, 4}[random.nextInt(2)] : -1;
      refreshAfterWrite =
          loading && random.nextInt(3) == 0 ? new long[] {1, 2, 4}[random.nextInt(3)] : -1;
      bulk = random.nextBoolean();
      async = loading && random.nextInt(3) == 0;
    }

    @Override
    public String toString() {
      return "loading=" + loading + " maxSize=" + maximumSize + " maxWeight=" + maximumWeight
          + " eaw=" + expireAfterWrite + " eaa=" + expireAfterAccess + " raw=" + refreshAfterWrite
          + " bulk=" + bulk + " async=" + async;
    }
  }

  /** The common surface of both libraries' caches. */
  private interface Side {
    Integer get(int key) throws Exception;

    Integer getUnchecked(int key);

    Integer getIfPresent(int key);

    Integer get(int key, Callable<Integer> loader) throws Exception;

    void put(int key, int value);

    void putAll(Map<Integer, Integer> map);

    void invalidate(int key);

    void invalidateAll(Iterable<?> keys);

    void invalidateAll();

    Map<Integer, Integer> getAll(Iterable<Integer> keys) throws Exception;

    Map<Integer, Integer> getAllPresent(Iterable<?> keys);

    void refresh(int key);

    long size();

    void cleanUp();

    String stats(boolean withEvictions);

    ConcurrentMap<Integer, Integer> asMap();

    List<String> notifications();

    QueueExecutor executor();
  }

  /**
   * Loader behaviour shared by both sides; each side owns one instance. Results depend on per-key
   * attempt counts only, so loads of different keys may happen in either order (operations such
   * as {@code removeIf} reach stale entries, and refresh them, in iteration order).
   */
  private static final class LoaderLogic {
    private final Map<Integer, Integer> attempts = new HashMap<>();

    private int nextAttempt(Integer key) {
      return attempts.merge(key, 1, Integer::sum);
    }

    Integer load(Integer key) throws Exception {
      int attempt = nextAttempt(key);
      switch (key) {
        case 6:
          if (attempt % 2 == 1) {
            throw new IllegalStateException("boom " + key);
          }
          break;
        case 7:
          return null;
        case 8:
          throw new Exception("checked " + key);
        case 9:
          if (attempt % 3 == 0) {
            throw new AssertionError("error " + key);
          }
          break;
        default:
      }
      return key * 1000 + attempt;
    }

    Map<Integer, Integer> loadAll(Iterable<? extends Integer> keys) throws Exception {
      Map<Integer, Integer> result = new LinkedHashMap<>();
      for (Integer key : keys) {
        if (key == 6) {
          throw new IllegalStateException("bulk boom");
        }
        result.put(key, key == 7 ? null : (Integer) (key * 1000 + nextAttempt(key)));
      }
      if (result.size() > 1) {
        result.put(KEYS + result.size(), -1); // an extra key, which the cache stores too
      }
      return result;
    }
  }

  private static final class LavaSide implements Side {
    final Cache<Integer, Integer> cache;
    final List<String> notifications = new ArrayList<>();
    final QueueExecutor executor = new QueueExecutor();

    LavaSide(Config config, Clock clock) {
      CacheBuilder<Object, Object> builder =
          CacheBuilder.newBuilder()
              .recordStats()
              .ticker(
                  new com.simplelibs.lava.base.Ticker() {
                    @Override
                    public long read() {
                      return clock.nanos;
                    }
                  });
      if (config.maximumSize >= 0) {
        builder.maximumSize(config.maximumSize);
      }
      if (config.maximumWeight >= 0) {
        builder.maximumWeight(config.maximumWeight).weigher((Integer k, Integer v) -> Math.abs(v % 3));
      }
      if (config.expireAfterWrite >= 0) {
        builder.expireAfterWrite(config.expireAfterWrite, TimeUnit.NANOSECONDS);
      }
      if (config.expireAfterAccess >= 0) {
        builder.expireAfterAccess(config.expireAfterAccess, TimeUnit.NANOSECONDS);
      }
      if (config.refreshAfterWrite >= 0) {
        builder.refreshAfterWrite(config.refreshAfterWrite, TimeUnit.NANOSECONDS);
      }
      CacheBuilder<Integer, Integer> typed =
          builder.removalListener(
              (RemovalNotification<Integer, Integer> n) ->
                  notifications.add(n.getCause() + ":" + n.getKey() + "=" + n.getValue()));
      if (config.loading) {
        LoaderLogic logic = new LoaderLogic();
        CacheLoader<Integer, Integer> loader =
            config.bulk
                ? new CacheLoader<Integer, Integer>() {
                  @Override
                  public Integer load(Integer key) throws Exception {
                    return logic.load(key);
                  }

                  @Override
                  public Map<Integer, Integer> loadAll(Iterable<? extends Integer> keys)
                      throws Exception {
                    return logic.loadAll(keys);
                  }

                  @Override
                  public String toString() {
                    return "TestLoader";
                  }
                }
                : new CacheLoader<Integer, Integer>() {
                  @Override
                  public Integer load(Integer key) throws Exception {
                    return logic.load(key);
                  }

                  @Override
                  public String toString() {
                    return "TestLoader";
                  }
                };
        cache = typed.build(config.async ? CacheLoader.asyncReloading(loader, executor) : loader);
      } else {
        cache = typed.build();
      }
    }

    private LoadingCache<Integer, Integer> loading() {
      return (LoadingCache<Integer, Integer>) cache;
    }

    @Override
    public Integer get(int key) throws Exception {
      return loading().get(key);
    }

    @Override
    public Integer getUnchecked(int key) {
      return loading().getUnchecked(key);
    }

    @Override
    public Integer getIfPresent(int key) {
      return cache.getIfPresent(key);
    }

    @Override
    public Integer get(int key, Callable<Integer> loader) throws Exception {
      return cache.get(key, loader);
    }

    @Override
    public void put(int key, int value) {
      cache.put(key, value);
    }

    @Override
    public void putAll(Map<Integer, Integer> map) {
      cache.putAll(map);
    }

    @Override
    public void invalidate(int key) {
      cache.invalidate(key);
    }

    @Override
    public void invalidateAll(Iterable<?> keys) {
      cache.invalidateAll(keys);
    }

    @Override
    public void invalidateAll() {
      cache.invalidateAll();
    }

    @Override
    public Map<Integer, Integer> getAll(Iterable<Integer> keys) throws Exception {
      return loading().getAll(keys);
    }

    @Override
    public Map<Integer, Integer> getAllPresent(Iterable<?> keys) {
      return cache.getAllPresent(keys);
    }

    @Override
    public void refresh(int key) {
      loading().refresh(key);
    }

    @Override
    public long size() {
      return cache.size();
    }

    @Override
    public void cleanUp() {
      cache.cleanUp();
    }

    @Override
    public String stats(boolean withEvictions) {
      CacheStats s = cache.stats();
      return s.hitCount() + "/" + s.missCount() + "/" + s.loadSuccessCount() + "/"
          + s.loadExceptionCount() + (withEvictions ? "/" + s.evictionCount() : "");
    }

    @Override
    public ConcurrentMap<Integer, Integer> asMap() {
      return cache.asMap();
    }

    @Override
    public List<String> notifications() {
      return notifications;
    }

    @Override
    public QueueExecutor executor() {
      return executor;
    }
  }

  private static final class GuavaSide implements Side {
    final com.google.common.cache.Cache<Integer, Integer> cache;
    final List<String> notifications = new ArrayList<>();
    final QueueExecutor executor = new QueueExecutor();

    GuavaSide(Config config, Clock clock) {
      com.google.common.cache.CacheBuilder<Object, Object> builder =
          com.google.common.cache.CacheBuilder.newBuilder()
              .concurrencyLevel(1)
              .recordStats()
              .ticker(
                  new com.google.common.base.Ticker() {
                    @Override
                    public long read() {
                      return clock.nanos;
                    }
                  });
      if (config.maximumSize >= 0) {
        builder.maximumSize(config.maximumSize);
      }
      if (config.maximumWeight >= 0) {
        builder.maximumWeight(config.maximumWeight).weigher((Integer k, Integer v) -> Math.abs(v % 3));
      }
      if (config.expireAfterWrite >= 0) {
        builder.expireAfterWrite(config.expireAfterWrite, TimeUnit.NANOSECONDS);
      }
      if (config.expireAfterAccess >= 0) {
        builder.expireAfterAccess(config.expireAfterAccess, TimeUnit.NANOSECONDS);
      }
      if (config.refreshAfterWrite >= 0) {
        builder.refreshAfterWrite(config.refreshAfterWrite, TimeUnit.NANOSECONDS);
      }
      com.google.common.cache.CacheBuilder<Integer, Integer> typed =
          builder.removalListener(
              (com.google.common.cache.RemovalNotification<Integer, Integer> n) ->
                  notifications.add(n.getCause() + ":" + n.getKey() + "=" + n.getValue()));
      if (config.loading) {
        LoaderLogic logic = new LoaderLogic();
        com.google.common.cache.CacheLoader<Integer, Integer> loader =
            config.bulk
                ? new com.google.common.cache.CacheLoader<Integer, Integer>() {
                  @Override
                  public Integer load(Integer key) throws Exception {
                    return logic.load(key);
                  }

                  @Override
                  public Map<Integer, Integer> loadAll(Iterable<? extends Integer> keys)
                      throws Exception {
                    return logic.loadAll(keys);
                  }

                  @Override
                  public String toString() {
                    return "TestLoader";
                  }
                }
                : new com.google.common.cache.CacheLoader<Integer, Integer>() {
                  @Override
                  public Integer load(Integer key) throws Exception {
                    return logic.load(key);
                  }

                  @Override
                  public String toString() {
                    return "TestLoader";
                  }
                };
        cache =
            typed.build(
                config.async
                    ? com.google.common.cache.CacheLoader.asyncReloading(loader, executor)
                    : loader);
      } else {
        cache = typed.build();
      }
    }

    private com.google.common.cache.LoadingCache<Integer, Integer> loading() {
      return (com.google.common.cache.LoadingCache<Integer, Integer>) cache;
    }

    @Override
    public Integer get(int key) throws Exception {
      return loading().get(key);
    }

    @Override
    public Integer getUnchecked(int key) {
      return loading().getUnchecked(key);
    }

    @Override
    public Integer getIfPresent(int key) {
      return cache.getIfPresent(key);
    }

    @Override
    public Integer get(int key, Callable<Integer> loader) throws Exception {
      return cache.get(key, loader);
    }

    @Override
    public void put(int key, int value) {
      cache.put(key, value);
    }

    @Override
    public void putAll(Map<Integer, Integer> map) {
      cache.putAll(map);
    }

    @Override
    public void invalidate(int key) {
      cache.invalidate(key);
    }

    @Override
    public void invalidateAll(Iterable<?> keys) {
      cache.invalidateAll(keys);
    }

    @Override
    public void invalidateAll() {
      cache.invalidateAll();
    }

    @Override
    public Map<Integer, Integer> getAll(Iterable<Integer> keys) throws Exception {
      return loading().getAll(keys);
    }

    @Override
    public Map<Integer, Integer> getAllPresent(Iterable<?> keys) {
      return cache.getAllPresent(keys);
    }

    @Override
    public void refresh(int key) {
      loading().refresh(key);
    }

    @Override
    public long size() {
      return cache.size();
    }

    @Override
    public void cleanUp() {
      cache.cleanUp();
    }

    @Override
    public String stats(boolean withEvictions) {
      com.google.common.cache.CacheStats s = cache.stats();
      return s.hitCount() + "/" + s.missCount() + "/" + s.loadSuccessCount() + "/"
          + s.loadExceptionCount() + (withEvictions ? "/" + s.evictionCount() : "");
    }

    @Override
    public ConcurrentMap<Integer, Integer> asMap() {
      return cache.asMap();
    }

    @Override
    public List<String> notifications() {
      return notifications;
    }

    @Override
    public QueueExecutor executor() {
      return executor;
    }
  }

  /** An operation on a side, described by its result (or thrown exception and cause). */
  private interface Op {
    Object run(Side side) throws Exception;
  }

  /** Set LAVA_CACHE_FUZZ_TRACE=seed:round to print every step of one round. */
  private static final String TRACE = System.getenv("LAVA_CACHE_FUZZ_TRACE");

  private static String outcome(Side side, Op op) {
    String result;
    try {
      result = "= " + show(op.run(side));
    } catch (Throwable t) {
      result = "! " + describe(t);
      if (tracing) {
        t.printStackTrace(System.out);
      }
    }
    side.executor().runAll();
    return result;
  }

  private static boolean tracing;

  private static String describe(Throwable t) {
    // The asynchronous-reloading wrappers are anonymous classes with identity toStrings.
    String message =
        String.valueOf(t.getMessage()).replaceAll("[\\w.]*CacheLoader\\$\\d+@[0-9a-f]+", "AsyncLoader");
    String s = t.getClass().getSimpleName() + "(" + message + ")";
    if (t.getCause() != null && t.getCause() != t) {
      s += " caused by " + describe(t.getCause());
    }
    return s;
  }

  private static String show(Object o) {
    if (o instanceof Map) {
      return new TreeMap<>((Map<?, ?>) o) + (o instanceof com.google.common.collect.ImmutableMap
              || o instanceof com.simplelibs.lava.collect.ImmutableMap
          ? " in order " + ((Map<?, ?>) o).keySet()
          : "");
    }
    return String.valueOf(o);
  }

  private static List<String> sortedEntries(ConcurrentMap<Integer, Integer> map) {
    List<String> entries = new ArrayList<>();
    for (Map.Entry<Integer, Integer> e : map.entrySet()) {
      entries.add(e.getKey() + "=" + e.getValue());
    }
    Collections.sort(entries);
    return entries;
  }

  @TestFactory
  List<DynamicTest> randomOperationSequencesMatchGuava() {
    List<DynamicTest> tests = new ArrayList<>();
    // LAVA_CACHE_FUZZ_SEEDS raises the number of seeds for longer local runs.
    String seeds = System.getenv("LAVA_CACHE_FUZZ_SEEDS");
    int chunks = seeds == null ? 10 : Integer.parseInt(seeds);
    for (int chunk = 0; chunk < chunks; chunk++) {
      long seed = 0xCAC4EL + chunk;
      tests.add(dynamicTest("seed " + seed, () -> fuzz(seed)));
    }
    return tests;
  }

  private static void fuzz(long seed) {
    Random random = new Random(seed);
    for (int round = 0; round < ROUNDS / 10; round++) {
      Config config = new Config(random);
      Clock clock = new Clock();
      Side lava = new LavaSide(config, clock);
      Side guava = new GuavaSide(config, clock);
      tracing = (seed + ":" + round).equals(TRACE);
      StringBuilder trace = new StringBuilder();
      for (int step = 0; step < OPS; step++) {
        int k = random.nextInt(KEYS);
        int v = random.nextInt(20);
        int choice = random.nextInt(34);
        Op op = operation(choice, k, v, config, clock);
        trace.append(choice).append('(').append(k).append(',').append(v).append(") ");
        String what = "seed " + seed + " round " + round + " [" + config + "] ops: " + trace;
        String l = outcome(lava, op);
        String g = outcome(guava, op);
        if (tracing) {
          System.out.println(step + " op " + choice + "(" + k + "," + v + ") lava " + l + " | guava " + g
              + " | lava " + sortedEntries(lava.asMap()) + " guava " + sortedEntries(guava.asMap())
              + " | t=" + clock.nanos + " stats " + lava.stats(false) + " " + guava.stats(false)
              + " notes " + lava.notifications() + " " + guava.notifications());
        }
        assertThat(l).as(what).isEqualTo(g);
        assertThat(lava.stats(false)).as(what + " stats").isEqualTo(guava.stats(false));
        if (choice == 26 || step == OPS - 1) {
          lava.cleanUp();
          guava.cleanUp();
          assertThat(sortedEntries(lava.asMap())).as(what + " contents").isEqualTo(sortedEntries(guava.asMap()));
          assertThat(lava.size()).as(what + " size").isEqualTo(guava.size());
          assertThat(lava.stats(true)).as(what + " stats with evictions").isEqualTo(guava.stats(true));
          List<String> ln = new ArrayList<>(lava.notifications());
          List<String> gn = new ArrayList<>(guava.notifications());
          Collections.sort(ln);
          Collections.sort(gn);
          assertThat(ln).as(what + " notifications").isEqualTo(gn);
          lava.notifications().clear();
          guava.notifications().clear();
        }
      }
    }
  }

  private static Op operation(int choice, int k, int v, Config config, Clock clock) {
    // Operations that touch every entry in iteration order (replaceAll, and the entrySet and values
    // removeIfs, which read each entry) update recency in that order. Iteration order is
    // unspecified and differs between the implementations, so with a size bound, where recency
    // decides what is evicted next, only order-insensitive variants are used.
    boolean recencyDecidesEviction = config.maximumSize >= 0 || config.maximumWeight >= 0;
    if (recencyDecidesEviction && (choice == 30 || (choice == 24 && v % 3 != 1))) {
      return side -> side.asMap().keySet().removeIf(key -> key % 4 == v % 4);
    }
    switch (choice) {
      case 0:
        return config.loading ? side -> side.get(k) : side -> side.getIfPresent(k);
      case 1:
        return config.loading ? side -> side.getUnchecked(k) : side -> side.getIfPresent(k);
      case 2:
        return side -> side.getIfPresent(k);
      case 3:
        return side ->
            side.get(
                k,
                () -> {
                  if (k == 4) {
                    throw new java.io.IOException("callable " + k);
                  }
                  return k == 5 ? null : k * 7 + v;
                });
      case 4:
        return side -> {
          side.put(k, v);
          return "put";
        };
      case 5:
        return side -> {
          Map<Integer, Integer> map = new LinkedHashMap<>();
          map.put(k, v);
          map.put((k + 1) % KEYS, v + 1);
          side.putAll(map);
          return "putAll";
        };
      case 6:
        return side -> {
          side.invalidate(k);
          return "invalidate";
        };
      case 7:
        return side -> {
          side.invalidateAll(Arrays.asList(k, null, (k + 2) % KEYS));
          return "invalidateAll(keys)";
        };
      case 8:
        return v < 5
            ? side -> {
              side.invalidateAll();
              return "invalidateAll";
            }
            : side -> side.getIfPresent(k);
      case 9:
        return config.loading
            ? side -> side.getAll(Arrays.asList(k, (k + 1) % KEYS, k))
            : side -> side.getIfPresent(k);
      case 10:
        return side -> side.getAllPresent(Arrays.asList(k, (k + 1) % KEYS, k, null));
      case 11:
        return config.loading
            ? side -> {
              side.refresh(k);
              return "refresh";
            }
            : side -> side.getIfPresent(k);
      case 12:
        return side -> side.asMap().get(k);
      case 13:
        return side -> side.asMap().put(k, v);
      case 14:
        return side -> side.asMap().putIfAbsent(k, v);
      case 15:
        return side -> side.asMap().remove(k);
      case 16:
        return side -> side.asMap().remove(k, v);
      case 17:
        return side -> side.asMap().replace(k, v);
      case 18:
        return side -> side.asMap().replace(k, v, v + 1);
      case 19:
        return side -> side.asMap().containsKey(k) + "/" + side.asMap().containsValue(v);
      case 20:
        return side -> side.asMap().compute(k, (key, old) -> old == null ? (k == 3 ? null : (Integer) v) : (Integer) (old + 1));
      case 21:
        return side -> side.asMap().computeIfAbsent(k, key -> k == 2 ? null : (Integer) v);
      case 22:
        return side -> side.asMap().computeIfPresent(k, (key, old) -> old + 2);
      case 23:
        return side -> side.asMap().merge(k, v, Integer::sum);
      case 24:
        switch (v % 3) {
          case 0:
            return side -> side.asMap().entrySet().removeIf(e -> e.getValue() % 2 == 0);
          case 1:
            return side -> side.asMap().keySet().removeIf(key -> key % 3 == 0);
          default:
            return side -> side.asMap().values().removeIf(value -> value % 5 == 1);
        }
      case 25:
        return side -> {
          if (side instanceof LavaSide) {
            clock.nanos += v % 4;
          }
          return "tick";
        };
      case 26:
        return side -> {
          side.cleanUp();
          return "cleanUp";
        };
      case 27:
        return side -> {
          Iterator<Integer> keys = side.asMap().keySet().iterator();
          while (keys.hasNext()) {
            if (keys.next() == k) {
              keys.remove();
              return "removed through iterator";
            }
          }
          return sortedEntries(side.asMap());
        };
      case 28:
        return side -> side.asMap().getOrDefault(k, -1);
      case 29:
        return side -> {
          for (Map.Entry<Integer, Integer> entry : side.asMap().entrySet()) {
            if (entry.getKey() == k) {
              return entry.setValue(v) + " " + entry;
            }
          }
          return "absent";
        };
      case 30:
        return side -> {
          side.asMap().replaceAll((key, value) -> value + 1);
          return sortedEntries(side.asMap());
        };
      case 31:
        return side -> {
          // Guava's size and isEmpty count expired entries until cleanup, which equals uses.
          side.cleanUp();
          Map<Integer, Integer> copy = new HashMap<>(side.asMap());
          return side.asMap().equals(copy) + "/" + (side.asMap().hashCode() == copy.hashCode())
              + "/" + side.asMap().isEmpty() + "/" + side.asMap().entrySet().contains(
                  new java.util.AbstractMap.SimpleImmutableEntry<>(k, v));
        };
      case 32:
        return side -> side.asMap().values().contains(v) + "/" + side.asMap().keySet().contains(k)
            + "/" + side.asMap().entrySet().remove(new java.util.AbstractMap.SimpleImmutableEntry<>(k, v));
      default:
        return side -> {
          if (side instanceof LavaSide) {
            clock.nanos += 1 + v % 6;
          }
          return "big tick";
        };
    }
  }
}
