# Cross-Review: Software Engineer — PROPERTIES-luopan-p3-luopan-mode

| Field | Value |
|-------|-------|
| Reviewer | Software Engineer |
| Document | PROPERTIES-luopan-p3-luopan-mode.md |
| Date | 2026-04-25 |
| Recommendation | Need Attention |

## Summary

The PROPERTIES document is thorough and well-structured for a v1.0 first draft. Coverage of the 76 properties is broad, the test-level assignments are largely sound, and the gap analysis is honest. However, the document was authored against TSPEC v1.1 and the repository now contains TSPEC v1.2, which introduced significant architectural changes to `ZuoXiangLock` and `lockXiang()` in the N-F01–N-F05 iteration. Several PG-02 properties describe the old (incorrect) contract, a new `LockState` field (`displayXiangBearing` / `displayZuoBearing`) is entirely absent, and a new test (`lockXiang_magneticMode_convertsToTrueNorth`) is not covered by any property. These gaps mean the PROPERTIES document would not catch the lockXiang bearing-conversion bug (N-F02) or the `rederive()` contract violation (N-F01) — exactly the classes of bug the TSPEC cross-review previously identified.

---

## Findings

### High Priority

---

#### F-01 — PROP-02-017 describes the wrong `rederive()` contract (N-F01 regression)

| Field | Detail |
|-------|--------|
| Severity | High |
| Finding | PROP-02-017 states that `rederive(trueBearing)` "calls `lock(trueBearing)` with the new True North bearing." This is the *old* v1.1 design that was explicitly fixed in TSPEC v1.2 (N-F01). The corrected contract is: `rederive(declinationDeg, isMagneticNorth)` does NOT call `lock()`; it only updates `displayXiangBearing` and `displayZuoBearing` via `_lockState.set(current.copy(...))`, leaving `xiangBearing` and `zuoBearing` (True North) untouched. A test written from PROP-02-017 as written would exercise an implementation that overwrites `xiangBearing` — exactly the bug N-F01 fixed. |
| Section ref | PROP-02-017, TSPEC v1.2 §4.4 (rederive() contract, N-F01/N-F03) |

---

#### F-02 — `displayXiangBearing` / `displayZuoBearing` fields are absent from all properties

| Field | Detail |
|-------|--------|
| Severity | High |
| Finding | TSPEC v1.2 introduced two new `LockState` fields: `displayXiangBearing: Float?` and `displayZuoBearing: Float?`. These are the fields the mapper and overlay read for display — `xiangBearing` / `zuoBearing` are now True-North-only storage fields. No property in PG-02 or PG-03 covers: (a) that `rederive()` correctly computes `displayXiangBearing = (xiangTrueN - declinationDeg + 360f) % 360f` for Magnetic North mode, (b) that the mapper reads `lockState.displayXiangBearing` (not `lockState.xiangBearing`) for overlay rendering, or (c) that `displayXiangBearing` and `displayZuoBearing` are initialised to the True North values at lock time. The TSPEC's canonical AC-23 test (`rederive_northSwitch_doesNotChangeStoredTrueNorthBearing`) asserts both the True North invariant AND `displayXiangBearing == 48.5f` — the latter assertion has no corresponding property. |
| Section ref | PROP-02-017, PROP-02-018, PROP-03-032, TSPEC v1.2 §4.4, §5.1 |

---

#### F-03 — No property covers `lockXiang()` Mag-N → True-N conversion (N-F02)

| Field | Detail |
|-------|--------|
| Severity | High |
| Finding | TSPEC v1.2 §8.1 specifies that `lockXiang()` must convert the display bearing to True North before calling `zuoXiangLock.lock()` when `northType == MAGNETIC` (`True North = displayBearing + declinationDeg`). This is the N-F02 bug fix. No property in PG-02 or PG-09 asserts this behaviour. The TSPEC test `lockXiang_magneticMode_convertsToTrueNorth` (new in v1.2) — which verifies that a 41.5° Magnetic North display bearing with declination −3.5° stores a 38.0° True North value — has no property. Without this property the test will not be written and the regression will recur. |
| Section ref | PROP-09-070 (nearest), TSPEC v1.2 §8.1 `lockXiang()` code, §12.1.3 N-F02 test |

---

### Medium Priority

---

#### F-04 — PROP-02-018 is partially correct but misattributes responsibility

| Field | Detail |
|-------|--------|
| Severity | Medium |
| Finding | PROP-02-018 states "only display conversion (`displayBearing = xiangBearing_trueN ± declinationDeg`) changes in the View layer." In TSPEC v1.2 this conversion is performed inside `ZuoXiangLock.rederive()` and stored in `displayXiangBearing` — not in the View layer. The View layer (`LuopanFragment.updateZuoXiangOverlay()`) reads `lockState.displayXiangBearing` directly and performs no arithmetic. The negative assertion "ZuoXiangLock must NOT apply declination to the stored `xiangBearing` field" is correct, but the positive side ("View layer performs the conversion") is now architecturally wrong. A test derived from this property could incorrectly assert that the View layer does arithmetic when in fact it should be a pass-through read. |
| Section ref | PROP-02-018, TSPEC v1.2 §4.4 ("Overlay rendering"), §6.2 `updateZuoXiangOverlay()` |

---

#### F-05 — PROP-02-025 thread-safety test is not implementable as specified

| Field | Detail |
|-------|--------|
| Severity | Medium |
| Finding | PROP-02-025 specifies "100 iterations of alternating lock/clear from two coroutines on `Dispatchers.Default`." The TSPEC thread-safety contract (§4.4) establishes a single-writer guarantee: all mutators are always called from `viewModelScope`, so only one coroutine at a time calls `lock()` or `clear()`. Testing concurrent calls from two coroutines simultaneously contradicts the stated invariant — the implementation is not designed to be multi-writer safe, only to safely publish state from one writer to many readers via `AtomicReference`. The correct test is a read-while-write test: one writer coroutine calls `lock()` repeatedly while a reader coroutine concurrently reads `lockState`, verifying no torn read is ever observed. The property should be reworded accordingly. |
| Section ref | PROP-02-025, TSPEC v1.2 §4.4 (thread-safety contract, single-writer) |

---

#### F-06 — PROP-04-043 (`onDraw` pre-computation) is not automatically assertable at Integration level

| Field | Detail |
|-------|--------|
| Severity | Medium |
| Finding | PROP-04-043 is listed as Integration (FrameMetrics instrumentation) but asserts an implementation detail: "onDraw must NOT allocate new `Paint` objects, compute trigonometric functions, or allocate `Path` objects." FrameMetrics measures frame duration but cannot introspect allocation patterns or trigonometric calls. The correct test infrastructure for this property is either: (a) an Android Profiler / `Debug.startAllocTracking()` test that counts allocations per-frame, or (b) a code-review only assertion (not an automated test). As written it is not automatically assertable with `FrameMetrics` alone. The TSPEC performance budget (≤16 ms per frame) is separately covered by PROP-08-065 — the "no allocations" invariant is implementation guidance, not a runtime-checkable property. |
| Section ref | PROP-04-043, TSPEC v1.2 §10.1 |

---

#### F-07 — PROP-06-053 and PROP-08-067 are duplicate properties for the same invariant

| Field | Detail |
|-------|--------|
| Severity | Medium |
| Finding | PROP-06-053 states "first complete dial frame rendered within 300 ms" (E2E, `ModeSwitcherTest`) and PROP-08-067 states "first dial render completes in ≤ 300 ms" (E2E, `ModeSwitcherTest`). These are the same property mapped to the same test class, test level, and REQ/AC. At the unit of test infrastructure there is one test covering both. One of these properties should be removed (or one consolidated to be distinct — e.g., PROP-06-053 could focus exclusively on the navigation action completing and the fragment becoming visible, while PROP-08-067 focuses on the first complete painted frame), or the duplication should be explicitly acknowledged as a deliberate cross-reference rather than two independent tests. |
| Section ref | PROP-06-053, PROP-08-067 |

---

#### F-08 — Gap Analysis claims "No uncovered gaps" but misses N-F01–N-F05 findings from TSPEC v1.2

| Field | Detail |
|-------|--------|
| Severity | Medium |
| Finding | The gap analysis table at the bottom of the document does not reference the N-F01–N-F05 cross-review findings from TSPEC v1.2 (the TE cross-review iteration 2). TE-F01–TE-F04 and PM-Q01 are covered, but N-F01 (`rederive()` contract), N-F02 (`lockXiang()` Mag-N conversion), N-F03 (no `lock()` call from `rederive()`), N-F04 (distinct bearing test), and N-F05 (AC-23 overlay instrumented test) are entirely absent. The document's claim of complete coverage is therefore incorrect. |
| Section ref | Gap Analysis section (lines 959–1005), TSPEC v1.2 revision note (N-F01–N-F05) |

---

### Low Priority

---

#### F-09 — PROP-02-015 test does not specify how "True North" is verified

| Field | Detail |
|-------|--------|
| Severity | Low |
| Finding | PROP-02-015 states `xiangBearing` is "always stored as a True North Float bearing, regardless of the north type active at the time of locking." The test class is `ZuoXiangLockTest`. However, `ZuoXiangLock.lock()` accepts a `bearingTrueNorth: Float` parameter — so a test of `ZuoXiangLock` in isolation can only verify that the stored value equals the passed parameter; it cannot verify that `lockXiang()` in the ViewModel correctly converts from Magnetic North before calling `lock()`. The actual True-North-storage invariant for the Magnetic-North case is only testable at the ViewModel level (now covered by N-F02 test in the TSPEC). The property should clarify that the ViewModel-level conversion is the critical assertion and reference F-03 above. |
| Section ref | PROP-02-015, TSPEC v1.2 §8.1 `lockXiang()` |

---

#### F-10 — PROP-08-068 (battery test) has no assertion mechanism defined

| Field | Detail |
|-------|--------|
| Severity | Low |
| Finding | PROP-08-068 references "Android battery historian on API-26 mid-range hardware during 1-hour continuous luopan display." Battery historian is an offline manual analysis tool — it cannot be run as an automated CI test. There is no automated assertion mechanism for ≤7%/hr. The property is not automatically assertable. It should either be reclassified as a manual benchmark (non-automated property) with an explicit annotation, or replaced with a `PowerManager`/`BatteryManager` continuous-drain delta measurement over a shorter duration extrapolated to 1 hour. |
| Section ref | PROP-08-068, TSPEC v1.2 §10.4 |

---

#### F-11 — PROP-01-009 is assigned to wrong test class

| Field | Detail |
|-------|--------|
| Severity | Low |
| Finding | PROP-01-009 tests `RingLabelProvider.ring3Label()` for correct label content. The test class is listed as `RingLabelProviderTest` (correct), but the property is placed inside group PG-01 (Sector Assignment) with test class `SectorLookupTest` stated in the group header. The group header says "Test class: `SectorLookupTest`" while PROP-01-009 itself correctly overrides to `RingLabelProviderTest`. This is a layout inconsistency that could mislead an implementor placing the test in the wrong file. PROP-01-009 through PROP-01-014 all test `RingLabelProvider` and belong in a separate sub-group (or PG-01 should be named "Sector Assignment and Ring Labels") with the header updated. |
| Section ref | PROP-01-009, PG-01 group header |

---

#### F-12 — PROP-04-039 visibility condition is under-specified

| Field | Detail |
|-------|--------|
| Severity | Low |
| Finding | PROP-04-039 states "when `ringVisible[4] = false` (Ring 5 hidden), the tick mark is also not drawn." The TSPEC (`drawGoldTickMark` code in §6.1.4) draws the gold tick mark inside the `if (isLockActive) drawGoldTickMark(canvas)` guard, which executes regardless of `ringVisible[4]`. There is no code path in the TSPEC that suppresses the tick mark when Ring 5 is hidden — the tick mark is on the Ring 5 outer edge, so hiding Ring 5 visually obscures the ring labels but there is no explicit tick-mark suppression logic specified. The property asserts behaviour that may not be required or implemented. If this is a desired behaviour, it needs a corresponding implementation spec in the TSPEC; if it is not desired, the negative test in PROP-04-039 should be removed. |
| Section ref | PROP-04-039, TSPEC v1.2 §6.1.4 `drawGoldTickMark()` |

---

## Positive Observations

- The 76-property count and 9-group structure provide excellent traceability: every BR, AC, and NFR from the REQ has at least one property. The gap analysis table is a useful audit artefact.
- PG-01 (Sector Assignment) properties are precise and directly implementable as JUnit tests with no ambiguity — the exact float boundary values (337.4f, 337.5f, etc.) eliminate discretion from test implementation.
- PG-02 properties correctly identify `ZuoXiangLock` as the domain object under test (not the ViewModel) for the majority of lock invariants, keeping unit tests fast and Android-free.
- PROP-02-025 (thread-safety) and PROP-01-001 (exhaustive coverage / no IllegalStateException) are correctly identified as non-trivial properties that require explicit test scaffolding beyond simple equality assertions.
- The PG-09 group correctly separates the lock UI behaviour (button enable state, label text, overlay visibility, gold tick mark) from the domain logic in PG-02, enabling integration tests to verify UI-layer concerns independently.
- The `rederive()` test contract in TSPEC v1.2 §12.1.3 (`rederive_northSwitch_doesNotChangeStoredTrueNorthBearing`) is fully self-consistent and would be directly expressible as a PROPERTIES entry once PROP-02-017 is corrected.

---

## Recommendation

**Need Attention**

Three high-priority findings must be resolved before this document is used as an implementation contract:

1. **F-01:** Rewrite PROP-02-017 to describe the correct TSPEC v1.2 `rederive(declinationDeg, isMagneticNorth)` contract: it does NOT call `lock()`, it only updates `displayXiangBearing` and `displayZuoBearing` via `current.copy(...)`.

2. **F-02:** Add new properties (suggested: PROP-02-017a and PROP-03-033a) covering the `displayXiangBearing` / `displayZuoBearing` fields: their initialisation at lock time, their update by `rederive()`, and the mapper's use of them for overlay display rather than the raw `xiangBearing` field.

3. **F-03:** Add a new property in PG-02 or PG-09 (suggested: PROP-09-070a) for `lockXiang()` Mag-N → True-N conversion: given `heading_deg = 41.5` Magnetic with `declination = −3.5f`, the stored `xiangBearing` must be `38.0f` True North.

Additionally, F-04 (PROP-02-018 misattributes View-layer conversion), F-05 (PROP-02-025 thread-safety test contradicts the single-writer contract), F-07 (PROP-06-053 and PROP-08-067 duplication), and F-08 (gap analysis omits N-F01–N-F05) should be corrected in the same revision.
