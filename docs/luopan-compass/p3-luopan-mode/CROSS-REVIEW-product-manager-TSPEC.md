# Cross-Review: Product Manager — TSPEC-luopan-p3-luopan-mode

| Field | Value |
|-------|-------|
| Reviewer | Product Manager |
| Document | TSPEC-luopan-p3-luopan-mode.md |
| Date | 2026-04-25 |
| Recommendation | Approved with Minor Issues |

---

## Summary

The TSPEC-luopan-p3-luopan-mode covers the architectural and component design for Phase 3 faithfully. All six rings are accounted for, the 坐向 lock derivation is correctly specified, sector-boundary rules are carried through from the REQ and FSPEC, and all five `OverallConfidence` states are mapped to explicit UI behaviours. The requirements-traceability table (§13) maps every P0/P1 requirement to a concrete implementation section.

Two Low-priority gaps were found: one acceptance criterion from the FSPEC has no coverage in the traceability table (AC-23, north-reference switch re-derivation), and the `LuopanState.INITIAL` companion value uses `"Magnetic N"` as the `northLabel` default where the REQ/FSPEC use the string `"Mag N"`. No P0 or P1 requirements were silently dropped or de-scoped.

---

## Findings

### High Priority

None.

---

### Medium Priority

None.

---

### Low Priority

| ID | Severity | Finding | Requirement ref |
|----|----------|---------|----------------|
| F-01 | Low | `LuopanState.INITIAL.northLabel` is set to `"Magnetic N"` (§5.1), but the REQ (§5.5, Scenario J) and FSPEC (Flow 3, AC-22) define the string as `"Mag N"`. If the View renders the INITIAL state before the first real state is emitted, the label string will differ from the specified value. The fix is trivial — change `"Magnetic N"` to `"Mag N"` in the companion object. | REQ §5.5, FSPEC Flow 3 |
| F-02 | Low | FSPEC AC-23 ("North Reference Switch Re-Derives Locked 向") is not listed in the requirements-traceability table (§13). The underlying implementation is present in §8.2 and `ZuoXiangLock.rederive()`, but the missing traceability entry means a reviewer cannot confirm coverage without reading prose. Recommend adding an explicit row to §13 for AC-23 / Scenario J-2. | FSPEC AC-23, REQ §12 Scenario J-2 |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | §6.2 `updateLockButton()` sets `btnLockXiang.isEnabled = canLock && !state.isLockActive`. This means once the lock is active, the button is disabled even though it should now be labelled "Clear 向" and remain tappable (to unlock). The REQ and FSPEC describe the button toggling between "Lock 向" (enabled when confidence permits) and "Clear 向" (always enabled when locked). Is the intent that `isEnabled` is always `true` when `isLockActive == true`, regardless of confidence? If so the condition should be `canLock || state.isLockActive`. Please clarify and fix before implementation. |
| Q-02 | The TSPEC defers the `SightingFragment` to Phase 5 with a stub destination in the nav graph (§1.2). The REQ §13 confirms this deferral. However, the navigation graph XML (§9.2) does not include the `dest_sighting` entry that REQ §9.1 lists. Is the Phase 5 stub destination intentionally omitted at this stage, or should a placeholder `fragment` element be added now to avoid a navigation-graph refactor later? |

---

## Positive Observations

- All six rings are fully specified with compile-time constant sector tables, exactly matching REQ §5.3 and FSPEC §4.1–4.6. No ring is omitted or merged.
- The 坐向 lock derivation formula (`zuoBearing = (xiangBearing + 180f) % 360f`) and both wrap-around test cases (向 = 270° → 坐 = 90°; 向 = 350° → 坐 = 170°) are explicitly covered in §4.4 and `ZuoXiangLockTest`, matching REQ §8 and FSPEC BR-06.
- All five `OverallConfidence` states (`HIGH`, `MODERATE`, `POOR`, `STABILIZING`, `SENSOR_ERROR`) are mapped to UI behaviour in §2.3, §11, and the traceability table, faithfully implementing BR-05 from the FSPEC.
- Session-only vs. persisted settings are cleanly separated (§7.1, §7.2): ring visibility and zoom are ViewModel-only; `luopan_show_romanization`, `luopan_show_my_language`, and `display_mode` are persisted — exactly matching REQ §5.7 and FSPEC BR-09.
- The lock-state preservation across mode switches is explicitly architected in §4.4 (`ZuoXiangLock` lives in `CompassViewModel`) and tested in `ModeSwitcherTest`, satisfying REQ §8 and FSPEC BR-10.
- The Ring Visibility BottomSheet accessibility alternative (three-dot overflow menu) is included in §6.4, matching REQ §7.1 and FSPEC Flow 5.
- The INITIAL state (§5.1) begins at confidence `POOR`, correctly preventing the lock button from being enabled before the first valid heading arrives.

---

## Recommendation

**Approved with Minor Issues**

F-01 (`"Magnetic N"` vs `"Mag N"`) and F-02 (missing traceability row for AC-23) are both Low severity and can be resolved without re-review. The implementation question in Q-01 about the `isEnabled` condition on the Clear 向 button should be confirmed and corrected by the SE author before the coding phase begins — while it reads as a Low issue in the TSPEC, it would produce a P0 bug at runtime (the user cannot unlock without confidence degrading and recovering).
