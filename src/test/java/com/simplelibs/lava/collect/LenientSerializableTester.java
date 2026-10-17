package com.simplelibs.lava.collect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.simplelibs.lava.testing.SerializableTester;
import java.util.Set;

/**
 * Port of Guava's test-only {@code LenientSerializableTester}: checks that a reserialized set is
 * equal and still an {@link ImmutableSet}, without requiring the same class.
 */
final class LenientSerializableTester {
  private LenientSerializableTester() {}

  static <E> Set<E> reserializeAndAssertLenient(Set<E> original) {
    Set<E> copy = SerializableTester.reserialize(original);
    assertEquals(original, copy);
    assertTrue(copy instanceof ImmutableSet, "reserialized set is not an ImmutableSet: " + copy);
    return copy;
  }

  static <E> ImmutableCollection<E> reserializeAndAssertElementsEqual(
      ImmutableCollection<E> original) {
    ImmutableCollection<E> copy = SerializableTester.reserialize(original);
    assertEquals(ImmutableList.copyOf(original), ImmutableList.copyOf(copy));
    return copy;
  }
}
