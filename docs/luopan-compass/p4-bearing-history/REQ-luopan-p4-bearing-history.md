# REQ-luopan-p4-bearing-history
## Phase 4: Bearing History, Recalibration Refinements, and Sensor Diagnostics

| Field | Value |
|-------|-------|
| **Version** | 0.3-draft |
| **Date** | 2026-04-27 |
| **Status** | Draft |
| **Phase** | 4 of 5 |
| **Parent REQ** | [REQ-luopan-compass v0.2-draft](REQ-luopan-compass.md) |
| **Prerequisite** | Phase 3 complete and stable |
| **Delivery plan** | [DELIVERY-PLAN.md](DELIVERY-PLAN.md) |

---

## 1. Goal

Give professionals and power users the ability to **review, search, and manage saved bearings** in a structured history screen, improve the calibration lifecycle with **automatic recalibration prompts**, flag records that were captured under interference, and begin building the **sensor quality diagnostic log** that will inform per-OEM accuracy improvements.

This is a deepening phase: no new display mode, no new persona served. Every existing persona benefits from better field workflow.

---

## 2. Usable Application State After Phase 4

| Capability | Detail |
|-----------|--------|
| Bearing history screen | Full list of saved bearings, sorted newest-first; shows name, bearing, confidence badge, timestamp per row; tap to expand full record |
| History search | Case-insensitive substring search by name; shows empty-state view on no match; list restores immediately when query is cleared |
| Swipe to delete | Deletion committed on swipe; 5-second undo toast; single active undo at a time |
| Interference flag | Records captured under WARNING interference (Poor confidence) show "⚠ Captured under interference" in history view; badge absent for MODERATE/CLEAR records |
| Recalibration prompts | Non-blocking banner when calibration is >30 days old; automatic prompt when systematic field drift detected for >60 s (only when not already in a WARNING interference state); banners are per-session dismissible; age-based banner re-appears on next launch if condition still holds; drift prompt has 10-minute cooldown after dismissal |
| Sensor diagnostic log | On first launch after Phase 4 version install: `sensor_profile.json` written to internal app storage (accessible via ADB only; never transmitted) with device model, Android version, sensor types, resolution, range |

---

## 3. Personas Deepened After Phase 4

| Persona | New Benefit |
|---------|-------------|
| Persona 1 — Master Li | Review bearing notes from a site visit; see which readings were flagged as interference-affected |
| Persona 2 — Engineer Chen | Review all bearings from a field session in chronological order; assess any interference flags |
| Persona 3 — Yamada | Review antenna bearings from last session |
| All | Automatic recalibration nudges mean fewer silently-stale calibrations |

---

## 4. User Stories Active in This Phase

| ID | Title |
|----|-------|
| US-06 | Reviewing saved bearings *(newly active)* |
| US-03 | Calibrating in the field *(improved: automatic prompts)* |
| US-04 | Detecting magnetic interference *(improved: interference flag in records)* |

---

## 5. Requirements in Scope

### 5.1 Bearing History

| ID | Title | Priority | Key Spec |
|----|-------|----------|----------|
| REQ-CAPTURE-03 | Bearing history screen | P1 | Sorted by captured_at (newest first); per-row: name, bearing + north type, confidence badge, date/time; swipe-to-delete with undo toast (5 s); case-insensitive substring search by name; tap to expand full record details and notes; DAO must expose a reactive `Flow<List<BearingRecord>>` (or `PagingSource`) — not a plain `List` — to support live updates after delete/undo and new captures |

**Expanded record fields (all records):** bearing value + north type, confidence level, captured_at timestamp, name, notes. For records with interference_flag=true: additionally field_deviation_pct (displayed as percentage: stored fractional × 100, e.g., 0.25 → "25%") and inclination_deviation_deg.

**Navigation architecture:** The bearing history screen is a new third tab in the existing `TabLayout` / `nav_graph.xml` — a dedicated `BearingHistoryFragment` added as a new navigation destination. This resolves Risk P4-R4. The existing two tabs (Modern, Luopan) are unaffected; Phase 3 code requires no structural change. `CalibrationWizardActivity` is launched from the history screen via `ActivityResultLauncher` registered in `BearingHistoryFragment`. On `RESULT_OK`, the fragment calls `viewModel.loadCalibrationAge()` to update the banner state. On cancel or back-press, the user returns to the history screen (standard Activity back-stack behavior).

**Search behaviour:**
- Match type: case-insensitive substring (e.g., query "north" matches "North Gate", "northeast", "Northing")
- Debounce: trigger search after 300 ms of no input; the 300 ms window restarts on each keystroke (standard debounce — each new character resets the timer); no minimum character count
- Empty query: restore full sorted list immediately
- Zero matches: show empty-state view with message "No bearings match your search"

**Swipe-to-delete behaviour:**
- Deletion is committed to the database immediately on swipe (not deferred to toast expiry)
- "Undo" Snackbar is shown for exactly 5 seconds
- While the Snackbar is visible, tapping "Undo" restores the deleted record in its original sort position; if a new insert has shifted positions, the restored record is re-inserted by captured_at ordering
- Only one active undo is supported at a time: swiping a second record while the first Snackbar is still visible dismisses the first Snackbar (first deletion is permanent) and starts a new 5-second window for the second record
- If the app is backgrounded during the undo window and the process is killed, the deletion is permanent; no undo on next launch

**Empty history state:** When zero bearings are saved, the screen shows an illustration and message "No bearings yet — capture your first bearing from the compass screen."

**Performance NFR:** Initial list load of up to 500 records must complete within 500 ms on `Dispatchers.IO` on a mid-range device (e.g., Pixel 4a equivalent). Frame time during fling scroll must not exceed 16 ms (60 fps) with 500 records loaded. Engineering MUST use `RecyclerView` with a `Flow`- or `PagingSource`-backed adapter.

**Performance NFR test methodology:** The 500 ms initial-load threshold is verified by an instrumented test using a real Room in-memory database seeded with 500 records, asserting that the first `Flow` emission completes within 500 ms on `Dispatchers.IO`. The 16 ms frame-time threshold is verified by a Macrobenchmark instrumented test using `FrameTimingMetric` with the `BearingHistoryFragment` fling scenario; this test is run in the `benchmark` build variant and its result is reported in CI as a tracked metric (non-blocking gate in CI — a regression alert, not a build failure). These test types are consistent with the macrobenchmark workflow already present in `.github/workflows`.

### 5.2 Interference Flag in Records

| ID | Title | Priority | Key Spec |
|----|-------|----------|----------|
| REQ-DETECT-05 | Interference flag in bearing record | P1 | When interference_flag=true in saved record, history row shows "⚠ Captured under interference" badge; full record detail shows field_deviation_pct (displayed as a percentage: stored fractional × 100, e.g., 0.25 → "25%") and inclination_deviation_deg at time of capture. Badge MUST NOT appear on records with interference_flag=false. |

### 5.3 Recalibration Lifecycle

| ID | Title | Priority | Key Spec |
|----|-------|----------|----------|
| REQ-CAL-05 | Recalibration prompts | P1 | See full specification below. |

**REQ-CAL-05 Full Specification**

Two independent prompt conditions exist. Both are non-blocking banners; the app remains fully usable when either is shown.

**Condition A — Calibration age >30 days:**
- Trigger: `(today − calibration_date) > 30 days`
- Day count N for banner text: computed as integer floor division of elapsed milliseconds (e.g., 31 days and 23 hours → N = 31; 32 days exactly → N = 32). N is never rounded up.
- Banner text: "Your calibration is [N] days old — consider recalibrating"
- Tapping the banner opens `CalibrationWizardActivity` directly via `ActivityResultLauncher`; on `RESULT_OK`, banner is dismissed and `viewModel.loadCalibrationAge()` is called
- Dismissal: per-session (stored in ViewModel only); banner re-appears on the next app launch if the condition still holds
- No daily cooldown for the age-based prompt; it reappears every launch until the user recalibrates
- **Confidence impact (from master REQ §8.3):** When calibration age > 30 days, the `cal_age` dimension scores POOR in the confidence model. The `min()` across all dimensions means overall confidence is capped at MODERATE if all other dimensions are GOOD. If any other dimension is also degraded, confidence may be POOR. Scenario D is updated accordingly — see §7.

**Condition B — Systematic field drift:**
- Trigger preconditions (ALL must hold continuously for the timer to count):
  1. Device is stationary: accelerometer variance of the **combined 3-axis magnitude** `sqrt(ax²+ay²+az²)` < 0.01 (m/s²)² over a rolling 5-second window (250 samples at 50 Hz). A single variance accumulator over the magnitude series is used — not three independent per-axis accumulators.
  2. Interference state is not already WARNING: the active `InterferenceState` from REQ-DETECT-01 is `CLEAR` or `MODERATE` (i.e., condition B must NOT fire when REQ-DETECT-01 has set `InterferenceState` to `WARNING`, the state that maps to Poor confidence via the confidence model)
  3. A `CalibrationRecord` with `expected_field_ut > 0.0` is present (zero value from MIGRATION_2_3 disables Condition B — see DB constraint below)
- Timer ownership: the 60-second countdown lives in a dedicated `DriftDetector` component injected into `CompassViewModel`. `DriftDetector` is NOT implemented as an inline stateful field in the ViewModel's 200 Hz sensor loop. It accepts a `Clock` interface dependency (consistent with the `FakeClock` pattern used in `ConfidenceModel` and calibration tests) to enable deterministic unit testing of the 60-second threshold and 10-minute cooldown without real-time delays.
- Timer reset behavior: **any single violation of any precondition immediately resets the timer to zero**, with no hysteresis window. The timer only counts elapsed time during frames where ALL three preconditions hold simultaneously.
- Timer: starts counting when all preconditions hold; resets to zero if any precondition is violated
- Trigger: preconditions held continuously for >60 seconds AND measured field magnitude differs from `CalibrationRecord.expected_field_ut` by >10%
- Formula: `|measured_magnitude_uT − expected_field_ut| / expected_field_ut > 0.10` (only evaluated when `expected_field_ut > 0.0`; a zero value skips evaluation entirely)
- Banner text: "Magnetic environment may have changed — recalibrate for best accuracy"
- Tapping the banner opens `CalibrationWizardActivity` directly via `ActivityResultLauncher`; on `RESULT_OK`, banner is dismissed and drift detector is reset
- Dismissal: per-dismissal cooldown of 10 minutes; the cooldown timestamp is stored in `SettingsRepository` as a Unix epoch millisecond value (so it survives process death). After 10 minutes, if the drift condition still holds, the banner re-arms and will show again after a new 60-second continuous drift window.
- The drift prompt and the age-based prompt may both be shown simultaneously as separate banners; they are not merged

**Database constraint (requires MIGRATION_2_3):** `CalibrationRecord` must gain a new column `expected_field_ut: Float` — the calibration-time measured field magnitude in µT. This value is sourced from the sphere radius `r` computed by `CalibrationEngine.fitEllipsoid`. To expose `r`, `CalibrationResult` must gain a new field `sphereRadius_uT: Float`; `fitEllipsoid` must populate it; and `CalibrationRepository.toRecord()` must map `sphereRadius_uT` to `expected_field_ut`. This is a three-file change: `CalibrationEngine`, `CalibrationResult`, and `CalibrationRepository`. The migration must handle existing records by setting `expected_field_ut = 0.0` (which disables Condition B triggering via the `expected_field_ut > 0.0` precondition guard until the user recalibrates).

### 5.4 Sensor Capability Logging

| ID | Title | Priority | Key Spec |
|----|-------|----------|----------|
| REQ-SENSOR-07 | Per-device sensor capability logging | P2 | On first launch after Phase 4 version install: write `sensor_profile.json` to `Context.getFilesDir()` (internal app storage, never transmitted, accessible via ADB only — not via standard Android file manager on non-rooted devices). See first-launch detection and file schema below. |

**First-launch detection mechanism:** Gated by a version-code comparison in `SettingsRepository`. On each app launch, compare `BuildConfig.VERSION_CODE` to a stored `sensor_profile_written_for_version: Int` key. If `VERSION_CODE > sensor_profile_written_for_version` (including the initial case where the key is absent / 0), write the file and update the stored key. This ensures the profile is refreshed on each version upgrade. If the user clears app data, the stored key is reset to 0, triggering a re-write on the next launch. `sensor_profile_written_for_version` must be added as a new `const val` key and `var` property in `SettingsRepository`, consistent with the existing Phase 3 additions block — not as an ad-hoc call at the use site.

**I/O failure contract:** If the `sensor_profile.json` write fails (e.g., storage full), the failure is logged at `Log.e` level and the `sensor_profile_written_for_version` key is NOT updated. This means the write will be retried on the next app launch until it succeeds. Silent data loss is not acceptable; the failure must be observable in logcat.

**sensor_profile.json schema:**
```json
{
  "device_model": "string (Build.MODEL)",
  "device_manufacturer": "string (Build.MANUFACTURER)",
  "android_version": "string (Build.VERSION.RELEASE)",
  "android_api_level": "integer (Build.VERSION.SDK_INT)",
  "app_version_code": "integer (BuildConfig.VERSION_CODE)",
  "written_at_iso8601": "string (ISO-8601 UTC timestamp)",
  "sensors": [
    {
      "type_constant": "integer (Sensor.TYPE_*)",
      "name": "string (Sensor.getName())",
      "vendor": "string (Sensor.getVendor())",
      "resolution_ut_or_native": "float (Sensor.getResolution(), in native unit)",
      "max_range_native": "float (Sensor.getMaximumRange(), in native unit)",
      "reporting_mode": "string (e.g., CONTINUOUS, ON_CHANGE)"
    }
  ]
}
```
File is pretty-printed (2-space indent). Non-transmission is a design guarantee enforced by the existing `NoInternetPermissionCheck` lint rule; it is not tested via automated network assertion (design-level only). File inaccessibility via the standard Android file manager on non-rooted devices is an implicit guarantee from Android's storage sandboxing model — it is not asserted in automated acceptance tests.

---

## 6. Requirements Deferred from Phase 4

| ID | Title | Deferred to |
|----|-------|-------------|
| REQ-CAPTURE-05 | Share / export (CSV) | Phase 5 |

---

## 7. Phase 4 End-to-End Acceptance Test

**Scenario A — Bearing history screen (normal)**

*Given* 10 bearings were saved across two field sessions (Phase 2/3 functionality),  
*When* user opens Bearing History,  
*Then*:
- All 10 records visible in scrollable list, newest first
- Each row shows: name, bearing + north type label, confidence badge, timestamp
- Tapping a row expands it; the expanded view shows: bearing value + north type, confidence level, captured_at timestamp, name, notes
- For rows with interference_flag=false, no "⚠ Captured under interference" badge is shown

**Scenario A2 — Empty history state**

*Given* no bearings have been saved,  
*When* user opens Bearing History,  
*Then*:
- An empty-state illustration and the message "No bearings yet — capture your first bearing from the compass screen" are displayed
- No list items are shown

**Scenario B — Interference flag display**

*Given* one saved bearing has interference_flag=true (captured under WARNING interference / Poor confidence) and another has interference_flag=false,  
*When* both records are visible in Bearing History,  
*Then*:
- "⚠ Captured under interference" badge is shown only on the flagged row; the non-flagged row shows no badge
- Expanding the flagged record shows field_deviation_pct formatted as a percentage (e.g., "25%") and inclination_deviation_deg at time of capture

**Scenario C — Swipe to delete (single record)**

*Given* any record in Bearing History,  
*When* user swipes the row left,  
*Then*:
- Record is immediately removed from the list and committed to the database as deleted
- "Undo" Snackbar appears for exactly 5 seconds
- Tapping "Undo" within 5 seconds restores the record; the record reappears in its correct captured_at-ordered position
- If "Undo" is not tapped within 5 seconds, the deletion is permanent

**Scenario C2 — Swipe to delete (second swipe while Snackbar active)**

*Given* a first "Undo" Snackbar is visible for record A,  
*When* user swipes a second record B,  
*Then*:
- The first Snackbar (for record A) is dismissed; record A's deletion becomes permanent
- Record B is immediately removed from the list
- A new 5-second "Undo" Snackbar appears for record B only

**Scenario C3 — Swipe to delete with backgrounding**

*Given* an "Undo" Snackbar is visible,  
*When* user presses Home and the process is killed before 5 seconds elapse,  
*Then*:
- On next launch the deleted record is absent; no undo is offered

**Scenario D — Recalibration prompt (age)**

*Given* the current calibration is 31 days old (cal_age dimension scores POOR),  
*When* the app is opened,  
*Then*:
- A non-blocking banner appears: "Your calibration is 31 days old — consider recalibrating" (N = floor division of elapsed time; 31 days and 23 hours → "31 days old")
- User can dismiss the banner; the compass remains fully functional
- Per master REQ §8.3: overall confidence is capped at MODERATE if all other dimensions are GOOD (min over all dimensions: POOR from cal_age → MODERATE cap from the scoring formula); if another dimension is also POOR the overall confidence shows POOR
- Banner re-appears on next launch if calibration is still >30 days old

**Scenario D2 — Age-based banner boundary (negative)**

*Given* the current calibration is exactly 30 days old,  
*When* the app is opened,  
*Then*:
- No age-based recalibration banner is shown (trigger is strictly >30 days)

**Scenario E — Recalibration prompt (drift)**

*Given*:
- The device is stationary (variance of combined 3-axis magnitude `sqrt(ax²+ay²+az²)` < 0.01 (m/s²)² over 5 s)
- Active interference state is `CLEAR` (not already in WARNING from REQ-DETECT-01)
- A CalibrationRecord with `expected_field_ut > 0.0` is present
- The measured field magnitude has differed from expected_field_ut by >10% continuously for >60 seconds (with no precondition violations during that window)

*When* these conditions are met,  
*Then*:
- Banner appears: "Magnetic environment may have changed — recalibrate for best accuracy"
- Banner is dismissible; tapping the banner opens CalibrationWizardActivity
- After dismissal, the banner does not re-appear for 10 minutes even if the drift condition persists (cooldown timestamp persisted in SettingsRepository, survives process death)

**Scenario E2 — Drift prompt suppressed during active interference (negative)**

*Given* active interference from a proximate source has set `InterferenceState` to `WARNING` (the state that maps to Poor confidence via the confidence model, per REQ-DETECT-01),  
*When* the measured field magnitude simultaneously exceeds the 10% drift threshold for >60 seconds,  
*Then*:
- The drift prompt (Condition B) does NOT appear — only the WARNING-interference indicator from REQ-DETECT-01 is shown
- The drift detection timer does not count while `InterferenceState` is `WARNING`

**Scenario F — Sensor profile**

*Given* Phase 4 version is installed on a device for the first time (or as an upgrade from a prior version),  
*When* the app launches,  
*Then*:
- `sensor_profile.json` is written to `Context.getFilesDir()` (internal app storage)
- The file contains the expected JSON fields: device_model, device_manufacturer, android_version, android_api_level, app_version_code, written_at_iso8601, and a sensors array with type_constant, name, vendor, resolution_ut_or_native, max_range_native, reporting_mode per sensor
- A `sensor_profile_written_for_version` key in SettingsRepository equals `BuildConfig.VERSION_CODE`

**Scenario F2 — Sensor profile not rewritten on subsequent launches**

*Given* sensor_profile.json was already written on a previous launch of the same app version,  
*When* the app launches again,  
*Then*:
- sensor_profile.json is NOT rewritten (the existing file is preserved)

**Scenario F3 — Sensor profile rewritten after data clear**

*Given* the user has cleared app data (resetting SettingsRepository),  
*When* the app launches,  
*Then*:
- sensor_profile.json is rewritten (the stored version key is absent/0, triggering a re-write)

**Scenario F4 — Sensor profile write failure**

*Given* the `sensor_profile.json` write fails (e.g., storage full),  
*When* the app launches on the next occasion,  
*Then*:
- The write is retried (because `sensor_profile_written_for_version` was not updated on the failed attempt)
- The failure from the previous attempt is present in logcat at `Log.e` level

---

## 8. Open Questions and Risks Specific to Phase 4

**Risk P4-R1 — Drift detection false positives:** The "10% deviation for >60 s" rule for recalibration prompts may trigger when the user is in a legitimately different magnetic environment (e.g., traveled to a different city) rather than when calibration has decayed. This is by design — the user should recalibrate in a new environment anyway. Wording of the prompt ("Magnetic environment may have changed") is intentionally neutral.

**Risk P4-R2 — Bearing history performance (NFR):** With hundreds of records, the history screen must remain performant. The measurable threshold is: initial load of up to 500 records completes within 500 ms on Dispatchers.IO; frame time does not exceed 16 ms during fling scroll with 500 records on a mid-range device. Engineering must use a `Flow<List<BearingRecord>>` or `PagingSource`-backed DAO query — the plain `getAll(): List<BearingRecord>` DAO method is insufficient for reactive updates and unbounded memory load. See §5.1 for test methodology.

**Risk P4-R3 — CalibrationRecord DB migration:** Adding `expected_field_ut` to `CalibrationRecord` requires a Room DB migration from version 2 to 3. Existing records must be updated with `expected_field_ut = 0.0`, which disables drift detection (Condition B) for those records until the user recalibrates. Engineering must ensure `CalibrationEngine`, `CalibrationResult` (new `sphereRadius_uT` field), and `CalibrationRepository` handle this field correctly. The three-file change is explicitly scoped in §5.3.

**Risk P4-R4 — Navigation architecture (resolved):** The bearing history screen is implemented as a third tab (`BearingHistoryFragment`) in the existing `TabLayout` / `nav_graph.xml`. See §5.1 Navigation architecture for the full contract, including `ActivityResultLauncher` registration for `CalibrationWizardActivity` and back-navigation behavior.
