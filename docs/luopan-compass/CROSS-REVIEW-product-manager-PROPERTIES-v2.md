# Cross-Review: PM — PROPERTIES-luopan-p2-true-north-capture (Iteration 2)

**Reviewer role:** Product Manager
**Document reviewed:** PROPERTIES-luopan-p2-true-north-capture.md v0.2
**Prior review:** CROSS-REVIEW-product-manager-PROPERTIES.md (Iteration 1 — Approved with Minor Issues)
**Date:** 2026-04-24

---

## Purpose

This is a verification-only second-iteration review. Its sole scope is to confirm that the five findings from Iteration 1 (PM-P-01 through PM-P-05) have been correctly addressed in v0.2, that the REQ coverage matrix remains complete, and that no new PM-level issues have been introduced.

---

## Finding Verification

### PM-P-01 (Critical) — `id: Long` → `id: String (UUID v4)`

**Status: RESOLVED**

PROP-SCHEMA-01 schema table now declares `id` as `String (UUID v4, TEXT primary key — NOT auto-increment Long)`. The Then clause assertion confirms `id.returnType.classifier == String::class`. The normative note ("Not an auto-increment integer, to support future sync/export without collision") is retained verbatim from FSPEC §6.1. The prior spurious reflection assertion `id.returnType.classifier == Long::class` is gone.

---

### PM-P-02 (Critical) — `altitude_m` → `alt_m`

**Status: RESOLVED**

The schema table row and the Then clause assertion both now use `alt_m` throughout (`alt_m.returnType.isMarkedNullable == true && alt_m.returnType.classifier == Double::class`). The PROP-LOCATION-04 prose reference also correctly uses `alt_m`. No residual `altitude_m` identifier remains in the document.

---

### PM-P-03 (Minor) — FSPEC AT-G coverage labeling

**Status: RESOLVED**

AT-G references are now present in the FSPEC column of the applicable summary table rows:

- PROP-NORTH-04 → `AT-G-05`
- PROP-NORTH-05 → `AT-G-08`
- PROP-LOCATION-03 → `AT-G-06, AT-G-07`
- PROP-LOCATION-04 → `AT-G-09`
- PROP-LATITUDE-01 → `AT-G-01 through AT-G-04`
- PROP-LATITUDE-02 → `AT-G-01, AT-G-02`

All nine AT-G scenarios (G-01 through G-09) now have explicit FSPEC column citations in the relevant properties. Traceability audits against the FSPEC AT-G section will succeed.

---

### PM-P-04 (Minor) — PROP-DECL-02 missing FSPEC §2.3 panel fields

**Status: RESOLVED**

PROP-DECL-02 now covers all seven panel fields required by FSPEC §2.3:

1. Declination value formatted as `±X.XX°E/W`
2. Model identifier `"WMM2025"`
3. Coordinates masked to 2 decimal places
4. Last-updated date string
5. **Coordinates type label** (`"GPS fix"` / `"Cached location"` / `"Manual entry"`) — sub-case A/B
6. **Cache age display** (`"N days ago"` via floor division) — sub-case B (cached, 4 days old)
7. **Inactive-True-North note** when `northType=MAGNETIC` — sub-case C

All three previously missing fields have individual Given/When/Then sub-cases with specific view IDs. The summary table FSPEC column for PROP-DECL-02 also now cites `FSPEC §2.3`.

---

### PM-P-05 (Minor) — GPS-toggle-ON default property

**Status: RESOLVED**

PROP-CAPTURE-10 ("GPS-include toggle defaults to ON on first capture dialog open") has been added as a new E2E / Espresso property at P1 priority. It asserts `onView(withId(R.id.capture_gps_toggle)).check(matches(isChecked()))` on first dialog open with `bearing_location_consent_shown` absent or `false`. The coverage matrix row for `REQ-CAPTURE-06` now lists `PROP-LOCATION-04, PROP-PERSIST-04, PROP-CAPTURE-10`. The finding is fully resolved.

---

## REQ Coverage Matrix Completeness

All REQ IDs in-scope for Phase 2 (REQ §5) are present in the coverage matrix:

| REQ ID | In-Scope in REQ §5 | Covered in Matrix | Note |
|--------|-------------------|------------------|------|
| REQ-NORTH-01 | Yes (P0) | Yes | PROP-NORTH-01, -03, -04, PROP-INTERFERENCE-01, -02 |
| REQ-NORTH-02 | Yes (P0) | Yes | PROP-NORTH-04, PROP-INTERFERENCE-03 |
| REQ-NORTH-03 | Yes (P0) | Yes | PROP-LOCATION-01 through -06 |
| REQ-NORTH-04 | Yes (P0) | Yes | PROP-NORTH-01, -02, -05, -06 |
| REQ-DECL-01 | Yes (P0) | Yes | PROP-DECL-01, PROP-NORTH-05, -06 |
| REQ-DECL-02 | Yes (P1) | Yes | PROP-DECL-02 |
| REQ-CAPTURE-01 | Yes (P0) | Yes | PROP-CAPTURE-01 through -06, PROP-SCHEMA-01 |
| REQ-CAPTURE-02 | Yes (P0) | Yes | PROP-CAPTURE-07, -08, -09 |
| REQ-CAPTURE-04 | Yes (P0) | Yes | PROP-PERSIST-01, -02, -03 |
| REQ-CAPTURE-06 | Yes (P0) | Yes | PROP-LOCATION-04, PROP-PERSIST-04, PROP-CAPTURE-10 |
| REQ-NFR-08 | Yes (P1) | Yes | PROP-PERF-01, -02 |
| REQ-DETECT-01 | Phase 2 upgrade (no new ID) | Yes | PROP-INTERFERENCE-01, -03 |
| REQ-DETECT-02 | Phase 2 upgrade (no new ID) | Yes | PROP-INTERFERENCE-02 |
| REQ-NFR-05 | Master REQ inherited | Yes | PROP-DECL-03 (offline constraint) |
| REQ §7 §11.4 | Yes (extreme latitude) | Yes | PROP-LATITUDE-01, -02 |

No REQ ID from REQ §5 is absent from the matrix. No out-of-scope Phase 3/4/5 requirements are introduced.

---

## Informational Note: PM-P-06 (Carried Forward)

The PM-P-06 observation from Iteration 1 (REQ-NFR-05 coverage notation potentially confusing during Phase 2-only traceability audits) has not been actioned as a structural change; PROP-DECL-03 retains its REQ-NFR-05 citation without an inline clarification note or BR-01 cross-reference. This remains informational and does not block approval. The behavior tested by PROP-DECL-03 (fully offline WMM computation) is unambiguously correct and necessary for Phase 2.

---

## New Issues in v0.2

None identified. The document total has grown from 36 to 37 properties due to the addition of PROP-CAPTURE-10. All summary tables and the by-domain / by-level / by-priority counts are updated consistently. No new scope creep, priority misalignments, or traceability gaps were found.

---

## Summary

| Finding | Iteration 1 Severity | Resolution in v0.2 |
|---------|---------------------|-------------------|
| PM-P-01 — `id: Long` → `id: String (UUID v4)` | Critical | Resolved |
| PM-P-02 — `altitude_m` → `alt_m` | Critical | Resolved |
| PM-P-03 — AT-G FSPEC column labeling | Minor | Resolved |
| PM-P-04 — PROP-DECL-02 missing panel fields | Minor | Resolved |
| PM-P-05 — GPS-toggle-ON default (PROP-CAPTURE-10) | Minor | Resolved |
| PM-P-06 — REQ-NFR-05 confusion note | Informational | Not actioned (acceptable) |

---

## Recommendation: Approved

All five actionable findings from Iteration 1 are fully resolved in v0.2. The two Critical blockers (schema type conflict and field name mismatch) are corrected with precise reflection assertions that will catch implementation deviations. The three Minor findings are addressed with complete, testable property definitions. The REQ coverage matrix is complete for all Phase 2 in-scope requirements. The document is approved for test implementation.
