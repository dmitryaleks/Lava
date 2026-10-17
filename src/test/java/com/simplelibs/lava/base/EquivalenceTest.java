/*
 * Copyright (C) 2010 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * diOBJECTibuted under the License is diOBJECTibuted on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified for Lava: single-threaded, Guava-independent port of Guava 27.0.1.
 */

package com.simplelibs.lava.base;

import com.simplelibs.lava.base.Equivalence.Wrapper;
import java.util.List;
import com.simplelibs.lava.testing.EqualsTester;
import com.simplelibs.lava.testing.EquivalenceTester;
import com.simplelibs.lava.testing.LavaTestCase;
import com.simplelibs.lava.testing.NullPointerTester;
import com.simplelibs.lava.testing.SerializableTester;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link Equivalence}.
 *
 * @author Jige Yu
 */
public class EquivalenceTest extends LavaTestCase {
  @SuppressWarnings("unchecked") // varargs
  @Test
  public void testPairwiseEquivalent() {
    EquivalenceTester.of(Equivalence.equals().<String>pairwise())
        .addEquivalenceGroup(List.<String>of())
        .addEquivalenceGroup(List.of("a"))
        .addEquivalenceGroup(List.of("b"))
        .addEquivalenceGroup(List.of("a", "b"), List.of("a", "b"))
        .test();
  }

  @Test
  public void testPairwiseEquivalent_equals() {
    new EqualsTester()
        .addEqualityGroup(Equivalence.equals().pairwise(), Equivalence.equals().pairwise())
        .addEqualityGroup(Equivalence.identity().pairwise())
        .testEquals();
  }

  /** Stand-in for Guava's {@code Functions.toStringFunction()}. */
  private enum ToStringFunction implements Function<Object, String> {
    INSTANCE;

    @Override
    public String apply(Object o) {
      return o.toString();
    }

    @Override
    public String toString() {
      return "Functions.toStringFunction()";
    }
  }

  /** Stand-in for Guava's {@code Functions.identity()}. */
  private enum IdentityFunction implements Function<Object, Object> {
    INSTANCE;

    @Override
    public Object apply(Object o) {
      return o;
    }

    @Override
    public String toString() {
      return "Functions.identity()";
    }
  }

  private enum LengthFunction implements Function<String, Integer> {
    INSTANCE;

    @Override
    public Integer apply(String input) {
      return input.length();
    }
  }

  private static final Equivalence<String> LENGTH_EQUIVALENCE =
      Equivalence.equals().onResultOf(LengthFunction.INSTANCE);

  @Test
  public void testWrap() {
    new EqualsTester()
        .addEqualityGroup(
            LENGTH_EQUIVALENCE.wrap("hello"),
            LENGTH_EQUIVALENCE.wrap("hello"),
            LENGTH_EQUIVALENCE.wrap("world"))
        .addEqualityGroup(LENGTH_EQUIVALENCE.wrap("hi"), LENGTH_EQUIVALENCE.wrap("yo"))
        .addEqualityGroup(LENGTH_EQUIVALENCE.wrap(null), LENGTH_EQUIVALENCE.wrap(null))
        .addEqualityGroup(Equivalence.equals().wrap("hello"))
        .addEqualityGroup(Equivalence.equals().wrap(null))
        .testEquals();
  }

  @Test
  public void testWrap_get() {
    String test = "test";
    Wrapper<String> wrapper = LENGTH_EQUIVALENCE.wrap(test);
    assertSame(test, wrapper.get());
  }

  @Test
  public void testSerialization() {
    SerializableTester.reserializeAndAssert(LENGTH_EQUIVALENCE.wrap("hello"));
    SerializableTester.reserializeAndAssert(Equivalence.equals());
    SerializableTester.reserializeAndAssert(Equivalence.identity());
  }

  private static class IntValue {
    private final int value;

    IntValue(int value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return "value = " + value;
    }
  }

  @Test
  public void testOnResultOf() {
    EquivalenceTester.of(Equivalence.equals().onResultOf(ToStringFunction.INSTANCE))
        .addEquivalenceGroup(new IntValue(1), new IntValue(1))
        .addEquivalenceGroup(new IntValue(2))
        .test();
  }

  @Test
  public void testOnResultOf_equals() {
    new EqualsTester()
        .addEqualityGroup(
            Equivalence.identity().onResultOf(ToStringFunction.INSTANCE),
            Equivalence.identity().onResultOf(ToStringFunction.INSTANCE))
        .addEqualityGroup(Equivalence.equals().onResultOf(ToStringFunction.INSTANCE))
        .addEqualityGroup(Equivalence.identity().onResultOf(IdentityFunction.INSTANCE))
        .testEquals();
  }

  @Test
  public void testEquivalentTo() {
    Predicate<Object> equalTo1 = Equivalence.equals().equivalentTo("1");
    assertTrue(equalTo1.test("1"));
    assertFalse(equalTo1.test("2"));
    assertFalse(equalTo1.test(null));
    Predicate<Object> isNull = Equivalence.equals().equivalentTo(null);
    assertFalse(isNull.test("1"));
    assertFalse(isNull.test("2"));
    assertTrue(isNull.test(null));

    new EqualsTester()
        .addEqualityGroup(equalTo1, Equivalence.equals().equivalentTo("1"))
        .addEqualityGroup(isNull)
        .addEqualityGroup(Equivalence.identity().equivalentTo("1"))
        .testEquals();
  }

  @Test
  public void testEqualsEquivalent() {
    EquivalenceTester.of(Equivalence.equals())
        .addEquivalenceGroup(new Integer(42), 42)
        .addEquivalenceGroup("a")
        .test();
  }

  @Test
  public void testIdentityEquivalent() {
    EquivalenceTester.of(Equivalence.identity())
        .addEquivalenceGroup(new Integer(42))
        .addEquivalenceGroup(new Integer(42))
        .addEquivalenceGroup("a")
        .test();
  }

  @Test
  public void testEquals() {
    new EqualsTester()
        .addEqualityGroup(Equivalence.equals(), Equivalence.equals())
        .addEqualityGroup(Equivalence.identity(), Equivalence.identity())
        .testEquals();
  }

  @Test
  public void testNulls() {
    new NullPointerTester().testAllPublicStaticMethods(Equivalence.class);
    new NullPointerTester().testAllPublicInstanceMethods(Equivalence.equals());
    new NullPointerTester().testAllPublicInstanceMethods(Equivalence.identity());
  }
}
