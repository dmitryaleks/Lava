/*
 * Copyright (C) 2012 The Guava Authors
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

import static com.simplelibs.lava.base.Preconditions.checkState;
import static com.simplelibs.lava.collect.testing.Helpers.mapEntry;
import static org.assertj.core.api.Assertions.assertThat;

import com.simplelibs.lava.base.Equivalence;
import com.simplelibs.lava.collect.testing.AnEnum;
import com.simplelibs.lava.collect.testing.Helpers;
import com.simplelibs.lava.testing.CollectorTester;
import com.simplelibs.lava.testing.LavaTestCase;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@code ImmutableEnumMap}.
 *
 * @author Louis Wasserman
 */
public class ImmutableEnumMapTest extends LavaTestCase {

  @Test
  public void testIteratesOnce() {
    Map<AnEnum, AnEnum> map =
        Maps.asMap(
            ImmutableSet.of(AnEnum.A),
            new Function<AnEnum, AnEnum>() {
              boolean used = false;

              @Override
              public AnEnum apply(AnEnum ae) {
                checkState(!used, "should not be applied more than once");
                used = true;
                return ae;
              }
            });
    ImmutableMap<AnEnum, AnEnum> copy = Maps.immutableEnumMap(map);
    assertThat(copy.entrySet()).containsExactlyInAnyOrder(Helpers.mapEntry(AnEnum.A, AnEnum.A));
  }

  @Test
  public void testEmptyImmutableEnumMap() {
    ImmutableMap<AnEnum, String> map = Maps.immutableEnumMap(ImmutableMap.<AnEnum, String>of());
    assertEquals(ImmutableMap.of(), map);
  }

  @Test
  public void testImmutableEnumMapOrdering() {
    ImmutableMap<AnEnum, String> map =
        Maps.immutableEnumMap(ImmutableMap.of(AnEnum.C, "c", AnEnum.A, "a", AnEnum.E, "e"));

    assertThat(map.entrySet())
        .containsExactly(
            Helpers.mapEntry(AnEnum.A, "a"),
            Helpers.mapEntry(AnEnum.C, "c"),
            Helpers.mapEntry(AnEnum.E, "e"));
  }

  @Test
  public void testToImmutableEnumMap() {
    Collector<Entry<AnEnum, Integer>, ?, ImmutableMap<AnEnum, Integer>> collector =
        Maps.toImmutableEnumMap(Entry::getKey, Entry::getValue);
    Equivalence<ImmutableMap<AnEnum, Integer>> equivalence =
        Equivalence.equals().<Entry<AnEnum, Integer>>pairwise().onResultOf(ImmutableMap::entrySet);
    CollectorTester.of(collector, equivalence)
        .expectCollects(
            ImmutableMap.of(AnEnum.A, 1, AnEnum.C, 2, AnEnum.E, 3),
            mapEntry(AnEnum.A, 1),
            mapEntry(AnEnum.C, 2),
            mapEntry(AnEnum.E, 3));
  }

  @Test
  public void testToImmutableMap_exceptionOnDuplicateKey() {
    Collector<Entry<AnEnum, Integer>, ?, ImmutableMap<AnEnum, Integer>> collector =
        Maps.toImmutableEnumMap(Entry::getKey, Entry::getValue);
    try {
      Stream.of(mapEntry(AnEnum.A, 1), mapEntry(AnEnum.A, 11)).collect(collector);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testToImmutableMapMerging() {
    Collector<Entry<AnEnum, Integer>, ?, ImmutableMap<AnEnum, Integer>> collector =
        Maps.toImmutableEnumMap(Entry::getKey, Entry::getValue, Integer::sum);
    Equivalence<ImmutableMap<AnEnum, Integer>> equivalence =
        Equivalence.equals().<Entry<AnEnum, Integer>>pairwise().onResultOf(ImmutableMap::entrySet);
    CollectorTester.of(collector, equivalence)
        .expectCollects(
            ImmutableMap.of(AnEnum.A, 1, AnEnum.B, 4, AnEnum.C, 3),
            mapEntry(AnEnum.A, 1),
            mapEntry(AnEnum.B, 2),
            mapEntry(AnEnum.C, 3),
            mapEntry(AnEnum.B, 2));
  }
}
