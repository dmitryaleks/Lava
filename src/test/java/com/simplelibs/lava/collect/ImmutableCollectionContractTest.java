package com.simplelibs.lava.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.simplelibs.lava.collect.testing.MapInterfaceTest;
import com.simplelibs.lava.testing.CollectionContract;
import com.simplelibs.lava.testing.ListContract;
import com.simplelibs.lava.testing.SerializableTester;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestFactory;

/**
 * Lava replacement for the guava-testlib collection suites that {@code ImmutableListTest}, {@code
 * ImmutableSetTest} and {@code ImmutableMapTest} build: runs the hand-written List, Set and
 * Collection contracts over every immutable implementation and view.
 */
class ImmutableCollectionContractTest {

  private static final List<String> ELEMENTS =
      List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n");

  private static List<String> first(int n) {
    return ELEMENTS.subList(0, n);
  }

  /** {@code ImmutableList.of} overloads for every arity up to the varargs one. */
  private static ImmutableList<String> listOf(int n) {
    String[] e = ELEMENTS.toArray(new String[0]);
    switch (n) {
      case 0:
        return ImmutableList.of();
      case 1:
        return ImmutableList.of(e[0]);
      case 2:
        return ImmutableList.of(e[0], e[1]);
      case 3:
        return ImmutableList.of(e[0], e[1], e[2]);
      case 4:
        return ImmutableList.of(e[0], e[1], e[2], e[3]);
      case 5:
        return ImmutableList.of(e[0], e[1], e[2], e[3], e[4]);
      case 6:
        return ImmutableList.of(e[0], e[1], e[2], e[3], e[4], e[5]);
      case 7:
        return ImmutableList.of(e[0], e[1], e[2], e[3], e[4], e[5], e[6]);
      case 8:
        return ImmutableList.of(e[0], e[1], e[2], e[3], e[4], e[5], e[6], e[7]);
      case 9:
        return ImmutableList.of(e[0], e[1], e[2], e[3], e[4], e[5], e[6], e[7], e[8]);
      case 10:
        return ImmutableList.of(e[0], e[1], e[2], e[3], e[4], e[5], e[6], e[7], e[8], e[9]);
      case 11:
        return ImmutableList.of(e[0], e[1], e[2], e[3], e[4], e[5], e[6], e[7], e[8], e[9], e[10]);
      default:
        return ImmutableList.of(
            e[0], e[1], e[2], e[3], e[4], e[5], e[6], e[7], e[8], e[9], e[10], e[11],
            Arrays.copyOfRange(e, 12, n));
    }
  }

  /** {@code ImmutableSet.of} overloads for every arity up to the varargs one. */
  private static ImmutableSet<String> setOf(int n) {
    String[] e = ELEMENTS.toArray(new String[0]);
    switch (n) {
      case 0:
        return ImmutableSet.of();
      case 1:
        return ImmutableSet.of(e[0]);
      case 2:
        return ImmutableSet.of(e[0], e[1]);
      case 3:
        return ImmutableSet.of(e[0], e[1], e[2]);
      case 4:
        return ImmutableSet.of(e[0], e[1], e[2], e[3]);
      case 5:
        return ImmutableSet.of(e[0], e[1], e[2], e[3], e[4]);
      default:
        return ImmutableSet.of(e[0], e[1], e[2], e[3], e[4], e[5], Arrays.copyOfRange(e, 6, n));
    }
  }

  private static ImmutableMap<String, Integer> mapOf(int n) {
    ImmutableMap.Builder<String, Integer> builder = ImmutableMap.builder();
    for (int i = 0; i < n; i++) {
      builder.put(ELEMENTS.get(i), i);
    }
    return builder.build();
  }

  private static void listCase(
      List<DynamicTest> tests, String name, Supplier<List<String>> factory, List<String> expected) {
    tests.add(
        dynamicTest(
            name,
            () -> {
              List<String> list = factory.get();
              assertThat(list).isInstanceOf(ImmutableList.class);
              ListContract.assertReadContract(list, expected, "absent");
              ListContract.assertAlwaysThrowsUnsupported(list, "z");
              List<String> copy = SerializableTester.reserialize(list);
              ListContract.assertReadContract(copy, expected, "absent");
              assertThat(copy).isInstanceOf(ImmutableList.class);
            }));
  }

  private static <E> void collectionCase(
      List<DynamicTest> tests,
      String name,
      Supplier<? extends Collection<E>> factory,
      Collection<E> expected,
      E sample) {
    tests.add(
        dynamicTest(
            name,
            () -> {
              Collection<E> collection = factory.get();
              CollectionContract.assertReadContract(collection, expected, true);
              CollectionContract.assertAlwaysThrowsUnsupported(collection, sample);
              Collection<E> copy = SerializableTester.reserialize(collection);
              CollectionContract.assertReadContract(copy, expected, true);
              if (collection instanceof ImmutableCollection) {
                ImmutableList<E> asList = ((ImmutableCollection<E>) collection).asList();
                ListContract.assertReadContract(asList, new ArrayList<>(expected));
                ListContract.assertAlwaysThrowsUnsupported(asList, sample);
                ListContract.assertReadContract(
                    SerializableTester.reserialize(asList), new ArrayList<>(expected));
              }
            }));
  }

  @TestFactory
  List<DynamicTest> immutableLists() {
    List<DynamicTest> tests = new ArrayList<>();
    for (int n : new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 14}) {
      List<String> expected = first(n);
      listCase(tests, "of(" + n + ")", () -> listOf(n), expected);
      listCase(tests, "copyOf(array, " + n + ")", () -> ImmutableList.copyOf(expected.toArray(new String[0])), expected);
      listCase(tests, "copyOf(collection, " + n + ")", () -> ImmutableList.copyOf(new ArrayList<>(expected)), expected);
      listCase(tests, "copyOf(iterable, " + n + ")", () -> ImmutableList.copyOf((Iterable<String>) expected::iterator), expected);
      listCase(tests, "copyOf(iterator, " + n + ")", () -> ImmutableList.copyOf(expected.iterator()), expected);
      listCase(
          tests,
          "builder(" + n + ")",
          () -> ImmutableList.<String>builder().addAll(expected).build(),
          expected);
      listCase(
          tests,
          "builderWithExpectedSize(" + n + ")",
          () -> {
            ImmutableList.Builder<String> builder = ImmutableList.builderWithExpectedSize(n);
            expected.forEach(builder::add);
            return builder.build();
          },
          expected);
      List<String> reversed = new ArrayList<>(expected);
      java.util.Collections.reverse(reversed);
      listCase(tests, "reverse(" + n + ")", () -> listOf(n).reverse(), reversed);
      listCase(tests, "reverse().reverse(" + n + ")", () -> listOf(n).reverse().reverse(), expected);
      listCase(tests, "sortedCopyOf(" + n + ")", () -> ImmutableList.sortedCopyOf(reversed), expected);
      if (n >= 2) {
        List<String> middle = expected.subList(1, n - 1);
        listCase(tests, "subList(" + n + ")", () -> listOf(n).subList(1, n - 1), middle);
        listCase(tests, "copyOf(subList(" + n + "))", () -> ImmutableList.copyOf(listOf(n).subList(1, n - 1)), middle);
      }
      listCase(tests, "ImmutableSet.asList(" + n + ")", () -> setOf(n).asList(), expected);
      listCase(tests, "keySet().asList(" + n + ")", () -> mapOf(n).keySet().asList(), expected);
      listCase(tests, "toImmutableList(" + n + ")", () -> expected.stream().collect(ImmutableList.toImmutableList()), expected);
    }
    return tests;
  }

  /** Lists with duplicate elements, so indexOf and lastIndexOf differ. */
  @TestFactory
  List<DynamicTest> immutableListsWithDuplicates() {
    List<DynamicTest> tests = new ArrayList<>();
    for (List<String> expected :
        List.of(
            List.of("a", "a"),
            List.of("a", "b", "a"),
            List.of("a", "b", "a", "c", "b", "a"),
            List.of("x", "y", "x", "y", "x", "y", "x", "y", "x", "y", "x", "y", "x"))) {
      listCase(tests, "copyOf(dups " + expected + ")", () -> ImmutableList.copyOf(expected), expected);
      List<String> reversed = new ArrayList<>(expected);
      java.util.Collections.reverse(reversed);
      listCase(tests, "reverse(dups " + expected + ")", () -> ImmutableList.copyOf(expected).reverse(), reversed);
      listCase(
          tests,
          "subList(dups " + expected + ")",
          () -> ImmutableList.copyOf(expected).subList(0, expected.size() - 1),
          expected.subList(0, expected.size() - 1));
      listCase(
          tests,
          "builder(dups " + expected + ")",
          () -> ImmutableList.<String>builder().addAll(expected).build(),
          expected);
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> immutableSetsAndViews() {
    List<DynamicTest> tests = new ArrayList<>();
    for (int n : new int[] {0, 1, 2, 3, 5, 6, 9, 14}) {
      List<String> expected = first(n);
      collectionCase(tests, "ImmutableSet.of(" + n + ")", () -> setOf(n), expected, "z");
      collectionCase(tests, "ImmutableSet.copyOf(array, " + n + ")", () -> ImmutableSet.copyOf(expected.toArray(new String[0])), expected, "z");
      collectionCase(tests, "ImmutableSet.copyOf(duplicates, " + n + ")", () -> {
        List<String> doubled = new ArrayList<>(expected);
        doubled.addAll(expected);
        return ImmutableSet.copyOf(doubled);
      }, expected, "z");
      collectionCase(tests, "ImmutableSet.copyOf(iterator, " + n + ")", () -> ImmutableSet.copyOf(expected.iterator()), expected, "z");
      collectionCase(tests, "ImmutableSet.builder(" + n + ")", () -> ImmutableSet.<String>builder().addAll(expected).addAll(expected).build(), expected, "z");
      collectionCase(tests, "toImmutableSet(" + n + ")", () -> expected.stream().collect(ImmutableSet.toImmutableSet()), expected, "z");
      collectionCase(tests, "ImmutableMap.keySet(" + n + ")", () -> mapOf(n).keySet(), expected, "z");
      List<Integer> values = new ArrayList<>();
      List<Entry<String, Integer>> entries = new ArrayList<>();
      for (int i = 0; i < n; i++) {
        values.add(i);
        entries.add(Maps.immutableEntry(ELEMENTS.get(i), i));
      }
      collectionCase(tests, "ImmutableMap.values(" + n + ")", () -> mapOf(n).values(), values, 99);
      collectionCase(tests, "ImmutableMap.entrySet(" + n + ")", () -> mapOf(n).entrySet(), entries, Maps.immutableEntry("z", 99));
      collectionCase(tests, "ImmutableSetMultimap.keySet(" + n + ")", () -> multimapOf(n).keySet(), expected, "z");
      collectionCase(tests, "ImmutableSetMultimap.entries(" + n + ")", () -> multimapOf(n).entries(), entries, Maps.immutableEntry("z", 99));
      collectionCase(tests, "ImmutableSetMultimap.values(" + n + ")", () -> multimapOf(n).values(), values, 99);
      collectionCase(tests, "asMultimap().entries(" + n + ")", () -> mapOf(n).asMultimap().entries(), entries, Maps.immutableEntry("z", 99));
    }
    for (int n : new int[] {0, 1, 2, 4}) {
      EnumSet<Thread.State> source = EnumSet.noneOf(Thread.State.class);
      source.addAll(Arrays.asList(Thread.State.values()).subList(0, n));
      collectionCase(tests, "ImmutableSet.copyOf(EnumSet, " + n + ")", () -> ImmutableSet.copyOf(source), new ArrayList<>(source), Thread.State.TERMINATED);
    }
    List<String> colliding = collidingStrings(5); // 32 strings with one hash code
    collectionCase(tests, "ImmutableSet.copyOf(hash flooding)", () -> ImmutableSet.copyOf(colliding), colliding, "z");
    tests.add(
        dynamicTest(
            "hash flooding falls back to JdkBackedImmutableSet exactly when Guava's does",
            () -> {
              for (int power = 3; power <= 11; power++) {
                List<String> strings = collidingStrings(power);
                assertThat(ImmutableSet.copyOf(strings).getClass().getSimpleName())
                    .as("power " + power)
                    .isEqualTo(com.google.common.collect.ImmutableSet.copyOf(strings).getClass().getSimpleName());
              }
              assertThat(ImmutableSet.copyOf(collidingStrings(11))).isInstanceOf(JdkBackedImmutableSet.class);
            }));

    // Views of the map implementations that RegularImmutableMap does not cover.
    Map<String, Integer> flooded = new LinkedHashMap<>();
    for (int i = 0; i < colliding.size(); i++) {
      flooded.put(colliding.get(i), i);
    }
    Supplier<ImmutableMap<String, Integer>> jdkBacked = () -> ImmutableMap.copyOf(flooded);
    collectionCase(tests, "JdkBackedImmutableMap.keySet", () -> jdkBacked.get().keySet(), new ArrayList<>(flooded.keySet()), "z");
    collectionCase(tests, "JdkBackedImmutableMap.values", () -> jdkBacked.get().values(), new ArrayList<>(flooded.values()), -1);
    collectionCase(tests, "JdkBackedImmutableMap.entrySet", () -> jdkBacked.get().entrySet(), new ArrayList<>(flooded.entrySet()), Maps.immutableEntry("z", -1));
    EnumMap<Thread.State, Integer> enumSource = new EnumMap<>(Thread.State.class);
    enumSource.put(Thread.State.NEW, 1);
    enumSource.put(Thread.State.BLOCKED, 3);
    enumSource.put(Thread.State.WAITING, 4);
    Supplier<ImmutableMap<Thread.State, Integer>> enumBacked = () -> ImmutableMap.copyOf(enumSource);
    collectionCase(tests, "ImmutableEnumMap.keySet", () -> enumBacked.get().keySet(), new ArrayList<>(enumSource.keySet()), Thread.State.TERMINATED);
    collectionCase(tests, "ImmutableEnumMap.values", () -> enumBacked.get().values(), new ArrayList<>(enumSource.values()), -1);
    collectionCase(tests, "ImmutableEnumMap.entrySet", () -> enumBacked.get().entrySet(), new ArrayList<>(enumSource.entrySet()), Maps.immutableEntry(Thread.State.TERMINATED, -1));
    tests.add(
        dynamicTest(
            "map and multimap forEach",
            () -> {
              for (ImmutableMap<?, ?> map :
                  List.of(jdkBacked.get(), enumBacked.get(), mapOf(4), ImmutableMap.of())) {
                List<Object> keys = new ArrayList<>();
                List<Object> values = new ArrayList<>();
                map.forEach((k, v) -> {
                  keys.add(k);
                  values.add(v);
                });
                assertThat(keys).containsExactlyElementsOf(map.keySet());
                assertThat(values).containsExactlyElementsOf(map.values());
                List<Object> viaKeySet = new ArrayList<>();
                map.keySet().forEach(viaKeySet::add);
                assertThat(viaKeySet).containsExactlyElementsOf(map.keySet());
                List<Object> viaValues = new ArrayList<>();
                map.values().forEach(viaValues::add);
                assertThat(viaValues).containsExactlyElementsOf(map.values());
                assertThat(SerializableTester.reserialize(map)).isEqualTo(map);
              }
              ImmutableSetMultimap<String, Integer> multimap = multimapOf(5);
              List<String> forEachKeys = new ArrayList<>();
              multimap.forEach((k, v) -> forEachKeys.add(k + "=" + v));
              assertThat(forEachKeys).containsExactly("a=0", "b=1", "c=2", "d=3", "e=4");
              assertThat(multimap.asMap().values().stream().map(Collection::size))
                  .containsExactly(1, 1, 1, 1, 1);
              assertThat(mapOf(3).asMultimap().inverse().get(1)).containsExactly("b");
            }));
    return tests;
  }

  private static ImmutableSetMultimap<String, Integer> multimapOf(int n) {
    ImmutableSetMultimap.Builder<String, Integer> builder = ImmutableSetMultimap.builder();
    for (int i = 0; i < n; i++) {
      builder.put(ELEMENTS.get(i), i);
    }
    return builder.build();
  }

  /** Returns 2^power distinct strings that all have the same hash code. */
  static List<String> collidingStrings(int power) {
    List<String> result = new ArrayList<>();
    result.add("");
    for (int i = 0; i < power; i++) {
      List<String> next = new ArrayList<>();
      for (String s : result) {
        next.add(s + "Aa");
        next.add(s + "BB");
      }
      result = next;
    }
    return result;
  }

  @TestFactory
  List<DynamicTest> immutableSetMultimapValueSets() {
    List<DynamicTest> tests = new ArrayList<>();
    ImmutableSetMultimap<String, Integer> multimap =
        ImmutableSetMultimap.<String, Integer>builder()
            .putAll("a", 3, 1, 2)
            .put("b", 9)
            .putAll("c", 5, 4)
            .build();
    collectionCase(tests, "get(a)", () -> multimap.get("a"), List.of(3, 1, 2), 99);
    collectionCase(tests, "get(absent)", () -> multimap.get("zz"), List.of(), 99);
    collectionCase(tests, "inverse().get(9)", () -> multimap.inverse().get(9), List.of("b"), "z");
    collectionCase(tests, "asMap().keySet()", () -> multimap.asMap().keySet(), List.of("a", "b", "c"), "z");
    ImmutableSetMultimap<String, Integer> sorted =
        ImmutableSetMultimap.<String, Integer>builder()
            .orderValuesBy(java.util.Comparator.reverseOrder())
            .putAll("a", 3, 1, 2)
            .build();
    collectionCase(tests, "orderValuesBy get(a)", () -> sorted.get("a"), List.of(3, 2, 1), 99);
    return tests;
  }

  /** MapInterfaceTest over the ImmutableMap implementations the ported suites do not reach. */
  abstract static class ImmutableMapInterfaceTest<K, V> extends MapInterfaceTest<K, V> {
    ImmutableMapInterfaceTest() {
      super(false, false, false, false, false);
    }

    @Override
    protected Map<K, V> makeEmptyMap() {
      return ImmutableMap.of();
    }
  }

  @Nested
  class EnumMapBacked extends ImmutableMapInterfaceTest<Thread.State, String> {
    @Override
    protected Map<Thread.State, String> makePopulatedMap() {
      EnumMap<Thread.State, String> map = new EnumMap<>(Thread.State.class);
      map.put(Thread.State.NEW, "new");
      map.put(Thread.State.RUNNABLE, "runnable");
      map.put(Thread.State.BLOCKED, "blocked");
      ImmutableMap<Thread.State, String> result = ImmutableMap.copyOf(map);
      assertThat(result).isInstanceOf(ImmutableEnumMap.class);
      return result;
    }

    @Override
    protected Thread.State getKeyNotInPopulatedMap() {
      return Thread.State.TERMINATED;
    }

    @Override
    protected String getValueNotInPopulatedMap() {
      return "terminated";
    }
  }

  @Nested
  class JdkBackedAfterHashFlooding extends ImmutableMapInterfaceTest<String, Integer> {
    @Override
    protected Map<String, Integer> makePopulatedMap() {
      Map<String, Integer> source = new LinkedHashMap<>();
      List<String> keys = collidingStrings(5);
      for (int i = 0; i < keys.size(); i++) {
        source.put(keys.get(i), i);
      }
      ImmutableMap<String, Integer> result = ImmutableMap.copyOf(source);
      assertThat(result).isInstanceOf(JdkBackedImmutableMap.class);
      return result;
    }

    @Override
    protected String getKeyNotInPopulatedMap() {
      return "absent";
    }

    @Override
    protected Integer getValueNotInPopulatedMap() {
      return -1;
    }
  }

  @Nested
  class AsMultimapAsMap
      extends ImmutableMapInterfaceTest<String, Collection<Integer>> {
    @Override
    protected Map<String, Collection<Integer>> makeEmptyMap() {
      return ImmutableMap.<String, Integer>of().asMultimap().asMap();
    }

    @Override
    protected Map<String, Collection<Integer>> makePopulatedMap() {
      return ImmutableMap.of("one", 1, "two", 2, "three", 3).asMultimap().asMap();
    }

    @Override
    protected String getKeyNotInPopulatedMap() {
      return "four";
    }

    @Override
    protected Collection<Integer> getValueNotInPopulatedMap() {
      return ImmutableSet.of(4);
    }
  }

  @TestFactory
  List<DynamicTest> hashFloodingFallbacksKeepOrderAndContents() {
    List<DynamicTest> tests = new ArrayList<>();
    List<String> keys = collidingStrings(6);
    tests.add(
        dynamicTest(
            "ImmutableMap.builder with flooding keys",
            () -> {
              ImmutableMap.Builder<String, Integer> builder = ImmutableMap.builder();
              for (int i = 0; i < keys.size(); i++) {
                builder.put(keys.get(i), i);
              }
              ImmutableMap<String, Integer> map = builder.build();
              assertThat(map.keySet().asList()).containsExactlyElementsOf(keys);
              for (int i = 0; i < keys.size(); i++) {
                assertThat(map.get(keys.get(i))).isEqualTo(i);
              }
              assertThat(map.get("absent")).isNull();
            }));
    tests.add(
        dynamicTest(
            "ImmutableSet.builder with flooding elements",
            () -> {
              ImmutableSet<String> set = ImmutableSet.<String>builder().addAll(keys).addAll(keys).build();
              assertThat(set.asList()).containsExactlyElementsOf(keys);
              assertThat(set.stream().collect(Collectors.toList())).containsExactlyElementsOf(keys);
              Set<String> copy = SerializableTester.reserializeAndAssert(set);
              assertThat(copy).containsExactlyElementsOf(keys);
            }));
    return tests;
  }
}
