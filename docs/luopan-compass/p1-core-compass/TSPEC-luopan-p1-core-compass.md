# TSPEC-luopan-p1-core-compass
## Technical Specification — Phase 1: Core Compass

| Field | Value |
|-------|-------|
| **Document ID** | TSPEC-luopan-p1-core-compass |
| **Version** | 0.2-draft |
| **Status** | Draft |
| **Date** | 2026-04-23 |
| **Phase** | 1 of 5 |
| **Author** | Engineering |
| **Parent REQ** | [REQ-luopan-p1-core-compass.md](REQ-luopan-p1-core-compass.md) |
| **Parent FSPEC** | [FSPEC-luopan-p1-core-compass.md](FSPEC-luopan-p1-core-compass.md) |
| **Master REQ** | [REQ-luopan-compass.md](REQ-luopan-compass.md) |

> **Purpose of this document:** This TSPEC translates Phase 1 behavioral requirements into concrete technical design decisions. It is the authoritative implementation reference for developers: architecture, class names, data models, algorithm parameters, and all engineering choices are specified here. It does NOT invent product behavior — it specifies HOW to implement what the REQ and FSPEC define.

---

## §0 Requirements Traceability

*(PM-FIND-06)* This table maps every Phase 1 REQ ID from REQ §5 to its TSPEC section and the governing FSPEC ID(s). It is the authoritative cross-reference for reviewers verifying that TSPEC design decisions match the REQ and FSPEC.

| TSPEC Section | REQ-ID(s) | FSPEC-ID(s) |
|---|---|---|
| §2.1 — Sensor Registration | REQ-SENSOR-01, REQ-SENSOR-05, SE-05 | FSPEC-SENSOR-01 |
| §2.2 — Sensor Availability Decision Tree | REQ-SENSOR-01, REQ-SENSOR-03 | FSPEC-SENSOR-01 |
| §2.3 — SensorFrame Data Model | REQ-SENSOR-01, REQ-SENSOR-02 | FSPEC-SENSOR-01 |
| §2.4 — Raw Accel Low-Pass Filter | REQ-SENSOR-02 | FSPEC-SENSOR-01 |
| §2.5 — Power-Saving Mode Detection | REQ-NFR-02, REQ-NFR-03, SE-09 | FSPEC-SENSOR-04 |
| §2.6 — Rapid Rotation Detection | REQ-SENSOR-06 (§11.7) | FSPEC-SENSOR-03 |
| §2.7 — Stuck Sensor Detection | REQ-SENSOR-06 (§11.3) | FSPEC-SENSOR-03 |
| §3 — Sensor Fusion Engine | REQ-SENSOR-04, SE-06 | FSPEC-FUSION-01 |
| §3.4 — Quaternion-to-Heading | REQ-SENSOR-04, SE-06 | FSPEC-FUSION-01 |
| §4 — Calibration Engine | REQ-CAL-01, REQ-CAL-02, SE-07 | FSPEC-CAL-01, FSPEC-CAL-02 |
| §4.4 — Calibration Quality Classification | REQ §5.3, TE-04 | FSPEC-CAL-02 |
| §4.6 — Calibration Persistence | REQ-CAL-04, TE-07 | FSPEC-CAL-03 |
| §5 — Interference Detector | REQ-DETECT-01, REQ-DETECT-02, TE-11 | FSPEC-DETECT-01, FSPEC-DETECT-02 |
| §5.3 — Noise Spike Rejection | REQ-DETECT-04 | FSPEC-DETECT-01 |
| §5.4 — Interference State Machine | REQ-DETECT-01, REQ-DETECT-02, TE-11 | FSPEC-DETECT-01, FSPEC-DETECT-02 |
| §6 — Confidence Model | REQ §5.3, TE-04 | FSPEC-CONF-01 |
| §7 — Display Layer | REQ-DISPLAY-01, REQ-DISPLAY-02, REQ-DISPLAY-10 | FSPEC-DISPLAY-01 through FSPEC-DISPLAY-05 |
| §7.5 — Wake Lock | REQ-DISPLAY-10 | FSPEC-DISPLAY-05 |
| §8 — Localization | REQ-L10N-01 | FSPEC-L10N-01 |
| §9 — Persistence Layer | REQ-CAL-04, REQ-NFR-06 | FSPEC-CAL-03 |
| §10.1 — Latency | REQ-NFR-01, SE-09 | FSPEC-NFR-01 |
| §10.2 — Update Rate | REQ-NFR-02 | FSPEC-NFR-02 |
| §10.3 — Battery | REQ-NFR-03 | FSPEC-NFR-03 |
| §10.4 — SDK Version | REQ-NFR-04 | — |
| §10.5 — Offline Operation | REQ-NFR-05 | FSPEC-NFR-05 |
| §10.6 — APK Size | REQ-NFR-07 | — |

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Sensor Layer](#2-sensor-layer)
3. [Sensor Fusion Engine](#3-sensor-fusion-engine)
4. [Calibration Engine](#4-calibration-engine)
5. [Interference Detector](#5-interference-detector)
6. [Confidence Model](#6-confidence-model)
7. [Display Layer — Modern Mode](#7-display-layer--modern-mode)
8. [Localization](#8-localization)
9. [Persistence Layer](#9-persistence-layer)
10. [Non-Functional Requirements Implementation](#10-non-functional-requirements-implementation)
11. [Key Technical Risks and Mitigations](#11-key-technical-risks-and-mitigations)
12. [Android Manifest and Build Configuration](#12-android-manifest-and-build-configuration)
13. [Dependency Inventory](#13-dependency-inventory)

---

## 1. Architecture Overview

### 1.1 Module / Layer Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                         DISPLAY LAYER                               │
│  CompassActivity ──► CompassViewModel ──► CompassUiState (StateFlow)│
│  CompassRoseView / ComposeCanvas    TiltIndicatorView               │
│  ConfidenceBadgeView                InterferenceWarningBanner       │
└───────────────────────────────┬─────────────────────────────────────┘
                                │ observes StateFlow<CompassUiState>
┌───────────────────────────────▼─────────────────────────────────────┐
│                        DOMAIN / PIPELINE LAYER                      │
│  ConfidenceModel ◄── FusionEngine ◄──── SensorLayer                │
│       ▲                   ▲                   │ SensorFrame         │
│       │            CalibrationEngine          │ (raw events)        │
│       │                   │                   │                     │
│  InterferenceDetector ────┘          NoiseVarianceTracker          │
│       │                                       │                     │
│  SensorStateMonitor (stuck / rapid-rotation / power-save)          │
└───────────────────────────────┬─────────────────────────────────────┘
                                │ reads / writes
┌───────────────────────────────▼─────────────────────────────────────┐
│                       PERSISTENCE LAYER                             │
│  CalibrationRepository ──► Room DB (SQLCipher)                      │
│  SettingsRepository    ──► SharedPreferences (EncryptedSharedPrefs) │
│  LocationProvider      ──► LocationManager (NETWORK / PASSIVE)      │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.2 Android Component Mapping

| Module | Android Component | Rationale |
|--------|-------------------|-----------|
| `SensorLayer` | `SensorEventListener` registered/unregistered in `Activity.onStart()` / `Activity.onStop()` | REQ-SENSOR-05: unregister on background, register on foreground |
| `FusionEngine` | Plain Kotlin object, called on a dedicated `HandlerThread` (`sensorThread`) | CPU-bound math; must not block main thread |
| `CalibrationEngine` | Plain Kotlin object with `CoroutineScope(Dispatchers.Default)` for ellipsoid fitting | Fitting is CPU-intensive but short-lived |
| `InterferenceDetector` | Plain Kotlin object called from `sensorThread` | In-path with fusion; zero allocations in hot path |
| `ConfidenceModel` | Plain Kotlin object called from `sensorThread` | Stateless scorer |
| `CompassViewModel` | `AndroidViewModel` with `StateFlow<CompassUiState>` | Survives configuration changes; exposes UI state |
| `CalibrationRepository` | Repository class with Room DAO; accessed via `Dispatchers.IO` coroutine | Async storage, main-thread safe |
| `SettingsRepository` | Repository wrapping `EncryptedSharedPreferences` | Simple key-value; sync OK on main thread for reads |
| `CompassActivity` | Single `Activity`; no Fragments in Phase 1 | Simplest possible navigation structure |
| `CalibrationWizardActivity` | Separate `Activity` launched via `ActivityResultLauncher` using `registerForActivityResult(ActivityResultContracts.StartActivityForResult())` (modern Activity Result API, avoids deprecated `startActivityForResult`) | Isolation; easy back-stack management |

### 1.3 Data Flow Summary

```
SensorEvent (onSensorChanged, sensorThread)
  │
  ▼
SensorLayer.onFrame(SensorFrame)
  │
  ├──► NoiseVarianceTracker.update(mag_xyz) → variance: Float
  │
  ├──► InterferenceDetector.check(calibrated_mag_xyz) → InterferenceState
  │
  ├──► FusionEngine.update(SensorFrame) → FusionResult(heading, pitch, roll, ts)
  │
  └──► ConfidenceModel.compute(FusionResult, InterferenceState, CalibrationRecord?, variance)
         → OverallConfidence
  
  All outputs ──► CompassViewModel.updateState()
                    │  (emit on sensorThread; StateFlow conflates rapid updates)
                    ▼
              Main thread: CompassActivity observes StateFlow<CompassUiState>
                    │
                    ▼
              CompassRoseView.invalidate() at ≥20 Hz (Choreographer-gated)
```

### 1.4 Calibration Data Flow

```
CalibrationWizardActivity
  │ MagSample ring buffer (capacity 500)
  │
  ├──► Auto-complete gate: sampleCount ≥ 200 AND coverage ≥ 80% all axes AND rolling RMS < 1.0 µT
  │
  └──► "Done" tapped or auto-complete:
         CalibrationEngine.fit(samples: List<MagSample>) → CalibrationResult
              │
              └──► CalibrationRepository.save(CalibrationRecord)
                       (Room transaction, SQLCipher encrypted)
                              │
                              └──► CompassViewModel notified via SharedFlow or direct reload
```

---

## 2. Sensor Layer

### 2.1 SensorManager Registration

**Sensor probe sequence on `Activity.onStart()`:**

```kotlin
val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

// Magnetometer
val magSensor: Sensor? =
    sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED)
    ?: sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        ?.also { usingFallbackMag = true }

if (magSensor == null) {
    // Divert to no-magnetometer error screen (FSPEC-SENSOR-01)
    showNoMagnetometerScreen()
    return
}

// Accelerometer / Gravity
val gravitySensor: Sensor? =
    sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        ?.also { usingRawAccel = true }

// Gyroscope (optional)
val gyroSensor: Sensor? =
    sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    ?.also { hasGyroscope = true }
// If null: set hasGyroscope = false; cap confidence at MODERATE session-wide

sensorManager.registerListener(sensorListener, magSensor,
    SensorManager.SENSOR_DELAY_GAME, sensorHandler)
sensorManager.registerListener(sensorListener, gravitySensor,
    SensorManager.SENSOR_DELAY_GAME, sensorHandler)
gyroSensor?.let {
    sensorManager.registerListener(sensorListener, it,
        SensorManager.SENSOR_DELAY_GAME, sensorHandler)
}
```

**`SENSOR_DELAY_GAME`** delivers approximately 20 ms between events (~50 Hz for magnetometer, ~10 ms / ~100 Hz for accel and gyro on most devices). This is the target rate, not guaranteed.

**Unregistration on `Activity.onStop()`:**

```kotlin
sensorManager.unregisterListener(sensorListener)
```

Called in `onStop()`, not `onPause()`. Rationale: `onPause()` fires during transient focus loss (permission dialogs, notification overlays) without the app becoming invisible; unregistering there would interrupt compass delivery and violate REQ-SENSOR-05.

**`sensorHandler`:** A `Handler` created from a dedicated `HandlerThread` named `"SensorThread"`. All `SensorEventListener` callbacks fire on this thread, keeping the main thread free.

### 2.2 Sensor Availability Decision Tree

```
getDefaultSensor(TYPE_MAGNETIC_FIELD_UNCALIBRATED)
    │
    ├── non-null → use uncalibrated; usingFallbackMag = false
    │
    └── null → getDefaultSensor(TYPE_MAGNETIC_FIELD)
                   │
                   ├── non-null → usingFallbackMag = true
                   │              log "Fallback to TYPE_MAGNETIC_FIELD"
                   │              display "Limited calibration" badge
                   │
                   └── null → showNoMagnetometerScreen()
                               finish() or disable compass entirely

getDefaultSensor(TYPE_GYROSCOPE)
    │
    ├── non-null → hasGyroscope = true
    │
    └── null → hasGyroscope = false
               session-level Moderate cap active
               show advisory: "No gyroscope — heading may be less stable"
```

When `usingFallbackMag = true`:
- `SensorFrame.mag_bias_x/y/z` are set to 0.0f (unavailable).
- Hard-iron correction still runs with any persisted hard-iron offset; soft-iron correction is applied if available.
- The "Limited calibration" badge is shown in the UI (distinct from confidence badge).

### 2.3 SensorFrame Data Model

```kotlin
data class SensorFrame(
    val timestamp_ns: Long,           // SensorEvent.timestamp (elapsedRealtimeNanos)
    val mag_x: Float,                 // µT, device X axis
    val mag_y: Float,                 // µT, device Y axis
    val mag_z: Float,                 // µT, device Z axis
    val mag_bias_x: Float,            // µT, from TYPE_MAGNETIC_FIELD_UNCALIBRATED values[3..5]
    val mag_bias_y: Float,            // µT; 0.0f when using fallback TYPE_MAGNETIC_FIELD
    val mag_bias_z: Float,            // µT; 0.0f when using fallback TYPE_MAGNETIC_FIELD
    val accel_x: Float,               // m/s², device X axis (gravity or raw accel)
    val accel_y: Float,               // m/s², device Y axis
    val accel_z: Float,               // m/s², device Z axis
    val gyro_x: Float?,               // rad/s; null when TYPE_GYROSCOPE unavailable
    val gyro_y: Float?,               // rad/s; null when TYPE_GYROSCOPE unavailable
    val gyro_z: Float?,               // rad/s; null when TYPE_GYROSCOPE unavailable
)
```

Fields `gyro_x/y/z` are nullable. `FusionEngine` checks for null and uses the accelerometer-only (Madgwick accel-only) code path when they are null.

### 2.4 Raw Accelerometer Low-Pass Filter

When `usingRawAccel = true` (TYPE_GRAVITY not available, falling back to TYPE_ACCELEROMETER), a simple IIR low-pass filter isolates gravity from linear acceleration:

```kotlin
// α = 0.1 per REQ-SENSOR-02; lower α = more smoothing, higher latency
val alpha = 0.1f
gravityX = alpha * event.values[0] + (1 - alpha) * gravityX
gravityY = alpha * event.values[1] + (1 - alpha) * gravityY
gravityZ = alpha * event.values[2] + (1 - alpha) * gravityZ
```

`SensorFrame.accel_x/y/z` are populated with the filtered gravity values in this path.

### 2.5 Power-Saving Mode Detection

Implemented in `SensorRateMonitor`, a regular `class` (not singleton) running on `sensorThread`:

```kotlin
class SensorRateMonitor(
    windowMs: Long = 2000L,
    threshold: Float = 15.0f,
    timeSource: TimeSource,         // injectable for deterministic testing
) {
    private val eventTimestamps = ArrayDeque<Long>(capacity = 100)
    private val windowNs = windowMs * 1_000_000L

    fun onEvent(timestampNs: Long): Float {
        eventTimestamps.addLast(timestampNs)
        // Evict entries older than windowNs
        while (eventTimestamps.isNotEmpty() &&
               timestampNs - eventTimestamps.first() > windowNs) {
            eventTimestamps.removeFirst()
        }
        // Effective Hz = count / window_seconds
        return eventTimestamps.size / (windowNs / 1_000_000_000.0f)
    }
}
```

If `effectiveHz < 15.0f`, emit `SensorAdvisory.POWER_SAVING` to the UI (FSPEC-SENSOR-04). Advisory clears when `effectiveHz ≥ 15.0f` for ≥2 seconds.

Threshold: **15 Hz** (not 20 Hz) to tolerate brief delivery gaps without false positives. Advisory is informational only; the app does not alter sampling.

**Rationale for 15 Hz vs 20 Hz advisory threshold (PM-FIND-07):** The 15 Hz advisory trigger is intentionally set below the REQ-NFR-02 target of ≥20 Hz to tolerate brief delivery gaps (e.g., a single dropped frame at 50 ms intervals). Sustained sub-15 Hz delivery indicates a systemic rate reduction (OS power-saving mode), not a transient gap. The 15–20 Hz band is an acceptable transient degradation window. The advisory does NOT fire for momentary dips to 16–19 Hz; it only fires when the 2-second rolling average falls below 15 Hz.

### 2.6 Rapid Rotation Detection

Computed in `SensorStateMonitor` from gyroscope angular rate:

```kotlin
val angularRateDegPerSec = sqrt(
    (gyro_x ?: 0f).pow(2) +
    (gyro_y ?: 0f).pow(2) +
    (gyro_z ?: 0f).pow(2)
) * (180f / PI.toFloat())

// Entry into STABILIZING: rate > 180°/s sustained for > 0.5 s
// Exit from STABILIZING: rate < 10°/s sustained for ≥ 1.0 s
```

```kotlin
enum class SensorState {
    NORMAL,
    STABILIZING,  // rapid rotation in progress
    STUCK,        // sensor variance < 0.001 µT² for > 3 s, or {0,0,0} sample
}
```

When `SensorState.STABILIZING`, `OverallConfidence.STABILIZING` is emitted to the display layer and the confidence badge shows "Stabilizing…" (amber). This 4th enum value is display-only and does not participate in the 5-dimension scoring computation.

### 2.7 Stuck Sensor Detection

In `SensorStateMonitor`, evaluated on every magnetometer frame:

- **Immediate detection:** `mag_x == 0f && mag_y == 0f && mag_z == 0f` → emit `SensorState.STUCK` without waiting for the 3-second window.
- **Variance detection:** Population variance (denominator N) computed over the last min(N, 50) samples per axis (50 samples at 50 Hz ≈ 1 second of data; the 3-second badge requirement from REQ §11.3 is met because the detection window is checked on every incoming sample). STUCK condition requires **ALL three axes** to have population variance below `0.001f` µT². A sensor rotating around a single axis — producing zero variance on that axis but non-zero variance on the other two — is NOT considered stuck.
- **Recovery:** When all-axis variance recovers above `0.001f` for one full second, emit `SensorState.NORMAL`.

Stuck state freezes the last valid `FusionResult.heading_deg` in the display; `ConfidenceScore` is overridden to `SENSOR_ERROR` for display purposes (not a standard confidence dimension value).

---

## 3. Sensor Fusion Engine

### 3.1 Algorithm Choice: Madgwick Filter

**Selected algorithm:** Madgwick AHRS filter (Mahony and EKF were considered but not selected).

| Algorithm | CPU Cost | Complexity | Accuracy | Rationale |
|-----------|----------|------------|----------|-----------|
| Madgwick | Low | Low | ±1° feasible | Recommended. Single tuning parameter (β). No covariance matrix. Well-validated in embedded/mobile literature. |
| Mahony | Low | Low | ±1° feasible | Similar to Madgwick. Two parameters (Kp, Ki). Integral drift compensation. Would be an acceptable alternative; Madgwick is marginally simpler to tune. |
| EKF | High | High | ±0.5° feasible | Overkill for Phase 1. 15×15 state covariance propagation exceeds Phase 1 CPU budget on API 26 hardware. Deferred if Madgwick does not meet ±1° target on test devices. |

**Rationale for Madgwick:** The ±1° accuracy target under Good conditions is achievable with Madgwick on mid-range hardware without consuming more than ~2% CPU. The EKF's accuracy advantage (~0.5° vs ~1°) is not needed in Phase 1 (the target is ±1°, not sub-degree). The Madgwick filter is well-understood, has a reference Java implementation, and is tunable with a single β parameter.

**CI Proxy Test Entry Point (TE-F-13):** `FusionEngine` exposes a public `fun update(frame: SensorFrame)` method that accepts synthetic `SensorFrame` values directly, independent of `SensorLayer`. This is the entry point for the CI proxy test that injects a pre-recorded 30-second sensor sequence and asserts computed heading is within ±0.5° of ground truth (REQ §9, Scenario C / TE-06). `FusionEngine` has no direct dependency on `SensorLayer` — `SensorLayer` calls `FusionEngine.update()` on each sensor event, but `FusionEngine` does not reference `SensorLayer` in return.

### 3.2 Madgwick Filter Parameters

```kotlin
class MadgwickFilter(
    var beta: Float = 0.05f,  // Balance: 0.01 = slow convergence, low noise;
                               // 0.1 = fast convergence, more noise rejection lag
                               // Default 0.05 is standard for ≈50 Hz magnetometer
) {
    // Internal quaternion state (unit quaternion) — instance fields
    var q0: Float = 1.0f  // w component
    var q1: Float = 0.0f  // x component
    var q2: Float = 0.0f  // y component
    var q3: Float = 0.0f  // z component

    fun update(
        gx: Float, gy: Float, gz: Float,  // rad/s
        ax: Float, ay: Float, az: Float,  // m/s² (gravity)
        mx: Float, my: Float, mz: Float,  // µT (calibrated)
        dt: Float                          // seconds since last update
    )

    // Accelerometer-only update path (no gyroscope available)
    fun updateNoGyro(
        ax: Float, ay: Float, az: Float,
        mx: Float, my: Float, mz: Float,
        dt: Float
    )
}
```

`beta` is fixed at **0.05f** for Phase 1. Any future adjustment requires a product REQ change — it is NOT user-adjustable in Phase 1 and there is no scheduled slider in any current phase REQ.

**`MadgwickFilter` is a `class`, not an `object`** (TE-F-01): All quaternion state (`q0`, `q1`, `q2`, `q3`, `beta`) are instance fields. `FusionEngine` creates and holds the single live `MadgwickFilter()` instance for the production pipeline. Tests construct a fresh `MadgwickFilter()` instance per test case, ensuring no shared mutable state between parallel test runs.

The Madgwick filter re-normalizes the quaternion on every `update()` call (standard reference implementation behavior). This prevents quaternion drift from accumulated floating-point rounding error over long sessions.

### 3.3 FusionResult Data Model

```kotlin
data class FusionResult(
    val heading_deg: Double,    // [0.0, 360.0), magnetic heading from north
    val pitch_deg: Double,      // [-90.0, 90.0]
    val roll_deg: Double,       // [-180.0, 180.0]
    val tilt_deg: Double,       // departure from horizontal = sqrt(pitch² + roll²) for tilt indicator
    val timestamp_ns: Long,
)
```

### 3.4 Quaternion to Heading Extraction

Heading is extracted from the unit quaternion via a rotation matrix, not via naive Euler decomposition. This avoids gimbal lock at pitch = ±90° (vertical hold use case).

```kotlin
fun quaternionToHeadingDeg(q0: Float, q1: Float, q2: Float, q3: Float): Double {
    // Build 3x3 rotation matrix from quaternion
    // R = SensorManager.getRotationMatrixFromVector equivalent
    val R = FloatArray(9)
    R[0] = 1f - 2f * (q2*q2 + q3*q3)
    R[1] = 2f * (q1*q2 - q0*q3)
    R[2] = 2f * (q1*q3 + q0*q2)
    R[3] = 2f * (q1*q2 + q0*q3)
    R[4] = 1f - 2f * (q1*q1 + q3*q3)
    R[5] = 2f * (q2*q3 - q0*q1)
    R[6] = 2f * (q1*q3 - q0*q2)
    R[7] = 2f * (q2*q3 + q0*q1)
    R[8] = 1f - 2f * (q1*q1 + q2*q2)

    // Heading from R: azimuth = atan2(-R[6], R[0]) is NOT used (simple form, not gimbal-safe)
    // Use orientation = SensorManager.getOrientation(R, orientation) equivalent:
    // azimuth = atan2(R[1], R[4]) — gimbal-lock-safe via atan2 of two components of R column
    // This matches Android's SensorManager.getOrientation formula
    val azimuthRad = atan2(R[1].toDouble(), R[4].toDouble())
    val headingDeg = Math.toDegrees(azimuthRad)
    return ((headingDeg % 360.0) + 360.0) % 360.0  // normalize to [0, 360)
}
```

The heading remains `finite` (not NaN or Infinity) at all device orientations, including vertical hold. The `atan2` function is defined for all inputs except `(0, 0)`, which cannot occur when the quaternion is a unit quaternion.

**CI unit test requirement (TE-F-09):** Given accelerometer input simulating pitch = ±90° (device pointing straight up or straight down), `quaternionToHeadingDeg()` MUST return a finite value in `[0.0, 360.0)`. This test is part of the CI proxy test suite and is a non-negotiable gate for every build.

### 3.5 Tilt Computation

```kotlin
fun computeTiltDeg(pitch_deg: Double, roll_deg: Double): Double =
    sqrt(pitch_deg * pitch_deg + roll_deg * roll_deg)
```

This approximation is accurate to ~1° for tilt ≤ 45°. For large tilts the distortion is physically irrelevant (heading accuracy is already Poor at tilt > 20°).

### 3.6 Update Rate and Display Throttle

- **Fusion update rate:** Targeted 50 Hz from magnetometer callbacks. FusionEngine.update() is called on every magnetometer SensorEvent (the rate-limiter is the sensor hardware itself).
- **`CompassViewModel` update rate:** Gated by `Choreographer.FrameCallback` at the display frame rate (60 Hz on most devices). The ViewModel posts updates at ≤ display framerate but always at ≥ 20 Hz.
- **Throttle implementation:** A `Choreographer.FrameCallback` is registered in `onStart()` and unregistered in `onStop()`. Each frame callback reads the latest `FusionResult` from an `AtomicReference<FusionResult>` and emits it to the `StateFlow`. This decouples sensor rate from display rate without creating intermediate threads.

```kotlin
private val latestFusion = AtomicReference<FusionResult>()

// Called from sensorThread on every fusion update:
fun onFusionResult(result: FusionResult) {
    latestFusion.set(result)
}

// Called on main thread by Choreographer at display frame rate:
private val frameCallback = Choreographer.FrameCallback { frameTimeNs ->
    val fusion = latestFusion.get() ?: return@FrameCallback
    viewModelScope.launch(Dispatchers.Main.immediate) {
        _uiState.emit(buildUiState(fusion))
    }
    Choreographer.getInstance().postFrameCallback(this)
}
```

---

## 4. Calibration Engine

### 4.1 Sample Collection Data Model

```kotlin
data class MagSample(
    val x: Float,           // µT, raw uncalibrated
    val y: Float,
    val z: Float,
    val timestamp_ns: Long,
)
```

**Ring buffer:** `CalibrationSession` maintains a `CircularBuffer<MagSample>` of capacity 500. Newest samples overwrite oldest once full. The complete 500-sample buffer (or all samples if < 500) is used for the final ellipsoid fit.

**Rolling residual window:** The most recent 50 samples are used to compute the live residual RMS preview shown during collection. This 50-sample window gives fast feedback without the cost of a full fit on every new sample.

### 4.2 Coverage Computation

Per the Phase 1 Implementation Note SE-07 (the authoritative definition):

```kotlin
data class CoverageStats(
    val coverage_x: Float,  // 0.0 – 1.0
    val coverage_y: Float,
    val coverage_z: Float,
)

fun computeCoverage(samples: List<MagSample>): CoverageStats {
    val xRange = samples.maxOf { it.x } - samples.minOf { it.x }
    val yRange = samples.maxOf { it.y } - samples.minOf { it.y }
    val zRange = samples.maxOf { it.z } - samples.minOf { it.z }
    val totalRange = maxOf(xRange, yRange, zRange)

    return if (totalRange == 0f) {
        CoverageStats(0f, 0f, 0f)
    } else {
        CoverageStats(
            coverage_x = xRange / totalRange,
            coverage_y = yRange / totalRange,
            coverage_z = zRange / totalRange,
        )
    }
}
```

Coverage quality gates (from REQ §5.1 SE-07):
- **Good:** all three coverage values ≥ 0.20f
- **Fair:** all three ≥ 0.15f (and not all ≥ 0.20f)
- **Poor:** any < 0.15f

### 4.3 Ellipsoid Fitting Algorithm

**Selected algorithm:** Iterative least-squares via Levenberg-Marquardt (LM) minimization on a sphere model.

**Approach:** Fit raw magnetometer samples to a sphere of radius `targetRadius` by solving for the hard-iron offset vector `b` (3 elements) and soft-iron matrix `A` (3×3 symmetric) such that:

```
|A * (sample - b)| ≈ targetRadius  for all samples
```

**Why not MinVolEllipsoid (MVEE)?** MVEE computes the minimum-volume enclosing ellipsoid, which is a geometric fitting approach. LM on a sphere model directly minimizes the residuals relevant to magnetic field correction. LM is more numerically stable on the typical 500-sample sizes collected in a figure-8 motion and converges faster for the low-dimensional parameter space (9 unknowns for A, 3 for b). MVEE is an acceptable alternative if LM convergence is slow on budget hardware, but LM is the default.

**Implementation reference:** Use an existing open-source Java/Kotlin LM implementation (e.g., `org.apache.commons.math3:commons-math3` `LevenbergMarquardtOptimizer`) to avoid implementing numerical optimization from scratch. This keeps the APK clean and avoids introducing new math bugs.

**Target radius selection:**

`getTargetRadius()` accepts a `LocationProvider` parameter (defined in §5.0) instead of a raw `Context`, enabling deterministic unit testing with a fixed synthetic location.

```kotlin
fun getTargetRadius(locationProvider: LocationProvider): Float {
    val location: Location? = locationProvider.getLastKnownLocation()

    return if (location != null) {
        val geoField = GeomagneticField(
            location.latitude.toFloat(),
            location.longitude.toFloat(),
            location.altitude.toFloat(),
            System.currentTimeMillis()
        )
        geoField.fieldStrength / 1000f  // GeomagneticField returns nT; convert to µT
    } else {
        50.0f  // hardcoded global-average fallback per SE-02
    }
}
```

### 4.4 CalibrationResult and Quality Classification

```kotlin
data class Vector3(val x: Float, val y: Float, val z: Float)

data class Matrix3x3(
    val m00: Float, val m01: Float, val m02: Float,
    val m10: Float, val m11: Float, val m12: Float,
    val m20: Float, val m21: Float, val m22: Float,
)

data class CalibrationResult(
    val hard_iron: Vector3,             // µT offset vector
    val soft_iron: Matrix3x3,           // dimensionless correction matrix
    val residual_rms: Float,            // µT, RMS of |A*(sample - b)| - targetRadius
    val coverage: CoverageStats,
    val quality: CalibrationQuality,
    val target_radius: Float,           // µT, the sphere radius used in the fit
)

enum class CalibrationQuality { GOOD, FAIR, POOR }
```

**Quality classification (half-open intervals per REQ §5.3):**

| residual_rms | coverage | CalibrationQuality |
|---|---|---|
| `[0, 1.0]` µT AND all coverage ≥ 0.20 | — | `GOOD` |
| `(1.0, 2.0]` µT AND all coverage ≥ 0.15 | — | `FAIR` |
| `> 2.0` µT OR any coverage < 0.15 | — | `POOR` |

Note: exactly 1.0 µT residual → `GOOD` (lower-inclusive); exactly 2.0 µT → `FAIR` (lower-inclusive at 1.0, closed upper at 2.0); 2.0001 µT → `POOR`.

**Canonical source (PM-FIND-01 / TE-F-02):** The residual and coverage thresholds in §4.4 and §6.1 are identical by definition. If they ever diverge in a future edit, **§6.1 is authoritative**.

### 4.5 Correction Application in Real-Time Path

On every `SensorFrame`, before passing to `FusionEngine`:

```kotlin
fun applyCorrectedMag(
    raw_x: Float, raw_y: Float, raw_z: Float,
    calibration: CalibrationRecord?
): Triple<Float, Float, Float> {
    if (calibration == null) return Triple(raw_x, raw_y, raw_z)

    // Step 1: Hard-iron subtraction
    val hx = raw_x - calibration.hard_iron.x
    val hy = raw_y - calibration.hard_iron.y
    val hz = raw_z - calibration.hard_iron.z

    // Step 2: Soft-iron matrix multiplication
    val m = calibration.soft_iron
    val cx = m.m00 * hx + m.m01 * hy + m.m02 * hz
    val cy = m.m10 * hx + m.m11 * hy + m.m12 * hz
    val cz = m.m20 * hx + m.m21 * hy + m.m22 * hz

    return Triple(cx, cy, cz)
}
```

When `usingFallbackMag = true` (TYPE_MAGNETIC_FIELD), the bias fields `mag_bias_x/y/z` in `SensorFrame` are 0.0f. The OS has already applied its own hard-iron correction internally; the app applies its own ellipsoid-fit correction on top, which is acceptable as a best-effort approach.

### 4.6 Calibration Persistence Data Model

```kotlin
@Entity(tableName = "calibration_records")
data class CalibrationRecord(
    @PrimaryKey val id: Int,         // 1 = current, 2 = rollback
    val device_model: String,        // Build.MODEL
    val sensor_fingerprint: String,  // Build.FINGERPRINT trimmed to 128 chars
    val hard_iron_x: Float,
    val hard_iron_y: Float,
    val hard_iron_z: Float,
    // Soft-iron matrix stored as 9 floats (flat row-major)
    val soft_iron_00: Float, val soft_iron_01: Float, val soft_iron_02: Float,
    val soft_iron_10: Float, val soft_iron_11: Float, val soft_iron_12: Float,
    val soft_iron_20: Float, val soft_iron_21: Float, val soft_iron_22: Float,
    val residual_rms: Float,
    val quality: String,             // "GOOD" | "FAIR" | "POOR"
    val saved_at_utc: Long,          // System.currentTimeMillis() at moment of confirmed write
    val wmm_expected_field_ut: Float,// µT; -1.0f signals "50 µT fallback"
    val wmm_source: String,          // "GeomagneticField" | "fallback_50uT"
    val coverage_x: Float,
    val coverage_y: Float,
    val coverage_z: Float,
    val calibration_schema_version: Int = 1,
)
```

**ID convention:**
- `id = 1`: current (most recently saved) calibration record.
- `id = 2`: rollback (previous) record, preserved for OOM-kill recovery.
- Table has a maximum of 2 rows at any time.

**Atomic write via Room transaction:**

```kotlin
@Transaction
suspend fun saveCalibration(newRecord: CalibrationRecord) {
    val current = getById(1)
    if (current != null) {
        // Promote current to rollback slot
        delete(2)  // discard previous rollback
        insert(current.copy(id = 2))
    }
    delete(1)
    insert(newRecord.copy(id = 1))
}
```

Room executes this in a single SQLite transaction. A process kill mid-transaction leaves the database in the pre-transaction state (prior record intact), satisfying the atomic write requirement (REQ-CAL-04 TE-07).

### 4.7 Calibration Age Computation

```kotlin
fun calibrationAgeDays(record: CalibrationRecord): Long {
    val ageMs = System.currentTimeMillis() - record.saved_at_utc
    return ageMs / (1000L * 60 * 60 * 24)  // floor division → complete days
}
```

Age clock starts at the moment `saveCalibration()` commits (the value written to `saved_at_utc`), not at data collection start.

---

## 5. Interference Detector

### 5.0 Injectable Dependencies for InterferenceDetector and CalibrationEngine

*(TE-F-03)* To enable deterministic unit testing on the host JVM without Robolectric, the following interfaces are defined and injected into components that previously required a raw `Context` for location or time access.

#### LocationProvider

```kotlin
fun interface LocationProvider {
    fun getLastKnownLocation(): android.location.Location?
}
```

`LocationProvider` is a functional interface (SAM). The production implementation wraps `LocationManager.getLastKnownLocation()`:

```kotlin
class SystemLocationProvider(private val context: Context) : LocationProvider {
    override fun getLastKnownLocation(): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return runCatching {
            lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        }.getOrNull()
    }
}
```

In unit tests, a lambda is injected directly: `LocationProvider { fixedLocation }`.

#### TimeSource

```kotlin
interface TimeSource {
    fun nowMs(): Long   // wall-clock millis — System.currentTimeMillis() equivalent
    fun nowNs(): Long   // elapsed real-time nanos — SystemClock.elapsedRealtimeNanos() equivalent
}
```

Production implementation:

```kotlin
object SystemTimeSource : TimeSource {
    override fun nowMs(): Long = System.currentTimeMillis()
    override fun nowNs(): Long = SystemClock.elapsedRealtimeNanos()
}
```

In unit tests, a fake `TimeSource` is injected with manually advanced time, allowing sub-millisecond clearance-timer boundary tests without real sleeps.

#### Constructor signatures

- `InterferenceDetector(locationProvider: LocationProvider, timeSource: TimeSource)`
- `SensorRateMonitor(windowMs: Long = 2000L, threshold: Float = 15.0f, timeSource: TimeSource)`
- `getTargetRadius(locationProvider: LocationProvider)` — see §4.3

`DatabaseKeyManager.getOrCreateKey(context: Context)` retains `Context` (Android Keystore access requires it). For unit tests, `CalibrationRepository` is constructed via a `@VisibleForTesting` constructor that accepts a pre-built `RoomDatabase` instance (non-SQLCipher `Room.inMemoryDatabaseBuilder`). SQLCipher is tested only in instrumented tests on a real device or emulator. See §9.2 for full test strategy.

---

### 5.1 Expected Field Baseline

```kotlin
class InterferenceDetector(
    private val locationProvider: LocationProvider,  // injectable; see §5.0
    private val timeSource: TimeSource,              // injectable; see §5.0
) {

    private var expectedMagnitude_uT: Float = 50.0f   // fallback
    private var expectedInclination_deg: Float = 45.0f // mid-latitude fallback
    private var usingLocationFallback: Boolean = true

    // Explicit state field (TE-F-07): readable in tests; mutated by updateState()
    var currentState: InterferenceState = InterferenceState.CLEAR

    fun updateExpectedField() {
        val location = locationProvider.getLastKnownLocation()
        if (location != null) {
            val geoField = GeomagneticField(
                location.latitude.toFloat(),
                location.longitude.toFloat(),
                location.altitude.toFloat(),
                timeSource.nowMs()
            )
            expectedMagnitude_uT = geoField.fieldStrength / 1000f  // nT → µT
            expectedInclination_deg = geoField.inclination          // degrees
            usingLocationFallback = false
        } else {
            expectedMagnitude_uT = 50.0f
            expectedInclination_deg = 45.0f
            usingLocationFallback = true
        }
    }
}
```

**`currentState` field (TE-F-07):** `updateState()` reads and writes `currentState` as a side effect. For deterministic testing, the initial state can be set via a `@VisibleForTesting` setter or by passing an initial state via a secondary constructor overload.

`updateExpectedField()` is called:
- Once in `Activity.onStart()` (after sensor registration).
- Not continuously during active sensing (the field does not change meaningfully as the user moves within a building).

### 5.2 Field Magnitude and Inclination Deviation

```kotlin
data class InterferenceMetrics(
    val measuredMagnitude_uT: Float,
    val expectedMagnitude_uT: Float,
    val fieldDeviation: Float,          // ratio: |measured - expected| / expected
    val measuredInclination_deg: Float,
    val expectedInclination_deg: Float,
    val inclinationDeviation_deg: Float,
    val usingLocationFallback: Boolean,
)

fun computeMetrics(calibrated_x: Float, calibrated_y: Float, calibrated_z: Float): InterferenceMetrics {
    val measuredMag = sqrt(calibrated_x.pow(2) + calibrated_y.pow(2) + calibrated_z.pow(2))
    val fieldDeviation = abs(measuredMag - expectedMagnitude_uT) / expectedMagnitude_uT

    // Inclination: angle between field vector and horizontal plane
    val horizontalComponent = sqrt(calibrated_x.pow(2) + calibrated_y.pow(2))
    val measuredInclination = Math.toDegrees(
        atan2(calibrated_z.toDouble(), horizontalComponent.toDouble())
    ).toFloat()
    val inclinationDev = abs(measuredInclination - expectedInclination_deg)

    return InterferenceMetrics(
        measuredMagnitude_uT = measuredMag,
        expectedMagnitude_uT = this.expectedMagnitude_uT,
        fieldDeviation = fieldDeviation,
        measuredInclination_deg = measuredInclination,
        expectedInclination_deg = this.expectedInclination_deg,
        inclinationDeviation_deg = inclinationDev,
        usingLocationFallback = this.usingLocationFallback,
    )
}
```

### 5.3 Noise Spike Rejection

Implemented in `NoiseSpikeFilter`, a regular `class` instantiated by `SensorLayer`. Called before passing the mag sample to `InterferenceDetector` or `FusionEngine`.

Constructor: `NoiseSpikeFilter(windowSize: Int = 20, spikeThreshold: Float = 10.0f)`.

```kotlin
class NoiseSpikeFilter(
    private val windowSize: Int = 20,
    private val spikeThreshold: Float = 10.0f,
) {
    private val recentValues = ArrayDeque<Triple<Float, Float, Float>>(windowSize)

    // Returns the sample if accepted, or null if rejected
    fun filter(x: Float, y: Float, z: Float): Triple<Float, Float, Float>? {
        val meanX = recentValues.map { it.first }.average().toFloat()
        val meanY = recentValues.map { it.second }.average().toFloat()
        val meanZ = recentValues.map { it.third }.average().toFloat()

        val deviation = sqrt(
            (x - meanX).pow(2) + (y - meanY).pow(2) + (z - meanZ).pow(2)
        )

        return if (recentValues.size < 2 || deviation <= spikeThreshold) {
            // Accept: update rolling mean
            if (recentValues.size >= windowSize) recentValues.removeFirst()
            recentValues.addLast(Triple(x, y, z))
            Triple(x, y, z)
        } else {
            // Reject: log to diagnostic buffer; do NOT update rolling mean
            diagnosticBuffer.add("Spike rejected: deviation=${deviation}µT at $x,$y,$z")
            null
        }
    }
}
```

**Spike rejection boundary (TE-F-05):** The rejection condition is `deviation > spikeThreshold` (strict greater-than). A deviation of exactly 10.0 µT is **ACCEPTED** (condition `deviation <= 10.0f` is true). Tests verify this boundary explicitly: deviation = 10.0 µT → accepted; deviation = 10.001 µT → rejected.

Rejected samples are logged to `DiagnosticBuffer` (defined below). The fusion engine is NOT called with the rejected sample; `null` return means the frame is skipped for fusion purposes.

During initialization (`recentValues.size < 2`), spike rejection is disabled to avoid filtering valid early samples against an empty window.

#### DiagnosticBuffer

`DiagnosticBuffer<T>` is a generic circular buffer class used by `NoiseSpikeFilter` (rejected sample log) and `LatencyTracker` (latency samples):

```kotlin
class DiagnosticBuffer<T : Comparable<T>>(val capacity: Int) {
    fun add(value: T)
    fun percentile(p: Double): T   // p in [0.0, 1.0]; e.g., 0.95 for 95th percentile
    val size: Int
}
```

Thread safety: `DiagnosticBuffer` is protected by `ReentrantReadWriteLock` — multiple concurrent reads are allowed; writes acquire the exclusive lock. The `add()` operation evicts the oldest entry when the buffer is at capacity (circular overwrite). `percentile()` sorts a snapshot of current values and returns the value at index `floor(size * p)`.

### 5.4 Interference State Machine

```kotlin
enum class InterferenceState {
    CLEAR,    // field deviation < 15% AND inclination < 3° for ≥ 3 continuous seconds
    MODERATE, // field deviation [15%, 25%) OR inclination [3°, 8°) — advisory only
    WARNING,  // field deviation ≥ 25% OR inclination ≥ 8°
}
```

**State transitions:**

```
CLEAR → WARNING:   fieldDeviation ≥ 0.25f OR inclinationDev ≥ 8.0f
CLEAR → MODERATE:  (fieldDeviation ≥ 0.15f AND < 0.25f) OR (inclinationDev ≥ 3.0f AND < 8.0f)
                   [advisory indicator; not used for confidence scoring threshold]

WARNING → CLEAR:   fieldDeviation < 0.15f AND inclinationDev < 3.0f, sustained ≥ 3.0 seconds
                   (hysteresis timer resets on ANY excursion to ≥ 0.15f OR ≥ 3.0°)

MODERATE → CLEAR:  same clearance rule as WARNING → CLEAR
MODERATE → WARNING: fieldDeviation ≥ 0.25f OR inclinationDev ≥ 8.0f
```

The clearance threshold (15% field, 3° inclination) is lower than the trigger threshold (25% field, 8° inclination), creating deliberate hysteresis. An environment at 20% deviation will remain in `WARNING` (the warning triggered at 25% will not clear at 20%).

**Clearance timer implementation:**

```kotlin
private var clearanceStartNs: Long = 0L
private val CLEARANCE_DURATION_NS = 3_000_000_000L  // 3 seconds

// nowNs is supplied by the caller (SensorLayer passes timeSource.nowNs()) for deterministic testing
fun updateState(metrics: InterferenceMetrics, nowNs: Long): InterferenceState {
    val isBelow = metrics.fieldDeviation < 0.15f && metrics.inclinationDeviation_deg < 3.0f
    val isWarning = metrics.fieldDeviation >= 0.25f || metrics.inclinationDeviation_deg >= 8.0f

    return when {
        isWarning -> {
            clearanceStartNs = 0L
            InterferenceState.WARNING
        }
        isBelow -> {
            if (clearanceStartNs == 0L) clearanceStartNs = nowNs
            if (nowNs - clearanceStartNs >= CLEARANCE_DURATION_NS) {
                InterferenceState.CLEAR
            } else {
                // Still in clearance window; remain in current non-CLEAR state
                currentState
            }
        }
        else -> {
            // Moderate band (15–25% field or 3–8° inclination)
            clearanceStartNs = 0L  // reset — not yet in clearance territory
            InterferenceState.MODERATE
        }
    }
}
```

When the app is backgrounded (`onStop()`), the clearance timer is frozen (no sensor events arrive). On return to foreground, `updateExpectedField()` is called and the timer resets to 0 regardless of prior state, consistent with FSPEC-DETECT-02.

Phase 1 behavior: `InterferenceState` is emitted to the UI only. No bearing capture button exists. No override button exists (introduced in Phase 2 with REQ-CAPTURE).

---

## 6. Confidence Model

### 6.1 Scoring Dimensions

```kotlin
enum class ConfidenceScore(val numericValue: Int) {
    GOOD(3), MODERATE(2), POOR(1)
}
```

All interval boundaries use the half-open convention (lower-inclusive, upper-exclusive) unless specified. Closed-interval exceptions noted in the table.

**Evaluation order rule (PM-FIND-01 / TE-F-02):** For `cal_quality_score`, evaluate GOOD conditions first; if met, return GOOD without evaluating MODERATE. The row conditions are mutually exclusive: GOOD uses a closed upper bound at 1.0 µT; the MODERATE row starts strictly above 1.0 µT. This prevents the boundary ambiguity that would arise if rows were evaluated bottom-up.

| Dimension | GOOD (3) | MODERATE (2) | POOR (1) |
|-----------|----------|-------------|---------|
| `field_deviation_score` | `[0.0, 0.15)` | `[0.15, 0.25)` | `[0.25, ∞)` |
| `inclination_deviation_score` | `[0.0°, 3.0°)` | `[3.0°, 8.0°)` | `[8.0°, ∞)` |
| `cal_quality_score` | `residual in [0, 1.0 µT] AND all coverage ≥ 20%` | `residual in (1.0, 2.0 µT] AND all coverage ≥ 15%` | `residual > 2.0 µT OR any coverage < 15%` |
| `cal_age_score` | `[0, 7 days]` (closed) | `(7, 30 days]` (closed upper) | `(30 days, ∞)` |
| `noise_variance_score` | `[0.0, 0.1 µT²]` (closed) | `(0.1, 0.5 µT²]` (closed upper) | `(0.5, ∞)` |

Boundary values: 7 days → GOOD; 30 days → MODERATE; 31+ days → POOR. 1.0 µT residual → GOOD; 2.0 µT → MODERATE (FAIR); 2.001 µT → POOR. 0.1 µT² noise → GOOD; 0.5 µT² → MODERATE; 0.501 µT² → POOR.

**Test boundary values for confidence model (TE-F-02):**

| Boundary | Expected result |
|---|---|
| tilt = 5.0° | No tilt clamp (no penalty) |
| tilt = 5.1° | MODERATE clamp applied |
| tilt = 20.0° | MODERATE clamp applied (NOT POOR; see §6.3 note) |
| tilt = 20.1° | POOR clamp applied |
| residual = 1.0 µT, coverage ≥ 20% | `cal_quality_score` = GOOD |
| residual = 1.001 µT, coverage ≥ 15% | `cal_quality_score` = MODERATE |
| residual = 2.0 µT, coverage ≥ 15% | `cal_quality_score` = MODERATE |
| residual = 2.001 µT | `cal_quality_score` = POOR |
| noise variance = 0.1 µT² | `noise_variance_score` = GOOD |
| noise variance = 0.101 µT² | `noise_variance_score` = MODERATE |
| noise variance = 0.5 µT² | `noise_variance_score` = MODERATE |
| noise variance = 0.501 µT² | `noise_variance_score` = POOR |
| cal age = 7 days | `cal_age_score` = GOOD |
| cal age = 30 days | `cal_age_score` = MODERATE |
| cal age = 31 days | `cal_age_score` = POOR |

### 6.2 Noise Variance Computation

```kotlin
class NoiseVarianceTracker(private val windowSize: Int = 20) {
    private val samples = ArrayDeque<Float>(windowSize)

    fun update(mag_x: Float, mag_y: Float, mag_z: Float) {
        val magnitude = sqrt(mag_x.pow(2) + mag_y.pow(2) + mag_z.pow(2))
        if (samples.size >= windowSize) samples.removeFirst()
        samples.addLast(magnitude)
    }

    fun variance(): Float {
        if (samples.size < 2) return 0f
        val mean = samples.average().toFloat()
        return samples.map { (it - mean).pow(2) }.average().toFloat()
    }
}
```

**Window size:** 20 samples. At 50 Hz magnetometer, this is a 400 ms window — responsive to fast-changing noise without over-reacting to single spikes (which are separately rejected by `NoiseSpikeFilter` before this tracker sees them).

### 6.3 Confidence Computation Algorithm

```kotlin
fun compute(
    fieldDeviation: Float,
    inclinationDeviation_deg: Float,
    calibration: CalibrationRecord?,
    noiseVariance_uT2: Float,
    tilt_deg: Double,
    hasGyroscope: Boolean,
    sensorState: SensorState,
): OverallConfidence {

    // Special state overrides
    if (sensorState == SensorState.STUCK) return OverallConfidence.SENSOR_ERROR
    if (sensorState == SensorState.STABILIZING) return OverallConfidence.STABILIZING

    // Uncalibrated state forces POOR
    val cal_quality_score = if (calibration == null) {
        ConfidenceScore.POOR
    } else {
        scoreCalibrationQuality(calibration)
    }

    val field_deviation_score = scoreFieldDeviation(fieldDeviation)
    val inclination_deviation_score = scoreInclinationDeviation(inclinationDeviation_deg)
    val cal_age_score = if (calibration == null) ConfidenceScore.POOR
                        else scoreCalibrationAge(calibration.saved_at_utc)
    val noise_variance_score = scoreNoiseVariance(noiseVariance_uT2)

    // Step 1: minimum across all five dimensions
    val step1 = minOf(
        field_deviation_score.numericValue,
        inclination_deviation_score.numericValue,
        cal_quality_score.numericValue,
        cal_age_score.numericValue,
        noise_variance_score.numericValue,
    )

    // Step 2: tilt clamp (can only lower, never raise)
    // Boundary note (TE-F-02): pseudocode uses strict greater-than (>) at both thresholds.
    // tilt = 5.0° → NO clamp (falls to else branch); tilt = 5.1° → MODERATE clamp.
    // tilt = 20.0° → MODERATE clamp (NOT POOR — the > 20.0 condition is false);
    // tilt = 20.1° → POOR clamp.
    // This is consistent with REQ boundary convention TE-04: (20°, ∞) → POOR (open-lower),
    // meaning 20.0° itself is excluded from the POOR range and is clamped to MODERATE instead.
    // Explicit test boundary values: tilt = 5.0° → no clamp; tilt = 5.1° → MODERATE;
    // tilt = 20.0° → MODERATE; tilt = 20.1° → POOR.
    val overall = when {
        tilt_deg > 20.0 -> minOf(step1, ConfidenceScore.POOR.numericValue)
        tilt_deg > 5.0  -> minOf(step1, ConfidenceScore.MODERATE.numericValue)
        else            -> step1
    }

    // Step 3: no-gyroscope session cap (can only lower, never raise)
    val capped = if (!hasGyroscope) minOf(overall, ConfidenceScore.MODERATE.numericValue)
                 else overall

    return when (capped) {
        3    -> OverallConfidence.HIGH
        2    -> OverallConfidence.MODERATE
        else -> OverallConfidence.POOR
    }
}
```

### 6.4 OverallConfidence Enum

```kotlin
enum class OverallConfidence {
    HIGH,         // score = 3, display: "High" (green)
    MODERATE,     // score = 2, display: "Moderate" (amber)
    POOR,         // score = 1, display: "Poor" (red)
    STABILIZING,  // rapid rotation; display: "Stabilizing…" (amber) — does not enter scoring
    SENSOR_ERROR, // stuck sensor; display: "Sensor error" (red) — does not enter scoring
}
```

`STABILIZING` and `SENSOR_ERROR` are display-only states. They bypass the 5-dimension model entirely. The tilt clamp and no-gyroscope cap are NOT applied when these states are active.

---

## 7. Display Layer — Modern Mode

### 7.1 CompassViewModel

```kotlin
class CompassViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CompassUiState.INITIAL)
    val uiState: StateFlow<CompassUiState> = _uiState.asStateFlow()

    // Emitted by sensorThread pipeline via Choreographer callback on main thread
    fun updateState(
        fusionResult: FusionResult,
        confidence: OverallConfidence,
        interferenceState: InterferenceState,
        interferenceMetrics: InterferenceMetrics?,
        calibration: CalibrationRecord?,
        sensorAdvisory: SensorAdvisory?,
        hasGyroscope: Boolean,
        usingFallbackMag: Boolean,
        usingLocationFallback: Boolean,
    ) { /* calls buildUiState() then emits to _uiState */ }

    // TE-F-04: Pure function — no side effects, no framework dependencies.
    // Unit tests call this directly with synthetic inputs to verify state mapping
    // logic without touching Choreographer or StateFlow.
    internal fun buildUiState(
        fusion: FusionResult,
        confidence: OverallConfidence,
        interferenceState: InterferenceState,
        calibrationAgeDays: Int?,        // null = uncalibrated
        hasCalibration: Boolean,
        isPowerSaving: Boolean,
    ): CompassUiState { /* pure transformation; no side effects */ }
}
```

### 7.2 CompassUiState

```kotlin
data class CompassUiState(
    val heading_deg: Double,              // [0.0, 360.0)
    val heading_formatted: String,        // e.g., "247.3°" — formatted with Locale.ROOT (see §8.3)
    val north_label: String,              // "Magnetic N" (Phase 1 only magnetic north)
    val confidence: OverallConfidence,
    val interference_state: InterferenceState,
    val interference_metrics: InterferenceMetrics?,  // null when CLEAR
    val tilt_deg: Double,
    val tilt_text: String?,               // null if tilt ≤ 5°; amber text if 5–20°; warning if > 20°
    val calibration_age_days: Long,       // -1 if uncalibrated
    val calibration_age_label: String,    // "Uncalibrated" | "Cal: 0d" | "Cal: 3d" etc.
    val cal_dot_color: CalDotColor,       // GREEN | AMBER | RED
    val power_saving_advisory: Boolean,   // true → show power-save banner
    val no_gyroscope_advisory: Boolean,   // true → show no-gyroscope advisory (persistent)
    val fallback_mag_advisory: Boolean,   // true → show "Limited calibration" badge
    val location_fallback_advisory: Boolean, // true → show location-unavailable note
    val sensor_state: SensorState,        // drives STABILIZING / SENSOR_ERROR display
    val is_stabilizing: Boolean,          // convenience flag for UI
    val last_valid_heading_deg: Double?,  // non-null and frozen when sensor_state == STUCK
    val show_calibration_cta: Boolean,    // PM-FIND-03: true when no calibration record exists in DB
) {
    companion object {
        val INITIAL = CompassUiState(
            heading_deg = 0.0, heading_formatted = "---",
            north_label = "Magnetic N", confidence = OverallConfidence.POOR,
            interference_state = InterferenceState.CLEAR, interference_metrics = null,
            tilt_deg = 0.0, tilt_text = null, calibration_age_days = -1,
            calibration_age_label = "Uncalibrated", cal_dot_color = CalDotColor.RED,
            power_saving_advisory = false, no_gyroscope_advisory = false,
            fallback_mag_advisory = false, location_fallback_advisory = false,
            sensor_state = SensorState.NORMAL, is_stabilizing = false,
            last_valid_heading_deg = null,
            show_calibration_cta = true,  // default true until first calibration exists
        )
    }
}

enum class CalDotColor { GREEN, AMBER, RED }
```

### 7.3 Compass Rose Rendering

**Technology choice:** Custom Android `View` subclass `CompassRoseView` with manual `Canvas` drawing (Compose `Canvas` is an acceptable alternative in Phase 1 but the custom `View` approach avoids the Compose dependency for this phase). The View is the primary approach; Compose migration is deferred.

```kotlin
class CompassRoseView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var headingDeg: Double = 0.0
        set(value) { field = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        // Rotate canvas by -headingDeg so N stays fixed at top pointer
        canvas.save()
        canvas.rotate(-headingDeg.toFloat(), width / 2f, height / 2f)
        drawRoseGraduations(canvas)
        drawCardinalLabels(canvas)   // N, E, S, W, NE, SE, SW, NW — always Latin letters
        canvas.restore()
        drawNorthPointer(canvas)     // fixed; not rotated
    }
}
```

Key invariant: `canvas.rotate(-headingDeg)` rotates the dial, keeping the north pointer fixed at the screen top. Cardinal labels (N, E, S, W) are hardcoded Latin letters and are NOT localized in Phase 1.

### 7.4 Tilt Indicator Layout

The tilt indicator occupies a **reserved layout slot** of fixed height. The slot is always present in the view hierarchy (height does not collapse to 0 when empty), preventing layout reflow when the indicator appears or disappears.

The reserved height is sized for the longest worst-case string at the device's maximum supported font scale (1.3×):
`"Hold flat for best accuracy — Tilt: XX.X°"`

Implementation:

```xml
<!-- Reserved tilt slot — always in layout, text is "" when tilt ≤ 5° -->
<TextView
    android:id="@+id/tilt_indicator"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:minHeight="@dimen/tilt_indicator_reserved_height"
    android:text=""
    android:textColor="@color/tilt_amber"
    android:textSize="14sp"
    android:gravity="center" />
```

`@dimen/tilt_indicator_reserved_height` is set to the measured height of the longest tilt string at 1.3× font scale.

Tilt text rules:
- `tilt ≤ 5.0°`: `text = ""` (empty; slot is still present)
- `5.0° < tilt ≤ 20.0°`: `text = "Tilt: %.1f°".format(tilt_deg)` (amber)
- `tilt > 20.0°`: `text = "${resources.getString(R.string.hold_flat_for_accuracy)} — Tilt: %.1f°".format(tilt_deg)` (amber, same slot)

### 7.5 Mode Switcher Suppression

*(PM-FIND-02 — REQ-DISPLAY-01)* Phase 1 contains no navigation element for mode switching. The navigation graph (`nav_graph.xml`) contains only one destination: `CompassFragment` (Modern Mode). No bottom navigation view, tab layout, menu item, or floating action button referencing Luopan Mode or Sighting Mode is included in any layout resource.

The `activity_main.xml` layout contains only a `FragmentContainerView` for `CompassFragment`. Future mode destinations will be added to the navigation graph in their respective phases; they MUST NOT be scaffolded as disabled or hidden views in Phase 1.

**Test:** Espresso `onView(withId(R.id.bottom_nav))` MUST throw `NoMatchingViewException`; no view with content description `"Luopan"` or `"Sighting"` is present in the layout hierarchy.

### 7.6 First-Launch Calibration CTA

*(PM-FIND-03 — REQ §9 Scenario A)* When `CompassUiState.show_calibration_cta == true`, a non-blocking `MaterialBanner` is displayed at the bottom of the compass screen with the following properties:

- **Primary text:** localized string key `str_calibration_cta_title` (e.g., "Calibrate your compass for best accuracy")
- **Action button:** "Calibrate Now" (localized string key `str_calibrate_now`) — tapping launches `CalibrationWizardActivity` via `ActivityResultLauncher` (see §1.2)
- **Secondary dismiss action:** "Later" (localized string key `str_later`) — dismisses the banner for the current session; `show_calibration_cta` remains true until a calibration record is saved

**Lifecycle:** Once any calibration record exists in the database (even `POOR` quality), `show_calibration_cta` becomes `false` permanently. The banner is NOT shown again for that install. The banner is NOT a dialog — it does not block compass interaction.

**Implementation note:** `CompassViewModel` queries `CalibrationRepository.hasAnyCalibration(): Boolean` on startup. The `show_calibration_cta` field is set to `!hasAnyCalibration`. It is updated via `SharedFlow` or direct ViewModel reload when `CalibrationWizardActivity` returns a successful result.

### 7.7 Wake Lock

*(PM-FIND-05)* The wake lock is acquired **unconditionally** in `Activity.onStart()` using `PowerManager.WAKE_LOCK`. There is no user-configurable toggle for this behavior in Phase 1. If a toggle is desired in a future phase, a corresponding REQ change is required first. The `keepScreenOn` boolean and any `SettingsRepository` lookup for this setting are entirely absent from Phase 1.

```kotlin
class CompassActivity : AppCompatActivity() {
    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onStart() {
        super.onStart()
        // Wake lock acquired unconditionally — REQ-DISPLAY-10 (P0)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "luopan:CompassScreenOn"
        )
        wakeLock.acquire()  // No timeout — released in onStop()
        registerSensorListeners()
    }

    override fun onStop() {
        unregisterSensorListeners()
        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
        }
        super.onStop()
    }
}
```

**Permission required:** `android.permission.WAKE_LOCK` declared in `AndroidManifest.xml` as a normal permission (no user prompt required).

### 7.8 Update Throttle to ≥ 20 Hz

*(PM-FIND-07)* The `Choreographer.FrameCallback` mechanism (described in §3.6) ensures display updates are posted at display frame rate (≥60 Hz on modern devices, ≥20 Hz on minimum API 26 hardware). On devices with 60 Hz displays, the Choreographer fires at 60 Hz, which is well above the 20 Hz minimum.

REQ-NFR-02's ≥20 Hz display update target is satisfied by the sensor delivery rate (≥20 Hz at `SENSOR_DELAY_GAME`). `SensorRateMonitor` does NOT gate display updates — it only triggers the power-saving advisory when sustained sensor delivery falls below 15 Hz. Choreographer frame rate is not monitored separately; on API 26+ hardware it is always ≥60 Hz and never requires a runtime check.

---

## 8. Localization

### 8.1 Resource Directories

```
res/
  values/          → default (en)
  values-zh-rHant/ → Traditional Chinese (zh-TW, zh-HK, zh-MO)
  values-zh-rHans/ → Simplified Chinese (zh-CN, zh-SG, bare zh)
  values-ja/       → Japanese
```

### 8.2 Locale Matching Rules

Implemented via `AppCompatDelegate.setApplicationLocales()` and standard Android resource qualification. The Android resource system handles regional variants automatically:

| System locale | Resource directory matched |
|---|---|
| `zh-TW`, `zh-HK`, `zh-MO` | `values-zh-rHant` |
| `zh-CN`, `zh-SG`, `zh` (bare) | `values-zh-rHans` |
| `ja`, `ja-JP` | `values-ja` |
| `en-US`, `en-GB`, `en-AU`, any `en-*` | `values` (default) |
| Any other locale (`ko`, `fr`, `ar`, etc.) | `values` (default English) |

Note: `zh` (bare, no script tag) maps to `values-zh-rHans` per Android convention. The resource qualifier `zh-rHans` matches the bare `zh` locale in Android's resource resolution algorithm.

No in-app language override is provided in Phase 1. Locale is determined by `Locale.getDefault()` as read by the resource system at startup.

### 8.3 Strings Not Localized in Phase 1

The following strings are currently hardcoded as Latin/ASCII and do NOT appear in `strings.xml`:

- Cardinal compass rose labels: `"N"`, `"E"`, `"S"`, `"W"`, `"NE"`, `"SE"`, `"SW"`, `"NW"`
- Degree symbol: `"°"` (appended in code to numeric format strings)
- Unit: `"µT"` (always Latin µ + T)
- Numeric values: always Western Arabic digits (0–9) with `.` as decimal separator

**Product decision required before implementation (PM-FIND-04):** REQ-L10N-01 requires all Phase 1 UI strings to be translated for zh-Hant, zh-Hans, and ja locales. Cardinal compass rose labels (N, E, S, W, NE, SE, SW, NW) are currently specified as hardcoded Latin letters, which conflicts with REQ-L10N-01 for zh/ja locales where direction labels are conventionally written as 北/東/南/西. Engineering recommends localizing these via `strings.xml` entries for all four locales. If the product owner decides to keep Latin letters for all locales, the REQ §5.6 localization requirement must be amended with an explicit exception. **This decision gates §7.3 and §8.3 implementation.**

**Numeric format locale (PM-FIND-08):** All numeric heading values are formatted with `Locale.ROOT` to avoid locale-dependent decimal separators: `String.format(Locale.ROOT, "%.1f°", heading_deg)`. This ensures the heading display never shows a comma separator (e.g., `"247,3°"` in French/German system locales) regardless of the user's system locale setting.

All other user-facing strings (confidence badge text, warning messages, calibration instructions, tilt advisory, error messages, button labels, calibration age indicator) MUST have translations in all four locales. A missing translation in any supported locale is a release blocker.

### 8.4 Calibration Age Indicator Format

```kotlin
fun formatCalibrationAge(ageDays: Long, resources: Resources): String {
    return if (ageDays < 0L) {
        resources.getString(R.string.cal_uncalibrated)   // "Uncalibrated" localized
    } else {
        resources.getString(R.string.cal_age_format, ageDays)  // "Cal: %dd" localized template
    }
}
```

The `%d` placeholder is locale-independent (always Arabic numerals). The `"Cal:"` prefix and `"d"` suffix are in `strings.xml` for each locale.

---

## 9. Persistence Layer

### 9.1 Database Technology

**Room with SQLCipher encryption.**

```kotlin
// build.gradle (app)
implementation("net.zetetic:android-database-sqlcipher:4.5.4")
implementation("androidx.sqlite:sqlite-ktx:2.4.0")
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
kapt("androidx.room:room-compiler:2.6.1")
```

SQLCipher provides AES-256 encryption at the page level. The database key is generated and stored via Android Keystore to prevent extraction from the APK or device file system.

### 9.2 Key Generation and Storage

```kotlin
object DatabaseKeyManager {
    private const val PREF_NAME = "luopan_db_key"
    private const val KEY_ALIAS = "luopan_sqlcipher_key"

    fun getOrCreateKey(context: Context): ByteArray {
        val prefs = EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        val stored = prefs.getString(KEY_ALIAS, null)
        return if (stored != null) {
            Base64.decode(stored, Base64.NO_WRAP)
        } else {
            val newKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
            prefs.edit().putString(KEY_ALIAS, Base64.encodeToString(newKey, Base64.NO_WRAP)).apply()
            newKey
        }
    }
}
```

The 32-byte random key is stored in `EncryptedSharedPreferences`, which uses `MasterKey` backed by Android Keystore. The SQLCipher database is opened with this key:

```kotlin
val db = Room.databaseBuilder(context, AppDatabase::class.java, "luopan.db")
    .openHelperFactory(SupportFactory(DatabaseKeyManager.getOrCreateKey(context)))
    .build()
```

**Test strategy (TE-F-08):** Unit and CI tests use `Room.inMemoryDatabaseBuilder` with a plain (non-SQLCipher) database helper via a `@VisibleForTesting` constructor on `CalibrationRepository` that accepts a pre-built `RoomDatabase` instance. This constructor bypasses `DatabaseKeyManager` entirely, allowing `CalibrationRepository.save()` and `CalibrationRepository.getById()` to be exercised on the host JVM without Android Keystore.

SQLCipher encryption and `DatabaseKeyManager` are exercised only in instrumented tests running on an emulator or physical device.

OOM kill atomic write tests (REQ-CAL-04 / REQ §9 Scenario I) run as instrumented tests using `ProcessPhoenix` or equivalent to simulate process restart mid-transaction. These tests cannot run on the host JVM.

### 9.3 Database Schema

```sql
-- calibration_records: max 2 rows (id=1 current, id=2 rollback)
CREATE TABLE calibration_records (
    id INTEGER PRIMARY KEY,
    device_model TEXT NOT NULL,
    sensor_fingerprint TEXT NOT NULL,
    hard_iron_x REAL NOT NULL,
    hard_iron_y REAL NOT NULL,
    hard_iron_z REAL NOT NULL,
    soft_iron_00 REAL NOT NULL, soft_iron_01 REAL NOT NULL, soft_iron_02 REAL NOT NULL,
    soft_iron_10 REAL NOT NULL, soft_iron_11 REAL NOT NULL, soft_iron_12 REAL NOT NULL,
    soft_iron_20 REAL NOT NULL, soft_iron_21 REAL NOT NULL, soft_iron_22 REAL NOT NULL,
    residual_rms REAL NOT NULL,
    quality TEXT NOT NULL,
    saved_at_utc INTEGER NOT NULL,
    wmm_expected_field_ut REAL NOT NULL,
    wmm_source TEXT NOT NULL,
    coverage_x REAL NOT NULL,
    coverage_y REAL NOT NULL,
    coverage_z REAL NOT NULL,
    calibration_schema_version INTEGER NOT NULL DEFAULT 1
);

-- app_settings: simple key-value store for Phase 1 settings
CREATE TABLE app_settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
```

### 9.4 No Network Constraint

`AndroidManifest.xml` MUST NOT declare `android.permission.INTERNET`. This is enforced as a build-time check.

*(PM-FIND-12)* No standard Android Lint rule detects the presence of `INTERNET` permission in a manifest. The custom `NoInternetPermissionCheck` lint rule (detailed below) is the **sole enforcement mechanism** for this constraint. There is no standard `<issue id>` that provides equivalent coverage; the previously referenced `PermissionImpliesUnsafeProtectionLevel` lint ID is unrelated (it flags custom permissions with incorrect `protectionLevel` attributes) and has been removed.

In `build.gradle`:
```groovy
android {
    lintOptions {
        abortOnError true
        error 'HardcodedDebugMode'
        error 'NoInternetPermission'  // Custom rule ID (see below)
    }
}
```

A custom Lint rule `NoInternetPermissionCheck` will be added to the lint module in Phase 1 that fails the build if `INTERNET` permission is found in any merged manifest. The rule ID registered in the Lint registry is `NoInternetPermission`.

---

## 10. Non-Functional Requirements Implementation

### 10.1 REQ-NFR-01 — Heading Latency < 50 ms

**Pipeline design for < 50 ms:**

| Stage | Target time | Notes |
|-------|-------------|-------|
| SensorEvent delivery to `onSensorChanged()` | ~0 ms | Hardware interrupt; OS delivers on `sensorThread` |
| `SensorLayer.onFrame()` + spike filter | < 1 ms | Trivial arithmetic |
| `FusionEngine.update()` (Madgwick) | < 2 ms | ~200 FP ops on ARMv7; well within budget |
| `InterferenceDetector.check()` | < 1 ms | sqrt + divide |
| `ConfidenceModel.compute()` | < 0.5 ms | Integer comparisons |
| `AtomicReference.set()` (publish to Choreographer) | < 0.1 ms | Atomic write |
| **Sensor → publish total** | **< 5 ms** | Well within budget |
| Choreographer frame callback → `StateFlow.emit()` | < 1 ms | On main thread |
| `CompassRoseView.onDraw()` | < 5 ms | Simple canvas rotation |
| **Total (excluding vsync)** | **< 11 ms** | ~4× margin before 50 ms budget |

vsync latency (~16 ms at 60 Hz) is excluded per REQ-NFR-01 Phase 1 implementation note SE-09.

**Instrumentation:**

```kotlin
// In SensorEventListener.onSensorChanged():
val sensorTimestamp_ns = event.timestamp
val processingStart_ns = SystemClock.elapsedRealtimeNanos()

// In Choreographer.FrameCallback:
val drawTimestamp_ns = SystemClock.elapsedRealtimeNanos()
val latency_ms = (drawTimestamp_ns - sensorTimestamp_ns) / 1_000_000.0
// Log to DiagnosticBuffer; report 95th percentile in developer settings
```

### 10.2 REQ-NFR-02 — Heading Update Rate ≥ 20 Hz

- `SENSOR_DELAY_GAME` targets 50 Hz magnetometer delivery.
- `Choreographer.FrameCallback` fires at display frame rate (≥ 60 Hz on target hardware, 20 Hz minimum guaranteed by API 26 baseline).
- `View.invalidate()` is called from within the Choreographer callback; the view system will schedule one `onDraw()` per vsync.
- **Rate verification:** Count `invalidate()` calls producing a changed `headingDeg` value per second; must be ≥ 20.

### 10.3 REQ-NFR-03 — Battery ≤ 5% per Hour

- All `SensorManager.registerListener()` calls use `Activity.onStart()` / `onStop()` (not `onResume()` / `onPause()`). This ensures 0% background sensor usage.
- `PowerManager.WAKE_LOCK` released in `onStop()`.
- No `WorkManager` or `JobScheduler` jobs in Phase 1.
- No background threads continue after `onStop()`.
- No network requests (offline-only).
- ProGuard / R8 enabled in release builds to reduce bytecode size and eliminate dead code.

**Expected power breakdown at 50 Hz magnetometer + 100 Hz accel + 100 Hz gyro:**
- Sensor hardware: ~3% battery/hour (typical for continuous 9-DOF sensing)
- CPU processing: ~0.5% battery/hour (Madgwick at 50 Hz is trivial)
- Display: ~1.5% battery/hour (screen on, 50% brightness, wake lock held)
- **Total estimated: ~5% battery/hour** — at the target boundary; measure with Battery Historian

**Pre-release gate (PM-FIND-10):** Battery usage MUST be verified via Battery Historian at **100% screen brightness** on the reference device before Phase 1 release. The 50% brightness estimate above has no margin for outdoor use (Persona 3, Persona 4 typical conditions) where full brightness is common. If measured usage ≥ 5%/hour at full brightness, reduce `SENSOR_DELAY_GAME` polling interval or implement display brightness adaptation before release. This is a hard gate — the REQ-NFR-03 target is ≤5%/hour and an estimate "at the boundary" does not satisfy it.

### 10.4 REQ-NFR-04 — SDK 26+ / Target SDK 35

```groovy
// app/build.gradle
android {
    defaultConfig {
        minSdk 26
        targetSdk 35
        compileSdk 35
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
```

ABI filters for required architectures:
```groovy
ndk {
    abiFilters += ["arm64-v8a", "armeabi-v7a", "x86_64"]
}
```

### 10.5 REQ-NFR-05 — Offline Operation

Enforced by:
1. No `INTERNET` permission in manifest (lint enforced; see §9.4).
2. No HTTP client library in `dependencies` block of `build.gradle`.
3. `GeomagneticField` API uses bundled coefficients within the Android framework (no network call).
4. SQLCipher database is local file only.

### 10.6 REQ-NFR-07 — APK ≤ 30 MB

- No video assets. Calibration guidance animation is a lightweight Lottie JSON file (< 100 KB) or a programmatic animation.
- Compass rose drawn programmatically on `Canvas` — no bitmap assets.
- All drawables are `VectorDrawable` (XML).
- `commons-math3` is a dependency; ProGuard/R8 will strip unused optimizer classes. Include only `org.apache.commons.math3.fitting.leastsquares` package in ProGuard keep rules.
- ProGuard/R8 enabled in release builds.
- Target APK size: < 15 MB (well within budget to leave room for Phase 2–5 additions).

---

## 11. Key Technical Risks and Mitigations

### Risk P1-R1 — Sensor Rate on Low-End Devices

**Risk:** Android may not honor `SENSOR_DELAY_GAME` on budget API 26 devices (e.g., MediaTek chips, 2018-era mid-range hardware), resulting in magnetometer delivery at 15–20 Hz rather than ~50 Hz.

**Impact:** Heading update rate may drop below the ≥ 20 Hz target (REQ-NFR-02).

**Mitigation:**
1. `SensorRateMonitor` continuously measures effective delivery rate (§2.5). If < 15 Hz for > 2 seconds, show `SensorAdvisory.POWER_SAVING` banner (non-blocking advisory).
2. The Madgwick filter accepts variable-rate input via the `dt` parameter; heading quality degrades gracefully at lower rates rather than failing.
3. Document minimum reference device for QA: Pixel 4a API 34 emulator at default clock speeds. Also test on a physical Snapdragon 660 device (2018 mid-range baseline).
4. If effective rate consistently falls below 15 Hz on tested hardware, evaluate requesting `SensorManager.SENSOR_DELAY_FASTEST` as a fallback. Note: FASTEST may increase battery beyond the 5%/hr target; measure before enabling.

### Risk P1-R2 — Budget Magnetometer Residual Floors

**Risk:** Some devices (particularly MediaTek-based budget phones) have magnetometers with high intrinsic noise floor (±5 µT resolution). After ellipsoid fitting, residual RMS may never reach the "Good" threshold (< 1.0 µT) regardless of how carefully the user performs the figure-8 motion.

**Impact:** These users will permanently receive `CalibrationQuality.FAIR` or `POOR`, capping confidence at `MODERATE` or `POOR`, even under ideal conditions. This may be frustrating and confusing.

**Mitigation:**
1. During calibration quality classification, if the best achieved residual after 3+ full calibration attempts all exceed 2.0 µT, add a device-specific note to the result screen: `"Your device's magnetometer may have limited accuracy. This is normal for some device models."` — do NOT cap quality higher than the residual warrants.
2. In confidence display, when `CalibrationQuality.FAIR` is permanent (residual consistently 1.0–2.0 µT), the confidence cap at Moderate is correct behavior — it accurately represents the achievable accuracy on that hardware.
3. Track `residual_rms` in `CalibrationRecord` for future Phase 4 per-device profiling (REQ-SENSOR-07).
4. Engineering note: if best-achievable residual on the reference device exceeds 2.0 µT after 5 full calibrations, escalate as a hardware defect; do NOT artificially lower the threshold to give users false confidence.

### Risk P1-R3 — Android GeomagneticField Accuracy for Interference Baseline

**Risk:** Android's `GeomagneticField` class uses a WMM epoch that may be stale by 1–5 years at the time of Phase 1 deployment. WMM drift is approximately 0.02%–0.05% per year in field strength and 0.05°–0.1° per year in inclination. Over 5 years this is ~0.25% field strength error and ~0.5° inclination error — small relative to the 15%/3° thresholds but potentially non-trivial in regions of faster secular variation.

**Additional risk:** In regions with very different field strengths than the global average (e.g., Siberia: ~65 µT; equatorial regions: ~30 µT), using the 50 µT fallback when no location is available will produce 30–57% false deviations, triggering spurious interference warnings.

**Mitigation:**
1. When `usingLocationFallback = true`, always show the advisory: `"Location unavailable — interference detection uses approximate field strength"` in the interference warning banner (as specified in FSPEC-DETECT-01).
2. Document this limitation explicitly in the Phase 1 release notes. WMM2025 with GPS arrives in Phase 2 and resolves this.
3. Engineering to verify `GeomagneticField` accuracy against NOAA reference values for at least 5 geographically dispersed test locations during QA; confirm < 2% error at each location. If any location shows > 5% error from `GeomagneticField` API, escalate before Phase 1 release.
4. Consider whether to raise the interference trigger threshold slightly (e.g., from 25% to 28%) in Phase 1 to reduce false positives from GeomagneticField inaccuracy. Decision to be made after QA measurement. Default: keep 25% as specified in REQ; adjust only if false positive rate is confirmed > 5% in field testing.

---

## 12. Android Manifest and Build Configuration

### 12.1 Permissions

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.WAKE_LOCK" />
<!-- ACCESS_FINE_LOCATION is NOT requested in Phase 1; arrives in Phase 2 -->
<!-- INTERNET is explicitly absent; enforced by lint -->
```

`WAKE_LOCK` is a normal permission (no runtime prompt required). It is needed for `REQ-DISPLAY-10`.

No other permissions are required in Phase 1.

### 12.2 Hardware Features

```xml
<uses-feature android:name="android.hardware.sensor.compass"
              android:required="false" />
<uses-feature android:name="android.hardware.sensor.gyroscope"
              android:required="false" />
```

Both features are declared as `required="false"` so the app is installable on devices without these sensors. The app handles their absence programmatically (no-magnetometer error screen; no-gyroscope advisory).

### 12.3 Build Configuration

```groovy
android {
    defaultConfig {
        applicationId "com.luopan.compass"
        minSdk 26
        targetSdk 35
        versionCode 1
        versionName "1.0.0-phase1"
    }
    buildTypes {
        release {
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'),
                          'proguard-rules.pro'
        }
    }
    lint {
        abortOnError true
        warningsAsErrors false
        disable 'ObsoleteLintCustomCheck'
    }
}
```

**ProGuard rules for SQLCipher:**
```
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
```

**ProGuard rules for commons-math3 (keep only LM optimizer):**
```
-keep class org.apache.commons.math3.fitting.leastsquares.** { *; }
-keep class org.apache.commons.math3.linear.** { *; }
-keep class org.apache.commons.math3.optim.** { *; }
```

---

## 13. Dependency Inventory

| Library | Version | Purpose | Why chosen |
|---------|---------|---------|------------|
| `androidx.room:room-runtime` | 2.6.1 | ORM for calibration DB | Type-safe SQL; compile-time verification; transaction support |
| `net.zetetic:android-database-sqlcipher` | 4.5.4 | AES-256 DB encryption | Industry standard for Android encrypted SQLite; Room-compatible |
| `androidx.security:security-crypto` | 1.1.0-alpha06 | EncryptedSharedPreferences for DB key | Android Keystore-backed; no custom key derivation needed |
| `org.apache.commons:commons-math3` | 3.6.1 | Levenberg-Marquardt optimizer for ellipsoid fit | Battle-tested; avoids implementing numerical optimization from scratch; R8 strips unused classes |
| `androidx.lifecycle:lifecycle-viewmodel-ktx` | 2.7.0 | ViewModel + coroutines | Standard Jetpack; StateFlow integration |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.7.3 | Coroutines on Dispatchers.IO for DB writes | Async DB operations; main-thread safety |

**Explicitly excluded dependencies (offline / privacy constraints):**

| Library | Reason for exclusion |
|---------|---------------------|
| Any HTTP client (OkHttp, Retrofit, Ktor) | REQ-NFR-05 offline; no INTERNET permission |
| Firebase / Crashlytics (network-enabled) | REQ-NFR-06 no data transmission. Note: REQ-NFR-09 crash-free rate is measured via a local diagnostic log in Phase 1; Crashlytics integration is a Phase 2 decision pending privacy review. |
| Google Analytics / any analytics SDK | REQ-NFR-06 |
| Compose UI (Jetpack Compose) | Not required for Phase 1. Custom View approach is sufficient and avoids Compose startup overhead on API 26 devices. Compose migration is a Phase 3 option. |
| WMM2025 bundle | Deferred to Phase 2 (REQ-NORTH-01). Not needed in Phase 1; Android GeomagneticField is the fallback. |

---

*End of TSPEC-luopan-p1-core-compass v0.1-draft*
