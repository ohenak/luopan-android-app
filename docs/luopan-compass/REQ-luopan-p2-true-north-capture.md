# REQ-luopan-p2-true-north-capture
## Phase 2: True North, Magnetic Declination, and Bearing Capture

| Field | Value |
|-------|-------|
| **Version** | 0.3-draft |
| **Date** | 2026-04-24 |
| **Revised** | 2026-04-24 — v0.2-draft: addressed SE/TE cross-review findings (F-01–F-06, F-10, TE-REQ-01–TE-REQ-10); 2026-04-24 — v0.3-draft: addressed iteration 2 cross-review findings N-01 (`calibration_version` type and semantics) and N-02 (`lat`/`lon` precision) |
| **Status** | Draft |
| **Phase** | 2 of 5 |
| **Parent REQ** | [REQ-luopan-compass v0.2-draft](REQ-luopan-compass.md) |
| **Prerequisite** | Phase 1 complete and stable |
| **Delivery plan** | [DELIVERY-PLAN.md](DELIVERY-PLAN.md) |

---

## 1. Goal

Upgrade the compass from magnetic-only to **true north capable** by bundling WMM2025 for offline declination computation, and add **bearing capture** so field professionals can record and review headings with full metadata.

---

## 2. Usable Application State After Phase 2

| Capability | Detail |
|-----------|--------|
| True north | WMM2025 bundled; declination computed offline at current GPS location; heading labeled "True N" or "Magnetic N" |
| North type toggle | Quick toggle between True N / Magnetic N; visible in main UI without entering settings |
| Declination info panel | Shows declination value, WMM2025 source, coordinates used, expiry date |
| GPS-unavailable handling | Uses cached location (≤30 days) or prompts for manual coordinates; falls back to magnetic-only with clear label |
| Interference detection upgrade | REQ-DETECT-01 and REQ-DETECT-02 now use WMM2025 expected values instead of Phase 1's Android `GeomagneticField` fallback — more accurate thresholds |
| Extreme latitude advisory | WMM-predicted inclination ≥80° triggers advisory (§11.4) |
| Bearing save | User can name and save a bearing with: degrees, north type, confidence, timestamp, optional GPS coordinates, optional notes |
| Cold-start performance | App opens to first heading in ≤3 s (warm cache) / ≤5 s (cold) |

---

## 3. Personas Served After Phase 2

| Persona | Status | New capability |
|---------|--------|---------------|
| Persona 2 — Engineer Chen (surveyor) | **Fully served** | True north, bearing save, declination info |
| Persona 3 — Yamada (hiker) | **Improved** | Can record GPS-anchored bearings; true north for navigation |
| Persona 4 — Ms. Wang | **Improved** | True north toggle available |
| Persona 1 — Master Li | **Not yet served** | Luopan mode still Phase 3 |

---

## 4. User Stories Active in This Phase

| ID | Title |
|----|-------|
| US-05 | Recording a bearing for later reference *(newly active)* |
| US-09 | Checking magnetic declination at my location *(newly active)* |
| US-10 | Operating without GPS *(newly active)* |
| US-03, US-04, US-08, US-11 | Continued from Phase 1 |

---

## 5. Requirements in Scope

### 5.1 True North Correction

| ID | Title | Priority | Key Spec |
|----|-------|----------|----------|
| REQ-NORTH-01 | Bundled WMM2025 coefficients | P0 | 168 Gauss coefficients; valid 2025.0–2030.0; all declination/inclination/magnitude calculations use these offline |
| REQ-NORTH-02 | Android GeomagneticField fallback | P0 | Used when WMM2025 expires (post-2030) or fails; UI labels source as "Android model — may be less accurate" |
| REQ-NORTH-03 | GPS location handling | P0 | Location source priority (in order): (1) a current GPS fix obtained within the active session via `LocationManager.requestLocationUpdates`; (2) last known location from `LocationManager.getLastKnownLocation` if ≤30 days old — this is the "cached" location; (3) manual coordinate entry if neither (1) nor (2) is available. "Current GPS fix" means a fix received after the app opened in the current session; "last known location" means the OS-retained fix from a prior session. Cache age is evaluated at the moment the WMM calculation is triggered. Cache stored locally only. |
| REQ-NORTH-04 | North reference label on all headings | P0 | Every displayed heading value shows "True N", "Magnetic N", or "Grid N" (Grid N deferred to Phase 5) |

**Upgrade to Phase 1 interference detection — WMM interface contract:** With WMM2025 now available, REQ-DETECT-01 and REQ-DETECT-02 MUST switch from `GeomagneticField`-based expected values to WMM2025-based expected values. No new requirement ID — this is a conformance upgrade of existing requirements.

**Interface contract (F-01):** WMM2025 predictions MUST be injected into the interference detector via a `MagneticFieldModel` interface (not computed inline from live sensor EMA). The interface MUST expose at minimum:

```
interface MagneticFieldModel {
    fun getExpectedFieldMagnitude(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): Float  // µT
    fun getExpectedInclination(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): Float    // degrees
    fun getDeclination(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): Float            // degrees
}
```

Concrete implementations: `Wmm2025Model` (Phase 2 default) and `GeomagneticFieldModel` (Android fallback, used post-2030 or when WMM fails). The interference detector (`InterferenceDetector`) MUST accept `MagneticFieldModel` via constructor injection so tests can substitute a deterministic stub (see Scenario F and TE-REQ-09). `InterferenceMetrics` continues to carry `fieldDeviation` and `inclinationDeviation` computed against the model-supplied expected values — it does NOT derive baselines from live sensor EMA.

### 5.2 Declination Management

| ID | Title | Priority | Key Spec |
|----|-------|----------|----------|
| REQ-DECL-01 | North type toggle | P0 | True N / Magnetic N toggle visible in main UI (Grid N deferred); applies to all display modes simultaneously |
| REQ-DECL-02 | Declination info panel | P1 | Shows: declination in °E/°W + decimal, source label, coordinates (masked to 2 dp), last-updated date |

### 5.3 Bearing Capture

| ID | Title | Priority | Key Spec |
|----|-------|----------|----------|
| REQ-CAPTURE-01 | Bearing record schema | P0 | All required fields per the table below; see §5.3.1 for field definitions. |
| REQ-CAPTURE-02 | Capture flow UX | P0 | Name input dialog; bearing preview; optional notes; ≤3 taps from button to saved. **Note:** the first-ever capture when GPS is available inserts an additional mandatory location privacy dialog (REQ-CAPTURE-06) before the name input dialog — this dialog adds 1 tap (acknowledgement) but is shown only once. The ≤3 taps budget applies to subsequent captures where the privacy dialog has already been acknowledged. |
| REQ-CAPTURE-04 | Data persistence (encrypted) | P0 | SQLite/Room with SQLCipher or equivalent; survives force-stop. **Verifiability criterion:** after at least one BearingRecord has been saved, opening the bearing database file via the standard `android.database.sqlite.SQLiteDatabase.openDatabase()` (without the SQLCipher key) MUST throw an exception or produce a file whose first 16 bytes do NOT match the standard SQLite magic string (`53 51 4C 69 74 65 20 66 6F 72 6D 61 74 20 33 00`) — same contract as Phase 1 PROP-PERSIST-02 for calibration DB. |
| REQ-CAPTURE-06 | Location privacy confirmation | P0 | First-capture dialog explains location is optional and on-device only; toggle defaults to ON |

#### 5.3.1 BearingRecord Field Definitions

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | UUID | Yes | Unique record identifier, generated at capture time. |
| `name` | String (max 100 chars) | Yes | User-supplied name. |
| `bearing_deg` | Float (0.000–360.000) | Yes | Heading value at moment of capture. |
| `north_type` | Enum: TRUE, MAGNETIC, GRID | Yes | North reference active at capture time. |
| `confidence` | Enum: HIGH, MODERATE, POOR | Yes | `OverallConfidence` at moment of capture. |
| `captured_at` | ISO 8601 UTC timestamp | Yes | Wall-clock time of capture. |
| `calibration_version` | String | Yes | WMM model identifier at capture time — provided by `MagneticFieldModel.getModelId()`. Examples: `"WMM2025"`, `"AndroidGeoField"`. This field records which magnetic field model was active during the capture session. It is NOT the calibration record schema version (`CalibrationRecord.calibration_schema_version` is a separate integer field in the `calibration_records` table and MUST NOT be conflated with this field). |
| `field_deviation_pct` | Float | Yes | Fraction (×100 to get %) of magnetic field magnitude deviation from WMM2025 expected value at capture time — sourced from `InterferenceMetrics.fieldDeviation`. Value of `0.0` means no deviation. |
| `inclination_deviation_deg` | Float | Yes | Absolute deviation in degrees of measured inclination from WMM2025 expected inclination at capture time — sourced from `InterferenceMetrics.inclinationDeviation`. Value of `0.0` means no deviation. |
| `interference_flag` | Boolean | Yes | `true` if `InterferenceState` was `MODERATE` or `WARNING` at capture time. **Note:** a `POOR` `OverallConfidence` does NOT automatically set this flag — a bearing can be POOR confidence (e.g., due to low calibration quality) without magnetic interference being detected. |
| `latitude` | Double? | Conditional | Only stored if GPS consent given and a location fix is available. IEEE 754 double precision required for WMM computation accuracy (≈11 cm precision at 6 decimal places vs ≈11 m for Float). |
| `longitude` | Double? | Conditional | Only stored if GPS consent given and a location fix is available. IEEE 754 double precision required for WMM computation accuracy (≈11 cm precision at 6 decimal places vs ≈11 m for Float). |
| `altitude_m` | Float | Conditional | Only stored if GPS consent given and a location fix is available. |
| `notes` | String (max 1000 chars) | No | Optional free-text notes. |
| `display_mode` | Enum: MODERN, LUOPAN, SIGHTING | Yes | Active display mode at capture time. |

### 5.4 Non-Functional

| ID | Title | Priority | Target |
|----|-------|----------|--------|
| REQ-NFR-08 | Cold-start time | P1 | **Start event:** `Application.onCreate()` is entered. **End event:** the compass heading value is rendered in the UI with a non-"STABILIZING" `OverallConfidence`. **Targets:** ≤3 s (warm cache — WMM coefficients and cached GPS location already loaded); ≤5 s (cold start — first launch after install or data clear). **Reference device class:** mid-range Android device (≥2 GB RAM, Snapdragon 600-series or equivalent, API 26+). **Measurement method:** [AndroidX Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview) `StartupMode.COLD` and `StartupMode.WARM` with ≥5 iterations; median value reported. |

---

## 6. Requirements Deferred from Phase 2

| ID | Title | Deferred to |
|----|-------|-------------|
| REQ-CAPTURE-03 | Bearing history screen | Phase 4 |
| REQ-CAPTURE-05 | Share / export | Phase 5 |
| REQ-DECL-03 | Grid north | Phase 5 |
| REQ-DISPLAY-04–06 | Luopan mode | Phase 3 |

---

## 7. Edge Cases Newly Activated in Phase 2

From [master REQ §11](REQ-luopan-compass.md#11-edge-cases-and-failure-modes):

| §11 Section | Scenario | Required Behavior |
|-------------|----------|------------------|
| §11.4 | Extreme latitude (WMM inclination ≥ 80°) | Advisory banner; confidence capped at Moderate; 分金 ring (Phase 3) pre-blocked at "Moderate" cap |
| §11.8 | GPS completely unavailable | Use cached location (≤30 days) or prompt for manual coordinates; True North toggle shows source clearly |

---

## 8. Phase 2 End-to-End Acceptance Test

**Scenario A — True north switching**

*Given* Phase 1 is complete (calibrated, interference-free), GPS fix available,  
*When* the user taps the north type toggle to switch from Magnetic N to True N,  
*Then*:
- Heading value changes by the local declination within 200 ms. **Measurement:** wall-clock time from `View.performClick()` on the toggle (the tap event) to the first frame where the heading `TextView` displays a value differing from the pre-tap value by ≥ (`declination − 0.5°`). Measured via Espresso `IdlingResource` or Macrobenchmark frame timing. Tolerance: ±50 ms (i.e., pass if ≤250 ms on the reference device class defined in REQ-NFR-08).
- Label changes to "True N"
- Switching back restores "Magnetic N" and the original value

**Scenario B — Offline declination with GPS**

*Given* the device has no internet connection but has a recent GPS fix,  
*When* True North mode is active,  
*Then*:
- WMM2025 bundled model computes declination correctly (within ±0.1° of NOAA reference for the given location)
- Declination info panel shows "WMM2025 (valid to 2030)"
- No network request is made

**Static WMM2025 test vector (TE-REQ-02):** Tests MUST NOT call the live NOAA calculator. The following vector is derived from the WMM2025 Technical Note (NOAA, 2024) and must be used as a fixed JVM unit test assertion:

| Parameter | Value |
|-----------|-------|
| Latitude | 40.0° N |
| Longitude | −105.0° W (105° West) |
| Altitude | 0 m (WGS84 ellipsoid surface) |
| Epoch date | 2025.5 (1 July 2025) |
| Expected declination | **+8.93° E** |
| Tolerance | ±0.1° |
| Expected inclination | **+66.0°** |
| Inclination tolerance | ±0.5° |
| Expected total field | **52 300 nT (52.3 µT)** |
| Total field tolerance | ±200 nT |

*If NOAA publishes a corrected value before Phase 2 ships, the PM and SE must update this table and re-confirm the test passes.*

**Scenario C — No GPS, cached location**

*Given* GPS is unavailable; a cached location from 15 days ago is stored,  
*When* True North mode is activated,  
*Then*:
- Declination computed from cached location
- UI shows "Using last known location (N days ago)" where N is the integer number of whole days since the cached fix timestamp. **Formatting contract:** the string resource key is `R.string.location_cache_age_label` with a single integer substitution parameter `%d` (days). Acceptance criterion is assertable by injecting a fixed clock (e.g., `N = 15`): the displayed string MUST equal `"Using last known location (15 days ago)"` when the cache age is exactly 15 days. The days boundary is midnight UTC.
- Heading labeled "True N"

**Scenario D — No GPS, no cache**

*Given* GPS has never been available on this device,  
*When* user taps the True North toggle,  
*Then*:
- Dialog: "Enter coordinates for True North or use Magnetic North only"
- If user enters coordinates: declination computed; heading labeled "True N (manual location)"
- If user dismisses: mode stays Magnetic North

**Scenario E — Bearing capture (Modern Mode)**

*Given* confidence is "High" or "Moderate", True North active,  
*When* user taps the capture button, enters a name, and confirms,  
*Then*:
- Record saved with: bearing_deg, north_type=TRUE, confidence=HIGH (or MODERATE), timestamp, GPS coordinates, calibration_version, field_deviation_pct, inclination_deviation_deg, interference_flag
- `interference_flag` is `false` when `InterferenceState` is `CLEAR` at capture time, and `true` when `InterferenceState` is `MODERATE` or `WARNING`. **Note:** `interference_flag` is NOT derived from `OverallConfidence` — a bearing can have `OverallConfidence.POOR` (e.g., due to poor calibration quality) with `interference_flag=false` if `InterferenceState` is `CLEAR`.
- Confirmation toast: "Bearing saved as '[name]'"
- Capturing with `InterferenceState` of `MODERATE` or `WARNING`: bearing saves with `interference_flag=true`; record visually marked "⚠ Captured under interference" (visible in Phase 4 history; in Phase 2 a toast warns before saving)

**Scenario F — WMM interference baseline upgrade**

*Given* WMM2025 is now available (Phase 2),  
*When* the interference check runs,  
*Then*:
- Expected field magnitude and inclination are derived from WMM2025, not Android `GeomagneticField`
- Interference thresholds (15%/25% magnitude, 3°/8° inclination) apply with WMM-accurate baselines
- **Testable seam (TE-REQ-09):** `InterferenceDetector` MUST accept `MagneticFieldModel` via constructor injection (see §5.1 interface contract). In tests, a stub implementation returning fixed values MUST be substitutable for `Wmm2025Model` so that assertions on `InterferenceState` transitions are not dependent on the live WMM2025 model path. This allows test code to distinguish "WMM2025 path" from "GeomagneticField fallback path" at assertion time.

**Scenario G — GPS permission denied at runtime**

*Given* the user has denied `ACCESS_FINE_LOCATION` (and `ACCESS_COARSE_LOCATION`) when the system permission dialog was shown,  
*When* the app attempts to compute declination for True North mode,  
*Then*:
- The app does NOT crash or throw an unhandled `SecurityException`
- True North toggle is still accessible; on activation the app proceeds to the "No GPS, no cache" path (Scenario D) — manual coordinate entry or stay on Magnetic North
- The capture flow proceeds without GPS coordinates; `latitude`, `longitude`, and `altitude_m` fields are `null` in the saved BearingRecord
- No permission rationale dialog is re-shown automatically (the user chose to deny); a one-time informational banner MAY appear explaining that location was denied and True North will use manual coordinates
- The bearing history (Phase 4) correctly shows records without location as "No location recorded"

**Pass criteria:** No crash; app remains fully functional for magnetic-north bearing capture; no GPS coordinate is silently assumed or fabricated; UI clearly indicates no location is available.

**Fail criteria:** App crashes on permission denial; a `SecurityException` propagates to the UI; any location-dependent field is populated with a fabricated value.

---

## 9. Open Questions and Risks Specific to Phase 2

**Risk P2-R1 — WMM2025 licensing:** Confirm NOAA/BGS public domain status before bundling. See [master REQ §14 OQ-01](REQ-luopan-compass.md#oq-01-wmm2025-licensing-and-bundling). This is a blocker for REQ-NORTH-01.

**Risk P2-R2 — GPS permission rejection:** If the user denies `ACCESS_FINE_LOCATION`, all location-dependent features (declination, bearing GPS coordinates) degrade gracefully. REQ-NORTH-03 and REQ-CAPTURE-06 both handle this; Scenario G defines the explicit pass/fail criteria for the denied-permission path.

**Risk P2-R3 — Cached location age boundary:** The 30-day cache cutoff is an assumption. **Boundary behavior (TE-REQ-08):** a cached location that is exactly 30 days old (to the second) is considered valid and MUST be used; a location that is 30 days + 1 second old (i.e., strictly older than 30 days) is considered expired and MUST NOT be used — the app MUST proceed to Scenario D (manual entry or Magnetic North fallback). This boundary is evaluated in wall-clock seconds from `cached_at` UTC to the moment the WMM calculation is triggered. Feedback from beta practitioners may suggest a shorter window (e.g., 7 days) in areas of high magnetic secular variation (e.g., Alaska, Siberia) — this is deferred to Phase 4 settings.

**Risk P2-R4 — Room DB schema migration:** Phase 2 introduces `BearingRecord` as a new Room entity, adding a new table to the database (schema version v1 → v2). Engineering MUST implement an explicit Room `Migration(1, 2)` object (CREATE TABLE for `bearing_records`). `fallbackToDestructiveMigration` is PROHIBITED for production builds — a destructive migration on an existing install would erase Phase 1 `calibration_records`, destroying saved calibration data. The migration strategy MUST be documented in TSPEC §9 and covered by an instrumented migration test (`MigrationTest` using Room's `MigrationTestHelper`).

---

## 10. Technical Constraints

### 10.1 Android Permissions

Phase 2 requires the following runtime permissions in addition to any declared in Phase 1:

| Permission | Type | Required for | When requested |
|-----------|------|-------------|---------------|
| `ACCESS_FINE_LOCATION` | Dangerous (runtime) | Current GPS fix for declination; GPS coordinates in BearingRecord | Requested at the moment the user first activates True North mode (toggle tap), or first taps the capture button when GPS has not yet been requested in the session. A rationale dialog MUST be shown before the system permission prompt if `shouldShowRequestPermissionRationale()` returns `true`. |
| `ACCESS_COARSE_LOCATION` | Dangerous (runtime) | Fallback if FINE is denied; provides approximate location for declination | Requested alongside `ACCESS_FINE_LOCATION` in the same `requestPermissions` call. |

**Permission denial handling:** If both permissions are denied, the app MUST NOT crash. The app proceeds as described in Scenario G. The app MUST NOT repeatedly re-request permissions in the same session after the user has denied them.

**Declaration:** Both permissions MUST appear in `AndroidManifest.xml` as `<uses-permission>` entries. `ACCESS_FINE_LOCATION` implies `ACCESS_COARSE_LOCATION` on API 31+, but both MUST be declared for API 26–30 compatibility.

### 10.2 Room Database Schema Migration

Phase 2 bumps the Room database schema from version 1 (Phase 1, `calibration_records` table only) to version 2 (adds `bearing_records` table).

**Requirements:**
1. The `AppDatabase` `@Database(version = 2)` annotation MUST be accompanied by an explicit `Migration(1, 2)` passed to `.addMigrations()`.
2. `Migration(1, 2)` MUST execute only `CREATE TABLE IF NOT EXISTS bearing_records (...)` — it MUST NOT drop or alter `calibration_records`.
3. `fallbackToDestructiveMigration()` is **PROHIBITED** in any non-test build variant. Lint or a CI check MUST enforce this.
4. The TSPEC §9 MUST document the full SQL DDL for `bearing_records` and the migration object.
5. An instrumented `MigrationTest` (using `MigrationTestHelper`) MUST verify that upgrading a v1 database to v2 preserves all existing rows in `calibration_records` and successfully creates the `bearing_records` table.

### 10.3 MagneticFieldModel Interface (Testability Seam)

As specified in §5.1 interface contract, `MagneticFieldModel` is a required injectable interface. Engineering MUST:
1. Define `MagneticFieldModel` as a Kotlin `interface` in the domain layer.
2. Provide `Wmm2025Model : MagneticFieldModel` (Phase 2 default) and `GeomagneticFieldModel : MagneticFieldModel` (Android fallback).
3. Inject the active implementation into `InterferenceDetector` via constructor (no static/singleton references to `Wmm2025Model`).
4. Provide a `FakeMagneticFieldModel : MagneticFieldModel` in the `testFixtures` source set for use in unit and integration tests.
