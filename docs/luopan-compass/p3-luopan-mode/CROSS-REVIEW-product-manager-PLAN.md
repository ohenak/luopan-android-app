# Cross-Review: Product Manager — PLAN-luopan-p3-luopan-mode

| Field | Value |
|-------|-------|
| Reviewer | Product Manager |
| Document | PLAN-luopan-p3-luopan-mode.md |
| Date | 2026-04-25 |
| Recommendation | Approved with Minor Issues |

## Summary

The PLAN covers all P0 requirements and user stories active in Phase 3 (US-02, US-07, US-12). All six concentric rings, the numerical readout panel, the 坐向 lock, navigation migration, localization toggles, ring visibility, and pinch-to-zoom are accounted for in explicitly named tasks with traceable acceptance criteria. The batch ordering is logical from a user-delivery perspective: domain LUTs first (Batch 1), then ViewModel state (Batch 2), then navigation/fragment shell (Batch 3), then visible UI components (Batch 4), then interactions (Batch 5), then localization (Batch 6), and finally integration tests (Task 7.1).

No P0 requirement is missing from the task list. The findings below are low-priority issues related to gaps in acceptance-criteria specificity, a missing explicit test case for one P0 scenario, and a risk that should be flagged in the DoD.

---

## Findings

### High Priority

No high-priority findings.

### Medium Priority

No medium-priority findings.

### Low Priority

| ID | Finding | Requirement Ref |
|----|---------|----------------|
| F-01 | **Scenario B canonical readout not tested as an end-to-end instrumented test.** Task 4.2 acceptance (`ac03_readout_shows_wu_at_180_high`) only asserts the `山` character field. The REQ Scenario B canonical readout at 180°/High/True N requires all six fields in canonical order: "午 (Wǔ) · 午 (Wǔ) · ☲ 離 南 · 180.0° · True N · 壬午分金 · High". No single task's acceptance criteria verifies the full six-field canonical string end-to-end. | REQ §12 Scenario B; REQ §5.5 |
| F-02 | **Scenario J north-reference switch during active 坐向 lock (lock re-derivation) has no dedicated instrumented test.** Task 4.3 covers AC-23 (magnetic bearing display) and the tick mark fix (V3-F01), and Task 2.3 covers `lockXiang_under_magnetic_north_stores_true_north_bearing`, but no acceptance criterion explicitly verifies that switching north reference while locked updates the overlay 山 labels in real time (Scenario J-2: bearing crosses a 山 boundary). Task 7.1 and the DoD do not list this scenario. | REQ §12 Scenario J; REQ §8 (坐向 lock workflow — north-reference switch) |
| F-03 | **Scenario F (localization — system locale override) is covered by `ac12_default_language_english_locale` (en locale) in Task 6.1, but the `ja` locale case from Scenario F is not listed as an acceptance criterion in any task.** The REQ explicitly requires a `ja` locale test. | REQ §12 Scenario F (ja locale case) |
| F-04 | **OQ-P3-01 (六十分金 practitioner validation) appears only in the Definition of Done as a notice, but the PLAN has no task or process step that tracks resolution of this open question before release.** The REQ flags this as a mandatory sign-off before shipping the 分金 ring labels. Without a task or explicit gate, the DoD checkbox ("labels not final") may be overlooked during delivery sign-off. | REQ §5.6 (OQ-P3-01 notice); REQ §14 (OQ-P3-01 open question) |
| F-05 | **Scenario A legibility requirement is not reflected in any task's acceptance criteria.** The REQ requires all 12 地支 characters (Ring 4), all 24 山 characters (Ring 5), and all 60 分金 labels (Ring 6) to be readable by a user with 20/40 corrected vision at 30 cm on a 5-inch 1080p screen. Task 4.1's acceptance criteria include a visual inspection note but do not reference the 20/40 corrected vision standard or the minimum font sizes from REQ §6. | REQ §6 (legibility requirement); REQ §12 Scenario A |
| F-06 | **REQ-NFR-LUOPAN-03 (battery ≤ 7% per hour) has no corresponding task, measurement step, or DoD entry.** REQ-NFR-LUOPAN-01, -02, and -04 are all represented in the DoD. REQ-NFR-LUOPAN-03 is referenced only in Task 4.1's files-to-read list (REQ §11) but has no acceptance criterion or DoD checkbox that verifies it. | REQ §11 REQ-NFR-LUOPAN-03 |

---

## Recommendation

**Approved with Minor Issues.** All P0 requirements and active Phase 3 user stories are covered. The batch ordering correctly delivers the domain layer first, the ViewModel second, and user-visible surfaces last — this is the right sequencing from a user-delivery perspective. The 坐向 lock workflow, the full six-ring dial, and the localization toggles each map to clearly defined tasks with product-meaningful acceptance criteria.

The six low-priority findings should be addressed before implementation begins to avoid test gaps surfacing late:

- F-01: Extend Task 4.2 (or Task 7.1) acceptance to assert the full six-field canonical readout string from REQ §5.5 Scenario B.
- F-02: Add a test case to Task 4.3 or Task 7.1 that verifies 坐向 overlay 山 labels update correctly when north reference switches mid-lock and the adjusted bearing crosses a 山 boundary (REQ Scenario J-2).
- F-03: Add the `ja` locale variant of `ac12` to Task 6.1 acceptance criteria.
- F-04: Add an explicit delivery gate or a named task (even a lightweight one) that requires OQ-P3-01 practitioner sign-off before the 分金 ring labels are considered done. The current DoD checkbox is insufficient as a process control.
- F-05: Add a reference to the REQ §6 legibility standard (20/40 vision at 30 cm, minimum font sizes) in Task 4.1 acceptance criteria, even if verified by manual inspection.
- F-06: Add a DoD checkbox for REQ-NFR-LUOPAN-03 (battery ≤ 7%/hr measured via Android battery historian) to match the treatment given to NFRs -01, -02, and -04.
