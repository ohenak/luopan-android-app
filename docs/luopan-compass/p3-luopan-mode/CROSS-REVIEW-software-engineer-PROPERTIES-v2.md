# Cross-Review: Software Engineer — PROPERTIES-luopan-p3-luopan-mode (v2)

| Field | Value |
|-------|-------|
| Reviewer | Software Engineer |
| Document | PROPERTIES-luopan-p3-luopan-mode.md v1.1 |
| Date | 2026-04-25 |
| Iteration | 2 |
| Recommendation | Approved with Minor Issues |

---

## Prior Findings Resolution

All five mandatory findings from iteration 1 have been correctly resolved in v1.1.

| Prior ID | Severity | Status | Notes |
|----------|----------|--------|-------|
| F-01 | High | Resolved | PROP-02-017 now correctly states `rederive()` does NOT call `lock()`, writes only `displayXiangBearing`/`displayZuoBearing` via `current.copy()`, and the named test asserts `xiangBearing == 45.0f` (unchanged) alongside `displayXiangBearing == 48.5f` (updated). Contract is now accurate to TSPEC v1.2 §4.4. |
| F-02 | High | Resolved | PROP-02-026A–D added: (A) initialisation of `displayXiangBearing` to True North at lock time; (B) `rederive()` Mag-N formula for `displayXiangBearing`; (C) `rederive()` Mag-N formula for `displayZuoBearing`; (D) overlay reads `displayXiangBearing`/`displayZuoBearing`, not the True North storage fields. Each property has a named test and worked example. |
| F-03 | High | Resolved | PROP-02-026E added: `lockXiang()` in Magnetic North mode converts display bearing to True North (`bearingTrueN = (41.5f + (-3.5f) + 360f) % 360f = 38.0f`) before calling `ZuoXiangLock.lock()`. Negative assertion and concrete test specified. |
| F-04 | Medium | Resolved | PROP-02-018 now correctly attributes the declination conversion to `ZuoXiangLock.rederive()` (not the View layer). Negative assertion updated to prohibit View-layer arithmetic: "the View layer must NOT compute `xiangBearing ± declinationDeg` — it must read the pre-computed `displayXiangBearing` field." |
| F-05 | Medium | Resolved | PROP-02-025 rewritten to the single-writer model: one writer coroutine calls `lock()` 100 times; a concurrent reader reads `lockState`; asserts no torn state is ever observed. The negative assertion now documents that two concurrent writers are not supported. |
| F-07 | Medium | Resolved | PROP-08-067 tombstoned in-place with a "Removed in v1.1 (SE-F07)" notice. PROP-06-053 is the sole authoritative source for the ≤300 ms mode-entry invariant. |
| F-08 | Medium | Resolved | Gap analysis updated to explicitly reference N-F01 through N-F05. The "No uncovered gaps" claim is now supported by the full finding list including SE-F01–F05, SE-F07, PM-F01–F03, and N-F01–N-F05. |

Findings not mandated by the iteration-1 recommendation (F-06, F-09, F-10, F-11, F-12 — all Low) were carried forward without change; their status is noted in the New Findings section below.

---

## New Findings

### Low Priority

---

#### NF-01 — PROP-02-022 `clear()` assertion incomplete after v1.1 field additions

| Field | Detail |
|-------|--------|
| Severity | Low |
| Finding | PROP-02-022 asserts that `clear()` resets `isLockActive = false`, `xiangBearing = null`, `xiangMountain = null`, `zuoBearing = null`, `zuoMountain = null` — five fields. The v1.1 `LockState` data class (TSPEC v1.2 §4.4) has seven fields; the two fields added in this revision (`displayXiangBearing` and `displayZuoBearing`) are absent from the assertion list. `ZuoXiangLock.clear()` calls `_lockState.set(LockState())`, which correctly initialises all seven fields to null/false via the data-class default constructor, but the property's assertion language does not cover this. A test written from PROP-02-022 as-worded would not assert `displayXiangBearing == null` after `clear()`, leaving a gap in regression detection if `clear()` were ever changed to use `current.copy(isLockActive = false)` instead of a fresh `LockState()` (which would inadvertently preserve stale display bearing values). |
| Required change | Add `displayXiangBearing = null`, `displayZuoBearing = null` to the field list in PROP-02-022's Property row. |
| Section ref | PROP-02-022; TSPEC v1.2 §4.4 `LockState` data class and `clear()` implementation |

---

#### NF-02 — PROP-02-026E test name / test class mismatch

| Field | Detail |
|-------|--------|
| Severity | Low |
| Finding | PROP-02-026E's `Test` row names `ZuoXiangLockTest.lockXiang_magneticMode_convertsToTrueNorth`, but the `Test class` row correctly names `CompassViewModelSessionStateTest`. The test exercises `viewModel.lockXiang()` logic (a ViewModel method), not `ZuoXiangLock.lock()` directly. An implementor reading only the `Test` row would place the test in `ZuoXiangLockTest`, where it cannot invoke `viewModel.lockXiang()` without a full ViewModel setup. The TSPEC itself notes "the actual test may be in `CompassViewModelSessionStateTest`" (§12.1.3). The `Test` row in PROP-02-026E should be updated to name `CompassViewModelSessionStateTest.lockXiang_magneticMode_convertsToTrueNorth` to be consistent with the `Test class` row. |
| Required change | Change the test function qualifier in the `Test` row of PROP-02-026E from `ZuoXiangLockTest.lockXiang_magneticMode_convertsToTrueNorth` to `CompassViewModelSessionStateTest.lockXiang_magneticMode_convertsToTrueNorth`. |
| Section ref | PROP-02-026E; TSPEC v1.2 §12.1.3 note on test placement |

---

#### NF-03 — No property covers `rederive(isMagneticNorth = false)` reset path

| Field | Detail |
|-------|--------|
| Severity | Low |
| Finding | PROP-02-026B and PROP-02-026C cover `rederive(declinationDeg, isMagneticNorth = true)` — the conversion to Magnetic North. There is no property for the corresponding `rederive(declinationDeg, isMagneticNorth = false)` call (switching back to True North), which the TSPEC `rederive()` code handles by setting `displayXiang = xiangTrueN` (i.e., resetting display bearings to the stored True North values). Without a property for this path, a regression where `rederive()` applies declination even when `isMagneticNorth = false` would not be caught. The `onNorthTypeChanged()` ViewModel method (TSPEC §8.2) calls `rederive()` on every north-type toggle, so both directions of the toggle must be regression-proof. |
| Recommended addition | Add a property (suggested: PROP-02-026F) asserting: after `lock(45.0f)` + `rederive(-3.5f, true)` + `rederive(-3.5f, false)`, `displayXiangBearing == 45.0f` and `displayZuoBearing == 225.0f` (display bearings reset to True North). |
| Section ref | PROP-02-026B, PROP-02-026C; TSPEC v1.2 §4.4 `rederive()` (the `else xiangTrueN` branch) |

---

### Carried-Forward Low Findings (Unresolved from Iteration 1)

The following low-severity findings from iteration 1 were not mandated for correction and remain open. They are documented here for tracking completeness.

| Prior ID | Finding summary | Status |
|----------|----------------|--------|
| F-06 | PROP-04-043 labels test infrastructure as "FrameMetrics instrumentation" but FrameMetrics cannot assert per-frame allocation patterns or trigonometric call absence. The "no Paint/Path allocation in `onDraw`" invariant is not automatically assertable with FrameMetrics alone. | Open (Low) |
| F-09 | PROP-02-015 tests `ZuoXiangLock.lock()` in isolation, which only verifies that the stored value equals the passed-in `bearingTrueNorth` parameter — it cannot verify that `lockXiang()` in the ViewModel correctly converted from Magnetic before calling `lock()`. The True-North-storage invariant for the Magnetic-North case is only testable at ViewModel level (now correctly covered by PROP-02-026E). The property text in PROP-02-015 should cross-reference PROP-02-026E for the complete assertion, but this is a documentation clarity concern, not a coverage gap. | Open (Low) |
| F-10 | PROP-08-068 specifies "Android battery historian" as the test mechanism. Battery historian is an offline manual analysis tool with no automated assertion path. The property is not automatically assertable. Should be reclassified as a manual benchmark. | Open (Low) |
| F-11 | PG-01 group header declares `SectorLookupTest` as the test class, but PROP-01-009 through PROP-01-014 correctly override to `RingLabelProviderTest`. The group header is misleading for PROP-01-009–014 which test `RingLabelProvider` label content, not `SectorLookup` boundary logic. No coverage gap — just a readability issue. | Open (Low) |
| F-12 | PROP-04-039 asserts the gold tick mark is suppressed when `ringVisible[4] = false`. The TSPEC `onDraw` code (§6.1.2) calls `if (isLockActive) drawGoldTickMark(canvas)` independently of `if (ringVisible[4]) drawRing5Labels(canvas)` — there is no code path that suppresses the tick mark based on Ring 5 visibility. PROP-04-039's negative is asserting behavior that contradicts the TSPEC and may not be implemented. If tick-mark suppression when Ring 5 is hidden is desired, the TSPEC must add this guard explicitly. | Open (Low) |

---

## Positive Observations

- The three high-priority findings (F-01, F-02, F-03) are resolved with technically precise language. PROP-02-017's worked-example test (`xiangBearing == 45.0f` / `displayXiangBearing == 48.5f`) directly mirrors the canonical test in TSPEC v1.2 §12.1.3, eliminating any ambiguity between the property and its test implementation.
- PROP-02-026A through E form a coherent chain: lock initialises display bearings → rederive() updates them independently for each direction → the View layer reads display bearings, not storage fields → lockXiang() converts before storing. No link in this chain is missing.
- PROP-02-025's rewrite correctly distinguishes between the safe-publication guarantee (one writer, many readers, `AtomicReference`) and multi-writer safety (not required, not provided). The negative assertion in the property explicitly documents this contract, which will prevent a future implementor from replacing `AtomicReference` with a non-atomic field under the mistaken assumption the single-writer contract makes it safe.
- The gap analysis at the end of v1.1 is now accurate and complete: all SE, PM, and TE cross-review findings through two iterations are accounted for by name.
- PROP-07-065A (north reference switch while active, added to address PM-F01) correctly classifies the test as E2E (Instrumented) since it exercises the View-layer `updateZuoXiangOverlay()` function, which is not testable in a unit context.

---

## Recommendation

**Approved with Minor Issues**

The three high-severity findings (F-01, F-02, F-03) and all medium findings mandated in iteration 1 are fully resolved. The document is ready to be used as an implementation contract.

Three new low-priority findings (NF-01, NF-02, NF-03) should be addressed in a follow-on revision:

1. **NF-01 (Low):** Add `displayXiangBearing = null` and `displayZuoBearing = null` to the PROP-02-022 `clear()` assertion list to cover the two fields added in v1.1.
2. **NF-02 (Low):** Correct the test name in PROP-02-026E from `ZuoXiangLockTest.lockXiang_magneticMode_convertsToTrueNorth` to `CompassViewModelSessionStateTest.lockXiang_magneticMode_convertsToTrueNorth` to match the `Test class` row.
3. **NF-03 (Low):** Add a property (PROP-02-026F) covering the `rederive(isMagneticNorth = false)` path — the True North reset direction — to prevent regression if the `else` branch of `rederive()` is ever incorrectly modified.

These can be addressed in a future revision or as in-flight implementation notes without blocking implementation start.
