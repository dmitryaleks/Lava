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

import static com.simplelibs.lava.base.Preconditions.checkNotNull;
import static java.lang.Math.abs;

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

  /** Returns the result of dividing {@code p} by {@code q}, rounding using {@code mode}. */
  @SuppressWarnings("fallthrough")
  static int divide(int p, int q, RoundingMode mode) {
    checkNotNull(mode);
    if (q == 0) {
      throw new ArithmeticException("/ by zero");
    }
    int div = p / q;
    int rem = p - q * div; // equal to p % q

    if (rem == 0) {
      return div;
    }

    /*
     * Normal Java division rounds towards 0, consistently with RoundingMode.DOWN. We just have to
     * deal with the cases where rounding towards 0 is wrong, which typically depends on the sign of
     * p / q.
     *
     * signum is 1 if p and q are both nonnegative or both negative, and -1 otherwise.
     */
    int signum = 1 | ((p ^ q) >> (Integer.SIZE - 1));
    boolean increment;
    switch (mode) {
      case UNNECESSARY:
        checkRoundingUnnecessary(rem == 0);
        // fall through
      case DOWN:
        increment = false;
        break;
      case UP:
        increment = true;
        break;
      case CEILING:
        increment = signum > 0;
        break;
      case FLOOR:
        increment = signum < 0;
        break;
      case HALF_EVEN:
      case HALF_DOWN:
      case HALF_UP:
        int absRem = abs(rem);
        int cmpRemToHalfDivisor = absRem - (abs(q) - absRem);
        // subtracting two nonnegative ints can't overflow
        // cmpRemToHalfDivisor has the same sign as compare(abs(rem), abs(q) / 2).
        if (cmpRemToHalfDivisor == 0) { // exactly on the half mark
          increment =
              (mode == RoundingMode.HALF_UP
                  || (mode == RoundingMode.HALF_EVEN & (div & 1) != 0));
        } else {
          increment = cmpRemToHalfDivisor > 0; // closer to the UP value
        }
        break;
      default:
        throw new AssertionError();
    }
    return increment ? div + signum : div;
  }

  /** Returns the product of {@code a} and {@code b}, throwing on overflow. */
  static int checkedMultiply(int a, int b) {
    long result = (long) a * b;
    checkNoOverflow(result == (int) result, "checkedMultiply", a, b);
    return (int) result;
  }

  /** Returns the sum of {@code a} and {@code b} unless it would overflow, else the bound. */
  static int saturatedAdd(int a, int b) {
    return saturatedCast((long) a + b);
  }

  static void checkNoOverflow(boolean condition, String methodName, int a, int b) {
    if (!condition) {
      throw new ArithmeticException("overflow: " + methodName + "(" + a + ", " + b + ")");
    }
  }
}
