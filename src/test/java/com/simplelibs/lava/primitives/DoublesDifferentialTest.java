package com.simplelibs.lava.primitives;

import static org.assertj.core.api.Assertions.assertThat;

import com.simplelibs.lava.testing.ExceptionMessages;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

/** Differential tests: Lava's {@link Doubles} against the real Guava 27.0.1 implementation. */
class DoublesDifferentialTest {
  private static final long SEED = 0x1A7AL;
  private static final int ROUNDS = 2_000;

  private static final double[] SPECIALS = {
    0.0, -0.0, 1.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
    Double.MIN_VALUE, -Double.MIN_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, Double.MIN_NORMAL,
    Double.longBitsToDouble(0x7ff8000000000001L) // non-canonical NaN
  };

  private final Random random = new Random(SEED);

  private double randomDouble() {
    switch (random.nextInt(4)) {
      case 0:
        return SPECIALS[random.nextInt(SPECIALS.length)];
      case 1:
        return random.nextInt(7) - 3;
      case 2:
        return Double.longBitsToDouble(random.nextLong());
      default:
        return (random.nextDouble() - 0.5) * 1e6;
    }
  }

  private double[] randomArray(int maxLength) {
    double[] array = new double[random.nextInt(maxLength + 1)];
    for (int i = 0; i < array.length; i++) {
      array[i] = randomDouble();
    }
    return array;
  }

  /** Compares results bit-for-bit, or the exception classes if either side throws. */
  private static void same(String what, Callable<Object> guava, Callable<Object> lava) {
    Object g;
    Object l;
    String gThrown = null;
    String lThrown = null;
    try {
      g = guava.call();
    } catch (Exception e) {
      g = null;
      gThrown = e.getClass().getName() + ": " + ExceptionMessages.of(e);
    }
    try {
      l = lava.call();
    } catch (Exception e) {
      l = null;
      lThrown = e.getClass().getName() + ": " + ExceptionMessages.of(e);
    }
    assertThat(lThrown).as(what + " exception").isEqualTo(gThrown);
    assertThat(normalize(l)).as(what).isEqualTo(normalize(g));
  }

  private static Object normalize(Object o) {
    if (o instanceof Double) {
      return Double.doubleToRawLongBits((Double) o);
    }
    if (o instanceof double[]) {
      double[] a = (double[]) o;
      long[] bits = new long[a.length];
      for (int i = 0; i < a.length; i++) {
        bits[i] = Double.doubleToRawLongBits(a[i]);
      }
      return Arrays.toString(bits);
    }
    return o;
  }

  @Test
  void scalarFunctions() {
    for (int i = 0; i < ROUNDS; i++) {
      double a = randomDouble();
      double b = randomDouble();
      double c = randomDouble();
      same("hashCode", () -> com.google.common.primitives.Doubles.hashCode(a), () -> Doubles.hashCode(a));
      same(
          "compare",
          () -> Integer.signum(com.google.common.primitives.Doubles.compare(a, b)),
          () -> Integer.signum(Doubles.compare(a, b)));
      same("isFinite", () -> com.google.common.primitives.Doubles.isFinite(a), () -> Doubles.isFinite(a));
      same(
          "constrainToRange",
          () -> com.google.common.primitives.Doubles.constrainToRange(a, b, c),
          () -> Doubles.constrainToRange(a, b, c));
    }
  }

  @Test
  void arrayFunctions() {
    for (int i = 0; i < ROUNDS; i++) {
      double[] array = randomArray(8);
      double[] target = randomArray(2);
      double probe = random.nextBoolean() && array.length > 0 ? array[random.nextInt(array.length)] : randomDouble();
      same("contains", () -> com.google.common.primitives.Doubles.contains(array, probe), () -> Doubles.contains(array, probe));
      same("indexOf", () -> com.google.common.primitives.Doubles.indexOf(array, probe), () -> Doubles.indexOf(array, probe));
      same("indexOf[]", () -> com.google.common.primitives.Doubles.indexOf(array, target), () -> Doubles.indexOf(array, target));
      same("lastIndexOf", () -> com.google.common.primitives.Doubles.lastIndexOf(array, probe), () -> Doubles.lastIndexOf(array, probe));
      same("min", () -> com.google.common.primitives.Doubles.min(array), () -> Doubles.min(array));
      same("max", () -> com.google.common.primitives.Doubles.max(array), () -> Doubles.max(array));
      same("concat", () -> com.google.common.primitives.Doubles.concat(array, target, array), () -> Doubles.concat(array, target, array));
      same("join", () -> com.google.common.primitives.Doubles.join("|", array), () -> Doubles.join("|", array));
      int minLength = random.nextInt(12) - 1;
      int padding = random.nextInt(4) - 1;
      same(
          "ensureCapacity",
          () -> com.google.common.primitives.Doubles.ensureCapacity(array.clone(), minLength, padding),
          () -> Doubles.ensureCapacity(array.clone(), minLength, padding));
      same(
          "lexicographicalComparator",
          () -> Integer.signum(com.google.common.primitives.Doubles.lexicographicalComparator().compare(array, target)),
          () -> Integer.signum(Doubles.lexicographicalComparator().compare(array, target)));
      int from = random.nextInt(array.length + 3) - 1;
      int to = random.nextInt(array.length + 3) - 1;
      same(
          "sortDescending(range)",
          () -> {
            double[] copy = array.clone();
            com.google.common.primitives.Doubles.sortDescending(copy, from, to);
            return copy;
          },
          () -> {
            double[] copy = array.clone();
            Doubles.sortDescending(copy, from, to);
            return copy;
          });
      same(
          "sortDescending",
          () -> {
            double[] copy = array.clone();
            com.google.common.primitives.Doubles.sortDescending(copy);
            return copy;
          },
          () -> {
            double[] copy = array.clone();
            Doubles.sortDescending(copy);
            return copy;
          });
      same(
          "reverse",
          () -> {
            double[] copy = array.clone();
            com.google.common.primitives.Doubles.reverse(copy);
            return copy;
          },
          () -> {
            double[] copy = array.clone();
            Doubles.reverse(copy);
            return copy;
          });
      same(
          "reverse(range)",
          () -> {
            double[] copy = array.clone();
            com.google.common.primitives.Doubles.reverse(copy, from, to);
            return copy;
          },
          () -> {
            double[] copy = array.clone();
            Doubles.reverse(copy, from, to);
            return copy;
          });
    }
  }

  @Test
  void asListAndToArray() {
    for (int i = 0; i < ROUNDS; i++) {
      double[] array = randomArray(6);
      double[] other = random.nextBoolean() ? array.clone() : randomArray(6);
      List<Double> guava = com.google.common.primitives.Doubles.asList(array);
      List<Double> lava = Doubles.asList(array);
      List<Double> guavaOther = com.google.common.primitives.Doubles.asList(other);
      List<Double> lavaOther = Doubles.asList(other);
      double probe = randomDouble();
      same("asList.equals", () -> guava.equals(guavaOther), () -> lava.equals(lavaOther));
      same("asList.equals(ArrayList)", () -> guava.equals(new ArrayList<>(guavaOther)), () -> lava.equals(new ArrayList<>(lavaOther)));
      same("asList.hashCode", guava::hashCode, lava::hashCode);
      same("asList.toString", guava::toString, lava::toString);
      same("asList.contains", () -> guava.contains(probe), () -> lava.contains(probe));
      same("asList.indexOf", () -> guava.indexOf(probe), () -> lava.indexOf(probe));
      same("asList.lastIndexOf", () -> guava.lastIndexOf(probe), () -> lava.lastIndexOf(probe));
      same("toArray", () -> com.google.common.primitives.Doubles.toArray(guava), () -> Doubles.toArray(lava));
      if (array.length > 0) {
        int from = random.nextInt(array.length);
        int to = from + random.nextInt(array.length - from + 1);
        same("subList.toString", () -> guava.subList(from, to).toString(), () -> lava.subList(from, to).toString());
        same("subList.toArray", () -> com.google.common.primitives.Doubles.toArray(guava.subList(from, to)), () -> Doubles.toArray(lava.subList(from, to)));
      }
    }
  }

  @Test
  void tryParseAndStringConverter() {
    String[] fragments = {
      "", "+", "-", "0", "1", "9", ".", "e", "E", "x", "X", "p", "P", "f", "d", "F", "D", "a", "c",
      "NaN", "Infinity", " ", "_", "0x", "1.5", "e10", "p-3"
    };
    List<String> inputs = new ArrayList<>();
    for (double d : SPECIALS) {
      inputs.add(Double.toString(d));
      inputs.add(Double.toHexString(d));
    }
    for (int i = 0; i < ROUNDS * 5; i++) {
      StringBuilder sb = new StringBuilder();
      int parts = 1 + random.nextInt(5);
      for (int p = 0; p < parts; p++) {
        sb.append(fragments[random.nextInt(fragments.length)]);
      }
      inputs.add(sb.toString());
      inputs.add(Double.toString(randomDouble()));
    }
    for (String s : inputs) {
      same("tryParse(" + s + ")", () -> com.google.common.primitives.Doubles.tryParse(s), () -> Doubles.tryParse(s));
      same(
          "stringConverter(" + s + ")",
          () -> com.google.common.primitives.Doubles.stringConverter().convert(s),
          () -> Doubles.stringConverter().convert(s));
    }
    for (int i = 0; i < ROUNDS; i++) {
      double d = randomDouble();
      same(
          "stringConverter.reverse",
          () -> com.google.common.primitives.Doubles.stringConverter().reverse().convert(d),
          () -> Doubles.stringConverter().reverse().convert(d));
    }
  }

  @Test
  void constantsAndToArrayOfArbitraryNumbers() {
    assertThat(Doubles.BYTES).isEqualTo(com.google.common.primitives.Doubles.BYTES);
    for (int i = 0; i < ROUNDS; i++) {
      java.util.List<Number> numbers = new ArrayList<>();
      int size = random.nextInt(6);
      for (int j = 0; j < size; j++) {
        switch (random.nextInt(5)) {
          case 0:
            numbers.add(random.nextInt());
            break;
          case 1:
            numbers.add(random.nextLong());
            break;
          case 2:
            numbers.add((float) randomDouble());
            break;
          case 3:
            numbers.add(new java.math.BigDecimal(random.nextInt(1000)).movePointLeft(2));
            break;
          default:
            numbers.add(random.nextInt(20) == 0 ? null : randomDouble());
        }
      }
      same("toArray(" + numbers + ")",
          () -> com.google.common.primitives.Doubles.toArray(numbers),
          () -> Doubles.toArray(numbers));
    }
  }
}
