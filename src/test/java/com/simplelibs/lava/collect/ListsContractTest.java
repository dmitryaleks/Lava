package com.simplelibs.lava.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.simplelibs.lava.testing.ListContract;
import com.simplelibs.lava.testing.SerializableTester;
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
 * Lava replacement for the guava-testlib {@code ListTestSuiteBuilder} suites of {@code ListsTest}:
 * runs the hand-written List contract over every view {@link Lists} returns.
 */
class ListsContractTest {

  private static final List<String> ELEMENTS = List.of("a", "b", "c", "d", "e", "f", "g");

  /** Serializable function, so transformed views can be reserialized. */
  private enum Upper implements Function<String, String> {
    INSTANCE;

    @Override
    public String apply(String s) {
      return s.toUpperCase(java.util.Locale.ROOT);
    }
  }

  private static List<String> upper(List<String> list) {
    List<String> result = new ArrayList<>();
    for (String s : list) {
      result.add(s.toUpperCase(java.util.Locale.ROOT));
    }
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

  @TestFactory
  List<DynamicTest> views() {
    List<DynamicTest> tests = new ArrayList<>();
    for (int n = 0; n <= ELEMENTS.size(); n++) {
      List<String> expected = ELEMENTS.subList(0, n);
      int size = n;
      if (n >= 1) {
        String[] rest = expected.subList(1, n).toArray(new String[0]);
        readOnlyCase(tests, "asList(first, rest) " + n, () -> Lists.asList(expected.get(0), rest), expected);
      }
      if (n >= 2) {
        String[] rest = expected.subList(2, n).toArray(new String[0]);
        readOnlyCase(
            tests, "asList(first, second, rest) " + n, () -> Lists.asList(expected.get(0), expected.get(1), rest), expected);
      }
      readCase(tests, "transform(ArrayList) " + n, () -> Lists.transform(new ArrayList<>(expected), Upper.INSTANCE), upper(expected));
      readCase(tests, "transform(LinkedList) " + n, () -> Lists.transform(new LinkedList<>(expected), Upper.INSTANCE), upper(expected));
      List<String> reversed = new ArrayList<>(expected);
      Collections.reverse(reversed);
      readCase(tests, "reverse(ArrayList) " + n, () -> Lists.reverse(new ArrayList<>(expected)), reversed);
      readCase(tests, "reverse(LinkedList) " + n, () -> Lists.reverse(new LinkedList<>(expected)), reversed);
      readCase(tests, "reverse(reverse(ArrayList)) " + n, () -> Lists.reverse(Lists.reverse(new ArrayList<>(expected))), expected);
      readOnlyCase(tests, "reverse(ImmutableList) " + n, () -> Lists.reverse(ImmutableList.copyOf(expected)), reversed);
      readOnlyCase(tests, "charactersOf(String) " + n, () -> charsAsStrings(Lists.charactersOf(String.join("", expected))), expected);
      for (int chunk = 1; chunk <= 3 && n > 0; chunk++) {
        int c = chunk;
        for (int i = 0; i * c < size; i++) {
          int from = i * c;
          int to = Math.min(from + c, size);
          int index = i;
          readCase(
              tests,
              "partition(" + n + ", " + c + ").get(" + i + ")",
              () -> Lists.partition(new ArrayList<>(expected), c).get(index),
              expected.subList(from, to));
          readCase(
              tests,
              "partition(LinkedList " + n + ", " + c + ").get(" + i + ")",
              () -> Lists.partition(new LinkedList<>(expected), c).get(index),
              expected.subList(from, to));
        }
      }
    }
    return tests;
  }

  /** Adapts a List<Character> to List<String> through a copy, for the String-based contract. */
  private static List<String> charsAsStrings(List<Character> chars) {
    // charactersOf(String) is an ImmutableList; check its own contract on Characters too.
    List<Character> expected = new ArrayList<>(chars);
    ListContract.assertReadContract(chars, expected, '?');
    ListContract.assertAlwaysThrowsUnsupported(chars, '?');
    List<String> result = new ArrayList<>();
    for (char c : chars) {
      result.add(String.valueOf(c));
    }
    return Collections.unmodifiableList(result);
  }

  @Test
  void charactersOfCharSequence_isLiveViewNotRandomAccess() {
    StringBuilder builder = new StringBuilder("abc");
    List<Character> chars = Lists.charactersOf(builder);
    ListContract.assertReadContract(chars, List.of('a', 'b', 'c'), 'z');
    ListContract.assertUnmodifiable(chars, 'z');
    builder.append('d');
    assertThat(chars).containsExactly('a', 'b', 'c', 'd');
    assertThat(chars).isNotInstanceOf(RandomAccess.class);
    assertThat(Lists.charactersOf("abc")).isInstanceOf(ImmutableList.class);
    assertThat(Lists.charactersOf("abc").subList(1, 3)).containsExactly('b', 'c');
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
    }
    assertThat(product).isInstanceOf(RandomAccess.class);
    assertThat(Lists.cartesianProduct()).containsExactly(List.of());
    assertThat(Lists.cartesianProduct(List.of("a"), List.of())).isEmpty();
    assertThat(product.indexOf(List.of("b", "y", "1"))).isEqualTo(4);
    assertThat(product.indexOf(List.of("b", "y"))).isEqualTo(-1);
    assertThat(product.contains(List.of("b", "q", "1"))).isFalse();
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
    assertThatThrownBy(() -> transformed.add("D")).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> transformed.set(0, "D")).isInstanceOf(UnsupportedOperationException.class);
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
    assertThatThrownBy(() -> parts.add(List.of())).isInstanceOf(UnsupportedOperationException.class);
    assertThat(Lists.partition(new LinkedList<>(letters), 2)).isNotInstanceOf(RandomAccess.class);
    assertThat(Lists.partition(letters, 2)).isInstanceOf(RandomAccess.class);
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
  }

  @Test
  void serializationOfSerializableViews() {
    List<String> asList = Lists.asList("a", new String[] {"b", "c"});
    SerializableTester.reserializeAndAssert(asList);
    SerializableTester.reserializeAndAssert(Lists.asList("a", "b", new String[] {"c"}));
    List<String> transformed = Lists.transform(new ArrayList<>(List.of("a", "b")), Upper.INSTANCE);
    assertThat(SerializableTester.reserialize(transformed)).containsExactly("A", "B");
    List<String> sequentialTransformed =
        Lists.transform(new LinkedList<>(List.of("a", "b")), Upper.INSTANCE);
    assertThat(SerializableTester.reserialize(sequentialTransformed)).containsExactly("A", "B");
    // Like Guava, charactersOf(String) reserializes through ImmutableList.SerializedForm.
    assertThat(SerializableTester.reserialize(Lists.charactersOf("abc"))).isEqualTo(Lists.charactersOf("abc"));
    assertThat(Arrays.asList(Lists.newArrayList("a", "b").toArray())).containsExactly("a", "b");
  }
}
