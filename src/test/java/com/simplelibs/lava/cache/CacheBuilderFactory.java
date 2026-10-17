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

package com.simplelibs.lava.cache;

import com.simplelibs.lava.base.Preconditions;
import com.simplelibs.lava.checker.nullness.qual.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Helper class for creating {@link CacheBuilder} instances with all combinations of several sets of
 * parameters.
 *
 * <p>Lava: written on {@code CacheBuilder}'s public API ({@code weakKeys()}, {@code weakValues()},
 * {@code softValues()}) instead of the package-private {@code setKeyStrength}/{@code
 * setValueStrength}, and without Guava's {@code Optional} and {@code Sets.cartesianProduct}.
 *
 * @author mike nonemacher
 */
class CacheBuilderFactory {

  /** The reference strength of keys or values; {@code STRONG} is the {@code CacheBuilder} default. */
  enum Strength {
    STRONG,
    WEAK,
    SOFT
  }

  // Default values contain only 'null', which means don't call the CacheBuilder method (just give
  // the CacheBuilder default).
  private Set<Integer> concurrencyLevels = nullOnly();
  private Set<Integer> initialCapacities = nullOnly();
  private Set<Integer> maximumSizes = nullOnly();
  private Set<DurationSpec> expireAfterWrites = nullOnly();
  private Set<DurationSpec> expireAfterAccesses = nullOnly();
  private Set<DurationSpec> refreshes = nullOnly();
  private Set<Strength> keyStrengths = nullOnly();
  private Set<Strength> valueStrengths = nullOnly();

  private static <T> Set<T> nullOnly() {
    Set<T> result = new LinkedHashSet<>();
    result.add(null);
    return result;
  }

  CacheBuilderFactory withConcurrencyLevels(Set<Integer> concurrencyLevels) {
    this.concurrencyLevels = new LinkedHashSet<>(concurrencyLevels);
    return this;
  }

  CacheBuilderFactory withInitialCapacities(Set<Integer> initialCapacities) {
    this.initialCapacities = new LinkedHashSet<>(initialCapacities);
    return this;
  }

  CacheBuilderFactory withMaximumSizes(Set<Integer> maximumSizes) {
    this.maximumSizes = new LinkedHashSet<>(maximumSizes);
    return this;
  }

  CacheBuilderFactory withExpireAfterWrites(Set<DurationSpec> durations) {
    this.expireAfterWrites = new LinkedHashSet<>(durations);
    return this;
  }

  CacheBuilderFactory withExpireAfterAccesses(Set<DurationSpec> durations) {
    this.expireAfterAccesses = new LinkedHashSet<>(durations);
    return this;
  }

  CacheBuilderFactory withRefreshes(Set<DurationSpec> durations) {
    this.refreshes = new LinkedHashSet<>(durations);
    return this;
  }

  CacheBuilderFactory withKeyStrengths(Set<Strength> keyStrengths) {
    this.keyStrengths = new LinkedHashSet<>(keyStrengths);
    Preconditions.checkArgument(!this.keyStrengths.contains(Strength.SOFT));
    return this;
  }

  CacheBuilderFactory withValueStrengths(Set<Strength> valueStrengths) {
    this.valueStrengths = new LinkedHashSet<>(valueStrengths);
    return this;
  }

  /**
   * Returns a builder for every combination of the configured values, with the last parameter
   * varying fastest (the order of Guava's {@code Sets.cartesianProduct}).
   */
  Iterable<CacheBuilder<Object, Object>> buildAllPermutations() {
    List<List<Object>> combinations =
        buildCartesianProduct(
            concurrencyLevels,
            initialCapacities,
            maximumSizes,
            expireAfterWrites,
            expireAfterAccesses,
            refreshes,
            keyStrengths,
            valueStrengths);
    List<CacheBuilder<Object, Object>> builders = new ArrayList<>(combinations.size());
    for (List<Object> combination : combinations) {
      builders.add(
          createCacheBuilder(
              (Integer) combination.get(0),
              (Integer) combination.get(1),
              (Integer) combination.get(2),
              (DurationSpec) combination.get(3),
              (DurationSpec) combination.get(4),
              (DurationSpec) combination.get(5),
              (Strength) combination.get(6),
              (Strength) combination.get(7)));
    }
    return builders;
  }

  /**
   * The cartesian product of the given sets, which may contain null (meaning "don't call the
   * associated CacheBuilder method").
   */
  private static List<List<Object>> buildCartesianProduct(Set<?>... sets) {
    List<List<Object>> result = new ArrayList<>();
    result.add(new ArrayList<>());
    for (Set<?> set : sets) {
      List<List<Object>> next = new ArrayList<>();
      for (List<Object> prefix : result) {
        for (Object element : set) {
          List<Object> extended = new ArrayList<>(prefix);
          extended.add(element);
          next.add(extended);
        }
      }
      result = next;
    }
    return result;
  }

  private CacheBuilder<Object, Object> createCacheBuilder(
      Integer concurrencyLevel,
      Integer initialCapacity,
      Integer maximumSize,
      DurationSpec expireAfterWrite,
      DurationSpec expireAfterAccess,
      DurationSpec refresh,
      Strength keyStrength,
      Strength valueStrength) {

    CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder();
    if (concurrencyLevel != null) {
      builder.concurrencyLevel(concurrencyLevel);
    }
    if (initialCapacity != null) {
      builder.initialCapacity(initialCapacity);
    }
    if (maximumSize != null) {
      builder.maximumSize(maximumSize);
    }
    if (expireAfterWrite != null) {
      builder.expireAfterWrite(expireAfterWrite.duration, expireAfterWrite.unit);
    }
    if (expireAfterAccess != null) {
      builder.expireAfterAccess(expireAfterAccess.duration, expireAfterAccess.unit);
    }
    if (refresh != null) {
      builder.refreshAfterWrite(refresh.duration, refresh.unit);
    }
    if (keyStrength == Strength.WEAK) {
      builder.weakKeys();
    }
    if (valueStrength == Strength.WEAK) {
      builder.weakValues();
    } else if (valueStrength == Strength.SOFT) {
      builder.softValues();
    }
    return builder;
  }

  static class DurationSpec {
    private final long duration;
    private final TimeUnit unit;

    private DurationSpec(long duration, TimeUnit unit) {
      this.duration = duration;
      this.unit = unit;
    }

    public static DurationSpec of(long duration, TimeUnit unit) {
      return new DurationSpec(duration, unit);
    }

    @Override
    public int hashCode() {
      return Objects.hash(duration, unit);
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (o instanceof DurationSpec) {
        DurationSpec that = (DurationSpec) o;
        return unit.toNanos(duration) == that.unit.toNanos(that.duration);
      }
      return false;
    }

    @Override
    public String toString() {
      return "DurationSpec{duration=" + duration + ", unit=" + unit + "}";
    }
  }
}
