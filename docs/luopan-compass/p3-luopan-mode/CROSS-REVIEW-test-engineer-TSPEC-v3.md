# Cross-Review: Test Engineer — TSPEC-luopan-p3-luopan-mode (v3)

| Field | Value |
|-------|-------|
| Reviewer | Test Engineer |
| Document | TSPEC-luopan-p3-luopan-mode.md v1.2 |
| Date | 2026-04-25 |
| Iteration | 3 |
| Recommendation | Approved with Minor Issues |

---

## Prior Findings Resolution

| Prior ID | Severity | Status | Notes |
|----------|----------|--------|-------|
| N-F01 | High | **Resolved** | `rederive()` no longer calls `lock()`. Uses `_lockState.set(current.copy(...))` to update only `displayXiangBearing` / `displayZuoBearing`. `rederive_northSwitch_doesNotChangeStoredTrueNorthBearing` now calls `rederive(declinationDeg = -3.5f, isMagneticNorth = true)` and asserts all four invariants: stored `xiangBearing == 45.0f`, `zuoBearing == 225.0f`, `xiangMountain == "艮"`, `zuoMountain == "坤"` unchanged, and display values updated to 48.5f / 228.5f. |
| N-F02 | High | **Resolved** | `lockXiang()` now converts display bearing to True North before calling `lock()`: `bearingTrueNorth = (displayBearing + declinationDeg + 360f) % 360f` when `northType == MAGNETIC`. Math is correct: 41.5f + (−3.5f) = 38.0f. Test asserts `xiangBearing == 38.0f`. See math verification below. |
| N-F03 | Medium | **Resolved** | `rederive()` docstring (§4.4) explicitly states: "The stored `xiangBearing` (True North) is never modified by this method." The prose contract and the code body are now consistent — `rederive()` uses `current.copy(...)`, preserving all stored True North values. |
| N-F04 | Medium | **Resolved** | `northSwitch_doesNotChangeShanLabels` (§12.1.2) uses `lockState.xiangBearing = 45.0f` (艮 sector [37.5°–52.5°)) and `compassState.heading_deg = 60.0` (寅 sector [52.5°–67.5°)). The comment explicitly justifies the bearing choice: 50° would still fall in 艮 and fail to detect the bug; 60° (寅) definitively distinguishes the code paths. |
| N-F05 | Medium | **Resolved** | `ac23_northSwitch_overlayDisplaysConvertedBearing` added to `LuopanFragmentTest` (§12.5) with full three-step setup (inject lock at 45.0f True N, set declinationDeg = −3.5f, switch northType to MAGNETIC) and precise assertions: xiangLabel == "向: 艮 (48.5° Mag N)", zuoLabel == "坐: 坤 (228.5° Mag N)". The math for these values is verified correct (see below). |
| N-F06 | Medium | **Not addressed** | `FakeCompassUiState` is still listed in §12.6 with no protocol description. Carry-over. |
| N-F07 | Low | **Not addressed** | No unit test for the locked-at-MODERATE-confidence state asserting `fenJinLabel == null` in the overlay. Carry-over. |
| N-F08 | Low | **Resolved** | `rederive_updates_displayBearings_notStoredTrueNorth` is now specified (§12.1.3) with concrete Given/When/Then: lock at 45.0f, rederive with decl=−3.5f, assert display values updated and True N values unchanged. The previous ambiguous `rederive_updates_mountains` is superseded. |
| N-F09 | Low | **Partially addressed** | N-F08 resolved (see above). F-10, F-13, F-14, F-15, F-16, F-17 from the v1 review remain unresolved. |

---

## Math Verification

### N-F02 — `lockXiang_magneticMode_convertsToTrueNorth` expected value

The test scenario: display bearing = 41.5° Magnetic North, `declinationDeg = −3.5f` (East-positive convention).

Phase 2 pipeline formula (§2.2): `trueHeading = magneticHeading + declination`

Therefore: `TrueNorth = magneticDisplay + declinationDeg = 41.5 + (−3.5) = 38.0°`

The test asserts `xiangBearing == 38.0f`. **The math is correct.**

38.0° falls in Ring 5 sector 艮 [37.5°, 52.5°) — `xiangMountain == "艮"` is also correct.

### N-F05 — `rederive()` display bearing for AC-23 worked example

The AC-23 scenario: `xiangBearing = 45.0f True N`, `declinationDeg = −3.5f`, switching to Magnetic North.

`rederive()` formula (§4.4): `displayXiang = (xiangTrueN − declinationDeg + 360f) % 360f`

`displayXiang = 45.0 − (−3.5) = 48.5°`

`displayZuo = 225.0 − (−3.5) = 228.5°`

Overlay asserts "向: 艮 (48.5° Mag N)" and "坐: 坤 (228.5° Mag N)". **The math is correct.**

Formula consistency: if `TrueN = Mag + decl`, then `Mag = TrueN − decl = 45.0 − (−3.5) = 48.5°`. The display bearing in Magnetic North mode is the raw magnetic bearing — confirmed correct.

---

## New Findings

### Medium Priority

| ID | Severity | Finding | Section Ref |
|----|----------|---------|-------------|
| V3-F01 | Medium | **`LuopanView.setLockState()` is passed `luopan.xiangBearing` (True North) but the gold tick mark renders using this bearing as a position within a canvas rotating by `−bearingDeg` (magnetic heading in Mag N mode). In True N mode this is correct: the dial rotates by the True N heading and the tick at `xiangBearing` (True N) aligns to the locked field. But in Mag N mode, the dial rotates by the magnetic heading, so a tick placed at `xiangBearing` (True N) appears shifted from the locked bearing by `declinationDeg`. `LuopanState.displayXiangBearing` exists precisely for this purpose and is already passed to the overlay via `LockState`, but §6.2 passes `luopan.xiangBearing` to `setLockState()` instead. No test verifies the tick mark bearing in Mag N mode. `LuopanView` has no specification for which bearing it should use for the gold tick mark in Mag N mode.** | TSPEC §6.1.4, §6.2 (`luopanView.setLockState(luopan.isLockActive, luopan.xiangBearing)`); §5.1 (`displayXiangBearing` field) |

### Low Priority

| ID | Severity | Finding | Section Ref |
|----|----------|---------|-------------|
| V3-F02 | Low | **`lockXiang_magneticMode_convertsToTrueNorth` does not assert `displayXiangBearing` after the immediate `rederive()` call that follows in `lockXiang()`.** The test verifies that `xiangBearing == 38.0f` (correct True North stored) and `xiangMountain == "艮"` (correct). But `lockXiang()` also calls `zuoXiangLock.rederive(declinationDeg, isMagneticNorth = true)` immediately after `lock()`. An implementer who omits this `rederive()` call would see identical `xiangBearing` and `xiangMountain` results — the missing call is undetectable by this test. The expected `displayXiangBearing` after the combined `lock(38.0f)` + `rederive(−3.5f, true)` call should be `41.5f` (restoring the original Magnetic display bearing). Adding this assertion would ensure the `rederive()` call in `lockXiang()` is not accidentally dropped during implementation. | TSPEC §5.3 (`lockXiang()` code), §12.1.3 (`lockXiang_magneticMode_convertsToTrueNorth`) |
| V3-F03 | Low | **`FakeCompassUiState` (§12.6) is still described only as "Construct `CompassUiState` with specific confidence + bearing" with no protocol.** Carry-over from N-F06. The standard pattern for a `data class` is a top-level factory function (e.g., `fun fakeCompassUiState(confidence = ..., heading_deg = ..., declination_deg = ..., northType = ...) = CompassUiState(...)`). The absence of a specified pattern means different engineers may implement incompatible test helpers. | TSPEC §12.6 |
| V3-F04 | Low | **Prior low findings F-10, F-13, F-14, F-15, F-16, F-17 from the v1 review remain unresolved.** Carry-over from N-F09. Specifically: (a) `showMyLanguage=true` with empty English entry has no fallback test; (b) no Ring 6 exception test or mathematical sector-coverage proof; (c) no gold tick mark smoke test in `LuopanViewTest`; (d) `requestLayout()` → `onSizeChanged` recompute on zoom unverified; (e) `mode_switch_luopan_under_300ms` measurement methodology undefined; (f) `displayMode_persists_LUOPAN` write-then-overwrite round-trip unspecified. | TSPEC §12.1.1, §12.2.1, §12.3, §12.4 |

---

## Positive Observations

- **N-F01/N-F03 resolution is thorough.** The introduction of `displayXiangBearing` / `displayZuoBearing` as explicit fields in `LockState` is the correct architectural decision: it removes the ambiguity between "stored True North for sector lookup" and "display bearing for the overlay". The docstring, code body, §8.2 prose, and test are now fully consistent.
- **N-F02 implementation is correct and the math is verified.** `lockXiang()` correctly applies `displayBearing + declinationDeg` when `northType == MAGNETIC`, followed by an immediate `rederive()` to restore the display bearing to the original Magnetic value. The two-step pattern (lock True N, then rederive display) is sound.
- **N-F04 distinct-bearing fix is well-reasoned.** The comment in `northSwitch_doesNotChangeShanLabels` explicitly walks through why 50° would fail to detect the bug (still in 艮 sector) and why 60° (寅) is the correct choice. This level of test documentation reduces future maintenance risk.
- **N-F05 instrumented test is complete.** `ac23_northSwitch_overlayDisplaysConvertedBearing` specifies exact `LockState` emissions for each setup step, exact expected overlay strings, and the assertion that 山 labels are unchanged. The three-step Given/When/Then is sufficient for an implementer to write the test without further clarification.
- **`rederive_updates_displayBearings_notStoredTrueNorth`** (N-F08 resolution) now has four distinct assertions: two asserting True North values are preserved, two asserting display values are updated. This is the minimal sufficient test for the `rederive()` contract.

---

## Recommendation

**Approved with Minor Issues**

All five High and Medium prior findings (N-F01 through N-F05) are resolved. One new Medium finding (V3-F01) and three new Low findings (V3-F02 through V3-F04) were identified.

The new Medium finding (V3-F01) concerns the bearing passed to `LuopanView.setLockState()` in Magnetic North mode — the TSPEC passes `xiangBearing` (True North) rather than `displayXiangBearing`, with no specification of what behavior is expected and no test verifying the tick mark placement in Mag N mode. This is a specification gap that could produce a visual bug, but it does not affect the data integrity of the lock mechanism or the overlay display, and it may be intentional pending a FSPEC clarification on whether the gold tick mark tracks the True North or Magnetic North position in Mag N mode.

Given that:
- All previously blocking High findings are resolved
- The core lock/rederive contract is now internally consistent, tested, and correctly implemented
- The new Medium finding is isolated to a UI rendering specification gap (not a data correctness issue)
- The TSPEC is otherwise sufficient for implementation to proceed

This TSPEC may proceed to implementation, with V3-F01 tracked as a known open question to resolve with the product owner before or during the `drawGoldTickMark` implementation task. The Low findings may be addressed during implementation or in a subsequent TSPEC patch.
