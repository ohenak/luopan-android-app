# Cross-Review: Product Manager — Implementation
**Reviewer Role:** Product Manager
**Document:** Implementation (feat-luopan-p1-core-compass)
**Date:** 2026-04-24
**Recommendation:** Need Attention

## Summary

The Phase 1 implementation covers the required structural scope — sensor fusion (Madgwick), calibration wizard, interference detection, confidence model, and edge-case handling are all present and largely correct. However, two build-blocking bugs were found: a field-name mismatch (`recorded_at` vs. `saved_at_utc`) that would prevent compilation, and a fundamentally broken interference-detection baseline that makes the field-deviation computation meaningless. Additionally, three of the four required locales are missing, which breaks the P0 localization requirement.

---

## Findings

### High Severity

- **H-01 Build-breaking field name mismatch (`recorded_at` / `saved_at_utc`):**
  `CalibrationRecord.kt` declares the timestamp field as `saved_at_utc`, but both `CalibrationRepository.toRecord()` (line 31) and `CompassViewModel.loadCalibrationAge()` (line 66) reference `record.recorded_at`. This is a Kotlin compilation error; the app cannot be built in its current state. Calibration age display and the confidence model's `cal_age` dimension are both broken.

- **H-02 Interference field-deviation baseline is self-referential:**
  `CompassViewModel.startSensorCollection()` computes `fieldDeviation` as `(noiseVariance / 50.0)` and then sets `expectedField_uT = magnitude.toFloat()` — i.e., the expected field equals the measured field. This makes `fieldDeviation` a function of noise only, not the deviation from an expected geomagnetic reference. As a result, REQ-DETECT-01 (field magnitude check at 15%/25% thresholds) is never triggered by actual magnetic interference; the interference warning will not fire for REQ-DETECT-01 conditions. The `SystemLocationProvider` and the `GeomagneticField`/50 µT fallback path described in the spec are present as data structures but are never wired into the `InterferenceMetrics` computation.

- **H-03 Localization covers only one of four required locales:**
  REQ-L10N-01 requires `zh-Hant`, `zh-Hans`, `ja`, and `en`. Only `values/` (English) and `values-zh/` (generic Chinese — covers neither Hant nor Hans explicitly, and is a single file used for both) exist. There are no `values-zh-rTW/` (Traditional Chinese), `values-zh-rCN/` (Simplified Chinese), or `values-ja/` (Japanese) directories. The `values-zh/` file is also missing several strings present in the default (`compass_unavailable`, `no_magnetometer_explanation`, `sensor_error`, `sensor_not_responding`, `interference_explanation`). Scenario F (Japanese localization) is entirely unimplemented.

### Medium Severity

- **M-01 Heading digital readout lacks 0.1° precision:**
  REQ-DISPLAY-02 specifies a "0.1° digital readout." `formatHeading()` in `CompassViewModel` formats to whole-degree integers only (`"%03d°"`). There is no tenths-of-a-degree variant in either `FORMAT_DEGREES` or `FORMAT_DMS` paths.

- **M-02 Calibration quality score not fed into `ConfidenceModel`:**
  REQ-CAL-03 / §5.5 require `cal_quality` (Good/Fair/Poor) to be one of the five dimensions in the confidence min-aggregation. `ConfidenceModel.scoreCalibrationQuality()` (line 88) only checks whether `ageDays < 0` (uncalibrated), always returning `GOOD` for any calibrated device regardless of recorded quality (FAIR → should cap at MODERATE; POOR → should cap at POOR). The actual quality value from `CalibrationRecord.quality` is never read by the confidence model.

- **M-03 Interference warning is not user-dismissible:**
  REQ-DETECT-03 requires the interference warning to be "dismissible." The `interferenceBanner` is a plain `TextView` with no tap-to-dismiss handler; it only clears when the interference state resolves. The spec is explicit that the user must be able to dismiss it manually.

- **M-04 WakeLock never acquired or released:**
  REQ-DISPLAY-10 (P0 elevated) requires a screen-on wake lock. `WakeLockManager` exists and the `WAKE_LOCK` permission is declared, but `WakeLockManager` is not instantiated or called anywhere in `CompassActivity`. The wake lock is never acquired and never released.

- **M-05 Sensor listeners not managed in `onStop()`/`onStart()`:**
  REQ-SENSOR-05 (Phase 1 Implementation Note) explicitly mandates unregistering in `Activity.onStop()` and re-registering in `Activity.onStart()`. `CompassActivity` has no `onStop()` or `onStart()` overrides. The `SensorLayer.frames()` coroutine flow lives in `CompassViewModel` and is tied to `viewModelScope` rather than the Activity lifecycle, meaning sensors continue running during transient focus loss as a side effect, but sensors are **not** released when the user backgrounds the app — violating REQ-NFR-03 (0% battery in background).

- **M-06 Coverage computation does not follow the REQ-CAL-01 spec definition:**
  The spec defines per-axis coverage as `axis_range_i / max(axis_range across all three axes)`. `CalibrationEngine.getCoverageScore()` instead computes `xRange / (xRange + yRange + zRange)` — this is only the X-axis fraction and it uses sum-of-ranges as the denominator, not max. `CalibrationWizardActivity` further fakes Y and Z values by multiplying the single coverage score by 0.9 and 0.8 respectively (comment: "Simplified per-axis coverage (proxy via total coverage thirds)"). Coverage gating (`MIN_COVERAGE = 0.15`) and quality classification thresholds are therefore applied against incorrect inputs.

### Low Severity / Observations

- **L-01 Soft-iron matrix is spherical (identity-scaled), not full ellipsoid:**
  `CalibrationEngine.fitEllipsoid()` fits a sphere (hard-iron offset only) and outputs an identity-scaled soft-iron matrix (`1/r` on diagonal, 0 elsewhere). REQ-CAL-02 requires a full soft-iron correction matrix. While the spec acknowledges the 50 µT fallback as a Phase 1 known limitation, it does not relax the soft-iron requirement itself. This may degrade accuracy on devices with significant soft-iron distortion.

- **L-02 `CalibrationRepository.save()` is not atomic per REQ-CAL-04:**
  `save()` performs two separate `dao.upsert()` calls (one to promote current→rollback, one to write new current). Room executes each `@Insert` as an individual transaction. A process kill between the two upserts would leave the rollback record updated but the new current record absent. The spec requires an atomic write (single transaction or write-then-rename).

- **L-03 `location_fallback_advisory` field exists but is never shown:**
  REQ-DETECT-01 (Phase 1 note) requires the UI to show "Location unavailable — interference detection uses approximate field strength" when no location is available. `CompassUiState` has a `location_fallback_advisory` field but it is hardcoded to `false` in `buildUiState()` and there is no corresponding UI element in the layout.

- **L-04 Confidence badge text is hardcoded English strings:**
  `CompassActivity.observeUiState()` sets badge text to `"High accuracy"`, `"Moderate accuracy"`, `"Poor accuracy"` as string literals rather than string resource references. These will not be translated in any locale.

- **L-05 `NoiseSpikeFilter` spike threshold is 50 µT (vs. spec's 10 µT):**
  REQ-DETECT-04 specifies rejection of outliers `>10 µT` from the rolling mean. `NoiseSpikeFilter` uses `spikeThreshold = 50f`, which is 5× the spec value and would allow large spikes to pass through.

- **L-06 `CalibrationRecord` field `residual_rms` is never populated by `CalibrationRepository.toRecord()`:**
  `CalibrationRepository.toRecord()` does not map `result.residualMicroTesla` to `record.residual_rms`. The `residualMicroTesla` is always stored as 0 in the DB (and `fromRecord()` hardcodes `residualMicroTesla = 0f` on read-back). The calibration age UI will show correct age, but any future use of stored residual for re-calibration prompts (Phase 4) will be broken.

- **L-07 `ManifestActivity` class reference is `CompassActivity` not fully qualified:**
  `AndroidManifest.xml` registers `.CompassActivity` (short form). This works but the companion `CalibrationWizardActivity` is registered as `.calibration.ui.CalibrationWizardActivity`. The short form for `CompassActivity` requires the namespace to resolve correctly; no bug, but inconsistent style.

---

## Acceptance Criteria Traceability

| REQ ID | Title | Status |
|--------|-------|--------|
| REQ-SENSOR-01 | Uncalibrated magnetometer (`TYPE_MAGNETIC_FIELD_UNCALIBRATED`) | ✅ |
| REQ-SENSOR-02 | Accelerometer / gravity | ✅ |
| REQ-SENSOR-03 | Gyroscope + fallback (no-gyro advisory shown) | ✅ |
| REQ-SENSOR-04 | 9-DOF sensor fusion (Madgwick, quaternion-based, gimbal-lock-safe) | ✅ |
| REQ-SENSOR-05 | Sampling rate / power management (release on background) | ❌ (M-05) |
| REQ-SENSOR-06 | Noise variance tracking (rolling IQR, feeds confidence) | ✅ |
| REQ-CAL-01 | Calibration data collection (≥200 samples, 3D coverage check) | ⚠️ (M-06: coverage formula incorrect) |
| REQ-CAL-02 | Ellipsoid fitting (hard-iron + soft-iron, residual RMS) | ⚠️ (L-01: sphere only, no true soft-iron) |
| REQ-CAL-03 | Calibration quality scoring (Good/Fair/Poor → confidence) | ❌ (M-02: quality not read by confidence model) |
| REQ-CAL-04 | Calibration persistence and versioning (encrypted, rollback) | ⚠️ (H-01: build error; L-02: non-atomic save; L-06: residual not persisted) |
| REQ-DETECT-01 | Field magnitude check (vs. expected; 15%/25% thresholds) | ❌ (H-02: baseline is self-referential) |
| REQ-DETECT-02 | Inclination check (3°/8° thresholds) | ⚠️ (expectedInclination hardcoded to 0°; deviation check computes against pitch which is not dip angle) |
| REQ-DETECT-03 | Interference warning UX (red overlay, explanation, dismissible, confidence→Poor) | ⚠️ (M-03: not dismissible; otherwise present) |
| REQ-DETECT-04 | Noise spike rejection (>10 µT outliers) | ⚠️ (L-05: threshold is 50 µT, not 10 µT) |
| REQ-DISPLAY-01 | Mode switching hidden (only Modern Mode accessible) | ✅ |
| REQ-DISPLAY-02 | Modern Mode compass rose (360° rose, 0.1° readout, north label, confidence, cal age, tilt) | ⚠️ (M-01: 0.1° precision missing; rest present) |
| REQ-DISPLAY-03 | Confidence badge (High/Moderate/Poor, color+text) | ⚠️ (L-04: English-only hardcoded text) |
| REQ-DISPLAY-10 | Screen-on wake lock | ❌ (M-04: WakeLockManager never used) |
| REQ-DISPLAY-11 | Accessibility — text scaling (16sp minimum) | ✅ (heading at 48sp; north label at 14sp is below minimum — minor) |
| REQ-L10N-01 | Four supported locales (zh-Hant, zh-Hans, ja, en) | ❌ (H-03: only en + generic zh) |
| REQ-L10N-03 | RTL exclusion (LTR-only, documented) | ✅ |
| REQ-NFR-01 | Heading latency <50 ms (95th percentile) | ⚠️ (architecture supports it; not instrumented/verified) |
| REQ-NFR-02 | Heading update rate ≥20 Hz | ✅ (SENSOR_DELAY_GAME targets ~50 Hz) |
| REQ-NFR-03 | Battery ≤5%/hr active; 0% in background | ❌ (M-05: sensors not released on background) |
| REQ-NFR-04 | SDK targets (min 26, target 35) | ✅ |
| REQ-NFR-05 | Full offline operation | ✅ (no INTERNET permission; lint rule enforced) |
| REQ-NFR-06 | Data privacy (no transmission; calibration encrypted) | ✅ (SQLCipher + DatabaseKeyManager) |
| REQ-NFR-07 | Sensor sampling rates (mag ≈50 Hz; accel/gyro ≈100 Hz) | ⚠️ (all sensors use same SENSOR_DELAY_GAME; accel/gyro not independently set to 100 Hz) |
| REQ-NFR-09 | Crash-free rate ≥99.5% | ⚠️ (H-01 build error would cause guaranteed crash; once fixed, architecture is sound) |
| §8 Edge: No magnetometer | Error screen; no crash; no misleading heading | ✅ |
| §8 Edge: No gyroscope | Advisory shown; confidence capped at Moderate | ✅ |
| §8 Edge: Broken/stuck sensor | "Sensor error" badge within 3 s | ✅ (2 s stuck threshold, badge shown) |
| §8 Edge: Device held vertically | Tilt indicator; confidence capped at Moderate | ✅ |
| §8 Edge: Rapid rotation >180°/s | "Stabilizing…" badge; confidence Moderate | ✅ |
| §8 Edge: Power-saving mode | Advisory if sensor rate drops | ✅ |
| Scenario A | First-time user, no calibration | ✅ (Poor badge + CTA banner present) |
| Scenario B | Calibration flow (quality + accept + persist) | ⚠️ (H-01 breaks persistence; coverage proxy is approximate) |
| Scenario C | Good-conditions accuracy (±1°, High confidence) | ⚠️ (blocked by H-02; field test not possible until fixed) |
| Scenario D | Interference detected (red overlay, heading continues) | ❌ (H-02: interference trigger broken) |
| Scenario E | No magnetometer error screen | ✅ |
| Scenario F | Japanese localization | ❌ (H-03: no ja locale) |
| Scenario G | Calibration cancellation (data discarded, rollback intact) | ✅ |
| Scenario H | Poor-quality calibration acceptance | ✅ |
| Scenario I | OOM kill during calibration (atomic write) | ⚠️ (L-02: non-atomic Room upsert pair) |
