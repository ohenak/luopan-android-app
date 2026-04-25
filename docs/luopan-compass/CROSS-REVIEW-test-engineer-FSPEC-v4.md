# Cross-Review: TE — FSPEC-luopan-p2-true-north-capture (Iteration 4 — Final Verification)

**Reviewer role:** Senior Test Engineer
**Document reviewed:** FSPEC-luopan-p2-true-north-capture.md v0.4-draft and REQ-luopan-p2-true-north-capture.md v0.4-draft
**Date:** 2026-04-24
**Previous recommendation:** Approved with Minor Issues (iteration 3)

---

## Scope

This is a targeted verification review. No new functional flows were introduced in v0.4-draft. The review scope is:

1. Verify V-01: §5.2 state diagram decision node correctly says `InterferenceState ∈ {MODERATE, WARNING}` (not `interference_flag?`)
2. Verify V-02: AT-E-10 exists with correct assertions (interference_flag=false when POOR + CLEAR)
3. Check V-03: AT-NFR-02 warm precondition clarified to use `StartupMode.COLD` with seeded warm cache
4. Check V-04: AT-B-03 and AT-F-01 reference the pinned test vector (lat=40.0°N, lon=−105.0°W, epoch=2025.5)
5. All 7 REQ §8 scenarios (A–G) still covered
6. Any new issues in v0.4-draft

---

## Verification Results

### V-01 — §5.2 Diagram Decision Node

**Status: Resolved**

The §5.2 diagram decision node now reads:

```
InterferenceState ∈ {MODERATE, WARNING}? ─── NO ──► [Name/Notes dialog — High or Moderate confidence AND no interference]
```

This correctly replaces the prior `interference_flag?` label. The decision node is now anchored to `InterferenceState` — the normative source of the flag — rather than the derived flag value.

Two annotations follow the diagram and complete the behavioral contract:

- **NO path annotation:** "The NO branch covers all cases where `InterferenceState == CLEAR`, regardless of `OverallConfidence`. This includes captures where `OverallConfidence == POOR` due to calibration age or other non-interference reasons. Such captures save with `interference_flag=false`."
- **YES path annotation:** Confirms that `OverallConfidence.POOR` alone (with `InterferenceState.CLEAR`) does NOT take the YES path for `interference_flag`, and cross-references BR-10 and AT-E-10.

**Residual observation (informational, not a defect):** The diagram decision node tests `InterferenceState ∈ {MODERATE, WARNING}` only. Per §2.5 step 3b, the pre-capture warning dialog fires when *either* `InterferenceState ∈ {MODERATE, WARNING}` OR `OverallConfidence = POOR`. The diagram therefore underspecifies the dialog trigger condition: a POOR-confidence, CLEAR-interference capture takes the NO branch in the diagram but still receives a warning dialog per §2.5 step 3b. The NO path annotation acknowledges this case, and AT-E-10 covers it. This is a documentation imprecision at the diagram level, not a normative contradiction — §2.5 step 3b is the authoritative definition. No further FSPEC revision is required; the TSPEC author MUST implement §2.5 step 3b as written, not the diagram decision node alone. This observation is noted for the TSPEC author, not as a finding requiring PM action.

V-01 is closed.

---

### V-02 — AT-E-10 (POOR confidence + CLEAR interference → interference_flag=false)

**Status: Resolved**

AT-E-10 is present in the AT-E table with all required elements:

- **Given:** `OverallConfidence = POOR` (calibration confidence dimension Poor; all interference metrics CLEAR — `field_deviation_pct < 15`, `inclination_deviation_deg < 3`); `InterferenceState = CLEAR`
- **When:** User taps capture button; pre-capture warning dialog appears; user taps "Save with warning"; enters name; taps Save
- **Then:** Four-part assertion:
  - (a) Pre-capture warning dialog appears with correct text
  - (b) User path through "Save with warning"
  - (c) `BearingRecord.interference_flag = false` — explicitly NOT true
  - (d) `BearingRecord.confidence = "POOR"`
- **Purpose statement:** "This verifies that `OverallConfidence.POOR` alone does NOT set `interference_flag=true` — only `InterferenceState ∈ {MODERATE, WARNING}` does."

The test is fully unambiguous. The precondition pins the exact metric thresholds (`field_deviation_pct < 15`, `inclination_deviation_deg < 3`), which is sufficient for `FakeMagneticFieldModel` injection in instrumented tests. The assertion at (c) directly contradicts the incorrect behavior that V-02 was raised to catch.

V-02 is closed.

---

### V-03 — AT-NFR-02 Warm Precondition (N-05 carry-forward)

**Status: Unresolved**

The NFR-02 row in AT-NFR-01 is unchanged from v0.3-draft:

> `NFR-02 | Warm process start (StartupMode.WARM); WMM coefficients and GPS cache already loaded | App resumes to first non-STABILIZING heading frame | Elapsed time ≤ 3 s | Macrobenchmark median over ≥5 iterations ≤ 3000 ms (StartupMode.WARM)`

The v0.4-draft revision history does not list V-03 among the addressed items. The ambiguity identified in iterations 2 and 3 remains: REQ §5.4 defines the warm-cache scenario as "WMM coefficients and cached GPS location already loaded," describing a disk-warm, process-cold condition, but the AT row uses `StartupMode.WARM` (process not killed between iterations). Under `StartupMode.WARM`, `Application.onCreate()` is not re-entered, making the measurement window in the AT description technically inapplicable to this startup mode.

This is a Low-severity finding. As instructed, it does not affect the overall recommendation and may be resolved in the TSPEC. The TSPEC author should implement option (a) from the V-03 recommendation: `StartupMode.COLD` with a pre-run setup block that seeds WMM coefficients and GPS cache to disk before measurement begins.

V-03 remains open — deferred to TSPEC.

---

### V-04 — AT-B-03 and AT-F-01 Missing TE-REQ-02 Vector Reference

**Status: Unresolved**

Both rows are unchanged from v0.3-draft:

- **AT-B-03:** "known NOAA reference declination for test coordinates (lat, lon) is R degrees" — coordinates not pinned to TE-REQ-02.
- **AT-F-01:** "differs by a known amount for the test location" — test location not named; TE-REQ-02 not referenced.

The v0.4-draft revision history confirms V-04 was not addressed. The TE-REQ-02 test vector (lat=40.0°N, lon=−105.0°W, alt=0m, epoch=2025.5; expected declination=+8.93°E ±0.1°; total field=52 300 nT ±200 nT; inclination=+66.0° ±0.5°) is defined in REQ §8 Scenario B and remains available as the canonical fixed vector. Without this pinning, an implementer reading the FSPEC AT rows in isolation may choose coordinates that minimize the WMM2025 vs. GeomagneticField difference, weakening AT-F-01.

This is a Low-severity finding. It does not affect the overall recommendation and may be resolved in the TSPEC. The TSPEC author must pin the TE-REQ-02 vector in the unit test fixtures for AT-B-03 and AT-F-01.

V-04 remains open — deferred to TSPEC.

---

## Scenario Coverage Summary (REQ §8, All 7 Scenarios)

| REQ Scenario | FSPEC Flow | Acceptance Tests | V-04 Status | Coverage |
|-------------|-----------|-----------------|-------------|---------|
| A — True north switching | FSPEC-TOGGLE §2.2, FSPEC-TNORTH §2.1 | AT-A-01 to A-04 | N/A | Complete |
| B — Offline declination with GPS | FSPEC-TNORTH §2.1, FSPEC-GPS §2.4 | AT-B-01 to B-03 | V-04 (test vector not pinned — Low) | Functionally complete; test vector pinning deferred to TSPEC |
| C — No GPS, cached location | FSPEC-GPS §2.4, FSPEC-TOGGLE §2.2 | AT-C-01 to C-03 | N/A | Complete |
| D — No GPS, no cache | FSPEC-TOGGLE §2.2 step 4c, FSPEC-GPS §2.4 | AT-D-01 to D-04 | N/A | Complete |
| E — Bearing capture | FSPEC-CAPTURE §2.5 | AT-E-01 to E-10 | N/A | Complete — AT-E-10 added in v0.4-draft |
| F — WMM interference baseline upgrade | FSPEC-DETECTUPGRADE §2.6 | AT-F-01 to F-04 | V-04 (test location not named — Low) | Functionally complete; test location pinning deferred to TSPEC |
| G — GPS permission denied | FSPEC-GPS §2.4, §4, §5.3 | AT-G-09 | N/A | Complete |

All 7 REQ §8 scenarios have acceptance test coverage. No scenario is uncovered.

---

## New Findings in v0.4-draft

No new defects or ambiguities were introduced by the v0.4-draft changes (SE V3-N-01 through V3-N-05). The four SE-addressed items are reviewed briefly:

- **SE V3-N-01 (`onResume`→`onStart`):** §2.4 step 1 correctly uses `onStart` with the rationale that `onResume` fires on every dialog dismissal. The lifecycle trigger is now consistent with Phase 1 sensor registration.
- **SE V3-N-02 (`altitude_m` type `Double?`):** §6.1 and REQ §5.3.1 are now aligned on `Double?`. The normative note clarifying NULL storage vs. 0 m WMM computation is correct.
- **SE V3-N-03 (`display_mode` nullability):** §6.1 now specifies `String?` nullable with `null` permitted, `"MODERN"` mandated for Phase 2, and reserved values for Phases 3 and 5. REQ §5.3.1 aligned.
- **SE V3-N-04 (cache age formula):** `floor(elapsed_ms / 86_400_000L)` is now standardized across §2.3 step 7 and AT-C-02. The integer-division formula eliminates DST boundary risk.
- **SE V3-N-05 (§1.3 TSPEC cross-reference):** §1.3 now cross-references TSPEC §9 (not §10.2) for the Room migration requirement. Internally consistent.

No new test findings arise from any of these changes. The changes are clean, scoped, and do not alter any flow, business rule, or acceptance test other than AT-E-10 (TE V-02) and the §5.2 diagram (TE V-01), both of which are verified above.

---

## Summary Table

| Finding | Iteration Raised | v0.4-draft Status | Blocks TSPEC? |
|---------|-----------------|-------------------|---------------|
| V-01 — §5.2 diagram `interference_flag?` branch label (Medium) | Iteration 3 | **Resolved** | — |
| V-02 — AT-E-10 missing (POOR + CLEAR → interference_flag=false) (Medium) | Iteration 3 | **Resolved** | — |
| V-03 — AT-NFR-02 warm precondition incompatible with REQ §5.4 (Low) | Iteration 2 (N-05), Iteration 3 (V-03) | **Unresolved** — deferred to TSPEC | No |
| V-04 — AT-B-03, AT-F-01 missing TE-REQ-02 test vector reference (Low) | Iteration 3 | **Unresolved** — deferred to TSPEC | No |

---

## Positive Observations

The v0.4-draft is a targeted, disciplined patch that addresses exactly the two Medium findings without introducing scope creep. Specific commendations:

- **AT-E-10 quality:** The four-part assertion in AT-E-10 is precise, mechanically verifiable, and contains an explicit purpose statement explaining *why* the test exists. This is the standard the other AT rows should aspire to.

- **§5.2 annotations:** The two prose annotations below the diagram are a pragmatic solution to the inherent limitation of ASCII state diagrams — they allow the diagram to remain visually simple while the full behavioral contract (including the POOR-confidence edge case) is documented in prose. Cross-referencing BR-10 and AT-E-10 from the annotation creates a three-way consistency chain: normative flow (§2.5 step 3b) → business rule (BR-10) → acceptance test (AT-E-10) → state diagram annotation (§5.2).

- **Revision history discipline:** The v0.4-draft revision history is an accurate, specific enumeration of exactly what changed. A reviewer can identify every changed element without diffing the documents. This is exemplary documentation hygiene.

- **Scenario G completeness (AT-G-09):** Confirmed still present and unmodified. The six-part assertion continues to provide the most complete coverage of any individual AT row in the document.

---

## Recommendation: Approved

The FSPEC-luopan-p2-true-north-capture v0.4-draft is **approved** for TSPEC authoring. The two Medium findings from iteration 3 (V-01 and V-02) are both fully resolved. All 7 REQ §8 scenarios have acceptance test coverage. No new issues were introduced.

The two remaining Low findings (V-03 and V-04) are deferred to the TSPEC author:

- **V-03 (TSPEC action):** Implement AT-NFR-02 as `StartupMode.COLD` with a pre-run setup block that seeds WMM coefficients and GPS cache to disk, reflecting the REQ §5.4 "warm cache" definition. Do not use `StartupMode.WARM` for the 3 s budget.
- **V-04 (TSPEC action):** Pin AT-B-03 to TE-REQ-02 (lat=40.0°N, lon=−105.0°W, alt=0m, epoch=2025.5; expected declination=+8.93°E ±0.1°). Pin AT-F-01 to the same vector and assert total field = 52 300 nT ±200 nT. These are additive test fixture additions; no FSPEC change is required.

No further FSPEC re-review is required before TSPEC authoring proceeds.
