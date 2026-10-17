/**
 * Collection types and utilities ported from Guava 27.0.1's {@code collect} package for
 * single-threaded use: {@link com.simplelibs.lava.collect.ImmutableMap}, {@link
 * com.simplelibs.lava.collect.Maps} and {@link com.simplelibs.lava.collect.Lists}, plus the types
 * their public signatures need ({@link com.simplelibs.lava.collect.ImmutableCollection}, {@link
 * com.simplelibs.lava.collect.ImmutableList}, {@link com.simplelibs.lava.collect.ImmutableSet},
 * {@link com.simplelibs.lava.collect.BiMap}/{@link com.simplelibs.lava.collect.HashBiMap}, {@link
 * com.simplelibs.lava.collect.Range}, {@link com.simplelibs.lava.collect.MapDifference} and a
 * minimal {@link com.simplelibs.lava.collect.Multimap} family).
 *
 * <p>Lava is single-threaded. Immutable collections have no mutable state and are safe to share;
 * mutable collections and the live views returned by {@code Maps} and {@code Lists} must be
 * confined to one thread ({@code Maps.synchronizedBiMap} and {@code
 * Maps.synchronizedNavigableMap} exist for API compatibility).
 *
 * <p>Deliberate differences from Guava, also listed in the project's API exclusions:
 *
 * <ul>
 *   <li>{@code Multimap.keys()} is omitted (no {@code Multiset}), and {@code ImmutableMultimap} has
 *       no static factories or public {@code Builder} (they would need {@code
 *       ImmutableListMultimap}); use {@code ImmutableSetMultimap}.
 *   <li>{@code ImmutableSetMultimap.Builder.orderValuesBy} yields ordered {@code ImmutableSet}s
 *       rather than {@code ImmutableSortedSet}s (see its documentation).
 *   <li>{@code Range.canonical(DiscreteDomain)} is omitted; {@code Range} implements {@link
 *       java.util.function.Predicate} directly.
 *   <li>Functions and predicates are {@code java.util.function} types.
 * </ul>
 *
 * <p><b>Serialization.</b> Do not deserialize untrusted data. Immutable collections and maps
 * reject streams that bypass their serialized forms, and deserialized ranges and array views
 * re-check their invariants. But serializable views and functions, such as {@code
 * Lists.transform} or {@code Maps.asConverter}, call the functions and comparables they hold from
 * {@code hashCode}, {@code equals} and {@code compareTo}, so deserializing them can run code chosen
 * by whoever wrote the stream. Where Java serialization of untrusted data cannot be avoided,
 * install an {@link java.io.ObjectInputFilter} allow-list. See the project's {@code
 * SECURITY_AUDIT.md}.
 */
package com.simplelibs.lava.collect;
