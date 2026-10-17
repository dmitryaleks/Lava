package com.simplelibs.lava.collect;

import static org.assertj.core.api.Assertions.assertThat;

import com.simplelibs.lava.testing.ExceptionMessages;
import com.simplelibs.lava.testing.SerializableTester;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Maps#synchronizedBiMap}, replacing Guava's {@code SynchronizedBiMapTest} (whose
 * {@code BiMapTestSuiteBuilder} suites are not ported): identity of the wrapper, its inverse and its
 * views, {@code forcePut}, and every {@code Map} default method compared with Guava's wrapper.
 */
class SynchronizedBiMapTest {

  private static BiMap<String, Integer> lava() {
    BiMap<String, Integer> bimap = HashBiMap.create();
    bimap.put("a", 1);
    bimap.put("b", 2);
    bimap.put("c", 3);
    return Maps.synchronizedBiMap(bimap);
  }

  private static com.google.common.collect.BiMap<String, Integer> guava() {
    com.google.common.collect.BiMap<String, Integer> bimap =
        com.google.common.collect.HashBiMap.create();
    bimap.put("a", 1);
    bimap.put("b", 2);
    bimap.put("c", 3);
    return com.google.common.collect.Maps.synchronizedBiMap(bimap);
  }

  @Test
  void wrappingIsIdempotentAndTheInverseIsCached() {
    BiMap<String, Integer> sync = lava();
    assertThat(Maps.synchronizedBiMap(sync)).isSameAs(sync);
    assertThat(sync.inverse()).isSameAs(sync.inverse());
    assertThat(sync.inverse().inverse()).isSameAs(sync);
    assertThat(Maps.synchronizedBiMap(sync.inverse())).isSameAs(sync.inverse());
  }

  @Test
  void viewsAreCachedLikeGuavas() {
    BiMap<String, Integer> sync = lava();
    com.google.common.collect.BiMap<String, Integer> guava = guava();
    assertThat(sync.keySet() == sync.keySet()).isEqualTo(guava.keySet() == guava.keySet()).isTrue();
    assertThat(sync.values() == sync.values()).isEqualTo(guava.values() == guava.values()).isTrue();
    assertThat(sync.entrySet() == sync.entrySet())
        .isEqualTo(guava.entrySet() == guava.entrySet())
        .isTrue();
    // The views are live.
    sync.put("d", 4);
    assertThat(sync.values()).containsExactly(1, 2, 3, 4);
  }

  @Test
  void forcePutAndPutThroughTheInverse() {
    BiMap<String, Integer> sync = lava();
    assertThat(sync.forcePut("z", 1)).isNull();
    assertThat(sync).doesNotContainKey("a").containsEntry("z", 1);
    assertThat(sync.inverse().forcePut(2, "y")).isEqualTo("b");
    assertThat(sync.toString()).isEqualTo("{c=3, z=1, y=2}");
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> sync.put("q", 3))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("value already present: 3");
  }

  /** Runs one operation on Lava's and Guava's wrapper and compares results and contents. */
  private static void same(String what, Function<Map<String, Integer>, Object> operation) {
    BiMap<String, Integer> lava = lava();
    com.google.common.collect.BiMap<String, Integer> guava = guava();
    assertThat(outcome(lava, operation)).as(what).isEqualTo(outcome(guava, operation));
    assertThat(lava.toString()).as(what + " contents").isEqualTo(guava.toString());
    assertThat(lava.inverse().toString()).as(what + " inverse").isEqualTo(guava.inverse().toString());
  }

  private static String outcome(Map<String, Integer> map, Function<Map<String, Integer>, Object> op) {
    try {
      return String.valueOf(op.apply(map));
    } catch (RuntimeException e) {
      return e.getClass().getName() + ": " + ExceptionMessages.of(e);
    }
  }

  @Test
  void mapDefaultMethodsBehaveLikeGuavas() {
    same("replaceAll shift", m -> {
      m.replaceAll((k, v) -> v + 1);
      return m;
    });
    same("replaceAll permutation", m -> {
      m.replaceAll((k, v) -> 4 - v);
      return m;
    });
    same("replaceAll collision", m -> {
      m.replaceAll((k, v) -> 7);
      return m;
    });
    same("forEach", m -> {
      List<String> seen = new ArrayList<>();
      m.forEach((k, v) -> seen.add(k + v));
      return seen;
    });
    same("getOrDefault", m -> m.getOrDefault("a", 9) + "/" + m.getOrDefault("x", 9));
    same("putIfAbsent new", m -> m.putIfAbsent("d", 4));
    same("putIfAbsent existing", m -> m.putIfAbsent("a", 9));
    same("putIfAbsent duplicate value", m -> m.putIfAbsent("d", 1));
    same("replace", m -> m.replace("a", 9) + "/" + m.replace("x", 9));
    same("replace duplicate value", m -> m.replace("a", 2));
    same("replace(k, old, new)", m -> m.replace("a", 1, 8) + "/" + m.replace("b", 1, 8));
    same("remove(k, v)", m -> m.remove("a", 1) + "/" + m.remove("b", 1));
    same("computeIfAbsent", m -> m.computeIfAbsent("d", k -> 4) + "/" + m.computeIfAbsent("a", k -> 9));
    same("computeIfAbsent duplicate value", m -> m.computeIfAbsent("d", k -> 1));
    same("computeIfPresent", m -> m.computeIfPresent("a", (k, v) -> v * 10) + "/"
        + m.computeIfPresent("b", (k, v) -> null));
    same("compute", m -> m.compute("a", (k, v) -> v == null ? 0 : v + 10) + "/"
        + m.compute("x", (k, v) -> v == null ? 0 : v + 10));
    same("compute duplicate value", m -> m.compute("a", (k, v) -> 2));
    same("merge", m -> m.merge("a", 5, Integer::sum) + "/" + m.merge("x", 5, Integer::sum) + "/"
        + m.merge("b", 5, (x, y) -> null));
  }

  @Test
  void serialization() {
    BiMap<String, Integer> sync = lava();
    BiMap<String, Integer> copy = SerializableTester.reserialize(sync);
    assertThat(copy).isEqualTo(sync);
    assertThat(copy.inverse().get(2)).isEqualTo("b");
    assertThat(copy.inverse().inverse()).isSameAs(copy);
  }
}
