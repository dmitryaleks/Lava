package com.simplelibs.lava.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
 * fake clock. Keys come from a small pool with colliding hash codes and equal "twins" (distinct
 * keys under {@code weakKeys}); configurations include weak keys and weak or soft values (values
 * stay strongly reachable, so nothing is collected) and weights that exceed the maximum.
 *
 * <p>Every result, including exception classes, messages and causes, must match, and Lava's
 * internal invariants ({@link CacheTesting#invariantViolations}) must hold after every operation.
 * Because Lava runs maintenance eagerly where Guava defers it (see {@code LocalCache}), timed
 * configurations compare sizes, eviction counts and removal notifications after {@code
 * cleanUp()}, as sorted multisets. Untimed ones compare them after every operation, and the
 * notifications in exact order unless an operation since the last comparison worked in iteration
 * order, which is unspecified.
 *
 * <p>Deliberate differences are not exercised here: compute functions never throw and never
 * remove an existing entry by returning null, loaders do not re-enter the cache, and asynchronous
 * reloads are completed after every operation (Guava would block a single thread on a pending
 * refresh in some of these cases). Those paths have dedicated tests, and {@link
 * CacheReentrancyFuzzTest} checks Lava's invariants on them.
 */
class CacheDifferentialTest {
  private static final int ROUNDS_PER_SEED = 25;
  private static final int OPS = 80;
  private static final int KEYS = 10;

  /**
   * A key whose hash codes collide in threes. {@link #POOL} holds keys 0 to 9 and a second,
   * equal instance of keys 0 to 2.
   */
  private static final class Key {
    final int id;

    Key(int id) {
      this.id = id;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Key && ((Key) o).id == id;
    }

    @Override
    public int hashCode() {
      return id % 3;
    }

    @Override
    public String toString() {
      return "K" + id;
    }
  }

  private static final Key[] POOL = new Key[KEYS + 3];

  /** The extra keys that bulk loads return, which the caches store too. */
  private static final Key[] EXTRA = new Key[KEYS + 4];

  static {
    for (int i = 0; i < POOL.length; i++) {
      POOL[i] = new Key(i % KEYS);
    }
    for (int i = 0; i < EXTRA.length; i++) {
      EXTRA[i] = new Key(KEYS + i);
    }
  }

  /**
   * Keeps every value within the {@code Integer} cache, so that weakly and softly referenced
   * values stay strongly reachable and are never collected.
   */
  private static Integer bounded(int value) {
    return value % 120;
  }

  /** Weighs 0 to 2, except that 19 weighs more than any maximum. */
  private static int weigh(Integer value) {
    return value == 19 ? 20 : Math.abs(value % 3);
  }

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
    final boolean weakKeys;
    final boolean weakValues;
    final boolean softValues;

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
      weakKeys = random.nextInt(5) == 0;
      int values = random.nextInt(6);
      weakValues = values == 0;
      softValues = values == 1;
    }

    boolean timed() {
      return expireAfterWrite >= 0 || expireAfterAccess >= 0 || refreshAfterWrite >= 0;
    }

    @Override
    public String toString() {
      return "loading=" + loading + " maxSize=" + maximumSize + " maxWeight=" + maximumWeight
          + " eaw=" + expireAfterWrite + " eaa=" + expireAfterAccess + " raw=" + refreshAfterWrite
          + " bulk=" + bulk + " async=" + async + " weakKeys=" + weakKeys + " weakValues="
          + weakValues + " softValues=" + softValues;
    }
  }

  /** The common surface of both libraries' caches. */
  private interface Side {
    Integer get(Key key) throws Exception;

    Integer getUnchecked(Key key);

    Integer getIfPresent(Key key);

    Integer get(Key key, Callable<Integer> loader) throws Exception;

    void put(Key key, int value);

    void putAll(Map<Key, Integer> map);

    void invalidate(Key key);

    void invalidateAll(Iterable<?> keys);

    void invalidateAll();

    Map<Key, Integer> getAll(Iterable<Key> keys) throws Exception;

    Map<Key, Integer> getAllPresent(Iterable<?> keys);

    void refresh(Key key);

    long size();

    void cleanUp();

    String stats(boolean withEvictions);

    ConcurrentMap<Key, Integer> asMap();

    List<String> notifications();

    QueueExecutor executor();
  }

  /**
   * Loader behaviour shared by both sides; each side owns one instance. Results depend on per-key
   * attempt counts only, so loads of different keys may happen in either order (operations such
   * as {@code removeIf} reach stale entries, and refresh them, in iteration order).
   */
  private static final class LoaderLogic {
    // by identity: under weakKeys, twins are different keys
    private final Map<Key, Integer> attempts = new IdentityHashMap<>();

    private int nextAttempt(Key key) {
      return attempts.merge(key, 1, Integer::sum);
    }

    Integer load(Key key) throws Exception {
      int attempt = nextAttempt(key);
      switch (key.id) {
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
      return bounded(key.id * 10 + attempt % 10);
    }

    Map<Key, Integer> loadAll(Iterable<? extends Key> keys) throws Exception {
      Map<Key, Integer> result = new LinkedHashMap<>();
      for (Key key : keys) {
        if (key.id == 6) {
          throw new IllegalStateException("bulk boom");
        }
        result.put(key, key.id == 7 ? null : bounded(key.id * 10 + nextAttempt(key) % 10));
      }
      if (result.size() > 1) {
        Key extra = EXTRA[result.size()];
        result.put(extra, -extra.id); // an extra key, which the cache stores too
      }
      return result;
    }
  }

  private static final class LavaSide implements Side {
    final Cache<Key, Integer> cache;
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
        builder.maximumWeight(config.maximumWeight).weigher((Key k, Integer v) -> weigh(v));
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
      if (config.weakKeys) {
        builder.weakKeys();
      }
      if (config.weakValues) {
        builder.weakValues();
      } else if (config.softValues) {
        builder.softValues();
      }
      CacheBuilder<Key, Integer> typed =
          builder.removalListener(
              (RemovalNotification<Key, Integer> n) ->
                  notifications.add(n.getCause() + ":" + n.getKey() + "=" + n.getValue()));
      if (config.loading) {
        LoaderLogic logic = new LoaderLogic();
        CacheLoader<Key, Integer> loader =
            config.bulk
                ? new CacheLoader<Key, Integer>() {
                  @Override
                  public Integer load(Key key) throws Exception {
                    return logic.load(key);
                  }

                  @Override
                  public Map<Key, Integer> loadAll(Iterable<? extends Key> keys)
                      throws Exception {
                    return logic.loadAll(keys);
                  }

                  @Override
                  public String toString() {
                    return "TestLoader";
                  }
                }
                : new CacheLoader<Key, Integer>() {
                  @Override
                  public Integer load(Key key) throws Exception {
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

    private LoadingCache<Key, Integer> loading() {
      return (LoadingCache<Key, Integer>) cache;
    }

    @Override
    public Integer get(Key key) throws Exception {
      return loading().get(key);
    }

    @Override
    public Integer getUnchecked(Key key) {
      return loading().getUnchecked(key);
    }

    @Override
    public Integer getIfPresent(Key key) {
      return cache.getIfPresent(key);
    }

    @Override
    public Integer get(Key key, Callable<Integer> loader) throws Exception {
      return cache.get(key, loader);
    }

    @Override
    public void put(Key key, int value) {
      cache.put(key, value);
    }

    @Override
    public void putAll(Map<Key, Integer> map) {
      cache.putAll(map);
    }

    @Override
    public void invalidate(Key key) {
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
    public Map<Key, Integer> getAll(Iterable<Key> keys) throws Exception {
      return loading().getAll(keys);
    }

    @Override
    public Map<Key, Integer> getAllPresent(Iterable<?> keys) {
      return cache.getAllPresent(keys);
    }

    @Override
    public void refresh(Key key) {
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
    public ConcurrentMap<Key, Integer> asMap() {
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
    final com.google.common.cache.Cache<Key, Integer> cache;
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
        builder.maximumWeight(config.maximumWeight).weigher((Key k, Integer v) -> weigh(v));
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
      if (config.weakKeys) {
        builder.weakKeys();
      }
      if (config.weakValues) {
        builder.weakValues();
      } else if (config.softValues) {
        builder.softValues();
      }
      com.google.common.cache.CacheBuilder<Key, Integer> typed =
          builder.removalListener(
              (com.google.common.cache.RemovalNotification<Key, Integer> n) ->
                  notifications.add(n.getCause() + ":" + n.getKey() + "=" + n.getValue()));
      if (config.loading) {
        LoaderLogic logic = new LoaderLogic();
        com.google.common.cache.CacheLoader<Key, Integer> loader =
            config.bulk
                ? new com.google.common.cache.CacheLoader<Key, Integer>() {
                  @Override
                  public Integer load(Key key) throws Exception {
                    return logic.load(key);
                  }

                  @Override
                  public Map<Key, Integer> loadAll(Iterable<? extends Key> keys)
                      throws Exception {
                    return logic.loadAll(keys);
                  }

                  @Override
                  public String toString() {
                    return "TestLoader";
                  }
                }
                : new com.google.common.cache.CacheLoader<Key, Integer>() {
                  @Override
                  public Integer load(Key key) throws Exception {
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

    private com.google.common.cache.LoadingCache<Key, Integer> loading() {
      return (com.google.common.cache.LoadingCache<Key, Integer>) cache;
    }

    @Override
    public Integer get(Key key) throws Exception {
      return loading().get(key);
    }

    @Override
    public Integer getUnchecked(Key key) {
      return loading().getUnchecked(key);
    }

    @Override
    public Integer getIfPresent(Key key) {
      return cache.getIfPresent(key);
    }

    @Override
    public Integer get(Key key, Callable<Integer> loader) throws Exception {
      return cache.get(key, loader);
    }

    @Override
    public void put(Key key, int value) {
      cache.put(key, value);
    }

    @Override
    public void putAll(Map<Key, Integer> map) {
      cache.putAll(map);
    }

    @Override
    public void invalidate(Key key) {
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
    public Map<Key, Integer> getAll(Iterable<Key> keys) throws Exception {
      return loading().getAll(keys);
    }

    @Override
    public Map<Key, Integer> getAllPresent(Iterable<?> keys) {
      return cache.getAllPresent(keys);
    }

    @Override
    public void refresh(Key key) {
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
    public ConcurrentMap<Key, Integer> asMap() {
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
      List<String> entries = new ArrayList<>();
      for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
        entries.add(e.getKey() + "=" + e.getValue());
      }
      String order =
          o instanceof com.google.common.collect.ImmutableMap
                  || o instanceof com.simplelibs.lava.collect.ImmutableMap
              ? " in order " + entries
              : "";
      Collections.sort(entries);
      return entries + order;
    }
    return String.valueOf(o);
  }

  private static List<String> sortedEntries(Map<Key, Integer> map) {
    List<String> entries = new ArrayList<>();
    for (Map.Entry<Key, Integer> e : map.entrySet()) {
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
    int count = seeds == null ? 40 : Integer.parseInt(seeds);
    for (int i = 0; i < count; i++) {
      long seed = 0xCAC4EL + i;
      tests.add(dynamicTest("seed " + seed, () -> fuzz(seed)));
    }
    return tests;
  }

  /** Operations whose notifications (or recency updates) follow the unspecified iteration order. */
  private static final List<Integer> ITERATION_ORDER_OPERATIONS = List.of(8, 24, 27, 29, 30, 33, 34);

  private static void fuzz(long seed) {
    Random random = new Random(seed);
    for (int round = 0; round < ROUNDS_PER_SEED; round++) {
      Config config = new Config(random);
      Clock clock = new Clock();
      LavaSide lava = new LavaSide(config, clock);
      Side guava = new GuavaSide(config, clock);
      LocalCache<Key, Integer> localCache = CacheTesting.toLocalCache(lava.cache);
      tracing = (seed + ":" + round).equals(TRACE);
      StringBuilder trace = new StringBuilder();
      boolean ordered = true;
      for (int step = 0; step < OPS; step++) {
        Key k = POOL[random.nextInt(POOL.length)];
        Key k2 = POOL[random.nextInt(POOL.length)];
        int v = random.nextInt(20);
        int choice = random.nextInt(36);
        Op op = operation(choice, k, k2, v, config, clock);
        if (ITERATION_ORDER_OPERATIONS.contains(choice)) {
          ordered = false;
        }
        trace.append(choice).append('(').append(k).append(',').append(k2).append(',').append(v)
            .append(") ");
        String what = "seed " + seed + " round " + round + " [" + config + "] ops: " + trace;
        String l = outcome(lava, op);
        String g = outcome(guava, op);
        if (tracing) {
          System.out.println(step + " op " + choice + "(" + k + "," + k2 + "," + v + ") lava " + l
              + " | guava " + g + " | lava " + sortedEntries(lava.asMap()) + " guava "
              + sortedEntries(guava.asMap()) + " | t=" + clock.nanos + " stats "
              + lava.stats(false) + " " + guava.stats(false) + " notes " + lava.notifications()
              + " " + guava.notifications());
        }
        assertThat(l).as(what).isEqualTo(g);
        assertThat(CacheTesting.invariantViolations(localCache, true)).as(what + " invariants").isEmpty();
        assertThat(lava.stats(false)).as(what + " stats").isEqualTo(guava.stats(false));
        if (choice == 26 || step == OPS - 1 || !config.timed()) {
          if (config.timed()) {
            lava.cleanUp();
            guava.cleanUp();
          }
          assertThat(sortedEntries(lava.asMap())).as(what + " contents").isEqualTo(sortedEntries(guava.asMap()));
          assertThat(lava.size()).as(what + " size").isEqualTo(guava.size());
          assertThat(lava.stats(true)).as(what + " stats with evictions").isEqualTo(guava.stats(true));
          List<String> ln = new ArrayList<>(lava.notifications());
          List<String> gn = new ArrayList<>(guava.notifications());
          boolean exact = ordered && !config.timed();
          if (!exact) {
            Collections.sort(ln);
            Collections.sort(gn);
          }
          assertThat(ln).as(what + (exact ? " notifications in order" : " notifications")).isEqualTo(gn);
          lava.notifications().clear();
          guava.notifications().clear();
          ordered = true;
        }
      }
    }
  }

  /** Whether the caches see {@code a} and {@code b} as the same key. */
  private static boolean same(Key a, Key b, Config config) {
    return config.weakKeys ? a == b : a.equals(b);
  }

  private static Op operation(int choice, Key k, Key k2, int v, Config config, Clock clock) {
    // Operations that touch every entry in iteration order (replaceAll, and the entrySet and values
    // removeIfs, which read each entry) update recency in that order. Iteration order is
    // unspecified and differs between the implementations, so with a size bound, where recency
    // decides what is evicted next, only order-insensitive variants are used.
    boolean recencyDecidesEviction = config.maximumSize >= 0 || config.maximumWeight >= 0;
    if (recencyDecidesEviction && (choice == 30 || (choice == 24 && v % 3 != 1))) {
      return side -> side.asMap().keySet().removeIf(key -> key.id % 4 == v % 4);
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
                  if (k.id == 4) {
                    throw new java.io.IOException("callable " + k);
                  }
                  return k.id == 5 ? null : bounded(k.id * 7 + v);
                });
      case 4:
        return side -> {
          side.put(k, v);
          return "put";
        };
      case 5:
        return side -> {
          Map<Key, Integer> map = new LinkedHashMap<>();
          map.put(k, v);
          map.put(k2, v + 1);
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
          side.invalidateAll(Arrays.asList(k, null, k2));
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
            ? side -> side.getAll(Arrays.asList(k, k2, k))
            : side -> side.getIfPresent(k);
      case 10:
        return side -> side.getAllPresent(Arrays.asList(k, k2, k, null));
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
        return side ->
            side.asMap().compute(
                k, (key, old) -> old == null ? (k.id == 3 ? null : (Integer) v) : bounded(old + 1));
      case 21:
        return side -> side.asMap().computeIfAbsent(k, key -> k.id == 2 ? null : (Integer) v);
      case 22:
        return side -> side.asMap().computeIfPresent(k, (key, old) -> bounded(old + 2));
      case 23:
        return side -> side.asMap().merge(k, v, (a, b) -> bounded(a + b));
      case 24:
        switch (v % 3) {
          case 0:
            return side -> side.asMap().entrySet().removeIf(e -> e.getValue() % 2 == 0);
          case 1:
            return side -> side.asMap().keySet().removeIf(key -> key.id % 3 == 0);
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
          Iterator<Key> keys = side.asMap().keySet().iterator();
          while (keys.hasNext()) {
            if (same(keys.next(), k, config)) {
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
          for (Map.Entry<Key, Integer> entry : side.asMap().entrySet()) {
            if (same(entry.getKey(), k, config)) {
              return entry.setValue(v) + " " + entry;
            }
          }
          return "absent";
        };
      case 30:
        return side -> {
          side.asMap().replaceAll((key, value) -> bounded(value + 1));
          return sortedEntries(side.asMap());
        };
      case 31:
        return side -> {
          // Guava's size and isEmpty count expired entries until cleanup, which equals uses.
          side.cleanUp();
          Map<Key, Integer> copy = new HashMap<>(side.asMap());
          // Under weakKeys, equal twins are distinct keys, which the copy merges in iteration order.
          int entryHashes = 0;
          for (Map.Entry<Key, Integer> e : side.asMap().entrySet()) {
            entryHashes += e.hashCode();
          }
          int expectedHash = config.weakKeys ? entryHashes : copy.hashCode();
          return side.asMap().equals(copy) + "/" + (side.asMap().hashCode() == expectedHash)
              + "/" + side.asMap().isEmpty() + "/" + side.asMap().entrySet().contains(
                  new java.util.AbstractMap.SimpleImmutableEntry<>(k, v));
        };
      case 32:
        return side -> side.asMap().values().contains(v) + "/" + side.asMap().keySet().contains(k)
            + "/" + side.asMap().entrySet().remove(new java.util.AbstractMap.SimpleImmutableEntry<>(k, v));
      case 33:
        return side -> side.asMap().keySet().retainAll(Arrays.asList(k, k2));
      case 34:
        return side -> side.asMap().values().remove(-(KEYS + 2));
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
