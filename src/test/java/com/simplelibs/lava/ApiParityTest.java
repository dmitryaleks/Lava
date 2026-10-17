package com.simplelibs.lava;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Verifies that Lava keeps the public interface of Guava 27.0.1 (which is on the test classpath
 * only).
 *
 * <p>For every registered type, the public and protected members of the Guava type are mapped to
 * Lava names ({@code com.google.common.X} to {@code com.simplelibs.lava.X}, Guava's {@code
 * Function}/{@code Supplier}/{@code Predicate} to {@code java.util.function}, checker-qual to Lava)
 * and compared by {@link Method#toGenericString()} (modifiers, type parameters, exact wildcards,
 * return type and {@code throws} clause). The type header (modifiers, kind, type parameters,
 * superclass and interfaces) and the set of public/protected nested types are compared too, and
 * nested types are checked recursively.
 *
 * <p>{@link Mode#FULL} types must match Guava member-for-member. {@link Mode#SUBSET} types may omit
 * Guava members but must not add or change any. Intentional differences are listed with a reason
 * in {@code api-exclusions.txt}; exclusions that no longer match anything fail the test.
 */
class ApiParityTest {

  enum Mode {
    FULL,
    SUBSET
  }

  /** Guava type names (without the {@code com.google.common.} prefix unless noted) to check. */
  private static final Map<String, Mode> TYPES = new LinkedHashMap<>();

  static {
    full("org.checkerframework.checker.nullness.qual.NonNull");
    full("org.checkerframework.checker.nullness.qual.Nullable");
    full("base.Converter");
    full("base.Enums");
    full("base.Equivalence");
    full("base.Optional");
    full("base.Preconditions");
    full("base.Ticker");
    full("util.concurrent.ExecutionError");
    full("util.concurrent.ExecutionList");
    full("util.concurrent.ListenableFuture");
    full("util.concurrent.ListenableFutureTask");
    full("util.concurrent.UncheckedExecutionException");
    full("primitives.Doubles");
    subset("util.concurrent.Futures");
    subset("util.concurrent.MoreExecutors");
  }

  private static void full(String name) {
    TYPES.put(qualify(name), Mode.FULL);
  }

  private static void subset(String name) {
    TYPES.put(qualify(name), Mode.SUBSET);
  }

  private static String qualify(String name) {
    return name.startsWith("org.") ? name : "com.google.common." + name;
  }

  /** Maps a Guava type or signature string to the Lava equivalent. */
  static String map(String s) {
    return s.replace("com.google.common.base.Function", "java.util.function.Function")
        .replace("com.google.common.base.Supplier", "java.util.function.Supplier")
        .replace("com.google.common.base.Predicate", "java.util.function.Predicate")
        .replace("com.google.common.", "com.simplelibs.lava.")
        .replace("org.checkerframework.checker.nullness.qual.", "com.simplelibs.lava.checker.nullness.qual.");
  }

  @TestFactory
  List<DynamicTest> publicApiMatchesGuava() throws Exception {
    Map<String, String> exclusions = loadExclusions();
    Set<String> usedExclusions = new TreeSet<>();
    Set<String> checkedTypes = new TreeSet<>();
    List<DynamicTest> tests = new ArrayList<>();

    for (Map.Entry<String, Mode> e : TYPES.entrySet()) {
      Class<?> guava = Class.forName(e.getKey());
      List<String> problems = new ArrayList<>();
      compareType(guava, e.getValue(), exclusions, usedExclusions, checkedTypes, problems);
      tests.add(
          dynamicTest(
              map(e.getKey()),
              () ->
                  assertTrue(
                      problems.isEmpty(),
                      () -> "API differences (add to api-exclusions.txt if intentional):\n  "
                          + String.join("\n  ", problems))));
    }

    tests.add(
        dynamicTest(
            "no stale exclusions",
            () -> {
              List<String> stale =
                  exclusions.keySet().stream()
                      .filter(id -> checkedTypes.contains(typeOf(id)))
                      .filter(id -> !usedExclusions.contains(id))
                      .collect(Collectors.toList());
              assertTrue(stale.isEmpty(), () -> "Stale exclusions:\n  " + String.join("\n  ", stale));
            }));
    return tests;
  }

  private static String typeOf(String id) {
    int hash = id.indexOf('#');
    return hash < 0 ? id : id.substring(0, hash);
  }

  private static void compareType(
      Class<?> guava,
      Mode mode,
      Map<String, String> exclusions,
      Set<String> used,
      Set<String> checkedTypes,
      List<String> problems) {
    String lavaName = map(guava.getName());
    checkedTypes.add(lavaName);
    if (exclusions.containsKey(lavaName)) {
      used.add(lavaName);
      return; // whole type intentionally omitted
    }
    Class<?> lava;
    try {
      lava = Class.forName(lavaName);
    } catch (ClassNotFoundException ex) {
      if (mode == Mode.FULL || guava.getEnclosingClass() == null) {
        problems.add("MISSING TYPE " + lavaName);
      }
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
        if (mode == Mode.FULL) {
          problems.add("MISSING  " + id + "\n             guava: " + gs);
        }
      } else {
        problems.add("MISMATCH " + id + "\n             guava: " + gs + "\n             lava:  " + ls);
      }
    }

    for (Class<?> nested : guava.getDeclaredClasses()) {
      if (isApi(nested.getModifiers())) {
        compareType(nested, mode, exclusions, used, checkedTypes, problems);
      }
    }
    Set<String> guavaNested =
        Arrays.stream(guava.getDeclaredClasses())
            .filter(c -> isApi(c.getModifiers()))
            .map(c -> map(c.getName()))
            .collect(Collectors.toSet());
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
    result.put(typeName + "#<type>", norm(header(c), mapNames));
    if (c.isEnum()) {
      result.put(
          typeName + "#<constants>",
          Arrays.stream(c.getEnumConstants()).map(Object::toString).collect(Collectors.joining(",")));
    }
    for (Constructor<?> ctor : c.getDeclaredConstructors()) {
      if (isApi(ctor.getModifiers()) && !ctor.isSynthetic()) {
        result.put(id(typeName, "<init>", ctor, mapNames), norm(ctor.toGenericString(), mapNames));
      }
    }
    for (Method m : c.getDeclaredMethods()) {
      if (isApi(m.getModifiers()) && !m.isSynthetic() && !m.isBridge()) {
        result.put(id(typeName, m.getName(), m, mapNames), norm(m.toGenericString(), mapNames));
      }
    }
    for (Field f : c.getDeclaredFields()) {
      if (isApi(f.getModifiers()) && !f.isSynthetic() && !f.isEnumConstant()) {
        result.put(typeName + "#" + f.getName(), norm(f.toGenericString(), mapNames));
      }
    }
    return result;
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

  private static String header(Class<?> c) {
    StringBuilder sb = new StringBuilder();
    int mods = c.getModifiers() & ~Modifier.SYNCHRONIZED;
    if (c.isInterface()) {
      mods &= ~Modifier.ABSTRACT;
    }
    sb.append(Modifier.toString(mods)).append(' ');
    sb.append(c.isAnnotation() ? "@interface" : c.isInterface() ? "interface" : c.isEnum() ? "enum" : "class");
    sb.append(' ').append(c.getName());
    TypeVariable<?>[] vars = c.getTypeParameters();
    if (vars.length > 0) {
      sb.append(
          Arrays.stream(vars).map(ApiParityTest::typeVar).collect(Collectors.joining(",", "<", ">")));
    }
    Type sup = c.getGenericSuperclass();
    if (sup != null && sup != Object.class && !c.isEnum()) {
      sb.append(" extends ").append(sup.getTypeName());
    }
    Type[] ifaces = c.getGenericInterfaces();
    if (ifaces.length > 0) {
      Set<String> names = new LinkedHashSet<>();
      for (Type t : ifaces) {
        names.add(t.getTypeName());
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

  /** Lines: {@code <member id> -- <reason>}; blank lines and {@code //} comments are ignored. */
  private static Map<String, String> loadExclusions() throws IOException {
    Map<String, String> result = new LinkedHashMap<>();
    try (InputStream in = ApiParityTest.class.getResourceAsStream("/api-exclusions.txt")) {
      if (in == null) {
        return result;
      }
      BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
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
