# FSPEC-luopan-p3-luopan-mode
## Functional Specification: Phase 3 — Luopan Display Mode

| Field | Value |
|-------|-------|
| **ID** | FSPEC-LUOPAN-P3 |
| **Version** | 1.1 |
| **Date** | 2026-04-25 |
| **Status** | Draft |
| **Linked REQ** | [REQ-luopan-p3-luopan-mode v0.3-draft](REQ-luopan-p3-luopan-mode.md) |
| **Parent REQ** | [REQ-luopan-compass v0.3-draft](../REQ-luopan-compass.md) |
| **Author** | Product |
| **Revised** | 2026-04-25 — SE/TE cross-review iteration 1 feedback addressed |

---

## Table of Contents

1. [Scope](#1-scope)
2. [Feature Flows](#2-feature-flows)
   - [Flow 1: Entering Luopan Mode](#flow-1-entering-luopan-mode-from-modern-mode)
   - [Flow 2: Dial Rotation](#flow-2-dial-rotation-as-device-rotates)
   - [Flow 3: Numerical Readout Panel](#flow-3-numerical-readout-panel-updates)
   - [Flow 4: 坐向 Lock](#flow-4-坐向-lock)
   - [Flow 5: Ring Visibility Toggle](#flow-5-ring-visibility-toggle)
   - [Flow 6: Pinch-to-Zoom](#flow-6-pinch-to-zoom)
   - [Flow 7: Localization Toggles](#flow-7-localization-toggles)
3. [Business Rules](#3-business-rules)
4. [Data Definitions](#4-data-definitions)
5. [Error States and Edge Cases](#5-error-states-and-edge-cases)
6. [Acceptance Criteria](#6-acceptance-criteria)

---

## 1. Scope

### 1.1 Capabilities Delivered in Phase 3

| Capability | Description |
|------------|-------------|
| Luopan Mode — dial | Rotating six-ring dial with fixed pointer; counter-rotates 1:1 with device heading |
| Six concentric rings | 天池 (needle), 先天八卦, 後天八卦 + 方位, 十二地支, 二十四山, 六十分金 |
| Numerical readout panel | Six-field panel: 山, 十二地支, 後天八卦 方位, bearing + north type, 分金, confidence badge |
| 坐向 lock | "Lock 向" button; derives 坐 = (向 + 180°) mod 360°; gold tick mark on Ring 5 |
| Ring visibility toggle | Long-press opens BottomSheetDialog; per-ring switches; session-only |
| Pinch-to-zoom | Scale 0.8×–2.0×; session-only, survives config changes |
| Localization toggles | "Show romanization" (pinyin) and "Show in my language" (English equivalents) |
| Modern Mode | Unchanged; still accessible |

### 1.2 Capabilities Deferred from Phase 3

| Capability | Deferred To |
|------------|-------------|
| Sighting Mode (REQ-DISPLAY-07, 08) | Phase 5 |
| Heading smoothing control (REQ-DISPLAY-09) | Phase 5 |
| Dark/light theme toggle (REQ-DISPLAY-12) | Phase 5 |
| 六十龍 / 七十二龍 rings | v2 product roadmap |
| 天盤/人盤/地盤 triple-plate luopan | v2 product roadmap |

### 1.3 Assumptions and Prerequisites

- Phase 2 is complete and stable: sensor fusion pipeline, confidence model, WMM2025 true-north correction, bearing capture, and Modern Mode are all operational.
- `OverallConfidence` enum with values `HIGH | MODERATE | POOR | STABILIZING | SENSOR_ERROR` exists in `com.luopan.compass.model.OverallConfidence` from Phase 1.
- `CompassViewModel` is scoped to `CompassActivity` and shared across fragments via `activityViewModels()`.
- The sensor pipeline runs continuously in the ViewModel regardless of which mode is active.

### 1.4 Navigation Architecture — Phase 3 Migration Prerequisite

The current codebase uses a single `CompassActivity` with a flat `ConstraintLayout`. Phase 3 requires migrating to a fragment-based navigation architecture before any Phase 3 features can be implemented. This migration is **in-scope for Phase 3**.

**Navigation contract (what is required, not how to implement it):**

| Contract element | Specification |
|-----------------|---------------|
| Mode switcher trigger | User taps a tab in the bottom `TabLayout`; this triggers navigation between fragments |
| Navigation destinations | Two fragments: `ModernCompassFragment` (existing compass logic migrated from Activity) and `LuopanFragment` (new) |
| Navigation controller | A `NavHostFragment` with a `NavController` manages the back-stack; `NavigationUI.setupWithNavController` wires the `TabLayout` |
| ViewModel sharing | `CompassViewModel` is scoped to `CompassActivity`; both fragments obtain it via `activityViewModels()` so sensor data is never interrupted during mode transitions |
| Back-stack behavior | Tapping a tab navigates to that destination with `popUpTo(startDestination, inclusive = false)`; the back stack never grows beyond one entry per destination |
| Loading state between fragments | While the incoming fragment is inflating (before first `onDraw`), the screen shows a dark background at #2C0E0E; no spinner is required given the 300 ms budget |
| Persistence | The last-used mode is stored under key `display_mode` in `SettingsRepository` and restored on cold start |

**Files that must be created or modified as part of this migration:**
- `activity_compass.xml` — restructured to host `NavHostFragment` + bottom `TabLayout`
- `nav_graph.xml` — created with `dest_modern` and `dest_luopan` destinations
- `ModernCompassFragment` — new fragment; existing `CompassActivity` UI logic migrates here
- `LuopanFragment` — new fragment; Phase 3 luopan UI lives here

This section describes the navigation **contract** (triggers, back-stack behavior, loading state). Implementation details are the engineering team's responsibility and belong in the TSPEC.

---

## 2. Feature Flows

### Flow 1: Entering Luopan Mode (from Modern Mode)

**Trigger:** User taps the "Luopan" tab in the bottom `TabLayout`.

**Preconditions:** Modern Mode is active. Sensor pipeline is running. Phase 2 capabilities are available.

**Steps:**

1. The system receives the tab-tap event and navigates to `LuopanFragment` via `NavController`.
2. While `LuopanFragment` is inflating (before first `onDraw`), the screen shows a dark background at the Luopan theme color (#2C0E0E). No spinner is shown.
3. The mode transition completes within 300 ms from tap to first complete dial frame rendered.
4. `LuopanFragment` reads the current `LuopanState` from `CompassViewModel` — the ViewModel has been continuously computing heading and sector lookups regardless of which mode was displayed.
5. The dial renders at the current heading. The pointer appears fixed at screen top. The dial is positioned so that the sector corresponding to the current heading is centered under the pointer.
6. The numerical readout panel appears alongside (or below) the dial, populated with all six fields per the current `LuopanState`.
7. If a 坐向 lock was active before the mode switch (lock preserved across mode switches), the gold tick mark is shown on the dial and the 坐向 overlay is restored.
8. The `display_mode` key in `SettingsRepository` is updated to `LUOPAN` so the next cold start restores Luopan Mode.

**Exit conditions:**
- User taps the "Modern" tab → navigate back to Modern Mode; `display_mode` set to `MODERN`.
- App goes to background → sensors are unregistered; heading freezes at last computed value.

**Decision points:**

| Condition | Behavior |
|-----------|----------|
| 坐向 was locked before switching away | Overlay and gold tick mark are restored |
| 坐向 was not locked | No overlay; dial shows live heading |
| Confidence is SENSOR_ERROR | Dial and readout panel still render; all fields show their error-state values (see Flow 3); "Lock 向" is disabled |

---

### Flow 2: Dial Rotation as Device Rotates (Pointer Fixed, Dial Counter-Rotates)

**Trigger:** `CompassViewModel` emits an updated `bearingDeg` value.

**Preconditions:** Luopan Mode is active; `LuopanFragment` is in the foreground.

**Steps:**

1. The sensor fusion pipeline in `CompassViewModel` computes a new heading value (`bearingDeg`) and emits an updated `LuopanState` to the UI layer.
2. `LuopanFragment` observes the `LuopanState` flow. On update, it passes the new `bearingDeg` to `LuopanView` (the custom Canvas view rendering the dial).
3. `LuopanView` computes the dial rotation angle: `dialRotationDeg = -bearingDeg (mod 360°)`. The negative value is required because the dial counter-rotates relative to device rotation so that the physically-north sector stays aligned under the fixed north pointer. The modular reduction ensures the angle remains in `[0°, 360°)`.
4. `LuopanView` applies the rotation transform to the entire ring assembly (Rings 1–6 and all labels rotate together as a unit). The pointer graphic is drawn outside the rotation transform and remains fixed at screen top center.
5. The dial rotation happens continuously and smoothly — no snapping to sector boundaries.
6. The update rate is ≥ 20 Hz on the minimum target device (API 26, mid-range hardware).
7. The numerical readout panel updates in the same `LuopanState` emission: all six fields update simultaneously with the dial rotation.
8. If pinch-to-zoom is active, the scale transform is applied first (scale around dial center), then the rotation transform. This order ensures uniform dial expansion before heading-based rotation.

**Mapping rule:** When the device rotates clockwise by angle θ, the dial counter-rotates by θ. The sector currently pointed at by the fixed pointer reflects the device's current heading. Accuracy: 1:1 mapping ±2° (limited by sensor fusion, not by the rendering logic).

**Performance requirement:** Each `onDraw` call completes in ≤ 16 ms. Ring geometry (arc paths, sector bounds) is pre-computed in `onSizeChanged` and cached — not recomputed each frame.

---

### Flow 3: Numerical Readout Panel Updates (All 6 Fields, All Confidence States)

**Trigger:** `LuopanState` emitted by `CompassViewModel`.

**Preconditions:** Luopan Mode is active.

**Panel layout — canonical field order (left to right or top to bottom):**

```
[山 char] ([山 pinyin])  ·  [地支 char] ([地支 pinyin])  ·  [trigram] [卦名] [方位]  ·  [bearing]°  ·  [north type]  ·  [分金 field]  ·  [confidence badge]
```

**Field derivation:**

| Field | Source | Notes |
|-------|--------|-------|
| 山 char + pinyin | Ring 5 (二十四山) lookup by `bearingDeg` | Static LUT; 24 entries; 15° per sector |
| 地支 char + pinyin | Ring 4 (十二地支) lookup by `bearingDeg` | Static LUT; 12 entries; 30° per sector; 子 wraps at 0° |
| Trigram + 卦名 + 方位 | Ring 3 (後天八卦) lookup by `bearingDeg` | Static LUT; 8 entries; 45° per sector; 坎/北 wraps at 0° |
| Bearing in degrees | `bearingDeg` from `LuopanState` | Displayed to 0.1° precision |
| North type | `northLabel` from `LuopanState` | "True N" or "Mag N" |
| 分金 field | Ring 6 (六十分金) lookup, confidence-gated | Shown only when confidence is `HIGH`; otherwise shows substitute text |
| Confidence badge | `confidence` from `LuopanState` | Text label + color; see table below |

**All field values by confidence state:**

| `OverallConfidence` | 山 field | 地支 field | 後天八卦 field | Bearing field | 分金 field | Confidence badge |
|---------------------|----------|-----------|--------------|---------------|-----------|-----------------|
| `HIGH` | Shown | Shown | Shown | Shown | Shown (e.g., "壬午分金") | "High" (green) |
| `MODERATE` | Shown | Shown | Shown | Shown | "N/A — calibrate for 分金 precision" | "Moderate" (amber) |
| `POOR` | Shown | Shown | Shown | Shown | "N/A — calibrate for 分金 precision" | "Poor" (red) |
| `STABILIZING` | Shown | Shown | Shown | Shown | "N/A — calibrate for 分金 precision" | "Calibrating..." (amber) |
| `SENSOR_ERROR` | "—" | "—" | "—" | "—" | "N/A — calibrate for 分金 precision" | "Sensor error" (red) |

**Example outputs (bearing = 180°, True North):**

- High confidence: `午 (Wǔ) · 午 (Wǔ) · ☲ 離 南 · 180.0° · True N · 壬午分金 · High`
- Moderate confidence: `午 (Wǔ) · 午 (Wǔ) · ☲ 離 南 · 180.0° · True N · N/A — calibrate for 分金 precision · Moderate`
- Sensor error: `— · — · — · — · True N · N/A — calibrate for 分金 precision · Sensor error`

**Localization effects on the panel:**
- "Show romanization" ON: pinyin fields are shown (e.g., "(Wǔ)"). When OFF, pinyin fields are hidden and only the Chinese character is shown.
- "Show in my language" ON: character fields use English equivalents from the mapping tables (§4.5). For example, 後天八卦 field shows "Zhen · East" instead of "☳ 震 東". Trigram symbols are retained regardless of language mode.

**Update timing:** The panel updates synchronously with the dial rotation in the same `LuopanState` observation callback. There is no separate update cadence for the panel vs. the dial.

---

### Flow 4: 坐向 Lock

#### 4a. Locking 向

**Trigger:** User taps the "Lock 向" button.

**Preconditions:** Confidence is `HIGH` or `MODERATE` (button is enabled). Luopan Mode is active.

**Steps:**

1. User rotates device to the desired 向 (facing) direction. The numerical readout panel shows the current bearing and 山 label in real time.
2. User taps "Lock 向".
3. `CompassViewModel` records the current `bearingDeg` as `xiangBearing`. This value is **always stored as a True North bearing**, regardless of the current north-type display setting. If the user is currently viewing Magnetic North, the ViewModel converts the displayed magnetic bearing back to True North before storing: `xiangBearing_trueN = displayedBearing_magN + declinationDeg`. The `xiangBearing` field in `LuopanState` always holds a True North value.
4. `CompassViewModel` looks up the corresponding 山 label from the Ring 5 LUT as `xiangMountain`. The Ring 5 LUT lookup always uses the True North bearing.
5. `CompassViewModel` computes `zuoBearing_trueN = (xiangBearing_trueN + 180.0) mod 360.0` and looks up `zuoMountain` from the Ring 5 LUT.
6. `isLockActive` is set to `true` in `LuopanState`. This state is held in `CompassViewModel` memory (not persisted to `SettingsRepository`).
7. `LuopanFragment` observes the updated `LuopanState` and:
   a. Renders a persistent 坐向 overlay (e.g., a card or banner) showing:
      - "向: [xiangMountain] ([displayBearing formatted to 1 decimal]° [northType])"
      - "坐: [zuoMountain] ([zuoDisplayBearing formatted to 1 decimal]° [northType])"
      where `displayBearing` is converted to the current north reference for display (True N or Mag N).
   b. Draws a gold tick mark on **Ring 5 (二十四山)** — the outermost labeled ring — at the angular position corresponding to the locked 向 True North bearing. The tick mark rotates with the dial (it is anchored at the locked bearing relative to the ring assembly, not to the screen). As the device rotates, the tick mark moves on screen while the fixed pointer continues to show the live heading. When Ring 5 is hidden, the tick mark is also hidden.
   c. The live dial continues to rotate normally. The live numerical readout panel continues to update.
8. The "Lock 向" button label changes to "Clear 向".

**Display format for overlay:**
- North type in overlay matches the current north type setting: "True N" or "Mag N".
- 分金 label is NOT shown in the 坐向 overlay when confidence is `MODERATE` (only shown when `HIGH`).

#### 4b. Unlocking (Clearing 向)

**Trigger:** User taps "Clear 向".

**Steps:**

1. `CompassViewModel` clears `xiangBearing`, `xiangMountain`, `zuoBearing`, `zuoMountain`; sets `isLockActive = false`.
2. `LuopanFragment` removes the 坐向 overlay and the gold tick mark from the dial.
3. The button label reverts to "Lock 向".

#### 4c. Lock State across Mode Switches

**Rule:** When the user switches from Luopan Mode to Modern Mode while 坐向 is locked, the lock is **preserved** in `CompassViewModel` memory. When the user returns to Luopan Mode in the same session, the overlay and tick mark are restored. The lock is cleared only by explicit "Clear 向" action or app cold start.

**Steps (mode switch while locked):**

1. User is in Luopan Mode with 坐向 locked at `xiangBearing_trueN`.
2. User taps the "Modern" tab → `LuopanFragment` navigates away. Lock state remains in `CompassViewModel`.
3. User taps the "Luopan" tab → `LuopanFragment` inflates. On first observation of `LuopanState`, `isLockActive = true` is detected.
4. The overlay and gold tick mark are rendered immediately on first draw.

#### 4d. North Reference Switch While Locked

**Trigger:** User changes north type (True N ↔ Mag N) via the north-type toggle while 坐向 is locked.

**Internal storage rule:** `xiangBearing` is always stored as a True North bearing (see §4a step 3). The north-type switch does NOT change the stored True North bearing. Only the display values change.

**Steps:**

1. `CompassViewModel` receives the north type change event.
2. The stored `xiangBearing_trueN` remains unchanged — it is NOT reread from the live device heading.
3. For display purposes, the bearing shown in the overlay is converted to the new reference:
   - **True N → Mag N:** `displayBearing = xiangBearing_trueN − declinationDeg`
   - **Mag N → True N:** `displayBearing = xiangBearing_magN + declinationDeg`
4. The 山 labels (`xiangMountain`, `zuoMountain`) are derived from the **True North bearing** using the Ring 5 LUT (BR-01). They are NOT recalculated from the display bearing. 山 labels always reflect the True North sector and do NOT change when the north type switches.
5. The overlay updates immediately to show the new display bearing values and the updated north type label ("True N" or "Mag N"). The 山 labels remain the same.
6. `LuopanState.bearingDeg` and `LuopanState.northLabel` update to reflect the new north reference for the live readout panel as well.

**Worked example:**
- Locked at 向 = 45° True N → `xiangMountain` = 艮, `zuoBearing_trueN` = 225°, `zuoMountain` = 坤
- User switches to Magnetic North; local declination = −3.5°
- Display bearing for 向: `45.0° − (−3.5°) = 48.5°` → displayed as "向: 艮 (48.5° Mag N)"
- Display bearing for 坐: `225.0° − (−3.5°) = 228.5°` → displayed as "坐: 坤 (228.5° Mag N)"
- 山 labels remain 艮 and 坤 (derived from 45° and 225° True N — unchanged)

**Key principle:** Switching north type changes the DISPLAY of bearing numbers and the northLabel field only. The underlying True North bearing used for 山 sector lookup does not change. 山 labels are invariant to north-type switches.

**Decision point:** If the user was viewing Magnetic North at the time of locking and the displayed magnetic bearing was close to a 山 sector boundary, the True North bearing used for storage may fall in a different sector than the displayed magnetic bearing. The stored True North bearing determines the 山 label.

#### 4e. Button State Table

| Confidence | "Lock 向" button state | Tooltip on disabled tap |
|------------|----------------------|------------------------|
| `HIGH` | Enabled | — |
| `MODERATE` | Enabled | — |
| `POOR` | Disabled (greyed out) | "Cannot lock — heading is unreliable" |
| `STABILIZING` | Disabled (greyed out) | "Cannot lock — heading is unreliable" |
| `SENSOR_ERROR` | Disabled (greyed out) | "Cannot lock — heading is unreliable" |

---

### Flow 5: Ring Visibility Toggle

**Trigger:** User long-presses anywhere on the dial canvas for ≥ 500 ms.

**Preconditions:** Luopan Mode is active; `LuopanFragment` is in the foreground.

**Steps:**

1. After ≥ 500 ms long-press, a haptic pulse is emitted (if device supports it) and a `BottomSheetDialog` slides up from the bottom of the screen.
2. The sheet contains six toggle rows, one per ring, in order:
   - Ring 1 — 天池 (Heaven's Pool)
   - Ring 2 — 先天八卦 (Earlier Heaven Bagua)
   - Ring 3 — 後天八卦 (Later Heaven Bagua)
   - Ring 4 — 十二地支 (Twelve Earthly Branches)
   - Ring 5 — 二十四山 (Twenty-Four Mountains)
   - Ring 6 — 六十分金 (Sixty Gold Divisions)
3. Each row shows a `Switch` control. The initial state of each switch reflects the current in-memory ring visibility flags (`luopan_ring_visible_1` through `luopan_ring_visible_6`).
4. When a user toggles a switch:
   a. The corresponding in-memory visibility flag is updated immediately.
   b. `LuopanView` re-renders: the toggled ring disappears (or reappears). Remaining rings remain unaffected.
   c. The update is live while the sheet is open — no "Done" required to see the effect.
5. The sheet is dismissed when:
   - User taps outside the sheet, OR
   - User swipes the sheet downward, OR
   - User taps the "Done" button at the bottom of the sheet.
6. Ring visibility flags are held in `CompassViewModel` memory only. They are **not** written to `SettingsRepository`.
7. All visibility flags reset to `true` (all rings visible) on the next cold start.

**Accessibility alternative:** The ring visibility sheet is also accessible via a three-dot overflow menu button in the Luopan Mode action bar. This provides TalkBack users an alternative to the long-press gesture.

**Effect on heading:** Hiding a ring has no effect on heading computation. The sensor pipeline, sector lookups, and all numerical readout values continue to compute from all rings regardless of visibility.

**Decision points:**

| Condition | Behavior |
|-----------|----------|
| User hides Ring 6 (分金) | The 分金 ring disappears from the dial but the 分金 field in the numerical readout panel remains visible (when High confidence) |
| User hides Ring 5 (二十四山) | Ring 5 disappears from the dial; the gold tick mark (if 坐向 is locked) is also hidden because the tick mark is drawn on Ring 5 |
| User hides all rings | The dial shows only the fixed pointer and the background; heading computation is unaffected |
| User cold-starts app | All rings reset to visible; previous session's visibility state is not restored |

---

### Flow 6: Pinch-to-Zoom

**Trigger:** User performs a two-finger pinch or spread gesture on the dial canvas.

**Preconditions:** Luopan Mode is active; `LuopanFragment` is in the foreground.

**Steps:**

1. The pinch gesture is recognized by the system gesture detector.
2. The scale factor is computed as the ratio of the current finger spread to the initial finger spread at gesture start. The scale is clamped: `newScale = clamp(currentScale × gestureScaleFactor, 0.8, 2.0)`.
3. `LuopanView` applies the scale around the center of the dial. The dial grows or shrinks within the available canvas area. The pointer position scales with the dial.
4. The scale value is stored in `CompassViewModel` memory as `luopan_zoom_scale` (float, default 1.0). It is not written to `SettingsRepository`.
5. The scale survives configuration changes (e.g., screen rotation) via `CompassViewModel` state (since `CompassViewModel` is not destroyed on config change).
6. The scale resets to 1.0 on the next cold start.

**Scale boundaries:**

| Scale condition | Behavior |
|----------------|----------|
| Pinch to < 0.8× | Scale is clamped at 0.8×; no further reduction |
| Spread to > 2.0× | Scale is clamped at 2.0×; no further enlargement |
| Config change (e.g., screen rotation) | Scale is preserved via ViewModel |
| App cold start | Scale resets to 1.0× |

**Effect on readout panel:** The numerical readout panel is not affected by pinch-to-zoom. Only the dial canvas scales.

---

### Flow 7: Localization Toggles

Two independent toggles control how ring labels and numerical readout fields are rendered:

#### Toggle A: "Show romanization" (`luopan_show_romanization`)

**Default:** OFF (no pinyin shown).

**Scope:** Persisted in `SettingsRepository`. Restored on cold start.

**Steps when turned ON:**

1. User enables the "Show romanization" toggle (accessible from Settings or the Luopan Mode overflow menu).
2. `SettingsRepository` saves `luopan_show_romanization = true`.
3. `LuopanView` re-renders all ring labels to show pinyin below each Chinese character (e.g., "子 Zǐ", "南 Nán").
4. The numerical readout panel shows pinyin alongside each character field (e.g., "午 (Wǔ)" instead of "午").

**Steps when turned OFF:**

1. User disables the toggle.
2. `SettingsRepository` saves `luopan_show_romanization = false`.
3. Ring labels revert to Chinese characters only; pinyin is removed from the panel.

#### Toggle B: "Show in my language" (`luopan_show_my_language`)

**Default:** OFF (ring labels always in Traditional Chinese).

**Scope:** Persisted in `SettingsRepository`. Restored on cold start.

**Steps when turned ON:**

1. User enables the "Show in my language" toggle.
2. `SettingsRepository` saves `luopan_show_my_language = true`.
3. `LuopanView` re-renders ring labels using English equivalents from the mapping tables (§4.5), where defined. Entries without a standard English equivalent continue to display in Traditional Chinese.
4. The numerical readout panel applies the same substitution to all character fields: e.g., the 後天八卦 field shows "Zhen · East" instead of "☳ 震 東".
5. Trigram Unicode symbols (☰ ☱ ☲ ☳ ☴ ☵ ☶ ☷) are retained in all language modes.

**Steps when turned OFF:**

1. User disables the toggle.
2. `SettingsRepository` saves `luopan_show_my_language = false`.
3. All ring labels and panel fields revert to Traditional Chinese.

**Independence of the two toggles:** Both toggles may be active simultaneously. When both are ON, English equivalents are shown with pinyin romanization below them where applicable. When only "Show romanization" is ON with zh-Hant labels, pinyin appears below each character. When only "Show in my language" is ON, English labels are shown without additional pinyin.

**Default language baseline:** Ring labels MUST display in Traditional Chinese characters by default, regardless of system locale. A Japanese system locale does not cause Japanese ring labels to appear; an English system locale does not cause English labels to appear. Only the explicit "Show in my language" toggle changes ring label language.

---

## 3. Business Rules

### BR-01: Universal Sector Boundary Membership Rule

**Rule:** All rings (Rings 2, 3, 4, 5, and 6) use **inclusive-left, exclusive-right** `[start°, end°)` interval membership. A bearing exactly equal to the start boundary of a sector belongs to that sector. A bearing exactly equal to the end boundary belongs to the next sector (or wraps to the first sector at 360°).

**Formal definition:** For a sector with range `[a°, b°)`:
- Bearing `θ` belongs to this sector if and only if `a ≤ θ < b` (in modular arithmetic for wrap-around sectors).

**Applies to:** Rings 2, 3, 4, 5, and 6 for all sector lookup operations.

---

### BR-02: 子 Wrap-Around Rule (Ring 4)

**Rule:** The 子 sector in Ring 4 (十二地支) straddles the 0°/360° boundary and spans `[345°, 15°)` in modular space. This is equivalent to `[345°, 360°) ∪ [0°, 15°)`.

**Lookup algorithm:** For Ring 4, when evaluating bearing θ:
- If `345° ≤ θ < 360°` OR `0° ≤ θ < 15°` → sector is 子.
- Otherwise, use the standard `[start°, end°)` table lookup.

**Key boundary assertions:**

| Bearing | Expected Ring 4 | Reason |
|---------|-----------------|--------|
| 344.9° | 亥 `[315°, 345°)` | 344.9 < 345, inside 亥 |
| 345.0° | 子 `[345°, 15°)` | 345.0 = start of 子, inclusive-left |
| 14.9° | 子 `[345°, 15°)` | 14.9 < 15, inside 子 |
| 15.0° | 丑 `[15°, 45°)` | 15.0 = start of 丑, exclusive end of 子 |

---

### BR-03: Analogous Wrap-Around Rules for Rings 3 and 5

**Ring 3 (後天八卦):** The ☵ 坎 北 sector spans `[337.5°, 22.5°)` modularly.
- Bearings in `[337.5°, 360°) ∪ [0°, 22.5°)` → ☵ 坎 北.

**Ring 5 (二十四山):** Two sectors straddle 0°:
- 壬: `[337.5°, 352.5°)` — does NOT straddle 0°; standard evaluation.
- 子: `[352.5°, 7.5°)` → `[352.5°, 360°) ∪ [0°, 7.5°)`.

**Key boundary assertions for Rings 3 and 5:**

| Bearing | Expected Ring 3 | Expected Ring 5 |
|---------|-----------------|-----------------|
| 22.4° | ☵ 坎 北 | 癸 `[7.5°, 22.5°)` |
| 22.5° | ☶ 艮 東北 | 丑 `[22.5°, 37.5°)` |
| 7.4° | ☵ 坎 北 | 子 `[352.5°, 7.5°)` |
| 7.5° | ☵ 坎 北 | 癸 `[7.5°, 22.5°)` |

---

### BR-04: 壬子分金 Wrap-Around Rule (Ring 6)

**Rule:** The 壬子分金 sector in Ring 6 (六十分金) straddles 0° and spans `[358°, 4°)` in modular space. This is equivalent to `[358°, 360°) ∪ [0°, 4°)`.

**Key assertion:** A bearing of 0° falls within the 壬子分金 sector.

| Bearing | Expected Ring 6 | Range |
|---------|-----------------|-------|
| 357.9° | 庚子分金 | `[352°, 358°)` |
| 358.0° | 壬子分金 | `[358°, 4°)` |
| 0.0° | 壬子分金 | `[358°, 4°)` |
| 4.0° | 甲子分金 | `[4°, 10°)` |

---

### BR-05: Confidence State → UI State Mapping Table

| `OverallConfidence` | "Lock 向" button | Confidence badge text | Confidence badge color | 分金 field in readout | 分金 label in 坐向 overlay |
|---------------------|-----------------|----------------------|----------------------|----------------------|-----------------------------|
| `HIGH` | **Enabled** | "High" | Green | Shown (e.g., "壬午分金") | Shown |
| `MODERATE` | **Enabled** | "Moderate" | Amber | "N/A — calibrate for 分金 precision" | Hidden |
| `POOR` | **Disabled** | "Poor" | Red | "N/A — calibrate for 分金 precision" | Hidden |
| `STABILIZING` | **Disabled** | "Calibrating..." | Amber | "N/A — calibrate for 分金 precision" | Hidden |
| `SENSOR_ERROR` | **Disabled** | "Sensor error" | Red | "N/A — calibrate for 分金 precision" | Hidden |

---

### BR-06: 坐向 Lock Derivation Rule

**Rule:** When "Lock 向" is tapped at bearing `向` degrees (True North), the system derives 坐 as:

```
坐 = (向 + 180.0) mod 360.0
```

**Examples:**

| 向 (locked True North bearing) | 坐 = (向 + 180) mod 360 |
|---------------------|------------------------|
| 0° | 180° |
| 45° | 225° |
| 90° | 270° |
| 180° | 0° |
| 270° | 90° |
| 350° | 170° |

**山 label derivation:** Both `xiangMountain` and `zuoMountain` are obtained by applying the Ring 5 (二十四山) LUT lookup to `xiangBearing_trueN` and `zuoBearing_trueN` respectively, using the BR-01 sector membership rule. 山 labels always use True North bearings.

---

### BR-07: 分金 Display Rule

**Rule:** The 分金 label is displayed in the numerical readout panel **only** when `OverallConfidence == HIGH`. For all other confidence states, the 分金 field displays the substitute text: "N/A — calibrate for 分金 precision".

**Scope:** This rule applies to both the numerical readout panel and the 坐向 lock overlay.

---

### BR-08: Ring Label Language Default Rule

**Rule:** Ring labels (on the dial and in the numerical readout panel) MUST render in Traditional Chinese characters by default, regardless of system locale. The system locale does NOT affect ring label language. Only the explicit `luopan_show_my_language` toggle changes ring label language.

---

### BR-09: Session-Only vs. Persisted Settings

| Setting Key | Scope | Behavior |
|-------------|-------|----------|
| `display_mode` | **Persisted** | Written to `SettingsRepository`; restored on cold start |
| `luopan_show_romanization` | **Persisted** | Written to `SettingsRepository`; restored on cold start |
| `luopan_show_my_language` | **Persisted** | Written to `SettingsRepository`; restored on cold start |
| `luopan_ring_visible_1` through `_6` | **Session-only** | In `CompassViewModel` memory only; reset to `true` on cold start |
| `luopan_zoom_scale` | **Session-only** | In `CompassViewModel` memory only; reset to `1.0` on cold start; survives config changes |

**`SettingsRepository` extension required:** Phase 3 requires adding three new keys to `SettingsRepository` (`com.luopan.compass.settings.SettingsRepository`): `display_mode`, `luopan_show_romanization`, and `luopan_show_my_language`. These must be implemented as part of Phase 3 alongside the new fragment and navigation infrastructure.

**Non-normative rationale:** Ring visibility and zoom are moment-to-moment readability aids for a consultation session. Persisting them risks silently hiding rings or leaving the user at an unexpected zoom level on the next launch.

---

### BR-10: 坐向 Lock Persistence Scope

**Rule:** The 坐向 lock is held in `CompassViewModel` memory. It is **preserved** across mode switches within the same session (Luopan ↔ Modern). It is **cleared** on app cold start. It is **not** written to `SettingsRepository`.

---

### BR-11: Dial Rotation Math

**Rule:** The rendered dial rotation angle equals the negative of the current magnetic heading, reduced modulo 360°:

```
dialRotationDeg = -bearingDeg (mod 360°)
```

This ensures the ring assembly counter-rotates relative to device rotation, keeping the physically-north sector aligned under the fixed north pointer. The pointer element does not rotate; only the ring assembly (Rings 1–6 and all labels as a single unit) rotates.

**Examples:**

| Device heading (bearingDeg) | dialRotationDeg |
|-----------------------------|-----------------|
| 0° | 0° |
| 90° (device points East) | 270° (dial rotates 270° CCW = 90° CW of ring assembly CCW) |
| 180° | 180° |
| 270° | 90° |

---

## 4. Data Definitions

### 4.1 `LuopanState` Data Contract

`LuopanState` is the complete data contract emitted by `CompassViewModel` for consumption by `LuopanFragment`. All fields required to render the dial, numerical readout panel, and 坐向 overlay are contained within this state object.

```kotlin
data class LuopanState(
    // Live bearing and north reference (required for numerical readout panel)
    val bearingDeg: Float,              // Current bearing in degrees (True N or Mag N per northLabel)
    val northLabel: String,             // "True N" or "Mag N"

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

    // 坐向 lock state (bearings stored as True North internally)
    val xiangBearing: Double?,         // locked 向 bearing in True North degrees (null = unlocked)
    val xiangMountain: String?,        // 山 label for 向 (null = unlocked)
    val zuoBearing: Double?,           // derived 坐 True North bearing = (向 + 180) mod 360 (null = unlocked)
    val zuoMountain: String?,          // 山 label for 坐 (null = unlocked)
    val isLockActive: Boolean,         // true when 坐向 is locked

    // Confidence (mirrors CompassUiState.confidence)
    val confidence: OverallConfidence, // HIGH | MODERATE | POOR | STABILIZING | SENSOR_ERROR
)
```

**`bearingDeg` during SENSOR_ERROR:** `LuopanState.bearingDeg` retains the last valid bearing value during `SENSOR_ERROR` — it is not set to null or zero. The View is responsible for suppressing display (showing "—") when `confidence == SENSOR_ERROR`. The last retained `bearingDeg` value is also used to freeze the dial position at the last known heading.

**`northLabel` values:** "True N" when True North correction (WMM declination) is active; "Mag N" when Magnetic North is selected.

**`xiangBearing` and `zuoBearing`:** Always stored as True North bearings. For display, the View converts to the current north reference using the current declination value.

---

### 4.2 Ring Summary

| Ring # | Chinese | English | Type | Divisions | °/Division |
|--------|---------|---------|------|-----------|-----------|
| 1 | 天池 | Heaven's Pool | Rotating needle (center) | — | — |
| 2 | 先天八卦 | Earlier Heaven Bagua | Fuxi arrangement | 8 | 45° |
| 3 | 後天八卦 | Later Heaven Bagua | King Wen + 方位 | 8 | 45° |
| 4 | 十二地支 | Twelve Earthly Branches | Earthly Branches | 12 | 30° |
| 5 | 二十四山 | Twenty-Four Mountains | 24 Mountains | 24 | 15° |
| 6 | 六十分金 | Sixty Gold Divisions | 60 Gold Divisions | 60 | 6° |

### 4.3 Ring 2 Label Reference — 先天八卦 (Fuxi / Earlier Heaven Arrangement)

Sectors use the universal inclusive-left, exclusive-right `[start°, end°)` rule (BR-01).

| Sector # | Trigram | 卦名 | 方位 | Pinyin | Center Bearing | Sector Range |
|----------|---------|------|------|--------|---------------|--------------|
| 1 | ☰ | 乾 | 南 | Qián · Nán | 180° | 157.5°–202.5° |
| 2 | ☱ | 兌 | 東南 | Duì · Dōngnán | 135° | 112.5°–157.5° |
| 3 | ☲ | 離 | 東 | Lí · Dōng | 90° | 67.5°–112.5° |
| 4 | ☳ | 震 | 東北 | Zhèn · Dōngběi | 45° | 22.5°–67.5° |
| 5 | ☴ | 巽 | 北 | Xùn · Běi | 0° | 337.5°–22.5° |
| 6 | ☵ | 坎 | 西北 | Kǎn · Xīběi | 315° | 292.5°–337.5° |
| 7 | ☶ | 艮 | 西 | Gèn · Xī | 270° | 247.5°–292.5° |
| 8 | ☷ | 坤 | 西南 | Kūn · Xīnán | 225° | 202.5°–247.5° |

> **Note:** Ring 2 (先天八卦) is a decorative reference ring. Its label is NOT included in the numerical readout panel. Only Ring 3 (後天八卦) contributes the trigram field to the readout.

### 4.4 Ring 3 Label Reference — 後天八卦 (King Wen / Later Heaven Arrangement)

Each sector shows trigram symbol + 卦名 + 方位 direction name.

| Sector # | Trigram | 卦名 | 方位 | Pinyin | Center Bearing | Sector Range |
|----------|---------|------|------|--------|---------------|--------------|
| 1 | ☲ | 離 | 南 | Lí · Nán | 180° | 157.5°–202.5° |
| 2 | ☴ | 巽 | 東南 | Xùn · Dōngnán | 135° | 112.5°–157.5° |
| 3 | ☳ | 震 | 東 | Zhèn · Dōng | 90° | 67.5°–112.5° |
| 4 | ☶ | 艮 | 東北 | Gèn · Dōngběi | 45° | 22.5°–67.5° |
| 5 | ☵ | 坎 | 北 | Kǎn · Běi | 0° | 337.5°–22.5° |
| 6 | ☰ | 乾 | 西北 | Qián · Xīběi | 315° | 292.5°–337.5° |
| 7 | ☱ | 兌 | 西 | Duì · Xī | 270° | 247.5°–292.5° |
| 8 | ☷ | 坤 | 西南 | Kūn · Xīnán | 225° | 202.5°–247.5° |

### 4.5 Ring 4 Label Reference — 十二地支 (Twelve Earthly Branches)

The 子 sector wraps around 0°/360° (BR-02).

| # | 地支 | Pinyin | Center Bearing | Sector Range |
|---|------|--------|---------------|--------------|
| 1 | 子 | Zǐ | 0° | 345°–15° (wrap-around) |
| 2 | 丑 | Chǒu | 30° | 15°–45° |
| 3 | 寅 | Yín | 60° | 45°–75° |
| 4 | 卯 | Mǎo | 90° | 75°–105° |
| 5 | 辰 | Chén | 120° | 105°–135° |
| 6 | 巳 | Sì | 150° | 135°–165° |
| 7 | 午 | Wǔ | 180° | 165°–195° |
| 8 | 未 | Wèi | 210° | 195°–225° |
| 9 | 申 | Shēn | 240° | 225°–255° |
| 10 | 酉 | Yǒu | 270° | 255°–285° |
| 11 | 戌 | Xū | 300° | 285°–315° |
| 12 | 亥 | Hài | 330° | 315°–345° |

### 4.6 Ring 5 Label Reference — 二十四山 (Twenty-Four Mountains)

The 子 sector wraps around 0°/360°: `[352.5°, 7.5°)`.

| # | 山 | Pinyin | Center Bearing | Sector Range |
|---|---|--------|---------------|-------------|
| 1 | 壬 | Rén | 345° | 337.5°–352.5° |
| 2 | 子 | Zǐ | 0° | 352.5°–7.5° (wrap-around) |
| 3 | 癸 | Guǐ | 15° | 7.5°–22.5° |
| 4 | 丑 | Chǒu | 30° | 22.5°–37.5° |
| 5 | 艮 | Gèn | 45° | 37.5°–52.5° |
| 6 | 寅 | Yín | 60° | 52.5°–67.5° |
| 7 | 甲 | Jiǎ | 75° | 67.5°–82.5° |
| 8 | 卯 | Mǎo | 90° | 82.5°–97.5° |
| 9 | 乙 | Yǐ | 105° | 97.5°–112.5° |
| 10 | 辰 | Chén | 120° | 112.5°–127.5° |
| 11 | 巽 | Xùn | 135° | 127.5°–142.5° |
| 12 | 巳 | Sì | 150° | 142.5°–157.5° |
| 13 | 丙 | Bǐng | 165° | 157.5°–172.5° |
| 14 | 午 | Wǔ | 180° | 172.5°–187.5° |
| 15 | 丁 | Dīng | 195° | 187.5°–202.5° |
| 16 | 未 | Wèi | 210° | 202.5°–217.5° |
| 17 | 坤 | Kūn | 225° | 217.5°–232.5° |
| 18 | 申 | Shēn | 240° | 232.5°–247.5° |
| 19 | 庚 | Gēng | 255° | 247.5°–262.5° |
| 20 | 酉 | Yǒu | 270° | 262.5°–277.5° |
| 21 | 辛 | Xīn | 285° | 277.5°–292.5° |
| 22 | 戌 | Xū | 300° | 292.5°–307.5° |
| 23 | 乾 | Qián | 315° | 307.5°–322.5° |
| 24 | 亥 | Hài | 330° | 322.5°–337.5° |

### 4.7 Ring 6 Label Reference — 六十分金 (Sixty Gold Divisions)

> **Validation notice (OQ-P3-01):** This table uses the 三元/通用 convention. It MUST be validated by a practicing feng shui consultant before release. The 壬子分金 sector wraps at 0° per BR-04.

> **Note on Parent 山 column:** The `Parent 山` column is **informational metadata only** — it MUST NOT be used for programmatic lookups or cross-ring validation. The column reflects the traditional naming convention for each 分金 division and does not necessarily correspond to the Ring 5 sector at the stated center bearing. Do not derive Ring 5 labels from this column.

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

**Key unit-test bearings for Ring 6:**
- 180° → 壬午分金 (sector 178°–184°)
- 0° → 壬子分金 (sector 358°–4°; wraps at 0°)
- 90° → 壬卯分金 (sector 88°–94°)

### 4.8 "Show in My Language" — English Ring Label Mapping

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

*Trigram symbols (☰ ☱ ☲ ☳ ☴ ☵ ☶ ☷) are retained in all language modes.*

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

| 山 | Pinyin | English Label |
|----|--------|---------------|
| 壬 | Rén | Ren (Water-Yang) |
| 子 | Zǐ | Rat |
| 癸 | Guǐ | Gui (Water-Yin) |
| 丑 | Chǒu | Ox |
| 艮 | Gèn | Gen (Mountain) |
| 寅 | Yín | Tiger |
| 甲 | Jiǎ | Jia (Wood-Yang) |
| 卯 | Mǎo | Rabbit |
| 乙 | Yǐ | Yi (Wood-Yin) |
| 辰 | Chén | Dragon |
| 巽 | Xùn | Xun (Wind) |
| 巳 | Sì | Snake |
| 丙 | Bǐng | Bing (Fire-Yang) |
| 午 | Wǔ | Horse |
| 丁 | Dīng | Ding (Fire-Yin) |
| 未 | Wèi | Goat |
| 坤 | Kūn | Kun (Earth) |
| 申 | Shēn | Monkey |
| 庚 | Gēng | Geng (Metal-Yang) |
| 酉 | Yǒu | Rooster |
| 辛 | Xīn | Xin (Metal-Yin) |
| 戌 | Xū | Dog |
| 乾 | Qián | Qian (Heaven) |
| 亥 | Hài | Pig |

---

## 5. Error States and Edge Cases

### ES-01: SENSOR_ERROR Display

**Trigger:** `OverallConfidence == SENSOR_ERROR` — magnetometer is stuck, returns constant values for > 3 seconds, or returns {0, 0, 0}.

**Behavior:**

| Component | State |
|-----------|-------|
| Dial | Renders with the last valid bearing (`LuopanState.bearingDeg` retains last valid value); rotation is frozen at last known heading |
| Numerical readout — 山 field | Displays "—" |
| Numerical readout — 地支 field | Displays "—" |
| Numerical readout — 後天八卦 field | Displays "—" |
| Numerical readout — Bearing field | Displays "—" |
| Numerical readout — 分金 field | Displays "N/A — calibrate for 分金 precision" |
| Confidence badge | Displays "Sensor error" in red |
| "Lock 向" button | Displays "Clear 向"; lock remains active but frozen; tooltip "Cannot lock — heading is unreliable" if user taps |
| 坐向 overlay (if active) | Remains visible; bearing values are frozen at last-locked True North values; overlay badge (if any) shows "Sensor error" |

**Note on `LuopanState.bearingDeg` during SENSOR_ERROR:** The field retains the last valid bearing — it is not nulled or zeroed. The View layer is responsible for suppressing the display (showing "—") based on `confidence == SENSOR_ERROR`. The retained value freezes the dial position.

**Recovery:** When the sensor begins returning varying values again, the `SENSOR_ERROR` state clears and normal confidence evaluation resumes.

---

### ES-02: STABILIZING Display

**Trigger:** `OverallConfidence == STABILIZING` — the device has been rotated faster than 180°/s for > 0.5 seconds (active fast-rotation calibration in progress).

**Cold-start note:** At cold start, the device is stationary; the confidence model emits `POOR` (not `STABILIZING`). `STABILIZING` occurs only during active fast-rotation calibration. See ES-07 for cold-start behavior.

**Behavior:**

| Component | State |
|-----------|-------|
| Dial | Continues to rotate (heading is still being updated by gyroscope) |
| Numerical readout — all sector fields | Continue to update normally |
| Numerical readout — 分金 field | Displays "N/A — calibrate for 分金 precision" |
| Confidence badge | Displays "Calibrating..." in amber |
| "Lock 向" button | Disabled; tooltip "Cannot lock — heading is unreliable" if user taps |

**Recovery:** When angular velocity drops below 10°/s for > 1 second, confidence resumes normal evaluation. If pre-motion conditions were `HIGH`, the badge returns to "High" after recovery.

---

### ES-03: Switching North Type While Locked

**Trigger:** User switches between True North and Magnetic North while 坐向 is locked.

**Behavior:**

1. The stored `xiangBearing_trueN` in `CompassViewModel` does NOT change — the True North bearing is the invariant storage format.
2. `LuopanState.bearingDeg` and `LuopanState.northLabel` update to reflect the new north reference.
3. The 坐向 overlay display bearing is recalculated for display only: `displayBearing = xiangBearing_trueN ± declinationDeg` depending on direction of switch.
4. The `xiangMountain` and `zuoMountain` 山 labels remain unchanged — they are derived from the True North bearing and are invariant to north-type switches.
5. The overlay updates immediately with the new display bearing values and the new north type label ("Mag N" or "True N").

**Worked example:** Locked at 45.0° True N (向: 艮, 坐: 坤); declination = −3.5°.
- Switch to Magnetic North: display bearing = 45.0° − (−3.5°) = 48.5°
- Overlay shows: "向: 艮 (48.5° Mag N)" and "坐: 坤 (228.5° Mag N)"
- 山 labels (艮, 坤) are unchanged

---

### ES-04: Poor Confidence — Lock Disabled

**Trigger:** `OverallConfidence == POOR` and user attempts to tap "Lock 向".

**Behavior:**
- The button is visually disabled (greyed out).
- If the user taps the disabled button area, a tooltip appears: "Cannot lock — heading is unreliable".
- No lock state is created.

---

### ES-05: Ring Visibility — All Rings Hidden

**Trigger:** User hides all 6 rings via the visibility toggle sheet.

**Behavior:**
- The dial canvas shows only the fixed pointer graphic and the background (#2C0E0E).
- The numerical readout panel continues to update normally.
- Heading computation continues normally.
- The 坐向 lock (if active) continues to function; the gold tick mark is not shown because Ring 5 (二十四山) — the ring the tick mark is drawn on — is hidden.

---

### ES-06: Pinch-to-Zoom — Scale Clamping

**Trigger:** User pinches below 0.8× or spreads above 2.0×.

**Behavior:**
- The scale is clamped silently at the boundary. No error message or haptic feedback.
- The gesture continues to be tracked (if the user reverses direction, the scale moves back from the boundary).

---

### ES-07: Luopan Mode Entry While Sensor Pipeline Still Initializing (Cold Start)

**Trigger:** User navigates to Luopan Mode before the sensor pipeline has delivered the first valid heading (e.g., immediately after cold start).

**Cold-start confidence state:** At cold start, the device is stationary and the confidence model emits `POOR` (the initial state, not `STABILIZING`). `STABILIZING` requires active fast rotation at > 180°/s.

**Behavior:**
- The dial renders at bearing 0° (default/null heading).
- The confidence badge shows "Poor" (red) until the pipeline produces its first valid confidence evaluation.
- The "Lock 向" button is disabled.
- The numerical readout panel shows fields for bearing = 0° but with "Poor" confidence badge and sector fields populated from the 0° bearing.
- As soon as the first valid `LuopanState` is emitted, the dial updates to the live heading and the panel updates normally.
- If the user performs the fast-rotation calibration wand, confidence transitions: `POOR` → `STABILIZING` (during wand) → `HIGH` (once calibrated).

---

## 6. Acceptance Criteria

Each criterion below is testable, maps to one or more business rules or scenarios, and corresponds to a REQ acceptance scenario.

### AC-01: Mode Entry Latency (REQ-DISPLAY-01, Scenario A)

*Given* Phase 2 is complete and Modern Mode is active,  
*When* the user taps the Luopan tab,  
*Then* the first complete dial frame is rendered within 300 ms of the tap; the heading shown at first render matches the current device heading.

---

### AC-02a: Dial Counter-Rotation Math (BR-11, Scenario A)

*Given* Luopan Mode is active and the device is stationary at heading H,  
*When* the device is rotated clockwise by θ degrees,  
*Then* the dial ring assembly is rotated by `−θ` degrees from its reference position (±2°); `dialRotationDeg = -bearingDeg (mod 360°)` as specified in BR-11.

---

### AC-02b: Pointer Fixed (BR-11, Scenario A)

*Given* Luopan Mode is active,  
*When* the device rotates at any speed or direction,  
*Then* the pointer element does not rotate; only the ring assembly (Rings 1–6 together) rotates; the pointer remains fixed at screen top center at all times.

---

### AC-03: Readout Panel Canonical Order (Scenario B)

*Given* Luopan Mode is active, confidence is `HIGH`, True North is active, and the device points at 180°,  
*When* the numerical readout panel is observed,  
*Then* it displays (in canonical order): `午 (Wǔ) · 午 (Wǔ) · ☲ 離 南 · 180.0° · True N · 壬午分金 · High`.

---

### AC-04: Ring 4 Sector at 90° (Scenario C)

*Given* the device points at 90°,  
*When* Luopan Mode is active,  
*Then* Ring 4 shows "卯" under the pointer; Ring 5 shows "卯" under the pointer; Ring 3 shows "☳ 震 東" under the pointer.

---

### AC-05: Readout at 90° High Confidence (Scenario C-1)

*Given* the device points at 90° and confidence is `HIGH`,  
*When* the numerical readout panel is observed,  
*Then* it displays: `卯 (Mǎo) · 卯 (Mǎo) · ☳ 震 東 · 90.0° · True N · 壬卯分金 · High`.

---

### AC-06: 分金 Hidden at Moderate Confidence (BR-07, Scenario D)

*Given* confidence is `MODERATE`,  
*When* Luopan Mode is active,  
*Then* the 分金 field in the numerical readout panel displays "N/A — calibrate for 分金 precision".

---

### AC-07: 坐向 Lock at High Confidence (BR-06, Scenario E-1)

*Given* confidence is `HIGH` and the device points at 45°,  
*When* the user taps "Lock 向",  
*Then* the 坐向 overlay shows "向: 艮 (45.0° True N)" and "坐: 坤 (225.0° True N)"; a gold tick mark appears on Ring 5 (二十四山) at 45°; the live dial continues to rotate.

---

### AC-08: 坐向 Lock at Moderate Confidence (BR-05, Scenario E-2)

*Given* confidence is `MODERATE` and the device points at 90°,  
*When* the user taps "Lock 向",  
*Then* the button is enabled; the overlay shows "向: 卯 (90.0° True N)" and "坐: 酉 (270.0° True N)"; the 分金 field is NOT shown in the overlay.

---

### AC-09: Lock Button Disabled at Poor Confidence (BR-05, Scenario E-3)

*Given* confidence is `POOR`,  
*When* the user attempts to tap "Lock 向",  
*Then* the button is disabled (greyed out); a tooltip shows "Cannot lock — heading is unreliable".

---

### AC-10: 坐 Wrap-Around at 向 = 270° (BR-06, Scenario E-4)

*Given* confidence is `HIGH` and the device points at 270°,  
*When* the user taps "Lock 向",  
*Then* `坐 = (270 + 180) mod 360 = 90°`; the overlay shows "向: 酉 (270.0° True N)" and "坐: 卯 (90.0° True N)".

---

### AC-11: 坐 Wrap-Around at 向 = 350° (BR-06, Scenario E-5)

*Given* confidence is `HIGH` and the device points at 350°,  
*When* the user taps "Lock 向",  
*Then* `坐 = (350 + 180) mod 360 = 170°`; the overlay shows "向: 壬 (350.0° True N)" and "坐: 丙 (170.0° True N)".

---

### AC-12: Default Language — English Locale (BR-08, Scenario F)

*Given* the system locale is `en` and both localization toggles are OFF,  
*When* Luopan Mode is opened,  
*Then* all ring labels display in Traditional Chinese characters; no English labels appear.

---

### AC-13: "Show Romanization" Toggle (Flow 7, Scenario F)

*Given* the "Show romanization" toggle is enabled,  
*When* Luopan Mode is displayed,  
*Then* pinyin appears alongside each character on ring labels and in the numerical readout panel (e.g., "子 Zǐ" on Ring 4, "午 (Wǔ)" in the readout panel).

---

### AC-14: Ring Visibility — Hide and Verify (Flow 5, Scenario G)

*Given* the user long-presses the dial for ≥ 500 ms and the BottomSheetDialog appears,  
*When* the user hides Ring 4 (十二地支) via its switch,  
*Then* Ring 4 disappears from the dial immediately; Rings 1, 2, 3, 5, and 6 remain visible; heading computation and readout panel are unaffected.

---

### AC-15: Ring Visibility — Session Reset (BR-09, Scenario G)

*Given* Ring 4 was hidden in the current session,  
*When* the user cold-starts the app and opens Luopan Mode,  
*Then* Ring 4 is visible (visibility state reset to default).

---

### AC-16: Ring 4 子/亥 Wrap-Around — Boundary Assertions (BR-02, Scenario H)

*Given* Luopan Mode is active (all bearings evaluated against Ring 4 LUT):

| Bearing | Expected Ring 4 Label |
|---------|-----------------------|
| 344.9° | 亥 |
| 345.0° | 子 |
| 14.9° | 子 |
| 15.0° | 丑 |

*When* the sector lookup is evaluated for each bearing,  
*Then* the results match the table above exactly.

---

### AC-17: Ring 4 Generic Boundary (BR-01, Scenario H)

*Given* Luopan Mode is active:

| Bearing | Expected Ring 4 Label |
|---------|-----------------------|
| 44.9° | 丑 |
| 45.0° | 寅 |

*When* the sector lookup is evaluated,  
*Then* the results match the table above exactly.

---

### AC-18: Ring 5 Sub-15° Boundaries (BR-01, BR-03, Scenario H)

*Given* Luopan Mode is active:

| Bearing | Expected Ring 5 Label |
|---------|-----------------------|
| 7.4° | 子 |
| 7.5° | 癸 |
| 22.4° | 癸 |
| 22.5° | 丑 |

*When* the Ring 5 sector lookup is evaluated,  
*Then* the results match the table above exactly.

---

### AC-19: Ring 3 45° Boundaries (BR-01, BR-03, Scenario H)

*Given* Luopan Mode is active:

| Bearing | Expected Ring 3 Label |
|---------|-----------------------|
| 22.4° | ☵ 坎 北 |
| 22.5° | ☶ 艮 東北 |
| 67.4° | ☶ 艮 東北 |
| 67.5° | ☳ 震 東 |

*When* the Ring 3 sector lookup is evaluated,  
*Then* the results match the table above exactly.

---

### AC-20: 壬子分金 Wrap-Around (BR-04)

*Given* Luopan Mode is active and confidence is `HIGH`:

| Bearing | Expected Ring 6 Label |
|---------|-----------------------|
| 357.9° | 庚子分金 |
| 358.0° | 壬子分金 |
| 0.0° | 壬子分金 |
| 4.0° | 甲子分金 |

*When* the Ring 6 sector lookup is evaluated,  
*Then* the results match the table above exactly.

---

### AC-21: Lock Preserved Across Mode Switch (BR-10, Scenario I)

*Given* 坐向 is locked at 45° in Luopan Mode,  
*When* the user switches to Modern Mode and then returns to Luopan Mode in the same session,  
*Then* the 坐向 overlay is restored showing "向: 艮 (45.0° True N)" and "坐: 坤 (225.0° True N)".

---

### AC-22: North Reference Switch Updates Readout (Scenario J-1)

*Given* Luopan Mode is active, True North is selected, and the device points at 182°,  
*When* the user switches north reference to Magnetic North,  
*Then* the bearing in the numerical readout updates immediately to the magnetic bearing; the north type field changes from "True N" to "Mag N"; all ring-sector fields update to reflect the new bearing.

---

### AC-23: North Reference Switch — Locked 向 Display Updates, 山 Labels Unchanged (Scenario J-2)

*Given* 坐向 is locked at True N 45° (向: 艮) and north reference switches to Magnetic North (declination = −3.5°),  
*When* the Luopan readout updates,  
*Then* the overlay bearing values update to reflect the magnetic-adjusted heading (向: 艮 (48.5° Mag N), 坐: 坤 (228.5° Mag N)); the north type label in the overlay changes to "Mag N"; the 山 labels (艮, 坤) do NOT change — they remain derived from the stored True North bearing.

---

### AC-24: SENSOR_ERROR State — Fields Hidden (ES-01)

*Given* `OverallConfidence == SENSOR_ERROR`,  
*When* Luopan Mode is active,  
*Then* the 山, 地支, 後天八卦, and bearing fields in the numerical readout display "—"; the confidence badge shows "Sensor error" in red; the "Lock 向" button is disabled.

---

### AC-25: Pinch-to-Zoom — Scale Range (Flow 6, BR-09)

*Given* Luopan Mode is active at zoom scale 1.0×,  
*When* the user pinches to attempt 0.5× scale, then spreads to attempt 3.0× scale,  
*Then* the scale is clamped at 0.8× at minimum and 2.0× at maximum; no scale values outside this range are applied.

---

### AC-26: Zoom Survives Config Change (BR-09)

*Given* the user has set zoom to 1.5× in Luopan Mode,  
*When* the device undergoes a configuration change (e.g., screen rotation),  
*Then* the zoom scale remains at 1.5× after the config change; no reset to 1.0× occurs.

---

### AC-27: Zoom Resets on Cold Start (BR-09)

*Given* the user had zoom set to 1.5× in the previous session,  
*When* the user cold-starts the app and opens Luopan Mode,  
*Then* the zoom scale is 1.0× (session-only, not persisted).

---

### AC-28: SENSOR_ERROR While 坐向 Locked (ES-01, TE-F03)

*Given* 坐向 is locked and `OverallConfidence` transitions to `SENSOR_ERROR`,  
*When* Luopan Mode is active,  
*Then* the 坐向 overlay remains visible and displays the last-valid locked True North bearing values (frozen); the readout panel shows "—" for all computed fields (山, 地支, 後天八卦, bearing); the confidence badge shows "Sensor error" in red; the lock button label shows "Clear 向" (lock remains active but frozen).

---

### AC-29: STABILIZING State — Lock Button Disabled (ES-02, TE-F04)

*Given* `OverallConfidence == STABILIZING` (active fast-rotation calibration in progress),  
*When* Luopan Mode is active,  
*Then* the lock button is disabled; the confidence badge shows "Calibrating..." in amber; the 分金 field shows "N/A — calibrate for 分金 precision".

---

*End of FSPEC-luopan-p3-luopan-mode*
