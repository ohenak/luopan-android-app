# REQ-luopan-p1-core-compass
## Phase 1: Core Compass — Magnetic Heading with Calibration and Interference Detection

| Field | Value |
|-------|-------|
| **Version** | 0.1-draft |
| **Date** | 2026-04-23 |
| **Status** | Draft |
| **Phase** | 1 of 5 |
| **Parent REQ** | [REQ-luopan-compass v0.2-draft](REQ-luopan-compass.md) |
| **Prerequisite** | None — first delivery |
| **Delivery plan** | [DELIVERY-PLAN.md](DELIVERY-PLAN.md) |

---

## 1. Goal

Ship a magnetic compass that is **measurably more accurate and more honest** than the stock Android compass. The user can calibrate their specific device, understand the quality of their reading at a glance, and be warned when magnetic interference makes the reading unreliable.

No luopan display, no true north, no bearing save in this phase.

---

## 2. Usable Application State After Phase 1

| Capability | Detail |
|-----------|--------|
| Magnetic heading | Real-time display, updated ≥20 Hz, latency <50 ms sensor-to-screen |
| North reference | Magnetic north only (true north comes in Phase 2) |
| Calibration | Guided figure-8 flow; ellipsoid fitting; Good/Fair/Poor quality score; persisted per device |
| Confidence indicator | High / Moderate / Poor badge (colored + text) reflecting calibration quality, field deviation, noise variance, and tilt |
| Interference detection | Field magnitude and inclination deviation; red warning overlay with explanation when deviation exceeds thresholds |
| Localization | zh-Hant, zh-Hans, ja, en — all UI strings including warnings and calibration instructions |
| Edge case handling | No magnetometer → error screen; no gyroscope → advisory; broken sensor → "Sensor error" badge; vertical hold → tilt warning |
| Performance | ≤5% battery/hour active; sensors released in background; ≥99.5% crash-free |

**What Phase 1 is NOT:** There is no bearing save, no luopan display, no true north, no sighting mode.

---

## 3. Personas Served After Phase 1

| Persona | Status | What they can do |
|---------|--------|-----------------|
| Persona 4 — Ms. Wang (general consumer) | **Fully served** | Better compass than stock; understands calibration; gets interference warnings |
| Persona 3 — Yamada (hiker/outdoor) | **Partially served** | Core compass works; sighting mode deferred to Phase 5 |
| Persona 2 — Engineer Chen (surveyor) | **Not yet served** | Missing true north and bearing save (Phase 2) |
| Persona 1 — Master Li (feng shui) | **Not yet served** | Missing luopan mode (Phase 3) |

---

## 4. User Stories Active in This Phase

| ID | Title | Status |
|----|-------|--------|
| US-03 | Calibrating in the field | Active |
| US-04 | Detecting magnetic interference | Active |
| US-08 | General consumer quick bearing check | Active |
| US-11 | Holding the device vertically | Active |

User stories US-01, US-02, US-05–07, US-09–10, US-12 are deferred to later phases.

---

## 5. Requirements in Scope

All requirement specifications (description, acceptance criteria) are in the [master REQ §6](REQ-luopan-compass.md#6-functional-requirements).

### 5.1 Sensor Acquisition and Fusion

| ID | Title | Priority | Phase Note |
|----|-------|----------|-----------|
| REQ-SENSOR-01 | Uncalibrated magnetometer | P0 | Primary data source; fallback to `TYPE_MAGNETIC_FIELD` if unavailable |
| REQ-SENSOR-02 | Accelerometer / gravity | P0 | Tilt and horizontal field component |
| REQ-SENSOR-03 | Gyroscope + fallback | P0 | 9-DOF fusion; degrade gracefully if missing |
| REQ-SENSOR-04 | 9-DOF sensor fusion algorithm | P0 | Engineering selects Madgwick / Mahony / EKF; bounded by NFR targets |
| REQ-SENSOR-05 | Sampling rate / power management | P0 | Release listeners on background; resume on foreground |
| REQ-SENSOR-06 | Noise variance tracking | P1 | Rolling RMS over 2 s; feeds confidence model |

**Phase 1 Implementation Note — REQ-SENSOR-04 (SE-06):** The fusion algorithm MUST maintain internal orientation state as a unit quaternion. Euler angles MAY be derived from the quaternion for display, and MUST use gimbal-lock-safe decomposition (via rotation matrix, not naive Euler extraction). The heading value passed to the display layer MUST remain finite at all device orientations including vertical hold (pitch = ±90°).

**Phase 1 Implementation Note — REQ-SENSOR-05 (SE-05):** Sensor listeners MUST be unregistered in `Activity.onStop()` and re-registered in `Activity.onStart()`. Do NOT unregister in `onPause()` — doing so interrupts sensor delivery during transient focus loss events (notification overlays, permission dialogs).

### 5.2 Calibration

| ID | Title | Priority | Phase Note |
|----|-------|----------|-----------|
| REQ-CAL-01 | Calibration data collection | P0 | Figure-8 guided flow; ≥200 samples; 3D coverage check |
| REQ-CAL-02 | Ellipsoid fitting | P0 | Hard-iron vector + soft-iron matrix; residual RMS computed |
| REQ-CAL-03 | Calibration quality scoring | P0 | Good / Fair / Poor; determines max confidence level |
| REQ-CAL-04 | Calibration persistence and versioning | P0 | Encrypted local storage; current + 1 rollback |

**Phase 1 Implementation Note — REQ-CAL-01 "3D Coverage" definition (SE-07):** Coverage is measured in raw magnetometer device-frame axes (X, Y, Z in µT). For each axis: `axis_range = max(samples) − min(samples)`. `total_range = max(axis_range across all three axes)`. `Coverage_i = axis_range_i / total_range`. Good: all Coverage_i ≥ 20%; Fair: all ≥ 15%; Poor: any < 15%.

**Phase 1 Implementation Note — REQ-CAL-02 soft-iron target radius (SE-02):** In Phase 1, the soft-iron correction target radius uses Android `GeomagneticField` as a best-effort fallback (same as REQ-DETECT-01). If no location is available, a hardcoded 50 µT global-average seed is used. This is a known limitation; Phase 2 corrects it with WMM2025 and GPS.

**Phase 1 Implementation Note — REQ-CAL-04 calibration age start time (SE-08):** Calibration age = `current_utc_time − calibration_saved_at`. The clock starts at the moment the user confirms the calibration result and it is written to storage. "Last used" time is not tracked for aging purposes.

**Phase 1 Implementation Note — REQ-CAL-04 WMM field magnitude field (SE-02):** The "WMM-expected field magnitude" field in the persisted calibration record stores the `GeomagneticField` result, or "50 µT (fallback)" when no location is available.

**Phase 1 Acceptance Criterion — REQ-CAL-04 atomic writes (TE-07):** Calibration data writes MUST be atomic (e.g., write to temp file then rename, or Room database transaction). A process kill during write MUST leave the prior calibration intact and readable.

### 5.3 Magnetic Interference Detection

| ID | Title | Priority | Phase Note |
|----|-------|----------|-----------|
| REQ-DETECT-01 | Field magnitude check | P0 | Compare measured vs. WMM-expected; <15%/15-25%/>25% thresholds. **Note:** In Phase 1, "expected" uses a best-effort estimate without WMM (Android `GeomagneticField`). WMM2025 arrives in Phase 2. |
| REQ-DETECT-02 | Inclination check | P0 | Compare measured vs. expected inclination; <3°/3-8°/>8° thresholds |
| REQ-DETECT-03 | Interference warning UX | P0 | Red overlay; explanation; continued heading display; dismissible; confidence → Poor |
| REQ-DETECT-04 | Noise spike rejection | P1 | Single-sample outliers >10 µT from rolling mean rejected from fusion |

**Phase 1 Implementation Note — REQ-DETECT-01 no-location fallback (SE-04 / TE-02):** In Phase 1 (no GPS), the expected field magnitude for interference detection uses Android `GeomagneticField` with the device's last known coarse location (via `getLastKnownLocation(LocationManager.NETWORK_PROVIDER)` if available, or `LocationManager.PASSIVE_PROVIDER`). If no location is available at all, a hardcoded 50 µT global-average is used and the UI shows an advisory: "Location unavailable — interference detection uses approximate field strength." The inclination deviation check (REQ-DETECT-02) has the same fallback.

**Phase 1 Boundary Convention (TE-04):** All threshold ranges use half-open intervals (lower-inclusive, upper-exclusive) except where marked:
- Field deviation: `[0%, 15%)` → Good; `[15%, 25%)` → Moderate; `[25%, ∞)` → Poor
- Inclination deviation: `[0°, 3°)` → Good; `[3°, 8°)` → Moderate; `[8°, ∞)` → Poor
- Calibration residual: `[0, 1.0 µT]` → Good; `(1.0, 2.0 µT]` → Fair; `(2.0 µT, ∞)` → Poor
- Calibration age: `[0, 7 days]` → Good; `(7, 30 days]` → Moderate; `(30 days, ∞)` → Poor
- Noise variance: `[0, 0.1 µT²]` → Good; `(0.1, 0.5 µT²]` → Moderate; `(0.5 µT², ∞)` → Poor
- Tilt: `[0°, 5°]` → no penalty; `(5°, 20°]` → clamp to Moderate; `(20°, ∞)` → clamp to Poor

**Phase 1 Implementation Note — REQ-DETECT-01 warning clearance hysteresis (TE-11):** The interference warning clears ONLY after field deviation has remained continuously below 15% for ≥3 seconds. Any excursion above 15% during the 3-second window resets the timer.

### 5.4 Display — Modern Mode

| ID | Title | Priority | Phase Note |
|----|-------|----------|-----------|
| REQ-DISPLAY-01 | Mode switching | P0 | In Phase 1, only Modern Mode is accessible; the mode switcher MUST be hidden entirely. Luopan and Sighting Mode entries MUST NOT be visible to users until their respective phases are deployed. |
| REQ-DISPLAY-02 | Modern Mode compass rose | P0 | 360° rose, 0.1° digital readout, north reference label, confidence badge, cal age, tilt indicator |
| REQ-DISPLAY-03 | Confidence indicator | P0 | High/Moderate/Poor badge with color + text; accessible to color-blind users |
| REQ-DISPLAY-10 | Screen-on wake lock | **P0 (elevated)** | Prevents screen sleep during compass use |
| REQ-DISPLAY-11 | Accessibility — text scaling | P1 | Respect system font size; 16sp minimum for primary heading |

**Phase 1 Implementation Note — REQ-DISPLAY-02 "visible" definition (TE-09):** For test purposes, "visible without scrolling" means: each of the six UI elements has layout bounds fully within the screen viewport as reported by Espresso `isDisplayed()`, verified on a 5.0-inch 1080×1920 density-420 emulator (e.g., Pixel 4a API 34 emulator at default density).

### 5.5 Confidence Model

The confidence model is defined in [master REQ §8.3](REQ-luopan-compass.md#83-confidence-score-computation). The following clarification block supersedes the pseudocode in §8.3 and is the authoritative implementation reference (SE-03 / TE-01):

```
step1 = min(score_d for all d in {field_deviation, inclination_deviation, cal_quality, cal_age, noise_variance})
if tilt > 20°:
    overall_score = min(step1, POOR)      # clamp to 1; cannot raise a score
elif tilt > 5°:
    overall_score = min(step1, MODERATE)  # clamp to 2; no effect if already MODERATE or POOR
else:
    overall_score = step1
Result: 3 → "High" | 2 → "Moderate" | 1 → "Poor"
```

**Note:** The tilt penalty can only lower or preserve a score, never raise it.

**Acceptance Criteria (additional rows):**

| Who | Given | When | Then |
|-----|-------|------|------|
| App | All five dimensions Good; tilt 2° | Stationary heading | Confidence badge shows "High" |
| App | All dimensions Good except cal_age (31 days old) | Stationary heading | Confidence capped at "Moderate" |
| App | Field deviation 30%, all others Good | Stationary heading | Confidence shows "Poor"; interference warning shown |
| App | Already-Moderate (score=2); tilt 12° | Stationary heading | Result MUST be MODERATE — clamp has no effect: min(2, MODERATE=2) = 2 |
| App | Already-Poor (score=1); tilt 12° | Stationary heading | Result MUST be POOR — clamp has no effect: min(1, MODERATE=2) = 1 |

### 5.6 Localization

| ID | Title | Priority | Phase Note |
|----|-------|----------|-----------|
| REQ-L10N-01 | Four supported locales | P0 | zh-Hant, zh-Hans, ja, en; all Phase 1 UI strings translated |
| REQ-L10N-03 | RTL exclusion | P0 | Explicit non-requirement; document confirms LTR-only |

### 5.7 Non-Functional Requirements

| ID | Title | Priority | Target |
|----|-------|----------|--------|
| REQ-NFR-01 | Heading latency | P0 | <50 ms sensor-to-screen at 95th percentile |
| REQ-NFR-02 | Heading update rate | P0 | ≥20 Hz |
| REQ-NFR-03 | Battery | P0 | ≤5% per hour active; 0% in background |
| REQ-NFR-04 | SDK targets | P0 | Min SDK 26; target SDK 35 |
| REQ-NFR-05 | Full offline operation | P0 | All Phase 1 features work without network |
| REQ-NFR-06 | Data privacy | P0 | No data transmitted; calibration data encrypted locally |
| REQ-NFR-07 | Sensor sampling rates | P0 | Magnetometer ≈50 Hz; accel/gyro ≈100 Hz |
| REQ-NFR-09 | Crash-free rate | P0 | ≥99.5% sessions at 30-day rolling average |

**Phase 1 Implementation Note — REQ-NFR-01 vsync (SE-09):** Latency is measured from `SensorEvent.timestamp` to the `View.onDraw()` callback (or equivalent Compose draw phase). vsync latency (~16 ms) is excluded from this budget and accepted as a platform constraint. Total perceived latency including vsync is therefore approximately 66 ms.

**Phase 1 Test Method — REQ-NFR-01 (TE-05):** Instrument via `SensorEventListener.onSensorChanged()` timestamp and `Choreographer.FrameCallback` timestamp across a 60-second session at 30°/s on a mid-range reference device (e.g., Pixel 4a API 34 emulator or equivalent). vsync excluded. Report 95th percentile of per-event deltas.

**Phase 1 Test Method — REQ-NFR-02 (TE-05):** Count distinct `invalidate()` calls that produce a changed heading value per second.

**Phase 1 Test Method — REQ-NFR-03 (TE-05):** Android Battery Historian `--report`, screen on at 50% brightness, 4000 mAh reference device. Target: app wakelocks < 5% of total 1-hour energy draw.

**Phase 1 Test Method — REQ-NFR-09 (TE-05):** Denominator: sessions (app foreground launch-to-background), minimum 1000 sessions before metric is valid. Includes JVM exceptions, native crashes, and ANRs.

**Required Android Permissions in Phase 1 (SE-11):** `android.permission.WAKE_LOCK` (normal permission, required by REQ-DISPLAY-10). `ACCESS_FINE_LOCATION` is NOT requested in Phase 1 (arrives in Phase 2 with GPS).

---

## 6. Requirements Deferred from Phase 1

| ID | Title | Priority | Deferred to Phase |
|----|-------|----------|--------------------|
| REQ-SENSOR-07 | Sensor capability logging | P2 | 4 |
| REQ-CAL-05 | Recalibration prompts | P1 | 4 |
| REQ-CAL-06 | Manual calibration bypass | P2 | 5 |
| REQ-DETECT-05 | Interference flag in bearing record | P1 | 4 |
| REQ-NORTH-01–04 | True north / WMM2025 | P0 | 2 |
| REQ-DISPLAY-04–09, 12 | Luopan mode, sighting mode, smoothing, theme | P0–P2 | 3 (04–06), 5 (07–09, 12) |
| REQ-CAPTURE-01–06 | Bearing capture and history | P0–P2 | 2 (01, 02, 04, 06), 4 (03), 5 (05) |
| REQ-DECL-01–03 | Declination management | P0–P2 | 2 (01–02), 5 (03) |
| REQ-L10N-02 | Luopan terminology | P0 | 3 |
| REQ-LUOPAN-01–02 | Luopan ring content | P0 | 3 |
| REQ-NFR-08 | Cold-start time <3 s | P1 | 2 |

---

## 7. Phase 1 Accuracy and Calibration Constraints

**Applies in full from Phase 1.** See [master REQ §8](REQ-luopan-compass.md#8-accuracy-specification) and [§9](REQ-luopan-compass.md#9-calibration-ux-requirements) for complete specifications.

**Phase 1 note on REQ-DETECT-01 (field magnitude check):** WMM2025 is not yet available in Phase 1. The "expected" field magnitude for the interference check uses Android's `GeomagneticField` API. This is less accurate than WMM2025 but sufficient to detect gross interference. The interference threshold logic (15%/25% deviation) applies identically.

**Phase 1 note on REQ-DETECT-03 capture block (TE-03):** In Phase 1, bearing capture (REQ-CAPTURE-01–06) does not exist. The "bearing capture disabled until user manually overrides" behavior from master REQ §11.5 is vacuously satisfied. No override button is rendered in Phase 1. The interference warning is warn-only. The capture-block and override UI are introduced in Phase 2 when bearing capture arrives.

**Phase 1 note on REQ-CAL-02 / REQ-CAL-04 WMM fallback (SE-02):** The soft-iron correction target radius uses Android `GeomagneticField` as a best-effort fallback. If no location is available at all, a hardcoded 50 µT global-average seed is used. The "WMM-expected field magnitude" field in REQ-CAL-04 stores the `GeomagneticField` result or "50 µT (fallback)" when no location is available. This is a known limitation; Phase 2 corrects it with WMM2025 and GPS.

---

## 8. Edge Cases in Scope for Phase 1

From [master REQ §11](REQ-luopan-compass.md#11-edge-cases-and-failure-modes):

| §11 Section | Scenario | Required Behavior |
|-------------|----------|------------------|
| §11.1 | No magnetometer | Error screen; no crash; no misleading heading |
| §11.2 | No gyroscope | Advisory; confidence capped at Moderate; heading functional |
| §11.3 | Broken / stuck sensor | "Sensor error" badge within 3 s; confidence Poor |
| §11.5 | Strong local interference (>50% deviation) | Red warning; capture not yet available — warn only; no override button in Phase 1 (override button introduced in Phase 2 with bearing capture) |
| §11.6 | Device held vertically | Tilt indicator; confidence capped at Moderate |
| §11.7 | Rapid rotation (>180°/s) | "Stabilizing…" badge; confidence Moderate during motion |
| §11.9 | Power-saving mode | Advisory if sensor rate drops; no forced change |

Edge cases §11.4 (extreme latitude) and §11.8 (GPS unavailable) are Phase 2 because they depend on WMM and GPS.

---

## 9. Phase 1 End-to-End Acceptance Test

The following holistic scenarios validate that Phase 1 delivers its promised usable state. Individual requirement acceptance criteria are in the master REQ.

**Scenario A — First-time user opens app (no calibration)**

*Given* the app is installed and opened for the first time on a device with magnetometer and gyroscope,  
*When* the user reaches the main screen,  
*Then*:
- Modern Mode displays with a compass rose rotating in real time
- A "Needs Calibration" badge is shown; confidence is "Poor"
- A non-blocking prompt or banner invites the user to calibrate
- The app does not crash or freeze

**Scenario B — Calibration flow**

*Given* the user enters the calibration flow from the banner or menu,  
*When* the user performs the figure-8 motion until the completion gate is met (≥200 samples, adequate 3D coverage),  
*Then*:
- A quality score (Good/Fair/Poor) is displayed with the residual RMS
- Accepting the calibration persists it; the confidence badge updates to reflect the new calibration quality
- The calibration age indicator shows "Cal: 0d"

**Scenario C — Good-conditions accuracy** [Manual-Field]

*Given* the device is calibrated (Good quality), held level (tilt <5°), outdoors away from interference,  
*When* a stationary heading is observed for 30 seconds,  
*Then*:
- Confidence badge shows "High"
- Heading jitter is ≤0.3° RMS
- Heading latency to physical rotation is <50 ms (verified by engineering instrumentation)
- Heading accuracy is within ±1° of reference compass (verified in QA against §8.4 protocol)

**Note on Scenario C test execution (TE-06):** Scenario C (accuracy ±1°) is a [Manual-Field] test using the §8.4 protocol. It is a pre-release gate test, not a CI automated test. The automated CI proxy is: inject a pre-recorded 30-second sensor sequence (magnetometer + accelerometer + gyroscope) at a known heading into the fusion algorithm as a unit test, and assert computed heading is within ±0.5° of ground truth.

**Scenario D — Interference detected**

*Given* the user is near a large steel structure or electrical panel,  
*When* the field magnitude deviation exceeds 25% of expected,  
*Then*:
- Red interference warning overlay appears within 2 seconds
- Confidence badge changes to "Poor"
- The heading continues to display with the warning badge — it is NOT zeroed or frozen
- No capture button or override button is shown in Phase 1 (bearing capture is introduced in Phase 2)
- Moving away from the interference source causes the warning to clear automatically after field deviation has remained continuously below 15% for ≥3 seconds (any excursion above 15% resets the timer)

**Scenario E — No magnetometer**

*Given* a device with no magnetometer sensor,  
*When* the app is opened,  
*Then*:
- An error screen is shown: device cannot support compass
- No heading or dial is displayed
- The app does not crash

**Scenario F — Localization**

*Given* the device system locale is `ja` (Japanese),  
*When* the app is opened,  
*Then*:
- All UI text, labels, warning messages, and calibration instructions appear in Japanese
- No English strings visible in the main interface

**Scenario G — Calibration cancellation (TE-07)**

*Given* the user is in the calibration flow and has collected >50 samples,  
*When* the user taps Back or Cancel,  
*Then*:
- All partial data is discarded
- The previous calibration is intact (rollback copy preserved)
- The app returns to the main screen with the previous confidence level
- No "Needs Calibration" badge is shown unless no prior calibration existed

**Scenario H — Poor-quality calibration acceptance (TE-07)**

*Given* the user completes the figure-8 motion but residual RMS > 2.0 µT (Poor quality),  
*When* the user taps "Accept anyway" (after seeing the Poor quality warning),  
*Then*:
- Calibration is saved with quality=Poor
- The confidence badge shows "Poor" immediately (REQ-DISPLAY-03)
- No recalibration prompt is shown in Phase 1 (REQ-CAL-05 is deferred to Phase 4)
- The user is informed via the warning shown before acceptance

**Scenario I — OOM kill during calibration (TE-07)**

*Given* the app is killed by the OS during figure-8 data collection (before the user taps "Done"),  
*When* the app restarts,  
*Then*:
- The previous calibration is intact (no partial write — atomic write enforced per REQ-CAL-04)
- The app starts fresh with "Needs Calibration" badge if no prior calibration existed, or continues with the previous calibration if one existed
- No corrupted calibration state is possible (write-then-rename or Room transaction guarantees this)

---

## 10. Open Questions and Risks Specific to Phase 1

**Risk P1-R1 — Sensor rate on low-end devices:** Android may not honor `SENSOR_DELAY_GAME` on budget API 26 devices, resulting in <20 Hz delivery. Engineering must verify on oldest supported hardware and implement graceful degradation with an advisory.

**Risk P1-R2 — Calibration quality on budget magnetometers:** Some budget devices have magnetometers with ±5 µT full-scale resolution. Ellipsoid fitting residual may never reach the "Good" threshold (<1.0 µT) on these devices. Engineering should cap confidence at "Moderate" for devices where residual consistently exceeds 2.0 µT after multiple full calibrations.

**Risk P1-R3 — Android `GeomagneticField` accuracy for interference baseline:** Using `GeomagneticField` (not WMM2025) as the expected field magnitude in Phase 1 may produce false positives in regions where the Android model is stale. This is a known limitation; it is resolved in Phase 2 when WMM2025 is bundled.
