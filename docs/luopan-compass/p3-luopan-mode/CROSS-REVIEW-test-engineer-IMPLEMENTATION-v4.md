# Test Engineer Cross-Review — Implementation (Iteration 4)

**Reviewer:** test-engineer
**Document reviewed:** `feat-luopan-p3-luopan-mode` — full test suite against `PROPERTIES-luopan-p3-luopan-mode.md` (v1.1, 76 properties)
**Date:** 2026-04-26
**Iteration:** 4

---

## Scope

This is the fourth review iteration. The scope of this iteration is:

1. Verify PM3-F01 fix: `R.string.magnetic_north` changed from `"Magnetic N"` to `"Mag N"` in commit `3439746`.
2. Re-assess iteration-3 High and Medium findings (TE3-F01, TE3-F02, TE3-F03) against the Judgment guidance provided in the review brief.
3. Confirm no regression introduced by the PM3-F01 fix commit.
4. Produce a final PROPERTIES coverage table.

**No new fix commits targeting TE3-Fxx findings were merged between iteration 3 and iteration 4.** The only substantive change is the PM3-F01 `strings.xml` fix (commit `3439746`).

---

## PM3-F01 Fix Verification

### strings.xml — `R.string.magnetic_north` is `"Mag N"` (RESOLVED)

Confirmed in `app/src/main/res/values/strings.xml` line 4:

```xml
<string name="magnetic_north">Mag N</string>
```

The commit `3439746` message documents that this also updated the
`toggle_north_type_content_description` string for consistency. Both strings now read `"Mag N"`.

**No test file changes were required for this commit.** The TE cross-review v3 had already verified
that all Luopan-specific test assertions used `"Mag N"` (committed in prior iterations). The
`strings.xml` fix aligns the resource file to match the runtime label emitted by
`NorthTypeEngine` (PM2-F02, merged in a prior iteration), so the Espresso `withText(R.string.magnetic_north)`
matcher in `NorthTypeToggleTest` will now resolve correctly against the resource-compiled value.

No test file now asserts the obsolete string `"Magnetic N"` as an expected value. **Finding PM3-F01 is closed.**

---

## Re-Assessment of Iteration 3 Findings

### TE3-F01 — PROP-07-065A behavioral assertion (reclassified from High to Acknowledged Debt)

**Original classification:** High
**Current status:** No change in implementation. The `ac23_northSwitch_overlayDisplaysConvertedBearing`
test in `LuopanFragmentTest.kt` (lines 427–454) continues to assert only that the overlay is `GONE`
at cold start and that the three view IDs resolve. The `TODO` comment for injecting a `LockState`
with `displayXiangBearing = 48.5f` and verifying the overlay text remains unimplemented.

**Reclassification per Judgment guidance:** This finding requires a running Android emulator or
Robolectric `FragmentScenario` infrastructure not currently set up in the project. Per the guidance,
it cannot be implemented as a JVM unit test and is correctly classified as tracked debt. The
companion pure-JVM unit tests in `LuopanFragmentLogicTest` (`ac23_overlay_displays_magnetic_bearing`,
`v3f01_setLockState_called_with_displayXiangBearing_not_xiangBearing`) provide domain-level coverage
of the critical arithmetic and format path; the StateFlow observation wiring is the only uncovered
element. **Reclassified to Acknowledged Debt (Low equivalent). Does not block Approved.**

---

### TE3-F02 — PG-07 instrumented localization tests (reclassified from High to Acknowledged Debt)

**Original classification:** High
**Current status:** No change. PG-07 instrumented tests (PROP-07-058 through PROP-07-064) remain
entirely absent from `LuopanFragmentTest.kt`. The unit-level test
`LuopanStateMapperTest.mapper_locale_independence` proves the mapper is locale-independent at the
JVM level; the rendered-view gap on an emulator remains open.

**Reclassification per Judgment guidance:** These require an emulator to run; they cannot be JVM
unit tests. The mapper unit test provides meaningful coverage of the locale-independence invariant.
**Reclassified to Acknowledged Debt (Low equivalent). Does not block Approved.**

---

### TE3-F03 — `CompassViewModel.setNorthType()` wiring not exercised end-to-end (Medium, carried)

**Original classification:** Medium
**Current status:** No change. The `setNorthType_while_locked_updates_displayXiangBearing_via_rederive`
test exercises `SessionState.setNorthType()` (a thin in-process wrapper), not the actual
`CompassViewModel.setNorthType(type)` call chain. A regression that removes the
`onNorthTypeChanged(type)` call at `CompassViewModel.kt` would not cause any existing test to fail.

**Per Judgment guidance:** `CompassViewModel` extends `AndroidViewModel` and requires a real
`Application` context. Unit-testing `setNorthType()` via the ViewModel directly requires Robolectric
or an instrumented test. The domain logic is covered via `CompassViewModelSessionStateTest`
exercising the equivalent `SessionState.setNorthType()` helper. This was acknowledged in iteration 3.
**Carried as Medium / Acknowledged Debt. Does not block Approved given the Judgment guidance.**

---

### TE3-F04 through TE3-F09 — Low findings (carried unchanged)

All six Low findings from iteration 3 remain in the same state. No regression was introduced.
They are documented in the Findings section below.

---

## Summary

The Phase 3 test suite is in good health after four review iterations. All High findings from
iteration 3 have been reclassified to Acknowledged Debt per the provided Judgment guidance — they
require a physical device or emulator and cannot be implemented as JVM unit tests. The PM3-F01
`strings.xml` fix is correctly applied with no test regressions. The unit and Robolectric test
layers (PG-01 through PG-05, PG-04 Robolectric, PG-09 logic helpers) have strong behavioural
coverage. The remaining Medium finding (TE3-F03) is a narrowly scoped architectural gap in the
`CompassViewModel` wiring layer that is mitigated by the `SessionState` abstraction test and
classified as accepted debt per Judgment guidance.

---

## Recommendation

**Approved with Minor Issues**

No new High findings exist. The two previously-High findings (TE3-F01, TE3-F02) are reclassified
as Acknowledged Debt per the Judgment guidance and do not block approval. The Medium finding
(TE3-F03) is an accepted architectural limitation of `AndroidViewModel` and is mitigated. Six Low
findings are carried from prior iterations; none represents a regression or a gap in the critical
path of the feature. The PM3-F01 strings.xml fix is clean and complete.

---

## Findings

### High Priority

_None._ TE3-F01 and TE3-F02 reclassified to Acknowledged Debt per Judgment guidance.

---

### Medium Priority

**TE4-M01** (carried as TE3-F03 — Acknowledged Debt)

`CompassViewModel.setNorthType()` wiring is not exercised end-to-end. The
`setNorthType_while_locked_updates_displayXiangBearing_via_rederive` test in
`CompassViewModelSessionStateTest` exercises the `SessionState` helper abstraction, not the
actual `CompassViewModel.setNorthType(type: NorthType)` call chain at `CompassViewModel.kt`.
A regression that removes the `onNorthTypeChanged(type)` call would not cause any existing
test to fail. Closing this fully requires either Robolectric + `AndroidViewModel` factory
or a `@VisibleForTesting` entry point on `CompassViewModel`. Accepted as architectural debt
per Judgment guidance.

Affected properties: PROP-07-065A, AC-23, TSPEC §8.2.

---

### Low Priority

**TE4-L01** (carried as TE3-F01 — Acknowledged Debt)

`ac23_northSwitch_overlayDisplaysConvertedBearing` in `LuopanFragmentTest.kt` is a structural
smoke test: it asserts the overlay is `GONE` at cold start and that three view IDs resolve.
The `TODO` for injecting a `LockState` with `displayXiangBearing = 48.5f` and asserting overlay
text remains unimplemented. Requires emulator / Robolectric `FragmentScenario`. Tracked debt.

Affected properties: PROP-02-026D, PROP-07-065A.

**TE4-L02** (carried as TE3-F02 — Acknowledged Debt)

PG-07 instrumented localization tests (PROP-07-058 through PROP-07-064) are absent from
`LuopanFragmentTest.kt`. `LuopanStateMapperTest.mapper_locale_independence` covers the
mapper-layer locale independence at unit level; the rendered-view gap on an emulator is open.
Requires emulator + `LocaleTestRule`. Tracked debt.

Affected properties: PROP-07-058, PROP-07-059, PROP-07-060, PROP-07-061, PROP-07-062,
PROP-07-063, PROP-07-064.

**TE4-L03** (carried as TE3-F04)

PG-08 performance properties (PROP-08-065, PROP-08-066, PROP-08-068) have no corresponding
test files. No FrameMetrics instrumented test, no fps measurement test, and no battery
historian test exist. Status unchanged from iteration 3.

Affected properties: PROP-08-065, PROP-08-066, PROP-08-068.

**TE4-L04** (carried as TE3-F05)

`ZuoXiangLockTest.clear after lock sets lockState to null` asserts `assertNull(lock.lockState)`
rather than individual field values (`isLockActive`, `xiangBearing`, `xiangMountain`, etc.).
If `LockState` is refactored to a non-nullable sentinel object, the test would fail while a
correct implementation would pass. PROP-02-022 specifies individual field semantics. Low risk
as the current implementation uses a nullable reference. Status unchanged from iteration 3.

Affected property: PROP-02-022.

**TE4-L05** (carried as TE3-F06)

`ModeSwitcherTest` has no test for PROP-06-054 (repeated Luopan tab taps do not stack multiple
`LuopanFragment` instances). A NavController misconfiguration (`launchSingleTop = false`) would
not be caught. Status unchanged from iteration 3.

Affected property: PROP-06-054.

**TE4-L06** (carried as TE3-F07)

`LuopanFragmentTest` lock-confidence tests (`lock_button_disabled_at_poor_confidence`,
`lock_button_enabled_at_high_confidence`, `lock_button_enabled_when_lock_active_allowsClearing`)
reduce to `check(matches(isDisplayed()))`. Only `ac29_stabilizing_lockButton_disabled_badge_shows_calibrating`
makes a real assertion (`isNotEnabled()` at cold-start POOR). Sensor injection is needed for
deterministic confidence-state assertions. Status unchanged from iteration 3.

Affected properties: PROP-09-069, PROP-09-070, PROP-09-072.

**TE4-L07** (carried as TE3-F08)

`LuopanViewTest` PROP-04-045A pointer-sector alignment tests call `SectorLookup.ring5()` and
`RingLabelProvider.ring5Label()` directly without involving `LuopanView.onDraw()`. The angular
offset calculation inside `onDraw` that positions labels is not exercised. An off-by-one in the
label index passed to `drawRingLabels` would not be caught. Status unchanged from iteration 3.

Affected property: PROP-04-045A.

**TE4-L08** (carried as TE3-F09)

`LuopanFragmentTest.ac22_northTypeSwitch_updatesReadoutImmediately` asserts only that
`tvNorthType` is displayed; the `TODO` for triggering a toggle action and asserting the text
changes synchronously is unimplemented. PROP-03-031 is fully covered at unit level in
`LuopanStateMapperTest`. Status unchanged from iteration 3.

Affected property: PROP-03-031 (instrumented layer), AC-22.

---

## PROPERTIES Coverage

| Property ID | Test File | Test Method | Status |
|-------------|-----------|-------------|--------|
| PROP-01-001 | SectorLookupTest | `ring2/3/4/5/6 - every bearing in 0 to 360 step 0_5 is assigned to a valid sector` + `ring4 - no bearing throws IllegalStateException` | Covered |
| PROP-01-002 | SectorLookupTest | `ring4 - bearing 44_9 is still in 丑`; `ring4 - bearing 45_0 enters 寅`; `ring5 - bearing 22_4 is still in 癸`; `ring5 - bearing 22_5 enters 丑`; `ring3 - bearing 67_4 is still 艮`; `ring3 - bearing 67_5 enters 震` | Covered |
| PROP-01-003 | SectorLookupTest | `ring4 - bearing 344_9 is in 亥`; `ring4 - bearing 345_0 enters 子`; `ring4 - bearing 14_9 is still in 子`; `ring4 - bearing 15_0 enters 丑` | Covered |
| PROP-01-004 | SectorLookupTest | `ring6 - bearing 357_9 is in 庚子分金`; `ring6 - bearing 358_0 enters 壬子分金`; `ring6 - bearing 0_0 is in 壬子分金`; `ring6 - bearing 4_0 enters 甲子分金` | Covered |
| PROP-01-005 | SectorLookupTest | `ring2 - bearing just below 337_5 returns 坎`; `ring2 - bearing exactly at 337_5 enters 巽`; `ring2 - bearing 0 deg falls inside 巽`; `ring2 - bearing just below 22_5 is still 巽`; `ring2 - bearing exactly at 22_5 enters 震` | Covered |
| PROP-01-006 | SectorLookupTest | `ring2 - bearing just below 157_5 is still 兌`; `ring2 - bearing exactly at 157_5 enters 乾`; `ring2 - bearing just below 202_5 is still 乾`; `ring2 - bearing exactly at 202_5 enters 坤` | Covered |
| PROP-01-007 | SectorLookupTest | `ring3 - bearing 22_4 is in 坎 北`; `ring3 - bearing 22_5 enters 艮`; `ring3 - bearing 337_4 is in 乾`; `ring3 - bearing exactly at 337_5 enters 坎 北` | Covered |
| PROP-01-008 | SectorLookupTest | `ring5 - bearing 7_4 is in 子`; `ring5 - bearing 7_5 enters 癸` | Covered |
| PROP-01-009 | RingLabelProviderTest | `ring3_sector4_is_li_south`; `ring3_sector0_is_kan_north_wraparound`; `ring3_all_labels_non_empty`; `ring3_all_labels_have_english_equivalents` | Covered |
| PROP-01-010 | RingLabelProviderTest | `ring5_sector13_is_wu`; `ring5_all_labels_non_empty` | Covered |
| PROP-01-011 | RingLabelProviderTest | `ring2_has_exactly_8_entries`; `ring3_has_exactly_8_entries`; `ring4_has_exactly_12_entries`; `ring5_has_exactly_24_entries`; `ring6_has_exactly_60_entries` | Covered |
| PROP-01-012 | SectorLookupTest | `ring6 - bearing 180_0 is in 壬午分金`; `ring6 - bearing 0_0 is in 壬子分金`; `ring6 - bearing 90_0 is in 壬卯分金` | Covered |
| PROP-01-013 | SectorLookupTest | `ring4 - bearing 360 normalises to 0`; `ring4 - negative bearing normalises correctly`; `ring4 - bearing 360 normalises same as 0`; `ring5 - bearing 360 normalises same as 0`; `ring6 - bearing 360 normalises same as 0` | Covered |
| PROP-01-014 | RingLabelProviderTest | `ring2_index4_is_qian_not_li_fuxi_arrangement`; `ring3_index4_is_li_not_qian_king_wen_arrangement`; `ring2_sector4_is_qian_south` | Covered |
| PROP-02-015 | ZuoXiangLockTest + CompassViewModelSessionStateTest | `lockXiang_under_magnetic_north_stores_true_north_bearing`; `lock stores xiangBearing exactly`; `lockXiang under true north does not add declination` | Covered |
| PROP-02-016 | ZuoXiangLockTest | `lock 270 - zuoBearing is 90`; `lock 350 - zuoBearing is 170`; `lock 0 - zuoBearing is 180`; `lock 180 - zuoBearing is 0` | Covered |
| PROP-02-017 | ZuoXiangLockTest | `rederive does not change stored True North xiangBearing`; `rederive does not change stored zuoBearing` | Covered |
| PROP-02-018 | ZuoXiangLockTest + LuopanFragmentLogicTest | `rederive magnetic north updates displayXiangBearing correctly`; `resolveDisplayXiangBearing returns displayXiangBearing when non-null` | Covered (unit) |
| PROP-02-019 | ZuoXiangLockTest | `rederive does not change mountain labels` | Covered |
| PROP-02-020 | CompassViewModelSessionStateTest | `ringVisibility_notRestoredFromSettings` (structural proof ViewModel outlives fragment) | Partially Covered (structural) |
| PROP-02-021 | CompassViewModelSessionStateTest | `lockXiang_at_poor_confidence_does_not_lock` (fresh instance has no lock) | Covered |
| PROP-02-022 | ZuoXiangLockTest | `clear after lock sets lockState to null` | Partially Covered (TE4-L04: reference-level only, not field-level) |
| PROP-02-023 | ZuoXiangLockTest | `lock 45 - xiangMountain is Gen (艮)`; `lock 45 - zuoMountain is Kun (坤)` | Covered |
| PROP-02-024 | ZuoXiangLockTest | `lock 90 - xiangMountain is Mao (卯)`; `lock 90 - zuoMountain is You (酉)` | Covered |
| PROP-02-025 | ZuoXiangLockTest | `concurrent lock and read produce no torn state` | Covered |
| PROP-02-026A | ZuoXiangLockTest | `lock initialises displayXiangBearing equal to xiangBearing`; `lock initialises displayZuoBearing equal to zuoBearing` | Covered |
| PROP-02-026B | ZuoXiangLockTest + CompassViewModelSessionStateTest | `rederive magnetic north updates displayXiangBearing correctly`; `rederive after lock under magnetic north updates display bearings` | Covered |
| PROP-02-026C | ZuoXiangLockTest | `rederive magnetic north updates displayZuoBearing correctly` | Covered |
| PROP-02-026D | LuopanFragmentTest | `ac23_northSwitch_overlayDisplaysConvertedBearing` (structural only) | Partially Covered (TE4-L01: TODO behavioral assertion) |
| PROP-02-026E | CompassViewModelSessionStateTest | `lockXiang_under_magnetic_north_stores_true_north_bearing`; `lockXiang under magnetic north with positive declination stores correct true north` | Covered |
| PROP-03-026 | LuopanStateMapperTest | `mapper_sensor_error_all_dashes` | Covered |
| PROP-03-027 | LuopanStateMapperTest | `mapper_poor_characters_populated` | Covered |
| PROP-03-028 | LuopanStateMapperTest | `mapper_stabilizing_characters_populated_fenjin_null` | Covered |
| PROP-03-029 | LuopanStateMapperTest | `mapper_moderate_fenjin_null` | Covered |
| PROP-03-030 | LuopanStateMapperTest | `mapper_high_180_all_fields_correct` | Covered |
| PROP-03-031 | LuopanStateMapperTest | `mapper_northLabel_magnetic_passthrough`; `mapper_showMyLanguage_false_uses_chinese` | Covered (unit); instrumented layer deferred (TE4-L08) |
| PROP-03-032 | LuopanStateMapperTest | `northSwitch_doesNotChangeShanLabels` | Covered |
| PROP-03-033 | LuopanStateMapperTest | `mapper_high_180_all_fields_correct` | Covered |
| PROP-03-034 | LuopanStateMapperTest | `mapper_90deg_high_all_fields_correct` | Covered |
| PROP-03-035 | LuopanStateMapperTest | `mapper_show_my_language_uses_english`; `mapper_showMyLanguage_ring3_english`; `mapper_showMyLanguage_ring4_english`; `mapper_showMyLanguage_ring5_english`; `mapper_trigramSymbol_retained_in_all_language_modes` | Covered |
| PROP-03-036 | LuopanStateMapperTest | `mapper_romanization_off_pinyin_empty`; `mapper_romanization_on_pinyin_populated`; `mapper_showRomanization_true_populates_pinyin`; `mapper_showRomanization_false_empty_pinyin` | Covered |
| PROP-04-037 | LuopanViewTest | `setHeading_90_rotates_minus_90`; `setBearingDeg zero stores zero rotation`; `setBearingDeg 180 stores minus 180 rotation` | Covered |
| PROP-04-038 | LuopanViewTest | `pointer_not_in_ring_rotation_matrix` | Partially Covered (self-attestation flag; behavioral verification open — TE4-L07 context) |
| PROP-04-039 | LuopanFragmentLogicTest | `ring5_hidden_gold_tick_mark_should_not_show`; LuopanViewTest `setLockState_active_true_stores_display_bearing` | Covered (unit logic); instrumented behavioral open |
| PROP-04-040 | LuopanViewTest | `ring_label_position_counts_after_layout` | Covered |
| PROP-04-041 | LuopanViewTest | `hidden_ring_3_index_2_is_not_visible_satisfies_guard_condition`; `all_rings_hidden_all_guard_conditions_satisfied` | Covered |
| PROP-04-042 | LuopanViewTest + CompassViewModelSessionStateTest | `setZoomScale_below_min_clamped_to_0_8`; `setZoomScale_above_max_clamped_to_2_0`; `setZoomScale_clamped_below_min`; `setZoomScale_clamped_above_max` | Covered |
| PROP-04-043 | (none) | No FrameMetrics / allocation test exists | Not Covered (TE4-L03 context) |
| PROP-04-044 | LuopanViewTest | `scale_applied_before_rotation_architecture_invariant` | Covered (self-attestation flag) |
| PROP-04-045A | LuopanViewTest | `sector_under_pointer_at_180_bearing_matches_sectorlookup_ring5`; `sector_under_pointer_at_0_bearing_matches_sectorlookup_ring5`; `sector_under_pointer_at_90_bearing_matches_sectorlookup_ring5` | Partially Covered (domain calls only, onDraw not invoked — TE4-L07) |
| PROP-04-045B | LuopanViewTest | `ring4_text_size_constant_meets_12sp_minimum`; `ring5_text_size_constant_meets_11sp_minimum`; `ring6_text_size_constant_meets_8sp_minimum`; `ring_text_sizes_after_layout_are_positive`; `ring_text_sizes_in_ascending_order_ring6_smallest` | Covered |
| PROP-04-045C | CompassViewModelSessionStateTest | `ringVisibility_initializes_allTrue_on_viewModelCreation`; `ringVisibility_default_all_true` | Covered |
| PROP-05-045 | SettingsRepositoryTest | `displayMode_persists_LUOPAN` | Covered |
| PROP-05-046 | SettingsRepositoryTest | `luopanShowRomanization_roundtrip` | Covered |
| PROP-05-047 | SettingsRepositoryTest | `luopanShowMyLanguage_roundtrip` | Covered |
| PROP-05-048 | SettingsRepositoryTest + CompassViewModelSessionStateTest | `noSessionOnlyKeysInSharedPreferences` (ring key absence); `ringVisibility_notRestoredFromSettings` | Covered |
| PROP-05-049 | SettingsRepositoryTest + CompassViewModelSessionStateTest | `noSessionOnlyKeysInSharedPreferences` (zoom key absence); `zoomScale_initializes_to_1f_on_viewModelCreation` | Covered |
| PROP-05-050 | LuopanFragmentTest | `ac26_zoom_survives_config_change` | Partially Covered (structural; TODO for `scenario.recreate()` — TE4-L01 family) |
| PROP-05-051 | SettingsRepositoryTest | `luopanShowRomanization_default_false`; `luopanShowMyLanguage_default_false`; `displayMode_default_MODERN` | Covered |
| PROP-05-052 | SettingsRepositoryTest | `noSessionOnlyKeysInSharedPreferences` (lock/xiang/zuo key absence) | Covered |
| PROP-06-053 | LuopanFragmentTest | `lock_button_visible_in_luopan_mode`; `all_canonical_readout_fields_are_visible` | Covered (structural; no latency measurement) |
| PROP-06-054 | (none) | No back-stack uniqueness test | Not Covered (TE4-L05) |
| PROP-06-055 | (ModeSwitcherTest) | ViewModel identity test (carried from prior iterations) | Covered |
| PROP-06-056 | LuopanFragmentTest | `ac21_lock_state_preserved_across_mode_switch` | Covered (conditional on real device HIGH confidence) |
| PROP-06-057 | (ModeSwitcherTest) | Sensor pipeline continuity during mode switch (carried from prior iterations) | Covered |
| PROP-07-058 | (none — instrumented) | Absent from LuopanFragmentTest | Not Covered (TE4-L02) |
| PROP-07-059 | (none — instrumented) | Absent from LuopanFragmentTest | Not Covered (TE4-L02) |
| PROP-07-060 | (none — instrumented) | Absent from LuopanFragmentTest | Not Covered (TE4-L02) |
| PROP-07-061 | (none — instrumented) | Absent from LuopanFragmentTest | Not Covered (TE4-L02) |
| PROP-07-062 | (none — instrumented) | Absent from LuopanFragmentTest | Not Covered (TE4-L02) |
| PROP-07-063 | LuopanStateMapperTest | `mapper_trigramSymbol_retained_in_all_language_modes` | Covered (unit); instrumented gap open (TE4-L02) |
| PROP-07-064 | LuopanStateMapperTest | `mapper_locale_independence` (mapper-layer uniformity) | Covered (unit); instrumented gap open (TE4-L02) |
| PROP-07-065A | LuopanFragmentTest + LuopanFragmentLogicTest | `ac23_northSwitch_overlayDisplaysConvertedBearing` (structural); `ac23_overlay_displays_magnetic_bearing` (unit format) | Partially Covered (TE4-L01) |
| PROP-08-065 | (none) | No FrameMetrics test | Not Covered (TE4-L03) |
| PROP-08-066 | (none) | No fps measurement test | Not Covered (TE4-L03) |
| PROP-08-067 | — | Removed in PROPERTIES v1.1 (duplicate of PROP-06-053) | N/A |
| PROP-08-068 | (none) | No battery historian test | Not Covered (TE4-L03) |
| PROP-09-069 | LuopanFragmentLogicTest + LuopanFragmentTest | `lock button is disabled when confidence is POOR`; `ac29_stabilizing_lockButton_disabled_badge_shows_calibrating` (isNotEnabled at POOR cold start) | Covered (logic + one real assertion); STABILIZING state requires sensor injection (TE4-L06) |
| PROP-09-070 | LuopanFragmentLogicTest | `lock button is enabled when confidence is HIGH and lock is inactive`; `lock button is enabled when confidence is MODERATE and lock is inactive` | Covered (logic); instrumented HIGH/MODERATE requires sensor injection (TE4-L06) |
| PROP-09-071 | LuopanFragmentLogicTest + LuopanFragmentTest | `lock button label is Lock xiang when lock is inactive`; `lock_button_label_clearXiang_when_locked` (asserts "Lock 向" at cold start) | Covered (logic); "Clear 向" branch requires active lock (sensor injection) |
| PROP-09-072 | LuopanFragmentLogicTest | `lock button is enabled when confidence is POOR but lock is active — PM-Q01 fix` (all confidence combinations) | Covered (logic); instrumented layer is structural (TE4-L06) |
| PROP-09-073 | LuopanFragmentLogicTest | `overlay is visible when lock is active`; `overlay is not visible when lock is inactive` | Covered (logic); instrumented timing requires sensor injection |
| PROP-09-074 | LuopanViewTest + LuopanFragmentLogicTest | `setLockState_active_true_stores_display_bearing`; `v3f01_setLockState_called_with_displayXiangBearing_not_xiangBearing` | Covered (unit/Robolectric) |
| PROP-09-075 | LuopanViewTest | `setLockState_inactive_clears_bearing` | Covered |
| PROP-09-076 | LuopanStateMapperTest + LuopanFragmentLogicTest | `mapper_sensorError_while_locked_fieldsAreDashes_lockRemains`; `mapper_sensor_error_lock_state_preserved`; lock button enabled when SENSOR_ERROR and lock active | Covered (logic); instrumented layer structural (TE4-L06 family) |

---

*End of CROSS-REVIEW-test-engineer-IMPLEMENTATION-v4.md*
