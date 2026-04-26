# Cross-Review: Test Engineer — TSPEC-luopan-p3-luopan-mode

| Field | Value |
|-------|-------|
| Reviewer | Test Engineer |
| Document | TSPEC-luopan-p3-luopan-mode.md |
| Date | 2026-04-25 |
| Recommendation | Need Attention |

---

## Summary

The TSPEC presents a clean architectural decomposition and an explicit test strategy with identifiable test files. The domain objects (`SectorLookup`, `RingLabelProvider`, `ZuoXiangLock`, `LuopanStateMapper`) are pure functions or simple classes with no Android dependencies, making them highly testable as unit tests. However, several significant gaps exist: (1) the sector LUT tables contain a data error that would cause Ring 2 test assertions to be wrong; (2) multiple FSPEC acceptance criteria have no corresponding test; (3) the `ZuoXiangLock` thread-safety contract is untestable as specified; (4) the type mismatch between FSPEC (`Double?`) and TSPEC (`Float?`) for lock-bearing fields is unresolved and will cause contract failures; and (5) the north-reference-switch behavior for locked bearings (FSPEC Flow 4d / Scenario J-2) is untested entirely.

---

## Findings

### High Priority

| ID | Severity | Finding | Section Ref |
|----|----------|---------|-------------|
| F-01 | High | **Ring 2 sector LUT is incorrect — trigram-to-sector mapping is wrong.** In TSPEC §4.1 Ring 2 (先天八卦), Sector 0 is labeled "☴ 巽 北" with range `[337.5°, 22.5°)`. But the REQ §5.3a and FSPEC §4.3 both define Ring 2 Sector 5 (☴ 巽 北) at range 337.5°–22.5° and Sector 1 (☰ 乾 南) at 157.5°–202.5°. The TSPEC LUT has the entire Ring 2 assignment shifted or rearranged relative to what the REQ/FSPEC define. Any unit test asserting `ring2(bearing)` will test against a wrong oracle. `RingLabelProviderTest.ring2_*` tests (when written) will fail or give false confidence. | TSPEC §4.1 Ring 2 table vs. REQ §5.3a table vs. FSPEC §4.3 |
| F-02 | High | **Type mismatch between FSPEC and TSPEC for lock-bearing fields creates untestable contract.** FSPEC §4.1 `LuopanState` data contract specifies `xiangBearing: Double?` and `zuoBearing: Double?`. TSPEC §5.1 specifies both as `Float?`. This is an unresolved conflict — not a design decision documented in §2. Any instrumented test asserting the overlay display bearing (e.g., "45.0° True N") will behave differently: `Float` has ~7 significant digits while `Double` has ~15. Near sector boundaries, the two types can produce different formatted values. The TSPEC must formally decide and document which type is canonical; the FSPEC data contract must agree. Until resolved, the overlay display tests (AC-07, AC-08, AC-10, AC-11, AC-21, AC-23) cannot be written with confidence. | TSPEC §2.1, §5.1; FSPEC §4.1 |
| F-03 | High | **North-reference-switch-while-locked behavior (FSPEC Flow 4d / Scenario J-2) has no test.** FSPEC §§4d and ES-03 specify that when north type changes while locked, the stored True North `xiangBearing` does NOT change but the display bearing recalculates as `displayBearing = xiangBearing_trueN ± declinationDeg`. FSPEC Scenario J-2 is an explicit acceptance criterion (FSPEC AC-23). The TSPEC test strategy contains no unit test for `ZuoXiangLock.rederive()` behavior under north-type change, no mapper test for the display-bearing conversion path, and no instrumented test for AC-23. This is the most complex non-trivial business rule in the feature. | TSPEC §12.1.3, §12.4, §12.5; FSPEC §4d, AC-23 |
| F-04 | High | **`ZuoXiangLock` thread-safety contract is uncheckable as designed.** TSPEC §4.4 states the class is "thread-safe: only mutated on `Dispatchers.Default` in sensor collection or on main thread via explicit ViewModel functions" — but this is a design convention, not an enforced property. There is no test verifying that `lock()` and `clear()` are never called from the wrong dispatcher, and no thread-safety test under concurrent reads and writes. If a configuration change causes `clearXiang()` (called on main thread) to race with the sensor pipeline calling `recomputeLuopanState()` (on `Dispatchers.Default`), the lock state can be inconsistent. The class should use `@GuardedBy` or `AtomicReference`; the test strategy should include a concurrency test. | TSPEC §4.4, §12.1.3 |

### Medium Priority

| ID | Severity | Finding | Section Ref |
|----|----------|---------|-------------|
| F-05 | Medium | **FSPEC AC-22 (north reference switch updates readout immediately) has no test.** AC-22 tests that when north type switches to Mag N, the live bearing in the readout updates and ring fields update to reflect the new bearing. The `LuopanStateMapperTest` covers confidence states and localization but does not test north-type transition. The instrumented `LuopanFragmentTest` does not include this scenario. | TSPEC §12.1.2, §12.5; FSPEC AC-22 |
| F-06 | Medium | **FSPEC AC-28 (SENSOR_ERROR while 坐向 locked — overlay frozen, readout shows "—") has no test.** This is a compound state: `isLockActive = true` AND `confidence = SENSOR_ERROR`. The `LuopanStateMapperTest` tests each in isolation, but the interaction between an active lock and SENSOR_ERROR is not tested. Specifically: the test must assert that `fenJinLabel = null`, character fields = "—", and `isLockActive` remains `true` simultaneously. | TSPEC §12.1.2; FSPEC AC-28 |
| F-07 | Medium | **FSPEC AC-15 (ring visibility session reset on cold start) has no unit or integration test.** AC-15 asserts that ring visibility resets to all-`true` on cold start. The TSPEC §12.2 `SettingsRepositoryTest` verifies persisted settings; §12.4 `ModeSwitcherTest` covers mode-switch behavior. But there is no test asserting that `ringVisible` in `CompassViewModel` initializes to `BooleanArray(6) { true }` on ViewModel creation, and specifically that it is NOT restored from any persisted store. This is a negative test (MUST NOT persist) and requires explicit verification. | TSPEC §12.4; FSPEC AC-15; REQ §5.7.1 |
| F-08 | Medium | **FSPEC AC-27 (zoom resets on cold start) has no test.** Analogous to F-07. No test asserts that `_zoomScale` initializes to `1.0f` on ViewModel construction (cold start) rather than restoring any prior value. A simple ViewModel unit test would suffice. | TSPEC §12.3; FSPEC AC-27; REQ §5.7.2 |
| F-09 | Medium | **`SectorLookupTest` does not cover Ring 2 (先天八卦) boundary assertions.** The TSPEC §12.1.1 explicitly covers Rings 3, 4, 5, and 6 boundary assertions, but there are no tests for Ring 2. While Ring 2 is "decorative" (not in the readout panel), it is still rendered on the dial and its sector LUT feeds `RingLabelProvider.ring2Label()`. A data error in Ring 2 will render wrong trigram labels on the dial without any test catching it. At minimum, one boundary assertion per ring-2 sector boundary (337.5°, 22.5°, 67.5°, 112.5°, etc.) should be specified. | TSPEC §12.1.1 |
| F-10 | Medium | **`LuopanStateMapperTest` does not test the `showMyLanguage = true` path exhaustively — no test for the "empty English entry falls back to zh-Hant" behavior.** FSPEC §2 (Flow 7, Toggle B) specifies that entries without a standard English equivalent continue to display in Traditional Chinese. The mapper test for `showMyLanguage` (TSPEC §12.1.2) uses a single representative bearing and checks English labels. There is no negative test asserting that a Ring 5 entry with no English equivalent (if any exist) renders in zh-Hant rather than an empty string. This is particularly important because a missing entry would silently show blank text. | TSPEC §12.1.2; FSPEC Flow 7, Toggle B |
| F-11 | Medium | **FSPEC AC-03, AC-04, AC-05 — canonical readout format tests are not unit-testable as specified; they are placed at instrumented level without justification.** AC-03, AC-04, and AC-05 are fully deterministic given a fixed bearing and confidence state. They can be verified entirely in `LuopanStateMapperTest` at the unit level. Pushing them to instrumented tests in `LuopanFragmentTest` is more expensive and slower, and risks conflating rendering bugs with logic bugs. The TSPEC should specify mapper-level unit tests for the exact field values, with instrumented tests verifying display formatting separately. | TSPEC §12.1.2, §12.5 |
| F-12 | Medium | **`RingLabelProviderTest` does not cover the full label completeness requirement.** The TSPEC specifies `ring6_all_labels_non_empty()` but does not specify equivalent completeness tests for Rings 2, 3, 4, and 5. If a zh-Hant character string is accidentally empty (e.g., a copy-paste error during data entry), no test catches it. Add `ring2_all_labels_non_empty`, `ring3_all_labels_non_empty`, `ring4_all_labels_non_empty`, `ring5_all_labels_non_empty` to the specification. | TSPEC §12.1.4 |

### Low Priority

| ID | Severity | Finding | Section Ref |
|----|----------|---------|-------------|
| F-13 | Low | **`SectorLookupTest` does not specify a test for `IllegalStateException` when the sector table has a gap.** TSPEC §11.5 states that `SectorLookup.ring6()` throws `IllegalStateException` if a bearing falls in a gap — and that this "must not reach production" with unit test coverage as the defense. The `SectorLookupTest` should include a negative test that asserts the exception is thrown when a hypothetical gap-bearing is injected (or, alternatively, a table-coverage test that mathematically proves the 60 Ring 6 sectors cover exactly 360° without gap). | TSPEC §11.5, §12.1.1 |
| F-14 | Low | **`LuopanViewTest` (Robolectric) does not test gold tick mark rendering.** The tick mark is drawn by `drawGoldTickMark()` when `isLockActive = true`. The Robolectric test suite has no test for `setLockState(true, 45f)` and no assertion about the tick mark being drawn (or at minimum, that `invalidate()` is called). Even a smoke test (`lock_state_set_does_not_crash`) would add coverage. | TSPEC §12.3, §6.1.4 |
| F-15 | Low | **`LuopanViewTest` does not test `requestLayout()` behavior on zoom change.** TSPEC §6.2 observer code calls `luopanView.requestLayout()` when zoom changes to trigger `onSizeChanged` geometry recompute. No test verifies that `setZoomScale()` followed by a layout pass correctly updates `baseRadius` (which affects all pre-computed label positions). A Robolectric `onSizeChanged` simulation test would catch geometry recompute bugs. | TSPEC §12.3, §6.2 |
| F-16 | Low | **`ModeSwitcherTest` `mode_switch_luopan_under_300ms` does not specify the measurement methodology.** It is unclear whether this test measures wall-clock time from tab tap to `onDraw` completion, or uses `FrameMetrics`. Without a defined methodology, the test can give false pass/fail results depending on emulator speed. The test specification should state the measurement approach (e.g., `ActivityScenario` + `onIdle()` with a timestamp before the navigate call and a `Choreographer.FrameCallback` to record the first draw). | TSPEC §12.4; FSPEC AC-01 |
| F-17 | Low | **`SettingsRepositoryTest` does not specify a test for `displayMode` round-trip with `LUOPAN` value.** The test `displayMode_persists_LUOPAN` is listed but only one-directional. There is no test asserting that after writing `LUOPAN` and then writing `MODERN`, the result is `MODERN` (not stale). This matters because Android's SharedPreferences can return stale values in certain test configurations. | TSPEC §12.2 |
| F-18 | Low | **`FakeCompassUiState` test double is not described in enough detail to implement without further clarification.** TSPEC §12.6 lists `FakeCompassUiState` as a test double but gives no protocol — it is unclear whether this is a data class builder, a factory function, or a protocol-based fake. Given that `CompassUiState` is a data class (not an interface), the standard approach would be a factory/builder helper, not a "fake" in the protocol sense. The description should clarify the exact pattern to avoid engineers creating a brittle mock. | TSPEC §12.6 |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | FSPEC §4.1 specifies `xiangBearing: Double?` and `zuoBearing: Double?`; TSPEC §5.1 changes these to `Float?`. Is this a resolved open issue or an accidental divergence? The TSPEC Open Issue §2.1 covers `LuopanState` bearing fields but only discusses the general `Float` vs `Double` decision — it does not explicitly address the lock-bearing fields. Which document is canonical? |
| Q-02 | TSPEC §8.2 describes north-type re-derivation: "The mapper reads `compassState.heading_deg.toFloat()` as the input for lock re-derivation when `isLockActive == true`." This contradicts FSPEC §4d which states that the stored `xiangBearing_trueN` does NOT change when north type switches — only the display bearing changes. Which behavior is implemented: (a) the lock stores True North and display converts, or (b) the lock is re-derived from the live heading on every north-type change? These are functionally different. |
| Q-03 | TSPEC §12.1.1 lists `ring6_180_is_renwu` as a test case with the comment "sector 178°–184°". But the FSPEC §4.7 Ring 6 table (sector #32) gives 壬午分金 range as 178°–184°. At bearing exactly 178.0°, which sector does the test assert — 庚午分金 `[172°, 178°)` or 壬午分金 `[178°, 184°)`? The test name uses bearing 180° (sector center) so the assertion is clear for that value, but the boundary at 178.0° is not explicitly tested. Should this be a boundary test case? |
| Q-04 | Does `LuopanStateMapper` perform the display-bearing conversion for the locked overlay (True N ↔ Mag N), or does `LuopanFragment` perform this conversion from raw `xiangBearing` and current declination? The TSPEC §4.3 mapper logic (steps 1–11) does not include a display-bearing conversion step. If the View layer does it, there needs to be a unit test in the View layer (or a Robolectric test) for this conversion. Currently there is no test for this path. |

---

## Positive Observations

- The three core domain objects (`SectorLookup`, `RingLabelProvider`, `LuopanStateMapper`) are designed as pure objects with no Android dependencies. This is excellent — it makes the 30+ unit tests fully runnable on the JVM without an emulator or Robolectric, giving fast feedback and high confidence.
- The sector boundary test data in `SectorLookupTest` (§12.1.1) is derived directly from the FSPEC Scenario H tables. The boundary-pair pattern (e.g., 344.9° vs 345.0°, 14.9° vs 15.0°) correctly tests the inclusive-left exclusive-right convention at both sides of each boundary.
- The `ZuoXiangLock` wrap-around test cases (0°, 180°, 270°, 350°) cover the full modular arithmetic space including both `xiang < 180` and `xiang >= 180` branches of the `(xiang + 180f) % 360f` formula.
- Specifying Robolectric for `LuopanViewTest` (§12.3) rather than instrumented tests is the right call — it tests rendering math (rotation formula, zoom clamping) without emulator overhead.
- The test double table (§12.6) explicitly names the doubles and their purpose, making the testing contract clear.
- The traceability table (§13) provides a clear REQ-to-TSPEC mapping, making it easy to audit coverage during review.

---

## Recommendation

**Need Attention**

Four High-severity findings must be addressed before this TSPEC is approved:

1. **F-01 (Ring 2 LUT error):** The Ring 2 `SectorEntry` table in §4.1 must be corrected to match REQ §5.3a and FSPEC §4.3. Every sector label in the TSPEC table is different from the REQ/FSPEC tables. This is a data error that will produce wrong dial labels in production and wrong test oracles.

2. **F-02 (Float vs Double for lock bearings):** The TSPEC must explicitly resolve the `Float?` vs `Double?` type for `xiangBearing`/`zuoBearing` in §5.1 and document the resolution in §2.1. The FSPEC data contract must be updated to agree, or the TSPEC must justify the divergence with a formal open-issue resolution entry.

3. **F-03 (No test for north-reference-switch-while-locked):** FSPEC AC-23 (Scenario J-2) must have at least one test specified — either a `LuopanStateMapperTest` asserting display bearing conversion, or a `ZuoXiangLockTest` asserting `rederive()` behavior with a non-zero declination value.

4. **F-04 (`ZuoXiangLock` thread-safety):** Either the class must be made concurrency-safe by design (e.g., using `AtomicReference<LockState>` or confining all mutation to a single coroutine), or a test must be specified that verifies thread-safety under concurrent `lock()` and `clear()` calls. The current spec leaves a race condition untested.
