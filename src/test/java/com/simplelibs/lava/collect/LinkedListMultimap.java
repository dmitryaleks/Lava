package com.simplelibs.lava.collect;

/**
 * Test stand-in for Guava's {@code LinkedListMultimap}. Unlike Guava's, entries are grouped by key
 * rather than in global insertion order, which is the order {@code ImmutableSetMultimap} consumes
 * them in anyway.
 */
final class LinkedListMultimap<K, V> extends TestMultimap<K, V> {
  private LinkedListMultimap() {
    super(false);
  }

  static <K, V> LinkedListMultimap<K, V> create() {
    return new LinkedListMultimap<>();
  }
}
