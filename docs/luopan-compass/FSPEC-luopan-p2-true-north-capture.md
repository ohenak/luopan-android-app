# FSPEC-luopan-p2-true-north-capture
## Functional Specification — Phase 2: True North, Magnetic Declination, and Bearing Capture

| Field | Value |
|-------|-------|
| **Document ID** | FSPEC-luopan-p2-true-north-capture |
| **Version** | 0.2-draft |
| **Status** | Draft |
| **Date** | 2026-04-24 |
| **Revised** | 2026-04-24 (v0.2-draft — addressed SE/TE cross-review findings: SE-F-01 `calibration_version` type; SE-F-02 permission rationale flow; SE-F-03 Phase 1 baseline correction; SE-F-04 BearingRecord type conflicts; SE-F-05 `WallClock` rename; SE-F-06 `MagneticFieldModel` interface; SE-F-07 `requestLocationUpdates` params; SE-F-11 AT-NFR-01 cold-start; TE concurrent-save debounce; TE first-capture persistence; TE force-stop resilience) |
| **Phase** | 2 of 5 |
| **Parent REQ** | [REQ-luopan-p2-true-north-capture.md](REQ-luopan-p2-true-north-capture.md) |
| **Master REQ** | [REQ-luopan-compass.md](REQ-luopan-compass.md) |
| **Predecessor FSPEC** | [FSPEC-luopan-p1-core-compass.md](FSPEC-luopan-p1-core-compass.md) |
| **Author** | Product |

> **Purpose of this document:** This FSPEC translates Phase 2 requirements into precise behavioral descriptions — the decision logic, multi-step flows, and business rules that should not be left to engineering discretion. It is a product document written from the user's perspective. It does not specify architecture, class structures, or implementation choices.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Functional Flows](#2-functional-flows)
   - 2.1 [True North Correction Flow](#21-fspec-tnorth--true-north-correction-flow)
   - 2.2 [North Type Toggle Flow](#22-fspec-toggle--north-type-toggle-flow)
   - 2.3 [Declination Info Panel Flow](#23-fspec-declpanel--declination-info-panel-flow)
   - 2.4 [GPS Location Handling Flow](#24-fspec-gps--gps-location-handling-flow)
   - 2.5 [Bearing Capture Flow](#25-fspec-capture--bearing-capture-flow)
   - 2.6 [Interference Detection Upgrade Flow](#26-fspec-detectupgrade--interference-detection-upgrade-flow)
   - 2.7 [Extreme Latitude Advisory Flow](#27-fspec-latitude--extreme-latitude-advisory-flow)
3. [Business Rules](#3-business-rules)
4. [Error Scenarios](#4-error-scenarios)
5. [UI State Transitions](#5-ui-state-transitions)
6. [Data Contracts](#6-data-contracts)
7. [Acceptance Tests](#7-acceptance-tests)

---

## 1. Overview

### 1.1 Feature Summary

Phase 2 upgrades the Luopan compass from magnetic-only to true north capable by:

- Bundling WMM2025 coefficients for fully offline declination computation
- Adding a quick north type toggle (True N / Magnetic N) visible in the main compass screen
- Providing a declination info panel with source, coordinates, and validity date
- Handling all GPS availability states with a graceful fallback chain
- Enabling bearing capture so field professionals can record and review headings with full metadata
- Upgrading interference detection baselines to WMM2025 expected values
- Adding an extreme latitude advisory when WMM-predicted inclination reaches polar-region levels

### 1.2 Actors

| Actor | Role |
|-------|------|
| User | Operates the compass; toggles north type; captures bearings; reads declination info |
| System | Computes declination; manages location cache; evaluates WMM model; saves bearing records; triggers advisories |
| OS | Provides GPS fixes; reports permission grants/denials; enforces storage security |
| WMM2025 (bundled) | Offline model providing declination, inclination, and total field magnitude for any lat/lon/date |
| Android GeomagneticField | Fallback model used only when WMM2025 expires (post-2030) or fails to load |

### 1.3 Preconditions

- Phase 1 is complete and stable: sensor fusion, calibration wizard, interference detection, and confidence model are all operational.
- WMM2025 coefficients file is bundled in the APK assets (licensing resolved per Risk P2-R1 before release).
- The Room database schema uses `Migration(1,2)` which adds the `bearing_records` table; the `calibration_records` table is not altered (see §10.2 of TSPEC Phase 1).

---

## 2. Functional Flows

---

### 2.1 FSPEC-TNORTH — True North Correction Flow

**Title:** How the app computes and applies the magnetic declination to produce a true north heading

**Linked requirements:** REQ-NORTH-01, REQ-NORTH-02, REQ-NORTH-03, REQ-NORTH-04

**Actors:** System (WMM model, location provider, fusion pipeline), User (views heading)

**Preconditions:**
- Modern Mode is active
- The user has selected True North mode (FSPEC-TOGGLE, §2.2)
- A location has been resolved via the location fallback chain (FSPEC-GPS, §2.4)

**Behavioral flow:**

1. When True North mode is active, the system queries the WMM2025 model on every sensor update cycle with the current resolved location (latitude, longitude, altitude) and the current UTC date.

2. The WMM2025 model returns:
   - Declination in decimal degrees (positive = east; negative = west)
   - Inclination in decimal degrees (used for interference detection and extreme latitude advisory)
   - Total field magnitude in µT (used as the expected field for interference detection)

3. The system applies declination to the magnetic heading: `true_heading = magnetic_heading + declination`. Both values are normalized to the range [0°, 360°).

4. The corrected true heading is displayed in the digital readout and compass rose.

5. The north reference label updates to **"True N"** (localized).

6. **Declination source labeling:** The source used for the active declination computation is reflected in the declination info panel (FSPEC-DECLPANEL, §2.3). The label does not appear in the main heading area — only in the panel.

**WMM2025 validity check:**

7. The WMM2025 model is valid for dates 2025.0–2030.0 (approximately January 1, 2025 through January 1, 2030). The system checks the current UTC date against this range on each computation.

8a. **If the current date is within the valid range:** WMM2025 is used. No warning is displayed.

8b. **If the current date is past 2030.0 (model expired):** The system falls back to the Android `GeomagneticField` class. A persistent non-dismissible advisory appears in the declination info panel: **"Android model — may be less accurate"**. The north reference label in the main UI still shows "True N" but the info panel makes the source clear. The fallback is silent in the main heading view except for the changed source label in the panel.

8c. **If WMM2025 fails to load** (asset missing, parse error, corrupted file): Same fallback as 8b. Additionally, an internal error is logged. The app does not crash.

**Declination update frequency:**

9. The declination value is recomputed:
   - Whenever the resolved location changes (new GPS fix, cache load, or manual coordinate entry).
   - Once per app session start (warm-up computation at foreground entry).
   - Not on every sensor frame — declination changes too slowly for real-time recomputation to be necessary.
   - Note: the heading itself updates at ≥20 Hz by adding the most recently computed declination to the live magnetic heading.

**Business rules:**
- True North computation must work with no network connection. No network call is ever made to compute declination. This is a hard requirement.
- Declination is applied as an additive offset to the magnetic heading. There is no separate "true bearing" pipeline; it is one pipeline with a conditional offset applied.
- When north type is Magnetic N, declination is not applied and the offset is zero. Switching modes does not change the underlying magnetic heading — it only changes whether the offset is added for display.
- True heading is always normalized to [0°, 360°). Values that compute to negative or ≥360° are wrapped correctly.
- The WMM2025 model must not make any network calls. It is a pure offline computation against the bundled coefficients file.

**Edge cases:**
- Device clock is set to a date before 2025.0: WMM2025 extrapolation outside its valid range produces inaccurate results. The system treats this as a model validity failure and falls back to Android `GeomagneticField`. A clock advisory is shown in the info panel.
- Location latitude is ≥80° absolute (polar region): the extreme latitude advisory fires (FSPEC-LATITUDE, §2.7). True North computation continues — it is not blocked.
- Declination is exactly 0°: True N and Magnetic N display identical headings. The label still reflects the selected mode.

---

### 2.2 FSPEC-TOGGLE — North Type Toggle Flow

**Title:** How the user switches between True North and Magnetic North on the main compass screen

**Linked requirements:** REQ-DECL-01, REQ-NORTH-04

**Actors:** User (taps toggle), System (applies mode, persists preference)

**Preconditions:**
- Modern Mode is active
- The toggle is visible in the main compass UI

**Behavioral flow:**

**Toggle placement and appearance:**

1. The north type toggle is a clearly labeled control visible in Modern Mode without scrolling or entering any settings screen. It displays the current mode and allows one-tap switching.

2. In Phase 2, the toggle has exactly two states:
   - **Magnetic N** (default for new installs and devices without a resolved location)
   - **True N**

3. GRID north is not offered in this toggle. The toggle must not show a GRID option, a greyed-out GRID option, or any "coming soon" GRID placeholder. GRID north is Phase 5 (REQ-DECL-03 deferred).

**Toggle behavior — switching to True N:**

4. When the user taps the toggle to switch from Magnetic N to True N:

   4a. The system checks whether a location is available for declination computation (full logic in FSPEC-GPS, §2.4).

   4b. **If a location is available (current GPS or cached ≤30 days):**
   - Declination is computed immediately from WMM2025.
   - The heading value changes by the declination amount within 200 ms of the tap.
   - The north label changes to "True N".
   - No dialog is shown.

   4c. **If no location is available (no GPS fix, cache expired or absent):**
   - A dialog appears: "Enter coordinates for True North or use Magnetic North only" (localized). This dialog contains:
     - A coordinate entry field (degrees decimal, pre-filled with the last used manual coordinates if any).
     - A "Use True North" button (disabled until valid coordinates are entered).
     - A "Use Magnetic North" button.
   - If the user enters valid coordinates and taps "Use True North": the toggle switches to True N, declination is computed from the manual coordinates, and the heading changes. The north label shows "True N (manual location)" (localized).
   - If the user taps "Use Magnetic North" or dismisses the dialog: the toggle remains on Magnetic N. No mode change occurs.

**Toggle behavior — switching back to Magnetic N:**

5. When the user taps the toggle to switch from True N to Magnetic N:
   - The declination offset is removed immediately (within 200 ms).
   - The heading returns to the magnetic heading value.
   - The north label changes to "Magnetic N".
   - No dialog is shown.

**Persistence:**

6. The selected north type is persisted to local preferences. When the user restarts the app:
   - If True N was active and a location is still available: True N is restored immediately.
   - If True N was active but the cached location has now expired (>30 days since cache): the app silently falls back to Magnetic N on startup, shows the toggle in Magnetic N state, and displays a small advisory in the declination info panel: "Last location expired — switched to Magnetic North" (localized). The user can re-enter coordinates or wait for a GPS fix.

7. The toggle state persists across foreground/background cycles within a session.

**Business rules:**
- GRID is never shown in the toggle. The toggle is a strict binary choice in Phase 2: TRUE or MAGNETIC.
- The heading change when switching north types must occur within 200 ms of the tap, matching the acceptance test in REQ §8 Scenario A.
- Switching north types applies globally: both the compass rose and the digital readout reflect the selected north type simultaneously.
- The "True N (manual location)" label is distinct from "True N" to signal to the user that the declination source is not GPS-verified.
- If the app is installed for the first time: default is Magnetic N. This ensures the app works out-of-the-box even without GPS permission granted.

**Edge cases:**
- User taps the toggle rapidly multiple times: each tap toggles the state. The heading must not become desynchronized. The debounce is at most 50 ms (the 200 ms heading update budget includes this).
- User denies location permission and has no manual coordinates: True N mode cannot be activated. The toggle shows Magnetic N and the dialog prompts for manual entry.

---

### 2.3 FSPEC-DECLPANEL — Declination Info Panel Flow

**Title:** How the declination info panel is opened, what it shows, and when its data updates

**Linked requirements:** REQ-DECL-02

**Actors:** User (opens panel), System (populates panel content)

**Preconditions:**
- Modern Mode is active
- An info button or tappable label adjacent to the north type toggle is visible

**Behavioral flow:**

**Opening the panel:**

1. An info icon (ⓘ) is displayed adjacent to the north type toggle. Tapping it opens the declination info panel as a bottom sheet or modal dialog.

2. The panel is dismissed by tapping outside it, tapping a close button, or pressing the system back button.

**Panel contents:**

3. The panel always shows the following fields, regardless of whether True N or Magnetic N is currently active:

   | Field | Format | Example |
   |-------|--------|---------|
   | Declination value | `±D.D° E` or `±D.D° W` | `−5.3° W` |
   | Declination value (decimal) | `(−5.35°)` | `(−5.35°)` |
   | Source label | See source label rules below | `WMM2025 (valid to 2030)` |
   | Coordinates used | Masked to 2 decimal places | `Lat 25.04°, Lon 121.53°` |
   | Coordinates type | "GPS fix", "Cached location", or "Manual entry" | `Cached location` |
   | Last updated | Date only (no time): `YYYY-MM-DD` | `2026-04-20` |
   | Cache age | For cached locations: "N days ago" | `4 days ago` |

4. **Source label rules:**
   - WMM2025 in valid range: **"WMM2025 (valid to 2030)"**
   - Android GeomagneticField fallback (model expired or failed to load): **"Android model — may be less accurate"**
   - No location available (True N not active, no computation possible): **"No location available — True North disabled"**
   - Manual coordinates active: **"WMM2025 (manual location)"**

5. **"Last updated" field:**
   - For GPS fix: the date of the most recent successful GPS fix used for declination computation.
   - For cached location: the date the cache was written (when GPS last had a fix).
   - For manual entry: the date the user last entered coordinates.
   - The "last updated" date uses the device's local timezone for display, but is stored internally as UTC.

6. **If Magnetic N is currently active** (no declination applied): the panel still shows the computed declination value (so the user can see what the offset would be if they switched to True N) and the source, but adds a note: "True North is currently off. This declination would be applied if True North is enabled." (localized)

7. **Cache age display:** The age is computed using the `Clock` interface (see Business Rules below). For cached locations, the age is shown as "N days ago" where N is floor(cache_age_hours / 24). A cache from earlier today shows "0 days ago" (or equivalently "today").

**Business rules:**
- The panel must show coordinates masked to 2 decimal places (approximately 1.1 km resolution at the equator). Full precision coordinates are never displayed, per REQ-CAPTURE-06 location privacy principles.
- The declination value is shown with full precision (2 decimal places) in the parenthetical, and to 1 decimal place with E/W notation in the primary display.
- Cache age display and cache expiry calculation use the `Clock` interface (injectable for testing with `FakeClock`; production uses `WallClock`). This is defined here because the panel is where the cache age is surfaced to the user.
- The panel never makes a network call. All values are computed offline.

**Edge cases:**
- No location has ever been available and no manual coordinates entered: all location-dependent fields show "—" (em dash). The source label shows "No location available — True North disabled." The declination field shows "—".
- WMM2025 fails to load mid-session (e.g., storage error): the source label switches to "Android model — may be less accurate" on the next panel open.

---

### 2.4 FSPEC-GPS — GPS Location Handling Flow

**Title:** How the app acquires, caches, and falls back on location data for declination computation

**Linked requirements:** REQ-NORTH-03, REQ-CAPTURE-06

**Actors:** User (may grant/deny location permission, may enter manual coordinates), System (requests location, manages cache), OS (delivers GPS fix, reports permission state)

**Preconditions:**
- The app requires location for True North computation. Without a resolved location, True North mode cannot activate.

**Behavioral flow — location resolution on app launch:**

1. On foreground entry (Activity.onResume), the system initiates the location resolution chain:

   **Step 1 — Check current GPS fix:**
   - If `ACCESS_FINE_LOCATION` permission is granted AND a GPS fix is available within the last 60 seconds: use this fix. Cache it to local encrypted storage (lat, lon, alt, UTC timestamp).
   - Continue to Step 2 if no fresh fix.

   **Step 2 — Use cached location:**
   - If a cached location exists in local storage with age ≤30 days: use it for declination computation.
   - The UI shows the "Cached location" source label in the declination info panel.
   - Continue to Step 3 if cache is absent or expired.

   **Step 3 — No location available:**
   - True North mode cannot activate automatically.
   - If the user attempts to activate True North, the dialog described in FSPEC-TOGGLE §2.2 step 4c is shown.
   - If no manual coordinates are entered: the app remains in Magnetic N mode.

**Permission request flow:**

2. The first time True North mode is attempted AND `ACCESS_FINE_LOCATION` has never been requested: the system shows the system permission dialog (Android standard).

   **2a. Rationale dialog (re-request path):** If `shouldShowRequestPermissionRationale()` returns `true` (the OS signals the user has denied once before and has not selected "Don't ask again"), the app MUST show a brief in-app rationale dialog before presenting the system permission prompt. The rationale dialog text: "Location is used to compute magnetic declination for True North. Your coordinates are stored on-device only and are never shared." (localized). The dialog has two buttons: "Continue" (proceeds to system permission prompt) and "Not now" (abandons the permission request; the flow falls to 3b below).

3a. **Permission granted:** The location resolution chain (Step 1 above) runs.

3b. **Permission denied (first denial or "Not now" from rationale dialog):** The toggle remains on Magnetic N. A brief informational message is shown: "Location permission needed for True North. You can enter coordinates manually instead." (localized). The manual coordinate entry dialog is offered.

3c. **Permission permanently denied** (user selected "Don't ask again" on a prior denial): the system cannot show the permission dialog again. The same message as 3b is shown, and the app directs the user to device settings for manual permission grant. The manual coordinate entry dialog is still offered as an alternative.

**Location cache management:**

4. The location cache stores exactly one location record: the most recent valid GPS fix. It is not a history of locations.

5. Cache fields: latitude (double, degrees), longitude (double, degrees), altitude (double, meters), UTC timestamp of the fix (long, epoch milliseconds).

6. The cache is stored in encrypted local storage. It is never transmitted off-device.

7. Cache expiry is evaluated using the `Clock` interface. A cached location is considered valid if `Clock.nowMs() - cache.timestamp_ms < 30 * 24 * 60 * 60 * 1000L` (30 days in milliseconds).

8. When a new GPS fix is received that is more recent than the cached location, the cache is overwritten. There is no multi-location history in Phase 2.

**Continuous GPS update during session:**

9. While True North mode is active and location permission is granted, the system requests periodic GPS updates using `LocationManager.requestLocationUpdates()` with the following parameters: provider = `GPS_PROVIDER`, `minTimeMs` = 5000 (5 seconds), `minDistanceM` = 0.0f (no distance filter). A single-update timeout of 10 seconds is applied; if no fix arrives within 10 seconds of the initial request, the listener is cancelled and the system falls back to the cached location. When a new fix arrives:
   - The cache is updated.
   - Declination is recomputed from the new location.
   - If the new declination differs from the previous by more than 0.1°, the heading updates immediately.
   - The info panel "last updated" date updates.

**Business rules:**
- The location cache is stored on-device only. It is never synced, exported, or transmitted. This is a privacy requirement.
- A cached location older than 30 days is treated identically to no location — it does not produce a True North heading automatically. The user must either wait for a GPS fix or enter manual coordinates.
- Altitude is included in the cache for WMM2025 computation accuracy (the model uses altitude for the main field computation). If altitude is unavailable from the GPS fix, 0 meters (sea level) is used as the default.
- The 30-day cache expiry boundary is evaluated strictly: a cache entry from exactly 30 days ago is valid; one from 30 days + 1 second is expired.
- `ACCESS_COARSE_LOCATION` is not sufficient for Phase 2; `ACCESS_FINE_LOCATION` is required for declination accuracy.

**Edge cases:**
- GPS fix arrives while the manual-coordinates dialog is open: the dialog does not auto-dismiss. The user completes or cancels the dialog. On next location chain evaluation the fresh GPS fix will be available.
- User changes manual coordinates while True N is active: declination recomputes immediately. The "True N (manual location)" label remains. If the new coordinates produce a declination differing by more than 0.1° from the previous, the heading updates within 200 ms.
- GPS fix has extremely low accuracy (e.g., indoor location, accuracy radius > 5 km): the fix is still used. The app does not filter by GPS accuracy. The declination difference from a 5 km position error is well within magnetic model tolerances at most latitudes.

---

### 2.5 FSPEC-CAPTURE — Bearing Capture Flow

**Title:** Complete user journey from tapping the capture button to a saved bearing record

**Linked requirements:** REQ-CAPTURE-01, REQ-CAPTURE-02, REQ-CAPTURE-04, REQ-CAPTURE-06

**Actors:** User (initiates capture, names bearing, optionally adds notes, confirms save), System (freezes preview, validates input, persists record)

**Preconditions:**
- Modern Mode is active
- Phase 1 calibration exists (any quality)
- Sensor fusion is delivering heading values

**Behavioral flow:**

**Step 0 — Capture button visibility and state:**

1. A capture button (camera/pin icon with label "Save Bearing") is visible in Modern Mode at all times, not hidden behind any menu. This is an always-visible element per REQ-CAPTURE-02 (≤3 taps from button to saved).

2. The button is always tappable regardless of confidence level, north type, or interference state. It is never disabled or hidden. The bearing may be saved under any confidence level, including Poor (with a warning — see Step 1b).

**Step 1 — Pre-capture check and preview:**

3. When the user taps the capture button:

   3a. The system takes a snapshot of the current heading, confidence, interference state, and all other record fields at the exact moment of the tap. This snapshot is the "capture preview." The heading displayed in the capture dialog is the snapshot value — it does not continue updating.

   3b. **If current confidence is Poor OR interference_flag is true (field deviation ≥15% or inclination deviation ≥3°):** A warning dialog appears before the name entry dialog:
   - Warning text: "Interference detected or confidence is Poor. Bearing will be saved with a warning flag. Proceed?" (localized)
   - Primary action: **"Save with warning"** (amber button)
   - Secondary action: **"Cancel"** (grey button)
   - If the user taps "Cancel": the capture flow is abandoned. No record is created.
   - If the user taps "Save with warning": the flow continues to Step 2. The `interference_flag` is set to `true` in the record.

   3c. **If confidence is High or Moderate AND no interference:** No pre-save warning. Flow proceeds directly to Step 2.

**Step 2 — Name and notes entry:**

4. A capture dialog appears showing:
   - **Bearing preview** (read-only): the snapshot heading to 1 decimal place, the north type label, and the confidence badge. These values do not change while the dialog is open.
   - **Name field** (required, text input): default text "Bearing [N]" where N is the next sequential integer (e.g., "Bearing 1", "Bearing 2"). The default text is pre-selected so the user can immediately type a replacement name.
   - **Notes field** (optional, multi-line text input): empty by default, placeholder text "Add notes (optional)".
   - **GPS toggle** (on/off, default ON): "Include location with this bearing." If GPS permission is not granted, this toggle is absent (not shown as disabled — simply hidden). This is the REQ-CAPTURE-06 location privacy toggle.
   - **GPS privacy notice** (shown only on the first capture ever): a one-time notice below the GPS toggle: "Your location is saved on-device only and is never shared." This notice is shown exactly once per install. After the user confirms the first capture, it no longer appears.
   - **"Save" button** (primary, enabled only when name is non-empty after trimming whitespace)
   - **"Cancel" button** (secondary)

5. The name field has a maximum length of 100 characters. Characters beyond 100 are silently truncated (the input field prevents entry beyond this limit).

6. The notes field has a maximum length of 1000 characters. Characters beyond 1000 are silently truncated.

**Step 3 — Save and confirmation:**

7. When the user taps "Save":
   - The system constructs the full `BearingRecord` (see §6.1 for schema).
   - The record is written to the encrypted Room database atomically.
   - The capture dialog dismisses.
   - A **confirmation toast** appears: **"Bearing saved as '[name]'"** (localized). The toast is non-blocking, disappears after 3 seconds, and does not prevent compass use.

8. **On successful save:** the user is back in Modern Mode with the compass updating normally. The capture button is available immediately for the next capture.

9. **On save failure** (storage full, database error): a dialog appears: "Unable to save bearing — device storage may be full." with a "Retry" button and a "Cancel" button. "Retry" retries only the database write. "Cancel" discards the capture; the dialog and capture overlay are dismissed and the user returns to Modern Mode.

**GRID north capture prevention:**

10. The `north_type` field of a saved bearing record can only contain `TRUE` or `MAGNETIC`. `GRID` is never written. Because the toggle only offers TRUE or MAGNETIC (FSPEC-TOGGLE, §2.2 step 3), this is guaranteed by the toggle constraint — but the capture logic must additionally assert that `north_type != GRID` before writing and throw a programming-error exception if this invariant is violated.

**Business rules:**
- The capture flow is at most 3 taps from button to saved: (1) tap capture button, (2) edit name if needed + tap "Save", for a total of 2–3 taps. The pre-capture warning dialog (when interference_flag=true) adds one tap but this path is exceptional. The base path (no interference, High confidence) is 2 taps.
- The bearing snapshot is taken at the instant of the capture button tap, not at the instant of the "Save" button tap. The user sees the exact heading that was active when they initiated capture.
- The GPS toggle defaults to ON on first capture. Its state is preserved across captures within the same session (if the user turns it off, it stays off for subsequent captures in the same session). It resets to ON on each new app session.
- The first-capture GPS privacy notice (REQ-CAPTURE-06) is shown exactly once per install — on the first time the capture dialog opens with the GPS toggle visible. A SharedPreferences flag tracks whether the notice has been shown.
- `interference_flag` is set to `true` if the field deviation percentage at capture time is ≥15% OR the inclination deviation at capture time is ≥3°. This is the Moderate interference threshold, not the Warning threshold. The flag captures any meaningful interference, not just the red-warning level.
- The sequential bearing number in the default name is derived from the total count of records in the `bearing_records` table plus one. If the table has 5 records, the next default name is "Bearing 6." This counter never resets (it is not a "within-session" counter).
- **BR-CAP-08 (concurrent save / double-tap debounce):** The capture button MUST be disabled immediately after the first tap and re-enabled only after the save completes (success or failure). Rapid successive taps on the capture button MUST produce exactly one BearingRecord. The button disable-and-re-enable is handled in the ViewModel before and after the database write.
- **First-capture consent persistence:** First-capture detection uses a `SharedPreferences` boolean key `bearing_location_consent_shown`. The key is set to `true` after the first capture dialog is confirmed. Once set to `true`, the privacy notice is never shown again. The INSERT to the database is the critical write; the `SharedPreferences` write is best-effort. If `bearing_location_consent_shown` is lost (e.g., SharedPreferences cleared after a force-stop occurring between the DB insert and the prefs write), the privacy notice will appear once more on the next capture — this is acceptable per REQ-CAPTURE-06. Room and SharedPreferences are separate systems; true atomicity between them is not possible. The INSERT is the priority write.

**Edge cases:**
- User rotates device while capture dialog is open: the dialog remains open. The bearing preview does not change (snapshot). The underlying compass continues to update but is not shown in the dialog.
- User backgrounds and foregrounds the app while the capture dialog is open: the dialog is restored with the same snapshot data. If the OS kills the process, the capture is lost (no partial write).
- GPS fix is lost between the capture tap and the "Save" tap: if the GPS toggle is ON and location was available at capture time, the snapshotted location is used. If no location was available at capture time, the location fields are null even if a GPS fix arrives before "Save" is tapped.

---

### 2.6 FSPEC-DETECTUPGRADE — Interference Detection Upgrade Flow

**Title:** How WMM2025 expected field values replace the Phase 1 Android GeomagneticField baselines in interference detection

**Linked requirements:** REQ-DETECT-01 (conformance upgrade), REQ-DETECT-02 (conformance upgrade), REQ-NORTH-01

**Actors:** System (WMM model, interference detector)

**Preconditions:**
- Phase 1 interference detector is operational
- WMM2025 has been loaded and a location is resolved

**Behavioral flow:**

1. In Phase 1, the interference detector computed field deviation as `|measured_field_magnitude - expected_field_magnitude| / expected_field_magnitude`. The `expected_field_magnitude` was derived from a **rolling sensor EMA** (99% previous / 1% current reading — the `baselineFieldUt` in `CompassViewModel`). Android's `GeomagneticField` class was used only at calibration time (to compute the target sphere radius in `CalibrationEngine`) — it was not used as the runtime interference baseline.

2. In Phase 2, the `expected_field_magnitude` AND `expected_inclination` for interference detection are derived from **WMM2025** via `MagneticFieldModel.getExpectedFieldMagnitude()` and `MagneticFieldModel.getExpectedInclination()`, replacing the Phase 1 sensor EMA baseline.

3. The WMM2025 model provides both values simultaneously (it already computes total field, inclination, and declination in a single evaluation call). No additional computation is required beyond what is already done for true north correction.

4. **When WMM2025 is not available** (no location resolved, WMM expired, load failure): the interference baseline fallback chain is:
   - **(a) WMM2025** — preferred; requires a resolved location and a valid model.
   - **(b) Android `GeomagneticField`** — used when WMM2025 is expired or fails to load, and a resolved location is available (`GeomagneticField` also requires lat/lon/alt/time and cannot serve as a no-location fallback).
   - **(c) Sensor EMA (`baselineFieldUt`)** — the Phase 1 rolling EMA is retained as the no-location fallback, matching the original Phase 1 behavior; or a 50 µT global-average constant if the EMA has not yet stabilized.
   Note: `GeomagneticField` is never used as a fallback when no location is available — it requires coordinates just as WMM2025 does.

5. The interference thresholds (15%/25% magnitude deviation, 3°/8° inclination deviation) are **unchanged** from Phase 1. WMM2025 provides more accurate baseline values, not different thresholds.

6. The interference warning UI behavior (FSPEC-DETECT-01 and FSPEC-DETECT-02 from Phase 1 FSPEC) is unchanged. The user experience of interference detection is identical — only the accuracy of the expected field baseline improves.

7. **UI labeling of the baseline source:** The interference warning banner does not label its source. The source is an internal implementation detail. The user sees the same warning text as in Phase 1.

8. When WMM2025 is active (location available, model valid), the advisory line "Location unavailable — interference detection uses approximate field strength" (from Phase 1 FSPEC-DETECT-01) is **no longer shown** because a real WMM2025 baseline is now in use.

9. When WMM2025 is not available (no location), the same Phase 1 advisory line continues to appear when interference is detected.

**Business rules:**
- The interference detector must always have an expected field value. It can never compute deviation without a baseline. The fallback chain (WMM2025 → GeomagneticField → 50 µT global average) must be exhaustive and cover the no-location case.
- If the WMM2025 expected magnitude differs substantially from the Phase 1 `GeomagneticField` baseline (e.g., >5% difference at the user's location), the interference state may change on Phase 2 upgrade. This is expected and correct behavior — the WMM2025 value is more accurate.
- The same WMM2025 model call that computes declination also provides the interference baseline. The system must not call WMM2025 twice; the result is computed once per location update and cached for both uses.

---

### 2.7 FSPEC-LATITUDE — Extreme Latitude Advisory Flow

**Title:** Advisory shown when WMM-predicted inclination indicates polar magnetic conditions

**Linked requirements:** REQ-NORTH-04, master REQ §11.4

**Actors:** System (evaluates WMM inclination, triggers advisory), User (sees advisory)

**Preconditions:**
- WMM2025 has computed inclination for the current resolved location
- Modern Mode is active

**Behavioral flow:**

1. After each WMM2025 computation, the system checks the absolute value of the predicted inclination: `|inclination_deg| ≥ 80°`.

2a. **If `|inclination_deg| < 80°`:** No extreme latitude advisory is shown. Compass operates normally.

2b. **If `|inclination_deg| ≥ 80°`:** The system activates the extreme latitude advisory:
   - A **persistent amber banner** appears below the compass rose (same visual zone as other non-critical advisories): "Extreme latitude — compass unreliable near magnetic poles" (localized).
   - The confidence badge is capped at **Moderate**. Even if all five Phase 1 confidence dimensions score Good and tilt is ≤5°, the badge cannot exceed Moderate while the extreme latitude advisory is active. The cap is applied as a post-processing constraint, the same way the tilt penalty is applied.
   - The advisory is non-dismissible for the current session. It remains visible as long as the resolved location produces inclination ≥80°.

3. The bearing capture button remains available while the extreme latitude advisory is active. Captured bearings save normally. The `interference_flag` is NOT set to true by the extreme latitude condition alone — it is an advisory, not an interference detection event.

4. **Recovery:** When the location changes (new GPS fix or manual coordinate entry) and the new location produces `|inclination| < 80°`: the advisory disappears and the Moderate cap is removed. Confidence re-evaluates from the full model.

5. If no location is available: the inclination check cannot run. No extreme latitude advisory is shown. This is acceptable because the extreme latitude condition is location-dependent.

**Business rules:**
- The inclination threshold is exactly 80° absolute. `|inclination| = 80.0°` triggers the advisory; `|inclination| = 79.9°` does not.
- The Moderate cap from extreme latitude is separate from and independent of the Moderate cap from no-gyroscope (Phase 1 FSPEC-SENSOR-02). If both are active simultaneously, the result is Moderate (either cap alone produces Moderate as the ceiling).
- The bearing capture button is available during extreme latitude conditions. Capture is not blocked.
- The advisory banner coexists with the interference warning banner if both are active. Both are shown simultaneously (stacked).

---

## 3. Business Rules

The following discrete, testable rules are extracted from the functional flows above. Each rule is independently verifiable.

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-01 | True North computation is fully offline. No network call is ever made to compute declination. | FSPEC-TNORTH |
| BR-02 | `calibration_version` in BearingRecord is the WMM model identifier string returned by `MagneticFieldModel.getModelId()` (e.g., `"WMM2025"` or `"AndroidGeoField"`). This field records the magnetic field model used — NOT the CalibrationRecord schema version integer. These are distinct concepts stored in different tables. | FSPEC-CAPTURE, §6.1 |
| BR-03 | The north type toggle offers exactly two choices: TRUE and MAGNETIC. GRID is never shown in any form (no greyed-out option, no "coming soon" label). | FSPEC-TOGGLE |
| BR-04 | A BearingRecord's `north_type` field must be either `TRUE` or `MAGNETIC`. Writing `GRID` is a programming error and must be asserted against in the capture logic. | FSPEC-CAPTURE |
| BR-05 | A cached location older than 30 days is treated as absent. It does not enable True North mode automatically. | FSPEC-GPS |
| BR-06 | Cache age and cache expiry are computed using the `Clock` interface (`WallClock` in production, `FakeClock` in tests). No direct calls to `System.currentTimeMillis()` in cache age or expiry logic. `WallClock` is used (not `SystemClock`) to avoid collision with `android.os.SystemClock`. | FSPEC-GPS, FSPEC-DECLPANEL |
| BR-07 | Altitude is included in the location cache for WMM computation. If altitude is unavailable, 0 m is used. | FSPEC-GPS |
| BR-08 | The heading change when switching north types must occur within 200 ms of the tap. | FSPEC-TOGGLE |
| BR-09 | The bearing snapshot is taken at the instant of the capture button tap, not at the "Save" tap. | FSPEC-CAPTURE |
| BR-10 | `interference_flag` in BearingRecord is set to `true` if field deviation ≥15% OR inclination deviation ≥3° at capture time. This is the Moderate threshold, not the Warning threshold. | FSPEC-CAPTURE |
| BR-11 | The WMM2025 model result (declination, inclination, total field magnitude) is computed once per location update and reused for both true north correction and interference detection baselines. It is not computed twice. | FSPEC-DETECTUPGRADE |
| BR-12 | Interference detection thresholds (15%/25% magnitude, 3°/8° inclination) are unchanged from Phase 1. Only the expected baseline values become more accurate with WMM2025. | FSPEC-DETECTUPGRADE |
| BR-13 | Extreme latitude advisory fires when `|WMM_inclination_deg| ≥ 80°`. The confidence badge is capped at Moderate while the advisory is active. | FSPEC-LATITUDE |
| BR-14 | The bearing capture button is always visible and always tappable. It is never disabled or hidden, including during interference warnings or extreme latitude conditions. | FSPEC-CAPTURE |
| BR-15 | The GPS privacy notice (first-capture one-time dialog text) is shown exactly once per install. A SharedPreferences flag tracks whether it has been shown. | FSPEC-CAPTURE |
| BR-16 | Coordinates are displayed in the declination info panel masked to 2 decimal places. Full precision is never shown in the UI. | FSPEC-DECLPANEL |
| BR-17 | The `north_label` on all displayed headings must always be "True N", "True N (manual location)", or "Magnetic N". No heading is displayed without a north reference label. | FSPEC-TNORTH, FSPEC-TOGGLE |
| BR-18 | WMM2025 is valid for dates 2025.0–2030.0. Outside this range the app falls back to Android `GeomagneticField` and labels the source accordingly. | FSPEC-TNORTH |
| BR-19 | The capture dialog captures an immutable snapshot of the heading at tap time. The bearing preview in the dialog does not update while the dialog is open. | FSPEC-CAPTURE |
| BR-20 | The first-capture GPS privacy notice is suppressed when location permission is not granted (the GPS toggle is hidden, not disabled). | FSPEC-CAPTURE |
| BR-CAP-08 | The capture button MUST be disabled immediately after the first tap and re-enabled only after the save completes or fails. Rapid successive taps MUST produce exactly one BearingRecord. | FSPEC-CAPTURE |
| BR-LOC-04 | The app MUST show a permission rationale dialog before re-requesting `ACCESS_FINE_LOCATION` if `shouldShowRequestPermissionRationale()` returns `true`. The rationale dialog fires before the system permission prompt. | FSPEC-GPS §2.4 |

---

## 4. Error Scenarios

| Error | Trigger | User-Visible Behavior | System Behavior |
|-------|---------|----------------------|-----------------|
| WMM2025 fails to load (missing asset, parse error) | App launch or first True North activation | Info panel source label: "Android model — may be less accurate". True N still activates using GeomagneticField. Main UI unaffected. | Log error internally; fall back to GeomagneticField for all WMM computations. Do not crash. |
| GPS permission denied | User denies `ACCESS_FINE_LOCATION` | Brief message: "Location permission needed for True North. You can enter coordinates manually instead." Toggle stays on Magnetic N. Manual coordinate entry offered. | Do not request permission again in the same session if denied (system enforces this anyway). |
| GPS permission permanently denied | User previously selected "Don't ask again" | Same message as above, plus: "You can enable location in device Settings." Manual coordinate entry offered. | Direct user to Settings intent if they choose to fix it. |
| No GPS, cache expired (>30 days) | User activates True N with only an expired cache | Dialog: "Enter coordinates for True North or use Magnetic North only." Expired cache is not used. | Evaluate cache expiry using `Clock` interface. |
| No GPS, no cache, manual entry validation fails | User enters non-numeric or out-of-range coordinates | Inline error on coordinate field: "Enter a valid latitude (−90 to 90) and longitude (−180 to 180)." "Use True North" button stays disabled. | Validate on each keypress; enable button only when both fields parse correctly. |
| Bearing save fails (disk full) | Room insert fails | Dialog: "Unable to save bearing — device storage may be full." Retry and Cancel options. | On Retry: re-attempt the insert. On Cancel: dismiss capture overlay, return to Modern Mode. The snapshot is lost. |
| WMM model date out of range (device clock error — pre-2025) | Current date before WMM validity window | Info panel: "WMM2025 not yet valid for this date — using Android model." GeomagneticField fallback active. | Do not use WMM2025 for extrapolation before 2025.0. Fall back gracefully. |
| Extreme latitude — bearing captured | User captures a bearing when `|inclination| ≥ 80°` | No special dialog on capture. Extreme latitude advisory banner is visible. Record saves normally. `interference_flag` is not set by extreme latitude alone. | Save record with all fields. The advisory banner makes the unusual conditions visible. |
| Location cache write fails | Storage error when trying to cache a GPS fix | Silent failure — no user-visible error. The current session continues using the GPS fix in memory for declination. The cache is not updated. | Log internally. The in-memory location remains available for this session. |
| True N active; GPS fix lost mid-session | GPS provider stops delivering fixes | Heading continues using the last cached location for declination. Info panel shows "Cached location" with age. No disruption to the heading display. | Continue using the cached location. Update UI to reflect cached-location source. |

---

## 5. UI State Transitions

### 5.1 North Type Toggle States

```
                         ┌────────────────────────────────────────┐
                         │  State: MAGNETIC_N                      │
                         │  north_label = "Magnetic N"             │
                         │  declination_offset = 0                 │
                         └──────────────┬─────────────────────────┘
                                        │ user taps toggle
                                        ▼
                          ┌─────────────────────────┐
                          │ Location available?      │
                          └─────────────────────────┘
                         YES ↓                    NO ↓
          ┌──────────────────────┐     ┌────────────────────────────┐
          │  State: TRUE_N        │     │  Show coordinate dialog     │
          │  north_label = "True N"│    │                            │
          │  declination_offset =  │    │  User enters coords?       │
          │    WMM2025(lat,lon,alt)│    └────────────┬───────────────┘
          │  heading changes       │                 │
          │  within 200 ms         │       YES ↓              NO ↓
          └──────────────────────┘  ┌──────────────┐  ┌──────────────────┐
                                    │ State:        │  │ Stay: MAGNETIC_N  │
                                    │ TRUE_N_MANUAL │  └──────────────────┘
                                    │ north_label = │
                                    │ "True N       │
                                    │  (manual      │
                                    │  location)"   │
                                    └───────────────┘

  From any TRUE_N state: user taps toggle → State: MAGNETIC_N (immediate, no dialog)
```

### 5.2 Bearing Capture Dialog States

```
  [Compass active] → user taps capture button
         │
         ▼
  [Snapshot taken at tap time]
         │
         ▼
  interference_flag? ─── NO ──► [Name/Notes dialog — confidence High or Moderate]
         │                              │
        YES                         user taps Save
         │                              │
         ▼                              ▼
  [Interference warning dialog]   [BearingRecord written to DB]
         │                              │
  "Save with warning" / "Cancel"        ▼
         │                        [Toast: "Bearing saved as '[name]'"]
  Save with warning
         │
         ▼
  [Name/Notes dialog — with ⚠ flag pre-set]
         │
   user taps Save
         │
         ▼
  [BearingRecord written to DB, interference_flag=true]
         │
         ▼
  [Toast: "Bearing saved as '[name]'"]
```

### 5.3 Location Resolution State Machine

| State | Description | Declination Source | north_label (if True N active) |
|-------|-------------|-------------------|-------------------------------|
| GPS_FRESH | GPS fix received in last 60 s, permission granted | WMM2025(fresh GPS) | "True N" |
| GPS_CACHED | No fresh fix; cached location ≤30 days old | WMM2025(cached) | "True N" |
| GPS_MANUAL | No fix, cache absent/expired; user entered coordinates | WMM2025(manual) | "True N (manual location)" |
| GPS_UNAVAILABLE | No fix, no cache, no manual entry | N/A (True N cannot activate) | "Magnetic N" (forced) |
| GPS_PERMISSION_DENIED | Permission denied, no manual entry | N/A | "Magnetic N" (forced) |

### 5.4 WMM Model State Machine

| State | Condition | Effect |
|-------|-----------|--------|
| WMM_ACTIVE | Current date in [2025.0, 2030.0] AND asset loaded successfully | WMM2025 used for all computations |
| WMM_EXPIRED | Current date > 2030.0 | Android GeomagneticField used; info panel shows "Android model — may be less accurate" |
| WMM_NOT_YET_VALID | Current date < 2025.0 (clock error) | Android GeomagneticField used; info panel shows note about date |
| WMM_LOAD_FAILED | Asset missing, parse error | Android GeomagneticField used; same label as WMM_EXPIRED; internal error logged |

### 5.5 Extreme Latitude Advisory State

| State | `|inclination_deg|` | Confidence Cap | Advisory Banner |
|-------|---------------------|----------------|-----------------|
| NORMAL | < 80° | None (Phase 1 rules apply) | Hidden |
| EXTREME_LATITUDE | ≥ 80° | Moderate | Visible (amber, non-dismissible) |

---

## 6. Data Contracts

### 6.1 BearingRecord Schema

This is the complete schema for the `bearing_records` Room entity introduced in `Migration(1,2)`.

| Field | Type (Kotlin) | Column Type (SQLite) | Nullable | Constraint | Description |
|-------|--------------|----------------------|----------|------------|-------------|
| `id` | `String` | `TEXT` | No | Primary key, UUID v4 | Unique identifier for the record |
| `name` | `String` | `TEXT` | No | Length 1–100, trimmed | User-assigned name for the bearing |
| `bearing_deg` | `Float` | `REAL` | No | [0.0, 360.0) | Heading in degrees at capture time, normalized; `Float` is consistent with sensor pipeline output (REQ §5.3.1 canonical value) |
| `north_type` | `String` | `TEXT` | No | `"TRUE"` or `"MAGNETIC"` only | North reference used; GRID is forbidden |
| `confidence` | `String` | `TEXT` | No | `"HIGH"`, `"MODERATE"`, or `"POOR"` | Confidence level at capture time |
| `captured_at` | `Long` | `INTEGER` | No | > 0, UTC epoch ms | Timestamp of capture button tap; stored internally as `Long` epoch milliseconds (Room-idiomatic `INTEGER` column); display formatting uses ISO 8601 UTC at the presentation layer |
| `calibration_version` | `String` | `TEXT` | No | Non-empty | WMM model identifier active at capture time (e.g., `"WMM2025"` or `"AndroidGeoField"`); provided by `MagneticFieldModel.getModelId(): String`. This field records the magnetic field model used — NOT the CalibrationRecord schema version. See normative note below. |
| `field_deviation_pct` | `Float` | `REAL` | No | ≥ 0.0 | Field magnitude deviation percentage at capture time |
| `inclination_deviation_deg` | `Float` | `REAL` | No | ≥ 0.0 | Inclination deviation in degrees at capture time |
| `interference_flag` | `Int` | `INTEGER` | No | 0 or 1 (Room Boolean) | 1 if field_deviation_pct ≥ 15% OR inclination_deviation_deg ≥ 3° at capture; 0 otherwise |
| `lat` | `Double?` | `REAL` | Yes | [−90.0, 90.0] or NULL | GPS latitude at capture time; NULL if GPS toggle OFF or no fix; `Double` precision required for WMM computation accuracy (≈11 cm at 6 decimal places) |
| `lon` | `Double?` | `REAL` | Yes | [−180.0, 180.0) or NULL | GPS longitude at capture time; NULL if GPS toggle OFF or no fix; `Double` precision (see `lat`) |
| `alt_m` | `Double?` | `REAL` | Yes | NULL permitted | GPS altitude in meters; NULL if unavailable |
| `notes` | `String?` | `TEXT` | Yes | Length 0–1000 or NULL | Optional user notes; NULL if not entered (professional use case supports longer notes per REQ §5.3.1) |
| `display_mode` | `String?` | `TEXT` | Yes | `"MODERN"` or NULL (Phase 3 adds `"LUOPAN"`) | Display mode active at capture time; `"MODERN"` in Phase 2 |

**Notes on specific fields:**

- **`id`:** UUID v4 string (e.g., `"550e8400-e29b-41d4-a716-446655440000"`). Not an auto-increment integer, to support future sync/export without collision.
- **`calibration_version` (normative note):** This field records the WMM model identifier used during the bearing capture session. It is populated by `MagneticFieldModel.getModelId(): String` at capture time. Valid values in Phase 2: `"WMM2025"` (when the WMM2025 bundled model is active) or `"AndroidGeoField"` (when the Android GeomagneticField fallback is active). **This field records the WMM model used, NOT the calibration record schema version.** The `CalibrationRecord.calibration_schema_version` integer (a separate field in the `calibration_records` table defined by the Room migration number) MUST NOT be conflated with this field. The `calibration_records` table is not altered in Migration(1,2).
- **`bearing_deg` (canonical type):** `Float` is the canonical type per REQ §5.3.1. This is consistent with the sensor fusion pipeline output. Float provides approximately ±0.001° precision for an angle in [0, 360), which is more than sufficient for compass applications.
- **`lat` / `lon` (canonical types):** `Double?` is the canonical type. GPS coordinates require Double precision (6 decimal places ≈ 11 cm) for accurate WMM input computation. Float would provide only ≈11 km precision, which is insufficient.
- **`captured_at` (canonical type):** `Long` epoch milliseconds is the canonical internal type. ISO 8601 UTC formatting is applied at the display layer only.
- **`notes` (max length):** 1000 characters per REQ §5.3.1 (supports professional field note use case).
- **`interference_flag`:** Stored as `INTEGER` (0 or 1) for SQLite compatibility with Room's `@ColumnInfo` boolean mapping.
- **`display_mode`:** Set to `"MODERN"` for all captures in Phase 2. The field is included now to avoid a schema migration when Phase 3 adds Luopan Mode.

### 6.2 Clock Interface

The `Clock` interface is introduced in Phase 2 to allow injection of a testable time source for cache age display and cache expiry calculation.

> **Note:** The production implementation is named `WallClock` (not `SystemClock`) to avoid a naming collision with `android.os.SystemClock`, which is in scope in all Android files. `WallClock` is a distinct class in `com.luopan.compass.util`.

```kotlin
// com.luopan.compass.util.Clock
interface Clock {
    fun nowMs(): Long
}

// Production implementation (name chosen to avoid collision with android.os.SystemClock)
class WallClock : Clock {
    override fun nowMs(): Long = System.currentTimeMillis()
}

// Test implementation
class FakeClock(private var nowMs: Long) : Clock {
    override fun nowMs(): Long = nowMs
    fun advance(ms: Long) { nowMs += ms }
    fun set(ms: Long) { nowMs = ms }
}
```

The `Clock` interface is used in:
- Location cache expiry evaluation (`Clock.nowMs() - cache.timestamp_ms`)
- Declination info panel "N days ago" cache age display
- Calibration age computation (replaces direct calls to `System.currentTimeMillis()` in `CompassViewModel.loadCalibrationAge()` to make it testable — this is a Phase 2 cleanup opportunity for the Phase 1 code path)

### 6.3 MagneticFieldModel Interface

The WMM2025 model and its GeomagneticField fallback are accessed through the `MagneticFieldModel` interface. This interface **does not yet exist in the codebase** — it must be created in Phase 2. Both `WMM2025Engine` and `AndroidGeomagneticFieldAdapter` implement it, enabling `FakeMagneticFieldModel` injection in tests.

> **`epochYears` parameter:** `epochYears` = year + dayOfYear / 365.25 (e.g., 2025.5 = approximately 2 July 2025).

```kotlin
// com.luopan.compass.magnetic.MagneticFieldModel
interface MagneticFieldModel {
    /** Expected total field magnitude in µT for interference detection baseline. */
    fun getExpectedFieldMagnitude(lat: Double, lon: Double, altMeters: Double, epochYears: Double): Float

    /** Expected magnetic inclination (dip angle) in degrees, positive downward. */
    fun getExpectedInclination(lat: Double, lon: Double, altMeters: Double, epochYears: Double): Float

    /** Magnetic declination in decimal degrees, positive east. */
    fun getDeclination(lat: Double, lon: Double, altMeters: Double, epochYears: Double): Float

    /** WMM model identifier string (e.g., "WMM2025", "AndroidGeoField"). Used to populate BearingRecord.calibration_version. */
    fun getModelId(): String

    /** Returns true if the model is past its validity window and the fallback should be used. */
    fun isExpired(): Boolean
}
```

| Method | Return value | Unit | Description |
|--------|-------------|------|-------------|
| `getExpectedFieldMagnitude(...)` | `Float` | µT | Total field magnitude for interference detection baseline |
| `getExpectedInclination(...)` | `Float` | degrees | Magnetic inclination (dip angle), positive downward |
| `getDeclination(...)` | `Float` | degrees | Magnetic declination, positive east |
| `getModelId()` | `String` | — | Model identifier; e.g., `"WMM2025"` or `"AndroidGeoField"` |
| `isExpired()` | `Boolean` | — | `true` if the model is past its validity window |

---

## 7. Acceptance Tests

Each test maps to a REQ §8 scenario with precise Given/When/Then assertions.

---

### AT-A: True North Switching (REQ §8 Scenario A)

**Linked flow:** FSPEC-TOGGLE (§2.2), FSPEC-TNORTH (§2.1)

| # | Given | When | Then | Precise Assertion |
|---|-------|------|------|-------------------|
| A-01 | Phase 1 complete (calibrated, interference-free); GPS fix available; Magnetic N active; current declination is D degrees | User taps the north type toggle | Heading value changes by D degrees within 200 ms | `abs(new_heading - (old_heading + D)) < 0.05°` and transition completes in ≤200 ms |
| A-02 | A-01 preconditions; toggle has switched to True N | Toggle label observed | Label reads "True N" (localized) | North reference label in main UI is exactly "True N" (the localized string key `north_label_true`) |
| A-03 | True N is active (following A-01) | User taps the toggle again to switch back | Heading returns to Magnetic N value | `abs(restored_heading - original_magnetic_heading) < 0.05°`; label reads "Magnetic N" |
| A-04 | A-03 completed | Switch back confirmed | No dialog is shown when switching back to Magnetic N | No dialog, modal, or bottom sheet appears during the Magnetic N revert |

---

### AT-B: Offline Declination with GPS (REQ §8 Scenario B)

**Linked flow:** FSPEC-TNORTH (§2.1), FSPEC-GPS (§2.4)

| # | Given | When | Then | Precise Assertion |
|---|-------|------|------|-------------------|
| B-01 | No internet connection (airplane mode or no SIM); recent GPS fix available; True North mode active | System computes declination | WMM2025 bundled model is used (no network call) | Network request log is empty; declination value matches WMM2025 reference for the test location within ±0.1° |
| B-02 | B-01 preconditions | User opens declination info panel | Source label shows "WMM2025 (valid to 2030)" | Info panel source field is exactly `"WMM2025 (valid to 2030)"` (localized) |
| B-03 | B-01 preconditions; known NOAA reference declination for test coordinates (lat, lon) is R degrees | Declination value in info panel observed | `|computed_declination - R| ≤ 0.1°` | Absolute error ≤0.1° vs. NOAA WMM2025 online calculator for the same location and date |

---

### AT-C: No GPS, Cached Location (REQ §8 Scenario C)

**Linked flow:** FSPEC-GPS (§2.4), FSPEC-TOGGLE (§2.2), FSPEC-DECLPANEL (§2.3)

| # | Given | When | Then | Precise Assertion |
|---|-------|------|------|-------------------|
| C-01 | GPS unavailable (provider off or permission denied); cached location exists with age = 15 days; True North activated | System resolves location | Declination computed from cached location | `calibration_version` in session = `"WMM2025"`; declination matches WMM2025 result for cached lat/lon |
| C-02 | C-01 preconditions | User opens declination info panel | Info panel shows coordinates type "Cached location" and cache age | Coordinates type field reads "Cached location"; "last updated" date is 15 days before `Clock.nowMs()` (±1 day tolerance for display) |
| C-03 | C-01 preconditions | North label in main UI observed | Heading is labeled "True N" | North reference label is "True N" (not "Magnetic N", not "True N (manual location)") |

---

### AT-D: No GPS, No Cache (REQ §8 Scenario D)

**Linked flow:** FSPEC-TOGGLE (§2.2 step 4c), FSPEC-GPS (§2.4)

| # | Given | When | Then | Precise Assertion |
|---|-------|------|------|-------------------|
| D-01 | GPS has never been available on this device (clean install, no fix ever obtained, no cache); user taps True North toggle | Toggle tapped | Dialog appears with title and coordinate fields | Dialog text contains the localized string: "Enter coordinates for True North or use Magnetic North only" |
| D-02 | D-01 dialog is open; user enters valid coordinates (lat=35.68, lon=139.69) and taps "Use True North" | "Use True North" tapped | Declination computed from entered coordinates; heading changes; mode is True N | North label reads "True N (manual location)"; declination info panel shows coordinates type "Manual entry" |
| D-03 | D-01 dialog is open | User taps "Use Magnetic North" or dismisses dialog | Mode stays Magnetic N | North label remains "Magnetic N"; no heading change |
| D-04 | D-01 dialog is open; user enters out-of-range latitude (lat=95.0) | "Use True North" button state | Button is disabled | "Use True North" button is not tappable; inline error shows "Enter a valid latitude (−90 to 90)" |

---

### AT-E: Bearing Capture (REQ §8 Scenario E)

**Linked flow:** FSPEC-CAPTURE (§2.5)

| # | Given | When | Then | Precise Assertion |
|---|-------|------|------|-------------------|
| E-01 | Confidence is "High"; True North active; GPS fix available | User taps capture button, enters name "Site Alpha", taps Save | Record saved successfully | `BearingRecord.north_type = "TRUE"`; `BearingRecord.confidence = "HIGH"`; `BearingRecord.name = "Site Alpha"`; toast shows "Bearing saved as 'Site Alpha'" |
| E-02 | E-01 preconditions | Record inspected in DB | All required fields populated | `id` is non-null UUID; `captured_at` > 0; `calibration_version` is `"WMM2025"` or `"AndroidGeoField"`; `bearing_deg` ∈ [0.0, 360.0); `lat` and `lon` are non-null (GPS toggle ON) |
| E-03 | Confidence is "Moderate"; True North active | User captures bearing "Site Beta" | Record saved with correct confidence | `BearingRecord.confidence = "MODERATE"` |
| E-04 | Confidence is "Poor" (interference active; field deviation = 30%) | User taps capture button | Pre-capture interference warning dialog appears | Dialog contains "Interference detected or confidence is Poor. Bearing will be saved with a warning flag. Proceed?" before name entry |
| E-05 | E-04 dialog visible | User taps "Save with warning" and enters name "Bad Site", taps Save | Record saved with interference_flag | `BearingRecord.interference_flag = true`; toast shows "Bearing saved as 'Bad Site'"; Phase 4 history will show ⚠ marker |
| E-06 | E-04 dialog visible | User taps "Cancel" | Capture flow abandoned; no record created | No new row in `bearing_records` table; user returned to Modern Mode |
| E-07 | First-ever capture on this device; GPS permission granted | User opens capture dialog | GPS privacy notice is visible | One-time privacy notice text "Your location is saved on-device only and is never shared" is present in the capture dialog |
| E-08 | Second or subsequent capture (privacy notice already shown) | User opens capture dialog | GPS privacy notice is NOT visible | Notice text is absent from the dialog |
| E-09 | Any capture scenario | `north_type` field value inspected | `north_type` is never "GRID" | `BearingRecord.north_type ∈ {"TRUE", "MAGNETIC"}` — assertion throws if "GRID" is attempted |

---

### AT-F: WMM Interference Baseline Upgrade (REQ §8 Scenario F)

**Linked flow:** FSPEC-DETECTUPGRADE (§2.6)

| # | Given | When | Then | Precise Assertion |
|---|-------|------|------|-------------------|
| F-01 | WMM2025 is active (location available, model valid); device near interference source | Interference check runs | Expected field magnitude is from WMM2025, not Android GeomagneticField | Unit test: inject a location and date; assert that `expected_field_ut` returned by the model under test equals the WMM2025 result, not the GeomagneticField result (values must differ by a known amount for the test location) |
| F-02 | WMM2025 active; device field deviation 30% from WMM2025 expected | System evaluates interference | Interference warning fires using WMM2025 baseline | Warning appears within 2 seconds; banner deviation values reflect WMM2025-computed expected field |
| F-03 | WMM2025 active; interference at 20% deviation from WMM baseline | System evaluates | Moderate interference state | `InterferenceState = MODERATE`; no red banner (Moderate is amber-level per confidence model) |
| F-04 | No location available (WMM cannot compute) | Interference check runs | Android GeomagneticField or 50 µT fallback used | Advisory line "Location unavailable — interference detection uses approximate field strength" appears alongside any interference warning |

---

### AT-G: Additional Phase 2 Scenarios

**Linked flow:** FSPEC-LATITUDE (§2.7), FSPEC-GPS (§2.4), FSPEC-TNORTH (§2.1)

| # | Given | When | Then | Precise Assertion |
|---|-------|------|------|-------------------|
| G-01 | Resolved location produces WMM inclination = 83° (high-latitude test coordinates) | System evaluates inclination | Extreme latitude advisory appears | Amber advisory banner visible; confidence badge capped at Moderate |
| G-02 | G-01 active | Confidence dimensions all Good; tilt ≤5° | Badge still shows Moderate | `OverallConfidence = MODERATE` despite all five Phase 1 dimensions being Good |
| G-03 | G-01 active | User taps capture button and saves bearing | Record saves normally; interference_flag not set by latitude alone | `BearingRecord.interference_flag = false` (assuming field and inclination deviations are both below Moderate threshold) |
| G-04 | G-01 active; user moves to low-latitude location (inclination < 80°) | New GPS fix received | Advisory disappears; Moderate cap removed | Banner hidden; confidence re-evaluates to High if all other conditions Good |
| G-05 | True N active; WMM2025 computed date is 2030.5 (post-expiry) | System checks WMM validity | Fallback to Android GeomagneticField | Info panel source shows "Android model — may be less accurate"; heading still labeled "True N" |
| G-06 | `Clock.nowMs()` controlled by FakeClock in test; cache timestamp set to 31 days ago | Location cache expiry evaluated | Cache treated as expired | `isLocationCacheValid() = false` |
| G-07 | `Clock.nowMs()` controlled by FakeClock; cache timestamp set to exactly 30 days ago | Location cache expiry evaluated | Cache treated as valid | `isLocationCacheValid() = true` |
| G-08 | GRID option not present in any UI element | Phase 2 build running | GRID never appears in toggle, dialog, or any visible UI | Espresso: no view with text matching `"Grid"` or `"GRID"` exists in the view hierarchy on the main screen or capture dialog |

---

---

### AT-NFR-01: Cold-Start Performance (REQ-NFR-08)

**Linked flow:** REQ §5.4 REQ-NFR-08

**Measurement method:** Android Macrobenchmark (`StartupMode.COLD` / `StartupMode.WARM`). Measurement window: from `Application.onCreate()` to first `CompassUiState` emission where `headingState` is not `STABILIZING` (i.e., the first rendered heading frame). Minimum 5 iterations; report the **median** result. Reference device class: mid-range Android device as defined in REQ §5.4.

| # | Given | When | Then | Precise Assertion |
|---|-------|------|------|-------------------|
| NFR-01 | Fresh process start (`StartupMode.COLD`); WMM coefficients on-disk (not in-memory); no GPS fix cached | App launches to first non-STABILIZING heading frame | Elapsed time ≤ 5 s | Macrobenchmark median over ≥5 iterations ≤ 5000 ms (`StartupMode.COLD`) |
| NFR-02 | Warm process start (`StartupMode.WARM`); WMM coefficients and GPS cache already loaded | App resumes to first non-STABILIZING heading frame | Elapsed time ≤ 3 s | Macrobenchmark median over ≥5 iterations ≤ 3000 ms (`StartupMode.WARM`) |

---

*End of FSPEC-luopan-p2-true-north-capture v0.2-draft*
