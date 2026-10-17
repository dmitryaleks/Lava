package com.simplelibs.lava.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.simplelibs.lava.base.Enums;
import com.simplelibs.lava.base.Optional;
import com.simplelibs.lava.base.Ticker;
import com.simplelibs.lava.cache.CacheBuilder;
import com.simplelibs.lava.cache.CacheLoader;
import com.simplelibs.lava.cache.CacheStats;
import com.simplelibs.lava.cache.LoadingCache;
import com.simplelibs.lava.cache.RemovalCause;
import com.simplelibs.lava.cache.RemovalNotification;
import com.simplelibs.lava.checker.nullness.qual.NonNull;
import com.simplelibs.lava.collect.ImmutableList;
import com.simplelibs.lava.collect.ImmutableMap;
import com.simplelibs.lava.collect.Lists;
import com.simplelibs.lava.collect.Maps;
import com.simplelibs.lava.eventbus.DeadEvent;
import com.simplelibs.lava.eventbus.EventBus;
import com.simplelibs.lava.eventbus.Subscribe;
import com.simplelibs.lava.primitives.Doubles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Typical Guava client code, migrated by changing imports only: a loading cache with expiry,
 * removal notifications and statistics; an event bus with a dead-event handler; immutable maps;
 * map and list views; double parsing; enum lookup. It lives outside Lava's packages, so it can use
 * only the public API.
 */
class MigrationSmokeTest {

  enum Color {
    RED,
    GREEN
  }

  /** A manually advanced clock, as clients use in tests. */
  static final class ManualTicker extends Ticker {
    long nanos;

    @Override
    public long read() {
      return nanos;
    }
  }

  @Test
  void loadingCacheWithExpiryAndStats() throws Exception {
    ManualTicker ticker = new ManualTicker();
    List<RemovalNotification<String, Integer>> removed = new ArrayList<>();
    LoadingCache<String, Integer> lengths =
        CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .ticker(ticker)
            .recordStats()
            .removalListener((RemovalNotification<String, Integer> n) -> removed.add(n))
            .build(
                new CacheLoader<String, Integer>() {
                  @Override
                  public Integer load(String key) {
                    return key.length();
                  }
                });
    assertThat(lengths.get("lava")).isEqualTo(4);
    assertThat(lengths.getUnchecked("lava")).isEqualTo(4);
    assertThat(lengths.getAll(ImmutableList.of("a", "bb"))).isEqualTo(ImmutableMap.of("a", 1, "bb", 2));
    ticker.nanos += TimeUnit.MINUTES.toNanos(11);
    assertThat(lengths.getIfPresent("lava")).isNull();
    lengths.cleanUp();
    assertThat(removed).extracting(RemovalNotification::getCause).containsOnly(RemovalCause.EXPIRED);
    CacheStats stats = lengths.stats();
    assertThat(stats.hitCount()).isEqualTo(1);
    assertThat(stats.missCount()).isEqualTo(4);
    assertThat(stats.loadSuccessCount()).isEqualTo(3);
    assertThat(CacheBuilder.from("maximumSize=10,expireAfterAccess=5m").build()).isNotNull();
  }

  public static class Listener {
    final List<String> received = new ArrayList<>();
    final List<Object> dead = new ArrayList<>();

    @Subscribe
    public void onString(String event) {
      received.add(event);
    }

    @Subscribe
    public void onDeadEvent(DeadEvent event) {
      dead.add(event.getEvent());
    }
  }

  @Test
  void eventBusWithDeadEvents() {
    EventBus bus = new EventBus("app");
    Listener listener = new Listener();
    bus.register(listener);
    bus.post("hello");
    bus.post(42);
    assertThat(listener.received).containsExactly("hello");
    assertThat(listener.dead).containsExactly(42);
    bus.unregister(listener);
    bus.post("ignored");
    assertThat(listener.received).containsExactly("hello");
  }

  @Test
  void collectionsAndPrimitives() {
    ImmutableMap<String, Integer> ages =
        ImmutableMap.<String, Integer>builder().put("ann", 31).put("bob", 25).put("cy", 40).build();
    Map<String, Integer> adults = Maps.filterValues(ages, age -> age >= 30);
    assertThat(adults).containsOnlyKeys("ann", "cy");
    assertThat(Maps.filterKeys(ages, name -> name.length() == 3)).containsOnlyKeys("ann", "bob");
    assertThat(Maps.uniqueIndex(ImmutableList.of("x", "yy"), String::length))
        .isEqualTo(ImmutableMap.of(1, "x", 2, "yy"));
    assertThat(Lists.partition(Lists.newArrayList(1, 2, 3, 4, 5), 2))
        .containsExactly(List.of(1, 2), List.of(3, 4), List.of(5));
    assertThat(Lists.reverse(ImmutableList.of(1, 2, 3))).containsExactly(3, 2, 1);
    assertThat(Doubles.tryParse("3.5")).isEqualTo(3.5);
    assertThat(Doubles.tryParse("x")).isNull();
    assertThat(Doubles.max(1.0, 7.5, -2.0)).isEqualTo(7.5);
    Optional<Color> color = Enums.getIfPresent(Color.class, "GREEN");
    assertThat(color.get()).isEqualTo(Color.GREEN);
    assertThat(Enums.getIfPresent(Color.class, "BLUE").isPresent()).isFalse();
    @NonNull String annotated = "non-null";
    assertThat(annotated).isNotEmpty();
  }
}
