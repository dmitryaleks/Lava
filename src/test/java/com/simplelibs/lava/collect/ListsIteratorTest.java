package com.simplelibs.lava.collect;

import static com.simplelibs.lava.collect.testing.IteratorFeature.MODIFIABLE;
import static com.simplelibs.lava.collect.testing.IteratorFeature.SUPPORTS_REMOVE;
import static com.simplelibs.lava.collect.testing.IteratorFeature.SUPPORTS_SET;
import static com.simplelibs.lava.collect.testing.IteratorFeature.UNMODIFIABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.simplelibs.lava.collect.testing.IteratorFeature;
import com.simplelibs.lava.collect.testing.IteratorTester;
import com.simplelibs.lava.collect.testing.ListIteratorTester;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs testlib's exhaustive {@link ListIteratorTester} and {@link IteratorTester} over the views
 * {@link Lists} returns, as Guava's {@code ListsTest.suite()} does through {@code
 * ListTestSuiteBuilder}: every sequence of up to four list-iterator operations (five for plain
 * iterators) is compared against a reference iterator, including the illegal ones, and after each
 * sequence the backing list must hold the expected elements.
 */
class ListsIteratorTest {
  private static final int LIST_ITERATOR_STEPS = 4;
  private static final int ITERATOR_STEPS = 5;

  private static final List<List<String>> CONTENTS =
      List.of(List.of(), List.of("a"), List.of("a", "b", "c"));

  private static List<String> reversed(List<String> list) {
    List<String> result = new ArrayList<>(list);
    Collections.reverse(result);
    return result;
  }

  private static List<String> upper(List<String> list) {
    List<String> result = new ArrayList<>();
    for (String s : list) {
      result.add(s.toUpperCase(Locale.ROOT));
    }
    return result;
  }

  private static List<String> lower(List<String> list) {
    List<String> result = new ArrayList<>();
    for (String s : list) {
      result.add(s.toLowerCase(Locale.ROOT));
    }
    return result;
  }

  /**
   * A modifiable view under test: {@code backing} creates a fresh backing list from the elements
   * the view should show, {@code view} wraps it, and {@code backingContents} maps the elements the
   * view shows to those the backing list must hold.
   */
  private static final class ViewKind {
    final String name;
    final Set<IteratorFeature> features;
    final Function<List<String>, List<String>> backing;
    final Function<List<String>, List<String>> view;
    final Function<List<String>, List<String>> backingContents;

    ViewKind(
        String name,
        Set<IteratorFeature> features,
        Function<List<String>, List<String>> backing,
        Function<List<String>, List<String>> view,
        Function<List<String>, List<String>> backingContents) {
      this.name = name;
      this.features = features;
      this.backing = backing;
      this.view = view;
      this.backingContents = backingContents;
    }
  }

  private static final Function<String, String> UPPER = s -> s.toUpperCase(Locale.ROOT);

  private static List<ViewKind> viewKinds() {
    return List.of(
        new ViewKind(
            "reverse(ArrayList)",
            MODIFIABLE,
            elements -> new ArrayList<>(reversed(elements)),
            Lists::reverse,
            ListsIteratorTest::reversed),
        new ViewKind(
            "reverse(LinkedList)",
            MODIFIABLE,
            elements -> new LinkedList<>(reversed(elements)),
            Lists::reverse,
            ListsIteratorTest::reversed),
        new ViewKind(
            "reverse(ArrayList.subList)",
            MODIFIABLE,
            elements -> new ArrayList<>(reversed(elements)),
            backing -> Lists.reverse(backing.subList(0, backing.size())),
            ListsIteratorTest::reversed),
        new ViewKind(
            "reverse(LinkedList.subList)",
            MODIFIABLE,
            elements -> new LinkedList<>(reversed(elements)),
            backing -> Lists.reverse(backing.subList(0, backing.size())),
            ListsIteratorTest::reversed),
        new ViewKind(
            "reverse(Arrays.asList)",
            Set.of(SUPPORTS_SET),
            elements -> Arrays.asList(reversed(elements).toArray(new String[0])),
            Lists::reverse,
            ListsIteratorTest::reversed),
        new ViewKind(
            "transform(ArrayList)",
            Set.of(SUPPORTS_REMOVE),
            elements -> new ArrayList<>(lower(elements)),
            backing -> Lists.transform(backing, UPPER),
            ListsIteratorTest::lower),
        new ViewKind(
            "transform(LinkedList)",
            Set.of(SUPPORTS_REMOVE),
            elements -> new LinkedList<>(lower(elements)),
            backing -> Lists.transform(backing, UPPER),
            ListsIteratorTest::lower),
        new ViewKind(
            "transform(reverse(LinkedList))",
            Set.of(SUPPORTS_REMOVE),
            elements -> new LinkedList<>(reversed(lower(elements))),
            backing -> Lists.transform(Lists.reverse(backing), UPPER),
            elements -> reversed(lower(elements))),
        new ViewKind(
            "reverse(transform(ArrayList))",
            Set.of(SUPPORTS_REMOVE),
            elements -> new ArrayList<>(reversed(lower(elements))),
            backing -> Lists.reverse(Lists.transform(backing, UPPER)),
            elements -> reversed(lower(elements))));
  }

  @TestFactory
  List<DynamicTest> modifiableViews() {
    List<DynamicTest> tests = new ArrayList<>();
    for (ViewKind kind : viewKinds()) {
      for (List<String> contents : CONTENTS) {
        // Views over transform show upper-case elements; the inserted element must match.
        List<String> expected = kind.name.contains("transform") ? upper(contents) : contents;
        for (int start : startIndexes(expected.size())) {
          tests.add(
              dynamicTest(
                  kind.name + " " + expected + " listIterator(" + start + ")",
                  () -> runListIteratorTester(kind, expected, start)));
        }
        tests.add(
            dynamicTest(
                kind.name + " " + expected + " iterator()",
                () -> runIteratorTester(kind, expected)));
      }
    }
    return tests;
  }

  private static List<Integer> startIndexes(int size) {
    return size == 0 ? List.of(0) : List.of(0, size / 2 + 1 > size ? size : size / 2 + 1, size);
  }

  private static void runListIteratorTester(ViewKind kind, List<String> expected, int start) {
    List<?>[] backing = new List<?>[1];
    new ListIteratorTester<String>(
        LIST_ITERATOR_STEPS,
        Collections.singleton("X"),
        kind.features,
        expected,
        start) {
      @Override
      protected ListIterator<String> newTargetIterator() {
        List<String> b = kind.backing.apply(expected);
        backing[0] = b;
        return kind.view.apply(b).listIterator(start);
      }

      @Override
      protected void verify(List<String> elements) {
        assertThat(backing[0]).isEqualTo(kind.backingContents.apply(elements));
      }
    }.test();
  }

  private static void runIteratorTester(ViewKind kind, List<String> expected) {
    List<?>[] backing = new List<?>[1];
    Set<IteratorFeature> features =
        kind.features.contains(SUPPORTS_REMOVE) ? Set.of(SUPPORTS_REMOVE) : UNMODIFIABLE;
    new IteratorTester<String>(
        ITERATOR_STEPS, features, expected, IteratorTester.KnownOrder.KNOWN_ORDER) {
      @Override
      protected Iterator<String> newTargetIterator() {
        List<String> b = kind.backing.apply(expected);
        backing[0] = b;
        return kind.view.apply(b).iterator();
      }

      @Override
      protected void verify(List<String> elements) {
        assertThat(backing[0]).isEqualTo(kind.backingContents.apply(elements));
      }
    }.test();
  }

  /** The read-only views must reject every mutation through their list iterators. */
  @TestFactory
  List<DynamicTest> unmodifiableViews() {
    List<DynamicTest> tests = new ArrayList<>();
    unmodifiable(tests, "asList(a, [b, c])", () -> Lists.asList("a", new String[] {"b", "c"}), "X");
    unmodifiable(tests, "asList(a, [])", () -> Lists.asList("a", new String[0]), "X");
    unmodifiable(tests, "asList(a, b, [c])", () -> Lists.asList("a", "b", new String[] {"c"}), "X");
    unmodifiable(
        tests, "reverse(ImmutableList)", () -> Lists.reverse(ImmutableList.of("c", "b", "a")), "X");
    unmodifiable(tests, "charactersOf(String)", () -> Lists.charactersOf("abc"), 'X');
    unmodifiable(
        tests, "charactersOf(CharSequence)", () -> Lists.charactersOf(new StringBuilder("abc")), 'X');
    unmodifiable(
        tests,
        "cartesianProduct(...).get(0)",
        () -> Lists.cartesianProduct(List.of("a"), List.of("b"), List.of("c")).get(0),
        "X");
    unmodifiable(
        tests,
        "partition outer list",
        () -> Lists.partition(new ArrayList<>(List.of("a", "b", "c")), 2),
        List.of("X"));
    unmodifiable(
        tests,
        "cartesianProduct outer list",
        () -> Lists.cartesianProduct(List.of("a", "b"), List.of("c")),
        List.of("X"));
    return tests;
  }

  private static <E> void unmodifiable(
      List<DynamicTest> tests, String name, Supplier<List<E>> factory, E toInsert) {
    List<E> expected = new ArrayList<>(factory.get());
    for (int start : startIndexes(expected.size())) {
      tests.add(
          dynamicTest(
              name + " listIterator(" + start + ")",
              () ->
                  new ListIteratorTester<E>(
                      LIST_ITERATOR_STEPS,
                      Collections.singleton(toInsert),
                      UNMODIFIABLE,
                      expected,
                      start) {
                    @Override
                    protected ListIterator<E> newTargetIterator() {
                      return factory.get().listIterator(start);
                    }
                  }.test()));
    }
  }
}
