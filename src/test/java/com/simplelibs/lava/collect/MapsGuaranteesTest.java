package com.simplelibs.lava.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.simplelibs.lava.base.Converter;
import com.simplelibs.lava.testing.ExceptionMessages;
import com.simplelibs.lava.testing.SerializableTester;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Guarantees of the Milestone 5 types that the ported Guava tests leave unasserted: identity and
 * caching of wrappers, serialization of the serializable views, exception messages, the {@link
 * Range} fast paths for sorted sets and their tie-breaking, each compared with Guava 27.0.1 where
 * the behaviour is observable.
 */
class MapsGuaranteesTest {

  private enum Letter {
    A,
    B,
    C
  }

  private static String outcome(Supplier<?> call) {
    try {
      return String.valueOf(call.get());
    } catch (RuntimeException e) {
      return e.getClass().getName() + ": " + ExceptionMessages.of(e);
    }
  }

  @Test
  void unmodifiableNavigableMapIsIdempotentAndCachesItsDescendingMap() {
    NavigableMap<String, Integer> lava = Maps.unmodifiableNavigableMap(new TreeMap<>(Map.of("a", 1)));
    NavigableMap<String, Integer> guava =
        com.google.common.collect.Maps.unmodifiableNavigableMap(new TreeMap<>(Map.of("a", 1)));
    assertThat(Maps.unmodifiableNavigableMap(lava)).isSameAs(lava);
    assertThat(lava.descendingMap()).isSameAs(lava.descendingMap());
    assertThat(lava.descendingMap().descendingMap()).isSameAs(lava);
    assertThat(com.google.common.collect.Maps.unmodifiableNavigableMap(guava)).isSameAs(guava);
    assertThat(guava.descendingMap()).isSameAs(guava.descendingMap());
    assertThat(guava.descendingMap().descendingMap()).isSameAs(guava);
  }

  @Test
  void serializableViewsRoundTrip() {
    HashBiMap<String, Integer> bimap = HashBiMap.create(Map.of("a", 1, "b", 2));
    BiMap<Integer, String> inverse = SerializableTester.reserialize(bimap.inverse());
    assertThat(inverse).isEqualTo(bimap.inverse());
    // The inverse keeps its direction after deserialization.
    assertThat(inverse.get(1)).isEqualTo("a");
    assertThat(inverse.inverse().get("a")).isEqualTo(1);
    assertThat(inverse.inverse().inverse()).isSameAs(inverse);

    NavigableMap<String, Integer> unmodifiable =
        Maps.unmodifiableNavigableMap(new TreeMap<>(Map.of("a", 1, "b", 2)));
    NavigableMap<String, Integer> copy = SerializableTester.reserialize(unmodifiable);
    assertThat(copy).isEqualTo(unmodifiable);
    assertThatThrownBy(() -> copy.put("c", 3)).isInstanceOf(UnsupportedOperationException.class);

    SerializableTester.reserializeAndAssert(Maps.immutableEntry("k", 1));
    SerializableTester.reserializeAndAssert(Maps.immutableEntry(null, null));
    SerializableTester.reserializeAndAssert(Maps.unmodifiableBiMap(bimap));
    SerializableTester.reserializeAndAssert(Maps.synchronizedBiMap(HashBiMap.create(bimap)));
  }

  @Test
  void asConverterMessage() {
    Converter<String, Integer> converter = Maps.asConverter(HashBiMap.create(Map.of("a", 1)));
    assertThat(converter.convert("a")).isEqualTo(1);
    assertThat(converter.reverse().convert(1)).isEqualTo("a");
    assertThatThrownBy(() -> converter.convert("x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("No non-null mapping present for input: x");
    assertThatThrownBy(() -> converter.reverse().convert(9))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("No non-null mapping present for input: 9");
  }

  @Test
  void subMapRejectsRangesItsComparatorInverts() {
    NavigableMap<Integer, Integer> reversed = new TreeMap<>(Collections.reverseOrder());
    String expected = "map is using a custom comparator which is inconsistent with the natural ordering.";
    assertThatThrownBy(() -> Maps.subMap(reversed, Range.closed(2, 7)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(expected);
    assertThat(outcome(() -> Maps.subMap(reversed, Range.closed(2, 7))))
        .isEqualTo(
            outcome(
                () ->
                    com.google.common.collect.Maps.subMap(
                        reversed, com.google.common.collect.Range.closed(2, 7))));
    // Ranges with one bound, or with equal bounds, are accepted.
    assertThat(Maps.subMap(reversed, Range.atLeast(4))).isEmpty();
    assertThat(Maps.subMap(reversed, Range.closed(3, 3))).isEmpty();
  }

  @Test
  void immutableEnumMapReturnsAnImmutableEnumMapItself() {
    ImmutableMap<Letter, Integer> map = Maps.immutableEnumMap(Map.of(Letter.A, 1, Letter.C, 3));
    assertThat(Maps.immutableEnumMap(map)).isSameAs(map);
    assertThat(map.toString()).isEqualTo("{A=1, C=3}");
  }

  /**
   * Filtering a map whose key set is already a Lava filtered set composes both predicates
   * (Guava's {@code Sets.filter} composition). Reached through a user map that exposes another
   * view's key set.
   */
  @Test
  void filteringAFilteredKeySetComposesBothPredicates() {
    Map<Integer, Integer> base = new LinkedHashMap<>();
    for (int i = 0; i < 12; i++) {
      base.put(i, i * 10);
    }
    Map<Integer, Integer> evens = Maps.filterKeys(base, k -> k % 2 == 0);
    Map<Integer, Integer> exposing =
        new java.util.AbstractMap<Integer, Integer>() {
          @Override
          public java.util.Set<Entry<Integer, Integer>> entrySet() {
            return evens.entrySet();
          }

          @Override
          public java.util.Set<Integer> keySet() {
            return evens.keySet();
          }
        };
    Map<Integer, Integer> bigEvens = Maps.filterKeys(exposing, k -> k > 5);
    assertThat(bigEvens.keySet()).containsExactly(6, 8, 10);
    assertThat(bigEvens.keySet().contains(7)).isFalse();
    assertThat(bigEvens.entrySet().toString()).isEqualTo("[6=60, 8=80, 10=100]");
    Map<Integer, Integer> guavaEvens = com.google.common.collect.Maps.filterKeys(base, k -> k % 2 == 0);
    assertThat(bigEvens.keySet().toString())
        .isEqualTo(com.google.common.collect.Maps.filterKeys(
                new java.util.AbstractMap<Integer, Integer>() {
                  @Override
                  public java.util.Set<Entry<Integer, Integer>> entrySet() {
                    return guavaEvens.entrySet();
                  }

                  @Override
                  public java.util.Set<Integer> keySet() {
                    return guavaEvens.keySet();
                  }
                },
                k -> k > 5)
            .keySet()
            .toString());
  }

  @Test
  void immutableEnumMapChecksTheFirstEntryToo() {
    for (Map<Letter, Integer> input : nullEntryMaps()) {
      assertThat(outcome(() -> Maps.immutableEnumMap(input)))
          .as(String.valueOf(input))
          .isEqualTo(outcome(() -> com.google.common.collect.Maps.immutableEnumMap(input)))
          .startsWith("java.lang.NullPointerException");
    }
    assertThat(outcome(() -> Maps.immutableEnumMap(nullEntryMaps().get(0))))
        .isEqualTo("java.lang.NullPointerException: null value in entry: A=null");
  }

  private static List<Map<Letter, Integer>> nullEntryMaps() {
    List<Map<Letter, Integer>> maps = new ArrayList<>();
    Map<Letter, Integer> firstNull = new LinkedHashMap<>();
    firstNull.put(Letter.A, null);
    firstNull.put(Letter.B, 1);
    maps.add(firstNull);
    Map<Letter, Integer> laterNull = new EnumMap<>(Letter.class);
    laterNull.put(Letter.A, 1);
    laterNull.put(Letter.C, null);
    maps.add(laterNull);
    Map<Letter, Integer> nullKey = new LinkedHashMap<>();
    nullKey.put(Letter.A, 1);
    nullKey.put(null, 2);
    maps.add(nullKey);
    return maps;
  }

  @Test
  void rangeSortedSetFastPathsMatchTheSlowPaths() {
    Random random = new Random(0x5E7L);
    for (int round = 0; round < 500; round++) {
      List<Integer> values = new ArrayList<>();
      int n = 1 + random.nextInt(6);
      for (int i = 0; i < n; i++) {
        values.add(random.nextInt(20));
      }
      Range<Integer> range = Range.closed(5, 12);
      com.google.common.collect.Range<Integer> guavaRange =
          com.google.common.collect.Range.closed(5, 12);
      List<SortedSet<Integer>> sets = new ArrayList<>();
      sets.add(new TreeSet<>(values));
      SortedSet<Integer> naturalComparator = new TreeSet<>(Comparator.naturalOrder());
      naturalComparator.addAll(values);
      sets.add(naturalComparator);
      sets.add(Collections.unmodifiableSortedSet(new TreeSet<>(values)));
      for (SortedSet<Integer> set : sets) {
        String what = values + " as " + set.comparator();
        assertThat(range.containsAll(set)).as(what).isEqualTo(guavaRange.containsAll(set));
        assertThat(range.containsAll(set)).as(what).isEqualTo(range.containsAll(values));
        assertThat(Range.encloseAll(set).toString())
            .as(what)
            .isEqualTo(com.google.common.collect.Range.encloseAll(set).toString())
            .isEqualTo(Range.encloseAll(values).toString());
      }
      // A reverse-ordered set takes the general path, which must still find the extremes.
      SortedSet<Integer> reverse = new TreeSet<>(Collections.reverseOrder());
      reverse.addAll(values);
      assertThat(Range.encloseAll(reverse).toString())
          .isEqualTo(com.google.common.collect.Range.encloseAll(reverse).toString());
      assertThat(range.containsAll(reverse)).isEqualTo(guavaRange.containsAll(reverse));
    }
    // A set whose first element is outside but last element inside, and vice versa.
    SortedSet<Integer> firstOutside = new TreeSet<>(Comparator.naturalOrder());
    firstOutside.addAll(List.of(1, 6, 7));
    assertThat(Range.closed(5, 12).containsAll(firstOutside)).isFalse();
    SortedSet<Integer> lastOutside = new TreeSet<>(List.of(6, 7, 20));
    assertThat(Range.closed(5, 12).containsAll(lastOutside)).isFalse();
    assertThat(Range.closed(5, 12).containsAll(new TreeSet<>(List.of(5, 9, 12)))).isTrue();
  }

  @Test
  void encloseAllBreaksTiesLikeGuava() {
    // 1.0 and 1.00 compare as equal but are not equal, so the tie-breaking rule is observable.
    List<List<BigDecimal>> inputs =
        List.of(
            List.of(new BigDecimal("1.0"), new BigDecimal("1.00")),
            List.of(new BigDecimal("1.00"), new BigDecimal("1.0")),
            List.of(new BigDecimal("2"), new BigDecimal("1.0"), new BigDecimal("2.00"), new BigDecimal("1.00")),
            List.of(new BigDecimal("3.0"), new BigDecimal("3"), new BigDecimal("0.10"), new BigDecimal("0.1")));
    for (List<BigDecimal> values : inputs) {
      Range<BigDecimal> lava = Range.encloseAll(values);
      com.google.common.collect.Range<BigDecimal> guava =
          com.google.common.collect.Range.encloseAll(values);
      assertThat(lava.lowerEndpoint().toPlainString() + ".." + lava.upperEndpoint().toPlainString())
          .as(String.valueOf(values))
          .isEqualTo(
              guava.lowerEndpoint().toPlainString() + ".." + guava.upperEndpoint().toPlainString());
    }
  }

  @Test
  @SuppressWarnings("deprecation")
  void deprecatedApplyIsContains() {
    Range<Integer> range = Range.closedOpen(1, 4);
    for (int i = -1; i <= 5; i++) {
      assertThat(range.apply(i)).as(String.valueOf(i)).isEqualTo(range.contains(i));
      assertThat(range.test(i)).as(String.valueOf(i)).isEqualTo(range.contains(i));
    }
  }

  @Test
  void sortedDifferenceUsesGuavasNaturalOrdering() {
    // final review n1: Guava uses Ordering.natural(), not Comparator.naturalOrder()
    Map<Integer, Integer> withNullKey = new java.util.HashMap<>();
    withNullKey.put(null, 1);
    Throwable lava = catchThrowable(() -> Maps.difference(new TreeMap<Integer, Integer>(), withNullKey));
    Throwable guava =
        catchThrowable(
            () -> com.google.common.collect.Maps.difference(new TreeMap<Integer, Integer>(), withNullKey));
    assertThat(lava).isInstanceOf(NullPointerException.class);
    assertThat(lava.getMessage()).isEqualTo(guava.getMessage()).isNull();

    TreeMap<Integer, String> left = new TreeMap<>(Map.of(1, "a", 2, "b"));
    Map<Integer, String> right = Map.of(2, "b", 3, "c");
    SortedMapDifference<Integer, String> diff = Maps.difference(left, right);
    com.google.common.collect.SortedMapDifference<Integer, String> guavaDiff =
        com.google.common.collect.Maps.difference(left, right);
    assertThat(diff.entriesOnlyOnLeft().comparator().toString())
        .isEqualTo(guavaDiff.entriesOnlyOnLeft().comparator().toString())
        .isEqualTo("Ordering.natural()");
    assertThat(diff.toString()).isEqualTo(guavaDiff.toString());
    assertThat(SerializableTester.reserialize(diff.entriesOnlyOnLeft().comparator()))
        .isSameAs(diff.entriesOnlyOnLeft().comparator());
    // a sub map over it accepts natural ranges, like a map without a comparator
    NavigableMap<Integer, String> onBoth = new TreeMap<>(diff.entriesInCommon());
    assertThat(Maps.subMap(onBoth, Range.closed(1, 3))).isEqualTo(Map.of(2, "b"));
  }
}
