# Cross-Review: test-engineer — Implementation

**Reviewer:** test-engineer
**Document reviewed:** `feat-luopan-p3-luopan-mode` — full test suite against `PROPERTIES-luopan-p3-luopan-mode.md` (v1.1, 76 properties)
**Date:** 2026-04-25
**Iteration:** 2

---

## Scope

This is the second review iteration. Iteration 1 raised 12 findings (2 High, 5 Medium, 5 Low). The following fixes were declared in the `fix(p3-cr)` commit (`331c002`):

- **TE-F03** (Medium): Ring 3 337.4°/337.5° boundary tests added to `SectorLookupTest`
- **TE-F07** (Medium): `noSessionOnlyKeysInSharedPreferences` broadened from exact key names to substring checks
- **TE-F01** (High): PG-07 localization instrumented tests — acknowledged as instrumented-only; unit coverage noted in `LuopanStateMapperTest`
- **TE-F02** (High): PROP-07-065A behavioral assertion TODO — noted as tracked debt
- **TE-F04/F05** (Medium): Architectural and E2E constraints — acknowledged as deferred

Additionally, the PR description indicates a new test `rederive_called_when_northType_switches` was added to `CompassViewModelSessionStateTest` to cover the PM-F01 fix (wiring `setNorthType()` → `onNorthTypeChanged()` → `ZuoXiangLock.rederive()`).

---

## Fix Verification

### TE-F03 — Ring 3 337.4°/337.5° boundary tests (RESOLVED)

Both tests are present and correctly specified in `SectorLookupTest.kt`:

```
ring3 - bearing 337_4 is in 乾 西北 (index 7)   → assertEquals(7, SectorLookup.ring3(337.4f))
ring3 - bearing exactly at 337_5 enters 坎 北 wrap-around (index 0) → assertEquals(0, SectorLookup.ring3(337.5f))
```

The test at line 115 correctly asserts index 7 (乾, last sector before the wrap) and the test at line 121 asserts index 0 (坎 北, start of the wrap). These match PROP-01-007 exactly. **Finding closed.**

---

### TE-F07 — `noSessionOnlyKeysInSharedPreferences` substring broadening (PARTIAL — new gap introduced)

The `ring` and `zoom` checks were correctly broadened from exact substring matches (`ring_visible`, `zoom_scale`) to prefix-only matches (`ring`, `zoom`):

```kotlin
assertFalse("No key containing 'ring' should be present in SharedPreferences",
    allKeys.any { it.contains("ring") })
assertFalse("No key containing 'zoom' should be present in SharedPreferences",
    allKeys.any { it.contains("zoom") })
```

However, the **lock key check was NOT updated** — it remains an exact equality comparison:

```kotlin
assertFalse("lock key must not be stored in SharedPreferences",
    allKeys.any { it == "lock" })
```

The original TE-F07 finding explicitly identified `"xiang_bearing"`, `"zuo_bearing"`, and `"is_lock_active"` as key names that would not be caught by the then-existing narrow check. The fix commit message states "Broaden noSessionOnlyKeysInSharedPreferences assertions from exact substring matches to prefix-only matches" — but this was applied only to ring/zoom. The lock assertion remains at `it == "lock"`, which catches only the literal key named `"lock"` and misses any lock-related key written under a different name. **Finding NOT fully closed — lock key assertion still uses exact equality.**

---

### PM-F01 — `rederive_called_when_northType_switches` wiring test (ADDED — with coverage gap)

The test `rederive_called_when_northType_switches` was added to `CompassViewModelSessionStateTest` (lines 393–416). It:

1. Creates a bare `ZuoXiangLock()` instance (not a `CompassViewModel`)
2. Calls `lock.lock(45.0f)` directly
3. Calls `lock.rederive(-3.5f, isMagneticNorth = true)` directly
4. Asserts `displayXiangBearing == 48.5f` and `xiangBearing == 45.0f`

This test verifies that `ZuoXiangLock.rederive()` correctly updates the display bearing without modifying the stored True North bearing — but it does **not** test the wiring between `CompassViewModel.setNorthType()` → `CompassViewModel.onNorthTypeChanged()` → `zuoXiangLock.rederive()`. The comment in the test acknowledges this indirection ("simulate setNorthType(MAGNETIC) — which must call onNorthTypeChanged()") but does not exercise that call chain.

A regression that removes the `onNorthTypeChanged()` call from `setNorthType()` in `CompassViewModel` (line 193) would not cause this test to fail, because the test bypasses the ViewModel entirely. The actual wiring — `setNorthType()` calling `onNorthTypeChanged()` — is tested only implicitly through existing `CompassViewModelTest` tests for north-type state changes, none of which assert the downstream `zuoXiangLock.rederive()` effect.

**Finding status: test added but tests the wrong level — the ViewModel wiring gap (setNorthType → onNorthTypeChanged → rederive) remains uncovered.**

---

## Findings

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| TE2-F01 | High | **PROP-07-065A behavioral assertion remains a structural smoke test.** `ac23_northSwitch_overlayDisplaysConvertedBearing` in `LuopanFragmentTest` (lines 427–454) asserts only that the overlay is `GONE` at cold start and that the three view IDs resolve — the TODO comment for "inject LuopanState with isLockActive=true, displayXiangBearing=48.5f ... assert overlay VISIBLE and text matches" is still unimplemented. No instrumented test injects a `LockState` with updated `displayXiangBearing` and verifies the overlay text update. The companion unit test `ac23_overlay_displays_magnetic_bearing` in `LuopanFragmentLogicTest` covers format-string computation in isolation but cannot verify the StateFlow → overlay update pipeline. This was carried from iteration 1 F-02 as acknowledged tracked debt. | PROP-02-026D, PROP-07-065A |
| TE2-F02 | High | **PG-07 instrumented localization tests (PROP-07-058 through PROP-07-064) remain entirely absent.** No test in `LuopanFragmentTest` verifies that an English system locale does not cause English ring labels, that the "Show romanization" toggle renders pinyin on-device, that "Show in my language" renders English equivalents uniformly, or that trigram symbols are retained in all modes. This was carried from iteration 1 F-01 as acknowledged tracked debt. | PROP-07-058–064 |
| TE2-F03 | Medium | **TE-F07 lock-key assertion was not fully broadened — remains exact equality.** After the fix, the `noSessionOnlyKeysInSharedPreferences` test checks `allKeys.any { it == "lock" }` for lock-related keys (line 101). This is an exact equality match, not a substring check. Keys such as `"xiang_bearing"`, `"zuo_bearing"`, `"lock_active"`, or `"is_lock_active"` would pass this test even if erroneously written to `SharedPreferences`. The ring/zoom checks were correctly broadened to `.contains(...)` but the lock check was not updated to match. The fix was partial. | PROP-05-052, BR-10, REQ §8 |
| TE2-F04 | Medium | **`rederive_called_when_northType_switches` tests the domain object, not the ViewModel wiring.** The PM-F01 test calls `ZuoXiangLock.lock()` and `ZuoXiangLock.rederive()` directly, bypassing `CompassViewModel.setNorthType()` and `CompassViewModel.onNorthTypeChanged()`. Removing the `onNorthTypeChanged()` call at `CompassViewModel.kt:193` would not cause any unit test to fail. No test in any file calls `viewModel.setNorthType(NorthType.MAGNETIC)` and then asserts that `zuoXiangLock.lockState.displayXiangBearing` was updated. The wiring gap (`setNorthType → onNorthTypeChanged → rederive`) is untested. | PROP-07-065A, AC-23, TSPEC §8.2 |
| TE2-F05 | Low | **F-06 (PG-08 performance properties) remains uncovered.** No FrameMetrics, fps, or battery historian instrumented tests exist. Status unchanged from iteration 1. | PROP-08-065, PROP-08-066, PROP-08-068 |
| TE2-F06 | Low | **F-08 (`ZuoXiangLock.clear()` field nullity) remains reference-only.** `clear after lock sets lockState to null` asserts `assertNull(lock.lockState)` rather than individual field values. Status unchanged from iteration 1. | PROP-02-022 |
| TE2-F07 | Low | **F-09 (back-stack uniqueness) remains untested.** `ModeSwitcherTest` has no test for PROP-06-054 (repeated Luopan tab taps do not stack multiple `LuopanFragment` instances). Status unchanged from iteration 1. | PROP-06-054 |
| TE2-F08 | Low | **F-10 (`LuopanFragmentTest` confidence tests are structural smoke tests).** `lock_button_disabled_at_poor_confidence`, `lock_button_enabled_at_high_confidence`, and `lock_button_enabled_when_lock_active_allowsClearing` all reduce to `check(matches(isDisplayed()))` with comments acknowledging full verification is deferred. Status unchanged from iteration 1. | PROP-09-069, PROP-09-070, PROP-09-072 |
| TE2-F09 | Low | **F-11 (PROP-04-045A pointer-sector alignment) is tested via domain calls, not rendered view).** Three tests in `LuopanViewTest` call `SectorLookup.ring5()` and `RingLabelProvider.ring5Label()` directly without invoking `LuopanView.onDraw()`. Status unchanged from iteration 1. | PROP-04-045A |
| TE2-F10 | Low | **F-12 (`ac22_northTypeSwitch_updatesReadoutImmediately` defers core assertion).** Test asserts only that `tvNorthType` is displayed, with TODO for the toggle action and text assertion. Status unchanged from iteration 1. | AC-22, PROP-03-031 |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | For TE2-F03 (lock key check), would a single-line change from `it == "lock"` to `it.contains("lock") || it.contains("xiang") || it.contains("zuo")` be acceptable? This would catch any lock-related key variant. |
| Q-02 | For TE2-F04 (ViewModel wiring gap), the `CompassViewModelTest` already has infrastructure that creates `NorthTypeEngine` instances. Could a test be added to `CompassViewModelTest` that stubs `ZuoXiangLock` (or uses a real one) and asserts `lockState.displayXiangBearing` after calling `viewModel.setNorthType(NorthType.MAGNETIC)` + `viewModel.lockXiang()` + `viewModel.setNorthType(NorthType.TRUE)`? |
| Q-03 | (Carried from iteration 1 Q-02) For PROP-07-065A, is downsizing from E2E to Integration (Robolectric + `MutableStateFlow` injection) accepted by the team? |

---

## Positive Observations

- **TE-F03 fix is correct and precisely specified.** The two new `SectorLookupTest` tests match the PROP-01-007 worked example exactly — index 7 for 337.4° and index 0 for 337.5°. The comment labelling accurately references the `[292.5°, 337.5°)` range. No regression was introduced in adjacent tests.

- **`rederive_called_when_northType_switches` is well-structured as a domain test.** Even though it does not cover the ViewModel wiring gap (TE2-F04), it is a useful regression guard for `ZuoXiangLock.rederive()` correctness. The worked example (45° True N + declination −3.5° → 48.5° display) matches PROP-02-026B exactly, and the True North invariant assertion is correct.

- **PM-F02 click-handler logic tests in `LuopanFragmentLogicTest` are complete and cover all five confidence states.** The `clickHandlerAction` helper mirrors the post-fix handler decision tree (show_toast / lock / clear) and all five paths are exercised. This is a meaningful improvement over the pre-fix state.

- **Ring/zoom key substring broadening in `noSessionOnlyKeysInSharedPreferences` is the right approach.** The change from `.contains("ring_visible")` to `.contains("ring")` correctly catches any ring-related key variant. The fix direction is correct; only the lock key was missed (TE2-F03).

- **Overall iteration-1 findings that are acknowledged as tracked debt (F-04, F-05, F-06, F-08–F-12) remain stable — no regression** in those areas was introduced by the fix commit.

---

## Recommendation

**Need Attention**

Two High and two Medium findings are present:

- **TE2-F01** (PROP-07-065A behavioral assertion) and **TE2-F02** (PG-07 localization instrumented tests absent) are unchanged from iteration 1 and accepted as tracked debt.
- **TE2-F03** (lock key exact-equality assertion) is a regression in the fix itself — the ring/zoom keys were broadened but the lock key was not, leaving the original TE-F07 finding only 2/3 resolved.
- **TE2-F04** (ViewModel wiring gap for `setNorthType → onNorthTypeChanged → rederive`) is a new gap introduced by the PM-F01 fix: the test was placed at the wrong level and does not cover the actual call chain.

**Required before merge:**

1. In `SettingsRepositoryTest.noSessionOnlyKeysInSharedPreferences`, change the lock key assertion from `it == "lock"` to a substring check covering lock-related key names (e.g., `.contains("lock") || .contains("xiang") || .contains("zuo")`). This closes TE2-F03. (1-line change.)

2. Add a unit test that calls `CompassViewModel.setNorthType(NorthType.MAGNETIC)` (or `onNorthTypeChanged()`) directly after a lock is active, and asserts that `zuoXiangLock.lockState!!.displayXiangBearing` differs from `zuoXiangLock.lockState!!.xiangBearing`. This closes TE2-F04 by exercising the actual wiring, not the domain object in isolation. (Can be placed in `CompassViewModelSessionStateTest` using the existing `SessionState` helper or a minimal `CompassViewModel` test helper.)

**Acceptable as tracked debt (unchanged from iteration 1):**

3. TE2-F01 (PROP-07-065A instrumented behavioral assertion — requires ViewModel seam or test fake)
4. TE2-F02 (PG-07 localization instrumented tests — require emulator locale override)
5. TE2-F05 through TE2-F10 (performance E2E, field-level nullity assertions, back-stack uniqueness, confidence-driven enable, pointer-sector alignment, north-type text assertion)
