# Cross-Review: product-manager — Implementation

**Reviewer:** product-manager
**Document reviewed:** Implementation — Phase 3 Luopan Display Mode (feat-luopan-p3-luopan-mode)
**Date:** 2026-04-25
**Iteration:** 3

---

## Summary of Changes Since Iteration 2

Two Medium findings from iteration 2 (PM2-F01 and PM2-F02) were targeted for resolution in commit `4a3c2d0`. This review verifies the fixes and checks for regressions.

**PM2-F01 fix:** `CompassViewModel.setNorthType(type)` now calls `onNorthTypeChanged(type)`, passing the new `NorthType` directly. `onNorthTypeChanged(type)` derives `isMagneticNorth = type == NorthType.MAGNETIC` from the parameter, not the stale `_uiState.value.north_type`.

**PM2-F02 fix:** `NorthTypeEngine.computeHeadingFields()` now emits `"Mag N"` (not `"Magnetic N"`) in both the magnetic-mode path and the True-N-no-location fallback path. `HeadingFields` KDoc, `CompassUiState.INITIAL`, `CompassViewModelTest`, and `CompassUiStateTest` were all updated to `"Mag N"`.

---

## Findings

| ID | Severity | Finding | Requirement ref |
|----|----------|---------|----------------|
| PM3-F01 | **Medium** | **PM2-F02 fix is incomplete: `NorthTypeToggleTest` Espresso test will fail.** The PM2-F02 fix correctly updated `NorthTypeEngine`, `CompassUiState.INITIAL`, unit tests, and `HeadingFields` KDoc to use `"Mag N"`. However, it did not update `NorthTypeToggleTest` (an instrumented Espresso test in `androidTest/`). Line 86 of that test asserts `withText(R.string.magnetic_north)` on `R.id.northLabel`. Because `R.string.magnetic_north` is still `"Magnetic N"` in `app/src/main/res/values/strings.xml`, the assertion will fail at runtime — `northLabel.text` is set to `state.north_label` = `"Mag N"`, not `"Magnetic N"`. This means the fix that was intended to close PM2-F02 introduced a broken instrumented test. The fix is: either update `R.string.magnetic_north` to `"Mag N"` (which also updates the toggle button label from `"Magnetic N"` to `"Mag N"`), or update the Espresso test to use `withText("Mag N")` directly. Note: updating the string resource would also change the toggle button label on screen, which is a UX change that should be confirmed. | AC-03, AC-05, AC-22, AC-23, Scenario J, REQ §5.5 |
| PM3-F02 | Low | **`LuopanState.INITIAL.northLabel` is `"True N"` but `NorthTypeEngine` defaults to `NorthType.MAGNETIC`.** `CompassUiState.INITIAL` correctly has `north_label = "Mag N"` and `north_type = NorthType.MAGNETIC`. However, the embedded `LuopanState.INITIAL` has `northLabel = "True N"` (a pre-existing inconsistency carried from PM-F05). The comment in `LuopanState.INITIAL` states "northLabel defaults to True N per the app default north reference at startup", which contradicts the engine default. Since `LuopanStateMapper.map()` overwrites `northLabel` from `compassState.north_label` on the first sensor frame, this is a sub-frame cosmetic flash only. However, the inconsistency between `CompassUiState.INITIAL.north_label` (`"Mag N"`) and `LuopanState.INITIAL.northLabel` (`"True N"`) could cause confusion in unit tests that snapshot initial state. This was already tracked as PM-F05 (Low) — it has not regressed but has also not been corrected. | ES-07, Scenario J, REQ §5.5 |

---

## Verification of PM2-F01 Fix — AC-23 / Scenario J-2

**Fix applied (commit `4a3c2d0`):** `CompassViewModel.setNorthType(type)` now passes `type` directly to `onNorthTypeChanged(type)` (line 195). `onNorthTypeChanged(type)` reads `isMagneticNorth = type == NorthType.MAGNETIC` from the new parameter (line 314), not from the stale `_uiState.value.north_type`.

**Status: Fixed.** The root cause identified in PM2 is resolved. The call chain is:

```
setNorthType(type)
  → northTypeEngine.setNorthType(type)   // updates northType StateFlow
  → onNorthTypeChanged(type)             // type param = new type (not stale _uiState.value)
       → zuoXiangLock.rederive(declinationDeg, isMagneticNorth = type == MAGNETIC)
       → recomputeLuopanState()
```

At the moment `onNorthTypeChanged()` fires, `isMagneticNorth` is derived from the just-passed `type` argument, not from the sensor pipeline's stale `_uiState.value.north_type`. The stale-state inversion bug identified in iteration 2 is eliminated.

`ZuoXiangLock.rederive()` logic itself remains correct and unchanged: display bearings are adjusted by subtracting `declinationDeg` when `isMagneticNorth = true`, and set equal to the stored True North bearing when `false`. Mountain labels (`xiangMountain`, `zuoMountain`) are correctly invariant.

**Unit test coverage (`CompassViewModelSessionStateTest`):** The new `TE2-F04` test (`setNorthType_while_locked_updates_displayXiangBearing_via_rederive`) exercises the full chain via the `SessionState` helper and asserts that `displayXiangBearing` updates correctly from 45f (True N) to 48.5f (Mag N, declination −3.5°) after `setNorthType(MAGNETIC, −3.5f)`. This covers the AC-23 / Scenario J-2 assertion path. **AC-23 is now satisfied.**

---

## Verification of PM2-F02 Fix — North Type Label "Mag N"

**Fix applied (commit `4a3c2d0`):** `NorthTypeEngine.computeHeadingFields()` emits `"Mag N"` in the magnetic-mode path (line 173) and the True-N-no-location fallback path. `HeadingFields` KDoc updated. `CompassUiState.INITIAL.north_label` changed to `"Mag N"`. `CompassViewModelTest` and `CompassUiStateTest` updated to assert `"Mag N"`.

**Status: Partially fixed — new regression in instrumented test.** The production code path is correct: `NorthTypeEngine` emits `"Mag N"`, which flows through `CompassUiState.north_label` → `LuopanState.northLabel` → `tvNorthType.text` and overlay lines. The unit test suite (`CompassViewModelTest`, `LuopanStateMapperTest`, `LuopanFragmentLogicTest`) all correctly assert `"Mag N"`. However, `NorthTypeToggleTest` (an instrumented Espresso test in `androidTest/`) still asserts `withText(R.string.magnetic_north)` = `"Magnetic N"` on `R.id.northLabel` (line 86), and `R.string.magnetic_north` in `strings.xml` was not updated. This test will fail on device.

**What must change:** Either:

1. Update `R.string.magnetic_north` from `"Magnetic N"` to `"Mag N"` in `app/src/main/res/values/strings.xml` — this also shortens the toggle button label and the toggle button accessibility description. This requires PM sign-off on the button label change.
2. Update `NorthTypeToggleTest` to assert `withText("Mag N")` directly — preserves `"Magnetic N"` as the toggle button label and string resource, but makes the Espresso assertion consistent with the runtime label.

---

## Re-assessment of Retained Low Findings from Iteration 2

### PM-F03 (Low) — Ring 3 label truncation on dial

**Status: Unchanged — retained as Low.** `drawRing3Labels()` still uses `label.character.take(1)`, showing only the trigram symbol (e.g., `"☵"`) instead of the full compound label `"☵ 坎 北"`. REQ-LUOPAN-01 requires `"trigram symbol + 卦名 + direction name"` on the dial. The numerical readout panel shows the full label correctly. PM sign-off required before release.

### PM-F04 (Low) — Ring 6 label truncation on dial

**Status: Unchanged — retained as Low.** `drawRing6Labels()` still uses `label.character.take(2)`, showing e.g., `"壬午"` instead of `"壬午分金"`. The numerical readout shows the full 4-character label. PM sign-off required before release.

### PM-F05 (Low) — LuopanState.INITIAL inconsistencies

**Status: Partially improved but not fully resolved — retained as Low.** The `confidence = OverallConfidence.POOR` correction (from iteration 2) is intact. However, `LuopanState.INITIAL.northLabel` remains `"True N"` while `CompassUiState.INITIAL.north_label` is `"Mag N"` and `NorthTypeEngine` defaults to `NorthType.MAGNETIC`. The inconsistency is cosmetic (sub-frame flash) and is now documented as PM3-F02 (Low).

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | PM3-F01: Should `R.string.magnetic_north` be updated from `"Magnetic N"` to `"Mag N"` (which also updates the toggle button label), or should `NorthTypeToggleTest` be corrected to assert `"Mag N"` while keeping the button label as `"Magnetic N"`? The choice is a product decision about the toggle button label. |
| Q-02 (retained) | PM-F03: Is the single-character truncation of Ring 3 labels on the dial (showing only the trigram symbol, e.g., `"☵"`) an approved product decision, or should the full compound label `"☵ 坎 北"` be rendered (possibly at reduced font size or on two lines)? Requires explicit PM sign-off before release. |
| Q-03 (retained) | PM-F04: Is the 2-character truncation of Ring 6 labels on the dial (showing e.g., `"壬午"` for `"壬午分金"`) an approved product decision? The REQ specifies legibility at 8sp with pinch-to-zoom as the mitigation. The truncation should be documented as approved before release. |

---

## Positive Observations

- **PM2-F01 is cleanly resolved.** The stale-state bug in `onNorthTypeChanged()` is eliminated. The parameter-pass approach is the more explicit and testable fix. The `SessionState.setNorthType()` helper and `TE2-F04` test correctly cover the ViewModel wiring.
- **PM2-F02 production code is fully correct.** All production code paths (`NorthTypeEngine`, `CompassUiState.INITIAL`, `LuopanState.northLabel`, `LuopanStateMapper`, `LuopanFragment`) consistently use `"Mag N"`. Unit tests (`CompassViewModelTest`, `LuopanStateMapperTest`, `LuopanFragmentLogicTest`) are updated and consistent.
- **AC-23 is now satisfied end-to-end.** The `onNorthTypeChanged(type)` wiring, `ZuoXiangLock.rederive()` correctness, `LuopanStateMapper` display-bearing propagation, and `LuopanFragment` overlay rendering are all correct and consistent.
- All sector lookups (Rings 2–6), 坐向 derivation, 分金 gating, session-only vs persisted state contract, ring visibility bottom sheet, mode-switcher architecture, and lock state persistence across mode switches remain correctly implemented — no regressions in any of these areas.
- The `isLockButtonEnabled()` PM-Q01 fix (always-enabled button + alpha + Toast) remains intact and correctly handles all five confidence levels including the edge case of `SENSOR_ERROR` while locked.
- `ZuoXiangLock.rederive()` correctly leaves stored True North bearings and mountain labels invariant — only `displayXiangBearing` and `displayZuoBearing` change. This ensures AC-23 (Scenario J-2 re-derivation) does not corrupt the True North lock data.

---

## What Must Change Before Ship

1. **PM3-F01 (Medium — blocking):** Fix `NorthTypeToggleTest` broken by the PM2-F02 label rename. Either update `R.string.magnetic_north` to `"Mag N"` in `strings.xml` (also affects toggle button label — requires PM sign-off), or change line 86 of `NorthTypeToggleTest` to assert `withText("Mag N")` directly.

2. **PM-F03 / PM-F04 (Low — require PM sign-off):** Ring label truncation on the dial for Ring 3 and Ring 6 must be explicitly approved by PM before release. These are engineering trade-offs for ring band width constraints and have not been formally approved in the REQ or FSPEC.

---

## Recommendation

**Need Attention**

One Medium finding (PM3-F01) requires resolution before the feature can ship: the `NorthTypeToggleTest` instrumented test will fail because `R.string.magnetic_north` = `"Magnetic N"` was not updated to match the new `"Mag N"` label emitted by `NorthTypeEngine`. The resolution requires a product decision about the toggle button label (Q-01). Low findings (PM3-F02, PM-F03, PM-F04) require PM sign-off but do not block merge for testing.
