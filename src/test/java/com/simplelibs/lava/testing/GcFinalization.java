package com.simplelibs.lava.testing;

import java.lang.ref.WeakReference;
import java.util.function.BooleanSupplier;

/** Minimal replacement for guava-testlib's {@code GcFinalization}. */
public final class GcFinalization {
  private static final long TIMEOUT_MILLIS = 10_000;

  private GcFinalization() {}

  /** Runs the garbage collector until {@code ref} is cleared, or fails after a timeout. */
  public static void awaitClear(WeakReference<?> ref) {
    awaitDone(() -> ref.get() == null, "Timed out waiting for " + ref + " to be cleared");
  }

  /** Runs the garbage collector until {@code condition} holds, or fails after a timeout. */
  public static void awaitDone(BooleanSupplier condition, String message) {
    long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
    while (!condition.getAsBoolean()) {
      if (System.currentTimeMillis() > deadline) {
        throw new AssertionError(message);
      }
      System.gc();
      System.runFinalization();
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
    }
  }
}
