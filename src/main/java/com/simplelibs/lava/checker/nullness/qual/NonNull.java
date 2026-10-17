/*
 * Derived from the Checker Framework's checker-qual 2.5.2
 * (org/checkerframework/checker/nullness/qual/NonNull.java), MIT License.
 * Copyright (c) 2004-present by the Checker Framework developers.
 *
 * Modified for Lava: relocated, and the Checker Framework type-system meta-annotations
 * (SubtypeOf, ImplicitFor, DefaultQualifierInHierarchy, DefaultFor, DefaultInUncheckedCodeFor)
 * were removed because they are only meaningful to the Checker Framework's type checker.
 */
package com.simplelibs.lava.checker.nullness.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a variable, field, return value or type argument will never hold the value
 * {@code null}.
 *
 * <p>This is a documentation-only marker with the same retention and targets as the Checker
 * Framework annotation it replaces. Lava's API treats references as non-null unless they are
 * annotated {@link Nullable}, so {@code NonNull} is rarely written explicitly; it is mainly useful
 * to override a {@code @Nullable} type variable at a use site, for example {@code @NonNull T}.
 *
 * <p>Because the annotation targets {@link ElementType#TYPE_USE}, it is visible at run time through
 * the {@link java.lang.reflect.AnnotatedType} APIs (for example {@code
 * Method.getAnnotatedReturnType()}), not through {@code Method.getAnnotations()}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface NonNull {}
