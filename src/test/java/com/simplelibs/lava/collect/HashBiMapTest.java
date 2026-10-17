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

import static org.assertj.core.api.Assertions.assertThat;

import com.simplelibs.lava.testing.LavaTestCase;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link HashBiMap}.
 *
 * @author Mike Bostock
 */
public class HashBiMapTest extends LavaTestCase {

  @Test
  public void testMapConstructor() {
    /* Test with non-empty Map. */
    Map<String, String> map =
        ImmutableMap.of(
            "canada", "dollar",
            "chile", "peso",
            "switzerland", "franc");
    HashBiMap<String, String> bimap = HashBiMap.create(map);
    assertEquals("dollar", bimap.get("canada"));
    assertEquals("canada", bimap.inverse().get("dollar"));
  }

  private static final int N = 1000;

  @Test
  public void testBashIt() throws Exception {
    BiMap<Integer, Integer> bimap = HashBiMap.create(N);
    BiMap<Integer, Integer> inverse = bimap.inverse();

    for (int i = 0; i < N; i++) {
      assertNull(bimap.put(2 * i, 2 * i + 1));
    }
    for (int i = 0; i < N; i++) {
      assertEquals(2 * i + 1, (int) bimap.get(2 * i));
    }
    for (int i = 0; i < N; i++) {
      assertEquals(2 * i, (int) inverse.get(2 * i + 1));
    }
    for (int i = 0; i < N; i++) {
      int oldValue = bimap.get(2 * i);
      assertEquals(2 * i + 1, (int) bimap.put(2 * i, oldValue - 2));
    }
    for (int i = 0; i < N; i++) {
      assertEquals(2 * i - 1, (int) bimap.get(2 * i));
    }
    for (int i = 0; i < N; i++) {
      assertEquals(2 * i, (int) inverse.get(2 * i - 1));
    }
    Set<Entry<Integer, Integer>> entries = bimap.entrySet();
    for (Entry<Integer, Integer> entry : entries) {
      entry.setValue(entry.getValue() + 2 * N);
    }
    for (int i = 0; i < N; i++) {
      assertEquals(2 * N + 2 * i - 1, (int) bimap.get(2 * i));
    }
  }

  @Test
  public void testBiMapEntrySetIteratorRemove() {
    BiMap<Integer, String> map = HashBiMap.create();
    map.put(1, "one");
    Set<Entry<Integer, String>> entries = map.entrySet();
    Iterator<Entry<Integer, String>> iterator = entries.iterator();
    Entry<Integer, String> entry = iterator.next();
    entry.setValue("two"); // changes the iterator's current entry value
    assertEquals("two", map.get(1));
    assertEquals(Integer.valueOf(1), map.inverse().get("two"));
    iterator.remove(); // removes the updated entry
    assertTrue(map.isEmpty());
  }

  @Test
  public void testInsertionOrder() {
    BiMap<String, Integer> map = HashBiMap.create();
    map.put("foo", 1);
    map.put("bar", 2);
    map.put("quux", 3);
    assertThat(map.entrySet())
        .containsExactly(
            Maps.immutableEntry("foo", 1),
            Maps.immutableEntry("bar", 2),
            Maps.immutableEntry("quux", 3));
  }

  @Test
  public void testInsertionOrderAfterRemoveFirst() {
    BiMap<String, Integer> map = HashBiMap.create();
    map.put("foo", 1);
    map.put("bar", 2);
    map.put("quux", 3);

    map.remove("foo");
    assertThat(map.entrySet())
        .containsExactly(Maps.immutableEntry("bar", 2), Maps.immutableEntry("quux", 3));
  }

  @Test
  public void testInsertionOrderAfterRemoveMiddle() {
    BiMap<String, Integer> map = HashBiMap.create();
    map.put("foo", 1);
    map.put("bar", 2);
    map.put("quux", 3);

    map.remove("bar");
    assertThat(map.entrySet())
        .containsExactly(Maps.immutableEntry("foo", 1), Maps.immutableEntry("quux", 3));
  }

  @Test
  public void testInsertionOrderAfterRemoveLast() {
    BiMap<String, Integer> map = HashBiMap.create();
    map.put("foo", 1);
    map.put("bar", 2);
    map.put("quux", 3);

    map.remove("quux");
    assertThat(map.entrySet())
        .containsExactly(Maps.immutableEntry("foo", 1), Maps.immutableEntry("bar", 2));
  }

  @Test
  public void testInsertionOrderAfterForcePut() {
    BiMap<String, Integer> map = HashBiMap.create();
    map.put("foo", 1);
    map.put("bar", 2);
    map.put("quux", 3);

    map.forcePut("quux", 1);
    assertThat(map.entrySet())
        .containsExactly(Maps.immutableEntry("bar", 2), Maps.immutableEntry("quux", 1));
  }

  @Test
  public void testInsertionOrderAfterInverseForcePut() {
    BiMap<String, Integer> map = HashBiMap.create();
    map.put("foo", 1);
    map.put("bar", 2);
    map.put("quux", 3);

    map.inverse().forcePut(1, "quux");
    assertThat(map.entrySet())
        .containsExactly(Maps.immutableEntry("bar", 2), Maps.immutableEntry("quux", 1));
  }

  @Test
  public void testInverseInsertionOrderAfterInverse() {
    BiMap<String, Integer> map = HashBiMap.create();
    map.put("bar", 2);
    map.put("quux", 1);

    assertThat(map.inverse().entrySet())
        .containsExactly(Maps.immutableEntry(2, "bar"), Maps.immutableEntry(1, "quux"));
  }

  @Test
  public void testInverseInsertionOrderAfterInverseForcePut() {
    BiMap<String, Integer> map = HashBiMap.create();
    map.put("foo", 1);
    map.put("bar", 2);
    map.put("quux", 3);

    map.inverse().forcePut(1, "quux");
    assertThat(map.inverse().entrySet())
        .containsExactly(Maps.immutableEntry(2, "bar"), Maps.immutableEntry(1, "quux"));
  }

  @Test
  public void testInverseInsertionOrderAfterInverseForcePutPresentKey() {
    BiMap<String, Integer> map = HashBiMap.create();
    map.put("foo", 1);
    map.put("bar", 2);
    map.put("quux", 3);
    map.put("nab", 4);

    map.inverse().forcePut(4, "bar");
    assertThat(map.entrySet())
        .containsExactly(
            Maps.immutableEntry("foo", 1),
            Maps.immutableEntry("bar", 4),
            Maps.immutableEntry("quux", 3));
  }

  @Test
  public void testInverseEntrySetValueNewKey() {
    BiMap<Integer, String> map = HashBiMap.create();
    map.put(1, "a");
    map.put(2, "b");
    Iterator<Entry<String, Integer>> inverseEntryItr = map.inverse().entrySet().iterator();
    Entry<String, Integer> entry = inverseEntryItr.next();
    entry.setValue(3);
    assertEquals(Maps.immutableEntry("b", 2), inverseEntryItr.next());
    assertFalse(inverseEntryItr.hasNext());
    assertThat(map.entrySet())
        .containsExactly(Maps.immutableEntry(2, "b"), Maps.immutableEntry(3, "a"));
  }
}
