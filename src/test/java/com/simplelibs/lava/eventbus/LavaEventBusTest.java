package com.simplelibs.lava.eventbus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/** Tests of Lava-specific EventBus behavior and documented deviations from Guava. */
class LavaEventBusTest {

  static class BadArity {
    @Subscribe
    public void twoArgs(String a, String b) {}
  }

  @Test
  void badArityThrowsIllegalArgumentExceptionDirectly() {
    EventBus bus = new EventBus();
    // Guava wraps this in UncheckedExecutionException (it is thrown inside a cache loader).
    assertThatThrownBy(() -> bus.register(new BadArity()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("has @Subscribe annotation but has 2 parameters.Subscriber methods");
    // Failures are not cached: every call throws again.
    assertThatThrownBy(() -> bus.unregister(new BadArity()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  static class ErrorThrower {
    final List<String> seen = new ArrayList<>();
    EventBus bus;

    @Subscribe
    public void on(String s) {
      seen.add(s);
      if (s.equals("first")) {
        bus.post("queued"); // queued behind the current event
        throw new AssertionError("fatal");
      }
    }
  }

  @Test
  void errorEscapesPostAndResetsTheDispatchQueue() {
    EventBus bus = new EventBus();
    ErrorThrower thrower = new ErrorThrower();
    thrower.bus = bus;
    bus.register(thrower);
    assertThatThrownBy(() -> bus.post("first")).isInstanceOf(AssertionError.class).hasMessage("fatal");
    // The queued event was discarded together with the failed dispatch, as in Guava.
    assertThat(thrower.seen).containsExactly("first");
    bus.post("second");
    assertThat(thrower.seen).containsExactly("first", "second");
  }

  static class Failing {
    @Subscribe
    public void on(Integer i) {
      throw new IllegalStateException("bad " + i);
    }
  }

  @Test
  void loggingHandlerUsesGuavaFormatOnPerBusLogger() {
    Logger logger = Logger.getLogger(EventBus.class.getName() + ".lava-bus");
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
      EventBus bus = new EventBus("lava-bus");
      Failing failing = new Failing();
      bus.register(failing);
      bus.post(7);
    } finally {
      logger.removeHandler(handler);
      logger.setUseParentHandlers(useParent);
    }
    assertThat(records).hasSize(1);
    assertThat(records.get(0).getLevel()).isEqualTo(Level.SEVERE);
    assertThat(records.get(0).getMessage())
        .startsWith("Exception thrown by subscriber method on(java.lang.Integer) on subscriber ")
        .endsWith(" when dispatching event: 7");
    assertThat(records.get(0).getThrown()).hasMessage("bad 7");
  }

  @Test
  void toStrings() {
    assertThat(new EventBus()).hasToString("EventBus{default}");
    assertThat(new EventBus("x")).hasToString("EventBus{x}");
    assertThat(new AsyncEventBus(Runnable::run)).hasToString("AsyncEventBus{default}");
    EventBus bus = new EventBus();
    assertThat(new DeadEvent(bus, "e")).hasToString("DeadEvent{source=EventBus{default}, event=e}");
  }

  interface Left {}

  interface Right {}

  static class Both implements Left, Right, Serializable {
    private static final long serialVersionUID = 0;
  }

  @Test
  void flattenHierarchyIsDeterministicAndMostSpecificFirst() {
    // Guava orders Left/Right/Serializable (equal depth) by hash code; Lava uses declaration order.
    assertThat(SubscriberRegistry.flattenHierarchy(Both.class).asList())
        .containsExactly(Both.class, Left.class, Right.class, Serializable.class, Object.class);
    assertThat(SubscriberRegistry.flattenHierarchy(Both.class))
        .containsExactlyInAnyOrderElementsOf(
            com.google.common.reflect.TypeToken.of(Both.class).getTypes().rawTypes());
    assertThat(SubscriberRegistry.flattenHierarchy(Integer.class))
        .containsExactlyInAnyOrderElementsOf(
            com.google.common.reflect.TypeToken.of(Integer.class).getTypes().rawTypes());
    assertThat(SubscriberRegistry.flattenHierarchy(Integer.class).asList().get(0))
        .isEqualTo(Integer.class);
    assertThat(SubscriberRegistry.flattenHierarchy(Integer.class).asList())
        .endsWith(Object.class);
  }
}
