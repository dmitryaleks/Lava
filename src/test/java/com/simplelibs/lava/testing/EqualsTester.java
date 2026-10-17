package com.simplelibs.lava.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Minimal replacement for guava-testlib's {@code EqualsTester}: checks reflexivity, symmetry,
 * transitivity within groups, inequality across groups, inequality to {@code null} and to an
 * unrelated object, and hashCode consistency within groups.
 */
public final class EqualsTester {
  private final List<List<Object>> groups = new ArrayList<>();

  public EqualsTester addEqualityGroup(Object... group) {
    assertTrue(group.length > 0, "empty equality group");
    groups.add(Arrays.asList(group));
    return this;
  }

  /** Like guava-testlib, every check is repeated to catch unstable equals or hashCode. */
  private static final int REPETITIONS = 3;

  public EqualsTester testEquals() {
    for (int run = 0; run < REPETITIONS; run++) {
      testEqualsOnce();
    }
    return this;
  }

  @SuppressWarnings({"SelfEquals", "EqualsIncompatibleType", "ObjectEqualsNull"})
  private void testEqualsOnce() {
    Object unrelated = new Object() {
      @Override
      public String toString() {
        return "NotAnInstance";
      }
    };
    for (int g = 0; g < groups.size(); g++) {
      List<Object> group = groups.get(g);
      for (Object item : group) {
        assertTrue(item.equals(item), () -> item + " must be equal to itself");
        assertTrue(!item.equals(null), () -> item + ".equals(null) must be false");
        assertTrue(!item.equals(unrelated), () -> item + " must not equal an unrelated object");
        for (Object other : group) {
          assertTrue(item.equals(other), () -> item + " must be equal to " + other);
          assertEquals(
              item.hashCode(),
              other.hashCode(),
              () -> "hashCode of " + item + " must equal hashCode of " + other);
        }
        for (int h = 0; h < groups.size(); h++) {
          if (h == g) {
            continue;
          }
          for (Object other : groups.get(h)) {
            assertTrue(!item.equals(other), () -> item + " must not be equal to " + other);
          }
        }
      }
    }
  }
}
