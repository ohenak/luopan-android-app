# Cross-Review: Test Engineer — FSPEC-luopan-p3-luopan-mode (v2)

| Field | Value |
|-------|-------|
| Reviewer | Test Engineer |
| Document | FSPEC-luopan-p3-luopan-mode.md v1.1 |
| Date | 2026-04-25 |
| Iteration | 2 |
| Recommendation | Approved with Minor Issues |

---

## Prior Findings Resolution

### High Priority Findings

| ID | Status | Resolution Summary |
|----|--------|--------------------|
| F-01 | **Resolved** | BR-11 added in §3 with formal definition (`dialRotationDeg = -bearingDeg (mod 360°)`) and a lookup table of examples. AC-02 split into AC-02a (dial rotation math, references BR-11) and AC-02b (pointer fixed, references BR-11). The broken `BR-dialrotation` tag is gone. |
| F-02 | **Resolved** | Flow 4d completely redesigned: `xiangBearing` is now stored as True North always (§4a step 3); north-type switch only changes display values, never the stored bearing. This eliminates the ambiguity about continuous re-derivation. AC-23 updated with concrete declination worked example (45° True N, −3.5° decl → 48.5° Mag N display). The "crosses a 山 boundary" branch is moot because 山 labels are now invariant to north-type switches by architectural choice. |

### Medium Priority Findings

| ID | Status | Resolution Summary |
|----|--------|--------------------|
| F-03 | **Resolved** | AC-28 added: SENSOR_ERROR while 坐向 locked — overlay frozen with last-valid True North values, readout panel shows "—", badge "Sensor error", button shows "Clear 向". ES-01 table updated accordingly. |
| F-04 | **Partially Resolved** | AC-29 added and covers three of the five required properties: lock button disabled ✓, badge "Calibrating..." amber ✓, 分金 substitute text ✓. The two distinguishing STABILIZING properties — dial continues rotating while sectors continue updating (which contrast with SENSOR_ERROR where the dial freezes) — are not asserted in AC-29. A test written solely from AC-29 cannot verify the dial behaviour during STABILIZING. See new finding NF-01. |
| F-05 | **Resolved (by architecture change)** | The "crosses a 山 boundary" branch no longer exists. In v1.1 the 山 labels are derived from the stored True North bearing and are invariant to north-type switches. The conditional branch that F-05 found untestable has been eliminated. |
| F-06 | **Resolved** | §4.1 defines `LuopanState` as a full Kotlin data class with `bearingDeg: Float` (non-nullable). The spec explicitly states: "LuopanState.bearingDeg retains the last valid bearing value during SENSOR_ERROR — it is not set to null or zero. The View is responsible for suppressing display." This is sufficient for ViewModel unit test authors to write an assertion on a non-null stale value. |

### Low Priority Findings

| ID | Status | Resolution Summary |
|----|--------|--------------------|
| F-07 | **Not addressed** | AC-01 still does not specify whether the 300 ms timer starts at `MotionEvent.ACTION_DOWN` or `ACTION_UP`. Carried forward as Low; TSPEC author must resolve before writing the performance test. |
| F-08 | **Not addressed** | No acceptance criterion for the both-ON toggle combination (English equivalents with pinyin romanization). Still Low; testable behaviour is described in Flow 7. |
| F-09 | **Not addressed** | Battery NFR measurement procedure remains absent. Still Low; acceptable to defer to TSPEC. |
| F-10 | **Not addressed** | AC-14 still tests only Ring 4 visibility. The Ring-6-hidden-but-panel-visible distinction (noted in F-10) has no explicit AC. Still Low. Note: v1.1 adds a new decision-table row (Ring 5 hidden → tick mark hidden) with no corresponding AC — see NF-02. |

---

## New Findings

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| NF-01 | Medium | **AC-29 omits the two behaviours that distinguish STABILIZING from SENSOR_ERROR, making the criterion insufficient to prevent regression.** The critical distinction in ES-02 is that during STABILIZING the dial continues rotating and all sector fields continue to update normally — unlike SENSOR_ERROR where the dial is frozen and sector fields show "—". AC-29 asserts only lock button state, badge text, and 分金 field. A test suite that passes AC-29 would also pass if the implementation accidentally froze the dial during STABILIZING (same button, badge, and 分金 state; different dial behaviour). AC-29 must add: dial rotates to reflect live heading updates; Ring 3, 4, and 5 sector fields in the readout panel continue to show the current live sector (not "—"). | §5 ES-02, §6 AC-29 |
| NF-02 | Low | **Flow 5 added a new decision point (Ring 5 hidden → gold tick mark hidden) with no corresponding acceptance criterion.** The v1.1 decision table in Flow 5 now includes: "User hides Ring 5 (二十四山) → Ring 5 disappears from the dial; the gold tick mark (if 坐向 is locked) is also hidden because the tick mark is drawn on Ring 5." This is a distinct observable behaviour — the lock is still active (overlay remains visible) but its visual indicator on the dial disappears. No AC covers this state. Without an AC, an implementation that fails to hide the tick mark when Ring 5 is toggled off will not be caught by the test suite. | §2 Flow 5 (decision table), §5 ES-05 |
| NF-03 | Low | **`LuopanState` does not include `declinationDeg`, yet the View is required to perform a declination conversion at render time.** §4.1 states "For display, the View converts to the current north reference using the current declination value." However, the `LuopanState` data class (§4.1) contains no `declinationDeg` field. The View layer has no access to the declination value through `LuopanState` alone. This is not a testing concern per se, but it makes the View conversion logic untestable in isolation: a unit test for the View's overlay rendering cannot inject a declination value through `LuopanState`, so it must either test via the ViewModel (integration test) or the View must receive declination through a separate channel not specified in the FSPEC. The TSPEC author will need to decide where declination is sourced in the View, and this decision should be made explicit in the FSPEC data contract. | §4.1 `LuopanState`, §2 Flow 4d step 3 |
| NF-04 | Low | **The "Clear 向" button state during SENSOR_ERROR + active lock is ambiguous for testing.** AC-28 states the lock button "shows 'Clear 向' (lock remains active but frozen)." ES-01 table states "Displays 'Clear 向'; lock remains active but frozen; tooltip 'Cannot lock — heading is unreliable' if user taps." BR-05 and §4e table state that during SENSOR_ERROR the button is "Disabled (greyed out)" with tooltip "Cannot lock — heading is unreliable." A test author needs to know: (a) can the user tap "Clear 向" to actually clear the lock during SENSOR_ERROR? (b) if the button is disabled, does it show "Clear 向" or "Lock 向"? The FSPEC implies the lock is preserved (it cannot be cleared during SENSOR_ERROR) but this is not explicitly stated. If the user cannot clear the lock during SENSOR_ERROR, that is a distinct user-observable behaviour requiring an AC; if they can, the disabled state makes no sense. | §5 ES-01, §6 AC-28, §3 BR-05 |

---

## Positive Observations

- The `LuopanState` data class definition in §4.1 is a substantial improvement that directly enables ViewModel unit tests without guessing field types or nullability. `bearingDeg: Float` (non-nullable), `fenJinLabel: String?` (nullable), and the explicit True North storage convention for `xiangBearing` / `zuoBearing` are all at the right level of precision for test authoring.
- The architectural decision to store `xiangBearing` as True North (§4a step 3) and make 山 labels invariant to north-type switches is a significant testability improvement over v1.0. The single-truth-source design eliminates the conditional branch (F-05) and makes AC-23 a straightforward parameterized test with a fixed input/output pair.
- BR-11 is precise and includes a four-row lookup table mapping `bearingDeg` to `dialRotationDeg`. This table is directly convertible to a `@ParameterizedTest` without further interpretation.
- AC-28 (SENSOR_ERROR + lock) correctly identifies the two distinct display areas (readout panel vs. lock overlay) and specifies contradictory but correct behaviour for each, which was the core risk in F-03.
- The Flow 4d worked example uses a non-round declination (−3.5°) and a non-boundary True North bearing (45°), which is the right design for a concrete test case — it avoids coincidentally passing with a wrong implementation.
- ES-07 (cold-start sensor init) now correctly distinguishes cold-start confidence state (`POOR`, not `STABILIZING`) with a clear explanation of when each state occurs. This eliminates a latent test confusion about which badge to assert at first launch.
- §1.4 (navigation architecture contract) is a clean addition that constrains the navigation without prescribing implementation. The fragment names, ViewModel sharing contract, and back-stack rule give the TSPEC author sufficient information to write navigation integration tests.

---

## Recommendation

**Approved with Minor Issues**

One Medium finding remains open:

1. **NF-01** — AC-29 must be extended to assert that the dial continues rotating and sector fields (Ring 3, 4, 5) continue updating during STABILIZING. These two properties are what distinguish STABILIZING from SENSOR_ERROR and are the primary regression risk in the confidence-state rendering logic.

Three Low findings (NF-02, NF-03, NF-04) and three carried-forward Low findings (F-07, F-08, F-10) may be resolved by the TSPEC author without requiring another FSPEC iteration. Specifically:

- **NF-02** (Ring 5 hidden → tick mark hidden) should be added as an AC or absorbed into a decision-table test in the TSPEC.
- **NF-03** (missing `declinationDeg` in `LuopanState`) should be resolved at TSPEC authoring time by specifying how the View obtains the declination value.
- **NF-04** (ambiguous "Clear 向" state during SENSOR_ERROR) should be clarified in a TSPEC comment or by a one-line FSPEC addition stating whether the user can or cannot clear the lock during SENSOR_ERROR.
- **F-07** (AC-01 timer start point) must be resolved in the TSPEC before a performance test can be written.
- **F-08** (both-ON toggle combination) should be added as a TSPEC test case.
- **F-10** (Ring 6 hide / tick mark on Ring 5) can be captured as additional TSPEC parameterized cases covering the Flow 5 decision table exhaustively.

The overall quality of v1.1 is high. The data contract additions (§4.1 `LuopanState`), the True North storage invariant for the lock, the BR-11 dial rotation math, and the two new ACs (AC-28, AC-29) collectively address the structural gaps identified in iteration 1. The TSPEC can be authored from v1.1 with the understanding that NF-01 requires AC-29 expansion and that the Low findings above are deferred to TSPEC.
