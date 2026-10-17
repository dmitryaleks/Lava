package com.simplelibs.lava.collect;

import static org.assertj.core.api.Assertions.assertThat;

import com.simplelibs.lava.testing.ExceptionMessages;
import com.simplelibs.lava.testing.NullPointerTester;
import com.simplelibs.lava.testing.SerializableTester;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Differential tests against Guava 27.0.1 for {@link ImmutableSetMultimap} with value and key
 * comparators (including comparators inconsistent with {@code equals}), and for single-entry
 * {@link ImmutableMap}s.
 */
class ImmutableSetMultimapDifferentialTest {

  /** Orders by length only, so different strings of the same length compare as equal. */
  private enum ByLength implements Comparator<String>, Serializable {
    INSTANCE;

    @Override
    public int compare(String a, String b) {
      return Integer.compare(a.length(), b.length());
    }
  }

  private static String describe(Object multimap, Map<?, ?> asMap, Object values, Object inverse) {
    return multimap + "|" + multimap.hashCode() + "|" + asMap.size() + "|" + values + "|" + inverse;
  }

  @Test
  void orderValuesByMatchesGuavaIncludingComparatorEqualValues() {
    Random random = new Random(7);
    String[] words = {"a", "b", "xx", "yy", "zzz", "w", "q", "ab", "abc", "c"};
    for (int round = 0; round < 500; round++) {
      ImmutableSetMultimap.Builder<String, String> lava = ImmutableSetMultimap.builder();
      com.google.common.collect.ImmutableSetMultimap.Builder<String, String> guava =
          com.google.common.collect.ImmutableSetMultimap.builder();
      boolean orderValues = random.nextBoolean();
      boolean orderKeys = random.nextBoolean();
      if (orderValues) {
        lava.orderValuesBy(ByLength.INSTANCE);
        guava.orderValuesBy(ByLength.INSTANCE);
      }
      if (orderKeys) {
        lava.orderKeysBy(Comparator.reverseOrder());
        guava.orderKeysBy(Comparator.reverseOrder());
      }
      int n = random.nextInt(12);
      for (int i = 0; i < n; i++) {
        String key = "k" + random.nextInt(4);
        String value = words[random.nextInt(words.length)];
        lava.put(key, value);
        guava.put(key, value);
      }
      ImmutableSetMultimap<String, String> l = lava.build();
      com.google.common.collect.ImmutableSetMultimap<String, String> g = guava.build();
      String what = "round " + round + " (orderValues=" + orderValues + ")";
      assertThat(describe(l, l.asMap(), l.values(), l.inverse()))
          .as(what)
          .isEqualTo(describe(g, g.asMap(), g.values(), g.inverse()));
      assertThat(l.size()).as(what).isEqualTo(g.size());
      for (int k = 0; k < 5; k++) {
        assertThat(l.get("k" + k).toString()).as(what).isEqualTo(g.get("k" + k).toString());
      }
      assertThat(l.entries().toString()).as(what).isEqualTo(g.entries().toString());
      // Serialization round trip keeps contents and order (Lava to Lava).
      ImmutableSetMultimap<String, String> copy = SerializableTester.reserialize(l);
      assertThat(copy.toString()).isEqualTo(l.toString());
      assertThat(copy.get("absent")).isEmpty();
      // copyOf(Iterable<Entry>) and putAll(Iterable<Entry>) agree with Guava too.
      List<Map.Entry<String, String>> entries = new ArrayList<>(l.entries());
      assertThat(ImmutableSetMultimap.copyOf(entries).toString())
          .isEqualTo(com.google.common.collect.ImmutableSetMultimap.copyOf(entries).toString());
      assertThat(ImmutableSetMultimap.<String, String>builder().putAll(entries).build())
          .isEqualTo(ImmutableSetMultimap.copyOf(entries));
    }
  }

  @Test
  void entriesContainsChecksValues() {
    ImmutableSetMultimap<String, Integer> multimap = ImmutableSetMultimap.of("a", 1, "a", 2, "b", 3);
    assertThat(multimap.entries().contains(Maps.immutableEntry("a", 1))).isTrue();
    assertThat(multimap.entries().contains(Maps.immutableEntry("a", 3))).isFalse();
    assertThat(multimap.entries().contains(Maps.immutableEntry("c", 1))).isFalse();
    assertThat(multimap.entries().contains("not an entry")).isFalse();
  }

  @Test
  void singleEntryMapsBehaveLikeGuavasSingletonBiMap() {
    List<ImmutableMap<String, Integer>> lava =
        List.of(
            ImmutableMap.of("a", 1),
            ImmutableMap.<String, Integer>builder().put("a", 1).build(),
            ImmutableMap.copyOf(Map.of("a", 1)),
            ImmutableMap.copyOf(List.of(Maps.immutableEntry("a", 1))));
    com.google.common.collect.ImmutableMap<String, Integer> guava =
        com.google.common.collect.ImmutableMap.of("a", 1);
    for (ImmutableMap<String, Integer> map : lava) {
      assertThat(map).isInstanceOf(SingletonImmutableMap.class);
      assertThat(map.values()).isInstanceOf(ImmutableSet.class);
      assertThat(map.values().equals(ImmutableSet.of(1)))
          .isEqualTo(guava.values().equals(com.google.common.collect.ImmutableSet.of(1)))
          .isTrue();
      assertThat(map.values().hashCode()).isEqualTo(guava.values().hashCode());
      assertThat(map.toString()).isEqualTo(guava.toString());
      assertThat(map.entrySet().toString()).isEqualTo(guava.entrySet().toString());
      assertThat(map.get("a")).isEqualTo(1);
      assertThat(map.get("b")).isNull();
      assertThat(map.getOrDefault("b", 7)).isEqualTo(guava.getOrDefault("b", 7));
      assertThat(map.containsValue(1)).isTrue();
      assertThat(map.containsValue(2)).isFalse();
      assertThat(map.asMultimap().toString()).isEqualTo(guava.asMultimap().toString());
      assertThat(SerializableTester.reserialize(map)).isEqualTo(map);
      List<String> forEach = new ArrayList<>();
      map.forEach((k, v) -> forEach.add(k + v));
      assertThat(forEach).containsExactly("a1");
    }
  }

  private static String outcome(java.util.function.Supplier<?> call) {
    try {
      return String.valueOf(call.get());
    } catch (RuntimeException e) {
      return e.getClass().getName() + ": " + ExceptionMessages.of(e);
    }
  }

  @Test
  void nullHandlingMatchesGuava() {
    List<String> nullValue = java.util.Arrays.asList("x", null);
    assertThat(outcome(() -> ImmutableSetMultimap.<String, Integer>builder().putAll(null, List.of(1))))
        .isEqualTo(
            outcome(
                () ->
                    com.google.common.collect.ImmutableSetMultimap.<String, Integer>builder()
                        .putAll(null, List.of(1))))
        .contains("null key in entry: null=[1]");
    assertThat(outcome(() -> ImmutableSetMultimap.<String, String>builder().putAll("k", nullValue)))
        .isEqualTo(
            outcome(
                () ->
                    com.google.common.collect.ImmutableSetMultimap.<String, String>builder()
                        .putAll("k", nullValue)));
    assertThat(outcome(() -> ImmutableSetMultimap.<String, Integer>builder().put(null, 1)))
        .isEqualTo(
            outcome(
                () ->
                    com.google.common.collect.ImmutableSetMultimap.<String, Integer>builder()
                        .put(null, 1)));
    assertThat(outcome(() -> ImmutableSetMultimap.<String, Integer>builder().orderKeysBy(null)))
        .isEqualTo(
            outcome(
                () ->
                    com.google.common.collect.ImmutableSetMultimap.<String, Integer>builder()
                        .orderKeysBy(null)));
    assertThat(outcome(() -> ImmutableSetMultimap.<String, Integer>builder().orderValuesBy(null)))
        .isEqualTo(
            outcome(
                () ->
                    com.google.common.collect.ImmutableSetMultimap.<String, Integer>builder()
                        .orderValuesBy(null)));
    // A null key is rejected even when its value stream is empty.
    assertThat(
            outcome(
                () ->
                    java.util.stream.Stream.of("a")
                        .collect(
                            ImmutableSetMultimap.flatteningToImmutableSetMultimap(
                                s -> (String) null, s -> java.util.stream.Stream.<String>empty()))))
        .isEqualTo(
            outcome(
                () ->
                    java.util.stream.Stream.of("a")
                        .collect(
                            com.google.common.collect.ImmutableSetMultimap
                                .flatteningToImmutableSetMultimap(
                                    s -> (String) null, s -> java.util.stream.Stream.<String>empty()))))
        .startsWith("java.lang.NullPointerException");
    assertThat(
            outcome(
                () ->
                    java.util.stream.Stream.of("a")
                        .collect(
                            ImmutableSetMultimap.flatteningToImmutableSetMultimap(
                                s -> s, s -> nullValue.stream()))))
        .isEqualTo(
            outcome(
                () ->
                    java.util.stream.Stream.of("a")
                        .collect(
                            com.google.common.collect.ImmutableSetMultimap
                                .flatteningToImmutableSetMultimap(s -> s, s -> nullValue.stream()))));
    assertThat(
            outcome(
                () ->
                    java.util.stream.Stream.of("a")
                        .collect(ImmutableSetMultimap.toImmutableSetMultimap(s -> s, s -> (String) null))))
        .isEqualTo(
            outcome(
                () ->
                    java.util.stream.Stream.of("a")
                        .collect(
                            com.google.common.collect.ImmutableSetMultimap.toImmutableSetMultimap(
                                s -> s, s -> (String) null))));
  }

  @Test
  void nullPointers() {
    NullPointerTester tester = new NullPointerTester();
    tester.testAllPublicStaticMethods(ImmutableSetMultimap.class);
    tester.testAllPublicInstanceMethods(ImmutableSetMultimap.builder());
    tester.testAllPublicInstanceMethods(ImmutableSetMultimap.of("a", 1));
    tester.testAllPublicInstanceMethods(ImmutableMap.of("a", 1));
  }
}
