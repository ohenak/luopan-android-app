# PLAN-luopan-p1-core-compass
## Execution Plan — Phase 1: Core Compass

| Field | Value |
|-------|-------|
| **Document ID** | PLAN-luopan-p1-core-compass |
| **Version** | 0.1-draft |
| **Status** | Draft |
| **Date** | 2026-04-23 |
| **Phase** | 1 of 5 |
| **Author** | Engineering |
| **Parent TSPEC** | [TSPEC-luopan-p1-core-compass.md](TSPEC-luopan-p1-core-compass.md) |
| **Parent REQ** | [REQ-luopan-p1-core-compass.md](REQ-luopan-p1-core-compass.md) |
| **Parent FSPEC** | [FSPEC-luopan-p1-core-compass.md](FSPEC-luopan-p1-core-compass.md) |

---

## Table of Contents

1. [Dependency Graph](#1-dependency-graph)
2. [Batch 0 — Project Scaffold](#2-batch-0--project-scaffold)
3. [Batch 1 — Data Models and Interfaces](#3-batch-1--data-models-and-interfaces)
4. [Batch 2 — Core Algorithms](#4-batch-2--core-algorithms)
5. [Batch 3 — Android Infrastructure](#5-batch-3--android-infrastructure)
6. [Batch 4 — ViewModel and Display](#6-batch-4--viewmodel-and-display)
7. [Batch 5 — Calibration Wizard UI](#7-batch-5--calibration-wizard-ui)
8. [Batch 6 — Integration Wiring and Edge Cases](#8-batch-6--integration-wiring-and-edge-cases)
9. [Risk and Timeline Notes](#9-risk-and-timeline-notes)

---

## 1. Dependency Graph

```
Batch 0 (scaffold)
    └─> Batch 1 (data models + interfaces)
            ├─> Batch 2 (core algorithms)  ──────────────┐
            └─> Batch 3 (Android infra)    ──────────────┤
                                                         ▼
                                             Batch 4 (ViewModel + display)
                                                         │
                                             ┌───────────┴───────────┐
                                             ▼                       ▼
                                     Batch 5 (cal wizard)    Batch 6 (integration)
                                             │                       ▲
                                             └───────────────────────┘
                                          (Batch 6 depends on Batch 5)
```

**Batch parallelism summary:**

| Batch | Depends on | Can run in parallel with |
|-------|-----------|--------------------------|
| Batch 0 | — | — |
| Batch 1 | Batch 0 | — |
| Batch 2 | Batch 1 | Batch 3 |
| Batch 3 | Batch 1 | Batch 2 |
| Batch 4 | Batch 2, Batch 3 | — |
| Batch 5 | Batch 3, Batch 4 | — |
| Batch 6 | Batch 4, Batch 5 | — |

---

## 2. Batch 0 — Project Scaffold

**Gate:** All tasks in this batch must complete before Batch 1 can begin.
**Estimated duration:** 1–2 days (single developer)

---

### T-0-01: Android project creation and SDK configuration

- **Status:** `[ ]`
- **Title:** Create Android project with minSdk 26, targetSdk 35, Kotlin, Jetpack
- **Description:** Create a new Android project with `applicationId "com.luopan.compass"`. Configure `app/build.gradle` with `minSdk 26`, `targetSdk 35`, `compileSdk 35`. Set Kotlin JVM target to 17 and Java source/target compatibility to `JavaVersion.VERSION_17`. Add ABI filters `["arm64-v8a", "armeabi-v7a", "x86_64"]` in the `ndk` block. Set `versionCode 1` and `versionName "1.0.0-phase1"`. Confirm project builds cleanly with a passing empty instrumented test and an empty unit test.
- **TSPEC ref:** §10.4, §12.3
- **REQ ref:** REQ-NFR-04
- **Acceptance check:** `./gradlew assembleDebug` succeeds with no warnings; `./gradlew test` passes; `./gradlew connectedAndroidTest` (on emulator) passes on an empty test suite.
- **Dependencies:** None
- **Status:** `[ ]`

---

### T-0-02: Dependency declarations

- **Status:** `[ ]`
- **Title:** Add all Phase 1 library dependencies to build.gradle
- **Description:** Add the following to `app/build.gradle`: `androidx.room:room-runtime:2.6.1`, `androidx.room:room-ktx:2.6.1`, `kapt("androidx.room:room-compiler:2.6.1")`; `net.zetetic:android-database-sqlcipher:4.5.4`; `androidx.sqlite:sqlite-ktx:2.4.0`; `androidx.security:security-crypto:1.1.0-alpha06`; `org.apache.commons:commons-math3:3.6.1`; `androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0`; `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3`; standard AppCompat and Material Design libraries. Explicitly verify that no HTTP client (OkHttp, Retrofit, Ktor) is present and that no Firebase or analytics SDK is declared. Also add the following test dependencies: `testImplementation "org.robolectric:robolectric:4.11.1"` (required by T-3-04 `SettingsRepository` tests which use `ApplicationProvider.getApplicationContext()`); `androidTestImplementation "com.jakewharton:process-phoenix:2.1.2"` (required by T-5-06 OOM kill simulation test). Add `testOptions { unitTests { includeAndroidResources = true; returnDefaultValues = true } }` to `app/build.gradle` to enable Robolectric resource access.
- **TSPEC ref:** §13
- **REQ ref:** REQ-NFR-05, REQ-NFR-06
- **Acceptance check:** `./gradlew dependencies` output contains all listed libraries with the correct versions; grep for `okhttp`, `retrofit`, `firebase`, `crashlytics`, `analytics` returns no matches in any `build.gradle`. `./gradlew dependencies` confirms `org.robolectric:robolectric:4.11.1` is present under `testRuntimeClasspath`; `com.jakewharton:process-phoenix:2.1.2` is present under `androidTestRuntimeClasspath`.
- **Dependencies:** T-0-01
- **Status:** `[ ]`

---

### T-0-03: ProGuard/R8 configuration

- **Status:** `[ ]`
- **Title:** Configure R8 minification with SQLCipher and commons-math3 keep rules
- **Description:** Enable `minifyEnabled true` and `shrinkResources true` for the `release` build type in `app/build.gradle`. Create or update `proguard-rules.pro` with keep rules for: `net.sqlcipher.**`, `net.sqlcipher.database.**` (SQLCipher), and `org.apache.commons.math3.fitting.leastsquares.**`, `org.apache.commons.math3.linear.**`, `org.apache.commons.math3.optim.**` (commons-math3 LM optimizer). Verify target APK size goal: release build should be well under 30 MB (target < 15 MB).
- **TSPEC ref:** §10.6, §12.3
- **REQ ref:** REQ-NFR-07
- **Acceptance check:** `./gradlew assembleRelease` succeeds; `ls -lh app/build/outputs/apk/release/*.apk` shows size < 15 MB; no `ClassNotFoundException` in smoke tests for SQLCipher or LM optimizer classes at runtime.
- **Dependencies:** T-0-02
- **Status:** `[ ]`

---

### T-0-04: AndroidManifest permissions and hardware features

- **Status:** `[ ]`
- **Title:** Declare WAKE_LOCK permission and hardware feature declarations; explicitly exclude INTERNET
- **Description:** In `AndroidManifest.xml`, declare `<uses-permission android:name="android.permission.WAKE_LOCK" />`. Declare `<uses-feature android:name="android.hardware.sensor.compass" android:required="false" />` and `<uses-feature android:name="android.hardware.sensor.gyroscope" android:required="false" />`. Verify that `android.permission.INTERNET` is absent from the manifest and from any merged manifest (including library transitive manifests). Document the reason in a manifest comment: `<!-- INTERNET permission is explicitly excluded per REQ-NFR-05. Enforced by NoInternetPermission lint rule. -->`.
- **TSPEC ref:** §12.1, §12.2
- **REQ ref:** REQ-NFR-05, REQ-DISPLAY-10, REQ-SENSOR-01, REQ-SENSOR-03
- **Acceptance check:** `./gradlew processDebugManifest` completes; `grep -r "INTERNET" app/build/intermediates/merged_manifests/` returns no matches; `grep "WAKE_LOCK" app/src/main/AndroidManifest.xml` returns one match; `grep "required=\"false\"" app/src/main/AndroidManifest.xml` returns two matches (compass + gyroscope).
- **Dependencies:** T-0-01
- **Status:** `[ ]`

---

### T-0-05: Custom lint rule — NoInternetPermissionCheck

- **Status:** `[ ]`
- **Title:** Implement and register custom lint rule that fails the build if INTERNET permission is present
- **Description:** Create a `lint` module (separate Android library module, e.g., `lint/`) containing a `NoInternetPermissionCheck` class implementing `Detector` and `XmlScanner`. The rule scans `AndroidManifest.xml` and any merged manifest for `<uses-permission android:name="android.permission.INTERNET" />`. If found, it reports an error with rule ID `NoInternetPermission` and severity `ERROR`. Register the rule in a `LintRegistry` class and declare it in the lint module's `META-INF/services/com.android.tools.lint.client.api.IssueRegistry`. Wire to `app/build.gradle` with `lintChecks project(':lint')` and configure `lintOptions { abortOnError true; error 'NoInternetPermission' }`.
- **TSPEC ref:** §9.4, §12.3
- **REQ ref:** REQ-NFR-05
- **Acceptance check:** Temporarily add `<uses-permission android:name="android.permission.INTERNET" />` to `AndroidManifest.xml`; run `./gradlew lint`; build fails with the `NoInternetPermission` error. Remove the INTERNET permission; `./gradlew lint` passes.
- **Dependencies:** T-0-01
- **Status:** `[ ]`

---

### T-0-06: Create and validate CI proxy sensor sequence for TE-F-13

- **Status:** `[ ]`
- **Title:** Create and validate CI proxy sensor sequence for TE-F-13
- **Description:** Record a 30-second sequence of `SensorFrame` values on a calibrated reference device at a known heading (±0.5° verified against an independent hardware compass). Serialize the sequence to `app/src/test/resources/sensor_sequences/known_heading_30s.json`. Commit the file to version control. Ground-truth heading must be verified against an independent reference hardware compass with ±0.5° known accuracy at time of recording. The resource directory must exist before recording begins (created as part of this task).
- **TSPEC ref:** §3.1 (FusionEngine.update CI proxy test)
- **REQ ref:** REQ §9 Scenario C note (CI proxy test)
- **Acceptance check:** The file is present at `app/src/test/resources/sensor_sequences/known_heading_30s.json`, contains ≥ 1500 frames, and `FusionEngineTest` can deserialize and load it without error. Ground-truth heading verified to ±0.5° by a separate hardware compass reading at time of recording.
- **Dependencies:** T-0-01 (project scaffold must exist for resource directory)
- **Status:** `[ ]`

---

## 3. Batch 1 — Data Models and Interfaces

**Gate:** All tasks in this batch must complete before Batch 2 and Batch 3 can begin. Tasks within this batch have no intra-batch dependencies and can be implemented in parallel.
**Estimated duration:** 1–2 days (multiple developers in parallel)

---

### T-1-01: SensorFrame data class

- **Status:** `[ ]`
- **Title:** Implement `SensorFrame` data class with nullable gyro fields
- **Description:** Create `data class SensorFrame` in package `com.luopan.compass.sensor` with fields: `timestamp_ns: Long`, `mag_x/y/z: Float`, `mag_bias_x/y/z: Float` (0.0f when using fallback TYPE_MAGNETIC_FIELD), `accel_x/y/z: Float`, `gyro_x/y/z: Float?` (null when TYPE_GYROSCOPE unavailable). No logic — pure data holder. The class must be a `data class` (not `class`) to get structural equality for test assertions.
- **TSPEC ref:** §2.3
- **REQ ref:** REQ-SENSOR-01, REQ-SENSOR-02, REQ-SENSOR-03
- **Acceptance check:** Unit test: instantiate `SensorFrame` with all non-null fields, assert `copy()` produces structural equality; instantiate with null gyro fields, assert `gyro_x == null`; test compiles and passes.
- **Dependencies:** None (within Batch 1)
- **Status:** `[ ]`

---

### T-1-02: FusionResult data class

- **Status:** `[ ]`
- **Title:** Implement `FusionResult` data class
- **Description:** Create `data class FusionResult` in package `com.luopan.compass.fusion` with fields: `heading_deg: Double` (range [0.0, 360.0)), `pitch_deg: Double` ([-90.0, 90.0]), `roll_deg: Double` ([-180.0, 180.0]), `tilt_deg: Double` (≥ 0.0, computed as `sqrt(pitch² + roll²)`), `timestamp_ns: Long`. No logic — pure data holder.
- **TSPEC ref:** §3.3
- **REQ ref:** REQ-SENSOR-04
- **Acceptance check:** Unit test: instantiate with representative values; assert structural equality via `copy()`; confirm `data class` (not plain class).
- **Dependencies:** None (within Batch 1)
- **Status:** `[ ]`

---

### T-1-03: CalibrationRecord Room entity

- **Status:** `[ ]`
- **Title:** Implement `CalibrationRecord` data class annotated as a Room `@Entity`
- **Description:** Create `@Entity(tableName = "calibration_records") data class CalibrationRecord` in package `com.luopan.compass.calibration` with the exact fields specified in TSPEC §4.6: `@PrimaryKey val id: Int` (1=current, 2=rollback), `device_model: String`, `sensor_fingerprint: String`, `hard_iron_x/y/z: Float`, `soft_iron_00` through `soft_iron_22: Float` (9 floats, flat row-major), `residual_rms: Float`, `quality: String` ("GOOD"|"FAIR"|"POOR"), `saved_at_utc: Long`, `wmm_expected_field_ut: Float` (-1.0f signals 50 µT fallback), `wmm_source: String` ("GeomagneticField"|"fallback_50uT"), `coverage_x/y/z: Float`, `calibration_schema_version: Int = 1`.
- **TSPEC ref:** §4.6
- **REQ ref:** REQ-CAL-04
- **Acceptance check:** Unit test: instantiate `CalibrationRecord` with `id=1`, verify all 24+ fields present; Room compilation (`./gradlew kaptDebugKotlin`) produces no errors on this entity; `copy(id = 2)` works (used in rollback logic).
- **Dependencies:** None (within Batch 1)
- **Status:** `[ ]`

---

### T-1-04: Enum types — OverallConfidence, InterferenceState, CalibrationQuality, SensorAdvisory, SensorState, CalDotColor

- **Status:** `[ ]`
- **Title:** Implement all Phase 1 enum types
- **Description:** Create the following enums in `com.luopan.compass.model`: `enum class OverallConfidence { HIGH, MODERATE, POOR, STABILIZING, SENSOR_ERROR }`; `enum class InterferenceState { CLEAR, MODERATE, WARNING }`; `enum class CalibrationQuality { GOOD, FAIR, POOR }`; `enum class SensorAdvisory { NONE, NO_GYROSCOPE, POWER_SAVING, STABILIZING }`; `enum class SensorState { NORMAL, STABILIZING, STUCK }`; `enum class CalDotColor { GREEN, AMBER, RED }`. Also create `enum class ConfidenceScore(val numericValue: Int) { GOOD(3), MODERATE(2), POOR(1) }` in `com.luopan.compass.confidence`. All enums must be in Kotlin files; no additional logic or companion objects beyond what is specified.
- **TSPEC ref:** §2.6, §5.4, §6.1, §6.4, §7.2
- **REQ ref:** REQ-SENSOR-01, REQ-SENSOR-03, REQ-SENSOR-04, REQ-SENSOR-06, REQ-DETECT-01, REQ-DETECT-02
- **Acceptance check:** Compile check only — `./gradlew compileDebugKotlin` succeeds; unit test: exhaustive `when` over each enum compiles without an `else` branch (proving all cases are enumerated).
- **Dependencies:** None (within Batch 1)
- **Status:** `[ ]`

---

### T-1-05: LocationProvider functional interface and TimeSource interface

- **Status:** `[ ]`
- **Title:** Implement injectable `LocationProvider` and `TimeSource` interfaces per TSPEC §5.0
- **Description:** Create `fun interface LocationProvider { fun getLastKnownLocation(): android.location.Location? }` in `com.luopan.compass.sensor`. Create `interface TimeSource { fun nowMs(): Long; fun nowNs(): Long }` and its production implementation `object SystemTimeSource : TimeSource` (using `System.currentTimeMillis()` and `SystemClock.elapsedRealtimeNanos()`). Create `class SystemLocationProvider(private val context: Context) : LocationProvider` that tries `NETWORK_PROVIDER` first, falls back to `PASSIVE_PROVIDER`, wraps in `runCatching`. All are in `com.luopan.compass.sensor`. These interfaces are the seams for deterministic unit testing without Robolectric.
- **TSPEC ref:** §5.0
- **REQ ref:** REQ-DETECT-01, REQ-DETECT-02, REQ-CAL-02
- **Acceptance check:** Unit test: inject `LocationProvider { null }` and assert `getLastKnownLocation()` returns null; inject `LocationProvider { mockLocation }` and assert it returns `mockLocation`; inject a fake `TimeSource` with fixed values, assert `nowMs()` and `nowNs()` return the injected values.
- **Dependencies:** None (within Batch 1)
- **Status:** `[ ]`

---

### T-1-06: DiagnosticBuffer generic class

- **Status:** `[ ]`
- **Title:** Implement thread-safe `DiagnosticBuffer<T>` circular buffer
- **Description:** Create `class DiagnosticBuffer<T : Comparable<T>>(val capacity: Int)` in `com.luopan.compass.diagnostics` with methods: `fun add(value: T)` (evicts oldest when at capacity), `fun percentile(p: Double): T` (sorts a snapshot, returns value at `floor(size * p)`, where p ∈ [0.0, 1.0]), `val size: Int`. Thread safety: protect with `ReentrantReadWriteLock` — `add()` acquires write lock; `percentile()` acquires read lock on snapshot copy. `add()` must be allocation-free in the hot path when the buffer is not at capacity (no new ArrayDeque allocation per call).
- **TSPEC ref:** §5.3
- **REQ ref:** REQ-DETECT-04, REQ-NFR-01
- **Acceptance check:** Unit tests: (1) add `capacity + 1` elements, verify `size == capacity` and oldest element is evicted; (2) add [1, 2, 3, 4, 5], assert `percentile(0.0)` = 1, `percentile(1.0)` = 5, `percentile(0.5)` = 3; (3) two threads calling `add()` concurrently on 10,000 iterations — no `ConcurrentModificationException`.
- **Dependencies:** None (within Batch 1)
- **Status:** `[ ]`

---

### T-1-07: CompassUiState data class

- **Status:** `[ ]`
- **Title:** Implement `CompassUiState` data class with `INITIAL` companion object
- **Description:** Create `data class CompassUiState` in `com.luopan.compass.ui` with all fields specified in TSPEC §7.2: `heading_deg: Double`, `heading_formatted: String`, `north_label: String`, `confidence: OverallConfidence`, `interference_state: InterferenceState`, `interference_metrics: InterferenceMetrics?`, `tilt_deg: Double`, `tilt_text: String?`, `calibration_age_days: Long` (-1 if uncalibrated), `calibration_age_label: String`, `cal_dot_color: CalDotColor`, `power_saving_advisory: Boolean`, `no_gyroscope_advisory: Boolean`, `fallback_mag_advisory: Boolean`, `location_fallback_advisory: Boolean`, `sensor_state: SensorState`, `is_stabilizing: Boolean`, `last_valid_heading_deg: Double?`, `show_calibration_cta: Boolean`. Implement `companion object { val INITIAL = CompassUiState(...) }` with the exact initial field values from TSPEC §7.2 (`heading_formatted = "---"`, `confidence = OverallConfidence.POOR`, `calibration_age_days = -1`, `calibration_age_label = "Uncalibrated"`, `show_calibration_cta = true`, etc.). Also define `data class InterferenceMetrics` (TSPEC §5.2) in `com.luopan.compass.sensor`.
- **TSPEC ref:** §7.2, §5.2
- **REQ ref:** REQ-DISPLAY-02, REQ-DISPLAY-03, REQ-DETECT-01, REQ-DETECT-02
- **Acceptance check:** Unit test: `CompassUiState.INITIAL.show_calibration_cta == true`; `CompassUiState.INITIAL.heading_formatted == "---"`; `CompassUiState.INITIAL.calibration_age_days == -1L`; all 19 fields accessible by name; `copy()` with one changed field preserves all others.
- **Dependencies:** T-1-04 (enums must exist to be referenced as field types)
- **Status:** `[ ]`

---

### T-1-08: MagSample, Vector3, Matrix3x3, CalibrationResult data classes

- **Status:** `[ ]`
- **Title:** Implement calibration algorithm data classes
- **Description:** Create the following in `com.luopan.compass.calibration`: `data class MagSample(val x: Float, val y: Float, val z: Float, val timestamp_ns: Long)`; `data class Vector3(val x: Float, val y: Float, val z: Float)`; `data class Matrix3x3(val m00: Float, val m01: Float, val m02: Float, val m10: Float, val m11: Float, val m12: Float, val m20: Float, val m21: Float, val m22: Float)`; `data class CoverageStats(val coverage_x: Float, val coverage_y: Float, val coverage_z: Float)`; `data class CalibrationResult(val hard_iron: Vector3, val soft_iron: Matrix3x3, val residual_rms: Float, val coverage: CoverageStats, val quality: CalibrationQuality, val target_radius: Float)`. Pure data classes — no logic.
- **TSPEC ref:** §4.1, §4.2, §4.4
- **REQ ref:** REQ-CAL-02, REQ-CAL-03
- **Acceptance check:** Compile check: `./gradlew compileDebugKotlin` succeeds; unit test: instantiate each data class with representative values and assert structural equality via `copy()`.
- **Dependencies:** T-1-04 (CalibrationQuality enum)
- **Status:** `[ ]`

---

## 4. Batch 2 — Core Algorithms

**Gate:** All tasks in Batch 1 must be complete. Tasks within Batch 2 have no intra-batch dependencies and can be implemented in parallel.
**Estimated duration:** 3–5 days (multiple developers in parallel; ellipsoid fitting is the longest task)

---

### T-2-01: MadgwickFilter class

- **Status:** `[ ]`
- **Title:** Implement `MadgwickFilter` class with quaternion state and both update paths
- **Description:** Create `class MadgwickFilter(var beta: Float = 0.05f)` in `com.luopan.compass.fusion`. Instance fields: `var q0: Float = 1.0f`, `var q1: Float = 0.0f`, `var q2: Float = 0.0f`, `var q3: Float = 0.0f`. Implement `fun update(gx, gy, gz, ax, ay, az, mx, my, mz, dt: Float)` — the full Madgwick AHRS update with gyroscope. Implement `fun updateNoGyro(ax, ay, az, mx, my, mz, dt: Float)` — the accelerometer-only path used when no gyroscope is available. Both methods must re-normalize the quaternion at the end of every call to prevent drift. `beta` is fixed at 0.05f and must NOT be changed at runtime; it has no setter beyond the constructor parameter (can be changed in tests only). This is a `class`, NOT an `object` — each test creates a fresh instance to avoid shared mutable state across parallel test runs (TSPEC TE-F-01).
- **TSPEC ref:** §3.1, §3.2
- **REQ ref:** REQ-SENSOR-04
- **Acceptance check:** Unit tests: (1) after 100 `update()` calls with synthetic gravity-only input (no gyro), quaternion remains unit-length (`sqrt(q0² + q1² + q2² + q3²) ≈ 1.0f ± 0.0001`); (2) `updateNoGyro()` convergence: inject `updateNoGyro()` with `ax=0, ay=0, az=-9.81` (gravity down), `mx=30, my=0, mz=-30` (mag field pointing North-down), `dt=0.02f`; after 50 iterations assert `quaternionToHeadingDeg(q0, q1, q2, q3)` is within ±5.0° of 0.0° (North). Tolerance is ±5° (convergence speed test, not final accuracy — final accuracy is tested by the CI proxy test in T-2-02); (3) two `MadgwickFilter` instances in the same test have independent state (mutating one does not affect the other).
- **Dependencies:** None (within Batch 2)
- **Status:** `[ ]`

---

### T-2-02: FusionEngine class with quaternion-to-heading extraction

- **Status:** `[ ]`
- **Title:** Implement `FusionEngine` wrapping `MadgwickFilter`; public `update(SensorFrame)` entry point; gimbal-lock-safe heading extraction
- **Description:** Create `class FusionEngine` in `com.luopan.compass.fusion`. It holds a single `MadgwickFilter()` instance. Implement `fun update(frame: SensorFrame): FusionResult` as the public entry point: (1) extract calibrated mag from frame (applying any persisted calibration via `applyCorrectedMag()` — stub this call to passthrough initially, wired in Batch 6); (2) dispatch to `MadgwickFilter.update()` or `updateNoGyro()` based on `frame.gyro_x == null`; (3) compute `dt` from `frame.timestamp_ns` delta; (4) call `quaternionToHeadingDeg()` and `computeTiltDeg()` to produce `FusionResult`. Implement `fun quaternionToHeadingDeg(q0, q1, q2, q3: Float): Double` using the rotation matrix formula from TSPEC §3.4 (gimbal-lock-safe via `atan2(R[1], R[4])`). Implement `fun computeTiltDeg(pitch_deg, roll_deg: Double): Double = sqrt(pitch² + roll²)`. `FusionEngine` must have NO direct dependency on `SensorLayer` — `SensorLayer` calls `FusionEngine.update()`, not the reverse.
- **TSPEC ref:** §3.1, §3.4, §3.5, §3.6
- **REQ ref:** REQ-SENSOR-04, SE-06
- **Acceptance check:** Unit tests: (1) inject synthetic `SensorFrame` with pitch=90°-equivalent accelerometer values; assert `FusionResult.heading_deg` is finite and in [0.0, 360.0) (TE-F-09 — gimbal-lock test); (2) inject a 30-second pre-recorded sensor sequence at a known heading; assert computed heading is within ±0.5° of ground truth (CI proxy test TE-F-13); (3) call `update()` with null gyro fields, assert it uses the `updateNoGyro` path without crashing.
- **Dependencies:** T-2-01, T-0-06 (pre-recorded sensor sequence file must exist before CI proxy acceptance test can run)
- **Status:** `[ ]`

---

### T-2-03: CalibrationEngine — ellipsoid fitting and coverage computation

- **Status:** `[ ]`
- **Title:** Implement `CalibrationEngine` with Levenberg-Marquardt ellipsoid fit, coverage computation, quality classification, and correction application
- **Description:** Create `class CalibrationEngine` in `com.luopan.compass.calibration`. Implement `fun computeCoverage(samples: List<MagSample>): CoverageStats` using the exact formula from TSPEC §4.2 (axis ranges divided by max total range; returns all-zero `CoverageStats` if `totalRange == 0f`). Implement `suspend fun fit(samples: List<MagSample>, locationProvider: LocationProvider): CalibrationResult` using `LevenbergMarquardtOptimizer` from `commons-math3` to solve for `hard_iron: Vector3` (3 parameters) and `soft_iron: Matrix3x3` (9 parameters) such that `|A * (sample - b)| ≈ targetRadius`. Call `getTargetRadius(locationProvider)` to get the sphere radius (uses `GeomagneticField` if location available, else 50.0f). Compute `residual_rms` as RMS of per-sample residuals. Classify quality per TSPEC §4.4: GOOD if residual ≤ 1.0 µT AND all coverage ≥ 0.20; FAIR if residual ≤ 2.0 µT AND all coverage ≥ 0.15; POOR otherwise. Implement `fun applyCorrectedMag(raw_x, raw_y, raw_z: Float, calibration: CalibrationRecord?): Triple<Float, Float, Float>` (passthrough if calibration is null; else hard-iron subtraction then soft-iron matrix multiply per TSPEC §4.5). `fit()` runs on `Dispatchers.Default`; it must not block the main thread. **Optional split:** T-2-03a (`computeCoverage`, quality classification, `applyCorrectedMag` — 1 day, low risk) and T-2-03b (LM ellipsoid fitting — 3–4 days, high risk). If LM fitting blocks, T-2-03a's `applyCorrectedMag` can unblock T-6-01 earlier. Engineering discretion.
- **TSPEC ref:** §4.2, §4.3, §4.4, §4.5
- **REQ ref:** REQ-CAL-01, REQ-CAL-02, REQ-CAL-03, SE-07, SE-02
- **Acceptance check:** Unit tests: (1) `computeCoverage()` with 3 samples spanning only X-axis: `coverage_y == 0f` and `coverage_z == 0f`; (2) quality boundary: residual = 1.0 µT + all coverage ≥ 0.20 → GOOD; residual = 1.001 µT + coverage ≥ 0.15 → FAIR; residual = 2.001 µT → POOR (TE-F-02); (3) `applyCorrectedMag()` with null calibration returns the raw values unchanged; (4) `fit()` on a synthetic sphere dataset (500 points on a sphere of radius 50 µT with known hard-iron offset) recovers the hard-iron vector within 1 µT.
- **Dependencies:** T-1-05 (LocationProvider), T-1-08 (MagSample, Vector3, Matrix3x3, CalibrationResult, CoverageStats)
- **Status:** `[ ]`

---

### T-2-04: NoiseSpikeFilter class

- **Status:** `[ ]`
- **Title:** Implement `NoiseSpikeFilter` class with rolling-mean spike detection
- **Description:** Create `class NoiseSpikeFilter(private val windowSize: Int = 20, private val spikeThreshold: Float = 10.0f)` in `com.luopan.compass.sensor`. Internal state: `private val recentValues = ArrayDeque<Triple<Float, Float, Float>>(windowSize)`. Implement `fun filter(x: Float, y: Float, z: Float): Triple<Float, Float, Float>?` per TSPEC §5.3: compute deviation as 3D Euclidean distance from rolling mean; return `null` (rejected, log to `DiagnosticBuffer`) if `recentValues.size >= 2 AND deviation > spikeThreshold`; else add to window (evict oldest if full) and return the sample. Boundary: deviation exactly equal to `spikeThreshold` (10.0f) is ACCEPTED (`<= threshold` condition). Log rejected samples to a `DiagnosticBuffer<String>` injected via constructor (default: `DiagnosticBuffer(100)`).
- **TSPEC ref:** §5.3
- **REQ ref:** REQ-DETECT-04
- **Acceptance check:** Unit tests per TE-F-05: (1) deviation = 10.0 µT → sample accepted, `filter()` returns non-null; (2) deviation = 10.001 µT → sample rejected, `filter()` returns null; (3) with `recentValues.size < 2`, all samples are accepted (no false rejections during initialization).
- **Dependencies:** T-1-06 (DiagnosticBuffer)
- **Status:** `[ ]`

---

### T-2-05: InterferenceDetector class with state machine

- **Status:** `[ ]`
- **Title:** Implement `InterferenceDetector` with expected field computation, deviation metrics, and hysteretic state machine
- **Description:** Create `class InterferenceDetector(private val locationProvider: LocationProvider, private val timeSource: TimeSource)` in `com.luopan.compass.sensor`. Implement `fun updateExpectedField()` using `GeomagneticField` (TSPEC §5.1); fallback to 50.0 µT / 45.0° when no location. Implement `fun computeMetrics(calibrated_x, calibrated_y, calibrated_z: Float): InterferenceMetrics` per TSPEC §5.2 (field magnitude, inclination, deviation ratios). Implement `fun updateState(metrics: InterferenceMetrics, nowNs: Long): InterferenceState` — the hysteretic state machine from TSPEC §5.4: WARNING triggers at fieldDeviation ≥ 0.25f OR inclinationDev ≥ 8.0f (reset clearance timer); clearance requires both < 0.15f field AND < 3.0f° inclination, sustained for CLEARANCE_DURATION_NS = 3,000,000,000 ns; any excursion resets the clearance timer to 0. Expose `var currentState: InterferenceState = InterferenceState.CLEAR` as a readable field (TE-F-07).
- **TSPEC ref:** §5.1, §5.2, §5.4
- **REQ ref:** REQ-DETECT-01, REQ-DETECT-02, TE-11
- **Acceptance check:** Unit tests using injected fake `TimeSource` and `LocationProvider { null }` (fallback): (1) deviation 0.30 → state transitions to WARNING immediately; (2) deviation drops to 0.10 → clearance timer starts; advance fake clock by 2.9 s → state still WARNING; advance by 0.1 s → state clears to CLEAR; (3) deviation drops to 0.10, then spikes to 0.16 at t=2s → timer resets; must wait full 3s from reset point to clear.
- **Dependencies:** T-1-04 (enums), T-1-05 (LocationProvider, TimeSource), T-1-07 (InterferenceMetrics)
- **Status:** `[ ]`

---

### T-2-06: ConfidenceModel class with five-dimension scoring and tilt clamp

- **Status:** `[ ]`
- **Title:** Implement `ConfidenceModel` computing `OverallConfidence` from five dimensions plus tilt clamp and no-gyroscope cap
- **Description:** Create `class ConfidenceModel` (stateless — no instance fields) in `com.luopan.compass.confidence`. Implement `fun compute(fieldDeviation: Float, inclinationDeviation_deg: Float, calibration: CalibrationRecord?, noiseVariance_uT2: Float, tilt_deg: Double, hasGyroscope: Boolean, sensorState: SensorState): OverallConfidence` per TSPEC §6.3: short-circuit on `STUCK` → `SENSOR_ERROR`; on `STABILIZING` → `STABILIZING`. Compute five `ConfidenceScore` values per §6.1 tables. Step 1: `minOf` all five numeric values. Step 2: tilt clamp — `tilt > 20.0` → clamp to POOR; `tilt > 5.0` → clamp to MODERATE (exact boundary: `tilt = 5.0` → no clamp; `tilt = 5.1` → MODERATE clamp; `tilt = 20.0` → MODERATE clamp; `tilt = 20.1` → POOR clamp, per TE-F-02). Step 3: no-gyroscope cap — `minOf(capped, MODERATE.numericValue)` if `!hasGyroscope`. Map final value 3→HIGH, 2→MODERATE, 1→POOR. Implement `NoiseVarianceTracker(windowSize: Int = 20)` (TSPEC §6.2) as a separate class in the same package.
- **TSPEC ref:** §6.1, §6.2, §6.3, §6.4
- **REQ ref:** REQ §5.3, TE-04
- **Acceptance check:** Unit tests per TE-F-02 boundary table: (1) tilt=5.0 → no clamp; (2) tilt=5.1 → MODERATE clamp applied; (3) tilt=20.0 → MODERATE (not POOR); (4) tilt=20.1 → POOR; (5) residual=1.0 µT + coverage≥20% → cal_quality=GOOD; (6) residual=1.001 µT + coverage≥15% → MODERATE; (7) residual=2.001 µT → POOR; (8) noise=0.5 µT² → MODERATE; (9) noise=0.501 µT² → POOR; (10) cal_age=7d → GOOD; (11) cal_age=30d → MODERATE; (12) cal_age=31d → POOR; (13) sensorState=STUCK → SENSOR_ERROR overrides all scoring.
- **Dependencies:** T-1-03 (CalibrationRecord), T-1-04 (enums), T-1-06 (DiagnosticBuffer)
- **Status:** `[ ]`

---

### T-2-07: SensorRateMonitor and SensorStateMonitor classes

- **Status:** `[ ]`
- **Title:** Implement `SensorRateMonitor` (power-saving detection) and `SensorStateMonitor` (stuck/rapid-rotation detection)
- **Description:** Create `class SensorRateMonitor(windowMs: Long = 2000L, threshold: Float = 15.0f, timeSource: TimeSource)` in `com.luopan.compass.sensor` per TSPEC §2.5. Implement `fun onEvent(timestampNs: Long): Float` — maintains `ArrayDeque<Long>` of event timestamps, evicts entries older than `windowNs`, returns effective Hz. Create `class SensorStateMonitor` in the same package implementing: (1) `SensorState.STABILIZING` detection via gyroscope angular rate > 180°/s sustained > 0.5 s; exit when rate < 10°/s for ≥ 1.0 s (TSPEC §2.6); (2) `SensorState.STUCK` detection — immediate on {0,0,0} sample; variance detection over last min(N, 50) magnetometer samples: ALL three axes must have population variance < 0.001 µT² (not just one axis); recovery when all-axis variance > 0.001 µT² for 1 full second (TSPEC §2.7).
- **TSPEC ref:** §2.5, §2.6, §2.7
- **REQ ref:** REQ-NFR-02, REQ-NFR-03, REQ-SENSOR-06
- **Acceptance check:** Unit tests using injected `TimeSource`: (1) `SensorRateMonitor`: 20 events in 2 seconds → returns ~10 Hz (below threshold); 50 events in 2 seconds → returns ~25 Hz (above); (2) `SensorStateMonitor`: all-zero sample → immediately returns `STUCK`; single-axis constant values but other axes varying → NOT STUCK; variance recovery after 1 second clears STUCK; gyro rate > 180°/s for 600 ms → STABILIZING; gyro drops to 5°/s for 1.1 s → NORMAL.
- **Dependencies:** T-1-04 (SensorState enum), T-1-05 (TimeSource)
- **Status:** `[ ]`

---

## 5. Batch 3 — Android Infrastructure

**Gate:** All tasks in Batch 1 must be complete. Tasks within Batch 3 have no intra-batch dependencies and can be implemented in parallel.
**Estimated duration:** 2–3 days (multiple developers in parallel; SQLCipher setup is the most complex task)

---

### T-3-01: SensorLayer class

- **Status:** `[ ]`
- **Title:** Implement `SensorLayer` handling sensor registration, sensor probe sequence, and HandlerThread dispatch
- **Description:** Create `class SensorLayer(context: Context)` in `com.luopan.compass.sensor`. Implement the sensor probe sequence from TSPEC §2.1 and §2.2: probe `TYPE_MAGNETIC_FIELD_UNCALIBRATED` → fallback to `TYPE_MAGNETIC_FIELD` (set `usingFallbackMag = true`); probe `TYPE_GRAVITY` → fallback to `TYPE_ACCELEROMETER` (set `usingRawAccel = true`, apply IIR low-pass filter with α=0.1 per TSPEC §2.4); probe `TYPE_GYROSCOPE` (set `hasGyroscope = true/false`). Create a `HandlerThread("SensorThread")` and a `Handler` on it. Register all listeners with `SENSOR_DELAY_GAME` and `sensorHandler`. Implement `SensorEventListener.onSensorChanged()` to assemble `SensorFrame` from latest values of each sensor type, then call the injected callback (`onFrame: (SensorFrame) -> Unit`). Implement `fun start()` (called from `Activity.onStart()`) and `fun stop()` (called from `Activity.onStop()`). If `magSensor == null` after both probes, invoke `onNoMagnetometer` callback instead of starting.
- **TSPEC ref:** §2.1, §2.2, §2.3, §2.4
- **REQ ref:** REQ-SENSOR-01, REQ-SENSOR-02, REQ-SENSOR-03, REQ-SENSOR-05, SE-05
- **Acceptance check:** Instrumented test (emulator): verify sensor registration calls occur; verify `onStop()` unregisters all listeners; verify `usingFallbackMag` is `false` on a standard emulator (which has `TYPE_MAGNETIC_FIELD_UNCALIBRATED`); verify `onFrame` callback fires on `sensorThread` (not main thread).
- **Dependencies:** T-1-01 (SensorFrame)
- **Status:** `[ ]`

---

### T-3-02: DatabaseKeyManager and EncryptedSharedPreferences key storage

- **Status:** `[ ]`
- **Title:** Implement `DatabaseKeyManager` using Android Keystore and EncryptedSharedPreferences
- **Description:** Create `object DatabaseKeyManager` in `com.luopan.compass.persistence` per TSPEC §9.2. Implement `fun getOrCreateKey(context: Context): ByteArray`: check `EncryptedSharedPreferences` for existing key (Base64-encoded 32-byte array stored under alias `luopan_sqlcipher_key`); if found, decode and return; if not found, generate via `SecureRandom().nextBytes(ByteArray(32))`, Base64-encode, store in `EncryptedSharedPreferences` backed by `MasterKey.KeyScheme.AES256_GCM`, and return. Use `EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV` and `PrefValueEncryptionScheme.AES256_GCM`. This is an instrumented-test-only component (requires Android Keystore); no JVM unit test needed.
- **TSPEC ref:** §9.2
- **REQ ref:** REQ-NFR-06, REQ-CAL-04
- **Acceptance check:** Instrumented test (emulator): (1) first call to `getOrCreateKey()` returns a 32-byte array; (2) second call returns the identical bytes (idempotency); (3) EncryptedSharedPreferences contains exactly one entry after two calls.
- **Dependencies:** T-0-02 (dependency declarations)
- **Status:** `[ ]`

---

### T-3-03: Room database definition, DAOs, and CalibrationRepository

- **Status:** `[ ]`
- **Title:** Define Room database schema, DAOs, and implement `CalibrationRepository` with SQLCipher and rollback logic
- **Description:** Create `@Database(entities = [CalibrationRecord::class], version = 1) abstract class AppDatabase : RoomDatabase()` in `com.luopan.compass.persistence`. Create `@Dao interface CalibrationDao` with: `@Insert(onConflict = REPLACE) suspend fun insert(record: CalibrationRecord)`, `@Query("DELETE FROM calibration_records WHERE id = :id") suspend fun delete(id: Int)`, `@Query("SELECT * FROM calibration_records WHERE id = :id") suspend fun getById(id: Int): CalibrationRecord?`, `@Query("SELECT COUNT(*) FROM calibration_records") suspend fun count(): Int`. Create `class CalibrationRepository` with primary constructor `CalibrationRepository(context: Context)` that opens the database via `Room.databaseBuilder` with `SupportFactory(DatabaseKeyManager.getOrCreateKey(context))`. Provide a `@VisibleForTesting constructor(db: RoomDatabase)` overload that accepts an in-memory test database (bypasses SQLCipher). Implement `@Transaction suspend fun saveCalibration(newRecord: CalibrationRecord)` using the atomic promote-to-rollback logic from TSPEC §4.6. Implement `suspend fun hasAnyCalibration(): Boolean` (returns `count() > 0`). Implement `suspend fun getCurrentCalibration(): CalibrationRecord?` (returns `getById(1)`).
- **TSPEC ref:** §4.6, §9.1, §9.2, §9.3
- **REQ ref:** REQ-CAL-04, REQ-NFR-06, TE-07
- **Acceptance check:** JVM unit test using in-memory database via `@VisibleForTesting` constructor: (1) `saveCalibration(record1)` → `getCurrentCalibration()` returns `record1` with `id=1`; (2) `saveCalibration(record2)` → `id=1` is `record2`, `id=2` is `record1` (rollback); (3) `saveCalibration(record3)` → `id=1` is `record3`, `id=2` is `record2` (oldest record `record1` is gone). Instrumented test: verify SQLCipher encryption (cannot open database file with a plain SQLite client).
- **Dependencies:** T-1-03 (CalibrationRecord), T-3-02 (DatabaseKeyManager)
- **Status:** `[ ]`

---

### T-3-04: SettingsRepository class

- **Status:** `[ ]`
- **Title:** Implement `SettingsRepository` wrapping SharedPreferences for Phase 1 app settings
- **Description:** Create `class SettingsRepository(context: Context)` in `com.luopan.compass.persistence`. For Phase 1, this is a thin wrapper around standard `SharedPreferences` (plain, not encrypted — calibration storage uses SQLCipher; settings are non-sensitive). Implement `fun getBoolean(key: String, default: Boolean): Boolean` and `fun setBoolean(key: String, value: Boolean)`. Provide a `hasCalibrationCtaDismissed()` convenience method returning `getBoolean("cal_cta_dismissed", false)`. The repository is synchronous (reads on any thread, writes with `apply()`). Phase 1 has no user-configurable settings toggle for wake lock or calibration; this class exists as the stub for future phases.
- **TSPEC ref:** §1.2
- **REQ ref:** REQ-NFR-06
- **Acceptance check:** Unit test with `ApplicationProvider.getApplicationContext()` (Robolectric): write `setBoolean("test_key", true)`, read back `getBoolean("test_key", false)`, assert `true`; verify `hasCalibrationCtaDismissed()` returns `false` by default.
- **Dependencies:** T-0-01
- **Status:** `[ ]`

---

## 6. Batch 4 — ViewModel and Display

**Gate:** All tasks in Batch 2 (core algorithms) and Batch 3 (Android infra) must be complete. Tasks within Batch 4 have no intra-batch dependencies and can run in parallel.
**Estimated duration:** 3–4 days (multiple developers in parallel)

---

### T-4-01: CompassViewModel with StateFlow and Choreographer FrameCallback

- **Status:** `[ ]`
- **Title:** Implement `CompassViewModel` with `StateFlow<CompassUiState>`, `AtomicReference<FusionResult>`, and Choreographer frame-rate gating
- **Description:** Create `class CompassViewModel(application: Application) : AndroidViewModel(application)` in `com.luopan.compass.ui`. Expose `val uiState: StateFlow<CompassUiState>` backed by `MutableStateFlow(CompassUiState.INITIAL)`. Implement `fun onFusionResult(result: FusionResult)` (called from `sensorThread`) that atomically stores to `latestFusion: AtomicReference<FusionResult>`. Implement `Choreographer.FrameCallback` registered in `onStart()` and unregistered in `onStop()` that reads from `latestFusion`, calls `buildUiState(...)`, and emits to `_uiState` via `viewModelScope.launch(Dispatchers.Main.immediate)`. Implement `internal fun buildUiState(fusion: FusionResult, confidence: OverallConfidence, interferenceState: InterferenceState, calibrationAgeDays: Int?, hasCalibration: Boolean, isPowerSaving: Boolean): CompassUiState` as a **pure function** with no side effects (TE-F-04) — it must not access any repository, context, or Android framework object. Implement `fun onAppStart()` that calls `CalibrationRepository.getCurrentCalibration()` and `CalibrationRepository.hasAnyCalibration()` (on `viewModelScope + Dispatchers.IO`), updates `show_calibration_cta` accordingly. Implement `formatCalibrationAge(ageDays: Long, resources: Resources): String` per TSPEC §8.4.
- **TSPEC ref:** §3.6, §7.1, §7.2, §8.4
- **REQ ref:** REQ-DISPLAY-02, REQ-DISPLAY-03, REQ-NFR-01, REQ-NFR-02
- **Acceptance check:** Unit tests (host JVM, no Android framework): (1) `buildUiState()` with all-Good calibration, tilt=2°, no interference → `uiState.confidence == HIGH`; (2) `buildUiState()` with `calibrationAgeDays = null` → `calibration_age_label = "Uncalibrated"`, `show_calibration_cta = true`; (3) `buildUiState()` with `confidence = STABILIZING` → `is_stabilizing = true`; (4) `buildUiState()` pure function: same inputs always produce equal output (call twice, assert equal).
- **Dependencies:** T-1-07 (CompassUiState), T-2-02 (FusionResult via FusionEngine), T-2-06 (ConfidenceModel), T-3-03 (CalibrationRepository)
- **Status:** `[ ]`

---

### T-4-02: CompassRoseView — canvas-based compass rose rendering

- **Status:** `[ ]`
- **Title:** Implement `CompassRoseView` custom View with canvas rotation, cardinal labels, graduations, and north pointer
- **Description:** Create `class CompassRoseView(context: Context, attrs: AttributeSet?) : View(context, attrs)` in `com.luopan.compass.ui`. Expose `var headingDeg: Double = 0.0` with a setter that calls `invalidate()`. Override `onDraw(canvas: Canvas)`: (1) `canvas.save()`; (2) `canvas.rotate(-headingDeg.toFloat(), width / 2f, height / 2f)` — rotates the dial so the rose rotates while the north pointer stays fixed at screen top; (3) `drawRoseGraduations(canvas)` — tick marks at every 10° and every 30°; (4) `drawCardinalLabels(canvas)` — fixed Latin letters N, E, S, W, NE, SE, SW, NW (NOT localized per TSPEC §8.3 and FSPEC-L10N-02); (5) `canvas.restore()`; (6) `drawNorthPointer(canvas)` — drawn AFTER restore so it is fixed at screen top and does NOT rotate. All drawing must use pre-allocated `Paint` objects (initialized in `init {}`) — no object allocation in `onDraw()` hot path. Support portrait and landscape orientations; use `min(width, height)` as the rose diameter.
- **TSPEC ref:** §7.3, §8.3
- **REQ ref:** REQ-DISPLAY-02
- **Acceptance check:** Instrumented test: render `CompassRoseView` at `headingDeg = 0.0`, verify north pointer is at screen top; render at `headingDeg = 90.0`, verify the rose has rotated but the north pointer has not moved; verify `onDraw()` creates no new objects (use Android strict mode allocation tracking); screenshot test (optional): capture bitmap at 0°, 90°, 180°, 270° and compare to expected bitmaps.
- **Dependencies:** None (within Batch 4; needs Batch 0 for project scaffold)
- **Status:** `[ ]`

---

### T-4-03: CompassActivity / CompassFragment layout

- **Status:** `[ ]`
- **Title:** Implement `CompassActivity` and `activity_main.xml` layout with all reserved UI slots
- **Description:** Create `class CompassActivity : AppCompatActivity()` in `com.luopan.compass`. Create `activity_main.xml` containing a `FragmentContainerView` for `CompassFragment` (per TSPEC §7.5 mode-suppression requirement: NO bottom nav, NO tab layout, NO swipe gestures to other modes). Create `CompassFragment` with its layout `fragment_compass.xml` containing: (1) `CompassRoseView` centered; (2) digital heading `TextView` (`@+id/heading_readout`, minimum 16sp); (3) "Magnetic N" north reference label; (4) confidence badge `TextView` (`@+id/confidence_badge`); (5) calibration age `TextView` (`@+id/cal_age_indicator`); (6) tilt indicator `TextView` (`@+id/tilt_indicator`) with `android:minHeight="@dimen/tilt_indicator_reserved_height"` so slot is always present but text is empty when tilt ≤ 5° (TSPEC §7.4 — no layout reflow on appearance/disappearance); (7) reserved slot for advisory banners at bottom. `@dimen/tilt_indicator_reserved_height` must be sized for the longest tilt string at 1.3× font scale. Wire `CompassFragment` to observe `CompassViewModel.uiState` and update each view accordingly.
- **TSPEC ref:** §7.4, §7.5
- **REQ ref:** REQ-DISPLAY-01, REQ-DISPLAY-02, REQ-DISPLAY-11
- **Acceptance check:** Espresso test: `onView(withId(R.id.bottom_nav))` throws `NoMatchingViewException` (TSPEC §7.5 test); `onView(withId(R.id.heading_readout)).check(matches(isDisplayed()))` passes on Pixel 4a API 34 emulator; `onView(withId(R.id.tilt_indicator))` always exists in view hierarchy regardless of tilt state; layout does NOT reflow when tilt indicator text changes.
- **Dependencies:** T-4-01 (CompassViewModel), T-4-02 (CompassRoseView)
- **Status:** `[ ]`

---

### T-4-04: Wake lock management

- **Status:** `[ ]`
- **Title:** Implement unconditional screen-on wake lock acquisition and release in `CompassActivity`
- **Description:** In `CompassActivity.onStart()`, acquire `PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP` with tag `"luopan:CompassScreenOn"` (no timeout — released in `onStop()`). In `CompassActivity.onStop()`, release the wake lock if held: `if (::wakeLock.isInitialized && wakeLock.isHeld) { wakeLock.release() }`. The wake lock is UNCONDITIONAL in Phase 1 — there is no user toggle, no SettingsRepository lookup, and no `keepScreenOn` property involved (TSPEC §7.7). `WAKE_LOCK` permission is already declared (T-0-04). This must be in `onStart()`/`onStop()` (not `onResume()`/`onPause()`) to match sensor registration lifecycle.
- **TSPEC ref:** §7.7
- **REQ ref:** REQ-DISPLAY-10
- **Acceptance check:** Instrumented test: (1) launch `CompassActivity`, verify `wakeLock.isHeld == true`; (2) send app to background (`moveTaskToBack(true)`), verify `wakeLock.isHeld == false`; (3) bring app back to foreground, verify `wakeLock.isHeld == true` again.
- **Dependencies:** T-4-03 (CompassActivity skeleton exists)
- **Status:** `[ ]`

---

### T-4-05: Localization — strings.xml for four locales

- **Status:** `[ ]`
- **Title:** Create `strings.xml` for en (default), zh-Hant, zh-Hans, and ja locales with all Phase 1 UI strings
- **Description:** Create `res/values/strings.xml` (English default), `res/values-zh-rHant/strings.xml` (Traditional Chinese), `res/values-zh-rHans/strings.xml` (Simplified Chinese), `res/values-ja/strings.xml` (Japanese). Include all user-facing strings from FSPEC and TSPEC: confidence badge text (`str_confidence_high`, `str_confidence_moderate`, `str_confidence_poor`, `str_stabilizing`, `str_sensor_error`); calibration wizard strings (instructions, button labels, quality labels Good/Fair/Poor, result text); interference warning text (`str_interference_detected`, `str_interference_explanation`, `str_location_unavailable_advisory`); tilt advisory (`str_tilt_advisory_label`, `str_hold_flat_for_accuracy`); calibration age format (`str_cal_age_format` with `%d` placeholder, `str_cal_uncalibrated`); CTA strings (`str_calibration_cta_title`, `str_calibrate_now`, `str_later`); sensor degradation strings (no-magnetometer, no-gyroscope advisory, stuck sensor message, power-saving advisory). Cardinal labels (N, E, S, W, NE, SE, SW, NW) are hardcoded Latin letters in code (NOT in strings.xml) per FSPEC-L10N-02. Numeric format strings always use `Locale.ROOT` in code.
- **TSPEC ref:** §8.1, §8.2, §8.3, §8.4
- **REQ ref:** REQ-L10N-01, REQ-L10N-03
- **Acceptance check:** (1) (Automated) `./gradlew lint` with `MissingTranslation` as an error passes only when all four locale directory files have matching string keys. (2) (Human gate — QA checklist) Native or near-native speaker spot-check for zh-Hant, zh-Hans, ja: verify these 5 string IDs are correctly translated: `str_interference_detected`, `str_confidence_high`, `str_cal_uncalibrated`, `str_no_gyroscope_advisory`, `str_poor_quality_warning`. This is a QA checklist item, not an automated test. Document the review in `docs/qa/l10n-spot-check-p1.md`. (3) (End-to-end locale test) Launch `CompassActivity` with `Locale("ja")` forced via `ActivityScenario.launch(intent.apply { putExtra("OVERRIDE_LOCALE", "ja") })` (or equivalent `AppCompatDelegate` override in the test). Assert: confidence badge text matches `str_confidence_high`/`str_confidence_moderate`/`str_confidence_poor` from the `ja` strings.xml (not the default English). Assert: at least one advisory banner text (`str_no_gyroscope_advisory`) does not contain any ASCII letter from the set {A-Z, a-z} when locale is `ja`.
- **Dependencies:** T-4-03 (layout files reference string IDs)
- **Status:** `[ ]`

---

## 7. Batch 5 — Calibration Wizard UI

**Gate:** Batch 3 (Android infra — for CalibrationRepository) and Batch 4 (ViewModel and display — for navigation and CompassActivity context) must both be complete.
**Estimated duration:** 3–4 days (can split into two parallel streams: wizard flow + sphere visualization)

---

### T-5-01: CalibrationWizardActivity skeleton and step navigation

- **Status:** `[ ]`
- **Title:** Implement `CalibrationWizardActivity` structure — pre-check, guidance, collection, result, and cancel flow
- **Description:** Create `class CalibrationWizardActivity : AppCompatActivity()` in `com.luopan.compass.calibration.ui`. Wire it to be launched from `CompassActivity` via `ActivityResultLauncher` using `registerForActivityResult(ActivityResultContracts.StartActivityForResult())` (modern Activity Result API — no deprecated `startActivityForResult`). Implement five step states: Step 0 (pre-calibration environment check — show advisory if field deviation > 20%; "Move and retry" / "Continue anyway" options); Step 1 (motion guidance — figure-8 animation + instructions + Start/Cancel buttons); Step 2 (live collection — sample count, axis coverage percentages, quality preview, Done/Cancel); Step 3 (quality result screen — score display with actions based on Good/Fair/Poor); Step 4 (confirmation/save). Implement Cancel flow at Steps 1–3: show confirmation dialog "Cancel calibration? Data will be discarded" → on confirm, discard all session data, return to `CompassActivity` with `RESULT_CANCELED`. Return `RESULT_OK` with calibration quality in result intent when a calibration is saved.
- **TSPEC ref:** §1.2
- **REQ ref:** REQ-CAL-01, REQ-CAL-02, REQ-CAL-03, REQ-CAL-04
- **Acceptance check:** Espresso test: (1) navigate through Step 0 → Step 1 → tapping "Start" → Step 2 with sample counter visible; (2) tapping Cancel at Step 2 shows confirmation dialog; (3) confirming cancel returns to CompassActivity without saving; (4) `ActivityResultLauncher` receives `RESULT_CANCELED` when cancelled; (5) After confirming cancel: `CalibrationRepository.getCurrentCalibration()` returns the identical record that was read before the wizard was launched (same id, same residual_rms, same saved_at timestamp). The rollback slot is also unchanged.
- **Dependencies:** T-3-03 (CalibrationRepository), T-4-01 (CompassViewModel for CTA launch)
- **Status:** `[ ]`

---

### T-5-02: Real-time sample collection and completion gate logic

- **Status:** `[ ]`
- **Title:** Implement sample ring buffer, live coverage feedback, rolling RMS preview, Done-button gate, and auto-complete logic
- **Description:** In `CalibrationWizardActivity` Step 2, implement `CircularBuffer<MagSample>(capacity = 500)` for sample collection. On each magnetometer event during collection: add sample to buffer, compute live `CoverageStats` via `CalibrationEngine.computeCoverage()`, compute rolling residual RMS over the most recent 50 samples (as a simplified sphere-distance estimate, not full LM fit). Update UI: sample count label, per-axis coverage bars (red < 15%, amber 15–20%, green ≥ 20%), RMS preview in µT. Done button activation gate: enabled only when `sampleCount >= 200 AND coverage_x >= 0.15 AND coverage_y >= 0.15 AND coverage_z >= 0.15` (both conditions required simultaneously). Auto-complete: when `sampleCount >= 200 AND all_coverage >= 0.80 AND rollingRms < 1.0 µT` simultaneously, trigger completion automatically (show sphere-green pulse animation, proceed to fit computation). Instructional hints update dynamically based on lowest-coverage axis.
- **TSPEC ref:** §4.1, §4.2, §1.4
- **REQ ref:** REQ-CAL-01, SE-07
- **Acceptance check:** Unit test: (1) inject 199 samples → Done button disabled; inject 200th sample with adequate coverage → Done button enabled; (2) auto-complete fires when all three conditions met simultaneously but not when only two are met; (3) hint text changes to Z-axis guidance when Z coverage is the lowest.
- **Dependencies:** T-2-03 (CalibrationEngine.computeCoverage), T-5-01 (wizard skeleton)
- **Status:** `[ ]`

---

### T-5-03: Sphere coverage visualization

- **Status:** `[ ]`
- **Title:** Implement 3D sphere visualization showing sampled coverage regions
- **Description:** Create `SphereVisualizationView` (custom `View` subclass) in `com.luopan.compass.calibration.ui`. Render a wireframe sphere using `Canvas` drawing (no 3D library). Map each `MagSample` to a point on the sphere surface by normalizing the (x, y, z) vector. Project to 2D using a simple orthographic or pseudo-3D projection. Each point is drawn as a small dot — grey/blue initially, transitioning to green as the region density increases (threshold: ≥ 3 samples within a 15° angular neighborhood). Update in real time on each sample addition (via `invalidate()`). Visual density feedback: high-density regions are bright green; low-density or uncovered regions are grey. No axis labels needed on the sphere itself; coverage percentage labels are provided separately in T-5-02.
- **TSPEC ref:** §1.4
- **REQ ref:** REQ-CAL-01
- **Acceptance check:** Instrumented test: render `SphereVisualizationView` with 0 samples — sphere is entirely grey; inject 200 samples covering one hemisphere — that half is green, the other half remains grey; verify `onDraw()` creates no new `Paint` objects (pre-allocated in `init {}`).
- **Dependencies:** T-5-01 (wizard layout must have a container for this view)
- **Status:** `[ ]`

---

### T-5-04: Quality result screen — Good/Fair/Poor display and action paths

- **Status:** `[ ]`
- **Title:** Implement calibration quality result screen with all three quality paths and Poor two-tap confirmation
- **Description:** After the user taps Done (or auto-complete fires), call `CalibrationEngine.fit()` on `Dispatchers.Default`, show a spinner "Computing calibration quality…" during computation (≤ 2 s on reference hardware). On completion, show the quality result screen with: large colored quality label (GOOD=green, FAIR=amber, POOR=red); residual RMS value ("Fit accuracy: X.X µT"); per-axis coverage percentages. Action buttons by quality level: **GOOD** → "Save Calibration" (primary green) + "Retry for better results" (text link); **FAIR** → "Save — Moderate Accuracy" (primary amber) + caption "Confidence will be capped at Moderate. Retry for High accuracy." + "Retry" (text link); **POOR** → "Retry" (primary amber) + "Accept Anyway" (grey). For Poor "Accept Anyway" tap: show confirmation dialog "This calibration is Poor quality. Your confidence level will be Poor and readings may not be reliable. Save anyway?" with "Yes, save" and "Go back" buttons (two-tap confirmation per FSPEC-CAL-01 §14). On confirmed save, call `CalibrationRepository.saveCalibration()` on `Dispatchers.IO`; show confirmation "Calibration saved." screen; Done button returns to `CompassActivity` with `RESULT_OK`.
- **TSPEC ref:** §4.4
- **REQ ref:** REQ-CAL-02, REQ-CAL-03, REQ-CAL-04
- **Acceptance check:** Espresso test: (1) Poor quality result → tap "Accept Anyway" → confirmation dialog appears; (2) tap "Go back" → dialog dismissed, quality screen still shown; (3) tap "Accept Anyway" → "Yes, save" → `RESULT_OK` returned to CompassActivity; (4) after successful save, `CalibrationRepository.getCurrentCalibration()` returns a non-null record; (5) After RESULT_OK is received by `CompassActivity`: `onView(withId(R.id.confidence_badge)).check(matches(withText(containsString(str_confidence_poor))))` passes within one UI update cycle (using `IdlingResource` or `waitForIdleSync()`).
- **Dependencies:** T-2-03 (CalibrationEngine.fit), T-3-03 (CalibrationRepository.saveCalibration), T-5-01 (wizard skeleton)
- **Status:** `[ ]`

---

### T-5-05: First-launch calibration CTA MaterialBanner

- **Status:** `[ ]`
- **Title:** Implement non-blocking `MaterialBanner` CTA shown when no calibration exists
- **Description:** In `CompassFragment`, observe `CompassUiState.show_calibration_cta`. When `true`, show a `MaterialBanner` anchored at the bottom of the screen (positioned above any other banners) with: primary text `str_calibration_cta_title`; action button `str_calibrate_now` — tapping launches `CalibrationWizardActivity` via the same `ActivityResultLauncher` as other entry points; dismiss action `str_later` — dismisses banner for the current session without changing `show_calibration_cta` in the ViewModel (it will reappear on next cold start if still uncalibrated). When `show_calibration_cta` becomes `false` (after a calibration is saved), remove the banner permanently (do not re-add it). The banner must NOT be a dialog — it must be non-blocking and the compass rose must remain visible and interactive while the banner is shown.
- **TSPEC ref:** §7.6
- **REQ ref:** REQ §9 Scenario A
- **Acceptance check:** Espresso test: (1) fresh state (`show_calibration_cta = true`) — MaterialBanner is visible; compass rose and heading are also visible simultaneously; (2) tap "Later" — banner disappears; (3) simulate calibration saved → `show_calibration_cta = false` → banner does NOT reappear; (4) verify banner is not a `Dialog` (no `BlockingDialogMatcher` behavior).
- **Dependencies:** T-4-01 (CompassViewModel.show_calibration_cta field), T-5-01 (CalibrationWizardActivity launching)
- **Status:** `[ ]`

---

### T-5-06: OOM resilience — atomic write verification and session data discard

- **Status:** `[ ]`
- **Title:** Verify OOM resilience: atomic calibration write survives process kill; session data is never partially persisted
- **Description:** This task has no new production code to write — it implements the verification tests for the OOM resilience behavior specified in FSPEC-CAL-02. Write an instrumented test that: (1) starts a calibration session; (2) uses `ProcessPhoenix` (or `Process.killProcess(Process.myPid())`) to kill the app mid-collection; (3) relaunches the app; (4) asserts the prior calibration (if any) is intact, or uncalibrated state is shown — never a partial/corrupt record. Write a second test that kills the process during the atomic save transaction: verifies the pre-transaction calibration state is preserved (Room transaction leaves DB in pre-transaction state on kill). Document in the test class that these tests require an emulator or physical device and cannot run on the host JVM. OOM kill simulation uses `ProcessPhoenix.triggerRebirth(context)` (declared in T-0-02). The test runner must be configured with `android:process=":test"` in the test manifest to survive the kill — the `:test` process is separate from the app process so the instrumentation runner continues after the app is killed.
- **TSPEC ref:** §4.6, §9.2
- **REQ ref:** REQ-CAL-04, TE-07, REQ §9 Scenario I
- **Acceptance check:** Both instrumented tests pass on a physical device or emulator. `CalibrationRepository.getCurrentCalibration()` returns the pre-session record (or null if none existed) after each kill scenario.
- **Dependencies:** T-3-03 (CalibrationRepository), T-5-04 (save flow exists to be killed mid-transaction)
- **Status:** `[ ]`

---

## 8. Batch 6 — Integration Wiring and Edge Cases

**Gate:** Batch 4 (ViewModel + display) and Batch 5 (calibration wizard) must both be complete.
**Estimated duration:** 3–4 days (most tasks are independent and can be parallelized)

---

### T-6-01: Pipeline wiring — SensorLayer → FusionEngine → InterferenceDetector → ConfidenceModel → CompassViewModel

- **Status:** `[ ]`
- **Title:** Wire the full sensor pipeline in `CompassActivity` / `CompassFragment`
- **Description:** In `CompassActivity.onStart()` (after sensor registration): instantiate `SensorLayer`, `FusionEngine`, `CalibrationEngine`, `InterferenceDetector(SystemLocationProvider(this), SystemTimeSource)`, `SensorStateMonitor`, `SensorRateMonitor(timeSource = SystemTimeSource)`, `NoiseVarianceTracker`, `NoiseSpikeFilter`. Wire them per TSPEC §1.3 data flow: on each `SensorLayer.onFrame(SensorFrame)` callback (running on `sensorThread`): (1) pass mag through `NoiseSpikeFilter.filter()`; if null (rejected spike), skip fusion for this frame; (2) apply calibration correction via `CalibrationEngine.applyCorrectedMag()`; (3) call `FusionEngine.update(frame)` → `FusionResult`; (4) call `InterferenceDetector.computeMetrics()` and `updateState()` → `InterferenceState`; (5) update `SensorStateMonitor` and `SensorRateMonitor`; (6) update `NoiseVarianceTracker`; (7) call `ConfidenceModel.compute()` → `OverallConfidence`; (8) call `viewModel.onFusionResult(result)` and `viewModel.updateState(...)`. Call `InterferenceDetector.updateExpectedField()` once in `onStart()` after sensor registration. Ensure all these calls happen on `sensorThread`, not the main thread. The interference warning banner in `CompassFragment` is non-dismissible in Phase 1 (no tap-to-dismiss). It disappears only when `InterferenceState.CLEAR` is emitted after the 3-second hysteresis window. There is no override button (Phase 1 has no bearing capture).
- **TSPEC ref:** §1.3, §1.4
- **REQ ref:** REQ-SENSOR-04, REQ-DETECT-01, REQ-DETECT-02, REQ §5.3
- **Acceptance check:** (1) Inject a single `SensorFrame` with mag vector pointing North (mx=30, my=0, mz=-30), gravity down (az=-9.81), no gyro perturbation. Call `viewModel.forceUpdateForTest(frame)` (or invoke `buildUiState()` directly as per TSPEC §7.1 pure function). Assert `uiState.value.heading_deg` is within ±2.0° of 0.0°; `uiState.value.confidence != SENSOR_ERROR`; `uiState.value.interference_state == CLEAR`. (2) Interference warning overlay: when `InterferenceState.WARNING` is emitted, the red warning banner appears in `CompassFragment` within 2 seconds; explanation text (`str_interference_explanation`) is visible; heading readout continues to update (not frozen/zeroed); the banner is non-dismissible (tapping the banner does not hide it). Verify with Espresso: `onView(withId(R.id.interference_banner)).check(matches(isDisplayed()))` after injecting a sensor frame with 30% field deviation. (3) Interference clearance hysteresis: inject interference (30% deviation) → banner appears; drop to 12% deviation for 2.9 seconds → banner still visible; complete 3.0 seconds at 12% → banner disappears. Verify banner state at each step with Espresso. Also verify: mid-window spike to 18% resets the 3-second timer (banner still visible after 3.0 s from original drop time).
- **Dependencies:** All Batch 2 and Batch 3 tasks; T-4-01 (CompassViewModel)
- **Status:** `[ ]`

---

### T-6-02: Mode switcher suppression

- **Status:** `[ ]`
- **Title:** Enforce single-destination nav graph and verify no mode-switcher UI elements exist
- **Description:** Create `res/navigation/nav_graph.xml` with exactly ONE destination: `CompassFragment` (no Luopan mode destination, no Sighting mode destination, no placeholder fragment). In `activity_main.xml`, confirm only a `FragmentContainerView` is present — no `BottomNavigationView`, `TabLayout`, or any view with content description or tag referencing "Luopan" or "Sighting". Add a lint check or CI-visible comment: future phases MUST add destinations to this nav graph and MUST NOT add hidden/disabled mode tabs in Phase 1. This task also enforces the swipe gesture constraint: verify no `ViewPager2` or swipe gesture listener is wired to the compass fragment.
- **TSPEC ref:** §7.5
- **REQ ref:** REQ-DISPLAY-01
- **Acceptance check:** Espresso test: `onView(withId(R.id.bottom_nav))` throws `NoMatchingViewException`; `onView(withContentDescription("Luopan"))` throws `NoMatchingViewException`; `onView(withContentDescription("Sighting"))` throws `NoMatchingViewException`. Nav graph XML contains exactly one `<fragment>` element.
- **Dependencies:** T-4-03 (CompassActivity layout)
- **Status:** `[ ]`

---

### T-6-03: Rapid rotation — STABILIZING state end-to-end

- **Status:** `[ ]`
- **Title:** Wire rapid rotation detection through to confidence badge showing "Stabilizing…"
- **Description:** Ensure `SensorStateMonitor.STABILIZING` state (from T-2-07) propagates correctly through the pipeline to the UI. When `SensorState.STABILIZING` is active: `ConfidenceModel.compute()` returns `OverallConfidence.STABILIZING` (short-circuit — no five-dimension scoring); `CompassUiState.is_stabilizing = true`; the confidence badge `TextView` shows the localized "Stabilizing…" string in amber color instead of the normal High/Moderate/Poor text. The badge style (color, font weight) must match the Moderate badge visually (amber). The confidence badge view must NOT be replaced by a different view — the same `TextView` changes its text and color. When `SensorState.NORMAL` is restored, the badge immediately reverts to the normal confidence computation. Only applies when `hasGyroscope = true`; when `hasGyroscope = false`, STABILIZING can never trigger (no gyroscope → no angular rate monitoring).
- **TSPEC ref:** §2.6, §6.3, §6.4
- **REQ ref:** REQ §11.7, FSPEC-SENSOR-05
- **Acceptance check:** Integration test: inject gyro values > 180°/s for 600 ms → `uiState.confidence == STABILIZING`; inject gyro values < 10°/s for 1.2 s → `uiState.confidence` reverts to the model-computed value; Espresso: badge text changes to the localized "Stabilizing…" string during rapid rotation.
- **Dependencies:** T-2-07 (SensorStateMonitor), T-6-01 (pipeline wiring), T-4-03 (CompassFragment for badge display)
- **Status:** `[ ]`

---

### T-6-04: Power-saving advisory end-to-end

- **Status:** `[ ]`
- **Title:** Wire `SensorRateMonitor` below-15-Hz advisory to the UI banner
- **Description:** When `SensorRateMonitor.onEvent()` reports effective Hz < 15.0 for ≥ 2 seconds (sustained, not a transient dip), set `CompassUiState.power_saving_advisory = true`. In `CompassFragment`, observe this field and show a non-blocking `MaterialBanner` or non-dismissible advisory text below the compass rose with text `str_power_saving_advisory` ("Reduced accuracy — device is in power-saving mode"). When effective Hz returns to ≥ 15.0 for ≥ 2 seconds, set `power_saving_advisory = false` and clear the banner automatically. If the no-gyroscope advisory is also active (T-6-05), both banners stack vertically (FSPEC-SENSOR-04 §5). Advisory is non-dismissible by the user.
- **TSPEC ref:** §2.5
- **REQ ref:** REQ §11.9, FSPEC-SENSOR-04
- **Acceptance check:** Unit test: inject < 15 Hz events via fake `TimeSource` for 2.1 s → `uiState.power_saving_advisory = true`; inject ≥ 15 Hz events for 2.1 s → `power_saving_advisory = false`. Espresso: advisory banner appears and disappears correctly.
- **Dependencies:** T-2-07 (SensorRateMonitor), T-6-01 (pipeline), T-4-01 (CompassViewModel state field)
- **Status:** `[ ]`

---

### T-6-05: No-magnetometer error screen

- **Status:** `[ ]`
- **Title:** Implement the no-magnetometer error screen shown when both sensor types return null
- **Description:** In `SensorLayer.start()`, if `magSensor == null` after both probes (TSPEC §2.2), invoke an `onNoMagnetometer` callback. In `CompassActivity`, wire this callback to navigate to (or replace the current view with) a full-screen error layout containing: localized `str_compass_unavailable` heading ("Compass unavailable"), localized `str_no_magnetometer_explanation` body ("This device does not have a magnetometer. Compass functionality is unavailable."). No compass rose, no heading value, no confidence badge — NONE of these are shown. The error state is permanent for the session (no retry button). The activity does NOT crash; it does not call `finish()` — it remains in the error state. The error is NOT logged as a crash event.
- **TSPEC ref:** §2.2
- **REQ ref:** REQ-SENSOR-01, REQ §11.1, FSPEC-SENSOR-01
- **Acceptance check:** Instrumented test using a mock `SensorManager` that returns null for both `TYPE_MAGNETIC_FIELD_UNCALIBRATED` and `TYPE_MAGNETIC_FIELD`: error screen is displayed; `onView(withId(R.id.compass_rose))` throws `NoMatchingViewException` or is not displayed; `onView(withText("Compass unavailable"))` (using locale-matched string) is displayed; no crash observed.
- **Dependencies:** T-3-01 (SensorLayer with onNoMagnetometer callback), T-4-03 (CompassActivity layout)
- **Status:** `[ ]`

---

### T-6-06: No-gyroscope advisory and Moderate confidence cap end-to-end

- **Status:** `[ ]`
- **Title:** Wire no-gyroscope detection to persistent advisory and session-level Moderate confidence cap
- **Description:** When `SensorLayer.hasGyroscope == false` after startup probe: set `CompassUiState.no_gyroscope_advisory = true` (permanent for session). In `CompassFragment`, observe this field and show a non-dismissible persistent advisory text below the confidence badge: localized `str_no_gyroscope_advisory` ("No gyroscope — heading may be less stable"). This advisory cannot be dismissed and remains for the entire session. Also ensure the no-gyroscope Moderate cap in `ConfidenceModel.compute()` (T-2-06) is correctly wired: `hasGyroscope = false` passed to every `compute()` call, resulting in the session-level Moderate ceiling. The cap must reduce HIGH → MODERATE but must NOT raise POOR → MODERATE.
- **TSPEC ref:** §2.2
- **REQ ref:** REQ-SENSOR-03, REQ §11.2, FSPEC-SENSOR-02
- **Acceptance check:** Instrumented test with no-gyroscope device (or mock): advisory text is visible and non-dismissible; `uiState.confidence` never shows HIGH; `uiState.no_gyroscope_advisory == true`. Unit test: `ConfidenceModel.compute()` with all-GOOD dimensions and `hasGyroscope = false` → returns MODERATE; with a POOR dimension and `hasGyroscope = false` → returns POOR (cap does not raise).
- **Dependencies:** T-2-06 (ConfidenceModel no-gyroscope cap), T-3-01 (SensorLayer hasGyroscope), T-6-01 (pipeline), T-4-01 (CompassViewModel state field)
- **Status:** `[ ]`

---

### T-6-07: Stuck sensor detection badge and heading freeze end-to-end

- **Status:** `[ ]`
- **Title:** Wire `SensorState.STUCK` to "Sensor error" badge and heading freeze in the display
- **Description:** When `SensorStateMonitor` emits `SensorState.STUCK`: `ConfidenceModel.compute()` returns `OverallConfidence.SENSOR_ERROR`; `CompassUiState.sensor_state = STUCK`; `CompassUiState.last_valid_heading_deg` is set to the last heading value computed BEFORE the stuck condition was detected (this field must be set by `CompassViewModel` on every normal fusion result so it is always current when needed). The compass rose and heading readout display the frozen `last_valid_heading_deg` value — the rose does NOT continue rotating. The confidence badge shows localized `str_sensor_error` ("Sensor error") in red, NOT "Poor." An explanation text appears: `str_sensor_not_responding` ("Sensor not responding — try restarting the app or device"). Recovery: when `SensorStateMonitor` returns to `NORMAL`, the badge reverts to normal confidence computation and the heading unfreezes.
- **TSPEC ref:** §2.7, §6.3
- **REQ ref:** REQ §11.3, FSPEC-SENSOR-03
- **Acceptance check:** Integration test: inject 60 identical magnetometer frames (all-zero variance) → after 3s, `uiState.sensor_state == STUCK`; `uiState.confidence == SENSOR_ERROR`; heading display is frozen at last valid value; inject varying magnetometer values for 1s → heading unfreezes; badge reverts to normal. Espresso: badge text shows "Sensor error" (localized), NOT "Poor".
- **Dependencies:** T-2-07 (SensorStateMonitor.STUCK), T-6-01 (pipeline), T-4-01 (CompassViewModel last_valid_heading_deg), T-4-03 (CompassFragment badge display)
- **Status:** `[ ]`

---

### T-6-08: Espresso integration test suite — REQ §9 scenarios A–I

- **Status:** `[ ]`
- **Title:** Espresso integration test suite — REQ §9 scenarios A–I
- **Description:** Author a single `CompassIntegrationTest` Espresso class with one @Test method per scenario letter. Method names: `scenarioA_firstLaunchShowsCTABanner()`, `scenarioB_calibrationFlowPersistsAndUpdatesConfidence()`, `scenarioC_accuracyWithinjectedSequence()` (uses T-0-06 file), `scenarioD_interferenceWarningClearsAfter3s()`, `scenarioE_noMagnetometerShowsErrorScreen()`, `scenarioF_japaneseLocaleNoEnglishStrings()`, `scenarioG_cancelPreservesRollback()`, `scenarioH_poorAcceptanceConfidenceBadgeIsPoor()`, `scenarioI_oomKillRetainsCalibration()`. Scenarios C (accuracy) and F (locale) may use emulator substitutes. All nine @Test methods must pass on the Pixel 4a API 34 emulator. This class provides holistic Phase 1 "usable application state" validation and closes the REQ §9 traceability gap.
- **TSPEC ref:** All sections
- **REQ ref:** REQ §9 Scenarios A–I
- **Acceptance check:** All nine @Test methods pass on Pixel 4a API 34 emulator in one test run. No English strings appear in Scenario F when locale is set to "ja" via `ActivityScenario`.
- **Dependencies:** All Batch 5 and Batch 6 tasks (T-5-01 through T-6-07)
- **Status:** `[ ]`

---

### T-6-09: Battery Historian verification gate — REQ-NFR-03

- **Status:** `[ ]`
- **Title:** Battery Historian verification gate — REQ-NFR-03
- **Description:** Run a 1-hour Battery Historian measurement session on a 4000 mAh reference device at 100% screen brightness with compass active. Use `adb shell dumpsys batterystats --reset` before the session and `adb bugreport` after. Analyze with Battery Historian; commit the HTML report to `docs/qa/battery-historian-p1.html`. This task promotes the pre-release QA gate from the Known Blockers risk note to a formal tracked task with an explicit definition of done.
- **TSPEC ref:** §10.3 (battery estimates)
- **REQ ref:** REQ-NFR-03
- **Acceptance check:** App wakelocks < 5% of 1-hour total energy draw per Battery Historian report. Report artifact committed to `docs/qa/battery-historian-p1.html`. If wakelock usage is ≥ 5%: raise a defect before Phase 1 release and evaluate switching `SENSOR_DELAY_GAME` to a slower sensor rate for the magnetometer.
- **Dependencies:** T-6-01 (full pipeline must be wired)
- **Status:** `[ ]`

---

## 9. Risk and Timeline Notes

### Critical Path

The sequential chain of longest-duration batches defines the critical path:

```
T-0-01 (project scaffold)
  → T-1-03 + T-1-07 + T-1-08 (CalibrationRecord, CompassUiState, cal data classes)
    → T-2-03 (CalibrationEngine / ellipsoid fitting) [LONGEST SINGLE TASK: 3-5 days]
      → T-4-01 (CompassViewModel) + T-3-03 (CalibrationRepository)
        → T-5-01 through T-5-04 (calibration wizard)
          → T-6-01 (pipeline wiring) + T-6-07 (stuck sensor)
```

**Estimated total wall-clock time on the critical path with parallel execution:**
- Batch 0: ~2 days
- Batch 1: ~2 days (parallel)
- Batch 2+3: ~4 days (parallel, limited by T-2-03 ellipsoid fitting)
- Batch 4: ~4 days (parallel)
- Batch 5: ~4 days (parallel)
- Batch 6: ~3 days (mostly parallel)
- **Total: ~19 days wall-clock with 3–4 developers**

---

### Complexity Spikes

**T-2-03 — Ellipsoid fitting (HIGH complexity):**
The Levenberg-Marquardt optimization via `commons-math3` requires careful setup: defining the cost function (per-sample residual `|A*(sample - b)| - targetRadius`), setting convergence tolerances, and handling degenerate inputs (e.g., fewer than 12 unique samples, collinear samples). Budget 3–4 days for this task including tuning. Risk mitigation: prepare a synthetic sphere test dataset before implementation; use it as the ground truth for acceptance. If `LevenbergMarquardtOptimizer` convergence is slow on budget API 26 hardware during integration testing, evaluate switching to `GaussNewtonOptimizer` (faster per iteration, less robust) as a fallback.

**T-3-02 + T-3-03 — SQLCipher setup (MODERATE-HIGH complexity):**
Android Keystore + `EncryptedSharedPreferences` + SQLCipher integration has multiple failure modes: Keystore unavailability on some Android 8.0 (API 26) devices with non-standard security configurations; `EncryptedSharedPreferences` in alpha version (1.1.0-alpha06) may have edge cases; Room + `SupportFactory` integration requires exact version pinning. Budget 2 days. Have a test emulator (API 26) ready for early instrumented testing. The `@VisibleForTesting` in-memory database constructor (T-3-03) is the escape hatch for unblocking other batches while SQLCipher issues are resolved.

**T-2-01 + T-2-02 — Madgwick filter tuning (MODERATE complexity):**
The Madgwick filter at `beta = 0.05f` is correct per spec, but the CI proxy test (TE-F-13 — ±0.5° accuracy on a 30-second pre-recorded sequence) may reveal that the filter needs a warm-up period (first 2–3 seconds of data) before heading stabilizes. The test sequence should account for this by measuring accuracy only after the first 5 seconds. If CI accuracy fails, the fallback is to increase `beta` to 0.08–0.10 (faster convergence at the cost of slightly higher noise). Any beta change requires a TSPEC amendment.

**T-5-03 — Sphere visualization (MODERATE complexity):**
Rendering 500 points on a pseudo-3D sphere with real-time density coloring on the `Canvas` without allocations in `onDraw()` requires careful pre-allocation. Budget 2 days. A simpler fallback: if the 3D sphere visualization is not performant on API 26 hardware, replace with three 2D circular coverage gauges (one per axis) — this satisfies the functional requirement without 3D rendering overhead.

---

### Known Blockers and Open Decisions

**PM-FIND-04 — Cardinal label localization decision (gates T-4-02 and T-4-05):**
TSPEC §8.3 documents a pending product decision: cardinal labels (N, E, S, W, NE, SE, SW, NW) are currently specified as hardcoded Latin letters. FSPEC-L10N-02 §9 confirms this is a deliberate Phase 1 simplification. No action required — implement as Latin-only per FSPEC-L10N-02. If the product owner reverses this decision before T-4-02 starts, add string resource keys for cardinal labels in T-4-05 and consume them in `CompassRoseView.drawCardinalLabels()`.

**PM-FIND-10 — Battery verification gate (addressed by T-6-09):**
REQ-NFR-03 requires Battery Historian verification at 100% screen brightness before Phase 1 release. This is now formally tracked as T-6-09 in Batch 6 with an explicit acceptance check and status checkbox. If measured usage exceeds 5%/hour at 100% brightness, revisit `SENSOR_DELAY_GAME` vs. a slower rate for the magnetometer (separate from the power-saving detection).

**TE-F-13 — CI proxy test sensor sequence (addressed by T-0-06):**
The CI proxy test requires a pre-recorded 30-second sensor sequence at a known heading. This is now formally tracked as T-0-06 in Batch 0, with acceptance criteria, a status checkbox, and an explicit dependency declared in T-2-02. Format: a list of `SensorFrame` values serialized to `app/src/test/resources/sensor_sequences/known_heading_30s.json`. Ground truth heading must be verified against an independent reference hardware compass (±0.5° known accuracy). T-0-06 must complete before T-2-02 acceptance testing can run.

---

*End of PLAN-luopan-p1-core-compass v0.1-draft*
