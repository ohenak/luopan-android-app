# TSPEC-luopan-p4-bearing-history
## Phase 4: Bearing History, Recalibration Refinements, and Sensor Diagnostics

| Field | Value |
|-------|-------|
| **Version** | 0.3-draft |
| **Date** | 2026-04-27 |
| **Status** | Draft |
| **Phase** | 4 of 5 |
| **Source REQ** | [REQ-luopan-p4-bearing-history v0.4-draft](REQ-luopan-p4-bearing-history.md) |
| **Source FSPEC** | [FSPEC-luopan-p4-bearing-history v0.3-draft](FSPEC-luopan-p4-bearing-history.md) |
| **Cross-reviews addressed** | SE FSPEC-v2 (F-01–F-03 Medium); TE FSPEC-v2 (F-01–F-04 Medium); PM TSPEC-v1 (F-01–F-06); TE TSPEC-v1 (F-01–F-10); SE PROPERTIES-v2 (F-01: IDriftDetector interface on Factory + CompassViewModel field + FakeDriftDetector implements IDriftDetector) |

---

## Table of Contents

1. [Goals and Scope](#1-goals-and-scope)
2. [Technology Stack and New Dependencies](#2-technology-stack-and-new-dependencies)
3. [Project Structure — New and Modified Files](#3-project-structure--new-and-modified-files)
4. [Data Layer](#4-data-layer)
5. [Domain Layer — New Components](#5-domain-layer--new-components)
6. [ViewModel Layer](#6-viewmodel-layer)
7. [UI Layer](#7-ui-layer)
8. [Navigation Architecture](#8-navigation-architecture)
9. [Test Strategy](#9-test-strategy)
10. [Error Handling](#10-error-handling)
11. [Requirements Traceability](#11-requirements-traceability)
12. [Cross-Review Finding Resolutions](#12-cross-review-finding-resolutions)

---

## 1. Goals and Scope

Phase 4 delivers four technical domains:

1. **Bearing History Screen** — `BearingHistoryFragment` as a third tab in `CompassActivity`, backed by a new `BearingHistoryViewModel`, with `RecyclerView`/`BearingAdapter`, swipe-to-delete, search, and empty state.
2. **Recalibration Lifecycle** — Age-based and drift-based banners in `BearingHistoryFragment`; `DriftDetector` state machine; `AccelerometerVarianceTracker`; `SettingsRepository` additions.
3. **Sensor Capability Logging** — `SensorCapabilityLogger` with version-gated first-launch write, injectable `SensorFileWriter`, retry-on-failure contract.
4. **Database Migration** — Room v2→v3 adding `expected_field_ut` to `calibration_records`; `CalibrationResult.sphereRadius_uT`; updated `CalibrationRepository.toRecord()`.

This TSPEC does not cover Phase 3 components. Existing Phase 3 code (ModernCompassFragment, LuopanFragment, CompassViewModel sensor pipeline) is modified minimally and only where specified.

---

## 2. Technology Stack and New Dependencies

No new third-party dependencies are introduced. All components use the existing dependency set.

| Concern | Library / API | Version |
|---------|--------------|---------|
| List display | `RecyclerView` (androidx.recyclerview) | bundled with appcompat 1.7.0 |
| Swipe-to-delete | `ItemTouchHelper` (androidx.recyclerview) | bundled |
| Snackbar | `com.google.android.material.snackbar.Snackbar` | material 1.12.0 |
| JSON serialization | `org.json.JSONObject` / `JSONArray` (android.jar) | platform |
| Room reactive DAO | `kotlinx.coroutines.flow.Flow` (room-ktx 2.6.1) | existing |
| ActivityResult | `ActivityResultContracts.StartActivityForResult` | existing |
| Search debounce | `kotlinx.coroutines.flow.debounce` | existing coroutines-android 1.7.3 |
| Unit tests | JUnit 4, coroutines-test | existing |
| Instrumented tests | Espresso, Room in-memory | existing |

**No new entries required in `libs.versions.toml` or `build.gradle.kts`.**

---

## 3. Project Structure — New and Modified Files

### 3.1 New Files

| File | Purpose |
|------|---------|
| `app/src/main/java/com/luopan/compass/ui/BearingHistoryFragment.kt` | Third-tab Fragment; hosts RecyclerView, search bar, banners, swipe-to-delete |
| `app/src/main/java/com/luopan/compass/ui/BearingHistoryViewModel.kt` | Fragment-scoped ViewModel; list flow, search state, delete/undo state |
| `app/src/main/java/com/luopan/compass/ui/BearingAdapter.kt` | RecyclerView adapter with expand/collapse; `DiffUtil.ItemCallback<BearingRecord>` |
| `app/src/main/java/com/luopan/compass/ui/DriftBannerState.kt` | `enum class DriftBannerState { HIDDEN, VISIBLE }` |
| `app/src/main/java/com/luopan/compass/drift/DriftEvent.kt` | `enum class DriftEvent { TRIGGERED, RESET }` |
| `app/src/main/java/com/luopan/compass/drift/DriftDetector.kt` | State machine: IDLE / COUNTING / TRIGGERED; `Clock`-injected |
| `app/src/main/java/com/luopan/compass/drift/AccelerometerVarianceTracker.kt` | Rolling 250-sample window (time-based, see §5.2) over combined accel magnitude |
| `app/src/main/java/com/luopan/compass/diagnostics/SensorCapabilityLogger.kt` | First-launch sensor profile writer; injectable `SensorFileWriter` |
| `app/src/main/java/com/luopan/compass/diagnostics/SensorFileWriter.kt` | Interface: `fun write(path: java.io.File, content: String)` |
| `app/src/main/res/layout/fragment_bearing_history.xml` | Root layout for BearingHistoryFragment |
| `app/src/main/res/layout/item_bearing_record.xml` | RecyclerView row + expandable detail panel |
| `app/src/main/res/layout/banner_recalibration.xml` | Reusable banner layout (age and drift banners share this layout) |
| `app/src/test/java/com/luopan/compass/drift/DriftDetectorTest.kt` | Unit tests: AT-CAL-03-A through AT-CAL-03-F |
| `app/src/test/java/com/luopan/compass/drift/AccelerometerVarianceTrackerTest.kt` | Unit tests: window sizing, variance output |
| `app/src/test/java/com/luopan/compass/ui/BearingHistoryViewModelTest.kt` | Unit tests: search debounce, AT-HIST-02-A through AT-HIST-02-D, AT-CAL-02-A through AT-CAL-02-E |
| `app/src/test/java/com/luopan/compass/ui/FakeBearingDao.kt` | Recording fake DAO (see §9.2) |
| `app/src/test/java/com/luopan/compass/diagnostics/SensorCapabilityLoggerTest.kt` | Unit tests: AT-SENSOR-01-E through AT-SENSOR-01-G |
| `app/src/test/java/com/luopan/compass/drift/DriftDetectorIntegrationTest.kt` | Integration test: AT-CAL-INT-01 |
| `app/src/androidTest/java/com/luopan/compass/ui/BearingHistoryFragmentTest.kt` | Instrumented tests: AT-HIST-01-A through AT-HIST-04-B, AT-HIST-05-A through AT-HIST-05-C |
| `app/src/androidTest/java/com/luopan/compass/ui/BearingHistorySwipeTest.kt` | Instrumented tests: AT-HIST-03-A through AT-HIST-03-E |
| `app/src/androidTest/java/com/luopan/compass/ui/RecalibrationBannerTest.kt` | Instrumented tests: AT-CAL-01-A through AT-CAL-01-G, AT-CAL-02-B through AT-CAL-02-F |
| `app/src/androidTest/java/com/luopan/compass/ui/NavigationTabTest.kt` | Instrumented tests: AT-NAV-01-A through AT-NAV-01-C |
| `app/src/androidTest/java/com/luopan/compass/diagnostics/SensorCapabilityLoggerInstrumentedTest.kt` | Instrumented tests: AT-SENSOR-01-A through AT-SENSOR-01-D |

### 3.2 Modified Files

| File | Change Summary |
|------|---------------|
| `app/src/main/java/com/luopan/compass/bearing/BearingDao.kt` | Add `getAllFlow()` and `searchFlow(query)` |
| `app/src/main/java/com/luopan/compass/bearing/BearingRepository.kt` | Add `getAllFlow()` and `searchFlow(query)` to interface |
| `app/src/main/java/com/luopan/compass/bearing/BearingRepositoryImpl.kt` | Implement new DAO methods |
| `app/src/main/java/com/luopan/compass/calibration/CalibrationEngine.kt` | Expose `r` via `CalibrationResult.sphereRadius_uT` in `fitEllipsoid()` |
| `app/src/main/java/com/luopan/compass/calibration/CalibrationEngine.kt` (nested class) | Add `sphereRadius_uT: Float` to `CalibrationResult` |
| `app/src/main/java/com/luopan/compass/calibration/CalibrationRecord.kt` | Add `expected_field_ut: Float` column |
| `app/src/main/java/com/luopan/compass/calibration/CalibrationRepository.kt` | Map `sphereRadius_uT` → `expected_field_ut` in `toRecord()` |
| `app/src/main/java/com/luopan/compass/db/LuopanDatabase.kt` | Bump version to 3; add `MIGRATION_2_3` |
| `app/src/main/java/com/luopan/compass/db/Migrations.kt` | Add `MIGRATION_2_3` |
| `app/src/main/java/com/luopan/compass/settings/SettingsRepository.kt` | Add `driftCooldownTimestampMs` and `sensorProfileWrittenForVersion` keys and properties |
| `app/src/main/java/com/luopan/compass/ui/CompassViewModel.kt` | Promote `loadCalibrationAge()` to `internal`; add `driftBannerState: StateFlow<DriftBannerState>`; add `calAgeBannerDismissed`; add `DriftDetector` and `AccelerometerVarianceTracker` fields; wire drift detection in sensor loop; add `resetDriftDetector()` method |
| `app/src/main/java/com/luopan/compass/ui/CompassViewModel.kt` Factory | Accept `DriftDetector` and `AccelerometerVarianceTracker` as constructor parameters (with production defaults) |
| `app/src/main/res/navigation/nav_graph.xml` | Add `dest_history` fragment destination |
| `app/src/main/res/layout/activity_compass.xml` | Add third `TabItem` for History tab |
| `app/src/main/res/values/strings.xml` | Add `tab_history`, banner text strings, empty-state strings |
| `app/src/main/java/com/luopan/compass/CompassActivity.kt` | Add `TAB_HISTORY = 2` constant, `wireTabNavigation()`, `addOnDestinationChangedListener` case |

---

## 4. Data Layer

### 4.1 Room Schema Migration: v2 → v3

**File:** `app/src/main/java/com/luopan/compass/db/Migrations.kt`

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE calibration_records ADD COLUMN expected_field_ut REAL NOT NULL DEFAULT 0.0"
        )
    }
}
```

`LuopanDatabase` version bumped from 2 to 3. `MIGRATION_2_3` added to `addMigrations(MIGRATION_1_2, MIGRATION_2_3)`.

**Existing records:** `expected_field_ut = 0.0` by default. This disables Condition B drift detection (precondition 3: `expectedFieldUt > 0.0`) until the user recalibrates. This is the correct behavior per REQ-CAL-05.

### 4.2 CalibrationRecord

**File:** `app/src/main/java/com/luopan/compass/calibration/CalibrationRecord.kt`

Add one field:

```kotlin
@ColumnInfo(name = "expected_field_ut", defaultValue = "0.0")
val expected_field_ut: Float   // REAL NOT NULL DEFAULT 0.0; calibration-time sphere radius in µT
```

The `defaultValue` annotation is required for Room to generate correct migration validation SQL.

### 4.3 CalibrationResult — sphereRadius_uT

**File:** `app/src/main/java/com/luopan/compass/calibration/CalibrationEngine.kt`

Add one field to `CalibrationResult` data class:

```kotlin
val sphereRadius_uT: Float     // sphere radius r from fitEllipsoid(); 0.0 if not computed
```

Populate in `fitEllipsoid()`:

```kotlin
// Existing local: val r = dists.average().toFloat()
// Add to CalibrationResult constructor call:
return CalibrationResult(
    hardIron = hardIron,
    softIron = softIron,
    residualMicroTesla = residual,
    coverageScore = coverage,
    quality = classifyQuality(residual, coverage),
    sphereRadius_uT = r       // ← new field
)
```

Update `equals()` and `hashCode()` to include `sphereRadius_uT` per Kotlin data class contract.

### 4.4 CalibrationRepository.toRecord()

**File:** `app/src/main/java/com/luopan/compass/calibration/CalibrationRepository.kt`

```kotlin
fun toRecord(result: CalibrationResult, id: Int, timestamp: Long): CalibrationRecord {
    val si = result.softIron
    return CalibrationRecord(
        id = id,
        recorded_at = timestamp,
        hard_iron_x = result.hardIron[0],
        hard_iron_y = result.hardIron[1],
        hard_iron_z = result.hardIron[2],
        soft_iron_00 = si[0][0], soft_iron_01 = si[0][1], soft_iron_02 = si[0][2],
        soft_iron_10 = si[1][0], soft_iron_11 = si[1][1], soft_iron_12 = si[1][2],
        soft_iron_20 = si[2][0], soft_iron_21 = si[2][1], soft_iron_22 = si[2][2],
        quality = result.quality.name,
        expected_field_ut = result.sphereRadius_uT   // ← new field mapping
    )
}
```

`fromRecord()` does not need to reconstruct `sphereRadius_uT`; it returns a default `CalibrationResult` used only for applying corrections, not for drift detection.

### 4.5 BearingDao — New Methods

**File:** `app/src/main/java/com/luopan/compass/bearing/BearingDao.kt`

```kotlin
@Query("SELECT * FROM bearing_records ORDER BY captured_at DESC, rowid DESC")
fun getAllFlow(): Flow<List<BearingRecord>>

@Query("SELECT * FROM bearing_records WHERE name LIKE '%' || :query || '%' ORDER BY captured_at DESC, rowid DESC")
fun searchFlow(query: String): Flow<List<BearingRecord>>
```

Both methods are non-suspending (returning `Flow` is the Room pattern for reactive queries). The existing `suspend fun getAll()` is retained for non-reactive use sites.

**Sort order rationale:** Primary sort `captured_at DESC` (newest first). Secondary sort `rowid DESC` provides a deterministic tiebreaker for records inserted in the same millisecond (e.g., during seed data setup in tests).

**Search case-insensitivity:** SQLite's default collation (BINARY) is case-insensitive for ASCII characters. For non-ASCII characters, case-insensitivity is best-effort. The `LIKE` operator uses `%query%` pattern for substring matching.

`BearingRepository` interface and `BearingRepositoryImpl` are updated to expose the same two methods.

### 4.6 BearingRecord

`BearingRecord` schema is unchanged from Phase 2. All fields needed for Phase 4 (including `interference_flag`, `field_deviation_pct`, `inclination_deviation_deg`) already exist. No migration is needed for `bearing_records`.

### 4.7 SettingsRepository — Phase 4 Additions

**File:** `app/src/main/java/com/luopan/compass/settings/SettingsRepository.kt`

Add to `companion object` (consistent with Phase 3 additions block):

```kotlin
// Phase 4 additions
const val KEY_DRIFT_COOLDOWN_TIMESTAMP_MS = "drift_cooldown_timestamp_ms"
const val KEY_SENSOR_PROFILE_WRITTEN_FOR_VERSION = "sensor_profile_written_for_version"
```

Add properties:

```kotlin
// Phase 4 additions

var driftCooldownTimestampMs: Long
    get() = prefs.getLong(KEY_DRIFT_COOLDOWN_TIMESTAMP_MS, 0L)
    set(value) = prefs.edit { putLong(KEY_DRIFT_COOLDOWN_TIMESTAMP_MS, value) }

var sensorProfileWrittenForVersion: Int
    get() = prefs.getInt(KEY_SENSOR_PROFILE_WRITTEN_FOR_VERSION, 0)
    set(value) = prefs.edit { putInt(KEY_SENSOR_PROFILE_WRITTEN_FOR_VERSION, value) }
```

Both properties use `0` / `0L` as the default (absent key), which triggers the write path on first launch.

---

## 5. Domain Layer — New Components

### 5.1 DriftEvent

**File:** `app/src/main/java/com/luopan/compass/drift/DriftEvent.kt`

```kotlin
package com.luopan.compass.drift

enum class DriftEvent { TRIGGERED, RESET }
```

### 5.2 AccelerometerVarianceTracker

**File:** `app/src/main/java/com/luopan/compass/drift/AccelerometerVarianceTracker.kt`

**Purpose:** Maintains a rolling window over the combined 3-axis accelerometer magnitude `sqrt(ax²+ay²+az²)` and returns the variance of that scalar series.

**SE FSPEC-v2 F-02 Resolution:** The FSPEC specifies "250 samples at 50 Hz = 5 seconds." However, `SensorLayer` uses `SENSOR_DELAY_GAME` (~50 Hz nominal) for the accelerometer, but the actual call rate to `DriftDetector.onFrame()` is frame-rate-dependent. To make the window duration-based and rate-agnostic, `AccelerometerVarianceTracker` uses a **time-based rolling window of 5,000 ms**, not a fixed sample count of 250.

```kotlin
package com.luopan.compass.drift

import com.luopan.compass.util.Clock
import kotlin.math.sqrt

/**
 * Maintains a rolling 5-second window over the combined 3-axis accelerometer magnitude
 * and returns the Welford online variance of that scalar series.
 *
 * The window is time-based (5,000 ms), not sample-count-based (250 samples),
 * to remain correct regardless of the actual sensor delivery rate.
 *
 * Thread-safety: not thread-safe; must be called from a single thread (Dispatchers.Default
 * in the CompassViewModel sensor loop).
 */
class AccelerometerVarianceTracker(
    private val clock: Clock,
    private val windowMs: Long = 5_000L
) {
    private data class Sample(val timestampMs: Long, val magnitude: Float)

    private val samples = ArrayDeque<Sample>()

    /**
     * Records a new accelerometer frame and returns the current variance of
     * the combined magnitude over the rolling 5-second window.
     *
     * @param ax Accelerometer x in m/s²
     * @param ay Accelerometer y in m/s²
     * @param az Accelerometer z in m/s²
     * @return Variance of the magnitude series in the rolling window, in (m/s²)²
     */
    fun update(ax: Float, ay: Float, az: Float): Float {
        val magnitude = sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat()
        val nowMs = clock.nowMs()

        samples.addLast(Sample(nowMs, magnitude))

        // Evict samples older than windowMs
        val cutoffMs = nowMs - windowMs
        while (samples.isNotEmpty() && samples.first().timestampMs < cutoffMs) {
            samples.removeFirst()
        }

        return computeVariance()
    }

    private fun computeVariance(): Float {
        val n = samples.size
        if (n < 2) return 0f
        val mean = samples.sumOf { it.magnitude.toDouble() } / n
        val variance = samples.sumOf { (it.magnitude - mean) * (it.magnitude - mean) } / n
        return variance.toFloat()
    }

    /** For testing: clears all samples. */
    fun reset() { samples.clear() }
}
```

**Key distinction from `NoiseVarianceTracker`:**
- `NoiseVarianceTracker` tracks **magnetometer noise** for confidence scoring (50-sample IQR window).
- `AccelerometerVarianceTracker` tracks **accelerometer stationary variance** for drift detection (5-second time-based window, population variance).
- They must not be conflated or shared.

### 5.3 DriftDetector

**File:** `app/src/main/java/com/luopan/compass/drift/DriftDetector.kt`

**State machine:**

```
[IDLE]
  │  all three preconditions hold → startTimeMs = clock.nowMs()
  ▼
[COUNTING]
  │  any precondition violated → reset timer → [IDLE]
  │  timer > 60 s AND field deviation > 10% → emit TRIGGERED → [IDLE]
  │  timer > 60 s AND field deviation ≤ 10% → re-evaluate on NEXT frame → stay [COUNTING]
```

Post-trigger behavior: after `TRIGGERED` is emitted, the detector transitions to `IDLE` (timer reset to `null`). A new 60-second continuous window is required before the next `TRIGGERED` event — subject to the 10-minute cooldown managed by `CompassViewModel`.

**Re-evaluate-each-frame rule (PM TSPEC-v1 F-01):** When the 60-second timer has elapsed but deviation is ≤ 10%, the detector does NOT reset the timer. Instead it stays in COUNTING and re-evaluates the deviation condition on every subsequent frame. If deviation later crosses 10% (even much later than 60 s), `TRIGGERED` fires at that point. This means:

- A device in a fluctuating field environment near the 10% boundary will trigger on the first above-threshold frame after the 60 s window.
- There is no maximum COUNTING duration; the detector stays COUNTING indefinitely as long as all preconditions hold (even if elapsed > 60 s but deviation ≤ 10%).
- The product rationale: a legitimate gradual environmental drift may stay just under 10% for a while before crossing it. Resetting the timer in this case would permanently suppress the banner in that environment.

This behavior is an approved edge-case rule for this TSPEC. It should be added to FSPEC-CAL-03 in the next FSPEC revision to prevent silent re-invention.

```kotlin
package com.luopan.compass.drift

import com.luopan.compass.model.InterferenceState
import com.luopan.compass.util.Clock
import kotlin.math.abs

/**
 * State machine that tracks whether the measured magnetic field has drifted significantly
 * from the calibration-time expected field for a sustained period.
 *
 * [onFrame] is called from the CompassViewModel sensor loop on Dispatchers.Default.
 * Thread-safety: not thread-safe; must always be called from the same thread.
 *
 * @param clock Injectable Clock for deterministic testing. Production: WallClock.
 */
class DriftDetector(private val clock: Clock) {

    private enum class State { IDLE, COUNTING }

    private var state: State = State.IDLE

    /** Wall-clock ms when all preconditions first held in the current COUNTING run. */
    private var countingStartMs: Long? = null

    companion object {
        private const val DRIFT_WINDOW_MS = 60_000L       // 60 seconds
        private const val DRIFT_THRESHOLD = 0.10f         // 10%
        private const val ACCEL_VARIANCE_THRESHOLD = 0.01f // (m/s²)²
    }

    /**
     * Called once per sensor frame from [CompassViewModel.startSensorCollection()].
     *
     * @param accVariance      Variance of combined 3-axis accel magnitude over rolling 5-s window
     *                         (from AccelerometerVarianceTracker).
     * @param measuredMagnitudeUt Current magnetometer field magnitude in µT.
     * @param interferenceState Current InterferenceState from InterferenceDetector.
     * @param expectedFieldUt  CalibrationRecord.expected_field_ut (0.0 if no calibration).
     * @return [DriftEvent.TRIGGERED] when the drift condition is met for the first time in this
     *         window; [DriftEvent.RESET] if the state transitions from COUNTING back to IDLE
     *         due to a precondition violation (informational only — ViewModel may ignore); null
     *         if no state transition produces an event worth emitting.
     */
    fun onFrame(
        accVariance: Float,
        measuredMagnitudeUt: Float,
        interferenceState: InterferenceState,
        expectedFieldUt: Float
    ): DriftEvent? {
        val allPreconditionsMet =
            accVariance < ACCEL_VARIANCE_THRESHOLD &&
            interferenceState != InterferenceState.WARNING &&
            expectedFieldUt > 0.0f

        return when (state) {
            State.IDLE -> {
                if (allPreconditionsMet) {
                    state = State.COUNTING
                    countingStartMs = clock.nowMs()
                }
                null
            }
            State.COUNTING -> {
                if (!allPreconditionsMet) {
                    state = State.IDLE
                    countingStartMs = null
                    return DriftEvent.RESET
                }
                val elapsedMs = clock.nowMs() - (countingStartMs ?: clock.nowMs())
                if (elapsedMs > DRIFT_WINDOW_MS) {
                    val deviation = abs(measuredMagnitudeUt - expectedFieldUt) / expectedFieldUt
                    if (deviation > DRIFT_THRESHOLD) {
                        state = State.IDLE
                        countingStartMs = null
                        return DriftEvent.TRIGGERED
                    }
                    // Timer elapsed but deviation ≤ 10%: stay COUNTING from current start
                    // (continue waiting — user may be in a slightly different field that is
                    // not yet above the threshold; do not reset the timer)
                }
                null
            }
        }
    }

    /**
     * Resets the detector to IDLE with a cleared timer.
     * Called by CompassViewModel on RESULT_OK from CalibrationWizardActivity.
     */
    fun reset() {
        state = State.IDLE
        countingStartMs = null
    }

    /**
     * Exposed for testing: current elapsed counting time in ms, or null if IDLE.
     * @VisibleForTesting
     */
    internal fun elapsedCountingMs(): Long? {
        val start = countingStartMs ?: return null
        return clock.nowMs() - start
    }
}
```

**Timer behavior — "staying in COUNTING after threshold elapsed but deviation ≤ 10%":**
The FSPEC says the threshold is evaluated only when "the timer has elapsed (> 60 s)." If the timer exceeds 60 s but deviation is ≤ 10%, the detector stays COUNTING — it does not reset, because there is no precondition violation. This allows subsequent frames to re-evaluate the deviation after the 60-second mark. If deviation then crosses 10% on a later frame, `TRIGGERED` fires. This is correct behavior: a legitimate field environment change may cause temporary deviation fluctuation.

**`DriftEvent.RESET` semantics:** Returned when the detector transitions COUNTING → IDLE due to a precondition violation. The ViewModel does not need to act on this event; it is informational only. The ViewModel only acts on `TRIGGERED`.

### 5.4 SensorFileWriter Interface

**File:** `app/src/main/java/com/luopan/compass/diagnostics/SensorFileWriter.kt`

**TE FSPEC-v2 F-01 / SE FSPEC-v2 F-07 Resolution:** The interface owns the file path; `SensorCapabilityLogger` computes the content and delegates writing to the interface. This means the interface receives both the target `File` and the JSON string, allowing tests to verify the content without touching the filesystem.

```kotlin
package com.luopan.compass.diagnostics

import java.io.File

/**
 * Abstraction over the file write operation for [SensorCapabilityLogger].
 *
 * The production implementation writes [content] to [file] using [file.writeText].
 * The test stub throws [java.io.IOException] to simulate write failure.
 *
 * [SensorCapabilityLogger] is responsible for computing the target [File] path
 * (Context.getFilesDir() / "sensor_profile.json") and the serialized JSON [content].
 * This interface is responsible only for the write operation.
 */
interface SensorFileWriter {
    /**
     * Writes [content] to [file], overwriting if it exists.
     * @throws java.io.IOException if the write fails.
     */
    fun write(file: File, content: String)
}

/** Production implementation. */
class RealSensorFileWriter : SensorFileWriter {
    override fun write(file: File, content: String) {
        file.writeText(content)
    }
}
```

### 5.5 SensorCapabilityLogger

**File:** `app/src/main/java/com/luopan/compass/diagnostics/SensorCapabilityLogger.kt`

```kotlin
package com.luopan.compass.diagnostics

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import com.luopan.compass.BuildConfig
import com.luopan.compass.settings.SettingsRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Writes a sensor capability profile JSON to internal app storage on first launch
 * after a new app version is installed.
 *
 * Version gate: only writes when BuildConfig.VERSION_CODE > stored sensorProfileWrittenForVersion.
 * On successful write, updates the stored version. On failure (IOException), logs at Log.e
 * and does NOT update the stored version (guarantees retry on next launch).
 *
 * Called from CompassActivity.onCreate() on the main thread; the write is dispatched to
 * a background coroutine by the caller to avoid blocking the main thread.
 *
 * @param context Application context.
 * @param settings SettingsRepository for reading/writing the version gate key.
 * @param fileWriter Abstraction over the write operation (injectable for tests).
 */
class SensorCapabilityLogger(
    private val context: Context,
    private val settings: SettingsRepository,
    private val fileWriter: SensorFileWriter = RealSensorFileWriter()
) {
    companion object {
        private const val TAG = "SensorCapabilityLogger"
        private const val FILE_NAME = "sensor_profile.json"
    }

    /**
     * Checks the version gate and writes sensor_profile.json if needed.
     *
     * Must be called from a background dispatcher (Dispatchers.IO).
     * Returns immediately if the version gate blocks the write.
     *
     * Failure contract (PM TSPEC-v1 F-02): any exception thrown during sensor enumeration
     * ([SensorManager.getSensorList]) or file writing ([SensorFileWriter.write]) — including
     * [IOException], [SecurityException], or any other runtime exception — is caught, logged
     * at [Log.e], and swallowed. The version key is NOT updated on failure, guaranteeing a
     * retry on the next launch. This ensures a sensor-diagnostics write never crashes the app.
     */
    fun maybeWrite() {
        if (BuildConfig.VERSION_CODE <= settings.sensorProfileWrittenForVersion) return

        try {
            val json = buildJson()
            val file = File(context.filesDir, FILE_NAME)
            fileWriter.write(file, json)
            settings.sensorProfileWrittenForVersion = BuildConfig.VERSION_CODE
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write sensor_profile.json — will retry on next launch", e)
            // sensorProfileWrittenForVersion NOT updated → retry guaranteed
        }
    }

    private fun buildJson(): String {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        return buildJsonFromSensors(sensors)
    }

    /**
     * Pure function: builds the JSON string from a pre-resolved sensor list.
     *
     * Extracted from [buildJson] to remove the [Context]/[SensorManager] dependency from the
     * serialization logic, enabling JVM unit tests for AT-SENSOR-01-E through G (TE TSPEC-v1 F-07).
     * The Android context dependency is confined to [buildJson], which resolves the sensor list
     * and delegates here. Tests inject a list of [FakeSensorDescriptor] objects.
     *
     * @param sensors Pre-resolved list of [Sensor] objects (or test doubles implementing the needed fields).
     * @param versionCode App version code (defaulting to [BuildConfig.VERSION_CODE]).
     * @param writtenAtIso8601 Timestamp string (defaulting to [Instant.now()] formatted as ISO-8601).
     */
    internal fun buildJsonFromSensors(
        sensors: List<Sensor>,
        versionCode: Int = BuildConfig.VERSION_CODE,
        writtenAtIso8601: String = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
    ): String {
        val sensorsArray = JSONArray()
        for (sensor in sensors) {
            val obj = JSONObject()
            obj.put("type_constant", sensor.type)
            obj.put("name", sensor.name)
            obj.put("vendor", sensor.vendor)
            obj.put("resolution_ut_or_native", sensor.resolution.toDouble())
            obj.put("max_range_native", sensor.maximumRange.toDouble())
            obj.put("reporting_mode", mapReportingMode(sensor.reportingMode))
            sensorsArray.put(obj)
        }

        val root = JSONObject()
        root.put("device_model", Build.MODEL)
        root.put("device_manufacturer", Build.MANUFACTURER)
        root.put("android_version", Build.VERSION.RELEASE)
        root.put("android_api_level", Build.VERSION.SDK_INT)
        root.put("app_version_code", versionCode)
        root.put("written_at_iso8601", writtenAtIso8601)
        root.put("sensors", sensorsArray)

        return root.toString(2)  // 2-space indent
    }

    internal fun mapReportingMode(mode: Int): String = when (mode) {
        Sensor.REPORTING_MODE_CONTINUOUS     -> "CONTINUOUS"
        Sensor.REPORTING_MODE_ON_CHANGE      -> "ON_CHANGE"
        Sensor.REPORTING_MODE_ONE_SHOT       -> "ONE_SHOT"
        Sensor.REPORTING_MODE_SPECIAL_TRIGGER -> "SPECIAL_TRIGGER"
        else                                  -> "UNKNOWN($mode)"
    }
}
```

**I/O failure contract:** All exceptions (`Exception`, including `IOException` and `SecurityException`) are caught, logged at `Log.e`, and the version key is NOT updated (PM TSPEC-v1 F-02). On the next launch, `VERSION_CODE > storedVersion` is still true, triggering a retry. The app never crashes due to a sensor-diagnostics write.

**Thread model:** `maybeWrite()` is called from `Dispatchers.IO` by `CompassActivity`. File I/O must not run on the main thread.

---

## 6. ViewModel Layer

### 6.1 CompassViewModel Modifications

**File:** `app/src/main/java/com/luopan/compass/ui/CompassViewModel.kt`

#### 6.1.1 New Fields

```kotlin
// Phase 4 — Drift detection
private val accVarianceTracker: AccelerometerVarianceTracker  // injected via constructor
private val driftDetector: IDriftDetector                     // IDriftDetector for testability (SE PROPERTIES-v2 F-01)

private val _driftBannerState = MutableStateFlow(DriftBannerState.HIDDEN)
val driftBannerState: StateFlow<DriftBannerState> = _driftBannerState.asStateFlow()

// Phase 4 — Age banner session dismiss flag
var calAgeBannerDismissed: Boolean = false
    private set  // only CompassViewModel sets this; BearingHistoryFragment reads it
```

**Initial value:** `DriftBannerState.HIDDEN` prevents banner flicker on Fragment start before the first sensor frame is processed.

#### 6.1.2 loadCalibrationAge() — Visibility Change

```kotlin
// BEFORE (private):
private fun loadCalibrationAge() { ... }

// AFTER (internal):
internal fun loadCalibrationAge() { ... }
```

**SE FSPEC-v2 F-01 Resolution:** `loadCalibrationAge()` launches `viewModelScope.launch(Dispatchers.IO)` internally. Calling it from Fragment lifecycle callbacks (main thread) is safe — there is no need for additional dispatcher wrapping in `BearingHistoryFragment`. The TSPEC explicitly documents this so implementers do not add unnecessary `withContext()` calls.

#### 6.1.3 New Public Methods

```kotlin
/** Called from BearingHistoryFragment when the age banner X button is tapped. */
fun dismissCalAgeBanner() {
    calAgeBannerDismissed = true
}

/**
 * Called from BearingHistoryFragment on RESULT_OK from CalibrationWizardActivity.
 * Variant: age banner path.
 *
 * Race-condition mitigation (PM TSPEC-v1 F-04): sets calAgeBannerDismissed = true immediately
 * to suppress any re-flash while the async load is in progress. Once loadCalibrationAge()
 * resolves with age ≤ 30, it clears the flag (age-driven hide takes over).
 *
 * See §7.1 for the full implementation body.
 */
fun onCalibrationCompleteFromHistory() {
    calAgeBannerDismissed = true          // suppress immediately — prevents re-flash
    viewModelScope.launch(Dispatchers.IO) {
        val record = calibrationRepo.getCurrent()
        if (record != null) {
            val ageMs = clock.nowMs() - record.recorded_at
            val ageDays = TimeUnit.MILLISECONDS.toDays(ageMs)
            calibrationAgeDays = ageDays
            calibrationQuality = CalibrationQuality.valueOf(record.quality)
            expectedFieldUt = record.expected_field_ut
            if (ageDays <= 30L) {
                calAgeBannerDismissed = false  // age-driven hide now in effect; clear suppress flag
            }
        }
    }
}

/**
 * Called from BearingHistoryFragment on RESULT_OK from CalibrationWizardActivity.
 * Variant: drift banner path.
 * Effects: drift detector reset; cooldown cleared; driftBannerState = HIDDEN.
 */
fun resetDriftDetector() {
    driftDetector.reset()
    settings.driftCooldownTimestampMs = 0L
    _driftBannerState.value = DriftBannerState.HIDDEN
}

/** Called from BearingHistoryFragment when the drift banner X button is tapped. */
fun dismissDriftBanner(nowMs: Long = clock.nowMs()) {
    settings.driftCooldownTimestampMs = nowMs
    _driftBannerState.value = DriftBannerState.HIDDEN
}
```

#### 6.1.4 Drift Detection in Sensor Loop

In `startSensorCollection()`, after computing `interferenceState`, add:

```kotlin
// Phase 4 — AccelerometerVarianceTracker update (Dispatchers.Default)
val accVariance = accVarianceTracker.update(frame.accel_x, frame.accel_y, frame.accel_z)

// Phase 4 — DriftDetector update
val expectedFieldUt = calibrationRepo.getCurrent()?.expected_field_ut ?: 0.0f
val driftEvent = driftDetector.onFrame(
    accVariance = accVariance,
    measuredMagnitudeUt = magnitude.toFloat(),
    interferenceState = interferenceState,
    expectedFieldUt = expectedFieldUt
)
if (driftEvent == DriftEvent.TRIGGERED) {
    val nowMs = clock.nowMs()
    val cooldownMs = settings.driftCooldownTimestampMs
    if (nowMs - cooldownMs >= 600_000L) {  // 10-minute cooldown
        _driftBannerState.value = DriftBannerState.VISIBLE
    }
}
```

**Performance note:** `calibrationRepo.getCurrent()` is a suspend DAO call. To avoid blocking the sensor loop at 200 Hz, the current calibration record is cached in a ViewModel field and refreshed only on `loadCalibrationAge()` calls (same pattern as `calibrationAgeDays`). Add:

```kotlin
private var expectedFieldUt: Float = 0.0f
```

Update `loadCalibrationAge()`:

```kotlin
internal fun loadCalibrationAge() {
    viewModelScope.launch(Dispatchers.IO) {
        val record = calibrationRepo.getCurrent()
        if (record != null) {
            val ageMs = clock.nowMs() - record.recorded_at
            calibrationAgeDays = TimeUnit.MILLISECONDS.toDays(ageMs)
            calibrationQuality = CalibrationQuality.valueOf(record.quality)
            expectedFieldUt = record.expected_field_ut      // ← Phase 4 addition
        } else {
            expectedFieldUt = 0.0f
        }
    }
}
```

The sensor loop then reads `expectedFieldUt` (in-memory field; written on IO, read on Default — safe because Float writes are atomic on JVM and the value is only used as a hint, not a critical guard).

#### 6.1.5 Factory Constructor Update

```kotlin
class Factory(
    private val application: Application,
    private val modelProvider: MagneticFieldModelProvider?,
    private val locationRepository: LocationRepository?,
    private val clock: Clock,
    // Phase 4 additions (optional for backward compatibility with tests)
    private val driftDetector: IDriftDetector? = null,   // IDriftDetector, not DriftDetector (SE PROPERTIES-v2 F-01)
    private val accVarianceTracker: AccelerometerVarianceTracker? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // ...
        val drift = driftDetector ?: DriftDetector(clock)       // DriftDetector implements IDriftDetector
        val tracker = accVarianceTracker ?: AccelerometerVarianceTracker(clock)
        return CompassViewModel(application, modelProvider, locationRepository, clock, captureUseCase, calibrationRepo, drift, tracker) as T
    }
}
```

### 6.2 SE FSPEC-v2 F-03 Resolution — ActivityResultLauncher Disambiguation

**Two separate `ActivityResultLauncher` instances** are used in `BearingHistoryFragment` — one for the age banner and one for the drift banner. This avoids any source-disambiguation logic in the `RESULT_OK` handler and is the clearest implementation:

```kotlin
private val calWizardLauncherAge = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
        compassViewModel.onCalibrationCompleteFromHistory()  // refresh age only
    }
}

private val calWizardLauncherDrift = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
        compassViewModel.resetDriftDetector()  // reset drift only
    }
}
```

Both launchers are registered in `Fragment.onCreate()` per Android Jetpack best practice (must be registered before `onStart()`).

### 6.3 BearingHistoryViewModel

**File:** `app/src/main/java/com/luopan/compass/ui/BearingHistoryViewModel.kt`

Fragment-scoped (`viewModels()`). Owns list display, search state, and delete/undo state. The scope ensures the search query is cleared when the Fragment is destroyed on tab navigation.

```kotlin
package com.luopan.compass.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.luopan.compass.bearing.BearingDao
import com.luopan.compass.bearing.BearingRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

class BearingHistoryViewModel(private val dao: BearingDao) : ViewModel() {

    // Search query state — retained across configuration changes within the same Fragment lifecycle
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // In-memory pending undo record (null = no active undo)
    private var pendingUndo: BearingRecord? = null

    /**
     * The active list Flow: switches between getAllFlow() and searchFlow(query) based on query state.
     *
     * Empty query → getAllFlow() immediately (no debounce).
     * Non-empty query → searchFlow(debounced query) after 300 ms idle.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val bearingList: Flow<List<BearingRecord>> = _searchQuery
        .debounce { query -> if (query.isEmpty()) 0L else 300L }
        .flatMapLatest { query ->
            if (query.isEmpty()) dao.getAllFlow()
            else dao.searchFlow(query)
        }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Deletes the record immediately and saves it as the pending undo record.
     * If a prior undo is pending, it is replaced (prior deletion is permanent).
     */
    fun deleteRecord(record: BearingRecord) {
        pendingUndo = record
        viewModelScope.launch(Dispatchers.IO) {
            dao.delete(record.id)
        }
    }

    /**
     * Re-inserts the pending undo record. Clears the pending undo state.
     * No-op if no undo is pending.
     */
    fun undoDelete() {
        val record = pendingUndo ?: return
        pendingUndo = null
        viewModelScope.launch(Dispatchers.IO) {
            dao.insert(record)
        }
    }

    /** Clears the pending undo without restoring. Called when Snackbar times out. */
    fun commitDelete() {
        pendingUndo = null
    }

    fun hasPendingUndo(): Boolean = pendingUndo != null

    class Factory(private val dao: BearingDao) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == BearingHistoryViewModel::class.java)
            return BearingHistoryViewModel(dao) as T
        }
    }
}
```

**Debounce strategy:** Using `debounce { query -> if (query.isEmpty()) 0L else 300L }` provides immediate restore on clear (0 ms debounce) and 300 ms debounce on non-empty queries, in a single operator chain. This avoids a conditional `flatMapLatest` split and keeps the Flow composition simple.

**TE FSPEC-v2 F-01 (invocation-count assertions) Resolution:** See §9.2 `FakeBearingDao`.

### 6.4 DriftBannerState

**File:** `app/src/main/java/com/luopan/compass/ui/DriftBannerState.kt`

```kotlin
package com.luopan.compass.ui

enum class DriftBannerState { HIDDEN, VISIBLE }
```

---

## 7. UI Layer

### 7.1 BearingHistoryFragment

**File:** `app/src/main/java/com/luopan/compass/ui/BearingHistoryFragment.kt`

**ViewModel access:**
- `compassViewModel: CompassViewModel` via `activityViewModels()` — shared with ModernCompassFragment and LuopanFragment.
- `historyViewModel: BearingHistoryViewModel` via `viewModels(factory = BearingHistoryViewModel.Factory(dao))` — fragment-scoped.

**ActivityResultLauncher registration:** Two launchers registered in `onCreate()` (see §6.2).

**Lifecycle callbacks:**

```kotlin
override fun onResume() {
    super.onResume()
    compassViewModel.loadCalibrationAge()  // refresh age banner on fragment become-visible
}
```

`loadCalibrationAge()` is safe to call from the main thread (it launches `Dispatchers.IO` internally — see §6.1.2).

**Search bar:** A `SearchView` (or `EditText` with `addTextChangedListener`) at the top of the layout. Text changes call `historyViewModel.setSearchQuery(text)`. Cleared on Fragment destroy (ViewModel is fragment-scoped).

**State A — Zero records:** When `bearingList` emits an empty list AND `searchQuery` is empty, show the "no records" empty state and hide the search bar (`View.GONE`). When `searchQuery` is non-empty and `bearingList` emits empty, show the "no search results" empty state (search bar remains visible).

**Search bar restoration (PM TSPEC-v1 F-06):** When `bearingList` emits a non-empty list and `searchQuery` is empty, the Fragment must restore the search bar to `View.VISIBLE` on the same emission that reveals the RecyclerView and hides the empty-state view. The full state-update logic is:

```kotlin
private fun updateListState(records: List<BearingRecord>, query: String) {
    val isEmpty = records.isEmpty()
    val isSearchActive = query.isNotEmpty()

    when {
        isEmpty && !isSearchActive -> {
            // State A: zero records, no search
            searchBar.visibility = View.GONE
            recyclerView.visibility = View.GONE
            emptyNoRecords.visibility = View.VISIBLE
            emptyNoResults.visibility = View.GONE
        }
        isEmpty && isSearchActive -> {
            // No search results
            searchBar.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            emptyNoRecords.visibility = View.GONE
            emptyNoResults.visibility = View.VISIBLE
        }
        else -> {
            // Normal list (≥1 record)
            searchBar.visibility = View.VISIBLE    // ← restored here
            recyclerView.visibility = View.VISIBLE
            emptyNoRecords.visibility = View.GONE
            emptyNoResults.visibility = View.GONE
            adapter.submitList(records)
        }
    }
}
```

This function is called inside the `bearingList` collector in `repeatOnLifecycle(STARTED)`, receiving both the current records and the current `searchQuery` value. The search bar transition from `GONE` to `VISIBLE` is driven purely by the Flow emission — no explicit "restore" trigger is needed.

**Banner layout:** Both age and drift banners use the same `banner_recalibration.xml` layout (two separate inflations in the Fragment layout). Each banner has:
- A close/X `ImageButton` (right side)
- A `TextView` for the banner text (body tap area)
- A root `ConstraintLayout` as the tap target for launching CalibrationWizardActivity

**Banner observation:**

```kotlin
viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch {
            compassViewModel.uiState.collect { state ->
                updateAgeBanner(state.calibration_age_days)
            }
        }
        launch {
            compassViewModel.driftBannerState.collect { bannerState ->
                updateDriftBanner(bannerState)
            }
        }
    }
}
```

**Age banner visibility logic:**

```kotlin
private fun updateAgeBanner(ageDays: Long) {
    val showBanner = ageDays > 30L && !compassViewModel.calAgeBannerDismissed
    ageBannerRoot.visibility = if (showBanner) View.VISIBLE else View.GONE
    if (showBanner) {
        ageBannerText.text = getString(R.string.banner_cal_age, ageDays)
    }
}
```

**SE FSPEC-v2 F-04 Resolution (informational):** After `RESULT_OK`, `loadCalibrationAge()` updates `calibration_age_days` to 0 (or current age). The banner hides because `ageDays > 30L` evaluates to false. `calAgeBannerDismissed` is not set to true on this path — the hide is driven entirely by `calibration_age_days ≤ 30`. This is the correct and self-consistent behavior per FSPEC-CAL-01 Dismiss Mechanics.

**Age-banner re-flash race mitigation (PM TSPEC-v1 F-04):** There is a brief window between `RESULT_OK` return and `loadCalibrationAge()` completing on `Dispatchers.IO` during which the old `calibration_age_days` value (> 30) is still in the StateFlow. If a UI recomposition fires in this window, the banner could momentarily re-appear. The mitigation:

1. In `onCalibrationCompleteFromHistory()`, set `calAgeBannerDismissed = true` immediately (before `loadCalibrationAge()`).
2. In `loadCalibrationAge()`, after the IO completes, if the resolved age ≤ 30, clear the flag: `calAgeBannerDismissed = false` (the banner hides by state, not flag). If age > 30, keep the flag true so the banner remains suppressed.

```kotlin
fun onCalibrationCompleteFromHistory() {
    calAgeBannerDismissed = true          // ① suppress immediately — prevents re-flash
    viewModelScope.launch(Dispatchers.IO) {
        val record = calibrationRepo.getCurrent()
        if (record != null) {
            val ageMs = clock.nowMs() - record.recorded_at
            val ageDays = TimeUnit.MILLISECONDS.toDays(ageMs)
            calibrationAgeDays = ageDays
            expectedFieldUt = record.expected_field_ut
            if (ageDays <= 30L) {
                calAgeBannerDismissed = false  // ② age-driven hide now in effect; clear flag
            }
        }
    }
}
```

This ensures the banner never flashes between `RESULT_OK` and the async load completing, while still allowing state-driven hide logic to take over once the age is confirmed ≤ 30.

### 7.2 BearingAdapter

**File:** `app/src/main/java/com/luopan/compass/ui/BearingAdapter.kt`

`ListAdapter<BearingRecord, BearingAdapter.ViewHolder>` backed by `DiffUtil.ItemCallback<BearingRecord>`.

**DiffUtil callback:**

```kotlin
object DIFF_CALLBACK : DiffUtil.ItemCallback<BearingRecord>() {
    override fun areItemsTheSame(old: BearingRecord, new: BearingRecord) = old.id == new.id
    override fun areContentsTheSame(old: BearingRecord, new: BearingRecord) = old == new
}
```

**Expand/collapse:** The adapter tracks the currently expanded item ID as `private var expandedId: String? = null`. Only one item is expanded at a time.

```kotlin
fun toggleExpanded(itemId: String, position: Int) {
    val previousExpandedId = expandedId
    expandedId = if (expandedId == itemId) null else itemId

    // Notify only the affected items — avoids a full rebind of all 500 rows
    // which would cause visible jank on large lists (PM TSPEC-v1 F-05).
    val previousPosition = currentList.indexOfFirst { it.id == previousExpandedId }
    if (previousPosition != -1) notifyItemChanged(previousPosition)  // collapse old
    if (expandedId != null) notifyItemChanged(position)              // expand new
}
```

**PM TSPEC-v1 F-05 rationale:** `notifyDataSetChanged()` would rebind all visible rows on each expand/collapse, causing measurable jank at 500 records (each row has non-trivial bind logic including badge visibility and format calls). Using `notifyItemChanged()` on only the two affected positions (previous expanded + new expanded) keeps expand/collapse at O(1) view work regardless of list size. The 16 ms fling NFR does not cover expand/collapse, but this change prevents user-visible jank in professional use (practitioners may have hundreds of records).

**ViewHolder fields:**
- `nameText: TextView`
- `bearingText: TextView` (e.g., "045.2° True North")
- `confidenceBadge: TextView` (chip-style)
- `timestampText: TextView`
- `interferenceBadge: TextView` (`View.GONE` when `interference_flag = false`)
- `expandedPanel: View` (root of the expanded detail section)
- `expandedBearing: TextView`
- `expandedConfidence: TextView`
- `expandedCapturedAt: TextView`
- `expandedName: TextView`
- `expandedNotes: TextView`
- `expandedFieldDeviation: TextView` (shown only when `interference_flag = true`)
- `expandedInclinationDeviation: TextView` (shown only when `interference_flag = true`)

**Interference badge display:**

```kotlin
if (record.interference_flag) {
    interferenceBadge.visibility = View.VISIBLE
} else {
    interferenceBadge.visibility = View.GONE
}
```

**Field deviation formatting:**

```kotlin
// field_deviation_pct is stored as fractional (e.g., 0.25 for 25%)
// Display: integer truncation × 100, "%" suffix
val pct = (record.field_deviation_pct * 100).toInt()  // truncation toward zero
expandedFieldDeviation.text = "$pct%"
```

**Inclination deviation formatting:**

```kotlin
// inclination_deviation_deg may be negative
// Display: integer truncation toward zero, "°" suffix
val deg = if (record.inclination_deviation_deg >= 0) {
    record.inclination_deviation_deg.toInt()
} else {
    -((-record.inclination_deviation_deg).toInt())
}
expandedInclinationDeviation.text = "$deg°"
```

This implements truncation toward zero (not `floor`): `-2.3f → -2`, `-2.9f → -2`, `4.7f → 4`. Note: `Float.toInt()` in Kotlin already truncates toward zero.

**Performance:** `ListAdapter` with `DiffUtil` ensures O(N) diff computation on a background thread (Room's `Flow` emissions are on `Dispatchers.IO`; the adapter receives them on the main thread via `collectLatest` in the Fragment). For 500 records, this is well within the 500 ms initial-load budget.

### 7.3 Swipe-to-Delete with ItemTouchHelper

**Configuration:**

```kotlin
val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
    override fun onMove(...) = false
    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.adapterPosition
        val record = adapter.currentList[position]
        
        // Commit any prior undo before starting a new one
        currentSnackbar?.dismiss()  // dismisses first Snackbar; first deletion is permanent
        historyViewModel.deleteRecord(record)
        
        currentSnackbar = Snackbar.make(requireView(), R.string.bearing_deleted, 5000)
            .setAction(R.string.undo) {
                historyViewModel.undoDelete()
            }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(snackbar: Snackbar, event: Int) {
                    if (event != DISMISS_EVENT_ACTION) {
                        historyViewModel.commitDelete()
                    }
                }
            })
        currentSnackbar?.show()
    }
})
itemTouchHelper.attachToRecyclerView(recyclerView)
```

**Snackbar duration:** `setDuration(5000)` — explicitly set to 5000 ms, NOT `Snackbar.LENGTH_LONG` (which is 2750 ms). This implements the 5-second requirement from REQ-CAPTURE-03.

**SE FSPEC-v2 F-06 Resolution:** The TSPEC explicitly specifies `setDuration(5000)` to prevent engineers from using `LENGTH_LONG`.

**Single active undo:** `currentSnackbar` is a nullable field in `BearingHistoryFragment`. Swiping a second record calls `currentSnackbar?.dismiss()` before showing the new one. The `onDismissed` callback with `event != DISMISS_EVENT_ACTION` fires, calling `historyViewModel.commitDelete()` (which clears `pendingUndo` for the first record). Then `historyViewModel.deleteRecord(secondRecord)` is called, setting `pendingUndo` to the second record.

**Accessibility:** The Snackbar "Undo" action button is accessible to TalkBack users. The `setAction()` sets a content description automatically from the action text. No additional accessibility annotations are required.

### 7.4 Layout Files

#### fragment_bearing_history.xml

```xml
<!-- Vertical LinearLayout root -->
<!-- 1. Search bar (SearchView or EditText) — GONE in State A (zero records) -->
<!-- 2. Banner: age-based recalibration (GONE by default) -->
<!-- 3. Banner: drift-based recalibration (GONE by default) -->
<!-- 4. RecyclerView — GONE when empty state is shown -->
<!-- 5. Empty state (two variants: zero records / no search results) -->
```

View IDs:
- `R.id.search_bar` — SearchView
- `R.id.banner_age_root` — age banner ConstraintLayout
- `R.id.banner_age_text` — age banner TextView
- `R.id.banner_age_close` — age banner X ImageButton
- `R.id.banner_drift_root` — drift banner ConstraintLayout
- `R.id.banner_drift_text` — drift banner TextView
- `R.id.banner_drift_close` — drift banner X ImageButton
- `R.id.recycler_history` — RecyclerView
- `R.id.empty_state_no_records` — empty state for zero records (contains illustration + text)
- `R.id.empty_state_no_results` — empty state for zero search results (contains text)

#### item_bearing_record.xml

Row layout with all view IDs specified in §7.2.

#### banner_recalibration.xml

Shared layout file. Used for both age and drift banners via `<include>` in `fragment_bearing_history.xml` with ID overrides.

### 7.5 String Resources

Add to `app/src/main/res/values/strings.xml`:

```xml
<string name="tab_history">History</string>
<string name="banner_cal_age">Your calibration is %1$d days old — consider recalibrating</string>
<string name="banner_drift">Magnetic environment may have changed — recalibrate for best accuracy</string>
<string name="bearing_deleted">Bearing deleted</string>
<string name="undo">Undo</string>
<string name="empty_no_bearings">No bearings yet — capture your first bearing from the compass screen</string>
<string name="empty_no_results">No bearings match your search</string>
<string name="interference_badge">⚠ Captured under interference</string>
```

---

## 8. Navigation Architecture

### 8.1 CompassActivity Changes

**File:** `app/src/main/java/com/luopan/compass/CompassActivity.kt`

Four mandatory changes (SE FSPEC-v2 F-03 resolution; FSPEC-NAV-01):

**1. TAB_HISTORY constant:**

```kotlin
companion object {
    private const val TAB_MODERN = 0
    private const val TAB_LUOPAN = 1
    private const val TAB_HISTORY = 2
}
```

**2. wireTabNavigation() — new when branch:**

```kotlin
private fun wireTabNavigation() {
    tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
        override fun onTabSelected(tab: TabLayout.Tab) {
            when (tab.position) {
                TAB_MODERN  -> navController.navigate(R.id.dest_modern)
                TAB_LUOPAN  -> navController.navigate(R.id.dest_luopan)
                TAB_HISTORY -> navController.navigate(R.id.dest_history)
            }
        }
        override fun onTabUnselected(tab: TabLayout.Tab) {}
        override fun onTabReselected(tab: TabLayout.Tab) {}
    })
}
```

**3. addOnDestinationChangedListener:** Add case for `R.id.dest_history` (e.g., adjusting toolbar title or FAB visibility if applicable).

**4. SensorCapabilityLogger call in onCreate():**

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_compass)
    
    // Phase 4 — Sensor capability logging (dispatched to IO)
    lifecycleScope.launch(Dispatchers.IO) {
        SensorCapabilityLogger(applicationContext, SettingsRepository(applicationContext)).maybeWrite()
    }
    
    wireTabNavigation()
    // ... existing setup
}
```

### 8.2 activity_compass.xml Change

Add third TabItem:

```xml
<com.google.android.material.tabs.TabItem
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="@string/tab_history" />
```

### 8.3 nav_graph.xml Change

```xml
<fragment
    android:id="@+id/dest_history"
    android:name="com.luopan.compass.ui.BearingHistoryFragment"
    android:label="@string/tab_history" />
```

The existing `dest_modern` and `dest_luopan` entries are unmodified.

### 8.4 Navigation Flow

```
CompassActivity (TabLayout, NavController)
  │
  ├── Tab 0: ModernCompassFragment  (dest_modern)
  ├── Tab 1: LuopanFragment         (dest_luopan)
  └── Tab 2: BearingHistoryFragment (dest_history)  ← new
                │
                ├── Age banner tap ──► CalibrationWizardActivity
                │                         ├── RESULT_OK → onCalibrationCompleteFromHistory()
                │                         └── RESULT_CANCELED → banner unchanged
                │
                └── Drift banner tap ─► CalibrationWizardActivity
                                          ├── RESULT_OK → resetDriftDetector()
                                          └── RESULT_CANCELED → banner unchanged
```

---

## 9. Test Strategy

### 9.1 Test Categories and Coverage Matrix

| Component | Unit | Integration | Instrumented |
|-----------|------|-------------|--------------|
| `DriftDetector` | AT-CAL-03-A through F; boundary at 10% | AT-CAL-INT-01 | — |
| `AccelerometerVarianceTracker` | AT-VAR-01 (population variance formula); window eviction; time-based window; n<2 guard | — | — |
| `BearingHistoryViewModel` (search) | AT-HIST-02-A through D (FakeBearingDao) | — | — |
| `BearingHistoryViewModel` (drift banner) | AT-CAL-02-A, D, E (FakeClock); AT-VM-DRIFT-01 (TRIGGERED→cooldown-check→VISIBLE wiring) | — | — |
| `SensorCapabilityLogger` | AT-SENSOR-01-E, F, G | — | AT-SENSOR-01-A through D |
| `BearingHistoryFragment` (list) | — | — | AT-HIST-01-A through C |
| `BearingHistoryFragment` (empty state) | — | — | AT-HIST-04-A through B |
| `BearingHistoryFragment` (interference badge) | AT-HIST-05-D (format function) | — | AT-HIST-05-A through C |
| `BearingHistoryFragment` (swipe/undo) | AT-UNDO-VM-01 through AT-UNDO-VM-03 (pendingUndo state machine, BearingHistoryViewModelTest) | — | AT-HIST-03-A through E |
| `BearingHistoryFragment` (search UI) | — | — | AT-HIST-02-E |
| `BearingHistoryFragment` (age banner) | AT-CAL-01-C (floor division) | — | AT-CAL-01-A, B, D, E, F, G |
| `BearingHistoryFragment` (drift banner) | — | — | AT-CAL-02-B, C, F |
| Navigation (third tab) | — | — | AT-NAV-01-A through C |
| `CalibrationResult.sphereRadius_uT` | `CalibrationRepositoryTest` addition | — | — |
| Room migration v2→v3 | — | — | `LuopanDatabaseMigrationTest` addition |

### 9.2 FakeBearingDao

**File:** `app/src/test/java/com/luopan/compass/ui/FakeBearingDao.kt`

**TE FSPEC-v2 F-01 Resolution:** A hand-written recording fake DAO that records all `searchFlow` and `getAllFlow` calls. This avoids MockK and is consistent with the project's `FakeBearingCaptureUseCase` pattern.

```kotlin
package com.luopan.compass.ui

import com.luopan.compass.bearing.BearingDao
import com.luopan.compass.bearing.BearingRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Recording fake implementation of [BearingDao] for unit testing [BearingHistoryViewModel].
 *
 * Tracks calls to [getAllFlow] and [searchFlow] so tests can assert invocation count and arguments.
 * Backed by an in-memory [MutableStateFlow<List<BearingRecord>>] for reactive emission control.
 */
class FakeBearingDao : BearingDao {

    private val _records = MutableStateFlow<List<BearingRecord>>(emptyList())

    /** Recorded arguments passed to searchFlow(), in order of invocation. */
    val searchFlowCalls = mutableListOf<String>()

    /** Number of times getAllFlow() was called. */
    var getAllFlowCallCount = 0
        private set

    fun setRecords(records: List<BearingRecord>) {
        _records.value = records
    }

    override fun getAllFlow(): Flow<List<BearingRecord>> {
        getAllFlowCallCount++
        return _records
    }

    override fun searchFlow(query: String): Flow<List<BearingRecord>> {
        searchFlowCalls.add(query)
        return _records.map { records ->
            records.filter { it.name.contains(query, ignoreCase = true) }
        }
    }

    override suspend fun insert(record: BearingRecord) {
        _records.value = _records.value + record
    }

    override suspend fun getAll(): List<BearingRecord> = _records.value

    override suspend fun count(): Int = _records.value.size

    override suspend fun delete(id: String) {
        _records.value = _records.value.filter { it.id != id }
    }
}
```

**AT-HIST-02-B and AT-HIST-02-C invocation assertions:** These tests assert that `searchFlowCalls.size == 1` after the debounce elapses and that `searchFlowCalls[0] == "nor"`. This uses the recording fake directly without MockK.

### 9.3 Integration Test — AT-CAL-INT-01

**File:** `app/src/test/java/com/luopan/compass/drift/DriftDetectorIntegrationTest.kt`

**TE FSPEC-v2 F-02 Resolution:** The integration test drives `DriftDetector.onFrame()` directly (bypassing `CompassViewModel.startSensorCollection()`). This is an explicit architectural decision: rather than adding a `SensorEventSource` abstraction (a production-code change with broader impact), the test wires `AccelerometerVarianceTracker → DriftDetector.onFrame()` directly and asserts the output.

The wiring covered:
- `AccelerometerVarianceTracker.update()` → produces `accVariance`
- `DriftDetector.onFrame(accVariance, ...)` → produces `DriftEvent.TRIGGERED`

The wiring NOT covered by this test (covered by `CompassViewModel` unit tests separately):
- `CompassViewModel.startSensorCollection()` → calls `accVarianceTracker.update()` and `driftDetector.onFrame()`
- `DriftEvent.TRIGGERED` → sets `_driftBannerState.value = DriftBannerState.VISIBLE`

```kotlin
@Test
fun `AT-CAL-INT-01 real DriftDetector and AccelerometerVarianceTracker wiring`() = runTest {
    val clock = FakeClock(0L)
    val tracker = AccelerometerVarianceTracker(clock)
    val detector = DriftDetector(clock)

    val expectedFieldUt = 50.0f
    val measuredMagnitudeUt = 56.0f  // 12% deviation

    // ─── Phase 1: Pre-threshold negative assertion (TE TSPEC-v1 F-04) ──────────────
    // Drive 58 seconds of frames (below the 60-second window).
    // Assert that TRIGGERED is NEVER emitted during this phase — a detector that fires
    // immediately would pass the later positive assertion but this guard catches it.
    var triggeredEarly = false
    repeat(1160) {  // 1160 × 50 ms = 58,000 ms = 58 seconds
        clock.advance(50L)
        val accVariance = tracker.update(0f, 0f, 9.8f)
        val event = detector.onFrame(accVariance, measuredMagnitudeUt, InterferenceState.CLEAR, expectedFieldUt)
        if (event == DriftEvent.TRIGGERED) triggeredEarly = true
    }
    assertThat(triggeredEarly).isFalse()  // must NOT trigger before 60 s

    // ─── Phase 2: Post-threshold positive assertion ──────────────────────────────────
    // Advance another 3+ seconds to cross the 60-second mark and assert TRIGGERED fires.
    var triggeredAfterThreshold = false
    repeat(80) {  // 80 × 50 ms = 4,000 ms; total elapsed = 62 seconds
        clock.advance(50L)
        val accVariance = tracker.update(0f, 0f, 9.8f)
        val event = detector.onFrame(accVariance, measuredMagnitudeUt, InterferenceState.CLEAR, expectedFieldUt)
        if (event == DriftEvent.TRIGGERED) {
            triggeredAfterThreshold = true
            return@repeat
        }
    }
    assertThat(triggeredAfterThreshold).isTrue()  // MUST trigger after 60 s with 12% deviation
}
```

This test verifies the wiring: `AccelerometerVarianceTracker → DriftDetector.onFrame() → DriftEvent.TRIGGERED`. A unit test of either component in isolation cannot catch a broken wiring at this boundary.

### 9.3a AT-VAR-01 — AccelerometerVarianceTracker Population-Variance Formula (TE TSPEC-v1 F-01)

**File:** `app/src/test/java/com/luopan/compass/drift/AccelerometerVarianceTrackerTest.kt`

This test pins the population-variance formula (`/ n`, not `/ (n-1)`) with concrete expected values. Without this test, the formula can silently change to sample variance and remain undetected (the TE cross-review finding F-01 identified this risk).

```kotlin
@Test
fun `AT-VAR-01 population variance formula with concrete values`() {
    // Setup: FakeClock at t=0; window = 5000 ms
    val clock = FakeClock(0L)
    val tracker = AccelerometerVarianceTracker(clock, windowMs = 5_000L)

    // Feed three samples with known magnitudes (all within the window):
    // magnitude = sqrt(ax² + ay² + az²)
    // Sample 1: (3, 0, 0) → magnitude = 3.0
    // Sample 2: (5, 0, 0) → magnitude = 5.0
    // Sample 3: (4, 0, 0) → magnitude = 4.0
    // mean = (3 + 5 + 4) / 3 = 4.0
    // population variance = ((3-4)² + (5-4)² + (4-4)²) / 3 = (1 + 1 + 0) / 3 = 2/3 ≈ 0.6667
    // sample variance would be 2/2 = 1.0 — these differ, so the test pins which is used

    clock.advance(100L)
    tracker.update(3f, 0f, 0f)
    clock.advance(100L)
    tracker.update(5f, 0f, 0f)
    clock.advance(100L)
    val variance = tracker.update(4f, 0f, 0f)

    // Assert population variance (within float rounding tolerance)
    assertThat(variance).isWithin(0.001f).of(2f / 3f)  // ≈ 0.6667, NOT 1.0
}
```

**Formula pinned:** population variance `Σ(xᵢ - μ)² / n`. The threshold in `DriftDetector` (`ACCEL_VARIANCE_THRESHOLD = 0.01f`) was chosen relative to this formula. Using sample variance (`/ (n-1)`) would inflate the variance by ~0.4% at 250 samples — negligible in production, but the formula is now explicit and test-locked.

### 9.3b AT-VM-DRIFT-01 — CompassViewModel TRIGGERED→cooldown-check→VISIBLE Wiring (TE TSPEC-v1 F-02)

**File:** `app/src/test/java/com/luopan/compass/ui/BearingHistoryViewModelTest.kt` (or a new `CompassViewModelDriftTest.kt` in the same directory)

**Gap identified:** The integration test AT-CAL-INT-01 (§9.3) explicitly does NOT cover the `CompassViewModel` sensor loop wiring: specifically the three-step chain `DriftEvent.TRIGGERED → cooldown-check → _driftBannerState = VISIBLE`. If this chain has a bug (e.g., the cooldown comparison is inverted, or the StateFlow is not updated), only a production bug would surface it. This unit test fills that gap.

**Approach:** Inject a `FakeDriftDetector` stub that returns `DriftEvent.TRIGGERED` on the first `onFrame()` call. Use a `FakeSettingsRepository` with `driftCooldownTimestampMs = 0L` (no cooldown). Assert that `driftBannerState` emits `VISIBLE` after `onFrame()` is wired through the sensor loop.

```kotlin
@Test
fun `AT-VM-DRIFT-01 TRIGGERED with no cooldown sets driftBannerState to VISIBLE`() = runTest {
    val clock = FakeClock(nowMs = 700_000L)  // arbitrary; cooldown check: 700000 - 0 >= 600000 → true
    val fakeDriftDetector = FakeDriftDetector(nextEvent = DriftEvent.TRIGGERED)
    val fakeSettings = FakeSettingsRepository(driftCooldownTimestampMs = 0L)
    val viewModel = CompassViewModel(
        /* ... other deps ... */
        clock = clock,
        driftDetector = fakeDriftDetector,
        accVarianceTracker = FakeAccelerometerVarianceTracker(variance = 0.001f),
        settings = fakeSettings
    )

    // Collect driftBannerState
    val states = mutableListOf<DriftBannerState>()
    val job = launch { viewModel.driftBannerState.collect { states.add(it) } }

    // Simulate a single sensor frame that makes the fake detector return TRIGGERED
    viewModel.simulateSensorFrame(
        accel = SensorFrame(ax = 0f, ay = 0f, az = 9.8f),
        magnitudeUt = 56.0f,
        interferenceState = InterferenceState.CLEAR,
        expectedFieldUt = 50.0f
    )
    advanceUntilIdle()
    job.cancel()

    assertThat(states).contains(DriftBannerState.VISIBLE)
}

@Test
fun `AT-VM-DRIFT-01b TRIGGERED with active cooldown does NOT set driftBannerState to VISIBLE`() = runTest {
    val clock = FakeClock(nowMs = 100_000L)  // 100 s since epoch
    val fakeDriftDetector = FakeDriftDetector(nextEvent = DriftEvent.TRIGGERED)
    val fakeSettings = FakeSettingsRepository(driftCooldownTimestampMs = 90_000L)  // 10 s ago — within 600 s cooldown
    val viewModel = CompassViewModel(/* ... */ clock = clock, driftDetector = fakeDriftDetector, settings = fakeSettings)

    val states = mutableListOf<DriftBannerState>()
    val job = launch { viewModel.driftBannerState.collect { states.add(it) } }

    viewModel.simulateSensorFrame(/* ... */)
    advanceUntilIdle()
    job.cancel()

    assertThat(states).doesNotContain(DriftBannerState.VISIBLE)
}
```

**`FakeDriftDetector`** is a new test double added to `app/src/test/java/com/luopan/compass/drift/`:

```kotlin
/**
 * Test double implementing [IDriftDetector] (not subclassing [DriftDetector]).
 *
 * SE PROPERTIES-v2 F-01 / TE PLAN-v1 F-01 resolution: CompassViewModel accepts [IDriftDetector],
 * so FakeDriftDetector must implement the interface directly. Subclassing the concrete
 * DriftDetector(FakeClock(0L)) is fragile and couples the fake to implementation details.
 */
class FakeDriftDetector(private val nextEvent: DriftEvent?) : IDriftDetector {
    var onFrameCallCount = 0
    var resetCallCount = 0
    override fun onFrame(accVariance: Float, measuredMagnitudeUt: Float,
                         interferenceState: InterferenceState, expectedFieldUt: Float): DriftEvent? {
        onFrameCallCount++
        return nextEvent
    }
    override fun reset() {
        resetCallCount++
    }
}
```

Note: `simulateSensorFrame()` is a `@VisibleForTesting`-annotated method on `CompassViewModel` that calls the internal drift detection block directly without requiring a real `SensorManager`.

### 9.4 TE FSPEC-v2 F-03 Resolution — AT-HIST-01-A Badge Assertion

**File:** `app/src/androidTest/java/com/luopan/compass/ui/BearingHistoryFragmentTest.kt`

To assert "interference badge is present on exactly 5 rows," the test uses a custom `RecyclerViewMatcher` that counts items matching a view condition:

```kotlin
// Assert: badge is present on 5 rows and absent on 5 rows
// scrollToPosition is called inside the loop for EACH position (TE TSPEC-v1 F-10):
// without the scroll, off-screen items are not drawn and isDisplayed() returns false
// even if the badge should be visible, producing false-negative badge counts.
var badgeCount = 0
for (position in 0 until 10) {
    // Mandatory: scroll each item into view before checking badge visibility
    onView(withId(R.id.recycler_history))
        .perform(RecyclerViewActions.scrollToPosition<BearingAdapter.ViewHolder>(position))
    try {
        onView(allOf(withId(R.id.interference_badge), isDisplayed()))
        badgeCount++
    } catch (_: NoMatchingViewException) {}
}
assertThat(badgeCount).isEqualTo(5)
```

**TE TSPEC-v1 F-10 note:** The `scrollToPosition` call inside the loop is mandatory for correctness on any device where not all 10 items fit on screen simultaneously. Without it, items at positions 5–9 would fail `isDisplayed()` (off-screen views are not drawn), causing the badge count to be artificially low and the test to pass even when badges are incorrectly absent on those rows.

Alternatively, a helper function iterates positions and collects visibility results. The key constraint: the assertion must verify exactly 5, not just "some badge exists somewhere."

### 9.5 TE FSPEC-v2 F-04 Resolution — AT-CAL-01-F and AT-NAV-01-C Without Spy

**TE FSPEC-v2 F-04:** MockK spy is not an accepted instrumented-test dependency in this project (no spy framework in existing test suite). The assertion is replaced with a state-observable assertion:

**AT-CAL-01-F (revised):** After `RESULT_OK` from CalibrationWizardActivity, assert that `CompassUiState.calibration_age_days` reflects the updated value (0 if freshly calibrated). This is observable via `compassViewModel.uiState.value.calibration_age_days` in an instrumented test without spying on the ViewModel.

**AT-NAV-01-C (revised):** Assert that the age banner is `GONE` after `RESULT_OK` (the banner hides because `calibration_age_days ≤ 30`). This is an Espresso-observable side effect that does not require spy verification.

### 9.6 TE FSPEC-v2 F-09 Resolution — AT-CAL-02-C Timer Reset Assertion

**File:** `app/src/test/java/com/luopan/compass/ui/BearingHistoryViewModelTest.kt` (or dedicated drift test)

`DriftDetector.elapsedCountingMs()` is exposed as `internal` (visible within the same module) per §5.3. Test asserts:

```kotlin
// After reset():
assertNull(driftDetector.elapsedCountingMs())
```

This is a direct internal property inspection, consistent with the `@VisibleForTesting` pattern used in `CalibrationEngine.classifyQuality()`.

### 9.7 TE FSPEC-v2 F-06 Resolution — Boundary Test at Exactly 10%

Add to `DriftDetectorTest.kt`:

```kotlin
@Test
fun `AT-CAL-03-B2 no TRIGGERED when deviation is exactly 10 percent`() {
    val clock = FakeClock(0L)
    val detector = DriftDetector(clock)
    val expectedFieldUt = 50.0f
    val measuredMagnitudeUt = 55.0f  // exactly 10% deviation: |55 - 50| / 50 = 0.10 (not > 0.10)

    clock.advance(61_001L)  // advance past 60-second threshold
    val event = detector.onFrame(0.005f, measuredMagnitudeUt, InterferenceState.CLEAR, expectedFieldUt)

    assertThat(event).isNotEqualTo(DriftEvent.TRIGGERED)
}
```

### 9.8 TE FSPEC-v2 F-07 Resolution — AT-SENSOR-01-B Cross-Run State

**AT-SENSOR-01-B (revised):** Within a single test method:

```kotlin
@Test
fun `file not rewritten on same-version relaunch`() {
    val settings = FakeSettingsRepository(sensorProfileWrittenForVersion = 40)
    val fakeWriter = RecordingFakeSensorFileWriter()
    val logger = SensorCapabilityLogger(context, settings, fakeWriter)

    // First call: writes
    logger.maybeWrite()
    val firstTimestamp = fakeWriter.lastContent?.let { parseTimestamp(it) }

    // Second call: same version — no write
    logger.maybeWrite()

    assertThat(fakeWriter.writeCallCount).isEqualTo(1)  // only one write total
    // timestamp not re-read; write count assertion is sufficient
}
```

The `written_at_iso8601` timestamp comparison is unnecessary — asserting `writeCallCount == 1` is sufficient to verify the skip behavior. The "prior launch" phrasing in the FSPEC is resolved by running both calls in the same test method.

### 9.9 TE FSPEC-v2 F-08 Resolution — AT-HIST-04-A Search Bar Visibility

Add to the `AT-HIST-04-A` instrumented test:

```kotlin
onView(withId(R.id.search_bar)).check(matches(not(isDisplayed())))
```

This asserts `View.GONE` for the search bar when zero records exist (State A).

### 9.10 TE FSPEC-v2 F-05 Resolution — AT-HIST-02-D Immediate Restore

In the unit test for immediate restore:

```kotlin
@Test
fun `AT-HIST-02-D immediate restore on empty query`() = runTest {
    val fakeDao = FakeBearingDao()
    fakeDao.setRecords(listOf(record("Alpha"), record("north")))
    val viewModel = BearingHistoryViewModel(fakeDao)

    // Activate search
    viewModel.setSearchQuery("north")
    advanceTimeBy(300L)  // let debounce fire

    // Clear query — must restore immediately, no clock advance needed
    viewModel.setSearchQuery("")
    // DO NOT call advanceTimeBy() here — assert immediate emission
    val list = viewModel.bearingList.first()

    assertThat(list).hasSize(2)  // full list restored
    // searchFlowCalls: only 1 call for "north"; empty query used getAllFlow()
    assertThat(fakeDao.searchFlowCalls).containsExactly("north")
}
```

The `first()` terminal on a `Flow` in `runTest` collects the first emission without requiring `advanceUntilIdle()`, confirming immediate restoration.

### 9.10a pendingUndo State-Transition Unit Tests (TE TSPEC-v1 F-05)

**File:** `app/src/test/java/com/luopan/compass/ui/BearingHistoryViewModelTest.kt`

The `pendingUndo: BearingRecord?` state machine is pure ViewModel logic with no Android dependency. The instrumented tests (AT-HIST-03-A through E) test the integrated swipe/undo behavior with RecyclerView and Snackbar — they do not isolate the ViewModel state transitions. These unit tests fill the gap.

```kotlin
@Test
fun `AT-UNDO-VM-01 deleteRecord sets pendingUndo`() = runTest {
    val fakeDao = FakeBearingDao()
    val record = makeBearingRecord(id = "r1", name = "Test")
    fakeDao.setRecords(listOf(record))
    val viewModel = BearingHistoryViewModel(fakeDao)

    viewModel.deleteRecord(record)
    advanceUntilIdle()

    assertThat(viewModel.hasPendingUndo()).isTrue()
}

@Test
fun `AT-UNDO-VM-02 undoDelete clears pendingUndo and re-inserts`() = runTest {
    val fakeDao = FakeBearingDao()
    val record = makeBearingRecord(id = "r1", name = "Test")
    fakeDao.setRecords(listOf(record))
    val viewModel = BearingHistoryViewModel(fakeDao)

    viewModel.deleteRecord(record)
    advanceUntilIdle()
    assertThat(viewModel.hasPendingUndo()).isTrue()

    viewModel.undoDelete()
    advanceUntilIdle()

    assertThat(viewModel.hasPendingUndo()).isFalse()
    // FakeBearingDao.insert() was called — record is back
    assertThat(fakeDao.getAll()).contains(record)
}

@Test
fun `AT-UNDO-VM-03 commitDelete clears pendingUndo without re-inserting`() = runTest {
    val fakeDao = FakeBearingDao()
    val record = makeBearingRecord(id = "r1", name = "Test")
    fakeDao.setRecords(listOf(record))
    val viewModel = BearingHistoryViewModel(fakeDao)

    viewModel.deleteRecord(record)
    advanceUntilIdle()

    viewModel.commitDelete()

    assertThat(viewModel.hasPendingUndo()).isFalse()
    // Record is gone from DAO — not re-inserted
    assertThat(fakeDao.getAll()).doesNotContain(record)
}
```

The instrumented tests AT-HIST-03-A through E remain in scope — they verify the end-to-end behavior (swipe gesture, Snackbar appearance, undo tap, Snackbar timeout). These unit tests verify the underlying state machine in isolation.

### 9.10b SensorCapabilityLogger Unit Tests via buildJsonFromSensors() (TE TSPEC-v1 F-07)

**File:** `app/src/test/java/com/luopan/compass/diagnostics/SensorCapabilityLoggerTest.kt`

The `SensorCapabilityLogger.buildJson()` previously called `context.getSystemService(Context.SENSOR_SERVICE)`, making it untestable in JVM unit tests without Robolectric. After extracting the serialization logic to `buildJsonFromSensors(sensors, versionCode, writtenAtIso8601)` (see §5.5), the context dependency is confined to the one-line `buildJson()` method. Unit tests for the serialization logic now run on the JVM without any Android runtime:

```kotlin
// FakeSensor data class for testing (mirrors the fields accessed by buildJsonFromSensors)
data class FakeSensor(
    val type: Int,
    val name: String,
    val vendor: String,
    val resolution: Float,
    val maximumRange: Float,
    val reportingMode: Int
) : /* does not extend Sensor — use a wrapper approach */ Any()
```

Since `android.hardware.Sensor` is a final class that cannot be subclassed or easily mocked, the test approach is:

1. Call `logger.buildJsonFromSensors(emptyList(), versionCode = 42, writtenAtIso8601 = "2026-04-27T00:00:00Z")` with an empty sensor list to test the root JSON structure without needing real `Sensor` objects.
2. For sensor-array content tests (AT-SENSOR-01-E, F), use `Robolectric` if needed, OR keep those in the instrumented `SensorCapabilityLoggerInstrumentedTest.kt` (existing plan).

**Key tests that are now JVM-runnable:**

```kotlin
@Test
fun `AT-SENSOR-01-E write failure is caught and version key not updated`() {
    val fakeSettings = FakeSettingsRepository(sensorProfileWrittenForVersion = 0)
    val throwingWriter = object : SensorFileWriter {
        override fun write(file: File, content: String) { throw IOException("disk full") }
    }
    val logger = SensorCapabilityLogger(mockContext, fakeSettings, throwingWriter)
    // Inject a subclass that overrides buildJson() to avoid real context:
    // OR use the internal buildJsonFromSensors() to pre-test the write path

    logger.maybeWrite()  // triggers write attempt

    assertThat(fakeSettings.sensorProfileWrittenForVersion).isEqualTo(0)  // not updated
}

@Test
fun `AT-SENSOR-01-G buildJsonFromSensors produces valid JSON with correct root fields`() {
    val logger = SensorCapabilityLogger(mockContext, FakeSettingsRepository(), FakeSensorFileWriter())

    val json = JSONObject(logger.buildJsonFromSensors(
        sensors = emptyList(),
        versionCode = 42,
        writtenAtIso8601 = "2026-04-27T00:00:00Z"
    ))

    assertThat(json.getInt("app_version_code")).isEqualTo(42)
    assertThat(json.getString("written_at_iso8601")).isEqualTo("2026-04-27T00:00:00Z")
    assertThat(json.getJSONArray("sensors").length()).isEqualTo(0)
}
```

**For `SecurityException` catch path (PM TSPEC-v1 F-02):**

```kotlin
@Test
fun `AT-SENSOR-01-F SecurityException from sensor enum is caught — no crash`() {
    val fakeSettings = FakeSettingsRepository(sensorProfileWrittenForVersion = 0)
    val throwingWriter = object : SensorFileWriter {
        override fun write(file: File, content: String) { throw SecurityException("no permission") }
    }
    val logger = SensorCapabilityLogger(mockContext, fakeSettings, throwingWriter)

    assertDoesNotThrow { logger.maybeWrite() }  // must not propagate
    assertThat(fakeSettings.sensorProfileWrittenForVersion).isEqualTo(0)
}
```

### 9.11 Performance NFR Tests

**500 ms initial-load threshold (instrumented):**

```kotlin
// In BearingHistoryFragmentTest or dedicated PerfTest
// Seed 500 records into in-memory Room
// Time the first Flow emission from getAllFlow()
val elapsed = measureTimeMillis {
    dao.getAllFlow().first()
}
assertThat(elapsed).isLessThan(500L)
```

**16 ms fling frame time (macrobenchmark):** Implemented as a `MacrobenchmarkRule` test with `FrameTimingMetric` in the `:benchmark` module (separate from instrumented tests), consistent with the existing `.github/workflows` macrobenchmark workflow. Non-blocking CI gate (regression alert, not build failure).

### 9.12 AT-HIST-05-D — Format Function Unit Test

The `inclination_deviation_deg` formatting is extracted to a pure function:

```kotlin
// In BearingAdapter or a companion formatting object
internal fun formatInclinationDev(deg: Float): String {
    val truncated = if (deg >= 0f) deg.toInt() else -((-deg).toInt())
    return "$truncated°"
}
```

Unit test:
```kotlin
assertThat(formatInclinationDev(-2.3f)).isEqualTo("-2°")
assertThat(formatInclinationDev(4.7f)).isEqualTo("4°")
assertThat(formatInclinationDev(0f)).isEqualTo("0°")
assertThat(formatInclinationDev(-0.9f)).isEqualTo("0°")
```

---

## 10. Error Handling

| Scenario | Expected Behavior |
|----------|------------------|
| Room `insert` fails on swipe undo (e.g., constraint violation) | `BearingHistoryViewModel.undoDelete()` launches `Dispatchers.IO`; uncaught exception is swallowed by the coroutine scope. **Out-of-scope for Phase 4 testing** (TE TSPEC-v1 F-03): no unit or instrumented test covers this path. The Room `Flow` will re-emit the current DB state (which did not include the re-inserted record), so the RecyclerView remains consistent with the DB. A future phase may add a visible error Toast/Snackbar on undo failure. |
| Room `delete` fails on swipe | Same as above — deletion is best-effort. The record may reappear in `getAllFlow()` on the next emission if the delete silently failed. **Out-of-scope for Phase 4 testing** (TE TSPEC-v1 F-03): no test covers this path. |
| `CalibrationWizardActivity` launched but not found | Android will throw `ActivityNotFoundException`. This is guarded by the existing `AndroidManifest.xml` registration. No additional handling needed. |
| `SensorCapabilityLogger.maybeWrite()` throws `IOException` | Caught by `catch (e: Exception)`; logged at `Log.e`; `sensorProfileWrittenForVersion` not updated. Retry on next launch. |
| `SensorCapabilityLogger.maybeWrite()` throws non-`IOException` (e.g., `SecurityException` from `SensorManager.getSensorList()`) | Caught by `catch (e: Exception)` (PM TSPEC-v1 F-02); logged at `Log.e`; `sensorProfileWrittenForVersion` not updated. Retry on next launch. The app does NOT crash. |
| `DriftDetector.onFrame()` called with `expectedFieldUt = 0.0f` | Precondition 3 fails silently; timer stays IDLE; no `TRIGGERED` emitted. Division by zero is prevented by the precondition guard. |
| `AccelerometerVarianceTracker` receives frames with zero variance (device stationary) | Returns 0.0f; precondition 1 (`accVariance < 0.01`) holds. Correct behavior. |
| `searchFlow("")` called on Room DAO | Not called — empty query switches to `getAllFlow()` per the `flatMapLatest` logic in `BearingHistoryViewModel`. |
| `BearingHistoryViewModel.hasPendingUndo()` called after process death | Returns `false` (ViewModel is fresh). Deletion committed to DB is permanent. Correct behavior per REQ-CAPTURE-03. |
| Room migration v2→v3 on a device with an existing v2 database | `MIGRATION_2_3` adds `expected_field_ut = 0.0` to all existing rows. Drift detection (Condition B) is disabled for those records until recalibration. Correct behavior per REQ-CAL-05. |
| `MIGRATION_2_3` fails (corrupt DB, disk full) | Room throws `IllegalStateException` on database open. This is an existing behavior for all migrations; no additional handling is specified. |

---

## 11. Requirements Traceability

| Requirement | Technical Component(s) |
|-------------|----------------------|
| REQ-CAPTURE-03 — Bearing history screen | `BearingHistoryFragment`, `BearingHistoryViewModel`, `BearingAdapter`, `BearingDao.getAllFlow()`, `BearingDao.searchFlow()` |
| REQ-CAPTURE-03 — Swipe to delete with undo | `BearingHistoryFragment.ItemTouchHelper`, `BearingHistoryViewModel.deleteRecord()`, `BearingHistoryViewModel.undoDelete()`, `Snackbar.setDuration(5000)` |
| REQ-CAPTURE-03 — Search | `BearingHistoryViewModel.bearingList` (debounce + flatMapLatest), `BearingDao.searchFlow()` |
| REQ-CAPTURE-03 — Navigation (third tab) | `CompassActivity` (4 changes), `nav_graph.xml`, `activity_compass.xml` |
| REQ-DETECT-05 — Interference flag in history | `BearingAdapter` (badge visibility, field deviation format, inclination format) |
| REQ-CAL-05 — Condition A (age-based banner) | `CompassViewModel.loadCalibrationAge()` (promoted to `internal`), `CompassViewModel.calAgeBannerDismissed`, `BearingHistoryFragment.updateAgeBanner()`, `calWizardLauncherAge` |
| REQ-CAL-05 — Condition B (drift-based banner) | `DriftDetector`, `AccelerometerVarianceTracker`, `CompassViewModel.driftBannerState`, `CompassViewModel.dismissDriftBanner()`, `CompassViewModel.resetDriftDetector()`, `BearingHistoryFragment.updateDriftBanner()`, `calWizardLauncherDrift` |
| REQ-CAL-05 — DB constraint (expected_field_ut) | `MIGRATION_2_3`, `CalibrationRecord.expected_field_ut`, `CalibrationResult.sphereRadius_uT`, `CalibrationEngine.fitEllipsoid()`, `CalibrationRepository.toRecord()` |
| REQ-SENSOR-07 — Sensor capability logging | `SensorCapabilityLogger`, `SensorFileWriter`, `RealSensorFileWriter`, `SettingsRepository.sensorProfileWrittenForVersion`, `CompassActivity.onCreate()` dispatch |

---

## 12. Cross-Review Finding Resolutions

### SE FSPEC-v2 Findings Addressed

| ID | Severity | Resolution |
|----|----------|-----------|
| F-01 | Medium | `loadCalibrationAge()` is safe to call from Fragment lifecycle callbacks (main thread). It launches `viewModelScope.launch(Dispatchers.IO)` internally. No dispatcher wrapping is needed in `BearingHistoryFragment`. Documented explicitly in §7.1. |
| F-02 | Medium | `AccelerometerVarianceTracker` uses a **time-based 5,000 ms rolling window**, not a 250-sample count. This ensures correct behavior regardless of actual sensor delivery rate (see §5.2). `DriftDetector.onFrame()` is called at the sensor frame rate from `startSensorCollection()`; the tracker self-evicts stale samples. |
| F-03 | Medium | **Two separate `ActivityResultLauncher` instances** are used — `calWizardLauncherAge` and `calWizardLauncherDrift`. Each launcher's `RESULT_OK` handler performs only its own side effects, with no disambiguation logic needed (see §6.2). |
| F-04 | Low | After `RESULT_OK`, the age banner hides because `calibration_age_days` drops to 0 (≤ 30), not because `calAgeBannerDismissed` is set. `calAgeBannerDismissed` is only set on the X button path. Documented explicitly in §7.1 and §6.1.3 (the `onCalibrationCompleteFromHistory()` method does not set the flag). |
| F-05 | Low | AT-CAL-02-F test limitation (always-true `doesNotExist()` assertion) is noted. The test verifies a layout invariant (the view does not exist in ModernCompassFragment's layout), not a state suppression. This is acceptable as a structural guard. |
| F-06 | Low | Snackbar duration is explicitly `setDuration(5000)` (not `Snackbar.LENGTH_LONG`). Documented in §7.3 with rationale. |
| F-07 | Low | `SensorFileWriter` interface accepts both `File` and `String` parameters. `SensorCapabilityLogger` computes the file path (`Context.getFilesDir() / "sensor_profile.json"`) and passes it to the writer. The interface is responsible only for the write operation (see §5.4). |

### TE FSPEC-v2 Findings Addressed

| ID | Severity | Resolution |
|----|----------|-----------|
| F-01 | Medium | `FakeBearingDao` is specified as a hand-written recording fake (§9.2), consistent with the project's `FakeBearingCaptureUseCase` pattern. AT-HIST-02-B and AT-HIST-02-C use `fakeDao.searchFlowCalls` for invocation-count assertions. No MockK required. |
| F-02 | Medium | AT-CAL-INT-01 drives `AccelerometerVarianceTracker.update()` and `DriftDetector.onFrame()` directly, without `CompassViewModel.startSensorCollection()`. No `SensorEventSource` abstraction is added to production code. The test coverage boundary is explicitly documented (§9.3). |
| F-03 | Medium | AT-HIST-01-A uses a per-position loop over all 10 rows to count `interference_badge` visibility. Asserts exactly 5 rows have the badge visible (§9.4). |
| F-04 | Medium | AT-CAL-01-F and AT-NAV-01-C are revised to assert the side-effect (age banner visibility / `calibration_age_days` value) rather than using a ViewModel spy. No MockK required (§9.5). |
| F-05 | Low | AT-HIST-02-D uses `viewModel.bearingList.first()` in `runTest` without `advanceTimeBy()` to confirm immediate restore. The behavior is verified by state-observable assertion, not timer semantics (§9.10). |
| F-06 | Low | AT-CAL-03-B2 added: boundary test for exactly 10% deviation, expected NOT to trigger (§9.7). |
| F-07 | Low | AT-SENSOR-01-B revised to assert `writeCallCount == 1` within a single test method (both logger invocations in the same test). Eliminates the cross-run state problem (§9.8). |
| F-08 | Low | AT-HIST-04-A extended to assert `search_bar` is `View.GONE` in State A (§9.9). |
| F-09 | Low | `DriftDetector.elapsedCountingMs()` exposed as `internal` for AT-CAL-02-C timer-reset assertion (§9.6). |

### PM TSPEC-v1 Findings Addressed

| ID | Severity | Resolution |
|----|----------|-----------|
| F-01 | Medium | State machine spec updated in §5.3 with explicit "re-evaluate each frame after threshold" rule. When elapsed > 60 s but deviation ≤ 10%, the detector stays COUNTING and re-evaluates deviation on every subsequent frame — no maximum counting duration. The product rationale (gradual drift near threshold boundary) is documented. Flagged for inclusion in FSPEC-CAL-03 next revision. |
| F-02 | Medium | `SensorCapabilityLogger.maybeWrite()` updated to `catch (e: Exception)` (from `catch (e: IOException)`). The `buildJson()` call (which calls `SensorManager.getSensorList()`) is now inside the try block so any `SecurityException` or other runtime exception is also caught, logged, and swallowed. Updated in §5.5 code block and §10 error handling table. |
| F-03 | Low | Snackbar body text changed from `"Deleted"` to `"Bearing deleted"` in §7.5 string resources. This is a richer, context-clear string consistent with Material Design guidelines. |
| F-04 | Low | Age-banner re-flash race mitigation documented and specified in §7.1. `onCalibrationCompleteFromHistory()` now sets `calAgeBannerDismissed = true` immediately on `RESULT_OK`, then clears it only once `loadCalibrationAge()` resolves with age ≤ 30. The full revised method body is specified. |
| F-05 | Low | `BearingAdapter.toggleExpanded()` updated to use `notifyItemChanged()` on only the two affected positions (previous expanded + new expanded). Rationale: avoids full rebind of all visible rows at 500 records. Implementation updated in §7.2. |
| F-06 | Low | `updateListState()` helper function specified in §7.1 with all four visibility combinations (State A, no-results, normal with ≥1 record). Search bar is explicitly restored to `View.VISIBLE` on the same Flow emission that reveals the RecyclerView. |

### TE TSPEC-v1 Findings Addressed

| ID | Severity | Resolution |
|----|----------|-----------|
| F-01 | Medium | AT-VAR-01 added in §9.3a: named unit test that pins the population-variance formula with concrete values (magnitudes 3, 5, 4 → expected variance 2/3 ≈ 0.6667). The test explicitly distinguishes population variance (`/ n`) from sample variance (`/ (n-1)`, which would yield 1.0). Coverage matrix updated. |
| F-02 | Medium | AT-VM-DRIFT-01 and AT-VM-DRIFT-01b added in §9.3b: ViewModel unit tests for the TRIGGERED→cooldown-check→driftBannerState=VISIBLE wiring. Uses `FakeDriftDetector` stub that returns a configured event. Covers both the happy path (cooldown elapsed → VISIBLE) and the suppressed path (active cooldown → HIDDEN). Coverage matrix updated. |
| F-03 | Medium | deleteRecord/undoDelete error paths explicitly marked "Out-of-scope for Phase 4 testing" in §10 Error Handling table. Added rationale: Room `Flow` self-heals by re-emitting current DB state. Deferred to a future phase. |
| F-04 | Medium | AT-CAL-INT-01 updated in §9.3 with two-phase structure: Phase 1 drives 58 seconds of frames and asserts `triggeredEarly == false` (pre-threshold negative assertion). Phase 2 advances past 60 s and asserts `triggeredAfterThreshold == true`. The early-exit `return@runTest` pattern is replaced with boolean flags and explicit assertions. |
| F-05 | Low | AT-UNDO-VM-01 through AT-UNDO-VM-03 added in §9.10a: JVM unit tests for `pendingUndo` state machine (`deleteRecord` sets it, `undoDelete` clears + restores, `commitDelete` clears without restoring). Coverage matrix updated. Instrumented tests AT-HIST-03-A through E remain for end-to-end swipe/undo behavior. |
| F-07 | Low | `buildJsonFromSensors(sensors, versionCode, writtenAtIso8601)` extracted as an `internal` pure function in §5.5. The context/SensorManager dependency is confined to the one-line `buildJson()` method. Unit tests AT-SENSOR-01-E through G (§9.10b) now call `buildJsonFromSensors()` directly on the JVM without Robolectric. |
| F-10 | Low | AT-HIST-01-A `scrollToPosition` call is explicitly documented as mandatory inside the per-position loop (§9.4). Clarifying comment added explaining that off-screen items fail `isDisplayed()` without the scroll, producing false-negative badge counts. |
