# TSPEC-luopan-p2-true-north-capture
## Technical Specification — Phase 2: True North, Magnetic Declination, and Bearing Capture

| Field | Value |
|-------|-------|
| **Document ID** | TSPEC-luopan-p2-true-north-capture |
| **Version** | 0.2.1 |
| **Status** | Approved |
| **Date** | 2026-04-24 |
| **Revised** | 2026-04-24 (v0.2.1 — patched TE "Approved with Conditions" findings: TE-T-05-NEW removed misplaced concurrent double-tap test from `BearingCaptureUseCaseTest` (ViewModel member, not use-case member); TE-T-03-NEW added AT-B traceability callout to `WmmDeclinationAccuracyTest`; TE-T-02-NEW fixed residual `"luopan.db"` reference in §4.5 to `"bearing_records.db"`) |
| **Phase** | 2 of 5 |
| **Author** | Engineering |
| **Parent REQ** | [REQ-luopan-p2-true-north-capture.md](REQ-luopan-p2-true-north-capture.md) |
| **Parent FSPEC** | [FSPEC-luopan-p2-true-north-capture.md](FSPEC-luopan-p2-true-north-capture.md) |
| **Predecessor TSPEC** | [TSPEC-luopan-p1-core-compass.md](TSPEC-luopan-p1-core-compass.md) |
| **Master REQ** | [REQ-luopan-compass.md](REQ-luopan-compass.md) |

> **Purpose of this document:** This TSPEC translates Phase 2 behavioral requirements into concrete technical design decisions. It is the authoritative implementation reference for developers: architecture, class names, data models, algorithm parameters, and all engineering choices are specified here. It does NOT invent product behavior — it specifies HOW to implement what the REQ and FSPEC define.

---

## §0 Requirements Traceability

| TSPEC Section | REQ-ID(s) | FSPEC-ID(s) |
|---|---|---|
| §1 — Architecture Overview | all | all |
| §2 — New Interfaces | REQ-NORTH-01, REQ-NORTH-02 | FSPEC §6.2, §6.3 |
| §3.1 — Wmm2025Model | REQ-NORTH-01 | FSPEC-TNORTH, §6.3 |
| §3.2 — AndroidGeoFieldModel | REQ-NORTH-02 | FSPEC-TNORTH §2.1 step 8b |
| §3.3 — MagneticFieldModelProvider | REQ-NORTH-01, REQ-NORTH-02 | FSPEC-TNORTH §2.1 steps 7–8c |
| §3.4 — LocationRepository | REQ-NORTH-03 | FSPEC-GPS §2.4 |
| §3.5 — BearingRepository | REQ-CAPTURE-01, REQ-CAPTURE-04 | FSPEC-CAPTURE §2.5 |
| §3.6 — BearingCaptureUseCase | REQ-CAPTURE-01, REQ-CAPTURE-02 | FSPEC-CAPTURE §2.5, BR-10 |
| §3.7 — CompassViewModel (extended) | REQ-DECL-01, REQ-NORTH-04 | FSPEC-TOGGLE §2.2, FSPEC-TNORTH §2.1 |
| §3.8 — InterferenceDetector (updated) | REQ-DETECT-01, REQ-DETECT-02 | FSPEC-DETECTUPGRADE §2.6 |
| §4 — Data Layer | REQ-CAPTURE-01, REQ-CAPTURE-04, REQ §10.2 | FSPEC §6.1 |
| §5 — WMM2025 Bundling | REQ-NORTH-01 | FSPEC-TNORTH §2.1 |
| §6 — Location Strategy | REQ-NORTH-03, REQ-CAPTURE-06 | FSPEC-GPS §2.4 |
| §7.2 — Declination Info Panel | REQ-DECL-02 | FSPEC-DECLPANEL §2.3 |
| §7 — UI Changes | REQ-DECL-01, REQ-DECL-02, REQ-CAPTURE-02 | FSPEC-TOGGLE, FSPEC-DECLPANEL, FSPEC-CAPTURE |
| §8 — Threading Model | REQ-NFR-08 | FSPEC §7 AT-NFR-01 |
| §9 — Deferred Decisions | — | FSPEC §6.2, §6.3, FSPEC-GPS |
| §10 — Test Strategy | REQ §10.2 | FSPEC §7 |

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [New Interfaces](#2-new-interfaces)
3. [New Classes and Their Responsibilities](#3-new-classes-and-their-responsibilities)
4. [Data Layer](#4-data-layer)
5. [WMM2025 Bundling Strategy](#5-wmm2025-bundling-strategy)
6. [Location Strategy](#6-location-strategy)
7. [UI Changes](#7-ui-changes)
8. [Threading Model](#8-threading-model)
9. [Deferred Decisions](#9-deferred-decisions)
10. [Test Strategy](#10-test-strategy)

---

## 1. Architecture Overview

### 1.1 How Phase 2 Extends the Phase 1 Architecture

Phase 1 established a single-pipeline architecture: `SensorLayer` → `FusionEngine` → `InterferenceDetector` → `ConfidenceModel` → `CompassViewModel` → `CompassUiState`. All components are wired inside `CompassViewModel`, which acts as the orchestrator.

Phase 2 adds three orthogonal concerns that plug into this pipeline:

1. **Magnetic field model layer** (`com.luopan.compass.magnetic`): A new package providing `MagneticFieldModel` and its two implementations (`Wmm2025Model`, `AndroidGeoFieldModel`), selected at runtime by `MagneticFieldModelProvider`. The model is injected into `InterferenceDetector` to replace the Phase 1 rolling EMA baseline, and consumed by `CompassViewModel` to compute declination for True North correction.

2. **Location layer** (`com.luopan.compass.location`): `LocationRepository` manages GPS acquisition, caching, and fallback. Its output (a resolved `CachedLocation?`) feeds `CompassViewModel` to supply lat/lon/alt to the magnetic field model. The Phase 1 `LocationProvider` / `SystemLocationProvider` pair (used only for diagnostic display) is superseded by `LocationRepository` for all P2 location needs.

3. **Bearing capture layer** (`com.luopan.compass.bearing`): `BearingRepository`, `BearingCaptureUseCase`, and `BearingRecord` (Room entity) provide the capture-and-persist path. `BearingCaptureUseCase` is invoked from a new `BearingCaptureViewModel` (or via `CompassViewModel` — see §3.7).

`CompassViewModel` gains new injected dependencies (`MagneticFieldModelProvider`, `LocationRepository`) and new state fields (north type, declination, extreme latitude advisory). The `InterferenceDetector` signature changes to accept a `MagneticFieldModel?` parameter on `updateState`.

### 1.2 New Packages

| Package | Contents |
|---------|----------|
| `com.luopan.compass.magnetic` | `MagneticFieldModel` (interface), `Wmm2025Model`, `AndroidGeoFieldModel`, `MagneticFieldModelProvider`, `WmmResult` (data class) |
| `com.luopan.compass.location` | `LocationRepository`, `CachedLocation` (data class), `LocationState` (sealed class) |
| `com.luopan.compass.bearing` | `BearingRecord` (Room entity), `BearingDao`, `BearingRepository`, `BearingCaptureUseCase`, `BearingSnapshot` (data class) |
| `com.luopan.compass.util` | `Clock` (interface), `WallClock`, `FakeClock` (test only) |

### 1.3 Dependency Graph

```
CompassViewModel
  ├── MagneticFieldModelProvider
  │     ├── Wmm2025Model  (primary)
  │     └── AndroidGeoFieldModel  (fallback)
  ├── LocationRepository
  │     └── Clock  (for cache expiry)
  ├── InterferenceDetector  (updated — receives WmmResult)
  └── [all Phase 1 components unchanged]

BearingCaptureUseCase
  ├── BearingRepository → LuopanDatabase (version 2)
  ├── LocationRepository  (constructor-injected; Phase 2 uses snapshotted location values
  │                        but the dependency is explicit for future phases)
  └── MagneticFieldModelProvider  (reads getModelId())

LuopanDatabase (v2)
  ├── CalibrationDao  (unchanged)
  └── BearingDao  (new, Migration 1→2)
```

---

## 2. New Interfaces

### 2.1 `MagneticFieldModel`

**Package:** `com.luopan.compass.magnetic`

This interface abstracts the geomagnetic field model. It has exactly two implementations in Phase 2 (`Wmm2025Model` and `AndroidGeoFieldModel`) and one test double (`FakeMagneticFieldModel`). All three methods that accept coordinates use the parameter names and types established here; no other parameter names are permitted in implementing classes.

```kotlin
package com.luopan.compass.magnetic

interface MagneticFieldModel {

    /**
     * Expected total field magnitude in µT for use as the interference detection baseline.
     *
     * @param latDeg  Geographic latitude in decimal degrees. Range: [−90.0, 90.0].
     * @param lonDeg  Geographic longitude in decimal degrees. Range: [−180.0, 180.0).
     * @param altM    Altitude above WGS-84 ellipsoid in meters. Use 0.0 when unavailable.
     * @param epochYears  Decimal year. Computed as: year + dayOfYear / 365.25.
     *                    Example: 2025-07-02 ≈ 2025.5.
     * @return Total field magnitude in µT. Always > 0.
     */
    fun getExpectedFieldMagnitude(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): Float

    /**
     * Expected magnetic inclination (dip angle) in degrees.
     * Positive values indicate downward dip (northern hemisphere typical).
     *
     * @param latDeg  Geographic latitude in decimal degrees.
     * @param lonDeg  Geographic longitude in decimal degrees.
     * @param altM    Altitude in meters.
     * @param epochYears  Decimal year (see getExpectedFieldMagnitude for formula).
     * @return Inclination in degrees. Range: [−90.0, 90.0].
     */
    fun getExpectedInclination(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): Float

    /**
     * Magnetic declination (angle between magnetic north and geographic north) in decimal degrees.
     * Positive values indicate east declination; negative values indicate west declination.
     *
     * @param latDeg  Geographic latitude in decimal degrees.
     * @param lonDeg  Geographic longitude in decimal degrees.
     * @param altM    Altitude in meters.
     * @param epochYears  Decimal year.
     * @return Declination in degrees. Range: (−180.0, 180.0].
     */
    fun getDeclination(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): Float

    /**
     * Returns the model identifier string written into BearingRecord.calibration_version.
     * Canonical values: "WMM2025" for Wmm2025Model; "AndroidGeoField" for AndroidGeoFieldModel.
     * The returned string is non-empty and stable across app restarts.
     */
    fun getModelId(): String

    /**
     * Returns true when the model is outside its valid epoch range and the provider
     * should switch to the fallback model.
     *
     * For Wmm2025Model: true when epochYears < 2025.0 or epochYears >= 2030.0.
     * For AndroidGeoFieldModel: always returns false (no expiry concept).
     */
    fun isExpired(): Boolean
}
```

**Contracts:**
- `getExpectedFieldMagnitude`, `getExpectedInclination`, and `getDeclination` are pure functions: given the same four parameters they always return the same result. They have no observable side effects.
- All three coordinate-accepting methods are safe to call from any thread.
- `isExpired()` uses the current wall-clock epoch year internally and is not deterministic across dates. In tests, implementations receive a `Clock` dependency to make `isExpired()` deterministic (see `Wmm2025Model` constructor, §3.1).
- No implementation may make a network call. All computations are offline.

**Test double:**

```kotlin
// com.luopan.compass.magnetic (androidTest / test source set only)
class FakeMagneticFieldModel(
    private val fieldMagnitude: Float = 50f,
    private val inclination: Float = 45f,
    private val declination: Float = 0f,
    private val modelId: String = "FakeModel",
    private val expired: Boolean = false
) : MagneticFieldModel {
    override fun getExpectedFieldMagnitude(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double) = fieldMagnitude
    override fun getExpectedInclination(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double) = inclination
    override fun getDeclination(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double) = declination
    override fun getModelId() = modelId
    override fun isExpired() = expired
}
```

---

### 2.2 `Clock` Interface — Decision

**Decision: define a new `Clock` interface in `com.luopan.compass.util`; do NOT reuse `TimeSource`.**

**Rationale:** The existing `TimeSource` interface (defined in `com.luopan.compass.sensor`) exposes two methods: `nowMs(): Long` and `nowNs(): Long`. The nanosecond method is sensor-pipeline-specific — it wraps `android.os.SystemClock.elapsedRealtimeNanos()` which returns a monotonic clock unsuitable for wall-clock date arithmetic. Reusing `TimeSource` for cache-age and cache-expiry logic would:

1. Pull a sensor-package type into `location`, `bearing`, and `ui` packages — creating an inappropriate cross-package dependency.
2. Force `FakeClock` implementations to provide a `nowNs()` that is semantically unrelated to the purpose (cache-age display is in calendar milliseconds, not nanoseconds).
3. Conflate two distinct time concepts: monotonic elapsed-time (for sensor pipeline hysteresis) vs. wall-clock epoch-milliseconds (for cache date arithmetic).

The new `Clock` interface lives in `com.luopan.compass.util` and has exactly one method, keeping it focused on its single responsibility.

```kotlin
// com.luopan.compass.util.Clock
package com.luopan.compass.util

interface Clock {
    /** Returns the current UTC time in milliseconds since the Unix epoch. */
    fun nowMs(): Long
}

// Production implementation.
// Named WallClock (not SystemClock) to avoid shadowing android.os.SystemClock.
// WallClock has a no-arg constructor — it simply wraps System.currentTimeMillis().
class WallClock : Clock {
    override fun nowMs(): Long = System.currentTimeMillis()
}

// Test implementation (lives in test / androidTest source sets).
class FakeClock(private var nowMs: Long = 0L) : Clock {
    override fun nowMs(): Long = nowMs
    fun advance(ms: Long) { nowMs += ms }
    fun set(ms: Long) { nowMs = ms }
}
```

> **DI contract for `WallClock` (TE-T-06):** `WallClock` has a no-arg constructor and is a concrete class — this is intentional, as it wraps a single `System.currentTimeMillis()` call with no configuration. However, **all production uses of the `Clock` interface MUST be injected via the DI module**. No class other than the DI module (the Hilt/manual module that binds `WallClock` to `Clock`) may instantiate `WallClock()` directly. This is enforced by convention and code review: if a class needs the current time, it declares a `Clock` constructor parameter and receives `WallClock` through DI in production and `FakeClock` in tests. The pattern `class Foo(private val clock: Clock = WallClock())` with a default parameter is **prohibited** — it allows callers to bypass DI silently. Constructor parameters of type `Clock` MUST NOT have default values.

`TimeSource` and `SystemTimeSource` in `com.luopan.compass.sensor` are **not modified** in Phase 2. They remain the time source for the sensor pipeline and `InterferenceDetector` hysteresis timing.

---

## 3. New Classes and Their Responsibilities

### 3.1 `Wmm2025Model`

**Package:** `com.luopan.compass.magnetic`

**Responsibility:** Implements `MagneticFieldModel` using the bundled WMM2025 Gauss coefficients (loaded from `res/raw/wmm2025_cof.txt`). This is the primary model used when the device date is within the 2025.0–2030.0 validity window.

**Constructor parameters (all injected):**

| Parameter | Type | Description |
|-----------|------|-------------|
| `context` | `Context` | Used once to open the raw resource for coefficient loading. |
| `clock` | `Clock` | Injected for `isExpired()` epoch-year computation. `WallClock` in production; `FakeClock` in tests. |

**Public methods:**

```kotlin
class Wmm2025Model(context: Context, private val clock: Clock) : MagneticFieldModel {

    // Eagerly loads coefficients in the constructor. Throws WmmLoadException on failure.
    // Coefficients are stored as two parallel float arrays: g[n][m] and h[n][m], n ∈ [1,12], m ∈ [0,n].

    override fun getExpectedFieldMagnitude(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): Float
    override fun getExpectedInclination(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): Float
    override fun getDeclination(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): Float
    override fun getModelId(): String = "WMM2025"
    override fun isExpired(): Boolean  // true when nowEpochYears < 2025.0 || nowEpochYears >= 2030.0

    // Internal: evaluate() runs the spherical harmonic computation for all three outputs
    // (magnitude, inclination, declination) in a single pass. All three public methods
    // delegate to evaluate() and return the relevant field. Results are NOT cached between
    // calls — the caller (MagneticFieldModelProvider) is responsible for caching per location update.
    private fun evaluate(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): WmmResult
}

// Return type from evaluate() — internal to the magnetic package.
data class WmmResult(
    val declination: Float,      // degrees, positive east
    val inclination: Float,      // degrees, positive downward
    val totalField: Float        // µT
)
```

**Key implementation notes:**
- Coefficient loading happens once in the constructor. If parsing fails (malformed file, missing asset), `Wmm2025Model` throws `WmmLoadException` (a sealed subclass of `Exception`). `MagneticFieldModelProvider` catches this and falls back to `AndroidGeoFieldModel`.
- The spherical harmonic evaluation follows the standard WMM technical note algorithm (NOAA/NGDC WMM2020 Technical Note — same algorithm applies to WMM2025 coefficients). The implementation uses `apache commons-math3` (already a dependency per `build.gradle.kts`) only if its `LegendrePolynomials` utility reduces code; otherwise a standalone recursive implementation is acceptable. Do NOT add a new dependency for WMM math.
- `isExpired()` computes the current epoch year on every call: `epochYears = calendar.get(YEAR) + calendar.get(DAY_OF_YEAR) / 365.25`. It uses the `clock.nowMs()` value converted to a `Calendar` in UTC. This is O(1) and sufficiently fast for the debounced check frequency specified in FSPEC §2.1.
- `getModelId()` returns the compile-time constant `"WMM2025"`.

---

### 3.2 `AndroidGeoFieldModel`

**Package:** `com.luopan.compass.magnetic`

**Responsibility:** Implements `MagneticFieldModel` as a thin adapter over `android.hardware.GeomagneticField`. Used when `Wmm2025Model.isExpired()` is true or when `Wmm2025Model` throws `WmmLoadException`.

**Constructor parameters:** none.

**Public methods:**

```kotlin
class AndroidGeoFieldModel : MagneticFieldModel {
    override fun getExpectedFieldMagnitude(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): Float
    override fun getExpectedInclination(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): Float
    override fun getDeclination(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): Float
    override fun getModelId(): String = "AndroidGeoField"
    override fun isExpired(): Boolean = false  // GeomagneticField has no expiry concept
}
```

**Key implementation notes:**
- `GeomagneticField` requires a timestamp in milliseconds. The `epochYears` parameter is converted to epoch-ms: `((epochYears - 1970.0) * 365.25 * 24 * 3600 * 1000).toLong()`. This is an approximation; for the expiry-fallback use case the accuracy is sufficient.
- `android.hardware.GeomagneticField` is constructed fresh on each method call (it is a lightweight object). Do not cache an instance across different lat/lon/alt/epoch arguments — the constructor sets all inputs.
- `getModelId()` returns the compile-time constant `"AndroidGeoField"`.

---

### 3.3 `MagneticFieldModelProvider`

**Package:** `com.luopan.compass.magnetic`

**Responsibility:** Selects the active `MagneticFieldModel` at runtime (WMM2025 preferred; `AndroidGeoFieldModel` as fallback). Caches the most recent `WmmResult` per location update so that `CompassViewModel` and `InterferenceDetector` share one evaluation (per BR-11).

**Constructor parameters (all injected):**

| Parameter | Type | Description |
|-----------|------|-------------|
| `wmm` | `Wmm2025Model` | The primary model. May be null if loading failed; provider handles null by activating fallback immediately. |
| `fallback` | `AndroidGeoFieldModel` | Always available. |

**Public methods:**

```kotlin
class MagneticFieldModelProvider(
    private val wmm: Wmm2025Model?,
    private val fallback: AndroidGeoFieldModel
) {
    /**
     * Returns the currently active MagneticFieldModel.
     * Delegates isExpired() to wmm; if wmm is null or expired, returns fallback.
     */
    fun activeModel(): MagneticFieldModel

    /**
     * Evaluates the active model for the given location and current epoch year.
     * Caches the result as lastResult. Subsequent calls to getLastResult() return
     * the cached value without re-evaluating.
     *
     * Thread safety: must be called from a single coroutine (the sensor pipeline coroutine
     * on Dispatchers.Default). Not thread-safe by design — callers must not call this
     * from multiple coroutines concurrently.
     *
     * @param latDeg  Latitude in decimal degrees.
     * @param lonDeg  Longitude in decimal degrees.
     * @param altM    Altitude in meters (0.0 if unknown).
     * @param epochYears  Current decimal year (caller computes from Clock.nowMs()).
     */
    fun evaluate(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): WmmResult

    /**
     * Returns the most recent cached WmmResult, or null if evaluate() has never been called.
     * Used by InterferenceDetector to retrieve the expected baseline without re-evaluating.
     */
    fun getLastResult(): WmmResult?

    /**
     * Returns true when the active model has changed since the last call to activeModel().
     * CompassViewModel uses this to update the declination info panel source label.
     */
    fun hasModelChanged(): Boolean
}
```

**Key implementation notes:**
- `activeModel()` calls `wmm?.isExpired()` on each invocation. The result of `isExpired()` is not cached — the FSPEC specifies an `onResume` debounce (at most once per 60 s) which is handled in `CompassViewModel`, not here.
- The cached `WmmResult` is invalidated on each call to `evaluate()`. A single field `lastResult: WmmResult?` holds the cache; there is no LRU or time-based eviction.
- `MagneticFieldModelProvider` does not hold a `Clock` directly. The caller (`CompassViewModel`) computes `epochYears` from `Clock.nowMs()` and passes it.

---

### 3.4 `LocationRepository`

**Package:** `com.luopan.compass.location`

**Responsibility:** Manages the full GPS location lifecycle: requests a fresh fix on `onStart`, applies the 30-day cache expiry rule, stores/retrieves the cached location from `SharedPreferences`, and exposes a `StateFlow<LocationState>` for `CompassViewModel` to observe.

**Constructor parameters (all injected):**

| Parameter | Type | Description |
|-----------|------|-------------|
| `context` | `Context` | For `LocationManager` access and `SharedPreferences`. |
| `clock` | `Clock` | For cache-age and cache-expiry computation. |
| `prefs` | `SharedPreferences` | The `"luopan_location_cache"` prefs file, passed in to allow test injection. |

**Public methods:**

```kotlin
class LocationRepository(
    private val context: Context,
    private val clock: Clock,
    private val prefs: SharedPreferences
) {
    /** Observed by CompassViewModel. Emits the current location resolution state. */
    val locationState: StateFlow<LocationState>

    /**
     * Called from Activity.onStart(). Fires the location resolution chain at most once
     * per session. Subsequent calls within the same Activity lifecycle session are no-ops.
     * Launches a coroutine internally on Dispatchers.IO.
     */
    fun onStart(scope: CoroutineScope)

    /**
     * Called from Activity.onStop() to cancel any active GPS listener and avoid leaks.
     */
    fun onStop()

    /**
     * Stores manual coordinates entered by the user. Triggers a re-evaluation of
     * LocationState, emitting LocationState.Manual.
     */
    fun setManualLocation(latDeg: Double, lonDeg: Double, altM: Double = 0.0)

    /**
     * Clears manual coordinates. If no GPS cache is available, LocationState transitions
     * to LocationState.Unavailable.
     */
    fun clearManualLocation()

    /**
     * Returns true if the cached location (if any) is within the 30-day validity window.
     * Uses Clock.nowMs() for the current time. Public for unit testing.
     */
    fun isLocationCacheValid(): Boolean

    /**
     * Returns the currently resolved CachedLocation (GPS fresh, GPS cached, or manual),
     * or null if no location is available. Synchronous convenience accessor; equivalent
     * to reading locationState.value.location.
     */
    fun resolvedLocation(): CachedLocation?
}
```

**`LocationState` sealed class:**

```kotlin
// com.luopan.compass.location
sealed class LocationState {
    data class GpsFresh(val location: CachedLocation) : LocationState()
    data class GpsCached(val location: CachedLocation, val ageMs: Long) : LocationState()
    data class Manual(val location: CachedLocation) : LocationState()
    object Unavailable : LocationState()
    object PermissionDenied : LocationState()
}

data class CachedLocation(
    val latDeg: Double,
    val lonDeg: Double,
    val altM: Double,       // 0.0 if altitude unavailable from fix
    val timestampMs: Long   // UTC epoch ms when the fix was obtained
)
```

**Key implementation notes:**
- The location chain runs on `Dispatchers.IO`. `SharedPreferences` reads and writes are performed synchronously (they are fast; no Room interaction).
- Cache storage uses `SharedPreferences` (not Room). Justification: the location cache is a single record (not a history), requires no query capability, and must survive app-kills without needing Room's transaction machinery. Using SharedPreferences avoids a second database and keeps the cache co-located with other simple key-value settings. The prefs file `"luopan_location_cache"` is distinct from `"luopan_settings"` to allow targeted clearing in tests.
- Cache keys: `loc_lat` (Double), `loc_lon` (Double), `loc_alt` (Double), `loc_ts` (Long). Written atomically via `prefs.edit().apply()`.
- The GPS single-fix request uses `LocationManager.requestLocationUpdates()` with `GPS_PROVIDER`, `minTimeMs = 5000`, `minDistanceM = 0.0f`. A `Handler(Looper.getMainLooper())` is used for the listener callback. A 5-second timeout cancels the listener if no fix arrives (FSPEC §2.4 step 9 specifies 10 s, but the "session fresh" window for checking an already-available fix is 60 s — the 5 s timeout is for the *initial-request* wait; if no fix arrives in 5 s on first request, fall to cache). The FSPEC §2.4 step 1 specifies a 60-second freshness window for an already-cached GPS fix; the 10-second timeout in §2.4 step 9 applies to the ongoing periodic update listener that is registered after the session fix is established.
- `ACCESS_FINE_LOCATION` permission check is performed before any `LocationManager` call. If denied, `locationState` emits `LocationState.PermissionDenied`.

---

### 3.5 `BearingRepository`

**Package:** `com.luopan.compass.bearing`

**Responsibility:** Room-based CRUD for `BearingRecord`. Owns the `BearingDao` reference. Does not own encryption setup (that remains in `LuopanDatabase` / `DatabaseKeyManager` as in Phase 1).

**Constructor parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `dao` | `BearingDao` | Room DAO, injected by `LuopanDatabase`. |

**Public methods:**

```kotlin
class BearingRepository(private val dao: BearingDao) {

    /**
     * Inserts a new BearingRecord atomically. Suspends until the insert completes.
     * Throws on constraint violation or storage failure (caller handles).
     */
    suspend fun insert(record: BearingRecord)

    /**
     * Returns the total count of records in bearing_records. Used to compute the
     * default capture name "Bearing [N+1]".
     */
    suspend fun count(): Int

    /**
     * Returns all BearingRecords ordered by captured_at descending.
     * Used in Phase 4 for the history screen; present now to avoid a future migration.
     */
    suspend fun getAll(): List<BearingRecord>

    /**
     * Deletes a single record by id. Used in Phase 4; present now.
     */
    suspend fun delete(id: String)
}
```

---

### 3.6 `BearingCaptureUseCase`

**Package:** `com.luopan.compass.bearing`

**Responsibility:** Orchestrates the capture flow end-to-end: validates the snapshot, resolves `interference_flag` from `InterferenceState`, constructs the `BearingRecord`, and delegates to `BearingRepository.insert()`. This is a pure domain class — no Android context, no ViewModel lifecycle.

**Constructor parameters (all injected):**

| Parameter | Type | Description |
|-----------|------|-------------|
| `bearingRepository` | `BearingRepository` | Writes the record. |
| `locationRepository` | `LocationRepository` | Provides the latest resolved location (`lat`, `lon`, `alt_m`) at save time when the GPS toggle is ON. |
| `modelProvider` | `MagneticFieldModelProvider` | Reads `activeModel().getModelId()` for `calibration_version`. |
| `clock` | `Clock` | Reserved for future use (e.g., clock-skew diagnostics). `captured_at` is carried in `BearingSnapshot.tapTimestampMs` — see PM-T-01 note below. |

> **PM-T-03 resolution:** `LocationRepository` is added to the constructor (aligning §1.3 dependency graph with §3.6). The use case reads `locationRepository.resolvedLocation()` at `execute()` time to populate `lat`/`lon`/`alt_m` when `snapshot.includeLocation == true`. The ViewModel sets `snapshot.latDeg`/`lonDeg`/`altM` from the snapshot taken at tap time; the use case may also validate the resolved location is still the same (or accept the snapshotted value). For Phase 2, the ViewModel populates the location fields in the snapshot at tap time from `LocationRepository.resolvedLocation()` and passes them through; `BearingCaptureUseCase` uses the snapshotted values directly (it does not call `locationRepository.resolvedLocation()` a second time). The `LocationRepository` dependency is present in the constructor to make the contract explicit and to allow future phases to query the live location at save time without a constructor change.

**Public methods:**

```kotlin
class BearingCaptureUseCase(
    private val bearingRepository: BearingRepository,
    private val locationRepository: LocationRepository,
    private val modelProvider: MagneticFieldModelProvider,
    private val clock: Clock
) {
    /**
     * Executes the save. Constructs a BearingRecord from the snapshot and persists it.
     *
     * @param snapshot  Immutable snapshot taken at capture button tap time.
     *                  snapshot.tapTimestampMs is used for BearingRecord.captured_at (PM-T-01).
     * @return The saved BearingRecord (with id assigned).
     * @throws IllegalStateException if snapshot.northType == NorthType.GRID (programming error guard).
     * @throws BearingInsertException wrapping the Room exception on storage failure.
     */
    suspend fun execute(snapshot: BearingSnapshot): BearingRecord
}
```

**`BearingSnapshot` data class:**

```kotlin
// com.luopan.compass.bearing
data class BearingSnapshot(
    val bearingDeg: Float,               // normalized to [0.0, 360.0)
    val northType: NorthType,            // TRUE or MAGNETIC; GRID is rejected in execute()
    val confidence: OverallConfidence,   // HIGH, MODERATE, or POOR
    val interferenceState: InterferenceState, // used to derive interference_flag per BR-10
    val fieldDeviationPct: Float,        // InterferenceMetrics.fieldDeviation * 100
    val inclinationDeviationDeg: Float,  // InterferenceMetrics.inclinationDeviation_deg
    val latDeg: Double?,                 // null if GPS toggle OFF or unavailable
    val lonDeg: Double?,
    val altM: Double?,
    val name: String,                    // trimmed, length 1–100
    val notes: String?,                  // null if not entered; empty string is coerced to null
    val displayMode: String = "MODERN",  // Phase 2 always writes "MODERN"
    val includeLocation: Boolean,        // from GPS toggle state
    val tapTimestampMs: Long             // PM-T-01: wall-clock ms at the instant the capture button was tapped;
                                        // populated by CompassViewModel.captureBearing() via clock.nowMs()
                                        // BEFORE showing any dialog; used as BearingRecord.captured_at
)

enum class NorthType { TRUE, MAGNETIC, GRID }
```

**Key implementation notes:**
- `interference_flag` derivation: `snapshot.interferenceState == InterferenceState.MODERATE || snapshot.interferenceState == InterferenceState.WARNING`. This is the sole source — `OverallConfidence.POOR` does not affect this field (BR-10).
- The GRID invariant: `require(snapshot.northType != NorthType.GRID) { "GRID north type must never be written to BearingRecord" }`. This is a programming-error guard, not a user-facing validation.
- `notes` empty-state: if `snapshot.notes` is an empty string after trimming, `execute()` writes `null` to the record. Only a genuinely entered string (non-empty after trim) is stored.
- `id` generation: `java.util.UUID.randomUUID().toString()` — UUID v4.
- `captured_at` (PM-T-01 resolution): `BearingRecord.captured_at` is set to `snapshot.tapTimestampMs` — the wall-clock milliseconds recorded at the instant the capture button was tapped, populated by `CompassViewModel.captureBearing()` via `clock.nowMs()` before any dialog is shown. This aligns with FSPEC §6.1 and BR-09: "the bearing snapshot is taken at the instant of the capture button tap." The use case does NOT use `clock.nowMs()` at execute time for `captured_at`; the tap timestamp is carried through the snapshot. This ensures a user who taps at 10:00:00 and types a name for 14 seconds still sees the record labeled 10:00:00 in the Phase 4 history screen.

---

### 3.7 `CompassViewModel` (Extended)

**Package:** `com.luopan.compass.ui`

**Responsibility:** Extended to own True North toggle state, declination value, extreme latitude advisory, and the bridge to `BearingCaptureUseCase`. The Phase 1 `CompassViewModel` constructor receives new injected dependencies via the `ViewModelProvider.Factory` pattern (replacing the current `AndroidViewModel` no-arg constructor pattern).

**New constructor parameters (added to existing Phase 1 parameters):**

| Parameter | Type | Description |
|-----------|------|-------------|
| `modelProvider` | `MagneticFieldModelProvider` | Provides declination and interference baseline. |
| `locationRepository` | `LocationRepository` | Provides resolved location. |
| `captureUseCase` | `BearingCaptureUseCase` | Invoked on bearing capture. |
| `clock` | `Clock` | For calibration age computation (replaces `System.currentTimeMillis()` in `loadCalibrationAge()` — Phase 2 cleanup opportunity per FSPEC §6.2). |

**New public methods and state:**

```kotlin
// New StateFlow fields exposed by CompassViewModel:
val northType: StateFlow<NorthType>              // TRUE or MAGNETIC
val declinationDeg: StateFlow<Float>             // current declination, 0f when magnetic mode
val locationState: StateFlow<LocationState>      // forwarded from LocationRepository
val extremeLatitudeAdvisory: StateFlow<Boolean>  // true when |inclination| >= 80°
val wmmModelId: StateFlow<String>                // "WMM2025" or "AndroidGeoField"
val captureButtonEnabled: StateFlow<Boolean>     // BR-CAP-08 debounce state

// New public methods:
fun setNorthType(type: NorthType)
fun setManualLocation(latDeg: Double, lonDeg: Double, altM: Double)
fun onActivityStart(scope: CoroutineScope)       // delegates to locationRepository.onStart()
fun onActivityStop()                             // delegates to locationRepository.onStop()
fun checkWmmExpiry()                             // called from onResume when northType == TRUE; debounced to 60 s
fun captureBearing(snapshot: BearingSnapshot)    // disables button, launches coroutine, re-enables after save

// CompassUiState additions (new fields on the existing data class):
//   north_type: NorthType
//   declination_deg: Float
//   extreme_latitude_advisory: Boolean
//   wmm_model_id: String
//   capture_button_enabled: Boolean
```

**Key implementation notes:**
- `setNorthType()` is the only entry point for north type changes. It persists the preference to `SettingsRepository` (new key `KEY_NORTH_TYPE`) and triggers an immediate declination computation if a location is available.
- Declination application: `displayHeading = if (northType == NorthType.TRUE) (magneticHeading + declinationDeg + 360.0) % 360.0 else magneticHeading`. The `+360.0) % 360.0` normalization ensures the result is always in [0°, 360°).
- The expiry debounce: `lastExpiryCheckMs: Long = -1L`. In `checkWmmExpiry()`: `if (clock.nowMs() - lastExpiryCheckMs < 60_000L) return`. Update `lastExpiryCheckMs` after each check.
- `captureBearing()` sets `_captureButtonEnabled.value = false`, launches a coroutine on `Dispatchers.IO` calling `captureUseCase.execute(snapshot)`, then sets `_captureButtonEnabled.value = true` in a `finally` block.
- The extreme latitude advisory: computed in the sensor pipeline coroutine after each `WmmResult` update. `_extremeLatitudeAdvisory.value = Math.abs(wmmResult.inclination) >= 80.0f`. The confidence cap is applied in `ConfidenceModel.compute()` — a new parameter `extremeLatitudeActive: Boolean` is added to `ConfidenceModel.compute()`, which caps the result at `OverallConfidence.MODERATE` if `true`.

---

### 3.8 `InterferenceDetector` (Updated)

**Package:** `com.luopan.compass.sensor`

**Current signature:**
```kotlin
fun updateState(metrics: InterferenceMetrics, nowNs: Long)
```

**Phase 2 change:** The `expectedField_uT` and `expectedInclination_deg` fields of `InterferenceMetrics` are now populated by the caller (`CompassViewModel`) from the `WmmResult` rather than from the rolling EMA. `InterferenceDetector` itself does not change its `updateState` signature — only the values passed in `metrics` change.

Specifically, in `CompassViewModel.startSensorCollection()`:
- The `baselineFieldUt` EMA is retained as a no-location fallback only (when `modelProvider.getLastResult() == null`).
- When `getLastResult()` is non-null: `metrics.expectedField_uT = wmmResult.totalField`; `metrics.expectedInclination_deg = wmmResult.inclination`; `metrics.inclinationDeviation_deg = abs(fusion.pitch_deg - wmmResult.inclination)`.
- When `getLastResult()` is null: existing EMA behavior is retained; `metrics.expectedInclination_deg = 0f` and `metrics.inclinationDeviation_deg = abs(fusion.pitch_deg)`.

**No new constructor parameters are added to `InterferenceDetector`** — the WMM integration is handled entirely by the caller constructing a more accurate `InterferenceMetrics`. This preserves the Phase 1 `InterferenceDetector` contract and requires zero changes to its internal state machine.

---

## 4. Data Layer

### 4.1 `BearingRecord` Room Entity

**Package:** `com.luopan.compass.bearing`

```kotlin
@Entity(tableName = "bearing_records")
data class BearingRecord(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,                          // UUID v4, e.g. "550e8400-e29b-41d4-a716-446655440000"

    @ColumnInfo(name = "name")
    val name: String,                        // TEXT NOT NULL; length 1–100 trimmed

    @ColumnInfo(name = "bearing_deg")
    val bearing_deg: Float,                  // REAL NOT NULL; range [0.0, 360.0)

    @ColumnInfo(name = "north_type")
    val north_type: String,                  // TEXT NOT NULL; "TRUE" or "MAGNETIC"

    @ColumnInfo(name = "confidence")
    val confidence: String,                  // TEXT NOT NULL; "HIGH", "MODERATE", or "POOR"

    @ColumnInfo(name = "captured_at")
    val captured_at: Long,                   // INTEGER NOT NULL; UTC epoch ms

    @ColumnInfo(name = "calibration_version")
    val calibration_version: String,         // TEXT NOT NULL; MagneticFieldModel.getModelId()

    @ColumnInfo(name = "field_deviation_pct")
    val field_deviation_pct: Float,          // REAL NOT NULL; >= 0.0

    @ColumnInfo(name = "inclination_deviation_deg")
    val inclination_deviation_deg: Float,    // REAL NOT NULL; >= 0.0

    @ColumnInfo(name = "interference_flag")
    val interference_flag: Boolean,          // INTEGER NOT NULL; 0 or 1

    @ColumnInfo(name = "lat")
    val lat: Double?,                        // REAL NULL

    @ColumnInfo(name = "lon")
    val lon: Double?,                        // REAL NULL

    @ColumnInfo(name = "alt_m")
    val alt_m: Double?,                      // REAL NULL

    @ColumnInfo(name = "notes")
    val notes: String?,                      // TEXT NULL

    @ColumnInfo(name = "display_mode")
    val display_mode: String?                // TEXT NULL; Phase 2 writes "MODERN"
)
```

**Field notes:**
- `id`: UUID v4 string. Not auto-increment integer. Supports future sync/export without collision.
- `interference_flag`: Room maps `Boolean` to `INTEGER` (0/1) automatically when using the default type converters. No explicit `@TypeConverter` is required.
- `lat`, `lon`, `alt_m`: `Double?` — nullable Double maps to `REAL NULL` in SQLite. `Float` would give only ~11 km precision; `Double` gives ~11 cm at 6 decimal places (required for WMM input accuracy per FSPEC §6.1).
- `notes`: null when not entered; empty string is never written (coerced to null in `BearingCaptureUseCase.execute()`).
- `display_mode`: nullable String. Phase 2 always writes `"MODERN"`. Nullable to avoid a schema migration when Phase 3 introduces Luopan mode.
- `calibration_version`: stores the WMM model identifier (e.g., `"WMM2025"` or `"AndroidGeoField"`). This is NOT the `CalibrationRecord.calibration_schema_version` integer — it records the magnetic field model active at capture time.

### 4.2 `BearingDao`

**Package:** `com.luopan.compass.bearing`

```kotlin
@Dao
interface BearingDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: BearingRecord)

    @Query("SELECT COUNT(*) FROM bearing_records")
    suspend fun count(): Int

    @Query("SELECT * FROM bearing_records ORDER BY captured_at DESC")
    suspend fun getAll(): List<BearingRecord>

    @Query("DELETE FROM bearing_records WHERE id = :id")
    suspend fun delete(id: String)
}
```

Note: `OnConflictStrategy.ABORT` is used (not REPLACE) because UUID v4 collisions are astronomically unlikely; an abort on conflict surfaces a programming error rather than silently overwriting a record.

### 4.3 Database Migration: `Migration(1, 2)`

**Package:** `com.luopan.compass.db`

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS bearing_records (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                bearing_deg REAL NOT NULL,
                north_type TEXT NOT NULL,
                confidence TEXT NOT NULL,
                captured_at INTEGER NOT NULL,
                calibration_version TEXT NOT NULL,
                field_deviation_pct REAL NOT NULL,
                inclination_deviation_deg REAL NOT NULL,
                interference_flag INTEGER NOT NULL,
                lat REAL,
                lon REAL,
                alt_m REAL,
                notes TEXT,
                display_mode TEXT
            )
        """.trimIndent())
    }
}
```

### 4.4 `LuopanDatabase` Changes (Version 1 → 2)

The following changes are made to `LuopanDatabase`:

1. **`@Database` annotation update:**
   ```kotlin
   @Database(
       entities = [CalibrationRecord::class, BearingRecord::class],
       version = 2,
       exportSchema = true   // REQUIRED — MigrationTestHelper requires schema JSON files
   )
   ```

2. **Remove `fallbackToDestructiveMigration()`** from `buildDatabase()`. Replace with explicit migration:
   ```kotlin
   .addMigrations(MIGRATION_1_2)
   ```
   This is a hard constraint from REQ §10.2: `fallbackToDestructiveMigration()` MUST NOT be used.

3. **Add abstract DAO accessor:**
   ```kotlin
   abstract fun bearingDao(): BearingDao
   ```

4. **`exportSchema = true`**: The schema JSON files are exported to `app/schemas/`. These files must be committed to version control. `MigrationTestHelper` in `androidTest` requires them to verify migration correctness.

### 4.5 SQLCipher Encryption

The SQLCipher setup from Phase 1 is unchanged: `DatabaseKeyManager` generates a 256-bit AES-GCM key in Android Keystore, encrypts a 32-byte random passphrase, stores it in `SharedPreferences`, and passes it to `SupportFactory`. The `bearing_records` table is automatically covered by the same database encryption because it is in the same `bearing_records.db` database file.

No changes to `DatabaseKeyManager` are required in Phase 2.

> **Canonical database filename (TE-T-02 normative note):** The canonical SQLite database filename is **`bearing_records.db`** as specified in FSPEC AT-PERSIST-01. All tests that open the raw database file MUST use `targetContext.getDatabasePath("bearing_records.db")`. The internal Room `@Database` builder uses this filename (passed to `Room.databaseBuilder(context, LuopanDatabase::class.java, "bearing_records.db")`). References to `"luopan.db"` in Phase 1 documentation referred to the Phase 1 calibration database; Phase 2 the bearing database file is `"bearing_records.db"`. All tests must use this name.

---

## 5. WMM2025 Bundling Strategy

### 5.1 Storage Format

The 168 Gauss coefficients (12 degrees, each degree n having n+1 orders from 0 to n; total = 2 × Σ(n=1 to 12)(n+1) = 2 × 90 = 180 values, but the m=0 h-coefficients are zero by definition, leaving 168 non-trivial values) are stored as a raw text resource.

**File:** `app/src/main/res/raw/wmm2025_cof.txt`

**Format:** The standard WMM COF (coefficient) file format published by NOAA/BGS. Each data line has the format:
```
n  m  gnm  hnm  gnm_sv  hnm_sv
```
Where `gnm` and `hnm` are the main field Gauss coefficients, and `gnm_sv` / `hnm_sv` are the secular variation coefficients. All are floating-point values in nanoTesla. The file starts with a header line containing the epoch year and model name.

**Rationale for raw resource vs. constants file:** The COF file format is the canonical distribution format from NOAA. Storing it as a raw resource preserves the original file checksum for license/provenance verification, avoids hard-coding 168 floating-point constants in source code (which would be error-prone and hard to audit), and allows a simple diff against the NOAA-published file during code review. A constants file (Kotlin companion object with 168 `const val` entries) would be acceptable from a performance standpoint but inferior for auditability.

### 5.2 Parsing/Loading Strategy

`Wmm2025Model` loads the file in its constructor using:

```kotlin
context.resources.openRawResource(R.raw.wmm2025_cof)
    .bufferedReader(Charsets.UTF_8)
    .use { reader -> parseCoefficients(reader) }
```

`parseCoefficients()` reads lines, skips the header, and populates two 2D arrays: `g[n][m]: Double` and `h[n][m]: Double` (and corresponding secular variation arrays `gSv[n][m]`, `hSv[n][m]`). Indices: `n` in 1..12, `m` in 0..n. Any line that cannot be parsed (wrong token count, non-numeric value) throws `WmmLoadException`.

The load is performed once (in the constructor) and the coefficient arrays are held as `private val` fields. Loading from disk on the first True North activation would add latency to the toggle; loading in the constructor (called at app startup by `MagneticFieldModelProvider`) amortizes the cost before user interaction.

**Cold-start impact and lazy-loading init path (PM-T-04):** The COF file is approximately 3–5 KB of text. Parsing 168 lines on the main thread would add ~2–5 ms on a mid-range device. `Wmm2025Model` is constructed on `Dispatchers.IO` inside `MagneticFieldModelProvider` initialization, which is launched as a coroutine from `CompassViewModel.init {}`. This keeps the main thread unblocked.

The precise init sequence is:

1. `CompassViewModel.init {}` launches `viewModelScope.async(Dispatchers.IO) { Wmm2025Model(context, clock) }` — this is the `Deferred<Wmm2025Model?>` held by the ViewModel.
2. WMM coefficients are loaded lazily on first access via a `Mutex`-protected `val coefficients` that is populated on `Dispatchers.IO` within the `Wmm2025Model` constructor. `MagneticFieldModelProvider.getModel()` calls `awaitCoefficients()` if the deferred has not yet resolved.
3. This ensures no blocking on the main thread. Cold starts resolve WMM on first sensor tick, which fires within 100–300 ms of `onStart` (the sensor registration lifecycle trigger). By the time the user can tap the True North toggle, WMM is almost always already loaded.
4. When `setNorthType(TRUE)` is called and the `Deferred` has not yet completed, `CompassViewModel` calls `wmm.await()` on the deferred inside a `viewModelScope.launch(Dispatchers.IO)` block, ensuring the `await()` never blocks the main thread. If the `await` takes longer than 200 ms (extremely rare cold-start edge case), the heading update budget is not violated because the declination computation runs on `Dispatchers.IO` and posts the result to the UI via `StateFlow`.

### 5.3 Epoch Year Computation

```kotlin
fun Clock.toEpochYears(): Double {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = this.nowMs()
    val year = cal.get(Calendar.YEAR)
    val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
    val daysInYear = if (cal.getActualMaximum(Calendar.DAY_OF_YEAR) == 366) 366.0 else 365.0
    return year + (dayOfYear - 1) / daysInYear
}
```

Using `daysInYear` from the actual calendar (366 for leap years) rather than the fixed `365.25` denominator from the FSPEC is more accurate. Both approaches are acceptable; this implementation is preferred for correctness. The difference is <0.003 epoch years, well within WMM accuracy tolerances.

---

## 6. Location Strategy

### 6.1 Priority Chain

The location resolution chain fires on `Activity.onStart()` (not `onResume()` — see FSPEC §2.4 step 1 for rationale). The chain evaluates steps sequentially:

| Step | Condition | Action | `LocationState` emitted |
|------|-----------|--------|------------------------|
| 1 | `ACCESS_FINE_LOCATION` granted AND GPS fix available within 60 s | Use fix; cache to SharedPreferences | `LocationState.GpsFresh` |
| 1a | `ACCESS_FINE_LOCATION` granted AND no fresh fix | Request single-update with 5 s timeout; if fix arrives → use; if timeout → go to Step 2 | (waits) |
| 2 | Valid cache exists (`clock.nowMs() - ts < 30 × 86_400_000`) | Use cached location | `LocationState.GpsCached` |
| 3 | No fresh fix, cache absent or expired | Emit `Unavailable`; offer manual entry on True North toggle | `LocationState.Unavailable` |

Permission denied at any step: emit `LocationState.PermissionDenied`.

### 6.2 GPS Request Parameters

```kotlin
LocationManager.requestLocationUpdates(
    LocationManager.GPS_PROVIDER,
    5_000L,     // minTimeMs: 5 seconds
    0.0f,       // minDistanceM: no distance filter
    locationListener,
    Looper.getMainLooper()
)
```

A `Handler.postDelayed(timeout = 5_000L)` cancels the listener if no fix arrives within 5 seconds of the initial request during Step 1a. For the ongoing periodic update (Step 9 in FSPEC §2.4), the listener remains registered while `northType == TRUE` and the activity is started; a separate 10-second timeout applies to the first update delivery.

### 6.3 Location Provider Scope (PM-T-06)

**`NETWORK_PROVIDER` and `PASSIVE_PROVIDER` are NOT used in Phase 2.** The location resolution chain uses only `GPS_PROVIDER` (Step 1/1a in §6.1). The FSPEC §2.4 priority chain is: session GPS fix → 30-day cached location → manual entry. Network and passive providers are explicitly out of scope for Phase 2.

**Rationale:** A network-provider fix could yield significantly lower accuracy (e.g., 1–5 km cell-tower accuracy) than GPS, but would be presented with the same "GPS fix" or "Cached location" label in the declination info panel, misleading users about the declination computation precision. FSPEC §2.4 does not mention `NETWORK_PROVIDER`; engineering additions that change location resolution semantics require FSPEC alignment before implementation.

If `GPS_PROVIDER` is unavailable (e.g., indoor, or location services disabled), the chain falls through Step 1a (timeout), then Step 2 (30-day cache), then Step 3 (manual entry). No network or passive provider is consulted.

### 6.4 Cache Storage

**Choice: SharedPreferences** (not Room).

**Justification:** The location cache is a single record (not a list), requires no SQL query, and must be available synchronously before Room is fully initialized. Writing four primitive values to SharedPreferences is faster than a Room INSERT and avoids a Room-on-main-thread constraint. SharedPreferences is appropriate for single-record key-value state. The cache is stored in a dedicated prefs file `"luopan_location_cache"` (separate from `"luopan_settings"`) to allow targeted test clearing without affecting other settings.

**Cache keys and types:**

| Key | Type | Default |
|-----|------|---------|
| `loc_lat` | `Double` (stored as `String` via `toString()`/`toDouble()`) | none |
| `loc_lon` | `Double` | none |
| `loc_alt` | `Double` | `0.0` |
| `loc_ts` | `Long` | `0L` |

Note: `SharedPreferences` does not natively support `Double`; store as `String` via `Double.toString()` and `String.toDouble()`. Alternatively, use `Float` (accepting the precision loss) or `Long` (via `Double.toBits()` / `Double.fromBits()`). Use `Double.toBits()` / `Double.fromBits()` stored as `Long` to preserve full double precision without string parsing.

### 6.5 Permission Request Flow

1. On True North toggle tap, check `ContextCompat.checkSelfPermission(ACCESS_FINE_LOCATION)`.
2. If granted: proceed with location chain.
3. If not granted AND `ActivityCompat.shouldShowRequestPermissionRationale()` returns `true`: show rationale dialog ("Location is used to compute magnetic declination for True North. Your coordinates are stored on-device only and are never shared.") with "Continue" and "Not now" buttons. On "Continue": launch `ActivityResultLauncher<Array<String>>` for `ACCESS_FINE_LOCATION`. On "Not now": fall to Step 5.
4. If not granted AND `shouldShowRequestPermissionRationale()` returns `false` (first request or permanent denial): launch `ActivityResultLauncher` directly (first request) or show Settings-redirect message (permanent denial, detected by checking if permission is permanently denied using standard Android approach).
5. Permission denied: emit `LocationState.PermissionDenied`; show brief snackbar: "Location permission needed for True North. You can enter coordinates manually instead."; offer manual coordinate entry dialog.

---

## 7. UI Changes

### 7.1 True North Toggle

**Placement:** In `activity_compass.xml`, a `MaterialButtonToggleGroup` (or a pair of `MaterialButton` views in a toggle group) is added to the main compass layout, positioned in the top action bar area or below the compass rose heading display — specifically to the right of the heading digital readout, where it is visible without scrolling. The toggle occupies a single row with two buttons: "Magnetic N" and "True N". No third button for GRID is present.

**Implementation:** `MaterialButtonToggleGroup` with `app:singleSelection="true"` and `app:selectionRequired="true"`. Button IDs: `btn_magnetic_n` and `btn_true_n`. The toggle is bound to `CompassViewModel.northType` via `StateFlow` observation.

**Tap handling:** `onButtonChecked` listener calls `viewModel.setNorthType(NorthType.TRUE)` or `NorthType.MAGNETIC`. `setNorthType()` in the ViewModel is synchronous for the state update; the declination additive offset is applied on the next sensor frame (guaranteed ≤ one 20 Hz frame = 50 ms, well within the 200 ms budget).

**Info button:** An `ImageButton` with the info icon (`ic_info_outline_24`) is placed immediately adjacent to the toggle group. Tapping it opens the declination info panel (§7.2).

### 7.2 Declination Info Panel

**Implementation:** A `BottomSheetDialogFragment` subclass `DeclinationInfoBottomSheet`. This is preferred over a `Dialog` because:
- Bottom sheets have a standard dismiss gesture (swipe down) without requiring a "Close" button.
- Material Design 3 bottom sheets handle window-inset and back-press dismissal automatically.
- The panel is informational-only (no user action required) — a bottom sheet's non-blocking nature fits this use case better than a modal dialog.

**`DeclinationInfoBottomSheet` layout** shows all fields from FSPEC §2.3 step 3:
- Declination value (primary: `±D.D° E/W`, secondary: `(−D.DD°)`)
- Source label (from `wmmModelId` and `locationState`)
- Coordinates (masked to 2 decimal places via `"%.2f".format(lat)`)
- Coordinates type ("GPS fix", "Cached location", "Manual entry")
- Last updated date (`YYYY-MM-DD` formatted from `CachedLocation.timestampMs` in device local timezone)
- Cache age ("N days ago" for cached locations; hidden for GPS fix)
- Note when Magnetic N is active

**Data binding:** The bottom sheet receives a `DeclinationInfo` data class from the `CompassViewModel` (exposed as a `StateFlow<DeclinationInfo>`). Updates to the bottom sheet content happen on each `locationState` change.

### 7.3 Bearing Capture Button and Dialog Flow

**Capture button:** A `FloatingActionButton` (FAB) or large `MaterialButton` with a save/pin icon, labeled "Save Bearing" (`btn_save_bearing`). It is always visible in Modern Mode. It is enabled/disabled by observing `CompassViewModel.captureButtonEnabled` (BR-CAP-08).

**Dialog flow:**

1. **Pre-capture warning dialog** (shown only when `InterferenceState ∈ {MODERATE, WARNING}` OR `OverallConfidence == POOR`): `MaterialAlertDialog` with title, warning message, "Save with warning" (amber positive button), and "Cancel" (negative button). Implemented as `InterferenceWarningDialogFragment`.

2. **Name/Notes dialog** (`BearingCaptureDialogFragment`, a `DialogFragment`): contains all fields from FSPEC §2.5 step 4. The `TextInputEditText` for name has `maxLength="100"` via `android:maxLength`. Notes has `maxLength="1000"`. The GPS toggle is a `MaterialSwitch`. The first-capture privacy notice is a `TextView` shown/hidden based on `SharedPreferences` key `bearing_location_consent_shown`.

3. On "Save" tap: the dialog calls `viewModel.captureBearing(snapshot)`. The dialog dismisses immediately; the ViewModel handles the async insert and shows the confirmation toast via a `SharedFlow<String>` event channel.

**Toast:** `Toast.makeText(context, "Bearing saved as '${name}'", Toast.LENGTH_SHORT).show()` — short duration (≈2 s). Triggered from `CompassActivity` observing the ViewModel's `captureConfirmation: SharedFlow<String>`.

### 7.4 Extreme Latitude Advisory Banner

**Implementation:** A `TextView` (or `MaterialBanner`) in the main compass layout, positioned below the compass rose. Hidden by default (`View.GONE`). Shown when `CompassViewModel.extremeLatitudeAdvisory` emits `true`. The banner text is the localized string `R.string.extreme_latitude_advisory`. The banner is amber-colored (use `com.google.android.material.color.MaterialColors.getColor` with error container or warning color token). Non-dismissible: no close button; no click listener.

The banner coexists with the interference warning banner. Both are stacked vertically in a `LinearLayout` advisory container at the bottom of the compass layout.

---

## 8. Threading Model

### 8.1 IO-Bound Operations

| Operation | Dispatcher | Notes |
|-----------|-----------|-------|
| Room INSERT (`BearingRepository.insert`) | `Dispatchers.IO` | Room suspends on IO thread automatically for suspend functions |
| Room SELECT (`BearingRepository.count`, `getAll`) | `Dispatchers.IO` | Same |
| WMM coefficient loading (`Wmm2025Model` constructor) | `Dispatchers.IO` | File I/O; launched in `init {}` block of `CompassViewModel` |
| GPS location request / listener callback | Main thread (Looper) | `LocationManager` callbacks delivered on main thread; post results to `Dispatchers.Default` |
| SharedPreferences cache read/write | Calling thread | Fast; acceptable on `Dispatchers.IO` when called from coroutines |

### 8.2 CPU-Bound Operations

| Operation | Dispatcher | Notes |
|-----------|-----------|-------|
| WMM spherical harmonic evaluation | `Dispatchers.Default` | Called from the sensor pipeline coroutine which already runs on `Dispatchers.Default` |
| Interference metrics computation | `Dispatchers.Default` | Unchanged from Phase 1 |
| Declination addition to heading | `Dispatchers.Default` | Inline in the sensor coroutine |

### 8.3 ViewModel Coroutine Scopes

All coroutines launched from `CompassViewModel` use `viewModelScope`. Specific dispatchers:

- `viewModelScope.launch(Dispatchers.IO)` — database operations (calibration load, bearing insert).
- `viewModelScope.launch(Dispatchers.Default)` — sensor pipeline (unchanged from Phase 1).
- `viewModelScope.launch(Dispatchers.IO)` — WMM coefficient loading at init time; the `Wmm2025Model` instance is held in a `Deferred<Wmm2025Model?>` initialized with `viewModelScope.async(Dispatchers.IO) { ... }`.

### 8.4 StateFlow Update Threading

`_uiState.value = ...` and all other `MutableStateFlow.value` assignments are performed from within coroutines (not the main thread). `StateFlow` is thread-safe for `value` writes; the `CompassActivity` collector runs on the main thread via `lifecycleScope.launch { ... collect { ... } }` with `Lifecycle.State.STARTED`.

---

## 9. Deferred Decisions

The following decisions were flagged as deferred in FSPEC §6.2, §6.3, and elsewhere. Each is resolved here.

### 9.1 Clock/TimeSource Decision

**Resolution: define a new `Clock` interface in `com.luopan.compass.util`; do NOT reuse `TimeSource`.**

Full rationale in §2.2 above. Summary: `TimeSource.nowNs()` is sensor-pipeline-specific (monotonic nanoseconds) and inappropriate for calendar-based cache expiry arithmetic. A new `Clock` interface with `nowMs()` only keeps the concern clean and avoids cross-package dependencies.

`TimeSource` and `SystemTimeSource` are untouched. `CompassViewModel` gains a `Clock` injection; `loadCalibrationAge()` is updated to use `clock.nowMs()` instead of `System.currentTimeMillis()` as a Phase 2 cleanup.

### 9.2 `MagneticFieldModel` Parameter Names

**Resolution: adopt `latDeg`, `lonDeg`, `altM`, `epochYears`.**

These names are used consistently in all four coordinate-accepting methods of `MagneticFieldModel` (§2.1) and in all call sites in `CompassViewModel`, `BearingCaptureUseCase`, and `LocationRepository`. No alternative names are used in the codebase.

### 9.3 GPS Freshness Window

**Resolution: a GPS fix is "fresh" (eligible for `LocationState.GpsFresh`) if its timestamp is within the last 60 seconds.**

This is distinct from the 30-day cache validity window. A fix obtained 61 seconds ago is still cacheable (≪30 days old) but is not "fresh" for the initial session resolution (Step 1 of the location chain). Fresh fixes are used without showing "Cached location" labels in the UI.

The 10-second timeout in FSPEC §2.4 step 9 applies to the ongoing periodic GPS update listener, not to the session-start freshness check.

### 9.4 `notes` Empty-State

**Resolution: `null` means no note was entered; empty string is not written.**

In `BearingCaptureUseCase.execute()`:
```kotlin
val notes = snapshot.notes?.trim()?.ifEmpty { null }
```

The value written to `BearingRecord.notes` is always either `null` or a non-empty, trimmed string. An empty `TextInputEditText` produces `null` in the record. This matches FSPEC §6.1 which specifies `notes` as nullable.

---

## 10. Test Strategy

### 10.1 Unit Tests (JVM / Robolectric)

| Test Class | Target | Key Assertions |
|-----------|--------|----------------|
| `Wmm2025ModelTest` | `Wmm2025Model` | **Primary NOAA-pinned test vector (TE-T-01 — required; REQ §8 TE-REQ-02):** `lat=40.0, lon=−105.0, altM=0.0, epochYears=2025.5`. Assert: `getDeclination()` == `+8.93° ± 0.1°`; `getExpectedInclination()` == `+66.0° ± 0.5°`; `getExpectedFieldMagnitude()` == `52300 nT ± 200 nT`. This is the canonical vector pinned from the NOAA WMM2025 reference — it MUST remain the primary assertion and MUST NOT be replaced with a call to the live NOAA calculator. **Expiry assertions (inject `FakeClock`):** `isExpired()` returns `true` when clock set to epoch year 2030.5; returns `false` when 2027.0; returns `true` when 2024.9 (pre-validity). **Supplementary vectors (optional, non-blocking):** Additional vectors (e.g., lat=0°, lon=0°, epoch=2025.0; a southern-hemisphere vector) may be added as supplementary cases after the primary pinned assertion to widen coverage, but the primary NOAA-pinned vector must always be present and must be the first assertion. |
| `MagneticFieldModelProviderTest` | `MagneticFieldModelProvider` | `activeModel()` returns `Wmm2025Model` when `wmm.isExpired() == false`; returns `AndroidGeoFieldModel` when `wmm.isExpired() == true`; returns `AndroidGeoFieldModel` when `wmm == null`. `getLastResult()` returns null before first `evaluate()` call; returns the cached `WmmResult` after. |
| `BearingCaptureUseCaseTest` | `BearingCaptureUseCase` | `interference_flag = true` when `InterferenceState.MODERATE`; `interference_flag = true` when `InterferenceState.WARNING`; `interference_flag = false` when `InterferenceState.CLEAR` even if `OverallConfidence.POOR` (AT-E-10). `north_type` assertion: `execute()` throws `IllegalStateException` when `snapshot.northType == GRID`. `notes` coercion: empty string input → null in record. `id` is a valid UUID format. **`tapTimestampMs` assertion (PM-T-01):** `BearingRecord.captured_at` equals `snapshot.tapTimestampMs` — NOT `clock.nowMs()` at execute time. Verify by setting `FakeClock` to T=1000 at snap time and T=15000 at execute time; assert `captured_at == 1000`. **Note (TE-T-05-NEW):** Concurrent double-tap protection is tested in `CompassViewModelTest` case (c) — `captureButtonEnabled` is owned by the ViewModel, not the use case. |
| `LocationRepositoryTest` | `LocationRepository` (with Robolectric `ShadowLocationManager`) | `isLocationCacheValid()` returns `true` when cache age = 30 days exactly; `false` when age = 30 days + 1 ms (AT-G-06, AT-G-07). Cache age floor division: `floor(elapsed / 86_400_000L)` — verified with `FakeClock`. `LocationState.Unavailable` emitted when no fix, no cache, no manual. |
| `ClockEpochYearTest` | `Clock.toEpochYears()` extension | 2025-01-01 UTC → 2025.0; 2025-07-02 UTC → ~2025.5; leap year 2028-02-29 is handled correctly. |
| `CompassViewModelTest` | `CompassViewModel` (TE-T-04) | Four mandatory cases — all using injected `FakeMagneticFieldModel`, `FakeClock`, and `TestCoroutineDispatcher`: (a) **200 ms heading budget:** inject `FakeSensorFusion` emitting a magnetic heading; set `northType = TRUE`; inject `FakeMagneticFieldModel` returning declination = 8.93°; assert that `CompassUiState.heading` reflects the updated true heading within one coroutine dispatch cycle (≤ one 20 Hz sensor frame = 50 ms, well within 200 ms). (b) **60 s WMM expiry debounce:** call `checkWmmExpiry()` twice within 59 999 ms (`FakeClock.advance(59_999L)`); assert that `isExpired()` on the model is called only once. Advance clock to 60 001 ms past the first check; call `checkWmmExpiry()` again; assert `isExpired()` is called a second time. (c) **`captureButtonEnabled` BR-CAP-08:** call `captureBearing(snapshot)` with a slow `FakeBearingCaptureUseCase` that suspends; assert `captureButtonEnabled == false` during the suspend; assert `captureButtonEnabled == true` after completion. (d) **Extreme latitude confidence cap:** inject `FakeMagneticFieldModel` returning inclination = 80.0°; trigger a WMM evaluation; assert `extremeLatitudeAdvisory == true` and `CompassUiState.confidence == MODERATE` even when all Phase 1 confidence dimensions are Good. |

**AT-B, AT-C, AT-D, AT-F unit-level coverage (TE-T-03):**

| Test Class | FSPEC AT | Key Assertions |
|-----------|----------|----------------|
| `WmmDeclinationAccuracyTest` | AT-B | Unit test using `FakeMagneticFieldModel` returning the NOAA-pinned vector (declination=8.93°, inclination=66.0°, totalField=52300 nT). Inject into `CompassViewModel` with `northType = TRUE`. Assert `CompassUiState.declination_deg == 8.93 ± 0.1`. Verifies the ViewModel correctly propagates the model's declination value to UI state. **Note (TE-T-03-NEW):** This test uses `FakeMagneticFieldModel` to verify ViewModel propagation of declination (AT-B-01 / AT-B-02). Accuracy of WMM2025 math against the NOAA reference is covered by `Wmm2025ModelTest` (AT-B-03). Both tests together satisfy the full AT-B acceptance criterion. |
| `LocationCacheAgeLabelTest` | AT-C | Unit test using `FakeClock` set to exactly `15 × 86_400_000 ms` after the cache timestamp (15 full days). Assert that the cache age label string produced by `LocationRepository` or the ViewModel equals `R.string.location_cache_age_label` formatted with `15` (whole-day `floor` division: `floor(elapsed_ms / 86_400_000L)`). `FakeClock` eliminates DST and midnight-boundary flakiness — no ±1 day tolerance is permitted. |
| `NoGpsDialogTest` | AT-D | Robolectric test (or Espresso with `ActivityScenario`): configure `FakeLocationRepository` to emit `LocationState.Unavailable` and return no cache. Tap the True North toggle. Assert that the manual coordinate entry dialog appears (dialog text contains "Enter coordinates for True North or use Magnetic North only"). Assert that mode remains `MAGNETIC` until coordinates are entered and confirmed. |
| `InterferenceBaselineUpgradeTest` | AT-F | Unit test — inject `FakeMagneticFieldModel` returning WMM values (e.g., `totalField=52300 nT`, `inclination=66.0°`); inject a second `FakeMagneticFieldModel` configured to return Android `GeomagneticField`-equivalent values at the same location (e.g., `totalField=49000 nT` — a value that differs by >5%); hold sensor values constant near a realistic local interference scenario (sensor field = 55000 nT, sensor inclination = 70°). Assert that `InterferenceState` differs between the two model configurations. This verifies that the interference baseline source (WMM2025 vs. GeomagneticField) materially affects the interference classification as required by AT-F-01. |

### 10.2 Integration Tests (Android Instrumented)

| Test Class | Target | Key Assertions |
|-----------|--------|----------------|
| `LuopanDatabaseMigrationTest` | `Migration(1, 2)` via `MigrationTestHelper` | Creates a v1 database with a `calibration_records` row, runs `MIGRATION_1_2`, verifies `bearing_records` table exists with correct schema (all 15 columns, correct types and nullability). Verifies `calibration_records` is untouched. Uses `MigrationTestHelper(instrumentation, LuopanDatabase::class.java.canonicalName)` — requires `exportSchema = true`. |
| `BearingEncryptionTest` | SQLCipher encryption (AT-PERSIST-01) | After inserting one `BearingRecord`, opens `targetContext.getDatabasePath("bearing_records.db")` as raw bytes; asserts `bytes.slice(0..15) != "SQLite format 3".toByteArray()` (encrypted file does not expose plaintext SQLite header). **The canonical database filename is `bearing_records.db`. All tests must use this name** (see §4.5 normative note; TE-T-02 resolution). |
| `BearingForceStopTest` | Force-stop resilience (AT-PERSIST-02) | Uses `ProcessPhoenix` (already in androidTest dependencies) to kill and restart the process mid-insert. On restart: row count is either pre-kill or pre-kill+1; no record with any required field null exists. |

### 10.3 UI Tests (Espresso)

| Test Class | Scenario | Key Steps |
|-----------|---------|-----------|
| `TrueNorthToggleTest` | AT-A (Scenario A) | Injects `FakeMagneticFieldModel` with fixed declination; taps toggle; asserts heading text changes by declination within IdlingResource timeout; asserts label = "True N". |
| `BearingCaptureFlowTest` | AT-E (Scenario E) | Taps capture button with mocked `High` confidence; enters name; taps Save; asserts toast with name; asserts Room DB row count = 1. Verifies `interference_flag = false` for CLEAR state. |
| `InterferenceWarningCaptureTest` | AT-E-04/05 | Injects `MODERATE` interference state; taps capture; asserts warning dialog appears; taps "Save with warning"; asserts `interference_flag = true` in DB. |
| `PermissionDeniedCaptureTest` | AT-G-09 (Scenario G) | Revokes location permission via `GrantPermissionRule`; taps True North toggle; asserts manual coordinate dialog; enters coordinates; dismisses; taps capture; saves; asserts `lat = null`, `lon = null` in record; GPS toggle absent from capture dialog. |
| `GridNorthAbsenceTest` | AT-G-08 | Asserts no view with text containing "Grid" or "GRID" exists on main screen or capture dialog. |
| `LocationPermissionRationaleTest` | BR-LOC-04 (TE-T-10) | Uses `ActivityScenario` with a custom permission handler that makes `shouldShowRequestPermissionRationale(ACCESS_FINE_LOCATION)` return `true` (simulating a prior denial where the OS has not yet set "Don't ask again"). Taps the True North toggle. Asserts that the in-app rationale dialog appears **before** the system permission dialog: the rationale dialog text "Location is used to compute magnetic declination for True North. Your coordinates are stored on-device only and are never shared." is visible. Taps "Continue"; asserts the system permission dialog is then presented (or that `ActivityResultLauncher` was invoked). Taps "Not now"; asserts the rationale dialog is dismissed and the system permission dialog is NOT shown, and the toggle remains on Magnetic N. |

### 10.4 Macrobenchmark

| Benchmark | Startup Mode | Measurement Window | Target |
|-----------|------------|-------------------|--------|
| `CompassColdStartBenchmark` | `StartupMode.COLD` | `Application.onCreate()` to first `CompassUiState` emission with `headingState != STABILIZING` | Median ≤ 5000 ms over ≥5 iterations |
| `CompassWarmStartBenchmark` | `StartupMode.WARM` | Process resume to first non-STABILIZING heading frame | Median ≤ 3000 ms over ≥5 iterations |

Macrobenchmarks live in the `:macrobenchmark` Gradle module (to be created in Phase 2 build setup). They use `androidx.benchmark.macro` and target the `release` build variant.

---

*End of TSPEC-luopan-p2-true-north-capture v0.2-draft*
