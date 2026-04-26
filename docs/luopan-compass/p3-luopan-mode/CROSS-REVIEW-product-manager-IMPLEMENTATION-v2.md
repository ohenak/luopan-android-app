# Cross-Review: product-manager — Implementation

**Reviewer:** product-manager
**Document reviewed:** Implementation — Phase 3 Luopan Display Mode (feat-luopan-p3-luopan-mode)
**Date:** 2026-04-25
**Iteration:** 2

---

## Summary of Changes Since Iteration 1

Two Medium findings (PM-F01, PM-F02) were targeted for resolution. Two Low findings (PM-F03, PM-F04) were acknowledged as engineering trade-offs. PM-F05 was acknowledged as low-impact cold-start cosmetic issue.

This review verifies the fixes and checks for regressions.

---

## Findings

| ID | Severity | Finding | Requirement ref |
|----|----------|---------|----------------|
| PM2-F01 | **Medium** | **Regression introduced by PM-F01 fix:** `setNorthType(type)` now calls `onNorthTypeChanged()`, which reads `_uiState.value.north_type` to determine `isMagneticNorth`. However, `_uiState.value.north_type` is only updated on the next sensor frame — it is **not** updated by `northTypeEngine.setNorthType(type)`. At the instant `onNorthTypeChanged()` fires, `_uiState.value.north_type` still holds the **old** north type. This means `isMagneticNorth` is evaluated against the previous north type, inverting the rederive direction. Example: user switches from True N → Mag N; `onNorthTypeChanged()` reads `north_type == TRUE` (stale) → `isMagneticNorth = false` → `rederive(..., isMagneticNorth = false)` computes display bearing as True North when it should compute as Magnetic North. The overlay shows the wrong display bearing immediately after a north-type switch while locked. AC-23 is still not reliably satisfied — it will produce the correct result only after the next sensor frame overwrites the stale state. | AC-23, Scenario J-2, FSPEC §4d, REQ §8 |
| PM2-F02 | **Medium** | **North type label mismatch — "Magnetic N" vs "Mag N":** `NorthTypeEngine.computeHeadingFields()` produces `"Magnetic N"` as the northLabel when magnetic north is active (and also as the fallback label when True North has no location). The REQ (§5.5 canonical readout format, Scenarios B, C, E, J) and FSPEC (Flow 3 field table, FSPEC §4.4 and §4a step 7) specify the north type label in the numerical readout and 坐向 overlay as `"Mag N"`. `LuopanState.northLabel` is documented in a code comment as `// "True N" or "Mag N"`, but the runtime value is `"Magnetic N"`. This means AC-03 (`午 (Wǔ) · … · 180.0° · True N · …`), AC-05, AC-22, AC-23, and all Scenario E overlay assertions will fail when magnetic north is active. The `"Magnetic N"` label is used unchanged by `LuopanStateMapper` and rendered directly in `tvNorthType` and in the overlay lines. | AC-03, AC-05, AC-22, AC-23, Scenarios B, C, E-2, J-1, J-2, REQ §5.5, FSPEC Flow 3 |

---

## Verification of Iteration 1 Fixes

### PM-F01 Fix Verification (AC-23 / Scenario J-2)

**Fix applied:** `setNorthType()` now calls `onNorthTypeChanged()`, and `toggleNorthType()` delegates through `setNorthType()`. The wiring between north-type change and lock rederivation is now present.

**Status: Partially fixed — new regression introduced.** The call chain is wired (`CompassViewModel.kt` lines 191–193), but `onNorthTypeChanged()` reads `_uiState.value.north_type` (line 307) to determine the new north type. Because `_uiState.value` is only updated by the sensor collection coroutine (`startSensorCollection`), the `north_type` field in `_uiState.value` is always one sensor frame behind. At the moment `onNorthTypeChanged()` is called synchronously from `setNorthType()`, the stale value is used. The correct fix is to pass the new `NorthType` directly as a parameter: `zuoXiangLock.rederive(declinationDeg, isMagneticNorth = type == NorthType.MAGNETIC)`, eliminating the dependency on stale `_uiState.value`.

**What must change:** `onNorthTypeChanged()` must receive or read the **new** north type (not the previous value from `_uiState`). The simplest fix is to refactor `onNorthTypeChanged()` to accept the new type as a parameter, or to read it from `northTypeEngine.northType.value` (which is already updated before `onNorthTypeChanged()` fires).

### PM-F02 Fix Verification (AC-09 / Scenario E-3)

**Fix applied:** `btnLockXiang.isEnabled = true` always; visual state communicated via `alpha`; `setOnClickListener` routes to `viewModel.clearXiang()`, `viewModel.lockXiang()`, or `Toast.makeText(...)` based on state.

**Status: Fixed.** The button is always enabled (`LuopanFragment.kt` line 277), click events are delivered in all confidence states, and the Toast fires when `!canLock && !isLockActive`. The `alpha` dimming visually signals non-lockable states. This fully satisfies the AC-09 affordance requirement. No regressions observed in the lock/clear routing logic.

---

## Re-assessment of Low Findings from Iteration 1

### PM-F03 (Low) — Ring 3 label truncation on dial

**Status: Acknowledged trade-off — retained as Low.** No change in implementation. The dial renders only the trigram symbol (first character of the compound "☲ 離 南" label) due to ring band width constraints. The REQ requirement for the full compound label on the dial (REQ-LUOPAN-01, Scenario A/B) is not satisfied on the dial face itself, but the numerical readout panel shows the full label correctly. This requires explicit PM sign-off before release.

### PM-F04 (Low) — Ring 6 label truncation on dial

**Status: Acknowledged trade-off — retained as Low.** No change in implementation. 分金 labels on the dial are truncated to 2 characters for legibility. Same sign-off requirement as PM-F03.

### PM-F05 (Low) — LuopanState.INITIAL brief "—" display

**Status: Partially improved — retained as Low.** `LuopanState.INITIAL` now correctly sets `confidence = OverallConfidence.POOR` (not SENSOR_ERROR), which is the correct cold-start state per ES-07. However, the character fields in `INITIAL` (`mountainChar = "—"`, `earthlyBranchChar = "—"`, `trigramSymbol = "—"`) are still set to the SENSOR_ERROR sentinel rather than the 0° bearing values that ES-07 specifies should be shown at cold start with POOR confidence. This is a brief cosmetic flash before the first sensor frame; the impact remains low.

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | PM2-F01: Should `onNorthTypeChanged()` be refactored to accept the new `NorthType` as a parameter (most explicit), or should it read from `northTypeEngine.northType.value` (which is already updated before the call)? Both approaches fix the stale-state bug; the parameter approach is more testable. |
| Q-02 | PM2-F02: Should the north type label for magnetic north in the numerical readout and overlay be `"Mag N"` (as the REQ specifies) or `"Magnetic N"` (as currently implemented)? This affects all scenario assertions. If the REQ should be updated to `"Magnetic N"`, a REQ/FSPEC revision is required before the assertions are finalized. |
| Q-03 (retained) | PM-F03: Is the single-character truncation of Ring 3 labels on the dial (showing only the trigram symbol, e.g., "☵") an approved product decision, or should the full compound label ("☵ 坎 北") be rendered — possibly at reduced font size or on two lines? This needs explicit PM sign-off before release given REQ-LUOPAN-01 specifies the full compound label. |
| Q-04 (retained) | PM-F04: Is the 2-character truncation of Ring 6 labels on the dial (showing e.g., "壬午" for "壬午分金") an approved product decision? The REQ notes that legibility at 8sp is required (REQ-NFR-LUOPAN-02) and pinch-to-zoom is the mitigation for small screens. The truncation should be explicitly documented as approved. |

---

## Positive Observations

- PM-F02 (Medium) is correctly and cleanly resolved: the "always-enabled button + alpha + Toast" pattern satisfies AC-09 without introducing any observable regressions in the lock/clear routing. The `isLockButtonEnabled()` helper correctly handles the PM-Q01 case (Clear 向 remains enabled during SENSOR_ERROR when lock is active).
- The `setNorthType()` → `onNorthTypeChanged()` wiring (PM-F01 intent) is structurally correct. The issue is localized to a single line (reading stale `_uiState.value.north_type`), making the fix straightforward.
- The `ZuoXiangLock.rederive()` implementation itself is correct: display bearings are adjusted by declination when magnetic north is active, and True North stored bearings and mountain labels are invariant. The bug is in the caller's `isMagneticNorth` argument, not in `rederive()`.
- `LuopanStateMapper` correctly reads `lockState.displayXiangBearing` and `lockState.displayZuoBearing` and propagates them to `LuopanState`. The V3-F01 fix (using display bearings for the overlay and tick mark) is confirmed intact.
- Session-only vs persisted settings contract (BR-09), sector lookup correctness (BR-01 through BR-04), 坐向 derivation (BR-06), 分金 gating (BR-07), and language default (BR-08) all remain correctly implemented — no regressions detected in those areas.
- The `LuopanState.INITIAL.confidence` field was corrected to `OverallConfidence.POOR` (from the SENSOR_ERROR-like sentinel used in the initial implementation), partially addressing PM-F05.

---

## What Must Change Before Ship

1. **PM2-F01 (Medium — blocking):** Fix `onNorthTypeChanged()` to use the **new** north type, not the stale `_uiState.value.north_type`. Simplest fix: change the call in `setNorthType()` to `zuoXiangLock.rederive(state.declination_deg, isMagneticNorth = type == NorthType.MAGNETIC)` directly, or refactor `onNorthTypeChanged(newType: NorthType)` to accept the type as a parameter.

2. **PM2-F02 (Medium — blocking):** Resolve the `"Magnetic N"` vs `"Mag N"` label discrepancy. Either update `NorthTypeEngine` to emit `"Mag N"` for magnetic mode (aligning with REQ), or revise the REQ/FSPEC to specify `"Magnetic N"`. All scenario assertions depend on the exact label string.

---

## Recommendation

**Need Attention**

Two new Medium findings require resolution before the feature can ship. The PM-F01 intent was correct but introduced a stale-state regression in `onNorthTypeChanged()`. PM-F02 (lock button always enabled) is confirmed fixed with no regressions. A new Medium finding (PM2-F02) on north type label mismatch is identified for the first time in this review.

Low findings (PM-F03, PM-F04, PM-F05) remain acknowledged trade-offs; PM sign-off required on ring label truncation decisions before release.
