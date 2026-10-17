package com.simplelibs.lava.testing;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.stream.Collector;

/**
 * Minimal replacement for guava-testlib's {@code CollectorTester}: checks that a collector gives
 * the expected result when used sequentially and when partial results are combined left- and
 * right-associatively, so the accumulator, combiner and finisher must all agree.
 */
public final class CollectorTester<T, A, R> {
  private final Collector<T, A, R> collector;
  private final BiPredicate<? super R, ? super R> equivalence;

  private CollectorTester(
      Collector<T, A, R> collector, BiPredicate<? super R, ? super R> equivalence) {
    this.collector = collector;
    this.equivalence = equivalence;
  }

  public static <T, A, R> CollectorTester<T, A, R> of(Collector<T, A, R> collector) {
    return new CollectorTester<>(collector, Objects::equals);
  }

  public static <T, A, R> CollectorTester<T, A, R> of(
      Collector<T, A, R> collector, BiPredicate<? super R, ? super R> equivalence) {
    return new CollectorTester<>(collector, equivalence);
  }

  @SafeVarargs
  public final CollectorTester<T, A, R> expectCollects(R expectedResult, T... inputs) {
    List<T> list = Arrays.asList(inputs);
    check("sequential", expectedResult, sequential(list));
    check("stream", expectedResult, list.stream().collect(collector));
    check("merge left-associative", expectedResult, mergeLeft(list));
    check("merge right-associative", expectedResult, mergeRight(list));
    check("split in halves", expectedResult, splitInHalves(list));
    return this;
  }

  private void check(String how, R expected, R actual) {
    assertTrue(
        equivalence.test(expected, actual),
        () -> "Collector (" + how + ") produced " + actual + " but expected " + expected);
  }

  private R sequential(List<T> inputs) {
    A container = collector.supplier().get();
    for (T t : inputs) {
      collector.accumulator().accept(container, t);
    }
    return collector.finisher().apply(container);
  }

  private A singleton(T t) {
    A container = collector.supplier().get();
    collector.accumulator().accept(container, t);
    return container;
  }

  private R mergeLeft(List<T> inputs) {
    A acc = collector.supplier().get();
    for (T t : inputs) {
      acc = collector.combiner().apply(acc, singleton(t));
    }
    return collector.finisher().apply(acc);
  }

  private R mergeRight(List<T> inputs) {
    A acc = collector.supplier().get();
    List<T> reversed = new ArrayList<>(inputs);
    java.util.Collections.reverse(reversed);
    for (T t : reversed) {
      acc = collector.combiner().apply(singleton(t), acc);
    }
    return collector.finisher().apply(acc);
  }

  private R splitInHalves(List<T> inputs) {
    int mid = inputs.size() / 2;
    A left = collector.supplier().get();
    for (T t : inputs.subList(0, mid)) {
      collector.accumulator().accept(left, t);
    }
    A right = collector.supplier().get();
    for (T t : inputs.subList(mid, inputs.size())) {
      collector.accumulator().accept(right, t);
    }
    return collector.finisher().apply(collector.combiner().apply(left, right));
  }
}
