# Cross-Review: test-engineer — Implementation

**Reviewer:** test-engineer
**Document reviewed:** `feat-luopan-p3-luopan-mode` — full test suite against `PROPERTIES-luopan-p3-luopan-mode.md` (v1.1, 76 properties)
**Date:** 2026-04-25
**Iteration:** 3

---

## Scope

This is the third review iteration. Iteration 2 raised 10 findings (2 High, 2 Medium carried from TE2-F03/F04, 6 Low). The following fixes were declared in commits `4a3c2d0` and merge `62e1685`:

- **TE2-F03** (Medium): `noSessionOnlyKeysInSharedPreferences` lock assertion broadened to `.contains("lock")` (excluding `"wake_lock_enabled"`), `.contains("xiang")`, `.contains("zuo")`
- **TE2-F04** (Medium): `SessionState.setNorthType(type, declinationDeg)` helper added; new test `setNorthType_while_locked_updates_displayXiangBearing_via_rederive` added to `CompassViewModelSessionStateTest`
- **TE2-F01/F02** (High): Acknowledged as tracked debt — not addressed in this iteration
- **TE2-F05 through TE2-F10** (Low): Carried from iteration 2 unchanged

Additionally, the `fix(p3)` merge commit includes **PM2-F02** (label rename from `"Magnetic N"` to `"Mag N"`) applied to production sources (`NorthTypeEngine.kt`, `CompassUiState.kt`, `HeadingFields.kt`, `CompassViewModel.kt`) and test files (`CompassViewModelTest.kt`, `CompassUiStateTest.kt`).

---

## Fix Verification

### TE2-F03 — Lock key assertion broadened (RESOLVED)

The `noSessionOnlyKeysInSharedPreferences` test at `SettingsRepositoryTest.kt` lines 100–108 now reads:

```kotlin
assertFalse("No lock-related session-state key must be stored in SharedPreferences",
    allKeys.any { key ->
        (key.contains("lock") && key != "wake_lock_enabled") ||
        key.contains("xiang") ||
        key.contains("zuo")
    })
```

This correctly catches all three families of lock-related key variants (`lock*`, `xiang*`, `zuo*`) while explicitly excluding the legitimate settings key `"wake_lock_enabled"`. The fix matches the exact specification from TE2-F03. **Finding closed.**

---

### TE2-F04 — ViewModel wiring test (PARTIALLY RESOLVED — residual gap)

The new test `setNorthType_while_locked_updates_displayXiangBearing_via_rederive` in `CompassViewModelSessionStateTest.kt` (lines 442–464) calls `SessionState.setNorthType(NorthType.MAGNETIC, -3.5f)`, which in turn calls `zuoXiangLock.rederive(declinationDeg, isMagneticNorth = (type == NorthType.MAGNETIC))`.

**What this test covers:**
- The `SessionState.setNorthType` helper is a thin in-process wrapper that mirrors the structure of `CompassViewModel.setNorthType` — it calls `rederive()` using the new `type` parameter rather than stale UI state. The test correctly exercises the wiring layer above the bare domain object: it goes through a `setNorthType`-shaped call (not `ZuoXiangLock.rederive()` directly) and asserts `displayXiangBearing == 48.5f` and `xiangBearing == 45.0f`.

**What this test does NOT cover:**
- The actual `CompassViewModel.setNorthType(type: NorthType)` method at line 193 of `CompassViewModel.kt`. That method calls `northTypeEngine.setNorthType(type)` and then `onNorthTypeChanged(type)`. The `SessionState` helper has no `northTypeEngine` dependency and does not call `onNorthTypeChanged()`. If a regression removed the `onNorthTypeChanged(type)` call from `CompassViewModel.setNorthType()`, no test would fail because no test calls the real `CompassViewModel.setNorthType()` and then inspects `zuoXiangLock.lockState.displayXiangBearing`.

**Assessment:** The fix closes approximately 60% of the TE2-F04 gap. The wiring is now tested one layer above the domain object (via the `SessionState` abstraction), which catches regressions within that layer. The final gap — exercising `CompassViewModel.setNorthType()` end-to-end — remains open. However, given that `CompassViewModel` is an `AndroidViewModel` requiring an `Application` context and a running sensor pipeline, this level of test requires either a ViewModel test helper with a fake sensor layer or an instrumented test. **Finding PARTIALLY CLOSED — remaining gap is accepted as tracked debt (equivalent to TE2-F01/F02 classification).**

---

### PM2-F02 — Label rename from `"Magnetic N"` to `"Mag N"` (NO REGRESSION)

The production sources now consistently emit `"Mag N"` (verified in `NorthTypeEngine.kt` line 173, `CompassUiState.kt` line 63, `CompassViewModel.kt` line 544, `HeadingFields.kt` line 12). The test files updated as part of this fix (`CompassViewModelTest.kt`, `CompassUiStateTest.kt`) now assert `"Mag N"`.

All assertions in the Luopan-specific test files that reference the north label use `"Mag N"` (e.g., `LuopanFragmentLogicTest.kt` lines 323, 330, 539, 542; `LuopanStateMapperTest.kt` line 386; `LuopanFragmentTest.kt` lines 221, 232, 403, 444, 453). No test file was found asserting the obsolete string `"Magnetic N"` as an expected value in a direct equality or text-match assertion. The rename is consistent across production and test. **No regression introduced.**

One observation: `NorthTypeToggleTest.kt` and `NoGpsDialogEspressoTest.kt` in the instrumented test suite use `"Magnetic N"` as a label description in comments and in Espresso `withText(...)` matchers that target the toggle button UI label (not the `northLabel` field). These reference a different UI element (the `rbMagneticN` radio button text from layout resources) and are not affected by the `northLabel` rename. **No issue.**

---

## Findings

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| TE3-F01 | High | **PROP-07-065A behavioral assertion remains a structural smoke test.** `ac23_northSwitch_overlayDisplaysConvertedBearing` in `LuopanFragmentTest.kt` (lines 427–454) still asserts only that the overlay is `GONE` at cold start and that the three view IDs resolve. The TODO comment for injecting a `LockState` with `displayXiangBearing=48.5f` and asserting overlay text remains unimplemented. No instrumented test injects a `LockState` with updated `displayXiangBearing` and verifies the overlay text update. Carried from TE2-F01 as acknowledged tracked debt. | PROP-02-026D, PROP-07-065A |
| TE3-F02 | High | **PG-07 instrumented localization tests (PROP-07-058 through PROP-07-064) remain entirely absent.** No test in `LuopanFragmentTest.kt` verifies that an English system locale does not cause English ring labels on the rendered dial, that the "Show romanization" toggle displays pinyin on-device, that "Show in my language" renders English equivalents uniformly across dial and readout panel, or that trigram symbols are retained in all modes. `LuopanStateMapperTest.mapper_locale_independence` covers the pure-logic equivalent at the unit level but the Integration/E2E gap remains. Carried from TE2-F02 as acknowledged tracked debt. | PROP-07-058–064 |
| TE3-F03 | Medium | **TE2-F04 residual gap: `CompassViewModel.setNorthType()` wiring is not exercised end-to-end.** The new `setNorthType_while_locked_updates_displayXiangBearing_via_rederive` test exercises the `SessionState` abstraction (a thin in-process wrapper), not the actual `CompassViewModel.setNorthType(type)` call chain. A regression that removes or misorders the `onNorthTypeChanged(type)` call at `CompassViewModel.kt:195` would not cause any existing test to fail. To close this fully, a test should call `viewModel.setNorthType(NorthType.MAGNETIC)` (or `viewModel.onNorthTypeChanged(NorthType.MAGNETIC)`) on a real or fake `CompassViewModel` after `zuoXiangLock.lock(45f)` and assert `zuoXiangLock.lockState!!.displayXiangBearing != 45f`. This is a narrower residual than the original TE2-F04 — the wiring layer is now structurally modelled — but the gap is real. | PROP-07-065A, AC-23, TSPEC §8.2 |
| TE3-F04 | Low | **F-06 (PG-08 performance properties) remains uncovered.** No FrameMetrics, fps, or battery historian instrumented tests exist. Status unchanged from iteration 2. | PROP-08-065, PROP-08-066, PROP-08-068 |
| TE3-F05 | Low | **F-08 (`ZuoXiangLock.clear()` field nullity) remains reference-only.** `clear after lock sets lockState to null` asserts `assertNull(lock.lockState)` rather than individual field values. Status unchanged from iteration 2. | PROP-02-022 |
| TE3-F06 | Low | **F-09 (back-stack uniqueness) remains untested.** `ModeSwitcherTest` has no test for PROP-06-054 (repeated Luopan tab taps do not stack multiple `LuopanFragment` instances). Status unchanged from iteration 2. | PROP-06-054 |
| TE3-F07 | Low | **F-10 (`LuopanFragmentTest` confidence tests are structural smoke tests).** `lock_button_disabled_at_poor_confidence`, `lock_button_enabled_at_high_confidence`, and `lock_button_enabled_when_lock_active_allowsClearing` each reduce to `check(matches(isDisplayed()))`. Only `ac29_stabilizing_lockButton_disabled_badge_shows_calibrating` makes a real assertion (`isNotEnabled()`). Status unchanged from iteration 2. | PROP-09-069, PROP-09-070, PROP-09-072 |
| TE3-F08 | Low | **F-11 (PROP-04-045A pointer-sector alignment) is tested via domain calls, not the rendered view.** Three tests in `LuopanViewTest` call `SectorLookup.ring5()` and `RingLabelProvider.ring5Label()` directly without invoking `LuopanView.onDraw()`. Status unchanged from iteration 2. | PROP-04-045A |
| TE3-F09 | Low | **F-12 (`ac22_northTypeSwitch_updatesReadoutImmediately` defers core assertion).** Test asserts only that `tvNorthType` is displayed, with TODO for the toggle action and text assertion. Status unchanged from iteration 2. | AC-22, PROP-03-031 |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | (Carried from iteration 2 Q-03) For PROP-07-065A, is downsizing from E2E to Integration (Robolectric + `MutableStateFlow` injection) accepted by the team? This would close TE3-F01 without requiring a real emulator for the behavioral assertion. |
| Q-02 | For TE3-F03, would it be acceptable to add `viewModel.onNorthTypeChanged(NorthType.MAGNETIC)` as a public or `@VisibleForTesting` entry point in `CompassViewModel`, allowing a unit-level test to exercise the full wiring without instantiating the sensor pipeline? |

---

## Positive Observations

- **TE2-F03 fix is correct and complete.** The three-clause substring check for `lock`, `xiang`, and `zuo` keys (with the `wake_lock_enabled` exclusion) is the right approach and matches the specification from the TE2-F03 finding exactly. The implementation is also more robust than a list of exact keys — it catches any future key variant in these families.

- **TE2-F04 wiring model is a meaningful improvement over the iteration-2 state.** The `SessionState.setNorthType(type, declinationDeg)` helper accurately mirrors the structure of the real `CompassViewModel.setNorthType()` and provides a reusable abstraction that future tests can extend. The worked example (45° True N + declination −3.5° → 48.5° display) matches PROP-02-026B exactly.

- **PM2-F02 label rename (`"Magnetic N"` → `"Mag N"`) is applied consistently.** All production sources, all Luopan-specific test assertions, and all updated non-Luopan test files use `"Mag N"`. The existing instrumented tests (`NorthTypeToggleTest`, `NoGpsDialogEspressoTest`) that reference `"Magnetic N"` as UI button text are correctly untouched — they test a different UI element whose string resource was not renamed.

- **The `setNorthType_while_locked_updates_displayXiangBearing_via_rederive` test is well-structured and correctly placed.** The test name and the inline comment clearly distinguish it from the earlier `rederive_called_when_northType_switches` test, making the layered intent (domain test vs. wiring-layer test) explicit. Both tests are needed and neither is redundant.

- **Overall test suite stability across three iterations is strong.** No regression was introduced in the PG-01 through PG-03 unit tests, the `LuopanFragmentLogicTest` pure-JVM suite, or the `ModeSwitcherTest` instrumented suite across any of the fix commits. The six Low findings carried from iteration 2 have not deteriorated.

---

## Re-Assessment: High Findings as Tracked Debt

Both High findings (TE3-F01, TE3-F02) require a running Android emulator for full resolution:

- **TE3-F01** (PROP-07-065A): The behavioral assertion requires either a ViewModel seam that accepts a pre-built `LuopanState` (bypassing the sensor pipeline) or a full Robolectric Fragment test. Robolectric Fragment rendering is not currently set up in this project (the project uses Espresso instrumented tests for fragment-level UI). A Robolectric approach would require adding `FragmentScenario` + `ShadowLooper` infrastructure, which is non-trivial but feasible without an emulator. This remains the recommended path if the team accepts Robolectric for this property.

- **TE3-F02** (PROP-07-058–064): Locale override tests using `Locale.setDefault(Locale.ENGLISH)` inside an `ActivityScenario`-scoped rule can be run on an emulator without any hardware. The unit test `LuopanStateMapperTest.mapper_locale_independence` already proves the mapper is locale-independent at the JVM level. The remaining gap is the rendered view — that the `Context.getString()` calls for ring labels do not accidentally pull from Android resources in a locale-sensitive way. This is verifiable with a Robolectric `ActivityScenario` or a single Espresso test with a `LocaleTestRule`.

Both findings are correctly classified as tracked debt. Neither blocks merge on its own if the team has accepted this classification.

---

## Recommendation

**Need Attention**

Two High and one Medium finding are present. The Medium finding (TE3-F03) is a residual from TE2-F04 after a partial fix.

**Status of iteration-2 required fixes:**

| Finding | Iteration-2 requirement | Iteration-3 status |
|---------|------------------------|-------------------|
| TE2-F03 | Broaden lock key substring check | CLOSED |
| TE2-F04 | Exercise real ViewModel wiring for `setNorthType → rederive` | PARTIALLY CLOSED — residual gap at `CompassViewModel` level (TE3-F03) |

**Required before merge:**

There are no new blocking findings introduced in this iteration. The two High findings (TE3-F01, TE3-F02) and the Medium finding (TE3-F03) are all continuations of previously acknowledged tracked debt. If the team accepts the tracked debt classification for all three, the branch is clear to merge.

**Recommended as tracked debt:**

1. TE3-F01 (PROP-07-065A instrumented behavioral assertion) — investigate Robolectric `FragmentScenario` as a non-emulator path.
2. TE3-F02 (PG-07 localization instrumented tests) — a single `ActivityScenario` + `withText(containsString("午"))` Espresso test with `LocaleTestRule(Locale.ENGLISH)` would cover PROP-07-058 with minimal effort.
3. TE3-F03 (`CompassViewModel.setNorthType` wiring) — add `@VisibleForTesting` access or a fake `CompassViewModel` helper that allows calling `setNorthType` with a pre-built `ZuoXiangLock` instance.
4. TE3-F04 through TE3-F09 (Low, unchanged) — performance E2E, field-level nullity, back-stack uniqueness, confidence-driven enable, pointer-sector alignment, north-type text assertion.
