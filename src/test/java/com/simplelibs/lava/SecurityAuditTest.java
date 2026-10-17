package com.simplelibs.lava;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.simplelibs.lava.base.Equivalence;
import com.simplelibs.lava.base.Enums;
import com.simplelibs.lava.cache.Cache;
import com.simplelibs.lava.cache.CacheBuilder;
import com.simplelibs.lava.cache.RemovalCause;
import com.simplelibs.lava.collect.BoundType;
import com.simplelibs.lava.collect.ImmutableMap;
import com.simplelibs.lava.collect.ImmutableSet;
import com.simplelibs.lava.collect.Lists;
import com.simplelibs.lava.collect.Range;
import com.simplelibs.lava.eventbus.EventBus;
import com.simplelibs.lava.eventbus.Subscribe;
import com.simplelibs.lava.primitives.Doubles;
import com.simplelibs.lava.util.concurrent.ListenableFutureTask;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Month;
import java.time.chrono.IsoEra;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the fixes made after the security audit ({@code SECURITY_AUDIT.md}). Each
 * test names the finding it covers. Package-private code is reached through reflection.
 */
class SecurityAuditTest {

  private static Object invoke(Object target, Class<?> owner, String name, Class<?>[] types,
      Object... args) throws Throwable {
    Method method = owner.getDeclaredMethod(name, types);
    method.setAccessible(true);
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  private static void setField(Object target, Class<?> owner, String name, Object value)
      throws ReflectiveOperationException {
    Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  // SA-01

  @Test
  void enumLookupsAreSafeFromSeveralThreads() throws InterruptedException {
    List<Class<? extends Enum<?>>> types =
        List.of(TimeUnit.class, RoundingMode.class, ElementType.class, RetentionPolicy.class,
            DayOfWeek.class, Month.class, ChronoUnit.class, TextStyle.class, IsoEra.class,
            Thread.State.class, RemovalCause.class, BoundType.class);
    List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
    List<Thread> threads = new ArrayList<>();
    for (int t = 0; t < 8; t++) {
      Thread thread =
          new Thread(
              () -> {
                try {
                  for (int i = 0; i < 2_000; i++) {
                    check(types.get(i % types.size()));
                  }
                } catch (Throwable e) {
                  failures.add(e);
                }
              });
      threads.add(thread);
      thread.start();
    }
    for (Thread thread : threads) {
      thread.join(TimeUnit.SECONDS.toMillis(30));
      assertThat(thread.isAlive()).as("a lookup never returned").isFalse();
    }
    assertThat(failures).isEmpty();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void check(Class<? extends Enum<?>> type) {
    Enum<?> first = type.getEnumConstants()[0];
    assertThat(Enums.getIfPresent((Class) type, first.name()).get()).isSameAs(first);
    assertThat(Enums.getIfPresent((Class) type, "no such constant").isPresent()).isFalse();
  }

  // SA-02

  @Test
  void aCacheUsedFromTwoThreadsAtOnceFailsFast() throws Exception {
    Cache<String, String> cache = CacheBuilder.newBuilder().build();
    CountDownLatch loading = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicReference<Throwable> other = new AtomicReference<>();
    Thread owner =
        new Thread(
            () -> {
              try {
                cache.get(
                    "k",
                    () -> {
                      loading.countDown();
                      release.await();
                      return "v";
                    });
              } catch (Exception e) {
                other.set(e);
              }
            });
    owner.start();
    assertThat(loading.await(10, TimeUnit.SECONDS)).isTrue();
    // the owner is inside get(): using the cache now is concurrent use
    assertThatThrownBy(() -> cache.getIfPresent("x"))
        .isInstanceOf(ConcurrentModificationException.class)
        .hasMessageContaining("not thread-safe");
    release.countDown();
    owner.join(TimeUnit.SECONDS.toMillis(10));
    assertThat(other.get()).isNull();
    // handing the cache to another thread afterwards is fine
    assertThat(cache.getIfPresent("k")).isEqualTo("v");
    cache.put("x", "y");
    assertThat(cache.asMap()).containsOnlyKeys("k", "x");
  }

  @Test
  void listenableFutureTasksRunEveryListenerOnceOnAThreadPool() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(4);
    try {
      for (int round = 0; round < 50; round++) {
        ListenableFutureTask<String> task = ListenableFutureTask.create(() -> "done");
        AtomicInteger runs = new AtomicInteger();
        CountDownLatch all = new CountDownLatch(200);
        pool.execute(task);
        for (int i = 0; i < 200; i++) {
          task.addListener(
              () -> {
                runs.incrementAndGet();
                all.countDown();
              },
              pool);
        }
        assertThat(all.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(task.get()).isEqualTo("done");
        assertThat(runs.get()).isEqualTo(200);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  // SA-03

  @Test
  void eventsAreDeliveredOnThePostingThreadWithItsContext() throws Exception {
    ThreadLocal<String> user = new ThreadLocal<>();
    List<String> deliveries = Collections.synchronizedList(new ArrayList<>());
    CountDownLatch dispatching = new CountDownLatch(1);
    CountDownLatch otherPosted = new CountDownLatch(1);
    EventBus bus = new EventBus();
    bus.register(
        new Object() {
          @Subscribe
          public void on(String event) throws InterruptedException {
            deliveries.add(event + " as " + user.get());
            if (event.equals("first")) {
              dispatching.countDown();
              otherPosted.await(10, TimeUnit.SECONDS);
            }
          }
        });
    Thread alice =
        new Thread(
            () -> {
              user.set("alice");
              bus.post("first");
            });
    alice.start();
    assertThat(dispatching.await(10, TimeUnit.SECONDS)).isTrue();
    user.set("bob");
    bus.post("second"); // delivered here, before post returns, with bob's context
    assertThat(deliveries).contains("second as bob");
    otherPosted.countDown();
    alice.join(TimeUnit.SECONDS.toMillis(10));
    assertThat(deliveries).containsExactlyInAnyOrder("first as alice", "second as bob");
  }

  // SA-04

  @Test
  void multimapDeserializationDoesNotTrustTheValueCount() throws Throwable {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject("only value");
    }
    Class<?> multimap = Class.forName("com.simplelibs.lava.collect.ImmutableSetMultimap");
    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      // a count far beyond the data must fail on the missing data, not on a huge allocation
      assertThatThrownBy(
              () ->
                  invoke(null, multimap, "readValues",
                      new Class<?>[] {ObjectInputStream.class, int.class}, in, Integer.MAX_VALUE))
          .isInstanceOf(IOException.class);
    }
  }

  // SA-05

  @Test
  void immutableSetsAndMapsRejectDirectDeserialization() throws Throwable {
    Class<?> collection = Class.forName("com.simplelibs.lava.collect.ImmutableCollection");
    for (Object instance : List.of(ImmutableSet.of("a"), ImmutableSet.of("a", "b", "c"))) {
      assertThatThrownBy(
              () -> invoke(instance, collection, "readObject",
                  new Class<?>[] {ObjectInputStream.class}, (Object) null))
          .isInstanceOf(InvalidObjectException.class)
          .hasMessage("Use SerializedForm");
    }
    for (Object instance : List.of(ImmutableMap.of(), ImmutableMap.of("k", "v"), ImmutableMap.of("a", 1, "b", 2))) {
      assertThatThrownBy(
              () -> invoke(instance, ImmutableMap.class, "readObject",
                  new Class<?>[] {ObjectInputStream.class}, (Object) null))
          .isInstanceOf(InvalidObjectException.class)
          .hasMessage("Use SerializedForm");
    }
    // ordinary serialization still goes through the serialized forms
    assertThat(roundTrip(ImmutableSet.of("a", "b"))).isEqualTo(ImmutableSet.of("a", "b"));
    assertThat(roundTrip(ImmutableMap.of("k", "v"))).isEqualTo(ImmutableMap.of("k", "v"));
    assertThat(roundTrip(ImmutableMap.of("k", "v").keySet())).isEqualTo(Set.of("k"));
  }

  @Test
  void everyClassSerializedThroughAProxyIsGuarded() throws Exception {
    Path root = Path.of(ImmutableSet.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    List<String> unguarded = new ArrayList<>();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
        String name = root.relativize(file).toString().replace(File.separatorChar, '.');
        Class<?> type = Class.forName(name.substring(0, name.length() - ".class".length()), false,
            getClass().getClassLoader());
        if (Serializable.class.isAssignableFrom(type) && !type.isInterface() && usesProxy(type)
            && !guarded(type)) {
          unguarded.add(type.getName());
        }
      }
    }
    // a view whose state is a HashBiMap, which validates itself in its own readObject
    assertThat(unguarded).containsExactly("com.simplelibs.lava.collect.HashBiMap$Inverse");
  }

  private static boolean usesProxy(Class<?> type) {
    for (Class<?> c = type; c != null; c = c.getSuperclass()) {
      try {
        Method m = c.getDeclaredMethod("writeReplace");
        if (c == type || !Modifier.isPrivate(m.getModifiers())) {
          return true;
        }
      } catch (NoSuchMethodException e) {
        // keep looking
      }
    }
    return false;
  }

  private static boolean guarded(Class<?> type) {
    for (Class<?> c = type; c != null && Serializable.class.isAssignableFrom(c); c = c.getSuperclass()) {
      try {
        c.getDeclaredMethod("readObject", ObjectInputStream.class);
        return true;
      } catch (NoSuchMethodException e) {
        // keep looking
      }
    }
    return false;
  }

  private static Object roundTrip(Object object) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(object);
    }
    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return in.readObject();
    }
  }

  // SA-07

  @Test
  void deserializedRangesAreRevalidated() throws Throwable {
    Field lower = Range.class.getDeclaredField("lowerBound");
    lower.setAccessible(true);
    Range<Integer> inverted = Range.closed(1, 5);
    setField(inverted, Range.class, "lowerBound", lower.get(Range.closed(9, 9)));
    assertThatThrownBy(() -> invoke(inverted, Range.class, "readResolve", new Class<?>[0]))
        .isInstanceOf(InvalidObjectException.class);
    Range<Integer> missing = Range.closed(1, 5);
    setField(missing, Range.class, "lowerBound", null);
    assertThatThrownBy(() -> invoke(missing, Range.class, "readResolve", new Class<?>[0]))
        .isInstanceOf(InvalidObjectException.class);
    assertThat(invoke(Range.closed(1, 5), Range.class, "readResolve", new Class<?>[0]))
        .isEqualTo(Range.closed(1, 5));
    assertThat(invoke(Range.all(), Range.class, "readResolve", new Class<?>[0])).isSameAs(Range.all());
    assertThat(roundTrip(Range.openClosed(2, 3))).isEqualTo(Range.openClosed(2, 3));
  }

  @Test
  void deserializedArrayViewsAreRevalidated() throws Throwable {
    List<Double> view = Doubles.asList(1, 2, 3);
    Class<?> type = view.getClass();
    setField(view, type, "end", 5);
    assertThatThrownBy(() -> invoke(view, type, "readResolve", new Class<?>[0]))
        .isInstanceOf(InvalidObjectException.class);
    List<String> plus = Lists.asList("a", new String[] {"b"});
    setField(plus, plus.getClass(), "rest", null);
    assertThatThrownBy(() -> invoke(plus, plus.getClass(), "readResolve", new Class<?>[0]))
        .isInstanceOf(InvalidObjectException.class);
    assertThat(roundTrip(Doubles.asList(1, 2, 3))).isEqualTo(List.of(1.0, 2.0, 3.0));
    assertThat(roundTrip(Lists.asList("a", "b", new String[] {"c"}))).isEqualTo(List.of("a", "b", "c"));
  }

  // SA-08

  @Test
  void cachesKeyedByCollidingStringsStayCorrect() {
    List<String> keys = collidingStrings(10); // 1,024 strings with one hash code
    assertThat(keys.stream().mapToInt(String::hashCode).distinct().count()).isEqualTo(1);
    Cache<String, Integer> cache = CacheBuilder.newBuilder().build();
    for (int i = 0; i < keys.size(); i++) {
      cache.put(keys.get(i), i);
    }
    for (int i = 0; i < keys.size(); i++) {
      assertThat(cache.getIfPresent(keys.get(i))).isEqualTo(i);
    }
    for (int i = 0; i < keys.size(); i += 2) {
      cache.invalidate(keys.get(i));
    }
    assertThat(cache.size()).isEqualTo(keys.size() / 2);
    for (int i = 0; i < keys.size(); i++) {
      assertThat(cache.getIfPresent(keys.get(i))).isEqualTo(i % 2 == 0 ? null : (Integer) i);
    }
  }

  private static List<String> collidingStrings(int blocks) {
    List<String> result = new ArrayList<>(List.of(""));
    for (int b = 0; b < blocks; b++) {
      List<String> next = new ArrayList<>();
      for (String s : result) {
        next.add(s + "Aa");
        next.add(s + "BB");
      }
      result = next;
    }
    return result;
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void strongKeysOrderOnlyComparableKeysOfOneClass() throws Exception {
    Class<?> strongKey = Class.forName("com.simplelibs.lava.cache.LocalCache$StrongKey");
    Constructor<?> constructor = strongKey.getDeclaredConstructor(Object.class, int.class, Equivalence.class);
    constructor.setAccessible(true);
    Equivalence<Object> equals = Equivalence.equals();
    Comparable a = (Comparable) constructor.newInstance("a", 1, equals);
    Comparable b = (Comparable) constructor.newInstance("b", 1, equals);
    Comparable one = (Comparable) constructor.newInstance(1, 1, equals);
    Comparable plain = (Comparable) constructor.newInstance(new Object(), 1, equals);
    Comparable identityA = (Comparable) constructor.newInstance("a", 1, Equivalence.identity());
    Comparable identityB = (Comparable) constructor.newInstance("b", 1, Equivalence.identity());
    assertThat(a.compareTo(b)).isNegative();
    assertThat(b.compareTo(a)).isPositive();
    assertThat(a.compareTo(constructor.newInstance("a", 1, equals))).isZero();
    assertThat(a.compareTo(one)).isZero(); // different classes are unordered
    assertThat(plain.compareTo(plain)).isZero(); // not Comparable
    assertThat(identityA.compareTo(identityB)).isZero(); // natural order is not the equivalence
  }

  // SA-10

  @Test
  void concatRejectsTotalsThatDoNotFitInAnArray() {
    double[] chunk = new double[1 << 20];
    double[][] arrays = new double[1 << 11][];
    Arrays.fill(arrays, chunk); // 2^31 elements in total, 8 MB of memory
    assertThatThrownBy(() -> Doubles.concat(arrays))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("the total number of elements (2147483648) in the arrays must fit in an int");
    assertThat(Doubles.concat(new double[] {1}, new double[] {2, 3})).containsExactly(1, 2, 3);
  }
}
