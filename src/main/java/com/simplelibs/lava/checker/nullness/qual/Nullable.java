/*
 * Derived from the Checker Framework's checker-qual 2.5.2
 * (org/checkerframework/checker/nullness/qual/Nullable.java), MIT License.
 * Copyright (c) 2004-present by the Checker Framework developers.
 *
 * Modified for Lava: relocated, and the Checker Framework type-system meta-annotations
 * (SubtypeOf, ImplicitFor, DefaultInUncheckedCodeFor) were removed because they are only
 * meaningful to the Checker Framework's type checker.
 */
package com.simplelibs.lava.checker.nullness.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a variable, field, parameter, return value or type argument may hold the value
 * {@code null}.
 *
 * <p>Lava's public API carries this annotation wherever Guava 27.0.1 does. Any reference type that
 * is not annotated {@code @Nullable} is non-null by default.
 *
 * <p>Because the annotation targets {@link ElementType#TYPE_USE}, qualified types are annotated on
 * the simple name, for example {@code java.util.@Nullable Optional<T>}, and the annotation is
 * visible at run time through the {@link java.lang.reflect.AnnotatedType} APIs.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface Nullable {}
