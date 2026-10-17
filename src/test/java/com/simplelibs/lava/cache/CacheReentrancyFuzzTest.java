package com.simplelibs.lava.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Re-entrancy fuzz of Lava's cache, which {@link CacheDifferentialTest} cannot compare with Guava
 * (Guava blocks or leaves entries stuck on some of these paths): seeded random operations whose
 * removal listeners, loaders, bulk loaders, {@code Callable}s, compute functions and iterations
 * call back into the cache (up to two levels deep) or throw, on random configurations with
 * bounds, expiration, refresh, weak or soft values and same-thread asynchronous reloading. After
 * every operation, with the queued reloads completed, the {@linkplain
 * CacheTesting#invariantViolations internal invariants} must hold and the size must agree with
 * iteration.
 */
class CacheReentrancyFuzzTest {
  private static final int ROUNDS_PER_SEED = 25;
  private static final int STEPS = 60;
  private static final Logger CACHE_LOGGER = Logger.getLogger(LocalCache.class.getName());
  private static Level savedLevel;

  @BeforeAll
  static void silenceFailedRefreshLogging() {
    savedLevel = CACHE_LOGGER.getLevel();
    CACHE_LOGGER.setLevel(Level.OFF);
  }

  @AfterAll
  static void restoreLogging() {
    CACHE_LOGGER.setLevel(savedLevel);
  }

  /** A FIFO executor drained explicitly, used for same-thread asynchronous reloading. */
  private static final class QueueExecutor implements Executor {
    final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

    @Override
    public void execute(Runnable command) {
      tasks.add(command);
    }
  }

  private static final class Config {
    final int bound;
    final long maximum;
    final long expireAfterWrite;
    final long expireAfterAccess;
    final long refreshAfterWrite;
    final boolean weakValues;
    final boolean softValues;
    final boolean loading;
    final boolean async;
    final boolean bulk;
    final boolean listener;
    final double reentryChance;
    final boolean throwing;

    Config(Random random) {
      bound = random.nextInt(3);
      maximum = bound == 1 ? random.nextInt(5) : 3 + random.nextInt(20);
      expireAfterWrite = random.nextInt(3) == 0 ? 1 + random.nextInt(6) : -1;
      expireAfterAccess = random.nextInt(4) == 0 ? 1 + random.nextInt(6) : -1;
      loading = random.nextInt(4) != 0;
      refreshAfterWrite = loading && random.nextInt(4) == 0 ? 1 + random.nextInt(4) : -1;
      weakValues = random.nextInt(8) == 0;
      softValues = !weakValues && random.nextInt(8) == 0;
      async = loading && random.nextBoolean();
      bulk = random.nextBoolean();
      listener = random.nextInt(3) != 0;
      reentryChance = new double[] {0.15, 0.4, 0.8}[random.nextInt(3)];
      throwing = random.nextBoolean();
    }

    @Override
    public String toString() {
      return "bound=" + bound + " max=" + maximum + " eaw=" + expireAfterWrite + " eaa="
          + expireAfterAccess + " raw=" + refreshAfterWrite + " weakValues=" + weakValues
          + " softValues=" + softValues + " loading=" + loading + " async=" + async + " bulk="
          + bulk + " listener=" + listener + " reentry=" + reentryChance + " throwing=" + throwing;
    }
  }

  /** One round: a cache, its configuration and the state of the hooks. */
  private static final class Round {
    final Config config;
    final long[] now = {0};
    final QueueExecutor executor = new QueueExecutor();
    final StringBuilder trace = new StringBuilder();
    final Cache<Integer, Integer> cache;
    final LoadingCache<Integer, Integer> loadingCache;
    Random random;
    int depth;
    int budget;

    Round(Config config) {
      this.config = config;
      CacheBuilder<Object, Object> builder =
          CacheBuilder.newBuilder()
              .recordStats()
              .ticker(
                  new com.simplelibs.lava.base.Ticker() {
                    @Override
                    public long read() {
                      return now[0];
                    }
                  });
      if (config.bound == 1) {
        builder.maximumSize(config.maximum);
      } else if (config.bound == 2) {
        builder.maximumWeight(config.maximum).weigher((Integer k, Integer v) -> Math.abs(v % 6));
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
      if (config.weakValues) {
        builder.weakValues(); // values stay within the Integer cache, so are never collected
      } else if (config.softValues) {
        builder.softValues();
      }
      CacheBuilder<Integer, Integer> typed =
          config.listener
              ? builder.removalListener((RemovalNotification<Integer, Integer> n) -> reenter("listener"))
              : typed(builder);
      if (config.loading) {
        CacheLoader<Integer, Integer> loader =
            new CacheLoader<Integer, Integer>() {
              @Override
              public Integer load(Integer key) {
                reenter("load");
                maybeThrow("load");
                return key * 3 + (int) (now[0] % 7);
              }

              @Override
              public Map<Integer, Integer> loadAll(Iterable<? extends Integer> keys)
                  throws Exception {
                if (!Round.this.config.bulk) {
                  return super.loadAll(keys);
                }
                reenter("loadAll");
                maybeThrow("loadAll");
                Map<Integer, Integer> result = new LinkedHashMap<>();
                for (Integer key : keys) {
                  result.put(key, key * 3 + 1);
                }
                return result;
              }
            };
        loadingCache = typed.build(config.async ? CacheLoader.asyncReloading(loader, executor) : loader);
        cache = loadingCache;
      } else {
        loadingCache = null;
        cache = typed.build();
      }
    }

    @SuppressWarnings("unchecked") // the builder takes any keys and values until it has a listener
    private static CacheBuilder<Integer, Integer> typed(CacheBuilder<Object, Object> builder) {
      return (CacheBuilder<Integer, Integer>) (CacheBuilder<?, ?>) builder;
    }

    /** With some probability, calls back into the cache from a hook; failures propagate. */
    void reenter(String where) {
      if (depth >= 2 || budget-- <= 0 || random.nextDouble() > config.reentryChance) {
        return;
      }
      int k = random.nextInt(5);
      int v = random.nextInt(30);
      int what = random.nextInt(24);
      trace.append('[').append(where).append(':').append(what).append('(').append(k).append(',')
          .append(v).append(")] ");
      depth++;
      try {
        operation(what, k, v);
      } finally {
        depth--;
      }
    }

    void maybeThrow(String where) {
      if (config.throwing && random.nextInt(8) == 0) {
        trace.append("[throw@").append(where).append("] ");
        throw new IllegalStateException("hook " + where);
      }
    }

    /** Runs operation {@code what}; nested operations are drawn from the first 24. */
    Object operation(int what, int k, int v) {
      Map<Integer, Integer> map = cache.asMap();
      switch (what) {
        case 0:
          return loadingCache != null ? loadingCache.getUnchecked(k) : cache.getIfPresent(k);
        case 1:
          cache.put(k, v);
          return "put";
        case 2:
          cache.invalidate(k);
          return "invalidate";
        case 3:
          return cache.getIfPresent(k);
        case 4:
          return map.put(k, v);
        case 5:
          return map.remove(k);
        case 6:
          return map.replace(k, v);
        case 7:
          return map.putIfAbsent(k, v);
        case 8:
          return map.compute(
              k,
              (key, old) -> {
                reenter("compute");
                maybeThrow("compute");
                int choice = random.nextInt(4);
                return choice == 0 ? null : choice == 1 && old != null ? old : (Integer) v;
              });
        case 9:
          return map.computeIfAbsent(
              k,
              key -> {
                reenter("computeIfAbsent");
                maybeThrow("computeIfAbsent");
                return v;
              });
        case 10:
          return map.computeIfPresent(
              k,
              (key, old) -> {
                reenter("computeIfPresent");
                maybeThrow("computeIfPresent");
                return random.nextBoolean() ? null : (Integer) ((old + 1) % 100);
              });
        case 11:
          return map.merge(
              k,
              v,
              (a, b) -> {
                reenter("merge");
                maybeThrow("merge");
                return random.nextBoolean() ? null : (Integer) ((a + b) % 100);
              });
        case 12:
          cache.cleanUp();
          return "cleanUp";
        case 13:
          return cache.size();
        case 14:
          if (loadingCache != null) {
            loadingCache.refresh(k);
          }
          return "refresh";
        case 15:
          return loadingCache != null
              ? loadingCache.getUnchecked((k + 1) % 5) + "/" + loadingCacheGetAll(k)
              : "-";
        case 16:
          return map.keySet().removeIf(key -> key % 2 == k % 2);
        case 17:
          for (Map.Entry<Integer, Integer> e : map.entrySet()) {
            return e.setValue(v);
          }
          return "empty";
        case 18:
          return map.entrySet().removeIf(e -> e.getValue() % 3 == v % 3);
        case 19:
          return cache.getAllPresent(Arrays.asList(k, (k + 2) % 5));
        case 20:
          return map.replace(k, v, v + 1);
        case 21:
          return map.remove(k, v);
        case 22:
          try {
            return cache.get(
                k,
                () -> {
                  reenter("callable");
                  maybeThrow("callable");
                  return v;
                });
          } catch (java.util.concurrent.ExecutionException e) {
            return "!" + e.getCause().getClass().getSimpleName();
          }
        case 23:
          cache.invalidateAll();
          return "invalidateAll";
        case 24:
          now[0] += 1 + v % 4;
          return "tick";
        case 25:
          {
            int count = 0;
            java.util.Iterator<Map.Entry<Integer, Integer>> it = map.entrySet().iterator();
            while (it.hasNext()) {
              it.next();
              reenter("iteration");
              if (++count == 2) {
                it.remove();
              }
            }
            return "iterated " + count;
          }
        case 26:
          now[0] += 6;
          return "big tick";
        default:
          return map.containsKey(k) + "/" + map.containsValue(v) + "/" + cache.size();
      }
    }

    private Object loadingCacheGetAll(int k) {
      try {
        return loadingCache.getAll(Arrays.asList(k, (k + 1) % 5, (k + 2) % 5));
      } catch (Exception e) {
        return e.getClass().getSimpleName();
      }
    }

    /** Completes the queued reloads, whose completions may queue more. */
    void settle() {
      for (int spin = 0; spin < 20 && !executor.tasks.isEmpty(); spin++) {
        Runnable task;
        while ((task = executor.tasks.poll()) != null) {
          task.run();
        }
        cache.cleanUp();
      }
      assertThat(executor.tasks).as("reloads keep queueing reloads").isEmpty();
    }
  }

  @TestFactory
  List<DynamicTest> reentrantOperationsKeepTheCacheConsistent() {
    List<DynamicTest> tests = new ArrayList<>();
    String seeds = System.getenv("LAVA_CACHE_FUZZ_SEEDS");
    int count = seeds == null ? 40 : Integer.parseInt(seeds);
    for (int i = 0; i < count; i++) {
      long seed = 0x4EE47L + i;
      tests.add(dynamicTest("seed " + seed, () -> fuzz(seed)));
    }
    return tests;
  }

  private static void fuzz(long seed) {
    Random configs = new Random(seed);
    for (int r = 0; r < ROUNDS_PER_SEED; r++) {
      Round round = new Round(new Config(configs));
      round.random = new Random(configs.nextLong());
      LocalCache<Integer, Integer> localCache = CacheTesting.toLocalCache(round.cache);
      for (int step = 0; step < STEPS; step++) {
        int what = round.random.nextInt(28);
        int k = round.random.nextInt(5);
        int v = round.random.nextInt(30);
        round.depth = 0;
        round.budget = 6;
        round.trace.append("\n  ").append(step).append(": ").append(what).append('(').append(k)
            .append(',').append(v).append(") ");
        String result;
        try {
          result = String.valueOf(round.operation(what, k, v));
        } catch (RuntimeException | Error e) {
          result = "!" + e.getClass().getSimpleName();
        }
        round.trace.append("-> ").append(result);
        round.depth = 0;
        round.budget = 0; // the hooks stay quiet while the round settles and is checked
        round.settle();
        String what2 = "seed " + seed + " round " + r + " [" + round.config + "]" + round.trace;
        assertThat(CacheTesting.invariantViolations(localCache, true)).as(what2).isEmpty();
        int iterated = 0;
        for (Map.Entry<Integer, Integer> e : round.cache.asMap().entrySet()) {
          iterated++;
        }
        assertThat(round.cache.size()).as(what2).isEqualTo(iterated);
      }
    }
  }
}
