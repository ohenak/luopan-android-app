# Cross-Review: Software Engineer — FSPEC-luopan-p3-luopan-mode (v2)

| Field | Value |
|-------|-------|
| Reviewer | Software Engineer |
| Document | FSPEC-luopan-p3-luopan-mode.md v1.1 |
| Date | 2026-04-25 |
| Iteration | 2 |
| Recommendation | Approved with Minor Issues |

---

## Prior Findings Resolution

### High Findings

| Prior ID | Finding | Resolution Status | Notes |
|----------|---------|------------------|-------|
| F-01 | Navigation architecture gap | **Resolved** | §1.4 added with explicit navigation contract: `NavHostFragment`, `TabLayout`, `NavController`, `ModernCompassFragment`, `LuopanFragment`, `nav_graph.xml`. Files to create/modify enumerated. The section correctly delegates implementation decisions to the TSPEC. |
| F-02 | Flow 4d "current bearing" ambiguity | **Resolved** | Flow 4d §4a step 3 now specifies True North storage: `xiangBearing_trueN = displayedBearing_magN + declinationDeg`. Step 2 in Flow 4d explicitly states the stored True North bearing is NOT reread from live heading. Worked example with declination = −3.5° provided. ES-03 aligns. |

### Medium Findings

| Prior ID | Finding | Resolution Status | Notes |
|----------|---------|------------------|-------|
| F-03 | `LuopanState` missing `bearingDeg` and `northLabel` | **Resolved** | §4.1 now defines the full `LuopanState` Kotlin data class with `bearingDeg: Float` and `northLabel: String` at the top. Flow 3 field table updated to source from `LuopanState` (`bearingDeg` from `LuopanState`, `northLabel` from `LuopanState`). |
| F-04 | ES-07 incorrect "Calibrating..." at cold start | **Resolved** | ES-07 now explicitly states cold-start confidence is `POOR`, badge shows "Poor" (red). ES-02 has a new "Cold-start note" clarifying `STABILIZING` requires active fast rotation. |
| F-05 | Gold tick mark parent ring unspecified | **Resolved** | §1.1 capability table updated to "gold tick mark on Ring 5". Flow 4a step 7b now explicitly states "Draws a gold tick mark on Ring 5 (二十四山)". ES-05 states "the ring the tick mark is drawn on — is hidden" and now names Ring 5 explicitly. AC-07 updated to reference Ring 5. Flow 5 decision-point table now has an explicit row for hiding Ring 5. |
| F-06 | Ring 6 Parent 山 column mismatch | **Resolved** | §4.7 now carries a prominent note: "The `Parent 山` column is **informational metadata only** — it MUST NOT be used for programmatic lookups or cross-ring validation." This resolves the implementability risk by prohibiting programmatic use of the column. |
| F-07 | `SettingsRepository` missing 3 keys | **Resolved** | BR-09 table now lists all three persisted keys (`display_mode`, `luopan_show_romanization`, `luopan_show_my_language`) and includes an explicit note: "Phase 3 requires adding three new keys to `SettingsRepository`" with the class FQN. |

### Low Findings (Carry-Over Status)

| Prior ID | Finding | Resolution Status |
|----------|---------|------------------|
| F-08 | 坐向 overlay romanization toggle scope | **Not addressed in v1.1** — still unspecified |
| F-09 | No English mapping for Ring 2 (先天八卦) | **Not addressed in v1.1** — still unspecified |
| F-10 | 300 ms budget tight for CJK font measurement | **Not addressed in v1.1** |
| F-11 | Canvas transform order underspecified | **Partially resolved** — Flow 2 step 8 now states scale is applied first "around dial center, then the rotation transform" with explicit rationale. BR-11 formalizes `dialRotationDeg = -bearingDeg (mod 360°)` with example table. Still no pseudo-code or Canvas API sequence, but the order and rationale are now explicit enough for implementation. |
| F-12 | Long-press + pinch-to-zoom gesture conflict | **Not addressed in v1.1** |

---

## New Findings

### New Issues Introduced or Surfaced by v1.1

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| N-01 | Medium | **`LuopanState.bearingDeg` type mismatch between data class and usage.** §4.1 declares `bearingDeg: Float`, but the 坐向 lock fields (`xiangBearing`, `zuoBearing`) are declared `Double?`. Flow 4a step 3 converts `displayedBearing_magN + declinationDeg` — if `bearingDeg` is `Float` and declination is a `Double` (as returned by the WMM model), Kotlin will require explicit widening. More critically, the readout panel in Flow 3 specifies display precision of 0.1° ("Displayed to 0.1° precision") — `Float` has ~7 significant decimal digits which is sufficient for degrees, but all bearing arithmetic in the lock flows uses `Double`. Using mixed `Float`/`Double` types for the same conceptual value across the same data class is a contract inconsistency that will surface as implicit casts. Either declare `bearingDeg: Double` throughout or document the precision contract explicitly. | §4.1 data class, Flow 4a step 3, Flow 3 field table |
| N-02 | Medium | **Flow 4d declination sign convention not grounded in a concrete API contract.** Flow 4d step 3 specifies: "True N → Mag N: `displayBearing = xiangBearing_trueN − declinationDeg`". The ES-03 worked example uses declination = −3.5° giving `45.0 − (−3.5) = 48.5°`. This is internally consistent only if `declinationDeg` from the WMM model carries a signed value where East-positive conventions yield a negative number for westward declination. The Phase 2 WMM2025 model (`Wmm2025Model`) convention is not referenced here. If the sign convention of `declinationDeg` in `CompassViewModel` differs from what Flow 4d assumes (e.g., if declination is always stored as a positive magnitude with a separate direction), every north-type switch while locked will show the wrong display bearing. The FSPEC must reference the declination sign convention established in Phase 2 or state it explicitly (e.g., "East-positive: 5°E declination is stored as +5.0, 3.5°W is stored as −3.5"). | Flow 4d step 3, ES-03 worked example, §1.3 Phase 2 prerequisites |
| N-03 | Low | **`LuopanState.bearingDeg` during `SENSOR_ERROR`: retains last valid value, but initial cold-start value is 0°.** §4.1 states "`LuopanState.bearingDeg` retains the last valid bearing value during `SENSOR_ERROR`". ES-07 states the dial renders at bearing 0° at cold start before the first valid heading arrives, with confidence `POOR`. If `SENSOR_ERROR` occurs before any valid heading has been emitted (pathological cold start with broken sensor), `bearingDeg` will be 0° from initial state — not a "last valid" value. The View layer is told to suppress display when `confidence == SENSOR_ERROR`, so the 0° value will not display incorrectly in the readout, but the dial will render frozen at 0°. This case is not distinguished from the case where a valid heading was established. No change is strictly required, but the §4.1 note should clarify that "last valid bearing" defaults to 0° when no valid heading has ever been received. | §4.1 `bearingDeg` note, ES-07 |
| N-04 | Low | **`LuopanState` `zuoBearing` field name inconsistency.** §4.1 declares `val zuoBearing: Double?` but the Kotlin comment says "derived 坐 True North bearing = (向 + 180) mod 360 (null = unlocked)". Flow 4b step 1 says `CompassViewModel` clears `xiangBearing`, `xiangMountain`, `zuoBearing`, `zuoMountain`. However, Flow 4a step 5 says `CompassViewModel` computes `zuoBearing_trueN` — the `_trueN` suffix is dropped in the data class field name but used in all prose. This inconsistency will confuse the implementing engineer. Use `zuoBearing` consistently in all prose, or rename the data class field to `zuoBearingTrueN` — either way, be consistent throughout. | §4.1, Flow 4a step 5, Flow 4b step 1 |
| N-05 | Low | **AC-28 references "TE-F03" and AC-29 references "TE-F04" without defining these identifiers.** These tags appear to reference property IDs from a PROPERTIES document that does not yet exist. Cross-referencing forward to a not-yet-authored artifact will break tooling that validates cross-references and confuses engineers reading the FSPEC before PROPERTIES are written. Remove the TE-FXX references from the FSPEC, or add a parenthetical such as "(see future PROPERTIES doc)". | AC-28, AC-29 |
| N-06 | Low | **Effect of "Show romanization" on the 坐向 overlay is still unspecified (carry-over F-08).** v1.1 made no change to this. The 坐向 overlay shows `xiangMountain` and `zuoMountain` character fields (e.g., "向: 艮 (45.0° True N)"). When "Show romanization" is ON, it is not specified whether the 向 and 坐 mountain labels in the overlay show pinyin (e.g., "向: 艮 Gèn (45.0° True N)"). This will surface as a direct implementation question. | Flow 4a step 7a, Flow 7 Toggle A |
| N-07 | Low | **Ring 2 (先天八卦) English label mapping absent (carry-over F-09).** v1.1 made no change. §4.8 provides English label mapping tables for Rings 3, 4, and 5. Ring 2 is a visible rendered ring. When "Show in my language" is ON, no substitution is specified for Ring 2 labels (乾, 兌, 離, 震, 巽, 坎, 艮, 坤 — the Earlier Heaven arrangement). The §4.2 note says Ring 2 is "decorative" and not in the readout panel; however, the dial still renders Ring 2 labels visually. The FSPEC should state whether Ring 2 renders in Traditional Chinese in all language modes, or provide an English mapping table. | §4.2 note, §4.8, Flow 7 Toggle B step 3 |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | **`bearingDeg` type: Float or Double?** §4.1 declares `Float`. Lock fields use `Double`. WMM declination is `Double`. Should `bearingDeg` be widened to `Double` for internal consistency, or is `Float` deliberate (e.g., to match sensor output type)? |
| Q-02 | **Declination sign convention:** Is `declinationDeg` in `CompassViewModel` East-positive signed (5°E = +5.0, 3.5°W = −3.5)? This must be confirmed against the Phase 2 WMM implementation to validate the Flow 4d arithmetic. |
| Q-03 | **Ring 2 in "Show in my language" mode:** Should Ring 2 (先天八卦 / Fuxi arrangement) labels render in Traditional Chinese regardless of the language toggle (matching the §4.2 "decorative" note), or should they use English equivalents? |

---

## Positive Observations

- **F-01 resolution is exemplary.** §1.4 provides a clean navigation contract without over-specifying implementation. The contract/implementation separation is correctly maintained.
- **F-02 resolution is thorough.** The True North storage invariant is now stated in four places: §1.1 capability table ("gold tick mark on Ring 5"), Flow 4a step 3, Flow 4d, and BR-06. The worked example with signed declination value eliminates the sign ambiguity from v1.0.
- **F-03 resolution is excellent.** The Kotlin `data class` definition in §4.1 is a concrete, implementable contract. Including nullability annotations (`?`) for lock fields is precisely the kind of specification that prevents null-safety bugs.
- **F-04 resolution is clean.** The `POOR` vs. `STABILIZING` distinction is now explicit in both ES-02 (with cold-start note) and ES-07 (with explicit `POOR` badge and rationale). No implementation ambiguity remains.
- **F-05 resolution is complete.** Ring 5 is named in five locations: §1.1, Flow 4a step 7b, Flow 5 decision-point table, ES-05, and AC-07. Engineers cannot miss it.
- **BR-11 is a valuable new addition.** Formalizing `dialRotationDeg = -bearingDeg (mod 360°)` with a four-row example table makes the rotation contract directly testable. The AC-02a/AC-02b split into math-correctness and pointer-fixedness is a good testability improvement.
- **AC-28 and AC-29 are good additions.** The `SENSOR_ERROR` while locked scenario and `STABILIZING` lock-disabled scenario were gaps in v1.0 acceptance criteria.

---

## Recommendation

**Approved with Minor Issues**

All seven High and Medium findings from iteration 1 (F-01 through F-07) are resolved. No new High findings were introduced. Two new Medium findings (N-01, N-02) require resolution before TSPEC authoring — specifically the `Float`/`Double` type contract for `bearingDeg` and the declination sign convention reference. The five Low findings (N-03 through N-07) and three carry-over Lows (F-08, F-09, F-12) do not block TSPEC authoring but should be captured as TSPEC-phase implementation questions.

**Mandatory before TSPEC:**

1. **(N-01)** Decide whether `LuopanState.bearingDeg` is `Float` or `Double`. All lock arithmetic (`xiangBearing`, `zuoBearing`) uses `Double`; the WMM declination is `Double`. Recommend `Double` throughout or document the precision rationale for `Float`.

2. **(N-02)** Add one line to Flow 4d or §1.3 stating the declination sign convention: e.g., "East-positive: 5°E declination = +5.0°, 3.5°W = −3.5°. This matches the `Wmm2025Model` convention from Phase 2." This anchors the arithmetic to the existing codebase contract.

**Recommended before TSPEC (Low):**

3. **(N-04)** Standardize `zuoBearing` vs. `zuoBearing_trueN` naming between the data class and prose.

4. **(N-05)** Remove TE-F03/TE-F04 cross-references from AC-28 and AC-29, or add a note that these are forward references to the PROPERTIES document.
