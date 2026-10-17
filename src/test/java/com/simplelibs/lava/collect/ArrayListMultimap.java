package com.simplelibs.lava.collect;

/** Test stand-in for Guava's {@code ArrayListMultimap} (list values, key-grouped). */
final class ArrayListMultimap<K, V> extends TestMultimap<K, V> {
  private ArrayListMultimap() {
    super(false);
  }

  static <K, V> ArrayListMultimap<K, V> create() {
    return new ArrayListMultimap<>();
  }
}
