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

import com.simplelibs.lava.checker.nullness.qual.Nullable;
import com.simplelibs.lava.collect.testing.MapInterfaceTest;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Maps#transformValues}.
 *
 * @author Isaac Shum
 */
public class MapsTransformValuesTest extends MapInterfaceTest<String, String> {

  /**
   * Constructor that assigns {@code supportsIteratorRemove} the same value as {@code
   * supportsRemove}.
   */
  public MapsTransformValuesTest() {
    super(false, true, false, true, true);
  }

  protected Map<String, String> makeEmptyMap() {
    return Maps.transformValues(Maps.<String, String>newHashMap(), GuavaStandIns.<String>identity());
  }

  @Override
  protected Map<String, String> makePopulatedMap() {
    Map<String, Integer> underlying = Maps.newHashMap();
    underlying.put("a", 1);
    underlying.put("b", 2);
    underlying.put("c", 3);
    return Maps.transformValues(underlying, GuavaStandIns.toStringFunction());
  }

  @Override
  protected String getKeyNotInPopulatedMap() throws UnsupportedOperationException {
    return "z";
  }

  @Override
  protected String getValueNotInPopulatedMap() throws UnsupportedOperationException {
    return "26";
  }

  /** Helper assertion comparing two maps */
  private void assertMapsEqual(Map<?, ?> expected, Map<?, ?> map) {
    assertEquals(expected, map);
    assertEquals(expected.hashCode(), map.hashCode());
    assertEquals(expected.entrySet(), map.entrySet());

    // Assert that expectedValues > mapValues and that
    // mapValues > expectedValues; i.e. that expectedValues == mapValues.
    Collection<?> expectedValues = expected.values();
    Collection<?> mapValues = map.values();
    assertEquals(expectedValues.size(), mapValues.size());
    assertTrue(expectedValues.containsAll(mapValues));
    assertTrue(mapValues.containsAll(expectedValues));
  }

  @Test
  public void testTransformEmptyMapEquality() {
    Map<String, String> map =
        Maps.transformValues(ImmutableMap.<String, Integer>of(), GuavaStandIns.toStringFunction());
    assertMapsEqual(Maps.newHashMap(), map);
  }

  @Test
  public void testTransformSingletonMapEquality() {
    Map<String, String> map =
        Maps.transformValues(ImmutableMap.of("a", 1), GuavaStandIns.toStringFunction());
    Map<String, String> expected = ImmutableMap.of("a", "1");
    assertMapsEqual(expected, map);
    assertEquals(expected.get("a"), map.get("a"));
  }

  @Test
  public void testTransformIdentityFunctionEquality() {
    Map<String, Integer> underlying = ImmutableMap.of("a", 1);
    Map<String, Integer> map = Maps.transformValues(underlying, GuavaStandIns.<Integer>identity());
    assertMapsEqual(underlying, map);
  }

  @Test
  public void testTransformPutEntryIsUnsupported() {
    Map<String, String> map =
        Maps.transformValues(ImmutableMap.of("a", 1), GuavaStandIns.toStringFunction());
    try {
      map.put("b", "2");
      fail();
    } catch (UnsupportedOperationException expected) {
    }

    try {
      map.putAll(ImmutableMap.of("b", "2"));
      fail();
    } catch (UnsupportedOperationException expected) {
    }

    try {
      map.entrySet().iterator().next().setValue("one");
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  @Test
  public void testTransformRemoveEntry() {
    Map<String, Integer> underlying = Maps.newHashMap();
    underlying.put("a", 1);
    Map<String, String> map = Maps.transformValues(underlying, GuavaStandIns.toStringFunction());
    assertEquals("1", map.remove("a"));
    assertNull(map.remove("b"));
  }

  @Test
  public void testTransformEqualityOfMapsWithNullValues() {
    Map<String, String> underlying = Maps.newHashMap();
    underlying.put("a", null);
    underlying.put("b", "");

    Map<String, Boolean> map =
        Maps.transformValues(
            underlying,
            new Function<String, Boolean>() {
              @Override
              public Boolean apply(@Nullable String from) {
                return from == null;
              }
            });
    Map<String, Boolean> expected = ImmutableMap.of("a", true, "b", false);
    assertMapsEqual(expected, map);
    assertEquals(expected.get("a"), map.get("a"));
    assertEquals(expected.containsKey("a"), map.containsKey("a"));
    assertEquals(expected.get("b"), map.get("b"));
    assertEquals(expected.containsKey("b"), map.containsKey("b"));
    assertEquals(expected.get("c"), map.get("c"));
    assertEquals(expected.containsKey("c"), map.containsKey("c"));
  }

  @Test
  public void testTransformReflectsUnderlyingMap() {
    Map<String, Integer> underlying = Maps.newHashMap();
    underlying.put("a", 1);
    underlying.put("b", 2);
    underlying.put("c", 3);
    Map<String, String> map = Maps.transformValues(underlying, GuavaStandIns.toStringFunction());
    assertEquals(underlying.size(), map.size());

    underlying.put("d", 4);
    assertEquals(underlying.size(), map.size());
    assertEquals("4", map.get("d"));

    underlying.remove("c");
    assertEquals(underlying.size(), map.size());
    assertFalse(map.containsKey("c"));

    underlying.clear();
    assertEquals(underlying.size(), map.size());
  }

  @Test
  public void testTransformChangesAreReflectedInUnderlyingMap() {
    Map<String, Integer> underlying = Maps.newLinkedHashMap();
    underlying.put("a", 1);
    underlying.put("b", 2);
    underlying.put("c", 3);
    underlying.put("d", 4);
    underlying.put("e", 5);
    underlying.put("f", 6);
    underlying.put("g", 7);
    Map<String, String> map = Maps.transformValues(underlying, GuavaStandIns.toStringFunction());

    map.remove("a");
    assertFalse(underlying.containsKey("a"));

    Set<String> keys = map.keySet();
    keys.remove("b");
    assertFalse(underlying.containsKey("b"));

    Iterator<String> keyIterator = keys.iterator();
    keyIterator.next();
    keyIterator.remove();
    assertFalse(underlying.containsKey("c"));

    Collection<String> values = map.values();
    values.remove("4");
    assertFalse(underlying.containsKey("d"));

    Iterator<String> valueIterator = values.iterator();
    valueIterator.next();
    valueIterator.remove();
    assertFalse(underlying.containsKey("e"));

    Set<Entry<String, String>> entries = map.entrySet();
    Entry<String, String> firstEntry = entries.iterator().next();
    entries.remove(firstEntry);
    assertFalse(underlying.containsKey("f"));

    Iterator<Entry<String, String>> entryIterator = entries.iterator();
    entryIterator.next();
    entryIterator.remove();
    assertFalse(underlying.containsKey("g"));

    assertTrue(underlying.isEmpty());
    assertTrue(map.isEmpty());
    assertTrue(keys.isEmpty());
    assertTrue(values.isEmpty());
    assertTrue(entries.isEmpty());
  }

  @Test
  public void testTransformEquals() {
    Map<String, Integer> underlying = ImmutableMap.of("a", 0, "b", 1, "c", 2);
    Map<String, Integer> expected = Maps.transformValues(underlying, GuavaStandIns.<Integer>identity());

    assertMapsEqual(expected, expected);

    Map<String, Integer> equalToUnderlying = Maps.newTreeMap();
    equalToUnderlying.putAll(underlying);
    Map<String, Integer> map =
        Maps.transformValues(equalToUnderlying, GuavaStandIns.<Integer>identity());
    assertMapsEqual(expected, map);

    map =
        Maps.transformValues(
            ImmutableMap.of("a", 1, "b", 2, "c", 3),
            new Function<Integer, Integer>() {
              @Override
              public Integer apply(Integer from) {
                return from - 1;
              }
            });
    assertMapsEqual(expected, map);
  }

  @Test
  public void testTransformEntrySetContains() {
    Map<String, Boolean> underlying = Maps.newHashMap();
    underlying.put("a", null);
    underlying.put("b", true);
    underlying.put(null, true);

    Map<String, Boolean> map =
        Maps.transformValues(
            underlying,
            new Function<Boolean, Boolean>() {
              @Override
              public Boolean apply(@Nullable Boolean from) {
                return (from == null) ? true : null;
              }
            });

    Set<Entry<String, Boolean>> entries = map.entrySet();
    assertTrue(entries.contains(Maps.immutableEntry("a", true)));
    assertTrue(entries.contains(Maps.immutableEntry("b", (Boolean) null)));
    assertTrue(entries.contains(Maps.immutableEntry((String) null, (Boolean) null)));

    assertFalse(entries.contains(Maps.immutableEntry("c", (Boolean) null)));
    assertFalse(entries.contains(Maps.immutableEntry((String) null, true)));
  }

  @Override
  @Test
  public void testKeySetRemoveAllNullFromEmpty() {
    try {
      super.testKeySetRemoveAllNullFromEmpty();
    } catch (RuntimeException tolerated) {
      // GWT's HashMap.keySet().removeAll(null) doesn't throws NPE.
    }
  }

  @Override
  @Test
  public void testEntrySetRemoveAllNullFromEmpty() {
    try {
      super.testEntrySetRemoveAllNullFromEmpty();
    } catch (RuntimeException tolerated) {
      // GWT's HashMap.entrySet().removeAll(null) doesn't throws NPE.
    }
  }
}
