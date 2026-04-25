# PLAN-luopan-p2-true-north-capture
## Execution Plan — Phase 2: True North, Magnetic Declination, and Bearing Capture

| Field | Value |
|-------|-------|
| **Document ID** | PLAN-luopan-p2-true-north-capture |
| **Version** | 0.3 |
| **Date** | 2026-04-24 |
| **Revised** | 2026-04-24 (v0.3 — patch NEW-1/2/3/4/5: NEW-1 replace com.luopan.compass.wmm with com.luopan.compass.magnetic in P2.2/P2.3/P2.4/P5.1; NEW-2 replace com.luopan.compass.capture with com.luopan.compass.bearing in P6.1/P6.2; NEW-3 remove non-existent activeSourceLabel() from P2.4, note ViewModel derives label from getModelId()+isExpired(); NEW-4 replace save() with insert() and remove getById() in P6.2 per TSPEC §3.5; NEW-5 fix magnitude tolerance typo ±100 nT → ±200 nT in P2.2 NOAA pinned vector) | 2026-04-24 (v0.2 — addressed PM/TE cross-review findings: TE-P-01 add BearingForceStopTest to P8.4; TE-P-02/PM-F-01 fix interference_flag derivation in P6.1; TE-P-03 fix captured_at semantics in P6.1 and §5.4; TE-P-04 add NOAA pinned vector to P8.1; TE-P-05 move migration test gate before BearingRepository; TE-P-06/PM-F-02 rename SystemClock→WallClock throughout; TE-P-07 fix interface method signatures; TE-P-08 map 8 TSPEC test classes to phases; TE-P-09 expand P8.3 success criteria; TE-P-10 add DB filename to P8.4; PM-F-03 expand P6.3 warning dialog description; PM-F-04 add GRID exclusion guard; PM-F-05 move P6.4 into Batch 6 and clarify inline privacy notice; PM-F-06 fix north_label set in P4.1) |
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
| **P1.1** | Interfaces & Clock | Define `MagneticFieldModel` interface (`getDeclination(latDeg, lonDeg, altM, epochYears)`, `getExpectedInclination(latDeg, lonDeg, altM, epochYears)`, `getExpectedFieldMagnitude(latDeg, lonDeg, altM, epochYears)`, `isExpired()` — no parameters) and `Clock` interface (in `com.luopan.compass.util`). `WallClock` production impl wraps `System.currentTimeMillis()` (named `WallClock`, not `SystemClock`, to avoid collision with `android.os.SystemClock` — TSPEC §2.2). | — | `ClockTest` (unit) | S | ⬜ |
| **P1.2** | BearingRecord entity & DAO | Define `BearingRecord` Room entity with all REQ-CAPTURE-01 fields. Define `BearingDao` (insert, getById, getAll, delete). Write `Migration(1,2)` adding `bearing_records` table. | — | `BearingRecordTest`, `BearingDaoTest` (unit, in-memory) | M | ⬜ |
| **P1.3** | DB schema export & migration wiring | Enable `exportSchema = true` on `LuopanDatabase`; remove `fallbackToDestructiveMigration()`; register `Migration(1,2)`; add `BearingDao` abstract accessor; update `buildInMemory` helper. Configure `room.schemaLocation` in `build.gradle.kts`. | P1.2 | `LuopanDatabaseMigrationTest` (instrumented) | S | ⬜ |
| **P1.4** | Location permissions in Manifest | Add `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` `<uses-permission>` to `AndroidManifest.xml`. | — | (manifest lint check) | S | ⬜ |
| **P2.1** | Bundle WMM2025 coefficients | Create `app/src/main/res/raw/wmm2025_cof.txt` with NOAA WMM2025 COF-format coefficients (168 Gauss terms; valid 2025.0–2030.0). Add license comment header confirming NOAA public domain. | — | `Wmm2025CofResourceTest` (verifies resource loads, first coefficient parses correctly) | S | ⬜ |
| **P2.2** | `Wmm2025Model` implementation | Implement `Wmm2025Model : MagneticFieldModel` in `com.luopan.compass.magnetic`. Parse COF file once at construction. Implement spherical harmonic evaluation for declination, inclination, and total field magnitude at (lat, lon, alt, epochYear). `isExpired()` returns true when `epochYear ≥ 2030.0`. | P1.1, P2.1 | `Wmm2025ModelTest`: NOAA reference test vectors (≥5 geographic locations, ±0.1° accuracy for declination/inclination, ±200 nT for magnitude) | L | ⬜ |
| **P2.3** | `AndroidGeoFieldModel` fallback | Implement `AndroidGeoFieldModel : MagneticFieldModel` in `com.luopan.compass.magnetic`. Wraps `android.hardware.GeomagneticField`. `isExpired()` always returns false. | P1.1 | `AndroidGeoFieldModelTest`: verifies delegation to `GeomagneticField` for known location (Robolectric) | S | ⬜ |
| **P2.4** | `MagneticFieldModelProvider` | Implement `MagneticFieldModelProvider` in `com.luopan.compass.magnetic`. Selects `Wmm2025Model` when not expired; falls back to `AndroidGeoFieldModel`. No `activeSourceLabel()` method — the ViewModel derives the source label from `getModelId()` and `isExpired()`. Inject `Clock` for testability. | P1.1, P2.2, P2.3 | `MagneticFieldModelProviderTest`: expired/non-expired switching | S | ⬜ |
| **P3.1** | `LocationRepository` | Implement `LocationRepository` in `com.luopan.compass.location`. Responsibilities: (1) request fresh GPS fix via `FusedLocationProviderClient`; (2) cache last fix with timestamp in SharedPreferences (`luopan_location_cache`); (3) expose `getLocation(): LocationResult` sealed class (`GpsFix`, `CachedFix(ageMs)`, `ManualEntry`, `Unavailable`); (4) accept manual lat/lon entry; (5) evict cache older than 30 days. | — | `LocationRepositoryTest`: cached/GPS/manual/unavailable paths; cache eviction (unit, fake clock) | M | ⬜ |
| **P3.2** | Permission request flow | In `CompassActivity`: request `ACCESS_FINE_LOCATION` on first True North activation using `ActivityResultContracts.RequestMultiplePermissions`. Show rationale dialog (Material AlertDialog) before request if `shouldShowRequestPermissionRationale` is true. On permanent denial, show "Open Settings" dialog. | P1.4, P3.1 | `LocationPermissionTest` (UI/Espresso): grant path, deny path, rationale shown | M | ⬜ |
| **P4.1** | `CompassViewModel` — true north integration | Inject `MagneticFieldModelProvider` and `LocationRepository` into `CompassViewModel` (constructor injection via factory). Add `northType: StateFlow<NorthType>` (`MAGNETIC` / `TRUE`). When `TRUE`, apply declination offset to `heading_deg`. Populate `CompassUiState.north_label` correctly. Populate `location_fallback_advisory` (cached location) and `fallback_mag_advisory` (Android fallback model). | P1.1, P2.4, P3.1 | `CompassViewModelTest`: north toggle changes heading, advisory flags set correctly (unit, fake dependencies) | M | ⬜ |
| **P4.2** | Interference detector — WMM baseline upgrade | Update `CompassViewModel.startSensorCollection()`: when `MagneticFieldModelProvider` has a location fix, populate `InterferenceMetrics.expectedField_uT` from `MagneticFieldModel.getExpectedFieldMagnitude(latDeg, lonDeg, altM, epochYears)` and `expectedInclination_deg` from `MagneticFieldModel.getExpectedInclination(latDeg, lonDeg, altM, epochYears)` instead of EMA. Keep EMA fallback when no location is available. | P2.4, P3.1, P4.1 | `InterferenceDetectorTest`: add WMM-baseline test cases; `CompassViewModelTest`: verify `expectedField_uT` comes from model when location available | M | ⬜ |
| **P4.3** | North type toggle UI | Add toggle button (Material `Button` or `ToggleButton`) to `activity_compass.xml`. Wire `onClick` → `CompassViewModel.toggleNorthType()`. Observe `northType` StateFlow; update button label and heading label. Toggle MUST be binary TRUE/MAGNETIC only; GRID label MUST NOT appear anywhere in the UI (AT-G-08). | P4.1 | `NorthTypeToggleTest` (Espresso): tap toggle, assert heading label changes | S | ⬜ |
| **P5.1** | `DeclinationInfo` data class & population | Define `DeclinationInfo(declination_deg, source_label, lat_masked, lon_masked, last_updated, valid_until)` in `com.luopan.compass.magnetic`. Populate in `CompassViewModel` from `MagneticFieldModelProvider` + `LocationRepository`. Expose as `StateFlow<DeclinationInfo?>`. | P4.1 | `DeclinationInfoTest`: field masking to 2 dp, source label propagation (unit) | S | ⬜ |
| **P5.2** | Declination info panel UI | Implement bottom sheet (`BottomSheetDialogFragment`) showing all `DeclinationInfo` fields. Trigger from info icon near heading label in `activity_compass.xml`. Dismiss on tap-outside. | P5.1 | `DeclinationInfoPanelTest` (Espresso): opens on icon tap, shows correct source label | S | ⬜ |
| **P6.1** | `BearingCaptureUseCase` | Implement `BearingCaptureUseCase` in `com.luopan.compass.bearing`. Inputs: `BearingSnapshot` (immutable snapshot created at tap time by `CompassViewModel`), `LocationRepository`, `Clock`. Constructs `BearingRecord`; sets `captured_at` to `snapshot.tapTimestampMs` (the wall-clock ms recorded by `CompassViewModel.captureBearing()` via `clock.nowMs()` BEFORE any dialog is shown — the use case does NOT call `clock.nowMs()` for `captured_at`); sets `interference_flag = true` when `snapshot.interferenceState ∈ {MODERATE, WARNING}` — `OverallConfidence.POOR` alone does NOT set this flag (FSPEC BR-10, AT-E-10); sets `north_type` from active `NorthType`. Throws `IllegalStateException` if `snapshot.northType == GRID`. Returns persisted `BearingRecord`. | P1.1, P4.1 | `BearingCaptureUseCaseTest`: interference_flag set when MODERATE or WARNING; interference_flag=false when POOR+CLEAR (AT-E-10); captured_at equals snapshot.tapTimestampMs not execute-time clock; north_type propagated; GRID throws | M | ⬜ |
| **P6.2** | `BearingRepository` | Implement `BearingRepository` in `com.luopan.compass.bearing`. Wraps `BearingDao`. Exposes `insert(BearingRecord)`, `count()`, `getAll()`, `delete(id)`. All operations are suspend functions on `Dispatchers.IO`. | P1.2, P1.3 | `BearingRepositoryTest`: CRUD round-trip, encryption verified (instrumented with SQLCipher) | M | ⬜ |
| **P6.3** | Capture button & dialog UI | Add floating action button (FAB) to `activity_compass.xml`. On tap: (1) `CompassViewModel.captureBearing()` records `tapTimestampMs = clock.nowMs()` immediately before any dialog is shown; (2) pre-capture warning dialog (`InterferenceWarningDialogFragment`) is shown when `InterferenceState ∈ {MODERATE, WARNING}` OR `OverallConfidence == POOR` (FSPEC §2.5 step 3b) — warning dialog precedes name/notes dialog; "Cancel" abandons capture entirely; (3) show `BearingCaptureDialogFragment` (name text field, bearing preview, optional notes, GPS toggle with first-capture privacy notice inline — see P6.4); (4) on confirm: invoke `BearingCaptureUseCase` + `BearingRepository.insert()`; show confirmation Toast "Bearing saved as '[name]'". ≤3 taps from button to saved (FAB → dialog → confirm). `north_type` assertion: `IllegalStateException` thrown if `north_type == GRID` before DB write (AT-G-08). | P6.1, P6.2, P6.4 | `BearingCaptureFlowTest` (Espresso): full save flow, interference warning shown; AT-E-10 case: POOR confidence + CLEAR interference → interference_flag=false; `InterferenceWarningCaptureTest`: MODERATE → warning dialog → interference_flag=true | M | ⬜ |
| **P6.4** | First-capture location privacy notice (inline) | Track `bearing_location_consent_shown` boolean in SharedPreferences. The GPS toggle and first-capture privacy notice ("Your location will be attached to this bearing and stored only on your device. Location is optional.") are displayed INLINE inside `BearingCaptureDialogFragment` (not as a separate preceding modal — FSPEC §2.5 step 4, BR-15). The notice `TextView` is shown/hidden based on `bearing_location_consent_shown`; the GPS toggle (`MaterialSwitch`) is always present with default ON. On first-ever confirm: mark `bearing_location_consent_shown = true`; never show notice again. P6.4 must be implemented alongside P6.3 in the same batch — the capture dialog must never ship without the privacy toggle/notice. | P6.1, P6.2 | `BearingCaptureFlowTest`: first-capture privacy notice visible on first open, absent on subsequent opens; GPS toggle OFF saves bearing with null lat/lon | S | ⬜ |
| **P7.1** | Extreme latitude advisory | In `CompassViewModel`: after computing WMM inclination, if `abs(inclination_deg) ≥ 80°` add `EXTREME_LATITUDE` to `SensorAdvisory` set in `CompassUiState`. Cap `OverallConfidence` at `MODERATE`. Update UI to show advisory banner. | P4.1 | `CompassViewModelTest`: inclination ≥80° caps confidence, advisory flag set | S | ⬜ |
| **P7.2** | GPS unavailable flow | In `LocationRepository.getLocation()`: return `CachedFix` with age label when cache is valid; return `Unavailable` when no cache. In `CompassViewModel`: when `Unavailable`, offer manual entry dialog if user taps True North toggle; show "Magnetic N only" if dismissed. Display age label when `CachedFix`. | P3.1, P4.1 | `CompassViewModelTest`: unavailable triggers magnetic fallback; `BearingCaptureFlowTest`: bearing without GPS stores null lat/lon | M | ⬜ |
| **P8.1** | WMM unit tests | `Wmm2025ModelTest`, `AndroidGeoFieldModelTest`, `MagneticFieldModelProviderTest`, `WmmDeclinationAccuracyTest` — full coverage per TSPEC accuracy requirements. Primary NOAA-pinned vector (TSPEC §10.1 TE-T-01, mandatory): `lat=40.0°N, lon=−105.0°W, altM=0.0, epochYears=2025.5` — assert `getDeclination() == +8.93° ± 0.1°`, `getExpectedInclination() == +66.0° ± 0.5°`, `getExpectedFieldMagnitude() == 52300 nT ± 200 nT`. This vector must be the first assertion and must NOT be replaced by a live NOAA calculator call. | P2.2, P2.3, P2.4 | `Wmm2025ModelTest`, `AndroidGeoFieldModelTest`, `MagneticFieldModelProviderTest`, `WmmDeclinationAccuracyTest` | M | ⬜ |
| **P8.2** | Location & capture unit tests | `LocationRepositoryTest`, `BearingCaptureUseCaseTest`, `LocationCacheAgeLabelTest`, `NoGpsDialogTest`, `InterferenceBaselineUpgradeTest` — cover all `LocationResult` branches, cache age label formatting (AT-C), GPS-unavailable manual-entry dialog (AT-D), and all capture flag logic including POOR+CLEAR → interference_flag=false (AT-E-10). | P3.1, P6.1 | `LocationRepositoryTest`, `BearingCaptureUseCaseTest`, `LocationCacheAgeLabelTest`, `NoGpsDialogTest`, `InterferenceBaselineUpgradeTest` | M | ⬜ |
| **P8.3** | ViewModel unit tests (Phase 2 additions) | `CompassViewModelTest` additions per TSPEC §10.1 TE-T-04 (all four mandatory cases): (a) 200 ms heading budget — inject `FakeSensorFusion`; set `northType=TRUE`; assert heading reflects true north within one coroutine dispatch (≤50 ms); (b) 60 s WMM expiry debounce — call `checkWmmExpiry()` twice within 59 999 ms (`FakeClock.advance`); assert `isExpired()` called only once; advance to 60 001 ms; assert second call; (c) `captureButtonEnabled` BR-CAP-08 — call `captureBearing()` with slow `FakeBearingCaptureUseCase`; assert `captureButtonEnabled=false` during suspend, `true` after; (d) extreme latitude confidence cap — inject `FakeMagneticFieldModel` returning `inclination=80.0°`; assert `extremeLatitudeAdvisory=true` and `confidence==MODERATE`. | P4.1, P4.2, P7.1, P7.2 | `CompassViewModelTest` (modify existing) | M | ⬜ |
| **P8.4** | Integration tests | `LuopanDatabaseMigrationTest` (v1→v2 migration preserves calibration rows, new bearing table created — **hard gate**: `LuopanDatabaseMigrationTest` must pass before P6.2 `BearingRepository` is implemented; see §5.2). `BearingEncryptionTest` (raw SQLite read without passphrase fails; uses `getDatabasePath("bearing_records.db")` — canonical DB name, TE-T-02). `BearingForceStopTest` (AT-PERSIST-02: force-stop resilience via `ProcessPhoenix`; on relaunch row count is pre-kill or pre-kill+1; no record with null required fields). | P1.3, P6.2 | `LuopanDatabaseMigrationTest`, `BearingEncryptionTest`, `BearingForceStopTest` | M | ⬜ |
| **P8.5** | UI / Espresso tests | `BearingCaptureFlowTest`, `NorthTypeToggleTest`, `LocationPermissionTest`, `GridNorthAbsenceTest` (AT-G-08: asserts no view with text "Grid" or "GRID" in main screen or capture dialog), `LocationPermissionRationaleTest` (BR-LOC-04: rationale dialog shown before system permission dialog), `PermissionDeniedCaptureTest` (AT-G-09: permission denied → manual coord dialog → capture saves null lat/lon), `NoGpsDialogTest` (AT-D: `LocationState.Unavailable` → manual entry dialog on True North toggle) — full end-to-end flows on emulator. | P3.2, P4.3, P6.3, P6.4 | `BearingCaptureFlowTest`, `NorthTypeToggleTest`, `LocationPermissionTest`, `GridNorthAbsenceTest`, `LocationPermissionRationaleTest`, `PermissionDeniedCaptureTest`, `NoGpsDialogTest` | L | ⬜ |
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
P1.2 ──► P1.3 ──► P6.2 ──► P6.3 ──────────────────────────────────  │
           │           │      │                                          │
           │           │      ├──► P6.4 (same batch; inline in dialog)  │
           │           │      │                                          │
           │           └──► P8.4 (LuopanDatabaseMigrationTest gates P6.2)│
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
| **Batch 4** | P4.2, P4.3, P5.1, P6.1, P7.1, P7.2, P8.2 (partial), **LuopanDatabaseMigrationTest (P8.4 gate)** | P4.1 done | P6.1 also needs P1.1; P7.x are independent once VM ready; `LuopanDatabaseMigrationTest` must pass in this batch as a hard gate before P6.2 (TE-P-05 / §5.2) |
| **Batch 5** | P5.2, P6.2, P8.3 | P5.1 + P6.1 + P1.3 done + `LuopanDatabaseMigrationTest` passed | P6.2 needs the DB migration from P1.3 AND the migration test gate from Batch 4; P8.3 tests the VM additions |
| **Batch 6** | P6.3, P6.4, P8.4, P8.6 | P6.1 + P6.2 + P4.3 + P1.3 done | UI capture flow; P6.4 (inline privacy notice) moves here alongside P6.3 — capture dialog must never ship without privacy toggle (PM-F-05); full DB integration tests; startup benchmark |
| **Batch 7** | P8.5 | P6.3 + P6.4 + P3.2 done | Full UI test suite |

---

## 4. Success Criteria per Phase

### P1.1 — Interfaces & Clock
- `MagneticFieldModel` interface compiles with canonical TSPEC §2.1 signatures: `getDeclination(latDeg, lonDeg, altM, epochYears): Float`, `getExpectedInclination(latDeg, lonDeg, altM, epochYears): Float`, `getExpectedFieldMagnitude(latDeg, lonDeg, altM, epochYears): Float`, `isExpired(): Boolean` (no parameters — epoch year is computed internally via the injected `Clock`), `getModelId(): String`.
- `Clock` interface has `nowMs(): Long`; production implementation is named `WallClock` (NOT `SystemClock` — avoids collision with `android.os.SystemClock`; TSPEC §2.2); `WallClock` delegates to `System.currentTimeMillis()`.
- `ClockTest` passes: fake clock (`FakeClock`) returns injected value; `FakeClock.advance(ms)` and `FakeClock.set(ms)` work correctly.
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
- Implements `MagneticFieldModel` with TSPEC §2.1 canonical method signatures.
- **Primary NOAA-pinned vector (TE-T-01 mandatory first assertion):** `lat=40.0°N, lon=−105.0°W, altM=0.0, epochYears=2025.5` — `getDeclination() == +8.93° ± 0.1°`; `getExpectedInclination() == +66.0° ± 0.5°`; `getExpectedFieldMagnitude() == 52300 nT ± 200 nT`. Must not be replaced with a live NOAA calculator call.
- For ≥5 NOAA reference test vectors (including the pinned primary): declination within ±0.1°, inclination within ±0.5°, total field magnitude within ±200 nT.
- `isExpired()` (no parameters — uses injected `Clock` internally): returns `true` when `FakeClock` set to epoch year ≥ 2030.0 or < 2025.0; returns `false` when 2027.0.
- `Wmm2025ModelTest` passes all reference vector assertions.

### P2.3 — `AndroidGeoFieldModel`
- Implements `MagneticFieldModel` with TSPEC §2.1 canonical method signatures (`getExpectedFieldMagnitude`, `getExpectedInclination`, `getDeclination`); delegates to `android.hardware.GeomagneticField`.
- `isExpired()` (no parameters) always returns `false`.
- `AndroidGeoFieldModelTest` passes (Robolectric; result within ±1° of expected for a known location).

### P2.4 — `MagneticFieldModelProvider`
- Returns `Wmm2025Model` when `!wmm.isExpired()` (no parameters — model checks its own expiry internally via injected `Clock`).
- Returns `AndroidGeoFieldModel` when WMM is expired.
- No `activeSourceLabel()` method is exposed. The ViewModel derives the source label string (e.g., `"WMM2025 (valid to 2030)"` or `"Android model — may be less accurate"`) by inspecting `activeModel().getModelId()` and `activeModel().isExpired()` (TSPEC §3.3).
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
- `north_label` has exactly three valid values (FSPEC §2.2 FSPEC-TOGGLE, §5.3): `"True N"` (for both GPS fresh and cached GPS location), `"True N (manual location)"` (for manual coordinate entry), and `"Magnetic N"`. `"True N (cached location)"` is NOT a valid label — cached-vs-fresh distinction is surfaced in the declination info panel source label, not the heading label.
- `location_fallback_advisory` = true when using cached location.
- `fallback_mag_advisory` = true when using Android fallback model.
- `CompassViewModelTest` passes with all three location states (GPS, cached, unavailable) and both north types.

### P4.2 — Interference detector WMM baseline upgrade
- `InterferenceMetrics.expectedField_uT` populated from `MagneticFieldModel.getExpectedFieldMagnitude(latDeg, lonDeg, altM, epochYears)` when location is available.
- `InterferenceMetrics.expectedInclination_deg` populated from `MagneticFieldModel.getExpectedInclination(latDeg, lonDeg, altM, epochYears)`.
- EMA baseline used as fallback when no location fix.
- `InterferenceDetectorTest` additions pass; `CompassViewModelTest` verifies field sourced from model.

### P4.3 — North type toggle UI
- Toggle button visible in `activity_compass.xml`.
- Toggle is binary TRUE/MAGNETIC only; GRID label does not appear anywhere in the UI or view hierarchy (`GridNorthAbsenceTest` — AT-G-08).
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
- `captured_at` is set to `snapshot.tapTimestampMs` — the wall-clock milliseconds recorded by `CompassViewModel.captureBearing()` via `clock.nowMs()` BEFORE any dialog is shown. The use case does NOT call `clock.nowMs()` for `captured_at`. Verified by `BearingCaptureUseCaseTest`: set `FakeClock` to T=1000 at snap time and T=15000 at execute time; assert `captured_at == 1000` (TSPEC §3.6 PM-T-01, FSPEC BR-09).
- `interference_flag = true` when `InterferenceState ∈ {MODERATE, WARNING}` at tap time; `interference_flag = false` when `InterferenceState == CLEAR`, even if `OverallConfidence == POOR` (FSPEC BR-10, AT-E-10). `OverallConfidence.POOR` alone does NOT set this flag.
- `north_type` matches `CompassViewModel.northType`; throws `IllegalStateException` if `northType == GRID`.
- `BearingCaptureUseCaseTest` passes all cases: MODERATE→flag=true; WARNING→flag=true; POOR+CLEAR→flag=false (AT-E-10); tapTimestampMs assertion; GRID guard.

### P6.2 — `BearingRepository`
- `insert(BearingRecord)` persists via `BearingDao`; `getAll()` returns saved record. (`getById()` is not part of the TSPEC §3.5 interface and must not be added.)
- `BearingEncryptionTest`: raw SQLite without passphrase throws `SQLiteException` (data is encrypted).
- `BearingRepositoryTest` passes CRUD round-trip.

### P6.3 — Capture button & dialog UI
- FAB visible in `activity_compass.xml`.
- Tap sequence: (1) `tapTimestampMs` recorded before any dialog; (2) pre-capture warning dialog (`InterferenceWarningDialogFragment`) shown when `InterferenceState ∈ {MODERATE, WARNING}` OR `OverallConfidence == POOR` (FSPEC §2.5 step 3b); "Cancel" abandons capture entirely; (3) `BearingCaptureDialogFragment` shows heading preview, name field, notes, GPS toggle with first-capture privacy notice inline (P6.4).
- POOR confidence + CLEAR interference → pre-capture warning shown but `interference_flag = false` in saved record (AT-E-10); `BearingCaptureFlowTest` covers this case.
- `north_type` assertion: `IllegalStateException` thrown before DB write if `north_type == GRID` (AT-G-08).
- ≤3 taps from FAB to record saved (FAB → dialog → confirm button).
- `BearingCaptureFlowTest` (Espresso) passes happy path and interference warning path.
- `InterferenceWarningCaptureTest` (Espresso): MODERATE state → warning dialog appears → "Save with warning" → `interference_flag = true` in DB.

### P6.4 — First-capture location privacy notice (inline)
- Privacy notice is displayed INLINE inside `BearingCaptureDialogFragment` — NOT as a separate preceding modal (FSPEC §2.5 step 4, BR-15).
- GPS toggle (`MaterialSwitch`, default ON) is always present in the capture dialog.
- First-capture privacy notice `TextView` is shown on the first capture (when `bearing_location_consent_shown == false`); hidden on all subsequent captures.
- On first-ever confirm: `bearing_location_consent_shown = true` written to SharedPreferences; notice never shown again.
- GPS toggle OFF → bearing saved with `lat = null`, `lon = null`, `alt_m = null`.
- Implemented in the same batch as P6.3; capture dialog MUST NOT ship without privacy toggle.
- `BearingCaptureFlowTest` extended test case: privacy notice visible on first open, absent on second open; GPS toggle OFF skips location.

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
- **Primary NOAA-pinned vector (mandatory first assertion):** WMM test uses pinned vector `lat=40.0°N, lon=−105.0°W, altM=0.0, epochYears=2025.5` — asserts `getDeclination() == +8.93° ± 0.1°`, `getExpectedInclination() == +66.0° ± 0.5°`, `getExpectedFieldMagnitude() == 52300 nT ± 200 nT` (TSPEC §10.1 TE-T-01; REQ §8 TE-REQ-02). Must not be replaced with a live NOAA calculator call.
- All NOAA reference vector assertions pass with tolerances from P2.2 success criteria.
- `MagneticFieldModelProviderTest` covers both model selections (WMM and fallback).
- `WmmDeclinationAccuracyTest` (AT-B): verifies `CompassViewModel` correctly propagates model declination to `CompassUiState.declination_deg` using `FakeMagneticFieldModel` with NOAA-pinned values.
- Line coverage ≥ 90% for `com.luopan.compass.magnetic` package.

### P8.2 — Location & capture unit tests
- All `LocationResult` branches covered in `LocationRepositoryTest`.
- `BearingCaptureUseCaseTest` covers interference flag (MODERATE→true, WARNING→true, POOR+CLEAR→false per AT-E-10), north type, `tapTimestampMs` assertion (PM-T-01), GRID guard.
- `LocationCacheAgeLabelTest` (AT-C): `FakeClock` set to exactly 15 × 86 400 000 ms after cache timestamp; assert cache age label = 15 whole days (floor division — no ±1 day tolerance).
- `NoGpsDialogTest` (AT-D): `FakeLocationRepository` emits `LocationState.Unavailable`; tap True North toggle; assert manual coordinate entry dialog appears; mode stays MAGNETIC until coordinates confirmed.
- `InterferenceBaselineUpgradeTest` (AT-F): inject two `FakeMagneticFieldModel` configurations (WMM vs. GeomagneticField-equivalent values); assert `InterferenceState` differs between configurations for the same sensor input.
- Line coverage ≥ 85% for `com.luopan.compass.location` and `com.luopan.compass.bearing`.

### P8.3 — ViewModel unit tests (Phase 2 additions)
Four mandatory cases per TSPEC §10.1 TE-T-04 (all using `FakeMagneticFieldModel`, `FakeClock`, `TestCoroutineDispatcher`):
- **(a) 200 ms heading budget:** inject `FakeSensorFusion`; set `northType=TRUE`; inject `FakeMagneticFieldModel` returning `declination=8.93°`; assert `CompassUiState.heading` reflects true heading within one coroutine dispatch cycle (≤50 ms, well within the 200 ms budget).
- **(b) 60 s WMM expiry debounce:** call `checkWmmExpiry()` twice within 59 999 ms (`FakeClock.advance(59_999L)`); assert `isExpired()` called only once; advance to 60 001 ms; call `checkWmmExpiry()` again; assert `isExpired()` called a second time.
- **(c) `captureButtonEnabled` BR-CAP-08:** call `captureBearing(snapshot)` with slow `FakeBearingCaptureUseCase` that suspends; assert `captureButtonEnabled == false` during suspend; assert `captureButtonEnabled == true` after completion.
- **(d) Extreme latitude confidence cap:** inject `FakeMagneticFieldModel` returning `inclination=80.0°`; trigger WMM evaluation; assert `extremeLatitudeAdvisory == true` and `CompassUiState.confidence == MODERATE` even when all Phase 1 confidence dimensions are Good.
- North toggle heading delta test: heading changes by ±declination value.
- WMM interference baseline test: `expectedField_uT` sourced from model (not EMA) when location available.
- All existing `CompassViewModelTest` tests continue to pass (no regression).

### P8.4 — Integration tests
- `LuopanDatabaseMigrationTest`: v1 calibration row survives v1→v2 migration; `bearing_records` table exists with correct schema (15 columns, correct types and nullability). **Hard gate for P6.2:** this test must pass before `BearingRepository` is implemented (§5.2; TE-P-05).
- `BearingEncryptionTest`: raw file read of `targetContext.getDatabasePath("bearing_records.db")` without passphrase does not expose `"SQLite format 3"` plaintext header (AT-PERSIST-01). All raw-file database access in tests MUST use `getDatabasePath("bearing_records.db")` — the name `"luopan.db"` must not appear in any Phase 2 test (TSPEC §4.5 normative, TE-T-02).
- `BearingForceStopTest` (AT-PERSIST-02): uses `ProcessPhoenix` to kill and restart the process mid-insert; on relaunch row count is pre-kill or pre-kill+1; no record with any required (non-null) field null exists.
- All three tests run on emulator API 26+ (min SDK).

### P8.5 — UI / Espresso tests
- `BearingCaptureFlowTest`: FAB → dialog → save → toast, inline privacy notice appears once; AT-E-10 case: POOR+CLEAR → warning shown but `interference_flag=false`.
- `NorthTypeToggleTest`: toggle label and heading update within IdlingResource timeout.
- `LocationPermissionTest`: grant path leads to GPS fix; deny path degrades to Magnetic N.
- `GridNorthAbsenceTest` (AT-G-08): asserts no view with text containing "Grid" or "GRID" exists in main screen or capture dialog view hierarchy.
- `LocationPermissionRationaleTest` (BR-LOC-04): `shouldShowRequestPermissionRationale` returns `true`; tap True North toggle; assert in-app rationale dialog appears BEFORE system permission dialog; "Not now" → system dialog not shown, toggle stays Magnetic N.
- `PermissionDeniedCaptureTest` (AT-G-09): revoke location permission; tap True North toggle; assert manual coordinate dialog; dismiss; tap capture; save; assert `lat=null`, `lon=null` in record; GPS toggle absent from capture dialog.
- All test classes pass on emulator API 26+.

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
This is a one-way change: once removed, any missing migration crashes on upgrade. The migration from v1→v2 must be correct before this is merged. `LuopanDatabaseMigrationTest` is a hard gate that MUST pass before P6.2 (`BearingRepository`) is implemented — migration correctness gates the data layer. The test must be executed in Batch 4 (before P6.2 in Batch 5). See TE-P-05.

### 5.3 EMA baseline vs WMM baseline (P4.2)
The `baselineFieldUt` EMA in `CompassViewModel` must remain as a fallback (for users who have never granted GPS). The WMM model is used as a higher-priority source when a location fix is available. The two code paths must not interfere.

### 5.4 `captured_at` semantics (P6.1)
`captured_at` must equal `snapshot.tapTimestampMs` — the wall-clock milliseconds recorded by `CompassViewModel.captureBearing()` via `clock.nowMs()` BEFORE any dialog is shown, and carried through `BearingSnapshot`. `BearingCaptureUseCase.execute()` reads this value from the snapshot — it does NOT call `clock.nowMs()` for `captured_at`. This ensures a user who taps at 10:00:00 and types a name for 14 seconds still sees the record labeled 10:00:00 in the Phase 4 history screen. (TSPEC §3.6 PM-T-01, FSPEC BR-09; see also TE-P-03.)

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

### 5.8 Production `Clock` implementation naming
The production `Clock` implementation MUST be named `WallClock` — NOT `SystemClock`. `android.os.SystemClock` is imported in virtually every Android file; a class named `SystemClock` in `com.luopan.compass.util` would silently shadow it, causing confusing resolution errors. `WallClock` is the TSPEC §2.2 mandated name. The pattern `class Foo(private val clock: Clock = WallClock())` with a default parameter is prohibited — all `Clock` constructor parameters must be injected with no default. (See TE-P-06 / PM-F-02.)

### 5.9 `BearingForceStopTest` — `ProcessPhoenix` dependency (P8.4)
`BearingForceStopTest` (AT-PERSIST-02) uses `ProcessPhoenix` to kill and restart the process mid-insert. Ensure `ProcessPhoenix` is declared in `androidTestImplementation` in `app/build.gradle.kts` before P8.4 work begins. The library is already listed in TSPEC §10.2 androidTest dependencies.
