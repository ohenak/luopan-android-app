# Cross-Review: Test Engineer — FSPEC-luopan-p3-luopan-mode

| Field | Value |
|-------|-------|
| Reviewer | Test Engineer |
| Document | FSPEC-luopan-p3-luopan-mode.md |
| Date | 2026-04-25 |
| Recommendation | Need Attention |

---

## Summary

The FSPEC is a significant quality uplift over the REQ. It resolves the highest-priority REQ findings (NF-01 data contradiction in canonical format, NF-02 placeholder in Scenario C, NF-03 missing Ring 2 table, NF-04 north-type display, NF-05 sector boundary rule) and raises a previously Medium REQ finding (zoom config-change survival) to AC-26. The business rules section is precise and individually unit-testable. The 10 business rules map cleanly to test-level logic.

However, four issues require attention before the TSPEC can be authored: one High finding (AC-02 references a non-existent business rule label, and the dial-rotation assertion combines two properties that require separate test cases), two Medium findings (AC-23 is ambiguous about a critical branch, and Flow 4d introduces new behavior not covered by any AC), and one Medium finding (the SENSOR_ERROR overlay freeze behavior in ES-01 has no AC). Three Low findings cover gaps that can be absorbed by the TSPEC author with reasonable effort.

---

## Findings

### High Priority

| ID | Severity | Finding | Section ref |
|----|----------|---------|-------------|
| F-01 | High | **AC-02 references a non-existent BR label and bundles two distinct testable properties into one criterion.** AC-02 is tagged `(BR-dialrotation, Scenario A)` — but no business rule is labelled `BR-dialrotation` anywhere in the FSPEC. The BR section defines BR-01 through BR-10; this tag resolves to nothing, making traceability links invalid. Beyond the broken tag, AC-02 combines two separate assertions: (a) dial counter-rotates by θ when device rotates by θ, and (b) the pointer remains fixed at screen top. These are independent properties (one is a rotation-math contract testable at the ViewModel level; the other is a rendering contract testable via UI instrumentation). Bundling them prevents assigning each to the correct test level and means a failure report cannot isolate which property broke. Both properties need a BR number and each should be a separate AC. | §6 AC-02 |
| F-02 | High | **Flow 4d introduces new lock re-derivation behavior that is not fully covered by any acceptance criterion.** Flow 4d (North Reference Switch While Locked, §2 Flow 4d) specifies that when the user switches north type while 坐向 is locked, `xiangBearing` is **updated to the current bearing in the new reference frame** (step 3). This is a stateful mutation: the lock no longer holds the original captured bearing — it holds the live bearing at the moment the north-type switch occurs. AC-23 tests that "the overlay's bearing value and 山 label update to reflect the magnetic-adjusted heading" but never asserts the specific input → output transformation: given a known declination offset, what exact bearing and 山 label should appear? Without a concrete numeric assertion (e.g., "locked at True N 45°, declination 5°E, switch to Mag N → overlay shows 40.0° Mag N, 山 = 艮"), the criterion cannot be implemented as an automated test. More critically, AC-23 does not cover the case where the device is **moving** when the north-type switch occurs — the bearing used to re-derive `xiangBearing` is undefined in that scenario (is it the instantaneous bearing at the moment of the toggle, or is it re-derived continuously?). The FSPEC must clarify and AC-23 must be made concrete. | §2 Flow 4d, §6 AC-23 |

---

### Medium Priority

| ID | Severity | Finding | Section ref |
|----|----------|---------|-------------|
| F-03 | Medium | **ES-01 (SENSOR_ERROR with active lock) introduces a frozen-overlay behavior with no acceptance criterion.** ES-01 specifies: "坐向 overlay (if active) remains visible but bearing values are frozen at last-locked values; badge shows 'Sensor error'". This is a distinct branch — confidence badge changes to "Sensor error" but the overlay bearing does NOT update (unlike the north-type switch case in Flow 4d where it does update). AC-24 tests the non-locked case: "山, 地支, 後天八卦, and bearing fields display '—'". But it does not test the locked case: overlay bearing is frozen, badge is "Sensor error", the live readout panel shows "—" while the overlay shows stale values. A test cannot verify this combination from the current ACs. The two display areas (readout panel vs. lock overlay) have contradictory update rules in SENSOR_ERROR and this must be asserted explicitly. | §5 ES-01, §6 AC-24 |
| F-04 | Medium | **No acceptance criterion covers the STABILIZING state for the dial rendering behavior.** ES-02 specifies that during STABILIZING, "the dial continues to rotate (heading is still being updated by gyroscope)" and "all sector fields continue to update normally." This is notably different from SENSOR_ERROR where the dial freezes. AC-24 covers SENSOR_ERROR. No AC covers STABILIZING in isolation, meaning the distinct STABILIZING behavior (dial continues, sectors update, but 分金 hidden and lock button disabled) is tested only indirectly through the confidence badge text appearing in AC-06's umbrella coverage of Moderate. An explicit AC for STABILIZING should assert: dial rotates, sectors update, confidence badge shows "Calibrating...", 分金 shows substitute text, lock button disabled. | §5 ES-02, §6 AC-06 |
| F-05 | Medium | **AC-23 does not specify what "crosses a 山 boundary" means concretely, leaving the conditional branch untestable.** The final clause of AC-23 states "if the magnetic-adjusted bearing crosses a 山 boundary, the corrected 山 label is shown." This is a conditional with no concrete test case that exercises the truthy branch. A test author cannot write a parameterized test for this without knowing: (a) a specific starting True-N bearing, (b) a specific declination value, (c) the resulting magnetic bearing, and (d) the expected 山 label before and after. Without a concrete triple (input, declination, expected output), this branch is dead code in the test suite. The criterion should include at least one worked example that crosses a boundary, e.g., "True N 45° with 7.6° west declination → Mag N 52.6° → 山 changes from 艮 [37.5°, 52.5°) to 寅 [52.5°, 67.5°)." | §6 AC-23 |
| F-06 | Medium | **Flow 3 does not define whether the bearing field shows "—" or the last valid bearing during SENSOR_ERROR when the dial is described as "frozen at the last valid bearing."** ES-01 states the dial renders at the last valid bearing but the bearing field in the numerical readout shows "—". These are inconsistent: the dial uses the last bearing, but the panel hides it. A test must assert both properties simultaneously but the relationship between them is described in two separate sections without a cross-reference. It is not clear from the FSPEC whether "—" in the bearing field means the underlying `bearingDeg` is cleared to null or whether it is retained in the ViewModel but suppressed in the View. This is observable: a test asserting `LuopanState.bearingDeg` in SENSOR_ERROR must know whether the field is null or stale. Flow 3's confidence table (§2 Flow 3) shows `Bearing field: "—"` for SENSOR_ERROR, but ES-01's dial description says "Renders with the last valid bearing". The data contract for `LuopanState.bearingDeg` during SENSOR_ERROR must be defined. | §2 Flow 3, §5 ES-01 |

---

### Low Priority

| ID | Severity | Finding | Section ref |
|----|----------|---------|-------------|
| F-07 | Low | **AC-01 (mode entry latency ≤ 300 ms) lacks a definition of "tap event" start time.** AC-01 states "the first complete dial frame is rendered within 300 ms of the tap." The start of the 300 ms window is ambiguous: is it the `MotionEvent.ACTION_DOWN`, the `ACTION_UP`, the `NavController.navigate()` call, or the tab-selected callback? On a budget device, the difference between ACTION_DOWN and ACTION_UP can be 50–100 ms. Flow 1 Step 1 says "receives the tab-tap event" without specifying which event type. Without a precise start point, two test implementations will measure different elapsed times and may disagree on pass/fail at the 300 ms boundary. The FSPEC should specify start = ACTION_UP (or equivalent NavController navigation trigger) and end = first `onDraw` completion. | §2 Flow 1, §6 AC-01 |
| F-08 | Low | **The "Show in my language" + "Show romanization" combination behavior is specified in Flow 7 but has no acceptance criterion.** Flow 7 explicitly defines the combined-on behavior: "English equivalents are shown with pinyin romanization below them where applicable." Neither AC-12 nor AC-13 tests the both-ON case. AC-13 tests romanization ON with zh-Hant labels; AC-12 tests English locale with both toggles OFF. A test should assert the combined state: e.g., Ring 4 十二地支 label shows "Rat" with "Zǐ" below it when both toggles are ON. Without this, an implementation that correctly handles each toggle independently but fails to combine them correctly will pass all ACs. | §2 Flow 7, §6 AC-12, AC-13 |
| F-09 | Low | **Unresolved REQ finding NF-08 (battery NFR has no measurement procedure) remains open; no AC added in FSPEC.** REQ-NFR-LUOPAN-03 (≤ 7% battery/hr) carries no test procedure, no device class specification, and no defined measurement environment. The FSPEC did not add an acceptance criterion or designate this as a manual test. This finding was Low in the REQ review; it remains Low here. The TSPEC author will need to either (a) add a measurement procedure for the battery NFR or (b) explicitly defer it, to prevent it from silently falling through to release untested. | §11 REQ-NFR-LUOPAN-03 (inherited from REQ) |
| F-10 | Low | **AC-14 (ring visibility — hide and verify) tests only Ring 4 but the criterion is written as if it generalizes.** "Rings 1, 2, 3, 5, and 6 remain visible" is assertable for the Ring 4 case, but the AC does not test hiding Ring 1 (天池 needle), Ring 6 (分金), or Ring 2 (先天八卦). ES-05 specifies that hiding all 6 rings is valid; no AC verifies this edge case. Hiding Ring 6 specifically has an interesting nuance: Flow 5's decision table states "the 分金 ring disappears from the dial but the 分金 field in the numerical readout panel remains visible (when High confidence)." This is a distinct behavior worth an explicit assertion — a dial-hidden-but-panel-visible state that is easy to get wrong. | §5 ES-05, §6 AC-14 |

---

## Positive Observations

- All five High and three Medium findings from the REQ v2 review (NF-01 through NF-05, NF-06, NF-07, NF-08) are resolved in the FSPEC. NF-01 (wrong 分金 label in the canonical example) is corrected; NF-02 (placeholder in Scenario C) is replaced with concrete values in AC-05; NF-03 (missing Ring 2 table) is resolved by §4.2 which inlines all 8 rows; NF-04 (north-type switch behavior) is addressed in Scenario J and AC-22; NF-05 (sector boundary rule) is codified as BR-01 through BR-04; NF-06 (zoom config-change survival) is now AC-26.
- The 10 business rules (BR-01 through BR-10) are individually unit-testable. Each rule has a formal definition with enough specificity to write a parameterized test: BR-01's `[start°, end°)` semantics, BR-02's Ring 4 algorithm, BR-04's Ring 6 wrap-around with exact boundary assertions, and BR-06's modular arithmetic formula are all at the right level of precision.
- The `LuopanState` data class definition (§10 of REQ, mirrored in FSPEC Flow 3) gives the ViewModel contract precisely — field names, types, nullable semantics — so ViewModel unit tests can be written directly from the FSPEC without guessing property names.
- Flow 4 sub-flows (4a locking, 4b unlocking, 4c mode switches, 4d north reference) are correctly decomposed and each sub-flow maps to a distinct test scenario. The button-state table (§2 Flow 4e) covers all five confidence states exhaustively.
- AC-16 through AC-20 provide parameterized boundary tables directly convertible to `@ParameterizedTest` data providers. The Ring 6 wrap-around table (AC-20) correctly covers both sides of the 358°/4° boundary.
- BR-07 resolves the 分金/overlay ambiguity that was previously Medium: the rule applies to both the readout panel and the 坐向 overlay, with the overlay column explicit in BR-05.

---

## Recommendation

**Need Attention**

Two High findings must be resolved before the TSPEC can be authored:

1. **F-01** — Assign a BR number to the dial counter-rotation contract (e.g., BR-11), split AC-02 into two separate criteria (one for the mathematical mapping, one for the pointer-fixed rendering assertion), and remove the broken `BR-dialrotation` tag.

2. **F-02** — Clarify in Flow 4d whether `xiangBearing` is re-derived once at the moment of the north-type toggle or continuously thereafter. Add a concrete numeric worked example to AC-23 (e.g., True N 45°, declination 5°E → Mag N 40°, 山 = 艮) so the criterion is automatable. Add a separate assertion for the "bearing crosses a 山 boundary" branch (see also F-05).

Four Medium findings should also be resolved before TSPEC authoring:

3. **F-03** — Add an acceptance criterion for the SENSOR_ERROR + active-lock state: readout panel shows "—", lock overlay shows frozen bearing values, confidence badge shows "Sensor error" in both areas.

4. **F-04** — Add an acceptance criterion for STABILIZING: dial continues rotating, all sector fields update, 分金 field shows substitute text, lock button is disabled, badge shows "Calibrating...".

5. **F-05** — Provide a concrete boundary-crossing example in AC-23 (specific True-N bearing, specific declination, expected Mag-N bearing, expected 山 label before and after switch).

6. **F-06** — Define the `LuopanState.bearingDeg` contract during SENSOR_ERROR: is it null (cleared) or retained-but-suppressed? This determines whether the ViewModel unit test asserts null or a stale value, and whether the rendering logic does a null-check or a confidence-gate.

Low findings F-07 through F-10 may be deferred to the TSPEC author, with the exception of F-09 (battery NFR), which should be explicitly marked as a manual test with a defined measurement procedure before the PLAN is written.
