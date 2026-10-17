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

import static com.simplelibs.lava.collect.testing.IteratorFeature.UNMODIFIABLE;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import com.simplelibs.lava.collect.testing.Helpers;
import com.simplelibs.lava.collect.testing.IteratorTester;
import com.simplelibs.lava.collect.testing.MinimalCollection;
import com.simplelibs.lava.collect.testing.MinimalIterable;
import com.simplelibs.lava.testing.LavaTestCase;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Base class for {@link ImmutableSet} and {@link ImmutableSortedSet} tests.
 *
 * @author Kevin Bourrillion
 * @author Jared Levy
 */
public abstract class AbstractImmutableSetTest extends LavaTestCase {

  protected abstract <E extends Comparable<? super E>> Set<E> of();

  protected abstract <E extends Comparable<? super E>> Set<E> of(E e);

  protected abstract <E extends Comparable<? super E>> Set<E> of(E e1, E e2);

  protected abstract <E extends Comparable<? super E>> Set<E> of(E e1, E e2, E e3);

  protected abstract <E extends Comparable<? super E>> Set<E> of(E e1, E e2, E e3, E e4);

  protected abstract <E extends Comparable<? super E>> Set<E> of(E e1, E e2, E e3, E e4, E e5);

  @SuppressWarnings("unchecked")
  protected abstract <E extends Comparable<? super E>> Set<E> of(
      E e1, E e2, E e3, E e4, E e5, E e6, E... rest);

  protected abstract <E extends Comparable<? super E>> Set<E> copyOf(E[] elements);

  protected abstract <E extends Comparable<? super E>> Set<E> copyOf(
      Collection<? extends E> elements);

  protected abstract <E extends Comparable<? super E>> Set<E> copyOf(
      Iterable<? extends E> elements);

  protected abstract <E extends Comparable<? super E>> Set<E> copyOf(
      Iterator<? extends E> elements);

  @Test
  public void testCreation_noArgs() {
    Set<String> set = of();
    assertEquals(Collections.<String>emptySet(), set);
    assertSame(of(), set);
  }

  @Test
  public void testCreation_oneElement() {
    Set<String> set = of("a");
    assertEquals(Collections.singleton("a"), set);
  }

  @Test
  public void testCreation_twoElements() {
    Set<String> set = of("a", "b");
    assertEquals(Helpers.newHashSet("a", "b"), set);
  }

  @Test
  public void testCreation_threeElements() {
    Set<String> set = of("a", "b", "c");
    assertEquals(Helpers.newHashSet("a", "b", "c"), set);
  }

  @Test
  public void testCreation_fourElements() {
    Set<String> set = of("a", "b", "c", "d");
    assertEquals(Helpers.newHashSet("a", "b", "c", "d"), set);
  }

  @Test
  public void testCreation_fiveElements() {
    Set<String> set = of("a", "b", "c", "d", "e");
    assertEquals(Helpers.newHashSet("a", "b", "c", "d", "e"), set);
  }

  @Test
  public void testCreation_sixElements() {
    Set<String> set = of("a", "b", "c", "d", "e", "f");
    assertEquals(Helpers.newHashSet("a", "b", "c", "d", "e", "f"), set);
  }

  @Test
  public void testCreation_sevenElements() {
    Set<String> set = of("a", "b", "c", "d", "e", "f", "g");
    assertEquals(Helpers.newHashSet("a", "b", "c", "d", "e", "f", "g"), set);
  }

  @Test
  public void testCreation_eightElements() {
    Set<String> set = of("a", "b", "c", "d", "e", "f", "g", "h");
    assertEquals(Helpers.newHashSet("a", "b", "c", "d", "e", "f", "g", "h"), set);
  }

  @Test
  public void testCopyOf_emptyArray() {
    String[] array = new String[0];
    Set<String> set = copyOf(array);
    assertEquals(Collections.<String>emptySet(), set);
    assertSame(of(), set);
  }

  @Test
  public void testCopyOf_arrayOfOneElement() {
    String[] array = new String[] {"a"};
    Set<String> set = copyOf(array);
    assertEquals(Collections.singleton("a"), set);
  }

  @Test
  public void testCopyOf_nullArray() {
    try {
      copyOf((String[]) null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void testCopyOf_arrayContainingOnlyNull() {
    String[] array = new String[] {null};
    try {
      copyOf(array);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void testCopyOf_collection_empty() {
    // "<String>" is required to work around a javac 1.5 bug.
    Collection<String> c = MinimalCollection.<String>of();
    Set<String> set = copyOf(c);
    assertEquals(Collections.<String>emptySet(), set);
    assertSame(of(), set);
  }

  @Test
  public void testCopyOf_collection_oneElement() {
    Collection<String> c = MinimalCollection.of("a");
    Set<String> set = copyOf(c);
    assertEquals(Collections.singleton("a"), set);
  }

  @Test
  public void testCopyOf_collection_oneElementRepeated() {
    Collection<String> c = MinimalCollection.of("a", "a", "a");
    Set<String> set = copyOf(c);
    assertEquals(Collections.singleton("a"), set);
  }

  @Test
  public void testCopyOf_collection_general() {
    Collection<String> c = MinimalCollection.of("a", "b", "a");
    Set<String> set = copyOf(c);
    assertEquals(2, set.size());
    assertTrue(set.contains("a"));
    assertTrue(set.contains("b"));
  }

  @Test
  public void testCopyOf_collectionContainingNull() {
    Collection<String> c = MinimalCollection.of("a", null, "b");
    try {
      copyOf(c);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  enum TestEnum {
    A,
    B,
    C,
    D
  }

  @Test
  public void testCopyOf_collection_enumSet() {
    Collection<TestEnum> c = EnumSet.of(TestEnum.A, TestEnum.B, TestEnum.D);
    Set<TestEnum> set = copyOf(c);
    assertEquals(3, set.size());
    assertEquals(c, set);
  }

  @Test
  public void testCopyOf_iterator_empty() {
    Iterator<String> iterator = Iterators.emptyIterator();
    Set<String> set = copyOf(iterator);
    assertEquals(Collections.<String>emptySet(), set);
    assertSame(of(), set);
  }

  @Test
  public void testCopyOf_iterator_oneElement() {
    Iterator<String> iterator = Iterators.singletonIterator("a");
    Set<String> set = copyOf(iterator);
    assertEquals(Collections.singleton("a"), set);
  }

  @Test
  public void testCopyOf_iterator_oneElementRepeated() {
    Iterator<String> iterator = Iterators.forArray("a", "a", "a");
    Set<String> set = copyOf(iterator);
    assertEquals(Collections.singleton("a"), set);
  }

  @Test
  public void testCopyOf_iterator_general() {
    Iterator<String> iterator = Iterators.forArray("a", "b", "a");
    Set<String> set = copyOf(iterator);
    assertEquals(2, set.size());
    assertTrue(set.contains("a"));
    assertTrue(set.contains("b"));
  }

  @Test
  public void testCopyOf_iteratorContainingNull() {
    Iterator<String> c = Iterators.forArray("a", null, "b");
    try {
      copyOf(c);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  private static class CountingIterable implements Iterable<String> {
    int count = 0;

    @Override
    public Iterator<String> iterator() {
      count++;
      return Iterators.forArray("a", "b", "a");
    }
  }

  @Test
  public void testCopyOf_plainIterable() {
    CountingIterable iterable = new CountingIterable();
    Set<String> set = copyOf(iterable);
    assertEquals(2, set.size());
    assertTrue(set.contains("a"));
    assertTrue(set.contains("b"));
  }

  @Test
  public void testCopyOf_plainIterable_iteratesOnce() {
    CountingIterable iterable = new CountingIterable();
    Set<String> unused = copyOf(iterable);
    assertEquals(1, iterable.count);
  }

  @Test
  public void testCopyOf_shortcut_empty() {
    Collection<String> c = of();
    assertEquals(Collections.<String>emptySet(), copyOf(c));
    assertSame(c, copyOf(c));
  }

  @Test
  public void testCopyOf_shortcut_singleton() {
    Collection<String> c = of("a");
    assertEquals(Collections.singleton("a"), copyOf(c));
    assertSame(c, copyOf(c));
  }

  @Test
  public void testCopyOf_shortcut_sameType() {
    Collection<String> c = of("a", "b", "c");
    assertSame(c, copyOf(c));
  }

  @Test
  public void testToString() {
    Set<String> set = of("a", "b", "c", "d", "e", "f", "g");
    assertEquals("[a, b, c, d, e, f, g]", set.toString());
  }

  @Test
  public void testIterator_oneElement() {
    new IteratorTester<String>(
        5, UNMODIFIABLE, Collections.singleton("a"), IteratorTester.KnownOrder.KNOWN_ORDER) {
      @Override
      protected Iterator<String> newTargetIterator() {
        return of("a").iterator();
      }
    }.test();
  }

  @Test
  public void testIterator_general() {
    new IteratorTester<String>(
        5, UNMODIFIABLE, asList("a", "b", "c"), IteratorTester.KnownOrder.KNOWN_ORDER) {
      @Override
      protected Iterator<String> newTargetIterator() {
        return of("a", "b", "c").iterator();
      }
    }.test();
  }

  @Test
  public void testContainsAll_sameType() {
    Collection<String> c = of("a", "b", "c");
    assertFalse(c.containsAll(of("a", "b", "c", "d")));
    assertFalse(c.containsAll(of("a", "d")));
    assertTrue(c.containsAll(of("a", "c")));
    assertTrue(c.containsAll(of("a", "b", "c")));
  }

  @Test
  public void testEquals_sameType() {
    Collection<String> c = of("a", "b", "c");
    assertTrue(c.equals(of("a", "b", "c")));
    assertFalse(c.equals(of("a", "b", "d")));
  }

  abstract <E extends Comparable<E>> ImmutableSet.Builder<E> builder();

  @Test
  public void testBuilderWithNonDuplicateElements() {
    ImmutableSet<String> set =
        this.<String>builder()
            .add("a")
            .add("b", "c")
            .add("d", "e", "f")
            .add("g", "h", "i", "j")
            .build();
    assertThat(set).containsExactly("a", "b", "c", "d", "e", "f", "g", "h", "i", "j");
  }

  @Test
  public void testReuseBuilderWithNonDuplicateElements() {
    ImmutableSet.Builder<String> builder = this.<String>builder().add("a").add("b");
    assertThat(builder.build()).containsExactly("a", "b");
    builder.add("c", "d");
    assertThat(builder.build()).containsExactly("a", "b", "c", "d");
  }

  @Test
  public void testBuilderWithDuplicateElements() {
    ImmutableSet<String> set =
        this.<String>builder()
            .add("a")
            .add("a", "a")
            .add("a", "a", "a")
            .add("a", "a", "a", "a")
            .build();
    assertTrue(set.contains("a"));
    assertFalse(set.contains("b"));
    assertEquals(1, set.size());
  }

  @Test
  public void testReuseBuilderWithDuplicateElements() {
    ImmutableSet.Builder<String> builder = this.<String>builder().add("a").add("a", "a").add("b");
    assertThat(builder.build()).containsExactly("a", "b");
    builder.add("a", "b", "c", "c");
    assertThat(builder.build()).containsExactly("a", "b", "c");
  }

  @Test
  public void testBuilderAddAll() {
    List<String> a = asList("a", "b", "c");
    List<String> b = asList("c", "d", "e");
    ImmutableSet<String> set = this.<String>builder().addAll(a).addAll(b).build();
    assertThat(set).containsExactly("a", "b", "c", "d", "e");
  }

  static final int LAST_COLOR_ADDED = 0x00BFFF;

  @Test
  public void testComplexBuilder() {
    List<Integer> colorElem = asList(0x00, 0x33, 0x66, 0x99, 0xCC, 0xFF);
    // javac won't compile this without "this.<Integer>"
    ImmutableSet.Builder<Integer> webSafeColorsBuilder = this.<Integer>builder();
    for (Integer red : colorElem) {
      for (Integer green : colorElem) {
        for (Integer blue : colorElem) {
          webSafeColorsBuilder.add((red << 16) + (green << 8) + blue);
        }
      }
    }
    ImmutableSet<Integer> webSafeColors = webSafeColorsBuilder.build();
    assertEquals(216, webSafeColors.size());
    Integer[] webSafeColorArray = webSafeColors.toArray(new Integer[webSafeColors.size()]);
    assertEquals(0x000000, (int) webSafeColorArray[0]);
    assertEquals(0x000033, (int) webSafeColorArray[1]);
    assertEquals(0x000066, (int) webSafeColorArray[2]);
    assertEquals(0x003300, (int) webSafeColorArray[6]);
    assertEquals(0x330000, (int) webSafeColorArray[36]);
    ImmutableSet<Integer> addedColor = webSafeColorsBuilder.add(LAST_COLOR_ADDED).build();
    assertEquals(
        "Modifying the builder should not have changed any already built sets",
        216,
        webSafeColors.size());
    assertEquals("the new array should be one bigger than webSafeColors", 217, addedColor.size());
    Integer[] appendColorArray = addedColor.toArray(new Integer[addedColor.size()]);
    assertEquals(getComplexBuilderSetLastElement(), (int) appendColorArray[216]);
  }

  abstract int getComplexBuilderSetLastElement();

  @Test
  public void testBuilderAddHandlesNullsCorrectly() {
    ImmutableSet.Builder<String> builder = this.<String>builder();
    try {
      builder.add((String) null);
      fail("expected NullPointerException"); // COV_NF_LINE
    } catch (NullPointerException expected) {
    }

    builder = this.<String>builder();
    try {
      builder.add((String[]) null);
      fail("expected NullPointerException"); // COV_NF_LINE
    } catch (NullPointerException expected) {
    }

    builder = this.<String>builder();
    try {
      builder.add("a", (String) null);
      fail("expected NullPointerException"); // COV_NF_LINE
    } catch (NullPointerException expected) {
    }

    builder = this.<String>builder();
    try {
      builder.add("a", "b", (String) null);
      fail("expected NullPointerException"); // COV_NF_LINE
    } catch (NullPointerException expected) {
    }

    builder = this.<String>builder();
    try {
      builder.add("a", "b", "c", null);
      fail("expected NullPointerException"); // COV_NF_LINE
    } catch (NullPointerException expected) {
    }

    builder = this.<String>builder();
    try {
      builder.add("a", "b", null, "c");
      fail("expected NullPointerException"); // COV_NF_LINE
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void testBuilderAddAllHandlesNullsCorrectly() {
    ImmutableSet.Builder<String> builder = this.<String>builder();
    try {
      builder.addAll((Iterable<String>) null);
      fail("expected NullPointerException"); // COV_NF_LINE
    } catch (NullPointerException expected) {
    }

    try {
      builder.addAll((Iterator<String>) null);
      fail("expected NullPointerException"); // COV_NF_LINE
    } catch (NullPointerException expected) {
    }

    builder = this.<String>builder();
    List<String> listWithNulls = asList("a", null, "b");
    try {
      builder.addAll(listWithNulls);
      fail("expected NullPointerException"); // COV_NF_LINE
    } catch (NullPointerException expected) {
    }

    Iterable<String> iterableWithNulls = MinimalIterable.of("a", null, "b");
    try {
      builder.addAll(iterableWithNulls);
      fail("expected NullPointerException"); // COV_NF_LINE
    } catch (NullPointerException expected) {
    }
  }

  /**
   * Verify thread safety by using a collection whose size() may be inconsistent with the actual
   * number of elements. Tests using this method might fail in GWT because the GWT emulations might
   * count on size() during copy. It is safe to do so in GWT because javascript is single-threaded.
   */
  // TODO(benyu): turn this into a test once all copyOf(Collection) are
  // thread-safe
  void verifyThreadSafe() {
    List<String> sample = Lists.newArrayList("a", "b", "c");
    for (int delta : new int[] {-1, 0, 1}) {
      for (int i = 0; i < sample.size(); i++) {
        Collection<String> misleading = Helpers.misleadingSizeCollection(delta);
        List<String> expected = sample.subList(0, i);
        misleading.addAll(expected);
        assertEquals(
            "delta: " + delta + " sample size: " + i,
            Helpers.newHashSet(expected),
            copyOf(misleading));
      }
    }
  }
}
