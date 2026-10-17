package com.simplelibs.lava.collect;

import static org.assertj.core.api.Assertions.assertThat;

import com.simplelibs.lava.testing.ExceptionMessages;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.Test;

/**
 * Differential tests for the package-private {@link CollectMath} helpers against the Guava 27.0.1
 * originals they were copied from ({@code IntMath}, {@code Ints}), over edge values and random
 * values in every rounding mode, comparing results and exception messages.
 */
class CollectMathTest {

  private static List<Integer> values() {
    List<Integer> values = new ArrayList<>();
    int[] edges = {
      0, 1, -1, 2, -2, 3, -3, 4, 5, 7, 8, 9, 10, 11, 1 << 15, (1 << 30) - 1, 1 << 30,
      Integer.MAX_VALUE, Integer.MAX_VALUE - 1, Integer.MIN_VALUE, Integer.MIN_VALUE + 1, 46340, 46341,
      0xB504F333, 0xB504F334 >>> 1, -46341
    };
    for (int edge : edges) {
      values.add(edge);
    }
    Random random = new Random(0xC0113C7L);
    for (int i = 0; i < 60; i++) {
      values.add(random.nextInt());
      values.add(random.nextInt(100) - 50);
    }
    return values;
  }

  private static String outcome(IntSupplier call) {
    try {
      return String.valueOf(call.getAsInt());
    } catch (ArithmeticException | IllegalArgumentException e) {
      return e.getClass().getName() + ": " + ExceptionMessages.of(e);
    }
  }

  @Test
  void divideMatchesIntMathInEveryMode() {
    List<Integer> values = values();
    for (RoundingMode mode : RoundingMode.values()) {
      for (int p : values) {
        for (int q : values) {
          String what = "divide(" + p + ", " + q + ", " + mode + ")";
          assertThat(outcome(() -> CollectMath.divide(p, q, mode)))
              .as(what)
              .isEqualTo(outcome(() -> com.google.common.math.IntMath.divide(p, q, mode)));
        }
      }
    }
  }

  @Test
  void log2MatchesIntMathInEveryMode() {
    for (RoundingMode mode : RoundingMode.values()) {
      for (int x : values()) {
        assertThat(outcome(() -> CollectMath.log2(x, mode)))
            .as("log2(" + x + ", " + mode + ")")
            .isEqualTo(outcome(() -> com.google.common.math.IntMath.log2(x, mode)));
      }
    }
  }

  @Test
  void overflowHelpersMatchGuava() {
    List<Integer> values = values();
    for (int a : values) {
      assertThat(CollectMath.isPowerOfTwo(a))
          .as("isPowerOfTwo(" + a + ")")
          .isEqualTo(com.google.common.math.IntMath.isPowerOfTwo(a));
      for (int b : values) {
        String what = "(" + a + ", " + b + ")";
        assertThat(outcome(() -> CollectMath.checkedMultiply(a, b)))
            .as("checkedMultiply" + what)
            .isEqualTo(outcome(() -> com.google.common.math.IntMath.checkedMultiply(a, b)));
        assertThat(CollectMath.saturatedAdd(a, b))
            .as("saturatedAdd" + what)
            .isEqualTo(com.google.common.math.IntMath.saturatedAdd(a, b));
        long wide = (long) a * b;
        assertThat(CollectMath.saturatedCast(wide))
            .as("saturatedCast(" + wide + ")")
            .isEqualTo(com.google.common.primitives.Ints.saturatedCast(wide));
      }
    }
    assertThat(CollectMath.MAX_POWER_OF_TWO).isEqualTo(com.google.common.primitives.Ints.MAX_POWER_OF_TWO);
  }
}
