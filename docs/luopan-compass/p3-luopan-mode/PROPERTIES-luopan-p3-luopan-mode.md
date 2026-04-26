# PROPERTIES-luopan-p3-luopan-mode

| Field | Value |
|-------|-------|
| Version | 1.0 |
| Date | 2026-04-25 |
| Based on | TSPEC v1.1, FSPEC v1.1, REQ v0.3-draft |
| Status | Draft |

> **Note on input documents:** The task referenced TSPEC v1.2 and PLAN v1.0; at time of authoring, TSPEC v1.1 is the latest available version and no PLAN document exists in the repository. This document is based on TSPEC v1.1, FSPEC v1.1, and REQ v0.3-draft. The PROPERTIES document will be updated when TSPEC v1.2 and PLAN v1.0 are published.

---

## Coverage Matrix

| Group | Properties | Test Level | REQ / AC Coverage |
|-------|-----------|------------|-------------------|
| PG-01: Sector Assignment | 001–014 | Unit | BR-01, BR-02, BR-03, BR-04, Scenario H, REQ §5.3b |
| PG-02: ZuoXiangLock Invariants | 015–025 | Unit | BR-06, BR-10, AC-07–11, AC-21, AC-23, FSPEC §4a–4d |
| PG-03: LuopanState Mapper Invariants | 026–036 | Unit | BR-05, BR-07, BR-08, AC-03–06, AC-22, AC-24, AC-29 |
| PG-04: LuopanView Rendering | 037–044 | Unit/Integration | BR-11, AC-02a, AC-02b, REQ §5.3, FSPEC Flow 2, §6.1 |
| PG-05: Settings Persistence | 045–052 | Integration | BR-09, AC-15, AC-26, AC-27, REQ §5.7, FSPEC §7 |
| PG-06: Navigation | 053–057 | Integration/E2E | AC-01, AC-21, Scenario I, REQ §9 |
| PG-07: Localization | 058–064 | Integration | BR-08, AC-12, AC-13, REQ-L10N-02, FSPEC Flow 7 |
| PG-08: Performance | 065–068 | E2E | REQ-NFR-LUOPAN-01–04 |
| PG-09: 坐向 Lock UI | 069–076 | E2E | AC-07–09, AC-28, BR-05, FSPEC §4e, §6.2 |

---

## Property Groups

---

### PG-01: Sector Assignment (Unit)

Properties of `SectorLookup` — must hold for all ring/bearing inputs. All sectors use inclusive-left, exclusive-right `[start°, end°)` intervals (BR-01). Wrap-around sectors straddle 0°/360°.

**Test class:** `SectorLookupTest`

---

#### PROP-01-001

| Item | Detail |
|------|--------|
| Property | For any normalized bearing B ∈ [0°, 360°), `SectorLookup` assigns B to **exactly one** sector per ring — no two sectors overlap and no bearing is left unassigned |
| Negative | No ring lookup must ever throw `IllegalStateException` for any valid normalized bearing in [0°, 360°) |
| Test level | Unit |
| Test class | `SectorLookupTest` |
| REQ / AC | BR-01, REQ §5.3b |

---

#### PROP-01-002

| Item | Detail |
|------|--------|
| Property | For any sector with range `[a°, b°)` (non-wrap), bearing `a` belongs to that sector and bearing `b` belongs to the **next** sector |
| Test cases | Ring 4: `44.9°` → 丑 (index 1); `45.0°` → 寅 (index 2). Ring 5: `22.4°` → 癸 (index 2); `22.5°` → 丑 (index 3). Ring 3: `67.4°` → ☶ 艮 東北 (index 1); `67.5°` → ☳ 震 東 (index 2) |
| Test level | Unit |
| Test class | `SectorLookupTest` |
| REQ / AC | BR-01, AC-17, AC-18, AC-19, Scenario H |

---

#### PROP-01-003

| Item | Detail |
|------|--------|
| Property | Ring 4 (十二地支) 子 wrap-around sector `[345°, 15°)` correctly classifies: `344.9°` → 亥, `345.0°` → 子, `14.9°` → 子, `15.0°` → 丑 |
| Test level | Unit |
| Test class | `SectorLookupTest` |
| REQ / AC | BR-02, AC-16, Scenario H |

---

#### PROP-01-004

| Item | Detail |
|------|--------|
| Property | Ring 6 (六十分金) 壬子分金 wrap-around sector `[358°, 4°)` correctly classifies: `357.9°` → 庚子分金, `358.0°` → 壬子分金, `0.0°` → 壬子分金, `4.0°` → 甲子分金 |
| Test level | Unit |
| Test class | `SectorLookupTest` |
| REQ / AC | BR-04, AC-20 |

---

#### PROP-01-005

| Item | Detail |
|------|--------|
| Property | Ring 2 (先天八卦) 巽 wrap-around sector `[337.5°, 22.5°)` correctly classifies: `337.4°` → ☵ 坎 西北 (index 7), `337.5°` → ☴ 巽 北 (index 0), `0°` → ☴ 巽 北, `22.4°` → ☴ 巽 北, `22.5°` → ☳ 震 東北 (index 1) |
| Test level | Unit |
| Test class | `SectorLookupTest` |
| REQ / AC | BR-01, REQ §5.3a, TSPEC §4.1 (TE-F01 correction) |

---

#### PROP-01-006

| Item | Detail |
|------|--------|
| Property | Ring 2 乾 sector `[157.5°, 202.5°)` places ☰ 乾 at South: `157.4°` → ☱ 兌 (index 3), `157.5°` → ☰ 乾 (index 4), `202.4°` → ☰ 乾, `202.5°` → ☷ 坤 (index 5) |
| Test level | Unit |
| Test class | `SectorLookupTest` |
| REQ / AC | BR-01, REQ §5.3a, TSPEC §4.1 (TE-F01 correction) |

---

#### PROP-01-007

| Item | Detail |
|------|--------|
| Property | Ring 3 (後天八卦) ☵ 坎 北 wrap-around sector `[337.5°, 22.5°)` correctly classifies: `22.4°` → ☵ 坎 北, `22.5°` → ☶ 艮 東北 |
| Test level | Unit |
| Test class | `SectorLookupTest` |
| REQ / AC | BR-03, AC-19, Scenario H |

---

#### PROP-01-008

| Item | Detail |
|------|--------|
| Property | Ring 5 (二十四山) 子 wrap-around sector `[352.5°, 7.5°)` correctly classifies: `7.4°` → 子 (index 1), `7.5°` → 癸 (index 2) |
| Test level | Unit |
| Test class | `SectorLookupTest` |
| REQ / AC | BR-03, AC-18, Scenario H |

---

#### PROP-01-009

| Item | Detail |
|------|--------|
| Property | All 8 Ring 3 sectors produce correct trigram + 卦名 + 方位 labels via `RingLabelProvider.ring3Label()`. Spot checks: index 4 → `LabelData(character="☲ 離 南", pinyin="Lí · Nán", english="Li · South")`; index 0 → `LabelData(character="☵ 坎 北", ..., english="Kan · North")` |
| Test level | Unit |
| Test class | `RingLabelProviderTest` |
| REQ / AC | REQ-LUOPAN-01, AC-03, AC-04, FSPEC §4.4 |

---

#### PROP-01-010

| Item | Detail |
|------|--------|
| Property | All 24 Ring 5 sectors produce correct 山 characters and pinyin via `RingLabelProvider.ring5Label()`. Spot check: index 13 → `LabelData(character="午", pinyin="Wǔ", english="Horse")` |
| Test level | Unit |
| Test class | `RingLabelProviderTest` |
| REQ / AC | REQ-DISPLAY-05, FSPEC §4.6 |

---

#### PROP-01-011

| Item | Detail |
|------|--------|
| Property | `RingLabelProvider` arrays are complete: ring2 has exactly 8 entries, ring3 has 8, ring4 has 12, ring5 has 24, ring6 has 60 — all with non-empty `character` strings |
| Negative | No `LabelData.character` field must be an empty string or null across any ring |
| Test level | Unit |
| Test class | `RingLabelProviderTest` |
| REQ / AC | REQ §5.3, TSPEC §12.1.4 (TE-F12) |

---

#### PROP-01-012

| Item | Detail |
|------|--------|
| Property | Key Ring 6 test bearings produce correct 分金 labels: `180°` → 壬午分金, `0°` → 壬子分金, `90°` → 壬卯分金 |
| Test level | Unit |
| Test class | `SectorLookupTest` |
| REQ / AC | REQ §5.6, AC-20, FSPEC §4.7 |

---

#### PROP-01-013

| Item | Detail |
|------|--------|
| Property | Bearing normalization in `SectorLookup` handles edge values: `360.0f` is treated identically to `0.0f` for all rings; negative inputs are normalized to `[0°, 360°)` before lookup |
| Test level | Unit |
| Test class | `SectorLookupTest` |
| REQ / AC | BR-01, TSPEC §4.1 |

---

#### PROP-01-014

| Item | Detail |
|------|--------|
| Property | Ring 2 uses the Fuxi / Earlier Heaven (先天) arrangement — NOT the King Wen arrangement. Specifically, ☰ 乾 maps to South (center 180°) in Ring 2, while ☲ 離 maps to South (center 180°) in Ring 3 |
| Negative | Ring 2 must NOT use the King Wen arrangement (i.e., Ring 2 index 4 must be ☰ 乾, NOT ☲ 離) |
| Test level | Unit |
| Test class | `RingLabelProviderTest` |
| REQ / AC | REQ §5.3a, TSPEC §4.1 (TE-F01), FSPEC §4.3 |

---

### PG-02: ZuoXiangLock Invariants (Unit)

Properties of `ZuoXiangLock` — the lock state machine that records 向 bearing and derives 坐 bearing.

**Test class:** `ZuoXiangLockTest`

---

#### PROP-02-015

| Item | Detail |
|------|--------|
| Property | `xiangBearing` is always stored as a True North Float bearing, regardless of the north type (True N or Magnetic N) active at the time of locking |
| Test level | Unit |
| Test class | `ZuoXiangLockTest` |
| REQ / AC | BR-06, FSPEC §4a step 3, AC-23 |

---

#### PROP-02-016

| Item | Detail |
|------|--------|
| Property | After `lock(bearing)`, `zuoBearing = (xiangBearing + 180f) % 360f` holds for all inputs. Spot checks: `lock(270f)` → `zuoBearing = 90f`; `lock(350f)` → `zuoBearing = 170f`; `lock(0f)` → `zuoBearing = 180f`; `lock(180f)` → `zuoBearing = 0f` |
| Test level | Unit |
| Test class | `ZuoXiangLockTest` |
| REQ / AC | BR-06, AC-10, AC-11 |

---

#### PROP-02-017

| Item | Detail |
|------|--------|
| Property | `rederive(trueBearing)` does NOT modify `LockState.xiangBearing` to a different stored value — it calls `lock(trueBearing)` with the new True North bearing as the updated stored anchor; the stored bearing is always the authoritative True North value |
| Test level | Unit |
| Test class | `ZuoXiangLockTest` |
| REQ / AC | FSPEC §4d, AC-23, TSPEC §4.4 |

---

#### PROP-02-018

| Item | Detail |
|------|--------|
| Property | When north type switches to Magnetic while lock is active, the stored `xiangBearing` (True North) does NOT change; only display conversion (`displayBearing = xiangBearing_trueN ± declinationDeg`) changes in the View layer |
| Negative | `ZuoXiangLock` must NOT apply declination to the stored `xiangBearing` field |
| Test level | Unit |
| Test class | `ZuoXiangLockTest` |
| REQ / AC | FSPEC §4d, ES-03, AC-23, TSPEC §4.4 |

---

#### PROP-02-019

| Item | Detail |
|------|--------|
| Property | 山 labels (`xiangMountain`, `zuoMountain`) are always derived from the True North bearing using the Ring 5 LUT; they do NOT change when north type switches from True N to Magnetic N |
| Test level | Unit |
| Test class | `ZuoXiangLockTest` |
| REQ / AC | FSPEC §4d key principle, AC-23 |

---

#### PROP-02-020

| Item | Detail |
|------|--------|
| Property | Lock state (`isLockActive`, `xiangBearing`, `xiangMountain`, `zuoBearing`, `zuoMountain`) persists across `LuopanFragment` destruction and recreation (i.e., configuration changes) because it lives in `CompassViewModel` which survives config changes |
| Test level | Unit (ViewModel test) |
| Test class | `CompassViewModelSessionStateTest` |
| REQ / AC | BR-10, Scenario I |

---

#### PROP-02-021

| Item | Detail |
|------|--------|
| Property | Lock is cleared on ViewModel destruction (process death / cold start): after a fresh `CompassViewModel` creation, `ZuoXiangLock.lockState.isLockActive == false` and all bearing fields are `null` |
| Test level | Unit (ViewModel test) |
| Test class | `CompassViewModelSessionStateTest` |
| REQ / AC | BR-10, REQ §8 |

---

#### PROP-02-022

| Item | Detail |
|------|--------|
| Property | `clear()` resets all `LockState` fields: `isLockActive = false`, `xiangBearing = null`, `xiangMountain = null`, `zuoBearing = null`, `zuoMountain = null` |
| Test level | Unit |
| Test class | `ZuoXiangLockTest` |
| REQ / AC | FSPEC §4b, BR-10 |

---

#### PROP-02-023

| Item | Detail |
|------|--------|
| Property | `lock(45.0f)` produces `xiangMountain = "艮"` and `zuoMountain = "坤"` (Ring 5 sector lookup for 45° and 225°) |
| Test level | Unit |
| Test class | `ZuoXiangLockTest` |
| REQ / AC | BR-06, AC-07 |

---

#### PROP-02-024

| Item | Detail |
|------|--------|
| Property | `lock(90.0f)` produces `xiangMountain = "卯"` and `zuoMountain = "酉"` (Ring 5 sector lookup for 90° and 270°) |
| Test level | Unit |
| Test class | `ZuoXiangLockTest` |
| REQ / AC | BR-06, AC-08 |

---

#### PROP-02-025

| Item | Detail |
|------|--------|
| Property | Concurrent calls to `lock()` and `clear()` must not produce a torn state where `isLockActive = true` with `xiangBearing = null`, or `isLockActive = false` with `xiangBearing != null` (ensured by `AtomicReference<LockState>`) |
| Test level | Unit |
| Test class | `ZuoXiangLockTest` |
| REQ / AC | TSPEC §4.4 (TE-F04) |

---

### PG-03: LuopanState Mapper Invariants (Unit)

Properties of `LuopanStateMapper.map()` — the pure function that converts `CompassUiState` + session state into a fully populated `LuopanState`.

**Test class:** `LuopanStateMapperTest`

---

#### PROP-03-026

| Item | Detail |
|------|--------|
| Property | When `confidence == SENSOR_ERROR`, all character fields (`mountainChar`, `earthlyBranchChar`, `trigramSymbol`, `trigramName`, `trigramDirection`) are set to `"—"` and `fenJinLabel` is `null`, regardless of `bearingDeg` |
| Test level | Unit |
| Test class | `LuopanStateMapperTest` |
| REQ / AC | AC-24, ES-01, FSPEC §5 ES-01, TSPEC §11.1 |

---

#### PROP-03-027

| Item | Detail |
|------|--------|
| Property | When `confidence == POOR`, character fields (`mountainChar`, `earthlyBranchChar`, `trigramSymbol`, `trigramName`, `trigramDirection`) are populated from the sector lookup (not `"—"`), and `fenJinLabel` is `null` |
| Test level | Unit |
| Test class | `LuopanStateMapperTest` |
| REQ / AC | BR-05, BR-07, FSPEC Flow 3 table |

---

#### PROP-03-028

| Item | Detail |
|------|--------|
| Property | When `confidence == STABILIZING`, all sector character fields continue to update from the live `bearingDeg` (dial still rotates); `fenJinLabel` is `null`; `confidence` in `LuopanState` is `STABILIZING` |
| Negative | `STABILIZING` must NOT set character fields to `"—"` (unlike `SENSOR_ERROR`) |
| Test level | Unit |
| Test class | `LuopanStateMapperTest` |
| REQ / AC | AC-29, ES-02, FSPEC §5 ES-02, TSPEC §2.3 |

---

#### PROP-03-029

| Item | Detail |
|------|--------|
| Property | When `confidence == MODERATE`, all sector character fields are populated; `fenJinLabel` is `null` |
| Test level | Unit |
| Test class | `LuopanStateMapperTest` |
| REQ / AC | BR-05, BR-07, AC-06 |

---

#### PROP-03-030

| Item | Detail |
|------|--------|
| Property | When `confidence == HIGH`, all sector character fields are populated AND `fenJinLabel` is non-null (contains the 分金 label for the current bearing) |
| Test level | Unit |
| Test class | `LuopanStateMapperTest` |
| REQ / AC | BR-07, AC-03, AC-05, AC-12 |

---

#### PROP-03-031

| Item | Detail |
|------|--------|
| Property | `northLabel` is `"True N"` when `CompassUiState.northLabel == "True N"` and `"Mag N"` when `CompassUiState.northLabel == "Mag N"` — passed through without modification |
| Test level | Unit |
| Test class | `LuopanStateMapperTest` |
| REQ / AC | FSPEC §4.1, Scenario J, AC-22 |

---

#### PROP-03-032

| Item | Detail |
|------|--------|
| Property | 山 labels (Ring 5) in `LuopanState` are always derived from the True North bearing, making them invariant to north type switches. When `compassState.northLabel` changes from "True N" to "Mag N", `mountainChar` does NOT change if the True North bearing is unchanged |
| Test level | Unit |
| Test class | `LuopanStateMapperTest` |
| REQ / AC | FSPEC §4d key principle, AC-23 |

---

#### PROP-03-033

| Item | Detail |
|------|--------|
| Property | At bearing 180°, `confidence = HIGH`, `northLabel = "True N"`: mapper produces `mountainChar = "午"`, `earthlyBranchChar = "午"`, `trigramSymbol = "☲"`, `trigramName = "離"`, `trigramDirection = "南"`, `fenJinLabel = "壬午分金"` |
| Test level | Unit |
| Test class | `LuopanStateMapperTest` |
| REQ / AC | AC-03, Scenario B, REQ §5.5 |

---

#### PROP-03-034

| Item | Detail |
|------|--------|
| Property | At bearing 90°, `confidence = HIGH`: mapper produces `mountainChar = "卯"` (Ring 5), `earthlyBranchChar = "卯"` (Ring 4), `trigramSymbol = "☳"`, `trigramName = "震"`, `trigramDirection = "東"`, `fenJinLabel = "壬卯分金"` |
| Test level | Unit |
| Test class | `LuopanStateMapperTest` |
| REQ / AC | AC-04, AC-05, Scenario C / C-1 |

---

#### PROP-03-035

| Item | Detail |
|------|--------|
| Property | When `showMyLanguage = true`, the mapper uses `LabelData.english` (where non-empty) for `trigramName`, `trigramDirection`, `earthlyBranchChar`, and `mountainChar`. Trigram symbols (`trigramSymbol`) are retained regardless |
| Negative | Trigram Unicode symbols (☰ ☱ ☲ ☳ ☴ ☵ ☶ ☷) must NEVER be replaced, even when `showMyLanguage = true` |
| Test level | Unit |
| Test class | `LuopanStateMapperTest` |
| REQ / AC | BR-08, REQ §5.8, FSPEC §7 Toggle B |

---

#### PROP-03-036

| Item | Detail |
|------|--------|
| Property | When `showRomanization = false`, `mountainPinyin` and `earthlyBranchPinyin` are empty strings in `LuopanState`; when `showRomanization = true`, they contain the correct pinyin (e.g., `mountainPinyin = "Wǔ"` at 180°) |
| Test level | Unit |
| Test class | `LuopanStateMapperTest` |
| REQ / AC | AC-13, REQ-L10N-02, FSPEC §7 Toggle A |

---

### PG-04: LuopanView Rendering (Unit/Integration)

Properties of `LuopanView` Canvas rendering — geometry, rotation math, and pointer behavior.

**Test classes:** `LuopanViewTest` (Robolectric), `LuopanFragmentTest` (Instrumented)

---

#### PROP-04-037

| Item | Detail |
|------|--------|
| Property | `dialRotationDeg = -bearingDeg (mod 360°)`. When `bearingDeg = 0°`, dial rotation is `0°`. When `bearingDeg = 90°`, dial rotation is `-90°` (equivalently 270° CCW). When `bearingDeg = 180°`, dial rotation is `180°` |
| Test level | Unit (Robolectric) |
| Test class | `LuopanViewTest` |
| REQ / AC | BR-11, AC-02a, FSPEC §3 BR-11 |

---

#### PROP-04-038

| Item | Detail |
|------|--------|
| Property | The pointer element (gold triangle at screen top center) does NOT participate in the ring assembly rotation transform — it is drawn outside `canvas.rotate()`, after `canvas.restore()`, and remains visually fixed at screen top center for any bearing value |
| Negative | The pointer must NEVER rotate with the ring assembly |
| Test level | Unit (Robolectric) |
| Test class | `LuopanViewTest` |
| REQ / AC | BR-11, AC-02b, FSPEC §6.1.2 |

---

#### PROP-04-039

| Item | Detail |
|------|--------|
| Property | When `isLockActive = true`, the gold tick mark is drawn at `xiangBearing` degrees **inside** the rotation transform (so it rotates with the dial and appears at the physically-correct bearing position); when `ringVisible[4] = false` (Ring 5 hidden), the tick mark is also not drawn |
| Negative | Gold tick mark must NOT be drawn outside the rotation transform (it must physically track the locked bearing as the device rotates) |
| Test level | Unit (Robolectric) / Integration |
| Test class | `LuopanViewTest`, `LuopanFragmentTest` |
| REQ / AC | FSPEC §4a step 7b, AC-07, V3-F01 fix noted in task spec |

---

#### PROP-04-040

| Item | Detail |
|------|--------|
| Property | All 6 rings are drawn with angular widths matching the spec (Ring 2: 45°/sector, Ring 3: 45°/sector, Ring 4: 30°/sector, Ring 5: 15°/sector, Ring 6: 6°/sector) — verified by checking that 8, 8, 12, 24, and 60 label positions are evenly distributed at the correct angular spacing |
| Test level | Unit (Robolectric) |
| Test class | `LuopanViewTest` |
| REQ / AC | REQ §5.3, TSPEC §6.1 |

---

#### PROP-04-041

| Item | Detail |
|------|--------|
| Property | When `ringVisible[i] = false` for ring `i`, `onDraw` skips `drawRingNLabels()` for that ring entirely — the hidden ring produces no draw calls |
| Test level | Unit (Robolectric) |
| Test class | `LuopanViewTest` |
| REQ / AC | FSPEC Flow 5, AC-14, ES-05 |

---

#### PROP-04-042

| Item | Detail |
|------|--------|
| Property | Zoom scale is clamped to `[0.8f, 2.0f]`: `setZoomScale(0.5f)` results in effective scale `0.8f`; `setZoomScale(3.0f)` results in effective scale `2.0f` |
| Test level | Unit (Robolectric) |
| Test class | `LuopanViewTest` |
| REQ / AC | AC-25, FSPEC Flow 6, ES-06 |

---

#### PROP-04-043

| Item | Detail |
|------|--------|
| Property | Ring geometry (arc paths, label positions) is pre-computed in `onSizeChanged` and cached; `onDraw` reads only cached values and performs no trigonometric computation |
| Negative | `onDraw` must NOT allocate new `Paint` objects, compute trigonometric functions, or allocate `Path` objects |
| Test level | Integration (FrameMetrics instrumentation) |
| Test class | Performance instrumented test |
| REQ / AC | REQ-NFR-LUOPAN-02, TSPEC §10.1 |

---

#### PROP-04-044

| Item | Detail |
|------|--------|
| Property | Scale transform is applied before rotation transform in `onDraw` (zoom applied first, then bearing-based rotation), ensuring uniform dial expansion before heading-based counter-rotation |
| Test level | Unit (Robolectric) |
| Test class | `LuopanViewTest` |
| REQ / AC | FSPEC Flow 2 step 8, TSPEC §6.1.2 |

---

### PG-05: Settings Persistence (Integration)

Properties of `SettingsRepository` persistence contract — which settings survive cold start and which are session-only.

**Test classes:** `SettingsRepositoryTest`, `CompassViewModelSessionStateTest`

---

#### PROP-05-045

| Item | Detail |
|------|--------|
| Property | `display_mode` persists across app restarts: after setting `displayMode = "LUOPAN"` and restarting (fresh `SettingsRepository` read), `displayMode` returns `"LUOPAN"` |
| Test level | Integration |
| Test class | `SettingsRepositoryTest` |
| REQ / AC | BR-09, REQ §5.7, FSPEC §7 |

---

#### PROP-05-046

| Item | Detail |
|------|--------|
| Property | `luopan_show_romanization` persists across app restarts: after setting `luopanShowRomanization = true` and restarting (fresh `SettingsRepository` read), it returns `true` |
| Test level | Integration |
| Test class | `SettingsRepositoryTest` |
| REQ / AC | BR-09, REQ §5.7, FSPEC Flow 7 Toggle A |

---

#### PROP-05-047

| Item | Detail |
|------|--------|
| Property | `luopan_show_my_language` persists across app restarts: after setting `luopanShowMyLanguage = true` and restarting, it returns `true` |
| Test level | Integration |
| Test class | `SettingsRepositoryTest` |
| REQ / AC | BR-09, REQ §5.7, FSPEC Flow 7 Toggle B |

---

#### PROP-05-048

| Item | Detail |
|------|--------|
| Property | Ring visibility flags (`luopan_ring_visible_1` through `_6`) are session-only: they are NEVER written to `SettingsRepository`. A fresh `CompassViewModel` initializes all ring visibility flags to `true`, even if SharedPreferences contained any ring-visibility keys from a previous erroneous write |
| Negative | `SettingsRepository` must NOT contain any `luopan_ring_visible_*` keys |
| Test level | Integration |
| Test class | `CompassViewModelSessionStateTest` |
| REQ / AC | BR-09, AC-15, REQ §5.7 / §5.7.1 |

---

#### PROP-05-049

| Item | Detail |
|------|--------|
| Property | Zoom scale (`luopan_zoom_scale`) is session-only: it is NEVER written to `SettingsRepository`. A fresh `CompassViewModel` initializes `_zoomScale` to `1.0f` |
| Negative | `SettingsRepository` must NOT contain a `luopan_zoom_scale` key |
| Test level | Integration |
| Test class | `CompassViewModelSessionStateTest` |
| REQ / AC | BR-09, AC-27, REQ §5.7 / §5.7.2 |

---

#### PROP-05-050

| Item | Detail |
|------|--------|
| Property | Zoom scale survives configuration changes (e.g., screen rotation): after setting zoom to `1.5f` then triggering a config change (ViewModel not destroyed), `zoomScale.value` remains `1.5f` |
| Test level | Integration |
| Test class | `LuopanFragmentTest` (Instrumented) |
| REQ / AC | AC-26, FSPEC Flow 6 |

---

#### PROP-05-051

| Item | Detail |
|------|--------|
| Property | Default values on first install: `luopanShowRomanization = false`, `luopanShowMyLanguage = false`, `displayMode = "MODERN"` |
| Test level | Integration |
| Test class | `SettingsRepositoryTest` |
| REQ / AC | BR-09, FSPEC §7 defaults |

---

#### PROP-05-052

| Item | Detail |
|------|--------|
| Property | 坐向 lock state is session-only and NOT written to `SettingsRepository`: after a cold start, `ZuoXiangLock.lockState.isLockActive == false` regardless of any lock that was active in the previous session |
| Test level | Integration |
| Test class | `CompassViewModelSessionStateTest` |
| REQ / AC | BR-10, REQ §8 |

---

### PG-06: Navigation (Integration/E2E)

Properties of the NavController / TabLayout wiring and ViewModel sharing across fragment transitions.

**Test classes:** `ModeSwitcherTest` (Instrumented)

---

#### PROP-06-053

| Item | Detail |
|------|--------|
| Property | Tapping the Luopan tab navigates to `LuopanFragment` and the first complete dial frame is rendered within 300 ms of the tap |
| Test level | E2E |
| Test class | `ModeSwitcherTest` |
| REQ / AC | AC-01, Scenario A, REQ-NFR-LUOPAN-04 |

---

#### PROP-06-054

| Item | Detail |
|------|--------|
| Property | The back stack never contains more than one `LuopanFragment` instance: repeated taps on the Luopan tab do not stack multiple `LuopanFragment` instances |
| Test level | Integration |
| Test class | `ModeSwitcherTest` |
| REQ / AC | FSPEC §1.4 back-stack behavior |

---

#### PROP-06-055

| Item | Detail |
|------|--------|
| Property | Both `ModernCompassFragment` and `LuopanFragment` obtain the **same** `CompassViewModel` instance via `activityViewModels()` — verified by identity (`===` equality) |
| Test level | Integration |
| Test class | `ModeSwitcherTest` |
| REQ / AC | REQ §9.2, FSPEC §1.3 |

---

#### PROP-06-056

| Item | Detail |
|------|--------|
| Property | 坐向 lock state is preserved when switching from Luopan Mode to Modern Mode and back: after locking at 45° in Luopan Mode, switching to Modern Mode, then returning to Luopan Mode, the overlay shows "向: 艮 (45.0° True N)" and "坐: 坤 (225.0° True N)" |
| Test level | Integration |
| Test class | `ModeSwitcherTest` |
| REQ / AC | AC-21, Scenario I, BR-10, FSPEC §4c |

---

#### PROP-06-057

| Item | Detail |
|------|--------|
| Property | The sensor pipeline continues computing heading uninterrupted during the mode switch — `CompassViewModel.uiState` emits new values throughout the tab transition (no heading freeze due to navigation) |
| Test level | Integration |
| Test class | `ModeSwitcherTest` |
| REQ / AC | REQ §9.2, FSPEC §1.3 assumption 4 |

---

### PG-07: Localization (Integration)

Properties of the localization system — default language, romanization toggle, and English toggle.

**Test classes:** `LuopanFragmentTest` (Instrumented)

---

#### PROP-07-058

| Item | Detail |
|------|--------|
| Property | Ring labels display in Traditional Chinese (zh-Hant) by default when system locale is `en` and both localization toggles are OFF |
| Negative | An English system locale must NOT cause English labels to appear on the dial or in the readout panel |
| Test level | Integration |
| Test class | `LuopanFragmentTest` |
| REQ / AC | BR-08, AC-12, Scenario F |

---

#### PROP-07-059

| Item | Detail |
|------|--------|
| Property | Ring labels display in Traditional Chinese (zh-Hant) by default when system locale is `ja` and both localization toggles are OFF |
| Negative | A Japanese system locale must NOT cause Japanese characters to appear in place of Chinese characters |
| Test level | Integration |
| Test class | `LuopanFragmentTest` |
| REQ / AC | BR-08, Scenario F |

---

#### PROP-07-060

| Item | Detail |
|------|--------|
| Property | When "Show romanization" is ON, pinyin appears alongside (below) each character label on the dial rings AND in the numerical readout panel. Example: Ring 4 shows "子 Zǐ"; readout panel mountain field shows "午 (Wǔ)" |
| Test level | Integration |
| Test class | `LuopanFragmentTest` |
| REQ / AC | AC-13, Scenario F, REQ-L10N-02 |

---

#### PROP-07-061

| Item | Detail |
|------|--------|
| Property | When "Show in my language" is ON, ring labels use English equivalents from the §5.8 mapping tables on both the dial AND in the numerical readout panel. Example: Ring 3 readout shows "Zhen · East" instead of "☳ 震 東"; trigram symbol "☳" is retained |
| Test level | Integration |
| Test class | `LuopanFragmentTest` |
| REQ / AC | BR-08, REQ §5.8, FSPEC §7 Toggle B, REQ-L10N-02 |

---

#### PROP-07-062

| Item | Detail |
|------|--------|
| Property | Both toggles can be active simultaneously without conflict: when both "Show romanization" and "Show in my language" are ON, English labels are shown with pinyin romanization below them |
| Test level | Integration |
| Test class | `LuopanFragmentTest` |
| REQ / AC | FSPEC §7 independence note |

---

#### PROP-07-063

| Item | Detail |
|------|--------|
| Property | Trigram Unicode symbols (☰ ☱ ☲ ☳ ☴ ☵ ☶ ☷) are retained in the `trigramSymbol` field and rendered on dial in ALL language modes — never replaced by English text |
| Negative | "Show in my language" must NOT replace trigram symbols with text equivalents |
| Test level | Integration |
| Test class | `LuopanFragmentTest` |
| REQ / AC | REQ §5.8 note, FSPEC §7 Toggle B |

---

#### PROP-07-064

| Item | Detail |
|------|--------|
| Property | Both localization toggles apply uniformly to ring labels AND all character fields in the numerical readout panel — there is no state where the dial uses zh-Hant but the readout panel uses English (or vice versa) |
| Test level | Integration |
| Test class | `LuopanFragmentTest` |
| REQ / AC | REQ §5.4, REQ-L10N-02 |

---

### PG-08: Performance (E2E)

Properties verified under real hardware conditions with hardware-accelerated Canvas.

**Test class:** Performance instrumented tests (FrameMetrics, battery historian)

---

#### PROP-08-065

| Item | Detail |
|------|--------|
| Property | `onDraw` completes in ≤ 16 ms per frame at a 20 Hz update rate on API-26 mid-range hardware, measured via `FrameMetrics` with hardware acceleration (`LAYER_TYPE_HARDWARE`) enabled |
| Test level | E2E |
| Test class | Instrumented FrameMetrics test |
| REQ / AC | REQ-NFR-LUOPAN-02, TSPEC §10.1 |

---

#### PROP-08-066

| Item | Detail |
|------|--------|
| Property | Dial rotation is sustained at ≥ 20 fps during continuous device rotation on API-26 mid-range hardware |
| Test level | E2E |
| Test class | Instrumented FrameMetrics test |
| REQ / AC | REQ-NFR-LUOPAN-01, TSPEC §10 |

---

#### PROP-08-067

| Item | Detail |
|------|--------|
| Property | First dial render (from mode-switch tap to first complete painted frame) completes in ≤ 300 ms |
| Test level | E2E |
| Test class | `ModeSwitcherTest` (Instrumented) |
| REQ / AC | REQ-NFR-LUOPAN-04, AC-01 |

---

#### PROP-08-068

| Item | Detail |
|------|--------|
| Property | Battery consumption in Luopan Mode is ≤ 7%/hr, measured via Android battery historian on API-26 mid-range hardware during 1-hour continuous luopan display |
| Test level | E2E |
| Test class | Battery historian instrumented test |
| REQ / AC | REQ-NFR-LUOPAN-03 |

---

### PG-09: 坐向 Lock UI (E2E)

End-to-end properties of the "Lock 向" / "Clear 向" button and overlay, verified via the instrumented UI.

**Test classes:** `LuopanFragmentTest` (Instrumented)

---

#### PROP-09-069

| Item | Detail |
|------|--------|
| Property | "Lock 向" button is **disabled** when `confidence ∈ {POOR, SENSOR_ERROR, STABILIZING}` and no lock is currently active |
| Test level | E2E |
| Test class | `LuopanFragmentTest` |
| REQ / AC | AC-09, AC-29, BR-05, FSPEC §4e |

---

#### PROP-09-070

| Item | Detail |
|------|--------|
| Property | "Lock 向" button is **enabled** when `confidence ∈ {MODERATE, HIGH}` and no lock is currently active |
| Test level | E2E |
| Test class | `LuopanFragmentTest` |
| REQ / AC | AC-07, AC-08, BR-05, REQ §8 |

---

#### PROP-09-071

| Item | Detail |
|------|--------|
| Property | Button label shows **"Lock 向"** when lock is not active; shows **"Clear 向"** when lock is active (regardless of current confidence) |
| Test level | E2E |
| Test class | `LuopanFragmentTest` |
| REQ / AC | FSPEC §4a step 8, FSPEC §4b step 3 |

---

#### PROP-09-072

| Item | Detail |
|------|--------|
| Property | "Clear 向" button is **enabled** when a lock is active, even if current `confidence ∈ {POOR, SENSOR_ERROR, STABILIZING}` — the user must always be able to clear a lock |
| Negative | The "Clear 向" button must NEVER be disabled due to confidence state alone (PM-Q01 fix: `canLock || state.isLockActive`) |
| Test level | E2E |
| Test class | `LuopanFragmentTest` |
| REQ / AC | TSPEC §6.2 (PM-Q01 fix), FSPEC §4b |

---

#### PROP-09-073

| Item | Detail |
|------|--------|
| Property | 坐向 overlay appears **immediately** (same render frame as the `LuopanState` update) when "Lock 向" is tapped; overlay is dismissed **immediately** when "Clear 向" is tapped |
| Test level | E2E |
| Test class | `LuopanFragmentTest` |
| REQ / AC | FSPEC §4a step 7, FSPEC §4b step 2 |

---

#### PROP-09-074

| Item | Detail |
|------|--------|
| Property | Gold tick mark appears at the correct Ring 5 position on lock: after `lock(45.0f)`, the tick mark is drawn at 45° within the rotation-transformed canvas (tracking the physical bearing as the device rotates) |
| Test level | E2E |
| Test class | `LuopanFragmentTest` |
| REQ / AC | AC-07, FSPEC §4a step 7b |

---

#### PROP-09-075

| Item | Detail |
|------|--------|
| Property | Gold tick mark disappears on clear: after "Clear 向" is tapped, no gold tick mark is drawn on the dial canvas |
| Test level | E2E |
| Test class | `LuopanFragmentTest` |
| REQ / AC | FSPEC §4b step 2 |

---

#### PROP-09-076

| Item | Detail |
|------|--------|
| Property | When `SENSOR_ERROR` occurs while a lock is active: (a) the 坐向 overlay remains visible with last-valid frozen bearing values, (b) the readout panel shows `"—"` for all computed fields, (c) the confidence badge shows "Sensor error" in red, (d) the button label shows "Clear 向" and is enabled (allowing the user to clear the frozen lock) |
| Test level | E2E |
| Test class | `LuopanFragmentTest` |
| REQ / AC | AC-28, ES-01 SENSOR_ERROR table, FSPEC §5 ES-01 |

---

## Gap Analysis

The following requirements and acceptance criteria are fully covered by the properties above:

| AC / REQ | Covered by |
|----------|------------|
| AC-01 (mode entry latency) | PROP-06-053, PROP-08-067 |
| AC-02a / AC-02b (dial rotation math) | PROP-04-037, PROP-04-038 |
| AC-03 (canonical readout at 180°) | PROP-03-033 |
| AC-04 / AC-05 (readout at 90°) | PROP-03-034 |
| AC-06 (分金 N/A at MODERATE) | PROP-03-029 |
| AC-07 / AC-08 (坐向 lock at HIGH / MODERATE) | PROP-02-023, PROP-02-024, PROP-09-070 |
| AC-09 (lock disabled at POOR) | PROP-09-069 |
| AC-10 / AC-11 (坐 wrap-around) | PROP-02-016 |
| AC-12 (default zh-Hant) | PROP-07-058, PROP-07-059 |
| AC-13 (romanization toggle) | PROP-03-036, PROP-07-060 |
| AC-14 / AC-15 (ring visibility) | PROP-04-041, PROP-05-048 |
| AC-16 (子/亥 wrap-around) | PROP-01-003 |
| AC-17 (generic boundary) | PROP-01-002 |
| AC-18 (Ring 5 sub-15° boundaries) | PROP-01-008 |
| AC-19 (Ring 3 45° boundaries) | PROP-01-007 |
| AC-20 (壬子分金 wrap-around) | PROP-01-004 |
| AC-21 (lock preserved across mode switch) | PROP-06-056 |
| AC-22 (north switch updates readout) | PROP-03-031 |
| AC-23 (locked 山 labels invariant to north switch) | PROP-02-018, PROP-02-019, PROP-03-032 |
| AC-24 (SENSOR_ERROR fields hidden) | PROP-03-026 |
| AC-25 (zoom clamping) | PROP-04-042 |
| AC-26 (zoom survives config change) | PROP-05-050 |
| AC-27 (zoom resets on cold start) | PROP-05-049 |
| AC-28 (SENSOR_ERROR while locked) | PROP-09-076 |
| AC-29 (STABILIZING lock disabled) | PROP-03-028, PROP-09-069 |
| BR-01 (universal boundary rule) | PROP-01-001, PROP-01-002 |
| BR-02 (子 wrap-around Ring 4) | PROP-01-003 |
| BR-03 (analogous wrap-arounds) | PROP-01-007, PROP-01-008 |
| BR-04 (壬子分金 wrap-around) | PROP-01-004 |
| BR-05 (confidence → lock state) | PROP-03-026–030, PROP-09-069, PROP-09-070 |
| BR-06 (坐向 derivation) | PROP-02-016, PROP-02-023, PROP-02-024 |
| BR-07 (分金 display rule) | PROP-03-029, PROP-03-030 |
| BR-08 (default zh-Hant) | PROP-03-035, PROP-07-058, PROP-07-059 |
| BR-09 (session vs persisted) | PROP-05-045–052 |
| BR-10 (lock across mode switch) | PROP-02-020, PROP-02-021, PROP-06-056 |
| BR-11 (dial rotation math) | PROP-04-037, PROP-04-038 |
| REQ-NFR-LUOPAN-01–04 | PROP-08-065–068 |
| TE-F01 (Ring 2 LUT corrected) | PROP-01-005, PROP-01-006, PROP-01-014 |
| TE-F04 (thread-safety) | PROP-02-025 |
| PM-Q01 (Clear 向 always enabled) | PROP-09-072 |

**No uncovered gaps identified.** All acceptance criteria, business rules, and cross-review findings (TE-F01, TE-F04, PM-Q01) have at least one corresponding property.

---

*End of PROPERTIES-luopan-p3-luopan-mode*
