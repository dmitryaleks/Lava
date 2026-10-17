/*
 * Copyright (C) 2011 The Guava Authors
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

package com.simplelibs.lava.base;

import static com.simplelibs.lava.testing.SerializableTester.reserialize;
import static org.assertj.core.api.Assertions.assertThat;

import com.simplelibs.lava.testing.EqualsTester;
import com.simplelibs.lava.testing.LavaTestCase;
import com.simplelibs.lava.testing.NullPointerTester;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link Optional}.
 *
 * @author Kurt Alfred Kluever
 */
public final class OptionalTest extends LavaTestCase {
  @Test
  public void testToJavaUtil_static() {
    assertNull(Optional.toJavaUtil(null));
    assertEquals(java.util.Optional.empty(), Optional.toJavaUtil(Optional.absent()));
    assertEquals(java.util.Optional.of("abc"), Optional.toJavaUtil(Optional.of("abc")));
  }

  @Test
  public void testToJavaUtil_instance() {
    assertEquals(java.util.Optional.empty(), Optional.absent().toJavaUtil());
    assertEquals(java.util.Optional.of("abc"), Optional.of("abc").toJavaUtil());
  }

  @Test
  public void testFromJavaUtil() {
    assertNull(Optional.fromJavaUtil(null));
    assertEquals(Optional.absent(), Optional.fromJavaUtil(java.util.Optional.empty()));
    assertEquals(Optional.of("abc"), Optional.fromJavaUtil(java.util.Optional.of("abc")));
  }

  @Test
  public void testAbsent() {
    Optional<String> optionalName = Optional.absent();
    assertFalse(optionalName.isPresent());
  }

  @Test
  public void testOf() {
    assertEquals("training", Optional.of("training").get());
  }

  @Test
  public void testOf_null() {
    try {
      Optional.of(null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void testFromNullable() {
    Optional<String> optionalName = Optional.fromNullable("bob");
    assertEquals("bob", optionalName.get());
  }

  @Test
  public void testFromNullable_null() {
    // not promised by spec, but easier to test
    assertSame(Optional.absent(), Optional.fromNullable(null));
  }

  @Test
  public void testIsPresent_no() {
    assertFalse(Optional.absent().isPresent());
  }

  @Test
  public void testIsPresent_yes() {
    assertTrue(Optional.of("training").isPresent());
  }

  @Test
  public void testGet_absent() {
    Optional<String> optional = Optional.absent();
    try {
      optional.get();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testGet_present() {
    assertEquals("training", Optional.of("training").get());
  }

  @Test
  public void testOr_T_present() {
    assertEquals("a", Optional.of("a").or("default"));
  }

  @Test
  public void testOr_T_absent() {
    assertEquals("default", Optional.absent().or("default"));
  }

  @Test
  public void testOr_supplier_present() {
    assertEquals("a", Optional.of("a").or(ofInstance("fallback")));
  }

  @Test
  public void testOr_supplier_absent() {
    assertEquals("fallback", Optional.absent().or(ofInstance("fallback")));
  }

  @Test
  public void testOr_nullSupplier_absent() {
    Supplier<Object> nullSupplier = ofInstance(null);
    Optional<Object> absentOptional = Optional.absent();
    try {
      absentOptional.or(nullSupplier);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void testOr_nullSupplier_present() {
    Supplier<String> nullSupplier = ofInstance(null);
    assertEquals("a", Optional.of("a").or(nullSupplier));
  }

  @Test
  public void testOr_Optional_present() {
    assertEquals(Optional.of("a"), Optional.of("a").or(Optional.of("fallback")));
  }

  @Test
  public void testOr_Optional_absent() {
    assertEquals(Optional.of("fallback"), Optional.absent().or(Optional.of("fallback")));
  }

  @Test
  public void testOrNull_present() {
    assertEquals("a", Optional.of("a").orNull());
  }

  @Test
  public void testOrNull_absent() {
    assertNull(Optional.absent().orNull());
  }

  @Test
  public void testAsSet_present() {
    Set<String> expected = Collections.singleton("a");
    assertEquals(expected, Optional.of("a").asSet());
  }

  @Test
  public void testAsSet_absent() {
    assertTrue("Returned set should be empty", Optional.absent().asSet().isEmpty());
  }

  @Test
  public void testAsSet_presentIsImmutable() {
    Set<String> presentAsSet = Optional.of("a").asSet();
    try {
      presentAsSet.add("b");
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  @Test
  public void testAsSet_absentIsImmutable() {
    Set<Object> absentAsSet = Optional.absent().asSet();
    try {
      absentAsSet.add("foo");
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  @Test
  public void testTransform_absent() {
    assertEquals(Optional.absent(), Optional.absent().transform(Function.identity()));
    assertEquals(Optional.absent(), Optional.absent().transform(ToStringFunction.INSTANCE));
  }

  @Test
  public void testTransform_presentIdentity() {
    assertEquals(Optional.of("a"), Optional.of("a").transform(Function.identity()));
  }

  @Test
  public void testTransform_presentToString() {
    assertEquals(Optional.of("42"), Optional.of(42).transform(ToStringFunction.INSTANCE));
  }

  @Test
  public void testTransform_present_functionReturnsNull() {
    try {
      Optional<String> unused =
          Optional.of("a")
              .transform(
                  new Function<String, String>() {
                    @Override
                    public String apply(String input) {
                      return null;
                    }
                  });
      fail("Should throw if Function returns null.");
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void testTransform_absent_functionReturnsNull() {
    assertEquals(
        Optional.absent(),
        Optional.absent()
            .transform(
                new Function<Object, Object>() {
                  @Override
                  public Object apply(Object input) {
                    return null;
                  }
                }));
  }

  @Test
  public void testEqualsAndHashCode() {
    new EqualsTester()
        .addEqualityGroup(Optional.absent(), reserialize(Optional.absent()))
        .addEqualityGroup(Optional.of(Long.valueOf(5)), reserialize(Optional.of(Long.valueOf(5))))
        .addEqualityGroup(Optional.of(Long.valueOf(42)), reserialize(Optional.of(Long.valueOf(42))))
        .testEquals();
  }

  @Test
  public void testToString_absent() {
    assertEquals("Optional.absent()", Optional.absent().toString());
  }

  @Test
  public void testToString_present() {
    assertEquals("Optional.of(training)", Optional.of("training").toString());
  }

  @Test
  public void testPresentInstances_allPresent() {
    List<Optional<String>> optionals =
        List.of(Optional.of("a"), Optional.of("b"), Optional.of("c"));
    assertThat(Optional.presentInstances(optionals)).containsExactly("a", "b", "c");
  }

  @Test
  public void testPresentInstances_allAbsent() {
    List<Optional<Object>> optionals = java.util.Arrays.asList(Optional.absent(), Optional.absent());
    assertThat(Optional.presentInstances(optionals)).isEmpty();
  }

  @Test
  public void testPresentInstances_somePresent() {
    List<Optional<String>> optionals =
        List.of(Optional.of("a"), Optional.<String>absent(), Optional.of("c"));
    assertThat(Optional.presentInstances(optionals)).containsExactly("a", "c");
  }

  @Test
  public void testPresentInstances_callingIteratorTwice() {
    List<Optional<String>> optionals =
        List.of(Optional.of("a"), Optional.<String>absent(), Optional.of("c"));
    Iterable<String> onlyPresent = Optional.presentInstances(optionals);
    assertThat(onlyPresent).containsExactly("a", "c");
    assertThat(onlyPresent).containsExactly("a", "c");
  }

  @Test
  public void testPresentInstances_wildcards() {
    List<Optional<? extends Number>> optionals =
        List.<Optional<? extends Number>>of(Optional.<Double>absent(), Optional.of(2));
    Iterable<Number> onlyPresent = Optional.presentInstances(optionals);
    assertThat(onlyPresent).containsExactly(2);
  }

  private static Optional<Integer> getSomeOptionalInt() {
    return Optional.of(1);
  }

  /*
   * The following tests demonstrate the shortcomings of or() and test that the casting workaround
   * mentioned in the method Javadoc does in fact compile.
   */

  @SuppressWarnings("unused") // compilation test
  @Test
  public void testSampleCodeError1() {
    Optional<Integer> optionalInt = getSomeOptionalInt();
    // Number value = optionalInt.or(0.5); // error
  }

  @SuppressWarnings("unused") // compilation test
  @Test
  public void testSampleCodeFine1() {
    Optional<Number> optionalInt = Optional.of((Number) 1);
    Number value = optionalInt.or(0.5); // fine
  }

  /** Stand-in for Guava's {@code Suppliers.ofInstance}. */
  private static <T> Supplier<T> ofInstance(T instance) {
    return () -> instance;
  }

  /** Stand-in for Guava's {@code Functions.toStringFunction()}. */
  private enum ToStringFunction implements Function<Object, String> {
    INSTANCE;

    @Override
    public String apply(Object o) {
      return o.toString();
    }
  }

  @Test
  public void testNullPointers() {
    NullPointerTester npTester = new NullPointerTester();
    npTester.testAllPublicConstructors(Optional.class);
    npTester.testAllPublicStaticMethods(Optional.class);
    npTester.testAllPublicInstanceMethods(Optional.absent());
    npTester.testAllPublicInstanceMethods(Optional.of("training"));
  }

  /** Lava: presentInstances is a lazy view of its input. */
  @Test
  public void testPresentInstances_isLazyView() {
    List<Optional<String>> optionals = new java.util.ArrayList<>();
    Iterable<String> onlyPresent = Optional.presentInstances(optionals);
    assertThat(onlyPresent).isEmpty();
    optionals.add(Optional.of("a"));
    optionals.add(Optional.<String>absent());
    optionals.add(Optional.of("b"));
    assertThat(onlyPresent).containsExactly("a", "b");
  }
}
