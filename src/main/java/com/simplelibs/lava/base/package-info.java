/**
 * Basic utility libraries and interfaces, ported from Guava 27.0.1's {@code base} package
 * for single-threaded use: {@link com.simplelibs.lava.base.Enums}, {@link
 * com.simplelibs.lava.base.Converter}, {@link com.simplelibs.lava.base.Optional}, {@link
 * com.simplelibs.lava.base.Equivalence}, {@link com.simplelibs.lava.base.Ticker} and {@link
 * com.simplelibs.lava.base.Preconditions}.
 *
 * <p>Guava's {@code Function}, {@code Supplier} and {@code Predicate} are not ported; the Lava API
 * uses {@code java.util.function.Function}, {@code Supplier} and {@code Predicate}, which Guava's
 * versions extend. Classes in this package are not thread-safe unless documented otherwise.
 */
package com.simplelibs.lava.base;
