package com.simplelibs.lava.collect;

import static org.assertj.core.api.Assertions.assertThat;

import com.simplelibs.lava.testing.ExceptionMessages;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Differential tests against the real Guava 27.0.1 for {@link Maps} factories and snapshots,
 * {@link HashBiMap} and {@link Range}.
 */
class MapsDifferentialTest {
  private static final int ROUNDS = 1_000;
  private final Random random = new Random(0x3A95L);

  /**
   * Compares results, or the throwable class and message if either side throws. Errors are
   * compared too: Guava's {@code Range.gap} throws an {@code AssertionError} for some unbounded
   * ranges (its error-message construction fails), and Lava faithfully does the same.
   */
  private static void same(String what, Callable<Object> guava, Callable<Object> lava) {
    String g;
    String l;
    try {
      g = String.valueOf(guava.call());
    } catch (Throwable e) {
      g = e.getClass().getName() + ": " + ExceptionMessages.of(e);
    }
    try {
      l = String.valueOf(lava.call());
    } catch (Throwable e) {
      l = e.getClass().getName() + ": " + ExceptionMessages.of(e);
    }
    assertThat(l).as(what).isEqualTo(g);
  }

  private Map<Integer, Integer> randomMap(boolean sorted) {
    Map<Integer, Integer> map = sorted ? new TreeMap<>() : new LinkedHashMap<>();
    int n = random.nextInt(7);
    for (int i = 0; i < n; i++) {
      map.put(random.nextInt(8), random.nextInt(4));
    }
    return map;
  }

  @Test
  void difference() {
    for (int i = 0; i < ROUNDS; i++) {
      boolean sorted = random.nextBoolean();
      Map<Integer, Integer> left = randomMap(sorted);
      Map<Integer, Integer> right = randomMap(random.nextBoolean());
      same("difference", () -> com.google.common.collect.Maps.difference(left, right), () -> Maps.difference(left, right));
      same(
          "difference.parts",
          () -> {
            com.google.common.collect.MapDifference<Integer, Integer> d = com.google.common.collect.Maps.difference(left, right);
            return d.areEqual() + "|" + d.entriesOnlyOnLeft() + "|" + d.entriesOnlyOnRight() + "|"
                + d.entriesInCommon() + "|" + d.entriesDiffering() + "|" + d.hashCode();
          },
          () -> {
            MapDifference<Integer, Integer> d = Maps.difference(left, right);
            return d.areEqual() + "|" + d.entriesOnlyOnLeft() + "|" + d.entriesOnlyOnRight() + "|"
                + d.entriesInCommon() + "|" + d.entriesDiffering() + "|" + d.hashCode();
          });
      same(
          "difference(equivalence)",
          () -> com.google.common.collect.Maps.difference(left, right, com.google.common.base.Equivalence.identity()),
          () -> Maps.difference(left, right, com.simplelibs.lava.base.Equivalence.identity()));
      if (sorted) {
        TreeMap<Integer, Integer> sortedLeft = new TreeMap<>(left);
        same(
            "difference(SortedMap)",
            () -> com.google.common.collect.Maps.difference(sortedLeft, right).entriesOnlyOnLeft().getClass().getSimpleName()
                + com.google.common.collect.Maps.difference(sortedLeft, right),
            () -> Maps.difference(sortedLeft, right).entriesOnlyOnLeft().getClass().getSimpleName()
                + Maps.difference(sortedLeft, right));
      }
    }
  }

  @Test
  void indexingFactories() {
    for (int i = 0; i < ROUNDS; i++) {
      List<Integer> values = new ArrayList<>();
      int n = random.nextInt(6);
      for (int j = 0; j < n; j++) {
        values.add(random.nextInt(20) == 0 ? null : random.nextInt(9));
      }
      same("uniqueIndex", () -> com.google.common.collect.Maps.uniqueIndex(values, v -> v % 5), () -> Maps.uniqueIndex(values, v -> v % 5));
      same("uniqueIndex(iterator)", () -> com.google.common.collect.Maps.uniqueIndex(values.iterator(), v -> v * 2), () -> Maps.uniqueIndex(values.iterator(), v -> v * 2));
      same("toMap", () -> com.google.common.collect.Maps.toMap(values, v -> v + 1), () -> Maps.toMap(values, v -> v + 1));
      same("toMap(iterator)", () -> com.google.common.collect.Maps.toMap(values.iterator(), v -> v + 1), () -> Maps.toMap(values.iterator(), v -> v + 1));
      List<Thread.State> states = new ArrayList<>();
      for (Integer v : values) {
        states.add(v == null ? null : Thread.State.values()[v % Thread.State.values().length]);
      }
      same(
          "toImmutableEnumMap",
          () -> states.stream().collect(com.google.common.collect.Maps.toImmutableEnumMap(s -> s, s -> s.ordinal())),
          () -> states.stream().collect(Maps.toImmutableEnumMap(s -> s, s -> s.ordinal())));
      same(
          "toImmutableEnumMap(merge)",
          () -> states.stream().collect(com.google.common.collect.Maps.toImmutableEnumMap(s -> s, s -> 1, Integer::sum)),
          () -> states.stream().collect(Maps.toImmutableEnumMap(s -> s, s -> 1, Integer::sum)));
      EnumMap<Thread.State, Integer> enumMap = new EnumMap<>(Thread.State.class);
      for (Thread.State s : states) {
        if (s != null) {
          enumMap.put(s, s.ordinal());
        }
      }
      same("immutableEnumMap", () -> com.google.common.collect.Maps.immutableEnumMap(enumMap), () -> Maps.immutableEnumMap(enumMap));
      same("newHashMapWithExpectedSize", () -> com.google.common.collect.Maps.newHashMapWithExpectedSize(n - 2), () -> Maps.newHashMapWithExpectedSize(n - 2));
      same("newLinkedHashMapWithExpectedSize", () -> com.google.common.collect.Maps.newLinkedHashMapWithExpectedSize(n - 2), () -> Maps.newLinkedHashMapWithExpectedSize(n - 2));
    }
    Properties defaults = new Properties();
    defaults.setProperty("a", "1");
    Properties properties = new Properties(defaults);
    properties.setProperty("b", "2");
    same("fromProperties", () -> new TreeMap<>(com.google.common.collect.Maps.fromProperties(properties)), () -> new TreeMap<>(Maps.fromProperties(properties)));
    same("immutableEntry", () -> com.google.common.collect.Maps.immutableEntry("k", null).hashCode() + "|" + com.google.common.collect.Maps.immutableEntry("k", 1),
        () -> Maps.immutableEntry("k", null).hashCode() + "|" + Maps.immutableEntry("k", 1));
  }

  @Test
  void hashBiMap() {
    for (int round = 0; round < 300; round++) {
      com.google.common.collect.HashBiMap<Integer, Integer> g = com.google.common.collect.HashBiMap.create();
      HashBiMap<Integer, Integer> l = HashBiMap.create();
      com.google.common.collect.BiMap<Integer, Integer> gInverse = g.inverse();
      BiMap<Integer, Integer> lInverse = l.inverse();
      for (int op = 0; op < 30; op++) {
        int k = random.nextInt(8);
        int v = random.nextInt(8);
        int choice = random.nextInt(9);
        String what = "HashBiMap op " + choice + "(" + k + "," + v + ")";
        switch (choice) {
          case 0:
            same(what, () -> g.put(k, v), () -> l.put(k, v));
            break;
          case 1:
            same(what, () -> g.forcePut(k, v), () -> l.forcePut(k, v));
            break;
          case 2:
            same(what, () -> gInverse.put(k, v), () -> lInverse.put(k, v));
            break;
          case 3:
            same(what, () -> gInverse.forcePut(k, v), () -> lInverse.forcePut(k, v));
            break;
          case 4:
            same(what, () -> g.remove(k), () -> l.remove(k));
            break;
          case 5:
            same(what, () -> gInverse.remove(v), () -> lInverse.remove(v));
            break;
          case 6:
            same(what, () -> {
              Iterator<Integer> it = g.values().iterator();
              if (it.hasNext()) { it.next(); it.remove(); }
              return g;
            }, () -> {
              Iterator<Integer> it = l.values().iterator();
              if (it.hasNext()) { it.next(); it.remove(); }
              return l;
            });
            break;
          case 7:
            same(what, () -> {
              for (Map.Entry<Integer, Integer> e : g.entrySet()) {
                if (e.getKey() == k) { return e.setValue(v); }
              }
              return "none";
            }, () -> {
              for (Map.Entry<Integer, Integer> e : l.entrySet()) {
                if (e.getKey() == k) { return e.setValue(v); }
              }
              return "none";
            });
            break;
          default:
            same(what, () -> { g.replaceAll((a, b) -> (a + b) % 8); return g; }, () -> { l.replaceAll((a, b) -> (a + b) % 8); return l; });
        }
        assertThat(l.toString()).as(what).isEqualTo(g.toString());
        assertThat(lInverse.toString()).as(what + " inverse").isEqualTo(gInverse.toString());
        assertThat(l.hashCode()).isEqualTo(g.hashCode());
      }
    }
  }

  private com.google.common.collect.Range<Integer> guavaRange(int kind, int a, int b) {
    int lo = Math.min(a, b);
    int hi = Math.max(a, b);
    switch (kind) {
      case 0: return com.google.common.collect.Range.closed(lo, hi);
      case 1: return com.google.common.collect.Range.open(lo, lo == hi ? hi + 1 : hi);
      case 2: return com.google.common.collect.Range.closedOpen(lo, hi);
      case 3: return com.google.common.collect.Range.openClosed(lo, hi);
      case 4: return com.google.common.collect.Range.atLeast(a);
      case 5: return com.google.common.collect.Range.atMost(a);
      case 6: return com.google.common.collect.Range.greaterThan(a);
      case 7: return com.google.common.collect.Range.lessThan(a);
      case 8: return com.google.common.collect.Range.singleton(a);
      default: return com.google.common.collect.Range.all();
    }
  }

  private Range<Integer> lavaRange(int kind, int a, int b) {
    int lo = Math.min(a, b);
    int hi = Math.max(a, b);
    switch (kind) {
      case 0: return Range.closed(lo, hi);
      case 1: return Range.open(lo, lo == hi ? hi + 1 : hi);
      case 2: return Range.closedOpen(lo, hi);
      case 3: return Range.openClosed(lo, hi);
      case 4: return Range.atLeast(a);
      case 5: return Range.atMost(a);
      case 6: return Range.greaterThan(a);
      case 7: return Range.lessThan(a);
      case 8: return Range.singleton(a);
      default: return Range.all();
    }
  }

  @Test
  void rangeAlgebra() {
    for (int i = 0; i < ROUNDS * 3; i++) {
      int k1 = random.nextInt(10);
      int k2 = random.nextInt(10);
      int a1 = random.nextInt(6);
      int b1 = random.nextInt(6);
      int a2 = random.nextInt(6);
      int b2 = random.nextInt(6);
      int x = random.nextInt(7) - 1;
      com.google.common.collect.Range<Integer> g1 = guavaRange(k1, a1, b1);
      com.google.common.collect.Range<Integer> g2 = guavaRange(k2, a2, b2);
      Range<Integer> l1 = lavaRange(k1, a1, b1);
      Range<Integer> l2 = lavaRange(k2, a2, b2);
      same("toString", () -> g1 + "|" + g1.isEmpty(), () -> l1 + "|" + l1.isEmpty());
      // Guava's BelowAll/AboveAll cuts hash by identity, so only bounded ranges have stable hashes.
      if (g1.hasLowerBound() && g1.hasUpperBound()) {
        same("hashCode", g1::hashCode, l1::hashCode);
      }
      assertThat(lavaRange(k1, a1, b1).hashCode()).isEqualTo(l1.hashCode());
      same("contains", () -> g1.contains(x), () -> l1.contains(x));
      same("encloses", () -> g1.encloses(g2), () -> l1.encloses(l2));
      same("isConnected", () -> g1.isConnected(g2), () -> l1.isConnected(l2));
      same("intersection", () -> g1.intersection(g2), () -> l1.intersection(l2));
      same("span", () -> g1.span(g2), () -> l1.span(l2));
      same("gap", () -> g1.gap(g2), () -> l1.gap(l2));
      same("equals", () -> g1.equals(g2), () -> l1.equals(l2));
      same("bounds", () -> g1.hasLowerBound() + "/" + g1.hasUpperBound(), () -> l1.hasLowerBound() + "/" + l1.hasUpperBound());
      same("lowerEndpoint", () -> g1.lowerEndpoint() + "/" + g1.lowerBoundType(), () -> l1.lowerEndpoint() + "/" + l1.lowerBoundType());
      same("upperEndpoint", () -> g1.upperEndpoint() + "/" + g1.upperBoundType(), () -> l1.upperEndpoint() + "/" + l1.upperBoundType());
      List<Integer> values = random.ints(random.nextInt(4), 0, 6).boxed().collect(Collectors.toList());
      same("encloseAll", () -> com.google.common.collect.Range.encloseAll(values), () -> Range.encloseAll(values));
      same("containsAll", () -> g1.containsAll(values), () -> l1.containsAll(values));
      same("closed(hi, lo)", () -> com.google.common.collect.Range.closed(b1 + 1, b1), () -> Range.closed(b1 + 1, b1));
      same("open(a, a)", () -> com.google.common.collect.Range.open(a1, a1), () -> Range.open(a1, a1));
    }
  }
}
