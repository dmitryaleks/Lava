/*
 * Copyright (C) 2014 The Guava Authors
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

import static com.simplelibs.lava.base.Preconditions.checkArgument;
import static com.simplelibs.lava.base.Preconditions.checkNotNull;

import com.simplelibs.lava.checker.nullness.qual.Nullable;
import com.simplelibs.lava.collect.ImmutableList;
import com.simplelibs.lava.collect.ImmutableSet;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

/**
 * Registry of subscribers to a single event bus.
 *
 * <p>Lava: single-threaded. Guava keeps a {@code ConcurrentMap} of {@code CopyOnWriteArraySet}s so
 * that posting takes a lock-free snapshot; Lava keeps plain insertion-ordered sets and copies them
 * into a snapshot list when an event is posted, which gives the same semantics: subscribers that
 * are registered or unregistered while an event is being dispatched do not affect that event.
 *
 * @author Colin Decker
 */
final class SubscriberRegistry {

  /** All registered subscribers, indexed by event type, in registration order. */
  private final Map<Class<?>, Set<Subscriber>> subscribers = new HashMap<>();

  /** The event bus this registry belongs to. */
  private final EventBus bus;

  SubscriberRegistry(EventBus bus) {
    this.bus = checkNotNull(bus);
  }

  /** Registers all subscriber methods on the given listener object. */
  void register(Object listener) {
    Map<Class<?>, List<Subscriber>> listenerMethods = findAllSubscribers(listener);

    for (Entry<Class<?>, List<Subscriber>> entry : listenerMethods.entrySet()) {
      Class<?> eventType = entry.getKey();
      Collection<Subscriber> eventMethodsInListener = entry.getValue();
      subscribers.computeIfAbsent(eventType, type -> new LinkedHashSet<>())
          .addAll(eventMethodsInListener);
    }
  }

  /** Unregisters all subscribers on the given listener object. */
  void unregister(Object listener) {
    Map<Class<?>, List<Subscriber>> listenerMethods = findAllSubscribers(listener);

    for (Entry<Class<?>, List<Subscriber>> entry : listenerMethods.entrySet()) {
      Class<?> eventType = entry.getKey();
      Collection<Subscriber> listenerMethodsForType = entry.getValue();

      Set<Subscriber> currentSubscribers = subscribers.get(eventType);
      if (currentSubscribers == null || !currentSubscribers.removeAll(listenerMethodsForType)) {
        // if removeAll returns true, all we really know is that at least one subscriber was
        // removed... however, barring something very strange we can assume that if at least one
        // subscriber was removed, all subscribers on listener for that event type were... after
        // all, the definition of subscribers on a particular class is totally static
        throw new IllegalArgumentException(
            "missing event subscriber for an annotated method. Is " + listener + " registered?");
      }

      // Like Guava, an emptied set is kept: a later registration reuses it.
    }
  }

  Set<Subscriber> getSubscribersForTesting(Class<?> eventType) {
    Set<Subscriber> eventSubscribers = subscribers.get(eventType);
    return eventSubscribers == null
        ? ImmutableSet.<Subscriber>of()
        : Collections.unmodifiableSet(eventSubscribers);
  }

  /**
   * Gets an iterator representing an immutable snapshot of all subscribers to the given event at
   * the time this method is called.
   */
  Iterator<Subscriber> getSubscribers(Object event) {
    ImmutableSet<Class<?>> eventTypes = flattenHierarchy(event.getClass());

    List<Subscriber> snapshot = new ArrayList<>();
    for (Class<?> eventType : eventTypes) {
      Set<Subscriber> eventSubscribers = subscribers.get(eventType);
      if (eventSubscribers != null) {
        snapshot.addAll(eventSubscribers);
      }
    }
    return Collections.unmodifiableList(snapshot).iterator();
  }

  /**
   * A cache that contains the mapping from each class to all methods in that class and all
   * super-classes, that are annotated with {@code @Subscribe}. The cache is shared across all
   * instances of this class; this greatly improves performance if multiple EventBus instances are
   * created and objects of the same class are registered on all of them.
   *
   * <p>Lava uses a {@link ClassValue} where Guava uses a weak-keyed {@code LoadingCache}. The
   * {@code ClassValue} lets an unregistered listener's class loader be collected; Guava 27.0.1's
   * cache keeps it reachable through the cached {@code Method}s. A {@code @Subscribe} method with
   * the wrong number of parameters makes every lookup throw {@link IllegalArgumentException} (Guava
   * wraps that exception in an {@code UncheckedExecutionException}).
   */
  private static final ClassValue<ImmutableList<Method>> subscriberMethodsCache =
      new ClassValue<ImmutableList<Method>>() {
        @Override
        protected ImmutableList<Method> computeValue(Class<?> concreteClass) {
          return getAnnotatedMethodsNotCached(concreteClass);
        }
      };

  /**
   * Returns all subscribers for the given listener grouped by the type of event they subscribe to.
   */
  private Map<Class<?>, List<Subscriber>> findAllSubscribers(Object listener) {
    Map<Class<?>, List<Subscriber>> methodsInListener = new LinkedHashMap<>();
    Class<?> clazz = listener.getClass();
    for (Method method : getAnnotatedMethods(clazz)) {
      Class<?>[] parameterTypes = method.getParameterTypes();
      Class<?> eventType = parameterTypes[0];
      methodsInListener
          .computeIfAbsent(eventType, type -> new ArrayList<>())
          .add(Subscriber.create(bus, listener, method));
    }
    return methodsInListener;
  }

  private static ImmutableList<Method> getAnnotatedMethods(Class<?> clazz) {
    return subscriberMethodsCache.get(clazz);
  }

  private static ImmutableList<Method> getAnnotatedMethodsNotCached(Class<?> clazz) {
    Set<Class<?>> supertypes = flattenHierarchyNotCached(clazz);
    Map<MethodIdentifier, Method> identifiers = new LinkedHashMap<>();
    for (Class<?> supertype : supertypes) {
      for (Method method : supertype.getDeclaredMethods()) {
        if (method.isAnnotationPresent(Subscribe.class) && !method.isSynthetic()) {
          // TODO(cgdecker): Should check for a generic parameter type and error out
          Class<?>[] parameterTypes = method.getParameterTypes();
          checkArgument(
              parameterTypes.length == 1,
              "Method %s has @Subscribe annotation but has %s parameters."
                  + "Subscriber methods must have exactly 1 parameter.",
              method,
              parameterTypes.length);

          MethodIdentifier ident = new MethodIdentifier(method);
          if (!identifiers.containsKey(ident)) {
            identifiers.put(ident, method);
          }
        }
      }
    }
    return ImmutableList.copyOf(identifiers.values());
  }

  /** Global cache of classes to their flattened hierarchy of supertypes. */
  private static final ClassValue<ImmutableSet<Class<?>>> flattenHierarchyCache =
      new ClassValue<ImmutableSet<Class<?>>>() {
        @Override
        protected ImmutableSet<Class<?>> computeValue(Class<?> concreteClass) {
          return ImmutableSet.copyOf(flattenHierarchyNotCached(concreteClass));
        }
      };

  /**
   * Flattens a class's type hierarchy into a set of {@code Class} objects including all
   * superclasses (transitively) and all interfaces implemented by these superclasses.
   */
  static ImmutableSet<Class<?>> flattenHierarchy(Class<?> concreteClass) {
    return flattenHierarchyCache.get(concreteClass);
  }

  /**
   * The raw types of {@code type} in the order of Guava's {@code
   * TypeToken.of(type).getTypes().rawTypes()}: sorted by decreasing depth, where a type's depth is
   * one more than the deepest of its superclass and interfaces (an interface counts at least one,
   * so interfaces come before {@code Object}). Subtypes therefore come before their supertypes.
   * Guava breaks ties in hash order; Lava breaks them deterministically in discovery order
   * (interfaces in declaration order, then the superclass, depth first).
   */
  static Set<Class<?>> flattenHierarchyNotCached(Class<?> type) {
    Map<Class<?>, Integer> depths = new LinkedHashMap<>();
    collectTypes(type, depths);
    List<Class<?>> types = new ArrayList<>(depths.keySet());
    // List.sort is stable, so ties keep their discovery order.
    types.sort((left, right) -> Integer.compare(depths.get(right), depths.get(left)));
    return new LinkedHashSet<>(types);
  }

  private static int collectTypes(Class<?> type, Map<Class<?>, Integer> depths) {
    Integer existing = depths.get(type);
    if (existing != null) {
      return existing;
    }
    // Interfaces should be listed before Object.
    int aboveMe = type.isInterface() ? 1 : 0;
    for (Class<?> interfaceType : type.getInterfaces()) {
      aboveMe = Math.max(aboveMe, collectTypes(interfaceType, depths));
    }
    Class<?> superclass = type.getSuperclass();
    if (superclass != null) {
      aboveMe = Math.max(aboveMe, collectTypes(superclass, depths));
    }
    depths.put(type, aboveMe + 1);
    return aboveMe + 1;
  }

  private static final class MethodIdentifier {

    private final String name;
    private final List<Class<?>> parameterTypes;

    MethodIdentifier(Method method) {
      this.name = method.getName();
      this.parameterTypes = Arrays.asList(method.getParameterTypes());
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, parameterTypes);
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (o instanceof MethodIdentifier) {
        MethodIdentifier ident = (MethodIdentifier) o;
        return name.equals(ident.name) && parameterTypes.equals(ident.parameterTypes);
      }
      return false;
    }
  }
}
