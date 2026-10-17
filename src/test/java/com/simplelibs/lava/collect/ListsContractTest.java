package com.simplelibs.lava.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.simplelibs.lava.testing.ListContract;
import com.simplelibs.lava.testing.SerializableTester;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.RandomAccess;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Read-only and unmodifiability contracts for every view {@link Lists} returns, over fixtures with
 * distinct elements, duplicate elements and null elements, plus the flags (RandomAccess,
 * Serializable) and special cases Guava documents. The mutation contract of the modifiable views is
 * covered by {@link ListsIteratorTest} and {@link ListsViewDifferentialTest}.
 */
class ListsContractTest {

  private static final List<String> ELEMENTS = List.of("a", "b", "c", "d", "e", "f", "g");

  /** Fixtures with repeated elements, so indexOf and lastIndexOf differ. */
  private static final List<List<String>> DUPLICATES =
      List.of(List.of("a", "a"), List.of("a", "b", "a"), List.of("a", "b", "a", "c", "b"));

  /** Fixtures with null elements, for the views that allow them. */
  private static final List<List<String>> WITH_NULLS =
      List.of(
          Arrays.asList((String) null),
          Arrays.asList("a", null),
          Arrays.asList(null, "a", null, "b"));

  /** Serializable, null-tolerant function, so transformed views can be reserialized. */
  private enum Upper implements Function<String, String> {
    INSTANCE;

    @Override
    public String apply(String s) {
      return s == null ? null : s.toUpperCase(java.util.Locale.ROOT);
    }
  }

  private static List<String> upper(List<String> list) {
    List<String> result = new ArrayList<>();
    for (String s : list) {
      result.add(Upper.INSTANCE.apply(s));
    }
    return result;
  }

  private static List<String> reversed(List<String> list) {
    List<String> result = new ArrayList<>(list);
    Collections.reverse(result);
    return result;
  }

  private static void readOnlyCase(
      List<DynamicTest> tests, String name, Supplier<List<String>> factory, List<String> expected) {
    tests.add(
        dynamicTest(
            name,
            () -> {
              List<String> list = factory.get();
              ListContract.assertReadContract(list, expected, "absent");
              ListContract.assertUnmodifiable(list, "z");
            }));
  }

  private static void readCase(
      List<DynamicTest> tests, String name, Supplier<List<String>> factory, List<String> expected) {
    tests.add(
        dynamicTest(name, () -> ListContract.assertReadContract(factory.get(), expected, "absent")));
  }

  /** Cases for the views that accept any elements, including duplicates and nulls. */
  private static void generalCases(List<DynamicTest> tests, String label, List<String> expected) {
    int n = expected.size();
    if (n >= 1) {
      String[] rest = expected.subList(1, n).toArray(new String[0]);
      readOnlyCase(
          tests, "asList(first, rest) " + label, () -> Lists.asList(expected.get(0), rest), expected);
    }
    if (n >= 2) {
      String[] rest = expected.subList(2, n).toArray(new String[0]);
      readOnlyCase(
          tests,
          "asList(first, second, rest) " + label,
          () -> Lists.asList(expected.get(0), expected.get(1), rest),
          expected);
    }
    readCase(
        tests,
        "transform(ArrayList) " + label,
        () -> Lists.transform(new ArrayList<>(expected), Upper.INSTANCE),
        upper(expected));
    readCase(
        tests,
        "transform(LinkedList) " + label,
        () -> Lists.transform(new LinkedList<>(expected), Upper.INSTANCE),
        upper(expected));
    readCase(
        tests,
        "reverse(ArrayList) " + label,
        () -> Lists.reverse(new ArrayList<>(expected)),
        reversed(expected));
    readCase(
        tests,
        "reverse(LinkedList) " + label,
        () -> Lists.reverse(new LinkedList<>(expected)),
        reversed(expected));
    readCase(
        tests,
        "reverse(reverse(ArrayList)) " + label,
        () -> Lists.reverse(Lists.reverse(new ArrayList<>(expected))),
        expected);
    readCase(
        tests,
        "reverse(ArrayList.subList) " + label,
        () -> Lists.reverse(new ArrayList<>(expected).subList(0, n)),
        reversed(expected));
    for (int chunk = 1; chunk <= 3 && n > 0; chunk++) {
      int c = chunk;
      for (int i = 0; i * c < n; i++) {
        int from = i * c;
        int to = Math.min(from + c, n);
        int index = i;
        readCase(
            tests,
            "partition(" + label + ", " + c + ").get(" + i + ")",
            () -> Lists.partition(new ArrayList<>(expected), c).get(index),
            expected.subList(from, to));
        readCase(
            tests,
            "partition(LinkedList " + label + ", " + c + ").get(" + i + ")",
            () -> Lists.partition(new LinkedList<>(expected), c).get(index),
            expected.subList(from, to));
      }
    }
  }

  /** Cases for the views whose elements must be non-null. */
  private static void nonNullCases(List<DynamicTest> tests, String label, List<String> expected) {
    readOnlyCase(
        tests,
        "reverse(ImmutableList) " + label,
        () -> Lists.reverse(ImmutableList.copyOf(expected)),
        reversed(expected));
    readOnlyCase(
        tests,
        "charactersOf(String) " + label,
        () -> charsAsStrings(Lists.charactersOf(String.join("", expected)), expected),
        expected);
    readOnlyCase(
        tests,
        "charactersOf(CharSequence) " + label,
        () -> charsAsStrings(Lists.charactersOf(new StringBuilder(String.join("", expected))), expected),
        expected);
  }

  @TestFactory
  List<DynamicTest> views() {
    List<DynamicTest> tests = new ArrayList<>();
    for (int n = 0; n <= ELEMENTS.size(); n++) {
      List<String> expected = ELEMENTS.subList(0, n);
      generalCases(tests, String.valueOf(n), expected);
      nonNullCases(tests, String.valueOf(n), expected);
    }
    for (List<String> expected : DUPLICATES) {
      generalCases(tests, "dups " + expected, expected);
      nonNullCases(tests, "dups " + expected, expected);
    }
    for (List<String> expected : WITH_NULLS) {
      generalCases(tests, "nulls " + expected, expected);
    }
    return tests;
  }

  /**
   * Checks the contract of a {@code List<Character>} against single-letter strings, then adapts it
   * to the String-based contract through an unmodifiable copy.
   */
  private static List<String> charsAsStrings(List<Character> chars, List<String> expectedLetters) {
    List<Character> expected = new ArrayList<>();
    for (String letter : expectedLetters) {
      expected.add(letter.charAt(0));
    }
    ListContract.assertReadContract(chars, expected, '?');
    ListContract.assertUnmodifiable(chars, '?');
    List<String> result = new ArrayList<>();
    for (char c : chars) {
      result.add(String.valueOf(c));
    }
    return Collections.unmodifiableList(result);
  }

  @Test
  void charactersOf_flagsAndDuplicates() {
    StringBuilder builder = new StringBuilder("abc");
    List<Character> chars = Lists.charactersOf(builder);
    ListContract.assertReadContract(chars, List.of('a', 'b', 'c'), 'z');
    ListContract.assertUnmodifiable(chars, 'z');
    builder.append('d');
    assertThat(chars).containsExactly('a', 'b', 'c', 'd');
    assertThat(chars).isNotInstanceOf(RandomAccess.class);
    assertThat(chars).isNotInstanceOf(Serializable.class);

    List<Character> aba = Lists.charactersOf("aba");
    assertThat(aba).isInstanceOf(ImmutableList.class).isInstanceOf(RandomAccess.class);
    assertThat(aba.indexOf('a')).isEqualTo(0);
    assertThat(aba.lastIndexOf('a')).isEqualTo(2);
    assertThat(aba.indexOf("a")).isEqualTo(-1);
    assertThat(aba.subList(1, 3)).containsExactly('b', 'a');
    // Not a partial view, so copyOf returns the same instance (as in Guava).
    assertThat(ImmutableList.copyOf(aba)).isSameAs(aba);
    assertThat(Lists.charactersOf(new StringBuilder("aba")).lastIndexOf('a')).isEqualTo(2);
  }

  @Test
  void reverse_flagsAndFastPaths() {
    assertThat(Lists.reverse(new ArrayList<>(ELEMENTS))).isInstanceOf(RandomAccess.class);
    assertThat(Lists.reverse(new LinkedList<>(ELEMENTS))).isNotInstanceOf(RandomAccess.class);
    ImmutableList<String> immutable = ImmutableList.copyOf(ELEMENTS);
    // An ImmutableList is reversed by ImmutableList.reverse(), so the result is immutable too.
    List<String> reversedImmutable = Lists.reverse(immutable);
    assertThat(reversedImmutable).isInstanceOf(ImmutableList.class).isEqualTo(reversed(ELEMENTS));
    assertThat(Lists.reverse(reversedImmutable)).isSameAs(immutable);
    assertThatThrownBy(() -> reversedImmutable.remove("absent"))
        .isInstanceOf(UnsupportedOperationException.class);
    List<String> backing = new ArrayList<>(ELEMENTS);
    assertThat(Lists.reverse(Lists.reverse(backing))).isSameAs(backing);
  }

  @Test
  void cartesianProduct_outerAndInnerContracts() {
    List<List<String>> product =
        Lists.cartesianProduct(List.of("a", "b"), List.of("x", "y", "z"), List.of("1"));
    List<List<String>> expected = new ArrayList<>();
    for (String a : List.of("a", "b")) {
      for (String x : List.of("x", "y", "z")) {
        expected.add(List.of(a, x, "1"));
      }
    }
    ListContract.assertReadContract(product, expected, List.of("q", "q", "q"));
    ListContract.assertAlwaysThrowsUnsupported(product, List.of("a", "x", "1"));
    for (int i = 0; i < product.size(); i++) {
      ListContract.assertReadContract(product.get(i), expected.get(i), "absent");
      ListContract.assertAlwaysThrowsUnsupported(product.get(i), "z");
      // The element lists are partial views, so copyOf copies them (as in Guava).
      assertThat(ImmutableList.copyOf(product.get(i))).isNotSameAs(product.get(i));
    }
    assertThat(product).isInstanceOf(RandomAccess.class);
    assertThat(Lists.cartesianProduct()).containsExactly(List.of());
    assertThat(Lists.cartesianProduct(List.of("a"), List.of())).isEmpty();
    assertThat(product.indexOf(List.of("b", "y", "1"))).isEqualTo(4);
    assertThat(product.indexOf(List.of("b", "y"))).isEqualTo(-1);
    assertThat(product.contains(List.of("b", "q", "1"))).isFalse();
  }

  @Test
  void cartesianProduct_duplicateAxesElements() {
    List<List<String>> product = Lists.cartesianProduct(List.of("a", "a"), List.of("x", "y", "x"));
    assertThat(product).hasSize(6);
    assertThat(product.indexOf(List.of("a", "x"))).isEqualTo(0);
    assertThat(product.lastIndexOf(List.of("a", "x"))).isEqualTo(5);
    assertThat(product.get(4)).containsExactly("a", "y");
  }

  @Test
  void cartesianProduct_copiesItsAxesDefensively() {
    List<Integer> axis = new ArrayList<>(List.of(1, 2));
    List<List<Integer>> product = Lists.cartesianProduct(axis, axis);
    axis.clear();
    assertThat(product).hasSize(4);
    assertThat(product.get(3)).containsExactly(2, 2);
    axis.add(9);
    assertThat(product.toString()).isEqualTo("[[1, 1], [1, 2], [2, 1], [2, 2]]");
  }

  @Test
  void cartesianProduct_nullElementsAndEmptyAxes() {
    assertThatThrownBy(() -> Lists.cartesianProduct(Arrays.asList(1, null), List.of(2)))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("at index 1");
    assertThatThrownBy(() -> Lists.cartesianProduct(List.of(2), Arrays.asList(1, null)))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("at index 1");
    // Axes after the first empty one are neither checked for nulls nor counted towards the size.
    assertThat(Lists.cartesianProduct(List.of(), Arrays.asList(1, null))).isEmpty();
    List<Integer> big = Collections.nCopies(1 << 16, 0);
    assertThat(Lists.cartesianProduct(List.of(), big, big)).isEmpty();
    assertThat(Lists.cartesianProduct(big, List.of(), big)).isEmpty();
    // Every axis is copied before the size is computed, so an empty last axis wins too.
    assertThat(Lists.cartesianProduct(big, big, List.of())).isEmpty();
    assertThatThrownBy(() -> Lists.cartesianProduct(big, big))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cartesian product too large; must have size at most Integer.MAX_VALUE");
  }

  @Test
  void cartesianProduct_matchesGuavaOnEdgeCases() {
    List<Integer> big = Collections.nCopies(1 << 16, 0);
    List<List<List<Integer>>> inputs =
        List.of(
            List.of(),
            List.of(List.of()),
            List.of(List.of(), big, big),
            List.of(big, List.of(), big),
            List.of(big, big, List.of()),
            List.of(List.of(), Arrays.asList(1, null)),
            List.of(Arrays.asList(1, null)),
            List.of(List.of(1, 2), List.of(3)));
    for (List<List<Integer>> axes : inputs) {
      String guava = outcome(() -> com.google.common.collect.Lists.cartesianProduct(axes));
      String lava = outcome(() -> Lists.cartesianProduct(axes));
      assertThat(lava).as(String.valueOf(axes.size())).isEqualTo(guava);
    }
  }

  private static String outcome(Supplier<List<List<Integer>>> call) {
    try {
      List<List<Integer>> result = call.get();
      return result.size() < 10 ? result.toString() : "size " + result.size();
    } catch (RuntimeException e) {
      return e.getClass().getName() + ": " + e.getMessage();
    }
  }

  @Test
  void transform_flags() {
    assertThat(Lists.transform(new ArrayList<>(ELEMENTS), Upper.INSTANCE))
        .isInstanceOf(RandomAccess.class)
        .isInstanceOf(Serializable.class);
    assertThat(Lists.transform(new LinkedList<>(ELEMENTS), Upper.INSTANCE))
        .isNotInstanceOf(RandomAccess.class)
        .isInstanceOf(Serializable.class);
  }

  @Test
  void writeThroughSemantics() {
    // reverse: full read/write view.
    List<String> backing = new ArrayList<>(List.of("a", "b", "c"));
    List<String> reversed = Lists.reverse(backing);
    reversed.add("z"); // appends at the reversed end == front of the backing list
    assertThat(backing).containsExactly("z", "a", "b", "c");
    reversed.set(0, "C");
    assertThat(backing).containsExactly("z", "a", "b", "C");
    reversed.remove(0);
    assertThat(backing).containsExactly("z", "a", "b");
    reversed.subList(0, 2).clear();
    assertThat(backing).containsExactly("z");
    assertThat(Lists.reverse(reversed)).isSameAs(backing);
    ListContract.assertSetWritesThrough(Lists.reverse(backing), backing, "q");

    // transform: removal writes through, additions are unsupported.
    List<String> source = new ArrayList<>(List.of("a", "b", "c"));
    List<String> transformed = Lists.transform(source, Upper.INSTANCE);
    transformed.remove(1);
    assertThat(source).containsExactly("a", "c");
    assertThatThrownBy(() -> transformed.add("D"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> transformed.set(0, "D"))
        .isInstanceOf(UnsupportedOperationException.class);
    transformed.removeIf("C"::equals);
    assertThat(source).containsExactly("a");
    transformed.clear();
    assertThat(source).isEmpty();

    // partition: inner lists are subList views of the backing list.
    List<String> letters = new ArrayList<>(List.of("a", "b", "c", "d", "e"));
    List<List<String>> parts = Lists.partition(letters, 2);
    assertThat(parts).hasSize(3);
    parts.get(1).set(0, "C");
    assertThat(letters).containsExactly("a", "b", "C", "d", "e");
    assertThatThrownBy(() -> parts.add(List.of()))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(Lists.partition(new LinkedList<>(letters), 2)).isNotInstanceOf(RandomAccess.class);
    assertThat(Lists.partition(letters, 2)).isInstanceOf(RandomAccess.class);
    assertThat(Lists.partition(letters, 2).get(0)).isInstanceOf(RandomAccess.class);
  }

  @Test
  void partitionOfHugeListDoesNotOverflow() {
    // Lava fixes Guava's latent int overflow in Partition.get for lists of more than 2^30 elements.
    int hugeSize = Integer.MAX_VALUE - 1;
    List<Integer> huge =
        new java.util.AbstractList<Integer>() {
          @Override
          public Integer get(int index) {
            return index;
          }

          @Override
          public int size() {
            return hugeSize;
          }
        };
    List<List<Integer>> parts = Lists.partition(huge, 1 << 30);
    assertThat(parts).hasSize(2);
    assertThat(parts.get(1).size()).isEqualTo(hugeSize - (1 << 30));
    assertThat(parts.get(1).get(0)).isEqualTo(1 << 30);
    assertThatThrownBy(() -> com.google.common.collect.Lists.partition(huge, 1 << 30).get(1))
        .as("Guava 27.0.1 overflows here")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void serializationOfSerializableViews() {
    SerializableTester.reserializeAndAssert(Lists.asList("a", new String[] {"b", "c"}));
    SerializableTester.reserializeAndAssert(Lists.asList("a", "b", new String[] {"c"}));
    SerializableTester.reserializeAndAssert(Lists.asList(null, new String[] {null}));
    List<String> transformed = Lists.transform(new ArrayList<>(List.of("a", "b")), Upper.INSTANCE);
    assertThat(SerializableTester.reserialize(transformed)).containsExactly("A", "B");
    List<String> sequentialTransformed =
        Lists.transform(new LinkedList<>(List.of("a", "b")), Upper.INSTANCE);
    assertThat(SerializableTester.reserialize(sequentialTransformed)).containsExactly("A", "B");
    // Like Guava, charactersOf(String) reserializes through ImmutableList.SerializedForm.
    assertThat(SerializableTester.reserialize(Lists.charactersOf("abc")))
        .isEqualTo(Lists.charactersOf("abc"))
        .isInstanceOf(RegularImmutableList.class);
  }

  @Test
  void newCopyOnWriteArrayListFromCollectionAndIterable() {
    assertThat(Lists.newCopyOnWriteArrayList(List.of("a", "b"))).containsExactly("a", "b");
    Iterable<String> iterable = () -> List.of("c", "d").iterator();
    assertThat(Lists.newCopyOnWriteArrayList(iterable)).containsExactly("c", "d");
  }
}
