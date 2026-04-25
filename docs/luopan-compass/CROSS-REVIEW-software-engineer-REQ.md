# Cross-Review: software-engineer — REQ

**Reviewer:** software-engineer  
**Document reviewed:** docs/luopan-compass/REQ-luopan-p2-true-north-capture.md  
**Date:** 2026-04-24  
**Iteration:** 1  

---

## Summary

The Phase 2 REQ is well-structured and correctly scopes WMM2025 true-north correction, declination management, and bearing capture. Most acceptance criteria are implementable against the existing Phase 1 architecture. However, several significant gaps remain: the `InterferenceDetector` hardwires a self-calibrating baseline that is incompatible with the WMM baseline upgrade required by Scenario F; the `ACCESS_FINE_LOCATION` permission is absent from `AndroidManifest.xml` and the lint "no internet" guard may conflict with background GPS callbacks; the `LuopanDatabase` schema uses `fallbackToDestructiveMigration()` which will silently destroy bearing records when the schema evolves; and `REQ-NFR-08` cold-start targets are stated without a measurement definition, making them unverifiable. These issues need resolution before the TSPEC can be written.

---

## Findings

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| F-01 | High | `InterferenceDetector` computes `expectedField_uT` and `expectedInclination_deg` using a self-calibrating rolling EMA against the live sensor stream (see `CompassViewModel` lines 113–128) — not from any external geomagnetic model. Scenario F / §5.1 upgrade requires these values to come from WMM2025. The REQ must specify the exact contract: does `InterferenceMetrics.expectedField_uT` become the WMM-predicted value, or does a new parameter inject the WMM baseline into `InterferenceDetector`? Without this contract, the §5.1 upgrade note is unimplementable as written. | §5.1 upgrade note; Scenario F |
| F-02 | High | `AndroidManifest.xml` declares only `WAKE_LOCK`. Neither `ACCESS_FINE_LOCATION` nor `ACCESS_COARSE_LOCATION` is present. REQ-NORTH-03, REQ-CAPTURE-06, and the GPS fallback chain in Scenarios C/D all require at minimum `ACCESS_COARSE_LOCATION`; Scenario E saves GPS coordinates which implies `ACCESS_FINE_LOCATION`. The REQ must specify which permission level is required and whether the runtime permission request happens at app launch or lazily on first toggle to True North. The current custom lint rule (`NoInternetPermissionCheck`) only checks for INTERNET — it will not catch a missing location permission during CI. | REQ-NORTH-03; REQ-CAPTURE-06; Risk P2-R2 |
| F-03 | High | `LuopanDatabase` is built with `.fallbackToDestructiveMigration()`. Adding the `BearingRecord` entity (REQ-CAPTURE-01) requires a Room schema version bump to v2. On existing Phase 1 installs, `fallbackToDestructiveMigration()` will DROP `calibration_records` along with any future bearing records during upgrade, destroying user calibration data. The REQ must require that the migration strategy be defined before the BearingRecord entity is shipped, and must prohibit destructive migration in production builds for existing entities. | REQ-CAPTURE-04 |
| F-04 | High | `REQ-CAPTURE-01` lists `calibration_version` as a required field in the bearing schema, but `CalibrationRecord` has no version string — it uses only an integer primary key (`id: Int`, values 1 or 2 for current/rollback) and `recorded_at: Long`. There is no stable version identifier to capture. The REQ must define what `calibration_version` resolves to (e.g., `recorded_at` timestamp, a new UUID field on `CalibrationRecord`, or a separate app-level build version) and the `CalibrationRecord` entity may need to be extended. | REQ-CAPTURE-01 |
| F-05 | Medium | `REQ-NFR-08` specifies cold-start as "≤3 s warm cache / ≤5 s cold" but does not define: (a) the start event (OS delivers `Intent` vs. `Activity.onCreate`), (b) the end event ("first heading" — first non-`---` value in `heading_formatted`, or first `OverallConfidence != POOR`?), (c) the device class (low-end vs. flagship). WMM2025 coefficient parsing on the main thread during cold start is a real risk — 168 Gauss coefficients evaluated via a Legendre polynomial require measurable CPU time. Without a measurement definition this criterion cannot be verified in CI or accepted in testing. | REQ-NFR-08 |
| F-06 | Medium | `SystemLocationProvider.getLastKnownLocation()` calls `LocationManager.getLastKnownLocation()` which requires the app to already hold `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION` at call time, or it throws `SecurityException` (silently swallowed by `runCatching`). The current implementation uses only `NETWORK_PROVIDER` and `PASSIVE_PROVIDER` — it does not use `GPS_PROVIDER`. For REQ-NORTH-03 ("use current GPS"), a fresh GPS fix needs an active location request (e.g., `LocationManager.requestSingleUpdate` or `FusedLocationProviderClient`), not just `getLastKnownLocation`. The REQ must distinguish "current GPS fix" from "last known location" and specify the maximum age of a "current" fix for the purposes of Scenario B vs. Scenario C. | REQ-NORTH-03; §8 Scenario B vs. C |
| F-07 | Medium | Scenario A acceptance criterion states "heading value changes by the local declination within 200 ms." The existing `CompassViewModel` emits UI state on `Dispatchers.Default` in a `Flow.collect` loop — the toggle will be a user event on the main thread mutating a `StateFlow`. The 200 ms budget is plausible, but the REQ does not specify what "within 200 ms" is measured from (user touch → UI update, or sensor tick → heading computation). If WMM declination computation is synchronous and on the main thread, there is a latency risk on low-end devices. The REQ should specify whether declination computation may be pre-cached or must be on-demand. | §8 Scenario A; REQ-NORTH-01 |
| F-08 | Medium | `REQ-CAPTURE-04` requires SQLite/Room with SQLCipher. `LuopanDatabase` already uses SQLCipher with `DatabaseKeyManager` (AndroidKeyStore-backed AES-256-GCM). The Phase 2 `BearingRecord` entity must be added to the same `@Database` annotation. The REQ does not specify whether bearing records share the existing database or use a separate encrypted database. Sharing is the correct implementation choice, but the REQ is silent — leaving room for an engineer to create a second, separately-keyed database, which would complicate future migration and backup strategy. This should be made explicit. | REQ-CAPTURE-04 |
| F-09 | Medium | The `SettingsRepository` stores `declinationMode` with three string constants (`auto`, `manual`, `magnetic`). Phase 2 introduces a True N / Magnetic N toggle in the main UI (REQ-DECL-01). The existing `CompassViewModel` already reads `declinationMode` to set `north_label` and `CompassUiState.INITIAL` defaults to `"Magnetic N"`. The REQ does not specify how the main-UI toggle relates to the existing settings declination modes (`auto`/`manual`/`magnetic`). Specifically: does toggling to True N set `declinationMode = "auto"`, or is a new preference key required? Without this, two engineers touching `SettingsRepository` in parallel will produce conflicting implementations. | REQ-DECL-01; §5.2 |
| F-10 | Medium | Scenario E states "Capturing with confidence 'Poor': bearing saves with `interference_flag=true`." However, `OverallConfidence.POOR` is not the same as `InterferenceState != CLEAR`. A bearing can be POOR confidence due to tilt, noise variance, or calibration age — not interference. Mapping POOR confidence directly to `interference_flag=true` would produce false positives in the stored record. The REQ must clarify: is `interference_flag` driven by `InterferenceState` (MODERATE or WARNING), by `OverallConfidence == POOR`, or by both? The semantics affect every downstream query in Phase 4. | §8 Scenario E; REQ-CAPTURE-01 |
| F-11 | Low | `REQ-NORTH-01` references "168 Gauss coefficients." WMM2025 defines a degree/order 12 model: (12+1)² − 1 = 168 main-field coefficients plus secular variation coefficients (another 168), totalling 336 values in the published `.COF` file. The REQ should clarify whether it means 168 (main field only) or 336 (main + SV), since SV coefficients are required to compute declination at dates beyond the model epoch year (2025.0 vs. 2026.0+). Omitting SV coefficients would cause degrading accuracy over the 2025–2030 validity window. | REQ-NORTH-01 |
| F-12 | Low | `REQ-NORTH-02` specifies Android `GeomagneticField` as the fallback when WMM2025 expires (post-2030) or fails. `GeomagneticField` is documented to use a bundled WMM epoch that Google updates at major Android releases. On older Android versions (minSdk = 26), the bundled model may be WMM2015 or WMM2020. The REQ should state the minimum acceptable accuracy for the fallback scenario and acknowledge that the fallback accuracy guarantee depends on the OEM's system image, which is outside the app's control. | REQ-NORTH-02 |
| F-13 | Low | `REQ-CAPTURE-06` requires a first-capture privacy dialog with a location toggle defaulting to ON. The REQ does not specify where the "has shown privacy dialog" state is persisted (SharedPreferences key in `SettingsRepository`? A separate preference file?), nor what happens if the user clears app data between captures. This is a minor implementation ambiguity but will cause unnecessary PM re-consultation if not resolved in the REQ. | REQ-CAPTURE-06 |
| F-14 | Low | §9 (Open Questions) flags P2-R3 (30-day cache cutoff) as an assumption but does not specify the behavior when the cached location age is between 0 and 30 days with respect to the UI label in Scenario C: "Using last known location (15 days ago)." The age display implies a live computation (`currentTime - cacheTimestamp`), but there is no spec on age granularity (hours vs. days) or rounding, which will lead to inconsistent implementations across locales. | §8 Scenario C; REQ-NORTH-03 |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | Should `InterferenceMetrics.expectedField_uT` and `expectedInclination_deg` be populated by the WMM2025 engine from Phase 2 onward, making them true geomagnetic model predictions rather than rolling baseline estimates? If yes, the `InterferenceDetector` contract must be updated in the TSPEC. |
| Q-02 | Is the bearing capture database the same Room/SQLCipher instance as the calibration database (`luopan.db`)? If yes, the schema migration from v1 to v2 must be addressed explicitly before Phase 2 ships. |
| Q-03 | `fallbackToDestructiveMigration()` is currently active. Is there a plan to replace it with a real Room migration before Phase 2 (which will require a schema version bump)? If not, user calibration records will be lost on upgrade. |
| Q-04 | What is the WMM `.COF` asset file format to be bundled — the standard NOAA text format, or a pre-parsed binary? This affects the cold-start latency budget in REQ-NFR-08. |
| Q-05 | Does the True North / Magnetic North main-UI toggle replace or supplement the existing `SettingsRepository.declinationMode` setting? |

---

## Positive Observations

- The GPS fallback chain (live GPS → cached ≤30 days → manual entry → magnetic only) is well-specified across §5.1, §7, and Scenarios C/D. This is exactly the level of detail needed for implementation.
- SQLCipher is already integrated in Phase 1 (`LuopanDatabase`, `DatabaseKeyManager`). Adding `BearingRecord` to the existing encrypted database is straightforward — good architectural foresight.
- Deferring `REQ-CAPTURE-03` (history screen) to Phase 4 is the right call — writing to encrypted storage without a UI is testable and low-risk. Shipping the read path before the history screen reduces Phase 2 scope safely.
- Risk P2-R1 (WMM licensing) is correctly flagged as a blocker with a reference to the master REQ open question. No implementation should start until this is confirmed.
- The `OverallConfidence` enum (`HIGH`, `MODERATE`, `POOR`, `STABILIZING`, `SENSOR_ERROR`) already has the values needed by Scenario E without changes.
- Scenario F is included as an explicit acceptance test for the interference baseline upgrade — this is excellent because the upgrade is a conformance change to existing behavior with no new requirement ID, which could otherwise go untested.

---

## Recommendation

**Need Attention**

Findings F-01 through F-04 are High severity and must be resolved before TSPEC authoring begins. F-01 (WMM/interference baseline contract) and F-03 (destructive migration risk) are blockers for any Phase 2 database or interference work. F-02 (missing location permission) must be addressed in the manifest specification before Phase 2 implementation. F-04 (undefined `calibration_version` field) must be resolved to define the `BearingRecord` schema unambiguously. Medium findings F-05 through F-10 should be incorporated into either the REQ or the TSPEC before implementation begins.
