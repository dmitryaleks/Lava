/*
 * Copyright (C) 2007 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified for Lava: single-threaded, Guava-independent port of Guava 27.0.1.
 */

package com.simplelibs.lava.collect;

import static com.simplelibs.lava.collect.Maps.transformEntries;
import static com.simplelibs.lava.collect.Maps.transformValues;
import static com.simplelibs.lava.collect.Maps.unmodifiableNavigableMap;
import static com.simplelibs.lava.collect.testing.Helpers.mapEntry;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import com.simplelibs.lava.base.Converter;
import com.simplelibs.lava.base.Equivalence;
import com.simplelibs.lava.collect.Maps.EntryTransformer;
import com.simplelibs.lava.collect.Maps.ValueDifferenceImpl;
import com.simplelibs.lava.testing.EqualsTester;
import com.simplelibs.lava.testing.LavaTestCase;
import com.simplelibs.lava.testing.NullPointerTester;
import com.simplelibs.lava.testing.SerializableTester;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@code Maps}.
 *
 * @author Kevin Bourrillion
 * @author Mike Bostock
 * @author Jared Levy
 */
public class MapsTest extends LavaTestCase {

  private static final Comparator<Integer> SOME_COMPARATOR = Collections.reverseOrder();

  @Test
  public void testHashMap() {
    HashMap<Integer, Integer> map = Maps.newHashMap();
    assertEquals(Collections.emptyMap(), map);
  }

  @Test
  public void testHashMapWithInitialMap() {
    Map<String, Integer> original = new TreeMap<>();
    original.put("a", 1);
    original.put("b", 2);
    original.put("c", 3);
    HashMap<String, Integer> map = Maps.newHashMap(original);
    assertEquals(original, map);
  }

  @Test
  public void testHashMapGeneralizesTypes() {
    Map<String, Integer> original = new TreeMap<>();
    original.put("a", 1);
    original.put("b", 2);
    original.put("c", 3);
    HashMap<Object, Object> map =
        Maps.newHashMap((Map<? extends Object, ? extends Object>) original);
    assertEquals(original, map);
  }

  @Test
  public void testCapacityForNegativeSizeFails() {
    try {
      Maps.capacity(-1);
      fail("Negative expected size must result in IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  /**
   * Tests that nHMWES makes hash maps large enough that adding the expected number of elements
   * won't cause a rehash.
   *
   * <p>As of jdk7u40, HashMap has an empty-map optimization. The argument to new HashMap(int) is
   * noted, but the initial table is a zero-length array.
   *
   * <p>This test may fail miserably on non-OpenJDK environments...
   */
  @Test
  public void testNewHashMapWithExpectedSize_wontGrow() throws Exception {
    // before jdk7u40: creates one-bucket table
    // after  jdk7u40: creates empty table
    assertTrue(bucketsOf(Maps.newHashMapWithExpectedSize(0)) <= 1);

    for (int size = 1; size < 200; size++) {
      assertWontGrow(
          size, Maps.newHashMapWithExpectedSize(size), Maps.newHashMapWithExpectedSize(size));
    }
  }

  /** Same test as above but for newLinkedHashMapWithExpectedSize */
  @Test
  public void testNewLinkedHashMapWithExpectedSize_wontGrow() throws Exception {
    assertTrue(bucketsOf(Maps.newLinkedHashMapWithExpectedSize(0)) <= 1);

    for (int size = 1; size < 200; size++) {
      assertWontGrow(
          size,
          Maps.newLinkedHashMapWithExpectedSize(size),
          Maps.newLinkedHashMapWithExpectedSize(size));
    }
  }

  private static void assertWontGrow(
      int size, HashMap<Object, Object> map1, HashMap<Object, Object> map2) throws Exception {
    // Only start measuring table size after the first element inserted, to
    // deal with empty-map optimization.
    map1.put(0, null);

    int initialBuckets = bucketsOf(map1);

    for (int i = 1; i < size; i++) {
      map1.put(i, null);
    }
    assertThat(bucketsOf(map1))
        .as("table size after adding " + size + " elements")
        .isEqualTo(initialBuckets);

    /*
     * Something slightly different happens when the entries are added all at
     * once; make sure that passes too.
     */
    map2.putAll(map1);
    // Lava: Guava's test re-checks map1 here, so map2 was never checked.
    assertThat(bucketsOf(map2))
        .as("table size after adding " + size + " elements at once")
        .isEqualTo(initialBuckets);
  }

  private static int bucketsOf(HashMap<?, ?> hashMap) throws Exception {
    Field tableField = HashMap.class.getDeclaredField("table");
    tableField.setAccessible(true);
    Object[] table = (Object[]) tableField.get(hashMap);
    // In JDK8, table is set lazily, so it may be null.
    return table == null ? 0 : table.length;
  }

  @Test
  public void testCapacityForLargeSizes() {
    int[] largeExpectedSizes =
        new int[] {
          Integer.MAX_VALUE / 2 - 1,
          Integer.MAX_VALUE / 2,
          Integer.MAX_VALUE / 2 + 1,
          Integer.MAX_VALUE - 1,
          Integer.MAX_VALUE
        };
    for (int expectedSize : largeExpectedSizes) {
      int capacity = Maps.capacity(expectedSize);
      assertTrue(
          "capacity (" + capacity + ") must be >= expectedSize (" + expectedSize + ")",
          capacity >= expectedSize);
    }
  }

  @Test
  public void testLinkedHashMap() {
    LinkedHashMap<Integer, Integer> map = Maps.newLinkedHashMap();
    assertEquals(Collections.emptyMap(), map);
  }

  @SuppressWarnings("serial")
  @Test
  public void testLinkedHashMapWithInitialMap() {
    Map<String, String> map =
        new LinkedHashMap<String, String>(
            ImmutableMap.of(
                "Hello", "World",
                "first", "second",
                "polygene", "lubricants",
                "alpha", "betical"));

    LinkedHashMap<String, String> copy = Maps.newLinkedHashMap(map);

    Iterator<Entry<String, String>> iter = copy.entrySet().iterator();
    assertTrue(iter.hasNext());
    Entry<String, String> entry = iter.next();
    assertEquals("Hello", entry.getKey());
    assertEquals("World", entry.getValue());
    assertTrue(iter.hasNext());

    entry = iter.next();
    assertEquals("first", entry.getKey());
    assertEquals("second", entry.getValue());
    assertTrue(iter.hasNext());

    entry = iter.next();
    assertEquals("polygene", entry.getKey());
    assertEquals("lubricants", entry.getValue());
    assertTrue(iter.hasNext());

    entry = iter.next();
    assertEquals("alpha", entry.getKey());
    assertEquals("betical", entry.getValue());
    assertFalse(iter.hasNext());
  }

  @Test
  public void testLinkedHashMapGeneralizesTypes() {
    Map<String, Integer> original = new LinkedHashMap<>();
    original.put("a", 1);
    original.put("b", 2);
    original.put("c", 3);
    HashMap<Object, Object> map = Maps.<Object, Object>newLinkedHashMap(original);
    assertEquals(original, map);
  }

  @Test
  public void testIdentityHashMap() {
    IdentityHashMap<Integer, Integer> map = Maps.newIdentityHashMap();
    assertEquals(Collections.emptyMap(), map);
  }

  @Test
  public void testConcurrentMap() {
    ConcurrentMap<Integer, Integer> map = Maps.newConcurrentMap();
    assertEquals(Collections.emptyMap(), map);
  }

  @Test
  public void testTreeMap() {
    TreeMap<Integer, Integer> map = Maps.newTreeMap();
    assertEquals(Collections.emptyMap(), map);
    assertNull(map.comparator());
  }

  @Test
  public void testTreeMapDerived() {
    TreeMap<Derived, Integer> map = Maps.newTreeMap();
    assertEquals(Collections.emptyMap(), map);
    map.put(new Derived("foo"), 1);
    map.put(new Derived("bar"), 2);
    assertThat(map.keySet()).containsExactly(new Derived("bar"), new Derived("foo"));
    assertThat(map.values()).containsExactly(2, 1);
    assertNull(map.comparator());
  }

  @Test
  public void testTreeMapNonGeneric() {
    TreeMap<LegacyComparable, Integer> map = Maps.newTreeMap();
    assertEquals(Collections.emptyMap(), map);
    map.put(new LegacyComparable("foo"), 1);
    map.put(new LegacyComparable("bar"), 2);
    assertThat(map.keySet())
        .containsExactly(new LegacyComparable("bar"), new LegacyComparable("foo"));
    assertThat(map.values()).containsExactly(2, 1);
    assertNull(map.comparator());
  }

  @Test
  public void testTreeMapWithComparator() {
    TreeMap<Integer, Integer> map = Maps.newTreeMap(SOME_COMPARATOR);
    assertEquals(Collections.emptyMap(), map);
    assertSame(SOME_COMPARATOR, map.comparator());
  }

  @Test
  public void testTreeMapWithInitialMap() {
    SortedMap<Integer, Integer> map = Maps.newTreeMap();
    map.put(5, 10);
    map.put(3, 20);
    map.put(1, 30);
    TreeMap<Integer, Integer> copy = Maps.newTreeMap(map);
    assertEquals(copy, map);
    assertSame(copy.comparator(), map.comparator());
  }

  public enum SomeEnum {
    SOME_INSTANCE
  }

  @Test
  public void testEnumMap() {
    EnumMap<SomeEnum, Integer> map = Maps.newEnumMap(SomeEnum.class);
    assertEquals(Collections.emptyMap(), map);
    map.put(SomeEnum.SOME_INSTANCE, 0);
    assertEquals(Collections.singletonMap(SomeEnum.SOME_INSTANCE, 0), map);
  }

  @Test
  public void testEnumMapNullClass() {
    try {
      Maps.<SomeEnum, Long>newEnumMap((Class<MapsTest.SomeEnum>) null);
      fail("no exception thrown");
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void testEnumMapWithInitialEnumMap() {
    EnumMap<SomeEnum, Integer> original = Maps.newEnumMap(SomeEnum.class);
    original.put(SomeEnum.SOME_INSTANCE, 0);
    EnumMap<SomeEnum, Integer> copy = Maps.newEnumMap(original);
    assertEquals(original, copy);
  }

  @Test
  public void testEnumMapWithInitialEmptyEnumMap() {
    EnumMap<SomeEnum, Integer> original = Maps.newEnumMap(SomeEnum.class);
    EnumMap<SomeEnum, Integer> copy = Maps.newEnumMap(original);
    assertEquals(original, copy);
    assertNotSame(original, copy);
  }

  @Test
  public void testEnumMapWithInitialMap() {
    HashMap<SomeEnum, Integer> original = Maps.newHashMap();
    original.put(SomeEnum.SOME_INSTANCE, 0);
    EnumMap<SomeEnum, Integer> copy = Maps.newEnumMap(original);
    assertEquals(original, copy);
  }

  @Test
  public void testEnumMapWithInitialEmptyMap() {
    Map<SomeEnum, Integer> original = Maps.newHashMap();
    try {
      Maps.newEnumMap(original);
      fail("Empty map must result in an IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testToStringImplWithNullKeys() throws Exception {
    Map<String, String> hashmap = Maps.newHashMap();
    hashmap.put("foo", "bar");
    hashmap.put(null, "baz");

    assertEquals(hashmap.toString(), Maps.toStringImpl(hashmap));
  }

  @Test
  public void testToStringImplWithNullValues() throws Exception {
    Map<String, String> hashmap = Maps.newHashMap();
    hashmap.put("foo", "bar");
    hashmap.put("baz", null);

    assertEquals(hashmap.toString(), Maps.toStringImpl(hashmap));
  }

  @Test
  public void testNullPointerExceptions() {
    new NullPointerTester().testAllPublicStaticMethods(Maps.class);
  }

  private static final Map<Integer, Integer> EMPTY = Collections.emptyMap();
  private static final Map<Integer, Integer> SINGLETON = Collections.singletonMap(1, 2);

  @Test
  public void testMapDifferenceEmptyEmpty() {
    MapDifference<Integer, Integer> diff = Maps.difference(EMPTY, EMPTY);
    assertTrue(diff.areEqual());
    assertEquals(EMPTY, diff.entriesOnlyOnLeft());
    assertEquals(EMPTY, diff.entriesOnlyOnRight());
    assertEquals(EMPTY, diff.entriesInCommon());
    assertEquals(EMPTY, diff.entriesDiffering());
    assertEquals("equal", diff.toString());
  }

  @Test
  public void testMapDifferenceEmptySingleton() {
    MapDifference<Integer, Integer> diff = Maps.difference(EMPTY, SINGLETON);
    assertFalse(diff.areEqual());
    assertEquals(EMPTY, diff.entriesOnlyOnLeft());
    assertEquals(SINGLETON, diff.entriesOnlyOnRight());
    assertEquals(EMPTY, diff.entriesInCommon());
    assertEquals(EMPTY, diff.entriesDiffering());
    assertEquals("not equal: only on right={1=2}", diff.toString());
  }

  @Test
  public void testMapDifferenceSingletonEmpty() {
    MapDifference<Integer, Integer> diff = Maps.difference(SINGLETON, EMPTY);
    assertFalse(diff.areEqual());
    assertEquals(SINGLETON, diff.entriesOnlyOnLeft());
    assertEquals(EMPTY, diff.entriesOnlyOnRight());
    assertEquals(EMPTY, diff.entriesInCommon());
    assertEquals(EMPTY, diff.entriesDiffering());
    assertEquals("not equal: only on left={1=2}", diff.toString());
  }

  @Test
  public void testMapDifferenceTypical() {
    Map<Integer, String> left = ImmutableMap.of(1, "a", 2, "b", 3, "c", 4, "d", 5, "e");
    Map<Integer, String> right = ImmutableMap.of(1, "a", 3, "f", 5, "g", 6, "z");

    MapDifference<Integer, String> diff1 = Maps.difference(left, right);
    assertFalse(diff1.areEqual());
    assertEquals(ImmutableMap.of(2, "b", 4, "d"), diff1.entriesOnlyOnLeft());
    assertEquals(ImmutableMap.of(6, "z"), diff1.entriesOnlyOnRight());
    assertEquals(ImmutableMap.of(1, "a"), diff1.entriesInCommon());
    assertEquals(
        ImmutableMap.of(
            3, ValueDifferenceImpl.create("c", "f"), 5, ValueDifferenceImpl.create("e", "g")),
        diff1.entriesDiffering());
    assertEquals(
        "not equal: only on left={2=b, 4=d}: only on right={6=z}: "
            + "value differences={3=(c, f), 5=(e, g)}",
        diff1.toString());

    MapDifference<Integer, String> diff2 = Maps.difference(right, left);
    assertFalse(diff2.areEqual());
    assertEquals(ImmutableMap.of(6, "z"), diff2.entriesOnlyOnLeft());
    assertEquals(ImmutableMap.of(2, "b", 4, "d"), diff2.entriesOnlyOnRight());
    assertEquals(ImmutableMap.of(1, "a"), diff2.entriesInCommon());
    assertEquals(
        ImmutableMap.of(
            3, ValueDifferenceImpl.create("f", "c"), 5, ValueDifferenceImpl.create("g", "e")),
        diff2.entriesDiffering());
    assertEquals(
        "not equal: only on left={6=z}: only on right={2=b, 4=d}: "
            + "value differences={3=(f, c), 5=(g, e)}",
        diff2.toString());
  }

  @Test
  public void testMapDifferenceEquals() {
    Map<Integer, String> left = ImmutableMap.of(1, "a", 2, "b", 3, "c", 4, "d", 5, "e");
    Map<Integer, String> right = ImmutableMap.of(1, "a", 3, "f", 5, "g", 6, "z");
    Map<Integer, String> right2 = ImmutableMap.of(1, "a", 3, "h", 5, "g", 6, "z");
    MapDifference<Integer, String> original = Maps.difference(left, right);
    MapDifference<Integer, String> same = Maps.difference(left, right);
    MapDifference<Integer, String> reverse = Maps.difference(right, left);
    MapDifference<Integer, String> diff2 = Maps.difference(left, right2);

    new EqualsTester()
        .addEqualityGroup(original, same)
        .addEqualityGroup(reverse)
        .addEqualityGroup(diff2)
        .testEquals();
  }

  @Test
  public void testMapDifferencePredicateTypical() {
    Map<Integer, String> left = ImmutableMap.of(1, "a", 2, "b", 3, "c", 4, "d", 5, "e");
    Map<Integer, String> right = ImmutableMap.of(1, "A", 3, "F", 5, "G", 6, "Z");

    // TODO(kevinb): replace with Ascii.caseInsensitiveEquivalence() when it
    // exists
    Equivalence<String> caseInsensitiveEquivalence =
        Equivalence.equals()
            .onResultOf(
                new Function<String, String>() {
                  @Override
                  public String apply(String input) {
                    return input.toLowerCase();
                  }
                });

    MapDifference<Integer, String> diff1 = Maps.difference(left, right, caseInsensitiveEquivalence);
    assertFalse(diff1.areEqual());
    assertEquals(ImmutableMap.of(2, "b", 4, "d"), diff1.entriesOnlyOnLeft());
    assertEquals(ImmutableMap.of(6, "Z"), diff1.entriesOnlyOnRight());
    assertEquals(ImmutableMap.of(1, "a"), diff1.entriesInCommon());
    assertEquals(
        ImmutableMap.of(
            3, ValueDifferenceImpl.create("c", "F"), 5, ValueDifferenceImpl.create("e", "G")),
        diff1.entriesDiffering());
    assertEquals(
        "not equal: only on left={2=b, 4=d}: only on right={6=Z}: "
            + "value differences={3=(c, F), 5=(e, G)}",
        diff1.toString());

    MapDifference<Integer, String> diff2 = Maps.difference(right, left, caseInsensitiveEquivalence);
    assertFalse(diff2.areEqual());
    assertEquals(ImmutableMap.of(6, "Z"), diff2.entriesOnlyOnLeft());
    assertEquals(ImmutableMap.of(2, "b", 4, "d"), diff2.entriesOnlyOnRight());
    assertEquals(ImmutableMap.of(1, "A"), diff2.entriesInCommon());
    assertEquals(
        ImmutableMap.of(
            3, ValueDifferenceImpl.create("F", "c"), 5, ValueDifferenceImpl.create("G", "e")),
        diff2.entriesDiffering());
    assertEquals(
        "not equal: only on left={6=Z}: only on right={2=b, 4=d}: "
            + "value differences={3=(F, c), 5=(G, e)}",
        diff2.toString());
  }

  private static final SortedMap<Integer, Integer> SORTED_EMPTY = Maps.newTreeMap();
  private static final NavigableMap<Integer, Integer> SORTED_SINGLETON =
      GuavaStandIns.sortedMapOf(1, 2);

  @Test
  public void testMapDifferenceOfSortedMapIsSorted() {
    Map<Integer, Integer> map = SORTED_SINGLETON;
    MapDifference<Integer, Integer> difference = Maps.difference(map, EMPTY);
    assertTrue(difference instanceof SortedMapDifference);
  }

  @Test
  public void testSortedMapDifferenceEmptyEmpty() {
    SortedMapDifference<Integer, Integer> diff = Maps.difference(SORTED_EMPTY, SORTED_EMPTY);
    assertTrue(diff.areEqual());
    assertEquals(SORTED_EMPTY, diff.entriesOnlyOnLeft());
    assertEquals(SORTED_EMPTY, diff.entriesOnlyOnRight());
    assertEquals(SORTED_EMPTY, diff.entriesInCommon());
    assertEquals(SORTED_EMPTY, diff.entriesDiffering());
    assertEquals("equal", diff.toString());
  }

  @Test
  public void testSortedMapDifferenceEmptySingleton() {
    SortedMapDifference<Integer, Integer> diff = Maps.difference(SORTED_EMPTY, SORTED_SINGLETON);
    assertFalse(diff.areEqual());
    assertEquals(SORTED_EMPTY, diff.entriesOnlyOnLeft());
    assertEquals(SORTED_SINGLETON, diff.entriesOnlyOnRight());
    assertEquals(SORTED_EMPTY, diff.entriesInCommon());
    assertEquals(SORTED_EMPTY, diff.entriesDiffering());
    assertEquals("not equal: only on right={1=2}", diff.toString());
  }

  @Test
  public void testSortedMapDifferenceSingletonEmpty() {
    SortedMapDifference<Integer, Integer> diff = Maps.difference(SORTED_SINGLETON, SORTED_EMPTY);
    assertFalse(diff.areEqual());
    assertEquals(SORTED_SINGLETON, diff.entriesOnlyOnLeft());
    assertEquals(SORTED_EMPTY, diff.entriesOnlyOnRight());
    assertEquals(SORTED_EMPTY, diff.entriesInCommon());
    assertEquals(SORTED_EMPTY, diff.entriesDiffering());
    assertEquals("not equal: only on left={1=2}", diff.toString());
  }

  @Test
  public void testSortedMapDifferenceTypical() {
    SortedMap<Integer, String> left =
        GuavaStandIns.<Integer, String>reverseOrderSortedMap()
            .put(1, "a")
            .put(2, "b")
            .put(3, "c")
            .put(4, "d")
            .put(5, "e")
            .build();

    SortedMap<Integer, String> right = GuavaStandIns.sortedMapOf(1, "a", 3, "f", 5, "g", 6, "z");

    SortedMapDifference<Integer, String> diff1 = Maps.difference(left, right);
    assertFalse(diff1.areEqual());
    assertThat(diff1.entriesOnlyOnLeft().entrySet())
        .containsExactly(Maps.immutableEntry(4, "d"), Maps.immutableEntry(2, "b"));
    assertThat(diff1.entriesOnlyOnRight().entrySet()).contains(Maps.immutableEntry(6, "z"));
    assertThat(diff1.entriesInCommon().entrySet()).contains(Maps.immutableEntry(1, "a"));
    assertThat(diff1.entriesDiffering().entrySet())
        .containsExactly(
            Maps.immutableEntry(5, ValueDifferenceImpl.create("e", "g")),
            Maps.immutableEntry(3, ValueDifferenceImpl.create("c", "f")));
    assertEquals(
        "not equal: only on left={4=d, 2=b}: only on right={6=z}: "
            + "value differences={5=(e, g), 3=(c, f)}",
        diff1.toString());

    SortedMapDifference<Integer, String> diff2 = Maps.difference(right, left);
    assertFalse(diff2.areEqual());
    assertThat(diff2.entriesOnlyOnLeft().entrySet()).contains(Maps.immutableEntry(6, "z"));
    assertThat(diff2.entriesOnlyOnRight().entrySet())
        .containsExactly(Maps.immutableEntry(2, "b"), Maps.immutableEntry(4, "d"));
    assertThat(diff1.entriesInCommon().entrySet()).contains(Maps.immutableEntry(1, "a"));
    assertEquals(
        ImmutableMap.of(
            3, ValueDifferenceImpl.create("f", "c"),
            5, ValueDifferenceImpl.create("g", "e")),
        diff2.entriesDiffering());
    assertEquals(
        "not equal: only on left={6=z}: only on right={2=b, 4=d}: "
            + "value differences={3=(f, c), 5=(g, e)}",
        diff2.toString());
  }

  @Test
  public void testSortedMapDifferenceImmutable() {
    SortedMap<Integer, String> left =
        Maps.newTreeMap(GuavaStandIns.sortedMapOf(1, "a", 2, "b", 3, "c", 4, "d", 5, "e"));
    SortedMap<Integer, String> right =
        Maps.newTreeMap(GuavaStandIns.sortedMapOf(1, "a", 3, "f", 5, "g", 6, "z"));

    SortedMapDifference<Integer, String> diff1 = Maps.difference(left, right);
    left.put(6, "z");
    assertFalse(diff1.areEqual());
    assertThat(diff1.entriesOnlyOnLeft().entrySet())
        .containsExactly(Maps.immutableEntry(2, "b"), Maps.immutableEntry(4, "d"));
    assertThat(diff1.entriesOnlyOnRight().entrySet()).contains(Maps.immutableEntry(6, "z"));
    assertThat(diff1.entriesInCommon().entrySet()).contains(Maps.immutableEntry(1, "a"));
    assertThat(diff1.entriesDiffering().entrySet())
        .containsExactly(
            Maps.immutableEntry(3, ValueDifferenceImpl.create("c", "f")),
            Maps.immutableEntry(5, ValueDifferenceImpl.create("e", "g")));
    try {
      diff1.entriesInCommon().put(7, "x");
      fail();
    } catch (UnsupportedOperationException expected) {
    }
    try {
      diff1.entriesOnlyOnLeft().put(7, "x");
      fail();
    } catch (UnsupportedOperationException expected) {
    }
    try {
      diff1.entriesOnlyOnRight().put(7, "x");
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  @Test
  public void testSortedMapDifferenceEquals() {
    SortedMap<Integer, String> left = GuavaStandIns.sortedMapOf(1, "a", 2, "b", 3, "c", 4, "d", 5, "e");
    SortedMap<Integer, String> right = GuavaStandIns.sortedMapOf(1, "a", 3, "f", 5, "g", 6, "z");
    SortedMap<Integer, String> right2 = GuavaStandIns.sortedMapOf(1, "a", 3, "h", 5, "g", 6, "z");
    SortedMapDifference<Integer, String> original = Maps.difference(left, right);
    SortedMapDifference<Integer, String> same = Maps.difference(left, right);
    SortedMapDifference<Integer, String> reverse = Maps.difference(right, left);
    SortedMapDifference<Integer, String> diff2 = Maps.difference(left, right2);

    new EqualsTester()
        .addEqualityGroup(original, same)
        .addEqualityGroup(reverse)
        .addEqualityGroup(diff2)
        .testEquals();
  }

  private static final Function<String, Integer> LENGTH_FUNCTION =
      new Function<String, Integer>() {
        @Override
        public Integer apply(String input) {
          return input.length();
        }
      };

  @Test
  public void testAsMap() {
    Set<String> strings = ImmutableSet.of("one", "two", "three");
    Map<String, Integer> map = Maps.asMap(strings, LENGTH_FUNCTION);
    assertEquals(ImmutableMap.of("one", 3, "two", 3, "three", 5), map);
    assertEquals(Integer.valueOf(5), map.get("three"));
    assertNull(map.get("five"));
    assertThat(map.entrySet())
        .containsExactly(mapEntry("one", 3), mapEntry("two", 3), mapEntry("three", 5));
  }

  @Test
  public void testAsMapReadsThrough() {
    Set<String> strings = new java.util.LinkedHashSet<>();
    Collections.addAll(strings, "one", "two", "three");
    Map<String, Integer> map = Maps.asMap(strings, LENGTH_FUNCTION);
    assertEquals(ImmutableMap.of("one", 3, "two", 3, "three", 5), map);
    assertNull(map.get("four"));
    strings.add("four");
    assertEquals(ImmutableMap.of("one", 3, "two", 3, "three", 5, "four", 4), map);
    assertEquals(Integer.valueOf(4), map.get("four"));
  }

  @Test
  public void testAsMapWritesThrough() {
    Set<String> strings = new java.util.LinkedHashSet<>();
    Collections.addAll(strings, "one", "two", "three");
    Map<String, Integer> map = Maps.asMap(strings, LENGTH_FUNCTION);
    assertEquals(ImmutableMap.of("one", 3, "two", 3, "three", 5), map);
    assertEquals(Integer.valueOf(3), map.remove("two"));
    assertThat(strings).containsExactly("one", "three");
  }

  @Test
  public void testAsMapEmpty() {
    Set<String> strings = ImmutableSet.of();
    Map<String, Integer> map = Maps.asMap(strings, LENGTH_FUNCTION);
    assertThat(map.entrySet()).isEmpty();
    assertTrue(map.isEmpty());
    assertNull(map.get("five"));
  }

  private static class NonNavigableSortedSet extends ForwardingSortedSet<String> {
    private final SortedSet<String> delegate = new java.util.TreeSet<>();

    @Override
    protected SortedSet<String> delegate() {
      return delegate;
    }
  }

  @Test
  public void testAsMapSorted() {
    SortedSet<String> strings = new NonNavigableSortedSet();
    Collections.addAll(strings, "one", "two", "three");
    SortedMap<String, Integer> map = Maps.asMap(strings, LENGTH_FUNCTION);
    assertEquals(ImmutableMap.of("one", 3, "two", 3, "three", 5), map);
    assertEquals(Integer.valueOf(5), map.get("three"));
    assertNull(map.get("five"));
    assertThat(map.entrySet())
        .containsExactly(mapEntry("one", 3), mapEntry("three", 5), mapEntry("two", 3));
    assertThat(map.tailMap("onea").entrySet())
        .containsExactly(mapEntry("three", 5), mapEntry("two", 3));
    assertThat(map.subMap("one", "two").entrySet())
        .containsExactly(mapEntry("one", 3), mapEntry("three", 5));
  }

  @Test
  public void testAsMapSortedReadsThrough() {
    SortedSet<String> strings = new NonNavigableSortedSet();
    Collections.addAll(strings, "one", "two", "three");
    SortedMap<String, Integer> map = Maps.asMap(strings, LENGTH_FUNCTION);
    assertNull(map.comparator());
    assertEquals(GuavaStandIns.sortedMapOf("one", 3, "two", 3, "three", 5), map);
    assertNull(map.get("four"));
    strings.add("four");
    assertEquals(GuavaStandIns.sortedMapOf("one", 3, "two", 3, "three", 5, "four", 4), map);
    assertEquals(Integer.valueOf(4), map.get("four"));
    SortedMap<String, Integer> headMap = map.headMap("two");
    assertEquals(GuavaStandIns.sortedMapOf("four", 4, "one", 3, "three", 5), headMap);
    strings.add("five");
    strings.remove("one");
    assertEquals(GuavaStandIns.sortedMapOf("five", 4, "four", 4, "three", 5), headMap);
    assertThat(map.entrySet())
        .containsExactly(
            mapEntry("five", 4), mapEntry("four", 4), mapEntry("three", 5), mapEntry("two", 3));
  }

  @Test
  public void testAsMapSortedWritesThrough() {
    SortedSet<String> strings = new NonNavigableSortedSet();
    Collections.addAll(strings, "one", "two", "three");
    SortedMap<String, Integer> map = Maps.asMap(strings, LENGTH_FUNCTION);
    assertEquals(ImmutableMap.of("one", 3, "two", 3, "three", 5), map);
    assertEquals(Integer.valueOf(3), map.remove("two"));
    assertThat(strings).containsExactly("one", "three");
  }

  @Test
  public void testAsMapSortedSubViewKeySetsDoNotSupportAdd() {
    SortedMap<String, Integer> map = Maps.asMap(new NonNavigableSortedSet(), LENGTH_FUNCTION);
    try {
      map.subMap("a", "z").keySet().add("a");
      fail();
    } catch (UnsupportedOperationException expected) {
    }
    try {
      map.tailMap("a").keySet().add("a");
      fail();
    } catch (UnsupportedOperationException expected) {
    }
    try {
      map.headMap("r").keySet().add("a");
      fail();
    } catch (UnsupportedOperationException expected) {
    }
    try {
      map.headMap("r").tailMap("m").keySet().add("a");
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  @Test
  public void testAsMapSortedEmpty() {
    SortedSet<String> strings = new NonNavigableSortedSet();
    SortedMap<String, Integer> map = Maps.asMap(strings, LENGTH_FUNCTION);
    assertThat(map.entrySet()).isEmpty();
    assertTrue(map.isEmpty());
    assertNull(map.get("five"));
  }

  @Test
  public void testAsMapNavigable() {
    NavigableSet<String> strings = new java.util.TreeSet<>(asList("one", "two", "three"));
    NavigableMap<String, Integer> map = Maps.asMap(strings, LENGTH_FUNCTION);
    assertEquals(ImmutableMap.of("one", 3, "two", 3, "three", 5), map);
    assertEquals(Integer.valueOf(5), map.get("three"));
    assertNull(map.get("five"));
    assertThat(map.entrySet())
        .containsExactly(mapEntry("one", 3), mapEntry("three", 5), mapEntry("two", 3));
    assertThat(map.tailMap("onea").entrySet())
        .containsExactly(mapEntry("three", 5), mapEntry("two", 3));
    assertThat(map.subMap("one", "two").entrySet())
        .containsExactly(mapEntry("one", 3), mapEntry("three", 5));

    assertEquals(GuavaStandIns.sortedMapOf("two", 3, "three", 5), map.tailMap("three", true));
    assertEquals(GuavaStandIns.sortedMapOf("one", 3, "three", 5), map.headMap("two", false));
    assertEquals(GuavaStandIns.sortedMapOf("three", 5), map.subMap("one", false, "tr", true));

    assertEquals("three", map.higherKey("one"));
    assertEquals("three", map.higherKey("r"));
    assertEquals("three", map.ceilingKey("r"));
    assertEquals("one", map.ceilingKey("one"));
    assertEquals(mapEntry("three", 5), map.higherEntry("one"));
    assertEquals(mapEntry("one", 3), map.ceilingEntry("one"));
    assertEquals("one", map.lowerKey("three"));
    assertEquals("one", map.lowerKey("r"));
    assertEquals("one", map.floorKey("r"));
    assertEquals("three", map.floorKey("three"));

    assertThat(map.descendingMap().entrySet())
        .containsExactly(mapEntry("two", 3), mapEntry("three", 5), mapEntry("one", 3));
    assertEquals(map.headMap("three", true), map.descendingMap().tailMap("three", true));
    assertThat(map.tailMap("three", false).entrySet()).contains(mapEntry("two", 3));
    assertNull(map.tailMap("three", true).lowerEntry("three"));
    assertThat(map.headMap("two", false).values()).containsExactly(3, 5);
    assertThat(map.headMap("two", false).descendingMap().values()).containsExactly(5, 3);
    assertThat(map.descendingKeySet()).containsExactly("two", "three", "one");

    assertEquals(mapEntry("one", 3), map.pollFirstEntry());
    assertEquals(mapEntry("two", 3), map.pollLastEntry());
    assertEquals(1, map.size());
  }

  @Test
  public void testAsMapNavigableReadsThrough() {
    NavigableSet<String> strings = new java.util.TreeSet<>();
    Collections.addAll(strings, "one", "two", "three");
    NavigableMap<String, Integer> map = Maps.asMap(strings, LENGTH_FUNCTION);
    assertNull(map.comparator());
    assertEquals(GuavaStandIns.sortedMapOf("one", 3, "two", 3, "three", 5), map);
    assertNull(map.get("four"));
    strings.add("four");
    assertEquals(GuavaStandIns.sortedMapOf("one", 3, "two", 3, "three", 5, "four", 4), map);
    assertEquals(Integer.valueOf(4), map.get("four"));
    SortedMap<String, Integer> headMap = map.headMap("two");
    assertEquals(GuavaStandIns.sortedMapOf("four", 4, "one", 3, "three", 5), headMap);
    strings.add("five");
    strings.remove("one");
    assertEquals(GuavaStandIns.sortedMapOf("five", 4, "four", 4, "three", 5), headMap);
    assertThat(map.entrySet())
        .containsExactly(
            mapEntry("five", 4), mapEntry("four", 4), mapEntry("three", 5), mapEntry("two", 3));

    NavigableMap<String, Integer> tailMap = map.tailMap("s", true);
    NavigableMap<String, Integer> subMap = map.subMap("a", true, "t", false);

    strings.add("six");
    strings.remove("two");
    assertThat(tailMap.entrySet())
        .containsExactly(mapEntry("six", 3), mapEntry("three", 5));
    assertThat(subMap.entrySet())
        .containsExactly(mapEntry("five", 4), mapEntry("four", 4), mapEntry("six", 3));
  }

  @Test
  public void testAsMapNavigableWritesThrough() {
    NavigableSet<String> strings = new java.util.TreeSet<>();
    Collections.addAll(strings, "one", "two", "three");
    NavigableMap<String, Integer> map = Maps.asMap(strings, LENGTH_FUNCTION);
    assertEquals(ImmutableMap.of("one", 3, "two", 3, "three", 5), map);
    assertEquals(Integer.valueOf(3), map.remove("two"));
    assertThat(strings).containsExactly("one", "three");
    assertEquals(mapEntry("three", 5), map.subMap("one", false, "zzz", true).pollLastEntry());
    assertThat(strings).contains("one");
  }

  @Test
  public void testAsMapNavigableSubViewKeySetsDoNotSupportAdd() {
    NavigableMap<String, Integer> map = Maps.asMap(new java.util.TreeSet<String>(), LENGTH_FUNCTION);
    try {
      map.descendingKeySet().add("a");
      fail();
    } catch (UnsupportedOperationException expected) {
    }
    try {
      map.subMap("a", true, "z", false).keySet().add("a");
      fail();
    } catch (UnsupportedOperationException expected) {
    }
    try {
      map.tailMap("a", true).keySet().add("a");
      fail();
    } catch (UnsupportedOperationException expected) {
    }
    try {
      map.headMap("r", true).keySet().add("a");
      fail();
    } catch (UnsupportedOperationException expected) {
    }
    try {
      map.headMap("r", false).tailMap("m", true).keySet().add("a");
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  @Test
  public void testAsMapNavigableEmpty() {
    NavigableSet<String> strings = java.util.Collections.emptyNavigableSet();
    NavigableMap<String, Integer> map = Maps.asMap(strings, LENGTH_FUNCTION);
    assertThat(map.entrySet()).isEmpty();
    assertTrue(map.isEmpty());
    assertNull(map.get("five"));
  }

  @Test
  public void testToMap() {
    Iterable<String> strings = ImmutableList.of("one", "two", "three");
    ImmutableMap<String, Integer> map = Maps.toMap(strings, LENGTH_FUNCTION);
    assertEquals(ImmutableMap.of("one", 3, "two", 3, "three", 5), map);
    assertThat(map.entrySet())
        .containsExactly(mapEntry("one", 3), mapEntry("two", 3), mapEntry("three", 5));
  }

  @Test
  public void testToMapIterator() {
    Iterator<String> strings = ImmutableList.of("one", "two", "three").iterator();
    ImmutableMap<String, Integer> map = Maps.toMap(strings, LENGTH_FUNCTION);
    assertEquals(ImmutableMap.of("one", 3, "two", 3, "three", 5), map);
    assertThat(map.entrySet())
        .containsExactly(mapEntry("one", 3), mapEntry("two", 3), mapEntry("three", 5));
  }

  @Test
  public void testToMapWithDuplicateKeys() {
    Iterable<String> strings = ImmutableList.of("one", "two", "three", "two", "one");
    ImmutableMap<String, Integer> map = Maps.toMap(strings, LENGTH_FUNCTION);
    assertEquals(ImmutableMap.of("one", 3, "two", 3, "three", 5), map);
    assertThat(map.entrySet())
        .containsExactly(mapEntry("one", 3), mapEntry("two", 3), mapEntry("three", 5));
  }

  @Test
  public void testToMapWithNullKeys() {
    Iterable<String> strings = Arrays.asList("one", null, "three");
    try {
      Maps.toMap(strings, GuavaStandIns.constant("foo"));
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void testToMapWithNullValues() {
    Iterable<String> strings = ImmutableList.of("one", "two", "three");
    try {
      Maps.toMap(strings, GuavaStandIns.constant(null));
      fail();
    } catch (NullPointerException expected) {
    }
  }

  private static final BiMap<Integer, String> INT_TO_STRING_MAP =
      new GuavaStandIns.BiMapBuilder<Integer, String>()
          .put(1, "one")
          .put(2, "two")
          .put(3, "three")
          .build();

  @Test
  public void testUniqueIndexCollection() {
    ImmutableMap<Integer, String> outputMap =
        Maps.uniqueIndex(INT_TO_STRING_MAP.values(), GuavaStandIns.forMap(INT_TO_STRING_MAP.inverse()));
    assertEquals(INT_TO_STRING_MAP, outputMap);
  }

  @Test
  public void testUniqueIndexIterable() {
    ImmutableMap<Integer, String> outputMap =
        Maps.uniqueIndex(
            new Iterable<String>() {
              @Override
              public Iterator<String> iterator() {
                return INT_TO_STRING_MAP.values().iterator();
              }
            },
            GuavaStandIns.forMap(INT_TO_STRING_MAP.inverse()));
    assertEquals(INT_TO_STRING_MAP, outputMap);
  }

  @Test
  public void testUniqueIndexIterator() {
    ImmutableMap<Integer, String> outputMap =
        Maps.uniqueIndex(
            INT_TO_STRING_MAP.values().iterator(), GuavaStandIns.forMap(INT_TO_STRING_MAP.inverse()));
    assertEquals(INT_TO_STRING_MAP, outputMap);
  }

  /** Can't create the map if more than one value maps to the same key. */
  @Test
  public void testUniqueIndexDuplicates() {
    try {
      Map<Integer, String> unused =
          Maps.uniqueIndex(ImmutableSet.of("one", "uno"), GuavaStandIns.constant(1));
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected.getMessage()).contains("Multimaps.index");
    }
  }

  /** Null values are not allowed. */
  @Test
  public void testUniqueIndexNullValue() {
    List<String> listWithNull = Lists.newArrayList((String) null);
    try {
      Maps.uniqueIndex(listWithNull, GuavaStandIns.constant(1));
      fail();
    } catch (NullPointerException expected) {
    }
  }

  /** Null keys aren't allowed either. */
  @Test
  public void testUniqueIndexNullKey() {
    List<String> oneStringList = Lists.newArrayList("foo");
    try {
      Maps.uniqueIndex(oneStringList, GuavaStandIns.constant(null));
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @SuppressWarnings("deprecation") // StringBufferInputStream
  @Test
  public void testFromProperties() throws IOException {
    Properties testProp = new Properties();

    Map<String, String> result = Maps.fromProperties(testProp);
    assertTrue(result.isEmpty());
    testProp.setProperty("first", "true");

    result = Maps.fromProperties(testProp);
    assertEquals("true", result.get("first"));
    assertEquals(1, result.size());
    testProp.setProperty("second", "null");

    result = Maps.fromProperties(testProp);
    assertEquals("true", result.get("first"));
    assertEquals("null", result.get("second"));
    assertEquals(2, result.size());

    // Now test values loaded from a stream.
    String props = "test\n second = 2\n Third item :   a short  phrase   ";

    testProp.load(new StringReader(props));

    result = Maps.fromProperties(testProp);
    assertEquals(4, result.size());
    assertEquals("true", result.get("first"));
    assertEquals("", result.get("test"));
    assertEquals("2", result.get("second"));
    assertEquals("item :   a short  phrase   ", result.get("Third"));
    assertFalse(result.containsKey("not here"));

    // Test loading system properties
    result = Maps.fromProperties(System.getProperties());
    assertTrue(result.containsKey("java.version"));

    // Test that defaults work, too.
    testProp = new Properties(System.getProperties());
    String override = "test\njava.version : hidden";

    testProp.load(new StringReader(override));

    result = Maps.fromProperties(testProp);
    assertTrue(result.size() > 2);
    assertEquals("", result.get("test"));
    assertEquals("hidden", result.get("java.version"));
    assertNotSame(System.getProperty("java.version"), result.get("java.version"));
  }

  @SuppressWarnings("serial") // never serialized
  @Test
  public void testFromPropertiesNullKey() {
    Properties properties =
        new Properties() {
          @Override
          public Enumeration<?> propertyNames() {
            return java.util.Collections.enumeration(com.simplelibs.lava.collect.testing.Helpers.copyToList(Arrays.asList(null, "first", "second")));
          }
        };
    properties.setProperty("first", "true");
    properties.setProperty("second", "null");

    try {
      Maps.fromProperties(properties);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @SuppressWarnings("serial") // never serialized
  @Test
  public void testFromPropertiesNonStringKeys() {
    Properties properties =
        new Properties() {
          @Override
          public Enumeration<?> propertyNames() {
            return java.util.Collections.enumeration(com.simplelibs.lava.collect.testing.Helpers.copyToList(
                Arrays.<Object>asList(Integer.valueOf(123), "first")));
          }
        };

    try {
      Maps.fromProperties(properties);
      fail();
    } catch (ClassCastException expected) {
    }
  }

  @Test
  public void testAsConverter_nominal() throws Exception {
    BiMap<String, Integer> biMap =
        GuavaStandIns.biMapOf(
            "one", 1,
            "two", 2);
    Converter<String, Integer> converter = Maps.asConverter(biMap);
    for (Entry<String, Integer> entry : biMap.entrySet()) {
      assertSame(entry.getValue(), converter.convert(entry.getKey()));
    }
  }

  @Test
  public void testAsConverter_inverse() throws Exception {
    BiMap<String, Integer> biMap =
        GuavaStandIns.biMapOf(
            "one", 1,
            "two", 2);
    Converter<String, Integer> converter = Maps.asConverter(biMap);
    for (Entry<String, Integer> entry : biMap.entrySet()) {
      assertSame(entry.getKey(), converter.reverse().convert(entry.getValue()));
    }
  }

  @Test
  public void testAsConverter_noMapping() throws Exception {
    BiMap<String, Integer> biMap =
        GuavaStandIns.biMapOf(
            "one", 1,
            "two", 2);
    Converter<String, Integer> converter = Maps.asConverter(biMap);
    try {
      converter.convert("three");
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testAsConverter_nullConversions() throws Exception {
    BiMap<String, Integer> biMap =
        GuavaStandIns.biMapOf(
            "one", 1,
            "two", 2);
    Converter<String, Integer> converter = Maps.asConverter(biMap);
    assertNull(converter.convert(null));
    assertNull(converter.reverse().convert(null));
  }

  @Test
  public void testAsConverter_isAView() throws Exception {
    BiMap<String, Integer> biMap = HashBiMap.create();
    biMap.put("one", 1);
    biMap.put("two", 2);
    Converter<String, Integer> converter = Maps.asConverter(biMap);

    assertSame(1, converter.convert("one"));
    assertSame(2, converter.convert("two"));
    try {
      converter.convert("three");
      fail();
    } catch (IllegalArgumentException expected) {
    }

    biMap.put("three", 3);

    assertSame(1, converter.convert("one"));
    assertSame(2, converter.convert("two"));
    assertSame(3, converter.convert("three"));
  }

  @Test
  public void testAsConverter_withNullMapping() throws Exception {
    BiMap<String, Integer> biMap = HashBiMap.create();
    biMap.put("one", 1);
    biMap.put("two", 2);
    biMap.put("three", null);
    try {
      Maps.asConverter(biMap).convert("three");
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testAsConverter_toString() {
    BiMap<String, Integer> biMap =
        GuavaStandIns.biMapOf(
            "one", 1,
            "two", 2);
    Converter<String, Integer> converter = Maps.asConverter(biMap);
    assertEquals("Maps.asConverter({one=1, two=2})", converter.toString());
  }

  @Test
  public void testAsConverter_serialization() {
    BiMap<String, Integer> biMap =
        GuavaStandIns.biMapOf(
            "one", 1,
            "two", 2);
    Converter<String, Integer> converter = Maps.asConverter(biMap);
    SerializableTester.reserializeAndAssert(converter);
  }

  @Test
  public void testUnmodifiableBiMap() {
    BiMap<Integer, String> mod = HashBiMap.create();
    mod.put(1, "one");
    mod.put(2, "two");
    mod.put(3, "three");

    BiMap<Number, String> unmod = Maps.<Number, String>unmodifiableBiMap(mod);

    /* No aliasing on inverse operations. */
    assertSame(unmod.inverse(), unmod.inverse());
    assertSame(unmod, unmod.inverse().inverse());

    /* Unmodifiable is a view. */
    mod.put(4, "four");
    assertEquals(true, unmod.get(4).equals("four"));
    assertEquals(true, unmod.inverse().get("four").equals(4));

    /* UnsupportedOperationException on direct modifications. */
    try {
      unmod.put(4, "four");
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }
    try {
      unmod.forcePut(4, "four");
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }
    try {
      unmod.putAll(Collections.singletonMap(4, "four"));
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }

    /* UnsupportedOperationException on indirect modifications. */
    BiMap<String, Number> inverse = unmod.inverse();
    try {
      inverse.put("four", 4);
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }
    try {
      inverse.forcePut("four", 4);
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }
    try {
      inverse.putAll(Collections.singletonMap("four", 4));
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }
    Set<String> values = unmod.values();
    try {
      values.remove("four");
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }
    Set<Entry<Number, String>> entries = unmod.entrySet();
    Entry<Number, String> entry = entries.iterator().next();
    try {
      entry.setValue("four");
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }
    @SuppressWarnings("unchecked")
    Entry<Integer, String> entry2 = (Entry<Integer, String>) entries.toArray()[0];
    try {
      entry2.setValue("four");
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }
  }

  @Test
  public void testImmutableEntry() {
    Entry<String, Integer> e = Maps.immutableEntry("foo", 1);
    assertEquals("foo", e.getKey());
    assertEquals(1, (int) e.getValue());
    try {
      e.setValue(2);
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }
    assertEquals("foo=1", e.toString());
    assertEquals(101575, e.hashCode());
  }

  @Test
  public void testImmutableEntryNull() {
    Entry<String, Integer> e = Maps.immutableEntry((String) null, (Integer) null);
    assertNull(e.getKey());
    assertNull(e.getValue());
    try {
      e.setValue(null);
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }
    assertEquals("null=null", e.toString());
    assertEquals(0, e.hashCode());
  }

  /** See {@link SynchronizedBiMapTest} for more tests. */
  @Test
  public void testSynchronizedBiMap() {
    BiMap<String, Integer> bimap = HashBiMap.create();
    bimap.put("one", 1);
    BiMap<String, Integer> sync = Maps.synchronizedBiMap(bimap);
    bimap.put("two", 2);
    sync.put("three", 3);
    assertEquals(ImmutableSet.of(1, 2, 3), bimap.inverse().keySet());
    assertEquals(ImmutableSet.of(1, 2, 3), sync.inverse().keySet());
  }

  private static final Predicate<String> NOT_LENGTH_3 =
      new Predicate<String>() {
        @Override
        public boolean test(String input) {
          return input == null || input.length() != 3;
        }
      };

  private static final Predicate<Integer> EVEN =
      new Predicate<Integer>() {
        @Override
        public boolean test(Integer input) {
          return input == null || input % 2 == 0;
        }
      };

  private static final Predicate<Entry<String, Integer>> CORRECT_LENGTH =
      new Predicate<Entry<String, Integer>>() {
        @Override
        public boolean test(Entry<String, Integer> input) {
          return input.getKey().length() == input.getValue();
        }
      };

  private static final Function<Integer, Double> SQRT_FUNCTION =
      new Function<Integer, Double>() {
        @Override
        public Double apply(Integer in) {
          return Math.sqrt(in);
        }
      };

  public static class FilteredMapTest extends LavaTestCase {
    Map<String, Integer> createUnfiltered() {
      return Maps.newHashMap();
    }

    @Test
    public void testFilteredKeysIllegalPut() {
      Map<String, Integer> unfiltered = createUnfiltered();
      Map<String, Integer> filtered = Maps.filterKeys(unfiltered, NOT_LENGTH_3);
      filtered.put("a", 1);
      filtered.put("b", 2);
      assertEquals(ImmutableMap.of("a", 1, "b", 2), filtered);

      try {
        filtered.put("yyy", 3);
        fail();
      } catch (IllegalArgumentException expected) {
      }
    }

    @Test
    public void testFilteredKeysIllegalPutAll() {
      Map<String, Integer> unfiltered = createUnfiltered();
      Map<String, Integer> filtered = Maps.filterKeys(unfiltered, NOT_LENGTH_3);
      filtered.put("a", 1);
      filtered.put("b", 2);
      assertEquals(ImmutableMap.of("a", 1, "b", 2), filtered);

      try {
        filtered.putAll(ImmutableMap.of("c", 3, "zzz", 4, "b", 5));
        fail();
      } catch (IllegalArgumentException expected) {
      }

      assertEquals(ImmutableMap.of("a", 1, "b", 2), filtered);
    }

    @Test
    public void testFilteredKeysFilteredReflectsBackingChanges() {
      Map<String, Integer> unfiltered = createUnfiltered();
      Map<String, Integer> filtered = Maps.filterKeys(unfiltered, NOT_LENGTH_3);
      unfiltered.put("two", 2);
      unfiltered.put("three", 3);
      unfiltered.put("four", 4);
      assertEquals(ImmutableMap.of("two", 2, "three", 3, "four", 4), unfiltered);
      assertEquals(ImmutableMap.of("three", 3, "four", 4), filtered);

      unfiltered.remove("three");
      assertEquals(ImmutableMap.of("two", 2, "four", 4), unfiltered);
      assertEquals(ImmutableMap.of("four", 4), filtered);

      unfiltered.clear();
      assertEquals(ImmutableMap.of(), unfiltered);
      assertEquals(ImmutableMap.of(), filtered);
    }

    @Test
    public void testFilteredValuesIllegalPut() {
      Map<String, Integer> unfiltered = createUnfiltered();
      Map<String, Integer> filtered = Maps.filterValues(unfiltered, EVEN);
      filtered.put("a", 2);
      unfiltered.put("b", 4);
      unfiltered.put("c", 5);
      assertEquals(ImmutableMap.of("a", 2, "b", 4), filtered);

      try {
        filtered.put("yyy", 3);
        fail();
      } catch (IllegalArgumentException expected) {
      }
      assertEquals(ImmutableMap.of("a", 2, "b", 4), filtered);
    }

    @Test
    public void testFilteredValuesIllegalPutAll() {
      Map<String, Integer> unfiltered = createUnfiltered();
      Map<String, Integer> filtered = Maps.filterValues(unfiltered, EVEN);
      filtered.put("a", 2);
      unfiltered.put("b", 4);
      unfiltered.put("c", 5);
      assertEquals(ImmutableMap.of("a", 2, "b", 4), filtered);

      try {
        filtered.putAll(ImmutableMap.of("c", 4, "zzz", 5, "b", 6));
        fail();
      } catch (IllegalArgumentException expected) {
      }
      assertEquals(ImmutableMap.of("a", 2, "b", 4), filtered);
    }

    @Test
    public void testFilteredValuesIllegalSetValue() {
      Map<String, Integer> unfiltered = createUnfiltered();
      Map<String, Integer> filtered = Maps.filterValues(unfiltered, EVEN);
      filtered.put("a", 2);
      filtered.put("b", 4);
      assertEquals(ImmutableMap.of("a", 2, "b", 4), filtered);

      Entry<String, Integer> entry = filtered.entrySet().iterator().next();
      try {
        entry.setValue(5);
        fail();
      } catch (IllegalArgumentException expected) {
      }

      assertEquals(ImmutableMap.of("a", 2, "b", 4), filtered);
    }

    @Test
    public void testFilteredValuesClear() {
      Map<String, Integer> unfiltered = createUnfiltered();
      unfiltered.put("one", 1);
      unfiltered.put("two", 2);
      unfiltered.put("three", 3);
      unfiltered.put("four", 4);
      Map<String, Integer> filtered = Maps.filterValues(unfiltered, EVEN);
      assertEquals(ImmutableMap.of("one", 1, "two", 2, "three", 3, "four", 4), unfiltered);
      assertEquals(ImmutableMap.of("two", 2, "four", 4), filtered);

      filtered.clear();
      assertEquals(ImmutableMap.of("one", 1, "three", 3), unfiltered);
      assertTrue(filtered.isEmpty());
    }

    @Test
    public void testFilteredEntriesIllegalPut() {
      Map<String, Integer> unfiltered = createUnfiltered();
      unfiltered.put("cat", 3);
      unfiltered.put("dog", 2);
      unfiltered.put("horse", 5);
      Map<String, Integer> filtered = Maps.filterEntries(unfiltered, CORRECT_LENGTH);
      assertEquals(ImmutableMap.of("cat", 3, "horse", 5), filtered);

      filtered.put("chicken", 7);
      assertEquals(ImmutableMap.of("cat", 3, "horse", 5, "chicken", 7), filtered);

      try {
        filtered.put("cow", 7);
        fail();
      } catch (IllegalArgumentException expected) {
      }
      assertEquals(ImmutableMap.of("cat", 3, "horse", 5, "chicken", 7), filtered);
    }

    @Test
    public void testFilteredEntriesIllegalPutAll() {
      Map<String, Integer> unfiltered = createUnfiltered();
      unfiltered.put("cat", 3);
      unfiltered.put("dog", 2);
      unfiltered.put("horse", 5);
      Map<String, Integer> filtered = Maps.filterEntries(unfiltered, CORRECT_LENGTH);
      assertEquals(ImmutableMap.of("cat", 3, "horse", 5), filtered);

      filtered.put("chicken", 7);
      assertEquals(ImmutableMap.of("cat", 3, "horse", 5, "chicken", 7), filtered);

      try {
        filtered.putAll(ImmutableMap.of("sheep", 5, "cow", 7));
        fail();
      } catch (IllegalArgumentException expected) {
      }
      assertEquals(ImmutableMap.of("cat", 3, "horse", 5, "chicken", 7), filtered);
    }

    @Test
    public void testFilteredEntriesObjectPredicate() {
      Map<String, Integer> unfiltered = createUnfiltered();
      unfiltered.put("cat", 3);
      unfiltered.put("dog", 2);
      unfiltered.put("horse", 5);
      Predicate<Object> predicate = x -> false;
      Map<String, Integer> filtered = Maps.filterEntries(unfiltered, predicate);
      assertTrue(filtered.isEmpty());
    }

    @Test
    public void testFilteredEntriesWildCardEntryPredicate() {
      Map<String, Integer> unfiltered = createUnfiltered();
      unfiltered.put("cat", 3);
      unfiltered.put("dog", 2);
      unfiltered.put("horse", 5);
      Predicate<Entry<?, ?>> predicate =
          new Predicate<Entry<?, ?>>() {
            @Override
            public boolean test(Entry<?, ?> input) {
              return "cat".equals(input.getKey()) || Integer.valueOf(2) == input.getValue();
            }
          };
      Map<String, Integer> filtered = Maps.filterEntries(unfiltered, predicate);
      assertEquals(ImmutableMap.of("cat", 3, "dog", 2), filtered);
    }
  }

  public static class FilteredSortedMapTest extends FilteredMapTest {
    @Override
    SortedMap<String, Integer> createUnfiltered() {
      return Maps.newTreeMap();
    }

    @Test
    public void testFirstAndLastKeyFilteredMap() {
      SortedMap<String, Integer> unfiltered = createUnfiltered();
      unfiltered.put("apple", 2);
      unfiltered.put("banana", 6);
      unfiltered.put("cat", 3);
      unfiltered.put("dog", 5);

      SortedMap<String, Integer> filtered = Maps.filterEntries(unfiltered, CORRECT_LENGTH);
      assertEquals("banana", filtered.firstKey());
      assertEquals("cat", filtered.lastKey());
    }

    @Test
    public void testHeadSubTailMap_FilteredMap() {
      SortedMap<String, Integer> unfiltered = createUnfiltered();
      unfiltered.put("apple", 2);
      unfiltered.put("banana", 6);
      unfiltered.put("cat", 4);
      unfiltered.put("dog", 3);
      SortedMap<String, Integer> filtered = Maps.filterEntries(unfiltered, CORRECT_LENGTH);

      assertEquals(ImmutableMap.of("banana", 6), filtered.headMap("dog"));
      assertEquals(ImmutableMap.of(), filtered.headMap("banana"));
      assertEquals(ImmutableMap.of("banana", 6, "dog", 3), filtered.headMap("emu"));

      assertEquals(ImmutableMap.of("banana", 6), filtered.subMap("banana", "dog"));
      assertEquals(ImmutableMap.of("dog", 3), filtered.subMap("cat", "emu"));

      assertEquals(ImmutableMap.of("dog", 3), filtered.tailMap("cat"));
      assertEquals(ImmutableMap.of("banana", 6, "dog", 3), filtered.tailMap("banana"));
    }
  }

  public static class FilteredBiMapTest extends FilteredMapTest {
    @Override
    BiMap<String, Integer> createUnfiltered() {
      return HashBiMap.create();
    }
  }

  @Test
  public void testTransformValues() {
    Map<String, Integer> map = ImmutableMap.of("a", 4, "b", 9);
    Map<String, Double> transformed = transformValues(map, SQRT_FUNCTION);

    assertEquals(ImmutableMap.of("a", 2.0, "b", 3.0), transformed);
  }

  @Test
  public void testTransformEntries() {
    Map<String, String> map = ImmutableMap.of("a", "4", "b", "9");
    EntryTransformer<String, String, String> concat =
        new EntryTransformer<String, String, String>() {
          @Override
          public String transformEntry(String key, String value) {
            return key + value;
          }
        };
    Map<String, String> transformed = transformEntries(map, concat);

    assertEquals(ImmutableMap.of("a", "a4", "b", "b9"), transformed);
  }

  @SuppressWarnings("unused")
  @Test
  public void testTransformEntriesGenerics() {
    Map<Object, Object> map1 = ImmutableMap.<Object, Object>of(1, 2);
    Map<Object, Number> map2 = ImmutableMap.<Object, Number>of(1, 2);
    Map<Object, Integer> map3 = ImmutableMap.<Object, Integer>of(1, 2);
    Map<Number, Object> map4 = ImmutableMap.<Number, Object>of(1, 2);
    Map<Number, Number> map5 = ImmutableMap.<Number, Number>of(1, 2);
    Map<Number, Integer> map6 = ImmutableMap.<Number, Integer>of(1, 2);
    Map<Integer, Object> map7 = ImmutableMap.<Integer, Object>of(1, 2);
    Map<Integer, Number> map8 = ImmutableMap.<Integer, Number>of(1, 2);
    Map<Integer, Integer> map9 = ImmutableMap.<Integer, Integer>of(1, 2);
    Map<? extends Number, ? extends Number> map0 = ImmutableMap.of(1, 2);

    EntryTransformer<Number, Number, Double> transformer =
        new EntryTransformer<Number, Number, Double>() {
          @Override
          public Double transformEntry(Number key, Number value) {
            return key.doubleValue() + value.doubleValue();
          }
        };

    Map<Object, Double> objectKeyed;
    Map<Number, Double> numberKeyed;
    Map<Integer, Double> integerKeyed;

    numberKeyed = transformEntries(map5, transformer);
    numberKeyed = transformEntries(map6, transformer);
    integerKeyed = transformEntries(map8, transformer);
    integerKeyed = transformEntries(map9, transformer);

    Map<? extends Number, Double> wildcarded = transformEntries(map0, transformer);

    // Can't loosen the key type:
    // objectKeyed = transformEntries(map5, transformer);
    // objectKeyed = transformEntries(map6, transformer);
    // objectKeyed = transformEntries(map8, transformer);
    // objectKeyed = transformEntries(map9, transformer);
    // numberKeyed = transformEntries(map8, transformer);
    // numberKeyed = transformEntries(map9, transformer);

    // Can't loosen the value type:
    // Map<Number, Number> looseValued1 = transformEntries(map5, transformer);
    // Map<Number, Number> looseValued2 = transformEntries(map6, transformer);
    // Map<Integer, Number> looseValued3 = transformEntries(map8, transformer);
    // Map<Integer, Number> looseValued4 = transformEntries(map9, transformer);

    // Can't call with too loose a key:
    // transformEntries(map1, transformer);
    // transformEntries(map2, transformer);
    // transformEntries(map3, transformer);

    // Can't call with too loose a value:
    // transformEntries(map1, transformer);
    // transformEntries(map4, transformer);
    // transformEntries(map7, transformer);
  }

  @Test
  public void testTransformEntriesExample() {
    Map<String, Boolean> options = ImmutableMap.of("verbose", true, "sort", false);
    EntryTransformer<String, Boolean, String> flagPrefixer =
        new EntryTransformer<String, Boolean, String>() {
          @Override
          public String transformEntry(String key, Boolean value) {
            return value ? key : "no" + key;
          }
        };
    Map<String, String> transformed = transformEntries(options, flagPrefixer);
    assertEquals("{verbose=verbose, sort=nosort}", transformed.toString());
  }

  // Logically this would accept a NavigableMap, but that won't work under GWT.
  private static <K, V> SortedMap<K, V> sortedNotNavigable(final SortedMap<K, V> map) {
    return new ForwardingSortedMap<K, V>() {
      @Override
      protected SortedMap<K, V> delegate() {
        return map;
      }
    };
  }

  @Test
  public void testSortedMapTransformValues() {
    SortedMap<String, Integer> map = sortedNotNavigable(GuavaStandIns.sortedMapOf("a", 4, "b", 9));
    SortedMap<String, Double> transformed = transformValues(map, SQRT_FUNCTION);

    /*
     * We'd like to sanity check that we didn't get a NavigableMap out, but we
     * can't easily do so while maintaining GWT compatibility.
     */
    assertEquals(GuavaStandIns.sortedMapOf("a", 2.0, "b", 3.0), transformed);
  }

  @Test
  public void testNavigableMapTransformValues() {
    NavigableMap<String, Integer> map = GuavaStandIns.sortedMapOf("a", 4, "b", 9);
    NavigableMap<String, Double> transformed = transformValues(map, SQRT_FUNCTION);

    assertEquals(GuavaStandIns.sortedMapOf("a", 2.0, "b", 3.0), transformed);
  }

  @Test
  public void testSortedMapTransformEntries() {
    SortedMap<String, String> map = sortedNotNavigable(GuavaStandIns.sortedMapOf("a", "4", "b", "9"));
    EntryTransformer<String, String, String> concat =
        new EntryTransformer<String, String, String>() {
          @Override
          public String transformEntry(String key, String value) {
            return key + value;
          }
        };
    SortedMap<String, String> transformed = transformEntries(map, concat);

    /*
     * We'd like to sanity check that we didn't get a NavigableMap out, but we
     * can't easily do so while maintaining GWT compatibility.
     */
    assertEquals(GuavaStandIns.sortedMapOf("a", "a4", "b", "b9"), transformed);
  }

  @Test
  public void testNavigableMapTransformEntries() {
    NavigableMap<String, String> map = GuavaStandIns.sortedMapOf("a", "4", "b", "9");
    EntryTransformer<String, String, String> concat =
        new EntryTransformer<String, String, String>() {
          @Override
          public String transformEntry(String key, String value) {
            return key + value;
          }
        };
    NavigableMap<String, String> transformed = transformEntries(map, concat);

    assertEquals(GuavaStandIns.sortedMapOf("a", "a4", "b", "b9"), transformed);
  }

  @Test
  public void testUnmodifiableNavigableMap() {
    TreeMap<Integer, String> mod = Maps.newTreeMap();
    mod.put(1, "one");
    mod.put(2, "two");
    mod.put(3, "three");

    NavigableMap<Integer, String> unmod = unmodifiableNavigableMap(mod);

    /* unmod is a view. */
    mod.put(4, "four");
    assertEquals("four", unmod.get(4));
    assertEquals("four", unmod.descendingMap().get(4));

    ensureNotDirectlyModifiable(unmod);
    ensureNotDirectlyModifiable(unmod.descendingMap());
    ensureNotDirectlyModifiable(unmod.headMap(2, true));
    ensureNotDirectlyModifiable(unmod.subMap(1, true, 3, true));
    ensureNotDirectlyModifiable(unmod.tailMap(2, true));

    Collection<String> values = unmod.values();
    try {
      values.add("4");
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }
    try {
      values.remove("four");
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }
    try {
      values.removeAll(Collections.singleton("four"));
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }
    try {
      values.retainAll(Collections.singleton("four"));
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }
    try {
      Iterator<String> iterator = values.iterator();
      iterator.next();
      iterator.remove();
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }

    Set<Entry<Integer, String>> entries = unmod.entrySet();
    try {
      Iterator<Entry<Integer, String>> iterator = entries.iterator();
      iterator.next();
      iterator.remove();
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }
    Entry<Integer, String> entry = entries.iterator().next();
    try {
      entry.setValue("four");
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }
    entry = unmod.lowerEntry(1);
    assertNull(entry);
    entry = unmod.floorEntry(2);
    try {
      entry.setValue("four");
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }
    entry = unmod.ceilingEntry(2);
    try {
      entry.setValue("four");
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }
    entry = unmod.lowerEntry(2);
    try {
      entry.setValue("four");
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }
    entry = unmod.higherEntry(2);
    try {
      entry.setValue("four");
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }
    entry = unmod.firstEntry();
    try {
      entry.setValue("four");
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }
    entry = unmod.lastEntry();
    try {
      entry.setValue("four");
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }
    @SuppressWarnings("unchecked")
    Entry<Integer, String> entry2 = (Entry<Integer, String>) entries.toArray()[0];
    try {
      entry2.setValue("four");
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }
  }

  void ensureNotDirectlyModifiable(NavigableMap<Integer, String> unmod) {
    try {
      unmod.put(4, "four");
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }
    try {
      unmod.putAll(Collections.singletonMap(4, "four"));
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }
    try {
      unmod.remove(4);
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }
    try {
      unmod.pollFirstEntry();
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }
    try {
      unmod.pollLastEntry();
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException expected) {
    }
  }

  @Test
  public void testSubMap_boundedRange() {
    NavigableMap<Integer, Integer> map = GuavaStandIns.sortedMapOf(2, 0, 4, 0, 6, 0, 8, 0, 10, 0);
    NavigableMap<Integer, Integer> empty = GuavaStandIns.sortedMapOf();

    assertEquals(map, Maps.subMap(map, Range.closed(0, 12)));
    assertEquals(GuavaStandIns.sortedMapOf(2, 0, 4, 0), Maps.subMap(map, Range.closed(0, 4)));
    assertEquals(GuavaStandIns.sortedMapOf(2, 0, 4, 0, 6, 0), Maps.subMap(map, Range.closed(2, 6)));
    assertEquals(GuavaStandIns.sortedMapOf(4, 0, 6, 0), Maps.subMap(map, Range.closed(3, 7)));
    assertEquals(empty, Maps.subMap(map, Range.closed(20, 30)));

    assertEquals(map, Maps.subMap(map, Range.open(0, 12)));
    assertEquals(GuavaStandIns.sortedMapOf(2, 0), Maps.subMap(map, Range.open(0, 4)));
    assertEquals(GuavaStandIns.sortedMapOf(4, 0), Maps.subMap(map, Range.open(2, 6)));
    assertEquals(GuavaStandIns.sortedMapOf(4, 0, 6, 0), Maps.subMap(map, Range.open(3, 7)));
    assertEquals(empty, Maps.subMap(map, Range.open(20, 30)));

    assertEquals(map, Maps.subMap(map, Range.openClosed(0, 12)));
    assertEquals(GuavaStandIns.sortedMapOf(2, 0, 4, 0), Maps.subMap(map, Range.openClosed(0, 4)));
    assertEquals(GuavaStandIns.sortedMapOf(4, 0, 6, 0), Maps.subMap(map, Range.openClosed(2, 6)));
    assertEquals(GuavaStandIns.sortedMapOf(4, 0, 6, 0), Maps.subMap(map, Range.openClosed(3, 7)));
    assertEquals(empty, Maps.subMap(map, Range.openClosed(20, 30)));

    assertEquals(map, Maps.subMap(map, Range.closedOpen(0, 12)));
    assertEquals(GuavaStandIns.sortedMapOf(2, 0), Maps.subMap(map, Range.closedOpen(0, 4)));
    assertEquals(GuavaStandIns.sortedMapOf(2, 0, 4, 0), Maps.subMap(map, Range.closedOpen(2, 6)));
    assertEquals(GuavaStandIns.sortedMapOf(4, 0, 6, 0), Maps.subMap(map, Range.closedOpen(3, 7)));
    assertEquals(empty, Maps.subMap(map, Range.closedOpen(20, 30)));
  }

  @Test
  public void testSubMap_halfBoundedRange() {
    NavigableMap<Integer, Integer> map = GuavaStandIns.sortedMapOf(2, 0, 4, 0, 6, 0, 8, 0, 10, 0);
    NavigableMap<Integer, Integer> empty = GuavaStandIns.sortedMapOf();

    assertEquals(map, Maps.subMap(map, Range.atLeast(0)));
    assertEquals(
        GuavaStandIns.sortedMapOf(4, 0, 6, 0, 8, 0, 10, 0), Maps.subMap(map, Range.atLeast(4)));
    assertEquals(GuavaStandIns.sortedMapOf(8, 0, 10, 0), Maps.subMap(map, Range.atLeast(7)));
    assertEquals(empty, Maps.subMap(map, Range.atLeast(20)));

    assertEquals(map, Maps.subMap(map, Range.greaterThan(0)));
    assertEquals(GuavaStandIns.sortedMapOf(6, 0, 8, 0, 10, 0), Maps.subMap(map, Range.greaterThan(4)));
    assertEquals(GuavaStandIns.sortedMapOf(8, 0, 10, 0), Maps.subMap(map, Range.greaterThan(7)));
    assertEquals(empty, Maps.subMap(map, Range.greaterThan(20)));

    assertEquals(empty, Maps.subMap(map, Range.lessThan(0)));
    assertEquals(GuavaStandIns.sortedMapOf(2, 0), Maps.subMap(map, Range.lessThan(4)));
    assertEquals(GuavaStandIns.sortedMapOf(2, 0, 4, 0, 6, 0), Maps.subMap(map, Range.lessThan(7)));
    assertEquals(map, Maps.subMap(map, Range.lessThan(20)));

    assertEquals(empty, Maps.subMap(map, Range.atMost(0)));
    assertEquals(GuavaStandIns.sortedMapOf(2, 0, 4, 0), Maps.subMap(map, Range.atMost(4)));
    assertEquals(GuavaStandIns.sortedMapOf(2, 0, 4, 0, 6, 0), Maps.subMap(map, Range.atMost(7)));
    assertEquals(map, Maps.subMap(map, Range.atMost(20)));
  }

  @Test
  public void testSubMap_unboundedRange() {
    NavigableMap<Integer, Integer> map = GuavaStandIns.sortedMapOf(2, 0, 4, 0, 6, 0, 8, 0, 10, 0);

    assertEquals(map, Maps.subMap(map, Range.<Integer>all()));
  }

  @Test
  public void testSubMap_unnaturalOrdering() {
    NavigableMap<Integer, Integer> map =
        GuavaStandIns.<Integer, Integer>reverseOrderSortedMap()
            .put(2, 0)
            .put(4, 0)
            .put(6, 0)
            .put(8, 0)
            .put(10, 0)
            .build();

    try {
      Maps.subMap(map, Range.closed(4, 8));
      fail("IllegalArgumentException expected");
    } catch (IllegalArgumentException expected) {
    }

    // These results are all incorrect, but there's no way (short of iterating over the result)
    // to verify that with an arbitrary ordering or comparator.
    assertEquals(GuavaStandIns.sortedMapOf(2, 0, 4, 0), Maps.subMap(map, Range.atLeast(4)));
    assertEquals(GuavaStandIns.sortedMapOf(8, 0, 10, 0), Maps.subMap(map, Range.atMost(8)));
    assertEquals(
        GuavaStandIns.sortedMapOf(2, 0, 4, 0, 6, 0, 8, 0, 10, 0),
        Maps.subMap(map, Range.<Integer>all()));
  }
}
