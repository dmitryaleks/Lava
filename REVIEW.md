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
