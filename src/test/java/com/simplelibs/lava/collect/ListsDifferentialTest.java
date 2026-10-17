package com.simplelibs.lava.collect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

/** Differential tests: Lava's {@link Lists} against the real Guava 27.0.1 implementation. */
class ListsDifferentialTest {
  private static final int ROUNDS = 1_500;
  private final Random random = new Random(0x115L);

  /** Compares results, or exception class and message if either side throws. */
  private static void same(String what, Callable<Object> guava, Callable<Object> lava) {
    Object g;
    Object l;
    String gThrown = null;
    String lThrown = null;
    try {
      g = guava.call();
    } catch (Exception e) {
      g = null;
      gThrown = e.getClass().getName() + ": " + e.getMessage();
    }
    try {
      l = lava.call();
    } catch (Exception e) {
      l = null;
      lThrown = e.getClass().getName() + ": " + e.getMessage();
    }
    assertThat(lThrown).as(what + " exception").isEqualTo(gThrown);
    assertThat(String.valueOf(l)).as(what).isEqualTo(String.valueOf(g));
  }

  private List<Integer> randomList(int max) {
    List<Integer> list = new ArrayList<>();
    int n = random.nextInt(max + 1);
    for (int i = 0; i < n; i++) {
      list.add(random.nextInt(10));
    }
    return list;
  }

  @Test
  void factoriesAndSimpleViews() {
    for (int i = 0; i < ROUNDS; i++) {
      List<Integer> list = randomList(8);
      Integer[] array = list.toArray(new Integer[0]);
      int size = random.nextInt(5) - 1;
      same("partition", () -> com.google.common.collect.Lists.partition(list, size), () -> Lists.partition(list, size));
      same("partition.size", () -> com.google.common.collect.Lists.partition(list, Math.max(size, 1)).size(), () -> Lists.partition(list, Math.max(size, 1)).size());
      same("reverse", () -> com.google.common.collect.Lists.reverse(list), () -> Lists.reverse(list));
      same("transform", () -> com.google.common.collect.Lists.transform(list, x -> x * 2), () -> Lists.transform(list, x -> x * 2));
      same("newArrayList(varargs)", () -> com.google.common.collect.Lists.newArrayList(array), () -> Lists.newArrayList(array));
      same("newLinkedList", () -> com.google.common.collect.Lists.newLinkedList(list), () -> Lists.newLinkedList(list));
      same("newArrayListWithCapacity", () -> com.google.common.collect.Lists.newArrayListWithCapacity(size), () -> Lists.newArrayListWithCapacity(size));
      same("newArrayListWithExpectedSize", () -> com.google.common.collect.Lists.newArrayListWithExpectedSize(size), () -> Lists.newArrayListWithExpectedSize(size));
      if (array.length > 0) {
        Integer[] rest = java.util.Arrays.copyOfRange(array, 1, array.length);
        same("asList1", () -> com.google.common.collect.Lists.asList(array[0], rest), () -> Lists.asList(array[0], rest));
        int index = random.nextInt(array.length + 2) - 1;
        same("asList1.get", () -> com.google.common.collect.Lists.asList(array[0], rest).get(index), () -> Lists.asList(array[0], rest).get(index));
      }
      String chars = list.toString();
      same("charactersOf", () -> com.google.common.collect.Lists.charactersOf(chars), () -> Lists.charactersOf(chars));
      List<List<Integer>> axes = new ArrayList<>();
      int axisCount = random.nextInt(4);
      for (int a = 0; a < axisCount; a++) {
        axes.add(randomList(3));
      }
      same("cartesianProduct", () -> com.google.common.collect.Lists.cartesianProduct(axes), () -> Lists.cartesianProduct(axes));
    }
  }

  @Test
  void cartesianProductTooLarge() {
    List<Integer> big = Collections.nCopies(1 << 16, 0);
    same(
        "cartesianProduct too large",
        () -> com.google.common.collect.Lists.cartesianProduct(big, big),
        () -> Lists.cartesianProduct(big, big));
  }

  /** Random mutations through reverse/transform/partition views must give identical results. */
  @Test
  void mutationsThroughViews() {
    for (int round = 0; round < ROUNDS; round++) {
      boolean linked = random.nextBoolean();
      List<Integer> initial = randomList(8);
      List<Integer> gBacking = linked ? new LinkedList<>(initial) : new ArrayList<>(initial);
      List<Integer> lBacking = linked ? new LinkedList<>(initial) : new ArrayList<>(initial);
      List<Integer> gReverse = com.google.common.collect.Lists.reverse(gBacking);
      List<Integer> lReverse = Lists.reverse(lBacking);
      List<String> gTransform = com.google.common.collect.Lists.transform(gBacking, String::valueOf);
      List<String> lTransform = Lists.transform(lBacking, String::valueOf);
      for (int op = 0; op < 10; op++) {
        int index = random.nextInt(gBacking.size() + 2) - 1;
        int value = random.nextInt(10);
        switch (random.nextInt(8)) {
          case 0:
            same("reverse.add(i)", () -> { gReverse.add(index, value); return gBacking; }, () -> { lReverse.add(index, value); return lBacking; });
            break;
          case 1:
            same("reverse.set", () -> gReverse.set(index, value), () -> lReverse.set(index, value));
            break;
          case 2:
            same("reverse.remove(i)", () -> gReverse.remove(index), () -> lReverse.remove(index));
            break;
          case 3:
            same("transform.remove(i)", () -> gTransform.remove(index), () -> lTransform.remove(index));
            break;
          case 4:
            same("reverse.subList.clear", () -> {
              gReverse.subList(0, Math.max(0, Math.min(index, gReverse.size()))).clear();
              return gBacking;
            }, () -> {
              lReverse.subList(0, Math.max(0, Math.min(index, lReverse.size()))).clear();
              return lBacking;
            });
            break;
          case 5:
            same("reverse.listIterator", () -> {
              java.util.ListIterator<Integer> it = gReverse.listIterator(Math.max(0, Math.min(index, gReverse.size())));
              if (it.hasNext()) { it.next(); it.set(value); it.add(value + 1); }
              return gBacking;
            }, () -> {
              java.util.ListIterator<Integer> it = lReverse.listIterator(Math.max(0, Math.min(index, lReverse.size())));
              if (it.hasNext()) { it.next(); it.set(value); it.add(value + 1); }
              return lBacking;
            });
            break;
          case 6:
            same("transform.removeIf", () -> gTransform.removeIf(s -> s.equals(String.valueOf(value))), () -> lTransform.removeIf(s -> s.equals(String.valueOf(value))));
            break;
          default:
            int size = 1 + random.nextInt(3);
            same("partition.get.set", () -> {
              List<List<Integer>> parts = com.google.common.collect.Lists.partition(gBacking, size);
              if (!parts.isEmpty()) { parts.get(parts.size() - 1).set(0, value); }
              return parts;
            }, () -> {
              List<List<Integer>> parts = Lists.partition(lBacking, size);
              if (!parts.isEmpty()) { parts.get(parts.size() - 1).set(0, value); }
              return parts;
            });
        }
        assertThat(lBacking).isEqualTo(gBacking);
        assertThat(lReverse).isEqualTo(gReverse);
        assertThat(lTransform).isEqualTo(gTransform);
      }
    }
  }
}
