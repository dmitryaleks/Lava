# Adversarial review log

Per `SCOPE.md` and the review protocol in `PLAN.md`, each milestone is reviewed adversarially by a
dedicated `senior-code-reviewer` sub-agent. The reviewer works on the committed diff, compares it
against the Guava 27.0.1 sources, and backs each finding with a repro, a mutant or a probe against
the real Guava jar. Each finding is triaged as **accepted**, **rejected** or **deferred**. Disputed
findings go back to the reviewer, which concedes or defends them; the outcome is recorded here.

Severity: C = critical, M = major, m = minor, n = nit.

---

## Milestone 1 — base foundations, util.concurrent subset (commit `4619adf`)

The reviewer ran a line-by-line diff against Guava, about 125 behavioural probes against the Guava
jar, 200k random `lenientFormat` cases, a reflection comparison of annotations over 149 members,
and 11 mutation runs. **No critical defects. The ported main code matched Guava in every probe
except one: finding 5.**

| # | Sev | Finding | Verdict | Resolution |
|---|-----|---------|---------|------------|
| 1 | M | `./gradlew javadoc` failed (forward `{@link}`s to classes not yet ported); `check` did not run javadoc | Accepted | The forward links became `{@code}` until the targets landed. `check` now depends on `javadoc`. |
| 2 | M | `ApiParityTest` ignored type-use annotations, `@Deprecated` and varargs, so a spurious `@Nullable` also silently removed a parameter from the NPE sweep. SUBSET mode never reported omissions, constants were not compared, and unregistered public types went unnoticed | Accepted | `ApiParityTest` now compares annotations recursively, plus varargs, `@Deprecated`, `@FunctionalInterface` and constant values. SUBSET mode is gone: every omission is listed in `api-exclusions.txt`. A new check fails on any public Lava type that is not registered. |
| 3 | M | De-synchronised `ExecutionList` had no re-entrancy or Error-propagation tests; the ported concurrent test asserted a guarantee the class no longer gives | Accepted | Removed `testExecute_idempotentConcurrently`. Added tests for add-during-execute, execute-during-execute, a `RuntimeException` from an executor, and `Error` propagation. |
| 4 | m | `getDone_restoresInterruptFlag` was vacuous (its future never threw `InterruptedException`) | Accepted | It now uses a done future whose first `get()` throws `InterruptedException`, and asserts both the retry and the restored flag. |
| 5 | m | Immediate failed and cancelled futures ignored a pending interrupt; Guava's `TrustedFuture.get()` throws `InterruptedException` | Accepted | Mirrored Guava, with a test. |
| 6 | m | Test helpers were weaker than testlib: no class check after reserialization, a single `EqualsTester` pass, a vacuous assertion, dead code in `NullPointerTester` | Accepted | `reserializeAndAssert` checks the class and runs `EqualsTester`. Equals and equivalence testers repeat 3 times. Removed the vacuous assertion and the dead code. |
| 7 | m | Laziness of `presentInstances` and caching of `reverse()` were untested | Accepted | Added tests. |
| 8 | m | Stale Javadoc: `Verify`/`Strings` references, `Objects.equal`, the package-info synchronisation claim, the `googleOptional` parameter name, and the plan's blanket thread-safety rule | Accepted | Rewrote the Javadoc and renamed the parameter. The PLAN thread-safety rule was revised: immutable types carry no note. |
| 9 | m | `ListenableFutureTask` pairs a thread-safe `FutureTask` with an unsynchronised listener list; suggested keeping `synchronized` in `ExecutionList` | **Disputed → resolved** | Rejected re-adding locks, because the scope is single-threaded and partial thread-safety would be misleading. The reviewer accepted, and pointed out the remaining cross-thread risk: `asyncReloading(loader, newSingleThreadExecutor())`. The util.concurrent docs and PLAN now name thread-pool executors, including `newSingleThreadExecutor`, as unsupported. M7 will repeat this on `asyncReloading`. |
| 10 | m | `verifyNoGuavaInMain` is a line-based text scan | Accepted | Added `verifyNoRuntimeDependencies`, which asserts the compile and runtime classpaths are empty. It is wired into `check`. |
| 11 | n | `ApiParityTest.map` rewrote `Functions`/`FunctionalEquivalence` as if they were `Function` | Accepted | The mapping now matches whole words only. |
| 12 | n | Licence header in `EquivalenceTest` reads "diOBJECTibuted" | **Disputed → conceded** | The typo is upstream in Guava's own test file. The reviewer conceded. Fixed anyway (cosmetic). |
| 13 | n | Coverage gate had slack; `--add-opens` applies to all tests | Partly accepted | The gate was tightened (and later set to 85/80 once collect code landed; see M3). The `--add-opens` objection was **withdrawn** by the reviewer after discussion: M5's `HashMap.table` capacity tests need it, and main code never reflects into the JDK. |
| 14 | n | PLAN: "`Equivalence implements BiPredicate` is wrong"; "`from`/`identity` called deprecated"; a migration note missing | **Partly disputed** | The first claim was **conceded**: Guava 27.0.1's `Equivalence` line 37 is `implements BiPredicate<T, T>`. The second was ambiguous wording, now clarified. The third was accepted: the migration notes now cover `.apply` → `.test` on returned predicates. |

## Milestone 2 — Doubles, Enums (commit `5a8e2a7`)

The reviewer ran about 40 mutants, a 90-call side-by-side probe against Guava, and a `tryParse`
check over all 1,114,111 code points (0 differences). **It found no behavioural divergence from
Guava**; every finding concerned test strength or Javadoc.

| # | Sev | Finding | Verdict | Resolution |
|---|-----|---------|---------|------------|
| 1 | M | `ListContract` accepted broken lists (absent typed elements, `equals` with different content, typed `toArray`, list-iterator indices on the backward pass, `trySplit`, sub-list windows) | Accepted | `ListContract` was rewritten: typed absent elements, same-size and shorter `equals` negatives, typed and aliasing `toArray` checks, full list-iterator position checks in both directions, `tryAdvance` and split traversal, and sub-list window negatives. It also gained a `CollectionContract` sibling. |
| 2 | M | Mutants of `DoubleArrayAsList` window logic and data survived | Accepted | Added a deterministic quirks test over `[0.0, -0.0, NaN, 1, 1]`: windowed `contains`/`indexOf`, `set` bounds before null, and `equals`/`hashCode` with ±0 and NaN. |
| 3 | m | Vacuous `hasSize(1)` assertion | Accepted | Fixed. |
| 4 | m | Differential test gaps: `to > length` never generated, `reverse(double[])` and `BYTES` missing, `toArray` only given views, `ensureCapacity` length only, messages not compared, dead code | Accepted | All fixed. Exception messages are now compared and match Guava. |
| 4b | n | Comparator magnitude compared via `signum` | Rejected (agreed cosmetic) | The magnitude is unspecified by the `Comparator` contract. |
| 5 | m | No -0.0 in `testSortDescending`; regex never asserted identical to Guava's | Accepted | Added a signed-zero/NaN sort test and a reflection test that the pattern and flags equal Guava's. |
| 6 | m | Singleton `toString`s and `Enums.stringConverter` `equals`/`hashCode` untested | Accepted | Added tests. |
| 7 | m | Javadoc: `Ints.stringConverter` reference, Guava's `reverse` `@throws` typo (`>` for `<`), GWT note, "thread-safe as" wording | Accepted | Fixed. The `reverse` doc typo is an upstream Guava doc bug, corrected in Lava. |
| 8 | m/n | `GcFinalization` used `currentTimeMillis` | Accepted | Switched to `nanoTime`. The unloading test was confirmed non-vacuous by two mutants. |
| 9 | n | Unused helpers and long lines | Partly accepted | `assertSetWritesThrough` now verifies the restore; it is used from M4. `OtherEnum` is now used by the new equality test. |

## Milestone 3 — immutable collections core, minimal Multimap family (commit `ab4a1aa`)

Review method:
- a line-by-line diff of about 35 ported classes against Guava;
- about 1,000 probe lines run side by side against the Guava jar (the same probe source, compiled twice with the package swapped);
- a randomized builder differential of 3,000 sequences;
- a null sweep of 460 calls;
- serialization round trips;
- about 55 mutants.

**No critical defects.** The immutable collections matched Guava everywhere except findings 1 and 2.

| # | Sev | Finding | Verdict | Resolution |
|---|-----|---------|---------|------------|
| 1 | M | `ImmutableSetMultimap.Builder.orderValuesBy` deduplicated values by `equals`. Guava's `ImmutableSortedSet` collapses values that compare equal. The Javadoc still promised an `ImmutableSortedSet`. | Accepted | `valueSet` now sorts, then keeps the first value of each run that compares equal. This reproduces `ImmutableSortedSet.construct` for `size`, `toString`, `inverse` and `values`. One difference remains: the sets are plain `ImmutableSet`s, so `contains` uses `equals`. It is documented on `orderValuesBy` and in the `collect` package doc. The new `ImmutableSetMultimapDifferentialTest` compares 500 random builds against Guava, using a comparator that is inconsistent with `equals`. |
| 2 | m | One-entry maps were `RegularImmutableMap`s, so `values()` was not a `Set`. Guava returns a `SingletonImmutableBiMap`. | Accepted | Added `SingletonImmutableMap`: Guava's `SingletonImmutableBiMap` without the BiMap part. `of(k, v)`, the builder and `copyOf` use it for a single entry. It is tested against Guava for `values().equals(Set)`, `hashCode`, the views and serialization. |
| 3 | m | The contract suites missed real regressions (a–g, see below). | Accepted | All seven fixed (see below). |
| 4 | m | `ApiParityTest` compared only `@Deprecated` and `@FunctionalInterface`, so removing `@SafeVarargs` went unnoticed. Exclusions match by id only. | Partly accepted | All runtime declaration annotations are now compared, `@SafeVarargs` included. **Rejected:** pinning signatures in exclusions. An excluded member is absent or different by definition, every exclusion states a reason, and a stale exclusion already fails the test. |
| 5 | m | Stale Javadoc: references to the unported `ImmutableSortedSet`, `ImmutableSortedMap`, `ImmutableMultiset`, `ImmutableListMultimap`, `SortedSetMultimap` and `Multimaps.index`; the serialization note in `orderValuesBy`; drift from PLAN. | Accepted | Rewritten. The test stand-ins `HashMultimap` and `LinkedListMultimap` now document what they really are. |
| 6 | m | `collect` had no `package-info.java`. | Accepted | Added. It states that Lava is single-threaded and lists the deliberate differences. |
| 7 | n | Dead helper code, while the coverage gate was lowered. | Partly accepted | Pruned `NonTerminalImmutableBiMapEntry`, `getNextInValueBucket`, `Maps.UnmodifiableEntrySet`/`UnmodifiableEntries` and other unused helpers. `TransformedIterator`, `keyIterator`/`valueIterator` and the `AbstractMultimap` skeleton views have been in use since M5. **Rejected:** making `ImmutableMultimap.entries()` abstract. In Guava it is a concrete public method, and `ApiParityTest` would rightly flag the changed modifier. The unreachable `createEntries()` stays, with a comment. |
| 8 | n | The `valueComparator` field is vestigial. `FieldSetter` mutates final fields, which future JDKs restrict (JEP 500). | Partly accepted | Removed the field and its setter. **Deferred:** replacing the field-setter `readObject` with a `SerializedForm` proxy. The current code works on the Java 17 target and mirrors Guava's stream layout. Revisit if Lava moves to a JDK that restricts final-field mutation. |
| 9 | n | `CollectorTester` lacked the UNORDERED and IDENTITY_FINISH checks. `reserializeAndAssert` does not compare `toString`. | Partly accepted | Added both `CollectorTester` checks. **Rejected:** the `toString` comparison. testlib's `SerializableTester` does not make it either, and `equals` plus the class check already cover the contract. |

Finding 3 in detail:

| Part | Regression the suites missed | Fix |
|---|---|---|
| a | An entry `contains` that ignores the value | `CollectionContract` probes entries with the right key and a wrong value. |
| b | `get(absent)` after deserialization | The differential test reserializes, then queries an absent key. |
| c | List cases had no duplicates | New list cases with duplicate elements. |
| d | `copyOf(Iterable<Entry>)`, `putAll(Iterable)`, `getOrDefault` and flood detection were uncovered | Now covered by the differential and singleton tests. |
| e | Flooding tests never asserted the fallback class | They assert `JdkBackedImmutableSet`. |
| f | Null checks and their messages were untested | `NullPointerTester` sweeps the multimap statics and builder. A new test compares the null messages with Guava: `null key in entry: null=[1]`, a flattened null key with an empty stream, and others. |
| g | The `reserialize` result was discarded | The reserialized `asList` now goes through the list contract. |

## Milestone 4 — collect.Lists (commit `2e49626`)

Review method:
- about 260 probes against the Guava jar;
- a random operation-sequence fuzz over 22 views: 9 seeds of 3,000–6,000 rounds each, with 0 differences;
- a reflection comparison of all nested classes;
- 81 mutants.

**No production divergence from Guava.** About 20 mutants that changed user-visible behaviour survived. Every finding was about test strength, dead code or Javadoc.

| # | Sev | Finding | Verdict | Resolution |
|---|-----|---------|---------|------------|
| 1 | M | The `reverse()` list-iterator state machine and the mutation contract were unverified. testlib's `ListIteratorTester` was not ported. | Accepted | Ported `ListIteratorTester`. The new `ListsIteratorTest` runs it exhaustively and checks the backing list after every sequence. The runs cover every 4-step sequence, start indices 0, middle and end, and sizes 0, 1 and 3. The views tested are `reverse` of `ArrayList`, `LinkedList`, both sub-lists and `Arrays.asList`, plus `transform` and its compositions with `reverse`. `IteratorTester` covers the views' iterators, and every read-only view gets an unmodifiable `ListIteratorTester` run. |
| 2 | M | Documented behaviour was untested: the `cartesianProduct` defensive copy, the null-element NPE, empty axis versus overflow; `charactersOf` duplicates; the `reverse(ImmutableList)` fast path; the `RandomAccess`/`Serializable` flags. | Accepted | Added a test for each, including a Guava comparison of the cartesian edge cases. Writing them showed that an empty axis in any position wins over the overflow check, not only one placed before the large axes, because every axis is copied first. |
| 3 | M | `testTransformedSequentialIterationUsesBackingListIterationOnly` was weakened: its `AbstractSequentialList` tolerated any extra call. | Accepted | The list is now a dynamic proxy that supports only `size()` and `listIterator(int)` and throws `UnsupportedOperationException` for everything else. That is as strict as Guava's `ForwardingList`. A separate test asserts that the proxy is strict. |
| 4 | m | The contracts had no null or duplicate fixtures and no null queries, contained a vacuous assertion, derived expected values from the system under test, and the class Javadoc overstated the coverage. | Accepted | `ListsContractTest` gained duplicate and null fixtures. `ListContract` now checks `contains(null)`, `indexOf(null)` and `lastIndexOf(null)`, as Guava's `ALLOWS_NULL_QUERIES` does, and its mutation probes use null-tolerant lists. The vacuous line is gone, expected values come from the fixtures, and the Javadoc is accurate. |
| 5 | m | `ListsDifferentialTest` covered 8 operation kinds over 2 backings and never compared traits. | Accepted | Adopted the reviewer's fuzz as `ListsViewDifferentialTest`. It covers 21 view kinds, every `List` method, sub-list mutations and random `Iterator`/`ListIterator` sequences. It compares results, exception messages, the view, the backing list and the `RandomAccess`/`Serializable` flags. It also surfaced a harness problem: HotSpot drops the messages of implicit exceptions thrown from hot compiled code. The test JVM now runs with `-XX:-OmitStackTraceInFastThrow`. |
| 6 | m | Dead or weakly tested code: `listIteratorImpl`, `subListImpl`, `AbstractListWrapper`, `RandomAccessListWrapper`, `cast` and `ReverseList.removeRange` were dead; `hashCodeImpl` was never compared with `List.hashCode`; the `addAllImpl` test ignored order; `CollectMath.divide` was untested. | Accepted | Pruned the dead helpers. `hashCodeImpl` is now compared with `List.hashCode`, nulls included. The `addAllImpl` test now checks order. That exposed a wrong expectation in Guava's own `ListsImplTest`, hidden by its order-insensitive assertion; it is corrected. The new `CollectMathTest` compares `divide` and `log2` in every rounding mode, plus the overflow helpers, against Guava's `IntMath` and `Ints`. |
| 7 | m | Stale Javadoc: `Sets`/`Queues`, `FluentIterable`, advice to use `Iterables`/`Collections2`, a thread-safety claim, the `partitionSize` parameter name, an inaccurate `cartesianProduct` NPE clause, GWT comments and TODOs. | Accepted | Fixed. The `cartesianProduct` `@throws` now says that axes after the first empty one are not checked. |
| 8 | n | The partition overflow fix was documented only in a code comment. | Accepted | Documented in the `partition` Javadoc. A test shows Guava 27.0.1 failing where Lava returns the right sublist. The README's migration notes will list it (M8). |
| 9 | n | Long test lines; `newCopyOnWriteArrayList(Collection)` untested; `isPartialView` never executed. | Accepted | Reformatted `ListsContractTest`. The `Collection` branch is tested. `isPartialView` is observed through `copyOf` identity: a `charactersOf` result is reused, and cartesian element lists are copied. |

**M4 mutant re-run.** The reviewer's 40 surviving mutants were re-applied to the new tests:

- **Killed:** 28.
- **Equivalent:** 6 (sub-list double-wrapping, `CopyOnWriteArrayList` already being `RandomAccess`, an unreachable saturated add, `/ by zero` thrown by the JVM itself, and others).
- **Pattern gone:** 3, because the code they targeted was rewritten.
- **Fixed by follow-up tests:** 3.
  - The weakened sequential-transform test never called `size()`. Guava's `Iterables.elementsEqual` does, so the test now asserts the size.
  - The cartesian `isPartialView` check compared two different `get(i)` results.
  - Out-of-range `get`, `listIterator` and `subList` messages are now compared with Guava for every view.

## Milestone 5 — collect.Maps, MapDifference, BiMap/HashBiMap, Range (commit `e5a3fd0`)

Review method:
- a `diff -w` of every ported file;
- about 330 probes run against the Guava jar;
- three randomized transcript diffs (41 view factories × 34 operation kinds, with nulls and reverse comparators);
- a 4,000-case `Range` tie differential;
- 44 mutants: 11 killed, 33 survived, of which 27 were real test gaps.

**The ported code is faithful.** The one behavioural divergence was in the hand-written `SynchronizedBiMap` (finding 1).

| # | Sev | Finding | Verdict | Resolution |
|---|-----|---------|---------|------------|
| 1 | M | `SynchronizedBiMap` did not forward the `Map` default methods. `replaceAll` therefore went through `setValue` and threw `value already present`, where Guava succeeds. | Accepted | It now forwards `forEach`, `replaceAll`, `getOrDefault`, `putIfAbsent`, both `replace` overloads, the three `compute*` methods, `merge` and `remove(k, v)`, as Guava's `Synchronized.SynchronizedMap` does. It also caches its views, so `values() == values()` as in Guava. |
| 2 | M | `SynchronizedBiMap` was effectively untested: four mutants survived, and a reference to a nonexistent `SynchronizedBiMapTest` dangled. | Accepted | New `SynchronizedBiMapTest`: wrapper idempotence, inverse caching and round trip, `forcePut`, view caching, serialization, and every `Map` default method compared with Guava's wrapper (shifts, permutations and duplicate values). |
| 3 | M | The `MapsCollectionTest` replacement lacked several families: filter-of-filter, `filterKeys`/`filterEntries` on BiMaps, sorted and navigable `filterKeys`, `asMap(SortedSet)`. It had no Java 8 `Map` methods, and the serialization of several views was untested. | Accepted | `MapsContractTest` gained these 7 `MapInterfaceTest` families. New `MapsViewFuzzTest`, adapted from the reviewer's harness, compares 54 view and wrapper families with Guava. It covers null keys and values, reverse-ordered and `naturalOrder()` trees, sorted-only maps, filters of filters, every BiMap filter and wrapper, `forEach` on the map and its views, `replaceAll`, `compute*`, `merge`, `forcePut`, comparators and liveness of derived views. Serialization tests now cover `HashBiMap.inverse()` (direction preserved), `unmodifiableNavigableMap`, `unmodifiableBiMap`, `synchronizedBiMap` and `immutableEntry`. |
| 4 | m | Blind spots in `MapsViewDifferentialTest`: navigation came after a `put`, so read-only views threw before reaching it; navigable comparators were never compared. | Accepted | Navigation now runs before the write, and comparators are compared by behaviour. The broader cases live in `MapsViewFuzzTest`. |
| 5 | m | The `synchronizedNavigableMap` comment claimed "the same semantics" as Guava, but the JDK wrapper does not cache its views. | Accepted | The comment and the Javadoc now state the difference. |
| 6 | m | The `synchronizedBiMap` Javadoc promised thread-safety, and its views were uncached. | Accepted | Rewritten for the single-threaded contract. The views are now cached (see 1). |
| 7 | m | The `Range` sorted-set fast paths, the tie-breaking in `encloseAll` and the deprecated `apply` were never exercised. | Accepted | Added a randomized comparison with Guava over natural, `naturalOrder()`, unmodifiable and reverse-ordered sets, extreme-element cases, a BigDecimal tie differential (`1.0` vs `1.00`) and an `apply`/`test` check. Two surviving fast-path mutants are equivalent: dropping a fast path takes the slow path, which gives the same result. |
| 8 | m | `Maps` guarantees were unasserted: `unmodifiableNavigableMap` idempotence and descending-map caching, the `subMap` guard message, the `asConverter` message, the first-entry null check in `immutableEnumMap`. | Accepted | Each is tested and compared with Guava. Re-running the mutants found two more gaps, now tested: `keySet()`/`values()` `forEach`, and `Sets.filter` composition, reachable through a user map that exposes a filtered key set. The `immutableEnumMap` identity shortcut is also tested. |
| 9 | m | Garbled or false Javadoc: `{@code Function) Multimaps.index}`, a nonexistent `base.Functions.forMap`, `ImmutableSortedMap` advice and examples, a package-private predicate class in public docs, `Range.apply`'s deprecation note, the `DiscreteDomain`/`ContiguousSet` pointers, a `Multiset` reference in `Serialization`. | Accepted | Fixed. |
| 10 | n | Dead code: `Maps.DescendingMap` and `AbstractNavigableMap.DescendingMap` (both subclasses override `descendingMap`), `Range.lowerBoundFn`/`upperBoundFn`, every unused `standard*` helper in the package-private `Forwarding*` classes. | Accepted | Removed. The `Forwarding*` class docs now say that the helpers are not ported. |
| 11 | n | Vacuous upstream tests were carried over: `assertWontGrow` re-checked `map1`, and `RangeNonGwtTest` repeated a call. | Accepted | `assertWontGrow` now checks `map2`, and the duplicate call is removed. |

**M5 mutant re-run.** The reviewer's 34 surviving mutants were re-applied to the new tests:

- **Killed:** 30 (four of them after the follow-ups in finding 8).
- **Equivalent:** 4, all observably identical:
  - `encloseAll` without its fast path;
  - `containsAll` without the `naturalOrder()` disjunct;
  - `subMap` without the `naturalOrder()` identity check, which cannot fire because a `Range` never has lower > upper;
  - `SynchronizedBiMap.equals` without its `this` shortcut.
