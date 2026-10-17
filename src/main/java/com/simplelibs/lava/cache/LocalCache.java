/*
 * Copyright (C) 2009 The Guava Authors
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

package com.simplelibs.lava.cache;

import static com.simplelibs.lava.base.Preconditions.checkNotNull;
import static com.simplelibs.lava.base.Preconditions.checkState;

import com.simplelibs.lava.base.Equivalence;
import com.simplelibs.lava.base.Ticker;
import com.simplelibs.lava.cache.AbstractCache.SimpleStatsCounter;
import com.simplelibs.lava.cache.AbstractCache.StatsCounter;
import com.simplelibs.lava.cache.CacheBuilder.NullListener;
import com.simplelibs.lava.cache.CacheBuilder.OneWeigher;
import com.simplelibs.lava.cache.CacheLoader.InvalidCacheLoadException;
import com.simplelibs.lava.cache.CacheLoader.UnsupportedLoadingOperationException;
import com.simplelibs.lava.checker.nullness.qual.Nullable;
import com.simplelibs.lava.collect.ImmutableMap;
import com.simplelibs.lava.util.concurrent.ExecutionError;
import com.simplelibs.lava.util.concurrent.Futures;
import com.simplelibs.lava.util.concurrent.ListenableFuture;
import com.simplelibs.lava.util.concurrent.MoreExecutors;
import com.simplelibs.lava.util.concurrent.UncheckedExecutionException;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The single-threaded implementation behind {@link CacheBuilder}: a {@link ConcurrentMap} whose
 * semantics follow Guava 27.0.1's {@code LocalCache} with {@code concurrencyLevel(1)}, built on one
 * {@link HashMap} instead of lock-striped segments.
 *
 * <p>Each entry is a {@link Node} holding its key reference (strong, or weak and
 * identity-compared), a value reference (strong, weak or soft, with its weight, or a {@link
 * LoadingValue} while a load, refresh or compute is in flight), its access and write times, and its
 * positions in two intrusive doubly linked lists: access order (for LRU eviction and access
 * expiration) and write order (for write expiration). Every operation is O(1) apart from eviction
 * and expiration, which remove from the heads of those lists.
 *
 * <p>Differences from Guava's implementation, all within {@code Cache}'s documented contract:
 *
 * <ul>
 *   <li>Maintenance (reclaiming collected entries and expiring entries) runs at the start of every
 *       operation instead of on writes and every 64th read, so {@link #size} never counts expired
 *       entries, nor entries whose references the collector has enqueued. (A reference that has
 *       been cleared but not yet enqueued is counted until an operation reaches its entry, as in
 *       Guava.)
 *   <li>Removal notifications are queued while internal state is being changed and delivered, in
 *       order, before user code (a loader or compute function) runs and at the end of each
 *       operation, never while state is half-updated. A listener may call back into the cache.
 *       Guava delivers them before a loader runs too, but runs a compute function first.
 *   <li>A re-entrant {@code get} of a key whose load is running on the call stack throws {@code
 *       IllegalStateException("Recursive load of: ...")}, as in Guava. Where Guava would block the
 *       thread forever, Lava throws the same exception: an {@code asMap().compute*} of a key
 *       whose load, compute or refresh is running on the call stack, or a {@code get}, from a
 *       removal listener, of a key that is about to load. A {@code get} that finds an asynchronous
 *       refresh still pending, and no live value, loads synchronously instead of waiting; the
 *       refresh result is then discarded.
 *   <li>Guava 27.0.1 leaves an entry stuck in a loading state when a {@code compute} function
 *       throws or when {@code compute} removes an existing entry by returning null (later {@code
 *       get}s then throw {@code InvalidCacheLoadException}). Lava restores or removes the entry,
 *       and keeps its counts consistent when the function itself removes or replaces the entry.
 *       As in Guava, a value the function writes for its own key wins over a non-null result and
 *       is removed by a null one; but when the function returns the old value, Lava keeps the
 *       written value, which Guava drops without a notification.
 *   <li>Caches are not serializable.
 * </ul>
 *
 * <p><b>Performance:</b> creating an iterator over an {@code asMap()} view copies a snapshot of the
 * table, which costs O(n) even when only the first element is read (entries removed after that are
 * skipped). Avoid {@code iterator().next()}-style probes of large caches.
 *
 * <p>Like the rest of Lava, this class is not thread-safe.
 */
final class LocalCache<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V> {

  static final Logger logger = Logger.getLogger(LocalCache.class.getName());

  /** The largest initial table capacity, as in Guava. */
  static final int MAXIMUM_CAPACITY = 1 << 30;

  /** Reference strengths for keys and values. */
  enum Strength {
    STRONG {
      @Override
      Equivalence<Object> defaultEquivalence() {
        return Equivalence.equals();
      }
    },
    SOFT {
      @Override
      Equivalence<Object> defaultEquivalence() {
        return Equivalence.identity();
      }
    },
    WEAK {
      @Override
      Equivalence<Object> defaultEquivalence() {
        return Equivalence.identity();
      }
    };

    /** The default equivalence used to compare references of this strength. */
    abstract Equivalence<Object> defaultEquivalence();
  }

  final Strength keyStrength;
  final Strength valueStrength;
  final Equivalence<Object> keyEquivalence;
  final Equivalence<Object> valueEquivalence;
  final long maxWeight;
  final Weigher<K, V> weigher;
  final long expireAfterAccessNanos;
  final long expireAfterWriteNanos;
  final long refreshNanos;
  final RemovalListener<K, V> removalListener;
  final Ticker ticker;
  final StatsCounter statsCounter;
  final @Nullable CacheLoader<? super K, V> defaultLoader;

  // The table, the lists and the counters are package-private for CacheTesting's invariant checks.

  final HashMap<Object, Node<K, V>> table;
  private final @Nullable ArrayDeque<RemovalNotification<K, V>> pendingNotifications;
  private boolean flushing;
  private final @Nullable ReferenceQueue<Object> keyReferenceQueue;
  private final @Nullable ReferenceQueue<V> valueReferenceQueue;
  final Node<K, V> accessHead = new Node<>(null);
  final Node<K, V> writeHead = new Node<>(null);

  /** The number of live entries, as Guava's per-segment {@code count}. */
  int count;

  /** The total weight of the live entries. */
  long totalWeight;

  LocalCache(
      CacheBuilder<? super K, ? super V> builder, @Nullable CacheLoader<? super K, V> loader) {
    keyStrength = builder.getKeyStrength();
    valueStrength = builder.getValueStrength();
    keyEquivalence = builder.getKeyEquivalence();
    valueEquivalence = builder.getValueEquivalence();
    maxWeight = builder.getMaximumWeight();
    weigher = builder.getWeigher();
    expireAfterAccessNanos = builder.getExpireAfterAccessNanos();
    expireAfterWriteNanos = builder.getExpireAfterWriteNanos();
    refreshNanos = builder.getRefreshNanos();
    removalListener = builder.getRemovalListener();
    pendingNotifications = (removalListener == NullListener.INSTANCE) ? null : new ArrayDeque<>();
    ticker = builder.getTicker(recordsTime());
    statsCounter = builder.getStatsCounterSupplier().get();
    defaultLoader = loader;
    // As in Guava, initialCapacity sizes the table, but needn't exceed the maximum size.
    int initialCapacity = Math.min(builder.getInitialCapacity(), MAXIMUM_CAPACITY);
    if (evictsBySize() && !customWeigher()) {
      initialCapacity = (int) Math.min(initialCapacity, maxWeight);
    }
    table = new HashMap<>(initialCapacity);
    keyReferenceQueue = keyStrength == Strength.STRONG ? null : new ReferenceQueue<>();
    valueReferenceQueue = valueStrength == Strength.STRONG ? null : new ReferenceQueue<>();
    accessHead.prevAccess = accessHead;
    accessHead.nextAccess = accessHead;
    writeHead.prevWrite = writeHead;
    writeHead.nextWrite = writeHead;
  }

  boolean evictsBySize() {
    return maxWeight >= 0;
  }

  boolean customWeigher() {
    return weigher != OneWeigher.INSTANCE;
  }

  boolean expires() {
    return expiresAfterWrite() || expiresAfterAccess();
  }

  boolean expiresAfterWrite() {
    return expireAfterWriteNanos > 0;
  }

  boolean expiresAfterAccess() {
    return expireAfterAccessNanos > 0;
  }

  boolean refreshes() {
    return refreshNanos > 0;
  }

  boolean recordsWrite() {
    return expiresAfterWrite() || refreshes();
  }

  boolean recordsAccess() {
    return expiresAfterAccess();
  }

  boolean recordsTime() {
    return recordsWrite() || recordsAccess();
  }

  // Keys

  /** A key as stored in (or looked up in) the table, compared with the key equivalence. */
  interface KeyRef {
    @Nullable Object key();

    int keyHash();
  }

  static boolean keysEqual(Equivalence<Object> equivalence, KeyRef self, @Nullable Object other) {
    if (self == other) {
      return true;
    }
    if (!(other instanceof KeyRef)) {
      return false;
    }
    KeyRef that = (KeyRef) other;
    if (self.keyHash() != that.keyHash()) {
      return false;
    }
    Object a = self.key();
    Object b = that.key();
    return a != null && b != null && equivalence.equivalent(a, b);
  }

  static final class StrongKey implements KeyRef {
    private final Object key;
    private final int hash;
    private final Equivalence<Object> equivalence;

    StrongKey(Object key, int hash, Equivalence<Object> equivalence) {
      this.key = key;
      this.hash = hash;
      this.equivalence = equivalence;
    }

    @Override
    public Object key() {
      return key;
    }

    @Override
    public int keyHash() {
      return hash;
    }

    @Override
    public boolean equals(@Nullable Object other) {
      return keysEqual(equivalence, this, other);
    }

    @Override
    public int hashCode() {
      return hash;
    }
  }

  static final class WeakKey extends WeakReference<Object> implements KeyRef {
    private final int hash;
    private final Equivalence<Object> equivalence;

    WeakKey(Object key, int hash, Equivalence<Object> equivalence, ReferenceQueue<Object> queue) {
      super(key, queue);
      this.hash = hash;
      this.equivalence = equivalence;
    }

    @Override
    public @Nullable Object key() {
      return get();
    }

    @Override
    public int keyHash() {
      return hash;
    }

    @Override
    public boolean equals(@Nullable Object other) {
      return keysEqual(equivalence, this, other);
    }

    @Override
    public int hashCode() {
      return hash;
    }
  }

  private Object probe(Object key) {
    return new StrongKey(key, keyEquivalence.hash(key), keyEquivalence);
  }

  // Values

  /** A value reference: a live value with its weight, a collected one, or an in-flight load. */
  interface ValueRef<V> {
    /** Returns the value, or null if collected, unset or still loading without an old value. */
    @Nullable V get();

    int getWeight();

    /** Whether this is a {@link LoadingValue}. */
    boolean isLoading();

    /** Whether this reference holds (or held, until collected) a value. */
    boolean isActive();
  }

  private static final ValueRef<Object> UNSET =
      new ValueRef<Object>() {
        @Override
        public @Nullable Object get() {
          return null;
        }

        @Override
        public int getWeight() {
          return 0;
        }

        @Override
        public boolean isLoading() {
          return false;
        }

        @Override
        public boolean isActive() {
          return false;
        }
      };

  @SuppressWarnings("unchecked")
  static <V> ValueRef<V> unset() {
    return (ValueRef<V>) UNSET;
  }

  static final class StrongValue<V> implements ValueRef<V> {
    private final V value;
    private final int weight;

    StrongValue(V value, int weight) {
      this.value = value;
      this.weight = weight;
    }

    @Override
    public V get() {
      return value;
    }

    @Override
    public int getWeight() {
      return weight;
    }

    @Override
    public boolean isLoading() {
      return false;
    }

    @Override
    public boolean isActive() {
      return true;
    }
  }

  static final class WeakValue<K, V> extends WeakReference<V> implements ValueRef<V> {
    final Node<K, V> node;
    private final int weight;

    WeakValue(V value, int weight, Node<K, V> node, ReferenceQueue<V> queue) {
      super(value, queue);
      this.weight = weight;
      this.node = node;
    }

    @Override
    public int getWeight() {
      return weight;
    }

    @Override
    public boolean isLoading() {
      return false;
    }

    @Override
    public boolean isActive() {
      return true;
    }
  }

  static final class SoftValue<K, V> extends SoftReference<V> implements ValueRef<V> {
    final Node<K, V> node;
    private final int weight;

    SoftValue(V value, int weight, Node<K, V> node, ReferenceQueue<V> queue) {
      super(value, queue);
      this.weight = weight;
      this.node = node;
    }

    @Override
    public int getWeight() {
      return weight;
    }

    @Override
    public boolean isLoading() {
      return false;
    }

    @Override
    public boolean isActive() {
      return true;
    }
  }

  /**
   * An in-flight load, refresh or compute. Until it completes, reads see the old value (if any),
   * as with Guava's {@code LoadingValueReference}.
   */
  static final class LoadingValue<V> implements ValueRef<V> {
    ValueRef<V> oldValue;

    /** True while the loader or compute function runs on the call stack. */
    boolean running;

    /** True for {@code compute}, whose entry is skipped by eviction and expiration until done. */
    final boolean computing;

    private long startNanos = System.nanoTime();

    LoadingValue(ValueRef<V> oldValue, boolean computing) {
      this.oldValue = oldValue;
      this.computing = computing;
    }

    @Override
    public @Nullable V get() {
      return oldValue.get();
    }

    @Override
    public int getWeight() {
      return oldValue.getWeight();
    }

    @Override
    public boolean isLoading() {
      return true;
    }

    @Override
    public boolean isActive() {
      return oldValue.isActive();
    }

    /** The old value was removed while loading: the entry now loads as if absent. */
    void clearOldValue() {
      oldValue = unset();
    }

    /**
     * Starts timing the load once the pending notifications have been delivered, so that listener
     * time is not counted as load time (Guava starts its stopwatch at the same point).
     */
    void startClock() {
      startNanos = System.nanoTime();
    }

    long elapsedNanos() {
      return System.nanoTime() - startNanos;
    }
  }

  // Entries

  static final class Node<K, V> {
    /** The table key: a {@link StrongKey} or {@link WeakKey}; null for list sentinels. */
    final @Nullable Object mapKey;

    ValueRef<V> valueRef = unset();
    long accessTime;
    long writeTime;
    @Nullable Node<K, V> prevAccess;
    @Nullable Node<K, V> nextAccess;
    @Nullable Node<K, V> prevWrite;
    @Nullable Node<K, V> nextWrite;

    Node(@Nullable Object mapKey) {
      this.mapKey = mapKey;
    }

    @SuppressWarnings("unchecked")
    @Nullable K getKey() {
      return (K) ((KeyRef) mapKey).key();
    }
  }

  private Node<K, V> newNode(K key) {
    int hash = keyEquivalence.hash(key);
    Object mapKey =
        keyReferenceQueue == null
            ? new StrongKey(key, hash, keyEquivalence)
            : new WeakKey(key, hash, keyEquivalence, keyReferenceQueue);
    return new Node<>(mapKey);
  }

  private @Nullable Node<K, V> getEntry(Object key) {
    return table.get(probe(key));
  }

  private boolean isLinked(Node<K, V> node) {
    return table.get(node.mapKey) == node;
  }

  private ValueRef<V> referenceValue(Node<K, V> node, V value, int weight) {
    switch (valueStrength) {
      case WEAK:
        return new WeakValue<>(value, weight, node, valueReferenceQueue);
      case SOFT:
        return new SoftValue<>(value, weight, node, valueReferenceQueue);
      default:
        return new StrongValue<>(value, weight);
    }
  }

  private static boolean isComputing(Node<?, ?> node) {
    return node.valueRef instanceof LoadingValue && ((LoadingValue<?>) node.valueRef).computing;
  }

  // Access and write order

  private void moveToAccessTail(Node<K, V> node) {
    unlinkAccess(node);
    node.prevAccess = accessHead.prevAccess;
    node.nextAccess = accessHead;
    accessHead.prevAccess.nextAccess = node;
    accessHead.prevAccess = node;
  }

  private void unlinkAccess(Node<K, V> node) {
    if (node.nextAccess != null) {
      node.prevAccess.nextAccess = node.nextAccess;
      node.nextAccess.prevAccess = node.prevAccess;
      node.prevAccess = null;
      node.nextAccess = null;
    }
  }

  private void moveToWriteTail(Node<K, V> node) {
    unlinkWrite(node);
    node.prevWrite = writeHead.prevWrite;
    node.nextWrite = writeHead;
    writeHead.prevWrite.nextWrite = node;
    writeHead.prevWrite = node;
  }

  private void unlinkWrite(Node<K, V> node) {
    if (node.nextWrite != null) {
      node.prevWrite.nextWrite = node.nextWrite;
      node.nextWrite.prevWrite = node.prevWrite;
      node.prevWrite = null;
      node.nextWrite = null;
    }
  }

  private void recordRead(Node<K, V> node, long now) {
    if (isComputing(node)) {
      return; // Guava's computing entries are outside the queues until the compute completes
    }
    if (recordsAccess()) {
      node.accessTime = now;
    }
    if (node.nextAccess != null) {
      moveToAccessTail(node);
    }
  }

  private void recordWrite(Node<K, V> node, int weight, long now) {
    totalWeight += weight;
    if (recordsAccess()) {
      node.accessTime = now;
    }
    if (recordsWrite()) {
      node.writeTime = now;
    }
    moveToAccessTail(node);
    moveToWriteTail(node);
  }

  boolean isExpired(Node<K, V> node, long now) {
    if (expiresAfterAccess() && (now - node.accessTime >= expireAfterAccessNanos)) {
      return true;
    }
    return expiresAfterWrite() && (now - node.writeTime >= expireAfterWriteNanos);
  }

  /** Returns the live value of {@code node}, or null if collected, loading, or expired. */
  private @Nullable V getLiveValue(Node<K, V> node, long now) {
    if (node.getKey() == null) {
      return null;
    }
    V value = node.valueRef.get();
    if (value == null) {
      return null;
    }
    if (isExpired(node, now)) {
      return null;
    }
    return value;
  }

  // Operation framing, maintenance and notifications

  /** Starts a public operation: reads the ticker and runs maintenance. */
  private long beginOperation() {
    long now = ticker.read();
    runMaintenance(now);
    return now;
  }

  /** Ends a public operation: delivers pending notifications. */
  private void endOperation() {
    flushNotifications();
  }

  private void runMaintenance(long now) {
    drainReferenceQueues();
    expireEntries(now);
  }

  @SuppressWarnings("unchecked")
  private void drainReferenceQueues() {
    if (keyReferenceQueue != null) {
      Reference<?> ref;
      while ((ref = keyReferenceQueue.poll()) != null) {
        Node<K, V> node = table.get(ref);
        if (node != null && node.mapKey == ref) {
          removeEntry(node, RemovalCause.COLLECTED);
        }
      }
    }
    if (valueReferenceQueue != null) {
      Reference<? extends V> ref;
      while ((ref = valueReferenceQueue.poll()) != null) {
        Node<K, V> node =
            ref instanceof WeakValue ? ((WeakValue<K, V>) ref).node : ((SoftValue<K, V>) ref).node;
        if (node.valueRef == ref && isLinked(node)) {
          removeEntry(node, RemovalCause.COLLECTED);
        }
      }
    }
  }

  private void expireEntries(long now) {
    if (!expires()) {
      return;
    }
    if (expiresAfterWrite()) {
      Node<K, V> node;
      while ((node = firstNonComputing(true)) != null && isExpired(node, now)) {
        removeEntry(node, RemovalCause.EXPIRED);
      }
    }
    Node<K, V> node;
    while ((node = firstNonComputing(false)) != null && isExpired(node, now)) {
      removeEntry(node, RemovalCause.EXPIRED);
    }
  }

  private @Nullable Node<K, V> firstNonComputing(boolean writeOrder) {
    Node<K, V> head = writeOrder ? writeHead : accessHead;
    for (Node<K, V> node = writeOrder ? head.nextWrite : head.nextAccess;
        node != head;
        node = writeOrder ? node.nextWrite : node.nextAccess) {
      if (!isComputing(node)) {
        return node;
      }
    }
    return null;
  }

  private void enqueueNotification(
      @Nullable K key, @Nullable V value, int weight, RemovalCause cause) {
    totalWeight -= weight;
    if (cause.wasEvicted()) {
      statsCounter.recordEviction();
    }
    if (pendingNotifications != null) {
      pendingNotifications.add(RemovalNotification.create(key, value, cause));
    }
  }

  /**
   * Delivers the pending removal notifications in order. A listener that calls back into the cache
   * adds to the same queue, which the outermost delivery loop drains.
   */
  private void flushNotifications() {
    if (pendingNotifications == null || flushing) {
      return;
    }
    flushing = true;
    try {
      RemovalNotification<K, V> notification;
      while ((notification = pendingNotifications.poll()) != null) {
        try {
          removalListener.onRemoval(notification);
        } catch (Throwable e) {
          logger.log(Level.WARNING, "Exception thrown by removal listener", e);
        }
      }
    } finally {
      flushing = false;
    }
  }

  /**
   * Removes {@code node}'s value with {@code cause}. An in-flight load stays in the table (as in
   * Guava) and completes as if its key had been absent.
   */
  private void removeValueFromChain(
      Node<K, V> node, @Nullable K key, @Nullable V value, ValueRef<V> ref, RemovalCause cause) {
    enqueueNotification(key, value, ref.getWeight(), cause);
    unlinkWrite(node);
    unlinkAccess(node);
    if (ref.isLoading()) {
      ((LoadingValue<V>) ref).clearOldValue();
    } else {
      table.remove(node.mapKey);
    }
  }

  private void removeEntry(Node<K, V> node, RemovalCause cause) {
    removeValueFromChain(node, node.getKey(), node.valueRef.get(), node.valueRef, cause);
    count--;
  }

  private int weigh(K key, V value) {
    int weight = weigher.weigh(key, value);
    checkState(weight >= 0, "Weights must be non-negative");
    return weight;
  }

  private void setValue(Node<K, V> node, V value, int weight, long now) {
    node.valueRef = referenceValue(node, value, weight);
    recordWrite(node, weight, now);
  }

  private void evictEntries(Node<K, V> newest) {
    if (!evictsBySize()) {
      return;
    }
    // If the newest entry by itself is too heavy, don't bother evicting anything else.
    if (newest.valueRef.getWeight() > maxWeight && isLinked(newest)) {
      removeEntry(newest, RemovalCause.SIZE);
    }
    while (totalWeight > maxWeight) {
      Node<K, V> evictable = nextEvictable();
      if (evictable == null) {
        return;
      }
      removeEntry(evictable, RemovalCause.SIZE);
    }
  }

  private @Nullable Node<K, V> nextEvictable() {
    for (Node<K, V> node = accessHead.nextAccess; node != accessHead; node = node.nextAccess) {
      if (node.valueRef.getWeight() > 0 && !isComputing(node)) {
        return node;
      }
    }
    return null;
  }

  // Reads

  /** {@code Map.get}: no statistics, but a stale read may trigger a refresh. */
  @Override
  public @Nullable V get(@Nullable Object key) {
    if (key == null) {
      return null;
    }
    long now = beginOperation();
    try {
      Node<K, V> node = getEntry(key);
      if (node == null) {
        return null;
      }
      V value = getLiveValue(node, now);
      if (value == null) {
        return null;
      }
      recordRead(node, now);
      return scheduleRefresh(node, node.getKey(), value, now, defaultLoader);
    } finally {
      endOperation();
    }
  }

  @Override
  public @Nullable V getOrDefault(@Nullable Object key, @Nullable V defaultValue) {
    V result = get(key);
    return (result != null) ? result : defaultValue;
  }

  @Nullable V getIfPresent(Object key) {
    V value = get(checkNotNull(key));
    if (value == null) {
      statsCounter.recordMisses(1);
    } else {
      statsCounter.recordHits(1);
    }
    return value;
  }

  V getOrLoad(K key) throws ExecutionException {
    return get(key, defaultLoader);
  }

  V get(K key, CacheLoader<? super K, V> loader) throws ExecutionException {
    checkNotNull(key);
    checkNotNull(loader);
    long now = beginOperation();
    try {
      Node<K, V> node = getEntry(key);
      if (node != null) {
        V value = getLiveValue(node, now);
        if (value != null) {
          recordRead(node, now);
          statsCounter.recordHits(1);
          return scheduleRefresh(node, key, value, now, loader);
        }
        if (node.valueRef.isLoading() && ((LoadingValue<V>) node.valueRef).running) {
          // Guava fails fast the same way (it would otherwise wait for itself).
          throw new IllegalStateException("Recursive load of: " + key);
        }
      }
      return lockedGetOrLoad(key, now, loader);
    } catch (ExecutionException ee) {
      Throwable cause = ee.getCause();
      if (cause instanceof Error) {
        throw new ExecutionError((Error) cause);
      } else if (cause instanceof RuntimeException) {
        throw new UncheckedExecutionException(cause);
      }
      throw ee;
    } finally {
      endOperation();
    }
  }

  private V lockedGetOrLoad(K key, long now, CacheLoader<? super K, V> loader)
      throws ExecutionException {
    Node<K, V> node = getEntry(key);
    LoadingValue<V> loading = new LoadingValue<>(unset(), false);
    if (node != null) {
      ValueRef<V> ref = node.valueRef;
      // A pending asynchronous refresh is superseded (a single thread cannot wait for it).
      ValueRef<V> current = ref.isLoading() ? ((LoadingValue<V>) ref).oldValue : ref;
      if (current.isActive()) {
        V value = current.get();
        if (value == null) {
          enqueueNotification(node.getKey(), null, current.getWeight(), RemovalCause.COLLECTED);
        } else if (isExpired(node, now)) {
          // unreachable after the maintenance that started this operation; Guava has the same check
          enqueueNotification(node.getKey(), value, current.getWeight(), RemovalCause.EXPIRED);
        } else {
          recordRead(node, now);
          statsCounter.recordHits(1);
          return value;
        }
        // immediately reuse invalid entries
        unlinkWrite(node);
        unlinkAccess(node);
        count--;
      }
      if (ref.isLoading()) {
        ((LoadingValue<V>) ref).clearOldValue();
      }
      node.valueRef = loading;
    } else {
      node = newNode(key);
      node.valueRef = loading;
      table.put(node.mapKey, node);
    }
    try {
      return loadSync(key, loading, loader);
    } finally {
      statsCounter.recordMisses(1);
    }
  }

  private V loadSync(K key, LoadingValue<V> loading, CacheLoader<? super K, V> loader)
      throws ExecutionException {
    ListenableFuture<V> future = loadFuture(key, loading, loader);
    return getAndRecordStats(key, loading, future);
  }

  /** Runs the loader (or reloader) for {@code loading}, returning its result as a future. */
  private ListenableFuture<V> loadFuture(
      K key, LoadingValue<V> loading, CacheLoader<? super K, V> loader) {
    loading.running = true;
    flushNotifications();
    loading.startClock();
    try {
      V previousValue = loading.oldValue.get();
      if (previousValue == null) {
        return Futures.immediateFuture(loader.load(key));
      }
      ListenableFuture<V> newValue = loader.reload(key, previousValue);
      return newValue == null ? Futures.immediateFuture(null) : newValue;
    } catch (Throwable t) {
      if (t instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return Futures.immediateFailedFuture(t);
    } finally {
      loading.running = false;
    }
  }

  private V getAndRecordStats(K key, LoadingValue<V> loading, Future<V> newValue)
      throws ExecutionException {
    V value = null;
    boolean stored = false;
    try {
      value = getUninterruptibly(newValue);
      if (value == null) {
        throw new InvalidCacheLoadException("CacheLoader returned null for key " + key + ".");
      }
      statsCounter.recordLoadSuccess(loading.elapsedNanos());
      storeLoadedValue(key, loading, value);
      stored = true;
      return value;
    } finally {
      if (value == null) {
        statsCounter.recordLoadException(loading.elapsedNanos());
        removeLoadingValue(key, loading);
      } else if (!stored) {
        // Lava: the weigher threw; Guava 27.0.1 leaves the entry loading forever.
        removeLoadingValue(key, loading);
      }
    }
  }

  /** Guava's {@code Uninterruptibles.getUninterruptibly}, for futures that are already done. */
  private static <V> V getUninterruptibly(Future<V> future) throws ExecutionException {
    boolean interrupted = false;
    try {
      while (true) {
        try {
          return future.get();
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void storeLoadedValue(K key, LoadingValue<V> loading, V newValue) {
    long now = ticker.read();
    runMaintenance(now);
    int newCount = count + 1;
    Node<K, V> node = getEntry(key);
    if (node != null) {
      ValueRef<V> ref = node.valueRef;
      V entryValue = ref.get();
      // replace the loading value if it is still in place, otherwise perform a putIfAbsent
      if (loading == ref || (entryValue == null && ref != UNSET)) {
        int weight = weigh(key, newValue);
        // Lava: account for the reference in the entry, which is not this load's when the load is
        // stale (Guava 27.0.1 uses this load's, and miscounts).
        if (ref.isActive()) {
          RemovalCause cause =
              (entryValue == null) ? RemovalCause.COLLECTED : RemovalCause.REPLACED;
          enqueueNotification(key, entryValue, ref.getWeight(), cause);
          newCount--;
        }
        setValue(node, newValue, weight, now);
        count = newCount;
        evictEntries(node);
        return;
      }
      // the loaded value was already clobbered
      enqueueNotification(key, newValue, 0, RemovalCause.REPLACED);
      return;
    }
    int weight = weigh(key, newValue);
    node = newNode(key);
    setValue(node, newValue, weight, now);
    table.put(node.mapKey, node);
    count = newCount;
    evictEntries(node);
  }

  private void removeLoadingValue(K key, LoadingValue<V> loading) {
    Node<K, V> node = getEntry(key);
    if (node == null || node.valueRef != loading) {
      return;
    }
    if (loading.isActive()) {
      node.valueRef = loading.oldValue;
    } else {
      table.remove(node.mapKey);
    }
  }

  // Refresh

  private V scheduleRefresh(
      Node<K, V> node, K key, V oldValue, long now, CacheLoader<? super K, V> loader) {
    if (refreshes()
        && (now - node.writeTime > refreshNanos)
        && !node.valueRef.isLoading()) {
      V newValue = refresh(key, loader, true);
      if (newValue != null) {
        return newValue;
      }
    }
    return oldValue;
  }

  /**
   * Starts a refresh of {@code key} and returns the new value if it completed synchronously and
   * successfully, else null. Failures are logged and swallowed.
   */
  private @Nullable V refresh(K key, CacheLoader<? super K, V> loader, boolean checkTime) {
    LoadingValue<V> loading = insertLoadingValueReference(key, checkTime);
    if (loading == null) {
      return null;
    }
    ListenableFuture<V> result = loadAsync(key, loading, loader);
    if (result.isDone()) {
      try {
        return getUninterruptibly(result);
      } catch (Throwable t) {
        // don't let refresh exceptions propagate; the error was already logged
      }
    }
    return null;
  }

  private @Nullable LoadingValue<V> insertLoadingValueReference(K key, boolean checkTime) {
    long now = ticker.read();
    runMaintenance(now);
    Node<K, V> node = getEntry(key);
    if (node != null) {
      ValueRef<V> ref = node.valueRef;
      if (ref.isLoading() || (checkTime && (now - node.writeTime < refreshNanos))) {
        // refresh is a no-op if loading is pending; with checkTime, re-check if still needed
        return null;
      }
      // continue returning the old value while loading
      LoadingValue<V> loading = new LoadingValue<>(ref, false);
      node.valueRef = loading;
      return loading;
    }
    LoadingValue<V> loading = new LoadingValue<>(unset(), false);
    node = newNode(key);
    node.valueRef = loading;
    table.put(node.mapKey, node);
    return loading;
  }

  private ListenableFuture<V> loadAsync(
      K key, LoadingValue<V> loading, CacheLoader<? super K, V> loader) {
    ListenableFuture<V> loadingFuture = loadFuture(key, loading, loader);
    loadingFuture.addListener(
        () -> completeRefresh(key, loading, loadingFuture), MoreExecutors.directExecutor());
    return loadingFuture;
  }

  /**
   * Stores (or records the failure of) a refresh once its future is done. This may run inside the
   * refreshing operation or later, from the executor that completes the future, so it runs as an
   * operation of its own.
   */
  private void completeRefresh(K key, LoadingValue<V> loading, ListenableFuture<V> future) {
    try {
      getAndRecordStats(key, loading, future);
    } catch (Throwable t) {
      logger.log(Level.WARNING, "Exception thrown during refresh", t);
    } finally {
      endOperation();
    }
  }

  void refresh(K key) {
    checkNotNull(key);
    try {
      refresh(key, defaultLoader, false);
    } finally {
      endOperation();
    }
  }

  // Bulk reads

  ImmutableMap<K, V> getAllPresent(Iterable<?> keys) {
    int hits = 0;
    int misses = 0;
    Map<K, V> result = new LinkedHashMap<>();
    for (Object key : keys) {
      V value = get(key);
      if (value == null) {
        misses++;
      } else {
        @SuppressWarnings("unchecked")
        K castKey = (K) key;
        result.put(castKey, value);
        hits++;
      }
    }
    statsCounter.recordHits(hits);
    statsCounter.recordMisses(misses);
    return ImmutableMap.copyOf(result);
  }

  ImmutableMap<K, V> getAll(Iterable<? extends K> keys) throws ExecutionException {
    int hits = 0;
    int misses = 0;
    Map<K, V> result = new LinkedHashMap<>();
    Set<K> keysToLoad = new LinkedHashSet<>();
    for (K key : keys) {
      V value = get(key);
      if (!result.containsKey(key)) {
        result.put(key, value);
        if (value == null) {
          misses++;
          keysToLoad.add(key);
        } else {
          hits++;
        }
      }
    }
    try {
      if (!keysToLoad.isEmpty()) {
        try {
          Map<K, V> newEntries = loadAll(keysToLoad, defaultLoader);
          for (K key : keysToLoad) {
            V value = newEntries.get(key);
            if (value == null) {
              throw new InvalidCacheLoadException("loadAll failed to return a value for " + key);
            }
            result.put(key, value);
          }
        } catch (UnsupportedLoadingOperationException e) {
          // loadAll not implemented, fallback to load
          for (K key : keysToLoad) {
            misses--; // get will count this miss
            result.put(key, get(key, defaultLoader));
          }
        }
      }
      return ImmutableMap.copyOf(result);
    } finally {
      statsCounter.recordHits(hits);
      statsCounter.recordMisses(misses);
    }
  }

  /**
   * Returns the result of calling {@link CacheLoader#loadAll}, or null if {@code loader} doesn't
   * implement {@code loadAll}.
   */
  private Map<K, V> loadAll(Set<? extends K> keys, CacheLoader<? super K, V> loader)
      throws ExecutionException {
    checkNotNull(loader);
    checkNotNull(keys);
    flushNotifications();
    long start = System.nanoTime(); // after the listeners, as in Guava
    Map<K, V> result;
    boolean success = false;
    try {
      @SuppressWarnings("unchecked") // safe since all keys extend K
      Map<K, V> map = (Map<K, V>) loader.loadAll(keys);
      result = map;
      success = true;
    } catch (UnsupportedLoadingOperationException e) {
      success = true;
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ExecutionException(e);
    } catch (RuntimeException e) {
      throw new UncheckedExecutionException(e);
    } catch (Exception e) {
      throw new ExecutionException(e);
    } catch (Error e) {
      throw new ExecutionError(e);
    } finally {
      if (!success) {
        statsCounter.recordLoadException(System.nanoTime() - start);
      }
    }

    if (result == null) {
      statsCounter.recordLoadException(System.nanoTime() - start);
      throw new InvalidCacheLoadException(loader + " returned null map from loadAll");
    }

    long elapsed = System.nanoTime() - start;
    boolean nullsPresent = false;
    for (Entry<K, V> entry : result.entrySet()) {
      K key = entry.getKey();
      V value = entry.getValue();
      if (key == null || value == null) {
        // delay failure until non-null entries are stored
        nullsPresent = true;
      } else {
        put(key, value);
      }
    }

    if (nullsPresent) {
      statsCounter.recordLoadException(elapsed);
      throw new InvalidCacheLoadException(loader + " returned null keys or values from loadAll");
    }

    statsCounter.recordLoadSuccess(elapsed);
    return result;
  }

  // Map queries

  @Override
  public boolean containsKey(@Nullable Object key) {
    // does not impact recency ordering
    if (key == null) {
      return false;
    }
    long now = beginOperation();
    try {
      Node<K, V> node = getEntry(key);
      return node != null && getLiveValue(node, now) != null;
    } finally {
      endOperation();
    }
  }

  @Override
  public boolean containsValue(@Nullable Object value) {
    // does not impact recency ordering
    if (value == null) {
      return false;
    }
    long now = beginOperation();
    try {
      for (Node<K, V> node : new ArrayList<>(table.values())) {
        V v = getLiveValue(node, now);
        if (v != null && valueEquivalence.equivalent(value, v)) {
          return true;
        }
      }
      return false;
    } finally {
      endOperation();
    }
  }

  @Override
  public boolean isEmpty() {
    return longSize() == 0;
  }

  long longSize() {
    beginOperation();
    try {
      return count;
    } finally {
      endOperation();
    }
  }

  @Override
  public int size() {
    return (int) Math.min(longSize(), Integer.MAX_VALUE);
  }

  // Writes

  @Override
  public V put(K key, V value) {
    checkNotNull(key);
    checkNotNull(value);
    return put(key, value, false);
  }

  @Override
  public V putIfAbsent(K key, V value) {
    checkNotNull(key);
    checkNotNull(value);
    return put(key, value, true);
  }

  private @Nullable V put(K key, V value, boolean onlyIfAbsent) {
    long now = beginOperation();
    try {
      Node<K, V> node = getEntry(key);
      if (node != null) {
        ValueRef<V> ref = node.valueRef;
        V entryValue = ref.get();
        if (entryValue == null) {
          int weight = weigh(key, value);
          if (ref.isActive()) {
            enqueueNotification(key, null, ref.getWeight(), RemovalCause.COLLECTED);
            setValue(node, value, weight, now); // count remains unchanged
          } else {
            setValue(node, value, weight, now);
            count++;
          }
          evictEntries(node);
          return null;
        } else if (onlyIfAbsent) {
          // Mimic "if (!map.containsKey(key)) ... else return map.get(key);"
          recordRead(node, now);
          return entryValue;
        } else {
          // clobber existing entry, count remains unchanged
          int weight = weigh(key, value);
          enqueueNotification(key, entryValue, ref.getWeight(), RemovalCause.REPLACED);
          setValue(node, value, weight, now);
          evictEntries(node);
          return entryValue;
        }
      }
      int weight = weigh(key, value);
      node = newNode(key);
      setValue(node, value, weight, now);
      table.put(node.mapKey, node);
      count++;
      evictEntries(node);
      return null;
    } finally {
      endOperation();
    }
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> m) {
    for (Entry<? extends K, ? extends V> e : m.entrySet()) {
      put(e.getKey(), e.getValue());
    }
  }

  @Override
  public boolean replace(K key, @Nullable V oldValue, V newValue) {
    checkNotNull(key);
    checkNotNull(newValue);
    if (oldValue == null) {
      return false;
    }
    long now = beginOperation();
    try {
      Node<K, V> node = getEntry(key);
      if (node == null) {
        return false;
      }
      ValueRef<V> ref = node.valueRef;
      V entryValue = ref.get();
      if (entryValue == null) {
        if (ref.isActive()) {
          // If the value disappeared, this entry is partially collected.
          removeEntry(node, RemovalCause.COLLECTED);
        }
        return false;
      }
      if (valueEquivalence.equivalent(oldValue, entryValue)) {
        int weight = weigh(key, newValue);
        enqueueNotification(key, entryValue, ref.getWeight(), RemovalCause.REPLACED);
        setValue(node, newValue, weight, now);
        evictEntries(node);
        return true;
      }
      // Mimic "if (map.containsKey(key) && map.get(key).equals(oldValue))..."
      recordRead(node, now);
      return false;
    } finally {
      endOperation();
    }
  }

  @Override
  public @Nullable V replace(K key, V value) {
    checkNotNull(key);
    checkNotNull(value);
    long now = beginOperation();
    try {
      Node<K, V> node = getEntry(key);
      if (node == null) {
        return null;
      }
      ValueRef<V> ref = node.valueRef;
      V entryValue = ref.get();
      if (entryValue == null) {
        if (ref.isActive()) {
          removeEntry(node, RemovalCause.COLLECTED);
        }
        return null;
      }
      int weight = weigh(key, value);
      enqueueNotification(key, entryValue, ref.getWeight(), RemovalCause.REPLACED);
      setValue(node, value, weight, now);
      evictEntries(node);
      return entryValue;
    } finally {
      endOperation();
    }
  }

  @Override
  public @Nullable V remove(@Nullable Object key) {
    if (key == null) {
      return null;
    }
    beginOperation();
    try {
      Node<K, V> node = getEntry(key);
      if (node == null) {
        return null;
      }
      ValueRef<V> ref = node.valueRef;
      V entryValue = ref.get();
      RemovalCause cause;
      if (entryValue != null) {
        cause = RemovalCause.EXPLICIT;
      } else if (ref.isActive()) {
        cause = RemovalCause.COLLECTED;
      } else {
        return null; // currently loading
      }
      removeValueFromChain(node, node.getKey(), entryValue, ref, cause);
      count--;
      return entryValue;
    } finally {
      endOperation();
    }
  }

  @Override
  public boolean remove(@Nullable Object key, @Nullable Object value) {
    if (key == null || value == null) {
      return false;
    }
    beginOperation();
    try {
      Node<K, V> node = getEntry(key);
      if (node == null) {
        return false;
      }
      ValueRef<V> ref = node.valueRef;
      V entryValue = ref.get();
      RemovalCause cause;
      if (valueEquivalence.equivalent(value, entryValue)) {
        cause = RemovalCause.EXPLICIT;
      } else if (entryValue == null && ref.isActive()) {
        cause = RemovalCause.COLLECTED;
      } else {
        return false; // currently loading, or a different value
      }
      removeValueFromChain(node, node.getKey(), entryValue, ref, cause);
      count--;
      return cause == RemovalCause.EXPLICIT;
    } finally {
      endOperation();
    }
  }

  @Override
  public void clear() {
    beginOperation();
    try {
      if (count == 0) {
        return;
      }
      for (Node<K, V> node : new ArrayList<>(table.values())) {
        // Loading references aren't actually in the map yet.
        ValueRef<V> ref = node.valueRef;
        if (ref.isActive()) {
          K key = node.getKey();
          V value = ref.get();
          RemovalCause cause =
              (key == null || value == null) ? RemovalCause.COLLECTED : RemovalCause.EXPLICIT;
          enqueueNotification(key, value, ref.getWeight(), cause);
        }
        unlinkWrite(node);
        unlinkAccess(node);
      }
      table.clear();
      clearReferenceQueues();
      count = 0;
    } finally {
      endOperation();
    }
  }

  private void clearReferenceQueues() {
    if (keyReferenceQueue != null) {
      while (keyReferenceQueue.poll() != null) {}
    }
    if (valueReferenceQueue != null) {
      while (valueReferenceQueue.poll() != null) {}
    }
  }

  void invalidateAll(Iterable<?> keys) {
    for (Object key : keys) {
      remove(key);
    }
  }

  void cleanUp() {
    beginOperation();
    endOperation();
  }

  // Compute

  @Override
  public V compute(K key, BiFunction<? super K, ? super V, ? extends V> function) {
    checkNotNull(key);
    checkNotNull(function);
    long now = beginOperation();
    try {
      Node<K, V> node = getEntry(key);
      ValueRef<V> valueReference = null;
      boolean createNewEntry = node == null;
      if (node != null) {
        valueReference = node.valueRef;
        if (valueReference.isLoading() && ((LoadingValue<V>) valueReference).running) {
          // Guava would wait for this thread's own load forever.
          throw new IllegalStateException("Recursive load of: " + key);
        }
      }
      // valueReference may be an existing value or a pending refresh, whose old value is seen
      LoadingValue<V> loading =
          new LoadingValue<>(valueReference == null ? unset() : valueReference, true);
      if (createNewEntry) {
        node = newNode(key);
        table.put(node.mapKey, node);
      }
      node.valueRef = loading;
      V newValue;
      loading.running = true;
      flushNotifications();
      loading.startClock();
      try {
        newValue = function.apply(key, loading.get());
      } catch (Throwable t) {
        // Lava: restore the entry (Guava 27.0.1 leaves it stuck in a loading state), unless the
        // function removed it, which already cleared the old value and updated the counts.
        if (node.valueRef == loading && isLinked(node)) {
          if (createNewEntry || !loading.isActive()) {
            table.remove(node.mapKey);
          } else {
            node.valueRef = valueReference;
          }
        }
        throw t;
      } finally {
        loading.running = false;
      }
      if (newValue != null) {
        if (valueReference != null
            && newValue == valueReference.get()
            && loading.oldValue == valueReference // not removed by the function
            && node.valueRef == loading
            && isLinked(node)) {
          node.valueRef = valueReference;
          recordWrite(node, 0, now); // no change in weight
          return newValue;
        }
        try {
          return getAndRecordStats(key, loading, Futures.immediateFuture(newValue));
        } catch (ExecutionException exception) {
          throw new AssertionError("impossible; Futures.immediateFuture can't throw");
        }
      } else if (createNewEntry) {
        removeLoadingValue(key, loading);
        return null;
      } else {
        // Lava: remove the entry (Guava 27.0.1 leaves a loading entry behind). As in Guava, this
        // also removes a value that the function itself wrote for the key.
        if (isLinked(node)) {
          ValueRef<V> current = node.valueRef;
          if (current.isActive()) {
            enqueueNotification(
                node.getKey(), current.get(), current.getWeight(), RemovalCause.EXPLICIT);
            count--;
          }
          unlinkWrite(node);
          unlinkAccess(node);
          table.remove(node.mapKey);
        }
        return null;
      }
    } finally {
      endOperation();
    }
  }

  @Override
  public V computeIfAbsent(K key, Function<? super K, ? extends V> function) {
    checkNotNull(key);
    checkNotNull(function);
    return compute(key, (k, oldValue) -> (oldValue == null) ? function.apply(key) : oldValue);
  }

  @Override
  public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> function) {
    checkNotNull(key);
    checkNotNull(function);
    return compute(key, (k, oldValue) -> (oldValue == null) ? null : function.apply(k, oldValue));
  }

  @Override
  public V merge(K key, V newValue, BiFunction<? super V, ? super V, ? extends V> function) {
    checkNotNull(key);
    checkNotNull(newValue);
    checkNotNull(function);
    return compute(
        key, (k, oldValue) -> (oldValue == null) ? newValue : function.apply(oldValue, newValue));
  }

  boolean removeIf(BiPredicate<? super K, ? super V> filter) {
    checkNotNull(filter);
    boolean changed = false;
    for (K key : keySet()) {
      while (true) {
        V value = get(key);
        if (value == null || !filter.test(key, value)) {
          break;
        } else if (LocalCache.this.remove(key, value)) {
          changed = true;
          break;
        }
      }
    }
    return changed;
  }

  // Statistics

  CacheStats stats() {
    SimpleStatsCounter aggregator = new SimpleStatsCounter();
    aggregator.incrementBy(statsCounter);
    return aggregator.snapshot();
  }

  // Test hooks: simulate garbage collection of a reference.

  void simulateValueReclamation(Object key) {
    Node<K, V> node = getEntry(key);
    if (node != null && node.valueRef instanceof Reference) {
      Reference<?> ref = (Reference<?>) node.valueRef;
      ref.clear();
      ref.enqueue();
    }
  }

  void simulateKeyReclamation(Object key) {
    Node<K, V> node = getEntry(key);
    if (node != null && node.mapKey instanceof Reference) {
      Reference<?> ref = (Reference<?>) node.mapKey;
      ref.clear();
      ref.enqueue();
    }
  }

  // Views

  private @Nullable Set<K> keySet;
  private @Nullable Collection<V> values;
  private @Nullable Set<Entry<K, V>> entrySet;

  @Override
  public Set<K> keySet() {
    Set<K> ks = keySet;
    return (ks != null) ? ks : (keySet = new KeySet());
  }

  @Override
  public Collection<V> values() {
    Collection<V> vs = values;
    return (vs != null) ? vs : (values = new Values());
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    Set<Entry<K, V>> es = entrySet;
    return (es != null) ? es : (entrySet = new EntrySet());
  }

  /**
   * Iterates over a snapshot of the entries, skipping any that are no longer live when reached.
   * Never throws {@code ConcurrentModificationException}; order is unspecified.
   */
  abstract class HashIterator<T> implements Iterator<T> {
    private final Iterator<Node<K, V>> nodes = new ArrayList<>(table.values()).iterator();
    @Nullable WriteThroughEntry nextExternal;
    @Nullable WriteThroughEntry lastReturned;

    HashIterator() {
      advance();
    }

    @Override
    public abstract T next();

    final void advance() {
      nextExternal = null;
      while (nodes.hasNext()) {
        Node<K, V> node = nodes.next();
        if (isLinked(node)) {
          K key = node.getKey();
          V value = getLiveValue(node, ticker.read());
          if (key != null && value != null) {
            nextExternal = new WriteThroughEntry(key, value);
            return;
          }
        }
      }
    }

    @Override
    public boolean hasNext() {
      return nextExternal != null;
    }

    WriteThroughEntry nextEntry() {
      if (nextExternal == null) {
        throw new NoSuchElementException();
      }
      lastReturned = nextExternal;
      advance();
      return lastReturned;
    }

    @Override
    public void remove() {
      checkState(lastReturned != null);
      LocalCache.this.remove(lastReturned.getKey());
      lastReturned = null;
    }
  }

  final class KeyIterator extends HashIterator<K> {
    @Override
    public K next() {
      return nextEntry().getKey();
    }
  }

  final class ValueIterator extends HashIterator<V> {
    @Override
    public V next() {
      return nextEntry().getValue();
    }
  }

  /** An entry whose {@code setValue} writes through to the cache, as in Guava. */
  final class WriteThroughEntry implements Entry<K, V> {
    final K key; // non-null
    V value; // non-null

    WriteThroughEntry(K key, V value) {
      this.key = key;
      this.value = value;
    }

    @Override
    public K getKey() {
      return key;
    }

    @Override
    public V getValue() {
      return value;
    }

    @Override
    public boolean equals(@Nullable Object object) {
      // Cannot use key and value equivalence
      if (object instanceof Entry) {
        Entry<?, ?> that = (Entry<?, ?>) object;
        return key.equals(that.getKey()) && value.equals(that.getValue());
      }
      return false;
    }

    @Override
    public int hashCode() {
      // Cannot use key and value equivalence
      return key.hashCode() ^ value.hashCode();
    }

    @Override
    public V setValue(V newValue) {
      V oldValue = put(key, newValue);
      value = newValue; // only if put succeeds
      return oldValue;
    }

    @Override
    public String toString() {
      return getKey() + "=" + getValue();
    }
  }

  final class EntryIterator extends HashIterator<Entry<K, V>> {
    @Override
    public Entry<K, V> next() {
      return nextEntry();
    }
  }

  private static <E> ArrayList<E> toArrayList(Collection<E> c) {
    // Avoid calling ArrayList(Collection), which may call back into toArray.
    ArrayList<E> result = new ArrayList<>();
    for (E e : c) {
      result.add(e);
    }
    return result;
  }

  abstract class AbstractCacheSet<T> extends AbstractSet<T> {
    @Override
    public int size() {
      return LocalCache.this.size();
    }

    @Override
    public boolean isEmpty() {
      return LocalCache.this.isEmpty();
    }

    @Override
    public void clear() {
      LocalCache.this.clear();
    }

    @Override
    public Object[] toArray() {
      return toArrayList(this).toArray();
    }

    @Override
    public <E> E[] toArray(E[] a) {
      return toArrayList(this).toArray(a);
    }
  }

  final class KeySet extends AbstractCacheSet<K> {
    @Override
    public Iterator<K> iterator() {
      return new KeyIterator();
    }

    @Override
    public boolean contains(Object o) {
      return LocalCache.this.containsKey(o);
    }

    @Override
    public boolean remove(Object o) {
      return LocalCache.this.remove(o) != null;
    }
  }

  final class Values extends AbstractCollection<V> {
    @Override
    public int size() {
      return LocalCache.this.size();
    }

    @Override
    public boolean isEmpty() {
      return LocalCache.this.isEmpty();
    }

    @Override
    public void clear() {
      LocalCache.this.clear();
    }

    @Override
    public Iterator<V> iterator() {
      return new ValueIterator();
    }

    @Override
    public boolean removeIf(Predicate<? super V> filter) {
      checkNotNull(filter);
      return LocalCache.this.removeIf((k, v) -> filter.test(v));
    }

    @Override
    public boolean contains(Object o) {
      return LocalCache.this.containsValue(o);
    }

    @Override
    public Object[] toArray() {
      return toArrayList(this).toArray();
    }

    @Override
    public <E> E[] toArray(E[] a) {
      return toArrayList(this).toArray(a);
    }
  }

  final class EntrySet extends AbstractCacheSet<Entry<K, V>> {
    @Override
    public Iterator<Entry<K, V>> iterator() {
      return new EntryIterator();
    }

    @Override
    public boolean removeIf(Predicate<? super Entry<K, V>> filter) {
      checkNotNull(filter);
      return LocalCache.this.removeIf(
          (k, v) -> filter.test(new AbstractMap.SimpleImmutableEntry<>(k, v)));
    }

    @Override
    public boolean contains(Object o) {
      if (!(o instanceof Entry)) {
        return false;
      }
      Entry<?, ?> e = (Entry<?, ?>) o;
      Object key = e.getKey();
      if (key == null) {
        return false;
      }
      V v = LocalCache.this.get(key);
      return v != null && valueEquivalence.equivalent(e.getValue(), v);
    }

    @Override
    public boolean remove(Object o) {
      if (!(o instanceof Entry)) {
        return false;
      }
      Entry<?, ?> e = (Entry<?, ?>) o;
      Object key = e.getKey();
      return key != null && LocalCache.this.remove(key, e.getValue());
    }
  }

  // Cache facades

  static class LocalManualCache<K, V> implements Cache<K, V> {
    final LocalCache<K, V> localCache;

    LocalManualCache(CacheBuilder<? super K, ? super V> builder) {
      this(new LocalCache<K, V>(builder, null));
    }

    private LocalManualCache(LocalCache<K, V> localCache) {
      this.localCache = localCache;
    }

    @Override
    public @Nullable V getIfPresent(Object key) {
      return localCache.getIfPresent(key);
    }

    @Override
    public V get(K key, final Callable<? extends V> valueLoader) throws ExecutionException {
      checkNotNull(valueLoader);
      return localCache.get(
          key,
          new CacheLoader<Object, V>() {
            @Override
            public V load(Object key) throws Exception {
              return valueLoader.call();
            }
          });
    }

    @Override
    public ImmutableMap<K, V> getAllPresent(Iterable<?> keys) {
      return localCache.getAllPresent(keys);
    }

    @Override
    public void put(K key, V value) {
      localCache.put(key, value);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
      localCache.putAll(m);
    }

    @Override
    public void invalidate(Object key) {
      checkNotNull(key);
      localCache.remove(key);
    }

    @Override
    public void invalidateAll(Iterable<?> keys) {
      localCache.invalidateAll(keys);
    }

    @Override
    public void invalidateAll() {
      localCache.clear();
    }

    @Override
    public long size() {
      return localCache.longSize();
    }

    @Override
    public ConcurrentMap<K, V> asMap() {
      return localCache;
    }

    @Override
    public CacheStats stats() {
      return localCache.stats();
    }

    @Override
    public void cleanUp() {
      localCache.cleanUp();
    }
  }

  static class LocalLoadingCache<K, V> extends LocalManualCache<K, V>
      implements LoadingCache<K, V> {

    LocalLoadingCache(
        CacheBuilder<? super K, ? super V> builder, CacheLoader<? super K, V> loader) {
      super(new LocalCache<K, V>(builder, checkNotNull(loader)));
    }

    @Override
    public V get(K key) throws ExecutionException {
      return localCache.getOrLoad(key);
    }

    @Override
    public V getUnchecked(K key) {
      try {
        return get(key);
      } catch (ExecutionException e) {
        throw new UncheckedExecutionException(e.getCause());
      }
    }

    @Override
    public ImmutableMap<K, V> getAll(Iterable<? extends K> keys) throws ExecutionException {
      return localCache.getAll(keys);
    }

    @Override
    public void refresh(K key) {
      localCache.refresh(key);
    }

    @SuppressWarnings("deprecation") // overrides the deprecated LoadingCache.apply
    @Override
    public final V apply(K key) {
      return getUnchecked(key);
    }
  }
}
