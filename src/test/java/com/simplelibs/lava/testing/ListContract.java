package com.simplelibs.lava.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
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
  private static final Object MISSING =
      new Object() {
        @Override
        public String toString() {
          return "MISSING";
        }
      };

  /**
   * Asserts that {@code list} behaves as a list containing exactly {@code expected}, in order, for
   * every read operation including iterators, spliterators, streams and sub-lists (two levels deep
   * for lists of up to four elements, one level otherwise). Elements must have well-behaved {@code
   * equals} (no NaN).
   *
   * @param absent an element of the list's element type that is not in {@code expected}, used for
   *     negative queries that a type check alone cannot answer; may be {@code null} to skip them
   */
  public static <E> void assertReadContract(List<E> list, List<E> expected, E absent) {
    assertReadContract(list, expected, absent, 2);
  }

  /** Like {@link #assertReadContract(List, List, Object)} without a typed absent element. */
  public static <E> void assertReadContract(List<E> list, List<E> expected) {
    assertReadContract(list, expected, null, 2);
  }

  private static <E> void assertReadContract(List<E> list, List<E> expected, E absent, int depth) {
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
    if (absent != null) {
      assertFalse(list.contains(absent), "contains absent element " + absent);
      assertEquals(-1, list.indexOf(absent), "indexOf absent element");
      assertEquals(-1, list.lastIndexOf(absent), "lastIndexOf absent element");
    }
    assertTrue(list.containsAll(reference), "containsAll");
    assertFalse(list.containsAll(List.of(MISSING)), "containsAll MISSING");

    // Equality and hashing against several List implementations, in both directions.
    assertEquals(reference, list);
    assertEquals(list, reference);
    assertEquals(new LinkedList<>(reference), list);
    assertTrue(list.equals(new LinkedList<>(reference)), "equals a non-RandomAccess list");
    if (n > 0) {
      LinkedList<Object> differentLinked = new LinkedList<>(reference);
      differentLinked.set(n - 1, MISSING);
      assertFalse(list.equals(differentLinked), "equals a different non-RandomAccess list");
    }
    assertTrue(list.equals(list), "reflexive equals");
    assertFalse(list.equals(null), "equals(null)");
    assertEquals(reference.hashCode(), list.hashCode(), "hashCode");
    assertEquals(reference.toString(), list.toString(), "toString");
    List<Object> longer = new ArrayList<>(reference);
    longer.add(MISSING);
    assertFalse(list.equals(longer), "equals longer list");
    if (n > 0) {
      List<Object> sameSizeDifferent = new ArrayList<>(reference);
      sameSizeDifferent.set(n - 1, MISSING);
      assertFalse(list.equals(sameSizeDifferent), "equals same-size list with other content");
      assertFalse(list.equals(reference.subList(0, n - 1)), "equals shorter list");
    }
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
      assertListIteratorPosition(ri, li);
      while (ri.hasNext()) {
        assertEquals(ri.next(), li.next());
        assertListIteratorPosition(ri, li);
      }
      assertFalse(li.hasNext());
      while (ri.hasPrevious()) {
        assertEquals(ri.previous(), li.previous());
        assertListIteratorPosition(ri, li);
      }
      assertFalse(li.hasPrevious());
      ListIterator<E> atStart = li;
      assertThatThrownBy(atStart::previous).isInstanceOf(NoSuchElementException.class);
      if (n > 0) {
        // Reversing direction returns the same element.
        ListIterator<E> zigzag = list.listIterator(start);
        if (zigzag.hasNext()) {
          E forward = zigzag.next();
          assertEquals(forward, zigzag.previous(), "next() then previous()");
        } else {
          E backward = zigzag.previous();
          assertEquals(backward, zigzag.next(), "previous() then next()");
        }
      }
    }
    assertIndexOutOfBounds(() -> list.listIterator(-1));
    assertIndexOutOfBounds(() -> list.listIterator(n + 1));

    // Bulk views.
    Object[] array = list.toArray();
    assertThat(array).containsExactly(reference.toArray());
    if (n > 0) {
      array[0] = MISSING;
      assertEquals(reference.get(0), list.get(0), "toArray() must not expose internal state");
    }
    Object[] tooSmall = new Object[0];
    assertThat(list.toArray(tooSmall)).containsExactly(reference.toArray());
    Object[] tooBig = new Object[n + 2];
    tooBig[n] = "sentinel";
    tooBig[n + 1] = "untouched";
    Object[] filled = list.toArray(tooBig);
    assertTrue(filled == tooBig, "toArray(T[]) must reuse a large enough array");
    for (int i = 0; i < n; i++) {
      assertEquals(reference.get(i), filled[i]);
    }
    assertEquals(null, filled[n], "toArray(T[]) must null-terminate");
    assertEquals("untouched", filled[n + 1], "toArray(T[]) must not write past the terminator");
    if (n > 0) {
      Class<?> elementType = commonClass(java.util.Arrays.asList(list.toArray()));
      if (elementType != null) {
        Object[] typed = (Object[]) Array.newInstance(elementType, 0);
        Object[] result = list.toArray(typed);
        assertEquals(elementType, result.getClass().getComponentType(), "toArray(T[]) type");
        assertThat(result).containsExactly(reference.toArray());
      }
    }
    assertEquals(reference, list.stream().collect(Collectors.toList()), "stream");
    List<E> forEach = new ArrayList<>();
    list.forEach(forEach::add);
    assertEquals(reference, forEach, "forEach");
    assertSpliterator(list.spliterator(), reference);

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
          List<E> sub = list.subList(from, to);
          assertReadContract(sub, reference.subList(from, to), absent, nextDepth);
          if (from > 0 || to < n) {
            for (E outside : reference.subList(0, from)) {
              if (!reference.subList(from, to).contains(outside)) {
                assertFalse(sub.contains(outside), "subList contains element outside window");
                assertEquals(-1, sub.indexOf(outside), "subList indexOf element outside window");
              }
            }
            for (E outside : reference.subList(to, n)) {
              if (!reference.subList(from, to).contains(outside)) {
                assertFalse(sub.contains(outside), "subList contains element outside window");
                assertEquals(-1, sub.lastIndexOf(outside), "subList lastIndexOf outside window");
              }
            }
          }
        }
      }
    }
  }

  private static <E> void assertListIteratorPosition(ListIterator<E> ri, ListIterator<E> li) {
    assertEquals(ri.hasNext(), li.hasNext(), "hasNext");
    assertEquals(ri.hasPrevious(), li.hasPrevious(), "hasPrevious");
    assertEquals(ri.nextIndex(), li.nextIndex(), "nextIndex");
    assertEquals(ri.previousIndex(), li.previousIndex(), "previousIndex");
  }

  private static Class<?> commonClass(List<?> elements) {
    Class<?> type = null;
    for (Object e : elements) {
      if (e == null) {
        return null;
      }
      if (type == null) {
        type = e.getClass();
      } else if (type != e.getClass()) {
        return null;
      }
    }
    return type;
  }

  /** Checks traversal through tryAdvance, forEachRemaining and splitting. */
  public static <E> void assertSpliterator(Spliterator<E> spliterator, List<E> expected) {
    assertTrue(spliterator.hasCharacteristics(Spliterator.SIZED), "spliterator SIZED");
    assertEquals(expected.size(), spliterator.estimateSize(), "spliterator size");
    List<E> viaTryAdvance = new ArrayList<>();
    Spliterator<E> first = spliterator;
    while (first.tryAdvance(viaTryAdvance::add)) {
      // keep advancing
    }
    assertEquals(expected, viaTryAdvance, "spliterator tryAdvance traversal");
    assertFalse(first.tryAdvance(e -> {}), "exhausted spliterator must not advance");
  }

  /** Checks that recursively splitting {@code spliterator} still yields every element in order. */
  public static <E> void assertSplitsInOrder(Spliterator<E> spliterator, List<E> expected) {
    List<E> out = new ArrayList<>();
    split(spliterator, out, 0);
    assertEquals(expected, out, "split spliterator traversal");
  }

  private static <E> void split(Spliterator<E> s, List<E> out, int depth) {
    Spliterator<E> prefix = depth < 4 ? s.trySplit() : null;
    if (prefix != null) {
      assertNotSame(s, prefix, "trySplit must not return itself");
      split(prefix, out, depth + 1);
    }
    s.forEachRemaining(out::add);
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
    mutations.add(() -> list.sort((x, y) -> 0)); // a comparator, so non-Comparable elements reach set()
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

  /** Asserts that every mutator of {@code list} throws {@link UnsupportedOperationException}. */
  public static <E> void assertAlwaysThrowsUnsupported(List<E> list, E sample) {
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
    mutations.add(() -> list.sort((x, y) -> 0)); // a comparator, so non-Comparable elements reach set()
    mutations.add(list::clear);
    for (Executable mutation : mutations) {
      assertThatThrownBy(mutation::execute).isInstanceOf(UnsupportedOperationException.class);
    }
  }

  /** Asserts that {@code set} on {@code list} writes through to {@code observed} and back. */
  public static <E> void assertSetWritesThrough(List<E> list, List<E> observed, E replacement) {
    if (list.isEmpty()) {
      return;
    }
    E old = list.get(0);
    E returned = list.set(0, replacement);
    assertEquals(old, returned, "set returns previous element");
    assertEquals(replacement, list.get(0));
    assertTrue(Objects.equals(observed.get(0), replacement), "set must write through");
    list.set(0, old);
    assertEquals(old, observed.get(0), "restore must write through");
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

  static <E> List<E> copy(Iterable<E> iterable) {
    List<E> list = new ArrayList<>();
    iterable.forEach(list::add);
    return Collections.unmodifiableList(list);
  }
}
