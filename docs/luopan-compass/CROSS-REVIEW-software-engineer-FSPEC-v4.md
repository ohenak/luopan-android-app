# Cross-Review: SE — FSPEC-luopan-p2-true-north-capture (Iteration 4 — Final Verification)

**Reviewer role:** Senior Software Engineer
**Documents reviewed:** FSPEC-luopan-p2-true-north-capture.md v0.4-draft; REQ-luopan-p2-true-north-capture.md v0.4-draft
**Date:** 2026-04-24
**Previous recommendation:** Need Attention (Iteration 3)
**Scope:** Verification of V3-N-01 through V3-N-05 resolutions; check for remaining Medium/High findings; focused scan of v0.4-draft changes for new issues

---

## Verification of Iteration 3 Medium Findings

### V4-V-01 — V3-N-01: §2.4 step 1 trigger (`onResume` → `onStart`)

**Status: RESOLVED**

FSPEC §2.4 step 1 now reads:

> "On `Activity.onStart` (or on first True North activation within the session), the system initiates the location resolution chain. Using `onStart` rather than `onResume` matches the Phase 1 sensor registration lifecycle and prevents the location chain from restarting on every permission dialog dismissal (which cycles `onPause` → `onResume` without leaving the activity). The chain fires at most once per session start."

The rationale is explicit and correct. The change directly resolves the permission-dialog re-entry concern raised in iterations 1 and 2 and carried forward in V3-N-01. The WMM expiry check at §2.1 correctly retains `onResume` (with 60-second debounce), as this is the right lifecycle event for that lighter check. The two triggers are now coherently differentiated. **Resolved.**

---

### V4-V-02 — V3-N-02: `altitude_m` type `Double?`

**Status: RESOLVED**

REQ §5.3.1 now reads:

> `altitude_m` | Double? | Conditional | "Double? for consistency with GPS altitude precision and the WMM computation interface (altMeters: Double). NULL when altitude is unavailable from the GPS fix…"

FSPEC §6.1 defines `alt_m` as `Double?` with `Nullable = Yes` and includes a normative note: "REQ §5.3.1 has been updated (v0.4-draft) to reflect `Double?` type, resolving the prior REQ Float / FSPEC Double? conflict. The column name `alt_m` is the canonical SQLite column name; the REQ field name `altitude_m` maps to this column."

Both documents are now aligned on type (`Double?`) and the field-name discrepancy (`altitude_m` in REQ vs `alt_m` in FSPEC column) is explicitly called out in the normative note as a known mapping rather than a conflict. The TSPEC author has enough information to make a deterministic implementation decision. **Resolved.**

---

### V4-V-03 — V3-N-03: `display_mode` nullable `String?`

**Status: RESOLVED**

REQ §5.3.1 now reads:

> `display_mode` | String? | OPTIONAL (nullable) | "null is permitted when the display mode is unknown. Phase 2 implementations MUST write "MODERN". The value "LUOPAN" is reserved for Phase 3; "SIGHTING" is reserved for Phase 5…"

FSPEC §6.1 defines `display_mode` as `String?` with `Nullable = Yes (nullable)` and the constraint `"MODERN" or NULL; "LUOPAN" reserved Phase 3; "SIGHTING" reserved Phase 5`. The normative note states: "Phase 2 implementations MUST write "MODERN"" and "REQ §5.3.1 has been updated (v0.4-draft) to align on String? nullable, resolving the prior REQ required/non-nullable vs FSPEC nullable conflict."

Both documents are now aligned on nullability and permitted values. The Phase 2 MUST-write `"MODERN"` constraint is present in both. **Resolved.**

---

## Verification of Iteration 3 Low Findings

### V4-V-04 — V3-N-04: Cache age formula

**Status: RESOLVED**

FSPEC §2.3 step 7 now reads:

> "N = `floor((Clock.nowMs() − cache.timestamp_ms) / 86_400_000L)` (integer milliseconds division, no floating-point intermediate)."

AT-C-02 uses: "floor(elapsed_ms / 86 400 000)".

Both are now the same formula with the same millisecond integer arithmetic. The intermediate `cache_age_hours` step from the v0.3-draft is gone. The two expressions are now identical in semantics and implementation approach. **Resolved.**

Note: REQ §8 Scenario C still states "The days boundary is midnight UTC" but this qualifier has not been removed. This is a pre-existing minor REQ/FSPEC inconsistency (the FSPEC formula is a pure duration floor, not a calendar-day boundary) that was flagged in V3-N-04. It is Low severity and acceptable for TSPEC authoring since the FSPEC formula is normative and the `FakeClock` test eliminates the ambiguity. This finding is not re-raised.

---

### V4-V-05 — V3-N-05: §1.3 TSPEC cross-reference

**Status: RESOLVED**

FSPEC §1.3 now reads:

> "…the `calibration_records` table is not altered (see §9 of TSPEC Phase 1)."

The reference is now "§9" (database schema), which is the correct section. **Resolved.**

---

## Check for Remaining Unresolved Medium/High Findings from Prior Iterations

| Prior ID | Severity | Summary | v0.4-draft Status |
|----------|----------|---------|-------------------|
| V3-N-01 | Medium | Location chain `onResume` → `onStart` | **Resolved** — see V4-V-01 |
| V3-N-02 | Medium | `altitude_m` Float vs Double? | **Resolved** — see V4-V-02 |
| V3-N-03 | Medium | `display_mode` nullability conflict | **Resolved** — see V4-V-03 |
| N-11 (iter 2) | Medium | Main-screen "Using last known location (N days ago)" advisory unspecified | **Still open — see V4-N-01 below** |

All three Medium findings scoped for Iteration 4 verification (V3-N-01, V3-N-02, V3-N-03) are confirmed resolved. N-11 from iteration 2 was listed as open in the iteration 3 summary and was not addressed in v0.4-draft.

---

## New and Remaining Open Findings in v0.4-draft

### V4-N-01 — N-11 (iter 2, still open): Main-screen "Using last known location (N days ago)" advisory UI element unspecified

**Severity: Low** (downgraded from Medium in this iteration; reasoning below)

**Section:** §2.3, §5.3, AT-C-02; REQ §8 Scenario C

**Finding:** REQ §8 Scenario C requires: "UI shows 'Using last known location (N days ago)'…" with a string resource `R.string.location_cache_age_label`. The FSPEC places the "N days ago" label and the "Cached location" source string exclusively in the **declination info panel** (§2.3 step 3, AT-C-02). No FSPEC section specifies a main compass screen UI element (banner, subheading, or status row) for this string.

In iterations 2 and 3 this was raised as a Medium concern because an implementation engineer would not know where on screen to render the advisory. In v0.4-draft the gap is narrowed because:
- §2.4 step 2 (location chain) states "The UI shows the 'Cached location' source label in the declination info panel" — this makes the intent explicit, if panel-only.
- The GPS fix–lost error scenario in §4 states "Info panel shows 'Cached location' with age" — again panel-only.
- AT-C-02 asserts the info panel content correctly and deterministically.

The remaining risk is that REQ Scenario C uses the phrase "UI shows 'Using last known location…'" which an engineer reading the REQ in isolation could interpret as a main-screen element. The FSPEC does not explicitly confirm that the panel is the sole location for this string, nor does it include a normative note stating "this string does NOT appear on the main compass screen." This omission leaves a small interpretation gap.

**Recommendation:** Either (a) add a one-line note in FSPEC §2.4 step 2 explicitly stating "The cache-age advisory is shown only in the declination info panel — it is NOT displayed as a separate banner on the main compass screen," or (b) raise a REQ errata to amend Scenario C so that "UI" refers specifically to the info panel. Either approach is sufficient. Batch into the next revision alongside any TSPEC feedback.

**Downgrade rationale:** This finding no longer blocks TSPEC authoring. The FSPEC is internally consistent in placing the string in the info panel; the REQ Scenario C language is imprecise but does not create a type, schema, or lifecycle conflict. The TSPEC author can proceed with the panel-only interpretation.

---

### V4-N-02 — Cosmetic: FSPEC footer version label still reads v0.3-draft

**Severity: Trivial**

**Section:** Last line of FSPEC

**Finding:** The document footer reads `*End of FSPEC-luopan-p2-true-north-capture v0.3-draft*`. The document header (metadata table) correctly says `0.4-draft`. The footer was not updated when v0.4-draft changes were applied.

**Recommendation:** Update the footer to `v0.4-draft`. One-line fix; batch with any other pass.

---

### Carried Low Findings (Not Re-Raised as Blockers)

The following Low findings from prior iterations remain technically open but are not blocking and are expected to be resolved during TSPEC authoring or a subsequent REQ pass:

| Prior ID | Section | Summary | Disposition |
|----------|---------|---------|-------------|
| V3-N-06 | §2.4, §5.3 | 60-second GPS freshness window not aligned with REQ-NORTH-03 session-based definition | Deferred to TSPEC; TSPEC author must choose one definition and note the decision |
| V3-N-07 | §6.3, REQ §5.1 | `MagneticFieldModel` parameter names differ: REQ uses `latDeg`/`lonDeg`/`altM`; FSPEC uses `lat`/`lon`/`altMeters`. `getModelId()` and `isExpired()` are in FSPEC §6.3 but not in REQ §5.1 minimum interface | Deferred to TSPEC; TSPEC author must use one set of names and may extend the REQ minimum interface |
| V3-N-08 | §6.1, §2.5 | `notes` field: §2.5 step 6 says "empty by default" (implying `""`), §6.1 schema says `Nullable = Yes, Constraint = Length 0–1000 or NULL` | Deferred to TSPEC; recommend NULL as canonical empty-notes value |
| N-08 / V3-N-06 | §2.4, §5.3, REQ §5.1 | GPS freshness window (60-second) vs. REQ session-based definition | As above |
| N-10 | §2.4 step 6 | Location cache storage mechanism unspecified in FSPEC | Appropriately deferred to TSPEC |
| N-14 | §6.3 | `epochYears` formula placement (comment vs. contract) | Acceptable as-is; note is present adjacent to the interface |
| N-15 | AT-NFR | Reference device for NFR timing not pinned to a specific model | Partially resolved in REQ §5.4 (mid-range definition added); remaining imprecision acceptable at FSPEC level |

None of these findings introduce type conflicts, schema ambiguities, or lifecycle bugs at the FSPEC/REQ layer. They are appropriately deferred to TSPEC or a future REQ maintenance pass.

---

## Summary

| Item | Severity | Resolved? |
|------|----------|-----------|
| V3-N-01: §2.4 step 1 `onResume` → `onStart` | Medium | Yes |
| V3-N-02: `altitude_m` type `Double?` | Medium | Yes |
| V3-N-03: `display_mode` String? nullable | Medium | Yes |
| V3-N-04: Cache age formula standardized | Low | Yes |
| V3-N-05: §1.3 TSPEC cross-reference "§9" | Low | Yes |
| N-11 (iter 2): Main-screen cache advisory | Low (downgraded from Medium) | No — deferred; non-blocking |
| V4-N-01: Footer still says v0.3-draft | Trivial | No — cosmetic; non-blocking |
| All carried Low findings | Low | Deferred to TSPEC |

**No Medium or High findings remain open.** The one remaining Low finding (V4-N-01 / N-11) has been downgraded from Medium because the FSPEC is now internally consistent on the info-panel placement, and the REQ Scenario C language imprecision is not sufficient to create a schema or lifecycle ambiguity that would block TSPEC authoring.

---

## Positive Observations in v0.4-draft

- The `onStart` rationale in §2.4 step 1 is well-constructed: it states the reason (permission-dialog lifecycle cycle), gives the architectural justification (Phase 1 parity), and adds the "fires at most once per session start" guard. This is ready-to-implement prose.
- The `altitude_m` / `alt_m` name-mapping note in FSPEC §6.1 is exactly the right approach for handling a column-name vs. field-name discrepancy between the two documents — rather than silently picking one name, the FSPEC declares which name is canonical for each layer.
- The `display_mode` normative note in FSPEC §6.1 cleanly handles the Phase 2–3–5 reserved-value concern: Phase 2 MUST write `"MODERN"`, `"LUOPAN"` and `"SIGHTING"` are reserved, and NULL is permitted for unknown. An engineer reading this note cannot make an incorrect column decision.
- AT-E-10 (added in v0.4-draft per the TE feedback) correctly asserts that `OverallConfidence.POOR` with `InterferenceState.CLEAR` produces `interference_flag=false`. This closes the last behavioral ambiguity in the capture flow.

---

## Recommendation: Approved with Minor Issues

All three Medium findings (V3-N-01, V3-N-02, V3-N-03) have been correctly resolved. No new Medium or High findings are introduced in v0.4-draft. The one remaining open finding (V4-N-01 / N-11) has been downgraded to Low and does not block TSPEC authoring. The trivial footer version label (V4-N-02) is cosmetic.

**The FSPEC and REQ are approved for TSPEC authoring.** The TSPEC author should note the following deferred decisions to be resolved in the TSPEC:
1. `MagneticFieldModel` parameter name choice (`latDeg`/`lonDeg`/`altM` from REQ vs. `lat`/`lon`/`altMeters` from FSPEC §6.3) — pick one set and note the decision in TSPEC §6 or §9.
2. `Clock` vs. `TimeSource` interface decision — per FSPEC §6.2 guidance, document the decision in TSPEC §6 or §9.
3. GPS freshness window definition — 60-second duration (FSPEC §2.4, §5.3) vs. "fix received after app opened in the current session" (REQ-NORTH-03) — document the chosen definition in the TSPEC.
4. `notes` field empty-state canonical value — recommend `NULL` per §6.1 schema.
