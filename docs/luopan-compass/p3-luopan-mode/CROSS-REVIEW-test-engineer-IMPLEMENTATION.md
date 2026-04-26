# Cross-Review: test-engineer — Implementation

**Reviewer:** test-engineer
**Document reviewed:** `feat-luopan-p3-luopan-mode` — full test suite against `PROPERTIES-luopan-p3-luopan-mode.md` (v1.1, 76 properties)
**Date:** 2026-04-25
**Iteration:** 1

---

## Scope

Reviewed the following test files against all 76 PROPERTIES:

| File | Test count | Level |
|------|-----------|-------|
| `SectorLookupTest.kt` | 50 | Unit |
| `RingLabelProviderTest.kt` | 29 | Unit |
| `ZuoXiangLockTest.kt` | 25 | Unit |
| `LuopanStateMapperTest.kt` | 27 | Unit |
| `CompassViewModelSessionStateTest.kt` | 25 | Unit |
| `LuopanFragmentLogicTest.kt` | 64 | Unit |
| `LuopanViewTest.kt` | 37 | Robolectric |
| `SettingsRepositoryTest.kt` | 13 | Robolectric |
| `LuopanFragmentTest.kt` | 18 | Instrumented |
| `ModeSwitcherTest.kt` | 3 | Instrumented |

---

## Findings

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| F-01 | High | **PG-07 instrumented localization tests are fully absent.** PROP-07-058 through PROP-07-064 are classified as Integration/E2E and mapped to `LuopanFragmentTest`. None of those seven properties has a corresponding test in `LuopanFragmentTest.kt`. No test verifies that (a) an English system locale does NOT cause English ring labels to appear on the dial, (b) the "Show romanization" toggle displays pinyin in the readout panel on-device, (c) the "Show in my language" toggle changes ring labels and readout uniformly, or (d) trigram symbols are retained in all language modes on the rendered view. `LuopanStateMapperTest` covers the pure-logic equivalents but that is explicitly classified as a unit test one level below — the Integration/E2E gap for PROP-07-058–064 is entirely open. | PROP-07-058, PROP-07-059, PROP-07-060, PROP-07-061, PROP-07-062, PROP-07-063, PROP-07-064 |
| F-02 | High | **PROP-07-065A (north-reference switch while locked, no user interaction required) is tested only at the structural level.** The instrumented test `ac23_northSwitch_overlayDisplaysConvertedBearing` in `LuopanFragmentTest` asserts only that the overlay is `GONE` at cold start and that the three view IDs resolve correctly. The behaviorally critical assertion — that injecting a `LockState` with updated `displayXiangBearing` causes the overlay text to update *without any additional user action* — is deferred with a TODO comment. The companion unit test in `LuopanFragmentLogicTest` (`ac23_overlay_displays_magnetic_bearing`) verifies the format string computation in isolation but does NOT test the StateFlow observation path that drives the live update. The PROPERTIES doc classifies this as E2E (instrumented), and that level is not meaningfully covered. | PROP-02-026D, PROP-07-065A |
| F-03 | Medium | **PROP-01-007 / Ring 3 wrap-around start boundary is partially untested.** `SectorLookupTest` verifies `ring3(22.4°) → 坎` and `ring3(22.5°) → 艮` (the *end* of the 坎 wrap), and verifies `ring3(0°) → 坎`. It does NOT test `ring3(337.4°) → 乾 (last sector before wrap)` and `ring3(337.5°) → 坎 (start of wrap)`. The analogous Ring 2 test (`ring2 - bearing just below 337_5 returns 坎` and `ring2 - bearing exactly at 337_5 enters 巽`) correctly models this boundary — the Ring 3 counterpart is missing. A implementation bug that places the 坎 wrap-around start at 338° or 337° instead of 337.5° would not be caught. | PROP-01-007, Scenario H (REQ §5.3b) |
| F-04 | Medium | **PROP-04-038 (pointer not in rotation transform) is tested only via an architectural flag constant.** `LuopanViewTest.pointer_not_in_ring_rotation_matrix` asserts `LuopanView.POINTER_DRAWN_IN_SCREEN_SPACE == true`. This is a self-attestation — the view declares its own correctness. It does not behaviorally verify that the pointer pixel position is invariant to `setBearingDeg()` changes. A regression where `drawFixedPointer()` is accidentally moved inside `canvas.rotate()` would still pass this test (the constant would not change). A behavioral test would: call `setBearingDeg(0f)` and `setBearingDeg(90f)` and assert the pointer's rendered pixel position is identical in both frames (can be achieved via Robolectric + `Canvas` recording or a `RecordingCanvas` shadow). | PROP-04-038, BR-11, AC-02b |
| F-05 | Medium | **PROP-04-043 (no allocations in `onDraw`) is entirely untested.** The PROPERTIES doc acknowledges it requires "Integration (FrameMetrics instrumentation)" but no such test exists in any test file. There is no Robolectric test that injects a `RecordingCanvas` to count `Paint` allocations, no custom allocation counter, and no FrameMetrics instrumented test. The `SCALE_APPLIED_BEFORE_ROTATION` constant test next to it is again a self-attestation. A regression where ring label `Paint` objects are created inside `onDraw` would not be caught by any current test. | PROP-04-043, REQ-NFR-LUOPAN-02 |
| F-06 | Medium | **PG-08 performance properties (PROP-08-065, PROP-08-066, PROP-08-068) have no corresponding test files.** No FrameMetrics instrumented test, no battery historian test, and no fps measurement test exists in the repo. These are E2E properties and were always intended for a later hardware pass, but there is no placeholder test class, no `@Ignore`-tagged test with a clear TODO, and no CI gate. The absence means these REQ-NFR-LUOPAN-01/02/03 properties will never fail a build. | PROP-08-065, PROP-08-066, PROP-08-068 |
| F-07 | Medium | **PROP-05-052 (坐向 lock is session-only, not written to `SettingsRepository`) is not directly tested.** `SettingsRepositoryTest.noSessionOnlyKeysInSharedPreferences` checks that `ring_visible` and `zoom_scale` keys are absent, and that a key literally named `"lock"` is absent. It does NOT confirm the absence of all lock-related keys (e.g., `"xiang_bearing"`, `"zuo_bearing"`, `"is_lock_active"`). The test is too narrow — an implementation that persists the lock under any other key name would pass. | PROP-05-052, BR-10, REQ §8 |
| F-08 | Low | **`ZuoXiangLockTest` does not verify individual field nullity after `clear()`.** `clear after lock sets lockState to null` asserts `lock.lockState == null` which is sufficient when `lockState` is modelled as a nullable reference. However, PROP-02-022 specifies that `isLockActive = false`, `xiangBearing = null`, `xiangMountain = null`, etc. should each be individually verifiable. If `LockState` is later refactored to a non-nullable sentinel object (e.g., `LockState.EMPTY` with `isLockActive = false`), the current test would fail (it asserts null) while the implementation would still be correct. Conversely, if the implementation is changed to return a sentinel, the test would catch a regression. A more robust test would assert the semantic fields rather than reference nullity. | PROP-02-022, FSPEC §4b |
| F-09 | Low | **PROP-06-054 (back-stack uniqueness: repeated Luopan tab taps do not stack multiple instances) is not tested.** `ModeSwitcherTest` covers mode-switch timing (PROP-06-053), lock preservation (PROP-06-056), and ViewModel identity (PROP-06-055), but has no test that taps the Luopan tab multiple times and asserts the back-stack count remains 1. A NavController misconfiguration (e.g., `launchSingleTop = false`) would not be caught. | PROP-06-054, FSPEC §1.4 |
| F-10 | Low | **`LuopanFragmentTest` lock-confidence tests degrade to structural smoke tests.** Tests `lock_button_disabled_at_poor_confidence`, `lock_button_enabled_at_high_confidence`, and `lock_button_enabled_when_lock_active_allowsClearing` all reduce to `check(matches(isDisplayed()))` — they assert the button exists, not its enabled/disabled state. Only `ac29_stabilizing_lockButton_disabled_badge_shows_calibrating` makes a real assertion (`isNotEnabled()` at cold-start POOR). The three tests add false confidence coverage for PROP-09-069, PROP-09-070, and PROP-09-072 without actually exercising the confidence-driven enable logic on-device. | PROP-09-069, PROP-09-070, PROP-09-072 |
| F-11 | Low | **`LuopanViewTest` PROP-04-045A pointer-sector alignment is tested via pure domain calls, not via a rendered view.** The three tests for bearing 0°, 90°, and 180° call `SectorLookup.ring5()` and `RingLabelProvider.ring5Label()` directly and assert the returned label string — they do not interact with `LuopanView` at all. The view is never asked to render at those bearings and the label visually under the pointer is never verified. The gap is the angular offset calculation inside `onDraw` that positions labels relative to the dial center — an off-by-one in the label index passed to `drawRingLabels` would not be caught because the test bypasses `LuopanView.onDraw()` entirely. | PROP-04-045A, BR-11, AC-02a |
| F-12 | Low | **`LuopanFragmentTest.ac22_northTypeSwitch_updatesReadoutImmediately` defers its core assertion.** The test only verifies that `tvNorthType` is displayed — it does not assert the current text value, and the TODO comment for the toggle action is unimplemented. PROP-03-031 (north label pass-through) is fully covered at unit level in `LuopanStateMapperTest`, but the instrumented counterpart for AC-22 does not advance past structural wiring. | AC-22, PROP-03-031 |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | For PROP-07-058/059 (locale tests), was there a deliberate decision to defer the instrumented locale-override tests (e.g., using `Locale.setDefault()` in an `ActivityScenario`-scoped `@Rule`)? The `CompassIntegrationTest.scenarioF_japaneseLocaleNoEnglishStrings` is a TODO with no planned implementation date. |
| Q-02 | For PROP-07-065A, the PROPERTIES doc classifies this as E2E (instrumented). Would the team accept downsizing this to Integration (Robolectric) using a `MutableStateFlow` injection into `LuopanFragment` to verify the StateFlow-to-overlay update pipeline without requiring a real device? |
| Q-03 | For PROP-04-038 (pointer invariance to rotation), is there an existing Robolectric shadow that intercepts `canvas.rotate()` calls to make the "pointer drawn outside rotation" assertion behavioral rather than architectural? |
| Q-04 | For PROP-08-065/066/068, is there a performance CI pipeline (e.g., via Macrobenchmark on Firebase Test Lab) that was planned to host these tests, or are they deferred indefinitely? |

---

## Positive Observations

- **PG-01 boundary coverage is excellent.** `SectorLookupTest` exhaustively covers all four wrap-around sectors (Ring 2, 3, 4, 6), both ends of every boundary listed in Scenario H, normalization of 360°→0° and negative inputs, and the 0.5° step exhaustive sweep for all five rings. The specific REQ §5.3b boundary examples (344.9/345.0/14.9/15.0 for Ring 4; 7.4/7.5/22.4/22.5 for Ring 5; 22.4/22.5/67.4/67.5 for Ring 3) are all present and explicitly annotated.

- **PG-02 ZuoXiangLock unit tests are thorough and behaviorally precise.** The four spot-check bearings from PROP-02-016 are all present. The `rederive()` contract (PROP-02-017) is tested with the exact worked example from the PROPERTIES doc (lock 45°, declination −3.5°, expected displayXiang 48.5°). The wrap-around normalization test for `rederive` near 0° (5° True N, declination 10° → 355° display) is a valuable edge case not mandated by the PROPERTIES doc.

- **PG-03 `LuopanStateMapperTest` covers confidence-state transitions comprehensively.** All five `OverallConfidence` values (HIGH, MODERATE, POOR, STABILIZING, SENSOR_ERROR) are tested, including the critical distinction that STABILIZING keeps character fields populated while SENSOR_ERROR forces them to "—". The `mapper_locale_independence` test (Locale.ENGLISH JVM default → still zh-Hant output) is an especially strong regression guard for BR-08.

- **`LuopanFragmentLogicTest` is architecturally sound.** Extracting pure formatting helpers from `LuopanFragment` and testing them as pure JVM functions means the entire overlay formatting pipeline (AC-07, AC-08, AC-10, AC-11, AC-23, V3-F01) is covered deterministically without emulator or sensor data. The PM-Q01 fix (`canLock || isLockActive`) is explicitly tested for all five confidence states.

- **`CompassViewModelSessionStateTest` covers the Magnetic→True North conversion path (PROP-02-026E) with four distinct cases:** negative declination, positive declination, True North mode (no adjustment), and wrap-around normalization at 359°. These are the most regression-prone arithmetic cases and they are all present.

- **`SettingsRepositoryTest` correctly tests the negative session-only contract.** The `noSessionOnlyKeysInSharedPreferences` test performs actual writes of all persisted keys and then reads back the raw SharedPreferences map to confirm no session-only keys leaked in.

- **`ModeSwitcherTest.fragments_share_viewmodel_instance` uses `assertSame` (reference identity), not `assertEquals`.** This is the correct assertion for PROP-06-055 — structural equality of two different ViewModel instances would pass incorrectly if two ViewModels were created.

---

## Recommendation

**Need Attention**

Six High/Medium findings are present (F-01 through F-06). The most impactful are:

- **F-01** (PG-07 localization instrumented tests entirely absent) and **F-02** (PROP-07-065A structural-only) represent the largest coverage gap: the localization and north-switch-while-locked behaviors — two of the most user-visible features — have no on-device test that can catch a regression.
- **F-03** (Ring 3 wrap-around start boundary) is a small but concrete unit test gap that could mask a LUT indexing bug at 337.5°.
- **F-04** (pointer rotation self-attestation only) and **F-05** (no `onDraw` allocation test) represent properties that will never fail a build if regressed.
- **F-07** (PROP-05-052 lock-persistence check is too narrow) could miss a lock key persisted under a non-literal name.

**Required before merge:**

1. Add `ring3(337.4f) → 乾` and `ring3(337.5f) → 坎` boundary tests to `SectorLookupTest` (F-03 — 2 tests, trivial effort).
2. Broaden `noSessionOnlyKeysInSharedPreferences` to assert no key matching `"lock"`, `"xiang"`, or `"zuo"` is present in SharedPreferences after the standard SettingsRepository operations (F-07 — 1 line change).

**Recommended before merge (should not block if tracked):**

3. Add at least one locale-override instrumented test in `LuopanFragmentTest` for PROP-07-058 — using `Locale.setDefault(Locale.ENGLISH)` and verifying a ring label TextView shows a Chinese character (F-01).
4. Inject a `LuopanState` with `isLockActive=true` and `displayXiangBearing=48.5f` into `LuopanFragment` via a ViewModel seam (or fake) and assert the overlay TextView text in `ac23_northSwitch_overlayDisplaysConvertedBearing` (F-02).

**Acceptable as tracked debt:**

5. F-04 (pointer behavioral invariance test), F-05 (onDraw allocation test), F-06 (performance E2E tests), F-08–F-12 (low-severity gaps) — can be tracked as follow-up issues for the Phase 4 / Phase 5 test hardening sprint.
