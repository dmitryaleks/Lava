package com.simplelibs.lava.testing;

import static org.junit.jupiter.api.Assertions.fail;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Minimal replacement for guava-testlib's {@code NullPointerTester}.
 *
 * <p>For every tested method or constructor and every reference-typed parameter that is not
 * annotated {@code @Nullable} (as a type-use or declaration annotation with that simple name), the
 * member is invoked with {@code null} in that position and non-null defaults elsewhere. It must
 * throw {@link NullPointerException} or {@link UnsupportedOperationException}.
 */
public final class NullPointerTester {
  private final Map<Class<?>, Object> defaults = new HashMap<>();
  private final Set<String> ignoredNames = new HashSet<>();

  public NullPointerTester() {
    put(String.class, "");
    put(CharSequence.class, "");
    put(Object.class, "");
    put(Comparable.class, "");
    put(Integer.class, 0);
    put(Long.class, 0L);
    put(Double.class, 0.0);
    put(Float.class, 0.0f);
    put(Short.class, (short) 0);
    put(Byte.class, (byte) 0);
    put(Character.class, 'a');
    put(Boolean.class, false);
    put(Number.class, 0);
    put(Class.class, Object.class);
    put(Iterable.class, new ArrayList<>());
    put(Collection.class, new ArrayList<>());
    put(List.class, new ArrayList<>());
    put(ArrayList.class, new ArrayList<>());
    put(Set.class, new HashSet<>());
    put(SortedSet.class, new TreeSet<>());
    put(NavigableSet.class, new TreeSet<>());
    put(Map.class, new HashMap<>());
    put(LinkedHashMap.class, new LinkedHashMap<>());
    put(LinkedHashSet.class, new LinkedHashSet<>());
    put(SortedMap.class, new TreeMap<>());
    put(NavigableMap.class, new TreeMap<>());
    put(ConcurrentMap.class, new ConcurrentHashMap<>());
    put(Queue.class, new java.util.ArrayDeque<>());
    put(Deque.class, new java.util.ArrayDeque<>());
    put(Iterator.class, Collections.emptyIterator());
    put(ListIterator.class, Collections.emptyListIterator());
    put(Comparator.class, Comparator.naturalOrder());
    put(TimeUnit.class, TimeUnit.SECONDS);
    put(Duration.class, Duration.ZERO);
    put(Executor.class, (Executor) Runnable::run);
    put(Runnable.class, (Runnable) () -> {});
    put(Callable.class, (Callable<Object>) () -> "");
    put(Function.class, Function.identity());
    put(UnaryOperator.class, UnaryOperator.identity());
    put(Supplier.class, (Supplier<Object>) () -> "");
    put(Predicate.class, (Predicate<Object>) x -> true);
    put(Consumer.class, (Consumer<Object>) x -> {});
    put(BiConsumer.class, (BiConsumer<Object, Object>) (x, y) -> {});
    put(BiFunction.class, (BiFunction<Object, Object, Object>) (x, y) -> x);
    put(BinaryOperator.class, (BinaryOperator<Object>) (x, y) -> x);
    put(Pattern.class, Pattern.compile(""));
    put(Charset.class, StandardCharsets.UTF_8);
    put(Properties.class, new Properties());
    put(Throwable.class, new RuntimeException());
    put(Exception.class, new RuntimeException());
    put(RuntimeException.class, new RuntimeException());
    put(Error.class, new Error());
    put(java.util.Optional.class, java.util.Optional.empty());
    // Lava types (guava-testlib's ArbitraryInstances provides the Guava equivalents).
    put(com.simplelibs.lava.base.Optional.class, com.simplelibs.lava.base.Optional.absent());
    put(com.simplelibs.lava.base.Converter.class, com.simplelibs.lava.base.Converter.identity());
    put(com.simplelibs.lava.base.Equivalence.class, com.simplelibs.lava.base.Equivalence.equals());
    put(com.simplelibs.lava.base.Ticker.class, com.simplelibs.lava.base.Ticker.systemTicker());
    put(com.simplelibs.lava.collect.ImmutableList.class, com.simplelibs.lava.collect.ImmutableList.of());
    put(com.simplelibs.lava.collect.ImmutableSet.class, com.simplelibs.lava.collect.ImmutableSet.of());
    put(com.simplelibs.lava.collect.ImmutableCollection.class, com.simplelibs.lava.collect.ImmutableList.of());
    put(com.simplelibs.lava.collect.ImmutableMap.class, com.simplelibs.lava.collect.ImmutableMap.of());
    put(com.simplelibs.lava.collect.Range.class, com.simplelibs.lava.collect.Range.all());
    put(com.simplelibs.lava.collect.BiMap.class, com.simplelibs.lava.collect.HashBiMap.create());
    put(com.simplelibs.lava.collect.Multimap.class, com.simplelibs.lava.collect.ImmutableSetMultimap.of());
    put(com.simplelibs.lava.collect.SetMultimap.class, com.simplelibs.lava.collect.ImmutableSetMultimap.of());
  }

  private void put(Class<?> type, Object value) {
    defaults.put(type, value);
  }

  /** Sets the default value used for parameters of type {@code type}. */
  public <T> NullPointerTester setDefault(Class<T> type, T value) {
    defaults.put(type, value);
    return this;
  }

  /** Skips every method with this name. */
  public NullPointerTester ignore(String methodName) {
    ignoredNames.add(methodName);
    return this;
  }

  /** Tests every public static method declared by {@code type}. */
  public void testAllPublicStaticMethods(Class<?> type) {
    for (Method m : type.getDeclaredMethods()) {
      if (Modifier.isPublic(m.getModifiers())
          && Modifier.isStatic(m.getModifiers())
          && !m.isSynthetic()
          && !ignoredNames.contains(m.getName())) {
        testExecutable(null, m);
      }
    }
  }

  /** Tests every public constructor declared by {@code type}. */
  public void testAllPublicConstructors(Class<?> type) {
    for (Constructor<?> c : type.getDeclaredConstructors()) {
      if (Modifier.isPublic(c.getModifiers()) && !c.isSynthetic()) {
        testExecutable(null, c);
      }
    }
  }

  /** Tests every public instance method of {@code instance}'s class not declared by Object. */
  public void testAllPublicInstanceMethods(Object instance) {
    for (Method m : instance.getClass().getMethods()) {
      if (!Modifier.isStatic(m.getModifiers())
          && m.getDeclaringClass() != Object.class
          && !m.isSynthetic()
          && !m.isBridge()
          && !ignoredNames.contains(m.getName())
          && !(m.getName().equals("equals") && m.getParameterCount() == 1)) {
        testExecutable(instance, m);
      }
    }
  }

  private void testExecutable(Object instance, Executable member) {
    Parameter[] params = member.getParameters();
    for (int i = 0; i < params.length; i++) {
      if (params[i].getType().isPrimitive() || isNullable(params[i])) {
        continue;
      }
      Object[] args = new Object[params.length];
      for (int j = 0; j < params.length; j++) {
        args[j] = (j == i) ? null : defaultValue(params[j].getType(), member);
      }
      invokeExpectingNpe(instance, member, i, args);
    }
  }

  private void invokeExpectingNpe(Object instance, Executable member, int index, Object[] args) {
    member.setAccessible(true);
    try {
      if (member instanceof Method) {
        ((Method) member).invoke(instance, args);
      } else {
        ((Constructor<?>) member).newInstance(args);
      }
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof NullPointerException || cause instanceof UnsupportedOperationException) {
        return;
      }
      AssertionError error =
          new AssertionError(
              describe(member, index)
                  + " threw "
                  + cause
                  + " instead of NullPointerException or UnsupportedOperationException");
      error.initCause(cause);
      throw error;
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("Could not invoke " + member, e);
    }
    fail(describe(member, index) + " did not throw NullPointerException");
  }

  private static String describe(Executable member, int index) {
    return "Passing null as parameter #" + index + " of " + member.toGenericString();
  }

  private static boolean isNullable(Parameter p) {
    for (Annotation a : p.getAnnotations()) {
      if (isNullableAnnotation(a)) {
        return true;
      }
    }
    AnnotatedType type = p.getAnnotatedType();
    for (Annotation a : type.getAnnotations()) {
      if (isNullableAnnotation(a)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isNullableAnnotation(Annotation a) {
    String name = a.annotationType().getSimpleName();
    return name.equals("Nullable") || name.equals("CheckForNull");
  }

  private Object defaultValue(Class<?> type, Executable member) {
    if (type.isPrimitive()) {
      return primitiveDefault(type);
    }
    if (defaults.containsKey(type)) {
      return defaults.get(type);
    }
    for (Map.Entry<Class<?>, Object> e : defaults.entrySet()) {
      if (type.isAssignableFrom(e.getKey()) && e.getKey() != Object.class) {
        return e.getValue();
      }
    }
    if (type.isArray()) {
      return Array.newInstance(type.getComponentType(), 0);
    }
    if (type.isEnum()) {
      Object[] constants = type.getEnumConstants();
      return constants.length > 0 ? constants[0] : null;
    }
    if (type.isInterface()) {
      return Proxy.newProxyInstance(
          type.getClassLoader(),
          new Class<?>[] {type},
          (proxy, method, args) -> {
            if (method.getName().equals("toString") && method.getParameterCount() == 0) {
              return "Default " + type.getSimpleName();
            }
            if (method.getName().equals("hashCode") && method.getParameterCount() == 0) {
              return System.identityHashCode(proxy);
            }
            if (method.getName().equals("equals") && method.getParameterCount() == 1) {
              return proxy == args[0];
            }
            Class<?> rt = method.getReturnType();
            return rt.isPrimitive() ? primitiveDefault(rt) : null;
          });
    }
    try {
      Constructor<?> c = type.getDeclaredConstructor();
      c.setAccessible(true);
      return c.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(
          "No default value for " + type.getName() + " (needed by " + member + "); call setDefault");
    }
  }

  private static Object primitiveDefault(Class<?> type) {
    if (type == boolean.class) {
      return false;
    } else if (type == char.class) {
      return 'a';
    } else if (type == byte.class) {
      return (byte) 0;
    } else if (type == short.class) {
      return (short) 0;
    } else if (type == int.class) {
      return 0;
    } else if (type == long.class) {
      return 0L;
    } else if (type == float.class) {
      return 0f;
    } else if (type == double.class) {
      return 0d;
    } else if (type == void.class) {
      return null;
    }
    throw new AssertionError(type);
  }
}
