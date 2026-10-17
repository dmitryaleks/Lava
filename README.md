# Lava

Lava is a single-threaded, dependency-free Java 17 equivalent of a subset of
[Guava](https://github.com/google/guava) 27.0.1. It keeps Guava's public API, so client code
migrates by changing imports, but it replaces Guava's concurrency machinery (segments, locks,
`LongAdder`, CAS, thread-local queues) with plain code for programs that use these classes from one
thread.

Covered entry points, with the public helper types their signatures need:

| Guava 27.0.1 | Lava |
|---|---|
| `com.google.common.primitives.Doubles` | `com.simplelibs.lava.primitives.Doubles` |
| `com.google.common.cache.*` (`Cache`, `LoadingCache`, `CacheBuilder`, `CacheBuilderSpec`, `CacheLoader`, `CacheStats`, `RemovalListener`, ...) | `com.simplelibs.lava.cache.*` |
| `com.google.common.eventbus.*` (`EventBus`, `AsyncEventBus`, `Subscribe`, `DeadEvent`, ...) | `com.simplelibs.lava.eventbus.*` |
| `org.checkerframework.checker.nullness.qual.NonNull` (and `Nullable`) | `com.simplelibs.lava.checker.nullness.qual.NonNull` (and `Nullable`) |
| `com.google.common.base.Enums` | `com.simplelibs.lava.base.Enums` |
| `com.google.common.collect.ImmutableMap` | `com.simplelibs.lava.collect.ImmutableMap` |
| `com.google.common.collect.Maps` | `com.simplelibs.lava.collect.Maps` |
| `com.google.common.collect.Lists` | `com.simplelibs.lava.collect.Lists` |

Supporting public types are ported with them:
- `base`: `Converter`, `Optional`, `Equivalence`, `Ticker`, `Preconditions`.
- `collect`: `ImmutableCollection`, `ImmutableList`, `ImmutableSet`, `BiMap`, `HashBiMap`, `Range`, `BoundType`, `MapDifference`, `SortedMapDifference`, `Multimap`, `SetMultimap`, `ImmutableMultimap`, `ImmutableSetMultimap`, `UnmodifiableIterator`, `UnmodifiableListIterator`, `ForwardingObject`.
- `util.concurrent`: `ListenableFuture`, `ListenableFutureTask`, `ExecutionList`, `Futures` (immediate futures and `getDone`), `MoreExecutors.directExecutor`, `UncheckedExecutionException`, `ExecutionError`.

## Migrating

The general rule is to replace `com.google.common.` with `com.simplelibs.lava.` and
`org.checkerframework.` with `com.simplelibs.lava.`. Guava's `Function`, `Supplier` and `Predicate`
become `java.util.function.Function`, `Supplier` and `Predicate`.

Beyond the imports:

- **Predicates.**
  - Lambdas and method references need no change.
  - Anonymous `Predicate` classes must rename `apply` to `test`.
  - Code that calls `.apply(x)` on a predicate Lava returns (for example `Equivalence.equivalentTo(t)`) must call `.test(x)`.
  - `Range` keeps a deprecated `apply`. Anonymous `Function` and `Supplier` classes are unaffected.
- **Threads.** Nothing is thread-safe except immutable values. Confine caches, event buses, mutable collections and views to one thread.
  - Executors given to `AsyncEventBus`, `CacheLoader.asyncReloading` and `RemovalListeners.asynchronous` must run their tasks on that thread: a direct executor, or a queue the thread drains.
  - `CacheBuilder.concurrencyLevel` is accepted but has no effect. Eviction is exact LRU, as with Guava's `concurrencyLevel(1)`.
- **Omitted members**, all listed with reasons in `src/test/resources/api-exclusions.txt`:
  - `Multimap.keys()` and `ImmutableMultimap.keys()` (Lava has no `Multiset`);
  - `ImmutableMultimap`'s static factories and `Builder` (use `ImmutableSetMultimap`);
  - `Range.canonical(DiscreteDomain)`;
  - the `Futures` combinators.
- **`ImmutableSetMultimap.Builder.orderValuesBy`** collapses values that compare equal, as Guava does, but yields ordered `ImmutableSet`s rather than `ImmutableSortedSet`s, so `contains` uses `equals`.
- **`Lists.partition`** returns correct sublists for lists of more than 2<sup>30</sup> elements, where Guava 27.0.1's `int` arithmetic overflows.
- **Caches**:
  - Expired entries, and entries whose references the collector has enqueued, are removed at the start of every operation, so `size()` never counts them.
  - Removal notifications are delivered on the calling thread at the end of each operation and before a loader runs, as in Guava, and also before an `asMap().compute*` function runs.
  - A `get` of a key whose load is running on the call stack throws `IllegalStateException("Recursive load of: ...")`, as in Guava. Lava throws the same exception where Guava would hang: an `asMap().compute*` of such a key, or a `get` of the key about to load from a removal listener.
  - `asMap().compute*` that throws, or that removes an existing entry by returning null, leaves the cache consistent, and so does a weigher that throws for a loaded value. In Guava 27.0.1 the entry is left stuck loading.
  - When a compute function itself writes or removes its key, the counts stay right. As in Guava, the written value wins over a non-null result and is removed by a null one; if the function returns the old value, Lava keeps the written value, which Guava drops without a notification.
  - Weighers must not call back into the cache.
  - **Performance:** creating an `asMap()` iterator copies a snapshot of the table, which costs O(n) even if only the first element is read. Avoid `iterator().next()` probes of large caches.
  - Caches are not serializable.
- **Event bus**: errors found while discovering `@Subscribe` methods are thrown directly. A wrong-arity method gives `IllegalArgumentException`, and a missing class gives `NoClassDefFoundError`; Guava wraps these in `UncheckedExecutionException` and `ExecutionError`.
- **Names**: logger names and `toString`s that embed class names (`Enums.stringConverter(<class>.class)`) use the `com.simplelibs.lava` packages.
- **Serialization**: Lava's serialized forms are not wire-compatible with Guava's.

The package documentation of each package (`package-info.java`) lists its differences in detail.

## Building and testing

```
./gradlew check
```

`check` runs every step below:

1. Compiles the main code with `-Xlint:all -Werror` and runs the JUnit 5 tests.
2. Builds the Javadoc.
3. Enforces a JaCoCo gate of 85% line and 80% branch coverage on the main code.
4. Verifies that the main code neither mentions nor depends on Guava. The compile and runtime classpaths are empty.

Reports are written to `build/reports/tests/test` and `build/reports/jacoco`.

Guava 27.0.1 is a *test-only* dependency, used as an oracle:

- **`ApiParityTest`** compares every public type, member, generic signature and annotation with Guava's by reflection. Every intentional difference is listed in `api-exclusions.txt`.
- **`*DifferentialTest` and `*FuzzTest`** run seeded random operation sequences against Lava and Guava side by side, comparing results, exception messages and state.
- **Ported Guava tests and contract suites.** Guava's own tests are ported to JUnit 5 and AssertJ. The contract suites (`MapInterfaceTest`, `ListIteratorTester`, the list and collection contracts) stand in for guava-testlib.

Longer fuzz runs: set `LAVA_CACHE_FUZZ_SEEDS=400` (the default is 40, each seed running 25 random configurations) for `CacheDifferentialTest` and `CacheReentrancyFuzzTest`. Set `LAVA_CACHE_FUZZ_TRACE=<seed>:<round>` to print one round step by step. Tests tagged `gc` depend on the garbage collector.

`PLAN.md` describes the design and `REVIEW.md` records the adversarial review of each milestone.

## License

Lava is derived from Guava and is licensed under the Apache License 2.0 (`LICENSE`); see
`NOTICE` for attributions.
