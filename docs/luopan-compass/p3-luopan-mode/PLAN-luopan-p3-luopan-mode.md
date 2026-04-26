# PLAN-luopan-p3-luopan-mode

| Field | Value |
|-------|-------|
| Version | 1.0 |
| Date | 2026-04-25 |
| Based on | TSPEC-luopan-p3-luopan-mode.md v1.1 |
| Linked REQ | REQ-luopan-p3-luopan-mode.md v0.3-draft |
| Linked FSPEC | FSPEC-luopan-p3-luopan-mode.md v1.1 |

---

## Open Issue Notice — V3-F01 (TE finding, unresolved)

**Finding:** The gold tick mark canvas position in `LuopanView.drawGoldTickMark()` is specified in TSPEC §6.1.4 to use `xiangBearing` (always stored as True North). When the user is viewing Magnetic North, the tick mark angle on the dial should reflect the *display* bearing so that the visual position matches what the user sees in the overlay. Using the raw True North `xiangBearing` when the dial is showing magnetic headings will cause the tick mark to appear at the wrong sector.

**Required fix in Task 4.3:** `LuopanView` must receive a `displayXiangBearing: Float?` setter that is pre-converted to the current display reference (True N or Mag N) by the View layer before drawing. `LuopanFragment.updateZuoXiangOverlay()` computes `displayBearing = xiangBearing_trueN ± declinationDeg` for text display; that same value MUST also be passed to `LuopanView.setLockState(active, displayXiangBearing)` — NOT the raw `luopanState.xiangBearing`. This is the only public API call that controls tick mark position. Task 4.3 must implement and test this correctly.

**TSPEC change required:** TSPEC §6.1.4 `drawGoldTickMark` must use `displayXiangBearing` (the display-reference-converted bearing) not `xiangBearing` directly. This is a correctness fix, not a new feature.

---

## Summary

Phase 3 delivers the luopan display mode — the core product differentiator for feng shui practitioners. It introduces:

1. **Six concentric ring dial** — custom Canvas View with hardware-accelerated rotation; pointer fixed, dial counter-rotates
2. **Numerical readout panel** — six-field panel with 山, 十二地支, 後天八卦 方位, bearing, north reference, 分金 (High confidence only), and confidence badge
3. **坐向 lock** — "Lock 向" button; derives 坐 bearing; gold tick mark; preserves across mode switches
4. **Navigation migration** — Activity-level NavHostFragment + TabLayout; existing compass UI migrated to `ModernCompassFragment`
5. **Domain LUTs** — `SectorLookup`, `RingLabelProvider`, `ZuoXiangLock` — pure domain objects with no Android dependencies
6. **Session state** — ring visibility toggles, pinch-to-zoom, lock state (ViewModel in-memory, not persisted)
7. **Persisted settings** — `display_mode`, `luopan_show_romanization`, `luopan_show_my_language` (SettingsRepository)

The implementation is structured in 6 batches. Batch 1 (pure domain) and Batch 6 (localization settings) have no interdependency and can be executed in parallel. Subsequent batches depend on prior ones as described.

---

## Existing Codebase Integration Points

| Existing File | How Phase 3 Touches It |
|---------------|------------------------|
| `ui/CompassUiState.kt` | Add `val luopan: LuopanState = LuopanState.INITIAL` field and update `INITIAL` |
| `ui/CompassViewModel.kt` | Add session state fields, ZuoXiangLock, luopan computation in `startSensorCollection` |
| `ui/CompassActivity.kt` | Replace direct UI wiring with NavHostFragment + TabLayout; migrate UI to `ModernCompassFragment` |
| `res/layout/activity_compass.xml` | Replace ConstraintLayout content with NavHostFragment + bottom TabLayout |
| `settings/SettingsRepository.kt` | Add 3 new keys: `display_mode`, `luopan_show_romanization`, `luopan_show_my_language` |
| `model/OverallConfidence.kt` | Read-only — used by `LuopanState.confidence` |

---

## Batch 1 — Domain Layer (no dependencies)

Tasks 1.1, 1.2, and 1.3 have no dependencies on each other and can run in parallel.

---

### Task 1.1 — SectorLookup ✅

| Item | Detail |
|------|--------|
| Description | Pure `object` with sector boundary LUTs for all 6 rings. Implements universal inclusive-left exclusive-right `[start°, end°)` membership rule (BR-01) with wrap-around support for sectors straddling 0°/360° (BR-02, BR-03, BR-04). Provides `ring2()` through `ring6()` public functions each returning a 0-based sector index. No Android dependencies. |
| Files to read | `docs/luopan-compass/p3-luopan-mode/TSPEC-luopan-p3-luopan-mode.md` §4.1 (SectorLookup), §4.1 sector tables for all 6 rings; `docs/luopan-compass/p3-luopan-mode/FSPEC-luopan-p3-luopan-mode.md` §3 (BR-01 through BR-04), §4.2–4.7 (ring label reference tables including all boundary ranges) |
| Files to create | `app/src/main/java/com/luopan/compass/luopan/SectorLookup.kt` |
| Files to create (test) | `app/src/test/java/com/luopan/compass/luopan/SectorLookupTest.kt` |
| Acceptance | All unit tests pass: Ring 4 子/亥 wrap-around at 344.9°/345.0°/14.9°/15.0°; Ring 4 generic boundary at 44.9°/45.0°; Ring 5 sub-15° boundaries at 7.4°/7.5°/22.4°/22.5°; Ring 3 45° boundaries at 22.4°/22.5°/67.4°/67.5°; Ring 6 壬子分金 wrap-around at 357.9°/358.0°/0.0°/4.0°; Ring 6 key test bearings at 180°(壬午分金)/90°(壬卯分金)/0°(壬子分金); Ring 2 boundaries at 337.4°/337.5°/0°/22.4°/22.5°/157.4°/157.5°/202.4°/202.5°; normalization for 360° and negative inputs. `IllegalStateException` is thrown for any bearing not matched (Ring 6 gap defense). |
| Depends on | — |
| Status | ✅ Complete — 44 unit tests pass, zero failures. Note: task prompt listed `ring6(357.9f)==59` and `ring6(358.0f)==0` but these are internally inconsistent with `ring6(180f)==31` and `ring6(90f)==16`. Implementation follows TSPEC §4.1 (庚子分金=index 0, 壬子分金=index 1 wrap-around), which satisfies the business-critical bearing tests. |

---

### Task 1.2 — RingLabelProvider ✅

| Item | Detail |
|------|--------|
| Description | Pure `object` with compile-time `LabelData` arrays for all 6 rings. `LabelData` holds `character: String` (zh-Hant), `pinyin: String`, and `english: String` (from §5.8 mapping tables). All 8 + 8 + 12 + 24 + 60 entries populated. No Android dependencies. |
| Files to read | `docs/luopan-compass/p3-luopan-mode/TSPEC-luopan-p3-luopan-mode.md` §4.2 (RingLabelProvider); `docs/luopan-compass/p3-luopan-mode/FSPEC-luopan-p3-luopan-mode.md` §4.2–4.7 (full label tables for Rings 2–6), §4.8 ("Show in My Language" English mapping tables); `docs/luopan-compass/p3-luopan-mode/REQ-luopan-p3-luopan-mode.md` §5.3a (Ring 2 Fuxi arrangement), §5.6 (Ring 6 六十分金 table), §5.8 (English ring label mappings) |
| Files to create | `app/src/main/java/com/luopan/compass/luopan/RingLabelProvider.kt` |
| Files to create (test) | `app/src/test/java/com/luopan/compass/luopan/RingLabelProviderTest.kt` |
| Acceptance | All 8 Ring 2 labels non-empty with correct Fuxi arrangement (乾☰ at sector index 4, center 180°). All 8 Ring 3 labels non-empty with correct King Wen arrangement (☲離南 at index 4). All 12 Ring 4 labels non-empty (子 at index 0). All 24 Ring 5 labels non-empty (午 at index 13). All 60 Ring 6 labels non-empty (壬午分金 at index 31). English equivalents present for Rings 3, 4, 5 per §5.8 mapping tables. Ring 2 `ring2Label(4)` returns character="☰", name="乾", direction="南". Ring 6 `ring6Label(31)` returns "壬午分金". |
| Depends on | — |
| Status | ✅ Complete — all tests pass. Note: task prompt's "Sector 0: 壬子分金" was a typo; TSPEC §4.1 authoritative: index 0=庚子分金, index 1=壬子分金 (wrap). Tests adjusted accordingly. |

---

### Task 1.3 — ZuoXiangLock

| Item | Detail |
|------|--------|
| Description | Class holding 坐向 lock state backed by `AtomicReference<LockState>` for thread-safe publication across coroutine dispatchers (TE-F04). Provides `lock(bearing: Float)`, `clear()`, and `rederive(trueBearing: Float)` functions. Derives `zuoBearing = (xiangBearing + 180f) % 360f`. Calls `SectorLookup` and `RingLabelProvider` for 山 lookups. Note: depends on Tasks 1.1 and 1.2 for `SectorLookup.ring5()` and `RingLabelProvider.ring5Label()`. **TE-N-F01 resolution required:** `rederive()` implementation must be consistent with TSPEC §8.2 (`rederive()` IS called on north-switch and it calls `lock()` with the live True North bearing — updating `xiangBearing`). The test `rederive_northSwitch_doesNotChangeStoredTrueNorthBearing` must be rewritten to actually call `rederive()` with a new bearing and assert the updated state, per TE-N-F01. |
| Files to read | `docs/luopan-compass/p3-luopan-mode/TSPEC-luopan-p3-luopan-mode.md` §4.4 (ZuoXiangLock), §2.1 (Float? decision), §8.2 (north-type change → rederive); `docs/luopan-compass/p3-luopan-mode/FSPEC-luopan-p3-luopan-mode.md` §4c–§4d (lock state across mode switches; north reference switch); `docs/luopan-compass/p3-luopan-mode/CROSS-REVIEW-test-engineer-TSPEC-v2.md` (N-F01, N-F03 — rederive contradiction resolution) |
| Files to create | `app/src/main/java/com/luopan/compass/luopan/ZuoXiangLock.kt` |
| Files to create (test) | `app/src/test/java/com/luopan/compass/luopan/ZuoXiangLockTest.kt` |
| Acceptance | `lock(270f)` → `zuoBearing == 90f`; `lock(350f)` → `zuoBearing == 170f`; `lock(0f)` → `zuoBearing == 180f`; `lock(180f)` → `zuoBearing == 0f`; `lock(45f)` → `xiangMountain == "艮"` and `zuoMountain == "坤"`; `lock(90f)` → `xiangMountain == "卯"` and `zuoMountain == "酉"`; `clear()` sets `isLockActive = false` with all bearings null; `rederive(newBearing)` updates all lock fields via `lock(newBearing)` (resolves TE-N-F01/N-F03: `rederive` DOES call `lock`, thereby overwriting `xiangBearing` to the new True North bearing); concurrent `lock()`/`clear()` test produces no torn state (always fully-locked or fully-unlocked). |
| Depends on | Task 1.1 (SectorLookup), Task 1.2 (RingLabelProvider) |

---

## Batch 2 — ViewModel & State (depends on Batch 1)

Tasks 2.1, 2.2, and 2.3 can run in parallel after Batch 1 completes.

---

### Task 2.1 — LuopanState + LuopanStateMapper

| Item | Detail |
|------|--------|
| Description | Create `LuopanState` data class (with `INITIAL` companion) and `LuopanStateMapper` pure object. Mapper takes `CompassUiState`, `ZuoXiangLock.LockState`, `showRomanization: Boolean`, and `showMyLanguage: Boolean` and produces a fully-populated `LuopanState`. Implements SENSOR_ERROR guard (character fields → "—"). Confidence-gates `fenJinLabel` to HIGH only. Applies `showMyLanguage` and `showRomanization` to all label fields. **TE-N-F02 resolution:** Mapper reads `lockState.xiangBearing` (stored True North) for 山 lookup — NOT `compassState.heading_deg`. Live bearing fields (`bearingDeg`, `northLabel`) come from `compassState.heading_deg.toFloat()` and `compassState.north_label`. **TE-N-F04 resolution:** `northSwitch_doesNotChangeShanLabels` test MUST use different values for `lockState.xiangBearing` (e.g., 45f → 艮) and `compassState.heading_deg` (e.g., 50f → different sector) to prove the mapper reads lock state, not live bearing. |
| Files to read | `docs/luopan-compass/p3-luopan-mode/TSPEC-luopan-p3-luopan-mode.md` §5.1 (LuopanState), §4.3 (LuopanStateMapper), §2.1 (Float? types), §2.2 (declination sign convention), §2.3 (STABILIZING behavior), §11 (error handling); `docs/luopan-compass/p3-luopan-mode/FSPEC-luopan-p3-luopan-mode.md` §3 (Flow 3, all confidence states), §4.1 (LuopanState contract); `app/src/main/java/com/luopan/compass/ui/CompassUiState.kt`; `app/src/main/java/com/luopan/compass/model/OverallConfidence.kt`; `docs/luopan-compass/p3-luopan-mode/CROSS-REVIEW-test-engineer-TSPEC-v2.md` (N-F02, N-F04) |
| Files to create | `app/src/main/java/com/luopan/compass/luopan/LuopanState.kt`; `app/src/main/java/com/luopan/compass/luopan/LuopanStateMapper.kt` |
| Files to create (test) | `app/src/test/java/com/luopan/compass/luopan/LuopanStateMapperTest.kt` |
| Acceptance | `mapper_high_180_all_fields_correct`: mountainChar="午", earthlyBranchChar="午", trigramSymbol="☲", trigramName="離", trigramDirection="南", fenJinLabel="壬午分金"; `mapper_moderate_fenjin_null`: fenJinLabel is null; `mapper_poor_characters_populated`: character fields populated, fenJinLabel null; `mapper_stabilizing_characters_populated_fenjin_null`: character fields update, fenJinLabel null; `mapper_sensor_error_all_dashes`: all character fields are "—", fenJinLabel null; `mapper_show_my_language_uses_english`: Ring 3 shows English equivalent; `mapper_romanization_off_pinyin_empty`: pinyin fields empty; `northSwitch_doesNotChangeShanLabels`: uses xiangBearing=45f (艮) vs heading_deg=50f (different sector) to prove mapper reads lock state; `mapper_sensorError_while_locked_fieldsAreDashes_lockRemains`: isLockActive=true, character fields "—". |
| Depends on | Task 1.1 (SectorLookup), Task 1.2 (RingLabelProvider), Task 1.3 (ZuoXiangLock) |

---

### Task 2.2 — SettingsRepository additions

| Item | Detail |
|------|--------|
| Description | Extend `SettingsRepository` with three new persisted keys: `display_mode` (String, default "MODERN"), `luopan_show_romanization` (Boolean, default false), `luopan_show_my_language` (Boolean, default false). Use the same existing SharedPreferences file ("luopan_settings"). These MUST NOT be session-only — they survive cold starts. Session-only state (ring visibility, zoom, lock) MUST NOT be added here. |
| Files to read | `app/src/main/java/com/luopan/compass/settings/SettingsRepository.kt`; `docs/luopan-compass/p3-luopan-mode/TSPEC-luopan-p3-luopan-mode.md` §7.1 (SettingsRepository additions), §7.2 (session-only state — what NOT to add); `docs/luopan-compass/p3-luopan-mode/REQ-luopan-p3-luopan-mode.md` §5.7 (settings persistence contract); `app/src/test/java/com/luopan/compass/settings/SettingsRepositoryTest.kt` |
| Files to modify | `app/src/main/java/com/luopan/compass/settings/SettingsRepository.kt` |
| Files to modify (test) | `app/src/test/java/com/luopan/compass/settings/SettingsRepositoryTest.kt` (extend existing test file) |
| Acceptance | `luopanShowRomanization_default_false`; `luopanShowMyLanguage_default_false`; `displayMode_default_MODERN`; `displayMode_persists_LUOPAN` (write "LUOPAN", read back "LUOPAN" — round-trip); no session-only keys (ring visibility, zoom) are present in SharedPreferences. |
| Depends on | — (can start immediately; only touches SettingsRepository) |

---

### Task 2.3 — CompassViewModel extensions

| Item | Detail |
|------|--------|
| Description | Extend `CompassViewModel` with: (1) session-only state fields (`ringVisible: MutableStateFlow<BooleanArray>`, `_zoomScale: MutableStateFlow<Float>`, `zuoXiangLock: ZuoXiangLock`); (2) localization settings loaded from `SettingsRepository`; (3) `luopanState` computation in `startSensorCollection` via `LuopanStateMapper.map()` on `Dispatchers.Default`; (4) `lockXiang()` and `clearXiang()` public functions; (5) `setRingVisible()`, `setZoomScale()`, `setShowRomanization()`, `setShowMyLanguage()`, `setDisplayMode()` functions; (6) `onNorthTypeChanged()` calling `zuoXiangLock.rederive()`. Extend `CompassUiState` with `val luopan: LuopanState = LuopanState.INITIAL`. **TE-N-F02 resolution:** `lockXiang()` reads `heading_deg.toFloat()` which is the display bearing in the current north reference. Since `CompassUiState.heading_deg` already contains the display-reference-adjusted heading (True N or Mag N per `NorthTypeEngine`), `lockXiang()` must pass this to `zuoXiangLock.lock()` directly — it is already True North when north_type is TRUE. When north_type is MAGNETIC, `heading_deg` is the magnetic heading; `lockXiang()` must add `declination_deg` to convert to True North before calling `lock()`: `zuoXiangLock.lock(heading_deg.toFloat() + if (north_type == MAGNETIC) declination_deg else 0f)`. |
| Files to read | `app/src/main/java/com/luopan/compass/ui/CompassViewModel.kt`; `app/src/main/java/com/luopan/compass/ui/CompassUiState.kt`; `docs/luopan-compass/p3-luopan-mode/TSPEC-luopan-p3-luopan-mode.md` §5.2 (CompassUiState extension), §5.3 (session state in ViewModel), §8.1 (LuopanState computation), §8.2 (north type change → rederive), §8.3 (localization settings), §8.4 (mode persistence); `docs/luopan-compass/p3-luopan-mode/CROSS-REVIEW-test-engineer-TSPEC-v2.md` (N-F02 — lockXiang Mag→True conversion); `app/src/test/java/com/luopan/compass/ui/CompassViewModelTest.kt` (read existing test patterns) |
| Files to modify | `app/src/main/java/com/luopan/compass/ui/CompassViewModel.kt`; `app/src/main/java/com/luopan/compass/ui/CompassUiState.kt` |
| Files to create (test) | `app/src/test/java/com/luopan/compass/ui/CompassViewModelSessionStateTest.kt` |
| Acceptance | `ringVisibility_initializes_allTrue_on_viewModelCreation`: fresh ViewModel has `ringVisibility.value == BooleanArray(6) { true }`; `ringVisibility_notRestoredFromSettings`: no ring-visible keys read from SharedPreferences; `zoomScale_initializes_to_1f_on_viewModelCreation`: `zoomScale.value == 1.0f`; `zoomScale_notRestoredFromSettings`: no zoom key read from SharedPreferences; `lockXiang_under_magnetic_north_stores_true_north_bearing`: when `north_type == MAGNETIC` and `declination_deg == -3.5f` and `heading_deg == 48.5`, `lockXiang()` stores `xiangBearing ≈ 45.0f` (48.5 + (-3.5) = 45.0 True N); `lockXiang_at_moderate_confidence_locks`; `lockXiang_at_poor_confidence_does_not_lock`; `clearXiang_clears_lock_state`. |
| Depends on | Task 1.1 (SectorLookup), Task 1.2 (RingLabelProvider), Task 1.3 (ZuoXiangLock), Task 2.1 (LuopanState+Mapper), Task 2.2 (SettingsRepository) |

---

## Batch 3 — Navigation & Fragment Shell (depends on Batch 2)

Tasks 3.1 and 3.2 can run in parallel after Batch 2 completes.

---

### Task 3.1 — Navigation graph and Activity migration

| Item | Detail |
|------|--------|
| Description | Migrate `CompassActivity` from flat-layout to NavHostFragment + TabLayout architecture. Create `nav_graph.xml` with `dest_modern` and `dest_luopan`. Restructure `activity_compass.xml` to `LinearLayout` with `FragmentContainerView` (NavHostFragment) + `TabLayout`. Create `ModernCompassFragment` and `fragment_modern_compass.xml` by migrating the existing `CompassActivity` UI logic. Wire `TabLayout` ↔ `NavController` in `CompassActivity.onCreate`. Restore last-used mode from `SettingsRepository.displayMode` on cold start. All existing `CompassActivity` behavior (permission flow, calibration wizard, bearing capture, declination info, etc.) must continue to work after migration. |
| Files to read | `app/src/main/java/com/luopan/compass/ui/CompassActivity.kt`; `app/src/main/res/layout/activity_compass.xml`; `docs/luopan-compass/p3-luopan-mode/TSPEC-luopan-p3-luopan-mode.md` §9 (navigation architecture), §8.4 (mode persistence); `docs/luopan-compass/p3-luopan-mode/FSPEC-luopan-p3-luopan-mode.md` §1.4 (navigation contract), §2 Flow 1 (entering Luopan Mode); `app/src/androidTest/java/com/luopan/compass/ui/NoModeSwitcherTest.kt` (existing test asserting no mode switcher in Phase 2 — must be updated or removed) |
| Files to modify | `app/src/main/java/com/luopan/compass/ui/CompassActivity.kt`; `app/src/main/res/layout/activity_compass.xml` |
| Files to create | `app/src/main/java/com/luopan/compass/ui/ModernCompassFragment.kt`; `app/src/main/res/layout/fragment_modern_compass.xml`; `app/src/main/res/navigation/nav_graph.xml` |
| Files to modify (test) | `app/src/androidTest/java/com/luopan/compass/ui/NoModeSwitcherTest.kt` (update to assert TabLayout IS present in Phase 3) |
| Acceptance | App launches and shows TabLayout with "Modern" and "Luopan" tabs; tapping "Modern" tab shows existing compass UI with full Phase 1 and Phase 2 behavior; all existing instrumented tests in `ui/` still pass (permission flow, declination info, bearing capture); last-used mode is restored on cold start from `SettingsRepository`; mode transition completes within 300 ms (visual check). |
| Depends on | Task 2.2 (SettingsRepository — display_mode key), Task 2.3 (CompassViewModel — setDisplayMode) |

---

### Task 3.2 — LuopanFragment shell

| Item | Detail |
|------|--------|
| Description | Create `LuopanFragment` with full layout `fragment_luopan.xml` (ConstraintLayout with `LuopanView` placeholder, `NumericReadoutPanel`, `CardView` 坐向 overlay, `Button btnLockXiang`, pointer container). Wire `activityViewModels()` to `CompassViewModel`. Observe `uiState.luopan` and `zoomScale` and `ringVisibility` StateFlows. Implement `updateNumericReadout()`, `updateLockButton()`, and `updateZuoXiangOverlay()` with correct display-bearing conversion (`displayBearing = xiangBearing_trueN ± declinationDeg`). **V3-F01 fix:** Pass `displayXiangBearing` (not `xiangBearing`) to `LuopanView.setLockState()` for tick mark positioning. Call `viewModel.setDisplayMode(LUOPAN)` in `onStart`. `LuopanView` is included as a custom View in the layout but rendering tasks (ring drawing) are in Task 4.1. The fragment draws a dark background (#2C0E0E) immediately on entry. |
| Files to read | `docs/luopan-compass/p3-luopan-mode/TSPEC-luopan-p3-luopan-mode.md` §6.2 (LuopanFragment), §6.3 (fragment_luopan.xml layout), §5.1 (LuopanState fields), §4.4 (ZuoXiangLock display-bearing conversion); `docs/luopan-compass/p3-luopan-mode/FSPEC-luopan-p3-luopan-mode.md` §2 Flow 1 (entering Luopan Mode), Flow 3 (numerical readout), Flow 4 (坐向 lock), §5 (error states); `app/src/main/java/com/luopan/compass/luopan/LuopanState.kt` (from Task 2.1); `app/src/main/res/values/strings.xml` (existing strings) |
| Files to create | `app/src/main/java/com/luopan/compass/ui/LuopanFragment.kt`; `app/src/main/res/layout/fragment_luopan.xml` |
| Acceptance | `LuopanFragment` inflates without crash; background is #2C0E0E; confidence badge, lock button, and overlay card are present in layout; lock button is disabled at POOR confidence and enabled at HIGH/MODERATE; lock button label changes to "Clear 向" when `isLockActive`; overlay `CardView` is GONE when unlocked, VISIBLE when locked; `updateZuoXiangOverlay()` applies `displayBearing = xiangBearing ± declinationDeg` conversion and passes `displayXiangBearing` to `LuopanView.setLockState()` (not `xiangBearing`). |
| Depends on | Task 2.1 (LuopanState), Task 2.3 (CompassViewModel), Task 3.1 (nav_graph + NavHostFragment) |

---

## Batch 4 — UI Components (depends on Batch 3)

Tasks 4.1, 4.2, and 4.3 can run in parallel after Batch 3 completes.

---

### Task 4.1 — LuopanView (six-ring Canvas drawing)

| Item | Detail |
|------|--------|
| Description | Implement `LuopanView` custom Canvas View with hardware-accelerated six-ring dial. Pre-compute all ring geometry (radii, sector label positions, arc paths) in `onSizeChanged` — zero trigonometric computation in `onDraw`. Apply rotation (`canvas.rotate(-bearingDeg, cx, cy)`) and scale (`canvas.scale(zoomScale, zoomScale, cx, cy)`) transforms. Draw Ring 1 needle, Rings 2–6 labels (CJK text), ring border arcs. Attach `ScaleGestureDetector` for pinch-to-zoom and `GestureDetector` for long-press. Load `NotoSerifCJK_TC` font once via companion object. Implement public setter API: `setBearingDeg()`, `setZoomScale()`, `setRingVisible()`, `setLockState(active, displayXiangBearing)`. Note font resource `res/font/noto_serif_cjk_tc.ttf` (or `.otf`) must also be added. |
| Files to read | `docs/luopan-compass/p3-luopan-mode/TSPEC-luopan-p3-luopan-mode.md` §6.1 (LuopanView — full section including §6.1.1 geometry, §6.1.2 onDraw, §6.1.3 ring label rendering, §6.1.5 pinch-to-zoom, §6.1.6 long-press, §6.1.7 public setter API), §10 (performance strategy); `docs/luopan-compass/p3-luopan-mode/REQ-luopan-p3-luopan-mode.md` §6 (visual specification — colors, font sizes, dimensions), §11 (NFRs: ≥20 fps, ≤16 ms onDraw, ≤7% battery); `docs/luopan-compass/p3-luopan-mode/FSPEC-luopan-p3-luopan-mode.md` §2 Flow 2 (dial rotation math BR-11), Flow 5 (ring visibility), Flow 6 (pinch-to-zoom) |
| Files to create | `app/src/main/java/com/luopan/compass/ui/LuopanView.kt`; `app/src/main/res/font/noto_serif_cjk_tc.ttf` (or `.otf`) |
| Files to create (test) | `app/src/test/java/com/luopan/compass/ui/LuopanViewTest.kt` (Robolectric) |
| Acceptance | Robolectric tests pass: `setHeading_90_rotates_minus_90` (dial rotation math BR-11); `setZoomScale_below_min_clamped_to_0_8`; `setZoomScale_above_max_clamped_to_2_0`; `all_rings_hidden_no_crash`; `setLockState_active_with_display_bearing` (tick mark uses `displayXiangBearing` not raw `xiangBearing` — V3-F01 fix); pointer element is drawn outside rotation transform (never rotates). Visual inspection on emulator: all 6 rings visible at default zoom; ring labels legible; dial rotates counter-clockwise when device heading increases; font loads correctly (CJK characters render). |
| Depends on | Task 3.2 (LuopanFragment shell — provides layout context and setter API contract) |

---

### Task 4.2 — NumericReadoutPanel wiring

| Item | Detail |
|------|--------|
| Description | Complete the 6-field numerical readout panel in `LuopanFragment.updateNumericReadout()`. Implement field formatting: `山` (char + optional pinyin), `地支` (char + optional pinyin), `後天八卦` (trigram + name + direction), bearing (1 decimal), north type ("True N" / "Mag N"), 分金 (label or "N/A — calibrate for 分金 precision"), confidence badge (text + background color). Apply `showMyLanguage` toggle to character fields. Apply `showRomanization` toggle to pinyin fields. SENSOR_ERROR guard: show "—" for all computed fields except north type and confidence badge. All fields update in the same observer callback as dial rotation. |
| Files to read | `docs/luopan-compass/p3-luopan-mode/TSPEC-luopan-p3-luopan-mode.md` §6.2 (`updateNumericReadout()`, `updateLockButton()`), §5.1 (LuopanState fields); `docs/luopan-compass/p3-luopan-mode/FSPEC-luopan-p3-luopan-mode.md` §2 Flow 3 (all 6 fields, confidence state table), §3 BR-07 (分金 display rule), §5 ES-01 (SENSOR_ERROR display); `docs/luopan-compass/p3-luopan-mode/REQ-luopan-p3-luopan-mode.md` §5.5 (canonical numerical readout field order); `app/src/main/java/com/luopan/compass/ui/LuopanFragment.kt` (from Task 3.2) |
| Files to modify | `app/src/main/java/com/luopan/compass/ui/LuopanFragment.kt` |
| Files to create (test, instrumented) | `app/src/androidTest/java/com/luopan/compass/ui/LuopanFragmentTest.kt` |
| Acceptance | Instrumented tests: `lock_button_disabled_at_poor_confidence`; `lock_button_enabled_at_high_confidence`; `lock_button_enabled_when_lock_active_allowsClearing` (PM-Q01 fix); `lock_button_label_clearXiang_when_locked`; `fen_jin_shows_na_at_moderate`; `ac03_readout_shows_wu_at_180_high` (mountain field shows "午"); `ac22_northTypeSwitch_updatesReadoutImmediately` (bearing field updates + northLabel changes to "Mag N"); `ac28_sensorError_while_locked_overlayFrozen_readoutDashes` (overlay visible, readout shows "—", lock button shows "Clear 向"). |
| Depends on | Task 3.2 (LuopanFragment shell), Task 2.1 (LuopanState) |

---

### Task 4.3 — ZuoXiangOverlay + gold tick mark

| Item | Detail |
|------|--------|
| Description | Complete `LuopanFragment.updateZuoXiangOverlay()` to show/hide the 坐向 overlay CardView and format the overlay text. Implement overlay text format: "向: [mountain] ([displayBearing to 1 decimal]° [northType])" and "坐: [mountain] ([zuoDisplayBearing to 1 decimal]° [northType])". Apply declination conversion: `displayBearing = xiangBearing ± declinationDeg` (using `LuopanState.declinationDeg`). **V3-F01 fix (mandatory):** The computed `displayXiangBearing` MUST be passed to `luopanView.setLockState(active, displayXiangBearing)` — NOT `luopanState.xiangBearing`. This ensures the gold tick mark appears at the correct visual sector position when viewing Magnetic North. **TE-N-F05 requirement:** Add instrumented test asserting exact overlay display bearing: `ac23_overlay_displays_magnetic_bearing_correctly` with `xiangBearing=45.0f`, `declinationDeg=-3.5f` → overlay shows "向: 艮 (48.5° Mag N)". Hide 分金 from overlay when confidence is not HIGH (BR-07). |
| Files to read | `docs/luopan-compass/p3-luopan-mode/TSPEC-luopan-p3-luopan-mode.md` §6.1.4 (gold tick mark), §6.2 (`updateZuoXiangOverlay()`); `docs/luopan-compass/p3-luopan-mode/FSPEC-luopan-p3-luopan-mode.md` §2 Flow 4 (坐向 lock — 4a through 4e), §5 ES-03 (switching north type while locked); `docs/luopan-compass/p3-luopan-mode/REQ-luopan-p3-luopan-mode.md` §8 (坐向 lock workflow), §12 Scenario E (all sub-cases), Scenario J; `docs/luopan-compass/p3-luopan-mode/CROSS-REVIEW-test-engineer-TSPEC-v2.md` (N-F05 — AC-23 overlay test missing) |
| Files to modify | `app/src/main/java/com/luopan/compass/ui/LuopanFragment.kt` |
| Files to modify (test, instrumented) | `app/src/androidTest/java/com/luopan/compass/ui/LuopanFragmentTest.kt` |
| Acceptance | `ac07_lock_at_high_45deg_overlay_shows_gen_kun`: overlay shows "向: 艮 (45.0° True N)" and "坐: 坤 (225.0° True N)"; gold tick mark appears; `ac08_lock_at_moderate_no_fen_jin_in_overlay`: overlay shows 向/坐 labels without 分金 field; `ac10_zuo_wrapround_270_overlay_correct`: overlay shows "向: 酉 (270.0° True N)" and "坐: 卯 (90.0° True N)"; `ac11_zuo_wrapround_350_overlay_correct`: overlay shows "向: 壬 (350.0° True N)" and "坐: 丙 (170.0° True N)"; **`ac23_overlay_displays_magnetic_bearing_correctly`**: `xiangBearing=45.0f`, `declinationDeg=-3.5f` → overlay shows "向: 艮 (48.5° Mag N)" and "坐: 坤 (228.5° Mag N)"; **`v3f01_tick_mark_uses_display_bearing`**: `luopanView.setLockState()` called with `displayXiangBearing` (48.5f) not `xiangBearing` (45.0f) when in Mag N mode. |
| Depends on | Task 3.2 (LuopanFragment shell), Task 4.1 (LuopanView — `setLockState()` setter) |

---

## Batch 5 — Interaction (depends on Batch 4)

Tasks 5.1 and 5.2 can run in parallel after Batch 4 completes.

---

### Task 5.1 — Pinch-to-zoom

| Item | Detail |
|------|--------|
| Description | Complete pinch-to-zoom integration. `LuopanView` already attaches `ScaleGestureDetector` (Task 4.1). This task wires the `onZoomChanged` callback from `LuopanView` to `viewModel.setZoomScale(newScale)`. Observe `viewModel.zoomScale` StateFlow in `LuopanFragment` and call `luopanView.setZoomScale(scale)` + `luopanView.requestLayout()` on update. Verify: scale is clamped to [0.8f, 2.0f]; scale survives configuration changes (ViewModel survives config change); scale resets to 1.0f on cold start; readout panel does NOT scale. |
| Files to read | `docs/luopan-compass/p3-luopan-mode/TSPEC-luopan-p3-luopan-mode.md` §6.1.5 (pinch-to-zoom gesture), §5.3 (`_zoomScale` ViewModel field), §7.2 (session-only — NOT persisted); `docs/luopan-compass/p3-luopan-mode/FSPEC-luopan-p3-luopan-mode.md` §2 Flow 6 (pinch-to-zoom steps and scale boundaries), §3 BR-09, §5 ES-06; `docs/luopan-compass/p3-luopan-mode/REQ-luopan-p3-luopan-mode.md` §7 (interaction behavior), §5.7.2 (zoom session decision); `app/src/main/java/com/luopan/compass/ui/LuopanFragment.kt`; `app/src/main/java/com/luopan/compass/ui/LuopanView.kt` |
| Files to modify | `app/src/main/java/com/luopan/compass/ui/LuopanFragment.kt` |
| Files to modify (test, instrumented) | `app/src/androidTest/java/com/luopan/compass/ui/LuopanFragmentTest.kt` |
| Acceptance | `ac25_pinch_zoom_clamped_at_0_8_and_2_0`: scale stays within [0.8f, 2.0f]; `ac26_zoom_survives_config_change`: zoom at 1.5f persists after configuration change (rotation); `ac27_zoom_resets_on_cold_start`: fresh ViewModel has zoomScale 1.0f (from `CompassViewModelSessionStateTest` Task 2.3); readout panel is not affected by zoom (visual check). |
| Depends on | Task 4.1 (LuopanView — ScaleGestureDetector attached), Task 2.3 (CompassViewModel — setZoomScale) |

---

### Task 5.2 — Ring visibility BottomSheetDialog

| Item | Detail |
|------|--------|
| Description | Create `RingVisibilityBottomSheet` (`BottomSheetDialogFragment`) with layout `bottom_sheet_ring_visibility.xml`. Six toggle rows, one per ring, each with a `Switch` and label (ring number + Chinese + English name). Initial switch states from `viewModel.ringVisibility.value`. Toggle → `viewModel.setRingVisible(ringIndex, visible)` → immediate LuopanView re-render. Wire long-press on `LuopanView` to show sheet (via `onLongPressDetected` lambda). Wire three-dot overflow menu in Luopan Mode action bar as accessibility alternative (TalkBack). Gold tick mark hides when Ring 5 is hidden (handled by `LuopanView.setRingVisible()` — `drawGoldTickMark()` draws on Ring 5 region). |
| Files to read | `docs/luopan-compass/p3-luopan-mode/TSPEC-luopan-p3-luopan-mode.md` §6.4 (RingVisibilityBottomSheet), §6.1.6 (long-press gesture); `docs/luopan-compass/p3-luopan-mode/FSPEC-luopan-p3-luopan-mode.md` §2 Flow 5 (ring visibility toggle — all steps and decision points), §3 BR-09, §5 ES-05; `docs/luopan-compass/p3-luopan-mode/REQ-luopan-p3-luopan-mode.md` §7.1 (ring visibility menu UX specification); `app/src/main/java/com/luopan/compass/ui/LuopanFragment.kt`; `app/src/main/java/com/luopan/compass/ui/LuopanView.kt` |
| Files to create | `app/src/main/java/com/luopan/compass/ui/RingVisibilityBottomSheet.kt`; `app/src/main/res/layout/bottom_sheet_ring_visibility.xml` |
| Files to modify | `app/src/main/java/com/luopan/compass/ui/LuopanFragment.kt` |
| Files to modify (test, instrumented) | `app/src/androidTest/java/com/luopan/compass/ui/LuopanFragmentTest.kt` |
| Acceptance | `long_press_shows_ring_visibility_sheet`: ≥500 ms long-press opens BottomSheetDialog; `ac14_hide_ring4_disappears_others_remain`: hiding Ring 4 removes it from dial; Rings 1, 2, 3, 5, 6 remain; readout and heading computation unaffected; `ac15_ring_visibility_session_reset`: cold start restores all rings visible (from `CompassViewModelSessionStateTest`); gold tick mark hides when Ring 5 is hidden; overflow menu button opens same sheet (accessibility). |
| Depends on | Task 4.1 (LuopanView — long-press gesture, `setRingVisible()` setter), Task 2.3 (CompassViewModel — `setRingVisible()`, `ringVisibility` StateFlow) |

---

## Batch 6 — Localization (depends on Batch 2)

Task 6.1 can start after Batch 2 (Task 2.2 for SettingsRepository keys, Task 2.3 for ViewModel setters). It is independent of Batches 3–5 and can proceed in parallel with Batches 3–4 if resources allow.

---

### Task 6.1 — Localization toggles

| Item | Detail |
|------|--------|
| Description | Add "Show romanization" and "Show in my language" toggles accessible from the Luopan Mode overflow menu (three-dot). Wire toggles to `viewModel.setShowRomanization(v)` and `viewModel.setShowMyLanguage(v)`. Both toggles write to `SettingsRepository` (persisted) and trigger `recomputeLuopanState()` in the ViewModel. Verify: default OFF for both; system locale (en/ja) does NOT affect ring labels (BR-08); "Show romanization" ON → pinyin shown below characters on dial and in readout; "Show in my language" ON → English equivalents shown for Rings 3, 4, 5 per §5.8 mapping; entries without English equivalent stay in zh-Hant (fallback). Both toggles may be ON simultaneously. |
| Files to read | `docs/luopan-compass/p3-luopan-mode/TSPEC-luopan-p3-luopan-mode.md` §7.1 (SettingsRepository keys), §8.3 (localization settings read in ViewModel); `docs/luopan-compass/p3-luopan-mode/FSPEC-luopan-p3-luopan-mode.md` §2 Flow 7 (localization toggles — Toggle A and Toggle B), §3 BR-08 (ring label language default rule); `docs/luopan-compass/p3-luopan-mode/REQ-luopan-p3-luopan-mode.md` §5.4 (REQ-L10N-02), §5.8 ("Show in my language" mapping tables); `app/src/main/java/com/luopan/compass/luopan/RingLabelProvider.kt` (from Task 1.2 — english field) |
| Files to modify | `app/src/main/java/com/luopan/compass/ui/LuopanFragment.kt` (overflow menu + toggle wiring) |
| Files to create/modify (test, instrumented) | `app/src/androidTest/java/com/luopan/compass/ui/LuopanFragmentTest.kt` |
| Acceptance | `ac12_default_language_english_locale`: en system locale → ring labels in zh-Hant; `ac13_show_romanization_toggle`: enabling toggle → pinyin appears (e.g., "子 Zǐ" on Ring 4, "午 (Wǔ)" in readout); `mapper_show_my_language_uses_english` (unit test from Task 2.1): Ring 3 shows "Li · South" when enabled; English equivalent missing for Ring 2 entry falls back to zh-Hant character; both toggles ON simultaneously → English + pinyin; toggle persists across cold start (written to SettingsRepository). |
| Depends on | Task 2.2 (SettingsRepository — luopan_show_romanization, luopan_show_my_language keys), Task 2.3 (CompassViewModel — setShowRomanization, setShowMyLanguage), Task 1.2 (RingLabelProvider — english field populated) |

---

## Navigation & Mode-Switch Integration Test

### Task 7.1 — Navigation integration tests

| Item | Detail |
|------|--------|
| Description | Create `ModeSwitcherTest` instrumented test class. Verify: mode switch completes within 300 ms; 坐向 lock state is preserved across mode switches; both fragments share the same ViewModel instance. These tests require Tasks 3.1, 3.2, and 4.2 to be complete. |
| Files to read | `docs/luopan-compass/p3-luopan-mode/TSPEC-luopan-p3-luopan-mode.md` §12.4 (navigation integration tests), §9.4 (300 ms transition budget); `docs/luopan-compass/p3-luopan-mode/FSPEC-luopan-p3-luopan-mode.md` §2 Flow 1 (mode entry), Flow 4c (lock across mode switch); `docs/luopan-compass/p3-luopan-mode/REQ-luopan-p3-luopan-mode.md` §12 Scenario I (mode switch while locked) |
| Files to create (test, instrumented) | `app/src/androidTest/java/com/luopan/compass/ui/ModeSwitcherTest.kt` |
| Acceptance | `mode_switch_luopan_under_300ms`: tap-to-first-frame within 300 ms (measured via `SystemClock.elapsedRealtime()` before/after navigate call); `lock_preserved_across_mode_switch`: AC-21 — lock at 45° (艮→坤), switch to Modern, switch back → overlay restored; `fragments_share_viewmodel_instance`: both fragments obtain identical ViewModel instance via `activityViewModels()`. |
| Depends on | Task 3.1 (nav graph), Task 3.2 (LuopanFragment shell), Task 4.2 (NumericReadout — overlay visible) |

---

## Dependency Graph

```
Batch 1 (parallel) ──────────────────────────────────────────────┐
  Task 1.1 SectorLookup                                           │
  Task 1.2 RingLabelProvider                                      │
  1.3 ZuoXiangLock (needs 1.1 + 1.2)                             │
                                                                   ▼
Batch 2 (parallel, after Batch 1) ───────────────────────────────┐
  Task 2.1 LuopanState+Mapper (needs 1.1+1.2+1.3)                │
  Task 2.2 SettingsRepository additions (no domain dep)           │
  Task 2.3 CompassViewModel extensions (needs 1.1+1.2+1.3+2.1+2.2)│
                                                                   ▼
Batch 3 (parallel, after Batch 2) ───────────────────────────────┐
  Task 3.1 Navigation graph + ModernCompassFragment (needs 2.2+2.3)│
  Task 3.2 LuopanFragment shell (needs 2.1+2.3+3.1)              │
                                                                   ▼
Batch 4 (parallel, after Batch 3) ───────────────────────────────┐
  Task 4.1 LuopanView Canvas drawing (needs 3.2)                  │
  Task 4.2 NumericReadoutPanel wiring (needs 3.2+2.1)            │
  Task 4.3 ZuoXiangOverlay + gold tick mark (needs 3.2+4.1)      │
                                                                   ▼
Batch 5 (parallel, after Batch 4) ───────────────────────────────┐
  Task 5.1 Pinch-to-zoom (needs 4.1+2.3)                         │
  Task 5.2 Ring visibility BottomSheet (needs 4.1+2.3)           │
                                                                   ▼
Batch 6 (after Batch 2, can parallelize with Batches 3–4) ───────┐
  Task 6.1 Localization toggles (needs 2.2+2.3+1.2)              │
                                                                   ▼
Task 7.1 Navigation integration tests (after 3.1+3.2+4.2)
```

```
1.1 ──┐
1.2 ──┼──► 1.3 ──┐
      │           │
      └───────────┼──► 2.1 ──┐
                  │           │
      2.2 ────────┼───────────┼──► 2.3 ──┐
                  │           │           │
                  └───────────┘           │
                                          │
                    ┌─────────────────────┘
                    ▼
              3.1 ◄─── 2.2, 2.3
              3.2 ◄─── 2.1, 2.3, 3.1
                    │
                    ▼
              4.1 ◄─── 3.2
              4.2 ◄─── 3.2, 2.1
              4.3 ◄─── 3.2, 4.1
                    │
                    ▼
              5.1 ◄─── 4.1, 2.3
              5.2 ◄─── 4.1, 2.3
                    │
              6.1 ◄─── 2.2, 2.3, 1.2  (can run alongside Batches 3–4)
                    │
              7.1 ◄─── 3.1, 3.2, 4.2
```

---

## Definition of Done

- [ ] All new Kotlin source files compile without warnings
- [ ] All unit tests (`src/test`) pass (JVM, no emulator required)
- [ ] All Robolectric tests (`LuopanViewTest`) pass
- [ ] All instrumented tests (`src/androidTest`) pass on API 26 emulator
- [ ] V3-F01: `LuopanView.setLockState()` receives `displayXiangBearing` (not raw `xiangBearing`) from `LuopanFragment` — verified by `v3f01_tick_mark_uses_display_bearing` instrumented test
- [ ] TE-N-F02 resolution: `lockXiang()` adds `declination_deg` when `north_type == MAGNETIC` — verified by `lockXiang_under_magnetic_north_stores_true_north_bearing` unit test
- [ ] TE-N-F01 resolution: `rederive_northSwitch_doesNotChangeStoredTrueNorthBearing` rewritten to actually call `rederive()` with a new bearing
- [ ] TE-N-F04 resolution: `northSwitch_doesNotChangeShanLabels` uses different bearings for `lockState.xiangBearing` vs `compassState.heading_deg`
- [ ] TE-N-F05 resolution: `ac23_overlay_displays_magnetic_bearing_correctly` instrumented test passes
- [ ] All existing Phase 1 and Phase 2 instrumented tests still pass (no regression)
- [ ] `NoModeSwitcherTest` updated to assert TabLayout IS present (not absent)
- [ ] REQ-NFR-LUOPAN-02: measured `onDraw` time ≤ 16 ms on API 26 mid-range hardware (via `FrameMetrics` log)
- [ ] REQ-NFR-LUOPAN-04: mode-switch transition completes ≤ 300 ms (verified by `mode_switch_luopan_under_300ms`)
- [ ] Ring 6 六十分金 table carries practitioner validation notice (OQ-P3-01 is unresolved — engineering implements table but labels are not final)
- [ ] `display_mode`, `luopan_show_romanization`, `luopan_show_my_language` persist across cold start
- [ ] Session-only state (ring visibility, zoom, lock) does NOT persist across cold start
- [ ] Default ring labels render in Traditional Chinese regardless of system locale (en/ja tested)
