package com.simplelibs.lava.eventbus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * Differential test: the same random programs of register / unregister / post run against Lava's
 * {@link EventBus} and Guava 27.0.1's, using listener classes whose methods carry both libraries'
 * {@code @Subscribe}. The delivery logs (including reentrant posts, registration during dispatch,
 * subscriber exceptions and dead events) must be identical.
 *
 * <p>Event types form class chains without interfaces: Guava orders event types of equal depth
 * in its type hierarchy by hash code, which is not reproducible across libraries.
 */
class EventBusDifferentialTest {

  static class E1 {
    final int id;

    E1(int id) {
      this.id = id;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName() + "#" + id;
    }
  }

  static class E2 extends E1 {
    E2(int id) {
      super(id);
    }
  }

  static class E3 extends E2 {
    E3(int id) {
      super(id);
    }
  }

  /** Abstraction over the two buses. */
  interface Bus {
    void register(Object o);

    void unregister(Object o);

    void post(Object o);
  }

  /** Base listener: records deliveries into a shared log; may re-post through the bus. */
  abstract static class Listener {
    final String name;
    final List<String> log;
    Bus bus;

    Listener(String name, List<String> log) {
      this.name = name;
      this.log = log;
    }

    void record(String what, Object event) {
      log.add(name + "." + what + "(" + event + ")");
    }
  }

  static class AllLevels extends Listener {
    AllLevels(String name, List<String> log) {
      super(name, log);
    }

    @Subscribe
    @com.google.common.eventbus.Subscribe
    public void onE1(E1 e) {
      record("onE1", e);
    }

    @Subscribe
    @com.google.common.eventbus.Subscribe
    public void onE3(E3 e) {
      record("onE3", e);
    }
  }

  static class ObjectListener extends Listener {
    ObjectListener(String name, List<String> log) {
      super(name, log);
    }

    @Subscribe
    @com.google.common.eventbus.Subscribe
    public void onObject(Object e) {
      record("onObject", e);
    }
  }

  /** Re-posts a deeper event, exercising breadth-first reentrant dispatch. */
  static class Reposter extends Listener {
    Reposter(String name, List<String> log) {
      super(name, log);
    }

    @Subscribe
    @com.google.common.eventbus.Subscribe
    public void onE2(E2 e) {
      record("onE2", e);
      if (e.id < 1000 && !(e instanceof E3)) {
        bus.post(new E3(e.id * 10 + 1));
        bus.post(new E1(e.id * 10 + 2));
      }
    }
  }

  /** Registers / unregisters another listener while an event is being dispatched. */
  static class Registrar extends Listener {
    final Object other;
    boolean registered;

    Registrar(String name, List<String> log, Object other) {
      super(name, log);
      this.other = other;
    }

    @Subscribe
    @com.google.common.eventbus.Subscribe
    public void onE3(E3 e) {
      record("onE3", e);
      try {
        if (registered) {
          bus.unregister(other);
        } else {
          bus.register(other);
        }
        registered = !registered;
      } catch (IllegalArgumentException ex) {
        record("IAE", ex.getMessage().replaceAll("@[0-9a-f]+", "@x"));
      }
    }
  }

  static class Thrower extends Listener {
    Thrower(String name, List<String> log) {
      super(name, log);
    }

    @Subscribe
    @com.google.common.eventbus.Subscribe
    public void onE2(E2 e) {
      record("onE2", e);
      throw new IllegalStateException("boom " + e);
    }
  }

  static class DeadEventListener extends Listener {
    DeadEventListener(String name, List<String> log) {
      super(name, log);
    }

    @Subscribe
    public void onDead(DeadEvent e) {
      record("dead", e.getEvent());
    }

    @com.google.common.eventbus.Subscribe
    public void onGuavaDead(com.google.common.eventbus.DeadEvent e) {
      record("dead", e.getEvent());
    }
  }

  private static List<Listener> listeners(List<String> log) {
    List<Listener> result = new ArrayList<>();
    result.add(new AllLevels("all", log));
    result.add(new ObjectListener("object", log));
    result.add(new Reposter("reposter", log));
    Listener extra = new AllLevels("extra", log);
    result.add(new Registrar("registrar", log, extra));
    result.add(extra);
    result.add(new Thrower("thrower", log));
    result.add(new DeadEventListener("dead", log));
    return result;
  }

  private static void runProgram(long seed, Bus bus, List<Listener> listeners, List<String> log) {
    for (Listener l : listeners) {
      l.bus = bus;
    }
    Random random = new Random(seed);
    for (int op = 0; op < 60; op++) {
      int choice = random.nextInt(10);
      Listener target = listeners.get(random.nextInt(listeners.size()));
      try {
        if (choice < 2) {
          log.add("register " + target.name);
          bus.register(target);
        } else if (choice < 3) {
          log.add("unregister " + target.name);
          bus.unregister(target);
        } else {
          int id = op;
          Object event;
          switch (random.nextInt(4)) {
            case 0:
              event = new E1(id);
              break;
            case 1:
              event = new E2(id);
              break;
            case 2:
              event = new E3(id);
              break;
            default:
              event = new Object() {
                @Override
                public String toString() {
                  return "Plain#" + id;
                }
              };
          }
          log.add("post " + event);
          bus.post(event);
        }
      } catch (IllegalArgumentException e) {
        log.add("IAE " + e.getMessage().replaceAll("@[0-9a-f]+", "@x"));
      }
    }
  }

  @Test
  void randomProgramsProduceIdenticalLogs() {
    List<String> all = new ArrayList<>();
    for (long seed = 0; seed < 200; seed++) {
      List<String> lavaLog = new ArrayList<>();
      List<Listener> lavaListeners = listeners(lavaLog);
      EventBus lavaBus =
          new EventBus(
              (exception, context) ->
                  lavaLog.add(
                      "handler " + exception.getMessage() + " in "
                          + context.getSubscriberMethod().getName() + " for " + context.getEvent()));
      runProgram(
          seed,
          new Bus() {
            public void register(Object o) {
              lavaBus.register(o);
            }

            public void unregister(Object o) {
              lavaBus.unregister(o);
            }

            public void post(Object o) {
              lavaBus.post(o);
            }
          },
          lavaListeners,
          lavaLog);

      List<String> guavaLog = new ArrayList<>();
      List<Listener> guavaListeners = listeners(guavaLog);
      com.google.common.eventbus.EventBus guavaBus =
          new com.google.common.eventbus.EventBus(
              (exception, context) ->
                  guavaLog.add(
                      "handler " + exception.getMessage() + " in "
                          + context.getSubscriberMethod().getName() + " for " + context.getEvent()));
      runProgram(
          seed,
          new Bus() {
            public void register(Object o) {
              guavaBus.register(o);
            }

            public void unregister(Object o) {
              guavaBus.unregister(o);
            }

            public void post(Object o) {
              guavaBus.post(o);
            }
          },
          guavaListeners,
          guavaLog);

      assertThat(lavaLog).as("seed " + seed).containsExactlyElementsOf(guavaLog);
      all.addAll(lavaLog);
    }
    // The programs must actually exercise every path, or the comparison would be vacuous.
    for (String needle :
        List.of("handler boom", "dead.dead(", "reposter.onE2", "onE3(E3#", "registrar.onE3",
            "extra.onE1", "object.onObject(Plain#", "IAE missing event subscriber")) {
      assertThat(all).as(needle).anyMatch(line -> line.contains(needle));
    }
  }

  @Test
  void asyncEventBusWithQueueingExecutorMatchesGuava() {
    for (long seed = 0; seed < 100; seed++) {
      List<Runnable> lavaTasks = new ArrayList<>();
      List<Runnable> guavaTasks = new ArrayList<>();
      List<String> lavaLog = new ArrayList<>();
      List<String> guavaLog = new ArrayList<>();
      AsyncEventBus lavaBus = new AsyncEventBus("lava", lavaTasks::add);
      com.google.common.eventbus.AsyncEventBus guavaBus =
          new com.google.common.eventbus.AsyncEventBus("guava", guavaTasks::add);
      runAsync(seed, lavaBus::register, lavaBus::post, lavaTasks, lavaLog);
      runAsync(seed, guavaBus::register, guavaBus::post, guavaTasks, guavaLog);
      assertThat(lavaLog).as("seed " + seed).containsExactlyElementsOf(guavaLog);
    }
  }

  private static void runAsync(
      long seed,
      Consumer<Object> register,
      Consumer<Object> post,
      List<Runnable> tasks,
      List<String> log) {
    Random random = new Random(seed);
    register.accept(new AllLevels("all", log));
    register.accept(new ObjectListener("object", log));
    for (int op = 0; op < 20; op++) {
      if (random.nextInt(3) == 0 && !tasks.isEmpty()) {
        // Drain some queued deliveries, as an event loop would.
        int n = 1 + random.nextInt(tasks.size());
        for (int i = 0; i < n; i++) {
          tasks.remove(0).run();
        }
        log.add("drained " + n);
      } else {
        Object event = random.nextBoolean() ? new E1(op) : new E3(op);
        log.add("post " + event);
        post.accept(event);
      }
    }
    while (!tasks.isEmpty()) {
      tasks.remove(0).run();
    }
  }
}
