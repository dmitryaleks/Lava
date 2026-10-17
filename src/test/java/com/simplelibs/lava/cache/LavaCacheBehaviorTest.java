package com.simplelibs.lava.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.simplelibs.lava.testing.FakeTicker;
import com.simplelibs.lava.util.concurrent.ExecutionError;
import com.simplelibs.lava.util.concurrent.Futures;
import com.simplelibs.lava.util.concurrent.ListenableFuture;
import com.simplelibs.lava.util.concurrent.UncheckedExecutionException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Tests for the behaviour that is specific to Lava's single-threaded cache, documented in the
 * {@code cache} package and {@code LocalCache}: re-entrant use from loaders, compute functions and
 * removal listeners, the deliberate fixes of Guava 27.0.1's stuck loading entries, asynchronous
 * refresh on a same-thread executor, notification timing, and reference reclamation.
 */
class LavaCacheBehaviorTest {

  /** A FIFO executor that the test drains, standing in for an event loop. */
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

  private static List<String> notifications(List<RemovalNotification<?, ?>> raw) {
    List<String> result = new ArrayList<>();
    for (RemovalNotification<?, ?> n : raw) {
      result.add(n.getCause() + ":" + n.getKey() + "=" + n.getValue());
    }
    return result;
  }

  private static <K, V> LocalCache<K, V> local(Cache<K, V> cache) {
    return ((LocalCache.LocalManualCache<K, V>) cache).localCache;
  }

  // Re-entrancy from loaders

  @Test
  void recursiveLoadOfTheSameKeyFailsFast() {
    LoadingCache<String, String>[] holder = new LoadingCache[1];
    holder[0] =
        CacheBuilder.newBuilder()
            .recordStats()
            .build(CacheLoader.from(key -> key.equals("a") ? holder[0].getUnchecked("a") : key));
    assertThatThrownBy(() -> holder[0].getUnchecked("a"))
        .isInstanceOf(UncheckedExecutionException.class)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage("Recursive load of: a");
    // The failed load left nothing behind.
    assertThat(holder[0].asMap()).isEmpty();
    assertThat(holder[0].stats().loadExceptionCount()).isEqualTo(1);
    assertThat(holder[0].getIfPresent("a")).isNull();
  }

  @Test
  void recursiveLoadsOfOtherKeysWork() {
    LoadingCache<Integer, String>[] holder = new LoadingCache[1];
    holder[0] =
        CacheBuilder.newBuilder()
            .build(
                CacheLoader.from(
                    key -> key == 0 ? "0" : key + ", " + holder[0].getUnchecked(key - 1)));
    assertThat(holder[0].getUnchecked(3)).isEqualTo("3, 2, 1, 0");
    assertThat(holder[0].asMap()).hasSize(4);
  }

  @Test
  void readsAndWritesOfTheLoadingKeyFromInsideTheLoader() throws Exception {
    List<RemovalNotification<?, ?>> removed = new ArrayList<>();
    LoadingCache<String, String>[] holder = new LoadingCache[1];
    List<String> seen = new ArrayList<>();
    holder[0] =
        CacheBuilder.newBuilder()
            .recordStats()
            .removalListener(removed::add)
            .build(
                new CacheLoader<String, String>() {
                  @Override
                  public String load(String key) {
                    LoadingCache<String, String> cache = holder[0];
                    seen.add("getIfPresent=" + cache.getIfPresent(key));
                    seen.add("containsKey=" + cache.asMap().containsKey(key));
                    cache.invalidate(key); // a no-op while loading, as in Guava
                    cache.put(key, "put during load");
                    return "loaded";
                  }
                });
    // The loaded value is returned, but the value put during the load wins (as in Guava).
    assertThat(holder[0].get("k")).isEqualTo("loaded");
    assertThat(holder[0].getIfPresent("k")).isEqualTo("put during load");
    assertThat(seen).containsExactly("getIfPresent=null", "containsKey=false");
    assertThat(notifications(removed)).containsExactly("REPLACED:k=loaded");
  }

  @Test
  void computeOfTheLoadingKeyFromInsideTheLoaderFailsInsteadOfDeadlocking() {
    LoadingCache<String, String>[] holder = new LoadingCache[1];
    holder[0] =
        CacheBuilder.newBuilder()
            .build(CacheLoader.from(key -> holder[0].asMap().compute(key, (k, v) -> "c")));
    assertThatThrownBy(() -> holder[0].getUnchecked("k"))
        .isInstanceOf(UncheckedExecutionException.class)
        .hasRootCauseMessage("Recursive load of: k");
  }

  @Test
  void invalidateAllDuringALoadLetsTheLoadedValueLand() {
    List<RemovalNotification<?, ?>> removed = new ArrayList<>();
    LoadingCache<String, String>[] holder = new LoadingCache[1];
    holder[0] =
        CacheBuilder.newBuilder()
            .removalListener(removed::add)
            .build(
                CacheLoader.from(
                    key -> {
                      holder[0].invalidateAll();
                      return key.toUpperCase(java.util.Locale.ROOT);
                    }));
    holder[0].put("x", "X0");
    assertThat(holder[0].getUnchecked("y")).isEqualTo("Y");
    assertThat(holder[0].asMap()).containsOnlyKeys("y");
    assertThat(notifications(removed)).containsExactly("EXPLICIT:x=X0");
  }

  // Compute: Lava fixes Guava 27.0.1's stuck loading entries

  @Test
  void computeThatThrowsRestoresTheEntry() {
    List<RemovalNotification<?, ?>> removed = new ArrayList<>();
    LoadingCache<String, Integer> cache =
        CacheBuilder.newBuilder()
            .maximumSize(2)
            .removalListener(removed::add)
            .build(CacheLoader.from(key -> 100));
    cache.put("a", 1);
    cache.put("b", 2);
    assertThatThrownBy(
            () ->
                cache
                    .asMap()
                    .compute(
                        "a",
                        (k, v) -> {
                          throw new IllegalStateException("boom");
                        }))
        .hasMessage("boom");
    assertThat(cache.getIfPresent("a")).isEqualTo(1);
    assertThat(cache.asMap().compute("a", (k, v) -> v + 1)).isEqualTo(2);
    // An absent key is not left behind either.
    assertThatThrownBy(
            () ->
                cache
                    .asMap()
                    .computeIfAbsent(
                        "z",
                        k -> {
                          throw new IllegalStateException("boom");
                        }))
        .hasMessage("boom");
    assertThat(cache.getUnchecked("z")).isEqualTo(100);
    assertThat(notifications(removed)).contains("REPLACED:a=1");
  }

  @Test
  void computeReturningNullRemovesTheEntryCleanly() throws Exception {
    List<RemovalNotification<?, ?>> removed = new ArrayList<>();
    LoadingCache<String, Integer> cache =
        CacheBuilder.newBuilder()
            .recordStats()
            .removalListener(removed::add)
            .build(CacheLoader.from(key -> 100));
    cache.put("a", 1);
    assertThat(cache.asMap().compute("a", (k, v) -> null)).isNull();
    assertThat(cache.size()).isZero();
    assertThat(notifications(removed)).containsExactly("EXPLICIT:a=1");
    // Guava 27.0.1 throws InvalidCacheLoadException here; Lava loads normally.
    assertThat(cache.get("a")).isEqualTo(100);
    cache.put("b", 2);
    assertThat(cache.asMap().computeIfPresent("b", (k, v) -> null)).isNull();
    assertThat(cache.get("b")).isEqualTo(100);
  }

  @Test
  void computeReturningTheSameInstanceSendsNoNotification() {
    List<RemovalNotification<?, ?>> removed = new ArrayList<>();
    Cache<String, String> cache = CacheBuilder.newBuilder().removalListener(removed::add).build();
    String value = new String("v");
    cache.put("a", value);
    assertThat(cache.asMap().computeIfAbsent("a", k -> "other")).isSameAs(value);
    assertThat(cache.asMap().compute("a", (k, v) -> v)).isSameAs(value);
    assertThat(removed).isEmpty();
  }

  // Refresh

  @Test
  void asynchronousRefreshOnASameThreadExecutor() throws Exception {
    QueueExecutor executor = new QueueExecutor();
    FakeTicker ticker = new FakeTicker();
    int[] loads = new int[1];
    List<RemovalNotification<?, ?>> removed = new ArrayList<>();
    LoadingCache<String, Integer> cache =
        CacheBuilder.newBuilder()
            .ticker(ticker)
            .refreshAfterWrite(1, TimeUnit.NANOSECONDS)
            .recordStats()
            .removalListener(removed::add)
            .build(CacheLoader.asyncReloading(CacheLoader.from(key -> ++loads[0]), executor));
    assertThat(cache.get("k")).isEqualTo(1);
    ticker.advance(2);
    // The stale read returns the old value and schedules the reload.
    assertThat(cache.get("k")).isEqualTo(1);
    assertThat(executor.tasks).hasSize(1);
    // A second stale read does not schedule another one.
    assertThat(cache.get("k")).isEqualTo(1);
    assertThat(executor.tasks).hasSize(1);
    executor.runAll();
    assertThat(cache.getIfPresent("k")).isEqualTo(2);
    assertThat(notifications(removed)).containsExactly("REPLACED:k=1");
    assertThat(cache.stats().loadSuccessCount()).isEqualTo(2);
  }

  @Test
  void pendingRefreshIsSupersededWhenItsEntryIsGone() throws Exception {
    QueueExecutor executor = new QueueExecutor();
    FakeTicker ticker = new FakeTicker();
    int[] loads = new int[1];
    List<RemovalNotification<?, ?>> removed = new ArrayList<>();
    LoadingCache<String, Integer> cache =
        CacheBuilder.newBuilder()
            .ticker(ticker)
            .refreshAfterWrite(1, TimeUnit.NANOSECONDS)
            .removalListener(removed::add)
            .build(CacheLoader.asyncReloading(CacheLoader.from(key -> ++loads[0]), executor));
    cache.get("k");
    ticker.advance(2);
    cache.get("k"); // schedules a refresh
    cache.invalidate("k"); // removes the value while the refresh is pending
    // Guava would wait for the pending refresh here (forever, on one thread); Lava loads.
    assertThat(cache.get("k")).isEqualTo(2);
    executor.runAll(); // the stale refresh completes and is discarded
    assertThat(cache.getIfPresent("k")).isEqualTo(2);
    assertThat(notifications(removed)).containsExactly("EXPLICIT:k=1", "REPLACED:k=3");
  }

  @Test
  void failedRefreshIsLoggedAndKeepsTheOldValue() {
    FakeTicker ticker = new FakeTicker();
    boolean[] fail = new boolean[1];
    LoadingCache<String, String> cache =
        CacheBuilder.newBuilder()
            .ticker(ticker)
            .refreshAfterWrite(1, TimeUnit.NANOSECONDS)
            .recordStats()
            .build(
                CacheLoader.from(
                    key -> {
                      if (fail[0]) {
                        throw new IllegalStateException("refresh failed");
                      }
                      return "v";
                    }));
    cache.getUnchecked("k");
    ticker.advance(2);
    fail[0] = true;
    List<LogRecord> records = new ArrayList<>();
    Handler handler = recordingHandler(records);
    LocalCache.logger.addHandler(handler);
    boolean useParent = LocalCache.logger.getUseParentHandlers();
    LocalCache.logger.setUseParentHandlers(false);
    try {
      assertThat(cache.getUnchecked("k")).isEqualTo("v");
      assertThat(cache.getUnchecked("k")).isEqualTo("v"); // write time unchanged: retried
    } finally {
      LocalCache.logger.removeHandler(handler);
      LocalCache.logger.setUseParentHandlers(useParent);
    }
    assertThat(records).hasSize(2);
    assertThat(records.get(0).getMessage()).isEqualTo("Exception thrown during refresh");
    assertThat(records.get(0).getThrown())
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(IllegalStateException.class);
    assertThat(cache.stats().loadExceptionCount()).isEqualTo(2);
  }

  @Test
  void refreshOfAnAbsentKeyLoadsIt() {
    LoadingCache<String, String> cache =
        CacheBuilder.newBuilder().recordStats().build(CacheLoader.from(key -> key + "!"));
    cache.refresh("k");
    assertThat(cache.getIfPresent("k")).isEqualTo("k!");
    assertThat(cache.stats().missCount()).isEqualTo(0);
    assertThat(cache.stats().loadSuccessCount()).isEqualTo(1);
  }

  @Test
  void reloadReturningAFailedOrNullFutureIsAFailedRefresh() {
    FakeTicker ticker = new FakeTicker();
    int[] mode = new int[1];
    LoadingCache<String, String> cache =
        CacheBuilder.newBuilder()
            .ticker(ticker)
            .refreshAfterWrite(1, TimeUnit.NANOSECONDS)
            .recordStats()
            .build(
                new CacheLoader<String, String>() {
                  @Override
                  public String load(String key) {
                    return "v";
                  }

                  @Override
                  public ListenableFuture<String> reload(String key, String old) {
                    return mode[0] == 0
                        ? Futures.immediateFailedFuture(new Error("bad"))
                        : mode[0] == 1 ? null : Futures.immediateFuture(null);
                  }
                });
    cache.getUnchecked("k");
    Logger logger = LocalCache.logger;
    boolean useParent = logger.getUseParentHandlers();
    logger.setUseParentHandlers(false);
    try {
      for (mode[0] = 0; mode[0] < 3; mode[0]++) {
        ticker.advance(2);
        assertThat(cache.getUnchecked("k")).isEqualTo("v");
      }
    } finally {
      logger.setUseParentHandlers(useParent);
    }
    assertThat(cache.stats().loadExceptionCount()).isEqualTo(3);
  }

  // Loading results

  @Test
  void loaderExceptionsAreWrappedAsInGuava() {
    LoadingCache<Integer, String> cache =
        CacheBuilder.newBuilder()
            .build(
                new CacheLoader<Integer, String>() {
                  @Override
                  public String load(Integer key) throws Exception {
                    switch (key) {
                      case 0:
                        throw new java.io.IOException("checked");
                      case 1:
                        throw new IllegalStateException("unchecked");
                      case 2:
                        throw new AssertionError("error");
                      case 3:
                        throw new InterruptedException("interrupted");
                      default:
                        return null;
                    }
                  }
                });
    assertThatThrownBy(() -> cache.get(0))
        .isExactlyInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(java.io.IOException.class);
    assertThatThrownBy(() -> cache.getUnchecked(0))
        .isExactlyInstanceOf(UncheckedExecutionException.class)
        .hasCauseInstanceOf(java.io.IOException.class);
    assertThatThrownBy(() -> cache.get(1))
        .isExactlyInstanceOf(UncheckedExecutionException.class)
        .hasCauseInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> cache.get(2))
        .isExactlyInstanceOf(ExecutionError.class)
        .hasCauseInstanceOf(AssertionError.class);
    try {
      assertThatThrownBy(() -> cache.get(3))
          .isExactlyInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(InterruptedException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
    assertThatThrownBy(() -> cache.get(4))
        .isExactlyInstanceOf(CacheLoader.InvalidCacheLoadException.class)
        .hasMessage("CacheLoader returned null for key 4.");
    assertThat(cache.asMap()).isEmpty();
  }

  // Removal listeners

  @Test
  void listenersRunAfterTheStateIsConsistentAndMayReenter() {
    List<String> log = new ArrayList<>();
    Cache<String, String>[] holder = new Cache[1];
    holder[0] =
        CacheBuilder.newBuilder()
            .maximumSize(1)
            .removalListener(
                (RemovalNotification<String, String> n) -> {
                  Cache<String, String> cache = holder[0];
                  log.add(n.getCause() + ":" + n.getKey() + " size=" + cache.size()
                      + " present=" + cache.getIfPresent(n.getKey()));
                  if (n.getKey().equals("a")) {
                    cache.put("c", "C"); // evicts b, queued after this notification
                  }
                })
            .build();
    holder[0].put("a", "A");
    holder[0].put("b", "B"); // evicts a
    assertThat(log)
        .containsExactly(
            "SIZE:a size=1 present=null", "SIZE:b size=1 present=null");
    assertThat(holder[0].asMap()).containsOnlyKeys("c");
  }

  @Test
  void notificationsAreDeliveredBeforeTheLoaderRuns() {
    List<String> log = new ArrayList<>();
    FakeTicker ticker = new FakeTicker();
    LoadingCache<String, String> cache =
        CacheBuilder.newBuilder()
            .ticker(ticker)
            .expireAfterWrite(1, TimeUnit.NANOSECONDS)
            .removalListener((RemovalNotification<String, String> n) -> log.add("removed " + n.getKey()))
            .build(
                CacheLoader.from(
                    key -> {
                      log.add("load " + key);
                      return key;
                    }));
    cache.getUnchecked("a");
    ticker.advance(1);
    cache.getUnchecked("b");
    assertThat(log).containsExactly("load a", "removed a", "load b");
  }

  @Test
  void listenerExceptionsAreLoggedAndSwallowed() {
    Cache<String, String> cache =
        CacheBuilder.newBuilder()
            .removalListener(
                n -> {
                  throw new IllegalStateException("listener");
                })
            .build();
    List<LogRecord> records = new ArrayList<>();
    Handler handler = recordingHandler(records);
    LocalCache.logger.addHandler(handler);
    boolean useParent = LocalCache.logger.getUseParentHandlers();
    LocalCache.logger.setUseParentHandlers(false);
    try {
      cache.put("a", "1");
      cache.put("a", "2");
      cache.invalidateAll();
    } finally {
      LocalCache.logger.removeHandler(handler);
      LocalCache.logger.setUseParentHandlers(useParent);
    }
    assertThat(records).hasSize(2);
    assertThat(records.get(0).getMessage()).isEqualTo("Exception thrown by removal listener");
    assertThat(cache.size()).isZero();
  }

  private static Handler recordingHandler(List<LogRecord> records) {
    return new Handler() {
      @Override
      public void publish(LogRecord record) {
        records.add(record);
      }

      @Override
      public void flush() {}

      @Override
      public void close() {}
    };
  }

  // Maintenance

  @Test
  void sizeNeverCountsExpiredEntries() {
    FakeTicker ticker = new FakeTicker();
    List<RemovalNotification<?, ?>> removed = new ArrayList<>();
    Cache<String, String> cache =
        CacheBuilder.newBuilder()
            .ticker(ticker)
            .expireAfterAccess(2, TimeUnit.NANOSECONDS)
            .recordStats()
            .removalListener(removed::add)
            .build();
    cache.put("a", "A");
    cache.put("b", "B");
    ticker.advance(1);
    cache.getIfPresent("a");
    ticker.advance(1);
    assertThat(cache.size()).isEqualTo(1);
    assertThat(cache.asMap()).containsOnlyKeys("a");
    assertThat(notifications(removed)).containsExactly("EXPIRED:b=B");
    assertThat(cache.stats().evictionCount()).isEqualTo(1);
  }

  @Test
  void iteratorsToleratesMutationAndSkipRemovedEntries() {
    Cache<Integer, Integer> cache = CacheBuilder.newBuilder().build();
    for (int i = 0; i < 10; i++) {
      cache.put(i, i);
    }
    java.util.Iterator<Map.Entry<Integer, Integer>> it = cache.asMap().entrySet().iterator();
    List<Integer> seen = new ArrayList<>();
    while (it.hasNext()) {
      Map.Entry<Integer, Integer> e = it.next();
      seen.add(e.getKey());
      cache.invalidate((e.getKey() + 5) % 10);
      cache.put(100 + e.getKey(), 0);
    }
    assertThat(seen).hasSizeBetween(5, 6).doesNotHaveDuplicates();
    assertThat(cache.asMap().keySet().removeIf(k -> k >= 100)).isTrue();
    java.util.Iterator<Integer> keys = cache.asMap().keySet().iterator();
    assertThatThrownBy(keys::remove).isInstanceOf(IllegalStateException.class);
  }

  // References

  @Test
  void simulatedReclamationOfWeakValues() {
    List<RemovalNotification<?, ?>> removed = new ArrayList<>();
    LoadingCache<String, Object> cache =
        CacheBuilder.newBuilder()
            .weakValues()
            .recordStats()
            .removalListener(removed::add)
            .build(CacheLoader.from(key -> new Object()));
    Object first = cache.getUnchecked("a");
    local(cache).simulateValueReclamation("a");
    assertThat(cache.size()).isZero();
    assertThat(removed).hasSize(1);
    assertThat(removed.get(0).getCause()).isEqualTo(RemovalCause.COLLECTED);
    assertThat(removed.get(0).getKey()).isEqualTo("a");
    assertThat(removed.get(0).getValue()).isNull();
    assertThat(cache.getUnchecked("a")).isNotSameAs(first);
    assertThat(cache.stats().evictionCount()).isEqualTo(1);
  }

  @Test
  void simulatedReclamationOfWeakKeys() {
    List<RemovalNotification<?, ?>> removed = new ArrayList<>();
    Cache<Object, String> cache =
        CacheBuilder.newBuilder().weakKeys().removalListener(removed::add).build();
    Object key = new Object();
    Object other = new Object();
    cache.put(key, "v");
    cache.put(other, "w");
    local(cache).simulateKeyReclamation(key);
    assertThat(cache.size()).isEqualTo(1);
    assertThat(removed).hasSize(1);
    assertThat(removed.get(0).getKey()).isNull();
    assertThat(removed.get(0).getValue()).isEqualTo("v");
    assertThat(cache.getIfPresent(other)).isEqualTo("w");
  }

  @Test
  void weakKeysCompareByIdentity() {
    Cache<String, String> cache = CacheBuilder.newBuilder().weakKeys().build();
    String key = new String("k");
    cache.put(key, "v");
    assertThat(cache.getIfPresent(new String("k"))).isNull();
    assertThat(cache.getIfPresent(key)).isEqualTo("v");
    Cache<String, String> soft = CacheBuilder.newBuilder().softValues().build();
    String value = new String("v");
    soft.put("k", value);
    assertThat(soft.asMap().remove("k", new String("v"))).isFalse();
    assertThat(soft.asMap().remove("k", value)).isTrue();
  }

  @Test
  @Tag("gc")
  void weakValuesAreReallyCollected() {
    Cache<String, Object> cache = CacheBuilder.newBuilder().weakValues().build();
    cache.put("k", new Object());
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (cache.size() > 0 && System.nanoTime() < deadline) {
      System.gc();
      cache.cleanUp();
    }
    assertThat(cache.size()).isZero();
  }
}
