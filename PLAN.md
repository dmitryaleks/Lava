# Lava — Guava 27.0.1 single-threaded port: implementation plan

> Approved implementation plan (recorded from the planning session, 2026-10-17). Progress and review outcomes are tracked in `REVIEW.md` and git history.

## Context

`SCOPE.md` asks for Guava-independent, single-threaded equivalents of 8 Guava 27.0.1 entry points
(Doubles, Cache+CacheBuilder+helpers, EventBus+Subscribe+helpers, checker `@NonNull`, Enums,
ImmutableMap, Maps, Lists) under `com.simplelibs.lava.*`, on Java 17 with Gradle. The public
interface must be kept, so client code migrates by swapping imports. Concurrency machinery
(segments, locks, Striped64/LongAdder, CAS) is replaced by simple single-threaded code. The work
also needs proper unit tests and an adversarial review by a dedicated sub-agent.

Current state: an IntelliJ **Kotlin** template (`kotlin("jvm")`, `src/main/kotlin/Main.kt`),
Gradle 8.14 wrapper, JDK 17.0.12 installed, not a git repo. Guava sources are at
`guava-27.0.1/guava-27.0.1/guava/src`, tests at `.../guava-tests/test`. Maven Central is reachable;
JUnit 5.11.4 and AssertJ 3.26.3 are already in the Gradle cache.

### Decisions (confirmed with user)
| Topic | Decision |
|---|---|
| Language | **Java 17**. Remove the Kotlin plugin and `Main.kt`; use `java-library`. |
| Tests | JUnit 5 + AssertJ. **Real Guava 27.0.1-jre is a `testImplementation` oracle only** (differential tests, API-parity reflection). No guava-testlib, no JUnit 4/Vintage. |
| Heavy transitive types | **Port minimal versions** of BiMap/HashBiMap, Range/BoundType and Multimap/SetMultimap/ImmutableSetMultimap so every in-scope method exists. |
| Helper types | **Hybrid**: port Guava types whose API differs from the JDK (Optional, Converter, Ticker, Equivalence, Immutable{Collection,List,Set}, ListenableFuture subset, UncheckedExecutionException/ExecutionError). Use `java.util.function.{Function,Supplier,Predicate}` directly. Guava's versions extend these, so callers still compile. |

### Derived decisions (mine, open to veto at approval)
- **Package mapping rule:** drop the vendor prefix. `com.google.common.X` → `com.simplelibs.lava.X`;
  `org.checkerframework.X` → `com.simplelibs.lava.X` (so `com.simplelibs.lava.checker.nullness.qual.NonNull`).
- **`@Nullable` is ported next to `@NonNull`.** Guava 27.0.1 annotates its API with checker's
  `@Nullable`, so the ported signatures need it. Both are plain `@Documented @Retention(RUNTIME)
  @Target({TYPE_USE, TYPE_PARAMETER})` markers. The checker meta-annotations (`@SubtypeOf`,
  `@ImplicitFor`, `@DefaultQualifierInHierarchy`, `@DefaultFor`, `@DefaultInUncheckedCodeFor`)
  only mean something to the Checker Framework type-checker, so they are dropped and documented.
- **Dropped annotations:** `@GwtCompatible/@GwtIncompatible`, `@J2ObjCIncompatible`, `@Beta`,
  `@CanIgnoreReturnValue`, `@VisibleForTesting`, `@LazyInit`, `@Weak`, `@RetainedWith`, `@DoNotMock`.
  `@Deprecated` is kept wherever Guava has it.
- **Thread-safety contract:** every class Javadoc and every `package-info.java` says "not thread-safe;
  confine to one thread". Callbacks through user-supplied `Executor`s (AsyncEventBus,
  `RemovalListeners.asynchronous`, `CacheLoader.asyncReloading`) must run on that same thread, for
  example a direct executor or a queue the app loop drains.
- **Licensing:** the code is derived from Apache-2.0 Guava. Keep "Copyright (C) The Guava Authors"
  headers, add a "Modified for Lava" note, and ship `LICENSE` (copied from Guava's `COPYING`) plus a short `NOTICE`.
- **Serializable** where Guava is (immutable collections, Optional, `Doubles.asList`, entries,
  `HashBiMap`, `Range`, etc.). Round-trip tests run Lava→Lava only; the serialized form is not
  wire-compatible with Guava.
- **Git:** run `git init`, add the remote `origin = https://github.com/dmitryaleks/Lava` (requested by
  the user), and commit and push after each milestone so the reviewer sub-agent works on real diffs.
  `.gitignore` gets `guava-27.0.1/` and `guava-27.0.1.zip` (reference material, 8 MB+).
- **Compiler and Javadoc policy:** main code compiles with `-Xlint:all -Werror`. Guava-faithful spots get
  targeted `@SuppressWarnings` (`overrides` on `Converter`/`Equivalence`-style equals-only overrides,
  `unchecked`/`rawtypes` where Guava uses them, `deprecation` on internal calls). If a lint category
  turns out to be systematically noisy, it is disabled explicitly and recorded here. Javadoc runs with
  `-Xdoclint:all,-missing`. Ported Javadoc is adapted: links to Guava classes that aren't ported become
  `{@code …}`.
- **Shared internals first:** `ImmutableMap` and `ImmutableList` (M3) need `Maps.equalsImpl`/`toStringImpl`,
  `Sets.hashCodeImpl`/`equalsImpl` and `Lists.equalsImpl`/`hashCodeImpl`/`indexOfImpl`. M3 therefore
  creates `Maps`, `Lists` and a package-private `Sets` containing those package-private helpers. M4 and M5
  then add the public methods.

---

## Target layout

```
build.gradle.kts / settings.gradle.kts          java-library, toolchain 17, JUnit5, AssertJ, jacoco
LICENSE, NOTICE, PLAN.md, REVIEW.md
src/main/java/com/simplelibs/lava/
  checker/nullness/qual/  NonNull, Nullable
  base/                   Enums, Converter, Optional(+Absent/Present pkg-private), Ticker,
                          Equivalence, Preconditions (subset, public helper), package-info
  primitives/             Doubles
  util/concurrent/        ListenableFuture, ListenableFutureTask, Futures (subset),
                          MoreExecutors (directExecutor only), UncheckedExecutionException, ExecutionError
  collect/                ImmutableCollection, ImmutableList, ImmutableSet, ImmutableMap,
                          UnmodifiableIterator, UnmodifiableListIterator, ForwardingObject,
                          Lists, Maps, MapDifference, SortedMapDifference,
                          BiMap, HashBiMap, Range, BoundType,
                          Multimap, SetMultimap, ImmutableMultimap, ImmutableSetMultimap,
                          (pkg-private) CollectPreconditions, Regular*/Singleton* impls, view classes
  eventbus/               EventBus, AsyncEventBus, DeadEvent, Subscribe, AllowConcurrentEvents,
                          SubscriberExceptionContext, SubscriberExceptionHandler,
                          (pkg-private) Dispatcher, Subscriber, SubscriberRegistry
  cache/                  Cache, LoadingCache, CacheBuilder, CacheBuilderSpec, CacheLoader,
                          CacheStats, AbstractCache, AbstractLoadingCache, ForwardingCache,
                          ForwardingLoadingCache, RemovalCause, RemovalListener, RemovalListeners,
                          RemovalNotification, Weigher, (pkg-private) LocalCache
src/test/java/com/simplelibs/lava/...           same packages (white-box access to pkg-private)
  testing/                NullPointerTester-lite, EqualsTester-lite, SerializableTester-lite,
                          FakeTicker, Map/List/Collection contract base tests, ApiParity support
```

---

## Milestone 0 — Build and repo setup
1. Copy this plan to `PLAN.md`. `git init`, extend `.gitignore`, and make the first commit (build files + PLAN.md + SCOPE.md).
2. Rewrite `build.gradle.kts`:
   - `plugins { `java-library`; jacoco }`, `group = "com.simplelibs"`, `version = "0.1.0-SNAPSHOT"`.
   - `java { toolchain { languageVersion = JavaLanguageVersion.of(17) }; withSourcesJar(); withJavadocJar() }`.
   - `tasks.withType<JavaCompile> { options.release = 17; options.encoding = "UTF-8"; options.compilerArgs += listOf("-Xlint:all", "-Werror") }`.
     Main uses `-Werror`. Tests use `-Xlint:all` without `-Werror`, because they call deprecated mutators on purpose.
   - Test deps: `platform("org.junit:junit-bom:5.11.4")`, `junit-jupiter`, `testRuntimeOnly("org.junit.platform:junit-platform-launcher")`,
     `org.assertj:assertj-core:3.26.3`, **`com.google.guava:guava:27.0.1-jre` (testImplementation only)**.
   - `tasks.test { useJUnitPlatform() }`, `jacocoTestReport`, plus `jacocoTestCoverageVerification`
     (≥ 85% line and ≥ 75% branch on main), wired into `check`.
   - **Guava-free guard:** Guava is only on the test classpath, so main can't compile against it.
     Add a `verifyNoGuavaInMain` task as well. It fails if `src/main` mentions `com.google.` or
     `org.checkerframework.` anywhere, including FQNs inside Javadoc and strings. Wire it into `check`.
3. Delete `src/main/kotlin/Main.kt` and the Kotlin plugin, and keep `settings.gradle.kts` (foojay resolver). Run `./gradlew check` on an empty skeleton with one smoke test.

## Cross-cutting test strategy
- **Ported behavioral tests:** port the relevant `guava-tests` cases to JUnit 5 + AssertJ. Truth
  becomes AssertJ, and testlib helpers become the small `testing/` helpers.
- **Differential tests against the real Guava oracle:** seeded `java.util.Random` operation
  sequences run against Lava and Guava side by side, and observable results are compared.
  Test code refers to Guava by FQN (`com.google.common.collect.ImmutableMap`) to avoid name clashes.
- **Contract base tests:** in-house abstract JUnit 5 classes (`MapContractTest`, `ListContractTest`,
  `CollectionContractTest`) run against every view and immutable impl. They cover equals/hashCode
  and toString, iterator `remove`, UOE on mutators, and `ConcurrentModificationException` where it applies.
- **`ApiParityTest` (reflection):** for each ported Guava type, list its public and protected
  constructors, methods, fields and nested types, map the type names (`com.google.common.X` →
  `com.simplelibs.lava.X`, Guava `Function/Supplier/Predicate` → `java.util.function.*`, checker → Lava),
  and assert that Lava declares the same members. Matching compares the type-mapped
  **`toGenericString()`**: modifiers, type parameters, exact wildcards, return type and `throws` clause.
  It also checks the superclass and interfaces of each type, and enum constants.
  An explicit `src/test/resources/api-exclusions.txt` lists each intentional omission or difference with
  its reason. The test fails on any undocumented gap and on any stale exclusion, so
  "keep the interface" is mechanically verified. It runs per milestone, for the types ported so far.
- **NPE sweep:** a NullPointerTester-lite walks every public method and constructor and passes `null`
  for each parameter that isn't `@Nullable` (read through `getAnnotatedType()`). It expects NPE.

## Milestone 1 — Foundations (`checker…qual`, `base`, `util.concurrent`)
Source references: `guava/src/com/google/common/base/{Converter,Optional,Absent,Present,Ticker,Equivalence,Preconditions}.java`.
- **`@NonNull`, `@Nullable`.** Plain markers, as described above. Note: `TYPE_USE` annotations can only be
  read through `AnnotatedType` (`getAnnotatedReturnType()`, `Parameter.getAnnotatedType()`).
- **`Preconditions` (public subset):** `checkArgument`, `checkState`, `checkNotNull`, `checkElementIndex`,
  `checkPositionIndex`, `checkPositionIndexes`, with Guava's `%s`-only `lenientFormat` and its exact messages
  (`"index (5) must be less than size (3)"`, `"end index (%s) must not be less than start index (%s)"`, …).
- **`Converter<A,B> implements java.util.function.Function<A,B>`.** Port the full API:
  - `doForward`/`doBackward`, `convert`, `convertAll` (a lazy view that delegates `remove`), the cached
    `reverse()` (ReverseConverter: equals, `~hash`, `".reverse()"`), and `andThen(Converter)`.
    `andThen(Converter)` is an *overload* of the JDK `andThen(Function)`, not an override.
  - deprecated `apply`, `from(f, g)`, `identity()` (singleton, `readResolve`), and the package-private
    `Converter(boolean handleNullAutomatically)`.
  - Null handling: null maps to null, and a non-null input that maps to null throws NPE.
- **`Optional<T>` (abstract, pkg-private ctor) with `Absent`/`Present`.** Port the full API, including
  `fromJavaUtil`/`toJavaUtil`, `or(T|Optional|Supplier)` with the exact NPE messages, `asSet`, `transform`,
  `presentInstances`, the hash constants (`0x79a31aac`, `0x598df91c + h`), and toString
  `"Optional.of(x)"`/`"Optional.absent()"`. `Supplier` and `Function` parameters are `java.util.function`.
- **`Ticker`** (`read()`, `systemTicker()`). **`Equivalence<T> implements BiPredicate<T,T>`**, full API:
  `equivalent`, `test`, `hash`, `doEquivalent`/`doHash`, `onResultOf`, `wrap` + `Wrapper`, `pairwise`,
  `equivalentTo`, `equals()`, `identity()`. Maps.difference needs it.
- **`util.concurrent`:**
  - `ListenableFuture<V> extends Future<V>` (`addListener(Runnable, Executor)`).
  - `ListenableFutureTask` (`FutureTask` plus a listener list that runs on `done()`).
  - `Futures.{immediateFuture, immediateFailedFuture, immediateCancelledFuture, getDone}`.
  - `MoreExecutors.directExecutor()`.
  - `UncheckedExecutionException`, `ExecutionError`, with Guava's constructor sets.
  - Only what the cache API needs, plus the obvious factories that callers use to implement `CacheLoader.reload`.
- Tests: port `ConverterTest`, `OptionalTest`, `EquivalenceTest`, and Preconditions message tests.
  Use `Doubles.stringConverter()` in place of `Longs`, and serializable enum singletons for
  `Functions.toStringFunction`, so the serialization tests still hold.

## Milestone 2 — `primitives.Doubles`, `base.Enums`
- **Doubles:** a verbatim port of all 22 public members plus `BYTES`, including:
  - `DoubleConverter` (singleton, `readResolve`, `"Doubles.stringConverter()"`; forward trims via `Double.valueOf`).
  - the `LexicographicalComparator` enum.
  - `DoubleArrayAsList`. Keep its quirks: primitive `==` in contains/indexOf (NaN is never found, -0.0
    matches 0.0); the `equals` asymmetry; `set` checks the index before null; `subList(i,i)` and
    `asList()` return `Collections.emptyList()`; `spliterator` is SIZED|SUBSIZED only.
  - the package-private `FLOATING_POINT_PATTERN`, copied verbatim (possessive quantifiers; a full match, so
    surrounding whitespace returns null; hex floats need `p`).
- **Enums:**
  - `getField`.
  - `getIfPresent`, using Guava's unloading-safe cache: a `WeakHashMap<Class, Map<String, WeakReference<Enum>>>`,
    unsynchronized because the code is single-threaded.
  - `stringConverter` (a new instance per call; equals by class; `"Enums.stringConverter(<fqcn>.class)"`).
- Tests:
  - Port `DoublesTest`, including the all-code-points `tryParse` sweep compared against Guava's regex
    and `tryParse` as an oracle, plus extra -0.0/NaN cases for `sortDescending`.
  - Port `EnumsTest`. The class-unloading test uses a child `URLClassLoader` built from
    `java.class.path` with a bounded GC loop; mark it `@Tag("gc")` so it can be skipped if it's flaky.
  - Write a hand-written List-contract test for `asList` and its subLists (replacing `ListTestSuiteBuilder`).
  - Differential random tests against Guava for `join`, `min`/`max`, the comparator, `sortDescending`,
    `concat` and `toArray`.

## Milestone 3 — Immutable collections core (+ minimal Multimap family)
Source references: `collect/{ImmutableCollection,ImmutableList,ImmutableSet,ImmutableMap,UnmodifiableIterator,UnmodifiableListIterator,ForwardingObject,CollectPreconditions,ImmutableSetMultimap,ImmutableMultimap,Multimap,SetMultimap}.java`.
- **`ImmutableCollection`:**
  - `iterator()` returns `UnmodifiableIterator`.
  - Final `toArray`. Seven final, `@Deprecated` mutators that throw UOE.
  - `asList()`.
  - Nested `Builder`.
  - Package-private hooks: `isPartialView`, `copyIntoArray`, `writeReplace`.
- **`ImmutableList`:** full public API.
  - `of(0..11)` and the `@SafeVarargs` 12+ overload.
  - `copyOf` ×4, `sortedCopyOf` ×2, `builder`, `builderWithExpectedSize`, `toImmutableList`.
  - `subList` returns `ImmutableList`. `reverse()`. `listIterator` returns `UnmodifiableListIterator`.
  - Final deprecated mutators.
  - Backed by an array: Regular, Singleton, SubList, Reverse.
  - `SerializedForm`, plus `readObject` that throws `InvalidObjectException("Use SerializedForm")`.
- **`ImmutableSet`:** full public API.
  - `of` ×7, `copyOf` ×4, `builder`, `builderWithExpectedSize`, `toImmutableSet`, `asList`.
  - Backed by an ordered array plus a `HashMap` index. This resists hash flooding because `HashMap`
    treeifies buckets. No `ImmutableEnumSet`.
- **`ImmutableMap`:** every public member, including `asMultimap()`.
  - `of(0..5)`; the 1-arg form returns a package-private `SingletonImmutableMap` instead of `ImmutableBiMap`.
  - `Builder` (`put`, `putAll`, `orderEntriesByValue` with a stable sort and the ISE on a second call,
    reusable). Package-private `Builder(int)`.
  - `builderWithExpectedSize` (with IAE text) and `copyOf` ×2. `copyOf` returns the same instance for a
    non-partial `ImmutableMap`; an `EnumMap` becomes `ImmutableEnumMap`.
  - `toImmutableMap` ×2 (the 3-arg form via `LinkedHashMap` + `copyOf`).
  - Final deprecated mutators. `getOrDefault`.
  - Views: `ImmutableSet` for keys and entries, `ImmutableCollection` for values, each with `asList()`.
    View serialization round-trips to `map.keySet()` and the other views.
  - Exact messages: `"Multiple entries with same key: k=v2 and k=v1"`, `"null key in entry: null=v"`,
    `"null value in entry: k=null"`. Null checks happen at `put` time and are atomic.
  - `equals`, `hashCode` and toString follow the Map contract (`{k1=v1, k2=v2}`).
  - Implementations: `RegularImmutableMap` (an ordered `Entry[]` plus a `HashMap` index),
    `SingletonImmutableMap`, `ImmutableEnumMap` (wraps an `EnumMap`), and `SerializedForm`.
- **`UnmodifiableIterator`, `UnmodifiableListIterator`, `ForwardingObject`:** verbatim.
  Package-private `CollectPreconditions` (`checkEntryNotNull`, `checkNonnegative` with
  `"<name> cannot be negative but was: <v>"`, `checkRemove`).
- **Minimal Multimap family, needed by `asMultimap()`:**
  - `Multimap` and `SetMultimap` interfaces, as in Guava but **without `keys()`**, which would need `Multiset`.
  - `ImmutableMultimap`: abstract, instance API only. **No static factories and no public Builder**,
    because Guava's would need `ImmutableListMultimap`.
  - `ImmutableSetMultimap`:
    - `of(0..5)`, `builder()`, and a standalone `Builder` with every `put*` method plus
      `orderKeysBy`/`orderValuesBy`. Values come out ordered, but as a plain `ImmutableSet`, not an
      `ImmutableSortedSet`.
    - `copyOf` ×2, `toImmutableSetMultimap`, `flatteningToImmutableSetMultimap`.
    - `get` returns `ImmutableSet`; `inverse`, `entries`, `asMap` (an `ImmutableMap<K, Collection<V>>`),
      `keySet`, `values`; deprecated mutators throw UOE; `equals`/`hashCode`/toString go through `asMap()`.
  - The `api-exclusions.txt` file records every omission.
- Tests:
  - Port the JUnit-only parts of `ImmutableMapTest`, `ImmutableListTest`, `ImmutableSetTest`,
    `ImmutableCollectionTest` and `ImmutableSetMultimapTest`: creation, atomic null checks, duplicates,
    `copyOf` identity, collectors, flooding, view serialization, `keySet().spliterator().trySplit()`.
  - **Port testlib's `MapInterfaceTest`** (JDK + JUnit 3 only, about 1.6k lines) into JUnit 5 as an
    abstract base. Run it, plus the List and Collection contract bases, over regular, singleton, empty,
    enum, reserialized, colliding-hash and unhashable-value instances.
  - Differential checks against Guava for iteration order, toString, equals/hashCode, and exception messages.

## Milestone 4 — `collect.Lists`
All 19 public methods, ported verbatim per `collect/Lists.java`:
- **Capacity:** `computeArrayListCapacity` is `5 + n + n/10`, saturated, and throws IAE
  `"arraySize cannot be negative but was: …"`. `newArrayListWithCapacity` throws its own IAE text.
- **Factories:** `newCopyOnWriteArrayList` (JDK type).
- **`asList`:** `OnePlus`/`TwoPlus` lists (RandomAccess, Serializable; size via saturated add).
- **`transform`:** a RandomAccess variant and a Sequential variant. The Sequential one uses only
  `listIterator`/`size`. Both remove-through, both Serializable; never catch IOOBE.
- **`partition`:**
  - A live, unmodifiable outer list whose inner lists are subList views.
  - `size()` is computed with ceiling division and doesn't overflow.
  - **Fix Guava's latent `start+size` overflow** in `get()` and document it.
- **`reverse`:**
  - For an `ImmutableList`, returns `list.reverse()`.
  - Reversing a reversed view returns the original.
  - Otherwise a full read/write `ReverseList` with a custom `ListIterator`.
- **`cartesianProduct` ×2:**
  - Each axis is defensively copied with `ImmutableList.copyOf`. An empty axis gives `of()`; no axes give `[[]]`.
  - Overflow throws IAE `"Cartesian product too large; must have size at most Integer.MAX_VALUE"`.
  - `indexOf` and `contains` use index arithmetic.
- **`charactersOf(String)`:** returns an `ImmutableList`; `subList` maps to `substring`.
- **`charactersOf(CharSequence)`:** a live view, neither RandomAccess nor Serializable.
- **Package-private impl helpers**, used by `ImmutableList` and other code: `equalsImpl`, `hashCodeImpl`, `indexOfImpl`, `lastIndexOfImpl`.
- Tests:
  - Port `ListsTest` and `ListsImplTest` (`@ParameterizedTest`).
  - Run the List contract base over every view: `asList`, `transform` (both variants), `partition`
    inners, `reverse` (both), `charactersOf` ×2, and the `cartesianProduct` elements.
  - Differential random tests against Guava.

## Milestone 5 — `collect.Maps` (+ MapDifference, BiMap, Range)
All 52 public static methods and `EntryTransformer`, per `collect/Maps.java`. Exact generic wildcards are
kept, e.g. `Function<? super V1, V2>`, `Predicate<? super Entry<K,V>>`. The function types are JDK `java.util.function` ones.
- **Factories:**
  - `capacity()` follows Guava's formula. `newTreeMap()` keeps its raw `Comparable` bound.
  - `newConcurrentMap` returns `ConcurrentHashMap`.
  - `immutableEnumMap` reads the entry set once. `toImmutableEnumMap` ×2 use the exact NPE/IAE messages.
- **`difference` ×3:**
  - The result is a snapshot built from `LinkedHashMap`/`TreeMap`. A `SortedMap` left side dispatches at runtime.
  - `MapDifference`, `SortedMapDifference` and `ValueDifference` are ported with their equals, hashCode
    and toString (`"not equal: only on left=…: value differences={k=(l, r)}"`).
  - The `Equivalence` overload uses the base `Equivalence`.
- **Live views:**
  - `asMap` ×3: remove-only key sets; `put` throws UOE; the navigable variant supports polls and descending views.
  - `transformValues`/`transformEntries` ×3 each: lazy; entries are read-only; removal writes through.
  - `filterKeys`/`filterValues`/`filterEntries` ×4 each:
    - `put` throws IAE when the predicate fails, and `putAll` is atomic.
    - Iterators don't support `remove`; filtering a filtered map composes the predicates.
    - `setValue` throws IAE if the new value fails the predicate.
  - `unmodifiableNavigableMap`: idempotent; the descending map is cached; Serializable.
  - `synchronizedNavigableMap` delegates to `Collections.synchronizedNavigableMap`.
- **Other methods:**
  - `toMap` ×2, `uniqueIndex` ×2 (with the `"… use Multimaps.index."` suffix), and `fromProperties`, which includes defaults.
  - `immutableEntry`: an `AbstractMapEntry`-style hash and toString; Serializable.
- **Minimal BiMap support:**
  - `BiMap` interface: full (`put`, `forcePut`, `putAll`, `values` returning `Set`, `inverse`).
  - `HashBiMap`: `create()`, `create(int)`, `create(Map)`. Insertion-ordered; a live `inverse()`; `forcePut`;
    IAE `"value already present: …"`; Serializable.
  - Maps' internal `UnmodifiableBiMap`, `SynchronizedBiMap` (a delegating wrapper) and `FilteredEntryBiMap`.
  - `asConverter`: `"No non-null mapping present for input: %s"`; toString `Maps.asConverter(…)`.
  - No `ImmutableBiMap`.
- **Minimal Range support:**
  - `Range` has every public member **except `canonical(DiscreteDomain)`**: all 14 factories, bounds
    queries, `contains`/`containsAll`, `encloses`, `isConnected`, `intersection`, `gap`, `span`, equals,
    hashCode and toString (`[a..b)`, `(-∞..+∞)`).
  - It implements `java.util.function.Predicate<C>` and keeps a deprecated `apply`.
  - Internals: `Cut` and `BoundType`. Serializable.
  - `subMap(NavigableMap, Range)`.
- **Internal helpers**, package-private and hand-rolled on the JDK: `Sets.filter`, `Collections2.transform` and
  `FilteredCollection`, the Iterators/Iterables helpers, `AbstractMapEntry`, `AbstractNavigableMap`,
  `DescendingMap`, `ViewCachingAbstractMap`, `IteratorBasedAbstractMap`. Ordering is replaced by
  `Comparator`. There is no public `Sets` or `Iterables` API.
- Tests:
  - Port the JUnit-only parts of `MapsTest`, `FilteredMapTest`/`FilteredSortedMapTest`, `MapsTransformValues*Test`,
    `ImmutableEnumMapTest`, `RangeTest` (minus `canonical`) and `HashBiMapTest`.
  - Re-create `MapsCollectionTest`'s coverage by running the in-house `MapInterfaceTest` and contract bases
    over every view family, including filter-of-filter and the BiMap filters.
  - Give the test JVM `--add-opens java.base/java.util=ALL-UNNAMED` so the HashMap `table` reflection
    tests can run.
  - Differential random tests against Guava: random mutations through views must give identical backing
    maps, and `difference` must produce the same toString.

## Milestone 6 — `eventbus`
Faithful to `EventBus.java`, `Dispatcher.java`, `Subscriber.java`, `SubscriberRegistry.java`:
- **Public surface:**
  - `EventBus`: three public constructors, a package-private 4-arg constructor, `identifier()`,
    package-private `executor()` and `handleSubscriberException`, `register`, `unregister`, `post`,
    and toString `SimpleName{id}`.
  - `AsyncEventBus`: three constructors.
  - `DeadEvent`: no `equals`; toString `DeadEvent{source=…, event=…}`.
  - `Subscribe` and `AllowConcurrentEvents`: `RUNTIME`, `METHOD`, not `@Documented`.
  - `SubscriberExceptionContext`, with a package-private constructor.
  - `SubscriberExceptionHandler`.
- **Dispatcher (pkg-private), single-threaded:**
  - `perThreadDispatchQueue` uses plain fields (an `ArrayDeque` queue and a `dispatching` flag) and is
    breadth-first and FIFO. Reentrant posts only enqueue. An escaping `Error` clears the queue in `finally`.
  - `legacyAsync` enqueues all (event, subscriber) pairs, then drains.
  - `immediate` is depth-first.
- **Subscriber:**
  - One class; `@AllowConcurrentEvents` is a no-op marker. Calls `setAccessible(true)` and dispatches
    through `bus.executor()`.
  - Unwraps `InvocationTargetException`: an `Error` cause is rethrown; anything else goes to `handleSubscriberException`.
  - IAE becomes `Error("Method rejected target/argument: …")` and IAccE becomes `Error("Method became inaccessible: …")`.
  - equals is target identity plus method.
- **SubscriberRegistry:**
  - `HashMap<Class<?>, LinkedHashSet<Subscriber>>`. `getSubscribers` returns a **flattened `ArrayList`
    snapshot taken at post time**, so register and unregister during dispatch don't affect events already posted.
  - Method discovery walks every supertype and interface through `getDeclaredMethods()`, skips
    synthetic/bridge methods, and de-duplicates by `MethodIdentifier(name, paramTypes)`, most derived first.
    The arity IAE message is verbatim, including the missing space.
  - **Documented deviation:** a plain IAE where Guava throws `UncheckedExecutionException` wrapping it.
  - `flattenHierarchy` replicates TypeToken's depth-descending order: the class first, `Object` last,
    with deterministic tie-breaking via `LinkedHashMap`.
  - Caches use `ClassValue`, which doesn't pin classes.
  - Unregister has the same IAE message, and a class with no subscriber methods is a silent no-op, as in Guava.
- **Exceptions:**
  - Handler failures are caught as `Throwable` and logged with Guava's format on the
    `com.simplelibs.lava.eventbus.EventBus` logger.
  - `LoggingHandler` logs on `…EventBus.<identifier>` with the verbatim message.
  - DeadEvent is posted when nobody subscribes. It is never re-wrapped.
- Tests:
  - Port `EventBusTest`, `AsyncEventBusTest` (FakeExecutor), `DispatcherTest` (the legacyAsync test
    rewritten single-threaded), `ReentrantEventsTest`, `SubscriberRegistryTest`, `SubscriberTest`, and
    `outside/*` into a separate package.
  - Drop `testRegisterThreadSafety`.
  - Add a differential test that posts randomized event-class hierarchies to both Guava's and Lava's
    bus and compares delivery order. Compare as a multiset where Guava's order is hash-dependent.

## Milestone 7 — `cache` (largest piece)
Public types are ported one-to-one with exact signatures, per `guava/src/com/google/common/cache/*.java`:
- **Interfaces:** `Cache` (12 methods) and `LoadingCache extends Cache, java.util.function.Function`
  (`get`, `getUnchecked`, `getAll` → `ImmutableMap`, deprecated `apply`, `refresh`, `asMap`).
- **Base classes:**
  - `AbstractCache`, with its UOE defaults plus `StatsCounter` and `SimpleStatsCounter`. `SimpleStatsCounter`
    uses plain `long`s; `incrementBy` is included.
  - `AbstractLoadingCache`: `getUnchecked` wraps in `UncheckedExecutionException(cause)` and lets
    `ExecutionError` pass through; `getAll` loops over distinct keys.
  - `ForwardingCache extends collect.ForwardingObject` and `ForwardingLoadingCache`, each with its
    `Simple…` abstract nested class.
- **Value types:**
  - `CacheStats`: IAE on negatives, `minus` floors at 0, `plus` does not saturate (as in 27.0.1),
    rates are `1.0`/`0.0` on zero counts, `Objects.hash` hashCode, and Guava's toString.
  - `RemovalCause`, with package-private `wasEvicted()`.
  - `RemovalListener` and `Weigher` (`@FunctionalInterface`).
  - `RemovalNotification extends AbstractMap.SimpleImmutableEntry`; `create(k, v, cause)`. Its equals
    ignores cause.
  - `RemovalListeners.asynchronous`.
- **`CacheLoader`:**
  - `load`, plus `reload` (default `Futures.immediateFuture(load(k))`) and `loadAll` (default throws
    `UnsupportedLoadingOperationException`, whose constructor is package-private).
  - `from(java.util.function.Function)`, `from(Supplier)`, `asyncReloading` (uses `ListenableFutureTask`).
  - `InvalidCacheLoadException`.
- **`CacheBuilder`:**
  - Every setter, including the three `java.time.Duration` overloads.
  - Port every validation rule and message verbatim: set-once ISEs; `maximumWeight` assigns the field
    *before* its IAE; size+weigher conflicts; refresh needs a positive duration; expire accepts 0;
    `build()` throws `"refreshAfterWrite requires a LoadingCache"`; `checkWeightWithWeigher`.
  - Lenient mode for `from(spec)`, including the weigher WARNING and the quirk that drops maximumSize.
  - Effective max weight: `expireAfterX(0)` behaves as max 0 with cause SIZE.
  - Default ticker: system ticker only if a timed feature is set, otherwise a null ticker.
  - toString format.
  - Package-private getters, `lenientParsing`, `keyEquivalence`/`valueEquivalence`, `setKey/ValueStrength`.
  - `concurrencyLevel` is validated and shown in toString, but has **no behavioral effect**: eviction is
    exact global LRU, equivalent to Guava with `concurrencyLevel(1)`.
- **`CacheBuilderSpec`:**
  - The grammar as in Guava: split on `,` and `=`, trim, and keep empty pieces, so `"a,,b"` is
    `"unknown key "`.
  - The undocumented `refreshInterval` alias, lowercase `d/h/m/s` units, and every error message.
  - equals compares durations normalized to nanos. toString is `CacheBuilderSpec{<raw>}`.
  - `disableCaching()`.

**`LocalCache` (pkg-private) — single-threaded design:**
- **Storage:**
  - One `HashMap<Object, Node>`. With strong keys it is keyed by the key. With weak keys it is keyed by
    an identity `WeakKeyReference` (it carries `System.identityHashCode`), and lookups go through an
    identity probe.
  - Each `Node` holds its key ref, value ref (strong, weak or soft, plus a weight), `accessTime`,
    `writeTime`, and intrusive **access-order** and **write-order** doubly linked lists, so every
    operation is O(1).
  - Key and value comparison goes through `Equivalence`: `equals()` for strong references, `identity()`
    for weak or soft ones.
- **Maintenance at the start of every public operation:**
  - Drain the `ReferenceQueue`s and remove collected entries with COLLECTED.
  - Expire from the heads of the write and access lists (`now - t >= duration`) with EXPIRED.
  - This is more eager than Guava's write/64th-read cleanup but stays within the documented contract.
    It also means `size()` never counts dead entries.
- **Eviction after every write:**
  - If the newest entry alone is heavier than the max, evict only that entry with SIZE.
  - Then, while `totalWeight` (a `long`) exceeds the max, evict the first entry in access order whose
    weight is greater than 0. Weight-0 entries are never evicted by size.
  - A weigher that returns a negative weight throws ISE `"Weights must be non-negative"`.
- **Recency:** reads that hit (the get family, `getIfPresent`, `getAll`/`getAllPresent`, `asMap().get`) and
  all writes move the entry to MRU. `containsKey` and iteration do not.
- **Notifications:**
  - Notifications are queued during an operation and flushed when the outermost operation ends, after
    the state is consistent.
  - A `flushing` flag keeps FIFO order when listeners re-enter the cache.
  - If the listener throws, the `Throwable` is caught and logged at WARNING `"Exception thrown by removal listener"`
    on `com.simplelibs.lava.cache.LocalCache`.
  - Causes follow Guava's table. For example, a put over a collected entry gives COLLECTED, and a
    compute that returns the identical value gives no notification.
- **Loading:**
  - Use a `LOADING` node state.
  - A recursive load of the same key throws ISE `"Recursive load of: " + key`, which reaches the caller
    wrapped in `UncheckedExecutionException`. Recursive loads of other keys work.
  - Reentrant behavior during a load matches Guava. `getIfPresent` and `containsKey` see the key as
    absent and `invalidate` is a no-op. A `put` made during the load wins, and the loaded value is
    discarded with REPLACED while still being returned.
  - Exceptions are wrapped per the table: checked → `ExecutionException`; unchecked →
    `UncheckedExecutionException`; `Error` → `ExecutionError`. An `InterruptedException` re-sets the
    thread's interrupt flag. A null result throws an unwrapped `InvalidCacheLoadException("CacheLoader returned null for key …")`.
  - `getAll` and `loadAll` follow Guava exactly: fallback on UOE, the null-map and null-entry
    messages, and storing extra keys. It records one loadSuccess per bulk call.
- **Refresh:**
  - Triggered by a read hit when `now - writeTime > refreshNanos` (strictly greater) and the entry is not
    already loading. This includes `getIfPresent` and `asMap().get`.
  - If the `reload` future is already done, the new value is stored with REPLACED and returned.
    Otherwise the old value is returned, and the new value is stored by a `directExecutor` listener only
    if the entry hasn't been clobbered in the meantime.
  - A failure is logged at WARNING `"Exception thrown during refresh"`, increments `loadExceptionCount`,
    keeps the old value and does not reset the write time.
  - Explicit `refresh(k)` loads an absent key, reloads a present one, and is a no-op while the key is loading.
- **Stats:** hit/miss/load as in Guava. `asMap()` operations record no hits or misses, but `compute*`/`merge`
  storing a new value records a loadSuccess, as Guava does. `totalLoadTime` is measured with
  `System.nanoTime` (Guava's Stopwatch), not with the configured ticker. Without `recordStats`,
  `stats()` returns all zeros.
- **`asMap()`:**
  - A full `ConcurrentMap`, including `compute`/`computeIfAbsent`/`computeIfPresent`/`merge`.
  - The function runs with the entry unchanged until it returns. **Guava's stuck-loading bug when the
    function throws is not replicated.**
  - Views follow Guava: `values()` is not a `Set`, `keySet` and friends throw UOE on add, and
    `WriteThroughEntry.setValue` re-inserts the entry.
  - Iterators run over a **snapshot of the nodes**, skip dead entries, and never throw CME.
    `iterator.remove()` throws ISE before `next()`. `removeIf` tolerates the predicate mutating the map.
    Order is unspecified.
  - NPE and null-tolerance follow Guava's table, with `@Nullable` on the tolerant parameters.
- **Not ported (documented):**
  - Serialization of cache instances. Guava serializes only the configuration, and that drops
    `recordStats` and refresh anyway.
  - Segment and recency-queue internals and `LongAdder`/`Striped64`.
  - Guava's stuck-loading-on-throw compute bug.
- Tests:
  - Port the black-box parts of `CacheBuilderTest`, `CacheBuilderSpecTest`, `CacheStatsTest`,
    `AbstractCacheTest`, `AbstractLoadingCacheTest`, `RemovalNotificationTest`, `CacheManualTest`,
    `CacheBuilderGwtTest`, `CacheRefreshTest`, `NullCacheTest`, `CacheLoaderTest`, `CacheEvictionTest`
    (the exact weighted-LRU sequences), `CacheExpirationTest`, and `CacheLoadingTest` (single-threaded part,
    with log capture through a `java.util.logging.Handler`).
  - Also port `LocalCacheMapComputeTest` (run sequentially), the black-box cases of `LocalCacheTest`,
    and `ForwardingCacheTest`/`ForwardingLoadingCacheTest` with hand-written recording fakes (no Mockito).
  - Rewrite `CacheBuilderFactory` on the public API, and drive `EmptyCachesTest`,
    `PopulatedCachesTest` and `CacheReferencesTest` across the configuration product.
  - Run the in-house `MapContractTest` against `asMap()` in about 10 configurations.
  - References: package-private test hooks simulate GC (clear and enqueue the refs), plus one real-GC
    smoke test tagged `@Tag("gc")`.
  - **Differential test against Guava:** seeded random operation sequences (get, getIfPresent, put,
    invalidate, getAll, refresh, asMap ops, ticker advances) run against Lava and Guava. Guava is set to
    `concurrencyLevel(1)` and both share one fake ticker. After `cleanUp()`, compare return values and
    exception types, contents, `size()`, stats (except `totalLoadTime`), and the removal-notification
    multiset. Compare the order too whenever the ticker hasn't advanced.

## Review protocol (per milestone, per SCOPE.md)
1. `./gradlew check` is green, then commit.
2. Launch the **`senior-code-reviewer`** sub-agent with an adversarial brief. It hunts for: API
   divergences from the Guava 27.0.1 source (it reads both), behavioral bugs with concrete repro
   inputs, single-threaded reentrancy hazards (mutation during iteration or dispatch, recursive
   loads), missing or weak tests, and Javadoc that contradicts the behavior. Every finding needs a
   severity, a file:line and a repro or argument.
3. **Negotiate:** triage each finding as accept, reject or defer, with a rationale. For rejected or
   disputed findings, send the counter-argument back to the same reviewer through `SendMessage`, so
   it can concede or defend with evidence. Up to 2 rounds, then decide.
4. Fix the accepted findings, each with a regression test. Log every finding, verdict, rationale and
   fix in `REVIEW.md`, then commit.
5. After the last milestone, run one final whole-codebase adversarial review the same way.

## Milestone 8 — Finalization
- `ApiParityTest` passes for all ported types, and `api-exclusions.txt` is final.
- Coverage gate is green. Javadoc builds with no errors (`./gradlew javadoc`).
- A short `README.md` covers the import mapping table, the single-threaded contract, migration notes
  (below) and how to run the tests.
- Run the final whole-codebase adversarial review (see the protocol) and record it in `REVIEW.md`. Commit.

## Migration notes (changes for clients beyond swapping imports; also listed in README and exclusions)
- `com.google.common.base.{Function,Supplier,Predicate}` → `java.util.function.*`. Lambdas and method refs
  don't change. **Anonymous `Predicate` classes must rename `apply` to `test`.** Anonymous Function and
  Supplier classes are unaffected, because their method names are the same.
- Not thread-safe. `CacheBuilder.concurrencyLevel` is accepted but has no effect.
- Omitted members: `Multimap.keys()`/`ImmutableMultimap.keys()` (no Multiset), `ImmutableMultimap`
  static factories and `Builder`, and `Range.canonical(DiscreteDomain)`. Cache instances are not
  serializable.
- `EventBus.register` of a class with a bad-arity `@Subscribe` method throws IAE directly, where Guava
  wraps it in `UncheckedExecutionException`.
- Logger names and `toString` strings that embed class names (`Enums.stringConverter(<fqcn>.class)`)
  carry the `com.simplelibs.lava` package.
- Lava's serialized forms are not wire-compatible with Guava's.

## Risks and mitigations
| Risk | Mitigation |
|---|---|
| Collect view classes are large (Maps alone is 4.3k lines) and full of subtle view semantics | Port close to the Guava source, run contract bases over every view, and fuzz against Guava with random view mutations |
| Cache semantics drift (stats, notification causes, refresh) | Exact rules are listed above; differential random-sequence fuzzing against Guava with `concurrencyLevel(1)` |
| Reentrancy (listeners or subscribers calling back in, loads recursing) | Flush notifications after the state is consistent, take dispatch snapshots, track in-flight loads, and add dedicated reentrancy tests |
| GC-dependent tests are flaky | Simulate GC through package-private hooks, and tag real-GC tests `@Tag("gc")` |
| Hidden Guava dependency creeps into main | Guava is only on the test classpath, plus the `verifyNoGuavaInMain` scan |

---

## Verification (end-to-end)
1. `./gradlew clean check` runs compile with `-Werror`, all JUnit 5 tests, `verifyNoGuavaInMain` and the
   JaCoCo gate. The HTML reports are in `build/reports/tests/test` and `build/reports/jacoco`.
2. `ApiParityTest` is green, so every public member in Guava 27.0.1 for the 8 targets (and the ported
   helper types) exists in Lava with the same generic signature, or is listed in `api-exclusions.txt` with a reason.
3. The differential suites (`*DifferentialTest`, seeded, 1k+ operation sequences each) agree with Guava 27.0.1.
4. `./gradlew dependencies --configuration runtimeClasspath` shows **no dependencies at all**.
5. A smoke check: a small `src/test/java/.../MigrationSmokeTest` mirrors typical Guava client code
   (cache with a loader, expiry and stats; an EventBus with DeadEvent; `ImmutableMap.builder`;
   `Maps.filterKeys`; `Lists.partition`; `Doubles.tryParse`; `Enums.getIfPresent`) using only Lava imports.
6. `REVIEW.md` has an entry per milestone plus the final review, and every accepted finding is fixed
   with a regression test.
