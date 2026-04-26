# Cross-Review: Product Manager — TSPEC-luopan-p3-luopan-mode (v2)

| Field | Value |
|-------|-------|
| Reviewer | Product Manager |
| Document | TSPEC-luopan-p3-luopan-mode.md v1.1 |
| Date | 2026-04-25 |
| Iteration | 2 |
| Recommendation | Approved |

---

## Prior Findings Resolution

### Q-01 (P0 bug): Lock button condition `canLock || state.isLockActive`

**Status: Resolved.**

The v1.1 TSPEC corrects the condition in `updateLockButton()` (§6.2) from `canLock && !state.isLockActive` to `canLock || state.isLockActive`. The "Clear 向" button is now always enabled when a lock is active, regardless of confidence. The fix is documented in a prominent inline comment attributing it to PM-Q01, and a dedicated instrumented test (`lock_button_enabled_when_lock_active_allowsClearing`) is added in §12.5 to prevent regression. The fix also appears in the requirements-traceability table (§13, row `PM-Q01`). Resolution is complete and correctly implemented.

---

### F-01 (Low): `INITIAL.northLabel` should be `"Mag N"` not `"Magnetic N"`

**Status: Resolved.**

`LuopanState.INITIAL.northLabel` (§5.1) now reads `"Mag N"`, matching the canonical string used by REQ §5.5 (Scenario J), FSPEC Flow 3 / AC-22, and all formatted readout examples. Resolution is complete.

---

### F-02 (Low): AC-23 missing from requirements-traceability table (§13)

**Status: Resolved.**

The traceability table (§13) now includes an explicit row for AC-23 (`north-reference switch while locked`), mapping it to §4.4 and §8.2 (`ZuoXiangLock.rederive()` + display conversion in View) with the note that 山 labels are derived from the True North bearing and are invariant to north-type switches. Resolution is complete.

---

### Q-02 (Open): `dest_sighting` stub in nav graph

**Status: Not addressed in v1.1.**

The navigation graph (§9.2) still omits the `dest_sighting` destination that REQ §9.1 explicitly lists. The TSPEC §1.2 table marks it as *(deferred)* rather than including a placeholder element. This was flagged as a question in the v1 review. It carries no functional impact for Phase 3 — the Phase 5 deferral is correctly documented in REQ §13. No new finding is raised; this remains an open product decision about whether to stub the destination now or later.

---

## New Findings

### F-03 (Low): Internal contradiction between §8.2 and the ZuoXiangLockTest AC-23 comment

**Requirement ref:** FSPEC §4d, AC-23, REQ §8 Scenario J-2

In `ZuoXiangLockTest` (§12.1.3), the test `rederive_northSwitch_doesNotChangeStoredTrueNorthBearing` contains this comment:

> "Simulate: north type switches to Mag N — ViewModel does NOT call rederive() because the stored True N bearing is invariant. Only the display layer converts."

This comment is inconsistent with `CompassViewModel.onNorthTypeChanged()` (§8.2), which **does** call `zuoXiangLock.rederive(currentBearing)` on every north-type switch. The test verifies the correct behavior (stored True North bearing is invariant) but the comment gives an incorrect explanation of why it is invariant.

The behavior is functionally correct. Because `heading_deg` is always expressed in True North (per §2.2 and the sensor pipeline), passing `heading_deg.toFloat()` to `rederive()` after a north-type switch re-locks the same True North bearing — the stored value does not change in practice. The test assertion (`xiangBearing == 45.0f`) will pass, but for the wrong stated reason.

The risk is that a developer reading the test comment will believe `rederive()` is never called on a north-type switch, and may later remove the `onNorthTypeChanged()` call, breaking AC-23 for the case where declination or a moving heading causes the True North bearing to differ between the pre- and post-switch evaluations.

**Recommended fix:** Correct the test comment to accurately reflect the call path: `rederive()` IS called with the current True North bearing; the stored value is effectively invariant because `heading_deg` is always True North. Alternatively, rename the test to more precisely describe what is being tested.

---

## Positive Observations

- The v1.1 revision notes in the document header are comprehensive and accurately enumerate every PM, TE, and open-issue fix addressed — making the change history auditable without diffing.
- The PM-Q01 fix (lock button P0 bug) is handled with exemplary thoroughness: inline comment explains the bug, the corrected code is present, a dedicated regression test is added (`lock_button_enabled_when_lock_active_allowsClearing`), and the traceability table entry references the fix.
- The AC-23 / north-reference switch behavior is now fully traced: §4.4 (`ZuoXiangLock.rederive()` docstring), §8.2 (`onNorthTypeChanged()`), §12.1.2 (`LuopanStateMapperTest.northSwitch_doesNotChangeShanLabels`), §12.1.3 (`ZuoXiangLockTest.rederive_northSwitch_*`), §12.5 (`LuopanFragmentTest.ac22_northTypeSwitch_updatesReadoutImmediately`), and the §13 traceability row.
- Thread-safety (TE-F04) is addressed with `AtomicReference<LockState>` and a documented single-writer contract. The concurrency test (`concurrentLockClear_doesNotProduceInconsistentState`) in §12.1.3 is a good addition.
- The Ring 2 Fuxi / Earlier Heaven LUT correction (TE-F01) is clearly documented with a diff note in §4.1, and a dedicated test (`ring2_sector4_is_qian_south`) is added to catch regressions.
- All acceptance criteria (AC-03 through AC-29) now have unit-level, mapper-level, or instrumented-level test coverage per the expanded §12 test strategy. The test layering rationale in §12.5 is clear and appropriate.

---

## Recommendation

**Approved**

All three prior findings (Q-01 P0 bug, F-01 northLabel string, F-02 missing traceability) are fully resolved. The single new finding (F-03) is Low severity — the behavior is correct; only a test comment is misleading. It can be addressed without re-review during the implementation phase. The TSPEC v1.1 is ready to hand off to implementation.
