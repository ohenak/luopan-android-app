# REQ-luopan-compass: Product Requirements Document
## Luopan — High-Accuracy Android Compass App

| Field | Value |
|-------|-------|
| **Version** | 0.3-draft |
| **Date** | 2026-04-23 |
| **Revised** | 2026-04-23 (added 後天八卦 方位 labels, 十二地支 ring; decomposed into phase REQs) |
| **Status** | Draft — Canonical Master Reference |
| **Author** | Product |
| **Feature branch** | feat-luopan-compass |

> **This is the canonical master REQ.** It has been decomposed into five iterative delivery phases. Engineers work from the phase REQ for scope and from this master REQ for detailed requirement specs.
>
> **Delivery plan and phase REQ documents:** [DELIVERY-PLAN.md](DELIVERY-PLAN.md)
> | Phase | File | Goal |
> |-------|------|------|
> | 1 | [REQ-luopan-p1-core-compass.md](REQ-luopan-p1-core-compass.md) | Core magnetic compass, calibration, interference detection |
> | 2 | [REQ-luopan-p2-true-north-capture.md](REQ-luopan-p2-true-north-capture.md) | WMM2025 true north, bearing capture |
> | 3 | [REQ-luopan-p3-luopan-mode.md](REQ-luopan-p3-luopan-mode.md) | Full luopan display, 坐向 lock |
> | 4 | [REQ-luopan-p4-bearing-history.md](REQ-luopan-p4-bearing-history.md) | Bearing history screen, recalibration prompts |
> | 5 | [REQ-luopan-p5-sighting-polish.md](REQ-luopan-p5-sighting-polish.md) | Sighting mode, grid north, export, polish |

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Problem Statement](#2-problem-statement)
3. [Goals and Success Criteria](#3-goals-and-success-criteria)
4. [Target Users and Personas](#4-target-users-and-personas)
5. [User Stories](#5-user-stories)
6. [Functional Requirements](#6-functional-requirements)
   - 6.1 [Sensor Acquisition and Fusion — REQ-SENSOR](#61-sensor-acquisition-and-fusion--req-sensor)
   - 6.2 [Hard-Iron and Soft-Iron Calibration — REQ-CAL](#62-hard-iron-and-soft-iron-calibration--req-cal)
   - 6.3 [Magnetic Interference Detection — REQ-DETECT](#63-magnetic-interference-detection--req-detect)
   - 6.4 [True North Correction — REQ-NORTH](#64-true-north-correction--req-north)
   - 6.5 [Compass Display Modes — REQ-DISPLAY](#65-compass-display-modes--req-display)
   - 6.6 [Bearing Capture and History — REQ-CAPTURE](#66-bearing-capture-and-history--req-capture)
   - 6.7 [Declination Management — REQ-DECL](#67-declination-management--req-decl)
   - 6.8 [Localization — REQ-L10N](#68-localization--req-l10n)
   - 6.9 [Luopan Ring Content — REQ-LUOPAN](#69-luopan-ring-content--req-luopan)
7. [Non-Functional Requirements — REQ-NFR](#7-non-functional-requirements--req-nfr)
8. [Accuracy Specification](#8-accuracy-specification)
9. [Calibration UX Requirements](#9-calibration-ux-requirements)
10. [Luopan Mode Specification](#10-luopan-mode-specification)
11. [Edge Cases and Failure Modes](#11-edge-cases-and-failure-modes)
12. [Out of Scope for v1](#12-out-of-scope-for-v1)
13. [Prioritization and MVP Cutline](#13-prioritization-and-mvp-cutline)
14. [Open Questions and Risks](#14-open-questions-and-risks)
15. [Success Metrics](#15-success-metrics)
16. [Traceability Matrix](#16-traceability-matrix)

---

## 1. Executive Summary

**Luopan** is a high-accuracy Android compass application designed for professional and serious use cases where a silently wrong heading is worse than no heading at all. The app targets 風水 (feng shui) practitioners who require readings at the 60 分金 / 24 Mountains granularity (6°–15° per division), field surveyors, outdoor technical users, and precision-minded general consumers.

The stock Android compass is insufficient for these users because it does not expose calibration confidence, silently absorbs large errors from hard-iron distortion and magnetic interference, and provides no luopan-style display. Research confirms phone compass errors of 10°–20° are common in uncalibrated or interference-affected scenarios — an error that maps to one or more full 山 (Mountains) on a luopan, rendering any 坐向 assessment invalid.

Luopan solves this by combining rigorous sensor fusion, transparent confidence signaling, and an authentic luopan display mode alongside a modern compass view.

---

## 2. Problem Statement

### 2.1 Why the Stock Android Compass Fails for Luopan-Grade Use

| Problem | Impact on Luopan Use |
|---------|---------------------|
| No hard-iron / soft-iron calibration visible to the user | Phone-specific magnetic distortion (from speakers, camera modules, batteries) introduces constant heading offsets of 5°–30°. Users cannot know if this offset has been corrected. |
| No calibration age or quality indicator | A well-calibrated device driven through magnetic interference silently loses accuracy. The heading continues to display to 0.1° false precision. |
| No magnetic interference warning | Being near steel-framed buildings, vehicles, HVAC equipment, or electrical panels introduces 10°–90° errors with no warning. |
| Bundled WMM may be outdated | Android's `GeomagneticField` class uses a hardcoded WMM epoch that may lag by years, accumulating true-north error of 0.1°–1°+ per year of drift. |
| No luopan display | Practitioners must mentally map a 0–360° digital readout onto the 24 Mountains ring, creating transcription errors and slow workflow. |
| No bearing record | Practitioners typically photograph the screen — an unreliable, unstructured practice that loses metadata (confidence, location, time). |
| No reading orientation guidance | The phone may be held vertically or at a tilt, introducing azimuth error that the app does not flag. |

### 2.2 Market Gap

Existing luopan apps on Android (e.g., *LuoPan Compass*, *Feng-Shui Compass*) display traditional ring graphics but do not implement calibration flows, interference detection, or confidence scoring. They inherit whatever heading Android's `TYPE_ORIENTATION` (deprecated) or `TYPE_ROTATION_VECTOR` provides — which can be ±10°–20° in real-world conditions — and display it without caveat. For a feng shui practitioner making a 坐向 call on a property, this error is professionally unacceptable.

### 2.3 Root Cause

The 24 Mountains granularity is 15° per 山. The 60 分金 granularity is 6° per division. Achieving confident readings at these levels requires heading accuracy better than ±3° under moderate conditions and approaching ±1° under good conditions. This requires:
- Per-device sensor calibration (hard-iron and soft-iron correction)
- Active interference detection
- Sensor fusion that compensates for gyroscope drift and magnetometer noise
- Honest, visible confidence signaling

---

## 3. Goals and Success Criteria

### 3.1 Primary Goals

| Goal ID | Goal | Measurable Criterion |
|---------|------|---------------------|
| G-01 | Achieve ±1° heading accuracy under good conditions | Validated against a surveyor-grade reference compass or sun-shadow method at ≥10 known-bearing test points |
| G-02 | Never silently display an inaccurate heading | Zero user reports of the app showing a confident heading that differed from reference by >3° when the confidence indicator showed "High" |
| G-03 | Provide an authentic, legible luopan display | ≥4.0 avg rating on luopan usability from feng shui practitioners in beta |
| G-04 | Support four locales at parity | All UI, all luopan ring text, all error messages available in zh-Hant, zh-Hans, ja, en |
| G-05 | Work fully offline | All core features functional without network, including WMM declination computation |

### 3.2 Non-Goals (v1)

- Replacing a physical luopan for formal professional certification work
- Providing 擇日 (date selection) functionality
- Providing geomantic advice or interpretations

---

## 4. Target Users and Personas

### Persona 1: 李師傅 (Master Li) — Professional Feng Shui Consultant

| Attribute | Detail |
|-----------|--------|
| **Age** | 55 |
| **Role** | Independent feng shui consultant, 25 years practice |
| **Location** | Hong Kong, frequently travels to Taiwan, Mainland China, Southeast Asia |
| **Device** | High-end Android flagship (Samsung Galaxy S-series or Pixel), updated annually |
| **Language** | Traditional Chinese (zh-Hant) primary; reads Simplified Chinese |
| **Primary use** | Determining 坐向 of residential and commercial buildings; 龍穴 site assessment |
| **Tools today** | Physical luopan (羅盤) with 72-ring 三元 style; Samsung phone compass as sanity-check only |
| **Pain points** | Phone compass off by 1–2 山 from physical luopan; cannot use phone in buildings with steel structures |
| **Accuracy expectation** | **Must achieve ±1° under good conditions.** Needs to read 分金 level (6° divisions) with confidence. Would accept ±3° with an explicit "degraded" indicator and use the physical luopan instead. |
| **Critical requirement** | The app must tell him when it cannot be trusted. A lying compass is worse than no compass. |

---

### Persona 2: 陳工程師 (Engineer Chen) — Civil Surveyor

| Attribute | Detail |
|-----------|--------|
| **Age** | 42 |
| **Role** | Licensed surveyor, small engineering firm |
| **Location** | Taiwan |
| **Device** | Mid-range Android (no flagship budget), rugged case |
| **Language** | Traditional Chinese (zh-Hant) |
| **Primary use** | Field bearing checks, property boundary orientation, antenna alignment for survey markers |
| **Tools today** | Suunto A-10 optical compass; phone compass for rough cross-check |
| **Pain points** | Phone compass drift when near survey vehicles or steel fencing; no way to log multiple bearings with timestamps |
| **Accuracy expectation** | **±2° for field cross-check purposes.** Would trust ±1° app reading for minor surveys where full surveyor setup is impractical. |
| **Critical requirement** | Bearing log with timestamp and location; clear interference warnings near vehicles. |

---

### Persona 3: 山田タカシ (Yamada Takashi) — Technical Outdoor Enthusiast

| Attribute | Detail |
|-----------|--------|
| **Age** | 34 |
| **Role** | Weekend hiker, amateur astronomer, ham radio operator (JA call sign) |
| **Location** | Japan (Osaka) |
| **Device** | Google Pixel mid-range |
| **Language** | Japanese (ja) |
| **Primary use** | Trail navigation; antenna azimuth setting for Yagi at home; polar alignment for telescope |
| **Pain points** | Stock compass drifts when phone is cold; hard to read fine bearing for antenna work; no way to average a bearing over time |
| **Accuracy expectation** | **±2° for hiking; ±1° for antenna work.** Acceptable to show a 5-second averaged heading for antenna use. |
| **Critical requirement** | Sighting mode for line-of-sight bearing; works reliably in cold weather (outdoor use at 5°C). |

---

### Persona 4: 王小姐 (Ms. Wang) — General Consumer

| Attribute | Detail |
|-----------|--------|
| **Age** | 28 |
| **Role** | Office worker, occasional hiker, new homeowner curious about feng shui |
| **Location** | Guangzhou, China |
| **Device** | Mid-range Android (Xiaomi Redmi or similar) |
| **Language** | Simplified Chinese (zh-Hans) |
| **Primary use** | Rough compass check when hiking; checking 坐向 of apartment for basic feng shui curiosity |
| **Pain points** | Stock compass confusing; no idea if it's accurate; luopan terminology opaque |
| **Accuracy expectation** | **±5° is acceptable.** Will appreciate accuracy feedback but not use it for professional decisions. |
| **Critical requirement** | Simple, approachable UI; onboarding that explains calibration without overwhelming. |

---

## 5. User Stories

### Core Compass Use

**US-01 — Taking a bearing on a distant object**
> As a ham radio operator (Yamada), I want to point my phone at a distant object and read its magnetic bearing, so that I can set my Yagi antenna to that azimuth.

*Given* I am outdoors with a good magnetic environment,  
*When* I enter Sighting Mode and align the crosshair with the target object,  
*Then* the app displays a real-time bearing (true or magnetic, per my setting) accurate to ±1° when confidence is High, and I can capture that bearing with a single tap.

---

**US-02 — Orienting a building's 坐向 (sitting-facing direction)**
> As a feng shui practitioner (Master Li), I want to determine the precise 坐 (sitting/back) and 向 (facing/front) directions of a building, so that I can produce a correct 坐向 assessment.

*Given* I am standing at the facing (向) wall of a building, phone held level,  
*When* I align the device with the wall and lock the reading in Luopan Mode,  
*Then* the app shows me:
  - The 向 bearing in degrees (true and magnetic north variants)
  - The corresponding 山 label in 二十四山 (e.g., "午山子向")
  - The corresponding 分金 label if confidence is High
  - A confidence indicator (High / Moderate / Poor)

---

**US-03 — Calibrating in the field**
> As a surveyor (Engineer Chen), I want to calibrate the compass on-site before taking critical measurements, so that I can correct for the vehicle's hard-iron distortion that may have affected the device.

*Given* I have driven to the survey site in my vehicle and then stepped away,  
*When* I launch the calibration flow from the Luopan Mode menu,  
*Then* the app guides me through a figure-8 motion with real-time visual feedback, completes calibration when sufficient data is collected, and shows a calibration quality score before saving. The previous calibration is preserved until I confirm acceptance of the new one.

---

**US-04 — Detecting magnetic interference**
> As a feng shui practitioner (Master Li), I want the app to warn me when magnetic interference makes the heading unreliable, so that I do not make a wrong 坐向 call based on a corrupted reading.

*Given* I am inside a steel-framed building or near an HVAC unit,  
*When* the measured field magnitude deviates by more than 25% from the WMM-expected local field,  
*Then* the app:
  - Displays a prominent interference warning overlay
  - Changes the confidence indicator to "Poor"
  - Refuses to allow bearing capture until I acknowledge the warning or move to a clean environment
  - Does NOT change the heading display to zero — it continues to show the last reading with the warning badge

---

**US-05 — Recording a bearing for later reference**
> As a feng shui practitioner (Master Li), I want to save a named bearing with its metadata, so that I can reference it in my report without relying on a photo.

*Given* I have a stable, High-confidence heading in any display mode,  
*When* I tap the capture button and enter a name,  
*Then* the app saves: bearing value (degrees), north type (true/magnetic), confidence level at capture, device location (lat/lon, if GPS available and consented), timestamp, calibration snapshot ID, and optional free-text notes. The record is stored locally only.

---

**US-06 — Reviewing saved bearings**
> As a surveyor (Engineer Chen), I want to review and export the bearings I recorded at a site, so that I can reference them in my report.

*Given* I have saved multiple bearings during a field session,  
*When* I open the Bearing History screen,  
*Then* I see a list sorted by timestamp showing name, bearing, confidence, and location tag. I can tap each record to view full details and notes.

---

**US-07 — Reading 24 Mountains and 分金 in Luopan Mode**
> As a feng shui practitioner (Master Li), I want to read the current bearing directly from the luopan dial, so that I can identify the 山 without mental arithmetic.

*Given* Luopan Mode is active and confidence is High or Moderate,  
*When* I align the device with a wall or direction,  
*Then* the dial rotates so the fixed red pointer indicates the current bearing on the 二十四山 ring, with the 山 label visually centered under the pointer and displayed as text above the dial. When confidence is High, the 六十分金 ring is also readable.

---

**US-08 — General consumer quick bearing check**
> As a general consumer (Ms. Wang), I want to quickly see which direction I am facing, so that I can orient myself in an unfamiliar area.

*Given* I open the app for the first time and have not calibrated,  
*When* Modern Mode is displayed,  
*Then* the app shows a compass rose and digital bearing, with a first-launch prompt explaining that calibration will improve accuracy. The app remains usable with a "Needs Calibration" badge — it does not block access.

---

**US-09 — Checking magnetic declination at my location**
> As a surveyor (Engineer Chen), I want to know the magnetic declination at my current location, so that I can understand the offset between magnetic and true north for my survey notes.

*Given* GPS is available or a recent location is cached,  
*When* I open the declination info panel,  
*Then* the app shows: magnetic declination in decimal degrees and DDMM format, source (WMM2025 bundled or Android fallback), WMM epoch date, and the coordinates used for the calculation.

---

**US-10 — Operating without GPS**
> As any user in a GPS-denied environment (underground, indoors, abroad with data roaming off), I want the compass to still provide a declination estimate, so that true-north mode remains usable.

*Given* GPS is unavailable and no recent fix is cached,  
*When* I activate True North mode,  
*Then* the app uses the last known GPS position (if available and <30 days old) for declination, or prompts me to manually enter coordinates or accept magnetic-north-only mode. The data source is clearly labeled.

---

**US-11 — Holding the device vertically**
> As a hiker (Yamada), I want to use the compass while holding my phone upright (portrait, screen facing me), so that I can take a sight bearing without bending over.

*Given* I hold the phone vertically (tilt > 45° from horizontal),  
*When* the heading is displayed,  
*Then* the app warns that accuracy is reduced due to device orientation, shows the current tilt angle, and recommends holding flat for best results. The heading continues to update but is capped at "Moderate" confidence regardless of other conditions.

---

**US-12 — Reading 後天八卦 方位 and 十二地支 direction from the luopan dial**
> As a feng shui practitioner (Master Li), I want to read the 後天八卦 directional positions (南/北/東/西/東南/西北/東北/西南) and the 十二地支 compass hour (子/丑/寅…) directly from the dial rings, so that I can perform 方位 analysis and 坐向 confirmation without mental translation.

*Given* Luopan Mode is active and the dial is rotating with the heading,  
*When* I align the device with a wall or direction,  
*Then*:
- The 後天八卦 ring shows both the trigram symbol AND the Chinese cardinal/intercardinal direction name (e.g., "☲ 離 南") at each of the 8 positions under the pointer
- The 十二地支 ring shows the Earthly Branch character (e.g., "午") for the current 30° sector
- The numerical readout panel displays the current 十二地支 character and the 後天八卦 方位 label alongside the bearing in degrees

---

## 6. Functional Requirements

---

### 6.1 Sensor Acquisition and Fusion — REQ-SENSOR

#### REQ-SENSOR-01: Magnetometer Data Source

**Priority:** P0 | **Phase:** 1

**Description:** The app MUST subscribe to `TYPE_MAGNETIC_FIELD_UNCALIBRATED` as its primary magnetometer data source and implement its own hard-iron / soft-iron correction, rather than relying on the OS-calibrated `TYPE_MAGNETIC_FIELD` sensor. The OS-calibrated sensor may apply opaque corrections that interfere with the app's own ellipsoid fitting.

**Constraint:** The OS `TYPE_MAGNETIC_FIELD` sensor MAY be used as a fallback only on devices that do not provide `TYPE_MAGNETIC_FIELD_UNCALIBRATED`.

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| App | Device supports `TYPE_MAGNETIC_FIELD_UNCALIBRATED` | App starts | App subscribes to uncalibrated magnetometer at `SENSOR_DELAY_GAME` (≈50 Hz) |
| App | Device does NOT support `TYPE_MAGNETIC_FIELD_UNCALIBRATED` | App starts | App falls back to `TYPE_MAGNETIC_FIELD`, logs the fallback, and displays a "Limited calibration" badge |

**Source user stories:** US-02, US-03

---

#### REQ-SENSOR-02: Accelerometer and Gravity Data

**Priority:** P0 | **Phase:** 1

**Description:** The app MUST subscribe to `TYPE_GRAVITY` (preferred) or `TYPE_ACCELEROMETER` (fallback) at `SENSOR_DELAY_GAME` (≈100 Hz) to determine device tilt and compute the horizontal magnetic field component.

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| App | `TYPE_GRAVITY` is available | App starts | App subscribes to `TYPE_GRAVITY` at `SENSOR_DELAY_GAME` |
| App | `TYPE_GRAVITY` not available | App starts | App uses `TYPE_ACCELEROMETER` and applies a low-pass filter with α = 0.1 to isolate gravity |

**Source user stories:** US-02, US-11

---

#### REQ-SENSOR-03: Gyroscope Integration

**Priority:** P0 | **Phase:** 1

**Description:** The app MUST subscribe to `TYPE_GYROSCOPE` at `SENSOR_DELAY_GAME` (≈100 Hz) to enable sensor fusion that produces stable headings between magnetometer update intervals and during brief magnetic transients.

**Constraint:** If `TYPE_GYROSCOPE` is unavailable, the app MUST degrade to magnetometer-only mode and display a "Gyroscope unavailable — heading may be less stable" advisory.

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| App | `TYPE_GYROSCOPE` available | Heading displayed | Heading remains stable (jitter <0.5°) during slow, steady rotation |
| App | `TYPE_GYROSCOPE` unavailable | Heading displayed | App shows advisory and the confidence level cannot exceed "Moderate" |

**Source user stories:** US-01, US-02

---

#### REQ-SENSOR-04: Sensor Fusion Algorithm

**Priority:** P0 | **Phase:** 1

**Description:** The app MUST implement a 9-DOF orientation filter fusing magnetometer, accelerometer, and gyroscope data. The fusion filter MUST be configurable by the engineering team, but its external behavior — latency, stability, and compass accuracy — is bounded by the NFR targets in Section 7.

**Constraint:** The choice of fusion algorithm (Madgwick, Mahony, EKF, or a custom implementation) is an engineering decision. The algorithm must produce heading updates at ≥20 Hz and the latency from sensor event to display update must be <50 ms. This affects product behavior and is therefore stated as a constraint, not a design prescription.

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| App | Good-condition environment (§8.1) | Stationary, calibrated, phone level | Heading displayed updates at ≥20 Hz with jitter ≤0.3° RMS over a 10-second window |
| App | Phone rotated 360° at ~30°/s | Continuous rotation | Heading tracks rotation without lag >50 ms and returns to reference ±1° after full rotation |

**Source user stories:** US-01, US-02, US-08

---

#### REQ-SENSOR-05: Sampling Rate and Power Management

**Priority:** P0 | **Phase:** 1

**Description:** The app MUST request sensors at `SENSOR_DELAY_GAME`. It MUST unregister all sensor listeners when the app is in the background (not visible), and re-register when returning to the foreground.

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| App | App moved to background | User presses Home | All sensor listeners unregistered within 100 ms |
| App | App returned to foreground | User opens app from recents | Sensors re-registered and heading resumes within 500 ms |

**Source user stories:** REQ-NFR-04

---

#### REQ-SENSOR-06: Sensor Noise Variance Tracking

**Priority:** P1 | **Phase:** 1

**Description:** The app MUST continuously compute a rolling RMS noise variance for the magnetometer signal over a 2-second window. This metric feeds into the confidence scoring system (§8.3).

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| App | Magnetometer readings over 2 s | Ongoing operation | App maintains a rolling RMS variance; when variance exceeds 0.5 µT², confidence cannot be "High" |

**Source user stories:** US-04, US-07

---

#### REQ-SENSOR-07: Per-Device Sensor Capability Logging

**Priority:** P2 | **Phase:** 2

**Description:** On first launch, the app SHOULD log the device model, Android version, available sensor types, and reported sensor resolution and range to a local diagnostic file. This data is never transmitted.

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| App | First launch | App starts | A `sensor_profile.json` file is written to internal app storage with sensor capabilities |

**Source user stories:** US-03 (indirect — helps support calibration on known-bad devices)

---

### 6.2 Hard-Iron and Soft-Iron Calibration — REQ-CAL

#### REQ-CAL-01: Calibration Data Collection

**Priority:** P0 | **Phase:** 1

**Description:** The app MUST implement a guided figure-8 calibration flow that collects raw uncalibrated magnetometer readings across a full 3D range of device orientations. The minimum acceptance threshold is 200 samples with adequate 3D distribution (no axis less than 15% of total variance range).

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| User | Calibration flow is active | User performs figure-8 motion | App collects magnetometer samples and provides real-time visual feedback on 3D coverage |
| App | 200+ samples collected with adequate coverage | Coverage threshold met | App computes ellipsoid-fit calibration parameters and shows quality score |
| App | 200+ samples but coverage is poor | 10-minute timeout OR user taps Done | App shows "Coverage incomplete" warning but allows user to accept or retry |

**Source user stories:** US-03

---

#### REQ-CAL-02: Ellipsoid Fitting

**Priority:** P0 | **Phase:** 1

**Description:** The app MUST fit the collected magnetometer samples to a 3D ellipsoid to extract:
- **Hard-iron offset vector** (3-element vector, µT): the center of the ellipsoid
- **Soft-iron correction matrix** (3×3 matrix): maps the ellipsoid to a sphere of radius equal to the expected local field magnitude

The expected local field magnitude is computed from WMM2025 at the device's location (or last known location if GPS unavailable).

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| App | Sufficient samples collected | Calibration finalized | App outputs hard-iron vector and soft-iron matrix with residual RMS error |
| App | Residual RMS after fit > 2.0 µT | Calibration finalized | App flags calibration as "Fair" quality (not "Good") and recommends retry |
| App | Residual RMS after fit > 5.0 µT | Calibration finalized | App flags calibration as "Poor" quality and strongly recommends retry |

**Source user stories:** US-03

---

#### REQ-CAL-03: Calibration Quality Scoring

**Priority:** P0 | **Phase:** 1

**Description:** The app MUST assign a calibration quality score on a three-level scale based on fit residual and 3D coverage:

| Score | Residual RMS | 3D Coverage | Effect on Confidence |
|-------|-------------|-------------|----------------------|
| **Good** | ≤ 1.0 µT | All axes ≥ 20% of range | Allows "High" confidence |
| **Fair** | 1.0–2.0 µT | All axes ≥ 15% of range | Caps confidence at "Moderate" |
| **Poor** | > 2.0 µT or any axis < 15% | — | Caps confidence at "Poor" |

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| App | Good calibration active | Heading displayed | Confidence may reach "High" if other conditions also met |
| App | Fair calibration active | Heading displayed | Confidence is capped at "Moderate" regardless of field conditions |
| App | Poor calibration active | Heading displayed | Confidence is "Poor"; interference warning is shown |

**Source user stories:** US-02, US-04, US-07

---

#### REQ-CAL-04: Calibration Persistence and Versioning

**Priority:** P0 | **Phase:** 1

**Description:** Calibration results MUST be persisted to encrypted local storage keyed by device model and Android sensor fingerprint. Each saved calibration MUST include:
- Calibration timestamp (UTC)
- Hard-iron vector and soft-iron matrix
- Quality score
- WMM-expected field magnitude used as reference
- Location at calibration time (lat/lon, if GPS available)
- Calibration version integer (monotonically increasing)

When the app loads, it MUST restore the most recent calibration for the current device.

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| App | Valid calibration exists | App cold-starts | Calibration loads within 200 ms; heading applies correction immediately |
| App | No calibration exists | App cold-starts | App displays "Uncalibrated" badge and prompts calibration on first use |
| App | New calibration completed | User confirms acceptance | New calibration saved; version number incremented; previous calibration retained as `v(n-1)` |

**Source user stories:** US-03

---

#### REQ-CAL-05: Recalibration Prompts

**Priority:** P1 | **Phase:** 4

**Description:** The app MUST prompt the user to recalibrate in the following conditions:
- Calibration age > 30 days
- The app detects systematic magnetic drift: measured field magnitude at a stable, low-interference location differs from calibration-time expected magnitude by >10% for >60 consecutive seconds
- User explicitly requests recalibration

Prompts MUST be dismissible. The app MUST NOT block use pending recalibration but MUST downgrade confidence when calibration age > 30 days.

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| App | Calibration is 31 days old | App opens | App shows a non-blocking banner: "Your calibration is 31 days old — consider recalibrating" |
| App | Field magnitude drift detected | Heading displayed for >60 s | App shows: "Magnetic environment may have changed — recalibrate for best accuracy" |

**Source user stories:** US-03, US-04

---

#### REQ-CAL-06: Calibration Bypass for Advanced Users

**Priority:** P2 | **Phase:** 2

**Description:** Advanced users SHOULD be able to manually input hard-iron offset values (e.g., from a desktop calibration tool) via a settings screen. This overrides the ellipsoid-fit values. The calibration quality is set to "Fair" when manual values are used.

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| Advanced user | Manual offset entered | Settings saved | Hard-iron correction applied; quality forced to "Fair" |

**Source user stories:** US-09 (indirect)

---

### 6.3 Magnetic Interference Detection — REQ-DETECT

#### REQ-DETECT-01: Field Magnitude Check

**Priority:** P0 | **Phase:** 1

**Description:** The app MUST continuously compare the measured calibrated field magnitude against the WMM-expected magnitude at the device's location. The expected magnitude uses the WMM2025 model (§6.4).

**Deviation thresholds:**

| Deviation | Condition Level | Action |
|-----------|----------------|--------|
| < 15% | Good | No warning |
| 15%–25% | Moderate | Yellow advisory indicator |
| > 25% | Poor | Red interference warning; confidence → "Poor" |

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| App | Phone near speaker (hard-iron source) AND calibration not yet applied | Heading displayed | Deviation computed from uncalibrated field; warning shown if >25% |
| App | Phone well-calibrated but moved near electrical panel | Measured field 40% above expected | Red interference warning shown within 2 seconds; confidence set to "Poor" |
| App | User walks away from interference source | Field deviation drops to <15% for >3 s | Warning clears; confidence re-evaluated per full confidence model |

**Source user stories:** US-04, US-07

---

#### REQ-DETECT-02: Magnetic Inclination Check

**Priority:** P0 | **Phase:** 1

**Description:** The app MUST compare the measured inclination angle (dip angle) of the magnetic field against the WMM-expected inclination at the device's location.

**Deviation thresholds:**

| Inclination Deviation | Action |
|----------------------|--------|
| < 3° | No warning |
| 3°–8° | Yellow advisory |
| > 8° | Red interference warning; confidence → "Poor" |

**Rationale:** Hard-iron and soft-iron distortion from localized sources (reinforced concrete, HVAC ducts) rotates the field vector, which changes both azimuth and inclination. An inclination check catches interference that field magnitude alone may miss if the source adds and removes field in perpendicular axes.

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| App | Large external magnet distorts field direction without changing total magnitude | Heading displayed | Inclination deviation detected; interference warning shown |

**Source user stories:** US-04

---

#### REQ-DETECT-03: Interference Warning UX

**Priority:** P0 | **Phase:** 1

**Description:** The interference warning MUST:
- Be a visually prominent overlay (red border or red banner) that does not obscure the heading value
- Include a human-readable explanation of what interference means and what to do ("Move away from metal structures, appliances, or vehicles")
- Show the deviation values (field magnitude: X µT measured vs. Y µT expected; inclination: A° measured vs. B° expected)
- NOT zero or freeze the heading display — the last-computed heading continues to update with the warning badge
- Be dismissible by the user, but the confidence remains "Poor" until field conditions improve

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| User | Interference detected | Warning displayed | User can read the heading value, read the warning message, and understand the cause |
| User | User dismisses warning | Field still disturbed | Warning badge remains on heading display; confidence remains "Poor" |
| User | User moves away from interference | Field returns to normal | Warning automatically clears; no action required from user |

**Source user stories:** US-04

---

#### REQ-DETECT-04: Sensor Noise Spike Detection

**Priority:** P1 | **Phase:** 1

**Description:** The app MUST detect magnetometer noise spikes (single-sample outliers where |sample − rolling mean| > 10 µT) and reject them from the heading computation without displaying a spike in the needle position. Rejected samples MUST be logged to the local diagnostic buffer.

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| App | Single-sample outlier detected | Fusion step | Outlier excluded from fusion; heading does not jump by spike magnitude |

**Source user stories:** US-02 (stability), US-07

---

#### REQ-DETECT-05: Interference History in Bearing Record

**Priority:** P1 | **Phase:** 1

**Description:** When a bearing is captured, the app MUST record the interference status at the moment of capture (field magnitude deviation, inclination deviation, confidence level). If confidence was "Poor" at capture, the saved record MUST be marked with a visible "Captured under interference" flag.

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| User | Bearing captured with Poor confidence | Bearing saved | Record shows "⚠ Captured under interference" flag in Bearing History |

**Source user stories:** US-05

---

### 6.4 True North Correction — REQ-NORTH

#### REQ-NORTH-01: Bundled WMM2025 Coefficients

**Priority:** P0 | **Phase:** 2

**Description:** The app MUST bundle the WMM2025 spherical-harmonic coefficients (168 Gauss coefficients for the main field + secular variation model, valid 2025.0–2030.0) in the app binary. All declination, inclination, and field-strength calculations for offline use MUST use these bundled coefficients.

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| App | No network connectivity, GPS available | App computes declination | WMM2025 bundled model used; correct declination computed within ±0.1° of NOAA reference |
| App | WMM2025 validity expires (after 2030.0) | App opened | App warns user that bundled model is out of date and falls back to Android `GeomagneticField` |

**Source user stories:** US-09, US-10

---

#### REQ-NORTH-02: Android GeomagneticField Fallback

**Priority:** P0 | **Phase:** 2

**Description:** If the bundled WMM2025 model is past its valid epoch OR if computation fails, the app MUST fall back to Android's `GeomagneticField` API. The fallback MUST be visible to the user via a label change: "Declination: −13.2° (WMM2025)" becomes "Declination: −13.2° (Android model — may be less accurate)."

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| App | Bundled model expired | Declination computed | Android `GeomagneticField` used; label shows "(Android model)" |

**Source user stories:** US-09, US-10

---

#### REQ-NORTH-03: GPS Location Handling for Declination

**Priority:** P0 | **Phase:** 2

**Description:** The app MUST request `ACCESS_FINE_LOCATION` permission for declination computation. If permission is granted but GPS is unavailable, the app MUST use the most recent cached location (maximum age: 30 days). The cached location is stored in local app storage and is never transmitted.

If no location is available at all (permission denied, no cache), the app MUST:
1. Allow the user to manually enter coordinates for declination computation
2. Alternatively, display headings in magnetic-north mode only, clearly labeled

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| App | GPS unavailable; cached position age < 30 days | True North mode active | Declination computed from cached position; UI shows "Using last known location (X days ago)" |
| App | No location available at all | User enables True North | App shows dialog: "Enter coordinates for True North or use Magnetic North only" |
| User | User manually enters 25.05°N, 121.53°E | Coordinates submitted | Declination computed from WMM2025 at those coordinates |

**Source user stories:** US-10

---

#### REQ-NORTH-04: Graceful Degradation Display

**Priority:** P0 | **Phase:** 2

**Description:** The app MUST label all heading values with their north reference: "True N", "Magnetic N", or "Grid N". The label must change immediately when the user switches north type. No heading value may be displayed without its north reference label.

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| User | True North mode active | Heading displayed | "True N" label visible adjacent to heading value |
| User | Switches to Magnetic North | Toggle activated | Heading updates within 200 ms; label changes to "Magnetic N" |

**Source user stories:** US-09, US-02

---

### 6.5 Compass Display Modes — REQ-DISPLAY

#### REQ-DISPLAY-01: Mode Switching

**Priority:** P0 | **Phase:** 1

**Description:** The app MUST provide three display modes accessible via a mode switcher (tab bar or swipe gesture): **Modern Mode**, **Luopan Mode**, and **Sighting Mode**. The last-used mode MUST be persisted across app restarts.

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| User | Any mode active | User taps mode switcher | Mode transitions in <300 ms; heading continues without interruption |
| User | App restarted | Cold start | App opens in the mode used during the previous session |

**Source user stories:** US-01, US-02, US-08

---

#### REQ-DISPLAY-02: Modern Mode — Compass Rose

**Priority:** P0 | **Phase:** 1

**Description:** Modern Mode MUST display:
- A 360° compass rose with labeled cardinal (N, E, S, W) and intercardinal (NE, SE, SW, NW) points
- A digital heading readout to 0.1° precision (e.g., "247.3°")
- The current north reference label (True N / Magnetic N / Grid N)
- The confidence indicator (High / Moderate / Poor) with color coding: green / amber / red
- The calibration age in days (e.g., "Cal: 3d ago")
- A tilt indicator: shows "Hold flat for best accuracy" when tilt > 20°

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| User | Modern Mode active, phone level | Heading displayed | All six elements above are visible without scrolling on a 5-inch or larger screen |
| User | Phone tilted 30° from horizontal | Heading displayed | Tilt indicator appears; confidence capped at "Moderate" |

**Source user stories:** US-08, US-11

---

#### REQ-DISPLAY-03: Modern Mode — Confidence Indicator

**Priority:** P0 | **Phase:** 1

**Description:** The confidence indicator in Modern Mode MUST be a named badge (not just a color) that is accessible to color-blind users. The badge text must be translated to all supported locales.

| Badge | Color | Meaning |
|-------|-------|---------|
| High | Green | All conditions met (§8.1); heading accurate to ±1° |
| Moderate | Amber | One or more moderate conditions; heading accurate to ±3° |
| Poor | Red | One or more poor conditions; heading should not be trusted for precise work |

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| User (color-blind) | Poor confidence | Modern Mode | Badge reads "Poor" (or locale equivalent) in text, not only red |

**Source user stories:** US-04, US-08

---

#### REQ-DISPLAY-04: Luopan Mode — Overview

**Priority:** P0 | **Phase:** 1

**Description:** Luopan Mode MUST display a rotationally symmetric luopan graphic in which the **needle/pointer is fixed** and the **dial rotates** to follow the magnetic heading — matching physical luopan operation. The graphic MUST include the rings specified in §10.

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| User | Luopan Mode active | Device rotated clockwise 45° | Dial rotates counter-clockwise 45°; pointer remains fixed; the correct 山 moves under the pointer |

**Source user stories:** US-02, US-07

---

#### REQ-DISPLAY-05: Luopan Mode — Numerical Readout

**Priority:** P0 | **Phase:** 1

**Description:** Luopan Mode MUST show, alongside the dial graphic:
- Current bearing in degrees (0.0°–360.0°), labeled with north reference
- The 山 (Mountain) currently indicated by the pointer: Chinese character + romanization
- The 十二地支 sector currently under the pointer: Earthly Branch character + romanization (e.g., "午 Wǔ")
- The 後天八卦 方位 direction currently under the pointer: trigram + direction name (e.g., "☲ 離 · 南")
- The 分金 division (if confidence is High)
- The confidence indicator (same visual as Modern Mode)

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| User | Pointer at 180° (午) | Luopan Mode, High confidence | Display shows "午 (Wǔ) · ☲ 離 南 · 180.0° · True N · 丙子分金 · High" (or locale equivalent) |
| User | Pointer at 90° (卯) | Luopan Mode | 十二地支 shows "卯 Mǎo"; 後天八卦 方位 shows "☳ 震 東" |
| User | Confidence is Moderate | Luopan Mode | 分金 readout is hidden or marked "N/A — calibrate for 分金 precision"; 時辰 and 方位 labels remain visible |

**Source user stories:** US-02, US-07, US-12

---

#### REQ-DISPLAY-06: Luopan Mode — 坐向 Lock

**Priority:** P1 | **Phase:** 1

**Description:** Luopan Mode MUST provide a "Lock 向" button. When tapped:
- The current bearing is locked as the **向 (facing)** direction
- The **坐 (sitting)** direction is computed as 向 + 180°
- Both values are displayed with their 山 labels
- The locked reading is visually distinguished (e.g., a lock icon and a static secondary indicator on the dial)
- Locking is only permitted when confidence is "High" or "Moderate"; the button is disabled when confidence is "Poor"

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| User | Confidence is High | Taps "Lock 向" | Current bearing locked; 坐 computed and displayed; dial continues to rotate showing live reading alongside the locked pair |
| User | Confidence is Poor | Taps "Lock 向" | Button is disabled; tooltip: "Cannot lock — heading is unreliable" |

**Source user stories:** US-02

---

#### REQ-DISPLAY-07: Sighting Mode — Camera Overlay

**Priority:** P1 | **Phase:** 1

**Description:** Sighting Mode MUST display a live camera viewfinder (rear camera) with a centered crosshair overlay. The bearing of the crosshair direction MUST be computed from the current heading and displayed in real time at the top of the screen. A capture button saves the bearing when tapped.

**Rationale:** Camera passthrough allows the user to align with a distant object precisely without holding the phone at a constrained angle.

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| User | Sighting Mode active | Phone aimed at distant landmark | Camera preview shows; bearing of center crosshair updates continuously |
| User | CAMERA permission denied | Sighting Mode selected | Mode shows an explanatory message and a button to open system settings; falls back to Modern Mode |

**Source user stories:** US-01, US-11

---

#### REQ-DISPLAY-08: Sighting Mode — Bearing Capture from Crosshair

**Priority:** P1 | **Phase:** 1

**Description:** The capture button in Sighting Mode MUST save the crosshair bearing at the moment of tap, including all bearing record fields (§6.6). The app MUST NOT use the camera image itself in the saved record (no photo storage by default in v1).

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| User | Crosshair aligned with target | Capture tapped | Bearing record saved; confirmation toast shown; camera continues running |

**Source user stories:** US-01

---

#### REQ-DISPLAY-09: Heading Smoothing Control

**Priority:** P2 | **Phase:** 2

**Description:** Advanced users SHOULD be able to adjust heading display smoothing via a settings slider (Fast ↔ Smooth). "Fast" minimizes filter lag at the cost of more visible jitter; "Smooth" maximizes stability at the cost of slightly higher lag. Default is a balanced midpoint.

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| Advanced user | Smoothing set to "Fast" | Heading displayed | Heading updates show <30 ms additional lag; jitter within 1° RMS |
| Advanced user | Smoothing set to "Smooth" | Heading displayed | Heading jitter ≤0.2° RMS; additional lag ≤100 ms |

**Source user stories:** US-09 (indirect)

---

#### REQ-DISPLAY-10: Screen-On Lock

**Priority:** P0 (elevated) | **Phase:** 1

**Description:** When any compass display mode is active, the app MUST acquire a screen-on wake lock (partial wake lock) to prevent the screen from dimming during use. The wake lock MUST be released when the app is backgrounded.

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| App | Display mode active | 2 minutes of inactivity | Screen remains on; no dimming interrupts a bearing reading |

**Source user stories:** US-02, US-09

---

#### REQ-DISPLAY-11: Accessibility — Text Scaling

**Priority:** P1 | **Phase:** 1

**Description:** All text in Modern Mode and the bearing readout in Luopan Mode MUST respect the system font size setting. The luopan ring labels MUST be legible at default system font size on a 5-inch screen (minimum 8sp equivalent for ring labels; 16sp for the primary bearing readout).

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| User | System font size "Large" | Luopan Mode displayed | Primary bearing text readable without zoom; ring characters not clipped |

**Source user stories:** US-08

---

#### REQ-DISPLAY-12: Dark / Light Theme

**Priority:** P2 | **Phase:** 2

**Description:** The app SHOULD follow the system dark/light mode. Modern Mode uses standard Material Design theming. Luopan Mode uses its own dark-wood aesthetic (§10.3) by default; users MAY toggle a "Light Luopan" theme for high-ambient-light conditions.

**Source user stories:** US-07

---

### 6.6 Bearing Capture and History — REQ-CAPTURE

#### REQ-CAPTURE-01: Bearing Record Schema

**Priority:** P0 | **Phase:** 1

**Description:** Each saved bearing record MUST contain:

| Field | Type | Required |
|-------|------|----------|
| `id` | UUID | Yes |
| `name` | String (max 100 chars) | Yes |
| `bearing_deg` | Float (0.000–360.000) | Yes |
| `north_type` | Enum: TRUE, MAGNETIC, GRID | Yes |
| `confidence` | Enum: HIGH, MODERATE, POOR | Yes |
| `captured_at` | ISO 8601 UTC timestamp | Yes |
| `calibration_version` | Integer | Yes |
| `field_deviation_pct` | Float | Yes |
| `inclination_deviation_deg` | Float | Yes |
| `interference_flag` | Boolean | Yes |
| `latitude` | Float | Only if GPS consented and available |
| `longitude` | Float | Only if GPS consented and available |
| `altitude_m` | Float | Only if GPS consented and available |
| `notes` | String (max 1000 chars) | No |
| `display_mode` | Enum: MODERN, LUOPAN, SIGHTING | Yes |

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| App | Bearing captured | Record saved | All required fields populated; optional fields populated only if available and consented |

**Source user stories:** US-05, US-09

---

#### REQ-CAPTURE-02: Capture Flow UX

**Priority:** P0 | **Phase:** 1

**Description:** The bearing capture flow MUST:
1. Show a name input dialog (pre-filled with location label if GPS available, otherwise "Bearing 1", "Bearing 2", etc.)
2. Show a preview of the bearing value, confidence, and north type before confirmation
3. Offer an optional notes field
4. Complete in ≤3 taps from capture button to saved record

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| User | Capture button tapped | Modern Mode | Dialog appears with name input and bearing preview; user can confirm or cancel |
| User | Confirms capture | Dialog submitted | Record saved; confirmation shown ("Bearing saved as 'Site NE Corner'"); dialog dismissed |

**Source user stories:** US-05

---

#### REQ-CAPTURE-03: Bearing History Screen

**Priority:** P1 | **Phase:** 1

**Description:** The Bearing History screen MUST:
- List saved bearings sorted by captured_at (newest first)
- Show for each record: name, bearing + north type, confidence badge, date/time
- Support tap to expand to full record details
- Support swipe-to-delete with undo toast
- Support search by name

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| User | 20 bearings saved | History opened | All 20 visible in scrollable list; each shows name, bearing, confidence, timestamp |
| User | Swipes a record left | Delete triggered | Record removed; "Undo" toast appears for 5 seconds |

**Source user stories:** US-06

---

#### REQ-CAPTURE-04: Data Persistence

**Priority:** P0 | **Phase:** 1

**Description:** Bearing records MUST be stored in an encrypted local database (SQLite with SQLCipher or Android EncryptedFile/Room). Records MUST survive app uninstall only if the user has explicitly backed up via Android Backup. Records are NEVER synced to any cloud service.

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| App | 50 bearing records saved | App force-stopped and restarted | All 50 records available in Bearing History |

**Source user stories:** US-06

---

#### REQ-CAPTURE-05: Share / Export

**Priority:** P2 | **Phase:** 2

**Description:** Users SHOULD be able to share individual bearing records as plain text (for copy-paste into reports) and export the full history as a CSV file via the Android Share sheet.

**Source user stories:** US-06, US-09

---

#### REQ-CAPTURE-06: Location Privacy Confirmation

**Priority:** P0 | **Phase:** 1

**Description:** The first time a user captures a bearing when GPS is available, the app MUST explain that location is optional and will be stored only in the bearing record, locally. A toggle "Include location in this capture" must default to ON (convenience) but be easily changed. The setting is not persisted across captures; the user must choose each time or in Settings.

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| User | First capture with GPS available | Capture dialog shown | Location toggle visible with privacy note; user can deselect before saving |

**Source user stories:** US-05

---

### 6.7 Declination Management — REQ-DECL

#### REQ-DECL-01: North Type Toggle

**Priority:** P0 | **Phase:** 1

**Description:** The app MUST provide a user-accessible toggle to switch between True North, Magnetic North, and Grid North. The toggle must be visible without entering settings (e.g., a persistent toggle in the app bar or a quick-access overlay). The selected north type applies to all display modes simultaneously.

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| User | Magnetic North active | User taps north-type toggle | Mode switches to True North; heading value changes by declination amount; label changes |
| User | Grid North selected | Heading displayed | Grid convergence applied in addition to magnetic declination; label shows "Grid N" |

**Source user stories:** US-09, US-02

---

#### REQ-DECL-02: Declination Info Panel

**Priority:** P1 | **Phase:** 1

**Description:** An info panel (accessible from the toggle or from Settings) MUST display:
- Magnetic declination in °E/°W format and decimal degrees
- Grid convergence angle (if applicable to the user's grid zone)
- Data source: "WMM2025 (expires 2030)" or "Android GeomagneticField"
- Coordinates used for the calculation (masked to 2 decimal places for privacy)
- Date the calculation was last updated

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| User | WMM2025 bundled model in use | Info panel opened | Panel shows "Declination: 3.7°E · WMM2025 (valid to 2030) · Near 25.05°N, 121.53°E" |

**Source user stories:** US-09

---

#### REQ-DECL-03: Grid North Support

**Priority:** P2 | **Phase:** 2

**Description:** Grid North SHOULD apply a grid convergence angle based on the user's current UTM zone, added to (or subtracted from) the magnetic declination. The grid zone is computed from GPS coordinates.

**Source user stories:** US-09

---

### 6.8 Localization — REQ-L10N

#### REQ-L10N-01: Supported Locales

**Priority:** P0 | **Phase:** 1

**Description:** The app MUST ship with complete translations for:
- `zh-Hant` — Traditional Chinese (primary for Luopan Mode terminology)
- `zh-Hans` — Simplified Chinese
- `ja` — Japanese
- `en` — English (default fallback)

Every user-facing string, including error messages, warning labels, calibration instructions, and luopan ring term labels, MUST be translated in all four locales.

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| App | System locale is `ja` | App launched | All UI, warnings, and bearing labels appear in Japanese |
| App | System locale not in supported set | App launched | App falls back to `en` |

**Source user stories:** All

---

#### REQ-L10N-02: Luopan Terminology

**Priority:** P0 | **Phase:** 1

**Description:** Luopan ring labels MUST use Traditional Chinese characters (zh-Hant) by default in Luopan Mode, regardless of system locale. A setting MUST allow the user to display ring labels in their system locale. English transliterations (pinyin romanization) MUST be available as a toggle for all locales.

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| User (English) | System locale `en`, Luopan Mode | Default settings | Ring labels display in Traditional Chinese characters |
| User (English) | "Show ring labels in my language" enabled | Luopan Mode | Ring labels display in English equivalents where defined |
| User | "Show romanization" enabled | Luopan Mode | Pinyin romanization shown below each Chinese character label |

**Source user stories:** US-07, US-08

---

#### REQ-L10N-03: Right-to-Left (RTL) Exclusion

**Priority:** P0 | **Phase:** 1

**Description:** v1 does not support RTL layouts. The supported locales (zh-Hant, zh-Hans, ja, en) are all LTR. This is explicitly out of scope.

---

### 6.9 Luopan Ring Content — REQ-LUOPAN

#### REQ-LUOPAN-01: 後天八卦 Ring — Trigram and Direction Labels

**Priority:** P0 | **Phase:** 1

**Description:** The 後天八卦 ring (Ring 3) MUST display, within each of its 8 × 45° sectors:
1. The trigram Unicode symbol (☲ ☵ ☳ ☱ ☴ ☰ ☶ ☷)
2. The trigram Chinese name (離/坎/震/兌/巽/乾/艮/坤)
3. The Chinese cardinal or intercardinal direction name (南/北/東/西/東南/西北/東北/西南) associated with the King Wen arrangement

The direction name provides the 方位 compass reference that practitioners use for 坐向 analysis. The trigram symbol alone is insufficient because direction and trigram are the joint product of this ring.

The 後天八卦 directional assignment (King Wen arrangement) is:

| Trigram | 卦名 | 方位 (Direction) | Center Bearing | Sector Range |
|---------|------|-----------------|---------------|--------------|
| ☲ | 離 | 南 | 180° | 157.5°–202.5° |
| ☴ | 巽 | 東南 | 135° | 112.5°–157.5° |
| ☳ | 震 | 東 | 90° | 67.5°–112.5° |
| ☶ | 艮 | 東北 | 45° | 22.5°–67.5° |
| ☵ | 坎 | 北 | 0° | 337.5°–22.5° |
| ☰ | 乾 | 西北 | 315° | 292.5°–337.5° |
| ☱ | 兌 | 西 | 270° | 247.5°–292.5° |
| ☷ | 坤 | 西南 | 225° | 202.5°–247.5° |

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| User | Luopan Mode active; device pointing at 180° | Dial displayed | 後天八卦 ring shows "☲ 離 南" in the sector currently under the pointer |
| User | Device rotated to 90° | Dial displayed | 後天八卦 ring shows "☳ 震 東" under the pointer |
| User | System locale is `en` | Default luopan settings | Ring labels still display in Traditional Chinese characters (per REQ-L10N-02) |
| App | "Show romanization" toggle enabled | Luopan Mode | 方位 labels show pinyin below Chinese: "Nán" under 南, "Dōng" under 東, etc. |

**Dependencies:** REQ-DISPLAY-04, REQ-L10N-02

**Source user stories:** US-02, US-07, US-12

---

#### REQ-LUOPAN-02: 十二地支 Ring — Twelve Earthly Branch Directions

**Priority:** P0 | **Phase:** 1

**Description:** The Luopan Mode MUST include a **十二地支** ring (Ring 4 in the concentric ordering, positioned between the 後天八卦 ring and the 二十四山 ring) showing the 12 Earthly Branch characters at 30° intervals. Each sector spans 30° of compass arc.

This ring represents the **十二時辰方位** — the twelve directional compass sectors named after the Earthly Branches. It is a distinct ring from the 二十四山 ring (where Earthly Branches appear as individual 15° slots alternating with Heavenly Stems and Trigrams). The 十二地支 ring gives a broader 30° directional grouping, which practitioners read to identify the general 方位 sector before refining with the 二十四山 ring.

The 12 directional sectors are:

| # | 地支 | Pinyin | Direction | Center Bearing | Sector Range |
|---|------|--------|-----------|---------------|--------------|
| 1 | 子 | Zǐ | 正北 | 0° | 345°–15° |
| 2 | 丑 | Chǒu | 北北東 | 30° | 15°–45° |
| 3 | 寅 | Yín | 東北偏東 | 60° | 45°–75° |
| 4 | 卯 | Mǎo | 正東 | 90° | 75°–105° |
| 5 | 辰 | Chén | 東南偏東 | 120° | 105°–135° |
| 6 | 巳 | Sì | 南南東 | 150° | 135°–165° |
| 7 | 午 | Wǔ | 正南 | 180° | 165°–195° |
| 8 | 未 | Wèi | 南南西 | 210° | 195°–225° |
| 9 | 申 | Shēn | 西南偏西 | 240° | 225°–255° |
| 10 | 酉 | Yǒu | 正西 | 270° | 255°–285° |
| 11 | 戌 | Xū | 西北偏西 | 300° | 285°–315° |
| 12 | 亥 | Hài | 北北西 | 330° | 315°–345° |

**Relationship to 二十四山:** The 12 Earthly Branches also appear in the 二十四山 ring as the middle slot of each 45° group (e.g., 壬–子–癸 at north). The 十二地支 ring and the 二十四山 ring are complementary: the former gives the broad 30° sector, the latter gives the precise 15° bearing.

**Visual note:** Each sector label is the Earthly Branch character only (子/丑/寅/卯/辰/巳/午/未/申/酉/戌/亥). The direction description column above is for the reference table in this document and need not appear on-screen.

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| User | Luopan Mode active; device pointing at 0° | Dial displayed | 十二地支 ring shows "子" centered under the pointer |
| User | Device rotated to 90° | Dial displayed | Ring shows "卯" under pointer |
| User | Device rotated to 180° | Dial displayed | Ring shows "午" under pointer |
| User | Device at 22° bearing | Dial displayed | Ring shows "子" (22° is within 子 sector 345°–15°... wait — 22° falls in 丑 sector 15°–45°); ring shows "丑" |
| App | "Show romanization" enabled | Luopan Mode | Pinyin shown below each 地支 character (e.g., "子 Zǐ") |
| User | Ring visibility toggle: 十二地支 hidden | Luopan Mode | Ring disappears; 後天八卦 and 二十四山 rings remain; heading computation unaffected |

**Note on 22° acceptance criterion:** 22° falls in the 丑 sector (15°–45°), not 子. This is correct per the sector table above.

**Dependencies:** REQ-DISPLAY-04, REQ-LUOPAN-01

**Source user stories:** US-02, US-07, US-12

---

## 7. Non-Functional Requirements — REQ-NFR

#### REQ-NFR-01: Heading Latency

**Priority:** P0 | **Phase:** 1

**Description:** The time from a physical rotation event (measurable via gyroscope) to the corresponding change in displayed heading MUST be less than 50 ms, measured at 95th percentile over a 60-second continuous measurement session.

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| App | Good-condition environment | 360° rotation at 30°/s | 95th-percentile latency ≤50 ms; measured by instrumenting sensor callback and display draw timestamps |

---

#### REQ-NFR-02: Heading Update Rate

**Priority:** P0 | **Phase:** 1

**Description:** The displayed heading MUST update at ≥20 Hz (at least 20 frames per second of compass motion). Display update rate must not degrade below 20 Hz on the minimum target device (Android API 26, equivalent to ~2018 mid-range hardware).

---

#### REQ-NFR-03: Battery Consumption

**Priority:** P0 | **Phase:** 1

**Description:** Active compass display (all sensors running, screen on, Modern Mode) MUST consume ≤5% battery per hour on a device with a 4000 mAh battery, as measured by the Android battery historian toolchain. Background battery use (app not visible) MUST be 0% (all sensor listeners released).

---

#### REQ-NFR-04: Minimum SDK and Target

**Priority:** P0 | **Phase:** 1

**Description:**
- **Minimum SDK:** 26 (Android 8.0 Oreo)
- **Target SDK:** Latest stable (Android 15 / API 35 at time of writing)
- **Architecture support:** arm64-v8a, armeabi-v7a, x86_64

---

#### REQ-NFR-05: Offline Operation

**Priority:** P0 | **Phase:** 1

**Description:** All core features — compass display, calibration, interference detection, True North correction, bearing capture, bearing history — MUST function without any network connectivity. The app MUST NOT request network permissions for core functionality. An optional telemetry capability (§14 open question) requires explicit user opt-in.

---

#### REQ-NFR-06: Data Privacy

**Priority:** P0 | **Phase:** 1

**Description:**
- Location data (GPS coordinates) is stored only in bearing records, only when the user consents per capture (REQ-CAPTURE-06)
- No user data is transmitted over the network
- Calibration data and bearing records are stored in encrypted local storage
- No analytics SDK may be bundled that transmits location or heading data

---

#### REQ-NFR-07: Sensor Sampling Rates (Requested)

**Priority:** P0 | **Phase:** 1

**Description:** The app MUST request sensors at the following rates:

| Sensor | Requested Rate | Rationale |
|--------|---------------|-----------|
| `TYPE_MAGNETIC_FIELD_UNCALIBRATED` | `SENSOR_DELAY_GAME` (~50 Hz) | Nyquist margin for 25 Hz needle update |
| `TYPE_GRAVITY` / `TYPE_ACCELEROMETER` | `SENSOR_DELAY_GAME` (~100 Hz) | Tilt tracking for stable declination |
| `TYPE_GYROSCOPE` | `SENSOR_DELAY_GAME` (~100 Hz) | Smooth heading interpolation |
| `TYPE_PRESSURE` (barometer) | `SENSOR_DELAY_NORMAL` (~5 Hz) | Optional altitude for WMM |

Note: Android may not honor the exact requested rate on all devices; the app must handle variable delivery rates gracefully.

---

#### REQ-NFR-08: App Cold-Start Time

**Priority:** P1 | **Phase:** 2

**Description:** Time from app icon tap to first valid heading displayed (in Modern Mode) MUST be ≤3 seconds on a mid-range Android device with a warm cache. ≤5 seconds on first cold start including calibration load.

---

#### REQ-NFR-09: Crash-Free Rate

**Priority:** P0 | **Phase:** 1

**Description:** The app MUST target ≥99.5% crash-free sessions as measured by Firebase Crashlytics (or equivalent) at 30-day rolling average following public launch.

---

## 8. Accuracy Specification

This section is the contractual accuracy definition for the Luopan app. Every feature that claims to deliver accuracy references these definitions. Testing against these definitions constitutes acceptance.

### 8.1 Condition Levels

| Condition | Magnetic Field Magnitude Deviation | Inclination Deviation | Calibration Quality | Calibration Age | Sensor Noise Variance | Device Tilt |
|-----------|-----------------------------------|-----------------------|---------------------|----------------|----------------------|-------------|
| **Good** | < 15% from WMM expected | < 3° from WMM expected | Good (residual ≤1.0 µT) | ≤ 7 days | ≤ 0.1 µT² RMS over 2 s | ≤ 5° from horizontal |
| **Moderate** | 15%–25% from WMM expected | 3°–8° from WMM expected | Fair (residual ≤2.0 µT) | 7–30 days | 0.1–0.5 µT² RMS | 5°–20° from horizontal |
| **Poor** | > 25% from WMM expected | > 8° from WMM expected | Poor (residual > 2.0 µT) | > 30 days | > 0.5 µT² RMS | > 20° from horizontal |

A reading's condition is the **worst** of all individual dimension conditions. If any one dimension is "Poor", the reading is "Poor".

### 8.2 Accuracy Targets

| Condition | Heading Accuracy Target | Display Behavior |
|-----------|------------------------|-----------------|
| **Good** | ±1° (1σ) against reference compass | Confidence badge: "High" (green) |
| **Moderate** | ±3° (1σ) against reference compass | Confidence badge: "Moderate" (amber) |
| **Poor** | Undefined; MUST NOT claim precision | Confidence badge: "Poor" (red); warning overlay; bearing capture blocked unless user overrides with explicit acknowledgment |

**Note:** "1σ" means 68% of stationary readings fall within the stated range over a 30-second window at a fixed heading. The reference is a calibrated Suunto A-10, Brunton Conventional, or sun-shadow reference at a known bearing.

### 8.3 Confidence Score Computation

The confidence model combines the five condition dimensions into a single score:

```
For each dimension d in {field_deviation, inclination_deviation, cal_quality, cal_age, noise_variance}:
    score_d = GOOD (3) | MODERATE (2) | POOR (1)

overall_score = min(score_d for all d) + tilt_penalty

where tilt_penalty:
    tilt ≤ 5°:  0
    5° < tilt ≤ 20°: clamp overall to MODERATE if currently GOOD
    tilt > 20°: clamp overall to POOR

Result: 3 → "High" | 2 → "Moderate" | 1 → "Poor"
```

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| App | All five dimensions Good; tilt 2° | Stationary heading | Confidence badge shows "High" |
| App | All dimensions Good except cal_age (31 days old) | Stationary heading | Confidence capped at "Moderate" |
| App | Field deviation 30%, all others Good | Stationary heading | Confidence shows "Poor"; interference warning shown |

### 8.4 Accuracy Measurement Protocol (for QA and validation)

To validate ±1° accuracy claims during development and QA:

1. **Reference method A — Known-bearing landmark**: Identify a permanent landmark at a surveyed bearing (e.g., from a public survey marker database). Measure the landmark bearing with the app 20 times over 5 minutes. Compute mean error and 1σ.

2. **Reference method B — Sun-shadow**: At civil noon, a vertical gnomon's shadow points true north (corrected for equation of time). Use this as true-north reference; rotate device to align and compare.

3. **Reference method C — Surveyor compass**: Use a calibrated Suunto KB-14 or equivalent handheld sighting compass with declination correction as reference. Take 20 paired readings. Compute Δ = (app reading − reference reading) mean and SD.

**Pass criterion:** Mean |Δ| ≤ 1.0° AND SD(Δ) ≤ 0.5° under Good conditions, across ≥10 test devices of varying OEM and quality tier.

---

## 9. Calibration UX Requirements

### 9.1 Entry Points

The calibration flow MUST be accessible from:
1. A first-launch prompt (shown once when no calibration exists)
2. A persistent "Calibrate" button in the main menu / overflow
3. A recalibration prompt banner (non-blocking; tap to start)
4. A calibration age/quality indicator in settings

### 9.2 Pre-Calibration Check

Before starting calibration, the app MUST:
1. Check that the device is away from obvious interference (measured field magnitude within 20% of expected)
2. If interference is likely, show: "Move away from metal structures, vehicles, and appliances before calibrating"
3. If GPS is available, show the expected field magnitude at this location

### 9.3 Figure-8 Guided Motion

**Step 1 — Orientation prep:** Show an animation of a phone being held flat, then performing a slow figure-8 (infinity symbol) motion in the horizontal plane, tilting to cover all orientations. Display text: "Hold your phone flat and slowly trace a figure-8 pattern in the air. Rotate it in all directions."

**Step 2 — Real-time data visualization:** While collecting:
- A 3D sphere visualization shows where data points have been collected, color-coded from blue (no data) to green (good coverage)
- A progress indicator: "Coverage: 43% — tilt left and right more"
- A sample count: "172 of 200 minimum samples"
- A quality preview: real-time residual RMS estimate

**Step 3 — Completion gate:**
- Minimum 200 samples AND all axes ≥15% of range → "Done" button becomes active
- If user taps "Done" before gate: button is greyed; tooltip: "Keep rotating to improve coverage"
- Auto-complete: if coverage exceeds 80% on all axes AND residual RMS < 1.0 µT, completion triggers automatically with a success animation

**Step 4 — Quality report:**
- Show: Quality score (Good/Fair/Poor), residual RMS, coverage percentage per axis
- If Good: "Accept" and "Retry for better results" options
- If Fair: "Accept (Moderate accuracy)" and "Retry" options with explanation
- If Poor: "Retry recommended" as primary action; "Accept anyway" available with warning

**Step 5 — Confirmation:**
- Show: "Calibration saved. Your compass is now calibrated for this device."
- Previous calibration retained as rollback option for 7 days

### 9.4 Calibration Persistence Requirements

- Stored in Android `EncryptedSharedPreferences` or equivalent encrypted local storage
- Include calibration schema version for forward compatibility
- When app updates and calibration schema changes, old calibration is migrated or invalidated gracefully (user prompted to recalibrate)
- Maximum storage: 1 calibration record (current) + 1 rollback (previous) per device

### 9.5 Calibration Quality Display in Main UI

In all modes, a small calibration status indicator MUST be visible:
- Green dot + "Cal: 2d" = Good calibration, 2 days old
- Amber dot + "Cal: 18d" = Fair calibration or age approaching 30 days
- Red dot + "Uncalibrated" = No valid calibration

---

## 10. Luopan Mode Specification

### 10.1 Rings Included in v1

The v1 Luopan Mode displays the following concentric rings, from innermost to outermost:

| Ring | Chinese | Contents | Divisions | Degrees/Division |
|------|---------|----------|-----------|-----------------|
| 1 — Heaven's Pool | 天池 | Rotating compass needle (red north tip) | — | — |
| 2 — Earlier Heaven Bagua | 先天八卦 | 8 trigrams in Fuxi/pre-heaven arrangement | 8 × 45° | 45° |
| 3 — Later Heaven Bagua | 後天八卦 | 8 trigrams + 方位 direction names (南/北/東/西/東南/西北/東北/西南) in King Wen/post-heaven arrangement. Both trigram symbol and direction label displayed in each sector. See §10.2a for full table. | 8 × 45° | 45° |
| 4 — Twelve Earthly Branches | 十二地支 | 12 Earthly Branch characters (子丑寅卯辰巳午未申酉戌亥) marking the 十二時辰 compass directions at 30° intervals. See §10.2b for full table. | 12 × 30° | 30° |
| 5 — Twenty-Four Mountains | 二十四山 | 24 山 characters (Earthly Branches, Heavenly Stems, Trigrams) | 24 × 15° | 15° |
| 6 — Sixty Gold Divisions | 六十分金 | 60 auspicious sub-divisions of 24 Mountains | 60 × 6° | 6° |

**Note on 六十龍 (v2):** The 七十二龍 and 六十龍 rings require higher heading accuracy than the v1 hardware baseline can guarantee for general OEM devices. These rings are deferred to v2 with a device capability gate.

### 10.2a Ring Label Reference — 後天八卦 (Ring 3)

King Wen / Later Heaven arrangement. Each sector spans 45°. Both the trigram symbol and the 方位 direction label MUST be rendered in the ring.

| Trigram | 卦名 | 方位 | Pinyin | Center Bearing | Sector Range |
|---------|------|------|--------|---------------|--------------|
| ☲ | 離 | 南 | Lí · Nán | 180° | 157.5°–202.5° |
| ☴ | 巽 | 東南 | Xùn · Dōngnán | 135° | 112.5°–157.5° |
| ☳ | 震 | 東 | Zhèn · Dōng | 90° | 67.5°–112.5° |
| ☶ | 艮 | 東北 | Gèn · Dōngběi | 45° | 22.5°–67.5° |
| ☵ | 坎 | 北 | Kǎn · Běi | 0° | 337.5°–22.5° |
| ☰ | 乾 | 西北 | Qián · Xīběi | 315° | 292.5°–337.5° |
| ☱ | 兌 | 西 | Duì · Xī | 270° | 247.5°–292.5° |
| ☷ | 坤 | 西南 | Kūn · Xīnán | 225° | 202.5°–247.5° |

**Contrast with 先天八卦 (Ring 2):** In the Fuxi/Earlier Heaven arrangement the trigram-direction mapping is different — 乾 is at South, 坤 at North, 離 at East, 坎 at West. The full 先天八卦 reference:

| Trigram | 卦名 | 方位 | Center Bearing |
|---------|------|------|---------------|
| ☰ | 乾 | 南 | 180° |
| ☱ | 兌 | 東南 | 135° |
| ☲ | 離 | 東 | 90° |
| ☳ | 震 | 東北 | 45° |
| ☷ | 坤 | 北 | 0° |
| ☴ | 巽 | 西南 | 225° |
| ☵ | 坎 | 西 | 270° |
| ☶ | 艮 | 西北 | 315° |

Engineering MUST implement both arrangements as static look-up tables keyed by sector index (0–7), not by dynamic computation, to avoid floating-point boundary errors at sector edges.

---

### 10.2b Ring Label Reference — 十二地支 (Ring 4)

Each of the 12 Earthly Branches occupies a 30° sector. The label displayed on the ring is the character only; pinyin is shown when the "Show romanization" toggle is enabled.

| # | 地支 | Pinyin | Direction | Center Bearing | Sector Range |
|---|------|--------|-----------|---------------|--------------|
| 1 | 子 | Zǐ | 正北 | 0° | 345°–15° |
| 2 | 丑 | Chǒu | 北北東 | 30° | 15°–45° |
| 3 | 寅 | Yín | 東北偏東 | 60° | 45°–75° |
| 4 | 卯 | Mǎo | 正東 | 90° | 75°–105° |
| 5 | 辰 | Chén | 東南偏東 | 120° | 105°–135° |
| 6 | 巳 | Sì | 南南東 | 150° | 135°–165° |
| 7 | 午 | Wǔ | 正南 | 180° | 165°–195° |
| 8 | 未 | Wèi | 南南西 | 210° | 195°–225° |
| 9 | 申 | Shēn | 西南偏西 | 240° | 225°–255° |
| 10 | 酉 | Yǒu | 正西 | 270° | 255°–285° |
| 11 | 戌 | Xū | 西北偏西 | 300° | 285°–315° |
| 12 | 亥 | Hài | 北北西 | 330° | 315°–345° |

**Note on sector boundary at 子/亥:** The 子 sector wraps around 0°/360° (345°–15°). Engineering must handle the modular wrap-around correctly so that, for example, a bearing of 350° is assigned to 子, not to an out-of-range index.

---

### 10.2c Ring Label Reference — 二十四山 (Ring 5)

| # | 山 | Pinyin | Center Bearing | Range |
|---|---|--------|---------------|-------|
| 1 | 壬 | Rén | 345° | 337.5°–352.5° |
| 2 | 子 | Zǐ | 0° | 352.5°–7.5° |
| 3 | 癸 | Guǐ | 15° | 7.5°–22.5° |
| 4 | 丑 | Chǒu | 30° | 22.5°–37.5° |
| 5 | 艮 | Gèn | 45° | 37.5°–52.5° |
| 6 | 寅 | Yín | 60° | 52.5°–67.5° |
| 7 | 甲 | Jiǎ | 75° | 67.5°–82.5° |
| 8 | 卯 | Mǎo | 90° | 82.5°–97.5° |
| 9 | 乙 | Yǐ | 105° | 97.5°–112.5° |
| 10 | 辰 | Chén | 120° | 112.5°–127.5° |
| 11 | 巽 | Xùn | 135° | 127.5°–142.5° |
| 12 | 巳 | Sì | 150° | 142.5°–157.5° |
| 13 | 丙 | Bǐng | 165° | 157.5°–172.5° |
| 14 | 午 | Wǔ | 180° | 172.5°–187.5° |
| 15 | 丁 | Dīng | 195° | 187.5°–202.5° |
| 16 | 未 | Wèi | 210° | 202.5°–217.5° |
| 17 | 坤 | Kūn | 225° | 217.5°–232.5° |
| 18 | 申 | Shēn | 240° | 232.5°–247.5° |
| 19 | 庚 | Gēng | 255° | 247.5°–262.5° |
| 20 | 酉 | Yǒu | 270° | 262.5°–277.5° |
| 21 | 辛 | Xīn | 285° | 277.5°–292.5° |
| 22 | 戌 | Xū | 300° | 292.5°–307.5° |
| 23 | 乾 | Qián | 315° | 307.5°–322.5° |
| 24 | 亥 | Hài | 330° | 322.5°–337.5° |

### 10.3 Visual Styling

**Goal:** Authentic luopan aesthetic legible on a 5-inch to 7-inch phone screen.

| Element | Specification |
|---------|--------------|
| Background | Dark lacquer: #2C0E0E (deep burgundy-black) |
| Ring borders | Matte gold: #C9A84C |
| Ring backgrounds (alternating) | Dark red #3D1010 and deep brown #1E0A0A |
| Primary character color | Warm gold #E8C97A |
| Secondary character color (small rings) | Muted gold #B89A5A |
| Compass needle | Red tip (north) #CC2200; white south tip #F5F5F5; black shaft #1A1A1A |
| Fixed pointer indicator | Gold triangle #C9A84C centered at top of dial |
| Font | CJK-compatible serif font with broad Unicode coverage (e.g., Noto Serif CJK TC); 8sp minimum for 六十分金 ring (Ring 6), 11sp for 二十四山 ring (Ring 5), 12sp for 十二地支 ring (Ring 4), 14sp for 八卦 rings (Rings 2–3) |
| Drop shadows | Subtle inner shadow on needle to convey physical depth |

**Minimum legibility requirement:** On a 5-inch 1080p screen at default brightness, all 12 地支 characters on Ring 4, all 24 山 characters on Ring 5, and all 60 分金 labels on Ring 6 must be readable by a user with 20/40 corrected vision at 30 cm viewing distance.

### 10.4 Interaction Behavior

- **Needle vs. dial:** The red pointer is **fixed at the top of the screen**. The entire dial assembly rotates as the device rotates. This mirrors physical luopan operation where the luopan is rotated while the needle aligns with north.
- **Snap behavior:** No snapping. The dial rotates continuously and smoothly to follow the fusion-computed heading.
- **Scale adjustment:** A pinch-to-zoom gesture allows the user to enlarge the dial for easier reading on small screens (zoom range: 0.8× to 2.0×).
- **Ring visibility toggle:** Users can hide individual rings via a long-press menu to reduce visual clutter.

### 10.5 坐向 Setting and Display

1. User rotates device until the desired **向** direction is under the pointer
2. User taps "Lock 向" (REQ-DISPLAY-06)
3. The locked 向 bearing and derived 坐 bearing are shown in a persistent overlay:
   - "向: 午 (180.0° True N)"
   - "坐: 子 (0.0° True N)"
4. The live needle continues to animate; a secondary gold tick mark on the dial ring marks the locked 向 position
5. Tapping "Clear 向" removes the lock

---

## 11. Edge Cases and Failure Modes

### 11.1 No Magnetometer

**Detection:** `SensorManager.getDefaultSensor(TYPE_MAGNETIC_FIELD_UNCALIBRATED)` returns null AND `getDefaultSensor(TYPE_MAGNETIC_FIELD)` returns null.

**Behavior:**
- All three compass display modes are replaced by a single error screen: "This device does not have a magnetometer. Compass functionality is unavailable."
- Bearing history remains accessible (read only)
- A bug report option is offered

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| User | Device with no magnetometer | App launched | Error screen shown; no crash; no misleading heading displayed |

---

### 11.2 No Gyroscope

**Detection:** `getDefaultSensor(TYPE_GYROSCOPE)` returns null.

**Behavior:**
- Compass display remains functional in magnetometer-only + accelerometer mode
- Heading smoothing is reduced (magnetometer-only, more jitter)
- Confidence capped at "Moderate" regardless of other conditions
- Advisory shown: "No gyroscope — heading may be less stable"

---

### 11.3 Broken / Stuck Sensor

**Detection:** Magnetometer returns identical values for >3 seconds (variance < 0.001 µT²) OR returns exactly {0, 0, 0} for any sample.

**Behavior:**
- Confidence immediately set to "Poor"
- Warning: "Sensor not responding — try restarting the app or device"
- Heading display frozen at last valid value with a "Sensor error" badge

**Acceptance Criteria:**

| Who | Given | When | Then |
|-----|-------|------|------|
| App | Magnetometer stuck at constant value | Heading displayed | Within 3 seconds, "Sensor error" badge shown; confidence "Poor" |

---

### 11.4 Extreme Magnetic Latitude (Inclination ≥ 80°)

**Affected region:** Approximately above 70°N or below 70°S geographic latitude, or within ~500 km of the magnetic dip pole.

**Detection:** WMM-computed inclination ≥ 80° at device location.

**Behavior:**
- Advisory banner: "Near a magnetic pole — horizontal field is very weak. Heading accuracy is significantly reduced."
- Confidence capped at "Moderate"
- Luopan Mode remains available with the advisory; 分金 ring hidden (confidence cannot reach "High")

**Note:** The horizontal component of the magnetic field (the component that drives compass heading) approaches zero as inclination approaches 90°. Standard consumer magnetometers cannot reliably resolve headings under these conditions.

---

### 11.5 Strong Local Interference (e.g., Inside a Vehicle)

**Detection:** Field magnitude deviation > 50% from WMM expected.

**Behavior:**
- Red interference warning with explanation: "Very strong magnetic interference detected (e.g., vehicle, machinery). Move away from metal structures for an accurate reading."
- Confidence set to "Poor"
- Bearing capture disabled until user manually overrides (override requires tapping "Capture anyway — I understand accuracy is compromised")

---

### 11.6 Device Held Vertically

**Detection:** Accelerometer tilt angle > 45° from horizontal.

**Behavior:**
- Confidence capped at "Moderate"
- Tilt indicator shows current tilt angle
- Advisory: "Hold flat for best accuracy"
- Heading continues to update; the fusion algorithm compensates for tilt mathematically but cannot eliminate increased noise from near-parallel orientation to gravity vector

---

### 11.7 Rapid Rotation / Excessive Movement

**Detection:** Gyroscope angular rate > 180°/s for > 0.5 seconds.

**Behavior:**
- Heading display shows "Stabilizing..." badge
- Confidence capped at "Moderate" during motion
- Returns to pre-motion confidence level when angular rate drops below 10°/s for > 1 second

---

### 11.8 GPS Completely Unavailable

**Behavior:**
- WMM-based declination is computed from last cached location (if available, age ≤ 30 days)
- If no cached location: True North toggle prompts for manual coordinate entry or falls back to Magnetic North only
- Interference check uses stored expected field magnitude from last cached location
- All warnings clearly indicate when location data is estimated or unavailable

---

### 11.9 Low Battery / Power-Saving Mode

**Detection:** `PowerManager.isPowerSaveMode()` returns true OR system reduces sensor delivery rate below 20 Hz.

**Behavior:**
- If sensor delivery drops below 20 Hz: advisory shown "System power-saving mode may reduce accuracy"
- No forced reduction in sampling rate beyond what Android enforces

---

## 12. Out of Scope for v1

The following are explicitly out of scope for the initial release. They MAY be considered for v2 or later.

| Item | Reason for Deferral |
|------|---------------------|
| **iOS support** | Platform focus; different sensor API; different calibration behavior |
| **Wear OS / smartwatch companion** | Sensor access on Wear OS is limited; accuracy goals not achievable |
| **七十二龍 / 六十龍 ring** | Requires ±0.5° accuracy not achievable on broad OEM hardware without significant per-device profiling |
| **天盤 / 人盤 / 地盤 triple-plate luopan** | Complexity requires v2 feature work and additional practitioner research |
| **擇日 (date selection) integration** | Different product domain; creates scope and QA burden |
| **AR Mountain overlay** | Camera AR on rotating luopan creates UX complexity; requires reliable >±1° accuracy to be useful |
| **Cloud sync of bearing records** | Privacy stance for v1; requires server infrastructure and compliance review |
| **Offline map / satellite imagery** | Out of scope for a compass app |
| **RTL layout support** | No current target locale requires RTL |
| **Multiple device calibration profiles** | v1 is per-device; multi-profile is v2 for users who share devices |
| **WMMHR2025 high-resolution model** | 18,210 coefficients vs. 168 for standard WMM; accuracy gain is sub-arcminute and irrelevant at phone magnetometer resolution |

---

## 13. Prioritization and MVP Cutline

### Priority Legend

| Priority | Definition |
|----------|------------|
| **P0** | Product broken or unsafe without this. Blocks release. |
| **P1** | Product works but experience materially degraded without this. |
| **P2** | Nice to have; negligible impact if missing. |

### Priority Summary

| ID | Requirement | Priority | Delivery Phase | Phase REQ |
|----|-------------|----------|---------------|-----------|
| REQ-SENSOR-01 | Uncalibrated magnetometer | P0 | 1 | [p1](REQ-luopan-p1-core-compass.md) |
| REQ-SENSOR-02 | Accelerometer / gravity | P0 | 1 | [p1](REQ-luopan-p1-core-compass.md) |
| REQ-SENSOR-03 | Gyroscope | P0 | 1 | [p1](REQ-luopan-p1-core-compass.md) |
| REQ-SENSOR-04 | 9-DOF sensor fusion | P0 | 1 | [p1](REQ-luopan-p1-core-compass.md) |
| REQ-SENSOR-05 | Sampling rate / power | P0 | 1 | [p1](REQ-luopan-p1-core-compass.md) |
| REQ-SENSOR-06 | Noise variance tracking | P1 | 1 | [p1](REQ-luopan-p1-core-compass.md) |
| REQ-SENSOR-07 | Sensor capability logging | P2 | 4 | [p4](REQ-luopan-p4-bearing-history.md) |
| REQ-CAL-01 | Calibration data collection | P0 | 1 | [p1](REQ-luopan-p1-core-compass.md) |
| REQ-CAL-02 | Ellipsoid fitting | P0 | 1 | [p1](REQ-luopan-p1-core-compass.md) |
| REQ-CAL-03 | Calibration quality scoring | P0 | 1 | [p1](REQ-luopan-p1-core-compass.md) |
| REQ-CAL-04 | Calibration persistence | P0 | 1 | [p1](REQ-luopan-p1-core-compass.md) |
| REQ-CAL-05 | Recalibration prompts | P1 | 4 | [p4](REQ-luopan-p4-bearing-history.md) |
| REQ-CAL-06 | Manual calibration bypass | P2 | 5 | [p5](REQ-luopan-p5-sighting-polish.md) |
| REQ-DETECT-01 | Field magnitude check | P0 | 1 | [p1](REQ-luopan-p1-core-compass.md) |
| REQ-DETECT-02 | Inclination check | P0 | 1 | [p1](REQ-luopan-p1-core-compass.md) |
| REQ-DETECT-03 | Interference warning UX | P0 | 1 | [p1](REQ-luopan-p1-core-compass.md) |
| REQ-DETECT-04 | Noise spike detection | P1 | 1 | [p1](REQ-luopan-p1-core-compass.md) |
| REQ-DETECT-05 | Interference flag in bearing record | P1 | 4 | [p4](REQ-luopan-p4-bearing-history.md) |
| REQ-NORTH-01 | Bundled WMM2025 | P0 | 2 | [p2](REQ-luopan-p2-true-north-capture.md) |
| REQ-NORTH-02 | Android GeomagneticField fallback | P0 | 2 | [p2](REQ-luopan-p2-true-north-capture.md) |
| REQ-NORTH-03 | GPS handling for declination | P0 | 2 | [p2](REQ-luopan-p2-true-north-capture.md) |
| REQ-NORTH-04 | North label on all headings | P0 | 2 | [p2](REQ-luopan-p2-true-north-capture.md) |
| REQ-DISPLAY-01 | Mode switching | P0 | 1 | [p1](REQ-luopan-p1-core-compass.md) |
| REQ-DISPLAY-02 | Modern Mode compass rose | P0 | 1 | [p1](REQ-luopan-p1-core-compass.md) |
| REQ-DISPLAY-03 | Confidence indicator | P0 | 1 | [p1](REQ-luopan-p1-core-compass.md) |
| REQ-DISPLAY-04 | Luopan Mode overview | P0 | 3 | [p3](REQ-luopan-p3-luopan-mode.md) |
| REQ-DISPLAY-05 | Luopan numerical readout | P0 | 3 | [p3](REQ-luopan-p3-luopan-mode.md) |
| REQ-DISPLAY-06 | 坐向 lock | **P0 (elevated)** | 3 | [p3](REQ-luopan-p3-luopan-mode.md) |
| REQ-DISPLAY-07 | Sighting mode camera | P1 | 5 | [p5](REQ-luopan-p5-sighting-polish.md) |
| REQ-DISPLAY-08 | Sighting mode capture | P1 | 5 | [p5](REQ-luopan-p5-sighting-polish.md) |
| REQ-DISPLAY-09 | Heading smoothing control | P2 | 5 | [p5](REQ-luopan-p5-sighting-polish.md) |
| REQ-DISPLAY-10 | Screen-on wake lock | **P0 (elevated)** | 1 | [p1](REQ-luopan-p1-core-compass.md) |
| REQ-DISPLAY-11 | Accessibility text scaling | P1 | 1 | [p1](REQ-luopan-p1-core-compass.md) |
| REQ-DISPLAY-12 | Dark / light theme | P2 | 5 | [p5](REQ-luopan-p5-sighting-polish.md) |
| REQ-CAPTURE-01 | Bearing record schema | P0 | 2 | [p2](REQ-luopan-p2-true-north-capture.md) |
| REQ-CAPTURE-02 | Capture flow UX | P0 | 2 | [p2](REQ-luopan-p2-true-north-capture.md) |
| REQ-CAPTURE-03 | Bearing history screen | P1 | 4 | [p4](REQ-luopan-p4-bearing-history.md) |
| REQ-CAPTURE-04 | Data persistence (encrypted) | P0 | 2 | [p2](REQ-luopan-p2-true-north-capture.md) |
| REQ-CAPTURE-05 | Share / export | P2 | 5 | [p5](REQ-luopan-p5-sighting-polish.md) |
| REQ-CAPTURE-06 | Location privacy confirmation | P0 | 2 | [p2](REQ-luopan-p2-true-north-capture.md) |
| REQ-DECL-01 | North type toggle | P0 | 2 | [p2](REQ-luopan-p2-true-north-capture.md) |
| REQ-DECL-02 | Declination info panel | P1 | 2 | [p2](REQ-luopan-p2-true-north-capture.md) |
| REQ-DECL-03 | Grid north | P2 | 5 | [p5](REQ-luopan-p5-sighting-polish.md) |
| REQ-L10N-01 | Four supported locales | P0 | 1 | [p1](REQ-luopan-p1-core-compass.md) |
| REQ-L10N-02 | Luopan terminology handling | P0 | 3 | [p3](REQ-luopan-p3-luopan-mode.md) |
| REQ-L10N-03 | RTL exclusion | P0 (explicit non-requirement) | 1 | [p1](REQ-luopan-p1-core-compass.md) |
| REQ-LUOPAN-01 | 後天八卦 ring — trigram + 方位 direction labels | P0 | 3 | [p3](REQ-luopan-p3-luopan-mode.md) |
| REQ-LUOPAN-02 | 十二地支 ring — twelve directional sectors | P0 | 3 | [p3](REQ-luopan-p3-luopan-mode.md) |
| REQ-NFR-01 | Heading latency <50 ms | P0 | 1 | [p1](REQ-luopan-p1-core-compass.md) |
| REQ-NFR-02 | Heading update rate ≥20 Hz | P0 | 1 | [p1](REQ-luopan-p1-core-compass.md) |
| REQ-NFR-03 | Battery ≤5%/hr active | P0 | 1 | [p1](REQ-luopan-p1-core-compass.md) |
| REQ-NFR-04 | Min SDK 26, target SDK 35 | P0 | 1 | [p1](REQ-luopan-p1-core-compass.md) |
| REQ-NFR-05 | Full offline operation | P0 | 1 | [p1](REQ-luopan-p1-core-compass.md) |
| REQ-NFR-06 | Data privacy | P0 | 1 | [p1](REQ-luopan-p1-core-compass.md) |
| REQ-NFR-07 | Sensor sampling rates | P0 | 1 | [p1](REQ-luopan-p1-core-compass.md) |
| REQ-NFR-08 | Cold-start time | P1 | 2 | [p2](REQ-luopan-p2-true-north-capture.md) |
| REQ-NFR-09 | 99.5% crash-free | P0 | 1 | [p1](REQ-luopan-p1-core-compass.md) |

### MVP Cutline

**MVP = All P0 requirements.**

P1 items may be cut from the initial release if schedule requires, with the following exceptions where P1 items are considered P0 for the core persona (Master Li):
- REQ-DISPLAY-06 (坐向 lock) — core to Persona 1's primary workflow; promote to P0
- REQ-DISPLAY-10 (screen-on lock) — prevents heading from going dark mid-reading; promote to P0

**Revised P0 for MVP:**
All listed P0 requirements above PLUS REQ-DISPLAY-06 and REQ-DISPLAY-10.

**Note on REQ-LUOPAN-01 and REQ-LUOPAN-02:** Both are P0. The 後天八卦 方位 labels and 十二地支 ring are core to the luopan display and the primary differentiator for Persona 1. A luopan without directional labels and the 地支 ring is not a functional luopan.

---

## 14. Open Questions and Risks

### OQ-01: WMM2025 Licensing and Bundling

**Question:** Can WMM2025 coefficients be bundled in an Android APK without license restriction?

**Current position:** WMM is produced jointly by NOAA (USA) and BGS (UK). NOAA explicitly states the software and data are in the public domain. The coefficients file (`WMM.COF`) is freely redistributable. However, the app should attribute: "Magnetic declination data from WMM2025, NOAA / British Geological Survey."

**Risk level:** Low. No licensing barrier expected.

**Action:** Legal team should confirm with NOAA's NCEI licensing terms before publishing. Verify BGS terms separately.

**Resolution deadline:** Before engineering starts on REQ-NORTH-01.

---

### OQ-02: WMM Update Cadence

**Question:** WMM is updated every 5 years (WMM2025 valid 2025–2030). How should the app handle model expiry?

**Options:**
1. App auto-updates WMM when a new version is available via an app update
2. App ships with WMM + expiry logic that degrades to Android `GeomagneticField` when expired (already specified in REQ-NORTH-02)
3. App downloads updated model from a hosted endpoint (requires network; conflicts with offline goal)

**Recommended approach:** Option 2 as baseline (already specified). Add Option 3 as a background, optional check on launch with user opt-in. This defers the server requirement to v2.

**Risk:** Devices that never update the app after 2030 will silently downgrade to the Android model. Acceptable per REQ-NORTH-02's labeling requirement.

---

### OQ-03: Validating the ±1° Accuracy Claim

**Question:** How do we verify ±1° accuracy before shipping?

**Proposed testing protocol:**
1. Identify 10–15 known-bearing test sites (surveyed landmarks, or GPS-anchored reference lines)
2. Test on 10+ device models covering: Pixel (known-good sensors), Samsung Galaxy S (common, variable), Xiaomi Redmi (mid-range, often poor sensors), OPPO/Realme (budget)
3. Per device: perform full calibration per REQ-CAL-01/02/03, achieve "Good" calibration quality, then take 20 repeated measurements at each test site
4. Compute mean error and 1σ vs. reference
5. Pass criterion: mean |error| ≤ 1.0°, SD ≤ 0.5°, on ≥7 of 10 devices under Good conditions

**Risk:** Some Android OEMs have notoriously poor magnetometers (common in devices <$200 retail). The ±1° target under Good conditions may not be achievable on the lowest OEM tier even with perfect calibration.

**Mitigation:** If a device cannot achieve ±1° even after calibration (confidence calibration residual cannot reach <1.0 µT), the app should permanently cap confidence at "Moderate" for that device model and display an advisory: "Your device's magnetometer limits heading precision to approximately ±3°. For ±1° accuracy, a higher-grade device is recommended." This is a transparent, honest UX response that protects user trust.

---

### OQ-04: OEM and Sensor Quality Variation

**Known OEM issues:**
- Samsung Galaxy devices use both AKM and STMicroelectronics magnetometers depending on region and batch — significant variance within the same model
- Xiaomi MIUI applies aggressive system-level magnetometer calibration that may interfere with uncalibrated sensor readings
- Some Huawei/Honor devices report `TYPE_MAGNETIC_FIELD_UNCALIBRATED` with partially-corrected data (not truly uncalibrated)
- Low-cost devices may have magnetometers mounted near NFC antennas or speaker magnets with no shielding

**Mitigations:**
1. REQ-SENSOR-07 (sensor profile logging) helps build a compatibility matrix over time
2. OEM-specific calibration quality thresholds may be needed (engineering decision, flagged here for awareness)
3. A device-specific known-issues database (maintained by engineering, surfaced in-app for affected models) is a v2 consideration

---

### OQ-05: Devices with Notoriously Bad Magnetometers

**Known problematic families (as of 2025-2026):**
- Certain MediaTek-chipset devices report magnetometer data at ~10 Hz actual rate despite requesting `SENSOR_DELAY_GAME`
- Some budget Android devices have magnetometers with ±5 µT full-scale resolution (insufficient for ±1° accuracy even after calibration)

**Action:** The pre-calibration check (§9.2) should also validate sensor resolution. If reported `SensorEvent.accuracy` is `SENSOR_STATUS_UNRELIABLE` persistently, advise the user that their device may not support High confidence mode.

---

### OQ-06: Privacy Regulations for Location Data

**Question:** Storing GPS coordinates in bearing records — does this require GDPR / PIPL / APPI compliance?

**Assessment:**
- Bearing records are stored only on-device; not transmitted (REQ-NFR-06)
- Under GDPR, PIPL, and APPI, personal data stored entirely on a user's own device is generally not subject to the same processing obligations as server-side storage
- However, the privacy notice in the app listing must disclose that location may be stored at user choice

**Action:** Legal review required before Play Store submission. Draft privacy notice before shipping.

---

### OQ-07: Confidence Thresholds — Calibration

**Open question:** Are the calibration quality thresholds (residual RMS ≤ 1.0 µT for "Good") achievable on real devices during typical figure-8 motions?

**Hypothesis:** Achievable on high-end devices; may require longer collection time on mid-range devices.

**Action:** Engineering to prototype calibration on 5 device models before finalizing thresholds. If residuals are consistently higher, thresholds may need per-device adjustment.

---

## 15. Success Metrics

### 15.1 Accuracy Metrics (Post-Launch)

| Metric | Measurement Method | Target |
|--------|-------------------|--------|
| **High-confidence heading accuracy** | Beta tester comparison of app reading vs. reference compass at known-bearing sites (crowdsourced via structured feedback form) | Mean |error| ≤ 1.5° for "High" confidence readings |
| **Calibration completion rate** | Percentage of users who complete the calibration flow at least once within 7 days of install | ≥ 70% |
| **Calibration quality achieved** | Percentage of completed calibrations that achieve "Good" quality | ≥ 60% |
| **Interference warning frequency** | Average number of interference warnings shown per session | < 2 per session (high frequency indicates users taking readings in bad environments; used for UX iteration) |
| **Confidence distribution** | Percentage of heading-display time in High / Moderate / Poor | High > 40% in 30-day rolling window |

### 15.2 Retention Metrics (by Persona)

| Persona Group | Proxy Identifier | Target Metric |
|---------------|-----------------|---------------|
| Professional practitioners (Persona 1) | Users who access Luopan Mode ≥3 sessions | D30 retention ≥ 40% |
| Technical/outdoor users (Persona 2, 3) | Users who save ≥5 bearing records | D30 retention ≥ 35% |
| General consumers (Persona 4) | Users who open Modern Mode only | D7 retention ≥ 20% |

### 15.3 Quality Metrics

| Metric | Target |
|--------|--------|
| Crash-free sessions | ≥ 99.5% (30-day rolling) |
| App store rating | ≥ 4.3 stars at 90 days post-launch |
| Interference warning false-positive rate | < 5% (estimated from user feedback: "I was in a clear field but got an interference warning") |
| Bearing capture failure rate | < 0.1% (saves that fail silently) |

### 15.4 Practitioner-Specific Feedback

Within 60 days of launch, conduct a structured survey of beta practitioners (feng shui, surveyors):
- "Does the app's High confidence reading match your physical luopan within 1–2°?" (target: ≥ 80% "Yes" or "Usually")
- "Do you trust the app's interference warnings?" (target: ≥ 75% "Yes")
- "Would you use this alongside or instead of your physical luopan?" (target: ≥ 40% "alongside")

---

## 16. Traceability Matrix

| User Story | Requirements | FSPEC (TBD) |
|------------|-------------|------------|
| US-01 (bearing on distant object) | REQ-SENSOR-04, REQ-DISPLAY-07, REQ-DISPLAY-08, REQ-CAPTURE-01, REQ-CAPTURE-02 | TBD |
| US-02 (坐向 orientation) | REQ-SENSOR-01–04, REQ-DISPLAY-04–06, REQ-NORTH-01–04, REQ-DECL-01 | TBD |
| US-03 (field calibration) | REQ-CAL-01–05, REQ-SENSOR-01 | TBD |
| US-04 (interference detection) | REQ-DETECT-01–04, REQ-CAL-03, REQ-DISPLAY-03 | TBD |
| US-05 (record bearing) | REQ-CAPTURE-01, REQ-CAPTURE-02, REQ-CAPTURE-04, REQ-CAPTURE-06, REQ-DETECT-05 | TBD |
| US-06 (review bearings) | REQ-CAPTURE-03, REQ-CAPTURE-04, REQ-CAPTURE-05 | TBD |
| US-07 (24 Mountains reading) | REQ-DISPLAY-04, REQ-DISPLAY-05, REQ-L10N-02, REQ-LUOPAN-01, REQ-LUOPAN-02 | TBD |
| US-08 (general consumer) | REQ-DISPLAY-01–03, REQ-DISPLAY-11, REQ-L10N-01 | TBD |
| US-09 (check declination) | REQ-NORTH-01–04, REQ-DECL-01–02 | TBD |
| US-10 (no GPS) | REQ-NORTH-03 | TBD |
| US-11 (vertical hold) | REQ-SENSOR-02, REQ-DISPLAY-02 (tilt indicator) | TBD |
| US-12 (後天八卦 方位 + 十二地支 reading) | REQ-LUOPAN-01, REQ-LUOPAN-02, REQ-DISPLAY-05 | TBD |

---

*End of REQ-luopan-compass v0.2-draft*

*Next step: Engineering review and TSPEC authoring (se-author). TE authoring of PROPERTIES (te-author).*
