package com.simplelibs.lava.collect;

import com.simplelibs.lava.collect.testing.MapInterfaceTest;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Nested;

/**
 * Lava replacement for the guava-testlib suites of {@code MapsCollectionTest}: runs the ported
 * {@link MapInterfaceTest} over the main {@link Maps} view families, {@link HashBiMap} and its
 * views. (Exact parity with Guava's views under random mutation is checked by
 * {@link MapsViewDifferentialTest}.)
 */
class MapsContractTest {

  private static Map<String, Integer> populatedBacking() {
    Map<String, Integer> map = new LinkedHashMap<>();
    map.put("one", 1);
    map.put("two", 2);
    map.put("three", 3);
    map.put("four", 4);
    return map;
  }

  private static NavigableMap<String, Integer> populatedTree() {
    return new TreeMap<>(populatedBacking());
  }

  abstract static class StringIntegerMapTest extends MapInterfaceTest<String, Integer> {
    StringIntegerMapTest(
        boolean allowsNullKeys,
        boolean allowsNullValues,
        boolean supportsPut,
        boolean supportsRemove,
        boolean supportsClear,
        boolean supportsIteratorRemove) {
      super(allowsNullKeys, allowsNullValues, supportsPut, supportsRemove, supportsClear,
          supportsIteratorRemove);
    }

    @Override
    protected String getKeyNotInPopulatedMap() {
      return "six"; // even length 3: passes the filters used below
    }

    @Override
    protected Integer getValueNotInPopulatedMap() {
      return 6;
    }
  }

  @Nested
  class FilterKeys extends StringIntegerMapTest {
    FilterKeys() {
      super(false, true, true, true, true, false);
    }

    @Override
    protected Map<String, Integer> makeEmptyMap() {
      return Maps.filterKeys(new LinkedHashMap<String, Integer>(), k -> k.length() == 3);
    }

    @Override
    protected Map<String, Integer> makePopulatedMap() {
      return Maps.filterKeys(populatedBacking(), k -> k.length() == 3);
    }
  }

  @Nested
  class FilterValuesNavigable extends StringIntegerMapTest {
    FilterValuesNavigable() {
      super(false, false, true, true, true, false);
    }

    @Override
    protected Map<String, Integer> makeEmptyMap() {
      return Maps.filterValues(new TreeMap<String, Integer>(), v -> v % 2 == 0);
    }

    @Override
    protected Map<String, Integer> makePopulatedMap() {
      return Maps.filterValues(populatedTree(), v -> v % 2 == 0);
    }

    /**
     * Guava's filtered-entry maps return false here instead of throwing (verified against Guava
     * 27.0.1); MapInterfaceTest is stricter than guava-testlib's tolerant check.
     */
    @Override
    @org.junit.jupiter.api.Test
    public void testKeySetRemoveAllNullFromEmpty() {
      assertFalse(makeEmptyMap().keySet().removeAll(null));
    }
  }

  @Nested
  class FilterEntriesSorted extends StringIntegerMapTest {
    FilterEntriesSorted() {
      super(false, false, true, true, true, false);
    }

    @Override
    protected Map<String, Integer> makeEmptyMap() {
      return Maps.filterEntries(
          (java.util.SortedMap<String, Integer>) new TreeMap<String, Integer>(),
          e -> e.getValue() != 1);
    }

    @Override
    protected Map<String, Integer> makePopulatedMap() {
      return Maps.filterEntries(
          (java.util.SortedMap<String, Integer>) populatedTree(), e -> e.getValue() != 1);
    }

    /**
     * Guava's filtered-entry maps return false here instead of throwing (verified against Guava
     * 27.0.1); MapInterfaceTest is stricter than guava-testlib's tolerant check.
     */
    @Override
    @org.junit.jupiter.api.Test
    public void testKeySetRemoveAllNullFromEmpty() {
      assertFalse(makeEmptyMap().keySet().removeAll(null));
    }
  }

  @Nested
  class TransformValues extends StringIntegerMapTest {
    TransformValues() {
      super(false, true, false, true, true, true);
    }

    @Override
    protected Map<String, Integer> makeEmptyMap() {
      return Maps.transformValues(new LinkedHashMap<String, Integer>(), v -> v * 10);
    }

    @Override
    protected Map<String, Integer> makePopulatedMap() {
      return Maps.transformValues(populatedBacking(), v -> v * 10);
    }
  }

  @Nested
  class TransformEntriesNavigableDescending extends StringIntegerMapTest {
    TransformEntriesNavigableDescending() {
      super(false, true, false, true, true, true);
    }

    @Override
    protected Map<String, Integer> makeEmptyMap() {
      return Maps.transformEntries(new TreeMap<String, Integer>(), (k, v) -> k.length() + v)
          .descendingMap();
    }

    @Override
    protected Map<String, Integer> makePopulatedMap() {
      return Maps.transformEntries(populatedTree(), (k, v) -> k.length() + v).descendingMap();
    }
  }

  @Nested
  class AsMapSet extends StringIntegerMapTest {
    AsMapSet() {
      super(false, true, false, true, true, true);
    }

    @Override
    protected Map<String, Integer> makeEmptyMap() {
      return Maps.asMap(new LinkedHashSet<String>(), String::length);
    }

    @Override
    protected Map<String, Integer> makePopulatedMap() {
      Set<String> keys = new LinkedHashSet<>(populatedBacking().keySet());
      return Maps.asMap(keys, String::length);
    }
  }

  @Nested
  class AsMapNavigableSet extends StringIntegerMapTest {
    AsMapNavigableSet() {
      super(false, true, false, true, true, true);
    }

    @Override
    protected Map<String, Integer> makeEmptyMap() {
      return Maps.asMap(new TreeSet<String>(), String::length);
    }

    @Override
    protected Map<String, Integer> makePopulatedMap() {
      return Maps.asMap(new TreeSet<>(populatedBacking().keySet()), String::length).headMap("x", true);
    }
  }

  @Nested
  class UnmodifiableNavigable extends StringIntegerMapTest {
    UnmodifiableNavigable() {
      super(false, false, false, false, false, false);
    }

    @Override
    protected Map<String, Integer> makeEmptyMap() {
      return Maps.unmodifiableNavigableMap(new TreeMap<String, Integer>());
    }

    @Override
    protected Map<String, Integer> makePopulatedMap() {
      return Maps.unmodifiableNavigableMap(populatedTree()).descendingMap();
    }
  }

  @Nested
  class SubMapRange extends StringIntegerMapTest {
    SubMapRange() {
      super(false, true, true, true, true, true); // TreeMap permits null values
    }

    @Override
    protected Map<String, Integer> makeEmptyMap() {
      return Maps.subMap(new TreeMap<String, Integer>(), Range.closed("a", "z"));
    }

    @Override
    protected Map<String, Integer> makePopulatedMap() {
      return Maps.subMap(populatedTree(), Range.closed("a", "z"));
    }
  }

  @Nested
  class HashBiMapItself extends StringIntegerMapTest {
    HashBiMapItself() {
      super(true, true, true, true, true, true);
    }

    @Override
    protected Map<String, Integer> makeEmptyMap() {
      return HashBiMap.create();
    }

    @Override
    protected Map<String, Integer> makePopulatedMap() {
      return HashBiMap.create(populatedBacking());
    }
  }

  @Nested
  class HashBiMapInverse extends MapInterfaceTest<Integer, String> {
    HashBiMapInverse() {
      super(true, true, true, true, true, true);
    }

    @Override
    protected Map<Integer, String> makeEmptyMap() {
      return HashBiMap.<String, Integer>create().inverse();
    }

    @Override
    protected Map<Integer, String> makePopulatedMap() {
      return HashBiMap.create(populatedBacking()).inverse();
    }

    @Override
    protected Integer getKeyNotInPopulatedMap() {
      return 6;
    }

    @Override
    protected String getValueNotInPopulatedMap() {
      return "six";
    }
  }

  @Nested
  class UnmodifiableBiMap extends StringIntegerMapTest {
    UnmodifiableBiMap() {
      super(true, true, false, false, false, false);
    }

    @Override
    protected Map<String, Integer> makeEmptyMap() {
      return Maps.unmodifiableBiMap(HashBiMap.<String, Integer>create());
    }

    @Override
    protected Map<String, Integer> makePopulatedMap() {
      return Maps.unmodifiableBiMap(HashBiMap.create(populatedBacking()));
    }
  }

  @Nested
  class SynchronizedBiMap extends StringIntegerMapTest {
    SynchronizedBiMap() {
      super(true, true, true, true, true, true);
    }

    @Override
    protected Map<String, Integer> makeEmptyMap() {
      return Maps.synchronizedBiMap(HashBiMap.<String, Integer>create());
    }

    @Override
    protected Map<String, Integer> makePopulatedMap() {
      return Maps.synchronizedBiMap(HashBiMap.create(populatedBacking()));
    }
  }

  @Nested
  class FilteredBiMap extends StringIntegerMapTest {
    FilteredBiMap() {
      super(true, true, true, true, true, false);
    }

    @Override
    protected Map<String, Integer> makeEmptyMap() {
      return Maps.filterValues(HashBiMap.<String, Integer>create(), v -> v == null || v != 1);
    }

    @Override
    protected Map<String, Integer> makePopulatedMap() {
      return Maps.filterValues(HashBiMap.create(populatedBacking()), v -> v == null || v != 1);
    }

    /**
     * Guava's filtered-entry maps return false here instead of throwing (verified against Guava
     * 27.0.1); MapInterfaceTest is stricter than guava-testlib's tolerant check.
     */
    @Override
    @org.junit.jupiter.api.Test
    public void testKeySetRemoveAllNullFromEmpty() {
      assertFalse(makeEmptyMap().keySet().removeAll(null));
    }
  }
}
