package com.simplelibs.lava.cache;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import com.simplelibs.lava.testing.ExceptionMessages;
import com.simplelibs.lava.testing.FakeTicker;
import java.lang.ref.Reference;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Regression tests for the Milestone 7 adversarial review (see {@code REVIEW.md}): consistency when
 * a compute function removes its own entry, when a stale refresh completes or when the weigher
 * fails; load timing; and behaviours the review found unpinned (notification order and timing,
 * identity comparison of reference values, failure messages, eviction and expiration of entries
 * being computed). Where Guava 27.0.1 behaves the same, the test compares the two.
 */
class CacheReviewTest {

  /** A FIFO executor drained explicitly: same-thread asynchronous reloading. */
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

  private static <K, V> RemovalListener<K, V> recorder(List<String> log) {
    return n -> log.add(n.getCause() + ":" + n.getKey() + "=" + n.getValue());
  }

  private static <K, V> com.google.common.cache.RemovalListener<K, V> guavaRecorder(
      List<String> log) {
    return n -> log.add(n.getCause() + ":" + n.getKey() + "=" + n.getValue());
  }

  private static com.simplelibs.lava.base.Ticker lavaTicker(long[] now) {
    return new com.simplelibs.lava.base.Ticker() {
      @Override
      public long read() {
        return now[0];
      }
    };
  }

  private static com.google.common.base.Ticker guavaTicker(long[] now) {
    return new com.google.common.base.Ticker() {
      @Override
      public long read() {
        return now[0];
      }
    };
  }

  /** The class and message of what {@code call} throws, or its result. */
  private static String outcome(Callable<Object> call) {
    try {
      return "= " + call.call();
    } catch (Exception | Error e) {
      return "! " + e.getClass().getSimpleName() + ": " + ExceptionMessages.of(e);
    }
  }

  // F1: a compute function that removes its own entry

  @Test
  void computeThatRemovesItsOwnEntryAndReturnsTheOldValueStoresItAgain() {
    List<String> removed = new ArrayList<>();
    Cache<String, String> cache =
        CacheBuilder.newBuilder().removalListener(CacheReviewTest.<String, String>recorder(removed)).build();
    cache.put("a", "A");
    cache.put("b", "B");
    assertThat(
            cache.asMap().compute(
                "a",
                (k, v) -> {
                  cache.invalidate("a");
                  return v;
                }))
        .isEqualTo("A");
    CacheTesting.checkInvariants(cache, true);
    assertThat(cache.size()).isEqualTo(2);
    assertThat(cache.asMap()).containsOnly(entry("a", "A"), entry("b", "B"));
    cache.invalidate("a");
    cache.invalidate("b");
    assertThat(cache.size()).isZero();
    assertThat(cache.asMap()).isEmpty();
    assertThat(removed).containsExactly("EXPLICIT:a=A", "EXPLICIT:a=A", "EXPLICIT:b=B");
  }

  @Test
  void computeThatRemovesItsOwnEntryAndThrowsLeavesItRemoved() {
    Cache<String, String> lava = CacheBuilder.newBuilder().maximumSize(2).build();
    com.google.common.cache.Cache<String, String> guava =
        com.google.common.cache.CacheBuilder.newBuilder().concurrencyLevel(1).maximumSize(2).build();
    lava.put("a", "A");
    lava.put("b", "B");
    guava.put("a", "A");
    guava.put("b", "B");
    assertThatThrownBy(
            () ->
                lava.asMap().compute(
                    "a",
                    (k, v) -> {
                      lava.invalidate("a");
                      throw new IllegalStateException("boom");
                    }))
        .hasMessage("boom");
    assertThatThrownBy(
            () ->
                guava.asMap().compute(
                    "a",
                    (k, v) -> {
                      guava.invalidate("a");
                      throw new IllegalStateException("boom");
                    }))
        .hasMessage("boom");
    CacheTesting.checkInvariants(lava, true);
    for (String key : Arrays.asList("c", "d", "e")) {
      lava.put(key, key);
      guava.put(key, key);
    }
    CacheTesting.checkInvariants(lava, true);
    assertThat(lava.asMap()).isEqualTo(guava.asMap()).containsOnlyKeys("d", "e");
    assertThat(lava.size()).isEqualTo(2);
  }

  @Test
  void weightsStayConsistentWhenAComputeRemovesItsEntryAndThrows() {
    Cache<String, Integer> lava =
        CacheBuilder.newBuilder().maximumWeight(10).weigher((String k, Integer v) -> v).build();
    com.google.common.cache.Cache<String, Integer> guava =
        com.google.common.cache.CacheBuilder.newBuilder()
            .concurrencyLevel(1)
            .maximumWeight(10)
            .weigher((String k, Integer v) -> v)
            .build();
    lava.put("a", 4);
    lava.put("b", 4);
    guava.put("a", 4);
    guava.put("b", 4);
    try {
      lava.asMap().compute(
          "a",
          (k, v) -> {
            lava.invalidate("a");
            throw new IllegalStateException();
          });
    } catch (IllegalStateException expected) {
    }
    try {
      guava.asMap().compute(
          "a",
          (k, v) -> {
            guava.invalidate("a");
            throw new IllegalStateException();
          });
    } catch (IllegalStateException expected) {
    }
    lava.put("c", 4);
    lava.put("d", 4);
    guava.put("c", 4);
    guava.put("d", 4);
    CacheTesting.checkInvariants(lava, true);
    assertThat(lava.asMap()).isEqualTo(guava.asMap()).containsOnlyKeys("c", "d");
  }

  @Test
  void anEntryRemovedByAThrowingComputeIsNotRestored() {
    FakeTicker ticker = new FakeTicker();
    Cache<String, String> cache =
        CacheBuilder.newBuilder().ticker(ticker).expireAfterWrite(1, NANOSECONDS).build();
    cache.put("a", "A");
    try {
      cache.asMap().compute(
          "a",
          (k, v) -> {
            cache.invalidate("a");
            throw new IllegalStateException();
          });
    } catch (IllegalStateException expected) {
    }
    assertThat(cache.getIfPresent("a")).isNull();
    assertThat(cache.size()).isZero();
    CacheTesting.checkInvariants(cache, true);
  }

  @Test
  void aValueWrittenByAComputeFunctionWinsOverItsResult() {
    // a non-null result is discarded, as in Guava
    List<String> lavaRemoved = new ArrayList<>();
    List<String> guavaRemoved = new ArrayList<>();
    Cache<String, String> lava =
        CacheBuilder.newBuilder().removalListener(CacheReviewTest.<String, String>recorder(lavaRemoved)).build();
    com.google.common.cache.Cache<String, String> guava =
        com.google.common.cache.CacheBuilder.newBuilder()
            .removalListener(CacheReviewTest.<String, String>guavaRecorder(guavaRemoved))
            .build();
    lava.put("k", "old");
    guava.put("k", "old");
    assertThat(
            lava.asMap().compute(
                "k",
                (k, v) -> {
                  lava.put("k", "written");
                  return "computed";
                }))
        .isEqualTo(
            guava.asMap().compute(
                "k",
                (k, v) -> {
                  guava.put("k", "written");
                  return "computed";
                }));
    assertThat(lava.asMap()).isEqualTo(guava.asMap()).containsExactly(entry("k", "written"));
    assertThat(lavaRemoved).isEqualTo(guavaRemoved).containsExactly("REPLACED:k=old", "REPLACED:k=computed");
    CacheTesting.checkInvariants(lava, true);

    // a null result removes the written value, as in Guava (Map.compute: null means absent)
    lavaRemoved.clear();
    guavaRemoved.clear();
    assertThat(
            lava.asMap().compute(
                "k",
                (k, v) -> {
                  lava.put("k", "again");
                  return null;
                }))
        .isNull();
    assertThat(
            guava.asMap().compute(
                "k",
                (k, v) -> {
                  guava.put("k", "again");
                  return null;
                }))
        .isNull();
    assertThat(lava.asMap()).isEqualTo(guava.asMap()).isEmpty();
    assertThat(lavaRemoved)
        .isEqualTo(guavaRemoved)
        .containsExactly("REPLACED:k=written", "EXPLICIT:k=again");
    CacheTesting.checkInvariants(lava, true);

    // so do computeIfPresent and merge
    lava.put("k", "old");
    lava.asMap().computeIfPresent(
        "k",
        (k, v) -> {
          lava.put("k", "written");
          return null;
        });
    assertThat(lava.asMap()).isEmpty();
    lava.put("k", "old");
    lava.asMap().merge(
        "k",
        "x",
        (a, b) -> {
          lava.put("k", "written");
          return null;
        });
    assertThat(lava.asMap()).isEmpty();
    CacheTesting.checkInvariants(lava, true);
  }

  @Test
  void aValueWrittenByAComputeFunctionThatReturnsTheOldValueIsKept() {
    // Lava: Guava 27.0.1 restores the old value and drops the written one without a notification
    List<String> removed = new ArrayList<>();
    Cache<String, String> cache =
        CacheBuilder.newBuilder().removalListener(CacheReviewTest.<String, String>recorder(removed)).build();
    cache.put("k", "old");
    assertThat(
            cache.asMap().compute(
                "k",
                (k, v) -> {
                  cache.put("k", "written");
                  return v;
                }))
        .isEqualTo("old");
    assertThat(cache.asMap()).containsExactly(entry("k", "written"));
    assertThat(removed).containsExactly("REPLACED:k=old", "REPLACED:k=old");
    CacheTesting.checkInvariants(cache, true);

    com.google.common.cache.Cache<String, String> guava =
        com.google.common.cache.CacheBuilder.newBuilder().build();
    guava.put("k", "old");
    guava.asMap().compute(
        "k",
        (k, v) -> {
          guava.put("k", "written");
          return v;
        });
    assertThat(guava.asMap()).containsExactly(entry("k", "old")); // the documented difference
  }

  // F2: a stale refresh completing over an entry that holds a newer, cleared load

  @Test
  void staleRefreshCompletingAfterAnInvalidationKeepsCountsConsistent() {
    QueueExecutor executor = new QueueExecutor();
    int[] loads = {100};
    List<String> removed = new ArrayList<>();
    LoadingCache<String, Integer> cache =
        CacheBuilder.newBuilder()
            .recordStats()
            .maximumWeight(1000)
            .weigher((String k, Integer v) -> v % 10)
            .removalListener(CacheReviewTest.<String, Integer>recorder(removed))
            .build(CacheLoader.asyncReloading(CacheLoader.from((String k) -> ++loads[0]), executor));
    cache.put("k", 5);
    cache.refresh("k"); // reload A is queued
    cache.put("k", 2); // A is now stale
    cache.refresh("k"); // reload B is queued
    cache.invalidate("k"); // the value goes while B is pending
    CacheTesting.checkInvariants(cache, false);
    executor.runAll(); // A stores 101 in the empty entry; B's 102 is then discarded
    CacheTesting.checkInvariants(cache, true);
    assertThat(cache.size()).isEqualTo(1);
    assertThat(cache.asMap()).containsExactly(entry("k", 101));
    assertThat(removed).containsExactly("REPLACED:k=5", "EXPLICIT:k=2", "REPLACED:k=102");
    assertThat(cache.stats().evictionCount()).isZero();
    cache.invalidateAll();
    assertThat(cache.asMap()).isEmpty();
    CacheTesting.checkInvariants(cache, true);
  }

  @Test
  void refreshOverACollectedValueReportsItCollected() {
    QueueExecutor executor = new QueueExecutor();
    List<String> removed = new ArrayList<>();
    LoadingCache<String, String> cache =
        CacheBuilder.newBuilder()
            .weakValues()
            .removalListener(CacheReviewTest.<String, String>recorder(removed))
            .build(CacheLoader.asyncReloading(CacheLoader.from((String k) -> "fresh"), executor));
    cache.put("k", new String("old"));
    cache.refresh("k");
    CacheTesting.reclaimValue(CacheTesting.toLocalCache(cache), "k", false);
    executor.runAll();
    assertThat(removed).containsExactly("COLLECTED:k=null");
    assertThat(cache.asMap()).containsExactly(entry("k", "fresh"));
    CacheTesting.checkInvariants(cache, true);
  }

  // F4: load time

  private static final long LISTENER_MILLIS = 300;

  @Test
  void listenerTimeIsNotCountedAsLoadTime() {
    FakeTicker ticker = new FakeTicker();
    LoadingCache<String, String> cache =
        CacheBuilder.newBuilder()
            .recordStats()
            .ticker(ticker)
            .expireAfterWrite(1, NANOSECONDS)
            .removalListener(
                n -> {
                  try {
                    Thread.sleep(LISTENER_MILLIS);
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                })
            .build(CacheLoader.from((String k) -> k));
    cache.put("a", "a");
    ticker.advance(1);
    cache.getUnchecked("b"); // a expires, and its listener runs before the load
    ticker.advance(1);
    cache.asMap().compute("c", (k, v) -> k); // b expires, and its listener runs before the function
    assertThat(cache.stats().loadCount()).isEqualTo(2);
    // Counting the listeners would make it at least 2 * LISTENER_MILLIS; the loads take microseconds.
    assertThat(cache.stats().totalLoadTime()).isLessThan(MILLISECONDS.toNanos(LISTENER_MILLIS));
  }

  // F5: the weigher fails for a loaded value

  @Test
  void weigherFailureForALoadedValueLeavesNoLoadBehind() {
    LoadingCache<String, String> cache =
        CacheBuilder.newBuilder()
            .maximumWeight(10)
            .weigher(
                (String k, String v) -> {
                  if (v.equals("bad")) {
                    throw new IllegalStateException("weigher");
                  }
                  return 1;
                })
            .build(CacheLoader.from((String k) -> k));
    assertThatThrownBy(() -> cache.getUnchecked("bad"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("weigher");
    CacheTesting.checkInvariants(cache, true);
    assertThat(cache.asMap()).isEmpty();
    cache.put("k", "v");
    assertThatThrownBy(() -> cache.asMap().compute("k", (k, v) -> "bad"))
        .isInstanceOf(IllegalStateException.class);
    CacheTesting.checkInvariants(cache, true);
    assertThat(cache.asMap()).containsExactly(entry("k", "v"));
    assertThat(cache.getUnchecked("good")).isEqualTo("good");
  }

  @Test
  void negativeWeightsAreRejectedAsInGuava() {
    Cache<String, Integer> lava =
        CacheBuilder.newBuilder().maximumWeight(10).weigher((String k, Integer v) -> v).build();
    com.google.common.cache.Cache<String, Integer> guava =
        com.google.common.cache.CacheBuilder.newBuilder()
            .maximumWeight(10)
            .weigher((String k, Integer v) -> v)
            .build();
    assertThat(outcome(() -> lava.asMap().put("k", -1)))
        .isEqualTo(outcome(() -> guava.asMap().put("k", -1)))
        .isEqualTo("! IllegalStateException: Weights must be non-negative");
    CacheTesting.checkInvariants(lava, true);
  }

  // Notification order and timing

  @Test
  void notificationsOfOneOperationAreDeliveredInOrder() {
    List<String> lavaRemoved = new ArrayList<>();
    List<String> guavaRemoved = new ArrayList<>();
    Cache<String, Integer> lava =
        CacheBuilder.newBuilder()
            .maximumWeight(2)
            .weigher((String k, Integer v) -> v)
            .removalListener(CacheReviewTest.<String, Integer>recorder(lavaRemoved))
            .build();
    com.google.common.cache.Cache<String, Integer> guava =
        com.google.common.cache.CacheBuilder.newBuilder()
            .concurrencyLevel(1)
            .maximumWeight(2)
            .weigher((String k, Integer v) -> v)
            .removalListener(CacheReviewTest.<String, Integer>guavaRecorder(guavaRemoved))
            .build();
    lava.put("a", 1);
    lava.put("b", 1);
    lava.put("b", 2); // replaces b, then evicts a
    guava.put("a", 1);
    guava.put("b", 1);
    guava.put("b", 2);
    assertThat(lavaRemoved).isEqualTo(guavaRemoved).containsExactly("REPLACED:b=1", "SIZE:a=1");
  }

  @Test
  void notificationsAreDeliveredBeforeAComputeFunctionRuns() {
    long[] now = {0};
    List<String> lavaLog = new ArrayList<>();
    List<String> guavaLog = new ArrayList<>();
    Cache<String, String> lava =
        CacheBuilder.newBuilder()
            .ticker(lavaTicker(now))
            .expireAfterWrite(1, NANOSECONDS)
            .removalListener((RemovalNotification<String, String> n) -> lavaLog.add("removed " + n.getKey()))
            .build();
    com.google.common.cache.Cache<String, String> guava =
        com.google.common.cache.CacheBuilder.newBuilder()
            .concurrencyLevel(1) // one segment, so that its cleanup reaches a
            .ticker(guavaTicker(now))
            .expireAfterWrite(1, NANOSECONDS)
            .removalListener(
                (com.google.common.cache.RemovalNotification<String, String> n) ->
                    guavaLog.add("removed " + n.getKey()))
            .build();
    lava.put("a", "a");
    guava.put("a", "a");
    now[0]++;
    lava.asMap().compute(
        "b",
        (k, v) -> {
          lavaLog.add("compute " + k);
          return k;
        });
    guava.asMap().compute(
        "b",
        (k, v) -> {
          guavaLog.add("compute " + k);
          return k;
        });
    assertThat(lavaLog).containsExactly("removed a", "compute b");
    assertThat(guavaLog).containsExactly("compute b", "removed a"); // the documented difference
  }

  @Test
  void aRefreshCompletedLaterDeliversItsNotificationsAtOnce() {
    QueueExecutor lavaExecutor = new QueueExecutor();
    QueueExecutor guavaExecutor = new QueueExecutor();
    List<String> lavaRemoved = new ArrayList<>();
    List<String> guavaRemoved = new ArrayList<>();
    LoadingCache<String, String> lava =
        CacheBuilder.newBuilder()
            .removalListener(CacheReviewTest.<String, String>recorder(lavaRemoved))
            .build(CacheLoader.asyncReloading(CacheLoader.from((String k) -> k + "'"), lavaExecutor));
    com.google.common.cache.LoadingCache<String, String> guava =
        com.google.common.cache.CacheBuilder.newBuilder()
            .removalListener(CacheReviewTest.<String, String>guavaRecorder(guavaRemoved))
            .build(
                com.google.common.cache.CacheLoader.asyncReloading(
                    com.google.common.cache.CacheLoader.from((String k) -> k + "'"), guavaExecutor));
    lava.put("k", "v");
    guava.put("k", "v");
    lava.refresh("k");
    guava.refresh("k");
    assertThat(lavaRemoved).isEmpty();
    lavaExecutor.runAll();
    guavaExecutor.runAll();
    assertThat(lavaRemoved).isEqualTo(guavaRemoved).containsExactly("REPLACED:k=v");
  }

  @Test
  void aGetFromAListenerOfTheKeyAboutToLoadFailsFast() {
    // Guava would wait forever for the load that its own caller is about to run
    FakeTicker ticker = new FakeTicker();
    List<String> seen = new ArrayList<>();
    AtomicReference<LoadingCache<String, String>> holder = new AtomicReference<>();
    holder.set(
        CacheBuilder.newBuilder()
            .ticker(ticker)
            .expireAfterWrite(1, NANOSECONDS)
            .removalListener(
                n -> {
                  try {
                    holder.get().getUnchecked("b");
                  } catch (IllegalStateException e) {
                    seen.add(e.getMessage());
                  }
                })
            .build(CacheLoader.from((String k) -> k)));
    holder.get().put("a", "a");
    ticker.advance(1);
    assertThat(holder.get().getUnchecked("b")).isEqualTo("b");
    assertThat(seen).containsExactly("Recursive load of: b");
    CacheTesting.checkInvariants(holder.get(), true);
  }

  // Reference queues (F9)

  @Test
  void aStaleValueReferenceReachingTheQueueRemovesNothing() {
    List<String> removed = new ArrayList<>();
    Cache<String, String> cache =
        CacheBuilder.newBuilder()
            .weakValues()
            .removalListener(CacheReviewTest.<String, String>recorder(removed))
            .build();
    LocalCache<String, String> map = CacheTesting.toLocalCache(cache);
    String v1 = new String("v1");
    String v2 = new String("v2");
    String v3 = new String("v3");
    cache.put("k", v1);
    Reference<?> first = (Reference<?>) map.table.values().iterator().next().valueRef;
    cache.put("k", v2); // the same, still live entry gets a new reference
    first.enqueue();
    cache.cleanUp();
    assertThat(cache.getIfPresent("k")).isSameAs(v2);
    Reference<?> second = (Reference<?>) map.table.values().iterator().next().valueRef;
    cache.invalidate("k");
    cache.put("k", v3); // a new entry for an equal key
    second.enqueue(); // the reference of the removed entry, which still holds it
    cache.cleanUp();
    assertThat(cache.getIfPresent("k")).isSameAs(v3);
    assertThat(removed).containsExactly("REPLACED:k=v1", "EXPLICIT:k=v2");
    CacheTesting.checkInvariants(cache, true);
    Reference.reachabilityFence(v1);
    Reference.reachabilityFence(v2);
    Reference.reachabilityFence(v3);
  }

  @Test
  void reloadingACollectedValueKeepsTheEntryOutOfTheExpirationOrder() {
    FakeTicker ticker = new FakeTicker();
    List<String> removed = new ArrayList<>();
    String fresh = "fresh";
    AtomicReference<LoadingCache<String, String>> holder = new AtomicReference<>();
    holder.set(
        CacheBuilder.newBuilder()
            .ticker(ticker)
            .weakValues()
            .expireAfterWrite(10, NANOSECONDS)
            .removalListener(CacheReviewTest.<String, String>recorder(removed))
            .build(
                CacheLoader.from(
                    (String k) -> {
                      ticker.advance(20);
                      holder.get().cleanUp(); // would expire the entry if it were still listed
                      return fresh;
                    })));
    holder.get().put("k", new String("old"));
    CacheTesting.reclaimValue(CacheTesting.toLocalCache(holder.get()), "k", false);
    assertThat(holder.get().getUnchecked("k")).isSameAs(fresh);
    assertThat(removed).containsExactly("COLLECTED:k=null");
    assertThat(holder.get().size()).isEqualTo(1);
    CacheTesting.checkInvariants(holder.get(), true);
  }

  // Maintenance around loads and computes

  @Test
  void entriesThatExpireDuringALoadExpireBeforeAnythingIsEvicted() {
    long[] now = {0};
    List<String> lavaRemoved = new ArrayList<>();
    List<String> guavaRemoved = new ArrayList<>();
    LoadingCache<String, String> lava =
        CacheBuilder.newBuilder()
            .ticker(lavaTicker(now))
            .maximumSize(1)
            .expireAfterWrite(10, NANOSECONDS)
            .removalListener(CacheReviewTest.<String, String>recorder(lavaRemoved))
            .build(
                CacheLoader.from(
                    (String k) -> {
                      now[0] += 20;
                      return k;
                    }));
    com.google.common.cache.LoadingCache<String, String> guava =
        com.google.common.cache.CacheBuilder.newBuilder()
            .ticker(guavaTicker(now))
            .concurrencyLevel(1)
            .maximumSize(1)
            .expireAfterWrite(10, NANOSECONDS)
            .removalListener(CacheReviewTest.<String, String>guavaRecorder(guavaRemoved))
            .build(
                com.google.common.cache.CacheLoader.from(
                    (String k) -> {
                      now[0] += 20;
                      return k;
                    }));
    lava.put("b", "b");
    lava.getUnchecked("a");
    now[0] = 0;
    guava.put("b", "b");
    guava.getUnchecked("a");
    assertThat(lavaRemoved).isEqualTo(guavaRemoved).containsExactly("EXPIRED:b=b");
  }

  @Test
  void aLoadWhoseEntryWasClearedStillEvicts() {
    List<String> lavaRemoved = new ArrayList<>();
    List<String> guavaRemoved = new ArrayList<>();
    AtomicReference<LoadingCache<String, String>> lava = new AtomicReference<>();
    AtomicReference<com.google.common.cache.LoadingCache<String, String>> guava =
        new AtomicReference<>();
    lava.set(
        CacheBuilder.newBuilder()
            .maximumSize(2)
            .removalListener(CacheReviewTest.<String, String>recorder(lavaRemoved))
            .build(
                CacheLoader.from(
                    (String k) -> {
                      lava.get().invalidateAll();
                      lava.get().put("b", "b");
                      lava.get().put("c", "c");
                      return k;
                    })));
    guava.set(
        com.google.common.cache.CacheBuilder.newBuilder()
            .concurrencyLevel(1)
            .maximumSize(2)
            .removalListener(CacheReviewTest.<String, String>guavaRecorder(guavaRemoved))
            .build(
                com.google.common.cache.CacheLoader.from(
                    (String k) -> {
                      guava.get().invalidateAll();
                      guava.get().put("b", "b");
                      guava.get().put("c", "c");
                      return k;
                    })));
    lava.get().put("x", "x");
    guava.get().put("x", "x");
    assertThat(lava.get().getUnchecked("a")).isEqualTo(guava.get().getUnchecked("a"));
    assertThat(lava.get().asMap()).isEqualTo(guava.get().asMap()).containsOnlyKeys("a", "c");
    assertThat(lavaRemoved).isEqualTo(guavaRemoved).containsExactly("EXPLICIT:x=x", "SIZE:b=b");
    CacheTesting.checkInvariants(lava.get(), true);
  }

  @Test
  void anEntryBeingComputedIsNotEvicted() {
    List<String> lavaRemoved = new ArrayList<>();
    List<String> guavaRemoved = new ArrayList<>();
    Cache<String, String> lava =
        CacheBuilder.newBuilder()
            .maximumSize(1)
            .removalListener(CacheReviewTest.<String, String>recorder(lavaRemoved))
            .build();
    com.google.common.cache.Cache<String, String> guava =
        com.google.common.cache.CacheBuilder.newBuilder()
            .concurrencyLevel(1)
            .maximumSize(1)
            .removalListener(CacheReviewTest.<String, String>guavaRecorder(guavaRemoved))
            .build();
    lava.put("a", "a");
    guava.put("a", "a");
    lava.asMap().compute(
        "a",
        (k, v) -> {
          lava.put("b", "b");
          return v + "!";
        });
    guava.asMap().compute(
        "a",
        (k, v) -> {
          guava.put("b", "b");
          return v + "!";
        });
    assertThat(lava.asMap()).isEqualTo(guava.asMap()).containsExactly(entry("a", "a!"));
    assertThat(lavaRemoved).isEqualTo(guavaRemoved).containsExactly("SIZE:b=b", "REPLACED:a=a");
    CacheTesting.checkInvariants(lava, true);
  }

  @Test
  void anEntryBeingComputedDoesNotExpire() {
    long[] now = {0};
    List<String> lavaRemoved = new ArrayList<>();
    List<String> guavaRemoved = new ArrayList<>();
    Cache<String, String> lava =
        CacheBuilder.newBuilder()
            .ticker(lavaTicker(now))
            .expireAfterWrite(5, NANOSECONDS)
            .removalListener(CacheReviewTest.<String, String>recorder(lavaRemoved))
            .build();
    com.google.common.cache.Cache<String, String> guava =
        com.google.common.cache.CacheBuilder.newBuilder()
            .ticker(guavaTicker(now))
            .expireAfterWrite(5, NANOSECONDS)
            .removalListener(CacheReviewTest.<String, String>guavaRecorder(guavaRemoved))
            .build();
    lava.put("a", "a");
    guava.put("a", "a");
    lava.asMap().compute(
        "a",
        (k, v) -> {
          now[0] += 10;
          lava.cleanUp();
          return v + "!";
        });
    now[0] = 0;
    guava.asMap().compute(
        "a",
        (k, v) -> {
          now[0] += 10;
          guava.cleanUp();
          return v + "!";
        });
    assertThat(lava.asMap()).isEqualTo(guava.asMap()).containsExactly(entry("a", "a!"));
    assertThat(lavaRemoved).isEqualTo(guavaRemoved).containsExactly("REPLACED:a=a");
    CacheTesting.checkInvariants(lava, true);
  }

  @Test
  void oneWriteCanEvictManyEntriesInRecencyOrder() {
    // Guava's testEvictEntries evicts its whole segment in one call
    List<String> lavaRemoved = new ArrayList<>();
    List<String> guavaRemoved = new ArrayList<>();
    Cache<Integer, Integer> lava =
        CacheBuilder.newBuilder()
            .maximumWeight(1000)
            .weigher((Integer k, Integer v) -> v)
            .removalListener(CacheReviewTest.<Integer, Integer>recorder(lavaRemoved))
            .build();
    com.google.common.cache.Cache<Integer, Integer> guava =
        com.google.common.cache.CacheBuilder.newBuilder()
            .concurrencyLevel(1)
            .maximumWeight(1000)
            .weigher((Integer k, Integer v) -> v)
            .removalListener(CacheReviewTest.<Integer, Integer>guavaRecorder(guavaRemoved))
            .build();
    for (int i = 0; i < 1000; i++) {
      lava.put(i, 1);
      guava.put(i, 1);
    }
    for (int i = 0; i < 1000; i += 7) {
      lava.getIfPresent(i);
      guava.getIfPresent(i);
    }
    lava.put(1000, 1000);
    guava.put(1000, 1000);
    assertThat(lavaRemoved).hasSize(1000).isEqualTo(guavaRemoved);
    assertThat(lavaRemoved.get(0)).isEqualTo("SIZE:1=1");
    assertThat(lava.asMap()).containsExactly(entry(1000, 1000));
    CacheTesting.checkInvariants(lava, true);
  }

  @Test
  void readsInsideAThrowingComputeLeaveItsEntryWhereItWas() {
    Cache<String, String> cache = CacheBuilder.newBuilder().maximumSize(2).build();
    cache.put("a", "a");
    cache.put("b", "b");
    try {
      cache.asMap().compute(
          "a",
          (k, v) -> {
            assertThat(cache.getIfPresent("a")).isEqualTo("a");
            throw new IllegalStateException();
          });
    } catch (IllegalStateException expected) {
    }
    cache.put("c", "c"); // a is still the least recently used
    assertThat(cache.asMap()).containsOnlyKeys("b", "c");
    CacheTesting.checkInvariants(cache, true);
  }

  // Views

  @Test
  void iteratorRemoveRemovesTheKeyAfterAnUpdate() {
    List<String> lavaRemoved = new ArrayList<>();
    List<String> guavaRemoved = new ArrayList<>();
    Cache<String, String> lava =
        CacheBuilder.newBuilder().removalListener(CacheReviewTest.<String, String>recorder(lavaRemoved)).build();
    com.google.common.cache.Cache<String, String> guava =
        com.google.common.cache.CacheBuilder.newBuilder()
            .removalListener(CacheReviewTest.<String, String>guavaRecorder(guavaRemoved))
            .build();
    lava.put("k", "v1");
    guava.put("k", "v1");
    Iterator<String> lavaKeys = lava.asMap().keySet().iterator();
    Iterator<String> guavaKeys = guava.asMap().keySet().iterator();
    lavaKeys.next();
    guavaKeys.next();
    lava.put("k", "v2");
    guava.put("k", "v2");
    lavaKeys.remove();
    guavaKeys.remove();
    assertThat(lava.asMap()).isEmpty();
    assertThat(guava.asMap()).isEmpty();
    assertThat(lavaRemoved).isEqualTo(guavaRemoved).containsExactly("REPLACED:k=v1", "EXPLICIT:k=v2");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void referenceValuesAreComparedByIdentity(boolean weak) {
    String value = new String("v");
    String equalValue = new String("v");
    CacheBuilder<Object, Object> lavaBuilder = CacheBuilder.newBuilder();
    com.google.common.cache.CacheBuilder<Object, Object> guavaBuilder =
        com.google.common.cache.CacheBuilder.newBuilder();
    Cache<String, String> lava = (weak ? lavaBuilder.weakValues() : lavaBuilder.softValues()).build();
    com.google.common.cache.Cache<String, String> guava =
        (weak ? guavaBuilder.weakValues() : guavaBuilder.softValues()).build();
    List<String> lavaResults = identityProbe(lava.asMap(), value, equalValue);
    List<String> guavaResults = identityProbe(guava.asMap(), value, equalValue);
    assertThat(lavaResults)
        .isEqualTo(guavaResults)
        .containsExactly(
            "true", "false", "true", "false", "false", "false", "true", "true", "true", "false");
    Reference.reachabilityFence(value);
    Reference.reachabilityFence(equalValue);
  }

  private static List<String> identityProbe(Map<String, String> map, String value, String equal) {
    List<String> results = new ArrayList<>();
    map.put("k", value);
    results.add(String.valueOf(map.containsValue(value)));
    results.add(String.valueOf(map.containsValue(equal)));
    results.add(String.valueOf(map.entrySet().contains(new SimpleImmutableEntry<>("k", value))));
    results.add(String.valueOf(map.entrySet().contains(new SimpleImmutableEntry<>("k", equal))));
    results.add(String.valueOf(map.remove("k", equal)));
    results.add(String.valueOf(map.replace("k", equal, "w")));
    results.add(String.valueOf(map.replace("k", value, value)));
    results.add(String.valueOf(map.values().contains(value)));
    results.add(String.valueOf(map.values().remove(equal)));
    results.add(String.valueOf(map.remove("k", value)));
    return results;
  }

  @Test
  void viewRemoveIfRejectsNullEvenWhenEmpty() {
    Map<String, String> lava = CacheBuilder.newBuilder().<String, String>build().asMap();
    Map<String, String> guava =
        com.google.common.cache.CacheBuilder.newBuilder().<String, String>build().asMap();
    for (Map<String, String> map : Arrays.asList(lava, guava)) {
      assertThatNullPointerException().isThrownBy(() -> map.keySet().removeIf(null));
      assertThatNullPointerException().isThrownBy(() -> map.values().removeIf(null));
      assertThatNullPointerException().isThrownBy(() -> map.entrySet().removeIf(null));
    }
  }

  // Messages and validation

  private static final class BulkLoader extends CacheLoader<String, String> {
    final int mode;

    BulkLoader(int mode) {
      this.mode = mode;
    }

    @Override
    public String load(String key) {
      return key;
    }

    @Override
    public Map<String, String> loadAll(Iterable<? extends String> keys) {
      return bulk(mode, keys);
    }

    @Override
    public String toString() {
      return "BulkLoader";
    }
  }

  private static final class GuavaBulkLoader
      extends com.google.common.cache.CacheLoader<String, String> {
    final int mode;

    GuavaBulkLoader(int mode) {
      this.mode = mode;
    }

    @Override
    public String load(String key) {
      return key;
    }

    @Override
    public Map<String, String> loadAll(Iterable<? extends String> keys) {
      return bulk(mode, keys);
    }

    @Override
    public String toString() {
      return "BulkLoader";
    }
  }

  private static Map<String, String> bulk(int mode, Iterable<? extends String> keys) {
    if (mode == 0) {
      return null;
    }
    Map<String, String> result = new HashMap<>();
    for (String key : keys) {
      result.put(key, mode == 2 ? null : key);
    }
    if (mode == 1) {
      result.remove("b");
    }
    return result;
  }

  @Test
  void loadAllFailureMessagesMatchGuava() {
    for (int mode = 0; mode < 3; mode++) {
      LoadingCache<String, String> lava = CacheBuilder.newBuilder().build(new BulkLoader(mode));
      com.google.common.cache.LoadingCache<String, String> guava =
          com.google.common.cache.CacheBuilder.newBuilder().build(new GuavaBulkLoader(mode));
      List<String> keys = Arrays.asList("a", "b");
      assertThat(outcome(() -> lava.getUnchecked("x") + lava.getAll(keys)))
          .isEqualTo(outcome(() -> guava.getUnchecked("x") + guava.getAll(keys)));
    }
    assertThat(outcome(() -> CacheBuilder.newBuilder().build(new BulkLoader(1)).getAll(Arrays.asList("a", "b"))))
        .isEqualTo("! InvalidCacheLoadException: loadAll failed to return a value for b");
    assertThat(outcome(() -> CacheBuilder.newBuilder().build(new BulkLoader(0)).getAll(Arrays.asList("a", "b"))))
        .isEqualTo("! InvalidCacheLoadException: BulkLoader returned null map from loadAll");
  }

  @Test
  void buildValidationMatchesGuava() {
    CacheLoader<Object, Object> loader = CacheLoader.from(k -> k);
    com.google.common.cache.CacheLoader<Object, Object> guavaLoader =
        com.google.common.cache.CacheLoader.from(k -> k);
    Weigher<Object, Object> weigher = (k, v) -> 1;
    com.google.common.cache.Weigher<Object, Object> guavaWeigher = (k, v) -> 1;
    List<Callable<Object>> lava =
        Arrays.asList(
            () -> CacheBuilder.newBuilder().refreshAfterWrite(1, SECONDS).build(),
            () -> CacheBuilder.newBuilder().refreshAfterWrite(1, SECONDS).build(loader),
            () -> CacheBuilder.newBuilder().maximumWeight(1).build(),
            () -> CacheBuilder.newBuilder().maximumWeight(1).build(loader),
            () -> CacheBuilder.newBuilder().weigher(weigher).build(),
            () -> CacheBuilder.newBuilder().weigher(weigher).build(loader),
            () -> CacheBuilder.newBuilder().weigher(weigher).weigher(weigher),
            () -> CacheBuilder.newBuilder().maximumSize(1).weigher(weigher),
            () -> CacheBuilder.newBuilder().weigher(weigher).maximumSize(1));
    List<Callable<Object>> guava =
        Arrays.asList(
            () -> com.google.common.cache.CacheBuilder.newBuilder().refreshAfterWrite(1, SECONDS).build(),
            () -> com.google.common.cache.CacheBuilder.newBuilder().refreshAfterWrite(1, SECONDS).build(guavaLoader),
            () -> com.google.common.cache.CacheBuilder.newBuilder().maximumWeight(1).build(),
            () -> com.google.common.cache.CacheBuilder.newBuilder().maximumWeight(1).build(guavaLoader),
            () -> com.google.common.cache.CacheBuilder.newBuilder().weigher(guavaWeigher).build(),
            () -> com.google.common.cache.CacheBuilder.newBuilder().weigher(guavaWeigher).build(guavaLoader),
            () -> com.google.common.cache.CacheBuilder.newBuilder().weigher(guavaWeigher).weigher(guavaWeigher),
            () -> com.google.common.cache.CacheBuilder.newBuilder().maximumSize(1).weigher(guavaWeigher),
            () -> com.google.common.cache.CacheBuilder.newBuilder().weigher(guavaWeigher).maximumSize(1));
    for (int i = 0; i < lava.size(); i++) {
      String lavaOutcome = outcome(lava.get(i)).replaceAll("^= .*", "= built");
      String guavaOutcome = outcome(guava.get(i)).replaceAll("^= .*", "= built");
      assertThat(lavaOutcome).as("case " + i).isEqualTo(guavaOutcome);
    }
    assertThat(outcome(lava.get(0))).isEqualTo("! IllegalStateException: refreshAfterWrite requires a LoadingCache");
    assertThat(outcome(lava.get(2))).isEqualTo("! IllegalStateException: maximumWeight requires weigher");
    assertThat(outcome(lava.get(4))).isEqualTo("! IllegalStateException: weigher requires maximumWeight");
  }

  @Test
  void loaderFactoriesRejectNulls() {
    CacheLoader<String, String> fromFunction = CacheLoader.from((String k) -> k);
    CacheLoader<Object, String> fromSupplier = CacheLoader.from(() -> "v");
    CacheLoader<String, String> lenient =
        new CacheLoader<String, String>() {
          @Override
          public String load(String key) {
            return "v"; // accepts null, so that reload's own checks are what throws
          }
        };
    assertThatNullPointerException().isThrownBy(() -> lenient.reload(null, "v"));
    assertThatNullPointerException().isThrownBy(() -> lenient.reload("k", null));
    assertThatNullPointerException().isThrownBy(() -> fromFunction.load(null));
    assertThatNullPointerException().isThrownBy(() -> fromSupplier.load(null));
    assertThatNullPointerException().isThrownBy(() -> CacheLoader.asyncReloading(fromFunction, null));
    assertThatNullPointerException().isThrownBy(() -> CacheLoader.asyncReloading(null, Runnable::run));
  }
}
