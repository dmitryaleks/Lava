/*
 * Copyright (C) 2008 The Guava Authors
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

import static com.simplelibs.lava.collect.testing.Helpers.mapEntry;
import static com.simplelibs.lava.testing.SerializableTester.reserialize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.simplelibs.lava.base.Equivalence;
import com.simplelibs.lava.checker.nullness.qual.Nullable;
import com.simplelibs.lava.collect.ImmutableMap.Builder;
import com.simplelibs.lava.collect.testing.AnEnum;
import com.simplelibs.lava.collect.testing.Helpers;
import com.simplelibs.lava.collect.testing.MapInterfaceTest;
import com.simplelibs.lava.collect.testing.MinimalSet;
import com.simplelibs.lava.collect.testing.SampleElements.Colliders;
import com.simplelibs.lava.collect.testing.SampleElements.Unhashables;
import com.simplelibs.lava.collect.testing.UnhashableObject;
import com.simplelibs.lava.testing.CollectorTester;
import com.simplelibs.lava.testing.EqualsTester;
import com.simplelibs.lava.testing.LavaTestCase;
import com.simplelibs.lava.testing.NullPointerTester;
import com.simplelibs.lava.testing.SerializableTester;
import java.io.Serializable;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ImmutableMap}.
 *
 * @author Kevin Bourrillion
 * @author Jesse Wilson
 */
public class ImmutableMapTest extends LavaTestCase {

  public abstract static class AbstractMapTests<K, V> extends MapInterfaceTest<K, V> {
    public AbstractMapTests() {
      super(false, false, false, false, false);
    }

    @Override
    protected Map<K, V> makeEmptyMap() {
      throw new UnsupportedOperationException();
    }

    @Override
    protected void assertMoreInvariants(Map<K, V> map) {
      // TODO: can these be moved to MapInterfaceTest?
      for (Entry<K, V> entry : map.entrySet()) {
        assertEquals(entry.getKey() + "=" + entry.getValue(), entry.toString());
      }

      assertEquals("{" + map.entrySet().stream().map(String::valueOf).collect(Collectors.joining(", ")) + "}", map.toString());
      assertEquals("[" + map.entrySet().stream().map(String::valueOf).collect(Collectors.joining(", ")) + "]", map.entrySet().toString());
      assertEquals("[" + map.keySet().stream().map(String::valueOf).collect(Collectors.joining(", ")) + "]", map.keySet().toString());
      assertEquals("[" + map.values().stream().map(String::valueOf).collect(Collectors.joining(", ")) + "]", map.values().toString());

      assertEquals(MinimalSet.from(map.entrySet()), map.entrySet());
      assertEquals(Helpers.newHashSet(map.keySet()), map.keySet());
    }
  }

  public static class MapTests extends AbstractMapTests<String, Integer> {
    @Override
    protected Map<String, Integer> makeEmptyMap() {
      return ImmutableMap.of();
    }

    @Override
    protected Map<String, Integer> makePopulatedMap() {
      return ImmutableMap.of("one", 1, "two", 2, "three", 3);
    }

    @Override
    protected String getKeyNotInPopulatedMap() {
      return "minus one";
    }

    @Override
    protected Integer getValueNotInPopulatedMap() {
      return -1;
    }
  }

  public static class SingletonMapTests extends AbstractMapTests<String, Integer> {
    @Override
    protected Map<String, Integer> makePopulatedMap() {
      return ImmutableMap.of("one", 1);
    }

    @Override
    protected String getKeyNotInPopulatedMap() {
      return "minus one";
    }

    @Override
    protected Integer getValueNotInPopulatedMap() {
      return -1;
    }
  }

  public static class ReserializedMapTests extends AbstractMapTests<String, Integer> {
    @Override
    protected Map<String, Integer> makePopulatedMap() {
      return SerializableTester.reserialize(ImmutableMap.of("one", 1, "two", 2, "three", 3));
    }

    @Override
    protected String getKeyNotInPopulatedMap() {
      return "minus one";
    }

    @Override
    protected Integer getValueNotInPopulatedMap() {
      return -1;
    }
  }

  public static class MapTestsWithBadHashes extends AbstractMapTests<Object, Integer> {

    @Override
    protected Map<Object, Integer> makeEmptyMap() {
      throw new UnsupportedOperationException();
    }

    @Override
    protected Map<Object, Integer> makePopulatedMap() {
      Colliders colliders = new Colliders();
      return ImmutableMap.of(
          colliders.e0(), 0,
          colliders.e1(), 1,
          colliders.e2(), 2,
          colliders.e3(), 3);
    }

    @Override
    protected Object getKeyNotInPopulatedMap() {
      return new Colliders().e4();
    }

    @Override
    protected Integer getValueNotInPopulatedMap() {
      return 4;
    }
  }

  public static class MapTestsWithUnhashableValues
      extends AbstractMapTests<Integer, UnhashableObject> {
    @Override
    protected Map<Integer, UnhashableObject> makeEmptyMap() {
      return ImmutableMap.of();
    }

    @Override
    protected Map<Integer, UnhashableObject> makePopulatedMap() {
      Unhashables unhashables = new Unhashables();
      return ImmutableMap.of(0, unhashables.e0(), 1, unhashables.e1(), 2, unhashables.e2());
    }

    @Override
    protected Integer getKeyNotInPopulatedMap() {
      return 3;
    }

    @Override
    protected UnhashableObject getValueNotInPopulatedMap() {
      return new Unhashables().e3();
    }
  }

  public static class MapTestsWithSingletonUnhashableValue extends MapTestsWithUnhashableValues {
    @Override
    protected Map<Integer, UnhashableObject> makePopulatedMap() {
      Unhashables unhashables = new Unhashables();
      return ImmutableMap.of(0, unhashables.e0());
    }
  }

  public static class CreationTests extends LavaTestCase {
    @Test
    public void testEmptyBuilder() {
      ImmutableMap<String, Integer> map = new Builder<String, Integer>().build();
      assertEquals(Collections.<String, Integer>emptyMap(), map);
    }

    @Test
    public void testSingletonBuilder() {
      ImmutableMap<String, Integer> map = new Builder<String, Integer>().put("one", 1).build();
      assertMapEquals(map, "one", 1);
    }

    @Test
    public void testBuilder() {
      ImmutableMap<String, Integer> map =
          new Builder<String, Integer>()
              .put("one", 1)
              .put("two", 2)
              .put("three", 3)
              .put("four", 4)
              .put("five", 5)
              .build();
      assertMapEquals(map, "one", 1, "two", 2, "three", 3, "four", 4, "five", 5);
    }

    @Test
    public void testBuilderExactlySizedReusesArray() {
      ImmutableMap.Builder<Integer, Integer> builder = ImmutableMap.builderWithExpectedSize(10);
      Entry<Integer, Integer>[] builderArray = builder.entries;
      for (int i = 0; i < 10; i++) {
        builder.put(i, i);
      }
      Entry<Integer, Integer>[] builderArrayAfterPuts = builder.entries;
      RegularImmutableMap<Integer, Integer> map =
          (RegularImmutableMap<Integer, Integer>) builder.build();
      Entry<Integer, Integer>[] mapInternalArray = map.entries;
      assertSame(builderArray, builderArrayAfterPuts);
      assertSame(builderArray, mapInternalArray);
    }

    @Test
    public void testBuilder_orderEntriesByValue() {
      ImmutableMap<String, Integer> map =
          new Builder<String, Integer>()
              .orderEntriesByValue(Comparator.naturalOrder())
              .put("three", 3)
              .put("one", 1)
              .put("five", 5)
              .put("four", 4)
              .put("two", 2)
              .build();
      assertMapEquals(map, "one", 1, "two", 2, "three", 3, "four", 4, "five", 5);
    }

    @Test
    public void testBuilder_orderEntriesByValueAfterExactSizeBuild() {
      Builder<String, Integer> builder =
          new Builder<String, Integer>(2).put("four", 4).put("one", 1);
      ImmutableMap<String, Integer> keyOrdered = builder.build();
      ImmutableMap<String, Integer> valueOrdered =
          builder.orderEntriesByValue(Comparator.naturalOrder()).build();
      assertMapEquals(keyOrdered, "four", 4, "one", 1);
      assertMapEquals(valueOrdered, "one", 1, "four", 4);
    }

    @Test
    public void testBuilder_orderEntriesByValue_usedTwiceFails() {
      ImmutableMap.Builder<String, Integer> builder =
          new Builder<String, Integer>().orderEntriesByValue(Comparator.naturalOrder());
      try {
        builder.orderEntriesByValue(Comparator.naturalOrder());
        fail("Expected IllegalStateException");
      } catch (IllegalStateException expected) {
      }
    }

    @Test
    public void testBuilder_withImmutableEntry() {
      ImmutableMap<String, Integer> map =
          new Builder<String, Integer>().put(Maps.immutableEntry("one", 1)).build();
      assertMapEquals(map, "one", 1);
    }

    @Test
    public void testBuilder_withImmutableEntryAndNullContents() {
      Builder<String, Integer> builder = new Builder<>();
      try {
        builder.put(Maps.immutableEntry("one", (Integer) null));
        fail();
      } catch (NullPointerException expected) {
      }
      try {
        builder.put(Maps.immutableEntry((String) null, 1));
        fail();
      } catch (NullPointerException expected) {
      }
    }

    private static class StringHolder {
      String string;
    }

    @Test
    public void testBuilder_withMutableEntry() {
      ImmutableMap.Builder<String, Integer> builder = new Builder<>();
      final StringHolder holder = new StringHolder();
      holder.string = "one";
      Entry<String, Integer> entry =
          new AbstractMapEntry<String, Integer>() {
            @Override
            public String getKey() {
              return holder.string;
            }

            @Override
            public Integer getValue() {
              return 1;
            }
          };

      builder.put(entry);
      holder.string = "two";
      assertMapEquals(builder.build(), "one", 1);
    }

    @Test
    public void testBuilderPutAllWithEmptyMap() {
      ImmutableMap<String, Integer> map =
          new Builder<String, Integer>().putAll(Collections.<String, Integer>emptyMap()).build();
      assertEquals(Collections.<String, Integer>emptyMap(), map);
    }

    @Test
    public void testBuilderPutAll() {
      Map<String, Integer> toPut = new LinkedHashMap<>();
      toPut.put("one", 1);
      toPut.put("two", 2);
      toPut.put("three", 3);
      Map<String, Integer> moreToPut = new LinkedHashMap<>();
      moreToPut.put("four", 4);
      moreToPut.put("five", 5);

      ImmutableMap<String, Integer> map =
          new Builder<String, Integer>().putAll(toPut).putAll(moreToPut).build();
      assertMapEquals(map, "one", 1, "two", 2, "three", 3, "four", 4, "five", 5);
    }

    @Test
    public void testBuilderReuse() {
      Builder<String, Integer> builder = new Builder<>();
      ImmutableMap<String, Integer> mapOne = builder.put("one", 1).put("two", 2).build();
      ImmutableMap<String, Integer> mapTwo = builder.put("three", 3).put("four", 4).build();

      assertMapEquals(mapOne, "one", 1, "two", 2);
      assertMapEquals(mapTwo, "one", 1, "two", 2, "three", 3, "four", 4);
    }

    @Test
    public void testBuilderPutNullKeyFailsAtomically() {
      Builder<String, Integer> builder = new Builder<>();
      try {
        builder.put(null, 1);
        fail();
      } catch (NullPointerException expected) {
      }
      builder.put("foo", 2);
      assertMapEquals(builder.build(), "foo", 2);
    }

    @Test
    public void testBuilderPutImmutableEntryWithNullKeyFailsAtomically() {
      Builder<String, Integer> builder = new Builder<>();
      try {
        builder.put(Maps.immutableEntry((String) null, 1));
        fail();
      } catch (NullPointerException expected) {
      }
      builder.put("foo", 2);
      assertMapEquals(builder.build(), "foo", 2);
    }

    // for GWT compatibility
    static class SimpleEntry<K, V> extends AbstractMapEntry<K, V> {
      public K key;
      public V value;

      SimpleEntry(K key, V value) {
        this.key = key;
        this.value = value;
      }

      @Override
      public K getKey() {
        return key;
      }

      @Override
      public V getValue() {
        return value;
      }
    }

    @Test
    public void testBuilderPutMutableEntryWithNullKeyFailsAtomically() {
      Builder<String, Integer> builder = new Builder<>();
      try {
        builder.put(new SimpleEntry<String, Integer>(null, 1));
        fail();
      } catch (NullPointerException expected) {
      }
      builder.put("foo", 2);
      assertMapEquals(builder.build(), "foo", 2);
    }

    @Test
    public void testBuilderPutNullKey() {
      Builder<String, Integer> builder = new Builder<>();
      try {
        builder.put(null, 1);
        fail();
      } catch (NullPointerException expected) {
      }
    }

    @Test
    public void testBuilderPutNullValue() {
      Builder<String, Integer> builder = new Builder<>();
      try {
        builder.put("one", null);
        fail();
      } catch (NullPointerException expected) {
      }
    }

    @Test
    public void testBuilderPutNullKeyViaPutAll() {
      Builder<String, Integer> builder = new Builder<>();
      try {
        builder.putAll(Collections.<String, Integer>singletonMap(null, 1));
        fail();
      } catch (NullPointerException expected) {
      }
    }

    @Test
    public void testBuilderPutNullValueViaPutAll() {
      Builder<String, Integer> builder = new Builder<>();
      try {
        builder.putAll(Collections.<String, Integer>singletonMap("one", null));
        fail();
      } catch (NullPointerException expected) {
      }
    }

    @Test
    public void testPuttingTheSameKeyTwiceThrowsOnBuild() {
      Builder<String, Integer> builder =
          new Builder<String, Integer>()
              .put("one", 1)
              .put("one", 1); // throwing on this line would be even better

      try {
        builder.build();
        fail();
      } catch (IllegalArgumentException expected) {
      }
    }

    @Test
    public void testOf() {
      assertMapEquals(ImmutableMap.of("one", 1), "one", 1);
      assertMapEquals(ImmutableMap.of("one", 1, "two", 2), "one", 1, "two", 2);
      assertMapEquals(
          ImmutableMap.of("one", 1, "two", 2, "three", 3), "one", 1, "two", 2, "three", 3);
      assertMapEquals(
          ImmutableMap.of("one", 1, "two", 2, "three", 3, "four", 4),
          "one",
          1,
          "two",
          2,
          "three",
          3,
          "four",
          4);
      assertMapEquals(
          ImmutableMap.of("one", 1, "two", 2, "three", 3, "four", 4, "five", 5),
          "one",
          1,
          "two",
          2,
          "three",
          3,
          "four",
          4,
          "five",
          5);
    }

    @Test
    public void testOfNullKey() {
      try {
        ImmutableMap.of(null, 1);
        fail();
      } catch (NullPointerException expected) {
      }

      try {
        ImmutableMap.of("one", 1, null, 2);
        fail();
      } catch (NullPointerException expected) {
      }
    }

    @Test
    public void testOfNullValue() {
      try {
        ImmutableMap.of("one", null);
        fail();
      } catch (NullPointerException expected) {
      }

      try {
        ImmutableMap.of("one", 1, "two", null);
        fail();
      } catch (NullPointerException expected) {
      }
    }

    @Test
    public void testOfWithDuplicateKey() {
      try {
        ImmutableMap.of("one", 1, "one", 1);
        fail();
      } catch (IllegalArgumentException expected) {
      }
    }

    @Test
    public void testCopyOfEmptyMap() {
      ImmutableMap<String, Integer> copy =
          ImmutableMap.copyOf(Collections.<String, Integer>emptyMap());
      assertEquals(Collections.<String, Integer>emptyMap(), copy);
      assertSame(copy, ImmutableMap.copyOf(copy));
    }

    @Test
    public void testCopyOfSingletonMap() {
      ImmutableMap<String, Integer> copy = ImmutableMap.copyOf(Collections.singletonMap("one", 1));
      assertMapEquals(copy, "one", 1);
      assertSame(copy, ImmutableMap.copyOf(copy));
    }

    @Test
    public void testCopyOf() {
      Map<String, Integer> original = new LinkedHashMap<>();
      original.put("one", 1);
      original.put("two", 2);
      original.put("three", 3);

      ImmutableMap<String, Integer> copy = ImmutableMap.copyOf(original);
      assertMapEquals(copy, "one", 1, "two", 2, "three", 3);
      assertSame(copy, ImmutableMap.copyOf(copy));
    }

    @Test
    public void testToImmutableMap() {
      Collector<Entry<String, Integer>, ?, ImmutableMap<String, Integer>> collector =
          ImmutableMap.toImmutableMap(Entry::getKey, Entry::getValue);
      Equivalence<ImmutableMap<String, Integer>> equivalence =
          Equivalence.equals()
              .<Entry<String, Integer>>pairwise()
              .onResultOf(ImmutableMap::entrySet);
      CollectorTester.of(collector, equivalence)
          .expectCollects(
              ImmutableMap.of("one", 1, "two", 2, "three", 3),
              mapEntry("one", 1),
              mapEntry("two", 2),
              mapEntry("three", 3));
    }

    @Test
    public void testToImmutableMap_exceptionOnDuplicateKey() {
      Collector<Entry<String, Integer>, ?, ImmutableMap<String, Integer>> collector =
          ImmutableMap.toImmutableMap(Entry::getKey, Entry::getValue);
      try {
        Stream.of(mapEntry("one", 1), mapEntry("one", 11)).collect(collector);
        fail("Expected IllegalArgumentException");
      } catch (IllegalArgumentException expected) {
      }
    }

    @Test
    public void testToImmutableMapMerging() {
      Collector<Entry<String, Integer>, ?, ImmutableMap<String, Integer>> collector =
          ImmutableMap.toImmutableMap(Entry::getKey, Entry::getValue, Integer::sum);
      Equivalence<ImmutableMap<String, Integer>> equivalence =
          Equivalence.equals()
              .<Entry<String, Integer>>pairwise()
              .onResultOf(ImmutableMap::entrySet);
      CollectorTester.of(collector, equivalence)
          .expectCollects(
              ImmutableMap.of("one", 1, "two", 4, "three", 3),
              mapEntry("one", 1),
              mapEntry("two", 2),
              mapEntry("three", 3),
              mapEntry("two", 2));
    }
  }

  @Test
  public void testNullGet() {
    ImmutableMap<String, Integer> map = ImmutableMap.of("one", 1);
    assertNull(map.get(null));
  }

  @Test
  public void testAsMultimap() {
    ImmutableMap<String, Integer> map =
        ImmutableMap.of("one", 1, "won", 1, "two", 2, "too", 2, "three", 3);
    ImmutableSetMultimap<String, Integer> expected =
        ImmutableSetMultimap.of("one", 1, "won", 1, "two", 2, "too", 2, "three", 3);
    assertEquals(expected, map.asMultimap());
  }

  @Test
  public void testAsMultimapWhenEmpty() {
    ImmutableMap<String, Integer> map = ImmutableMap.of();
    ImmutableSetMultimap<String, Integer> expected = ImmutableSetMultimap.of();
    assertEquals(expected, map.asMultimap());
  }

  @Test
  public void testAsMultimapCaches() {
    ImmutableMap<String, Integer> map = ImmutableMap.of("one", 1);
    ImmutableSetMultimap<String, Integer> multimap1 = map.asMultimap();
    ImmutableSetMultimap<String, Integer> multimap2 = map.asMultimap();
    assertEquals(1, multimap1.asMap().size());
    assertSame(multimap1, multimap2);
  }

  @Test
  public void testNullPointers() {
    NullPointerTester tester = new NullPointerTester();
    tester.testAllPublicStaticMethods(ImmutableMap.class);
    tester.testAllPublicInstanceMethods(new ImmutableMap.Builder<Object, Object>());
    tester.testAllPublicInstanceMethods(ImmutableMap.of());
    tester.testAllPublicInstanceMethods(ImmutableMap.of("one", 1));
    tester.testAllPublicInstanceMethods(ImmutableMap.of("one", 1, "two", 2, "three", 3));
  }

  private static <K, V> void assertMapEquals(Map<K, V> map, Object... alternatingKeysAndValues) {
    assertEquals(map.size(), alternatingKeysAndValues.length / 2);
    int i = 0;
    for (Entry<K, V> entry : map.entrySet()) {
      assertEquals(alternatingKeysAndValues[i++], entry.getKey());
      assertEquals(alternatingKeysAndValues[i++], entry.getValue());
    }
  }

  private static class IntHolder implements Serializable {
    public int value;

    public IntHolder(int value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object o) {
      return (o instanceof IntHolder) && ((IntHolder) o).value == value;
    }

    @Override
    public int hashCode() {
      return value;
    }

    private static final long serialVersionUID = 5;
  }

  @Test
  public void testMutableValues() {
    IntHolder holderA = new IntHolder(1);
    IntHolder holderB = new IntHolder(2);
    Map<String, IntHolder> map = ImmutableMap.of("a", holderA, "b", holderB);
    holderA.value = 3;
    assertTrue(map.entrySet().contains(Maps.immutableEntry("a", new IntHolder(3))));
    Map<String, Integer> intMap = ImmutableMap.of("a", 3, "b", 2);
    assertEquals(intMap.hashCode(), map.entrySet().hashCode());
    assertEquals(intMap.hashCode(), map.hashCode());
  }

  @Test
  public void testCopyOfEnumMap() {
    EnumMap<AnEnum, String> map = new EnumMap<>(AnEnum.class);
    map.put(AnEnum.B, "foo");
    map.put(AnEnum.C, "bar");
    assertTrue(ImmutableMap.copyOf(map) instanceof ImmutableEnumMap);
  }

  @Test
  public void testViewSerialization() {
    Map<String, Integer> map = ImmutableMap.of("one", 1, "two", 2, "three", 3);
    LenientSerializableTester.reserializeAndAssertLenient(map.entrySet());
    LenientSerializableTester.reserializeAndAssertLenient(map.keySet());

    Collection<Integer> reserializedValues = reserialize(map.values());
    assertEquals(Lists.newArrayList(map.values()), Lists.newArrayList(reserializedValues));
    assertTrue(reserializedValues instanceof ImmutableCollection);
  }

  @Test
  public void testKeySetSplittable() {
    ImmutableMap<Integer, Integer> map =
        ImmutableMap.<Integer, Integer>builder()
            .put(1, 1)
            .put(2, 2)
            .put(3, 3)
            .put(4, 4)
            .put(5, 5)
            .put(6, 6)
            .build();
    assertNotNull(map.keySet().spliterator().trySplit());
  }

  @Test
  public void testEquals() {
    new EqualsTester()
        .addEqualityGroup(ImmutableMap.of(), ImmutableMap.builder().build())
        .addEqualityGroup(ImmutableMap.of(1, 1), ImmutableMap.builder().put(1, 1).build())
        .addEqualityGroup(ImmutableMap.of(1, 1, 2, 2))
        .addEqualityGroup(ImmutableMap.of(1, 1, 2, 2, 3, 3))
        .addEqualityGroup(ImmutableMap.of(1, 4, 2, 2, 3, 3))
        .addEqualityGroup(ImmutableMap.of(1, 1, 2, 4, 3, 3))
        .addEqualityGroup(ImmutableMap.of(1, 1, 2, 2, 3, 4))
        .addEqualityGroup(ImmutableMap.of(1, 2, 2, 3, 3, 1))
        .addEqualityGroup(ImmutableMap.of(1, 1, 2, 2, 3, 3, 4, 4))
        .testEquals();
  }

  /**
   * A Comparable wrapper around a String which executes callbacks on calls to hashCode, equals, and
   * compareTo.
   */
  private static class CountsHashCodeAndEquals implements Comparable<CountsHashCodeAndEquals> {
    private final String delegateString;
    private final Runnable onHashCode;
    private final Runnable onEquals;
    private final Runnable onCompareTo;

    CountsHashCodeAndEquals(
        String delegateString, Runnable onHashCode, Runnable onEquals, Runnable onCompareTo) {
      this.delegateString = delegateString;
      this.onHashCode = onHashCode;
      this.onEquals = onEquals;
      this.onCompareTo = onCompareTo;
    }

    @Override
    public int hashCode() {
      onHashCode.run();
      return delegateString.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object other) {
      onEquals.run();
      return other instanceof CountsHashCodeAndEquals
          && delegateString.equals(((CountsHashCodeAndEquals) other).delegateString);
    }

    @Override
    public int compareTo(CountsHashCodeAndEquals o) {
      onCompareTo.run();
      return delegateString.compareTo(o.delegateString);
    }
  }

  /** A holder of counters for calls to hashCode, equals, and compareTo. */
  private static final class CallsCounter {
    long hashCode;
    long equals;
    long compareTo;

    long total() {
      return hashCode + equals + compareTo;
    }

    void zero() {
      hashCode = 0;
      equals = 0;
      compareTo = 0;
    }
  }

  /** All the ways to create an ImmutableMap. */
  enum ConstructionPathway {
    COPY_OF_MAP {
      @Override
      ImmutableMap<?, ?> create(List<?> keys, Object value, CallsCounter counter) {
        Map<Object, Object> sourceMap = new LinkedHashMap<>();
        for (Object k : keys) {
          if (sourceMap.put(k, value) != null) {
            throw new UnsupportedOperationException("duplicate key");
          }
        }
        counter.zero();
        return ImmutableMap.copyOf(sourceMap);
      }
    },
    COPY_OF_ENTRIES {
      @Override
      ImmutableMap<?, ?> create(List<?> keys, Object value, CallsCounter counter) {
        return ImmutableMap.copyOf(keys.stream().map(k -> Maps.immutableEntry(k, value)).collect(Collectors.toList()));
      }
    },
    BUILDER_PUT_ONE_BY_ONE {
      @Override
      ImmutableMap<?, ?> create(List<?> keys, Object value, CallsCounter counter) {
        ImmutableMap.Builder<Object, Object> builder = ImmutableMap.builder();
        for (Object k : keys) {
          builder.put(k, value);
        }
        return builder.build();
      }
    },
    BUILDER_PUT_ENTRIES_ONE_BY_ONE {
      @Override
      ImmutableMap<?, ?> create(List<?> keys, Object value, CallsCounter counter) {
        ImmutableMap.Builder<Object, Object> builder = ImmutableMap.builder();
        for (Object k : keys) {
          builder.put(Maps.immutableEntry(k, value));
        }
        return builder.build();
      }
    },
    BUILDER_PUT_ALL_MAP {
      @Override
      ImmutableMap<?, ?> create(List<?> keys, Object value, CallsCounter counter) {
        Map<Object, Object> sourceMap = new LinkedHashMap<>();
        for (Object k : keys) {
          if (sourceMap.put(k, value) != null) {
            throw new UnsupportedOperationException("duplicate key");
          }
        }
        counter.zero();
        return ImmutableMap.builder().putAll(sourceMap).build();
      }
    },
    BUILDER_PUT_ALL_ENTRIES {
      @Override
      ImmutableMap<?, ?> create(List<?> keys, Object value, CallsCounter counter) {
        return ImmutableMap.builder()
            .putAll(keys.stream().map(k -> Maps.immutableEntry(k, value)).collect(Collectors.toList()))
            .build();
      }
    },
    FORCE_JDK {
      @Override
      ImmutableMap<?, ?> create(List<?> keys, Object value, CallsCounter counter) {
        ImmutableMap.Builder<Object, Object> builder = ImmutableMap.builder();
        for (Object k : keys) {
          builder.put(k, value);
        }
        return builder.buildJdkBacked();
      }
    };

    abstract ImmutableMap<?, ?> create(List<?> keys, Object value, CallsCounter counter);
  }

  /**
   * Returns a list of objects with the same hash code, of size 2^power, counting calls to equals,
   * hashCode, and compareTo in counter.
   */
  static List<CountsHashCodeAndEquals> createAdversarialInput(int power, CallsCounter counter) {
    String str1 = "Aa";
    String str2 = "BB";
    assertEquals(str1.hashCode(), str2.hashCode());
    List<String> haveSameHashes2 = Arrays.asList(str1, str2);
    List<CountsHashCodeAndEquals> result =
        Helpers.cartesianProduct(Collections.nCopies(power, haveSameHashes2)).stream()
            .map(
                strs ->
                    new CountsHashCodeAndEquals(
                        String.join("", strs),
                        () -> counter.hashCode++,
                        () -> counter.equals++,
                        () -> counter.compareTo++))
            .collect(Collectors.toList());
    assertEquals(
        result.get(0).delegateString.hashCode(),
        result.get(result.size() - 1).delegateString.hashCode());
    return result;
  }

  @Test
  public void testResistsHashFloodingInConstruction() {
    CallsCounter smallCounter = new CallsCounter();
    List<CountsHashCodeAndEquals> haveSameHashesSmall = createAdversarialInput(10, smallCounter);
    int smallSize = haveSameHashesSmall.size();

    CallsCounter largeCounter = new CallsCounter();
    List<CountsHashCodeAndEquals> haveSameHashesLarge = createAdversarialInput(15, largeCounter);
    int largeSize = haveSameHashesLarge.size();

    for (ConstructionPathway pathway : ConstructionPathway.values()) {
      smallCounter.zero();
      pathway.create(haveSameHashesSmall, "valueObject", smallCounter);
      long smallOps = smallCounter.total();

      largeCounter.zero();
      pathway.create(haveSameHashesLarge, "valueObject", largeCounter);
      long largeOps = largeCounter.total();

      double ratio = (double) largeOps / smallOps;
      assertThat(ratio)
          .as(
              "ratio of equals/hashCode/compareTo operations to build an ImmutableMap via %s"
                  + " with %s entries versus %s entries",
              pathway, largeSize, smallSize)
          .isLessThanOrEqualTo(2 * (largeSize * Math.log(largeSize)) / (smallSize * Math.log(smallSize)));
      // allow up to 2x wobble in the constant factors
    }
  }

  @Test
  public void testResistsHashFloodingOnGet() {
    CallsCounter smallCounter = new CallsCounter();
    List<CountsHashCodeAndEquals> haveSameHashesSmall = createAdversarialInput(10, smallCounter);
    int smallSize = haveSameHashesSmall.size();
    ImmutableMap<?, ?> smallMap =
        ConstructionPathway.BUILDER_PUT_ONE_BY_ONE.create(
            haveSameHashesSmall, "valueObject", smallCounter);
    long worstCaseQuerySmall = worstCaseQueryOperations(smallMap, smallCounter);

    CallsCounter largeCounter = new CallsCounter();
    List<CountsHashCodeAndEquals> haveSameHashesLarge = createAdversarialInput(15, largeCounter);
    int largeSize = haveSameHashesLarge.size();
    ImmutableMap<?, ?> largeMap =
        ConstructionPathway.BUILDER_PUT_ONE_BY_ONE.create(
            haveSameHashesLarge, "valueObject", largeCounter);
    long worstCaseQueryLarge = worstCaseQueryOperations(largeMap, largeCounter);

    double ratio = (double) worstCaseQueryLarge / worstCaseQuerySmall;
    assertThat(ratio)
        .as(
            "Ratio of worst case query operations for an ImmutableMap of size %s versus %s",
            largeSize, smallSize)
        .isLessThanOrEqualTo(2 * Math.log(largeSize) / Math.log(smallSize));
    // allow up to 2x wobble in the constant factors
  }

  private static long worstCaseQueryOperations(Map<?, ?> map, CallsCounter counter) {
    long worstCalls = 0;
    for (Object k : map.keySet()) {
      counter.zero();
      Object unused = map.get(k);
      worstCalls = Math.max(worstCalls, counter.total());
    }
    return worstCalls;
  }

  @Test
  public void testCopyOfMutableEntryList() {
    List<Entry<String, String>> entryList =
        Arrays.asList(
            new AbstractMap.SimpleEntry<>("a", "1"), new AbstractMap.SimpleEntry<>("b", "2"));
    ImmutableMap<String, String> map = ImmutableMap.copyOf(entryList);
    assertThat(map).containsExactly(entry("a", "1"), entry("b", "2"));
    entryList.get(0).setValue("3");
    assertThat(map).containsExactly(entry("a", "1"), entry("b", "2"));
  }

  @Test
  public void testBuilderPutAllEntryList() {
    List<Entry<String, String>> entryList =
        Arrays.asList(
            new AbstractMap.SimpleEntry<>("a", "1"), new AbstractMap.SimpleEntry<>("b", "2"));
    ImmutableMap<String, String> map =
        ImmutableMap.<String, String>builder().putAll(entryList).build();
    assertThat(map).containsExactly(entry("a", "1"), entry("b", "2"));
    entryList.get(0).setValue("3");
    assertThat(map).containsExactly(entry("a", "1"), entry("b", "2"));
  }

  @Test
  public void testBuilderPutAllEntryListJdkBacked() {
    List<Entry<String, String>> entryList =
        Arrays.asList(
            new AbstractMap.SimpleEntry<>("a", "1"), new AbstractMap.SimpleEntry<>("b", "2"));
    ImmutableMap<String, String> map =
        ImmutableMap.<String, String>builder().putAll(entryList).buildJdkBacked();
    assertThat(map).containsExactly(entry("a", "1"), entry("b", "2"));
    entryList.get(0).setValue("3");
    assertThat(map).containsExactly(entry("a", "1"), entry("b", "2"));
  }
}
