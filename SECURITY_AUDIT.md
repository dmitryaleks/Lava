# Lava security audit

| | |
|---|---|
| Scope | All main sources at commit `62ee086`: 127 classes in 7 packages (`base`, `cache`, `checker`, `collect`, `eventbus`, `primitives`, `util.concurrent`), plus the build (`build.gradle.kts`, the Gradle wrapper, dependencies) |
| Date | 2026-10-17 |
| Reference | Guava 27.0.1 sources, for behaviour Lava inherits |
| Method | Manual review of every class, by risk class (below), and a reflective sweep of the 91 `Serializable` classes: serialization hooks and serializable fields. Checks against the Guava 27.0.1 sources, and checksums of the build tooling against gradle.org. |
| Not done | No exploit proof-of-concept code was written. Findings rest on code inspection and the sweep, and their impact is argued, not demonstrated. Only Windows 11 with JDK 17 was used. |
| Fixes | Made after the audit, in the commit that adds this file; each finding records its resolution |

## Threat model

Lava is an in-process library. It does no file, network, process, JNDI, scripting, native or `Unsafe` access, loads no classes dynamically, and reads no system properties or environment variables (verified by search). Its attack surface is therefore:

1. **Untrusted data passed to its APIs**: keys and values, strings to parse, configuration specs.
2. **Untrusted Java serialization streams**, if an application deserializes them.
3. **Use against its single-threaded contract.** Lava is a drop-in replacement for Guava classes that *are* thread-safe, and migration changes only imports.
4. **The build supply chain.**

Risk classes reviewed:
- deserialization: forged instances, allocation, gadget behaviour;
- denial of service: allocation, algorithmic complexity, regular expressions, recursion, integer overflow;
- concurrency and static shared state;
- reflection;
- information exposure and log injection;
- supply chain.

## Summary

Severity reflects impact in a realistic deployment. "Inherited" means Guava 27.0.1 has the same weakness. The audit was made at `62ee086`. The fixes landed together with this file; see **Resolution** under each finding and the [verification](#verification-of-the-fixes) section.

| ID | Severity | Finding | Origin | Status |
|---|---|---|---|---|
| SA-01 | **High** | `Enums.getIfPresent` uses a global, unsynchronized `WeakHashMap` cache | Lava-specific | **Fixed** |
| SA-02 | **High** (misuse) | Thread-safe Guava types are replaced by non-thread-safe ones, with no runtime guard | Lava design | **Mitigated** |
| SA-03 | Medium | The event bus delivers another thread's event on the dispatching thread, with that thread's context | Lava-specific | **Fixed** |
| SA-04 | Medium | `ImmutableSetMultimap` deserialization pre-allocates from a count read from the stream | Lava regression | **Fixed** |
| SA-05 | Medium | Immutable set and map classes can be deserialized without `SerializedForm` validation | Inherited | **Fixed** |
| SA-06 | Medium (conditional) | Serializable views run user-supplied functions during deserialization | Inherited | Documented |
| SA-07 | Low | Default deserialization does not re-check invariants (`Range`, `Doubles.asList` and others) | Inherited | **Fixed** (main cases) |
| SA-08 | Low | Colliding hash codes degrade cache and `HashBiMap` operations to linear time | Partly inherited | **Fixed** for caches |
| SA-09 | Low | Configuration strings drive large allocations or unbounded caches | Inherited | Documented |
| SA-10 | Low | Integer overflow in `Doubles.concat` and `Doubles.join` | Inherited | **Fixed** |
| SA-11 | Low | User data in exception messages and logs; log injection | Inherited | Documented |
| SA-12 | Info | The event bus calls `setAccessible(true)` on subscriber methods | Inherited (by design) | Documented |
| SA-13 | Info | Unbounded recursion in `toString` of deeply nested values | Inherited | Accepted |
| SA-14 | Low | Supply chain: stale wrapper JAR, unpinned distribution, no dependency verification | Build | **Fixed** |

---

## Findings

### SA-01 — `Enums.getIfPresent` uses a global, unsynchronized cache (High)

**Location:** `base/Enums.java:81-101` (`enumConstantCache`, `populateCache`, `getEnumConstants`).

**Description:** `getIfPresent` reads and fills a `private static final WeakHashMap` without synchronization. The code comment says this is safe "because Lava is single-threaded"; Guava synchronizes the same cache. Lava's thread-confinement rule doesn't work here: you can confine an object to a thread, but not a static method. A multi-threaded application has no way to use `Enums.getIfPresent` safely, short of a global lock of its own. Typical uses, such as parsing request parameters in server threads, are exactly the multi-threaded case.

**Impact:** concurrent unsynchronized puts and reads can corrupt a `WeakHashMap` while it resizes:
- lost or misplaced entries, which are benign here because the cache is refilled;
- unexpected runtime exceptions;
- in the worst case, corrupted bucket chains that make a lookup loop forever, which pins a CPU core and hangs the request (availability).

**Recommendation:** make the cache thread-safe. Synchronizing on the map (as Guava does) is the minimal change. Alternatives are a `ClassValue` holding the per-class map of weak references, which is thread-safe and does not pin classes, or no cache at all: scan `getEnumConstants()`. In any case, audit every static field for shared mutable state; this audit found no other. The event bus's `ClassValue` caches are thread-safe.

**Resolution (fixed):** the cache is synchronized on the map, as in Guava. The per-class maps are never modified once published. The class Javadoc now says that `Enums` is safe to use from several threads. Test: `SecurityAuditTest.enumLookupsAreSafeFromSeveralThreads`.

### SA-02 — Thread-safe Guava types become non-thread-safe Lava types (High, misuse)

**Location:**
- `cache.*`: `LocalCache` and its views;
- `eventbus.EventBus`, `AsyncEventBus` and `Dispatcher`;
- `util.concurrent.ExecutionList` and `ListenableFutureTask`;
- `CacheLoader.asyncReloading` and `RemovalListeners.asynchronous`.

**Description:** Guava's `Cache`, `EventBus` and `ListenableFutureTask` are documented as thread-safe, and are routinely shared between threads. Lava keeps their APIs, so code migrates by changing imports, but it removes all synchronization. README, the package docs and class Javadoc say so, but nothing enforces it at run time. The executor-taking APIs (`AsyncEventBus`, `asyncReloading`, `RemovalListeners.asynchronous`, `ListenableFutureTask`) are designed for thread pools. With a real pool they race.

**Impact:** data races corrupt internal state:
- cache counts and LRU lists;
- the `HashMap` table, whose tree bins can loop forever under concurrent modification;
- lost or duplicated listener runs.

The consequences include:
- values that are visible after `invalidate`;
- size and weight bounds that no longer hold, so memory grows without limit;
- callbacks that never run, for example cleanup or audit hooks;
- requests that hang.

The failures are silent and depend on timing, which makes them hard to detect in testing.

**Recommendation:**
- Fail fast on cross-thread use: record the owner thread at construction or first use, and throw `IllegalStateException` from any other thread. The check is a single field comparison per operation. Apply it to caches, event buses and `ExecutionList`, possibly behind a switch.
- Keep the warning at the top of each affected class's Javadoc, and add it to the migration notes for each executor-taking factory.

**Resolution (mitigated):**
- **Caches:** they now detect use by a second thread while an operation is running, and throw `ConcurrentModificationException`.
  - Nested operations on the same thread are allowed: loaders, listeners and compute functions.
  - Handing a cache to another thread between operations is allowed.
  - The detection is best-effort, like the JDK's. `CacheTesting` checks after every fuzz step that each operation releases it.
- **Executor-facing classes:** these are made thread-safe as in Guava, because they exist to hand work to executors.
  - `ExecutionList` is synchronized, so `ListenableFutureTask` is safe on a thread pool.
  - The dispatch queue of `AsyncEventBus` is a `ConcurrentLinkedQueue`.
  - Events are delivered on the posting thread (SA-03).
- **Still not thread-safe, by design and documented:** caches, event-bus registration and mutable views. The README's thread-safety note lists what is safe.
- **Tests:** `aCacheUsedFromTwoThreadsAtOnceFailsFast`, `listenableFutureTasksRunEveryListenerOnceOnAThreadPool`.

### SA-03 — An event posted from another thread runs with the dispatching thread's context (Medium)

**Location:** `eventbus/Dispatcher.java`, the per-thread dispatch queue, which keeps its queue and `dispatching` flag in plain fields shared by all threads.

**Description:** in Guava the "per-thread" dispatcher is per thread: each posting thread delivers its own events. In Lava, while one thread is dispatching, a `post` from a second thread only enqueues its event and returns at once. The first thread later delivers it. This is documented in the `EventBus` Javadoc as "accidental cross-thread use", but the consequence is security-relevant: the subscriber runs on the wrong thread, and sees that thread's `ThreadLocal` state. That state includes security principals, tenant IDs, transactions and logging context (MDC).

**Impact:** a subscriber can authorize or audit one user's event under another user's identity. The posting thread also observes `post()` returning before delivery, which breaks "post, then rely on the side effects".

**Recommendation:** keep the queue and flag in a `ThreadLocal`, as Guava does. On the owning thread the behaviour is identical, and under misuse it stays correct. Or apply the owner-thread check from SA-02.

**Resolution (fixed):** the queue and the dispatching flag are `ThreadLocal`s, as in Guava, so every event is delivered on the thread that posts it, before `post` returns. Non-`@AllowConcurrentEvents` subscribers stay serialized by `SynchronizedSubscriber`, as in Guava. Test: `eventsAreDeliveredOnThePostingThreadWithItsContext`.

### SA-04 — `ImmutableSetMultimap` deserialization pre-allocates from the stream (Medium, Lava regression)

**Location:** `collect/ImmutableSetMultimap.java:625`: `List<Object> values = new ArrayList<>(valueCount);`

**Description:** `readObject` reads `valueCount` from the stream, checks only that it is positive, and allocates an array of that size before reading a single value. A stream of a few dozen bytes can request close to 2<sup>31</sup> references, which is several gigabytes. Guava 27.0.1 collects the values with a builder that grows with the data actually present, so this is a regression introduced by the port. It is the same weakness class as CWE-789 and CVE-2018-10237 (Guava 24.1.1), where a stream-supplied size was trusted.

**Impact:** an application that deserializes untrusted data can be driven to `OutOfMemoryError`, a denial of service for the whole JVM.

**Recommendation:** do not presize from stream data: use `new ArrayList<>()`, or cap the initial capacity (for example `Math.min(valueCount, 16)`). Add a regression test with an oversized count. `keyCount` is already safe, because `ImmutableMap.builder()` grows with the data, and so is `HashBiMap.readObject`, which uses `init(16)`.

**Resolution (fixed):** the values are read by `readValues`, which grows its list with the data actually present. Test: `multimapDeserializationDoesNotTrustTheValueCount`, in which a count far beyond the data fails with an `IOException`, not an allocation.

### SA-05 — Immutable sets and maps can be deserialized without validation (Medium, inherited)

**Location:**
- `collect/ImmutableCollection`, `ImmutableSet`, `ImmutableMap` and their subclasses:
  - `RegularImmutableSet`, `JdkBackedImmutableSet`, `IndexedImmutableSet`, `SingletonImmutableSet`, `ImmutableEnumSet`;
  - `RegularImmutableMap`, `JdkBackedImmutableMap`, `SingletonImmutableMap`, `ImmutableEnumMap`;
  - the key, value and entry-set views.
- Compare `ImmutableList.java:686` and `ImmutableAsList.java:73`, which have the guard.

**Description:** these classes serialize through `writeReplace` to a `SerializedForm`, whose `readResolve` rebuilds the collection through the validating builders, which check nulls and duplicates. The guard against the reverse path is missing: a `readObject` that throws `InvalidObjectException("Use SerializedForm")`. Only the `ImmutableList` hierarchy has it. A hand-crafted stream can therefore instantiate the set and map classes directly, without any validation:
- Most of them have only transient state, and come back with null internals. They throw `NullPointerException` when used.
- `JdkBackedImmutableSet` has two serializable fields, `delegate` (a `Set`) and `delegateList` (an `ImmutableList`), restored independently:
  - `contains` can disagree with iteration, `size()` and `equals`;
  - `delegate` can be a mutable set referenced elsewhere in the same stream, so the "immutable" set can change after deserialization.
- `ImmutableSet.copyOf` and `ImmutableMap.copyOf` return any non-partial instance unchanged, so a defensive copy of such an object does not help.

Guava 27.0.1 has the same gap: it guards only `ImmutableList`, `ImmutableAsList` and `ImmutableSortedSet`. Later Guava releases added the guard to many more classes.

**Impact:** for applications that deserialize untrusted data, "immutable" values may be inconsistent or change later. This can defeat validation done by one access path (for example iteration) and relied on by another (for example `contains`). The remaining classes give crashes.

**Recommendation:** add the `readObject` guard to `ImmutableCollection` and `ImmutableMap`, which covers all their subclasses. The `SerializedForm` classes are separate types and keep working. Check that `ImmutableSetMultimap`, which uses its own `readObject`, is unaffected, because it is not an `ImmutableCollection`. Add tests that the guard is present.

**Resolution (fixed):** `ImmutableCollection` and `ImmutableMap` declare the `readObject` guard, which covers every immutable set, map and view. Ordinary serialization is unchanged.
- `immutableSetsAndMapsRejectDirectDeserialization` checks the guards and the round trips.
- `everyClassSerializedThroughAProxyIsGuarded` scans all main classes. The only class that serializes through a proxy without a guard is `HashBiMap.Inverse`, a view whose only state is a `HashBiMap`, and `HashBiMap` validates itself in its own `readObject`.

### SA-06 — Serializable views run user-supplied code during deserialization (Medium, conditional, inherited)

**Location:**
- `Lists.transform` views (`TransformingRandomAccessList`, `TransformingSequentialList`: a `Function`);
- `Converter.from`, `andThen` and `reverse` (`FunctionBasedConverter`, `ConverterComposition`, `ReverseConverter`);
- `Equivalence.onResultOf`, `wrap` and `equivalentTo` (`FunctionalEquivalence`, `Equivalence.Wrapper`, `EquivalentToPredicate`);
- `Maps.asConverter`;
- `Range` and `Cut` (the endpoints' `compareTo`);
- `Enums.stringConverter`: `Enum.valueOf` on a class named in the stream, which initializes that enum class.

**Description:** these classes are `Serializable`, as in Guava. They hold functions, comparables or classes that come from the stream, and call them from `hashCode`, `equals`, `compareTo` or `toString`. Deserializing a `HashMap`, `HashSet` or `TreeMap` that contains such objects invokes those methods during deserialization. That is the "trigger" half of a classic gadget chain. Lava contains no function that invokes arbitrary methods, so Lava alone gives no code execution. But these classes can extend gadget chains built from other libraries on the classpath.

**Impact:** depends on the classpath. In the worst case, combined with a dangerous serializable function elsewhere, deserializing untrusted data executes attacker-chosen code.

**Recommendation:**
- Do not deserialize untrusted data with Java serialization.
- Where it cannot be avoided, install an `ObjectInputFilter` allow-list (JEP 290) that rejects these view, converter and equivalence classes.
- State this in the package docs and the README, next to the existing note that Lava's serialized forms are not wire-compatible with Guava's.

**Resolution (documented):** the serializable API is kept for Guava compatibility. The `collect` package docs and the README now warn against deserializing untrusted data and recommend an `ObjectInputFilter` allow-list.

### SA-07 — Default deserialization does not re-check invariants (Low, inherited)

**Location:**
- `collect/Range.java:650` (`readResolve` does not re-check `lower ≤ upper`), and `Cut`;
- `primitives/Doubles.java:499` (`DoubleArrayAsList`: `array`, `start`, `end` are not validated);
- `Lists.asList` (`OnePlusArrayList`, `TwoPlusArrayList`);
- the `Maps.unmodifiable*` and `Sets.unmodifiable*` wrappers.

**Description and impact:** these classes use default serialization and never re-validate their constructor invariants:
- a forged `Range` can be inverted and contain nothing;
- a forged `Doubles.asList` view can report a negative size or index outside its array, so it throws instead of reading out of bounds.

The result is unexpected exceptions or wrong answers, but no memory unsafety.

**Recommendation:** validate in `readObject`/`readResolve` and throw `InvalidObjectException`. For `Range`, compare the cuts; for `DoubleArrayAsList`, check `0 ≤ start ≤ end ≤ array.length`.

**Resolution (fixed for the main cases):**
- `Range.readResolve` re-runs the constructor's checks: ordered, non-null bounds whose endpoints are present.
- `Doubles.asList` views check that they lie within their array.
- `Lists.asList` views check that their array is present.
- All three throw `InvalidObjectException`.
- Not changed: the `Maps` and `Sets` unmodifiable wrappers only hold a delegate, and a missing one fails on first use.
- Tests: `deserializedRangesAreRevalidated`, `deserializedArrayViewsAreRevalidated`.

### SA-08 — Colliding hash codes degrade caches and `HashBiMap` (Low, partly inherited)

**Location:** `cache/LocalCache.java:265` (`StrongKey` and `WeakKey` wrap every key in the table), and `collect/HashBiMap.java` (chained buckets).

**Description:** `LocalCache` stores entries in a `HashMap` keyed by wrapper objects that are not `Comparable`. `HashMap`'s tree bins keep colliding keys searchable in O(log n) only when the keys themselves are mutually `Comparable`. Behind wrappers, colliding keys are searched linearly. `String`s with equal hash codes are trivial to generate. So a cache keyed by attacker-chosen strings can be made to do O(n) work per operation, and O(n<sup>2</sup>) to fill (CWE-407).
- Guava 27.0.1's segment tables chain collisions too, so this matches Guava. It is worse than a plain `HashMap<String, V>`, however.
- `HashBiMap` uses Guava's own chained tables, with the same behaviour.

**Positive:** Lava's immutable sets and maps keep Guava's hash-flooding defence (`ImmutableSet.hashFloodingDetected`, `RegularImmutableMap.MAX_HASH_BUCKET_LENGTH`). They fall back to JDK-backed tables, which do have tree bins.

**Impact:** CPU exhaustion through a cache that is fed untrusted keys. `maximumSize` limits the size n, and with it the damage.

**Recommendation:**
- Bound caches that hold untrusted keys, or key them by a hash that callers cannot predict.
- Optionally, let `StrongKey` implement `Comparable<StrongKey>`, delegating to the key when both keys are the same `Comparable` class. This restores tree-bin ordering for `String` keys.

**Resolution (fixed for caches):** `StrongKey` implements `Comparable<StrongKey>`. It orders two equals-compared keys of the same `Comparable` class by their natural order and treats any other pair as unordered, so `HashMap` keeps colliding `String` keys in a balanced tree. `HashBiMap` keeps Guava's chained table and is documented. Tests: `cachesKeyedByCollidingStringsStayCorrect`, `strongKeysOrderOnlyComparableKeysOfOneClass`, plus all cache suites and fuzzers.

### SA-09 — Configuration strings drive large allocations (Low, inherited)

**Location:** `cache/CacheBuilderSpec.java` and `CacheBuilder.from(String)`; `cache/LocalCache.java` (the constructor sizes the table from `initialCapacity`).

**Description:**
- `initialCapacity` up to 2<sup>30</sup> sizes the backing table, which is allocated on the first write: gigabytes of memory.
- `maximumSize`, `maximumWeight` and the expiry durations accept values up to `Long.MAX_VALUE`, which effectively disables the bounds.

Guava also allocates by `initialCapacity`, and eagerly.

**Impact:** a spec string taken from untrusted input, such as a request or a tenant setting, can exhaust memory or remove eviction.

**Recommendation:** treat specs as trusted configuration, and document it. Optionally, cap `initialCapacity` at a sane bound.

**Resolution (documented):** `CacheBuilder.from(String)`, the `cache` package docs and the README state that specifications are trusted configuration.

### SA-10 — Integer overflow in `Doubles.concat` and `Doubles.join` (Low, inherited)

**Location:** `primitives/Doubles.java:258-270` and `:336-349`.

**Description:** `concat` sums the array lengths in an `int` without an overflow check. The result is either `NegativeArraySizeException`, or a too-small result array, for which `System.arraycopy` throws `IndexOutOfBoundsException`. `join` presizes `new StringBuilder(array.length * 12)`, which overflows above about 179 million elements. Java bounds-checks every access, so there is no memory corruption, but the method throws unexpected runtime exceptions on very large inputs. Later Guava versions check the sum.

**Recommendation:** check the sum (`Math.addExact`, or a `long` sum with `checkArgument`), and clamp the `StringBuilder` capacity.

**Resolution (fixed):** `concat` sums in a `long` and rejects totals over `Integer.MAX_VALUE` with an `IllegalArgumentException`, using later Guava's message. `join` clamps its initial capacity. Test: `concatRejectsTotalsThatDoNotFitInAnArray`.

### SA-11 — User data in exception messages and logs (Low, inherited)

**Location:** examples:
- `CacheLoader.InvalidCacheLoadException("CacheLoader returned null for key " + key)`;
- the duplicate-key messages of `ImmutableMap`, which include both values;
- `Maps.uniqueIndex`;
- `HashBiMap` ("value already present: ...");
- the formatted messages of `Preconditions`;
- `EventBus`'s default exception handler (`EventBus.java:180-245`), which logs the subscriber's and the event's `toString()` at `SEVERE`;
- `DeadEvent.toString`.

**Description and impact:**
- Keys, values, events and subscribers are embedded with their `toString()`, so secrets in them reach exceptions and logs (CWE-209/532).
- Line breaks in those strings can forge log lines (CWE-117).
- JUL does not interpret Lava's messages as format strings, because Lava passes no parameters.

**Recommendation:**
- Keep secrets out of `toString()` of keys and events.
- Install a custom `SubscriberExceptionHandler` where events carry sensitive data.
- Use a log formatter that escapes line breaks.

**Resolution (documented):** the `cache` and `eventbus` package docs and the README warn about `toString()` in messages and logs, and point to `SubscriberExceptionHandler`. Messages keep Guava's format.

### SA-12 — The event bus calls `setAccessible(true)` (Info, by design)

**Location:** `eventbus/Subscriber.java:60` and `SubscriberRegistry.java:164`; also `collect/Serialization.java:114`, which only touches Lava's own fields.

**Description:** registering an object makes its `@Subscribe` methods callable, private ones included, by anyone who can `post` an event of a matching type. A subscriber to `Object` receives every event. Under strong module encapsulation, registering objects from packages that are not open throws `InaccessibleObjectException`.

**Recommendation:** register only trusted subscribers, avoid broad (`Object`) subscriptions on buses that carry untrusted payloads, and document the module requirement.

**Resolution (documented):** the `eventbus` package docs and the README.

### SA-13 — Unbounded recursion in `toString` (Info, inherited)

**Location:** `eventbus/DeadEvent.java` (`Arrays.deepToString` for array events), and the `toString` of nested collections.

**Description:** a deeply nested value throws `StackOverflowError` when rendered, as it does in the JDK. Self-referencing arrays are handled by `deepToString`.

**Recommendation:** none required; it is the JDK's behaviour. Avoid logging untrusted, deeply nested structures.

**Resolution (accepted):** this is the JDK's behaviour, and nothing changes.

### SA-14 — Build supply chain (Low)

**Findings:**
- **Wrapper JAR:** `gradle/wrapper/gradle-wrapper.jar` has SHA-256 `91a239400bb638f36a1795d8fdf7939d532cdc7d794d1119b7261aac158b1e60`. That is the official checksum for the Gradle **7.5 and 7.5.1** wrapper, checked against <https://gradle.org/release-checksums/>, so the JAR is genuine. It is stale, though: the distribution is 8.14, whose wrapper JAR is `7d3a4ac4…6172`.
- **Distribution download:** `gradle-wrapper.properties` has no `distributionSha256Sum`. A tampered distribution download would not be detected. The official checksum for 8.14 `-bin` is `61ad310d3c7d3e5da131b76bbf22b5a4c0786e9d892dae8c1658d4b484de3caa`.
- **Dependency verification:** there is no Gradle dependency verification (`gradle/verification-metadata.xml`) for the test dependencies, which are JUnit, AssertJ and Guava.
- **Guava's advisories:** the test-only oracle Guava 27.0.1-jre has published advisories, CVE-2020-8908 (`Files.createTempDir` permissions) and CVE-2023-2976 (`FileBackedOutputStream`). Neither affected API is used by Lava's main code, which cannot reference Guava (`verifyNoGuavaInMain`), or by the tests, and the shipped library has no runtime dependencies (`verifyNoRuntimeDependencies`). Dependency scanners will still flag the build.

**Recommendation:**
- Regenerate the wrapper with the distribution checksum pinned: `./gradlew wrapper --gradle-version 8.14 --gradle-distribution-sha256-sum 61ad310d3c7d3e5da131b76bbf22b5a4c0786e9d892dae8c1658d4b484de3caa`.
- Validate the wrapper in CI (`gradle/actions/wrapper-validation`).
- Enable dependency verification (`./gradlew --write-verification-metadata sha256 help`).
- Note in `PLAN.md` that Guava's advisories do not apply to the library.

**Resolution (fixed):**
- **Wrapper:** regenerated with Gradle 8.14. Its JAR's SHA-256 is now `7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172`, the official value.
- **Distribution:** `gradle-wrapper.properties` pins `distributionSha256Sum`.
- **Dependency verification:** `gradle/verification-metadata.xml` verifies the SHA-256 of all 42 build and test components. The checksums were generated from the local Gradle cache, which is trust on first use. Sources and Javadoc JARs, which only IDEs download, are trusted.
- **Still to do outside the repository:** wrapper validation in CI, and PGP signature verification (`verify-signatures`).

---

## Positive observations

- **No dependencies at run time or compile time.** The build fails if any is added, and main code cannot mention Guava or the Checker Framework.
- **No dangerous platform APIs:** none of the file, network, process, JNDI, scripting, reflection-based class loading, `Unsafe`, system property or environment APIs appear in main.
- **One regular expression only:** `Doubles.FLOATING_POINT_PATTERN`, copied from Guava. It uses possessive quantifiers, which rule out catastrophic backtracking.
- **Deserialization hardening kept from Guava:**
  - `ImmutableList` and `ImmutableAsList` reject direct deserialization;
  - every `SerializedForm` rebuilds through null- and duplicate-checking builders;
  - `HashBiMap.readObject` ignores the stream's count when sizing (`init(16)`).
- **Overflow fixed beyond Guava 27.0.1:** `Lists.partition`; `Lists.cartesianProduct` uses checked multiplication; capacity computations saturate.
- **Hash-flooding defence:** immutable sets and maps keep Guava's fallback to JDK-backed tables.
- **Defensive copies:** immutable collections reject nulls, `copyOf` copies every non-immutable input, and `toArray` returns copies.
- **Locale-independent:** case conversions and parsing use `Locale.ROOT`.
- **Bounded caches:** caches can bound memory with `maximumSize`/`maximumWeight` and weak or soft references. Expired and collected entries are removed eagerly.

## Verification of the fixes

- **Tests:** `SecurityAuditTest` holds 12 regression tests, one or more per fixed finding.
- **Against the audited code:** run against a checkout of `62ee086`, 10 of the 12 fail, including the thread-pool `ListenableFutureTask` test, which confirms that race. The two that pass there are:
  - the SA-08 test, which checks correct results, not speed;
  - the SA-01 race test, because the race is nondeterministic and did not occur in that run.
- **Full build:** `./gradlew check` passes the whole suite after the fixes, including the cache fuzzers. They now also check that every cache operation releases the concurrent-use detector.
- **Outside the scope of these fixes:** no exploit code was written, and the deserialization and denial-of-service findings were not reproduced against the code as audited.

## Prioritized recommendations (as audited)

1. **SA-01:** synchronize, or replace, the `Enums` cache. This is a small change, and it fixes the only finding that bites correct, documented use of Lava.
2. **SA-04:** stop presizing from the stream in `ImmutableSetMultimap.readObject`. One line, and it removes a regression against Guava.
3. **SA-03:** make the event bus dispatch queue thread-local, as in Guava.
4. **SA-02:** add an owner-thread check to caches, event buses and `ExecutionList`.
5. **SA-05 and SA-07:** add `readObject` guards and invariant checks to the immutable and value classes.
6. **SA-14:** pin the Gradle distribution checksum, refresh the wrapper and enable dependency verification.
7. **SA-06, SA-08 to SA-11:** document them. Optionally add a comparable `StrongKey`, an `initialCapacity` cap and the `Doubles` overflow checks.
