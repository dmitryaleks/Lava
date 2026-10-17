package com.simplelibs.lava.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Small subset of guava-testlib's {@code Helpers}. */
public final class Helpers {
  private Helpers() {}

  /** Asserts that {@code comparator} orders {@code valuesInExpectedOrder} strictly ascending. */
  @SafeVarargs
  public static <T> void testComparator(
      Comparator<? super T> comparator, T... valuesInExpectedOrder) {
    testComparator(comparator, Arrays.asList(valuesInExpectedOrder));
  }

  /** Asserts that {@code comparator} orders {@code valuesInExpectedOrder} strictly ascending. */
  public static <T> void testComparator(
      Comparator<? super T> comparator, List<T> valuesInExpectedOrder) {
    for (int i = 0; i < valuesInExpectedOrder.size(); i++) {
      T t = valuesInExpectedOrder.get(i);
      for (int j = 0; j < i; j++) {
        T lesser = valuesInExpectedOrder.get(j);
        assertTrue(comparator.compare(lesser, t) < 0, comparator + ".compare(" + lesser + ", " + t + ")");
      }
      assertEquals(0, comparator.compare(t, t), comparator + ".compare(" + t + ", " + t + ")");
      for (int j = i + 1; j < valuesInExpectedOrder.size(); j++) {
        T greater = valuesInExpectedOrder.get(j);
        assertTrue(comparator.compare(greater, t) > 0, comparator + ".compare(" + greater + ", " + t + ")");
      }
    }
  }

  /** Returns a mutable collection whose {@code size()} is off by {@code delta}. */
  public static <T> Collection<T> misleadingSizeCollection(final int delta) {
    return new ArrayList<T>() {
      @Override
      public int size() {
        return Math.max(0, super.size() + delta);
      }
    };
  }

  /** Returns an immutable entry, like guava-testlib's {@code Helpers.mapEntry}. */
  public static <K, V> Map.Entry<K, V> mapEntry(K key, V value) {
    return new AbstractMap.SimpleImmutableEntry<>(key, value);
  }
}
