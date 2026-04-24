# REQ-luopan-p2-true-north-capture
## Phase 2: True North, Magnetic Declination, and Bearing Capture

| Field | Value |
|-------|-------|
| **Version** | 0.1-draft |
| **Date** | 2026-04-23 |
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
| REQ-NORTH-03 | GPS location handling | P0 | Use current GPS; fall back to cached (≤30 days); prompt for manual entry if no location available; cache stored locally only |
| REQ-NORTH-04 | North reference label on all headings | P0 | Every displayed heading value shows "True N", "Magnetic N", or "Grid N" (Grid N deferred to Phase 5) |

**Upgrade to Phase 1 interference detection:** With WMM2025 now available, REQ-DETECT-01 and REQ-DETECT-02 MUST switch from `GeomagneticField`-based expected values to WMM2025-based expected values. No new requirement ID — this is a conformance upgrade of existing requirements.

### 5.2 Declination Management

| ID | Title | Priority | Key Spec |
|----|-------|----------|----------|
| REQ-DECL-01 | North type toggle | P0 | True N / Magnetic N toggle visible in main UI (Grid N deferred); applies to all display modes simultaneously |
| REQ-DECL-02 | Declination info panel | P1 | Shows: declination in °E/°W + decimal, source label, coordinates (masked to 2 dp), last-updated date |

### 5.3 Bearing Capture

| ID | Title | Priority | Key Spec |
|----|-------|----------|----------|
| REQ-CAPTURE-01 | Bearing record schema | P0 | All required fields: id, name, bearing_deg, north_type, confidence, captured_at, calibration_version, field_deviation_pct, inclination_deviation_deg, interference_flag, optional lat/lon/alt/notes/display_mode |
| REQ-CAPTURE-02 | Capture flow UX | P0 | Name input dialog; bearing preview; optional notes; ≤3 taps from button to saved |
| REQ-CAPTURE-04 | Data persistence (encrypted) | P0 | SQLite/Room with SQLCipher or equivalent; survives force-stop |
| REQ-CAPTURE-06 | Location privacy confirmation | P0 | First-capture dialog explains location is optional and on-device only; toggle defaults to ON |

### 5.4 Non-Functional

| ID | Title | Priority | Target |
|----|-------|----------|--------|
| REQ-NFR-08 | Cold-start time | P1 | ≤3 s to first heading (warm cache); ≤5 s (cold start) |

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
- Heading value changes by the local declination within 200 ms
- Label changes to "True N"
- Switching back restores "Magnetic N" and the original value

**Scenario B — Offline declination with GPS**

*Given* the device has no internet connection but has a recent GPS fix,  
*When* True North mode is active,  
*Then*:
- WMM2025 bundled model computes declination correctly (within ±0.1° of NOAA reference for the given location)
- Declination info panel shows "WMM2025 (valid to 2030)"
- No network request is made

**Scenario C — No GPS, cached location**

*Given* GPS is unavailable; a cached location from 15 days ago is stored,  
*When* True North mode is activated,  
*Then*:
- Declination computed from cached location
- UI shows "Using last known location (15 days ago)"
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
- Record saved with: bearing_deg, north_type=TRUE, confidence=HIGH (or MODERATE), timestamp, GPS coordinates, calibration_version, field_deviation_pct
- Confirmation toast: "Bearing saved as '[name]'"
- Capturing with confidence "Poor": bearing saves with interference_flag=true; record visually marked "⚠ Captured under interference" (visible in Phase 4 history; in Phase 2 a toast warns before saving)

**Scenario F — WMM interference baseline upgrade**

*Given* WMM2025 is now available (Phase 2),  
*When* the interference check runs,  
*Then*:
- Expected field magnitude and inclination are derived from WMM2025, not Android `GeomagneticField`
- Interference thresholds (15%/25% magnitude, 3°/8° inclination) apply with WMM-accurate baselines

---

## 9. Open Questions and Risks Specific to Phase 2

**Risk P2-R1 — WMM2025 licensing:** Confirm NOAA/BGS public domain status before bundling. See [master REQ §14 OQ-01](REQ-luopan-compass.md#oq-01-wmm2025-licensing-and-bundling). This is a blocker for REQ-NORTH-01.

**Risk P2-R2 — GPS permission rejection:** If the user denies `ACCESS_FINE_LOCATION`, all location-dependent features (declination, bearing GPS coordinates) degrade gracefully. REQ-NORTH-03 and REQ-CAPTURE-06 both handle this; engineering must test the denied-permission path.

**Risk P2-R3 — Cached location age:** The 30-day cache cutoff is an assumption. Feedback from beta practitioners may suggest a shorter window (7 days) in areas of high magnetic secular variation (e.g., Alaska, Siberia).
