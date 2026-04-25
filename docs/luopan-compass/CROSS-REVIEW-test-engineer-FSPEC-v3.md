# Cross-Review: TE — FSPEC-luopan-p2-true-north-capture (Iteration 3)

**Reviewer role:** Senior Test Engineer
**Document reviewed:** FSPEC-luopan-p2-true-north-capture.md v0.3-draft and REQ-luopan-p2-true-north-capture.md v0.3-draft
**Date:** 2026-04-24
**Previous recommendation:** Approved with Minor Issues (iteration 2)

---

## Resolution Verification

### N-01 (High) — Scenario G GPS denial: AT-G-09 added

**Status: Resolved**

AT-G-09 has been added to the AT-G table. The row maps precisely to REQ §8 Scenario G pass/fail criteria:
- (a) No SecurityException or crash
- (b) Manual coordinate entry dialog appears on True North toggle tap
- (c) `BearingRecord.lat = null`, `BearingRecord.lon = null`, `BearingRecord.alt_m = null`
- (d) GPS toggle absent from capture dialog (hidden, not disabled)
- (e) Toast confirms save
- (f) Informational message shown

The precondition correctly specifies both `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` denied with `shouldShowRequestPermissionRationale() = false`. The assertion is fully automated with Espresso. N-01 is closed.

---

### N-02 (Medium) — `interference_flag` set from `InterferenceState` only, not from `OverallConfidence.POOR` alone

**Status: Resolved in normative text; one residual diagram defect noted — see V-01 below**

The corrected definition appears consistently in:
- §2.5 step 3b, clarifying paragraph: "`interference_flag` in the saved record is set from interference metrics only (see §6.1 and BR-10 below), NOT from `OverallConfidence`."
- BR-10: "`OverallConfidence.POOR` does NOT set `interference_flag=true` unless interference is also detected."
- §6.1 `interference_flag` row: "NOT set by `OverallConfidence.POOR` alone."
- §6.1 notes section: "Set from `InterferenceState` at capture time — `MODERATE` or `WARNING` maps to `1`; `CLEAR` maps to `0`."
- REQ §5.3.1 `interference_flag` row (v0.3-draft): identical semantics confirmed.

The AT table does not contain the suggested AT-E-10 (Poor confidence / CLEAR interference → `interference_flag=false`). This is a gap. However, the normative correction in §2.5, BR-10, and §6.1 is unambiguous, and engineering now has a clear correct definition. The missing AT-E-10 is re-raised below as V-02.

The §5.2 state diagram retains a related defect — see V-01.

---

### N-03 (Medium) — AT-PERSIST-01 raw-bytes encryption check

**Status: Resolved**

AT-PERSIST-01 is present in the new AT-PERSIST section:
- Given: a BearingRecord has been saved.
- When: `bearing_records.db` opened as raw bytes via `targetContext.getDatabasePath()`.
- Then: first 16 bytes do NOT match the SQLite magic string `53 51 4C 69 74 65 20 66 6F 72 6D 61 74 20 33 00`.

The assertion is a byte-array check, not a dependency-presence check. This mirrors Phase 1 PROP-PERSIST-02 exactly. REQ §5.3 REQ-CAPTURE-04 also now contains an equivalent verifiability criterion, which means the test is anchored in both the REQ and the FSPEC. N-03 is closed.

---

### N-04 (Medium) — AT-PERSIST-02 force-stop resilience

**Status: Resolved**

AT-PERSIST-02 is present in the AT-PERSIST section:
- Given: bearing capture INSERT is in-flight.
- When: process killed via `adb shell am force-stop`.
- Then: on relaunch, `bearing_records` table row count is pre-kill count or pre-kill count + 1; a partial record with any required field null is a test FAILURE.

The assertion explicitly enumerates required fields (`id`, `name`, `bearing_deg`, `captured_at`). The note that Room's transactional guarantees enforce this is accurate; the test remains necessary to verify that the application code does not attempt partial multi-step writes outside a transaction. N-04 is closed.

---

### N-05 (Low) — AT-NFR-02 warm precondition ambiguity

**Status: Partially Resolved — see V-03 below**

The AT-NFR-01 table (NFR-02 row) still reads: `StartupMode.WARM`; WMM coefficients and GPS cache already loaded; target ≤3 s.

The v0.3-draft revision history does not list N-05 among the addressed items. No change was made to the NFR-02 row. The ambiguity identified in iteration 2 — whether `StartupMode.WARM` measures a genuine in-process resume (Activity.onResume path, measurement from Application.onCreate is inapplicable) versus a disk-warm cold restart — remains unresolved. REQ §5.4 defines "warm cache" as "WMM coefficients and cached GPS location already loaded," which is a disk-warm, process-cold scenario, but the AT row uses `StartupMode.WARM` (which implies a living process). This is re-raised as V-03.

---

## Scenario Coverage Summary (REQ §8, All 7 Scenarios)

| REQ Scenario | FSPEC Flow | Acceptance Tests | Coverage |
|-------------|-----------|-----------------|---------|
| A — True north switching | FSPEC-TOGGLE §2.2, FSPEC-TNORTH §2.1 | AT-A (A-01 to A-04) | Complete |
| B — Offline declination with GPS | FSPEC-TNORTH §2.1, FSPEC-GPS §2.4 | AT-B (B-01 to B-03) + static test vector | Complete |
| C — No GPS, cached location | FSPEC-GPS §2.4, FSPEC-TOGGLE §2.2 | AT-C (C-01 to C-03) | Complete |
| D — No GPS, no cache | FSPEC-TOGGLE §2.2 step 4c, FSPEC-GPS §2.4 | AT-D (D-01 to D-04) | Complete |
| E — Bearing capture | FSPEC-CAPTURE §2.5 | AT-E (E-01 to E-09) | Mostly complete — AT-E-10 missing (see V-02) |
| F — WMM interference baseline upgrade | FSPEC-DETECTUPGRADE §2.6 | AT-F (F-01 to F-04) | Complete |
| G — GPS permission denied | FSPEC-GPS §2.4, §4, §5.3 | AT-G-09 | Complete |

All 7 REQ §8 scenarios now have at least one dedicated acceptance test row. The remaining gap in Scenario E coverage (AT-E-10, Poor confidence / CLEAR interference) is a single missing assertion that does not prevent TSPEC authoring.

---

## Acceptance Test Ambiguity Assessment

Each AT section was reviewed for fixed inputs, expected outputs, and measurement methods.

**AT-A:** All four rows are unambiguous. A-01 uses Espresso `IdlingResource` with explicit ±50 ms tolerance on the reference device class. Fixed inputs: declination D, pre-tap heading. Pass threshold: ≤250 ms wall-clock. No ambiguity.

**AT-B:** B-03 references "known NOAA reference declination for test coordinates (lat, lon) is R degrees" without pinning (lat, lon). However, REQ §8 Scenario B (TE-REQ-02) provides the fixed test vector: lat=40.0°N, lon=−105.0°W, alt=0m, epoch=2025.5, expected declination=+8.93°E ±0.1°. The FSPEC AT-B-03 row should reference this vector explicitly (it does not). This is a minor cross-reference gap — see V-04.

**AT-C:** C-02 uses `FakeClock` with the exact millisecond formula `15 × 86 400 000 ms`. The format contract (`R.string.location_cache_age_label` with `%d`) is present. No ambiguity; no DST risk.

**AT-D:** D-01 through D-04 all have fixed inputs and deterministic assertions. D-04 uses lat=95.0 (specific out-of-range value). No ambiguity.

**AT-E:** E-01 through E-09 have unambiguous assertions. E-04 precondition bundles Poor confidence with active interference (field deviation = 30%) — this is an acceptable positive test. The missing E-10 (Poor confidence, CLEAR interference) is a gap, not an ambiguity.

**AT-F:** F-01 asserts the WMM2025 result "differs by a known amount for the test location" from GeomagneticField. The test location is not pinned in the FSPEC. However, the REQ §8 TE-REQ-02 vector (lat=40.0°N, lon=−105.0°W, epoch=2025.5) provides the fixed vector that can distinguish the two models. Same cross-reference gap as AT-B-03 — see V-04.

**AT-G:** G-01 through G-09 are all unambiguous. G-09 precondition is deterministic; all assertions are directly measurable in an Espresso instrumented test.

**AT-PERSIST:** Both rows are unambiguous. PERSIST-01 uses a byte-array comparison against a fixed hex pattern. PERSIST-02 defines pass/fail in terms of row count and required-field nullity.

**AT-NFR-01:** NFR-01 (`StartupMode.COLD`, ≤5000 ms) is unambiguous. NFR-02 (`StartupMode.WARM`, ≤3000 ms) is ambiguous — see V-03.

---

## New Findings in v0.3-draft

| ID | Severity | Section | Finding | Recommendation |
|----|----------|---------|---------|----------------|
| V-01 | **Medium** | §5.2 state diagram | **Bearing Capture Dialog state diagram branch label is inconsistent with the corrected §2.5 flow.** The diagram in §5.2 uses `interference_flag?` as the decision node that routes to either the Interference Warning Dialog or the Name/Notes Dialog. This is incorrect in two ways. First, the actual branching condition (per the corrected §2.5 step 3b) is `InterferenceState ∈ {MODERATE, WARNING}` OR `OverallConfidence = POOR` — not `interference_flag`. Second, the "NO" path is labeled "confidence High or Moderate," which excludes the case where confidence is POOR but `InterferenceState = CLEAR`. In this case, the warning dialog appears (correct per step 3b) but the record saves with `interference_flag=false` (correct per BR-10). The diagram does not represent this case. An engineer following the diagram alone would implement the branching logic incorrectly — routing POOR confidence without interference straight to the save path without a warning dialog. | Update the §5.2 diagram decision node from `interference_flag?` to `InterferenceState MODERATE/WARNING OR OverallConfidence POOR?`. Label the "NO" path as "High or Moderate confidence AND no interference" and the "YES" path as "Interference warning dialog." On the YES path, add a note: "Record saves with `interference_flag = true` only if `InterferenceState` is `MODERATE`/`WARNING`; saves with `interference_flag = false` if only `OverallConfidence` is `POOR`." This keeps the diagram consistent with §2.5 and BR-10. |
| V-02 | **Medium** | §7 AT-E | **AT-E-10 for Poor confidence / CLEAR interference is still absent.** N-02 recommended adding AT-E-10: "Given: `OverallConfidence=POOR` (due to calibration quality); `InterferenceState=CLEAR` (field dev <15%, inclination dev <3°). When: user taps capture, acknowledges warning, saves. Then: `BearingRecord.interference_flag = false`." This AT row was not added in v0.3-draft. The normative text (§2.5, BR-10, §6.1) is now correct, but without a dedicated test covering this exact combination, an engineering regression could silently reintroduce the incorrect behavior (setting `interference_flag=true` on any POOR-confidence capture). The diagram defect in V-01 makes this risk higher, not lower, because the diagram still implies a simpler branching model than the actual required logic. | Add AT-E-10: "Given: `OverallConfidence=POOR` (e.g., calibration confidence dimension is Poor; all other interference metrics CLEAR — `field_deviation_pct < 15`, `inclination_deviation_deg < 3`); `InterferenceState=CLEAR`. When: user taps capture button. Then: (a) pre-capture warning dialog appears with text `"Interference detected or confidence is Poor. Bearing will be saved with a warning flag. Proceed?"`; (b) user taps 'Save with warning'; (c) `BearingRecord.interference_flag = false` in the saved record." This is the normative case that distinguishes the two triggers of the warning dialog and verifies they produce different `interference_flag` values. |
| V-03 | **Low** | §7 AT-NFR-01, NFR-02 row | **AT-NFR-02 warm precondition ambiguity is unresolved (N-05 carry-forward).** The NFR-02 row continues to specify `StartupMode.WARM` for the 3 s target. REQ §5.4 defines the warm-cache scenario as "WMM coefficients and cached GPS location already loaded," which describes a disk-warm, process-cold scenario. `StartupMode.WARM` in Macrobenchmark means the process was not killed between iterations, making the `Application.onCreate()` measurement window inapplicable. If the intent is a disk-warm cold restart, the correct annotation is `StartupMode.COLD` with a pre-run setup iteration that seeds the WMM coefficients and GPS cache to disk. If the intent is a true in-process resume, the measurement window should start from `Activity.onResume()`, and the 3 s budget should reflect the time to stabilize the heading from a backgrounded state, not from a process start. As written, the AT is executable but may produce inconsistent median values depending on whether the Macrobenchmark harness counts the first iteration (which seeds the cache) as a valid measurement. | Clarify NFR-02 as one of: (a) `StartupMode.COLD` with a one-time setup block (`@Before` or `pressHome()` + wait) that pre-seeds WMM coefficients and GPS cache to disk before measurement begins, reflecting the REQ §5.4 "warm cache" definition; or (b) change the measurement window to `Activity.onResume()` → first non-STABILIZING heading frame for a genuine in-process resume scenario. Option (a) is the more faithful translation of REQ §5.4. |
| V-04 | **Low** | §7 AT-B-03, AT-F-01 | **AT-B-03 and AT-F-01 do not reference the fixed WMM2025 test vector defined in REQ §8 (TE-REQ-02).** AT-B-03 refers to "known NOAA reference declination for test coordinates (lat, lon) is R degrees" without pinning the coordinates. AT-F-01 requires the WMM2025 result to differ from GeomagneticField "by a known amount for the test location" without naming the location. The REQ §8 TE-REQ-02 vector (lat=40.0°N, lon=−105.0°W, alt=0m, epoch=2025.5; expected declination=+8.93°E ±0.1°; total field=52 300 nT ±200 nT; inclination=+66.0° ±0.5°) was defined specifically to make these tests non-network-dependent and deterministic. Without explicit pinning in the FSPEC AT rows, an implementer reading only the FSPEC (not the REQ) might choose arbitrary coordinates that happen to minimize the WMM2025 vs. GeomagneticField difference, weakening AT-F-01. | In AT-B-03, replace "known NOAA reference declination for test coordinates (lat, lon) is R degrees" with "location=lat 40.0°N, lon −105.0°W, alt 0m, epoch 2025.5; expected declination = +8.93°E; tolerance ±0.1° (TE-REQ-02)." In AT-F-01, add "Use the TE-REQ-02 test vector (lat=40.0°N, lon=−105.0°W, alt=0m, epoch=2025.5); assert `expected_field_ut` matches WMM2025 = 52 300 nT ±200 nT, not GeomagneticField (which differs by a predictable amount at this location)." These are additive clarifications with no structural impact on the FSPEC. |

---

## Positive Observations

- The three-section structure of the v0.3-draft AT appendix (AT-A through AT-G functional, AT-PERSIST persistence integrity, AT-NFR performance) is a clear improvement over v0.2-draft. Grouping AT-PERSIST separately from AT-E avoids the risk of persistence concerns being buried in scenario-specific rows.

- The `interference_flag` correction in §2.5 step 3b, BR-10, and §6.1 is thorough. The normative note in §2.5 step 3b — "Note: `interference_flag` is set from interference metrics only, not from `OverallConfidence`" — directly addresses the engineering confusion risk identified in N-02. The same correction is now mirrored in REQ §5.3.1, which means both documents are consistent on this point.

- AT-G-09 is the strongest new acceptance test row. The six-part assertion (a–f) covers crash safety, dialog routing, all three nullable location fields, GPS toggle hiddenness, toast content, and the informational message — exactly the pass/fail criteria from REQ §8 Scenario G. No test writer is left to interpret what "no crash" or "null location fields" means.

- The REQ v0.3-draft addition of Risk P2-R4 (Room DB migration) and §10.2 (Migration(1,2) requirements including the prohibition on `fallbackToDestructiveMigration`) is a valuable constraint that closes a risk not previously documented. This risk is well-scoped: the prohibition applies to production builds and is enforced by lint or CI, which is testable.

- The `calibration_version` field correction (String type; WMM model identifier semantics; explicit anti-conflation note with `CalibrationRecord.calibration_schema_version`) is consistently applied across FSPEC §6.1, FSPEC BR-02, and REQ §5.3.1. The cross-reference explicitly calls out the prior type conflict in the revision history, which aids future reviewers in understanding why this normative note exists.

---

## Summary Table

| Finding from v2 | v3 Status |
|----------------|-----------|
| N-01 (High) — AT-G-09 for GPS denial | **Resolved** |
| N-02 (Medium) — `interference_flag` from `InterferenceState` only | **Resolved in normative text**; diagram defect and missing AT remain (V-01, V-02) |
| N-03 (Medium) — AT-PERSIST-01 encryption check | **Resolved** |
| N-04 (Medium) — AT-PERSIST-02 force-stop resilience | **Resolved** |
| N-05 (Low) — AT-NFR-02 warm precondition | **Unresolved** (V-03) |

| New Finding | Severity | Blocks TSPEC? |
|------------|----------|--------------|
| V-01 — §5.2 diagram `interference_flag?` branch label | Medium | No |
| V-02 — AT-E-10 missing (POOR confidence / CLEAR interference) | Medium | No |
| V-03 — AT-NFR-02 warm precondition (N-05 carry-forward) | Low | No |
| V-04 — AT-B-03, AT-F-01 missing TE-REQ-02 vector reference | Low | No |

---

## Recommendation: Approved with Minor Issues

The v0.3-draft resolves all three prior Medium findings (N-02, N-03, N-04) and the one prior High finding (N-01). All 7 REQ §8 scenarios now have acceptance test coverage. The FSPEC is ready to proceed to TSPEC authoring.

The four new findings (V-01 through V-04) do not block TSPEC authoring. V-01 and V-02 should be resolved before the PROPERTIES document is finalized, as the diagram defect (V-01) risks incorrect implementation of the capture branching logic and the missing AT-E-10 (V-02) leaves the most subtle `interference_flag` case uncovered. V-03 and V-04 are low-severity clarifications that can be addressed during TSPEC authoring without requiring a FSPEC re-review.

The PM may address V-01 and V-02 as a targeted patch to §5.2 and the AT-E table respectively, without reopening any functional flows.
