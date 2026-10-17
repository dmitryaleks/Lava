package com.simplelibs.lava.checker.nullness.qual;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class NullnessAnnotationsTest {

  @Test
  void nonNullMatchesCheckerFrameworkMetaAnnotations() {
    assertMatchesChecker(NonNull.class, org.checkerframework.checker.nullness.qual.NonNull.class);
  }

  @Test
  void nullableMatchesCheckerFrameworkMetaAnnotations() {
    assertMatchesChecker(Nullable.class, org.checkerframework.checker.nullness.qual.Nullable.class);
  }

  private static void assertMatchesChecker(Class<?> lava, Class<?> checker) {
    assertThat(lava.isAnnotation()).isTrue();
    assertThat(lava.getDeclaredMethods()).isEmpty();
    assertThat(lava.isAnnotationPresent(Documented.class))
        .isEqualTo(checker.isAnnotationPresent(Documented.class));
    assertThat(lava.getAnnotation(Retention.class).value())
        .isEqualTo(checker.getAnnotation(Retention.class).value())
        .isEqualTo(RetentionPolicy.RUNTIME);
    assertThat(lava.getAnnotation(Target.class).value())
        .containsExactlyInAnyOrder(checker.getAnnotation(Target.class).value())
        .containsExactlyInAnyOrder(ElementType.TYPE_USE, ElementType.TYPE_PARAMETER);
  }

  @SuppressWarnings("unused")
  private static @Nullable String nullableReturn(@NonNull List<@Nullable String> list) {
    return null;
  }

  @Test
  void annotationsAreVisibleThroughAnnotatedTypes() throws Exception {
    Method m =
        NullnessAnnotationsTest.class.getDeclaredMethod("nullableReturn", List.class);
    assertThat(m.getAnnotatedReturnType().isAnnotationPresent(Nullable.class)).isTrue();
    assertThat(m.getAnnotatedParameterTypes()[0].isAnnotationPresent(NonNull.class)).isTrue();
    // TYPE_USE-only annotations are not declaration annotations.
    assertThat(m.getAnnotation(Nullable.class)).isNull();
  }
}
