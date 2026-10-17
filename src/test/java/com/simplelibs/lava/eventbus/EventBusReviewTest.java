package com.simplelibs.lava.eventbus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.simplelibs.lava.testing.NullPointerTester;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Tests added after the Milestone 6 adversarial review (most written by the reviewer to kill
 * surviving mutants): overloaded subscriber methods, listener identity, {@link AsyncEventBus}
 * wiring, exception-handler paths and their log messages, {@link DeadEvent} rendering, the null
 * sweep, and the event-type hierarchy order compared with Guava over the JDK's classes.
 */
class EventBusReviewTest {

  static final List<String> LOG = new ArrayList<>();

  /** Captures the records logged on {@code logger} while {@code action} runs. */
  private static List<LogRecord> captureLog(Logger logger, Runnable action) {
    List<LogRecord> records = new ArrayList<>();
    Handler handler =
        new Handler() {
          @Override
          public void publish(LogRecord record) {
            records.add(record);
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    boolean useParent = logger.getUseParentHandlers();
    logger.setUseParentHandlers(false);
    logger.addHandler(handler);
    try {
      action.run();
    } finally {
      logger.removeHandler(handler);
      logger.setUseParentHandlers(useParent);
    }
    return records;
  }

  public static class Overloads {
    @Subscribe
    public void on(String s) {
      LOG.add("S:" + s);
    }

    @Subscribe
    public void on(Integer i) {
      LOG.add("I:" + i);
    }

    @Subscribe
    public void other(String s) {
      LOG.add("O:" + s);
    }
  }

  @Test
  void overloadedAndSameTypeMethodsAllRegistered() {
    LOG.clear();
    EventBus bus = new EventBus();
    Overloads listener = new Overloads();
    bus.register(listener);
    bus.post("x");
    bus.post(5);
    assertThat(LOG).containsExactlyInAnyOrder("S:x", "O:x", "I:5");
    LOG.clear();
    bus.unregister(listener);
    bus.post("y");
    assertThat(LOG).isEmpty();
  }

  /** Equal to every other instance, and refuses to be hashed. */
  public static class Equalish {
    final String name;

    Equalish(String name) {
      this.name = name;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Equalish;
    }

    @Override
    public int hashCode() {
      throw new UnsupportedOperationException("hashCode");
    }

    @Override
    public String toString() {
      return "Equalish(" + name + ")";
    }

    @Subscribe
    public void on(String s) {
      LOG.add(name + ":" + s);
    }
  }

  @Test
  void equalButDistinctListenersBothReceive() {
    LOG.clear();
    EventBus bus = new EventBus();
    Equalish a = new Equalish("a");
    Equalish b = new Equalish("b");
    bus.register(a);
    bus.register(b);
    bus.post("x");
    assertThat(LOG).containsExactly("a:x", "b:x");
    bus.unregister(a);
    LOG.clear();
    bus.post("y");
    assertThat(LOG).containsExactly("b:y");
  }

  @Test
  void duplicateRegistrationIsIdempotent() {
    LOG.clear();
    EventBus bus = new EventBus();
    Equalish a = new Equalish("a");
    bus.register(a);
    bus.register(a);
    bus.post("x");
    assertThat(LOG).containsExactly("a:x");
    bus.unregister(a);
    assertThatThrownBy(() -> bus.unregister(a)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void deadEventSourceIsTheBus() {
    EventBus bus = new EventBus();
    List<DeadEvent> seen = new ArrayList<>();
    bus.register(
        new Object() {
          @Subscribe
          public void on(DeadEvent d) {
            seen.add(d);
          }
        });
    bus.post("nobody");
    assertThat(seen).hasSize(1);
    assertThat(seen.get(0).getSource()).isSameAs(bus);
    assertThat(seen.get(0).getEvent()).isEqualTo("nobody");
  }

  @Test
  void deadEventToStringRendersArraysLikeGuava() {
    EventBus bus = new EventBus("b");
    com.google.common.eventbus.EventBus guavaBus = new com.google.common.eventbus.EventBus("b");
    Object[] events = {new int[] {1, 2}, new int[][] {{1}, {2}}, new char[] {'x'}, "plain"};
    for (Object event : events) {
      assertThat(new DeadEvent(bus, event).toString())
          .isEqualTo(new com.google.common.eventbus.DeadEvent(guavaBus, event).toString());
    }
    assertThat(new DeadEvent(new String[] {"a"}, "x")).hasToString("DeadEvent{source=[a], event=x}");
  }

  @Test
  void handlerThatThrowsAnErrorDoesNotEscapePost() {
    EventBus bus =
        new EventBus(
            (e, c) -> {
              throw new AssertionError("from handler");
            });
    bus.register(
        new Object() {
          @Subscribe
          public void on(String s) {
            throw new IllegalStateException("x");
          }
        });
    List<LogRecord> records =
        captureLog(Logger.getLogger(EventBus.class.getName()), () -> bus.post("x"));
    assertThat(records).hasSize(1);
    assertThat(records.get(0).getThrown()).isInstanceOf(AssertionError.class);
  }

  @Test
  void handlerFailureIsLoggedOnTheClassLogger() {
    List<LogRecord> records =
        captureLog(
            Logger.getLogger(EventBus.class.getName()),
            () -> {
              EventBus bus =
                  new EventBus(
                      (e, c) -> {
                        throw new IllegalStateException("handler");
                      });
              bus.register(
                  new Object() {
                    @Subscribe
                    public void on(String s) {
                      throw new IllegalArgumentException("sub");
                    }
                  });
              bus.post("x");
            });
    assertThat(records).hasSize(1);
    assertThat(records.get(0).getLevel()).isEqualTo(Level.SEVERE);
    assertThat(records.get(0).getMessage())
        .isEqualTo(
            "Exception java.lang.IllegalStateException: handler thrown while handling exception: "
                + "java.lang.IllegalArgumentException: sub");
    assertThat(records.get(0).getThrown()).hasMessage("handler");
  }

  public static class Faulty {
    @Subscribe
    public void boom(String s) {
      throw new IllegalStateException("boom " + s);
    }
  }

  @Test
  void defaultHandlerMessageNamesEventSubscriberAndMethod() {
    EventBus bus = new EventBus("named");
    Faulty faulty = new Faulty();
    bus.register(faulty);
    List<LogRecord> records =
        captureLog(Logger.getLogger(EventBus.class.getName() + ".named"), () -> bus.post("e"));
    assertThat(records).hasSize(1);
    assertThat(records.get(0).getLevel()).isEqualTo(Level.SEVERE);
    assertThat(records.get(0).getMessage())
        .isEqualTo(
            "Exception thrown by subscriber method boom(java.lang.String) on subscriber "
                + faulty
                + " when dispatching event: e");
  }

  public static class Re {
    final String name;
    final EventBus bus;
    final List<String> log;

    Re(String name, EventBus bus, List<String> log) {
      this.name = name;
      this.bus = bus;
      this.log = log;
    }

    @Subscribe
    public void onString(String s) {
      log.add(name + ":" + s);
      if (s.equals("a")) {
        bus.post("b");
        bus.post("c");
      }
    }
  }

  private static List<String> scenario(java.util.function.Function<List<String>, EventBus> factory) {
    List<String> log = new ArrayList<>();
    EventBus bus = factory.apply(log);
    bus.register(new Re("x", bus, log));
    bus.register(new Re("y", bus, log));
    bus.post("a");
    return log;
  }

  @Test
  void asyncEventBusConstructorsUseLegacyAsyncDispatch() {
    SubscriberExceptionHandler handler = (e, c) -> {};
    Executor direct = Runnable::run;
    List<String> legacy =
        scenario(l -> new EventBus("ref", direct, Dispatcher.legacyAsync(), handler));
    List<String> perThread =
        scenario(l -> new EventBus("ref", direct, Dispatcher.perThreadDispatchQueue(), handler));
    List<String> immediate =
        scenario(l -> new EventBus("ref", direct, Dispatcher.immediate(), handler));
    assertThat(legacy).isNotEqualTo(perThread).isNotEqualTo(immediate);
    assertThat(scenario(l -> new AsyncEventBus("n", direct))).isEqualTo(legacy);
    assertThat(scenario(l -> new AsyncEventBus(direct))).isEqualTo(legacy);
    assertThat(scenario(l -> new AsyncEventBus(direct, handler))).isEqualTo(legacy);
    // The same orders as Guava's buses.
    List<String> guavaLegacy = new ArrayList<>();
    com.google.common.eventbus.AsyncEventBus guavaBus =
        new com.google.common.eventbus.AsyncEventBus("n", direct);
    for (String name : List.of("x", "y")) {
      guavaBus.register(
          new Object() {
            @com.google.common.eventbus.Subscribe
            public void onString(String s) {
              guavaLegacy.add(name + ":" + s);
              if (s.equals("a")) {
                guavaBus.post("b");
                guavaBus.post("c");
              }
            }
          });
    }
    guavaBus.post("a");
    assertThat(legacy).isEqualTo(guavaLegacy);
  }

  @Test
  void asyncEventBusIdentifiersAndHandlers() {
    Executor direct = Runnable::run;
    SubscriberExceptionHandler handler = (e, c) -> {};
    assertThat(new AsyncEventBus("n", direct).identifier()).isEqualTo("n");
    assertThat(new AsyncEventBus(direct).identifier()).isEqualTo("default");
    assertThat(new AsyncEventBus(direct, handler).identifier()).isEqualTo("default");
    assertThat(new EventBus(handler).identifier()).isEqualTo("default");
    List<Throwable> seen = new ArrayList<>();
    AsyncEventBus bus = new AsyncEventBus(direct, (e, c) -> seen.add(e));
    bus.register(new Faulty());
    bus.post("x");
    assertThat(seen).hasSize(1);
  }

  @Test
  void asyncEventBusDefaultHandlerLogsOnPerBusLogger() {
    for (String id : List.of("async-id", "default")) {
      List<LogRecord> records =
          captureLog(
              Logger.getLogger(EventBus.class.getName() + "." + id),
              () -> {
                Executor direct = Runnable::run;
                EventBus bus =
                    id.equals("default") ? new AsyncEventBus(direct) : new AsyncEventBus(id, direct);
                bus.register(new Faulty());
                bus.post("x");
              });
      assertThat(records).as(id).hasSize(1);
    }
  }

  public static class BaseL {
    @Subscribe
    public void on(String s) {
      throw new IllegalStateException("base");
    }
  }

  public static class SubL extends BaseL {
    @Subscribe
    @Override
    public void on(String s) {
      throw new IllegalStateException("sub");
    }
  }

  @Test
  void contextMethodIsTheMostDerivedOverride() {
    List<Method> methods = new ArrayList<>();
    EventBus bus = new EventBus((e, c) -> methods.add(c.getSubscriberMethod()));
    bus.register(new SubL());
    bus.post("x");
    assertThat(methods).hasSize(1);
    assertThat(methods.get(0).getDeclaringClass()).isEqualTo(SubL.class);
  }

  public static class BadArity {
    @Subscribe
    public void twoArgs(String a, String b) {}
  }

  @Test
  void arityMessageNamesTheMethod() {
    assertThatThrownBy(() -> new EventBus().register(new BadArity()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith(
            "Method public void "
                + BadArity.class.getName()
                + ".twoArgs(java.lang.String,java.lang.String) has @Subscribe annotation but has 2"
                + " parameters.");
  }

  @Test
  void unregisterOfListenerWithoutSubscribersIsSilent() {
    new EventBus().unregister(new Object());
  }

  @Test
  void wrongArgumentTypeBecomesError() throws Exception {
    Method charAt = String.class.getMethod("charAt", int.class);
    Subscriber subscriber = Subscriber.create(new EventBus(), "foo", charAt);
    assertThatThrownBy(() -> subscriber.invokeSubscriberMethod("notAnInt"))
        .isInstanceOf(Error.class)
        .hasMessage("Method rejected target/argument: notAnInt")
        .hasCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void legacyAsyncKeepsStalePairsAfterAnError_sameAsGuava() {
    List<String> log = new ArrayList<>();
    AsyncEventBus bus = new AsyncEventBus("a", Runnable::run);
    class Listener {
      final String name;

      Listener(String name) {
        this.name = name;
      }

      @Subscribe
      public void on(String s) {
        log.add(name + s);
        if (s.equals("boom")) {
          throw new AssertionError("x");
        }
      }
    }
    bus.register(new Listener("1"));
    bus.register(new Listener("2"));
    assertThatThrownBy(() -> bus.post("boom")).isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> bus.post("next")).isInstanceOf(AssertionError.class);
    assertThat(log).containsExactly("1boom", "2boom");
  }

  @Test
  void nullPointers() {
    NullPointerTester tester = new NullPointerTester();
    tester.testAllPublicConstructors(EventBus.class);
    tester.testAllPublicConstructors(AsyncEventBus.class);
    tester.testAllPublicConstructors(DeadEvent.class);
    tester.testAllPublicInstanceMethods(new EventBus());
    tester.testAllPublicInstanceMethods(new AsyncEventBus(Runnable::run));
  }

  // Hierarchy order

  interface I {}

  static class Y {}

  static class X extends Y implements I {}

  @Test
  void tieBreakInterfacesBeforeSuperclass() {
    assertThat(SubscriberRegistry.flattenHierarchy(X.class).asList())
        .containsExactly(X.class, I.class, Y.class, Object.class);
  }

  interface Top {}

  interface Left extends Top {}

  interface Right extends Top {}

  static class Dia implements Left, Right {}

  @Test
  void diamondInterfaceHierarchy() {
    assertThat(SubscriberRegistry.flattenHierarchy(Dia.class).asList())
        .containsExactly(Dia.class, Left.class, Right.class, Top.class, Object.class);
  }

  private static final Map<Class<?>, Integer> DEPTH = new HashMap<>();

  /** The longest path from {@code c} up to a root, as Guava's {@code TypeToken} ordering uses. */
  private static int depth(Class<?> c) {
    Integer d = DEPTH.get(c);
    if (d != null) {
      return d;
    }
    int max = c.isInterface() ? 1 : 0;
    for (Class<?> i : c.getInterfaces()) {
      max = Math.max(max, depth(i));
    }
    if (c.getSuperclass() != null) {
      max = Math.max(max, depth(c.getSuperclass()));
    }
    DEPTH.put(c, max + 1);
    return max + 1;
  }

  /** Groups {@code types} by depth, checking that depths never increase along the order. */
  private static Map<Integer, Set<String>> depthGroups(Iterable<?> types, Class<?> of) {
    Map<Integer, Set<String>> groups = new LinkedHashMap<>();
    int previous = Integer.MAX_VALUE;
    for (Object type : types) {
      Class<?> c = (Class<?>) type;
      int d = depth(c);
      assertThat(d).as("depth order of " + of).isLessThanOrEqualTo(previous);
      previous = d;
      groups.computeIfAbsent(d, k -> new TreeSet<>()).add(c.getName());
    }
    return groups;
  }

  /**
   * For hundreds of JDK classes (deep and diamond-shaped interface graphs), Lava's event types
   * match Guava's level by level, and supertypes never precede subtypes. Within one level Guava's
   * order is hash-dependent, so levels are compared as sets.
   */
  @Test
  void eventTypeHierarchyMatchesGuavaForJdkClasses() throws Exception {
    Method guavaFlatten =
        com.google.common.eventbus.EventBus.class
            .getClassLoader()
            .loadClass("com.google.common.eventbus.SubscriberRegistry")
            .getDeclaredMethod("flattenHierarchy", Class.class);
    guavaFlatten.setAccessible(true);
    FileSystem jrt = FileSystems.getFileSystem(URI.create("jrt:/"));
    List<Class<?>> classes = new ArrayList<>();
    for (String pkg : List.of("java/util", "java/util/concurrent", "java/io", "java/nio", "java/lang")) {
      Path root = jrt.getPath("/modules/java.base/" + pkg);
      try (Stream<Path> files = Files.list(root)) {
        for (Path file : (Iterable<Path>) files::iterator) {
          String name = file.toString();
          if (!name.endsWith(".class") || name.contains("-info")) {
            continue;
          }
          String className =
              name.substring("/modules/java.base/".length(), name.length() - 6).replace('/', '.');
          try {
            classes.add(Class.forName(className, false, null));
          } catch (ReflectiveOperationException | LinkageError e) {
            // skip classes that cannot be loaded
          }
        }
      }
    }
    classes.add(int[].class);
    classes.add(String[][].class);
    classes.add(Thread.State.class);
    assertThat(classes.size()).isGreaterThan(500);
    for (Class<?> c : classes) {
      assertThat(depthGroups(SubscriberRegistry.flattenHierarchy(c), c))
          .as(c.getName())
          .isEqualTo(depthGroups((Iterable<?>) guavaFlatten.invoke(null, c), c));
    }
  }
}
