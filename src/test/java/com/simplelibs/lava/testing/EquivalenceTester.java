package com.simplelibs.lava.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.simplelibs.lava.base.Equivalence;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Minimal replacement for guava-testlib's {@code EquivalenceTester}. */
public final class EquivalenceTester<T> {
  private final Equivalence<? super T> equivalence;
  private final List<List<T>> groups = new ArrayList<>();

  private EquivalenceTester(Equivalence<? super T> equivalence) {
    this.equivalence = equivalence;
  }

  public static <T> EquivalenceTester<T> of(Equivalence<? super T> equivalence) {
    return new EquivalenceTester<>(equivalence);
  }

  @SafeVarargs
  public final EquivalenceTester<T> addEquivalenceGroup(T first, T... rest) {
    List<T> group = new ArrayList<>();
    group.add(first);
    group.addAll(Arrays.asList(rest));
    groups.add(group);
    return this;
  }

  public EquivalenceTester<T> addEquivalenceGroup(Iterable<T> group) {
    List<T> list = new ArrayList<>();
    group.forEach(list::add);
    groups.add(list);
    return this;
  }

  public EquivalenceTester<T> test() {
    for (int run = 0; run < 3; run++) { // repeated like guava-testlib's REPETITIONS
      testOnce();
    }
    return this;
  }

  private void testOnce() {
    for (int g = 0; g < groups.size(); g++) {
      for (T a : groups.get(g)) {
        assertTrue(equivalence.equivalent(a, a), () -> a + " must be equivalent to itself");
        assertTrue(!equivalence.equivalent(a, null), () -> a + " must not be equivalent to null");
        assertTrue(!equivalence.equivalent(null, a), () -> "null must not be equivalent to " + a);
        for (T b : groups.get(g)) {
          assertTrue(equivalence.equivalent(a, b), () -> a + " must be equivalent to " + b);
          assertEquals(equivalence.hash(a), equivalence.hash(b), () -> "hash of " + a + " vs " + b);
        }
        for (int h = 0; h < groups.size(); h++) {
          if (h != g) {
            for (T b : groups.get(h)) {
              assertTrue(!equivalence.equivalent(a, b), () -> a + " must not be equivalent to " + b);
            }
          }
        }
      }
    }
    assertTrue(equivalence.equivalent(null, null), "null must be equivalent to null");
  }
}
