package com.simplelibs.lava.testing;

import java.lang.management.ManagementFactory;
import java.util.regex.Pattern;

/**
 * Exception messages for differential tests, which compare what Lava throws with what Guava
 * throws.
 *
 * <p>HotSpot stops filling in the messages of the exceptions it throws implicitly (a null
 * dereference, a bad array index or cast, division by zero) once a compiled site has thrown them
 * often, unless the JVM runs with {@code -XX:-OmitStackTraceInFastThrow}. The Gradle build passes
 * that flag, and then {@link #of} returns messages unchanged. In a JVM without it (an IDE's own
 * test runner, say), {@link #of} replaces JVM-generated or missing messages of those exceptions
 * with a placeholder, so that the comparison does not depend on the JIT, and keeps every message
 * that the libraries write themselves.
 */
public final class ExceptionMessages {
  private ExceptionMessages() {}

  /** Whether the JVM keeps implicit exceptions' messages, so that they can be compared exactly. */
  static final boolean EXACT =
      ManagementFactory.getRuntimeMXBean()
          .getInputArguments()
          .contains("-XX:-OmitStackTraceInFastThrow");

  static final String JVM_MESSAGE = "<JVM message>";

  private static final Pattern INDEX = Pattern.compile("Index -?\\d+ out of bounds for length \\d+");

  /** The message of {@code t}, comparable across JIT states. */
  public static String of(Throwable t) {
    String message = t.getMessage();
    if (EXACT) {
      return message;
    }
    boolean jvm;
    if (t instanceof NullPointerException) {
      jvm = message == null || message.startsWith("Cannot ");
    } else if (t instanceof ArrayIndexOutOfBoundsException) {
      jvm = message == null || INDEX.matcher(message).matches() || message.startsWith("arraycopy:");
    } else if (t instanceof ArithmeticException) {
      jvm = message == null || message.equals("/ by zero");
    } else if (t instanceof ClassCastException) {
      jvm = message == null || message.contains(" cannot be cast to ");
    } else {
      jvm = t instanceof ArrayStoreException;
    }
    return jvm ? JVM_MESSAGE : message;
  }
}
