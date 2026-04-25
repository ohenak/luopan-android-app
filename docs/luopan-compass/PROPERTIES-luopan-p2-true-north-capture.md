# PROPERTIES: luopan-p2-true-north-capture
**Version:** 0.2
**Date:** 2026-04-24
**Revised:** 2026-04-24 (v0.2 — addressed SE cross-review findings SE-P-01 through SE-P-07 and PM cross-review findings PM-P-01, PM-P-02, PM-P-04, PM-P-05)
**Status:** Draft
**Author:** te-author
**Source documents:**
- REQ: `docs/luopan-compass/REQ-luopan-p2-true-north-capture.md`
- FSPEC: `docs/luopan-compass/FSPEC-luopan-p2-true-north-capture.md`
- TSPEC: `docs/luopan-compass/TSPEC-luopan-p2-true-north-capture.md`
- PLAN: `docs/luopan-compass/PLAN-luopan-p2-true-north-capture.md`

---

## Overview

This document specifies testable properties (invariants) for Phase 2 of the Luopan compass app: True North, Magnetic Declination, and Bearing Capture. Each property is a precise, falsifiable assertion about system behaviour that must hold under all stated conditions.

**Scope:** Phase 2 adds WMM2025-based true north correction, a north type toggle (TRUE/MAGNETIC), bearing capture with full metadata, encrypted persistence for bearing records, GPS/location handling with privacy consent, and an upgrade to Phase 1 interference detection baselines.

**Test level key:**
- **JVM** — plain JUnit4/5, no Android framework, runs on host
- **Instrumented** — JUnit4 with AndroidJUnit4 runner, requires emulator/device
- **Espresso** — UI test with ActivityScenarioRule, requires emulator/device
- **Macrobenchmark** — Jetpack Macrobenchmark library, requires physical device or emulator

**Type key:**
- **Unit** — single class or function in isolation
- **Integration** — two or more collaborating classes
- **E2E** — full application stack through UI

**API notes specific to Phase 2:**
- `WmmModel.declination(lat, lon, altM, epochYear)` returns `Float` in degrees (+E, −W)
- `WmmModel.isExpired()` returns `Boolean`; expired when model epoch > 2030.0
- `WmmModel.getModelId()` returns `String` (e.g., `"WMM2025"`)
- `NorthType` enum: `TRUE`, `MAGNETIC` only in Phase 2; `GRID` never written
- `BearingRecord` is a Room `@Entity` with 15 fields (see PROP-SCHEMA-01)
- `LocationCache` stores `CachedLocation(lat, lon, altM, savedAtMs)` in `EncryptedSharedPreferences`
- Cache age: `floor((nowMs - savedAtMs) / 86_400_000L)` days via injected `Clock`
- `InterferenceDetector` in Phase 2 delegates `expectedField_uT` and `expectedInclination_deg` to `MagneticFieldModel` (WMM2025) when location is available, replacing Phase 1's `GeomagneticField`-based EMA

---

## Domain: PROP-NORTH — True North Correction

### Summary table

| ID | Description | Level | Test Class | REQ | FSPEC | Priority |
|----|-------------|-------|------------|-----|-------|----------|
| PROP-NORTH-01 | Displayed heading = magnetic heading + WMM2025 declination when northType=TRUE and GPS available | Unit | `CompassViewModelTest`, `WmmDeclinationAccuracyTest` | REQ-NORTH-01, REQ-NORTH-04 | AT-A | P0 |
| PROP-NORTH-02 | Displayed heading = magnetic heading (no declination) when northType=MAGNETIC | Unit | `CompassViewModelTest` | REQ-NORTH-04 | AT-A | P0 |
| PROP-NORTH-03 | WMM2025 declination within ±0.1° of NOAA reference for pinned test vector | Unit | `Wmm2025ModelTest` | REQ-NORTH-01 | Scenario B, AT-B | P0 |
| PROP-NORTH-04 | WMM2025 expired → system falls back to AndroidGeoFieldModel | Unit | `Wmm2025ModelTest`, `MagneticFieldModelProviderTest` | REQ-NORTH-02 | AT-G-05 | P0 |
| PROP-NORTH-05 | Heading label shows "True N" / "Magnetic N"; "Grid N" never appears | E2E | `TrueNorthToggleTest`, `GridNorthAbsenceTest` | REQ-NORTH-04 | AT-A, AT-G-08 | P0 |
| PROP-NORTH-06 | Heading label switches within 250 ms of toggle tap | E2E | `TrueNorthToggleTest` | REQ-NORTH-04, REQ-DECL-01 | AT-A timing | P0 |

---

### PROP-NORTH-01: Heading = magnetic heading + declination when TRUE

**Type:** Unit
**Test Level:** JVM
**Source:** REQ-NORTH-01, REQ-NORTH-04 | FSPEC AT-A (Scenario A, acceptance criteria bullet 1)

**Property:** When `northType = NorthType.TRUE` and a GPS location is available, the heading value delivered to the display layer equals `magneticHeading_deg + wmm2025Declination_deg`, normalized to `[0, 360)`, to within ±0.5°.

**Given:** `WmmModel` loaded with WMM2025 coefficients; a fixed `Location(lat=40.0°N, lon=−105.0°W, altM=0.0)` injected (matching the canonical NOAA test vector from TSPEC §10.1); `magneticHeading_deg = 45.0`
**When:** `TrueNorthTransformer.apply(magneticHeading_deg=45.0, northType=TRUE, location=fixedLocation)` is called
**Then:** `abs(result - (45.0 + 8.93)) <= 0.5`, normalized to `[0, 360)`

---

### PROP-NORTH-02: Heading = magnetic heading unchanged when MAGNETIC

**Type:** Unit
**Test Level:** JVM
**Source:** REQ-NORTH-04 | FSPEC AT-A (Scenario A, switching back restores original value)

**Property:** When `northType = NorthType.MAGNETIC`, no declination is applied and the raw magnetic heading is returned exactly.

**Given:** Any `WmmModel` state; `magneticHeading_deg = 182.7`
**When:** `TrueNorthTransformer.apply(magneticHeading_deg=182.7, northType=MAGNETIC, location=anyLocation)` is called
**Then:** `result == 182.7` (identity, no modification)

---

### PROP-NORTH-03: WMM2025 declination within ±0.1° of NOAA reference

**Type:** Unit
**Test Level:** JVM
**Source:** REQ-NORTH-01 | FSPEC Scenario B ("WMM2025 bundled model computes declination correctly — within ±0.1° of NOAA reference") | TSPEC §10.1 `Wmm2025ModelTest` (canonical pinned vector)

**Property:** `WmmModel.declination()` for the pinned NOAA test vector produces a value within ±0.1° of the published reference.

**Given:** `lat=40.0`, `lon=−105.0`, `altM=0.0`, `epochYear=2025.5`; NOAA reference value = `+8.93°E`
*(Note: `altM=0.0` matches the TSPEC §10.1 `Wmm2025ModelTest` canonical vector exactly. Altitude 0.0 m (sea level / WGS-84 ellipsoid) is the normative test altitude for this pinned vector.)*
**When:** `WmmModel.declination(lat=40.0f, lon=-105.0f, altM=0.0f, epochYear=2025.5f)` is called
**Then:** `abs(result - 8.93f) <= 0.1f`

---

### PROP-NORTH-04: WMM2025 expired → falls back to AndroidGeoFieldModel

**Type:** Unit
**Test Level:** JVM
**Source:** REQ-NORTH-02 | FSPEC (source label switches to "Android model — may be less accurate")

**Property:** When `WmmModel.isExpired()` returns `true`, the `DeclinationProvider` returns a declination computed from `AndroidGeoFieldModel` (i.e., `GeomagneticField`), not from the WMM2025 coefficients.

**Given:** A `WmmModel` stub returning `isExpired() = true`; `DeclinationProvider(wmmModel, androidGeoFieldModel)` constructed
**When:** `DeclinationProvider.getDeclination(location, epochMs)` is called
**Then:** The call is delegated to `androidGeoFieldModel` (verified by mock interaction); `WmmModel.declination()` is NOT called

---

### PROP-NORTH-05: Label shows "True N" or "Magnetic N"; "Grid N" never appears

**Type:** E2E
**Test Level:** Espresso
**Source:** REQ-NORTH-04, REQ-DECL-01 | FSPEC AT-A, §REQ §5.2 (Grid N deferred to Phase 5)

**Property:** The north-reference label in `CompassActivity` is exactly `str_true_north` when `northType=TRUE` and exactly `str_magnetic_north` when `northType=MAGNETIC`. A view with text matching `str_grid_north` is never present in the view hierarchy in Phase 2.

**Given:** `CompassActivity` running in Phase 2 build
**When:** The north type is set to `TRUE` then `MAGNETIC` via toggle
**Then:**
- When `TRUE`: `onView(withId(R.id.north_reference_label)).check(matches(withText(R.string.str_true_north)))`
- When `MAGNETIC`: `onView(withId(R.id.north_reference_label)).check(matches(withText(R.string.str_magnetic_north)))`
- At all times: `onView(withText(R.string.str_grid_north)).check(doesNotExist())`

---

### PROP-NORTH-06: Heading label switches within 250 ms of toggle tap

**Type:** E2E
**Test Level:** Espresso
**Source:** REQ-NORTH-04, REQ-DECL-01 | FSPEC AT-A ("Heading value changes by the local declination within 200 ms")

**Property:** After the user taps the north type toggle, the north-reference label text updates within 250 ms (the AT-A timing constraint, with 50 ms test execution tolerance).

**Given:** `CompassActivity` active; heading updating; north type is `MAGNETIC`
**When:** The north-type toggle is tapped
**Then:** Within 250 ms, `onView(withId(R.id.north_reference_label)).check(matches(withText(R.string.str_true_north)))` passes (measured via `SystemClock.uptimeMillis()` delta)

---

## Domain: PROP-DECL — Declination Management

### Summary table

| ID | Description | Level | Test Class | REQ | FSPEC | Priority |
|----|-------------|-------|------------|-----|-------|----------|
| PROP-DECL-01 | North type toggle is binary (TRUE/MAGNETIC); GRID state unreachable | Unit | `TrueNorthToggleTest`, `GridNorthAbsenceTest` | REQ-DECL-01 | AT-A, AT-G-08 | P0 |
| PROP-DECL-02 | Declination info panel shows required fields including coordinates type, cache age, and inactive-True-North note | E2E | `BearingCaptureFlowTest` | REQ-DECL-02 | Scenario B, FSPEC §2.3 | P1 |
| PROP-DECL-03 | No network request during WMM declination computation | Integration | `WmmDeclinationAccuracyTest` | REQ-NORTH-03, REQ-NFR-05 | Scenario B, BR-01 | P0 |

---

### PROP-DECL-01: North type toggle is binary; GRID unreachable

**Type:** Unit
**Test Level:** JVM
**Source:** REQ-DECL-01 | FSPEC §5.2 deferral note ("Grid N deferred to Phase 5")

**Property:** `NorthTypeToggle.toggle(currentType)` returns only `NorthType.TRUE` or `NorthType.MAGNETIC`. If the current type is `TRUE`, it returns `MAGNETIC`; if `MAGNETIC`, returns `TRUE`. `NorthType.GRID` is never a return value.

**Given:** Any `NorthType` value in `{TRUE, MAGNETIC}`
**When:** `NorthTypeToggle.toggle(current)` is called twice (once per value)
**Then:** Results are `{MAGNETIC, TRUE}` respectively; `NorthType.GRID` is never produced; `NorthType.entries` exhausted without finding `GRID` as an output

---

### PROP-DECL-02: Declination info panel shows all required fields

**Type:** E2E
**Test Level:** Espresso
**Source:** REQ-DECL-02 | FSPEC §2.3 (panel contents), Scenario B

**Property:** When the declination info panel is open, it simultaneously shows all of the following fields as specified in FSPEC §2.3:
1. Declination value formatted as `±X.XX°E` or `±X.XX°W`
2. Model identifier `"WMM2025"`
3. Coordinates masked to 2 decimal places (e.g., `"40.00°N, 105.00°W"`)
4. A last-updated date string
5. Coordinates type label: `"GPS fix"`, `"Cached location"`, or `"Manual entry"` — identifying the active location source
6. Cache age display (for cached locations): `"N days ago"` where N = `floor(elapsed_ms / 86_400_000L)`
7. When Magnetic N is active: a note reading "True North is currently off. This declination would be applied if True North is enabled." (localized)

**Sub-case A — GPS fix active, `northType=TRUE`:**
**Given:** `CompassActivity` with a fresh GPS fix; `northType=TRUE`; declination info panel opened
**When:** The panel is displayed
**Then:**
- `onView(withId(R.id.decl_value))` shows a string matching regex `[-+]?\d+\.\d{1,2}°[EW]`
- `onView(withId(R.id.decl_model_id))` shows `"WMM2025"`
- `onView(withId(R.id.decl_coordinates))` shows coordinates truncated to 2 decimal places
- `onView(withId(R.id.decl_last_updated))` shows a non-empty date string
- `onView(withId(R.id.decl_coordinates_type))` shows `"GPS fix"`
- `onView(withId(R.id.decl_cache_age))` is not visible (GPS fix — no cache age shown)
- `onView(withId(R.id.decl_inactive_true_north_note))` is not visible (`northType=TRUE`)

**Sub-case B — Cached location, `northType=TRUE`:**
**Given:** `CompassActivity` with a cached location 4 days old (injected via `FakeClock`); `northType=TRUE`; declination info panel opened
**When:** The panel is displayed
**Then:**
- `onView(withId(R.id.decl_coordinates_type))` shows `"Cached location"`
- `onView(withId(R.id.decl_cache_age))` shows `"4 days ago"`

**Sub-case C — Magnetic N active:**
**Given:** `CompassActivity` with GPS fix; `northType=MAGNETIC`; declination info panel opened
**When:** The panel is displayed
**Then:**
- `onView(withId(R.id.decl_inactive_true_north_note))` is visible and its text contains the localized inactive-True-North string (e.g., "True North is currently off")

---

### PROP-DECL-03: No network request during WMM declination computation

**Type:** Integration
**Test Level:** JVM
**Source:** REQ-NORTH-03 | FSPEC Scenario B ("No network request is made")

**Property:** `DeclinationProvider.getDeclination()` does not perform any I/O, DNS lookup, or socket operation. The computation is entirely in-memory using bundled WMM2025 coefficients.

**Given:** `DeclinationProvider` configured with a `WmmModel` loaded from bundled assets; network disabled (MockWebServer with 0 enqueued responses, or `OkHttpClient` replaced with a failing interceptor)
**When:** `DeclinationProvider.getDeclination(location, epochMs)` is called
**Then:** No network call is intercepted; the method returns a finite `Float` value without throwing; latency < 10 ms

---

## Domain: PROP-LOCATION — GPS and Location Handling

### Summary table

| ID | Description | Level | Test Class | REQ | FSPEC | Priority |
|----|-------------|-------|------------|-----|-------|----------|
| PROP-LOCATION-01 | Location priority chain: session GPS → cache (≤30d) → manual → None | Integration | `LocationRepositoryTest` | REQ-NORTH-03 | Scenarios C, D | P0 |
| PROP-LOCATION-02 | GPS location request fires on Activity.onStart (not onResume) | Integration | `LocationRepositoryTest` | REQ-NORTH-03 | Scenario C | P0 |
| PROP-LOCATION-03 | Cached location older than 30 days is treated as expired (1 ms boundary) | Unit | `LocationRepositoryTest` | REQ-NORTH-03 | Scenario C, AT-G-06, AT-G-07 | P0 |
| PROP-LOCATION-04 | ACCESS_FINE_LOCATION denied → BearingRecord lat/lon/alt_m = null, no crash | Integration | `PermissionDeniedCaptureTest` | REQ-NORTH-03, REQ-CAPTURE-06 | Scenario D, AT-G-09 | P0 |
| PROP-LOCATION-05 | shouldShowRationale=true triggers rationale dialog before permission request | E2E | `LocationPermissionRationaleTest` | REQ-NORTH-03 | BR-LOC-04 | P0 |
| PROP-LOCATION-06 | Cache age label = floor(elapsed_ms / 86_400_000L) via injected Clock | Unit | `LocationCacheAgeLabelTest` | REQ-NORTH-03 | Scenario C, AT-C | P1 |

---

### PROP-LOCATION-01: Location resolution follows priority chain

**Type:** Integration
**Test Level:** JVM
**Source:** REQ-NORTH-03 | FSPEC Scenarios C, D

**Property:** `LocationResolver.resolve()` returns the highest-priority available source in the chain: (1) live session GPS fix if present, (2) cached location not older than 30 days if no live fix, (3) manually entered coordinates if no valid cache, (4) `null` (None) if no manual entry provided.

**Given:** Four test scenarios with controlled fakes:
- Scenario 1: live fix available
- Scenario 2: no live fix; cached location saved 15 days ago
- Scenario 3: no live fix; no valid cache; manual entry `(35.0, 139.0)`
- Scenario 4: no live fix; no valid cache; no manual entry
**When:** `LocationResolver.resolve()` is called in each scenario
**Then:**
- Scenario 1: returns the live GPS fix (not the cached one)
- Scenario 2: returns the cached location (age = 15 days)
- Scenario 3: returns `(35.0, 139.0)` from manual entry
- Scenario 4: returns `null`

---

### PROP-LOCATION-02: GPS location request fires on Activity.onStart

**Type:** Integration
**Test Level:** Instrumented
**Source:** REQ-NORTH-03 | FSPEC Scenario C (cache-based True North without live GPS)

**Property:** `CompassActivity.onStart()` triggers a GPS location update request (via `FusedLocationProviderClient.requestLocationUpdates()` or `LocationManager`). The request is NOT deferred to `onResume()`.

**Given:** `CompassActivity` with a mocked `LocationProvider` spy
**When:** `ActivityScenario.launch(CompassActivity::class.java)`; lifecycle proceeds to `onStart()`
**Then:** `locationProvider.startLocationUpdates()` has been called by the time `onStart()` returns; it has NOT been called before `onStart()` (not in `onCreate()`)

---

### PROP-LOCATION-03: Cached location boundary — day 30 valid, 30d+1ms expired

**Type:** Unit
**Test Level:** JVM
**Source:** REQ-NORTH-03 | FSPEC Scenario C ("cached location ≤30 days"), AT-G-06, AT-G-07 | TSPEC §10.1 `LocationRepositoryTest`

**Property:** `LocationRepository.isLocationCacheValid()` returns `true` when cache age is exactly 30 days (`30 * 86_400_000L` ms) and `false` when age is 30 days + 1 millisecond (`30 * 86_400_000L + 1L` ms). The boundary check is strict: `elapsed_ms < 30 * 86_400_000L` (strictly less than), so a 1 ms overshoot triggers expiry.

**Given:** `cachedAtMs = T`; `FakeClock` injected into `LocationRepository`
**When:**
- Sub-case A: `FakeClock.set(T + 30 * 86_400_000L)` — exactly 30 days
- Sub-case B: `FakeClock.set(T + 30 * 86_400_000L + 1L)` — 30 days + 1 ms (canonical 1 ms boundary per TSPEC §10.1)
- Sub-case C: `FakeClock.set(T + 30 * 86_400_000L + 1_000L)` — 30 days + 1 s (additional confirming case)
**Then:**
- Sub-case A: `isLocationCacheValid()` returns `true` (AT-G-07: exactly 30 days is valid)
- Sub-case B: `isLocationCacheValid()` returns `false` (AT-G-06: 1 ms past 30 days is expired — reveals off-by-one errors in `<` vs `<=`)
- Sub-case C: `isLocationCacheValid()` returns `false` (30d+1s is also expired)

---

### PROP-LOCATION-04: ACCESS_FINE_LOCATION denied → null coordinates, no crash

**Type:** Integration
**Test Level:** Instrumented
**Source:** REQ-NORTH-03, REQ-CAPTURE-06 | FSPEC Scenario D (permission denial path)

**Property:** When `ACCESS_FINE_LOCATION` is permanently denied (or never granted), a captured `BearingRecord` has `lat = null`, `lon = null`, `alt_m = null`. The capture completes without a crash, `NullPointerException`, or `SecurityException`.

**Given:** Emulator with `ACCESS_FINE_LOCATION` permission revoked via `UiAutomation.executeShellCommand("pm revoke")`
**When:** User completes the capture flow (name entry + confirm)
**Then:** `BearingRepository.getAll().last()` has `lat == null && lon == null && alt_m == null`; no exception is thrown; `Activity` is not in `DESTROYED` state

---

### PROP-LOCATION-05: shouldShowRationale=true triggers rationale dialog

**Type:** E2E
**Test Level:** Espresso
**Source:** REQ-NORTH-03 | FSPEC BR-LOC-04

**Property:** When `ActivityCompat.shouldShowRequestPermissionRationale()` returns `true` for `ACCESS_FINE_LOCATION`, the app displays a rationale dialog BEFORE calling `ActivityCompat.requestPermissions()`.

**Given:** `CompassActivity` configured with a test double where `shouldShowRequestPermissionRationale` returns `true`; `ACCESS_FINE_LOCATION` not yet granted
**When:** The activity attempts to acquire location
**Then:** A dialog with a rationale string for location usage is visible; `requestPermissions()` has not yet been called (verified by spy/mock ordering)

---

### PROP-LOCATION-06: Cache age label = floor(elapsed_ms / 86_400_000L)

**Type:** Unit
**Test Level:** JVM
**Source:** REQ-NORTH-03 | FSPEC Scenario C ("UI shows 'Using last known location (15 days ago)'")

**Property:** `LocationCacheAgeFormatter.format(cachedAtMs, clock)` returns `floor((clock.nowMs() - cachedAtMs) / 86_400_000L)` as the numeric day count, computed via the injected `Clock` (not `System.currentTimeMillis()`).

**Given:** `cachedAtMs = T`; `clock.nowMs() = T + 15 * 86_400_000L + 3_600_000L` (15 days + 1 hour)
**When:** `LocationCacheAgeFormatter.format(cachedAtMs=T, clock=fakeClock)` is called
**Then:** Returns `15L` (floor division; fractional day ignored); no wall-clock call is made

---

## Domain: PROP-CAPTURE — Bearing Capture

### Summary table

| ID | Description | Level | Test Class | REQ | FSPEC | Priority |
|----|-------------|-------|------------|-----|-------|----------|
| PROP-CAPTURE-01 | BearingRecord.captured_at = tap timestamp, not save timestamp | Unit | `BearingCaptureUseCaseTest` | REQ-CAPTURE-01 | Scenario E | P0 |
| PROP-CAPTURE-02 | interference_flag = true iff InterferenceState ∈ {MODERATE, WARNING} at capture | Unit | `BearingCaptureUseCaseTest` | REQ-CAPTURE-01 | Scenario E, AT-E-10 | P0 |
| PROP-CAPTURE-03 | calibration_version = MagneticFieldModel.getModelId() at capture | Unit | `BearingCaptureUseCaseTest` | REQ-CAPTURE-01 | Scenario E | P0 |
| PROP-CAPTURE-04 | north_type is TRUE or MAGNETIC only; GRID never written | Unit | `BearingCaptureUseCaseTest` | REQ-CAPTURE-01 | Scenario E | P0 |
| PROP-CAPTURE-05 | BearingRecord.bearing_deg is Float type | Unit | `BearingCaptureUseCaseTest` | REQ-CAPTURE-01 | Scenario E | P0 |
| PROP-CAPTURE-06 | Empty notes string coerced to null before persistence | Unit | `BearingCaptureUseCaseTest` | REQ-CAPTURE-01 | Scenario E | P1 |
| PROP-CAPTURE-07 | Capture flow ≤3 taps for subsequent captures (first = +1 for privacy dialog) | E2E | `BearingCaptureFlowTest` | REQ-CAPTURE-02 | Scenario E | P0 |
| PROP-CAPTURE-08 | Rapid double-tap produces exactly one BearingRecord | Integration | `CompassViewModelTest` | REQ-CAPTURE-02 | BR-CAP-08 | P0 |
| PROP-CAPTURE-09 | Capture button disabled after tap until save completes or fails | E2E | `BearingCaptureFlowTest`, `CompassViewModelTest` | REQ-CAPTURE-02 | BR-CAP-08 | P0 |
| PROP-CAPTURE-10 | GPS-include toggle defaults to ON on first capture dialog open | E2E | `BearingCaptureFlowTest` | REQ-CAPTURE-06 | FSPEC §2.5 step 4 | P1 |

---

### PROP-CAPTURE-01: BearingRecord.captured_at = tap timestamp (not save time)

**Type:** Unit
**Test Level:** JVM
**Source:** REQ-CAPTURE-01 | FSPEC Scenario E (record saved with timestamp)

**Property:** `BearingCaptureUseCase.capture(tapTimestampMs, headingState, ...)` sets `BearingRecord.captured_at = tapTimestampMs`. If the save operation takes N ms, `captured_at` is not advanced by N ms.

**Given:** `fakeClock.nowMs() = 1_000_000L` at tap time; save completes 200 ms later (`fakeClock.nowMs() = 1_000_200L` at commit)
**When:** `BearingCaptureUseCase.capture(tapTimestampMs=1_000_000L, ...)` is called
**Then:** The `BearingRecord` written to the repository has `captured_at == 1_000_000L` (not `1_000_200L`)

---

### PROP-CAPTURE-02: interference_flag = MODERATE or WARNING at capture; POOR alone does not set it

**Type:** Unit
**Test Level:** JVM
**Source:** REQ-CAPTURE-01 | FSPEC Scenario E ("Capturing with confidence 'Poor': bearing saves with interference_flag=true")

**Property:** `BearingCaptureUseCase` sets `interference_flag = true` if and only if `interferenceState ∈ {MODERATE, WARNING}` at the moment of capture. `OverallConfidence.POOR` with `InterferenceState.CLEAR` does NOT set the flag.

**Given:** Three test cases:
- Case A: `interferenceState=MODERATE, confidence=HIGH`
- Case B: `interferenceState=WARNING, confidence=POOR`
- Case C: `interferenceState=CLEAR, confidence=POOR`
**When:** `BearingCaptureUseCase.capture(...)` is called in each case
**Then:**
- Case A: `record.interference_flag == true`
- Case B: `record.interference_flag == true`
- Case C: `record.interference_flag == false`

---

### PROP-CAPTURE-03: calibration_version = MagneticFieldModel.getModelId() at capture

**Type:** Unit
**Test Level:** JVM
**Source:** REQ-CAPTURE-01 | FSPEC Scenario E (record contains `calibration_version`)

**Property:** `BearingRecord.calibration_version` is set to `MagneticFieldModel.getModelId()` at the time `BearingCaptureUseCase.capture()` is called, not lazily at a later point.

**Given:** `MagneticFieldModel.getModelId()` returning `"WMM2025"` at capture time; a later call would return `"AndroidGeoField"`
**When:** `BearingCaptureUseCase.capture(...)` is called
**Then:** `record.calibration_version == "WMM2025"` (snapshot at capture time)

---

### PROP-CAPTURE-04: north_type is TRUE or MAGNETIC only; GRID never written

**Type:** Unit
**Test Level:** JVM
**Source:** REQ-CAPTURE-01 | FSPEC §5.2 deferral

**Property:** `BearingRecord.north_type` is always the string `"TRUE"` or `"MAGNETIC"`. The string `"GRID"` is never written to any `BearingRecord`, regardless of the input `NorthType` value.

**Given:** The two valid `NorthType` values: `TRUE`, `MAGNETIC`
**When:** `BearingCaptureUseCase.capture(northType=TRUE, ...)` and `capture(northType=MAGNETIC, ...)` are called
**Then:** `record.north_type == "TRUE"` and `"MAGNETIC"` respectively; no production code path produces `north_type == "GRID"`

---

### PROP-CAPTURE-05: BearingRecord.bearing_deg is Float type

**Type:** Unit
**Test Level:** JVM
**Source:** REQ-CAPTURE-01 | FSPEC §6.1 schema

**Property:** The Kotlin entity field `BearingRecord.bearing_deg` has compile-time type `Float` (not `Double`). The Room column type annotation maps it to `REAL`.

**Given:** Kotlin reflection on `BearingRecord::class.memberProperties`
**When:** `BearingRecord::bearing_deg.returnType` is inspected
**Then:** `returnType.classifier == Float::class`

---

### PROP-CAPTURE-06: Empty notes string coerced to null before persistence

**Type:** Unit
**Test Level:** JVM
**Source:** REQ-CAPTURE-01 | FSPEC §6.1 schema (`notes=String?`)

**Property:** `BearingCaptureUseCase` coerces an empty or blank `notes` string to `null` before constructing the `BearingRecord`. A non-empty notes string is preserved verbatim.

**Given:**
- Case A: `notes = ""`
- Case B: `notes = "   "` (whitespace only)
- Case C: `notes = "Test bearing"`
**When:** `BearingCaptureUseCase.capture(notes=..., ...)` is called in each case
**Then:**
- Case A: `record.notes == null`
- Case B: `record.notes == null`
- Case C: `record.notes == "Test bearing"`

---

### PROP-CAPTURE-07: Capture flow ≤3 taps for subsequent captures

**Type:** E2E
**Test Level:** Espresso
**Source:** REQ-CAPTURE-02 | FSPEC Scenario E

**Property:** After the privacy dialog has been accepted (first capture +1 tap), all subsequent captures from button tap to confirmed save require exactly 3 taps or fewer: (1) capture button, (2) [optional name edit — count as 1 if typed], (3) confirm. The app does not introduce extra steps.

**Given:** `CompassActivity` with privacy consent already recorded (`bearing_location_consent_shown = true` in SharedPreferences); a valid heading displayed
**When:** User taps capture → types name (1 tap on confirm) → taps confirm
**Then:** `BearingRepository.getAll()` grows by 1; total user interactions from button press to toast = ≤3

---

### PROP-CAPTURE-08: Rapid double-tap produces exactly one BearingRecord

**Type:** Integration
**Test Level:** Instrumented
**Source:** REQ-CAPTURE-02 | FSPEC BR-CAP-08

**Property:** If the capture button is tapped twice within 100 ms (before the button becomes disabled on the first tap), exactly one `BearingRecord` is created — not zero, not two.

**Given:** `BearingCaptureViewModel` with a real `BearingRepository` backed by in-memory Room database; capture button enabled
**When:** Two `performCapture()` calls are issued within 100 ms via `runBlockingTest` with fake time
**Then:** `BearingRepository.getAll().size == 1` after both calls complete; the idempotent guard is engaged after the first call

---

### PROP-CAPTURE-09: Capture button disabled after tap until save completes or fails

**Type:** E2E
**Test Level:** Espresso
**Source:** REQ-CAPTURE-02 | FSPEC BR-CAP-08 (double-tap prevention)

**Property:** After the user taps the capture button, it transitions to a disabled state before any confirmation dialog is shown, and remains disabled until the save coroutine either succeeds or fails.

**Given:** `CompassActivity` in a valid heading state; capture button enabled; save operation artificially delayed 500 ms
**When:** Capture button is tapped once
**Then:** `onView(withId(R.id.capture_button)).check(matches(not(isEnabled())))` passes immediately after the tap; button re-enables after save completes

---

### PROP-CAPTURE-10: GPS-include toggle defaults to ON on first capture dialog open

**Type:** E2E
**Test Level:** Espresso
**Source:** REQ-CAPTURE-06 | FSPEC §2.5 step 4 ("GPS toggle (on/off, default ON)")

**Property:** On the very first time the capture dialog opens in a given app session, the "Include GPS location" toggle (`R.id.capture_gps_toggle`) is in the checked (ON / `true`) state. A bug that defaults the toggle to OFF would go undetected without this property.

**Given:** `CompassActivity`; `ACCESS_FINE_LOCATION` permission granted; `bearing_location_consent_shown` is absent or `false` (first capture ever); capture button tapped
**When:** The capture dialog (`BearingCaptureDialogFragment`) becomes visible
**Then:** `onView(withId(R.id.capture_gps_toggle)).check(matches(isChecked()))` — the GPS toggle is checked (ON)

---

## Domain: PROP-PERSIST — Persistence

### Summary table

| ID | Description | Level | Test Class | REQ | FSPEC | Priority |
|----|-------------|-------|------------|-----|-------|----------|
| PROP-PERSIST-01 | bearing_records.db SQLite magic bytes absent (encrypted) | Integration | `BearingEncryptionTest` | REQ-CAPTURE-04 | AT-PERSIST-01 | P0 |
| PROP-PERSIST-02 | BearingRecord write is atomic — force-stop yields unchanged or +1 count | Integration | `BearingForceStopTest` | REQ-CAPTURE-04 | AT-PERSIST-02 | P0 |
| PROP-PERSIST-03 | Migration(1,2) adds bearing_records without touching calibration_records | Integration | `LuopanDatabaseMigrationTest` | REQ-CAPTURE-04 | — | P0 |
| PROP-PERSIST-04 | bearing_location_consent_shown set after first dialog acceptance; dialog never shown again | E2E | `BearingCaptureFlowTest` | REQ-CAPTURE-06 | Scenario E | P0 |

---

### PROP-PERSIST-01: bearing_records.db is encrypted (magic bytes absent)

**Type:** Integration
**Test Level:** Instrumented
**Source:** REQ-CAPTURE-04 | FSPEC Scenario E (encrypted local storage)

**Property:** The `bearing_records.db` file stored on-device does not start with the standard SQLite plaintext header bytes (`53 51 4C 69 74 65 20 66 6F 72 6D 61 74 20 33 00`), confirming SQLCipher encryption.

**Given:** `BearingRepository` has saved at least one `BearingRecord`
**When:** The database file at `context.getDatabasePath("bearing_records.db")` is opened as a raw byte stream and bytes `[0..15]` are read
**Then:** `bytes.take(16).toByteArray() != byteArrayOf(0x53,0x51,0x4C,0x69,0x74,0x65,0x20,0x66,0x6F,0x72,0x6D,0x61,0x74,0x20,0x33,0x00)`

---

### PROP-PERSIST-02: BearingRecord write is atomic — force-stop yields unchanged or +1

**Type:** Integration
**Test Level:** Instrumented
**Source:** REQ-CAPTURE-04 | FSPEC AT-PERSIST-02 (force-stop resilience) | TSPEC §10.2 `BearingForceStopTest`

**Property:** If the app process is killed during a `BearingRepository.save()` transaction, on relaunch the database contains either the same number of records as before the save, or one more. It never contains a partial record with null required fields (`bearing_deg`, `captured_at`, `calibration_version`, `north_type`, `confidence`).

**Given:** `BearingRepository` with N records; a save is initiated
**When:** `ProcessPhoenix.triggerRebirth(context)` is invoked via the `BearingForceStopTest` infrastructure (per TSPEC §10.2) to kill and restart the process during the Room `@Transaction` write; app relaunches
**Then:** `allRecords.size ∈ {N, N+1}`; `allRecords.all { it.bearing_deg.isFinite() && it.captured_at > 0 && it.calibration_version.isNotEmpty() }` is `true`

*(Note: The process is killed using `ProcessPhoenix.triggerRebirth(context)` — already available in the androidTest dependency set — or `adb shell am force-stop` from the test harness. `Process.killProcess()` injected mid-transaction from within the test process is NOT used, as it is unreliable without root on CI emulators. `ProcessPhoenix` provides a deterministic, rootless kill-and-restart mechanism that matches the TSPEC §10.2 `BearingForceStopTest` specification.)*

---

### PROP-PERSIST-03: Migration(1,2) adds bearing_records without touching calibration_records

**Type:** Integration
**Test Level:** Instrumented
**Source:** REQ-CAPTURE-04 | FSPEC data persistence spec

**Property:** `LuopanDatabase.MIGRATION_1_2` creates the `bearing_records` table with the correct column schema, and leaves the `calibration_records` table (schema and all data) completely unmodified.

**Given:** A database at schema version 1 containing two `CalibrationRecord` rows
**When:** `RoomDatabase.Builder.addMigrations(MIGRATION_1_2).build()` triggers the migration
**Then:**
- `SELECT name FROM sqlite_master WHERE type='table' AND name='bearing_records'` returns one row
- `calibration_records` query returns the same two rows with identical data
- No column has been dropped or altered in `calibration_records`

---

### PROP-PERSIST-04: Privacy consent set after first dialog acceptance; dialog never reshown

**Type:** E2E
**Test Level:** Espresso
**Source:** REQ-CAPTURE-06 | FSPEC Scenario E (first-capture dialog, location optional and on-device only)

**Property:** After the user accepts the location privacy dialog on first capture, `EncryptedSharedPreferences` key `"bearing_location_consent_shown"` is set to `true`. On subsequent captures in the same session and on app relaunch, the privacy dialog is never shown again.

**Given:** Fresh install with `bearing_location_consent_shown` absent from SharedPreferences
**When:** User completes first capture and accepts the privacy dialog
**Then:**
- `prefs.getBoolean("bearing_location_consent_shown", false) == true`
- Launching a second capture shows no privacy dialog
- After `ActivityScenario.recreate()`, a third capture also shows no privacy dialog

---

## Domain: PROP-INTERFERENCE — Upgraded Interference Detection

### Summary table

| ID | Description | Level | Test Class | REQ | FSPEC | Priority |
|----|-------------|-------|------------|-----|-------|----------|
| PROP-INTERFERENCE-01 | InterferenceDetector uses MagneticFieldModel for expectedField_uT when location available | Unit | `InterferenceBaselineUpgradeTest` | REQ-NORTH-01, REQ-DETECT-01 | Scenario F, AT-F | P0 |
| PROP-INTERFERENCE-02 | InterferenceDetector uses MagneticFieldModel for expectedInclination_deg when location available | Unit | `InterferenceBaselineUpgradeTest` | REQ-NORTH-01, REQ-DETECT-02 | Scenario F, AT-F | P0 |
| PROP-INTERFERENCE-03 | No location → falls back to Phase 1 EMA baseline (no regression) | Unit | `InterferenceBaselineUpgradeTest` | REQ-NORTH-02, REQ-DETECT-01 | Scenario F, AT-F-04 | P0 |

---

### PROP-INTERFERENCE-01: InterferenceDetector uses WMM expectedField_uT when location available

**Type:** Unit
**Test Level:** JVM
**Source:** REQ-NORTH-01 (upgrade to WMM2025 expected values), REQ-DETECT-01 | FSPEC Scenario F | TSPEC §2.1 `MagneticFieldModel`

**Property:** When `MagneticFieldModel` is available (location present), `CompassViewModel` calls `MagneticFieldModel.getExpectedFieldMagnitude(latDeg, lonDeg, altM, epochYears)` to populate `InterferenceMetrics.expectedField_uT`. The `InterferenceDetector` receives the WMM-sourced value via `InterferenceMetrics` — it does NOT use `GeomagneticField` or the rolling EMA baseline for this value when a location is available.

**Given:** `FakeMagneticFieldModel` configured to return `55.0f` µT for all inputs; `FakeGeomagneticField` stub (should NOT be called); `LocationRepository` returning a fixed location
**When:** The sensor pipeline processes a frame with WMM available (the `CompassViewModel` sensor coroutine calls `MagneticFieldModelProvider.evaluate()` and then constructs `InterferenceMetrics`)
**Then:** `InterferenceMetrics.expectedField_uT == 55.0f`; `FakeMagneticFieldModel.getExpectedFieldMagnitude(latDeg, lonDeg, altM, epochYears)` invocation count ≥ 1; `GeomagneticField` invocation count = 0

---

### PROP-INTERFERENCE-02: InterferenceDetector uses WMM expectedInclination_deg when location available

**Type:** Unit
**Test Level:** JVM
**Source:** REQ-NORTH-01 (upgrade), REQ-DETECT-02 | FSPEC Scenario F | TSPEC §2.1 `MagneticFieldModel`

**Property:** When `MagneticFieldModel` is available (location present), `CompassViewModel` calls `MagneticFieldModel.getExpectedInclination(latDeg, lonDeg, altM, epochYears)` to populate `InterferenceMetrics.expectedInclination_deg`. The correct TSPEC §2.1 canonical method name is `getExpectedInclination`, not `getInclination_deg`.

**Given:** `FakeMagneticFieldModel` configured to return `64.5f` degrees inclination for all inputs; `LocationRepository` returning a fixed location
**When:** The sensor pipeline processes a frame with WMM available (the `CompassViewModel` sensor coroutine constructs `InterferenceMetrics`)
**Then:** `InterferenceMetrics.expectedInclination_deg == 64.5f`; `FakeMagneticFieldModel.getExpectedInclination(latDeg, lonDeg, altM, epochYears)` invocation count ≥ 1

---

### PROP-INTERFERENCE-03: No location → Phase 1 EMA fallback (no regression)

**Type:** Unit
**Test Level:** JVM
**Source:** REQ-NORTH-02, REQ-DETECT-01 | FSPEC Scenario F, AT-F-04 (regression guard) | TSPEC §3.8

**Property:** When no location is available and `MagneticFieldModelProvider.getLastResult()` returns `null`, `CompassViewModel` falls back to the Phase 1 rolling sensor EMA (`baselineFieldUt`) to populate `InterferenceMetrics.expectedField_uT`. The `MagneticFieldModel.getExpectedFieldMagnitude()` method is NOT called in the no-location path. No exception is thrown. Existing Phase 1 interference detection behavior is preserved.

The specific numeric fallback values (EMA seed or inclination default) are implementation-defined per Phase 1 behavior (TSPEC §3.8) and are NOT asserted as hardcoded constants in this property — doing so would cause test failures against a valid implementation that uses a different but spec-compliant EMA seed.

**Given:** `FakeMagneticFieldModel` configured with `getLastResult() == null` (WMM not yet evaluated — no location resolved); `LocationRepository` emitting `LocationState.Unavailable`; sensor measurements active
**When:** The `CompassViewModel` sensor coroutine constructs `InterferenceMetrics`
**Then:**
- `FakeMagneticFieldModel.getExpectedFieldMagnitude(...)` is NOT called (invocation count = 0)
- `InterferenceMetrics.expectedField_uT` is set to a positive finite value sourced from the Phase 1 EMA (not from WMM)
- No exception is thrown
- `InterferenceDetector.updateState(metrics, nowNs)` processes normally with the EMA-sourced baseline

---

## Domain: PROP-LATITUDE — Extreme Latitude

### Summary table

| ID | Description | Level | Test Class | REQ | FSPEC | Priority |
|----|-------------|-------|------------|-----|-------|----------|
| PROP-LATITUDE-01 | WMM inclination ≥80° triggers advisory banner | Integration | `CompassViewModelTest` | REQ §7 §11.4 | AT-G-01 through AT-G-04 | P0 |
| PROP-LATITUDE-02 | OverallConfidence capped at MODERATE when inclination ≥80° | Unit | `CompassViewModelTest` | REQ §7 §11.4 | AT-G-01, AT-G-02 | P0 |

---

### PROP-LATITUDE-01: WMM inclination ≥80° triggers advisory banner

**Type:** Integration
**Test Level:** Instrumented
**Source:** REQ §7 §11.4 | FSPEC §11.4 ("WMM-predicted inclination ≥80° triggers advisory banner")

**Property:** When `MagneticFieldModel.getExpectedInclination(latDeg, lonDeg, altM, epochYears)` returns ≥ 80.0°, `CompassViewModel` emits a UI state with `extremeLatitudeAdvisory = true`, and the advisory banner is displayed in `CompassActivity`.

**Given:** `CompassViewModel` configured with a `MagneticFieldModel` stub returning `inclination_deg = 80.0f`
**When:** `CompassViewModel.onLocationUpdated(location)` is called and the UI state is observed
**Then:** `uiState.extremeLatitudeAdvisory == true`; `onView(withId(R.id.extreme_latitude_banner)).check(matches(isDisplayed()))`

---

### PROP-LATITUDE-02: OverallConfidence capped at MODERATE when inclination ≥80°

**Type:** Unit
**Test Level:** JVM
**Source:** REQ §7 §11.4 | FSPEC §11.4 (confidence cap)

**Property:** `ConfidenceModel.compute()` caps the result at `MODERATE` when `inclination_deg ≥ 80.0`. A computed `HIGH` confidence is reduced to `MODERATE`. A computed `POOR` confidence is not raised.

**Given:** All five dimension scores `GOOD(3)`; tilt = 0.0°; `hasGyroscope = true`; `sensorState = NORMAL`; `inclination_deg = 80.0f`
**When:** `ConfidenceModel.compute(..., inclination_deg=80.0f)` is called
**Then:** result is `OverallConfidence.MODERATE` (not HIGH)

Also:
**Given:** All five scores `POOR(1)` (or any state producing `POOR`); `inclination_deg = 85.0f`
**When:** `compute(..., inclination_deg=85.0f)` called
**Then:** result is `OverallConfidence.POOR` (cap does not raise to MODERATE)

---

## Domain: PROP-PERF — Performance

### Summary table

| ID | Description | Level | Test Class | REQ | FSPEC | Priority |
|----|-------------|-------|------------|-----|-------|----------|
| PROP-PERF-01 | Cold start ≤5s (Application.onCreate to first non-STABILIZING heading) | Macrobenchmark | `CompassColdStartBenchmark` | REQ-NFR-08 | AT-NFR-01 | P1 |
| PROP-PERF-02 | Warm start ≤3s, same method | Macrobenchmark | `CompassWarmStartBenchmark` | REQ-NFR-08 | AT-NFR-01 | P1 |

---

### PROP-PERF-01: Cold start ≤5s on reference device class

**Type:** E2E
**Test Level:** Macrobenchmark
**Source:** REQ-NFR-08 | FSPEC §2 "≤5 s (cold start)"

**Property:** From `Application.onCreate()` to the first `CompassUiState` emission with `confidence != STABILIZING`, elapsed real time is ≤ 5000 ms. Measured as the median of ≥5 Macrobenchmark iterations on a reference device (≥2 GB RAM, Snapdragon 600-series or equivalent, API 26+).

**Given:** Reference device; no prior app process; cold start triggered by Macrobenchmark
**When:** `MacrobenchmarkRule.measureRepeated(startupMode=StartupMode.COLD, iterations=5)` runs; timing recorded from `Application.onCreate()` to first non-STABILIZING heading event
**Then:** `median(iterationDurations) <= 5000L` ms

---

### PROP-PERF-02: Warm start ≤3s

**Type:** E2E
**Test Level:** Macrobenchmark
**Source:** REQ-NFR-08 | FSPEC §2 "≤3 s (warm cache)"

**Property:** Warm start (process alive, activity recreated) from `Activity.onStart()` to first non-STABILIZING heading is ≤ 3000 ms. Median of ≥5 Macrobenchmark iterations, same reference device class.

**Given:** Reference device; app process alive in background; warm start triggered
**When:** `MacrobenchmarkRule.measureRepeated(startupMode=StartupMode.WARM, iterations=5)` runs
**Then:** `median(iterationDurations) <= 3000L` ms

---

## Domain: PROP-SCHEMA — Data Schema Correctness

### Summary table

| ID | Description | Level | Test Class | REQ | FSPEC | Priority |
|----|-------------|-------|------------|-----|-------|----------|
| PROP-SCHEMA-01 | BearingRecord has exactly 15 fields with correct types | Unit | `BearingCaptureUseCaseTest` | REQ-CAPTURE-01 | FSPEC §6.1 | P0 |

---

### PROP-SCHEMA-01: BearingRecord has exactly 15 fields with correct types

**Type:** Unit
**Test Level:** JVM
**Source:** REQ-CAPTURE-01 | FSPEC §6.1 schema table

**Property:** `BearingRecord` is a Room `@Entity` with exactly 15 declared fields, each with the type specified in FSPEC §6.1:

| Field | Required Type |
|-------|--------------|
| `id` | `String` (UUID v4, TEXT primary key — NOT auto-increment Long) |
| `name` | `String` |
| `bearing_deg` | `Float` |
| `lat` | `Double?` |
| `lon` | `Double?` |
| `alt_m` | `Double?` |
| `captured_at` | `Long` |
| `notes` | `String?` |
| `display_mode` | `String?` |
| `calibration_version` | `String` |
| `north_type` | `String` |
| `confidence` | `String` |
| `interference_flag` | `Boolean` |
| `field_deviation_pct` | `Float` |
| `inclination_deviation_deg` | `Float` |

*(Note: `id` is `String` (UUID v4), not `Long` auto-increment — per FSPEC §6.1 normative note: "Not an auto-increment integer, to support future sync/export without collision." The Kotlin property name for the altitude field is `alt_m` — the canonical SQLite column name per FSPEC §6.1 — not `altitude_m`.)*

**Given:** Kotlin reflection on `BearingRecord::class`
**When:** All `memberProperties` are collected and their return types are inspected
**Then:**
- `memberProperties.size == 15`
- `id.returnType.classifier == String::class` (UUID v4 string, NOT Long)
- `bearing_deg.returnType.classifier == Float::class`
- `lat.returnType.isMarkedNullable == true && lat.returnType.classifier == Double::class`
- `lon.returnType.isMarkedNullable == true && lon.returnType.classifier == Double::class`
- `alt_m.returnType.isMarkedNullable == true && alt_m.returnType.classifier == Double::class`
- `captured_at.returnType.classifier == Long::class`
- `notes.returnType.isMarkedNullable == true && notes.returnType.classifier == String::class`
- `interference_flag.returnType.classifier == Boolean::class`
- `field_deviation_pct.returnType.classifier == Float::class`
- `inclination_deviation_deg.returnType.classifier == Float::class`

---

## Summary

### Properties by domain

| Domain | Count |
|--------|-------|
| PROP-NORTH | 6 |
| PROP-DECL | 3 |
| PROP-LOCATION | 6 |
| PROP-CAPTURE | 10 |
| PROP-PERSIST | 4 |
| PROP-INTERFERENCE | 3 |
| PROP-LATITUDE | 2 |
| PROP-PERF | 2 |
| PROP-SCHEMA | 1 |
| **Total** | **37** |

### Properties by test level

| Level | Count |
|-------|-------|
| Unit (JVM) | 21 |
| Integration (Instrumented/JVM) | 9 |
| E2E (Espresso) | 5 |
| E2E (Macrobenchmark) | 2 |
| **Total** | **37** |

### Properties by priority

| Priority | Count |
|----------|-------|
| P0 | 30 |
| P1 | 7 |
| **Total** | **37** |

---

## Coverage Matrix

REQ ID → PROP IDs that verify it:

| REQ ID | PROP IDs |
|--------|----------|
| REQ-NORTH-01 | PROP-NORTH-01, PROP-NORTH-03, PROP-NORTH-04, PROP-INTERFERENCE-01, PROP-INTERFERENCE-02 |
| REQ-NORTH-02 | PROP-NORTH-04, PROP-INTERFERENCE-03 |
| REQ-NORTH-03 | PROP-LOCATION-01, PROP-LOCATION-02, PROP-LOCATION-03, PROP-LOCATION-04, PROP-LOCATION-05, PROP-LOCATION-06 |
| REQ-NORTH-04 | PROP-NORTH-01, PROP-NORTH-02, PROP-NORTH-05, PROP-NORTH-06 |
| REQ-DECL-01 | PROP-DECL-01, PROP-NORTH-05, PROP-NORTH-06 |
| REQ-DECL-02 | PROP-DECL-02 |
| REQ-CAPTURE-01 | PROP-CAPTURE-01, PROP-CAPTURE-02, PROP-CAPTURE-03, PROP-CAPTURE-04, PROP-CAPTURE-05, PROP-CAPTURE-06, PROP-SCHEMA-01 |
| REQ-CAPTURE-02 | PROP-CAPTURE-07, PROP-CAPTURE-08, PROP-CAPTURE-09 |
| REQ-CAPTURE-04 | PROP-PERSIST-01, PROP-PERSIST-02, PROP-PERSIST-03 |
| REQ-CAPTURE-06 | PROP-LOCATION-04, PROP-PERSIST-04, PROP-CAPTURE-10 |
| REQ-DETECT-01 | PROP-INTERFERENCE-01, PROP-INTERFERENCE-03 |
| REQ-DETECT-02 | PROP-INTERFERENCE-02 |
| REQ-NFR-05 | PROP-DECL-03 |
| REQ-NFR-08 | PROP-PERF-01, PROP-PERF-02 |
| REQ §7 §11.4 | PROP-LATITUDE-01, PROP-LATITUDE-02 |

---

## Phase 1 Regression Properties

Phase 2 must not break any Phase 1 behavioral invariants. The following table cross-references the Phase 1 PROPERTIES document (`PROPERTIES-luopan-p1-core-compass.md`) domains that are at risk of regression in Phase 2, and identifies which Phase 2 changes could affect them.

| Phase 1 Domain | At-Risk Phase 1 Properties | Phase 2 Change That Could Break Them | Regression Guard |
|----------------|---------------------------|--------------------------------------|------------------|
| PROP-FUSION | PROP-FUSION-01 through -06 | None — sensor fusion is unchanged in Phase 2 | Re-run full PROP-FUSION suite in Phase 2 CI |
| PROP-DETECT | PROP-DETECT-01 through -13 | `InterferenceDetector` now uses `MagneticFieldModel` instead of `GeomagneticField`; could change threshold behavior | PROP-INTERFERENCE-03 guards fallback; PROP-DETECT suite re-run with WMM-backed `InterferenceDetector` |
| PROP-CONF | PROP-CONF-01 through -17 | `ConfidenceModel` gains extreme-latitude cap (PROP-LATITUDE-02) | Confirm cap is additive: all existing PROP-CONF tests must still pass when `inclination_deg < 80°` |
| PROP-PERSIST (Phase 1) | PROP-PERSIST-01 (`calibration_records` encrypted), PROP-PERSIST-02 (atomic write), PROP-PERSIST-03 (timestamps UTC) | Database migration (PROP-PERSIST-03 Phase 2) could corrupt `calibration_records` | PROP-PERSIST-03 (Phase 2) explicitly verifies `calibration_records` is untouched |
| PROP-DISPLAY | PROP-DISPLAY-01 through -11 | New UI slots for north toggle and capture button may cause layout reflow | Add Phase 2 Espresso check that Phase 1 always-visible elements (compass rose, heading readout, confidence badge) pass `isDisplayed()` after north toggle |
| PROP-CAL | PROP-CAL-01 through -10 | Calibration engine unchanged in Phase 2 | Re-run full PROP-CAL suite |
| PROP-NFR | PROP-NFR-01 through -05 | WMM computation in hot path could increase latency; APK size grows with bundled coefficients | PROP-PERF-01/-02 bound cold/warm start; Phase 1 PROP-NFR-01 (p95 ≤ 50 ms) re-run with Phase 2 binary |

### Explicit Phase 1 regression assertions required in Phase 2 CI

The following Phase 1 properties MUST be included in the Phase 2 test run without modification (they are copy-run, not reimplemented):

- **PROP-DETECT-01 through PROP-DETECT-13** — all interference state-machine boundary tests, now executed with `InterferenceDetector` wired to `MagneticFieldModel`. Expected behavior is identical when `InterferenceState` transitions are driven by the same field deviation fractions and inclination deviation degrees.
- **PROP-CONF-01 through PROP-CONF-17** — all confidence model boundary tests. The extreme-latitude cap is an additive post-processing step; existing tests pass `inclination_deg < 80°` and must produce unchanged results.
- **PROP-PERSIST-02 (Phase 1)** (`calibration_records` DB encrypted) — re-verified in Phase 2 build to confirm `bearing_records.db` encryption does not interfere with the existing `calibration_records.db` key management.
- **PROP-NFR-01** (sensor-to-UI latency p95 ≤ 50 ms) — re-run against Phase 2 binary to confirm WMM declination computation does not add latency to the sensor hot path.
- **PROP-NFR-04** (APK ≤ 15 MB) — re-verified; WMM2025 coefficient file is expected to add ~200 KB, keeping total well under the 15 MB limit.
