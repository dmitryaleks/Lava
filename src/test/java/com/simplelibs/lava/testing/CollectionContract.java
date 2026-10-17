package com.simplelibs.lava.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Spliterator;
import java.util.stream.Collectors;
import org.junit.jupiter.api.function.Executable;

/**
 * Hand-written replacement for the read-only parts of guava-testlib's {@code
 * CollectionTestSuiteBuilder} and {@code SetTestSuiteBuilder}.
 */
public final class CollectionContract {
  private CollectionContract() {}

  private static final Object MISSING =
      new Object() {
        @Override
        public String toString() {
          return "MISSING";
        }
      };

  /**
   * Asserts that {@code collection} contains exactly {@code expected}; if {@code ordered}, also in
   * that iteration order.
   */
  public static <E> void assertReadContract(
      Collection<E> collection, Collection<E> expected, boolean ordered) {
    List<E> reference = new ArrayList<>(expected);
    int n = reference.size();
    assertEquals(n, collection.size(), "size");
    assertEquals(n == 0, collection.isEmpty(), "isEmpty");
    for (E e : reference) {
      assertTrue(collection.contains(e), "contains " + e);
    }
    assertFalse(collection.contains(MISSING), "contains MISSING");
    assertTrue(collection.containsAll(reference), "containsAll");
    assertFalse(collection.containsAll(List.of(MISSING)), "containsAll MISSING");

    List<E> iterated = new ArrayList<>();
    Iterator<E> it = collection.iterator();
    while (it.hasNext()) {
      iterated.add(it.next());
    }
    assertFalse(it.hasNext());
    assertThatThrownBy(it::next).isInstanceOf(NoSuchElementException.class);
    assertContents(reference, iterated, ordered, "iterator");

    List<E> forEach = new ArrayList<>();
    collection.forEach(forEach::add);
    assertEquals(iterated, forEach, "forEach order must match iterator order");
    assertEquals(iterated, collection.stream().collect(Collectors.toList()), "stream");
    assertThat(collection.toArray()).containsExactly(iterated.toArray());
    assertThat(collection.toArray(new Object[0])).containsExactly(iterated.toArray());
    Object[] tooBig = new Object[n + 1];
    tooBig[n] = "sentinel";
    Object[] filled = collection.toArray(tooBig);
    assertTrue(filled == tooBig, "toArray(T[]) must reuse a large enough array");
    assertEquals(null, filled[n], "toArray(T[]) must null-terminate");

    Spliterator<E> spliterator = collection.spliterator();
    if (spliterator.hasCharacteristics(Spliterator.SIZED)) {
      assertEquals(n, spliterator.estimateSize(), "spliterator size");
    }
    List<E> split = new ArrayList<>();
    while (spliterator.tryAdvance(split::add)) {
      // keep advancing
    }
    assertEquals(iterated, split, "spliterator traversal must match iterator order");
    ListContract.assertSplitsInOrder(collection.spliterator(), iterated);
    assertEquals(iterated.toString(), collection.toString(), "toString");

    if (collection instanceof Set) {
      Set<E> set = (Set<E>) collection;
      assertEquals(new HashSet<>(reference), set, "Set.equals");
      assertEquals(set, new HashSet<>(reference), "Set.equals (reversed)");
      assertEquals(new HashSet<>(reference).hashCode(), set.hashCode(), "Set.hashCode");
      Set<Object> bigger = new HashSet<>(reference);
      bigger.add(MISSING);
      assertFalse(set.equals(bigger), "Set.equals larger set");
      assertFalse(set.equals(List.copyOf(reference)), "a Set must not equal a List");
      assertEquals(new LinkedHashSet<>(iterated).size(), n, "a Set must not repeat elements");
    }
  }

  private static <E> void assertContents(
      List<E> expected, List<E> actual, boolean ordered, String how) {
    if (ordered) {
      assertEquals(expected, actual, how + " order");
    } else {
      assertThat(actual).as(how).containsExactlyInAnyOrderElementsOf(expected);
    }
  }

  /** Asserts that every mutator of {@code collection} and its iterator throws UOE. */
  public static <E> void assertAlwaysThrowsUnsupported(Collection<E> collection, E sample) {
    List<Executable> mutations = new ArrayList<>();
    mutations.add(() -> collection.add(sample));
    mutations.add(() -> collection.addAll(List.of(sample)));
    mutations.add(() -> collection.remove(sample));
    mutations.add(() -> collection.removeAll(List.of(sample)));
    mutations.add(() -> collection.retainAll(List.of()));
    mutations.add(() -> collection.removeIf(x -> true));
    mutations.add(collection::clear);
    if (!collection.isEmpty()) {
      mutations.add(
          () -> {
            Iterator<E> it = collection.iterator();
            it.next();
            it.remove();
          });
    }
    for (Executable mutation : mutations) {
      assertThatThrownBy(mutation::execute).isInstanceOf(UnsupportedOperationException.class);
    }
  }
}
