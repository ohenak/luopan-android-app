# Cross-Review: software-engineer — PROPERTIES

**Reviewer:** software-engineer
**Document reviewed:** docs/luopan-compass/p4-bearing-history/PROPERTIES-luopan-p4-bearing-history.md (v0.4-draft)
**Date:** 2026-04-27
**Iteration:** 4

---

## v3 Finding Resolution Assessment

| v3 ID | Severity | v3 Finding | v0.4 Status | Verdict |
|-------|----------|-----------|-------------|---------|
| F-01 | Medium | TSPEC §5.5 declares `List<Sensor>` contradicting PROP-SENSOR-037's `List<SensorInfo>` mandate | Prerequisite note added to PROP-SENSOR-037: se-author must correct TSPEC §5.5 (define `SensorInfo`, change parameter type, add `buildJson()` mapping) before Phase C task C-1. PROPERTIES contract declared authoritative. §6.1 entry updated to remove the incorrect "already confirmed" claim. | **Closed with residual note — see F-01 below (Low).** The prerequisite note is correct, explicit, and follows the established PROP-CAL-036/037 IDriftDetector pattern. The remaining Low concern is the absence of a gating PLAN task analogous to B-3. |
| F-02 | Low | PROP-HIST-072 retained a source-inspection clause not verifiable at runtime | Source-inspection clause removed. Pass condition now: (1) structural `is ListAdapter` assertion; (2) runtime `RecordingBearingAdapter.submitList()` → `notifyDataSetChangedCount == 0`. Robolectric requirement noted. | **Closed** — Pass condition is now fully runtime-verifiable and consistent with the project's test double conventions. |
| F-03 | Low | PROP-CAL-019b did not specify how `interferenceState = WARNING` is achieved in the instrumented test | Pass condition rewritten. Injection mechanism: `FakeDriftDetector(nextEvent = null)` via custom `ViewModelProvider.Factory`. Primary assertion: drift banner `GONE` — unconditional and deterministic regardless of live sensor state. DriftDetector unit behavior and ViewModel TRIGGERED-to-banner logic explicitly delegated to PROP-CAL-031 and PROP-CAL-018/019 respectively. | **Closed** — Injection mechanism is now clear, implementable, and correctly scoped. The primary assertion is deterministic. |

---

## Findings

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| F-01 | Low | **PROP-SENSOR-037's prerequisite note lacks a gating PLAN task analogous to PLAN B-3 for IDriftDetector.** For `IDriftDetector`, PLAN task B-3 explicitly creates the interface and task B-2a writes the red contract test — these are hard gates in the dependency chain that prevent Phase B from completing without the interface existing. For `SensorInfo`, the PROP-SENSOR-037 prerequisite note correctly directs the se-author to correct TSPEC §5.5, but there is no PLAN task that mandates this correction and no failing test at C-1 time that would fail to compile unless the TSPEC is corrected first. (The C-1 failing test will be written against PROP-SENSOR-037, which mandates `SensorInfo` — but the compilation failure only manifests if the implementation (TSPEC-driven) uses `List<Sensor>` and the test uses `SensorInfo`. Since TDD means the test is written before the implementation, this mismatch will surface at the green-phase step of C-1, not before.) The risk is low because: (a) PROP-SENSOR-037 is explicit that the PROPERTIES contract is authoritative over the TSPEC; (b) the prerequisite note precisely enumerates the three TSPEC corrections required; (c) TDD will surface the mismatch before C-1 can be marked green. However, unlike PLAN B-3 which is a sequenced hard dependency, this is a soft prerequisite enforced only by process discipline. Recommendation: the se-author should correct TSPEC §5.5 as a standalone commit before Phase C begins — no PROPERTIES change needed. | PROP-SENSOR-037; TSPEC §5.5 |

---

## Questions

*None.*

---

## Prior Iteration Summary

All High findings (v1 F-01/F-02/F-03; v2 F-01/F-02/F-03) and all Medium findings (v1 F-04 through F-10 with the exception of v1-F-10/v2-F-07 which is now resolved via v0.4 prerequisite note; v2 F-04 through F-08) are confirmed closed. v3 F-01/F-02/F-03 are all resolved in v0.4. The only remaining item is the Low process gap noted above (missing PLAN task for TSPEC §5.5 correction).

---

## Positive Observations

- **PROP-SENSOR-037 prerequisite note mirrors the proven IDriftDetector pattern.** The note explicitly names the three required TSPEC §5.5 corrections (define `SensorInfo`, change parameter type, add `buildJson()` mapping), declares the PROPERTIES contract authoritative, and ties the gate to Phase C task C-1. This is the correct and minimal response to a TSPEC inconsistency that the PROPERTIES document cannot unilaterally fix.

- **PROP-HIST-072 pass condition is now fully runtime-verifiable.** Using `RecordingBearingAdapter.submitList()` → `notifyDataSetChangedCount == 0` is consistent with PROP-HIST-022's established `RecordingBearingAdapter` pattern and tests the same invariant (no `notifyDataSetChanged()` call) via a runtime assertion rather than a source check. Adding the Robolectric note acknowledges the `ListAdapter` dependency correctly.

- **PROP-CAL-019b injection mechanism is now deterministic and correctly scoped.** The `FakeDriftDetector(nextEvent = null)` approach correctly tests the user-observable outcome (drift banner `GONE`) without requiring control over the live `InterferenceDetector` or sensor pipeline. Delegating the DriftDetector unit behavior to PROP-CAL-031 and the ViewModel TRIGGERED-to-banner wiring to PROP-CAL-018/019 creates a clean separation of concerns across the property set.

- **§6.1 resolution history is accurate.** The v3 F-01 entry correctly acknowledges that the prior "already confirmed" claim was wrong and documents the actual correction made (prerequisite note added). The §6.1 table now provides an accurate audit trail through all four review iterations.

---

## Recommendation

**Approved with Minor Issues**

> **F-01 (Low):** PROP-SENSOR-037's prerequisite note correctly identifies the TSPEC §5.5 correction required and declares the PROPERTIES contract authoritative. The only residual gap is that no PLAN task gates this correction the way PLAN B-3 gates IDriftDetector creation. TDD will surface the TSPEC mismatch at C-1 green phase; the risk of silent failure is low. No PROPERTIES change needed — the se-author should correct TSPEC §5.5 as a standalone commit before Phase C begins.
>
> All High and Medium findings across four iterations are resolved. This PROPERTIES document may proceed to implementation.
