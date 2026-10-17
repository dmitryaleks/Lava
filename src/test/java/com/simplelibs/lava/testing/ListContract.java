package com.simplelibs.lava.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.stream.Collectors;
import org.junit.jupiter.api.function.Executable;

/**
 * Hand-written replacement for the read-only and unmodifiable parts of guava-testlib's {@code
 * ListTestSuiteBuilder}: checks a {@link List} implementation against a reference list holding the
 * expected elements in order.
 */
public final class ListContract {
  private ListContract() {}

  /** Element absent from every list under test. */
  private static final Object MISSING = new Object() {
    @Override
    public String toString() {
      return "MISSING";
    }
  };

  /**
   * Asserts that {@code list} behaves as a list containing exactly {@code expected}, in order, for
   * every read operation including iterators, spliterators, streams and (recursively, one level)
   * sub-lists. Elements must have well-behaved {@code equals} (no NaN).
   */
  public static <E> void assertReadContract(List<E> list, List<E> expected) {
    assertReadContract(list, expected, 2);
  }

  private static <E> void assertReadContract(List<E> list, List<E> expected, int depth) {
    List<E> reference = new ArrayList<>(expected);
    int n = reference.size();
    assertEquals(n, list.size(), "size");
    assertEquals(n == 0, list.isEmpty(), "isEmpty");
    for (int i = 0; i < n; i++) {
      assertEquals(reference.get(i), list.get(i), "get(" + i + ")");
    }
    assertIndexOutOfBounds(() -> list.get(-1));
    assertIndexOutOfBounds(() -> list.get(n));

    for (E e : reference) {
      assertTrue(list.contains(e), "contains " + e);
      assertEquals(reference.indexOf(e), list.indexOf(e), "indexOf " + e);
      assertEquals(reference.lastIndexOf(e), list.lastIndexOf(e), "lastIndexOf " + e);
    }
    assertFalse(list.contains(MISSING), "contains MISSING");
    assertEquals(-1, list.indexOf(MISSING));
    assertEquals(-1, list.lastIndexOf(MISSING));
    assertTrue(list.containsAll(reference), "containsAll");

    // Equality and hashing against several List implementations, in both directions.
    assertEquals(reference, list);
    assertEquals(list, reference);
    assertEquals(new LinkedList<>(reference), list);
    assertTrue(list.equals(list), "reflexive equals");
    assertEquals(reference.hashCode(), list.hashCode(), "hashCode");
    assertEquals(reference.toString(), list.toString(), "toString");
    List<Object> different = new ArrayList<>(reference);
    different.add(MISSING);
    assertFalse(list.equals(different), "equals longer list");
    assertFalse(list.equals(new Object()), "equals non-list");

    // Iteration.
    Iterator<E> it = list.iterator();
    for (int i = 0; i < n; i++) {
      assertTrue(it.hasNext(), "iterator.hasNext at " + i);
      assertEquals(reference.get(i), it.next());
    }
    assertFalse(it.hasNext());
    Iterator<E> exhausted = it;
    assertThatThrownBy(exhausted::next).isInstanceOf(NoSuchElementException.class);

    for (int start = 0; start <= n; start++) {
      ListIterator<E> li = list.listIterator(start);
      ListIterator<E> ri = reference.listIterator(start);
      while (ri.hasNext()) {
        assertTrue(li.hasNext());
        assertEquals(ri.nextIndex(), li.nextIndex(), "nextIndex");
        assertEquals(ri.previousIndex(), li.previousIndex(), "previousIndex");
        assertEquals(ri.next(), li.next());
      }
      assertFalse(li.hasNext());
      while (ri.hasPrevious()) {
        assertTrue(li.hasPrevious());
        assertEquals(ri.previous(), li.previous());
      }
      assertFalse(li.hasPrevious());
      ListIterator<E> atStart = li;
      assertThatThrownBy(atStart::previous).isInstanceOf(NoSuchElementException.class);
    }
    assertIndexOutOfBounds(() -> list.listIterator(-1));
    assertIndexOutOfBounds(() -> list.listIterator(n + 1));

    // Bulk views.
    assertThat(list.toArray()).containsExactly(reference.toArray());
    Object[] tooSmall = new Object[0];
    assertThat(list.toArray(tooSmall)).containsExactly(reference.toArray());
    Object[] tooBig = new Object[n + 2];
    tooBig[n] = "sentinel";
    Object[] filled = list.toArray(tooBig);
    assertTrue(filled == tooBig, "toArray(T[]) must reuse a large enough array");
    for (int i = 0; i < n; i++) {
      assertEquals(reference.get(i), filled[i]);
    }
    assertEquals(null, filled[n], "toArray(T[]) must null-terminate");
    assertEquals(reference, list.stream().collect(Collectors.toList()), "stream");
    List<E> forEach = new ArrayList<>();
    list.forEach(forEach::add);
    assertEquals(reference, forEach, "forEach");
    Spliterator<E> spliterator = list.spliterator();
    assertTrue(spliterator.hasCharacteristics(Spliterator.SIZED), "spliterator SIZED");
    assertEquals(n, spliterator.estimateSize(), "spliterator size");
    List<E> split = new ArrayList<>();
    spliterator.forEachRemaining(split::add);
    assertEquals(reference, split, "spliterator traversal");

    // Sub-lists.
    assertIndexOutOfBounds(() -> list.subList(-1, 0));
    assertIndexOutOfBounds(() -> list.subList(0, n + 1));
    if (n > 0) {
      assertThatThrownBy(() -> list.subList(1, 0))
          .isInstanceOfAny(IndexOutOfBoundsException.class, IllegalArgumentException.class);
    }
    if (depth > 0) {
      for (int from = 0; from <= n; from++) {
        for (int to = from; to <= n; to++) {
          // Recurse twice for short lists, once otherwise, to keep the cost bounded.
          int nextDepth = n <= 4 ? depth - 1 : 0;
          assertReadContract(list.subList(from, to), reference.subList(from, to), nextDepth);
        }
      }
    }
  }

  /**
   * Asserts that every mutator of {@code list} (and of its iterators and sub-lists) throws {@link
   * UnsupportedOperationException} and leaves the list unchanged.
   */
  public static <E> void assertUnmodifiable(List<E> list, E sample) {
    List<E> before = new ArrayList<>(list);
    List<Executable> mutations = new ArrayList<>();
    mutations.add(() -> list.add(sample));
    mutations.add(() -> list.add(0, sample));
    mutations.add(() -> list.addAll(List.of(sample)));
    mutations.add(() -> list.addAll(0, List.of(sample)));
    mutations.add(() -> list.set(0, sample));
    mutations.add(() -> list.remove(0));
    mutations.add(() -> list.remove(sample));
    mutations.add(() -> list.removeAll(List.of(sample)));
    mutations.add(() -> list.retainAll(List.of()));
    mutations.add(() -> list.removeIf(x -> true));
    mutations.add(() -> list.replaceAll(x -> x));
    mutations.add(() -> list.sort(null));
    mutations.add(list::clear);
    if (!before.isEmpty()) {
      mutations.add(
          () -> {
            Iterator<E> it = list.iterator();
            it.next();
            it.remove();
          });
      mutations.add(
          () -> {
            ListIterator<E> it = list.listIterator();
            it.next();
            it.set(sample);
          });
      mutations.add(() -> list.listIterator().add(sample));
      mutations.add(() -> list.subList(0, 1).clear());
    }
    for (Executable mutation : mutations) {
      try {
        mutation.execute();
      } catch (UnsupportedOperationException expected) {
        continue;
      } catch (IndexOutOfBoundsException expected) {
        // Acceptable for index-based mutators of an empty list.
        if (before.isEmpty()) {
          continue;
        }
        throw new AssertionError("Unexpected exception from mutator", expected);
      } catch (Throwable t) {
        throw new AssertionError("Unexpected exception from mutator", t);
      }
      // Some mutators are permitted to be no-ops when they would not change the list.
      assertEquals(before, list, "mutator changed an unmodifiable list");
    }
    assertEquals(before, list);
  }

  /** Asserts a write-through {@code set} on {@code list} and {@code backing} views of each other. */
  public static <E> void assertSetWritesThrough(List<E> list, Collection<E> observed, E replacement) {
    if (list.isEmpty()) {
      return;
    }
    E old = list.get(0);
    E returned = list.set(0, replacement);
    assertEquals(old, returned, "set returns previous element");
    assertEquals(replacement, list.get(0));
    assertTrue(observed.contains(replacement) || Objects.equals(replacement, old));
    list.set(0, old);
  }

  private static void assertIndexOutOfBounds(Executable executable) {
    try {
      executable.execute();
    } catch (IndexOutOfBoundsException expected) {
      return;
    } catch (Throwable t) {
      throw new AssertionError("expected IndexOutOfBoundsException", t);
    }
    throw new AssertionError("expected IndexOutOfBoundsException");
  }
}
