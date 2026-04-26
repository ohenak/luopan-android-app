# PROPERTIES: luopan-p1-core-compass
**Version:** 0.2-draft
**Date:** 2026-04-24
**Status:** Draft
**Author:** te-author
**Source documents:**
- REQ: `docs/luopan-compass/REQ-luopan-p1-core-compass.md`
- FSPEC: `docs/luopan-compass/FSPEC-luopan-p1-core-compass.md`
- TSPEC: `docs/luopan-compass/TSPEC-luopan-p1-core-compass.md`
- PLAN: `docs/luopan-compass/PLAN-luopan-p1-core-compass.md`

---

## Overview

This document specifies testable properties (invariants) for Phase 1 of the Luopan compass app. Each property is a precise, falsifiable assertion about system behaviour that must hold under all stated conditions.

**API notes (from TSPEC):**
- `FusionResult.heading_deg` is `Double` — use `0.0`, `360.0` (not `0.0f`)
- `MadgwickFilter.update(gx, gy, gz, ax, ay, az, mx, my, mz, dt: Float)` — full update with gyro
- `MadgwickFilter.updateNoGyro(ax, ay, az, mx, my, mz, dt: Float)` — accelerometer + mag only
- `InterferenceState` enum: `CLEAR`, `MODERATE`, `WARNING` — no `INTERFERENCE` value
- `InterferenceDetector.updateState(metrics, nowNs)` — `metrics.fieldDeviation` is a fraction (0.25f = 25%)
- `OverallConfidence` enum: `HIGH`, `MODERATE`, `POOR`, `SENSOR_ERROR`, `STABILIZING`
- `ConfidenceScore` enum: `GOOD(3)`, `MODERATE(2)`, `POOR(1)` — used for dimension scores
- `SensorState` enum: `NORMAL`, `STABILIZING`, `STUCK`
- `CalibrationQuality` enum: `GOOD`, `FAIR`, `POOR`
- `CalibrationEngine` exposes `@VisibleForTesting fun classifyQuality(residual: Float, coverage: CoverageStats): CalibrationQuality`

**Test level key:**
- **JVM** — plain JUnit4/5, no Android framework, runs on host
- **Instrumented** — JUnit4 with AndroidJUnit4 runner, requires emulator/device
- **Espresso** — UI test with ActivityScenarioRule, requires emulator/device

**Type key:**
- **Unit** — single class or function in isolation
- **Integration** — two or more collaborating classes
- **E2E** — full application stack through UI

---

## Domain: PROP-FUSION — Sensor Fusion Engine

### PROP-FUSION-01: Heading always in [0, 360)
**Type:** Unit
**Test Level:** JVM
**Source:** REQ-SENSOR-04, TSPEC §3.4

**Property:** `FusionEngine.update()` always produces a heading value in the half-open interval `[0.0, 360.0)`.

**Given:** A `MadgwickFilter` in any state (fresh, partially converged, fully converged)
**When:** `FusionEngine.update(frame)` is called with any valid `SensorFrame`
**Then:** `result.heading_deg >= 0.0 && result.heading_deg < 360.0`

---

### PROP-FUSION-02: Heading is never NaN or Infinite
**Type:** Unit
**Test Level:** JVM
**Source:** REQ-SENSOR-04, TSPEC §3.4

**Property:** `FusionEngine.update()` never returns a NaN or Infinite heading, even with degenerate sensor input (all-zero magnetometer, vertical orientation).

**Given:** A fresh `FusionEngine`
**When:** `update(frame)` is called with degenerate inputs (e.g., `mx=0f, my=0f, mz=0f`)
**Then:** `result.heading_deg.isFinite() == true && !result.heading_deg.isNaN()`

---

### PROP-FUSION-03: Quaternion normalized after every Madgwick update
**Type:** Unit
**Test Level:** JVM
**Source:** TSPEC §3.1, §3.2

**Property:** After each `MadgwickFilter.update()` call, the internal quaternion satisfies `|q| ∈ [1.0 - 1e-6, 1.0 + 1e-6]`.

**Given:** A `MadgwickFilter` instance with any internal state
**When:** `update(gx=0f, gy=0f, gz=0f, ax=0f, ay=0f, az=-9.81f, mx=30f, my=0f, mz=-30f, dt=0.02f)` is called
**Then:** `sqrt(q0*q0 + q1*q1 + q2*q2 + q3*q3).toDouble()` is within `1e-6` of `1.0`

---

### PROP-FUSION-04: Full update converges to North within 50 iterations
**Type:** Unit
**Test Level:** JVM
**Source:** REQ-SENSOR-04, TSPEC §3.2 — T-2-01

**Property:** `MadgwickFilter.update()` (with zero gyro input) converges toward North (0°) within 50 iterations when given gravity-down acceleration and a North-pointing magnetometer field.

**Given:** A fresh `MadgwickFilter` instance
**When:** `update(gx=0f, gy=0f, gz=0f, ax=0f, ay=0f, az=-9.81f, mx=30f, my=0f, mz=-30f, dt=0.02f)` is called 50 times
**Then:** `quaternionToHeadingDeg(q0, q1, q2, q3)` is within ±5.0° of 0.0

---

### PROP-FUSION-05: No-gyro mode converges to North within 50 iterations
**Type:** Unit
**Test Level:** JVM
**Source:** REQ-SENSOR-04, TSPEC §3.2 — T-2-01

**Property:** `MadgwickFilter.updateNoGyro()` converges toward North (0°) within 50 iterations.

**Given:** A fresh `MadgwickFilter` instance
**When:** `updateNoGyro(ax=0f, ay=0f, az=-9.81f, mx=30f, my=0f, mz=-30f, dt=0.02f)` is called 50 times
**Then:** `quaternionToHeadingDeg(q0, q1, q2, q3)` is within ±5.0° of 0.0

---

### PROP-FUSION-06: CI proxy — 30 s sequence within ±0.5° of ground truth
**Type:** Integration
**Test Level:** JVM
**Source:** REQ §9 Scenario C, TSPEC §3 — T-2-02 (TE-F-13)

**Property:** `FusionEngine.update()` processing the pre-recorded 30-second sensor sequence produces a final heading within ±0.5° of the recorded ground-truth heading.

**Given:** The file `app/src/test/resources/sensor_sequences/known_heading_30s.json` exists, contains ≥ 1500 `SensorFrame` entries, and the recorded ground-truth heading is `H_ref`
**When:** All frames are fed in order to `FusionEngine.update(frame)`
**Then:** `abs(result.heading_deg - H_ref) <= 0.5` (handling 0°/360° wraparound)

---

## Domain: PROP-CAL — Calibration Engine

### PROP-CAL-01: Coverage zero when samples span no range
**Type:** Unit
**Test Level:** JVM
**Source:** REQ-CAL-02, TSPEC §4.2

**Property:** `computeCoverage()` returns `coverage_x = 0f, coverage_y = 0f, coverage_z = 0f` when all samples are at the same point (zero axis ranges).

**Given:** A list of 50 `MagSample` instances all with identical coordinates `(x=10f, y=5f, z=-3f)` — all axis ranges are 0
**When:** `computeCoverage(samples)` is called
**Then:** `coverage.coverage_x == 0f && coverage.coverage_y == 0f && coverage.coverage_z == 0f`

---

### PROP-CAL-02: Coverage 1.0 on the dominant axis when it spans the full range
**Type:** Unit
**Test Level:** JVM
**Source:** REQ-CAL-02, TSPEC §4.2

**Property:** `computeCoverage()` returns `coverage_x = 1.0f` when the x-axis spans the largest range across all axes and the samples are distributed across the full range.

**Given:** Samples where `x` ranges from -50f to +50f (xRange=100f) and `y`, `z` ranges are smaller (yRange=20f, zRange=10f) — `totalRange = max(100, 20, 10) = 100`
**When:** `computeCoverage(samples)` is called
**Then:** `coverage.coverage_x == 1.0f` and `coverage.coverage_y == 0.2f` and `coverage.coverage_z == 0.1f`

---

### PROP-CAL-03: Residual ≤ 1.0 µT and coverage ≥ 20% → GOOD quality
**Type:** Unit
**Test Level:** JVM
**Source:** REQ-CAL-01, TSPEC §4.4

**Property:** `CalibrationEngine.classifyQuality()` returns `GOOD` when `residual_rms ≤ 1.0 µT` and all coverage axes ≥ 0.20f.

**Given:** `residual = 0.8f` µT; `coverage = CoverageStats(coverage_x=0.3f, coverage_y=0.3f, coverage_z=0.3f)`
**When:** `classifyQuality(residual, coverage)` is called
**Then:** result is `CalibrationQuality.GOOD`

---

### PROP-CAL-04: Residual exactly 1.0 µT → GOOD (closed upper bound)
**Type:** Unit
**Test Level:** JVM
**Source:** REQ §5.3 TE-04, TSPEC §4.4

**Property:** A residual of exactly 1.0 µT maps to `GOOD`, not `FAIR`. The GOOD interval is `[0, 1.0 µT]` (closed upper bound).

**Given:** `residual = 1.0f` µT; `coverage = CoverageStats(0.25f, 0.25f, 0.25f)`
**When:** `classifyQuality(residual, coverage)` is called
**Then:** result is `CalibrationQuality.GOOD`

---

### PROP-CAL-05: Residual above 1.0 µT → FAIR (open lower bound on FAIR interval)
**Type:** Unit
**Test Level:** JVM
**Source:** REQ §5.3 TE-04, TSPEC §4.4

**Property:** A residual just above 1.0 µT maps to `FAIR`. The FAIR interval is `(1.0, 2.0 µT]` (open lower bound).

**Given:** `residual = 1.0001f` µT; `coverage = CoverageStats(0.20f, 0.20f, 0.20f)`
**When:** `classifyQuality(residual, coverage)` is called
**Then:** result is `CalibrationQuality.FAIR`

---

### PROP-CAL-06: Residual exactly 2.0 µT → FAIR (not POOR)
**Type:** Unit
**Test Level:** JVM
**Source:** REQ §5.3 TE-04, TSPEC §4.4

**Property:** A residual of exactly 2.0 µT maps to `FAIR`. The FAIR interval is closed at the upper bound.

**Given:** `residual = 2.0f` µT; `coverage = CoverageStats(0.20f, 0.20f, 0.20f)`
**When:** `classifyQuality(residual, coverage)` is called
**Then:** result is `CalibrationQuality.FAIR`

---

### PROP-CAL-07: Residual above 2.0 µT → POOR
**Type:** Unit
**Test Level:** JVM
**Source:** REQ §5.3 TE-04, TSPEC §4.4

**Property:** A residual above 2.0 µT maps to `POOR`.

**Given:** `residual = 2.5f` µT; `coverage = CoverageStats(0.20f, 0.20f, 0.20f)`
**When:** `classifyQuality(residual, coverage)` is called
**Then:** result is `CalibrationQuality.POOR`

---

### PROP-CAL-08: Atomic write — OOM mid-write leaves DB readable
**Type:** Integration
**Test Level:** Instrumented
**Source:** REQ-CAL-04, TSPEC §4.6 — T-5-06

**Property:** If the app process is killed during a calibration write, the database remains consistent and `getCurrentCalibration()` returns either the previous record or the new record — never null (unless no prior record existed) and never a corrupt partial record.

**Given:** A `CalibrationRepository` with one existing `CalibrationRecord` (id=1)
**When:** The process is killed (via `ProcessPhoenix` or `Process.killProcess()`) while a new calibration is being saved in a `@Transaction`
**Then:** After relaunch, `CalibrationRepository.getCurrentCalibration()` returns either the original record or the new record; for whichever is returned, `record.residual_rms.isFinite()` is `true`

---

### PROP-CAL-09: Rollback slot — third save evicts oldest
**Type:** Unit
**Test Level:** JVM
**Source:** REQ-CAL-04, TSPEC §4.6 — T-3-03

**Property:** After three sequential saves, the database contains exactly 2 records (current + 1 rollback); the oldest record is evicted. Tested via Robolectric in-memory Room database.

**Given:** A `CalibrationRepository` backed by `Room.inMemoryDatabaseBuilder()`
**When:** Three `CalibrationRecord` instances are saved in sequence (record1 → record2 → record3)
**Then:** `getAll()` returns exactly 2 records: `record3` (current) and `record2` (rollback); `record1` is absent

---

### PROP-CAL-10: Age computed in UTC days since last save
**Type:** Unit
**Test Level:** JVM
**Source:** REQ-CAL-02, TSPEC §9.2

**Property:** Calibration age is computed as `(currentTimeUtcMs - savedAtUtcMs) / 86_400_000L` days using UTC epoch milliseconds.

**Given:** A `CalibrationRecord` with `saved_at_utc = T` ms and current time = `T + 8 * 86_400_000L`
**When:** age in days is computed
**Then:** `ageInDays == 8L`

---

## Domain: PROP-DETECT — Interference Detection

*Note: `InterferenceDetector.updateState(metrics, nowNs)` uses `metrics.fieldDeviation` as a fraction (0.25f = 25%) and `metrics.inclinationDeviation_deg` in degrees. The three `InterferenceState` values are: `CLEAR`, `MODERATE`, `WARNING`.*

### PROP-DETECT-01: Field deviation 24.9% (0.249f) — stays CLEAR from CLEAR
**Type:** Unit
**Test Level:** JVM
**Source:** REQ §5.3 TE-11, TSPEC §5.4

**Property:** A field deviation of 24.9% (0.249f) from a CLEAR initial state does not trigger WARNING; it transitions to MODERATE (the advisory band).

**Given:** `InterferenceDetector` with `currentState = CLEAR`; `metrics.inclinationDeviation_deg = 0f`
**When:** `updateState(metrics(fieldDeviation=0.249f), nowNs)` is called
**Then:** result is `InterferenceState.MODERATE`

---

### PROP-DETECT-02: Field deviation 25.0% (0.25f) → WARNING
**Type:** Unit
**Test Level:** JVM
**Source:** REQ §5.3 TE-11, TSPEC §5.4

**Property:** A field deviation of exactly 25.0% (0.25f) triggers `WARNING`.

**Given:** `InterferenceDetector` with `currentState = CLEAR`
**When:** `updateState(metrics(fieldDeviation=0.25f, inclinationDeviation_deg=0f), nowNs)` is called
**Then:** result is `InterferenceState.WARNING`

---

### PROP-DETECT-03: Field deviation 25.1% (0.251f) → WARNING
**Type:** Unit
**Test Level:** JVM
**Source:** REQ §5.3 TE-11, TSPEC §5.4

**Property:** A field deviation above 25.0% continues to trigger `WARNING`.

**Given:** `InterferenceDetector` with `currentState = CLEAR`
**When:** `updateState(metrics(fieldDeviation=0.251f, inclinationDeviation_deg=0f), nowNs)` is called
**Then:** result is `InterferenceState.WARNING`

---

### PROP-DETECT-04: Field below 15% for 3 s from WARNING → CLEAR
**Type:** Unit
**Test Level:** JVM
**Source:** REQ §5.3 TE-11, TSPEC §5.4

**Property:** After `WARNING` triggers, sustained field deviation below 15% (< 0.15f) for ≥ 3.0 seconds clears the state.

**Given:** `InterferenceDetector` with `currentState = WARNING`; `FakeTimeSource` at `t = 0 ns`
**When:** `updateState(metrics(fieldDeviation=0.10f, inclinationDeviation_deg=0f), nowNs=0)` is called to start the timer, then `updateState(..., nowNs=3_000_000_000L)` is called (3.0 s elapsed)
**Then:** final result is `InterferenceState.CLEAR`

---

### PROP-DETECT-05: Field exactly 15.0% resets clearance timer → MODERATE (not CLEAR)
**Type:** Unit
**Test Level:** JVM
**Source:** REQ §5.3 TE-11, TSPEC §5.4

**Property:** Field deviation at exactly 15.0% (0.15f) does NOT satisfy the `isBelow` clearance condition (`< 0.15f` is strict) — it falls in the MODERATE band and resets `clearanceStartNs` to 0.

**Given:** `InterferenceDetector` with `currentState = WARNING`; clearance timer running (field was below 15% for 2.9 s)
**When:** One frame arrives with `fieldDeviation = 0.15f`
**Then:** result is `InterferenceState.MODERATE` (not CLEAR; the clearance timer has been reset)

---

### PROP-DETECT-06: Clearance timer not elapsed → stays WARNING
**Type:** Unit
**Test Level:** JVM
**Source:** REQ §5.3 TE-11, TSPEC §5.4

**Property:** Field deviation below 15% for only 2.9 seconds does not clear the `WARNING` state — `currentState` (WARNING) is returned.

**Given:** `InterferenceDetector` with `currentState = WARNING`; `FakeTimeSource` at `t = 0`
**When:** `updateState(metrics(fieldDeviation=0.10f), nowNs=0L)` to start timer; then `updateState(metrics(fieldDeviation=0.10f), nowNs=2_900_000_000L)` (2.9 s elapsed)
**Then:** result is `InterferenceState.WARNING` (clearance window not yet elapsed)

---

### PROP-DETECT-07: Clearance timer resets on mid-window excursion
**Type:** Unit
**Test Level:** JVM
**Source:** REQ §5.3 TE-11, TSPEC §5.4

**Property:** If field deviation rises to ≥ 0.15f during the clearance window, `clearanceStartNs` resets to 0. A full uninterrupted 3.0 s below 0.15f is required from that point to reach CLEAR.

**Given:** `InterferenceDetector` with `currentState = WARNING`
**When:** (1) `fieldDeviation=0.10f` for 2.9 s (timer running); (2) one frame at `fieldDeviation=0.20f` (MODERATE band — timer resets); (3) `fieldDeviation=0.10f` for 2.9 s again
**Then:** After step (3) the state is still WARNING (timer reset in step 2; need another full 3.0 s)

---

### PROP-DETECT-08: Inclination < 3° — stays CLEAR
**Type:** Unit
**Test Level:** JVM
**Source:** REQ §5.3, TSPEC §5.4

**Property:** An inclination deviation below 3.0° when field deviation is also below 15% keeps the state in the `isBelow` clearance zone (does not trigger MODERATE or WARNING).

**Given:** `InterferenceDetector` with `currentState = CLEAR`; `fieldDeviation = 0.05f`
**When:** `updateState(metrics(fieldDeviation=0.05f, inclinationDeviation_deg=2.9f), nowNs=0)` is called; then repeated at `nowNs = 3_100_000_000L`
**Then:** final result is `InterferenceState.CLEAR` (stayed in clearance zone the whole time, timer completes)

---

### PROP-DETECT-09: Inclination 3.0° → MODERATE (advisory band)
**Type:** Unit
**Test Level:** JVM
**Source:** REQ §5.3, TSPEC §5.4

**Property:** Inclination deviation at exactly 3.0° does NOT satisfy `isBelow` (`< 3.0f` strict) and does NOT satisfy `isWarning` (`>= 8.0f`) — it falls in the MODERATE advisory band.

**Given:** `InterferenceDetector` with `currentState = CLEAR`; `fieldDeviation = 0.05f`
**When:** `updateState(metrics(fieldDeviation=0.05f, inclinationDeviation_deg=3.0f), nowNs)` is called
**Then:** result is `InterferenceState.MODERATE`

---

### PROP-DETECT-10: Inclination 7.9° → MODERATE (still advisory, below WARNING threshold)
**Type:** Unit
**Test Level:** JVM
**Source:** REQ §5.3, TSPEC §5.4

**Property:** Inclination deviation of 7.9° is below the WARNING trigger of 8.0° — state is MODERATE.

**Given:** `InterferenceDetector` with `currentState = CLEAR`; `fieldDeviation = 0.05f`
**When:** `updateState(metrics(fieldDeviation=0.05f, inclinationDeviation_deg=7.9f), nowNs)` is called
**Then:** result is `InterferenceState.MODERATE`

---

### PROP-DETECT-11: Inclination 8.0° → WARNING
**Type:** Unit
**Test Level:** JVM
**Source:** REQ §5.3, TSPEC §5.4

**Property:** Inclination deviation at exactly 8.0° triggers `WARNING`.

**Given:** `InterferenceDetector` with `currentState = CLEAR`
**When:** `updateState(metrics(fieldDeviation=0.05f, inclinationDeviation_deg=8.0f), nowNs)` is called
**Then:** result is `InterferenceState.WARNING`

---

### PROP-DETECT-12: Spike rejection — single outlier does not trigger WARNING
**Type:** Unit
**Test Level:** JVM
**Source:** REQ-DETECT-04, TSPEC §5.2

**Property:** A single frame spike above the interference threshold, surrounded by normal frames, is filtered by `NoiseSpikeFilter` and does not cause a WARNING state transition.

**Given:** `NoiseSpikeFilter` and `InterferenceDetector` in CLEAR state; `fieldDeviation` consistently at 0.05f
**When:** One frame arrives with `fieldDeviation = 0.80f`; subsequent frames return to 0.05f
**Then:** `InterferenceDetector.currentState == InterferenceState.CLEAR` after the outlier frame is processed

---

### PROP-DETECT-13: Spike filter boundary (`>` operator — strict)
**Type:** Unit
**Test Level:** JVM
**Source:** TSPEC §5.2 — T-2-04

**Property:** `NoiseSpikeFilter` uses a strict `>` comparison at the spike threshold `T`: a value exactly at `T` passes through (not filtered); a value strictly above `T` is filtered.

**Given:** A `NoiseSpikeFilter` with threshold `T`
**When:** Two frames: frame A at exactly `T`, frame B at `T + epsilon`
**Then:** Frame A passes through unmodified; Frame B is filtered (replaced with previous valid value)

---

## Domain: PROP-CONF — Confidence Model

*Note: `ConfidenceModel.compute()` returns `OverallConfidence` (HIGH/MODERATE/POOR/SENSOR_ERROR/STABILIZING). Dimension scores use `ConfidenceScore` (GOOD(3)/MODERATE(2)/POOR(1)). All tilt values are `Double`.*

### PROP-CONF-01: Overall confidence is minimum of five dimension scores
**Type:** Unit
**Test Level:** JVM
**Source:** REQ §5.5, TSPEC §6.3

**Property:** `ConfidenceModel.compute()` returns the OverallConfidence corresponding to the minimum of the five dimension ConfidenceScore values before tilt clamping.

**Given:** Five dimension scores: `[GOOD(3), GOOD(3), MODERATE(2), GOOD(3), GOOD(3)]`; `tilt_deg = 0.0`; `hasGyroscope = true`; `sensorState = NORMAL`
**When:** `compute(...)` is called
**Then:** result is `OverallConfidence.MODERATE` (minimum = 2 → MODERATE)

---

### PROP-CONF-02: Tilt ≤ 5.0° — no clamp applied
**Type:** Unit
**Test Level:** JVM
**Source:** REQ §5.5, TSPEC §6.3

**Property:** When `tilt_deg ≤ 5.0`, the tilt clamp does not reduce the overall confidence.

**Given:** All five dimensions `GOOD(3)`; `tilt_deg = 5.0`; `hasGyroscope = true`; `sensorState = NORMAL`
**When:** `compute(...)` is called
**Then:** result is `OverallConfidence.HIGH`

---

### PROP-CONF-03: Tilt 5.1° → MODERATE clamp applied
**Type:** Unit
**Test Level:** JVM
**Source:** REQ §5.5, TSPEC §6.3 — boundary `(5°, 20°]`

**Property:** `tilt_deg > 5.0` applies the MODERATE clamp. At 5.1°, a GOOD overall is clamped to MODERATE.

**Given:** All five dimensions `GOOD(3)`; `tilt_deg = 5.1`; `hasGyroscope = true`; `sensorState = NORMAL`
**When:** `compute(...)` is called
**Then:** result is `OverallConfidence.MODERATE`

---

### PROP-CONF-04: Tilt 20.0° → MODERATE clamp (not POOR)
**Type:** Unit
**Test Level:** JVM
**Source:** REQ §5.5, TSPEC §6.3 — POOR clamp condition is `tilt_deg > 20.0`

**Property:** Tilt at exactly 20.0° does NOT satisfy `tilt_deg > 20.0` — the MODERATE clamp applies, not the POOR clamp.

**Given:** All five dimensions `GOOD(3)`; `tilt_deg = 20.0`; `hasGyroscope = true`; `sensorState = NORMAL`
**When:** `compute(...)` is called
**Then:** result is `OverallConfidence.MODERATE` (not POOR)

---

### PROP-CONF-05: Tilt 20.1° → POOR clamp applied
**Type:** Unit
**Test Level:** JVM
**Source:** REQ §5.5, TSPEC §6.3

**Property:** `tilt_deg > 20.0` applies the POOR clamp. At 20.1°, any overall above POOR is clamped to POOR.

**Given:** All five dimensions `GOOD(3)`; `tilt_deg = 20.1`; `hasGyroscope = true`; `sensorState = NORMAL`
**When:** `compute(...)` is called
**Then:** result is `OverallConfidence.POOR`

---

### PROP-CONF-06: No-gyroscope caps confidence at MODERATE
**Type:** Unit
**Test Level:** JVM
**Source:** REQ §8 §11.2, TSPEC §6.3 Step 3

**Property:** When `hasGyroscope = false`, overall confidence is capped at `MODERATE` regardless of dimension scores.

**Given:** All five dimensions `GOOD(3)`; `tilt_deg = 0.0`; `hasGyroscope = false`; `sensorState = NORMAL`
**When:** `compute(...)` is called
**Then:** result is `OverallConfidence.MODERATE`

---

### PROP-CONF-07: SensorState.STUCK bypasses scoring → SENSOR_ERROR
**Type:** Unit
**Test Level:** JVM
**Source:** REQ §11.3, TSPEC §6.3

**Property:** When `sensorState = SensorState.STUCK`, `compute()` returns `OverallConfidence.SENSOR_ERROR` without evaluating any dimension scores.

**Given:** `sensorState = SensorState.STUCK`; dimension scores and tilt irrelevant
**When:** `compute(...)` is called
**Then:** result is `OverallConfidence.SENSOR_ERROR`

---

### PROP-CONF-08: SensorState.STABILIZING bypasses scoring → STABILIZING
**Type:** Unit
**Test Level:** JVM
**Source:** REQ §11.7, TSPEC §6.3

**Property:** When `sensorState = SensorState.STABILIZING`, `compute()` returns `OverallConfidence.STABILIZING` without evaluating dimension scores.

**Given:** `sensorState = SensorState.STABILIZING`; dimension scores and tilt irrelevant
**When:** `compute(...)` is called
**Then:** result is `OverallConfidence.STABILIZING`

---

### PROP-CONF-09: Evaluation order — residual 1.0 µT scores GOOD not MODERATE
**Type:** Unit
**Test Level:** JVM
**Source:** TSPEC §6.1 evaluation order rule

**Property:** When `cal_quality` dimension has `residual_rms = 1.0 µT` AND all coverage ≥ 0.20f, the `cal_quality_score` is `ConfidenceScore.GOOD`. The GOOD condition is evaluated first; the MODERATE row starts strictly above 1.0 µT.

**Given:** A `CalibrationRecord` with `residual_rms = 1.0f`, all `coverage ≥ 0.20f`
**When:** `scoreCalibrationQuality(calibration)` is called (or equivalent internal method)
**Then:** result is `ConfidenceScore.GOOD`

---

### PROP-CONF-10: Noise variance 0.1 µT² → GOOD (closed upper bound)
**Type:** Unit
**Test Level:** JVM
**Source:** TSPEC §6.1 — T-2-06

**Property:** `noise_variance_score` for a variance of exactly 0.1 µT² is `GOOD`. The GOOD interval is `[0.0, 0.1 µT²]` (closed upper bound).

**Given:** `noiseVariance_uT2 = 0.1f`
**When:** `scoreNoiseVariance(noiseVariance_uT2)` is called
**Then:** result is `ConfidenceScore.GOOD`

---

### PROP-CONF-11: Noise variance 0.101 µT² → MODERATE
**Type:** Unit
**Test Level:** JVM
**Source:** TSPEC §6.1 — T-2-06

**Property:** Noise variance just above 0.1 µT² falls in the MODERATE interval `(0.1, 0.5 µT²]`.

**Given:** `noiseVariance_uT2 = 0.101f`
**When:** `scoreNoiseVariance(noiseVariance_uT2)` is called
**Then:** result is `ConfidenceScore.MODERATE`

---

### PROP-CONF-12: Noise variance 0.5 µT² → MODERATE (closed upper bound)
**Type:** Unit
**Test Level:** JVM
**Source:** TSPEC §6.1 — T-2-06

**Property:** Noise variance at exactly 0.5 µT² is MODERATE (closed upper bound for MODERATE interval).

**Given:** `noiseVariance_uT2 = 0.5f`
**When:** `scoreNoiseVariance(noiseVariance_uT2)` is called
**Then:** result is `ConfidenceScore.MODERATE`

---

### PROP-CONF-13: Noise variance above 0.5 µT² → POOR
**Type:** Unit
**Test Level:** JVM
**Source:** TSPEC §6.1 — T-2-06

**Property:** Noise variance above 0.5 µT² is POOR.

**Given:** `noiseVariance_uT2 = 0.501f`
**When:** `scoreNoiseVariance(noiseVariance_uT2)` is called
**Then:** result is `ConfidenceScore.POOR`

---

### PROP-CONF-14: Calibration age 7 days → GOOD (closed upper bound)
**Type:** Unit
**Test Level:** JVM
**Source:** TSPEC §6.1 — T-2-06

**Property:** A calibration age of exactly 7 days is `GOOD`. The GOOD interval is `[0, 7 days]` (closed upper bound).

**Given:** `saved_at_utc = T`; current time = `T + 7 * 86_400_000L` (exactly 7 days)
**When:** `scoreCalibrationAge(saved_at_utc)` is called at the current time
**Then:** result is `ConfidenceScore.GOOD`

---

### PROP-CONF-15: Calibration age just above 7 days → MODERATE
**Type:** Unit
**Test Level:** JVM
**Source:** TSPEC §6.1 — T-2-06

**Property:** A calibration age just above 7 days (e.g., 7 days + 1 ms) falls in the MODERATE interval `(7, 30 days]`.

**Given:** `saved_at_utc = T`; current time = `T + 7 * 86_400_000L + 1L` (7 days + 1 ms)
**When:** `scoreCalibrationAge(saved_at_utc)` is called
**Then:** result is `ConfidenceScore.MODERATE`

---

### PROP-CONF-16: Calibration age 30 days → MODERATE (closed upper bound)
**Type:** Unit
**Test Level:** JVM
**Source:** TSPEC §6.1 — T-2-06

**Property:** A calibration age of exactly 30 days is `MODERATE`.

**Given:** `saved_at_utc = T`; current time = `T + 30 * 86_400_000L`
**When:** `scoreCalibrationAge(saved_at_utc)` is called
**Then:** result is `ConfidenceScore.MODERATE`

---

### PROP-CONF-17: Calibration age above 30 days → POOR
**Type:** Unit
**Test Level:** JVM
**Source:** TSPEC §6.1 — T-2-06

**Property:** A calibration age above 30 days is `POOR`.

**Given:** `saved_at_utc = T`; current time = `T + 31 * 86_400_000L`
**When:** `scoreCalibrationAge(saved_at_utc)` is called
**Then:** result is `ConfidenceScore.POOR`

---

## Domain: PROP-DISPLAY — Display and UI Layout

### PROP-DISPLAY-01: All required UI slots present in layout
**Type:** E2E
**Test Level:** Espresso
**Source:** REQ-DISPLAY-01, REQ-DISPLAY-02, REQ-DISPLAY-03, TSPEC §7.2

**Property:** The CompassActivity layout contains all required slots: compass rose, heading readout (degrees), calibration age indicator, confidence badge, tilt indicator slot, interference warning slot, and calibration CTA slot.

**Given:** `CompassActivity` launched in a calibrated state
**When:** The layout is rendered
**Then:** `onView(withId(R.id.compass_rose))`, `withId(R.id.heading_readout)`, `withId(R.id.cal_age_label)`, `withId(R.id.confidence_badge)`, `withId(R.id.tilt_indicator)`, `withId(R.id.interference_warning)`, `withId(R.id.calibration_cta)` all exist in the view hierarchy

---

### PROP-DISPLAY-02: Wake lock flag set unconditionally in onStart
**Type:** Integration
**Test Level:** Instrumented
**Source:** REQ-DISPLAY-10, TSPEC §7.7

**Property:** `CompassActivity` sets `FLAG_KEEP_SCREEN_ON` on the window in `onStart()` unconditionally — no settings flag or boolean gates this behaviour.

**Given:** `CompassActivity` with default settings
**When:** `onStart()` is called
**Then:** `activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0`

---

### PROP-DISPLAY-03: Wake lock flag cleared in onStop
**Type:** Integration
**Test Level:** Instrumented
**Source:** REQ-DISPLAY-10, TSPEC §7.7

**Property:** `FLAG_KEEP_SCREEN_ON` is cleared when `onStop()` is called.

**Given:** `CompassActivity` with wake lock active from `onStart()`
**When:** `onStop()` is called
**Then:** `activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON == 0`

---

### PROP-DISPLAY-04: Mode switcher absent from layout
**Type:** E2E
**Test Level:** Espresso
**Source:** REQ-DISPLAY-01, TSPEC §7.5 — T-6-02

**Property:** No mode navigation element (bottom navigation bar, tab bar, menu item, FAB) is present in the Phase 1 UI.

**Given:** `CompassActivity` launched
**When:** The UI is rendered
**Then:** `onView(withId(R.id.bottom_navigation)).check(doesNotExist())` and no menu item with text matching "Luopan" or "Sighting" is visible

---

### PROP-DISPLAY-05: Interference warning is user-dismissible
**Type:** E2E
**Test Level:** Espresso
**Source:** REQ-DETECT-03, TSPEC §7 — T-6-01

**Property:** When the interference warning banner is visible, a user tap on it dismisses it immediately. The banner does not reappear until interference clears and re-triggers. Once cleared naturally (state returns to CLEAR), the dismissal flag resets so the banner can show again on the next interference event.

**Given:** `CompassActivity` receiving `InterferenceState.WARNING`; warning banner visible
**When:** User taps the interference warning banner
**Then:** `onView(withId(R.id.interference_banner)).check(matches(not(isDisplayed())))` (hidden after tap)

---

### PROP-DISPLAY-06: Interference overlay appears within 2 seconds of WARNING trigger
**Type:** Integration
**Test Level:** Instrumented
**Source:** REQ §9 Scenario D, TSPEC §7

**Property:** After `InterferenceState` transitions to WARNING, the interference overlay is visible in the UI within 2 seconds.

**Given:** `CompassViewModel` with `interferenceState = CLEAR`
**When:** `interferenceState` is updated to `InterferenceState.WARNING`; `SystemClock.sleep(2000)` elapses
**Then:** `uiState.value.interference_state == InterferenceState.WARNING` and the corresponding UI overlay is displayed within 2000 ms

---

### PROP-DISPLAY-07: Heading continues to update during interference (not frozen)
**Type:** Integration
**Test Level:** Instrumented
**Source:** REQ-DETECT-03, REQ §9 Scenario D

**Property:** While `InterferenceState.WARNING` is active, the heading display continues to update — it is NOT zeroed, frozen, or replaced with a placeholder.

**Given:** `CompassViewModel` in WARNING state; `FusionEngine` receiving live sensor frames
**When:** Two `FusionResult` values are emitted with different `heading_deg` values while interference is WARNING
**Then:** `CompassUiState.heading_deg` changes between the two emissions; it does not remain at 0.0 or a fixed value

---

### PROP-DISPLAY-08: Calibration CTA shown on first launch (no calibration)
**Type:** E2E
**Test Level:** Espresso
**Source:** REQ §9 Scenario A, TSPEC §7.6 — T-5-05

**Property:** When the app launches with no stored calibration, the calibration CTA banner is displayed.

**Given:** `CompassActivity` launched with an empty `CalibrationRepository`
**When:** The UI is rendered
**Then:** `onView(withId(R.id.calibration_cta)).check(matches(isDisplayed()))`

---

### PROP-DISPLAY-09: Calibration CTA hidden after successful calibration
**Type:** E2E
**Test Level:** Espresso
**Source:** REQ §9 Scenario B, TSPEC §7.6

**Property:** After a calibration is saved, the CTA banner is not displayed when returning to `CompassActivity`.

**Given:** `CompassActivity` with a completed and saved `CalibrationRecord`
**When:** `CompassActivity` resumes from `CalibrationWizardActivity` with `RESULT_OK`
**Then:** `onView(withId(R.id.calibration_cta)).check(matches(not(isDisplayed())))`

---

### PROP-DISPLAY-10: Stabilizing badge shown when SensorState.STABILIZING
**Type:** Integration
**Test Level:** Instrumented
**Source:** REQ §11.7, TSPEC §2.6

**Property:** When `SensorState` is `STABILIZING`, the confidence badge area shows the "Stabilizing…" amber badge instead of the normal confidence level.

**Given:** `CompassViewModel` emitting `sensorState = SensorState.STABILIZING`
**When:** `buildUiState()` is called with the STABILIZING state
**Then:** `uiState.confidence == OverallConfidence.STABILIZING` and the UI renders the "Stabilizing…" text (localized `str_stabilizing_advisory`) in the confidence badge

---

### PROP-DISPLAY-11: Tilt indicator slot does not cause layout reflow
**Type:** Integration
**Test Level:** Instrumented
**Source:** REQ-DISPLAY-01, TSPEC §7.4

**Property:** Toggling the tilt indicator's visibility does not shift the compass rose or heading readout positions.

**Given:** `CompassActivity` with compass rose and heading readout rendered
**When:** Tilt indicator visibility is toggled from VISIBLE to GONE and back to VISIBLE
**Then:** The bounding rect of `R.id.compass_rose` and `R.id.heading_readout` is identical before and after the toggle

---

## Domain: PROP-CAL-UX — Calibration Wizard UX

### PROP-CAL-UX-01: Cancel preserves prior calibration record
**Type:** Integration
**Test Level:** Instrumented
**Source:** REQ §9 Scenario G, TSPEC §5.3 — T-5-01

**Property:** Cancelling the calibration wizard leaves the pre-existing `CalibrationRecord` unchanged in the repository.

**Given:** `CalibrationRepository` containing `record_before`; `CalibrationWizardActivity` launched
**When:** User confirms cancel; wizard returns `RESULT_CANCELED`
**Then:** `CalibrationRepository.getCurrentCalibration()` returns a record with the same `id`, `residual_rms`, and `saved_at_utc` as `record_before`

---

### PROP-CAL-UX-02: Poor quality requires two taps to accept
**Type:** E2E
**Test Level:** Espresso
**Source:** REQ §9 Scenario H, TSPEC §5 — T-5-04

**Property:** When calibration quality is POOR, the user must perform two distinct taps to save: first on "Accept", then on a confirmation dialog "Confirm".

**Given:** Calibration wizard at completion state with `CalibrationQuality.POOR`
**When:** User taps "Accept" once
**Then:** A confirmation dialog is shown; calibration is NOT yet saved; a second tap on "Confirm" is required

---

### PROP-CAL-UX-03: Done button disabled until auto-complete gate criteria met
**Type:** E2E
**Test Level:** Espresso
**Source:** REQ-CAL-02, TSPEC §4 — T-5-02

**Property:** The "Done" button in the calibration wizard is disabled until the auto-complete gate criteria are met: `sampleCount ≥ 200 AND coverage ≥ 80% on all three axes AND rolling RMS < 1.0 µT`.

**Given:** Calibration wizard active; only 100 samples collected with partial coverage (< 80% on at least one axis)
**When:** The coverage display is below the gate threshold
**Then:** `onView(withId(R.id.btn_done)).check(matches(not(isEnabled())))`

---

### PROP-CAL-UX-04: Cancel confirmation dialog prevents accidental dismissal
**Type:** E2E
**Test Level:** Espresso
**Source:** FSPEC-CAL-01, TSPEC §5 — T-5-01

**Property:** Tapping cancel in the calibration wizard shows a confirmation dialog before discarding the session.

**Given:** Calibration wizard active with in-progress rotation data
**When:** User taps the cancel/back button
**Then:** A confirmation dialog with at least two actions (confirm cancel / continue calibration) is shown; the wizard is not immediately dismissed

---

### PROP-CAL-UX-05: Confidence badge shows Poor immediately after Poor-quality save
**Type:** E2E
**Test Level:** Espresso
**Source:** REQ §9 Scenario H, REQ-DISPLAY-03 — T-5-04

**Property:** After accepting a POOR-quality calibration and `CompassActivity` receives `RESULT_OK`, the confidence badge immediately displays the "Poor" label.

**Given:** `CalibrationWizardActivity` returning `RESULT_OK` with POOR quality; `CompassActivity` as the launching activity
**When:** `RESULT_OK` is received and `CompassActivity` resumes
**Then:** `onView(withId(R.id.confidence_badge)).check(matches(withText(R.string.str_confidence_poor)))` passes within one UI update cycle

---

### PROP-CAL-UX-06: OOM kill — prior calibration readable after relaunch
**Type:** Integration
**Test Level:** Instrumented
**Source:** REQ §9 Scenario I, REQ-CAL-04 — T-5-06

**Property:** If the process is killed while the calibration wizard is active, the prior calibration record is still readable after relaunch.

**Given:** `CalibrationRepository` with `record_before`; calibration wizard active mid-session (not yet committed)
**When:** Process is killed via `ProcessPhoenix` or `Process.killProcess(android.os.Process.myPid())`; app is relaunched
**Then:** `CalibrationRepository.getCurrentCalibration()` returns `record_before` (non-null, `residual_rms.isFinite() == true`)

---

## Domain: PROP-SENSOR — Sensor Layer

### PROP-SENSOR-01: No-magnetometer → SENSOR_ERROR state and hidden compass
**Type:** E2E
**Test Level:** Espresso
**Source:** REQ §9 Scenario E, TSPEC §2 — T-6-05

**Property:** When `TYPE_MAGNETIC_FIELD_UNCALIBRATED` is unavailable, the compass rose is hidden and an error message is displayed.

**Given:** `CompassActivity` launched with a mocked `SensorManager` returning `null` for `TYPE_MAGNETIC_FIELD_UNCALIBRATED`
**When:** `onStart()` runs
**Then:** `onView(withId(R.id.compass_rose)).check(matches(not(isDisplayed())))` and `onView(withId(R.id.error_message)).check(matches(withText(R.string.str_no_magnetometer_error)))` both pass

---

### PROP-SENSOR-02: No-gyroscope → advisory banner shown
**Type:** E2E
**Test Level:** Espresso
**Source:** REQ §11.2, TSPEC §2 — T-6-06

**Property:** When `TYPE_GYROSCOPE` is unavailable, the no-gyroscope advisory banner is displayed.

**Given:** `CompassActivity` launched with a mocked `SensorManager` returning `null` for `TYPE_GYROSCOPE`
**When:** `onStart()` runs
**Then:** `onView(withId(R.id.no_gyroscope_advisory)).check(matches(isDisplayed()))`

---

### PROP-SENSOR-03: No-gyroscope caps OverallConfidence at MODERATE
**Type:** Integration
**Test Level:** JVM
**Source:** REQ §11.2, TSPEC §6.3 Step 3

**Property:** When `hasGyroscope = false`, `CompassViewModel.buildUiState()` emits confidence no higher than `OverallConfidence.MODERATE` regardless of dimension scores.

**Given:** `CompassViewModel` initialized with `hasGyroscope = false`; all sensor conditions ideal (all dimension scores GOOD)
**When:** `buildUiState(...)` is called
**Then:** `uiState.confidence == OverallConfidence.MODERATE`

---

### PROP-SENSOR-04: Stuck sensor — all-zero mag → immediate STUCK detection
**Type:** Unit
**Test Level:** JVM
**Source:** REQ §11.3, TSPEC §2.7

**Property:** When the magnetometer delivers `(0f, 0f, 0f)` — the immediate stuck-sensor detection path — `SensorState.STUCK` is emitted without waiting for the 3-second sustained window.

**Given:** `SensorStateMonitor` in NORMAL state
**When:** A `SensorEvent` arrives with `mag_x=0f, mag_y=0f, mag_z=0f`
**Then:** `SensorStateMonitor` emits `SensorState.STUCK` immediately (within the same frame)

---

### PROP-SENSOR-05: Stuck sensor — repeated non-zero values → STUCK after sustained window
**Type:** Unit
**Test Level:** JVM
**Source:** REQ §11.3, TSPEC §2.7

**Property:** When the magnetometer delivers the same non-zero value for more than 2 seconds, `SensorState.STUCK` is triggered via the sustained detection path.

**Given:** `SensorStateMonitor` with a `FakeTimeSource` at `t=0`; prior reading `(10f, 5f, -3f)`
**When:** Identical values `(10f, 5f, -3f)` arrive at `t=100ms, 200ms, ..., 2100ms`
**Then:** After `t > 2000 ms`, `SensorStateMonitor` emits `SensorState.STUCK`

---

### PROP-SENSOR-06: Power-saving advisory at < 15 Hz sustained 2 s
**Type:** Unit
**Test Level:** JVM
**Source:** TSPEC §2.5

**Property:** When the effective update rate drops below 15 Hz and remains there for ≥ 2 seconds, `SensorAdvisory.POWER_SAVING` is triggered.

**Given:** `SensorRateMonitor` with a `FakeTimeSource` at `t=0`
**When:** Frames arrive at ~12 Hz (one every 83 ms) for 2.1 seconds (≈ 26 frames)
**Then:** `SensorAdvisory.POWER_SAVING` is emitted after 2.0 s of sustained sub-15 Hz delivery

---

## Domain: PROP-PERSIST — Persistence and Privacy

### PROP-PERSIST-01: INTERNET permission absent from merged manifest
**Type:** Integration
**Test Level:** Instrumented
**Source:** REQ-NFR-05, TSPEC §9.4 — T-0-04

**Property:** The merged `AndroidManifest.xml` does not declare `android.permission.INTERNET`.

**Given:** A built APK or merged manifest
**When:** Inspected by the custom `NoInternetPermissionCheck` lint rule or programmatic manifest parsing
**Then:** `android.permission.INTERNET` is absent from all `<uses-permission>` entries

---

### PROP-PERSIST-02: Calibration database is encrypted with SQLCipher
**Type:** Integration
**Test Level:** Instrumented
**Source:** REQ-NFR-06, TSPEC §9.1 — T-3-02

**Property:** The database file is encrypted and cannot be opened as a standard SQLite database without the SQLCipher key.

**Given:** `CalibrationRepository` has saved at least one record
**When:** The database file is opened via `android.database.sqlite.SQLiteDatabase.openDatabase()` (no SQLCipher)
**Then:** The call throws or the first 16 bytes of the file do NOT match the standard SQLite magic string (`53 51 4C 69 74 65 20 66 6F 72 6D 61 74 20 33 00`)

---

### PROP-PERSIST-03: App fully functional offline
**Type:** E2E
**Test Level:** Espresso
**Source:** REQ-NFR-05, TSPEC §10.5

**Property:** The app operates fully with no network connectivity and makes no network calls.

**Given:** `CompassActivity` launched on a device/emulator with network disabled
**When:** Full compass operation, calibration, and interference detection are exercised for ≥ 60 seconds
**Then:** No `NetworkOnMainThreadException`, no `UnknownHostException`; all UI elements remain functional

---

### PROP-PERSIST-04: Timestamps stored as UTC epoch milliseconds
**Type:** Unit
**Test Level:** JVM
**Source:** TSPEC §9.2

**Property:** `CalibrationRecord.saved_at_utc` is a `Long` representing UTC epoch milliseconds. Its value is invariant to device timezone changes.

**Given:** A `CalibrationRecord` saved with a known UTC timestamp `T`
**When:** The device timezone is changed to a different zone and the record is read back
**Then:** `record.saved_at_utc == T` (unchanged); `Instant.ofEpochMilli(record.saved_at_utc)` produces the correct UTC instant

---

## Domain: PROP-NFR — Non-Functional Requirements

### PROP-NFR-01: Sensor-to-UI latency p95 ≤ 50 ms
**Type:** Integration
**Test Level:** Instrumented
**Source:** REQ-NFR-01, TSPEC §10.1

**Property:** The 95th-percentile latency from `SensorEvent` arrival to `CompassUiState` emission by `CompassViewModel` is ≤ 50 ms.

**Given:** `CompassViewModel` wired to a `FakeSensorLayer` injecting `SensorFrame` events at 50 Hz
**When:** 500 frames are injected and latency is measured per frame
**Then:** `percentile(latencies, 95) <= 50_000_000L` nanoseconds (50 ms)

---

### PROP-NFR-02: Compass update rate ≥ 20 Hz under normal conditions
**Type:** Integration
**Test Level:** Instrumented
**Source:** REQ-NFR-02, TSPEC §2.5

**Property:** Under normal conditions, `CompassUiState.heading_deg` is updated at ≥ 20 Hz.

**Given:** `CompassActivity` running on the Pixel 4a API 34 emulator; sensor delivering events at `SENSOR_DELAY_GAME`
**When:** Heading changes are observed over a 5-second window
**Then:** At least 100 distinct heading updates are emitted (≥ 20 Hz average)

---

### PROP-NFR-03: Battery consumption ≤ 5%/hour (pre-release gate)
**Type:** E2E
**Test Level:** Manual gate (Battery Historian)
**Source:** REQ-NFR-03, TSPEC §10.3 — T-6-09

**Property:** Battery Historian `--report` on a 4000 mAh reference device at 100% screen brightness shows app wakelock attribution < 5% of 1-hour energy draw.

**Given:** Reference device at 100% brightness running only the app for 1 hour
**When:** Battery Historian report is generated from a `bugreport` captured at start and end
**Then:** App wakelock attribution ≤ 5% of total energy draw; report artifact committed to `docs/qa/battery-historian-p1.html`

**Note:** This is a manual gate. CI verification (T-6-09) checks that the report file exists and is committed; the ≤ 5% assertion is verified by a human reviewer.

---

### PROP-NFR-04: Release APK size ≤ 15 MB
**Type:** Integration
**Test Level:** Build check
**Source:** REQ-NFR-07, TSPEC §10 — T-0-03

**Property:** The release APK is ≤ 15 MB.

**Given:** `./gradlew assembleRelease` completes
**When:** APK size is measured
**Then:** `apk.length() <= 15_728_640L` bytes (15 MB)

---

### PROP-NFR-05: No crashes in automated test suite
**Type:** Integration / E2E
**Test Level:** All
**Source:** REQ-NFR-01

**Property:** The full automated test suite completes with 0 unexpected crashes.

**Given:** Full suite run via `./gradlew test connectedAndroidTest`
**When:** All `@Test` methods execute
**Then:** No test fails with `Process crashed` or `FATAL EXCEPTION`; all methods pass or fail with assertion errors only (not process crashes)

---

## Domain: PROP-L10N — Localization

### PROP-L10N-01: App starts without crash in all four locales
**Type:** E2E
**Test Level:** Espresso
**Source:** REQ-L10N-01, TSPEC §8.1 — T-4-05

**Property:** `CompassActivity` launches without crash or `Resources.NotFoundException` in all four supported locales.

**Given:** `ActivityScenario.launch(CompassActivity::class.java)` with locale forced to each of `Locale("en")`, `Locale("zh", "TW")`, `Locale("zh", "CN")`, `Locale("ja")`
**When:** The activity fully renders
**Then:** No crash; activity is in `RESUMED` state for each locale

---

### PROP-L10N-02: No English strings visible when locale is Japanese
**Type:** E2E
**Test Level:** Espresso
**Source:** REQ §9 Scenario F, REQ-L10N-01

**Property:** When the system locale is `ja`, all visible UI text uses Japanese string resources; no English fallback strings are visible.

**Given:** `CompassActivity` launched with `Locale("ja")`; all UI states exercised (calibrated, uncalibrated, interference, no-gyroscope)
**When:** Each state is rendered
**Then:** Confidence badge, interference warning, calibration CTA, and advisory banners contain Japanese characters (U+3040–U+30FF or U+4E00–U+9FFF) and do NOT match the English-locale string values for those IDs

---

### PROP-L10N-03: zh-HK and zh-MO use zh-Hant (Traditional Chinese) resources
**Type:** Integration
**Test Level:** Instrumented
**Source:** REQ-L10N-03, TSPEC §8.1

**Property:** `getString(R.string.str_confidence_high)` returns the Traditional Chinese value when locale is `zh-HK` or `zh-MO`.

**Given:** Application context with `Locale("zh", "HK")` and separately `Locale("zh", "MO")`
**When:** `resources.getString(R.string.str_confidence_high)` is called
**Then:** Returned string matches the `res/values-zh-rTW/strings.xml` value (Traditional Chinese), not the Simplified Chinese value from `res/values-zh-rCN/strings.xml`

---

### PROP-L10N-04: Generic zh locale uses Simplified Chinese resources
**Type:** Integration
**Test Level:** Instrumented
**Source:** REQ-L10N-03, TSPEC §8.1

**Property:** When locale is generic `zh` (no region qualifier), the app uses Simplified Chinese (`zh-Hans`) resources.

**Given:** Application context with `Locale("zh")`
**When:** `resources.getString(R.string.str_confidence_high)` is called
**Then:** Returned string matches the `res/values-zh-rCN/strings.xml` value (Simplified Chinese)

---

## Summary Table

| ID | Name | Type | Level | Source |
|----|------|------|-------|--------|
| PROP-FUSION-01 | Heading always in [0, 360) | Unit | JVM | REQ-SENSOR-04 |
| PROP-FUSION-02 | Heading is never NaN or Infinite | Unit | JVM | REQ-SENSOR-04 |
| PROP-FUSION-03 | Quaternion normalized after every update | Unit | JVM | TSPEC §3.1 |
| PROP-FUSION-04 | Full update converges to North | Unit | JVM | TSPEC §3.2 |
| PROP-FUSION-05 | No-gyro mode converges to North | Unit | JVM | TSPEC §3.2 |
| PROP-FUSION-06 | CI proxy — 30 s sequence ±0.5° | Integration | JVM | REQ §9 Scenario C |
| PROP-CAL-01 | Coverage zero with no-range samples | Unit | JVM | REQ-CAL-02 |
| PROP-CAL-02 | Coverage 1.0 on dominant axis | Unit | JVM | REQ-CAL-02 |
| PROP-CAL-03 | Residual ≤ 1.0 µT → GOOD | Unit | JVM | TSPEC §4.4 |
| PROP-CAL-04 | Residual exactly 1.0 µT → GOOD | Unit | JVM | REQ §5.3 TE-04 |
| PROP-CAL-05 | Residual above 1.0 µT → FAIR | Unit | JVM | REQ §5.3 TE-04 |
| PROP-CAL-06 | Residual exactly 2.0 µT → FAIR | Unit | JVM | REQ §5.3 TE-04 |
| PROP-CAL-07 | Residual above 2.0 µT → POOR | Unit | JVM | REQ §5.3 TE-04 |
| PROP-CAL-08 | Atomic write — OOM leaves DB readable | Integration | Instrumented | REQ-CAL-04 |
| PROP-CAL-09 | Rollback slot — third save evicts oldest | Unit | JVM | REQ-CAL-04 |
| PROP-CAL-10 | Age computed in UTC days | Unit | JVM | TSPEC §9.2 |
| PROP-DETECT-01 | Field 0.249f — MODERATE (not WARNING) | Unit | JVM | TSPEC §5.4 |
| PROP-DETECT-02 | Field 0.25f → WARNING | Unit | JVM | REQ §5.3 TE-11 |
| PROP-DETECT-03 | Field 0.251f → WARNING | Unit | JVM | REQ §5.3 TE-11 |
| PROP-DETECT-04 | Field below 15% for 3 s → CLEAR | Unit | JVM | REQ §5.3 TE-11 |
| PROP-DETECT-05 | Field 15.0% resets timer → MODERATE | Unit | JVM | TSPEC §5.4 |
| PROP-DETECT-06 | Timer not elapsed → stays WARNING | Unit | JVM | TSPEC §5.4 |
| PROP-DETECT-07 | Timer resets on mid-window excursion | Unit | JVM | REQ §5.3 TE-11 |
| PROP-DETECT-08 | Inclination < 3° — clearance zone | Unit | JVM | TSPEC §5.4 |
| PROP-DETECT-09 | Inclination 3.0° → MODERATE | Unit | JVM | TSPEC §5.4 |
| PROP-DETECT-10 | Inclination 7.9° → MODERATE | Unit | JVM | TSPEC §5.4 |
| PROP-DETECT-11 | Inclination 8.0° → WARNING | Unit | JVM | TSPEC §5.4 |
| PROP-DETECT-12 | Spike rejection — no WARNING trigger | Unit | JVM | REQ-DETECT-04 |
| PROP-DETECT-13 | Spike filter boundary (`>` strict) | Unit | JVM | TSPEC §5.2 |
| PROP-CONF-01 | Confidence = min of five dimensions | Unit | JVM | REQ §5.5 |
| PROP-CONF-02 | Tilt ≤ 5.0° — no clamp | Unit | JVM | REQ §5.5 |
| PROP-CONF-03 | Tilt 5.1° → MODERATE clamp | Unit | JVM | REQ §5.5 |
| PROP-CONF-04 | Tilt 20.0° → MODERATE clamp (not POOR) | Unit | JVM | TSPEC §6.3 |
| PROP-CONF-05 | Tilt 20.1° → POOR clamp | Unit | JVM | TSPEC §6.3 |
| PROP-CONF-06 | No-gyroscope caps at MODERATE | Unit | JVM | REQ §11.2 |
| PROP-CONF-07 | STUCK bypasses → SENSOR_ERROR | Unit | JVM | REQ §11.3 |
| PROP-CONF-08 | STABILIZING bypasses scoring | Unit | JVM | REQ §11.7 |
| PROP-CONF-09 | Evaluation order — 1.0 µT → GOOD | Unit | JVM | TSPEC §6.1 |
| PROP-CONF-10 | Noise variance 0.1 µT² → GOOD | Unit | JVM | TSPEC §6.1 |
| PROP-CONF-11 | Noise variance 0.101 µT² → MODERATE | Unit | JVM | TSPEC §6.1 |
| PROP-CONF-12 | Noise variance 0.5 µT² → MODERATE | Unit | JVM | TSPEC §6.1 |
| PROP-CONF-13 | Noise variance above 0.5 µT² → POOR | Unit | JVM | TSPEC §6.1 |
| PROP-CONF-14 | Cal age 7 days → GOOD | Unit | JVM | TSPEC §6.1 |
| PROP-CONF-15 | Cal age just above 7 days → MODERATE | Unit | JVM | TSPEC §6.1 |
| PROP-CONF-16 | Cal age 30 days → MODERATE | Unit | JVM | TSPEC §6.1 |
| PROP-CONF-17 | Cal age above 30 days → POOR | Unit | JVM | TSPEC §6.1 |
| PROP-DISPLAY-01 | All required UI slots present | E2E | Espresso | REQ-DISPLAY-01 |
| PROP-DISPLAY-02 | Wake lock flag set in onStart | Integration | Instrumented | REQ-DISPLAY-10 |
| PROP-DISPLAY-03 | Wake lock flag cleared in onStop | Integration | Instrumented | REQ-DISPLAY-10 |
| PROP-DISPLAY-04 | Mode switcher absent | E2E | Espresso | REQ-DISPLAY-01 |
| PROP-DISPLAY-05 | Interference warning user-dismissible | E2E | Espresso | REQ-DETECT-03 |
| PROP-DISPLAY-06 | Interference overlay appears within 2 s | Integration | Instrumented | REQ §9 Scenario D |
| PROP-DISPLAY-07 | Heading continues during interference | Integration | Instrumented | REQ-DETECT-03 |
| PROP-DISPLAY-08 | CTA shown on first launch | E2E | Espresso | REQ §9 Scenario A |
| PROP-DISPLAY-09 | CTA hidden after calibration | E2E | Espresso | REQ §9 Scenario B |
| PROP-DISPLAY-10 | Stabilizing badge shown when STABILIZING | Integration | Instrumented | REQ §11.7 |
| PROP-DISPLAY-11 | Tilt slot no layout reflow | Integration | Instrumented | REQ-DISPLAY-01 |
| PROP-CAL-UX-01 | Cancel preserves prior calibration | Integration | Instrumented | REQ §9 Scenario G |
| PROP-CAL-UX-02 | Poor quality requires two taps | E2E | Espresso | REQ §9 Scenario H |
| PROP-CAL-UX-03 | Done button disabled until gate met | E2E | Espresso | REQ-CAL-02 |
| PROP-CAL-UX-04 | Cancel confirmation dialog | E2E | Espresso | FSPEC-CAL-01 |
| PROP-CAL-UX-05 | Confidence badge shows Poor immediately | E2E | Espresso | REQ §9 Scenario H |
| PROP-CAL-UX-06 | OOM kill — prior calibration readable | Integration | Instrumented | REQ §9 Scenario I |
| PROP-SENSOR-01 | No-mag → SENSOR_ERROR + hidden rose | E2E | Espresso | REQ §9 Scenario E |
| PROP-SENSOR-02 | No-gyroscope → advisory banner | E2E | Espresso | REQ §11.2 |
| PROP-SENSOR-03 | No-gyroscope caps at MODERATE | Integration | JVM | REQ §11.2 |
| PROP-SENSOR-04 | All-zero mag → immediate STUCK | Unit | JVM | TSPEC §2.7 |
| PROP-SENSOR-05 | Repeated non-zero → STUCK after 2 s | Unit | JVM | TSPEC §2.7 |
| PROP-SENSOR-06 | Power-saving advisory at < 15 Hz | Unit | JVM | TSPEC §2.5 |
| PROP-PERSIST-01 | INTERNET permission absent | Integration | Instrumented | REQ-NFR-05 |
| PROP-PERSIST-02 | Calibration DB encrypted | Integration | Instrumented | REQ-NFR-06 |
| PROP-PERSIST-03 | App fully functional offline | E2E | Espresso | REQ-NFR-05 |
| PROP-PERSIST-04 | Timestamps as UTC epoch ms | Unit | JVM | TSPEC §9.2 |
| PROP-NFR-01 | Latency p95 ≤ 50 ms | Integration | Instrumented | REQ-NFR-01 |
| PROP-NFR-02 | Update rate ≥ 20 Hz | Integration | Instrumented | REQ-NFR-02 |
| PROP-NFR-03 | Battery ≤ 5%/hour (manual gate) | Manual | Battery Historian | REQ-NFR-03 |
| PROP-NFR-04 | APK ≤ 15 MB | Integration | Build check | REQ-NFR-07 |
| PROP-NFR-05 | No crashes in test suite | E2E | All | REQ-NFR-01 |
| PROP-L10N-01 | App starts in all four locales | E2E | Espresso | REQ-L10N-01 |
| PROP-L10N-02 | No English strings in Japanese locale | E2E | Espresso | REQ §9 Scenario F |
| PROP-L10N-03 | zh-HK/MO → zh-Hant resources | Integration | Instrumented | REQ-L10N-03 |
| PROP-L10N-04 | Generic zh → zh-Hans resources | Integration | Instrumented | REQ-L10N-03 |

**Total properties: 76**
- Unit (JVM): 39
- Integration (Instrumented/JVM): 22
- E2E (Espresso): 14
- Manual gate: 1
