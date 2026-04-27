# Cross-Review: software-engineer — PROPERTIES

**Reviewer:** software-engineer
**Document reviewed:** docs/luopan-compass/p4-bearing-history/PROPERTIES-luopan-p4-bearing-history.md (v0.3-draft)
**Date:** 2026-04-27
**Iteration:** 3

---

## Focus of This Review

Iteration 3 focuses on:
1. Whether the three High findings from iteration 2 (F-01: `IDriftDetector`, F-02: `FakeBearingDao`, F-03: `PROP-CAL-040` reclassification) are genuinely resolved in v0.3.
2. Full sweep of the remaining properties for implementability, test-level correctness, and pass-condition feasibility.

---

## High Findings Resolution Assessment

### F-01 (IDriftDetector) — Partially Resolved

**Status: Partially resolved — residual TSPEC conflict unaddressed.**

PROPERTIES v0.3 correctly adds a "Creation prerequisite" note to PROP-CAL-036 and PROP-CAL-037 stating that `IDriftDetector` must be created in PLAN task B-3 and that TSPEC §9.3b is superseded. The PLAN v0.2 also correctly mandates B-3 (create `IDriftDetector`) before B-4 (wire into `CompassViewModel`).

However, TSPEC v0.2 still contains two structurally inconsistent artifacts that are not superseded by either the PROPERTIES or the PLAN:

- **TSPEC §9.3b** (line 1578): `class FakeDriftDetector(…) : DriftDetector(FakeClock(0L))` — the fake subclasses the concrete class, not `IDriftDetector`. This will not compile once `CompassViewModel` is updated to accept `IDriftDetector` and implementers try to inject a `FakeDriftDetector` that does not implement `IDriftDetector`.
- **TSPEC §6.1.5** (line 800): `private val driftDetector: DriftDetector? = null` in the Factory — the Factory still accepts the concrete type, contradicting the PLAN B-4 mandate and PROP-CAL-037.

The PROPERTIES note says TSPEC §9.3b is "superseded by the PLAN B-3 mandate." The PLAN text (B-4 note) says "CompassViewModel accepts `IDriftDetector` (not `DriftDetector`)." These are prose overrides that create a three-way inconsistency: PROPERTIES says interface, PLAN says interface, TSPEC says concrete class. An implementer following TSPEC §6.1.5 literally will produce code that conflicts with PROP-CAL-037's pass condition. The TSPEC must be updated.

### F-02 (FakeBearingDao) — Resolved

**Status: Fully resolved.**

The codebase confirms `BearingDao` is declared as `interface BearingDao` (not an abstract class). The §1.4 section note in v0.3 correctly states `FakeBearingDao` is a hand-written class implementing the interface. The `Dispatchers.setMain(testDispatcher)` requirement is reinforced in PROP-HIST-031, PROP-HIST-032, and PROP-HIST-033. The `FakeBearingDao` specification in TSPEC §9.2 is structurally correct. This finding is closed.

### F-03 (PROP-CAL-040 reclassification) — Resolved

**Status: Fully resolved.**

PROP-CAL-040 has been reclassified from Unit (`CompassViewModelDriftTest.kt`) to Integration instrumented (`RecalibrationBannerTest.kt`). The pass condition correctly requires a real `SettingsRepository` backed by a real `SharedPreferences` instance, a second `SettingsRepository` instance over the same file to simulate process restart, and `@After` cleanup. This is implementable. This finding is closed.

---

## Findings

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| F-01 | Medium | **TSPEC §9.3b and §6.1.5 still show `FakeDriftDetector : DriftDetector` and `driftDetector: DriftDetector?` respectively.** PROP-CAL-036 and PROP-CAL-037 correctly require `IDriftDetector`, and the PLAN B-3/B-4 mandate is correct. But TSPEC §9.3b's code snippet and §6.1.5's Factory signature remain the concrete-type versions. The PROPERTIES note says they are "superseded," but an implementer following only the TSPEC (the primary engineering artifact) will write `FakeDriftDetector : DriftDetector(FakeClock(0L))` and `CompassViewModel(… driftDetector: DriftDetector? …)`. This will cause a type mismatch at PLAN B-4 when `CompassViewModel` must accept `IDriftDetector`. The TSPEC (not just the PROPERTIES note) must be updated to declare the interface and correct the Factory and FakeDriftDetector signatures. | PROP-CAL-036, PROP-CAL-037; TSPEC §6.1.5, §9.3b |
| F-02 | Medium | **PROP-CAL-038 classifies as "Integration (Robolectric)" but no `@RunWith(RobolectricTestRunner::class)` or `@Config` annotation is specified in the PLAN task.** PLAN task B-7 places `DriftDetectorIntegrationTest.kt` in `app/src/test/java/com/luopan/compass/drift/` (the JVM test source set) and describes it as "JVM integration." PROP-CAL-038's pass condition requires `@RunWith(RobolectricTestRunner::class)` and `@Config(sdk = [Build.VERSION_CODES.TIRAMISU])`. However, TSPEC §9.3 (`AT-CAL-INT-01`) drives `AccelerometerVarianceTracker → DriftDetector.onFrame()` directly — these are both pure Kotlin classes with no Android dependency. The integration test as specified in TSPEC §9.3 (lines 1441–1477) instantiates `FakeClock`, `AccelerometerVarianceTracker`, and `DriftDetector` — no `Application` context is required. Robolectric is unnecessary for this particular test. The conflict: PROP-CAL-038 says "Robolectric required"; TSPEC §9.3 shows a plain JVM `runTest` block with no Android runtime dependency. The test file location (`src/test/`) and PLAN task B-7 both imply plain JVM. If this test truly does not involve `CompassViewModel` (which would require `AndroidViewModel`), Robolectric is not needed and the PROP-CAL-038 Robolectric classification is incorrect. If the intent is to test the full chain including `CompassViewModel`, then the TSPEC §9.3 test body is wrong and must be rewritten. The classification conflict must be resolved. | PROP-CAL-038; TSPEC §9.3; PLAN B-7 |
| F-03 | Medium | **PROP-HIST-040 pass condition references `dao.getAll()` in an instrumented test without specifying which DAO instance or thread-safety guarantee.** The pass condition states "`dao.getAll()` count == N−1 immediately after swipe callback fires." In `BearingHistorySwipeTest.kt` (an instrumented test), calling a suspend DAO function (`getAll()`) from the main thread requires `allowMainThreadQueries()`. The production `LuopanDatabase` uses `SupportFactory` (SQLCipher) and does not call `allowMainThreadQueries()`. `LuopanDatabase.buildInMemory()` does call `allowMainThreadQueries()` (confirmed at line 41 of `LuopanDatabase.kt`). The pass condition must specify that the test uses a separate in-memory database instance (`LuopanDatabase.buildInMemory()`) seeded with test records, not the production database, to avoid a `java.lang.IllegalStateException: Cannot access database on the main thread` failure. The same gap affects PROP-HIST-043 and PROP-HIST-044 which also call `dao.getAll()` or `dao.getAll()` equivalents. | PROP-HIST-040, PROP-HIST-043, PROP-HIST-044 |
| F-04 | Medium | **PROP-CAL-019b classifies as both "Integration" category and "Integration" level (`RecalibrationBannerTest.kt`) but the unit-level sub-test uses `FakeDriftDetector` injected into `CompassViewModel` — which requires `AndroidViewModel` and therefore Robolectric or instrumentation.** The pass condition says "Using `FakeDriftDetector` injected into `CompassViewModel`" and then separately says "in the instrumented scenario, navigate to History tab." The first half implies a unit-level `CompassViewModel` test (JVM or Robolectric); the second half is a full instrumented E2E scenario. These are two different test verification mechanisms described in a single pass condition with no clear boundary. An implementer cannot determine which test file and which level covers which assertion. The pass condition must be split: (a) a unit/Robolectric assertion on `CompassViewModel.driftBannerState` remaining `HIDDEN` when `interferenceState = WARNING`; (b) a separate E2E Espresso assertion on drift banner visibility. Without this split, the test file assignment is ambiguous and the test cannot be written. | PROP-CAL-019b |
| F-05 | Low | **PROP-HIST-062 pass condition still checks integer resource ID inequality (`R.string.empty_no_bearings != R.string.empty_no_results`) rather than resolved string value inequality.** Integer resource IDs for distinct string resources are guaranteed to differ by the build system — this assertion is trivially true at compile time and adds no value. The meaningful check is `context.getString(R.string.empty_no_bearings) != context.getString(R.string.empty_no_results)`. This was raised as F-11 (Low) in iteration 1 and remains unresolved in v0.3. | PROP-HIST-062 |
| F-06 | Low | **PROP-HIST-035 pass condition is not a behavioral test of Fragment destruction.** The condition "After ViewModel recreation, `searchQuery.value` equals the query set before recreation" is satisfied by simply testing that a new `BearingHistoryViewModel` instance with `savedStateHandle["query"] = "abc"` returns "abc" from `searchQuery.value`. This does not verify that the ViewModel is actually created with the `SavedStateHandle` in the Fragment recreation path. A more meaningful assertion would use `ActivityScenario.recreate()` or verify the ViewModel receives the correct `SavedStateHandle` at recreation. As written, this passes even if `SavedStateHandle` is never wired in the Fragment. | PROP-HIST-035 |
| F-07 | Low | **PROP-CAL-026 pass condition for "detector timer reset" is unverifiable from an E2E test.** The pass condition states "detector timer reset" as one of three required assertions after RESULT_OK on the drift banner body tap. From an E2E test in `RecalibrationBannerTest.kt`, there is no direct way to observe `DriftDetector.elapsedCountingMs()` — it is `internal` to the production module and exposed for testing only within `src/test/`. The TSPEC §9.6 suggests asserting `assertNull(driftDetector.elapsedCountingMs())` from a unit test. The E2E pass condition should replace "detector timer reset" with an observable proxy: "a new `DriftEvent.TRIGGERED` cannot fire within the next 60 seconds without a new COUNTING window starting" — or more practically, replace it with the unit-level assertion in `CompassViewModelDriftTest.kt` (PROP-CAL-043 already covers the `driftCooldownTimestampMs == 0L` reset). The "detector timer reset" sub-assertion in the E2E test should be dropped or replaced with a proxy observable assertion. | PROP-CAL-026 |
| F-08 | Low | **PROP-HIST-036 note about Fragment destruction on tab navigation contradicts the default ViewPager2 behavior.** The property states "ViewModel is destroyed when the Fragment is destroyed on tab navigation." Whether the Fragment is actually destroyed on tab navigation depends on the `FragmentManager` back-stack behavior and whether `ViewPager2` or `TabLayout + NavController` is used. The TSPEC uses `NavController` with `TabLayout` (not `ViewPager2`). With `NavController.navigate()`, re-navigating to a previously visited destination pops the back stack and re-creates the Fragment. This does destroy the Fragment-scoped ViewModel — so the statement is correct for the NavController approach. However, the pass condition ("new `BearingHistoryViewModel` instance has `searchQuery.value == ""`") is still trivially true for any new instance (default value is `""`). A stronger pass condition would navigate away, navigate back, and assert the ViewModel instance is different from the first one. | PROP-HIST-036 |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | For PROP-CAL-038: is the integration test intended to verify only `AccelerometerVarianceTracker → DriftDetector.onFrame()` wiring (no `CompassViewModel` involved, no Android runtime needed, plain JVM test), or does it also cover `CompassViewModel → driftBannerState`? If the former, Robolectric is not needed and the pass condition should be updated to remove the `@RunWith` requirement. If the latter, the TSPEC §9.3 test body must be rewritten to include `CompassViewModel`. |
| Q-02 | PROP-NAV-030 does not acknowledge the SQLCipher `SupportFactory` vs. `FrameworkSQLiteOpenHelperFactory` asymmetry in the migration test. The existing `LuopanDatabaseMigrationTest.kt` uses `FrameworkSQLiteOpenHelperFactory` (a plain SQLite test helper) alongside an SQLCipher-encrypted production database. This is confirmed in the existing test file. Should PROP-NAV-030's pass condition explicitly note that `MigrationTestHelper` must use `FrameworkSQLiteOpenHelperFactory` (not the production SQLCipher factory) and that this means the migration is validated against a plain SQLite copy — not the encrypted production DB? This was raised as High F-03 in iteration 1 but was not carried forward in v0.2 or v0.3 despite remaining unaddressed. |
| Q-03 | `BearingHistoryViewModel` in TSPEC §6.3 accepts `BearingDao` directly (bypassing `BearingRepository`). PLAN task E-1 also creates `FakeBearingDao` in `src/test/java/com/luopan/compass/ui/` — not `src/test/java/com/luopan/compass/bearing/` (where `BearingDaoTest.kt` would live). Is the intent to have two separate fakes: a `FakeBearingDao` in the UI test package for ViewModel tests, and the real Room DAO (tested via Robolectric in `BearingDaoTest.kt`) for DAO-layer contract tests? The two-location pattern is valid but should be explicit to avoid implementers creating a single shared fake that couples the ViewModel tests to the DAO test setup. |

---

## Positive Observations

- **F-01 (IDriftDetector) is 80% resolved.** The PROPERTIES document correctly identifies the gap, adds prerequisite notes to PROP-CAL-036 and PROP-CAL-037, and the PLAN B-3 task is explicit. The remaining work (updating TSPEC §9.3b and §6.1.5) is owned by SE-author, not TE-author, and the PROPERTIES correctly documents the dependency.

- **F-02 (FakeBearingDao) is fully and correctly resolved.** The codebase confirms `BearingDao` is an interface (not an abstract class), making the hand-written-implements approach correct and not requiring any architectural change. The section note in §1.4 is clear and actionable.

- **F-03 (PROP-CAL-040 reclassification) is correctly and completely resolved.** The new pass condition using a real `SharedPreferences`-backed `SettingsRepository` with a second instance over the same file is the correct mechanism for simulating process restart without an actual process kill. The `@After` cleanup requirement is present.

- **PROP-CAL-016 (COUNTING-past-60s re-evaluate rule) is correctly specified.** The two-step pass condition (first `onFrame` with 10% deviation stays COUNTING, then `onFrame` with 11% fires TRIGGERED) correctly tests the boundary without the single-call structural bug from AT-CAL-03-B2.

- **The §1.4 FakeBearingDao section note is well-placed** — putting it at the section level (rather than repeating it in each of PROP-HIST-031/032/033) reduces noise while ensuring the constraint is visible before implementers write the tests.

- **PROP-HIST-064 reclassification to E2E is correct.** The `updateListState()` function in TSPEC §7.1 directly references `searchBar`, `recyclerView`, `emptyNoRecords`, and `emptyNoResults` as Fragment View fields — it cannot be called from a JVM test. The four-branch Espresso-based pass condition is implementable.

- **The Coverage Matrix (§5.1 and §5.2) correctly includes PROP-CAL-040 in the AT-CAL-02-D row** with the updated test file assignment (`RecalibrationBannerTest.kt`). The matrix is internally consistent.

- **PROP-SENSOR-037 resolution is complete and correct.** The `SensorInfo` wrapper data class approach is confirmed as the right fix — `android.hardware.Sensor` is a final class with no public constructor, and the TSPEC §5.5 `buildJsonFromSensors()` function now takes `List<SensorInfo>` in the PROPERTIES pass condition (even though TSPEC §5.5 code still shows `List<Sensor>` — this is a TSPEC artifact, not a PROPERTIES defect).

---

## Recommendation

**Approved with Minor Issues**

The three High findings from iteration 2 are resolved at the PROPERTIES level. No new High findings are introduced. The two Medium findings (F-01, F-02) are implementation-risk items that must be resolved in the TSPEC before PLAN phases B-3/B-4 execute, and in the instrumented test setup before Phase F executes — but they do not block the PROPERTIES document from being used as a test specification.

**Required before PLAN phase B executes:**
- F-01: TSPEC §9.3b must change `FakeDriftDetector : DriftDetector(FakeClock(0L))` to `FakeDriftDetector : IDriftDetector`. TSPEC §6.1.5 Factory must change `driftDetector: DriftDetector?` to `driftDetector: IDriftDetector?`. This is a TSPEC update, not a PROPERTIES update.

**Required before PLAN phase F-9 executes:**
- F-02 (new, Medium): `PROP-HIST-040/043/044` pass conditions must specify `LuopanDatabase.buildInMemory()` as the DAO source in instrumented swipe tests to avoid main-thread database access failures.

**Required before PLAN phase D executes:**
- Q-01: The Robolectric classification of `PROP-CAL-038` vs. the plain JVM `DriftDetectorIntegrationTest.kt` in TSPEC §9.3 must be resolved. If the integration test does not involve `CompassViewModel`, Robolectric is unnecessary and the pass condition should be corrected.
