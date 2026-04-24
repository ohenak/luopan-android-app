# Luopan App — Iterative Delivery Plan

| Field | Value |
|-------|-------|
| **Parent REQ** | [REQ-luopan-compass v0.2-draft](REQ-luopan-compass.md) |
| **Date** | 2026-04-23 |
| **Status** | Draft |

The monolithic REQ has been decomposed into five delivery phases. Each phase ships a **usable app state** and is implemented in full before the next phase begins. Engineering works from the phase REQ document for scope + from the master REQ for detailed requirement specs.

---

## Phase Map

| Phase | REQ Document | Goal / Usable State | New Personas Served | MVP? |
|-------|-------------|---------------------|---------------------|------|
| **1** | [REQ-luopan-p1-core-compass](REQ-luopan-p1-core-compass.md) | Magnetic compass with calibration and interference detection | Persona 4 (general consumer), Persona 3 (hiker, partial) | **Yes — minimum shippable** |
| **2** | [REQ-luopan-p2-true-north-capture](REQ-luopan-p2-true-north-capture.md) | True north via WMM2025, magnetic declination display, bearing save | Persona 2 (surveyor) | Yes |
| **3** | [REQ-luopan-p3-luopan-mode](REQ-luopan-p3-luopan-mode.md) | Full luopan display: 六環 rings, 坐向 lock, Traditional Chinese terminology | Persona 1 (feng shui practitioner) | Yes |
| **4** | [REQ-luopan-p4-bearing-history](REQ-luopan-p4-bearing-history.md) | Bearing history screen, recalibration prompts, record interference flags | All — deepens utility | No |
| **5** | [REQ-luopan-p5-sighting-polish](REQ-luopan-p5-sighting-polish.md) | Sighting mode, grid north, data export, UX polish | Persona 3 (sighting mode), all | No |

---

## Requirement Distribution by Phase

| Domain | Phase 1 | Phase 2 | Phase 3 | Phase 4 | Phase 5 |
|--------|---------|---------|---------|---------|---------|
| REQ-SENSOR | 01–06 | — | — | 07 | — |
| REQ-CAL | 01–04 | — | — | 05 | 06 |
| REQ-DETECT | 01–04 | — | — | 05 | — |
| REQ-NORTH | — | 01–04 | — | — | — |
| REQ-DISPLAY | 01, 02, 03, 10, 11 | — | 04, 05, 06 | — | 07, 08, 09, 12 |
| REQ-CAPTURE | — | 01, 02, 04, 06 | — | 03 | 05 |
| REQ-DECL | — | 01, 02 | — | — | 03 |
| REQ-L10N | 01, 03 | — | 02 | — | — |
| REQ-LUOPAN | — | — | 01, 02 | — | — |
| REQ-NFR | 01–07, 09 | 08 | — | — | — |

**Total requirements:** 57 across 5 phases (29 / 11 / 6 / 4 / 7)

---

## Cross-cutting Sections (Apply to All Phases)

These sections of the master REQ apply across all phases and are not duplicated in phase documents:

| Section | Applies from Phase |
|---------|-------------------|
| §8 Accuracy Specification | Phase 1 |
| §9 Calibration UX Requirements | Phase 1 |
| §11 Edge Cases and Failure Modes | Phase 1 (11.1–11.3, 11.5–11.7, 11.9), Phase 2 (11.4, 11.8) |
| §14 Open Questions and Risks | All phases |
| §15 Success Metrics | All phases |

---

## App State Progression

```
Phase 1 ──► Phase 2 ──► Phase 3 ──► Phase 4 ──► Phase 5
  │            │            │            │            │
  ▼            ▼            ▼            ▼            ▼
Magnetic    +True North  +Luopan     +History     +Sighting
compass     +Capture     mode        screen       +Export
+Cal        +Declination +坐向 lock  +Recal        +Grid N
+Detect     +WMM2025     +6 rings    prompts      +Polish
(Modern     (Modern +    (Luopan +   (all modes)  (all modes)
 Mode)       capture)     Modern)
```

---

## Engineering Handoff Checklist (per phase)

Before handing a phase to engineering, confirm:
- [ ] Phase REQ reviewed and accepted by product
- [ ] Master REQ §8 (accuracy spec) reviewed for any phase-specific constraints
- [ ] All `REQ-{DOMAIN}-{NUMBER}` IDs in scope confirmed in master REQ
- [ ] Phase end-to-end acceptance test understood
- [ ] Prerequisites from prior phase confirmed complete
