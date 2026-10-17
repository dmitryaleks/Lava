package com.simplelibs.lava.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Differential tests against Guava 27.0.1 for {@link CacheBuilderSpec} parsing and {@link
 * CacheBuilder#from(String)}: random specifications built from valid and invalid keys, values,
 * units, separators and whitespace (including the Unicode whitespace Guava's {@code
 * CharMatcher.whitespace()} trims and the characters it does not), compared by outcome, exception
 * message, {@code toString}, {@code toParsableString}, equality and the resulting builder.
 */
class CacheBuilderSpecDifferentialTest {

  private static final List<String> KEYS =
      List.of(
          "initialCapacity", "maximumSize", "maximumWeight", "concurrencyLevel", "weakKeys",
          "softValues", "weakValues", "recordStats", "expireAfterAccess", "expireAfterWrite",
          "refreshAfterWrite", "refreshInterval", "unknown", "", "MaximumSize");

  private static final List<String> VALUES =
      List.of("", "0", "1", "10", "-1", "x", "10s", "3m", "2h", "1d", "5S", "7", "10 m", " 4s", "9999999999");

  /** Whitespace Guava trims (space, tab, NBSP, ideographic space) and some it does not. */
  private static final List<String> SPACES =
      List.of("", " ", "\t", "\u00A0", "\u3000", "\u2007", "\u001C", "\u200B");

  private static String randomSpec(Random random) {
    StringBuilder spec = new StringBuilder();
    int pairs = random.nextInt(4);
    for (int i = 0; i < pairs; i++) {
      if (i > 0) {
        spec.append(random.nextInt(10) == 0 ? ",," : ",");
      }
      spec.append(SPACES.get(random.nextInt(SPACES.size())));
      spec.append(KEYS.get(random.nextInt(KEYS.size())));
      spec.append(SPACES.get(random.nextInt(SPACES.size())));
      int shape = random.nextInt(6);
      if (shape > 0) {
        spec.append('=').append(SPACES.get(random.nextInt(SPACES.size())));
        spec.append(VALUES.get(random.nextInt(VALUES.size())));
        if (shape == 5) {
          spec.append("=1");
        }
      }
    }
    if (random.nextInt(15) == 0) {
      spec.append(',');
    }
    return spec.toString();
  }

  private static String outcome(Supplier<Object> call) {
    try {
      return "= " + call.get();
    } catch (RuntimeException e) {
      return "! " + e.getClass().getSimpleName() + ": " + e.getMessage();
    }
  }

  @Test
  void parsingMatchesGuava() {
    Random random = new Random(0x5BEC);
    for (int i = 0; i < 20_000; i++) {
      String spec = randomSpec(random);
      String what = "spec <" + spec + ">";
      assertThat(outcome(() -> CacheBuilderSpec.parse(spec).toParsableString()))
          .as(what)
          .isEqualTo(
              outcome(() -> com.google.common.cache.CacheBuilderSpec.parse(spec).toParsableString()));
      assertThat(outcome(() -> CacheBuilderSpec.parse(spec).toString()))
          .as(what)
          .isEqualTo(outcome(() -> com.google.common.cache.CacheBuilderSpec.parse(spec).toString()));
      assertThat(outcome(() -> CacheBuilder.from(spec).toString()))
          .as(what + " builder")
          .isEqualTo(outcome(() -> com.google.common.cache.CacheBuilder.from(spec).toString()));
    }
  }

  @Test
  void equalityMatchesGuava() {
    Random random = new Random(0xE0);
    int compared = 0;
    for (int i = 0; i < 20_000; i++) {
      String a = randomSpec(random);
      String b = random.nextBoolean() ? randomSpec(random) : a.replace("1m", "60s");
      CacheBuilderSpec la;
      CacheBuilderSpec lb;
      com.google.common.cache.CacheBuilderSpec ga;
      com.google.common.cache.CacheBuilderSpec gb;
      try {
        ga = com.google.common.cache.CacheBuilderSpec.parse(a);
        gb = com.google.common.cache.CacheBuilderSpec.parse(b);
      } catch (IllegalArgumentException e) {
        continue;
      }
      la = CacheBuilderSpec.parse(a);
      lb = CacheBuilderSpec.parse(b);
      compared++;
      assertThat(la.equals(lb)).as(a + " vs " + b).isEqualTo(ga.equals(gb));
      if (la.equals(lb)) {
        assertThat(la.hashCode()).as(a + " vs " + b).isEqualTo(lb.hashCode());
      }
    }
    assertThat(compared).isGreaterThan(1000);
    assertThat(CacheBuilderSpec.parse("expireAfterWrite=60s"))
        .isEqualTo(CacheBuilderSpec.parse("expireAfterWrite=1m"));
    assertThat(CacheBuilderSpec.disableCaching()).isEqualTo(CacheBuilderSpec.parse("maximumSize=0"));
  }
}
