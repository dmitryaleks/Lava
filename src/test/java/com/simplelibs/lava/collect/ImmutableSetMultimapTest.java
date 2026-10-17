/*
 * Copyright (C) 2009 The Guava Authors
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
import static org.assertj.core.api.Assertions.assertThat;

import com.simplelibs.lava.base.Equivalence;
import com.simplelibs.lava.collect.ImmutableSetMultimap.Builder;
import com.simplelibs.lava.collect.testing.google.UnmodifiableCollectionTests;
import com.simplelibs.lava.testing.CollectorTester;
import com.simplelibs.lava.testing.EqualsTester;
import com.simplelibs.lava.testing.LavaTestCase;
import com.simplelibs.lava.testing.SerializableTester;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map.Entry;
import java.util.function.BiPredicate;
import java.util.stream.Collector;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ImmutableSetMultimap}.
 *
 * @author Mike Ward
 */
public class ImmutableSetMultimapTest extends LavaTestCase {

  @Test
  public void testBuilder_withImmutableEntry() {
    ImmutableSetMultimap<String, Integer> multimap =
        new Builder<String, Integer>().put(Maps.immutableEntry("one", 1)).build();
    assertEquals(ImmutableSet.of(1), multimap.get("one"));
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
    ImmutableSetMultimap.Builder<String, Integer> builder = new Builder<>();
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
    assertEquals(ImmutableSet.of(1), builder.build().get("one"));
  }

  @Test
  public void testBuilderPutAllIterable() {
    ImmutableSetMultimap.Builder<String, Integer> builder = ImmutableSetMultimap.builder();
    builder.putAll("foo", Arrays.asList(1, 2, 3));
    builder.putAll("bar", Arrays.asList(4, 5));
    builder.putAll("foo", Arrays.asList(6, 7));
    Multimap<String, Integer> multimap = builder.build();
    assertEquals(ImmutableSet.of(1, 2, 3, 6, 7), multimap.get("foo"));
    assertEquals(ImmutableSet.of(4, 5), multimap.get("bar"));
    assertEquals(7, multimap.size());
  }

  @Test
  public void testBuilderPutAllVarargs() {
    ImmutableSetMultimap.Builder<String, Integer> builder = ImmutableSetMultimap.builder();
    builder.putAll("foo", 1, 2, 3);
    builder.putAll("bar", 4, 5);
    builder.putAll("foo", 6, 7);
    Multimap<String, Integer> multimap = builder.build();
    assertEquals(ImmutableSet.of(1, 2, 3, 6, 7), multimap.get("foo"));
    assertEquals(ImmutableSet.of(4, 5), multimap.get("bar"));
    assertEquals(7, multimap.size());
  }

  @Test
  public void testBuilderPutAllMultimap() {
    Multimap<String, Integer> toPut = LinkedListMultimap.create();
    toPut.put("foo", 1);
    toPut.put("bar", 4);
    toPut.put("foo", 2);
    toPut.put("foo", 3);
    Multimap<String, Integer> moreToPut = LinkedListMultimap.create();
    moreToPut.put("foo", 6);
    moreToPut.put("bar", 5);
    moreToPut.put("foo", 7);
    ImmutableSetMultimap.Builder<String, Integer> builder = ImmutableSetMultimap.builder();
    builder.putAll(toPut);
    builder.putAll(moreToPut);
    Multimap<String, Integer> multimap = builder.build();
    assertEquals(ImmutableSet.of(1, 2, 3, 6, 7), multimap.get("foo"));
    assertEquals(ImmutableSet.of(4, 5), multimap.get("bar"));
    assertEquals(7, multimap.size());
  }

  @Test
  public void testBuilderPutAllWithDuplicates() {
    ImmutableSetMultimap.Builder<String, Integer> builder = ImmutableSetMultimap.builder();
    builder.putAll("foo", 1, 2, 3);
    builder.putAll("bar", 4, 5);
    builder.putAll("foo", 1, 6, 7);
    ImmutableSetMultimap<String, Integer> multimap = builder.build();
    assertEquals(7, multimap.size());
  }

  @Test
  public void testBuilderPutWithDuplicates() {
    ImmutableSetMultimap.Builder<String, Integer> builder = ImmutableSetMultimap.builder();
    builder.putAll("foo", 1, 2, 3);
    builder.putAll("bar", 4, 5);
    builder.put("foo", 1);
    ImmutableSetMultimap<String, Integer> multimap = builder.build();
    assertEquals(5, multimap.size());
  }

  @Test
  public void testBuilderPutAllMultimapWithDuplicates() {
    Multimap<String, Integer> toPut = LinkedListMultimap.create();
    toPut.put("foo", 1);
    toPut.put("bar", 4);
    toPut.put("foo", 2);
    toPut.put("foo", 1);
    toPut.put("bar", 5);
    ImmutableSetMultimap.Builder<String, Integer> builder = ImmutableSetMultimap.builder();
    builder.putAll(toPut);
    ImmutableSetMultimap<String, Integer> multimap = builder.build();
    assertEquals(4, multimap.size());
  }

  @Test
  public void testBuilderPutNullKey() {
    Multimap<String, Integer> toPut = LinkedListMultimap.create();
    toPut.put("foo", null);
    ImmutableSetMultimap.Builder<String, Integer> builder = ImmutableSetMultimap.builder();
    try {
      builder.put(null, 1);
      fail();
    } catch (NullPointerException expected) {
    }
    try {
      builder.putAll(null, Arrays.asList(1, 2, 3));
      fail();
    } catch (NullPointerException expected) {
    }
    try {
      builder.putAll(null, 1, 2, 3);
      fail();
    } catch (NullPointerException expected) {
    }
    try {
      builder.putAll(toPut);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void testBuilderPutNullValue() {
    Multimap<String, Integer> toPut = LinkedListMultimap.create();
    toPut.put(null, 1);
    ImmutableSetMultimap.Builder<String, Integer> builder = ImmutableSetMultimap.builder();
    try {
      builder.put("foo", null);
      fail();
    } catch (NullPointerException expected) {
    }
    try {
      builder.putAll("foo", Arrays.asList(1, null, 3));
      fail();
    } catch (NullPointerException expected) {
    }
    try {
      builder.putAll("foo", 4, null, 6);
      fail();
    } catch (NullPointerException expected) {
    }
    try {
      builder.putAll(toPut);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void testBuilderOrderKeysBy() {
    ImmutableSetMultimap.Builder<String, Integer> builder = ImmutableSetMultimap.builder();
    builder.put("b", 3);
    builder.put("d", 2);
    builder.put("a", 5);
    builder.orderKeysBy(Collections.reverseOrder());
    builder.put("c", 4);
    builder.put("a", 2);
    builder.put("b", 6);
    ImmutableSetMultimap<String, Integer> multimap = builder.build();
    assertThat(multimap.keySet()).containsExactly("d", "c", "b", "a");
    assertThat(multimap.values()).containsExactly(2, 4, 3, 6, 5, 2);
    assertThat(multimap.get("a")).containsExactly(5, 2);
    assertThat(multimap.get("b")).containsExactly(3, 6);
  }

  @Test
  public void testBuilderOrderKeysByDuplicates() {
    ImmutableSetMultimap.Builder<String, Integer> builder = ImmutableSetMultimap.builder();
    builder.put("bb", 3);
    builder.put("d", 2);
    builder.put("a", 5);
    builder.orderKeysBy(
        new Comparator<String>() {
          @Override
          public int compare(String left, String right) {
            return left.length() - right.length();
          }
        });
    builder.put("cc", 4);
    builder.put("a", 2);
    builder.put("bb", 6);
    ImmutableSetMultimap<String, Integer> multimap = builder.build();
    assertThat(multimap.keySet()).containsExactly("d", "a", "bb", "cc");
    assertThat(multimap.values()).containsExactly(2, 5, 2, 3, 6, 4);
    assertThat(multimap.get("a")).containsExactly(5, 2);
    assertThat(multimap.get("bb")).containsExactly(3, 6);
  }

  @Test
  public void testBuilderOrderValuesBy() {
    ImmutableSetMultimap.Builder<String, Integer> builder = ImmutableSetMultimap.builder();
    builder.put("b", 3);
    builder.put("d", 2);
    builder.put("a", 5);
    builder.orderValuesBy(Collections.reverseOrder());
    builder.put("c", 4);
    builder.put("a", 2);
    builder.put("b", 6);
    ImmutableSetMultimap<String, Integer> multimap = builder.build();
    assertThat(multimap.keySet()).containsExactly("b", "d", "a", "c");
    assertThat(multimap.values()).containsExactly(6, 3, 2, 5, 2, 4);
    assertThat(multimap.get("a")).containsExactly(5, 2);
    assertThat(multimap.get("b")).containsExactly(6, 3);
  }

  @Test
  public void testBuilderOrderKeysAndValuesBy() {
    ImmutableSetMultimap.Builder<String, Integer> builder = ImmutableSetMultimap.builder();
    builder.put("b", 3);
    builder.put("d", 2);
    builder.put("a", 5);
    builder.orderKeysBy(Collections.reverseOrder());
    builder.orderValuesBy(Collections.reverseOrder());
    builder.put("c", 4);
    builder.put("a", 2);
    builder.put("b", 6);
    ImmutableSetMultimap<String, Integer> multimap = builder.build();
    assertThat(multimap.keySet()).containsExactly("d", "c", "b", "a");
    assertThat(multimap.values()).containsExactly(2, 4, 6, 3, 5, 2);
    assertThat(multimap.get("a")).containsExactly(5, 2);
    assertThat(multimap.get("b")).containsExactly(6, 3);
  }

  @Test
  public void testCopyOf() {
    HashMultimap<String, Integer> input = HashMultimap.create();
    input.put("foo", 1);
    input.put("bar", 2);
    input.put("foo", 3);
    Multimap<String, Integer> multimap = ImmutableSetMultimap.copyOf(input);
    assertEquals(multimap, input);
    assertEquals(input, multimap);
  }

  @Test
  public void testCopyOfWithDuplicates() {
    ArrayListMultimap<Object, Object> input = ArrayListMultimap.create();
    input.put("foo", 1);
    input.put("bar", 2);
    input.put("foo", 3);
    input.put("foo", 1);
    ImmutableSetMultimap<Object, Object> copy = ImmutableSetMultimap.copyOf(input);
    assertEquals(3, copy.size());
  }

  @Test
  public void testCopyOfEmpty() {
    HashMultimap<String, Integer> input = HashMultimap.create();
    Multimap<String, Integer> multimap = ImmutableSetMultimap.copyOf(input);
    assertEquals(multimap, input);
    assertEquals(input, multimap);
  }

  @Test
  public void testCopyOfImmutableSetMultimap() {
    Multimap<String, Integer> multimap = createMultimap();
    assertSame(multimap, ImmutableSetMultimap.copyOf(multimap));
  }

  @Test
  public void testCopyOfNullKey() {
    HashMultimap<String, Integer> input = HashMultimap.create();
    input.put(null, 1);
    try {
      ImmutableSetMultimap.copyOf(input);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void testCopyOfNullValue() {
    HashMultimap<String, Integer> input = HashMultimap.create();
    input.putAll("foo", Arrays.asList(1, null, 3));
    try {
      ImmutableSetMultimap.copyOf(input);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void testToImmutableSetMultimap() {
    Collector<Entry<String, Integer>, ?, ImmutableSetMultimap<String, Integer>> collector =
        ImmutableSetMultimap.toImmutableSetMultimap(Entry::getKey, Entry::getValue);
    BiPredicate<ImmutableSetMultimap<?, ?>, ImmutableSetMultimap<?, ?>> equivalence =
        Equivalence.equals()
            .onResultOf(
                (ImmutableSetMultimap<?, ?> mm) ->
                    ImmutableList.copyOf(mm.asMap().entrySet()))
            .and(Equivalence.equals());
    CollectorTester.of(collector, equivalence)
        .expectCollects(ImmutableSetMultimap.of())
        .expectCollects(
            ImmutableSetMultimap.of("a", 1, "b", 2, "a", 3, "c", 4),
            mapEntry("a", 1),
            mapEntry("b", 2),
            mapEntry("a", 3),
            mapEntry("c", 4));
  }

  @Test
  public void testFlatteningToImmutableSetMultimap() {
    Collector<String, ?, ImmutableSetMultimap<Character, Character>> collector =
        ImmutableSetMultimap.flatteningToImmutableSetMultimap(
            str -> str.charAt(0), str -> str.substring(1).chars().mapToObj(c -> (char) c));
    BiPredicate<Multimap<?, ?>, Multimap<?, ?>> equivalence =
        Equivalence.equals()
            .onResultOf((Multimap<?, ?> mm) -> ImmutableList.copyOf(mm.asMap().entrySet()))
            .and(Equivalence.equals());
    ImmutableSetMultimap<Character, Character> empty = ImmutableSetMultimap.of();
    ImmutableSetMultimap<Character, Character> filled =
        ImmutableSetMultimap.<Character, Character>builder()
            .putAll('b', Arrays.asList('a', 'n', 'a', 'n', 'a'))
            .putAll('a', Arrays.asList('p', 'p', 'l', 'e'))
            .putAll('c', Arrays.asList('a', 'r', 'r', 'o', 't'))
            .putAll('a', Arrays.asList('s', 'p', 'a', 'r', 'a', 'g', 'u', 's'))
            .putAll('c', Arrays.asList('h', 'e', 'r', 'r', 'y'))
            .build();
    CollectorTester.of(collector, equivalence)
        .expectCollects(empty)
        .expectCollects(filled, "banana", "apple", "carrot", "asparagus", "cherry");
  }

  @Test
  public void testEmptyMultimapReads() {
    Multimap<String, Integer> multimap = ImmutableSetMultimap.of();
    assertFalse(multimap.containsKey("foo"));
    assertFalse(multimap.containsValue(1));
    assertFalse(multimap.containsEntry("foo", 1));
    assertTrue(multimap.entries().isEmpty());
    assertTrue(multimap.equals(HashMultimap.create()));
    assertEquals(Collections.emptySet(), multimap.get("foo"));
    assertEquals(0, multimap.hashCode());
    assertTrue(multimap.isEmpty());
    assertEquals(Collections.emptySet(), multimap.keySet());
    assertEquals(0, multimap.size());
    assertTrue(multimap.values().isEmpty());
    assertEquals("{}", multimap.toString());
  }

  @Test
  public void testEmptyMultimapWrites() {
    Multimap<String, Integer> multimap = ImmutableSetMultimap.of();
    UnmodifiableCollectionTests.assertMultimapIsUnmodifiable(multimap, "foo", 1);
  }

  @Test
  public void testMultimapReads() {
    Multimap<String, Integer> multimap = createMultimap();
    assertTrue(multimap.containsKey("foo"));
    assertFalse(multimap.containsKey("cat"));
    assertTrue(multimap.containsValue(1));
    assertFalse(multimap.containsValue(5));
    assertTrue(multimap.containsEntry("foo", 1));
    assertFalse(multimap.containsEntry("cat", 1));
    assertFalse(multimap.containsEntry("foo", 5));
    assertFalse(multimap.entries().isEmpty());
    assertEquals(3, multimap.size());
    assertFalse(multimap.isEmpty());
    assertEquals("{foo=[1, 3], bar=[2]}", multimap.toString());
  }

  @Test
  public void testMultimapWrites() {
    Multimap<String, Integer> multimap = createMultimap();
    UnmodifiableCollectionTests.assertMultimapIsUnmodifiable(multimap, "bar", 2);
  }

  @Test
  public void testMultimapEquals() {
    Multimap<String, Integer> multimap = createMultimap();
    Multimap<String, Integer> hashMultimap = HashMultimap.create();
    hashMultimap.putAll("foo", Arrays.asList(1, 3));
    hashMultimap.put("bar", 2);

    new EqualsTester()
        .addEqualityGroup(
            multimap,
            createMultimap(),
            hashMultimap,
            ImmutableSetMultimap.<String, Integer>builder()
                .put("bar", 2)
                .put("foo", 1)
                .put("foo", 3)
                .build(),
            ImmutableSetMultimap.<String, Integer>builder()
                .put("bar", 2)
                .put("foo", 3)
                .put("foo", 1)
                .build())
        .addEqualityGroup(
            ImmutableSetMultimap.<String, Integer>builder()
                .put("foo", 2)
                .put("foo", 3)
                .put("foo", 1)
                .build())
        .addEqualityGroup(
            ImmutableSetMultimap.<String, Integer>builder().put("bar", 2).put("foo", 3).build())
        .testEquals();
  }

  @Test
  public void testOf() {
    assertMultimapEquals(ImmutableSetMultimap.of("one", 1), "one", 1);
    assertMultimapEquals(ImmutableSetMultimap.of("one", 1, "two", 2), "one", 1, "two", 2);
    assertMultimapEquals(
        ImmutableSetMultimap.of("one", 1, "two", 2, "three", 3), "one", 1, "two", 2, "three", 3);
    assertMultimapEquals(
        ImmutableSetMultimap.of("one", 1, "two", 2, "three", 3, "four", 4),
        "one",
        1,
        "two",
        2,
        "three",
        3,
        "four",
        4);
    assertMultimapEquals(
        ImmutableSetMultimap.of("one", 1, "two", 2, "three", 3, "four", 4, "five", 5),
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
  public void testInverse() {
    assertEquals(
        ImmutableSetMultimap.<Integer, String>of(),
        ImmutableSetMultimap.<String, Integer>of().inverse());
    assertEquals(ImmutableSetMultimap.of(1, "one"), ImmutableSetMultimap.of("one", 1).inverse());
    assertEquals(
        ImmutableSetMultimap.of(1, "one", 2, "two"),
        ImmutableSetMultimap.of("one", 1, "two", 2).inverse());
    assertEquals(
        ImmutableSetMultimap.of('o', "of", 'f', "of", 't', "to", 'o', "to"),
        ImmutableSetMultimap.of("of", 'o', "of", 'f', "to", 't', "to", 'o').inverse());
  }

  @Test
  public void testInverseMinimizesWork() {
    ImmutableSetMultimap<String, Character> multimap =
        ImmutableSetMultimap.of("of", 'o', "of", 'f', "to", 't', "to", 'o');
    assertSame(multimap.inverse(), multimap.inverse());
    assertSame(multimap, multimap.inverse().inverse());
  }

  private static <K, V> void assertMultimapEquals(
      Multimap<K, V> multimap, Object... alternatingKeysAndValues) {
    assertEquals(multimap.size(), alternatingKeysAndValues.length / 2);
    int i = 0;
    for (Entry<K, V> entry : multimap.entries()) {
      assertEquals(alternatingKeysAndValues[i++], entry.getKey());
      assertEquals(alternatingKeysAndValues[i++], entry.getValue());
    }
  }

  @Test
  public void testSerialization() {
    Multimap<String, Integer> multimap = createMultimap();
    SerializableTester.reserializeAndAssert(multimap);
    assertEquals(multimap.size(), SerializableTester.reserialize(multimap).size());
    SerializableTester.reserializeAndAssert(multimap.get("foo"));
    LenientSerializableTester.reserializeAndAssertLenient(multimap.keySet());
    SerializableTester.reserializeAndAssert(multimap.asMap());
    Collection<Integer> valuesCopy = SerializableTester.reserialize(multimap.values());
    assertThat(valuesCopy).containsExactlyInAnyOrderElementsOf(multimap.values());
  }

  @Test
  public void testEmptySerialization() {
    Multimap<String, Integer> multimap = ImmutableSetMultimap.of();
    assertSame(multimap, SerializableTester.reserialize(multimap));
  }

  @Test
  public void testSortedSerialization() {
    Multimap<String, Integer> multimap =
        new ImmutableSetMultimap.Builder<String, Integer>()
            .orderKeysBy(Comparator.<String>naturalOrder().reversed())
            .orderValuesBy((Comparator<Object> & java.io.Serializable) (a, b) -> a.toString().compareTo(b.toString()))
            .put("a", 2)
            .put("a", 10)
            .put("b", 1)
            .build();
    multimap = SerializableTester.reserialize(multimap);
    assertThat(multimap.keySet()).containsExactly("b", "a");
    assertThat(multimap.get("a")).containsExactly(10, 2);
  }

  private ImmutableSetMultimap<String, Integer> createMultimap() {
    return ImmutableSetMultimap.<String, Integer>builder()
        .put("foo", 1)
        .put("bar", 2)
        .put("foo", 3)
        .build();
  }
}
