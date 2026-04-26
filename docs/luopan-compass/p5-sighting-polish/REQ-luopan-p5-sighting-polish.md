# REQ-luopan-p5-sighting-polish
## Phase 5: Sighting Mode, Grid North, Export, and UX Polish

| Field | Value |
|-------|-------|
| **Version** | 0.1-draft |
| **Date** | 2026-04-23 |
| **Status** | Draft |
| **Phase** | 5 of 5 |
| **Parent REQ** | [REQ-luopan-compass v0.2-draft](REQ-luopan-compass.md) |
| **Prerequisite** | Phase 4 complete and stable |
| **Delivery plan** | [DELIVERY-PLAN.md](DELIVERY-PLAN.md) |

---

## 1. Goal

Complete the feature set: add **sighting mode** (camera + crosshair for line-of-sight bearings), **grid north** for surveyors, **data export** for report workflows, and **UX polish** including heading smoothing control, dark/light luopan theme, and advanced calibration bypass. This phase has no blocking new persona — it deepens utility for Personas 2, 3, and power users of all types.

After Phase 5, the app is feature-complete for v1.

---

## 2. Usable Application State After Phase 5

| Capability | Detail |
|-----------|--------|
| Sighting Mode | Rear camera viewfinder with centered crosshair; real-time bearing of crosshair; bearing captured on tap; CAMERA permission handling |
| Grid North | UTM grid convergence applied; north type toggle adds Grid N option; grid zone computed from GPS |
| Bearing history export | Share individual bearing as plain text; export full history as CSV via Android Share sheet |
| Heading smoothing | Slider in settings: Fast (lower lag, more jitter) ↔ Smooth (higher lag, less jitter); default midpoint |
| Dark/Light Luopan theme | System dark/light mode followed in Modern Mode; Luopan Mode uses its own dark-wood aesthetic by default; "Light Luopan" theme toggle for high-ambient-light use |
| Manual calibration bypass | Advanced settings: enter hard-iron offset values manually; quality forced to "Fair" |

---

## 3. Personas Deepened After Phase 5

| Persona | New Benefit |
|---------|-------------|
| Persona 3 — Yamada (hiker/antenna) | Sighting mode for line-of-sight antenna bearing; heading smoothing for stable antenna azimuth |
| Persona 2 — Engineer Chen | Grid North for survey notes; CSV export for report |
| Persona 1 — Master Li | Light Luopan theme for outdoor use in bright sunlight |
| Advanced users | Manual calibration bypass for custom workflows |

---

## 4. User Stories Active in This Phase

| ID | Title |
|----|-------|
| US-01 | Taking a bearing on a distant object *(newly active — sighting mode)* |
| US-06 | Reviewing saved bearings *(improved — export)* |
| US-09 | Checking declination *(improved — grid north)* |

---

## 5. Requirements in Scope

### 5.1 Sighting Mode

| ID | Title | Priority | Key Spec |
|----|-------|----------|----------|
| REQ-DISPLAY-07 | Sighting Mode — camera overlay | P1 | Rear camera viewfinder; centered crosshair overlay; bearing of crosshair displayed in real time at top of screen; CAMERA permission handling (graceful fallback if denied) |
| REQ-DISPLAY-08 | Sighting Mode — bearing capture from crosshair | P1 | Capture button saves crosshair bearing with full REQ-CAPTURE-01 schema; confirmation toast; camera continues running; no photo stored in record |

**Sighting Mode — permission denied path:**  
If CAMERA permission is denied, Sighting Mode shows: "Camera permission is required for Sighting Mode. [Open Settings]". Falls back to Modern Mode without crashing.

**Sighting Mode — confidence in sighting:**  
The sighting crosshair bearing uses the same confidence model as Modern Mode (master REQ §8). Confidence badge is visible in sighting mode overlay.

### 5.2 Grid North

| ID | Title | Priority | Key Spec |
|----|-------|----------|----------|
| REQ-DECL-03 | Grid North support | P2 | Grid convergence angle from UTM zone (computed from GPS coordinates); added to magnetic declination; north type toggle adds "Grid N" option; labeled "Grid N" on all headings when selected |

### 5.3 Export

| ID | Title | Priority | Key Spec |
|----|-------|----------|----------|
| REQ-CAPTURE-05 | Share / export | P2 | (a) Share individual bearing as plain text via Android Share sheet; (b) Export full bearing history as CSV file via Share sheet. CSV columns: id, name, bearing_deg, north_type, confidence, captured_at, lat, lon, interference_flag, notes |

### 5.4 UX Polish

| ID | Title | Priority | Key Spec |
|----|-------|----------|----------|
| REQ-DISPLAY-09 | Heading smoothing control | P2 | Slider in Settings: Fast (lag ≤30 ms add., jitter ≤1° RMS) ↔ Smooth (lag ≤100 ms add., jitter ≤0.2° RMS); default midpoint |
| REQ-DISPLAY-12 | Dark / light theme | P2 | Modern Mode follows system dark/light; Luopan Mode uses dark-wood theme by default; "Light Luopan" toggle available in display settings |

### 5.5 Advanced Calibration

| ID | Title | Priority | Key Spec |
|----|-------|----------|----------|
| REQ-CAL-06 | Manual calibration bypass | P2 | Advanced settings screen: enter hard-iron offset (3-value vector in µT); saves as calibration with quality forced to "Fair"; overrides ellipsoid-fit result; intended for users with desktop calibration tools |

---

## 6. Phase 5 End-to-End Acceptance Test

**Scenario A — Sighting Mode basic flow**

*Given* Phase 4 is complete, CAMERA permission granted,  
*When* user switches to Sighting Mode,  
*Then*:
- Rear camera preview is visible
- Centered crosshair overlay is shown
- Real-time bearing of crosshair direction is shown at top of screen, updated ≥20 Hz
- Confidence badge is visible

*When* user taps the capture button,  
*Then*:
- Bearing record saved with crosshair bearing at moment of tap
- Confirmation toast shown
- Camera continues running

**Scenario B — Sighting Mode, no camera permission**

*Given* CAMERA permission is denied,  
*When* user selects Sighting Mode,  
*Then*:
- Informational screen shown: "Camera permission needed for Sighting Mode. [Open Settings]"
- No crash; no blank screen

**Scenario C — Grid North**

*Given* GPS available, user is in a UTM-zoned region,  
*When* user selects "Grid N" from the north type toggle,  
*Then*:
- Grid convergence is added to declination; heading labeled "Grid N"
- Declination info panel shows both magnetic declination and grid convergence angle separately

**Scenario D — Export CSV**

*Given* 5 bearings saved in history,  
*When* user taps "Export History" → Android Share sheet,  
*Then*:
- CSV file with all 5 records is shared via the chosen Share target
- CSV includes all REQ-CAPTURE-01 fields; sensitive fields (lat/lon) present only if location was consented at capture time

**Scenario E — Heading smoothing**

*Given* user sets smoothing to "Fast" in Settings,  
*When* device is slowly rotated,  
*Then*:
- Heading lag relative to gyroscope is ≤80 ms total (50 ms baseline + ≤30 ms smoothing add)
- Visible jitter ≤1° RMS over 10 s stationary window

*Given* user sets smoothing to "Smooth",  
*When* device is stationary,  
*Then*:
- Visible jitter ≤0.2° RMS over 10 s
- Additional smoothing lag ≤100 ms

**Scenario F — Light Luopan theme**

*Given* user enables "Light Luopan" theme in display settings,  
*When* Luopan Mode is opened,  
*Then*:
- Ring colors use a lighter palette (light parchment background, dark ink characters) vs. the default dark lacquer
- All 6 rings remain legible; text contrast ratio ≥4.5:1

---

## 7. Out of Scope After Phase 5 (Deferred to v2)

Everything in [master REQ §12](REQ-luopan-compass.md#12-out-of-scope-for-v1) remains out of scope:

| Item | v2 Candidate? |
|------|--------------|
| iOS port | Yes |
| 七十二龍 / 六十龍 rings | Yes — with per-device accuracy gate |
| 天盤/人盤/地盤 triple-plate | Yes |
| 擇日 integration | Separate product |
| AR Mountain overlay on camera | Yes |
| Cloud sync | Yes — with privacy architecture |
| WMMHR2025 high-resolution model | Low value at phone magnetometer resolution |

---

## 8. Open Questions and Risks Specific to Phase 5

**Risk P5-R1 — Camera latency in Sighting Mode:** Android camera preview latency varies significantly by device (30–150 ms). The compass heading updates at ≤50 ms latency (REQ-NFR-01) but the camera frame may lag. The crosshair bearing must be computed from the sensor heading (fast path), not from image processing. This is an implementation constraint engineering must respect.

**Risk P5-R2 — Grid convergence accuracy:** UTM grid convergence varies within a zone. The standard formula from GPS lat/lon is accurate to <0.01° within most zones. Engineering should use an established library (e.g., proj4 or equivalent) rather than a hand-rolled formula.

**Risk P5-R3 — CSV export privacy:** The export includes GPS coordinates from bearing records (where location was consented at capture time). Engineering must ensure: (a) coordinates are included only when the user consented at capture (interference_flag field indicates this was checked); (b) the Share sheet dialog gives the user a final preview of what is being shared.
