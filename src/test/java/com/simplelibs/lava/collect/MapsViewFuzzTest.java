package com.simplelibs.lava.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.simplelibs.lava.testing.ExceptionMessages;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * A second, broader differential fuzz of the {@link Maps} views and the BiMap wrappers against
 * Guava 27.0.1 (adapted from the Milestone 5 reviewer's harness). It adds what {@link
 * MapsViewDifferentialTest} lacks: null keys and values, reverse-ordered and {@code
 * Comparator.naturalOrder()} tree maps, sorted maps that are not navigable, filters of filters, all
 * BiMap filters and wrappers, the Java 8 {@code Map} default methods ({@code forEach}, {@code
 * replaceAll}, {@code compute*}, {@code merge}, ...), {@code forcePut}, comparators, and
 * navigation on read-only views. After every operation the result (or exception class and
 * message), the view, the backing map, the hash codes and equality with a copy must agree.
 */
class MapsViewFuzzTest {
  private static final int ROUNDS = 60;
  private static final int OPS = 25;

  private enum Backing {
    HASH(true),
    TREE(false),
    TREE_REVERSE(false),
    TREE_NATURAL_COMPARATOR(false),
    SORTED_ONLY(false),
    BIMAP(false),
    NULL_BIMAP(true);

    final boolean nulls;

    Backing(boolean nulls) {
      this.nulls = nulls;
    }

    Map<Integer, Integer> create(boolean lava) {
      switch (this) {
        case TREE:
          return new TreeMap<>();
        case TREE_REVERSE:
          return new TreeMap<>(Collections.reverseOrder());
        case TREE_NATURAL_COMPARATOR:
          return new TreeMap<>(Comparator.<Integer>naturalOrder());
        case SORTED_ONLY:
          return new SortedOnlyMap<>(new TreeMap<>());
        case BIMAP:
        case NULL_BIMAP:
          return lava ? HashBiMap.create() : com.google.common.collect.HashBiMap.create();
        default:
          return new LinkedHashMap<>();
      }
    }
  }

  /** Creates the view under test over {@code backing}, with Lava's ({@code lava}) or Guava's API. */
  private interface Family {
    Map<Integer, Integer> make(Map<Integer, Integer> backing, boolean lava);
  }

  private static final java.util.function.Predicate<Integer> EVEN = k -> k == null || k % 2 == 0;
  private static final com.google.common.base.Predicate<Integer> G_EVEN =
      k -> k == null || k % 2 == 0;
  private static final java.util.function.Predicate<Integer> BIG = k -> k != null && k > 3;
  private static final com.google.common.base.Predicate<Integer> G_BIG = k -> k != null && k > 3;
  private static final java.util.function.Predicate<Map.Entry<Integer, Integer>> SUM =
      e -> nz(e.getKey()) + nz(e.getValue()) < 12;
  private static final com.google.common.base.Predicate<Map.Entry<Integer, Integer>> G_SUM =
      e -> nz(e.getKey()) + nz(e.getValue()) < 12;

  private static int nz(Integer i) {
    return i == null ? 0 : i;
  }

  private static Integer times10(Integer v) {
    return v == null ? -1 : v * 10;
  }

  private static Integer combine(Integer k, Integer v) {
    return (k == null ? 100 : k) + (v == null ? 1000 : v);
  }

  private static Integer times3(Integer k) {
    return k == null ? -5 : k * 3;
  }

  @SuppressWarnings("unchecked")
  private static NavigableMap<Integer, Integer> nav(Map<Integer, Integer> map) {
    return (NavigableMap<Integer, Integer>) map;
  }

  @SuppressWarnings("unchecked")
  private static SortedMap<Integer, Integer> sorted(Map<Integer, Integer> map) {
    return (SortedMap<Integer, Integer>) map;
  }

  @SuppressWarnings("unchecked")
  private static BiMap<Integer, Integer> bimap(Map<Integer, Integer> map) {
    return (BiMap<Integer, Integer>) map;
  }

  @SuppressWarnings("unchecked")
  private static com.google.common.collect.BiMap<Integer, Integer> gbimap(Map<Integer, Integer> map) {
    return (com.google.common.collect.BiMap<Integer, Integer>) map;
  }

  private static final class Case {
    final String name;
    final Backing backing;
    final Family family;

    Case(String name, Backing backing, Family family) {
      this.name = name;
      this.backing = backing;
      this.family = family;
    }
  }

  private static List<Case> cases() {
    List<Case> c = new ArrayList<>();
    // Hash-backed, null keys and values allowed.
    c.add(new Case("filterKeys(hash)", Backing.HASH, (b, l) -> l
        ? Maps.filterKeys(b, EVEN) : com.google.common.collect.Maps.filterKeys(b, G_EVEN)));
    c.add(new Case("filterValues(hash)", Backing.HASH, (b, l) -> l
        ? Maps.filterValues(b, EVEN) : com.google.common.collect.Maps.filterValues(b, G_EVEN)));
    c.add(new Case("filterEntries(hash)", Backing.HASH, (b, l) -> l
        ? Maps.filterEntries(b, SUM) : com.google.common.collect.Maps.filterEntries(b, G_SUM)));
    c.add(new Case("filterEntries(filterKeys(hash))", Backing.HASH, (b, l) -> l
        ? Maps.filterEntries(Maps.filterKeys(b, EVEN), SUM)
        : com.google.common.collect.Maps.filterEntries(
            com.google.common.collect.Maps.filterKeys(b, G_EVEN), G_SUM)));
    c.add(new Case("filterValues(filterValues(hash))", Backing.HASH, (b, l) -> l
        ? Maps.filterValues(Maps.filterValues(b, EVEN), BIG)
        : com.google.common.collect.Maps.filterValues(
            com.google.common.collect.Maps.filterValues(b, G_EVEN), G_BIG)));
    c.add(new Case("filterKeys(filterEntries(hash))", Backing.HASH, (b, l) -> l
        ? Maps.filterKeys(Maps.filterEntries(b, SUM), BIG)
        : com.google.common.collect.Maps.filterKeys(
            com.google.common.collect.Maps.filterEntries(b, G_SUM), G_BIG)));
    c.add(new Case("transformValues(hash)", Backing.HASH, (b, l) -> l
        ? Maps.transformValues(b, MapsViewFuzzTest::times10)
        : com.google.common.collect.Maps.transformValues(b, MapsViewFuzzTest::times10)));
    c.add(new Case("transformEntries(hash)", Backing.HASH, (b, l) -> l
        ? Maps.transformEntries(b, MapsViewFuzzTest::combine)
        : com.google.common.collect.Maps.transformEntries(b, MapsViewFuzzTest::combine)));
    c.add(new Case("asMap(hash.keySet)", Backing.HASH, (b, l) -> l
        ? Maps.asMap(b.keySet(), MapsViewFuzzTest::times3)
        : com.google.common.collect.Maps.asMap(b.keySet(), MapsViewFuzzTest::times3)));
    // Navigable, natural order.
    c.add(new Case("filterKeys(tree)", Backing.TREE, (b, l) -> l
        ? Maps.filterKeys(nav(b), EVEN) : com.google.common.collect.Maps.filterKeys(nav(b), G_EVEN)));
    c.add(new Case("filterKeys(filterKeys(tree))", Backing.TREE, (b, l) -> l
        ? Maps.filterKeys(Maps.filterKeys(nav(b), EVEN), BIG)
        : com.google.common.collect.Maps.filterKeys(
            com.google.common.collect.Maps.filterKeys(nav(b), G_EVEN), G_BIG)));
    c.add(new Case("filterEntries(tree).descendingMap", Backing.TREE, (b, l) -> l
        ? Maps.filterEntries(nav(b), SUM).descendingMap()
        : com.google.common.collect.Maps.filterEntries(nav(b), G_SUM).descendingMap()));
    c.add(new Case("transformEntries(tree).descendingMap", Backing.TREE, (b, l) -> l
        ? Maps.transformEntries(nav(b), MapsViewFuzzTest::combine).descendingMap()
        : com.google.common.collect.Maps.transformEntries(nav(b), MapsViewFuzzTest::combine)
            .descendingMap()));
    c.add(new Case("transformValues(tree)", Backing.TREE, (b, l) -> l
        ? Maps.transformValues(nav(b), MapsViewFuzzTest::times10)
        : com.google.common.collect.Maps.transformValues(nav(b), MapsViewFuzzTest::times10)));
    c.add(new Case("unmodifiableNavigableMap(tree).descendingMap", Backing.TREE, (b, l) -> l
        ? Maps.unmodifiableNavigableMap(nav(b)).descendingMap()
        : com.google.common.collect.Maps.unmodifiableNavigableMap(nav(b)).descendingMap()));
    c.add(new Case("subMap(tree, [2..7])", Backing.TREE, (b, l) -> l
        ? Maps.subMap(nav(b), Range.closed(2, 7))
        : com.google.common.collect.Maps.subMap(nav(b), com.google.common.collect.Range.closed(2, 7))));
    c.add(new Case("subMap(tree, (2..7)).descendingMap", Backing.TREE, (b, l) -> l
        ? Maps.subMap(nav(b), Range.open(2, 7)).descendingMap()
        : com.google.common.collect.Maps.subMap(nav(b), com.google.common.collect.Range.open(2, 7))
            .descendingMap()));
    c.add(new Case("subMap(tree, (-inf..5])", Backing.TREE, (b, l) -> l
        ? Maps.subMap(nav(b), Range.atMost(5))
        : com.google.common.collect.Maps.subMap(nav(b), com.google.common.collect.Range.atMost(5))));
    c.add(new Case("subMap(tree.descendingMap, [2..7])", Backing.TREE, (b, l) -> l
        ? Maps.subMap(nav(b).descendingMap(), Range.closed(2, 7))
        : com.google.common.collect.Maps.subMap(
            nav(b).descendingMap(), com.google.common.collect.Range.closed(2, 7))));
    c.add(new Case("asMap(tree.navigableKeySet).descendingMap", Backing.TREE, (b, l) -> l
        ? Maps.asMap(nav(b).navigableKeySet(), MapsViewFuzzTest::times3).descendingMap()
        : com.google.common.collect.Maps.asMap(nav(b).navigableKeySet(), MapsViewFuzzTest::times3)
            .descendingMap()));
    c.add(new Case("synchronizedNavigableMap(tree).descendingMap", Backing.TREE, (b, l) -> l
        ? Maps.synchronizedNavigableMap(nav(b)).descendingMap()
        : com.google.common.collect.Maps.synchronizedNavigableMap(nav(b)).descendingMap()));
    // Reverse order and Comparator.naturalOrder().
    c.add(new Case("filterKeys(treeReverse)", Backing.TREE_REVERSE, (b, l) -> l
        ? Maps.filterKeys(nav(b), EVEN) : com.google.common.collect.Maps.filterKeys(nav(b), G_EVEN)));
    c.add(new Case("filterValues(treeReverse).descendingMap", Backing.TREE_REVERSE, (b, l) -> l
        ? Maps.filterValues(nav(b), EVEN).descendingMap()
        : com.google.common.collect.Maps.filterValues(nav(b), G_EVEN).descendingMap()));
    c.add(new Case("transformValues(treeReverse)", Backing.TREE_REVERSE, (b, l) -> l
        ? Maps.transformValues(nav(b), MapsViewFuzzTest::times10)
        : com.google.common.collect.Maps.transformValues(nav(b), MapsViewFuzzTest::times10)));
    c.add(new Case("unmodifiableNavigableMap(treeReverse)", Backing.TREE_REVERSE, (b, l) -> l
        ? Maps.unmodifiableNavigableMap(nav(b))
        : com.google.common.collect.Maps.unmodifiableNavigableMap(nav(b))));
    c.add(new Case("subMap(treeReverse, [2..7])", Backing.TREE_REVERSE, (b, l) -> l
        ? Maps.subMap(nav(b), Range.closed(2, 7))
        : com.google.common.collect.Maps.subMap(nav(b), com.google.common.collect.Range.closed(2, 7))));
    c.add(new Case("subMap(treeReverse, [4..+inf))", Backing.TREE_REVERSE, (b, l) -> l
        ? Maps.subMap(nav(b), Range.atLeast(4))
        : com.google.common.collect.Maps.subMap(nav(b), com.google.common.collect.Range.atLeast(4))));
    c.add(new Case("subMap(treeReverse, all).descendingMap", Backing.TREE_REVERSE, (b, l) -> l
        ? Maps.subMap(nav(b), Range.all()).descendingMap()
        : com.google.common.collect.Maps.subMap(nav(b), com.google.common.collect.Range.<Integer>all())
            .descendingMap()));
    c.add(new Case("subMap(tree.descendingMap, [4..+inf))", Backing.TREE, (b, l) -> l
        ? Maps.subMap(nav(b).descendingMap(), Range.atLeast(4))
        : com.google.common.collect.Maps.subMap(
            nav(b).descendingMap(), com.google.common.collect.Range.atLeast(4))));
    c.add(new Case("asMap(treeReverse.navigableKeySet)", Backing.TREE_REVERSE, (b, l) -> l
        ? Maps.asMap(nav(b).navigableKeySet(), MapsViewFuzzTest::times3)
        : com.google.common.collect.Maps.asMap(nav(b).navigableKeySet(), MapsViewFuzzTest::times3)));
    c.add(new Case("subMap(treeNaturalComparator, [2..7])", Backing.TREE_NATURAL_COMPARATOR, (b, l) -> l
        ? Maps.subMap(nav(b), Range.closed(2, 7))
        : com.google.common.collect.Maps.subMap(nav(b), com.google.common.collect.Range.closed(2, 7))));
    c.add(new Case("filterKeys(treeNaturalComparator).descendingMap", Backing.TREE_NATURAL_COMPARATOR,
        (b, l) -> l
            ? Maps.filterKeys(nav(b), EVEN).descendingMap()
            : com.google.common.collect.Maps.filterKeys(nav(b), G_EVEN).descendingMap()));
    // Sorted but not navigable.
    c.add(new Case("filterKeys(sorted)", Backing.SORTED_ONLY, (b, l) -> l
        ? Maps.filterKeys(sorted(b), EVEN)
        : com.google.common.collect.Maps.filterKeys(sorted(b), G_EVEN)));
    c.add(new Case("filterValues(sorted)", Backing.SORTED_ONLY, (b, l) -> l
        ? Maps.filterValues(sorted(b), EVEN)
        : com.google.common.collect.Maps.filterValues(sorted(b), G_EVEN)));
    c.add(new Case("filterEntries(filterKeys(sorted))", Backing.SORTED_ONLY, (b, l) -> l
        ? Maps.filterEntries(Maps.filterKeys(sorted(b), EVEN), SUM)
        : com.google.common.collect.Maps.filterEntries(
            com.google.common.collect.Maps.filterKeys(sorted(b), G_EVEN), G_SUM)));
    c.add(new Case("transformValues(sorted)", Backing.SORTED_ONLY, (b, l) -> l
        ? Maps.transformValues(sorted(b), MapsViewFuzzTest::times10)
        : com.google.common.collect.Maps.transformValues(sorted(b), MapsViewFuzzTest::times10)));
    c.add(new Case("transformEntries(sorted)", Backing.SORTED_ONLY, (b, l) -> l
        ? Maps.transformEntries(sorted(b), MapsViewFuzzTest::combine)
        : com.google.common.collect.Maps.transformEntries(sorted(b), MapsViewFuzzTest::combine)));
    c.add(new Case("asMap(sortedKeySet)", Backing.SORTED_ONLY, (b, l) -> l
        ? Maps.asMap(Collections.unmodifiableSortedSet(new java.util.TreeSet<>(b.keySet())), MapsViewFuzzTest::times3)
        : com.google.common.collect.Maps.asMap(
            Collections.unmodifiableSortedSet(new java.util.TreeSet<>(b.keySet())), MapsViewFuzzTest::times3)));
    // BiMaps.
    c.add(new Case("bimap", Backing.BIMAP, (b, l) -> b));
    c.add(new Case("bimap.inverse", Backing.BIMAP, (b, l) -> l ? bimap(b).inverse() : gbimap(b).inverse()));
    c.add(new Case("filterKeys(bimap)", Backing.BIMAP, (b, l) -> l
        ? Maps.filterKeys(bimap(b), EVEN) : com.google.common.collect.Maps.filterKeys(gbimap(b), G_EVEN)));
    c.add(new Case("filterValues(bimap)", Backing.BIMAP, (b, l) -> l
        ? Maps.filterValues(bimap(b), BIG) : com.google.common.collect.Maps.filterValues(gbimap(b), G_BIG)));
    c.add(new Case("filterEntries(bimap).inverse", Backing.BIMAP, (b, l) -> l
        ? Maps.filterEntries(bimap(b), SUM).inverse()
        : com.google.common.collect.Maps.filterEntries(gbimap(b), G_SUM).inverse()));
    c.add(new Case("filterValues(filterKeys(bimap))", Backing.BIMAP, (b, l) -> l
        ? Maps.filterValues(Maps.filterKeys(bimap(b), EVEN), BIG)
        : com.google.common.collect.Maps.filterValues(
            com.google.common.collect.Maps.filterKeys(gbimap(b), G_EVEN), G_BIG)));
    c.add(new Case("synchronizedBiMap", Backing.BIMAP, (b, l) -> l
        ? Maps.synchronizedBiMap(bimap(b)) : com.google.common.collect.Maps.synchronizedBiMap(gbimap(b))));
    c.add(new Case("synchronizedBiMap.inverse", Backing.BIMAP, (b, l) -> l
        ? Maps.synchronizedBiMap(bimap(b)).inverse()
        : com.google.common.collect.Maps.synchronizedBiMap(gbimap(b)).inverse()));
    c.add(new Case("unmodifiableBiMap", Backing.BIMAP, (b, l) -> l
        ? Maps.unmodifiableBiMap(bimap(b)) : com.google.common.collect.Maps.unmodifiableBiMap(gbimap(b))));
    c.add(new Case("nullBimap", Backing.NULL_BIMAP, (b, l) -> b));
    c.add(new Case("nullBimap.inverse", Backing.NULL_BIMAP, (b, l) -> l ? bimap(b).inverse() : gbimap(b).inverse()));
    c.add(new Case("filterKeys(nullBimap)", Backing.NULL_BIMAP, (b, l) -> l
        ? Maps.filterKeys(bimap(b), EVEN) : com.google.common.collect.Maps.filterKeys(gbimap(b), G_EVEN)));
    c.add(new Case("filterValues(nullBimap).inverse", Backing.NULL_BIMAP, (b, l) -> l
        ? Maps.filterValues(bimap(b), EVEN).inverse()
        : com.google.common.collect.Maps.filterValues(gbimap(b), G_EVEN).inverse()));
    c.add(new Case("unmodifiableBiMap(nullBimap).inverse", Backing.NULL_BIMAP, (b, l) -> l
        ? Maps.unmodifiableBiMap(bimap(b)).inverse()
        : com.google.common.collect.Maps.unmodifiableBiMap(gbimap(b)).inverse()));
    c.add(new Case("synchronizedBiMap(nullBimap)", Backing.NULL_BIMAP, (b, l) -> l
        ? Maps.synchronizedBiMap(bimap(b)) : com.google.common.collect.Maps.synchronizedBiMap(gbimap(b))));
    c.add(new Case("transformEntries(nullBimap)", Backing.NULL_BIMAP, (b, l) -> l
        ? Maps.transformEntries(b, MapsViewFuzzTest::combine)
        : com.google.common.collect.Maps.transformEntries(b, MapsViewFuzzTest::combine)));
    return c;
  }

  @TestFactory
  List<DynamicTest> viewsBehaveLikeGuava() {
    List<DynamicTest> tests = new ArrayList<>();
    long seed = 987;
    for (Case c : cases()) {
      long caseSeed = seed++;
      tests.add(dynamicTest(c.name, () -> fuzz(c, caseSeed)));
    }
    return tests;
  }

  private static void fuzz(Case c, long seed) {
    Random random = new Random(seed);
    for (int round = 0; round < ROUNDS; round++) {
      Map<Integer, Integer> lavaBacking = c.backing.create(true);
      Map<Integer, Integer> guavaBacking = c.backing.create(false);
      int initial = random.nextInt(8);
      for (int i = 0; i < initial; i++) {
        Integer k = randomInt(random, c.backing.nulls);
        Integer v = randomInt(random, c.backing.nulls);
        String l = call(() -> lavaBacking.put(k, v));
        String g = call(() -> guavaBacking.put(k, v));
        assertThat(l).isEqualTo(g);
      }
      // Some factories reject their input (subMap with a range the comparator inverts); both
      // libraries must reject it identically.
      Object[] made = new Object[2];
      String lavaMade = call(() -> made[0] = c.family.make(lavaBacking, true));
      String guavaMade = call(() -> made[1] = c.family.make(guavaBacking, false));
      if (made[0] == null || made[1] == null) {
        assertThat(lavaMade).as(c.name + " factory").isEqualTo(guavaMade);
        continue;
      }
      @SuppressWarnings("unchecked")
      Map<Integer, Integer> lava = (Map<Integer, Integer>) made[0];
      @SuppressWarnings("unchecked")
      Map<Integer, Integer> guava = (Map<Integer, Integer>) made[1];
      StringBuilder trace = new StringBuilder();
      for (int op = 0; op < OPS; op++) {
        Integer k = randomInt(random, c.backing.nulls);
        Integer v = randomInt(random, c.backing.nulls);
        int choice = random.nextInt(36);
        trace.append(choice).append('(').append(k).append(',').append(v).append(") ");
        String what = c.name + " round " + round + ": " + trace;
        assertThat(call(() -> apply(choice, lava, k, v)))
            .as(what)
            .isEqualTo(call(() -> apply(choice, guava, k, v)));
        assertThat(state(lava, lavaBacking)).as(what + " state").isEqualTo(state(guava, guavaBacking));
      }
    }
  }

  private static Integer randomInt(Random random, boolean nulls) {
    return nulls && random.nextInt(8) == 0 ? null : random.nextInt(10);
  }

  private static String state(Map<Integer, Integer> view, Map<Integer, Integer> backing) {
    return call(() -> view) + " | " + call(() -> backing) + " | h="
        + call(() -> view.hashCode() + "/" + view.entrySet().hashCode() + "/" + view.keySet().hashCode())
        + " | eq=" + call(() -> view.equals(new LinkedHashMap<>(view)) + "/"
            + new LinkedHashMap<>(view).equals(view));
  }

  private static String show(Object o) {
    if (o instanceof Object[]) {
      return Arrays.toString((Object[]) o);
    }
    return String.valueOf(o)
        .replace("com.simplelibs.lava.collect.", "")
        .replace("com.google.common.collect.", "");
  }

  private static String call(Callable<Object> c) {
    try {
      return show(c.call());
    } catch (Throwable t) {
      return "EX " + t.getClass().getSimpleName() + ": " + show(ExceptionMessages.of(t));
    }
  }

  private static String comparatorSignature(SortedMap<Integer, ?> map) {
    Comparator<? super Integer> comparator = map.comparator();
    return comparator == null ? "natural" : "cmp(1,2)=" + Integer.signum(comparator.compare(1, 2));
  }

  /** Describes a bimap-specific operation for either library's BiMap. */
  private static Object forcePut(Map<Integer, Integer> view, Integer k, Integer v) {
    if (view instanceof BiMap) {
      BiMap<Integer, Integer> bm = bimap(view);
      return bm.forcePut(k, v) + "/" + bm.inverse() + "/" + bm.values() + "/"
          + (bm.inverse().inverse() == bm) + "/" + (bm.inverse() == bm.inverse());
    }
    if (view instanceof com.google.common.collect.BiMap) {
      com.google.common.collect.BiMap<Integer, Integer> bm = gbimap(view);
      return bm.forcePut(k, v) + "/" + bm.inverse() + "/" + bm.values() + "/"
          + (bm.inverse().inverse() == bm) + "/" + (bm.inverse() == bm.inverse());
    }
    return view.values().stream().filter(Objects::nonNull).count();
  }

  private static Object apply(int choice, Map<Integer, Integer> view, Integer k, Integer v) {
    NavigableMap<Integer, Integer> nav = view instanceof NavigableMap ? nav(view) : null;
    int kk = k == null ? 0 : k;
    switch (choice) {
      case 0:
        return view.put(k, v);
      case 1:
        return view.remove(k);
      case 2:
        return view.get(k) + "/" + view.containsKey(k) + "/" + view.containsValue(v);
      case 3: {
        Map<Integer, Integer> more = new LinkedHashMap<>();
        more.put(k, v);
        more.put(kk + 1, v == null ? 1 : v + 1);
        view.putAll(more);
        return view.size();
      }
      case 4:
        return view.keySet().remove(k);
      case 5:
        return view.values().remove(v);
      case 6:
        return view.entrySet().remove(new AbstractMap.SimpleImmutableEntry<>(k, v));
      case 7:
        return view.entrySet().removeIf(e -> Objects.equals(e.getValue(), v));
      case 8: {
        Iterator<Map.Entry<Integer, Integer>> it = view.entrySet().iterator();
        if (it.hasNext()) {
          it.next();
          it.remove();
        }
        return view.size();
      }
      case 9:
        for (Map.Entry<Integer, Integer> e : view.entrySet()) {
          if (Objects.equals(e.getKey(), k)) {
            return e.setValue(v);
          }
        }
        return "none";
      case 10:
        return view.keySet().retainAll(Arrays.asList(k, kk + 1));
      case 11:
        return view.values().removeAll(Arrays.asList(v)) + "/" + view.values().size();
      case 12:
        view.clear();
        return view.isEmpty();
      case 13:
        return view.putIfAbsent(k, v) + "/" + view.getOrDefault(k, -1);
      case 14:
        if (nav != null) {
          return nav.firstEntry() + "/" + nav.lowerKey(kk) + "/" + nav.ceilingEntry(kk) + "/"
              + nav.headMap(kk, true) + "/" + nav.pollLastEntry();
        }
        return view.hashCode();
      case 15:
        return view + "/" + view.keySet() + "/" + view.values() + "/" + view.entrySet();
      case 16:
        if (nav != null) {
          NavigableSet<Integer> ks = nav.navigableKeySet();
          return ks.lower(kk) + "/" + ks.floor(kk) + "/" + ks.higher(kk) + "/" + ks.ceiling(kk)
              + "/" + ks.headSet(kk, true) + "/" + ks.tailSet(kk, false) + "/" + ks.descendingSet()
              + "/" + (ks.isEmpty() ? "-" : ks.first() + ":" + ks.last());
        }
        if (view instanceof SortedMap) {
          SortedMap<Integer, Integer> s = sorted(view);
          return s.headMap(kk) + "/" + s.tailMap(kk) + "/" + s.subMap(kk - 2, kk + 2) + "/"
              + (s.isEmpty() ? "-" : s.firstKey() + ":" + s.lastKey()) + "/" + comparatorSignature(s);
        }
        return view.keySet().contains(k) + "/" + view.keySet().size();
      case 17:
        if (nav != null) {
          NavigableSet<Integer> ks = nav.navigableKeySet();
          return ks.pollFirst() + "/" + ks.descendingSet().pollFirst();
        }
        return view.keySet().removeIf(x -> Objects.equals(x, k));
      case 18:
        if (nav != null) {
          NavigableMap<Integer, Integer> sub = nav.subMap(kk - 3, true, kk + 3, false);
          return sub + "/" + sub.descendingMap().firstEntry() + "/" + nav.floorEntry(kk) + "/"
              + nav.higherEntry(kk) + "/" + nav.lowerEntry(kk) + "/" + nav.floorKey(kk) + "/"
              + nav.higherKey(kk) + "/" + nav.ceilingKey(kk);
        }
        return view.put(k, v);
      case 19:
        if (view instanceof SortedMap) {
          String signature = comparatorSignature(sorted(view));
          if (nav != null) {
            signature += "/" + comparatorSignature(nav.descendingMap()) + "/"
                + comparatorSignature(nav.headMap(kk, true)) + "/"
                + (nav.navigableKeySet().comparator() == null ? "natural" : "keys have comparator");
          }
          return signature;
        }
        return "unsorted";
      case 20: {
        Iterator<Integer> keys = view.keySet().iterator();
        while (keys.hasNext()) {
          if (Objects.equals(keys.next(), k)) {
            keys.remove();
            return "removed";
          }
        }
        return "absent";
      }
      case 21: {
        Iterator<Integer> values = view.values().iterator();
        if (values.hasNext()) {
          values.next();
          values.remove();
        }
        return view.size();
      }
      case 22:
        return view.entrySet().retainAll(List.of(new AbstractMap.SimpleImmutableEntry<>(kk, kk)))
            + "/" + view.entrySet().contains(new AbstractMap.SimpleImmutableEntry<>(k, v));
      case 23:
        if (nav != null) {
          return nav.descendingMap().descendingMap().equals(nav) + "/" + nav.descendingKeySet() + "/"
              + nav.lastEntry() + "/" + (nav.isEmpty() ? "-" : nav.firstKey());
        }
        return view.keySet().stream().filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
      case 24:
        view.replaceAll((key, value) -> value == null ? 1 : value + 1);
        return view;
      case 25:
        return view.computeIfAbsent(k, key -> 7) + "/" + view.merge(k, 1, Integer::sum);
      case 26:
        return view.compute(k, (key, value) -> value == null ? 3 : null);
      case 27: {
        StringBuilder sb = new StringBuilder();
        view.forEach((key, value) -> sb.append(key).append(':').append(value).append(','));
        sb.append('|');
        view.keySet().forEach(key -> sb.append(key).append(','));
        sb.append('|');
        view.values().forEach(value -> sb.append(value).append(','));
        sb.append('|');
        view.entrySet().forEach(entry -> sb.append(entry).append(','));
        return sb;
      }
      case 28:
        return forcePut(view, k, v);
      case 29:
        return view.entrySet().toArray().length + "/"
            + Arrays.toString(view.keySet().toArray(new Integer[0])) + "/"
            + Arrays.toString(view.values().toArray());
      case 30:
        return view.keySet().containsAll(Arrays.asList(k)) + "/"
            + view.values().containsAll(Arrays.asList(v)) + "/"
            + view.entrySet().containsAll(List.of(new AbstractMap.SimpleImmutableEntry<>(kk, kk)));
      case 31:
        return view.keySet().addAll(Arrays.asList(k));
      case 32:
        return view.entrySet().add(new AbstractMap.SimpleImmutableEntry<>(k, v));
      case 33:
        return view.computeIfPresent(k, (key, value) -> value == null ? 5 : value * 2) + "/"
            + view.replace(k, v) + "/" + view.replace(k, v, kk) + "/" + view.remove(k, kk);
      case 34:
        if (nav != null) {
          NavigableMap<Integer, Integer> head = nav.headMap(kk, false);
          String before = head.toString();
          nav.put(kk - 1, kk);
          return before + "->" + head + "/" + head.size() + "/" + nav.tailMap(kk, true).keySet();
        }
        return view.entrySet().stream().map(String::valueOf).reduce("", String::concat);
      default: {
        Set<Map.Entry<Integer, Integer>> entries = view.entrySet();
        return view.values().removeIf(x -> Objects.equals(x, v)) + "/"
            + view.keySet().removeAll(Arrays.asList(k)) + "/"
            + view.values().retainAll(Arrays.asList(v, kk)) + "/" + entries.size();
      }
    }
  }

  /** A mutable sorted map that is deliberately not a {@code NavigableMap}. */
  private static final class SortedOnlyMap<K, V> extends AbstractMap<K, V>
      implements SortedMap<K, V> {
    private final SortedMap<K, V> delegate;

    SortedOnlyMap(SortedMap<K, V> delegate) {
      this.delegate = delegate;
    }

    @Override
    public Comparator<? super K> comparator() {
      return delegate.comparator();
    }

    @Override
    public SortedMap<K, V> subMap(K fromKey, K toKey) {
      return new SortedOnlyMap<>(delegate.subMap(fromKey, toKey));
    }

    @Override
    public SortedMap<K, V> headMap(K toKey) {
      return new SortedOnlyMap<>(delegate.headMap(toKey));
    }

    @Override
    public SortedMap<K, V> tailMap(K fromKey) {
      return new SortedOnlyMap<>(delegate.tailMap(fromKey));
    }

    @Override
    public K firstKey() {
      return delegate.firstKey();
    }

    @Override
    public K lastKey() {
      return delegate.lastKey();
    }

    @Override
    public V put(K key, V value) {
      return delegate.put(key, value);
    }

    @Override
    public V get(Object key) {
      return delegate.get(key);
    }

    @Override
    public V remove(Object key) {
      return delegate.remove(key);
    }

    @Override
    public boolean containsKey(Object key) {
      return delegate.containsKey(key);
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
      return delegate.entrySet();
    }
  }
}
