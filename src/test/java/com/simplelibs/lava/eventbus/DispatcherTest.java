/*
 * Copyright (C) 2014 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified for Lava: single-threaded, Guava-independent port of Guava 27.0.1.
 */

package com.simplelibs.lava.eventbus;

import static org.assertj.core.api.Assertions.assertThat;

import com.simplelibs.lava.collect.ImmutableList;
import com.simplelibs.lava.testing.LavaTestCase;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Dispatcher} implementations.
 *
 * @author Colin Decker
 */

public class DispatcherTest extends LavaTestCase {

  private final EventBus bus = new EventBus();

  private final IntegerSubscriber i1 = new IntegerSubscriber("i1");
  private final IntegerSubscriber i2 = new IntegerSubscriber("i2");
  private final IntegerSubscriber i3 = new IntegerSubscriber("i3");
  private final ImmutableList<Subscriber> integerSubscribers =
      ImmutableList.of(
          subscriber(bus, i1, "handleInteger", Integer.class),
          subscriber(bus, i2, "handleInteger", Integer.class),
          subscriber(bus, i3, "handleInteger", Integer.class));

  private final StringSubscriber s1 = new StringSubscriber("s1");
  private final StringSubscriber s2 = new StringSubscriber("s2");
  private final ImmutableList<Subscriber> stringSubscribers =
      ImmutableList.of(
          subscriber(bus, s1, "handleString", String.class),
          subscriber(bus, s2, "handleString", String.class));

  private final ConcurrentLinkedQueue<Object> dispatchedSubscribers =
      new ConcurrentLinkedQueue<>();

  private Dispatcher dispatcher;

  @Test
  public void testPerThreadQueuedDispatcher() {
    dispatcher = Dispatcher.perThreadDispatchQueue();
    dispatcher.dispatch(1, integerSubscribers.iterator());

    assertThat(dispatchedSubscribers)
        .containsExactly(
            i1,
            i2,
            i3, // Integer subscribers are dispatched to first.
            s1,
            s2, // Though each integer subscriber dispatches to all string subscribers,
            s1,
            s2, // those string subscribers aren't actually dispatched to until all integer
            s1,
            s2 // subscribers have finished.
            );
  }

  /**
   * Lava: Guava's version posts from two threads at once and asserts only the multiset of
   * dispatches. Lava is single-threaded, so the two posts happen one after the other on this
   * thread, and the order is deterministic: each post enqueues all of its (event, subscriber)
   * pairs and then drains the queue, so the string events posted by the Integer subscribers are
   * delivered after all Integer subscribers have run.
   */
  @Test
  public void testLegacyAsyncDispatcher() {
    dispatcher = Dispatcher.legacyAsync();

    dispatcher.dispatch(2, integerSubscribers.iterator());
    dispatcher.dispatch("foo", stringSubscribers.iterator());

    assertThat(dispatchedSubscribers)
        .containsExactlyInAnyOrder(i1, i2, i3, s1, s1, s1, s1, s2, s2, s2, s2);
    assertThat(dispatchedSubscribers)
        .containsExactly(i1, i2, i3, s1, s2, s1, s2, s1, s2, s1, s2);
  }

  @Test
  public void testImmediateDispatcher() {
    dispatcher = Dispatcher.immediate();
    dispatcher.dispatch(1, integerSubscribers.iterator());

    assertThat(dispatchedSubscribers)
        .containsExactly(
            i1, s1, s2, // Each integer subscriber immediately dispatches to 2 string subscribers.
            i2, s1, s2, i3, s1, s2);
  }

  private static Subscriber subscriber(
      EventBus bus, Object target, String methodName, Class<?> eventType) {
    try {
      return Subscriber.create(bus, target, target.getClass().getMethod(methodName, eventType));
    } catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
  }

  public final class IntegerSubscriber {
    private final String name;

    public IntegerSubscriber(String name) {
      this.name = name;
    }

    @Subscribe
    public void handleInteger(Integer integer) {
      dispatchedSubscribers.add(this);
      dispatcher.dispatch("hello", stringSubscribers.iterator());
    }

    @Override
    public String toString() {
      return name;
    }
  }

  public final class StringSubscriber {
    private final String name;

    public StringSubscriber(String name) {
      this.name = name;
    }

    @Subscribe
    public void handleString(String string) {
      dispatchedSubscribers.add(this);
    }

    @Override
    public String toString() {
      return name;
    }
  }
}
