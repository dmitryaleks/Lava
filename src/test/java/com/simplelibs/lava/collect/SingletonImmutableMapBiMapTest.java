package com.simplelibs.lava.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.simplelibs.lava.testing.SerializableTester;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Every one-entry {@code ImmutableMap} is a {@code BiMap} in Guava 27.0.1 (a {@code
 * SingletonImmutableBiMap}), and Lava's {@code SingletonImmutableMap} is one too: the final review
 * found that it was not, which changed {@code instanceof} checks, {@code values()} equality of
 * filtered views and event-bus dispatch. The empty map is not a {@code BiMap} in either library.
 */
class SingletonImmutableMapBiMapTest {

  enum Color {
    RED,
    GREEN
  }

  /** Whether each one-entry (or empty) factory result is a BiMap, for the given library. */
  private static List<Boolean> biMapness(
      List<Map<?, ?>> maps, Class<?> biMapType) {
    List<Boolean> result = new ArrayList<>();
    for (Map<?, ?> map : maps) {
      result.add(biMapType.isInstance(map));
    }
    return result;
  }

  @Test
  void oneEntryMapsAreBiMapsAsInGuava() {
    EnumMap<Color, String> lavaEnum = new EnumMap<>(Color.class);
    lavaEnum.put(Color.RED, "r");
    List<Map<?, ?>> lava =
        List.of(
            ImmutableMap.of(),
            ImmutableMap.of(1, "a"),
            ImmutableMap.copyOf(Map.of(1, "a")),
            ImmutableMap.builder().put(1, "a").build(),
            Maps.toMap(List.of(1), k -> "a"),
            Maps.uniqueIndex(List.of("a"), String::length),
            Maps.immutableEnumMap(lavaEnum),
            ImmutableSetMultimap.of(1, "a").asMap());
    List<Map<?, ?>> guava =
        List.of(
            com.google.common.collect.ImmutableMap.of(),
            com.google.common.collect.ImmutableMap.of(1, "a"),
            com.google.common.collect.ImmutableMap.copyOf(Map.of(1, "a")),
            com.google.common.collect.ImmutableMap.builder().put(1, "a").build(),
            com.google.common.collect.Maps.toMap(List.of(1), k -> "a"),
            com.google.common.collect.Maps.uniqueIndex(List.of("a"), String::length),
            com.google.common.collect.Maps.immutableEnumMap(lavaEnum),
            com.google.common.collect.ImmutableSetMultimap.of(1, "a").asMap());
    assertThat(biMapness(lava, BiMap.class))
        .isEqualTo(biMapness(guava, com.google.common.collect.BiMap.class))
        .containsExactly(false, true, true, true, true, true, true, true);
  }

  @Test
  void biMapBehaviourMatchesGuava() {
    @SuppressWarnings("unchecked")
    BiMap<Integer, String> lava = (BiMap<Integer, String>) ImmutableMap.of(1, "a");
    @SuppressWarnings("unchecked")
    com.google.common.collect.BiMap<Integer, String> guava =
        (com.google.common.collect.BiMap<Integer, String>) com.google.common.collect.ImmutableMap.of(1, "a");
    assertThat(lava.inverse()).isEqualTo(guava.inverse()).isEqualTo(Map.of("a", 1));
    assertThat(lava.inverse().inverse()).isSameAs(lava);
    assertThat(lava.inverse()).isSameAs(lava.inverse());
    assertThat(lava.inverse().toString()).isEqualTo(guava.inverse().toString());
    assertThat(lava.values()).isInstanceOf(Set.class).isEqualTo(Set.of("a"));
    assertThat(lava.values().equals(Set.of("a"))).isEqualTo(guava.values().equals(Set.of("a")));
    assertThat(lava.inverse().values()).isEqualTo(Set.of(1));
    assertThatThrownBy(() -> forcePut(lava)).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> forcePut(guava::forcePut)).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> lava.put(2, "b")).isInstanceOf(UnsupportedOperationException.class);
    assertThat(lava).isEqualTo(ImmutableMap.of(1, "a"));

    Object copy = SerializableTester.reserialize(lava);
    assertThat(copy).isInstanceOf(BiMap.class).isEqualTo(lava);
    assertThat(((BiMap<?, ?>) copy).inverse()).isEqualTo(Map.of("a", 1));
  }

  @SuppressWarnings("deprecation")
  private static void forcePut(BiMap<Integer, String> map) {
    map.forcePut(2, "b");
  }

  private static void forcePut(java.util.function.BiFunction<Integer, String, String> forcePut) {
    forcePut.apply(2, "b");
  }

  @Test
  void filteredViewsOfOneEntryMapsMatchGuava() {
    Map<Integer, String> lava = ImmutableMap.of(1, "a");
    Map<Integer, String> guava = com.google.common.collect.ImmutableMap.of(1, "a");
    List<Function<Map<Integer, String>, Map<Integer, String>>> lavaViews =
        List.of(
            m -> Maps.filterKeys(m, k -> k > 0),
            m -> Maps.filterValues(m, v -> !v.isEmpty()),
            m -> Maps.filterEntries(m, e -> e.getKey() > 0));
    List<Function<Map<Integer, String>, Map<Integer, String>>> guavaViews =
        List.of(
            m -> com.google.common.collect.Maps.filterKeys(m, k -> k > 0),
            m -> com.google.common.collect.Maps.filterValues(m, v -> !v.isEmpty()),
            m -> com.google.common.collect.Maps.filterEntries(m, e -> e.getKey() > 0));
    for (int i = 0; i < lavaViews.size(); i++) {
      Map<Integer, String> l = lavaViews.get(i).apply(lava);
      Map<Integer, String> g = guavaViews.get(i).apply(guava);
      assertThat(l instanceof BiMap).as("view " + i).isEqualTo(g instanceof com.google.common.collect.BiMap);
      assertThat(l.values().equals(Set.of("a")))
          .as("view " + i)
          .isEqualTo(g.values().equals(Set.of("a")));
      assertThat(l).isEqualTo(g);
    }
  }
}
