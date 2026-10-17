package com.simplelibs.lava.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.function.Function;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Differential tests for the live views returned by {@link Maps}: identical random operation
 * sequences are applied through Lava's view and through the real Guava 27.0.1 view, each over its
 * own equal backing map. After every operation the results (or exception class and message), the
 * view and the backing map must agree.
 */
class MapsViewDifferentialTest {
  private static final int ROUNDS = 150;
  private static final int OPS = 25;

  /** A pair of view factories: Lava's and Guava's, applied to a fresh backing map. */
  private interface ViewFactory {
    Map<Integer, Integer> lava(Map<Integer, Integer> backing);

    Map<Integer, Integer> guava(Map<Integer, Integer> backing);
  }

  private enum Backing {
    HASH,
    TREE,
    BIMAP
  }

  private static Map<Integer, Integer> newBacking(Backing kind, boolean guava) {
    switch (kind) {
      case TREE:
        return new TreeMap<>();
      case BIMAP:
        return guava ? com.google.common.collect.HashBiMap.create() : HashBiMap.create();
      default:
        return new LinkedHashMap<>();
    }
  }

  private static final java.util.function.Predicate<Integer> EVEN = k -> k % 2 == 0;
  private static final com.google.common.base.Predicate<Integer> G_EVEN = k -> k % 2 == 0;
  private static final java.util.function.Predicate<Entry<Integer, Integer>> SUM_SMALL =
      e -> e.getKey() + e.getValue() < 12;
  private static final com.google.common.base.Predicate<Entry<Integer, Integer>> G_SUM_SMALL =
      e -> e.getKey() + e.getValue() < 12;

  private static Function<Integer, Integer> TIMES_TEN = v -> v * 10;
  private static com.google.common.base.Function<Integer, Integer> G_TIMES_TEN = v -> v * 10;

  @TestFactory
  List<DynamicTest> viewsBehaveLikeGuava() {
    List<DynamicTest> tests = new ArrayList<>();
    add(tests, "filterKeys(Map)", Backing.HASH, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.filterKeys(b, EVEN); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.filterKeys(b, G_EVEN); }
    });
    add(tests, "filterValues(Map)", Backing.HASH, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.filterValues(b, EVEN); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.filterValues(b, G_EVEN); }
    });
    add(tests, "filterEntries(Map)", Backing.HASH, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.filterEntries(b, SUM_SMALL); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.filterEntries(b, G_SUM_SMALL); }
    });
    add(tests, "filterKeys(filterValues(Map))", Backing.HASH, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.filterKeys(Maps.filterValues(b, EVEN), k -> k > 2); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.filterKeys(com.google.common.collect.Maps.filterValues(b, G_EVEN), k -> k > 2); }
    });
    add(tests, "filterKeys(SortedMap)", Backing.TREE, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.filterKeys((SortedMap<Integer, Integer>) b, EVEN); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.filterKeys((SortedMap<Integer, Integer>) b, G_EVEN); }
    });
    add(tests, "filterEntries(NavigableMap)", Backing.TREE, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.filterEntries((NavigableMap<Integer, Integer>) b, SUM_SMALL); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.filterEntries((NavigableMap<Integer, Integer>) b, G_SUM_SMALL); }
    });
    add(tests, "filterValues(NavigableMap).descendingMap()", Backing.TREE, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.filterValues((NavigableMap<Integer, Integer>) b, EVEN).descendingMap(); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.filterValues((NavigableMap<Integer, Integer>) b, G_EVEN).descendingMap(); }
    });
    add(tests, "filterKeys(BiMap)", Backing.BIMAP, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.filterKeys((BiMap<Integer, Integer>) b, EVEN); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.filterKeys((com.google.common.collect.BiMap<Integer, Integer>) b, G_EVEN); }
    });
    add(tests, "filterEntries(BiMap).inverse()", Backing.BIMAP, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.filterEntries((BiMap<Integer, Integer>) b, SUM_SMALL).inverse(); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.filterEntries((com.google.common.collect.BiMap<Integer, Integer>) b, G_SUM_SMALL).inverse(); }
    });
    add(tests, "transformValues(Map)", Backing.HASH, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.transformValues(b, TIMES_TEN); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.transformValues(b, G_TIMES_TEN); }
    });
    add(tests, "transformEntries(NavigableMap)", Backing.TREE, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.transformEntries((NavigableMap<Integer, Integer>) b, (k, v) -> k * 100 + v); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.transformEntries((NavigableMap<Integer, Integer>) b, (k, v) -> k * 100 + v); }
    });
    add(tests, "transformValues(SortedMap).headMap(5)", Backing.TREE, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.transformValues((SortedMap<Integer, Integer>) b, TIMES_TEN).headMap(5); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.transformValues((SortedMap<Integer, Integer>) b, G_TIMES_TEN).headMap(5); }
    });
    add(tests, "unmodifiableNavigableMap", Backing.TREE, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.unmodifiableNavigableMap((NavigableMap<Integer, Integer>) b); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.unmodifiableNavigableMap((NavigableMap<Integer, Integer>) b); }
    });
    add(tests, "unmodifiableBiMap", Backing.BIMAP, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.unmodifiableBiMap((BiMap<Integer, Integer>) b); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.unmodifiableBiMap((com.google.common.collect.BiMap<Integer, Integer>) b); }
    });
    add(tests, "synchronizedBiMap.inverse()", Backing.BIMAP, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.synchronizedBiMap((BiMap<Integer, Integer>) b).inverse(); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.synchronizedBiMap((com.google.common.collect.BiMap<Integer, Integer>) b).inverse(); }
    });
    add(tests, "synchronizedNavigableMap", Backing.TREE, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.synchronizedNavigableMap((NavigableMap<Integer, Integer>) b); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.synchronizedNavigableMap((NavigableMap<Integer, Integer>) b); }
    });
    add(tests, "subMap(closedOpen(2, 7))", Backing.TREE, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.subMap((NavigableMap<Integer, Integer>) b, Range.closedOpen(2, 7)); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.subMap((NavigableMap<Integer, Integer>) b, com.google.common.collect.Range.closedOpen(2, 7)); }
    });
    add(tests, "subMap(atLeast(4)).descendingMap()", Backing.TREE, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.subMap((NavigableMap<Integer, Integer>) b, Range.atLeast(4)).descendingMap(); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.subMap((NavigableMap<Integer, Integer>) b, com.google.common.collect.Range.atLeast(4)).descendingMap(); }
    });
    add(tests, "asMap(Set)", Backing.HASH, new ViewFactory() {
      // asMap is keyed by a backing *set*; use the backing map's key set.
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.asMap(b.keySet(), k -> k * 3); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.asMap(b.keySet(), k -> k * 3); }
    });
    add(tests, "asMap(NavigableSet)", Backing.TREE, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.asMap(((NavigableMap<Integer, Integer>) b).navigableKeySet(), k -> k * 3); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.asMap(((NavigableMap<Integer, Integer>) b).navigableKeySet(), k -> k * 3); }
    });
    add(tests, "filterKeys(TreeMap typed as Map)", Backing.TREE, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.filterKeys(b, EVEN); } // a TreeMap, typed as Map
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.filterKeys(b, G_EVEN); }
    });
    add(tests, "filterKeys(filterKeys(TreeMap typed as Map))", Backing.TREE, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.filterKeys(Maps.filterKeys(b, EVEN), k -> k < 7); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.filterKeys(com.google.common.collect.Maps.filterKeys(b, G_EVEN), k -> k < 7); }
    });
    add(tests, "filterEntries(filterValues(NavigableMap))", Backing.TREE, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.filterEntries(Maps.filterValues((NavigableMap<Integer, Integer>) b, EVEN), SUM_SMALL); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.filterEntries(com.google.common.collect.Maps.filterValues((NavigableMap<Integer, Integer>) b, G_EVEN), G_SUM_SMALL); }
    });
    add(tests, "filterEntries(filterKeys(SortedMap))", Backing.TREE, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.filterEntries(Maps.filterKeys((SortedMap<Integer, Integer>) b, EVEN), SUM_SMALL); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.filterEntries(com.google.common.collect.Maps.filterKeys((SortedMap<Integer, Integer>) b, G_EVEN), G_SUM_SMALL); }
    });
    add(tests, "filterValues(filterKeys(BiMap))", Backing.BIMAP, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.filterValues(Maps.filterKeys((BiMap<Integer, Integer>) b, EVEN), v -> v > 1); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.filterValues(com.google.common.collect.Maps.filterKeys((com.google.common.collect.BiMap<Integer, Integer>) b, G_EVEN), v -> v > 1); }
    });
    add(tests, "unmodifiableBiMap.inverse().entrySet view", Backing.BIMAP, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.unmodifiableBiMap((BiMap<Integer, Integer>) b).inverse(); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.unmodifiableBiMap((com.google.common.collect.BiMap<Integer, Integer>) b).inverse(); }
    });
    add(tests, "filterKeys(NavigableMap)", Backing.TREE, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.filterKeys((NavigableMap<Integer, Integer>) b, EVEN); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.filterKeys((NavigableMap<Integer, Integer>) b, G_EVEN); }
    });
    add(tests, "filterValues(SortedMap).tailMap(3)", Backing.TREE, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.filterValues((SortedMap<Integer, Integer>) b, EVEN).tailMap(3); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.filterValues((SortedMap<Integer, Integer>) b, G_EVEN).tailMap(3); }
    });
    add(tests, "filterEntries(SortedMap)", Backing.TREE, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.filterEntries((SortedMap<Integer, Integer>) b, SUM_SMALL); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.filterEntries((SortedMap<Integer, Integer>) b, G_SUM_SMALL); }
    });
    add(tests, "filterValues(BiMap)", Backing.BIMAP, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.filterValues((BiMap<Integer, Integer>) b, EVEN); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.filterValues((com.google.common.collect.BiMap<Integer, Integer>) b, G_EVEN); }
    });
    add(tests, "transformValues(NavigableMap)", Backing.TREE, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.transformValues((NavigableMap<Integer, Integer>) b, TIMES_TEN); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.transformValues((NavigableMap<Integer, Integer>) b, G_TIMES_TEN); }
    });
    add(tests, "transformEntries(SortedMap)", Backing.TREE, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.transformEntries((SortedMap<Integer, Integer>) b, (k, v) -> k - v); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.transformEntries((SortedMap<Integer, Integer>) b, (k, v) -> k - v); }
    });
    add(tests, "transformEntries(Map)", Backing.HASH, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.transformEntries(b, (k, v) -> k - v); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.transformEntries(b, (k, v) -> k - v); }
    });
    add(tests, "asMap(SortedSet)", Backing.TREE, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.asMap((java.util.SortedSet<Integer>) ((NavigableMap<Integer, Integer>) b).navigableKeySet(), k -> k + 7); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.asMap((java.util.SortedSet<Integer>) ((NavigableMap<Integer, Integer>) b).navigableKeySet(), k -> k + 7); }
    });
    add(tests, "asMap(NavigableSet).descendingMap()", Backing.TREE, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.asMap(((NavigableMap<Integer, Integer>) b).navigableKeySet(), k -> k * 3).descendingMap(); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.asMap(((NavigableMap<Integer, Integer>) b).navigableKeySet(), k -> k * 3).descendingMap(); }
    });
    add(tests, "unmodifiableNavigableMap.descendingMap()", Backing.TREE, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.unmodifiableNavigableMap((NavigableMap<Integer, Integer>) b).descendingMap(); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.unmodifiableNavigableMap((NavigableMap<Integer, Integer>) b).descendingMap(); }
    });
    add(tests, "subMap(openClosed(1, 8))", Backing.TREE, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.subMap((NavigableMap<Integer, Integer>) b, Range.openClosed(1, 8)); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.subMap((NavigableMap<Integer, Integer>) b, com.google.common.collect.Range.openClosed(1, 8)); }
    });
    add(tests, "subMap(lessThan(6))", Backing.TREE, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.subMap((NavigableMap<Integer, Integer>) b, Range.lessThan(6)); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.subMap((NavigableMap<Integer, Integer>) b, com.google.common.collect.Range.lessThan(6)); }
    });
    add(tests, "subMap(all())", Backing.TREE, new ViewFactory() {
      public Map<Integer, Integer> lava(Map<Integer, Integer> b) { return Maps.subMap((NavigableMap<Integer, Integer>) b, Range.all()); }
      public Map<Integer, Integer> guava(Map<Integer, Integer> b) { return com.google.common.collect.Maps.subMap((NavigableMap<Integer, Integer>) b, com.google.common.collect.Range.all()); }
    });
    return tests;
  }

  private static void add(List<DynamicTest> tests, String name, Backing kind, ViewFactory factory) {
    tests.add(dynamicTest(name, () -> run(name, kind, factory)));
  }

  private static void run(String name, Backing kind, ViewFactory factory) {
    Random random = new Random(name.hashCode());
    for (int round = 0; round < ROUNDS; round++) {
      Map<Integer, Integer> lBacking = newBacking(kind, false);
      Map<Integer, Integer> gBacking = newBacking(kind, true);
      int initial = random.nextInt(8);
      for (int i = 0; i < initial; i++) {
        int k = random.nextInt(10);
        int v = random.nextInt(10);
        try {
          lBacking.put(k, v);
          gBacking.put(k, v);
        } catch (IllegalArgumentException duplicateBiMapValue) {
          // BiMap rejects duplicate values in both; keep them in sync.
        }
      }
      assertThat(lBacking).isEqualTo(gBacking);
      Map<Integer, Integer> lView = factory.lava(lBacking);
      Map<Integer, Integer> gView = factory.guava(gBacking);
      for (int op = 0; op < OPS; op++) {
        int k = random.nextInt(10);
        int v = random.nextInt(10);
        int choice = random.nextInt(26);
        String what = name + " round " + round + " op " + op + " #" + choice + "(" + k + "," + v + ")";
        same(what, () -> apply(choice, gView, k, v), () -> apply(choice, lView, k, v));
        assertThat(lView).as(what + " view").isEqualTo(gView);
        assertThat(new ArrayList<>(lView.entrySet()).toString())
            .as(what + " iteration order")
            .isEqualTo(new ArrayList<>(gView.entrySet()).toString());
        assertThat(lBacking).as(what + " backing").isEqualTo(gBacking);
      }
    }
  }

  /** Applies operation {@code choice} to a view; the result is rendered as a String. */
  private static Object apply(int choice, Map<Integer, Integer> view, int k, int v) {
    switch (choice) {
      case 0:
        return view.put(k, v);
      case 1:
        return view.remove(k);
      case 2:
        return view.get(k) + "/" + view.containsKey(k) + "/" + view.containsValue(v);
      case 3:
        Map<Integer, Integer> more = new LinkedHashMap<>();
        more.put(k, v);
        more.put(k + 1, v + 1);
        view.putAll(more);
        return view.size();
      case 4:
        return view.keySet().remove(k);
      case 5:
        return view.values().remove(v);
      case 6:
        return view.entrySet().remove(Maps.immutableEntry(k, v));
      case 7:
        return view.entrySet().removeIf(e -> e.getValue() == v);
      case 8:
        Iterator<Entry<Integer, Integer>> it = view.entrySet().iterator();
        if (it.hasNext()) {
          it.next();
          it.remove();
        }
        return view.size();
      case 9:
        for (Entry<Integer, Integer> e : view.entrySet()) {
          if (e.getKey() == k) {
            return e.setValue(v);
          }
        }
        return "none";
      case 10:
        return view.keySet().retainAll(List.of(k, k + 1, k + 2));
      case 11:
        Collection<Integer> values = view.values();
        return values.removeAll(List.of(v)) + "/" + values.size();
      case 12:
        view.clear();
        return view.isEmpty();
      case 13:
        return view.putIfAbsent(k, v) + "/" + view.getOrDefault(k + 1, -1);
      case 14:
        if (view instanceof NavigableMap) {
          NavigableMap<Integer, Integer> nav = (NavigableMap<Integer, Integer>) view;
          return nav.firstEntry() + "/" + nav.lowerKey(k) + "/" + nav.ceilingEntry(k) + "/"
              + nav.headMap(k, true) + "/" + nav.pollLastEntry();
        }
        if (view instanceof SortedMap) {
          SortedMap<Integer, Integer> sorted = (SortedMap<Integer, Integer>) view;
          return sorted.headMap(k) + "/" + sorted.tailMap(k);
        }
        return view.hashCode();
      case 15:
        return view.toString() + "/" + view.keySet() + "/" + view.values();
      case 16:
        if (view.keySet() instanceof java.util.NavigableSet) {
          java.util.NavigableSet<Integer> keys = (java.util.NavigableSet<Integer>) view.keySet();
          return keys.lower(k) + "/" + keys.floor(k) + "/" + keys.higher(k) + "/" + keys.ceiling(k)
              + "/" + keys.headSet(k, true) + "/" + keys.tailSet(k, false) + "/"
              + keys.subSet(k - 2, true, k + 2, false) + "/" + keys.descendingSet() + "/"
              + (keys.isEmpty() ? "-" : keys.first() + ":" + keys.last());
        }
        if (view.keySet() instanceof java.util.SortedSet) {
          java.util.SortedSet<Integer> keys = (java.util.SortedSet<Integer>) view.keySet();
          return keys.headSet(k) + "/" + keys.tailSet(k) + "/" + keys.subSet(k - 2, k + 2)
              + "/" + (keys.isEmpty() ? "-" : keys.first() + ":" + keys.last());
        }
        return view.keySet().contains(k) + "/" + view.keySet().size();
      case 17:
        if (view.keySet() instanceof java.util.NavigableSet) {
          java.util.NavigableSet<Integer> keys = (java.util.NavigableSet<Integer>) view.keySet();
          return keys.pollFirst() + "/" + keys.descendingSet().pollFirst() + "/"
              + keys.headSet(k, false).remove(k - 1);
        }
        return view.keySet().removeIf(x -> x == k);
      case 18:
        if (view instanceof NavigableMap) {
          NavigableMap<Integer, Integer> nav = (NavigableMap<Integer, Integer>) view;
          NavigableMap<Integer, Integer> sub = nav.subMap(k - 3, true, k + 3, false);
          return sub + "/" + sub.put(k, v) + "/" + sub.descendingMap().firstEntry() + "/"
              + nav.floorEntry(k) + "/" + nav.higherEntry(k) + "/" + nav.lowerEntry(k) + "/"
              + nav.navigableKeySet().descendingIterator().hasNext();
        }
        return view.put(k, v);
      case 19:
        if (view instanceof NavigableMap) {
          NavigableMap<Integer, Integer> nav = (NavigableMap<Integer, Integer>) view;
          nav.tailMap(k, true).keySet().removeIf(x -> x % 3 == 0);
          nav.headMap(k).values().removeIf(x -> x == v);
          return nav.pollFirstEntry() + "/" + nav.descendingKeySet();
        }
        return view.values().removeIf(x -> x == v);
      case 20:
        Iterator<Integer> keys = view.keySet().iterator();
        while (keys.hasNext()) {
          if (keys.next() == k) {
            keys.remove();
            return "removed";
          }
        }
        return "absent";
      case 21:
        Iterator<Integer> valuesIt = view.values().iterator();
        if (valuesIt.hasNext()) {
          valuesIt.next();
          valuesIt.remove();
        }
        return view.size();
      case 22:
        return view.entrySet().retainAll(List.of(Maps.immutableEntry(k, v)))
            + "/" + view.entrySet().contains(Maps.immutableEntry(k, v))
            + "/" + view.values().contains(v) + "/" + view.keySet().containsAll(List.of(k));
      case 23:
        if (view instanceof NavigableMap) {
          NavigableMap<Integer, Integer> nav = (NavigableMap<Integer, Integer>) view;
          String s =
              nav.floorKey(k) + "/" + nav.ceilingKey(k) + "/" + nav.higherKey(k) + "/"
                  + nav.lowerKey(k) + "/" + nav.ceilingEntry(k) + "/" + nav.lastEntry() + "/"
                  + nav.firstEntry() + "/" + nav.headMap(k) + "/" + nav.tailMap(k) + "/"
                  + nav.subMap(k - 2, k + 3) + "/" + nav.descendingKeySet().headSet(k, true) + "/"
                  + nav.navigableKeySet().tailSet(k) + "/" + nav.descendingMap().tailMap(k, true);
          if (!nav.isEmpty()) {
            s += "/" + nav.firstKey() + ":" + nav.lastKey();
          }
          return s;
        }
        if (view instanceof SortedMap) {
          SortedMap<Integer, Integer> sorted = (SortedMap<Integer, Integer>) view;
          String s = sorted.subMap(k - 2, k + 3) + "/" + sorted.comparator();
          return sorted.isEmpty() ? s : s + "/" + sorted.firstKey() + ":" + sorted.lastKey();
        }
        return view.equals(new java.util.HashMap<>(view)) + "/" + view.hashCode();
      case 24:
        if (view.keySet() instanceof java.util.NavigableSet) {
          java.util.NavigableSet<Integer> navKeys = (java.util.NavigableSet<Integer>) view.keySet();
          java.util.NavigableSet<Integer> desc = navKeys.descendingSet();
          return desc.headSet(k) + "/" + desc.tailSet(k, true) + "/" + desc.subSet(k + 2, k - 2)
              + "/" + desc.lower(k) + "/" + desc.higher(k) + "/" + navKeys.pollLast() + "/"
              + java.util.Arrays.toString(desc.toArray()) + "/" + desc.descendingSet();
        }
        if (view.keySet() instanceof java.util.SortedSet) {
          java.util.SortedSet<Integer> sortedKeys = (java.util.SortedSet<Integer>) view.keySet();
          return sortedKeys.subSet(k - 1, k + 3).size() + "/" + sortedKeys.comparator();
        }
        return view.keySet().stream().mapToInt(Integer::intValue).sum();
      default:
        return java.util.Arrays.toString(view.keySet().toArray()) + "/"
            + java.util.Arrays.toString(view.values().toArray(new Integer[0])) + "/"
            + view.entrySet().size() + "/" + view.entrySet().hashCode();
    }
  }

  /** Compares String-rendered results, or exception class and message if either side throws. */
  private static void same(String what, Callable<Object> guava, Callable<Object> lava) {
    String g;
    String l;
    try {
      g = String.valueOf(guava.call());
    } catch (Exception e) {
      g = e.getClass().getName() + ": " + e.getMessage();
    }
    try {
      l = String.valueOf(lava.call());
    } catch (Exception e) {
      l = e.getClass().getName() + ": " + e.getMessage();
    }
    assertThat(l).as(what).isEqualTo(g);
  }
}
