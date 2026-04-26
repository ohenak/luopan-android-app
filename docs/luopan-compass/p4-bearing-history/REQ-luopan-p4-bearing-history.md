# REQ-luopan-p4-bearing-history
## Phase 4: Bearing History, Recalibration Refinements, and Sensor Diagnostics

| Field | Value |
|-------|-------|
| **Version** | 0.1-draft |
| **Date** | 2026-04-23 |
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
| History search | Search by name |
| Swipe to delete | With 5-second undo toast |
| Interference flag | Records captured under Poor confidence show "⚠ Captured under interference" in history view |
| Recalibration prompts | Non-blocking banner when calibration is >30 days old; automatic prompt when systematic field drift detected for >60 s |
| Sensor diagnostic log | On first launch after Phase 4 install: `sensor_profile.json` written to internal storage with device model, Android version, sensor types, resolution, range |

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
| REQ-CAPTURE-03 | Bearing history screen | P1 | Sorted by captured_at (newest first); per-row: name, bearing + north type, confidence badge, date/time; swipe-to-delete with undo toast (5 s); search by name; tap to expand full record details and notes |

### 5.2 Interference Flag in Records

| ID | Title | Priority | Key Spec |
|----|-------|----------|----------|
| REQ-DETECT-05 | Interference flag in bearing record | P1 | When interference_flag=true in saved record, history row shows "⚠ Captured under interference" badge; full record detail shows field_deviation_pct and inclination_deviation_deg at time of capture |

### 5.3 Recalibration Lifecycle

| ID | Title | Priority | Key Spec |
|----|-------|----------|----------|
| REQ-CAL-05 | Recalibration prompts | P1 | Prompt when: (a) calibration age >30 days — non-blocking banner; (b) systematic drift detected — measured field magnitude at stable, low-interference location differs from calibration-time expected by >10% for >60 consecutive seconds. Prompts dismissible; app remains usable; confidence automatically downgraded when calibration age >30 days |

### 5.4 Sensor Capability Logging

| ID | Title | Priority | Key Spec |
|----|-------|----------|----------|
| REQ-SENSOR-07 | Per-device sensor capability logging | P2 | On first launch after Phase 4 installs: write `sensor_profile.json` to internal app storage (never transmitted). Contents: device model + manufacturer, Android version + API level, available sensor types + names, reported resolution and range per sensor. Useful for diagnosing per-OEM accuracy issues. |

---

## 6. Requirements Deferred from Phase 4

| ID | Title | Deferred to |
|----|-------|-------------|
| REQ-CAPTURE-05 | Share / export (CSV) | Phase 5 |

---

## 7. Phase 4 End-to-End Acceptance Test

**Scenario A — Bearing history screen**

*Given* 10 bearings were saved across two field sessions (Phase 2/3 functionality),  
*When* user opens Bearing History,  
*Then*:
- All 10 records visible in scrollable list, newest first
- Each row shows: name, bearing + north type label, confidence badge, timestamp
- Tapping a row expands it to show all fields including notes

**Scenario B — Interference flag display**

*Given* one saved bearing has interference_flag=true (captured under Poor confidence),  
*When* that record is visible in Bearing History,  
*Then*:
- "⚠ Captured under interference" badge is shown on the row
- Expanding the record shows field_deviation_pct and inclination_deviation_deg at time of capture

**Scenario C — Swipe to delete**

*Given* any record in Bearing History,  
*When* user swipes the row left,  
*Then*:
- Record removed from list immediately
- "Undo" toast appears for 5 seconds
- Tapping "Undo" restores the record in its original position

**Scenario D — Recalibration prompt (age)**

*Given* the current calibration is 31 days old,  
*When* the app is opened,  
*Then*:
- A non-blocking banner appears: "Your calibration is 31 days old — consider recalibrating"
- User can dismiss the banner; the compass remains fully functional
- Confidence is capped at "Moderate" until recalibration occurs (calibration age >30 days condition per master REQ §8.1)

**Scenario E — Recalibration prompt (drift)**

*Given* the device is stationary outdoors in a low-interference environment,  
*When* the measured field magnitude differs from the calibration-time expected magnitude by >10% continuously for >60 seconds,  
*Then*:
- Banner appears: "Magnetic environment may have changed — recalibrate for best accuracy"
- Banner is dismissible; recalibration can be launched directly from the banner

**Scenario F — Sensor profile**

*Given* Phase 4 installs on a device for the first time,  
*When* the app launches,  
*Then*:
- `sensor_profile.json` is written to internal app storage
- File is accessible via Android file manager (internal/files path) but not network-accessible
- File contains device model, all sensor types with names, resolution, and range

---

## 8. Open Questions and Risks Specific to Phase 4

**Risk P4-R1 — Drift detection false positives:** The "10% deviation for >60 s" rule for recalibration prompts may trigger when the user is in a legitimately different magnetic environment (e.g., traveled to a different city) rather than when calibration has decayed. This is by design — the user should recalibrate in a new environment anyway. Wording of the prompt should be: "Magnetic environment may have changed" (not "Your calibration is wrong").

**Risk P4-R2 — Bearing history performance:** With hundreds of records, the history screen must remain performant. Engineering should paginate or use a `RecyclerView` with virtualization; the requirement does not prescribe implementation but does require smooth scrolling and no jank.
