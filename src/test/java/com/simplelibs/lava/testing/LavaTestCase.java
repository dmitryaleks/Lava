package com.simplelibs.lava.testing;

import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.opentest4j.AssertionFailedError;

/**
 * JUnit 3 compatibility base class for tests ported from Guava 27.0.1's {@code guava-tests}.
 *
 * <p>It reproduces {@code junit.framework.TestCase}'s assertion methods with their original
 * message-first semantics, and calls the overridable {@link #setUp()}/{@link #tearDown()} hooks
 * around each JUnit 5 test, so ported tests keep their bodies unchanged. Test methods must be
 * annotated with {@code @org.junit.jupiter.api.Test}.
 */
public abstract class LavaTestCase {

  @BeforeEach
  final void lavaTestCaseSetUp() throws Exception {
    setUp();
  }

  @AfterEach
  final void lavaTestCaseTearDown() throws Exception {
    tearDown();
  }

  protected void setUp() throws Exception {}

  protected void tearDown() throws Exception {}

  public static void assertTrue(String message, boolean condition) {
    if (!condition) {
      fail(message);
    }
  }

  public static void assertTrue(boolean condition) {
    assertTrue(null, condition);
  }

  public static void assertFalse(String message, boolean condition) {
    assertTrue(message, !condition);
  }

  public static void assertFalse(boolean condition) {
    assertFalse(null, condition);
  }

  public static void fail(String message) {
    throw new AssertionFailedError(message);
  }

  public static void fail() {
    fail(null);
  }

  public static void assertEquals(String message, Object expected, Object actual) {
    if (!Objects.equals(expected, actual)) {
      failNotEquals(message, expected, actual);
    }
  }

  public static void assertEquals(Object expected, Object actual) {
    assertEquals(null, expected, actual);
  }

  public static void assertEquals(String message, String expected, String actual) {
    assertEquals(message, (Object) expected, (Object) actual);
  }

  public static void assertEquals(String expected, String actual) {
    assertEquals(null, expected, actual);
  }

  public static void assertEquals(String message, double expected, double actual, double delta) {
    if (Double.compare(expected, actual) == 0) {
      return;
    }
    if (!(Math.abs(expected - actual) <= delta)) {
      failNotEquals(message, expected, actual);
    }
  }

  public static void assertEquals(double expected, double actual, double delta) {
    assertEquals(null, expected, actual, delta);
  }

  public static void assertEquals(String message, float expected, float actual, float delta) {
    if (Float.compare(expected, actual) == 0) {
      return;
    }
    if (!(Math.abs(expected - actual) <= delta)) {
      failNotEquals(message, expected, actual);
    }
  }

  public static void assertEquals(float expected, float actual, float delta) {
    assertEquals(null, expected, actual, delta);
  }

  public static void assertEquals(String message, long expected, long actual) {
    assertEquals(message, Long.valueOf(expected), Long.valueOf(actual));
  }

  public static void assertEquals(long expected, long actual) {
    assertEquals(null, expected, actual);
  }

  public static void assertEquals(String message, boolean expected, boolean actual) {
    assertEquals(message, Boolean.valueOf(expected), Boolean.valueOf(actual));
  }

  public static void assertEquals(boolean expected, boolean actual) {
    assertEquals(null, expected, actual);
  }

  public static void assertEquals(String message, byte expected, byte actual) {
    assertEquals(message, Byte.valueOf(expected), Byte.valueOf(actual));
  }

  public static void assertEquals(byte expected, byte actual) {
    assertEquals(null, expected, actual);
  }

  public static void assertEquals(String message, char expected, char actual) {
    assertEquals(message, Character.valueOf(expected), Character.valueOf(actual));
  }

  public static void assertEquals(char expected, char actual) {
    assertEquals(null, expected, actual);
  }

  public static void assertEquals(String message, short expected, short actual) {
    assertEquals(message, Short.valueOf(expected), Short.valueOf(actual));
  }

  public static void assertEquals(short expected, short actual) {
    assertEquals(null, expected, actual);
  }

  public static void assertEquals(String message, int expected, int actual) {
    assertEquals(message, Integer.valueOf(expected), Integer.valueOf(actual));
  }

  public static void assertEquals(int expected, int actual) {
    assertEquals(null, expected, actual);
  }

  public static void assertNotNull(Object object) {
    assertNotNull(null, object);
  }

  public static void assertNotNull(String message, Object object) {
    assertTrue(message, object != null);
  }

  public static void assertNull(Object object) {
    assertNull(null, object);
  }

  public static void assertNull(String message, Object object) {
    if (object != null) {
      fail(format(message) + "expected null, but was:<" + object + ">");
    }
  }

  public static void assertSame(String message, Object expected, Object actual) {
    if (expected != actual) {
      fail(format(message) + "expected same:<" + expected + "> was not:<" + actual + ">");
    }
  }

  public static void assertSame(Object expected, Object actual) {
    assertSame(null, expected, actual);
  }

  public static void assertNotSame(String message, Object expected, Object actual) {
    if (expected == actual) {
      fail(format(message) + "expected not same");
    }
  }

  public static void assertNotSame(Object expected, Object actual) {
    assertNotSame(null, expected, actual);
  }

  private static void failNotEquals(String message, Object expected, Object actual) {
    throw new AssertionFailedError(
        format(message) + "expected:<" + expected + "> but was:<" + actual + ">",
        expected,
        actual);
  }

  private static String format(String message) {
    return message == null || message.isEmpty() ? "" : message + " ";
  }

  /** Convenience for ported tests that use JUnit 5 style assertions on exceptions. */
  public static <T extends Throwable> T assertThrows(
      Class<T> expectedType, org.junit.jupiter.api.function.Executable executable) {
    return Assertions.assertThrows(expectedType, executable);
  }
}
