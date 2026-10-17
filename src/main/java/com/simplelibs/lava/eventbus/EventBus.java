/*
 * Copyright (C) 2007 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * Modified for Lava: single-threaded, Guava-independent port of Guava 27.0.1.
 */

package com.simplelibs.lava.eventbus;

import static com.simplelibs.lava.base.Preconditions.checkNotNull;

import com.simplelibs.lava.util.concurrent.MoreExecutors;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dispatches events to listeners, and provides ways for listeners to register themselves.
 *
 * <p>The EventBus allows publish-subscribe-style communication between components without requiring
 * the components to explicitly register with one another (and thus be aware of each other). It is
 * designed exclusively to replace traditional Java in-process event distribution using explicit
 * registration. It is <em>not</em> a general-purpose publish-subscribe system, nor is it intended
 * for interprocess communication.
 *
 * <h2>Receiving Events</h2>
 *
 * <p>To receive events, an object should:
 *
 * <ol>
 *   <li>Expose a public method, known as the <i>event subscriber</i>, which accepts a single
 *       argument of the type of event desired;
 *   <li>Mark it with a {@link Subscribe} annotation;
 *   <li>Pass itself to an EventBus instance's {@link #register(Object)} method.
 * </ol>
 *
 * <h2>Posting Events</h2>
 *
 * <p>To post an event, simply provide the event object to the {@link #post(Object)} method. The
 * EventBus instance will determine the type of event and route it to all registered listeners.
 *
 * <p>Events are routed based on their type &mdash; an event will be delivered to any subscriber for
 * any type to which the event is <em>assignable.</em> This includes implemented interfaces, all
 * superclasses, and all interfaces implemented by superclasses.
 *
 * <p>When {@code post} is called, all registered subscribers for an event are run in sequence, so
 * subscribers should be reasonably quick. If an event may trigger an extended process (such as a
 * database load), queue it for later. (For a convenient way to do this, use an {@link
 * AsyncEventBus} with an executor whose queue the same thread drains.)
 *
 * <h2>Subscriber Methods</h2>
 *
 * <p>Event subscriber methods must accept only one argument: the event.
 *
 * <p>Subscribers should not, in general, throw. If they do, the EventBus will catch and log the
 * exception. This is rarely the right solution for error handling and should not be relied upon; it
 * is intended solely to help find problems during development.
 *
 * <p>Lava calls subscribers only on the thread that posts, so subscriber methods need not be
 * thread-safe; the {@link AllowConcurrentEvents} annotation is accepted for compatibility. A
 * subscriber that posts reentrantly does not re-enter itself: the event is queued.
 *
 * <h2>Dead Events</h2>
 *
 * <p>If an event is posted, but no registered subscribers can accept it, it is considered "dead."
 * To give the system a second chance to handle dead events, they are wrapped in an instance of
 * {@link DeadEvent} and reposted.
 *
 * <p>If a subscriber for a supertype of all events (such as Object) is registered, no event will
 * ever be considered dead, and no DeadEvents will be generated. Accordingly, while DeadEvent
 * extends {@link Object}, a subscriber registered to receive any Object will never receive a
 * DeadEvent.
 *
 * <p><b>Lava:</b> unlike Guava's, this class is <b>not</b> thread-safe. Register, unregister and
 * post on one thread; reentrant posts from subscribers are queued and delivered breadth-first, as
 * in Guava. Using a bus from several threads is not detected, but each event is still delivered on
 * the thread that posts it, as in Guava, so subscribers run with the poster's thread-local context.
 * Errors found while discovering
 * subscriber methods propagate directly: a {@code @Subscribe} method with the wrong number of
 * parameters makes {@link #register} and {@link #unregister} throw {@link
 * IllegalArgumentException} (Guava wraps it in an {@code UncheckedExecutionException}), and an
 * {@code Error} such as {@code NoClassDefFoundError} is thrown as is (Guava wraps it in an {@code
 * ExecutionError}). Several subscriber methods of one listener for the same event type are called
 * in an unspecified order, as in Guava.
 *
 * <p>See the Guava User Guide article on <a
 * href="https://github.com/google/guava/wiki/EventBusExplained">{@code EventBus}</a>.
 *
 * @author Cliff Biffle
 * @since 10.0
 */
public class EventBus {

  private static final Logger logger = Logger.getLogger(EventBus.class.getName());

  private final String identifier;
  private final Executor executor;
  private final SubscriberExceptionHandler exceptionHandler;

  private final SubscriberRegistry subscribers = new SubscriberRegistry(this);
  private final Dispatcher dispatcher;

  /** Creates a new EventBus named "default". */
  public EventBus() {
    this("default");
  }

  /**
   * Creates a new EventBus with the given {@code identifier}.
   *
   * @param identifier a brief name for this bus, for logging purposes. Should be a valid Java
   *     identifier.
   */
  public EventBus(String identifier) {
    this(
        identifier,
        MoreExecutors.directExecutor(),
        Dispatcher.perThreadDispatchQueue(),
        LoggingHandler.INSTANCE);
  }

  /**
   * Creates a new EventBus with the given {@link SubscriberExceptionHandler}.
   *
   * @param exceptionHandler Handler for subscriber exceptions.
   * @since 16.0
   */
  public EventBus(SubscriberExceptionHandler exceptionHandler) {
    this(
        "default",
        MoreExecutors.directExecutor(),
        Dispatcher.perThreadDispatchQueue(),
        exceptionHandler);
  }

  EventBus(
      String identifier,
      Executor executor,
      Dispatcher dispatcher,
      SubscriberExceptionHandler exceptionHandler) {
    this.identifier = checkNotNull(identifier);
    this.executor = checkNotNull(executor);
    this.dispatcher = checkNotNull(dispatcher);
    this.exceptionHandler = checkNotNull(exceptionHandler);
  }

  /**
   * Returns the identifier for this event bus.
   *
   * @since 19.0
   */
  public final String identifier() {
    return identifier;
  }

  /** Returns the default executor this event bus uses for dispatching events to subscribers. */
  final Executor executor() {
    return executor;
  }

  /** Handles the given exception thrown by a subscriber with the given context. */
  void handleSubscriberException(Throwable e, SubscriberExceptionContext context) {
    checkNotNull(e);
    checkNotNull(context);
    try {
      exceptionHandler.handleException(e, context);
    } catch (Throwable e2) {
      // if the handler threw an exception... well, just log it
      logger.log(
          Level.SEVERE,
          String.format(Locale.ROOT, "Exception %s thrown while handling exception: %s", e2, e),
          e2);
    }
  }

  /**
   * Registers all subscriber methods on {@code object} to receive events.
   *
   * @param object object whose subscriber methods should be registered.
   */
  public void register(Object object) {
    subscribers.register(object);
  }

  /**
   * Unregisters all subscriber methods on a registered {@code object}.
   *
   * @param object object whose subscriber methods should be unregistered.
   * @throws IllegalArgumentException if the object was not previously registered.
   */
  public void unregister(Object object) {
    subscribers.unregister(object);
  }

  /**
   * Posts an event to all registered subscribers. This method will return successfully after the
   * event has been posted to all subscribers, and regardless of any exceptions thrown by
   * subscribers.
   *
   * <p>If no subscribers have been subscribed for {@code event}'s class, and {@code event} is not
   * already a {@link DeadEvent}, it will be wrapped in a DeadEvent and reposted.
   *
   * @param event event to post.
   */
  public void post(Object event) {
    Iterator<Subscriber> eventSubscribers = subscribers.getSubscribers(event);
    if (eventSubscribers.hasNext()) {
      dispatcher.dispatch(event, eventSubscribers);
    } else if (!(event instanceof DeadEvent)) {
      // the event had no subscribers and was not itself a DeadEvent
      post(new DeadEvent(this, event));
    }
  }

  @Override
  public String toString() {
    // Guava's MoreObjects.toStringHelper(this).addValue(identifier) format.
    return getClass().getSimpleName() + "{" + identifier + "}";
  }

  /** Simple logging handler for subscriber exceptions. */
  static final class LoggingHandler implements SubscriberExceptionHandler {
    static final LoggingHandler INSTANCE = new LoggingHandler();

    @Override
    public void handleException(Throwable exception, SubscriberExceptionContext context) {
      Logger logger = logger(context);
      if (logger.isLoggable(Level.SEVERE)) {
        logger.log(Level.SEVERE, message(context), exception);
      }
    }

    private static Logger logger(SubscriberExceptionContext context) {
      return Logger.getLogger(EventBus.class.getName() + "." + context.getEventBus().identifier());
    }

    private static String message(SubscriberExceptionContext context) {
      Method method = context.getSubscriberMethod();
      return "Exception thrown by subscriber method "
          + method.getName()
          + '('
          + method.getParameterTypes()[0].getName()
          + ')'
          + " on subscriber "
          + context.getSubscriber()
          + " when dispatching event: "
          + context.getEvent();
    }
  }
}
