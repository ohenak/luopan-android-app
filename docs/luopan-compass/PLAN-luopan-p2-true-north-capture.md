# PLAN-luopan-p2-true-north-capture
## Execution Plan — Phase 2: True North, Magnetic Declination, and Bearing Capture

| Field | Value |
|-------|-------|
| **Document ID** | PLAN-luopan-p2-true-north-capture |
| **Version** | 0.1 |
| **Date** | 2026-04-24 |
| **Status** | Draft |
| **Phase** | 2 of 5 |
| **Author** | Engineering |
| **Parent REQ** | [REQ-luopan-p2-true-north-capture.md](REQ-luopan-p2-true-north-capture.md) |
| **Branch** | `feat-luopan-p2-true-north-capture` |

---

## Codebase Baseline (Phase 1 complete)

| Component | File / Package | Notes |
|-----------|---------------|-------|
| `LuopanDatabase` | `com.luopan.compass.db` | Version 1, `exportSchema=false`, `fallbackToDestructiveMigration()`, SQLCipher-encrypted |
| `CalibrationRecord` | `com.luopan.compass.calibration` | Only Room entity; DB migration to v2 needed for `BearingRecord` |
| `InterferenceDetector` | `com.luopan.compass.sensor` | Uses EMA baseline field magnitude — must switch to WMM-based expected values in Phase 4.2 |
| `InterferenceMetrics` | `com.luopan.compass.sensor` | Has `expectedField_uT` and `expectedInclination_deg` fields (currently zeroed/EMA) |
| `CompassViewModel` | `com.luopan.compass.ui` | `AndroidViewModel`; reads `declinationMode` from `SettingsRepository` but does not apply actual declination offset |
| `CompassUiState` | `com.luopan.compass.ui` | Has `north_label`, `location_fallback_advisory`, `fallback_mag_advisory` fields (currently stub values) |
| `SettingsRepository` | `com.luopan.compass.settings` | SharedPreferences; has `declinationMode` key (`DECLINATION_MAGNETIC` default) |
| `LocationProvider` | `com.luopan.compass.sensor` | `fun interface`; `SystemLocationProvider` wraps `LocationManager.getLastKnownLocation` |
| Permissions | `AndroidManifest.xml` | Only `WAKE_LOCK`; GPS permissions absent |
| Raw resources | `app/src/main/res/raw/` | Directory does not exist yet |
| SQLCipher | `app/build.gradle.kts` | Already added as dependency |
| Room | `app/build.gradle.kts` | `room.runtime`, `room.ktx`, `room.compiler` (kapt) already present |

---

## 1. Phase Table

| Phase ID | Name | Description | Dependencies | Test Targets | Complexity | Status |
|----------|------|-------------|--------------|--------------|------------|--------|
| **P1.1** | Interfaces & Clock | Define `MagneticFieldModel` interface (`getDeclination`, `getInclination`, `getMagnitude`, `isExpired`) and `Clock` interface (in `com.luopan.compass.util`). `SystemClock` production impl wraps `System.currentTimeMillis()`. | — | `ClockTest` (unit) | S | ⬜ |
| **P1.2** | BearingRecord entity & DAO | Define `BearingRecord` Room entity with all REQ-CAPTURE-01 fields. Define `BearingDao` (insert, getById, getAll, delete). Write `Migration(1,2)` adding `bearing_records` table. | — | `BearingRecordTest`, `BearingDaoTest` (unit, in-memory) | M | ⬜ |
| **P1.3** | DB schema export & migration wiring | Enable `exportSchema = true` on `LuopanDatabase`; remove `fallbackToDestructiveMigration()`; register `Migration(1,2)`; add `BearingDao` abstract accessor; update `buildInMemory` helper. Configure `room.schemaLocation` in `build.gradle.kts`. | P1.2 | `LuopanDatabaseMigrationTest` (instrumented) | S | ⬜ |
| **P1.4** | Location permissions in Manifest | Add `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` `<uses-permission>` to `AndroidManifest.xml`. | — | (manifest lint check) | S | ⬜ |
| **P2.1** | Bundle WMM2025 coefficients | Create `app/src/main/res/raw/wmm2025_cof.txt` with NOAA WMM2025 COF-format coefficients (168 Gauss terms; valid 2025.0–2030.0). Add license comment header confirming NOAA public domain. | — | `Wmm2025CofResourceTest` (verifies resource loads, first coefficient parses correctly) | S | ⬜ |
| **P2.2** | `Wmm2025Model` implementation | Implement `Wmm2025Model : MagneticFieldModel` in `com.luopan.compass.wmm`. Parse COF file once at construction. Implement spherical harmonic evaluation for declination, inclination, and total field magnitude at (lat, lon, alt, epochYear). `isExpired()` returns true when `epochYear ≥ 2030.0`. | P1.1, P2.1 | `Wmm2025ModelTest`: NOAA reference test vectors (≥5 geographic locations, ±0.1° accuracy for declination/inclination, ±100 nT for magnitude) | L | ⬜ |
| **P2.3** | `AndroidGeoFieldModel` fallback | Implement `AndroidGeoFieldModel : MagneticFieldModel` in `com.luopan.compass.wmm`. Wraps `android.hardware.GeomagneticField`. `isExpired()` always returns false. | P1.1 | `AndroidGeoFieldModelTest`: verifies delegation to `GeomagneticField` for known location (Robolectric) | S | ⬜ |
| **P2.4** | `MagneticFieldModelProvider` | Implement `MagneticFieldModelProvider` in `com.luopan.compass.wmm`. Selects `Wmm2025Model` when not expired; falls back to `AndroidGeoFieldModel`. Exposes `activeSourceLabel(): String`. Inject `Clock` for testability. | P1.1, P2.2, P2.3 | `MagneticFieldModelProviderTest`: expired/non-expired switching, source label | S | ⬜ |
| **P3.1** | `LocationRepository` | Implement `LocationRepository` in `com.luopan.compass.location`. Responsibilities: (1) request fresh GPS fix via `FusedLocationProviderClient`; (2) cache last fix with timestamp in SharedPreferences (`luopan_location_cache`); (3) expose `getLocation(): LocationResult` sealed class (`GpsFix`, `CachedFix(ageMs)`, `ManualEntry`, `Unavailable`); (4) accept manual lat/lon entry; (5) evict cache older than 30 days. | — | `LocationRepositoryTest`: cached/GPS/manual/unavailable paths; cache eviction (unit, fake clock) | M | ⬜ |
| **P3.2** | Permission request flow | In `CompassActivity`: request `ACCESS_FINE_LOCATION` on first True North activation using `ActivityResultContracts.RequestMultiplePermissions`. Show rationale dialog (Material AlertDialog) before request if `shouldShowRequestPermissionRationale` is true. On permanent denial, show "Open Settings" dialog. | P1.4, P3.1 | `LocationPermissionTest` (UI/Espresso): grant path, deny path, rationale shown | M | ⬜ |
| **P4.1** | `CompassViewModel` — true north integration | Inject `MagneticFieldModelProvider` and `LocationRepository` into `CompassViewModel` (constructor injection via factory). Add `northType: StateFlow<NorthType>` (`MAGNETIC` / `TRUE`). When `TRUE`, apply declination offset to `heading_deg`. Populate `CompassUiState.north_label` correctly. Populate `location_fallback_advisory` (cached location) and `fallback_mag_advisory` (Android fallback model). | P1.1, P2.4, P3.1 | `CompassViewModelTest`: north toggle changes heading, advisory flags set correctly (unit, fake dependencies) | M | ⬜ |
| **P4.2** | Interference detector — WMM baseline upgrade | Update `CompassViewModel.startSensorCollection()`: when `MagneticFieldModelProvider` has a location fix, populate `InterferenceMetrics.expectedField_uT` and `expectedInclination_deg` from `MagneticFieldModel` instead of EMA. Keep EMA fallback when no location is available. | P2.4, P3.1, P4.1 | `InterferenceDetectorTest`: add WMM-baseline test cases; `CompassViewModelTest`: verify `expectedField_uT` comes from model when location available | M | ⬜ |
| **P4.3** | North type toggle UI | Add toggle button (Material `Button` or `ToggleButton`) to `activity_compass.xml`. Wire `onClick` → `CompassViewModel.toggleNorthType()`. Observe `northType` StateFlow; update button label and heading label. | P4.1 | `NorthTypeToggleTest` (Espresso): tap toggle, assert heading label changes | S | ⬜ |
| **P5.1** | `DeclinationInfo` data class & population | Define `DeclinationInfo(declination_deg, source_label, lat_masked, lon_masked, last_updated, valid_until)` in `com.luopan.compass.wmm`. Populate in `CompassViewModel` from `MagneticFieldModelProvider` + `LocationRepository`. Expose as `StateFlow<DeclinationInfo?>`. | P4.1 | `DeclinationInfoTest`: field masking to 2 dp, source label propagation (unit) | S | ⬜ |
| **P5.2** | Declination info panel UI | Implement bottom sheet (`BottomSheetDialogFragment`) showing all `DeclinationInfo` fields. Trigger from info icon near heading label in `activity_compass.xml`. Dismiss on tap-outside. | P5.1 | `DeclinationInfoPanelTest` (Espresso): opens on icon tap, shows correct source label | S | ⬜ |
| **P6.1** | `BearingCaptureUseCase` | Implement `BearingCaptureUseCase` in `com.luopan.compass.capture`. Inputs: current `CompassUiState`, `LocationRepository`, `Clock`. Constructs `BearingRecord`; sets `captured_at` to tap-time (`Clock.now()`); sets `interference_flag` when `confidence == POOR`; sets `north_type` from active `NorthType`. Returns `BearingRecord` (not yet persisted). | P1.1, P4.1 | `BearingCaptureUseCaseTest`: interference_flag set when POOR, captured_at is tap-time, north_type propagated | M | ⬜ |
| **P6.2** | `BearingRepository` | Implement `BearingRepository` in `com.luopan.compass.capture`. Wraps `BearingDao`. Exposes `save(BearingRecord)`, `getAll()`, `getById(id)`, `delete(id)`. All operations are suspend functions on `Dispatchers.IO`. | P1.2, P1.3 | `BearingRepositoryTest`: CRUD round-trip, encryption verified (instrumented with SQLCipher) | M | ⬜ |
| **P6.3** | Capture button & dialog UI | Add floating action button (FAB) to `activity_compass.xml`. On tap: show `BearingCaptureDialogFragment` (name text field, bearing preview, optional notes). On confirm: invoke `BearingCaptureUseCase` + `BearingRepository.save()`. Show confirmation Toast "Bearing saved as '[name]'". If `interference_flag`, show warning before confirm. ≤3 taps from button to saved (FAB → dialog → confirm). | P6.1, P6.2 | `BearingCaptureFlowTest` (Espresso): full save flow, interference warning shown | M | ⬜ |
| **P6.4** | First-capture location privacy dialog | Track `first_capture_done` boolean in SharedPreferences. Before the first save, show dialog: "Your location will be attached to this bearing and stored only on your device. Location is optional." with toggle (default ON) and Confirm/Cancel. Store preference; never show again. | P6.3 | `BearingCaptureFlowTest`: first-capture dialog appears once, toggle off skips location | S | ⬜ |
| **P7.1** | Extreme latitude advisory | In `CompassViewModel`: after computing WMM inclination, if `abs(inclination_deg) ≥ 80°` add `EXTREME_LATITUDE` to `SensorAdvisory` set in `CompassUiState`. Cap `OverallConfidence` at `MODERATE`. Update UI to show advisory banner. | P4.1 | `CompassViewModelTest`: inclination ≥80° caps confidence, advisory flag set | S | ⬜ |
| **P7.2** | GPS unavailable flow | In `LocationRepository.getLocation()`: return `CachedFix` with age label when cache is valid; return `Unavailable` when no cache. In `CompassViewModel`: when `Unavailable`, offer manual entry dialog if user taps True North toggle; show "Magnetic N only" if dismissed. Display age label when `CachedFix`. | P3.1, P4.1 | `CompassViewModelTest`: unavailable triggers magnetic fallback; `BearingCaptureFlowTest`: bearing without GPS stores null lat/lon | M | ⬜ |
| **P8.1** | WMM unit tests | `Wmm2025ModelTest`, `AndroidGeoFieldModelTest`, `MagneticFieldModelProviderTest` — full coverage per TSPEC accuracy requirements (NOAA reference vectors). | P2.2, P2.3, P2.4 | Same files | M | ⬜ |
| **P8.2** | Location & capture unit tests | `LocationRepositoryTest`, `BearingCaptureUseCaseTest` — cover all `LocationResult` branches and all capture flag logic. | P3.1, P6.1 | Same files | M | ⬜ |
| **P8.3** | ViewModel unit tests (Phase 2 additions) | `CompassViewModelTest` additions: north toggle heading delta, advisory flags, extreme latitude, interference WMM baseline. | P4.1, P4.2, P7.1, P7.2 | `CompassViewModelTest` (modify existing) | M | ⬜ |
| **P8.4** | Integration tests | `LuopanDatabaseMigrationTest` (v1→v2 migration preserves calibration rows, new bearing table created). `BearingEncryptionTest` (raw SQLite read without passphrase fails). | P1.3, P6.2 | New instrumented test files | M | ⬜ |
| **P8.5** | UI / Espresso tests | `BearingCaptureFlowTest`, `NorthTypeToggleTest`, `LocationPermissionTest` — full end-to-end flows on emulator. | P3.2, P4.3, P6.3, P6.4 | New androidTest files | L | ⬜ |
| **P8.6** | Macrobenchmark — cold/warm startup | Add `:benchmark` module with `StartupBenchmark` measuring cold start (≤5 s) and warm start (≤3 s) to first heading (REQ-NFR-08). | P4.1 | `StartupBenchmark` in `:benchmark` module | M | ⬜ |

---

## 2. Dependency Graph

```
P1.1 ──────────────────────────────────────────────────────────────────┐
  │                                                                     │
  ├──► P2.2 ──► P2.4 ──► P4.1 ──► P4.2 ──► P8.3                      │
  │      ▲        ▲        │  │              │                          │
  │    P2.1     P2.3       │  │              └──► P8.2 (partial)        │
  │                        │  │                                          │
  │                        │  ├──► P4.3 ──► P8.5                        │
  │                        │  │                                          │
  │                        │  ├──► P5.1 ──► P5.2                        │
  │                        │  │                                          │
  │                        │  ├──► P6.1 ──► P6.3 ──► P6.4 ──► P8.5    │
  │                        │  │     │                                    │
  │                        │  │     └──► P8.2                           │
  │                        │  │                                          │
  │                        │  ├──► P7.1 ──► P8.3                        │
  │                        │  │                                          │
  │                        │  └──► P7.2 ──► P8.3                        │
  │                        │                                             │
  │                       P3.1 ──► P3.2 ──► P8.5                       │
  │                         ▲                                            │
P1.4 ──────────────────────►┘                                           │
                                                                         │
P1.2 ──► P1.3 ──► P6.2 ──► P6.3 ──► P6.4 ──────────────────────────  │
           │                 │                                           │
           │                 └──► P8.4                                   │
           │                                                             │
           └──────────────────────────────────────────────── P8.4 ◄─────┘

P8.6 depends on P4.1 (first heading available to benchmark)
```

**Legend:** `A ──► B` means B requires A to be complete first.

---

## 3. Parallelization Opportunities

The following groups of phases have no inter-dependencies and can be worked simultaneously by different engineers:

| Batch | Phases | Can start after | Notes |
|-------|--------|-----------------|-------|
| **Batch 0** (Foundation, truly parallel) | P1.1, P1.2, P1.4, P2.1 | Nothing | All are pure additions; P1.1 has no external deps; P1.2 and P2.1 are independent new artifacts |
| **Batch 1** | P1.3, P2.2, P2.3, P3.1 | P1.1 + P1.2 + P2.1 + P1.4 done | P1.3 needs P1.2; P2.2 needs P1.1+P2.1; P2.3 needs P1.1; P3.1 needs P1.4 (manifest) |
| **Batch 2** | P2.4, P3.2 | P2.2 + P2.3 + P3.1 done | P2.4 needs all three models; P3.2 needs P1.4 + P3.1 |
| **Batch 3** | P4.1, P8.1 | P2.4 + P3.1 done | P4.1 is the ViewModel integration hub; P8.1 tests the WMM layer |
| **Batch 4** | P4.2, P4.3, P5.1, P6.1, P7.1, P7.2, P8.2 (partial) | P4.1 done | P6.1 also needs P1.1; P7.x are independent once VM ready |
| **Batch 5** | P5.2, P6.2, P8.3 | P5.1 + P6.1 + P1.3 done | P6.2 needs the DB migration from P1.3; P8.3 tests the VM additions |
| **Batch 6** | P6.3, P8.4, P8.6 | P6.1 + P6.2 + P4.3 + P1.3 done | UI capture flow; DB integration tests; startup benchmark |
| **Batch 7** | P6.4, P8.5 | P6.3 + P3.2 done | Privacy dialog; full UI test suite |

---

## 4. Success Criteria per Phase

### P1.1 — Interfaces & Clock
- `MagneticFieldModel` interface compiles with `getDeclination(lat, lon, alt, epochYear)`, `getInclination(...)`, `getMagnitude(...)`, `isExpired(epochYear)` signatures.
- `Clock` interface has `nowMs(): Long`; `SystemClock` delegates to `System.currentTimeMillis()`.
- `ClockTest` passes: fake clock returns injected value.
- No production behavior changed.

### P1.2 — BearingRecord entity & DAO
- `BearingRecord` entity compiles with all REQ-CAPTURE-01 fields (id, name, bearing_deg, north_type, confidence, captured_at, calibration_version, field_deviation_pct, inclination_deviation_deg, interference_flag, lat?, lon?, alt?, notes?, display_mode?).
- `BearingDao` interface compiles with insert, getById, getAll, delete.
- `Migration(1,2)` SQL reviewed: adds `bearing_records` table without touching `calibration_records`.
- `BearingDaoTest` passes against in-memory database.

### P1.3 — DB schema export & migration wiring
- `LuopanDatabase` version = 2, `exportSchema = true`, schema JSON written to `app/schemas/`.
- `fallbackToDestructiveMigration()` removed; `addMigrations(MIGRATION_1_2)` added.
- `LuopanDatabaseMigrationTest` passes: opens a v1 database (pre-populated with a calibration row), runs migration, verifies calibration row survives, `bearing_records` table exists.

### P1.4 — Permissions
- `AndroidManifest.xml` contains both `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`.
- Existing lint rule `NoInternetPermission` still passes (INTERNET not added).

### P2.1 — WMM2025 COF file
- `res/raw/wmm2025_cof.txt` exists and is loadable via `resources.openRawResource(R.raw.wmm2025_cof)`.
- File header comment references NOAA public-domain license.
- First data line parses to correct `n=1, m=0` coefficients per NOAA WMM2025 publication.
- `Wmm2025CofResourceTest` passes.

### P2.2 — `Wmm2025Model`
- Implements `MagneticFieldModel`.
- For all 5 NOAA reference test vectors: declination within ±0.1°, inclination within ±0.1°, total field magnitude within ±100 nT.
- `isExpired(2030.0)` returns true; `isExpired(2029.9)` returns false.
- `Wmm2025ModelTest` passes all reference vector assertions.

### P2.3 — `AndroidGeoFieldModel`
- Implements `MagneticFieldModel`; delegates to `android.hardware.GeomagneticField`.
- `isExpired()` always returns false.
- `AndroidGeoFieldModelTest` passes (Robolectric; result within ±1° of expected for a known location).

### P2.4 — `MagneticFieldModelProvider`
- Returns `Wmm2025Model` when `!wmm.isExpired(epochYear)`.
- Returns `AndroidGeoFieldModel` when WMM is expired.
- `activeSourceLabel()` returns `"WMM2025 (valid to 2030)"` or `"Android model — may be less accurate"`.
- `MagneticFieldModelProviderTest` passes: both branches exercised with fake clock.

### P3.1 — `LocationRepository`
- `getLocation()` returns `GpsFix` when GPS available; `CachedFix(ageMs)` when cache ≤30 days old; `Unavailable` when no cache or cache expired.
- Manual entry accepted via `setManualLocation(lat, lon)`.
- Cache stored in SharedPreferences; survives process restart.
- `LocationRepositoryTest` passes all branches with fake clock and fake GPS provider.

### P3.2 — Permission request flow
- Rationale dialog shown when `shouldShowRequestPermissionRationale` is true.
- "Open Settings" dialog shown after permanent denial.
- `LocationPermissionTest` passes: grant path leads to GPS fix; deny path degrades gracefully (Magnetic N only).

### P4.1 — `CompassViewModel` true north integration
- `northType: StateFlow<NorthType>` exposed; `toggleNorthType()` function implemented.
- When `northType == TRUE` and a location is available, `heading_deg` in `CompassUiState` includes declination offset.
- `north_label` is `"True N"`, `"True N (manual location)"`, `"True N (cached location)"`, or `"Magnetic N"` as appropriate.
- `location_fallback_advisory` = true when using cached location.
- `fallback_mag_advisory` = true when using Android fallback model.
- `CompassViewModelTest` passes with all three location states (GPS, cached, unavailable) and both north types.

### P4.2 — Interference detector WMM baseline upgrade
- `InterferenceMetrics.expectedField_uT` populated from `MagneticFieldModel.getMagnitude()` when location is available.
- `InterferenceMetrics.expectedInclination_deg` populated from `MagneticFieldModel.getInclination()`.
- EMA baseline used as fallback when no location fix.
- `InterferenceDetectorTest` additions pass; `CompassViewModelTest` verifies field sourced from model.

### P4.3 — North type toggle UI
- Toggle button visible in `activity_compass.xml`.
- Tapping changes `northType` StateFlow and updates heading label within 200 ms.
- `NorthTypeToggleTest` (Espresso) passes: tap toggle → label reads "True N"; tap again → "Magnetic N".

### P5.1 — `DeclinationInfo` & population
- `DeclinationInfo` data class has all required fields.
- Coordinates masked to 2 decimal places (e.g., `37.42°N, 122.08°W`).
- `CompassViewModel` exposes `declinationInfo: StateFlow<DeclinationInfo?>`.
- `DeclinationInfoTest` passes: masking, source label, null when magnetic-only mode.

### P5.2 — Declination info panel UI
- Bottom sheet opens on info icon tap.
- Displays declination in `°E`/`°W` format plus decimal.
- Shows source label, masked coordinates, last-updated date.
- `DeclinationInfoPanelTest` (Espresso) passes: opens, correct source label visible.

### P6.1 — `BearingCaptureUseCase`
- `captured_at` set to `clock.nowMs()` (tap time), not sensor frame time.
- `interference_flag = true` when `confidence == POOR`.
- `north_type` matches `CompassViewModel.northType`.
- `BearingCaptureUseCaseTest` passes all cases including POOR confidence.

### P6.2 — `BearingRepository`
- `save(BearingRecord)` persists via `BearingDao`; `getAll()` returns saved record.
- `BearingEncryptionTest`: raw SQLite without passphrase throws `SQLiteException` (data is encrypted).
- `BearingRepositoryTest` passes CRUD round-trip.

### P6.3 — Capture button & dialog UI
- FAB visible in `activity_compass.xml`.
- Dialog shows current heading preview and name field.
- ≤3 taps from FAB to record saved (FAB → dialog → confirm button).
- Warning toast visible before confirm when `interference_flag = true`.
- `BearingCaptureFlowTest` (Espresso) passes happy path and interference warning path.

### P6.4 — First-capture privacy dialog
- Dialog shown exactly once (first save); never shown on subsequent saves.
- Toggle defaults to ON (location included); turning OFF saves bearing with null lat/lon.
- `BearingCaptureFlowTest` extended test case: dialog appears first time, not second time.

### P7.1 — Extreme latitude advisory
- When WMM inclination ≥ 80°: `CompassUiState` includes `EXTREME_LATITUDE` advisory.
- `confidence` capped at `MODERATE` regardless of other factors.
- Advisory banner visible in `activity_compass.xml`.
- `CompassViewModelTest` extreme latitude test passes.

### P7.2 — GPS unavailable flow
- Manual entry dialog offered when user taps True North toggle and `LocationResult == Unavailable`.
- If user enters coordinates: True N displayed with `"True N (manual location)"` label.
- If user dismisses: mode stays Magnetic N.
- `CompassViewModelTest` unavailable-GPS test passes.
- `BearingCaptureFlowTest`: bearing saved with null lat/lon when no GPS.

### P8.1 — WMM unit tests
- All NOAA reference vector assertions pass with tolerances from P2.2 success criteria.
- `MagneticFieldModelProviderTest` covers both model selections.
- Line coverage ≥ 90% for `com.luopan.compass.wmm` package.

### P8.2 — Location & capture unit tests
- All `LocationResult` branches covered in `LocationRepositoryTest`.
- `BearingCaptureUseCaseTest` covers interference flag, north type, tap-time timestamp.
- Line coverage ≥ 85% for `com.luopan.compass.location` and `com.luopan.compass.capture`.

### P8.3 — ViewModel unit tests (Phase 2 additions)
- North toggle heading delta test: heading changes by ±declination value.
- Extreme latitude advisory test: confidence capped at MODERATE.
- WMM interference baseline test: `expectedField_uT` sourced from model.
- All existing `CompassViewModelTest` tests continue to pass (no regression).

### P8.4 — Integration tests
- `LuopanDatabaseMigrationTest`: v1 calibration row survives v1→v2 migration; `bearing_records` table exists.
- `BearingEncryptionTest`: file-level encryption verified.
- Both tests run on emulator API 26+ (min SDK).

### P8.5 — UI / Espresso tests
- `BearingCaptureFlowTest`: FAB → dialog → save → toast, privacy dialog appears once.
- `NorthTypeToggleTest`: toggle label and heading update.
- `LocationPermissionTest`: grant and deny paths.
- All three test classes pass on emulator.

### P8.6 — Macrobenchmark
- `:benchmark` module added to `settings.gradle.kts` with `com.android.test` plugin.
- `StartupBenchmark.coldStart` median ≤ 5 000 ms.
- `StartupBenchmark.warmStart` median ≤ 3 000 ms.
- Benchmark results logged; CI step records them as build artifacts.

---

## 5. Key Implementation Notes

### 5.1 WMM2025 Licensing (Risk P2-R1)
Phase P2.1 is gated on confirming NOAA public-domain status. The COF file header must include the NOAA attribution comment. Do not proceed to P2.2 until confirmed.

### 5.2 `fallbackToDestructiveMigration` removal (P1.3)
This is a one-way change: once removed, any missing migration crashes on upgrade. The migration from v1→v2 must be correct before this is merged. The `LuopanDatabaseMigrationTest` in P8.4 is a hard gate.

### 5.3 EMA baseline vs WMM baseline (P4.2)
The `baselineFieldUt` EMA in `CompassViewModel` must remain as a fallback (for users who have never granted GPS). The WMM model is used as a higher-priority source when a location fix is available. The two code paths must not interfere.

### 5.4 `captured_at` semantics (P6.1)
`captured_at` must record the moment the user taps "Capture", not the most recent sensor frame timestamp. Use `Clock.nowMs()` injected at use-case construction time, called at the start of `execute()`.

### 5.5 Room schema export directory (P1.3)
Add to `app/build.gradle.kts` under `android { defaultConfig { ... } }`:
```kotlin
javaCompileOptions {
    annotationProcessorOptions {
        arguments["room.schemaLocation"] = "$projectDir/schemas"
    }
}
```
Commit the generated JSON schema file alongside P1.3.

### 5.6 `NorthType` enum placement
Define `NorthType` in `com.luopan.compass.model` (alongside `OverallConfidence`, `InterferenceState`, etc.) to avoid circular package dependencies.

### 5.7 `BearingRecord.north_type` storage
Store as `String` (Room enum mapping) consistent with `CalibrationRecord.quality`. Value set: `"MAGNETIC"`, `"TRUE"`.
