package com.simplelibs.lava;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.AnnotatedWildcardType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Verifies that Lava keeps the public interface of Guava 27.0.1 (which is on the test classpath
 * only).
 *
 * <p>For every registered type, the public and protected members of the Guava type are mapped to
 * Lava names ({@code com.google.common.X} to {@code com.simplelibs.lava.X}, Guava's {@code
 * Function}/{@code Supplier}/{@code Predicate} to {@code java.util.function}, checker-qual to Lava)
 * and compared by:
 *
 * <ul>
 *   <li>{@link Method#toGenericString()}: modifiers, type parameters, exact wildcards, return type
 *       and {@code throws} clause;
 *   <li>type-use annotations ({@code @Nullable}, {@code @NonNull}) on the return, parameter and
 *       field types, recursively through type arguments, arrays and wildcards;
 *   <li>varargs, {@code @Deprecated}, and the values of public constants;
 *   <li>the type header (modifiers, kind, type parameters, superclass, interfaces, {@code
 *       @FunctionalInterface}, {@code @Deprecated}) and enum constants.
 * </ul>
 *
 * <p>Nested public/protected types are checked recursively. Every Guava member must exist in Lava
 * and every Lava member must exist in Guava, unless the difference is listed with a reason in
 * {@code api-exclusions.txt}; exclusions that no longer match anything fail the test. Finally, every
 * public top-level Lava type must be registered here, so nothing escapes the comparison.
 */
class ApiParityTest {

  /** Guava type names (relative to {@code com.google.common.} unless they start with "org."). */
  private static final List<String> TYPES =
      List.of(
          "org.checkerframework.checker.nullness.qual.NonNull",
          "org.checkerframework.checker.nullness.qual.Nullable",
          "base.Converter",
          "base.Enums",
          "base.Equivalence",
          "base.Optional",
          "base.Preconditions",
          "base.Ticker",
          "eventbus.AllowConcurrentEvents",
          "eventbus.AsyncEventBus",
          "eventbus.DeadEvent",
          "eventbus.EventBus",
          "eventbus.Subscribe",
          "eventbus.SubscriberExceptionContext",
          "eventbus.SubscriberExceptionHandler",
          "primitives.Doubles",
          "util.concurrent.ExecutionError",
          "util.concurrent.ExecutionList",
          "util.concurrent.Futures",
          "util.concurrent.ListenableFuture",
          "util.concurrent.ListenableFutureTask",
          "util.concurrent.MoreExecutors",
          "util.concurrent.UncheckedExecutionException",
          "collect.ForwardingObject",
          "collect.ImmutableCollection",
          "collect.ImmutableList",
          "collect.ImmutableMap",
          "collect.ImmutableMultimap",
          "collect.ImmutableSet",
          "collect.ImmutableSetMultimap",
          "collect.BiMap",
          "collect.BoundType",
          "collect.HashBiMap",
          "collect.Lists",
          "collect.MapDifference",
          "collect.Maps",
          "collect.Range",
          "collect.SortedMapDifference",
          "collect.Multimap",
          "collect.SetMultimap",
          "collect.UnmodifiableIterator",
          "collect.UnmodifiableListIterator");

  private static String qualify(String name) {
    return name.startsWith("org.") ? name : "com.google.common." + name;
  }

  private static final Pattern GUAVA_FUNCTIONAL =
      Pattern.compile("com\\.google\\.common\\.base\\.(Function|Supplier|Predicate)\\b");

  /** Maps a Guava type or signature string to the Lava equivalent. */
  static String map(String s) {
    s = GUAVA_FUNCTIONAL.matcher(s).replaceAll("java.util.function.$1");
    return s.replace("com.google.common.", "com.simplelibs.lava.")
        .replace(
            "org.checkerframework.checker.nullness.qual.",
            "com.simplelibs.lava.checker.nullness.qual.");
  }

  @TestFactory
  List<DynamicTest> publicApiMatchesGuava() throws Exception {
    Map<String, String> exclusions = loadExclusions();
    Set<String> usedExclusions = new TreeSet<>();
    List<DynamicTest> tests = new ArrayList<>();

    for (String name : TYPES) {
      Class<?> guava = Class.forName(qualify(name));
      List<String> problems = new ArrayList<>();
      compareType(guava, exclusions, usedExclusions, problems);
      tests.add(
          dynamicTest(
              map(guava.getName()),
              () ->
                  assertTrue(
                      problems.isEmpty(),
                      () ->
                          "API differences (add to api-exclusions.txt if intentional):\n  "
                              + String.join("\n  ", problems))));
    }

    tests.add(
        dynamicTest(
            "no stale exclusions",
            () -> {
              List<String> stale =
                  exclusions.keySet().stream()
                      .filter(id -> !usedExclusions.contains(id))
                      .collect(Collectors.toList());
              assertTrue(
                  stale.isEmpty(), () -> "Stale exclusions:\n  " + String.join("\n  ", stale));
            }));

    tests.add(
        dynamicTest(
            "every public Lava type is registered",
            () -> {
              Set<String> registered =
                  TYPES.stream().map(n -> map(qualify(n))).collect(Collectors.toSet());
              List<String> unregistered =
                  publicLavaTopLevelTypes().stream()
                      .filter(n -> !registered.contains(n))
                      .collect(Collectors.toList());
              assertTrue(
                  unregistered.isEmpty(),
                  () ->
                      "Public Lava types missing from ApiParityTest.TYPES:\n  "
                          + String.join("\n  ", unregistered));
            }));
    return tests;
  }

  private static void compareType(
      Class<?> guava, Map<String, String> exclusions, Set<String> used, List<String> problems) {
    String lavaName = map(guava.getName());
    if (exclusions.containsKey(lavaName)) {
      used.add(lavaName);
      return; // whole type intentionally omitted or differing
    }
    Class<?> lava;
    try {
      lava = Class.forName(lavaName);
    } catch (ClassNotFoundException ex) {
      problems.add("MISSING TYPE " + lavaName);
      return;
    }

    Map<String, String> g = members(guava, true);
    Map<String, String> l = members(lava, false);
    Set<String> ids = new TreeSet<>(g.keySet());
    ids.addAll(l.keySet());
    for (String id : ids) {
      String gs = g.get(id);
      String ls = l.get(id);
      if (gs != null && gs.equals(ls)) {
        continue;
      }
      if (exclusions.containsKey(id)) {
        used.add(id);
        continue;
      }
      if (gs == null) {
        problems.add("EXTRA    " + id + "\n             lava:  " + ls);
      } else if (ls == null) {
        problems.add("MISSING  " + id + "\n             guava: " + gs);
      } else {
        problems.add(
            "MISMATCH " + id + "\n             guava: " + gs + "\n             lava:  " + ls);
      }
    }

    Set<String> guavaNested = new TreeSet<>();
    for (Class<?> nested : guava.getDeclaredClasses()) {
      if (isApi(nested.getModifiers())) {
        guavaNested.add(map(nested.getName()));
        compareType(nested, exclusions, used, problems);
      }
    }
    for (Class<?> nested : lava.getDeclaredClasses()) {
      if (isApi(nested.getModifiers()) && !guavaNested.contains(nested.getName())) {
        if (exclusions.containsKey(nested.getName())) {
          used.add(nested.getName());
        } else {
          problems.add("EXTRA    nested type " + nested.getName());
        }
      }
    }
  }

  private static boolean isApi(int modifiers) {
    return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
  }

  /** Returns member id to normalized signature for the API members of {@code c}. */
  private static Map<String, String> members(Class<?> c, boolean mapNames) {
    Map<String, String> result = new TreeMap<>();
    String typeName = map(c.getName());
    result.put(typeName + "#<type>", norm(header(c, mapNames), mapNames));
    if (c.isEnum()) {
      result.put(
          typeName + "#<constants>",
          Arrays.stream(c.getEnumConstants())
              .map(Object::toString)
              .collect(Collectors.joining(",")));
    }
    for (Constructor<?> ctor : c.getDeclaredConstructors()) {
      if (isApi(ctor.getModifiers()) && !ctor.isSynthetic()) {
        result.put(id(typeName, "<init>", ctor, mapNames), norm(signature(ctor), mapNames));
      }
    }
    for (Method m : c.getDeclaredMethods()) {
      if (isApi(m.getModifiers()) && !m.isSynthetic() && !m.isBridge()) {
        result.put(id(typeName, m.getName(), m, mapNames), norm(signature(m), mapNames));
      }
    }
    for (Field f : c.getDeclaredFields()) {
      if (isApi(f.getModifiers()) && !f.isSynthetic() && !f.isEnumConstant()) {
        result.put(typeName + "#" + f.getName(), norm(signature(f), mapNames));
      }
    }
    return result;
  }

  private static String signature(Executable e) {
    StringBuilder sb = new StringBuilder(e.toGenericString());
    String ret = annotations(e.getAnnotatedReturnType());
    List<String> params = new ArrayList<>();
    for (AnnotatedType p : e.getAnnotatedParameterTypes()) {
      params.add(annotations(p));
    }
    if (!ret.isEmpty() || params.stream().anyMatch(s -> !s.isEmpty())) {
      sb.append(" | types: ").append(ret).append(" (").append(String.join(",", params)).append(')');
    }
    if (e.isVarArgs()) {
      sb.append(" | varargs");
    }
    appendDeprecated(sb, e);
    return sb.toString();
  }

  private static String signature(Field f) {
    StringBuilder sb = new StringBuilder(f.toGenericString());
    String ann = annotations(f.getAnnotatedType());
    if (!ann.isEmpty()) {
      sb.append(" | types: ").append(ann);
    }
    if (Modifier.isStatic(f.getModifiers())
        && Modifier.isFinal(f.getModifiers())
        && (f.getType().isPrimitive() || f.getType() == String.class)) {
      try {
        sb.append(" = ").append(f.get(null));
      } catch (IllegalAccessException ex) {
        throw new AssertionError(ex);
      }
    }
    appendDeprecated(sb, f);
    return sb.toString();
  }

  private static void appendDeprecated(StringBuilder sb, AnnotatedElement e) {
    if (e.isAnnotationPresent(Deprecated.class)) {
      sb.append(" | @Deprecated");
    }
  }

  /** Renders the type-use annotations of {@code t}, recursively; empty if there are none. */
  private static String annotations(AnnotatedType t) {
    StringBuilder sb = new StringBuilder();
    for (Annotation a : t.getAnnotations()) {
      sb.append('@').append(a.annotationType().getSimpleName());
    }
    List<String> nested = new ArrayList<>();
    if (t instanceof AnnotatedParameterizedType) {
      for (AnnotatedType arg : ((AnnotatedParameterizedType) t).getAnnotatedActualTypeArguments()) {
        nested.add(annotations(arg));
      }
    } else if (t instanceof AnnotatedArrayType) {
      nested.add(annotations(((AnnotatedArrayType) t).getAnnotatedGenericComponentType()));
    } else if (t instanceof AnnotatedWildcardType) {
      AnnotatedWildcardType w = (AnnotatedWildcardType) t;
      for (AnnotatedType b : w.getAnnotatedUpperBounds()) {
        nested.add(annotations(b));
      }
      for (AnnotatedType b : w.getAnnotatedLowerBounds()) {
        nested.add(annotations(b));
      }
    }
    if (nested.stream().anyMatch(s -> !s.isEmpty())) {
      sb.append('<').append(String.join(",", nested)).append('>');
    }
    return sb.toString();
  }

  private static String id(String typeName, String name, Executable e, boolean mapNames) {
    String params =
        Arrays.stream(e.getParameterTypes())
            .map(Class::getTypeName)
            .map(s -> mapNames ? map(s) : s)
            .collect(Collectors.joining(","));
    return typeName + "#" + name + "(" + params + ")";
  }

  private static String norm(String s, boolean mapNames) {
    return mapNames ? map(s) : s;
  }

  private static String header(Class<?> c, boolean mapNames) {
    StringBuilder sb = new StringBuilder();
    int mods = c.getModifiers() & ~Modifier.SYNCHRONIZED;
    if (c.isInterface()) {
      mods &= ~Modifier.ABSTRACT;
    }
    if (c.isAnnotationPresent(FunctionalInterface.class)) {
      sb.append("@FunctionalInterface ");
    }
    if (c.isAnnotationPresent(Deprecated.class)) {
      sb.append("@Deprecated ");
    }
    if (c.isAnnotation()) {
      java.lang.annotation.Retention retention = c.getAnnotation(java.lang.annotation.Retention.class);
      java.lang.annotation.Target target = c.getAnnotation(java.lang.annotation.Target.class);
      sb.append("@Retention(").append(retention == null ? "CLASS" : retention.value()).append(") ");
      if (target != null) {
        sb.append("@Target(")
            .append(Arrays.stream(target.value()).map(Enum::name).sorted().collect(Collectors.joining(",")))
            .append(") ");
      }
      if (c.isAnnotationPresent(java.lang.annotation.Documented.class)) {
        sb.append("@Documented ");
      }
      if (c.isAnnotationPresent(java.lang.annotation.Inherited.class)) {
        sb.append("@Inherited ");
      }
    }
    sb.append(Modifier.toString(mods)).append(' ');
    sb.append(
        c.isAnnotation()
            ? "@interface"
            : c.isInterface() ? "interface" : c.isEnum() ? "enum" : "class");
    sb.append(' ').append(c.getName());
    TypeVariable<?>[] vars = c.getTypeParameters();
    if (vars.length > 0) {
      sb.append(
          Arrays.stream(vars)
              .map(ApiParityTest::typeVar)
              .collect(Collectors.joining(",", "<", ">")));
    }
    Type sup = c.getGenericSuperclass();
    if (sup != null && sup != Object.class && !c.isEnum()) {
      sb.append(" extends ").append(sup.getTypeName());
    }
    Type[] ifaces = c.getGenericInterfaces();
    if (ifaces.length > 0) {
      Set<String> names = new LinkedHashSet<>();
      for (Type t : ifaces) {
        // Map before sorting, so the order does not depend on Guava's package names.
        names.add(mapNames ? map(t.getTypeName()) : t.getTypeName());
      }
      sb.append(c.isInterface() ? " extends " : " implements ")
          .append(String.join(",", new TreeSet<>(names)));
    }
    return sb.toString().trim();
  }

  private static String typeVar(TypeVariable<?> v) {
    Type[] bounds = v.getBounds();
    if (bounds.length == 1 && bounds[0] == Object.class) {
      return v.getName();
    }
    return v.getName()
        + " extends "
        + Arrays.stream(bounds).map(Type::getTypeName).collect(Collectors.joining(" & "));
  }

  /** All public top-level types under {@code com.simplelibs.lava} in the main output. */
  private static List<String> publicLavaTopLevelTypes() throws IOException, URISyntaxException {
    Path root =
        Paths.get(
            com.simplelibs.lava.base.Converter.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
    List<String> result = new ArrayList<>();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path p : (Iterable<Path>) files::iterator) {
        String rel = root.relativize(p).toString().replace('\\', '/');
        if (!rel.endsWith(".class") || rel.contains("$") || rel.endsWith("package-info.class")) {
          continue;
        }
        String className = rel.substring(0, rel.length() - ".class".length()).replace('/', '.');
        try {
          Class<?> c = Class.forName(className, false, ApiParityTest.class.getClassLoader());
          if (Modifier.isPublic(c.getModifiers())) {
            result.add(className);
          }
        } catch (ClassNotFoundException e) {
          throw new AssertionError(e);
        }
      }
    }
    result.sort(null);
    return result;
  }

  /** Lines: {@code <member id> -- <reason>}; blank lines and {@code //} comments are ignored. */
  private static Map<String, String> loadExclusions() throws IOException {
    Map<String, String> result = new LinkedHashMap<>();
    try (InputStream in = ApiParityTest.class.getResourceAsStream("/api-exclusions.txt")) {
      if (in == null) {
        return result;
      }
      BufferedReader reader =
          new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("//")) {
          continue;
        }
        int sep = line.indexOf(" -- ");
        if (sep < 0) {
          throw new IllegalArgumentException("Exclusion without ' -- reason': " + line);
        }
        result.put(line.substring(0, sep).trim(), line.substring(sep + 4).trim());
      }
    }
    return result;
  }
}
