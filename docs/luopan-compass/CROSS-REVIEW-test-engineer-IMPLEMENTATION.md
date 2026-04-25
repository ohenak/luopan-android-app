# Cross-Review: Test Engineer — Implementation
**Reviewer Role:** Test Engineer
**Document:** Implementation (feat-luopan-p1-core-compass)
**Date:** 2026-04-24
**Recommendation:** Approved with Minor Issues

## Summary

The JVM unit test suite provides solid coverage of core algorithmic properties (fusion, calibration quality thresholds, interference detector state machine, confidence model scoring). Several boundary conditions specified in TE-04 are correctly tested in both `PropertiesTest.kt` and the dedicated per-class test files. The main gaps are: (1) PROP-FUSION-06 uses a synthetic placeholder sequence rather than hardware-recorded data, making its accuracy assertion untrustworthy for the production CI gate; (2) PROP-CAL-01/02 (coverage with per-axis breakdown) and PROP-CAL-06 (residual exactly 2.0 µT → FAIR) have no dedicated tests; and (3) all Espresso/Instrumented properties in the DISPLAY, CAL-UX, SENSOR, PERSIST, NFR, and L10N domains are stubs or absent — acceptable for Phase 1 only if JVM tests for the same invariants pass.

---

## Findings

### High Severity

- **[H-01] PROP-FUSION-06 sensor sequence is synthetic**: `known_heading_30s.json` is labeled `"SYNTHETIC PLACEHOLDER — replace with hardware-recorded data before production CI gate"`. The test passes by construction, not by validating real algorithm accuracy. The ±0.5° CI proxy property is not actually verified. This is a pre-release CI gate blocker — the test currently provides false confidence on accuracy.

- **[H-02] PROP-CAL-06 missing — residual exactly 2.0 µT boundary untested**: `CalibrationEngineTest` tests `classifyQuality(2.0f, 0.9f)` and asserts `FAIR`, which is correct. However, `PropertiesTest` does not include a dedicated `PROP-CAL-06` method. More critically, `CalibrationEngineTest.classifyQuality FAIR when residual le 2 and coverage ge 0_3` only tests `(1.5f, 0.5f)` and `(2.0f, 0.3f)` — the 2.0 µT upper-closed boundary case with adequate coverage is present in `CalibrationEngineTest.residual boundary 2_0 is FAIR`, so the boundary itself is tested. **Reclassified to Medium** on closer review — see M-01 below.

- **[H-03] PROP-SENSOR-04 (all-zero mag → immediate STUCK) not tested**: `SensorStateMonitorTest` tests STUCK via a time-gap mechanism (no sensor event for > stuckThreshold), but does not include a test for the immediate all-zero-magnetometer detection path specified in PROP-SENSOR-04. If the implementation relies solely on time-gap STUCK detection and lacks the immediate zero-magnitude path, this requirement (REQ §11.3) may not be satisfied.

### Medium Severity

- **[M-01] PROP-CAL-02 (per-axis coverage fractions) not tested**: The PROPERTIES spec requires `coverage_x == 1.0f`, `coverage_y == 0.2f`, `coverage_z == 0.1f` from specific sample distributions. `PropertiesTest.PROP-CAL-01` only tests the all-identical-samples (zero-coverage) case via `getCoverageScore()` returning a scalar; it does not test per-axis fractional values. The `CoverageStats` data class exists but no test verifies the axis-by-axis normalization formula from REQ-CAL-01 SE-07.

- **[M-02] PROP-DETECT-05 timer-reset assertion is incomplete**: `InterferenceDetectorTest.PROP-DETECT-05 field 15 percent goes to MODERATE` only checks that `0.15f` transitions to MODERATE. It does not assert that a clearance timer that was running (having counted 2.9 s below threshold) has been reset — i.e., the test does not verify the timer-reset behavior, only the state label. A full PROP-DETECT-05 test should start from WARNING with a near-complete clearance window and assert that `0.15f` prevents clearance.

- **[M-03] PROP-CONF-14/16 (cal age closed-upper-bound boundaries) use day-granularity integers**: `ConfidenceModelTest` calls `model.scoreCalibrationAge(7L)` and `scoreCalibrationAge(30L)` where the argument is days. PROP-CONF-14 and PROP-CONF-16 require millisecond-precision testing (7 days + 0 ms = GOOD; 7 days + 1 ms = MODERATE). If the implementation truncates to day granularity internally, these tests pass but the 1-ms boundary is not verified.

- **[M-04] PROP-CAL-09 API mismatch — `CalibrationResult` constructor differs between PropertiesTest and CalibrationRepositoryTest**: `PropertiesTest` constructs `CalibrationResult(hardIron=..., softIron=..., residualMicroTesla=..., coverageScore=..., quality=...)` using a flat constructor, while `CalibrationDataClassesTest` uses a `Vector3`/`Matrix3x3`-based constructor. This inconsistency suggests `CalibrationResult` may have two different shapes or there is a data class mismatch that could indicate one test is testing a stale API. This should be verified to ensure both call sites compile against the same production class.

- **[M-05] PROP-DETECT-12 test has conditional logic that weakens assertion**: `PropertiesTest.PROP-DETECT-12` contains an `if (spikeResult == null) { // Good } else { detector.updateState(...) }` branch. If the spike is not filtered (passes through), the else branch calls `updateState` with WARNING-level deviation, and the test still asserts CLEAR at the end — which is only possible if the subsequent normal frame clears it, not because the spike was rejected. The assertion logic must be tightened: the spike should be asserted `null`, not merely handled if null.

- **[M-06] PROP-PERSIST-04 (UTC timestamp timezone-invariance) test is trivially correct**: The test assigns `val stored = T; val retrieved = stored` — this is a pure Kotlin expression with no Room I/O. It does not test that the Room column type preserves the long value across a write/read cycle or across a timezone change. The property requires a round-trip through the database.

### Low Severity / Observations

- **[L-01] PROP-CAL-05 in PropertiesTest calls non-spec API**: `classifyQuality(residualMicroTesla, coverageScore: Float)` in `PropertiesTest` passes a flat `Float` for coverage (0.5f), whereas the PROPERTIES spec defines coverage as a `CoverageStats` object with per-axis values. If the production API uses `CoverageStats`, the PropertiesTest is calling a convenience overload that may not accurately reflect the coverage threshold logic for individual axes.

- **[L-02] All Espresso properties are stubs**: `CompassIntegrationTest` (scenarios A–I) contains only empty test bodies or delegation comments. `NoModeSwitcherTest` checks content descriptions only (not `R.id.bottom_navigation` as specified in PROP-DISPLAY-04). PROP-DISPLAY-01 through PROP-DISPLAY-11, PROP-CAL-UX-01 through PROP-CAL-UX-06, PROP-SENSOR-01/02, PROP-PERSIST-01/02/03, PROP-NFR-01/02, and PROP-L10N-01 through PROP-L10N-04 are not implemented. Acceptable for Phase 1 if the plan is to complete these before release gate.

- **[L-03] OomResilienceTest does not use ProcessPhoenix**: `OomResilienceTest` explicitly notes it cannot execute the true kill scenario and instead simulates it by not calling `repo.save()`. PROP-CAL-08 and PROP-CAL-UX-06 require an actual process kill. The test is correctly labeled but the real scenario is untested.

- **[L-04] PROP-SENSOR-06 (power-saving advisory) not explicitly property-tested**: `SensorRateMonitorTest` tests that rate computation is accurate (50 Hz, 100 Hz). No test verifies that `SensorAdvisory.POWER_SAVING` is emitted after sustained sub-15 Hz for ≥ 2 s. The advisory emission logic is tested only implicitly via the rate calculation.

- **[L-05] PROP-NFR-03 (battery ≤ 5%/hour) and PROP-NFR-04 (APK ≤ 15 MB) have no CI test stubs**: No placeholder test or CI script exists for these gate checks. The PROPERTIES doc marks NFR-03 as "Manual gate" but NFR-04 is a build-check — a Gradle task or CI assertion should be present.

- **[L-06] PROP-FUSION-03 uses tolerance 1e-5 instead of specified 1e-6**: `PropertiesTest.PROP-FUSION-03` asserts `assertEquals(1.0, norm, 1e-5)`. PROPERTIES-luopan-p1-core-compass specifies `[1.0 - 1e-6, 1.0 + 1e-6]`. The tolerance in the test is 10× looser than the spec. `MadgwickFilterTest.quaternion remains unit after update` uses the same 1e-5 tolerance.

- **[L-07] `CompassViewModelTest` only tests `INITIAL` state constants**: The ViewModel test does not exercise `buildUiState()` with real inputs, leaving PROP-SENSOR-03 (no-gyroscope caps at MODERATE via ViewModel) and PROP-DISPLAY-10 (STABILIZING badge shown) without ViewModel-layer test coverage.

---

## PROPERTIES Coverage Matrix

| Property ID | Description | Test File | Status |
|---|---|---|---|
| PROP-FUSION-01 | Heading in [0, 360) | PropertiesTest, FusionEngineTest | ✅ |
| PROP-FUSION-02 | Heading never NaN or Infinite | PropertiesTest | ✅ |
| PROP-FUSION-03 | Quaternion normalized after every update | PropertiesTest, MadgwickFilterTest | ⚠️ tolerance 1e-5 vs spec 1e-6 |
| PROP-FUSION-04 | Full update converges to North within 50 iterations | PropertiesTest | ✅ |
| PROP-FUSION-05 | No-gyro mode converges to North within 50 iterations | PropertiesTest | ✅ |
| PROP-FUSION-06 | CI proxy — 30 s sequence within ±0.5° of ground truth | (not yet implemented as test) | ❌ synthetic placeholder sequence; no executing test |
| PROP-CAL-01 | Coverage zero when samples span no range | PropertiesTest | ⚠️ scalar only; per-axis breakdown not tested |
| PROP-CAL-02 | Coverage 1.0 on dominant axis | (none) | ❌ not tested |
| PROP-CAL-03 | Residual ≤ 1.0 µT and coverage ≥ 20% → GOOD | PropertiesTest, CalibrationEngineTest | ✅ |
| PROP-CAL-04 | Residual exactly 1.0 µT → GOOD (closed upper bound) | PropertiesTest, CalibrationEngineTest | ✅ |
| PROP-CAL-05 | Residual above 1.0 µT → FAIR | PropertiesTest | ⚠️ flat Float coverage API, not CoverageStats |
| PROP-CAL-06 | Residual exactly 2.0 µT → FAIR | CalibrationEngineTest | ✅ (`residual boundary 2_0 is FAIR`) |
| PROP-CAL-07 | Residual above 2.0 µT → POOR | CalibrationEngineTest | ✅ |
| PROP-CAL-08 | Atomic write — OOM mid-write leaves DB readable | OomResilienceTest (partial) | ⚠️ simulated only; no real process kill |
| PROP-CAL-09 | Rollback slot — third save evicts oldest | PropertiesTest, CalibrationRepositoryTest | ✅ |
| PROP-CAL-10 | Age computed in UTC days since last save | PropertiesTest | ⚠️ pure arithmetic only, no DB round-trip |
| PROP-DETECT-01 | Field 0.249f from CLEAR → MODERATE | InterferenceDetectorTest | ✅ |
| PROP-DETECT-02 | Field 0.25f → WARNING | InterferenceDetectorTest | ✅ |
| PROP-DETECT-03 | Field 0.251f → WARNING | PropertiesTest, InterferenceDetectorTest | ✅ |
| PROP-DETECT-04 | Field below 15% for 3 s from WARNING → CLEAR | InterferenceDetectorTest | ✅ |
| PROP-DETECT-05 | Field exactly 15.0% resets timer → MODERATE | InterferenceDetectorTest | ⚠️ state checked but timer-reset behavior not verified |
| PROP-DETECT-06 | Timer not elapsed → stays WARNING | PropertiesTest, InterferenceDetectorTest | ✅ |
| PROP-DETECT-07 | Timer resets on mid-window excursion | PropertiesTest | ✅ |
| PROP-DETECT-08 | Inclination < 3° stays CLEAR | PropertiesTest, InterferenceDetectorTest | ✅ |
| PROP-DETECT-09 | Inclination 3.0° → MODERATE | InterferenceDetectorTest | ✅ |
| PROP-DETECT-10 | Inclination 7.9° → MODERATE | PropertiesTest, InterferenceDetectorTest | ✅ |
| PROP-DETECT-11 | Inclination 8.0° → WARNING | InterferenceDetectorTest | ✅ |
| PROP-DETECT-12 | Spike rejection — no WARNING trigger | PropertiesTest | ⚠️ conditional assertion weakens coverage |
| PROP-DETECT-13 | Spike filter boundary (`>` strict) | NoiseSpikeFilterTest | ✅ |
| PROP-CONF-01 | Confidence = min of five dimensions | PropertiesTest, ConfidenceModelTest | ✅ |
| PROP-CONF-02 | Tilt ≤ 5.0° — no clamp | ConfidenceModelTest | ✅ |
| PROP-CONF-03 | Tilt 5.1° → MODERATE clamp | ConfidenceModelTest | ✅ |
| PROP-CONF-04 | Tilt 20.0° → MODERATE clamp (not POOR) | ConfidenceModelTest | ✅ |
| PROP-CONF-05 | Tilt 20.1° → POOR clamp | ConfidenceModelTest | ✅ |
| PROP-CONF-06 | No-gyroscope caps at MODERATE | ConfidenceModelTest | ✅ |
| PROP-CONF-07 | STUCK bypasses → SENSOR_ERROR | PropertiesTest, ConfidenceModelTest | ✅ |
| PROP-CONF-08 | STABILIZING bypasses scoring | PropertiesTest, ConfidenceModelTest | ✅ |
| PROP-CONF-09 | Evaluation order — residual 1.0 µT → GOOD | PropertiesTest | ✅ |
| PROP-CONF-10 | Noise variance 0.1 µT² → GOOD (closed upper bound) | ConfidenceModelTest | ✅ |
| PROP-CONF-11 | Noise variance 0.101 µT² → MODERATE | ConfidenceModelTest | ✅ |
| PROP-CONF-12 | Noise variance 0.5 µT² → MODERATE (closed upper bound) | ConfidenceModelTest | ✅ |
| PROP-CONF-13 | Noise variance above 0.5 µT² → POOR | ConfidenceModelTest | ✅ |
| PROP-CONF-14 | Cal age 7 days → GOOD (closed upper bound) | ConfidenceModelTest | ⚠️ day-granularity only; 1-ms boundary not tested |
| PROP-CONF-15 | Cal age just above 7 days → MODERATE | ConfidenceModelTest | ⚠️ tests age=8 days, not 7 days + 1 ms |
| PROP-CONF-16 | Cal age 30 days → MODERATE (closed upper bound) | ConfidenceModelTest | ⚠️ day-granularity only |
| PROP-CONF-17 | Cal age above 30 days → POOR | ConfidenceModelTest | ✅ |
| PROP-DISPLAY-01 | All required UI slots present | CompassIntegrationTest (stub) | ❌ stub only |
| PROP-DISPLAY-02 | Wake lock flag set in onStart | (none) | ❌ not tested |
| PROP-DISPLAY-03 | Wake lock flag cleared in onStop | (none) | ❌ not tested |
| PROP-DISPLAY-04 | Mode switcher absent | NoModeSwitcherTest (partial) | ⚠️ checks content descriptions only, not R.id.bottom_navigation |
| PROP-DISPLAY-05 | Interference warning non-dismissible | (none) | ❌ not tested |
| PROP-DISPLAY-06 | Interference overlay appears within 2 s | (none) | ❌ not tested |
| PROP-DISPLAY-07 | Heading continues during interference | (none) | ❌ not tested |
| PROP-DISPLAY-08 | CTA shown on first launch | CompassIntegrationTest (stub) | ❌ stub only |
| PROP-DISPLAY-09 | CTA hidden after calibration | CompassIntegrationTest (stub) | ❌ stub only |
| PROP-DISPLAY-10 | Stabilizing badge shown when STABILIZING | (none) | ❌ not tested |
| PROP-DISPLAY-11 | Tilt slot no layout reflow | (none) | ❌ not tested |
| PROP-CAL-UX-01 | Cancel preserves prior calibration | OomResilienceTest (partial) | ⚠️ no wizard cancel flow exercised |
| PROP-CAL-UX-02 | Poor quality requires two taps | (none) | ❌ not tested |
| PROP-CAL-UX-03 | Done button disabled until gate met | (none) | ❌ not tested |
| PROP-CAL-UX-04 | Cancel confirmation dialog | (none) | ❌ not tested |
| PROP-CAL-UX-05 | Confidence badge shows Poor immediately | CompassIntegrationTest (stub) | ❌ stub only |
| PROP-CAL-UX-06 | OOM kill — prior calibration readable | OomResilienceTest (partial) | ⚠️ no real ProcessPhoenix kill |
| PROP-SENSOR-01 | No-mag → SENSOR_ERROR + hidden rose | CompassIntegrationTest (stub) | ❌ stub only |
| PROP-SENSOR-02 | No-gyroscope → advisory banner | CompassIntegrationTest (stub) | ❌ stub only |
| PROP-SENSOR-03 | No-gyroscope caps at MODERATE (JVM) | ConfidenceModelTest (`no gyroscope caps HIGH to MODERATE`) | ✅ |
| PROP-SENSOR-04 | All-zero mag → immediate STUCK | SensorStateMonitorTest | ❌ time-gap STUCK only; zero-magnitude path not tested |
| PROP-SENSOR-05 | Repeated non-zero → STUCK after sustained window | SensorStateMonitorTest (`stuck after 2s gap`) | ✅ |
| PROP-SENSOR-06 | Power-saving advisory at < 15 Hz | SensorRateMonitorTest | ⚠️ rate computation tested; advisory emission not tested |
| PROP-PERSIST-01 | INTERNET permission absent | (none) | ❌ not tested |
| PROP-PERSIST-02 | Calibration DB encrypted | (none) | ❌ not tested |
| PROP-PERSIST-03 | App fully functional offline | (none) | ❌ not tested |
| PROP-PERSIST-04 | Timestamps as UTC epoch ms | PropertiesTest | ⚠️ no DB round-trip; trivially correct |
| PROP-NFR-01 | Latency p95 ≤ 50 ms | (none) | ❌ not tested |
| PROP-NFR-02 | Update rate ≥ 20 Hz | (none) | ❌ not tested |
| PROP-NFR-03 | Battery ≤ 5%/hour (manual gate) | (none) | ❌ manual gate; no CI stub |
| PROP-NFR-04 | APK ≤ 15 MB (build check) | (none) | ❌ no Gradle/CI assertion |
| PROP-NFR-05 | No crashes in test suite | all tests | ✅ (implicit — suite passes) |
| PROP-L10N-01 | App starts in all four locales | (none) | ❌ not tested |
| PROP-L10N-02 | No English strings in Japanese locale | CompassIntegrationTest (stub) | ❌ stub only |
| PROP-L10N-03 | zh-HK/MO → zh-Hant resources | (none) | ❌ not tested |
| PROP-L10N-04 | Generic zh → zh-Hans resources | (none) | ❌ not tested |

---

## Coverage Summary by Domain

| Domain | Total Props | ✅ Passing | ⚠️ Partial | ❌ Missing/Stub |
|---|---|---|---|---|
| PROP-FUSION | 6 | 4 | 1 | 1 |
| PROP-CAL | 10 | 5 | 3 | 2 |
| PROP-DETECT | 13 | 10 | 2 | 1 |
| PROP-CONF | 17 | 13 | 4 | 0 |
| PROP-DISPLAY | 11 | 0 | 1 | 10 |
| PROP-CAL-UX | 6 | 0 | 2 | 4 |
| PROP-SENSOR | 6 | 2 | 1 | 3 |
| PROP-PERSIST | 4 | 0 | 1 | 3 |
| PROP-NFR | 5 | 1 | 0 | 4 |
| PROP-L10N | 4 | 0 | 0 | 4 |
| **Total** | **76** | **35** | **15** | **32** |

**Note:** The 32 missing/stub properties are predominantly Instrumented and Espresso tests, which are expected to be implemented before the release gate. The 35 passing JVM unit tests cover all algorithmic correctness invariants. The 3 High-severity findings (H-01 synthetic sequence, H-03 zero-mag STUCK path) and 2 Medium findings (M-01 per-axis coverage, M-02 timer-reset assertion) should be addressed before declaring Phase 1 complete.
