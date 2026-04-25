# REQ-luopan-p3-luopan-mode
## Phase 3: Luopan Display Mode — Core Differentiator

| Field | Value |
|-------|-------|
| **Version** | 0.1-draft |
| **Date** | 2026-04-23 |
| **Status** | Draft |
| **Phase** | 3 of 5 |
| **Parent REQ** | [REQ-luopan-compass v0.2-draft](REQ-luopan-compass.md) |
| **Prerequisite** | Phase 2 complete and stable |
| **Delivery plan** | [DELIVERY-PLAN.md](DELIVERY-PLAN.md) |

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
| Numerical readout | Shows: 山 (character + pinyin), 時辰 sector (地支 character + pinyin), 方位 (trigram + direction name), bearing in degrees, confidence badge; 分金 shown only at High confidence |
| 坐向 lock | "Lock 向" button; locks 向 bearing; derives 坐 bearing (向 + 180°); both labeled with 山 names; disabled at Poor confidence |
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

All full specifications in [master REQ §6.5, §6.8, §6.9, §10](REQ-luopan-compass.md#65-compass-display-modes--req-display).

### 5.1 Luopan Display

| ID | Title | Priority | Key Spec |
|----|-------|----------|----------|
| REQ-DISPLAY-04 | Luopan Mode overview | P0 | Rotationally symmetric graphic; **needle/pointer fixed, dial rotates**; matches physical luopan operation. See [§10](REQ-luopan-compass.md#10-luopan-mode-specification) for full ring and visual spec. |
| REQ-DISPLAY-05 | Luopan numerical readout | P0 | Alongside dial: 山 (char + pinyin), 十二地支 sector (char + pinyin), 後天八卦 方位 (trigram + direction), bearing in degrees + north reference, 分金 (High confidence only), confidence badge |
| REQ-DISPLAY-06 | 坐向 lock | **P0 (elevated)** | Lock 向 bearing; compute 坐 = 向 + 180°; both labeled with 山; gold tick mark on dial; disabled at Poor confidence |

### 5.2 Luopan Ring Content

| ID | Title | Priority | Key Spec |
|----|-------|----------|----------|
| REQ-LUOPAN-01 | 後天八卦 ring — trigram + 方位 labels | P0 | Each of 8 × 45° sectors: trigram symbol (☲ etc.) + 卦名 + Chinese direction name (南/北/東/西/東南/西北/東北/西南). King Wen arrangement. See [master REQ §6.9 REQ-LUOPAN-01](REQ-luopan-compass.md#req-luopan-01-後天八卦-ring--trigram-and-direction-labels) and [§10.2a](REQ-luopan-compass.md#102a-ring-label-reference--後天八卦-ring-3) for full table. |
| REQ-LUOPAN-02 | 十二地支 ring — twelve directional sectors | P0 | Ring 4; 12 × 30° sectors; characters 子丑寅卯辰巳午未申酉戌亥; wrap-around at 子 sector (345°–15°). See [master REQ §6.9 REQ-LUOPAN-02](REQ-luopan-compass.md#req-luopan-02-十二地支-ring--twelve-earthly-branch-directions) and [§10.2b](REQ-luopan-compass.md#102b-ring-label-reference--十二地支-ring-4) for full table. |

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

Full reference tables in [master REQ §10.2a, §10.2b, §10.2c](REQ-luopan-compass.md#102a-ring-label-reference--後天八卦-ring-3).

### 5.4 Localization for Luopan Mode

| ID | Title | Priority | Key Spec |
|----|-------|----------|----------|
| REQ-L10N-02 | Luopan terminology | P0 | Ring labels in zh-Hant by default regardless of system locale; "Show romanization" toggle shows pinyin below each character; "Show in my language" toggle maps to English/Japanese equivalents where defined |

---

## 6. Visual Specification Reference

From [master REQ §10.3](REQ-luopan-compass.md#103-visual-styling):

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

From [master REQ §10.4](REQ-luopan-compass.md#104-interaction-behavior):

- **Pointer fixed, dial rotates.** The pointer stays at screen top; the entire ring assembly rotates to reflect the heading.
- **No snapping.** Continuous smooth rotation.
- **Pinch-to-zoom.** Scale 0.8×–2.0× for readability on small screens.
- **Ring visibility toggle.** Long-press on the dial opens a menu to hide/show individual rings. Hiding a ring does not affect heading computation.

---

## 8. 坐向 Lock Workflow

From [master REQ §10.5](REQ-luopan-compass.md#105-坐向-setting-and-display):

1. User rotates device to desired **向** direction
2. Taps "Lock 向" (disabled if confidence = Poor)
3. App locks: **向** bearing and its 山 label
4. App derives: **坐** = 向 + 180°, and its 山 label
5. Overlay shows: "向: 午 (180.0° True N)" and "坐: 子 (0.0° True N)"
6. A gold tick mark appears on the dial at the locked 向 position; live needle continues rotating
7. "Clear 向" removes the lock

---

## 9. Requirements Deferred from Phase 3

| ID | Title | Deferred to |
|----|-------|-------------|
| REQ-DISPLAY-07, 08 | Sighting mode | Phase 5 |
| REQ-DISPLAY-09 | Heading smoothing control | Phase 5 |
| REQ-DISPLAY-12 | Dark/light theme | Phase 5 |
| 六十龍 / 七十二龍 rings | Beyond hardware accuracy | v2 product roadmap |
| 天盤/人盤/地盤 triple-plate | Out of scope v1 | v2 product roadmap |

---

## 10. Phase 3 End-to-End Acceptance Test

**Scenario A — Luopan Mode accessible**

*Given* Phase 2 is complete,  
*When* user taps the mode switcher to select Luopan Mode,  
*Then*:
- Luopan dial is visible; rotates smoothly as device rotates
- Pointer is fixed at screen top; dial rotates counter-clockwise when device rotates clockwise
- All six rings are visible and their labels legible on a 5-inch 1080p screen

**Scenario B — 後天八卦 方位 reading**

*Given* Luopan Mode active, device pointing at 180°,  
*When* the user looks at the 後天八卦 ring (Ring 3),  
*Then*:
- "☲ 離 南" is displayed centered under the pointer
- Numerical panel shows: "☲ 離 · 南 · 午 (Wǔ) · 180.0° · True N · [confidence]"

**Scenario C — 十二地支 sector reading**

*Given* device pointing at 90°,  
*When* Luopan Mode is displayed,  
*Then*:
- Ring 4 shows "卯" under the pointer
- Numerical panel shows "卯 (Mǎo)"
- Ring 3 shows "☳ 震 東" under the pointer

**Scenario D — 分金 visibility**

*Given* confidence is "High" (all conditions met per master REQ §8.1),  
*When* Luopan Mode is active,  
*Then*: 分金 division is shown in numerical panel

*Given* confidence is "Moderate",  
*When* Luopan Mode is active,  
*Then*: 分金 panel shows "N/A — calibrate for 分金 precision"

**Scenario E — 坐向 lock**

*Given* confidence is "High", device pointing at 45° (艮),  
*When* user taps "Lock 向",  
*Then*:
- 向 locked at 45° (艮); 坐 derived as 225° (坤)
- Overlay shows: "向: 艮 (45.0° True N)" and "坐: 坤 (225.0° True N)"
- Gold tick mark visible at 45° on dial ring
- Live dial continues to rotate; pointer shows current heading

*Given* confidence is "Poor",  
*When* user attempts to tap "Lock 向",  
*Then*: Button is disabled; tooltip shows "Cannot lock — heading is unreliable"

**Scenario F — Localization**

*Given* system locale is `en`,  
*When* Luopan Mode is opened with default settings,  
*Then*: All ring labels display in Traditional Chinese characters (不是英文)

*Given* user enables "Show romanization",  
*When* Luopan Mode is displayed,  
*Then*: Pinyin appears below each character (e.g., "子 Zǐ", "南 Nán")

**Scenario G — Ring visibility toggle**

*Given* user long-presses the luopan dial,  
*When* the ring visibility menu appears and user hides Ring 4 (十二地支),  
*Then*: Ring 4 disappears; other rings remain; heading computation is unaffected; hiding is not persisted across app restarts (or is persisted — engineering decision, but must be documented in settings)

---

## 11. Open Questions and Risks Specific to Phase 3

**Risk P3-R1 — Legibility on small screens:** A 5-inch screen with 6 concentric rings may make Ring 6 (六十分金) labels too small to read without zoom. The pinch-to-zoom mitigation (REQ-DISPLAY-04 interaction spec) must be implemented before release.

**Risk P3-R2 — 先天八卦 vs. 後天八卦 ring alignment:** The two 八卦 rings have different trigram-to-direction mappings (see [§10.2a](REQ-luopan-compass.md#102a-ring-label-reference--後天八卦-ring-3) for contrast table). Engineering MUST implement both as static look-up tables keyed by sector index, not by computed heading, to avoid floating-point boundary errors placing a trigram in the wrong sector.

**Risk P3-R3 — Feng shui practitioner validation:** The luopan ring arrangement must be validated by at least one practicing feng shui consultant before public release. In particular, confirm: (a) the direction-to-trigram mappings match the 三元 / 三合 school conventions used by target users, and (b) the 分金 ring labels are correct for v1.

**OQ-P3-01 — School of feng shui:** The 二十四山 and 分金 ring assignments differ between schools (三元, 三合, 玄空). The current spec uses the most common 三元 / 通用 convention. Confirm target school with Master Li persona before ring labels are finalized.
