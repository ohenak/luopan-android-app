# Cross-Review: software-engineer — PROPERTIES

**Reviewer:** software-engineer
**Document reviewed:** docs/luopan-compass/p4-bearing-history/PROPERTIES-luopan-p4-bearing-history.md (v0.3-draft)
**Date:** 2026-04-27
**Iteration:** 3

---

## v2 Finding Resolution Assessment

The three High findings from SE PROPERTIES-v2 are reviewed first, followed by the five Medium findings.

### High Findings (v2 F-01, F-02, F-03)

| v2 ID | Severity | v2 Finding | v0.3 Status | Verdict |
|-------|----------|-----------|-------------|---------|
| F-01 | High | `IDriftDetector` interface absent from TSPEC and codebase; PROP-CAL-036/037 unimplementable | PROP-CAL-036/037 notes that `IDriftDetector` must be created in PLAN task B-3 before B-4 wires it into `CompassViewModel`. Pass conditions updated to reflect post-B-3/post-B-4 prerequisite sequencing. | **Closed** — PLAN B-3 clearly mandates interface creation; PROP-CAL-036 covers the B-2a contract test; PROP-CAL-037 correctly defers to B-4. Resolution is structurally sound. |
| F-02 | High | `BearingHistoryViewModel` takes `BearingDao` directly; `FakeBearingDao` implementability unclear given Room abstract-class concern | §1.4 note added clarifying that Room `@Dao`-annotated types are interfaces (not abstract classes), so `FakeBearingDao` implements `BearingDao` directly. `Dispatchers.setMain(testDispatcher)` requirement reinforced in PROP-HIST-031/032/033 pass conditions. | **Closed** — `BearingDao` confirmed as `interface` in production source (`app/src/main/java/com/luopan/compass/bearing/BearingDao.kt`, line 9: `interface BearingDao`). Hand-written `FakeBearingDao` implementing it is the correct and implementable approach. TSPEC §9.2 shows the full `FakeBearingDao` implementation, which confirms the interface approach compiles. |
| F-03 | High | PROP-CAL-040 JVM unit test with `FakeSettingsRepository` cannot simulate process death | Reclassified from Unit / `CompassViewModelDriftTest.kt` to Integration (instrumented) / `RecalibrationBannerTest.kt`. Pass condition updated to use real `SettingsRepository` backed by real SharedPreferences, with a second instance constructed to simulate re-reading from disk. | **Closed** — Reclassification is correct; the instrumented approach with two `SettingsRepository` instances over the same SharedPreferences file is the practical and testable approximation. |

All three High findings are resolved. No new High findings identified.

### Medium Findings (v2 F-04 through F-08)

| v2 ID | Severity | v2 Finding | v0.3 Claim | Verdict |
|-------|----------|-----------|-----------|---------|
| F-04 | Medium | PROP-HIST-022 pass condition contained both runtime assertion and source-code inspection clause | "Already resolved in v0.2 — confirmed" | **Closed** — PROP-HIST-022 in v0.3 uses `RecordingBearingAdapter` approach exclusively; no source-inspection clause present. Confirmed. |
| F-05 | Medium | PROP-HIST-071 macrobenchmark module existence unconfirmed | Module confirmed at `benchmark/build.gradle.kts`; PROP-HIST-071 updated to reference `:benchmark` source set | **Closed** — Pass condition is now precise and actionable. |
| F-06 | Medium | PROP-HIST-064 `updateListState()` is a private Fragment method unreachable from JVM ViewModel test | Reclassified to E2E / `BearingHistoryFragmentTest.kt` | **Closed** — Reclassification is correct; four-branch instrumented test approach is well-specified. |
| F-07 | Medium | `buildJsonFromSensors()` signature in TSPEC §5.5 takes `List<Sensor>` (final Android class, no public constructor); incompatible with JVM unit test | "Already resolved in v0.2 — confirmed" | **Open — residual defect. See F-01 below.** |
| F-08 | Medium | PROP-HIST-042 reflection-based Snackbar duration verification fragile and blocked on Android 14+ | "Already resolved in v0.2 — confirmed" | **Closed** — PROP-HIST-042 in v0.3 uses behavioral timing assertion (`SystemClock.sleep(5100)` + visibility check); reflection fully removed. Confirmed. |

---

## Findings

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| F-01 | Medium | **TSPEC §5.5 still declares `buildJsonFromSensors(sensors: List<Sensor>, ...)` — directly contradicts PROP-SENSOR-037's `List<SensorInfo>` mandate, and the inconsistency was not corrected before this review.** TSPEC §5.5 (line 607) shows: `internal fun buildJsonFromSensors(sensors: List<Sensor>, ...)`. The same paragraph's Kdoc comment states: "Tests inject a list of `FakeSensorDescriptor` objects" — a type that is defined nowhere in the TSPEC or the codebase. PROP-SENSOR-037 correctly mandates that the parameter type must be `List<SensorInfo>` (not `List<android.hardware.Sensor>`) and defines `SensorInfo` as a data class wrapper. The v0.3 §6.1 resolution entry reads: "Already resolved in v0.2 (`SensorInfo` wrapper introduced; function signature changed to `List<SensorInfo>`)." This claim is false: the TSPEC §5.5 function signature remains `List<Sensor>` and the TSPEC still references the undefined `FakeSensorDescriptor` name. An implementer following TSPEC §5.5 will implement `buildJsonFromSensors(sensors: List<Sensor>, ...)`. When Phase C (task C-1) tests call `buildJsonFromSensors(listOf(SensorInfo(...)))`, they will fail to compile because the parameter type does not match. PROP-SENSOR-037's pass condition states: "callable in a JVM unit test without Robolectric or instrumentation" — this is structurally impossible with `List<Sensor>` because `android.hardware.Sensor` is a `final` class with no public constructor. **Required action before Phase C implementation:** Update TSPEC §5.5 to (1) define `data class SensorInfo(val type: Int, val name: String, val vendor: String, val resolution: Float, val maximumRange: Float, val reportingMode: Int)` in the diagnostics package, (2) change `buildJsonFromSensors()` parameter to `List<SensorInfo>`, (3) show the `sensors.map { SensorInfo(it.type, it.name, it.vendor, it.resolution, it.maximumRange, it.reportingMode) }` mapping in `buildJson()`. This TSPEC update must be committed before Phase C task C-1 is written, or the failing test will not compile against the implementation. | PROP-SENSOR-037; TSPEC §5.5, lines 596–643 |
| F-02 | Low | **PROP-HIST-072 retains a source-inspection assertion in its pass condition: "no `notifyDataSetChanged()` call in adapter source."** This is the same pattern that was correctly removed from PROP-HIST-022 per v1 F-06 resolution. A runtime unit test in `BearingAdapterFormatTest.kt` cannot assert the absence of a method call in source code — the test runs compiled bytecode. The meaningful verifiable assertion is the first clause: "Adapter class extends `ListAdapter`" (checkable via `adapter.javaClass.superclass`). The source-inspection clause should be removed and replaced with a structural runtime assertion: instantiate `BearingAdapter`, call `toggleExpanded()` / `submitList()` on a `RecordingBearingAdapter`, and assert `notifyDataSetChangedCount == 0`. | PROP-HIST-072 |
| F-03 | Low | **PROP-CAL-019b pass condition does not specify how `interferenceState = WARNING` is achieved in the instrumented test context.** The property reads: "Using `FakeDriftDetector` injected into `CompassViewModel`: set `interferenceState = WARNING`, advance clock past 60 s, call `onFrame()` with >10% deviation." In `RecalibrationBannerTest.kt` (instrumented test), `CompassViewModel` is obtained from a live `CompassActivity`. `interferenceState` inside `CompassViewModel` is computed by the `InterferenceDetector` receiving real sensor frames via `SensorLayer` — it cannot be set directly. For the test to produce `interferenceState = WARNING`, the test must either (a) inject a fake `InterferenceDetector` that returns `WARNING` via a test factory, or (b) drive the device's magnetometer sensor into a high-variance state via ShadowSensorManager (Robolectric) or Espresso sensor injection. Neither mechanism is specified in the pass condition. Without this specification, the "instrumented scenario" branch of PROP-CAL-019b is under-specified and the implementer cannot write a deterministic test. | PROP-CAL-019b |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | For PROP-CAL-019b F-03 above: is the intended injection mechanism a custom `ViewModelProvider.Factory` that replaces `InterferenceDetector` with a fake returning `WARNING`, or is the property intended to be tested via the `DriftDetector.onFrame()` precondition check only (i.e., `FakeDriftDetector` configured to never return `TRIGGERED` when the ViewModel passes `interferenceState = WARNING` through the sensor loop)? Clarifying this would resolve the pass condition ambiguity. |
| Q-02 | PROP-CAL-040 pass condition says "call `compassViewModel.dismissDriftBanner()` using a real `SettingsRepository`." In an instrumented test, is `CompassViewModel` obtained by launching `CompassActivity` with a test factory, or is `dismissDriftBanner()` called via a helper that directly writes to `SettingsRepository` (bypassing the ViewModel)? The latter would be simpler and more reliable but changes what is actually being tested (ViewModel wiring vs. SharedPreferences persistence). |
| Q-03 | For PROP-HIST-022, does `BearingAdapterFormatTest.kt` require `@RunWith(RobolectricTestRunner::class)` to instantiate `RecordingBearingAdapter` (which subclasses `BearingAdapter` which extends `ListAdapter`)? `ListAdapter` is an AndroidX class and may require the Android runtime. The PROPERTIES classifies this as Unit — if the project convention permits Robolectric in Unit tests (as in `BearingDaoTest.kt`), no change is needed; if Unit is strictly JVM-only, the level should be Integration. |

---

## Prior Iteration Finding Resolution Summary

| Iteration | Finding | Status |
|-----------|---------|--------|
| SE v1 F-01 (High: espresso-contrib) | Resolved in v0.2 | ✅ Confirmed in v0.3 |
| SE v1 F-02 (High: runBlocking for Flow DAOs) | Resolved in v0.2 | ✅ Confirmed in v0.3 |
| SE v1 F-03 (High: PROP-NAV-030 SQLCipher vs FrameworkSQLiteOpenHelperFactory) | Resolved in v0.2 | ✅ Confirmed in v0.3 |
| SE v1 F-04 (Medium: debounce TestScope injection) | Resolved in v0.2 | ✅ Confirmed in v0.3 |
| SE v1 F-05 (Medium: computeCalibrationAgeDays() extraction) | Resolved in v0.2 | ✅ Confirmed in v0.3 |
| SE v1 F-06 (Medium: spy-based PROP-HIST-022) | Resolved in v0.2 | ✅ Confirmed in v0.3 |
| SE v1 F-07 (Medium: PROP-HIST-042 reflection) | Resolved in v0.2 | ✅ Confirmed in v0.3 |
| SE v1 F-08 (Medium: PROP-CAL-038 AndroidViewModel needs Robolectric) | Resolved in v0.2 | ✅ Confirmed in v0.3 |
| SE v1 F-09 (Medium: PROP-HIST-040 main-thread DAO call without allowMainThreadQueries) | Resolved in v0.2 | ✅ Confirmed in v0.3 |
| SE v1 F-10 (Medium: buildJsonFromSensors List<Sensor>) | Claimed resolved in v0.2 | **❌ TSPEC §5.5 still shows List<Sensor> — see F-01 above** |
| SE v2 F-01 (High: IDriftDetector missing) | Resolved in v0.3 | ✅ PLAN B-3 mandate is correct |
| SE v2 F-02 (High: FakeBearingDao / BearingDao interface) | Resolved in v0.3 | ✅ BearingDao is confirmed interface |
| SE v2 F-03 (High: PROP-CAL-040 process death) | Resolved in v0.3 | ✅ Reclassified to Integration instrumented |
| SE v2 F-04 (Medium: source-inspection clause in PROP-HIST-022) | Confirmed resolved in v0.2 | ✅ Confirmed |
| SE v2 F-05 (Medium: benchmark module unconfirmed) | Resolved in v0.3 | ✅ Module confirmed |
| SE v2 F-06 (Medium: PROP-HIST-064 unreachable from JVM test) | Resolved in v0.3 | ✅ Reclassified to E2E |
| SE v2 F-07 (Medium: TSPEC §5.5 List<Sensor> vs SensorInfo) | Claimed confirmed resolved in v0.2 | **❌ TSPEC §5.5 not updated — see F-01 above** |
| SE v2 F-08 (Medium: PROP-HIST-042 reflection already removed) | Confirmed resolved in v0.2 | ✅ Confirmed |

---

## Positive Observations

- **All three v2 High findings are cleanly resolved.** F-01 (IDriftDetector) is correctly handled via PLAN prerequisite notes without requiring the PROPERTIES to invent architecture; F-02 (FakeBearingDao) is confirmed by reading the actual `BearingDao` source; F-03 (PROP-CAL-040) reclassification to Integration instrumented is the technically correct solution.

- **PROP-HIST-022 pass condition is significantly improved.** The `RecordingBearingAdapter` approach is a well-established pattern in this codebase (consistent with `FakeBearingCaptureUseCase`, `FakeBearingDao`) and eliminates the spy/reflection dependency. The exact field names (`notifyDataSetChangedCount`, `notifyItemChangedPositions`) are specified, leaving no ambiguity for the test implementer.

- **PROP-HIST-031/032/033 pass conditions are precise and implementable.** The `Dispatchers.setMain(testDispatcher)` / constructor-injected `TestScope` clarification, combined with the exact virtual-time advancement sequences (t=0, t=250, t=300, t=550 ms), provides a deterministic test protocol for a notoriously hard-to-test operator (`debounce`).

- **PROP-CAL-040 reclassification is architecturally sound.** The two-`SettingsRepository`-instance approach correctly isolates the SharedPreferences write path and is a well-understood Android testing pattern. The `@After` cleanup requirement prevents cross-test contamination.

- **PROP-CAL-036/037 prerequisite notes correctly manage the B-3/B-4 dependency.** By annotating the properties with explicit PLAN task prerequisites rather than requiring the PROPERTIES to change the architecture, the document remains authoritative about the desired contract without overreaching into PLAN territory.

- **The §6.1 resolution table is well-maintained.** Tracking all SE v1 and v2 findings in §6.1 with explicit resolution descriptions creates a clear audit trail. The "already resolved in v0.2 — confirmed" entries for F-04, F-07, F-08 are appropriate for findings resolved in a prior iteration, with the exception of F-07 (TSPEC §5.5 was not updated).

---

## Recommendation

**Need Attention**

> **F-01 (Medium):** TSPEC §5.5 must be updated to define `SensorInfo` data class and change `buildJsonFromSensors()` parameter type from `List<Sensor>` to `List<SensorInfo>`. This update must occur before Phase C task C-1 is written, or the failing unit test will not compile against the TSPEC-driven implementation. The PROPERTIES document correctly mandates `List<SensorInfo>` in PROP-SENSOR-037 — the required action is a TSPEC correction, not a PROPERTIES change.
>
> **F-02 and F-03 (Low):** PROP-HIST-072 source-inspection clause should be removed and PROP-CAL-019b instrumented test injection mechanism should be specified. These can be addressed in the same revision as F-01 or deferred to a minor erratum commit before Phase C/B-5 implementation.
>
> Once the TSPEC §5.5 correction is committed, all High and Medium findings will be resolved and the PROPERTIES document can proceed to the implementation gate.
