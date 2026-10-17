package com.simplelibs.lava.primitives;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.simplelibs.lava.testing.ListContract;
import com.simplelibs.lava.testing.SerializableTester;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.RandomAccess;
import java.util.Spliterator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Replaces Guava's {@code DoubleArrayAsListTest} (a guava-testlib {@code ListTestSuiteBuilder}
 * suite) with hand-written contract checks for {@link Doubles#asList} and its sub-lists.
 */
class DoubleArrayAsListTest {

  private static double[] array(int size) {
    double[] array = new double[size];
    for (int i = 0; i < size; i++) {
      array[i] = (i % 2 == 0 ? 1.5 : -2.25) * (i + 1); // distinct, no NaN or signed zero
    }
    return array;
  }

  private static List<Double> boxed(double[] array) {
    List<Double> list = new ArrayList<>();
    for (double d : array) {
      list.add(d);
    }
    return list;
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3, 5})
  void readContract(int size) {
    double[] array = array(size);
    List<Double> list = Doubles.asList(array);
    ListContract.assertReadContract(list, boxed(array));
    assertThat(list).isInstanceOf(RandomAccess.class);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 3})
  void structuralMutatorsUnsupported(int size) {
    List<Double> list = Doubles.asList(array(size));
    List<Double> before = new ArrayList<>(list);
    assertThatThrownBy(() -> list.add(9.0)).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> list.add(0, 9.0)).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> list.remove(0)).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(list::clear).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> list.addAll(List.of(1.0))).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> list.removeIf(x -> true)).isInstanceOf(UnsupportedOperationException.class);
    assertThat(list).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 4})
  void setWritesThroughToArrayAndSubLists(int size) {
    double[] array = array(size);
    List<Double> list = Doubles.asList(array);
    assertThat(list.set(0, 42.0)).isEqualTo(boxed(array(size)).get(0));
    assertThat(array[0]).isEqualTo(42.0);
    if (size > 1) {
      List<Double> tail = list.subList(1, size);
      tail.set(0, 7.0);
      assertThat(array[1]).isEqualTo(7.0);
      assertThat(list.get(1)).isEqualTo(7.0);
    }
    array[size - 1] = -1.0;
    assertThat(list.get(size - 1)).isEqualTo(-1.0);
    assertThatThrownBy(() -> list.set(0, null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> list.set(size, 1.0)).isInstanceOf(IndexOutOfBoundsException.class);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 3})
  void serializationIncludingSubLists(int size) {
    List<Double> list = Doubles.asList(array(size));
    SerializableTester.reserializeAndAssert(list);
    SerializableTester.reserializeAndAssert(list.subList(0, size));
    if (size > 1) {
      SerializableTester.reserializeAndAssert(list.subList(1, size));
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 3})
  void spliteratorIsSizedButNotOrdered(int size) {
    Spliterator<Double> spliterator = Doubles.asList(array(size)).spliterator();
    assertThat(spliterator.hasCharacteristics(Spliterator.SIZED)).isTrue();
    assertThat(spliterator.hasCharacteristics(Spliterator.SUBSIZED)).isTrue();
    assertThat(spliterator.characteristics())
        .isEqualTo(Spliterator.SIZED | Spliterator.SUBSIZED);
  }

  @ParameterizedTest
  @ValueSource(ints = {2, 4})
  void emptySubListIsEmptyList(int size) {
    List<Double> list = Doubles.asList(array(size));
    assertThat(list.subList(1, 1)).isSameAs(java.util.Collections.emptyList());
    assertThat(Arrays.asList(Doubles.toArray(list.subList(0, size)))).hasSize(1);
  }
}
