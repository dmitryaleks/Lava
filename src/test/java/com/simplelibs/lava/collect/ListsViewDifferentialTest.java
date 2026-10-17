package com.simplelibs.lava.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.simplelibs.lava.testing.ExceptionMessages;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.RandomAccess;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Differential tests for the live views returned by {@link Lists}: identical random operation
 * sequences (every {@code List} method, sub-list mutations, and random {@code Iterator} and {@code
 * ListIterator} sequences including illegal ones) run through Lava's view and through the real
 * Guava 27.0.1 view, each over its own equal backing list. Results, exception classes and messages,
 * the view, the backing list and the view's {@code RandomAccess}/{@code Serializable} traits must
 * agree after every operation.
 */
class ListsViewDifferentialTest {
  private static final int ROUNDS = 400;
  private static final int OPS = 6;

  /** A view and the list it is backed by (which may be the view's own backing view). */
  private static final class Pair {
    final List<Integer> view;
    final List<Integer> backing;

    Pair(List<Integer> view, List<Integer> backing) {
      this.view = view;
      this.backing = backing;
    }
  }

  /** Builds the view under test from initial contents, with Lava's ({@code lava}) or Guava's. */
  private interface Kind {
    Pair make(List<Integer> init, boolean lava);
  }

  private static List<Integer> immutable(List<Integer> init, boolean lava) {
    return lava
        ? ImmutableList.copyOf(init)
        : com.google.common.collect.ImmutableList.copyOf(init);
  }

  private static List<Integer> reverse(List<Integer> list, boolean lava) {
    return lava ? Lists.reverse(list) : com.google.common.collect.Lists.reverse(list);
  }

  private static List<Integer> transform(List<Integer> list, boolean lava) {
    return lava
        ? Lists.transform(list, x -> x * 2)
        : com.google.common.collect.Lists.transform(list, x -> x * 2);
  }

  private static List<List<Integer>> partition(List<Integer> list, int size, boolean lava) {
    return lava
        ? Lists.partition(list, size)
        : com.google.common.collect.Lists.partition(list, size);
  }

  private static Map<String, Kind> kinds() {
    Map<String, Kind> kinds = new LinkedHashMap<>();
    kinds.put("reverse(ArrayList)", (i, l) -> backed(new ArrayList<>(i), b -> reverse(b, l)));
    kinds.put("reverse(LinkedList)", (i, l) -> backed(new LinkedList<>(i), b -> reverse(b, l)));
    kinds.put(
        "reverse(Arrays.asList)",
        (i, l) -> backed(Arrays.asList(i.toArray(new Integer[0])), b -> reverse(b, l)));
    kinds.put(
        "reverse(CopyOnWriteArrayList)",
        (i, l) -> backed(new CopyOnWriteArrayList<>(i), b -> reverse(b, l)));
    kinds.put(
        "reverse(unmodifiableList)",
        (i, l) ->
            backed(java.util.Collections.unmodifiableList(new ArrayList<>(i)), b -> reverse(b, l)));
    kinds.put("reverse(ImmutableList)", (i, l) -> backed(immutable(i, l), b -> reverse(b, l)));
    kinds.put(
        "reverse(reverse(ArrayList))",
        (i, l) -> backed(new ArrayList<>(i), b -> reverse(reverse(b, l), l)));
    kinds.put(
        "reverse(ImmutableList.reverse())",
        (i, l) -> {
          List<Integer> b = immutable(i, l);
          List<Integer> r =
              l
                  ? ((ImmutableList<Integer>) b).reverse()
                  : ((com.google.common.collect.ImmutableList<Integer>) b).reverse();
          return new Pair(reverse(r, l), r);
        });
    kinds.put(
        "reverse(reverse(LinkedList).subList)",
        (i, l) -> {
          List<Integer> b = new LinkedList<>(i);
          List<Integer> r = reverse(b, l);
          int from = Math.min(1, i.size());
          int to = Math.max(from, Math.min(i.size(), i.size() / 2 + 1));
          return new Pair(reverse(r.subList(from, to), l), b);
        });
    kinds.put(
        "reverse(ArrayList.subList)",
        (i, l) -> {
          List<Integer> b = new ArrayList<>(i);
          return new Pair(reverse(b.subList(Math.min(1, i.size()), i.size()), l), b);
        });
    kinds.put(
        "reverse(LinkedList.subList)",
        (i, l) -> {
          List<Integer> b = new LinkedList<>(i);
          return new Pair(reverse(b.subList(Math.min(1, i.size()), i.size()), l), b);
        });
    kinds.put("transform(ArrayList)", (i, l) -> backed(new ArrayList<>(i), b -> transform(b, l)));
    kinds.put(
        "transform(LinkedList)", (i, l) -> backed(new LinkedList<>(i), b -> transform(b, l)));
    kinds.put(
        "transform(Arrays.asList)",
        (i, l) -> backed(Arrays.asList(i.toArray(new Integer[0])), b -> transform(b, l)));
    kinds.put(
        "transform(CopyOnWriteArrayList)",
        (i, l) -> backed(new CopyOnWriteArrayList<>(i), b -> transform(b, l)));
    kinds.put("transform(ImmutableList)", (i, l) -> backed(immutable(i, l), b -> transform(b, l)));
    kinds.put(
        "transform(reverse(LinkedList))",
        (i, l) -> backed(new LinkedList<>(i), b -> transform(reverse(b, l), l)));
    kinds.put(
        "reverse(transform(ArrayList))",
        (i, l) -> backed(new ArrayList<>(i), b -> reverse(transform(b, l), l)));
    kinds.put(
        "reverse(transform(LinkedList))",
        (i, l) -> backed(new LinkedList<>(i), b -> reverse(transform(b, l), l)));
    kinds.put(
        "partition(ArrayList, 2).last",
        (i, l) -> {
          List<Integer> b = new ArrayList<>(i);
          List<List<Integer>> p = partition(b, 2, l);
          return new Pair(p.isEmpty() ? new ArrayList<>() : p.get(p.size() - 1), b);
        });
    kinds.put(
        "partition(LinkedList, 3).first",
        (i, l) -> {
          List<Integer> b = new LinkedList<>(i);
          List<List<Integer>> p = partition(b, 3, l);
          return new Pair(p.isEmpty() ? new ArrayList<>() : p.get(0), b);
        });
    return kinds;
  }

  private static Pair backed(
      List<Integer> backing, java.util.function.Function<List<Integer>, List<Integer>> view) {
    return new Pair(view.apply(backing), backing);
  }

  @TestFactory
  List<DynamicTest> viewsBehaveLikeGuava() {
    List<DynamicTest> tests = new ArrayList<>();
    long seed = 0x4C495354L;
    for (Map.Entry<String, Kind> kind : kinds().entrySet()) {
      long kindSeed = seed++;
      tests.add(dynamicTest(kind.getKey(), () -> fuzz(kind.getKey(), kind.getValue(), kindSeed)));
    }
    return tests;
  }

  private static void fuzz(String name, Kind kind, long seed) {
    Random random = new Random(seed);
    for (int round = 0; round < ROUNDS; round++) {
      int n = random.nextInt(7);
      List<Integer> init = new ArrayList<>();
      for (int i = 0; i < n; i++) {
        init.add(random.nextInt(6));
      }
      Pair guava = kind.make(init, false);
      Pair lava = kind.make(init, true);
      assertThat(traits(lava.view)).as(name + " traits").isEqualTo(traits(guava.view));
      StringBuilder trace = new StringBuilder();
      for (int step = 0; step < OPS; step++) {
        int[] a = new int[16];
        for (int k = 0; k < a.length; k++) {
          a[k] = random.nextInt(1000);
        }
        a[0] = random.nextInt(21);
        trace.append(a[0]).append(' ');
        String what = name + " init=" + init + " ops=" + trace;
        assertThat(exec(lava.view, a)).as(what).isEqualTo(exec(guava.view, a));
        assertThat(lava.view.toString()).as(what + " view").isEqualTo(guava.view.toString());
        assertThat(lava.backing.toString())
            .as(what + " backing")
            .isEqualTo(guava.backing.toString());
      }
    }
  }

  private static String traits(List<?> list) {
    return (list instanceof RandomAccess ? "RandomAccess " : "")
        + (list instanceof Serializable ? "Serializable" : "");
  }

  private static String thrown(Throwable t) {
    return "THROW " + t.getClass().getName() + ": " + ExceptionMessages.of(t);
  }

  /** Executes the operation that {@code a} describes on {@code v} and describes its outcome. */
  private static String exec(List<Integer> v, int[] a) {
    int n = v.size();
    int idx = a[1] % (n + 3) - 1; // -1 .. n + 1
    int val = a[2] % 6;
    try {
      switch (a[0]) {
        case 0:
          return String.valueOf(v.add(val));
        case 1:
          v.add(idx, val);
          return "ok";
        case 2:
          return String.valueOf(v.addAll(Arrays.asList(val, val + 1)));
        case 3:
          return String.valueOf(v.addAll(idx, Arrays.asList(val, val + 1)));
        case 4:
          return String.valueOf(v.set(idx, val));
        case 5:
          return String.valueOf(v.remove(idx));
        case 6:
          return String.valueOf(v.remove((Object) val));
        case 7:
          return String.valueOf(v.removeAll(Arrays.asList(val, val + 1)));
        case 8:
          return String.valueOf(v.retainAll(Arrays.asList(val, val + 1, val + 2)));
        case 9:
          return String.valueOf(v.removeIf(x -> x % 2 == val % 2));
        case 10:
          v.replaceAll(x -> x + 1);
          return "ok";
        case 11:
          v.sort(Comparator.reverseOrder());
          return "ok";
        case 12:
          v.clear();
          return "ok";
        case 13:
          return v.indexOf(val) + "," + v.lastIndexOf(val) + "," + v.contains(val) + ","
              + v.indexOf(null) + "," + v.contains(null);
        case 14:
          return Arrays.toString(v.toArray()) + Arrays.toString(v.toArray(new Integer[0]))
              + v.hashCode() + v + v.size() + v.isEmpty();
        case 15:
          return subList(v, a, n, idx, val);
        case 16:
          return listIterator(v.listIterator(Math.max(0, Math.min(n, idx))), a, 5);
        case 17:
          return iterator(v.iterator(), a);
        case 18:
          return "" + v.equals(new ArrayList<>(v)) + v.equals(new LinkedList<>(v)) + v.equals(null)
              + v.stream().map(String::valueOf).reduce("", String::concat);
        case 19:
          return String.valueOf(v.get(idx));
        default:
          ListIterator<Integer> q = v.listIterator(idx);
          return q.nextIndex() + "/" + q.previousIndex() + "/" + q.hasNext() + "/" + q.hasPrevious();
      }
    } catch (Throwable t) {
      return thrown(t);
    }
  }

  private static String subList(List<Integer> v, int[] a, int n, int idx, int val) {
    int to = Math.max(0, Math.min(n, a[3] % (n + 2)));
    int from = Math.max(0, Math.min(to, idx));
    List<Integer> s = v.subList(from, to);
    switch (a[4] % 6) {
      case 0:
        s.clear();
        break;
      case 1:
        s.add(val);
        break;
      case 2:
        if (!s.isEmpty()) {
          s.set(0, val);
        }
        break;
      case 3:
        if (!s.isEmpty()) {
          s.remove(s.size() - 1);
        }
        break;
      case 4:
        s.addAll(0, Arrays.asList(val, 9));
        break;
      default:
        s.sort(null);
    }
    return "sub " + s;
  }

  private static String iterator(Iterator<Integer> it, int[] a) {
    StringBuilder sb = new StringBuilder();
    for (int k = 5; k < 12; k++) {
      try {
        switch (a[k] % 3) {
          case 0:
            sb.append(it.hasNext());
            break;
          case 1:
            sb.append(it.next());
            break;
          default:
            it.remove();
            sb.append("rm");
        }
      } catch (Throwable t) {
        sb.append(thrown(t));
      }
      sb.append(';');
    }
    return sb.toString();
  }

  /** A random sequence of operations on one list iterator, including illegal ones. */
  private static String listIterator(ListIterator<Integer> it, int[] a, int start) {
    StringBuilder sb = new StringBuilder();
    for (int k = start; k < a.length; k++) {
      try {
        switch (a[k] % 9) {
          case 0:
            sb.append("hn=").append(it.hasNext());
            break;
          case 1:
            sb.append("n=").append(it.next());
            break;
          case 2:
            sb.append("hp=").append(it.hasPrevious());
            break;
          case 3:
            sb.append("p=").append(it.previous());
            break;
          case 4:
            sb.append("ni=").append(it.nextIndex());
            break;
          case 5:
            sb.append("pi=").append(it.previousIndex());
            break;
          case 6:
            it.add(a[k] % 7);
            sb.append("add");
            break;
          case 7:
            it.set(a[k] % 5);
            sb.append("set");
            break;
          default:
            it.remove();
            sb.append("rm");
        }
      } catch (Throwable t) {
        sb.append(thrown(t));
      }
      sb.append(';');
    }
    return sb.toString();
  }
}
