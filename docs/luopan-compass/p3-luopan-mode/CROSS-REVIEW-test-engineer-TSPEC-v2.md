# Cross-Review: Test Engineer — TSPEC-luopan-p3-luopan-mode (v2)

| Field | Value |
|-------|-------|
| Reviewer | Test Engineer |
| Document | TSPEC-luopan-p3-luopan-mode.md v1.1 |
| Date | 2026-04-25 |
| Iteration | 2 |
| Recommendation | Need Attention |

---

## Prior Findings Resolution

| Prior ID | Severity | Status | Notes |
|----------|----------|--------|-------|
| F-01 | High | **Resolved** | Ring 2 table corrected with Fuxi/Earlier Heaven arrangement (乾☰ at South 157.5°–202.5°). Canonical trigram-to-bearing table added. Ring 2 boundary tests added (TE-F09). RingLabelProviderTest `ring2_sector4_is_qian_south` added. |
| F-02 | High | **Resolved** | §2.1 now contains formal 4-point rationale for `Float?`. FSPEC divergence (Double? → Float?) is documented with explicit justification. `LuopanState` data class comment updated referencing §2.1. |
| F-03 | High | **Partially Resolved — new High finding introduced** | AC-23 tests were added in both `LuopanStateMapperTest` and `LuopanFragmentTest`. However, the `ZuoXiangLockTest.rederive_northSwitch_doesNotChangeStoredTrueNorthBearing` test does not call `rederive()` — it only calls `lock()` and asserts the state is unchanged. The test verifies a trivially true property (state after `lock()` equals the locked state) without exercising the north-switch scenario. See new finding N-F01. |
| F-04 | High | **Resolved** | `ZuoXiangLock` upgraded to `AtomicReference<LockState>`. Single-writer contract documented in §4.4. `concurrentLockClear_doesNotProduceInconsistentState` test specified in `ZuoXiangLockTest`. |
| F-05 | Medium | **Resolved** | `mapper_northSwitch_updates_bearingDeg_and_northLabel` test added to `LuopanStateMapperTest`. `ac22_northTypeSwitch_updatesReadoutImmediately` added to `LuopanFragmentTest`. |
| F-06 | Medium | **Resolved** | `mapper_sensorError_while_locked_fieldsAreDashes_lockRemains` added to `LuopanStateMapperTest`. `ac28_sensorError_while_locked_overlayFrozen_readoutDashes` added to `LuopanFragmentTest`. |
| F-07 | Medium | **Resolved** | `CompassViewModelSessionStateTest` added (§12.2.2) with `ringVisibility_initializes_allTrue_on_viewModelCreation` and negative test `ringVisibility_notRestoredFromSettings`. |
| F-08 | Medium | **Resolved** | `zoomScale_initializes_to_1f_on_viewModelCreation` and negative test `zoomScale_notRestoredFromSettings` added to `CompassViewModelSessionStateTest`. |
| F-09 | Medium | **Resolved** | Ring 2 boundary test cases added to `SectorLookupTest` with 9 test cases covering key boundaries at 337.4°, 337.5°, 0°, 22.4°, 22.5°, 157.4°, 157.5°, 202.4°, 202.5°. |
| F-10 | Medium | **Not addressed** | The `showMyLanguage = true` path with empty English entry fallback to zh-Hant is still not tested. Remains open. |
| F-11 | Medium | **Resolved** | AC-03/04/05 moved to unit level in `LuopanStateMapperTest`. Instrumented tests retained as rendering smoke tests with explicit rationale note in §12.5. |
| F-12 | Medium | **Resolved** | Label completeness tests added for Rings 2–5 in `RingLabelProviderTest` (`ring2_all_labels_non_empty` through `ring5_all_labels_non_empty`). |
| F-13 | Low | **Not addressed** | No `IllegalStateException` negative test or sector-coverage mathematical proof specified for Ring 6 gap defense. Remains open. |
| F-14 | Low | **Not addressed** | No `LuopanViewTest` smoke test for `setLockState(true, 45f)` (gold tick mark). Remains open. |
| F-15 | Low | **Not addressed** | No Robolectric test for `requestLayout()` → `onSizeChanged` geometry recompute on zoom change. Remains open. |
| F-16 | Low | **Not addressed** | `mode_switch_luopan_under_300ms` measurement methodology still not specified. Remains open. |
| F-17 | Low | **Not addressed** | `displayMode_persists_LUOPAN` write-then-overwrite round-trip test not specified. Remains open. |
| F-18 | Low | **Not addressed** | `FakeCompassUiState` pattern not described. Remains open. |

---

## New Findings

### High Priority

| ID | Severity | Finding | Section Ref |
|----|----------|---------|-------------|
| N-F01 | High | **`rederive_northSwitch_doesNotChangeStoredTrueNorthBearing` does not test the north-switch scenario — it is a vacuous test.** The test body calls `lock.lock(45.0f)` and immediately asserts the stored state equals `45.0f`. It never calls `rederive()`. The test comment explicitly states "ViewModel does NOT call rederive() because the stored True N bearing is invariant." But this directly contradicts §8.2, which specifies `zuoXiangLock.rederive(currentBearing)` IS called from `onNorthTypeChanged()`. One of these two assertions is wrong: either `rederive()` is called on north-switch (§8.2) or it is not (test comment). If `rederive()` IS called (as §8.2 specifies), it invokes `lock(trueBearing)`, which OVERWRITES `xiangBearing` — making the stored value no longer the original 45.0f. The test would only pass in a world where `rederive()` is never called on north-switch. A real AC-23 test must: (1) call `lock(45.0f)`, (2) call `rederive()` with a different bearing or assert that it should NOT be called, then (3) verify the stored state. Currently the test gives false confidence that the True North bearing is preserved under north-switch. | TSPEC §4.4, §8.2, §12.1.3; FSPEC §4d |
| N-F02 | High | **`lockXiang()` in §5.3 does not implement the FSPEC §4a step 3 True-North conversion when locking under Magnetic North.** FSPEC §4a step 3 specifies: "If the user is currently viewing Magnetic North, the ViewModel converts the displayed magnetic bearing back to True North before storing: `xiangBearing_trueN = displayedBearing_magN + declinationDeg`." The TSPEC `lockXiang()` code (§5.3) reads `uiState.value.heading_deg.toFloat()` and passes it directly to `zuoXiangLock.lock()` with no declination adjustment. If the user is on Magnetic North when they tap "Lock 向", `heading_deg` is already the display bearing in the magnetic reference frame — not True North. Passing it directly to `lock()` stores a Magnetic North bearing as if it were True North. This produces wrong 山 sector lookup (山 is derived from a mag bearing, not True N) and wrong display conversion later. There is no unit test verifying `lockXiang()` stores True North when called under Magnetic North mode. This is a P0 correctness bug in the specification. | TSPEC §5.3, §4.4; FSPEC §4a step 3 |

### Medium Priority

| ID | Severity | Finding | Section Ref |
|----|----------|---------|-------------|
| N-F03 | Medium | **The behavioral contract of `rederive()` is internally contradictory and unresolvable without a decision.** §4.4 has two conflicting descriptions of what `rederive()` is for: (a) The prose says "Re-derive display bearing when north type changes while locked. The stored True North xiangBearing does NOT change — only display values change." (b) The code calls `lock(trueBearing)`, which sets `_lockState.set(LockState(..., xiangBearing = xiang, ...))` — overwriting `xiangBearing`. These two statements cannot both be true. If `rederive()` calls `lock()`, then `xiangBearing` IS changed. If `xiangBearing` must NOT change, then `rederive()` must NOT call `lock()`. The test comment in `rederive_northSwitch_doesNotChangeStoredTrueNorthBearing` sides with the FSPEC ("stored True N bearing is invariant, rederive() is not called"), but §8.2 sides with the code ("zuoXiangLock.rederive(currentBearing) is called"). This contradiction must be resolved before implementation to avoid a split-brain behavior where different engineers implement conflicting models. | TSPEC §4.4 `rederive()` doc-comment vs. code body; TSPEC §8.2; §12.1.3 test comment |
| N-F04 | Medium | **`northSwitch_doesNotChangeShanLabels` in `LuopanStateMapperTest` is insufficiently specified to detect the bug described in N-F02.** The test sets up `lockState.xiangBearing=45f` and `compassState.northLabel="Mag N"` and asserts `state.xiangMountain="艮"`. But it does not verify that the mapper uses the stored `lockState.xiangBearing` (not the live `compassState.heading_deg`) for the 山 lookup. If the mapper mistakenly re-derives the 山 label from the live bearing, and the test happens to use the same bearing for both `lockState.xiangBearing` and `compassState.heading_deg`, the test would pass even on a broken implementation. The test must use different values for `lockState.xiangBearing` (e.g., 45f for 艮) and `compassState.heading_deg` (e.g., 50f for a different 山) to prove that the mapper reads the lock state, not the live bearing. | TSPEC §12.1.2; FSPEC §4d |
| N-F05 | Medium | **AC-23 overlay display-bearing conversion (`48.5° Mag N`) has no test in `LuopanFragmentTest`.** `ac22_northTypeSwitch_updatesReadoutImmediately` tests the live readout update (FSPEC AC-22), not the locked overlay (FSPEC AC-23). `ac28_sensorError_while_locked_overlayFrozen_readoutDashes` tests SENSOR_ERROR, not north-switch. The AC-23 scenario — "overlay bearing values update to reflect the magnetic-adjusted heading (向: 艮 (48.5° Mag N), 坐: 坤 (228.5° Mag N))" — has no instrumented test that verifies the exact display bearing computation (`xiangBearing_trueN ± declinationDeg`). This conversion is performed in `LuopanFragment.updateZuoXiangOverlay()` per §4.4 — a View-layer function that cannot be tested at the unit level. A `LuopanFragmentTest` asserting the overlay text "向: 艮 (48.5° Mag N)" with `declinationDeg = -3.5f` is required. | TSPEC §12.5; FSPEC AC-23 |
| N-F06 | Medium | **`FakeCompassUiState` is not described in enough detail to implement without ambiguity (carry-over from F-18, unresolved).** The test double table (§12.6) lists `FakeCompassUiState` with purpose "Construct `CompassUiState` with specific confidence + bearing" but gives no protocol. This is the only test double that exercises the boundary between `CompassUiState` and `LuopanStateMapper`. Without knowing whether it is a `data class` builder, a factory function with default parameters, or an `interface`-based fake, different engineers will implement it differently — leading to incompatible test helper patterns. Given that `CompassUiState` is a `data class`, the standard approach is a factory function (e.g., `fun fakeCompassUiState(confidence = ..., heading_deg = ...) = CompassUiState(...)`). The TSPEC should specify the exact pattern. | TSPEC §12.6 |

### Low Priority

| ID | Severity | Finding | Section Ref |
|----|----------|---------|-------------|
| N-F07 | Low | **`lockXiang()` confidence guard in §5.3 allows locking at MODERATE confidence but FSPEC §4a says "Confidence is HIGH or MODERATE" — this is correct.** However, MODERATE locks do not show `fenJinLabel` in the overlay (FSPEC §4a step 7 note). The TSPEC AC-28 test (`ac28_sensorError_while_locked_overlayFrozen_readoutDashes`) specifies `lock button shows "Clear 向" (still enabled)` in the SENSOR_ERROR-while-locked case. This is correct per the PM-Q01 fix. But there is no test verifying that when locked at MODERATE confidence, the overlay does NOT show 分金 — only the AC-07/AC-08 tests cover the general overlay format, and those are not clearly differentiated for MODERATE-locked vs. HIGH-locked scenarios. A unit test in `LuopanStateMapperTest` for the locked-at-MODERATE state would close this gap. | TSPEC §12.5; FSPEC §4a, step 7 note |
| N-F08 | Low | **The `rederive_updates_mountains` test lacks sufficient specificity to detect a regression.** The test name implies it verifies that `rederive()` with a new bearing updates `xiangMountain` and `zuoMountain`. But if `rederive()` currently calls `lock()` with a new bearing (thereby overwriting `xiangBearing`), then this test would also implicitly test that `xiangBearing` changes — which contradicts FSPEC §4d. Once N-F01 and N-F03 are resolved and `rederive()` is given a definitive implementation, this test must be updated with precise Given/When/Then specifying whether `xiangBearing` should or should not change. | TSPEC §12.1.3 |
| N-F09 | Low | **Prior low findings F-10, F-13, F-14, F-15, F-16, F-17 remain unresolved.** The commit message for iteration 1 lists these as not addressed. While individually Low, the accumulation of 6 unresolved Low findings adds engineering risk during implementation: (a) F-13: Ring 6 gap defense has no mathematical proof or exception test; (b) F-14: gold tick mark smoke test absent; (c) F-15: `requestLayout()` geometry recompute on zoom unverified; (d) F-16: `mode_switch_luopan_under_300ms` methodology undefined; (e) F-17: `displayMode` stale-value round-trip unverified. Any of these could produce silent production bugs. | TSPEC §12.1.1, §12.3, §12.4 |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | Is `rederive()` called or NOT called on a north-type switch while locked? §8.2 says it IS called (`zuoXiangLock.rederive(currentBearing)` in `onNorthTypeChanged()`). The `rederive_northSwitch_doesNotChangeStoredTrueNorthBearing` test comment says it is NOT called. These are mutually exclusive. Which is correct? This directly determines whether `xiangBearing` is overwritten on north-switch. |
| Q-02 | When `lockXiang()` is called while the user is viewing Magnetic North, does `heading_deg` in `CompassUiState` already reflect True North (because the NorthTypeEngine converts before emitting), or does it reflect the Magnetic North display bearing? If True North is always what `heading_deg` holds (i.e., the pipeline always outputs True N and the UI just labels it differently), then the FSPEC §4a step 3 conversion note is misleading. If Magnetic North is what `heading_deg` holds during Mag N mode, then the TSPEC `lockXiang()` is missing the `+ declinationDeg` conversion. Clarifying the invariant of `CompassUiState.heading_deg` — True North always, or current-north-reference — is needed to determine if N-F02 is a real gap or not. |
| Q-03 | AC-23 specifies the exact display string "向: 艮 (48.5° Mag N)" with `declinationDeg = -3.5f`. Which layer performs the `45.0f - (-3.5f) = 48.5f` computation: `LuopanFragment.updateZuoXiangOverlay()` using `LuopanState.declinationDeg` (as §4.4 states), or `LuopanStateMapper` (which would require `LuopanState.xiangDisplayBearing` to be a separate field)? The current `LuopanState` only has `xiangBearing: Float?` (True North) and `declinationDeg: Float` — no precomputed display bearing. Is the View expected to do this arithmetic? If so, a unit test for `updateZuoXiangOverlay()` with a concrete declination would require Robolectric or an instrumented test; the current TSPEC has no such test. |

---

## Positive Observations

- **F-01 (Ring 2 LUT)** is the highest-impact correction and it was executed thoroughly: the corrected table is present with a prominent correction note, the canonical trigram-to-bearing mapping table is a valuable addition for implementors, and the matching `SectorLookupTest` boundary cases close the gap end-to-end.
- **F-04 (`AtomicReference`)** is a solid fix: the `AtomicReference<LockState>` pattern combined with the explicit single-writer contract in §4.4 is the correct approach for safe publication across coroutine dispatchers. The `concurrentLockClear_doesNotProduceInconsistentState` test specification is well-structured with an observable invariant (fully-locked OR fully-unlocked, never torn).
- **`CompassViewModelSessionStateTest` (§12.2.2)** correctly includes both positive assertions (correct initial value) and negative assertions (not restored from SharedPreferences). The negative tests are the more important half and they were specified correctly.
- **AC-03/04/05 test level migration (F-11)** was handled well: the unit tests specify exact field values (`mountainChar="午"`, `fenJinLabel="壬午分金"`) while the instrumented smoke tests verify rendering. The rationale note in §12.5 explains the split clearly.
- **PM-Q01 lock button fix** is a correct behavioral fix: `canLock || state.isLockActive` enables the button for both locking and clearing. The two new instrumented tests (`lock_button_enabled_when_lock_active_allowsClearing`, `lock_button_label_clearXiang_when_locked`) directly verify the PM-Q01 scenario.

---

## Recommendation

**Need Attention**

Two new High-severity findings must be addressed before this TSPEC is approved:

1. **N-F01 (`rederive_northSwitch_doesNotChangeStoredTrueNorthBearing` is vacuous):** The test does not call `rederive()`. This means the AC-23 test for `ZuoXiangLock` (the unit closest to the bug surface) does not actually test anything about the north-switch scenario. Once the contradiction in Q-01 is resolved (N-F03), this test must be rewritten to actually exercise the north-switch path with a meaningful assertion.

2. **N-F02 (`lockXiang()` missing Mag N → True N conversion):** FSPEC §4a step 3 requires a declination adjustment when locking under Magnetic North. The TSPEC `lockXiang()` code passes `heading_deg.toFloat()` directly to `lock()`. If `heading_deg` holds the magnetic bearing during Mag N mode (see Q-02), this is a P0 correctness bug in the specification that will produce wrong 山 labels and wrong overlay bearing values. This must be resolved — either by confirming `heading_deg` is always True North (clearing the bug) or by adding the conversion to `lockXiang()` and a corresponding test.

The resolution to Q-01 and Q-02 is a prerequisite for writing correct AC-23 tests. These questions have been open since iteration 1 (previously surfaced in Q-02 and Q-04 of the v1 review) and have now produced two new High findings by being left unresolved.
