# PLAN-luopan-p4-bearing-history
## Phase 4: Bearing History, Recalibration Refinements, and Sensor Diagnostics

| Field | Value |
|-------|-------|
| **Version** | 0.3-draft |
| **Date** | 2026-04-27 |
| **Status** | In Progress |
| **Phase** | 4 of 5 |
| **Source REQ** | [REQ-luopan-p4-bearing-history v0.4-draft](REQ-luopan-p4-bearing-history.md) |
| **Source FSPEC** | [FSPEC-luopan-p4-bearing-history v0.3-draft](FSPEC-luopan-p4-bearing-history.md) |
| **Source TSPEC** | [TSPEC-luopan-p4-bearing-history v0.3-draft](TSPEC-luopan-p4-bearing-history.md) |
| **Cross-reviews addressed** | TE TSPEC-v1 (F-01 DriftDetector interface; F-02 FakeDriftDetector for AT-VM-DRIFT-01); PM TSPEC-v1 (F-01 COUNTING-past-60s rule backport to FSPEC-CAL-03); PM PLAN-v1 (F-01 A-0 dependency arc; F-04 AT-CAL-01-C); TE PLAN-v1 (F-01 B-3 red task; F-02 dismissBanner unit tests; F-03 AT-CAL-02-D/E; F-04 updateListState unit test; F-06 AT-CAL-01-C; F-07 perf test isolation; F-08 DoD manual sign-off; F-09 onCalibrationCompleteFromHistory unit test) |

---

## Summary

Phase 4 delivers four independent technical domains, each building on the existing Phase 3 codebase:

1. **Database Migration** — Room v2→v3: adds `expected_field_ut` to `calibration_records`; surfaces `sphereRadius_uT` through `CalibrationResult` and `CalibrationRepository`.
2. **Drift Detection Domain** — `DriftDetector` (with extracted interface `IDriftDetector` for testability), `AccelerometerVarianceTracker`, and `SettingsRepository` additions.
3. **Sensor Capability Logging** — `SensorCapabilityLogger` with version-gated first-launch write and injectable `SensorFileWriter`.
4. **Bearing History UI** — `BearingHistoryFragment` (third tab), `BearingHistoryViewModel`, `BearingAdapter`, search, swipe-to-delete, interference badge, and recalibration banners.

The domains are partially independent. The dependency chain is:

```
Phase A: DB Migration + Data Layer foundations
    └── Phase B: Drift Detection domain (depends on SettingsRepository additions from A)
    └── Phase C: Sensor Capability Logging (depends on SettingsRepository additions from A)
Phase D: CompassViewModel wiring (depends on B — DriftDetector)
Phase E: Bearing History UI (depends on D — CompassViewModel; depends on A — DAO changes)
Phase F: Navigation architecture (depends on E)
Phase G: Integration + macrobenchmark (depends on all)
```

**Phases B and C can run in parallel** once Phase A is complete.
**Phase D can start as soon as Phase B is done.**
**Phase E can start as soon as Phase A (DAO) and Phase D (ViewModel) are done.**

---

## Status Key

| Symbol | Meaning |
|--------|---------|
| ⬚ | Not Started |
| 🔴 | Red (failing test written) |
| 🟢 | Green (implementation passing) |
| 🔵 | Refactored |
| ✅ | Done |

---

## Cross-Review Issues Addressed in This Plan

The following issues raised in TSPEC cross-reviews are addressed as explicit tasks below:

| Source | Finding | Resolution in Plan |
|--------|---------|-------------------|
| TE TSPEC-v1 F-01 | `DriftDetector` needs to be injectable as a fake (for AT-VM-DRIFT-01) | Phase B includes extraction of `IDriftDetector` interface; `FakeDriftDetector` is a test double created in the same phase. |
| TE TSPEC-v1 F-02 | `AT-VM-DRIFT-01` (CompassViewModel TRIGGERED→cooldown→VISIBLE wiring) requires `FakeDriftDetector` | Phase B task B-4 creates `FakeDriftDetector`; Phase D task D-4 writes and wires `AT-VM-DRIFT-01` and `AT-VM-DRIFT-01b`. |
| TE TSPEC-v1 F-07 | `SensorCapabilityLogger.buildJson()` must be a pure function to enable JVM unit tests | Phase C task C-1 specifies `buildJsonFromSensors()` extraction as the primary design point; all unit tests (AT-SENSOR-01-E through G) call `buildJsonFromSensors()` directly. |
| PM TSPEC-v1 F-01 | "COUNTING past 60 s when deviation ≤ 10%" rule must be backported to FSPEC-CAL-03 | Phase A-0 includes an explicit task to update FSPEC-CAL-03 before implementation begins. |
| PM PLAN-v1 F-01 | Phase A-0 gate not enforced structurally in the dependency graph — Phase A could appear parallelizable with A-0 | Dependency graph updated: Phase A entry now explicitly lists A-0 as a required predecessor. |
| PM PLAN-v1 F-04 | AT-CAL-01-C (floor-division unit test for `computeCalibrationAgeDays()`) not named in any PLAN task | New task D-1a added to Phase D: write failing unit test AT-CAL-01-C before implementing calibration age logic. |
| TE PLAN-v1 F-01 | B-3 (`IDriftDetector` creation) has no preceding red-phase task — pure interface/enum with no failing test first | New task B-2a added before B-3: write failing structural/contract test for `IDriftDetector` interface and `DriftEvent` enum. |
| TE PLAN-v1 F-02 | `dismissCalAgeBanner()` and `dismissDriftBanner()` have no dedicated unit test tasks in Phase D | New task D-1b added to Phase D: write failing unit tests for `dismissCalAgeBanner()` and `dismissDriftBanner()` state transitions. |
| TE PLAN-v1 F-03 | AT-CAL-02-D (cooldown suppression) and AT-CAL-02-E (cooldown re-arm) absent from all Phase D tasks | New task D-1c added to Phase D: write failing unit tests AT-CAL-02-D and AT-CAL-02-E for cooldown suppression and re-arm logic. |
| TE PLAN-v1 F-04 | No failing-test task for `updateListState()` four-branch visibility logic in Phase E | New task E-2a added to Phase E: write failing unit test for `updateListState()` four-branch logic. |
| TE PLAN-v1 F-06 | AT-CAL-01-C not in Test File Inventory (same gap as PM PLAN-v1 F-04) | Resolved by D-1a (same fix covers both findings). |
| TE PLAN-v1 F-07 | 500-record performance test co-located in `BearingHistoryFragmentTest.kt` alongside functional tests | Phase G task G-1 updated to use dedicated `BearingHistoryPerfTest.kt`; Test File Inventory updated. |
| TE PLAN-v1 F-08 | Manual test AT-HIST-03-D has no DoD gate requiring a recorded sign-off artifact | DoD checklist entry updated to require explicit sign-off record. |
| TE PLAN-v1 F-09 | No PLAN task covers `onCalibrationCompleteFromHistory()` re-flash mitigation unit test | New task D-1d added to Phase D: write failing unit test for the async suppress-then-clear race path. |

---

## Phase A-0: FSPEC Backport (Pre-implementation)

**Dependency:** None. Must complete before implementation starts.
**Parallelizable with:** Nothing — this is a prerequisite gate.

| # | Task | Test File | Source File | Status |
|---|------|-----------|-------------|--------|
| A-0-1 | Add "COUNTING past 60 s when deviation ≤ 10%" as an explicit approved edge-case rule in FSPEC-CAL-03. This backports the TSPEC §5.3 "re-evaluate each frame" decision into the FSPEC so it is not a silent TSPEC invention. Update the DriftDetector state diagram and Timer Behavior section of FSPEC-CAL-03. (PM TSPEC-v1 F-01) | — | `docs/luopan-compass/p4-bearing-history/FSPEC-luopan-p4-bearing-history.md` | ✅ |
| A-0-2 | Fix FSPEC-HIST-03 Snackbar text from "Deleted" to "Bearing deleted" (PM PROPERTIES-v1 F-01). | — | `docs/luopan-compass/p4-bearing-history/FSPEC-luopan-p4-bearing-history.md` | ✅ |
| A-0-3 | Update TSPEC §9.3b FakeDriftDetector from `DriftDetector` subclass to `IDriftDetector` implementor; update TSPEC §6.1.5 Factory parameter type from `DriftDetector?` to `IDriftDetector?`; update §6.1.1 CompassViewModel field type (SE PROPERTIES-v2 F-01). | — | `docs/luopan-compass/p4-bearing-history/TSPEC-luopan-p4-bearing-history.md` | ✅ |

---

## Phase A: Database Migration and Data Layer

**Dependency:** Phase A-0 (FSPEC backport must complete before any implementation begins). Builds on existing codebase.
**Parallelizable with:** Nothing at the start — Phases B and C depend on A completing first.

This phase completes all data layer changes: Room migration, entity changes, DAO additions, and SettingsRepository additions. It lays the groundwork for all other phases.

| # | Task | Test File | Source File | Status |
|---|------|-----------|-------------|--------|
| A-1 | Write failing test for `MIGRATION_2_3`: assert `expected_field_ut` column added; existing rows default to 0.0; `CalibrationRecord.expected_field_ut` round-trips via DAO. | `app/src/androidTest/java/com/luopan/compass/db/LuopanDatabaseMigrationTest.kt` | — | ✅ |
| A-2 | Add `MIGRATION_2_3` to `Migrations.kt`: `ALTER TABLE calibration_records ADD COLUMN expected_field_ut REAL NOT NULL DEFAULT 0.0`. Bump `LuopanDatabase` version to 3 and register migration. | `app/src/androidTest/java/com/luopan/compass/db/LuopanDatabaseMigrationTest.kt` | `app/src/main/java/com/luopan/compass/db/Migrations.kt`, `app/src/main/java/com/luopan/compass/db/LuopanDatabase.kt` | ✅ |
| A-3 | Add `expected_field_ut: Float` field to `CalibrationRecord` with `@ColumnInfo(name = "expected_field_ut", defaultValue = "0.0")`. | `app/src/androidTest/java/com/luopan/compass/db/LuopanDatabaseMigrationTest.kt` | `app/src/main/java/com/luopan/compass/calibration/CalibrationRecord.kt` | ✅ |
| A-4 | Write failing test for `CalibrationResult.sphereRadius_uT`: assert `fitEllipsoid()` populates it; assert `toRecord()` maps it to `expected_field_ut`. | `app/src/test/java/com/luopan/compass/calibration/CalibrationRepositoryTest.kt` | — | ✅ |
| A-5 | Add `sphereRadius_uT: Float` field to `CalibrationResult` data class (in `CalibrationEngine.kt`); populate it in `fitEllipsoid()` from the existing local `r` variable. | `app/src/test/java/com/luopan/compass/calibration/CalibrationRepositoryTest.kt` | `app/src/main/java/com/luopan/compass/calibration/CalibrationEngine.kt` | ✅ |
| A-6 | Update `CalibrationRepository.toRecord()` to map `result.sphereRadius_uT → record.expected_field_ut`. | `app/src/test/java/com/luopan/compass/calibration/CalibrationRepositoryTest.kt` | `app/src/main/java/com/luopan/compass/calibration/CalibrationRepository.kt` | ✅ |
| A-7 | Write failing tests for new `BearingDao` methods: `getAllFlow()` returns records sorted newest-first; `searchFlow(query)` returns case-insensitive substring matches; both are reactive (Flow). | `app/src/test/java/com/luopan/compass/bearing/BearingDaoTest.kt` | — | ✅ |
| A-8 | Add `getAllFlow()` and `searchFlow(query)` to `BearingDao`, `BearingRepository` interface, and `BearingRepositoryImpl`. | `app/src/test/java/com/luopan/compass/bearing/BearingDaoTest.kt` | `app/src/main/java/com/luopan/compass/bearing/BearingDao.kt`, `app/src/main/java/com/luopan/compass/bearing/BearingRepository.kt`, `app/src/main/java/com/luopan/compass/bearing/BearingRepositoryImpl.kt` | ✅ |
| A-9 | Write failing tests for `SettingsRepository` Phase 4 additions: `driftCooldownTimestampMs` reads/writes correctly (default 0L); `sensorProfileWrittenForVersion` reads/writes correctly (default 0). | `app/src/test/java/com/luopan/compass/settings/SettingsRepositoryTest.kt` | — | ✅ |
| A-10 | Add `KEY_DRIFT_COOLDOWN_TIMESTAMP_MS`, `KEY_SENSOR_PROFILE_WRITTEN_FOR_VERSION` constants and corresponding `var` properties to `SettingsRepository`. | `app/src/test/java/com/luopan/compass/settings/SettingsRepositoryTest.kt` | `app/src/main/java/com/luopan/compass/settings/SettingsRepository.kt` | ✅ |

**Files touched in Phase A:**

- `app/src/main/java/com/luopan/compass/db/Migrations.kt` (new: MIGRATION_2_3)
- `app/src/main/java/com/luopan/compass/db/LuopanDatabase.kt` (version bump, addMigrations)
- `app/src/main/java/com/luopan/compass/calibration/CalibrationRecord.kt` (new field)
- `app/src/main/java/com/luopan/compass/calibration/CalibrationEngine.kt` (new field in CalibrationResult, populate in fitEllipsoid)
- `app/src/main/java/com/luopan/compass/calibration/CalibrationRepository.kt` (toRecord mapping)
- `app/src/main/java/com/luopan/compass/bearing/BearingDao.kt` (getAllFlow, searchFlow)
- `app/src/main/java/com/luopan/compass/bearing/BearingRepository.kt` (interface additions)
- `app/src/main/java/com/luopan/compass/bearing/BearingRepositoryImpl.kt` (implementations)
- `app/src/main/java/com/luopan/compass/settings/SettingsRepository.kt` (Phase 4 keys and properties)
- `app/src/androidTest/java/com/luopan/compass/db/LuopanDatabaseMigrationTest.kt` (v2→v3 tests)
- `app/src/test/java/com/luopan/compass/calibration/CalibrationRepositoryTest.kt` (additions)
- `app/src/test/java/com/luopan/compass/bearing/BearingDaoTest.kt` (Flow method tests)
- `app/src/test/java/com/luopan/compass/settings/SettingsRepositoryTest.kt` (Phase 4 property tests)

---

## Phase B: Drift Detection Domain

**Dependency:** Phase A (SettingsRepository additions must exist; `InterferenceState` already exists from Phase 3).
**Parallelizable with:** Phase C (Sensor Capability Logging).

This phase delivers the `IDriftDetector` interface (for testability), `DriftDetector` implementation, `AccelerometerVarianceTracker`, their test doubles (`FakeDriftDetector`, `FakeAccelerometerVarianceTracker`), and all associated unit and integration tests.

**Note on IDriftDetector interface (TE TSPEC-v1 F-01):** `DriftDetector` is extracted behind an `IDriftDetector` interface so that `CompassViewModel` can accept a fake in unit tests (AT-VM-DRIFT-01 in Phase D). Without this interface, `FakeDriftDetector` cannot be injected because `DriftDetector` is a concrete class; subclassing it (as shown in the original TSPEC §9.3b) is fragile and couples the fake to implementation details.

| # | Task | Test File | Source File | Status |
|---|------|-----------|-------------|--------|
| B-1 | Write failing unit tests for `AccelerometerVarianceTracker`: AT-VAR-01 (population variance formula — magnitudes 3,5,4 → variance ≈ 0.6667, NOT sample variance 1.0); rolling window eviction (samples older than 5 s are removed); n<2 guard (returns 0f). | `app/src/test/java/com/luopan/compass/drift/AccelerometerVarianceTrackerTest.kt` | — | ⬚ |
| B-2 | Implement `AccelerometerVarianceTracker`: time-based 5,000 ms rolling window over combined 3-axis accelerometer magnitude; population variance formula (`/ n`); `Clock`-injected; `reset()` method. | `app/src/test/java/com/luopan/compass/drift/AccelerometerVarianceTrackerTest.kt` | `app/src/main/java/com/luopan/compass/drift/AccelerometerVarianceTracker.kt` | ⬚ |
| B-2a | Write failing structural/contract test for `IDriftDetector` interface and `DriftEvent` enum: assert `DriftEvent.TRIGGERED` and `DriftEvent.RESET` enum constants exist; assert `FakeDriftDetector` (once created in B-4) compiles as an `IDriftDetector`; assert `onFrame()` signature matches the contract. This red task drives and documents the interface contract before the interface is created. (TE PLAN-v1 F-01) | `app/src/test/java/com/luopan/compass/drift/IDriftDetectorContractTest.kt` | — | ⬚ |
| B-3 | Create `IDriftDetector` interface exposing `onFrame(accVariance, measuredMagnitudeUt, interferenceState, expectedFieldUt): DriftEvent?` and `reset()`. Also create `DriftEvent` enum (`TRIGGERED`, `RESET`). | `app/src/test/java/com/luopan/compass/drift/IDriftDetectorContractTest.kt` | `app/src/main/java/com/luopan/compass/drift/IDriftDetector.kt`, `app/src/main/java/com/luopan/compass/drift/DriftEvent.kt` | ⬚ |
| B-4 | Create `FakeDriftDetector` test double: implements `IDriftDetector`; constructor-configured `nextEvent: DriftEvent?`; records `onFrameCallCount`; `reset()` records call. Create `FakeAccelerometerVarianceTracker` test double: returns configured `variance: Float`. | `app/src/test/java/com/luopan/compass/drift/DriftDetectorTest.kt` | `app/src/test/java/com/luopan/compass/drift/FakeDriftDetector.kt`, `app/src/test/java/com/luopan/compass/drift/FakeAccelerometerVarianceTracker.kt` | ⬚ |
| B-5 | Write failing unit tests for `DriftDetector` (AT-CAL-03-A through AT-CAL-03-F, AT-CAL-03-B2): timer reset on precondition violation; TRIGGERED after 60 s with >10% deviation; no TRIGGERED at ≤10% (including exactly 10%); expectedFieldUt=0.0 disables; no hysteresis; post-trigger requires new 60 s window. Also include "COUNTING past 60 s stays COUNTING when deviation ≤10%" test (FSPEC-CAL-03 backported rule). | `app/src/test/java/com/luopan/compass/drift/DriftDetectorTest.kt` | — | ⬚ |
| B-6 | Implement `DriftDetector` implementing `IDriftDetector`: IDLE/COUNTING state machine; `Clock`-injected; `countingStartMs: Long?`; `onFrame()` per TSPEC §5.3 (including re-evaluate-each-frame rule after 60 s when deviation ≤ 10%); `reset()`; `elapsedCountingMs()` internal accessor. | `app/src/test/java/com/luopan/compass/drift/DriftDetectorTest.kt` | `app/src/main/java/com/luopan/compass/drift/DriftDetector.kt` | ⬚ |
| B-7 | Write failing integration test AT-CAL-INT-01: two-phase structure — Phase 1 drives 58 s of frames and asserts `triggeredEarly == false`; Phase 2 advances past 60 s and asserts `triggeredAfterThreshold == true`. Uses real `AccelerometerVarianceTracker` and real `DriftDetector`. | `app/src/test/java/com/luopan/compass/drift/DriftDetectorIntegrationTest.kt` | — | ⬚ |
| B-8 | Verify integration test passes with real implementations wired together (AccelerometerVarianceTracker → DriftDetector.onFrame() → DriftEvent.TRIGGERED). | `app/src/test/java/com/luopan/compass/drift/DriftDetectorIntegrationTest.kt` | — | ⬚ |

**Files touched in Phase B:**

- `app/src/main/java/com/luopan/compass/drift/IDriftDetector.kt` (new interface)
- `app/src/main/java/com/luopan/compass/drift/DriftEvent.kt` (new enum)
- `app/src/main/java/com/luopan/compass/drift/DriftDetector.kt` (new class)
- `app/src/main/java/com/luopan/compass/drift/AccelerometerVarianceTracker.kt` (new class)
- `app/src/main/java/com/luopan/compass/ui/DriftBannerState.kt` (new enum — needed by phase D but defined early)
- `app/src/test/java/com/luopan/compass/drift/IDriftDetectorContractTest.kt` (new — B-2a contract test)
- `app/src/test/java/com/luopan/compass/drift/DriftDetectorTest.kt` (new)
- `app/src/test/java/com/luopan/compass/drift/AccelerometerVarianceTrackerTest.kt` (new)
- `app/src/test/java/com/luopan/compass/drift/DriftDetectorIntegrationTest.kt` (new JVM integration)
- `app/src/test/java/com/luopan/compass/drift/FakeDriftDetector.kt` (new test double)
- `app/src/test/java/com/luopan/compass/drift/FakeAccelerometerVarianceTracker.kt` (new test double)

---

## Phase C: Sensor Capability Logging

**Dependency:** Phase A (SettingsRepository `sensorProfileWrittenForVersion` additions).
**Parallelizable with:** Phase B.

This phase delivers `SensorFileWriter`, `SensorCapabilityLogger` (with `buildJsonFromSensors()` as a pure function per TE TSPEC-v1 F-07), and all unit and instrumented tests. The pure function extraction is the key design point: `buildJson()` is a one-liner that calls `context.getSystemService()` and delegates to `buildJsonFromSensors(sensors, versionCode, writtenAtIso8601)`, which is a context-free JVM-testable function.

| # | Task | Test File | Source File | Status |
|---|------|-----------|-------------|--------|
| C-1 | Write failing unit tests for `SensorCapabilityLogger`: AT-SENSOR-01-E (IOException caught, version key not updated); AT-SENSOR-01-F (SecurityException caught, no crash); AT-SENSOR-01-G (buildJsonFromSensors() with empty list produces correct root JSON structure); reporting mode mapping tests (CONTINUOUS→"CONTINUOUS"; UNKNOWN 99→"UNKNOWN(99)"). All call `buildJsonFromSensors()` directly — no Android runtime needed. | `app/src/test/java/com/luopan/compass/diagnostics/SensorCapabilityLoggerTest.kt` | — | ✅ |
| C-2 | Create `SensorFileWriter` interface (`fun write(file: File, content: String)`) and `RealSensorFileWriter` production implementation. | `app/src/test/java/com/luopan/compass/diagnostics/SensorCapabilityLoggerTest.kt` | `app/src/main/java/com/luopan/compass/diagnostics/SensorFileWriter.kt` | ✅ |
| C-3 | Implement `SensorCapabilityLogger`: version gate (`VERSION_CODE > storedVersion`); `buildJsonFromSensors(sensors, versionCode, writtenAtIso8601)` as `internal` pure function; `buildJson()` resolves sensor list from `SensorManager` and delegates; `maybeWrite()` with `catch (e: Exception)` (covers both IOException and SecurityException per PM TSPEC-v1 F-02); 2-space-indented JSON; reporting mode mapping including `"UNKNOWN($mode)"` fallback. | `app/src/test/java/com/luopan/compass/diagnostics/SensorCapabilityLoggerTest.kt` | `app/src/main/java/com/luopan/compass/diagnostics/SensorCapabilityLogger.kt` | ✅ |
| C-4 | Write failing instrumented tests for sensor logging: AT-SENSOR-01-A (file written on first launch, key=0); AT-SENSOR-01-B (file not rewritten on same-version relaunch, writeCallCount==1 in single test method); AT-SENSOR-01-C (rewritten on upgrade, key 39→40); AT-SENSOR-01-D (rewritten after data clear). | `app/src/androidTest/java/com/luopan/compass/diagnostics/SensorCapabilityLoggerInstrumentedTest.kt` | — | ✅ |
| C-5 | Verify all instrumented sensor logging tests pass (requires on-device/emulator run). | `app/src/androidTest/java/com/luopan/compass/diagnostics/SensorCapabilityLoggerInstrumentedTest.kt` | `app/src/main/java/com/luopan/compass/diagnostics/SensorCapabilityLogger.kt` | ✅ |

**Files touched in Phase C:**

- `app/src/main/java/com/luopan/compass/diagnostics/SensorFileWriter.kt` (new interface + RealSensorFileWriter)
- `app/src/main/java/com/luopan/compass/diagnostics/SensorCapabilityLogger.kt` (new class)
- `app/src/test/java/com/luopan/compass/diagnostics/SensorCapabilityLoggerTest.kt` (new)
- `app/src/androidTest/java/com/luopan/compass/diagnostics/SensorCapabilityLoggerInstrumentedTest.kt` (new)

---

## Phase D: CompassViewModel Wiring

**Dependency:** Phase B (IDriftDetector, DriftDetector, FakeDriftDetector, AccelerometerVarianceTracker, DriftBannerState must exist).
**Parallelizable with:** Phase C (after B is done).

This phase wires drift detection into `CompassViewModel`: new fields, modified `loadCalibrationAge()` visibility, new public methods (`dismissCalAgeBanner`, `onCalibrationCompleteFromHistory`, `resetDriftDetector`, `dismissDriftBanner`), drift detection in the sensor loop, and the critical AT-VM-DRIFT-01 unit tests.

**Note (TE TSPEC-v1 F-02):** `CompassViewModel` accepts `IDriftDetector` (not `DriftDetector`) so that `FakeDriftDetector` can be injected in unit tests. `simulateSensorFrame()` is added as an `@VisibleForTesting` method to drive the internal drift detection block without requiring a real `SensorManager`.

| # | Task | Test File | Source File | Status |
|---|------|-----------|-------------|--------|
| D-1 | Write failing unit tests AT-VM-DRIFT-01 and AT-VM-DRIFT-01b: inject `FakeDriftDetector` and `FakeAccelerometerVarianceTracker` into `CompassViewModel`; assert `driftBannerState` emits `VISIBLE` when TRIGGERED with no cooldown; assert `driftBannerState` stays `HIDDEN` when TRIGGERED with active cooldown. | `app/src/test/java/com/luopan/compass/ui/CompassViewModelDriftTest.kt` | — | ✅ |
| D-1a | Write failing unit test AT-CAL-01-C for `computeCalibrationAgeDays()` floor division: inject `FakeClock` with timestamp representing 31d 23h elapsed; assert return value equals 31 (not 32). This ensures floor-division semantics before the function is implemented. (PM PLAN-v1 F-04; TE PLAN-v1 F-06) | `app/src/test/java/com/luopan/compass/ui/CompassViewModelDriftTest.kt` | — | ✅ |
| D-1b | Write failing unit tests for `dismissCalAgeBanner()` and `dismissDriftBanner()` ViewModel state transitions: assert `dismissCalAgeBanner()` sets `calAgeBannerDismissed = true` and `calAgeBanner` StateFlow emits `GONE`; assert `dismissDriftBanner()` writes `driftCooldownTimestampMs` to `SettingsRepository` and `driftBannerState` emits `HIDDEN`. (TE PLAN-v1 F-02) | `app/src/test/java/com/luopan/compass/ui/CompassViewModelDriftTest.kt` | — | ✅ |
| D-1c | Write failing unit tests AT-CAL-02-D and AT-CAL-02-E for drift banner cooldown logic: AT-CAL-02-D asserts that when `driftCooldownTimestampMs` is set and has not expired (< 10 min elapsed), a new TRIGGERED event does not emit `VISIBLE`; AT-CAL-02-E asserts that after the cooldown expires, the next TRIGGERED event does emit `VISIBLE`. (TE PLAN-v1 F-03) | `app/src/test/java/com/luopan/compass/ui/CompassViewModelDriftTest.kt` | — | ✅ |
| D-1d | Write failing unit test for `onCalibrationCompleteFromHistory()` async re-flash mitigation: assert that `calAgeBannerDismissed` is set to `true` synchronously before the IO coroutine resolves; assert that if the refreshed calibration age is ≤ 30, the flag is cleared to `false` after IO completes; use `TestCoroutineScheduler` to control coroutine advancement. (TE PLAN-v1 F-09) | `app/src/test/java/com/luopan/compass/ui/CompassViewModelDriftTest.kt` | — | ✅ |
| D-2 | Update `CompassViewModel` constructor to accept `IDriftDetector` and `AccelerometerVarianceTracker` parameters (with production defaults); update `Factory` accordingly. Add `DriftBannerState` StateFlow, `calAgeBannerDismissed` field, `expectedFieldUt` cache field. Add `@VisibleForTesting simulateSensorFrame()` method. | `app/src/test/java/com/luopan/compass/ui/CompassViewModelDriftTest.kt` | `app/src/main/java/com/luopan/compass/ui/CompassViewModel.kt` | ✅ |
| D-3 | Promote `loadCalibrationAge()` from `private` to `internal`; update it to also populate `expectedFieldUt` from `CalibrationRecord.expected_field_ut`. Add `AccelerometerVarianceTracker.update()` call and `DriftDetector.onFrame()` call in sensor loop; check 10-minute cooldown; update `_driftBannerState` on TRIGGERED. | `app/src/test/java/com/luopan/compass/ui/CompassViewModelDriftTest.kt` | `app/src/main/java/com/luopan/compass/ui/CompassViewModel.kt` | ✅ |
| D-4 | Implement `dismissCalAgeBanner()`, `onCalibrationCompleteFromHistory()` (with re-flash mitigation: set `calAgeBannerDismissed = true` immediately; clear flag if age ≤ 30 after IO resolves), `resetDriftDetector()`, `dismissDriftBanner()` methods in `CompassViewModel`. | `app/src/test/java/com/luopan/compass/ui/CompassViewModelDriftTest.kt` | `app/src/main/java/com/luopan/compass/ui/CompassViewModel.kt` | ✅ |
| D-5 | Verify all `CompassViewModelDriftTest` unit tests pass; verify no existing `CompassViewModelTest` tests regress. | `app/src/test/java/com/luopan/compass/ui/CompassViewModelDriftTest.kt`, `app/src/test/java/com/luopan/compass/ui/CompassViewModelTest.kt` | — | ✅ |

**Files touched in Phase D:**

- `app/src/main/java/com/luopan/compass/ui/CompassViewModel.kt` (fields, methods, sensor loop, Factory)
- `app/src/test/java/com/luopan/compass/ui/CompassViewModelDriftTest.kt` (new)

---

## Phase E: Bearing History UI — ViewModel and Adapter

**Dependency:** Phase A (BearingDao.getAllFlow() and searchFlow()); Phase D (CompassViewModel changes done).
**Parallelizable with:** Nothing — UI phases are sequential E→F.

This phase delivers `BearingHistoryViewModel`, `FakeBearingDao`, all ViewModel unit tests (search debounce, undo state machine), `BearingAdapter` with expand/collapse and formatting, and the string resources and layout files.

| # | Task | Test File | Source File | Status |
|---|------|-----------|-------------|--------|
| E-1 | Create `FakeBearingDao`: recording fake implementing `BearingDao`; tracks `searchFlowCalls` list and `getAllFlowCallCount`; backed by `MutableStateFlow<List<BearingRecord>>`; implements `insert`, `getAll`, `count`, `delete`. | `app/src/test/java/com/luopan/compass/ui/FakeBearingDao.kt` | `app/src/test/java/com/luopan/compass/ui/FakeBearingDao.kt` | ⬚ |
| E-2 | Write failing unit tests for `BearingHistoryViewModel` search: AT-HIST-02-A (substring match, fake DAO); AT-HIST-02-B (debounce — no call at 200 ms, one call at 500 ms); AT-HIST-02-C (timer restart on keystroke — no call at 300 ms from t=0, fires at t=550 ms); AT-HIST-02-D (immediate restore on empty query, no debounce). | `app/src/test/java/com/luopan/compass/ui/BearingHistoryViewModelTest.kt` | — | ⬚ |
| E-2a | Write failing unit test for `updateListState()` four-branch visibility logic: (1) zero records + empty query → State A (search bar GONE, empty-no-bearings VISIBLE, RecyclerView GONE); (2) ≥1 record + empty query → normal list (search bar VISIBLE, empty views GONE, RecyclerView VISIBLE); (3) ≥1 record + active query + matches → search results (RecyclerView VISIBLE, no-results GONE); (4) ≥1 record + active query + no matches → empty-no-results VISIBLE. Extract `updateListState()` as an `internal` pure function on `BearingHistoryViewModel` (or a companion helper) so it can be called in JVM unit tests without a Fragment. (TE PLAN-v1 F-04) | `app/src/test/java/com/luopan/compass/ui/BearingHistoryViewModelTest.kt` | — | ⬚ |
| E-3 | Write failing unit tests for `BearingHistoryViewModel` undo state machine: AT-UNDO-VM-01 (`deleteRecord()` sets `pendingUndo`); AT-UNDO-VM-02 (`undoDelete()` clears and re-inserts); AT-UNDO-VM-03 (`commitDelete()` clears without re-inserting). | `app/src/test/java/com/luopan/compass/ui/BearingHistoryViewModelTest.kt` | — | ⬚ |
| E-4 | Implement `BearingHistoryViewModel`: `_searchQuery: MutableStateFlow<String>`; `bearingList: Flow<List<BearingRecord>>` with `debounce { if (empty) 0L else 300L }` + `flatMapLatest`; `pendingUndo: BearingRecord?`; `deleteRecord()`, `undoDelete()`, `commitDelete()`, `hasPendingUndo()`; `Factory`. | `app/src/test/java/com/luopan/compass/ui/BearingHistoryViewModelTest.kt` | `app/src/main/java/com/luopan/compass/ui/BearingHistoryViewModel.kt` | ⬚ |
| E-5 | Write failing unit test for inclinationDeviation format function (AT-HIST-05-D): -2.3 → "-2°", 4.7 → "4°", 0f → "0°", -0.9f → "0°". Write failing unit test for fieldDeviation format: 0.25 → "25%", 0.0 → "0%", 2.5 → "250%". | `app/src/test/java/com/luopan/compass/ui/BearingAdapterFormatTest.kt` | — | ⬚ |
| E-6 | Implement `BearingAdapter`: `ListAdapter<BearingRecord, ViewHolder>` with `DiffUtil.ItemCallback`; expand/collapse via `expandedId: String?` and `notifyItemChanged()` on only the two affected positions (PM TSPEC-v1 F-05); badge visibility (`View.GONE` for false); `formatFieldDeviation()` and `formatInclinationDev()` as `internal` pure functions; all ViewHolder fields from TSPEC §7.2. | `app/src/test/java/com/luopan/compass/ui/BearingAdapterFormatTest.kt` | `app/src/main/java/com/luopan/compass/ui/BearingAdapter.kt` | ⬚ |
| E-7 | Create layout files: `fragment_bearing_history.xml` (search bar, two banners, RecyclerView, two empty-state variants; all view IDs per TSPEC §7.4); `item_bearing_record.xml` (all ViewHolder view IDs); `banner_recalibration.xml` (shared banner layout with X button and text). | — | `app/src/main/res/layout/fragment_bearing_history.xml`, `app/src/main/res/layout/item_bearing_record.xml`, `app/src/main/res/layout/banner_recalibration.xml` | ⬚ |
| E-8 | Add string resources: `tab_history`, `banner_cal_age`, `banner_drift`, `bearing_deleted` ("Bearing deleted" per PM TSPEC-v1 F-03), `undo`, `empty_no_bearings`, `empty_no_results`, `interference_badge`. | — | `app/src/main/res/values/strings.xml` | ⬚ |

**Files touched in Phase E:**

- `app/src/main/java/com/luopan/compass/ui/BearingHistoryViewModel.kt` (new)
- `app/src/main/java/com/luopan/compass/ui/BearingAdapter.kt` (new)
- `app/src/main/res/layout/fragment_bearing_history.xml` (new)
- `app/src/main/res/layout/item_bearing_record.xml` (new)
- `app/src/main/res/layout/banner_recalibration.xml` (new)
- `app/src/main/res/values/strings.xml` (additions)
- `app/src/test/java/com/luopan/compass/ui/FakeBearingDao.kt` (new)
- `app/src/test/java/com/luopan/compass/ui/BearingHistoryViewModelTest.kt` (new)
- `app/src/test/java/com/luopan/compass/ui/BearingAdapterFormatTest.kt` (new)

---

## Phase F: Navigation Architecture and BearingHistoryFragment

**Dependency:** Phase E (layouts, strings, BearingHistoryViewModel, BearingAdapter, CompassViewModel changes from D).
**Parallelizable with:** Phase C completion (but C feeds nothing in F directly).

This phase wires everything into the app: third tab navigation, `BearingHistoryFragment` with all observers and click handlers, and all instrumented tests.

| # | Task | Test File | Source File | Status |
|---|------|-----------|-------------|--------|
| F-1 | Update `nav_graph.xml`: add `dest_history` fragment destination. Update `activity_compass.xml`: add third `TabItem` (`@string/tab_history`). | — | `app/src/main/res/navigation/nav_graph.xml`, `app/src/main/res/layout/activity_compass.xml` | ⬚ |
| F-2 | Update `CompassActivity`: add `TAB_HISTORY = 2` constant; add `TAB_HISTORY -> navController.navigate(R.id.dest_history)` branch in `wireTabNavigation()`; add `dest_history` case in `addOnDestinationChangedListener`; add `SensorCapabilityLogger` dispatch in `onCreate()` via `lifecycleScope.launch(Dispatchers.IO)`. | — | `app/src/main/java/com/luopan/compass/CompassActivity.kt` | ⬚ |
| F-3 | Implement `BearingHistoryFragment`: `activityViewModels()` for `CompassViewModel`; `viewModels(factory = BearingHistoryViewModel.Factory(dao))` for `BearingHistoryViewModel`; two `ActivityResultLauncher` registrations in `onCreate()` (`calWizardLauncherAge`, `calWizardLauncherDrift`); `loadCalibrationAge()` call in `onResume()`; search bar text watcher → `historyViewModel.setSearchQuery()`; `ItemTouchHelper` swipe-to-delete with `setDuration(5000)` Snackbar (SE FSPEC-v2 F-06) and single-active-undo pattern; `updateListState()` helper per TSPEC §7.1 (all four visibility combinations, search bar restored per PM TSPEC-v1 F-06); age banner observer (`updateAgeBanner()`); drift banner observer (`updateDriftBanner()`); X button click handlers calling `dismissCalAgeBanner()` / `dismissDriftBanner()`; body tap handlers launching CalibrationWizardActivity. | — | `app/src/main/java/com/luopan/compass/ui/BearingHistoryFragment.kt` | ⬚ |
| F-4 | Write failing instrumented tests for navigation: AT-NAV-01-A (third tab shows BearingHistoryFragment); AT-NAV-01-B (shared CompassViewModel instance). | `app/src/androidTest/java/com/luopan/compass/ui/NavigationTabTest.kt` | — | ⬚ |
| F-5 | Write failing instrumented tests for bearing history list: AT-HIST-01-A (10 records, sort order, badge count with per-position scrollToPosition loop per TSPEC §9.4); AT-HIST-01-B (expand single row, panel VISIBLE); AT-HIST-01-C (only one row expanded at a time). | `app/src/androidTest/java/com/luopan/compass/ui/BearingHistoryFragmentTest.kt` | — | ⬚ |
| F-6 | Write failing instrumented tests for empty state: AT-HIST-04-A (zero records: empty state visible, RecyclerView GONE, search bar GONE per TSPEC §9.9); AT-HIST-04-B (State A → List reactive transition). | `app/src/androidTest/java/com/luopan/compass/ui/BearingHistoryFragmentTest.kt` | — | ⬚ |
| F-7 | Write failing instrumented tests for interference badge: AT-HIST-05-A (badge visible on flagged record); AT-HIST-05-B (badge GONE on clean record); AT-HIST-05-C (expanded detail shows "25%" and "4°" for stored values 0.25 and 4.7). | `app/src/androidTest/java/com/luopan/compass/ui/BearingHistoryFragmentTest.kt` | — | ⬚ |
| F-8 | Write failing instrumented tests for search UI: AT-HIST-02-E (zero-match shows "No bearings match your search"). | `app/src/androidTest/java/com/luopan/compass/ui/BearingHistoryFragmentTest.kt` | — | ⬚ |
| F-9 | Write failing instrumented tests for swipe-to-delete: AT-HIST-03-A (immediate DB commit on swipe); AT-HIST-03-B (undo restores record); AT-HIST-03-C (second swipe cancels first undo); AT-HIST-03-E (ActivityScenario close + relaunch: no Snackbar, record absent). | `app/src/androidTest/java/com/luopan/compass/ui/BearingHistorySwipeTest.kt` | — | ⬚ |
| F-10 | Write failing instrumented tests for age banner: AT-CAL-01-A (banner at 31 days); AT-CAL-01-B (banner absent at exactly 30 days); AT-CAL-01-D (X dismiss persists in session after tab switch); AT-CAL-01-E (banner reappears after session dismiss in new ActivityScenario); AT-CAL-01-F (RESULT_OK dismisses banner and updates age days); AT-CAL-01-G (both banners visible simultaneously). | `app/src/androidTest/java/com/luopan/compass/ui/RecalibrationBannerTest.kt` | — | ⬚ |
| F-11 | Write failing instrumented tests for drift banner: AT-CAL-02-B (X button starts cooldown timestamp); AT-CAL-02-C (RESULT_OK resets detector and clears cooldown); AT-CAL-02-F (banner root view does not exist in ModernCompassFragment layout). | `app/src/androidTest/java/com/luopan/compass/ui/RecalibrationBannerTest.kt` | — | ⬚ |
| F-12 | Write failing instrumented test AT-NAV-01-C (RESULT_OK: age banner GONE, calibration_age_days reflects updated value — no spy required per TSPEC §9.5). | `app/src/androidTest/java/com/luopan/compass/ui/NavigationTabTest.kt` | — | ⬚ |
| F-13 | Verify all instrumented tests pass (on-device/emulator run). Fix any failures. | All instrumented test files | `app/src/main/java/com/luopan/compass/ui/BearingHistoryFragment.kt` | ⬚ |

**Files touched in Phase F:**

- `app/src/main/res/navigation/nav_graph.xml`
- `app/src/main/res/layout/activity_compass.xml`
- `app/src/main/java/com/luopan/compass/CompassActivity.kt`
- `app/src/main/java/com/luopan/compass/ui/BearingHistoryFragment.kt` (new)
- `app/src/androidTest/java/com/luopan/compass/ui/BearingHistoryFragmentTest.kt` (new)
- `app/src/androidTest/java/com/luopan/compass/ui/BearingHistorySwipeTest.kt` (new)
- `app/src/androidTest/java/com/luopan/compass/ui/RecalibrationBannerTest.kt` (new)
- `app/src/androidTest/java/com/luopan/compass/ui/NavigationTabTest.kt` (new)

---

## Phase G: Performance and Final Integration

**Dependency:** Phase F complete. All unit and instrumented tests passing.
**Parallelizable with:** Nothing — this is the final gate.

| # | Task | Test File | Source File | Status |
|---|------|-----------|-------------|--------|
| G-1 | Write instrumented performance test in a dedicated file: seed 500 records in Room in-memory DB; time `dao.getAllFlow().first()` on `Dispatchers.IO`; assert elapsed < 500 ms. Kept separate from `BearingHistoryFragmentTest.kt` to avoid slow data-seeding bleed into functional test runs. (TE PLAN-v1 F-07) | `app/src/androidTest/java/com/luopan/compass/ui/BearingHistoryPerfTest.kt` | — | ⬚ |
| G-2 | Write macrobenchmark fling test: `FrameTimingMetric` with `BearingHistoryFragment` fling scenario at 500 records; non-blocking CI gate (regression alert only). | `benchmark/src/main/java/com/luopan/compass/benchmark/BearingHistoryBenchmark.kt` | — | ⬚ |
| G-3 | Run full test suite (unit + instrumented); confirm zero regressions in existing tests. | All test files | — | ⬚ |
| G-4 | Manual test AT-HIST-03-D: launch app, save bearing, swipe to delete, press Home, force-stop via Settings → Apps, relaunch, open History tab, verify deleted record absent and no Snackbar shown. Record result in task tracking. | — | — | ⬚ |

**Files touched in Phase G:**

- `app/src/androidTest/java/com/luopan/compass/ui/BearingHistoryPerfTest.kt` (new — 500-record Room perf test, isolated from functional tests)
- `benchmark/src/main/java/com/luopan/compass/benchmark/BearingHistoryBenchmark.kt` (new or additions to existing benchmark module)

---

## Dependency Graph

```
A-0: FSPEC Backport  ← GATE: no implementation phase may start until A-0 is complete
    │
    └── Phase A: DB Migration + Data Layer  [requires A-0]
            │
            ├── Phase B: Drift Detection (parallel with C)  [requires A]
            │       │
            │       └── Phase D: CompassViewModel Wiring  [requires B]
            │               │
            │               └── Phase E: BearingHistoryViewModel + Adapter  [requires A + D]
            │                       │
            │                       └── Phase F: Navigation + Fragment + Instrumented Tests  [requires C + E]
            │                               │
            │                               └── Phase G: Performance + Final Integration  [requires F]
            │
            └── Phase C: Sensor Capability Logging (parallel with B)  [requires A]
                    │
                    └── (merges into Phase F — SensorCapabilityLogger called from CompassActivity in F-2)
```

**Parallelizable pairs:**
- Phase B and Phase C can run in parallel once Phase A is done.
- Phase D can start as soon as Phase B is done (does not wait for C).
- Phase F includes the `CompassActivity` changes that wire in `SensorCapabilityLogger` (Phase C output); therefore F can only start once both C and E are done.

---

## Integration Points

| Component | Integrates With | How |
|-----------|----------------|-----|
| `DriftDetector` (via `IDriftDetector`) | `CompassViewModel` | Constructor injection; `onFrame()` called in sensor loop on `Dispatchers.Default` |
| `AccelerometerVarianceTracker` | `CompassViewModel` | Constructor injection; `update()` called before `DriftDetector.onFrame()` in sensor loop |
| `BearingDao.getAllFlow()` / `searchFlow()` | `BearingHistoryViewModel` | DAO injected via constructor; `flatMapLatest` in `bearingList` Flow |
| `CompassViewModel.driftBannerState` | `BearingHistoryFragment` | `repeatOnLifecycle(STARTED)` collector; `updateDriftBanner()` call |
| `CompassViewModel.uiState.calibration_age_days` | `BearingHistoryFragment` | `repeatOnLifecycle(STARTED)` collector; `updateAgeBanner()` call |
| `SensorCapabilityLogger` | `CompassActivity` | Called in `onCreate()` via `lifecycleScope.launch(Dispatchers.IO)` |
| `CalibrationResult.sphereRadius_uT` | `CalibrationRepository.toRecord()` | Mapped to `CalibrationRecord.expected_field_ut` |
| `CalibrationRecord.expected_field_ut` | `CompassViewModel` | Cached as `expectedFieldUt: Float`; refreshed in `loadCalibrationAge()` |
| `SettingsRepository.driftCooldownTimestampMs` | `CompassViewModel` | Read in sensor loop cooldown check; written by `dismissDriftBanner()` |
| `SettingsRepository.sensorProfileWrittenForVersion` | `SensorCapabilityLogger` | Version gate read/write |
| `MIGRATION_2_3` | `LuopanDatabase` | Added to `addMigrations(MIGRATION_1_2, MIGRATION_2_3)` |
| `BearingHistoryFragment.calWizardLauncherAge` | `CompassViewModel.onCalibrationCompleteFromHistory()` | Calls on `RESULT_OK` |
| `BearingHistoryFragment.calWizardLauncherDrift` | `CompassViewModel.resetDriftDetector()` | Calls on `RESULT_OK` |

---

## Test File Inventory

### New Unit Test Files

| File | Tests |
|------|-------|
| `app/src/test/java/com/luopan/compass/drift/AccelerometerVarianceTrackerTest.kt` | AT-VAR-01; window eviction; n<2 guard |
| `app/src/test/java/com/luopan/compass/drift/IDriftDetectorContractTest.kt` | IDriftDetector interface contract; DriftEvent enum values (B-2a) |
| `app/src/test/java/com/luopan/compass/drift/DriftDetectorTest.kt` | AT-CAL-03-A through F; AT-CAL-03-B2 |
| `app/src/test/java/com/luopan/compass/drift/DriftDetectorIntegrationTest.kt` | AT-CAL-INT-01 JVM integration (two-phase: pre-threshold negative + post-threshold positive) |
| `app/src/test/java/com/luopan/compass/drift/FakeDriftDetector.kt` | Test double (no tests; used by CompassViewModelDriftTest) |
| `app/src/test/java/com/luopan/compass/drift/FakeAccelerometerVarianceTracker.kt` | Test double (no tests; used by CompassViewModelDriftTest) |
| `app/src/test/java/com/luopan/compass/ui/CompassViewModelDriftTest.kt` | AT-VM-DRIFT-01; AT-VM-DRIFT-01b; AT-CAL-01-C (floor division); dismissCalAgeBanner() state transition; dismissDriftBanner() cooldown write; AT-CAL-02-D (cooldown suppression); AT-CAL-02-E (cooldown re-arm); onCalibrationCompleteFromHistory() re-flash mitigation |
| `app/src/test/java/com/luopan/compass/ui/BearingHistoryViewModelTest.kt` | AT-HIST-02-A through D; AT-UNDO-VM-01 through 03; updateListState() four-branch logic (E-2a) |
| `app/src/test/java/com/luopan/compass/ui/FakeBearingDao.kt` | Recording fake (no tests; used by BearingHistoryViewModelTest) |
| `app/src/test/java/com/luopan/compass/ui/BearingAdapterFormatTest.kt` | AT-HIST-05-D; field deviation format tests |
| `app/src/test/java/com/luopan/compass/diagnostics/SensorCapabilityLoggerTest.kt` | AT-SENSOR-01-E through G; reporting mode mapping |

### Modified Unit Test Files

| File | Additions |
|------|-----------|
| `app/src/test/java/com/luopan/compass/calibration/CalibrationRepositoryTest.kt` | `sphereRadius_uT` field mapping tests |
| `app/src/test/java/com/luopan/compass/bearing/BearingDaoTest.kt` | `getAllFlow()` and `searchFlow()` tests |
| `app/src/test/java/com/luopan/compass/settings/SettingsRepositoryTest.kt` | Phase 4 property tests |

### New Instrumented Test Files

| File | Tests |
|------|-------|
| `app/src/androidTest/java/com/luopan/compass/ui/BearingHistoryFragmentTest.kt` | AT-HIST-01-A through C; AT-HIST-04-A through B; AT-HIST-05-A through C; AT-HIST-02-E |
| `app/src/androidTest/java/com/luopan/compass/ui/BearingHistoryPerfTest.kt` | 500-record Room performance test (isolated from functional tests per TE PLAN-v1 F-07) |
| `app/src/androidTest/java/com/luopan/compass/ui/BearingHistorySwipeTest.kt` | AT-HIST-03-A through C; AT-HIST-03-E |
| `app/src/androidTest/java/com/luopan/compass/ui/RecalibrationBannerTest.kt` | AT-CAL-01-A through G; AT-CAL-02-B through C; AT-CAL-02-F |
| `app/src/androidTest/java/com/luopan/compass/ui/NavigationTabTest.kt` | AT-NAV-01-A through C |
| `app/src/androidTest/java/com/luopan/compass/diagnostics/SensorCapabilityLoggerInstrumentedTest.kt` | AT-SENSOR-01-A through D |

### Modified Instrumented Test Files

| File | Additions |
|------|-----------|
| `app/src/androidTest/java/com/luopan/compass/db/LuopanDatabaseMigrationTest.kt` | MIGRATION_2_3 tests |

### New Benchmark Files

| File | Tests |
|------|-------|
| `benchmark/src/main/java/com/luopan/compass/benchmark/BearingHistoryBenchmark.kt` | 16 ms fling frame time (non-blocking CI gate) |

---

## Manual Tests

| ID | Procedure | Condition |
|----|-----------|-----------|
| AT-HIST-03-D | (1) Launch app. (2) Save a bearing. (3) Swipe to delete — Snackbar appears. (4) Press Home. (5) Force-stop app via Settings → Apps. (6) Relaunch. (7) Open History tab. Verify: deleted record absent; no Snackbar shown. | Per FSPEC-HIST-03: process-death deletion is permanent; not automatable as a standard instrumented test. |

---

## Definition of Done

- [ ] All unit tests pass on JVM (`./gradlew test`)
- [ ] All instrumented tests pass on a device or emulator (`./gradlew connectedAndroidTest`)
- [ ] Room migration test (v2→v3) passes; existing v1→v2 migration test continues to pass
- [ ] No existing tests regressed (Phase 1–3 test suite fully green)
- [ ] `FSPEC-CAL-03` updated with "COUNTING past 60 s when deviation ≤ 10%" rule (A-0 complete)
- [ ] `IDriftDetector` interface exists; `CompassViewModel` accepts it so `FakeDriftDetector` can be injected in tests
- [ ] `SensorCapabilityLogger.buildJsonFromSensors()` is an `internal` pure function; context dependency confined to `buildJson()`
- [ ] `SensorCapabilityLogger.maybeWrite()` uses `catch (e: Exception)` (covers both IOException and SecurityException)
- [ ] `BearingAdapter.toggleExpanded()` uses `notifyItemChanged()` on only two affected positions (not `notifyDataSetChanged()`)
- [ ] Snackbar duration is `setDuration(5000)` (not `Snackbar.LENGTH_LONG`)
- [ ] Snackbar body text is "Bearing deleted" (not "Deleted")
- [ ] AT-HIST-01-A badge assertion uses `scrollToPosition` inside per-position loop
- [ ] AT-CAL-INT-01 has two-phase structure: pre-threshold negative assertion (58 s) + post-threshold positive assertion (62 s)
- [ ] AT-CAL-01-C (floor-division unit test for `computeCalibrationAgeDays()`) written and passing
- [ ] AT-CAL-02-D (cooldown suppression) and AT-CAL-02-E (cooldown re-arm) unit tests written and passing in `CompassViewModelDriftTest.kt`
- [ ] `dismissCalAgeBanner()` and `dismissDriftBanner()` have dedicated unit tests asserting ViewModel state transitions
- [ ] `onCalibrationCompleteFromHistory()` async re-flash mitigation has a unit test asserting synchronous flag set and post-IO flag clear
- [ ] `updateListState()` four-branch logic has a unit test covering all four visibility combinations
- [ ] `IDriftDetector` interface contract and `DriftEvent` enum values are asserted by `IDriftDetectorContractTest`
- [ ] Performance test is in dedicated `BearingHistoryPerfTest.kt` (not co-located in `BearingHistoryFragmentTest.kt`)
- [ ] Manual test AT-HIST-03-D executed, result signed off, and sign-off record linked in task tracking (sign-off is required to close this DoD item)
- [ ] Macrobenchmark fling test added to `:benchmark` module and CI reports result (non-blocking gate)
