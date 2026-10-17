/*
 * Copyright (C) 2006 The Guava Authors
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

import static org.assertj.core.api.Assertions.assertThat;

import com.simplelibs.lava.testing.LavaTestCase;
import com.simplelibs.lava.testing.NullPointerTester;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

/**
 * Unit test for {@link Preconditions}.
 *
 * @author Kevin Bourrillion
 * @author Jared Levy
 */
public class PreconditionsTest extends LavaTestCase {
  @Test
  public void testCheckArgument_simple_success() {
    Preconditions.checkArgument(true);
  }

  @Test
  public void testCheckArgument_simple_failure() {
    try {
      Preconditions.checkArgument(false);
      fail("no exception thrown");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testCheckArgument_simpleMessage_success() {
    Preconditions.checkArgument(true, IGNORE_ME);
  }

  @Test
  public void testCheckArgument_simpleMessage_failure() {
    try {
      Preconditions.checkArgument(false, new Message());
      fail("no exception thrown");
    } catch (IllegalArgumentException expected) {
      verifySimpleMessage(expected);
    }
  }

  @Test
  public void testCheckArgument_nullMessage_failure() {
    try {
      Preconditions.checkArgument(false, null);
      fail("no exception thrown");
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessage("null");
    }
  }

  @Test
  public void testCheckArgument_nullMessageWithArgs_failure() {
    try {
      Preconditions.checkArgument(false, null, "b", "d");
      fail("no exception thrown");
    } catch (IllegalArgumentException e) {
      assertThat(e).message().isEqualTo("null [b, d]");
    }
  }

  @Test
  public void testCheckArgument_nullArgs_failure() {
    try {
      Preconditions.checkArgument(false, "A %s C %s E", null, null);
      fail("no exception thrown");
    } catch (IllegalArgumentException e) {
      assertThat(e).message().isEqualTo("A null C null E");
    }
  }

  @Test
  public void testCheckArgument_notEnoughArgs_failure() {
    try {
      Preconditions.checkArgument(false, "A %s C %s E", "b");
      fail("no exception thrown");
    } catch (IllegalArgumentException e) {
      assertThat(e).message().isEqualTo("A b C %s E");
    }
  }

  @Test
  public void testCheckArgument_tooManyArgs_failure() {
    try {
      Preconditions.checkArgument(false, "A %s C %s E", "b", "d", "f");
      fail("no exception thrown");
    } catch (IllegalArgumentException e) {
      assertThat(e).message().isEqualTo("A b C d E [f]");
    }
  }

  @Test
  public void testCheckArgument_singleNullArg_failure() {
    try {
      Preconditions.checkArgument(false, "A %s C", (Object) null);
      fail("no exception thrown");
    } catch (IllegalArgumentException e) {
      assertThat(e).message().isEqualTo("A null C");
    }
  }

  @Test
  public void testCheckArgument_singleNullArray_failure() {
    try {
      Preconditions.checkArgument(false, "A %s C", (Object[]) null);
      fail("no exception thrown");
    } catch (IllegalArgumentException e) {
      assertThat(e).message().isEqualTo("A (Object[])null C");
    }
  }

  @Test
  public void testCheckArgument_complexMessage_success() {
    Preconditions.checkArgument(true, "%s", IGNORE_ME);
  }

  @Test
  public void testCheckArgument_complexMessage_failure() {
    try {
      Preconditions.checkArgument(false, FORMAT, 5);
      fail("no exception thrown");
    } catch (IllegalArgumentException expected) {
      verifyComplexMessage(expected);
    }
  }

  @Test
  public void testCheckState_simple_success() {
    Preconditions.checkState(true);
  }

  @Test
  public void testCheckState_simple_failure() {
    try {
      Preconditions.checkState(false);
      fail("no exception thrown");
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testCheckState_simpleMessage_success() {
    Preconditions.checkState(true, IGNORE_ME);
  }

  @Test
  public void testCheckState_simpleMessage_failure() {
    try {
      Preconditions.checkState(false, new Message());
      fail("no exception thrown");
    } catch (IllegalStateException expected) {
      verifySimpleMessage(expected);
    }
  }

  @Test
  public void testCheckState_nullMessage_failure() {
    try {
      Preconditions.checkState(false, null);
      fail("no exception thrown");
    } catch (IllegalStateException expected) {
      assertThat(expected).hasMessage("null");
    }
  }

  @Test
  public void testCheckState_complexMessage_success() {
    Preconditions.checkState(true, "%s", IGNORE_ME);
  }

  @Test
  public void testCheckState_complexMessage_failure() {
    try {
      Preconditions.checkState(false, FORMAT, 5);
      fail("no exception thrown");
    } catch (IllegalStateException expected) {
      verifyComplexMessage(expected);
    }
  }

  private static final String NON_NULL_STRING = "foo";

  @Test
  public void testCheckNotNull_simple_success() {
    String result = Preconditions.checkNotNull(NON_NULL_STRING);
    assertSame(NON_NULL_STRING, result);
  }

  @Test
  public void testCheckNotNull_simple_failure() {
    try {
      Preconditions.checkNotNull(null);
      fail("no exception thrown");
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void testCheckNotNull_simpleMessage_success() {
    String result = Preconditions.checkNotNull(NON_NULL_STRING, IGNORE_ME);
    assertSame(NON_NULL_STRING, result);
  }

  @Test
  public void testCheckNotNull_simpleMessage_failure() {
    try {
      Preconditions.checkNotNull(null, new Message());
      fail("no exception thrown");
    } catch (NullPointerException expected) {
      verifySimpleMessage(expected);
    }
  }

  @Test
  public void testCheckNotNull_complexMessage_success() {
    String result = Preconditions.checkNotNull(NON_NULL_STRING, "%s", IGNORE_ME);
    assertSame(NON_NULL_STRING, result);
  }

  @Test
  public void testCheckNotNull_complexMessage_failure() {
    try {
      Preconditions.checkNotNull(null, FORMAT, 5);
      fail("no exception thrown");
    } catch (NullPointerException expected) {
      verifyComplexMessage(expected);
    }
  }

  @Test
  public void testCheckElementIndex_ok() {
    assertEquals(0, Preconditions.checkElementIndex(0, 1));
    assertEquals(0, Preconditions.checkElementIndex(0, 2));
    assertEquals(1, Preconditions.checkElementIndex(1, 2));
  }

  @Test
  public void testCheckElementIndex_badSize() {
    try {
      Preconditions.checkElementIndex(1, -1);
      fail();
    } catch (IllegalArgumentException expected) {
      // don't care what the message text is, as this is an invalid usage of
      // the Preconditions class, unlike all the other exceptions it throws
    }
  }

  @Test
  public void testCheckElementIndex_negative() {
    try {
      Preconditions.checkElementIndex(-1, 1);
      fail();
    } catch (IndexOutOfBoundsException expected) {
      assertThat(expected).hasMessage("index (-1) must not be negative");
    }
  }

  @Test
  public void testCheckElementIndex_tooHigh() {
    try {
      Preconditions.checkElementIndex(1, 1);
      fail();
    } catch (IndexOutOfBoundsException expected) {
      assertThat(expected).hasMessage("index (1) must be less than size (1)");
    }
  }

  @Test
  public void testCheckElementIndex_withDesc_negative() {
    try {
      Preconditions.checkElementIndex(-1, 1, "foo");
      fail();
    } catch (IndexOutOfBoundsException expected) {
      assertThat(expected).hasMessage("foo (-1) must not be negative");
    }
  }

  @Test
  public void testCheckElementIndex_withDesc_tooHigh() {
    try {
      Preconditions.checkElementIndex(1, 1, "foo");
      fail();
    } catch (IndexOutOfBoundsException expected) {
      assertThat(expected).hasMessage("foo (1) must be less than size (1)");
    }
  }

  @Test
  public void testCheckPositionIndex_ok() {
    assertEquals(0, Preconditions.checkPositionIndex(0, 0));
    assertEquals(0, Preconditions.checkPositionIndex(0, 1));
    assertEquals(1, Preconditions.checkPositionIndex(1, 1));
  }

  @Test
  public void testCheckPositionIndex_badSize() {
    try {
      Preconditions.checkPositionIndex(1, -1);
      fail();
    } catch (IllegalArgumentException expected) {
      // don't care what the message text is, as this is an invalid usage of
      // the Preconditions class, unlike all the other exceptions it throws
    }
  }

  @Test
  public void testCheckPositionIndex_negative() {
    try {
      Preconditions.checkPositionIndex(-1, 1);
      fail();
    } catch (IndexOutOfBoundsException expected) {
      assertThat(expected).hasMessage("index (-1) must not be negative");
    }
  }

  @Test
  public void testCheckPositionIndex_tooHigh() {
    try {
      Preconditions.checkPositionIndex(2, 1);
      fail();
    } catch (IndexOutOfBoundsException expected) {
      assertThat(expected).hasMessage("index (2) must not be greater than size (1)");
    }
  }

  @Test
  public void testCheckPositionIndex_withDesc_negative() {
    try {
      Preconditions.checkPositionIndex(-1, 1, "foo");
      fail();
    } catch (IndexOutOfBoundsException expected) {
      assertThat(expected).hasMessage("foo (-1) must not be negative");
    }
  }

  @Test
  public void testCheckPositionIndex_withDesc_tooHigh() {
    try {
      Preconditions.checkPositionIndex(2, 1, "foo");
      fail();
    } catch (IndexOutOfBoundsException expected) {
      assertThat(expected).hasMessage("foo (2) must not be greater than size (1)");
    }
  }

  @Test
  public void testCheckPositionIndexes_ok() {
    Preconditions.checkPositionIndexes(0, 0, 0);
    Preconditions.checkPositionIndexes(0, 0, 1);
    Preconditions.checkPositionIndexes(0, 1, 1);
    Preconditions.checkPositionIndexes(1, 1, 1);
  }

  @Test
  public void testCheckPositionIndexes_badSize() {
    try {
      Preconditions.checkPositionIndexes(1, 1, -1);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testCheckPositionIndex_startNegative() {
    try {
      Preconditions.checkPositionIndexes(-1, 1, 1);
      fail();
    } catch (IndexOutOfBoundsException expected) {
      assertThat(expected).hasMessage("start index (-1) must not be negative");
    }
  }

  @Test
  public void testCheckPositionIndexes_endTooHigh() {
    try {
      Preconditions.checkPositionIndexes(0, 2, 1);
      fail();
    } catch (IndexOutOfBoundsException expected) {
      assertThat(expected).hasMessage("end index (2) must not be greater than size (1)");
    }
  }

  @Test
  public void testCheckPositionIndexes_reversed() {
    try {
      Preconditions.checkPositionIndexes(1, 0, 1);
      fail();
    } catch (IndexOutOfBoundsException expected) {
      assertThat(expected).hasMessage("end index (0) must not be less than start index (1)");
    }
  }

  @Test
  public void testAllOverloads_checkArgument() throws Exception {
    for (List<Class<?>> sig : allSignatures(boolean.class)) {
      Method checkArgumentMethod =
          Preconditions.class.getMethod("checkArgument", sig.toArray(new Class<?>[] {}));
      checkArgumentMethod.invoke(null /* static method */, getParametersForSignature(true, sig));

      Object[] failingParams = getParametersForSignature(false, sig);
      try {
        checkArgumentMethod.invoke(null /* static method */, failingParams);
        fail();
      } catch (InvocationTargetException ite) {
        assertFailureCause(ite.getCause(), IllegalArgumentException.class, failingParams);
      }
    }
  }

  @Test
  public void testAllOverloads_checkState() throws Exception {
    for (List<Class<?>> sig : allSignatures(boolean.class)) {
      Method checkArgumentMethod =
          Preconditions.class.getMethod("checkState", sig.toArray(new Class<?>[] {}));
      checkArgumentMethod.invoke(null /* static method */, getParametersForSignature(true, sig));

      Object[] failingParams = getParametersForSignature(false, sig);
      try {
        checkArgumentMethod.invoke(null /* static method */, failingParams);
        fail();
      } catch (InvocationTargetException ite) {
        assertFailureCause(ite.getCause(), IllegalStateException.class, failingParams);
      }
    }
  }

  @Test
  public void testAllOverloads_checkNotNull() throws Exception {
    for (List<Class<?>> sig : allSignatures(Object.class)) {
      Method checkArgumentMethod =
          Preconditions.class.getMethod("checkNotNull", sig.toArray(new Class<?>[] {}));
      checkArgumentMethod.invoke(
          null /* static method */, getParametersForSignature(new Object(), sig));

      Object[] failingParams = getParametersForSignature(null, sig);
      try {
        checkArgumentMethod.invoke(null /* static method */, failingParams);
        fail();
      } catch (InvocationTargetException ite) {
        assertFailureCause(ite.getCause(), NullPointerException.class, failingParams);
      }
    }
  }

  /**
   * Asserts that the given throwable has the given class and then asserts on the message as using
   * the full set of method parameters.
   */
  private void assertFailureCause(
      Throwable throwable, Class<? extends Throwable> clazz, Object[] params) {
    assertThat(throwable).isInstanceOf(clazz);
    if (params.length == 1) {
      assertThat(throwable).hasMessage(null);
    } else if (params.length == 2) {
      assertThat(throwable).hasMessage("");
    } else {
      assertThat(throwable)
          .hasMessage(Preconditions.lenientFormat("", Arrays.copyOfRange(params, 2, params.length)));
    }
  }

  /**
   * Returns an array containing parameters for invoking a checkArgument, checkNotNull or checkState
   * method reflectively
   *
   * @param firstParam The first parameter
   * @param sig The method signature
   */
  private Object[] getParametersForSignature(Object firstParam, List<Class<?>> sig) {
    Object[] params = new Object[sig.size()];
    params[0] = firstParam;
    if (params.length > 1) {
      params[1] = "";
      if (params.length > 2) {
        // fill in the rest of the array with arbitrary instances
        for (int i = 2; i < params.length; i++) {
          params[i] = arbitraryInstance(sig.get(i));
        }
      }
    }
    return params;
  }

  private static final List<Class<?>> possibleParamTypes =
      List.of(char.class, int.class, long.class, Object.class);

  /** Stand-in for guava-testlib's {@code ArbitraryInstances.get}. */
  private static Object arbitraryInstance(Class<?> type) {
    if (type == char.class) {
      return 'a';
    } else if (type == int.class) {
      return 0;
    } else if (type == long.class) {
      return 0L;
    }
    return "";
  }

  /**
   * Returns a list of parameters for invoking an overload of checkState, checkArgument or
   * checkNotNull
   *
   * @param predicateType The first parameter to the method (boolean or Object)
   */
  private static List<List<Class<?>>> allSignatures(Class<?> predicateType) {
    Set<List<Class<?>>> allOverloads = new LinkedHashSet<>();
    // The first two are for the overloads that don't take formatting args, e.g.
    // checkArgument(boolean) and checkArgument(boolean, Object)
    allOverloads.add(List.<Class<?>>of(predicateType));
    allOverloads.add(List.<Class<?>>of(predicateType, Object.class));

    // One and two formatting args of each possible type.
    for (Class<?> p1 : possibleParamTypes) {
      allOverloads.add(List.<Class<?>>of(predicateType, String.class, p1));
      for (Class<?> p2 : possibleParamTypes) {
        allOverloads.add(List.<Class<?>>of(predicateType, String.class, p1, p2));
      }
    }
    return new ArrayList<>(allOverloads);
  }

  // 'test' to demonstrate some potentially ambiguous overloads.  This 'test' is kind of strange,
  // but essentially each line will be a call to a Preconditions method that, but for a documented
  // change would be a compiler error.
  // See http://docs.oracle.com/javase/specs/jls/se7/html/jls-15.html#jls-15.12.2 for the spec on
  // how javac selects overloads
  @SuppressWarnings("null")
  public void overloadSelection() {
    Boolean boxedBoolean = null;
    boolean aBoolean = true;
    Long boxedLong = null;
    int anInt = 1;
    // With a boxed predicate, no overloads can be selected in phase 1
    // ambiguous without the call to .booleanValue to unbox the Boolean
    Preconditions.checkState(boxedBoolean.booleanValue(), "", 1);
    // ambiguous without the cast to Object because the boxed predicate prevents any overload from
    // being selected in phase 1
    Preconditions.checkState(boxedBoolean, "", (Object) boxedLong);

    // ternaries introduce their own problems. because of the ternary (which requires a boxing
    // operation) no overload can be selected in phase 1.  and in phase 2 it is ambiguous since it
    // matches with the second parameter being boxed and without it being boxed.  The cast to Object
    // avoids this.
    Preconditions.checkState(aBoolean, "", aBoolean ? "" : anInt, (Object) anInt);

    // ambiguous without the .booleanValue() call since the boxing forces us into phase 2 resolution
    short s = 2;
    Preconditions.checkState(boxedBoolean.booleanValue(), "", s);
  }

  @Test
  public void testNullPointers() {
    NullPointerTester tester = new NullPointerTester();
    tester.testAllPublicStaticMethods(Preconditions.class);
  }

  private static final Object IGNORE_ME =
      new Object() {
        @Override
        public String toString() {
          throw new AssertionFailedError();
        }
      };

  private static class Message {
    boolean invoked;

    @Override
    public String toString() {
      assertFalse(invoked);
      invoked = true;
      return "A message";
    }
  }

  private static final String FORMAT = "I ate %s pies.";

  private static void verifySimpleMessage(Exception e) {
    assertThat(e).hasMessage("A message");
  }

  private static void verifyComplexMessage(Exception e) {
    assertThat(e).hasMessage("I ate 5 pies.");
  }
}
