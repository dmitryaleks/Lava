/*
 * Copyright (C) 2011 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * Modified for Lava: single-threaded, Guava-independent port of Guava 27.0.1.
 */

package com.simplelibs.lava.collect;

import java.math.RoundingMode;

/**
 * The few integer arithmetic helpers from Guava's {@code Ints}, {@code IntMath} and {@code
 * MathPreconditions} that Lava's collections use, kept package-private.
 */
final class CollectMath {
  private CollectMath() {}

  /** The largest power of two that can be represented as an {@code int} (Guava's {@code Ints}). */
  static final int MAX_POWER_OF_TWO = 1 << (Integer.SIZE - 2);

  static final int MAX_POWER_OF_SQRT2_UNSIGNED = 0xB504F333;

  /** Returns the {@code int} nearest in value to {@code value} (Guava's {@code Ints}). */
  static int saturatedCast(long value) {
    if (value > Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;
    }
    if (value < Integer.MIN_VALUE) {
      return Integer.MIN_VALUE;
    }
    return (int) value;
  }

  static boolean isPowerOfTwo(int x) {
    return x > 0 & (x & (x - 1)) == 0;
  }

  static int lessThanBranchFree(int x, int y) {
    return ~~(x - y) >>> (Integer.SIZE - 1);
  }

  /** Returns the base-2 logarithm of {@code x}, rounded according to {@code mode}. */
  @SuppressWarnings("fallthrough")
  static int log2(int x, RoundingMode mode) {
    checkPositive("x", x);
    switch (mode) {
      case UNNECESSARY:
        checkRoundingUnnecessary(isPowerOfTwo(x));
        // fall through
      case DOWN:
      case FLOOR:
        return (Integer.SIZE - 1) - Integer.numberOfLeadingZeros(x);

      case UP:
      case CEILING:
        return Integer.SIZE - Integer.numberOfLeadingZeros(x - 1);

      case HALF_DOWN:
      case HALF_UP:
      case HALF_EVEN:
        // Since sqrt(2) is irrational, log2(x) - logFloor cannot be exactly 0.5
        int leadingZeros = Integer.numberOfLeadingZeros(x);
        int cmp = MAX_POWER_OF_SQRT2_UNSIGNED >>> leadingZeros;
        // floor(2^(logFloor + 0.5))
        int logFloor = (Integer.SIZE - 1) - leadingZeros;
        return logFloor + lessThanBranchFree(cmp, x);

      default:
        throw new AssertionError();
    }
  }

  static int checkPositive(String role, int x) {
    if (x <= 0) {
      throw new IllegalArgumentException(role + " (" + x + ") must be > 0");
    }
    return x;
  }

  static void checkRoundingUnnecessary(boolean condition) {
    if (!condition) {
      throw new ArithmeticException("mode was UNNECESSARY, but rounding was necessary");
    }
  }
}
