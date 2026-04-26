# REQ-luopan-p3-luopan-mode
## Phase 3: Luopan Display Mode — Core Differentiator

| Field | Value |
|-------|-------|
| **Version** | 0.2-draft |
| **Date** | 2026-04-25 |
| **Status** | Draft |
| **Phase** | 3 of 5 |
| **Parent REQ** | [REQ-luopan-compass v0.3-draft](../REQ-luopan-compass.md) |
| **Prerequisite** | Phase 2 complete and stable |
| **Delivery plan** | [DELIVERY-PLAN.md](../DELIVERY-PLAN.md) |
| **Revised** | 2026-04-25 — addressed SE/TE cross-review iteration 1 |

---

## 1. Goal

Ship the luopan display mode — the core product differentiator that serves feng shui practitioners. A rotating dial with six concentric rings (天池, 先天八卦, 後天八卦 with 方位 labels, 十二地支, 二十四山, 六十分金), a fixed pointer, 坐向 lock, and Traditional Chinese terminology. The numerical readout panel shows the current 山, 時辰 sector, 方位, and (when High confidence) 分金 division alongside the bearing in degrees.

After Phase 3, Persona 1 (Master Li, feng shui practitioner) is fully served.

---

## 2. Usable Application State After Phase 3

| Capability | Detail |
|-----------|--------|
| Luopan Mode | Accessible via mode switcher; dial rotates as device rotates; pointer fixed |
| Six concentric rings | 天池 needle, 先天八卦 (45°), 後天八卦 + 方位 labels (45°), 十二地支 (30°), 二十四山 (15°), 六十分金 (6°) |
| 後天八卦 方位 | Each sector shows trigram symbol + 卦名 + direction name (e.g., "☲ 離 南"); see REQ-LUOPAN-01 |
| 十二地支 ring | 12 Earthly Branch characters at 30° intervals; wraps correctly at 子/亥 boundary; see REQ-LUOPAN-02 |
| Numerical readout | Canonical field order: 山 (char + pinyin), 十二地支 sector (char + pinyin), 後天八卦 方位 (trigram + direction), bearing in degrees + north reference, 分金 (High confidence only), confidence badge |
| 坐向 lock | "Lock 向" button; enabled at **High or Moderate** confidence; locks 向 bearing; derives 坐 bearing = (向 + 180°) mod 360°; both labeled with 山 names; gold tick mark on dial; disabled at Poor confidence |
| Luopan terminology | Ring labels in Traditional Chinese by default regardless of system locale; pinyin romanization toggle; "Show in my language" toggle for English ring equivalents |
| Modern Mode | Unchanged and still accessible |

---

## 3. Personas Served After Phase 3

| Persona | Status | New capability |
|---------|--------|---------------|
| Persona 1 — Master Li (feng shui) | **Fully served** | Luopan mode, 坐向 lock, Traditional Chinese terminology |
| Persona 2, 3, 4 | **Unchanged** — Phase 2 and Phase 1 capabilities maintained |

---

## 4. User Stories Active in This Phase

| ID | Title |
|----|-------|
| US-02 | Orienting a building's 坐向 *(newly active)* |
| US-07 | Reading 24 Mountains and 分金 *(newly active)* |
| US-12 | Reading 後天八卦 方位 and 十二地支 direction *(newly active)* |
| Prior stories | US-03, 04, 05, 08, 09, 10, 11 — continued from prior phases |

---

## 5. Requirements in Scope

All full specifications in [master REQ §6.5, §6.8, §6.9, §10](../REQ-luopan-compass.md).

### 5.1 Luopan Display

| ID | Title | Priority | Key Spec |
|----|-------|----------|----------|
| REQ-DISPLAY-04 | Luopan Mode overview | P0 | Rotationally symmetric graphic; **needle/pointer fixed, dial rotates**; matches physical luopan operation. See [§10](../REQ-luopan-compass.md#10-luopan-mode-specification) for full ring and visual spec. |
| REQ-DISPLAY-05 | Luopan numerical readout | P0 | Alongside dial — canonical field order (see §5.5): 山 (char + pinyin), 十二地支 sector (char + pinyin), 後天八卦 方位 (trigram + direction), bearing in degrees + north reference, 分金 (High confidence only), confidence badge |
| REQ-DISPLAY-06 | 坐向 lock | **P0 (elevated)** | Lock 向 bearing; compute 坐 = (向 + 180°) mod 360°; both labeled with 山; gold tick mark on dial; **enabled at High and Moderate confidence; disabled at Poor confidence only** |

### 5.2 Luopan Ring Content

| ID | Title | Priority | Key Spec |
|----|-------|----------|----------|
| REQ-LUOPAN-01 | 後天八卦 ring — trigram + 方位 labels | P0 | Each of 8 × 45° sectors: trigram symbol (☲ etc.) + 卦名 + Chinese direction name (南/北/東/西/東南/西北/東北/西南). King Wen arrangement. See [master REQ §6.9 REQ-LUOPAN-01](../REQ-luopan-compass.md#req-luopan-01-後天八卦-ring--trigram-and-direction-labels) and [§10.2a](../REQ-luopan-compass.md#102a-ring-label-reference--後天八卦-ring-3) for full table. |
| REQ-LUOPAN-02 | 十二地支 ring — twelve directional sectors | P0 | Ring 4; 12 × 30° sectors; characters 子丑寅卯辰巳午未申酉戌亥; wrap-around at 子 sector (345°–15°). See [master REQ §6.9 REQ-LUOPAN-02](../REQ-luopan-compass.md#req-luopan-02-十二地支-ring--twelve-earthly-branch-directions) and [§10.2b](../REQ-luopan-compass.md#102b-ring-label-reference--十二地支-ring-4) for full table. |

### 5.3 Luopan Ring Reference — All Six Rings (v1)

Engineering MUST implement exactly these six rings in this radial order:

| Ring # | Chinese | Type | Divisions | °/Division |
|--------|---------|------|-----------|-----------|
| 1 | 天池 | Needle (center) | — | — |
| 2 | 先天八卦 | Fuxi arrangement | 8 | 45° |
| 3 | 後天八卦 | King Wen + 方位 | 8 | 45° |
| 4 | 十二地支 | Earthly Branches | 12 | 30° |
| 5 | 二十四山 | 24 Mountains | 24 | 15° |
| 6 | 六十分金 | 60 Gold Divisions | 60 | 6° |

Full reference tables in [master REQ §10.2a, §10.2b, §10.2c](../REQ-luopan-compass.md#102a-ring-label-reference--後天八卦-ring-3).

### 5.4 Localization for Luopan Mode

| ID | Title | Priority | Key Spec |
|----|-------|----------|----------|
| REQ-L10N-02 | Luopan terminology | P0 | Ring labels in zh-Hant by default regardless of system locale; "Show romanization" toggle shows pinyin below each character; "Show in my language" toggle maps to English equivalents per §5.8 |

### 5.5 Canonical Numerical Readout Field Order

The numerical readout panel MUST display fields in the following canonical order. All scenarios in this document use this order.

```
[山 character] ([山 pinyin]) · [地支 character] ([地支 pinyin]) · [trigram] [卦名] [方位] · [bearing]° · [north type] · [分金 label or "N/A"] · [confidence badge]
```

**Example at 180°, High confidence, True North:**
> 午 (Wǔ) · 午 (Wǔ) · ☲ 離 南 · 180.0° · True N · 丙子分金 · High

**Example at 180°, Moderate confidence:**
> 午 (Wǔ) · 午 (Wǔ) · ☲ 離 南 · 180.0° · True N · N/A — calibrate for 分金 precision · Moderate

**Note:** At 180°, the 山 (二十四山 Ring 5) and 地支 (十二地支 Ring 4) both read "午". At other bearings they will differ — the 山 panel shows the 15° ring result and the 地支 panel shows the 30° ring result.

### 5.6 六十分金 Ring Labels (Ring 6)

> **NOTE — FENG SHUI VALIDATION REQUIRED:** The 六十分金 label table below uses the common 三元/通用 convention derived from the 二十四山 assignments. These labels MUST be validated by a practicing feng shui consultant before release (see OQ-P3-01, §11). Engineering may implement this table for development purposes but the labels are not considered final until OQ-P3-01 is resolved.

The 六十分金 ring assigns four 6° sub-divisions to each of the 24 Mountains. Only 60 of the 64 possible combinations are used (the four divisions of the four 空亡 Mountains are excluded). The table below lists all 60 active divisions in bearing order:

| # | 分金 Label | Center Bearing | Range | Parent 山 |
|---|-----------|---------------|-------|----------|
| 1 | 庚子分金 | 355° | 352°–358° | 壬 |
| 2 | 壬子分金 | 1° | 358°–4° | 子 |
| 3 | 甲子分金 | 7° | 4°–10° | 子 |
| 4 | 丙子分金 | 13° | 10°–16° | 癸 |
| 5 | 戊子分金 | 19° | 16°–22° | 癸 |
| 6 | 庚丑分金 | 25° | 22°–28° | 丑 |
| 7 | 壬丑分金 | 31° | 28°–34° | 丑 |
| 8 | 甲丑分金 | 37° | 34°–40° | 艮 |
| 9 | 丙丑分金 | 43° | 40°–46° | 艮 |
| 10 | 戊丑分金 | 49° | 46°–52° | 寅 |
| 11 | 庚寅分金 | 55° | 52°–58° | 寅 |
| 12 | 壬寅分金 | 61° | 58°–64° | 甲 |
| 13 | 甲寅分金 | 67° | 64°–70° | 甲 |
| 14 | 丙寅分金 | 73° | 70°–76° | 卯 |
| 15 | 戊寅分金 | 79° | 76°–82° | 卯 |
| 16 | 庚卯分金 | 85° | 82°–88° | 乙 |
| 17 | 壬卯分金 | 91° | 88°–94° | 乙 |
| 18 | 甲卯分金 | 97° | 94°–100° | 辰 |
| 19 | 丙卯分金 | 103° | 100°–106° | 辰 |
| 20 | 戊卯分金 | 109° | 106°–112° | 巽 |
| 21 | 庚辰分金 | 115° | 112°–118° | 巽 |
| 22 | 壬辰分金 | 121° | 118°–124° | 巳 |
| 23 | 甲辰分金 | 127° | 124°–130° | 巳 |
| 24 | 丙辰分金 | 133° | 130°–136° | 丙 |
| 25 | 戊辰分金 | 139° | 136°–142° | 丙 |
| 26 | 庚巳分金 | 145° | 142°–148° | 午 |
| 27 | 壬巳分金 | 151° | 148°–154° | 午 |
| 28 | 甲巳分金 | 157° | 154°–160° | 丁 |
| 29 | 丙巳分金 | 163° | 160°–166° | 丁 |
| 30 | 戊巳分金 | 169° | 166°–172° | 未 |
| 31 | 庚午分金 | 175° | 172°–178° | 未 |
| 32 | 壬午分金 | 181° | 178°–184° | 坤 |
| 33 | 甲午分金 | 187° | 184°–190° | 坤 |
| 34 | 丙午分金 | 193° | 190°–196° | 申 |
| 35 | 戊午分金 | 199° | 196°–202° | 申 |
| 36 | 庚未分金 | 205° | 202°–208° | 庚 |
| 37 | 壬未分金 | 211° | 208°–214° | 庚 |
| 38 | 甲未分金 | 217° | 214°–220° | 酉 |
| 39 | 丙未分金 | 223° | 220°–226° | 酉 |
| 40 | 戊未分金 | 229° | 226°–232° | 辛 |
| 41 | 庚申分金 | 235° | 232°–238° | 辛 |
| 42 | 壬申分金 | 241° | 238°–244° | 戌 |
| 43 | 甲申分金 | 247° | 244°–250° | 戌 |
| 44 | 丙申分金 | 253° | 250°–256° | 乾 |
| 45 | 戊申分金 | 259° | 256°–262° | 乾 |
| 46 | 庚酉分金 | 265° | 262°–268° | 亥 |
| 47 | 壬酉分金 | 271° | 268°–274° | 亥 |
| 48 | 甲酉分金 | 277° | 274°–280° | 壬 |
| 49 | 丙酉分金 | 283° | 280°–286° | 壬 |
| 50 | 戊酉分金 | 289° | 286°–292° | 子 |
| 51 | 庚戌分金 | 295° | 292°–298° | 子 |
| 52 | 壬戌分金 | 301° | 298°–304° | 癸 |
| 53 | 甲戌分金 | 307° | 304°–310° | 丑 |
| 54 | 丙戌分金 | 313° | 310°–316° | 丑 |
| 55 | 戊戌分金 | 319° | 316°–322° | 艮 |
| 56 | 庚亥分金 | 325° | 322°–328° | 艮 |
| 57 | 壬亥分金 | 331° | 328°–334° | 寅 |
| 58 | 甲亥分金 | 337° | 334°–340° | 寅 |
| 59 | 丙亥分金 | 343° | 340°–346° | 甲 |
| 60 | 戊亥分金 | 349° | 346°–352° | 甲 |

**Key test bearings for Ring 6 unit tests:**
- 180° → 壬午分金 (sector 178°–184°)
- 0° → 壬子分金 (sector 358°–4°)
- 90° → 壬卯分金 (sector 88°–94°)

### 5.7 Settings Persistence Contract

The following boolean flags MUST be persisted in `SettingsRepository` (implemented as `SharedPreferences` or `DataStore`). Engineering MUST use the exact key names below for cross-component consistency.

| Setting Key | Type | Default | Scope | Description |
|-------------|------|---------|-------|-------------|
| `luopan_show_romanization` | Boolean | `false` | **Persisted across app restarts** | Show pinyin below each ring character |
| `luopan_show_my_language` | Boolean | `false` | **Persisted across app restarts** | Show English equivalents instead of zh-Hant ring labels |
| `luopan_ring_visible_1` | Boolean | `true` | **Session-only** (not persisted) | Visibility of Ring 1 (天池) — see §5.7.1 |
| `luopan_ring_visible_2` | Boolean | `true` | **Session-only** (not persisted) | Visibility of Ring 2 (先天八卦) |
| `luopan_ring_visible_3` | Boolean | `true` | **Session-only** (not persisted) | Visibility of Ring 3 (後天八卦) |
| `luopan_ring_visible_4` | Boolean | `true` | **Session-only** (not persisted) | Visibility of Ring 4 (十二地支) |
| `luopan_ring_visible_5` | Boolean | `true` | **Session-only** (not persisted) | Visibility of Ring 5 (二十四山) |
| `luopan_ring_visible_6` | Boolean | `true` | **Session-only** (not persisted) | Visibility of Ring 6 (六十分金) |
| `luopan_zoom_scale` | Float (0.8–2.0) | `1.0` | **Session-only** (not persisted) | Pinch-to-zoom scale factor — see §5.7.2 |
| `display_mode` | Enum | `MODERN` | **Persisted across app restarts** | Last-used display mode (from Phase 1) |

#### 5.7.1 Ring Visibility Persistence Decision

**PM Decision:** Ring visibility is **session-only**. Ring visibility state is held in `CompassViewModel` memory and is reset to all-visible on every cold start.

**Rationale:** Practitioners typically adjust ring visibility to reduce clutter for a specific consultation session. Persisting the choice would silently hide rings on the next launch — a worse surprise than a reset. If practitioner feedback after beta indicates persistence is preferred, this decision will be revisited in Phase 5.

#### 5.7.2 Pinch-to-Zoom Persistence Decision

**PM Decision:** Zoom scale is **session-only**. It is NOT persisted across app restarts. It MUST survive configuration changes (e.g., screen rotation) via `CompassViewModel` or `onSaveInstanceState`.

**Rationale:** Zoom preference is a moment-to-moment readability aid, not a persistent preference. Persisting it risks the user forgetting they are zoomed in and misinterpreting ring boundaries on the next launch.

### 5.8 "Show in My Language" — English Ring Label Mapping

When the "Show in my language" toggle is enabled, ring labels MUST display English equivalents for the entries defined below. Entries marked "—" have no standard English equivalent; they continue to display in Traditional Chinese characters.

#### Ring 3 — 後天八卦 方位 English Labels

| zh-Hant | English Equivalent |
|---------|--------------------|
| 離 南 | Li · South |
| 巽 東南 | Xun · Southeast |
| 震 東 | Zhen · East |
| 艮 東北 | Gen · Northeast |
| 坎 北 | Kan · North |
| 乾 西北 | Qian · Northwest |
| 兌 西 | Dui · West |
| 坤 西南 | Kun · Southwest |

*(Trigram symbols ☲☴☳☶☵☰☱☷ are retained in all language modes.)*

#### Ring 4 — 十二地支 English Labels

| zh-Hant | Pinyin | English Equivalent |
|---------|--------|--------------------|
| 子 | Zǐ | Rat |
| 丑 | Chǒu | Ox |
| 寅 | Yín | Tiger |
| 卯 | Mǎo | Rabbit |
| 辰 | Chén | Dragon |
| 巳 | Sì | Snake |
| 午 | Wǔ | Horse |
| 未 | Wèi | Goat |
| 申 | Shēn | Monkey |
| 酉 | Yǒu | Rooster |
| 戌 | Xū | Dog |
| 亥 | Hài | Pig |

#### Ring 5 — 二十四山 English Labels

English equivalents for 二十四山 use the element/stem/branch category of each position:

| 山 | Pinyin | English Category | English Label |
|----|--------|-----------------|---------------|
| 壬 | Rén | Heavenly Stem | Ren (Water-Yang) |
| 子 | Zǐ | Earthly Branch | Rat |
| 癸 | Guǐ | Heavenly Stem | Gui (Water-Yin) |
| 丑 | Chǒu | Earthly Branch | Ox |
| 艮 | Gèn | Trigram | Gen (Mountain) |
| 寅 | Yín | Earthly Branch | Tiger |
| 甲 | Jiǎ | Heavenly Stem | Jia (Wood-Yang) |
| 卯 | Mǎo | Earthly Branch | Rabbit |
| 乙 | Yǐ | Heavenly Stem | Yi (Wood-Yin) |
| 辰 | Chén | Earthly Branch | Dragon |
| 巽 | Xùn | Trigram | Xun (Wind) |
| 巳 | Sì | Earthly Branch | Snake |
| 丙 | Bǐng | Heavenly Stem | Bing (Fire-Yang) |
| 午 | Wǔ | Earthly Branch | Horse |
| 丁 | Dīng | Heavenly Stem | Ding (Fire-Yin) |
| 未 | Wèi | Earthly Branch | Goat |
| 坤 | Kūn | Trigram | Kun (Earth) |
| 申 | Shēn | Earthly Branch | Monkey |
| 庚 | Gēng | Heavenly Stem | Geng (Metal-Yang) |
| 酉 | Yǒu | Earthly Branch | Rooster |
| 辛 | Xīn | Heavenly Stem | Xin (Metal-Yin) |
| 戌 | Xū | Earthly Branch | Dog |
| 乾 | Qián | Trigram | Qian (Heaven) |
| 亥 | Hài | Earthly Branch | Pig |

---

## 6. Visual Specification Reference

From [master REQ §10.3](../REQ-luopan-compass.md#103-visual-styling):

| Element | Value |
|---------|-------|
| Background | #2C0E0E (dark lacquer) |
| Ring borders | #C9A84C (matte gold) |
| Ring backgrounds (alternating) | #3D1010 / #1E0A0A |
| Primary character color | #E8C97A |
| Secondary character (small rings) | #B89A5A |
| Needle: north tip / south tip / shaft | #CC2200 / #F5F5F5 / #1A1A1A |
| Fixed pointer | Gold triangle #C9A84C at top center |
| Font | Noto Serif CJK TC (or equivalent); 8sp min (Ring 6), 11sp (Ring 5), 12sp (Ring 4), 14sp (Rings 2–3) |

**Legibility requirement:** On a 5-inch 1080p screen at default brightness, all 12 地支 characters (Ring 4), all 24 山 characters (Ring 5), and all 60 分金 labels (Ring 6) must be readable by a user with 20/40 corrected vision at 30 cm.

---

## 7. Luopan Interaction Behavior

From [master REQ §10.4](../REQ-luopan-compass.md#104-interaction-behavior):

- **Pointer fixed, dial rotates.** The pointer stays at screen top; the entire ring assembly rotates to reflect the heading.
- **No snapping.** Continuous smooth rotation.
- **Pinch-to-zoom.** Scale 0.8×–2.0× for readability on small screens. Scale is session-only (see §5.7.2).
- **Ring visibility toggle.** Long-press on the dial opens a ring visibility menu (see §7.1). Hiding a ring does not affect heading computation. Visibility is session-only (see §5.7.1).

### 7.1 Ring Visibility Menu — UX Specification

- **Trigger:** Long-press anywhere on the dial canvas for ≥500 ms.
- **Widget type:** `BottomSheetDialog` anchored to the bottom of the screen.
- **Menu items:** One toggle row per ring, labeled with ring number + Chinese name + English name in parentheses:
  - Ring 1 — 天池 (Heaven's Pool)
  - Ring 2 — 先天八卦 (Earlier Heaven Bagua)
  - Ring 3 — 後天八卦 (Later Heaven Bagua)
  - Ring 4 — 十二地支 (Twelve Earthly Branches)
  - Ring 5 — 二十四山 (Twenty-Four Mountains)
  - Ring 6 — 六十分金 (Sixty Gold Divisions)
- **Toggle behavior:** Each row contains a `Switch` control. Toggling hides or shows the corresponding ring immediately; the dial updates in real time while the sheet is open.
- **Dismiss:** User taps outside the sheet, swipes down, or taps a "Done" button at the bottom of the sheet.
- **Accessibility:** The sheet is reachable via a three-dot overflow menu button on the Luopan Mode action bar, providing an accessibility alternative to long-press for TalkBack users.
- **Persistence:** Session-only — state resets to all-visible on next cold start (see §5.7.1).

---

## 8. 坐向 Lock Workflow

From [master REQ §10.5](../REQ-luopan-compass.md#105-坐向-setting-and-display):

1. User rotates device to desired **向** direction
2. Taps "Lock 向" (enabled at High or Moderate confidence; disabled only at Poor)
3. App locks: **向** bearing and its 山 label
4. App derives: **坐** = (向 + 180°) mod 360°, and its 山 label
5. Overlay shows: "向: 午 (180.0° True N)" and "坐: 子 (0.0° True N)"
6. A gold tick mark appears on the dial at the locked 向 position; live needle continues rotating
7. "Clear 向" removes the lock

**Confidence rule (single testable statement):**
> The "Lock 向" button is **enabled** when confidence is **High** or **Moderate**. The button is **disabled** when confidence is **Poor**. This resolves the apparent conflict between Scenario E (which tests only the High case) and master REQ §6.5 REQ-DISPLAY-06 (which permits Moderate). Scenario E in §10 now includes an explicit Moderate case.

**Lock state across mode switches:** When the user switches from Luopan Mode to Modern Mode while 坐向 is locked, the lock is **preserved**. If the user returns to Luopan Mode within the same session, the locked overlay is restored. The lock is cleared only when the user explicitly taps "Clear 向" or the app is cold-started.

---

## 9. Mode-Switcher Architecture

### 9.1 Navigation Contract

The app uses a **single-Activity architecture** with a `NavHostFragment` managed by Navigation Component (Jetpack). The mode switcher is a `TabLayout` (Material Design) docked at the bottom of `CompassActivity`, wired to `NavHostFragment` via `NavigationUI.setupWithNavController`.

Each mode corresponds to a `Fragment` in the navigation graph:

| Mode | Fragment class | Nav destination ID |
|------|---------------|-------------------|
| Modern Mode | `ModernCompassFragment` | `dest_modern` |
| Luopan Mode | `LuopanFragment` | `dest_luopan` |
| Sighting Mode | `SightingFragment` (Phase 5) | `dest_sighting` |

### 9.2 ViewModel Sharing

`CompassViewModel` is scoped to `CompassActivity`. Both `ModernCompassFragment` and `LuopanFragment` obtain the same `CompassViewModel` instance via `activityViewModels()`. The sensor pipeline runs continuously in the ViewModel; neither fragment owns sensors. This ensures heading data continues uninterrupted during mode transitions.

### 9.3 Transition Behavior

- Mode transition MUST complete within 300 ms (per REQ-DISPLAY-01).
- While `LuopanFragment` is inflating and its first `onDraw` is pending, the fragment shows a dark background at the Luopan theme color (#2C0E0E). No spinner or loading state is required given the 300 ms budget.
- The last-used mode is persisted in `SettingsRepository` under key `display_mode` (from Phase 1) and restored on cold start.

---

## 10. UI State Contract — `CompassUiState` Luopan Fields

The existing `CompassUiState` data class MUST be extended with the following fields for Phase 3. All sector-lookup fields are derived from `bearingDeg` by the ViewModel using the static look-up tables from §5.2–5.6.

```kotlin
data class LuopanState(
    // Ring 5 — 二十四山
    val mountainChar: String,          // e.g., "午"
    val mountainPinyin: String,        // e.g., "Wǔ"

    // Ring 4 — 十二地支
    val earthlyBranchChar: String,     // e.g., "午"
    val earthlyBranchPinyin: String,   // e.g., "Wǔ"

    // Ring 3 — 後天八卦
    val trigramSymbol: String,         // e.g., "☲"
    val trigramName: String,           // e.g., "離"
    val trigramDirection: String,      // e.g., "南"

    // Ring 6 — 六十分金 (null when confidence != HIGH)
    val fenJinLabel: String?,          // e.g., "壬午分金" or null

    // 坐向 lock state
    val xiangBearing: Double?,         // locked 向 bearing in degrees (null = unlocked)
    val xiangMountain: String?,        // 山 label for 向 (null = unlocked)
    val zuoBearing: Double?,           // derived 坐 bearing = (向 + 180) mod 360 (null = unlocked)
    val zuoMountain: String?,          // 山 label for 坐 (null = unlocked)
    val isLockActive: Boolean,         // true when 坐向 is locked

    // Confidence (mirrors CompassUiState.confidence — included here for LuopanFragment independence)
    val confidence: Confidence,        // HIGH | MODERATE | POOR
)
```

`CompassUiState` gains a `val luopan: LuopanState` field. When `LuopanFragment` is not active, the ViewModel may still compute `luopan` (computation is cheap) or compute it lazily — engineering decision.

**Sector-lookup responsibility:** The mapping from `bearingDeg` to ring characters MUST live in `CompassViewModel` (or a domain use-case called by the ViewModel), not in the View. This keeps the View as a pure renderer of the state.

---

## 11. Non-Functional Requirements — Luopan Mode Specific

In addition to the global NFRs in master REQ §7, the following NFRs apply specifically to Luopan Mode:

| ID | Requirement | Target | Rationale |
|----|-------------|--------|-----------|
| REQ-NFR-LUOPAN-01 | Dial rotation frame rate | ≥ 20 fps sustained during continuous rotation on API-26 mid-range hardware | Matches master REQ-NFR-02; the luopan dial must not be visually jerky |
| REQ-NFR-LUOPAN-02 | `onDraw` time budget | ≤ 16 ms per frame on API-26 mid-range hardware (measured via `FrameMetrics`) | Six rings × 60+ labels with CJK font; ring geometry MUST be pre-computed into `Path` objects in `onSizeChanged`, not recomputed each frame |
| REQ-NFR-LUOPAN-03 | Luopan Mode battery | ≤ 7% battery per hour (vs. 5% for Modern Mode) | Additional Canvas draw work for six rings; measured via Android battery historian |
| REQ-NFR-LUOPAN-04 | First render latency | ≤ 300 ms from mode-switch tap to first complete dial frame | Matches mode-transition budget in REQ-DISPLAY-01 |

**Hardware acceleration:** `LuopanView` MUST use hardware-accelerated Canvas (`View.setLayerType(LAYER_TYPE_HARDWARE, null)` or the default accelerated window). The engineering team may choose between `View`/Canvas or `SurfaceView` based on measured frame times, but MUST meet REQ-NFR-LUOPAN-02 on the minimum target device.

---

## 12. Phase 3 End-to-End Acceptance Test

**Scenario A — Luopan Mode accessible**

*Given* Phase 2 is complete,
*When* user taps the mode switcher to select Luopan Mode,
*Then*:
- Luopan dial is visible; rotates smoothly as device rotates
- Pointer is fixed at screen top; dial rotates counter-clockwise when device rotates clockwise (1:1 mapping ±2°)
- All six rings are visible and their labels legible on a 5-inch 1080p screen

**Scenario B — 後天八卦 方位 reading (canonical readout format)**

*Given* Luopan Mode active, device pointing at 180°, confidence is High, True North active,
*When* the user looks at the 後天八卦 ring (Ring 3),
*Then*:
- "☲ 離 南" is displayed centered under the pointer
- Numerical panel shows (in canonical order): "午 (Wǔ) · 午 (Wǔ) · ☲ 離 南 · 180.0° · True N · 壬午分金 · High"

**Scenario C — 十二地支 sector reading**

*Given* device pointing at 90°,
*When* Luopan Mode is displayed,
*Then*:
- Ring 4 shows "卯" under the pointer
- Ring 5 shows "卯" under the pointer
- Ring 3 shows "☳ 震 東" under the pointer
- Numerical panel shows (canonical order): "卯 (Mǎo) · 卯 (Mǎo) · ☳ 震 東 · 90.0° · True N · [分金 per confidence] · [confidence]"

**Scenario D — 分金 visibility**

*Given* confidence is "High" (all conditions Good per master REQ §8.1),
*When* Luopan Mode is active,
*Then*: 分金 division is shown in numerical panel (e.g., "壬午分金" at 180°)

*Given* confidence is "Moderate",
*When* Luopan Mode is active,
*Then*: 分金 panel shows "N/A — calibrate for 分金 precision"

**Scenario E — 坐向 lock**

*Case E-1: High confidence lock at 45°*
*Given* confidence is "High", device pointing at 45° (艮),
*When* user taps "Lock 向",
*Then*:
- 向 locked at 45° (艮); 坐 derived as 225° (坤) — i.e., (45 + 180) mod 360 = 225
- Overlay shows: "向: 艮 (45.0° True N)" and "坐: 坤 (225.0° True N)"
- Gold tick mark visible at 45° on dial ring
- Live dial continues to rotate; pointer shows current heading

*Case E-2: Moderate confidence lock (newly required)*
*Given* confidence is "Moderate", device pointing at 90° (卯),
*When* user taps "Lock 向",
*Then*:
- Button is **enabled** (Moderate confidence permits locking)
- 向 locked at 90° (卯); 坐 derived as 270° (酉) — i.e., (90 + 180) mod 360 = 270
- Overlay shows: "向: 卯 (90.0° True N)" and "坐: 酉 (270.0° True N)"
- 分金 field in overlay is hidden (confidence is not High)

*Case E-3: Poor confidence — lock disabled*
*Given* confidence is "Poor",
*When* user attempts to tap "Lock 向",
*Then*: Button is disabled; tooltip shows "Cannot lock — heading is unreliable"

*Case E-4: 坐 wrap-around — 向 near 270°*
*Given* confidence is "High", device pointing at 270°,
*When* user taps "Lock 向",
*Then*:
- 坐 = (270 + 180) mod 360 = 90°
- Overlay shows: "向: 酉 (270.0° True N)" and "坐: 卯 (90.0° True N)"

*Case E-5: 坐 wrap-around — 向 near 350°*
*Given* confidence is "High", device pointing at 350°,
*When* user taps "Lock 向",
*Then*:
- 坐 = (350 + 180) mod 360 = 170°
- Overlay shows: "向: 壬 (350.0° True N)" and "坐: 丙 (170.0° True N)"

**Scenario F — Localization**

*Given* system locale is `en`,
*When* Luopan Mode is opened with default settings,
*Then*: All ring labels display in Traditional Chinese characters (不是英文)

*Given* system locale is `ja`,
*When* Luopan Mode is opened with default settings,
*Then*: All ring labels display in Traditional Chinese characters (not Japanese)

*Given* user enables "Show romanization",
*When* Luopan Mode is displayed,
*Then*: Pinyin appears below each character (e.g., "子 Zǐ", "南 Nán")

**Scenario G — Ring visibility toggle**

*Given* user long-presses the luopan dial (≥500 ms),
*When* the `BottomSheetDialog` ring visibility menu appears and user hides Ring 4 (十二地支),
*Then*: Ring 4 disappears; other rings remain; heading computation is unaffected

*Given* Ring 4 is hidden and user cold-starts the app,
*When* Luopan Mode is opened,
*Then*: Ring 4 is visible again (session-only — not persisted across restarts)

**Scenario H — Sector boundary test cases**

The following boundary cases MUST pass. These test the sector-lookup LUT logic for Rings 3, 4, and 5.

*Ring 4 (十二地支) — 子/亥 wrap-around:*

| Bearing | Expected Ring 4 Label |
|---------|-----------------------|
| 344.9° | 亥 (sector 315°–345°) |
| 345.0° | 子 (sector 345°–15°, start boundary) |
| 15.0° | 子 (sector 345°–15°, end boundary — inclusive) |
| 15.1° | 丑 (sector 15°–45°) |

*Ring 4 — generic boundary:*

| Bearing | Expected Ring 4 Label |
|---------|-----------------------|
| 29.9° | 丑 |
| 30.0° | 丑 (center of 丑 sector) |
| 44.9° | 丑 |
| 45.0° | 寅 |

*Ring 5 (二十四山) — sub-15° boundaries:*

| Bearing | Expected Ring 5 Label |
|---------|-----------------------|
| 7.4° | 子 (sector 352.5°–7.5°) |
| 7.5° | 癸 (sector 7.5°–22.5°, start) |
| 22.4° | 癸 |
| 22.5° | 丑 (sector 22.5°–37.5°) |

*Ring 3 (後天八卦) — 45° boundaries:*

| Bearing | Expected Ring 3 Label |
|---------|-----------------------|
| 22.4° | ☵ 坎 北 (sector 337.5°–22.5°) |
| 22.5° | ☶ 艮 東北 (sector 22.5°–67.5°, start) |
| 67.4° | ☶ 艮 東北 |
| 67.5° | ☳ 震 東 (sector 67.5°–112.5°) |

**Scenario I — Mode switch while 坐向 locked**

*Given* 坐向 is locked at 45° in Luopan Mode,
*When* user taps the mode switcher to switch to Modern Mode,
*Then*: The lock state is preserved in memory; no change visible in Modern Mode

*Given* the user then switches back to Luopan Mode in the same session,
*When* LuopanFragment resumes,
*Then*: The 坐向 overlay is restored showing "向: 艮 (45.0° True N)" and "坐: 坤 (225.0° True N)"

---

## 13. Requirements Deferred from Phase 3

| ID | Title | Deferred to |
|----|-------|-------------|
| REQ-DISPLAY-07, 08 | Sighting mode | Phase 5 |
| REQ-DISPLAY-09 | Heading smoothing control | Phase 5 |
| REQ-DISPLAY-12 | Dark/light theme | Phase 5 |
| 六十龍 / 七十二龍 rings | Beyond hardware accuracy | v2 product roadmap |
| 天盤/人盤/地盤 triple-plate | Out of scope v1 | v2 product roadmap |

---

## 14. Open Questions and Risks Specific to Phase 3

**Risk P3-R1 — Legibility on small screens:** A 5-inch screen with 6 concentric rings may make Ring 6 (六十分金) labels too small to read without zoom. The pinch-to-zoom mitigation (REQ-DISPLAY-04 interaction spec) must be implemented before release.

**Risk P3-R2 — 先天八卦 vs. 後天八卦 ring alignment:** The two 八卦 rings have different trigram-to-direction mappings (see [§10.2a](../REQ-luopan-compass.md#102a-ring-label-reference--後天八卦-ring-3) for contrast table). Engineering MUST implement both as static look-up tables keyed by sector index, not by computed heading, to avoid floating-point boundary errors placing a trigram in the wrong sector.

**Risk P3-R3 — Feng shui practitioner validation:** The luopan ring arrangement must be validated by at least one practicing feng shui consultant before public release. In particular, confirm: (a) the direction-to-trigram mappings match the 三元 / 三合 school conventions used by target users, and (b) the 分金 ring labels are correct for v1.

**OQ-P3-01 — School of feng shui:** The 二十四山 and 分金 ring assignments differ between schools (三元, 三合, 玄空). The current spec uses the most common 三元 / 通用 convention. Confirm target school with Master Li persona before ring labels are finalized. **The 六十分金 table in §5.6 is a placeholder pending this confirmation — it MUST NOT be shipped without practitioner sign-off.**
